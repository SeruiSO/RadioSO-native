package com.seruiso.radio1

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Нижня стрічка now-playing: ~5 іконок + підпис.
 * Radio: більші клітинки; local: компактніше під прогрес.
 */
@Composable
fun NowPlayingStrip(
    arts: List<String>,
    labels: List<String>,
    curI: Int,
    nowLocal: Boolean,
    stripState: LazyListState,
    muted: Color,
    text: Color,
    onPick: (Int) -> Unit,
) {
    val stripH = if (nowLocal) 86.dp else 108.dp
    val stripCell = if (nowLocal) 56.dp else 70.dp
    val stripSel = if (nowLocal) 52.dp else 68.dp
    val stripUnsel = if (nowLocal) 46.dp else 62.dp
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(stripH),
        contentAlignment = Alignment.Center
    ) {
        if (arts.isEmpty()) return@BoxWithConstraints
        val cell = stripCell
        val hPad = ((maxWidth - cell) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = stripState,
            modifier = Modifier.fillMaxWidth().height(stripH),
            contentPadding = PaddingValues(horizontal = hPad),
            horizontalArrangement = Arrangement.spacedBy(if (nowLocal) 4.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(arts, key = { i, u -> "$i:$u" }) { i, u ->
                val label = labels.getOrNull(i) ?: ""
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(cell)
                        .clickable { onPick(i) }
                ) {
                    Box(modifier = Modifier.size(cell), contentAlignment = Alignment.Center) {
                        val target = if (i == curI) stripSel else stripUnsel
                        val sz by animateDpAsState(target, label = "stripSz")
                        val alpha by animateFloatAsState(if (i == curI) 1f else 0.72f, label = "stripA")
                        if (u.startsWith("http") || u.startsWith("content:")) {
                            AsyncImage(
                                model = u,
                                contentDescription = label.ifBlank { null },
                                modifier = Modifier
                                    .size(sz)
                                    .graphicsLayer { this.alpha = alpha }
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = label.ifBlank { null },
                                tint = muted,
                                modifier = Modifier.size(sz * 0.55f).graphicsLayer { this.alpha = alpha }
                            )
                        }
                    }
                    Text(
                        label,
                        color = if (i == curI) text else muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp).fillMaxWidth()
                    )
                }
            }
        }
    }
}
