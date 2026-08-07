package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.ui.about.DonateConfig
import com.g3ck0.seriestracker.ui.about.donationsVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The donation block is legally constrained, not merely optional: a `store` build must not
 * carry a wallet address at all. Both halves of that rule are checked here.
 */
class DonationsTest {

    @Test
    fun `the flavour alone decides whether donations may be shown`() {
        assertFalse(donationsVisible(enabled = false, "https://pay.example", "+70000000000", "T…"))
        assertTrue(donationsVisible(enabled = true, "https://pay.example"))
    }

    @Test
    fun `an enabled build with nothing configured shows nothing`() {
        // The state of CI and of a fresh clone: no local.properties, so no destinations.
        assertFalse(donationsVisible(enabled = true))
        assertFalse(donationsVisible(enabled = true, "", "  "))
    }

    @Test
    fun `one destination is enough`() {
        assertTrue(donationsVisible(enabled = true, "", "", "TXYZ"))
        assertTrue(donationsVisible(enabled = true, "", "+70000000000", ""))
    }

    @Test
    fun `a store build carries no destinations at all`() {
        if (!BuildConfig.DONATIONS_ENABLED) {
            assertEquals("", DonateConfig.url)
            assertEquals("", DonateConfig.sbp)
            assertEquals("", DonateConfig.usdt)
            assertFalse(DonateConfig.visible)
        }
    }
}
