package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Stack 4: do not start foreground service or playback on boot.
 * BT autostart remains in BluetoothReceiver (A2DP CONNECTED → ACTION_BT).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(intent.getAction())) {
            return;
        }
        Log.i("BootReceiver", "boot ignored — no service start; wait for A2DP if needed");
    }
}
