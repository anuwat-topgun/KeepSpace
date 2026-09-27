import SwiftUI

/// Adaptive app shell:
/// - compact width (iPhone, iPad slide-over / narrow split) → bottom tab bar
/// - regular width (iPad full screen / wide split) → sidebar + detail stack
struct RootView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var router = AppRouter()
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false

    var body: some View {
        Group {
            if sizeClass == .regular {
                SidebarShell()
            } else {
                TabShell()
            }
        }
        .environment(router)
        .tint(Palette.accent)
        #if DEBUG
        .task { router.applyDebugLaunchArguments() }
        #endif
        .fullScreenCover(isPresented: showsOnboarding) {
            OnboardingView {
                hasCompletedOnboarding = true
            }
        }
    }

    private var showsOnboarding: Binding<Bool> {
        Binding(get: { !hasCompletedOnboarding }, set: { hasCompletedOnboarding = !$0 })
    }
}

private struct TabShell: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        TabView(selection: tabSelection) {
            ForEach(AppTab.allCases) { tab in
                TabStack(tab: tab)
                    .tabItem { Label(tab.title, systemImage: tab.systemImage) }
                    .tag(tab)
            }
        }
    }

    /// Tapping the already-selected tab pops that tab to its root.
    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { router.selectedTab },
            set: { newValue in
                if newValue == router.selectedTab { router.popToRoot(newValue) }
                router.selectedTab = newValue
            }
        )
    }
}

private struct SidebarShell: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        @Bindable var router = router
        NavigationSplitView(columnVisibility: $router.sidebarVisibility) {
            List(AppTab.allCases, selection: sidebarSelection) { tab in
                Label(tab.title, systemImage: tab.systemImage)
                    .font(.system(.body, weight: .medium))
                    .padding(.vertical, 4)
            }
            .navigationTitle("KeepSpace")
            .navigationSplitViewColumnWidth(min: 220, ideal: 260, max: 300)
        } detail: {
            // Keep the navigation bar in split view so the sidebar toggle stays reachable.
            TabStack(tab: router.selectedTab, hidesRootNavigationBar: false)
                .id(router.selectedTab)
        }
        .navigationSplitViewStyle(.balanced)
    }

    private var sidebarSelection: Binding<AppTab?> {
        Binding(
            get: { router.selectedTab },
            set: { if let tab = $0 { router.selectedTab = tab } }
        )
    }
}

/// One tab's navigation stack: root screen plus pushed routes.
struct TabStack: View {
    let tab: AppTab
    var hidesRootNavigationBar = true
    @Environment(AppRouter.self) private var router

    var body: some View {
        NavigationStack(path: router.path(for: tab)) {
            root
                .toolbar(hidesRootNavigationBar ? .hidden : .automatic, for: .navigationBar)
                .navigationBarTitleDisplayMode(.inline)
                .navigationDestination(for: Route.self) { route in
                    RouteDestination(route: route)
                        .navigationBarTitleDisplayMode(.inline)
                }
        }
    }

    @ViewBuilder
    private var root: some View {
        switch tab {
        case .home: HomeView()
        case .clean: CleanView()
        case .library: LibraryView()
        case .insights: InsightsView()
        case .settings: SettingsView()
        }
    }
}

/// Maps routes to screens. Screens not built yet show a styled placeholder.
struct RouteDestination: View {
    let route: Route

    var body: some View {
        switch route {
        case .cleanupPlan: CleanupPlanView()
        case .similarPhotos: SimilarPhotosView()
        case .bestShot(let groupID):
            if let group = MockData.photoGroups.first(where: { $0.id == groupID }) {
                BestShotView(group: group)
            } else {
                ContentUnavailableView("Group not found", systemImage: "photo.on.rectangle.angled")
            }
        case .screenshots: ScreenshotsView()
        case .videos: VideosView()
        case .memories: MemoriesView()
        default: PlaceholderScreen(title: route.title)
        }
    }
}

#Preview("iPhone") {
    RootView()
}

#Preview("iPad", traits: .landscapeLeft) {
    RootView()
}
