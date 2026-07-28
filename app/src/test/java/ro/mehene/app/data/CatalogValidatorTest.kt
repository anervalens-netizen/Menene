package ro.mehene.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.mehene.app.model.CatalogSource
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog
import ro.mehene.app.model.LibraryDiagnostics
import ro.mehene.app.model.SeasonItem
import ro.mehene.app.model.SeriesItem

class CatalogValidatorTest {
    @Test
    fun acceptsConsistentCatalog() {
        assertTrue(CatalogValidator.validate(catalog()).isValid)
    }

    @Test
    fun rejectsDuplicateEpisodeIds() {
        val source = catalog()
        val episode = source.series.first().episodes.first()
        val duplicated = source.copy(
            series = source.series + source.series.first().copy(
                id = "series-2",
                title = "Alt serial",
                seasons = listOf(SeasonItem(1, "Sezonul 1", listOf(episode.copy(seriesId = "series-2")))),
            ),
        )
        assertFalse(CatalogValidator.validate(duplicated).isValid)
    }

    @Test
    fun rejectsEpisodeWithWrongSeriesReference() {
        val source = catalog()
        val broken = source.copy(
            series = listOf(
                source.series.first().copy(
                    seasons = listOf(
                        SeasonItem(1, "Sezonul 1", listOf(source.series.first().episodes.first().copy(seriesId = "wrong"))),
                    ),
                ),
            ),
        )
        assertFalse(CatalogValidator.validate(broken).isValid)
    }

    @Test
    fun rejectsSeriesWithoutDirectoryUri() {
        val source = catalog()
        val broken = source.copy(series = listOf(source.series.first().copy(directoryUri = "")))
        assertFalse(CatalogValidator.validate(broken).isValid)
    }

    private fun catalog(): LibraryCatalog {
        val episode = EpisodeItem(
            id = "episode-1",
            seriesId = "series-1",
            seasonNumber = 1,
            seasonTitle = "Sezonul 1",
            number = 1,
            sortOrder = 1,
            title = "Pilot",
            mediaUri = "content://episode-1",
            subtitleUri = null,
            artworkUri = null,
            artworkVersion = 0L,
        )
        return LibraryCatalog(
            rootUri = "content://root",
            series = listOf(
                SeriesItem(
                    id = "series-1",
                    title = "Serial",
                    directoryUri = "content://series-1",
                    coverUri = null,
                    coverVersion = 0L,
                    seasons = listOf(SeasonItem(1, "Sezonul 1", listOf(episode))),
                ),
            ),
            diagnostics = LibraryDiagnostics(seriesCount = 1, episodeCount = 1),
            generatedAtEpochMs = 1L,
            source = CatalogSource.FOLDER_SCAN,
        )
    }
}
