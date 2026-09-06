package com.seruiso.radio1;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
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
    /** Пам'ять гучності по конкретному BT-пристрою — незалежно від тумблера BT-watch. */
    private void applyRememberedVolume(Context c, String address) {
        try {
            Integer level = BtVolumeStore.get(c, address);
            if (level == null) return;
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0);
        } catch (Exception ignored) {}
    }
    private void saveCurrentVolume(Context c, String address) {
        if (address == null || address.isEmpty()) return;
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int level = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            BtVolumeStore.save(c, address, level);
        } catch (Exception ignored) {}
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        boolean a2dp = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        boolean hs = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action);
        if (!a2dp && !hs) return;
        int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
        Context app = context.getApplicationContext();
        String address = null;
        try {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device != null) address = device.getAddress();
        } catch (Exception ignored) {}
        if (state == BluetoothProfile.STATE_CONNECTED) {
            markA2dp(app);
            // Гучність — незалежно від тумблера BT-watch, це окрема фіча.
            applyRememberedVolume(app, address);
            if (!watchOn(app)) return;
            // одразу FGS — без postDelayed у ресівері (процес інакше вбивають)
            startSvc(app, RadioWatchService.ACTION_BT);
            return;
        }
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            saveCurrentVolume(app, address);
            if (!watchOn(app)) return;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (BtAudio.hasRoute(app)) return;
                startSvc(app, RadioWatchService.ACTION_PAUSE);
            }, 2000);
        }
    }
}
