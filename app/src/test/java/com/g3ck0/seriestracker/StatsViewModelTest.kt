package com.g3ck0.seriestracker

import app.cash.turbine.test
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.fake.FakeTmdbApi
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.MainDispatcherRule
import com.g3ck0.seriestracker.fake.awaitUntil
import com.g3ck0.seriestracker.fake.episodesFor
import com.g3ck0.seriestracker.fake.movieTitle
import com.g3ck0.seriestracker.fake.tvTitle
import com.g3ck0.seriestracker.ui.stats.StatsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTrackerDao()
    private val repository = TrackerRepository(dao, FakeTmdbApi(), "key")

    @Test
    fun `empty library reports zeroes`() = runTest {
        StatsViewModel(repository).stats.test {
            val stats = awaitItem()
            assertEquals(0, stats.watchedEpisodes)
            assertEquals(0, stats.totalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals combine episodes and movies`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1", runtimeMinutes = 45, status = WatchStatus.WATCHING))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 4)))
        dao.seedTitle(movieTitle(id = "movie_1", runtimeMinutes = 139))
        repository.setEpisodeWatched("tv_1", 1, 1, watched = true)
        repository.setEpisodeWatched("tv_1", 1, 2, watched = true)
        repository.setMovieWatched("movie_1", watched = true)

        StatsViewModel(repository).stats.test {
            val stats = awaitUntil { it.watchedEpisodes == 2 }
            assertEquals(90, stats.episodeMinutes)
            assertEquals(1, stats.watchedMovies)
            assertEquals(139, stats.movieMinutes)
            assertEquals(229, stats.totalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status breakdown counts every title once`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1", status = WatchStatus.WATCHING))
        dao.seedTitle(tvTitle(id = "tv_2", status = WatchStatus.WATCHING))
        dao.seedTitle(tvTitle(id = "tv_3", status = WatchStatus.DROPPED))

        StatsViewModel(repository).stats.test {
            val stats = awaitUntil { it.byStatus.isNotEmpty() }
            assertEquals(2, stats.byStatus[WatchStatus.WATCHING])
            assertEquals(1, stats.byStatus[WatchStatus.DROPPED])
            assertEquals(null, stats.byStatus[WatchStatus.COMPLETED])
            assertEquals(3, stats.seriesCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
