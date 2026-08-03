package com.g3ck0.seriestracker.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.ui.common.DesignChip
import com.g3ck0.seriestracker.ui.common.DesignDialog
import com.g3ck0.seriestracker.ui.common.DialogTextButton
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.label
import com.g3ck0.seriestracker.ui.common.seasonsLabel
import java.time.Year

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
 * Manual entry for titles the catalogue does not have (or when offline).
 * Seasons are described as a comma separated episode count per season: "12, 10, 8".
 */
@Composable
fun ManualAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: MediaType, episodesPerSeason: List<Int>, runtime: Int, year: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MediaType.TV) }
    var seasonsSpec by remember { mutableStateOf("10") }
    var runtime by remember { mutableStateOf("45") }
    var runtimeTouched by remember { mutableStateOf(false) }
    var year by remember { mutableStateOf("") }
    var showNameError by remember { mutableStateOf(false) }

    val episodesPerSeason = parseSeasons(seasonsSpec)
    val valid = name.isNotBlank() && (type == MediaType.MOVIE || episodesPerSeason.isNotEmpty())

    DesignDialog(
        title = "Добавить вручную",
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelledField(
                    label = "Название",
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    isError = showNameError,
                    fieldModifier = Modifier.testTag(ManualAddTags.NAME),
                )
                if (showNameError) {
                    Text(
                        text = "Введи название",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaType.entries.forEach { option ->
                        DesignChip(
                            label = option.label,
                            selected = type == option,
                            onClick = {
                                type = option
                                if (!runtimeTouched) {
                                    runtime = if (option == MediaType.MOVIE) "120" else "45"
                                }
                            },
                            modifier = Modifier.testTag(ManualAddTags.type(option)),
                        )
                    }
                }
                if (type == MediaType.TV) {
                    Column {
                        LabelledField(
                            label = "Серий по сезонам",
                            value = seasonsSpec,
                            onValueChange = { seasonsSpec = it },
                            fieldModifier = Modifier.testTag(ManualAddTags.SEASONS),
                        )
                        Text(
                            text = "Через запятую, по одному числу на сезон — например: 12, 10, 8",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                        if (episodesPerSeason.isNotEmpty()) {
                            Text(
                                text = "${seasonsLabel(episodesPerSeason.size)}, всего " +
                                    episodesLabel(episodesPerSeason.sum()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 2.dp)
                                    .testTag(ManualAddTags.SEASONS_SUMMARY),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelledField(
                        label = if (type == MediaType.TV) "Мин/серия" else "Длительность",
                        value = runtime,
                        onValueChange = {
                            runtime = it.filter(Char::isDigit).take(4)
                            runtimeTouched = true
                        },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        fieldModifier = Modifier.testTag(ManualAddTags.RUNTIME),
                    )
                    LabelledField(
                        label = "Год",
                        value = year,
                        onValueChange = { year = it.filter(Char::isDigit).take(4) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        fieldModifier = Modifier.testTag(ManualAddTags.YEAR),
                    )
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                label = "Добавить",
                enabled = true,
                onClick = {
                    if (valid) {
                        onConfirm(
                            name.trim(),
                            type,
                            episodesPerSeason,
                            runtime.toIntOrNull() ?: 0,
                            year.takeIf { it.length == 4 && isValidYear(it) },
                        )
                    } else {
                        showNameError = name.isBlank()
                    }
                },
                modifier = Modifier.testTag(ManualAddTags.CONFIRM),
            )
        },
        dismissButton = {
            DialogTextButton(
                label = "Отмена",
                onClick = onDismiss,
                modifier = Modifier.testTag(ManualAddTags.CANCEL),
            )
        },
    )
}

/** Outlined field with a caption above the value, as drawn in the mock. */
@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // The tag belongs on the editable node so performTextInput can reach it.
    fieldModifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
    }
}

internal fun parseSeasons(spec: String): List<Int> =
    spec.split(',', ';', ' ')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }

private const val EARLIEST_YEAR = 1888
private const val YEARS_INTO_THE_FUTURE = 5

/** Rejects placeholder years like 0000/9999 typed into a 4-digit field. */
internal fun isValidYear(year: String, currentYear: Int = Year.now().value): Boolean {
    val value = year.toIntOrNull() ?: return false
    return value in EARLIEST_YEAR..(currentYear + YEARS_INTO_THE_FUTURE)
}
