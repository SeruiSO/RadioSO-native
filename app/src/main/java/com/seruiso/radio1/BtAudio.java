package com.seruiso.radio1;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

/**
 * Bluetooth audio route helpers (stack 5).
 * hasRoute = device present; findA2dpDevice / preferA2dp try to steer output.
 */
public final class BtAudio {
    private BtAudio() {}

    public static boolean hasRoute(Context ctx) {
        return findA2dpDevice(ctx) != null
            || isLegacyBtOn(ctx);
    }

    private static boolean isLegacyBtOn(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            return am.isBluetoothA2dpOn() || am.isBluetoothScoOn();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** First suitable BT output device, or null. */
    public static AudioDeviceInfo findA2dpDevice(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return null;
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devs == null) return null;
            AudioDeviceInfo a2dp = null;
            for (AudioDeviceInfo d : devs) {
                int ty = d.getType();
                if (ty == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    a2dp = d;
                    break;
                }
                if (a2dp == null && (ty == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || ty == 26 || ty == 27
                        || ty == AudioDeviceInfo.TYPE_HEARING_AID)) {
                    a2dp = d;
                }
            }
            return a2dp;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Ask player to prefer BT output when API allows (Media3 / ExoPlayer).
     * No-op on failure — system routing still applies.
     */
    public static void preferA2dp(Context ctx, Object player) {
        if (player == null || Build.VERSION.SDK_INT < 23) return;
        AudioDeviceInfo dev = findA2dpDevice(ctx);
        if (dev == null) return;
        try {
            java.lang.reflect.Method m = player.getClass()
                .getMethod("setPreferredAudioDevice", AudioDeviceInfo.class);
            m.invoke(player, dev);
            android.util.Log.i("BtAudio", "preferred A2DP device set type=" + dev.getType());
        } catch (Exception e) {
            android.util.Log.d("BtAudio", "setPreferredAudioDevice n/a: " + e.getMessage());
        }
    }

    /** Clear preferred device so later phone speaker play works normally. */
    public static void clearPreferred(Object player) {
        if (player == null || Build.VERSION.SDK_INT < 23) return;
        try {
            java.lang.reflect.Method m = player.getClass()
                .getMethod("setPreferredAudioDevice", AudioDeviceInfo.class);
            m.invoke(player, new Object[]{null});
        } catch (Exception ignored) {}
    }
}
