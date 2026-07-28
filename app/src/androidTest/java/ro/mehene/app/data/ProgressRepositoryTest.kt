package ro.mehene.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
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
import ro.mehene.app.db.MeheneDatabase

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: MeheneDatabase
    private lateinit var backupStore: ProgressBackupStore
    private lateinit var repository: ProgressRepository
    private lateinit var backupFile: java.io.File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        backupFile = context.filesDir.resolve("mehene-progress-backup.json")
        backupFile.delete()
        database = Room.inMemoryDatabaseBuilder(context, MeheneDatabase::class.java)
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
        repository.checkpoint("e1", 80, 100, nowEpochMs = 200)
        repository.checkpoint("e1", 20, 100, nowEpochMs = 100)
        assertEquals(80, repository.get("e1")?.positionMs)
    }

    @Test
    fun runtimeStateUpdatesWhenCheckpointIsWritten() = runBlocking {
        repository.checkpoint("e1", 40, 100, nowEpochMs = 100)
        assertEquals(40, repository.progress.value["e1"]?.positionMs)
        assertTrue(repository.progress.value.containsKey("e1"))
    }

    @Test
    fun criticalCheckpointIsAvailableFromBackup() = runBlocking {
        repository.checkpoint("e1", 40, 100, nowEpochMs = 100)
        assertEquals(40, backupStore.get("e1")?.positionMs)
    }

    @Test
    fun delayedCheckpointCannotUndoReset() = runBlocking {
        repository.checkpoint("e1", 40, 100, nowEpochMs = 100)
        backupStore.clear(nowEpochMs = 200)
        repository.clear()
        repository.checkpoint("e1", 80, 100, nowEpochMs = 150)
        assertNull(repository.get("e1"))
    }
}
