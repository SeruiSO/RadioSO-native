package com.seruiso.radio1;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Intended / reported playback + last pause reason (single source of truth).
 * KEY_PLAY = intended; KEY_IS_PLAYING = reported.
 * pauseReason: none | user | route | focus | network
 */
public final class PlaybackPrefs {
    private PlaybackPrefs() {}

    public static final String KEY_PAUSE_REASON = "pauseReason";
    public static final String REASON_NONE = "none";
    public static final String REASON_USER = "user";
    public static final String REASON_ROUTE = "route";
    public static final String REASON_FOCUS = "focus";
    public static final String REASON_NETWORK = "network";

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

    public static void reportPlaying(Context ctx, boolean playing) {
        try {
            p(ctx).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, playing)
                .apply();
        } catch (Exception ignored) {}
    }

    public static boolean isReportedPlaying(Context ctx) {
        return p(ctx).getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false);
    }

    public static void setPauseReason(Context ctx, String reason) {
        try {
            p(ctx).edit()
                .putString(KEY_PAUSE_REASON, reason != null ? reason : REASON_NONE)
                .apply();
        } catch (Exception ignored) {}
    }

    public static String getPauseReason(Context ctx) {
        try {
            return p(ctx).getString(KEY_PAUSE_REASON, REASON_NONE);
        } catch (Exception e) {
            return REASON_NONE;
        }
    }

    public static void clearIntent(Context ctx) {
        try {
            p(ctx).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)
                .putBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
                .commit();
        } catch (Exception ignored) {}
    }

    /** Full clear of play intent + pause reason (e.g. STOP). */
    public static void clearAll(Context ctx) {
        try {
            p(ctx).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)
                .putBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
                .putString(KEY_PAUSE_REASON, REASON_NONE)
                .commit();
        } catch (Exception ignored) {}
    }
}
