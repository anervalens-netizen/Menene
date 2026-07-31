package ro.menene.app.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeneneDatabaseSchemaTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeneneDatabase::class.java,
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: MeneneDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun exportedVersionOneSchemaOpensWithCurrentRoomDatabase() = runBlocking {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO playback_progress (
                    episodeId,
                    positionMs,
                    durationMs,
                    completed,
                    lastPlayedAtEpochMs
                ) VALUES ('episode-1', 1200, 6000, 0, 42)
                """.trimIndent(),
            )
            close()
        }
        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MeneneDatabase.MIGRATION_1_2,
        ).close()

        database = Room.databaseBuilder(
            context,
            MeneneDatabase::class.java,
            TEST_DATABASE,
        ).addMigrations(MeneneDatabase.MIGRATION_1_2).build()

        assertEquals(1200L, database!!.playbackProgressDao().get("legacy", "episode-1")?.positionMs)
    }

    companion object {
        private const val TEST_DATABASE = "menene-schema-test"
    }
}
