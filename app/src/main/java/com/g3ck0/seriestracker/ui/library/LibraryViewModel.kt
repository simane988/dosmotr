package com.g3ck0.seriestracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.DeletedTitle
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.data.settings.SettingsStore
import com.g3ck0.seriestracker.data.telemetry.Telemetry
import com.g3ck0.seriestracker.data.telemetry.TelemetryEvent
import com.g3ck0.seriestracker.data.telemetry.telemetryParam
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
    /**
     * Whether to offer turning episode notifications on. True once there is something to
     * notify about and the permission has never been asked for; the screen narrows it
     * further by what the platform can actually ask (see `LibraryScreen`), so the
     * stateless half only has to render the flag.
     */
    val askNotifications: Boolean = false,
    /**
     * Titles to offer on an empty library — trending, so the first screen shows something
     * instead of an empty room. Empty whenever there is no backend or the request failed,
     * and the row is then simply not drawn.
     */
    val suggestions: List<SearchItem> = emptyList(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: TrackerRepository,
    private val settings: SettingsStore,
    private val telemetry: Telemetry,
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

    private val suggestions = MutableStateFlow<List<SearchItem>>(emptyList())

    /** Guards [loadSuggestions] against asking the backend twice for the same screen. */
    private var suggestionsRequested = false

    private val openTitleRequests = Channel<String>(Channel.BUFFERED)

    /**
     * Titles to navigate to, one per added suggestion.
     *
     * A channel rather than a field of the state: opening a title is an event, and a state
     * field would replay it on the next recomposition — the screen would bounce back into
     * the title the user has just come out of.
     */
    val openTitle: Flow<String> = openTitleRequests.receiveAsFlow()

    val state: StateFlow<LibraryUiState> =
        combine(
            repository.observeLibrary(),
            filters,
            message,
            orderEpoch,
            settings.notificationsAsked,
        ) { library, f, msg, _, asked ->
            val items = pin(library.filter { it.matches(f) })
            LibraryUiState(
                loading = false,
                items = items,
                filters = f,
                totalCount = library.size,
                message = msg,
                askNotifications = items.isNotEmpty() && !asked,
            )
            // combine() takes five flows at most, so the suggestions are folded in
            // afterwards rather than squeezed into the tuple above.
        }.combine(suggestions) { base, offered -> base.copy(suggestions = offered) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LibraryUiState(),
            )

    init {
        reportFirstLaunch()
    }

    /**
     * Sends `first_launch` once per install and remembers that it went out.
     *
     * Here rather than in the Application because the library is the first screen and this
     * is the first ViewModel built, and because a ViewModel can be driven by a JVM test
     * while an Application cannot. The flag is persisted, so reinstalling counts as a new
     * install and restarting does not — which is what makes the funnel in
     * product/07-metrics.md count people rather than launches.
     */
    private fun reportFirstLaunch() = viewModelScope.launch {
        if (settings.firstLaunchReported.first()) return@launch
        settings.setFirstLaunchReported(true)
        telemetry.event(TelemetryEvent.FIRST_LAUNCH)
    }

    /**
     * Fills [LibraryUiState.suggestions] with what is trending, for the empty state to show.
     *
     * Called by the screen when the empty library is on screen, and requested at most once
     * per emptiness: a cold start with a library in it must not touch the network at all,
     * which is why the library is checked here too rather than trusted to the caller.
     * A failure is silent — the row is an offer, and an error message on first launch is
     * not what the screen is for — but it is not remembered either, so entering the empty
     * library again tries once more.
     */
    fun loadSuggestions() {
        if (suggestionsRequested || !repository.hasBackend) return
        viewModelScope.launch {
            if (repository.observeLibrary().first().isNotEmpty()) return@launch
            suggestionsRequested = true
            repository.trending()
                .onSuccess { suggestions.value = it.take(SUGGESTION_COUNT) }
                .onFailure { suggestionsRequested = false }
        }
    }

    /**
     * Adds a suggested title, then asks the screen to open it. Adding first is what makes
     * the title real: the detail screen reads the database, so it has nothing to show for
     * a title that is only in the trending row.
     */
    fun addSuggestion(item: SearchItem) = viewModelScope.launch {
        repository.add(item)
            .onSuccess {
                telemetry.event(TelemetryEvent.TITLE_ADDED, item.mediaType.telemetryParam)
                openTitleRequests.send(it)
            }
            .onFailure { message.value = LibraryMessage("Не удалось добавить «${item.name}»") }
    }

    /**
     * Manual entry from the empty library. The dialog is the search screen's, but reaching
     * it used to mean going through a search that needs a backend nobody has offline.
     */
    fun addManual(
        name: String,
        mediaType: MediaType,
        episodesPerSeason: List<Int>,
        runtimeMinutes: Int,
        year: String?,
    ) = viewModelScope.launch {
        repository.addManual(name, mediaType, episodesPerSeason, runtimeMinutes, year)
        telemetry.event(TelemetryEvent.MANUAL_ADD)
        message.value = LibraryMessage("«$name» добавлен вручную")
    }

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
        if (episode != null) telemetry.event(TelemetryEvent.EPISODE_WATCHED)
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
     * Records that the notification permission has been asked for. Called on every outcome
     * of the system dialog, refusal included — the prompt is an offer, not a demand, and
     * one that keeps coming back is the reason people turn notifications off for good.
     *
     * [granted] is only reported, never stored: how many people say yes is what decides
     * whether the prompt is worth showing at all, and the system is the authority on the
     * answer anyway.
     */
    fun markNotificationsAsked(granted: Boolean) = viewModelScope.launch {
        settings.setNotificationsAsked(true)
        if (granted) telemetry.event(TelemetryEvent.NOTIFICATIONS_ALLOWED)
    }

    /** The overflow menu's «О приложении». No parameter — it is a screen, not a choice. */
    fun aboutOpened() {
        telemetry.event(TelemetryEvent.ABOUT_OPENED)
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

/** Enough posters for the row to scroll, few enough to stay one screenful of work. */
private const val SUGGESTION_COUNT = 12

private fun TitleWithProgress.matches(f: LibraryFilters): Boolean {
    if (f.status != null && title.status != f.status) return false
    if (f.mediaType != null && title.mediaType != f.mediaType) return false
    if (f.query.isNotBlank() && !title.name.contains(f.query.trim(), ignoreCase = true)) return false
    return true
}
