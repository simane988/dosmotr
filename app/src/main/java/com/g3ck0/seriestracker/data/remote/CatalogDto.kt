package com.g3ck0.seriestracker.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val page: Int = 1,
    val results: List<SearchResultDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class SearchResultDto(
    val id: Int,
    /** "tv", "movie" or "person" — absent on the type-specific endpoints. */
    @SerialName("media_type") val mediaType: String? = null,
    val name: String? = null,
    val title: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class TvDetailsDto(
    val id: Int,
    val name: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("number_of_episodes") val numberOfEpisodes: Int = 0,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    val seasons: List<SeasonSummaryDto> = emptyList(),
)

@Serializable
data class SeasonSummaryDto(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val name: String = "",
)

@Serializable
data class SeasonDetailsDto(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
)

@Serializable
data class MovieDetailsDto(
    val id: Int,
    val title: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
)
