package com.g3ck0.seriestracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.FloatingNavClearance
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.label
import com.g3ck0.seriestracker.ui.common.moviesLabel

object StatsTags {
    const val TOTAL = "stats:total"
    const val TOTAL_SUB = "stats:totalSub"
    const val SERIES_COUNT = "stats:seriesCount"
    const val MOVIE_COUNT = "stats:movieCount"
    const val SERIES_HOURS = "stats:seriesHours"
    const val MOVIE_HOURS = "stats:movieHours"
    fun statusCount(status: WatchStatus) = "stats:status:${status.name}"
}

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsContent(stats)
}

@Composable
fun StatsContent(stats: WatchStats) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = FloatingNavClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Статистика",
                fontSize = 32.sp,
                lineHeight = 40.sp,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp),
            )

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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStatCard(
                    "Часов на сериалы",
                    (stats.episodeMinutes / 60).toString(),
                    StatsTags.SERIES_HOURS,
                    Modifier.weight(1f),
                )
                SmallStatCard(
                    "Часов на фильмы",
                    (stats.movieMinutes / 60).toString(),
                    StatsTags.MOVIE_HOURS,
                    Modifier.weight(1f),
                )
            }

            Text(
                text = "По статусам",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            val total = stats.byStatus.values.sum().coerceAtLeast(1)
            WatchStatus.entries.forEach { status ->
                val count = stats.byStatus[status] ?: 0
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(status.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag(StatsTags.statusCount(status)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / total },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
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
private fun SmallStatCard(caption: String, value: String, tag: String, modifier: Modifier = Modifier) {
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
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(tag),
            )
        }
    }
}
