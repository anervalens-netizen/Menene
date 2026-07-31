package ro.menene.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ro.menene.app.db.MeneneDatabase
import ro.menene.app.db.PlaybackProgressDao
import ro.menene.app.db.PlaybackProgressEntity
import ro.menene.app.db.ResilientPlaybackProgressDao

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: MeneneDatabase
    private lateinit var backupStore: ProgressBackupStore
    private lateinit var repository: ProgressRepository
    private lateinit var backupFile: java.io.File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        backupFile = context.filesDir.resolve("menene-progress-backup.json")
        backupFile.delete()
        database = Room.inMemoryDatabaseBuilder(context, MeneneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupStore = ProgressBackupStore(context)
        repository = ProgressRepository(
            dao = database.playbackProgressDao(),
            backupStore = backupStore,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    @After
    fun tearDown() {
        backupFile.delete()
        database.close()
    }

    @Test
    fun olderCheckpointCannotOverwriteNewerProgress() = runBlocking {
        repository.checkpoint("e1", 8_000, 10_000, nowEpochMs = 200)
        repository.checkpoint("e1", 6_000, 10_000, nowEpochMs = 100)
        assertEquals(8_000L, repository.get("e1")?.positionMs)
    }

    @Test
    fun runtimeStateUpdatesWhenCheckpointIsWritten() = runBlocking {
        repository.checkpoint("e1", 6_000, 10_000, nowEpochMs = 100)
        assertEquals(6_000L, repository.progress.value["e1"]?.positionMs)
        assertTrue(repository.progress.value.containsKey("e1"))
    }

    @Test
    fun criticalCheckpointIsAvailableFromBackup() = runBlocking {
        repository.checkpoint("e1", 6_000, 10_000, nowEpochMs = 100)
        assertEquals(6_000L, backupStore.get("legacy", "e1")?.positionMs)
    }

    @Test
    fun delayedCheckpointCannotUndoReset() = runBlocking {
        repository.checkpoint("e1", 6_000, 10_000, nowEpochMs = 100)
        backupStore.clear("legacy", nowEpochMs = 200)
        repository.clear()
        repository.checkpoint("e1", 8_000, 10_000, nowEpochMs = 150)
        assertNull(repository.get("e1"))
    }

    @Test
    fun switchingLibrariesRetainsProgressAndEmptyLibraryDoesNotPrune() = runBlocking {
        repository.activateLibrary("library-a")
        repository.checkpoint("episode-a", 6_000, 10_000, nowEpochMs = 100)

        repository.activateLibrary("library-b")
        assertNull(repository.get("episode-a"))
        repository.checkpoint("episode-b", 7_000, 10_000, nowEpochMs = 200)

        repository.activateLibrary("library-empty")
        assertTrue(repository.snapshot().isEmpty())
        repository.activateLibrary("library-a")
        assertEquals(6_000L, repository.get("episode-a")?.positionMs)
        repository.activateLibrary("library-b")
        assertEquals(7_000L, repository.get("episode-b")?.positionMs)
    }

    @Test
    fun criticalCheckpointWritesBackupBeforeDaoCompletion() = runBlocking {
        val job = repository.checkpointCritical("episode-close", 7_000, 10_000, nowEpochMs = 300)
        assertEquals(7_000L, backupStore.get("legacy", "episode-close")?.positionMs)
        job.join()
    }

    @Test
    fun queryFailureSwitchesToFallbackAndRestartReadsAtomicBackup() = runBlocking {
        val resilient = ResilientPlaybackProgressDao(ThrowingDao())
        val fallbackRepository = ProgressRepository(
            dao = resilient,
            backupStore = ProgressBackupStore(context),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        assertTrue(!resilient.healthProbe())
        fallbackRepository.activateLibrary("library-failure")
        fallbackRepository.checkpoint("episode-failure", 8_000, 10_000, nowEpochMs = 400)
        assertTrue(resilient.isUsingFallback())
        assertEquals(8_000L, backupStore.get("library-failure", "episode-failure")?.positionMs)

        val restarted = ProgressRepository(
            dao = ResilientPlaybackProgressDao(ThrowingDao()),
            backupStore = ProgressBackupStore(context),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        restarted.activateLibrary("library-failure")
        assertEquals(8_000L, restarted.get("episode-failure")?.positionMs)
    }

    @Test
    fun backupRestoresIntoEmptyDatabaseAndLegacyProgressIsVisibleInFirstLibrary() = runBlocking {
        val legacy = PlaybackProgressEntity(LibraryId.LEGACY, "episode-legacy", 33, 100, false, 500)
        backupStore.upsertIfNewer(legacy)
        assertTrue(database.playbackProgressDao().getAll(LibraryId.LEGACY).isEmpty())
        assertTrue(database.playbackProgressDao().getAll("library-first").isEmpty())
        repository.activateLibrary("library-first")
        assertEquals(33L, repository.get("episode-legacy")?.positionMs)
        assertEquals(33L, database.playbackProgressDao().get("library-first", "episode-legacy")?.positionMs)
        val restarted = ProgressRepository(database.playbackProgressDao(), ProgressBackupStore(context), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        restarted.activateLibrary("library-first")
        assertEquals(33L, restarted.get("episode-legacy")?.positionMs)
    }

    @Test
    fun legacyRoomProgressBecomesVisibleInFirstLibrary() = runBlocking {
        val legacy = PlaybackProgressEntity(LibraryId.LEGACY, "episode-v1", 33, 100, false, 500)
        database.playbackProgressDao().upsert(legacy)

        repository.activateLibrary("library-first")

        assertEquals(33L, repository.get("episode-v1")?.positionMs)
        assertEquals(33L, database.playbackProgressDao().get(LibraryId.LEGACY, "episode-v1")?.positionMs)
        assertEquals(33L, database.playbackProgressDao().get("library-first", "episode-v1")?.positionMs)
    }

    @Test
    fun legacyRoomMigrationReplaysAfterMarkerProcessDeathWithoutOverwritingNewerTarget() = runBlocking {
        val dao = database.playbackProgressDao()
        dao.upsert(PlaybackProgressEntity(LibraryId.LEGACY, "episode-v1-killed", 33, 100, false, 500))
        dao.upsert(PlaybackProgressEntity(LibraryId.LEGACY, "episode-newer", 33, 100, false, 500))
        dao.upsert(PlaybackProgressEntity("library-first", "episode-newer", 44, 100, false, 600))

        // Simulates process death after the atomic marker write but before the Room copy.
        assertEquals("library-first", backupStore.migrateLegacyTo("library-first"))

        val restarted = ProgressRepository(
            dao,
            ProgressBackupStore(context),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        restarted.activateLibrary("library-first")

        assertEquals(33L, restarted.get("episode-v1-killed")?.positionMs)
        assertEquals(33L, dao.get("library-first", "episode-v1-killed")?.positionMs)
        assertEquals(44L, restarted.get("episode-newer")?.positionMs)
        assertEquals(44L, dao.get("library-first", "episode-newer")?.positionMs)
    }

    @Test
    fun criticalCheckpointWritesBackupBeforeWaitingForDaoMutex() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blockedBackup = ProgressBackupStore(context)
        val blockedRepository = ProgressRepository(BlockingDao(started, release), blockedBackup, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        blockedRepository.activateLibrary("library-mutex")
        val first = blockedRepository.checkpointCritical("episode-first", 6_000, 10_000, nowEpochMs = 100)
        withTimeout(5000) { started.await() }
        val second = blockedRepository.checkpointCritical("episode-second", 7_000, 10_000, nowEpochMs = 200)
        assertEquals(7_000L, blockedBackup.get("library-mutex", "episode-second")?.positionMs)
        val processRestart = ProgressRepository(database.playbackProgressDao(), blockedBackup, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        processRestart.activateLibrary("library-mutex")
        assertEquals(7_000L, processRestart.get("episode-second")?.positionMs)
        release.complete(Unit)
        first.join()
        second.join()
    }

    @Test
    fun queryTimeFailureSwitchesResilientDaoToFallback() = runBlocking {
        val resilient = ResilientPlaybackProgressDao(ThrowingFlowDao())
        assertTrue(resilient.observeAll("library-query").first().isEmpty())
        assertTrue(resilient.isUsingFallback())
    }

    private class BlockingDao(
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
    ) : PlaybackProgressDao {
        override fun observeAll(libraryId: String): Flow<List<PlaybackProgressEntity>> = emptyFlow()
        override suspend fun getAll(libraryId: String): List<PlaybackProgressEntity> = emptyList()
        override suspend fun get(libraryId: String, episodeId: String): PlaybackProgressEntity? = null
        override suspend fun upsert(entity: PlaybackProgressEntity) {
            started.complete(Unit)
            release.await()
        }
        override suspend fun deleteByIds(libraryId: String, episodeIds: List<String>) = Unit
        override suspend fun clear(libraryId: String) = Unit
    }

    private class ThrowingFlowDao : PlaybackProgressDao {
        override fun observeAll(libraryId: String): Flow<List<PlaybackProgressEntity>> = flow { error("query-time failure") }
        override suspend fun getAll(libraryId: String): List<PlaybackProgressEntity> = emptyList()
        override suspend fun get(libraryId: String, episodeId: String): PlaybackProgressEntity? = null
        override suspend fun upsert(entity: PlaybackProgressEntity): Unit = Unit
        override suspend fun deleteByIds(libraryId: String, episodeIds: List<String>): Unit = Unit
        override suspend fun clear(libraryId: String): Unit = Unit
    }

    private class ThrowingDao : PlaybackProgressDao {
        override fun observeAll(libraryId: String): Flow<List<PlaybackProgressEntity>> = emptyFlow()
        override suspend fun getAll(libraryId: String): List<PlaybackProgressEntity> = error("query failure")
        override suspend fun get(libraryId: String, episodeId: String): PlaybackProgressEntity? = error("query failure")
        override suspend fun upsert(entity: PlaybackProgressEntity): Unit = error("write failure")
        override suspend fun deleteByIds(libraryId: String, episodeIds: List<String>): Unit = error("delete failure")
        override suspend fun clear(libraryId: String): Unit = error("clear failure")
    }
}
