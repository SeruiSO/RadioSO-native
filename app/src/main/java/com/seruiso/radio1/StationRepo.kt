package com.seruiso.radio1

import android.content.Context
import org.json.JSONObject

object StationRepo {
    fun load(context: Context): Pair<List<String>, List<Station>> {
        val json = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val tabs = mutableListOf<String>()
        val all = mutableListOf<Station>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val tab = keys.next()
            tabs.add(tab)
            val arr = root.optJSONArray(tab) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("value")
                val name = o.optString("name")
                if (url.isBlank() || name.isBlank()) continue
                all.add(
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
        }
        return tabs to all
    }
}
