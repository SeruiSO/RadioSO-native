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

    fun search(query: String, country: String = "", genre: String = ""): List<Station> {
        val q = query.trim()
        val params = StringBuilder("hidebroken=true&limit=80&order=clickcount&reverse=true")
        if (q.isNotEmpty()) params.append("&name=").append(URLEncoder.encode(q, "UTF-8"))
        if (country.isNotBlank()) params.append("&country=").append(URLEncoder.encode(country, "UTF-8"))
        if (genre.isNotBlank()) params.append("&tag=").append(URLEncoder.encode(genre, "UTF-8"))
        if (q.isEmpty() && country.isBlank() && genre.isBlank()) return emptyList()
        val path = "/json/stations/search?$params"
        var last: Exception? = null
        for (host in hosts) {
            try {
                val conn = URL("https://$host$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "RadioSO-native/0.5")
                conn.setRequestProperty("Accept", "application/json")
                conn.connect()
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    continue
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val arr = JSONArray(body)
                val out = mutableListOf<Station>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    var url = o.optString("url_resolved")
                    if (url.isBlank()) url = o.optString("url")
                    val name = o.optString("name")
                    if (url.isBlank() || name.isBlank()) continue
                    out.add(
                        Station(
                            url = url,
                            name = name,
                            genre = o.optString("tags"),
                            country = o.optString("country"),
                            favicon = o.optString("favicon"),
                            tab = "search",
                        )
                    )
                }
                return out
            } catch (e: Exception) {
                last = e
            }
        }
        if (last != null) throw last
        return emptyList()
    }
}
