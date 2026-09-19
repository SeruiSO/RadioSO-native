package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Список радіо-станцій (+ appendix local favorites для heart/library).
 * Reorder long-press: dropAt/dragging з host (спільні з local-списком).
 */

/**
 * Колбеки списків radio + local (окремо від даних).
 * Один клас на обидва секції: local використовує підмножину.
 */
data class LibraryActions(
    val onDropAt: (Int) -> Unit,
    val onDragging: (Boolean) -> Unit,
    val onDragStart: () -> Unit,
    val onMoveTo: (Int, Int) -> Unit,
    val onMoveLocalTo: (Int, Int) -> Unit,
    val onPickRadio: (List<Station>, Int) -> Unit,
    val onPickLocal: (List<LocalTrack>, Int) -> Unit,
    val onNow: () -> Unit,
    val onToggleFav: (Station) -> Unit,
    val onAskDelete: (Station) -> Unit,
    val onAddToTab: (Station) -> Unit,
    val onMore: () -> Unit,
    val onToggleBest: (LocalTrack) -> Unit,
)

/**
 * Дані списків radio + local (без listState і без actions).
 * listState лишається окремим параметром — живий Compose scroll state.
 */
data class LibraryUi(
    val radioRows: List<Station>,
    val localRows: List<LocalTrack>,
    val bestRows: List<LocalTrack>,
    val dragging: Boolean,
    val dropAt: Int,
    val currentUrl: String,
    val tabs: List<String>,
    val tabIndex: Int,
    val bottomTab: String,
    val favUrls: Set<String>,
    val bestUris: Set<String>,
    val canMore: Boolean,
    val acc: Color,
    val muted: Color,
    val text: Color,
    val card: Color,
)


