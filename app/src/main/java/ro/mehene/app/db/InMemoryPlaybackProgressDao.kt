package ro.mehene.app.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Last-resort runtime DAO used only when Room cannot be opened. */
class InMemoryPlaybackProgressDao : PlaybackProgressDao {
    private val state = MutableStateFlow<List<PlaybackProgressEntity>>(emptyList())

    override fun observeAll(): Flow<List<PlaybackProgressEntity>> = state

    override suspend fun getAll(): List<PlaybackProgressEntity> = state.value

    override suspend fun get(episodeId: String): PlaybackProgressEntity? =
        state.value.firstOrNull { it.episodeId == episodeId }

    override suspend fun upsert(entity: PlaybackProgressEntity) {
        state.value = (state.value.filterNot { it.episodeId == entity.episodeId } + entity)
            .sortedByDescending(PlaybackProgressEntity::lastPlayedAtEpochMs)
    }

    override suspend fun deleteByIds(episodeIds: List<String>) {
        val ids = episodeIds.toSet()
        state.value = state.value.filterNot { it.episodeId in ids }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
