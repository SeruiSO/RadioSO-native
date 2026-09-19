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
    /** BT/profile gone — not user pause (auto-resume on next ACTION_BT). */
    public static final String ACTION_ROUTE_LOST = "com.seruiso.radio1.ROUTE_LOST";
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
    private long pausedByFocusAtMs = 0L;
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
    /** Відкладений ROUTE_LOST (BT handoff 3–5 с) */
    private Runnable routeLostRunnable;
    private int a2dpMissTicks = 0;
    /** Classic BT: чекаємо AVRCP PLAY з магнітоли; інакше автостарт (навушники). */
    private boolean awaitingHeadUnitPlay = false;
    private Runnable headUnitPlayWaitRunnable;
    private long playShieldUntilMs = 0L;
    private static final long HEADUNIT_PLAY_WAIT_MS = 4000L;
    private static final long PLAY_SHIELD_MS = 2500L;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered = false;
    private long networkLostAtMs = 0L;
    private android.os.Handler silenceHandler;
    private Runnable silenceCheck;
    private int bufferingTicks = 0;
    /** false до першого реального isPlaying по current URL */
    private boolean hasEverPlayedThisUrl = false;
    /** ms коли почали load цього URL (startup grace) */
    private long streamStartMs = 0L;
    private long lastBufferedMs = 0L;
    private long pendingSeekMs = -1L;
    private android.os.Handler positionHandler;
    private Runnable positionTicker;
    private int positionTickCount = 0;
    /** 0.13.77: відкладені watch-probe — скасовуємо на user pause. */
    private final java.util.ArrayList<Runnable> probeRunnables = new java.util.ArrayList<>();


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
                    1_000,   /* bufferForPlaybackMs — швидший перший звук (було 2500) */
                    1_000    /* bufferForPlaybackAfterRebufferMs */
                )
                .build();

        // М'якші HTTP-таймаути + User-Agent (деякі IPFM/ICY чутливі до дефолтного UA).
        // HTTP для радіо + DefaultDataSource зверху — щоб локальні content:// і file:// теж грали.
        java.util.Map<String, String> httpHeaders = new java.util.HashMap<>();
        httpHeaders.put("Icy-MetaData", "1");
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("RadioSO/1.0 (Linux; Android) ExoPlayer")
                .setDefaultRequestProperties(httpHeaders)
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
                return RadioWatchService.this.withinBtSettle();
            }

            /** Уже реально граємо / стартуємо — не смикати потік ще раз. */
            private boolean alreadyOutputting() {
                try {
                    if (player == null || player.getCurrentMediaItem() == null) return false;
                    if (RadioWatchService.this.withinBtSettle()) return true;
                    return player.isPlaying()
                        && player.getPlaybackState() == Player.STATE_READY;
                } catch (Exception e) {
                    return false;
                }
            }

            /**
             * 0.13.74: AVRCP PLAY від магнітоли часто приходить КОЛИ вже 1–2 с
             * грає в колонках. Повторний play()/prepare = реконект станції = пінок.
             * Якщо вже outputting — тільки intended, без super.play() / playUrl.
             * Якщо на паузі — звичайний resume.
             */
            private void playFromSessionSmart() {
                setUserPausedWhileBt(false);
                PlaybackPrefs.setPauseReason(RadioWatchService.this, PlaybackPrefs.REASON_NONE);
                setIntendedPlaying(true);
                try {
                    getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
                } catch (Exception ignored) {}
                // PLAY від магнітоли в вікні очікування — єдиний старт handoff
                if (awaitingHeadUnitPlay) {
                    awaitingHeadUnitPlay = false;
                    if (headUnitPlayWaitRunnable != null) {
                        mainHandler.removeCallbacks(headUnitPlayWaitRunnable);
                        headUnitPlayWaitRunnable = null;
                    }
                    playShieldUntilMs = System.currentTimeMillis() + PLAY_SHIELD_MS;
                    android.util.Log.i("RadioWatch", "session PLAY — headUnit wait satisfied");
                    try { if (player != null) player.setVolume(1f); } catch (Exception ignored) {}
                    if (alreadyPlayingLastUrl()) {
                        try {
                            if (player != null) player.setPlayWhenReady(true);
                        } catch (Exception ignored) {}
                        try { notifyUiStatus(getString(R.string.playing), 0); } catch (Exception ignored) {}
                        return;
                    }
                    playLast();
                    return;
                }
                // Повторний PLAY одразу після нашого старту — ігнор (анти-пинок)
                if (System.currentTimeMillis() < playShieldUntilMs) {
                    android.util.Log.i("RadioWatch", "session PLAY ignored — play shield");
                    try {
                        if (player != null) {
                            player.setVolume(1f);
                            player.setPlayWhenReady(true);
                        }
                    } catch (Exception ignored) {}
                    return;
                }
                if (alreadyOutputting()) {
                    android.util.Log.i("RadioWatch",
                        "session PLAY ignored — already playing / BT settle");
                    try {
                        if (player != null) {
                            player.setVolume(1f);
                            player.setPlayWhenReady(true);
                        }
                    } catch (Exception ignored) {}
                    return;
                }
                super.play();
            }

            @Override
            public void play() {
                playFromSessionSmart();
            }

            private void userPauseFromSession() {
                cancelHeadUnitPlayWait();
                pausedByFocusLoss = false;
                setIntendedPlaying(false);
                PlaybackPrefs.setPauseReason(RadioWatchService.this, PlaybackPrefs.REASON_USER);
                try {
                    getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false).apply();
                } catch (Exception ignored) {}
                markUserPausedIfBtConnected();
                try { writeActuallyPlaying(false); } catch (Exception ignored) {}
                android.util.Log.i("RadioWatch", "session PAUSE — intended cleared + userPausedWhileBt");
                super.pause();
                try { notifyForeground(); notifyUiPlayback(false); } catch (Exception ignored) {}
            }

            @Override
            public void pause() {
                if (withinBtSettle()) {
                    android.util.Log.i("RadioWatch", "session pause ignored — BT settle");
                    return;
                }
                userPauseFromSession();
            }

            @Override
            public void setPlayWhenReady(boolean playWhenReady) {
                if (playWhenReady) {
                    setUserPausedWhileBt(false);
                    playFromSessionSmart();
                    return;
                }
                if (withinBtSettle()) {
                    android.util.Log.i("RadioWatch", "session pause(pwr) ignored — BT settle");
                    return;
                }
                userPauseFromSession();
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
                        if (pausedByFocusLoss) {
                            android.util.Log.i("RadioWatch", "onAvailable during focus loss — wait GAIN");
                            mainHandler.postDelayed(() -> {
                                if (player == null) return;
                                if (!pausedByFocusLoss) return;
                                SharedPreferences sp2 = getSharedPreferences(
                                                BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                                if (!sp2.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)) return;
                                if (player.isPlaying()) return;
                                if (!requestFocus()) return;
                                attemptReconnect("network-available-focus-watchdog", true);
                            }, 2500);
                            return;
                        }
                        // Слабкий нет: intended + не пауза пальцем → завжди пробуємо
                        if (!PlaybackPrefs.isIntended(RadioWatchService.this)) {
                            android.util.Log.i("RadioWatch", "onAvailable skip — not intended");
                            return;
                        }
                        if (PlaybackPrefs.REASON_USER.equals(
                                PlaybackPrefs.getPauseReason(RadioWatchService.this))) {
                            android.util.Log.i("RadioWatch", "onAvailable skip — user pause");
                            return;
                        }
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
        if (PlaybackPrefs.REASON_USER.equals(PlaybackPrefs.getPauseReason(this))) {
            android.util.Log.i("RadioWatch", "attemptReconnect(" + reason + ") skip — user pause");
            return;
        }
        if (withinBtSettle()) {
            android.util.Log.i("RadioWatch", "attemptReconnect(" + reason + ") skip — BT settle");
            return;
        }
        if (player != null && player.isPlaying()
                && player.getPlaybackState() == Player.STATE_READY) {
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
        try {
            notifyUiStatus(getString(R.string.status_reconnect), reconnectAttempt + 1);
        } catch (Exception ignored) {}
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
                    .getLong(BluetoothAutoPlayPlugin.KEY_LAST_A2DP_MS, 0L);
                if (System.currentTimeMillis() - lastBtFocus < BT_HANDOFF_WINDOW_MS) {
                    android.util.Log.i("RadioWatch", "focus loss ignored — BT/AA handoff window");
                    break;
                }
                // відео / дзвінок / інший плеєр — пауза; resume на GAIN якщо intendedPlaying
                if (player.isPlaying() || player.getPlayWhenReady()) {
                    pausedByFocusLoss = true;
                    pausedByFocusAtMs = System.currentTimeMillis();
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
                    if (!pausedByFocusLoss) return;
                    SharedPreferences spGain = getSharedPreferences(
                        BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                    boolean wantPlay = spGain.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false);
                    if (!wantPlay) {
                        pausedByFocusLoss = false;
                        pausedByFocusAtMs = 0L;
                        return;
                    }
                    long pausedFor = pausedByFocusAtMs > 0L
                            ? System.currentTimeMillis() - pausedByFocusAtMs : 0L;
                    pausedByFocusLoss = false;
                    pausedByFocusAtMs = 0L;
                    int stGain = player.getPlaybackState();
                    boolean stale = pausedFor > 8_000L
                            || stGain == Player.STATE_IDLE
                            || stGain == Player.STATE_ENDED
                            || player.getCurrentMediaItem() == null;
                    if (stale) {
                        attemptReconnect("focus-gain-stale", true);
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
                    if (player == null) {
                        return;
                    }
                    SharedPreferences sp = getSharedPreferences(
                        BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                    // Стоп / знятий intended — не чіпаємо
                    if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)
                            || !PlaybackPrefs.isIntended(RadioWatchService.this)) {
                        bufferingTicks = 0;
                        return;
                    }
                    // Лише пауза пальцем — без авто-resume
                    if (PlaybackPrefs.REASON_USER.equals(
                            PlaybackPrefs.getPauseReason(RadioWatchService.this))) {
                        bufferingTicks = 0;
                        return;
                    }
                    if (withinBtSettle()) {
                        bufferingTicks = 0;
                        return;
                    }
                    int st = player.getPlaybackState();
                    boolean playing = player.isPlaying();
                    long buf = 0L;
                    try { buf = player.getTotalBufferedDuration(); } catch (Exception ignored) {}
                    long now = System.currentTimeMillis();
                    long sinceStart = streamStartMs > 0L ? (now - streamStartMs) : 0L;

                    if (playing && st == Player.STATE_READY && buf >= 400L) {
                        hasEverPlayedThisUrl = true;
                    }

                    boolean bufferGrowing = buf > lastBufferedMs + 50L;
                    lastBufferedMs = Math.max(lastBufferedMs, buf);

                    // Startup grace ~20s: не soft/hard лише через BUFFERING на першому старті
                    final long STARTUP_GRACE_MS = 20_000L;
                    boolean inStartup = !hasEverPlayedThisUrl && sinceStart < STARTUP_GRACE_MS;

                    if (playing && st == Player.STATE_READY && !isLocalMode()) {
                        if (bufferingTicks > 0) {
                            try { notifyUiStatus(getString(R.string.playing), 0); } catch (Exception ignored) {}
                        }
                        bufferingTicks = 0;
                        try {
                            if (PlaybackPrefs.REASON_NETWORK.equals(
                                    PlaybackPrefs.getPauseReason(RadioWatchService.this))) {
                                PlaybackPrefs.setPauseReason(RadioWatchService.this,
                                    PlaybackPrefs.REASON_NONE);
                            }
                        } catch (Exception ignored) {}
                    } else if (st == Player.STATE_BUFFERING && (bufferGrowing || inStartup)) {
                        bufferingTicks = 0;
                        if (inStartup || buf < 1500L) {
                            try {
                                notifyUiStatus(getString(R.string.status_buffering), reconnectAttempt);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        // Stall: idle/ended, buffering без прогресу, zombie, або intended але не грає
                        boolean silentZombie = st == Player.STATE_READY && playing && buf < 400L;
                        boolean intendedNotPlaying = !playing
                                && !inStartup
                                && st != Player.STATE_BUFFERING;
                        boolean stalled = silentZombie
                                || st == Player.STATE_IDLE
                                || st == Player.STATE_ENDED
                                || (st == Player.STATE_BUFFERING && !bufferGrowing && !inStartup)
                                || intendedNotPlaying;

                        if (stalled) {
                            bufferingTicks++;
                            // Тік 2с: soft ~8с (4), hard ~16с (8); далі цикл soft→hard
                            int softAt = hasEverPlayedThisUrl ? 4 : 6;
                            int hardAt = hasEverPlayedThisUrl ? 8 : 12;

                            if (bufferingTicks == 1) {
                                try {
                                    notifyUiStatus(getString(R.string.status_buffering), reconnectAttempt);
                                } catch (Exception ignored) {}
                            } else if (bufferingTicks == Math.max(2, softAt / 2)) {
                                try {
                                    notifyUiStatus(getString(R.string.status_reconnect), reconnectAttempt);
                                } catch (Exception ignored) {}
                            }

                            if (bufferingTicks == softAt) {
                                android.util.Log.w("RadioWatch",
                                    "silence soft → reconnect (ticks=" + bufferingTicks
                                        + " everPlayed=" + hasEverPlayedThisUrl
                                        + " sinceStart=" + sinceStart + "ms buf=" + buf + ")");
                                try {
                                    PlaybackPrefs.setPauseReason(RadioWatchService.this,
                                        PlaybackPrefs.REASON_NETWORK);
                                } catch (Exception ignored) {}
                                attemptReconnect("buffer-soft", false);
                            } else if (bufferingTicks >= hardAt) {
                                android.util.Log.w("RadioWatch",
                                    "silence hard → reconnect (ticks=" + bufferingTicks
                                        + " everPlayed=" + hasEverPlayedThisUrl + ")");
                                bufferingTicks = 0; // цикл далі: знову soft→hard
                                lastPlayedUrl = "";
                                lastPlayMs = 0;
                                hasEverPlayedThisUrl = false;
                                streamStartMs = now;
                                lastBufferedMs = 0L;
                                try {
                                    PlaybackPrefs.setPauseReason(RadioWatchService.this,
                                        PlaybackPrefs.REASON_NETWORK);
                                } catch (Exception ignored) {}
                                attemptReconnect("buffer-hard", true);
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("RadioWatch", "silenceCheck", e);
                } finally {
                    // Watchdog НІКОЛИ не вмирає сам (поки не знімуть armSilenceWatch)
                    if (silenceHandler != null && silenceCheck != null) {
                        silenceHandler.postDelayed(silenceCheck, 2000);
                    }
                }
            }
        };
        silenceHandler.postDelayed(silenceCheck, 2000);
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

    private boolean isUserPausedWhileBt() {
        try {
            return getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .getBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false);
        } catch (Exception e) { return false; }
    }

    private void setUserPausedWhileBt(boolean v) {
        try {
            SharedPreferences.Editor ed = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
                .putBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, v);
            if (!v) ed.putLong("userPausedWhileBtAt", 0L);
            ed.apply();
        } catch (Exception ignored) {}
    }

    private void cancelWatchProbes() {
        for (Runnable r : probeRunnables) {
            try { mainHandler.removeCallbacks(r); } catch (Exception ignored) {}
        }
        probeRunnables.clear();
    }

    /** Пауза при живому BT — блокує ACTION_BT / probe до реального disconnect. */
    private void markUserPausedIfBtConnected() {
        boolean bt = false;
        try {
            bt = BtAudio.hasRoute(this);
            if (!bt) {
                android.bluetooth.BluetoothAdapter a =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (a != null && a.isEnabled()) {
                    int pst = a.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP);
                    int hst = a.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET);
                    bt = pst == android.bluetooth.BluetoothProfile.STATE_CONNECTED
                        || hst == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
                }
            }
        } catch (Exception ignored) {}
        if (bt) {
            setUserPausedWhileBt(true);
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putLong("userPausedWhileBtAt", System.currentTimeMillis()).apply();
            } catch (Exception ignored) {}
            cancelWatchProbes();
            cancelBtTicks();
            android.util.Log.i("RadioWatch", "userPausedWhileBt=true (pause, BT still up)");
        } else {
            setUserPausedWhileBt(false);
        }
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

        if (ACTION_ROUTE_LOST.equals(action)) {
            scheduleRouteLostGrace("receiver");
            return START_STICKY;
        }

        if (ACTION_PAUSE.equals(action) || ACTION_NOTIF_PAUSE.equals(action)) {
            forceStopPlayback("USER_PAUSE");
            return START_STICKY;
        }

        if (ACTION_BT.equals(action)) {
            cancelRouteLostGrace();
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
            // 0.13.78: CONNECTED від системи = нове/повторне підключення профілю.
            // Завжди автостарт; userPaused більше НЕ блокує ACTION_BT назавжди
            // (інакше після паузи без «чистого» DISCONNECT автостарт мертвий).
            // Захист від паузи при живому BT: cancelWatchProbes + KEY_PLAY=false +
            // короткий debounce лише якщо CONNECTED прийшов одразу після паузи (<3с).
            long pausedAt = spBt.getLong("userPausedWhileBtAt", 0L);
            boolean userPaused = spBt.getBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false);
            long nowBt2 = System.currentTimeMillis();
            if (userPaused && pausedAt > 0L && (nowBt2 - pausedAt) < 3000L) {
                android.util.Log.i("RadioWatch",
                    "ACTION_BT ignored — userPaused <3s ago (profile blip)");
                notifyForeground();
                return START_STICKY;
            }
            setUserPausedWhileBt(false);
            PlaybackPrefs.setPauseReason(this, PlaybackPrefs.REASON_NONE);
            setIntendedPlaying(true);
            ignoreNoisyUntilMs = System.currentTimeMillis() + 8000L;
            try { notifyUiStatus(getString(R.string.connecting), 0); } catch (Exception ignored) {}
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
            } catch (Exception ignored) {}
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putLong(BluetoothAutoPlayPlugin.KEY_LAST_A2DP_MS, System.currentTimeMillis()).apply();
            } catch (Exception ignored) {}
            if (BtAudio.isAndroidAutoActive(this)) {
                cancelHeadUnitPlayWait();
                if (player != null) BtAudio.clearPreferred(player);
                playLast();
            } else {
                try {
                    getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_AA_ACTIVE, false).apply();
                } catch (Exception ignored) {}
                // Classic: не стартувати на speaker — чекати PLAY 4с або timeout (навушники)
                beginHeadUnitPlayWait();
                armA2dpRouteWatch();
            }
            return START_STICKY;
        }

        if (ACTION_PLAY.equals(action) || ACTION_NOTIF_PLAY.equals(action)) {
            cancelHeadUnitPlayWait();
            setUserPausedWhileBt(false);
            setIntendedPlaying(true);
            playShieldUntilMs = System.currentTimeMillis() + PLAY_SHIELD_MS;
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
            setUserPausedWhileBt(false);
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
                tickLocalPosition();
                if (player != null && isLocalMode() && (player.isPlaying() || player.getPlayWhenReady())) {
                    positionHandler.postDelayed(this, 1000);
                }
            }
        };
        positionHandler.post(positionTicker);
    }

    /**
     * Легкий тик для UI-позиції під час локального відтворення: broadcast щосекунди
     * (плавний seekbar), але запис у SharedPreferences (диск) — лише раз на 3 тики (~3с),
     * бо похибка відновлення позиції після рестарту сервісу в 1-2с некритична.
     * Менше диск-I/O і менше broadcast'ів → плавніше й економніше по батареї.
     */
    private void tickLocalPosition() {
        if (player == null || !isLocalMode()) return;
        try {
            long pos = Math.max(0, player.getCurrentPosition());
            long dur = player.getDuration();
            if (dur < 0 || dur == androidx.media3.common.C.TIME_UNSET) dur = 0;
            positionTickCount++;
            if (positionTickCount % 3 == 0) {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
                    .putLong("localPositionMs", pos)
                    .putLong("localDurationMs", dur)
                    .apply();
            }
            Intent i = new Intent(ACTION_PLAYBACK_UI);
            i.setPackage(getPackageName());
            i.putExtra("playing", player.isPlaying());
            i.putExtra("positionMs", pos);
            i.putExtra("durationMs", dur);
            sendBroadcast(i);
        } catch (Exception ignored) {}
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

    private boolean withinBtSettle() {
        long nowS = System.currentTimeMillis();
        if (nowS < ignoreNoisyUntilMs) return true;
        long lastBt = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .getLong(BluetoothAutoPlayPlugin.KEY_LAST_A2DP_MS, 0L);
        long ago = nowS - lastBt;
        return ago >= 0 && ago < BT_HANDOFF_WINDOW_MS;
    }

    private boolean alreadyPlayingLastUrl() {
        if (player == null) return false;
        if (withinBtSettle() && player.getCurrentMediaItem() != null) {
            try {
                String want0 = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .getString(BluetoothAutoPlayPlugin.KEY_URL, "");
                String cur0 = player.getCurrentMediaItem().localConfiguration.uri.toString();
                if (want0 != null && want0.equals(cur0)) return true;
            } catch (Exception ignored) {}
        }
        if (!player.isPlaying() || player.getPlaybackState() != Player.STATE_READY) return false;
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
        // VoIP (WhatsApp/Viber) через BT: A2DP→SCO може дати pause.
        // Не затираємо intended при soft focus, інакше AUDIOFOCUS_GAIN не відновить ефір.
        // ROUTE_LOST (disconnect) ≠ USER_PAUSE (палець) — інакше «вийшов→сів» ламається.
        boolean asUser = "USER_PAUSE".equals(reason) || "ACTION_PAUSE".equals(reason);
        if (asUser && pausedByFocusLoss) {
            boolean stillBt = false;
            try {
                android.bluetooth.BluetoothAdapter a =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                stillBt = a != null && a.isEnabled() && BtAudio.hasRoute(this);
            } catch (Exception ignored) {}
            if (stillBt) {
                android.util.Log.i("RadioWatch",
                    "forceStopPlayback soft — focus loss + BT still up (likely call/SCO)");
                if (player != null) {
                    try {
                        player.setPlayWhenReady(false);
                        player.pause();
                    } catch (Exception e) {
                        android.util.Log.w("RadioWatch", "forceStop soft player", e);
                    }
                }
                PlaybackPrefs.setPauseReason(this, PlaybackPrefs.REASON_FOCUS);
                writeActuallyPlaying(false);
                notifyForeground();
                notifyUiPlayback(false);
                try { notifyUiStatus(getString(R.string.pause), 0); } catch (Exception ignored) {}
                return;
            }
            android.util.Log.i("RadioWatch",
                "forceStopPlayback full — focus loss but BT gone (left car during call)");
        }
        pausedByFocusLoss = false;
        ignoreNoisyUntilMs = 0L;
        sawA2dpAfterBtStart = false;
        cancelBtTicks();
        cancelWatchProbes();

        boolean routeLost = "ROUTE_LOST".equals(reason)
            || (reason != null && (reason.startsWith("NOISY")
                || reason.contains("DISCONNECTED")
                || reason.contains("a2dp-route-lost")));
        boolean userPause = asUser && !routeLost;

        if (routeLost) {
            setUserPausedWhileBt(false);
            PlaybackPrefs.setPauseReason(this, PlaybackPrefs.REASON_ROUTE);
            clearPlaybackIntent();
        } else if (userPause) {
            PlaybackPrefs.setPauseReason(this, PlaybackPrefs.REASON_USER);
            clearPlaybackIntent();
            markUserPausedIfBtConnected();
        } else {
            clearPlaybackIntent();
            if (reason != null && reason.startsWith("NOISY")) {
                markUserPausedIfBtConnected();
            }
        }
        try {
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false).apply();
        } catch (Exception ignored) {}
        if (player != null) {
            try {
                if (isLocalMode()) {
                    try {
                        long pos = player.getCurrentPosition();
                        if (pos > 0L) {
                            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                                .edit().putLong("localPositionMs", pos).apply();
                        }
                    } catch (Exception ignored) {}
                }
                player.setPlayWhenReady(false);
                player.pause();
            } catch (Exception e) {
                android.util.Log.w("RadioWatch", "forceStop player", e);
            }
        }
        writeActuallyPlaying(false);
        notifyForeground();
        notifyUiPlayback(false);
        try { notifyUiStatus(getString(R.string.pause), 0); } catch (Exception ignored) {}
    }


    private void cancelRouteLostGrace() {
        if (routeLostRunnable != null) {
            try { mainHandler.removeCallbacks(routeLostRunnable); } catch (Exception ignored) {}
            routeLostRunnable = null;
        }
    }

    /**
     * BT ACL/profile disconnect часто 1–3 с під час handoff телефону→авто.
     * Не forceStop одразу: soft-pause, intended лишається; якщо A2DP знову є — resume.
     */
    private void scheduleRouteLostGrace(String why) {
        android.util.Log.i("RadioWatch", "ROUTE_LOST grace start (" + why + ")");
        cancelRouteLostGrace();
        try {
            PlaybackPrefs.setPauseReason(this, PlaybackPrefs.REASON_ROUTE);
        } catch (Exception ignored) {}
        // intended НЕ чистимо — це не USER_STOP
        try {
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        } catch (Exception e) {
            android.util.Log.w("RadioWatch", "route grace soft pause", e);
        }
        try { writeActuallyPlaying(false); } catch (Exception ignored) {}
        try { notifyUiPlayback(false); } catch (Exception ignored) {}
        try { notifyUiStatus(getString(R.string.pause), 0); } catch (Exception ignored) {}

        final long GRACE_MS = 4_000L;
        routeLostRunnable = new Runnable() {
            @Override public void run() {
                routeLostRunnable = null;
                boolean back = false;
                try {
                    back = BtAudio.hasRoute(RadioWatchService.this)
                        || BtAudio.hasA2dpOutput(RadioWatchService.this);
                } catch (Exception ignored) {}
                if (back) {
                    android.util.Log.i("RadioWatch", "ROUTE_LOST grace cancelled — BT/A2DP back");
                    if (PlaybackPrefs.isIntended(RadioWatchService.this)) {
                        try {
                            requestFocus();
                            if (player != null) {
                                player.setVolume(1f);
                                player.setPlayWhenReady(true);
                            }
                            PlaybackPrefs.setPauseReason(RadioWatchService.this, PlaybackPrefs.REASON_NONE);
                            notifyUiPlayback(true);
                            notifyUiStatus(getString(R.string.playing), 0);
                        } catch (Exception e) {
                            android.util.Log.w("RadioWatch", "route grace resume", e);
                        }
                    }
                    return;
                }
                android.util.Log.i("RadioWatch", "ROUTE_LOST grace expired — real stop");
                forceStopPlayback("ROUTE_LOST");
            }
        };
        mainHandler.postDelayed(routeLostRunnable, GRACE_MS);
    }

    private void armA2dpRouteWatch() {
        if (routeWatchTick != null) {
            mainHandler.removeCallbacks(routeWatchTick);
            routeWatchTick = null;
        }
        sawA2dpAfterBtStart = BtAudio.hasA2dpOutput(this);
        a2dpMissTicks = 0;
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
                    if (a2dp) {
                        sawA2dpAfterBtStart = true;
                        a2dpMissTicks = 0;
                    } else if (sawA2dpAfterBtStart) {
                        a2dpMissTicks++;
                        // ~3 * 1.5s ≈ 4.5s без A2DP після того як уже бачили route
                        if (a2dpMissTicks >= 3) {
                            android.util.Log.i("RadioWatch", "A2DP lost confirmed after debounce");
                            a2dpMissTicks = 0;
                            scheduleRouteLostGrace("a2dp-watch");
                            routeWatchTick = null;
                            return;
                        }
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

    private void cancelHeadUnitPlayWait() {
        awaitingHeadUnitPlay = false;
        if (headUnitPlayWaitRunnable != null) {
            mainHandler.removeCallbacks(headUnitPlayWaitRunnable);
            headUnitPlayWaitRunnable = null;
        }
    }

    /**
     * Classic BT handoff: не грати на динамік тел.
     * 4 с чекаємо PLAY з магнітоли; якщо немає — автостарт (навушники).
     * Android Auto path не використовує це.
     */
    private void beginHeadUnitPlayWait() {
        cancelHeadUnitPlayWait();
        if (PlaybackPrefs.REASON_USER.equals(PlaybackPrefs.getPauseReason(this))) {
            android.util.Log.i("RadioWatch", "headUnit wait skip — user pause");
            return;
        }
        if (!PlaybackPrefs.isIntended(this)
                && !getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)) {
            setIntendedPlaying(true);
            try {
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true).apply();
            } catch (Exception ignored) {}
        }
        awaitingHeadUnitPlay = true;
        // Зупинити вивід на speaker тел, НЕ знімаючи intended (не USER pause)
        try {
            if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                player.setPlayWhenReady(false);
                android.util.Log.i("RadioWatch", "headUnit wait — paused phone output, await PLAY 4s");
            }
        } catch (Exception ignored) {}
        try { notifyUiStatus(getString(R.string.connecting), 0); } catch (Exception ignored) {}
        headUnitPlayWaitRunnable = () -> {
            headUnitPlayWaitRunnable = null;
            if (!awaitingHeadUnitPlay) return;
            awaitingHeadUnitPlay = false;
            if (PlaybackPrefs.REASON_USER.equals(PlaybackPrefs.getPauseReason(this))) {
                android.util.Log.i("RadioWatch", "headUnit timeout skip — user pause");
                return;
            }
            android.util.Log.i("RadioWatch", "headUnit wait timeout 4s — auto play (headphones)");
            playShieldUntilMs = System.currentTimeMillis() + PLAY_SHIELD_MS;
            try { if (player != null) player.setVolume(1f); } catch (Exception ignored) {}
            playLast();
        };
        mainHandler.postDelayed(headUnitPlayWaitRunnable, HEADUNIT_PLAY_WAIT_MS);
        android.util.Log.i("RadioWatch", "headUnit PLAY wait started 4s");
    }

    private void playLastWhenBtReady() {

        if (isUserPausedWhileBt()) {
            android.util.Log.i("RadioWatch", "playLastWhenBtReady skip — userPausedWhileBt");
            return;
        }
        final android.os.Handler h = mainHandler;
        if (btReadyTick != null) {
            h.removeCallbacks(btReadyTick);
            btReadyTick = null;
        }
        ignoreNoisyUntilMs = System.currentTimeMillis() + 8000L;
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
        // Не чекаємо жорстко TYPE_A2DP (частина магнітол — HFP/Headset only).
        // 0.13.73: 900 мс замість 500 — ACL уже не стартує play, тож A2DP/Headset
        // CONNECTED майже завжди приходить раніше; зайві 400 мс зменшують шанс
        // старту на динамік телефону без mute-хаків.
        btReadyTick = () -> {
            btReadyTick = null;
            if (alreadyPlayingLastUrl()) {
                try { if (player != null) { player.setVolume(1f); player.setPlayWhenReady(true); } } catch (Exception ignored) {}
                return;
            }
            try { if (player != null) player.setVolume(1f); } catch (Exception ignored) {}
            playLast();
        };
        h.postDelayed(btReadyTick, 900);
    }

    /** Cold boot / пропущений receiver. Скасовується на user pause. */
    private void scheduleWatchProbe() {
        cancelWatchProbes();
        final long[] delays = new long[] { 2500L, 6000L, 12000L };
        for (int i = 0; i < delays.length; i++) {
            final int attempt = i + 1;
            final long delay = delays[i];
            final Runnable r = new Runnable() {
                @Override public void run() {
                    probeRunnables.remove(this);
                    try {
                        SharedPreferences sp = getSharedPreferences(
                            BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE);
                        if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) return;
                        if (sp.getBoolean(BluetoothAutoPlayPlugin.KEY_USER_PAUSED_BT, false)) {
                            android.util.Log.i("RadioWatch",
                                "watch probe #" + attempt + " skip — userPausedWhileBt");
                            return;
                        }
                        // Після UI-паузи KEY_PLAY=false — не піднімати станцію знову
                        if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false)) {
                            android.util.Log.i("RadioWatch",
                                "watch probe #" + attempt + " skip — not intended");
                            return;
                        }
                        if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                            android.util.Log.i("RadioWatch",
                                "watch probe #" + attempt + " skip — already playing");
                            return;
                        }
                        boolean routeOk = BtAudio.hasRoute(RadioWatchService.this);
                        if (!routeOk) {
                            try {
                                android.bluetooth.BluetoothAdapter a =
                                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                                if (a == null) return;
                                int pst = a.getProfileConnectionState(
                                    android.bluetooth.BluetoothProfile.A2DP);
                                int hst = a.getProfileConnectionState(
                                    android.bluetooth.BluetoothProfile.HEADSET);
                                routeOk = pst == android.bluetooth.BluetoothProfile.STATE_CONNECTED
                                    || hst == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
                            } catch (Exception ignored) {
                                return;
                            }
                        }
                        if (!routeOk) {
                            android.util.Log.i("RadioWatch",
                                "watch probe #" + attempt + " — no A2DP/Headset yet");
                            return;
                        }
                        String url = sp.getString(BluetoothAutoPlayPlugin.KEY_URL, "");
                        if (url == null || url.isEmpty()) {
                            android.util.Log.i("RadioWatch",
                                "watch probe #" + attempt + " — no last URL");
                            return;
                        }
                        android.util.Log.i("RadioWatch",
                            "watch probe #" + attempt + " — route up, start play");
                        try { notifyUiStatus(getString(R.string.connecting), attempt); } catch (Exception ignored) {}
                        setIntendedPlaying(true);
                        playLastWhenBtReady();
                    } catch (Exception e) {
                        android.util.Log.e("RadioWatch", "watch probe #" + attempt, e);
                    }
                }
            };
            probeRunnables.add(r);
            mainHandler.postDelayed(r, delay);
        }
    }


    /**
     * Local (і радіо на паузі): той самий MediaItem уже в плеєрі — лише resume,
     * без setMediaItem(resetPosition) (інакше трек з 0:00).
     */
    private boolean tryResumeSameItem(String url) {
        if (player == null || url == null || url.isEmpty()) return false;
        try {
            if (player.getMediaItemCount() <= 0 || player.getCurrentMediaItem() == null
                    || player.getCurrentMediaItem().localConfiguration == null) {
                return false;
            }
            String cur = player.getCurrentMediaItem().localConfiguration.uri.toString();
            if (!url.equals(cur)) return false;
            // Вже грає — нічого не робити
                    if (withinBtSettle()) {
            return player.isPlaying() || player.getPlayWhenReady()
                    || player.getPlaybackState() != Player.STATE_IDLE;
        }
        if (player.isPlaying() && player.getPlaybackState() == Player.STATE_READY) {
            return true;
        }
        if (!isLocalMode()) return false;

            if (!requestFocus()) {
                android.util.Log.w("RadioWatch", "tryResumeSameItem: no audio focus");
            }
            // Підстрахування позиції для local після forceStop/pause
            if (isLocalMode()) {
                long pos = player.getCurrentPosition();
                long saved = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .getLong("localPositionMs", 0L);
                if (pos < 400L && saved > 400L) {
                    try { player.seekTo(saved); } catch (Exception ignored) {}
                }
            }
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            }
            player.setPlayWhenReady(true);
            player.play();
            if (isLocalMode()) armPositionTicker();
            writeActuallyPlaying(true);
            notifyForeground();
            notifyUiPlayback(true);
            android.util.Log.i("RadioWatch", "resume same item (no reset): " + url);
            return true;
        } catch (Exception e) {
            android.util.Log.w("RadioWatch", "tryResumeSameItem", e);
            return false;
        }
    }

    private void playLast() {
        SharedPreferences p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
        String url = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "");
        String name = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O");
        if (name != null && !name.isEmpty()) currentName = name;
        if (tryResumeSameItem(url)) return;
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
            // Той самий URI на паузі — resume без setMediaItem(reset) (local 0:00 bug)
            if (sameAsCurrent && tryResumeSameItem(url)) {
                currentPlayUrl = url;
                lastPlayMs = now;
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
            hasEverPlayedThisUrl = false;
            streamStartMs = System.currentTimeMillis();
            lastBufferedMs = 0L;
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
    /** Focus LOSS ігноруємо коротше: 6 с достатньо для handoff, але дзвінок
     *  одразу після сідання вже має паузити радіо. NOISY settle — окремо (4 с). */
    private static final long BT_HANDOFF_WINDOW_MS = 8000L;
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
        try { RadioSoWidget.refresh(this); } catch (Exception ignored) {}
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
        // Зупинити self-rescheduling таймери / probes / art download (батарея)
        if (silenceHandler != null) {
            silenceHandler.removeCallbacksAndMessages(null);
        }
        if (positionHandler != null) {
            positionHandler.removeCallbacksAndMessages(null);
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        cancelWatchProbes();
        cancelBtTicks();
        if (artConn != null) {
            try { artConn.disconnect(); } catch (Exception ignored) {}
            artConn = null;
        }
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
