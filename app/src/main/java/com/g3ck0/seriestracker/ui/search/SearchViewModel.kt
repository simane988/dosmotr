package com.g3ck0.seriestracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.data.telemetry.Telemetry
import com.g3ck0.seriestracker.data.telemetry.TelemetryEvent
import com.g3ck0.seriestracker.data.telemetry.telemetryParam
import com.g3ck0.seriestracker.ui.common.UserError
import com.g3ck0.seriestracker.ui.common.toUserError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchItem> = emptyList(),
    val loading: Boolean = false,
    val error: UserError? = null,
    val message: String? = null,
    val showingTrending: Boolean = true,
    val hasBackend: Boolean = true,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TrackerRepository,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(hasBackend = repository.hasBackend))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Ids already in the library, so the list can show "Добавлено" instead of a button. */
    val trackedIds: StateFlow<Set<String>> = repository.observeTrackedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var searchJob: Job? = null

    init {
        if (repository.hasBackend) loadTrending()
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            loadTrending()
            return
        }
        // Debounce so typing does not fire a request per keystroke.
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(query)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        val query = _state.value.query
        if (query.isBlank()) loadTrending() else viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        if (!repository.hasBackend) return
        _state.value = _state.value.copy(loading = true, error = null)
        repository.search(query)
            .onSuccess {
                // The event says a search ran, and that is all it says: [query] is the
                // most personal string in the app and never leaves the device. Reported
                // after the request rather than per keystroke — the debounce above is what
                // decides that a search happened.
                telemetry.event(TelemetryEvent.SEARCH_PERFORMED)
                _state.value = _state.value.copy(
                    loading = false,
                    results = it,
                    showingTrending = false,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.toUserError(),
                )
            }
    }

    private fun loadTrending() = viewModelScope.launch {
        if (!repository.hasBackend) return@launch
        _state.value = _state.value.copy(loading = true, error = null)
        repository.trending()
            .onSuccess {
                _state.value = _state.value.copy(
                    loading = false,
                    results = it,
                    showingTrending = true,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.toUserError(),
                )
            }
    }

    fun add(item: SearchItem, status: WatchStatus = WatchStatus.PLANNED) = viewModelScope.launch {
        repository.add(item, status)
            .onSuccess {
                telemetry.event(TelemetryEvent.TITLE_ADDED, item.mediaType.telemetryParam)
                _state.value = _state.value.copy(message = "«${item.name}» добавлен")
            }
            .onFailure {
                val error = it.toUserError("Не удалось добавить")
                _state.value = _state.value.copy(message = error.combined)
            }
    }

    fun addManual(
        name: String,
        mediaType: MediaType,
        episodesPerSeason: List<Int>,
        runtimeMinutes: Int,
        year: String?,
    ) = viewModelScope.launch {
        repository.addManual(name, mediaType, episodesPerSeason, runtimeMinutes, year)
        telemetry.event(TelemetryEvent.MANUAL_ADD)
        _state.value = _state.value.copy(message = "«$name» добавлен вручную")
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
