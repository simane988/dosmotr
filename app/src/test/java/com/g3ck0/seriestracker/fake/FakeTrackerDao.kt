package com.g3ck0.seriestracker.fake

import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.RemainingWatch
import com.g3ck0.seriestracker.data.local.StatusCount
import com.g3ck0.seriestracker.data.local.TitleEntity
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for [TrackerDao].
 *
 * Mirrors the SQL semantics the real DAO relies on — IGNORE vs upsert on episodes,
 * the FK cascade on delete, and the library sort order — so repository and ViewModel
 * tests exercise the same behaviour the database would give them.
 * The SQL itself is verified separately by the instrumented `TrackerDaoTest`.
 */
class FakeTrackerDao : TrackerDao {

    private data class Db(
        val titles: List<TitleEntity> = emptyList(),
        val episodes: List<EpisodeEntity> = emptyList(),
    )

    private val state = MutableStateFlow(Db())

    private fun EpisodeEntity.key() = Triple(titleId, seasonNumber, episodeNumber)

    private fun progressOf(db: Db, title: TitleEntity): TitleWithProgress {
        val own = db.episodes.filter { it.titleId == title.id }
        // Same row PROGRESS_SELECT's subqueries pick: first unwatched in airing order.
        val next = own.filter { !it.watched }
            .minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        return TitleWithProgress(
            title = title,
            episodeCount = own.size,
            watchedCount = own.count { it.watched },
            nextSeason = next?.seasonNumber,
            nextEpisode = next?.episodeNumber,
            nextName = next?.name,
        )
    }

    private fun <T> observe(block: (Db) -> T): Flow<T> =
        state.map(block).distinctUntilChanged()

    private fun mutate(block: (Db) -> Db) {
        state.value = block(state.value)
    }

    private fun mutateTitle(titleId: String, block: (TitleEntity) -> TitleEntity) = mutate { db ->
        db.copy(titles = db.titles.map { if (it.id == titleId) block(it) else it })
    }

    // --- reads ---

    override fun observeLibrary(): Flow<List<TitleWithProgress>> = observe { db ->
        db.titles
            // Mirrors the SQL: active statuses first, then newest watch, then newest added.
            .sortedWith(
                compareBy<TitleEntity> { it.status.libraryOrder }
                    .thenBy { it.lastWatchedAt == null }
                    .thenByDescending { it.lastWatchedAt ?: 0L }
                    .thenByDescending { it.addedAt }
            )
            .map { progressOf(db, it) }
    }

    override fun observeTitle(titleId: String): Flow<TitleWithProgress?> = observe { db ->
        db.titles.firstOrNull { it.id == titleId }?.let { progressOf(db, it) }
    }

    override suspend fun getTitle(titleId: String): TitleEntity? =
        state.value.titles.firstOrNull { it.id == titleId }

    override fun observeTrackedIds(): Flow<List<String>> = observe { db -> db.titles.map { it.id } }

    override fun observeEpisodes(titleId: String): Flow<List<EpisodeEntity>> = observe { db ->
        db.episodes.filter { it.titleId == titleId }
            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    }

    override suspend fun getEpisodes(titleId: String): List<EpisodeEntity> =
        state.value.episodes.filter { it.titleId == titleId }
            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

    override suspend fun nextUnwatched(titleId: String): EpisodeEntity? =
        getEpisodes(titleId).firstOrNull { !it.watched }

    // --- writes ---

    override suspend fun upsertTitle(title: TitleEntity) = mutate { db ->
        val exists = db.titles.any { it.id == title.id }
        // Upsert never touches episodes — that is the whole point of not using REPLACE.
        db.copy(titles = if (exists) db.titles.map { if (it.id == title.id) title else it } else db.titles + title)
    }

    override suspend fun updateTitle(title: TitleEntity) = mutate { db ->
        db.copy(titles = db.titles.map { if (it.id == title.id) title else it })
    }

    override suspend fun insertEpisodes(episodes: List<EpisodeEntity>) = mutate { db ->
        val existing = db.episodes.map { it.key() }.toSet()
        db.copy(episodes = db.episodes + episodes.filter { it.key() !in existing })
    }

    override suspend fun upsertEpisodes(episodes: List<EpisodeEntity>) = mutate { db ->
        val incoming = episodes.associateBy { it.key() }
        val updated = db.episodes.map { incoming[it.key()] ?: it }
        val existing = db.episodes.map { it.key() }.toSet()
        db.copy(episodes = updated + episodes.filter { it.key() !in existing })
    }

    override suspend fun deleteTitle(titleId: String) = mutate { db ->
        db.copy(
            titles = db.titles.filterNot { it.id == titleId },
            episodes = db.episodes.filterNot { it.titleId == titleId }, // FK cascade
        )
    }

    override suspend fun allTitles(): List<TitleEntity> = state.value.titles.sortedBy { it.addedAt }

