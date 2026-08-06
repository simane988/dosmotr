package com.g3ck0.seriestracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType { TV, MOVIE }

/**
 * [libraryOrder] drives the library sort: what you are still going to watch comes first,
 * finished and abandoned titles sink to the bottom. Freshly completing a series would
 * otherwise push it to the very top, exactly when it stops being useful.
 *
 * The same order is hard-coded in `TrackerDao.observeLibrary`'s SQL — keep both in sync;
 * `TrackerDaoTest.libraryPutsActiveTitlesFirst` fails if they drift apart.
 */
enum class WatchStatus(val libraryOrder: Int) {
    WATCHING(0),
    PLANNED(1),
    ON_HOLD(2),
    COMPLETED(3),
    DROPPED(4),
}

/**
 * One tracked series or movie. [id] is stable and human-readable so a catalogue title
 * can never be added twice: "tv_1399", "movie_550", "local_<uuid>".
 */
@Entity(tableName = "titles")
data class TitleEntity(
    @PrimaryKey val id: String,
    // The column names predate the backend and stay put: renaming one is a schema change
    // like any other, so it needs a hand-written entry in AppDatabase.MIGRATIONS that
    // copies every existing library across. Nothing about the names is worth that.
    @ColumnInfo(name = "tmdbId") val catalogId: Int?,
    val mediaType: MediaType,
    val name: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: String? = null,
    val status: WatchStatus = WatchStatus.WATCHING,
    /** User score 1..10, null when not rated. */
    val userRating: Int? = null,
    /** Score from the catalogue, 0..10. [userRating] is the user's own. */
    @ColumnInfo(name = "tmdbRating") val catalogRating: Double? = null,
    /** Minutes per episode for TV, total runtime for a movie. */
    val runtimeMinutes: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastWatchedAt: Long? = null,
    val notes: String = "",
    /** Movies have no episode rows; this is their watched flag. */
    val movieWatched: Boolean = false,
    /** False until seasons/episodes were pulled from the backend. */
    val episodesLoaded: Boolean = false,
) {
    val isMovie: Boolean get() = mediaType == MediaType.MOVIE
}

@Entity(
    tableName = "episodes",
    primaryKeys = ["titleId", "seasonNumber", "episodeNumber"],
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("titleId"), Index("titleId", "seasonNumber")],
)
data class EpisodeEntity(
    val titleId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String = "",
    val overview: String = "",
    val airDate: String? = null,
    val stillPath: String? = null,
    val runtimeMinutes: Int = 0,
    val watched: Boolean = false,
    val watchedAt: Long? = null,
)

/** Row of the library list: the title plus its episode counters. */
data class TitleWithProgress(
    @Embedded val title: TitleEntity,
    @ColumnInfo(name = "episodeCount") val episodeCount: Int,
    @ColumnInfo(name = "watchedCount") val watchedCount: Int,
    // The first unwatched episode in airing order, computed by the same query as the
    // counters: the library card names what "+ 1 серия" will mark, and one row per title
    // beats a per-item suspend call that cannot stay in sync with the Flow.
    // All three are null when nothing is left to watch, and for movies, which have no
    // episode rows at all.
    @ColumnInfo(name = "nextSeason") val nextSeason: Int? = null,
    @ColumnInfo(name = "nextEpisode") val nextEpisode: Int? = null,
    @ColumnInfo(name = "nextName") val nextName: String? = null,
) {
    val isCompleted: Boolean
        get() = if (title.isMovie) title.movieWatched else episodeCount > 0 && watchedCount >= episodeCount

    /** 0f..1f; movies are all-or-nothing. */
    val progress: Float
        get() = when {
            title.isMovie -> if (title.movieWatched) 1f else 0f
            episodeCount <= 0 -> 0f
            else -> watchedCount.toFloat() / episodeCount
        }

    val remaining: Int
        get() = if (title.isMovie) {
            if (title.movieWatched) 0 else 1
        } else {
            (episodeCount - watchedCount).coerceAtLeast(0)
        }
}

/** Aggregate for the stats screen. */
data class WatchStats(
    val watchedEpisodes: Int = 0,
    val episodeMinutes: Int = 0,
    val watchedMovies: Int = 0,
    val movieMinutes: Int = 0,
    val seriesCount: Int = 0,
    val movieCount: Int = 0,
    val byStatus: Map<WatchStatus, Int> = emptyMap(),
    /** Unwatched episodes of the titles in "Смотрю" — what is left to finish them. */
    val remainingEpisodes: Int = 0,
    val remainingMinutes: Int = 0,
) {
    val totalMinutes: Int get() = episodeMinutes + movieMinutes

    /** True while the library holds nothing at all: the screen then has no numbers to show. */
    val isEmpty: Boolean get() = seriesCount == 0 && movieCount == 0
}
