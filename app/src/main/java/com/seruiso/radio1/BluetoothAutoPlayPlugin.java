package com.seruiso.radio1;

public class BluetoothAutoPlayPlugin {
    public static final String PREFS = "radio_autoplay";
    public static final String KEY_URL = "lastStationUrl";
    public static final String KEY_NAME = "lastStationName";
    /** Намір: користувач/автостарт хоче playback (resume після focus/мережі). */
    public static final String KEY_PLAY = "intendedPlaying";
    public static final String KEY_QUEUE_URLS = "queueUrls";
    public static final String KEY_QUEUE_NAMES = "queueNames";
    public static final String KEY_QUEUE_INDEX = "queueIndex";
    public static final String KEY_QUEUE_FAVICONS = "queueFavicons";
    public static final String KEY_QUEUE_GENRES = "queueGenres";
    public static final String KEY_QUEUE_COUNTRIES = "queueCountries";
    public static final String KEY_BT_WATCH = "btWatchEnabled";
    /** true, коли активна сесія Android Auto (RadioAutoService підключений браузером). */
    public static final String KEY_AA_ACTIVE = "androidAutoActive";
    /** Фактичний reported playing для UI/reconnect — завжди писати разом з KEY_IS_PLAYING. */
    public static final String KEY_ACTUALLY_PLAYING = "actuallyPlaying";
    /** Те саме, що KEY_ACTUALLY_PLAYING (legacy alias). UI читає цей ключ. */
    public static final String KEY_IS_PLAYING = "isPlaying";
    public static final String KEY_TRACK = "lastTrackTitle";
    public static final String KEY_FAVICON = "lastStationFavicon";
    public static final String KEY_GENRE = "lastStationGenre";
    public static final String KEY_COUNTRY = "lastStationCountry";
    public static final String KEY_FAVORITES = "favoriteUrls";
    public static final String KEY_LOCAL_BEST = "localBestUrls";
    /** radio | local | off — off = жанр/історія, без next/prev */
    public static final String KEY_SKIP_MODE = "skipMode";
    public static final String KEY_TEMP_URLS = "tempQueueUrls";
    public static final String KEY_TEMP_NAMES = "tempQueueNames";
    public static final String KEY_TEMP_FAVICONS = "tempQueueFavicons";
    public static final String KEY_TEMP_GENRES = "tempQueueGenres";
    public static final String KEY_TEMP_COUNTRIES = "tempQueueCountries";
    public static final String KEY_TEMP_INDEX = "tempQueueIndex";
}
