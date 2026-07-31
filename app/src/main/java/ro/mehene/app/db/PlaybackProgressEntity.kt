package ro.mehene.app.db

import androidx.room.Entity

@Entity(tableName = "playback_progress", primaryKeys = ["libraryId", "episodeId"])
data class PlaybackProgressEntity(
    val libraryId: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAtEpochMs: Long,
)
