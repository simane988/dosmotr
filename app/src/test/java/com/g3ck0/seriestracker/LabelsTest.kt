package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.ui.common.episodeCode
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.formatAirDate
import com.g3ck0.seriestracker.ui.common.formatBackupTime
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.moviesLabel
import com.g3ck0.seriestracker.ui.common.nextLabel
import com.g3ck0.seriestracker.ui.common.seasonsLabel
import com.g3ck0.seriestracker.fake.progressOf
import com.g3ck0.seriestracker.fake.tvTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class LabelsTest {

    @Test
    fun `zero minutes`() {
        assertEquals("0 мин", formatMinutes(0))
        assertEquals("0 мин", formatMinutes(-5))
    }

    @Test
    fun `minutes only`() {
        assertEquals("45 мин", formatMinutes(45))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals("2 ч 30 мин", formatMinutes(150))
    }

    @Test
    fun `days roll over`() {
        assertEquals("1 д 1 ч 1 мин", formatMinutes(24 * 60 + 61))
    }

    @Test
    fun `russian plurals`() {
        assertEquals("1 серия", episodesLabel(1))
        assertEquals("2 серии", episodesLabel(2))
        assertEquals("5 серий", episodesLabel(5))
        assertEquals("11 серий", episodesLabel(11))
        assertEquals("21 серия", episodesLabel(21))
        assertEquals("112 серий", episodesLabel(112))

        assertEquals("1 фильм", moviesLabel(1))
        assertEquals("3 фильма", moviesLabel(3))
        assertEquals("0 фильмов", moviesLabel(0))
        assertEquals("11 фильмов", moviesLabel(11))

        assertEquals("1 сезон", seasonsLabel(1))
        assertEquals("3 сезона", seasonsLabel(3))
        assertEquals("0 сезонов", seasonsLabel(0))
        assertEquals("11 сезонов", seasonsLabel(11))
        assertEquals("31 сезон", seasonsLabel(31))
    }

    @Test
    fun `air dates are written the Russian way`() {
        assertEquals("20 января 2008", formatAirDate("2008-01-20"))
        assertEquals("1 сентября 2021", formatAirDate("2021-09-01"))
        assertEquals("31 декабря 1999", formatAirDate("1999-12-31"))
    }

    /**
     * The moment is built through the device's own zone, so the assertion holds wherever
     * the suite runs — what is pinned is the language, not the clock.
     */
    @Test
    fun `the last backup is dated to the minute`() {
        val moment = LocalDateTime.of(2026, 8, 3, 14, 20)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals("3 августа, 14:20", formatBackupTime(moment))
    }

    /** The device language must not change the label — the UI is Russian either way. */
    @Test
    fun `air dates ignore the default locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("10 февраля 2008", formatAirDate("2008-02-10"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** A broken date is dropped by the caller, which only draws a non-empty label. */
    @Test
    fun `unparseable air dates come back empty`() {
        assertEquals("", formatAirDate(""))
        assertEquals("", formatAirDate("   "))
        assertEquals("", formatAirDate("2008"))
        assertEquals("", formatAirDate("20.01.2008"))
        assertEquals("", formatAirDate("2008-13-40"))
    }

    @Test
    fun `air dates tolerate surrounding whitespace`() {
        assertEquals("20 января 2008", formatAirDate(" 2008-01-20 "))
    }

    @Test
    fun `episode code is zero padded`() {
        assertEquals("S01E05", episodeCode(1, 5))
        assertEquals("S12E120", episodeCode(12, 120))
    }

    @Test
    fun `next label joins the code and the episode name`() {
        val row = progressOf(
            tvTitle(),
            episodeCount = 10,
            watchedCount = 3,
            nextSeason = 1,
            nextEpisode = 4,
            nextName = "Двойники",
        )

        assertEquals("S01E04 · Двойники", row.nextLabel)
    }

    /** Backend episodes often come without a name; the code alone is still useful. */
    @Test
    fun `next label falls back to the code alone without a name`() {
        val row = progressOf(tvTitle(), nextSeason = 2, nextEpisode = 1, nextName = "  ")

        assertEquals("S02E01", row.nextLabel)
    }

    @Test
    fun `next label is null when there is no next episode`() {
        assertNull(progressOf(tvTitle(), episodeCount = 4, watchedCount = 4).nextLabel)
    }
}
