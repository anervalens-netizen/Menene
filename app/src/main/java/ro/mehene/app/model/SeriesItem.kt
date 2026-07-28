package ro.mehene.app.model

data class SeriesItem(
    val id: String,
    val title: String,
    val directoryUri: String,
    val coverUri: String?,
    val coverVersion: Long,
    val seasons: List<SeasonItem>,
) {
    val episodeCount: Int
        get() = seasons.sumOf { it.episodes.size }

    val episodes: List<EpisodeItem>
        get() = seasons.sortedBy { it.number }.flatMap { season ->
            season.episodes.sortedWith(compareBy(EpisodeItem::sortOrder, EpisodeItem::number, EpisodeItem::title))
        }
}
