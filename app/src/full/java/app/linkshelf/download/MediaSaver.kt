package app.linkshelf.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Moves a finished download into the phone's shared storage:
 * videos → Movies/LinkShelf, audio → Music/LinkShelf, images → Pictures/LinkShelf.
 */
object MediaSaver {

    private enum class Kind { VIDEO, AUDIO, IMAGE, OTHER }

    fun save(context: Context, file: File) {
        val (mime, kind) = typeOf(file.extension.lowercase())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, file, mime, kind)
        } else {
            // Android 8–9: app folder on shared storage, no extra permission needed.
            val base = context.getExternalFilesDir(directoryFor(kind)) ?: context.filesDir
            val target = File(File(base, "LinkShelf").apply { mkdirs() }, file.name)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        }
    }

    private fun saveWithMediaStore(context: Context, file: File, mime: String, kind: Kind) {
        val resolver = context.contentResolver
        val collection = when (kind) {
            Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Kind.OTHER -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${directoryFor(kind)}/LinkShelf")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create ${file.name}")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Could not write ${file.name}")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun directoryFor(kind: Kind): String = when (kind) {
        Kind.VIDEO -> Environment.DIRECTORY_MOVIES
        Kind.AUDIO -> Environment.DIRECTORY_MUSIC
        Kind.IMAGE -> Environment.DIRECTORY_PICTURES
        Kind.OTHER -> Environment.DIRECTORY_DOWNLOADS
    }

    private fun typeOf(extension: String): Pair<String, Kind> = when (extension) {
        "mp4", "m4v" -> "video/mp4" to Kind.VIDEO
        "webm" -> "video/webm" to Kind.VIDEO
        "mkv" -> "video/x-matroska" to Kind.VIDEO
        "mov" -> "video/quicktime" to Kind.VIDEO
        "mp3" -> "audio/mpeg" to Kind.AUDIO
        "m4a", "aac" -> "audio/mp4" to Kind.AUDIO
        "opus", "ogg" -> "audio/ogg" to Kind.AUDIO
        "wav" -> "audio/wav" to Kind.AUDIO
        "jpg", "jpeg" -> "image/jpeg" to Kind.IMAGE
        "png" -> "image/png" to Kind.IMAGE
        "webp" -> "image/webp" to Kind.IMAGE
        else -> "application/octet-stream" to Kind.OTHER
    }
}
