package com.seruiso.radio1;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Класичний BT (не AA): A2DP/Headset CONNECTED → ACTION_BT; disconnect/BT off → ACTION_PAUSE.
 * ACL_CONNECTED лише mark timestamp (не play) — менше звуку з телефону / пинка.
 * AA: не чіпаємо маршрутизацію; pause skip лише коли KEY_AA_ACTIVE і BT ще увімкнений.
 */
public class BluetoothReceiver extends BroadcastReceiver {
    private static final String TAG = "RadioWatch";

    private boolean watchOn(Context c) {
        return c.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true);
    }

    private void markA2dp(Context c) {
        c.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putLong(BluetoothAutoPlayPlugin.KEY_LAST_A2DP_MS, System.currentTimeMillis()).apply();
    }

    private void startSvc(Context c, String action) {
        try {
            Intent i = new Intent(c, RadioWatchService.class);
            i.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
            Log.i(TAG, "startSvc " + action);
        } catch (Exception e) {
            Log.e(TAG, "startSvc fail " + action, e);
            try {
                Intent i = new Intent(c, RadioWatchService.class);
                i.setAction(action);
                c.startService(i);
            } catch (Exception e2) {
                Log.e(TAG, "startService fail", e2);
            }
        }
    }

    private boolean btOn() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        Context app = context.getApplicationContext();
        boolean aa = BtAudio.isAndroidAutoActive(app);
        Log.i(TAG, "BT recv " + action + " watch=" + watchOn(app) + " aa=" + aa + " btOn=" + btOn());

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
            if ((st == BluetoothAdapter.STATE_OFF || st == BluetoothAdapter.STATE_TURNING_OFF)
                    && watchOn(app)) {
                // Вимкнули BT повністю — класичний стоп завжди
                startSvc(app, RadioWatchService.ACTION_PAUSE);
            }
            return;
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            // 0.13.73: ACL = лінка є, sink ще може не бути.
            // Не стартуємо play — лише timestamp для handoff-вікна.
            // Play лише з A2DP / Headset STATE_CONNECTED нижче.
            markA2dp(app);
            Log.i(TAG, "ACL_CONNECTED — mark only, no play");
            return;
        }
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            if (!watchOn(app)) return;
            // Класичний BT: стоп. Skip лише жива AA + BT ще on.
            if (aa && btOn()) {
                Log.i(TAG, "ACL_DISCONNECTED skip — live AA");
                return;
            }
            startSvc(app, RadioWatchService.ACTION_PAUSE);
            return;
        }

        boolean a2dp = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        boolean hs = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        if (!a2dp && !hs) return;
        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);

        if (state == BluetoothProfile.STATE_CONNECTED) {
            markA2dp(app);
            if (!watchOn(app)) return;
            startSvc(app, RadioWatchService.ACTION_BT);
            return;
        }
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            if (!watchOn(app)) return;
            if (aa && btOn()) {
                Log.i(TAG, "profile DISCONNECTED skip — live AA");
                return;
            }
            // A2DP disconnect = магнітола пішла (класичний режим)
            startSvc(app, RadioWatchService.ACTION_PAUSE);
        }
    }
}
