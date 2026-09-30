package com.seruiso.radio1

import android.content.Context
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

    fun isDownloaded(ctx: Context, audioUrl: String): Boolean {
        val f = episodeFile(ctx, audioUrl)
        return f.isFile && f.length() > 1024
    }

    fun download(ctx: Context, audioUrl: String): Result<File> = runCatching {
        val out = episodeFile(ctx, audioUrl)
        if (out.isFile && out.length() > 1024) return@runCatching out
        val tmp = File(out.absolutePath + ".part")
        val conn = (URL(audioUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RadioSO/1.0 (podcast-dl)")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            // index meta for "Downloaded" section
            rememberDownload(ctx, audioUrl)
            out
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
    ) {
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
                .put("duration", duration),
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

    fun savePos(ctx: Context, audioUrl: String, ms: Long) {
        val u = audioUrl.trim()
        if (u.isBlank() || ms < 0L) return
        try {
            val o = org.json.JSONObject(prefs(ctx).getString(BluetoothAutoPlayPlugin.KEY_POD_POS, "{}") ?: "{}")
            o.put(u, ms)
            prefs(ctx).edit().putString(BluetoothAutoPlayPlugin.KEY_POD_POS, o.toString()).apply()
        } catch (_: Exception) {}
    }

    fun deleteDownload(ctx: Context, audioUrl: String) {
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

data class PodcastRecent(
    val title: String,
    val audioUrl: String,
    val mediaUrl: String,
    val showTitle: String,
    val artwork: String,
    val duration: String,
)
