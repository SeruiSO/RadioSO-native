package com.seruiso.radio1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.res.Configuration;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import androidx.media.MediaBrowserServiceCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.extractor.metadata.icy.IcyInfo;
import androidx.media3.common.C;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.session.MediaSession;
import org.json.JSONArray;

public class RadioWatchService extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_BT = "com.seruiso.radio1.BT_CONNECTED";
    public static final String ACTION_START = "com.seruiso.radio1.START_WATCH";
    public static final String ACTION_AA_ROUTE = "com.seruiso.radio1.AA_ROUTE";
    public static final String ACTION_STOP = "com.seruiso.radio1.STOP";
    public static final String ACTION_PLAY = "com.seruiso.radio1.PLAY";
    public static final String ACTION_PAUSE = "com.seruiso.radio1.PAUSE";
    public static final String ACTION_PLAY_URL = "com.seruiso.radio1.PLAY_URL";
    public static final String ACTION_MEDIA_NEXT = "com.seruiso.radio1.MEDIA_NEXT";
    public static final String ACTION_MEDIA_PREV = "com.seruiso.radio1.MEDIA_PREV";
    public static final String ACTION_NOTIF_PLAY = "com.seruiso.radio1.NOTIF_PLAY";
    public static final String ACTION_NOTIF_PAUSE = "com.seruiso.radio1.NOTIF_PAUSE";
    public static final String ACTION_NOTIF_NEXT = "com.seruiso.radio1.NOTIF_NEXT";
    public static final String ACTION_NOTIF_PREV = "com.seruiso.radio1.NOTIF_PREV";
    public static final String ACTION_STATUS_UI = "com.seruiso.radio1.STATUS_UI";
    public static final String ACTION_TRACK_META = "com.seruiso.radio1.TRACK_META";
    public static final String ACTION_PLAYBACK_UI = "com.seruiso.radio1.PLAYBACK_UI";
    public static final String ACTION_SEEK = "com.seruiso.radio1.SEEK";
    public static final String EXTRA_POSITION_MS = "positionMs";
    public static final String EXTRA_TRACK = "track";

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";

    private static final String CHANNEL = "radio_playback";
    private static final int NOTIF_ID = 42;

    private static volatile RadioWatchService INSTANCE;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private String currentName = "Radio S O";
    private long lastSkipMs = 0;
    private long lastPlayMs = 0;
    private String lastPlayedUrl = "";
    /** URL що реально грає зараз — source of truth для reconnect */
    private String currentPlayUrl = "";
    private boolean pausedByFocusLoss = false;
    private String lastTrackTitle = "";
    private Bitmap stationArt = null;
    private String stationArtUrl = "";
    private int artGen = 0;
    /** Поточне HTTP-з'єднання завантаження обкладинки — щоб можна було скасувати. */
    private volatile java.net.HttpURLConnection artConn = null;
    private final java.util.Map<String, Bitmap> artCache = new java.util.LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) {
            return size() > 24;
        }
    };
    private static final int ART_MAX_BYTES = 512 * 1024;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean noisyRegistered = false;
    private long ignoreNoisyUntilMs = 0L;
    private boolean sawA2dpAfterBtStart = false;
    private Runnable routeWatchTick;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered = false;
    private long networkLostAtMs = 0L;
    private android.os.Handler silenceHandler;
    private Runnable silenceCheck;
    private int bufferingTicks = 0;
    private long pendingSeekMs = -1L;
    private android.os.Handler positionHandler;
    private Runnable positionTicker;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                long nowN = System.currentTimeMillis();
                if (nowN < ignoreNoisyUntilMs) {
                    android.util.Log.i("RadioWatch", "NOISY ignored — BT/AA settle window");
                    return;
                }
                if (BtAudio.isAndroidAutoActive(RadioWatchService.this)) {
                    android.util.Log.i("RadioWatch", "NOISY ignored — Android Auto");
                    return;
                }
                SharedPreferences spNoisy = getSharedPreferences(
                    BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                boolean watch = spNoisy.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true);
                if (watch) {
                    if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                        forceStopPlayback("NOISY classic watch");
                    }
                    return;
                }
                if (BtAudio.hasRoute(RadioWatchService.this)) {
                    android.util.Log.i("RadioWatch", "NOISY ignored — route present, watch off");
                    return;
                }
                if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                    forceStopPlayback("NOISY watch-off");
                }
            }
        }
    };

    /** Токен Media3-сесії для Android Auto (той самий плеєр, що грає звук). */
    public static android.support.v4.media.session.MediaSessionCompat.Token compatToken() {
        RadioWatchService s = INSTANCE;
        if (s == null || s.mediaSession == null) return null;
        try {
            java.lang.reflect.Method m = s.mediaSession.getClass().getMethod("getSessionCompatToken");
            Object t = m.invoke(s.mediaSession);
            if (t instanceof android.support.v4.media.session.MediaSessionCompat.Token)
                return (android.support.v4.media.session.MediaSessionCompat.Token) t;
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        createChannel();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        // Живий радіопотік: менший minBuffer — менше «затягувати» 320 kbps на старті.
        // bufferForPlaybackAfterRebufferMs=1000 — швидке повернення звуку після короткого збою.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    12_000,  /* minBufferMs — було 30с, для 320kbps це зайве навантаження */
                    60_000,  /* maxBufferMs — достатньо для live, без гігантського запасу */
                    2_500,   /* bufferForPlaybackMs */
                    1_000    /* bufferForPlaybackAfterRebufferMs */
                )
                .build();

        // М'якші HTTP-таймаути + User-Agent (деякі IPFM/ICY чутливі до дефолтного UA).
        // HTTP для радіо + DefaultDataSource зверху — щоб локальні content:// і file:// теж грали.
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("RadioSO/0.12.21 (Android)")
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);

        // Не здаємось з першого короткого збою: до 8 спроб, backoff 1..5с.
        LoadErrorHandlingPolicy softErrors = new DefaultLoadErrorHandlingPolicy(/* minRetry */ 6) {
            @Override
            public long getRetryDelayMsFor(LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
                int n = loadErrorInfo.errorCount;
                if (n > 8) return C.TIME_UNSET; // далі — onPlayerError / наш reconnect
                return Math.min(1000L * n, 5000L);
            }
        };

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(softErrors);

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        // Фокус тримаємо вручну (requestFocus/abandonFocus) — вимикаємо
        // вбудоване керування фокусом ExoPlayer, щоб не було подвійного
        // requestAudioFocus() і конфліктів саме в момент BT/AA-хендоверу.
        androidx.media3.common.AudioAttributes media3Attrs =
                new androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build();
        player.setAudioAttributes(media3Attrs, /* handleAudioFocus= */ false);

        Player sessionPlayer = new ForwardingPlayer(player) {
            @Override
            public boolean isCommandAvailable(int command) {
                if (command == Player.COMMAND_SEEK_TO_NEXT
                        || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                        || command == Player.COMMAND_SEEK_TO_PREVIOUS
                        || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                    return true;
                }
                return super.isCommandAvailable(command);
            }

            @Override
            public Player.Commands getAvailableCommands() {
                return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build();
            }

            private boolean withinBtSettle() {
                if (System.currentTimeMillis() < ignoreNoisyUntilMs) return true;
                long lastBt = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .getLong("lastA2dpConnectMs", 0L);
                long ago = System.currentTimeMillis() - lastBt;
                return ago >= 0 && ago < 4000;
            }

            @Override
            public void play() {
                setIntendedPlaying(true);
                try {
                    getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
                } catch (Exception ignored) {}
                super.play();
            }

            @Override
            public void pause() {
                if (withinBtSettle()) {
                    android.util.Log.i("RadioWatch", "session pause ignored — BT settle");
                    return;
                }
                super.pause();
            }

            @Override
            public void setPlayWhenReady(boolean playWhenReady) {
                if (playWhenReady) {
                    setIntendedPlaying(true);
                    try {
                        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                            .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
                    } catch (Exception ignored) {}
                    super.setPlayWhenReady(true);
                    return;
                }
                if (withinBtSettle()) {
                    android.util.Log.i("RadioWatch", "session pause(pwr) ignored — BT settle");
                    return;
                }
                super.setPlayWhenReady(false);
            }

            @Override
            public void seekToNext() {
                if (!isLocalMode() && player != null
                        && player.getPlaybackState() == Player.STATE_ENDED) {
                    attemptReconnect("session-ended", true);
                    return;
                }
                skip(true);
            }

            @Override
            public void seekToNextMediaItem() {
                if (!isLocalMode() && player != null
                        && player.getPlaybackState() == Player.STATE_ENDED) {
                    attemptReconnect("session-ended", true);
                    return;
                }
                skip(true);
            }

            @Override
            public void seekToPrevious() { skip(false); }

            @Override
            public void seekToPreviousMediaItem() { skip(false); }
        };

        mediaSession = new MediaSession.Builder(this, sessionPlayer).build();
        try { player.setPauseAtEndOfMediaItems(true); } catch (Throwable ignored) {}
        android.support.v4.media.session.MediaSessionCompat.Token tok = compatToken();
        if (tok != null) {
            setSessionToken(tok);
            android.util.Log.i("RadioWatch", "MediaBrowser token = ExoPlayer session");
        } else {
            android.util.Log.e("RadioWatch", "compatToken null — Auto will have no player session");
        }
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                reportPlaying(isPlaying);
                writeLocalPosition();
                notifyForeground();
                notifyUiPlayback(isPlaying);
            }

            @Override
            public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
                if (mediaMetadata == null) return;
                CharSequence title = mediaMetadata.title;
                if (title == null || title.length() == 0) title = mediaMetadata.displayTitle;
                if (title != null && title.length() > 0) {
                    publishTrack(title.toString());
                }
            }

            @Override
            public void onMetadata(Metadata metadata) {
                if (metadata == null) return;
                for (int i = 0; i < metadata.length(); i++) {
                    Metadata.Entry e = metadata.get(i);
                    if (e instanceof IcyInfo) {
                        String title = ((IcyInfo) e).title;
                        if (title != null && !title.trim().isEmpty()) {
                            publishTrack(title.trim());
                            return;
                        }
                    }
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                android.util.Log.w("RadioWatch", "player error: " + error.getMessage());
                attemptReconnect("player-error", false);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && isLocalMode()) {
                    if (pendingSeekMs >= 0 && player != null) {
                        player.seekTo(pendingSeekMs);
                        pendingSeekMs = -1L;
                    }
                    writeLocalPosition();
                    armPositionTicker();
                }
                if (state == Player.STATE_ENDED) {
                    if (isLocalMode()) {
                        handleLocalEnded();
                        return;
                    }
                    if (!pausedByFocusLoss) attemptReconnect("state-ended", false);
                } else if (state == Player.STATE_IDLE) {
                    if (isLocalMode()) return;
                    if (!pausedByFocusLoss) attemptReconnect("state-idle", false);
                }
            }
        });
        registerNoisy();
        registerNetworkCallback();
        notifyForeground();
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered) return;
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        if (networkLostAtMs <= 0) {
                            android.util.Log.i("RadioWatch", "onAvailable initial — ignore");
                            return;
                        }
                        long lostAgo = System.currentTimeMillis() - networkLostAtMs;
                        networkLostAtMs = 0L;
                        SharedPreferences sp = getSharedPreferences(
                            BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                        if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)) return;
                        if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
                                && (player == null || !player.getPlayWhenReady())) return;
                        if (isLocalMode()) return;
                        if (player != null && player.isPlaying()) {
                            reconnectAttempt = 0;
                            notifyUiStatus(getString(R.string.playing), 0);
                            return;
                        }
                        // ще буферизує (не зупинився, не в помилці) — дати шанс
                        // самому догрузитись замість форсування повного реконекту
                        if (player != null
                                && player.getPlaybackState() == Player.STATE_BUFFERING
                                && player.getPlayWhenReady()) {
                            android.util.Log.i("RadioWatch", "still buffering — grace period before forcing reconnect");
                            if (reconnectHandler == null) {
                                reconnectHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            }
                            reconnectHandler.postDelayed(() -> {
                                if (player != null && player.isPlaying()) {
                                    reconnectAttempt = 0;
                                    notifyUiStatus(getString(R.string.playing), 0);
                                    return;
                                }
                                android.util.Log.i("RadioWatch", "still not playing after grace — forcing reconnect");
                                attemptReconnect("network-available-after-grace", true);
                            }, 4000);
                            return;
                        }
                        attemptReconnect("network-available", true);
                    } catch (Exception e) {
                        android.util.Log.e("RadioWatch", "onAvailable", e);
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (hasInternet()) {
                        android.util.Log.i("RadioWatch", "onLost but other net alive");
                        return;
                    }
                    networkLostAtMs = System.currentTimeMillis();
                    android.util.Log.i("RadioWatch", "network lost (grace, keep playing)");
                });
            }
        };
        try {
            NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
            connectivityManager.registerNetworkCallback(req, networkCallback);
            networkCallbackRegistered = true;
        } catch (Exception e) {
            android.util.Log.e("RadioWatch", "registerNetworkCallback", e);
        }
    }

    /** Форсований реконект: скидає лічильники і одразу пробує грати resolved URL. */
    private void forceNetworkReconnect() {
        android.util.Log.i("RadioWatch", "network available → reconnect");
        notifyUiStatus(getString(R.string.status_reconnect), reconnectAttempt + 1);
        reconnectAttempt = 0;
        reconnectWindowStart = 0L;
        if (reconnectHandler != null) {
            reconnectHandler.removeCallbacksAndMessages(null);
        }
        lastPlayedUrl = "";
        lastPlayMs = 0;
        if (reconnectHandler == null) {
            reconnectHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        reconnectHandler.postDelayed(() -> {
            try {
                String url = resolveReconnectUrl();
                if (url != null && !url.isEmpty()) playUrl(url);
                else scheduleReconnect();
            } catch (Exception e) {
                android.util.Log.e("RadioWatch", "reconnect", e);
            }
        }, 700);
    }

    /**
     * Єдина точка входу для всіх reconnect/resume-тригерів (помилка плеєра,
     * ENDED/IDLE, мережа знову з'явилась, аудіо-фокус повернувся після дзвінка,
     * вотчдог тиші/буферизації). Дебаунс запобігає подвійному playUrl(),
     * якщо два тригери спрацюють майже одночасно.
     */
    private void attemptReconnect(String reason, boolean immediate) {
        if (isLocalMode()) return;
        if (!PlaybackPrefs.isIntended(this)) {
            android.util.Log.i("RadioWatch", "attemptReconnect(" + reason + ") skip — not intended");
            return;
        }
        if (player != null && player.isPlaying()) {
            reconnectAttempt = 0;
            reconnectWindowStart = 0L;
            notifyUiStatus(getString(R.string.playing), 0);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReconnectTriggerMs < RECONNECT_DEBOUNCE_MS) {
            android.util.Log.i("RadioWatch", "attemptReconnect(" + reason + ") debounced");
            return;
        }
        lastReconnectTriggerMs = now;
        android.util.Log.i("RadioWatch", "attemptReconnect: " + reason + " immediate=" + immediate);
        if (immediate) {
            forceNetworkReconnect();
        } else {
            scheduleReconnect();
        }
    }

    private void registerNoisy() {
        if (noisyRegistered) return;
        IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, f);
        }
        noisyRegistered = true;
    }

    private boolean requestFocus() {
        if (audioManager == null) return true;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(this)
                        .setAcceptsDelayedFocusGain(true)
                        .build();
            }
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (player == null) return;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Під час handoff на магнітолу стек інколи краде focus на секунду —
                // не паузимо в цьому вікні (інакше «тиша після перемикання на BT»).
                long lastBtFocus = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .getLong("lastA2dpConnectMs", 0L);
                if (System.currentTimeMillis() - lastBtFocus < BT_HANDOFF_WINDOW_MS) {
                    android.util.Log.i("RadioWatch", "focus loss ignored — BT/AA handoff window");
                    break;
                }
                // відео / дзвінок / інший плеєр — пауза; resume на GAIN якщо intendedPlaying
                if (player.isPlaying() || player.getPlayWhenReady()) {
                    pausedByFocusLoss = true;
                    player.pause();
                    notifyForeground();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Короткий звук, що НЕ потребує тиші (пуш-сповіщення, системний клік) —
                // система явно дозволяє просто притишити, а не зупиняти. Пауза тут була б
                // надлишковою і для живого стріму означала б зайвий ребаферинг на кожен пінг.
                if (player.isPlaying()) {
                    player.setVolume(0.2f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                player.setVolume(1f);
                if (!pausedByFocusLoss) break;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (player == null) return;
                    // Resume тільки якщо саме ми віддали фокус (дзвінок/відео),
                    // а не коли додаток уже був на паузі користувачем.
                    if (!pausedByFocusLoss) return;
                    SharedPreferences sp = getSharedPreferences(
                        BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                    boolean wantPlay = sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false);
                    if (!wantPlay) {
                        pausedByFocusLoss = false;
                        return;
                    }
                    pausedByFocusLoss = false;
                    int state = player.getPlaybackState();
                    if (state == Player.STATE_IDLE || state == Player.STATE_ENDED
                            || player.getCurrentMediaItem() == null) {
                        attemptReconnect("focus-gain", true);
                    } else {
                        requestFocus();
                        player.setPlayWhenReady(true);
                    }
                    notifyForeground();
                    notifyUiPlayback(true);
                }, 700);
                break;
        }
    }

    private void skip(boolean next) {
        long now = System.currentTimeMillis();
        if (now - lastSkipMs < 600) return;
        lastSkipMs = now;

        SharedPreferences p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
        String skipMode = p.getString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "radio");
        if ("off".equals(skipMode)) return;

        if ("temp".equals(skipMode)) {
            try {
                JSONArray urls = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_URLS, "[]"));
                JSONArray names = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_NAMES, "[]"));
                JSONArray favs = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_FAVICONS, "[]"));
                JSONArray genres = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_GENRES, "[]"));
                JSONArray countries = new JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_COUNTRIES, "[]"));
                int index = p.getInt(BluetoothAutoPlayPlugin.KEY_TEMP_INDEX, 0);
                if (urls.length() == 0) { notifyUiSkip(next); return; }
                if (next) index = (index + 1) % urls.length();
                else index = (index - 1 + urls.length()) % urls.length();
                String url = urls.optString(index, "");
                if (url.isEmpty()) return;
                String name = names.optString(index, "Radio S O");
                p.edit()
                    .putInt(BluetoothAutoPlayPlugin.KEY_TEMP_INDEX, index)
                    .putString(BluetoothAutoPlayPlugin.KEY_URL, url)
                    .putString(BluetoothAutoPlayPlugin.KEY_NAME, name)
                    .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, favs.optString(index, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_GENRE, genres.optString(index, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_COUNTRY, countries.optString(index, ""))
                    .putString(BluetoothAutoPlayPlugin.KEY_TRACK, "")
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
                    .commit();
                currentName = name;
                lastTrackTitle = "";
                stationArt = null; stationArtUrl = ""; artGen++;
                playUrl(url);
                loadStationArtAsync();
                notifyUiSkip(next);
            } catch (Exception e) { notifyUiSkip(next); }
            return;
        }

        if ("local".equals(skipMode) || isLocalMode()) {
            try {
                org.json.JSONArray uris = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_URIS, "[]"));
                org.json.JSONArray titles = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_TITLES, "[]"));
                org.json.JSONArray artists = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, "[]"));
                org.json.JSONArray albumIds = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_ALBUM_IDS, "[]"));
                int n = uris.length();
                if (n == 0) return;
                int idx = p.getInt(LocalMusicPlugin.KEY_LOCAL_INDEX, 0);
                boolean shuffle = p.getBoolean(LocalMusicPlugin.KEY_LOCAL_SHUFFLE, false);
                String repeat = p.getString(LocalMusicPlugin.KEY_LOCAL_REPEAT, "off");
                if (shuffle && n > 1) {
                    int nidx = idx;
                    int guard = 0;
                    while (nidx == idx && guard++ < 20) nidx = (int) (Math.random() * n);
                    idx = nidx;
                } else {
                    idx = next ? (idx + 1) : (idx - 1);
                    if (idx < 0) idx = "all".equals(repeat) ? n - 1 : 0;
                    if (idx >= n) {
                        if ("all".equals(repeat)) idx = 0;
                        else {
                            if (player != null) player.pause();
                            clearPlaybackIntent();
                            notifyUiPlayback(false);
                            notifyForeground();
                            return;
                        }
                    }
                }
                String uri = uris.getString(idx);
                String title = idx < titles.length() ? titles.optString(idx, "Local") : "Local";
                String artist = idx < artists.length() ? artists.optString(idx, "") : "";
                String albumId = idx < albumIds.length() ? albumIds.optString(idx, "0") : "0";
                p.edit()
                    .putInt(LocalMusicPlugin.KEY_LOCAL_INDEX, idx)
                    .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
                    .putString(BluetoothAutoPlayPlugin.KEY_URL, uri)
                    .putString(BluetoothAutoPlayPlugin.KEY_NAME, title)
                    .putString(BluetoothAutoPlayPlugin.KEY_TRACK, artist)
                    .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, albumId)
                    .commit();
                currentName = title;
                lastTrackTitle = artist != null ? artist : "";
                loadLocalAlbumArt(albumId);
                playUrl(uri);
                notifyUiSkip(next);
                notifyForeground();
            } catch (Exception e) {
                android.util.Log.w("RadioWatch", "local skip", e);
            }
            return;
        }

        String urlsJson = p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, "[]");
        String namesJson = p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, "[]");
        String favsJson = p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_FAVICONS, "[]");
        String genresJson = p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, "[]");
        String countriesJson = p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, "[]");
        int index = p.getInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, 0);

        try {
            JSONArray urls = new JSONArray(urlsJson);
            JSONArray names = new JSONArray(namesJson);
            JSONArray favs = new JSONArray(favsJson);
            JSONArray genres = new JSONArray(genresJson);
            JSONArray countries = new JSONArray(countriesJson);
            if (urls.length() == 0) {
                notifyUiSkip(next);
                return;
            }
            if (next) {
                index = (index + 1) % urls.length();
            } else {
                index = (index - 1 + urls.length()) % urls.length();
            }
            String url = urls.optString(index, "");
            String name = names.optString(index, "Radio S O");
            String fav = favs.optString(index, "");
            String genre = genres.optString(index, "");
            String country = countries.optString(index, "");
            if (url.isEmpty()) return;

            p.edit()
                .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, index)
                .putString(BluetoothAutoPlayPlugin.KEY_URL, url)
                .putString(BluetoothAutoPlayPlugin.KEY_NAME, name)
                .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, fav != null ? fav : "")
                .putString(BluetoothAutoPlayPlugin.KEY_GENRE, genre != null ? genre : "")
                .putString(BluetoothAutoPlayPlugin.KEY_COUNTRY, country != null ? country : "")
                .putString(BluetoothAutoPlayPlugin.KEY_TRACK, "")
                .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
                .commit(); // commit: reconnect має бачити новий URL одразу

            currentName = name;
            lastTrackTitle = "";
            // скинути кеш іконки щоб форсовано перезавантажити
            stationArt = null;
            stationArtUrl = "";
            artGen++;
            playUrl(url);
            loadStationArtAsync();
            notifyUiSkip(next);
        } catch (Exception e) {
            notifyUiSkip(next);
        }
    }







    private void loadStationArtAsync() {
        SharedPreferences sp = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
        final String fav = sp.getString(BluetoothAutoPlayPlugin.KEY_FAVICON, "");
        if (fav == null || fav.isEmpty()) {
            stationArt = null;
            stationArtUrl = "";
            artGen++;
            return;
        }
        // memory cache hit
        synchronized (artCache) {
            Bitmap cached = artCache.get(fav);
            if (cached != null && !cached.isRecycled()) {
                stationArt = cached;
                stationArtUrl = fav;
                applySessionMetadata(currentName, lastTrackTitle);
                notifyForeground();
                return;
            }
        }
        // Уже завантажено і є bitmap — нічого не робимо.
        if (fav.equals(stationArtUrl) && stationArt != null) return;
        // Той самий URL уже качається — не стартуємо другий потік.
        if (fav.equals(stationArtUrl) && artConn != null) return;

        // Скасувати попереднє завантаження (інший URL або застаріле).
        java.net.HttpURLConnection prev = artConn;
        artConn = null;
        if (prev != null) {
            try { prev.disconnect(); } catch (Exception ignored) {}
        }

        final int gen = ++artGen;
        stationArtUrl = fav;
        new Thread(() -> {
            Bitmap bmp = null;
            HttpURLConnection conn = null;
            try {
                URL u = new URL(fav);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setInstanceFollowRedirects(true);
                artConn = conn;
                conn.connect();
                int code = conn.getResponseCode();
                if (code == 200) {
                    int cl = conn.getContentLength();
                    if (cl > ART_MAX_BYTES) {
                        android.util.Log.w("RadioWatch", "art too large by CL: " + cl);
                    } else {
                        InputStream is = conn.getInputStream();
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n, total = 0;
                        boolean tooBig = false;
                        while ((n = is.read(buf)) != -1) {
                            total += n;
                            if (total > ART_MAX_BYTES) { tooBig = true; break; }
                            bos.write(buf, 0, n);
                        }
                        is.close();
                        if (!tooBig) {
                            byte[] data = bos.toByteArray();
                            Bitmap raw = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (raw != null) {
                                int max = 256;
                                int w = raw.getWidth(), h = raw.getHeight();
                                if (w > max || h > max) {
                                    float s = Math.min((float) max / w, (float) max / h);
                                    bmp = Bitmap.createScaledBitmap(raw, Math.round(w * s), Math.round(h * s), true);
                                    if (bmp != raw) raw.recycle();
                                } else {
                                    bmp = raw;
                                }
                            }
                        } else {
                            android.util.Log.w("RadioWatch", "art too large while reading");
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("RadioWatch", "art load fail: " + e.getMessage());
            } finally {
                if (artConn == conn) artConn = null;
                if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
            }
            final Bitmap result = bmp;
            mainHandler.post(() -> {
                // застаріла відповідь — ігноруємо
                if (gen != artGen) {
                    if (result != null) {
                        try { result.recycle(); } catch (Exception ignored) {}
                    }
                    return;
                }
                if (result != null) {
                    synchronized (artCache) {
                        artCache.put(fav, result);
                    }
                }
                stationArt = result;
                applySessionMetadata(currentName, lastTrackTitle);
                notifyForeground();
            });
        }).start();
    }

    private void applySessionMetadata(String station, String track) {
        if (player == null) return;
        try {
            String title = (track != null && !track.isEmpty()) ? track : (station != null ? station : "Radio S O");
            String artist = (station != null && !station.isEmpty()) ? station : "Radio S O";
            MediaMetadata.Builder mdb = new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setDisplayTitle(title)
                .setSubtitle(artist)
                .setAlbumTitle("Radio S O");
            if (stationArt != null) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    stationArt.compress(Bitmap.CompressFormat.PNG, 90, baos);
                    mdb.setArtworkData(baos.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER);
                } catch (Exception ignored) {}
            }
            MediaMetadata md = mdb.build();
            MediaItem current = player.getCurrentMediaItem();
            if (current == null) return;
            int idx = player.getCurrentMediaItemIndex();
            if (idx < 0) idx = 0;
            MediaItem updated = current.buildUpon().setMediaMetadata(md).build();
            player.replaceMediaItem(idx, updated);
        } catch (Exception e) {
            android.util.Log.w("RadioWatch", "applySessionMetadata", e);
        }
    }

    private void notifyUiStatus(String status, int attempt) {
        try {
            Intent i = new Intent(ACTION_STATUS_UI);
            i.setPackage(getPackageName());
            i.putExtra("status", status == null ? "" : status);
            i.putExtra("attempt", attempt);
            sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    private void armSilenceWatch() {
        if (silenceHandler == null) {
            silenceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        if (silenceCheck != null) silenceHandler.removeCallbacks(silenceCheck);
        silenceCheck = new Runnable() {
            @Override public void run() {
                try {
                    if (player == null) return;
                    SharedPreferences sp = getSharedPreferences(
                        BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                    if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)) {
                        bufferingTicks = 0;
                        return;
                    }
                    int st = player.getPlaybackState();
                    boolean playing = player.isPlaying();
                    // «тиша» / довгий buffering при intended play
                    if (!playing && (st == Player.STATE_BUFFERING
                            || st == Player.STATE_IDLE
                            || st == Player.STATE_ENDED)) {
                        bufferingTicks++;
                        if (bufferingTicks == 1) {
                            notifyUiStatus(getString(R.string.status_buffering), reconnectAttempt);
                        }
                        if (bufferingTicks >= 8) { // ~8 * 3s ≈ 24s
                            android.util.Log.w("RadioWatch", "silence/buffer timeout → reconnect");
                            bufferingTicks = 0;
                            lastPlayedUrl = "";
                            lastPlayMs = 0;
                            attemptReconnect("buffer-timeout", false);
                            return;
                        }
                    } else if (playing) {
                        if (bufferingTicks > 0) notifyUiStatus(getString(R.string.playing), 0);
                        bufferingTicks = 0;
                    }
                } catch (Exception e) {
                    android.util.Log.w("RadioWatch", "silenceCheck", e);
                }
                if (silenceHandler != null && silenceCheck != null) {
                    silenceHandler.postDelayed(silenceCheck, 3000);
                }
            }
        };
        silenceHandler.postDelayed(silenceCheck, 3000);
    }

    /** Delegates to PlaybackPrefs (stack 6). */
    private void reportPlaying(boolean playing) {
        PlaybackPrefs.reportPlaying(this, playing);
    }

    private void writeActuallyPlaying(boolean playing) {
        reportPlaying(playing);
    }

    private void setIntendedPlaying(boolean intended) {
        PlaybackPrefs.setIntended(this, intended);
    }

    private void clearPlaybackIntent() {
        PlaybackPrefs.clearIntent(this);
    }

    private void notifyUiPlayback(boolean playing) {
        Intent i = new Intent(ACTION_PLAYBACK_UI);
        i.setPackage(getPackageName());
        i.putExtra("playing", playing);
        sendBroadcast(i);
    }

    private void notifyUiSkip(boolean next) {
        // Не піднімаємо Activity з фону — лише sticky broadcast для живої UI
        Intent i = new Intent(next ? ACTION_MEDIA_NEXT : ACTION_MEDIA_PREV);
        i.setPackage(getPackageName());
        i.putExtra("fromNativeSkip", true);
        sendBroadcast(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notifyForeground();
        String action = intent != null ? intent.getAction() : null;

        // Stack 4 + 0.9.51: watch FGS; one-shot A2DP probe (cold boot / missed receiver)
        if (ACTION_START.equals(action)) {
            notifyForeground();
            scheduleWatchProbe();
            return START_STICKY;
        }

        if (ACTION_AA_ROUTE.equals(action)) {
            if (player != null) {
                BtAudio.clearPreferred(player);
                player.setVolume(1f);
            }
            return START_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            pausedByFocusLoss = false;
            clearPlaybackIntent();
            if (player != null) {
                BtAudio.clearPreferred(player);
                player.stop();
                player.clearMediaItems();
            }
            abandonFocus();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_PAUSE.equals(action) || ACTION_NOTIF_PAUSE.equals(action)) {
            forceStopPlayback("ACTION_PAUSE");
            return START_STICKY;
        }

        if (ACTION_BT.equals(action)) {
            long nowBt = System.currentTimeMillis();
            if (nowBt - lastBtActionMs < 1500L) {
                android.util.Log.i("RadioWatch", "ACTION_BT debounced");
                return START_STICKY;
            }
            lastBtActionMs = nowBt;
            SharedPreferences spBt = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
            if (!spBt.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) {
                android.util.Log.i("RadioWatch", "ACTION_BT ignored — BT watch off");
                notifyForeground();
                return START_STICKY;
            }
            setIntendedPlaying(true);
            ignoreNoisyUntilMs = System.currentTimeMillis() + 4000L;
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
            } catch (Exception ignored) {}
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putLong("lastA2dpConnectMs", System.currentTimeMillis()).apply();
            } catch (Exception ignored) {}
            if (BtAudio.isAndroidAutoActive(this)) {
                if (player != null) BtAudio.clearPreferred(player);
                playLast();
            } else {
                try {
                    getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_AA_ACTIVE, false).apply();
                } catch (Exception ignored) {}
                playLastWhenBtReady();
                armA2dpRouteWatch();
            }
            return START_STICKY;
        }

        if (ACTION_PLAY.equals(action) || ACTION_NOTIF_PLAY.equals(action)) {
            setIntendedPlaying(true);
            playLast();
            return START_STICKY;
        }

        if (ACTION_NOTIF_NEXT.equals(action)) {
            skip(true);
            return START_STICKY;
        }
        if (ACTION_NOTIF_PREV.equals(action)) {
            skip(false);
            return START_STICKY;
        }

        if (ACTION_PLAY_URL.equals(action) && intent != null) {
            String url = intent.getStringExtra(EXTRA_URL);
            String name = intent.getStringExtra(EXTRA_NAME);
            SharedPreferences.Editor ed = getSharedPreferences(
                BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true);
            if (url != null && !url.isEmpty()) {
                ed.putString(BluetoothAutoPlayPlugin.KEY_URL, url);
            }
            if (name != null && !name.isEmpty()) {
                currentName = name;
                ed.putString(BluetoothAutoPlayPlugin.KEY_NAME, name);
            }
            ed.commit();
            if (isLocalMode()) {
                String albumId = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .getString(BluetoothAutoPlayPlugin.KEY_FAVICON, "0");
                loadLocalAlbumArt(albumId);
            }
            playUrl(url);
            long seekPos = intent.getLongExtra(EXTRA_POSITION_MS, -1L);
            if (seekPos >= 0 && player != null) {
                player.seekTo(seekPos);
            }
            return START_STICKY;
        }

        if (ACTION_SEEK.equals(action) && intent != null) {
            pendingSeekMs = intent.getLongExtra(EXTRA_POSITION_MS, -1L);
            if (pendingSeekMs < 0) pendingSeekMs = intent.getIntExtra(EXTRA_POSITION_MS, -1);
            long pos = intent.getLongExtra(EXTRA_POSITION_MS, -1L);
            if (pos < 0) pos = intent.getIntExtra(EXTRA_POSITION_MS, -1);
            if (player != null && pos >= 0) {
                if (player.getPlaybackState() == Player.STATE_READY) {
                    player.seekTo(pos);
                    pendingSeekMs = -1L;
                } else {
                    pendingSeekMs = pos;
                }
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putLong("localPositionMs", pos).commit();
                writeLocalPosition();
            }
            return START_STICKY;
        }

        return START_STICKY;
    }

    private boolean isLocalMode() {
        try {
            return "local".equals(getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .getString(LocalMusicPlugin.KEY_MODE, "radio"));
        } catch (Exception e) {
            return false;
        }
    }

    private void armPositionTicker() {
        if (positionHandler == null) positionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        if (positionTicker != null) positionHandler.removeCallbacks(positionTicker);
        positionTicker = new Runnable() {
            @Override public void run() {
                writeLocalPosition();
                if (player != null && isLocalMode() && (player.isPlaying() || player.getPlayWhenReady())) {
                    positionHandler.postDelayed(this, 400);
                }
            }
        };
        positionHandler.post(positionTicker);
    }

    private void writeLocalPosition() {
        if (player == null || !isLocalMode()) return;
        try {
            long pos = Math.max(0, player.getCurrentPosition());
            long dur = player.getDuration();
            if (dur < 0 || dur == androidx.media3.common.C.TIME_UNSET) dur = 0;
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
                .putLong("localPositionMs", pos)
                .putLong("localDurationMs", dur)
                .apply();
            Intent i = new Intent(ACTION_PLAYBACK_UI);
            i.setPackage(getPackageName());
            i.putExtra("playing", player.isPlaying());
            i.putExtra("positionMs", pos);
            i.putExtra("durationMs", dur);
            sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    private void loadLocalAlbumArt(String albumIdStr) {
        try {
            long albumId = 0;
            try { albumId = Long.parseLong(albumIdStr); } catch (Exception ignored) {}
            if (albumId <= 0) { stationArt = null; return; }
            android.net.Uri artUri = android.content.ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"), albumId);
            try (java.io.InputStream is = getContentResolver().openInputStream(artUri)) {
                if (is != null) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null) { stationArt = bmp; stationArtUrl = artUri.toString(); }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("RadioWatch", "local art", e);
        }
    }

    private void handleLocalEnded() {
        SharedPreferences sp = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
        String repeat = sp.getString(LocalMusicPlugin.KEY_LOCAL_REPEAT, "off");
        if ("one".equals(repeat) && player != null) {
            player.seekTo(0); player.play(); return;
        }
        skip(true);
    }

    private Runnable btReadyTick;

    private boolean alreadyPlayingLastUrl() {
        if (player == null) return false;
        if (!player.isPlaying() && !player.getPlayWhenReady()) return false;
        try {
            String want = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .getString(BluetoothAutoPlayPlugin.KEY_URL, "");
            if (want == null || want.isEmpty()) return false;
            if (player.getCurrentMediaItem() == null
                    || player.getCurrentMediaItem().localConfiguration == null) return false;
            return want.equals(player.getCurrentMediaItem().localConfiguration.uri.toString());
        } catch (Exception e) {
            return false;
        }
    }

    private void cancelBtTicks() {
        try {
            if (btReadyTick != null) {
                mainHandler.removeCallbacks(btReadyTick);
                btReadyTick = null;
            }
        } catch (Exception ignored) {}
        try {
            if (routeWatchTick != null) {
                mainHandler.removeCallbacks(routeWatchTick);
                routeWatchTick = null;
            }
        } catch (Exception ignored) {}
    }

    private void forceStopPlayback(String reason) {
        android.util.Log.i("RadioWatch", "forceStopPlayback: " + reason);
        // VoIP (WhatsApp/Viber) через BT: A2DP→SCO дає ACTION_PAUSE від BluetoothReceiver.
        // Не затираємо pausedByFocusLoss, інакше AUDIOFOCUS_GAIN після дзвінка не відновить ефір.
        if ("ACTION_PAUSE".equals(reason) && pausedByFocusLoss) {
            android.util.Log.i("RadioWatch", "forceStopPlayback skipped — already paused by focus loss (likely call)");
            return;
        }
        pausedByFocusLoss = false;
        ignoreNoisyUntilMs = 0L;
        sawA2dpAfterBtStart = false;
        cancelBtTicks();
        clearPlaybackIntent();
        try {
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false).apply();
        } catch (Exception ignored) {}
        if (player != null) {
            try {
                player.setPlayWhenReady(false);
                player.pause();
            } catch (Exception e) {
                android.util.Log.w("RadioWatch", "forceStop player", e);
            }
        }
        writeActuallyPlaying(false);
        notifyForeground();
        notifyUiPlayback(false);
    }

    private void armA2dpRouteWatch() {
        if (routeWatchTick != null) {
            mainHandler.removeCallbacks(routeWatchTick);
            routeWatchTick = null;
        }
        sawA2dpAfterBtStart = BtAudio.hasA2dpOutput(this);
        routeWatchTick = new Runnable() {
            @Override public void run() {
                try {
                    SharedPreferences sp = getSharedPreferences(
                        BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                    if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) {
                        routeWatchTick = null;
                        return;
                    }
                    if (BtAudio.isAndroidAutoActive(RadioWatchService.this)) {
                        mainHandler.postDelayed(this, 2000);
                        return;
                    }
                    if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)) {
                        routeWatchTick = null;
                        return;
                    }
                    boolean a2dp = BtAudio.hasA2dpOutput(RadioWatchService.this);
                    if (a2dp) sawA2dpAfterBtStart = true;
                    else if (sawA2dpAfterBtStart) {
                        forceStopPlayback("a2dp-route-lost");
                        routeWatchTick = null;
                        return;
                    }
                    mainHandler.postDelayed(this, 1500);
                } catch (Exception e) {
                    android.util.Log.w("RadioWatch", "routeWatch", e);
                    try { mainHandler.postDelayed(this, 2000); } catch (Exception ignored) {}
                }
            }
        };
        mainHandler.postDelayed(routeWatchTick, 5000);
        android.util.Log.i("RadioWatch", "A2DP route watch armed");
    }

    private void playLastWhenBtReady() {
        final android.os.Handler h = mainHandler;
        if (btReadyTick != null) {
            h.removeCallbacks(btReadyTick);
            btReadyTick = null;
        }
        ignoreNoisyUntilMs = System.currentTimeMillis() + 4000L;
        try {
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
        } catch (Exception ignored) {}
        if (alreadyPlayingLastUrl()) {
            android.util.Log.i("RadioWatch", "BT ready skip — already playing last");
            try {
                if (player != null) {
                    player.setVolume(1f);
                    player.setPlayWhenReady(true);
                }
            } catch (Exception ignored) {}
            return;
        }
        // Не чекаємо TYPE_A2DP: частина магнітол — HFP/BLE/ACL only. Один старт.
        btReadyTick = () -> {
            btReadyTick = null;
            if (alreadyPlayingLastUrl()) {
                try { if (player != null) { player.setVolume(1f); player.setPlayWhenReady(true); } } catch (Exception ignored) {}
                return;
            }
            try { if (player != null) player.setVolume(1f); } catch (Exception ignored) {}
            playLast();
        };
        h.postDelayed(btReadyTick, 500);
    }

    private void scheduleWatchProbe() {
        mainHandler.postDelayed(() -> {
            try {
                SharedPreferences sp = getSharedPreferences(
                    BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) return;
                if (player != null && (player.isPlaying() || player.getPlayWhenReady())) return;
                if (!BtAudio.hasRoute(this)) {
                    // profile state fallback
                    try {
                        android.bluetooth.BluetoothAdapter a =
                            android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                        if (a == null) return;
                        int st = a.getProfileConnectionState(
                            android.bluetooth.BluetoothProfile.A2DP);
                        if (st != android.bluetooth.BluetoothProfile.STATE_CONNECTED) return;
                    } catch (Exception ignored) {
                        return;
                    }
                }
                String url = sp.getString(BluetoothAutoPlayPlugin.KEY_URL, "");
                if (url == null || url.isEmpty()) {
                    android.util.Log.i("RadioWatch", "watch probe — no last URL");
                    return;
                }
                android.util.Log.i("RadioWatch", "watch probe — A2DP up, start play");
                setIntendedPlaying(true);
                playLastWhenBtReady();
            } catch (Exception e) {
                android.util.Log.e("RadioWatch", "watch probe", e);
            }
        }, 2500);
    }

    private void playLast() {
        SharedPreferences p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
        String url = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "");
        String name = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O");
        if (name != null && !name.isEmpty()) currentName = name;
        playUrl(url);
    }

    private void playUrl(String url) {
        if (url == null || url.isEmpty() || player == null) return;
        // НЕ форсуємо https — багато потоків лише http
        try {
            long now = System.currentTimeMillis();
            // той самий URL уже грає / щойно стартував — не перезапускати (BT double-play)
            String currentUri = null;
            if (player.getCurrentMediaItem() != null
                    && player.getCurrentMediaItem().localConfiguration != null) {
                currentUri = player.getCurrentMediaItem().localConfiguration.uri.toString();
            }
            // Дубль лише якщо ЦЕЙ САМИЙ uri уже в плеєрі і реально грає/стартує.
            // lastPlayedUrl НЕ порівнюємо — інакше швидкий A→B→A або зміна
            // під час буфера блокує новий play.
            boolean sameAsCurrent = currentUri != null && url.equals(currentUri);
            if (sameAsCurrent
                    && (player.isPlaying() || player.getPlayWhenReady())
                    && (now - lastPlayMs < 4000)) {
                android.util.Log.d("RadioWatch", "playUrl skip duplicate: " + url);
                currentPlayUrl = url;
                try {
                    getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .edit().putString(BluetoothAutoPlayPlugin.KEY_URL, url).commit();
                } catch (Exception ignored) {}
                notifyForeground();
                return;
            }

            // Скасувати відкладений reconnect старого URL (головне при швидкому skip)
            if (reconnectHandler != null) {
                reconnectHandler.removeCallbacksAndMessages(null);
            }
            reconnectAttempt = 0;

            if (!requestFocus()) {
                android.util.Log.w("RadioWatch", "audio focus not granted");
            }
            lastPlayMs = now;
            lastPlayedUrl = url;
            currentPlayUrl = url;
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
            } catch (Exception ignored) {}
            boolean localMode = isLocalMode();
            // Радіо: трек ще не відомий (прийде з ICY/onMediaMetadataChanged) — чистимо.
            // Локальна музика: артист/назва вже відомі заздалегідь (playLocal/skip їх щойно
            // записали) — не затирати тим самим стартом відтворення.
            if (!localMode) {
                lastTrackTitle = "";
            }
            // Критично: те що граємо = source of truth для reconnect (skip/UI/BT)
            SharedPreferences.Editor ed = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .edit()
                .putString(BluetoothAutoPlayPlugin.KEY_URL, url);
            if (!localMode) {
                ed.putString(BluetoothAutoPlayPlugin.KEY_TRACK, "");
            }
            ed.commit();
            MediaItem item = new MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(new MediaMetadata.Builder()
                    .setTitle(currentName != null ? currentName : "Radio S O")
                    .setArtist(currentName != null ? currentName : "Radio S O")
                    .setDisplayTitle(currentName != null ? currentName : "Radio S O")
                    .setSubtitle("Radio S O")
                    .build())
                .build();
            // Заміна потоку без stop()/clear — ExoPlayer сам кине попередній load
            player.setMediaItem(item, /* resetPosition= */ true);
            player.prepare();
            player.setVolume(1f);
            BtAudio.preferA2dp(this, player);
            player.setPlayWhenReady(true);
            if (isLocalMode()) armPositionTicker();
            // 0.9.51: reported playing тільки з onIsPlayingChanged — не раніше
            loadStationArtAsync();
            notifyUiStatus(getString(R.string.connecting), 0);
            bufferingTicks = 0;
            if (!isLocalMode()) armSilenceWatch(); // лише для радіо-потоків
            notifyForeground();
        } catch (Exception e) {
            android.util.Log.e("RadioWatch", "playUrl failed: " + url, e);
            try {
                if (player != null) player.setPlayWhenReady(false);
            } catch (Exception ignored) {}
            notifyForeground();
        }
    }


    private void publishTrack(String title) {
        if (title == null) return;
        title = title.replace("StreamTitle=", "").replace("'", "").trim();
        if (title.isEmpty() || title.equalsIgnoreCase(currentName)) return;
        if (title.equals(lastTrackTitle)) return;
        lastTrackTitle = title;
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .edit().putString(BluetoothAutoPlayPlugin.KEY_TRACK, title).apply();
        Intent i = new Intent(ACTION_TRACK_META);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_TRACK, title);
        sendBroadcast(i);
        applySessionMetadata(currentName, title);
        notifyForeground();
    }

    private android.os.Handler reconnectHandler;
    private int reconnectAttempt = 0;
    private long reconnectWindowStart = 0L;
    private long lastReconnectTriggerMs = 0L;
    /** Дебаунс подвійного ACTION_BT (ACL + A2DP STATE_CONNECTED). */
    private long lastBtActionMs = 0L;
    private static final long RECONNECT_DEBOUNCE_MS = 1500L;
    /** Скільки часу після BT-конекту вважаємо, що ще триває апаратний handoff
     *  (магнітола/колонка можуть на мить забрати audio focus чи знімати маршрут) —
     *  протягом цього вікна ігноруємо NOISY та transient focus loss, щоб не
     *  ставити паузу через "тишу після перемикання на BT". Було 10с — на
     *  повільніших головних пристроях цього не завжди вистачало. */
    private static final long BT_HANDOFF_WINDOW_MS = 15000L;
    // timings → ReconnectPolicy


    /** URL для reconnect: спочатку те що грали, інакше prefs */
    private String resolveReconnectUrl() {
        try {
            if (player != null && player.getCurrentMediaItem() != null
                    && player.getCurrentMediaItem().localConfiguration != null) {
                String u = player.getCurrentMediaItem().localConfiguration.uri.toString();
                if (u != null && !u.isEmpty()) return u;
            }
        } catch (Exception ignored) {}
        if (currentPlayUrl != null && !currentPlayUrl.isEmpty()) return currentPlayUrl;
        try {
            String u = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .getString(BluetoothAutoPlayPlugin.KEY_URL, "");
            if (u != null && !u.isEmpty()) return u;
        } catch (Exception ignored) {}
        return "";
    }

    private boolean hasInternet() {
        try {
            if (connectivityManager == null) {
                connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            }
            if (connectivityManager == null) return false;
            Network[] nets = connectivityManager.getAllNetworks();
            for (Network n : nets) {
                NetworkCapabilities c = connectivityManager.getNetworkCapabilities(n);
                // VALIDATED — мережа реально перевірена системою на вихід в інтернет,
                // а не просто "заявляє" про це (рятує від капчальних порталів/мертвого Wi-Fi)
                if (c != null
                        && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void scheduleReconnect() {
        if (isLocalMode()) return;
        if (!PlaybackPrefs.isIntended(this)) return;
        if (player != null && player.isPlaying()) {
            reconnectAttempt = 0;
            reconnectWindowStart = 0L;
            return;
        }
        if (reconnectHandler == null) {
            reconnectHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        reconnectHandler.removeCallbacksAndMessages(null);
        long now = System.currentTimeMillis();
        if (reconnectWindowStart == 0L) reconnectWindowStart = now;
        long elapsed = now - reconnectWindowStart;
        if (ReconnectPolicy.windowExpired(elapsed)) {
            notifyUiStatus(getString(R.string.status_no_network), reconnectAttempt);
            // Повільний heartbeat після 5хв вікна: мережа може «бути», але не працювати
            // (onAvailable тоді не прийде). Не чіпаємо ReconnectPolicy — лише retry тут.
            final int attemptHb = reconnectAttempt;
            reconnectHandler.postDelayed(() -> {
                if (player == null) return;
                if (isLocalMode()) return;
                if (!PlaybackPrefs.isIntended(RadioWatchService.this)) return;
                if (player.isPlaying()) {
                    reconnectAttempt = 0;
                    reconnectWindowStart = 0L;
                    notifyUiStatus(getString(R.string.playing), 0);
                    return;
                }
                if (!hasInternet()) {
                    scheduleReconnect();
                    return;
                }
                String url = resolveReconnectUrl();
                if (url != null && !url.isEmpty()) {
                    reconnectAttempt = attemptHb + 1;
                    lastPlayedUrl = "";
                    lastPlayMs = 0;
                    playUrl(url);
                }
                scheduleReconnect();
            }, 90_000L);
            return;
        }
        long delay = ReconnectPolicy.nextDelayMs(elapsed, reconnectAttempt);
        final int attempt = reconnectAttempt;
        reconnectHandler.postDelayed(() -> {
            if (player == null) return;
            if (!PlaybackPrefs.isIntended(RadioWatchService.this)) return;
            if (player.isPlaying()) {
                reconnectAttempt = 0;
                reconnectWindowStart = 0L;
                notifyUiStatus(getString(R.string.playing), 0);
                return;
            }
            if (!hasInternet()) {
                notifyUiStatus(getString(R.string.status_no_network), attempt + 1);
                scheduleReconnect();
                return;
            }
            String url = resolveReconnectUrl();
            android.util.Log.i("RadioWatch", "reconnect attempt " + attempt + " url=" + url);
            if (url != null && !url.isEmpty()) {
                reconnectAttempt = attempt + 1;
                lastPlayedUrl = "";
                lastPlayMs = 0;
                playUrl(url);
                scheduleReconnect();
            }
        }, delay);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Radio S O", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.notif_channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void notifyForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification() {
        boolean playing = player != null && player.isPlaying();
        boolean btWatch = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true);
        return RadioNotificationFactory.build(
            this, CHANNEL, RadioWatchService.class, MainActivity.class,
            playing, currentName, lastTrackTitle, stationArt, mediaSession, btWatch);
    }

    @Override
    public void onDestroy() {
        if (noisyRegistered) {
            try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
            noisyRegistered = false;
        }
        if (networkCallbackRegistered && connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
            networkCallbackRegistered = false;
        }
        if (reconnectHandler != null) {
            reconnectHandler.removeCallbacksAndMessages(null);
        }
        if (INSTANCE == this) INSTANCE = null;
        abandonFocus();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }


    private static boolean isCarClient(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("gearhead") || p.contains("projection")
                || p.contains("android.car") || p.contains("gms.car");
    }

    private void setAaActive(boolean active) {
        boolean was = false;
        try {
            was = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .getBoolean(BluetoothAutoPlayPlugin.KEY_AA_ACTIVE, false);
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_AA_ACTIVE, active).apply();
        } catch (Exception ignored) {}
        if (active && player != null) BtAudio.clearPreferred(player);
        // Перший bind Gearhead — перестворити AudioTrack (аналог тумблера «медіа»).
        if (active && !was) forceAudioRouteRefresh();
    }

    /** Recreate audio sink so a stuck A2DP preferred-device is dropped. */
    private void forceAudioRouteRefresh() {
        if (player == null) return;
        final boolean want = player.getPlayWhenReady()
                || player.isPlaying()
                || PlaybackPrefs.isIntended(this);
        mainHandler.post(() -> {
            try {
                if (player == null) return;
                BtAudio.clearPreferred(player);
                player.setAudioAttributes(
                    new androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false);
                if (want && player.getMediaItemCount() > 0) {
                    player.prepare();
                    player.setPlayWhenReady(true);
                    player.setVolume(1f);
                }
                android.util.Log.i("RadioWatch", "AA audio route refresh want=" + want);
            } catch (Exception e) {
                android.util.Log.w("RadioWatch", "forceAudioRouteRefresh", e);
            }
        });
    }

    @Override
    public BrowserRoot onGetRoot(@androidx.annotation.NonNull String clientPackageName, int clientUid,
                                 @androidx.annotation.Nullable android.os.Bundle rootHints) {
        if (isCarClient(clientPackageName)) {
            setAaActive(true);
            android.util.Log.i("RadioWatch", "AA client: " + clientPackageName);
        }
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(@androidx.annotation.NonNull String parentId,
                               @androidx.annotation.NonNull Result<java.util.List<MediaBrowserCompat.MediaItem>> result) {
        java.util.List<MediaBrowserCompat.MediaItem> out = new java.util.ArrayList<>();
        if (!"root".equals(parentId)) { result.sendResult(out); return; }
        SharedPreferences p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
        try {
            if (isLocalMode()) {
                org.json.JSONArray uris = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_URIS, "[]"));
                org.json.JSONArray titles = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_TITLES, "[]"));
                org.json.JSONArray artists = new org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, "[]"));
                for (int i = 0; i < uris.length(); i++) {
                    String uri = uris.optString(i);
                    if (uri == null || uri.isEmpty()) continue;
                    String title = i < titles.length() ? titles.optString(i, "Local") : "Local";
                    String artist = i < artists.length() ? artists.optString(i, "") : "";
                    out.add(browseItem(uri, title, artist));
                }
            } else if ("temp".equals(p.getString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "radio"))) {
                org.json.JSONArray urls = new org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_URLS, "[]"));
                org.json.JSONArray names = new org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_NAMES, "[]"));
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i);
                    if (url == null || url.isEmpty()) continue;
                    String name = i < names.length() ? names.optString(i, "Station") : "Station";
                    out.add(browseItem(url, name, ""));
                }
            } else {
                org.json.JSONArray urls = new org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, "[]"));
                org.json.JSONArray names = new org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, "[]"));
                org.json.JSONArray genres = new org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, "[]"));
                org.json.JSONArray countries = new org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, "[]"));
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i);
                    if (url == null || url.isEmpty()) continue;
                    String name = i < names.length() ? names.optString(i, "Station") : "Station";
                    String g = i < genres.length() ? genres.optString(i, "") : "";
                    String c = i < countries.length() ? countries.optString(i, "") : "";
                    out.add(browseItem(url, name, (g + " · " + c).trim()));
                }
            }
        } catch (Exception e) {
            android.util.Log.w("RadioWatch", "children", e);
        }
        result.sendResult(out);
    }

    private static MediaBrowserCompat.MediaItem browseItem(String id, String title, String subtitle) {
        MediaDescriptionCompat d = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowserCompat.MediaItem(d, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        IBinder b = super.onBind(intent);
        return b;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        setAaActive(false);
        android.util.Log.i("RadioWatch", "onUnbind — AA flag cleared");
        return super.onUnbind(intent);
    }
}
