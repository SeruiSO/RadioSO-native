package com.seruiso.radio1

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

data class PodcastShow(
    val id: Long,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artwork: String,
    val trackCount: Int,
    val store: String = "",
    val kind: String = "",
)

data class PodcastEpisode(
    val title: String,
    val audioUrl: String,
    val pubDate: String = "",
    val duration: String = "",
    val image: String = "",
    val description: String = "",
)

private enum class PodSub { SHOWS, FAV_EPS, DOWNLOADED, SEARCH, NEW }

private fun formatPodDate(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    val patterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy",
        "dd MMM yyyy",
        "yyyy-MM-dd",
    )
    for (p in patterns) {
        try {
            val d = SimpleDateFormat(p, Locale.US).parse(s) ?: continue
            return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(d)
        } catch (_: Exception) {
        }
    }
    return s.take(32)
}

private fun formatPodDuration(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    if (s.contains(":")) return s
    val sec = s.toIntOrNull() ?: return s
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val r = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}


/** Apple mzstatic: 100x100 → 600x600. Інші URL — як є (не ламати радіо/RSS). */
private fun hiResArt(url: String): String {
    if (url.isBlank()) return url
    val low = url.lowercase()
    if ("mzstatic.com" !in low) return url
    return try {
        Regex("""(\d+)x(\d+)([a-z]*)\.(jpg|png|webp)""", RegexOption.IGNORE_CASE)
            .replace(url) { m ->
                "600x600${m.groupValues[3]}.${m.groupValues[4]}"
            }
    } catch (_: Exception) {
        url
    }
}


/** UI-стан подкастів: переживає portrait↔landscape (різні місця в Compose-дереві). */
private object PodUiSession {
    var subName by mutableStateOf("")
    var subStamp by mutableIntStateOf(0)
    fun pickSub(name: String) {
        subName = name
        selected = null
        episodes = emptyList()
        error = ""
        subStamp++
    }
    var query: String = ""
    var searchTerm: String = ""
    var results: List<PodcastShow> = emptyList()
    var searchPool: List<PodcastShow> = emptyList()
    var visibleN: Int = 50
    var canLoadMore: Boolean = false
    var searchOffset: Int = 0
    var selected: PodcastShow? = null
    var episodes: List<PodcastEpisode> = emptyList()
    var openedFeed: String = ""
    var blurb: String = ""
    var status: String = ""
    var error: String = ""
    var epFilter: String = "all"
    var epVisible: Int = 50
    var kindFilter: String = "all"
    var showQ: String = ""
    var showSort: String = "new"
    var epSort: Boolean = false
    var descOpen: Boolean = false
}

