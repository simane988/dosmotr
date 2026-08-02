package com.g3ck0.seriestracker.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.backup.BackupRepository.ImportMode
import com.g3ck0.seriestracker.ui.FloatingFabClearance
import com.g3ck0.seriestracker.ui.FloatingNavClearance
import com.g3ck0.seriestracker.ui.about.AboutDialog
import com.g3ck0.seriestracker.ui.backup.BackupViewModel
import com.g3ck0.seriestracker.ui.common.ClearFocusWhenDialogCloses
import com.g3ck0.seriestracker.ui.common.DesignChip
import com.g3ck0.seriestracker.ui.common.DesignDialog
import com.g3ck0.seriestracker.ui.common.DialogTextButton
import com.g3ck0.seriestracker.ui.common.ExtendedActionButton
import com.g3ck0.seriestracker.ui.common.PillSearchField
import com.g3ck0.seriestracker.ui.common.Poster
import com.g3ck0.seriestracker.ui.common.ProgressBar
import com.g3ck0.seriestracker.ui.common.SnackbarOverlay
import com.g3ck0.seriestracker.ui.common.label
import com.g3ck0.seriestracker.ui.common.nextLabel
import com.g3ck0.seriestracker.ui.common.plural

object LibraryTags {
    const val LIST = "library:list"
    const val FILTER_QUERY = "library:query"
    const val EMPTY = "library:empty"
    const val TOP_MENU = "library:topMenu"
    const val EXPORT = "library:export"
    const val IMPORT = "library:import"
    const val ABOUT = "library:about"
    const val FAB = "library:fab"
    fun card(titleId: String) = "library:card:$titleId"
    fun markNext(titleId: String) = "library:markNext:$titleId"
    fun nextEpisode(titleId: String) = "library:next:$titleId"
    fun overflow(titleId: String) = "library:overflow:$titleId"

    /** Menu entries repeat the filter-chip labels, so they need their own handles. */
    fun statusItem(status: WatchStatus) = "library:menu:${status.name}"
    const val DELETE_ITEM = "library:menu:delete"

    /** Header naming the card the menu belongs to — the menu can cover that card. */
    const val MENU_TITLE = "library:menu:title"

    // Chips wrap onto as many rows as they need; tags address them by identity.
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
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    var askImportMode by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf(ImportMode.MERGE) }

    // The ViewModel outlives this composable, so entering the screen is what re-sorts the
    // library; while it is open the order stays put (see LibraryViewModel.pinnedOrder).
    LaunchedEffect(Unit) { viewModel.refreshOrder() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(backupViewModel::export) }

    // Some file managers report JSON as octet-stream or plain text — accept all three.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { backupViewModel.import(it, importMode) } }

    // Without this the search field grabs focus as the dialog closes, pops the keyboard
    // and covers the bottom bar, so the next tab tap goes nowhere.
    ClearFocusWhenDialogCloses(askImportMode)

    if (askImportMode) {
        DesignDialog(
            title = "Импорт из JSON",
            onDismiss = { askImportMode = false },
            content = {
                Text(
                    "Объединить — библиотека сохранится, добавятся недостающие тайтлы, " +
                        "отметки о просмотре сложатся.\n\n" +
                        "Заменить — текущая библиотека будет стёрта и восстановлена из файла."
                )
            },
            confirmButton = {
                DialogTextButton(
                    label = "Объединить",
                    onClick = {
                        askImportMode = false
                        importMode = ImportMode.MERGE
                        importLauncher.launch(IMPORT_TYPES)
                    },
                )
            },
            dismissButton = {
                DialogTextButton(
                    label = "Заменить",
                    onClick = {
                        askImportMode = false
                        importMode = ImportMode.REPLACE
                        importLauncher.launch(IMPORT_TYPES)
                    },
                )
            },
        )
    }

    LibraryContent(
        state = state,
        message = state.message ?: backupState.message?.let { LibraryMessage(it) },
        onMessageShown = {
            if (state.message != null) viewModel.consumeMessage() else backupViewModel.consumeMessage()
        },
        onMessageAction = viewModel::undoDelete,
        onQueryChange = viewModel::setQuery,
        onStatusFilter = viewModel::setStatusFilter,
        onMediaFilter = viewModel::setMediaFilter,
        onOpenTitle = onOpenTitle,
        onSearch = onSearch,
        onMarkNext = { viewModel.markNextWatched(it) },
        onToggleMovie = { id, watched -> viewModel.toggleMovieWatched(id, watched) },
        onSetStatus = { id, status -> viewModel.setStatus(id, status) },
        onDelete = { viewModel.delete(it) },
        onExport = { exportLauncher.launch(backupViewModel.suggestedFileName()) },
        onImport = { askImportMode = true },
    )
}

