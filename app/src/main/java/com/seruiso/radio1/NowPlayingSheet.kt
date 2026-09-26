package com.seruiso.radio1

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.layout.width
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
        val nowLocal = isLocalNow || currentUrl.startsWith("content:")
        val nowLocalRows = when {
            showLocal -> localRows
            nowLocal && bestRows.isNotEmpty() -> bestRows
            else -> localRows
        }
        val nowRadioRows = when {
            // temp = ізольована черга (історія / схожі на Home) — skip лише по ній
            skipMode == "temp" && tempRows.isNotEmpty() -> tempRows
            else -> radioRows
        }
        val arts: List<String> = if (nowLocal) {
            nowLocalRows.map { if (it.albumId.isNotBlank() && it.albumId != "0") "content://media/external/audio/albumart/${it.albumId}" else "" }
        } else nowRadioRows.map { it.favicon }
        val curI0 = if (nowLocal) nowLocalRows.indexOfFirst { it.uri == currentUrl }
                    else nowRadioRows.indexOfFirst { it.url == currentUrl }
        val curI = if (curI0 >= 0) curI0 else 0
        // Динамічний колір з поточної обкладинки (як у Spotify) — для розмитого фону картки Now Playing
        val artCtx = LocalContext.current
        val currentArt = arts.getOrNull(curI) ?: ""
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
        LaunchedEffect(pagerState.settledPage) {
            if (pagerIgnorePick) return@LaunchedEffect
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
            if (arts.isNotEmpty()) stripState.animateScrollToItem(curI)
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
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        label = "npFlip",
                    )
                    val density = LocalDensity.current
                    val cameraDist = 12f * density.density
                    val showBack = flipAngle > 90f

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = flipAngle
                                cameraDistance = cameraDist
                            },
                    ) {
                        if (showBack) {
                            val fmt = remember {
                                SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            }
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationY = 180f }
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 4.dp)
                                    .clickable { ui.onToggleTrackHistory() },
                            ) {
                                Text(
                                    "Історія треків · тап щоб закрити",
                                    color = acc,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                if (ui.trackHistory.isEmpty()) {
                                    Text(
                                        "Поки немає треків",
                                        color = muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    ui.trackHistory.take(30).forEach { item ->
                                        val artist = artistFromTrackTitle(item.title)
                                        val photo by rememberArtistPhotoUrl(artist, bust = item.title)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val iconUrl = when {
                                                !photo.isNullOrBlank() -> photo
                                                item.favicon.isNotBlank() -> artUrl(item.favicon)
                                                else -> ""
                                            }
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
                                                Text(
                                                    item.title,
                                                    color = text,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
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
                                }
                            }
                        } else {
                            val pageKeys = List(
                                if (nowLocal) nowLocalRows.size else nowRadioRows.size
                            ) { page ->
                                if (nowLocal) nowLocalRows.getOrNull(page)?.uri ?: "L$page"
                                else nowRadioRows.getOrNull(page)?.url ?: "R$page"
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { ui.onToggleTrackHistory() },
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
                                        if (nowLocal) nowLocalRows.getOrNull(page)?.artist ?: ""
                                        else artistFromTrackTitle(track)
                                    },
                                    acc = acc,
                                    muted = muted,
                                )
                            }
                            val pagerDragModifier: Modifier =
                                if (!nowLocal && arts.isNotEmpty() && !blockPagerSwipe) {
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
                            )
                        }
                    }
                }
                if (isLocalNow || currentUrl.startsWith("content:")) {
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
