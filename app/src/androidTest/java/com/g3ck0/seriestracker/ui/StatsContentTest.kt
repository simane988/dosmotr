package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.stats.StatsContent
import com.g3ck0.seriestracker.ui.stats.StatsTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatsContentTest {

    @get:Rule
    val compose = createComposeRule()

    /** A library with a single title, so the screen shows numbers instead of the empty state. */
    private fun nonEmpty(stats: WatchStats) =
        if (stats.isEmpty) stats.copy(seriesCount = 1) else stats

    @Test
    fun emptyLibraryShowsThePlaceholderInsteadOfZeroes() {
        var searched = false
        compose.setThemedContent { StatsContent(WatchStats(), onSearch = { searched = true }) }

        compose.onNodeWithTag(StatsTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.EMPTY_SEARCH).performClick()
        assertTrue(searched)
    }

    @Test
    fun aLibraryWithNothingWatchedStillRendersZeroes() {
        compose.setThemedContent { StatsContent(WatchStats(seriesCount = 1)) }

        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals("0 мин")
        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertTextEquals("0 серий · 0 фильмов")
        compose.onNodeWithTag(StatsTags.SERIES_COUNT).assertTextEquals("1")
    }

    @Test
    fun totalsAreFormattedAsHoursAndMinutes() {
        compose.setThemedContent {
            StatsContent(
                nonEmpty(
                    WatchStats(
                        watchedEpisodes = 5,
                        episodeMinutes = 225,
                        watchedMovies = 1,
                        movieMinutes = 139,
                    )
                )
            )
        }

        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals("6 ч 4 мин")
        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertTextEquals("5 серий · 1 фильм")
    }

    @Test
    fun pluralsFollowTheCount() {
        compose.setThemedContent {
            StatsContent(nonEmpty(WatchStats(watchedEpisodes = 2, watchedMovies = 3, episodeMinutes = 60)))
        }

        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertTextEquals("2 серии · 3 фильма")
    }

    @Test
    fun timeCardsKeepTheMinutes() {
        compose.setThemedContent {
            StatsContent(nonEmpty(WatchStats(episodeMinutes = 200, movieMinutes = 59)))
        }

        compose.onNodeWithTag(StatsTags.SERIES_TIME).assertTextEquals("3 ч 20 мин")
        compose.onNodeWithTag(StatsTags.MOVIE_TIME).assertTextEquals("59 мин")
    }

    @Test
    fun libraryCountsAreShown() {
        compose.setThemedContent { StatsContent(WatchStats(seriesCount = 7, movieCount = 4)) }

        compose.onNodeWithTag(StatsTags.SERIES_COUNT).assertTextEquals("7")
        compose.onNodeWithTag(StatsTags.MOVIE_COUNT).assertTextEquals("4")
    }

    @Test
    fun remainingIsShownWhenSomethingIsLeftToWatch() {
        compose.setThemedContent {
            StatsContent(WatchStats(seriesCount = 2, remainingEpisodes = 7, remainingMinutes = 315))
        }

        compose.onNodeWithTag(StatsTags.REMAINING).assertTextEquals("5 ч 15 мин")
        compose.onNodeWithTag(StatsTags.REMAINING_SUB).assertTextEquals("7 серий в статусе «Смотрю»")
    }

    @Test
    fun remainingCardIsHiddenWithNothingLeft() {
        compose.setThemedContent { StatsContent(WatchStats(seriesCount = 2)) }

        compose.onNodeWithTag(StatsTags.REMAINING).assertDoesNotExist()
    }

    @Test
    fun onlyNonEmptyStatusesGetALegendRow() {
        compose.setThemedContent {
            StatsContent(
                WatchStats(
                    seriesCount = 4,
                    byStatus = mapOf(WatchStatus.WATCHING to 3, WatchStatus.DROPPED to 1),
                )
            )
        }

        compose.onNodeWithTag(StatsTags.STATUS_BAR).assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.statusCount(WatchStatus.WATCHING)).assertTextEquals("3")
        compose.onNodeWithTag(StatsTags.statusCount(WatchStatus.DROPPED)).assertTextEquals("1")
        compose.onNodeWithTag(StatsTags.statusCount(WatchStatus.COMPLETED)).assertDoesNotExist()
    }
}
