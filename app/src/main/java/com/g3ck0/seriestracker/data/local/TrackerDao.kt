package com.g3ck0.seriestracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val PROGRESS_SELECT = """
    SELECT t.*,
        (SELECT COUNT(*) FROM episodes e WHERE e.titleId = t.id) AS episodeCount,
        (SELECT COUNT(*) FROM episodes e WHERE e.titleId = t.id AND e.watched = 1) AS watchedCount
    FROM titles t
"""

@Dao
interface TrackerDao {

    @Transaction
    @Query("$PROGRESS_SELECT ORDER BY CASE WHEN t.lastWatchedAt IS NULL THEN 1 ELSE 0 END, t.lastWatchedAt DESC, t.addedAt DESC")
    fun observeLibrary(): Flow<List<TitleWithProgress>>

    @Transaction
    @Query("$PROGRESS_SELECT WHERE t.id = :titleId")
    fun observeTitle(titleId: String): Flow<TitleWithProgress?>

    @Query("SELECT * FROM titles WHERE id = :titleId")
    suspend fun getTitle(titleId: String): TitleEntity?

    @Query("SELECT id FROM titles")
    fun observeTrackedIds(): Flow<List<String>>

    @Query("SELECT * FROM episodes WHERE titleId = :titleId ORDER BY seasonNumber, episodeNumber")
    fun observeEpisodes(titleId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE titleId = :titleId ORDER BY seasonNumber, episodeNumber")
    suspend fun getEpisodes(titleId: String): List<EpisodeEntity>

    /** First unwatched episode in airing order — the "watch next" target. */
    @Query(
        """
        SELECT * FROM episodes
        WHERE titleId = :titleId AND watched = 0
        ORDER BY seasonNumber, episodeNumber
        LIMIT 1
        """
    )
    suspend fun nextUnwatched(titleId: String): EpisodeEntity?

    // Upsert, not INSERT OR REPLACE: REPLACE deletes the row first, and the FK
    // cascade would take every watched episode with it.
    @Upsert
    suspend fun upsertTitle(title: TitleEntity)

    @Update
    suspend fun updateTitle(title: TitleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    /** Import path: the file is authoritative, so existing rows are overwritten. */
    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM titles WHERE id = :titleId")
    suspend fun deleteTitle(titleId: String)

    // --- backup ---

    @Query("SELECT * FROM titles ORDER BY addedAt")
    suspend fun allTitles(): List<TitleEntity>

    @Query("SELECT * FROM episodes ORDER BY titleId, seasonNumber, episodeNumber")
    suspend fun allEpisodes(): List<EpisodeEntity>

    /** Episodes go too — the foreign key cascades. */
    @Query("DELETE FROM titles")
    suspend fun deleteAllTitles()

    @Query("UPDATE titles SET status = :status WHERE id = :titleId")
    suspend fun setStatus(titleId: String, status: WatchStatus)

    @Query("UPDATE titles SET userRating = :rating WHERE id = :titleId")
    suspend fun setRating(titleId: String, rating: Int?)

    @Query("UPDATE titles SET notes = :notes WHERE id = :titleId")
    suspend fun setNotes(titleId: String, notes: String)

    @Query("UPDATE titles SET lastWatchedAt = :timestamp WHERE id = :titleId")
    suspend fun touchWatched(titleId: String, timestamp: Long?)

    @Query("UPDATE titles SET movieWatched = :watched WHERE id = :titleId")
    suspend fun setMovieWatched(titleId: String, watched: Boolean)

    @Query("UPDATE titles SET episodesLoaded = 1 WHERE id = :titleId")
    suspend fun markEpisodesLoaded(titleId: String)

    @Query(
        """
        UPDATE episodes SET watched = :watched, watchedAt = :watchedAt
        WHERE titleId = :titleId AND seasonNumber = :season AND episodeNumber = :episode
        """
    )
    suspend fun setEpisodeWatched(
        titleId: String,
        season: Int,
        episode: Int,
        watched: Boolean,
        watchedAt: Long?,
    )

    @Query(
        """
        UPDATE episodes SET watched = :watched, watchedAt = :watchedAt
        WHERE titleId = :titleId AND seasonNumber = :season
        """
    )
    suspend fun setSeasonWatched(titleId: String, season: Int, watched: Boolean, watchedAt: Long?)

    @Query("UPDATE episodes SET watched = :watched, watchedAt = :watchedAt WHERE titleId = :titleId")
    suspend fun setAllWatched(titleId: String, watched: Boolean, watchedAt: Long?)

    /**
     * Marks everything up to and including the given episode as watched — the usual
     * "I actually resumed at S03E07" catch-up gesture.
     */
    @Query(
        """
        UPDATE episodes SET watched = 1, watchedAt = :watchedAt
        WHERE titleId = :titleId
          AND (seasonNumber < :season OR (seasonNumber = :season AND episodeNumber <= :episode))
        """
    )
    suspend fun watchUpTo(titleId: String, season: Int, episode: Int, watchedAt: Long?)

    @Query("SELECT COUNT(*) FROM episodes WHERE titleId = :titleId AND watched = 0")
    suspend fun unwatchedCount(titleId: String): Int

    // --- stats ---

    @Query("SELECT COUNT(*) FROM episodes WHERE watched = 1")
    fun observeWatchedEpisodeCount(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN e.runtimeMinutes > 0 THEN e.runtimeMinutes ELSE t.runtimeMinutes END), 0)
        FROM episodes e JOIN titles t ON t.id = e.titleId
        WHERE e.watched = 1
        """
    )
    fun observeWatchedEpisodeMinutes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM titles WHERE mediaType = 'MOVIE' AND movieWatched = 1")
    fun observeWatchedMovieCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(runtimeMinutes), 0) FROM titles WHERE mediaType = 'MOVIE' AND movieWatched = 1")
    fun observeWatchedMovieMinutes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM titles WHERE mediaType = :type")
    fun observeCountByType(type: MediaType): Flow<Int>

    @Query("SELECT status, COUNT(*) AS count FROM titles GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCount>>
}

data class StatusCount(val status: WatchStatus, val count: Int)
