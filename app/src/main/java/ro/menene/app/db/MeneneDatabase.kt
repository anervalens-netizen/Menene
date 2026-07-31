package ro.menene.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackProgressEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MeneneDatabase : RoomDatabase() {
    abstract fun playbackProgressDao(): PlaybackProgressDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playback_progress_v2 (
                        libraryId TEXT NOT NULL,
                        episodeId TEXT NOT NULL,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        lastPlayedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(libraryId, episodeId)
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO playback_progress_v2 (libraryId, episodeId, positionMs, durationMs, completed, lastPlayedAtEpochMs)
                    SELECT 'legacy', episodeId, positionMs, durationMs, completed, lastPlayedAtEpochMs
                    FROM playback_progress
                """.trimIndent())
                database.execSQL("DROP TABLE playback_progress")
                database.execSQL("ALTER TABLE playback_progress_v2 RENAME TO playback_progress")
            }
        }


        @Volatile
        private var instance: MeneneDatabase? = null

        fun get(context: Context): MeneneDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MeneneDatabase::class.java,
                "menene.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
