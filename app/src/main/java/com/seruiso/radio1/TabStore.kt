package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TabStore {
    private const val KEY_CUSTOM = "customTabs"
    private const val KEY_ADDED = "userAddedStations"
    val reserved = setOf("fav", "best", "local", "search", "localbest")

    fun customTabs(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_CUSTOM, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optString(i).trim()
            if (t.isNotEmpty()) out.add(t)
        }
        return out
    }

    fun addTab(ctx: Context, rawName: String): String? {
        val name = rawName.trim().lowercase()
        if (name.isEmpty()) return "Введи назву"
        if (name.length > 10 || !name.matches(Regex("^[a-z0-9_-]+$"))) {
            return "Лише a-z 0-9 _ - до 10 символів"
        }
        val cur = customTabs(ctx).toMutableList()
        if (reserved.contains(name) || cur.contains(name)) return "Така вкладка вже є"
        if (cur.size >= 7) return "Максимум 7 кастомних"
        cur.add(name)
        saveTabs(ctx, cur)
        return null
    }

    fun addStation(ctx: Context, tab: String, s: Station): String? {
        if (reserved.contains(tab) || tab == "search") return "Сюди не можна"
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        val arr = root.optJSONArray(tab) ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("value") == s.url) return "Вже є в $tab"
        }
        arr.put(
            JSONObject()
                .put("value", s.url)
                .put("name", s.name)
                .put("genre", s.genre)
                .put("country", s.country)
                .put("favicon", s.favicon)
        )
        root.put(tab, arr)
        prefs(ctx).edit().putString(KEY_ADDED, root.toString()).commit()
        return null
    }

    fun renameTab(ctx: Context, old: String, rawNew: String): String? {
        val name = rawNew.trim().lowercase()
        if (name.isEmpty()) return "Введи назву"
        if (name == old) return null
        if (name.length > 10 || !name.matches(Regex("^[a-z0-9_-]+$"))) {
            return "Лише a-z 0-9 _ - до 10 символів"
        }
        val cur = customTabs(ctx).toMutableList()
        if (reserved.contains(name) || cur.contains(name) || name in listOf("techno","trance","ukraine","pop")) {
            return "Така вкладка вже є"
        }
        val idx = cur.indexOf(old)
        if (idx < 0) return "Немає вкладки"
        cur[idx] = name
        saveTabs(ctx, cur)
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        if (root.has(old)) {
            root.put(name, root.optJSONArray(old) ?: JSONArray())
            root.remove(old)
            prefs(ctx).edit().putString(KEY_ADDED, root.toString()).commit()
        }
        return null
    }

    fun deleteTab(ctx: Context, tab: String) {
        saveTabs(ctx, customTabs(ctx).filter { it != tab })
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        root.remove(tab)
        prefs(ctx).edit().putString(KEY_ADDED, root.toString()).commit()
    }

    fun removeStation(ctx: Context, tab: String, url: String) {
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        val arr = root.optJSONArray(tab) ?: return
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("value") != url) next.put(o)
        }
        root.put(tab, next)
        prefs(ctx).edit().putString(KEY_ADDED, root.toString()).commit()
        val del = JSONArray(prefs(ctx).getString("deletedStations", "[]") ?: "[]")
        del.put(url)
        prefs(ctx).edit().putString("deletedStations", del.toString()).apply()
    }

    fun moveStation(ctx: Context, tab: String, url: String, dir: Int) {
        val list = extraStations(ctx, tab).toMutableList()
        val i = list.indexOfFirst { it.url == url }
        val j = i + dir
        if (i < 0 || j !in list.indices) return
        val a = list[i]; list[i] = list[j]; list[j] = a
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().put("value", s.url).put("name", s.name).put("genre", s.genre).put("country", s.country).put("favicon", s.favicon))
        }
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        root.put(tab, arr)
        prefs(ctx).edit().putString(KEY_ADDED, root.toString()).commit()
    }

    fun saveOrder(ctx: Context, tab: String, urls: List<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        prefs(ctx).edit().putString("order_" + tab, arr.toString()).commit()
    }

    fun applyOrder(ctx: Context, tab: String, list: List<Station>): List<Station> {
        val raw = prefs(ctx).getString("order_" + tab, null) ?: return list
        val arr = JSONArray(raw)
        val map = list.associateBy { it.url }.toMutableMap()
        val out = mutableListOf<Station>()
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            val s = map.remove(u) ?: continue
            out.add(s)
        }
        out.addAll(map.values)
        return out
    }

    fun extraStations(ctx: Context, tab: String): List<Station> {
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        val arr = root.optJSONArray(tab) ?: return emptyList()
        val out = mutableListOf<Station>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("value")
            val name = o.optString("name")
            if (url.isBlank() || name.isBlank()) continue
            out.add(
                Station(
                    url = url,
                    name = name,
                    genre = o.optString("genre"),
                    country = o.optString("country"),
                    favicon = o.optString("favicon"),
                    tab = tab,
                )
            )
        }
        return out
    }

    private fun saveTabs(ctx: Context, tabs: List<String>) {
        val arr = JSONArray()
        tabs.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_CUSTOM, arr.toString()).commit()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
}
