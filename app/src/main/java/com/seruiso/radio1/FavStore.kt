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
        val exists = cur.any { it.url == s.url }
        val next = if (exists) cur.filter { it.url != s.url } else listOf(s) + cur
        val arr = JSONArray()
        next.forEach {
            arr.put(JSONObject().put("value", it.url).put("name", it.name).put("genre", it.genre).put("country", it.country).put("favicon", it.favicon))
        }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, arr.toString()).commit()
        return !exists
    }

    fun saveStations(context: Context, list: List<Station>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("value", it.url).put("name", it.name).put("genre", it.genre).put("country", it.country).put("favicon", it.favicon))
        }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, arr.toString()).commit()
    }

    fun save(context: Context, key: String, urls: Set<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, arr.toString()).commit()
    }

    fun toggle(context: Context, key: String, url: String): Boolean {
        val set = urls(context, key)
        val now = if (set.contains(url)) { set.remove(url); false } else { set.add(url); true }
        save(context, key, set)
        return now
    }
}
