package ro.menene.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ro.menene.app.model.CatalogSource
import ro.menene.app.model.EpisodeItem
import ro.menene.app.model.LibraryCatalog
import ro.menene.app.model.LibraryDiagnostics
import ro.menene.app.model.SeasonItem
import ro.menene.app.model.SeriesItem

@RunWith(AndroidJUnit4::class)
class CatalogCacheStoreTest {
    private lateinit var context: Context
    private lateinit var store: CatalogCacheStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CatalogCacheStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun fingerprintControlsCacheValidity() {
        store.save(catalog(), sourceFingerprint = "abc", nowEpochMs = 100)
        assertEquals(1, store.load("content://root", "abc", Long.MAX_VALUE, 101)?.episodeCount)
        assertNull(store.load("content://root", "other", Long.MAX_VALUE, 101))
    }

    @Test
    fun folderScanCacheExpires() {
        store.save(catalog(), sourceFingerprint = null, nowEpochMs = 100)
        assertNull(store.load("content://root", null, maxAgeMs = 10, nowEpochMs = 111))
    }

    @Test
    fun corruptedCacheIsDiscarded() {
        context.filesDir.resolve("menene-catalog-cache-v2.json").writeText("{broken")
        assertNull(store.load("content://root", null, Long.MAX_VALUE, 100))
        assertNull(store.load("content://root", null, Long.MAX_VALUE, 100))
    }

    private fun catalog(): LibraryCatalog {
        val episode = EpisodeItem(
            id = "e1",
            seriesId = "s1",
            seasonNumber = 1,
            seasonTitle = "Sezonul 1",
            number = 1,
            sortOrder = 1,
            title = "Pilot",
            mediaUri = "content://episode",
            subtitleUri = null,
            artworkUri = null,
            artworkVersion = 0,
        )
        return LibraryCatalog(
            rootUri = "content://root",
            series = listOf(
                SeriesItem(
                    id = "s1",
                    title = "Serial",
                    directoryUri = "content://series",
                    coverUri = null,
                    coverVersion = 0,
                    seasons = listOf(SeasonItem(1, "Sezonul 1", listOf(episode))),
                ),
            ),
            diagnostics = LibraryDiagnostics(seriesCount = 1, episodeCount = 1),
            generatedAtEpochMs = 100,
            source = CatalogSource.FOLDER_SCAN,
        )
    }
}
