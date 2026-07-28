package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.library.LibraryFilters
import com.g3ck0.seriestracker.ui.library.LibraryTags
import com.g3ck0.seriestracker.ui.library.LibraryContent
import com.g3ck0.seriestracker.ui.library.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun series(
        id: String = "tv_1",
        name: String = "Dark",
        status: WatchStatus = WatchStatus.WATCHING,
        episodes: Int = 10,
        watched: Int = 3,
    ) = TitleWithProgress(
        title = TitleEntity(
            id = id,
            tmdbId = 1,
            mediaType = MediaType.TV,
            name = name,
            status = status,
            year = "2017",
        ),
        episodeCount = episodes,
        watchedCount = watched,
    )

    private fun movie(id: String = "movie_1", name: String = "Fight Club", watched: Boolean = false) =
        TitleWithProgress(
            title = TitleEntity(
                id = id,
                tmdbId = 5,
                mediaType = MediaType.MOVIE,
                name = name,
                status = if (watched) WatchStatus.COMPLETED else WatchStatus.PLANNED,
                movieWatched = watched,
                year = "1999",
            ),
            episodeCount = 0,
            watchedCount = 0,
        )

    @Test
    fun seriesCardShowsProgressAndRemaining() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Сериал · 2017 · Смотрю").assertIsDisplayed()
        compose.onNodeWithText("3 / 10 · осталось 7").assertIsDisplayed()
    }

    @Test
    fun movieCardShowsWatchedState() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(movie(watched = true))))
        }

        compose.onNodeWithText("Fight Club").assertIsDisplayed()
        compose.onNodeWithText("Просмотрен").assertIsDisplayed()
    }

    @Test
    fun titleWithoutEpisodesSaysSo() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series(episodes = 0, watched = 0)))
            )
        }

        compose.onNodeWithText("Серии не загружены").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryInvitesToSearch() {
        var searched = false
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = emptyList(), totalCount = 0),
                onSearch = { searched = true },
            )
        }

        compose.onNodeWithTag(LibraryTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Библиотека пуста").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.FAB).performClick()
        assertTrue(searched)
    }

    @Test
    fun filteredToNothingSaysFiltersNotEmptyLibrary() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = emptyList(), totalCount = 4),
            )
        }

        compose.onNodeWithText("Ничего не найдено по фильтрам").assertIsDisplayed()
    }

    @Test
    fun typingInTheFilterReportsTheQuery() {
        var query = ""
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onQueryChange = { query = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.FILTER_QUERY).performTextInput("dar")

        assertEquals("dar", query)
    }

    @Test
    fun statusChipReportsSelection() {
        var selected: WatchStatus? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onStatusFilter = { selected = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.statusChip(WatchStatus.COMPLETED))
            .performScrollTo()
            .performClick()

        assertEquals(WatchStatus.COMPLETED, selected)
    }

    @Test
    fun tappingASelectedStatusChipClearsIt() {
        var selected: WatchStatus? = WatchStatus.COMPLETED
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series()),
                    filters = LibraryFilters(status = WatchStatus.COMPLETED),
                ),
                onStatusFilter = { selected = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.statusChip(WatchStatus.COMPLETED))
            .performScrollTo()
            .performClick()

        assertEquals(null, selected)
    }

    @Test
    fun mediaChipReportsSelection() {
        var selected: MediaType? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onMediaFilter = { selected = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.mediaChip(MediaType.MOVIE))
            .performScrollTo()
            .performClick()

        assertEquals(MediaType.MOVIE, selected)
    }

    @Test
    fun cardClickOpensTheTitle() {
        var opened: String? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onOpenTitle = { opened = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.card("tv_1")).performClick()

        assertEquals("tv_1", opened)
    }

    @Test
    fun plusOneButtonMarksTheNextEpisode() {
        var marked: String? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onMarkNext = { marked = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.markNext("tv_1")).performClick()

        assertEquals("tv_1", marked)
    }

    @Test
    fun finishedSeriesHidesThePlusButton() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series(episodes = 10, watched = 10)),
                ),
            )
        }

        compose.onNodeWithTag(LibraryTags.markNext("tv_1")).assertDoesNotExist()
    }

    @Test
    fun movieButtonTogglesWatched() {
        var result: Pair<String, Boolean>? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(movie())),
                onToggleMovie = { id, watched -> result = id to watched },
            )
        }

        compose.onNodeWithTag(LibraryTags.markNext("movie_1")).performClick()

        assertEquals("movie_1" to true, result)
    }

    @Test
    fun overflowMenuChangesStatus() {
        var result: Pair<String, WatchStatus>? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onSetStatus = { id, status -> result = id to status },
            )
        }

        compose.onNodeWithTag(LibraryTags.overflow("tv_1")).performClick()
        compose.onNodeWithTag(LibraryTags.statusItem(WatchStatus.DROPPED)).performClick()

        assertEquals("tv_1" to WatchStatus.DROPPED, result)
    }

    @Test
    fun overflowMenuDeletes() {
        var deleted: String? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onDelete = { deleted = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.overflow("tv_1")).performClick()
        compose.onNodeWithTag(LibraryTags.DELETE_ITEM).performClick()

        assertEquals("tv_1", deleted)
    }

    @Test
    fun messageIsShownAndAcknowledgedOnce() {
        var shown = 0
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                message = "Отмечено: S01E04",
                onMessageShown = { shown++ },
            )
        }

        compose.onNodeWithText("Отмечено: S01E04").assertIsDisplayed()
        // The message is acknowledged only once the snackbar finishes showing.
        compose.waitUntil(timeoutMillis = 10_000) { shown == 1 }
    }

    @Test
    fun listRendersEveryTitle() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series(id = "tv_1", name = "Dark"), movie(id = "movie_1")),
                ),
            )
        }

        compose.onNodeWithTag(LibraryTags.LIST).assertIsDisplayed()
        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Fight Club").assertIsDisplayed()
    }
}
