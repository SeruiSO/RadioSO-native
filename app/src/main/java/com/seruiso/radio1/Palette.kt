package com.seruiso.radio1

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Перемикач світлої/темної теми. Стан лежить в ОДНОМУ місці (Compose State) —
 * і Material3-схема (RadioSOTheme), і кастомні кольори в StationScreen
 * (bg/card/panel/text/muted) читають саме звідси, тож реагують на
 * перемикання одночасно, без дублювання логіки по всьому файлу.
 */
object Palette {
    private const val KEY = "uiLightTheme"

    var isLight by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        isLight = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun toggle(ctx: Context) {
        isLight = !isLight
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, isLight).apply()
    }

    val bg: Color get() = if (isLight) Color(0xFFF4F4F7) else Color(0xFF000000)
    val card: Color get() = if (isLight) Color(0xFFFFFFFF) else Color(0xFF141418)
    val panel: Color get() = if (isLight) Color(0xFFEAEAF0) else Color(0xFF1A1A1E)
    val panel2: Color get() = if (isLight) Color(0xFFE0E0E8) else Color(0xFF16161A)
    val text: Color get() = if (isLight) Color(0xFF16161A) else Color(0xFFF2F2F5)
    val muted: Color get() = if (isLight) Color(0x991A1A1E) else Color(0x9EF2F2F5)
}
