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

    val countries = listOf(
        "Germany", "France", "United Kingdom", "Italy", "Spain", "Netherlands",
        "Switzerland", "Belgium", "Sweden", "Norway", "Denmark", "Austria",
        "Poland", "Ukraine", "Canada", "United States", "Australia", "Japan"
    )
    val genres = listOf(
        "Pop", "Rock", "Dance", "Electronic", "Techno", "Trance", "House",
        "Jazz", "Chill", "Classical", "Hip Hop", "Metal"
    )

    fun search(name: String, country: String, genre: String, gen: Int): List<Station>? {
        val n = name.trim()
        val c = country.trim()
        val g = genre.trim()
        if (n.isEmpty() && c.isEmpty() && g.isEmpty()) return emptyList()
        val p = StringBuilder("hidebroken=true&limit=500&order=clickcount&reverse=true")
        if (n.isNotEmpty()) p.append("&name=").append(URLEncoder.encode(n, "UTF-8"))
        if (c.isNotEmpty()) p.append("&country=").append(URLEncoder.encode(c, "UTF-8"))
        if (g.isNotEmpty()) p.append("&tag=").append(URLEncoder.encode(g, "UTF-8"))
        val path = "/json/stations/search?$p"
        for (host in hosts) {
            if (gen != activeGen) return null
            try {
                val conn = URL("https://$host$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "RadioSO-native/0.6")
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
                    if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                    val title = o.optString("name")
                    if (title.isBlank()) continue
                    val tags = o.optString("tags").split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    val short = if (tags.size > 4) tags.take(4).joinToString(", ") + "..." else tags.joinToString(", ")
                    out.add(
                        Station(
                            url = url,
                            name = title,
                            genre = short,
                            country = o.optString("country"),
                            favicon = o.optString("favicon"),
                            tab = "search",
                        )
                    )
                }
                return out
            } catch (_: Exception) { }
        }
        return emptyList()
    }
}
