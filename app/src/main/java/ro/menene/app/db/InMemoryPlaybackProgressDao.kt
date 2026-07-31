package ro.menene.app.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Last-resort runtime DAO used only when Room cannot be opened. */
class InMemoryPlaybackProgressDao : PlaybackProgressDao {
    private val state = MutableStateFlow<List<PlaybackProgressEntity>>(emptyList())

    private fun forLibrary(libraryId: String): List<PlaybackProgressEntity> =
        state.value.filter { it.libraryId == libraryId }

    override fun observeAll(libraryId: String): Flow<List<PlaybackProgressEntity>> =
        state.map { values -> values.filter { it.libraryId == libraryId } }

    override suspend fun getAll(libraryId: String): List<PlaybackProgressEntity> = forLibrary(libraryId)

    override suspend fun get(libraryId: String, episodeId: String): PlaybackProgressEntity? =
        forLibrary(libraryId).firstOrNull { it.episodeId == episodeId }

    override suspend fun upsert(entity: PlaybackProgressEntity) {
        state.value = (state.value.filterNot { it.libraryId == entity.libraryId && it.episodeId == entity.episodeId } + entity)
            .sortedByDescending(PlaybackProgressEntity::lastPlayedAtEpochMs)
    }

    override suspend fun deleteByIds(libraryId: String, episodeIds: List<String>) {
        val ids = episodeIds.toSet()
        state.value = state.value.filterNot { it.libraryId == libraryId && it.episodeId in ids }
    }

    override suspend fun clear(libraryId: String) {
        state.value = state.value.filterNot { it.libraryId == libraryId }
    }
}
