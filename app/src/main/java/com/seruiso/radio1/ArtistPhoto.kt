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
    " - ", " – ", " — ", " − ", " ‒ ",
    " | ", " / ", " \\ ", " // ",
    " • ", " · ", " ~ ", " –",
    " ~ ", ": ", " – ",
)

private val JUNK_EXACT = setOf(
    "unknown", "n/a", "na", "null", "-", "--", "---", "untitled", "no title",
    "advert", "advertisement", "commercial", "promo", "jingle", "id",
    "ident", "bumper", "sweeper", "break", "news", "weather", "traffic",
    "live", "on air", "on-air", "onair", "air", "studio", "studia",
    "studia air", "studio air", "studio on air",
    "radio", "fm", "am", "dj", "mc", "mix", "megamix",
    "ефір", "в ефірі", "студія", "реклама", "новини", "перерва",
    "lux", "lux fm", "люкс", "люкс фм",
)

private val JUNK_CONTAINS = listOf(
    "studia air", "studio air", "on air", "on-air",
    "advert", "jingle", "реклама", "в ефірі",
)

private val PREFIXES = listOf(
    "now playing:", "now playing -", "nowplaying:", "np:", "playing:",
    "streamtitle=", "title:", "track:", "song:", "current:",
)

data class ParsedTrack(val artist: String, val title: String) {
    val display: String
        get() = when {
            artist.isNotBlank() && title.isNotBlank() -> "$artist — $title"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> ""
        }
}

private fun stripIcyNoise(raw: String): String {
    var s = raw.replace("\u0000", " ").replace('\u00a0', ' ')
    s = s.replace("StreamTitle=", "", ignoreCase = true)
    s = s.replace("StreamUrl=", "", ignoreCase = true)
    s = s.trim().trim('\'', '"', '`')
    s = s.replace(Regex("""\s+"""), " ").trim()
    val low = s.lowercase()
    for (p in PREFIXES) {
        if (low.startsWith(p)) {
            s = s.substring(p.length).trim().trim('\'', '"')
            break
        }
    }
    // «STUDIA AIR @@», «***», «###»
    s = s.replace(Regex("""[@#*_~]{2,}"""), " ").replace(Regex("""\s+"""), " ").trim()
    return s
}

fun looksLikeJunk(t: String): Boolean {
    val x = stripIcyNoise(t).lowercase()
    if (x.length < 2) return true
    if (x in JUNK_EXACT) return true
    if (JUNK_CONTAINS.any { x.contains(it) }) return true
    if (x.startsWith("http") || x.contains("www.")) return true
    val letters = x.count { it.isLetter() }
    if (letters < 2) return true
    val punct = x.count { !it.isLetterOrDigit() && !it.isWhitespace() }
    if (x.isNotEmpty() && punct * 2 >= x.length) return true
    return false
}

private fun sameStation(part: String, station: String): Boolean {
    if (station.isBlank()) return false
    val a = stripIcyNoise(part).lowercase()
    val b = stripIcyNoise(station).lowercase()
    if (a.length < 4 || b.length < 4) return false
    if (a == b) return true
    // Лише префікс назви («люкс фм»), не слоган «сучасні хіти» всередині station
    val head = b.split(Regex("""\s+[-–—|]\s+""")).firstOrNull() ?: b
    if (head.length >= 4 && (a == head || a.startsWith("$head "))) return true
    return false
}

/** «Люкс ФМ - Сучасні хіти - Artist - Song» → «Artist - Song» */
private fun stripStationPrefix(raw: String, station: String): String {
    if (station.isBlank()) return raw
    var t = raw
    val sn = stripIcyNoise(station)
    if (sn.length >= 4 && t.regionMatches(0, sn, 0, sn.length, ignoreCase = true)) {
        t = t.substring(sn.length).trim()
        t = t.replaceFirst(Regex("""^[-–—|:·/]+\s*"""), "").trim()
        return t.ifBlank { raw }
    }
    val parts = sn.split(Regex("""\s+[-–—|]\s+""")).filter { it.length >= 3 }
    for (part in parts) {
        if (t.regionMatches(0, part, 0, part.length, ignoreCase = true)) {
            t = t.substring(part.length).trim()
            t = t.replaceFirst(Regex("""^[-–—|:·/]+\s*"""), "").trim()
        }
    }
    return t.ifBlank { raw }
}

