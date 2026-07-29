package com.g3ck0.seriestracker.data.remote

import com.g3ck0.seriestracker.BuildConfig

/**
 * Artwork URLs. The backend serves images under `/img/<size>/<path>`; the sizes are part
 * of that contract, not of whatever catalogue sits behind it.
 */
object CatalogImage {
    fun poster(path: String?, size: String = "w342"): String? =
        path?.let { BuildConfig.BACKEND_IMAGE_URL + size + it }

    fun still(path: String?, size: String = "w300"): String? =
        path?.let { BuildConfig.BACKEND_IMAGE_URL + size + it }

    fun backdrop(path: String?, size: String = "w780"): String? =
        path?.let { BuildConfig.BACKEND_IMAGE_URL + size + it }
}
