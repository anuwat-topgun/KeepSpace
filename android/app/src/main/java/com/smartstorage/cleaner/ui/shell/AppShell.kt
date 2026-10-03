package com.smartstorage.cleaner.ui.shell

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import com.smartstorage.cleaner.ui.feature.review.ReviewSource
import com.smartstorage.cleaner.ui.feature.review.ReviewScreen
import com.smartstorage.cleaner.ui.feature.rules.RuleEditorScreen
import com.smartstorage.cleaner.ui.feature.rules.StorageRulesScreen
import com.smartstorage.cleaner.ui.feature.receipts.ReceiptFilingScreen
import com.smartstorage.cleaner.ui.feature.receipts.ReceiptsScreen
import com.smartstorage.cleaner.media.ReviewKind
import androidx.compose.material3.Icon
import com.smartstorage.cleaner.ui.i18n.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.smartstorage.cleaner.ui.feature.settings.SubscriptionScreen
import com.smartstorage.cleaner.monetization.ProFeature
import com.smartstorage.cleaner.ui.feature.paywall.ProGate
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import com.smartstorage.cleaner.media.libraryState
import com.smartstorage.cleaner.ui.feature.clean.CleanScreen
import com.smartstorage.cleaner.ui.feature.clean.CleanupPlanScreen
import com.smartstorage.cleaner.ui.feature.home.HomeScreen
import com.smartstorage.cleaner.ui.feature.insights.InsightsScreen
import com.smartstorage.cleaner.ui.feature.library.BestShotScreen
import com.smartstorage.cleaner.ui.feature.library.LibraryScreen
import com.smartstorage.cleaner.ui.feature.library.MemoriesScreen
import com.smartstorage.cleaner.ui.feature.library.MemoryDetailScreen
import com.smartstorage.cleaner.ui.feature.library.ScreenshotsScreen
import com.smartstorage.cleaner.ui.feature.library.SimilarPhotosScreen
import com.smartstorage.cleaner.ui.feature.library.VideosScreen
import com.smartstorage.cleaner.ui.feature.placeholder.PlaceholderScreen
import com.smartstorage.cleaner.ui.feature.settings.AboutScreen
import com.smartstorage.cleaner.ui.feature.settings.AiTasteScreen
import com.smartstorage.cleaner.ui.feature.settings.NotificationsScreen
import com.smartstorage.cleaner.ui.feature.settings.PhotoAccessScreen
import com.smartstorage.cleaner.ui.feature.settings.PrivacyScreen
import com.smartstorage.cleaner.ui.feature.settings.SettingsScreen
import com.smartstorage.cleaner.ui.feature.cloud.CloudOverviewScreen
import com.smartstorage.cleaner.ui.feature.cloud.ManualBackupScreen
import com.smartstorage.cleaner.ui.theme.SmartTheme

/**
 * Adaptive app shell:
 * - compact width (phones) → bottom navigation bar
 * - medium width, or any short window (phone landscape, unfolded foldable) → navigation rail
 * - expanded width (tablets, desktop windows) → permanent sidebar drawer
 */
