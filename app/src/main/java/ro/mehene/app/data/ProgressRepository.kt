package ro.mehene.app.data

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.mehene.app.db.PlaybackProgressDao
import ro.mehene.app.db.PlaybackProgressEntity

class ProgressRepository(
    private val dao: PlaybackProgressDao,
    private val backupStore: ProgressBackupStore,
    private val applicationScope: CoroutineScope,
) {
    private val writeMutex = Mutex()
    private val recoveryStarted = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val mutableProgress = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())

    /**
     * Application-owned state is the runtime source of truth. Room and the atomic backup
     * are persistence layers; a failure in either one must not break the child UI.
     */
    val progress: StateFlow<Map<String, EpisodeProgress>> = mutableProgress.asStateFlow()

    fun warmUp(): Job = applicationScope.launch {
        if (!recoveryStarted.compareAndSet(false, true)) return@launch
        writeMutex.withLock { loadInitialStateUnlocked() }
    }

    suspend fun snapshot(): Map<String, EpisodeProgress> = writeMutex.withLock {
        if (!initialized.get()) loadInitialStateUnlocked()
        mutableProgress.value
    }

    suspend fun get(episodeId: String): EpisodeProgress? = writeMutex.withLock {
        mutableProgress.value[episodeId]?.let { return@withLock it }
        val databaseValue = runCatching { dao.get(episodeId) }
            .onFailure { Log.e(TAG, "Room get failed", it) }
            .getOrNull()
        val entity = databaseValue ?: runCatching { backupStore.get(episodeId) }
            .onFailure { Log.e(TAG, "Progress backup get failed", it) }
            .getOrNull()
        entity?.toDomain()?.also { domain ->
            mutableProgress.value = mutableProgress.value + (episodeId to domain)
        }
    }

    suspend fun clear() = writeMutex.withLock {
        mutableProgress.value = emptyMap()
        initialized.set(true)
        runCatching { backupStore.clear() }
            .onFailure { Log.e(TAG, "Progress backup clear failed", it) }
        runCatching { dao.clear() }
            .onFailure { Log.e(TAG, "Room clear failed", it) }
    }

    suspend fun save(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        persist(
            PlaybackProgressPolicy.evaluate(
                episodeId = episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
                lastPlayedAtEpochMs = nowEpochMs,
            ).toEntity(),
            backupRequired = false,
        )
    }

    suspend fun checkpoint(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        persist(
            PlaybackProgressPolicy.evaluate(
                episodeId = episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
                lastPlayedAtEpochMs = nowEpochMs,
            ).toEntity(),
            backupRequired = true,
        )
    }

    fun enqueueCheckpoint(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Job = applicationScope.launch {
        checkpoint(episodeId, positionMs, durationMs, nowEpochMs)
    }

    suspend fun markCompleted(
        episodeId: String,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        persist(
            PlaybackProgressEntity(
                episodeId = episodeId,
                positionMs = 0L,
                durationMs = durationMs.coerceAtLeast(0L),
                completed = true,
                lastPlayedAtEpochMs = nowEpochMs,
            ),
            backupRequired = true,
        )
    }

    suspend fun prune(validEpisodeIds: Set<String>) = writeMutex.withLock {
        if (!initialized.get()) loadInitialStateUnlocked()
        if (validEpisodeIds.isEmpty()) {
            mutableProgress.value = emptyMap()
            runCatching { backupStore.clear() }
                .onFailure { Log.e(TAG, "Progress backup prune clear failed", it) }
            runCatching { dao.clear() }
                .onFailure { Log.e(TAG, "Room prune clear failed", it) }
            return@withLock
        }

        val staleIds = mutableProgress.value.keys.filterNot(validEpisodeIds::contains)
        if (staleIds.isEmpty()) return@withLock
        mutableProgress.value = mutableProgress.value - staleIds.toSet()
        runCatching { backupStore.deleteByIds(staleIds) }
            .onFailure { Log.e(TAG, "Progress backup prune failed", it) }
        staleIds.chunked(400).forEach { chunk ->
            runCatching { dao.deleteByIds(chunk) }
                .onFailure { Log.e(TAG, "Room prune failed", it) }
        }
    }

    private suspend fun persist(entity: PlaybackProgressEntity, backupRequired: Boolean) = writeMutex.withLock {
        if (!initialized.get()) loadInitialStateUnlocked()
        val current = mutableProgress.value[entity.episodeId]
        if (current != null && entity.lastPlayedAtEpochMs < current.lastPlayedAtEpochMs) return@withLock

        // Publish to the UI first. Persistence failures remain recoverable and must not freeze the app.
        mutableProgress.value = mutableProgress.value + (entity.episodeId to entity.toDomain())

        if (backupRequired) {
            runCatching { backupStore.upsertIfNewer(entity) }
                .onFailure { Log.e(TAG, "Critical progress backup write failed", it) }
        }
        val databaseWrite = runCatching { dao.upsert(entity) }
            .onFailure { Log.e(TAG, "Room progress write failed; backup retained", it) }
        if (databaseWrite.isFailure && !backupRequired) {
            runCatching { backupStore.upsertIfNewer(entity) }
                .onFailure { Log.e(TAG, "Fallback progress backup write failed", it) }
        }
    }

    private suspend fun loadInitialStateUnlocked() {
        if (initialized.get()) return
        val backup = runCatching { backupStore.loadAll() }
            .onFailure { Log.e(TAG, "Progress backup read failed", it) }
            .getOrDefault(emptyList())
        val database = runCatching { dao.getAll() }
            .onFailure { Log.e(TAG, "Room startup read failed; continuing from backup", it) }
            .getOrNull()

        val merged = linkedMapOf<String, PlaybackProgressEntity>()
        backup.forEach { mergeNewer(merged, it) }
        database.orEmpty().forEach { mergeNewer(merged, it) }
        mutableProgress.value = merged.values.associate { it.episodeId to it.toDomain() }
        initialized.set(true)

        if (database != null && backup.isNotEmpty()) {
            backup.forEach { backupEntity ->
                val databaseEntity = database.firstOrNull { it.episodeId == backupEntity.episodeId }
                if (databaseEntity == null || backupEntity.lastPlayedAtEpochMs > databaseEntity.lastPlayedAtEpochMs) {
                    runCatching { dao.upsert(backupEntity) }
                        .onFailure { Log.e(TAG, "Room backup restore failed", it) }
                }
            }
        }
    }

    private fun mergeNewer(
        target: MutableMap<String, PlaybackProgressEntity>,
        entity: PlaybackProgressEntity,
    ) {
        val current = target[entity.episodeId]
        if (current == null || entity.lastPlayedAtEpochMs >= current.lastPlayedAtEpochMs) {
            target[entity.episodeId] = entity
        }
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

    companion object {
        private const val TAG = "MeheneProgress"
    }
}
