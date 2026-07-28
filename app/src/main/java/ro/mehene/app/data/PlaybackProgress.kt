package ro.mehene.app.data

enum class EpisodePlaybackState {
    UNWATCHED,
    IN_PROGRESS,
    COMPLETED,
}

data class EpisodeProgress(
    val state: EpisodePlaybackState,
    val fraction: Float,
)

object PlaybackProgressPolicy {
    private const val MIN_STARTED_MS = 3_000L
    private const val COMPLETION_RATIO = 0.95
    private const val COMPLETION_REMAINING_MS = 5_000L

    fun evaluate(positionMs: Long, durationMs: Long): EpisodeProgress {
        if (durationMs <= 0L || positionMs < MIN_STARTED_MS) {
            return EpisodeProgress(EpisodePlaybackState.UNWATCHED, 0f)
        }

        val safePosition = positionMs.coerceIn(0L, durationMs)
        val completed = safePosition >= (durationMs * COMPLETION_RATIO).toLong() ||
            durationMs - safePosition <= COMPLETION_REMAINING_MS
        if (completed) {
            return EpisodeProgress(EpisodePlaybackState.COMPLETED, 1f)
        }

        return EpisodeProgress(
            state = EpisodePlaybackState.IN_PROGRESS,
            fraction = (safePosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
        )
    }
}