private val IMPORT_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
fun LibraryContent(
    state: LibraryUiState,
    message: LibraryMessage? = null,
    onMessageShown: () -> Unit = {},
    onMessageAction: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onStatusFilter: (WatchStatus?) -> Unit = {},
    onMediaFilter: (MediaType?) -> Unit = {},
    onOpenTitle: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onMarkNext: (String) -> Unit = {},
    onToggleMovie: (String, Boolean) -> Unit = { _, _ -> },
    onSetStatus: (String, WatchStatus) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var aboutOpen by remember { mutableStateOf(false) }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })

    LaunchedEffect(message) {
        message?.let {
            val result = snackbar.showSnackbar(
                message = it.text,
                actionLabel = it.action,
                duration = if (it.action == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            // Consumed first, then acted on: the action posts a message of its own
            // ("восстановлен"), and consuming afterwards would wipe it before it is shown.
            onMessageShown()
            if (result == SnackbarResult.ActionPerformed) onMessageAction()
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                LargeHeader(
                    title = "Моя библиотека",
                    onExport = onExport,
                    onImport = onImport,
                    onAbout = { aboutOpen = true },
                )

                PillSearchField(
                    value = state.filters.query,
                    onValueChange = onQueryChange,
                    placeholder = "Фильтр по названию",
                    fieldModifier = Modifier.testTag(LibraryTags.FILTER_QUERY),
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                )

                FilterRow(
                    selectedStatus = state.filters.status,
                    selectedType = state.filters.mediaType,
                    onStatus = onStatusFilter,
                    onType = onMediaFilter,
                )

                if (state.items.isEmpty() && !state.loading) {
                    EmptyLibrary(hasAnything = state.totalCount > 0)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = FloatingNavClearance,
                        ),
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

            ExtendedActionButton(
                icon = Icons.Filled.Add,
                label = "Добавить",
                onClick = onSearch,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(end = 16.dp, bottom = FloatingFabClearance)
                    .testTag(LibraryTags.FAB),
            )

            SnackbarOverlay(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Large top app bar from the mock: 32sp title, menu button on its own container. */
@Composable
private fun LargeHeader(
    title: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAbout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        )
        Box {
            Surface(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp).testTag(LibraryTags.TOP_MENU),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("Экспорт в JSON") },
                    leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                    onClick = { menuOpen = false; onExport() },
                    modifier = Modifier.testTag(LibraryTags.EXPORT),
                )
                DropdownMenuItem(
                    text = { Text("Импорт из JSON") },
                    leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    onClick = { menuOpen = false; onImport() },
                    modifier = Modifier.testTag(LibraryTags.IMPORT),
                )
                DropdownMenuItem(
                    text = { Text("О приложении") },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = { menuOpen = false; onAbout() },
                    modifier = Modifier.testTag(LibraryTags.ABOUT),
                )
            }
        }
    }
}

