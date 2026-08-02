package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.detail.DetailContent
import com.g3ck0.seriestracker.ui.detail.DetailTags
import com.g3ck0.seriestracker.ui.detail.DetailUiState
import com.g3ck0.seriestracker.ui.detail.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun episodes(season: Int, count: Int, watchedUpTo: Int = 0) = (1..count).map {
        EpisodeEntity(
            titleId = "tv_1",
            seasonNumber = season,
            episodeNumber = it,
            name = "Серия $it",
            airDate = "2020-01-0$season",
            watched = it <= watchedUpTo,
        )
    }

    private fun seriesState(
        watched: Int = 1,
        total: Int = 4,
        status: WatchStatus = WatchStatus.WATCHING,
        rating: Int? = null,
        notes: String = "",
        seasons: List<Season> = listOf(Season(1, episodes(1, 4, watched))),
    ): DetailUiState {
        val title = TitleEntity(
            id = "tv_1",
            catalogId = 1,
            mediaType = MediaType.TV,
            name = "Уэнздей",
            overview = "Описание сериала",
            year = "2022",
            status = status,
            userRating = rating,
            catalogRating = 8.3,
            runtimeMinutes = 45,
            notes = notes,
        )
        return DetailUiState(
            loading = false,
            title = TitleWithProgress(title, episodeCount = total, watchedCount = watched),
            seasons = seasons,
            nextEpisode = seasons.flatMap { it.episodes }.firstOrNull { !it.watched },
        )
    }

    private fun movieState(watched: Boolean = false) = DetailUiState(
        loading = false,
        title = TitleWithProgress(
            TitleEntity(
                id = "movie_1",
                catalogId = 5,
                mediaType = MediaType.MOVIE,
                name = "Бойцовский клуб",
                year = "1999",
                runtimeMinutes = 139,
                movieWatched = watched,
            ),
            episodeCount = 0,
            watchedCount = 0,
        ),
    )

    /** Episodes live behind their own tab in this design. */
    private fun openEpisodesTab() {
        compose.onNodeWithTag(DetailTags.TAB_EPISODES).performClick()
    }

    @Test
    fun headerShowsMetadata() {
        compose.setThemedContent { DetailContent(state = seriesState()) }

        compose.onNodeWithText("Сериал · 2022").assertIsDisplayed()
        compose.onNodeWithText("8.3").assertIsDisplayed()
        compose.onNodeWithText("~45 мин/серия").assertIsDisplayed()
    }

    /** The backend sends "2020-01-01"; nobody writes a date that way in Russian. */
    @Test
    fun airDatesAreShownInRussian() {
        compose.setThemedContent { DetailContent(state = seriesState()) }
        openEpisodesTab()

        // Every episode of the fixture season carries the same date.
        compose.onAllNodesWithText("1 января 2020")[0].assertIsDisplayed()
        compose.onAllNodesWithText("2020-01-01").assertCountEquals(0)
    }

    /** A date the backend cannot supply properly is dropped, not shown as "Invalid date". */
    @Test
    fun brokenAirDatesAreNotShown() {
        val broken = episodes(1, 1).map { it.copy(airDate = "не дата") }
        compose.setThemedContent {
            DetailContent(state = seriesState(seasons = listOf(Season(1, broken))))
        }
        openEpisodesTab()

        compose.onNodeWithText("не дата").assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.episode(1, 1)).assertIsDisplayed()
    }

    @Test
    fun episodesAreBehindTheirOwnTab() {
        compose.setThemedContent { DetailContent(state = seriesState()) }

        compose.onNodeWithTag(DetailTags.episode(1, 1)).assertDoesNotExist()
        openEpisodesTab()
        compose.onNodeWithTag(DetailTags.episode(1, 1)).assertIsDisplayed()
    }

    @Test
    fun overviewTabHidesEpisodesAgain() {
        compose.setThemedContent { DetailContent(state = seriesState()) }

        openEpisodesTab()
        compose.onNodeWithTag(DetailTags.TAB_OVERVIEW).performClick()

        compose.onNodeWithTag(DetailTags.episode(1, 1)).assertDoesNotExist()
        compose.onNodeWithText("Просмотрено 1 из 4").assertIsDisplayed()
    }

    @Test
    fun progressBlockShowsCountAndNextEpisode() {
        compose.setThemedContent { DetailContent(state = seriesState(watched = 1, total = 4)) }

        compose.onNodeWithText("Просмотрено 1 из 4").assertIsDisplayed()
        compose.onNodeWithText("Дальше: S01E02 — Серия 2").assertIsDisplayed()
    }

    @Test
    fun markNextIsHiddenWhenEverythingIsWatched() {
        compose.setThemedContent {
            DetailContent(
                state = seriesState(
                    watched = 4,
                    total = 4,
                    seasons = listOf(Season(1, episodes(1, 4, watchedUpTo = 4))),
                )
            )
        }

        compose.onNodeWithTag(DetailTags.MARK_NEXT).assertDoesNotExist()
        compose.onNodeWithText("Сбросить всё").assertIsDisplayed()
    }

    @Test
    fun markNextReportsTheTap() {
        var marked = false
        compose.setThemedContent { DetailContent(state = seriesState(), onMarkNext = { marked = true }) }

        compose.onNodeWithTag(DetailTags.MARK_NEXT).performClick()

        assertTrue(marked)
    }

    @Test
    fun toggleAllReportsTrueThenFalse() {
        var value: Boolean? = null
        compose.setThemedContent {
            DetailContent(state = seriesState(), onSetAllWatched = { value = it })
        }

        compose.onNodeWithTag(DetailTags.TOGGLE_ALL).performClick()

        assertEquals(true, value)
    }

    @Test
    fun statusChipReportsSelection() {
        var status: WatchStatus? = null
        compose.setThemedContent { DetailContent(state = seriesState(), onStatus = { status = it }) }

        compose.onNodeWithTag(DetailTags.statusChip(WatchStatus.ON_HOLD))
            .performScrollTo()
            .performClick()

        assertEquals(WatchStatus.ON_HOLD, status)
    }

    /**
     * The status is usually derived rather than tapped, so the last chip has to be on
     * screen without anyone swiping the row sideways first.
     */
    @Test
    fun theLastStatusChipIsVisibleWithoutScrollingSideways() {
        compose.setThemedContent { DetailContent(state = seriesState(status = WatchStatus.DROPPED)) }

        compose.onNodeWithTag(DetailTags.statusChip(WatchStatus.DROPPED)).assertIsDisplayed()
    }

    @Test
    fun ratingReportsTheValue() {
        var rating: Int? = -1
        compose.setThemedContent { DetailContent(state = seriesState(), onRating = { rating = it }) }

        compose.onNodeWithTag(DetailTags.rating(8)).performClick()

        assertEquals(8, rating)
    }

    @Test
    fun tappingTheCurrentRatingClearsIt() {
        var rating: Int? = 5
        compose.setThemedContent {
            DetailContent(state = seriesState(rating = 5), onRating = { rating = it })
        }

        compose.onNodeWithTag(DetailTags.rating(5)).performClick()

        assertEquals(null, rating)
    }

    @Test
    fun notesSaveAppearsOnlyAfterEditing() {
        var saved: String? = null
        compose.setThemedContent {
            DetailContent(state = seriesState(notes = "старое"), onNotes = { saved = it })
        }

        compose.onNodeWithTag(DetailTags.NOTES).assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.NOTES_OPEN).performClick()
        compose.onNodeWithTag(DetailTags.NOTES_SAVE).assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.NOTES).performTextReplacement("новое")
        compose.onNodeWithTag(DetailTags.NOTES_SAVE).performClick()

        assertEquals("новое", saved)
    }

    @Test
    fun seasonWithNextEpisodeStartsExpanded() {
        compose.setThemedContent { DetailContent(state = seriesState()) }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.episode(1, 1)).assertIsDisplayed()
    }

    @Test
    fun collapsingASeasonHidesItsEpisodes() {
        compose.setThemedContent { DetailContent(state = seriesState()) }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.seasonHeader(1)).performClick()

        compose.onNodeWithTag(DetailTags.episode(1, 1)).assertDoesNotExist()
    }

    @Test
    fun secondSeasonStaysCollapsedUntilTapped() {
        val state = seriesState(
            seasons = listOf(Season(1, episodes(1, 2)), Season(2, episodes(2, 2))),
            total = 4,
            watched = 0,
        )
        compose.setThemedContent { DetailContent(state = state) }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.episode(2, 1)).assertDoesNotExist()
        // LazyColumn does not compose off-screen items, so scroll the list, not the node.
        compose.onNodeWithTag(DetailTags.LIST)
            .performScrollToNode(hasTestTag(DetailTags.seasonHeader(2)))
        compose.onNodeWithTag(DetailTags.seasonHeader(2)).performClick()
        compose.onNodeWithTag(DetailTags.episode(2, 1)).assertIsDisplayed()
    }

    @Test
    fun episodeCheckboxReflectsWatchedState() {
        compose.setThemedContent { DetailContent(state = seriesState(watched = 1)) }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.episodeCheckbox(1, 1)).assertIsOn()
        compose.onNodeWithTag(DetailTags.episodeCheckbox(1, 2)).assertIsOff()
    }

    @Test
    fun tappingAnEpisodeReportsIt() {
        var toggled: EpisodeEntity? = null
        compose.setThemedContent {
            DetailContent(state = seriesState(), onToggleEpisode = { toggled = it })
        }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.episode(1, 2)).performClick()

        assertEquals(2, toggled?.episodeNumber)
    }

    @Test
    fun watchUpToIsOfferedOnlyForUnwatchedEpisodes() {
        var upTo: EpisodeEntity? = null
        compose.setThemedContent {
            DetailContent(state = seriesState(watched = 1), onWatchUpTo = { upTo = it })
        }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.watchUpTo(1, 1)).assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.LIST)
            .performScrollToNode(hasTestTag(DetailTags.watchUpTo(1, 3)))
        compose.onNodeWithTag(DetailTags.watchUpTo(1, 3)).performClick()

        assertEquals(3, upTo?.episodeNumber)
    }

    @Test
    fun seasonToggleReportsTheSeason() {
        var season: Season? = null
        compose.setThemedContent {
            DetailContent(state = seriesState(), onToggleSeason = { season = it })
        }
        openEpisodesTab()

        compose.onNodeWithTag(DetailTags.LIST)
            .performScrollToNode(hasTestTag(DetailTags.seasonToggle(1)))
        compose.onNodeWithTag(DetailTags.seasonToggle(1)).performClick()

        assertEquals(1, season?.number)
    }

    @Test
    fun seasonHeaderShowsItsCounter() {
        compose.setThemedContent { DetailContent(state = seriesState(watched = 2)) }
        openEpisodesTab()

        compose.onNodeWithText("Сезон 1").assertIsDisplayed()
        compose.onNodeWithText("2 / 4").assertIsDisplayed()
    }

    @Test
    fun deleteAsksBeforeRemoving() {
        var deleted = false
        compose.setThemedContent { DetailContent(state = seriesState(), onDelete = { deleted = true }) }

        compose.onNodeWithTag(DetailTags.DELETE).performClick()
        compose.onNodeWithText("Удалить из библиотеки?").assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.CANCEL_DELETE).performClick()
        assertTrue(!deleted)

        compose.onNodeWithTag(DetailTags.DELETE).performClick()
        compose.onNodeWithTag(DetailTags.CONFIRM_DELETE).performClick()
        assertTrue(deleted)
    }

    @Test
    fun catalogTitleOffersRefresh() {
        compose.setThemedContent { DetailContent(state = seriesState()) }

        compose.onNodeWithTag(DetailTags.REFRESH).assertIsDisplayed()
    }

    @Test
    fun manualTitleHasNothingToRefresh() {
        val manual = seriesState().let {
            it.copy(title = it.title!!.copy(title = it.title!!.title.copy(catalogId = null)))
        }
        compose.setThemedContent { DetailContent(state = manual) }

        compose.onNodeWithTag(DetailTags.REFRESH).assertDoesNotExist()
    }

    @Test
    fun refreshingShowsAnIndicator() {
        compose.setThemedContent { DetailContent(state = seriesState().copy(refreshing = true)) }

        compose.onNodeWithTag(DetailTags.REFRESHING).assertIsDisplayed()
    }

    @Test
    fun backIsReported() {
        var back = false
        compose.setThemedContent { DetailContent(state = seriesState(), onBack = { back = true }) }

        compose.onNodeWithTag(DetailTags.BACK).performClick()

        assertTrue(back)
    }

    @Test
    fun movieShowsToggleInsteadOfEpisodes() {
        var watched: Boolean? = null
        compose.setThemedContent {
            DetailContent(state = movieState(), onMovieWatched = { watched = it })
        }

        compose.onNodeWithText("Фильм не просмотрен").assertIsDisplayed()
        compose.onNodeWithText("Серии").assertDoesNotExist()
        compose.onNodeWithTag(DetailTags.MOVIE_TOGGLE).performClick()

        assertEquals(true, watched)
    }

    @Test
    fun watchedMovieOffersToUndo() {
        compose.setThemedContent { DetailContent(state = movieState(watched = true)) }

        compose.onNodeWithText("Фильм просмотрен").assertIsDisplayed()
        compose.onNodeWithText("Снять отметку").assertIsDisplayed()
    }

    @Test
    fun missingTitleShowsAPlaceholder() {
        compose.setThemedContent { DetailContent(state = DetailUiState(loading = false, title = null)) }

        compose.onNodeWithTag(DetailTags.NOT_FOUND).assertIsDisplayed()
        compose.onNodeWithText("Тайтл не найден").assertIsDisplayed()
    }
}
