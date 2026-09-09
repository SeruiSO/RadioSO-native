package com.seruiso.radio1

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import java.util.Locale
import android.location.LocationManager
import android.location.Geocoder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.seruiso.radio1.ui.theme.RadioSOTheme
import org.json.JSONArray
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import androidx.palette.graphics.Palette as SwatchPalette
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight


/** Підписи вкладок (UA) — top-level, щоб StationScreen теж бачив */
private fun tabLabel(tab: String): String = when (tab.lowercase()) {
    "fav" -> "Обране"
    "best" -> "Топ лок."
    "local" -> "Локальні"
    "search" -> "Пошук"
    "ukraine", "ua" -> "UA"
    "techno" -> "Techno"
    "trance" -> "Trance"
    "pop" -> "Pop"
    else -> tab.replaceFirstChar { it.uppercase() }
}


/** Злити два знімки станції: непорожній favicon/genre/country ніколи не затирається порожнім. */
private fun preferRichStation(a: Station, b: Station): Station {
    val favicon = when {
        a.favicon.isNotBlank() && b.favicon.isNotBlank() ->
            // обидва є — лишаємо довший/http (часто краща якість)
            if (b.favicon.startsWith("http") && !a.favicon.startsWith("http")) b.favicon
            else if (b.favicon.length > a.favicon.length) b.favicon
            else a.favicon
        a.favicon.isNotBlank() -> a.favicon
        else -> b.favicon
    }
    val name = if (b.name.length > a.name.length) b.name else a.name
    val genre = a.genre.ifBlank { b.genre }
    val country = a.country.ifBlank { b.country }
    return Station(a.url, name, genre.ifBlank { b.genre }, country.ifBlank { b.country }, favicon, a.tab)
}

/** distinctBy збагаченням meta замість «перший виграв». Порядок першої появи зберігається. */
private fun mergeStationsRich(list: List<Station>): List<Station> {
    val map = linkedMapOf<String, Station>()
    for (s in list) {
        val prev = map[s.url]
        map[s.url] = if (prev == null) s else preferRichStation(prev, s)
    }
    return map.values.toList()
}


