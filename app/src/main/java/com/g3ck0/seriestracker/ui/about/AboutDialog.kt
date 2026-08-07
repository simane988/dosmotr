package com.g3ck0.seriestracker.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.BuildConfig
import com.g3ck0.seriestracker.R
import com.g3ck0.seriestracker.ui.common.DesignDialog
import com.g3ck0.seriestracker.ui.common.DialogTextButton

object AboutTags {
    const val DIALOG = "about:dialog"
    const val VERSION = "about:version"
    const val REPO = "about:repo"
    const val TMDB_NOTICE = "about:tmdb"
    const val CLOSE = "about:close"
    const val DONATE_BLOCK = "about:donate"
    const val DONATE_LINK = "about:donate:link"
    const val DONATE_SBP = "about:donate:sbp"
    const val DONATE_CRYPTO = "about:donate:crypto"
    const val DONATE_COPY_SBP = "about:donate:sbp:copy"
    const val DONATE_COPY_CRYPTO = "about:donate:crypto:copy"
    const val CRASH_REPORTS = "about:crashReports"
}

/**
 * Resolves the ViewModel behind the crash-report switch; everything visible is
 * [AboutContent]'s, which is what the UI tests drive.
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AboutContent(
        state = state,
        onDismiss = onDismiss,
        onCrashReportsChange = viewModel::setCrashReportsEnabled,
    )
}

/**
 * Everything the app has to say about itself, including the TMDB attribution.
 *
 * The attribution is not optional and not a footnote: while TMDB is the catalogue behind
 * the backend, their terms require *this app* to carry their logo and the sentence below,
 * verbatim and in English. Hiding the provider behind our own backend removes the
 * coupling, not the obligation.
 */
@Composable
fun AboutContent(
    state: AboutUiState = AboutUiState(),
    onDismiss: () -> Unit = {},
    onCrashReportsChange: (Boolean) -> Unit = {},
) {
    DesignDialog(
        title = "О приложении",
        onDismiss = onDismiss,
        modifier = Modifier.testTag(AboutTags.DIALOG),
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text("Трекер сериалов и фильмов. Библиотека хранится на устройстве.")

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow(
                        label = "Версия",
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        tag = AboutTags.VERSION,
                    )
                    InfoRow(label = "Пакет", value = BuildConfig.APPLICATION_ID)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Открытый исходный код:")
                RepoLink()

                // What makes the store build safe is not this branch but what it has to
                // show: DONATE_* are compiled in as empty strings there, so the addresses
                // are not in the APK at all, whatever R8 does with the code around them.
                if (DonateConfig.visible) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DonateSection()
                }

                // Absent, not disabled, where nothing can be sent: a switch over an empty
                // implementation would suggest the `direct` build reports something when
                // it does not carry the code to.
                if (state.crashReportsAvailable) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CrashReportsSwitch(
                        enabled = state.crashReportsEnabled,
                        onChange = onCrashReportsChange,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_tmdb_logo),
                        contentDescription = "TMDB",
                        modifier = Modifier.height(12.dp),
                    )
                    Text(
                        text = TMDB_DISCLAIMER,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag(AboutTags.TMDB_NOTICE),
                    )
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                label = "Закрыть",
                onClick = onDismiss,
                modifier = Modifier.testTag(AboutTags.CLOSE),
            )
        },
    )
}

/** Required by TMDB word for word, in English — do not translate or reword. */
private const val TMDB_DISCLAIMER =
    "This application uses TMDB and the TMDB APIs but is not endorsed, certified, " +
        "or otherwise approved by TMDB."

private const val REPO_URL = "https://github.com/simane988/dosmotr"

/**
 * The one thing in this dialog that can be changed rather than read.
 *
 * The wording names what is sent *and* what is not, because "данные остаются на
 * устройстве" is a promise the app makes in its store description and in its privacy
 * policy — a switch that only said «отчёты о падениях» would leave the reader guessing
 * whether the titles go too. They do not: what leaves is a stack trace and eleven counters
 * with no content of their own (see `TelemetryEvent`).
 */
@Composable
private fun CrashReportsSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Отправлять отчёты о падениях",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.testTag(AboutTags.CRASH_REPORTS),
            )
        }
        Text(
            text = "Отправляются только стек падения и обезличенные счётчики действий. " +
                "Названия тайтлов, поисковые запросы, заметки и оценки не отправляются никогда.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens in the browser; the visible text is the URL without its scheme. */
@Composable
private fun RepoLink() {
    // Underlined as well as tinted: on a dynamic palette the primary colour can land
    // close enough to the body text that colour alone does not read as a link.
    val style = MaterialTheme.typography.bodyMedium.toSpanStyle().copy(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    Text(
        text = buildAnnotatedString {
            withLink(LinkAnnotation.Url(REPO_URL, TextLinkStyles(style = style))) {
                append(REPO_URL.substringAfter("://"))
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(AboutTags.REPO),
    )
}

@Composable
private fun InfoRow(label: String, value: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 16.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        )
    }
}
