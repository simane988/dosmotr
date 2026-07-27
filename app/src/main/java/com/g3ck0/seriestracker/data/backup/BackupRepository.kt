package com.g3ck0.seriestracker.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.g3ck0.seriestracker.data.local.AppDatabase
import com.g3ck0.seriestracker.data.local.EpisodeEntity
import com.g3ck0.seriestracker.data.local.TrackerDao
import com.g3ck0.seriestracker.data.local.WatchStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val dao: TrackerDao,
) {

    enum class ImportMode {
        /** Keeps everything already in the library; watched flags are unioned. */
        MERGE,

        /** Wipes the library and restores the file exactly. */
        REPLACE,
    }

    data class ImportResult(
        val titlesAdded: Int,
        val titlesUpdated: Int,
        val episodes: Int,
        val skipped: Int,
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun suggestedFileName(): String = "dosmotr-${LocalDate.now()}.json"

    /** Returns how many titles were written. */
    suspend fun export(uri: Uri): Result<Int> = runCatching {
        val text = exportToJson()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Не удалось открыть файл для записи")
        }
        json.decodeFromString(BackupFile.serializer(), text).titles.size
    }

    /** Serialisation half of [export], split out so it can be tested without a Uri. */
    suspend fun exportToJson(): String =
        json.encodeToString(BackupFile.serializer(), buildBackup())

    private suspend fun buildBackup(): BackupFile {
        val titles = dao.allTitles()
        val episodesByTitle = dao.allEpisodes().groupBy { it.titleId }
        return BackupFile(
            version = BACKUP_VERSION,
            exportedAt = Instant.now().toString(),
            titles = titles.map { it.toBackup(episodesByTitle[it.id].orEmpty()) },
        )
    }

    suspend fun import(uri: Uri, mode: ImportMode): Result<ImportResult> = runCatching {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не удалось открыть файл")
        }
        importFromJson(text, mode)
    }

    /** Parsing half of [import], split out so it can be tested without a Uri. */
    suspend fun importFromJson(text: String, mode: ImportMode): ImportResult {
        val backup = json.decodeFromString(BackupFile.serializer(), text)
        require(backup.version <= BACKUP_VERSION) {
            "Файл сделан более новой версией приложения (v${backup.version})"
        }
        val incoming = backup.titles.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        require(incoming.isNotEmpty()) { "В файле нет ни одного тайтла" }

        return db.withTransaction { applyImport(incoming, backup.titles.size - incoming.size, mode) }
    }

    private suspend fun applyImport(
        incoming: List<BackupTitle>,
        skipped: Int,
        mode: ImportMode,
    ): ImportResult {
        if (mode == ImportMode.REPLACE) dao.deleteAllTitles()

        val existingTitles = dao.allTitles().associateBy { it.id }
        val existingEpisodes = dao.allEpisodes()
            .associateBy { EpisodeKey(it.titleId, it.seasonNumber, it.episodeNumber) }

        var added = 0
        var updated = 0
        var episodeCount = 0

        for (backupTitle in incoming) {
            val local = existingTitles[backupTitle.id]
            val fromFile = backupTitle.toEntity()

            val merged = when {
                local == null -> fromFile
                // Title already tracked: the user's own status/rating/notes win,
                // only watch timestamps and missing metadata are pulled from the file.
                else -> local.copy(
                    posterPath = local.posterPath ?: fromFile.posterPath,
                    backdropPath = local.backdropPath ?: fromFile.backdropPath,
                    overview = local.overview.ifBlank { fromFile.overview },
                    year = local.year ?: fromFile.year,
                    notes = local.notes.ifBlank { fromFile.notes },
                    userRating = local.userRating ?: fromFile.userRating,
                    runtimeMinutes = if (local.runtimeMinutes > 0) local.runtimeMinutes
                    else fromFile.runtimeMinutes,
                    movieWatched = local.movieWatched || fromFile.movieWatched,
                    lastWatchedAt = maxOfNullable(local.lastWatchedAt, fromFile.lastWatchedAt),
                    episodesLoaded = local.episodesLoaded || fromFile.episodesLoaded,
                )
            }
            dao.upsertTitle(merged)
            if (local == null) added++ else updated++

            val episodes = backupTitle.episodes.map { backupEpisode ->
                val entity = backupEpisode.toEntity(backupTitle.id)
                val previous = existingEpisodes[
                    EpisodeKey(backupTitle.id, entity.seasonNumber, entity.episodeNumber)
                ]
                if (previous == null) entity else mergeEpisode(previous, entity)
            }
            if (episodes.isNotEmpty()) {
                dao.upsertEpisodes(episodes)
                episodeCount += episodes.size
            }
        }

        // A restored title may now be fully watched — keep the status honest.
        for (backupTitle in incoming) {
            val title = dao.getTitle(backupTitle.id) ?: continue
            if (!title.isMovie && dao.unwatchedCount(title.id) == 0) {
                val hasEpisodes = backupTitle.episodes.isNotEmpty()
                if (hasEpisodes) dao.setStatus(title.id, WatchStatus.COMPLETED)
            }
        }

        return ImportResult(added, updated, episodeCount, skipped)
    }

    /** Watched never gets un-watched by an import; the earliest watch date wins. */
    private fun mergeEpisode(local: EpisodeEntity, fromFile: EpisodeEntity): EpisodeEntity {
        val watched = local.watched || fromFile.watched
        val watchedAt = when {
            !watched -> null
            local.watchedAt == null -> fromFile.watchedAt
            fromFile.watchedAt == null -> local.watchedAt
            else -> minOf(local.watchedAt, fromFile.watchedAt)
        }
        return local.copy(
            name = local.name.ifBlank { fromFile.name },
            overview = local.overview.ifBlank { fromFile.overview },
            airDate = local.airDate ?: fromFile.airDate,
            stillPath = local.stillPath ?: fromFile.stillPath,
            runtimeMinutes = if (local.runtimeMinutes > 0) local.runtimeMinutes
            else fromFile.runtimeMinutes,
            watched = watched,
            watchedAt = watchedAt,
        )
    }

    private data class EpisodeKey(val titleId: String, val season: Int, val episode: Int)

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> max(a, b)
    }
}
