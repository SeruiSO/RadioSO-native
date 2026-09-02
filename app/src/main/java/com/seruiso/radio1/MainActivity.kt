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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    private val extraTabs = listOf("fav", "best", "local")
    private val uiTabs: List<String> get() = extraTabs + sourceTabs

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RadioWatchService.ACTION_PLAYBACK_UI -> {
                    isPlaying = intent.getBooleanExtra("playing", false)
                }
                RadioWatchService.ACTION_TRACK_META -> {
                    trackTitle = intent.getStringExtra(RadioWatchService.EXTRA_TRACK) ?: ""
                }
                RadioWatchService.ACTION_STATUS_UI -> {
                    val st = intent.getStringExtra("status") ?: ""
                    val attempt = intent.getIntExtra("attempt", 0)
                    statusText = if (attempt > 0) "$st #$attempt" else st
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
                        onToggleFav = { toggleFav(it.url) },
                        onToggleBest = { toggleBest(it.uri) },
                        onScan = { reloadLocal() },
                    )
                }
            }
        }
    }

    private fun currentTab(): String = uiTabs.getOrNull(tabIndex) ?: ""

    private fun visibleRadio(): List<Station> {
        val tab = currentTab()
        return when (tab) {
            "fav" -> stations.filter { favUrls.contains(it.url) }.distinctBy { it.url }
            "best", "local" -> emptyList()
            else -> stations.filter { it.tab == tab }
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

@Composable
fun StationScreen(
    tabs: List<String>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
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
        if (tabs.isNotEmpty()) {
            ScrollableTabRow(selectedTabIndex = tabIndex.coerceAtMost(tabs.lastIndex)) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = i == tabIndex, onClick = { onTab(i) }, text = { Text(t) })
                }
            }
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
                            Text(t.title, style = MaterialTheme.typography.bodyLarge)
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
                Text("Порожньо. Додай ★ до станції.", modifier = Modifier.padding(16.dp))
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
                            Text(s.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${s.genre} · ${s.country}", style = MaterialTheme.typography.bodySmall)
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
