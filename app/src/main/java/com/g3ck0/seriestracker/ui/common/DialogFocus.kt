package com.g3ck0.seriestracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Drops focus once [dialogOpen] goes false.
 *
 * Closing a Compose dialog hands focus back to the window, where the first focusable
 * node takes it — on a screen whose first node is a text field that silently raises the
 * keyboard. The keyboard then covers the bottom navigation bar and swallows taps on it,
 * which reads as "the tab is dead" rather than "the keyboard is open".
 *
 * The frame wait matters: focus returns after the dialog leaves composition, so clearing
 * it in the same frame would be undone.
 */
@Composable
fun ClearFocusWhenDialogCloses(dialogOpen: Boolean) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(dialogOpen) {
        if (!dialogOpen) {
            withFrameNanos { }
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
