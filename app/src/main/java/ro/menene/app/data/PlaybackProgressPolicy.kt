package ro.menene.app.data

object PlaybackProgressPolicy {
    private const val COMPLETED_THRESHOLD = 0.95
    private const val MINIMUM_PROGRESS_MS = 5_000L

    fun evaluate(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        lastPlayedAtEpochMs: Long,
    ): EpisodeProgress {
        if (durationMs <= 0L || positionMs < MINIMUM_PROGRESS_MS) {
            return EpisodeProgress(
                episodeId = episodeId,
                state = EpisodePlaybackState.UNWATCHED,
                positionMs = 0L,
                durationMs = durationMs.coerceAtLeast(0L),
                lastPlayedAtEpochMs = lastPlayedAtEpochMs,
            )
        }
        val completed = positionMs.toDouble() / durationMs.toDouble() >= COMPLETED_THRESHOLD
        return EpisodeProgress(
            episodeId = episodeId,
            state = if (completed) EpisodePlaybackState.COMPLETED else EpisodePlaybackState.IN_PROGRESS,
            positionMs = if (completed) 0L else positionMs.coerceIn(0L, durationMs),
            durationMs = durationMs,
            lastPlayedAtEpochMs = lastPlayedAtEpochMs,
        )
    }
}