class MainActivity : ComponentActivity() {

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            statusText = BackupStore.importJson(this, raw)
            customTabs = TabStore.customTabs(this)
            favUrls = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_FAVORITES)
            bestUris = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST)
            btWatch = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)
            addedRev++
        } catch (e: Exception) {
            holdStatus("помилка імпорту")
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        reloadLocal()
        maybeStartBtIfConnected()
    }

    private var stationName by mutableStateOf("Виберіть станцію")
    private var currentGenre by mutableStateOf("-")
    private var currentCountry by mutableStateOf("-")
    private var currentFavicon by mutableStateOf("")
    private var currentUrl by mutableStateOf("")
    private var themeId by mutableStateOf("shadow-pulse")
    private var accent by mutableStateOf(0xFF00E676)
    private var nowOpen by mutableStateOf(false)
    private var recentStations by mutableStateOf<List<Station>>(emptyList())
    private var pendingDelete by mutableStateOf<Station?>(null)
    private var holdSeek by mutableStateOf(false)
    private var posMs by mutableStateOf(0L)
    private var durMs by mutableStateOf(0L)
    private var isLocalNow by mutableStateOf(false)
    private val posHandler = Handler(Looper.getMainLooper())
    private val posTick = object : Runnable {
        override fun run() {
            val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            posMs = p.getLong("localPositionMs", 0L)
            durMs = p.getLong("localDurationMs", 0L)
            isLocalNow = p.getString(LocalMusicPlugin.KEY_MODE, "radio") == "local"
            if ((nowOpen || isLocalNow) && !holdSeek) posHandler.postDelayed(this, 400)
        }
    }
    private var trackTitle by mutableStateOf("")
    private var isPlaying by mutableStateOf(false)
    private var statusText by mutableStateOf("готово")
    private var tabIndex by mutableIntStateOf(0)
    // Нижні вкладки: "home" | "stations"(★) | "heart"(♥) | "music" | "search"
    private var bottomTab by mutableStateOf("home")
    private var sourceTabs by mutableStateOf(listOf<String>())
    private var stations by mutableStateOf(listOf<Station>())
    private var localTracks by mutableStateOf(listOf<LocalTrack>())
    private var favUrls by mutableStateOf(setOf<String>())
    private var bestUris by mutableStateOf(setOf<String>())
    private var customTabs by mutableStateOf(listOf<String>())
    private var addedRev by mutableIntStateOf(0)
    private var qName by mutableStateOf("")
    private var qCountry by mutableStateOf("")
    private var qGenre by mutableStateOf("")
    private var searchOpen by mutableStateOf(false)
    private var suggestFor by mutableStateOf("")
    private var searchAll by mutableStateOf(listOf<Station>())
    private var searchRows by mutableStateOf(listOf<Station>())
    private var searchShown by mutableIntStateOf(0)
    private var pickStation by mutableStateOf<Station?>(null)
    private var newTabOpen by mutableStateOf(false)
    private var newTabName by mutableStateOf("")
    private var editTab by mutableStateOf<String?>(null)
    private var editName by mutableStateOf("")
    private var deleteArmed by mutableStateOf(false)
    private var menuOpen by mutableStateOf(false)
    private var sleepMenu by mutableStateOf(false)
    private var btWatch by mutableStateOf(true)
    private var sleepLabel by mutableStateOf("Таймер сну")
    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    private val uiTabs: List<String>
        get() {
            val mid = sourceTabs.filter { it !in listOf("fav", "best", "local", "search") } +
                customTabs.filter { it !in sourceTabs && it !in listOf("fav", "best", "local", "search") }
            return listOf("fav", "best") + mid.distinct() + listOf("local", "search")
        }

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RadioWatchService.ACTION_PLAYBACK_UI -> {
                    // Stack 2: prefs meta, then isPlaying from service (intent wins)
                    val pos = intent.getLongExtra("positionMs", -1L)
                    val dur = intent.getLongExtra("durationMs", -1L)
                    if (!holdSeek && pos >= 0) posMs = pos
                    if (dur > 0) durMs = dur
                    readPrefs()
        if (recentStations.isEmpty()) recentStations = loadRecentStations()
                    isPlaying = intent.getBooleanExtra("playing", false)
                    if (isPlaying) softStatus( "відтворення")
                    else if (statusText == "відтворення") statusText = "пауза"
                    isLocalNow = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .getString(LocalMusicPlugin.KEY_MODE, "radio") == "local"
                    if (isLocalNow) {
                        posHandler.removeCallbacks(posTick)
                        posHandler.post(posTick)
                    }
                }
                
                RadioWatchService.ACTION_TRACK_META -> {
                    readPrefs()
                    val extra = intent.getStringExtra(RadioWatchService.EXTRA_TRACK) ?: ""
                    if (extra.isNotBlank()) trackTitle = extra
                }
                RadioWatchService.ACTION_MEDIA_NEXT,
                RadioWatchService.ACTION_MEDIA_PREV -> readPrefs()
                RadioWatchService.ACTION_STATUS_UI -> {
                    val st = intent.getStringExtra("status") ?: ""
                    val attempt = intent.getIntExtra("attempt", 0)
                    statusText = if (attempt > 0) "$st #$attempt" else st
                    readPrefs()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askPermissions()
        maybeStartBtIfConnected()
        Palette.init(this)
        val loaded = StationRepo.load(this)
        sourceTabs = loaded.first
        stations = loaded.second
        favUrls = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_FAVORITES)
        bestUris = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST)
        customTabs = TabStore.customTabs(this)
        reloadLocal()
        readPrefs()
        val lastTab = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).getString("currentTab", "fav")
        val idx = uiTabs.indexOf(lastTab)
        if (idx >= 0) tabIndex = idx
        val lastBottom = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .getString("bottomTab", "home") ?: "home"
        if (lastBottom in listOf("home", "stations", "heart", "music", "tabs", "search", "library")) {
            bottomTab = if (lastBottom == "library") "stations" else lastBottom
        }

        setContent {
            RadioSOTheme(accent = Color(accent)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val tab = uiTabs.getOrNull(tabIndex) ?: ""
                    val radioRowsMemo = remember(addedRev, tabIndex, customTabs, favUrls, searchRows) {
                        visibleRadio()
                    }
                    val allRadioMemo = remember(addedRev, customTabs, favUrls, searchRows) {
                        allRadioStations()
                    }
                    val localRowsMemo = remember(tabIndex, customTabs, localTracks, bestUris, addedRev) {
                        visibleLocal()
                    }
                    val bestRowsMemo = remember(customTabs, localTracks, bestUris, addedRev) {
                        visibleLocal("best")
                    }
                    StationScreen(
                        tabs = uiTabs,
                        tabIndex = tabIndex,
                        onTab = {
                            tabIndex = it
                            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                                .edit().putString("currentTab", uiTabs.getOrNull(it) ?: "fav").apply()
                            persistVisibleQueue()
                        },
                        bottomTab = bottomTab,
                        onBottomTab = { selectBottomTab(it) },
                        bestRows = bestRowsMemo,
                        
                        qName = qName, onName = { qName = it },
                        qCountry = qCountry, onCountry = { qCountry = it },
                        qGenre = qGenre, onGenre = { qGenre = it },
                        searchOpen = searchOpen,
                        onSearchOpen = { searchOpen = !searchOpen },
                        onSearch = { runSearch(); searchOpen = false },
                        onRightOpen = { },
                        suggestFor = suggestFor,
                        onSuggestFor = { suggestFor = it },
                        nameHints = SearchHints.past(this) + SearchHints.names,
                        countryHints = SearchHints.countries,
                        genreHints = SearchHints.genres,
                        onMore = { loadMoreSearch() },
                        canMore = searchShown < searchAll.size && currentTab() == "search",
                        canMoreSearch = searchShown < searchAll.size,
                        countries = RadioBrowser.countries,
                        genres = RadioBrowser.genres,
                        onAddTab = { newTabOpen = true },
                        pickStation = pickStation,
                        targetTabs = targetTabs(),
                        onPickTabForStation = { tab ->
                            val s = pickStation
                            if (s != null) {
                                // "Вже є" лише якщо РЕАЛЬНО видно на вкладці.
                                // Після removeStation URL у deletedStations — у списку її немає,
                                // тож already=false → addStation зробить unDelete + свіжі meta.
                                val deleted = TabStore.deleted(this, tab)
                                val inExtra = TabStore.extraStations(this, tab).any { it.url == s.url }
                                val inBase = stations.any { it.url == s.url && it.tab == tab }
                                val already = (inExtra || inBase) && s.url !in deleted
                                if (already) {
                                    statusText = "вже є в $tab"
                                } else {
                                    val err = TabStore.addStation(this, tab, s)
                                    statusText = err ?: "додано в $tab"
                                    if (err == null) {
                                        // якщо вже в ★ — оновити знімок (іконка/жанр з пошуку)
                                        if (favUrls.contains(s.url)) {
                                            FavStore.refreshStation(this, s)
                                        }
                                        addedRev++
                                    }
                                }
                            }
                            pickStation = null
                        },
                        onCancelPick = { pickStation = null },
                        newTabOpen = newTabOpen,
                        newTabName = newTabName,
                        onNewTabName = { newTabName = it },
                        onCreateTab = {
                            val err = TabStore.addTab(this, newTabName, sourceTabs)
                            if (err == null) {
                                customTabs = TabStore.customTabs(this)
                                statusText = "вкладка ${newTabName.lowercase()} створена"
                                newTabName = ""
                                newTabOpen = false
                            } else holdStatus(err)
                        },
                        onCancelNewTab = { newTabOpen = false },
                        customTabs = customTabs,
                        editTab = editTab,
                        editName = editName,
                        deleteArmed = deleteArmed,
                        onLongTab = { tab ->
                            if (tab in customTabs) {
                                editTab = tab
                                editName = tab
                                deleteArmed = false
                            }
                        },
                        onEditName = { editName = it },
                        onRenameTab = {
                            val oldName = editTab ?: return@StationScreen
                            val err = TabStore.renameTab(this, oldName, editName, sourceTabs)
                            if (err == null) {
                                customTabs = TabStore.customTabs(this)
                                addedRev++
                                statusText = "перейменовано"
                                editTab = null
                            } else statusText = err
                        },
                        onDeleteTab = {
                            val tab = editTab ?: return@StationScreen
                            if (!deleteArmed) { deleteArmed = true; return@StationScreen }
                            TabStore.deleteTab(this, tab)
                            customTabs = TabStore.customTabs(this)
                            addedRev++
                            if (uiTabs.getOrNull(tabIndex) == tab) tabIndex = 0
                            statusText = "видалено $tab"
                            editTab = null
                            deleteArmed = false
                        },
                        onCancelEdit = { editTab = null; deleteArmed = false },
                        radioRows = radioRowsMemo,
                        searchRows = searchRows,
                        allRadio = allRadioMemo,
                        recentStations = recentStations,
                        localRows = localRowsMemo,
                        allLocal = localTracks,
                        showLocal = tab == "local" || tab == "best",
                        name = stationName,
                        genre = currentGenre,
                        country = currentCountry,
                        favicon = currentFavicon,
                        currentUrl = currentUrl,
                        accent = accent,
                        themeName = themeId,
                        nowOpen = nowOpen,
                        onNow = {
                            nowOpen = true
                            posHandler.removeCallbacks(posTick)
                            posHandler.post(posTick)
                        },
                        onNowClose = { nowOpen = false },
                        onTheme = { /* picker inside StationScreen */ },
                        onPickTheme = { id ->
                            val n = ThemeStore.set(this, id)
                            themeId = n.id
                            accent = n.accent
                            statusText = n.id
                        },
                        pendingDelete = pendingDelete,
                        onAskDelete = { pendingDelete = it },
                        onCancelDelete = { pendingDelete = null },
                        onDeleteStation = { s ->
                            val tab = currentTab()
                            if (tab == "fav") {
                                // З «Обраного» — лише зняти ★
                                toggleFav(s)
                            } else {
                                TabStore.removeStation(this, tab, s.url)
                                val rest = visibleRadio().map { it.url }.filter { it != s.url }
                                TabStore.saveOrder(this, tab, rest)
                                // Видалення з основної вкладки також прибирає з «Обраного»
                                if (favUrls.contains(s.url)) toggleFav(s)
                            }
                            addedRev++
                            statusText = "видалено"
                        },
                        track = trackTitle,
                        playing = isPlaying,
                        status = statusText,
                        favUrls = favUrls,
                        bestUris = bestUris,
                        onPlayPause = {
                            if (isPlaying) sendAction(RadioWatchService.ACTION_PAUSE)
                            else playCurrentOrFirst()
                        },
                        onNext = { sendAction(RadioWatchService.ACTION_NOTIF_NEXT) },
                        onPrev = { sendAction(RadioWatchService.ACTION_NOTIF_PREV) },
                        onPickRadio = { list, index -> menuOpen = false; playRadio(list, index) },
                        onPickLocal = { list, index -> menuOpen = false; playLocal(list, index) },
                        onToggleFav = { s -> toggleFav(s.url) },
                        onAddToTab = { s -> pickStation = s },
                        onDragStart = { vibrateTick() },
                        onMoveTo = { from, to -> moveRadioTo(from, to) },
                        onMoveLocalTo = { from, to -> moveRadioTo(from, to) },
                        onMoveStation = { s, dir ->
                            val tab = currentTab()
                            if (tab !in listOf("fav", "best", "local", "search")) {
                                val list = visibleRadio().toMutableList()
                                val i = list.indexOfFirst { it.url == s.url }
                                val j = i + dir
                                if (i >= 0 && j in list.indices) {
                                    val a = list[i]; list[i] = list[j]; list[j] = a
                                    TabStore.saveOrder(this, tab, list.map { it.url })
                                    addedRev++
                                }
                            }
                        },
                        onSeek = { seekTo(it) },
                        onShuffle = {
                            val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                            val v = !p.getBoolean(LocalMusicPlugin.KEY_LOCAL_SHUFFLE, false)
                            p.edit().putBoolean(LocalMusicPlugin.KEY_LOCAL_SHUFFLE, v).apply()
                            holdStatus(if (v) "перемішування: увімк" else "перемішування: вимк")
                        },
                        onRepeat = {
                            val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                            val cur = p.getString(LocalMusicPlugin.KEY_LOCAL_REPEAT, "off")
                            val next = when (cur) { "off" -> "all"; "all" -> "one"; else -> "off" }
                            p.edit().putString(LocalMusicPlugin.KEY_LOCAL_REPEAT, next).apply()
                            holdStatus(when (next) {
                                "all" -> "повтор: усі"
                                "one" -> "повтор: один трек"
                                else -> "повтор: вимкнено"
                            })
                        },
                        posMs = posMs,
                        durMs = durMs,
                        isLocalNow = isLocalNow,

                        onToggleBest = { toggleBest(it.uri) },
                        onScan = { reloadLocal() },
                        menuOpen = menuOpen,
                        onMenu = { menuOpen = !menuOpen },
                        onCloseMenu = { menuOpen = false; sleepMenu = false },
                        btWatch = btWatch,
                        onBt = { toggleBt() },
                        sleepLabel = sleepLabel,
                        sleepMenu = sleepMenu,
                        onSleepMenu = { sleepMenu = !sleepMenu },
                        onSleep = { armSleep(it) },
                        onExport = { exportBackup(); menuOpen = false },
                        onImport = { importLauncher.launch("application/json"); menuOpen = false },

                    )
                }
            }
        }
    }

    private fun targetTabs(): List<String> {
        val built = sourceTabs.filter { it !in TabStore.reserved && it != "search" }
        return (built + customTabs).distinct()
    }

    /** Повідомлення в інфо-панелі тримається holdMs, щоб «відтворення» його не змивало */
    private var statusHoldUntil = 0L

    private fun holdStatus(msg: String, holdMs: Long = 2200L) {
        statusText = msg
        statusHoldUntil = System.currentTimeMillis() + holdMs
    }

    private fun softStatus(msg: String) {
        if (System.currentTimeMillis() < statusHoldUntil) return
        statusText = msg
    }

    private fun runSearch(countryOverride: String? = null) {
        val n = qName.trim()
        val c = normalizeCountry(countryOverride ?: qCountry)
        // не затираємо поле країни при гео-пошуку (override)
        if (countryOverride == null) qCountry = c
        val g = qGenre.trim()
        if (n.isNotBlank()) {
            val past = SearchHints.past(this).toMutableList()
            past.remove(n)
            past.add(0, n)
            SearchHints.savePast(this, past.take(5))
        }
        if (n.isBlank() && c.isBlank() && g.isBlank()) {
            holdStatus("введи назву, країну або жанр")
            return
        }
        val gen = ++RadioBrowser.activeGen
        statusText = "пошук..."
        searchAll = emptyList()
        searchRows = emptyList()
        searchShown = 0
        Thread {
            val result = try { RadioBrowser.search(n, c, g, gen) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (gen != RadioBrowser.activeGen) return@runOnUiThread
                searchAll = result ?: emptyList()
                searchShown = minOf(100, searchAll.size)
                searchRows = searchAll.take(searchShown)
                holdStatus(if (searchAll.isEmpty()) "нічого не знайдено" else "знайдено: ${searchAll.size}")
            }
        }.start()
    }

    private fun loadMoreSearch() {
        if (searchShown >= searchAll.size) return
        searchShown = minOf(searchShown + 100, searchAll.size)
        searchRows = searchAll.take(searchShown)
    }

    private fun exportBackup() {
        val json = BackupStore.exportJson(this)
        val file = File(cacheDir, "radio_settings.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
        send.type = "application/json"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.putExtra(Intent.EXTRA_SUBJECT, "radio_settings.json")
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Експорт RadioSO"))
        statusText = "експорт"
    }

    private fun toggleBt() {
        btWatch = !btWatch
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, btWatch).commit()
        statusText = if (btWatch) "BT стеження увімк" else "BT стеження вимк"
    }

    private fun armSleep(mins: Int) {
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        sleepRunnable = null
        if (mins <= 0) {
            sleepLabel = "Таймер сну"
            statusText = "таймер вимкнено"
            sleepMenu = false
            return
        }
        sleepLabel = "Сон: ${mins} хв"
        statusText = sleepLabel
        val r = Runnable {
            sendAction(RadioWatchService.ACTION_PAUSE)
            sleepLabel = "Таймер сну"
            softStatus("таймер сну: пауза")
        }
        sleepRunnable = r
        sleepHandler.postDelayed(r, mins * 60_000L)
        sleepMenu = false
    }

    private fun normalizeCountry(raw: String): String {
        val m = mapOf(
            "ukraine" to "Ukraine", "ua" to "Ukraine",
            "italy" to "Italy", "it" to "Italy",
            "german" to "Germany", "germany" to "Germany", "de" to "Germany",
            "france" to "France", "fr" to "France",
            "spain" to "Spain", "es" to "Spain",
            "usa" to "United States", "us" to "United States", "united states" to "United States",
            "uk" to "United Kingdom", "gb" to "United Kingdom", "united kingdom" to "United Kingdom",
            "netherlands" to "Netherlands", "nl" to "Netherlands",
            "canada" to "Canada", "ca" to "Canada",
            "poland" to "Poland", "pl" to "Poland",
            "austria" to "Austria", "at" to "Austria",
            "switzerland" to "Switzerland", "ch" to "Switzerland",
            "belgium" to "Belgium", "be" to "Belgium",
            "sweden" to "Sweden", "se" to "Sweden",
            "norway" to "Norway", "no" to "Norway",
            "denmark" to "Denmark", "dk" to "Denmark",
            "australia" to "Australia", "au" to "Australia",
            "japan" to "Japan", "jp" to "Japan",
            "south korea" to "South Korea", "korea" to "South Korea", "kr" to "South Korea",
            "new zealand" to "New Zealand", "nz" to "New Zealand",
            "portugal" to "Portugal", "pt" to "Portugal",
            "romania" to "Romania", "ro" to "Romania",
            "czech" to "Czechia", "czechia" to "Czechia", "cz" to "Czechia",
            "slovakia" to "Slovakia", "sk" to "Slovakia",
            "hungary" to "Hungary", "hu" to "Hungary",
            "ireland" to "Ireland", "ie" to "Ireland",
            "finland" to "Finland", "fi" to "Finland",
            "greece" to "Greece", "gr" to "Greece",
            "turkey" to "Turkey", "tr" to "Turkey",
            "brazil" to "Brazil", "br" to "Brazil",
            "mexico" to "Mexico", "mx" to "Mexico",
            "india" to "India", "in" to "India",
            "israel" to "Israel", "il" to "Israel",
            "russia" to "Russia", "ru" to "Russia"
        )
        val k = raw.trim().lowercase()
        if (k.isEmpty()) return ""
        return m[k] ?: raw.trim().replaceFirstChar { it.uppercase() }
    }

    private fun countryFromLocale(): String {
        val iso = try { Locale.getDefault().country } catch (_: Exception) { "" }
        return normalizeCountry(iso)
    }

    private fun countryFromCache(): String {
        val raw = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .getString("geoCountry", "") ?: ""
        return normalizeCountry(raw)
    }

    private fun saveGeoCountry(c: String) {
        val n = normalizeCountry(c)
        if (n.isBlank()) return
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .edit().putString("geoCountry", n).apply()
    }

    /** IP → країна (без GPS). */
    private fun countryFromIp(): String {
        val urls = listOf(
            "http://ip-api.com/json/?fields=status,country",
            "https://ipapi.co/json/"
        )
        for (u in urls) {
            try {
                val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "RadioSO-native/0.9.82")
                if (conn.responseCode != 200) { conn.disconnect(); continue }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val o = org.json.JSONObject(body)
                val name = when {
                    o.optString("status") == "success" -> o.optString("country")
                    o.has("country_name") -> o.optString("country_name")
                    o.has("country") -> o.optString("country")
                    else -> ""
                }
                val n = normalizeCountry(name)
                if (n.isNotBlank()) return n
            } catch (_: Exception) { }
        }
        return ""
    }

    /** Coarse location → країна, лише якщо дозвіл уже є. */
    private fun countryFromLocation(): String {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return ""
            val lm = getSystemService(LocationManager::class.java) ?: return ""
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: return ""
            if (!Geocoder.isPresent()) return ""
            val geo = Geocoder(this, Locale.ENGLISH)
            @Suppress("DEPRECATION")
            val list = geo.getFromLocation(loc.latitude, loc.longitude, 1)
            val c = list?.firstOrNull()?.countryName ?: ""
            return normalizeCountry(c)
        } catch (_: Exception) {
            return ""
        }
    }

    /**
     * Гібрид D при відкритті правої картки:
     * 1) кеш / Locale → одразу пошук
     * 2) IP (і GPS якщо є дозвіл) → уточнити й перезапустити, якщо країна інша
     */
    private fun autoSearchByGeo() {
        val cached = countryFromCache()
        val localeC = countryFromLocale()
        val first = when {
            cached.isNotBlank() -> cached
            localeC.isNotBlank() -> localeC
            else -> ""
        }
        qName = ""
        qGenre = ""
        qCountry = "" // поля порожні — зручно вводити свій запит
        if (first.isNotBlank()) {
            holdStatus("пошук: $first…")
            runSearch(countryOverride = first)
        } else {
            statusText = "визначаємо країну…"
            searchAll = emptyList()
            searchRows = emptyList()
            searchShown = 0
        }
        Thread {
            var refined = countryFromIp()
            if (refined.isBlank()) refined = countryFromLocation()
            if (refined.isBlank()) {
                runOnUiThread {
                    if (searchRows.isEmpty() && first.isBlank()) holdStatus("не вдалося визначити країну")
                }
                return@Thread
            }
            saveGeoCountry(refined)
            // не пишемо refined у qCountry — лише перезапуск пошуку, якщо інша країна
            if (normalizeCountry(first) != refined) {
                runOnUiThread {
                    holdStatus("пошук: $refined…")
                    runSearch(countryOverride = refined)
                }
            } else if (cached.isBlank()) {
                // вже шукали по locale — просто зберегли IP-країну
            }
        }.start()
    }

    private fun vibrateTick() {
        try {
            val v = getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun moveRadioTo(from: Int, to: Int) {
        val tab = currentTab()
        if (tab in listOf("search", "local")) return
        if (from == to) return
        if (tab == "best") {
            val list = visibleLocal().toMutableList()
            if (from !in list.indices || to !in list.indices) return
            val item = list.removeAt(from)
            list.add(to, item)
            FavStore.save(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, list.map { it.uri }.toSet())
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .edit().putString("order_best_uris", JSONArray(list.map { it.uri }).toString()).commit()
            bestUris = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST)
            addedRev++
            return
        }
        val list = visibleRadio().toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        TabStore.saveOrder(this, tab, list.map { it.url })
        if (tab == "fav") FavStore.saveStations(this, list)
        addedRev++
    }

    private fun currentTab(): String = uiTabs.getOrNull(tabIndex) ?: ""

    // Перемикання нижньої навігації: синхронізує tabIndex з потрібною
    // зарезервованою вкладкою, щоб перевикористати вже готову логіку списків.
    private fun selectBottomTab(t: String) {
        bottomTab = t
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .edit().putString("bottomTab", t).apply()
        val wantTab = when (t) {
            "stations" -> "fav"
            "heart" -> "best"
            "music" -> "local"
            "search" -> "search"
            "tabs" -> null          // лише відкрити праву картку (у StationScreen)
            "library" -> "fav"
            else -> null
        }
        if (wantTab != null) {
            val i = uiTabs.indexOf(wantTab)
            if (i >= 0) {
                tabIndex = i
                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                    .edit().putString("currentTab", wantTab).apply()
            }
        }
        if (t == "search") autoSearchByGeo()
        persistVisibleQueue()
    }


    private fun allRadioStations(): List<Station> {
        addedRev
        val deletedMap = TabStore.deletedMap(this)
        val tabIds = (sourceTabs + customTabs)
            .filter { it !in listOf("fav", "best", "local", "search") }
            .distinct()
        val fromTabs = tabIds.flatMap { tab ->
            TabStore.extraStations(this, tab) + stations.filter { it.tab == tab }
        }
        return mergeStationsRich(
            FavStore.stations(this) +
                searchRows +
                fromTabs +
                stations
        ).filter { it.url !in (deletedMap[it.tab] ?: emptySet()) }
    }

    private fun loadRecentStations(): List<Station> {
        return try {
            val raw = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .getString("recentStations", "[]") ?: "[]"
            val arr = JSONArray(raw)
            val out = mutableListOf<Station>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isBlank()) continue
                out.add(
                    Station(
                        url,
                        o.optString("name", "Station"),
                        o.optString("genre", ""),
                        o.optString("country", ""),
                        o.optString("favicon", ""),
                        "recent"
                    )
                )
            }
            out.take(8)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun pushRecentStation(s: Station) {
        if (s.url.isBlank() || s.url.startsWith("content:")) return
        try {
            val cur = loadRecentStations().filter { it.url != s.url }.toMutableList()
            cur.add(0, s)
            val arr = JSONArray()
            cur.take(8).forEach { x ->
                arr.put(
                    org.json.JSONObject()
                        .put("url", x.url)
                        .put("name", x.name)
                        .put("genre", x.genre)
                        .put("country", x.country)
                        .put("favicon", x.favicon)
                )
            }
            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                .edit().putString("recentStations", arr.toString()).apply()
            recentStations = cur.take(8)
        } catch (_: Exception) {}
    }

    private fun visibleRadio(): List<Station> {
        addedRev // observe
        val tab = currentTab()
        val deleted = TabStore.deleted(this, tab)
        return when (tab) {
            "fav" -> {
                // 1) FavStore = джерело членства в ★
                // 2) збагачуємо search/extra/catalog БЕЗ затирання favicon
                val fromExtra = (sourceTabs + customTabs)
                    .filter { it !in listOf("fav", "best", "local", "search") }
                    .distinct()
                    .flatMap { t -> TabStore.extraStations(this, t) }
                    .filter { favUrls.contains(it.url) }
                val merged = mergeStationsRich(
                    FavStore.stations(this) +
                        searchRows.filter { favUrls.contains(it.url) } +
                        fromExtra +
                        stations.filter { favUrls.contains(it.url) }
                ).filter { it.url !in deleted }
                // якщо з’явилась краща іконка — зберегти в FavStore (щоб не відкочувалось)
                val saved = FavStore.stations(this).associateBy { it.url }
                var dirty = false
                val fixed = merged.map { s ->
                    val old = saved[s.url]
                    if (old != null && old.favicon.isBlank() && s.favicon.isNotBlank()) {
                        dirty = true
                    }
                    s
                }
                if (dirty) {
                    FavStore.saveStations(this, fixed.map { it.copy(tab = "fav") })
                }
                TabStore.applyOrder(this, "fav", fixed)
            }
            "best", "local" -> emptyList()
            "search" -> searchRows
            else -> {
                val base = stations.filter { it.tab == tab }
                val extra = TabStore.extraStations(this, tab)
                // mergeRich: extra meta + catalog, без втрати favicon
                TabStore.applyOrder(
                    this, tab,
                    mergeStationsRich(extra + base).filter { it.url !in deleted }
                )
            }
        }
    }

    private fun visibleLocal(tabOverride: String? = null): List<LocalTrack> {
        val tab = tabOverride ?: currentTab()
        return when (tab) {
            "local" -> localTracks
            "best" -> {
                val raw = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).getString("order_best_uris", null)
                val base = localTracks.filter { bestUris.contains(it.uri) }
                if (raw.isNullOrBlank()) base
                else {
                    val arr = JSONArray(raw)
                    val map = base.associateBy { it.uri }.toMutableMap()
                    val out = mutableListOf<LocalTrack>()
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i)
                        val x = map.remove(u) ?: continue
                        out.add(x)
                    }
                    out.addAll(map.values)
                    out
                }
            }
            else -> emptyList()
        }
    }

    private fun reloadLocal() {
        if (!hasAudioPermission()) {
            localTracks = emptyList()
            holdStatus("немає дозволу на аудіо")
            return
        }
        localTracks = try {
            LocalLibrary.list(this)
        } catch (e: Exception) {
            holdStatus("помилка сканування")
            emptyList()
        }
        if (currentTab() == "local") {
            statusText = "треків: ${localTracks.size}"
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(RadioWatchService.ACTION_PLAYBACK_UI)
            addAction(RadioWatchService.ACTION_TRACK_META)
            addAction(RadioWatchService.ACTION_STATUS_UI)
            addAction(RadioWatchService.ACTION_MEDIA_NEXT)
            addAction(RadioWatchService.ACTION_MEDIA_PREV)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(uiReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(uiReceiver, f)
        }
        readPrefs()
    }

    override fun onStop() {
        try { unregisterReceiver(uiReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun readPrefs() {
        val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        stationName = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Виберіть станцію") ?: "Виберіть станцію"
        trackTitle = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "") ?: ""
        currentGenre = p.getString(BluetoothAutoPlayPlugin.KEY_GENRE, "-") ?: "-"
        currentCountry = p.getString(BluetoothAutoPlayPlugin.KEY_COUNTRY, "-") ?: "-"
        currentFavicon = p.getString(BluetoothAutoPlayPlugin.KEY_FAVICON, "") ?: ""
        currentUrl = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
        // Reported only (KEY_IS_PLAYING). Never KEY_PLAY for UI chrome.
        isPlaying = p.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
        val th = ThemeStore.get(this)
        themeId = th.id
        accent = th.accent
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun maybeStartBtIfConnected() {
        val sp = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) return
        try {
            val i = Intent(this, RadioWatchService::class.java)
            i.action = RadioWatchService.ACTION_START
            startForegroundService(i)
        } catch (_: Exception) {}
    }

    private fun askPermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) need.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) need.add(Manifest.permission.POST_NOTIFICATIONS)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) need.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (need.isNotEmpty()) permissionsLauncher.launch(need.toTypedArray())
    }

    private fun toggleFav(station: Station) {
        FavStore.toggleStation(this, station)
        favUrls = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_FAVORITES)
    }
    private fun toggleFav(url: String) {
        // Пріоритет: search → extra поточної вкладки → catalog → FavStore → поточне відтворення.
        // Інакше ★ зберігає старий знімок без favicon з stations.json.
        val candidates = (
            searchRows.filter { it.url == url } +
            TabStore.extraStations(this, currentTab()).filter { it.url == url } +
            stations.filter { it.url == url } +
            FavStore.stations(this).filter { it.url == url }
        )
        val s = candidates.firstOrNull { it.favicon.isNotBlank() }
            ?: candidates.firstOrNull()
            ?: Station(url, stationName, currentGenre, currentCountry, currentFavicon, "fav")
        toggleFav(s)
    }

    private fun toggleBest(uri: String) {
        FavStore.toggle(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, uri)
        bestUris = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST)
        customTabs = TabStore.customTabs(this)
    }

    private fun playCurrentOrFirst() {
        val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        val url = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
        if (url.isNotBlank()) {
            sendAction(RadioWatchService.ACTION_PLAY)
            return
        }
        val locals = visibleLocal()
        if (locals.isNotEmpty()) {
            playLocal(locals, 0)
            return
        }
        val radios = visibleRadio()
        if (radios.isNotEmpty()) playRadio(radios, 0)
    }


    /** Черга Auto/skip = видимий список вкладки. Без старту відтворення. */
    private fun persistVisibleQueue() {
        val tab = currentTab()
        if (tab == "home" || bottomTab == "home" || bottomTab == "tabs") return
        val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        if (tab == "local" || tab == "best") {
            val list = visibleLocal()
            if (list.isEmpty()) return
            val uris = JSONArray(); val titles = JSONArray()
            val artists = JSONArray(); val albumIds = JSONArray()
            list.forEach {
                uris.put(it.uri); titles.put(it.title)
                artists.put(it.artist); albumIds.put(it.albumId)
            }
            val cur = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
            val idx = list.indexOfFirst { it.uri == cur }.let { if (it >= 0) it else 0 }
            p.edit()
                .putString(LocalMusicPlugin.KEY_MODE, "local")
                .putString(LocalMusicPlugin.KEY_LOCAL_URIS, uris.toString())
                .putString(LocalMusicPlugin.KEY_LOCAL_TITLES, titles.toString())
                .putString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, artists.toString())
                .putString(LocalMusicPlugin.KEY_LOCAL_ALBUM_IDS, albumIds.toString())
                .putInt(LocalMusicPlugin.KEY_LOCAL_INDEX, idx)
                .apply()
        } else {
            val list = visibleRadio()
            if (list.isEmpty()) return
            val urls = JSONArray(); val names = JSONArray(); val favs = JSONArray()
            val genres = JSONArray(); val countries = JSONArray()
            list.forEach {
                urls.put(it.url); names.put(it.name); favs.put(it.favicon)
                genres.put(it.genre); countries.put(it.country)
            }
            val cur = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
            val idx = list.indexOfFirst { it.url == cur }.let { if (it >= 0) it else 0 }
            p.edit()
                .putString(LocalMusicPlugin.KEY_MODE, "radio")
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, urls.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, names.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_FAVICONS, favs.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, genres.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, countries.toString())
                .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, idx)
                .apply()
        }
    }

    private fun playRadio(list: List<Station>, index: Int) {
        if (index !in list.indices) return
        val s = list[index]
        pushRecentStation(s)
        stationName = s.name
        trackTitle = ""
        val urls = JSONArray(); val names = JSONArray(); val favs = JSONArray()
        val genres = JSONArray(); val countries = JSONArray()
        list.forEach {
            urls.put(it.url); names.put(it.name); favs.put(it.favicon)
            genres.put(it.genre); countries.put(it.country)
        }
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
            .putString(LocalMusicPlugin.KEY_MODE, "radio")
            .putString(BluetoothAutoPlayPlugin.KEY_URL, s.url)
            .putString(BluetoothAutoPlayPlugin.KEY_NAME, s.name)
            .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, s.favicon)
            .putString(BluetoothAutoPlayPlugin.KEY_GENRE, s.genre)
            .putString(BluetoothAutoPlayPlugin.KEY_COUNTRY, s.country)
            .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, urls.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, names.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_FAVICONS, favs.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, genres.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, countries.toString())
            .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, index)
            .apply()
        startPlay(s.url, s.name)
    }

    private fun playLocal(list: List<LocalTrack>, index: Int) {
        if (index !in list.indices) return
        val t = list[index]
        stationName = t.title
        trackTitle = t.artist
        val uris = JSONArray(); val titles = JSONArray()
        val artists = JSONArray(); val albumIds = JSONArray()
        list.forEach {
            uris.put(it.uri); titles.put(it.title)
            artists.put(it.artist); albumIds.put(it.albumId)
        }
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
            .putString(LocalMusicPlugin.KEY_MODE, "local")
            .putString(LocalMusicPlugin.KEY_LOCAL_URIS, uris.toString())
            .putString(LocalMusicPlugin.KEY_LOCAL_TITLES, titles.toString())
            .putString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, artists.toString())
            .putString(LocalMusicPlugin.KEY_LOCAL_ALBUM_IDS, albumIds.toString())
            .putInt(LocalMusicPlugin.KEY_LOCAL_INDEX, index)
            .putString(BluetoothAutoPlayPlugin.KEY_URL, t.uri)
            .putString(BluetoothAutoPlayPlugin.KEY_NAME, t.title)
            .putString(BluetoothAutoPlayPlugin.KEY_TRACK, t.artist)
            .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, t.albumId)
            .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
            .apply()
        startPlay(t.uri, t.title)
    }

    private fun startPlay(url: String, name: String) {
        val i = Intent(this, RadioWatchService::class.java)
        i.action = RadioWatchService.ACTION_PLAY_URL
        i.putExtra(RadioWatchService.EXTRA_URL, url)
        i.putExtra(RadioWatchService.EXTRA_NAME, name)
        startForegroundService(i)
        statusText = "запуск"
        isLocalNow = url.startsWith("content:")
        if (nowOpen || url.startsWith("content:")) { posHandler.removeCallbacks(posTick); posHandler.post(posTick) }
    }

    private fun seekTo(pos: Long) {
        holdSeek = true
        posMs = pos
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .edit().putLong("localPositionMs", pos).commit()
        val i = Intent(this, RadioWatchService::class.java)
        i.action = RadioWatchService.ACTION_SEEK
        i.putExtra(RadioWatchService.EXTRA_POSITION_MS, pos)
        startForegroundService(i)
        posHandler.postDelayed({ holdSeek = false }, 400)
    }

    private fun sendAction(action: String) {
        val i = Intent(this, RadioWatchService::class.java)
        i.action = action
        startForegroundService(i)
    }
}

