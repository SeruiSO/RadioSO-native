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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
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
                    readPrefs()
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
                            if (tab !in listOf("fav", "best", "local", "search") ) {
                                TabStore.removeStation(this, tab, s.url)
                                addedRev++
                                statusText = "видалено з $tab"
                            } else if (tab == "fav") {
                                toggleFav(s.url)
                            }
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
                        onToggleFav = { s ->
                            if (currentTab() == "search") pickStation = s
                            else toggleFav(s.url)
                        },
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
        val tab = currentTab()
        return when (tab) {
            "fav" -> stations.filter { favUrls.contains(it.url) }.distinctBy { it.url }
            "best", "local" -> emptyList()
            "search" -> searchRows
            else -> {
                val base = stations.filter { it.tab == tab }
                val extra = TabStore.extraStations(this, tab)
                (base + extra).distinctBy { it.url }
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

    private fun toggleFav(url: String) {
        FavStore.toggle(this, BluetoothAutoPlayPlugin.KEY_FAVORITES, url)
        favUrls = FavStore.urls(this, BluetoothAutoPlayPlugin.KEY_FAVORITES)
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
    val acc = Color(accent)
    val bg = Color(0xFF0A0A0C)
    val card = Color(0xFF1A1A1E)
    val text = Color(0xFFF2F2F5)
    val muted = Color(0x9EF2F2F5)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 8.dp)
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
                if (favicon.startsWith("http")) {
                    AsyncImage(model = favicon, contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
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
                                onLongClick = { onDeleteStation(s) }
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
                        Text(
                            if (tabs.getOrNull(tabIndex) == "search") "ADD" else if (favUrls.contains(s.url)) "★" else "☆",
                            color = acc,
                            modifier = Modifier.clickable { onToggleFav(s) }.padding(8.dp)
                        )
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
                                tab,
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
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPrev) { Text("⏮") }
            Button(onClick = onPlayPause) { Text(if (playing) "⏸" else "▶") }
            Button(onClick = onNext) { Text("⏭") }
            if (showLocal) Button(onClick = onScan) { Text("Scan") }
        }
    }
    if (pickStation != null) {
        AlertDialog(
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
            onDismissRequest = onCancelNewTab,
            title = { Text("Нова вкладка") },
            text = { OutlinedTextField(value = newTabName, onValueChange = onNewTabName, singleLine = true) },
            confirmButton = { Button(onClick = onCreateTab) { Text("Створити") } },
            dismissButton = { Button(onClick = onCancelNewTab) { Text("Скасувати") } }
        )
    }
    if (editTab != null) {
        AlertDialog(
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
                if (favicon.startsWith("http")) {
                    AsyncImage(model = favicon, contentDescription = null, modifier = Modifier.size(160.dp), contentScale = ContentScale.Fit)
                } else Text("🎵", style = MaterialTheme.typography.displayMedium)
                Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("жанр: $genre", color = muted)
                Text("країна: $country", color = muted)
                Text(if (track.isBlank()) "🎵 Трек: невідомо" else "🎵 $track", color = Color.White)
                Text(status, color = acc)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Button(onClick = onPrev) { Text("⏮") }
                    Button(onClick = onPlayPause) { Text(if (playing) "⏸" else "▶") }
                    Button(onClick = onNext) { Text("⏭") }
                }
            }
        }
    }
}
