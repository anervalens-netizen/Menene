package ro.menene.app.ui.state

import ro.menene.app.data.PlaybackMode
import ro.menene.app.model.EpisodeItem
import ro.menene.app.model.LibraryCatalog

sealed interface MainUiState {
    data object Loading : MainUiState
    data object NotConfigured : MainUiState
    data class Unavailable(val reason: LibraryUnavailableReason) : MainUiState
    data class Content(
        val catalog: LibraryCatalog,
        val continueEpisode: EpisodeItem?,
        val tvEpisode: EpisodeItem?,
        val playbackMode: PlaybackMode,
    ) : MainUiState
}

enum class LibraryUnavailableReason {
    PERMISSION_LOST,
    STORAGE_UNAVAILABLE,
    FAILURE,
}
