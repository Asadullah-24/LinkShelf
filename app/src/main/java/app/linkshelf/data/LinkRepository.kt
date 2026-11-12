package app.linkshelf.data

import app.linkshelf.domain.LinkParser
import app.linkshelf.domain.ParsedLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    private val thumbnailDir: File,
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
                backgroundScope.launch { loadPreview(id, parsed) }
            }
        }
        SaveResult(found = urls.size, added = added, duplicates = duplicates)
    }

    suspend fun rename(id: Long, title: String) = dao.rename(id, title.trim())

    suspend fun delete(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        dao.getByIds(ids.toList()).forEach { link -> link.thumbnailPath?.let { File(it).delete() } }
        dao.delete(ids.toList())
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        thumbnailDir.listFiles()?.forEach { it.delete() }
    }

    /** Fetches titles and thumbnails for links that have none yet. Returns how many got a preview. */
    suspend fun refreshMissingPreviews(): Int = withContext(Dispatchers.IO) {
        var updated = 0
        for (link in dao.getWithoutThumbnail()) {
            if (loadPreview(link.id, LinkParser.detect(link.url))) updated++
        }
        updated
    }

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

    /**
     * Fetches the title and preview image for one link and stores the image locally.
     * Network failures are ignored; the card keeps its placeholder. Returns true if a thumbnail was saved.
     */
    private suspend fun loadPreview(id: Long, parsed: ParsedLink): Boolean = withContext(Dispatchers.IO) {
        val meta = MetadataFetcher.fetch(parsed) ?: return@withContext false
        meta.title?.let { dao.setFetchedTitle(id, it) }
        val imageUrl = meta.imageUrl ?: return@withContext false
        val file = File(thumbnailDir, "$id.jpg")
        if (MetadataFetcher.downloadThumbnail(imageUrl, file)) {
            dao.setThumbnail(id, file.absolutePath)
            true
        } else {
            false
        }
    }
}
