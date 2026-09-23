package com.seruiso.radio1

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Чи вважаємо URL придатною іконкою станції. */
fun isStationArtUrl(raw: String?): Boolean = normalizeFavicon(raw).isNotEmpty()

private object StationArtLoader {
    @Volatile private var instance: ImageLoader? = null
    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val dispatcher = Dispatcher().apply {
                maxRequests = 12
                maxRequestsPerHost = 4
            }
            val http = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .dispatcher(dispatcher)
                .build()
            val loader = ImageLoader.Builder(context.applicationContext)
                .okHttpClient(http)
                .respectCacheHeaders(false) // інакше вкладки качають іконку щоразу
                .crossfade(false)
                .build()
            instance = loader
            return loader
        }
    }
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
    val launcher = painterResource(R.mipmap.ic_launcher_foreground)
    val normalized = remember(url) { normalizeFavicon(url) }
    if (normalized.isEmpty()) {
        Image(
            painter = launcher,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = contentScale,
        )
        return
    }
    val context = LocalContext.current
    val req = remember(normalized) {
        ImageRequest.Builder(context)
            .data(normalized)
            .size(96)
            .crossfade(80)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }
    AsyncImage(
        model = req,
        imageLoader = StationArtLoader.get(context),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = launcher,
        error = launcher,
        fallback = launcher,
    )
}
