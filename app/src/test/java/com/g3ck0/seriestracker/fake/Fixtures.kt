package com.g3ck0.seriestracker.fake

import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus

fun tvTitle(
    id: String = "tv_1",
    tmdbId: Int? = 1,
    name: String = "Series",
    status: WatchStatus = WatchStatus.WATCHING,
    runtimeMinutes: Int = 40,
    episodesLoaded: Boolean = true,
    addedAt: Long = 1_000,
    lastWatchedAt: Long? = null,
    userRating: Int? = null,
    year: String? = "2020",
) = TitleEntity(
    id = id,
    tmdbId = tmdbId,
    mediaType = MediaType.TV,
    name = name,
    status = status,
    runtimeMinutes = runtimeMinutes,
    episodesLoaded = episodesLoaded,
    addedAt = addedAt,
    lastWatchedAt = lastWatchedAt,
    userRating = userRating,
    year = year,
)

fun movieTitle(
    id: String = "movie_1",
    tmdbId: Int? = 1,
    name: String = "Movie",
    status: WatchStatus = WatchStatus.PLANNED,
    runtimeMinutes: Int = 120,
    movieWatched: Boolean = false,
) = TitleEntity(
    id = id,
    tmdbId = tmdbId,
    mediaType = MediaType.MOVIE,
    name = name,
    status = status,
    runtimeMinutes = runtimeMinutes,
    movieWatched = movieWatched,
    episodesLoaded = true,
)

/** [sizes] maps season number to episode count. */
fun episodesFor(titleId: String, sizes: Map<Int, Int>, runtimeMinutes: Int = 0): List<EpisodeEntity> =
    sizes.flatMap { (season, count) ->
        (1..count).map { number ->
            EpisodeEntity(
                titleId = titleId,
                seasonNumber = season,
                episodeNumber = number,
                name = "S${season}E$number",
                runtimeMinutes = runtimeMinutes,
            )
        }
    }

fun progressOf(
    title: TitleEntity,
    episodeCount: Int = 0,
    watchedCount: Int = 0,
) = TitleWithProgress(title, episodeCount, watchedCount)
