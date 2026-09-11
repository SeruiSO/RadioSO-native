package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FavStore {
    fun urls(context: Context, key: String): MutableSet<String> {
        val raw = context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val set = linkedSetOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            if (item is JSONObject) {
                val u = item.optString("value")
                if (u.isNotBlank()) set.add(u)
            } else {
                val u = arr.optString(i)
                if (u.isNotBlank()) set.add(u)
            }
        }
        return set
    }

    fun stations(context: Context): List<Station> {
        val raw = context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString(BluetoothAutoPlayPlugin.KEY_FAVORITES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<Station>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("value")
            val name = o.optString("name")
            if (url.isBlank() || name.isBlank()) continue
            out.add(Station(url, name, o.optString("genre"), o.optString("country"), o.optString("favicon"), "fav"))
        }
        return out
    }

    fun toggleStation(context: Context, s: Station): Boolean {
        val cur = stations(context).toMutableList()
        val idx = cur.indexOfFirst { it.url == s.url }
        val prefs = context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
        val next: List<Station>
        val nowFav: Boolean
        if (idx >= 0) {
            // Вже в обраному → зняти ★ і прибрати з order_fav
            next = cur.filter { it.url != s.url }
            nowFav = false
            val rawOrd = prefs.getString(BluetoothAutoPlayPlugin.KEY_ORDER_FAV, "[]") ?: "[]"
            val oa = JSONArray(rawOrd)
            val order = JSONArray()
            for (i in 0 until oa.length()) {
                val u = oa.optString(i)
                if (u.isNotBlank() && u != s.url) order.put(u)
            }
            prefs.edit()
                .putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, stationsToJson(next).toString())
                .putString(BluetoothAutoPlayPlugin.KEY_ORDER_FAV, order.toString())
                .apply()
        } else {
            // Нова ★ → свіжі метадані на початок + order_fav на початок
            next = listOf(s) + cur
            nowFav = true
            val rawOrd = prefs.getString(BluetoothAutoPlayPlugin.KEY_ORDER_FAV, "[]") ?: "[]"
            val oa = JSONArray(rawOrd)
            val order = JSONArray()
            order.put(s.url)
            for (i in 0 until oa.length()) {
                val u = oa.optString(i)
                if (u.isNotBlank() && u != s.url) order.put(u)
            }
            prefs.edit()
                .putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, stationsToJson(next).toString())
                .putString(BluetoothAutoPlayPlugin.KEY_ORDER_FAV, order.toString())
                .apply()
        }
        return nowFav
    }

    /** Оновити знімок в обраному свіжими name/genre/country/favicon (без зміни ★). */
    fun refreshStation(context: Context, s: Station) {
        val cur = stations(context)
        if (cur.none { it.url == s.url }) return
        val next = cur.map { if (it.url == s.url) s.copy(tab = "fav") else it }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, stationsToJson(next).toString()).apply()
    }

    private fun stationsToJson(list: List<Station>): JSONArray {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("value", it.url)
                    .put("name", it.name)
                    .put("genre", it.genre)
                    .put("country", it.country)
                    .put("favicon", it.favicon)
            )
        }
        return arr
    }

    fun saveStations(context: Context, list: List<Station>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("value", it.url).put("name", it.name).put("genre", it.genre).put("country", it.country).put("favicon", it.favicon))
        }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, arr.toString()).apply()
    }

    fun save(context: Context, key: String, urls: Set<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, arr.toString()).apply()
    }

    fun toggle(context: Context, key: String, url: String): Boolean {
        val set = urls(context, key)
        val now = if (set.contains(url)) { set.remove(url); false } else { set.add(url); true }
        save(context, key, set)
        return now
    }
}
