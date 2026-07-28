package ro.mehene.app.data

import ro.mehene.app.db.PlaybackProgressDao
import ro.mehene.app.db.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgressRepository(
    private val dao: PlaybackProgressDao,
) {
    val progress: Flow<Map<String, EpisodeProgress>> = dao.observeAll().map { entities ->
        entities.associate { entity -> entity.episodeId to entity.toDomain() }
    }

    suspend fun snapshot(): Map<String, EpisodeProgress> =
        dao.getAll().associate { entity -> entity.episodeId to entity.toDomain() }

    suspend fun get(episodeId: String): EpisodeProgress? = dao.get(episodeId)?.toDomain()

    suspend fun clear() = dao.clear()

    suspend fun save(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val evaluated = PlaybackProgressPolicy.evaluate(
            episodeId = episodeId,
            positionMs = positionMs,
            durationMs = durationMs,
            lastPlayedAtEpochMs = nowEpochMs,
        )
        dao.upsert(evaluated.toEntity())
    }

    suspend fun markCompleted(
        episodeId: String,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        dao.upsert(
            PlaybackProgressEntity(
                episodeId = episodeId,
                positionMs = 0L,
                durationMs = durationMs.coerceAtLeast(0L),
                completed = true,
                lastPlayedAtEpochMs = nowEpochMs,
            ),
        )
    }

    suspend fun prune(validEpisodeIds: Set<String>) {
        if (validEpisodeIds.isEmpty()) {
            dao.clear()
            return
        }
        val staleIds = dao.getAll().asSequence()
            .map { it.episodeId }
            .filterNot(validEpisodeIds::contains)
            .toList()
        staleIds.chunked(400).forEach { dao.deleteByIds(it) }
    }

    private fun PlaybackProgressEntity.toDomain(): EpisodeProgress = EpisodeProgress(
        episodeId = episodeId,
        state = when {
            completed -> EpisodePlaybackState.COMPLETED
            positionMs > 0L -> EpisodePlaybackState.IN_PROGRESS
            else -> EpisodePlaybackState.UNWATCHED
        },
        positionMs = positionMs,
        durationMs = durationMs,
        lastPlayedAtEpochMs = lastPlayedAtEpochMs,
    )

    private fun EpisodeProgress.toEntity(): PlaybackProgressEntity = PlaybackProgressEntity(
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = state == EpisodePlaybackState.COMPLETED,
        lastPlayedAtEpochMs = lastPlayedAtEpochMs,
    )
}
