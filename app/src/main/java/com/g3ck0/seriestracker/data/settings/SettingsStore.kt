package com.g3ck0.seriestracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

    private companion object {
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
    }
}
