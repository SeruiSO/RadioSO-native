package com.seruiso.radio1

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PodcastShow(
    val id: Long,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artwork: String,
    val trackCount: Int,
)

@Composable
fun PodcastsTabContent(
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastShow>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) {
            error = ctx.getString(R.string.podcast_empty_query)
            return
        }
        kb?.hide()
        loading = true
        error = ""
        status = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { ItunesPodcasts.searchUa(q) }
            loading = false
            r.onSuccess {
                results = it
                status = if (it.isEmpty()) ctx.getString(R.string.nothing_found)
                else ctx.getString(R.string.podcast_found, it.size)
            }.onFailure {
                results = emptyList()
                error = it.message ?: ctx.getString(R.string.scan_error)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            ctx.getString(R.string.nav_podcasts),
            color = acc,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            ctx.getString(R.string.podcast_hint),
            color = muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(ctx.getString(R.string.podcast_search_hint), color = muted) },
            trailingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = ctx.getString(R.string.find),
                    tint = acc,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { doSearch() },
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = text,
                unfocusedTextColor = text,
                focusedBorderColor = acc,
                unfocusedBorderColor = muted.copy(alpha = 0.4f),
                cursorColor = acc,
            ),
            shape = RoundedCornerShape(14.dp),
        )
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = acc, modifier = Modifier.size(36.dp))
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFE57373), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (status.isNotBlank() && !loading) {
            Text(status, color = muted, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { show ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(card)
                        .clickable {
                            if (show.feedUrl.isNotBlank()) {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(show.feedUrl))
                                    ctx.startActivity(i)
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.panel2),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (show.artwork.isNotBlank()) {
                            AsyncImage(
                                model = show.artwork,
                                contentDescription = show.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Filled.Podcasts, null, tint = muted, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            show.title,
                            color = text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (show.author.isNotBlank()) {
                            Text(
                                show.author,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        val sub = buildString {
                            if (show.trackCount > 0) append(ctx.getString(R.string.podcast_episodes, show.trackCount))
                            if (show.feedUrl.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("RSS")
                            }
                        }
                        if (sub.isNotBlank()) {
                            Text(sub, color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

object ItunesPodcasts {
    fun searchUa(term: String): Result<List<PodcastShow>> = runCatching {
        val enc = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val url = "https://itunes.apple.com/search?term=$enc&media=podcast&entity=podcast&country=ua&limit=40"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "RadioSO/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
            val out = ArrayList<PodcastShow>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val feed = o.optString("feedUrl").trim()
                val title = o.optString("collectionName").ifBlank { o.optString("trackName") }.trim()
                if (title.isBlank()) continue
                out.add(
                    PodcastShow(
                        id = o.optLong("collectionId", o.optLong("trackId")),
                        title = title,
                        author = o.optString("artistName").trim(),
                        feedUrl = feed,
                        artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") },
                        trackCount = o.optInt("trackCount", 0),
                    ),
                )
            }
            out
        } finally {
            conn.disconnect()
        }
    }
}
