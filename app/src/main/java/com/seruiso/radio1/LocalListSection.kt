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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Список локальних треків (вкладки local / best).
 * Reorder long-press лише на tab "best"; localDrop у жесті (не stale dropAt).
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.LocalListSection(
    ui: LibraryUi,
    listState: LazyListState,
    actions: LibraryActions,
) {
    val localRows = ui.localRows
    val dragging = ui.dragging
    val dropAt = ui.dropAt
    val currentUrl = ui.currentUrl
    val tabs = ui.tabs
    val tabIndex = ui.tabIndex
    val bestUris = ui.bestUris
    val acc = ui.acc
    val muted = ui.muted
    val text = ui.text
    val card = ui.card
    // Reorder на рівні списку
    val dragFrom = remember { intArrayOf(-1) }
    val dropHold = remember { intArrayOf(-1) }
    val fingerY = remember { floatArrayOf(-1f) }

    fun localIndexUnderY(y: Float): Int {
        if (localRows.isEmpty()) return -1
        val info = listState.layoutInfo
        val yi = y.toInt()
        val hit = info.visibleItemsInfo.firstOrNull { yi >= it.offset && yi < it.offset + it.size }
            ?: info.visibleItemsInfo.minByOrNull {
                val c = it.offset + it.size / 2
                kotlin.math.abs(c - yi)
            }
        val idx = hit?.index ?: return -1
        return if (idx in localRows.indices) idx else -1
    }

    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (isActive && dragging) {
            val y = fingerY[0]
            if (y >= 0f && localRows.isNotEmpty()) {
                val info = listState.layoutInfo
                val h = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
                val edge = 96f
                val speed = 14f
                when {
                    y < edge && listState.canScrollBackward -> listState.dispatchRawDelta(-speed)
                    y > h - edge && listState.canScrollForward -> listState.dispatchRawDelta(speed)
                }
                val under = localIndexUnderY(y)
                if (under >= 0 && under != dropHold[0]) {
                    dropHold[0] = under
                    actions.onDropAt(under)
                }
            }
            delay(32)
        }
    }

    if (localRows.isEmpty()) {
        EmptySlot(LocalContext.current.getString(R.string.no_tracks_scan), muted)
    }
    LaunchedEffect(currentUrl, localRows, dragging) {
        if (dragging) return@LaunchedEffect
        val i = localRows.indexOfFirst { it.uri == currentUrl }
        if (i < 0) return@LaunchedEffect
        val vis = listState.layoutInfo.visibleItemsInfo
        if (vis.any { it.index == i }) return@LaunchedEffect
        listState.animateScrollToItem(i)
    }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .pointerInput(localRows.size, tabs.getOrNull(tabIndex)) {
                // reorder лише на вкладці best
                if (tabs.getOrNull(tabIndex) != "best") return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val idx = localIndexUnderY(offset.y)
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
                        if (from >= 0 && to >= 0 && from != to) actions.onMoveLocalTo(from, to)
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
                    val under = localIndexUnderY(change.position.y)
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
        itemsIndexed(localRows, key = { _, x -> x.uri }) { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .background(when { dropAt == index -> acc.copy(alpha = 0.40f); item.uri == currentUrl -> acc.copy(alpha = 0.18f); else -> card }, RoundedCornerShape(12.dp))
                    
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            if (item.uri != currentUrl) actions.onPickLocal(localRows, index)
                            actions.onNow()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val a = if (item.albumId.isNotBlank() && item.albumId != "0")
                        "content://media/external/audio/albumart/${item.albumId}" else ""
                    if (a.isNotEmpty()) AsyncImage(model = a, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.MusicNote, contentDescription = LocalContext.current.getString(R.string.no_cover), tint = muted)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .clickable { actions.onPickLocal(localRows, index) }
                ) {
                    Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    if (bestUris.contains(item.uri)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (bestUris.contains(item.uri)) LocalContext.current.getString(R.string.remove_from_local_fav) else LocalContext.current.getString(R.string.add_to_local_fav_short),
                    tint = acc,
                    modifier = Modifier.clickable { actions.onToggleBest(item) }.padding(start = 10.dp, end = 2.dp).size(24.dp)
                )
            }
        }
    }
}
