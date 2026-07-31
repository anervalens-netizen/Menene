package ro.menene.app.ui.state

import ro.menene.app.data.PlaybackMode
import ro.menene.app.kiosk.KioskState
import ro.menene.app.model.LibraryCatalog

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
