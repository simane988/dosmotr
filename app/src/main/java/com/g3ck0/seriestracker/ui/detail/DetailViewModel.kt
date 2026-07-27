package com.g3ck0.seriestracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Season(
    val number: Int,
    val episodes: List<EpisodeEntity>,
) {
    val watchedCount: Int get() = episodes.count { it.watched }
    val allWatched: Boolean get() = episodes.isNotEmpty() && watchedCount == episodes.size
}

data class DetailUiState(
    val loading: Boolean = true,
    val title: TitleWithProgress? = null,
    val seasons: List<Season> = emptyList(),
    val nextEpisode: EpisodeEntity? = null,
    val refreshing: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: TrackerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val titleId: String = checkNotNull(savedStateHandle["titleId"])

    private val refreshing = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<DetailUiState> = combine(
        repository.observeTitle(titleId),
        repository.observeEpisodes(titleId),
        refreshing,
        deleted,
        message,
    ) { title, episodes, isRefreshing, isDeleted, msg ->
        val seasons = episodes
            .groupBy { it.seasonNumber }
            .toSortedMap()
            .map { (number, list) -> Season(number, list.sortedBy { it.episodeNumber }) }
        DetailUiState(
            loading = false,
            title = title,
            seasons = seasons,
            nextEpisode = episodes.firstOrNull { !it.watched },
            refreshing = isRefreshing,
            deleted = isDeleted,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        // Titles added while offline never got their episode list — fill it in on open.
        viewModelScope.launch {
            val entity = repository.observeTitle(titleId).filterNotNull().first().title
            if (!entity.episodesLoaded && entity.tmdbId != null) refresh()
        }
    }

    fun toggleEpisode(episode: EpisodeEntity) = viewModelScope.launch {
        repository.setEpisodeWatched(
            titleId = titleId,
            season = episode.seasonNumber,
            episode = episode.episodeNumber,
            watched = !episode.watched,
        )
    }

    fun toggleSeason(season: Season) = viewModelScope.launch {
        repository.setSeasonWatched(titleId, season.number, !season.allWatched)
    }

    fun watchUpTo(episode: EpisodeEntity) = viewModelScope.launch {
        repository.watchUpTo(titleId, episode.seasonNumber, episode.episodeNumber)
        message.value = "Отмечено всё до S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber)
    }

    fun markNext() = viewModelScope.launch {
        val next = repository.markNextWatched(titleId)
        message.value = next?.let { "Отмечено: S%02dE%02d".format(it.seasonNumber, it.episodeNumber) }
            ?: "Все серии уже отмечены"
    }

    fun setAllWatched(watched: Boolean) = viewModelScope.launch {
        repository.setAllWatched(titleId, watched)
    }

    fun setMovieWatched(watched: Boolean) = viewModelScope.launch {
        repository.setMovieWatched(titleId, watched)
    }

    fun setStatus(status: WatchStatus) = viewModelScope.launch {
        repository.setStatus(titleId, status)
    }

    fun setRating(rating: Int?) = viewModelScope.launch {
        repository.setRating(titleId, rating)
    }

    fun setNotes(notes: String) = viewModelScope.launch {
        repository.setNotes(titleId, notes)
    }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        repository.refreshFromTmdb(titleId)
            .onFailure { message.value = it.message ?: "Не удалось обновить" }
            .onSuccess { message.value = "Обновлено из TMDB" }
        refreshing.value = false
    }

    fun delete() = viewModelScope.launch {
        repository.delete(titleId)
        deleted.value = true
    }

    fun consumeMessage() {
        message.value = null
    }
}
