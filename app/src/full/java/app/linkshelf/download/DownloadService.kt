package app.linkshelf.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.linkshelf.R
import app.linkshelf.capture.Notifications
import app.linkshelf.ui.MainActivity
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Downloads queued links one by one with yt-dlp, showing progress in a notification,
 * then moves each file to Movies/Music/Pictures → LinkShelf.
 */
class DownloadService : Service() {

    private data class Task(val url: String, val title: String, val mode: DownloadMode)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<Task>()
    private var worker: Job? = null

    private var total = 0
    private var finished = 0
    private var failed = 0
    private var lastError: String? = null
    private var lastPercent = -1

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val urls = intent?.getStringArrayExtra(EXTRA_URLS).orEmpty()
        val titles = intent?.getStringArrayExtra(EXTRA_TITLES).orEmpty()
        val mode = runCatching { DownloadMode.valueOf(intent?.getStringExtra(EXTRA_MODE).orEmpty()) }
            .getOrDefault(DownloadMode.VIDEO)

        synchronized(this) {
            urls.forEachIndexed { i, url -> queue.add(Task(url, titles.getOrElse(i) { url }, mode)) }
            total += urls.size
        }

        ServiceCompat.startForeground(
            this,
            PROGRESS_ID,
            progressNotification("Preparing…", 0, indeterminate = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        if (worker?.isActive != true) worker = scope.launch { processQueue() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun processQueue() {
        try {
            Downloads.ensureInitialized(this)
        } catch (e: Exception) {
            synchronized(this) {
                failed += queue.size
                queue.clear()
            }
            lastError = "Downloader could not start: ${e.message?.take(100)}"
        }

        while (true) {
            val task = synchronized(this) { queue.poll() } ?: break
            lastPercent = -1
            updateProgress(task, 0f)
            try {
                download(task)
                finished++
            } catch (e: Exception) {
                failed++
                lastError = friendlyError(e.message)
            }
        }

        showSummary()
        synchronized(this) {
            if (queue.isEmpty()) {
                total = 0
                finished = 0
                failed = 0
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                worker = scope.launch { processQueue() }
            }
        }
    }

    private fun download(task: Task) {
        val workDir = File(cacheDir, "downloads/${System.nanoTime()}").apply { mkdirs() }
        try {
            val request = YoutubeDLRequest(task.url).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("-o", File(workDir, "%(title).80s [%(id)s].%(ext)s").absolutePath)
                if (task.mode == DownloadMode.AUDIO) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                } else {
                    addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
            }
            YoutubeDL.getInstance().execute(request, "linkshelf-${System.nanoTime()}") { progress, _, _ ->
                updateProgress(task, progress)
            }
            val files = workDir.walkTopDown()
                .filter { it.isFile && it.length() > 0 }
                .filterNot { it.name.endsWith(".part") || it.name.endsWith(".ytdl") || it.name.endsWith(".temp") }
                .toList()
            if (files.isEmpty()) error("Nothing was downloaded")
            files.forEach { MediaSaver.save(this, it) }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun updateProgress(task: Task, progress: Float) {
        val percent = progress.toInt().coerceIn(0, 100)
        if (percent == lastPercent) return
        lastPercent = percent
        val position = finished + failed + 1
        val label = if (task.mode == DownloadMode.AUDIO) "audio" else "video"
        val text = "Downloading $label $position of $total · $percent%"
        notificationManager.notify(PROGRESS_ID, progressNotification(text, percent, indeterminate = percent == 0, title = task.title))
    }

    private fun progressNotification(
        text: String,
        percent: Int,
        indeterminate: Boolean,
        title: String = "LinkShelf downloads",
    ): Notification =
        NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_bookmark)
            .setContentTitle(title.take(80))
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun showSummary() {
        val text = buildString {
            append("Saved $finished file${if (finished == 1) "" else "s"}")
            if (failed > 0) append(" · $failed failed")
        }
        val details = if (failed > 0 && lastError != null) "$text\n$lastError" else
            "$text to Movies / Music → LinkShelf"
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_bookmark)
            .setContentTitle(if (failed > 0 && finished == 0) "Download failed" else "Download complete")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager.notify(SUMMARY_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 2,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Turns yt-dlp's error output into one readable line. */
    private fun friendlyError(raw: String?): String {
        val line = raw.orEmpty().lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: raw.orEmpty().lines().lastOrNull { it.isNotBlank() }
            ?: "Unknown error"
        val clean = line.substringAfter("ERROR:").trim()
        return when {
            clean.contains("login", ignoreCase = true) || clean.contains("cookies", ignoreCase = true) ->
                "This post needs a logged-in account, so it can't be downloaded."
            clean.contains("private", ignoreCase = true) -> "This post is private."
            clean.contains("Unsupported URL", ignoreCase = true) -> "This link has no downloadable video."
            else -> clean.take(160)
        }
    }

    companion object {
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_MODE = "mode"
        private const val PROGRESS_ID = 2001
        private const val SUMMARY_ID = 2002

        fun enqueue(context: Context, items: List<Pair<String, String>>, mode: DownloadMode) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_URLS, items.map { it.first }.toTypedArray())
                .putExtra(EXTRA_TITLES, items.map { it.second }.toTypedArray())
                .putExtra(EXTRA_MODE, mode.name)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
