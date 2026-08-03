package com.g3ck0.seriestracker.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.common.ClearFocusWhenDialogCloses
import com.g3ck0.seriestracker.ui.common.DesignChip
import com.g3ck0.seriestracker.ui.common.DesignDialog
import com.g3ck0.seriestracker.ui.common.DialogTextButton
import com.g3ck0.seriestracker.ui.common.IndeterminateProgressBar
import com.g3ck0.seriestracker.ui.common.Poster
import com.g3ck0.seriestracker.ui.common.ProgressBar
import com.g3ck0.seriestracker.ui.common.SnackbarOverlay
import com.g3ck0.seriestracker.ui.common.episodeCode
import com.g3ck0.seriestracker.ui.common.formatAirDate
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.label
import kotlinx.coroutines.launch

object DetailTags {
    const val LIST = "detail:list"
    const val BACK = "detail:back"
    const val REFRESH = "detail:refresh"
    const val DELETE = "detail:delete"
    const val CONFIRM_DELETE = "detail:confirmDelete"
    const val CANCEL_DELETE = "detail:cancelDelete"
    const val MARK_NEXT = "detail:markNext"
    const val TOGGLE_ALL = "detail:toggleAll"
    const val MOVIE_TOGGLE = "detail:movieToggle"
    const val NOTES = "detail:notes"
    const val NOTES_SAVE = "detail:notesSave"
    const val NOTES_CANCEL = "detail:notesCancel"
    const val NOTES_OPEN = "detail:notesOpen"
    const val REFRESHING = "detail:refreshing"
    const val NOT_FOUND = "detail:notFound"
    const val TOP_TITLE = "detail:topTitle"
    const val TAB_OVERVIEW = "detail:tab:overview"
    const val TAB_EPISODES = "detail:tab:episodes"
    const val OVERVIEW = "detail:overview"
    const val OVERVIEW_EXPAND = "detail:overviewExpand"
    fun statusChip(status: WatchStatus) = "detail:status:${status.name}"
    fun rating(value: Int) = "detail:rating:$value"
    fun seasonHeader(season: Int) = "detail:season:$season"
    fun seasonToggle(season: Int) = "detail:seasonToggle:$season"

    /** Tick on a fully watched season; the bar under a started one. Neither exists otherwise. */
    fun seasonDone(season: Int) = "detail:seasonDone:$season"
    fun seasonProgress(season: Int) = "detail:seasonProgress:$season"
    fun episode(season: Int, episode: Int) = "detail:episode:$season:$episode"

    /** The row is clickable, the checkbox is toggleable — assertions need the latter. */
    fun episodeCheckbox(season: Int, episode: Int) = "detail:episodeCheck:$season:$episode"
    fun watchUpTo(season: Int, episode: Int) = "detail:upTo:$season:$episode"

    /** Chevron that reveals the episode synopsis; the row itself stays a watched toggle. */
    fun episodeExpand(season: Int, episode: Int) = "detail:episodeExpand:$season:$episode"
    fun episodeOverview(season: Int, episode: Int) = "detail:episodeOverview:$season:$episode"
}

