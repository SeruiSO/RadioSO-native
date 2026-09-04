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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.seruiso.radio1.ui.theme.RadioSOTheme
import org.json.JSONArray

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
            statusText = "помилка імпорту"
        }
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
                    if (isPlaying) statusText = "playing"
                    else if (statusText == "playing") statusText = "pause"
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
        setContent {
            RadioSOTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val tab = uiTabs.getOrNull(tabIndex) ?: ""
                    StationScreen(
                        tabs = uiTabs,
                        tabIndex = tabIndex,
                        onTab = {
                            tabIndex = it
                            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                                .edit().putString("currentTab", uiTabs.getOrNull(it) ?: "fav").apply()
                        },
                        
                        qName = qName, onName = { qName = it },
                        qCountry = qCountry, onCountry = { qCountry = it },
                        qGenre = qGenre, onGenre = { qGenre = it },
                        searchOpen = searchOpen,
                        onSearchOpen = { searchOpen = !searchOpen },
                        onSearch = { runSearch(); searchOpen = false },
                        suggestFor = suggestFor,
                        onSuggestFor = { suggestFor = it },
                        nameHints = SearchHints.past(this) + SearchHints.names,
                        countryHints = SearchHints.countries,
                        genreHints = SearchHints.genres,
                        onMore = { loadMoreSearch() },
                        canMore = searchShown < searchAll.size && currentTab() == "search",
                        countries = RadioBrowser.countries,
                        genres = RadioBrowser.genres,
                        onAddTab = { newTabOpen = true },
                        pickStation = pickStation,
                        targetTabs = targetTabs(),
                        onPickTabForStation = { tab ->
                            val s = pickStation
                            if (s != null) {
                                val err = TabStore.addStation(this, tab, s)
                                statusText = err ?: "додано в $tab"
                                if (err == null) addedRev++
                            }
                            pickStation = null
                        },
                        onCancelPick = { pickStation = null },
                        newTabOpen = newTabOpen,
                        newTabName = newTabName,
                        onNewTabName = { newTabName = it },
                        onCreateTab = {
                            val err = TabStore.addTab(this, newTabName)
                            if (err == null) {
                                customTabs = TabStore.customTabs(this)
                                statusText = "вкладка ${newTabName.lowercase()} створена"
                                newTabName = ""
                                newTabOpen = false
                            } else statusText = err
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
                            val err = TabStore.renameTab(this, oldName, editName)
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
                        radioRows = visibleRadio(),
                        allRadio = allRadioStations(),
                        recentStations = recentStations,
                        localRows = visibleLocal(),
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
                                toggleFav(s)
                            } else {
                                TabStore.removeStation(this, tab, s.url)
                                val rest = visibleRadio().map { it.url }.filter { it != s.url }
                                TabStore.saveOrder(this, tab, rest)
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
                            statusText = if (v) "shuffle on" else "shuffle off"
                        },
                        onRepeat = {
                            val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                            val cur = p.getString(LocalMusicPlugin.KEY_LOCAL_REPEAT, "off")
                            val next = when (cur) { "off" -> "all"; "all" -> "one"; else -> "off" }
                            p.edit().putString(LocalMusicPlugin.KEY_LOCAL_REPEAT, next).apply()
                            statusText = "repeat $next"
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

    private fun tabLabel(tab: String): String = when (tab.lowercase()) {
        "fav" -> "Best"
        "best" -> "Lokal Best"
        "local" -> "Lokal"
        "search" -> "SEARCH"
        "ukraine", "ua" -> "UA"
        "techno" -> "Techno"
        "trance" -> "Trance"
        "pop" -> "Pop"
        else -> tab.replaceFirstChar { it.uppercase() }
    }

    private fun targetTabs(): List<String> {
        val built = sourceTabs.filter { it !in TabStore.reserved && it != "search" }
        return (built + customTabs).distinct()
    }

    private fun runSearch() {
        val n = qName.trim()
        val c = normalizeCountry(qCountry)
        qCountry = c
        val g = qGenre.trim()
        if (n.isNotBlank()) {
            val past = SearchHints.past(this).toMutableList()
            past.remove(n)
            past.add(0, n)
            SearchHints.savePast(this, past.take(5))
        }
        if (n.isBlank() && c.isBlank() && g.isBlank()) {
            statusText = "введи назву, країну або жанр"
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
                statusText = if (searchAll.isEmpty()) "нічого не знайдено" else "знайдено: ${searchAll.size}"
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
            statusText = "таймер сну: пауза"
        }
        sleepRunnable = r
        sleepHandler.postDelayed(r, mins * 60_000L)
        sleepMenu = false
    }

    private fun normalizeCountry(raw: String): String {
        val m = mapOf(
            "ukraine" to "Ukraine", "ua" to "Ukraine", "italy" to "Italy",
            "german" to "Germany", "germany" to "Germany", "france" to "France",
            "spain" to "Spain", "usa" to "United States", "us" to "United States",
            "uk" to "United Kingdom", "united kingdom" to "United Kingdom",
            "netherlands" to "Netherlands", "canada" to "Canada"
        )
        val k = raw.trim().lowercase()
        if (k.isEmpty()) return ""
        return m[k] ?: raw.trim().replaceFirstChar { it.uppercase() }
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


    private fun allRadioStations(): List<Station> {
        addedRev
        val deleted = TabStore.deleted(this)
        val fav = (FavStore.stations(this) + stations.filter { favUrls.contains(it.url) })
        val fromTabs = customTabs.flatMap { tab ->
            stations.filter { it.tab == tab } + TabStore.extraStations(this, tab)
        }
        return (fav + fromTabs + searchRows + stations)
            .distinctBy { it.url }
            .filter { it.url !in deleted }
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
        val deleted = TabStore.deleted(this)
        val tab = currentTab()
        return when (tab) {
            "fav" -> TabStore.applyOrder(this, "fav", (FavStore.stations(this) + stations.filter { favUrls.contains(it.url) }).distinctBy { it.url }.filter { it.url !in deleted })
            "best", "local" -> emptyList()
            "search" -> searchRows
            else -> {
                val base = stations.filter { it.tab == tab }
                val extra = TabStore.extraStations(this, tab)
                TabStore.applyOrder(this, tab, (base + extra).distinctBy { it.url }.filter { it.url !in deleted })
            }
        }
    }

    private fun visibleLocal(): List<LocalTrack> {
        val tab = currentTab()
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
            statusText = "немає дозволу на аудіо"
            return
        }
        localTracks = try {
            LocalLibrary.list(this)
        } catch (e: Exception) {
            statusText = "scan error"
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) { reloadLocal(); maybeStartBtIfConnected() }
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
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1002)
    }

    private fun toggleFav(station: Station) {
        FavStore.toggleStation(this, station)
        favUrls = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_FAVORITES)
    }
    private fun toggleFav(url: String) {
        val s = (FavStore.stations(this) + stations + TabStore.extraStations(this, currentTab()))
            .firstOrNull { it.url == url } ?: Station(url, stationName, currentGenre, currentCountry, currentFavicon, "fav")
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
            .commit()
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
            .commit()
        startPlay(t.uri, t.title)
    }

    private fun startPlay(url: String, name: String) {
        val i = Intent(this, RadioWatchService::class.java)
        i.action = RadioWatchService.ACTION_PLAY_URL
        i.putExtra(RadioWatchService.EXTRA_URL, url)
        i.putExtra(RadioWatchService.EXTRA_NAME, name)
        startForegroundService(i)
        statusText = "start"
        isLocalNow = url.startsWith("content:")
        if (nowOpen) { posHandler.removeCallbacks(posTick); posHandler.post(posTick) }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    tabs: List<String>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    onRevealCurrent: () -> Unit = {},
    qName: String, onName: (String) -> Unit,
    qCountry: String, onCountry: (String) -> Unit,
    qGenre: String, onGenre: (String) -> Unit,
    searchOpen: Boolean = false,
    onSearchOpen: () -> Unit = {},
    onSearch: () -> Unit,
    suggestFor: String = "",
    onSuggestFor: (String) -> Unit = {},
    nameHints: List<String> = emptyList(),
    countryHints: List<String> = emptyList(),
    genreHints: List<String> = emptyList(),
    onMore: () -> Unit,
    canMore: Boolean,
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
    allRadio: List<Station> = emptyList(),
    recentStations: List<Station> = emptyList(),
    localRows: List<LocalTrack>,
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
    val bg = Color(0xFF000000)
    val card = Color(0xFF141418)
    val text = Color(0xFFF2F2F5)
    val muted = Color(0x9EF2F2F5)

    @Composable
    fun PlayBtn(
        playing: Boolean,
        status: String,
        sizeDp: androidx.compose.ui.unit.Dp,
        onClick: () -> Unit,
    ) {
        val st = status.lowercase()
        val busy = !playing && (
            st.contains("connect") || st.contains("buffer") || st.contains("reconnect") ||
            st == "start" || st.contains("підключ")
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
        val pressSc by animateFloatAsState(if (pressed) 0.88f else 1f, label = "playPress")
        val sc = (if (pulseOn) pulse else 1f) * pressSc
        Box(
            modifier = Modifier
                .size(sizeDp)
                .graphicsLayer { scaleX = sc; scaleY = sc }
                .background(acc, RoundedCornerShape(16.dp))
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
                playing -> Text("⏸", color = Color(0xFF0A0A0C), style = MaterialTheme.typography.headlineMedium)
                else -> Text("▶", color = Color(0xFF0A0A0C), style = MaterialTheme.typography.headlineMedium)
            }
        }
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
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp)) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).size(40.dp).background(card, RoundedCornerShape(12.dp)).clickable { topThemeOpen = true },
                contentAlignment = Alignment.Center
            ) { Text("🌙") }
            Text("Radio S O", color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.Center))
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).background(card, RoundedCornerShape(12.dp)).clickable { onMenu() },
                contentAlignment = Alignment.Center
            ) { Text("⋯", color = Color.White) }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCloseMenu(); onNow() }
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF222228), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                    AsyncImage(model = artUrl(favicon), contentDescription = null, modifier = Modifier.size(72.dp), contentScale = ContentScale.Crop)
                } else {
                    Text("🎵")
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("жанр: $genre", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("країна: $country", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("🎵 " + (if (track.isBlank()) "Трек: невідомо" else track), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(status, color = acc, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp, bottom = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ⌄ НАД візуалізатором
                Text(
                    "⌄",
                    color = muted.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
                val inf = rememberInfiniteTransition(label = "viz")
                val pulseA = inf.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), "a").value
                val pulseB = inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(680), RepeatMode.Reverse), "b").value
                val pulseC = inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), "c").value
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(32.dp)
                ) {
                    listOf(0.35f, 0.7f, 0.5f, 1f, 0.45f, 0.85f, 0.4f, 0.65f, 0.55f).forEachIndexed { i, base ->
                        val p = when (i % 3) { 0 -> pulseA; 1 -> pulseB; else -> pulseC }
                        Box(
                            modifier = Modifier.padding(horizontal = 1.2.dp).width(3.5.dp)
                                .height((if (playing) 8f + 24f * base * p else 6f).dp)
                                .background(acc.copy(alpha = if (playing) 0.55f + 0.45f * p else 0.35f), RoundedCornerShape(50))
                        )
                    }
                }
            }
            } // end info Row
        } // end info Column
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        if (tabs.getOrNull(tabIndex) == "search") {
            Column(modifier = Modifier.padding(vertical = 4.dp).background(card, RoundedCornerShape(16.dp)).padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSearchOpen() }.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍  Пошук…", color = Color.White, modifier = Modifier.weight(1f))
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
                        Column(modifier = Modifier.fillMaxWidth().background(card, RoundedCornerShape(6.dp)).padding(6.dp)) {
                            hints.distinct().take(12).forEach { h ->
                                Text(h, color = text, modifier = Modifier.fillMaxWidth().clickable { set(h); onSuggestFor("") }.padding(6.dp))
                            }
                        }
                    }
                }
                field(qName, onName, "Назва", "name", nameHints)
                field(qCountry, onCountry, "Країна", "country", countryHints)
                field(qGenre, onGenre, "Жанр", "genre", genreHints)
                Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color(0xFF0A0A0C))) { Text("🔍 Знайти") }
                }
            }
        }
        if (showLocal) {
            if (localRows.isEmpty()) Text("Немає треків. Scan.", color = muted)
            LazyColumn(modifier = Modifier.weight(1f), state = listState, userScrollEnabled = !dragging) {
                itemsIndexed(localRows, key = { _, x -> x.uri }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(when { dropAt == index -> acc.copy(alpha = 0.40f); item.uri == currentUrl -> acc.copy(alpha = 0.18f); else -> Color.Transparent }, RoundedCornerShape(10.dp))
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
                            .clickable { onPickLocal(localRows, index) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            val a = if (item.albumId.isNotBlank() && item.albumId != "0")
                                "content://media/external/audio/albumart/${item.albumId}" else ""
                            if (a.isNotEmpty()) AsyncImage(model = a, contentDescription = null, modifier = Modifier.size(42.dp), contentScale = ContentScale.Crop)
                            else Text("🎵")
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (bestUris.contains(item.uri)) "★" else "☆", color = acc, modifier = Modifier.clickable { onToggleBest(item) }.padding(start = 10.dp, end = 2.dp), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), state = listState, userScrollEnabled = !dragging) {
                itemsIndexed(radioRows, key = { i, s -> s.tab + s.url + i }) { index, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(when { dropAt == index -> acc.copy(alpha = 0.40f); s.url == currentUrl -> acc.copy(alpha = 0.18f); else -> Color.Transparent }, RoundedCornerShape(10.dp))
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
                            .clickable { onPickRadio(radioRows, index) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(42.dp), contentScale = ContentScale.Crop)
                            } else Text("🎵")
                        }
                        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.genre} · ${s.country}", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (tabs.getOrNull(tabIndex) == "search") {
                            Text("+", color = acc, modifier = Modifier.clickable { onAddToTab(s) }.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
                        } else {
                            Text(
                                if (favUrls.contains(s.url)) "★" else "☆",
                                color = acc,
                                modifier = Modifier.clickable { onToggleFav(s) }.padding(start = 8.dp, end = 2.dp),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            if (tabs.getOrNull(tabIndex) != "fav") {
                                Text("🗑", modifier = Modifier.clickable { onAskDelete(s) }.padding(start = 8.dp, end = 0.dp))
                            }
                        }
                    }
                }
                if (canMore) {
                    item { Button(onClick = onMore, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Ще 100") } }
                }
            }
        }
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { i, tab ->
                    val lab = when (tab.lowercase()) {
                        "fav" -> "Best"
                        "best" -> "Lokal Best"
                        "local" -> "Lokal"
                        "search" -> "SEARCH"
                        "ukraine", "ua" -> "UA"
                        "techno" -> "Techno"
                        "trance" -> "Trance"
                        "pop" -> "Pop"
                        else -> tab.replaceFirstChar { it.uppercase() }
                    }
                    Text(
                        lab,
                        color = if (i == tabIndex) Color(0xFF0A0A0C) else muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(if (i == tabIndex) acc else card, RoundedCornerShape(6.dp))
                            .border(1.dp, if (i == tabIndex) acc else Color(0xFF3A3A42), RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = {
                                if (i == tabIndex) {
                                    val idx = if (showLocal) localRows.indexOfFirst { it.uri == currentUrl }
                                    else radioRows.indexOfFirst { it.url == currentUrl }
                                    if (idx >= 0) scope.launch { listState.scrollToItem(idx) }
                                } else onTab(i)
                            }, onLongClick = { onLongTab(tab) })
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Text("+", color = acc, modifier = Modifier.padding(horizontal = 6.dp).clickable { onAddTab() })
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp).pointerInput(Unit) {
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
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(78.dp).background(card, RoundedCornerShape(16.dp)).clickable { onPrev() },
                contentAlignment = Alignment.Center
            ) { Text("⏮", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
            PlayBtn(playing = playing, status = status, sizeDp = 78.dp, onClick = onPlayPause)
            Box(
                modifier = Modifier.size(78.dp).background(card, RoundedCornerShape(16.dp)).clickable { onNext() },
                contentAlignment = Alignment.Center
            ) { Text("⏭", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
            if (tabs.getOrNull(tabIndex) == "local") {
                Box(
                    modifier = Modifier.size(56.dp).background(Color(0xFF1A1A1E), RoundedCornerShape(12.dp)).clickable { onScan() },
                    contentAlignment = Alignment.Center
                ) { Text("Scan", color = acc, style = MaterialTheme.typography.bodySmall) }
            }
        }
            Text("⌃", color = muted, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterEnd).clickable { onNow() }.padding(4.dp))
        }
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
                    .fillMaxHeight(0.90f)
                    .graphicsLayer {
                        translationY = topA.value
                        // дзеркало низу: closed(-780)≈0.45, open(0)=1
                        val sc = (1f + topA.value / 1400f).coerceIn(0.45f, 1f)
                        scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    }
                    .background(Color(0xFF121214), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
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
                    .verticalScroll(rememberScrollState())
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
                            .background(Color(0xFF222228), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val u = artUrl(favicon)
                        if (u.startsWith("http") || u.startsWith("content:")) {
                            AsyncImage(model = u, contentDescription = null, modifier = Modifier.size(72.dp), contentScale = ContentScale.Crop)
                        } else Text("🎵")
                    }
                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (track.isBlank()) "🎵 Трек: невідомо" else "🎵 $track",
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
                    Text(
                        if (isLocalCard) {
                            if (bestUris.contains(currentUrl)) "★" else "☆"
                        } else {
                            if (favUrls.contains(currentUrl)) "★" else "☆"
                        },
                        color = acc,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .padding(start = 6.dp)
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
                                                .size(52.dp)
                                                .background(Color(0xFF222228), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (iu.startsWith("content:")) {
                                                AsyncImage(model = iu, contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
                                            } else Text("🎵")
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
                                                .size(52.dp)
                                                .background(Color(0xFF222228), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                                AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
                                            } else Text("🎵")
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
                                            .size(52.dp)
                                            .background(Color(0xFF222228), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                            AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
                                        } else Text("🎵")
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
                            .background(Color(0xFF1A1A1E), RoundedCornerShape(12.dp))
                            .clickable { topSleepOpen = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("◔ ${sleepLabel}", color = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (btWatch) acc.copy(alpha = 0.25f) else Color(0xFF1A1A1E), RoundedCornerShape(12.dp))
                            .clickable { onBt() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (btWatch) "◉ BT вкл" else "○ BT викл", color = if (btWatch) acc else text, style = MaterialTheme.typography.labelLarge)
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.75f)
                            .background(Color(0xFF1A1A1E), RoundedCornerShape(12.dp))
                            .clickable { topThemeOpen = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🌙", style = MaterialTheme.typography.titleMedium)
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
                            .background(Color(0xFF1A1A1E), RoundedCornerShape(12.dp))
                            .clickable { onExport() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↑ Експорт", color = text, style = MaterialTheme.typography.labelLarge)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1A1A1E), RoundedCornerShape(12.dp))
                            .clickable { onImport() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↓ Імпорт", color = text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    if (topSleepOpen) {
        AlertDialog(
            containerColor = Color(0xFF1A1A1E),
            onDismissRequest = { topSleepOpen = false },
            title = { Text("Таймер сну", color = Color.White) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(15, 30, 60, 0).forEach { m ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF121214), RoundedCornerShape(10.dp))
                                .clickable {
                                    onSleep(m)
                                    topSleepOpen = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(if (m == 0) "off" else "${m} хв", color = acc)
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
            containerColor = Color(0xFF1A1A1E),
            onDismissRequest = { topThemeOpen = false },
            title = { Text("Тема", color = Color.White) },
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
                                        .background(Color(th.accent), RoundedCornerShape(10.dp))
                                        .then(
                                            if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
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
                    .background(Color(0xFF16161A), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Text((if (btWatch) "◉ BT увімк" else "○ BT вимк"), color = text, modifier = Modifier.fillMaxWidth().clickable { onBt(); onCloseMenu() }.padding(8.dp))
                Text("◔ $sleepLabel", color = text, modifier = Modifier.fillMaxWidth().clickable { onSleepMenu() }.padding(8.dp))
                if (sleepMenu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 0).forEach { m ->
                            Text(if (m == 0) "off" else "${m}хв", color = acc, modifier = Modifier.clickable { onSleep(m); onCloseMenu() }.padding(6.dp))
                        }
                    }
                }
                Text("↑ Експорт", color = text, modifier = Modifier.fillMaxWidth().clickable { onExport() }.padding(8.dp))
                Text("↓ Імпорт", color = text, modifier = Modifier.fillMaxWidth().clickable { onImport() }.padding(8.dp))
            }
        }
    }
    }
    if (pickStation != null) {
        AlertDialog(
            containerColor = Color(0xFF1A1A1E),
            onDismissRequest = onCancelPick,
            title = { Text("Виберіть вкладку") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    targetTabs.forEach { tab ->
                        Text(when (tab.lowercase()) { "fav"->"Best"; "best"->"Lokal Best"; "ukraine","ua"->"UA"; "techno"->"Techno"; "trance"->"Trance"; "pop"->"Pop"; else -> tab.replaceFirstChar { it.uppercase() } }, modifier = Modifier.fillMaxWidth().clickable { onPickTabForStation(tab) }.padding(12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { Button(onClick = onCancelPick) { Text("Скасувати") } }
        )
    }
    if (newTabOpen) {
        AlertDialog(
            containerColor = Color(0xFF1A1A1E),
            onDismissRequest = onCancelNewTab,
            title = { Text("Створити нову вкладку") },
            text = { OutlinedTextField(value = newTabName, onValueChange = onNewTabName, singleLine = true) },
            confirmButton = { Button(onClick = onCreateTab) { Text("Створити") } },
            dismissButton = { Button(onClick = onCancelNewTab) { Text("Скасувати") } }
        )
    }
    if (editTab != null) {
        AlertDialog(
            containerColor = Color(0xFF1A1A1E),
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
            containerColor = Color(0xFF1A1A1E),
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
            Box(modifier = Modifier.fillMaxSize().background(Color(0x88000000).copy(alpha = ((560f - pullA.value) / 560f * 0.55f).coerceIn(0f, 0.55f))).clickable {
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
            Column(
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
                    .background(Color(0xFF121214), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
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
                            .height(236.dp),
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
                                    .size(220.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val u = arts.getOrNull(page) ?: ""
                                if (u.startsWith("http") || u.startsWith("content:")) {
                                    AsyncImage(
                                        model = u,
                                        contentDescription = null,
                                        modifier = Modifier.size(220.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("🎵", style = MaterialTheme.typography.displayLarge)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .then(
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
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val isLocalCard = showLocal || currentUrl.startsWith("content:")
                        if (isLocalCard) {
                            val on = bestUris.contains(currentUrl)
                            Text(
                                if (on) "★" else "☆",
                                color = acc,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        val t = localRows.firstOrNull { it.uri == currentUrl }
                                        if (t != null) onToggleBest(t)
                                    }
                            )
                        } else {
                            val on = favUrls.contains(currentUrl)
                            Text(
                                if (on) "★" else "☆",
                                color = acc,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
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
                        if (track.isBlank()) "🎵 Трек: невідомо" else "🎵 $track",
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!showLocal && !isLocalNow && !currentUrl.startsWith("content:") && arts.isNotEmpty()) {
                                    Modifier.pointerInput(currentUrl + "t", pageCount) {
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
                                            pagerState.dispatchRawDelta(-drag)
                                        }
                                    }
                                } else Modifier
                            )
                    )
                }
                if (isLocalNow || currentUrl.startsWith("content:")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(6.dp)) {
                        Text("🔀", modifier = Modifier.size(36.dp).clickable { onShuffle() }, style = MaterialTheme.typography.headlineSmall)
                        Text("🔁", modifier = Modifier.size(36.dp).clickable { onRepeat() }, style = MaterialTheme.typography.headlineSmall)
                    }
                    val d = if (durMs > 0) durMs else 1L
                    var slide by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(-1f) }
                    androidx.compose.material3.Slider(
                        value = if (slide >= 0f) slide else (posMs.toFloat() / d.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { slide = it },
                        onValueChangeFinished = {
                            if (durMs > 0 && slide >= 0f) onSeek((slide * durMs).toLong())
                            slide = -1f
                        }
                    )
                    fun fmt(ms: Long): String {
                        val s = (ms / 1000).coerceAtLeast(0)
                        return "%d:%02d".format(s / 60, s % 60)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(posMs), color = muted); Text(fmt(durMs), color = muted)
                    }
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
                                                    .graphicsLayer { this.alpha = alpha },
                                                contentScale = ContentScale.Crop
                                            )
                                        } else Text("🎵", modifier = Modifier.graphicsLayer { this.alpha = alpha })
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
                    Box(modifier = Modifier.size(80.dp).background(Color(0xFF1A1A1E), RoundedCornerShape(16.dp)).clickable { onPrev() }, contentAlignment = Alignment.Center) { Text("⏮", style = MaterialTheme.typography.headlineMedium) }
                    PlayBtn(playing = playing, status = status, sizeDp = 80.dp, onClick = onPlayPause)
                    Box(modifier = Modifier.size(80.dp).background(Color(0xFF1A1A1E), RoundedCornerShape(16.dp)).clickable { onNext() }, contentAlignment = Alignment.Center) { Text("⏭", style = MaterialTheme.typography.headlineMedium) }
                }
            }
        }
    }

}
