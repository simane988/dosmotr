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
class FakeSettingsStore(notificationsAsked: Boolean = false) : SettingsStore {

    private val asked = MutableStateFlow(notificationsAsked)

    override val notificationsAsked: Flow<Boolean> = asked

    override suspend fun setNotificationsAsked(value: Boolean) {
        asked.value = value
    }

    /** What a restart would read back — the flag as it is stored, without collecting. */
    val storedNotificationsAsked: Boolean get() = asked.value
}
