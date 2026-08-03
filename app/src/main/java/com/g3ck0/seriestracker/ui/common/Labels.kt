package com.g3ck0.seriestracker.ui.common

import com.g3ck0.seriestracker.data.local.MediaType
import com.g3ck0.seriestracker.data.local.TitleWithProgress
import com.g3ck0.seriestracker.data.local.WatchStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

val WatchStatus.label: String
    get() = when (this) {
        WatchStatus.WATCHING -> "Смотрю"
        WatchStatus.PLANNED -> "Буду смотреть"
        WatchStatus.COMPLETED -> "Просмотрено"
        WatchStatus.ON_HOLD -> "Отложено"
        WatchStatus.DROPPED -> "Брошено"
    }

val MediaType.label: String
    get() = when (this) {
        MediaType.TV -> "Сериал"
        MediaType.MOVIE -> "Фильм"
    }

/** "12 ч 30 мин" — hours only once there is at least one. */
fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0 мин"
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    val mins = minutes % 60
    return buildString {
        if (days > 0) append("$days д ")
        if (days > 0 || hours > 0) append("$hours ч ")
        append("$mins мин")
    }.trim()
}

/**
 * The locale is pinned rather than taken from the device: the UI is Russian whatever the
 * system language is, the same reason the backend is pinned to `ru-RU`.
 */
private val AIR_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

/** "2008-01-20" -> "20 января 2008". Empty when the date does not parse. */
fun formatAirDate(iso: String): String =
    try {
        LocalDate.parse(iso.trim()).format(AIR_DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        ""
    }

fun episodeCode(season: Int, episode: Int): String =
    "S%02dE%02d".format(season, episode)

/**
 * "S01E18 · Мухобойка" — the episode the library card's "+ 1 серия" button will mark.
 * Null when there is nothing left to watch, and for movies. Lives here rather than on
 * [TitleWithProgress] so the data layer keeps no opinion on formatting.
 */
val TitleWithProgress.nextLabel: String?
    get() {
        val season = nextSeason ?: return null
        val episode = nextEpisode ?: return null
        val code = episodeCode(season, episode)
        val name = nextName?.takeIf { it.isNotBlank() } ?: return code
        return "$code · $name"
    }

/** Russian plural forms: 1 серия / 2 серии / 5 серий. */
fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

fun episodesLabel(count: Int): String =
    "$count ${plural(count, "серия", "серии", "серий")}"

fun moviesLabel(count: Int): String =
    "$count ${plural(count, "фильм", "фильма", "фильмов")}"

fun seasonsLabel(count: Int): String =
    "$count ${plural(count, "сезон", "сезона", "сезонов")}"
