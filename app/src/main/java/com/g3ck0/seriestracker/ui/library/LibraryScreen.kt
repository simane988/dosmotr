package com.g3ck0.seriestracker.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.backup.BackupMenu
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.label
import com.g3ck0.seriestracker.ui.common.Poster

object LibraryTags {
    const val LIST = "library:list"
    const val FILTER_QUERY = "library:query"
    const val EMPTY = "library:empty"
    fun card(titleId: String) = "library:card:$titleId"
    fun markNext(titleId: String) = "library:markNext:$titleId"
    fun overflow(titleId: String) = "library:overflow:$titleId"

    /** Menu entries repeat the filter-chip labels, so they need their own handles. */
    fun statusItem(status: WatchStatus) = "library:menu:${status.name}"
    const val DELETE_ITEM = "library:menu:delete"

    // Chips live in a horizontal scroller; tags let tests scroll to them by identity.
    const val CHIP_ALL = "library:chip:all"
    fun statusChip(status: WatchStatus) = "library:chip:${status.name}"
    fun mediaChip(type: MediaType) = "library:chip:${type.name}"
}

/** Wires the ViewModel to [LibraryContent]; all UI lives in the stateless half. */
@Composable
fun LibraryScreen(
    onOpenTitle: (String) -> Unit,
    onSearch: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var backupMessage by remember { mutableStateOf<String?>(null) }

    LibraryContent(
        state = state,
        message = state.message ?: backupMessage,
        onMessageShown = {
            if (state.message != null) viewModel.consumeMessage() else backupMessage = null
        },
        onQueryChange = viewModel::setQuery,
        onStatusFilter = viewModel::setStatusFilter,
        onMediaFilter = viewModel::setMediaFilter,
        onOpenTitle = onOpenTitle,
        onSearch = onSearch,
        onMarkNext = { viewModel.markNextWatched(it) },
        onToggleMovie = { id, watched -> viewModel.toggleMovieWatched(id, watched) },
        onSetStatus = { id, status -> viewModel.setStatus(id, status) },
        onDelete = { viewModel.delete(it) },
        actions = { BackupMenu(onMessage = { backupMessage = it }) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onStatusFilter: (WatchStatus?) -> Unit = {},
    onMediaFilter: (MediaType?) -> Unit = {},
    onOpenTitle: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onMarkNext: (String) -> Unit = {},
    onToggleMovie: (String, Boolean) -> Unit = { _, _ -> },
    onSetStatus: (String, WatchStatus) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Моя библиотека") }, actions = actions)
        },
        // The outer Scaffold in AppRoot already consumed the system insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onSearch) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = onQueryChange,
                label = { Text("Фильтр по названию") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(LibraryTags.FILTER_QUERY),
            )

            FilterRow(
                selectedStatus = state.filters.status,
                selectedType = state.filters.mediaType,
                onStatus = onStatusFilter,
                onType = onMediaFilter,
            )

            if (state.items.isEmpty() && !state.loading) {
                EmptyLibrary(hasAnything = state.totalCount > 0, onSearch = onSearch)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag(LibraryTags.LIST),
                ) {
                    items(state.items, key = { it.title.id }, contentType = { "title" }) { item ->
                        TitleCard(
                            item = item,
                            onOpen = { onOpenTitle(item.title.id) },
                            onMarkNext = { onMarkNext(item.title.id) },
                            onToggleMovie = { onToggleMovie(item.title.id, it) },
                            onStatus = { onSetStatus(item.title.id, it) },
                            onDelete = { onDelete(item.title.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedStatus: WatchStatus?,
    selectedType: MediaType?,
    onStatus: (WatchStatus?) -> Unit,
    onType: (MediaType?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedStatus == null && selectedType == null,
            onClick = { onStatus(null); onType(null) },
            label = { Text("Все") },
            modifier = Modifier.testTag(LibraryTags.CHIP_ALL),
        )
        WatchStatus.entries.forEach { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onStatus(if (selectedStatus == status) null else status) },
                label = { Text(status.label) },
                modifier = Modifier.testTag(LibraryTags.statusChip(status)),
            )
        }
        MediaType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onType(if (selectedType == type) null else type) },
                label = { Text(type.label) },
                modifier = Modifier.testTag(LibraryTags.mediaChip(type)),
            )
        }
    }
}

@Composable
private fun TitleCard(
    item: TitleWithProgress,
    onOpen: () -> Unit,
    onMarkNext: () -> Unit,
    onToggleMovie: (Boolean) -> Unit,
    onStatus: (WatchStatus) -> Unit,
    onDelete: () -> Unit,
) {
    val title = item.title
    Card(
        onClick = onOpen,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LibraryTags.card(title.id)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(
                path = title.posterPath,
                title = title.name,
                modifier = Modifier
                    .width(62.dp)
                    .height(93.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        title.mediaType.label,
                        title.year,
                        title.status.label,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (title.isMovie) {
                    Text(
                        text = if (title.movieWatched) "Просмотрен" else "Не просмотрен",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Прогресс" },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (item.episodeCount == 0) {
                            "Серии не загружены"
                        } else {
                            "${item.watchedCount} / ${item.episodeCount} · осталось ${episodesLabel(item.remaining)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (title.isMovie) {
                    FilledTonalIconButton(
                        onClick = { onToggleMovie(!title.movieWatched) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(LibraryTags.markNext(title.id)),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Отметить просмотренным",
                        )
                    }
                } else if (item.remaining > 0) {
                    FilledTonalIconButton(
                        onClick = onMarkNext,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(LibraryTags.markNext(title.id)),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Плюс одна серия")
                    }
                }
                OverflowMenu(
                    titleId = title.id,
                    onStatus = onStatus,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(titleId: String, onStatus: (WatchStatus) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(LibraryTags.overflow(titleId)),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WatchStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label) },
                    onClick = { onStatus(status); expanded = false },
                    modifier = Modifier.testTag(LibraryTags.statusItem(status)),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Удалить") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { onDelete(); expanded = false },
                modifier = Modifier.testTag(LibraryTags.DELETE_ITEM),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(hasAnything: Boolean, onSearch: () -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag(LibraryTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = if (hasAnything) "Ничего не найдено по фильтрам" else "Библиотека пуста",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasAnything) {
                    "Сбрось фильтры или измени запрос"
                } else {
                    "Найди сериал или фильм и добавь его в отслеживание"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasAnything) {
                Spacer(Modifier.height(16.dp))
                FloatingActionButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Поиск")
                }
            }
        }
    }
}
