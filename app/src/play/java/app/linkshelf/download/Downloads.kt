package app.linkshelf.download

import android.content.Context
import app.linkshelf.data.LinkEntity

/** Google Play build: no downloader. The UI hides download options when [isAvailable] is false. */
object Downloads {

    const val isAvailable: Boolean = false

    fun enqueue(context: Context, links: List<LinkEntity>, mode: DownloadMode) = Unit

    suspend fun updateEngine(context: Context): String = "Downloads are not available in this version"
}
