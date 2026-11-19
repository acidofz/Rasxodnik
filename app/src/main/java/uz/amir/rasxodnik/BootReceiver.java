package uz.amir.rasxodnik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После перезагрузки телефона будильники сбрасываются — ставим заново. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Reminders.schedule(c);
    }
}
