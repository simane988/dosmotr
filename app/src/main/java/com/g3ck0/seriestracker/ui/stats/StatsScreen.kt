package com.g3ck0.seriestracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.FloatingNavClearance
import com.g3ck0.seriestracker.ui.common.ExtendedActionButton
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.label
import com.g3ck0.seriestracker.ui.common.moviesLabel

object StatsTags {
    const val TOTAL = "stats:total"
    const val TOTAL_SUB = "stats:totalSub"
    const val SERIES_COUNT = "stats:seriesCount"
    const val MOVIE_COUNT = "stats:movieCount"
    const val SERIES_TIME = "stats:seriesTime"
    const val MOVIE_TIME = "stats:movieTime"
    const val REMAINING = "stats:remaining"
    const val REMAINING_SUB = "stats:remainingSub"
    const val STATUS_BAR = "stats:statusBar"
    const val EMPTY = "stats:empty"
    const val EMPTY_SEARCH = "stats:empty:search"
    fun statusCount(status: WatchStatus) = "stats:status:${status.name}"
}

@Composable
fun StatsScreen(onSearch: () -> Unit = {}, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsContent(stats, onSearch)
}

@Composable
fun StatsContent(stats: WatchStats, onSearch: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        if (stats.isEmpty) {
            EmptyStats(onSearch)
            return@Surface
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FloatingNavClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // No screen title: the navigation pill names the tab, and a 32sp heading
            // pushed the first card 76 dp down the screen.
            StatCard {
                Text(
                    text = "Всего просмотрено",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatMinutes(stats.totalMinutes),
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag(StatsTags.TOTAL),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${episodesLabel(stats.watchedEpisodes)} · ${moviesLabel(stats.watchedMovies)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(StatsTags.TOTAL_SUB),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStatCard("Сериалов", stats.seriesCount.toString(), StatsTags.SERIES_COUNT, Modifier.weight(1f))
                SmallStatCard("Фильмов", stats.movieCount.toString(), StatsTags.MOVIE_COUNT, Modifier.weight(1f))
            }
            // Hours were printed with integer division, so 59 minutes of watching read as
            // "0" and the top card's "13 ч 56 мин" sat above a card saying "13".
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStatCard(
                    "Время на сериалы",
                    formatMinutes(stats.episodeMinutes),
                    StatsTags.SERIES_TIME,
                    Modifier.weight(1f),
                    valueSize = TIME_VALUE_SIZE,
                )
                SmallStatCard(
                    "Время на фильмы",
                    formatMinutes(stats.movieMinutes),
                    StatsTags.MOVIE_TIME,
                    Modifier.weight(1f),
                    valueSize = TIME_VALUE_SIZE,
                )
            }

            if (stats.remainingEpisodes > 0) {
                RemainingCard(stats)
            }

            Text(
                text = "По статусам",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            StatusBreakdown(stats.byStatus)
        }
    }
}

/**
 * "За сколько я это досмотрю" — the one question the six counters above could not answer,
 * and the data for it was already in the database.
 */
@Composable
private fun RemainingCard(stats: WatchStats) {
    StatCard {
        Text(
            text = "Осталось досмотреть",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatMinutes(stats.remainingMinutes),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(StatsTags.REMAINING),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${episodesLabel(stats.remainingEpisodes)} в статусе «Смотрю»",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(StatsTags.REMAINING_SUB),
        )
    }
}

/**
 * One stacked bar plus a legend, instead of five equally long progress bars whose fill was
 * a share of the total: a status with no titles used to draw an empty track that read as
 * the *fullest* row of all. Empty statuses are simply left out now, and the proportions
 * are visible without reading the numbers.
 */
@Composable
private fun StatusBreakdown(byStatus: Map<WatchStatus, Int>) {
    val present = WatchStatus.entries.mapNotNull { status ->
        val count = byStatus[status] ?: 0
        if (count > 0) status to count else null
    }
    if (present.isEmpty()) return

    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .testTag(StatsTags.STATUS_BAR),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        present.forEach { (status, count) ->
            Box(
                Modifier
                    .weight(count.toFloat())
                    .fillMaxSize()
                    .background(status.color)
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    present.forEach { (status, count) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(status.color)
            )
            Spacer(Modifier.size(8.dp))
            Text(status.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag(StatsTags.statusCount(status)),
            )
        }
    }
}

/**
 * Segment colours come from the scheme's roles rather than from literals, so the bar keeps
 * following Material You and the dark scheme. Grey for "отложено" and the error colour for
 * "брошено" are the two statuses that are not progress, and they have to stay apart from
 * each other.
 */
private val WatchStatus.color: Color
    @Composable get() = when (this) {
        WatchStatus.WATCHING -> MaterialTheme.colorScheme.primary
        WatchStatus.PLANNED -> MaterialTheme.colorScheme.tertiary
        WatchStatus.ON_HOLD -> MaterialTheme.colorScheme.outline
        WatchStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        WatchStatus.DROPPED -> MaterialTheme.colorScheme.error
    }

@Composable
private fun EmptyStats(onSearch: () -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag(StatsTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 64.dp),
        ) {
            Text(text = "Пока нечего считать", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "Добавь сериал или фильм — часы и статусы появятся здесь сами",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            ExtendedActionButton(
                icon = Icons.Filled.Add,
                label = "Найти сериал или фильм",
                onClick = onSearch,
                modifier = Modifier.testTag(StatsTags.EMPTY_SEARCH),
            )
        }
    }
}

@Composable
private fun StatCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun SmallStatCard(
    caption: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 24.sp,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = valueSize,
                lineHeight = valueSize * 1.34f,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

/** "13 ч 56 мин" is three times as wide as "13" — half a screen does not hold it at 24sp. */
private val TIME_VALUE_SIZE = 20.sp
