package ro.mehene.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.mehene.app.data.LibraryPreferences
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.data.PlaybackMode
import ro.mehene.app.kiosk.KioskController
import ro.mehene.app.model.LibraryCatalog
import ro.mehene.app.ui.state.AdminUiState

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.meheneContainer()
    private val preferences = LibraryPreferences(application)
    private val catalogResult = MutableStateFlow<LibraryResult<LibraryCatalog>?>(null)

    val uiState = combine(
        catalogResult,
        container.settingsRepository.playbackMode,
        container.settingsRepository.preferredAudioLanguage,
    ) { result, mode, audioLanguage ->
        when (result) {
            null -> AdminUiState.Loading
            is LibraryResult.Success -> AdminUiState.Ready(
                catalog = result.value,
                playbackMode = mode,
                preferredAudioLanguage = audioLanguage,
                kioskState = KioskController.state(application),
                libraryUri = preferences.libraryUri,
            )
            LibraryResult.NotConfigured -> AdminUiState.Ready(
                catalog = null,
                playbackMode = mode,
                preferredAudioLanguage = audioLanguage,
                kioskState = KioskController.state(application),
                libraryUri = null,
            )
            LibraryResult.PermissionLost -> AdminUiState.Error("Accesul la bibliotecă s-a pierdut")
            LibraryResult.StorageUnavailable -> AdminUiState.Error("Cardul sau memoria nu este disponibilă")
            is LibraryResult.Failure -> AdminUiState.Error(result.error.message ?: "Eroare necunoscută")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdminUiState.Loading,
    )

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (catalogResult.value == null || force) catalogResult.value = null
            catalogResult.value = container.libraryRepository.loadCatalog(force)
        }
    }

    fun setLibrary(uri: Uri) {
        viewModelScope.launch {
            catalogResult.value = null
            catalogResult.value = container.libraryRepository.persistLibraryUri(uri)
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        viewModelScope.launch {
            container.settingsRepository.setPlaybackMode(mode)
        }
    }

    fun setPreferredAudioLanguage(language: String) {
        viewModelScope.launch {
            container.settingsRepository.setPreferredAudioLanguage(language)
        }
    }

    fun clearProgress() {
        viewModelScope.launch {
            container.progressRepository.clear()
        }
    }
}
