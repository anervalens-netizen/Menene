package ro.mehene.app

import android.content.Context
import ro.mehene.app.data.CatalogCacheStore
import ro.mehene.app.data.LibraryRepository
import ro.mehene.app.data.ProgressRepository
import ro.mehene.app.data.SettingsRepository
import ro.mehene.app.db.MeheneDatabase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { MeheneDatabase.get(appContext) }

    val settingsRepository by lazy { SettingsRepository(appContext) }
    val progressRepository by lazy { ProgressRepository(database.playbackProgressDao()) }
    val libraryRepository by lazy { LibraryRepository(appContext, CatalogCacheStore(appContext)) }
}

fun Context.meheneContainer(): AppContainer =
    (applicationContext as MeheneApplication).container
