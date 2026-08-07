package com.g3ck0.seriestracker.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.g3ck0.seriestracker.BuildConfig

/**
 * Where donations may be shown, and what to.
 *
 * Two independent gates, and the block needs both:
 *
 *  * `DONATIONS_ENABLED` is the flavour (see the `productFlavors` block in
 *    app/build.gradle.kts). A `store` build must not so much as mention a wallet.
 *  * the destinations themselves come from local.properties, which exists on the author's
 *    machine only — a `direct` build without them, which is what CI builds, has nothing to
 *    show and shows nothing rather than an empty heading.
 */
object DonateConfig {
    val url: String = BuildConfig.DONATE_URL
    val sbp: String = BuildConfig.DONATE_SBP
    val usdt: String = BuildConfig.DONATE_USDT

    val visible: Boolean = donationsVisible(BuildConfig.DONATIONS_ENABLED, url, sbp, usdt)
}

/** Split out from [DonateConfig] so the rule itself can be tested without a build variant. */
internal fun donationsVisible(enabled: Boolean, vararg destinations: String): Boolean =
    enabled && destinations.any { it.isNotBlank() }

/**
 * The «Поддержать автора» block of the about dialog.
 *
 * The wording is constrained, and not by taste (product/09-donations.md): nothing is given
 * in return — no feature, no badge, not even a thank-you screen — because the moment a
 * donation buys something it stops being a gift and becomes a payment for a service. No
 * amounts are named for the same reason, and the text is about supporting the *author*
 * rather than the app or a particular feature.
 */
@Composable
fun DonateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val copy: (String) -> Unit = { value ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Досмотр", value))
        // Android 13 shows its own copy confirmation, so a toast there is the same message
        // twice. Below it nothing is shown at all and the tap looks like it did nothing.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, "Адрес скопирован", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().testTag(AboutTags.DONATE_BLOCK),
    ) {
        Text(
            text = "Поддержать автора",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(DONATE_TEXT, style = MaterialTheme.typography.bodyMedium)

        if (DonateConfig.url.isNotBlank()) {
            DonateLink(DonateConfig.url)
        }
        if (DonateConfig.sbp.isNotBlank()) {
            CopyableRow(
                label = "СБП",
                value = DonateConfig.sbp,
                tag = AboutTags.DONATE_SBP,
                copyTag = AboutTags.DONATE_COPY_SBP,
                onCopy = copy,
            )
        }
        if (DonateConfig.usdt.isNotBlank()) {
            CopyableRow(
                label = "USDT (TRC-20)",
                value = DonateConfig.usdt,
                tag = AboutTags.DONATE_CRYPTO,
                copyTag = AboutTags.DONATE_COPY_CRYPTO,
                onCopy = copy,
            )
        }
    }
}

private const val DONATE_TEXT =
    "«Досмотр» бесплатный и таким останется. Если приложение оказалось полезным, автору " +
        "можно перевести любую сумму — это никак не влияет на функции приложения."

/** Opens the payment page in the browser, styled like [AboutTags.REPO] above it. */
@Composable
private fun DonateLink(url: String) {
    val style = MaterialTheme.typography.bodyMedium.toSpanStyle().copy(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    Text(
        text = buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style = style))) {
                append("Перевести")
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(AboutTags.DONATE_LINK),
    )
}

/**
 * A destination nobody is going to retype by hand. The value is truncated on screen — a
 * TRC-20 address is 34 characters and the dialog is not that wide — so the copy button is
 * the only way it is meant to be used, not a convenience.
 */
@Composable
private fun CopyableRow(
    label: String,
    value: String,
    tag: String,
    copyTag: String,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(tag),
        )
        IconButton(onClick = { onCopy(value) }, modifier = Modifier.testTag(copyTag)) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Скопировать: $label",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
