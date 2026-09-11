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
    /** @deprecated legacy alias of KEY_IS_PLAYING — do not read; still written by PlaybackPrefs. */
    public static final String KEY_ACTUALLY_PLAYING = "actuallyPlaying";
    /** Canonical reported playing for UI/reconnect. */
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

    // --- UI / order / misc (були магічними рядками) ---
    public static final String KEY_SELECTED_THEME = "selectedTheme";
    public static final String KEY_CUSTOM_TABS = "customTabs";
    public static final String KEY_USER_ADDED = "userAddedStations";
    public static final String KEY_HIDDEN_TABS = "hiddenTabs";
    public static final String KEY_DELETED_STATIONS = "deletedStations";
    public static final String KEY_ORDER_PREFIX = "order_";
    public static final String KEY_ORDER_FAV = "order_fav";
    public static final String KEY_ORDER_BEST_URIS = "order_best_uris";
    public static final String KEY_LAST_A2DP_MS = "lastA2dpConnectMs";
    public static final String KEY_CURRENT_TAB = "currentTab";
    public static final String KEY_BOTTOM_TAB = "bottomTab";
    public static final String KEY_UI_LIGHT = "uiLightTheme";
    public static final String KEY_RECENT = "recentStations";
    public static final String KEY_LOCAL_POS_MS = "localPositionMs";
    public static final String KEY_PAST_SEARCHES = "pastSearches";
}
