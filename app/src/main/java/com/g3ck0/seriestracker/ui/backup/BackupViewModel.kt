package com.g3ck0.seriestracker.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.g3ck0.seriestracker.data.backup.BackupRepository
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
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(): String = repository.suggestedFileName()

    fun export(uri: Uri) = viewModelScope.launch {
        _state.value = BackupUiState(busy = true)
        repository.export(uri)
            .onSuccess { count ->
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
        repository.import(uri, mode)
            .onSuccess { result ->
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
