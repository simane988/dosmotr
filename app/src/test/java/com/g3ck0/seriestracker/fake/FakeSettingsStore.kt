package com.g3ck0.seriestracker.fake

import com.g3ck0.seriestracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory stand-in for [SettingsStore], the way [FakeTrackerDao] stands in for the DAO.
 *
 * DataStore keeps the value in a file and emits the current one on collection; so does
 * this, which is all a ViewModel can tell apart. The real implementation is verified by
 * the instrumented `SettingsStoreTest`.
 */
class FakeSettingsStore(
    notificationsAsked: Boolean = false,
    autoBackupEnabled: Boolean = true,
    backupFolderUri: String? = null,
    crashReportsEnabled: Boolean = true,
    firstLaunchReported: Boolean = false,
) : SettingsStore {

    private val asked = MutableStateFlow(notificationsAsked)
    private val backupEnabled = MutableStateFlow(autoBackupEnabled)
    private val folderUri = MutableStateFlow(backupFolderUri)
    private val crashReports = MutableStateFlow(crashReportsEnabled)
    private val firstLaunch = MutableStateFlow(firstLaunchReported)
    private val backupAt = MutableStateFlow<Long?>(null)
    private val backupLocation = MutableStateFlow<String?>(null)

    override val notificationsAsked: Flow<Boolean> = asked

    override suspend fun setNotificationsAsked(value: Boolean) {
        asked.value = value
    }

    override val autoBackupEnabled: Flow<Boolean> = backupEnabled

    override suspend fun setAutoBackupEnabled(value: Boolean) {
        backupEnabled.value = value
    }

    override val backupFolderUri: Flow<String?> = folderUri

    override suspend fun setBackupFolderUri(value: String?) {
        folderUri.value = value
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

    override val lastBackupLocation: Flow<String?> = backupLocation

    override suspend fun setLastBackup(timestamp: Long, location: String) {
        backupAt.value = timestamp
        backupLocation.value = location
    }

    /** What a restart would read back — the flag as it is stored, without collecting. */
    val storedNotificationsAsked: Boolean get() = asked.value

    val storedAutoBackupEnabled: Boolean get() = backupEnabled.value

    val storedBackupFolderUri: String? get() = folderUri.value

    val storedCrashReportsEnabled: Boolean get() = crashReports.value

    val storedFirstLaunchReported: Boolean get() = firstLaunch.value

    val storedLastBackupAt: Long? get() = backupAt.value

    val storedLastBackupLocation: String? get() = backupLocation.value
}
