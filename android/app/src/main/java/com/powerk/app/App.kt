package com.powerk.app

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import com.powerk.app.components.DockItem
import com.powerk.app.components.brandBoltPath
import com.powerk.app.components.FloatingDock
import com.powerk.app.components.NavRail
import com.powerk.app.components.Ticker
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.powerk.app.screens.ActivityScreen
import com.powerk.app.screens.AutomationHubScreen
import com.powerk.app.screens.AutomationsScreen
import com.powerk.app.screens.RoomsScreen
import com.powerk.app.screens.RuleTypeScreen
import com.powerk.app.screens.SchedulesScreen
import com.powerk.app.screens.StripMenuScreen
import com.powerk.app.screens.EnergyScreen
import com.powerk.app.screens.HomeScreen
import com.powerk.app.screens.SettingsScreen
import com.powerk.app.screens.StripDetailScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOME = "home"
private const val ENERGY = "energy"
private const val AUTOMATIONS = "automations"
private const val ACTIVITY = "activity"
private const val SETTINGS = "settings"
private const val STRIP = "strip/{mac}"
private const val MENU = "menu/{mac}"
private const val SCHEDULES = "schedules/{mac}"
private const val RULES = "rules"
private const val ROOMS = "rooms"
private const val RULE_TYPE = "rule/{type}/{mac}"
private const val ENERGY_ROUTE = "energy?strip={strip}"

private data class TabItem(val route: String, val icon: ImageVector, val label: @Composable () -> String)

private val TABS = listOf(
    TabItem(HOME, Icons.Rounded.Home) { stringResource(R.string.tab_plugs) },
    TabItem(ENERGY, Icons.Rounded.Bolt) { stringResource(R.string.tab_power) },
    TabItem(AUTOMATIONS, Icons.Rounded.Schedule) { stringResource(R.string.tab_automations) },
    TabItem(ACTIVITY, Icons.Rounded.Notifications) { stringResource(R.string.tab_activity) },
    TabItem(SETTINGS, Icons.Rounded.Settings) { stringResource(R.string.tab_settings) },
)

private fun String?.tabRoute(): String? = this?.substringBefore('?')?.takeIf { r -> TABS.any { it.route == r } }

