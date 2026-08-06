package com.g3ck0.seriestracker

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.SCHEMA_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

/**
 * The database is built without fallbackToDestructiveMigration(), so an upgrade has to
 * carry the user's library across instead of dropping it. These tests are what prove the
 * chain is intact: a version bump with no matching entry in [AppDatabase.MIGRATIONS], or
 * an uncommitted schema file, fails here rather than on someone's phone.
 *
 * When version 2 arrives it gets its own `migrate1To2`; [openingVersion1Database] keeps
 * covering the whole path from the first shipped version to the current one.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * The version every published build has had. It never changes: this is the oldest
     * database still out there, not the current schema version.
     */
    private val firstShippedVersion = 1

    // Same vararg API as provideDatabase — see the note there.
    @Suppress("SpreadOperator")
    @Test
    fun openingVersion1Database() = runTest {
        helper.createDatabase(TEST_DB, firstShippedVersion).use { db ->
            db.execSQL(
                """
                INSERT INTO titles (
                    id, tmdbId, mediaType, name, overview, posterPath, backdropPath, year,
                    status, userRating, tmdbRating, runtimeMinutes, addedAt, lastWatchedAt,
                    notes, movieWatched, episodesLoaded
                ) VALUES (
                    'tv_1399', 1399, 'TV', 'Игра престолов', '', NULL, NULL, '2011',
                    'WATCHING', NULL, 9.3, 60, 1000, 2000, 'заметка', 0, 1
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO episodes (
                    titleId, seasonNumber, episodeNumber, name, overview, airDate,
                    stillPath, runtimeMinutes, watched, watchedAt
                ) VALUES
                    ('tv_1399', 1, 1, 'Winter Is Coming', '', '2011-04-17', NULL, 62, 1, 3000),
                    ('tv_1399', 1, 2, 'The Kingsroad', '', '2011-04-24', NULL, 56, 0, NULL)
                """.trimIndent(),
            )
        }

        // Opening it the way the app does. Room validates the schema against the current
        // entities here, so a bumped version with no migration throws instead of passing.
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(*AppDatabase.MIGRATIONS).build()

        try {
            val dao = db.trackerDao()
            val row = dao.observeLibrary().first().single()
            assertEquals("tv_1399", row.title.id)
            assertEquals("Игра престолов", row.title.name)
            assertEquals("заметка", row.title.notes)
            assertEquals(1399, row.title.catalogId)
            // Progress is a COUNT(*) over the rows that survived, not a stored column.
            assertEquals(2, row.episodeCount)
            assertEquals(1, row.watchedCount)

            val episodes = dao.observeEpisodes("tv_1399").first()
            assertEquals(2, episodes.size)
            // The watched flag is the one thing a destructive fallback would have taken.
            assertEquals(listOf(true, false), episodes.map { it.watched })
        } finally {
            db.close()
        }
    }

    /**
     * Without this, bumping `version` and forgetting to commit the exported schema leaves
     * the next migration with nothing to test against — and the omission only shows up
     * once a user's database fails to open.
     */
    @Test
    fun schemaFileIsCommitted() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val path = "${AppDatabase::class.java.canonicalName}/$SCHEMA_VERSION.json"
        val schema = try {
            assets.open(path).use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "No exported schema for version $SCHEMA_VERSION at app/schemas/$path — " +
                    "build once and commit what ksp writes there.",
                e,
            )
        }
        assertTrue("Exported schema $path is empty", schema.isNotEmpty())
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
