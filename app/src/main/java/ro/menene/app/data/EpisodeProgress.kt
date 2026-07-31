package ro.menene.app.data

enum class EpisodePlaybackState {
    UNWATCHED,
    IN_PROGRESS,
    COMPLETED,
}

data class EpisodeProgress(
    val episodeId: String,
    val state: EpisodePlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAtEpochMs: Long,
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}
