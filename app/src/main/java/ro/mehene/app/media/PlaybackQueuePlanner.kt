package ro.mehene.app.media

import ro.mehene.app.data.EpisodePlaybackState
import ro.mehene.app.data.EpisodeProgress
import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog

object PlaybackQueuePlanner {
    fun continueEpisode(
        catalog: LibraryCatalog,
        progress: Map<String, EpisodeProgress>,
    ): EpisodeItem? = progress.values
        .asSequence()
        .filter { it.state == EpisodePlaybackState.IN_PROGRESS }
        .sortedByDescending { it.lastPlayedAtEpochMs }
        .mapNotNull { catalog.findEpisode(it.episodeId) }
        .firstOrNull()

    fun tvStartEpisode(
        catalog: LibraryCatalog,
        progress: Map<String, EpisodeProgress>,
    ): EpisodeItem? = continueEpisode(catalog, progress)
        ?: tvSequence(catalog).firstOrNull { episode ->
            progress[episode.id]?.state != EpisodePlaybackState.COMPLETED
        }
        ?: tvSequence(catalog).firstOrNull()

    fun nextEpisode(
        catalog: LibraryCatalog,
        currentEpisodeId: String,
        mode: PlaybackMode,
        progress: Map<String, EpisodeProgress>,
    ): EpisodeItem? = when (mode) {
        PlaybackMode.SINGLE -> null
        PlaybackMode.CONTINUE_SERIES -> nextInSeries(catalog, currentEpisodeId, progress)
        PlaybackMode.MEHENE_TV -> nextInTv(catalog, currentEpisodeId, progress)
    }

    private fun nextInSeries(
        catalog: LibraryCatalog,
        currentEpisodeId: String,
        progress: Map<String, EpisodeProgress>,
    ): EpisodeItem? {
        val current = catalog.findEpisode(currentEpisodeId) ?: return null
        val episodes = catalog.findSeries(current.seriesId)?.episodes.orEmpty()
        val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex < 0) return null
        return episodes.drop(currentIndex + 1).firstOrNull { episode ->
            progress[episode.id]?.state != EpisodePlaybackState.COMPLETED
        }
    }

    private fun nextInTv(
        catalog: LibraryCatalog,
        currentEpisodeId: String,
        progress: Map<String, EpisodeProgress>,
    ): EpisodeItem? {
        val sequence = tvSequence(catalog)
        if (sequence.isEmpty()) return null
        val currentIndex = sequence.indexOfFirst { it.id == currentEpisodeId }
        val rotated = if (currentIndex >= 0) {
            sequence.drop(currentIndex + 1) + sequence.take(currentIndex + 1)
        } else {
            sequence
        }
        return rotated.firstOrNull { episode ->
            episode.id != currentEpisodeId && progress[episode.id]?.state != EpisodePlaybackState.COMPLETED
        }
    }

    internal fun tvSequence(catalog: LibraryCatalog): List<EpisodeItem> {
        val episodesBySeries = catalog.series.map { it.episodes }
        val maximumSize = episodesBySeries.maxOfOrNull { it.size } ?: return emptyList()
        return buildList {
            for (episodeIndex in 0 until maximumSize) {
                episodesBySeries.forEach { episodes ->
                    episodes.getOrNull(episodeIndex)?.let(::add)
                }
            }
        }
    }
}
