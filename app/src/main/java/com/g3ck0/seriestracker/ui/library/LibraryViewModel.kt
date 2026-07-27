package com.g3ck0.seriestracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryFilters(
    val status: WatchStatus? = null,
    val mediaType: MediaType? = null,
    val query: String = "",
)

data class LibraryUiState(
    val loading: Boolean = true,
    val items: List<TitleWithProgress> = emptyList(),
    val filters: LibraryFilters = LibraryFilters(),
    val totalCount: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: TrackerRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryFilters())
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<LibraryUiState> =
        combine(repository.observeLibrary(), filters, message) { library, f, msg ->
            LibraryUiState(
                loading = false,
                items = library.filter { it.matches(f) },
                filters = f,
                totalCount = library.size,
                message = msg,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun setStatusFilter(status: WatchStatus?) {
        filters.value = filters.value.copy(status = status)
    }

    fun setMediaFilter(type: MediaType?) {
        filters.value = filters.value.copy(mediaType = type)
    }

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun markNextWatched(titleId: String) = viewModelScope.launch {
        val episode = repository.markNextWatched(titleId)
        message.value = if (episode == null) {
            "Все серии уже отмечены"
        } else {
            "Отмечено: S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber)
        }
    }

    fun toggleMovieWatched(titleId: String, watched: Boolean) = viewModelScope.launch {
        repository.setMovieWatched(titleId, watched)
    }

    fun setStatus(titleId: String, status: WatchStatus) = viewModelScope.launch {
        repository.setStatus(titleId, status)
    }

    fun delete(titleId: String) = viewModelScope.launch {
        repository.delete(titleId)
        message.value = "Удалено"
    }

    fun consumeMessage() {
        message.value = null
    }
}

private fun TitleWithProgress.matches(f: LibraryFilters): Boolean {
    if (f.status != null && title.status != f.status) return false
    if (f.mediaType != null && title.mediaType != f.mediaType) return false
    if (f.query.isNotBlank() && !title.name.contains(f.query.trim(), ignoreCase = true)) return false
    return true
}
