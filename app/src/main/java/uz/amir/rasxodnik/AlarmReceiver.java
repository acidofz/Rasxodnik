package uz.amir.rasxodnik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    static final String MORNING = "uz.amir.rasxodnik.MORNING";
    static final String EVENING = "uz.amir.rasxodnik.EVENING";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (MORNING.equals(a)) Reminders.morning(c);
        else if (EVENING.equals(a)) Reminders.evening(c);
    }
}
