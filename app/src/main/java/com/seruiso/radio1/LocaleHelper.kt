package com.seruiso.radio1

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App language: "uk" | "en". Default **uk** (not system).
 * wrap() uses empty Configuration so Activity configChanges (orientation/uiMode) stay flexible.
 */
object LocaleHelper {
    @JvmStatic
    fun current(ctx: Context): String {
        val v = ctx.applicationContext
            .getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString(BluetoothAutoPlayPlugin.KEY_APP_LANG, null)
        return if (v == "en" || v == "uk") v else "uk"
    }

    @JvmStatic
    fun set(ctx: Context, lang: String) {
        val l = if (lang == "en") "en" else "uk"
        ctx.applicationContext
            .getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(BluetoothAutoPlayPlugin.KEY_APP_LANG, l).apply()
    }

    @JvmStatic
    fun wrap(base: Context): Context {
        val lang = current(base)
        val locale = Locale(lang)
        val cfg = Configuration() // empty — do not freeze orientation/uiMode
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
