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
 * Сніг 30 с. Осідає високо: на верх панелі вкладок і на play/skip.
 * Осілий сніг ніколи не видаляється під час сесії.
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
        delay(30_000)
        onDismiss()
    }

    LaunchedEffect(active, w, h) {
        if (!active || w <= 0f || h <= 0f) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        val playPx = with(density) { playDp.dp.toPx() }
        val liftPx = with(density) { playLiftDp.dp.toPx() }
        val gapPx = with(density) { wingGapDp.dp.toPx() }
        val barH = with(density) { 56.dp.toPx() }
        // панель вкладок
        val panelTop = h - barH
        // ~2× вище попереднього: лінія купи біля верху горки/кнопок
        val dockTop = panelTop - liftPx
        val playCx = w / 2f
        val playCy = panelTop - liftPx + playPx / 2f
        val playR = playPx / 2f + with(density) { 10.dp.toPx() }
        val wingR = playR
        val wingOff = playPx + gapPx

        fun newFlake(fromTop: Boolean): Flake = Flake(
            x = rnd.nextFloat() * w,
            y = if (fromTop) -rnd.nextFloat() * h * 0.12f else -rnd.nextFloat() * h * 0.4f,
            r = with(density) { (1.6f + rnd.nextFloat() * 2.6f).dp.toPx() },
            speed = with(density) { (24f + rnd.nextFloat() * 48f).dp.toPx() / 60f },
            drift = (rnd.nextFloat() - 0.5f) * 0.5f,
            phase = rnd.nextFloat() * 6.28f,
        )

        if (!started) {
            flakes = List(40) { newFlake(fromTop = false) }
            started = true
        }

        var t = 0f
        var spawnAcc = 0f
        while (isActive && active) {
            t += 0.016f
            spawnAcc += 0.016f
            val settled = flakes.filter { it.settled }
            var flying = flakes.filter { !it.settled }

            if (spawnAcc >= 0.14f) {
                spawnAcc = 0f
                // не більше ~35 літаючих — щоб не «збивали» купу
                if (flying.size < 35) {
                    repeat(1 + rnd.nextInt(2)) { flying = flying + newFlake(fromTop = true) }
                }
            }

            val moved = flying.map { f ->
                var nx = f.x + f.drift + sin(t * 1.7f + f.phase) * 0.32f
                var ny = f.y + f.speed
                if (nx < -10f) nx = w + 10f
                if (nx > w + 10f) nx = -10f

                // кнопки
                val dPlay = kotlin.math.hypot(nx - playCx, ny - playCy)
                if (ny >= playCy - playR && dPlay <= playR) {
                    return@map f.copy(
                        x = nx,
                        y = (playCy - playR + f.r * 0.5f),
                        settled = true,
                        alpha = 0.82f,
                    )
                }
                if (wingsOpen) {
                    for (sign in floatArrayOf(-1f, 1f)) {
                        val wx = playCx + sign * wingOff
                        if (ny >= playCy - wingR && kotlin.math.hypot(nx - wx, ny - playCy) <= wingR) {
                            return@map f.copy(
                                x = nx,
                                y = (playCy - wingR + f.r * 0.5f),
                                settled = true,
                                alpha = 0.82f,
                            )
                        }
                    }
                }
                // лінія панелі/горки — обов'язкове осідання, без зникнення
                if (ny >= dockTop - f.r) {
                    return@map f.copy(x = nx, y = dockTop - f.r, settled = true, alpha = 0.75f)
                }
                f.copy(x = nx, y = ny)
            }

            // осілий сніг ніколи не чіпаємо; ліміт лише на flying
            val settledCap = if (settled.size > 120) settled.takeLast(120) else settled
            flakes = settledCap + moved
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
