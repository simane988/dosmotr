package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.ui.common.UserError
import com.g3ck0.seriestracker.ui.search.ManualAddTags
import com.g3ck0.seriestracker.ui.search.SearchContent
import com.g3ck0.seriestracker.ui.search.SearchTags
import com.g3ck0.seriestracker.ui.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun item(
        catalogId: Int = 1,
        name: String = "Dark",
        type: MediaType = MediaType.TV,
        rating: Double? = 8.3,
    ) = SearchItem(
        catalogId = catalogId,
        mediaType = type,
        name = name,
        overview = "Описание $name",
        posterPath = null,
        backdropPath = null,
        year = "2017",
        voteAverage = rating,
    )

    @Test
    fun trendingHeaderShowsOnlyForTrending() {
        compose.setThemedContent {
            SearchContent(state = SearchUiState(results = listOf(item()), showingTrending = true))
        }

        compose.onNodeWithText("Популярное за неделю").assertIsDisplayed()
    }

    @Test
    fun searchResultsHideTheTrendingHeader() {
        compose.setThemedContent {
            SearchContent(
                state = SearchUiState(query = "dark", results = listOf(item()), showingTrending = false)
            )
        }

        compose.onNodeWithText("Популярное за неделю").assertDoesNotExist()
        compose.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun resultShowsTypeYearAndRating() {
        compose.setThemedContent {
            SearchContent(state = SearchUiState(results = listOf(item())))
        }

        compose.onNodeWithText("Сериал · 2017").assertIsDisplayed()
        compose.onNodeWithText("8.3").assertIsDisplayed()
        compose.onNodeWithText("Описание Dark").assertIsDisplayed()
    }

    @Test
    fun typingReportsTheQuery() {
        var typed = ""
        compose.setThemedContent {
            SearchContent(state = SearchUiState(), onQueryChange = { typed = it })
        }

        compose.onNodeWithTag(SearchTags.QUERY).performTextInput("wed")

        assertEquals("wed", typed)
    }

    @Test
    fun clearButtonEmptiesTheQuery() {
        var typed = "dark"
        compose.setThemedContent {
            SearchContent(state = SearchUiState(query = "dark"), onQueryChange = { typed = it })
        }

        compose.onNodeWithTag(SearchTags.CLEAR).performClick()

        assertEquals("", typed)
    }

    @Test
    fun addButtonReportsTheItem() {
        var added: SearchItem? = null
        compose.setThemedContent {
            SearchContent(state = SearchUiState(results = listOf(item())), onAdd = { added = it })
        }

        compose.onNodeWithTag(SearchTags.add("tv_1")).performClick()

        assertEquals("Dark", added?.name)
    }

    @Test
    fun alreadyTrackedItemOpensInsteadOfAdding() {
        var opened: String? = null
        var added = false
        compose.setThemedContent {
            SearchContent(
                state = SearchUiState(results = listOf(item())),
                trackedIds = setOf("tv_1"),
                onAdd = { added = true },
                onOpenTitle = { opened = it },
            )
        }

        compose.onNodeWithTag(SearchTags.add("tv_1")).performClick()

        assertEquals("tv_1", opened)
        assertTrue(!added)
    }

    @Test
    fun missingApiKeyBlocksTheFieldAndExplains() {
        compose.setThemedContent {
            SearchContent(state = SearchUiState(hasBackend = false))
        }

        compose.onNodeWithTag(SearchTags.NO_BACKEND).assertIsDisplayed()
        compose.onNodeWithText("Поиск недоступен").assertIsDisplayed()
        compose.onNodeWithTag(SearchTags.QUERY).assertIsNotEnabled()
    }

    @Test
    fun errorOffersRetry() {
        var retried = false
        compose.setThemedContent {
            SearchContent(
                state = SearchUiState(
                    error = UserError("Нет сети", "Проверь сеть и попробуй ещё раз"),
                ),
                onSearchNow = { retried = true },
            )
        }

        compose.onNodeWithTag(SearchTags.ERROR).assertIsDisplayed()
        compose.onNodeWithText("Нет сети").assertIsDisplayed()
        compose.onNodeWithText("Проверь сеть и попробуй ещё раз").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()

        assertTrue(retried)
    }

    @Test
    fun emptyResultsExplainAndOfferManualEntry() {
        compose.setThemedContent {
            SearchContent(state = SearchUiState(query = "zzz", results = emptyList(), showingTrending = false))
        }

        compose.onNodeWithTag(SearchTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Ничего не найдено").assertIsDisplayed()
    }

    @Test
    fun emptyQueryDoesNotShowTheEmptyState() {
        compose.setThemedContent {
            SearchContent(state = SearchUiState(query = "", results = emptyList()))
        }

        compose.onNodeWithTag(SearchTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun loadingIndicatorFollowsTheFlag() {
        compose.setThemedContent {
            SearchContent(state = SearchUiState(loading = true))
        }

        compose.onNodeWithTag(SearchTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun manualDialogCollectsNameAndSeasons() {
        var result: Triple<String, MediaType, List<Int>>? = null
        compose.setThemedContent {
            SearchContent(
                state = SearchUiState(),
                onAddManual = { name, type, seasons, _, _ -> result = Triple(name, type, seasons) },
            )
        }

        compose.onNodeWithTag(SearchTags.MANUAL_FAB).performClick()
        compose.onNodeWithTag(ManualAddTags.NAME).performTextInput("Своё шоу")
        compose.onNodeWithTag(ManualAddTags.SEASONS).performTextReplacement("4, 6")
        compose.onNodeWithTag(ManualAddTags.CONFIRM).performClick()

        assertEquals(Triple("Своё шоу", MediaType.TV, listOf(4, 6)), result)
    }

    @Test
    fun manualDialogRefusesAnEmptyName() {
        compose.setThemedContent { SearchContent(state = SearchUiState()) }

        compose.onNodeWithTag(SearchTags.MANUAL_FAB).performClick()
        compose.onNodeWithTag(ManualAddTags.CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun manualDialogHidesSeasonsForMovies() {
        compose.setThemedContent { SearchContent(state = SearchUiState()) }

        compose.onNodeWithTag(SearchTags.MANUAL_FAB).performClick()
        compose.onNodeWithTag(ManualAddTags.type(MediaType.MOVIE)).performClick()

        compose.onNodeWithTag(ManualAddTags.SEASONS).assertDoesNotExist()
    }

    @Test
    fun manualDialogCanBeCancelled() {
        compose.setThemedContent { SearchContent(state = SearchUiState()) }

        compose.onNodeWithTag(SearchTags.MANUAL_FAB).performClick()
        compose.onNodeWithTag(ManualAddTags.CANCEL).performClick()

        compose.onNodeWithTag(ManualAddTags.NAME).assertDoesNotExist()
    }

    @Test
    fun seasonSummaryUpdatesWhileTyping() {
        compose.setThemedContent { SearchContent(state = SearchUiState()) }

        compose.onNodeWithTag(SearchTags.MANUAL_FAB).performClick()
        compose.onNodeWithTag(ManualAddTags.SEASONS).performTextReplacement("3, 3, 4")

        // The supporting text is merged into the text field's semantics node.
        compose.onNodeWithTag(ManualAddTags.SEASONS_SUMMARY, useUnmergedTree = true)
            .assertTextEquals("3 сезона, всего 10 серий")
    }
}
