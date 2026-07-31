package ro.menene.app.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import ro.menene.app.db.PlaybackProgressEntity

class ProgressBackupStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))
    private val lock = Any()
    private var loaded = false
    private val clearedAtEpochMsByLibrary = linkedMapOf<String, Long>()
    private val entities = linkedMapOf<Key, PlaybackProgressEntity>()
    private var legacyMigratedTo: String? = null

    fun loadAll(libraryId: String): List<PlaybackProgressEntity> = synchronized(lock) {
        ensureLoaded()
        entities.values.filter { it.libraryId == libraryId }
            .sortedByDescending(PlaybackProgressEntity::lastPlayedAtEpochMs)
    }

    fun clearedAtEpochMs(libraryId: String): Long = synchronized(lock) {
        ensureLoaded()
        clearedAtEpochMsByLibrary[libraryId] ?: 0L
    }

    fun get(libraryId: String, episodeId: String): PlaybackProgressEntity? = synchronized(lock) {
        ensureLoaded()
        entities[Key(libraryId, episodeId)]
    }

    /**
     * Pins the first real library as the legacy target and returns it on every later call. Replaying
     * the Room copy is intentionally idempotent so process death after this atomic marker is safe.
     */
    fun migrateLegacyTo(libraryId: String): String? = synchronized(lock) {
        ensureLoaded()
        if (libraryId == LibraryId.LEGACY) return@synchronized null
        val targetLibraryId = legacyMigratedTo ?: libraryId
        entities.values.filter { it.libraryId == LibraryId.LEGACY }.forEach { legacy ->
            val target = legacy.copy(libraryId = targetLibraryId)
            val key = Key(targetLibraryId, target.episodeId)
            val existing = entities[key]
            if (existing == null || target.lastPlayedAtEpochMs >= existing.lastPlayedAtEpochMs) {
                entities[key] = target
            }
        }
        legacyMigratedTo = targetLibraryId
        persist()
        targetLibraryId
    }

    fun upsertIfNewer(entity: PlaybackProgressEntity) = synchronized(lock) {
        ensureLoaded()
        if (entity.lastPlayedAtEpochMs < clearedAtEpochMs(entity.libraryId)) return@synchronized
        val key = Key(entity.libraryId, entity.episodeId)
        val existing = entities[key]
        if (existing == null || entity.lastPlayedAtEpochMs >= existing.lastPlayedAtEpochMs) {
            entities[key] = entity
            persist()
        }
    }

    fun replaceAll(values: Collection<PlaybackProgressEntity>) = synchronized(lock) {
        entities.clear()
        clearedAtEpochMsByLibrary.clear()
        legacyMigratedTo = null
        values.forEach { entity ->
            val key = Key(entity.libraryId, entity.episodeId)
            val existing = entities[key]
            if (existing == null || entity.lastPlayedAtEpochMs >= existing.lastPlayedAtEpochMs) {
                entities[key] = entity
            }
        }
        loaded = true
        persist()
    }

    fun deleteByIds(libraryId: String, episodeIds: Collection<String>) = synchronized(lock) {
        ensureLoaded()
        val ids = episodeIds.toSet()
        var changed = false
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.libraryId == libraryId && entry.key.episodeId in ids) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) persist()
    }

    fun clear(libraryId: String, nowEpochMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        ensureLoaded()
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.libraryId == libraryId) iterator.remove()
        }
        clearedAtEpochMsByLibrary[libraryId] = nowEpochMs.coerceAtLeast(clearedAtEpochMs(libraryId))
        persist()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!atomicFile.baseFile.isFile) return
        runCatching {
            val root = atomicFile.openRead().bufferedReader().use { JSONObject(it.readText()) }
            val schemaVersion = root.optInt("schemaVersion")
            require(schemaVersion == LEGACY_SCHEMA_VERSION || schemaVersion == SCHEMA_VERSION)
            if (schemaVersion == LEGACY_SCHEMA_VERSION) {
                clearedAtEpochMsByLibrary[LibraryId.LEGACY] = root.optLong("clearedAtEpochMs")
            } else {
                legacyMigratedTo = root.optString("legacyMigratedTo", "").takeIf { it.isNotBlank() }
                val cleared = root.optJSONObject("clearedAtEpochMsByLibrary")
                if (cleared != null) {
                    cleared.keys().forEach { libraryId ->
                        clearedAtEpochMsByLibrary[libraryId] = cleared.optLong(libraryId)
                    }
                }
            }
            val array = root.optJSONArray("progress") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val libraryId = if (schemaVersion == LEGACY_SCHEMA_VERSION) {
                    LibraryId.LEGACY
                } else {
                    item.optString("libraryId", LibraryId.LEGACY)
                }
                val entity = PlaybackProgressEntity(
                    libraryId = libraryId,
                    episodeId = item.getString("episodeId"),
                    positionMs = item.optLong("positionMs"),
                    durationMs = item.optLong("durationMs"),
                    completed = item.optBoolean("completed"),
                    lastPlayedAtEpochMs = item.optLong("lastPlayedAtEpochMs"),
                )
                if (entity.episodeId.isNotBlank()) entities[Key(entity.libraryId, entity.episodeId)] = entity
            }
        }.onFailure {
            entities.clear()
            clearedAtEpochMsByLibrary.clear()
            atomicFile.delete()
        }
    }

    private fun persist() {
        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("legacyMigratedTo", legacyMigratedTo ?: JSONObject.NULL)
            put("clearedAtEpochMsByLibrary", JSONObject().apply {
                clearedAtEpochMsByLibrary.forEach { (libraryId, timestamp) -> put(libraryId, timestamp) }
            })
            put("progress", JSONArray().apply {
                entities.values.forEach { entity ->
                    put(JSONObject().apply {
                        put("libraryId", entity.libraryId)
                        put("episodeId", entity.episodeId)
                        put("positionMs", entity.positionMs)
                        put("durationMs", entity.durationMs)
                        put("completed", entity.completed)
                        put("lastPlayedAtEpochMs", entity.lastPlayedAtEpochMs)
                    })
                }
            })
        }.toString()
        var output = atomicFile.startWrite()
        try {
            output.write(root.toByteArray(Charsets.UTF_8))
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private data class Key(val libraryId: String, val episodeId: String)

    companion object {
        private const val FILE_NAME = "menene-progress-backup.json"
        private const val LEGACY_SCHEMA_VERSION = 1
        private const val SCHEMA_VERSION = 2
    }
}
