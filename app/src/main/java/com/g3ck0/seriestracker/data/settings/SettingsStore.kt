package com.g3ck0.seriestracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The app's persisted settings — everything that is neither library data nor a build
 * constant. Room stays the single source of truth for the library itself.
 *
 * An interface with one real implementation, so JVM tests get a fake the way they get one
 * for the DAO instead of needing a file on disk.
 */
interface SettingsStore {

    /**
     * Whether the notification permission has already been asked for, whatever the answer
     * was. Asking again after a refusal only reopens a dialog the system will not show.
     */
    val notificationsAsked: Flow<Boolean>

    suspend fun setNotificationsAsked(value: Boolean)

    /**
     * Whether the weekly backup runs. On by default: the private folder it writes into
     * needs no permission and no question on first launch, and a backup nobody switched
     * on is exactly the backup that exists when the phone is lost.
     */
    val autoBackupEnabled: Flow<Boolean>

    suspend fun setAutoBackupEnabled(value: Boolean)

    /**
     * The SAF tree the user picked, as a string, or null while backups go to the app's
     * own folder. A string rather than a `Uri` so that everything above the storage layer
     * — the ViewModel and its JVM tests included — stays free of Android types.
     */
    val backupFolderUri: Flow<String?>

    suspend fun setBackupFolderUri(value: String?)

    /**
     * Whether crash reports and the anonymous counters may be sent. On by default, and
     * only ever consulted in the `store` flavour — `direct` has no reporting compiled in,
     * so there is nothing for the flag to switch (see `TelemetryConfig.available`).
     */
    val crashReportsEnabled: Flow<Boolean>

    suspend fun setCrashReportsEnabled(value: Boolean)

    /**
     * Whether the one-per-install `first_launch` event has already gone out. Without it
     * the event would be sent on every cold start and the funnel it feeds would count
     * launches instead of installs.
     */
    val firstLaunchReported: Flow<Boolean>

    suspend fun setFirstLaunchReported(value: Boolean)

    /** When the last backup was written, epoch millis, or null while there is none. */
    val lastBackupAt: Flow<Long?>

    /**
     * Where that backup landed, as shown in the dialog. Stored rather than derived from
     * [backupFolderUri]: a run that fell back to the private folder because the chosen
     * one is gone must say so, and the setting still names the chosen one.
     */
    val lastBackupLocation: Flow<String?>

    suspend fun setLastBackup(timestamp: Long, location: String)
}

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    // A read that fails takes the library screen's whole state flow down with it, and a
    // settings file that cannot be read is not worth a crash: the defaults are usable.
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    override val notificationsAsked: Flow<Boolean> =
        preferences.map { it[NOTIFICATIONS_ASKED] ?: false }

    override suspend fun setNotificationsAsked(value: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ASKED] = value }
    }

    override val autoBackupEnabled: Flow<Boolean> =
        preferences.map { it[AUTO_BACKUP_ENABLED] ?: true }

    override suspend fun setAutoBackupEnabled(value: Boolean) {
        dataStore.edit { it[AUTO_BACKUP_ENABLED] = value }
    }

    override val backupFolderUri: Flow<String?> =
        preferences.map { it[BACKUP_FOLDER_URI]?.takeIf(String::isNotBlank) }

    override suspend fun setBackupFolderUri(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(BACKUP_FOLDER_URI)
            else preferences[BACKUP_FOLDER_URI] = value
        }
    }

    override val crashReportsEnabled: Flow<Boolean> =
        preferences.map { it[CRASH_REPORTS_ENABLED] ?: true }

    override suspend fun setCrashReportsEnabled(value: Boolean) {
        dataStore.edit { it[CRASH_REPORTS_ENABLED] = value }
    }

    override val firstLaunchReported: Flow<Boolean> =
        preferences.map { it[FIRST_LAUNCH_REPORTED] ?: false }

    override suspend fun setFirstLaunchReported(value: Boolean) {
        dataStore.edit { it[FIRST_LAUNCH_REPORTED] = value }
    }

    override val lastBackupAt: Flow<Long?> = preferences.map { it[LAST_BACKUP_AT] }

    override val lastBackupLocation: Flow<String?> = preferences.map { it[LAST_BACKUP_LOCATION] }

    override suspend fun setLastBackup(timestamp: Long, location: String) {
        dataStore.edit {
            it[LAST_BACKUP_AT] = timestamp
            it[LAST_BACKUP_LOCATION] = location
        }
    }

    private companion object {
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val CRASH_REPORTS_ENABLED = booleanPreferencesKey("crash_reports_enabled")
        val FIRST_LAUNCH_REPORTED = booleanPreferencesKey("first_launch_reported")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_LOCATION = stringPreferencesKey("last_backup_location")
    }
}
