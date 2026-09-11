package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TabStore {
    private val KEY_CUSTOM = BluetoothAutoPlayPlugin.KEY_CUSTOM_TABS
    private val KEY_ADDED = BluetoothAutoPlayPlugin.KEY_USER_ADDED
    private val KEY_HIDDEN = BluetoothAutoPlayPlugin.KEY_HIDDEN_TABS
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

    /** Вкладки, які користувач «видалив» (у т.ч. вбудовані techno/pop/…). */
    fun hiddenTabs(ctx: Context): Set<String> {
        val raw = prefs(ctx).getString(KEY_HIDDEN, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optString(i).trim()
            if (t.isNotEmpty()) out.add(t)
        }
        return out
    }

    private fun saveHidden(ctx: Context, tabs: Collection<String>) {
        val arr = JSONArray()
        tabs.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_HIDDEN, arr.toString()).apply()
    }

    fun addTab(ctx: Context, rawName: String, builtInTabs: List<String> = emptyList()): String? {
        val name = rawName.trim().lowercase()
        if (name.isEmpty()) return ctx.getString(R.string.err_enter_name)
        // латиниця + українські літери (id у JSON/prefs — безпечно)
        if (name.length > 10 || !name.matches(Regex("^[a-z0-9_а-яіїєґ-]+$"))) {
            return ctx.getString(R.string.err_tab_chars)
        }
        val cur = customTabs(ctx).toMutableList()
        val builtInLower = builtInTabs.map { it.lowercase() }
        if (reserved.contains(name) || cur.contains(name) || builtInLower.contains(name)) {
            return ctx.getString(R.string.err_tab_exists)
        }
        if (cur.size >= 7) return ctx.getString(R.string.err_tab_max)
        cur.add(name)
        saveTabs(ctx, cur)
        return null
    }

    fun addStation(ctx: Context, tab: String, s: Station): String? {
        if (reserved.contains(tab) || tab == "search") return ctx.getString(R.string.err_tab_reserved)
        unDelete(ctx, tab, s.url)
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        val arr = root.optJSONArray(tab) ?: JSONArray()
        val next = JSONArray()
        next.put(
            JSONObject()
                .put("value", s.url)
                .put("name", s.name)
                .put("genre", s.genre)
                .put("country", s.country)
                .put("favicon", s.favicon)
        )
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("value") == s.url) continue
            next.put(o)
        }
        root.put(tab, next)
        val order = mutableListOf(s.url)
        val rawOrd = prefs(ctx).getString("${BluetoothAutoPlayPlugin.KEY_ORDER_PREFIX}$tab", "[]") ?: "[]"
        val oa = JSONArray(rawOrd)
        for (i in 0 until oa.length()) {
            val u = oa.optString(i)
            if (u.isNotBlank() && u != s.url) order.add(u)
        }
        prefs(ctx).edit()
            .putString(KEY_ADDED, root.toString())
            .putString("${BluetoothAutoPlayPlugin.KEY_ORDER_PREFIX}$tab", JSONArray(order).toString())
            .apply()
        return null
    }

    /** Безпечно читає deletedStations як об'єкт {tab: [urls]}. Якщо у старій версії
     *  застосунку там лежав плаский масив — просто починаємо з чистого об'єкта,
     *  щоб не впасти при парсингу. */
    private fun deletedRoot(ctx: Context): JSONObject {
        val raw = prefs(ctx).getString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, "{}") ?: "{}"
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    fun unDelete(ctx: Context, tab: String, url: String) {
        val root = deletedRoot(ctx)
        val arr = root.optJSONArray(tab) ?: return
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            if (u != url) next.put(u)
        }
        root.put(tab, next)
        prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, root.toString()).apply()
    }

    fun renameTab(ctx: Context, old: String, rawNew: String, builtInTabs: List<String> = emptyList()): String? {
        val name = rawNew.trim().lowercase()
        if (name.isEmpty()) return ctx.getString(R.string.err_enter_name)
        if (name == old) return null
        if (name.length > 10 || !name.matches(Regex("^[a-z0-9_-]+$"))) {
            return ctx.getString(R.string.err_tab_chars_en)
        }
        val cur = customTabs(ctx).toMutableList()
        val builtInLower = builtInTabs.map { it.lowercase() }
        if (reserved.contains(name) || cur.contains(name) || builtInLower.contains(name)) {
            return ctx.getString(R.string.err_tab_exists)
        }
        val idx = cur.indexOf(old)
        if (idx < 0) return ctx.getString(R.string.err_tab_no)
        cur[idx] = name
        saveTabs(ctx, cur)
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        if (root.has(old)) {
            root.put(name, root.optJSONArray(old) ?: JSONArray())
            root.remove(old)
            prefs(ctx).edit().putString(KEY_ADDED, root.toString()).apply()
        }
        val delRoot = deletedRoot(ctx)
        if (delRoot.has(old)) {
            delRoot.put(name, delRoot.optJSONArray(old) ?: JSONArray())
            delRoot.remove(old)
            prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, delRoot.toString()).apply()
        }
        return null
    }

    fun deleteTab(ctx: Context, tab: String) {
        if (tab in reserved || tab == "search") return
        // Кастомна — прибираємо зі списку customTabs
        if (tab in customTabs(ctx)) {
            saveTabs(ctx, customTabs(ctx).filter { it != tab })
        } else {
            // Вбудована (techno/trance/ukraine/pop…) — ховаємо, stations.json не чіпаємо
            val h = hiddenTabs(ctx).toMutableSet()
            h.add(tab)
            saveHidden(ctx, h)
        }
        val root = JSONObject(prefs(ctx).getString(KEY_ADDED, "{}") ?: "{}")
        root.remove(tab)
        prefs(ctx).edit().putString(KEY_ADDED, root.toString()).apply()
        val delRoot = deletedRoot(ctx)
        delRoot.remove(tab)
        prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, delRoot.toString()).apply()
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
        prefs(ctx).edit().putString(KEY_ADDED, root.toString()).apply()
        // Позначаємо станцію видаленою САМЕ на цій вкладці — на інших вкладках,
        // де є ця сама станція (той самий URL доданий окремо), вона й далі
        // показуватиметься без змін.
        val delRoot = deletedRoot(ctx)
        val delArr = delRoot.optJSONArray(tab) ?: JSONArray()
        delArr.put(url)
        delRoot.put(tab, delArr)
        prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_DELETED_STATIONS, delRoot.toString()).apply()
    }

    fun saveOrder(ctx: Context, tab: String, urls: List<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_ORDER_PREFIX + tab, arr.toString()).apply()
    }

    fun applyOrder(ctx: Context, tab: String, list: List<Station>): List<Station> {
        val raw = prefs(ctx).getString(BluetoothAutoPlayPlugin.KEY_ORDER_PREFIX + tab, null) ?: return list
        val arr = JSONArray(raw)
        val map = list.associateBy { it.url }.toMutableMap()
        val out = mutableListOf<Station>()
        for (i in 0 until arr.length()) {
            val u = arr.optString(i)
            val s = map.remove(u) ?: continue
            out.add(s)
        }
        out.addAll(0, map.values)
        return out
    }

    fun deleted(ctx: Context, tab: String): Set<String> {
        val arr = deletedRoot(ctx).optJSONArray(tab) ?: JSONArray()
        val s = linkedSetOf<String>()
        for (i in 0 until arr.length()) s.add(arr.optString(i))
        return s
    }

    /** Мапа вкладка -> набір видалених URL. Для екранів, що показують станції
     *  з кількох вкладок одразу (наприклад, загальний пошуковий індекс), де
     *  видалення потрібно перевіряти саме по вкладці конкретної станції. */
    fun deletedMap(ctx: Context): Map<String, Set<String>> {
        val root = deletedRoot(ctx)
        val out = mutableMapOf<String, Set<String>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = root.optJSONArray(k) ?: continue
            val s = linkedSetOf<String>()
            for (i in 0 until arr.length()) s.add(arr.optString(i))
            out[k] = s
        }
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
        prefs(ctx).edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
}
