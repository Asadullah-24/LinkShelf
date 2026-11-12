package app.linkshelf.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.linkshelf.data.LinkEntity

/** Full (GitHub) build: video and audio downloads powered by yt-dlp. */
object Downloads {

    const val isAvailable: Boolean = true

    @Volatile
    private var initialized = false

    /** Unpacks yt-dlp, Python and ffmpeg on first use. Blocking: call off the main thread. */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            initialized = true
        }
    }

    fun enqueue(context: Context, links: List<LinkEntity>, mode: DownloadMode) {
        if (links.isEmpty()) return
        DownloadService.enqueue(context, links.map { it.url to it.title }, mode)
    }

    /** Updates yt-dlp, which sites change often. Returns a message for the user. */
    suspend fun updateEngine(context: Context): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized(context)
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                YoutubeDL.UpdateChannel.STABLE,
            )
            when (status?.name) {
                "DONE" -> "Downloader updated"
                "ALREADY_UP_TO_DATE" -> "Downloader is already up to date"
                else -> "Update finished"
            }
        }.getOrElse { "Update failed: ${it.message?.take(120)}" }
    }
}
