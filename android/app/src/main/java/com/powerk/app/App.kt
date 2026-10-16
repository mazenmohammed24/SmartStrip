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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.powerk.app.components.DockItem
import com.powerk.app.components.FloatingDock
import com.powerk.app.components.Ticker
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
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
    val primary = MaterialTheme.colorScheme.primary
    val scale = remember { Animatable(0.5f) }
    val glow = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(0.55f, Spring.StiffnessLow)) }
        launch { glow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        launch { delay(280); text.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(320.dp).graphicsLayer { alpha = glow.value * 0.9f; scaleX = 0.7f + glow.value * 0.3f; scaleY = 0.7f + glow.value * 0.3f }
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.42f), Color.Transparent))),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.size(128.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = (scale.value * 1.6f - 0.6f).coerceIn(0f, 1f) },
            )
            Text(
                stringResource(R.string.app_name).uppercase(),
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (4 + (1f - text.value) * 8).sp,
                modifier = Modifier.graphicsLayer { alpha = text.value },
            )
        }
    }
}
@Composable
fun PowerkApp() {
    val vm: StripsViewModel = viewModel()
    val focus = LocalFocusManager.current
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1050); splash = false }
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
        PowerkNavigation(vm)
        AnimatedVisibility(splash, exit = fadeOut(tween(380, easing = FastOutSlowInEasing)) + scaleOut(tween(380, easing = FastOutSlowInEasing), targetScale = 2.4f)) {
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
        delay(1600)
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
    
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            nav,
            startDestination = HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { enterFor(initialState, targetState) },
            exitTransition = { exitFor(initialState, targetState) },
            popEnterTransition = { popEnterFor(initialState, targetState) },
            popExitTransition = { popExitFor(initialState, targetState) },
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
        SnackbarHost(vm.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp))
        AnimatedVisibility(
            currentTab != null,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.8f, Spring.StiffnessMedium)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(120)),
        ) {
            FloatingDock(items, TABS.indexOfFirst { it.route == currentTab }, { i -> if (currentTab != TABS[i].route) goTab(TABS[i].route) })
        }
    }
}

private fun isTab(e: NavBackStackEntry) = e.destination.route.tabRoute() != null

private val fadeShort = tween<Float>(150)

private fun enterFor(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
    if (isTab(from) && isTab(to)) fadeIn(fadeShort)
    else slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(220))

private fun exitFor(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
    if (isTab(from) && isTab(to)) fadeOut(tween(100))
    else fadeOut(tween(120))

private fun popEnterFor(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
    if (isTab(from) && isTab(to)) fadeIn(fadeShort) else fadeIn(tween(180))

private fun popExitFor(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
    if (isTab(from) && isTab(to)) fadeOut(tween(100))
    else slideOutHorizontally(tween(200)) { it / 5 } + fadeOut(tween(200))
