package com.seruiso.radio1

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.Modifier
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
            if (nowOpen) posHandler.postDelayed(this, 500)
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

    private val extraTabs = listOf("fav", "best", "local", "search")
    private val uiTabs: List<String> get() = extraTabs + sourceTabs + customTabs.filter { it !in sourceTabs }

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RadioWatchService.ACTION_PLAYBACK_UI -> {
                    isPlaying = intent.getBooleanExtra("playing", false)
                    if (isPlaying) statusText = "playing"
                    readPrefs()
                    isLocalNow = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
                        .getString(LocalMusicPlugin.KEY_MODE, "radio") == "local"
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
                        onSearch = { runSearch() },
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
                        onNow = { nowOpen = true },
                        onNowClose = { nowOpen = false },
                        onTheme = {
                            val n = ThemeStore.next(this)
                            themeId = n.id
                            accent = n.accent
                            statusText = n.id
                        },
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
                        onPickRadio = { list, index -> playRadio(list, index) },
                        onPickLocal = { list, index -> playLocal(list, index) },
                        onToggleFav = { s -> toggleFav(s.url) },
                        onAddToTab = { s -> pickStation = s },
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

    private fun runSearch() {
        val n = qName; val c = qCountry; val g = qGenre
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
        val send = Intent(Intent.ACTION_SEND)
        send.type = "application/json"
        send.putExtra(Intent.EXTRA_TEXT, json)
        send.putExtra(Intent.EXTRA_SUBJECT, "RadioSO-backup.json")
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

    private fun currentTab(): String = uiTabs.getOrNull(tabIndex) ?: ""

    private fun visibleRadio(): List<Station> {
        addedRev // observe
        val deleted = TabStore.deleted(this)
        val tab = currentTab()
        return when (tab) {
            "fav" -> (FavStore.stations(this) + stations.filter { favUrls.contains(it.url) }).distinctBy { it.url }.filter { it.url !in deleted }
            "best", "local" -> emptyList()
            "search" -> searchRows.filter { it.url !in TabStore.deleted(this) }
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
            "best" -> localTracks.filter { bestUris.contains(it.uri) }
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
        if (requestCode == 1002) reloadLocal()
    }

    private fun readPrefs() {
        val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        stationName = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Виберіть станцію") ?: "Виберіть станцію"
        trackTitle = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "") ?: ""
        currentGenre = p.getString(BluetoothAutoPlayPlugin.KEY_GENRE, "-") ?: "-"
        currentCountry = p.getString(BluetoothAutoPlayPlugin.KEY_COUNTRY, "-") ?: "-"
        currentFavicon = p.getString(BluetoothAutoPlayPlugin.KEY_FAVICON, "") ?: ""
        currentUrl = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
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

    private fun askPermissions() {
        val need = mutableListOf<String>()
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
        val i = Intent(this, RadioWatchService::class.java)
        i.action = RadioWatchService.ACTION_SEEK
        i.putExtra(RadioWatchService.EXTRA_POSITION_MS, pos)
        startForegroundService(i)
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
    qName: String, onName: (String) -> Unit,
    qCountry: String, onCountry: (String) -> Unit,
    qGenre: String, onGenre: (String) -> Unit,
    onSearch: () -> Unit,
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
    onDeleteStation: (Station) -> Unit,
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
    val bg = Color(0xFF0A0A0C)
    val card = Color(0xFF1A1A1E)
    val text = Color(0xFFF2F2F5)
    val muted = Color(0x9EF2F2F5)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .navigationBarsPadding()
            .padding(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Radio S O", color = acc, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("🌙", modifier = Modifier.padding(8.dp).clickable { onTheme() })
            Text("⋯", color = text, modifier = Modifier.padding(8.dp).clickable { onMenu() })
        }
        if (menuOpen) {
            Column(modifier = Modifier.background(card, RoundedCornerShape(12.dp)).padding(8.dp)) {
                Button(onClick = onBt) { Text(if (btWatch) "BT стеження: увімк" else "BT стеження: вимк") }
                Button(onClick = onSleepMenu) { Text(sleepLabel) }
                if (sleepMenu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 0).forEach { m ->
                            Button(onClick = { onSleep(m) }) { Text(if (m == 0) "off" else "$m") }
                        }
                    }
                }
                Button(onClick = onExport) { Text("Експорт") }
                Button(onClick = onImport) { Text("Імпорт") }
                Button(onClick = onCloseMenu) { Text("Закрити") }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(card, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, drag -> if (drag < -24) onNow() }
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF222228), CircleShape)
                    .clickable { onNow() },
                contentAlignment = Alignment.Center
            ) {
                if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                    AsyncImage(model = artUrl(favicon), contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
                } else {
                    Text("🎵")
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("жанр: $genre", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("країна: $country", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("🎵 " + (if (track.isBlank()) "Трек: невідомо" else track), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(status, color = acc, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val inf = rememberInfiniteTransition(label = "viz")
            val pulse = inf.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
                label = "p"
            ).value
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(36.dp).padding(start = 8.dp)) {
                listOf(0.45f, 1f, 0.6f, 0.9f, 0.5f, 0.8f, 0.55f).forEachIndexed { i, base ->
                    val h = if (playing) (10f + 22f * base * pulse) else 8f
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.5.dp)
                            .width(4.dp)
                            .height(h.dp)
                            .background(acc.copy(alpha = if (playing) 1f else 0.25f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        if (tabs.getOrNull(tabIndex) == "search") {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                OutlinedTextField(value = qName, onValueChange = onName, singleLine = true, label = { Text("Назва") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qCountry, onValueChange = onCountry, singleLine = true, label = { Text("Країна") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qGenre, onValueChange = onGenre, singleLine = true, label = { Text("Жанр") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = onSearch, modifier = Modifier.padding(top = 6.dp)) { Text("🔍 Знайти") }
            }
        }
        if (showLocal) {
            if (localRows.isEmpty()) Text("Немає треків. Scan.", color = muted)
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(localRows, key = { _, x -> x.uri }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (item.uri == currentUrl) acc.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onPickLocal(localRows, index) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎵", modifier = Modifier.padding(end = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (bestUris.contains(item.uri)) "+b" else "b", color = acc, modifier = Modifier.clickable { onToggleBest(item) }.padding(8.dp))
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(radioRows, key = { i, s -> s.tab + s.url + i }) { index, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (s.url == currentUrl) acc.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onPickRadio(radioRows, index) },
                                onLongClick = { }
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                                AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(32.dp), contentScale = ContentScale.Fit)
                            } else Text("🎵")
                        }
                        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.genre} · ${s.country}", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (tabs.getOrNull(tabIndex) == "search") {
                            Text("ADD", color = acc, modifier = Modifier.clickable { onAddToTab(s) }.padding(6.dp))
                        } else {
                            if (tabs.getOrNull(tabIndex) !in listOf("fav", "best", "local")) {
                                Text("↑", color = muted, modifier = Modifier.clickable { onMoveStation(s, -1) }.padding(4.dp))
                                Text("↓", color = muted, modifier = Modifier.clickable { onMoveStation(s, 1) }.padding(4.dp))
                            }
                            Text("✕", color = muted, modifier = Modifier.size(40.dp).clickable { onDeleteStation(s) }, style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (favUrls.contains(s.url)) "★" else "☆",
                                color = acc,
                                modifier = Modifier.size(40.dp).clickable { onToggleFav(s) },
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
                if (canMore) {
                    item { Button(onClick = onMore, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Ще 100") } }
                }
            }
        }
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = tabIndex.coerceAtMost(tabs.lastIndex),
                containerColor = bg,
                contentColor = acc,
                edgePadding = 4.dp
            ) {
                tabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = i == tabIndex,
                        onClick = { onTab(i) },
                        selectedContentColor = acc,
                        unselectedContentColor = muted,
                        text = {
                            Text(
                                when (tab) {
                                    "fav" -> "FAV"
                                    "best" -> "Best"
                                    "local" -> "Local"
                                    "search" -> "SEARCH"
                                    "ukraine" -> "UA"
                                    "techno" -> "Techno"
                                    "trance" -> "Trance"
                                    "pop" -> "Pop"
                                    else -> tab
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickable(onClick = { onTab(i) }, onLongClick = { onLongTab(tab) })
                            )
                        }
                    )
                }
                Tab(selected = false, onClick = onAddTab, text = { Text("+") }, selectedContentColor = acc, unselectedContentColor = acc)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp).pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -24) onNow() } },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(Triple("⏮", 68, onPrev), Triple(if (playing) "⏸" else "▶", if (playing) 76 else 76, onPlayPause), Triple("⏭", 68, onNext)).forEach { (lab, sz, act) ->
                Box(
                    modifier = Modifier
                        .size(sz.dp)
                        .background(Color(0xFF1A1A1E), RoundedCornerShape(14.dp))
                        .border(1.dp, acc.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { act() },
                    contentAlignment = Alignment.Center
                ) { Text(lab, color = Color.White, style = MaterialTheme.typography.headlineSmall) }
            }
            if (showLocal) {
                Box(
                    modifier = Modifier.size(56.dp).background(Color(0xFF1A1A1E), RoundedCornerShape(12.dp)).clickable { onScan() },
                    contentAlignment = Alignment.Center
                ) { Text("Scan", color = acc, style = MaterialTheme.typography.bodySmall) }
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
                        Text(tab.uppercase(), modifier = Modifier.fillMaxWidth().clickable { onPickTabForStation(tab) }.padding(12.dp))
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
            title = { Text("Нова вкладка") },
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
    if (nowOpen) {
        ModalBottomSheet(onDismissRequest = onNowClose, containerColor = Color(0xFF121214)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                    AsyncImage(model = artUrl(favicon), contentDescription = null, modifier = Modifier.size(160.dp), contentScale = ContentScale.Fit)
                } else Text("🎵", style = MaterialTheme.typography.displayMedium)
                Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("жанр: $genre", color = muted)
                Text("країна: $country", color = muted)
                Text(if (track.isBlank()) "🎵 Трек: невідомо" else "🎵 $track", color = Color.White)
                Text(status, color = acc)
                if (isLocalNow || currentUrl.startsWith("content:")) {
                    Text(if (bestUris.contains(currentUrl)) "★ Local Best" else "☆ у best", color = acc, modifier = Modifier.clickable {
                        localRows.firstOrNull { it.uri == currentUrl }?.let { onToggleBest(it) }
                    }.padding(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(8.dp)) {
                        Text("Shuffle", color = acc, modifier = Modifier.clickable { onShuffle() })
                        Text("Repeat", color = acc, modifier = Modifier.clickable { onRepeat() })
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
                        Text(fmt(posMs), color = muted)
                        Text(fmt(durMs), color = muted)
                    }
                } else {
                    Text(if (favUrls.contains(currentUrl)) "★ fav" else "☆ fav", color = acc, modifier = Modifier.clickable {
                        radioRows.firstOrNull { it.url == currentUrl }?.let { onToggleFav(it) }
                            ?: onToggleFav(Station(currentUrl, name, genre, country, favicon, ""))
                    }.padding(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Box(modifier = Modifier.size(56.dp).background(Color(0xFF1A1A1E), RoundedCornerShape(14.dp)).clickable { onPrev() }, contentAlignment = Alignment.Center) { Text("⏮") }
                    Box(modifier = Modifier.size(72.dp).background(acc, RoundedCornerShape(16.dp)).clickable { onPlayPause() }, contentAlignment = Alignment.Center) { Text(if (playing) "⏸" else "▶", color = Color(0xFF0A0A0C)) }
                    Box(modifier = Modifier.size(56.dp).background(Color(0xFF1A1A1E), RoundedCornerShape(14.dp)).clickable { onNext() }, contentAlignment = Alignment.Center) { Text("⏭") }
                }
            }
        }
    }
}
