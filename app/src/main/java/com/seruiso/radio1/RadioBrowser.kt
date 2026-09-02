package com.seruiso.radio1

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RadioBrowser {
    private val hosts = listOf(
        "de1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "at1.api.radio-browser.info",
        "fr1.api.radio-browser.info",
    )
    @Volatile var activeGen: Int = 0

    val countries = SearchHints.countries
    val genres = SearchHints.genres

    fun search(name: String, country: String, genre: String, gen: Int): List<Station>? {
        val n = name.trim()
        val c = country.trim()
        val g = genre.trim()
        if (n.isEmpty() && c.isEmpty() && g.isEmpty()) return emptyList()
        val params = StringBuilder("hidebroken=true&limit=500&order=clickcount&reverse=true")
        if (n.isNotEmpty()) params.append("&name=").append(URLEncoder.encode(n, "UTF-8"))
        if (c.isNotEmpty()) params.append("&country=").append(URLEncoder.encode(c, "UTF-8"))
        if (g.isNotEmpty()) {
            params.append("&tag=").append(URLEncoder.encode(g.lowercase(), "UTF-8"))
            params.append("&tagExact=false")
        }
        var out = fetch("/json/stations/search?$params", gen)
        if (out != null && out.size < 15 && g.isNotEmpty() && n.isEmpty()) {
            val extra = fetch("/json/stations/bytag/" + URLEncoder.encode(g.lowercase(), "UTF-8") + "?hidebroken=true&limit=500&order=clickcount&reverse=true", gen)
            if (extra != null) out = (out + extra).distinctBy { it.url }
        }
        return out
    }

    private fun fetch(path: String, gen: Int): List<Station>? {
        for (host in hosts) {
            if (gen != activeGen) return null
            try {
                val conn = URL("https://$host$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "RadioSO-native/0.9")
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode != 200) { conn.disconnect(); continue }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                if (gen != activeGen) return null
                val arr = JSONArray(body)
                val out = mutableListOf<Station>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    var url = o.optString("url_resolved")
                    if (url.isBlank()) url = o.optString("url")
                    if (!url.startsWith("http")) continue
                    val title = o.optString("name")
                    if (title.isBlank()) continue
                    val tags = o.optString("tags").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val short = if (tags.size > 4) tags.take(4).joinToString(", ") + "..." else tags.joinToString(", ")
                    out.add(Station(url, title, short, o.optString("country"), o.optString("favicon"), "search"))
                }
                return out
            } catch (_: Exception) { }
        }
        return emptyList()
    }
}
