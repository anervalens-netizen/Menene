package ro.menene.app.model

data class SeasonItem(
    val number: Int,
    val title: String,
    val episodes: List<EpisodeItem>,
)
