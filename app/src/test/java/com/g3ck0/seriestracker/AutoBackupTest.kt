package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.data.backup.AutoBackupManager
import com.g3ck0.seriestracker.data.backup.AutoBackupResult
import com.g3ck0.seriestracker.fake.FakeAutoBackupScheduler
import com.g3ck0.seriestracker.fake.FakeAutoBackupStorage
import com.g3ck0.seriestracker.fake.FakeBackupFolder
import com.g3ck0.seriestracker.fake.FakeBackupSource
import com.g3ck0.seriestracker.fake.FakeSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of the weekly backup: when it is scheduled, when it refuses to write, and
 * what the rotation keeps. Everything runs on fakes — the real file writing is the
 * instrumented `AutoBackupFileTest`.
 */
class AutoBackupTest {

    private val source = FakeBackupSource()
    private val storage = FakeAutoBackupStorage()
    private val scheduler = FakeAutoBackupScheduler()

    private fun manager(settings: FakeSettingsStore) =
        AutoBackupManager(source, settings, storage, scheduler)

    @Test
    fun aFreshInstallSchedulesTheWeeklyBackup() = runTest {
        val settings = FakeSettingsStore()

        manager(settings).syncSchedule()

        assertTrue(scheduler.scheduled)
    }

    @Test
    fun switchingItOffTakesTheWorkDown() = runTest {
        val settings = FakeSettingsStore()
        val manager = manager(settings)
        manager.syncSchedule()

        manager.setEnabled(false)

        assertFalse(scheduler.scheduled)
        assertEquals(1, scheduler.cancelCalls)
        assertFalse(settings.storedAutoBackupEnabled)
    }

    @Test
    fun switchingItBackOnSchedulesItAgain() = runTest {
        val settings = FakeSettingsStore(autoBackupEnabled = false)
        val manager = manager(settings)
        manager.syncSchedule()

        manager.setEnabled(true)

        assertTrue(scheduler.scheduled)
        assertTrue(settings.storedAutoBackupEnabled)
    }

    @Test
    fun aRunWritesTheFileAndRemembersWhen() = runTest {
        val settings = FakeSettingsStore()
        source.titles = 7

        val result = manager(settings).runScheduled()

        assertEquals(
            AutoBackupResult.Written("dosmotr-2026-08-06.json", "Память приложения", 7),
            result,
        )
        assertEquals(source.json, storage.folder.files["dosmotr-2026-08-06.json"])
        assertNotNull(settings.storedLastBackupAt)
        assertEquals("Память приложения", settings.storedLastBackupLocation)
    }

    @Test
    fun theScheduledRunDoesNothingWhileTheSettingIsOff() = runTest {
        val settings = FakeSettingsStore(autoBackupEnabled = false)

        val result = manager(settings).runScheduled()

        assertTrue(result is AutoBackupResult.Skipped)
        assertTrue(storage.folder.files.isEmpty())
        assertNull(settings.storedLastBackupAt)
    }

    /** The button in the dialog is a direct request, so it does not consult the switch. */
    @Test
    fun makeItNowRunsEvenWithTheSettingOff() = runTest {
        val settings = FakeSettingsStore(autoBackupEnabled = false)

        val result = manager(settings).runNow()

        assertTrue(result is AutoBackupResult.Written)
        assertEquals(1, storage.folder.files.size)
    }

    /**
     * An empty library would produce a file the import refuses ("В файле нет ни одного
     * тайтла") and push a real backup out of the rotation on its way.
     */
    @Test
    fun anEmptyLibraryIsNotWorthAFile() = runTest {
        val settings = FakeSettingsStore()
        source.titles = 0

        val result = manager(settings).runScheduled()

        assertTrue(result is AutoBackupResult.Skipped)
        assertTrue(storage.folder.files.isEmpty())
        assertNull(settings.storedLastBackupAt)
    }

    @Test
    fun aFolderThatRefusesTheWriteIsOneFailedRunAndNothingElse() = runTest {
        val settings = FakeSettingsStore()
        storage.folder.failOnWrite = true

        val result = manager(settings).runScheduled()

        assertTrue(result is AutoBackupResult.Failed)
        assertNull(settings.storedLastBackupAt)
    }

    @Test
    fun theRotationKeepsThreeFilesAndDropsTheOldest() = runTest {
        val settings = FakeSettingsStore()
        storage.folder = FakeBackupFolder(
            files = mapOf(
                "dosmotr-2026-07-16.json" to "oldest",
                "dosmotr-2026-07-23.json" to "older",
                "dosmotr-2026-07-30.json" to "old",
            )
        )

        manager(settings).runScheduled()

        assertEquals(
            listOf(
                "dosmotr-2026-07-23.json",
                "dosmotr-2026-07-30.json",
                "dosmotr-2026-08-06.json",
            ),
            storage.folder.files.keys.sorted(),
        )
    }

    /** A second run on the same day replaces its own file rather than filling the rotation. */
    @Test
    fun aSecondRunOnTheSameDayKeepsTheOtherTwo() = runTest {
        val settings = FakeSettingsStore()
        storage.folder = FakeBackupFolder(
            files = mapOf(
                "dosmotr-2026-07-23.json" to "older",
                "dosmotr-2026-07-30.json" to "old",
                "dosmotr-2026-08-06.json" to "stale copy of today",
            )
        )

        manager(settings).runScheduled()

        assertEquals(3, storage.folder.files.size)
        assertEquals(source.json, storage.folder.files["dosmotr-2026-08-06.json"])
    }

    /** The chosen folder is the user's own — nothing that is not ours is ever deleted. */
    @Test
    fun theRotationOnlyTouchesFilesThisAppWrote() = runTest {
        val settings = FakeSettingsStore()
        storage.folder = FakeBackupFolder(
            files = mapOf(
                "dosmotr-2026-07-16.json" to "oldest",
                "dosmotr-2026-07-23.json" to "older",
                "dosmotr-2026-07-30.json" to "old",
                "отпуск.jpg" to "a photo",
                "notes.txt" to "a note",
            )
        )

        manager(settings).runScheduled()

        assertTrue(storage.folder.files.containsKey("отпуск.jpg"))
        assertTrue(storage.folder.files.containsKey("notes.txt"))
        assertFalse(storage.folder.files.containsKey("dosmotr-2026-07-16.json"))
    }

    @Test
    fun choosingAFolderIsHandedToTheStorage() = runTest {
        val settings = FakeSettingsStore()

        manager(settings).chooseFolder("content://tree/backups")

        assertEquals("content://tree/backups", storage.chosenFolder)
    }

    @Test
    fun theDialogIsToldWhereTheNextBackupGoes() = runTest {
        val settings = FakeSettingsStore()
        storage.folder = FakeBackupFolder(label = "Dosmotr", isPrivate = false)

        assertEquals("Dosmotr", manager(settings).folderLabel())
    }
}
