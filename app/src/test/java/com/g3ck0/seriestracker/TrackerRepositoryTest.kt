package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.repository.TrackerRepository
import com.g3ck0.seriestracker.data.repository.titleIdOf
import com.g3ck0.seriestracker.fake.FakeCatalogApi
import com.g3ck0.seriestracker.fake.FakeTrackerDao
import com.g3ck0.seriestracker.fake.episodesFor
import com.g3ck0.seriestracker.fake.movieResult
import com.g3ck0.seriestracker.fake.movieTitle
import com.g3ck0.seriestracker.fake.personResult
import com.g3ck0.seriestracker.fake.seasonOf
import com.g3ck0.seriestracker.fake.tvDetailsOf
import com.g3ck0.seriestracker.fake.tvResult
import com.g3ck0.seriestracker.fake.tvTitle
import com.g3ck0.seriestracker.data.remote.MovieDetailsDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TrackerRepositoryTest {

    private val dao = FakeTrackerDao()
    private val api = FakeCatalogApi()

    private fun repository(apiKey: String = "key") = TrackerRepository(dao, api, apiKey)

    // --- api key gating ---

    @Test
    fun `no api key disables search`() = runTest {
        val repo = repository(apiKey = "")

        assertFalse(repo.hasBackend)
        assertTrue(repo.search("anything").isFailure)
        assertTrue(repo.trending().isFailure)
    }

    @Test
    fun `search maps tv and movies and drops people`() = runTest {
        api.searchResults = listOf(tvResult(1, "Series"), personResult(9), movieResult(2, "Film"))

        val results = repository().search("q").getOrThrow()

        assertEquals(listOf("Series", "Film"), results.map { it.name })
        assertEquals(listOf(MediaType.TV, MediaType.MOVIE), results.map { it.mediaType })
        assertEquals(listOf("tv_1", "movie_2"), results.map { it.id })
        assertEquals("q", api.lastQuery)
    }

    @Test
    fun `search extracts year from either date field`() = runTest {
        api.searchResults = listOf(tvResult(1, "Series", year = "2014"), movieResult(2, "Film", year = "1999"))

        val results = repository().search("q").getOrThrow()

        assertEquals(listOf("2014", "1999"), results.map { it.year })
    }

    @Test
    fun `network error surfaces as failure`() = runTest {
        api.failure = IOException("offline")

        val result = repository().search("q")

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }

    // --- adding ---

    @Test
    fun `add stores title even when details fetch fails`() = runTest {
        api.failure = IOException("offline")
        val item = com.g3ck0.seriestracker.data.repository.SearchItem(
            catalogId = 42,
            mediaType = MediaType.TV,
            name = "Offline Series",
            overview = "o",
            posterPath = "/p.jpg",
            backdropPath = null,
            year = "2021",
            voteAverage = 7.0,
        )

        val id = repository().add(item).getOrThrow()

        assertEquals("tv_42", id)
        val stored = dao.getTitle(id)
        assertNotNull(stored)
        assertEquals("Offline Series", stored!!.name)
        // Not loaded, so the detail screen knows to retry later.
        assertFalse(stored.episodesLoaded)
    }

    @Test
    fun `add pulls seasons and skips specials`() = runTest {
        api.tvDetails = tvDetailsOf(7, "Show", seasonSizes = mapOf(0 to 3, 1 to 2, 2 to 2))
        api.seasons = mapOf(1 to seasonOf(1, 2), 2 to seasonOf(2, 2))
        val item = com.g3ck0.seriestracker.data.repository.SearchItem(
            catalogId = 7, mediaType = MediaType.TV, name = "Show", overview = "",
            posterPath = null, backdropPath = null, year = null, voteAverage = null,
        )

        repository().add(item).getOrThrow()

        assertEquals(listOf(1, 2), api.requestedSeasons)
        assertEquals(4, dao.episodes().size)
        assertTrue(dao.episodes().none { it.seasonNumber == 0 })
        assertTrue(dao.getTitle("tv_7")!!.episodesLoaded)
    }

    @Test
    fun `refresh keeps watched flags and adds newly aired episodes`() = runTest {
        val id = titleIdOf(MediaType.TV, 7)
        dao.seedTitle(tvTitle(id = id, catalogId = 7, episodesLoaded = true))
        dao.seedEpisodes(episodesFor(id, mapOf(1 to 2)))
        repository().setEpisodeWatched(id, 1, 1, watched = true)

        api.tvDetails = tvDetailsOf(7, "Show", seasonSizes = mapOf(1 to 4))
        api.seasons = mapOf(1 to seasonOf(1, 4))
        repository().refreshFromBackend(id).getOrThrow()

        val episodes = dao.getEpisodes(id)
        assertEquals(4, episodes.size)
        assertTrue(episodes.first { it.episodeNumber == 1 }.watched)
        assertFalse(episodes.first { it.episodeNumber == 3 }.watched)
    }

    @Test
    fun `refresh of a movie fills runtime`() = runTest {
        dao.seedTitle(movieTitle(id = "movie_5", catalogId = 5, runtimeMinutes = 0))
        api.movieDetails = MovieDetailsDto(
            id = 5, title = "Film", overview = "o", releaseDate = "1999-01-01", runtime = 139,
        )

        repository().refreshFromBackend("movie_5").getOrThrow()

        val stored = dao.getTitle("movie_5")!!
        assertEquals(139, stored.runtimeMinutes)
        assertEquals("1999", stored.year)
    }

    @Test
    fun `manual tv entry creates one row per episode`() = runTest {
        val id = repository().addManual(
            name = "Manual",
            mediaType = MediaType.TV,
            episodesPerSeason = listOf(3, 2),
            runtimeMinutes = 30,
            year = "2019",
        )

        val episodes = dao.getEpisodes(id)
        assertEquals(5, episodes.size)
        assertEquals(listOf(1, 1, 1, 2, 2), episodes.map { it.seasonNumber })
        assertEquals(listOf(1, 2, 3, 1, 2), episodes.map { it.episodeNumber })
        assertTrue(episodes.all { it.runtimeMinutes == 30 })
        assertTrue(dao.getTitle(id)!!.episodesLoaded)
    }

    @Test
    fun `manual movie creates no episodes`() = runTest {
        val id = repository().addManual("Film", MediaType.MOVIE, emptyList(), 100, "2001")

        assertTrue(dao.getEpisodes(id).isEmpty())
        assertEquals(MediaType.MOVIE, dao.getTitle(id)!!.mediaType)
    }

    // --- progress and status ---

    @Test
    fun `watching the last episode completes the title`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 2)))

        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)
        assertEquals(WatchStatus.WATCHING, dao.getTitle("tv_1")!!.status)

        repo.setEpisodeWatched("tv_1", 1, 2, watched = true)
        assertEquals(WatchStatus.COMPLETED, dao.getTitle("tv_1")!!.status)
    }

    @Test
    fun `unwatching pulls a completed title back to watching`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1", status = WatchStatus.COMPLETED))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 2)))
        repo.setAllWatched("tv_1", watched = true)

        repo.setEpisodeWatched("tv_1", 1, 2, watched = false)

        assertEquals(WatchStatus.WATCHING, dao.getTitle("tv_1")!!.status)
    }

    @Test
    fun `planned title starts watching on first episode`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1", status = WatchStatus.PLANNED))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 3)))

        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)

        assertEquals(WatchStatus.WATCHING, dao.getTitle("tv_1")!!.status)
    }

    @Test
    fun `dropped title is left alone while episodes remain`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1", status = WatchStatus.DROPPED))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 3)))

        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)

        assertEquals(WatchStatus.DROPPED, dao.getTitle("tv_1")!!.status)
    }

    @Test
    fun `markNext walks episodes in airing order and stops at the end`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 2, 2 to 1)))

        assertEquals(1 to 1, repo.markNextWatched("tv_1")!!.let { it.seasonNumber to it.episodeNumber })
        assertEquals(1 to 2, repo.markNextWatched("tv_1")!!.let { it.seasonNumber to it.episodeNumber })
        assertEquals(2 to 1, repo.markNextWatched("tv_1")!!.let { it.seasonNumber to it.episodeNumber })
        assertNull(repo.markNextWatched("tv_1"))
    }

    @Test
    fun `watchUpTo marks earlier seasons too and nothing after`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 3, 2 to 3)))

        repo.watchUpTo("tv_1", season = 2, episode = 2)

        val watched = dao.getEpisodes("tv_1").filter { it.watched }
            .map { it.seasonNumber to it.episodeNumber }
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3, 2 to 1, 2 to 2), watched)
    }

    @Test
    fun `season toggle only touches that season`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 2, 2 to 2)))

        repo.setSeasonWatched("tv_1", season = 1, watched = true)

        assertTrue(dao.getEpisodes("tv_1").filter { it.seasonNumber == 1 }.all { it.watched })
        assertTrue(dao.getEpisodes("tv_1").filter { it.seasonNumber == 2 }.none { it.watched })
    }

    @Test
    fun `watching stamps lastWatchedAt and unwatching the last one clears status only`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1", lastWatchedAt = null))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 2)))

        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)

        assertNotNull(dao.getTitle("tv_1")!!.lastWatchedAt)
    }

    @Test
    fun `movie watched flips status both ways`() = runTest {
        val repo = repository()
        dao.seedTitle(movieTitle(id = "movie_1"))

        repo.setMovieWatched("movie_1", watched = true)
        dao.getTitle("movie_1")!!.let {
            assertTrue(it.movieWatched)
            assertEquals(WatchStatus.COMPLETED, it.status)
            assertNotNull(it.lastWatchedAt)
        }

        repo.setMovieWatched("movie_1", watched = false)
        dao.getTitle("movie_1")!!.let {
            assertFalse(it.movieWatched)
            assertEquals(WatchStatus.PLANNED, it.status)
            assertNull(it.lastWatchedAt)
        }
    }

    @Test
    fun `delete removes the title and its episodes`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 3)))

        repo.delete("tv_1")

        assertNull(dao.getTitle("tv_1"))
        assertTrue(dao.episodes().isEmpty())
    }

    @Test
    fun `rating and notes round trip`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))

        repo.setRating("tv_1", 9)
        repo.setNotes("tv_1", "с женой")

        assertEquals(9, dao.getTitle("tv_1")!!.userRating)
        assertEquals("с женой", dao.getTitle("tv_1")!!.notes)
    }

    // --- observation ---

    @Test
    fun `library exposes progress counters`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 4)))
        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)

        val row = repo.observeLibrary().first().single()

        assertEquals(4, row.episodeCount)
        assertEquals(1, row.watchedCount)
        assertEquals(0.25f, row.progress, 0.001f)
        assertEquals(3, row.remaining)
    }

    @Test
    fun `finished titles sink below the ones still being watched`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_active", status = WatchStatus.WATCHING, lastWatchedAt = 1))
        dao.seedEpisodes(episodesFor("tv_active", mapOf(1 to 2)))
        dao.seedTitle(tvTitle(id = "tv_finishing", status = WatchStatus.WATCHING, lastWatchedAt = 2))
        dao.seedEpisodes(episodesFor("tv_finishing", mapOf(1 to 1)))

        assertEquals("tv_finishing", repo.observeLibrary().first().first().title.id)

        // Watching the last episode completes it, which must not park it on top.
        repo.markNextWatched("tv_finishing")

        val order = repo.observeLibrary().first().map { it.title.id }
        assertEquals(listOf("tv_active", "tv_finishing"), order)
    }

    @Test
    fun `dropped titles sit at the very bottom`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_dropped", status = WatchStatus.DROPPED, lastWatchedAt = 900))
        dao.seedTitle(tvTitle(id = "tv_planned", status = WatchStatus.PLANNED))
        dao.seedTitle(tvTitle(id = "tv_watching", status = WatchStatus.WATCHING))

        val order = repo.observeLibrary().first().map { it.title.id }

        assertEquals(listOf("tv_watching", "tv_planned", "tv_dropped"), order)
    }

    @Test
    fun `tracked ids expose what is already in the library`() = runTest {
        dao.seedTitle(tvTitle(id = "tv_1"))
        dao.seedTitle(movieTitle(id = "movie_2"))

        assertEquals(setOf("tv_1", "movie_2"), repository().observeTrackedIds().first())
    }

    @Test
    fun `stats sum episode and movie minutes`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1", runtimeMinutes = 40))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 3)))
        dao.seedTitle(movieTitle(id = "movie_1", runtimeMinutes = 120))
        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)
        repo.setEpisodeWatched("tv_1", 1, 2, watched = true)
        repo.setMovieWatched("movie_1", watched = true)

        val stats = repo.observeStats().first()

        assertEquals(2, stats.watchedEpisodes)
        assertEquals(80, stats.episodeMinutes) // falls back to the title runtime
        assertEquals(1, stats.watchedMovies)
        assertEquals(120, stats.movieMinutes)
        assertEquals(200, stats.totalMinutes)
        assertEquals(1, stats.seriesCount)
        assertEquals(1, stats.movieCount)
    }

    @Test
    fun `episode runtime wins over the title average`() = runTest {
        val repo = repository()
        dao.seedTitle(tvTitle(id = "tv_1", runtimeMinutes = 40))
        dao.seedEpisodes(episodesFor("tv_1", mapOf(1 to 1), runtimeMinutes = 65))
        repo.setEpisodeWatched("tv_1", 1, 1, watched = true)

        assertEquals(65, repo.observeStats().first().episodeMinutes)
    }
}
