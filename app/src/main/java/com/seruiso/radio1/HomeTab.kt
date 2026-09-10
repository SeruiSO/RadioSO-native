package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    sleepLabel: String,
    btWatch: Boolean,
    onSleep: () -> Unit,
    onBt: () -> Unit,
    onTheme: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    currentUrl: String = "",
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            HomeSectionHeader("Станції додані до улюблених", acc, text, onAll = onAllStations, icon = Icons.Filled.Star)
            if (favRows.isEmpty()) HomeEmpty("додайте станції до улюблених", muted, Icons.Filled.Star) else HomeStationGrid(favRows, muted, text) { s ->
                val i = favRows.indexOfFirst { it.url == s.url }
                if (s.url != currentUrl) onPickRadio(favRows, if (i >= 0) i else 0)
                onPlayNow()
            }
        }
        item {
            HomeSectionHeader("Обрана локальна музика", acc, text, onAll = onAllHeart, icon = Icons.Filled.Favorite)
            if (heartRows.isEmpty()) HomeEmpty("додайте локальну музику до улюблених", muted, Icons.Filled.Favorite) else HomeLocalGrid(heartRows, muted, text) { t ->
                val i = heartRows.indexOfFirst { it.uri == t.uri }
                if (t.uri != currentUrl) onPickLocal(heartRows, if (i >= 0) i else 0)
                onPlayNow()
            }
        }
        item {
            HomeSectionHeader(similarTitle, acc, text, onAll = null)
            val similar10 = similar.take(10)
            if (similar10.isEmpty()) HomeEmpty("поки порожньо", muted) else HomeStationGrid(similar10, muted, text) { s ->
                if (s.url != currentUrl) onPickOneRadio(similar10, similar10.indexOfFirst { it.url == s.url }.coerceAtLeast(0))
                onPlayNow()
            }
        }
        item {
            HomeSectionHeader("Історія", acc, text, onAll = null)
            val recent10 = recent.take(10)
            if (recent10.isEmpty()) HomeEmpty("поки порожньо", muted) else HomeStationGrid(recent10, muted, text) { s ->
                if (s.url != currentUrl) onPickOneRadio(recent10, recent10.indexOfFirst { it.url == s.url }.coerceAtLeast(0))
                onPlayNow()
            }
        }
        item { HomeSettingsRow1(sleepLabel, btWatch, acc, text, onSleep, onBt, onTheme) }
        item { HomeSettingsRow2(text, onExport, onImport) }
    }
}

@Composable
private fun HomeSectionHeader(title: String, acc: Color, text: Color, onAll: (() -> Unit)?, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = acc, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        }
        Text(title, color = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onAll != null) {
            Text(
                "Усі",
                color = acc,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clickable { onAll() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HomeEmpty(hint: String, muted: Color, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(bottom = 10.dp)
            .background(Palette.panel.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp).padding(end = 8.dp))
        }
        Text(hint, color = muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeStationGrid(list: List<Station>, muted: Color, text: Color, onTap: (Station) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(list, key = { it.url }) { s ->
            HomeIcon(s.favicon, s.name, muted, text) { onTap(s) }
        }
    }
}

@Composable
private fun HomeLocalGrid(list: List<LocalTrack>, muted: Color, text: Color, onTap: (LocalTrack) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(list, key = { it.uri }) { t ->
            val art = if (t.albumId.isNotBlank() && t.albumId != "0")
                "content://media/external/audio/albumart/${t.albumId}" else ""
            HomeIcon(art, t.title, muted, text) { onTap(t) }
        }
    }
}

@Composable
private fun HomeIcon(art: String, label: String, muted: Color, text: Color, onTap: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable { onTap() },
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(Palette.panel2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val ok = (art.startsWith("http") && !art.contains("example.com")) || art.startsWith("content:")
            if (ok) AsyncImage(model = art, contentDescription = null, modifier = Modifier.size(56.dp).clip(AppShapes.card), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, contentDescription = null, tint = muted)
        }
        Text(label, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HomeSettingsRow1(
    sleepLabel: String, btWatch: Boolean, acc: Color, text: Color,
    onSleep: () -> Unit, onBt: () -> Unit, onTheme: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f).background(Palette.panel, RoundedCornerShape(12.dp)).clickable { onSleep() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Timer, null, tint = text, modifier = Modifier.size(18.dp))
                Text(sleepLabel, color = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(modifier = Modifier.weight(1f).background(if (btWatch) acc.copy(alpha = 0.25f) else Palette.panel, RoundedCornerShape(12.dp)).clickable { onBt() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (btWatch) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, null, tint = if (btWatch) acc else text, modifier = Modifier.size(18.dp))
                Text(if (btWatch) "BT: увімкнено" else "BT: вимкнено", color = if (btWatch) acc else text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Box(modifier = Modifier.weight(0.75f).background(Palette.panel, RoundedCornerShape(12.dp)).clickable { onTheme() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DarkMode, contentDescription = "Обрати тему оформлення", tint = acc)
        }
    }
}

@Composable
private fun HomeSettingsRow2(text: Color, onExport: () -> Unit, onImport: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f).background(Palette.panel, RoundedCornerShape(12.dp)).clickable { onExport() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.FileUpload, contentDescription = "Експорт налаштувань", tint = text, modifier = Modifier.size(18.dp))
                Text("Експорт", color = text, style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(modifier = Modifier.weight(1f).background(Palette.panel, RoundedCornerShape(12.dp)).clickable { onImport() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Імпорт налаштувань", tint = text, modifier = Modifier.size(18.dp))
                Text("Імпорт", color = text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
