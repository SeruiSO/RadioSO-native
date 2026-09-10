package com.seruiso.radio1;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
public class BluetoothReceiver extends BroadcastReceiver {
    private boolean watchOn(Context c) {
        return c.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true);
    }
    private void markA2dp(Context c) {
        c.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putLong("lastA2dpConnectMs", System.currentTimeMillis()).commit();
    }
    private void startSvc(Context c, String action) {
        Intent i = new Intent(c, RadioWatchService.class);
        i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        Context app = context.getApplicationContext();

        // ACL_CONNECTED — ранній тригер (часто приходить раніше за A2DP-профіль)
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            markA2dp(app);
            if (!watchOn(app)) return;
            startSvc(app, RadioWatchService.ACTION_BT);
            return;
        }
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            if (!watchOn(app)) return;
            // Не перевіряємо hasRoute: годинник/SCO лишають «маршрут» і стоп не стається.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (BtAudio.isAndroidAutoActive(app)) return;
                startSvc(app, RadioWatchService.ACTION_PAUSE);
            }, 800);
            return;
        }

        boolean a2dp = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        boolean hs = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        if (!a2dp && !hs) return;
        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);

        if (state == BluetoothProfile.STATE_CONNECTED) {
            markA2dp(app);
            if (!watchOn(app)) return;
            // одразу FGS — без postDelayed у ресівері (процес інакше вбивають)
            startSvc(app, RadioWatchService.ACTION_BT);
            return;
        }
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            if (!watchOn(app)) return;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (BtAudio.isAndroidAutoActive(app)) return;
                startSvc(app, RadioWatchService.ACTION_PAUSE);
            }, 800);
        }
    }
}
