package com.seruiso.radio1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Виконавця з ICY-метаданих радіопотоку намагаємось витягти за конвенцією
 * "Виконавець - Назва" (найпоширеніший формат StreamTitle). Це евристика,
 * а не гарантія: не всі станції її дотримуються, тож у сумнівних випадках
 * (немає роздільника, задовгий/закороткий кандидат) краще повернути "",
 * ніж показати фото не того виконавця.
 */
fun artistFromTrackTitle(title: String): String {
    val t = title.trim()
    if (t.isEmpty()) return ""
    for (sep in listOf(" - ", " – ", " — ", " | ")) {
        val idx = t.indexOf(sep)
        if (idx > 0) {
            val a = t.substring(0, idx).trim()
            if (a.length in 2..60) return a
        }
    }
    return ""
}

private const val UA = "RadioSO/1.0 (+https://github.com/SeruiSO/RadioSO-native)"

private fun httpGetJson(urlStr: String, accept: String): String {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.connectTimeout = 4000
    conn.readTimeout = 4000
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", UA)
    conn.setRequestProperty("Accept", accept)
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    conn.disconnect()
    return body
}

/** Deezer — швидко і без ключа, але слабко покриває менш "мейнстрімних" українських виконавців. */
private fun deezerArtistPhoto(artist: String): String? = try {
    val q = URLEncoder.encode(artist, "UTF-8")
    val body = httpGetJson("https://api.deezer.com/search/artist?q=$q&limit=1", "application/json")
    val arr = JSONObject(body).optJSONArray("data")
    if (arr != null && arr.length() > 0) {
        val obj = arr.getJSONObject(0)
        listOf(obj.optString("picture_big"), obj.optString("picture_medium"), obj.optString("picture"))
            .firstOrNull { it.isNotBlank() }
    } else null
} catch (e: Exception) {
    null
}

/** MusicBrainz — відкрита база даних виконавців (без ключа), значно ширше охоплення регіональних імен. */
private fun musicBrainzMbid(artist: String): String? = try {
    val q = URLEncoder.encode("artist:$artist", "UTF-8")
    val body = httpGetJson("https://musicbrainz.org/ws/2/artist/?query=$q&fmt=json&limit=1", "application/json")
    val arr = JSONObject(body).optJSONArray("artists")
    if (arr != null && arr.length() > 0) arr.getJSONObject(0).optString("id").ifBlank { null } else null
} catch (e: Exception) {
    null
}

/** Фото за MusicBrainz ID через Wikidata (P434 -> P18): працює для будь-кого зі статтею у Вікіпедії/Вікідані. */
private fun wikidataPhotoByMbid(mbid: String): String? = try {
    val sparql = """
        SELECT ?image WHERE {
          ?artist wdt:P434 "$mbid" .
          ?artist wdt:P18 ?image .
        } LIMIT 1
    """.trimIndent()
    val q = URLEncoder.encode(sparql, "UTF-8")
    val body = httpGetJson(
        "https://query.wikidata.org/sparql?query=$q&format=json",
        "application/sparql-results+json"
    )
    val bindings = JSONObject(body).optJSONObject("results")?.optJSONArray("bindings")
    if (bindings != null && bindings.length() > 0) {
        bindings.getJSONObject(0).optJSONObject("image")?.optString("value")?.ifBlank { null }
    } else null
} catch (e: Exception) {
    null
}

/**
 * Фото виконавця: спершу швидкий Deezer, і якщо там нічого не знайшлось —
 * MusicBrainz + Wikidata (ширше покриття, зокрема для українських
 * виконавців, яких немає в каталозі Deezer). null — якщо не знайдено ніде
 * або якщо всі запити не вдались; тоді в UI лишається фавікон/іконка.
 */
@Composable
fun rememberArtistPhotoUrl(artist: String): State<String?> {
    val result = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(artist) {
        result.value = null
        if (artist.isBlank()) return@LaunchedEffect
        delay(250) // невеликий дебаунс, щоб не бомбити API під час свайпу пейджера
        result.value = withContext(Dispatchers.IO) {
            deezerArtistPhoto(artist)
                ?: musicBrainzMbid(artist)?.let { wikidataPhotoByMbid(it) }
        }
    }
    return result
}
