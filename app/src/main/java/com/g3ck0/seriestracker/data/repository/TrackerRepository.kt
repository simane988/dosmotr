package com.g3ck0.seriestracker.data.repository

import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.RemainingWatch
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.remote.SearchResultDto
import com.g3ck0.seriestracker.data.remote.CatalogApi
import com.g3ck0.seriestracker.di.BackendToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerRepository @Inject constructor(
    private val dao: TrackerDao,
    private val api: CatalogApi,
    @BackendToken private val apiKey: String,
) {

    val hasBackend: Boolean get() = apiKey.isNotBlank()

    // --- reads ---

    fun observeLibrary(): Flow<List<TitleWithProgress>> = dao.observeLibrary()

    fun observeTitle(titleId: String): Flow<TitleWithProgress?> = dao.observeTitle(titleId)

    fun observeEpisodes(titleId: String): Flow<List<EpisodeEntity>> = dao.observeEpisodes(titleId)

    fun observeTrackedIds(): Flow<Set<String>> = dao.observeTrackedIds().map { it.toSet() }

    fun observeStats(): Flow<WatchStats> = combine(
        dao.observeWatchedEpisodeCount(),
        dao.observeWatchedEpisodeMinutes(),
        dao.observeWatchedMovieCount(),
        dao.observeWatchedMovieMinutes(),
        // combine() tops out at five flows, so the library-wide counters travel as one
        // value of their own.
        combine(
            dao.observeCountByType(MediaType.TV),
            dao.observeCountByType(MediaType.MOVIE),
            dao.observeStatusCounts(),
            dao.observeRemaining(),
        ) { series, movies, statuses, remaining ->
            LibraryCounts(series, movies, statuses.associate { it.status to it.count }, remaining)
        },
    ) { episodes, episodeMinutes, movies, movieMinutes, counts ->
        WatchStats(
            watchedEpisodes = episodes,
            episodeMinutes = episodeMinutes,
            watchedMovies = movies,
            movieMinutes = movieMinutes,
            seriesCount = counts.seriesCount,
            movieCount = counts.movieCount,
            byStatus = counts.byStatus,
            remainingEpisodes = counts.remaining.episodes,
            remainingMinutes = counts.remaining.minutes,
        )
    }

    // --- discovery ---

    suspend fun search(query: String): Result<List<SearchItem>> = runCatching {
        requireBackend()
        api.searchMulti(query).results.mapNotNull { it.toSearchItem() }
    }

    suspend fun trending(): Result<List<SearchItem>> = runCatching {
        requireBackend()
        api.trending().results.mapNotNull { it.toSearchItem() }
    }

    // --- adding ---

    /** Saves the title immediately, then pulls details/episodes so the UI never waits on the network. */
    suspend fun add(item: SearchItem, status: WatchStatus = WatchStatus.PLANNED): Result<String> =
        runCatching {
            val id = item.id
            dao.upsertTitle(
                TitleEntity(
                    id = id,
                    catalogId = item.catalogId,
                    mediaType = item.mediaType,
                    name = item.name,
                    overview = item.overview,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    year = item.year,
                    status = status,
                    catalogRating = item.voteAverage,
                )
            )
            runCatching { refreshFromBackend(id) }
            id
        }

    /** Manual entry for anything the catalogue lacks. [episodesPerSeason] is empty for movies. */
    suspend fun addManual(
        name: String,
        mediaType: MediaType,
        episodesPerSeason: List<Int>,
        runtimeMinutes: Int,
        year: String?,
        status: WatchStatus = WatchStatus.PLANNED,
    ): String {
        val id = "local_${UUID.randomUUID()}"
        dao.upsertTitle(
            TitleEntity(
                id = id,
                catalogId = null,
                mediaType = mediaType,
                name = name,
                year = year,
                status = status,
                runtimeMinutes = runtimeMinutes,
                episodesLoaded = true,
            )
        )
        if (mediaType == MediaType.TV) {
            val episodes = episodesPerSeason.flatMapIndexed { index, count ->
                (1..count).map { number ->
                    EpisodeEntity(
                        titleId = id,
                        seasonNumber = index + 1,
                        episodeNumber = number,
                        name = "Серия $number",
                        runtimeMinutes = runtimeMinutes,
                    )
                }
            }
            dao.insertEpisodes(episodes)
        }
        return id
    }

    /**
     * Re-pulls metadata and episode lists. Episodes are inserted with IGNORE, so
     * newly aired ones show up while watched flags stay untouched.
     */
    suspend fun refreshFromBackend(titleId: String): Result<Unit> = runCatching {
        requireBackend()
        val title = dao.getTitle(titleId) ?: error("Тайтл не найден")
        val catalogId = title.catalogId ?: return@runCatching

        when (title.mediaType) {
            MediaType.TV -> {
                val details = api.tvDetails(catalogId)
                val perEpisode = details.episodeRunTime.firstOrNull() ?: DEFAULT_EPISODE_MINUTES
                dao.updateTitle(
                    title.copy(
                        name = details.name.ifBlank { title.name },
                        overview = details.overview.ifBlank { title.overview },
                        posterPath = details.posterPath ?: title.posterPath,
                        backdropPath = details.backdropPath ?: title.backdropPath,
                        year = details.firstAirDate?.take(4) ?: title.year,
                        catalogRating = details.voteAverage ?: title.catalogRating,
                        runtimeMinutes = perEpisode,
                        episodesLoaded = true,
                    )
                )
                // Season 0 is specials — skip it, it inflates progress for no reason.
                val seasons = details.seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                for (season in seasons) {
                    val fetched = runCatching { api.season(catalogId, season.seasonNumber) }.getOrNull()
                        ?: continue
                    dao.insertEpisodes(
                        fetched.episodes.map { episode ->
                            EpisodeEntity(
                                titleId = titleId,
                                seasonNumber = season.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                name = episode.name,
                                overview = episode.overview,
                                airDate = episode.airDate,
                                stillPath = episode.stillPath,
                                runtimeMinutes = episode.runtime ?: perEpisode,
                            )
                        }
                    )
                }
            }

            MediaType.MOVIE -> {
                val details = api.movieDetails(catalogId)
                dao.updateTitle(
                    title.copy(
                        name = details.title.ifBlank { title.name },
                        overview = details.overview.ifBlank { title.overview },
                        posterPath = details.posterPath ?: title.posterPath,
                        backdropPath = details.backdropPath ?: title.backdropPath,
                        year = details.releaseDate?.take(4) ?: title.year,
                        catalogRating = details.voteAverage ?: title.catalogRating,
                        runtimeMinutes = details.runtime ?: title.runtimeMinutes,
                        episodesLoaded = true,
                    )
                )
            }
        }
    }

    // --- progress ---

    suspend fun setEpisodeWatched(titleId: String, season: Int, episode: Int, watched: Boolean) {
        val now = System.currentTimeMillis()
        dao.setEpisodeWatched(titleId, season, episode, watched, if (watched) now else null)
        afterProgressChange(titleId, watched, now)
    }

    suspend fun setSeasonWatched(titleId: String, season: Int, watched: Boolean) {
        val now = System.currentTimeMillis()
        dao.setSeasonWatched(titleId, season, watched, if (watched) now else null)
        afterProgressChange(titleId, watched, now)
    }

    suspend fun setAllWatched(titleId: String, watched: Boolean) {
        val now = System.currentTimeMillis()
        dao.setAllWatched(titleId, watched, if (watched) now else null)
        afterProgressChange(titleId, watched, now)
    }

    suspend fun watchUpTo(titleId: String, season: Int, episode: Int) {
        val now = System.currentTimeMillis()
        dao.watchUpTo(titleId, season, episode, now)
        afterProgressChange(titleId, watched = true, now = now)
    }

    /** Quick "+1 серия" from the library list. No-op when nothing is left. */
    suspend fun markNextWatched(titleId: String): EpisodeEntity? {
        val next = dao.nextUnwatched(titleId) ?: return null
        setEpisodeWatched(titleId, next.seasonNumber, next.episodeNumber, watched = true)
        return next
    }

    suspend fun setMovieWatched(titleId: String, watched: Boolean) {
        val now = System.currentTimeMillis()
        dao.setMovieWatched(titleId, watched)
        dao.touchWatched(titleId, if (watched) now else null)
        dao.setStatus(titleId, if (watched) WatchStatus.COMPLETED else WatchStatus.PLANNED)
    }

    suspend fun setStatus(titleId: String, status: WatchStatus) = dao.setStatus(titleId, status)

    suspend fun setRating(titleId: String, rating: Int?) = dao.setRating(titleId, rating)

    suspend fun setNotes(titleId: String, notes: String) = dao.setNotes(titleId, notes)

    /**
     * Deletes the title and returns what was removed, so a caller can offer an undo.
     * The foreign key cascade takes the episodes with the title, hence the snapshot:
     * nothing else in the database remembers which ones were watched.
     */
    suspend fun delete(titleId: String): DeletedTitle? {
        val title = dao.getTitle(titleId) ?: return null
        val episodes = dao.getEpisodes(titleId)
        dao.deleteTitle(titleId)
        return DeletedTitle(title, episodes)
    }

    /** Puts a [delete]d title back, watched flags included. */
    suspend fun restore(deleted: DeletedTitle) {
        dao.upsertTitle(deleted.title)
        dao.upsertEpisodes(deleted.episodes)
    }

    /**
     * Keeps status honest without taking it away from the user: finishing the last
     * episode completes the title, un-watching one pulls a completed title back to watching.
     */
    private suspend fun afterProgressChange(titleId: String, watched: Boolean, now: Long) {
        val title = dao.getTitle(titleId) ?: return
        if (watched) dao.touchWatched(titleId, now)

        val left = dao.unwatchedCount(titleId)
        when {
            left == 0 && title.status != WatchStatus.COMPLETED ->
                dao.setStatus(titleId, WatchStatus.COMPLETED)

            left > 0 && title.status == WatchStatus.COMPLETED ->
                dao.setStatus(titleId, WatchStatus.WATCHING)

            left > 0 && title.status == WatchStatus.PLANNED && watched ->
                dao.setStatus(titleId, WatchStatus.WATCHING)
        }
    }

    private fun requireBackend() {
        check(hasBackend) { "Бэкенд не настроен. Добавь backend.url и backend.token в local.properties." }
    }

    private companion object {
        const val DEFAULT_EPISODE_MINUTES = 45
    }
}

/** The half of [WatchStats] that counts titles rather than watched time. */
private data class LibraryCounts(
    val seriesCount: Int,
    val movieCount: Int,
    val byStatus: Map<WatchStatus, Int>,
    val remaining: RemainingWatch,
)

/** A title as it was just before [TrackerRepository.delete] removed it. */
data class DeletedTitle(val title: TitleEntity, val episodes: List<EpisodeEntity>)

private fun SearchResultDto.toSearchItem(): SearchItem? {
    val type = when (mediaType) {
        "tv" -> MediaType.TV
        "movie" -> MediaType.MOVIE
        else -> return null // people, and anything new the catalogue starts returning
    }
    val displayName = (name ?: title)?.takeIf { it.isNotBlank() } ?: return null
    return SearchItem(
        catalogId = id,
        mediaType = type,
        name = displayName,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = (firstAirDate ?: releaseDate)?.take(4)?.takeIf { it.isNotBlank() },
        voteAverage = voteAverage,
    )
}
