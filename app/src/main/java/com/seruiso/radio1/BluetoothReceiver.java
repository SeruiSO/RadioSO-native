package com.seruiso.radio1;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Класичний BT (не AA): CONNECTED → ACTION_BT; disconnect → ACTION_ROUTE_LOST (service: ~4s grace).
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
            // Android 12–15: FGS з фону часто ріжеться (немає видимого Activity).
            Log.w(TAG, "startSvc denied " + action + " " + e.getClass().getSimpleName());
            if (RadioWatchService.ACTION_BT.equals(action)) {
                // AlarmManager має exemption — підніме сервіс через 4с (навушники без PLAY)
                RadioWatchService.scheduleHeadphoneFallback(c, 300L);
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


    /** Годинник/браслет/миша — не ROUTE_LOST і не BT handoff window. */
    private static boolean isAudioBluetoothDevice(BluetoothDevice device) {
        if (device == null) return true; // немає extra — не блокуємо (старі інтенти)
        try {
            BluetoothClass cls = device.getBluetoothClass();
            if (cls == null) return true;
            int major = cls.getMajorDeviceClass();
            return major == BluetoothClass.Device.Major.AUDIO_VIDEO
                    || major == BluetoothClass.Device.Major.UNCATEGORIZED
                    || major == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private void doRouteLost(Context app) {
        try {
            app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)
                .putLong("userPausedWhileBtAt", 0L)
                .apply();
        } catch (Exception ignored) {}
        startSvc(app, RadioWatchService.ACTION_ROUTE_LOST);
    }

    private boolean deferIfA2dpStillUp(final Context app, final String tag) {
        boolean up = false;
        try { up = BtAudio.hasA2dpOutput(app); } catch (Exception ignored) {}
        if (!up) return false;
        Log.i(TAG, tag + " — A2DP still listed, recheck 1.8s");
        final PendingResult pr = goAsync();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    boolean still = false;
                    try { still = BtAudio.hasA2dpOutput(app); } catch (Exception ignored) {}
                    if (still) Log.i(TAG, tag + " skip — A2DP remains");
                    else {
                        Log.i(TAG, tag + " — A2DP gone, ROUTE_LOST");
                        doRouteLost(app);
                    }
                } finally {
                    if (pr != null) pr.finish();
                }
            }
        }, 1800L);
        return true;
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
                            try {
                app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)
                    .putLong("userPausedWhileBtAt", 0L)
                    .apply();
            } catch (Exception ignored) {}
                startSvc(app, RadioWatchService.ACTION_ROUTE_LOST);
            }
            return;
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            BluetoothDevice devConn = null;
            try {
                devConn = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            } catch (Exception ignored) {}
            if (!isAudioBluetoothDevice(devConn)) {
                Log.i(TAG, "ACL_CONNECTED skip — non-audio device");
                return;
            }
            // 0.13.73: ACL = лінка є, sink ще може не бути.
            // Не стартуємо play — лише timestamp для handoff-вікна.
            // Play лише з A2DP / Headset STATE_CONNECTED нижче.
            markA2dp(app);
            Log.i(TAG, "ACL_CONNECTED — mark only, no play");
            return;
        }
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            BluetoothDevice devDis = null;
            try {
                devDis = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            } catch (Exception ignored) {}
            if (!isAudioBluetoothDevice(devDis)) {
                Log.i(TAG, "ACL_DISCONNECTED skip — non-audio device");
                return;
            }
            if (!watchOn(app)) return;
            // Класичний BT: стоп. Skip лише жива AA + BT ще on.
            if (aa && btOn()) {
                Log.i(TAG, "ACL_DISCONNECTED skip — live AA");
                return;
            }
            if (deferIfA2dpStillUp(app, "ACL_DISCONNECTED")) return;
                        try {
                app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)
                    .putLong("userPausedWhileBtAt", 0L)
                    .apply();
            } catch (Exception ignored) {}
            startSvc(app, RadioWatchService.ACTION_ROUTE_LOST);
            return;
        }

        boolean a2dp = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        boolean hs = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        if (!a2dp && !hs) return;
        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);

        if (state == BluetoothProfile.STATE_CONNECTED) {
            markA2dp(app);
            if (!watchOn(app)) return;
            try {
                app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PENDING_BT_AFTER_BOOT, false).apply();
            } catch (Exception ignored) {}
            startSvc(app, RadioWatchService.ACTION_BT);
            return;
        }
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            if (!watchOn(app)) return;
            if (aa && btOn()) {
                Log.i(TAG, "profile DISCONNECTED skip — live AA");
                return;
            }
            if (deferIfA2dpStillUp(app, "profile DISCONNECTED")) return;
            try { RadioWatchService.cancelHeadphoneFallback(app); } catch (Exception ignored) {}
            // A2DP disconnect = магнітола пішла (класичний режим)
            try {
                app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)
                    .putLong("userPausedWhileBtAt", 0L)
                    .apply();
            } catch (Exception ignored) {}
            startSvc(app, RadioWatchService.ACTION_ROUTE_LOST);
        }
    }
}
