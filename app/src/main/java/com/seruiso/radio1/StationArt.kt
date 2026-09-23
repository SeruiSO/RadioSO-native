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
fun isStationArtUrl(raw: String?): Boolean = displayFavicon(raw).isNotEmpty()

/**
 * URL для малювання. Не ріже .ico: шторка це декодує BitmapFactory,
 * часто всередині звичайний PNG. .svg лишаємо за бортом.
 */
fun displayFavicon(raw: String?): String {
    val u = raw?.trim().orEmpty()
    if (u.isEmpty()) return ""
    val low = u.lowercase()
    if (low == "-" || low == "n/a" || low == "null" || low == "none") return ""
    if (low.contains("example.com")) return ""
    if ("google.com/s2/favicons" in low) return ""
    if (!(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("content:"))) return ""
    if (low.endsWith(".svg")) return ""
    return u
}

private object StationArtLoader {
    @Volatile private var instance: ImageLoader? = null
    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val dispatcher = Dispatcher().apply {
                maxRequests = 24
                maxRequestsPerHost = 6
            }
            val ua = System.getProperty("http.agent") ?: "Dalvik/2.1.0 (Linux; Android)"
            val http = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .dispatcher(dispatcher)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", ua)
                            .build()
                    )
                }
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
    val normalized = remember(url) { displayFavicon(url) }
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
            .size(128)
            .allowHardware(false)
            .crossfade(false)
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
