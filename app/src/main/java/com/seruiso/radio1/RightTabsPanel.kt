package com.seruiso.radio1

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Права шторка жанрових/кастомних вкладок + edge-swipe.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.RightTabsPanel(
    rightA: Animatable<Float, *>,
    rightShow: Boolean,
    onRightShow: (Boolean) -> Unit,
    showRightEdge: Boolean,
    onRightOpen: () -> Unit,
    tabs: List<String>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    onLongTab: (String) -> Unit,
    acc: Color, muted: Color, text: Color, card: Color,
) {
    val sheetScope = rememberCoroutineScope()
    fun closeRightSheet() {
        sheetScope.launch {
            rightA.stop()
            rightA.animateTo(1f, tween(280))
            onRightShow(false)
        }
    }
    if (rightShow || rightA.value < 0.999f) {
        val cfg = LocalConfiguration.current
        val density = LocalDensity.current
        val sheetW = (cfg.screenWidthDp * 0.58f).dp
        val sheetWpx = with(density) { sheetW.toPx() }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0x88000000).copy(
                            alpha = ((1f - rightA.value) * 0.5f).coerceIn(0f, 0.5f)
                        )
                    )
                    .clickable { closeRightSheet() }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(sheetW)
                    .graphicsLayer { translationX = rightA.value * sheetWpx }
                    .background(
                        Palette.card,
                        RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
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
                                if (!ch.pressed) {
                                    finished = true
                                    break
                                }
                                val dx = ch.positionChange().x
                                val dy = ch.positionChange().y
                                accX += dx
                                accY += dy
                                if (!locked) {
                                    if (kotlin.math.abs(accX) > slop || kotlin.math.abs(accY) > slop) {
                                        if (kotlin.math.abs(accX) > kotlin.math.abs(accY) && accX > 0f) locked = true
                                        else break
                                    }
                                }
                                if (locked) {
                                    ch.consume()
                                    val next = (rightA.value + dx / sheetWpx).coerceIn(0f, 1f)
                                    sheetScope.launch { rightA.snapTo(next) }
                                }
                            }
                            if (locked) {
                                sheetScope.launch {
                                    rightA.stop()
                                    if (rightA.value >= 0.07f) {
                                        rightA.animateTo(1f, tween(280))
                                        onRightShow(false)
                                    } else {
                                        rightA.animateTo(0f, tween(280))
                                        onRightShow(true)
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).background(muted, RoundedCornerShape(2.dp)))
                }
                Text(
                    LocalContext.current.getString(R.string.tabs),
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    LocalContext.current.getString(R.string.tap_hold_hint),
                    color = muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                val genreTabs = tabs.withIndex().filter { it.value !in listOf("fav", "best", "local", "search") }
                if (genreTabs.isEmpty()) {
                    EmptySlot(LocalContext.current.getString(R.string.no_genre_tabs), muted)
                }
                // reverseLayout: перший item знизу — список росте вгору
                LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                    // «+» першим у коді → візуально внизу картки
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(acc.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .clickable { onAddTab() }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("+", color = acc, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 10.dp))
                            Text(LocalContext.current.getString(R.string.add_tab), color = acc, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    itemsIndexed(genreTabs, key = { _, iv -> "tab-" + iv.value }) { _, iv ->
                        val (i, tab) = iv
                        val selected = i == tabIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (selected) acc.copy(alpha = 0.20f) else Palette.panel,
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) acc else muted.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .combinedClickable(
                                    onClick = { onTab(i); closeRightSheet() },
                                    onLongClick = { onLongTab(tab) }
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Category,
                                contentDescription = null,
                                tint = if (selected) acc else muted,
                                modifier = Modifier.size(20.dp).padding(end = 2.dp)
                            )
                            Text(
                                tabLabel(LocalContext.current, tab),
                                color = if (selected) acc else text,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 10.dp)
                            )
                            if (selected) {
                                Text("●", color = acc, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
    if (showRightEdge) {
        val dens = LocalDensity.current
        val cfg = LocalConfiguration.current
        val sheetWpxEdge = with(dens) { (cfg.screenWidthDp * 0.58f).dp.toPx() }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(22.dp).pointerInput(sheetWpxEdge) {
                var dragged = false
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val was = dragged
                        dragged = false
                        if (!was) return@detectHorizontalDragGestures
                        sheetScope.launch {
                            rightA.stop()
                            if (rightA.value <= 0.93f) {
                                onRightShow(true)
                                rightA.animateTo(0f, tween(280)); rightA.snapTo(0f)
                            } else {
                                rightA.animateTo(1f, tween(260)); rightA.snapTo(1f); onRightShow(false)
                            }
                        }
                    },
                    onDragCancel = {
                        val was = dragged
                        dragged = false
                        if (!was) return@detectHorizontalDragGestures
                        sheetScope.launch {
                            rightA.stop()
                            if (rightA.value <= 0.93f) {
                                onRightShow(true)
                                rightA.animateTo(0f, tween(280)); rightA.snapTo(0f)
                            } else {
                                rightA.animateTo(1f, tween(240)); rightA.snapTo(1f); onRightShow(false)
                            }
                        }
                    }
                ) { _, drag ->
                    if (!dragged) {
                        if (drag >= -0.5f) return@detectHorizontalDragGestures
                        dragged = true
                        onRightShow(true); onRightOpen()
                    }
                    val next = (rightA.value + drag / sheetWpxEdge).coerceIn(0f, 1f)
                    sheetScope.launch { rightA.snapTo(next) }
                }
            }
        )
    }
}

