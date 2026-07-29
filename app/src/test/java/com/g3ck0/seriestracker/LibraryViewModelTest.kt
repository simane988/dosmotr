package com.g3ck0.seriestracker

import app.cash.turbine.test
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.fake.FakeCatalogApi
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.MainDispatcherRule
import com.g3ck0.seriestracker.fake.awaitUntil
import com.g3ck0.seriestracker.fake.episodesFor
import com.g3ck0.seriestracker.fake.movieTitle
import com.g3ck0.seriestracker.fake.tvTitle
import com.g3ck0.seriestracker.ui.library.LibraryViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTrackerDao()
    private val repository = TrackerRepository(dao, FakeCatalogApi(), "key")

    private fun viewModel() = LibraryViewModel(repository)

    private fun seedLibrary() {
        dao.seedTitle(tvTitle(id = "tv_1", name = "Dark", status = WatchStatus.WATCHING))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 4)))
        dao.seedTitle(tvTitle(id = "tv_2", name = "Fringe", status = WatchStatus.COMPLETED))
        dao.seedTitle(movieTitle(id = "movie_1", name = "Fight Club", status = WatchStatus.PLANNED))
    }

    @Test
    fun `library starts empty and loading`() = runTest {
        viewModel().state.test {
            val initial = awaitItem()
            assertTrue(initial.loading)
            assertTrue(initial.items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all titles are shown without filters`() = runTest {
        seedLibrary()

        viewModel().state.test {
            val loaded = awaitUntil { !it.loading }
            assertEquals(3, loaded.items.size)
            assertEquals(3, loaded.totalCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status filter narrows the list`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.setStatusFilter(WatchStatus.COMPLETED)
            val filtered = awaitUntil { it.filters.status == WatchStatus.COMPLETED }
            assertEquals(listOf("Fringe"), filtered.items.map { it.title.name })
            // The unfiltered size stays available for the empty-state copy.
            assertEquals(3, filtered.totalCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `media filter narrows the list`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.setMediaFilter(MediaType.MOVIE)
            val filtered = awaitUntil { it.filters.mediaType == MediaType.MOVIE }
            assertEquals(listOf("Fight Club"), filtered.items.map { it.title.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query filter is case insensitive and trims`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.setQuery("  dArK ")
            val filtered = awaitUntil { it.filters.query.isNotBlank() }
            assertEquals(listOf("Dark"), filtered.items.map { it.title.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters combine`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.setStatusFilter(WatchStatus.COMPLETED)
            vm.setMediaFilter(MediaType.MOVIE)
            val filtered = awaitUntil {
                it.filters.status == WatchStatus.COMPLETED && it.filters.mediaType == MediaType.MOVIE
            }
            assertTrue(filtered.items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking the next episode reports which one`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.markNextWatched("tv_1")
            val message = awaitUntil { it.message != null }.message
            assertEquals("Отмечено: S01E01", message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking past the end says so instead of failing`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1"))
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.markNextWatched("tv_1")
            assertEquals("Все серии уже отмечены", awaitUntil { it.message != null }.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting removes the row and reports it`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.delete("tv_1")
            val afterDelete = awaitUntil { it.message == "Удалено" }
            assertEquals(2, afterDelete.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consuming the message clears it`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1"))
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.delete("tv_1")
            awaitUntil { it.message != null }
            vm.consumeMessage()
            assertEquals(null, awaitUntil { it.message == null }.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status change is persisted`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.setStatus("tv_1", WatchStatus.DROPPED)
        advanceUntilIdle()

        assertEquals(WatchStatus.DROPPED, dao.getTitle("tv_1")!!.status)
    }

    @Test
    fun `movie toggle is persisted`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.toggleMovieWatched("movie_1", watched = true)
        advanceUntilIdle()

        assertTrue(dao.getTitle("movie_1")!!.movieWatched)
    }
}
