package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight


@Composable
private fun InfoMarquee(
    text: String,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        maxLines = 2,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        style = style,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Головний екран (layout + секції). Стан і колбеки лишаються в MainActivity.
 * BottomNavBar тут, бо використовується лише цим екраном.
 */
// Нижня навігація: Дім / Обрані / Пошук
@Composable
fun BottomNavBar(
    current: String,
    onSelect: (String) -> Unit,
    acc: Color,
    muted: Color,
    card: Color,
    playing: Boolean,
    status: String,
    onPlayPause: () -> Unit,
    extraAbove: (@Composable () -> Unit)? = null,
    onSwipeUp: () -> Unit = {},
    onPull: (Float) -> Unit = {},
    onPullEnd: () -> Unit = {},
    canSkip: Boolean = true,
    wingsOpen: Boolean = true,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val playDp = 58.dp
    val wingDp = playDp
    val wing by animateFloatAsState(
        targetValue = if (wingsOpen && canSkip) 1f else 0f,
        animationSpec = tween(durationMillis = if (wingsOpen && canSkip) 360 else 200, easing = FastOutSlowInEasing),
        label = "skipWings",
    )
    // Та сама форма/розмір; на подкастах лише зсув угору, щоб низ горки не накривав Шоу/Обране
    val playLift = 58.dp
    // Панель card; обводка — той самий тон капсул, але непрозора
    val wrapFill = lerp(card, muted, 0.18f)
    @Composable
    fun NavIco(key: String, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
        val selected = current == key
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) acc.copy(alpha = 0.16f) else Color.Transparent)
                .clickable { onSelect(key) }
                .padding(horizontal = 6.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = desc, tint = if (selected) acc else muted, modifier = Modifier.size(32.dp))
        }
    }
    @Composable
    fun Capsule(active: Boolean, content: @Composable () -> Unit) {
        Row(
            modifier = Modifier
                .background(
                    muted.copy(alpha = 0.08f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
    Box(Modifier.fillMaxWidth().graphicsLayer { clip = false }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val play = playDp.toPx()
                    val wrap = 3.dp.toPx()
                    val corner = 20.dp.toPx()
                    val half = play / 2f
                    val outer = half + wrap
                    val cy = half - playLift.toPx()
                    val gap = 28.dp.toPx()
                    val dist = if (wing > 0.02f) (play + gap) * wing else 0f
                    val shoulder = 40.dp.toPx()
                    // Основна панель
                    val bar = Path().apply {
                        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(corner, corner)))
                    }
                    drawPath(bar, color = card)
                    // Одна «гора»: 1 вершина на Play, 3 вершини при скіпах (середня вища)
                    val joinY = 0.dp.toPx()
                    val left = cx - dist - outer
                    val right = cx + dist + outer
                    val btnTop = cy - outer
                    // головна вершина над Play
                    val mainPeak = btnTop - 18.dp.toPx()
                    // бічні нижчі
                    val sidePeak = btnTop - 10.dp.toPx()
                    val lobe = Path().apply {
                        moveTo(left - shoulder, joinY)
                        // плавний вхід у панель → лівий край
                        cubicTo(
                            left - shoulder * 0.72f, joinY,
                            left - 6.dp.toPx(), joinY,
                            left - 2.dp.toPx(), cy - outer * 0.12f,
                        )
                        cubicTo(
                            left - 1.dp.toPx(), cy - outer * 0.04f,
                            left, cy - outer * 0.02f,
                            left, cy,
                        )
                        if (wing > 0.35f) {
                            // три вершини: ліва → середня (найвища) → права
                            val lx = cx - dist
                            val rx = cx + dist
                            // підйом до лівої вершини
                            cubicTo(
                                left + outer * 0.4f, btnTop - 2.dp.toPx(),
                                lx - outer * 0.3f, sidePeak + 3.dp.toPx(),
                                lx, sidePeak,
                            )
                            // спуск + підйом до центральної (гострої)
                            cubicTo(
                                lx + outer * 0.35f, sidePeak + 4.dp.toPx(),
                                cx - outer * 0.25f, mainPeak + 6.dp.toPx(),
                                cx, mainPeak,
                            )
                            // спуск + підйом до правої
                            cubicTo(
                                cx + outer * 0.25f, mainPeak + 6.dp.toPx(),
                                rx - outer * 0.35f, sidePeak + 4.dp.toPx(),
                                rx, sidePeak,
                            )
                            // спуск до правого краю кнопки
                            cubicTo(
                                rx + outer * 0.3f, sidePeak + 3.dp.toPx(),
                                right - outer * 0.4f, btnTop - 2.dp.toPx(),
                                right, cy,
                            )
                        } else {
                            // одна гостра вершина над Play
                            cubicTo(
                                left + outer * 0.35f, btnTop - 2.dp.toPx(),
                                cx - outer * 0.2f, mainPeak + 5.dp.toPx(),
                                cx, mainPeak,
                            )
                            cubicTo(
                                cx + outer * 0.2f, mainPeak + 5.dp.toPx(),
                                right - outer * 0.35f, btnTop - 2.dp.toPx(),
                                right, cy,
                            )
                        }
                        // вихід у панель справа
                        cubicTo(
                            right, cy - outer * 0.02f,
                            right + 1.dp.toPx(), cy - outer * 0.04f,
                            right + 2.dp.toPx(), cy - outer * 0.12f,
                        )
                        cubicTo(
                            right + 6.dp.toPx(), joinY,
                            right + shoulder * 0.72f, joinY,
                            right + shoulder, joinY,
                        )
                        close()
                    }
                    drawPath(lobe, color = wrapFill)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onPullEnd() },
                        onDragCancel = { onPullEnd() },
                    ) { _, drag -> onPull(drag) }
                },
        ) {
            // без pointerInput тут — інакше з’їдає кліки Шоу/Обране/Пошук
            if (extraAbove != null) extraAbove()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 2.dp,
                        end = 2.dp,
                        // 1 dp між підвкладками подкастів і основним рядом
                        top = if (extraAbove != null) 3.dp else 6.dp,
                        bottom = 6.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Capsule(true) {
                    NavIco("music", Icons.Filled.LibraryMusic, ctx.getString(R.string.nav_music))
                    NavIco("heart", Icons.Filled.Favorite, ctx.getString(R.string.favorites_plural))
                }
                NavIco("home", Icons.Filled.Home, ctx.getString(R.string.nav_home))
                NavIco("podcasts", Icons.Filled.Podcasts, ctx.getString(R.string.nav_podcasts))
                NavIco("search", Icons.Filled.Search, ctx.getString(R.string.nav_search))
                Capsule(true) {
                    NavIco("stations", Icons.Filled.Star, ctx.getString(R.string.nav_stations))
                    NavIco("tabs", Icons.Filled.Radio, ctx.getString(R.string.tabs))
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-playLift))
                .zIndex(4f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onPullEnd() },
                        onDragCancel = { onPullEnd() },
                    ) { _, drag -> onPull(drag) }
                },
            contentAlignment = Alignment.Center,
        ) {
            PlayBtn(
                playing = playing,
                status = status,
                sizeDp = playDp,
                onClick = onPlayPause,
                accent = acc,
                shape = RoundedCornerShape(16.dp),
            )
        }
        if (wing > 0.02f && canSkip) {
            val spread = playDp / 2 + wingDp / 2 + 28.dp
            @Composable
            fun Wing(dx: Dp, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, go: () -> Unit) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressSc by animateFloatAsState(
                    if (pressed) 0.88f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "wingPress",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = dx, y = -playLift)
                        .zIndex(3f)
                        .graphicsLayer {
                            alpha = wing
                            scaleX = pressSc
                            scaleY = pressSc
                        }
                        .size(wingDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.panel)
                        .clickable(
                            enabled = wing > 0.6f,
                            interactionSource = interaction,
                            indication = null,
                        ) { go() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = desc, tint = Palette.text, modifier = Modifier.size(28.dp))
                }
            }
            Wing(-spread * wing, Icons.Filled.SkipPrevious, ctx.getString(R.string.prev_station), onPrev)
            Wing(spread * wing, Icons.Filled.SkipNext, ctx.getString(R.string.next_station), onNext)
        }
    }
}