@Composable
fun androidx.compose.foundation.layout.ColumnScope.StationListSection(
    ui: LibraryUi,
    listState: LazyListState,
    actions: LibraryActions,
) {
    val radioRows = ui.radioRows
    val bestRows = ui.bestRows
    val dragging = ui.dragging
    val dropAt = ui.dropAt
    val currentUrl = ui.currentUrl
    val tabs = ui.tabs
    val tabIndex = ui.tabIndex
    val bottomTab = ui.bottomTab
    val favUrls = ui.favUrls
    val canMore = ui.canMore
    val acc = ui.acc
    val muted = ui.muted
    val text = ui.text
    val card = ui.card
    // Reorder: жест на рівні списку (не на рядку) — скрол не скасовує drag
    val dragFrom = remember { intArrayOf(-1) }
    val dropHold = remember { intArrayOf(-1) }
    val fingerY = remember { floatArrayOf(-1f) }

    fun radioIndexUnderY(y: Float): Int {
        if (radioRows.isEmpty()) return -1
        val info = listState.layoutInfo
        val yi = y.toInt()
        val hit = info.visibleItemsInfo.firstOrNull { yi >= it.offset && yi < it.offset + it.size }
            ?: info.visibleItemsInfo.minByOrNull {
                val c = it.offset + it.size / 2
                kotlin.math.abs(c - yi)
            }
        val idx = hit?.index ?: return -1
        // лише радіо-рядки (не empty/more/best appendix)
        return if (idx in radioRows.indices) idx else -1
    }

    // автоскрол краю, поки палець утримується (ключ лише dragging — без рестарту на dropAt)
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (isActive && dragging) {
            val y = fingerY[0]
            if (y >= 0f && radioRows.isNotEmpty()) {
                val info = listState.layoutInfo
                val h = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
                val edge = 96f
                val speed = 14f
                when {
                    y < edge && listState.canScrollBackward -> listState.dispatchRawDelta(-speed)
                    y > h - edge && listState.canScrollForward -> listState.dispatchRawDelta(speed)
                }
                val under = radioIndexUnderY(y)
                if (under >= 0 && under != dropHold[0]) {
                    dropHold[0] = under
                    actions.onDropAt(under)
                }
            }
            delay(32)
        }
    }

    // Поточна станція завжди в полі зору (скіп з керма / фон / зміна вкладки) (скіп з керма / фон / зміна вкладки)
    LaunchedEffect(currentUrl, radioRows, bestRows, bottomTab, canMore, dragging) {
        if (dragging) return@LaunchedEffect
        suspend fun scrollIfNeeded(index: Int) {
            if (index < 0) return
            val vis = listState.layoutInfo.visibleItemsInfo
            if (vis.any { it.index == index }) return
            listState.animateScrollToItem(index)
        }
        val radioIdx = radioRows.indexOfFirst { it.url == currentUrl }
        if (radioIdx >= 0) {
            // empty placeholder займає item 0 лише коли список порожній
            scrollIfNeeded(radioIdx)
            return@LaunchedEffect
        }
        if (bottomTab == "heart" || bottomTab == "library") {
            val bi = bestRows.indexOfFirst { it.uri == currentUrl }
            if (bi >= 0) {
                var base = if (radioRows.isEmpty()) 1 else radioRows.size // empty slot або рядки
                if (canMore) base += 1
                base += 1 // заголовок «локальні обрані»
                scrollIfNeeded(base + bi)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .pointerInput(radioRows.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val idx = radioIndexUnderY(offset.y)
                        if (idx < 0) return@detectDragGesturesAfterLongPress
                        dragFrom[0] = idx
                        dropHold[0] = idx
                        fingerY[0] = offset.y
                        actions.onDropAt(idx)
                        actions.onDragging(true)
                        actions.onDragStart()
                    },
                    onDragEnd = {
                        val from = dragFrom[0]
                        val to = dropHold[0]
                        if (from >= 0 && to >= 0 && from != to) actions.onMoveTo(from, to)
                        dragFrom[0] = -1
                        dropHold[0] = -1
                        fingerY[0] = -1f
                        actions.onDropAt(-1)
                        actions.onDragging(false)
                    },
                    onDragCancel = {
                        dragFrom[0] = -1
                        dropHold[0] = -1
                        fingerY[0] = -1f
                        actions.onDropAt(-1)
                        actions.onDragging(false)
                    },
                ) { change, _ ->
                    fingerY[0] = change.position.y
                    val under = radioIndexUnderY(change.position.y)
                    if (under >= 0) {
                        dropHold[0] = under
                        actions.onDropAt(under)
                    }
                    val info = listState.layoutInfo
                    val h = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
                    val edge = 96f
                    val speed = 14f
                    val y = change.position.y
                    when {
                        y < edge && listState.canScrollBackward -> listState.dispatchRawDelta(-speed)
                        y > h - edge && listState.canScrollForward -> listState.dispatchRawDelta(speed)
                    }
                }
            },
        state = listState,
        userScrollEnabled = !dragging,
    ) {
        if (radioRows.isEmpty()) {
            item {
                EmptySlot(
                    when {
                        tabs.getOrNull(tabIndex) == "search" || bottomTab == "search" -> LocalContext.current.getString(R.string.nothing_found)
                        tabs.getOrNull(tabIndex) == "fav" || bottomTab == "stations" -> LocalContext.current.getString(R.string.fav_hint_long)
                        else -> LocalContext.current.getString(R.string.empty_for_now)
                    },
                    muted
                )
            }
        }
        itemsIndexed(radioRows, key = { i, s -> s.tab + s.url + i }) { index, s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .background(when { dropAt == index -> acc.copy(alpha = 0.40f); s.url == currentUrl -> acc.copy(alpha = 0.18f); else -> card }, RoundedCornerShape(12.dp))
                    
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // іконка → відтворення + відкрити нижню картку
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            if (s.url != currentUrl) actions.onPickRadio(radioRows, index)
                            actions.onNow()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (s.favicon.startsWith("http") && !s.favicon.contains("example.com")) {
                        AsyncImage(model = s.favicon, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    } else Icon(Icons.Filled.MusicNote, contentDescription = LocalContext.current.getString(R.string.no_cover), tint = muted)
                }
                // рядок (назва) → лише відтворення, без нижньої картки
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                        .clickable { actions.onPickRadio(radioRows, index) }
                ) {
                    Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.genre} · ${s.country}", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    if (favUrls.contains(s.url)) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favUrls.contains(s.url)) LocalContext.current.getString(R.string.remove_from_favorites) else LocalContext.current.getString(R.string.add_to_favorites),
                    tint = acc,
                    modifier = Modifier.clickable { actions.onToggleFav(s) }.padding(start = 8.dp, end = 2.dp).size(24.dp)
                )
                if (tabs.getOrNull(tabIndex) == "search") {
                    Text("+", color = acc, modifier = Modifier.clickable { actions.onAddToTab(s) }.padding(start = 4.dp), style = MaterialTheme.typography.headlineMedium)
                } else if (tabs.getOrNull(tabIndex) != "fav") {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = LocalContext.current.getString(R.string.delete_station),
                        tint = muted,
                        modifier = Modifier.clickable { actions.onAskDelete(s) }.padding(start = 8.dp, end = 0.dp).size(22.dp)
                    )
                }
            }
        }
        if (canMore) {
            item { Button(onClick = actions.onMore, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text(LocalContext.current.getString(R.string.more_100)) } }
        }
        // ===== heart/library: далі йдуть обрані локальні треки =====
        // Серце (heart): лише обрані локальні (best). Станції — на зірці (stations).
        if (bottomTab == "heart" || bottomTab == "library") {
            item {
                Text(
                    LocalContext.current.getString(R.string.local_favorites),
                    color = muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            if (bestRows.isEmpty()) {
                item {
                    EmptySlot(LocalContext.current.getString(R.string.local_hint_long), muted)
                }
            }
            itemsIndexed(bestRows, key = { i, x -> "best-" + x.uri + i }) { _, item ->
                LocalTrackRow(
                    item,
                    item.uri == currentUrl,
                    acc,
                    muted,
                    text,
                    isBest = true,
                    onToggleBest = { actions.onToggleBest(item) },
                    onArt = {
                        val i = bestRows.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                        if (item.uri != currentUrl) actions.onPickLocal(bestRows, i)
                        actions.onNow()
                    },
                ) {
                    actions.onPickLocal(bestRows, bestRows.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0))
                }
            }
        }
    }
}
