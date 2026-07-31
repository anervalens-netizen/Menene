package ro.mehene.app.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackProgressDaoTest {
    private lateinit var database: MeheneDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MeheneDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertReplacesProgress() = runBlocking {
        val dao = database.playbackProgressDao()
        dao.upsert(PlaybackProgressEntity("library-a", "e1", 10, 100, false, 1))
        dao.upsert(PlaybackProgressEntity("library-a", "e1", 50, 100, false, 2))
        assertEquals(50L, dao.get("library-a", "e1")?.positionMs)
    }
}
