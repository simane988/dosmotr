package com.g3ck0.seriestracker.data.backup

import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.WatchStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bumped only on a breaking shape change; [BackupFile.version] is checked on import. */
const val BACKUP_VERSION = 1

@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    @SerialName("exported_at") val exportedAt: String = "",
    /** Informational only — never validated, so old "series-tracker" files still import. */
    val app: String = "dosmotr",
    val titles: List<BackupTitle> = emptyList(),
)

@Serializable
data class BackupTitle(
    val id: String,
    // The JSON keys keep their original names so backups written by older builds still
    // import. They say nothing about where the data comes from today.
    @SerialName("tmdb_id") val catalogId: Int? = null,
    val type: String,
    val name: String,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val year: String? = null,
    val status: String = WatchStatus.WATCHING.name,
    val rating: Int? = null,
    @SerialName("tmdb_rating") val catalogRating: Double? = null,
    @SerialName("runtime_minutes") val runtimeMinutes: Int = 0,
    @SerialName("added_at") val addedAt: Long = 0,
    @SerialName("last_watched_at") val lastWatchedAt: Long? = null,
    val notes: String = "",
    @SerialName("movie_watched") val movieWatched: Boolean = false,
    val episodes: List<BackupEpisode> = emptyList(),
)

@Serializable
data class BackupEpisode(
    val season: Int,
    val episode: Int,
    val name: String = "",
    val overview: String = "",
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("runtime_minutes") val runtimeMinutes: Int = 0,
    val watched: Boolean = false,
    @SerialName("watched_at") val watchedAt: Long? = null,
)

// --- mapping (pure, unit-testable) ---

fun TitleEntity.toBackup(episodes: List<EpisodeEntity>) = BackupTitle(
    id = id,
    catalogId = catalogId,
    type = mediaType.name,
    name = name,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    year = year,
    status = status.name,
    rating = userRating,
    catalogRating = catalogRating,
    runtimeMinutes = runtimeMinutes,
    addedAt = addedAt,
    lastWatchedAt = lastWatchedAt,
    notes = notes,
    movieWatched = movieWatched,
    episodes = episodes.map { it.toBackup() },
)

fun EpisodeEntity.toBackup() = BackupEpisode(
    season = seasonNumber,
    episode = episodeNumber,
    name = name,
    overview = overview,
    airDate = airDate,
    stillPath = stillPath,
    runtimeMinutes = runtimeMinutes,
    watched = watched,
    watchedAt = watchedAt,
)

fun BackupTitle.toEntity() = TitleEntity(
    id = id,
    catalogId = catalogId,
    mediaType = enumOrDefault(type, MediaType.TV),
    name = name,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    year = year,
    status = enumOrDefault(status, WatchStatus.WATCHING),
    userRating = rating?.coerceIn(1, 10),
    catalogRating = catalogRating,
    runtimeMinutes = runtimeMinutes,
    addedAt = addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    lastWatchedAt = lastWatchedAt,
    notes = notes,
    movieWatched = movieWatched,
    // Episodes come from the file itself, so nothing has to be re-fetched.
    episodesLoaded = episodes.isNotEmpty() || enumOrDefault(type, MediaType.TV) == MediaType.MOVIE,
)

fun BackupEpisode.toEntity(titleId: String) = EpisodeEntity(
    titleId = titleId,
    seasonNumber = season,
    episodeNumber = episode,
    name = name,
    overview = overview,
    airDate = airDate,
    stillPath = stillPath,
    runtimeMinutes = runtimeMinutes,
    watched = watched,
    watchedAt = watchedAt,
)

/** Unknown enum names in a hand-edited file fall back instead of crashing the import. */
private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: fallback
