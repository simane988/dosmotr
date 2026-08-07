package com.g3ck0.seriestracker.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.backup.AutoBackupManager
import com.g3ck0.seriestracker.data.backup.AutoBackupResult
import com.g3ck0.seriestracker.data.settings.SettingsStore
import com.g3ck0.seriestracker.ui.common.plural
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutoBackupUiState(
    val enabled: Boolean = true,
    /** Where the next backup will go — the app's folder unless one was picked. */
    val folder: String = "",
    /** True once a folder of the user's own is in use, which is what can be given back. */
    val customFolder: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastBackupLocation: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val autoBackup: AutoBackupManager,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    /**
     * The folder's display name, refreshed by hand rather than derived from the setting:
     * turning a tree Uri into a name is a question for the document provider, and the
     * answer can be "gone" — in which case the name shown is the private folder's.
     */
    private val folder = MutableStateFlow("")

    val state: StateFlow<AutoBackupUiState> =
        combine(
            settings.autoBackupEnabled,
            settings.backupFolderUri,
            settings.lastBackupAt,
            settings.lastBackupLocation,
            folder,
        ) { enabled, folderUri, lastAt, lastLocation, folderLabel ->
            AutoBackupUiState(
                enabled = enabled,
                folder = folderLabel,
                customFolder = folderUri != null,
                lastBackupAt = lastAt,
                lastBackupLocation = lastLocation,
            )
        }.combine(busy) { base, isBusy -> base.copy(busy = isBusy) }
            .combine(message) { base, text -> base.copy(message = text) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AutoBackupUiState(),
            )

    init {
        refreshFolder()
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        autoBackup.setEnabled(enabled)
    }

    /** [folderUri] is the tree from the system picker, as a string. */
    fun chooseFolder(folderUri: String) = viewModelScope.launch {
        autoBackup.chooseFolder(folderUri)
        refreshFolderNow()
        message.value = "Бэкапы будут сохраняться в выбранную папку"
    }

    /** Gives the chosen folder back and returns to the app's own. */
    fun useAppFolder() = viewModelScope.launch {
        autoBackup.chooseFolder(null)
        refreshFolderNow()
        message.value = "Бэкапы будут сохраняться в память приложения"
    }

    fun runNow() = viewModelScope.launch {
        busy.value = true
        message.value = when (val result = autoBackup.runNow()) {
            is AutoBackupResult.Written -> {
                val titles = result.titles
                "Сохранено $titles ${plural(titles, "тайтл", "тайтла", "тайтлов")} " +
                    "в «${result.location}»"
            }

            is AutoBackupResult.Skipped -> result.reason
            is AutoBackupResult.Failed -> result.error.message ?: "Не удалось сделать бэкап"
        }
        refreshFolderNow()
        busy.value = false
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun refreshFolder() = viewModelScope.launch { refreshFolderNow() }

    private suspend fun refreshFolderNow() {
        folder.value = autoBackup.folderLabel()
    }
}
