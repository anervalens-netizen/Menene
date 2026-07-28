package ro.mehene.app.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import ro.mehene.app.db.PlaybackProgressEntity
import org.json.JSONArray
import org.json.JSONObject

class ProgressBackupStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, FILE_NAME))
    private val lock = Any()
    private var loaded = false
    private var clearedAtEpochMs = 0L
    private val entities = linkedMapOf<String, PlaybackProgressEntity>()

    fun loadAll(): List<PlaybackProgressEntity> = synchronized(lock) {
        ensureLoaded()
        entities.values.sortedByDescending(PlaybackProgressEntity::lastPlayedAtEpochMs)
    }

    fun clearedAtEpochMs(): Long = synchronized(lock) {
        ensureLoaded()
        clearedAtEpochMs
    }

    fun get(episodeId: String): PlaybackProgressEntity? = synchronized(lock) {
        ensureLoaded()
        entities[episodeId]
    }

    fun upsertIfNewer(entity: PlaybackProgressEntity) = synchronized(lock) {
        ensureLoaded()
        if (entity.lastPlayedAtEpochMs < clearedAtEpochMs) return@synchronized
        val existing = entities[entity.episodeId]
        if (existing == null || entity.lastPlayedAtEpochMs >= existing.lastPlayedAtEpochMs) {
            entities[entity.episodeId] = entity
            persist()
        }
    }

    fun replaceAll(values: Collection<PlaybackProgressEntity>) = synchronized(lock) {
        entities.clear()
        clearedAtEpochMs = 0L
        values.forEach { entity ->
            val existing = entities[entity.episodeId]
            if (existing == null || entity.lastPlayedAtEpochMs >= existing.lastPlayedAtEpochMs) {
                entities[entity.episodeId] = entity
            }
        }
        loaded = true
        persist()
    }

    fun deleteByIds(episodeIds: Collection<String>) = synchronized(lock) {
        ensureLoaded()
        if (episodeIds.fold(false) { changed, id -> entities.remove(id) != null || changed }) persist()
    }

    fun clear(nowEpochMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        entities.clear()
        clearedAtEpochMs = nowEpochMs.coerceAtLeast(clearedAtEpochMs)
        loaded = true
        persist()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!atomicFile.baseFile.isFile) return
        runCatching {
            val root = atomicFile.openRead().bufferedReader().use { JSONObject(it.readText()) }
            require(root.optInt("schemaVersion") == SCHEMA_VERSION)
            clearedAtEpochMs = root.optLong("clearedAtEpochMs")
            val array = root.optJSONArray("progress") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val entity = PlaybackProgressEntity(
                    episodeId = item.getString("episodeId"),
                    positionMs = item.optLong("positionMs"),
                    durationMs = item.optLong("durationMs"),
                    completed = item.optBoolean("completed"),
                    lastPlayedAtEpochMs = item.optLong("lastPlayedAtEpochMs"),
                )
                if (entity.episodeId.isNotBlank()) entities[entity.episodeId] = entity
            }
        }.onFailure {
            entities.clear()
            clearedAtEpochMs = 0L
            atomicFile.delete()
        }
    }

    private fun persist() {
        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("clearedAtEpochMs", clearedAtEpochMs)
            put("progress", JSONArray().apply {
                entities.values.forEach { entity ->
                    put(JSONObject().apply {
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

    companion object {
        private const val FILE_NAME = "mehene-progress-backup.json"
        private const val SCHEMA_VERSION = 1
    }
}