@Composable
private fun MiniProgressBar(
    posMs: Long,
    durMs: Long,
    accent: Color,
    muted: Color,
    onSeek: (Long) -> Unit,
    onShuffle: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    controlsTint: Color = muted,
) {
    val d = if (durMs > 0) durMs else 1L
    var slide by remember { mutableStateOf(-1f) }
    val frac = (if (slide >= 0f) slide else posMs.toFloat() / d).coerceIn(0f, 1f)
    fun fmt(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }
    // Один ряд: [shuffle] час |——прогрес——| час [repeat]
    // Кнопки лише якщо передані (локальний Now Playing); в інших місцях — як раніше без них.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onShuffle != null) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Перемішати",
                tint = controlsTint,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
                    .clickable { onShuffle() }
            )
        }
        Text(
            fmt(posMs),
            color = muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 6.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .pointerInput(d) {
                    detectTapGestures { off ->
                        if (d > 0L) {
                            val f = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeek((d * f).toLong())
                        }
                    }
                }
                .pointerInput(d) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (durMs > 0 && slide >= 0f) onSeek((slide * durMs).toLong())
                            slide = -1f
                        },
                        onDragCancel = { slide = -1f },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            slide = change.position.x.coerceIn(0f, w) / w
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(muted.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(8.dp)
                    .background(accent, RoundedCornerShape(4.dp))
            )
        }
        Text(
            fmt(durMs),
            color = muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp)
        )
        if (onRepeat != null) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = "Повторити",
                tint = controlsTint,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp)
                    .clickable { onRepeat() }
            )
        }
    }
}

