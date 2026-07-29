package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.detail.DetailContent
import com.g3ck0.seriestracker.ui.detail.DetailTags
import com.g3ck0.seriestracker.ui.detail.DetailUiState
import com.g3ck0.seriestracker.ui.detail.Season
import com.g3ck0.seriestracker.ui.library.LibraryContent
import com.g3ck0.seriestracker.ui.library.LibraryTags
import com.g3ck0.seriestracker.ui.library.LibraryUiState
import com.g3ck0.seriestracker.ui.search.SearchContent
import com.g3ck0.seriestracker.ui.search.SearchTags
import com.g3ck0.seriestracker.ui.search.SearchUiState
import com.g3ck0.seriestracker.ui.stats.StatsContent
import com.g3ck0.seriestracker.ui.stats.StatsTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The app runs dark on most phones, but every other UI test renders light. These keep
 * the dark scheme covered: content must still be there and still respond to taps.
 *
 * Contrast itself is not asserted — Compose semantics carry no colour, so a washed-out
 * palette would pass. That check stays manual (or belongs in screenshot tests).
 */
class DarkThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private val series = TitleWithProgress(
        title = TitleEntity(
            id = "tv_1",
            catalogId = 1,
            mediaType = MediaType.TV,
            name = "Уэнздей",
            overview = "Описание",
            year = "2022",
            status = WatchStatus.WATCHING,
            runtimeMinutes = 45,
        ),
        episodeCount = 8,
        watchedCount = 2,
    )

    @Test
    fun libraryRendersAndRespondsInDark() {
        var opened: String? = null
        compose.setThemedContent(darkTheme = true) {
            LibraryContent(
                state = LibraryUiState(loading = false, items = listOf(series)),
                onOpenTitle = { opened = it },
            )
        }

        compose.onNodeWithText("Уэнздей").assertIsDisplayed()
        compose.onNodeWithText("2 / 8 · осталось 6").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.card("tv_1")).performClick()

        assertEquals("tv_1", opened)
    }

    @Test
    fun emptyLibraryRendersInDark() {
        compose.setThemedContent(darkTheme = true) {
            LibraryContent(state = LibraryUiState(loading = false, items = emptyList()))
        }

        compose.onNodeWithTag(LibraryTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Библиотека пуста").assertIsDisplayed()
    }

    @Test
    fun detailRendersAndRespondsInDark() {
        var marked = false
        val episodes = (1..4).map {
            EpisodeEntity("tv_1", 1, it, name = "Серия $it", watched = it <= 2)
        }
        compose.setThemedContent(darkTheme = true) {
            DetailContent(
                state = DetailUiState(
                    loading = false,
                    title = series,
                    seasons = listOf(Season(1, episodes)),
                    nextEpisode = episodes.first { !it.watched },
                ),
                onMarkNext = { marked = true },
            )
        }

        compose.onNodeWithText("Просмотрено 2 из 8").assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.MARK_NEXT).performClick()

        assertTrue(marked)
    }

    @Test
    fun searchRendersInDark() {
        compose.setThemedContent(darkTheme = true) {
            SearchContent(state = SearchUiState(hasBackend = false))
        }

        compose.onNodeWithTag(SearchTags.NO_BACKEND).assertIsDisplayed()
        compose.onNodeWithText("Поиск недоступен").assertIsDisplayed()
    }

    @Test
    fun statsRenderInDark() {
        compose.setThemedContent(darkTheme = true) {
            StatsContent(WatchStats(watchedEpisodes = 5, episodeMinutes = 225, watchedMovies = 1, movieMinutes = 139))
        }

        compose.onNodeWithTag(StatsTags.TOTAL).assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.TOTAL_SUB).assertIsDisplayed()
    }
}
