package com.g3ck0.seriestracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Track colour for every progress bar in the app.
 *
 * Material3's default is `secondaryContainer`, which on a dynamic (Material You) palette
 * is a mid-tone: against `primary` it drops to ~2.1:1, below the 3:1 WCAG 1.4.11 asks for
 * non-text UI, while contrasting strongly with the card behind it — so an empty bar reads
 * as a full one. `surfaceVariant` sits next to the card surface in both schemes instead,
 * which keeps the filled part the only thing the eye picks up.
 */
private val progressTrackColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

/**
 * Determinate progress bar. Wraps `LinearProgressIndicator` only to pin the track colour,
 * so the three screens that show progress cannot drift apart again.
 */
@Composable
fun ProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    LinearProgressIndicator(
        progress = progress,
        strokeCap = StrokeCap.Round,
        trackColor = progressTrackColor,
        modifier = modifier.height(height),
    )
}

/** Indeterminate counterpart, for the search and refresh bars. */
@Composable
fun IndeterminateProgressBar(modifier: Modifier = Modifier, height: Dp = 4.dp) {
    LinearProgressIndicator(
        trackColor = progressTrackColor,
        modifier = modifier.height(height),
    )
}

/** Extended FAB from the mock: 56dp tall pill on primaryContainer, icon plus label. */
@Composable
fun ExtendedActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier.height(56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text = label, fontSize = 15.sp)
        }
    }
}

/**
 * Snackbar sits above the floating navigation pill rather than at the screen edge,
 * otherwise the bar covers it. It also clears the keyboard: AppRoot already consumes
 * the IME inset, but a screen shown outside it (tests, previews) would otherwise draw
 * the snackbar underneath an open keyboard.
 */
@Composable
fun SnackbarOverlay(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 104.dp),
    ) { data ->
        Snackbar(
            shape = RoundedCornerShape(4.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Dialog shell from the mock: 28dp corners on surfaceContainerHigh, 24sp title. */
@Composable
fun DesignDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(text = title, fontSize = 24.sp, lineHeight = 32.sp) },
        text = content?.let { { Column(Modifier.fillMaxWidth()) { it() } } },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = modifier,
    )
}

/** Text button used inside dialogs — primary coloured, 20dp pill. */
@Composable
fun DialogTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = color,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
