package com.g3ck0.seriestracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            // Only the bottom bar is consumed here; every screen's own TopAppBar
            // draws under the status bar and applies that inset itself.
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
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
    }
}
