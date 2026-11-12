package app.linkshelf.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import app.linkshelf.domain.ParsedLink
import app.linkshelf.domain.Platform
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up a title and preview image for a saved link.
 * YouTube and TikTok use their public oEmbed endpoints; everything else uses the page's Open Graph tags.
 * All calls are blocking: run them on a background dispatcher.
 */
object MetadataFetcher {

    data class Metadata(val title: String?, val imageUrl: String?)

    // Social sites serve preview tags to link-preview crawlers, so identify as one.
    private const val PREVIEW_AGENT =
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MAX_HTML_BYTES = 1_500_000
    private const val MAX_IMAGE_BYTES = 8_000_000
    private const val THUMB_WIDTH = 480

    fun fetch(link: ParsedLink): Metadata? = runCatching {
        when (link.platform) {
            Platform.YOUTUBE -> {
                val oembed = oembed("https://www.youtube.com/oembed?format=json&url=" + encode(link.url))
                val image = link.videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: oembed?.imageUrl
                Metadata(oembed?.title, image)
            }
            Platform.TIKTOK ->
                oembed("https://www.tiktok.com/oembed?url=" + encode(link.url)) ?: openGraph(link.url)
            else -> openGraph(link.url)
        }
    }.getOrNull()

    /** Downloads [imageUrl], shrinks it to a card-sized JPEG and writes it to [target]. */
    fun downloadThumbnail(imageUrl: String, target: File): Boolean = runCatching {
        val bytes = getBytes(imageUrl, BROWSER_AGENT, MAX_IMAGE_BYTES) ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return false
        target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    private fun oembed(endpoint: String): Metadata? {
        val body = getBytes(endpoint, BROWSER_AGENT, MAX_HTML_BYTES)?.decodeToString() ?: return null
        val json = JSONObject(body)
        return Metadata(
            title = json.optString("title").ifBlank { null },
            imageUrl = json.optString("thumbnail_url").ifBlank { null },
        )
    }

    private fun openGraph(url: String): Metadata? {
        val html = getBytes(url, PREVIEW_AGENT, MAX_HTML_BYTES)?.decodeToString() ?: return null
        val title = metaContent(html, "og:title") ?: metaContent(html, "twitter:title")
        val image = metaContent(html, "og:image") ?: metaContent(html, "twitter:image")
        if (title == null && image == null) return null
        return Metadata(title?.take(150), image)
    }

    private fun metaContent(html: String, property: String): String? {
        val prop = Regex.escape(property)
        val value = """(?:"([^"]*)"|'([^']*)')"""
        val propFirst = Regex(
            """<meta[^>]+(?:property|name)\s*=\s*["']$prop["'][^>]*?content\s*=\s*$value""",
            RegexOption.IGNORE_CASE,
        )
        val contentFirst = Regex(
            """<meta[^>]+content\s*=\s*$value[^>]*?(?:property|name)\s*=\s*["']$prop["']""",
            RegexOption.IGNORE_CASE,
        )
        val match = propFirst.find(html) ?: contentFirst.find(html) ?: return null
        val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim().ifBlank { null }
    }

    private fun getBytes(url: String, userAgent: String, maxBytes: Int): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "en-US,en;q=0.8")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (out.size() < maxBytes) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
