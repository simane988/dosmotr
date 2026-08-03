package com.g3ck0.seriestracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real SQL. The JVM tests use a hand-written fake DAO, so these are what
 * prove the queries themselves — cascades, conflict strategies and sort order included.
 */
@RunWith(AndroidJUnit4::class)
class TrackerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrackerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.trackerDao()
    }

    @After
    fun tearDown() = db.close()

    private fun title(
        id: String = "tv_1",
        type: MediaType = MediaType.TV,
        status: WatchStatus = WatchStatus.WATCHING,
        addedAt: Long = 1,
        lastWatchedAt: Long? = null,
        runtime: Int = 40,
        movieWatched: Boolean = false,
    ) = TitleEntity(
        id = id,
        catalogId = 1,
        mediaType = type,
        name = id,
        status = status,
        addedAt = addedAt,
        lastWatchedAt = lastWatchedAt,
        runtimeMinutes = runtime,
        movieWatched = movieWatched,
    )

    private fun episodes(titleId: String, season: Int, count: Int, runtime: Int = 0) =
        (1..count).map {
            EpisodeEntity(titleId, season, it, name = "S${season}E$it", runtimeMinutes = runtime)
        }

    @Test
    fun progressCountersComeFromSubqueries() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 4))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 10)

        val row = dao.observeLibrary().first().single()

        assertEquals(4, row.episodeCount)
        assertEquals(1, row.watchedCount)
    }

    @Test
    fun nextUnwatchedColumnsFollowAiringOrderNotInsertionOrder() = runTest {
        dao.upsertTitle(title())
        // Season 2 inserted first on purpose: only the ORDER BY can put S01E02 ahead.
        dao.insertEpisodes(episodes("tv_1", 2, 2))
        dao.insertEpisodes(episodes("tv_1", 1, 3))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 10)

        val row = dao.observeLibrary().first().single()

        assertEquals(1, row.nextSeason)
        assertEquals(2, row.nextEpisode)
        assertEquals("S1E2", row.nextName)
    }

    @Test
    fun nextUnwatchedColumnsAreNullOnceEverythingIsWatched() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 2))
        dao.setAllWatched("tv_1", watched = true, watchedAt = 10)

        val row = dao.observeLibrary().first().single()

        assertNull(row.nextSeason)
        assertNull(row.nextEpisode)
        assertNull(row.nextName)
    }

    /** Movies carry no episode rows at all, so the subqueries have nothing to find. */
    @Test
    fun nextUnwatchedColumnsAreNullForAMovie() = runTest {
        dao.upsertTitle(title(id = "movie_1", type = MediaType.MOVIE))

        val row = dao.observeLibrary().first().single()

        assertNull(row.nextSeason)
        assertNull(row.nextEpisode)
        assertNull(row.nextName)
    }

    @Test
    fun libraryPutsRecentlyWatchedFirstThenNewlyAdded() = runTest {
        dao.upsertTitle(title(id = "old", addedAt = 1))
        dao.upsertTitle(title(id = "new", addedAt = 5))
        dao.upsertTitle(title(id = "watched", addedAt = 2, lastWatchedAt = 100))

        val order = dao.observeLibrary().first().map { it.title.id }

        assertEquals(listOf("watched", "new", "old"), order)
    }

    @Test
    fun libraryPutsActiveTitlesFirst() = runTest {
        // Deliberately seeded so that sorting by lastWatchedAt alone would invert this.
        dao.upsertTitle(title(id = "dropped", status = WatchStatus.DROPPED, lastWatchedAt = 500))
        dao.upsertTitle(title(id = "completed", status = WatchStatus.COMPLETED, lastWatchedAt = 400))
        dao.upsertTitle(title(id = "onHold", status = WatchStatus.ON_HOLD, lastWatchedAt = 300))
        dao.upsertTitle(title(id = "planned", status = WatchStatus.PLANNED, lastWatchedAt = 200))
        dao.upsertTitle(title(id = "watching", status = WatchStatus.WATCHING, lastWatchedAt = 100))

        val order = dao.observeLibrary().first().map { it.title.id }

        assertEquals(listOf("watching", "planned", "onHold", "completed", "dropped"), order)
    }

    @Test
    fun sqlOrderMatchesTheEnumOrder() = runTest {
        // Guards the duplicated CASE in the query against the enum drifting away.
        WatchStatus.entries.forEach { status ->
            dao.upsertTitle(title(id = status.name, status = status, addedAt = 1))
        }

        val order = dao.observeLibrary().first().map { it.title.status }

        assertEquals(WatchStatus.entries.sortedBy { it.libraryOrder }, order)
    }

    @Test
    fun finishingASeriesMovesItBelowActiveOnes() = runTest {
        dao.upsertTitle(title(id = "active", status = WatchStatus.WATCHING, lastWatchedAt = 1))
        dao.upsertTitle(title(id = "justFinished", status = WatchStatus.WATCHING, lastWatchedAt = 2))
        assertEquals("justFinished", dao.observeLibrary().first().first().title.id)

        dao.setStatus("justFinished", WatchStatus.COMPLETED)

        assertEquals("active", dao.observeLibrary().first().first().title.id)
    }

    @Test
    fun watchedOrderStillAppliesWithinAStatus() = runTest {
        dao.upsertTitle(title(id = "a", status = WatchStatus.WATCHING, lastWatchedAt = 10))
        dao.upsertTitle(title(id = "b", status = WatchStatus.WATCHING, lastWatchedAt = 50))
        dao.upsertTitle(title(id = "never", status = WatchStatus.WATCHING, addedAt = 99))

        val order = dao.observeLibrary().first().map { it.title.id }

        assertEquals(listOf("b", "a", "never"), order)
    }

    @Test
    fun deletingATitleCascadesToEpisodes() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 3))

        dao.deleteTitle("tv_1")

        assertNull(dao.getTitle("tv_1"))
        assertTrue(dao.allEpisodes().isEmpty())
    }

    @Test
    fun upsertingATitleKeepsItsEpisodes() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 3))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 5)

        // This is what INSERT OR REPLACE used to break: the row is rewritten wholesale.
        dao.upsertTitle(title().copy(name = "Renamed"))

        assertEquals("Renamed", dao.getTitle("tv_1")!!.name)
        assertEquals(3, dao.getEpisodes("tv_1").size)
        assertTrue(dao.getEpisodes("tv_1").first().watched)
    }

    @Test
    fun insertIgnoresEpisodesThatAlreadyExist() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 2))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 5)

        // A refresh re-sends season 1 and adds season 2.
        dao.insertEpisodes(episodes("tv_1", 1, 3) + episodes("tv_1", 2, 1))

        val all = dao.getEpisodes("tv_1")
        assertEquals(4, all.size)
        assertTrue(all.first { it.seasonNumber == 1 && it.episodeNumber == 1 }.watched)
    }

    @Test
    fun upsertEpisodesOverwrites() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 1))

        dao.upsertEpisodes(
            listOf(EpisodeEntity("tv_1", 1, 1, name = "Imported", watched = true, watchedAt = 7))
        )

        val episode = dao.getEpisodes("tv_1").single()
        assertEquals("Imported", episode.name)
        assertTrue(episode.watched)
    }

    @Test
    fun nextUnwatchedFollowsAiringOrder() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 2, 2) + episodes("tv_1", 1, 2))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 1)

        val next = dao.nextUnwatched("tv_1")!!

        assertEquals(1, next.seasonNumber)
        assertEquals(2, next.episodeNumber)
    }

    @Test
    fun watchUpToSpansEarlierSeasons() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 3) + episodes("tv_1", 2, 3))

        dao.watchUpTo("tv_1", season = 2, episode = 2, watchedAt = 9)

        val watched = dao.getEpisodes("tv_1").filter { it.watched }
        assertEquals(5, watched.size)
        assertFalse(dao.getEpisodes("tv_1").first { it.seasonNumber == 2 && it.episodeNumber == 3 }.watched)
    }

    @Test
    fun seasonUpdateLeavesOtherSeasonsAlone() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 2) + episodes("tv_1", 2, 2))

        dao.setSeasonWatched("tv_1", season = 1, watched = true, watchedAt = 3)

        assertEquals(2, dao.getEpisodes("tv_1").count { it.watched })
        assertEquals(2, dao.unwatchedCount("tv_1"))
    }

    @Test
    fun statsFallBackToTitleRuntimeWhenEpisodeHasNone() = runTest {
        dao.upsertTitle(title(runtime = 45))
        dao.insertEpisodes(episodes("tv_1", 1, 2, runtime = 0))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 1)
        dao.setEpisodeWatched("tv_1", 1, 2, watched = true, watchedAt = 2)

        assertEquals(2, dao.observeWatchedEpisodeCount().first())
        assertEquals(90, dao.observeWatchedEpisodeMinutes().first())
    }

    @Test
    fun statsPreferEpisodeRuntime() = runTest {
        dao.upsertTitle(title(runtime = 45))
        dao.insertEpisodes(episodes("tv_1", 1, 1, runtime = 62))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 1)

        assertEquals(62, dao.observeWatchedEpisodeMinutes().first())
    }

    @Test
    fun movieStatsCountOnlyWatchedMovies() = runTest {
        dao.upsertTitle(title(id = "movie_1", type = MediaType.MOVIE, runtime = 120, movieWatched = true))
        dao.upsertTitle(title(id = "movie_2", type = MediaType.MOVIE, runtime = 90))

        assertEquals(1, dao.observeWatchedMovieCount().first())
        assertEquals(120, dao.observeWatchedMovieMinutes().first())
        assertEquals(2, dao.observeCountByType(MediaType.MOVIE).first())
        assertEquals(0, dao.observeCountByType(MediaType.TV).first())
    }

    @Test
    fun statusCountsGroupCorrectly() = runTest {
        dao.upsertTitle(title(id = "a", status = WatchStatus.WATCHING))
        dao.upsertTitle(title(id = "b", status = WatchStatus.WATCHING))
        dao.upsertTitle(title(id = "c", status = WatchStatus.DROPPED))

        val counts = dao.observeStatusCounts().first().associate { it.status to it.count }

        assertEquals(2, counts[WatchStatus.WATCHING])
        assertEquals(1, counts[WatchStatus.DROPPED])
    }

    @Test
    fun remainingSkipsWatchedEpisodesAndTitlesNotBeingWatched() = runTest {
        dao.upsertTitle(title(id = "tv_1", runtime = 45))
        dao.insertEpisodes(episodes("tv_1", 1, 3))
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 1)
        // Same three episodes, but the title is only planned: it is not counted at all.
        dao.upsertTitle(title(id = "tv_2", status = WatchStatus.PLANNED, runtime = 20))
        dao.insertEpisodes(episodes("tv_2", 1, 3))

        val remaining = dao.observeRemaining().first()

        assertEquals(2, remaining.episodes)
        assertEquals(90, remaining.minutes)
    }

    @Test
    fun remainingPrefersTheEpisodeRuntime() = runTest {
        dao.upsertTitle(title(runtime = 45))
        dao.insertEpisodes(episodes("tv_1", 1, 1, runtime = 62))

        assertEquals(62, dao.observeRemaining().first().minutes)
    }

    @Test
    fun enumsSurviveTheRoundTrip() = runTest {
        dao.upsertTitle(title(id = "movie_1", type = MediaType.MOVIE, status = WatchStatus.ON_HOLD))

        val stored = dao.getTitle("movie_1")!!

        assertEquals(MediaType.MOVIE, stored.mediaType)
        assertEquals(WatchStatus.ON_HOLD, stored.status)
    }

    @Test
    fun deleteAllClearsBothTables() = runTest {
        dao.upsertTitle(title())
        dao.insertEpisodes(episodes("tv_1", 1, 2))

        dao.deleteAllTitles()

        assertTrue(dao.allTitles().isEmpty())
        assertTrue(dao.allEpisodes().isEmpty())
    }
}
