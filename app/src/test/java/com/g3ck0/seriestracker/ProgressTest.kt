package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.ui.search.parseSeasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    private fun tv(watched: Int, total: Int) = TitleWithProgress(
        title = TitleEntity(id = "tv_1", tmdbId = 1, mediaType = MediaType.TV, name = "Test"),
        episodeCount = total,
        watchedCount = watched,
    )

    private fun movie(watched: Boolean) = TitleWithProgress(
        title = TitleEntity(
            id = "movie_1",
            tmdbId = 1,
            mediaType = MediaType.MOVIE,
            name = "Test",
            movieWatched = watched,
        ),
        episodeCount = 0,
        watchedCount = 0,
    )

    @Test
    fun `tv progress`() {
        assertEquals(0.5f, tv(5, 10).progress, 0.001f)
        assertEquals(5, tv(5, 10).remaining)
        assertFalse(tv(5, 10).isCompleted)
        assertTrue(tv(10, 10).isCompleted)
    }

    @Test
    fun `title without episodes is not completed`() {
        assertEquals(0f, tv(0, 0).progress, 0.001f)
        assertFalse(tv(0, 0).isCompleted)
    }

    @Test
    fun `movies are all or nothing`() {
        assertEquals(0f, movie(false).progress, 0.001f)
        assertEquals(1, movie(false).remaining)
        assertEquals(1f, movie(true).progress, 0.001f)
        assertEquals(0, movie(true).remaining)
        assertTrue(movie(true).isCompleted)
    }

    @Test
    fun `season spec parsing`() {
        assertEquals(listOf(12, 10, 8), parseSeasons("12, 10, 8"))
        assertEquals(listOf(12, 10), parseSeasons("12 10"))
        assertEquals(emptyList<Int>(), parseSeasons("абв"))
        assertEquals(listOf(6), parseSeasons("6, 0, -3"))
    }
}
