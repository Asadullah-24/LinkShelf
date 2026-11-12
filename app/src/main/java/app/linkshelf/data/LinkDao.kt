package app.linkshelf.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    @Query("SELECT * FROM links ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links ORDER BY addedAt DESC")
    suspend fun getAll(): List<LinkEntity>

    /** Returns the new row id, or -1 when the URL is already saved. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkEntity): Long

    @Query("UPDATE links SET title = :title, customTitle = 1 WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE links SET title = :title WHERE id = :id AND customTitle = 0")
    suspend fun setFetchedTitle(id: Long, title: String)

    @Query("DELETE FROM links WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM links")
    suspend fun deleteAll()
}
