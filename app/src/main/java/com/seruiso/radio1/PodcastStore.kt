package com.seruiso.radio1

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class PodcastFavEpisode(
    val title: String,
    val audioUrl: String,
    val showTitle: String = "",
    val artwork: String = "",
    val pubDate: String = "",
    val duration: String = "",
)

object PodcastStore {
    private const val KEY_SUBS = "podcastSubsJson"
    private const val KEY_FAV_EPS = "podcastFavEpsJson"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)

    // ── shows (підписки) ─────────────────────────────────
    fun subs(ctx: Context): List<PodcastShow> {
        val arr = JSONArray(prefs(ctx).getString(KEY_SUBS, "[]") ?: "[]")
        val out = ArrayList<PodcastShow>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val feed = o.optString("feedUrl").trim()
            val title = o.optString("title").trim()
            if (feed.isBlank() || title.isBlank()) continue
            out.add(
                PodcastShow(
                    id = o.optLong("id"),
                    title = title,
                    author = o.optString("author").trim(),
                    feedUrl = feed,
                    artwork = o.optString("artwork").trim(),
                    trackCount = o.optInt("trackCount", 0),
                ),
            )
        }
        return out
    }

    fun isSub(ctx: Context, feedUrl: String): Boolean {
        val f = feedUrl.trim()
        return f.isNotBlank() && subs(ctx).any { it.feedUrl == f }
    }

    fun toggle(ctx: Context, show: PodcastShow): Boolean {
        val cur = subs(ctx).toMutableList()
        val idx = cur.indexOfFirst { it.feedUrl == show.feedUrl }
        val now: Boolean
        if (idx >= 0) {
            cur.removeAt(idx)
            now = false
        } else {
            cur.add(0, show)
            now = true
        }
        saveSubs(ctx, cur)
        return now
    }

    private fun saveSubs(ctx: Context, list: List<PodcastShow>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("author", it.author)
                    .put("feedUrl", it.feedUrl)
                    .put("artwork", it.artwork)
                    .put("trackCount", it.trackCount),
            )
        }
        prefs(ctx).edit().putString(KEY_SUBS, arr.toString()).apply()
    }

    // ── favorite episodes ────────────────────────────────
    fun favEpisodes(ctx: Context): List<PodcastFavEpisode> {
        val arr = JSONArray(prefs(ctx).getString(KEY_FAV_EPS, "[]") ?: "[]")
        val out = ArrayList<PodcastFavEpisode>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("audioUrl").trim()
            val title = o.optString("title").trim()
            if (url.isBlank() || title.isBlank()) continue
            out.add(
                PodcastFavEpisode(
                    title = title,
                    audioUrl = url,
                    showTitle = o.optString("showTitle").trim(),
                    artwork = o.optString("artwork").trim(),
                    pubDate = o.optString("pubDate").trim(),
                    duration = o.optString("duration").trim(),
                ),
            )
        }
        return out
    }

    fun isFavEpisode(ctx: Context, audioUrl: String): Boolean {
        val u = audioUrl.trim()
        return u.isNotBlank() && favEpisodes(ctx).any { it.audioUrl == u }
    }

    fun toggleFavEpisode(ctx: Context, ep: PodcastFavEpisode): Boolean {
        val cur = favEpisodes(ctx).toMutableList()
        val idx = cur.indexOfFirst { it.audioUrl == ep.audioUrl }
        val now: Boolean
        if (idx >= 0) {
            cur.removeAt(idx)
            now = false
        } else {
            cur.add(0, ep)
            now = true
        }
        val arr = JSONArray()
        cur.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("audioUrl", it.audioUrl)
                    .put("showTitle", it.showTitle)
                    .put("artwork", it.artwork)
                    .put("pubDate", it.pubDate)
                    .put("duration", it.duration),
            )
        }
        prefs(ctx).edit().putString(KEY_FAV_EPS, arr.toString()).apply()
        return now
    }

    // ── downloads ────────────────────────────────────────
    fun episodeFile(ctx: Context, audioUrl: String): File {
        val dir = File(ctx.filesDir, "podcasts").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(audioUrl.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val ext = when {
            audioUrl.contains(".m4a", true) -> "m4a"
            audioUrl.contains(".aac", true) -> "aac"
            audioUrl.contains(".ogg", true) -> "ogg"
            audioUrl.contains(".mp4", true) -> "mp4"
            else -> "mp3"
        }
        return File(dir, "$hash.$ext")
    }

    private val dlOk = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun storedUri(ctx: Context, audioUrl: String): String {
        val arr = JSONArray(prefs(ctx).getString(KEY_DL_META, "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("audioUrl") == audioUrl) return o.optString("localUri")
        }
        return ""
    }

    fun localPath(ctx: Context, audioUrl: String): String {
        dlOk[audioUrl]?.let { if (it.isNotBlank()) return it }
        val uri = storedUri(ctx, audioUrl)
        if (uri.startsWith("content:")) {
            val ok = try {
                ctx.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { it.statSize > 1024L } == true
            } catch (_: Exception) {
                false
            }
            if (ok) {
                dlOk[audioUrl] = uri
                return uri
            }
        }
        val f = episodeFile(ctx, audioUrl)
        if (f.isFile && f.length() > 1024) {
            dlOk[audioUrl] = f.absolutePath
            return f.absolutePath
        }
        return ""
    }

    fun isDownloaded(ctx: Context, audioUrl: String): Boolean = localPath(ctx, audioUrl).isNotBlank()

    fun publishToMusic(ctx: Context, file: File, title: String, show: String): String {
        return try {
            val ext = file.extension.ifBlank { "mp3" }
            val mime = when (ext) {
                "m4a", "mp4" -> "audio/mp4"
                "aac" -> "audio/aac"
                "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }
            val base = listOf(show, title).filter { it.isNotBlank() }.joinToString(" - ")
                .replace(Regex("[\\/:*?\"<>|]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(70)
                .ifBlank { file.nameWithoutExtension }
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$base.$ext")
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/RadioSO")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.TITLE, title.ifBlank { base })
                put(MediaStore.Audio.Media.ARTIST, show.ifBlank { "RadioSO" })
                put(MediaStore.Audio.Media.ALBUM, "RadioSO")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val uri = ctx.contentResolver.insert(collection, values) ?: return ""
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return ""
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues()
                done.put(MediaStore.Audio.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, done, null, null)
            }
            uri.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun openDl(url: String): HttpURLConnection {
        var current = url
        for (hop in 0 until 5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "RadioSO/1.0 (podcast-dl)")
            }
            val code = try { conn.responseCode } catch (e: Exception) { conn.disconnect(); throw e }
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("redirect")
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                continue
            }
            return conn
        }
        error("redirect")
    }

    fun download(ctx: Context, audioUrl: String): Result<File> = runCatching {
        val out = episodeFile(ctx, audioUrl)
        if (out.isFile && out.length() > 1024) return@runCatching out
        val tmp = File(out.absolutePath + ".part")
        if (cancelled.contains(audioUrl)) error("cancel")
        val conn = openDl(audioUrl)
        try {
            val code = conn.responseCode
            if (code !in 200..299 && code != 206) error("HTTP $code")
            val total = conn.contentLengthLong
            var got = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { outS ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        if (cancelled.contains(audioUrl)) error("cancel")
                        val n = input.read(buf)
                        if (n < 0) break
                        outS.write(buf, 0, n)
                        got += n
                        if (total > 0) fracs[audioUrl] = (got.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            fracs.remove(audioUrl)
            if (total > 0 && got + 2048 < total) {
                tmp.delete()
                error("short")
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            out
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private const val KEY_DL_META = "podcastDlMetaJson"

    fun rememberDownload(
        ctx: Context,
        audioUrl: String,
        title: String = "",
        showTitle: String = "",
        artwork: String = "",
        pubDate: String = "",
        duration: String = "",
        localUri: String = "",
    ) {
        if (localUri.isNotBlank()) dlOk[audioUrl] = localUri
        val arr = JSONArray(prefs(ctx).getString(KEY_DL_META, "[]") ?: "[]")
        val out = JSONArray()
        // newest first
        out.put(
            JSONObject()
                .put("audioUrl", audioUrl)
                .put("title", title)
                .put("showTitle", showTitle)
                .put("artwork", artwork)
                .put("pubDate", pubDate)
                .put("duration", duration)
                .put("localUri", localUri),
        )
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("audioUrl") == audioUrl) continue
            out.put(o)
        }
        prefs(ctx).edit().putString(KEY_DL_META, out.toString()).apply()
    }

    fun downloadedList(ctx: Context): List<PodcastFavEpisode> {
        val arr = JSONArray(prefs(ctx).getString(KEY_DL_META, "[]") ?: "[]")
        val out = ArrayList<PodcastFavEpisode>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("audioUrl").trim()
            if (url.isBlank() || !isDownloaded(ctx, url)) continue
            out.add(
                PodcastFavEpisode(
                    title = o.optString("title").ifBlank { url.substringAfterLast('/') },
                    audioUrl = url,
                    showTitle = o.optString("showTitle"),
                    artwork = o.optString("artwork"),
                    pubDate = o.optString("pubDate"),
                    duration = o.optString("duration"),
                ),
            )
        }
        return out
    }

    val io = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    private val fracs = java.util.concurrent.ConcurrentHashMap<String, Float>()
    private val waiting = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val dlq = java.util.concurrent.ConcurrentLinkedQueue<DlJob>()
    private val pumping = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile var busyUrl: String = ""

    fun cancel(url: String) {
        val u = url.trim()
        if (u.isBlank()) return
        cancelled.add(u)
        waiting.remove(u)
        fracs.remove(u)
        val left = ArrayList<DlJob>()
        while (true) {
            val j = dlq.poll() ?: break
            if (j.url != u) left.add(j)
        }
        left.forEach { dlq.add(it) }
    }

    fun isBusy(url: String) = url.isNotBlank() && (busyUrl == url || waiting.contains(url))
    fun anyBusy() = busyUrl.isNotBlank() || waiting.isNotEmpty()
    fun fracOf(url: String): Float = fracs[url] ?: -1f

    fun enqueue(
        ctx: Context,
        audioUrl: String,
        title: String,
        showTitle: String,
        artwork: String,
        pubDate: String,
        duration: String,
        done: (String?) -> Unit,
    ) {
        val u = audioUrl.trim()
        cancelled.remove(u)
        if (u.isBlank() || isDownloaded(ctx, u) || !waiting.add(u)) return
        dlq.add(DlJob(ctx.applicationContext, u, title, showTitle, artwork, pubDate, duration, done))
        pump()
    }

    private fun pump() {
        if (!pumping.compareAndSet(false, true)) return
        io.launch {
            try {
                while (true) {
                    val job = dlq.poll() ?: break
                    busyUrl = job.url
                    val err = download(job.ctx, job.url).fold(
                        onSuccess = { file ->
                            val uri = publishToMusic(job.ctx, file, job.title, job.show)
                            if (uri.startsWith("content:")) file.delete()
                            rememberDownload(job.ctx, job.url, job.title, job.show, job.art, job.pub, job.dur, uri)
                            null
                        },
                        onFailure = { if (it.message == "cancel") null else it.message ?: "download" },
                    )
                    waiting.remove(job.url)
                    fracs.remove(job.url)
                    busyUrl = ""
                    job.done(err)
                }
            } finally {
                pumping.set(false)
                if (dlq.isNotEmpty()) pump()
            }
        }
    }

    private const val KEY_FEED_CACHE = "podcastFeedCache"

    fun cacheShow(ctx: Context, feedUrl: String, showTitle: String, artwork: String, eps: List<PodcastEpisode>) {
        val feed = feedUrl.trim()
        if (feed.isBlank()) return
        val root = org.json.JSONObject(prefs(ctx).getString(KEY_FEED_CACHE, "{}") ?: "{}")
        val arr = JSONArray()
        eps.take(25).forEach { e ->
            arr.put(
                JSONObject()
                    .put("title", e.title)
                    .put("audioUrl", e.audioUrl)
                    .put("pubDate", e.pubDate)
                    .put("duration", e.duration)
                    .put("image", e.image.ifBlank { artwork })
                    .put("description", e.description)
                    .put("showTitle", showTitle),
            )
        }
        root.put(feed, JSONObject().put("title", showTitle).put("artwork", artwork).put("eps", arr))
        prefs(ctx).edit().putString(KEY_FEED_CACHE, root.toString()).apply()
    }

    fun news(ctx: Context): List<PodcastEpisode> {
        val allowed = subs(ctx).map { it.feedUrl.trim() }.filter { it.isNotBlank() }.toSet()
        val root = org.json.JSONObject(prefs(ctx).getString(KEY_FEED_CACHE, "{}") ?: "{}")
        val out = ArrayList<PodcastEpisode>()
        val drop = ArrayList<String>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val feed = keys.next()
            if (feed !in allowed) {
                drop.add(feed)
                continue
            }
            val o = root.optJSONObject(feed) ?: continue
            val eps = o.optJSONArray("eps") ?: continue
            val n = minOf(4, eps.length())
            for (i in 0 until n) {
                val e = eps.optJSONObject(i) ?: continue
                val url = e.optString("audioUrl").trim()
                if (url.isBlank()) continue
                out.add(
                    PodcastEpisode(
                        title = e.optString("title"),
                        audioUrl = url,
                        pubDate = e.optString("pubDate"),
                        duration = e.optString("duration"),
                        image = e.optString("image"),
                        description = e.optString("showTitle"),
                    ),
                )
            }
        }
        if (drop.isNotEmpty()) {
            drop.forEach { root.remove(it) }
            prefs(ctx).edit().putString(KEY_FEED_CACHE, root.toString()).apply()
        }
        return out.take(40)
    }

    fun pos(ctx: Context, audioUrl: String): Long {
        val u = audioUrl.trim()
        if (u.isBlank()) return 0L
        return try {
            org.json.JSONObject(prefs(ctx).getString(BluetoothAutoPlayPlugin.KEY_POD_POS, "{}") ?: "{}")
                .optLong(u, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    fun savePos(ctx: Context, audioUrl: String, ms: Long) = savePos(ctx, audioUrl, ms, 0L)

    fun savePos(ctx: Context, audioUrl: String, ms: Long, durMs: Long) {
        val u = audioUrl.trim()
        if (u.isBlank() || ms < 0L) return
        try {
            val keys = linkedSetOf<String>()
            fun add(raw: String) {
                val s = raw.trim()
                if (s.isBlank()) return
                keys.add(s)
                if (s.startsWith("file://")) keys.add(s.removePrefix("file://"))
                else if (s.startsWith("/")) keys.add("file://$s")
            }
            add(u)
            for (r in recent(ctx)) {
                if (r.audioUrl == u || r.mediaUrl == u) {
                    add(r.audioUrl)
                    add(r.mediaUrl)
                }
            }
            val o = org.json.JSONObject(prefs(ctx).getString(BluetoothAutoPlayPlugin.KEY_POD_POS, "{}") ?: "{}")
            for (k in keys) o.put(k, ms)
            val ed = prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_POD_POS, o.toString())
            if (durMs > 1000L) {
                val d = org.json.JSONObject(prefs(ctx).getString("podDurJson", "{}") ?: "{}")
                for (k in keys) d.put(k, durMs)
                ed.putString("podDurJson", d.toString())
            }
            if (ms <= 1500L) ed.commit() else ed.apply()
        } catch (_: Exception) {}
    }

    fun progressMs(ctx: Context, audioUrl: String, extra: String = ""): Long {
        var best = 0L
        for (k in progressKeys(ctx, audioUrl, extra)) {
            val v = pos(ctx, k)
            if (v > best) best = v
        }
        return best
    }

    fun progressDur(ctx: Context, audioUrl: String, extra: String = ""): Long {
        val root = try {
            org.json.JSONObject(prefs(ctx).getString("podDurJson", "{}") ?: "{}")
        } catch (_: Exception) {
            return 0L
        }
        var best = 0L
        for (k in progressKeys(ctx, audioUrl, extra)) {
            val v = root.optLong(k, 0L)
            if (v > best) best = v
        }
        return best
    }

    private fun progressKeys(ctx: Context, audioUrl: String, extra: String): Set<String> {
        val keys = linkedSetOf<String>()
        fun add(raw: String) {
            val s = raw.trim()
            if (s.isBlank()) return
            keys.add(s)
            if (s.startsWith("file://")) keys.add(s.removePrefix("file://"))
            else if (s.startsWith("/")) keys.add("file://$s")
            if (s.startsWith("http")) {
                val f = episodeFile(ctx, s)
                if (f.isFile && f.length() > 1024) {
                    keys.add(f.absolutePath)
                    keys.add("file://${f.absolutePath}")
                }
            }
        }
        add(audioUrl)
        add(extra)
        for (r in recent(ctx)) {
            if (r.audioUrl == audioUrl || r.mediaUrl == audioUrl || r.mediaUrl == extra || r.audioUrl == extra) {
                add(r.audioUrl)
                add(r.mediaUrl)
            }
        }
        return keys
    }

    fun deleteDownload(ctx: Context, audioUrl: String) {
        val uri = storedUri(ctx, audioUrl)
        if (uri.startsWith("content:")) {
            try { ctx.contentResolver.delete(android.net.Uri.parse(uri), null, null) } catch (_: Exception) {}
        }
        dlOk.remove(audioUrl)
        try { episodeFile(ctx, audioUrl).delete() } catch (_: Exception) {}
        try { File(episodeFile(ctx, audioUrl).absolutePath + ".part").delete() } catch (_: Exception) {}
        val arr = JSONArray(prefs(ctx).getString(KEY_DL_META, "[]") ?: "[]")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("audioUrl") == audioUrl) continue
            out.put(o)
        }
        prefs(ctx).edit().putString(KEY_DL_META, out.toString()).apply()
    }

    private const val KEY_RECENT_EP = "podcastRecentEps"

    fun notePlay(
        ctx: Context,
        title: String,
        audioUrl: String,
        mediaUrl: String,
        showTitle: String,
        artwork: String,
        duration: String,
    ) {
        val u = audioUrl.trim()
        if (u.isBlank()) return
        val arr = JSONArray(prefs(ctx).getString(KEY_RECENT_EP, "[]") ?: "[]")
        val out = JSONArray()
        out.put(
            JSONObject()
                .put("title", title)
                .put("audioUrl", u)
                .put("mediaUrl", mediaUrl)
                .put("showTitle", showTitle)
                .put("artwork", artwork)
                .put("duration", duration),
        )
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("audioUrl") == u) continue
            out.put(o)
            if (out.length() >= 8) break
        }
        prefs(ctx).edit().putString(KEY_RECENT_EP, out.toString()).apply()
    }

    fun recent(ctx: Context): List<PodcastRecent> {
        val arr = JSONArray(prefs(ctx).getString(KEY_RECENT_EP, "[]") ?: "[]")
        val out = ArrayList<PodcastRecent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("audioUrl").trim()
            if (url.isBlank()) continue
            out.add(
                PodcastRecent(
                    title = o.optString("title").ifBlank { url.substringAfterLast('/') },
                    audioUrl = url,
                    mediaUrl = o.optString("mediaUrl").ifBlank { url },
                    showTitle = o.optString("showTitle"),
                    artwork = o.optString("artwork"),
                    duration = o.optString("duration"),
                ),
            )
            if (out.size >= 3) break
        }
        return out
    }

    private const val KEY_PLAYED = "podcastPlayedUrls"

    fun played(ctx: Context): Set<String> {
        val arr = JSONArray(prefs(ctx).getString(KEY_PLAYED, "[]") ?: "[]")
        val out = HashSet<String>()
        for (i in 0 until arr.length()) {
            val u = arr.optString(i).trim()
            if (u.isNotBlank()) out.add(u)
        }
        return out
    }

    fun markPlayed(ctx: Context, audioUrl: String) {
        val u = audioUrl.trim()
        if (u.isBlank()) return
        val keys = linkedSetOf(u)
        for (r in recent(ctx)) {
            if (r.audioUrl == u || r.mediaUrl == u) {
                if (r.audioUrl.isNotBlank()) keys.add(r.audioUrl)
                if (r.mediaUrl.isNotBlank()) keys.add(r.mediaUrl)
            }
        }
        val cur = played(ctx).toMutableSet()
        var changed = false
        for (k in keys) if (cur.add(k)) changed = true
        if (!changed) return
        val arr = JSONArray()
        cur.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_PLAYED, arr.toString()).apply()
    }

    fun togglePlayed(ctx: Context, audioUrl: String): Boolean {
        val u = audioUrl.trim()
        if (u.isBlank()) return false
        val cur = played(ctx).toMutableSet()
        val now = if (u in cur) {
            cur.remove(u)
            false
        } else {
            cur.add(u)
            true
        }
        val arr = JSONArray()
        cur.forEach { arr.put(it) }
        prefs(ctx).edit().putString(KEY_PLAYED, arr.toString()).apply()
        return now
    }
}

private class DlJob(
    val ctx: Context,
    val url: String,
    val title: String,
    val show: String,
    val art: String,
    val pub: String,
    val dur: String,
    val done: (String?) -> Unit,
)

data class PodcastRecent(
    val title: String,
    val audioUrl: String,
    val mediaUrl: String,
    val showTitle: String,
    val artwork: String,
    val duration: String,
)
