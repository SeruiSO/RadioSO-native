package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NowPlayingMeta(
    name: String,
    track: String,
    currentUrl: String,
    isFavorite: Boolean,
    isBest: Boolean,
    acc: Color,
    text: Color,
    muted: Color,
    pagerDragModifier: Modifier,
    onToggleFavorite: () -> Unit,
    onToggleBest: () -> Unit,
    onAddToTab: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .then(pagerDragModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = text,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val isLocalCard = currentUrl.startsWith("content:")
        if (isLocalCard) {
            Icon(
                if (isBest) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isBest) LocalContext.current.getString(R.string.remove_from_local_fav)
                else LocalContext.current.getString(R.string.add_to_local_fav),
                tint = acc,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .springPress(0.75f, onToggleBest)
            )
        } else {
            // Іконка 28dp як раніше; зона тапу більша + відступ від краю/між кнопками
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .springPress(0.75f, onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) LocalContext.current.getString(R.string.remove_from_favorites)
                    else LocalContext.current.getString(R.string.add_to_favorites),
                    tint = acc,
                    modifier = Modifier.size(28.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, end = 10.dp)
                    .size(44.dp)
                    .springPress(0.75f, onAddToTab),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    color = acc,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
    // виконавець + трек одразу під назвою станції (мінімальні відступи)
    val raw = track.trim()
    val (artistLine, titleLine) = run {
        if (raw.isBlank()) {
            "" to LocalContext.current.getString(R.string.track_unknown2)
        } else {
            var a = ""
            var t = raw
            for (sep in listOf(" - ", " – ", " — ", " | ")) {
                val i = raw.indexOf(sep)
                if (i > 0) {
                    a = raw.substring(0, i).trim()
                    t = raw.substring(i + sep.length).trim()
                    break
                }
            }
            a to t.ifBlank { raw }
        }
    }
    if (artistLine.isNotBlank()) {
        Text(
            artistLine,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 0.dp)
                .then(pagerDragModifier)
        )
    }
    Text(
        titleLine,
        color = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 4.dp)
            .then(pagerDragModifier)
    )
}

@Composable
fun NowPlayingLocalProgress(
    posMs: Long,
    durMs: Long,
    acc: Color,
    muted: Color,
    text: Color,
    onSeek: (Long) -> Unit,
    onShuffle: (() -> Unit)?,
    onRepeat: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        MiniProgressBar(
            posMs, durMs, acc, muted, onSeek,
            onShuffle = onShuffle,
            onRepeat = onRepeat,
            controlsTint = text,
        )
    }
}

@Composable
fun NowPlayingTransport(
    canSkip: Boolean,
    playing: Boolean,
    status: String,
    acc: Color,
    text: Color,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
    ) {
        if (canSkip) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Palette.panel, RoundedCornerShape(16.dp))
                    .springPress { onPrev() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = LocalContext.current.getString(R.string.prev_station),
                    tint = text,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        PlayBtn(
            playing = playing,
            status = status,
            sizeDp = 80.dp,
            onClick = onPlayPause,
            accent = acc
        )
        if (canSkip) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Palette.panel, RoundedCornerShape(16.dp))
                    .springPress { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = LocalContext.current.getString(R.string.next_station),
                    tint = text,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniProgressBar(
    posMs: Long,
    durMs: Long,
    accent: Color,
    muted: Color,
    onSeek: (Long) -> Unit,
    onShuffle: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    controlsTint: Color = muted,
) {
    val d = if (durMs > 0) durMs else 1L
    var slide by remember { mutableStateOf(-1f) }
    val frac = (if (slide >= 0f) slide else posMs.toFloat() / d).coerceIn(0f, 1f)
    fun fmt(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onShuffle != null) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = LocalContext.current.getString(R.string.shuffle),
                tint = controlsTint,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
                    .clickable { onShuffle() }
            )
        }
        Text(
            fmt(posMs),
            color = muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 6.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .pointerInput(d) {
                    detectTapGestures { off ->
                        if (d > 0L) {
                            val f = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeek((d * f).toLong())
                        }
                    }
                }
                .pointerInput(d) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (durMs > 0 && slide >= 0f) onSeek((slide * durMs).toLong())
                            slide = -1f
                        },
                        onDragCancel = { slide = -1f },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            slide = change.position.x.coerceIn(0f, w) / w
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(muted.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(8.dp)
                    .background(accent, RoundedCornerShape(4.dp))
            )
        }
        Text(
            fmt(durMs),
            color = muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp)
        )
        if (onRepeat != null) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = LocalContext.current.getString(R.string.repeat),
                tint = controlsTint,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp)
                    .clickable { onRepeat() }
            )
        }
    }
}
