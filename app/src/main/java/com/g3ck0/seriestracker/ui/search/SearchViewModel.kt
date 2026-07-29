package com.g3ck0.seriestracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.data.repository.TrackerRepository
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
    val error: String? = null,
    val message: String? = null,
    val showingTrending: Boolean = true,
    val hasBackend: Boolean = true,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TrackerRepository,
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
                _state.value = _state.value.copy(
                    loading = false,
                    results = it,
                    showingTrending = false,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Ошибка сети",
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
                    error = it.message ?: "Ошибка сети",
                )
            }
    }

    fun add(item: SearchItem, status: WatchStatus = WatchStatus.WATCHING) = viewModelScope.launch {
        repository.add(item, status)
            .onSuccess { _state.value = _state.value.copy(message = "«${item.name}» добавлен") }
            .onFailure { _state.value = _state.value.copy(message = it.message ?: "Не удалось добавить") }
    }

    fun addManual(
        name: String,
        mediaType: MediaType,
        episodesPerSeason: List<Int>,
        runtimeMinutes: Int,
        year: String?,
    ) = viewModelScope.launch {
        repository.addManual(name, mediaType, episodesPerSeason, runtimeMinutes, year)
        _state.value = _state.value.copy(message = "«$name» добавлен вручную")
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
