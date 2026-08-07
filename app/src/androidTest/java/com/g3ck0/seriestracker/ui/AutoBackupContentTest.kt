package com.g3ck0.seriestracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.g3ck0.seriestracker.ui.backup.AutoBackupContent
import com.g3ck0.seriestracker.ui.backup.AutoBackupTags
import com.g3ck0.seriestracker.ui.backup.AutoBackupUiState
import com.g3ck0.seriestracker.ui.library.LibraryContent
import com.g3ck0.seriestracker.ui.library.LibraryTags
import com.g3ck0.seriestracker.ui.library.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The stateless half of the dialog, driven directly — no Hilt, no WorkManager. */
class AutoBackupContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun theSwitchShowsWhetherTheBackupRuns() {
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(enabled = true, folder = "Память приложения"))
        }

        compose.onNodeWithTag(AutoBackupTags.TOGGLE).assertIsOn()
    }

    @Test
    fun aSwitchedOffBackupSaysSo() {
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(enabled = false))
        }

        compose.onNodeWithTag(AutoBackupTags.TOGGLE).assertIsOff()
    }

    @Test
    fun theSwitchReportsTheNewValue() {
        var toggled: Boolean? = null
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(enabled = true), onToggle = { toggled = it })
        }

        compose.onNodeWithTag(AutoBackupTags.TOGGLE).performClick()

        assertEquals(false, toggled)
    }

    @Test
    fun theDialogNamesTheFolderTheNextBackupGoesTo() {
        compose.setThemedContent {
            AutoBackupContent(
                state = AutoBackupUiState(folder = "Dosmotr", customFolder = true),
            )
        }

        compose.onNodeWithText("Папка: Dosmotr").assertIsDisplayed()
    }

    @Test
    fun theDateOfTheLastBackupIsShown() {
        compose.setThemedContent {
            AutoBackupContent(
                state = AutoBackupUiState(
                    lastBackupAt = millis(2026, 8, 3, 14, 20),
                    lastBackupLocation = "Память приложения",
                ),
            )
        }

        compose.onNodeWithTag(AutoBackupTags.LAST)
            .assertIsDisplayed()
        compose.onNodeWithText("Последний бэкап: 3 августа, 14:20 · Память приложения")
            .assertIsDisplayed()
    }

    @Test
    fun withoutABackupItSaysThereIsNone() {
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState())
        }

        compose.onNodeWithText("Бэкапов ещё не было").assertIsDisplayed()
    }

    @Test
    fun makeItNowFiresTheCallback() {
        var runs = 0
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(), onRunNow = { runs++ })
        }

        compose.onNodeWithTag(AutoBackupTags.RUN_NOW).performClick()

        assertEquals(1, runs)
    }

    /** The private folder is the only one that can be given back, so the button is conditional. */
    @Test
    fun theAppFolderButtonAppearsOnlyWithAFolderOfOnesOwn() {
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(customFolder = false))
        }

        compose.onNodeWithTag(AutoBackupTags.PICK_FOLDER).assertIsDisplayed()
        compose.onNodeWithTag(AutoBackupTags.APP_FOLDER).assertDoesNotExist()
    }

    @Test
    fun aChosenFolderCanBeGivenBack() {
        var back = 0
        compose.setThemedContent {
            AutoBackupContent(
                state = AutoBackupUiState(customFolder = true, folder = "Dosmotr"),
                onUseAppFolder = { back++ },
            )
        }

        compose.onNodeWithTag(AutoBackupTags.APP_FOLDER).performClick()

        assertEquals(1, back)
    }

    @Test
    fun whatARunSaidIsShownInTheDialog() {
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(message = "Сохранено 7 тайтлов"))
        }

        compose.onNodeWithTag(AutoBackupTags.MESSAGE).assertIsDisplayed()
        compose.onNodeWithText("Сохранено 7 тайтлов").assertIsDisplayed()
    }

    /**
     * The way in. The dialog resolves a ViewModel of its own, so `LibraryScreen` hosts it
     * and all the stateless library owes is the menu entry that asks for it — which is why
     * this one test lives here rather than in `LibraryContentTest`.
     */
    @Test
    fun theLibraryMenuOffersAutoBackup() {
        var opened = 0
        compose.setThemedContent {
            LibraryContent(
                state = LibraryUiState(loading = false),
                onAutoBackup = { opened++ },
            )
        }

        compose.onNodeWithTag(LibraryTags.TOP_MENU).performClick()
        compose.onNodeWithText("Автобэкап").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.AUTO_BACKUP).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun aRunInFlightDisablesItsOwnButton() {
        var runs = 0
        compose.setThemedContent {
            AutoBackupContent(state = AutoBackupUiState(busy = true), onRunNow = { runs++ })
        }

        compose.onNodeWithTag(AutoBackupTags.RUN_NOW).performClick()

        assertTrue(runs == 0)
    }
}
