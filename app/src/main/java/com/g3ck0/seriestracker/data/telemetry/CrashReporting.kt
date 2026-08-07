package com.g3ck0.seriestracker.data.telemetry

import com.g3ck0.seriestracker.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The switch in «О приложении», and everything that has to happen when it moves.
 *
 * Split from [Telemetry] the way [com.g3ck0.seriestracker.data.backup.AutoBackupManager] is
 * split from the scheduler: the setting is persisted here, applying it is the
 * implementation's business, and both halves are drivable by fakes on the JVM.
 *
 * The stored value is what a restart reads back, so [sync] is called on every app start —
 * a build that was switched off must not start collecting again because the process
 * restarted, and Crashlytics remembers its own flag in a file this app does not own.
 */
@Singleton
class CrashReporting @Inject constructor(
    private val settings: SettingsStore,
    private val telemetry: Telemetry,
) {

    /** Whether there is anything behind the switch — false in the `direct` flavour. */
    val available: Boolean get() = TelemetryConfig.available

    /** Brings the implementation in line with the stored setting. */
    suspend fun sync() {
        telemetry.setEnabled(enabledNow())
    }

    suspend fun setEnabled(enabled: Boolean) {
        settings.setCrashReportsEnabled(enabled)
        telemetry.setEnabled(enabled)
    }

    private suspend fun enabledNow(): Boolean = settings.crashReportsEnabled.first()
}
