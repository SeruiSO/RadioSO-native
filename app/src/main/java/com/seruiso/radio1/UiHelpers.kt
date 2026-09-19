package com.seruiso.radio1

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

/** Album art / favicon URL (http or MediaStore albumId). */
fun artUrl(raw: String): String {
    if (raw.startsWith("http")) return raw
    if (raw.isNotBlank() && raw != "0" && raw.all { it.isDigit() }) {
        return "content://media/external/audio/albumart/$raw"
    }
    return raw
}

@Composable
fun PlayBtn(
    playing: Boolean,
    status: String,
    sizeDp: Dp,
    onClick: () -> Unit,
    accent: Color,
    shape: Shape = AppShapes.hero,
) {
    val st = status.lowercase()
    val busy = !playing && (
        st.contains("підключ") || st.contains(LocalContext.current.getString(R.string.buffer)) || st == "запуск"
    )
    val pulseOn = playing || busy
    val infinite = rememberInfiniteTransition(label = "playPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (busy) 1.09f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (busy) 420 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playPulseSc"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressSc by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "playPress"
    )
    val sc = (if (pulseOn) pulse else 1f) * pressSc
    Box(
        modifier = Modifier
            .size(sizeDp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .background(accent, shape)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(sizeDp * 0.38f),
                color = Color(0xFF0A0A0C),
                strokeWidth = 2.5.dp
            )
            playing -> Icon(
                Icons.Filled.Pause,
                contentDescription = LocalContext.current.getString(R.string.pause),
                tint = Color(0xFF0A0A0C),
                modifier = Modifier.size(sizeDp * 0.42f)
            )
            else -> Icon(
                Icons.Filled.PlayArrow,
                contentDescription = LocalContext.current.getString(R.string.play),
                tint = Color(0xFF0A0A0C),
                modifier = Modifier.size(sizeDp * 0.42f)
            )
        }
    }
}

/** Spring scale on press — prev/next, fav, etc. */
@Composable
fun Modifier.springPress(pressedScale: Float = 0.88f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "springPress"
    )
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Підписи вкладок (UA) — top-level, щоб StationScreen теж бачив */
fun tabLabel(ctx: android.content.Context, tab: String): String = when (tab.lowercase()) {
    "fav" -> ctx.getString(R.string.favorites)
    "best" -> ctx.getString(R.string.local_best_short)
    "local" -> ctx.getString(R.string.tab_local)
    "search" -> ctx.getString(R.string.nav_search)
    "ukraine", "ua" -> "UA"
    "techno" -> "Techno"
    "trance" -> "Trance"
    "pop" -> "Pop"
    else -> tab.replaceFirstChar { it.uppercase() }
}

/** Статус ефіру для інфо-панелі; решта йде в тост. */
/** Статус ефіру для інфо-панелі; усе інше (пошук, вкладки, тема…) — тост. */
fun playbackInfoText(ctx: android.content.Context, status: String): String? {
    val raw = status.trim()
    val x = raw.lowercase()
    if (x.isEmpty() || x == ctx.getString(R.string.done).lowercase()) return null
    val attempt = Regex("""#\s*(\d+)""").find(raw)?.groupValues?.getOrNull(1)
    fun withAttempt(label: String): String =
        if (attempt != null) "$label #$attempt" else label
    return when {
        x.startsWith("відтвор") -> ctx.getString(R.string.playing_cap)
        x == "пауза" || x.contains(ctx.getString(R.string.sleep_pause).lowercase()) ->
            ctx.getString(R.string.pause)
        x.startsWith("стоп") -> ctx.getString(R.string.stop)
        x.contains("буфер") || x.contains(ctx.getString(R.string.status_buffering).lowercase()) ->
            withAttempt(ctx.getString(R.string.buffer_cap))
        x.contains("повторн") || x.contains(ctx.getString(R.string.status_reconnect).lowercase()) ->
            withAttempt(ctx.getString(R.string.status_reconnect))
        x.contains("немає мереж") || x.contains(ctx.getString(R.string.status_no_network).lowercase()) ->
            withAttempt(ctx.getString(R.string.status_no_network))
        x.startsWith("підключ") || x.contains(ctx.getString(R.string.connecting).lowercase()) ->
            withAttempt(ctx.getString(R.string.connecting_cap))
        x == "запуск" -> ctx.getString(R.string.start)
        // номер спроби лише разом з відомим ефірним статусом уже оброблено вище
        else -> null
    }
}


