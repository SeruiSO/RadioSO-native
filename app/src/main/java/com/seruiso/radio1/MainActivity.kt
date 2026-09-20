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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Home
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
            holdStatus(getString(R.string.import_error))
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        reloadLocal()
        maybeStartBtIfConnected()
    }

    private var stationName by mutableStateOf("")  // set in onCreate — Context not ready in <init>
    private var currentGenre by mutableStateOf("-")
    private var currentCountry by mutableStateOf("-")
    private var currentFavicon by mutableStateOf("")
    private var currentUrl by mutableStateOf("")
    private var themeId by mutableStateOf("shadow-pulse")
    private var accent by mutableStateOf(0xFF00E676)
    private var nowOpen by mutableStateOf(false)
    private var recentStations by mutableStateOf<List<Station>>(emptyList())
    private var homeNearby by mutableStateOf<List<Station>>(emptyList())
    private var homeSimilarRb by mutableStateOf<List<Station>>(emptyList())
    private var homeRailsGen = 0
    private var lastRailsKey = ""
    private var pendingDelete by mutableStateOf<Station?>(null)
    private var holdSeek by mutableStateOf(false)
    private var posMs by mutableStateOf(0L)
    private var durMs by mutableStateOf(0L)
    private var isLocalNow by mutableStateOf(false)
    private var skipMode by mutableStateOf("radio")
    private var tempStations by mutableStateOf<List<Station>>(emptyList())
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
    private var statusText by mutableStateOf("")  // set in onCreate
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
    private var sleepLabel by mutableStateOf("")  // set in onCreate
    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    private val uiTabs: List<String>
        get() {
            // Жанри — лише customTabs (seed з JSON); fav/best/local/search системні
            val mid = TabStore.genreTabs(this)
            return listOf("fav", "best") + mid + listOf("local", "search")
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
                    if (isPlaying) softStatus( getString(R.string.playing))
                    else if (statusText == getString(R.string.playing)) statusText = "пауза"
                    // На Домі: skip / нова станція → нові «Схожі» (на інших вкладках не шукаємо)
                    if (bottomTab == "home") {
                        val railsKey = "$currentUrl|$currentGenre"
                        if (railsKey != lastRailsKey) {
                            refreshHomeRails(wantSimilar = true)
                        }
                    }
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (stationName.isEmpty()) stationName = getString(R.string.select_station)
        if (statusText.isEmpty()) statusText = getString(R.string.done)
        if (sleepLabel.isEmpty()) sleepLabel = getString(R.string.sleep_timer)
        enableEdgeToEdge()
        askPermissions()
        maybeStartBtIfConnected()
        Palette.init(this)
        val loaded = StationRepo.load(this)
        sourceTabs = loaded.first
        stations = loaded.second
        favUrls = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_FAVORITES)
        bestUris = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_LOCAL_BEST)
        TabStore.ensureGenreTabsSeeded(
            this,
            sourceTabs.filter { it !in TabStore.reserved && it != "search" }
        )
        customTabs = TabStore.customTabs(this)
        reloadLocal()
        readPrefs()
        recentStations = loadRecentStations()
        val lastTab = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).getString("currentTab", "fav")
        val idx = uiTabs.indexOf(lastTab)
        if (idx >= 0) tabIndex = idx
        val lastBottom = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .getString("bottomTab", "home") ?: "home"
        if (lastBottom in listOf("home", "stations", "heart", "music", "tabs", "search", "library")) {
            bottomTab = if (lastBottom == "library") "stations" else lastBottom
        }
        if (bottomTab == "home") refreshHomeRails(wantSimilar = true)

        setContent {
            RadioSOTheme(accent = Color(accent)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val tab = uiTabs.getOrNull(tabIndex) ?: ""
                    val radioRowsMemo = remember(addedRev, tabIndex, customTabs, favUrls, searchRows) {
                        visibleRadio()
                    }
                    val favRowsMemo = remember(addedRev, customTabs, favUrls, searchRows) {
                        visibleRadio("fav")
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
                        favRows = favRowsMemo,
                        
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
                                // getString(R.string.already_have) лише якщо РЕАЛЬНО видно на вкладці.
                                // Після removeStation URL у deletedStations — у списку її немає,
                                // тож already=false → addStation зробить unDelete + свіжі meta.
                                val deleted = TabStore.deleted(this, tab)
                                val ck = TabStore.catalogKey(this, tab)
                                val inExtra = TabStore.extraStations(this, tab).any { it.url == s.url }
                                val inBase = stations.any { it.url == s.url && it.tab == ck }
                                val already = (inExtra || inBase) && s.url !in deleted
                                if (already) {
                                    holdStatus(getString(R.string.already_in_tab, tab))
                                } else {
                                    val err = TabStore.addStation(this, tab, s)
                                    holdStatus(err ?: getString(R.string.added_to_tab, tab))
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
                                holdStatus(getString(R.string.tab_created, newTabName.lowercase()))
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
                            // Можна керувати і вбудованими (techno/pop…), і кастомними; системні — ні
                            if (tab !in listOf("fav", "best", "local", "search") && tab !in TabStore.reserved) {
                                editTab = tab
                                editName = tab
                                deleteArmed = false
                            }
                        },
                        onEditName = { editName = it },
                        onRenameTab = {
                            val oldName = editTab ?: return@StationScreen
                            val newName = editName.trim().lowercase()
                            val err = TabStore.renameTab(this, oldName, newName, sourceTabs)
                            if (err == null) {
                                customTabs = TabStore.customTabs(this)
                                addedRev++
                                val sp = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                                if (sp.getString("currentTab", "") == oldName) {
                                    sp.edit().putString("currentTab", newName).apply()
                                }
                                val ix = uiTabs.indexOf(newName)
                                if (ix >= 0) tabIndex = ix
                                holdStatus(getString(R.string.tab_renamed))
                                editTab = null
                            } else holdStatus(err)
                        },
                        onDeleteTab = {
                            val tab = editTab ?: return@StationScreen
                            if (!deleteArmed) { deleteArmed = true; return@StationScreen }
                            TabStore.deleteTab(this, tab)
                            customTabs = TabStore.customTabs(this)
                            addedRev++
                            if (uiTabs.getOrNull(tabIndex) == tab) tabIndex = 0
                            holdStatus(getString(R.string.tab_deleted, tab))
                            editTab = null
                            deleteArmed = false
                        },
                        onCancelEdit = { editTab = null; deleteArmed = false },
                        radioRows = radioRowsMemo,
                        searchRows = searchRows,
                        allRadio = allRadioMemo,
                        recentStations = recentStations,
                        homeNearby = homeNearby,
                        homeSimilarRb = homeSimilarRb,
                        onGenreChip = { g ->
                            qName = ""
                            qCountry = ""
                            qGenre = g
                            // без selectBottomTab("search") — інакше autoSearchByGeo зітре жанр
                            bottomTab = "search"
                            getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                                .edit().putString("bottomTab", "search").apply()
                            val i = uiTabs.indexOf("search")
                            if (i >= 0) {
                                tabIndex = i
                                getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                                    .edit().putString("currentTab", "search").apply()
                            }
                            runSearch()
                        },
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
                        onPickTheme = { id ->
                            val n = ThemeStore.set(this, id)
                            themeId = n.id
                            accent = n.accent
                            holdStatus(n.id)
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
                            holdStatus(getString(R.string.deleted))
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
                        onPickRadio = { list, index -> menuOpen = false; playRadio(list, index, asQueue = true) },
                        onPickOneRadio = { list, i -> menuOpen = false; playRadio(list, i, asQueue = false) },
                        onPickLocal = { list, index -> menuOpen = false; playLocal(list, index) },
                        onToggleFav = { s -> toggleFav(s) },
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
                            holdStatus(if (v) getString(R.string.shuffle_on) else getString(R.string.shuffle_off))
                        },
                        onRepeat = {
                            val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                            val cur = p.getString(LocalMusicPlugin.KEY_LOCAL_REPEAT, "off")
                            val next = when (cur) { "off" -> "all"; "all" -> "one"; else -> "off" }
                            p.edit().putString(LocalMusicPlugin.KEY_LOCAL_REPEAT, next).apply()
                            holdStatus(when (next) {
                                "all" -> getString(R.string.repeat_all)
                                "one" -> getString(R.string.repeat_one)
                                else -> getString(R.string.repeat_off)
                            })
                        },
                        posMs = posMs,
                        durMs = durMs,
                        isLocalNow = isLocalNow,
                        canSkip = skipMode != "off",
                        skipMode = skipMode,
                        tempRows = tempStations,

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
        // Права панель: LazyColumn(reverseLayout) → візуально зверху = кінець genreTabs.
        // Модалка + без reverse → asReversed(), щоб зверху вниз як на картці.
        return TabStore.genreTabs(this).asReversed()
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
            holdStatus(getString(R.string.search_hint))
            return
        }
        val gen = ++RadioBrowser.activeGen
        statusText = getString(R.string.search_ellipsis)
        searchAll = emptyList()
        searchRows = emptyList()
        searchShown = 0
        Thread {
            val result = try { RadioBrowser.search(n, c, g, gen) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (gen != RadioBrowser.activeGen) return@runOnUiThread
                searchAll = dedupeStationsByStream(result ?: emptyList())
                searchShown = minOf(100, searchAll.size)
                searchRows = searchAll.take(searchShown)
                holdStatus(if (searchAll.isEmpty()) getString(R.string.nothing_found) else getString(R.string.search_found, searchAll.size))
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
        startActivity(Intent.createChooser(send, getString(R.string.export_radioso)))
        statusText = "експорт"
    }

    private fun toggleBt() {
        btWatch = !btWatch
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .edit().putBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, btWatch).commit()
        statusText = if (btWatch) getString(R.string.bt_watch_on) else getString(R.string.bt_watch_off)
    }

    private fun armSleep(mins: Int) {
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        sleepRunnable = null
        if (mins <= 0) {
            sleepLabel = getString(R.string.sleep_timer)
            statusText = getString(R.string.sleep_off)
            sleepMenu = false
            return
        }
        sleepLabel = getString(R.string.sleep_mins, mins)
        statusText = sleepLabel
        val r = Runnable {
            sendAction(RadioWatchService.ACTION_PAUSE)
            sleepLabel = getString(R.string.sleep_timer)
            softStatus(getString(R.string.sleep_pause))
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

        
    /** Дім: «поруч» раз на 10 днів.
     *  «Схожі» (макс 10): захід на Дім уже з радіо, і кожен скіп на Домі.
     *  Пошук не чіпаємо. */
    private val nearbyTtlMs = 10L * 24 * 60 * 60 * 1000

    private fun loadCachedNearby(): List<Station> {
        val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        val raw = p.getString(BluetoothAutoPlayPlugin.KEY_HOME_NEARBY_JSON, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                val url = o.optString("url")
                val name = o.optString("name")
                if (url.isBlank() || name.isBlank()) null
                else Station(url, name, o.optString("genre"), o.optString("country"), o.optString("favicon"), "search")
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveCachedNearby(list: List<Station>) {
        val arr = org.json.JSONArray()
        list.take(10).forEach { st ->
            arr.put(org.json.JSONObject()
                .put("url", st.url).put("name", st.name)
                .put("genre", st.genre).put("country", st.country)
                .put("favicon", st.favicon))
        }
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
            .putString(BluetoothAutoPlayPlugin.KEY_HOME_NEARBY_JSON, arr.toString())
            .putLong(BluetoothAutoPlayPlugin.KEY_HOME_NEARBY_AT, System.currentTimeMillis())
            .apply()
    }

    private fun nearbyCacheFresh(): Boolean {
        val at = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
            .getLong(BluetoothAutoPlayPlugin.KEY_HOME_NEARBY_AT, 0L)
        return at > 0L && System.currentTimeMillis() - at < nearbyTtlMs
    }

    private fun refreshHomeRails(wantSimilar: Boolean = true) {
        if (bottomTab != "home") return
        val genreSnap = currentGenre.trim()
        val urlSnap = currentUrl
        val key = "$urlSnap|$genreSnap"
        val stationChanged = key != lastRailsKey
        lastRailsKey = key
        val token = ++homeRailsGen
        val cached = loadCachedNearby()
        if (cached.isNotEmpty() && homeNearby.isEmpty()) homeNearby = cached
        val needNearbyNet = !nearbyCacheFresh()
        val isRadio = urlSnap.isNotBlank() && !urlSnap.startsWith("content:")
        val needSimilar = wantSimilar && isRadio && (stationChanged || homeSimilarRb.isEmpty())
        if (!needNearbyNet && !needSimilar) {
            if (cached.isNotEmpty()) homeNearby = cached
            return
        }
        Thread {
            var nearby = cached
            if (needNearbyNet) {
                var country = countryFromCache()
                if (country.isBlank()) country = countryFromLocale()
                if (country.isBlank()) {
                    try { country = countryFromIp() } catch (_: Exception) {}
                }
                nearby = try {
                    if (country.isNotBlank())
                        RadioBrowser.searchQuiet("", country, "", limit = 30)
                            ?.filter { it.url != urlSnap }
                            ?.distinctBy { it.url }
                            ?.take(10)
                            ?: emptyList()
                    else emptyList()
                } catch (_: Exception) { cached }
                if (nearby.isNotEmpty()) saveCachedNearby(nearby)
                else if (cached.isNotEmpty()) nearby = cached
            }
            var similar = emptyList<Station>()
            if (needSimilar) {
                val tags = genreSnap
                    .split(',', ';', '/', '|')
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != "-" && it.length >= 2 }
                val known = SearchHints.homeGenres.map { it.lowercase() }.toSet()
                val ordered = (tags.filter { it.lowercase() in known } + tags).distinct()
                for (tag in ordered) {
                    try {
                        val found = RadioBrowser.searchQuiet("", "", tag, limit = 30)
                            ?.filter { it.url != urlSnap }
                            ?.distinctBy { it.url }
                            ?: emptyList()
                        if (found.isNotEmpty()) {
                            similar = found.take(10)
                            break
                        }
                    } catch (_: Exception) { }
                }
            }
            runOnUiThread {
                if (bottomTab != "home") return@runOnUiThread
                if (token != homeRailsGen) return@runOnUiThread
                if (needNearbyNet) homeNearby = nearby
                else if (cached.isNotEmpty()) homeNearby = cached
                if (needSimilar) homeSimilarRb = similar
            }
        }.start()
    }

    


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
            holdStatus(getString(R.string.search_progress, first))
            runSearch(countryOverride = first)
        } else {
            statusText = getString(R.string.detecting_country)
            searchAll = emptyList()
            searchRows = emptyList()
            searchShown = 0
        }
        Thread {
            var refined = countryFromIp()
            if (refined.isBlank()) refined = countryFromLocation()
            if (refined.isBlank()) {
                runOnUiThread {
                    if (searchRows.isEmpty() && first.isBlank()) holdStatus(getString(R.string.country_unknown))
                }
                return@Thread
            }
            saveGeoCountry(refined)
            // не пишемо refined у qCountry — лише перезапуск пошуку, якщо інша країна
            if (normalizeCountry(first) != refined) {
                runOnUiThread {
                    holdStatus(getString(R.string.search_progress, refined))
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
        if (t != "home") {
            // «Усі» з Дому → показати список, не now-playing поверх
            nowOpen = false
            menuOpen = false
        }
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
        if (t == "home") refreshHomeRails(wantSimilar = true)
        persistVisibleQueue()
    }


    private fun allRadioStations(): List<Station> {
        addedRev
        val deletedMap = TabStore.deletedMap(this)
        val tabIds = TabStore.genreTabs(this)
        val fromTabs = tabIds.flatMap { tab ->
            val ck = TabStore.catalogKey(this, tab)
            TabStore.extraStations(this, tab) + stations.filter { it.tab == ck }
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
            out.take(10)
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
            cur.take(10).forEach { x ->
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
                .edit().putString("recentStations", arr.toString()).commit()
            recentStations = cur.take(10)
        } catch (_: Exception) {}
    }

    private fun visibleRadio(tabOverride: String? = null): List<Station> {
        addedRev // observe
        val tab = tabOverride ?: currentTab()
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
                val ck = TabStore.catalogKey(this, tab)
                val base = stations.filter { it.tab == ck }
                val extra = TabStore.extraStations(this, tab)
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
            holdStatus(getString(R.string.no_audio_permission))
            return
        }
        localTracks = try {
            LocalLibrary.list(this)
        } catch (e: Exception) {
            holdStatus(getString(R.string.scan_error))
            emptyList()
        }
        if (currentTab() == "local") {
            statusText = getString(R.string.tracks_count, localTracks.size)
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
        stationName = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, getString(R.string.select_station)) ?: getString(R.string.select_station)
        trackTitle = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "") ?: ""
        currentGenre = p.getString(BluetoothAutoPlayPlugin.KEY_GENRE, "-") ?: "-"
        currentCountry = p.getString(BluetoothAutoPlayPlugin.KEY_COUNTRY, "-") ?: "-"
        currentFavicon = p.getString(BluetoothAutoPlayPlugin.KEY_FAVICON, "") ?: ""
        currentUrl = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
        skipMode = p.getString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "radio") ?: "radio"
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

    private fun startFg(i: Intent) {
        try {
            startForegroundService(i)
        } catch (e: Exception) {
            android.util.Log.w("RadioSO", "startFg " + e.javaClass.simpleName)
        }
    }

    private fun maybeStartBtIfConnected() {
        val sp = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        if (!sp.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true)) return
        sp.edit().putBoolean(BluetoothAutoPlayPlugin.KEY_PENDING_BT_AFTER_BOOT, false).apply()
        val i = Intent(this, RadioWatchService::class.java)
        i.action = RadioWatchService.ACTION_START
        startFg(i)
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
        // Пріоритет: поточне відтворення → search → extra → catalog → FavStore.
        // На Home currentTab() може бути «порожнім»/чужим — завжди дозволяємо ★ з картки Now Playing.
        val currentSnap = if (url == currentUrl && currentUrl.isNotBlank())
            Station(url, stationName, currentGenre, currentCountry, currentFavicon, "fav")
        else null
        val candidates = listOfNotNull(currentSnap) + (
            searchRows.filter { it.url == url } +
            recentStations.filter { it.url == url } +
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

    private fun playRadio(list: List<Station>, index: Int, asQueue: Boolean = true) {
        if (index !in list.indices) return
        val s = list[index]
        pushRecentStation(s)
        stationName = s.name
        trackTitle = ""
        currentFavicon = s.favicon
        currentUrl = s.url
        currentGenre = s.genre
        if (bottomTab == "home") refreshHomeRails(wantSimilar = true)
        currentCountry = s.country
        isLocalNow = false
        // На Домі немає «видимого» tab-queue — skip має крутити саме list (10 з секції / обрані).
        if (bottomTab == "home") {
            skipMode = "temp"
            tempStations = list
        } else {
            skipMode = if (asQueue) "radio" else "temp"
            if (!asQueue) tempStations = list
        }
        val ed = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
            .putString(LocalMusicPlugin.KEY_MODE, "radio")
            .putString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, skipMode)
            .putString(BluetoothAutoPlayPlugin.KEY_URL, s.url)
            .putString(BluetoothAutoPlayPlugin.KEY_NAME, s.name)
            .putString(BluetoothAutoPlayPlugin.KEY_TRACK, "")
            .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, s.favicon)
            .putString(BluetoothAutoPlayPlugin.KEY_GENRE, s.genre)
            .putString(BluetoothAutoPlayPlugin.KEY_COUNTRY, s.country)
            .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
        val urls = JSONArray(); val names = JSONArray(); val favs = JSONArray()
        val genres = JSONArray(); val countries = JSONArray()
        list.forEach {
            urls.put(it.url); names.put(it.name); favs.put(it.favicon)
            genres.put(it.genre); countries.put(it.country)
        }
        if (asQueue) {
            ed.putString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, urls.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, names.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_FAVICONS, favs.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, genres.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, countries.toString())
                .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, index)
        } else {
            ed.putString(BluetoothAutoPlayPlugin.KEY_TEMP_URLS, urls.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_TEMP_NAMES, names.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_TEMP_FAVICONS, favs.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_TEMP_GENRES, genres.toString())
                .putString(BluetoothAutoPlayPlugin.KEY_TEMP_COUNTRIES, countries.toString())
                .putInt(BluetoothAutoPlayPlugin.KEY_TEMP_INDEX, index)
        }
        ed.apply()
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
        currentFavicon = t.albumId
        currentUrl = t.uri
        isLocalNow = true
        skipMode = "local"
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
            .putString(LocalMusicPlugin.KEY_MODE, "local")
            .putString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "local")
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
        startFg(i)
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
        startFg(i)
        posHandler.postDelayed({ holdSeek = false }, 400)
    }

    private fun sendAction(action: String) {
        val i = Intent(this, RadioWatchService::class.java)
        i.action = action
        startFg(i)
    }
}
