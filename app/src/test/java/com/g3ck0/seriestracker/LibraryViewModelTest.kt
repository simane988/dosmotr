package com.g3ck0.seriestracker

import app.cash.turbine.test
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.SearchItem
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.fake.FakeCatalogApi
import com.g3ck0.seriestracker.fake.FakeSettingsStore
import com.g3ck0.seriestracker.fake.FakeTelemetry
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.MainDispatcherRule
import com.g3ck0.seriestracker.fake.awaitUntil
import com.g3ck0.seriestracker.fake.episodesFor
import com.g3ck0.seriestracker.fake.movieResult
import com.g3ck0.seriestracker.fake.movieTitle
import com.g3ck0.seriestracker.fake.tvResult
import com.g3ck0.seriestracker.fake.tvTitle
import com.g3ck0.seriestracker.ui.library.LibraryViewModel
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTrackerDao()
    private val api = FakeCatalogApi()
    private val repository = TrackerRepository(dao, api, "key")
    private val settings = FakeSettingsStore()
    private val telemetry = FakeTelemetry()

    private fun viewModel() = LibraryViewModel(repository, settings, telemetry)

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
            assertEquals("Отмечено: S01E01", message?.text)
            assertNull(message?.action)
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
            assertEquals("Все серии уже отмечены", awaitUntil { it.message != null }.message?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting removes the row and names the title it took`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.delete("tv_1")
            val afterDelete = awaitUntil { it.message != null }
            assertEquals("Тайтл «Dark» удалён", afterDelete.message?.text)
            assertEquals("Отменить", afterDelete.message?.action)
            assertEquals(2, afterDelete.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo brings the title back with its watched episodes`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1", name = "Dark", status = WatchStatus.WATCHING))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 4)))
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.markNextWatched("tv_1")
            advanceUntilIdle()

            vm.delete("tv_1")
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().items.isEmpty())

            vm.undoDelete()
            advanceUntilIdle()
            val restored = expectMostRecentItem()
            assertEquals("tv_1", restored.items.single().title.id)
            assertEquals(4, restored.items.single().episodeCount)
            assertEquals(1, restored.items.single().watchedCount)
            assertEquals("Тайтл «Dark» восстановлен", restored.message?.text)
            assertNull(restored.message?.action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo does nothing when nothing was deleted`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            val loaded = awaitUntil { !it.loading }
            assertEquals(3, loaded.items.size)

            vm.undoDelete()
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second undo cannot restore the title twice`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.delete("tv_1")
            advanceUntilIdle()
            vm.undoDelete()
            advanceUntilIdle()
            assertEquals(3, expectMostRecentItem().items.size)

            vm.undoDelete()
            advanceUntilIdle()

            expectNoEvents()
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

    /** Three watching titles the DAO orders A, B, C — none of them watched yet. */
    private fun seedWatchingRows() {
        listOf("tv_1" to "A", "tv_2" to "B", "tv_3" to "C").forEachIndexed { index, (id, name) ->
            dao.seedTitle(
                tvTitle(
                    id = id,
                    name = name,
                    addedAt = 3_000L - index * 1_000L,
                    status = WatchStatus.WATCHING,
                )
            )
            dao.seedEpisodes(episodesFor(id, mapOf(1 to 4)))
        }
    }

    @Test
    fun `marking an episode leaves the row where it is`() = runTest {
        seedWatchingRows()
        val vm = viewModel()

        vm.state.test {
            assertEquals(listOf("A", "B", "C"), awaitUntil { !it.loading }.items.map { it.title.name })
            vm.markNextWatched("tv_3")
            // The DAO now sorts C first (lastWatchedAt DESC); the frozen order must not.
            val after = awaitUntil { it.message != null }
            assertEquals(listOf("A", "B", "C"), after.items.map { it.title.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshing the order applies the DAO sort again`() = runTest {
        seedWatchingRows()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.markNextWatched("tv_3")
            awaitUntil { it.message != null }
            vm.refreshOrder()
            val resorted = awaitUntil { it.items.first().title.name == "C" }
            assertEquals(listOf("C", "A", "B"), resorted.items.map { it.title.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a filter change applies the DAO sort again`() = runTest {
        seedWatchingRows()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.markNextWatched("tv_3")
            awaitUntil { it.message != null }
            vm.setStatusFilter(WatchStatus.WATCHING)
            val filtered = awaitUntil { it.filters.status == WatchStatus.WATCHING }
            assertEquals(listOf("C", "A", "B"), filtered.items.map { it.title.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a title added while the order is frozen keeps its place in it`() = runTest {
        seedWatchingRows()
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            // The DAO sorts this one between A and B; it must land there, not at the end.
            dao.seedTitle(
                tvTitle(id = "tv_4", name = "New", addedAt = 2_500, status = WatchStatus.WATCHING)
            )
            val grown = awaitUntil { it.items.size == 4 }
            assertEquals(listOf("A", "New", "B", "C"), grown.items.map { it.title.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** An empty library has nothing to notify about, so there is nothing to ask for yet. */
    @Test
    fun `notifications are offered once the library is not empty`() = runTest {
        val vm = viewModel()

        vm.state.test {
            assertFalse(awaitUntil { !it.loading }.askNotifications)
            seedLibrary()
            assertTrue(awaitUntil { it.items.isNotEmpty() }.askNotifications)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notifications are not offered again once the question was asked`() = runTest {
        seedLibrary()
        val vm = LibraryViewModel(repository, FakeSettingsStore(notificationsAsked = true), telemetry)

        vm.state.test {
            val loaded = awaitUntil { !it.loading }
            assertEquals(3, loaded.items.size)
            assertFalse(loaded.askNotifications)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Whatever the system dialog answered, the flag has to survive the next start. */
    @Test
    fun `answering the permission dialog is remembered`() = runTest {
        seedLibrary()
        val vm = viewModel()

        vm.state.test {
            assertTrue(awaitUntil { !it.loading }.askNotifications)
            vm.markNotificationsAsked(granted = true)
            awaitUntil { !it.askNotifications }
            assertTrue(settings.storedNotificationsAsked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- feature-15: what an empty library offers ---

    private fun suggestion(id: Int = 1399, name: String = "Game of Thrones") = SearchItem(
        catalogId = id,
        mediaType = MediaType.TV,
        name = name,
        overview = "",
        posterPath = "/p$id.jpg",
        backdropPath = null,
        year = "2011",
        voteAverage = 8.4,
    )

    @Test
    fun `an empty library is offered what is trending`() = runTest {
        api.trendingResults = listOf(tvResult(1, "Dark"), movieResult(2, "Fight Club"))
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.loadSuggestions()
            val offered = awaitUntil { it.suggestions.isNotEmpty() }
            assertEquals(listOf("Dark", "Fight Club"), offered.suggestions.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The whole point of loading lazily: every cold start with a library in it would
     * otherwise cost a request nobody sees the result of.
     */
    @Test
    fun `trending is never requested while the library has titles`() = runTest {
        seedLibrary()
        api.trendingResults = listOf(tvResult(1, "Dark"))
        val vm = viewModel()

        vm.loadSuggestions()
        advanceUntilIdle()

        assertEquals(0, api.trendingCalls)
    }

    @Test
    fun `trending is asked for once, however often the empty state appears`() = runTest {
        api.trendingResults = listOf(tvResult(1, "Dark"))
        val vm = viewModel()

        vm.loadSuggestions()
        advanceUntilIdle()
        vm.loadSuggestions()
        advanceUntilIdle()

        assertEquals(1, api.trendingCalls)
    }

    /** No backend configured: the row is not worth a message, and not worth a call either. */
    @Test
    fun `without a backend nothing is requested`() = runTest {
        val vm = LibraryViewModel(TrackerRepository(dao, api, ""), settings, telemetry)

        vm.loadSuggestions()
        advanceUntilIdle()

        assertEquals(0, api.trendingCalls)
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    /** A failed request costs the row, nothing else — and it may be retried later. */
    @Test
    fun `a failed trending request leaves the screen without suggestions`() = runTest {
        api.failure = IOException("offline")
        val vm = viewModel()

        vm.state.test {
            val loaded = awaitUntil { !it.loading }
            assertTrue(loaded.suggestions.isEmpty())
            assertNull(loaded.message)

            vm.loadSuggestions()
            advanceUntilIdle()
            // Nothing at all reaches the screen: no row, and no error line either.
            expectNoEvents()

            api.failure = null
            api.trendingResults = listOf(tvResult(1, "Dark"))
            vm.loadSuggestions()
            assertEquals(1, awaitUntil { it.suggestions.isNotEmpty() }.suggestions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The detail screen reads the database, so a suggestion has to be added before it can
     * be opened — the id only arrives once that worked.
     */
    @Test
    fun `tapping a suggestion adds the title and opens it`() = runTest {
        val vm = viewModel()

        vm.openTitle.test {
            vm.addSuggestion(suggestion())
            assertEquals("tv_1399", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Game of Thrones", dao.getTitle("tv_1399")?.name)
    }

    @Test
    fun `a manual entry made from the empty library is stored`() = runTest {
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.loading }
            vm.addManual("Мой сериал", MediaType.TV, listOf(3), runtimeMinutes = 30, year = "2024")
            assertEquals(
                "«Мой сериал» добавлен вручную",
                awaitUntil { it.message != null }.message?.text,
            )
            cancelAndIgnoreRemainingEvents()
        }

        val stored = dao.titles().single()
        assertTrue(stored.id.startsWith("local_"))
        assertEquals(3, dao.episodes().count { it.titleId == stored.id })
    }
}