@Composable
fun AppShell(openCleanupPlanRequest: Int = 0) {
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

    // A tapped Weekly Smart Clean reminder opens the Cleanup Plan, from wherever the app was.
    LaunchedEffect(openCleanupPlanRequest) {
        if (openCleanupPlanRequest > 0) {
            navController.selectTab(AppTab.Clean, currentTab)
            navController.popBackStack(AppTab.Clean.rootRoute, inclusive = false)
            navController.navigate(AppTab.Clean.screenRoute(Screen.CleanupPlan))
        }
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
                    label = { TabLabel(tab.title) },
                )
            }
        },
        containerColor = colors.background,
    ) {
        Box {
            AppNavHost(navController)
            NoticeBanner(Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * A tab label that stays on one line. Long translations (e.g. the German word for Settings) shrink a little until they fit the tab
 * instead of wrapping in the middle of a word; below 8sp they are cut with an ellipsis rather than shrink further.
 */
@Composable
private fun TabLabel(title: String) {
    var size by remember(title) { mutableFloatStateOf(12f) }
    Text(
        title,
        fontSize = size.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.isLineEllipsized(0) && size > 8f) size -= 0.5f },
    )
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = AppTab.Home.route) {
        AppTab.entries.forEach { tab ->
            navigation(startDestination = tab.rootRoute, route = tab.route) {
                val push: (Screen) -> Unit = { navController.navigate(tab.screenRoute(it)) }
                val back: () -> Unit = { navController.popBackStack() }
                val openGroup: (String) -> Unit = { navController.navigate(tab.bestShotRoute(it)) }
                val review: (ReviewKind) -> Unit = { navController.navigate(tab.reviewRoute(it)) }

                composable(tab.rootRoute) {
                    when (tab) {
                        AppTab.Home -> HomeScreen(
                            onOpen = push,
                            onReview = review,
                            onFreeUp = { navController.selectTab(AppTab.Clean, tab) },
                        )
                        AppTab.Clean -> CleanScreen(onOpen = push)
                        AppTab.Library -> LibraryScreen(onOpen = push)
                        AppTab.Insights -> InsightsScreen(
                            onOpenPhotos = { push(Screen.SimilarPhotos) },
                            onOpenVideos = { push(Screen.Videos) },
                            onSmartClean = { navController.selectTab(AppTab.Clean, tab) },
                        )
                        AppTab.Settings -> SettingsScreen(onOpen = push)
                    }
                }
                composable(tab.screenPattern) { entry ->
                    val screen = entry.arguments?.getString(AppTab.SCREEN_ARG)
                        ?.let { name -> Screen.entries.firstOrNull { it.name == name } }
                        ?: Screen.CleanupPlan
                    when (screen) {
                        Screen.CleanupPlan -> CleanupPlanScreen(onOpen = push, onReview = review, onBack = back)
                        Screen.SimilarPhotos -> SimilarPhotosScreen(onOpenGroup = openGroup, onBack = back)
                        Screen.Screenshots -> ScreenshotsScreen(onReview = review, onBack = back)
                        Screen.StorageRules -> StorageRulesScreen(
                            onAdd = { push(Screen.NewRule) },
                            onEdit = { navController.navigate(tab.ruleRoute(it)) },
                            onBack = back,
                        )
                        Screen.NewRule -> RuleEditorScreen(ruleId = null, onDone = back)
                        Screen.Receipts -> ProGate(ProFeature.ReceiptFiling, back) { ReceiptsScreen(onOpen = { navController.navigate(tab.receiptRoute(it)) }, onBack = back) }
                        Screen.PhotoAccess -> PhotoAccessScreen(onBack = back)
                        Screen.Notifications -> NotificationsScreen(onBack = back)
                        Screen.CloudOverview -> CloudOverviewScreen(onBackUpNow = { push(Screen.ManualBackup) }, onBack = back)
                        Screen.ManualBackup -> ManualBackupScreen(onBack = back)
                        Screen.AiTaste -> AiTasteScreen(onBack = back)
                        Screen.Privacy -> PrivacyScreen(onBack = back)
                        Screen.About -> AboutScreen(onBack = back)
                        Screen.Subscription -> SubscriptionScreen(onBack = back)
                        Screen.Videos -> VideosScreen(onReview = review, onBack = back)
                        Screen.Memories -> MemoriesScreen(
                            onOpen = { navController.navigate(tab.memoryRoute(it)) },
                            onOpenSimilar = { push(Screen.SimilarPhotos) },
                            onBack = back,
                        )
                        else -> PlaceholderScreen(title = screen.title, onBack = back)
                    }
                }
                composable(tab.reviewPattern) { entry ->
                    val kind = entry.arguments?.getString(AppTab.REVIEW_ARG)?.let(ReviewKind::fromKey) ?: ReviewKind.Similar
                    ReviewScreen(ReviewSource.Kind(kind), onBack = back)
                }
                composable(tab.reviewGroupPattern) { entry ->
                    ReviewScreen(ReviewSource.Group(entry.arguments?.getString(AppTab.GROUP_ARG).orEmpty()), onBack = back)
                }
                composable(tab.rulePattern) { entry ->
                    RuleEditorScreen(ruleId = entry.arguments?.getString(AppTab.RULE_ARG), onDone = back)
                }
                composable(tab.memoryPattern) { entry ->
                    MemoryDetailScreen(
                        memoryId = entry.arguments?.getString(AppTab.MEMORY_ARG).orEmpty(),
                        onOpenSimilar = { push(Screen.SimilarPhotos) },
                        onReviewBlurry = { review(ReviewKind.Blurry) },
                        onBack = back,
                    )
                }
                composable(tab.receiptPattern) { entry ->
                    ProGate(ProFeature.ReceiptFiling, back) {
                        ReceiptFilingScreen(
                            receiptId = entry.arguments?.getString(AppTab.RECEIPT_ARG).orEmpty(),
                            onConnectCloud = { push(Screen.CloudOverview) },
                            onReviewRule = { push(Screen.StorageRules) },
                            onBack = back,
                        )
                    }
                }
                composable(tab.bestShotPattern) { entry ->
                    val id = entry.arguments?.getString(AppTab.GROUP_ARG)
                    val group = libraryState().content.photoGroups.firstOrNull { it.id == id }
                    if (group != null) {
                        BestShotScreen(group, onBack = back)
                    } else {
                        PlaceholderScreen(title = "Group not found", onBack = back)
                    }
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
