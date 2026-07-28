package ro.mehene.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.mehene.app.data.LibraryResult
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.ui.state.SeriesUiState

class SeriesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.meheneContainer()
    private val series = MutableStateFlow<SeriesItem?>(null)
    private val unavailable = MutableStateFlow(false)
    private val selectedSeasonNumber = MutableStateFlow<Int?>(null)
    private var loadJob: Job? = null
    private var requestedSeriesId: String? = null

    val uiState = combine(
        series,
        unavailable,
        selectedSeasonNumber,
        container.progressRepository.progress,
        container.settingsRepository.playbackMode,
    ) { seriesItem, isUnavailable, requestedSeason, progress, mode ->
        when {
            isUnavailable -> SeriesUiState.Unavailable
            seriesItem == null -> SeriesUiState.Loading
            else -> {
                val seasons = seriesItem.seasons.sortedBy { it.number }
                val selected = seasons.firstOrNull { it.number == requestedSeason }
                    ?: seasons.first()
                SeriesUiState.Content(
                    series = seriesItem,
                    seasons = seasons,
                    selectedSeason = selected,
                    progress = progress,
                    playbackMode = mode,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SeriesUiState.Loading,
    )

    fun load(seriesId: String) {
        if (series.value?.id == seriesId && !unavailable.value) return
        requestedSeriesId = seriesId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            unavailable.value = false
            when (val result = container.libraryRepository.loadCatalog()) {
                is LibraryResult.Success -> {
                    if (requestedSeriesId != seriesId) return@launch
                    val item = result.value.findSeries(seriesId)
                    if (item == null) unavailable.value = true else series.value = item
                }
                else -> if (requestedSeriesId == seriesId) unavailable.value = true
            }
        }
    }

    fun selectSeason(number: Int) {
        selectedSeasonNumber.value = number
    }
}
