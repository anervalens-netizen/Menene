package ro.mehene.app.data

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.mehene.app.db.PlaybackProgressDao
import ro.mehene.app.db.PlaybackProgressEntity
import ro.mehene.app.db.ResilientPlaybackProgressDao

class ProgressRepository(
    private val dao: PlaybackProgressDao,
    private val backupStore: ProgressBackupStore,
    private val applicationScope: CoroutineScope,
) {
    private val writeMutex = Mutex()
    private val recoveryStarted = AtomicBoolean(false)
    private val mutableProgress = kotlinx.coroutines.flow.MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())

    @Volatile
    private var activeLibraryId: String? = null
    private var initialized = false

    /** The active library namespace is the only progress exposed to the UI. */
    val progress: kotlinx.coroutines.flow.StateFlow<Map<String, EpisodeProgress>> = mutableProgress

    fun warmUp(): Job = applicationScope.launch {
        if (!recoveryStarted.compareAndSet(false, true)) return@launch
        if (dao is ResilientPlaybackProgressDao) dao.healthProbe()
    }

    suspend fun activateLibrary(libraryId: String) = writeMutex.withLock {
        require(libraryId.isNotBlank())
        if (initialized && activeLibraryId == libraryId) return@withLock
        val legacyTargetLibraryId = if (libraryId != LibraryId.LEGACY) {
            runCatching { backupStore.migrateLegacyTo(libraryId) }
                .onFailure { Log.e(TAG, "Legacy backup target migration failed", it) }
                .getOrNull()
        } else {
            null
        }
        if (legacyTargetLibraryId != null) {
            runCatching { dao.getAll(LibraryId.LEGACY) }
                .onFailure { Log.e(TAG, "Legacy Room progress read failed", it) }
                .getOrDefault(emptyList())
                .forEach { legacy ->
                    val migrated = legacy.copy(libraryId = legacyTargetLibraryId)
                    val current = runCatching { dao.get(legacyTargetLibraryId, legacy.episodeId) }
                        .onFailure { Log.e(TAG, "Legacy target progress read failed", it) }
                        .getOrNull()
                    if (current == null || migrated.lastPlayedAtEpochMs >= current.lastPlayedAtEpochMs) {
                        runCatching { dao.upsert(migrated) }
                            .onFailure { Log.e(TAG, "Legacy Room progress migration failed", it) }
                    }
                    runCatching { backupStore.upsertIfNewer(migrated) }
                        .onFailure { Log.e(TAG, "Legacy backup progress migration failed", it) }
                }
        }
        activeLibraryId = libraryId
        mutableProgress.value = loadStateUnlocked(libraryId)
        initialized = true
    }

    suspend fun snapshot(): Map<String, EpisodeProgress> = writeMutex.withLock {
        ensureActiveLibraryUnlocked()
        mutableProgress.value
    }

    suspend fun get(episodeId: String): EpisodeProgress? = writeMutex.withLock {
        ensureActiveLibraryUnlocked()
        mutableProgress.value[episodeId]?.let { return@withLock it }
        val libraryId = requireNotNull(activeLibraryId)
        val clearedAtEpochMs = runCatching { backupStore.clearedAtEpochMs(libraryId) }.getOrDefault(0L)
        val databaseValue = runCatching { dao.get(libraryId, episodeId) }
            .onFailure { Log.e(TAG, "Room get failed", it) }
            .getOrNull()
            ?.takeIf { it.lastPlayedAtEpochMs >= clearedAtEpochMs }
        val entity = databaseValue ?: runCatching { backupStore.get(libraryId, episodeId) }
            .onFailure { Log.e(TAG, "Progress backup get failed", it) }
            .getOrNull()
        entity?.toDomain()?.also { domain ->
            mutableProgress.value = mutableProgress.value + (episodeId to domain)
        }
    }

    suspend fun clear() = writeMutex.withLock {
        ensureActiveLibraryUnlocked()
        val libraryId = requireNotNull(activeLibraryId)
        mutableProgress.value = emptyMap()
        initialized = true
        runCatching { backupStore.clear(libraryId) }
            .onFailure { Log.e(TAG, "Progress backup clear failed", it) }
        runCatching { dao.clear(libraryId) }
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

    fun checkpointCritical(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Job {
        val entity = PlaybackProgressPolicy.evaluate(
            episodeId = episodeId,
            positionMs = positionMs,
            durationMs = durationMs,
            lastPlayedAtEpochMs = nowEpochMs,
        ).toEntity(activeLibraryId ?: LibraryId.LEGACY)
        runCatching { backupStore.upsertIfNewer(entity) }
            .onFailure { Log.e(TAG, "Critical progress backup write failed before close", it) }
        return applicationScope.launch {
            writeMutex.withLock {
                ensureActiveLibraryUnlocked()
                if (activeLibraryId == entity.libraryId) {
                    mutableProgress.value = mutableProgress.value + (entity.episodeId to entity.toDomain())
                }
                runCatching { dao.upsert(entity) }
                    .onFailure { Log.e(TAG, "Room critical progress write failed; backup retained", it) }
            }
        }
    }

    suspend fun markCompleted(
        episodeId: String,
        durationMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        persist(
            PlaybackProgressEntity(
                libraryId = requireActiveLibrary(),
                episodeId = episodeId,
                positionMs = 0L,
                durationMs = durationMs.coerceAtLeast(0L),
                completed = true,
                lastPlayedAtEpochMs = nowEpochMs,
            ),
            backupRequired = true,
        )
    }

    private suspend fun persist(entity: PlaybackProgressEntity, backupRequired: Boolean) = writeMutex.withLock {
        ensureActiveLibraryUnlocked()
        val libraryId = requireNotNull(activeLibraryId)
        val namespaced = entity.copy(libraryId = libraryId)
        val clearedAtEpochMs = runCatching { backupStore.clearedAtEpochMs(libraryId) }.getOrDefault(0L)
        if (namespaced.lastPlayedAtEpochMs < clearedAtEpochMs) return@withLock
        val current = mutableProgress.value[namespaced.episodeId]
        if (current != null && namespaced.lastPlayedAtEpochMs < current.lastPlayedAtEpochMs) return@withLock

        mutableProgress.value = mutableProgress.value + (namespaced.episodeId to namespaced.toDomain())
        if (backupRequired) {
            runCatching { backupStore.upsertIfNewer(namespaced) }
                .onFailure { Log.e(TAG, "Critical progress backup write failed", it) }
        }
        val databaseWrite = runCatching { dao.upsert(namespaced) }
            .onFailure { Log.e(TAG, "Room progress write failed; backup retained", it) }
        if (databaseWrite.isFailure && !backupRequired) {
            runCatching { backupStore.upsertIfNewer(namespaced) }
                .onFailure { Log.e(TAG, "Fallback progress backup write failed", it) }
        }
    }

    private suspend fun ensureActiveLibraryUnlocked() {
        if (!initialized) {
            activeLibraryId = LibraryId.LEGACY
            mutableProgress.value = loadStateUnlocked(LibraryId.LEGACY)
            initialized = true
        }
    }

    private suspend fun loadStateUnlocked(libraryId: String): Map<String, EpisodeProgress> {
        val clearedAtEpochMs = runCatching { backupStore.clearedAtEpochMs(libraryId) }
            .onFailure { Log.e(TAG, "Progress reset marker read failed", it) }
            .getOrDefault(0L)
        val backup = runCatching { backupStore.loadAll(libraryId) }
            .onFailure { Log.e(TAG, "Progress backup read failed", it) }
            .getOrDefault(emptyList())
        val database = runCatching { dao.getAll(libraryId) }
            .onFailure { Log.e(TAG, "Room progress read failed; continuing from backup", it) }
            .getOrDefault(emptyList())

        val merged = linkedMapOf<String, PlaybackProgressEntity>()
        backup.filter { it.lastPlayedAtEpochMs >= clearedAtEpochMs }.forEach { mergeNewer(merged, it) }
        database.filter { it.lastPlayedAtEpochMs >= clearedAtEpochMs }.forEach { mergeNewer(merged, it) }
        backup.forEach { backupEntity ->
                val databaseEntity = database.firstOrNull { it.episodeId == backupEntity.episodeId }
                if (backupEntity.lastPlayedAtEpochMs >= clearedAtEpochMs &&
                    (databaseEntity == null || backupEntity.lastPlayedAtEpochMs > databaseEntity.lastPlayedAtEpochMs)
                ) {
                    runCatching { dao.upsert(backupEntity) }
                        .onFailure { Log.e(TAG, "Room backup restore failed", it) }
                }
            }
        return merged.values.associate { it.episodeId to it.toDomain() }
    }

    private fun mergeNewer(target: MutableMap<String, PlaybackProgressEntity>, entity: PlaybackProgressEntity) {
        val current = target[entity.episodeId]
        if (current == null || entity.lastPlayedAtEpochMs >= current.lastPlayedAtEpochMs) {
            target[entity.episodeId] = entity
        }
    }

    private fun requireActiveLibrary(): String = activeLibraryId ?: LibraryId.LEGACY

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

    private fun EpisodeProgress.toEntity(libraryId: String = requireActiveLibrary()): PlaybackProgressEntity = PlaybackProgressEntity(
        libraryId = libraryId,
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
