package com.seruiso.radio1

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs

/**
 * Велика карусель обкладинок now-playing.
 * Radio: 300dp; local: 248dp. Фото виконавця — лише на поточній сторінці.
 */
@Composable
fun NowPlayingPager(
    pagerState: PagerState,
    nowLocal: Boolean,
    pageKeys: List<String>,
    arts: List<String>,
    currentUrl: String,
    track: String,
    pageArtistFor: (page: Int) -> String,
    acc: Color,
    muted: Color,
) {
    val npArt = if (nowLocal) 248.dp else 300.dp
    val npPagerH = if (nowLocal) 262.dp else 318.dp
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = if (nowLocal) 48.dp else 40.dp),
        pageSpacing = if (nowLocal) 10.dp else 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(npPagerH),
        key = { page -> pageKeys.getOrNull(page) ?: "p$page" },
    ) { page ->
        val dist = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val abs = abs(dist).coerceIn(0f, 1f)
        val scale = 1f - 0.14f * abs
        val alpha = 1f - 0.38f * abs
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(npArt)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(acc.copy(alpha = 0.35f), Color.Transparent),
                            radius = 420f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(npArt)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val pageArtist = if (page != pagerState.currentPage) ""
                    else pageArtistFor(page)
                    val artistPhoto by rememberArtistPhotoUrl(
                        pageArtist,
                        (if (nowLocal) "L" else "R") + currentUrl + page
                    )
                    val photo = artistPhoto
                    val fallbackArt = arts.getOrNull(page) ?: ""
                    val shown = photo
                        ?: fallbackArt.takeIf { it.startsWith("http") || it.startsWith("content:") }
                        ?: ""
                    val zoom by animateFloatAsState(
                        targetValue = if (photo != null) 1f else 0.985f,
                        animationSpec = tween(420),
                        label = "artZoom",
                    )
                    val ctx = LocalContext.current
                    Crossfade(
                        targetState = shown,
                        animationSpec = tween(420),
                        label = "artXf",
                    ) { src ->
                        if (src.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(src)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(npArt)
                                    .graphicsLayer { scaleX = zoom; scaleY = zoom }
                                    .clip(AppShapes.card),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = muted,
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