fun parseStreamTitle(raw: String, stationName: String = ""): ParsedTrack {
    var t = stripIcyNoise(raw)
    t = stripStationPrefix(t, stationName)
    if (t.isEmpty() || looksLikeJunk(t)) return ParsedTrack("", "")
    var left = ""
    var right = ""
    for (sep in TITLE_SEPS) {
        val idx = t.indexOf(sep)
        if (idx > 0) {
            left = t.substring(0, idx).trim()
            right = t.substring(idx + sep.length).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) break
        }
    }
    if (left.isEmpty() || right.isEmpty()) return ParsedTrack("", "")
    if (looksLikeJunk(left) || sameStation(left, stationName)) {
        val nested = parseStreamTitle(right, stationName)
        return if (nested.display.isNotBlank()) nested else ParsedTrack("", right)
    }
    if (looksLikeJunk(right)) return ParsedTrack("", "")
    if (left.length in 2..80) return ParsedTrack(left, right)
    return ParsedTrack("", t)
}

/**
 * Виконавець з ICY StreamTitle. Слоган/ефір/назва станції → порожньо (без фото).
 */
fun artistFromTrackTitle(title: String, stationName: String = ""): String {
    return parseStreamTitle(title, stationName).artist
}

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
private const val DISK_FILE = "artist_photo_cache_v2.json"
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
                    val url = o.optString("url", MISS)
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

private fun normName(s: String): String =
    s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()

private fun similarArtist(query: String, result: String): Boolean {
    val q = normName(query)
    val r = normName(result)
    if (q.length < 2 || r.length < 2) return false
    if (q == r) return true
    if (q.length >= 4 && (r.contains(q) || q.contains(r))) return true
    val qt = q.split(" ").filter { it.length > 1 }
    if (qt.isEmpty()) return false
    val rt = r.split(" ").toSet()
    val hit = qt.count { it in rt }
    return hit * 2 >= qt.size && hit >= 1
}

private fun deezerArtistPhoto(artist: String): String? {
    return try {
    val q = URLEncoder.encode(artist, "UTF-8")
    val body = httpGetJson("https://api.deezer.com/search/artist?q=$q&limit=5", "application/json")
    val arr = JSONObject(body).optJSONArray("data") ?: return null
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val name = obj.optString("name")
        if (!similarArtist(artist, name)) continue
        val pic = listOf(
            obj.optString("picture_big"),
            obj.optString("picture_medium"),
            obj.optString("picture"),
        ).firstOrNull { it.isNotBlank() }
        if (pic != null) return pic
    }
    // UA/трансліт: ім'я в Deezer інше — як раніше беремо 1-й хіт
    if (arr.length() > 0) {
        val obj = arr.getJSONObject(0)
        val pic = listOf(
            obj.optString("picture_big"),
            obj.optString("picture_medium"),
            obj.optString("picture"),
        ).firstOrNull { it.isNotBlank() }
        if (pic != null) return pic
    }
    null
} catch (_: Exception) {
        null
    }
}

/** iTunes Search — fallback після Deezer. */
private fun itunesArtistPhoto(artist: String): String? {
    return try {
    val q = URLEncoder.encode(artist, "UTF-8")
    val body = httpGetJson(
        "https://itunes.apple.com/search?term=$q&entity=musicArtist&limit=5",
        "application/json"
    )
    val arr = JSONObject(body).optJSONArray("results") ?: return null
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val name = obj.optString("artistName")
        if (!similarArtist(artist, name)) continue
        val raw = obj.optString("artworkUrl100")
        if (raw.isNotBlank()) {
            return raw.replace("100x100bb", "600x600bb").replace("100x100", "600x600")
        }
    }
    if (arr.length() > 0) {
        val raw = arr.getJSONObject(0).optString("artworkUrl100")
        if (raw.isNotBlank()) {
            return raw.replace("100x100bb", "600x600bb").replace("100x100", "600x600")
        }
    }
    null
} catch (_: Exception) {
        null
    }
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
        val photo = deezerArtistPhoto(artist) ?: itunesArtistPhoto(artist)
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
