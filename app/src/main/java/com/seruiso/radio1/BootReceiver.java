package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(intent.getAction())) return;
        boolean watch = context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                .getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true);
        if (!watch) return;
        Intent svc = new Intent(context, RadioWatchService.class);
        svc.setAction(RadioWatchService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
        else context.startService(svc);
    }
}
