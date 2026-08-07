package com.g3ck0.seriestracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.g3ck0.seriestracker.data.backup.BackupWorker
import com.g3ck0.seriestracker.data.backup.WorkManagerAutoBackupScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real WorkManager, since what the switch has to do is take the job down — a fake
 * scheduler can only prove that `cancel()` was called, not that nothing is left enqueued.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerAutoBackupScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerAutoBackupScheduler(context)
        // The app schedules the same unique work on start, so the test begins from nothing.
        scheduler.cancel()
    }

    @After
    fun tearDown() {
        scheduler.cancel()
    }

    private fun states(): List<WorkInfo.State> =
        workManager.getWorkInfosForUniqueWork(BackupWorker.WORK_NAME).get().map { it.state }

    /**
     * Enqueueing and cancelling are both asynchronous — they hand back an `Operation` the
     * production callers have no reason to wait on — so the test waits for the database
     * behind WorkManager to catch up rather than reading it once and hoping.
     */
    private fun awaitStates(condition: (List<WorkInfo.State>) -> Boolean): List<WorkInfo.State> {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        var current = states()
        while (!condition(current) && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS)
            current = states()
        }
        return current
    }

    @Test
    fun schedulingEnqueuesOnePeriodicJob() {
        scheduler.schedule()

        assertEquals(
            listOf(WorkInfo.State.ENQUEUED),
            awaitStates { it == listOf(WorkInfo.State.ENQUEUED) },
        )
    }

    @Test
    fun schedulingTwiceKeepsTheOneAlreadyWaiting() {
        scheduler.schedule()
        scheduler.schedule()

        assertEquals(1, awaitStates { it.size == 1 }.size)
    }

    @Test
    fun cancellingLeavesNothingEnqueued() {
        scheduler.schedule()
        awaitStates { it.contains(WorkInfo.State.ENQUEUED) }

        scheduler.cancel()

        assertTrue(awaitStates { state -> state.none { it == WorkInfo.State.ENQUEUED } }
            .none { it == WorkInfo.State.ENQUEUED })
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
