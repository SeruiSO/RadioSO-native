package com.seruiso.radio1

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

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
    val view = LocalView.current
    var current by remember { mutableStateOf<MusicTvChannel?>(null) }

    DisposableEffect(open, current?.url) {
        val keep = open && current != null
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = false }
    }
    var fullscreen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<String?>(null) } // null = home (countries + favs)
    var favs by remember { mutableStateOf(MusicTvFavStore.load(ctx)) }

    // Окремий плеєр ТВ (не RadioWatchService). При lock екрана — грає далі;
    // stop/release лише коли закрили sheet або скинули канал.
    val player = remember {
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 2_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000,
            )
            .build()
        ExoPlayer.Builder(ctx)
            .setLoadControl(load)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus= */ true,
                )
            }
    }

    var retryLeft by remember { mutableStateOf(3) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (current != null) status = "…"
                    }
                    Player.STATE_READY -> {
                        retryLeft = 3
                        status = current?.name.orEmpty()
                    }
                    Player.STATE_ENDED -> { /* live rarely ends */ }
                    else -> Unit
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (current == null) return
                if (retryLeft > 0) {
                    retryLeft -= 1
                    status = "…"
                    try {
                        player.prepare()
                        player.play()
                    } catch (_: Exception) {}
                } else {
                    status = error.localizedMessage ?: "Error"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            try { player.stop() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(current?.url) {
        val u = current?.url
        if (u.isNullOrBlank()) {
            try { player.stop() } catch (_: Exception) {}
            return@LaunchedEffect
        }
        try {
            onPauseRadio()
            retryLeft = 3
            status = "…"
            player.setMediaItem(MediaItem.fromUri(u))
            player.prepare()
            player.playWhenReady = true
            player.play()
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
            country != null -> country = null
            else -> onClose()
        }
    }

    if (fullscreen && current != null) {
        Box(Modifier.fillMaxSize().background(Color.Black).zIndex(90f)) {
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
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Filled.FullscreenExit, null, tint = Color.White)
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(card)
            .statusBarsPadding()
            .zIndex(70f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    when {
                        current != null -> {
                            try { player.stop() } catch (_: Exception) {}
                            current = null
                        }
                        country != null -> country = null
                        else -> onClose()
                    }
                }) {
                    Icon(
                        if (country != null || current != null) Icons.Filled.ArrowBack else Icons.Filled.Close,
                        contentDescription = "Back",
                        tint = text,
                    )
                }
                Text(
                    when {
                        current != null -> current!!.name
                        country != null -> MusicTvChannels.sections.firstOrNull { it.first == country }?.second ?: country!!
                        else -> "Music TV"
                    },
                    color = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // player strip
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
                        null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(26.dp),
                    )
                }
                Text(
                    status,
                    color = muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            val list: List<MusicTvChannel> = when {
                country == "FAV" -> MusicTvChannels.all.filter { it.url in favs }
                country != null -> MusicTvChannels.bySection(country!!)
                else -> emptyList()
            }

            if (country == null) {
                // Favorites row
                val favList = MusicTvChannels.all.filter { it.url in favs }
                if (favList.isNotEmpty()) {
                    Text(
                        "Улюблені",
                        color = acc,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        favList.forEach { ch ->
                            Column(
                                Modifier
                                    .width(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(acc.copy(alpha = 0.12f))
                                    .clickable { current = ch }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ChannelLogo(ch, 40.dp, acc, muted, card)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    ch.name,
                                    color = text,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Text(
                    "Країни",
                    color = muted,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                LazyColumn(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
                    item {
                        CountryRow("FAV", "Улюблені (${favs.size})", acc, muted, text, card) {
                            country = "FAV"
                        }
                    }
                    items(MusicTvChannels.sections, key = { it.first }) { (code, label) ->
                        val n = MusicTvChannels.byCountry(code).size
                        CountryRow(code, "$label · $n", acc, muted, text, card) {
                            country = code
                        }
                    }
                }
            } else {
                if (list.isEmpty()) {
                    Text(
                        "Порожньо",
                        color = muted,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(list, key = { it.url }) { ch ->
                            val selected = ch.url == current?.url
                            val isFav = ch.url in favs
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) acc.copy(alpha = 0.18f)
                                        else muted.copy(alpha = 0.08f)
                                    )
                                    .clickable { current = ch }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    ChannelLogo(ch, 56.dp, acc, muted, card)
                                    Icon(
                                        if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = "Fav",
                                        tint = if (isFav) acc else muted.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clickable {
                                                favs = MusicTvFavStore.toggle(ctx, ch.url)
                                            },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    ch.name,
                                    color = if (selected) acc else text,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    code: String,
    label: String,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(acc.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                code.take(2),
                color = acc,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChannelLogo(
    ch: MusicTvChannel,
    size: androidx.compose.ui.unit.Dp,
    acc: Color,
    muted: Color,
    card: Color,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(card.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!ch.logo.isNullOrBlank()) {
            AsyncImage(
                model = ch.logo,
                contentDescription = ch.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                ch.name.take(1).uppercase(),
                color = acc,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
