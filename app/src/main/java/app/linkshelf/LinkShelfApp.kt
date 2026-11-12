package app.linkshelf

import android.app.Application
import android.content.Context
import app.linkshelf.capture.Notifications
import app.linkshelf.data.AppDatabase
import app.linkshelf.data.LinkRepository
import app.linkshelf.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Application entry point. Holds the app-wide singletons (a tiny service locator). */
class LinkShelfApp : Application() {

    /** Scope for work that must outlive a screen, e.g. fetching titles after a save. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: LinkRepository by lazy { LinkRepository(database.linkDao(), appScope, File(filesDir, "thumbnails")) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

/** Shortcut to the application object from any context. */
val Context.linkShelf: LinkShelfApp
    get() = applicationContext as LinkShelfApp
