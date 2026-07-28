package ro.mehene.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAtEpochMs: Long,
)
