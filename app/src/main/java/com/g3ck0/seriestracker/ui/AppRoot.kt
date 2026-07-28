package com.g3ck0.seriestracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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

/** Slight overshoot so the highlight settles rather than stopping dead. */
private val NavIndicatorSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
/** Label expansion drives the layout, so it must not bounce or the text jitters. */
private val NavSizeSpec = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private const val NAV_LABEL_MILLIS = 180
private const val NAV_COLOR_MILLIS = 220

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

/**
 * The coloured highlight is a single pill drawn behind the row and animated to the
 * selected tab's measured bounds, so it slides across instead of blinking from one
 * item to the next. The tabs themselves expand and collapse their label underneath it.
 */
@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Measured per tab; the highlight needs real bounds because a selected tab is
    // wider than a collapsed one and every position shifts when the label appears.
    val bounds = remember { mutableStateMapOf<String, Pair<Dp, Dp>>() }
    val target = bounds[currentRoute]

    val indicatorOffset by animateDpAsState(
        targetValue = target?.first ?: 0.dp,
        animationSpec = NavIndicatorSpec,
        label = "navIndicatorOffset",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = target?.second ?: 0.dp,
        animationSpec = NavIndicatorSpec,
        label = "navIndicatorWidth",
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.testTag(NavTags.BAR),
    ) {
        Box(Modifier.height(64.dp).padding(8.dp)) {
            if (target != null) {
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(24.dp),
                        )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(NAV_COLOR_MILLIS),
                        label = "navTabColor",
                    )

                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .onGloballyPositioned { coords ->
                                val x = with(density) { coords.positionInParent().x.toDp() }
                                val w = with(density) { coords.size.width.toDp() }
                                if (bounds[tab.route] != x to w) bounds[tab.route] = x to w
                            }
                            .clip(RoundedCornerShape(24.dp))
                            // No ripple: it draws its own translucent blob on top of
                            // the sliding highlight, and the two read as a rendering
                            // glitch. The highlight moving is the feedback here.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(tab.route) },
                            )
                            .padding(horizontal = 16.dp)
                            .testTag(NavTags.tab(tab.route)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                        // Expanding the label is what makes the row re-layout, which
                        // in turn gives the highlight somewhere new to slide to.
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(NAV_LABEL_MILLIS)) +
                                expandHorizontally(NavSizeSpec),
                            exit = fadeOut(tween(NAV_LABEL_MILLIS)) +
                                shrinkHorizontally(NavSizeSpec),
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
