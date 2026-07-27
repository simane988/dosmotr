package com.g3ck0.seriestracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.g3ck0.seriestracker.data.backup.BackupRepository
import com.g3ck0.seriestracker.data.backup.BackupRepository.ImportMode
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrackerDao
    private lateinit var backup: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.trackerDao()
        backup = BackupRepository(context, db, dao)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedSeries(
        id: String = "tv_1",
        status: WatchStatus = WatchStatus.WATCHING,
        rating: Int? = 8,
        notes: String = "",
        watchedEpisodes: Int = 1,
    ) {
        dao.upsertTitle(
            TitleEntity(
                id = id,
                tmdbId = 1,
                mediaType = MediaType.TV,
                name = "Series $id",
                status = status,
                userRating = rating,
                notes = notes,
                runtimeMinutes = 40,
                episodesLoaded = true,
            )
        )
        dao.insertEpisodes((1..4).map { EpisodeEntity(id, 1, it, name = "S01E$it") })
        repeat(watchedEpisodes) { index ->
            dao.setEpisodeWatched(id, 1, index + 1, watched = true, watchedAt = 100L + index)
        }
    }

    @Test
    fun exportThenReplaceRestoresEverything() = runTest {
        seedSeries(rating = 9, notes = "с женой", watchedEpisodes = 2)
        val json = backup.exportToJson()
        dao.deleteAllTitles()

        val result = backup.importFromJson(json, ImportMode.REPLACE)

        assertEquals(1, result.titlesAdded)
        assertEquals(4, result.episodes)
        val restored = dao.getTitle("tv_1")!!
        assertEquals(9, restored.userRating)
        assertEquals("с женой", restored.notes)
        assertEquals(2, dao.getEpisodes("tv_1").count { it.watched })
    }

    @Test
    fun replaceWipesTitlesMissingFromTheFile() = runTest {
        seedSeries(id = "tv_1")
        val json = backup.exportToJson()
        seedSeries(id = "tv_2")

        backup.importFromJson(json, ImportMode.REPLACE)

        assertNotNull(dao.getTitle("tv_1"))
        assertNull(dao.getTitle("tv_2"))
    }

    @Test
    fun mergeKeepsTitlesMissingFromTheFile() = runTest {
        seedSeries(id = "tv_1")
        val json = backup.exportToJson()
        seedSeries(id = "tv_2")

        val result = backup.importFromJson(json, ImportMode.MERGE)

        assertEquals(0, result.titlesAdded)
        assertEquals(1, result.titlesUpdated)
        assertNotNull(dao.getTitle("tv_2"))
    }

    @Test
    fun mergeUnionsWatchedFlagsAndNeverUnwatches() = runTest {
        seedSeries(watchedEpisodes = 3)
        val json = backup.exportToJson()          // 1,2,3 watched
        dao.setAllWatched("tv_1", watched = false, watchedAt = null)
        dao.setEpisodeWatched("tv_1", 1, 4, watched = true, watchedAt = 500) // only 4 locally

        backup.importFromJson(json, ImportMode.MERGE)

        val watched = dao.getEpisodes("tv_1").filter { it.watched }.map { it.episodeNumber }
        assertEquals(listOf(1, 2, 3, 4), watched)
    }

    @Test
    fun mergeKeepsTheEarlierWatchDate() = runTest {
        seedSeries(watchedEpisodes = 0)
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 100)
        val json = backup.exportToJson()
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 900)

        backup.importFromJson(json, ImportMode.MERGE)

        assertEquals(100L, dao.getEpisodes("tv_1").first().watchedAt)
    }

    @Test
    fun mergeDoesNotOverwriteLocalRatingStatusOrNotes() = runTest {
        seedSeries(status = WatchStatus.WATCHING, rating = 3, notes = "из файла")
        val json = backup.exportToJson()
        dao.setStatus("tv_1", WatchStatus.DROPPED)
        dao.setRating("tv_1", 10)
        dao.setNotes("tv_1", "моя заметка")

        backup.importFromJson(json, ImportMode.MERGE)

        val stored = dao.getTitle("tv_1")!!
        assertEquals(WatchStatus.DROPPED, stored.status)
        assertEquals(10, stored.userRating)
        assertEquals("моя заметка", stored.notes)
    }

    @Test
    fun mergeFillsFieldsThatAreLocallyEmpty() = runTest {
        seedSeries(rating = 7, notes = "из файла")
        val json = backup.exportToJson()
        dao.setRating("tv_1", null)
        dao.setNotes("tv_1", "")

        backup.importFromJson(json, ImportMode.MERGE)

        val stored = dao.getTitle("tv_1")!!
        assertEquals(7, stored.userRating)
        assertEquals("из файла", stored.notes)
    }

    @Test
    fun importedFullyWatchedSeriesBecomesCompleted() = runTest {
        seedSeries(status = WatchStatus.WATCHING, watchedEpisodes = 4)
        val json = backup.exportToJson()
        dao.deleteAllTitles()

        backup.importFromJson(json, ImportMode.REPLACE)

        assertEquals(WatchStatus.COMPLETED, dao.getTitle("tv_1")!!.status)
    }

    @Test
    fun rowsWithoutIdOrNameAreSkippedAndCounted() = runTest {
        val json = """
            {"version":1,"titles":[
              {"id":"tv_9","type":"TV","name":"Good","episodes":[]},
              {"id":"","type":"TV","name":"No id","episodes":[]},
              {"id":"tv_8","type":"TV","name":"","episodes":[]}
            ]}
        """.trimIndent()

        val result = backup.importFromJson(json, ImportMode.MERGE)

        assertEquals(1, result.titlesAdded)
        assertEquals(2, result.skipped)
        assertEquals(1, dao.allTitles().size)
    }

    @Test
    fun unknownEnumsFallBackInsteadOfThrowing() = runTest {
        val json = """
            {"version":1,"titles":[{"id":"x","type":"HOLOGRAM","name":"N","status":"ЧТО-ТО","episodes":[]}]}
        """.trimIndent()

        backup.importFromJson(json, ImportMode.MERGE)

        val stored = dao.getTitle("x")!!
        assertEquals(MediaType.TV, stored.mediaType)
        assertEquals(WatchStatus.WATCHING, stored.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun newerFileVersionIsRejected() = runTest {
        backup.importFromJson(
            """{"version":99,"titles":[{"id":"x","type":"TV","name":"N","episodes":[]}]}""",
            ImportMode.MERGE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyFileIsRejected() = runTest {
        backup.importFromJson("""{"version":1,"titles":[]}""", ImportMode.MERGE)
    }

    @Test
    fun malformedJsonLeavesTheLibraryUntouched() = runTest {
        seedSeries()

        runCatching { backup.importFromJson("{ not json", ImportMode.REPLACE) }

        // REPLACE wipes inside the transaction; parsing fails before it opens.
        assertNotNull(dao.getTitle("tv_1"))
        assertEquals(4, dao.getEpisodes("tv_1").size)
    }

    @Test
    fun exportIncludesEveryTitleAndEpisode() = runTest {
        seedSeries(id = "tv_1")
        seedSeries(id = "tv_2")
        dao.upsertTitle(
            TitleEntity(
                id = "movie_1",
                tmdbId = 5,
                mediaType = MediaType.MOVIE,
                name = "Film",
                movieWatched = true,
                runtimeMinutes = 100,
            )
        )

        val json = backup.exportToJson()

        assertTrue(json.contains("tv_1"))
        assertTrue(json.contains("tv_2"))
        assertTrue(json.contains("movie_1"))
        dao.deleteAllTitles()
        val result = backup.importFromJson(json, ImportMode.REPLACE)
        assertEquals(3, result.titlesAdded)
        assertEquals(8, result.episodes)
        assertTrue(dao.getTitle("movie_1")!!.movieWatched)
        assertFalse(dao.getTitle("movie_1")!!.mediaType == MediaType.TV)
    }
}
