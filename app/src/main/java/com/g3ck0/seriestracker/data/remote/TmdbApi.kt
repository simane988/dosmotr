package com.g3ck0.seriestracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): SearchResponseDto

    @GET("trending/all/week")
    suspend fun trending(): SearchResponseDto

    @GET("tv/{id}")
    suspend fun tvDetails(@Path("id") id: Int): TvDetailsDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(@Path("id") id: Int, @Path("season") season: Int): SeasonDetailsDto

    @GET("movie/{id}")
    suspend fun movieDetails(@Path("id") id: Int): MovieDetailsDto
}
