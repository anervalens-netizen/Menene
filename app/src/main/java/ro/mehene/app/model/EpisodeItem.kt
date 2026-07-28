package ro.mehene.app.model

data class EpisodeItem(
    val title: String,
    val mediaUri: String,
    val playbackKey: String,
    val artworkUri: String?,
    val artworkVersion: Long,
    val number: Int,
)
