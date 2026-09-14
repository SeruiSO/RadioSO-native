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
@Composable
fun androidx.compose.foundation.layout.ColumnScope.StationListSection(
    radioRows: List<Station>,
    listState: LazyListState,
    dragging: Boolean,
    dropAt: Int,
    onDropAt: (Int) -> Unit,
    onDragging: (Boolean) -> Unit,
    currentUrl: String,
    tabs: List<String>,
    tabIndex: Int,
    bottomTab: String,
    favUrls: Set<String>,
    canMore: Boolean,
    bestRows: List<LocalTrack>,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
    onDragStart: () -> Unit,
    onMoveTo: (Int, Int) -> Unit,
    onPickRadio: (List<Station>, Int) -> Unit,
    onNow: () -> Unit,
    onToggleFav: (Station) -> Unit,
    onAskDelete: (Station) -> Unit,
    onAddToTab: (Station) -> Unit,
    onMore: () -> Unit,
    onPickLocal: (List<LocalTrack>, Int) -> Unit,
    onToggleBest: (LocalTrack) -> Unit,
) {
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
                        detectDragGesturesAfterLongPress(
                            onDragStart = { accDrag = 0f; onDropAt(index); onDragging(true); onDragStart() },
                            onDragEnd = {
                                val dest = dropAt.coerceIn(0, radioRows.lastIndex)
                                if (dest != index) onMoveTo(index, dest)
                                accDrag = 0f
                                onDropAt(-1)
                                onDragging(false)
                            },
                            onDragCancel = { accDrag = 0f; onDropAt(-1); onDragging(false) }
                        ) { _, drag ->
                            accDrag += drag.y
                            onDropAt((index + (accDrag / 168f).toInt()).coerceIn(0, radioRows.lastIndex))
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
                            if (s.url != currentUrl) onPickRadio(radioRows, index)
                            onNow()
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
                        .clickable { onPickRadio(radioRows, index) }
                ) {
                    Text(s.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.genre} · ${s.country}", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                if (tabs.getOrNull(tabIndex) == "search") {
                    Text("+", color = acc, modifier = Modifier.clickable { onAddToTab(s) }.padding(start = 8.dp), style = MaterialTheme.typography.headlineMedium)
                } else {
                    Icon(
                        if (favUrls.contains(s.url)) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favUrls.contains(s.url)) LocalContext.current.getString(R.string.remove_from_favorites) else LocalContext.current.getString(R.string.add_to_favorites),
                        tint = acc,
                        modifier = Modifier.clickable { onToggleFav(s) }.padding(start = 8.dp, end = 2.dp).size(24.dp)
                    )
                    if (tabs.getOrNull(tabIndex) != "fav") {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = LocalContext.current.getString(R.string.delete_station),
                            tint = muted,
                            modifier = Modifier.clickable { onAskDelete(s) }.padding(start = 8.dp, end = 0.dp).size(22.dp)
                        )
                    }
                }
            }
        }
        if (canMore) {
            item { Button(onClick = onMore, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text(LocalContext.current.getString(R.string.more_100)) } }
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
                    onToggleBest = { onToggleBest(item) },
                    onArt = {
                        val i = bestRows.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                        if (item.uri != currentUrl) onPickLocal(bestRows, i)
                        onNow()
                    },
                ) {
                    onPickLocal(bestRows, bestRows.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0))
                }
            }
        }
    }
}
