package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray

object SearchHints {
    val countries = listOf(
        "Germany", "France", "United Kingdom", "Italy", "Spain", "Netherlands",
        "Switzerland", "Belgium", "Sweden", "Norway", "Denmark", "Austria",
        "Poland", "Ukraine", "Canada", "United States", "Australia", "Japan",
        "South Korea", "New Zealand"
    )
    /** Перші — чіпи на Дім. Решта — підказки в полі жанру пошуку. */
    val homeGenres = listOf(
        "Pop", "Dance", "Techno", "Trance", "House", "Rock", "Chill",
        "Lounge", "Ambient", "Jazz", "Hip-Hop", "Classical", "News",
    )
    val genres = homeGenres + listOf(
        "Electronic", "EDM", "Rap", "Country", "Reggae",
        "Blues", "Folk", "Metal", "R&B", "Soul", "Indie", "Synthwave",
    )
    val names = emptyList<String>()

    fun past(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString("pastSearches", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    fun savePast(ctx: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString("pastSearches", arr.toString()).apply()
    }
}