/** Which half of the detail screen is on show. */
enum class DetailTab { OVERVIEW, EPISODES }

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    DetailContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onDelete = viewModel::delete,
        onStatus = viewModel::setStatus,
        onRating = viewModel::setRating,
        onNotes = viewModel::setNotes,
        onMarkNext = viewModel::markNext,
        onSetAllWatched = viewModel::setAllWatched,
        onMovieWatched = viewModel::setMovieWatched,
        onToggleEpisode = viewModel::toggleEpisode,
        onToggleSeason = viewModel::toggleSeason,
        onWatchUpTo = viewModel::watchUpTo,
        onMessageShown = viewModel::consumeMessage,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailContent(
    state: DetailUiState,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDelete: () -> Unit = {},
    onStatus: (WatchStatus) -> Unit = {},
    onRating: (Int?) -> Unit = {},
    onNotes: (String) -> Unit = {},
    onMarkNext: () -> Unit = {},
    onSetAllWatched: (Boolean) -> Unit = {},
    onMovieWatched: (Boolean) -> Unit = {},
    onToggleEpisode: (EpisodeEntity) -> Unit = {},
    onToggleSeason: (Season) -> Unit = {},
    onWatchUpTo: (EpisodeEntity) -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(DetailTab.OVERVIEW) }
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    // Both expansions are hoisted out of the list items: remember() inside a LazyColumn item
    // is dropped as soon as the row scrolls out of composition, so the block would collapse
    // itself behind the user's back.
    var overviewExpanded by remember { mutableStateOf(false) }
    val expandedEpisodes = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    // Open the season that holds the next unwatched episode; the rest stay collapsed.
    LaunchedEffect(state.nextEpisode?.seasonNumber) {
        state.nextEpisode?.let { next ->
            if (expanded[next.seasonNumber] == null) expanded[next.seasonNumber] = true
        }
    }

    // Same trap as on the search screen: the notes field would grab focus on close.
    ClearFocusWhenDialogCloses(confirmDelete)

    if (confirmDelete) {
        DesignDialog(
            title = "Удалить из библиотеки?",
            onDismiss = { confirmDelete = false },
            content = { Text("Прогресс просмотра будет потерян.") },
            confirmButton = {
                DialogTextButton(
                    label = "Удалить",
                    destructive = true,
                    onClick = { confirmDelete = false; onDelete() },
                    modifier = Modifier.testTag(DetailTags.CONFIRM_DELETE),
                )
            },
            dismissButton = {
                DialogTextButton(
                    label = "Отмена",
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag(DetailTags.CANCEL_DELETE),
                )
            },
        )
    }

    val item = state.title
    val listState = rememberLazyListState()
    // The name is already in the header right below the bar, so the bar only takes it over
    // once that header has scrolled away.
    val titleInBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                DetailTopBar(
                    name = if (titleInBar) item?.title?.name.orEmpty() else "",
                    canRefresh = item?.title?.catalogId != null,
                    onBack = onBack,
                    onRefresh = onRefresh,
                    onDelete = { confirmDelete = true },
                )
                if (state.refreshing) {
                    IndeterminateProgressBar(
                        Modifier.fillMaxWidth().testTag(DetailTags.REFRESHING)
                    )
                }

                if (item == null) {
                    Box(
                        Modifier.fillMaxSize().testTag(DetailTags.NOT_FOUND),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!state.loading) Text("Тайтл не найден")
                    }
                    return@Column
                }

                val hasEpisodes = state.seasons.isNotEmpty()
                val showOverview = !hasEpisodes || tab == DetailTab.OVERVIEW

                // Hoisted above the LazyColumn on purpose: "notes" is an `item`, so it is
                // disposed by an ordinary scroll or an Обзор/Серии tab switch, not just by
                // leaving the screen. State living inside the item was reinitialised (and
                // its flush-on-dispose fired) on every such scroll.
                val notes = rememberNotesState(initial = item.title.notes, onNotes = onNotes)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag(DetailTags.LIST),
                    // Without the gesture-bar inset the last episode all but touches the pill.
                    contentPadding = PaddingValues(
                        bottom = 48.dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "header", contentType = "header") { Header(item) }

                    if (hasEpisodes) {
                        stickyHeader(key = "tabs") {
                            TabSwitcher(
                                selected = tab,
                                onSelect = { tab = it },
                            )
                        }
                    }

                    if (showOverview) {
                        item(key = "status", contentType = "status") {
                            StatusPicker(current = item.title.status, onSelect = onStatus)
                        }
                        // Manually added titles have no synopsis at all — no empty block for them.
                        if (item.title.overview.isNotBlank()) {
                            item(key = "overview", contentType = "overview") {
                                OverviewBlock(
                                    text = item.title.overview,
                                    expanded = overviewExpanded,
                                    onToggle = { overviewExpanded = !overviewExpanded },
                                )
                            }
                        }
                        item(key = "progress", contentType = "progress") {
                            ProgressBlock(
                                item = item,
                                next = state.nextEpisode,
                                onMarkNext = onMarkNext,
                                onAll = { onSetAllWatched(true) },
                                onNone = { onSetAllWatched(false) },
                                onMovieWatched = onMovieWatched,
                            )
                        }
                        item(key = "rating", contentType = "rating") {
                            RatingPicker(rating = item.title.userRating, onSelect = onRating)
                        }
                        item(key = "notes", contentType = "notes") {
                            NotesBlock(
                                initial = notes.committed,
                                open = notes.open,
                                text = notes.text,
                                committed = notes.committed,
                                focusRequester = notes.focusRequester,
                                onOpen = notes.onOpen,
                                onTextChange = notes.onTextChange,
                                onSave = notes.onSave,
                                onCancel = notes.onCancel,
                            )
                        }
                    } else {
                        seasonsSection(
                            seasons = state.seasons,
                            expandedSeasons = expanded,
                            expandedEpisodes = expandedEpisodes,
                            onToggleEpisode = onToggleEpisode,
                            onToggleSeason = onToggleSeason,
                            onWatchUpTo = onWatchUpTo,
                        )
                    }
                }
            }

            SnackbarOverlay(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The "Серии" half of the list: a header per season and, while it is open, its episodes.
 *
 * Both expansion maps are owned by [DetailContent] — a LazyColumn drops the state of an
 * item that scrolls out of composition, so neither can live inside a row.
 */
private fun LazyListScope.seasonsSection(
    seasons: List<Season>,
    expandedSeasons: MutableMap<Int, Boolean>,
    expandedEpisodes: MutableMap<String, Boolean>,
    onToggleEpisode: (EpisodeEntity) -> Unit,
    onToggleSeason: (Season) -> Unit,
    onWatchUpTo: (EpisodeEntity) -> Unit,
) {
    seasons.forEach { season ->
        val isOpen = expandedSeasons[season.number] ?: false
        item(key = "season_${season.number}", contentType = "seasonHeader") {
            SeasonHeader(
                number = season.number,
                watchedCount = season.watchedCount,
                total = season.episodes.size,
                allWatched = season.allWatched,
                expanded = isOpen,
                onToggleExpand = { expandedSeasons[season.number] = !isOpen },
                onToggleWatched = { onToggleSeason(season) },
            )
        }
        if (isOpen) {
            items(
                items = season.episodes,
                key = { "ep_${season.number}_${it.episodeNumber}" },
                contentType = { "episode" },
            ) { episode ->
                val key = "${episode.seasonNumber}:${episode.episodeNumber}"
                EpisodeRow(
                    episode = episode,
                    onToggle = onToggleEpisode,
                    onWatchUpTo = onWatchUpTo,
                    overviewExpanded = expandedEpisodes[key] ?: false,
                    onToggleOverview = {
                        expandedEpisodes[key] = !(expandedEpisodes[key] ?: false)
                    },
                )
            }
        }
    }
}

/**
 * Bar above the list. [name] is empty until the header carrying the same name has scrolled
 * away, so the title is never written twice on one screen.
 */
@Composable
private fun DetailTopBar(
    name: String,
    canRefresh: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onBack, DetailTags.BACK)
        Text(
            text = name,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(DetailTags.TOP_TITLE),
        )
        if (canRefresh) {
            BarIcon(Icons.Filled.Refresh, "Обновить", onRefresh, DetailTags.REFRESH)
        }
        BarIcon(Icons.Filled.Delete, "Удалить", onDelete, DetailTags.DELETE)
    }
}

