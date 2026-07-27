package com.g3ck0.seriestracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g3ck0.seriestracker.data.local.WatchStats
import com.g3ck0.seriestracker.data.local.WatchStatus
import com.g3ck0.seriestracker.ui.common.episodesLabel
import com.g3ck0.seriestracker.ui.common.formatMinutes
import com.g3ck0.seriestracker.ui.common.moviesLabel
import com.g3ck0.seriestracker.ui.common.label

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(stats: WatchStats) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Статистика") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigCard(
                caption = "Всего просмотрено",
                value = formatMinutes(stats.totalMinutes),
                sub = "${episodesLabel(stats.watchedEpisodes)} · ${moviesLabel(stats.watchedMovies)}",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard(
                    caption = "Сериалов",
                    value = stats.seriesCount.toString(),
                    modifier = Modifier.weight(1f),
                    tag = StatsTags.SERIES_COUNT,
                )
                SmallCard(
                    caption = "Фильмов",
                    value = stats.movieCount.toString(),
                    modifier = Modifier.weight(1f),
                    tag = StatsTags.MOVIE_COUNT,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard(
                    caption = "Часов на сериалы",
                    value = (stats.episodeMinutes / 60).toString(),
                    modifier = Modifier.weight(1f),
                    tag = StatsTags.SERIES_HOURS,
                )
                SmallCard(
                    caption = "Часов на фильмы",
                    value = (stats.movieMinutes / 60).toString(),
                    modifier = Modifier.weight(1f),
                    tag = StatsTags.MOVIE_HOURS,
                )
            }

            Text("По статусам", style = MaterialTheme.typography.titleMedium)
            val total = stats.byStatus.values.sum().coerceAtLeast(1)
            WatchStatus.entries.forEach { status ->
                val count = stats.byStatus[status] ?: 0
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(status.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(StatsTags.statusCount(status)),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BigCard(caption: String, value: String, sub: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(caption, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(StatsTags.TOTAL),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(StatsTags.TOTAL_SUB),
            )
        }
    }
}

@Composable
private fun SmallCard(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(caption, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
            )
        }
    }
}
