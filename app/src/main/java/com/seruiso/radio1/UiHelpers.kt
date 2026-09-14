package com.seruiso.radio1

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Album art / favicon URL (http or MediaStore albumId). */
fun artUrl(raw: String): String {
    if (raw.startsWith("http")) return raw
    if (raw.isNotBlank() && raw != "0" && raw.all { it.isDigit() }) {
        return "content://media/external/audio/albumart/$raw"
    }
    return raw
}

@Composable
fun PlayBtn(
    playing: Boolean,
    status: String,
    sizeDp: Dp,
    onClick: () -> Unit,
    accent: Color,
    shape: Shape = AppShapes.hero,
) {
    val st = status.lowercase()
    val busy = !playing && (
        st.contains("підключ") || st.contains(LocalContext.current.getString(R.string.buffer)) || st == "запуск"
    )
    val pulseOn = playing || busy
    val infinite = rememberInfiniteTransition(label = "playPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (busy) 1.09f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (busy) 420 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playPulseSc"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressSc by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "playPress"
    )
    val sc = (if (pulseOn) pulse else 1f) * pressSc
    Box(
        modifier = Modifier
            .size(sizeDp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .background(accent, shape)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(sizeDp * 0.38f),
                color = Color(0xFF0A0A0C),
                strokeWidth = 2.5.dp
            )
            playing -> Icon(
                Icons.Filled.Pause,
                contentDescription = LocalContext.current.getString(R.string.pause),
                tint = Color(0xFF0A0A0C),
                modifier = Modifier.size(sizeDp * 0.42f)
            )
            else -> Icon(
                Icons.Filled.PlayArrow,
                contentDescription = LocalContext.current.getString(R.string.play),
                tint = Color(0xFF0A0A0C),
                modifier = Modifier.size(sizeDp * 0.42f)
            )
        }
    }
}

/** Spring scale on press — prev/next, fav, etc. */
@Composable
fun Modifier.springPress(pressedScale: Float = 0.88f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "springPress"
    )
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
