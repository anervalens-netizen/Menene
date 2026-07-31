package ro.menene.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ro.menene.app.data.EpisodePlaybackState
import ro.menene.app.data.EpisodeProgress
import ro.menene.app.data.PlaybackMode
import ro.menene.app.model.CatalogSource
import ro.menene.app.model.EpisodeItem
import ro.menene.app.model.LibraryCatalog
import ro.menene.app.model.LibraryDiagnostics
import ro.menene.app.model.SeasonItem
import ro.menene.app.model.SeriesItem

class PlaybackQueuePlannerTest {
    @Test
    fun interleavesSeriesForMeneneTv() {
        val catalog = catalog()
        assertEquals(listOf("a1", "b1", "a2", "b2"), PlaybackQueuePlanner.tvSequence(catalog).map { it.id })
    }

    @Test
    fun continuesSeriesButStopsInSingleMode() {
        val catalog = catalog()
        val progress = mapOf(
            "a1" to EpisodeProgress("a1", EpisodePlaybackState.COMPLETED, 0, 100, 1),
        )
        assertEquals("a2", PlaybackQueuePlanner.nextEpisode(catalog, "a1", PlaybackMode.CONTINUE_SERIES, progress)?.id)
        assertNull(PlaybackQueuePlanner.nextEpisode(catalog, "a1", PlaybackMode.SINGLE, progress))
    }

    private fun catalog(): LibraryCatalog {
        fun episode(id: String, seriesId: String, number: Int) = EpisodeItem(
            id = id,
            seriesId = seriesId,
            seasonNumber = 1,
            seasonTitle = "Sezonul 1",
            number = number,
            sortOrder = number,
            title = id,
            mediaUri = "content://$id",
            subtitleUri = null,
            artworkUri = null,
            artworkVersion = 0,
        )
        fun series(id: String) = SeriesItem(
            id = id,
            title = id,
            directoryUri = "content://$id",
            coverUri = null,
            coverVersion = 0,
            seasons = listOf(SeasonItem(1, "Sezonul 1", listOf(episode("${id}1", id, 1), episode("${id}2", id, 2)))),
        )
        return LibraryCatalog(
            rootUri = "content://root",
            series = listOf(series("a"), series("b")),
            diagnostics = LibraryDiagnostics(),
            generatedAtEpochMs = 0,
            source = CatalogSource.FOLDER_SCAN,
        )
    }
}
