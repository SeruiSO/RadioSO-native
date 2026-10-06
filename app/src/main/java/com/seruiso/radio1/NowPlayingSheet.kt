package com.seruiso.radio1

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.palette.graphics.Palette as SwatchPalette

/**
 * Now-playing bottom sheet: pullA/sheetShow + pager + meta + progress + strip + transport.
 * Мутації pullA/sheetShow — через параметри (host тримає state, бо списки теж чіпають).
 */

/**
 * Колбеки now-playing sheet (окремо від даних — стабільніший lifecycle).
 */
data class NowPlayingActions(
    val onSheetShow: (Boolean) -> Unit,
    val onNowClose: () -> Unit,
    val onPickLocal: (List<LocalTrack>, Int) -> Unit,
    val onPickRadio: (List<Station>, Int) -> Unit,
    val onPickOneRadio: (List<Station>, Int) -> Unit,
    val onToggleFav: (Station) -> Unit,
    val onAddToTab: (Station) -> Unit = {},
    val onToggleBest: (LocalTrack) -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: (() -> Unit)?,
    val onRepeat: (() -> Unit)?,
    val onPlayPause: () -> Unit,
    val skipUi: (Boolean) -> Unit,
)

/**
 * Дані now-playing sheet (без open/close/pullA і без actions).
 */
data class NowPlayingUi(
    val isLocalNow: Boolean,
    val currentUrl: String,
    val showLocal: Boolean,
    val localRows: List<LocalTrack>,
    val bestRows: List<LocalTrack>,
    val radioRows: List<Station>,
    val tempRows: List<Station>,
    val skipMode: String,
    val name: String,
    val track: String,
    val trackHistory: List<TrackHistoryItem> = emptyList(),
    val showTrackHistory: Boolean = false,
    val onToggleTrackHistory: () -> Unit = {},
    val genre: String,
    val country: String,
    val favicon: String,
    val favUrls: Set<String>,
    val bestUris: Set<String>,
    val posMs: Long,
    val durMs: Long,
    val canSkip: Boolean,
    val playing: Boolean,
    val status: String,
    val acc: Color,
    val text: Color,
    val muted: Color,
)


