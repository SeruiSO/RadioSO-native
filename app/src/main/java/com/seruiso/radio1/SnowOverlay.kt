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
 * Сніг 30 с. Осідає на верх панелі вкладок і на play/skip (горка).
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
        // верх панелі вкладок (лінія осідання по всій ширині)
        val panelTop = h - barH
        // кнопки зсунуті вгору на playLift — верх горки
        val hillTop = panelTop - liftPx
        // основна лінія купи = верх панелі; над кнопками — вище
        val dockTop = panelTop
        val playCx = w / 2f
        val playCy = panelTop - liftPx + playPx / 2f
        val playR = playPx / 2f + with(density) { 8.dp.toPx() }
        val wingR = playR
        val wingOff = playPx + gapPx

        fun newFlake(fromTop: Boolean): Flake = Flake(
            x = rnd.nextFloat() * w,
            y = if (fromTop) -rnd.nextFloat() * h * 0.12f else -rnd.nextFloat() * h * 0.45f,
            r = with(density) { (1.6f + rnd.nextFloat() * 2.6f).dp.toPx() },
            speed = with(density) { (26f + rnd.nextFloat() * 50f).dp.toPx() / 60f },
            drift = (rnd.nextFloat() - 0.5f) * 0.55f,
            phase = rnd.nextFloat() * 6.28f,
        )

        if (!started) {
            flakes = List(50) { newFlake(fromTop = false) }
            started = true
        }

        var t = 0f
        var spawnAcc = 0f
        while (isActive && active) {
            t += 0.016f
            spawnAcc += 0.016f
            val toAdd = mutableListOf<Flake>()
            if (spawnAcc >= 0.11f) {
                spawnAcc = 0f
                repeat(1 + rnd.nextInt(2)) { toAdd += newFlake(fromTop = true) }
            }
            val base = if (toAdd.isEmpty()) flakes else flakes + toAdd
            // кап: не викидати осілий сніг з низу — лише старі літаючі
            val capped = if (base.size > 160) {
                val settled = base.filter { it.settled }
                val flying = base.filter { !it.settled }
                settled.takeLast(90) + flying.takeLast(70)
            } else base

            flakes = capped.map { f ->
                if (f.settled) return@map f
                var nx = f.x + f.drift + sin(t * 1.7f + f.phase) * 0.35f
                var ny = f.y + f.speed
                if (nx < -10f) nx = w + 10f
                if (nx > w + 10f) nx = -10f

                // спочатку кнопки (вище панелі)
                val dPlay = kotlin.math.hypot(nx - playCx, ny - playCy)
                if (dPlay <= playR && ny >= playCy - playR) {
                    // лягає на «дах» кнопки
                    val sy = (playCy - playR + f.r).coerceAtMost(dockTop - f.r)
                    return@map f.copy(x = nx, y = sy, settled = true, alpha = 0.8f)
                }
                if (wingsOpen) {
                    for (sign in floatArrayOf(-1f, 1f)) {
                        val wx = playCx + sign * wingOff
                        if (kotlin.math.hypot(nx - wx, ny - playCy) <= wingR && ny >= playCy - wingR) {
                            val sy = (playCy - wingR + f.r).coerceAtMost(dockTop - f.r)
                            return@map f.copy(x = nx, y = sy, settled = true, alpha = 0.8f)
                        }
                    }
                }
                // панель вкладок — завжди осісти, ніколи не зникнути під низом
                if (ny >= dockTop - f.r) {
                    return@map f.copy(y = dockTop - f.r, settled = true, alpha = 0.72f)
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
