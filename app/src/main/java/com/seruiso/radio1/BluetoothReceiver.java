package com.seruiso.radio1;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

public class BluetoothReceiver extends BroadcastReceiver {
    private boolean isWatchEnabled(Context context) {
        SharedPreferences p = context.getSharedPreferences(
            BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
        return p.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true);
    }

    private void markA2dp(Context context) {
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putLong("lastA2dpConnectMs", System.currentTimeMillis()).commit();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        // Автоплей / stop — ТІЛЬКИ по A2DP. Headset/ACL/Adapter лише mark.
        if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                markA2dp(context);
                if (!isWatchEnabled(context)) {
                    android.util.Log.i("BluetoothReceiver", "BT watch disabled — ignore A2DP connect");
                    return;
                }
                final Context appCtx = context.getApplicationContext();
                // 0.9.51: 900ms — профілі машини часто стабілізуються повільніше
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isWatchEnabled(appCtx)) return;
                    if (!BtAudio.hasRoute(appCtx)) {
                        android.util.Log.i("BluetoothReceiver", "A2DP connected but no route yet — still send ACTION_BT");
                    }
                    Intent svc = new Intent(appCtx, RadioWatchService.class);
                    svc.setAction(RadioWatchService.ACTION_BT);
                    if (Build.VERSION.SDK_INT >= 26) {
                        appCtx.startForegroundService(svc);
                    } else {
                        appCtx.startService(svc);
                    }
                }, 900);
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (!isWatchEnabled(context)) return;
                final Context appCtx = context.getApplicationContext();
                // 0.9.51: 2s — transient disconnect під час handoff машини
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (BtAudio.hasRoute(appCtx)) {
                        android.util.Log.i("BluetoothReceiver", "A2DP disconnect ignored — route still present");
                        return;
                    }
                    Intent svc = new Intent(appCtx, RadioWatchService.class);
                    svc.setAction(RadioWatchService.ACTION_STOP);
                    if (Build.VERSION.SDK_INT >= 26) {
                        appCtx.startForegroundService(svc);
                    } else {
                        appCtx.startService(svc);
                    }
                }, 2000);
            }
            return;
        }

        if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
            if (state == BluetoothProfile.STATE_CONNECTED && BtAudio.hasRoute(context)) {
                markA2dp(context);
            }
            return;
        }
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (BtAudio.hasRoute(context)) markA2dp(context);
            return;
        }
        if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                BluetoothAdapter.STATE_DISCONNECTED);
            if (state == BluetoothAdapter.STATE_CONNECTED && BtAudio.hasRoute(context)) {
                markA2dp(context);
            }
        }
    }
}
