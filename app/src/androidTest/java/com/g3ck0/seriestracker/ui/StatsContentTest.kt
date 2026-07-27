package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.stats.StatsContent
import com.g3ck0.seriestracker.ui.stats.StatsTags
import org.junit.Rule
import org.junit.Test

class StatsContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStatsRenderZeroes() {
        compose.setThemedContent { StatsContent(WatchStats()) }

        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals("0 мин")
        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertTextEquals("0 серий · 0 фильмов")
        compose.onNodeWithTag(StatsTags.SERIES_COUNT).assertTextEquals("0")
    }

    @Test
    fun totalsAreFormattedAsHoursAndMinutes() {
        compose.setThemedContent {
            StatsContent(
                WatchStats(
                    watchedEpisodes = 5,
                    episodeMinutes = 225,
                    watchedMovies = 1,
                    movieMinutes = 139,
                )
            )
        }

        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals("6 ч 4 мин")
        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertTextEquals("5 серий · 1 фильм")
    }

    @Test
    fun pluralsFollowTheCount() {
        compose.setThemedContent {
            StatsContent(WatchStats(watchedEpisodes = 2, watchedMovies = 3, episodeMinutes = 60))
        }

        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertTextEquals("2 серии · 3 фильма")
    }

    @Test
    fun hourCardsTruncateToWholeHours() {
        compose.setThemedContent {
            StatsContent(WatchStats(episodeMinutes = 200, movieMinutes = 59))
        }

        compose.onNodeWithTag(StatsTags.SERIES_HOURS).assertTextEquals("3")
        compose.onNodeWithTag(StatsTags.MOVIE_HOURS).assertTextEquals("0")
    }

    @Test
    fun libraryCountsAreShown() {
        compose.setThemedContent { StatsContent(WatchStats(seriesCount = 7, movieCount = 4)) }

        compose.onNodeWithTag(StatsTags.SERIES_COUNT).assertTextEquals("7")
        compose.onNodeWithTag(StatsTags.MOVIE_COUNT).assertTextEquals("4")
    }

    @Test
    fun everyStatusRowIsRenderedIncludingZeroes() {
        compose.setThemedContent {
            StatsContent(
                WatchStats(
                    byStatus = mapOf(WatchStatus.WATCHING to 3, WatchStatus.DROPPED to 1)
                )
            )
        }

        compose.onNodeWithTag(StatsTags.statusCount(WatchStatus.WATCHING)).assertTextEquals("3")
        compose.onNodeWithTag(StatsTags.statusCount(WatchStatus.DROPPED)).assertTextEquals("1")
        compose.onNodeWithTag(StatsTags.statusCount(WatchStatus.COMPLETED)).assertTextEquals("0")
    }
}