// Рядок локального треку — перевикористовується у вкладці "Обрані"
// для секцій "Обрані локальні" та "Локальна музика".
@Composable
private fun LocalTrackRow(
    item: LocalTrack,
    isCurrent: Boolean,
    acc: Color,
    muted: Color,
    text: Color,
    isBest: Boolean = false,
    onToggleBest: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .background(if (isCurrent) acc.copy(alpha = 0.18f) else Palette.card, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            val a = if (item.albumId.isNotBlank() && item.albumId != "0")
                "content://media/external/audio/albumart/${item.albumId}" else ""
            if (a.isNotEmpty()) AsyncImage(model = a, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        if (onToggleBest != null) {
            Icon(
                if (isBest) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isBest) "Прибрати з обраних локальних" else "В обрані локальні",
                tint = acc,
                modifier = Modifier
                    .clickable { onToggleBest() }
                    .padding(start = 8.dp, end = 2.dp)
                    .size(24.dp)
            )
        }
    }
}

// Нижня навігація: Дім / Обрані / Пошук
@Composable
private fun BottomNavBar(
    current: String,
    onSelect: (String) -> Unit,
    acc: Color,
    muted: Color,
    card: Color,
    onSwipeUp: () -> Unit = {},
    onPull: (Float) -> Unit = {},
    onPullEnd: () -> Unit = {},
) {
    val items = listOf(
        Triple("home", "Дім", Icons.Filled.Home),
        Triple("stations", "Станції", Icons.Filled.Star),
        Triple("heart", "Обрані", Icons.Filled.Favorite),
        Triple("music", "Музика", Icons.Filled.LibraryMusic),
        Triple("tabs", "Вкладки", Icons.Filled.Category),
        Triple("search", "Пошук", Icons.Filled.Search),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(card, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onPullEnd() },
                    onDragCancel = { onPullEnd() }
                ) { _, drag ->
                    if (drag < 0) onPull(drag)
                    else onPull(drag)
                }
            }
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (key, label, icon) ->
            val selected = current == key
            Column(
                modifier = Modifier
                    .clickable { onSelect(key) }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label, tint = if (selected) acc else muted, modifier = Modifier.size(28.dp))
                Text(label, color = if (selected) acc else muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 0.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    tabs: List<String>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    bottomTab: String = "home",
    onBottomTab: (String) -> Unit = {},
    bestRows: List<LocalTrack> = emptyList(),
    onRevealCurrent: () -> Unit = {},
    qName: String, onName: (String) -> Unit,
    qCountry: String, onCountry: (String) -> Unit,
    qGenre: String, onGenre: (String) -> Unit,
    searchOpen: Boolean = false,
    onSearchOpen: () -> Unit = {},
    onSearch: () -> Unit,
    onRightOpen: () -> Unit = {},
    suggestFor: String = "",
    onSuggestFor: (String) -> Unit = {},
    nameHints: List<String> = emptyList(),
    countryHints: List<String> = emptyList(),
    genreHints: List<String> = emptyList(),
    onMore: () -> Unit,
    canMore: Boolean,
    canMoreSearch: Boolean = false,
    countries: List<String>,
    genres: List<String>,
    onAddTab: () -> Unit,
    pickStation: Station?,
    targetTabs: List<String>,
    onPickTabForStation: (String) -> Unit,
    onCancelPick: () -> Unit,
    newTabOpen: Boolean,
    newTabName: String,
    onNewTabName: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCancelNewTab: () -> Unit,
    customTabs: List<String>,
    editTab: String?,
    editName: String,
    deleteArmed: Boolean,
    onLongTab: (String) -> Unit,
    onEditName: (String) -> Unit,
    onRenameTab: () -> Unit,
    onDeleteTab: () -> Unit,
    onCancelEdit: () -> Unit,
    radioRows: List<Station>,
    searchRows: List<Station> = emptyList(),
    allRadio: List<Station> = emptyList(),
    recentStations: List<Station> = emptyList(),
    localRows: List<LocalTrack>,
    allLocal: List<LocalTrack> = emptyList(),
    showLocal: Boolean,
    name: String,
    genre: String,
    country: String,
    favicon: String,
    currentUrl: String,
    accent: Long,
    themeName: String,
    nowOpen: Boolean,
    onNow: () -> Unit,
    onNowClose: () -> Unit,
    onTheme: () -> Unit,
    onPickTheme: (String) -> Unit = {},
    onDeleteStation: (Station) -> Unit,
    pendingDelete: Station? = null,
    onAskDelete: (Station) -> Unit = {},
    onCancelDelete: () -> Unit = {},
    track: String,
    playing: Boolean,
    status: String,
    favUrls: Set<String>,
    bestUris: Set<String>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onPickRadio: (List<Station>, Int) -> Unit,
    onPickLocal: (List<LocalTrack>, Int) -> Unit,
    onToggleFav: (Station) -> Unit,
    onAddToTab: (Station) -> Unit,
    onDragStart: () -> Unit = {},
    onMoveTo: (Int, Int) -> Unit = { _, _ -> },
    onMoveLocalTo: (Int, Int) -> Unit = { _, _ -> },
    onMoveStation: (Station, Int) -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    posMs: Long,
    durMs: Long,
    isLocalNow: Boolean,
    onToggleBest: (LocalTrack) -> Unit,
    onScan: () -> Unit,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    btWatch: Boolean,
    onBt: () -> Unit,
    sleepLabel: String,
    sleepMenu: Boolean,
    onSleepMenu: () -> Unit,
    onSleep: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    fun artUrl(raw: String): String {
        if (raw.startsWith("http")) return raw
        if (raw.isNotBlank() && raw != "0" && raw.all { it.isDigit() }) {
            return "content://media/external/audio/albumart/$raw"
        }
        return raw
    }

    val acc = Color(accent)
    val bg = Palette.bg
    val card = Palette.card
    val text = Palette.text
    val muted = Palette.muted
    val logoFont = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

    @Composable
    fun PlayBtn(
        playing: Boolean,
        status: String,
        sizeDp: androidx.compose.ui.unit.Dp,
        onClick: () -> Unit,
        shape: androidx.compose.ui.graphics.Shape = AppShapes.hero,
    ) {
        val st = status.lowercase()
        val busy = !playing && (
            st.contains("підключ") || st.contains("буфер") || st == "запуск"
        )
        val pulseOn = playing || busy
        val infinite = rememberInfiniteTransition(label = "playPulse")
        val pulse by infinite.animateFloat(
            initialValue = 1f,
            targetValue = if (busy) 1.09f else 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (busy) 420 else 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "playPulseSc"
        )
        val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pressSc by animateFloatAsState(
            if (pressed) 0.86f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "playPress"
        )
        val sc = (if (pulseOn) pulse else 1f) * pressSc
        Box(
            modifier = Modifier
                .size(sizeDp)
                .graphicsLayer { scaleX = sc; scaleY = sc }
                .background(acc, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(sizeDp * 0.38f),
                    color = Color(0xFF0A0A0C),
                    strokeWidth = 2.5.dp
                )
                playing -> Icon(Icons.Filled.Pause, contentDescription = "Пауза", tint = Color(0xFF0A0A0C), modifier = Modifier.size(sizeDp * 0.42f))
                else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Відтворити", tint = Color(0xFF0A0A0C), modifier = Modifier.size(sizeDp * 0.42f))
            }
        }
    }

    // Спружинена мікроанімація натискання — легкий "bounce" замість плаского tween,
    // перевикористовується на кнопках Попередня/Наступна, Перемішати/Повторити, Обране.
    @Composable
    fun Modifier.springPress(pressedScale: Float = 0.88f, onClick: () -> Unit): Modifier {
        val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val sc by animateFloatAsState(
            if (pressed) pressedScale else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "springPress"
        )
        return this
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    var dropAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pullA = androidx.compose.runtime.remember { Animatable(560f) }
    var sheetShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()
    // Верхня картка (свайп вниз по інфо-панелі)
    val topA = androidx.compose.runtime.remember { Animatable(-780f) }
    var topShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var topSleepOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var topThemeOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var infoDy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // Права картка пошуку (свайп з правого краю / 🔍)
    val rightA = androidx.compose.runtime.remember { Animatable(1f) } // 1=закрито, 0=відкрито
    var rightShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var rightSearchOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    fun openRightSheet() {
        onRightOpen()
        rightShow = true
        rightSearchOpen = true
        sheetScope.launch {
            rightA.stop()
            rightA.snapTo(1f)
            rightA.animateTo(0f, tween(300))
        }
    }
    // Відкривати шторку лише при тапі «Вкладки» внизу, не після вибору жанру в шторці.
    var skipTabsSheetOnce by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(bottomTab) {
        if (bottomTab != "tabs") {
            skipTabsSheetOnce = false
            return@LaunchedEffect
        }
        if (skipTabsSheetOnce) {
            skipTabsSheetOnce = false
            return@LaunchedEffect
        }
        openRightSheet()
    }
    fun closeRightSheet() {
        sheetScope.launch {
            rightA.stop()
            rightA.animateTo(1f, tween(280))
            rightShow = false
        }
    }
    // Ліва картка з локальною музикою видалена — весь її функціонал
    // перенесено у вкладку "Обрані" нижньої навігації.
    fun openTopSheet() {
        topShow = true
        sheetScope.launch {
            topA.snapTo(topA.value.coerceIn(-780f, 0f))
            topA.animateTo(0f, tween(320))
            topShow = true
        }
    }
    fun closeTopSheet() {
        sheetScope.launch {
            topA.animateTo(-780f, tween(280))
            topShow = false
            topSleepOpen = false
            topThemeOpen = false
        }
    }
    LaunchedEffect(nowOpen) {
        if (nowOpen) {
            sheetShow = true
            pullA.animateTo(0f, tween(420))
        } else if (!sheetShow) {
            pullA.snapTo(560f)
        }
    }
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize().background(bg).navigationBarsPadding()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)
    ) {

        // Тост-банер зверху (~2 с)


        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp)) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(card, AppShapes.chip).springPress(0.9f) { topThemeOpen = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Palette, contentDescription = "Тема оформлення", tint = text) }
            }
            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Text("Radio ", color = text, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold))
                Text("S O", color = acc, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold))
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                Box(
                    modifier = Modifier.size(40.dp).background(card, AppShapes.chip).springPress(0.9f) { onMenu() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.MoreVert, contentDescription = "Ще налаштування", tint = text) }
            }
        }
        // Інфо-панель: тап → Now Playing; свайп вниз → верхня картка; свайп вгору більше не відкриває
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(card, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            sheetScope.launch {
                                // як низ: відкрити якщо пройшли ~половину шляху, інакше закрити
                                if (topA.value > -420f) {
                                    topShow = true
                                    topA.animateTo(0f, tween(280))
                                } else {
                                    topA.animateTo(-780f, tween(260))
                                    topShow = false
                                }
                            }
                            infoDy = 0f
                        },
                        onDragCancel = { infoDy = 0f }
                    ) { _, drag ->
                        infoDy += drag
                        if (drag > 0 || topShow || infoDy > 8f) {
                            topShow = true
                            sheetScope.launch {
                                // relative як pullA у нижній картці
                                topA.snapTo((topA.value + drag).coerceIn(-780f, 0f))
                            }
                        }
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
            val glowInf = rememberInfiniteTransition(label = "vizGlow")
            val glowSc = glowInf.animateFloat(
                0.94f, 1.14f,
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                "vizGlowSc"
            ).value
            val glowA = glowInf.animateFloat(
                0.55f, 0.92f,
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                "vizGlowA"
            ).value
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(64.dp, 56.dp)
                    .graphicsLayer {
                        val sc = if (playing) glowSc else 1f
                        scaleX = sc; scaleY = sc
                        alpha = 1f
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                acc.copy(alpha = if (playing) 0.58f else 0.28f),
                                acc.copy(alpha = if (playing) (glowA * 0.58f) else 0.14f),
                                acc.copy(alpha = 0f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 62.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // іконка → нижня картка
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Palette.panel2, AppShapes.hero)
                    .clickable { onCloseMenu(); onNow() },
                contentAlignment = Alignment.Center
            ) {
                if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                    AsyncImage(model = artUrl(favicon), contentDescription = null, modifier = Modifier.size(72.dp).clip(AppShapes.card), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                }
            }
            // текст інфо → верхня картка
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
                    .clickable { onCloseMenu(); openTopSheet() }
            ) {
                Text(name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("жанр: $genre", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("країна: $country", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(if (track.isBlank()) "Трек: невідомо" else track, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(status, color = acc, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // vis replaced by glow overlay
            } // end info Row
            // ⌄ у правому верхньому куті інфо-панелі (розмір як ⌃ знизу)
            Text(
                "⌄",
                color = muted,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .clickable { onCloseMenu(); openTopSheet() }
            )
            } // end info Box overlay
        } // end info Column
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        if (bottomTab == "home") {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Home, contentDescription = null, tint = muted, modifier = Modifier.size(56.dp))
                    Text("Скоро тут щось з'явиться", color = muted, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
        if (tabs.getOrNull(tabIndex) == "search") {
            Column(modifier = Modifier.padding(vertical = 4.dp).background(card, RoundedCornerShape(16.dp)).padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSearchOpen() }.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Пошук", tint = text, modifier = Modifier.size(18.dp))
                    Text(" Пошук…", color = text, modifier = Modifier.weight(1f))
                    Text(if (searchOpen) "▴" else "▾", color = muted)
                }
                if (searchOpen) {
                @Composable fun field(v: String, set: (String) -> Unit, lab: String, key: String, hints: List<String>) {
                    OutlinedTextField(
                        value = v,
                        onValueChange = set,
                        singleLine = true,
                        label = { Text(lab) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text("▾", color = acc, modifier = Modifier.clickable { onSuggestFor(if (suggestFor == key) "" else key) }.padding(8.dp))
                        }
                    )
                    if (suggestFor == key) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .background(card, RoundedCornerShape(12.dp))
                                .padding(6.dp)
                        ) {
                            hints.distinct().take(24).forEach { h ->
                                Text(h, color = text, modifier = Modifier.fillMaxWidth().clickable { set(h); onSuggestFor("") }.padding(6.dp))
                            }
                        }
                    }
                }
                field(qName, onName, "Назва", "name", nameHints)
                field(qCountry, onCountry, "Країна", "country", countryHints)
                field(qGenre, onGenre, "Жанр", "genre", genreHints)
                Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color(0xFF0A0A0C))) { Text("Знайти") }
                }
            }
        }
        if (showLocal) {
            if (localRows.isEmpty()) Text("Немає треків. Натисни «Сканувати».", color = muted)
            LazyColumn(modifier = Modifier.weight(1f), state = listState, userScrollEnabled = !dragging) {
                itemsIndexed(localRows, key = { _, x -> x.uri }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .background(when { dropAt == index -> acc.copy(alpha = 0.40f); item.uri == currentUrl -> acc.copy(alpha = 0.18f); else -> card }, RoundedCornerShape(12.dp))
                            .pointerInput(item.uri, index, tabs.getOrNull(tabIndex)) {
                                if (tabs.getOrNull(tabIndex) != "best") return@pointerInput
                                var acc = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { acc = 0f; dropAt = index; dragging = true; onDragStart() },
                                    onDragEnd = {
                                        val dest = dropAt.coerceIn(0, localRows.lastIndex)
                                        if (dest != index) onMoveLocalTo(index, dest)
                                        acc = 0f
                                        dropAt = -1
                                        dragging = false
                                    },
                                    onDragCancel = { acc = 0f; dropAt = -1; dragging = false }
                                ) { _, drag ->
                                    acc += drag.y
                                    dropAt = (index + (acc / 168f).toInt()).coerceIn(0, localRows.lastIndex)
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    onPickLocal(localRows, index)
                                    onNow()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val a = if (item.albumId.isNotBlank() && item.albumId != "0")
                                "content://media/external/audio/albumart/${item.albumId}" else ""
                            if (a.isNotEmpty()) AsyncImage(model = a, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .clickable { onPickLocal(localRows, index) }
                        ) {
                            Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(
                            if (bestUris.contains(item.uri)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (bestUris.contains(item.uri)) "Прибрати з обраних локальних" else "В обрані локальні",
                            tint = acc,
                            modifier = Modifier.clickable { onToggleBest(item) }.padding(start = 10.dp, end = 2.dp).size(24.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), state = listState, userScrollEnabled = !dragging) {
                itemsIndexed(radioRows, key = { i, s -> s.tab + s.url + i }) { index, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .background(when { dropAt == index -> acc.copy(alpha = 0.40f); s.url == currentUrl -> acc.copy(alpha = 0.18f); else -> card }, RoundedCornerShape(12.dp))
                            .pointerInput(s.url, index) {
                                var acc = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { acc = 0f; dropAt = index; dragging = true; onDragStart() },
                                    onDragEnd = {
                                        val dest = dropAt.coerceIn(0, radioRows.lastIndex)
                                        if (dest != index) onMoveTo(index, dest)
                                        acc = 0f
                                        dropAt = -1
                                        dragging = false
                                    },
                                    onDragCancel = { acc = 0f; dropAt = -1; dragging = false }
                                ) { _, drag ->
                                    acc += drag.y
                                    dropAt = (index + (acc / 168f).toInt()).coerceIn(0, radioRows.lastIndex)
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // іконка → відтворення + відкрити нижню картку
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    onPickRadio(radioRows, index)
                                    onNow()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            } else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                        }
                        // рядок (назва) → лише відтворення, без нижньої картки
                        Column(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f)
                                .clickable { onPickRadio(radioRows, index) }
                        ) {
                            Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.genre} · ${s.country}", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (tabs.getOrNull(tabIndex) == "search") {
                            Text("+", color = acc, modifier = Modifier.clickable { onAddToTab(s) }.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
                        } else {
                            Icon(
                                if (favUrls.contains(s.url)) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (favUrls.contains(s.url)) "Прибрати з улюблених" else "Додати в улюблені",
                                tint = acc,
                                modifier = Modifier.clickable { onToggleFav(s) }.padding(start = 8.dp, end = 2.dp).size(24.dp)
                            )
                            if (tabs.getOrNull(tabIndex) != "fav") {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Видалити станцію",
                                    tint = muted,
                                    modifier = Modifier.clickable { onAskDelete(s) }.padding(start = 8.dp, end = 0.dp).size(22.dp)
                                )
                            }
                        }
                    }
                }
                if (canMore) {
                    item { Button(onClick = onMore, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Ще 100") } }
                }
                // ===== Вкладка "Обрані": далі йдуть обрані локальні треки та вся локальна музика =====
                // Серце (heart): лише обрані локальні (best). Станції — на зірці (stations).
                if (bottomTab == "heart" || bottomTab == "library") {
                    item {
                        Text(
                            "Обрані локальні",
                            color = muted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    if (bestRows.isEmpty()) {
                        item {
                            Text(
                                "Немає обраних локальних. Додай ♥ у «Музика».",
                                color = muted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                    itemsIndexed(bestRows, key = { i, x -> "best-" + x.uri + i }) { _, item ->
                        LocalTrackRow(
                            item,
                            item.uri == currentUrl,
                            acc,
                            muted,
                            text,
                            isBest = true,
                            onToggleBest = { onToggleBest(item) },
                        ) {
                            onPickLocal(bestRows, bestRows.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)); onNow()
                        }
                    }
                }
            }
        }
        }
        // Рядок жанрових вкладок перенесено у праву панель (RightTabsPanel).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
                .background(card, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (!nowOpen) {
                            sheetScope.launch {
                                if (pullA.value < 300f) {
                                    pullA.animateTo(0f, tween(280))
                                    onNow()
                                } else {
                                    pullA.animateTo(560f, tween(280))
                                    sheetShow = false
                                }
                            }
                        }
                    }
                ) { _, drag ->
                    if (drag < 0 || sheetShow) {
                        sheetShow = true
                        sheetScope.launch { pullA.snapTo((pullA.value + drag).coerceIn(0f, 560f)) }
                    }
                }
            }
        ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(68.dp)
                .background(card, RoundedCornerShape(20.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.fillMaxHeight().width(52.dp).background(card, RoundedCornerShape(20.dp)).clickable { onPrev() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Попередня станція",
                    tint = text,
                    modifier = Modifier.size(30.dp)
                )
            }
            PlayBtn(playing = playing, status = status, sizeDp = 60.dp, onClick = onPlayPause, shape = RoundedCornerShape(14.dp))
            Box(
                modifier = Modifier.fillMaxHeight().width(52.dp).background(card, RoundedCornerShape(20.dp)).clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Наступна станція",
                    tint = text,
                    modifier = Modifier.size(30.dp)
                )
            }
            if (tabs.getOrNull(tabIndex) == "local") {
                Box(
                    modifier = Modifier.size(40.dp).background(Palette.panel.copy(alpha = 0.90f), RoundedCornerShape(12.dp)).clickable { onScan() },
                    contentAlignment = Alignment.Center
                ) { Text("Скан", color = acc, style = MaterialTheme.typography.labelSmall) }
            }
        }
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Відкрити Now Playing", tint = muted, modifier = Modifier.align(Alignment.CenterEnd).clickable { onNow() }.padding(4.dp).size(28.dp))
        }
        BottomNavBar(
            current = bottomTab,
            onSelect = onBottomTab,
            acc = acc,
            muted = muted,
            card = card,
            onPull = { drag ->
                if (drag < 0 || sheetShow) {
                    sheetShow = true
                    sheetScope.launch { pullA.snapTo((pullA.value + drag).coerceIn(0f, 560f)) }
                }
            },
            onPullEnd = {
                if (!nowOpen) {
                    sheetScope.launch {
                        if (pullA.value < 300f) {
                            pullA.animateTo(0f, tween(280))
                            onNow()
                        } else {
                            pullA.animateTo(560f, tween(280))
                            sheetShow = false
                        }
                    }
                }
            }
        )
    }
    // ===== Верхня картка (свайп вниз) =====
    if (topShow) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000).copy(alpha = ((780f + topA.value) / 780f * 0.5f).coerceIn(0f, 0.5f)))
                    // закриття лише свайпом вгору
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .graphicsLayer {
                        translationY = topA.value
                        // дзеркало низу: closed(-780)≈0.45, open(0)=1
                        val sc = (1f + topA.value / 1400f).coerceIn(0.45f, 1f)
                        scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    }
                    .background(Palette.card, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .statusBarsPadding()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                sheetScope.launch {
                                    if (topA.value < -140f) {
                                        topA.animateTo(-780f, tween(280))
                                        topShow = false
                                        topSleepOpen = false
                                        topThemeOpen = false
                                    } else {
                                        topA.animateTo(0f, tween(280))
                                        topShow = true
                                    }
                                }
                            }
                        ) { _, drag ->
                            sheetScope.launch { topA.snapTo((topA.value + drag).coerceIn(-780f, 0f)) }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(muted, RoundedCornerShape(2.dp))
                        .align(Alignment.CenterHorizontally)
                )
                // Досьє (більше + маленький viz)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Palette.panel2, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val u = artUrl(favicon)
                        if (u.startsWith("http") || u.startsWith("content:")) {
                            AsyncImage(model = u, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        } else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                    }
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (track.isBlank()) "Трек: невідомо" else track,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(status, color = acc, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        val topInf = rememberInfiniteTransition(label = "topDossierViz")
                        val pulseA = topInf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), "pa").value
                        val pulseB = topInf.animateFloat(0.45f, 1f, infiniteRepeatable(tween(560), RepeatMode.Reverse), "pb").value
                        val pulseC = topInf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(480), RepeatMode.Reverse), "pc").value
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(18.dp).padding(top = 4.dp)) {
                            listOf(0.35f, 0.7f, 0.5f, 1f, 0.45f, 0.85f, 0.4f, 0.65f).forEachIndexed { i, base ->
                                val p = when (i % 3) { 0 -> pulseA; 1 -> pulseB; else -> pulseC }
                                Box(
                                    modifier = Modifier.padding(horizontal = 1.dp).width(3.dp)
                                        .height((if (playing) 4f + 12f * base * p else 3f).dp)
                                        .background(acc.copy(alpha = if (playing) 0.55f + 0.45f * p else 0.35f), RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                    val isLocalCard = showLocal || currentUrl.startsWith("content:")
                    Icon(
                        if (if (isLocalCard) bestUris.contains(currentUrl) else favUrls.contains(currentUrl))
                            Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (if (isLocalCard) bestUris.contains(currentUrl) else favUrls.contains(currentUrl))
                            "Прибрати з улюблених" else "Додати в улюблені",
                        tint = acc,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(32.dp)
                            .clickable {
                                if (isLocalCard) {
                                    localRows.firstOrNull { it.uri == currentUrl }?.let { onToggleBest(it) }
                                } else {
                                    onToggleFav(Station(currentUrl, name, genre, country, favicon, "fav"))
                                }
                            }
                    )
                }
                // Схожі з усіх вкладок (той самий жанр), 8 шт, по 4 в ряд
                val poolAll = (if (allRadio.isNotEmpty()) allRadio else radioRows).filter { it.url != currentUrl }
                val similarRadio: List<Station> = if (!showLocal) {
                    val same = if (genre.isNotBlank())
                        poolAll.filter {
                            it.genre.contains(genre, ignoreCase = true) ||
                                (genre.isNotBlank() && it.genre.isNotBlank() && genre.contains(it.genre, ignoreCase = true))
                        }
                    else emptyList()
                    (same + poolAll.filter { s -> same.none { it.url == s.url } }).take(8)
                } else emptyList()
                val similarLocal: List<LocalTrack> = if (showLocal) {
                    localRows.filter { it.uri != currentUrl }.take(8)
                } else emptyList()
                val simCount = if (showLocal) similarLocal.size else similarRadio.size
                if (simCount > 0) {
                    Text(
                        if (showLocal) "Ще з local"
                        else if (genre.isNotBlank()) "Жанр: $genre"
                        else "Схожі станції",
                        color = muted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )
                    val rows: List<List<Int>> = (0 until simCount).toList().chunked(4)
                    rows.forEach { idxs ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            idxs.forEach { i ->
                                if (showLocal) {
                                    val tr = similarLocal[i]
                                    val iu = if (tr.albumId.isNotBlank() && tr.albumId != "0")
                                        "content://media/external/audio/albumart/${tr.albumId}" else ""
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val idx = localRows.indexOfFirst { it.uri == tr.uri }
                                                if (idx >= 0) onPickLocal(localRows, idx)
                                            }
                                            .padding(horizontal = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Palette.panel2, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (iu.startsWith("content:")) {
                                                AsyncImage(model = iu, contentDescription = null, modifier = Modifier.size(56.dp).clip(AppShapes.card), contentScale = ContentScale.Crop)
                                            } else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                                        }
                                        Text(tr.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
                                    }
                                } else {
                                    val s = similarRadio[i]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val idx = radioRows.indexOfFirst { it.url == s.url }
                                                if (idx >= 0) onPickRadio(radioRows, idx)
                                                else onPickRadio(listOf(s), 0)
                                            }
                                            .padding(horizontal = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Palette.panel2, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                                AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(56.dp).clip(AppShapes.card), contentScale = ContentScale.Crop)
                                            } else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                                        }
                                        Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
                                    }
                                }
                            }
                            repeat(4 - idxs.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                // Історія — 8 (4×2)
                if (!showLocal && recentStations.isNotEmpty()) {
                    Text(
                        "Історія",
                        color = muted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                    // не ховаємо всю історію, якщо поточна = остання
                    val hist = recentStations.take(8)
                    hist.chunked(4).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            chunk.forEach { s ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onPickRadio(listOf(s), 0) }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Palette.panel2, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                            AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(56.dp).clip(AppShapes.card), contentScale = ContentScale.Crop)
                                        } else Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                                    }
                                    Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                            repeat(4 - chunk.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                // Дії: ряд1 сон/BT/тема, ряд2 експорт/імпорт
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Palette.panel, RoundedCornerShape(12.dp))
                            .clickable { topSleepOpen = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Timer, contentDescription = null, tint = text, modifier = Modifier.size(18.dp))
                            Text(sleepLabel, color = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (btWatch) acc.copy(alpha = 0.25f) else Palette.panel, RoundedCornerShape(12.dp))
                            .clickable { onBt() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (btWatch) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (btWatch) acc else text,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(if (btWatch) "BT: увімкнено" else "BT: вимкнено", color = if (btWatch) acc else text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.75f)
                            .background(Palette.panel, RoundedCornerShape(12.dp))
                            .clickable { topThemeOpen = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DarkMode, contentDescription = "Обрати тему оформлення", tint = acc)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Palette.panel, RoundedCornerShape(12.dp))
                            .clickable { onExport() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.FileUpload, contentDescription = "Експорт налаштувань", tint = text, modifier = Modifier.size(18.dp))
                            Text("Експорт", color = text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Palette.panel, RoundedCornerShape(12.dp))
                            .clickable { onImport() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Імпорт налаштувань", tint = text, modifier = Modifier.size(18.dp))
                            Text("Імпорт", color = text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    // ===== Права картка: жанрові та кастомні вкладки =====
    // Край поверх картки під час відкриття. Повністю відкриту — край вимкнено
    // (повторний свайп вліво більше не закриває).
    val rightFullyOpen = rightShow && rightA.value <= 0.05f
    val showRightEdge = !topShow && !nowOpen && !sheetShow && !rightFullyOpen

    RightTabsPanel(
        rightA = rightA,
        rightShow = rightShow,
        onRightShow = { rightShow = it },
        showRightEdge = showRightEdge,
        onRightOpen = onRightOpen,
        tabs = tabs,
        tabIndex = tabIndex,
        onTab = { i ->
            onTab(i)
            // підсвітити «Вкладки» внизу; не відкривати шторку знову після close
            skipTabsSheetOnce = true
            if (bottomTab != "tabs") onBottomTab("tabs")
            else skipTabsSheetOnce = false // вже на tabs — прапор не потрібен
        },
        onAddTab = onAddTab,
        onLongTab = onLongTab,
        acc = acc, muted = muted, text = text, card = card,
    )


    if (topSleepOpen) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = { topSleepOpen = false },
            title = { Text("Таймер сну", color = text) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(15, 30, 60, 0).forEach { m ->
                        Box(
                            modifier = Modifier
                                .background(Palette.panel2, RoundedCornerShape(12.dp))
                                .clickable {
                                    onSleep(m)
                                    topSleepOpen = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(if (m == 0) "Вимкнено" else "${m} хв", color = acc)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(onClick = { topSleepOpen = false }) { Text("Закрити") }
            }
        )
    }
    if (topThemeOpen) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = { topThemeOpen = false },
            title = { Text("Тема", color = text) },
            text = {
                Column {
                    // 4 в ряд
                    ThemeStore.all.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { th ->
                                val selected = th.id == themeName
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(th.accent), RoundedCornerShape(12.dp))
                                        .then(
                                            if (selected) Modifier.border(2.dp, text, RoundedCornerShape(12.dp))
                                            else Modifier
                                        )
                                        .clickable {
                                            onPickTheme(th.id)
                                            topThemeOpen = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(onClick = { topThemeOpen = false }) { Text("Закрити") }
            }
        )
    }
    // Хедер 🌙 теж відкриває вибір теми

        // Меню строго під кнопкою ⋯ (TopEnd + відступ під хедер)
    androidx.compose.animation.AnimatedVisibility(
        visible = menuOpen,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().clickable { onCloseMenu() })
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = 12.dp)
                    .width(200.dp)
                    .background(Palette.panel2, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                val ctxForTheme = LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clickable { Palette.toggle(ctxForTheme) }.padding(8.dp)) {
                    Icon(
                        if (Palette.isLight) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (Palette.isLight) "Світла тема" else "Темна тема",
                        tint = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(if (Palette.isLight) "Світла тема" else "Темна тема", color = text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onBt(); onCloseMenu() }.padding(8.dp)
                ) {
                    Icon(
                        if (btWatch) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                        contentDescription = if (btWatch) "BT відстеження увімкнено" else "BT відстеження вимкнено",
                        tint = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(if (btWatch) "BT увімк" else "BT вимк", color = text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSleepMenu() }.padding(8.dp)
                ) {
                    Icon(Icons.Filled.Timer, contentDescription = "Таймер сну", tint = text, modifier = Modifier.size(20.dp))
                    Text(sleepLabel, color = text)
                }
                if (sleepMenu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 0).forEach { m ->
                            Text(if (m == 0) "Вимкнено" else "${m}хв", color = acc, modifier = Modifier.clickable { onSleep(m); onCloseMenu() }.padding(6.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().clickable { onExport() }.padding(8.dp)) {
                    Icon(Icons.Filled.FileUpload, contentDescription = "Експорт налаштувань", tint = text, modifier = Modifier.size(18.dp))
                    Text("Експорт", color = text)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().clickable { onImport() }.padding(8.dp)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = "Імпорт налаштувань", tint = text, modifier = Modifier.size(18.dp))
                    Text("Імпорт", color = text)
                }
            }
        }
    }
    }
    if (pickStation != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = onCancelPick,
            title = { Text("Виберіть вкладку") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    targetTabs.forEach { tab ->
                        Text(tabLabel(tab), modifier = Modifier.fillMaxWidth().clickable { onPickTabForStation(tab) }.padding(12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { Button(onClick = onCancelPick) { Text("Скасувати") } }
        )
    }
    if (newTabOpen) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = onCancelNewTab,
            title = { Text("Створити нову вкладку") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTabName,
                        onValueChange = onNewTabName,
                        singleLine = true,
                        label = { Text("Назва") },
                        supportingText = {
                            Text("ua/en літери, цифри, _ - ; до 10 символів")
                        }
                    )
                }
            },
            confirmButton = { Button(onClick = onCreateTab) { Text("Створити") } },
            dismissButton = { Button(onClick = onCancelNewTab) { Text("Скасувати") } }
        )
    }
    if (editTab != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = onCancelEdit,
            title = { Text("Вкладка $editTab") },
            text = { OutlinedTextField(value = editName, onValueChange = onEditName, singleLine = true) },
            confirmButton = { Button(onClick = onRenameTab) { Text("Перейменувати") } },
            dismissButton = {
                Row {
                    Button(onClick = onDeleteTab) { Text(if (deleteArmed) "Точно видалити?" else "Видалити") }
                    Button(onClick = onCancelEdit) { Text("Скасувати") }
                }
            }
        )
    }
    if (pendingDelete != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = onCancelDelete,
            title = { Text("Видалити станцію?") },
            text = { Text(pendingDelete?.name ?: "") },
            confirmButton = {
                Button(onClick = {
                    pendingDelete?.let { onDeleteStation(it) }
                    onCancelDelete()
                }) { Text("Так") }
            },
            dismissButton = { Button(onClick = onCancelDelete) { Text("Ні") } }
        )
    }
    if (nowOpen || sheetShow) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // без затемнення — інфо-панель зверху лишається читабельною
                    .clickable {
                sheetScope.launch {
                    pullA.animateTo(560f, tween(300))
                    sheetShow = false
                    onNowClose()
                }
            })
            val arts: List<String> = if (showLocal) {
                localRows.map { if (it.albumId.isNotBlank() && it.albumId != "0") "content://media/external/audio/albumart/${it.albumId}" else "" }
            } else radioRows.map { it.favicon }
            val curI0 = if (showLocal) localRows.indexOfFirst { it.uri == currentUrl } else radioRows.indexOfFirst { it.url == currentUrl }
            val curI = if (curI0 >= 0) curI0 else 0
            // Динамічний колір з поточної обкладинки (як у Spotify) — для розмитого фону картки Now Playing
            val artCtx = LocalContext.current
            val currentArt = arts.getOrNull(curI) ?: ""
            var dynamicArtColor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Color?>(null) }
            LaunchedEffect(currentArt) {
                if (currentArt.startsWith("http") || currentArt.startsWith("content:")) {
                    try {
                        val extracted = withContext(Dispatchers.IO) {
                            val req = ImageRequest.Builder(artCtx).data(currentArt).allowHardware(false).size(120, 120).build()
                            val result = artCtx.imageLoader.execute(req)
                            val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                            if (bmp != null) {
                                val sw = SwatchPalette.from(bmp).generate()
                                val c = sw.vibrantSwatch?.rgb ?: sw.dominantSwatch?.rgb ?: sw.mutedSwatch?.rgb
                                if (c != null) Color(c) else null
                            } else null
                        }
                        dynamicArtColor = extracted
                    } catch (e: Exception) {
                        dynamicArtColor = null
                    }
                } else dynamicArtColor = null
            }
            val dynamicBg by animateColorAsState(
                targetValue = dynamicArtColor ?: acc,
                animationSpec = tween(650),
                label = "dynamicBg"
            )
            val stripState = rememberLazyListState()
            val pageCount = arts.size.coerceAtLeast(1)
            val pagerState = rememberPagerState(
                initialPage = curI.coerceIn(0, pageCount - 1)
            ) { pageCount }
            var pagerUserDrag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            // Зовнішня зміна станції (⏮⏭ / список) → підкрутити pager
            LaunchedEffect(curI, pageCount) {
                val target = curI.coerceIn(0, pageCount - 1)
                if (!pagerState.isScrollInProgress && pagerState.settledPage != target) {
                    pagerState.animateScrollToPage(target)
                }
            }
            // Користувач доскролив сторінку → реально змінити станцію
            LaunchedEffect(pagerState.settledPage) {
                if (pagerUserDrag) return@LaunchedEffect
                val i = pagerState.settledPage
                if (arts.isEmpty()) return@LaunchedEffect
                if (showLocal) {
                    if (i in localRows.indices && localRows[i].uri != currentUrl) {
                        onPickLocal(localRows, i)
                    }
                } else {
                    if (i in radioRows.indices && radioRows[i].url != currentUrl) {
                        onPickRadio(radioRows, i)
                    }
                }
            }
            LaunchedEffect(curI) {
                if (arts.isNotEmpty()) stripState.animateScrollToItem((curI - 3).coerceAtLeast(0))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .graphicsLayer {
                        translationY = pullA.value
                        val sc = (1f - pullA.value / 900f).coerceIn(0.45f, 1f)
                        scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Palette.card)
            ) {
                // Розмитий кольоровий фон з поточної обкладинки (dynamic color, ефект як у Spotify)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    dynamicBg.copy(alpha = 0.55f),
                                    dynamicBg.copy(alpha = 0.20f),
                                    Color.Transparent
                                ),
                                radius = 1100f
                            )
                        )
                        .blur(80.dp, BlurredEdgeTreatment.Unbounded)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    sheetScope.launch {
                                        if (pullA.value > 140f) {
                                            pullA.animateTo(560f, tween(280))
                                            sheetShow = false
                                            onNowClose()
                                        } else pullA.animateTo(0f, tween(280))
                                    }
                                }
                            ) { _, drag -> sheetScope.launch { pullA.snapTo((pullA.value + drag).coerceIn(0f, 560f)) } }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Box(modifier = Modifier.padding(bottom = 8.dp).width(40.dp).height(4.dp).background(muted, RoundedCornerShape(2.dp)))
                // Page-style: сусідні обкладинки видно, свайп як ViewPager
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(318.dp),
                        key = { page ->
                            if (showLocal) localRows.getOrNull(page)?.uri ?: "p$page"
                            else radioRows.getOrNull(page)?.url ?: "p$page"
                        }
                    ) { page ->
                        val dist = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val abs = kotlin.math.abs(dist).coerceIn(0f, 1f)
                        val scale = 1f - 0.14f * abs
                        val alpha = 1f - 0.38f * abs
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(300.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(acc.copy(alpha = 0.35f), Color.Transparent),
                                            radius = 420f
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                            Box(
                                modifier = Modifier
                                    .size(300.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Спочатку — звичайна іконка. Якщо відомий виконавець (для
                                // локального треку — з тегів, для радіо — з ICY на сторінці, що
                                // зараз грає), підміняємо іконку на його фото з відкритого API.
                                // Фавікон станції у великій обкладинці більше не показуємо.
                                // Спочатку -- іконка/фавікон станції. Якщо відомий виконавець (для
                                // локального треку -- з тегів, для радіо -- з ICY на сторінці, що
                                // зараз грає), підміняємо на його фото з відкритого API.
                                val pageArtist = if (showLocal) {
                                    localRows.getOrNull(page)?.artist ?: ""
                                } else if (page == curI) {
                                    artistFromTrackTitle(track)
                                } else ""
                                val artistPhoto by rememberArtistPhotoUrl(pageArtist)
                                val photo = artistPhoto
                                val fallbackArt = arts.getOrNull(page) ?: ""
                                when {
                                    photo != null -> AsyncImage(
                                        model = photo,
                                        contentDescription = null,
                                        modifier = Modifier.size(300.dp).clip(AppShapes.card),
                                        contentScale = ContentScale.Crop
                                    )
                                    fallbackArt.startsWith("http") || fallbackArt.startsWith("content:") -> AsyncImage(
                                        model = fallbackArt,
                                        contentDescription = null,
                                        modifier = Modifier.size(300.dp).clip(AppShapes.card),
                                        contentScale = ContentScale.Crop
                                    )
                                    else -> Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted, modifier = Modifier.size(96.dp))
                                }
                            }
                            }
                        }
                    }
                    val pagerDragModifier: Modifier =
                        if (!showLocal && !isLocalNow && !currentUrl.startsWith("content:") && arts.isNotEmpty()) {
                            Modifier.pointerInput(currentUrl, pageCount) {
                                detectHorizontalDragGestures(
                                    onDragStart = { pagerUserDrag = true },
                                    onDragEnd = {
                                        val page = pagerState.currentPage
                                        val off = pagerState.currentPageOffsetFraction
                                        val target = when {
                                            off > 0.28f -> (page + 1).coerceAtMost(pageCount - 1)
                                            off < -0.28f -> (page - 1).coerceAtLeast(0)
                                            else -> page
                                        }
                                        sheetScope.launch {
                                            pagerState.animateScrollToPage(target)
                                            pagerUserDrag = false
                                            // зміна станції лише після відпускання
                                            if (showLocal) {
                                                if (target in localRows.indices && localRows[target].uri != currentUrl)
                                                    onPickLocal(localRows, target)
                                            } else {
                                                if (target in radioRows.indices && radioRows[target].url != currentUrl)
                                                    onPickRadio(radioRows, target)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        sheetScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage)
                                            pagerUserDrag = false
                                        }
                                    }
                                ) { _, drag ->
                                    // синхронно за пальцем, без окремих launch-гонок
                                    pagerState.dispatchRawDelta(-drag)
                                }
                            }
                        } else Modifier

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .then(pagerDragModifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            color = text,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val isLocalCard = showLocal || currentUrl.startsWith("content:")
                        if (isLocalCard) {
                            val on = bestUris.contains(currentUrl)
                            Icon(
                                if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (on) "Прибрати з топ локальних" else "Додати в топ локальні",
                                tint = acc,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(28.dp)
                                    .springPress(0.75f) {
                                        val t = localRows.firstOrNull { it.uri == currentUrl }
                                        if (t != null) onToggleBest(t)
                                    }
                            )
                        } else {
                            val on = favUrls.contains(currentUrl)
                            Icon(
                                if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (on) "Прибрати з улюблених" else "Додати в улюблені",
                                tint = acc,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(28.dp)
                                    .springPress(0.75f) {
                                        onToggleFav(
                                            Station(
                                                currentUrl,
                                                name,
                                                genre,
                                                country,
                                                favicon,
                                                "fav"
                                            )
                                        )
                                    }
                            )
                        }
                    }
                    Text(
                        if (track.isBlank()) "Трек: невідомо" else track,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp)
                            .then(pagerDragModifier)
                    )
                }
                if (isLocalNow || currentUrl.startsWith("content:")) {
                    // shuffle | час | прогрес | тривалість | repeat — один ряд, без зайвої вертикалі
                    MiniProgressBar(
                        posMs, durMs, acc, muted, onSeek,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        controlsTint = text,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
                    if (arts.isNotEmpty()) {
                        LazyRow(state = stripState, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(arts) { i, u ->
                                val label = when {
                                    showLocal && i in localRows.indices -> localRows[i].title
                                    i in radioRows.indices -> radioRows[i].name
                                    else -> ""
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .clickable {
                                            if (showLocal && i in localRows.indices) onPickLocal(localRows, i)
                                            else if (i in radioRows.indices) onPickRadio(radioRows, i)
                                        }
                                ) {
                                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                        val target = if (i == curI) 54.dp else 42.dp
                                        val sz by androidx.compose.animation.core.animateDpAsState(target, label = "stripSz")
                                        val alpha by androidx.compose.animation.core.animateFloatAsState(if (i == curI) 1f else 0.72f, label = "stripA")
                                        if (u.startsWith("http") || u.startsWith("content:")) {
                                            AsyncImage(
                                                model = u,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(sz)
                                                    .graphicsLayer { this.alpha = alpha }
                                                    .clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted, modifier = Modifier.graphicsLayer { this.alpha = alpha })
                                    }
                                    Text(
                                        label,
                                        color = if (i == curI) text else muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 2.dp).fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Palette.panel, RoundedCornerShape(16.dp))
                            .springPress { onPrev() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Попередня станція", tint = text, modifier = Modifier.size(40.dp))
                    }
                    PlayBtn(playing = playing, status = status, sizeDp = 80.dp, onClick = onPlayPause)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Palette.panel, RoundedCornerShape(16.dp))
                            .springPress { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Наступна станція", tint = text, modifier = Modifier.size(40.dp))
                    }
                }
                }
            }
        }
    }

}

