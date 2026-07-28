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
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.media.PlaybackQueuePlanner
import ro.mehene.app.model.LibraryCatalog
import ro.mehene.app.ui.state.LibraryUnavailableReason
import ro.mehene.app.ui.state.MainUiState

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.meheneContainer()
    private val libraryRepository = container.libraryRepository
    private val progressRepository = container.progressRepository
    private val settingsRepository = container.settingsRepository

    private val catalogResult = MutableStateFlow<LibraryResult<LibraryCatalog>?>(null)

    val uiState = combine(
        catalogResult,
        progressRepository.progress,
        settingsRepository.playbackMode,
    ) { result, progress, playbackMode ->
        when (result) {
            null -> MainUiState.Loading
            LibraryResult.NotConfigured -> MainUiState.NotConfigured
            LibraryResult.PermissionLost -> MainUiState.Unavailable(LibraryUnavailableReason.PERMISSION_LOST)
            LibraryResult.StorageUnavailable -> MainUiState.Unavailable(LibraryUnavailableReason.STORAGE_UNAVAILABLE)
            is LibraryResult.Failure -> MainUiState.Unavailable(LibraryUnavailableReason.FAILURE)
            is LibraryResult.Success -> MainUiState.Content(
                catalog = result.value,
                continueEpisode = PlaybackQueuePlanner.continueEpisode(result.value, progress),
                tvEpisode = PlaybackQueuePlanner.tvStartEpisode(result.value, progress),
                playbackMode = playbackMode,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState.Loading,
    )

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (catalogResult.value == null || force) catalogResult.value = null
            val result = libraryRepository.loadCatalog(force)
            catalogResult.value = result
            if (result is LibraryResult.Success) {
                progressRepository.prune(result.value.episodes.map { it.id }.toSet())
            }
        }
    }

    fun setLibrary(uri: Uri) {
        viewModelScope.launch {
            catalogResult.value = null
            catalogResult.value = libraryRepository.persistLibraryUri(uri)
        }
    }
}
