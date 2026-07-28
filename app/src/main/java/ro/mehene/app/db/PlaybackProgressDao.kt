package ro.mehene.app.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress ORDER BY lastPlayedAtEpochMs DESC")
    fun observeAll(): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress")
    suspend fun getAll(): List<PlaybackProgressEntity>

    @Query("SELECT * FROM playback_progress WHERE episodeId = :episodeId LIMIT 1")
    suspend fun get(episodeId: String): PlaybackProgressEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE episodeId IN (:episodeIds)")
    suspend fun deleteByIds(episodeIds: List<String>)

    @Query("DELETE FROM playback_progress")
    suspend fun clear()
}
