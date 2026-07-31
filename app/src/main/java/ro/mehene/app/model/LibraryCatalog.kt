package ro.mehene.app.model

data class LibraryCatalog(
    val rootUri: String,
    val series: List<SeriesItem>,
    val diagnostics: LibraryDiagnostics,
    val generatedAtEpochMs: Long,
    val source: CatalogSource,
) {
    val libraryId: String
        get() = ro.mehene.app.data.LibraryId.fromRootUri(rootUri)

    val episodeCount: Int
        get() = series.sumOf { it.episodeCount }

    val episodes: List<EpisodeItem>
        get() = series.flatMap(SeriesItem::episodes)

    fun findSeries(seriesId: String): SeriesItem? = series.firstOrNull { it.id == seriesId }
    fun findEpisode(episodeId: String): EpisodeItem? = episodes.firstOrNull { it.id == episodeId }
}

enum class CatalogSource {
    GENERATED_CATALOG,
    FOLDER_SCAN,
    INTERNAL_CACHE,
}

data class LibraryDiagnostics(
    val ignoredVideoCount: Int = 0,
    val missingSeriesArtworkCount: Int = 0,
    val missingEpisodeArtworkCount: Int = 0,
    val seriesCount: Int = 0,
    val episodeCount: Int = 0,
    val scanDurationMs: Long = 0L,
    val lastError: String? = null,
)
