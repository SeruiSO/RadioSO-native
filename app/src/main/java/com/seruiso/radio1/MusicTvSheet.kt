package com.seruiso.radio1

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun MusicTvSheet(
    open: Boolean,
    onClose: () -> Unit,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
    onPauseRadio: () -> Unit,
) {
    if (!open) return
    val ctx = LocalContext.current
    var current by remember { mutableStateOf<MusicTvChannel?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { player.release() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(current?.url) {
        val u = current?.url ?: return@LaunchedEffect
        try {
            onPauseRadio()
            status = "…"
            player.setMediaItem(MediaItem.fromUri(u))
            player.prepare()
            player.play()
            status = current?.name.orEmpty()
        } catch (e: Exception) {
            status = e.message ?: "Error"
        }
    }

    BackHandler {
        when {
            fullscreen -> fullscreen = false
            current != null -> {
                try { player.stop() } catch (_: Exception) {}
                current = null
                status = ""
            }
            else -> onClose()
        }
    }

    if (fullscreen && current != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .zIndex(80f),
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = true
                        this.player = player
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { pv: PlayerView -> pv.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = { fullscreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit", tint = Color.White)
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onClose() }
            .zIndex(70f),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(card)
                .clickable(enabled = false) {},
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Music TV",
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = muted)
                }
            }

            if (current != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .aspectRatio(16f / 9f)
                        .clickable { fullscreen = true },
                ) {
                    AndroidView(
                        factory = { c ->
                            PlayerView(c).apply {
                                useController = false
                                this.player = player
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }
                        },
                        update = { pv: PlayerView -> pv.player = player },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(28.dp),
                    )
                }
                Text(
                    status.ifBlank { current?.name.orEmpty() },
                    color = muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .height(if (current != null) 280.dp else 420.dp)
                    .padding(bottom = 12.dp),
            ) {
                items(MusicTvChannels.all, key = { it.url }) { ch ->
                    val selected = ch.url == current?.url
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { current = ch }
                            .background(if (selected) acc.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = if (selected) acc else muted,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name,
                                color = if (selected) acc else text,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (ch.tag.isNotEmpty()) {
                                Text(
                                    ch.tag.uppercase(),
                                    color = muted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
