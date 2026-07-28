package com.g3ck0.seriestracker.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.ui.FloatingNavClearance
import com.g3ck0.seriestracker.ui.common.ClearFocusWhenDialogCloses
import com.g3ck0.seriestracker.ui.common.ExtendedActionButton
import com.g3ck0.seriestracker.ui.common.PillSearchField
import com.g3ck0.seriestracker.ui.common.Poster
import com.g3ck0.seriestracker.ui.common.SnackbarOverlay
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

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Text(
                    text = "Поиск",
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                )

                PillSearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Сериал или фильм",
                    enabled = state.hasApiKey,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchNow() }),
                    trailing = {
                        if (state.query.isNotEmpty()) {
                            Surface(
                                onClick = { onQueryChange("") },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp).testTag(SearchTags.CLEAR),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                                }
                            }
                        }
                    },
                    fieldModifier = Modifier.testTag(SearchTags.QUERY),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                if (state.loading) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(4.dp)
                            .testTag(SearchTags.LOADING)
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
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = FloatingNavClearance,
                            ),
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

            ExtendedActionButton(
                icon = Icons.Filled.Edit,
                label = "Вручную",
                onClick = { manualDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 104.dp)
                    .testTag(SearchTags.MANUAL_FAB),
            )

            SnackbarOverlay(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
    Surface(
        onClick = { if (added) onOpen() else onAdd() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(
                path = item.posterPath,
                title = item.name,
                corner = 12,
                modifier = Modifier.width(62.dp).height(93.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = listOfNotNull(item.mediaType.label, item.year).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.voteAverage?.takeIf { it > 0 }?.let { rating ->
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(text = "%.1f".format(rating), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Surface(
                onClick = { if (added) onOpen() else onAdd() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.height(40.dp).testTag(SearchTags.add(item.id)),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (added) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = if (added) "Уже добавлено" else "Добавить",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoApiKey(onManual: () -> Unit) {
    CenteredMessage(
        tag = SearchTags.NO_API_KEY,
        title = "Поиск недоступен",
        body = "Добавь строку tmdb.apiKey=… в local.properties и пересобери приложение. " +
            "Ключ бесплатный: themoviedb.org → Settings → API.",
        actionLabel = "Добавить вручную",
        onAction = onManual,
    )
}

@Composable
private fun NothingFound(query: String, onManual: () -> Unit) {
    CenteredMessage(
        tag = SearchTags.EMPTY,
        title = "Ничего не найдено",
        body = "По запросу «$query» в TMDB ничего нет. Проверь написание или добавь запись вручную.",
        actionLabel = "Добавить вручную",
        onAction = onManual,
    )
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    CenteredMessage(
        tag = SearchTags.ERROR,
        title = message,
        body = null,
        actionLabel = "Повторить",
        onAction = onRetry,
    )
}

@Composable
private fun CenteredMessage(
    tag: String,
    title: String,
    body: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onAction,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 8.dp).height(40.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