    override suspend fun allEpisodes(): List<EpisodeEntity> = state.value.episodes
        .sortedWith(compareBy({ it.titleId }, { it.seasonNumber }, { it.episodeNumber }))

    override suspend fun deleteAllTitles() = mutate { Db() }

    override suspend fun setStatus(titleId: String, status: WatchStatus) =
        mutateTitle(titleId) { it.copy(status = status) }

    override suspend fun setRating(titleId: String, rating: Int?) =
        mutateTitle(titleId) { it.copy(userRating = rating) }

    override suspend fun setNotes(titleId: String, notes: String) =
        mutateTitle(titleId) { it.copy(notes = notes) }

    override suspend fun touchWatched(titleId: String, timestamp: Long?) =
        mutateTitle(titleId) { it.copy(lastWatchedAt = timestamp) }

    override suspend fun setMovieWatched(titleId: String, watched: Boolean) =
        mutateTitle(titleId) { it.copy(movieWatched = watched) }

    override suspend fun markEpisodesLoaded(titleId: String) =
        mutateTitle(titleId) { it.copy(episodesLoaded = true) }

    override suspend fun setEpisodeWatched(
        titleId: String,
        season: Int,
        episode: Int,
        watched: Boolean,
        watchedAt: Long?,
    ) = mutate { db ->
        db.copy(
            episodes = db.episodes.map {
                if (it.titleId == titleId && it.seasonNumber == season && it.episodeNumber == episode) {
                    it.copy(watched = watched, watchedAt = watchedAt)
                } else {
                    it
                }
            }
        )
    }

    override suspend fun setSeasonWatched(
        titleId: String,
        season: Int,
        watched: Boolean,
        watchedAt: Long?,
    ) = mutate { db ->
        db.copy(
            episodes = db.episodes.map {
                if (it.titleId == titleId && it.seasonNumber == season) {
                    it.copy(watched = watched, watchedAt = watchedAt)
                } else {
                    it
                }
            }
        )
    }

    override suspend fun setAllWatched(titleId: String, watched: Boolean, watchedAt: Long?) =
        mutate { db ->
            db.copy(
                episodes = db.episodes.map {
                    if (it.titleId == titleId) it.copy(watched = watched, watchedAt = watchedAt) else it
                }
            )
        }

    override suspend fun watchUpTo(titleId: String, season: Int, episode: Int, watchedAt: Long?) =
        mutate { db ->
            db.copy(
                episodes = db.episodes.map {
                    val inRange = it.seasonNumber < season ||
                        (it.seasonNumber == season && it.episodeNumber <= episode)
                    if (it.titleId == titleId && inRange) {
                        it.copy(watched = true, watchedAt = watchedAt)
                    } else {
                        it
                    }
                }
            )
        }

    override suspend fun unwatchedCount(titleId: String): Int =
        state.value.episodes.count { it.titleId == titleId && !it.watched }

    // --- stats ---

    override fun observeWatchedEpisodeCount(): Flow<Int> =
        observe { db -> db.episodes.count { it.watched } }

    override fun observeWatchedEpisodeMinutes(): Flow<Int> = observe { db ->
        db.episodes.filter { it.watched }.sumOf { episode ->
            val title = db.titles.firstOrNull { it.id == episode.titleId }
            if (episode.runtimeMinutes > 0) episode.runtimeMinutes else title?.runtimeMinutes ?: 0
        }
    }

    override fun observeWatchedMovieCount(): Flow<Int> = observe { db ->
        db.titles.count { it.mediaType == MediaType.MOVIE && it.movieWatched }
    }

    override fun observeWatchedMovieMinutes(): Flow<Int> = observe { db ->
        db.titles.filter { it.mediaType == MediaType.MOVIE && it.movieWatched }
            .sumOf { it.runtimeMinutes }
    }

    override fun observeCountByType(type: MediaType): Flow<Int> =
        observe { db -> db.titles.count { it.mediaType == type } }

    override fun observeStatusCounts(): Flow<List<StatusCount>> = observe { db ->
        db.titles.groupingBy { it.status }.eachCount().map { StatusCount(it.key, it.value) }
    }

    override fun observeRemaining(): Flow<RemainingWatch> = observe { db ->
        val watching = db.titles.filter { it.status == WatchStatus.WATCHING }.associateBy { it.id }
        val left = db.episodes.filter { !it.watched && watching.containsKey(it.titleId) }
        RemainingWatch(
            episodes = left.size,
            minutes = left.sumOf { episode ->
                val title = watching[episode.titleId]
                if (episode.runtimeMinutes > 0) episode.runtimeMinutes else title?.runtimeMinutes ?: 0
            },
        )
    }

    // --- test helpers ---

    fun seedTitle(title: TitleEntity) = mutate { it.copy(titles = it.titles + title) }

    fun seedEpisodes(episodes: List<EpisodeEntity>) = mutate { it.copy(episodes = it.episodes + episodes) }

    fun titles(): List<TitleEntity> = state.value.titles

    fun episodes(): List<EpisodeEntity> = state.value.episodes
}
