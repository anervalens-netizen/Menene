package ro.menene.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.menene.app.data.LibraryResult
import ro.menene.app.media.PlaybackQueuePlanner
import ro.menene.app.model.LibraryCatalog
import ro.menene.app.ui.state.LibraryUnavailableReason
import ro.menene.app.ui.state.MainUiState

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.meneneContainer()
    private val libraryRepository = container.libraryRepository
    private val progressRepository = container.progressRepository
    private val settingsRepository = container.settingsRepository

    private val catalogResult = MutableStateFlow<LibraryResult<LibraryCatalog>?>(null)
    private var refreshJob: Job? = null
    private var requestGeneration = 0L

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
        val generation = ++requestGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val result = libraryRepository.loadCatalog(force)
            if (generation != requestGeneration) return@launch
            if (result is LibraryResult.Success) {
                progressRepository.activateLibrary(result.value.libraryId)
            }
            catalogResult.value = result
        }
    }

    fun setLibrary(uri: Uri) {
        val generation = ++requestGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val result = libraryRepository.persistLibraryUri(uri)
            if (generation != requestGeneration) return@launch
            if (result is LibraryResult.Success) {
                progressRepository.activateLibrary(result.value.libraryId)
            }
            catalogResult.value = result
        }
    }
}
