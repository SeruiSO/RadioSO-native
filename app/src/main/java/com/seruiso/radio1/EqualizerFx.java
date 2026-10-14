package com.seruiso.radio1;

import android.content.Context;
import android.media.audiofx.Equalizer;
import android.util.Log;

/** Системний Equalizer на audio session ExoPlayer. Пресети — криві по смугах. */
public final class EqualizerFx {
    private static final String TAG = "RadioWatch";
    public static final String KEY_ON = "eqEnabled";
    public static final String KEY_PRESET = "eqPreset";

    public static final String[] PRESETS = {
        "flat", "bass", "deepbass", "superbass", "vocal", "rock", "pop", "classical", "night"
    };

    private static Equalizer eq;
    private static int session;

    private EqualizerFx() {}

    public static void attach(Context c, int audioSessionId) {
        if (c == null || audioSessionId == 0) return;
        if (eq != null && session == audioSessionId) {
            apply(c);
            return;
        }
        release();
        session = audioSessionId;
        try {
            eq = new Equalizer(0, audioSessionId);
            apply(c);
            Log.i(TAG, "EQ attach session=" + audioSessionId + " bands=" + eq.getNumberOfBands());
        } catch (Throwable t) {
            Log.w(TAG, "EQ attach failed", t);
            eq = null;
        }
    }

    public static void apply(Context c) {
        if (eq == null || c == null) return;
        try {
            android.content.SharedPreferences p = c.getSharedPreferences(
                BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
            boolean on = p.getBoolean(KEY_ON, false);
            String preset = p.getString(KEY_PRESET, "flat");
            if (!on) {
                eq.setEnabled(false);
                return;
            }
            eq.setEnabled(true);
            short n = eq.getNumberOfBands();
            if (n <= 0) return;
            short[] range = eq.getBandLevelRange();
            short min = range[0];
            short max = range[1];
            float[] curve = curve(preset, n);
            // 0.72 — запас від кліпінгу в авто / навушниках
            final float scale = 0.72f;
            short softMax = (short) Math.round(max * scale);
            short softMin = (short) Math.round(min * scale);
            for (short i = 0; i < n; i++) {
                float g = curve[i];
                int level = g >= 0f ? Math.round(g * softMax) : Math.round(-g * softMin);
                if (level > max) level = max;
                if (level < min) level = min;
                eq.setBandLevel(i, (short) level);
            }
        } catch (Throwable t) {
            Log.w(TAG, "EQ apply failed", t);
        }
    }

    public static void release() {
        try {
            if (eq != null) {
                eq.setEnabled(false);
                eq.release();
            }
        } catch (Throwable ignored) {}
        eq = null;
        session = 0;
    }

    /** 5 контрольних точок 0..1 по частоті, інтерполяція на n смуг. Значення −1..1. */
    private static float[] curve(String preset, int n) {
        float[] src;
        // −1..1 → частка від hardware min/max; бас ≤~0.55, highs ≥0 (не ріжемо)
        if ("superbass".equals(preset)) src = new float[] {0.55f, 0.42f, 0.10f, 0.08f, 0.14f};
        else if ("deepbass".equals(preset)) src = new float[] {0.48f, 0.34f, 0.06f, 0.08f, 0.12f};
        else if ("bass".equals(preset)) src = new float[] {0.36f, 0.22f, 0.04f, 0.10f, 0.16f};
        else if ("vocal".equals(preset)) src = new float[] {-0.2f, 0.05f, 0.55f, 0.4f, 0.05f};
        else if ("rock".equals(preset)) src = new float[] {0.5f, 0.25f, -0.1f, 0.3f, 0.55f};
        else if ("pop".equals(preset)) src = new float[] {0.15f, 0.35f, 0.1f, 0.25f, 0.35f};
        else if ("classical".equals(preset)) src = new float[] {0.4f, 0.15f, -0.05f, 0.2f, 0.45f};
        else if ("night".equals(preset)) src = new float[] {-0.15f, -0.05f, 0.1f, -0.25f, -0.6f};
        else src = new float[] {0f, 0f, 0f, 0f, 0f};
        float[] out = new float[n];
        if (n == 1) { out[0] = src[2]; return out; }
        for (int i = 0; i < n; i++) {
            float x = i / (float) (n - 1) * (src.length - 1);
            int a = (int) Math.floor(x);
            int b = Math.min(a + 1, src.length - 1);
            float t = x - a;
            out[i] = src[a] * (1f - t) + src[b] * t;
        }
        return out;
    }
}