@Composable
private fun Splash() {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val reduced = AppReducedMotion
    // Phases: path trace → fill → Netflix-style zoom until the mark fills the whole
    // screen → hand over to the dashboard with a quick fade.
    val draw = remember { Animatable(if (reduced) 1f else 0f) }
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    val zoom = remember { Animatable(1f) }
    val cover = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().background(scheme.background), contentAlignment = Alignment.Center) {
        // The mark is sized from the window (big, not a badge), then the zoom factor is
        // computed so the bolt's bounding box passes every screen edge.
        val mark = (minOf(maxWidth, maxHeight) * 0.58f).coerceIn(160.dp, 340.dp)
        val coverScale = maxOf(maxWidth / (mark * 0.389f), maxHeight / (mark * 0.593f)) * 1.15f
        LaunchedEffect(Unit) {
            if (reduced) return@LaunchedEffect
            launch { draw.animateTo(1f, tween(430, easing = FastOutSlowInEasing)) }
            launch { delay(240); fill.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
            launch { delay(520); zoom.animateTo(coverScale, tween(520, easing = CubicBezierEasing(0.55f, 0f, 0.95f, 0.45f))) }
            launch { delay(940); cover.animateTo(1f, tween(240, easing = LinearEasing)) }
        }
        // Soft accent halo that breathes with the fill and evaporates as the zoom starts.
        Box(
            Modifier.size(mark * 2.6f).graphicsLayer {
                alpha = fill.value * (1f - cover.value) * 0.55f
                scaleX = 0.85f + fill.value * 0.15f
                scaleY = scaleX
            }.background(Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), Color.Transparent))),
        )
        Box(Modifier.size(mark).graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }) {
            Canvas(Modifier.fillMaxSize()) {
                val path = brandBoltPath(size)
                val d = draw.value
                if (d < 1f) {
                    // Trace the bolt outline as a rounded stroke, like it is being drawn.
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val segment = Path()
                    measure.getSegment(0f, measure.length * d, segment, true)
                    drawPath(
                        segment,
                        accent,
                        style = Stroke(width = size.minDimension * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else {
                    drawPath(
                        path,
                        Brush.linearGradient(listOf(lerp(accent, Color.White, 0.30f), accent)),
                        alpha = fill.value,
                    )
                }
            }
        }
        // Seals the bolt's silhouette notches into one clean accent field at the end of
        // the zoom, so the hand-over to the dashboard is seamless on any screen shape.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = cover.value }.background(accent))
    }
}

@Composable
fun PowerkApp() {
    val vm: StripsViewModel = viewModel()
    val focus = LocalFocusManager.current
    var splash by remember { mutableStateOf(true) }
    // The splash owns the first frames; the heavier navigation tree composes underneath it
    // a beat later so the cold start never drops frames while the logo animates.
    var uiReady by remember { mutableStateOf(false) }
    val splashReduced = AppReducedMotion
    LaunchedEffect(Unit) { delay(620); uiReady = true }
    // Long enough for trace + fill + full-screen zoom; near-instant in reduced motion.
    LaunchedEffect(Unit) { delay(if (splashReduced) 420L else 1210L); splash = false }
    Ticker.Run()
    LaunchedEffect(Unit) { Diag.runLoop() }
    DisposableEffect(Unit) {
        Diag.startFrameMonitor()
        onDispose { Diag.stopFrameMonitor() }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose {}
    }
    
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }) {
        if (uiReady) PowerkNavigation(vm)
        AnimatedVisibility(splash, exit = fadeOut(tween(260, easing = FastOutSlowInEasing))) {
            Splash()
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PowerkNavigation(vm: StripsViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination?.route.tabRoute()
    val context = LocalContext.current
    val route = entry?.destination?.route
    
    LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        Diag.route = route
        Diag.i("nav", route)
        delay(2600)
        context.findActivity()?.let { Diag.captureScreen(it, route.substringBefore('/').substringBefore('?')) }
    }
    
    fun goTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    val items = TABS.map { DockItem(it.icon, it.label(), if (it.route == ACTIVITY) vm.unread else 0) }
    val hazeState = rememberHazeState()
    val reducedMotion = AppReducedMotion
    // The rail keeps the owning tab highlighted while a detail screen is open.
    var railIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentTab) {
        val i = TABS.indexOfFirst { it.route == currentTab }
        if (i >= 0) railIndex = i
    }
    
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Adaptive shell: wide windows get a persistent navigation rail; compact ones keep
        // the floating dock. Content lives in a single NavHost in both cases so resizing
        // never recreates screens or loses navigation state.
        val expanded = maxWidth >= 600.dp
        val railPad by animateDpAsState(if (expanded) 100.dp else 0.dp, tween(240, easing = FastOutSlowInEasing), label = "railPad")
        Box(Modifier.fillMaxSize().padding(start = railPad)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            AmbientBackdrop()
            NavHost(
            nav,
            startDestination = HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { enterFor(initialState, targetState, reducedMotion) },
            exitTransition = { exitFor(initialState, targetState, reducedMotion) },
            popEnterTransition = { popEnterFor(initialState, targetState, reducedMotion) },
            popExitTransition = { popExitFor(initialState, targetState, reducedMotion) },
        ) {
            composable(HOME) {
                HomeScreen(
                    vm,
                    onOpenStrip = { nav.navigate("menu/${Uri.encode(it)}") },
                    onTab = { r -> if (TABS.any { it.route == r }) goTab(r) else nav.navigate(r) },
                )
            }
            composable(ENERGY_ROUTE, arguments = listOf(navArgument("strip") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                EnergyScreen(vm, it.arguments?.getString("strip"), onBack = null)
            }
            composable(AUTOMATIONS) { AutomationsScreen(vm) }
            composable(ACTIVITY) { ActivityScreen(vm) }
            composable(SETTINGS) { SettingsScreen(vm, onNavigate = { nav.navigate(it) }) }
            composable(STRIP, arguments = listOf(navArgument("mac") { type = NavType.StringType })) {
                val mac = it.arguments?.getString("mac").orEmpty()
                StripDetailScreen(
                    vm, mac,
                    onBack = { nav.popBackStack() },
                    onEnergy = { nav.navigate("energy?strip=${Uri.encode(mac)}") { launchSingleTop = true } },
                    onSchedules = { nav.navigate("schedules/${Uri.encode(mac)}") },
                )
            }
            composable(MENU, arguments = listOf(navArgument("mac") { type = NavType.StringType })) {
                val mac = it.arguments?.getString("mac").orEmpty()
                StripMenuScreen(vm, mac, onBack = { nav.popBackStack() }, onOpen = { r -> nav.navigate(r) })
            }
            composable(SCHEDULES, arguments = listOf(navArgument("mac") { type = NavType.StringType })) {
                SchedulesScreen(vm, it.arguments?.getString("mac").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(RULES) { AutomationHubScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
            composable(ROOMS) { RoomsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(RULE_TYPE, arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("mac") { type = NavType.StringType })) {
                RuleTypeScreen(vm, it.arguments?.getString("mac").orEmpty(), it.arguments?.getString("type").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
        }
        SnackbarHost(vm.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (expanded) 20.dp else 92.dp))
        AnimatedVisibility(
            !expanded && currentTab != null,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.8f, Spring.StiffnessMedium)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(120)),
        ) {
            FloatingDock(hazeState, items, TABS.indexOfFirst { it.route == currentTab }, { i -> if (currentTab != TABS[i].route) goTab(TABS[i].route) })
        }
        }
        AnimatedVisibility(
            expanded,
            Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { -it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(180)) { -it } + fadeOut(tween(120)),
        ) {
            NavRail(items, railIndex, onSelect = { i -> if (currentTab != TABS[i].route) goTab(TABS[i].route) })
        }
    }
    }
}

/** Base surface plus two very soft ambient colour fields, drawn in a single pass. */
@Composable
private fun AmbientBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() > 0.5f
    Box(
        Modifier.fillMaxSize().background(scheme.background).drawBehind {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = if (light) 0.10f else 0.17f), Color.Transparent),
                    center = Offset(w * 0.1f, h * 0.0f),
                    radius = maxOf(w, h) * 0.6f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(if (light) scheme.tertiary.copy(alpha = 0.07f) else scheme.primary.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(w * 0.98f, h * 0.82f),
                    radius = maxOf(w, h) * 0.5f,
                ),
            )
            // Soft floor vignette: gives the page a ground plane so lifted cards read above it.
            drawRect(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to (if (light) scheme.onSurface.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.22f)),
                ),
            )
        },
    )
}

