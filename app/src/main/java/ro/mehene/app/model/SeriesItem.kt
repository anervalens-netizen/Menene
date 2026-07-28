package ro.mehene.app.model

data class SeriesItem(
    val title: String,
    val directoryUri: String,
    val coverUri: String?,
    val coverVersion: Long,
    val episodeCount: Int,
)
