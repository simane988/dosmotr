package com.g3ck0.seriestracker

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.g3ck0.seriestracker.data.settings.DataStoreSettingsStore
import com.g3ck0.seriestracker.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real DataStore-backed store, on a file of its own rather than the app's — the point
 * being that what it writes is still there for the next instance to read, which is what
 * "the prompt does not come back after a restart" rests on.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private lateinit var directory: File
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.filesDir, "settings-test-${System.nanoTime()}")
        directory.mkdirs()
        file = File(directory, "settings.preferences_pb")
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        directory.deleteRecursively()
    }

    /**
     * Each store gets its own scope: DataStore refuses a second instance on a file one is
     * already open on, and cancelling the scope is what closes the first one.
     */
    private fun openStore(): SettingsStore {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scopes += scope
        return DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private suspend fun closeStores() {
        scopes.forEach { scope ->
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
        scopes.clear()
    }

    @Test
    fun nothingWasAskedOnAFreshInstall() = runTest {
        assertFalse(openStore().notificationsAsked.first())
    }

    @Test
    fun theAnswerIsReadableAsSoonAsItIsWritten() = runTest {
        val store = openStore()

        store.setNotificationsAsked(true)

        assertTrue(store.notificationsAsked.first())
    }

    @Test
    fun theAnswerOutlivesTheStoreThatWroteIt() = runTest {
        openStore().setNotificationsAsked(true)
        closeStores()

        assertTrue(openStore().notificationsAsked.first())
    }

    // --- feature-18 ---

    /**
     * On by default, because a crash nobody hears about is a crash nobody fixes — and the
     * switch in «О приложении» is right next to the sentence explaining what is sent.
     */
    @Test
    fun crashReportsAreOnUntilSomeoneTurnsThemOff() = runTest {
        assertTrue(openStore().crashReportsEnabled.first())
    }

    /** Half the DOD of the switch: it has to survive a restart, not just a recomposition. */
    @Test
    fun theCrashReportSwitchOutlivesTheStoreThatWroteIt() = runTest {
        openStore().setCrashReportsEnabled(false)
        closeStores()

        assertFalse(openStore().crashReportsEnabled.first())
    }

    /**
     * `first_launch` is the denominator of the first-session funnel, so it must be written
     * to disk rather than kept in memory: a process restart would otherwise count as a new
     * install and the ratio would quietly exceed 100 %.
     */
    @Test
    fun theFirstLaunchFlagOutlivesTheStoreThatWroteIt() = runTest {
        // One store at a time on one file: DataStore refuses a second instance outright,
        // which is what closeStores() above is for.
        val store = openStore()
        assertFalse(store.firstLaunchReported.first())
        store.setFirstLaunchReported(true)
        closeStores()

        assertTrue(openStore().firstLaunchReported.first())
    }
}
