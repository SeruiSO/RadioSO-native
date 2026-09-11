package com.seruiso.radio1

// R in same package

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage

@Composable
fun HomeTabContent(
    favRows: List<Station>,
    heartRows: List<LocalTrack>,
    similar: List<Station>,
    similarTitle: String,
    recent: List<Station>,
    acc: Color,
    muted: Color,
    text: Color,
    onAllStations: () -> Unit,
    onAllHeart: () -> Unit,
    onPickRadio: (List<Station>, Int) -> Unit,
    onPickLocal: (List<LocalTrack>, Int) -> Unit,
    onPickOneRadio: (List<Station>, Int) -> Unit = { _, _ -> },
    onPlayNow: () -> Unit,
    currentUrl: String = "",
    nowName: String = "",
    nowGenre: String = "",
    nowArt: String = "",
    nowPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
) {
    val recent10 = recent.take(10)
    val similar10 = similar.take(10)
    val emptyAll = favRows.isEmpty() && heartRows.isEmpty() && recent10.isEmpty() && similar10.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // «Продовжити» прибрано — додаток і так пам'ятає стан і позицію
        if (emptyAll && currentUrl.isBlank()) {
            item {
                HomeWelcome(muted, text, acc, onAllStations)
            }
        } else {
            if (recent10.isNotEmpty()) {
                item {
                    HomeSectionHeader(stringResource(R.string.home_recent), acc, text, onAll = null)
                    HomeStationGrid(recent10, currentUrl, muted, text, tile = 88.dp) { s ->
                        if (s.url != currentUrl) onPickOneRadio(recent10, recent10.indexOfFirst { it.url == s.url }.coerceAtLeast(0))
                        onPlayNow()
                    }
                }
            }
            if (favRows.isNotEmpty()) {
                item {
                    HomeSectionHeader(stringResource(R.string.home_favorites), acc, text, onAll = onAllStations, icon = Icons.Filled.Star)
                    HomeStationGrid(favRows, currentUrl, muted, text, tile = 88.dp) { s ->
                        val i = favRows.indexOfFirst { it.url == s.url }
                        if (s.url != currentUrl) onPickRadio(favRows, if (i >= 0) i else 0)
                        onPlayNow()
                    }
                }
            }
            if (heartRows.isNotEmpty()) {
                item {
                    HomeSectionHeader(stringResource(R.string.home_local_fav), acc, text, onAll = onAllHeart, icon = Icons.Filled.Favorite)
                    HomeLocalGrid(heartRows, currentUrl, muted, text, tile = 72.dp) { t ->
                        val i = heartRows.indexOfFirst { it.uri == t.uri }
                        if (t.uri != currentUrl) onPickLocal(heartRows, if (i >= 0) i else 0)
                        onPlayNow()
                    }
                }
            }
            if (similar10.isNotEmpty()) {
                item {
                    HomeSectionHeader(
                        if (similarTitle.isBlank()) stringResource(R.string.home_similar) else similarTitle,
                        acc, text, onAll = null,
                    )
                    HomeStationGrid(similar10, currentUrl, muted, text, tile = 80.dp) { s ->
                        if (s.url != currentUrl) onPickOneRadio(similar10, similar10.indexOfFirst { it.url == s.url }.coerceAtLeast(0))
                        onPlayNow()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWelcome(muted: Color, text: Color, acc: Color, onAllStations: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Palette.panel, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = acc, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.home_welcome_title), color = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.home_welcome_sub), color = muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.home_welcome_cta),
            color = acc,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(top = 14.dp)
                .background(acc.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .clickable { onAllStations() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun HomeSectionHeader(title: String, acc: Color, text: Color, onAll: (() -> Unit)?, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = acc, modifier = Modifier.size(16.dp).padding(end = 6.dp))
        }
        Text(title, color = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onAll != null) {
            Text(
                stringResource(R.string.home_all),
                color = acc,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable { onAll() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun HomeStationGrid(
    list: List<Station>, currentUrl: String, muted: Color, text: Color, tile: Dp, onTap: (Station) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(list, key = { it.url }) { s ->
            HomeTile(s.favicon, s.name, s.url == currentUrl, muted, text, tile) { onTap(s) }
        }
    }
}

@Composable
private fun HomeLocalGrid(
    list: List<LocalTrack>, currentUrl: String, muted: Color, text: Color, tile: Dp, onTap: (LocalTrack) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(list, key = { it.uri }) { t ->
            val art = if (t.albumId.isNotBlank() && t.albumId != "0")
                "content://media/external/audio/albumart/${t.albumId}" else ""
            HomeTile(art, t.title, t.uri == currentUrl, muted, text, tile) { onTap(t) }
        }
    }
}

@Composable
private fun HomeTile(art: String, label: String, current: Boolean, muted: Color, text: Color, tile: Dp, onTap: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(tile).clickable { onTap() },
    ) {
        HomeArt(art, label, tile, muted, current, acc = Color.Unspecified)
        Text(
            label,
            color = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeArt(art: String, key: String, size: Dp, muted: Color, current: Boolean, acc: Color) {
    val shape = RoundedCornerShape(12.dp)
    val resolved = when {
        art.startsWith("http") && !art.contains("example.com") -> art
        art.startsWith("content:") -> art
        art.isNotBlank() && art != "0" && !art.startsWith("http") ->
            "content://media/external/audio/albumart/$art"
        else -> ""
    }
    Box(
        modifier = Modifier
            .size(size)
            .then(if (current && acc != Color.Unspecified) Modifier.border(2.dp, acc, shape) else Modifier)
            .clip(shape)
            .background(Palette.panel2),
        contentAlignment = Alignment.Center,
    ) {
        val ok = resolved.startsWith("http") || resolved.startsWith("content:")
        if (ok) AsyncImage(model = resolved, contentDescription = null, modifier = Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted)
    }
}
