package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object PodcastStore {
    private const val KEY_SUBS = "podcastSubsJson"

    fun subs(ctx: Context): List<PodcastShow> {
        val raw = prefs(ctx).getString(KEY_SUBS, "[]") ?: "[]"
        val arr = JSONArray(raw)
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
        if (f.isBlank()) return false
        return subs(ctx).any { it.feedUrl == f }
    }

    /** true = тепер підписаний */
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
        save(ctx, cur)
        return now
    }

    private fun save(ctx: Context, list: List<PodcastShow>) {
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

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)

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
            out
        } finally {
            conn.disconnect()
        }
    }
}
