package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Після буту НЕ стартуємо FGS (Android 15+ кидає ForegroundServiceStartNotAllowed).
 * Лише прапорець: реальний старт — A2DP (BluetoothReceiver) або відкриття UI
 * (MainActivity.maybeStartBtIfConnected).
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
            p.edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PENDING_BT_AFTER_BOOT, true).apply();
            Log.i("BootReceiver", "boot — pendingBtWatchAfterBoot=true (no FGS)");
        } catch (Exception e) {
            Log.e("BootReceiver", "boot flag failed", e);
        }
    }
}
