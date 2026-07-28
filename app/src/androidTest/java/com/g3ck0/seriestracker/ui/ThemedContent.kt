package com.g3ck0.seriestracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.g3ck0.seriestracker.ui.theme.SeriesTrackerTheme

/**
 * Renders test content the way the app actually renders it.
 *
 * A bare `setContent` gets Compose's default MaterialTheme, which is light — so tests
 * would silently exercise a colour scheme the app never uses.
 *
 * Dynamic colour stays off here: it derives the scheme from the device wallpaper, which
 * would make results depend on whose phone the suite runs on.
 */
fun ComposeContentTestRule.setThemedContent(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent {
        SeriesTrackerTheme(darkTheme = darkTheme, dynamicColor = false, content = content)
    }
}
