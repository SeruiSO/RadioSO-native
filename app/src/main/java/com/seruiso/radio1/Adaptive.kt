package com.seruiso.radio1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Розміри від доступного вікна (не від моделі телефону).
 * compact / tight — мала висота або вузька ширина.
 * Обкладинка NP обмежена і висотою, і шириною, щоб не вилазила за екран.
 */
data class UiMetrics(
    val screenW: Int,
    val screenH: Int,
    val fontScale: Float = 1f,
) {
    val narrow: Boolean get() = screenW < 360
    val compact: Boolean get() = screenH < 700 || screenW < 360
    val tight: Boolean get() = screenH < 640 || screenW < 340

    private fun grow(base: Dp, extraPerScale: Float): Dp {
        val bump = ((fontScale - 1f).coerceIn(0f, 0.6f) * extraPerScale).dp
        return base + bump
    }

    val infoH: Dp get() = grow(
        when {
            tight -> 76.dp
            compact -> 88.dp
            else -> 100.dp
        },
        28f,
    )
    val infoLandW: Dp get() = when {
        screenW < 500 -> 148.dp
        screenW < 600 -> 168.dp
        else -> 196.dp
    }
    val infoLandArtH: Dp get() = when {
        tight -> 108.dp
        compact -> 124.dp
        else -> 148.dp
    }
    val sheetTop: Dp get() {
        // нижче інфо-панелі (шапка 40 + infoH), і ще трохи якщо великий шрифт
        val base = when {
            tight -> 88.dp
            compact -> 104.dp
            screenH < 800 -> 116.dp
            else -> 124.dp
        }
        val bump = ((fontScale - 1f).coerceIn(0f, 0.5f) * 16f).dp
        return base + bump
    }
    /** Велика обкладинка: не ширша за екран мінус поля пейджера. */
    val npArt: Dp get() {
        val byH = when {
            tight -> 200.dp
            compact -> 240.dp
            screenH < 800 -> 280.dp
            else -> 300.dp
        }
        val maxW = (screenW - 96).coerceAtLeast(148).dp
        return if (byH < maxW) byH else maxW
    }
    val npPagerH: Dp get() = npArt + 16.dp
    val npFallbackArtH: Dp get() = when {
        tight -> 180.dp
        compact -> 220.dp
        else -> npArt
    }
    val playBtn: Dp get() = if (tight) 68.dp else if (compact) 72.dp else 80.dp
    /** Горизонтальні поля каруселі обкладинок. */
    val pagerPad: Dp get() = if (narrow) 16.dp else 36.dp
}

@Composable
fun rememberUiMetrics(): UiMetrics {
    val c = LocalConfiguration.current
    return remember(c.screenWidthDp, c.screenHeightDp, c.fontScale) {
        UiMetrics(c.screenWidthDp, c.screenHeightDp, c.fontScale)
    }
}
