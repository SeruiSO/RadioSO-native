package com.seruiso.radio1

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PodcastShow(
    val id: Long,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artwork: String,
    val trackCount: Int,
)

data class PodcastEpisode(
    val title: String,
    val audioUrl: String,
    val pubDate: String = "",
    val duration: String = "",
    val image: String = "",
)

private enum class PodSub(val key: String) {
    SHOWS("shows"),      // ★ мої подкасти
    FAV_EPS("fav"),      // ♥ обрані епізоди
    DOWNLOADED("dl"),    // ↓ завантажені
    SEARCH("search"),
}

@Composable
fun PodcastsTabContent(
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current

    var sub by remember { mutableStateOf(PodSub.SHOWS) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastShow>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PodcastShow?>(null) }
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var loadingEps by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var dlBusy by remember { mutableStateOf<String?>(null) }
    // миттєве замальовування ★ у пошуку (до перечитування store)
    var pinnedLocal by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun refresh() {
        tick++
        pinnedLocal = PodcastStore.subs(ctx).map { it.feedUrl }.toSet()
    }
    // sync local pin set once / after tick
    val subs = remember(tick) {
        val s = PodcastStore.subs(ctx)
        pinnedLocal = s.map { it.feedUrl }.toSet()
        s
    }
    val favEps = remember(tick) { PodcastStore.favEpisodes(ctx) }
    val dlEps = remember(tick) { PodcastStore.downloadedList(ctx) }

    fun isPinned(feed: String) = feed in pinnedLocal || subs.any { it.feedUrl == feed }

    fun currentPlayUrl(): String {
        return try {
            ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
                .getString(BluetoothAutoPlayPlugin.KEY_URL, "") ?: ""
        } catch (_: Exception) { "" }
    }
    fun isPlayingAudio(audioUrl: String): Boolean {
        val cur = currentPlayUrl()
        if (cur.isBlank() || audioUrl.isBlank()) return false
        if (cur == audioUrl) return true
        if (cur.contains(audioUrl.substringAfterLast('/').take(40))) return true
        val local = PodcastStore.episodeFile(ctx, audioUrl).absolutePath
        return cur.contains(local) || cur.endsWith(local) || ("file://$local" == cur)
    }


    fun togglePin(show: PodcastShow) {
        // миттєво в UI
        pinnedLocal = if (show.feedUrl in pinnedLocal) pinnedLocal - show.feedUrl
        else pinnedLocal + show.feedUrl
        PodcastStore.toggle(ctx, show)
        tick++
    }

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
            val art = e.image.ifBlank { artwork }
            val path = if (PodcastStore.isDownloaded(ctx, e.audioUrl))
                PodcastStore.episodeFile(ctx, e.audioUrl).absolutePath else e.audioUrl
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
        val path0 = if (PodcastStore.isDownloaded(ctx, ep.audioUrl))
            PodcastStore.episodeFile(ctx, ep.audioUrl).absolutePath else ep.audioUrl
        val mediaUrl = if (path0.startsWith("/")) "file://$path0" else path0
        val art0 = ep.image.ifBlank { artwork }
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
        p.edit()
            .putString(LocalMusicPlugin.KEY_MODE, "podcast")
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_URLS, urls.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES, names.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_FAVICONS, favs.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_GENRES, genres.toString())
            .putString(BluetoothAutoPlayPlugin.KEY_QUEUE_COUNTRIES, countries.toString())
            .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, index)
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
            .putLong("localPositionMs", 0L)
            .apply()
        val i = Intent(ctx, RadioWatchService::class.java).apply {
            action = RadioWatchService.ACTION_PLAY_URL
            putExtra(RadioWatchService.EXTRA_URL, mediaUrl)
            putExtra(RadioWatchService.EXTRA_NAME, ep.title.ifBlank { showTitle })
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        } catch (_: Exception) {
            try { ctx.startService(i) } catch (_: Exception) {}
        }
        status = ctx.getString(R.string.podcast_playing, ep.title)
    }

    fun playFavList(list: List<PodcastFavEpisode>, ep: PodcastFavEpisode) {
        val mapped = list.map {
            PodcastEpisode(it.title, it.audioUrl, it.pubDate, it.duration, it.artwork)
        }
        val idx = list.indexOfFirst { it.audioUrl == ep.audioUrl }.coerceAtLeast(0)
        playEpisodeList(ep.showTitle.ifBlank { ep.title }, ep.artwork, mapped, idx)
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) {
            error = ctx.getString(R.string.podcast_empty_query)
            return
        }
        kb?.hide()
        loading = true
        error = ""
        status = ""
        selected = null
        episodes = emptyList()
        scope.launch {
            val r = withContext(Dispatchers.IO) { ItunesPodcasts.searchUa(q) }
            loading = false
            r.onSuccess {
                results = it
                status = if (it.isEmpty()) ctx.getString(R.string.nothing_found)
                else ctx.getString(R.string.podcast_found, it.size)
            }.onFailure {
                results = emptyList()
                error = it.message ?: ctx.getString(R.string.scan_error)
            }
        }
    }

    fun openShow(show: PodcastShow) {
        if (show.feedUrl.isBlank()) {
            error = ctx.getString(R.string.podcast_no_feed)
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
                if (it.isEmpty()) error = ctx.getString(R.string.podcast_no_episodes)
                else status = ctx.getString(R.string.podcast_episodes, it.size)
            }.onFailure {
                error = it.message ?: ctx.getString(R.string.scan_error)
            }
        }
    }

    fun downloadEp(ep: PodcastEpisode, showTitle: String, artwork: String) {
        if (dlBusy != null) return
        if (PodcastStore.isDownloaded(ctx, ep.audioUrl)) {
            status = ctx.getString(R.string.podcast_already_dl)
            return
        }
        dlBusy = ep.audioUrl
        status = ctx.getString(R.string.podcast_downloading)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                PodcastStore.download(ctx, ep.audioUrl).onSuccess {
                    PodcastStore.rememberDownload(
                        ctx, ep.audioUrl, ep.title, showTitle,
                        ep.image.ifBlank { artwork }, ep.pubDate, ep.duration,
                    )
                }
            }
            dlBusy = null
            refresh()
            r.onSuccess { status = ctx.getString(R.string.podcast_downloaded) }
                .onFailure { error = it.message ?: ctx.getString(R.string.scan_error) }
        }
    }

    @Composable
    fun ShowRow(show: PodcastShow) {
        val pinned = isPinned(show.feedUrl)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(card)
                .clickable { openShow(show) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Palette.panel2),
                contentAlignment = Alignment.Center,
            ) {
                if (show.artwork.isNotBlank()) {
                    AsyncImage(
                        model = show.artwork, contentDescription = show.title,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Filled.Podcasts, null, tint = muted, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(show.title, color = text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (show.author.isNotBlank()) {
                    Text(show.author, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    ) {
        val downloaded = PodcastStore.isDownloaded(ctx, ep.audioUrl)
        val fav = PodcastStore.isFavEpisode(ctx, ep.audioUrl)
        val busy = dlBusy == ep.audioUrl
        val img = ep.image.ifBlank { artwork }
        val playing = isPlayingAudio(ep.audioUrl)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (playing) acc.copy(alpha = 0.18f) else card)
                .clickable { playEpisodeList(showTitle, artwork, list, index) }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Palette.panel2),
                contentAlignment = Alignment.Center,
            ) {
                if (img.isNotBlank()) {
                    AsyncImage(model = img, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Podcasts, null, tint = muted, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ep.title, color = text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                val meta = buildList {
                    if (showTitle.isNotBlank()) add(showTitle)
                    if (ep.pubDate.isNotBlank()) add(ep.pubDate)
                    if (ep.duration.isNotBlank()) add(ep.duration)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            if (showFav) {
                Icon(
                    if (fav) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = ctx.getString(R.string.podcast_fav_ep),
                    tint = if (fav) acc else muted,
                    modifier = Modifier.size(24.dp).clickable {
                        PodcastStore.toggleFavEpisode(
                            ctx,
                            PodcastFavEpisode(
                                title = ep.title, audioUrl = ep.audioUrl,
                                showTitle = showTitle, artwork = img,
                                pubDate = ep.pubDate, duration = ep.duration,
                            ),
                        )
                        refresh()
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            if (showDl) {
                if (busy) {
                    CircularProgressIndicator(color = acc, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = ctx.getString(R.string.podcast_download),
                        tint = if (downloaded) acc else muted,
                        modifier = Modifier.size(26.dp).clickable {
                            downloadEp(ep, showTitle, artwork)
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun SubTab(icon: ImageVector, key: PodSub, label: String) {
        val on = sub == key && selected == null
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    selected = null
                    episodes = emptyList()
                    error = ""
                    sub = key
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = label, tint = if (on) acc else muted, modifier = Modifier.size(24.dp))
            Text(
                label,
                color = if (on) acc else muted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── контент ──
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (selected != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = ctx.getString(R.string.back_again),
                        tint = acc,
                        modifier = Modifier.size(28.dp).clickable {
                            selected = null
                            episodes = emptyList()
                            error = ""
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (selected!!.artwork.isNotBlank()) {
                        AsyncImage(
                            model = selected!!.artwork, contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(selected!!.title, color = text, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        if (selected!!.author.isNotBlank()) {
                            Text(selected!!.author, color = muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Icon(
                        if (isPinned(selected!!.feedUrl)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = ctx.getString(R.string.podcast_pin),
                        tint = if (isPinned(selected!!.feedUrl)) acc else muted,
                        modifier = Modifier.size(28.dp).clickable { togglePin(selected!!) },
                    )
                }
            } else {
                if (sub == PodSub.SEARCH) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        singleLine = true,
                        placeholder = { Text(ctx.getString(R.string.podcast_search_hint), color = muted) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = ctx.getString(R.string.find),
                                tint = acc,
                                modifier = Modifier.size(28.dp).clickable { doSearch() },
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = text, unfocusedTextColor = text,
                            focusedBorderColor = acc, unfocusedBorderColor = muted.copy(alpha = 0.4f),
                            cursorColor = acc,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                // інші міні-вкладки — без великого заголовка (підписи вже внизу)
            }

            if (loading || loadingEps) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = acc, modifier = Modifier.size(36.dp))
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFE57373), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (status.isNotBlank() && !loading && !loadingEps) {
                Text(status, color = muted, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
            }

            if (selected != null) {
                val art = selected!!.artwork
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(episodes, key = { i, e -> "${e.audioUrl}|$i" }) { index, ep ->
                        EpRow(ep, selected!!.title, art, episodes, index)
                    }
                }
            } else when (sub) {
                PodSub.SHOWS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (subs.isEmpty()) {
                            item {
                                Text(ctx.getString(R.string.podcast_my_empty), color = muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            items(subs, key = { "sub-${it.feedUrl}" }) { ShowRow(it) }
                        }
                    }
                }
                PodSub.FAV_EPS -> {
                    val list = favEps.map {
                        PodcastEpisode(it.title, it.audioUrl, it.pubDate, it.duration, it.artwork)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (favEps.isEmpty()) {
                            item {
                                Text(ctx.getString(R.string.podcast_fav_eps_empty), color = muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            itemsIndexed(list, key = { _, e -> "fav-${e.audioUrl}" }) { index, ep ->
                                EpRow(
                                    ep, favEps[index].showTitle, ep.image,
                                    list, index, showFav = true, showDl = true,
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
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (dlEps.isEmpty()) {
                            item {
                                Text(ctx.getString(R.string.podcast_downloaded_empty), color = muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            itemsIndexed(list, key = { _, e -> "dl-${e.audioUrl}" }) { index, ep ->
                                EpRow(
                                    ep, dlEps[index].showTitle, ep.image,
                                    list, index, showFav = true, showDl = false,
                                )
                            }
                        }
                    }
                }
                PodSub.SEARCH -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.id }) { ShowRow(it) }
                    }
                }
            }
        }

        // ── міні-вкладки над нижньою навігацією ──
        if (selected == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(card)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubTab(Icons.Filled.LibraryBooks, PodSub.SHOWS, ctx.getString(R.string.podcast_tab_shows))
                SubTab(Icons.Filled.Bookmark, PodSub.FAV_EPS, ctx.getString(R.string.podcast_tab_eps))
                SubTab(Icons.Filled.Download, PodSub.DOWNLOADED, ctx.getString(R.string.podcast_tab_dl))
                SubTab(Icons.Filled.Search, PodSub.SEARCH, ctx.getString(R.string.podcast_tab_search))
            }
        }
    }
}

object ItunesPodcasts {
    fun searchUa(term: String): Result<List<PodcastShow>> = runCatching {
        val enc = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val url = "https://itunes.apple.com/search?term=$enc&media=podcast&entity=podcast&country=ua&limit=40"
        val body = httpGet(url)
        val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
        val out = ArrayList<PodcastShow>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val feed = o.optString("feedUrl").trim()
            val title = o.optString("collectionName").ifBlank { o.optString("trackName") }.trim()
            if (title.isBlank()) continue
            out.add(
                PodcastShow(
                    id = o.optLong("collectionId", o.optLong("trackId")),
                    title = title,
                    author = o.optString("artistName").trim(),
                    feedUrl = feed,
                    artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") },
                    trackCount = o.optInt("trackCount", 0),
                ),
            )
        }
        out
    }

    fun fetchEpisodes(feedUrl: String): Result<List<PodcastEpisode>> = runCatching {
        parseRss(httpGet(feedUrl))
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RadioSO/1.0 (podcast)")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRss(xml: String): List<PodcastEpisode> {
        val out = ArrayList<PodcastEpisode>()
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
                .replace(Regex("\\s+\\d{2}:\\d{2}:\\d{2}.*"), "").take(32)
            val dur = durRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val img = imgRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            out.add(PodcastEpisode(title, audio, pub, dur, img))
            if (out.size >= 80) break
        }
        return out
    }
}
