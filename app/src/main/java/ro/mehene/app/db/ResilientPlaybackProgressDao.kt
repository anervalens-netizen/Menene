package ro.mehene.app.db

import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Keeps progress operations alive when Room fails after construction. The atomic backup remains
 * the durable source; the in-memory DAO is the process-local write/read fallback.
 */
class ResilientPlaybackProgressDao(
    private val primary: PlaybackProgressDao?,
    private val fallback: PlaybackProgressDao = InMemoryPlaybackProgressDao(),
) : PlaybackProgressDao {
    private val active = AtomicReference(primary ?: fallback)

    suspend fun healthProbe(): Boolean {
        val candidate = primary ?: return false.also { activateFallback(null) }
        return runCatching { candidate.getAll(HEALTH_LIBRARY_ID) }
            .onFailure { activateFallback(it) }
            .isSuccess
    }

    fun isUsingFallback(): Boolean = active.get() === fallback

    override fun observeAll(libraryId: String): Flow<List<PlaybackProgressEntity>> = flow {
        val candidate = active.get()
        try {
            emitAll(candidate.observeAll(libraryId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            activateFallback(error)
            emitAll(fallback.observeAll(libraryId))
        }
    }

    override suspend fun getAll(libraryId: String): List<PlaybackProgressEntity> = withFallback(
        operation = { it.getAll(libraryId) },
    )

    override suspend fun get(libraryId: String, episodeId: String): PlaybackProgressEntity? = withFallback(
        operation = { it.get(libraryId, episodeId) },
    )

    override suspend fun upsert(entity: PlaybackProgressEntity) {
        withFallback { it.upsert(entity) }
    }

    override suspend fun deleteByIds(libraryId: String, episodeIds: List<String>) {
        withFallback { it.deleteByIds(libraryId, episodeIds) }
    }

    override suspend fun clear(libraryId: String) {
        withFallback { it.clear(libraryId) }
    }

    private suspend fun <T> withFallback(operation: suspend (PlaybackProgressDao) -> T): T {
        val candidate = active.get()
        return try {
            operation(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            activateFallback(error)
            operation(fallback)
        }
    }

    private fun activateFallback(error: Throwable?) {
        if (active.getAndSet(fallback) !== fallback) {
            error?.let { Log.e(TAG, "Room progress DAO failed; switched to in-memory fallback", it) }
        }
    }

    companion object {
        private const val TAG = "MeheneProgressDao"
        private const val HEALTH_LIBRARY_ID = "__health_probe__"
    }
}
