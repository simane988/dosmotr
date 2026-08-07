package com.g3ck0.seriestracker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.fake.FakeCatalogApi
import com.g3ck0.seriestracker.fake.FakeTelemetry
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.MainDispatcherRule
import com.g3ck0.seriestracker.fake.awaitUntil
import com.g3ck0.seriestracker.fake.episodesFor
import com.g3ck0.seriestracker.fake.movieTitle
import com.g3ck0.seriestracker.fake.seasonOf
import com.g3ck0.seriestracker.fake.tvDetailsOf
import com.g3ck0.seriestracker.fake.tvTitle
import com.g3ck0.seriestracker.ui.detail.DetailViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTrackerDao()
    private val api = FakeCatalogApi()
    private val repository = TrackerRepository(dao, api, "key")
    private val telemetry = FakeTelemetry()

    private fun viewModel(titleId: String = "tv_1") =
        DetailViewModel(repository, telemetry, SavedStateHandle(mapOf("titleId" to titleId)))

    private fun seedSeries(episodesLoaded: Boolean = true) {
        dao.seedTitle(tvTitle(id = "tv_1", episodesLoaded = episodesLoaded))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 3, 2 to 2)))
    }

    @Test
    fun `episodes are grouped into ordered seasons`() = runTest {
        seedSeries()

        viewModel().state.test {
            val loaded = awaitUntil { it.seasons.isNotEmpty() }
            assertEquals(listOf(1, 2), loaded.seasons.map { it.number })
            assertEquals(listOf(3, 2), loaded.seasons.map { it.episodes.size })
            assertEquals(listOf(1, 2, 3), loaded.seasons.first().episodes.map { it.episodeNumber })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `next episode is the first unwatched in airing order`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()
        repository.setEpisodeWatched("tv_1", 1, 1, watched = true)

        vm.state.test {
            val state = awaitUntil { it.nextEpisode?.episodeNumber == 2 }
            assertEquals(1, state.nextEpisode!!.seasonNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `next episode is null once everything is watched`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()

        vm.setAllWatched(true)
        advanceUntilIdle()

        vm.state.test {
            assertNull(awaitUntil { it.seasons.isNotEmpty() && it.nextEpisode == null }.nextEpisode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `season counters track watched episodes`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleSeason(
            com.g3ck0.seriestracker.ui.detail.Season(1, dao.getEpisodes("tv_1").filter { it.seasonNumber == 1 })
        )
        advanceUntilIdle()

        vm.state.test {
            val state = awaitUntil { it.seasons.firstOrNull()?.allWatched == true }
            assertEquals(3, state.seasons.first().watchedCount)
            assertFalse(state.seasons[1].allWatched)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling an episode flips it back and forth`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()
        val episode = dao.getEpisodes("tv_1").first()

        vm.toggleEpisode(episode)
        advanceUntilIdle()
        assertTrue(dao.getEpisodes("tv_1").first().watched)

        vm.toggleEpisode(dao.getEpisodes("tv_1").first())
        advanceUntilIdle()
        assertFalse(dao.getEpisodes("tv_1").first().watched)
    }

    @Test
    fun `watchUpTo reports the episode it caught up to`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()
        val target = dao.getEpisodes("tv_1").first { it.seasonNumber == 2 && it.episodeNumber == 1 }

        vm.state.test {
            vm.watchUpTo(target)
            assertEquals("Отмечено всё до S02E01", awaitUntil { it.message != null }.message)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(4, dao.getEpisodes("tv_1").count { it.watched })
    }

    @Test
    fun `markNext reports the episode and stops at the end`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 1)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            vm.markNext()
            assertEquals("Отмечено: S01E01", awaitUntil { it.message != null }.message)
            vm.consumeMessage()
            awaitUntil { it.message == null }

            vm.markNext()
            assertEquals("Все серии уже отмечены", awaitUntil { it.message != null }.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status rating and notes go through`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()

        vm.setStatus(WatchStatus.ON_HOLD)
        vm.setRating(7)
        vm.setNotes("заметка")
        advanceUntilIdle()

        val stored = dao.getTitle("tv_1")!!
        assertEquals(WatchStatus.ON_HOLD, stored.status)
        assertEquals(7, stored.userRating)
        assertEquals("заметка", stored.notes)
    }

    @Test
    fun `deleting flags the screen so it can pop`() = runTest {
        seedSeries()
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            vm.delete()
            assertTrue(awaitUntil { it.deleted }.deleted)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(dao.getTitle("tv_1"))
    }

    @Test
    fun `a title opened without episodes refreshes itself`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1", catalogId = 7, episodesLoaded = false))
        api.tvDetails = tvDetailsOf(7, "Show", mapOf(1 to 3))
        api.seasons = mapOf(1 to seasonOf(1, 3))

        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.seasons.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(3, dao.getEpisodes("tv_1").size)
        assertTrue(dao.getTitle("tv_1")!!.episodesLoaded)
    }

    @Test
    fun `a manual title never hits the network`() = runTest {
        dao.seedTitle(tvTitle(id = "local_x", catalogId = null, episodesLoaded = false))
        api.failure = IOException("should not be called")

        val vm = viewModel(titleId = "local_x")
        advanceUntilIdle()

        assertNull(vm.state.value.message)
    }

    @Test
    fun `refresh failure surfaces a message`() = runTest {
        seedSeries()
        api.failure = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            vm.refresh()
            // Both land in the same emission — waiting for them separately would
            // block forever once the flow has settled.
            val failed = awaitUntil { it.message != null && !it.refreshing }
            assertEquals(
                "Нет соединения с интернетом. Проверь сеть и попробуй ещё раз",
                failed.message,
            )
            assertFalse(failed.refreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `movie detail exposes the watched flag`() = runTest {
        dao.seedTitle(movieTitle(id = "movie_1"))
        val vm = viewModel(titleId = "movie_1")
        advanceUntilIdle()

        vm.setMovieWatched(true)
        advanceUntilIdle()

        vm.state.test {
            val state = awaitUntil { it.title?.title?.movieWatched == true }
            assertTrue(state.title!!.isCompleted)
            assertTrue(state.seasons.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing title yields a null state instead of crashing`() = runTest {
        val vm = viewModel(titleId = "nope")
        advanceUntilIdle()

        vm.state.test {
            val state = awaitUntil { !it.loading }
            assertNull(state.title)
            assertTrue(state.seasons.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
