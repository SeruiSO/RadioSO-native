package com.seruiso.radio1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class RadioAutoService extends MediaBrowserServiceCompat {
    public static final String ROOT = "root";
    private MediaSessionCompat session;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSessionCompat(this, "radio_so_auto");
        session.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { send(RadioWatchService.ACTION_PLAY); }
            @Override public void onPause() { send(RadioWatchService.ACTION_PAUSE); }
            @Override public void onSkipToNext() { send(RadioWatchService.ACTION_NOTIF_NEXT); }
            @Override public void onSkipToPrevious() { send(RadioWatchService.ACTION_NOTIF_PREV); }
            @Override public void onPlayFromMediaId(String mediaId, Bundle extras) {
                if (mediaId == null || mediaId.isEmpty()) return;
                Intent i = new Intent(RadioAutoService.this, RadioWatchService.class);
                i.setAction(RadioWatchService.ACTION_PLAY_URL);
                i.putExtra(RadioWatchService.EXTRA_URL, mediaId);
                String name = nameFor(mediaId);
                if (name != null) i.putExtra(RadioWatchService.EXTRA_NAME, name);
                startForegroundService(i);
            }
        });
        session.setActive(true);
        setSessionToken(session.getSessionToken());
        refresh();
        prefListener = (p, key) -> refresh();
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    private void send(String action) {
        Intent i = new Intent(this, RadioWatchService.class);
        i.setAction(action);
        startForegroundService(i);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
    }

    private boolean isLocal() {
        return "local".equals(prefs().getString(LocalMusicPlugin.KEY_MODE, "radio"));
    }

    private String nameFor(String id) {
        try {
            if (isLocal()) {
                JSONArray uris = new JSONArray(prefs().getString(LocalMusicPlugin.KEY_LOCAL_URIS, "[]"));
                JSONArray titles = new JSONArray(prefs().getString(LocalMusicPlugin.KEY_LOCAL_TITLES, "[]"));
                for (int i = 0; i < uris.length(); i++) {
                    if (id.equals(uris.optString(i))) {
                        return i < titles.length() ? titles.optString(i) : null;
                    }
                }
            } else {
                JSONArray urls = new JSONArray(prefs().getString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, "[]"));
                JSONArray names = new JSONArray(prefs().getString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, "[]"));
                for (int i = 0; i < urls.length(); i++) {
                    if (id.equals(urls.optString(i))) {
                        return i < names.length() ? names.optString(i) : null;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void refresh() {
        SharedPreferences p = prefs();
        boolean playing = p.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
                || p.getBoolean(BluetoothAutoPlayPlugin.KEY_ACTUALLY_PLAYING, false);
        String name = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O");
        String track = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "");
        session.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        (track != null && !track.isEmpty()) ? track : name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, name)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                        (track != null && !track.isEmpty()) ? track : name)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, name)
                .build());
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());
        notifyChildrenChanged(ROOT);
    }

    private void setAaActive(boolean active) {
        try {
            prefs().edit().putBoolean(BluetoothAutoPlayPlugin.KEY_AA_ACTIVE, active).apply();
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        setAaActive(true);
        return new BrowserRoot(ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> out = new ArrayList<>();
        if (!ROOT.equals(parentId)) {
            result.sendResult(out);
            return;
        }
        SharedPreferences p = prefs();
        try {
            if (isLocal()) {
                JSONArray uris = new JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_URIS, "[]"));
                JSONArray titles = new JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_TITLES, "[]"));
                JSONArray artists = new JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, "[]"));
                for (int i = 0; i < uris.length(); i++) {
                    String uri = uris.optString(i);
                    if (uri == null || uri.isEmpty()) continue;
                    String title = i < titles.length() ? titles.optString(i, "Local") : "Local";
                    String artist = i < artists.length() ? artists.optString(i, "") : "";
                    out.add(item(uri, title, artist));
                }
            } else {
                JSONArray urls = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, "[]"));
                JSONArray names = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, "[]"));
                JSONArray genres = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, "[]"));
                JSONArray countries = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, "[]"));
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i);
                    if (url == null || url.isEmpty()) continue;
                    String name = i < names.length() ? names.optString(i, "Station") : "Station";
                    String g = i < genres.length() ? genres.optString(i, "") : "";
                    String c = i < countries.length() ? countries.optString(i, "") : "";
                    out.add(item(url, name, (g + " · " + c).trim()));
                }
            }
        } catch (Exception e) {
            android.util.Log.w("RadioAuto", "children", e);
        }
        result.sendResult(out);
    }

    private static MediaBrowserCompat.MediaItem item(String id, String title, String subtitle) {
        MediaDescriptionCompat d = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowserCompat.MediaItem(d, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    @Override
    public void onDestroy() {
        try {
            if (prefListener != null) {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .unregisterOnSharedPreferenceChangeListener(prefListener);
            }
        } catch (Exception ignored) {}
        setAaActive(false);
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        super.onDestroy();
    }
}
