package ro.mehene.app.ui.state

import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog

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
