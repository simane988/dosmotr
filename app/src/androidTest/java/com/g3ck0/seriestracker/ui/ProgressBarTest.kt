package com.g3ck0.seriestracker.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.g3ck0.seriestracker.ui.common.ProgressBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * bug-2: the track was left at Material3's default, `secondaryContainer`, which on a
 * dynamic palette is nearly as dark as `primary` — an empty bar then reads as a full one.
 *
 * Dynamic colour cannot be exercised here (the suite pins `dynamicColor = false`, and the
 * scheme would otherwise depend on the device wallpaper), so what this pins down is the
 * token: the unfilled part must be painted with `surfaceVariant`, not with whatever the
 * default happens to be. On the mock palette those two are different colours, so dropping
 * `trackColor` again fails the assertion.
 */
class ProgressBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val tag = "test:progress"

    @Test
    fun trackIsSurfaceVariantAndFillIsPrimary() = assertProgressBarColors(darkTheme = false)

    @Test
    fun trackIsSurfaceVariantAndFillIsPrimaryInDark() = assertProgressBarColors(darkTheme = true)

    private fun assertProgressBarColors(darkTheme: Boolean) {
        var primary = Color.Unspecified
        var surfaceVariant = Color.Unspecified
        compose.setThemedContent(darkTheme = darkTheme) {
            primary = MaterialTheme.colorScheme.primary
            surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            ProgressBar(
                progress = { 0.5f },
                modifier = Modifier.width(200.dp).testTag(tag),
                height = 8.dp,
            )
        }

        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        // The node is taller than the bar: Material3 pads a progress indicator's semantics
        // bounds out to a touch target, so the capture carries blank rows below the stripe.
        // Take the first row the fill is painted on rather than guessing at the middle.
        val filled = pixels.width / 10
        val y = (0 until pixels.height).firstOrNull { pixels[filled, it] == primary }
        assertNotNull("the filled part is not painted with primary anywhere", y)
        // Six tenths in is past the gap Material3 leaves after the indicator, and short of
        // the stop dot it draws at the far end — plain track.
        assertEquals(surfaceVariant, pixels[pixels.width * 6 / 10, y!!])
    }
}
