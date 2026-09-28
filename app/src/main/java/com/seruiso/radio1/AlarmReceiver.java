package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.seruiso.radio1.ALARM_FIRE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_FIRE.equals(intent.getAction())) return;
        RadioAlarm.State s = RadioAlarm.INSTANCE.load(context);
        if (!s.getEnabled()) {
            Log.i("AlarmReceiver", "disabled — skip");
            return;
        }
        RadioAlarm.INSTANCE.onFired(context);
        Intent i = new Intent(context, RadioWatchService.class);
        i.setAction(RadioWatchService.ACTION_ALARM);
        i.putExtra(RadioWatchService.EXTRA_URL, s.getUrl());
        i.putExtra(RadioWatchService.EXTRA_NAME, s.getName());
        i.putExtra("favicon", s.getFavicon());
        i.putExtra("genre", s.getGenre());
        i.putExtra("country", s.getCountry());
        i.setPackage(context.getPackageName());
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
            Log.i("AlarmReceiver", "start ACTION_ALARM " + s.getName());
        } catch (Exception e) {
            Log.w("AlarmReceiver", "start FGS failed", e);
        }
    }
}
