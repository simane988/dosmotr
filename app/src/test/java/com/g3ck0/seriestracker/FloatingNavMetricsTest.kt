package com.g3ck0.seriestracker

import androidx.compose.ui.unit.dp
import com.g3ck0.seriestracker.ui.FloatingNavMetrics
import com.g3ck0.seriestracker.ui.fabContentClearance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule bug-6 turns on: in portrait the pill spans most of the width and a corner FAB
 * has to be lifted over it, in landscape it does not and the FAB belongs at the bottom
 * edge, where it stops covering the buttons of the first card.
 */
class FloatingNavMetricsTest {

    @Test
    fun aFabIsLiftedOverThePillWhenThereIsNoRoomBesideIt() {
        // Portrait: 411 dp wide, the pill takes ~300 of them.
        assertFalse(FloatingNavMetrics(space = 104.dp, freeWidth = 95.dp).fabFitsBeside)
    }

    @Test
    fun aFabSitsBesideThePillOnAWideScreen() {
        // Landscape: 914 dp wide, everything right of the pill is free.
        assertTrue(FloatingNavMetrics(space = 104.dp, freeWidth = 598.dp).fabFitsBeside)
    }

    @Test
    fun theUnmeasuredDefaultKeepsContentClearOfThePill() {
        // Used for the first frame and by screens hosted without AppRoot; a zero here
        // would drop the last row of every list under the bar.
        assertTrue(FloatingNavMetrics().space > 0.dp)
        assertFalse(FloatingNavMetrics().fabFitsBeside)
    }

    /**
     * feature-3: the corner FAB is drawn over the library list, so content that only
     * clears the pill still ends up under the button — that is what covered the corner of
     * the last card.
     */
    @Test
    fun contentUnderTheFabClearsTheButtonItselfNotJustThePill() {
        // Portrait: both clearances are the same 120 dp, and 120 dp is not enough.
        val clearance = fabContentClearance(navClearance = 120.dp, fabClearance = 120.dp)
        assertTrue("$clearance leaves the last card under the FAB", clearance >= 176.dp)
    }

    /** Landscape: the FAB drops to the bottom edge, and the pill is then the taller one. */
    @Test
    fun contentNeverClearsLessThanThePill() {
        assertTrue(fabContentClearance(navClearance = 120.dp, fabClearance = 32.dp) >= 120.dp)
    }
}
