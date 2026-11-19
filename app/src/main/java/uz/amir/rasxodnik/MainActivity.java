package uz.amir.rasxodnik;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 7;
    private WebView web;

    public class Bridge {
        @JavascriptInterface
        public void share(String text, String title) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, title);
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, title));
            });
        }

        @JavascriptInterface
        public void setReminders(String json) {
            Reminders.save(MainActivity.this, json);
        }

        @JavascriptInterface
        public boolean notifAllowed() {
            return Reminders.canNotify(MainActivity.this);
        }

        @JavascriptInterface
        public void requestNotif() {
            runOnUiThread(MainActivity.this::askNotificationPermission);
        }

        @JavascriptInterface
        public void testNotify() {
            Reminders.post(MainActivity.this, Reminders.ID_TEST, "Расходник", "Уведомления работают");
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Reminders.ensureChannel(this);
        Reminders.schedule(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);
        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/www/index.html");

        SharedPreferences p = getSharedPreferences(Reminders.PREFS, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33 && !p.getBoolean("asked", false)) {
            p.edit().putBoolean("asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void askNotificationPermission() {
        if (Reminders.canNotify(this)) return;
        if (Build.VERSION.SDK_INT >= 33 && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    private void notifyWeb() {
        if (web != null) web.evaluateJavascript("window.onNotifPerm && window.onNotifPerm()", null);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        notifyWeb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        notifyWeb();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack ? String(window.onBack()) : 'false'", v -> {
            if (!"\"true\"".equals(v)) MainActivity.super.onBackPressed();
        });
    }
}
