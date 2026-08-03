package com.g3ck0.seriestracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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
 * Where the floating navigation pill actually ended up, measured by [AppRoot] and read
 * by everything drawn underneath it. Offsets sized for a 914 dp portrait screen land in
 * the middle of a 411 dp landscape one — that is bug-6 — so nothing below is a literal.
 */
data class FloatingNavMetrics(
    /** Distance from the bottom edge of the window to the top of the pill, insets included. */
    val space: Dp = EstimatedNavSpace,
    /** Free width left of the window edge beside the pill. */
    val freeWidth: Dp = 0.dp,
) {
    /** Whether a corner FAB clears the pill sideways and can drop to the bottom edge. */
    val fabFitsBeside: Boolean get() = freeWidth >= FabBesideNavMinWidth
}

/**
 * Used until the first measurement lands, and by screens hosted without [AppRoot]
 * (tests, previews). Matches the pill in portrait: 64 dp tall, 20 dp up, over a
 * gesture bar — close enough that the corrected frame does not visibly jump.
 */
private val EstimatedNavSpace = 104.dp

/**
 * The extended FAB is about 150 dp wide and keeps a 16 dp margin, so anything narrower
 * than this beside the pill means the two would overlap and the FAB has to sit above it.
 */
private val FabBesideNavMinWidth = 200.dp

/** Breathing room between the pill and whatever the clearances push above it. */
private val NavGap = 16.dp

val LocalFloatingNav = compositionLocalOf { FloatingNavMetrics() }

/**
 * Bottom padding for scrollable content, so the last row is not trapped under the bar.
 */
val FloatingNavClearance: Dp
    @Composable get() = LocalFloatingNav.current.space + NavGap

/**
 * Bottom padding for a FAB in the bottom-right corner. In landscape the pill leaves the
 * whole right-hand side free, so the FAB drops to the bottom edge instead of being lifted
 * a pill's height over the cards it would otherwise cover.
 */
val FloatingFabClearance: Dp
    @Composable get() {
        val nav = LocalFloatingNav.current
        val systemBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return if (nav.fabFitsBeside) systemBars + NavGap else nav.space + NavGap
    }

/** Height of [com.g3ck0.seriestracker.ui.common.ExtendedActionButton], the corner FAB. */
val ExtendedActionButtonHeight = 56.dp

/**
 * Bottom padding for scrollable content a corner FAB is drawn over. Clearing the pill is
 * not enough: the FAB stands its own height above its margin, so with only
 * [FloatingNavClearance] the last card ends up under the button — which is what covered
 * the bottom-right corner of the last library card.
 */
val FloatingFabContentClearance: Dp
    @Composable get() = fabContentClearance(FloatingNavClearance, FloatingFabClearance)

/** The arithmetic of [FloatingFabContentClearance], split out so it can be unit-tested. */
internal fun fabContentClearance(navClearance: Dp, fabClearance: Dp): Dp =
    maxOf(navClearance, fabClearance + ExtendedActionButtonHeight + NavGap)

/** Incoming screen is slightly slower than the outgoing one, so they never both fade out. */
private const val ENTER_MILLIS = 260
private const val EXIT_MILLIS = 200

/** Slight overshoot so the highlight settles rather than stopping dead. */
private val NavIndicatorSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioLowBouncy,
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

    val density = LocalDensity.current
    val systemBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var barBounds by remember { mutableStateOf<Rect?>(null) }
    val bar = barBounds
    // Both halves have to be in hand before the pill's position means anything, so the
    // metrics are derived in composition rather than written from the callbacks. On the
    // detail screen there is no pill, and then the only thing to clear is the system bar.
    val navMetrics = when {
        !onTabs -> FloatingNavMetrics(space = systemBars, freeWidth = Dp.Infinity)
        rootSize == IntSize.Zero || bar == null -> FloatingNavMetrics()
        else -> with(density) {
            FloatingNavMetrics(
                space = (rootSize.height - bar.top).toDp(),
                freeWidth = (rootSize.width - bar.right).toDp(),
            )
        }
    }

    // Painted behind the NavHost: during a transition both screens are partly
    // transparent, and without this the window background (white) shows through.
    //
    // imePadding() is applied once here rather than per screen: the activity is
    // edge-to-edge, so without it the system pans the whole window up and the top
    // bar leaves the screen while the keyboard is open. Padding the root shrinks
    // the content instead, and consumes the inset for everything below.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .onGloballyPositioned { rootSize = it.size }
    ) {
        // The screens read the measured pill through the local; the pill itself is
        // outside the provider, since it is what the measurement comes from.
        CompositionLocalProvider(LocalFloatingNav provides navMetrics) {
            AppNavHost(navController)
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
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .onGloballyPositioned { barBounds = it.boundsInParent() },
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
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
}

/**
 * The coloured highlight is a single pill drawn behind the row and animated to the
 * selected tab's measured bounds, so it slides across instead of blinking from one
 * item to the next. Every tab reserves the width of the longest label up front, so a
 * tab's bounds do not move when it is (de)selected — only the label's alpha does.
 */
@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    // Reserved once for every tab, sized to the longest label ("Статистика"), so
    // selecting a tab never re-lays out the row.
    val maxLabelWidth = remember(density, labelStyle) {
        with(density) {
            tabs.maxOf { textMeasurer.measure(text = it.label, style = labelStyle).size.width }.toDp()
        }
    }
    // Measured per tab; bounds are now fixed width, but still per-tab since x
    // position depends on where in the row the tab sits.
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
                        val labelAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = tween(NAV_LABEL_MILLIS),
                            label = "navLabelAlpha",
                        )
                        // Width is fixed for every tab, so fading the label in or out
                        // never changes the row's layout, only what is visible in it.
                        Text(
                            text = tab.label,
                            style = labelStyle,
                            color = contentColor,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .width(maxLabelWidth)
                                .alpha(labelAlpha),
                        )
                    }
                }
            }
        }
    }
}
