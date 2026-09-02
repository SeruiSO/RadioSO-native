package com.seruiso.radio1

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    private var stationName by mutableStateOf("Radio S O")
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
        setContent {
            RadioSOTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val tab = uiTabs.getOrNull(tabIndex) ?: ""
                    StationScreen(
                        tabs = uiTabs,
                        tabIndex = tabIndex,
                        onTab = { tabIndex = it },
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
        stationName = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O") ?: "Radio S O"
        trackTitle = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "") ?: ""
        isPlaying = p.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
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

@OptIn(ExperimentalFoundationApi::class)
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
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 36.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Text(if (track.isBlank()) "—" else track, modifier = Modifier.padding(horizontal = 16.dp))
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Button(onClick = onPrev) { Text("Prev") }
            Button(onClick = onPlayPause) { Text(if (playing) "Pause" else "Play") }
            Button(onClick = onNext) { Text("Next") }
            if (showLocal) Button(onClick = onScan) { Text("Scan") }
        }
        if (tabs.getOrNull(tabIndex) == "search") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(value = qName, onValueChange = onName, singleLine = true, label = { Text("Назва") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qCountry, onValueChange = onCountry, singleLine = true, label = { Text("Країна") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qGenre, onValueChange = onGenre, singleLine = true, label = { Text("Жанр") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) { Text("Знайти") }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    countries.take(6).forEach { c ->
                        Text(c, modifier = Modifier.clickable { onCountry(c) }.padding(4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genres.take(8).forEach { g ->
                        Text(g, modifier = Modifier.clickable { onGenre(g) }.padding(4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(selectedTabIndex = tabIndex.coerceAtMost(tabs.lastIndex)) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = i == tabIndex,
                        onClick = { onTab(i) },
                        text = {
                            Text(
                                t,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onTab(i) },
                                    onLongClick = { onLongTab(t) }
                                )
                            )
                        }
                    )
                }
                Tab(selected = false, onClick = onAddTab, text = { Text("+") })
            }
        }
        if (pickStation != null) {
            AlertDialog(
                onDismissRequest = onCancelPick,
                title = { Text("Виберіть вкладку") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        targetTabs.forEach { tab ->
                            Text(
                                tab.uppercase(),
                                modifier = Modifier.fillMaxWidth().clickable { onPickTabForStation(tab) }.padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { Button(onClick = onCancelPick) { Text("Скасувати") } }
            )
        }
        if (editTab != null) {
            AlertDialog(
                onDismissRequest = onCancelEdit,
                title = { Text("Вкладка $editTab") },
                text = {
                    OutlinedTextField(value = editName, onValueChange = onEditName, singleLine = true, label = { Text("перейменувати") })
                },
                confirmButton = { Button(onClick = onRenameTab) { Text("Перейменувати") } },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDeleteTab) { Text(if (deleteArmed) "Точно видалити?" else "Видалити") }
                        Button(onClick = onCancelEdit) { Text("Скасувати") }
                    }
                }
            )
        }
        if (newTabOpen) {
            AlertDialog(
                onDismissRequest = onCancelNewTab,
                title = { Text("Нова вкладка") },
                text = {
                    OutlinedTextField(value = newTabName, onValueChange = onNewTabName, singleLine = true, label = { Text("назва") })
                },
                confirmButton = { Button(onClick = onCreateTab) { Text("Створити") } },
                dismissButton = { Button(onClick = onCancelNewTab) { Text("Скасувати") } }
            )
        }
        if (showLocal) {
            if (localRows.isEmpty()) {
                Text("Немає треків. Дай дозвіл і натисни Scan.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(localRows, key = { _, t -> t.uri }) { index, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickLocal(localRows, index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (bestUris.contains(t.uri)) "+b" else "b",
                            modifier = Modifier.padding(8.dp).clickable { onToggleBest(t) },
                        )
                    }
                }
            }
        } else {
            if (radioRows.isEmpty()) {
                Text(if (tabs.getOrNull(tabIndex) == "search") "Знайди станцію. ★ — вибрати вкладку." else "Порожньо.", modifier = Modifier.padding(16.dp))
            }
            if (canMore) {
                Button(onClick = onMore, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text("Ще 100") }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(radioRows, key = { i, s -> s.tab + s.url + i }) { index, s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickRadio(radioRows, index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.genre} · ${s.country}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            if (favUrls.contains(s.url)) "★" else "☆",
                            modifier = Modifier.padding(8.dp).clickable { onToggleFav(s) },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}
