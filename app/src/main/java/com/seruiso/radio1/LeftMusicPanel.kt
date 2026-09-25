package com.seruiso.radio1

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.LeftMusicPanel(
    leftA: Animatable<Float, *>,
    leftShow: Boolean,
    onLeftShow: (Boolean) -> Unit,
    showLeftEdge: Boolean,
    tracks: List<LocalTrack>,
    currentUrl: String,
    bestUris: Set<String>,
    playing: Boolean,
    posMs: Long,
    durMs: Long,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
    onOpenTrack: (Int) -> Unit,
    onStep: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleBest: (LocalTrack) -> Unit,
) {
    val sheetScope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun closeLeftSheet() {
        sheetScope.launch {
            leftA.stop()
            leftA.animateTo(1f, tween(280))
            onLeftShow(false)
        }
    }
    if (leftShow || leftA.value < 0.999f) {
        val cfg = LocalConfiguration.current
        val density = LocalDensity.current
        val sheetW = (cfg.screenWidthDp * 0.58f).dp
        val sheetWpx = with(density) { sheetW.toPx() }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000).copy(alpha = ((1f - leftA.value) * 0.5f).coerceIn(0f, 0.5f)))
                    .clickable { closeLeftSheet() }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(sheetW)
                    .graphicsLayer { translationX = -leftA.value * sheetWpx }
                    .background(card, RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(sheetWpx) {
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var locked = false
                                var accX = 0f
                                var accY = 0f
                                var finished = false
                                while (!finished) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) { finished = true; break }
                                    val dx = ch.positionChange().x
                                    val dy = ch.positionChange().y
                                    accX += dx; accY += dy
                                    if (!locked) {
                                        if (kotlin.math.abs(accX) > slop || kotlin.math.abs(accY) > slop) {
                                            if (kotlin.math.abs(accX) > kotlin.math.abs(accY) && accX < 0f) locked = true
                                            else break
                                        }
                                    }
                                    if (locked) {
                                        ch.consume()
                                        val next = (leftA.value - dx / sheetWpx).coerceIn(0f, 1f)
                                        sheetScope.launch { leftA.snapTo(next) }
                                    }
                                }
                                if (locked) {
                                    sheetScope.launch {
                                        leftA.stop()
                                        if (leftA.value >= 0.07f) {
                                            leftA.animateTo(1f, tween(280)); onLeftShow(false)
                                        } else {
                                            leftA.animateTo(0f, tween(280)); onLeftShow(true)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Text(ctx.getString(R.string.nav_music), color = text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        ctx.getString(R.string.tracks_count, tracks.size),
                        color = muted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(tracks, key = { i, x -> "lm-" + x.uri + i }) { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        if (item.uri == currentUrl) acc.copy(alpha = 0.18f) else Color.Transparent,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { onOpenTrack(index) }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val art = if (item.albumId.isNotBlank() && item.albumId != "0")
                                    "content://media/external/audio/albumart/${item.albumId}" else ""
                                if (art.isNotEmpty()) {
                                    AsyncImage(model = art, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted, modifier = Modifier.size(42.dp))
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(
                                    if (bestUris.contains(item.uri)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = acc,
                                    modifier = Modifier.clickable { onToggleBest(item) }.padding(start = 6.dp).size(28.dp),
                                )
                            }
                        }
                    }
                }
                val localNow = currentUrl.startsWith("content:")
                if (localNow && durMs > 0) {
                    var slide by androidx.compose.runtime.remember { mutableStateOf(-1f) }
                    Slider(
                        value = if (slide >= 0f) slide else (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { slide = it },
                        onValueChangeFinished = {
                            if (slide >= 0f) onSeek((slide * durMs).toLong())
                            slide = -1f
                        },
                    )
                    fun fmt(ms: Long): String {
                        val s = (ms / 1000).coerceAtLeast(0)
                        return "%d:%02d".format(s / 60, s % 60)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(posMs), color = muted, style = MaterialTheme.typography.labelSmall)
                        Text(fmt(durMs), color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val pulseAnim = rememberInfiniteTransition(label = "leftArt")
                    val pulse by pulseAnim.animateFloat(
                        1f,
                        if (playing && localNow) 1.08f else 1f,
                        infiniteRepeatable(tween(if (playing && localNow) 900 else 1, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "leftArtSc",
                    )
                    val cur = tracks.firstOrNull { it.uri == currentUrl }
                    val art = if (cur != null && cur.albumId.isNotBlank() && cur.albumId != "0")
                        "content://media/external/audio/albumart/${cur.albumId}" else ""
                    Box(
                        modifier = Modifier.size(48.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(RoundedCornerShape(12.dp)).background(Palette.panel2),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (art.isNotEmpty()) AsyncImage(model = art, contentDescription = null, modifier = Modifier.size(48.dp), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted)
                    }
                    Box(modifier = Modifier.size(48.dp).background(Palette.panel2, RoundedCornerShape(12.dp)).clickable { onStep(false) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = ctx.getString(R.string.prev_station), tint = text)
                    }
                    Box(modifier = Modifier.size(56.dp).background(acc, RoundedCornerShape(14.dp)).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF0A0A0C))
                    }
                    Box(modifier = Modifier.size(48.dp).background(Palette.panel2, RoundedCornerShape(12.dp)).clickable { onStep(true) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SkipNext, contentDescription = ctx.getString(R.string.next_station), tint = text)
                    }
                }
            }
        }
    }
    if (showLeftEdge) {
        val dens = LocalDensity.current
        val cfg = LocalConfiguration.current
        val sheetWpxEdge = with(dens) { (cfg.screenWidthDp * 0.58f).dp.toPx() }
        Box(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(22.dp).pointerInput(sheetWpxEdge) {
                var dragged = false
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val was = dragged
                        dragged = false
                        if (!was) return@detectHorizontalDragGestures
                        sheetScope.launch {
                            leftA.stop()
                            if (leftA.value <= 0.93f) {
                                onLeftShow(true); leftA.animateTo(0f, tween(280)); leftA.snapTo(0f)
                            } else {
                                leftA.animateTo(1f, tween(260)); leftA.snapTo(1f); onLeftShow(false)
                            }
                        }
                    },
                    onDragCancel = {
                        val was = dragged
                        dragged = false
                        if (!was) return@detectHorizontalDragGestures
                        sheetScope.launch {
                            leftA.stop()
                            if (leftA.value <= 0.93f) {
                                onLeftShow(true); leftA.animateTo(0f, tween(280)); leftA.snapTo(0f)
                            } else {
                                leftA.animateTo(1f, tween(240)); leftA.snapTo(1f); onLeftShow(false)
                            }
                        }
                    },
                ) { _, drag ->
                    if (!dragged) {
                        if (drag <= 0.5f) return@detectHorizontalDragGestures
                        dragged = true
                        onLeftShow(true)
                    }
                    val next = (leftA.value - drag / sheetWpxEdge).coerceIn(0f, 1f)
                    sheetScope.launch { leftA.snapTo(next) }
                }
            }
        )
    }
}
