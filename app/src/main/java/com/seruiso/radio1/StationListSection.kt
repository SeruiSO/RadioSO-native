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
    // edge auto-scroll: повільно, кроками індексу (без стрибка на початок)
    val edgeSteps = remember { intArrayOf(0) }
    val dragStartIndex = remember { intArrayOf(-1) }
    val dragAccPx = remember { floatArrayOf(0f) }

    LaunchedEffect(dragging, dropAt, radioRows.size) {
        if (!dragging || dropAt < 0 || radioRows.isEmpty()) return@LaunchedEffect
        while (isActive && dragging) {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: break
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: break
            val speed = 8f
            var stepped = false
            when {
                dropAt <= first && listState.canScrollBackward -> {
                    listState.dispatchRawDelta(-speed)
                    edgeSteps[0] -= 1
                    stepped = true
                }
                dropAt >= last && listState.canScrollForward -> {
                    listState.dispatchRawDelta(speed)
                    edgeSteps[0] += 1
                    stepped = true
                }
            }
            if (stepped && dragStartIndex[0] >= 0) {
                val dest = (dragStartIndex[0] + (dragAccPx[0] / 200f).toInt() + edgeSteps[0])
                    .coerceIn(0, radioRows.lastIndex)
                if (dest != dropAt) actions.onDropAt(dest)
            }
            delay(48)
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

    LazyColumn(modifier = Modifier.weight(1f), state = listState, userScrollEnabled = !dragging) {
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
                    .pointerInput(s.url, index) {
                        var accDrag = 0f
                        var localDrop = index
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accDrag = 0f
                                localDrop = index
                                dragStartIndex[0] = index
                                dragAccPx[0] = 0f
                                edgeSteps[0] = 0
                                actions.onDropAt(index)
                                actions.onDragging(true)
                                actions.onDragStart()
                            },
                            onDragEnd = {
                                val dest = (dragStartIndex[0] + (dragAccPx[0] / 200f).toInt() + edgeSteps[0])
                                    .coerceIn(0, radioRows.lastIndex)
                                if (dest != index) actions.onMoveTo(index, dest)
                                accDrag = 0f
                                dragAccPx[0] = 0f
                                edgeSteps[0] = 0
                                dragStartIndex[0] = -1
                                actions.onDropAt(-1)
                                actions.onDragging(false)
                            },
                            onDragCancel = {
                                accDrag = 0f
                                dragAccPx[0] = 0f
                                edgeSteps[0] = 0
                                dragStartIndex[0] = -1
                                actions.onDropAt(-1)
                                actions.onDragging(false)
                            }
                        ) { _, drag ->
                            accDrag += drag.y
                            dragAccPx[0] = accDrag
                            localDrop = (index + (accDrag / 200f).toInt() + edgeSteps[0])
                                .coerceIn(0, radioRows.lastIndex)
                            actions.onDropAt(localDrop)
                        }
                    }
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
