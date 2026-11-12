package app.linkshelf.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.linkshelf.domain.Platform

@Entity(
    tableName = "links",
    indices = [Index(value = ["url"], unique = true), Index(value = ["platform"])],
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    /** [Platform] enum name, e.g. "INSTAGRAM". */
    val platform: String,
    /** Content type: Reel, Post, Short, Song, Video… */
    val kind: String,
    val videoId: String? = null,
    val title: String,
    /** True once the user renamed the item, so fetched titles never overwrite it. */
    val customTitle: Boolean = false,
    val addedAt: Long,
    /** Local preview image file, so cards show thumbnails offline. */
    val thumbnailPath: String? = null,
)

/** Kept outside the entity so Room does not try to store it as a column. */
fun LinkEntity.platformEnum(): Platform = Platform.fromKey(platform)
