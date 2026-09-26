package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrackHistoryItem(
    val title: String,
    val station: String,
    val url: String,
    val favicon: String,
    val atMs: Long,
)

/** Історія треків з ефіру (до 30). */
object TrackHistoryStore {
    private const val PREFS = "track_history"
    private const val KEY = "items"
    private const val MAX = 30

    fun list(ctx: Context): List<TrackHistoryItem> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val t = o.optString("title").trim()
                if (t.isBlank()) null
                else TrackHistoryItem(
                    title = t,
                    station = o.optString("station"),
                    url = o.optString("url"),
                    favicon = o.optString("favicon"),
                    atMs = o.optLong("at"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun push(ctx: Context, title: String, station: String, url: String, favicon: String) {
        val t = title.trim()
        if (t.length < 2) return
        val cur = list(ctx).toMutableList()
        if (cur.firstOrNull()?.title.equals(t, ignoreCase = true)) return
        cur.add(
            0,
            TrackHistoryItem(
                title = t,
                station = station.trim(),
                url = url,
                favicon = favicon,
                atMs = System.currentTimeMillis(),
            ),
        )
        while (cur.size > MAX) cur.removeAt(cur.lastIndex)
        val arr = JSONArray()
        cur.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("station", it.station)
                    .put("url", it.url)
                    .put("favicon", it.favicon)
                    .put("at", it.atMs),
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