@Composable
private fun BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(48.dp).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun Header(item: TitleWithProgress) {
    val title = item.title
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Poster(
            path = title.posterPath,
            title = title.name,
            corner = 14,
            modifier = Modifier.width(84.dp).height(126.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title.name, fontSize = 22.sp, lineHeight = 28.sp)
            Text(
                text = listOfNotNull(title.mediaType.label, title.year).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            title.catalogRating?.takeIf { it > 0 }?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("%.1f".format(it), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (title.runtimeMinutes > 0) {
                Text(
                    text = if (title.isMovie) {
                        formatMinutes(title.runtimeMinutes)
                    } else {
                        "~${title.runtimeMinutes} мин/серия"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Segmented control that splits the screen into "Обзор" and "Серии". */
@Composable
private fun TabSwitcher(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        ) {
            SegmentButton(
                label = "Обзор",
                selected = selected == DetailTab.OVERVIEW,
                onClick = { onSelect(DetailTab.OVERVIEW) },
                tag = DetailTags.TAB_OVERVIEW,
                modifier = Modifier.weight(1f),
            )
            SegmentButton(
                label = "Серии",
                selected = selected == DetailTab.EPISODES,
                onClick = { onSelect(DetailTab.EPISODES) },
                tag = DetailTags.TAB_EPISODES,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.height(40.dp).testTag(tag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

// FlowRow is still ExperimentalLayoutApi on Compose 1.7; only its stable arguments are used.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusPicker(current: WatchStatus, onSelect: (WatchStatus) -> Unit) {
    // Wrapping, not a horizontal scroller: the status is often derived rather than tapped
    // (afterProgressChange completes a title on its last episode), and a scroller left at
    // offset 0 hides the chip that just became selected.
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WatchStatus.entries.forEach { status ->
            DesignChip(
                label = status.label,
                selected = current == status,
                onClick = { onSelect(status) },
                modifier = Modifier.testTag(DetailTags.statusChip(status)),
            )
        }
    }
}

/** How much of the synopsis is shown before "Показать полностью". */
private const val OVERVIEW_COLLAPSED_LINES = 4

@Composable
private fun OverviewBlock(text: String, expanded: Boolean, onToggle: () -> Unit) {
    // A short synopsis needs no button, and whether it is short is only known once the
    // collapsed layout has run — hence reading hasVisualOverflow rather than counting
    // characters.
    var overflows by remember(text) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else OVERVIEW_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier.fillMaxWidth().testTag(DetailTags.OVERVIEW),
        )
        if (overflows) {
            Text(
                text = if (expanded) "Свернуть" else "Показать полностью",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle)
                    .padding(vertical = 4.dp)
                    .testTag(DetailTags.OVERVIEW_EXPAND),
            )
        }
    }
}

@Composable
private fun RatingPicker(rating: Int?, onSelect: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Моя оценка",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = rating?.let { "$it / 10" } ?: "не выставлена",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (1..10).forEach { value ->
                val selected = rating == value
                Surface(
                    // Tapping the current score clears it.
                    onClick = { onSelect(if (selected) null else value) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag(DetailTags.rating(value)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBlock(
    item: TitleWithProgress,
    next: EpisodeEntity?,
    onMarkNext: () -> Unit,
    onAll: () -> Unit,
    onNone: () -> Unit,
    onMovieWatched: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (item.title.isMovie) {
                Text(
                    text = if (item.title.movieWatched) "Фильм просмотрен" else "Фильм не просмотрен",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                FilledPill(
                    label = if (item.title.movieWatched) "Снять отметку" else "Отметить просмотренным",
                    onClick = { onMovieWatched(!item.title.movieWatched) },
                    modifier = Modifier.testTag(DetailTags.MOVIE_TOGGLE),
                )
                return@Column
            }

            Text(
                text = "Просмотрено ${item.watchedCount} из ${item.episodeCount}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (next != null) {
                Text(
                    text = "Дальше: ${episodeCode(next.seasonNumber, next.episodeNumber)} — ${next.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (next != null) {
                    FilledPill(
                        label = "+1 серия",
                        onClick = onMarkNext,
                        modifier = Modifier.testTag(DetailTags.MARK_NEXT),
                    )
                }
                if (item.episodeCount > 0) {
                    OutlinedPill(
                        label = if (item.isCompleted) "Сбросить всё" else "Отметить всё",
                        onClick = if (item.isCompleted) onNone else onAll,
                        modifier = Modifier.testTag(DetailTags.TOGGLE_ALL),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilledPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.height(40.dp),
    ) {
        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun OutlinedPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(40.dp),
    ) {
        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private class NotesState(
    val open: Boolean,
    val text: String,
    val committed: String,
    val focusRequester: FocusRequester,
    val onOpen: () -> Unit,
    val onTextChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Owns `NotesBlock`'s state, hoisted to a level that survives the block's own disposal.
 *
 * `NotesBlock` sits inside a `LazyColumn` `item`, which is disposed by an ordinary scroll
 * or a tab switch, not only by leaving the screen. State (and the focus/flush effects
 * that depend on it) living inside the block itself used to be reinitialised — and its
 * flush-on-dispose fired — on every such scroll, closing the field and committing a
 * draft the user never asked to save.
 */
@Composable
private fun rememberNotesState(initial: String, onNotes: (String) -> Unit): NotesState {
    var open by rememberSaveable(initial) { mutableStateOf(false) }
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    // What the database is known to hold. `initial` only catches up once the write has
    // travelled back through Room, which would otherwise make an explicit save look
    // unsaved to the flush below.
    var committed by rememberSaveable(initial) { mutableStateOf(initial) }
    // One-shot: consumed by the focus effect below, so re-entering composition with
    // `open` already true (e.g. scrolling the block back into view) does not raise the
    // keyboard again on its own.
    var focusPending by rememberSaveable(initial) { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusPending) {
        if (focusPending) {
            focusRequester.requestFocus()
            focusPending = false
        }
    }

    // Leaving the screen with unsaved text used to lose it silently. Flush on disposal
    // instead: cancelling puts the committed text back first, so it writes nothing then.
    // Keyed on Unit at this composable's own level — as long as its caller does not sit
    // inside the LazyColumn, disposal means the screen itself going away.
    val latestText by rememberUpdatedState(text)
    val latestCommitted by rememberUpdatedState(committed)
    val latestOnNotes by rememberUpdatedState(onNotes)
    DisposableEffect(Unit) {
        onDispose {
            if (latestText != latestCommitted) latestOnNotes(latestText)
        }
    }

    fun collapse() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        open = false
    }

    return NotesState(
        open = open,
        text = text,
        committed = committed,
        focusRequester = focusRequester,
        onOpen = { open = true; focusPending = true },
        onTextChange = { text = it },
        onSave = { onNotes(text); committed = text; collapse() },
        onCancel = { text = committed; collapse() },
    )
}

/**
 * Notes are collapsed behind a button until the user wants them.
 *
 * Stateless by design: this composable sits inside a `LazyColumn` `item`, which is
 * disposed by an ordinary scroll, not only by leaving the screen — so `open`/`text` and
 * the focus/flush effects that depend on them live in [rememberNotesState] instead,
 * called by the caller above the `LazyColumn`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesBlock(
    initial: String,
    open: Boolean,
    text: String,
    committed: String,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // The block sits at the very bottom of the overview, so with the keyboard open it
    // is below the shrunken viewport: focusing the field, and later the save row
    // appearing under it, both have to scroll the list to it.
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val edited = open && text != committed
    LaunchedEffect(edited) {
        if (edited) bringIntoView.bringIntoView()
    }

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .bringIntoViewRequester(bringIntoView)
    ) {
        if (!open) {
            Surface(
                onClick = onOpen,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.height(40.dp).testTag(DetailTags.NOTES_OPEN),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (initial.isBlank()) "Добавить заметку" else "Заметка: ${initial.take(24)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Заметки",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            // A long note used to scroll inside three fixed lines; let the
                            // field grow with the text and only then start scrolling.
                            .heightIn(min = 60.dp, max = 200.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focus ->
                                if (focus.isFocused) scope.launch { bringIntoView.bringIntoView() }
                            }
                            .testTag(DetailTags.NOTES),
                    )
                }
            }
            // Both buttons are always here: with «Отмена» shown only after an edit there
            // was no way to close a field opened by mistake. «Сохранить» is disabled
            // instead of hidden, so the row does not jump as the text changes.
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledPill(
                    label = "Сохранить",
                    enabled = text != committed,
                    onClick = onSave,
                    modifier = Modifier.testTag(DetailTags.NOTES_SAVE),
                )
                DialogTextButton(
                    label = "Отмена",
                    onClick = onCancel,
                    modifier = Modifier.testTag(DetailTags.NOTES_CANCEL),
                )
            }
        }
    }
}

/**
 * Season header.
 *
 * A finished season is painted on `secondaryContainer` and carries a tick, a started one
 * shows a thin progress bar under its counter: scanning six seasons for the place you
 * stopped should not mean reading six fractions.
 */
@Composable
private fun SeasonHeader(
    number: Int,
    watchedCount: Int,
    total: Int,
    allWatched: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val started = watchedCount > 0 && !allWatched
    Surface(
        onClick = onToggleExpand,
        shape = RoundedCornerShape(12.dp),
        color = if (allWatched) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (allWatched) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(DetailTags.seasonHeader(number)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (allWatched) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Сезон просмотрен",
                    modifier = Modifier
                        .size(18.dp)
                        .testTag(DetailTags.seasonDone(number)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Сезон $number",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$watchedCount / $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allWatched) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (started) {
                    ProgressBar(
                        progress = { watchedCount.toFloat() / total },
                        height = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .testTag(DetailTags.seasonProgress(number)),
                    )
                }
            }
            Surface(
                onClick = onToggleWatched,
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = LocalContentColor.current,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.height(32.dp).testTag(DetailTags.seasonToggle(number)),
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (allWatched) "Снять" else "Весь сезон",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = if (allWatched) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    onToggle: (EpisodeEntity) -> Unit,
    onWatchUpTo: (EpisodeEntity) -> Unit,
    overviewExpanded: Boolean = false,
    onToggleOverview: () -> Unit = {},
) {
    val hasOverview = episode.overview.isNotBlank()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(DetailTags.episode(episode.seasonNumber, episode.episodeNumber))
                .clickable { onToggle(episode) }
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EpisodeCheckbox(
                checked = episode.watched,
                onClick = { onToggle(episode) },
                modifier = Modifier
                    .testTag(DetailTags.episodeCheckbox(episode.seasonNumber, episode.episodeNumber)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${episodeCode(episode.seasonNumber, episode.episodeNumber)} · ${episode.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.airDate?.let(::formatAirDate)?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The bare "≡✓" icon told nobody what it did — only TalkBack ever heard the
            // description. The label is what makes the action readable, so it is spelled out.
            if (!episode.watched) {
                Surface(
                    onClick = { onWatchUpTo(episode) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .height(32.dp)
                        .semantics { contentDescription = "Отметить всё до этой серии" }
                        .testTag(DetailTags.watchUpTo(episode.seasonNumber, episode.episodeNumber)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "до сюда",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            // A tap on the row is already the watched toggle, so the synopsis opens from
            // its own chevron instead of stealing that gesture.
            if (hasOverview) {
                Surface(
                    onClick = onToggleOverview,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag(DetailTags.episodeExpand(episode.seasonNumber, episode.episodeNumber)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (overviewExpanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = if (overviewExpanded) {
                                "Скрыть описание серии"
                            } else {
                                "Описание серии"
                            },
                        )
                    }
                }
            }
        }
        if (hasOverview && overviewExpanded) {
            Text(
                text = episode.overview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 16.dp, bottom = 8.dp)
                    .testTag(DetailTags.episodeOverview(episode.seasonNumber, episode.episodeNumber)),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Square 20dp checkbox from the mock rather than Material's larger default.
 *
 * Uses `toggleable` rather than a plain click so it reports checked/unchecked to
 * accessibility services (and to tests) the way a real checkbox does.
 */
@Composable
private fun EpisodeCheckbox(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = if (checked) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = modifier
            .size(20.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
