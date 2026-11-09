package com.andromeda.app.data

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Title + thumbnail of a web link, shown on Atlas embed cards. Call from Dispatchers.IO. */
object LinkMeta {
    data class Meta(val title: String, val thumb: String)

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

    fun fetch(url: String): Meta {
        youtubeId(url)?.let { id ->
            val thumb = "https://img.youtube.com/vi/$id/hqdefault.jpg"
            val title = runCatching {
                val api = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
                Regex("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(get(api))?.groupValues?.get(1)?.let(::unescape)
            }.getOrNull().orEmpty()
            return Meta(title, thumb)
        }
        return runCatching {
            val html = get(url, 300_000)
            val title = metaTag(html, "og:title") ?: Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            val img = metaTag(html, "og:image")?.let { runCatching { URL(URL(url), it).toString() }.getOrNull() }
            Meta(decodeEntities(title.orEmpty()).trim(), img.orEmpty())
        }.getOrDefault(Meta("", ""))
    }

    fun youtubeId(url: String): String? =
        Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

    private fun metaTag(html: String, prop: String): String? {
        val p = Regex.escape(prop)
        val a = Regex("<meta[^>]+(?:property|name)=[\"']$p[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(html)
        val b = Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$p[\"']", RegexOption.IGNORE_CASE).find(html)
        return (a ?: b)?.groupValues?.get(1)?.let(::decodeEntities)
    }

    private fun get(url: String, limit: Int = 100_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        c.instanceFollowRedirects = true
        return try {
            c.inputStream.use { s ->
                val buf = ByteArray(limit)
                var n = 0
                while (n < limit) { val r = s.read(buf, n, limit - n); if (r < 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } finally { c.disconnect() }
    }

    private fun unescape(s: String) = s
        .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/").replace("\\n", " ")
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }

    private fun decodeEntities(s: String) = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
}

/** Decoded-image cache shared by Atlas items. */
object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(64) {}

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun decode(key: String, bytes: ByteArray, maxSide: Int = 1600): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }

    fun loadUrl(url: String): ImageBitmap? {
        get(url)?.let { return it }
        return runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            c.inputStream.use { decode(url, it.readBytes()) }
        }.getOrNull()
    }
}
