package com.seruiso.radio1

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val TITLE_SEPS = listOf(
    " - ", " – ", " — ", " | ",
    " / ", " \\ ", " // ",
    " • ", " ~ ",
)

private fun looksLikeJunk(t: String): Boolean {
    val x = t.trim().lowercase()
    if (x.length < 2) return true
    if (x in setOf("unknown", "n/a", "null", "-", "--", "---", "advert",
            "advertisement", "commercial", "promo", "jingle", "id")) return true
    if (x.startsWith("http") || x.contains("www.")) return true
    return false
}

/**
 * Виконавець з ICY StreamTitle: ліва частина до першого роздільника з пробілами.
 * Голий / і \ не чіпаємо (AC/DC). Лише " / " і " \ ".
 */
fun artistFromTrackTitle(title: String): String {
    val t = title.trim()
    if (t.isEmpty() || looksLikeJunk(t)) return ""
    for (sep in TITLE_SEPS) {
        val idx = t.indexOf(sep)
        if (idx > 0) {
            val a = t.substring(0, idx).trim()
            if (a.length in 2..80 && !looksLikeJunk(a)) return a
        }
    }
    return ""
}

/**
 * "A & B", "A feat. B", "A / B" (з пробілами) → ["A", "B"].
 * Голий слеш не сплітить.
 */
fun splitArtists(raw: String): List<String> {
    var s = raw.trim()
    if (s.isEmpty()) return emptyList()
    s = s.replace(Regex("""\s*[\(\[][^)\\]]*[\)\]]\s*$"""), "").trim()
    if (s.isEmpty()) return emptyList()
    val parts = s.split(
        Regex(
            """\s*(?:&| and | та | и | feat\.? | ft\.? | featuring | vs\.? | x | × | with |\s+/\s+|\s+\\\s+|;|\+)\s*|,\s*""",
            RegexOption.IGNORE_CASE
        )
    )
        .map { it.trim() }
        .filter { it.length in 2..60 && !looksLikeJunk(it) }
    return parts.distinct()
}

private const val UA = "RadioSO/1.0 (+https://github.com/SeruiSO/RadioSO-native)"
private const val DISK_FILE = "artist_photo_cache.json"
private const val TTL_MS = 14L * 24 * 60 * 60 * 1000 // 14 днів
private const val MISS = "" // порожній URL = "не знайдено"

private val photoCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean {
        return size > 200
    }
}
private val photoCacheLock = Any()
@Volatile private var diskLoaded = false

private fun cacheKey(artist: String) = artist.trim().lowercase()

private fun diskFile(ctx: Context): File = File(ctx.applicationContext.filesDir, DISK_FILE)

/** Підвантажити диск → пам'ять один раз на процес. */
private fun ensureDiskLoaded(ctx: Context) {
    if (diskLoaded) return
    synchronized(photoCacheLock) {
        if (diskLoaded) return
        try {
            val f = diskFile(ctx)
            if (f.isFile) {
                val root = JSONObject(f.readText())
                val now = System.currentTimeMillis()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = root.optJSONObject(k) ?: continue
                    val ts = o.optLong("ts", 0L)
                    if (ts > 0 && now - ts > TTL_MS) continue // прострочено
                    val url = o.optString("url", MISS)
                    photoCache[k] = url
                }
            }
        } catch (_: Exception) {
        }
        diskLoaded = true
    }
}

private fun persistDisk(ctx: Context) {
    try {
        val root = JSONObject()
        val now = System.currentTimeMillis()
        synchronized(photoCacheLock) {
            for ((k, v) in photoCache) {
                root.put(k, JSONObject().put("url", v).put("ts", now))
            }
        }
        diskFile(ctx).writeText(root.toString())
    } catch (_: Exception) {
    }
}

private fun httpGetJson(urlStr: String, accept: String): String {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.connectTimeout = 2500
    conn.readTimeout = 2500
    conn.requestMethod = "GET"
    conn.setRequestProperty("User-Agent", UA)
    conn.setRequestProperty("Accept", accept)
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    conn.disconnect()
    return body
}

private fun deezerArtistPhoto(artist: String): String? = try {
    val q = URLEncoder.encode(artist, "UTF-8")
    val body = httpGetJson("https://api.deezer.com/search/artist?q=$q&limit=1", "application/json")
    val arr = JSONObject(body).optJSONArray("data")
    if (arr != null && arr.length() > 0) {
        val obj = arr.getJSONObject(0)
        listOf(obj.optString("picture_big"), obj.optString("picture_medium"), obj.optString("picture"))
            .firstOrNull { it.isNotBlank() }
    } else null
} catch (_: Exception) {
    null
}

/** iTunes Search — без ключа, часто є те, чого немає в Deezer. */
private fun itunesArtistPhoto(artist: String): String? = try {
    val q = URLEncoder.encode(artist, "UTF-8")
    val body = httpGetJson(
        "https://itunes.apple.com/search?term=$q&entity=musicArtist&limit=1",
        "application/json"
    )
    val arr = JSONObject(body).optJSONArray("results")
    if (arr != null && arr.length() > 0) {
        val raw = arr.getJSONObject(0).optString("artworkUrl100")
        if (raw.isNotBlank()) raw.replace("100x100bb", "600x600bb").replace("100x100", "600x600")
        else null
    } else null
} catch (_: Exception) {
    null
}

private suspend fun photoForSingleArtist(ctx: Context, artist: String): String? {
    if (artist.isBlank() || looksLikeJunk(artist)) return null
    ensureDiskLoaded(ctx)
    val key = cacheKey(artist)
    synchronized(photoCacheLock) {
        photoCache[key]?.let { return it.ifBlank { null } }
    }
    val photo = coroutineScope {
        val deezer = async { deezerArtistPhoto(artist) }
        val itunes = async { itunesArtistPhoto(artist) }
        deezer.await() ?: itunes.await()
    }
    synchronized(photoCacheLock) {
        photoCache[key] = photo ?: MISS
    }
    // диск асинхронно — не блокуємо UI
    try { persistDisk(ctx) } catch (_: Exception) {}
    return photo
}

@Composable
fun rememberArtistPhotoUrl(artist: String, bust: String = ""): State<String?> {
    val ctx = LocalContext.current.applicationContext
    val result = remember(artist, bust) { mutableStateOf<String?>(null) }
    LaunchedEffect(artist, bust) {
        result.value = null
        if (artist.isBlank() || looksLikeJunk(artist)) return@LaunchedEffect
        delay(250)
        result.value = withContext(Dispatchers.IO) {
            val split = splitArtists(artist)
            val list = buildList {
                if (split.size >= 2) add(artist.trim())
                addAll(split)
                if (isEmpty()) add(artist.trim())
            }.distinct().take(3)
            for (name in list) {
                val photo = photoForSingleArtist(ctx, name)
                if (photo != null) return@withContext photo
            }
            null
        }
    }
    return result
}
