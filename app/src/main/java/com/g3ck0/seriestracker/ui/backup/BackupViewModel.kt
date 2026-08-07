package com.g3ck0.seriestracker.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.backup.BackupRepository
import com.g3ck0.seriestracker.data.telemetry.Telemetry
import com.g3ck0.seriestracker.data.telemetry.TelemetryEvent
import com.g3ck0.seriestracker.ui.common.plural
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(): String = repository.suggestedFileName()

    fun export(uri: Uri) = viewModelScope.launch {
        _state.value = BackupUiState(busy = true)
        repository.export(uri)
            .onSuccess { count ->
                // Without the count: how many titles someone has is a fact about them.
                telemetry.event(TelemetryEvent.EXPORT_DONE)
                _state.value = BackupUiState(
                    message = "Экспортировано $count ${plural(count, "тайтл", "тайтла", "тайтлов")}",
                )
            }
            .onFailure {
                _state.value = BackupUiState(message = it.message ?: "Не удалось экспортировать")
            }
    }

    fun import(uri: Uri, mode: BackupRepository.ImportMode) = viewModelScope.launch {
        _state.value = BackupUiState(busy = true)
        // Two events rather than one: an import that starts and never finishes is the
        // interesting case, and the difference between the counts is the only way to see
        // it — the file is the user's own and its contents are never reported.
        telemetry.event(TelemetryEvent.IMPORT_STARTED)
        repository.import(uri, mode)
            .onSuccess { result ->
                telemetry.event(TelemetryEvent.IMPORT_FINISHED)
                val parts = buildList {
                    if (result.titlesAdded > 0) add("добавлено ${result.titlesAdded}")
                    if (result.titlesUpdated > 0) add("обновлено ${result.titlesUpdated}")
                    if (result.episodes > 0) add("серий ${result.episodes}")
                    if (result.skipped > 0) add("пропущено ${result.skipped}")
                }
                _state.value = BackupUiState(
                    message = if (parts.isEmpty()) "Изменений нет" else "Импорт: " + parts.joinToString(", "),
                )
            }
            .onFailure {
                _state.value = BackupUiState(message = it.message ?: "Не удалось импортировать")
            }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
