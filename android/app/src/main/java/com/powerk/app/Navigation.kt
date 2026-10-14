package com.powerk.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.powerk.app.screens.ActivityScreen
import com.powerk.app.screens.AutomationsScreen
import com.powerk.app.screens.DashboardScreen
import com.powerk.app.screens.NotificationsScreen
import com.powerk.app.screens.PowerScreen
import com.powerk.app.screens.ScanScreen
import com.powerk.app.screens.SettingsScreen
import com.powerk.app.screens.StripDetailScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_POWER = "power"
private const val ROUTE_AUTOMATIONS = "automations"
private const val ROUTE_ACTIVITY = "activity"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_STRIP_DETAIL = "strip/{mac}"
private const val ROUTE_SCAN = "scan"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PowerkNavigation() {
    val navController = rememberNavController()
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateMapOf<String, Boolean>() }

    // unread notifications count for badge
    val unreadCount = 0 // will be updated when notifications load

    suspend fun refresh() {
        if (!Server.configured) return
        loading = true
        try { snapshot = Api.snapshot(Server.base, Server.token); error = null }
        catch (e: Exception) { error = e.message ?: e.javaClass.simpleName; snapshot = null }
        loading = false
    }

    LaunchedEffect(Unit) {
        while (true) { refresh(); delay(5000) }
    }

    fun command(mac: String, outlet: Int, on: Boolean) {
        val keys = if (outlet == 0) (1..4).map { "$mac:$it" } else listOf("$mac:$outlet")
        if (keys.any { pending.containsKey(it) }) return
        keys.forEach { pending[it] = on }
        scope.launch {
            try { Api.setOutlet(Server.base, Server.token, mac, outlet, on); refresh() }
            catch (e: Exception) { snackbar.showSnackbar(e.message ?: "command failed") }
            finally { keys.forEach { pending.remove(it) } }
        }
    }

    fun updateIcon(mac: String, outlet: Int?, icon: String) {
        scope.launch {
            try {
                if (outlet == null) Api.updateStripIcon(Server.base, Server.token, mac, icon)
                else Api.updateOutletIcon(Server.base, Server.token, mac, outlet, icon)
                refresh()
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "icon update failed") }
        }
    }

    val tabRoutes = listOf(ROUTE_DASHBOARD, ROUTE_POWER, ROUTE_AUTOMATIONS, ROUTE_ACTIVITY, ROUTE_NOTIFICATIONS, ROUTE_SETTINGS)

    // Standard bottom-nav pattern: pop up to (and save state for) the start destination, then
    // restore state when returning to a previously-visited tab. Without this, every tab tap
    // pushed a brand-new back-stack entry for that route with no popUpTo — the back stack grew
    // unbounded, and each entry gets its own ViewModelStore, so any screen-scoped ViewModel (e.g.
    // ProvisionViewModel) was silently recreated from scratch on every tab switch, which defeated
    // the point of moving the provisioning wizard's state/coroutine into a ViewModel in the first
    // place: it needs to survive switching tabs, not just surviving recomposition within one.
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                ShortNavigationBar {
                    ShortNavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; navigateToTab(ROUTE_DASHBOARD) },
                        icon = { Icon(Icons.Filled.Home, null) },
                        label = { Text(stringResource(R.string.tab_plugs)) },
                    )
                    ShortNavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; navigateToTab(ROUTE_POWER) },
                        icon = { Icon(Icons.Filled.Info, null) },
                        label = { Text(stringResource(R.string.tab_power)) },
                    )
                    ShortNavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; navigateToTab(ROUTE_AUTOMATIONS) },
                        icon = { Icon(Icons.Filled.Info, null) },
                        label = { Text(stringResource(R.string.tab_automations)) },
                    )
                    ShortNavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; navigateToTab(ROUTE_ACTIVITY) },
                        icon = { Icon(Icons.Filled.Warning, null) },
                        label = { Text(stringResource(R.string.tab_activity)) },
                    )
                    ShortNavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4; navigateToTab(ROUTE_NOTIFICATIONS) },
                        icon = {
                            BadgedBox(badge = { if (unreadCount > 0) Badge { Text("$unreadCount") } }) {
                                Icon(Icons.Default.Warning, null)
                            }
                        },
                        label = { Text(stringResource(R.string.tab_notifications)) },
                    )
                    ShortNavigationBarItem(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5; navigateToTab(ROUTE_SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_DASHBOARD,
                modifier = Modifier.padding(paddingValues),
                enterTransition = { fadeIn() + slideInHorizontally { it / 4 } },
                exitTransition = { fadeOut() + slideOutHorizontally { -it / 4 } },
                popEnterTransition = { fadeIn() + slideInHorizontally { -it / 4 } },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 4 } },
            ) {
                composable(ROUTE_DASHBOARD) {
                    selectedTab = 0
                    DashboardScreen(
                        snapshot = snapshot,
                        error = error,
                        loading = loading,
                        pending = pending,
                        onRefresh = { scope.launch { refresh() } },
                        onOpenSettings = { selectedTab = 5; navigateToTab(ROUTE_SETTINGS) },
                        onCommand = { mac, outlet, on -> command(mac, outlet, on) },
                        onIconChange = { mac, outlet, icon -> updateIcon(mac, outlet, icon) },
                        onStripTap = { mac -> navController.navigate("strip/$mac") },
                        onScan = { navController.navigate(ROUTE_SCAN) },
                    )
                }
                composable(ROUTE_POWER) { selectedTab = 1; PowerScreen(snapshot) }
                composable(ROUTE_AUTOMATIONS) { selectedTab = 2; AutomationsScreen(snapshot) }
                composable(ROUTE_ACTIVITY) { selectedTab = 3; ActivityScreen(snapshot) }
                composable(ROUTE_NOTIFICATIONS) { selectedTab = 4; NotificationsScreen() }
                composable(ROUTE_SETTINGS) { selectedTab = 5; SettingsScreen() }
                composable("strip/{mac}") { backStack ->
                    val mac = backStack.arguments?.getString("mac") ?: return@composable
                    StripDetailScreen(stripMac = mac, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_SCAN) {
                    ScanScreen(onStripAdopted = {
                        scope.launch { refresh() }
                        navController.popBackStack()
                    })
                }
            }
        }
    }
}