@Composable
private fun BoxScope.RightTabsPanel(
    rightA: Animatable<Float, *>,
    rightShow: Boolean,
    onRightShow: (Boolean) -> Unit,
    showRightEdge: Boolean,
    onRightOpen: () -> Unit,
    tabs: List<String>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    onLongTab: (String) -> Unit,
    acc: Color, muted: Color, text: Color, card: Color,
) {
    val sheetScope = rememberCoroutineScope()
    fun closeRightSheet() {
        sheetScope.launch {
            rightA.stop()
            rightA.animateTo(1f, tween(280))
            onRightShow(false)
        }
    }
    if (rightShow || rightA.value < 0.999f) {
        val cfg = LocalConfiguration.current
        val density = LocalDensity.current
        val sheetW = (cfg.screenWidthDp * 0.58f).dp
        val sheetWpx = with(density) { sheetW.toPx() }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0x88000000).copy(
                            alpha = ((1f - rightA.value) * 0.5f).coerceIn(0f, 0.5f)
                        )
                    )
                    .clickable { closeRightSheet() }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(sheetW)
                    .graphicsLayer { translationX = rightA.value * sheetWpx }
                    .background(
                        Palette.card,
                        RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(sheetWpx) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var locked = false
                            var accX = 0f
                            var accY = 0f
                            var finished = false
                            while (!finished) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) {
                                    finished = true
                                    break
                                }
                                val dx = ch.positionChange().x
                                val dy = ch.positionChange().y
                                accX += dx
                                accY += dy
                                if (!locked) {
                                    if (kotlin.math.abs(accX) > slop || kotlin.math.abs(accY) > slop) {
                                        if (kotlin.math.abs(accX) > kotlin.math.abs(accY) && accX > 0f) locked = true
                                        else break
                                    }
                                }
                                if (locked) {
                                    ch.consume()
                                    val next = (rightA.value + dx / sheetWpx).coerceIn(0f, 1f)
                                    sheetScope.launch { rightA.snapTo(next) }
                                }
                            }
                            if (locked) {
                                sheetScope.launch {
                                    rightA.stop()
                                    if (rightA.value >= 0.07f) {
                                        rightA.animateTo(1f, tween(280))
                                        onRightShow(false)
                                    } else {
                                        rightA.animateTo(0f, tween(280))
                                        onRightShow(true)
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).background(muted, RoundedCornerShape(2.dp)))
                }
                Text(
                    "Вкладки",
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    "Тап — відкрити · утримання — змінити",
                    color = muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                val genreTabs = tabs.withIndex().filter { it.value !in listOf("fav", "best", "local", "search") }
                if (genreTabs.isEmpty()) {
                    Text(
                        "Поки немає жанрових вкладок",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                // reverseLayout: перший item знизу — список росте вгору
                LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                    // «+» першим у коді → візуально внизу картки
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(acc.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .clickable { onAddTab() }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("+", color = acc, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 10.dp))
                            Text("Додати вкладку", color = acc, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    itemsIndexed(genreTabs, key = { _, iv -> "tab-" + iv.value }) { _, iv ->
                        val (i, tab) = iv
                        val selected = i == tabIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .background(
                                    if (selected) acc.copy(alpha = 0.22f) else card.copy(alpha = 0.55f),
                                    RoundedCornerShape(14.dp)
                                )
                                .combinedClickable(
                                    onClick = { onTab(i); closeRightSheet() },
                                    onLongClick = { onLongTab(tab) }
                                )
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Category,
                                contentDescription = null,
                                tint = if (selected) acc else muted,
                                modifier = Modifier.size(20.dp).padding(end = 2.dp)
                            )
                            Text(
                                tabLabel(tab),
                                color = if (selected) acc else text,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 10.dp)
                            )
                            if (selected) {
                                Text("●", color = acc, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
    if (showRightEdge) {
        val dens = LocalDensity.current
        val cfg = LocalConfiguration.current
        val sheetWpxEdge = with(dens) { (cfg.screenWidthDp * 0.58f).dp.toPx() }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(22.dp).pointerInput(sheetWpxEdge) {
                var dragged = false
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val was = dragged
                        dragged = false
                        if (!was) return@detectHorizontalDragGestures
                        sheetScope.launch {
                            rightA.stop()
                            if (rightA.value <= 0.93f) {
                                onRightShow(true)
                                rightA.animateTo(0f, tween(280)); rightA.snapTo(0f)
                            } else {
                                rightA.animateTo(1f, tween(260)); rightA.snapTo(1f); onRightShow(false)
                            }
                        }
                    },
                    onDragCancel = {
                        val was = dragged
                        dragged = false
                        if (!was) return@detectHorizontalDragGestures
                        sheetScope.launch {
                            rightA.stop()
                            if (rightA.value <= 0.93f) {
                                onRightShow(true)
                                rightA.animateTo(0f, tween(280)); rightA.snapTo(0f)
                            } else {
                                rightA.animateTo(1f, tween(240)); rightA.snapTo(1f); onRightShow(false)
                            }
                        }
                    }
                ) { _, drag ->
                    if (!dragged) {
                        if (drag >= -0.5f) return@detectHorizontalDragGestures
                        dragged = true
                        onRightShow(true); onRightOpen()
                    }
                    val next = (rightA.value + drag / sheetWpxEdge).coerceIn(0f, 1f)
                    sheetScope.launch { rightA.snapTo(next) }
                }
            }
        )
    }
}