/** Злити два знімки станції: непорожній favicon/genre/country ніколи не затирається порожнім. */
fun preferRichStation(a: Station, b: Station): Station {
    val favicon = when {
        a.favicon.isNotBlank() && b.favicon.isNotBlank() ->
            // обидва є — лишаємо довший/http (часто краща якість)
            if (b.favicon.startsWith("http") && !a.favicon.startsWith("http")) b.favicon
            else if (b.favicon.length > a.favicon.length) b.favicon
            else a.favicon
        a.favicon.isNotBlank() -> a.favicon
        else -> b.favicon
    }
    val name = if (b.name.length > a.name.length) b.name else a.name
    val genre = a.genre.ifBlank { b.genre }
    val country = a.country.ifBlank { b.country }
    return Station(a.url, name, genre.ifBlank { b.genre }, country.ifBlank { b.country }, favicon, a.tab)
}

/** distinctBy збагаченням meta замість «перший виграв». Порядок першої появи зберігається. */
/**
 * Ключ потоку: той самий Icecast/HTTP з http↔https, слешем, :80/:443, /; —
 * вважаємо однією станцією. Шлях лишаємо (різні /stream vs /128).
 */
fun streamIdentity(url: String): String {
    var u = url.trim()
    if (u.isEmpty()) return ""
    try {
        val uri = android.net.Uri.parse(u)
        val scheme = (uri.scheme ?: "http").lowercase()
        var host = (uri.host ?: "").lowercase().removePrefix("www.")
        var port = uri.port
        if (port == 80 && scheme == "http") port = -1
        if (port == 443 && scheme == "https") port = -1
        var path = uri.path ?: ""
        while (path.endsWith("/")) path = path.dropLast(1)
        if (path.endsWith("/;")) path = path.dropLast(2)
        if (path == ";") path = ""
        val portPart = if (port > 0) ":$port" else ""
        return host + portPart + path.lowercase()
    } catch (_: Exception) {
        u = u.lowercase()
        u = u.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        u = u.replace(":80/", "/").replace(":443/", "/")
        val q = u.indexOf('?')
        if (q >= 0) u = u.substring(0, q)
        val h = u.indexOf('#')
        if (h >= 0) u = u.substring(0, h)
        while (u.endsWith("/")) u = u.dropLast(1)
        if (u.endsWith("/;")) u = u.dropLast(2)
        return u
    }
}

/** Одна позиція на потік. Якщо є дубль — беремо той, у кого є favicon. */
fun dedupeStationsByStream(list: List<Station>): List<Station> {
    val map = linkedMapOf<String, Station>()
    for (s in list) {
        val k = streamIdentity(s.url)
        if (k.isBlank()) continue
        val prev = map[k]
        map[k] = when {
            prev == null -> s
            prev.favicon.isBlank() && s.favicon.isNotBlank() -> s
            prev.favicon.isNotBlank() && s.favicon.isBlank() -> prev
            else -> preferRichStation(prev, s)
        }
    }
    return map.values.toList()
}

fun mergeStationsRich(list: List<Station>): List<Station> {
    return dedupeStationsByStream(list)
}

// Рядок локального треку — перевикористовується у вкладці LocalContext.current.getString(R.string.favorites_plural)
// для секцій LocalContext.current.getString(R.string.local_favorites) та LocalContext.current.getString(R.string.local_music).
@Composable
fun LocalTrackRow(
    item: LocalTrack,
    isCurrent: Boolean,
    acc: Color,
    muted: Color,
    text: Color,
    isBest: Boolean = false,
    onToggleBest: (() -> Unit)? = null,
    onArt: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .background(if (isCurrent) acc.copy(alpha = 0.18f) else Palette.card, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clickable { onArt() },
            contentAlignment = Alignment.Center
        ) {
            val a = if (item.albumId.isNotBlank() && item.albumId != "0")
                "content://media/external/audio/albumart/${item.albumId}" else ""
            if (a.isNotEmpty()) AsyncImage(model = a, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, contentDescription = LocalContext.current.getString(R.string.no_cover), tint = muted)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp).clickable { onClick() }) {
            Text(item.title, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        if (onToggleBest != null) {
            Icon(
                if (isBest) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isBest) LocalContext.current.getString(R.string.remove_from_local_fav) else LocalContext.current.getString(R.string.add_to_local_fav_short),
                tint = acc,
                modifier = Modifier
                    .clickable { onToggleBest() }
                    .padding(start = 8.dp, end = 2.dp)
                    .size(24.dp)
            )
        }
    }
}


@Composable
fun EmptySlot(hint: String, muted: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Palette.panel.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.dp, muted.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            hint,
            color = muted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
