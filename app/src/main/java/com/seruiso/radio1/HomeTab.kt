package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun HomeTabContent(
    favRows: List<Station>,
    heartRows: List<LocalTrack>,
    similar: List<Station>,
    similarTitle: String,
    recent: List<Station>,
    nearby: List<Station> = emptyList(),
    genreChips: List<String> = emptyList(),
    favUrls: Set<String> = emptySet(),
    currentName: String = "",
    currentFavicon: String = "",
    acc: Color,
    muted: Color,
    text: Color,
    onAllStations: () -> Unit,
    onAllHeart: () -> Unit,
    onPickRadio: (List<Station>, Int) -> Unit,
    onPickLocal: (List<LocalTrack>, Int) -> Unit,
    onPickOneRadio: (List<Station>, Int) -> Unit = { _, _ -> },
    onPlayNow: () -> Unit,
    onToggleFav: (Station) -> Unit = {},
    onAddToTab: (Station) -> Unit = {},
    onGenreChip: (String) -> Unit = {},
    currentUrl: String = "",
) {
    val recent10 = recent.distinctBy { it.url }.take(10)
    val similar10 = similar.distinctBy { it.url }.take(10)
    val nearby10 = nearby.distinctBy { it.url }.take(10)
    val tile = 88.dp
    val emptyAll = favRows.isEmpty() && heartRows.isEmpty() && recent10.isEmpty()
        && similar10.isEmpty() && nearby10.isEmpty() && currentUrl.isBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        userScrollEnabled = true,
    ) {
        // 1) чіпи завжди зверху
        if (genreChips.isNotEmpty()) {
            item {
                HomeSectionHeader(stringResource(R.string.home_genres), acc, text, onAll = null)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp, bottom = 6.dp),
                ) {
                    items(genreChips, key = { it }) { g ->
                        Text(
                            g,
                            color = text,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .background(Palette.panel, RoundedCornerShape(20.dp))
                                .clickable { onGenreChip(g) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (emptyAll) {
            item { HomeWelcome(muted, text, acc, onAllStations) }
        } else {
            if (recent10.isNotEmpty()) {
                item {
                    HomeSectionHeader(stringResource(R.string.home_recent), acc, text, onAll = null)
                    HomeStationRow(
                        recent10, currentUrl, favUrls, muted, text, acc, tile,
                        onTap = { s ->
                            val i = recent10.indexOfFirst { it.url == s.url }.coerceAtLeast(0)
                            if (s.url != currentUrl) onPickRadio(recent10, i)
                            onPlayNow()
                        },
                        onStar = onToggleFav,
                        onPlus = onAddToTab,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (favRows.isNotEmpty()) {
                item {
                    HomeSectionHeader(stringResource(R.string.home_favorites), acc, text, onAll = onAllStations, icon = Icons.Filled.Star)
                    HomeStationRow(
                        favRows.distinctBy { it.url }, currentUrl, favUrls, muted, text, acc, tile,
                        onTap = { s ->
                            val i = favRows.indexOfFirst { it.url == s.url }
                            if (s.url != currentUrl) onPickRadio(favRows, if (i >= 0) i else 0)
                            onPlayNow()
                        },
                        onStar = onToggleFav,
                        onPlus = onAddToTab,
                    )
                    Spacer(Modifier.height(10.dp))
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
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (nearby10.isNotEmpty()) {
                item {
                    HomeSectionHeader(stringResource(R.string.home_nearby), acc, text, onAll = null)
                    HomeStationRow(
                        nearby10, currentUrl, favUrls, muted, text, acc, tile,
                        onTap = { s ->
                            val i = nearby10.indexOfFirst { it.url == s.url }.coerceAtLeast(0)
                            if (s.url != currentUrl) onPickRadio(nearby10, i)
                            onPlayNow()
                        },
                        onStar = onToggleFav,
                        onPlus = onAddToTab,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (similar10.isNotEmpty()) {
                item {
                    HomeSectionHeader(
                        if (similarTitle.isBlank()) stringResource(R.string.home_similar) else similarTitle,
                        acc, text, onAll = null,
                    )
                    HomeStationRow(
                        similar10, currentUrl, favUrls, muted, text, acc, tile,
                        onTap = { s ->
                            val i = similar10.indexOfFirst { it.url == s.url }.coerceAtLeast(0)
                            if (s.url != currentUrl) onPickRadio(similar10, i)
                            onPlayNow()
                        },
                        onStar = onToggleFav,
                        onPlus = onAddToTab,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeWelcome(muted: Color, text: Color, acc: Color, onAllStations: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_welcome_title), color = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.home_welcome_sub), color = muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.home_welcome_cta),
            color = acc,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp).clickable { onAllStations() },
        )
    }
}

@Composable
private fun HomeSectionHeader(title: String, acc: Color, text: Color, onAll: (() -> Unit)?, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = acc,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(22.dp),
            )
        }
        Text(
            title,
            color = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onAll != null) {
            Text(
                stringResource(R.string.home_all),
                color = acc,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .clickable { onAll() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun HomeStationRow(
    list: List<Station>,
    currentUrl: String,
    favUrls: Set<String>,
    muted: Color,
    text: Color,
    acc: Color,
    tile: Dp,
    onTap: (Station) -> Unit,
    onStar: (Station) -> Unit,
    onPlus: (Station) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(list.distinctBy { it.url }, key = { it.url }) { s ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(tile).clickable { onTap(s) },
            ) {
                Box {
                    HomeArt(s.favicon, s.name, tile, muted, s.url == currentUrl, acc)
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            if (favUrls.contains(s.url)) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = if (favUrls.contains(s.url)) acc else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .clickable { onStar(s) }
                                .padding(2.dp),
                        )
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .clickable { onPlus(s) }
                                .padding(2.dp),
                        )
                    }
                }
                Text(
                    s.name,
                    color = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeLocalGrid(
    list: List<LocalTrack>,
    currentUrl: String,
    muted: Color,
    text: Color,
    tile: Dp,
    onTap: (LocalTrack) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(list.distinctBy { it.uri }, key = { it.uri }) { t ->
            val art = if (t.albumId.isNotBlank() && t.albumId != "0")
                "content://media/external/audio/albumart/${t.albumId}" else ""
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(tile).clickable { onTap(t) },
            ) {
                HomeArt(art, t.title, tile, muted, t.uri == currentUrl, Color.Unspecified)
                Text(
                    t.title,
                    color = text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeArt(art: String, key: String, size: Dp, muted: Color, current: Boolean, acc: Color) {
    val shape = RoundedCornerShape(14.dp)
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
        if (ok) AsyncImage(
            model = resolved,
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        ) else Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted)
    }
}
