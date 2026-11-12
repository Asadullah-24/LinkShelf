package app.linkshelf.data

import app.linkshelf.domain.LinkParser
import app.linkshelf.domain.ParsedLink
import app.linkshelf.domain.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Outcome of saving links from a piece of text. */
data class SaveResult(val found: Int, val added: Int, val duplicates: Int) {
    val message: String
        get() = when {
            found == 0 -> "No link found"
            added > 0 && duplicates > 0 -> "Saved $added · $duplicates already saved"
            added == 1 -> "Link saved"
            added > 1 -> "Saved $added links"
            else -> "Already saved"
        }
}

/** Single source of truth for saved links. */
class LinkRepository(
    private val dao: LinkDao,
    private val backgroundScope: CoroutineScope,
) {

    val links: Flow<List<LinkEntity>> = dao.observeAll()

    /** Saves every link found in [text]. Duplicates are skipped. */
    suspend fun saveFromText(text: String?): SaveResult = withContext(Dispatchers.IO) {
        val urls = LinkParser.extractUrls(text)
        var added = 0
        var duplicates = 0
        for (raw in urls) {
            val url = LinkParser.normalize(raw) ?: continue
            val parsed = LinkParser.detect(url)
            val id = dao.insert(
                LinkEntity(
                    url = url,
                    platform = parsed.platform.name,
                    kind = parsed.kind,
                    videoId = parsed.videoId,
                    title = LinkParser.defaultTitle(parsed),
                    addedAt = System.currentTimeMillis(),
                )
            )
            if (id == -1L) {
                duplicates++
            } else {
                added++
                fetchTitleLater(id, parsed)
            }
        }
        SaveResult(found = urls.size, added = added, duplicates = duplicates)
    }

    suspend fun rename(id: Long, title: String) = dao.rename(id, title.trim())

    suspend fun delete(ids: Collection<Long>) = dao.delete(ids.toList())

    suspend fun deleteAll() = dao.deleteAll()

    /** Backup format, shared with the LinkShelf web version. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val items = JSONArray()
        dao.getAll().forEach { link ->
            items.put(
                JSONObject()
                    .put("url", link.url)
                    .put("platform", link.platform.lowercase())
                    .put("kind", link.kind)
                    .put("videoId", link.videoId ?: JSONObject.NULL)
                    .put("title", link.title)
                    .put("customTitle", link.customTitle)
                    .put("addedAt", link.addedAt)
            )
        }
        JSONObject().put("version", 1).put("items", items).toString(2)
    }

    /** Restores a backup. Existing links stay; duplicates are skipped. Returns how many were added. */
    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val items = JSONObject(json).optJSONArray("items") ?: JSONArray()
        var added = 0
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val url = LinkParser.normalize(obj.optString("url")) ?: continue
            val parsed = LinkParser.detect(url)
            val title = obj.optString("title").ifBlank { LinkParser.defaultTitle(parsed) }
            val id = dao.insert(
                LinkEntity(
                    url = url,
                    platform = parsed.platform.name,
                    kind = parsed.kind,
                    videoId = parsed.videoId,
                    title = title,
                    customTitle = obj.optBoolean("customTitle", false),
                    addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                )
            )
            if (id != -1L) added++
        }
        added
    }

    /** Looks up the real title for YouTube links in the background. Failures are ignored. */
    private fun fetchTitleLater(id: Long, parsed: ParsedLink) {
        if (parsed.platform != Platform.YOUTUBE || parsed.videoId == null) return
        backgroundScope.launch {
            runCatching {
                val endpoint = "https://www.youtube.com/oembed?format=json&url=" +
                    URLEncoder.encode(parsed.url, "UTF-8")
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                try {
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val title = JSONObject(body).optString("title")
                        if (title.isNotBlank()) dao.setFetchedTitle(id, title)
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
    }
}
