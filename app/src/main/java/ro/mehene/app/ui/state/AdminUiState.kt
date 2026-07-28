package ro.mehene.app.ui.state

import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.kiosk.KioskState
import ro.mehene.app.model.LibraryCatalog

sealed interface AdminUiState {
    data object Loading : AdminUiState
    data class Ready(
        val catalog: LibraryCatalog?,
        val playbackMode: PlaybackMode,
        val preferredAudioLanguage: String,
        val kioskState: KioskState,
        val libraryUri: String?,
    ) : AdminUiState
    data class Error(val message: String) : AdminUiState
}
