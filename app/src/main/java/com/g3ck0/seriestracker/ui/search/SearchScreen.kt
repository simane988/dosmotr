package com.g3ck0.seriestracker.ui.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.ui.common.ClearFocusWhenDialogCloses
import com.g3ck0.seriestracker.ui.common.Poster
import com.g3ck0.seriestracker.ui.common.label

object SearchTags {
    const val QUERY = "search:query"
    const val CLEAR = "search:clear"
    const val LIST = "search:list"
    const val MANUAL_FAB = "search:manualFab"
    const val LOADING = "search:loading"
    const val NO_API_KEY = "search:noApiKey"
    const val ERROR = "search:error"
    const val EMPTY = "search:empty"
    fun add(id: String) = "search:add:$id"
}

@Composable
fun SearchScreen(
    onOpenTitle: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracked by viewModel.trackedIds.collectAsStateWithLifecycle()

    SearchContent(
        state = state,
        trackedIds = tracked,
        onQueryChange = viewModel::onQueryChange,
        onSearchNow = viewModel::searchNow,
        onAdd = { viewModel.add(it) },
        onOpenTitle = onOpenTitle,
        onAddManual = { name, type, seasons, runtime, year ->
            viewModel.addManual(name, type, seasons, runtime, year)
        },
        onMessageShown = viewModel::consumeMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    state: SearchUiState,
    trackedIds: Set<String> = emptySet(),
    onQueryChange: (String) -> Unit = {},
    onSearchNow: () -> Unit = {},
    onAdd: (SearchItem) -> Unit = {},
    onOpenTitle: (String) -> Unit = {},
    onAddManual: (String, MediaType, List<Int>, Int, String?) -> Unit = { _, _, _, _, _ -> },
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var manualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    // Without this the search field grabs focus as the dialog closes, pops the keyboard
    // and covers the bottom bar, so the next tab tap goes nowhere.
    ClearFocusWhenDialogCloses(manualDialog)

    if (manualDialog) {
        ManualAddDialog(
            onDismiss = { manualDialog = false },
            onConfirm = { name, type, seasons, runtime, year ->
                onAddManual(name, type, seasons, runtime, year)
                manualDialog = false
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Поиск") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { manualDialog = true },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Вручную") },
                modifier = Modifier.testTag(SearchTags.MANUAL_FAB),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Сериал или фильм") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.testTag(SearchTags.CLEAR),
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchNow() }),
                enabled = state.hasApiKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SearchTags.QUERY),
            )

            if (state.loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().testTag(SearchTags.LOADING)
                )
            }

            when {
                !state.hasApiKey -> NoApiKey(onManual = { manualDialog = true })

                state.error != null -> ErrorBlock(message = state.error, onRetry = onSearchNow)

                state.results.isEmpty() && !state.loading && state.query.isNotBlank() ->
                    NothingFound(query = state.query, onManual = { manualDialog = true })

                else -> {
                    if (state.showingTrending && state.results.isNotEmpty()) {
                        Text(
                            text = "Популярное за неделю",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag(SearchTags.LIST),
                    ) {
                        items(state.results, key = { it.id }, contentType = { "result" }) { item ->
                            ResultCard(
                                item = item,
                                added = item.id in trackedIds,
                                onAdd = { onAdd(item) },
                                onOpen = { onOpenTitle(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    item: SearchItem,
    added: Boolean,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = { if (added) onOpen() else onAdd() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(
                path = item.posterPath,
                title = item.name,
                modifier = Modifier
                    .width(62.dp)
                    .height(93.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOfNotNull(item.mediaType.label, item.year).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.voteAverage?.takeIf { it > 0 }?.let { rating ->
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.width(14.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "%.1f".format(rating),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (item.overview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { if (added) onOpen() else onAdd() },
                modifier = Modifier.testTag(SearchTags.add(item.id)),
            ) {
                Icon(
                    imageVector = if (added) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (added) "Уже добавлено" else "Добавить",
                )
            }
        }
    }
}

@Composable
private fun NoApiKey(onManual: () -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag(SearchTags.NO_API_KEY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("Поиск недоступен", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Добавь строку tmdb.apiKey=… в local.properties и пересобери приложение. " +
                    "Ключ бесплатный: themoviedb.org → Settings → API.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onManual) { Text("Добавить вручную") }
        }
    }
}

@Composable
private fun NothingFound(query: String, onManual: () -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag(SearchTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "По запросу «$query» в TMDB ничего нет. Проверь написание " +
                    "или добавь запись вручную.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onManual) { Text("Добавить вручную") }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag(SearchTags.ERROR),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}
