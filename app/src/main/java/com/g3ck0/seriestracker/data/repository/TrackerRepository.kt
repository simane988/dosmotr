package com.g3ck0.seriestracker.data.repository

import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.data.remote.SearchResultDto
import com.g3ck0.seriestracker.data.remote.TmdbApi
import com.g3ck0.seriestracker.di.TmdbApiKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerRepository @Inject constructor(
    private val dao: TrackerDao,
    private val api: TmdbApi,
    @TmdbApiKey private val apiKey: String,
) {

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

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
        combine(
            dao.observeCountByType(MediaType.TV),
            dao.observeCountByType(MediaType.MOVIE),
            dao.observeStatusCounts(),
        ) { series, movies, statuses ->
            Triple(series, movies, statuses.associate { it.status to it.count })
        },
    ) { episodes, episodeMinutes, movies, movieMinutes, counts ->
        WatchStats(
            watchedEpisodes = episodes,
            episodeMinutes = episodeMinutes,
            watchedMovies = movies,
            movieMinutes = movieMinutes,
            seriesCount = counts.first,
            movieCount = counts.second,
            byStatus = counts.third,
        )
    }

    // --- discovery ---

    suspend fun search(query: String): Result<List<SearchItem>> = runCatching {
        requireApiKey()
        api.searchMulti(query).results.mapNotNull { it.toSearchItem() }
    }

    suspend fun trending(): Result<List<SearchItem>> = runCatching {
        requireApiKey()
        api.trending().results.mapNotNull { it.toSearchItem() }
    }

    // --- adding ---

    /** Saves the title immediately, then pulls details/episodes so the UI never waits on the network. */
    suspend fun add(item: SearchItem, status: WatchStatus = WatchStatus.WATCHING): Result<String> =
        runCatching {
            val id = item.id
            dao.upsertTitle(
                TitleEntity(
                    id = id,
                    tmdbId = item.tmdbId,
                    mediaType = item.mediaType,
                    name = item.name,
                    overview = item.overview,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    year = item.year,
                    status = status,
                    tmdbRating = item.voteAverage,
                )
            )
            runCatching { refreshFromTmdb(id) }
            id
        }

    /** Manual entry for anything TMDB does not have. [episodesPerSeason] is empty for movies. */
    suspend fun addManual(
        name: String,
        mediaType: MediaType,
        episodesPerSeason: List<Int>,
        runtimeMinutes: Int,
        year: String?,
        status: WatchStatus = WatchStatus.WATCHING,
    ): String {
        val id = "local_${UUID.randomUUID()}"
        dao.upsertTitle(
            TitleEntity(
                id = id,
                tmdbId = null,
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
    suspend fun refreshFromTmdb(titleId: String): Result<Unit> = runCatching {
        requireApiKey()
        val title = dao.getTitle(titleId) ?: error("Тайтл не найден")
        val tmdbId = title.tmdbId ?: return@runCatching

        when (title.mediaType) {
            MediaType.TV -> {
                val details = api.tvDetails(tmdbId)
                val perEpisode = details.episodeRunTime.firstOrNull() ?: DEFAULT_EPISODE_MINUTES
                dao.updateTitle(
                    title.copy(
                        name = details.name.ifBlank { title.name },
                        overview = details.overview.ifBlank { title.overview },
                        posterPath = details.posterPath ?: title.posterPath,
                        backdropPath = details.backdropPath ?: title.backdropPath,
                        year = details.firstAirDate?.take(4) ?: title.year,
                        tmdbRating = details.voteAverage ?: title.tmdbRating,
                        runtimeMinutes = perEpisode,
                        episodesLoaded = true,
                    )
                )
                // Season 0 is specials — skip it, it inflates progress for no reason.
                val seasons = details.seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                for (season in seasons) {
                    val fetched = runCatching { api.season(tmdbId, season.seasonNumber) }.getOrNull()
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
                val details = api.movieDetails(tmdbId)
                dao.updateTitle(
                    title.copy(
                        name = details.title.ifBlank { title.name },
                        overview = details.overview.ifBlank { title.overview },
                        posterPath = details.posterPath ?: title.posterPath,
                        backdropPath = details.backdropPath ?: title.backdropPath,
                        year = details.releaseDate?.take(4) ?: title.year,
                        tmdbRating = details.voteAverage ?: title.tmdbRating,
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

    suspend fun delete(titleId: String) = dao.deleteTitle(titleId)

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

    private fun requireApiKey() {
        check(hasApiKey) { "Нет TMDB API-ключа. Добавь tmdb.apiKey в local.properties." }
    }

    private companion object {
        const val DEFAULT_EPISODE_MINUTES = 45
    }
}

private fun SearchResultDto.toSearchItem(): SearchItem? {
    val type = when (mediaType) {
        "tv" -> MediaType.TV
        "movie" -> MediaType.MOVIE
        else -> return null // person results and anything new TMDB adds
    }
    val displayName = (name ?: title)?.takeIf { it.isNotBlank() } ?: return null
    return SearchItem(
        tmdbId = id,
        mediaType = type,
        name = displayName,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = (firstAirDate ?: releaseDate)?.take(4)?.takeIf { it.isNotBlank() },
        voteAverage = voteAverage,
    )
}
