package com.g3ck0.seriestracker

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.g3ck0.seriestracker.data.backup.AndroidBackupStorage
import com.g3ck0.seriestracker.data.backup.AutoBackupManager
import com.g3ck0.seriestracker.data.backup.AutoBackupResult
import com.g3ck0.seriestracker.data.backup.AutoBackupScheduler
import com.g3ck0.seriestracker.data.backup.BackupRepository
import com.g3ck0.seriestracker.data.backup.BackupRepository.ImportMode
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.settings.SettingsStore
import com.g3ck0.seriestracker.data.telemetry.Telemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The file the worker leaves behind, on the real filesystem: it has to be a file the
 * existing import reads back without losing anything, or the backup is decoration.
 *
 * The private folder is what a fresh install writes into, and the only one an instrumented
 * test can reach — a SAF tree needs a person to pick it in the system picker.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupFileTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: TrackerDao
    private lateinit var repository: BackupRepository
    private lateinit var settings: InMemorySettings
    private lateinit var manager: AutoBackupManager
    private lateinit var directory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.trackerDao()
        repository = BackupRepository(context, db, dao)
        settings = InMemorySettings()
        manager = AutoBackupManager(
            source = repository,
            settings = settings,
            storage = AndroidBackupStorage(context, settings),
            scheduler = NoScheduler,
            telemetry = NoTelemetry,
        )
        directory = File(context.filesDir, "backups")
        directory.deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        directory.deleteRecursively()
    }

    private suspend fun seedLibrary() {
        dao.upsertTitle(
            TitleEntity(
                id = "tv_1",
                catalogId = 1,
                mediaType = MediaType.TV,
                name = "Dark",
                status = WatchStatus.WATCHING,
                userRating = 9,
                notes = "с женой",
                runtimeMinutes = 50,
                episodesLoaded = true,
            )
        )
        dao.insertEpisodes((1..4).map { EpisodeEntity("tv_1", 1, it, name = "S01E$it") })
        dao.setEpisodeWatched("tv_1", 1, 1, watched = true, watchedAt = 100L)
        dao.setEpisodeWatched("tv_1", 1, 2, watched = true, watchedAt = 200L)
    }

    @Test
    fun aRunLeavesAFileInThePrivateFolder() = runTest {
        seedLibrary()

        val result = manager.runNow()

        assertTrue("$result", result is AutoBackupResult.Written)
        val written = File(directory, (result as AutoBackupResult.Written).fileName)
        assertTrue(written.exists())
        assertTrue(written.length() > 0)
    }

    /** The DOD in full: the file the backup wrote restores the library through «Заменить». */
    @Test
    fun theFileItWritesImportsBackWithoutLosingAnything() = runTest {
        seedLibrary()
        val written = manager.runNow() as AutoBackupResult.Written
        dao.deleteAllTitles()

        val file = File(directory, written.fileName)
        val imported = repository.import(Uri.fromFile(file), ImportMode.REPLACE).getOrThrow()

        assertEquals(1, imported.titlesAdded)
        assertEquals(4, imported.episodes)
        val restored = dao.getTitle("tv_1")!!
        assertEquals("Dark", restored.name)
        assertEquals(9, restored.userRating)
        assertEquals("с женой", restored.notes)
        assertEquals(2, dao.unwatchedCount("tv_1"))
    }

    @Test
    fun theFourthRunPushesTheOldestFileOut() = runTest {
        seedLibrary()
        directory.mkdirs()
        listOf("dosmotr-2020-01-01.json", "dosmotr-2020-01-08.json", "dosmotr-2020-01-15.json")
            .forEach { File(directory, it).writeText("{}") }

        manager.runNow()

        val left = directory.list()!!.sorted()
        assertEquals(3, left.size)
        assertTrue("dosmotr-2020-01-01.json" !in left)
        assertTrue("dosmotr-2020-01-08.json" in left)
    }

    @Test
    fun anEmptyLibraryWritesNothing() = runTest {
        val result = manager.runNow()

        assertTrue("$result", result is AutoBackupResult.Skipped)
        assertTrue(directory.list().isNullOrEmpty())
    }

    /**
     * A tree Uri nobody granted anything for: the run must land in the private folder
     * instead of failing, which is what a revoked permission looks like from here.
     */
    @Test
    fun aFolderWithoutPermissionFallsBackToThePrivateOne() = runTest {
        seedLibrary()
        settings.setBackupFolderUri("content://com.example.provider/tree/gone")

        val result = manager.runNow()

        assertTrue("$result", result is AutoBackupResult.Written)
        assertTrue(File(directory, (result as AutoBackupResult.Written).fileName).exists())
    }

    private object NoScheduler : AutoBackupScheduler {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }

    /** This test is about the files on disk; what was reported is `TelemetryTest`'s business. */
    private object NoTelemetry : Telemetry {
        override fun event(name: String, param: String?) = Unit
        override fun nonFatal(t: Throwable) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
    }

    /**
     * The app's own DataStore is a file shared with whatever else the process is doing;
     * this test only needs the values to come back, so it keeps them in memory.
     */
    private class InMemorySettings : SettingsStore {
        private val asked = MutableStateFlow(false)
        private val enabled = MutableStateFlow(true)
        private val folder = MutableStateFlow<String?>(null)
        private val crashReports = MutableStateFlow(true)
        private val firstLaunch = MutableStateFlow(false)
        private val backupAt = MutableStateFlow<Long?>(null)
        private val location = MutableStateFlow<String?>(null)

        override val notificationsAsked: Flow<Boolean> = asked
        override suspend fun setNotificationsAsked(value: Boolean) {
            asked.value = value
        }

        override val autoBackupEnabled: Flow<Boolean> = enabled
        override suspend fun setAutoBackupEnabled(value: Boolean) {
            enabled.value = value
        }

        override val backupFolderUri: Flow<String?> = folder
        override suspend fun setBackupFolderUri(value: String?) {
            folder.value = value
        }

        override val crashReportsEnabled: Flow<Boolean> = crashReports
        override suspend fun setCrashReportsEnabled(value: Boolean) {
            crashReports.value = value
        }

        override val firstLaunchReported: Flow<Boolean> = firstLaunch
        override suspend fun setFirstLaunchReported(value: Boolean) {
            firstLaunch.value = value
        }

        override val lastBackupAt: Flow<Long?> = backupAt
        override val lastBackupLocation: Flow<String?> = location
        override suspend fun setLastBackup(timestamp: Long, location: String) {
            backupAt.value = timestamp
            this.location.value = location
        }
    }
}
