package ro.mehene.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MeheneDatabase : RoomDatabase() {
    abstract fun playbackProgressDao(): PlaybackProgressDao

    companion object {
        @Volatile
        private var instance: MeheneDatabase? = null

        fun get(context: Context): MeheneDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MeheneDatabase::class.java,
                "mehene.db",
            ).build().also { instance = it }
        }
    }
}
