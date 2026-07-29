package com.g3ck0.seriestracker.data.repository

import com.g3ck0.seriestracker.data.local.MediaType

/** A catalogue search hit, already normalised for the UI. */
data class SearchItem(
    val catalogId: Int,
    val mediaType: MediaType,
    val name: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val year: String?,
    val voteAverage: Double?,
) {
    val id: String get() = titleIdOf(mediaType, catalogId)
}

fun titleIdOf(mediaType: MediaType, catalogId: Int): String =
    "${mediaType.name.lowercase()}_$catalogId"
