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
    val edgeHold = remember { floatArrayOf(0f) }
    val dragStartIndex = remember { intArrayOf(-1) }
    val dragAccPx = remember { floatArrayOf(0f) }

    LaunchedEffect(dragging, dropAt, localRows.size) {
        if (!dragging || dropAt < 0 || localRows.isEmpty()) return@LaunchedEffect
        while (isActive && dragging) {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: break
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: break
            val speed = 36f
            when {
                dropAt <= first + 1 && listState.canScrollBackward -> {
                    listState.dispatchRawDelta(-speed)
                    edgeHold[0] -= speed
                }
                dropAt >= last - 1 && listState.canScrollForward -> {
                    listState.dispatchRawDelta(speed)
                    edgeHold[0] += speed
                }
            }
            if (dragStartIndex[0] >= 0) {
                val dest = (dragStartIndex[0] + ((dragAccPx[0] + edgeHold[0]) / 168f).toInt())
                    .coerceIn(0, localRows.lastIndex)
                if (dest != dropAt) actions.onDropAt(dest)
            }
            delay(16)
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

    LazyColumn(modifier = Modifier.weight(1f), state = listState, userScrollEnabled = !dragging) {
        itemsIndexed(localRows, key = { _, x -> x.uri }) { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .background(when { dropAt == index -> acc.copy(alpha = 0.40f); item.uri == currentUrl -> acc.copy(alpha = 0.18f); else -> card }, RoundedCornerShape(12.dp))
                    .pointerInput(item.uri, index, tabs.getOrNull(tabIndex)) {
                        if (tabs.getOrNull(tabIndex) != "best") return@pointerInput
                        var accDrag = 0f
                        var localDrop = index
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accDrag = 0f
                                localDrop = index
                                dragStartIndex[0] = index
                                dragAccPx[0] = 0f
                                edgeHold[0] = 0f
                                actions.onDropAt(index)
                                actions.onDragging(true)
                                actions.onDragStart()
                            },
                            onDragEnd = {
                                val dest = (dragStartIndex[0] + ((dragAccPx[0] + edgeHold[0]) / 168f).toInt())
                                    .coerceIn(0, localRows.lastIndex)
                                if (dest != index) actions.onMoveLocalTo(index, dest)
                                accDrag = 0f
                                dragAccPx[0] = 0f
                                edgeHold[0] = 0f
                                dragStartIndex[0] = -1
                                actions.onDropAt(-1)
                                actions.onDragging(false)
                            },
                            onDragCancel = {
                                accDrag = 0f
                                dragAccPx[0] = 0f
                                edgeHold[0] = 0f
                                dragStartIndex[0] = -1
                                actions.onDropAt(-1)
                                actions.onDragging(false)
                            }
                        ) { _, drag ->
                            accDrag += drag.y
                            dragAccPx[0] = accDrag
                            localDrop = (index + ((accDrag + edgeHold[0]) / 168f).toInt())
                                .coerceIn(0, localRows.lastIndex)
                            actions.onDropAt(localDrop)
                        }
                    }
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
