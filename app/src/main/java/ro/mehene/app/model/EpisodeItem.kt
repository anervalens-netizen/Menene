package ro.mehene.app.model

data class EpisodeItem(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val seasonTitle: String,
    val number: Int,
    val sortOrder: Int,
    val title: String,
    val mediaUri: String,
    val subtitleUri: String?,
    val artworkUri: String?,
    val artworkVersion: Long,
    val durationMs: Long = 0L,
)
