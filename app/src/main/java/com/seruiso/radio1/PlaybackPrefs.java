package com.seruiso.radio1;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single place for intended / reported playback flags.
 * KEY_PLAY = intended; KEY_IS_PLAYING = reported (canonical).
 * KEY_ACTUALLY_PLAYING — legacy alias, still written for old readers, do not use for new reads.
 */
public final class PlaybackPrefs {
    private PlaybackPrefs() {}

    private static SharedPreferences p(Context ctx) {
        return ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isIntended(Context ctx) {
        return p(ctx).getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false);
    }

    public static void setIntended(Context ctx, boolean intended) {
        try {
            p(ctx).edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, intended).apply();
        } catch (Exception ignored) {}
    }

    /** Reported playing for UI. Writes canonical KEY_IS_PLAYING (+ legacy alias). */
    public static void reportPlaying(Context ctx, boolean playing) {
        try {
            p(ctx).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, playing)
                .putBoolean(BluetoothAutoPlayPlugin.KEY_ACTUALLY_PLAYING, playing) // legacy
                .apply();
        } catch (Exception ignored) {}
    }

    public static boolean isReportedPlaying(Context ctx) {
        return p(ctx).getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false);
    }

    public static void clearIntent(Context ctx) {
        try {
            // commit: stop must be visible to service/receivers immediately
            p(ctx).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)
                .putBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
                .putBoolean(BluetoothAutoPlayPlugin.KEY_ACTUALLY_PLAYING, false)
                .commit();
        } catch (Exception ignored) {}
    }
}
