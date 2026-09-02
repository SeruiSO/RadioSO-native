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
        val added = try { JSONObject(p.getString("userAddedStations", "{}")) } catch (_: Exception) { JSONObject() }
        return JSONObject()
            .put("selectedTheme", p.getString("selectedTheme", "shadow-pulse"))
            .put("customTabs", JSONArray(p.getString("customTabs", "[]")))
            .put("userAddedStations", added)
            .put("favoriteUrls", JSONArray(favRaw))
            .put("favoriteStations", JSONArray(favRaw))
            .put("localBestUrls", JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, "[]")))
            .put("localFavorites", JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, "[]")))
            .put("pastSearches", JSONArray(p.getString("pastSearches", "[]")))
            .put("deletedStations", JSONArray(p.getString("deletedStations", "[]")))
            .put("currentTab", p.getString("currentTab", "fav"))
            .put("btWatchEnabled", p.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true))
            .put("stationOrder", orders)
            .put("order_best_uris", p.getString("order_best_uris", "[]"))
            .toString(2)
    }

    fun importJson(ctx: Context, rawIn: String): String {
        var raw = rawIn.replace("\uFEFF", "").trim()
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) raw = raw.substring(start, end + 1)
        val o = JSONObject(raw)
        val e = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE).edit()
        if (o.has("selectedTheme")) e.putString("selectedTheme", o.optString("selectedTheme"))
        if (o.has("customTabs")) e.putString("customTabs", o.getJSONArray("customTabs").toString())
        if (o.has("userAddedStations")) {
            val u = o.opt("userAddedStations")
            e.putString("userAddedStations", u.toString())
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
        if (o.has("pastSearches")) e.putString("pastSearches", o.get("pastSearches").toString())
        if (o.has("deletedStations")) e.putString("deletedStations", o.get("deletedStations").toString())
        if (o.has("currentTab")) e.putString("currentTab", o.optString("currentTab"))
        if (o.has("btWatchEnabled")) e.putBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, o.optBoolean("btWatchEnabled", true))
        if (o.has("order_best_uris")) e.putString("order_best_uris", o.optString("order_best_uris"))
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
        e.commit()
        return "імпорт ок"
    }
}
