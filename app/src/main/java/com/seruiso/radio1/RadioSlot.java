package com.seruiso.radio1;

import android.content.Context;
import android.content.SharedPreferences;

/** Остання радіостанція окремо від подкаста / локальних файлів. */
public final class RadioSlot {
    private RadioSlot() {}

    public static void remember(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(
                    BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
            String genre = p.getString(BluetoothAutoPlayPlugin.KEY_GENRE, "");
            String mode = p.getString(LocalMusicPlugin.KEY_MODE, "radio");
            if ("podcast".equals(genre) || "podcast".equals(mode)) return;
            String url = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "");
            if (url == null || url.isEmpty()) return;
            if (url.startsWith("content:") || url.startsWith("file:")) return;
            p.edit()
                    .putString(BluetoothAutoPlayPlugin.KEY_RADIO_URL, url)
                    .putString(BluetoothAutoPlayPlugin.KEY_RADIO_NAME,
                            p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O"))
                    .putString(BluetoothAutoPlayPlugin.KEY_RADIO_FAV,
                            p.getString(BluetoothAutoPlayPlugin.KEY_FAVICON, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_RADIO_GENRE, genre == null ? "" : genre)
                    .putString(BluetoothAutoPlayPlugin.KEY_RADIO_COUNTRY,
                            p.getString(BluetoothAutoPlayPlugin.KEY_COUNTRY, ""))
                    .apply();
        } catch (Exception ignored) {}
    }

    /** Якщо зараз грає подкаст — підставити радіо в KEY_* перед автостартом BT. */
    public static boolean restoreIfPodcast(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(
                    BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
            String genre = p.getString(BluetoothAutoPlayPlugin.KEY_GENRE, "");
            String mode = p.getString(LocalMusicPlugin.KEY_MODE, "radio");
            if (!"podcast".equals(genre) && !"podcast".equals(mode)) return false;
            String url = p.getString(BluetoothAutoPlayPlugin.KEY_RADIO_URL, "");
            if (url == null || url.isEmpty()) return false;
            p.edit()
                    .putString(BluetoothAutoPlayPlugin.KEY_URL, url)
                    .putString(BluetoothAutoPlayPlugin.KEY_NAME,
                            p.getString(BluetoothAutoPlayPlugin.KEY_RADIO_NAME, "Radio S O"))
                    .putString(BluetoothAutoPlayPlugin.KEY_FAVICON,
                            p.getString(BluetoothAutoPlayPlugin.KEY_RADIO_FAV, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_GENRE,
                            p.getString(BluetoothAutoPlayPlugin.KEY_RADIO_GENRE, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_COUNTRY,
                            p.getString(BluetoothAutoPlayPlugin.KEY_RADIO_COUNTRY, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_TRACK, "")
                    .putString(LocalMusicPlugin.KEY_MODE, "radio")
                    .putString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "radio")
                    .putLong("localPositionMs", 0L)
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
