package com.g3ck0.seriestracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.DeletedTitle
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

/**
 * A snackbar line. [action] is the label of its button — set only when there is
 * something to undo, since the button is what makes the deletion recoverable.
 */
data class LibraryMessage(val text: String, val action: String? = null)

data class LibraryUiState(
    val loading: Boolean = true,
    val items: List<TitleWithProgress> = emptyList(),
    val filters: LibraryFilters = LibraryFilters(),
    val totalCount: Int = items.size,
    val message: LibraryMessage? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: TrackerRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryFilters())
    private val message = MutableStateFlow<LibraryMessage?>(null)

    /**
     * The last deleted title, kept until the snackbar offering the undo is gone.
     *
     * Deleting from a list is one tap with no dialog in front of it, and the row it hits
     * moves while the list re-sorts — so the recovery has to live somewhere. The database
     * cannot provide it: the foreign key cascade takes every watched episode with the
     * title, and nothing but a JSON backup remembers them afterwards.
     */
    private var deleted: DeletedTitle? = null

    /**
     * The row order the screen is currently showing, by title id.
     *
     * The DAO sorts by `lastWatchedAt DESC`, so marking an episode moves the card to the
     * top of its status group — out from under the finger that is tapping "+1 серия",
     * with the next tap landing on whatever slid into its place. The order is therefore
     * frozen while the screen is open: it is taken from the DAO once, and later emissions
     * are re-ordered onto it. [refreshOrder] and a filter change are what unfreeze it.
     */
    private var pinnedOrder: List<String> = emptyList()

    private val orderEpoch = MutableStateFlow(0)

    val state: StateFlow<LibraryUiState> =
        combine(repository.observeLibrary(), filters, message, orderEpoch) { library, f, msg, _ ->
            LibraryUiState(
                loading = false,
                items = pin(library.filter { it.matches(f) }),
                filters = f,
                totalCount = library.size,
                message = msg,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    /**
     * Drops the frozen order so the next emission is sorted by the DAO again. Called when
     * the screen is entered — a fresh look at the library is expected to be sorted, a card
     * moving while it is being tapped is not.
     */
    fun refreshOrder() {
        pinnedOrder = emptyList()
        orderEpoch.value++
    }

    fun setStatusFilter(status: WatchStatus?) {
        pinnedOrder = emptyList()
        filters.value = filters.value.copy(status = status)
    }

    fun setMediaFilter(type: MediaType?) {
        pinnedOrder = emptyList()
        filters.value = filters.value.copy(mediaType = type)
    }

    fun setQuery(query: String) {
        pinnedOrder = emptyList()
        filters.value = filters.value.copy(query = query)
    }

    fun markNextWatched(titleId: String) = viewModelScope.launch {
        val episode = repository.markNextWatched(titleId)
        message.value = LibraryMessage(
            if (episode == null) {
                "Все серии уже отмечены"
            } else {
                "Отмечено: S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber)
            }
        )
    }

    fun toggleMovieWatched(titleId: String, watched: Boolean) = viewModelScope.launch {
        repository.setMovieWatched(titleId, watched)
    }

    fun setStatus(titleId: String, status: WatchStatus) = viewModelScope.launch {
        repository.setStatus(titleId, status)
    }

    fun delete(titleId: String) = viewModelScope.launch {
        val removed = repository.delete(titleId) ?: return@launch
        deleted = removed
        // "Тайтл «…»", not just the name: the name's grammatical gender is unknown, and
        // it is often English, so anything agreeing with it directly would read wrong.
        message.value = LibraryMessage("Тайтл «${removed.title.name}» удалён", action = "Отменить")
    }

    /**
     * Puts the last deleted title back. The pending snapshot is taken before the restore
     * is launched, so a [consumeMessage] arriving in between cannot drop it mid-flight.
     */
    fun undoDelete() {
        val removed = deleted ?: return
        deleted = null
        viewModelScope.launch {
            repository.restore(removed)
            message.value = LibraryMessage("Тайтл «${removed.title.name}» восстановлен")
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    /**
     * Re-orders [items] onto [pinnedOrder] and remembers the result as the new order.
     *
     * Titles the frozen order does not know about — added, imported, or brought into the
     * filter since it was taken — keep the position the DAO gave them relative to the
     * titles around them, so a new row still appears where it belongs instead of at the
     * end. Deleted ones simply drop out.
     */
    private fun pin(items: List<TitleWithProgress>): List<TitleWithProgress> {
        val rank = pinnedOrder.withIndex().associate { (index, id) -> id to index.toDouble() }
        var anchor = -1.0
        var unranked = 0
        val ordered = items
            .map { item ->
                val pinned = rank[item.title.id]
                if (pinned != null) {
                    anchor = pinned
                    unranked = 0
                } else {
                    unranked++
                }
                item to (pinned ?: anchor + unranked / (items.size + 1.0))
            }
            .sortedBy { (_, position) -> position }
            .map { (item, _) -> item }
        pinnedOrder = ordered.map { it.title.id }
        return ordered
    }
}

private fun TitleWithProgress.matches(f: LibraryFilters): Boolean {
    if (f.status != null && title.status != f.status) return false
    if (f.mediaType != null && title.mediaType != f.mediaType) return false
    if (f.query.isNotBlank() && !title.name.contains(f.query.trim(), ignoreCase = true)) return false
    return true
}
