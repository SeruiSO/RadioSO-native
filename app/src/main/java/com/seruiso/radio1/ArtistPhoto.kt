package com.seruiso.radio1

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

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
    // Lux FM / ефір без треку — не артист, не фото
    if ("studia air" in x || "studio air" in x || "studio on air" in x) return true
    if ("@@" in x) return true
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
private const val PERSIST_MIN_INTERVAL_MS = 5000L

/** url + оригінальний ts додавання (не оновлюється при кожному persist). */
private data class CacheEntry(val url: String, val ts: Long)

private val photoCache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>): Boolean {
        return size > 200
    }
}
private val photoCacheLock = Any()
@Volatile private var diskLoaded = false
@Volatile private var lastPersistMs = 0L

/** In-flight: один мережевий пошук на ключ артиста. */
private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

private fun cacheKey(artist: String) = artist.trim().lowercase()

private fun diskFile(ctx: Context): File = File(ctx.applicationContext.filesDir, DISK_FILE)

private fun entryFresh(e: CacheEntry, now: Long = System.currentTimeMillis()): Boolean {
    return e.ts <= 0L || now - e.ts <= TTL_MS
}

/** Підвантажити диск → пам'ять один раз на процес. Зберігає оригінальний ts. */
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
                    if (ts > 0 && now - ts > TTL_MS) continue
                    val url0 = o.optString("url", MISS)
                    val url = if (
                        url0.contains("deezer.com") || url0.contains("mzstatic.com")
                    ) MISS else url0
                    photoCache[k] = CacheEntry(url, if (ts > 0) ts else now)
                }
            }
        } catch (_: Exception) {
        }
        diskLoaded = true
    }
}

/** Пише на диск оригінальні ts кожного запису (без «омолодження»). */
private fun persistDisk(ctx: Context) {
    try {
        val root = JSONObject()
        synchronized(photoCacheLock) {
            for ((k, e) in photoCache) {
                root.put(k, JSONObject().put("url", e.url).put("ts", e.ts))
            }
        }
        diskFile(ctx).writeText(root.toString())
    } catch (_: Exception) {
    }
}

private fun schedulePersist(ctx: Context) {
    val now = System.currentTimeMillis()
    if (now - lastPersistMs < PERSIST_MIN_INTERVAL_MS) return
    lastPersistMs = now
    try {
        persistDisk(ctx)
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
    try {
        val code = conn.responseCode
        if (code != 200) {
            throw java.io.IOException("HTTP $code for $urlStr")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        try {
            conn.disconnect()
        } catch (_: Exception) {
        }
    }
}

private fun openArtistPhoto(artist: String): String? = try {
    val q = URLEncoder.encode("artist:\"" + artist.replace("\"", "") + "\"", "UTF-8")
    val search = JSONObject(httpGetJson(
        "https://musicbrainz.org/ws/2/artist/?query=" + q + "&fmt=json&limit=1",
        "application/json",
    ))
    val artists = search.optJSONArray("artists")
    if (artists == null || artists.length() == 0) null
    else {
        val mbid = artists.getJSONObject(0).optString("id")
        if (mbid.isBlank()) null else imageFromArtist(mbid) ?: coverFromArtist(mbid)
    }
} catch (e: Exception) {
    android.util.Log.w("ArtistPhoto", "open", e)
    null
}

private fun imageFromArtist(mbid: String): String? = try {
    val rels = JSONObject(httpGetJson(
        "https://musicbrainz.org/ws/2/artist/" + mbid + "?inc=url-rels&fmt=json",
        "application/json",
    )).optJSONArray("relations") ?: return null
    var found: String? = null
    for (i in 0 until rels.length()) {
        val r = rels.optJSONObject(i) ?: continue
        if (r.optString("type") != "image") continue
        val res = r.optJSONObject("url")?.optString("resource").orEmpty()
        if (res.startsWith("http") && res.contains("wikimedia.org")) {
            found = res
            break
        }
    }
    found
} catch (_: Exception) {
    null
}

private fun coverFromArtist(mbid: String): String? = try {
    val releases = JSONObject(httpGetJson(
        "https://musicbrainz.org/ws/2/release?artist=" + mbid + "&fmt=json&limit=1",
        "application/json",
    )).optJSONArray("releases")
    if (releases == null || releases.length() == 0) null
    else {
        val rid = releases.getJSONObject(0).optString("id")
        if (rid.isBlank()) null
        else {
            val images = JSONObject(httpGetJson(
                "https://coverartarchive.org/release/" + rid,
                "application/json",
            )).optJSONArray("images")
            if (images == null || images.length() == 0) null
            else {
                var url: String? = null
                for (i in 0 until images.length()) {
                    val im = images.optJSONObject(i) ?: continue
                    val thumbs = im.optJSONObject("thumbnails")
                    val small = if (thumbs == null) "" else thumbs.optString("small")
                    val t = if (small.isNotBlank()) small else im.optString("image")
                    if (t.startsWith("http")) {
                        url = t
                        if (im.optBoolean("front")) break
                    }
                }
                url
            }
        }
    }
} catch (_: Exception) {
    null
}


private suspend fun photoForSingleArtist(ctx: Context, artist: String): String? {
    if (artist.isBlank() || looksLikeJunk(artist)) return null
    ensureDiskLoaded(ctx)
    val key = cacheKey(artist)
    val now = System.currentTimeMillis()
    synchronized(photoCacheLock) {
        val e = photoCache[key]
        if (e != null) {
            if (entryFresh(e, now)) return e.url.ifBlank { null }
            photoCache.remove(key)
        }
    }
    // In-flight dedupe
    inFlight[key]?.let { return it.await() }
    val deferred = CompletableDeferred<String?>()
    val prev = inFlight.putIfAbsent(key, deferred)
    if (prev != null) return prev.await()
    try {
        // Послідовно: спочатку Deezer, iTunes лише як fallback
        val photo = openArtistPhoto(artist)
        val urlStore = photo ?: MISS
        synchronized(photoCacheLock) {
            val old = photoCache[key]
            if (old == null || old.url != urlStore) {
                photoCache[key] = CacheEntry(urlStore, System.currentTimeMillis())
            }
            // якщо той самий url — ts не чіпаємо
        }
        schedulePersist(ctx)
        deferred.complete(photo)
    } catch (e: Exception) {
        deferred.complete(null)
    } finally {
        inFlight.remove(key, deferred)
    }
    return deferred.await()
}

@Composable
fun rememberArtistPhotoUrl(artist: String, bust: String = ""): State<String?> {
    val ctx = LocalContext.current.applicationContext
    // Ключ remember по artist+bust — нова станція = новий state, без «старого» URL
    val result = remember(artist, bust) { mutableStateOf<String?>(null) }
    LaunchedEffect(artist, bust) {
        if (artist.isBlank() || looksLikeJunk(artist)) {
            result.value = null
            return@LaunchedEffect
        }
        // Коротка затримка лише для мережі; з кешу — майже одразу
        delay(80)
        val photo = withContext(Dispatchers.IO) {
            val split = splitArtists(artist)
            val list = buildList {
                if (split.size >= 2) add(artist.trim())
                addAll(split)
                if (isEmpty()) add(artist.trim())
            }.distinct().take(3)
            for (name in list) {
                val p = photoForSingleArtist(ctx, name)
                if (p != null) return@withContext p
            }
            null
        }
        result.value = photo
    }
    return result
}
