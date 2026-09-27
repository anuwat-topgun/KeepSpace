package com.smartstorage.cleaner.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import com.smartstorage.cleaner.ui.feature.clean.CleanScreen
import com.smartstorage.cleaner.ui.feature.home.HomeScreen
import com.smartstorage.cleaner.ui.feature.insights.InsightsScreen
import com.smartstorage.cleaner.ui.feature.library.LibraryScreen
import com.smartstorage.cleaner.ui.feature.placeholder.PlaceholderScreen
import com.smartstorage.cleaner.ui.feature.settings.SettingsScreen
import com.smartstorage.cleaner.ui.theme.SmartTheme

/**
 * Adaptive app shell:
 * - compact width (phones) → bottom navigation bar
 * - medium width, or any short window (phone landscape, unfolded foldable) → navigation rail
 * - expanded width (tablets, desktop windows) → permanent sidebar drawer
 */
@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = AppTab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: AppTab.Home

    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val layoutType = when {
        sizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT -> NavigationSuiteType.NavigationBar
        sizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT -> NavigationSuiteType.NavigationRail
        sizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED -> NavigationSuiteType.NavigationDrawer
        else -> NavigationSuiteType.NavigationRail
    }

    val colors = SmartTheme.colors
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = colors.surface,
            navigationRailContainerColor = colors.surface,
            navigationDrawerContainerColor = colors.surface,
        ),
        navigationSuiteItems = {
            AppTab.entries.forEach { tab ->
                item(
                    selected = tab == currentTab,
                    onClick = { navController.selectTab(tab, currentTab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.title) },
                )
            }
        },
        containerColor = colors.background,
    ) {
        AppNavHost(navController)
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = AppTab.Home.route) {
        AppTab.entries.forEach { tab ->
            navigation(startDestination = tab.rootRoute, route = tab.route) {
                composable(tab.rootRoute) {
                    val push: (Screen) -> Unit = { navController.navigate(tab.screenRoute(it)) }
                    when (tab) {
                        AppTab.Home -> HomeScreen(
                            onOpen = push,
                            onFreeUp = { navController.selectTab(AppTab.Clean, tab) },
                        )
                        AppTab.Clean -> CleanScreen(onOpen = push)
                        AppTab.Library -> LibraryScreen(onOpen = push)
                        AppTab.Insights -> InsightsScreen()
                        AppTab.Settings -> SettingsScreen(onOpen = push)
                    }
                }
                composable(tab.screenPattern) { entry ->
                    val screen = entry.arguments?.getString(AppTab.SCREEN_ARG)
                        ?.let { name -> Screen.entries.firstOrNull { it.name == name } }
                        ?: Screen.CleanupPlan
                    PlaceholderScreen(title = screen.title, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/**
 * Switches tabs while keeping each tab's back stack (standard multi-back-stack pattern).
 * Re-selecting the active tab pops it to its root, matching platform behaviour.
 */
private fun NavHostController.selectTab(tab: AppTab, currentTab: AppTab) {
    if (tab == currentTab) {
        popBackStack(tab.rootRoute, inclusive = false)
        return
    }
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
