package com.g3ck0.seriestracker.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.g3ck0.seriestracker.BuildConfig
import com.g3ck0.seriestracker.ui.about.AboutContent
import com.g3ck0.seriestracker.ui.about.AboutTags
import com.g3ck0.seriestracker.ui.about.AboutUiState
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * One suite for both distribution flavours. What it asserts is the *rule*, not a fixed
 * answer: donations appear exactly when the flavour allows them and the build was given
 * somewhere to send them, so the same test passes on `store` (never), on CI's `direct`
 * (no local.properties, so still never) and on a locally configured `direct` (always).
 *
 * It drives [AboutContent] rather than `AboutDialog`, which resolves a Hilt ViewModel for
 * the crash-report switch — the split every screen in this app follows.
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
        compose.setThemedContent { AboutContent() }

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

        compose.setThemedContent { AboutContent() }
        compose.onNodeWithTag(AboutTags.DONATE_COPY_CRYPTO).performClick()

        assertEquals(BuildConfig.DONATE_USDT, clipboardText())
    }

    // --- feature-19: the project site and the privacy policy ---

    /**
     * Both links are there whatever the flavour, and that is the point rather than an
     * omission: `store` carries no donation block at all, so «Сайт проекта» is the only
     * route from the app to a page that may name a destination, and the policy has to be
     * reachable from inside the app because the store listing promises it is.
     */
    @Test
    fun theSiteAndThePolicyAreLinkedInBothFlavours() {
        compose.setThemedContent { AboutContent() }

        compose.onNodeWithTag(AboutTags.SITE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(AboutTags.PRIVACY).performScrollTo().assertIsDisplayed()
    }

    /**
     * The wording of that link is a store-policy decision, not a style one: a button asking
     * for money inside a Play build reads as circumventing Google's billing, while a link
     * to the project's own page does not (product/09-donations.md, §2). So the label is
     * asserted exactly — «Поддержать» must not creep into it.
     */
    @Test
    fun theSiteLinkNamesTheProjectRatherThanADonation() {
        compose.setThemedContent { AboutContent() }

        compose.onNodeWithTag(AboutTags.SITE).performScrollTo()
            .assertTextEquals("Сайт проекта")
        compose.onNodeWithTag(AboutTags.PRIVACY).performScrollTo()
            .assertTextEquals("Политика конфиденциальности")
    }

    @Test
    fun theTmdbAttributionIsUnaffected() {
        compose.setThemedContent { AboutContent() }

        compose.onNodeWithTag(AboutTags.TMDB_NOTICE).assertIsDisplayed()
        compose.onNodeWithTag(AboutTags.REPO).assertIsDisplayed()
        compose.onNodeWithTag(AboutTags.VERSION).assertIsDisplayed()
        compose.onNodeWithText("github.com/simane988/dosmotr").assertIsDisplayed()
        // TMDB requires this sentence verbatim, so the test spells it out in full. It used
        // to be asserted from LibraryContentTest, which opened the dialog itself; the
        // dialog is hosted by LibraryScreen now, so the sentence is checked here.
        compose.onNodeWithText(
            "This application uses TMDB and the TMDB APIs but is not endorsed, " +
                "certified, or otherwise approved by TMDB."
        ).assertIsDisplayed()
    }

    // --- feature-18: the crash-report switch ---

    /**
     * The switch exists only where something can be sent. In `direct` there is no Firebase
     * compiled in at all, so a switch there would be a promise about code that is absent.
     */
    @Test
    fun theSwitchFollowsWhatTheFlavourCanSend() {
        compose.setThemedContent {
            AboutContent(state = AboutUiState(crashReportsAvailable = BuildConfig.CRASH_REPORTING_AVAILABLE))
        }

        if (BuildConfig.CRASH_REPORTING_AVAILABLE) {
            compose.onNodeWithTag(AboutTags.CRASH_REPORTS).assertIsDisplayed()
            compose.onNodeWithText("Отправлять отчёты о падениях").assertIsDisplayed()
        } else {
            compose.onNodeWithTag(AboutTags.CRASH_REPORTS).assertDoesNotExist()
            compose.onNodeWithText("Отправлять отчёты о падениях").assertDoesNotExist()
        }
    }

    @Test
    fun theSwitchShowsTheStoredValueAndReportsAChange() {
        var changedTo: Boolean? = null
        compose.setThemedContent {
            AboutContent(
                state = AboutUiState(crashReportsAvailable = true, crashReportsEnabled = true),
                onCrashReportsChange = { changedTo = it },
            )
        }

        compose.onNodeWithTag(AboutTags.CRASH_REPORTS).assertIsOn().performClick()

        assertEquals(false, changedTo)
    }

    @Test
    fun aSwitchedOffBuildDrawsItOff() {
        compose.setThemedContent {
            AboutContent(state = AboutUiState(crashReportsAvailable = true, crashReportsEnabled = false))
        }

        compose.onNodeWithTag(AboutTags.CRASH_REPORTS).assertIsOff()
    }

    /**
     * The sentence under the switch is what the privacy policy and the Data Safety form
     * promise, in the one place a user will actually read it.
     */
    @Test
    fun theSwitchSaysWhatIsNotSent() {
        compose.setThemedContent {
            AboutContent(state = AboutUiState(crashReportsAvailable = true))
        }

        compose.onNodeWithText(
            "Отправляются только стек падения и обезличенные счётчики действий. " +
                "Названия тайтлов, поисковые запросы, заметки и оценки не отправляются никогда."
        ).assertIsDisplayed()
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
