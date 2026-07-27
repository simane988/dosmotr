package com.g3ck0.seriestracker.data.remote

import com.g3ck0.seriestracker.BuildConfig

object TmdbImage {
    fun poster(path: String?, size: String = "w342"): String? =
        path?.let { BuildConfig.TMDB_IMAGE_URL + size + it }

    fun still(path: String?, size: String = "w300"): String? =
        path?.let { BuildConfig.TMDB_IMAGE_URL + size + it }

    fun backdrop(path: String?, size: String = "w780"): String? =
        path?.let { BuildConfig.TMDB_IMAGE_URL + size + it }
}
