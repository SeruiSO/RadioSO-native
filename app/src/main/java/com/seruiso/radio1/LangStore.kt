package com.seruiso.radio1

import android.content.Context

object LangStore {
    const val KEY = "appLang"

    fun code(ctx: Context): String {
        val raw = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "uk") ?: "uk"
        return if (raw == "en") "en" else "uk"
    }

    fun s(ctx: Context): AppText = AppText.forCode(code(ctx))

    fun set(ctx: Context, code: String): AppText {
        val c = if (code == "en") "en" else "uk"
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, c).commit()
        return AppText.forCode(c)
    }

    fun toggle(ctx: Context): AppText {
        val next = if (code(ctx) == "uk") "en" else "uk"
        return set(ctx, next)
    }
}
