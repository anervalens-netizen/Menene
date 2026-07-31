package ro.menene.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ro.menene.app.data.CatalogCacheStore
import ro.menene.app.data.LibraryRepository
import ro.menene.app.data.ProgressBackupStore
import ro.menene.app.data.ProgressRepository
import ro.menene.app.data.SettingsRepository
import ro.menene.app.db.ResilientPlaybackProgressDao
import ro.menene.app.db.MeneneDatabase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackProgressDao by lazy {
        val primary = runCatching { MeneneDatabase.get(appContext).playbackProgressDao() }
            .onFailure { Log.e("MeneneDatabase", "Room unavailable; using in-memory progress DAO", it) }
            .getOrNull()
        ResilientPlaybackProgressDao(primary)
    }

    val settingsRepository by lazy { SettingsRepository(appContext) }
    val progressRepository by lazy {
        ProgressRepository(
            dao = playbackProgressDao,
            backupStore = ProgressBackupStore(appContext),
            applicationScope = applicationScope,
        )
    }
    val libraryRepository by lazy { LibraryRepository(appContext, CatalogCacheStore(appContext)) }

    fun warmUp() {
        progressRepository.warmUp()
    }
}

fun Context.meneneContainer(): AppContainer =
    (applicationContext as MeneneApplication).container
