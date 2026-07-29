package com.g3ck0.seriestracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The backend's catalogue contract. These paths are the app's own and deliberately not
 * the shape of any upstream provider, so the backend can change where the data comes
 * from without a new build going out. Language and adult filtering are the backend's
 * business too — the app does not ask for them.
 */
interface CatalogApi {

    @GET("v1/search")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): SearchResponseDto

    @GET("v1/trending")
    suspend fun trending(): SearchResponseDto

    @GET("v1/tv/{id}")
    suspend fun tvDetails(@Path("id") id: Int): TvDetailsDto

    @GET("v1/tv/{id}/season/{season}")
    suspend fun season(@Path("id") id: Int, @Path("season") season: Int): SeasonDetailsDto

    @GET("v1/movie/{id}")
    suspend fun movieDetails(@Path("id") id: Int): MovieDetailsDto
}
