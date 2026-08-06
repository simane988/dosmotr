package com.g3ck0.seriestracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.g3ck0.seriestracker.ui.about.AboutTags
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.common.SnackbarTags
import com.g3ck0.seriestracker.ui.library.LibraryFilters
import com.g3ck0.seriestracker.ui.library.LibraryMessage
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
        nextSeason: Int? = 1,
        nextEpisode: Int? = 4,
        nextName: String? = "Двойники",
        userRating: Int? = null,
        notes: String = "",
    ) = TitleWithProgress(
        title = TitleEntity(
            id = id,
            catalogId = 1,
            mediaType = MediaType.TV,
            name = name,
            status = status,
            year = "2017",
            userRating = userRating,
            notes = notes,
        ),
        episodeCount = episodes,
        watchedCount = watched,
        nextSeason = nextSeason,
        nextEpisode = nextEpisode,
        nextName = nextName,
    )

    private fun movie(
        id: String = "movie_1",
        name: String = "Fight Club",
        watched: Boolean = false,
        status: WatchStatus = if (watched) WatchStatus.COMPLETED else WatchStatus.PLANNED,
        overview: String = "",
    ) =
        TitleWithProgress(
            title = TitleEntity(
                id = id,
                catalogId = 5,
                mediaType = MediaType.MOVIE,
                name = name,
                status = status,
                overview = overview,
                movieWatched = watched,
                year = "1999",
            ),
            episodeCount = 0,
            watchedCount = 0,
        )

    @Test
    fun seriesCardShowsProgressAndNextEpisode() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Сериал · 2017 · Смотрю").assertIsDisplayed()
        compose.onNodeWithText("3 / 10 серий").assertIsDisplayed()
        // The card Surface is clickable, so it merges its children: the line has its own
        // node only in the unmerged tree.
        compose.onNodeWithTag(LibraryTags.nextEpisode("tv_1"), useUnmergedTree = true)
            .assertTextEquals("S01E04 · Двойники")
    }

    /** Nothing left to watch: the card says so rather than showing a stale episode. */
    @Test
    fun watchedThroughSeriesCardSaysEverythingWatched() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(
                        series(
                            watched = 10,
                            nextSeason = null,
                            nextEpisode = null,
                            nextName = null,
                        )
                    ),
                )
            )
        }

        // The card Surface is clickable, so it merges its children: the line has its own
        // node only in the unmerged tree.
        compose.onNodeWithTag(LibraryTags.nextEpisode("tv_1"), useUnmergedTree = true)
            .assertTextEquals("Всё просмотрено")
    }

    @Test
    fun movieCardShowsWatchedState() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(movie(watched = true))))
        }

        compose.onNodeWithText("Fight Club").assertIsDisplayed()
        compose.onNodeWithText("Просмотрен").assertIsDisplayed()
    }

    /**
     * feature-3: the score and the note were only visible inside a title, which made both
     * useless on a library of dozens.
     */
    @Test
    fun cardShowsTheUserScoreAndThatThereIsANote() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series(userRating = 9, notes = "Пересмотреть финал")),
                ),
            )
        }

        compose.onNodeWithTag(LibraryTags.rating("tv_1"), useUnmergedTree = true)
            .assertTextEquals("9")
        compose.onNodeWithTag(LibraryTags.notes("tv_1"), useUnmergedTree = true).assertExists()
    }

    /** Nothing to show: no empty star, no orphan note icon. */
    @Test
    fun anUnratedTitleWithoutNotesShowsNeitherBadge() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithTag(LibraryTags.rating("tv_1"), useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.notes("tv_1"), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /** A movie card is as tall as a series one, so the freed half carries the synopsis. */
    @Test
    fun movieCardFillsItsSpaceWithTheSynopsis() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(movie(overview = "Бессонный клерк основывает бойцовский клуб.")),
                ),
            )
        }

        compose.onNodeWithTag(LibraryTags.overview("movie_1"), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** No synopsis stored: the card simply has no such line, and nothing else moves. */
    @Test
    fun movieCardWithoutASynopsisShowsNoEmptyLine() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(movie())))
        }

        compose.onNodeWithTag(LibraryTags.overview("movie_1"), useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithText("Не просмотрен").assertIsDisplayed()
    }

    /** "Фильм · Смотрю" over "Не просмотрен" is a contradiction; the status goes. */
    @Test
    fun aWatchingMovieDoesNotAdvertiseThatStatus() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(movie(status = WatchStatus.WATCHING)),
                ),
            )
        }

        compose.onNodeWithText("Фильм · 1999").assertIsDisplayed()
        compose.onNodeWithText("Не просмотрен").assertIsDisplayed()
    }

    /** The other four statuses are hand-picked and stay on the card. */
    @Test
    fun aDroppedMovieStillShowsItsStatus() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(movie(status = WatchStatus.DROPPED)),
                ),
            )
        }

        compose.onNodeWithText("Фильм · 1999 · Брошено").assertIsDisplayed()
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
    fun emptyLibraryHidesFiltersAndOffersSearchAndImport() {
        var searched = false
        var imported = false
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = emptyList(), totalCount = 0),
                onSearch = { searched = true },
                onImport = { imported = true },
            )
        }

        compose.onNodeWithTag(LibraryTags.FILTER_QUERY).assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.statusChip(WatchStatus.WATCHING)).assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.CHIP_TYPE).assertDoesNotExist()
        // The menu shares the filter's row, and import is what an empty library needs.
        compose.onNodeWithTag(LibraryTags.TOP_MENU).assertIsDisplayed()

        compose.onNodeWithTag(LibraryTags.EMPTY_SEARCH).assertIsDisplayed().performClick()
        assertTrue(searched)

        compose.onNodeWithTag(LibraryTags.EMPTY_IMPORT).assertIsDisplayed().performClick()
        assertTrue(imported)
    }

    @Test
    fun notificationPromptIsOfferedAboveTheList() {
        var enabled = false
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series()),
                    askNotifications = true,
                ),
                onEnableNotifications = { enabled = true },
            )
        }

        compose.onNodeWithTag(LibraryTags.NOTIFY_PROMPT).assertIsDisplayed()
        compose.onNodeWithText("Сообщать о новых сериях?").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.NOTIFY_ENABLE).performClick()
        assertTrue(enabled)
    }

    /** The flag is the whole condition: cleared, the row is not in the list at all. */
    @Test
    fun notificationPromptIsAbsentWhenNotAsking() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series()),
                    askNotifications = false,
                ),
            )
        }

        compose.onNodeWithTag(LibraryTags.NOTIFY_PROMPT).assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.NOTIFY_ENABLE).assertDoesNotExist()
        compose.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun filteredToNothingSaysFiltersNotEmptyLibrary() {
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = emptyList(), totalCount = 4),
            )
        }

        compose.onNodeWithText("Ничего не найдено по фильтрам").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.FILTER_QUERY).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.statusChip(WatchStatus.WATCHING)).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.EMPTY_SEARCH).assertDoesNotExist()
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
            .performClick()

        assertEquals(null, selected)
    }

    /**
     * feature-12: two media chips became one that cycles, so each tap has to report the
     * next type in the ring rather than the one on the chip.
     */
    @Test
    fun theTypeChipCyclesThroughTheMediaTypes() {
        var selected: MediaType? = null
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                onMediaFilter = { selected = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.CHIP_TYPE).performClick()
        assertEquals(MediaType.TV, selected)
    }

    @Test
    fun theTypeChipClearsItselfOnTheThirdTap() {
        var selected: MediaType? = MediaType.MOVIE
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(
                    loading = false,
                    items = listOf(series()),
                    filters = LibraryFilters(mediaType = MediaType.MOVIE),
                ),
                onMediaFilter = { selected = it },
            )
        }

        compose.onNodeWithTag(LibraryTags.CHIP_TYPE).performClick()
        assertEquals(null, selected)
    }

    /**
     * The chips wrap onto a second row, so the last of them has to be on screen straight
     * away rather than hidden past the right edge.
     */
    @Test
    fun theLastFilterChipIsVisibleWithoutScrollingSideways() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithTag(LibraryTags.CHIP_TYPE).assertIsDisplayed()
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

    /**
     * The menu can open upwards over the neighbouring cards, so the title it belongs to
     * has to be named inside it — otherwise "Удалить" is aimed at nothing visible.
     */
    @Test
    fun overflowMenuNamesItsTitle() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithTag(LibraryTags.overflow("tv_1")).performClick()

        compose.onNodeWithTag(LibraryTags.MENU_TITLE).assertTextEquals("Dark")
    }

    @Test
    fun messageIsShownAndAcknowledgedOnce() {
        var shown = 0
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series())),
                message = LibraryMessage("Отмечено: S01E04"),
                onMessageShown = { shown++ },
            )
        }

        compose.onNodeWithText("Отмечено: S01E04").assertIsDisplayed()
        // The message is acknowledged only once the snackbar finishes showing.
        compose.waitUntil(timeoutMillis = 10_000) { shown == 1 }
    }

    @Test
    fun deleteMessageOffersUndo() {
        var undone = 0
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false, items = emptyList(), totalCount = 1),
                message = LibraryMessage("Тайтл «Dark» удалён", action = "Отменить"),
                onMessageAction = { undone++ },
            )
        }

        compose.onNodeWithText("Тайтл «Dark» удалён").assertIsDisplayed()
        compose.onNodeWithTag(SnackbarTags.ACTION).performClick()

        compose.waitUntil(timeoutMillis = 10_000) { undone == 1 }
    }

    @Test
    fun topMenuOpensAboutWithTheTmdbAttribution() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithTag(LibraryTags.TOP_MENU).performClick()
        compose.onNodeWithTag(LibraryTags.ABOUT).performClick()

        compose.onNodeWithTag(AboutTags.DIALOG).assertExists()
        compose.onNodeWithTag(AboutTags.VERSION).assertIsDisplayed()
        compose.onNodeWithTag(AboutTags.REPO).assertIsDisplayed()
        compose.onNodeWithText("github.com/simane988/dosmotr").assertIsDisplayed()
        // TMDB requires this sentence verbatim, so the test spells it out in full.
        compose.onNodeWithText(
            "This application uses TMDB and the TMDB APIs but is not endorsed, " +
                "certified, or otherwise approved by TMDB."
        ).assertIsDisplayed()
    }

    @Test
    fun aboutClosesOnItsButton() {
        compose.setThemedContent {
            LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
        }

        compose.onNodeWithTag(LibraryTags.TOP_MENU).performClick()
        compose.onNodeWithTag(LibraryTags.ABOUT).performClick()
        compose.onNodeWithTag(AboutTags.CLOSE).performClick()

        compose.onNodeWithTag(AboutTags.DIALOG).assertDoesNotExist()
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

    /**
     * bug-6: with room beside the pill (landscape, 914 dp wide) the FAB drops to the
     * bottom edge instead of hovering a pill's height up, where it covered the buttons
     * of the first card.
     */
    @Test
    fun theFabDropsToTheBottomEdgeWhenThePillLeavesRoomBesideIt() {
        var systemBars = 0.dp
        showLibraryWith(FloatingNavMetrics(space = 104.dp, freeWidth = 598.dp)) {
            systemBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        }

        // The bar itself is not a fixed height — 24 dp of gesture bar on one device, a
        // 48 dp button bar on the emulator — so the expected gap is measured, not spelt
        // out: it is exactly the bar plus the 16 dp gap the FAB keeps off it. Anything
        // lifted over the pill lands 104 dp higher and fails this.
        val gap = fabBottomGap()
        assertTrue(
            "FAB sits $gap above the bottom edge, over $systemBars of system bars",
            gap <= systemBars + 16.dp + tolerance,
        )
    }

    /** Portrait: the pill spans the width, so the FAB has to clear it vertically. */
    @Test
    fun theFabIsLiftedOverThePillWhenNothingFitsBesideIt() {
        showLibraryWith(FloatingNavMetrics(space = 104.dp, freeWidth = 95.dp))

        val gap = fabBottomGap()
        assertTrue("FAB sits $gap above the bottom edge", gap >= 104.dp)
    }

    /**
     * feature-3: the FAB is drawn over the list, so scrolling to the end used to leave the
     * bottom-right corner of the last card under the button.
     */
    @Test
    fun theLastCardIsNotTrappedUnderTheFab() {
        val items = (1..12).map { series(id = "tv_$it", name = "Тайтл $it") }
        compose.setThemedContent {
            CompositionLocalProvider(
                LocalFloatingNav provides FloatingNavMetrics(space = 104.dp, freeWidth = 95.dp)
            ) {
                LibraryContent(state = LibraryUiState(loading = false, items = items))
            }
        }

        // The filter block is item 0 of the list now, so the cards start one index later.
        compose.onNodeWithTag(LibraryTags.LIST).performScrollToIndex(items.size)
        compose.waitForIdle()

        val card = compose.onNodeWithTag(LibraryTags.card("tv_12")).getBoundsInRoot()
        val fab = compose.onNodeWithTag(LibraryTags.FAB).getBoundsInRoot()
        assertTrue(
            "card ends at ${card.bottom}, FAB starts at ${fab.top}",
            card.bottom <= fab.top,
        )
    }

    private fun showLibraryWith(metrics: FloatingNavMetrics, onComposed: @Composable () -> Unit = {}) {
        compose.setThemedContent {
            CompositionLocalProvider(LocalFloatingNav provides metrics) {
                onComposed()
                LibraryContent(state = LibraryUiState(loading = false, items = listOf(series())))
            }
        }
    }

    /** Rounding between measured insets and laid-out pixels, nothing more. */
    private val tolerance = 2.dp

    /** Distance from the bottom of the FAB to the bottom of the screen. */
    private fun fabBottomGap(): Dp =
        compose.onRoot().getBoundsInRoot().bottom -
            compose.onNodeWithTag(LibraryTags.FAB).getBoundsInRoot().bottom
}
