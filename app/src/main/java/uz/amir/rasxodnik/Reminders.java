package uz.amir.rasxodnik;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/** Хранит данные для напоминаний и показывает уведомления без открытия WebView. */
public final class Reminders {
    static final String CHANNEL = "payments";
    static final String PREFS = "rx_notif";
    static final int ID_PAYMENTS = 100, ID_EVENING = 101, ID_TEST = 102;

    private Reminders() {}

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Платежи и напоминания",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Обязательные платежи и вечернее напоминание записать расходы");
            nm.createNotificationChannel(ch);
        }
    }

    private static PendingIntent alarm(Context c, String action, int code) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Две ежедневные проверки: утром (платежи) и вечером (записал ли расходы). */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next(10, 0), AlarmManager.INTERVAL_DAY,
                alarm(c, AlarmReceiver.MORNING, 1));
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next(21, 0), AlarmManager.INTERVAL_DAY,
                alarm(c, AlarmReceiver.EVENING, 2));
    }

    private static long next(int hour, int minute) {
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, hour);
        k.set(Calendar.MINUTE, minute);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        if (k.getTimeInMillis() <= System.currentTimeMillis()) k.add(Calendar.DAY_OF_YEAR, 1);
        return k.getTimeInMillis();
    }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json).apply();
    }

    private static JSONObject load(Context c) {
        try {
            return new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    static void post(Context c, int id, String title, String text) {
        if (!canNotify(c)) return;
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }

    private static String monthKey(Calendar k) {
        return String.format(Locale.US, "%04d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1);
    }

    private static String dayKey(Calendar k) {
        return String.format(Locale.US, "%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1,
                k.get(Calendar.DAY_OF_MONTH));
    }

    /** Утро: неоплаченные обязательные платежи за 3 дня, за день, в день оплаты и просроченные. */
    static void morning(Context c) {
        JSONObject d = load(c);
        if (!d.optBoolean("recOn", true)) return;
        JSONArray rec = d.optJSONArray("rec");
        if (rec == null) return;
        Calendar now = Calendar.getInstance();
        String month = monthKey(now);
        int today = now.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < rec.length(); i++) {
            JSONObject r = rec.optJSONObject(i);
            if (r == null) continue;
            JSONArray paid = r.optJSONArray("paid");
            boolean isPaid = false;
            if (paid != null) for (int j = 0; j < paid.length(); j++) if (month.equals(paid.optString(j))) isPaid = true;
            if (isPaid) continue;
            int left = Math.min(r.optInt("day", 1), daysInMonth) - today;
            String when;
            if (left < 0) when = "просрочено на " + (-left) + " дн.";
            else if (left == 0) when = "сегодня";
            else if (left == 1) when = "завтра";
            else if (left == 3) when = "через 3 дня";
            else continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(r.optString("name")).append(" — ").append(r.optString("amt")).append(", ").append(when);
            count++;
        }
        if (count == 0) return;
        post(c, ID_PAYMENTS, count == 1 ? "Обязательный платёж" : "Обязательные платежи: " + count, sb.toString());
    }

    /** Вечер: если сегодня ничего не записано. */
    static void evening(Context c) {
        JSONObject d = load(c);
        if (!d.optBoolean("eveOn", true)) return;
        if (dayKey(Calendar.getInstance()).equals(d.optString("lastTx"))) return;
        post(c, ID_EVENING, "Запиши расходы за сегодня",
                "Пара минут сейчас, и в конце месяца будет видно, куда ушли деньги.");
    }
}
