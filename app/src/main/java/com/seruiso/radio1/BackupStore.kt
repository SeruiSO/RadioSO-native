package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BackupStore {
    fun exportJson(ctx: Context): String {
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
        val all = p.all
        val orders = JSONObject()
        all.keys.filter { it.startsWith("order_") }.forEach { k ->
            try { orders.put(k, JSONArray(p.getString(k, "[]"))) } catch (_: Exception) {}
        }
        val favRaw = p.getString(BluetoothAutoPlayPlugin.KEY_FAVORITES, "[]") ?: "[]"
        val added = try { JSONObject(p.getString(BluetoothAutoPlayPlugin.KEY_USER_ADDED, "{}")) } catch (_: Exception) { JSONObject() }
        // deletedStations тепер зберігається як {"вкладка": ["url", ...]}, а не пласким масивом —
        // читаємо саме так, зі страхуванням про всяк випадок (стара версія формату).
        val deletedRaw = try { JSONObject(p.getString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        return JSONObject()
            .put(BluetoothAutoPlayPlugin.KEY_SELECTED_THEME, p.getString(BluetoothAutoPlayPlugin.KEY_SELECTED_THEME, "shadow-pulse"))
            .put(BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS, JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS, "[]")))
            .put(BluetoothAutoPlayPlugin.KEY_USER_ADDED, added)
            .put("favoriteUrls", JSONArray(favRaw))
            .put("localBestUrls", JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, "[]")))
            .put(BluetoothAutoPlayPlugin.KEY_PAST_SEARCHES, JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_PAST_SEARCHES, "[]")))
            .put(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, deletedRaw)
            .put(BluetoothAutoPlayPlugin.KEY_CURRENT_TAB, p.getString(BluetoothAutoPlayPlugin.KEY_CURRENT_TAB, "fav"))
            .put("btWatchEnabled", p.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true))
            .put("stationOrder", orders)
            .put(BluetoothAutoPlayPlugin.KEY_ORDER_BEST_URIS, p.getString(BluetoothAutoPlayPlugin.KEY_ORDER_BEST_URIS, "[]"))
            // 0.13.184+: жанрові вкладки як editable + alias на stations.json
            .put("tabCatalogKeys", try {
                org.json.JSONObject(p.getString("tabCatalogKeys", "{}") ?: "{}")
            } catch (_: Exception) { org.json.JSONObject() })
            .put("genreTabsSeeded", p.getBoolean("genreTabsSeeded", false))
            .put(BluetoothAutoPlayPlugin.KEY_HIDDEN_TABS, try {
                org.json.JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_HIDDEN_TABS, "[]") ?: "[]")
            } catch (_: Exception) { org.json.JSONArray() })
            .toString(2)
    }

    fun importJson(ctx: Context, rawIn: String): String {
        var raw = rawIn.replace("\uFEFF", "").trim()
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) raw = raw.substring(start, end + 1)
        val o = JSONObject(raw)
        val e = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE).edit()
        if (o.has(BluetoothAutoPlayPlugin.KEY_SELECTED_THEME)) e.putString(BluetoothAutoPlayPlugin.KEY_SELECTED_THEME, o.optString(BluetoothAutoPlayPlugin.KEY_SELECTED_THEME))
        if (o.has(BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS)) e.putString(BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS, o.getJSONArray(BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS).toString())
        if (o.has(BluetoothAutoPlayPlugin.KEY_USER_ADDED)) {
            val u = o.opt(BluetoothAutoPlayPlugin.KEY_USER_ADDED)
            e.putString(BluetoothAutoPlayPlugin.KEY_USER_ADDED, u.toString())
        }
        val fav = when {
            o.has("favoriteUrls") -> o.get("favoriteUrls").toString()
            o.has("favoriteStations") -> o.get("favoriteStations").toString()
            else -> null
        }
        if (fav != null) e.putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, fav)
        val best = when {
            o.has("localBestUrls") -> o.get("localBestUrls").toString()
            o.has("localFavorites") -> o.get("localFavorites").toString()
            else -> null
        }
        if (best != null) e.putString(BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, best)
        if (o.has(BluetoothAutoPlayPlugin.KEY_PAST_SEARCHES)) e.putString(BluetoothAutoPlayPlugin.KEY_PAST_SEARCHES, o.get(BluetoothAutoPlayPlugin.KEY_PAST_SEARCHES).toString())
        if (o.has(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS)) e.putString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, o.get(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS).toString())
        if (o.has(BluetoothAutoPlayPlugin.KEY_CURRENT_TAB)) e.putString(BluetoothAutoPlayPlugin.KEY_CURRENT_TAB, o.optString(BluetoothAutoPlayPlugin.KEY_CURRENT_TAB))
        if (o.has("btWatchEnabled")) e.putBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, o.optBoolean("btWatchEnabled", true))
        if (o.has(BluetoothAutoPlayPlugin.KEY_ORDER_BEST_URIS)) e.putString(BluetoothAutoPlayPlugin.KEY_ORDER_BEST_URIS, o.optString(BluetoothAutoPlayPlugin.KEY_ORDER_BEST_URIS))
        val so = o.optJSONObject("stationOrder")
        if (so != null) {
            val keys = so.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = so.opt(k)
                val name = if (k.startsWith("order_")) k else "order_$k"
                e.putString(name, v.toString())
            }
        }
        if (o.has("tabCatalogKeys")) {
            val ck = o.opt("tabCatalogKeys")
            e.putString("tabCatalogKeys", ck?.toString() ?: "{}")
        }
        if (o.has("genreTabsSeeded")) {
            e.putBoolean("genreTabsSeeded", o.optBoolean("genreTabsSeeded", true))
        } else if (o.has(BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS)) {
            // старий backup зі списком вкладок — не пересівати поверх імпорту
            e.putBoolean("genreTabsSeeded", true)
        }
        if (o.has(BluetoothAutoPlayPlugin.KEY_HIDDEN_TABS)) {
            e.putString(
                BluetoothAutoPlayPlugin.KEY_HIDDEN_TABS,
                o.get(BluetoothAutoPlayPlugin.KEY_HIDDEN_TABS).toString()
            )
        }
        e.apply()
        // нормалізувати favicon у вже записаних favorites / userAdded
        enrichImportedFavicons(ctx)
        return ctx.getString(R.string.import_ok)
    }

    private fun enrichImportedFavicons(ctx: Context) {
        val catalog = try { StationRepo.load(ctx).second } catch (_: Exception) { emptyList() }
        val byId = catalog.groupBy { streamIdentity(it.url) }
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
        val e = p.edit()
        fun enrichArr(raw: String): String {
            val arr = try { JSONArray(raw) } catch (_: Exception) { return raw }
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val url: String
                var name = ""
                var genre = ""
                var country = ""
                var favicon = ""
                if (item is JSONObject) {
                    url = item.optString("value")
                    name = item.optString("name")
                    genre = item.optString("genre")
                    country = item.optString("country")
                    favicon = item.optString("favicon")
                } else {
                    url = arr.optString(i)
                }
                if (url.isBlank()) continue
                val cat = byId[streamIdentity(url)] ?: emptyList()
                if (name.isBlank()) name = cat.firstOrNull()?.name ?: url
                if (genre.isBlank()) genre = cat.firstOrNull()?.genre.orEmpty()
                if (country.isBlank()) country = cat.firstOrNull()?.country.orEmpty()
                favicon = resolvedFavicon(betterFavicon(favicon, bestFaviconFrom(cat)), null, url)
                out.put(
                    JSONObject()
                        .put("value", url)
                        .put("name", name)
                        .put("genre", genre)
                        .put("country", country)
                        .put("favicon", favicon)
                )
            }
            return out.toString()
        }
        e.putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, enrichArr(p.getString(BluetoothAutoPlayPlugin.KEY_FAVORITES, "[]") ?: "[]"))
        val addedRaw = p.getString(BluetoothAutoPlayPlugin.KEY_USER_ADDED, "{}") ?: "{}"
        try {
            val root = JSONObject(addedRaw)
            val keys = root.keys()
            val next = JSONObject()
            while (keys.hasNext()) {
                val k = keys.next()
                next.put(k, JSONArray(enrichArr(root.optJSONArray(k)?.toString() ?: "[]")))
            }
            e.putString(BluetoothAutoPlayPlugin.KEY_USER_ADDED, next.toString())
        } catch (_: Exception) {}
        e.apply()
    }
}
