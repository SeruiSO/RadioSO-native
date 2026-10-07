package com.seruiso.radio1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Розміри від висоти екрана — NP не наїжджає на інфо на малих телефонах. */
data class UiMetrics(val screenW: Int, val screenH: Int) {
    val compact: Boolean get() = screenH < 700 || screenW < 360
    val tight: Boolean get() = screenH < 640

    val infoH: Dp get() = when {
        tight -> 80.dp
        compact -> 90.dp
        else -> 100.dp
    }
    val infoLandW: Dp get() = if (screenW < 600) 168.dp else 196.dp
    val infoLandArtH: Dp get() = when {
        tight -> 120.dp
        compact -> 132.dp
        else -> 148.dp
    }
    /** Відступ картки NP зверху (під меню+інфо). */
    val sheetTop: Dp get() = when {
        tight -> 96.dp
        compact -> 112.dp
        screenH < 800 -> 124.dp
        else -> 130.dp
    }
    /** Висота великої обкладинки в NP. */
    val npArt: Dp get() = when {
        tight -> 220.dp
        compact -> 260.dp
        screenH < 800 -> 280.dp
        else -> 300.dp
    }
    val npPagerH: Dp get() = npArt + 18.dp
    /** Запасний блок під історію треків у NP. */
    val npFallbackArtH: Dp get() = when {
        tight -> 200.dp
        compact -> 248.dp
        else -> 320.dp
    }
    val playBtn: Dp get() = if (compact) 72.dp else 80.dp
}

@Composable
fun rememberUiMetrics(): UiMetrics {
    val c = LocalConfiguration.current
    return remember(c.screenWidthDp, c.screenHeightDp) {
        UiMetrics(c.screenWidthDp, c.screenHeightDp)
    }
}
