package ro.menene.app.ui.state

import ro.menene.app.data.EpisodeProgress
import ro.menene.app.data.PlaybackMode
import ro.menene.app.model.SeasonItem
import ro.menene.app.model.SeriesItem

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
