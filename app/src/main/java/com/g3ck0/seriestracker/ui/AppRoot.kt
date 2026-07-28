package com.g3ck0.seriestracker.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.g3ck0.seriestracker.ui.detail.DetailScreen
import com.g3ck0.seriestracker.ui.library.LibraryScreen
import com.g3ck0.seriestracker.ui.search.SearchScreen
import com.g3ck0.seriestracker.ui.stats.StatsScreen

object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val STATS = "stats"
    const val DETAIL = "detail/{titleId}"
    fun detail(titleId: String) = "detail/$titleId"
}

object NavTags {
    const val BAR = "nav:bar"
    fun tab(route: String) = "nav:tab:$route"
}

/**
 * Height the floating navigation pill occupies, including its bottom offset. Screens
 * pad their scrollable content by this so the last row is not trapped under the bar.
 */
val FloatingNavClearance = 140.dp

/** Incoming screen is slightly slower than the outgoing one, so they never both fade out. */
private const val ENTER_MILLIS = 260
private const val EXIT_MILLIS = 200

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, "Моё", Icons.Filled.VideoLibrary),
    Tab(Routes.SEARCH, "Поиск", Icons.Filled.Search),
    Tab(Routes.STATS, "Статистика", Icons.Filled.BarChart),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onTabs = currentRoute in tabs.map { it.route }

    // Painted behind the NavHost: during a transition both screens are partly
    // transparent, and without this the window background (white) shows through.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            // Material's shared-axis Z: the outgoing screen shrinks away, the
            // incoming one grows into place. Going back reverses the direction.
            enterTransition = {
                fadeIn(tween(ENTER_MILLIS)) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(ENTER_MILLIS))
            },
            exitTransition = {
                fadeOut(tween(EXIT_MILLIS)) +
                    scaleOut(targetScale = 0.94f, animationSpec = tween(EXIT_MILLIS))
            },
            popEnterTransition = {
                fadeIn(tween(ENTER_MILLIS)) +
                    scaleIn(initialScale = 1.06f, animationSpec = tween(ENTER_MILLIS))
            },
            popExitTransition = {
                fadeOut(tween(EXIT_MILLIS)) +
                    scaleOut(targetScale = 1.06f, animationSpec = tween(EXIT_MILLIS))
            },
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenTitle = { navController.navigate(Routes.detail(it)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenTitle = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("titleId") { type = NavType.StringType }),
            ) {
                DetailScreen(onBack = { navController.popBackStack() })
            }
        }

        // The bar floats over the content rather than docking, so it is hidden on the
        // detail screen instead of being drawn behind a full-bleed layout.
        if (onTabs) {
            FloatingNavBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.testTag(NavTags.BAR),
    ) {
        Row(
            modifier = Modifier.height(64.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                if (selected) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag(NavTags.tab(tab.route)),
                        onClick = { onSelect(tab.route) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(56.dp)
                            .height(48.dp)
                            .testTag(NavTags.tab(tab.route)),
                        onClick = { onSelect(tab.route) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
