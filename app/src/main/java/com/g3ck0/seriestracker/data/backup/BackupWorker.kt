package com.g3ck0.seriestracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The weekly backup job. Thin on purpose: everything it decides lives in
 * [AutoBackupManager], which is testable without a WorkManager around it.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val autoBackup: AutoBackupManager,
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result = when (autoBackup.runScheduled()) {
        is AutoBackupResult.Written -> Result.success()
        // Switched off or nothing to save: the next period will look again, and a
        // retry would only ask the same question sooner.
        is AutoBackupResult.Skipped -> Result.success()
        // Retry rather than failure: the usual reason is a folder that is momentarily
        // unavailable, and a periodic job that failed once still runs next week.
        is AutoBackupResult.Failed -> Result.retry()
    }

    companion object {
        const val WORK_NAME = "auto-backup"
    }
}

@Singleton
class WorkManagerAutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoBackupScheduler {

    /**
     * `KEEP` rather than `UPDATE`: the request is the same on every start, and replacing
     * it would restart the seven-day window each time the app is opened — on a phone
     * opened daily the backup would never come due.
     */
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(BACKUP_INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(
                // Writing a hundred kilobytes is cheap, so the only thing worth waiting
                // for is a battery that is not about to run out.
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.WORK_NAME)
    }

    private companion object {
        const val BACKUP_INTERVAL_DAYS = 7L
    }
}
