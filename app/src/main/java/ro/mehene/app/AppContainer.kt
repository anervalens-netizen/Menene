package ro.mehene.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ro.mehene.app.data.CatalogCacheStore
import ro.mehene.app.data.LibraryRepository
import ro.mehene.app.data.ProgressBackupStore
import ro.mehene.app.data.ProgressRepository
import ro.mehene.app.data.SettingsRepository
import ro.mehene.app.db.MeheneDatabase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { MeheneDatabase.get(appContext) }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val progressRepository by lazy {
        ProgressRepository(
            dao = database.playbackProgressDao(),
            backupStore = ProgressBackupStore(appContext),
            applicationScope = applicationScope,
        )
    }
    val libraryRepository by lazy { LibraryRepository(appContext, CatalogCacheStore(appContext)) }

    fun warmUp() {
        progressRepository.warmUp()
    }
}

fun Context.meheneContainer(): AppContainer =
    (applicationContext as MeheneApplication).container
