package com.g3ck0.seriestracker.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.backup.BackupRepository.ImportMode

/**
 * Export/import entry point for the library top bar.
 *
 * Uses the Storage Access Framework, so the user picks the file themselves and the
 * app needs no storage permission at all.
 */
@Composable
fun BackupMenu(
    onMessage: (String) -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var askMode by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::export) }

    // Some file managers report JSON as octet-stream or plain text — accept all three.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.import(it, mode) } }

    if (askMode) {
        AlertDialog(
            onDismissRequest = { askMode = false },
            title = { Text("Импорт из JSON") },
            text = {
                Text(
                    "Объединить — библиотека сохранится, добавятся недостающие тайтлы, " +
                        "отметки о просмотре сложатся.\n\n" +
                        "Заменить — текущая библиотека будет стёрта и восстановлена из файла."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askMode = false
                    mode = ImportMode.MERGE
                    importLauncher.launch(IMPORT_TYPES)
                }) { Text("Объединить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askMode = false
                    mode = ImportMode.REPLACE
                    importLauncher.launch(IMPORT_TYPES)
                }) { Text("Заменить") }
            },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }, enabled = !state.busy) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Экспорт в JSON") },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                onClick = {
                    expanded = false
                    exportLauncher.launch(viewModel.suggestedFileName())
                },
            )
            DropdownMenuItem(
                text = { Text("Импорт из JSON") },
                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                onClick = {
                    expanded = false
                    askMode = true
                },
            )
        }
    }
}

private val IMPORT_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")