@Composable
fun NowPlayingSheet(
    nowOpen: Boolean,
    sheetShow: Boolean,
    pullA: Animatable<Float, *>,
    actions: NowPlayingActions,
    ui: NowPlayingUi,
) {
    val isLocalNow = ui.isLocalNow
    val currentUrl = ui.currentUrl
    val showLocal = ui.showLocal
    val localRows = ui.localRows
    val bestRows = ui.bestRows
    val radioRows = ui.radioRows
    val tempRows = ui.tempRows
    val skipMode = ui.skipMode
    val name = ui.name
    val track = ui.track
    val genre = ui.genre
    val country = ui.country
    val favicon = ui.favicon
    val favUrls = ui.favUrls
    val bestUris = ui.bestUris
    val posMs = ui.posMs
    val durMs = ui.durMs
    val canSkip = ui.canSkip
    val playing = ui.playing
    val status = ui.status
    val acc = ui.acc
    val text = ui.text
    val muted = ui.muted
    if (!(nowOpen || sheetShow)) return
    val sheetScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // без затемнення — інфо-панель зверху лишається читабельною
                .clickable {
                    sheetScope.launch {
                        pullA.animateTo(560f, tween(300))
                        actions.onSheetShow(false)
                        actions.onNowClose()
                    }
                }
        )
        val isPodcastNow = genre.equals("podcast", ignoreCase = true)
        val nowLocal = (isLocalNow || currentUrl.startsWith("content:")) && !isPodcastNow
        // podcast: черга як радіо (URL+favicon), seek/progress — як local (isLocalNow у ui)
        val queueCtx = LocalContext.current
        val queueLocal = remember(currentUrl, name) {
            val p = queueCtx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
            val uris = org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_URIS, "[]") ?: "[]")
            val titles = org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_TITLES, "[]") ?: "[]")
            val artists = org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_ARTISTS, "[]") ?: "[]")
            val albums = org.json.JSONArray(p.getString(LocalMusicPlugin.KEY_LOCAL_ALBUM_IDS, "[]") ?: "[]")
            val out = ArrayList<LocalTrack>()
            for (i in 0 until uris.length()) {
                val u = uris.optString(i)
                if (u.isBlank()) continue
                out.add(LocalTrack(i.toString(), u, titles.optString(i, ""), artists.optString(i, ""), "", albums.optString(i, "0")))
            }
            out
        }
        val nowLocalRows = if (nowLocal && queueLocal.isNotEmpty()) queueLocal else when {
            showLocal -> localRows
            nowLocal && bestRows.isNotEmpty() -> bestRows
            else -> localRows
        }
        val nowRadioRows = when {
            // temp = ізольована черга (історія / схожі на Home) — skip лише по ній
            skipMode == "temp" && tempRows.isNotEmpty() -> tempRows
            else -> radioRows
        }
        val arts: List<String> = when {
            isPodcastNow -> if (nowRadioRows.isNotEmpty()) nowRadioRows.map { it.favicon } else listOf(favicon)
            nowLocal -> nowLocalRows.map {
                if (it.albumId.isNotBlank() && it.albumId != "0")
                    "content://media/external/audio/albumart/${it.albumId}" else ""
            }
            else -> nowRadioRows.map { it.favicon }
        }
        val curI0 = when {
            isPodcastNow || genre.equals("podcast", ignoreCase = true) ->
                nowRadioRows.indexOfFirst { it.url == currentUrl || currentUrl.contains(it.url.takeLast(24)) }
            nowLocal -> nowLocalRows.indexOfFirst { it.uri == currentUrl }
            else -> nowRadioRows.indexOfFirst { it.url == currentUrl }
        }
        val curI = if (curI0 >= 0) curI0 else 0
        // Динамічний колір з поточної обкладинки (як у Spotify) — для розмитого фону картки Now Playing
        val artCtx = LocalContext.current
        val currentArt = (arts.getOrNull(curI) ?: "").ifBlank { favicon }
        var dynamicArtColor by remember { mutableStateOf<Color?>(null) }
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
        var pagerUserDrag by remember { mutableStateOf(false) }
        var pagerIgnorePick by remember { mutableStateOf(true) }
        // під час свайпу вниз (закриття) — не чіпати pager / зміну станції
        var sheetVerticalDrag by remember { mutableStateOf(false) }
        val blockPagerSwipe = sheetVerticalDrag || pullA.value > 24f
        // Зовнішня зміна / перше відкриття — snap БЕЗ play
        LaunchedEffect(curI, pageCount) {
            pagerIgnorePick = true
            val target = curI.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (pagerState.settledPage != target) {
                pagerState.scrollToPage(target)
            }
            pagerIgnorePick = false
        }
        // Лише жест користувача по пейджеру змінює станцію
        fun playPodcastPage(i: Int) {
            val p = artCtx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, android.content.Context.MODE_PRIVATE)
            val urls = org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_URLS, "[]") ?: "[]")
            val names = org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_NAMES, "[]") ?: "[]")
            val favs = org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_TEMP_FAVICONS, "[]") ?: "[]")
            if (i !in 0 until urls.length()) return
            val url = urls.optString(i)
            if (url.isBlank() || url == currentUrl) return
            val nm = names.optString(i, name)
            val fav = favs.optString(i, favicon)
            val show = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, track) ?: track
            p.edit()
                .putString(LocalMusicPlugin.KEY_MODE, "podcast")
                .putString(BluetoothAutoPlayPlugin.KEY_SKIP_MODE, "temp")
                .putInt(BluetoothAutoPlayPlugin.KEY_TEMP_INDEX, i)
                .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, i)
                .putInt(LocalMusicPlugin.KEY_LOCAL_INDEX, i)
                .putString(BluetoothAutoPlayPlugin.KEY_URL, url)
                .putString(BluetoothAutoPlayPlugin.KEY_NAME, nm)
                .putString(BluetoothAutoPlayPlugin.KEY_FAVICON, fav)
                .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
                .apply()
            val intent = android.content.Intent(artCtx, RadioWatchService::class.java).apply {
                action = RadioWatchService.ACTION_PLAY_URL
                putExtra(RadioWatchService.EXTRA_URL, url)
                putExtra(RadioWatchService.EXTRA_NAME, nm)
                putExtra("favicon", fav)
                putExtra("genre", "podcast")
                putExtra("track", show)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) artCtx.startForegroundService(intent)
                else artCtx.startService(intent)
            } catch (_: Exception) {
                try { artCtx.startService(intent) } catch (_: Exception) {}
            }
        }
        LaunchedEffect(pagerState.settledPage) {
            if (pagerIgnorePick) return@LaunchedEffect
            val i0 = pagerState.settledPage
            if (isPodcastNow) {
                if (i0 != curI) playPodcastPage(i0)
                return@LaunchedEffect
            }
            val i = pagerState.settledPage
            if (i == curI) return@LaunchedEffect
            if (arts.isEmpty()) return@LaunchedEffect
            if (nowLocal) {
                if (i in nowLocalRows.indices && nowLocalRows[i].uri != currentUrl) {
                    actions.onPickLocal(nowLocalRows, i)
                }
            } else if (i in nowRadioRows.indices && nowRadioRows[i].url != currentUrl) {
                if (skipMode == "temp") actions.onPickOneRadio(nowRadioRows, i)
                else actions.onPickRadio(nowRadioRows, i)
            }
        }
        LaunchedEffect(curI, arts.size) {
            if (arts.isEmpty()) return@LaunchedEffect
            stripState.animateScrollToItem(curI.coerceAtMost(arts.lastIndex))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(top = 152.dp)
                .statusBarsPadding()
                .padding(top = 152.dp)
                .statusBarsPadding()
                // меню (~48) + інфо-панель (100) + зазор — картка не накриває інфо
                .padding(top = 152.dp)
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
                    // 80dp+Unbounded було найважчим ефектом у застосунку (GPU blur на весь екран
                    // кожен кадр, поки відкрито Now Playing). 36dp+Rectangle — той самий візуальний
                    // ефект (розмите кольорове підсвічування), помітно дешевше для GPU.
                    .blur(36.dp, BlurredEdgeTreatment.Rectangle)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { sheetVerticalDrag = true },
                            onDragEnd = {
                                sheetScope.launch {
                                    if (pullA.value > 140f) {
                                        pullA.animateTo(560f, tween(280))
                                        sheetVerticalDrag = false
                                        actions.onSheetShow(false)
                                        actions.onNowClose()
                                    } else {
                                        pullA.animateTo(0f, tween(280))
                                        sheetVerticalDrag = false
                                    }
                                }
                            },
                            onDragCancel = { sheetVerticalDrag = false },
                        ) { _, drag ->
                            sheetVerticalDrag = true
                            sheetScope.launch { pullA.snapTo((pullA.value + drag).coerceIn(0f, 560f)) }
                        }
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
                    val flipTarget = if (ui.showTrackHistory) 180f else 0f
                    val flipAngle by animateFloatAsState(
                        targetValue = flipTarget,
                        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                        label = "npFlip",
                    )
                    val density = LocalDensity.current
                    val showBack = flipAngle > 90f
                    val cam = 12f * density.density
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth(),
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = flipAngle
                                cameraDistance = cam
                                alpha = if (showBack) 0f else 1f
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    val pageKeys = List(
                        if (nowLocal) nowLocalRows.size else nowRadioRows.size
                    ) { page ->
                        if (nowLocal) nowLocalRows.getOrNull(page)?.uri ?: "L$page"
                        else nowRadioRows.getOrNull(page)?.url ?: "R$page"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !showBack && !isPodcastNow) { ui.onToggleTrackHistory() },
                    ) {
                    NowPlayingPager(
                        pagerState = pagerState,
                        userScrollEnabled = !blockPagerSwipe,
                        nowLocal = nowLocal,
                        pageKeys = pageKeys,
                        arts = arts,
                        currentUrl = currentUrl,
                        track = track,
                        pageArtistFor = { page ->
                            if (isPodcastNow) ""
                            else if (nowLocal) nowLocalRows.getOrNull(page)?.artist ?: ""
                            else artistFromTrackTitle(track)
                        },
                        acc = acc,
                        muted = muted,
                    )
                    }
                    val pagerDragModifier: Modifier =
                        if (!isPodcastNow && !nowLocal && arts.isNotEmpty() && !blockPagerSwipe) {
                            Modifier.pointerInput(currentUrl, pageCount, blockPagerSwipe) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        if (blockPagerSwipe) return@detectHorizontalDragGestures
                                        pagerUserDrag = true
                                    },
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
                                            if (nowLocal) {
                                                if (target in nowLocalRows.indices && nowLocalRows[target].uri != currentUrl)
                                                    actions.onPickLocal(nowLocalRows, target)
                                            } else if (target in nowRadioRows.indices && nowRadioRows[target].url != currentUrl) {
                                                if (skipMode == "temp") actions.onPickOneRadio(nowRadioRows, target)
                                                else actions.onPickRadio(nowRadioRows, target)
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

                    NowPlayingMeta(
                        name = name,
                        track = track,
                        currentUrl = currentUrl,
                        isFavorite = favUrls.contains(currentUrl),
                        isBest = bestUris.contains(currentUrl),
                        acc = acc,
                        text = text,
                        muted = muted,
                        pagerDragModifier = pagerDragModifier,
                        onToggleFavorite = {
                            actions.onToggleFav(
                                Station(currentUrl, name, genre, country, favicon, "fav")
                            )
                        },
                        onToggleBest = {
                            val tr = localRows.firstOrNull { it.uri == currentUrl }
                                ?: bestRows.firstOrNull { it.uri == currentUrl }
                            if (tr != null) actions.onToggleBest(tr)
                        },
                        onAddToTab = {
                            if (currentUrl.isNotBlank() && !currentUrl.startsWith("content:")) {
                                actions.onAddToTab(
                                    Station(currentUrl, name, genre, country, favicon, "")
                                )
                            }
                        },
                        showStationActions = !isPodcastNow && !currentUrl.startsWith("file:"),
                        titleMaxLines = if (isPodcastNow) 3 else 1,
                        genre = genre,
                        country = country,
                    )
                    }
                        if (ui.showTrackHistory || flipAngle > 0.5f) {
                            TrackHistoryBack(
                                items = ui.trackHistory,
                                text = text,
                                muted = muted,
                                acc = acc,
                                onClose = ui.onToggleTrackHistory,
                                loadArt = flipAngle >= 175f,
                                flipAngle = flipAngle,
                                cameraDist = cam,
                            )
                        }
                    }
                }
                if (isPodcastNow || isLocalNow || currentUrl.startsWith("content:") || currentUrl.startsWith("file:")) {
                    NowPlayingLocalProgress(
                        posMs = posMs,
                        durMs = durMs,
                        acc = acc,
                        muted = muted,
                        text = text,
                        onSeek = actions.onSeek,
                        onShuffle = actions.onShuffle,
                        onRepeat = actions.onRepeat,
                    )
                }
                if (isPodcastNow) {
                    PodcastJumpRow(posMs = posMs, durMs = durMs, acc = acc, text = text, onSeek = actions.onSeek)
                }
                if (!isPodcastNow) {
                val stripLabels = List(arts.size) { i ->
                    when {
                        nowLocal && i in nowLocalRows.indices -> nowLocalRows[i].title
                        i in nowRadioRows.indices -> nowRadioRows[i].name
                        i in radioRows.indices -> radioRows[i].name
                        else -> ""
                    }
                }
                NowPlayingStrip(
                    arts = arts,
                    labels = stripLabels,
                    curI = curI,
                    nowLocal = nowLocal,
                    stripState = stripState,
                    muted = muted,
                    text = text,
                    onPick = { i ->
                        if (nowLocal && i in nowLocalRows.indices) actions.onPickLocal(nowLocalRows, i)
                        else if (i in nowRadioRows.indices) {
                            if (skipMode == "temp") actions.onPickOneRadio(nowRadioRows, i)
                            else actions.onPickRadio(nowRadioRows, i)
                        }
                    },
                )
                }
                NowPlayingTransport(
                    canSkip = canSkip,
                    playing = playing,
                    status = status,
                    acc = acc,
                    text = text,
                    onPlayPause = actions.onPlayPause,
                    onPrev = { actions.skipUi(false) },
                    onNext = { actions.skipUi(true) },
                )
            }
        }
    }
}

