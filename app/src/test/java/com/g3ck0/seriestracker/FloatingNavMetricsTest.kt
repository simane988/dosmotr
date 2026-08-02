package com.g3ck0.seriestracker

import androidx.compose.ui.unit.dp
import com.g3ck0.seriestracker.ui.FloatingNavMetrics
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
}