private fun isTab(e: NavBackStackEntry) = e.destination.route.tabRoute() != null

private fun tabIndex(e: NavBackStackEntry): Int = TABS.indexOfFirst { it.route == e.destination.route.tabRoute() }

private fun tabDir(from: NavBackStackEntry, to: NavBackStackEntry): Int =
    if (tabIndex(to) >= tabIndex(from)) 1 else -1

private val tabSpring = spring<IntOffset>(dampingRatio = 0.88f, stiffness = 320f)
private val pageSpring = spring<IntOffset>(dampingRatio = 0.92f, stiffness = 400f)

private fun enterFor(from: NavBackStackEntry, to: NavBackStackEntry, reduced: Boolean): EnterTransition =
    if (reduced) {
        fadeIn(tween(140))
    } else if (isTab(from) && isTab(to)) {
        val dir = tabDir(from, to)
        slideInHorizontally(tabSpring) { w -> dir * w / 4 } +
            fadeIn(tween(200, easing = FastOutSlowInEasing)) +
            scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.94f)
    } else {
        slideInHorizontally(pageSpring) { w -> w / 4 } +
            fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.96f)
    }

private fun exitFor(from: NavBackStackEntry, to: NavBackStackEntry, reduced: Boolean): ExitTransition =
    if (reduced) {
        fadeOut(tween(110))
    } else if (isTab(from) && isTab(to)) {
        val dir = tabDir(from, to)
        slideOutHorizontally(pageSpring) { w -> -dir * w / 4 } +
            fadeOut(tween(170)) +
            scaleOut(tween(230, easing = FastOutSlowInEasing), targetScale = 0.97f)
    } else {
        fadeOut(tween(150)) + scaleOut(tween(240, easing = FastOutSlowInEasing), targetScale = 0.97f)
    }

private fun popEnterFor(from: NavBackStackEntry, to: NavBackStackEntry, reduced: Boolean): EnterTransition =
    if (reduced) {
        fadeIn(tween(140))
    } else if (isTab(from) && isTab(to)) {
        fadeIn(tween(200)) + scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.96f)
    } else {
        fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(tween(290, easing = FastOutSlowInEasing), initialScale = 0.98f)
    }

private fun popExitFor(from: NavBackStackEntry, to: NavBackStackEntry, reduced: Boolean): ExitTransition =
    if (reduced) {
        fadeOut(tween(110))
    } else if (isTab(from) && isTab(to)) {
        fadeOut(tween(150)) + scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.98f)
    } else {
        slideOutHorizontally(pageSpring) { w -> w / 4 } + fadeOut(tween(210))
    }
