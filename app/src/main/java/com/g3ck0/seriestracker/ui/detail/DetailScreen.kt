package com.g3ck0.seriestracker.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.common.ClearFocusWhenDialogCloses
import com.g3ck0.seriestracker.ui.common.Poster
import com.g3ck0.seriestracker.ui.common.episodeCode
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.label

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
    const val REFRESHING = "detail:refreshing"
    const val NOT_FOUND = "detail:notFound"
    fun statusChip(status: WatchStatus) = "detail:status:${status.name}"
    fun rating(value: Int) = "detail:rating:$value"
    fun seasonHeader(season: Int) = "detail:season:$season"
    fun seasonToggle(season: Int) = "detail:seasonToggle:$season"
    fun episode(season: Int, episode: Int) = "detail:episode:$season:$episode"

    /** The row is clickable, the checkbox is toggleable — assertions need the latter. */
    fun episodeCheckbox(season: Int, episode: Int) = "detail:episodeCheck:$season:$episode"
    fun watchUpTo(season: Int, episode: Int) = "detail:upTo:$season:$episode"
}

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

@OptIn(ExperimentalMaterial3Api::class)
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
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить из библиотеки?") },
            text = { Text("Прогресс просмотра будет потерян.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    modifier = Modifier.testTag(DetailTags.CONFIRM_DELETE),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag(DetailTags.CANCEL_DELETE),
                ) { Text("Отмена") }
            },
        )
    }

    val item = state.title

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item?.title?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(DetailTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (item?.title?.tmdbId != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag(DetailTags.REFRESH),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Обновить из TMDB")
                        }
                    }
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag(DetailTags.DELETE),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (item == null) {
            Box(
                Modifier.fillMaxSize().padding(padding).testTag(DetailTags.NOT_FOUND),
                contentAlignment = Alignment.Center,
            ) {
                if (!state.loading) Text("Тайтл не найден")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag(DetailTags.LIST),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.refreshing) {
                item(key = "refreshing", contentType = "refreshing") {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().testTag(DetailTags.REFRESHING)
                    )
                }
            }

            item(key = "header", contentType = "header") { Header(item) }

            item(key = "status", contentType = "status") {
                StatusPicker(current = item.title.status, onSelect = onStatus)
            }

            item(key = "rating", contentType = "rating") {
                RatingPicker(rating = item.title.userRating, onSelect = onRating)
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

            item(key = "notes", contentType = "notes") {
                NotesBlock(initial = item.title.notes, onSave = onNotes)
            }

            if (state.seasons.isNotEmpty()) {
                item(key = "episodesTitle", contentType = "sectionTitle") {
                    Text(
                        text = "Серии",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

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
                        // One content type for every row, so scrolling reuses
                        // composition slots instead of building each row from scratch.
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

@Composable
private fun Header(item: TitleWithProgress) {
    val title = item.title
    Row(Modifier.padding(horizontal = 16.dp)) {
        Poster(
            path = title.posterPath,
            title = title.name,
            modifier = Modifier.width(110.dp).height(165.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(title.mediaType.label, title.year).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            title.tmdbRating?.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("%.1f TMDB".format(it), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (title.runtimeMinutes > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (title.isMovie) {
                        formatMinutes(title.runtimeMinutes)
                    } else {
                        "~${title.runtimeMinutes} мин/серия"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (title.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title.overview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            FilterChip(
                selected = current == status,
                onClick = { onSelect(status) },
                label = { Text(status.label) },
                modifier = Modifier.testTag(DetailTags.statusChip(status)),
            )
        }
    }
}

@Composable
private fun RatingPicker(rating: Int?, onSelect: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Моя оценка", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        // All ten fit on a phone at this size, so no horizontal scroll is needed.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (1..10).forEach { value ->
                val selected = rating == value
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag(DetailTags.rating(value))
                        // Tapping the current score clears it.
                        .clickable { onSelect(if (selected) null else value) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = value.toString(),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (item.title.isMovie) {
                Text(
                    text = if (item.title.movieWatched) "Фильм просмотрен" else "Фильм не просмотрен",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { onMovieWatched(!item.title.movieWatched) },
                    modifier = Modifier.testTag(DetailTags.MOVIE_TOGGLE),
                ) {
                    Text(if (item.title.movieWatched) "Снять отметку" else "Отметить просмотренным")
                }
                return@Column
            }

            Text(
                text = "Просмотрено ${item.watchedCount} из ${item.episodeCount}",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
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
                    FilledTonalButton(
                        onClick = onMarkNext,
                        modifier = Modifier.testTag(DetailTags.MARK_NEXT),
                    ) {
                        Text("+1 серия", maxLines = 1)
                    }
                }
                if (item.episodeCount > 0) {
                    OutlinedButton(
                        onClick = if (item.isCompleted) onNone else onAll,
                        modifier = Modifier.testTag(DetailTags.TOGGLE_ALL),
                    ) {
                        Text(
                            text = if (item.isCompleted) "Сбросить всё" else "Отметить всё",
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesBlock(initial: String, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Заметки") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag(DetailTags.NOTES),
        )
        if (text != initial) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onSave(text) },
                    modifier = Modifier.testTag(DetailTags.NOTES_SAVE),
                ) { Text("Сохранить") }
                TextButton(onClick = { text = initial }) { Text("Отмена") }
            }
        }
    }
}

/** Takes plain values, not [Season] — a data class holding a List is unstable to Compose. */
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .testTag(DetailTags.seasonHeader(number))
            .clickable(onClick = onToggleExpand),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Сезон $number", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "$watchedCount / $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = onToggleWatched,
                label = { Text(if (allWatched) "Снять" else "Весь сезон") },
                modifier = Modifier.testTag(DetailTags.seasonToggle(number)),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
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
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = episode.watched,
                onCheckedChange = { onToggle(episode) },
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
                IconButton(
                    onClick = { onWatchUpTo(episode) },
                    modifier = Modifier
                        .testTag(DetailTags.watchUpTo(episode.seasonNumber, episode.episodeNumber)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAddCheck,
                        contentDescription = "Отметить всё до этой серии",
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
