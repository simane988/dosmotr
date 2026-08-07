package com.g3ck0.seriestracker.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.ui.common.DesignDialog
import com.g3ck0.seriestracker.ui.common.DialogTextButton
import com.g3ck0.seriestracker.ui.common.formatBackupTime

object AutoBackupTags {
    const val DIALOG = "autoBackup:dialog"
    const val TOGGLE = "autoBackup:toggle"
    const val FOLDER = "autoBackup:folder"
    const val PICK_FOLDER = "autoBackup:pickFolder"
    const val APP_FOLDER = "autoBackup:appFolder"
    const val LAST = "autoBackup:last"
    const val RUN_NOW = "autoBackup:runNow"
    const val MESSAGE = "autoBackup:message"
    const val CLOSE = "autoBackup:close"
}

/**
 * Wires the ViewModel and the folder picker to [AutoBackupContent]; the picker has to live
 * in a composable, and the persistable permission it grants is taken by the storage layer.
 */
@Composable
fun AutoBackupDialog(
    onDismiss: () -> Unit,
    viewModel: AutoBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.chooseFolder(it.toString()) } }

    AutoBackupContent(
        state = state,
        // The line the last action left behind belongs to this visit of the dialog, not
        // to the next one.
        onDismiss = {
            viewModel.consumeMessage()
            onDismiss()
        },
        onToggle = viewModel::setEnabled,
        // Null is "start at the top", which is where a folder picker opens by default.
        onPickFolder = { folderPicker.launch(null) },
        onUseAppFolder = { viewModel.useAppFolder() },
        onRunNow = { viewModel.runNow() },
    )
}

/**
 * Everything the automatic backup has to say: whether it runs, where it writes, when it
 * last did, and a way to do it right now.
 */
@Composable
fun AutoBackupContent(
    state: AutoBackupUiState,
    onDismiss: () -> Unit = {},
    onToggle: (Boolean) -> Unit = {},
    onPickFolder: () -> Unit = {},
    onUseAppFolder: () -> Unit = {},
    onRunNow: () -> Unit = {},
) {
    DesignDialog(
        title = "Автобэкап",
        onDismiss = onDismiss,
        modifier = Modifier.testTag(AutoBackupTags.DIALOG),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Раз в неделю сохранять библиотеку в файл",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.testTag(AutoBackupTags.TOGGLE),
                    )
                }

                Text(
                    text = "Папка: ${state.folder}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(AutoBackupTags.FOLDER),
                )
                Text(
                    // The private folder is deleted together with the app, so a backup
                    // there survives a mistake inside the app but not a lost phone. Saying
                    // so is the whole reason the SAF button is offered.
                    text = if (state.customFolder) {
                        "Папка останется доступной и после переустановки приложения. " +
                            "Хранятся последние три файла."
                    } else {
                        "Память приложения исчезнет вместе с приложением. Выберите папку " +
                            "на телефоне или в облаке, чтобы бэкап пережил переустановку. " +
                            "Хранятся последние три файла."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogTextButton(
                        label = if (state.customFolder) "Другая папка" else "Выбрать папку",
                        onClick = onPickFolder,
                        modifier = Modifier.testTag(AutoBackupTags.PICK_FOLDER),
                    )
                    if (state.customFolder) {
                        DialogTextButton(
                            label = "В память приложения",
                            onClick = onUseAppFolder,
                            modifier = Modifier.testTag(AutoBackupTags.APP_FOLDER),
                        )
                    }
                }

                Text(
                    text = state.lastBackupAt?.let { at ->
                        val moment = formatBackupTime(at)
                        val where = state.lastBackupLocation
                        if (where == null) "Последний бэкап: $moment"
                        else "Последний бэкап: $moment · $where"
                    } ?: "Бэкапов ещё не было",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(AutoBackupTags.LAST),
                )

                state.message?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(AutoBackupTags.MESSAGE),
                    )
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                label = "Готово",
                onClick = onDismiss,
                modifier = Modifier.testTag(AutoBackupTags.CLOSE),
            )
        },
        dismissButton = {
            DialogTextButton(
                label = if (state.busy) "Сохраняю…" else "Сделать сейчас",
                onClick = onRunNow,
                enabled = !state.busy,
                modifier = Modifier.testTag(AutoBackupTags.RUN_NOW),
            )
        },
    )
}