@Composable
fun PodcastsTabContent(
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
    blockBack: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current

    var sub by remember {
        mutableStateOf(
            enumValues<PodSub>().firstOrNull { it.name == PodUiSession.subName }
                ?: enumValues<PodSub>().firstOrNull {
                    it.name == ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
                        .getString("podUiSub", "SHOWS")
                } ?: PodSub.SHOWS
        )
    }
    var epFilter by remember { mutableStateOf(PodUiSession.epFilter) }
    var epVisible by remember { mutableStateOf(PodUiSession.epVisible) }
    var kindFilter by remember { mutableStateOf(PodUiSession.kindFilter) }
    var showQ by remember { mutableStateOf(PodUiSession.showQ) }
    var showSort by remember { mutableStateOf(PodUiSession.showSort) }
    var blurb by remember { mutableStateOf(PodUiSession.blurb) }
    var descOpen by remember { mutableStateOf(PodUiSession.descOpen) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(PodUiSession.query) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(PodUiSession.error) }
    var results by remember { mutableStateOf(PodUiSession.results) }
    var status by remember { mutableStateOf(PodUiSession.status) }
    var selected by remember { mutableStateOf(PodUiSession.selected) }
    var openedFeed by remember { mutableStateOf(PodUiSession.openedFeed) }
    var episodes by remember { mutableStateOf(PodUiSession.episodes) }
    var loadingEps by remember { mutableStateOf(false) }
    LaunchedEffect(PodUiSession.subStamp) {
        val n = enumValues<PodSub>().firstOrNull { it.name == PodUiSession.subName } ?: return@LaunchedEffect
        if (n == sub) return@LaunchedEffect
        selected = null
        episodes = emptyList()
        error = ""
        sub = n
    }

    var livePos by remember { mutableStateOf(0L) }
    var liveDur by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
        while (true) {
            val pos = p.getLong("localPositionMs", 0L)
            val dur = p.getLong("localDurationMs", 0L)
            if (pos != livePos) livePos = pos
            if (dur != liveDur) liveDur = dur
            delay(250)
        }
    }
    var playUrl by remember {
        mutableStateOf(
            ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
                .getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
        )
    }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
        while (true) {
            val u = prefs.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
            if (u != playUrl) playUrl = u
            delay(400)
        }
    }
    BackHandler(enabled = selected != null && !blockBack) {
        selected = null
        episodes = emptyList()
        error = ""
    }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            if (PodcastStore.anyBusy()) tick++
        }
    }
    var dlBusy by remember { mutableStateOf<String?>(null) }
    var pinnedLocal by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favLocal by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchOffset by remember { mutableStateOf(PodUiSession.searchOffset) }
    var searchTerm by remember { mutableStateOf(PodUiSession.searchTerm) }
    var canLoadMore by remember { mutableStateOf(PodUiSession.canLoadMore) }
    var searchPool by remember { mutableStateOf(PodUiSession.searchPool) }
    var visibleN by remember { mutableStateOf(PodUiSession.visibleN) }
    var epSort by remember { mutableStateOf(PodUiSession.epSort) }
    val showsState = rememberLazyListState()
    val episodeListState = rememberLazyListState()
    val searchState = rememberLazyListState()

    // Зберегти UI між portrait/landscape (дві різні гілки в StationScreen)
    androidx.compose.runtime.SideEffect {
        PodUiSession.subName = sub.name
        PodUiSession.query = query
        PodUiSession.searchTerm = searchTerm
        PodUiSession.results = results
        PodUiSession.searchPool = searchPool
        PodUiSession.visibleN = visibleN
        PodUiSession.canLoadMore = canLoadMore
        PodUiSession.searchOffset = searchOffset
        PodUiSession.selected = selected
        PodUiSession.episodes = episodes
        PodUiSession.openedFeed = openedFeed
        PodUiSession.blurb = blurb
        PodUiSession.status = status
        PodUiSession.error = error
        PodUiSession.epFilter = epFilter
        PodUiSession.epVisible = epVisible
        PodUiSession.kindFilter = kindFilter
        PodUiSession.showQ = showQ
        PodUiSession.showSort = showSort
        PodUiSession.epSort = epSort
        PodUiSession.descOpen = descOpen
    }

    DisposableEffect(Unit) {
        val prefs = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == BluetoothAutoPlayPlugin.KEY_URL
                || key == LocalMusicPlugin.KEY_LOCAL_INDEX
                || key == BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX
                || key == BluetoothAutoPlayPlugin.KEY_TEMP_INDEX
            ) {
                tick++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val subs = remember(tick) {
        val s = PodcastStore.subs(ctx)
        pinnedLocal = s.map { it.feedUrl }.toSet()
        s
    }
    val favEps = remember(tick) {
        val f = PodcastStore.favEpisodes(ctx)
        favLocal = f.map { it.audioUrl }.toSet()
        f
    }
    val dlEps = remember(tick) { PodcastStore.downloadedList(ctx) }
    val continueEps = remember(tick) { PodcastStore.recent(ctx) }
    val playedSet = remember(tick) { PodcastStore.played(ctx) }
    fun rssMs(duration: String): Long {
        val raw = duration.trim()
        val sec = when {
            raw.isBlank() -> 0
            raw.contains(":") -> {
                val p = raw.split(":").map { it.toIntOrNull() ?: 0 }
                when (p.size) {
                    3 -> p[0] * 3600 + p[1] * 60 + p[2]
                    2 -> p[0] * 60 + p[1]
                    else -> 0
                }
            }
            else -> raw.toIntOrNull() ?: 0
        }
        return if (sec > 0) sec * 1000L else 0L
    }
    fun podFrac(mediaUrl: String, duration: String): Float {
        val ms = PodcastStore.pos(ctx, mediaUrl)
        val raw = duration.trim()
        val sec = when {
            raw.isBlank() || ms <= 0L -> 0
            raw.contains(":") -> {
                val p = raw.split(":").map { it.toIntOrNull() ?: 0 }
                when (p.size) {
                    3 -> p[0] * 3600 + p[1] * 60 + p[2]
                    2 -> p[0] * 60 + p[1]
                    else -> 0
                }
            }
            else -> raw.toIntOrNull() ?: 0
        }
        if (sec <= 0) return -1f
        return (ms / 1000f / sec).coerceIn(0f, 1f)
    }
    fun listenFrac(audioUrl: String, duration: String): Float {
        val urls = ArrayList<String>()
        if (audioUrl.isNotBlank()) urls.add(audioUrl)
        continueEps.firstOrNull { it.audioUrl == audioUrl }?.mediaUrl?.let { if (it.isNotBlank()) urls.add(it) }
        if (PodcastStore.isDownloaded(ctx, audioUrl)) {
            val path = PodcastStore.localPath(ctx, audioUrl)
            if (path.isNotBlank()) {
                urls.add(path)
                if (path.startsWith("/")) urls.add("file://$path")
            }
        }
        val best = urls.maxByOrNull { PodcastStore.pos(ctx, it) } ?: audioUrl
        return podFrac(best, duration)
    }

    fun isPinned(feed: String) = feed in pinnedLocal
    fun togglePin(show: PodcastShow) {
        pinnedLocal = if (show.feedUrl in pinnedLocal) pinnedLocal - show.feedUrl else pinnedLocal + show.feedUrl
        PodcastStore.toggle(ctx, show)
        tick++
    }

    fun urlsMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        fun norm(u: String): String {
            var s = u.trim()
            if (s.startsWith("file://")) s = s.removePrefix("file://")
            return s.substringBefore('?')
        }
        val na = norm(a)
        val nb = norm(b)
        if (na == nb) return true
        if (na.endsWith(nb) || nb.endsWith(na)) return true
        val leaf = na.substringAfterLast('/')
        return leaf.length > 10 && nb.contains(leaf)
    }

    fun isPlayingAudio(audioUrl: String): Boolean {
        // обов'язково читаємо playUrl — інакше LazyColumn не рекомпонує рядок
        val cur = playUrl
        if (urlsMatch(audioUrl, cur)) return true
        try {
            val local = PodcastStore.localPath(ctx, audioUrl)
            if (local.isNotBlank() && (urlsMatch(cur, local) || urlsMatch(cur, "file://$local"))) return true
        } catch (_: Exception) {}
        return false
    }

    /** Черга як radio/temp — щоб NP карусель і skip бачили епізоди */
    fun playEpisodeList(showTitle: String, artwork: String, list: List<PodcastEpisode>, index: Int) {
        if (index !in list.indices) return
        val ep = list[index]
        if (ep.audioUrl.isBlank()) return
        val urls = JSONArray()
        val names = JSONArray()
        val favs = JSONArray()
        val genres = JSONArray()
        val countries = JSONArray()
        val localUris = JSONArray()
        val localTitles = JSONArray()
        val localArtists = JSONArray()
        val localAlbums = JSONArray()
        list.forEach { e ->
            val art = hiResArt(e.image.ifBlank { artwork })
            val path = PodcastStore.localPath(ctx, e.audioUrl).ifBlank { e.audioUrl }
            val media = if (path.startsWith("/")) "file://$path" else path
            urls.put(media)
            names.put(e.title.ifBlank { showTitle })
            favs.put(art)
            genres.put("podcast")
            countries.put("")
            localUris.put(media)
            localTitles.put(e.title.ifBlank { showTitle })
            localArtists.put(showTitle)
            localAlbums.put("0")
        }
        val path0 = PodcastStore.localPath(ctx, ep.audioUrl).ifBlank { ep.audioUrl }
        val mediaUrl = if (path0.startsWith("/")) "file://$path0" else path0
        val art0 = hiResArt(ep.image.ifBlank { artwork })
        RadioSlot.remember(ctx)
        PodcastStore.notePlay(
            ctx,
            title = ep.title.ifBlank { showTitle },
            audioUrl = ep.audioUrl,
            mediaUrl = mediaUrl,
            showTitle = showTitle,
            artwork = art0,
            duration = ep.duration,
        )
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
        val cur = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
        var resumeMs = (
            if (urlsMatch(cur, mediaUrl) || urlsMatch(cur, ep.audioUrl)) p.getLong("localPositionMs", 0L)
            else PodcastStore.progressMs(ctx, ep.audioUrl, mediaUrl)
        ).coerceAtLeast(0L)
        val knownDur = PodcastStore.progressDur(ctx, ep.audioUrl, mediaUrl)
        if (ep.audioUrl in playedSet || (knownDur > 15_000L && resumeMs >= knownDur - 10_000L)) resumeMs = 0L
        p.edit()
            .putString(LocalMusicPlugin.KEY_MODE, "podcast")
            .putString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "temp")
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, urls.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, names.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_FAVICONS, favs.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, genres.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, countries.toString())
            .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, index)
            .putString(BluetoothAutoPlayPlugin.KEY_TEMP_URLS, urls.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_TEMP_NAMES, names.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_TEMP_FAVICONS, favs.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_TEMP_GENRES, genres.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_TEMP_COUNTRIES, countries.toString())
            .putInt(BluetoothAutoPlayPlugin.KEY_TEMP_INDEX, index)
            .putString(LocalMusicPlugin.KEY_LOCAL_URIS, localUris.toString())
            .putString(LocalMusicPlugin.KEY_LOCAL_TITLES, localTitles.toString())
            .putString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, localArtists.toString())
            .putString(LocalMusicPlugin.KEY_LOCAL_ALBUM_IDS, localAlbums.toString())
            .putInt(LocalMusicPlugin.KEY_LOCAL_INDEX, index)
            .putString(BluetoothAutoPlayPlugin.KEY_URL, mediaUrl)
            .putString(BluetoothAutoPlayPlugin.KEY_NAME, ep.title.ifBlank { showTitle })
            .putString(BluetoothAutoPlayPlugin.KEY_TRACK, showTitle)
            .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, art0)
            .putString(BluetoothAutoPlayPlugin.KEY_GENRE, "podcast")
            .putString(BluetoothAutoPlayPlugin.KEY_COUNTRY, "")
            .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
            .putLong("localPositionMs", resumeMs)
            .commit()
        playUrl = mediaUrl  // миттєва підсвітка
        val i = Intent(ctx, RadioWatchService::class.java).apply {
            action = RadioWatchService.ACTION_PLAY_URL
            putExtra(RadioWatchService.EXTRA_URL, mediaUrl)
            putExtra(RadioWatchService.EXTRA_NAME, ep.title.ifBlank { showTitle })
            putExtra("favicon", art0)
            putExtra("genre", "podcast")
            putExtra("track", showTitle)
            if (resumeMs > 1500L) putExtra(RadioWatchService.EXTRA_POSITION_MS, resumeMs)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        } catch (_: Exception) {
            try { ctx.startService(i) } catch (_: Exception) {}
        }
        tick++
    }

    fun doSearch(more: Boolean = false) {
        val q = if (more) searchTerm else query.trim()
        if (q.isEmpty()) {
            error = ctx.getString(R.string.podcast_empty_query)
            return
        }
        sub = PodSub.SEARCH
        kb?.hide()
        if (more) {
            visibleN += 50
            return
        }
        loading = true
        error = ""
        status = ""
        selected = null
        episodes = emptyList()
        searchTerm = q
        kindFilter = "all"
        epSort = false
        visibleN = 50
        searchPool = emptyList()
        results = emptyList()
        scope.launch {
            val r = withContext(Dispatchers.IO) { ItunesPodcasts.searchUa(q, limit = 600, offset = 0) }
            r.onSuccess { batch ->
                val cand = batch.filter { it.feedUrl.startsWith("http") }
                loading = false
                if (cand.isEmpty()) {
                    status = ""
                    error = ctx.getString(R.string.podcast_no_working)
                    return@onSuccess
                }
                val alive = BooleanArray(cand.size)
                var checked = 0
                status = ctx.getString(R.string.podcast_checking, 0, cand.size)
                coroutineScope {
                    cand.mapIndexed { i, show ->
                        async(Dispatchers.IO) {
                            val ok = try { ItunesPodcasts.feedAlive(show.feedUrl) } catch (_: Exception) { false }
                            withContext(Dispatchers.Main) {
                                alive[i] = ok
                                checked++
                                status = ctx.getString(R.string.podcast_checking, checked, cand.size)
                                val kept = ArrayList<PodcastShow>()
                                for (n in cand.indices) if (alive[n]) kept.add(cand[n])
                                searchPool = kept
                            }
                        }
                    }.awaitAll()
                }
                status = ""
                if (searchPool.isEmpty()) error = ctx.getString(R.string.podcast_no_working)
            }.onFailure {
                loading = false
                searchPool = emptyList()
                error = it.message ?: ctx.getString(R.string.scan_error)
            }
        }
    }

    fun openShow(show: PodcastShow) {
        openedFeed = show.feedUrl
        epVisible = 50
        if (show.feedUrl.isBlank()) {
            error = ctx.getString(R.string.podcast_no_feed)
            return
        }
        // Той самий feed уже завантажений (поворот / повторний open) — без мережі
        if (selected?.feedUrl == show.feedUrl && episodes.isNotEmpty()) {
            selected = show
            loadingEps = false
            error = ""
            return
        }
        selected = show
        loadingEps = true
        error = ""
        episodes = emptyList()
        scope.launch {
            val r = withContext(Dispatchers.IO) { ItunesPodcasts.fetchEpisodes(show.feedUrl) }
            loadingEps = false
            r.onSuccess {
                episodes = it
                blurb = ItunesPodcasts.lastBlurb
                descOpen = false
                if (PodcastStore.subs(ctx).any { it.feedUrl == show.feedUrl }) {
                    PodcastStore.cacheShow(ctx, show.feedUrl, show.title, show.artwork, it)
                }
                if (it.isEmpty()) error = ctx.getString(R.string.podcast_no_episodes)
            }.onFailure {
                error = it.message ?: ctx.getString(R.string.scan_error)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Уже відновлено з PodUiSession (поворот) — не перезавантажувати feed/search
        if (selected != null && episodes.isNotEmpty()) return@LaunchedEffect
        if (selected != null) return@LaunchedEffect
        if (results.isNotEmpty() || searchTerm.isNotBlank()) return@LaunchedEffect
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
        val raw = p.getString("podUiShow", "") ?: ""
        if (raw.isBlank()) return@LaunchedEffect
        val o = org.json.JSONObject(raw)
        val show = PodcastShow(
            id = o.optLong("id"),
            title = o.optString("title"),
            author = o.optString("author"),
            feedUrl = o.optString("feedUrl"),
            artwork = o.optString("artwork"),
            trackCount = o.optInt("trackCount"),
        )
        if (show.feedUrl.isNotBlank()) openShow(show)
    }
    LaunchedEffect(sub, selected?.feedUrl, selected?.title) {
        val ed = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("podUiSub", sub.name)
        val s = selected
        if (s == null) ed.putString("podUiShow", "")
        else ed.putString(
            "podUiShow",
            org.json.JSONObject()
                .put("id", s.id)
                .put("title", s.title)
                .put("author", s.author)
                .put("feedUrl", s.feedUrl)
                .put("artwork", s.artwork)
                .put("trackCount", s.trackCount)
                .toString(),
        )
        ed.apply()
    }

    fun downloadEp(ep: PodcastEpisode, showTitle: String, artwork: String) {
        if (PodcastStore.isDownloaded(ctx, ep.audioUrl)) {
            PodcastStore.deleteDownload(ctx, ep.audioUrl)
            tick++
            return
        }
        PodcastStore.enqueue(
            ctx, ep.audioUrl, ep.title, showTitle,
            ep.image.ifBlank { artwork }, ep.pubDate, ep.duration,
        ) { err ->
            if (err != null) error = err
            tick++
        }
        tick++
    }

    @Composable
    fun SubTab(icon: ImageVector, key: PodSub, label: String) {
        val on = sub == key
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (on) acc.copy(alpha = 0.20f) else muted.copy(alpha = 0.12f))
                .clickable {
                    selected = null
                    episodes = emptyList()
                    error = ""
                    sub = key
                }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(label, color = if (on) acc else muted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }

    @Composable
    fun ShowRow(show: PodcastShow) {
        val pinned = isPinned(show.feedUrl)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (show.feedUrl.isNotBlank() && show.feedUrl == openedFeed) acc.copy(alpha = 0.28f) else card)
                .clickable { openShow(show) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Palette.panel2),
                contentAlignment = Alignment.Center,
            ) {
                if (show.artwork.isNotBlank()) {
                    AsyncImage(model = hiResArt(show.artwork), contentDescription = show.title,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Podcasts, null, tint = muted, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(show.title, color = text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                val meta = buildList {
                    if (show.author.isNotBlank()) add(show.author)
                    if (show.trackCount > 0) add(ctx.getString(R.string.podcast_ep_short, show.trackCount))
                    if (show.store.isNotBlank()) add(show.store)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Icon(
                if (pinned) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = ctx.getString(R.string.podcast_pin),
                tint = if (pinned) acc else muted,
                modifier = Modifier.size(28.dp).clickable { togglePin(show) },
            )
        }
    }

    @Composable
    fun EpRow(
        ep: PodcastEpisode,
        showTitle: String,
        artwork: String,
        list: List<PodcastEpisode>,
        index: Int,
        showFav: Boolean = true,
        showDl: Boolean = true,
        frac: Float = -1f,
    ) {
        val watchPlayUrl = playUrl
        val playing = isPlayingAudio(ep.audioUrl)
        val fav = ep.audioUrl in favLocal
        val downloaded = PodcastStore.isDownloaded(ctx, ep.audioUrl)
        val busy = PodcastStore.isBusy(ep.audioUrl)
        val img = ep.image.ifBlank { artwork }
        val durTxt = formatPodDuration(ep.duration)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (playing) acc.copy(alpha = 0.28f) else card)
                .clickable { playEpisodeList(showTitle, artwork, list, index) }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Palette.panel2),
                contentAlignment = Alignment.Center,
            ) {
                if (img.isNotBlank()) {
                    AsyncImage(model = hiResArt(img), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Podcasts, null, tint = muted, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val heard = ep.audioUrl in playedSet
                Text(ep.title, color = if (playing) acc else if (heard) muted else text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                val meta = buildList {
                    if (showTitle.isNotBlank()) add(showTitle)
                    if (ep.pubDate.isNotBlank()) add(formatPodDate(ep.pubDate))
                    if (durTxt.isNotBlank()) add(durTxt)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall)
                }
                val ms = if (playing) livePos else PodcastStore.progressMs(ctx, ep.audioUrl)
                val savedDur = if (playing && liveDur > 0L) liveDur else PodcastStore.progressDur(ctx, ep.audioUrl)
                val rss = rssMs(ep.duration)
                val durMs = if (savedDur > 0L) savedDur else rss
                val playedFrac = if (ms > 0L && durMs > 0L) (ms.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else -1f
                val bar = if (busy) PodcastStore.fracOf(ep.audioUrl) else playedFrac
                if (bar in 0.004f..0.995f) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(muted.copy(alpha = 0.28f)),
                    ) {
                        Box(Modifier.fillMaxWidth(bar).fillMaxHeight().background(acc))
                    }
                }
            }
            if (showDl && !busy) {
                Icon(
                    if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = ctx.getString(R.string.podcast_download),
                    tint = if (downloaded) acc else muted,
                    modifier = Modifier.size(22.dp).clickable { downloadEp(ep, showTitle, artwork) },
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    color = acc,
                    modifier = Modifier.size(22.dp).clickable {
                        PodcastStore.cancel(ep.audioUrl)
                        tick++
                    },
                    strokeWidth = 2.dp,
                )
            }
            run {
                var menu by remember(ep.audioUrl) { mutableStateOf(false) }
                Box {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = ctx.getString(R.string.podcast_menu),
                        tint = muted,
                        modifier = Modifier.size(40.dp).clickable { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (showFav) {
                            DropdownMenuItem(
                                text = { Text(if (fav) ctx.getString(R.string.podcast_unfav) else ctx.getString(R.string.podcast_fav_ep)) },
                                onClick = {
                                    menu = false
                                    favLocal = if (ep.audioUrl in favLocal) favLocal - ep.audioUrl else favLocal + ep.audioUrl
                                    PodcastStore.toggleFavEpisode(
                                        ctx,
                                        PodcastFavEpisode(
                                            title = ep.title, audioUrl = ep.audioUrl,
                                            showTitle = showTitle, artwork = img,
                                            pubDate = ep.pubDate, duration = ep.duration,
                                        ),
                                    )
                                    tick++
                                },
                            )
                        }
                        if (showDl) {
                            DropdownMenuItem(
                                text = { Text(if (downloaded) ctx.getString(R.string.podcast_delete_file) else ctx.getString(R.string.podcast_dl_action)) },
                                onClick = {
                                    menu = false
                                    downloadEp(ep, showTitle, artwork)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (ep.audioUrl in playedSet) ctx.getString(R.string.podcast_mark_unplayed) else ctx.getString(R.string.podcast_mark_played)) },
                            onClick = {
                                menu = false
                                PodcastStore.togglePlayed(ctx, ep.audioUrl)
                                tick++
                            },
                        )
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (selected != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = ctx.getString(R.string.back_again),
                        tint = acc,
                        modifier = Modifier.size(28.dp).clickable {
                            selected = null; episodes = emptyList(); error = ""
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (selected!!.artwork.isNotBlank()) {
                        AsyncImage(
                            model = hiResArt(selected!!.artwork), contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(selected!!.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                    }
                    Icon(
                        if (isPinned(selected!!.feedUrl)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = ctx.getString(R.string.podcast_pin),
                        tint = if (isPinned(selected!!.feedUrl)) acc else muted,
                        modifier = Modifier.size(28.dp).clickable { togglePin(selected!!) },
                    )
                }
            } else if (sub == PodSub.SEARCH) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = text,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(false) }),
                    cursorBrush = SolidColor(acc),
                    decorationBox = { inner ->
                        Row(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, muted.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (query.isEmpty()) {
                                    Text(
                                        ctx.getString(R.string.podcast_search_hint),
                                        color = muted,
                                        maxLines = 1,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                inner()
                            }
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = ctx.getString(R.string.find),
                                tint = acc,
                                modifier = Modifier.size(18.dp).clickable { doSearch(false) },
                            )
                        }
                    },
                )
                if (searchPool.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        listOf(
                            "all" to ctx.getString(R.string.podcast_all),
                            "music" to ctx.getString(R.string.podcast_music),
                            "about" to ctx.getString(R.string.podcast_about),
                            "other" to ctx.getString(R.string.podcast_other),
                        ).forEach { (k, label) ->
                            Text(
                                label,
                                color = if (kindFilter == k) acc else muted,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable { kindFilter = k },
                            )
                        }
                        Text(
                            ctx.getString(R.string.podcast_most_eps),
                            color = if (epSort) acc else muted,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { epSort = !epSort },
                        )
                    }
                }
            }

            if (loading || loadingEps) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = acc, modifier = Modifier.size(32.dp))
                }
            }
            if (status.isNotBlank() && selected == null) {
                Text(status, color = muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFE57373), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }

            if (selected != null) {
                val art = selected!!.artwork
                val filtered = episodes.filter { ep ->
                    val byTab = when (epFilter) {
                        "new" -> ep.audioUrl !in playedSet
                        "dl" -> PodcastStore.isDownloaded(ctx, ep.audioUrl)
                        else -> true
                    }
                    byTab
                }
                val shown = filtered.take(epVisible)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${shown.size} / ${filtered.size}",
                        color = muted,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    listOf(
                        "all" to ctx.getString(R.string.podcast_all),
                        "new" to ctx.getString(R.string.podcast_unplayed),
                        "dl" to ctx.getString(R.string.podcast_tab_dl),
                    ).forEach { (k, label) ->
                        Text(
                            label,
                            color = if (epFilter == k) acc else muted,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { epFilter = k; epVisible = 50 },
                        )
                    }
                }
                LaunchedEffect(selected?.feedUrl, episodes.size) {
                    val feed = selected?.feedUrl ?: return@LaunchedEffect
                    if (episodes.isEmpty()) return@LaunchedEffect
                    val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
                    if (p.getString("podUiFeed", "") != feed) return@LaunchedEffect
                    val want = p.getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
                    var idx = shown.indexOfFirst { it.audioUrl == want || (want.isNotBlank() && want.contains(it.audioUrl)) }
                    if (idx < 0) idx = p.getInt("podUiEpIndex", 0)
                    if (shown.isNotEmpty()) episodeListState.scrollToItem(idx.coerceIn(0, shown.lastIndex))
                }
                LaunchedEffect(episodeListState, selected?.feedUrl) {
                    val feed = selected?.feedUrl ?: return@LaunchedEffect
                    snapshotFlow { episodeListState.firstVisibleItemIndex }.collect { i ->
                        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
                            .edit().putInt("podUiEpIndex", i).putString("podUiFeed", feed).apply()
                    }
                }
                LazyColumn(
                    state = episodeListState,
                    modifier = Modifier.fillMaxSize().padding(top = 2.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(shown, key = { i, e -> "e-$i-${e.audioUrl}" }) { _, ep ->
                        val watchPlayUrl = playUrl
                        val index = episodes.indexOfFirst { it.audioUrl == ep.audioUrl }
                        EpRow(ep, selected!!.title, art, episodes, index)
                    }
                    if (shown.size < filtered.size) {
                        item {
                            TextButton(
                                onClick = { epVisible += 50 },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(ctx.getString(R.string.podcast_more50), color = acc)
                            }
                        }
                    }
                }
            } else when (sub) {
                PodSub.SHOWS -> LazyColumn(
                    state = showsState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (continueEps.isNotEmpty()) {
                        item { Text(ctx.getString(R.string.podcast_continue), color = text, style = MaterialTheme.typography.titleSmall) }
                        itemsIndexed(continueEps, key = { i, e -> "c-$i-${e.audioUrl}" }) { i, ep ->
                            val list = continueEps.map {
                                PodcastEpisode(it.title, it.audioUrl, "", it.duration, it.artwork)
                            }
                            EpRow(
                                list[i], ep.showTitle, ep.artwork, list, i,
                                showDl = true,
                                frac = podFrac(ep.mediaUrl, ep.duration),
                            )
                        }
                    }
                    item { Text(ctx.getString(R.string.podcast_my_shows), color = text, style = MaterialTheme.typography.titleSmall) }
                    if (subs.isEmpty()) {
                        item {
                            Text(ctx.getString(R.string.podcast_my_empty), color = muted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else items(subs, key = { "s-${it.feedUrl}" }) { ShowRow(it) }
                }
                PodSub.FAV_EPS -> {
                    val list = favEps.map {
                        PodcastEpisode(it.title, it.audioUrl, it.pubDate, it.duration, it.artwork)
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (list.isEmpty()) {
                            item {
                                Text(ctx.getString(R.string.podcast_fav_eps_empty), color = muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            itemsIndexed(list, key = { i, e -> "f-$i-${e.audioUrl}" }) { i, ep ->
                                EpRow(
                                    ep, favEps[i].showTitle, ep.image, list, i,
                                    showFav = true, showDl = true,
                                    frac = listenFrac(ep.audioUrl, ep.duration),
                                )
                            }
                        }
                    }
                }
                PodSub.DOWNLOADED -> {
                    val list = dlEps.map {
                        PodcastEpisode(it.title, it.audioUrl, it.pubDate, it.duration, it.artwork)
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (list.isEmpty()) {
                            item {
                                Text(ctx.getString(R.string.podcast_downloaded_empty), color = muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            itemsIndexed(list, key = { i, e -> "d-$i-${e.audioUrl}" }) { i, ep ->
                                EpRow(ep, dlEps[i].showTitle, ep.image, list, i, showFav = true, showDl = true)
                            }
                        }
                    }
                }
                PodSub.NEW -> {
                    val fresh = remember(tick) { PodcastStore.news(ctx) }
                    Column(Modifier.fillMaxSize()) {
                        Button(
                            onClick = {
                                if (refreshing) return@Button
                                refreshing = true
                                PodcastStore.io.launch {
                                    val shows = PodcastStore.subs(ctx).take(12)
                                    for (s in shows) {
                                        val eps = ItunesPodcasts.fetchEpisodes(s.feedUrl).getOrNull() ?: continue
                                        PodcastStore.cacheShow(ctx, s.feedUrl, s.title, s.artwork, eps)
                                    }
                                    refreshing = false
                                    tick++
                                }
                            },
                            enabled = !refreshing,
                            modifier = Modifier.padding(bottom = 4.dp).height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color.White),
                        ) {
                            Text(
                                if (refreshing) ctx.getString(R.string.podcast_refreshing) else ctx.getString(R.string.podcast_refresh),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (fresh.isEmpty()) {
                                item {
                                    Text(ctx.getString(R.string.podcast_no_new), color = muted,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                itemsIndexed(fresh, key = { i, e -> "n-$i-${e.audioUrl}" }) { i, ep ->
                                    EpRow(ep, ep.description, ep.image, fresh, i, showFav = true, showDl = true)
                                }
                            }
                        }
                    }
                }
                PodSub.SEARCH -> LazyColumn(
                    state = searchState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val ordered = if (epSort) searchPool.sortedByDescending { it.trackCount } else searchPool
                    val filtered = if (kindFilter == "all") ordered else ordered.filter { it.kind == kindFilter }
                    val shown = filtered.take(visibleN)
                    if (shown.isEmpty()) {
                        item { Text(ctx.getString(R.string.podcast_filter_empty), color = muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    itemsIndexed(shown, key = { i, it -> "q-$i-${it.feedUrl.ifBlank { it.id.toString() }}" }) { _, it -> ShowRow(it) }
                    if (shown.size < filtered.size) {
                        item {
                            TextButton(
                                onClick = { visibleN += 50 },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(ctx.getString(R.string.podcast_more50), color = acc)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastDockTabs(acc: Color, muted: Color) {
    val ctx = LocalContext.current
    val cur = PodUiSession.subName.ifBlank { "SHOWS" }
    @Composable
    fun Chip(key: PodSub, label: String) {
        val on = cur == key.name
        Text(
            label,
            color = if (on) acc else muted,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (on) acc.copy(alpha = 0.20f) else muted.copy(alpha = 0.12f))
                .clickable { PodUiSession.pickSub(key.name) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(PodSub.SHOWS, ctx.getString(R.string.podcast_tab_shows))
        Chip(PodSub.FAV_EPS, ctx.getString(R.string.podcast_tab_eps))
        Chip(PodSub.DOWNLOADED, ctx.getString(R.string.podcast_tab_dl))
        Chip(PodSub.SEARCH, ctx.getString(R.string.podcast_tab_search))
        Chip(PodSub.NEW, ctx.getString(R.string.podcast_tab_new))
    }
}

object ItunesPodcasts {
    fun searchUa(term: String, limit: Int = 50, offset: Int = 0): Result<List<PodcastShow>> = runCatching {
        val enc = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val countries = listOf("us", "de", "gb", "ua")
        val perCountry = ArrayList<List<PodcastShow>>()
        for (country in countries) {
            val url = "https://itunes.apple.com/search?term=$enc&media=podcast&entity=podcast&country=$country&limit=100"
            val list = ArrayList<PodcastShow>()
            try {
                val arr = JSONObject(httpGet(url)).optJSONArray("results") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val feed = o.optString("feedUrl").trim()
                    val title = o.optString("collectionName").ifBlank { o.optString("trackName") }.trim()
                    if (title.isBlank()) continue
                    val ids = o.optJSONArray("genreIds")
                    var music = false
                    var about = false
                    if (ids != null) {
                        for (g in 0 until ids.length()) {
                            when (ids.optString(g)) {
                                "1310" -> music = true
                                "1523", "1524", "1525" -> about = true
                            }
                        }
                    }
                    val kind = when {
                        about -> "about"
                        music -> "music"
                        else -> "other"
                    }
                    list.add(
                        PodcastShow(
                            id = o.optLong("collectionId", o.optLong("trackId")),
                            title = title,
                            author = o.optString("artistName").trim(),
                            feedUrl = feed,
                            artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") },
                            trackCount = o.optInt("trackCount", 0),
                            store = country.uppercase(),
                            kind = kind,
                        ),
                    )
                }
            } catch (_: Exception) {
            }
            perCountry.add(list)
        }
        val seen = HashSet<String>()
        val out = ArrayList<PodcastShow>()
        val max = perCountry.maxOfOrNull { it.size } ?: 0
        for (rank in 0 until max) {
            for (list in perCountry) {
                if (rank >= list.size) continue
                val show = list[rank]
                val key = show.feedUrl.ifBlank { show.id.toString() }
                if (seen.add(key)) out.add(show)
            }
        }
        out.drop(offset).take(limit)
    }

    @Volatile var lastBlurb: String = ""

    fun fetchEpisodes(feedUrl: String): Result<List<PodcastEpisode>> = runCatching {
        val xml = httpGet(feedUrl)
        val d = Regex("<description[^>]*>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</description>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.trim().orEmpty()
        lastBlurb = d.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(360)
        parseRss(xml)
    }

    private fun openFollow(url: String, range: String? = null): HttpURLConnection {
        var current = url
        for (hop in 0 until 5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "RadioSO/1.0 (podcast)")
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                if (range != null) setRequestProperty("Range", range)
            }
            val code = try { conn.responseCode } catch (e: Exception) { conn.disconnect(); throw e }
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("redirect")
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                continue
            }
            return conn
        }
        error("redirect")
    }

    fun feedAlive(url: String): Boolean {
        if (!url.startsWith("http")) return false
        return try {
            val conn = openFollow(url, "bytes=0-8191")
            try {
                val code = conn.responseCode
                if (code == 404 || code == 410) return false
                if (code !in 200..299 && code != 206) return true
                val buf = CharArray(8192)
                val n = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).read(buf)
                if (n <= 0) return true
                val body = String(buf, 0, n).lowercase()
                if ("<html" in body && "enclosure" !in body && "<item" !in body && "<rss" !in body) return false
                true
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun httpGet(url: String): String {
        val conn = openFollow(url)
        try {
            if (conn.responseCode !in 200..299 && conn.responseCode != 206) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRss(xml: String): List<PodcastEpisode> {
        val out = ArrayList<PodcastEpisode>()
        val seen = HashSet<String>()
        val itemRe = Regex("<item(\\s[^>]*)?>([\\s\\S]*?)</item>", RegexOption.IGNORE_CASE)
        val titleRe = Regex("<title[^>]*>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</title>", RegexOption.IGNORE_CASE)
        val encRe = Regex("""<enclosure[^>]*url\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        val encRe2 = Regex("""url\s*=\s*["']([^"']+\.(?:mp3|m4a|aac|ogg|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE)
        val dateRe = Regex("<pubDate[^>]*>([\\s\\S]*?)</pubDate>", RegexOption.IGNORE_CASE)
        val durRe = Regex("<itunes:duration[^>]*>([\\s\\S]*?)</itunes:duration>", RegexOption.IGNORE_CASE)
        val imgRe = Regex(
            """<(?:itunes:image|media:thumbnail)[^>]*(?:href|url)\s*=\s*["']([^"']+)["'][^>]*/?>""",
            RegexOption.IGNORE_CASE,
        )
        for (m in itemRe.findAll(xml)) {
            val block = m.groupValues[2]
            val audio = encRe.find(block)?.groupValues?.get(1)?.trim()
                ?: encRe2.find(block)?.groupValues?.get(1)?.trim()
                ?: continue
            if (!audio.startsWith("http")) continue
            var title = titleRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            title = title.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace(Regex("<[^>]+>"), "").trim()
            if (title.isBlank()) title = audio.substringAfterLast('/').substringBefore('?')
            val pub = dateRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(80)
            val dur = durRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val img = imgRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val descRe = Regex("<description[^>]*>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:]]>)?</description>", RegexOption.IGNORE_CASE)
            var desc = descRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            desc = desc.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(280)
            if (!seen.add(audio)) continue
            out.add(PodcastEpisode(title, audio, pub, dur, img, desc))
        }
        return out
    }
}