@Composable
private fun TrackHistoryBack(
    items: List<TrackHistoryItem>,
    text: Color,
    muted: Color,
    acc: Color,
    onClose: () -> Unit,
    loadArt: Boolean,
    flipAngle: Float,
    cameraDist: Float,
) {
    val fmt = remember { SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val ctx = LocalContext.current
    val show = flipAngle > 90f
    val rows = remember(items) { items.take(30) }
    // Box ловить тап по вільному місці; рядки — окремо (long-press на назві)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = flipAngle - 180f
                cameraDistance = cameraDist
                alpha = if (show) 1f else 0f
            }
            .clickable(enabled = show) { onClose() },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        ) {
            item(key = "hdr") {
                Text(
                    ctx.getString(R.string.track_history_hint),
                    color = acc,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (rows.isEmpty()) {
                    Text(
                        ctx.getString(R.string.track_history_empty),
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(rows, key = { it.atMs.toString() + it.title }) { item ->
                TrackHistoryRow(
                    item = item,
                    text = text,
                    muted = muted,
                    loadArt = loadArt,
                    fmt = fmt,
                    onClose = onClose,
                )
            }
            // місце під списком — тап проходить на Box → закриття
            item(key = "tail") {
                Box(Modifier.fillMaxWidth().height(320.dp))
            }
        }
    }
}

@Composable
private fun TrackHistoryRow(
    item: TrackHistoryItem,
    text: Color,
    muted: Color,
    loadArt: Boolean,
    fmt: SimpleDateFormat,
    onClose: () -> Unit,
) {
    val artist = artistFromTrackTitle(item.title)
    val photo by rememberArtistPhotoUrl(
        if (loadArt) artist else "",
        bust = if (loadArt) item.title else "",
    )
    val iconUrl = when {
        loadArt && !photo.isNullOrBlank() -> photo
        item.favicon.isNotBlank() -> artUrl(item.favicon)
        else -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClose() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.panel2),
            contentAlignment = Alignment.Center,
        ) {
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = artist.ifBlank { item.title },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            // довгий тап: копіювати / шукати в Google
            TrackLongBox(text = item.title, onClick = onClose) {
                Text(
                    item.title,
                    color = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val sub = buildString {
                if (item.station.isNotBlank()) append(item.station)
                if (isNotEmpty()) append(" · ")
                append(fmt.format(Date(item.atMs)))
            }
            Text(
                sub,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
