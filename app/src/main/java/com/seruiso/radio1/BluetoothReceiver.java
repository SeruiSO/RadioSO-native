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

    /** Хоч одна BT-функція з меню увімкнена. */
    private boolean btFeatureOn(Context c) {
        try {
            return BluetoothAutoPlayPlugin.isBtAutostart(c);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean btAutostartOn(Context c) {
        try {
            return c.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                .getBoolean(BluetoothAutoPlayPlugin.KEY_BT_AUTOSTART, true);
        } catch (Exception e) {
            return true;
        }
    }

    /** Сумісність: стеження = є хоч одна функція (або старий KEY_BT_WATCH). */
    private boolean watchOn(Context c) {
        try {
            android.content.SharedPreferences p = c.getSharedPreferences(
                BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
            if (btFeatureOn(c)) return true;
            return p.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, false);
        } catch (Exception e) {
            return true;
        }
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
        Log.i(TAG, "BT recv " + action + " watch=" + watchOn(app) + " auto=" + btAutostartOn(app) + " aa=" + aa + " btOn=" + btOn());

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
            // Прості навушники: часто лише ACL, без A2DP CONNECTION_STATE_CHANGED у фоні.
            // Завжди піднімаємо сервіс (MediaSession). Автоплей — лише якщо тумблер ON
            // (перевірка в RadioWatchService.ACTION_BT). Debounce 1.5s захищає від
            // подвійного ACL + profile CONNECTED.
            markA2dp(app);
            BluetoothAutoPlayPlugin.ensureWatchOn(app);
            try {
                app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_PENDING_BT_AFTER_BOOT, false)
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)
                    .putLong("userPausedWhileBtAt", 0L)
                    .apply();
            } catch (Exception ignored) {}
            Log.i(TAG, "ACL_CONNECTED — start ACTION_BT (auto=" + btAutostartOn(app) + ")");
            startSvc(app, RadioWatchService.ACTION_BT);
            // Страховка: FGS з фону на 12+ часто ріжеться — Alarm через 2с
            // (якщо вже грає, HEADPHONE_FALLBACK сам зробить skip).
            if (BluetoothAutoPlayPlugin.isBtAutostart(app)) {
                try { RadioWatchService.scheduleHeadphoneFallback(app, 4000L); } catch (Exception ignored) {}
            }
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
            BluetoothAutoPlayPlugin.ensureWatchOn(app);
            try {
                app.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_PENDING_BT_AFTER_BOOT, false)
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)
                    .putLong("userPausedWhileBtAt", 0L)
                    .apply();
            } catch (Exception ignored) {}
            // Завжди стартуємо сервіс: при autostart OFF — MediaSession живий для Play
            // з навушників; автоплей вирішує RadioWatchService (KEY_BT_AUTOSTART).
            if (!BluetoothAutoPlayPlugin.isBtAutostart(app)) {
                Log.i(TAG, "CONNECTED — autostart off, start service for MediaSession/Play");
            }
            startSvc(app, RadioWatchService.ACTION_BT);
            if (BluetoothAutoPlayPlugin.isBtAutostart(app)) {
                try { RadioWatchService.scheduleHeadphoneFallback(app, 4000L); } catch (Exception ignored) {}
            }
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
