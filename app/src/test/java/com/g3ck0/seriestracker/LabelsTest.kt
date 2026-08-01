package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.ui.common.episodeCode
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.moviesLabel
import com.g3ck0.seriestracker.ui.common.nextLabel
import com.g3ck0.seriestracker.fake.progressOf
import com.g3ck0.seriestracker.fake.tvTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