// FlowRow is still ExperimentalLayoutApi on Compose 1.7; only its stable arguments are used.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    selectedStatus: WatchStatus?,
    selectedType: MediaType?,
    onStatus: (WatchStatus?) -> Unit,
    onType: (MediaType?) -> Unit,
) {
    // Wrapping, not a horizontal scroller: eight chips do not fit on a 411 dp screen, and
    // in a scroller "Фильм" is off the right edge until the user thinks to swipe for it.
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DesignChip(
            label = "Все",
            selected = selectedStatus == null && selectedType == null,
            onClick = { onStatus(null); onType(null) },
            modifier = Modifier.testTag(LibraryTags.CHIP_ALL),
        )
        WatchStatus.entries.forEach { status ->
            DesignChip(
                label = status.label,
                selected = selectedStatus == status,
                onClick = { onStatus(if (selectedStatus == status) null else status) },
                modifier = Modifier.testTag(LibraryTags.statusChip(status)),
            )
        }
        MediaType.entries.forEach { type ->
            DesignChip(
                label = type.label,
                selected = selectedType == type,
                onClick = { onType(if (selectedType == type) null else type) },
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
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LibraryTags.card(title.id)),
    ) {
        Box {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Poster(
                    path = title.posterPath,
                    title = title.name,
                    corner = 14,
                    modifier = Modifier.width(64.dp).height(96.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.name,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
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
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))

                    if (title.isMovie) {
                        Text(
                            text = if (title.movieWatched) "Просмотрен" else "Не просмотрен",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (item.episodeCount == 0) {
                        Text(
                            text = "Серии не загружены",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // The watched count is the headline number in this design.
                        // One Text with two spans, not two Texts in a Row: side by side
                        // they wrap independently and the tail jumps above the number.
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                ) {
                                    append(item.watchedCount.toString())
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                ) {
                                    val unit = plural(
                                        item.episodeCount,
                                        "серия",
                                        "серии",
                                        "серий",
                                    )
                                    append(" / ${item.episodeCount} $unit")
                                }
                            },
                            lineHeight = 24.sp,
                            maxLines = 1,
                        )
                        // What "+ 1 серия" will mark, so the button is not pressed blind.
                        Text(
                            text = item.nextLabel ?: "Всё просмотрено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(LibraryTags.nextEpisode(title.id)),
                        )
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth(),
                            height = 8.dp,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    OverflowMenu(
                        titleId = title.id,
                        titleName = title.name,
                        onStatus = onStatus,
                        onDelete = onDelete,
                    )
                    if (title.isMovie) {
                        // A watched and an unwatched movie must not share one look: the
                        // pill is filled with a solid check once watched, and an outlined
                        // check on a plain outline while it is not.
                        ActionPill(
                            icon = if (title.movieWatched) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Outlined.CheckCircle
                            },
                            label = null,
                            onClick = { onToggleMovie(!title.movieWatched) },
                            filled = title.movieWatched,
                            contentDescription = if (title.movieWatched) {
                                "Снять отметку о просмотре"
                            } else {
                                "Отметить просмотренным"
                            },
                            modifier = Modifier.testTag(LibraryTags.markNext(title.id)),
                        )
                    } else if (item.remaining > 0) {
                        ActionPill(
                            icon = Icons.Filled.Add,
                            label = "1 серия",
                            onClick = onMarkNext,
                            modifier = Modifier.testTag(LibraryTags.markNext(title.id)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    contentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (filled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label == null) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription ?: label ?: "Отметить",
                modifier = Modifier.size(20.dp),
            )
            if (label != null) {
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    titleId: String,
    titleName: String,
    onStatus: (WatchStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp).testTag(LibraryTags.overflow(titleId)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            // The menu is anchored to a 36dp button and opens upwards on the lower cards,
            // covering the ones above it — including the card it belongs to. Without this
            // header there is nothing on screen saying which title is about to be deleted.
            Text(
                text = titleName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag(LibraryTags.MENU_TITLE),
            )
            HorizontalDivider()
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
private fun EmptyLibrary(hasAnything: Boolean) {
    Box(
        Modifier.fillMaxSize().testTag(LibraryTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 64.dp),
        ) {
            Text(
                text = if (hasAnything) "Ничего не найдено по фильтрам" else "Библиотека пуста",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (hasAnything) {
                    "Сбрось фильтры или измени запрос"
                } else {
                    "Найди сериал или фильм и добавь его в отслеживание"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
