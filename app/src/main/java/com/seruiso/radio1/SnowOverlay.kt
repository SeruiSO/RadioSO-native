package com.seruiso.radio1

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

private data class Flake(
    var x: Float,
    var y: Float,
    val r: Float,
    val speed: Float,
    val drift: Float,
    val phase: Float,
    var settled: Boolean = false,
    var alpha: Float = 0.85f,
)

/**
 * Сніг поверх UI. Не чіпає playback.
 * active=true → 10–12 с падає; тап → onDismiss.
 * settleY — верх панелі вкладок; playCenterY/playR — зона Play; wing half-width якщо крила відкриті.
 */
@Composable
fun SnowOverlay(
    active: Boolean,
    onDismiss: () -> Unit,
    wingsOpen: Boolean,
    playDp: Float,
    playLiftDp: Float,
    wingGapDp: Float,
) {
    if (!active) return
    val density = LocalDensity.current
    var flakes by remember { mutableStateOf(emptyList<Flake>()) }
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        started = false
        flakes = emptyList()
        // авто-стоп ~11 с
        delay(11_000)
        onDismiss()
    }

    LaunchedEffect(active, w, h) {
        if (!active || w <= 0f || h <= 0f) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        val playPx = with(density) { playDp.dp.toPx() }
        val liftPx = with(density) { playLiftDp.dp.toPx() }
        val gapPx = with(density) { wingGapDp.dp.toPx() }
        val dockTop = h - with(density) { 56.dp.toPx() }
        val playCy = dockTop - liftPx + playPx / 2f
        val playCx = w / 2f
        val playR = playPx / 2f + with(density) { 4.dp.toPx() }
        val wingR = playR
        val wingOff = playPx / 2f + playPx / 2f + gapPx

        if (!started) {
            flakes = List(70) {
                Flake(
                    x = rnd.nextFloat() * w,
                    y = -rnd.nextFloat() * h * 0.4f,
                    r = with(density) { (1.5f + rnd.nextFloat() * 2.8f).dp.toPx() },
                    speed = with(density) { (28f + rnd.nextFloat() * 55f).dp.toPx() / 60f },
                    drift = (rnd.nextFloat() - 0.5f) * 0.6f,
                    phase = rnd.nextFloat() * 6.28f,
                )
            }
            started = true
        }

        var t = 0f
        while (isActive && active) {
            t += 0.016f
            flakes = flakes.map { f ->
                if (f.settled) return@map f
                var nx = f.x + f.drift + sin(t * 1.7f + f.phase) * 0.35f
                var ny = f.y + f.speed
                if (nx < -10f) nx = w + 10f
                if (nx > w + 10f) nx = -10f

                // купа на панелі вкладок
                if (ny >= dockTop - f.r) {
                    return@map f.copy(y = dockTop - f.r, settled = true, alpha = 0.7f)
                }
                // купа на Play
                val dPlay = kotlin.math.hypot(nx - playCx, ny - playCy)
                if (dPlay <= playR) {
                    return@map f.copy(settled = true, alpha = 0.75f)
                }
                // купа на Skip, якщо крила відкриті
                if (wingsOpen) {
                    for (sign in floatArrayOf(-1f, 1f)) {
                        val wx = playCx + sign * wingOff
                        if (kotlin.math.hypot(nx - wx, ny - playCy) <= wingR) {
                            return@map f.copy(settled = true, alpha = 0.75f)
                        }
                    }
                }
                f.copy(x = nx, y = ny)
            }
            delay(16)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
    ) {
        w = size.width
        h = size.height
        val snow = Color(0xE6FFFFFF)
        for (f in flakes) {
            drawCircle(
                color = snow.copy(alpha = f.alpha),
                radius = f.r,
                center = Offset(f.x, f.y),
            )
        }
    }
}
