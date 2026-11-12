package app.linkshelf.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.linkshelf.data.LinkEntity
import app.linkshelf.linkShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.linkShelf.repository

    val links: StateFlow<List<LinkEntity>> = repository.links
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun saveText(text: String?, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.saveFromText(text)
            _messages.emit(result.message)
            onSaved(result.added > 0)
        }
    }

    fun rename(id: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch {
            repository.delete(ids)
            _messages.emit(if (ids.size == 1) "Deleted" else "Deleted ${ids.size} items")
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
            _messages.emit("All links deleted")
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = repository.exportJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } ?: error("Cannot open file")
                }
            }.isSuccess
            _messages.emit(if (ok) "Backup saved" else "Could not save the backup")
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    } ?: error("Cannot open file")
                }
                repository.importJson(json)
            }
            _messages.emit(
                result.fold(
                    onSuccess = { "Restored $it links" },
                    onFailure = { "That is not a valid backup" },
                )
            )
        }
    }

    fun addSamples() = saveText(
        listOf(
            "https://www.instagram.com/reel/SampleReel01/",
            "https://www.instagram.com/p/SamplePost02/",
            "https://www.facebook.com/reel/1234567890",
            "https://fb.watch/sampleVid/",
            "https://www.youtube.com/watch?v=sampleVid01",
            "https://music.youtube.com/watch?v=sampleSong1",
            "https://youtube.com/shorts/sampleShort",
            "https://www.linkedin.com/posts/sample-post-activity-1234",
            "https://www.tiktok.com/@creator/video/1234567890",
        ).joinToString("\n")
    )
}
