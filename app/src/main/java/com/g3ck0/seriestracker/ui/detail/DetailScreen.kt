package com.g3ck0.seriestracker.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
    const val NOTES_OPEN = "detail:notesOpen"
    const val REFRESHING = "detail:refreshing"
    const val NOT_FOUND = "detail:notFound"
    const val TAB_OVERVIEW = "detail:tab:overview"
    const val TAB_EPISODES = "detail:tab:episodes"
    fun statusChip(status: WatchStatus) = "detail:status:${status.name}"
    fun rating(value: Int) = "detail:rating:$value"
    fun seasonHeader(season: Int) = "detail:season:$season"
    fun seasonToggle(season: Int) = "detail:seasonToggle:$season"
    fun episode(season: Int, episode: Int) = "detail:episode:$season:$episode"

    /** The row is clickable, the checkbox is toggleable — assertions need the latter. */
    fun episodeCheckbox(season: Int, episode: Int) = "detail:episodeCheck:$season:$episode"
    fun watchUpTo(season: Int, episode: Int) = "detail:upTo:$season:$episode"
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

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                DetailTopBar(
                    name = item?.title?.name.orEmpty(),
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

                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(DetailTags.LIST),
                    contentPadding = PaddingValues(bottom = 48.dp),
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
                            NotesBlock(initial = item.title.notes, onSave = onNotes)
                        }
                    } else {
                        state.seasons.forEach { season ->
                            val isOpen = expanded[season.number] ?: false
                            item(key = "season_${season.number}", contentType = "seasonHeader") {
                                SeasonHeader(
                                    number = season.number,
                                    watchedCount = season.watchedCount,
                                    total = season.episodes.size,
                                    allWatched = season.allWatched,
                                    expanded = isOpen,
                                    onToggleExpand = { expanded[season.number] = !isOpen },
                                    onToggleWatched = { onToggleSeason(season) },
                                )
                            }
                            if (isOpen) {
                                items(
                                    items = season.episodes,
                                    key = { "ep_${season.number}_${it.episodeNumber}" },
                                    contentType = { "episode" },
                                ) { episode ->
                                    EpisodeRow(
                                        episode = episode,
                                        onToggle = onToggleEpisode,
                                        onWatchUpTo = onWatchUpTo,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SnackbarOverlay(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

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
            modifier = Modifier.weight(1f),
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

@Composable
private fun StatusPicker(current: WatchStatus, onSelect: (WatchStatus) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
private fun FilledPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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

/** Notes are collapsed behind a button until the user wants them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesBlock(initial: String, onSave: (String) -> Unit) {
    var open by remember(initial) { mutableStateOf(false) }
    var text by remember(initial) { mutableStateOf(initial) }

    // The block sits at the very bottom of the overview, so with the keyboard open it
    // is below the shrunken viewport: focusing the field, and later the save row
    // appearing under it, both have to scroll the list to it.
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val edited = open && text != initial
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
                onClick = { open = true },
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
                        onValueChange = { text = it },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(60.dp)
                            .onFocusChanged { focus ->
                                if (focus.isFocused) scope.launch { bringIntoView.bringIntoView() }
                            }
                            .testTag(DetailTags.NOTES),
                    )
                }
            }
            if (text != initial) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledPill(
                        label = "Сохранить",
                        onClick = { onSave(text); open = false },
                        modifier = Modifier.testTag(DetailTags.NOTES_SAVE),
                    )
                    DialogTextButton(label = "Отмена", onClick = { text = initial; open = false })
                }
            }
        }
    }
}

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
    Surface(
        onClick = onToggleExpand,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Сезон $number",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$watchedCount / $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onToggleWatched,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    onToggle: (EpisodeEntity) -> Unit,
    onWatchUpTo: (EpisodeEntity) -> Unit,
) {
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
                episode.airDate?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!episode.watched) {
                Surface(
                    onClick = { onWatchUpTo(episode) },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag(DetailTags.watchUpTo(episode.seasonNumber, episode.episodeNumber)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            contentDescription = "Отметить всё до этой серии",
                        )
                    }
                }
            }
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
