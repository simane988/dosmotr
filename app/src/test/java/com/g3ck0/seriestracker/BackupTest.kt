package com.g3ck0.seriestracker

import com.g3ck0.seriestracker.data.backup.BACKUP_VERSION
import com.g3ck0.seriestracker.data.backup.BackupFile
import com.g3ck0.seriestracker.data.backup.toBackup
import com.g3ck0.seriestracker.data.backup.toEntity
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.WatchStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private val title = TitleEntity(
        id = "tv_1399",
        tmdbId = 1399,
        mediaType = MediaType.TV,
        name = "Игра престолов",
        overview = "описание",
        posterPath = "/poster.jpg",
        year = "2011",
        status = WatchStatus.WATCHING,
        userRating = 9,
        tmdbRating = 8.4,
        runtimeMinutes = 60,
        addedAt = 1_700_000_000_000,
        lastWatchedAt = 1_700_000_500_000,
        notes = "смотрю с женой",
        episodesLoaded = true,
    )

    private val episodes = listOf(
        EpisodeEntity("tv_1399", 1, 1, "Зима близко", watched = true, watchedAt = 1_700_000_100_000),
        EpisodeEntity("tv_1399", 1, 2, "Дорога короля", runtimeMinutes = 56),
    )

    @Test
    fun `title survives a round trip`() {
        val restored = title.toBackup(episodes).toEntity()
        assertEquals(title, restored)
    }

    @Test
    fun `episodes survive a round trip`() {
        val backup = title.toBackup(episodes)
        val restored = backup.episodes.map { it.toEntity(backup.id) }
        assertEquals(episodes, restored)
    }

    @Test
    fun `json round trip keeps everything`() {
        val file = BackupFile(exportedAt = "2026-07-27T00:00:00Z", titles = listOf(title.toBackup(episodes)))
        val text = json.encodeToString(BackupFile.serializer(), file)
        val parsed = json.decodeFromString(BackupFile.serializer(), text)

        assertEquals(BACKUP_VERSION, parsed.version)
        assertEquals(1, parsed.titles.size)
        assertEquals(title, parsed.titles.first().toEntity())
        assertEquals(episodes, parsed.titles.first().episodes.map { it.toEntity("tv_1399") })
        assertTrue(text.contains("\"tmdb_id\""))
    }

    @Test
    fun `unknown enum names fall back instead of throwing`() {
        val text = """
            {"version":1,"titles":[{"id":"x","type":"HOLOGRAM","name":"N","status":"ЧТО-ТО"}]}
        """.trimIndent()
        val parsed = json.decodeFromString(BackupFile.serializer(), text).titles.first().toEntity()

        assertEquals(MediaType.TV, parsed.mediaType)
        assertEquals(WatchStatus.WATCHING, parsed.status)
    }

    @Test
    fun `movie without episodes is marked loaded`() {
        val movie = title.copy(id = "movie_550", mediaType = MediaType.MOVIE, movieWatched = true)
        val restored = movie.toBackup(emptyList()).toEntity()

        assertEquals(MediaType.MOVIE, restored.mediaType)
        assertTrue(restored.movieWatched)
        assertTrue(restored.episodesLoaded)
    }
}
