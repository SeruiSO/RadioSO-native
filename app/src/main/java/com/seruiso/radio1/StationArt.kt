package com.seruiso.radio1

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage

/** Чи вважаємо URL придатною іконкою станції. */
fun isStationArtUrl(raw: String?): Boolean {
    val u = raw?.trim().orEmpty()
    if (u.isEmpty()) return false
    if (u.contains("example.com", ignoreCase = true)) return false
    return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("content:")
}

/**
 * Єдина заглушка радіо: є favicon → воно; немає / помилка → іконка додатку (PNG foreground).
 * Не використовувати R.mipmap.ic_launcher (adaptive XML) — Compose painterResource падає.
 */
@Composable
fun StationArt(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    // raster PNG у mipmap-*; adaptive ic_launcher.xml не підтримується painterResource
    val launcher = painterResource(R.mipmap.ic_launcher_foreground)
    if (isStationArtUrl(url)) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = launcher,
            error = launcher,
            fallback = launcher,
        )
    } else {
        Image(
            painter = launcher,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
