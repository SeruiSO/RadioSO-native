package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * 0.9.51: after boot start watch FGS (ACTION_START) if BT watch enabled.
 * Does NOT autoplay — A2DP CONNECTED / service one-shot check starts station.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(intent.getAction())) {
            return;
        }
        try {
            SharedPreferences p = context.getSharedPreferences(
                BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
            if (!p.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) {
                Log.i("BootReceiver", "boot — BT watch off, skip");
                return;
            }
            Context app = context.getApplicationContext();
            Intent svc = new Intent(app, RadioWatchService.class);
            svc.setAction(RadioWatchService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
            Log.i("BootReceiver", "boot — started ACTION_START watch");
        } catch (Exception e) {
            Log.e("BootReceiver", "boot start failed", e);
        }
    }
}
