package app.linkshelf.domain

import android.net.Uri

/** Result of analysing one URL. */
data class ParsedLink(
    val url: String,
    val platform: Platform,
    val kind: String,
    val videoId: String? = null,
)

/**
 * Finds links in shared or copied text, strips tracking parameters,
 * and works out which platform and content type (Reel, Short, Post…) each link is.
 */
object LinkParser {

    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val hostPrefix = Regex("^(www|m|mobile|web)\\.")
    private val reelPath = Regex("/reels?/")

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "igsh", "igshid", "si", "fbclid", "mibextid", "feature", "rcm", "ref", "ref_src",
        "share_id", "sfnsn", "_r", "is_from_webapp", "sender_device",
    )

    /** All http(s) links in [text], in order, without duplicates. */
    fun extractUrls(text: String?): List<String> =
        urlRegex.findAll(text.orEmpty())
            .map { it.value.trimEnd(')', ',', '.', ';', '!', '?', ']') }
            .distinct()
            .toList()

    /** Removes tracking parameters and fragments so the same post always maps to one URL. */
    fun normalize(raw: String): String? = try {
        val uri = Uri.parse(raw.trim())
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
            null
        } else {
            val builder = uri.buildUpon().clearQuery().encodedFragment(null)
            for (name in uri.queryParameterNames) {
                if (name.lowercase() in trackingParams) continue
                for (value in uri.getQueryParameters(name)) builder.appendQueryParameter(name, value)
            }
            var result = builder.build().toString()
            if (result.endsWith("/") && builder.build().query.isNullOrEmpty()) {
                result = result.dropLast(1)
            }
            result
        }
    } catch (e: Exception) {
        null
    }

    fun detect(url: String): ParsedLink {
        val uri = Uri.parse(url)
        val host = hostPrefix.replace(uri.host.orEmpty().lowercase(), "")
        val path = uri.path.orEmpty()
        val segments = uri.pathSegments.orEmpty()

        fun hostIs(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }

        return when {
            hostIs("instagram.com", "instagr.am") -> {
                val kind = when {
                    reelPath.containsMatchIn(path) -> "Reel"
                    path.contains("/stories/") -> "Story"
                    path.contains("/tv/") -> "Video"
                    path.contains("/p/") -> "Post"
                    else -> "Profile"
                }
                ParsedLink(url, Platform.INSTAGRAM, kind)
            }

            hostIs("facebook.com", "fb.com", "fb.watch") -> {
                val kind = when {
                    reelPath.containsMatchIn(path) || path.contains("/share/r/") -> "Reel"
                    host == "fb.watch" || path.contains("/watch") ||
                        path.contains("/videos/") || path.contains("/share/v/") -> "Video"
                    else -> "Post"
                }
                ParsedLink(url, Platform.FACEBOOK, kind)
            }

            hostIs("youtube.com", "youtu.be", "youtube-nocookie.com") -> {
                var id: String? = null
                var kind = "Video"
                when {
                    host == "youtu.be" -> id = segments.firstOrNull()
                    segments.firstOrNull() == "shorts" -> { id = segments.getOrNull(1); kind = "Short" }
                    segments.firstOrNull() == "live" -> { id = segments.getOrNull(1); kind = "Live" }
                    else -> id = uri.getQueryParameter("v")
                }
                if (host == "music.youtube.com") kind = "Song"
                if (id == null && uri.getQueryParameter("list") != null) kind = "Playlist"
                ParsedLink(url, Platform.YOUTUBE, kind, id)
            }

            hostIs("linkedin.com", "lnkd.in") -> {
                val isProfile = (path.contains("/in/") || path.contains("/company/")) && !path.contains("/post")
                ParsedLink(url, Platform.LINKEDIN, if (isProfile) "Profile" else "Post")
            }

            hostIs("tiktok.com") ->
                ParsedLink(url, Platform.TIKTOK, if (path.contains("/photo/")) "Photo" else "Video")

            hostIs("twitter.com", "x.com") -> ParsedLink(url, Platform.X, "Post")

            else -> ParsedLink(url, Platform.OTHER, "Link")
        }
    }

    /** Readable placeholder title until a real one is fetched or the user renames it. */
    fun defaultTitle(link: ParsedLink): String {
        val uri = Uri.parse(link.url)
        val tail = link.videoId
            ?: uri.pathSegments.orEmpty().lastOrNull()
            ?: uri.host.orEmpty()
        return "${link.platform.displayName} ${link.kind} · ${Uri.decode(tail).take(24)}"
    }

    fun thumbnailUrl(videoId: String?): String? =
        videoId?.let { "https://i.ytimg.com/vi/${Uri.encode(it)}/hqdefault.jpg" }
}
