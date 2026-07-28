package ro.mehene.app.ui.state

import ro.mehene.app.data.EpisodeProgress
import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.model.SeasonItem
import ro.mehene.app.model.SeriesItem

sealed interface SeriesUiState {
    data object Loading : SeriesUiState
    data class Content(
        val series: SeriesItem,
        val seasons: List<SeasonItem>,
        val selectedSeason: SeasonItem,
        val progress: Map<String, EpisodeProgress>,
        val playbackMode: PlaybackMode,
    ) : SeriesUiState
    data object Unavailable : SeriesUiState
}
