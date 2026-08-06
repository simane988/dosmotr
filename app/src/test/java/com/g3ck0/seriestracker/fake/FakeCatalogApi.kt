package com.g3ck0.seriestracker.fake

import com.g3ck0.seriestracker.data.remote.EpisodeDto
import com.g3ck0.seriestracker.data.remote.MovieDetailsDto
import com.g3ck0.seriestracker.data.remote.SearchResponseDto
import com.g3ck0.seriestracker.data.remote.SearchResultDto
import com.g3ck0.seriestracker.data.remote.SeasonDetailsDto
import com.g3ck0.seriestracker.data.remote.SeasonSummaryDto
import com.g3ck0.seriestracker.data.remote.CatalogApi
import com.g3ck0.seriestracker.data.remote.TvDetailsDto
import java.io.IOException

/** Scriptable [CatalogApi]: set the responses a test needs, or make any call fail. */
class FakeCatalogApi : CatalogApi {

    var searchResults: List<SearchResultDto> = emptyList()
    var trendingResults: List<SearchResultDto> = emptyList()
    var tvDetails: TvDetailsDto? = null
    var movieDetails: MovieDetailsDto? = null
    var seasons: Map<Int, SeasonDetailsDto> = emptyMap()

    /** When set, every endpoint throws it. */
    var failure: Throwable? = null

    var searchCalls: Int = 0
        private set

    /** Counted so a test can prove the library never asked for trending at all. */
    var trendingCalls: Int = 0
        private set
    var lastQuery: String? = null
        private set
    val requestedSeasons = mutableListOf<Int>()

    override suspend fun searchMulti(query: String, page: Int): SearchResponseDto {
        failure?.let { throw it }
        searchCalls++
        lastQuery = query
        return SearchResponseDto(results = searchResults)
    }

    override suspend fun trending(): SearchResponseDto {
        failure?.let { throw it }
        trendingCalls++
        return SearchResponseDto(results = trendingResults)
    }

    override suspend fun tvDetails(id: Int): TvDetailsDto {
        failure?.let { throw it }
        return tvDetails ?: throw IOException("no tv details scripted")
    }

    override suspend fun season(id: Int, season: Int): SeasonDetailsDto {
        failure?.let { throw it }
        requestedSeasons += season
        return seasons[season] ?: SeasonDetailsDto(seasonNumber = season)
    }

    override suspend fun movieDetails(id: Int): MovieDetailsDto {
        failure?.let { throw it }
        return movieDetails ?: throw IOException("no movie details scripted")
    }
}

// --- builders keeping the tests readable ---

fun tvResult(id: Int, name: String, year: String = "2020", rating: Double = 8.0) = SearchResultDto(
    id = id,
    mediaType = "tv",
    name = name,
    overview = "overview $name",
    posterPath = "/p$id.jpg",
    firstAirDate = "$year-01-01",
    voteAverage = rating,
)

fun movieResult(id: Int, title: String, year: String = "1999") = SearchResultDto(
    id = id,
    mediaType = "movie",
    title = title,
    overview = "overview $title",
    releaseDate = "$year-05-05",
    voteAverage = 7.5,
)

fun personResult(id: Int) = SearchResultDto(id = id, mediaType = "person", name = "Some Actor")

fun tvDetailsOf(
    id: Int,
    name: String,
    seasonSizes: Map<Int, Int>,
    runtime: Int = 42,
) = TvDetailsDto(
    id = id,
    name = name,
    overview = "overview",
    posterPath = "/p$id.jpg",
    firstAirDate = "2020-01-01",
    voteAverage = 8.5,
    episodeRunTime = listOf(runtime),
    numberOfEpisodes = seasonSizes.values.sum(),
    numberOfSeasons = seasonSizes.count { it.key > 0 },
    seasons = seasonSizes.map { (number, count) -> SeasonSummaryDto(number, count, "Season $number") },
)

fun seasonOf(number: Int, episodes: Int, runtime: Int? = null) = SeasonDetailsDto(
    seasonNumber = number,
    episodes = (1..episodes).map {
        EpisodeDto(
            episodeNumber = it,
            seasonNumber = number,
            name = "S${number}E$it",
            airDate = "2020-01-0$number",
            runtime = runtime,
        )
    },
)
