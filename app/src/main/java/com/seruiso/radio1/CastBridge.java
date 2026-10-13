package com.seruiso.radio1;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.exoplayer.ExoPlayer;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;

/**
 * Cast не чіпає плеєр, поки немає сесії.
 * Поки сесія є — гучність локального плеєра 0, потік іде на ТВ.
 */
public final class CastBridge {
    private static final String TAG = "RadioWatch";
    private static boolean listening;
    private static ExoPlayer player;
    private static boolean ducked;

    private CastBridge() {}

    public static void init(Context ctx) {
        try {
            Context app = ctx.getApplicationContext();
            CastContext cc = CastContext.getSharedInstance(app);
            if (listening) return;
            listening = true;
            cc.getSessionManager().addSessionManagerListener(new SessionManagerListener<CastSession>() {
                @Override public void onSessionStarting(CastSession s) {}
                @Override public void onSessionStarted(CastSession s, String id) { onUp(app); }
                @Override public void onSessionStartFailed(CastSession s, int err) {}
                @Override public void onSessionEnding(CastSession s) { unduck(); }
                @Override public void onSessionEnded(CastSession s, int err) { unduck(); }
                @Override public void onSessionResuming(CastSession s, String id) {}
                @Override public void onSessionResumed(CastSession s, boolean was) { onUp(app); }
                @Override public void onSessionResumeFailed(CastSession s, int err) {}
                @Override public void onSessionSuspended(CastSession s, int reason) {}
            }, CastSession.class);
            Log.i(TAG, "cast ready");
        } catch (Throwable t) {
            Log.w(TAG, "cast unavailable", t);
        }
    }

    public static void bindPlayer(ExoPlayer p) {
        player = p;
    }

    /** Виклик з playUrl: якщо ТВ вже підключений — оновити потік. */
    public static void push(Context c, String url, String title) {
        try {
            CastSession s = CastContext.getSharedInstance(c.getApplicationContext())
                    .getSessionManager().getCurrentCastSession();
            if (s == null || !s.isConnected()) return;
            duck();
            load(s, url, title);
        } catch (Throwable ignored) {}
    }

    private static void onUp(Context c) {
        duck();
        try {
            SharedPreferences p = c.getSharedPreferences(
                    BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
            String url = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "");
            String name = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "RadioSO");
            CastSession s = CastContext.getSharedInstance(c)
                    .getSessionManager().getCurrentCastSession();
            if (s != null) load(s, url, name);
        } catch (Throwable t) {
            Log.w(TAG, "cast onUp", t);
        }
    }

    private static void load(CastSession s, String url, String title) {
        if (s == null || url == null || !url.startsWith("http")) {
            Log.i(TAG, "cast skip non-http");
            return;
        }
        if (s.getRemoteMediaClient() == null) return;
        String low = url.toLowerCase();
        String type = "audio/mpeg";
        if (low.contains(".m3u8")) type = "application/x-mpegURL";
        else if (low.contains(".aac")) type = "audio/aac";
        else if (low.contains(".ogg")) type = "audio/ogg";
        else if (low.contains(".flac")) type = "audio/flac";
        MediaMetadata md = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        md.putString(MediaMetadata.KEY_TITLE, title == null || title.isEmpty() ? "RadioSO" : title);
        MediaInfo info = new MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType(type)
                .setMetadata(md)
                .build();
        s.getRemoteMediaClient().load(
                new MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build());
        Log.i(TAG, "cast load " + title);
    }

    private static void duck() {
        try {
            if (player != null) {
                player.setVolume(0f);
                ducked = true;
            }
        } catch (Throwable ignored) {}
    }

    private static void unduck() {
        try {
            if (player != null && ducked) player.setVolume(1f);
        } catch (Throwable ignored) {}
        ducked = false;
    }
}
