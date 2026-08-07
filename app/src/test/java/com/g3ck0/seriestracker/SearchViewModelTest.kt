package com.g3ck0.seriestracker

import app.cash.turbine.test
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.fake.FakeCatalogApi
import com.g3ck0.seriestracker.fake.FakeTelemetry
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.MainDispatcherRule
import com.g3ck0.seriestracker.fake.awaitUntil
import com.g3ck0.seriestracker.fake.movieResult
import com.g3ck0.seriestracker.fake.seasonOf
import com.g3ck0.seriestracker.fake.tvDetailsOf
import com.g3ck0.seriestracker.fake.tvResult
import com.g3ck0.seriestracker.fake.tvTitle
import com.g3ck0.seriestracker.ui.search.SearchViewModel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTrackerDao()
    private val api = FakeCatalogApi()
    private val telemetry = FakeTelemetry()

    private fun viewModel(apiKey: String = "key") =
        SearchViewModel(TrackerRepository(dao, api, apiKey), telemetry)

    @Test
    fun `trending loads on open`() = runTest {
        api.trendingResults = listOf(tvResult(1, "Trending Show"))
        val vm = viewModel()

        vm.state.test {
            val loaded = awaitUntil { it.results.isNotEmpty() }
            assertTrue(loaded.showingTrending)
            assertEquals(listOf("Trending Show"), loaded.results.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without a key nothing is requested`() = runTest {
        val vm = viewModel(apiKey = "")
        advanceUntilIdle()

        vm.state.test {
            val state = awaitItem()
            assertFalse(state.hasBackend)
            assertTrue(state.results.isEmpty())
            assertEquals(0, api.searchCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing is debounced into a single request`() = runTest {
        api.searchResults = listOf(tvResult(1, "Dark"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange("d")
        advanceTimeBy(100)
        vm.onQueryChange("da")
        advanceTimeBy(100)
        vm.onQueryChange("dark")
        advanceUntilIdle()

        assertEquals(1, api.searchCalls)
        assertEquals("dark", api.lastQuery)
    }

    @Test
    fun `results replace trending`() = runTest {
        api.trendingResults = listOf(tvResult(9, "Trending"))
        api.searchResults = listOf(tvResult(1, "Dark"), movieResult(2, "Darkman"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            vm.onQueryChange("dark")
            val searched = awaitUntil { !it.showingTrending && it.results.isNotEmpty() }
            assertEquals(listOf("Dark", "Darkman"), searched.results.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the query brings trending back`() = runTest {
        api.trendingResults = listOf(tvResult(9, "Trending"))
        api.searchResults = listOf(tvResult(1, "Dark"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChange("dark")
        advanceUntilIdle()

        vm.state.test {
            vm.onQueryChange("")
            val back = awaitUntil { it.showingTrending }
            assertEquals(listOf("Trending"), back.results.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network failure is reported and clears loading`() = runTest {
        api.failure = IOException("no route to host")
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            val failed = awaitUntil { it.error != null }
            assertEquals("Нет соединения с интернетом", failed.error?.title)
            assertEquals("Проверь сеть и попробуй ещё раз", failed.error?.body)
            assertFalse(failed.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchNow skips the debounce`() = runTest {
        api.searchResults = listOf(tvResult(1, "Dark"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange("dark")
        vm.searchNow()
        advanceUntilIdle()

        assertEquals("dark", api.lastQuery)
        assertTrue(api.searchCalls >= 1)
    }

    @Test
    fun `adding a result stores it and reports success`() = runTest {
        api.searchResults = listOf(tvResult(7, "Show"))
        api.tvDetails = tvDetailsOf(7, "Show", mapOf(1 to 2))
        api.seasons = mapOf(1 to seasonOf(1, 2))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChange("show")
        advanceUntilIdle()

        val item = vm.state.value.results.first()
        vm.state.test {
            vm.add(item)
            val added = awaitUntil { it.message != null }
            assertEquals("«Show» добавлен", added.message)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(MediaType.TV, dao.getTitle("tv_7")!!.mediaType)
        assertEquals(2, dao.getEpisodes("tv_7").size)
        assertEquals(WatchStatus.PLANNED, dao.getTitle("tv_7")!!.status)
    }

    @Test
    fun `tracked ids reflect the library`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1"))
        val vm = viewModel()

        vm.trackedIds.test {
            assertEquals(setOf("tv_1"), awaitUntil { it.isNotEmpty() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual entry lands in the library`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.test {
            vm.addManual("Manual", MediaType.TV, listOf(2, 2), 30, "2020")
            val added = awaitUntil { it.message != null }
            assertEquals("«Manual» добавлен вручную", added.message)
            cancelAndIgnoreRemainingEvents()
        }
        val stored = dao.titles().single()
        assertTrue(stored.id.startsWith("local_"))
        assertEquals(4, dao.getEpisodes(stored.id).size)
        assertEquals(WatchStatus.PLANNED, stored.status)
    }

    @Test
    fun `message is consumed once`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addManual("Manual", MediaType.MOVIE, emptyList(), 100, null)
        advanceUntilIdle()

        vm.consumeMessage()

        assertEquals(null, vm.state.value.message)
    }
}
