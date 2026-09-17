package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

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
    onSwipeUp: () -> Unit = {},
    onPull: (Float) -> Unit = {},
    onPullEnd: () -> Unit = {},
) {
    val ctx = LocalContext.current
    @Composable
    fun NavIco(key: String, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String) {
        val selected = current == key
        Icon(
            icon,
            contentDescription = desc,
            tint = if (selected) acc else muted,
            modifier = Modifier
                .clickable { onSelect(key) }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .size(34.dp)
        )
    }
    @Composable
    fun Capsule(active: Boolean, content: @Composable () -> Unit) {
        Row(
            modifier = Modifier
                .background(
                    if (active) acc.copy(alpha = 0.14f) else muted.copy(alpha = 0.08f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(card, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onPullEnd() },
                    onDragCancel = { onPullEnd() }
                ) { _, drag ->
                    if (drag < 0) onPull(drag)
                    else onPull(drag)
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [★ | 📻] · Дім · Пошук · [♥ | ♫]
        Capsule(current == "stations" || current == "tabs") {
            NavIco("stations", Icons.Filled.Star, ctx.getString(R.string.nav_stations))
            NavIco("tabs", Icons.Filled.Radio, ctx.getString(R.string.tabs))
        }
        NavIco("home", Icons.Filled.Home, ctx.getString(R.string.nav_home))
        NavIco("search", Icons.Filled.Search, ctx.getString(R.string.nav_search))
        Capsule(current == "heart" || current == "music") {
            NavIco("heart", Icons.Filled.Favorite, ctx.getString(R.string.favorites_plural))
            NavIco("music", Icons.Filled.LibraryMusic, ctx.getString(R.string.nav_music))
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
) {
    val acc = Color(accent)
    val bg = Palette.bg
    val card = Palette.card
    val text = Palette.text
    val muted = Palette.muted
    val logoFont = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

    var dropAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val listState = rememberLazyListState()
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
            val n = nowLocalRowsUi.size
            if (n == 0) { if (next) onNext() else onPrev(); return }
            val i0 = nowLocalRowsUi.indexOfFirst { it.uri == currentUrl }.let { if (it < 0) 0 else it }
            val i = if (next) (i0 + 1) % n else (i0 - 1 + n) % n
            onPickLocal(nowLocalRowsUi, i)
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
    var topThemeOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Права картка пошуку (свайп з правого краю / 🔍)
    val rightA = androidx.compose.runtime.remember { Animatable(1f) } // 1=закрито, 0=відкрито
    var rightShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var rightSearchOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    fun openRightSheet() {
        onRightOpen()
        rightShow = true
        rightSearchOpen = true
        sheetScope.launch {
            rightA.stop()
            rightA.snapTo(1f)
            rightA.animateTo(0f, tween(300))
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)
    ) {

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp)) {
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
                Text("Radio ", color = text, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold))
                Text("S O", color = acc, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold))
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                Box(
                    modifier = Modifier.size(40.dp).background(card, AppShapes.chip).springPress(0.9f) { onMenu() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.MoreVert, contentDescription = LocalContext.current.getString(R.string.more_settings), tint = text) }
            }
        }
        // Інфо-панель: іконки на всю висоту, пульс як Play, жанр+країна
        val infoArtist = artistFromTrackTitle(track)
        val infoPhoto by rememberArtistPhotoUrl(infoArtist, bust = currentUrl)
        val infoH = 100.dp
        val infoBusy = !playing && run {
            val stt = status.lowercase()
            stt.contains("підключ") || stt.contains("буфер") || stt == "запуск"
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
                    contentAlignment = Alignment.Center,
                ) {
                    if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                        AsyncImage(
                            model = artUrl(favicon),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted, modifier = Modifier.size(32.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        name.uppercase(),
                        color = acc,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (track.isNotBlank()) track else LocalContext.current.getString(R.string.track_unknown),
                        color = text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val ctry = country.trim().let { if (it.isNotBlank() && it != "-") it else "" }
                    val gen = genre.trim().let { if (it.isNotBlank() && it != "-") it else "" }
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
                if (!infoPhoto.isNullOrBlank()) {
                    AsyncImage(
                        model = infoPhoto,
                        contentDescription = infoArtist,
                        modifier = Modifier
                            .size(infoH)
                            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                            .background(Palette.panel2),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        if (bottomTab == "home") {
            // weight + fillMaxSize: список сам скролить, без боротьби з parent drag
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fillMaxSize()
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
            )
            LocalListSection(
                ui = libraryUi,
                listState = listState,
                actions = libraryActions,
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
            )
        }
        }
        // Рядок жанрових вкладок перенесено у праву панель (RightTabsPanel).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
                .background(card, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
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
                ) { _, drag ->
                    if (drag < 0 || sheetShow) {
                        sheetShow = true
                        sheetScope.launch { pullA.snapTo((pullA.value + drag).coerceIn(0f, 560f)) }
                    }
                }
            }
        ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(68.dp)
                .background(card, RoundedCornerShape(20.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canSkip) {
            Box(
                modifier = Modifier.fillMaxHeight().width(52.dp).background(card, RoundedCornerShape(20.dp)).clickable { skipUi(false) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = LocalContext.current.getString(R.string.prev_station),
                    tint = text,
                    modifier = Modifier.size(30.dp)
                )
            }
            }
            PlayBtn(playing = playing, status = status, sizeDp = 60.dp, onClick = onPlayPause, accent = acc, shape = RoundedCornerShape(14.dp))
            if (canSkip) {
            Box(
                modifier = Modifier.fillMaxHeight().width(52.dp).background(card, RoundedCornerShape(20.dp)).clickable { skipUi(true) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = LocalContext.current.getString(R.string.next_station),
                    tint = text,
                    modifier = Modifier.size(30.dp)
                )
            }
            }
            if (tabs.getOrNull(tabIndex) == "local") {
                Box(
                    modifier = Modifier.size(40.dp).background(Palette.panel.copy(alpha = 0.90f), RoundedCornerShape(12.dp)).clickable { onScan() },
                    contentAlignment = Alignment.Center
                ) { Text(LocalContext.current.getString(R.string.scan), color = acc, style = MaterialTheme.typography.labelSmall) }
            }
        }
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = LocalContext.current.getString(R.string.open_now_playing),
                tint = muted,
                modifier = Modifier.align(Alignment.CenterEnd).clickable { onNow() }.padding(4.dp).size(28.dp)
            )
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
    // ===== Права картка: жанрові та кастомні вкладки =====
    // Край поверх картки під час відкриття. Повністю відкриту — край вимкнено
    // (повторний свайп вліво більше не закриває).
    val rightFullyOpen = rightShow && rightA.value <= 0.05f
    val showRightEdge = !nowOpen && !sheetShow && !rightFullyOpen

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
        onSleepMenu = onSleepMenu,
        onSleep = onSleep,
        onExport = onExport,
        onImport = onImport,
        acc = acc,
        text = text,
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

