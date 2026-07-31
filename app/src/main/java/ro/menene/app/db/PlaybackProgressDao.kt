package ro.menene.app.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE libraryId = :libraryId ORDER BY lastPlayedAtEpochMs DESC")
    fun observeAll(libraryId: String): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE libraryId = :libraryId")
    suspend fun getAll(libraryId: String): List<PlaybackProgressEntity>

    @Query("SELECT * FROM playback_progress WHERE libraryId = :libraryId AND episodeId = :episodeId LIMIT 1")
    suspend fun get(libraryId: String, episodeId: String): PlaybackProgressEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE libraryId = :libraryId AND episodeId IN (:episodeIds)")
    suspend fun deleteByIds(libraryId: String, episodeIds: List<String>)

    @Query("DELETE FROM playback_progress WHERE libraryId = :libraryId")
    suspend fun clear(libraryId: String)
}
