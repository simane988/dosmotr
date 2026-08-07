package com.g3ck0.seriestracker.data.backup

import com.g3ck0.seriestracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What an automatic backup needs from the library, behind an interface because
 * [BackupRepository] needs a real database and [AutoBackupManager] is tested on the JVM.
 */
interface BackupSource {

    /** Dated name to write under, so a week's backups do not overwrite each other. */
    fun suggestedFileName(): String

    /** Titles a backup taken now would carry. */
    suspend fun titleCount(): Int

    suspend fun exportToJson(): String
}

/**
 * A folder backups are written into — the app's own or the one picked through SAF. Both
 * answer the same three questions, which is all the rotation below needs.
 */
interface BackupFolder {

    /** Human-readable, shown in the dialog as where the last backup landed. */
    val label: String

    /** True for the app's private folder, i.e. when a chosen folder was not usable. */
    val isPrivate: Boolean

    /** Every file name in the folder — the caller decides which of them are backups. */
    suspend fun list(): List<String>

    /** Writes [content] as [name], replacing a file of that name if one is there. */
    suspend fun write(name: String, content: String)

    suspend fun delete(name: String)
}

/** Resolves the folder to write into and remembers the one the user picked. */
interface AutoBackupStorage {

    /**
     * The folder for the next backup: the picked one when it is still writable, the
     * private one otherwise. Never fails — a revoked permission is a fallback, not an
     * error, or the weekly backup would stop the moment a folder is renamed.
     */
    suspend fun openFolder(): BackupFolder

    /**
     * Remembers [folderUri] as the folder to write into, taking the persistable
     * permission with it. Null goes back to the app's private folder.
     */
    suspend fun useFolder(folderUri: String?)
}

/** The periodic work itself, behind an interface so the toggle can be tested without WorkManager. */
interface AutoBackupScheduler {

    /** Enqueues the weekly job, keeping an already scheduled one as it is. */
    fun schedule()

    fun cancel()
}

/** What one run did, for the worker to report and the dialog to show. */
sealed interface AutoBackupResult {

    data class Written(val fileName: String, val location: String, val titles: Int) : AutoBackupResult

    /** Nothing worth writing — the setting is off, or the library is empty. */
    data class Skipped(val reason: String) : AutoBackupResult

    data class Failed(val error: Throwable) : AutoBackupResult
}

/**
 * The weekly backup: writes the same JSON the manual export writes, keeps the last
 * [KEPT_BACKUPS] files and drops the rest.
 *
 * Everything here is deliberately free of Android types — the storage and the scheduler
 * are the two places where those live — so the rotation and the scheduling rules can be
 * driven by fakes in a JVM test.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    private val source: BackupSource,
    private val settings: SettingsStore,
    private val storage: AutoBackupStorage,
    private val scheduler: AutoBackupScheduler,
) {

    /**
     * Brings the schedule in line with the setting. Called on every app start, since work
     * enqueued by an earlier install is gone after "clear data" and a cancelled one must
     * not come back on its own.
     */
    suspend fun syncSchedule() {
        if (settings.autoBackupEnabled.first()) scheduler.schedule() else scheduler.cancel()
    }

    suspend fun setEnabled(enabled: Boolean) {
        settings.setAutoBackupEnabled(enabled)
        syncSchedule()
    }

    suspend fun chooseFolder(folderUri: String?) {
        storage.useFolder(folderUri)
    }

    /** Where the next backup would go, for the dialog to name before anything has run. */
    suspend fun folderLabel(): String = storage.openFolder().label

    /** The worker's body: the setting is checked here, not in the worker. */
    suspend fun runScheduled(): AutoBackupResult =
        if (settings.autoBackupEnabled.first()) run() else {
            AutoBackupResult.Skipped("Автобэкап выключен")
        }

    /**
     * "Сделать сейчас" — runs whatever the setting says, because a person who opened the
     * dialog and pressed the button has asked for this one directly.
     */
    suspend fun runNow(): AutoBackupResult = run()

    // Anything the storage throws — a folder gone, a full disk, a provider that refuses
    // the write — is one failed run, not a crashed app or a crashed worker.
    private suspend fun run(): AutoBackupResult =
        runCatching { write() }.getOrElse { AutoBackupResult.Failed(it) }

    private suspend fun write(): AutoBackupResult {
        val titles = source.titleCount()
        // A backup of an empty library restores nothing, and writing one would push a
        // real backup out of the rotation — so an empty library is skipped, not failed.
        if (titles == 0) return AutoBackupResult.Skipped("Библиотека пуста — нечего сохранять")

        val folder = storage.openFolder()
        val name = source.suggestedFileName()
        folder.write(name, source.exportToJson())
        rotate(folder, name)
        settings.setLastBackup(System.currentTimeMillis(), folder.label)
        return AutoBackupResult.Written(name, folder.label, titles)
    }

    /**
     * Keeps the newest [KEPT_BACKUPS] files and deletes the rest.
     *
     * Only files this app writes are considered: the chosen folder is the user's, and
     * everything else in it is none of our business. The names carry an ISO date, so
     * sorting them as strings sorts them by age — [current] is kept whatever its name
     * sorts as, since it is the one just written.
     */
    private suspend fun rotate(folder: BackupFolder, current: String) {
        val ours = folder.list().filter(::isBackupName).sorted()
        val stale = ours.filterNot { it == current }.dropLast(maxOf(KEPT_BACKUPS - 1, 0))
        stale.forEach { folder.delete(it) }
    }

    companion object {

        /** A month of history at one file a week — enough to go back past a bad import. */
        const val KEPT_BACKUPS = 3

        /** Matches [BackupRepository.suggestedFileName]. */
        fun isBackupName(name: String): Boolean =
            name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_SUFFIX)
    }
}

internal const val BACKUP_FILE_PREFIX = "dosmotr-"
internal const val BACKUP_FILE_SUFFIX = ".json"
