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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun AboutDialog(onDismiss: () -> Unit) {
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
