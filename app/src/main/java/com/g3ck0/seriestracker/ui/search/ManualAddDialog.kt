package com.g3ck0.seriestracker.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.ui.common.label

object ManualAddTags {
    const val NAME = "manual:name"
    const val SEASONS = "manual:seasons"
    const val SEASONS_SUMMARY = "manual:seasonsSummary"
    const val RUNTIME = "manual:runtime"
    const val YEAR = "manual:year"
    const val CONFIRM = "manual:confirm"
    const val CANCEL = "manual:cancel"
    fun type(type: MediaType) = "manual:type:${type.name}"
}

/**
 * Manual entry for titles TMDB does not have (or when offline).
 * Seasons are described as a comma separated episode count per season: "12, 10, 8".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: MediaType, episodesPerSeason: List<Int>, runtime: Int, year: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MediaType.TV) }
    var seasonsSpec by remember { mutableStateOf("10") }
    var runtime by remember { mutableStateOf("45") }
    var year by remember { mutableStateOf("") }

    val episodesPerSeason = parseSeasons(seasonsSpec)
    val valid = name.isNotBlank() && (type == MediaType.MOVIE || episodesPerSeason.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить вручную") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(ManualAddTags.NAME),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag(ManualAddTags.type(option)),
                        )
                    }
                }
                if (type == MediaType.TV) {
                    OutlinedTextField(
                        value = seasonsSpec,
                        onValueChange = { seasonsSpec = it },
                        label = { Text("Серий по сезонам") },
                        supportingText = {
                            Text(
                                text = if (episodesPerSeason.isEmpty()) {
                                    "Например: 12, 10, 8"
                                } else {
                                    "${episodesPerSeason.size} сезон(ов), всего ${episodesPerSeason.sum()} серий"
                                },
                                modifier = Modifier.testTag(ManualAddTags.SEASONS_SUMMARY),
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(ManualAddTags.SEASONS),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = runtime,
                        onValueChange = { runtime = it.filter(Char::isDigit).take(4) },
                        label = { Text(if (type == MediaType.TV) "Мин/серия" else "Длительность") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag(ManualAddTags.RUNTIME),
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter(Char::isDigit).take(4) },
                        label = { Text("Год") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag(ManualAddTags.YEAR),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                modifier = Modifier.testTag(ManualAddTags.CONFIRM),
                onClick = {
                    onConfirm(
                        name.trim(),
                        type,
                        episodesPerSeason,
                        runtime.toIntOrNull() ?: 0,
                        year.takeIf { it.length == 4 },
                    )
                },
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ManualAddTags.CANCEL),
            ) { Text("Отмена") }
        },
    )
}

internal fun parseSeasons(spec: String): List<Int> =
    spec.split(',', ';', ' ')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
