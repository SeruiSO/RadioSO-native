package com.seruiso.radio1;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
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

    /** True only if a classic A2DP output is listed (not SCO-only). */
    public static boolean hasA2dpOutput(Context ctx) {
        AudioDeviceInfo d = findA2dpDevice(ctx);
        return d != null && d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
    }

    /** True if current playback configs already sit on BT/USB/car (API 28+). */
    public static boolean isPlaybackOnBtOrCar(Context ctx) {
        if (isAndroidAutoActive(ctx)) return true;
        if (Build.VERSION.SDK_INT < 28) return hasA2dpOutput(ctx);
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return hasA2dpOutput(ctx);
            java.util.List<AudioPlaybackConfiguration> list = am.getActivePlaybackConfigurations();
            if (list == null || list.isEmpty()) return false;
            for (AudioPlaybackConfiguration c : list) {
                AudioDeviceInfo d = c.getAudioDeviceInfo();
                if (d == null) continue;
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || t == AudioDeviceInfo.TYPE_USB_DEVICE
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET
                        || t == AudioDeviceInfo.TYPE_USB_ACCESSORY
                        || t == AudioDeviceInfo.TYPE_BUS
                        || t == 26 || t == 27) return true;
            }
        } catch (Exception ignored) {}
        return false;
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
    /** true, якщо зараз активна сесія Android Auto (RadioAutoService підключений браузером). */
    public static boolean isAndroidAutoActive(Context ctx) {
        try {
            if (ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(BluetoothAutoPlayPlugin.KEY_AA_ACTIVE, false)) return true;
        } catch (Exception ignored) {}
        try {
            int ui = ctx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_TYPE_MASK;
            if (ui == android.content.res.Configuration.UI_MODE_TYPE_CAR) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public static void preferA2dp(Context ctx, Object player) {
        // Ніколи не setPreferredAudioDevice(A2DP).
        // Google: AA сам веде USAGE_MEDIA у колонки авто. Пінінг A2DP до
        // onGetRoot() залишає AudioTrack на класичному BT — UI «грає», звуку немає.
        if (player == null) return;
        clearPreferred(player);
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