@Composable
fun BottomNavRail(
    current: String,
    onSelect: (String) -> Unit,
    acc: Color,
    muted: Color,
    card: Color,
) {
    val ctx = LocalContext.current
    @Composable
    fun NavIco(key: String, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
        val selected = current == key
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) acc.copy(alpha = 0.16f) else Color.Transparent)
                .clickable { onSelect(key) }
                .padding(vertical = 8.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = desc, tint = if (selected) acc else muted, modifier = Modifier.size(26.dp))
        }
    }
    @Composable
    fun Capsule(active: Boolean, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .background(
                    muted.copy(alpha = 0.08f),
                    RoundedCornerShape(16.dp)
                )
                .padding(vertical = 2.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(58.dp)
            .background(card, RoundedCornerShape(20.dp))
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // [♫|♥] · Дім · Подкасти · Пошук · [★|📻]
        Capsule(true) {
            NavIco("music", Icons.Filled.LibraryMusic, ctx.getString(R.string.nav_music))
            NavIco("heart", Icons.Filled.Favorite, ctx.getString(R.string.favorites_plural))
        }
        NavIco("home", Icons.Filled.Home, ctx.getString(R.string.nav_home))
        NavIco("podcasts", Icons.Filled.Podcasts, ctx.getString(R.string.nav_podcasts))
        NavIco("search", Icons.Filled.Search, ctx.getString(R.string.nav_search))
        Capsule(true) {
            NavIco("stations", Icons.Filled.Star, ctx.getString(R.string.nav_stations))
            NavIco("tabs", Icons.Filled.Radio, ctx.getString(R.string.tabs))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    tabs: List<String>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    bottomTab: String = "home",
    onBottomTab: (String) -> Unit = {},
    bestRows: List<LocalTrack> = emptyList(),
    favRows: List<Station> = emptyList(),
    qName: String, onName: (String) -> Unit,
    qCountry: String, onCountry: (String) -> Unit,
    qGenre: String, onGenre: (String) -> Unit,
    searchOpen: Boolean = false,
    onSearchOpen: () -> Unit = {},
    onSearch: () -> Unit,
    onRightOpen: () -> Unit = {},
    suggestFor: String = "",
    onSuggestFor: (String) -> Unit = {},
    nameHints: List<String> = emptyList(),
    countryHints: List<String> = emptyList(),
    genreHints: List<String> = emptyList(),
    onMore: () -> Unit,
    canMore: Boolean,
    canMoreSearch: Boolean = false,
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
    searchRows: List<Station> = emptyList(),
    allRadio: List<Station> = emptyList(),
    recentStations: List<Station> = emptyList(),
    homeNearby: List<Station> = emptyList(),
    homeSimilarRb: List<Station> = emptyList(),
    onGenreChip: (String) -> Unit = {},
    localRows: List<LocalTrack>,
    allLocal: List<LocalTrack> = emptyList(),
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
    onPickTheme: (String) -> Unit = {},
    onDeleteStation: (Station) -> Unit,
    pendingDelete: Station? = null,
    onAskDelete: (Station) -> Unit = {},
    onCancelDelete: () -> Unit = {},
    track: String,
    trackHistory: List<TrackHistoryItem> = emptyList(),
    showTrackHistory: Boolean = false,
    onToggleTrackHistory: () -> Unit = {},
    playing: Boolean,
    status: String,
    favUrls: Set<String>,
    bestUris: Set<String>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onPickRadio: (List<Station>, Int) -> Unit,
    onPickOneRadio: (List<Station>, Int) -> Unit = { _, _ -> },
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
    canSkip: Boolean = true,
    skipMode: String = "radio",
    tempRows: List<Station> = emptyList(),
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
    onPrivacy: () -> Unit = {},
    onExit: () -> Unit = {},
    appLang: String = "uk",
    onToggleLanguage: () -> Unit = {},
) {
    val acc = Color(accent)
    val bg = Palette.bg
    val card = Palette.card
    val text = Palette.text
    val muted = Palette.muted
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val logoFont = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

    var dropAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val listState = rememberLazyListState()
    var wingsOpen by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var prev = -1
        snapshotFlow {
            listState.firstVisibleItemIndex * 100_000 + listState.firstVisibleItemScrollOffset
        }.collect { now ->
            if (prev < 0) {
                prev = now
                return@collect
            }
            val d = now - prev
            prev = now
            if (d > 16) wingsOpen = false
            else if (d < -16) wingsOpen = true
        }
    }
    val wingNest = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) wingsOpen = false
                else if (available.y > 6f) wingsOpen = true
                return Offset.Zero
            }
        }
    }
    val nowLocalUi = isLocalNow || currentUrl.startsWith("content:")
    val nowLocalRowsUi = when {
        showLocal -> localRows
        nowLocalUi && bestRows.isNotEmpty() -> bestRows
        else -> localRows
    }
    val nowRadioRowsUi = when {
        // temp = ізольована черга (історія / схожі на Home тощо) — не підміняти на ★
        skipMode == "temp" && tempRows.isNotEmpty() -> tempRows
        else -> radioRows
    }
    fun skipUi(next: Boolean) {
        if (nowLocalUi) {
            if (next) onNext() else onPrev()
        } else {
            val n = nowRadioRowsUi.size
            if (n == 0) { if (next) onNext() else onPrev(); return }
            val i0 = nowRadioRowsUi.indexOfFirst { it.url == currentUrl }.let { if (it < 0) 0 else it }
            val i = if (next) (i0 + 1) % n else (i0 - 1 + n) % n
            if (skipMode == "temp") onPickOneRadio(nowRadioRowsUi, i) else onPickRadio(nowRadioRowsUi, i)
        }
    }
    val pullA = androidx.compose.runtime.remember { Animatable(560f) }
    var sheetShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()
    // Верхня картка (свайп вниз по інфо-панелі)
    var topSleepOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var alarmOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var topThemeOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Права картка пошуку (свайп з правого краю / 🔍)
    val rightA = androidx.compose.runtime.remember { Animatable(1f) } // 1=закрито, 0=відкрито
    var rightShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var rightSearchOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val leftA = androidx.compose.runtime.remember { Animatable(1f) }
    var leftShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var musicAll by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun openRightSheet() {
        onRightOpen()
        rightShow = true
        rightSearchOpen = true
        leftShow = false
        sheetScope.launch {
            leftA.snapTo(1f)
            rightA.stop()
            rightA.snapTo(1f)
            rightA.animateTo(0f, tween(300))
        }
    }
    fun openLeftSheet() {
        rightShow = false
        leftShow = true
        sheetScope.launch {
            rightA.snapTo(1f)
            leftA.stop()
            leftA.snapTo(1f)
            leftA.animateTo(0f, tween(300))
        }
    }
    // Відкривати шторку лише при тапі «Вкладки» внизу, не після вибору жанру в шторці.
    var skipTabsSheetOnce by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(bottomTab) {
        if (bottomTab != "tabs") {
            skipTabsSheetOnce = false
            return@LaunchedEffect
        }
        if (skipTabsSheetOnce) {
            skipTabsSheetOnce = false
            return@LaunchedEffect
        }
        openRightSheet()
    }
    fun closeRightSheet() {
        sheetScope.launch {
            rightA.stop()
            rightA.animateTo(1f, tween(280))
            rightShow = false
        }
    }
    fun closeLeftSheet() {
        sheetScope.launch {
            leftA.stop()
            leftA.animateTo(1f, tween(280))
            leftShow = false
        }
    }
    fun playAllLocal(index: Int, close: Boolean) {
        if (index !in allLocal.indices) return
        onPickLocal(allLocal, index)
        musicAll = true
        onBottomTab("music")
        if (close) closeLeftSheet()
    }
    // Ліва картка з локальною музикою видалена — весь її функціонал
    // перенесено у вкладку LocalContext.current.getString(R.string.favorites_plural) нижньої навігації.
    LaunchedEffect(nowOpen) {
        if (nowOpen) {
            sheetShow = true
            pullA.animateTo(0f, tween(420))
        } else if (!sheetShow) {
            pullA.snapTo(560f)
        }
    }
    val scope = rememberCoroutineScope()
    var toastOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var toastTxt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val ctxToast = LocalContext.current
    LaunchedEffect(status) {
        if (playbackInfoText(ctxToast, status) != null || status.isBlank() || status == ctxToast.getString(R.string.done)) {
            toastOn = false
            return@LaunchedEffect
        }
        toastTxt = status
        toastOn = true
        try {
            kotlinx.coroutines.delay(2000)
        } finally {
            toastOn = false
        }
    }
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            pickStation != null -> onCancelPick()
            newTabOpen -> onCancelNewTab()
            editTab != null -> onCancelEdit()
            pendingDelete != null -> onCancelDelete()
            menuOpen -> onCloseMenu()
            sleepMenu -> onSleepMenu()
            topSleepOpen -> topSleepOpen = false
            topThemeOpen -> topThemeOpen = false
            nowOpen || sheetShow -> {
                sheetScope.launch {
                    pullA.stop()
                    pullA.animateTo(560f, tween(280))
                    sheetShow = false
                    onNowClose()
                }
            }
            leftShow -> closeLeftSheet()
            rightShow -> closeRightSheet()
            else -> {
                val t = System.currentTimeMillis()
                if (t - lastBackAt < 2000L) {
                    (ctxToast as? android.app.Activity)?.moveTaskToBack(true)
                } else {
                    lastBackAt = t
                    toastTxt = ctxToast.getString(R.string.back_again)
                    toastOn = true
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(bg).navigationBarsPadding()) {
    val density = LocalDensity.current
    val view = LocalView.current
    val statusTopDp = with(density) {
        val px = view.rootWindowInsets?.let { ri ->
            WindowInsetsCompat.toWindowInsetsCompat(ri)
                .getInsets(WindowInsetsCompat.Type.statusBars()).top
        } ?: 0
        px.toDp()
    }
    // адаптивна підтяжка: частка реальної висоти status bar (не фіксовані 12dp)
    val statusTighten = (statusTopDp * 0.22f).coerceIn(0.dp, 16.dp)
    val headerTop = (statusTopDp - statusTighten).coerceAtLeast(0.dp)
    val metricsTop = rememberUiMetrics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = headerTop,
                start = if (LocalConfiguration.current.screenWidthDp < 360) 8.dp else 12.dp,
                end = if (LocalConfiguration.current.screenWidthDp < 360) 8.dp else 12.dp,
                bottom = if (isLandscape) 4.dp else 8.dp,
            )
    ) {

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp).height(metricsTop.headerH)) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(card, AppShapes.chip).springPress(0.9f) { topThemeOpen = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Palette, contentDescription = LocalContext.current.getString(R.string.theme_title), tint = text) }
            }
            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                val logoStyle = if (LocalConfiguration.current.screenWidthDp < 360)
                    MaterialTheme.typography.titleMedium
                else
                    MaterialTheme.typography.headlineSmall
                Text("Radio ", color = text, style = logoStyle.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold), maxLines = 1)
                Text("S O", color = acc, style = logoStyle.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                Box(
                    modifier = Modifier.size(40.dp).background(card, AppShapes.chip).springPress(0.9f) { onMenu() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Menu, contentDescription = LocalContext.current.getString(R.string.more_settings), tint = text) }
            }
        }
        // Інфо-панель: іконки на всю висоту, пульс як Play, жанр+країна
        val localNow = isLocalNow || currentUrl.startsWith("content:")
        val infoArtist = if (localNow) {
            track.trim().ifBlank { artistFromTrackTitle(name) }
        } else artistFromTrackTitle(track)
        val infoPhoto by rememberArtistPhotoUrl(infoArtist, bust = if (localNow) infoArtist else currentUrl)
        val podcastNow = genre.equals("podcast", ignoreCase = true)
        val metrics = rememberUiMetrics()
        val infoH = metrics.infoH
        val infoBusy = !playing && run {
            val stt = status.lowercase()
            run {
                        val k = statusKind(LocalContext.current, status)
                        k == StatusKind.CONNECTING || k == StatusKind.BUFFER || k == StatusKind.START
                            || k == StatusKind.RECONNECT
                    }
        }
        val glowInf = rememberInfiniteTransition(label = "infoPulse")
        val pulseState = glowInf.animateFloat(
            1f,
            if (infoBusy) 1.09f else 1.06f,
            infiniteRepeatable(
                tween(if (infoBusy) 420 else 900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            "infoPulseSc",
        )

        @Composable
        fun InfoLand() {
            Column(
                modifier = Modifier
                    .width(metrics.infoLandW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(card)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.infoLandArtH)
                        .graphicsLayer {
                            val sc = if (playing || infoBusy) pulseState.value else 1f
                            scaleX = sc; scaleY = sc
                        }
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(Palette.panel2)
                        .clickable { onCloseMenu(); onNow() },
                ) {
                    val showArtistL = !podcastNow && !infoPhoto.isNullOrBlank()
                    if (showArtistL) {
                        AsyncImage(
                            model = infoPhoto,
                            contentDescription = infoArtist,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        StationArt(
                            url = artUrl(favicon),
                            contentDescription = name,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(0.dp)
                                .size(40.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = 8.dp,
                                        bottomEnd = 0.dp,
                                    )
                                )
                                .background(Palette.panel2),
                        )
                    } else {
                        StationArt(
                            url = artUrl(favicon),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    InfoMarquee(
                        if (podcastNow) name else name.uppercase(),
                        color = acc,
                        style = MaterialTheme.typography.titleSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    InfoMarquee(
                        if (track.isNotBlank()) track else LocalContext.current.getString(R.string.track_unknown),
                        color = text,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val ctryL = if (localNow) "" else DisplayNames.countryLabel(LocalContext.current, country)
                    val genL = if (localNow) "" else DisplayNames.genreLabel(LocalContext.current, genre)
                    if (ctryL.isNotEmpty()) {
                        Text(ctryL, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                    if (genL.isNotEmpty()) {
                        Text(genL, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                    val playInfoL = playbackInfoText(LocalContext.current, status)
                    if (playInfoL != null) {
                        Text(playInfoL, color = acc, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canSkip) {
                        Box(
                            modifier = Modifier.size(40.dp).clickable { skipUi(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = LocalContext.current.getString(R.string.prev_station), tint = text, modifier = Modifier.size(26.dp))
                        }
                    }
                    PlayBtn(playing = playing, status = status, sizeDp = 44.dp, onClick = onPlayPause, accent = acc, shape = RoundedCornerShape(12.dp))
                    if (canSkip) {
                        Box(
                            modifier = Modifier.size(40.dp).clickable { skipUi(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = LocalContext.current.getString(R.string.next_station), tint = text, modifier = Modifier.size(26.dp))
                        }
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = LocalContext.current.getString(R.string.open_now_playing),
                        tint = muted,
                        modifier = Modifier.clickable { onNow() }.size(24.dp)
                    )
                }
            }
        }

        @Composable
        fun ColumnScope.MainPane() {
        if (bottomTab == "podcasts") {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .nestedScroll(wingNest),
            ) {
                PodcastsTabContent(
                    acc = acc, muted = muted, text = text, card = card,
                    blockBack = nowOpen || sheetShow,
                    onScrollDir = { dir -> if (dir > 0) wingsOpen = false else wingsOpen = true },
                )
            }
        } else if (bottomTab == "home") {
            // weight + fillMaxSize: список сам скролить, без боротьби з parent drag
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (acc < -80f) openRightSheet()
                                else if (acc > 80f) openLeftSheet()
                                acc = 0f
                            },
                            onDragCancel = { acc = 0f },
                        ) { _, d -> acc += d }
                    },
            ) {
                HomeTabContent(
                    favRows = favRows,
                    heartRows = bestRows,
                    similar = homeSimilarRb,
                    similarTitle = if (genre.isNotBlank()) LocalContext.current.getString(R.string.similar_genre, genre) else LocalContext.current.getString(R.string.home_similar),
                    recent = recentStations,
                    nearby = homeNearby,
                    genreChips = SearchHints.homeGenres,
                    favUrls = favUrls,
                    acc = acc, muted = muted, text = text,
                    onAllStations = { onBottomTab("stations") },
                    onAllHeart = { onBottomTab("heart") },
                    onPickRadio = onPickRadio,
                    onPickLocal = onPickLocal,
                    onPickOneRadio = onPickOneRadio,
                    onPlayNow = { onCloseMenu(); onNow() },
                    onToggleFav = onToggleFav,
                    onAddToTab = onAddToTab,
                    onGenreChip = onGenreChip,
                    currentUrl = currentUrl,
                )
            }
        } else {
        if (tabs.getOrNull(tabIndex) == "search") {
            SearchSection(
                searchOpen = searchOpen,
                onSearchOpen = onSearchOpen,
                qName = qName,
                onName = onName,
                qCountry = qCountry,
                onCountry = onCountry,
                qGenre = qGenre,
                onGenre = onGenre,
                suggestFor = suggestFor,
                onSuggestFor = onSuggestFor,
                nameHints = nameHints,
                countryHints = countryHints,
                genreHints = genreHints,
                onSearch = onSearch,
                acc = acc,
                muted = muted,
                text = text,
                card = card,
            )
        }
        val libraryUi = LibraryUi(
            radioRows = radioRows,
            localRows = localRows,
            bestRows = bestRows,
            dragging = dragging,
            dropAt = dropAt,
            currentUrl = currentUrl,
            tabs = tabs,
            tabIndex = tabIndex,
            bottomTab = bottomTab,
            favUrls = favUrls,
            bestUris = bestUris,
            canMore = canMore,
            acc = acc,
            muted = muted,
            text = text,
            card = card,
            showAllMusic = musicAll,
        )
        if (showLocal) {
            val libraryActions = LibraryActions(
                onDropAt = { dropAt = it },
                onDragging = { dragging = it },
                onDragStart = onDragStart,
                onMoveTo = onMoveTo,
                onMoveLocalTo = onMoveLocalTo,
                onPickRadio = onPickRadio,
                onPickLocal = onPickLocal,
                onNow = onNow,
                onToggleFav = onToggleFav,
                onAskDelete = onAskDelete,
                onAddToTab = onAddToTab,
                onMore = onMore,
                onToggleBest = onToggleBest,
                onShowAllMusic = { musicAll = it },
            )
            LocalListSection(
                ui = libraryUi,
                listState = listState,
                actions = libraryActions,
                onSwipeOpenRight = { openRightSheet() },
                onSwipeOpenLeft = { openLeftSheet() },
            )
        } else {
            val libraryActions = LibraryActions(
                onDropAt = { dropAt = it },
                onDragging = { dragging = it },
                onDragStart = onDragStart,
                onMoveTo = onMoveTo,
                onMoveLocalTo = onMoveLocalTo,
                onPickRadio = onPickRadio,
                onPickLocal = onPickLocal,
                onNow = onNow,
                onToggleFav = onToggleFav,
                onAskDelete = onAskDelete,
                onAddToTab = onAddToTab,
                onMore = onMore,
                onToggleBest = onToggleBest,
            )
            StationListSection(
                ui = libraryUi,
                listState = listState,
                actions = libraryActions,
                onSwipeOpenRight = { openRightSheet() },
                onSwipeOpenLeft = { openLeftSheet() },
            )
        }
        }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                InfoLand()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp)
                ) {
                    MainPane()
                }
                BottomNavRail(
                    current = bottomTab,
                onSelect = { key ->
                    if (key == "tabs") {
                        skipTabsSheetOnce = false
                        onBottomTab("tabs")
                        openRightSheet()
                    } else {
                        onBottomTab(key)
                    }
                },
                    acc = acc,
                    muted = muted,
                    card = card,
                )
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(card)
                .clickable(onClick = { onCloseMenu(); onNow() })
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(infoH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(infoH)
                        .graphicsLayer {
                            val sc = if (playing || infoBusy) pulseState.value else 1f
                            scaleX = sc; scaleY = sc
                        }
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .background(Palette.panel2),
                ) {
                    val showArtist = !podcastNow && !infoPhoto.isNullOrBlank()
                    if (showArtist) {
                        AsyncImage(
                            model = infoPhoto,
                            contentDescription = infoArtist,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        StationArt(
                            url = artUrl(favicon),
                            contentDescription = name,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(0.dp)
                                .size(36.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                        bottomStart = 8.dp,
                                        bottomEnd = 0.dp,
                                    )
                                )
                                .background(Palette.panel2),
                        )
                    } else {
                        StationArt(
                            url = artUrl(favicon),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    InfoMarquee(
                        if (podcastNow) name else name.uppercase(),
                        color = acc,
                        style = MaterialTheme.typography.titleSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    InfoMarquee(
                        if (track.isNotBlank()) track else LocalContext.current.getString(R.string.track_unknown),
                        color = text,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val ctry = if (localNow) "" else DisplayNames.countryLabel(LocalContext.current, country)
                    val gen = if (localNow) "" else DisplayNames.genreLabel(LocalContext.current, genre)
                    if (ctry.isNotEmpty()) {
                        Text(
                            ctry,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (gen.isNotEmpty()) {
                        Text(
                            gen,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val playInfo = playbackInfoText(LocalContext.current, status)
                    if (playInfo != null) {
                        Text(
                            playInfo,
                            color = acc,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        if (bottomTab == "podcasts") {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .nestedScroll(wingNest),
            ) {
                PodcastsTabContent(
                    acc = acc, muted = muted, text = text, card = card,
                    blockBack = nowOpen || sheetShow,
                    onScrollDir = { dir -> if (dir > 0) wingsOpen = false else wingsOpen = true },
                )
            }
        } else if (bottomTab == "home") {
            // weight + fillMaxSize: список сам скролить, без боротьби з parent drag
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (acc < -80f) openRightSheet()
                                else if (acc > 80f) openLeftSheet()
                                acc = 0f
                            },
                            onDragCancel = { acc = 0f },
                        ) { _, d -> acc += d }
                    },
            ) {
                HomeTabContent(
                    favRows = favRows,
                    heartRows = bestRows,
                    similar = homeSimilarRb,
                    similarTitle = if (genre.isNotBlank()) LocalContext.current.getString(R.string.similar_genre, genre) else LocalContext.current.getString(R.string.home_similar),
                    recent = recentStations,
                    nearby = homeNearby,
                    genreChips = SearchHints.homeGenres,
                    favUrls = favUrls,
                    acc = acc, muted = muted, text = text,
                    onAllStations = { onBottomTab("stations") },
                    onAllHeart = { onBottomTab("heart") },
                    onPickRadio = onPickRadio,
                    onPickLocal = onPickLocal,
                    onPickOneRadio = onPickOneRadio,
                    onPlayNow = { onCloseMenu(); onNow() },
                    onToggleFav = onToggleFav,
                    onAddToTab = onAddToTab,
                    onGenreChip = onGenreChip,
                    currentUrl = currentUrl,
                )
            }
        } else {
        if (tabs.getOrNull(tabIndex) == "search") {
            SearchSection(
                searchOpen = searchOpen,
                onSearchOpen = onSearchOpen,
                qName = qName,
                onName = onName,
                qCountry = qCountry,
                onCountry = onCountry,
                qGenre = qGenre,
                onGenre = onGenre,
                suggestFor = suggestFor,
                onSuggestFor = onSuggestFor,
                nameHints = nameHints,
                countryHints = countryHints,
                genreHints = genreHints,
                onSearch = onSearch,
                acc = acc,
                muted = muted,
                text = text,
                card = card,
            )
        }
        val libraryUi = LibraryUi(
            radioRows = radioRows,
            localRows = localRows,
            bestRows = bestRows,
            dragging = dragging,
            dropAt = dropAt,
            currentUrl = currentUrl,
            tabs = tabs,
            tabIndex = tabIndex,
            bottomTab = bottomTab,
            favUrls = favUrls,
            bestUris = bestUris,
            canMore = canMore,
            acc = acc,
            muted = muted,
            text = text,
            card = card,
            showAllMusic = musicAll,
        )
        if (showLocal) {
            val libraryActions = LibraryActions(
                onDropAt = { dropAt = it },
                onDragging = { dragging = it },
                onDragStart = onDragStart,
                onMoveTo = onMoveTo,
                onMoveLocalTo = onMoveLocalTo,
                onPickRadio = onPickRadio,
                onPickLocal = onPickLocal,
                onNow = onNow,
                onToggleFav = onToggleFav,
                onAskDelete = onAskDelete,
                onAddToTab = onAddToTab,
                onMore = onMore,
                onToggleBest = onToggleBest,
                onShowAllMusic = { musicAll = it },
            )
            LocalListSection(
                ui = libraryUi,
                listState = listState,
                actions = libraryActions,
                onSwipeOpenRight = { openRightSheet() },
                onSwipeOpenLeft = { openLeftSheet() },
            )
        } else {
            val libraryActions = LibraryActions(
                onDropAt = { dropAt = it },
                onDragging = { dragging = it },
                onDragStart = onDragStart,
                onMoveTo = onMoveTo,
                onMoveLocalTo = onMoveLocalTo,
                onPickRadio = onPickRadio,
                onPickLocal = onPickLocal,
                onNow = onNow,
                onToggleFav = onToggleFav,
                onAskDelete = onAskDelete,
                onAddToTab = onAddToTab,
                onMore = onMore,
                onToggleBest = onToggleBest,
            )
            StationListSection(
                ui = libraryUi,
                listState = listState,
                actions = libraryActions,
                onSwipeOpenRight = { openRightSheet() },
                onSwipeOpenLeft = { openLeftSheet() },
            )
        }
        }
        BottomNavBar(
            current = bottomTab,
            onSelect = { key ->
                if (key == "tabs") {
                    // Кожен тап «Вкладки» знову відкриває панель (навіть якщо вже на жанрі)
                    skipTabsSheetOnce = false
                    onBottomTab("tabs")
                    openRightSheet()
                } else {
                    onBottomTab(key)
                }
            },
            acc = acc,
            muted = muted,
            card = card,
            playing = playing,
            status = status,
            onPlayPause = onPlayPause,
            canSkip = canSkip,
            wingsOpen = wingsOpen,
            onPrev = onPrev,
            onNext = onNext,
            extraAbove = if (bottomTab == "podcasts") {
                { PodcastDockTabs(acc = acc, muted = muted) }
            } else null,
            onPull = { drag ->
                if (drag < 0 || sheetShow) {
                    sheetShow = true
                    sheetScope.launch { pullA.snapTo((pullA.value + drag).coerceIn(0f, 560f)) }
                }
            },
            onPullEnd = {
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
        )
        }
    }
    // ===== Права картка: жанрові та кастомні вкладки =====
    // Край поверх картки під час відкриття. Повністю відкриту — край вимкнено
    // (повторний свайп вліво більше не закриває).
    val rightFullyOpen = rightShow && rightA.value <= 0.05f
    val leftFullyOpen = leftShow && leftA.value <= 0.05f
    val rightBusy = rightShow || rightA.value < 0.999f
    val leftBusy = leftShow || leftA.value < 0.999f
    val showRightEdge = !nowOpen && !sheetShow && !rightFullyOpen && !leftBusy
    val showLeftEdge = !nowOpen && !sheetShow && !leftFullyOpen && !rightBusy



    LeftMusicPanel(
        leftA = leftA,
        leftShow = leftShow,
        onLeftShow = { leftShow = it },
        showLeftEdge = showLeftEdge,
        tracks = allLocal,
        currentUrl = currentUrl,
        bestUris = bestUris,
        playing = playing,
        posMs = posMs,
        durMs = durMs,
        acc = acc, muted = muted, text = text, card = card,
        onOpenTrack = { playAllLocal(it, false) },
        onStep = { next ->
            if (allLocal.isEmpty()) return@LeftMusicPanel
            val i0 = allLocal.indexOfFirst { it.uri == currentUrl }
            val i = when {
                i0 < 0 && next -> 0
                i0 < 0 -> allLocal.lastIndex
                next -> (i0 + 1) % allLocal.size
                else -> (i0 - 1 + allLocal.size) % allLocal.size
            }
            playAllLocal(i, false)
        },
        onPlayPause = onPlayPause,
        onSeek = onSeek,
        onToggleBest = onToggleBest,
        onNow = onNow,
    )
    RightTabsPanel(
        rightA = rightA,
        rightShow = rightShow,
        onRightShow = { rightShow = it },
        showRightEdge = showRightEdge,
        onRightOpen = onRightOpen,
        tabs = tabs,
        tabIndex = tabIndex,
        onTab = { i ->
            onTab(i)
            // підсвітити «Вкладки» внизу; не відкривати шторку знову після close
            skipTabsSheetOnce = true
            if (bottomTab != "tabs") onBottomTab("tabs")
            else skipTabsSheetOnce = false // вже на tabs — прапор не потрібен
        },
        onAddTab = onAddTab,
        onLongTab = onLongTab,
        acc = acc, muted = muted, text = text, card = card,
    )

        androidx.compose.animation.AnimatedVisibility(
            visible = toastOn,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .zIndex(40f),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Text(
                toastTxt,
                color = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .wrapContentWidth()
                    .background(Palette.panel2, RoundedCornerShape(12.dp))
                    .border(1.dp, acc.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

    AppSleepDialog(
        open = topSleepOpen,
        onDismiss = { topSleepOpen = false },
        sleepLabel = sleepLabel,
        onSleep = onSleep,
        acc = acc,
        muted = muted,
        text = text,
        card = card,
    )
    AppThemeDialog(
        open = topThemeOpen,
        onDismiss = { topThemeOpen = false },
        themeName = themeName,
        onPickTheme = onPickTheme,
        acc = acc,
        muted = muted,
        text = text,
        card = card,
    )
    // Хедер 🌙 теж відкриває вибір теми
    AppOverflowMenu(
        menuOpen = menuOpen,
        onCloseMenu = onCloseMenu,
        btWatch = btWatch,
        onBt = onBt,
        sleepLabel = sleepLabel,
        sleepMenu = sleepMenu,
        onSleepMenu = { onCloseMenu(); topSleepOpen = true },
        onSleep = onSleep,
        onExport = onExport,
        onImport = onImport,
        onPrivacy = onPrivacy,
        onExit = onExit,
        onAlarm = { alarmOpen = true },
        lang = appLang,
        onLang = onToggleLanguage,
        acc = acc,
        text = text,
    )
    AppAlarmDialog(
        open = alarmOpen,
        onDismiss = { alarmOpen = false },
        nowUrl = currentUrl,
        nowName = name,
        nowFavicon = favicon,
        nowGenre = genre,
        nowCountry = country,
        acc = acc,
        muted = muted,
        text = text,
        card = card,
    )
    }
    PickStationTabDialog(
        pickStation = pickStation,
        targetTabs = targetTabs,
        onPickTabForStation = onPickTabForStation,
        onCancelPick = onCancelPick,
        muted = muted,
        text = text,
        card = card,
    )
    NewTabDialog(
        open = newTabOpen,
        newTabName = newTabName,
        onNewTabName = onNewTabName,
        onCreateTab = onCreateTab,
        onCancelNewTab = onCancelNewTab,
        acc = acc,
        muted = muted,
        text = text,
        card = card,
    )
    EditTabDialog(
        editTab = editTab,
        editName = editName,
        onEditName = onEditName,
        onRenameTab = onRenameTab,
        onDeleteTab = onDeleteTab,
        onCancelEdit = onCancelEdit,
        deleteArmed = deleteArmed,
        acc = acc,
        muted = muted,
        text = text,
        card = card,
    )
    DeleteStationDialog(
        pendingDelete = pendingDelete,
        onDeleteStation = onDeleteStation,
        onCancelDelete = onCancelDelete,
        muted = muted,
        text = text,
        card = card,
    )
    NowPlayingSheet(
        nowOpen = nowOpen,
        sheetShow = sheetShow,
        pullA = pullA,
        actions = NowPlayingActions(
            onSheetShow = { sheetShow = it },
            onNowClose = onNowClose,
            onPickLocal = onPickLocal,
            onPickRadio = onPickRadio,
            onPickOneRadio = onPickOneRadio,
            onToggleFav = onToggleFav,
            onAddToTab = onAddToTab,
            onToggleBest = onToggleBest,
            onSeek = onSeek,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            onPlayPause = onPlayPause,
            skipUi = { skipUi(it) },
        ),
        ui = NowPlayingUi(
            isLocalNow = isLocalNow,
            currentUrl = currentUrl,
            showLocal = showLocal,
            localRows = localRows,
            bestRows = bestRows,
            radioRows = radioRows,
            tempRows = tempRows,
            skipMode = skipMode,
            name = name,
            track = track,
            trackHistory = trackHistory,
            showTrackHistory = showTrackHistory,
            onToggleTrackHistory = onToggleTrackHistory,
            genre = genre,
            country = country,
            favicon = favicon,
            favUrls = favUrls,
            bestUris = bestUris,
            posMs = posMs,
            durMs = durMs,
            canSkip = canSkip,
            playing = playing,
            status = status,
            acc = acc,
            text = text,
            muted = muted,
        ),
    )

}

