package com.seruiso.radio1

import android.content.Context
import androidx.compose.ui.graphics.Color

object ThemeStore {
    data class Theme(val id: String, val accent: Long)
    val all = listOf(
        Theme("shadow-pulse", 0xFF00E676),
        Theme("dark-abyss", 0xFFAA00FF),
        Theme("emerald-glow", 0xFF2EC4B6),
        Theme("retro-wave", 0xFFFF69B4),
        Theme("neon-pulse", 0xFF00F0FF),
        Theme("lime-surge", 0xFFB2FF59),
        Theme("flamingo-flash", 0xFFFF4081),
        Theme("aqua-glow", 0xFF26C6DA),
        Theme("aurora-haze", 0xFF64FFDA),
        Theme("starlit-amethyst", 0xFFB388FF),
        Theme("lunar-frost", 0xFF40C4FF),
    )
    fun get(ctx: Context): Theme {
        val id = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString("selectedTheme", "shadow-pulse")
        return all.firstOrNull { it.id == id } ?: all.first()
    }
    fun next(ctx: Context): Theme {
        val cur = get(ctx)
        val n = all[(all.indexOf(cur) + 1) % all.size]
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString("selectedTheme", n.id).commit()
        return n
    }
    fun set(ctx: Context, id: String): Theme {
        val n = all.firstOrNull { it.id == id } ?: all.first()
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString("selectedTheme", n.id).commit()
        return n
    }
    fun color(t: Theme) = Color(t.accent)
}
