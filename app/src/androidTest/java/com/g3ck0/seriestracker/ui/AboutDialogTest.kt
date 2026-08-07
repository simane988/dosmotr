package com.g3ck0.seriestracker.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.g3ck0.seriestracker.BuildConfig
import com.g3ck0.seriestracker.ui.about.AboutDialog
import com.g3ck0.seriestracker.ui.about.AboutTags
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * One suite for both distribution flavours. What it asserts is the *rule*, not a fixed
 * answer: donations appear exactly when the flavour allows them and the build was given
 * somewhere to send them, so the same test passes on `store` (never), on CI's `direct`
 * (no local.properties, so still never) and on a locally configured `direct` (always).
 */
class AboutDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val destinations =
        listOf(BuildConfig.DONATE_URL, BuildConfig.DONATE_SBP, BuildConfig.DONATE_USDT)

    private val donationsExpected =
        BuildConfig.DONATIONS_ENABLED && destinations.any { it.isNotBlank() }

    @Test
    fun donationBlockFollowsTheDistributionFlavour() {
        compose.setThemedContent { AboutDialog(onDismiss = {}) }

        if (donationsExpected) {
            compose.onNodeWithTag(AboutTags.DONATE_BLOCK).assertIsDisplayed()
            compose.onNodeWithText("Поддержать автора").assertIsDisplayed()
        } else {
            compose.onNodeWithTag(AboutTags.DONATE_BLOCK).assertDoesNotExist()
            compose.onNodeWithText("Поддержать автора").assertDoesNotExist()
        }
    }

    @Test
    fun aStoreBuildCarriesNoDestinations() {
        // The legal half of the rule (259-FZ art. 14 §7): not "hidden in the UI" but "not
        // in the APK". If a destination ever leaks into the store flavour, this fails long
        // before a moderator finds it.
        assumeTrue(!BuildConfig.DONATIONS_ENABLED)

        assertEquals("", BuildConfig.DONATE_URL)
        assertEquals("", BuildConfig.DONATE_SBP)
        assertEquals("", BuildConfig.DONATE_USDT)
    }

    @Test
    fun theCopyButtonPutsTheAddressOnTheClipboard() {
        assumeTrue(donationsExpected && BuildConfig.DONATE_USDT.isNotBlank())

        compose.setThemedContent { AboutDialog(onDismiss = {}) }
        compose.onNodeWithTag(AboutTags.DONATE_COPY_CRYPTO).performClick()

        assertEquals(BuildConfig.DONATE_USDT, clipboardText())
    }

    @Test
    fun theTmdbAttributionIsUnaffected() {
        compose.setThemedContent { AboutDialog(onDismiss = {}) }

        compose.onNodeWithTag(AboutTags.TMDB_NOTICE).assertIsDisplayed()
        compose.onNodeWithTag(AboutTags.REPO).assertIsDisplayed()
    }

    /** The clipboard may only be touched from the main thread. */
    private fun clipboardText(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var text = ""
        instrumentation.runOnMainSync {
            val clipboard = instrumentation.targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        }
        return text
    }
}
