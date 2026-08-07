package com.g3ck0.seriestracker.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.settings.SettingsStore
import com.g3ck0.seriestracker.data.telemetry.CrashReporting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    /** Whether the switch is drawn at all — false in `direct`, where nothing is sent. */
    val crashReportsAvailable: Boolean = false,
    val crashReportsEnabled: Boolean = true,
)

/**
 * The only stateful thing the about dialog has: the crash-report switch.
 *
 * The value comes from [SettingsStore] rather than from the ViewModel's own memory, so
 * what the switch shows is what a restart would read back — which is the half of the DOD
 * that a toggle usually fails.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val crashReporting: CrashReporting,
    settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<AboutUiState> = settings.crashReportsEnabled
        .map { enabled ->
            AboutUiState(
                crashReportsAvailable = crashReporting.available,
                crashReportsEnabled = enabled,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AboutUiState(crashReportsAvailable = crashReporting.available),
        )

    fun setCrashReportsEnabled(enabled: Boolean) = viewModelScope.launch {
        crashReporting.setEnabled(enabled)
    }
}
