package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray

object FavStore {
    fun urls(context: Context, key: String): MutableSet<String> {
        val raw = context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val set = linkedSetOf<String>()
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            if (u.isNotBlank()) set.add(u)
        }
        return set
    }

    fun save(context: Context, key: String, urls: Set<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, arr.toString()).commit()
    }

    fun toggle(context: Context, key: String, url: String): Boolean {
        val set = urls(context, key)
        val now = if (set.contains(url)) {
            set.remove(url); false
        } else {
            set.add(url); true
        }
        save(context, key, set)
        return now
    }
}
