package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.seruiso.radio1.ALARM_FIRE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_FIRE.equals(intent.getAction())) return;
        RadioAlarm.INSTANCE.onReceiveFire(context, intent);
    }
}
