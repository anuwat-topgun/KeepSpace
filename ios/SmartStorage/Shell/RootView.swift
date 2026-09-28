import SwiftUI

/// Adaptive app shell:
/// - compact width (iPhone, iPad slide-over / narrow split) → bottom tab bar
/// - regular width (iPad full screen / wide split) → sidebar + detail stack
struct RootView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var scenePhase
    @State private var router = AppRouter()
    /// `-demoData YES` shows the mockup data set (screenshots, demos, simulators without photos).
    @State private var library = LibraryStore(demo: UserDefaults.standard.bool(forKey: "demoData"))
    @State private var rules = RuleStore()
    @State private var weekly = WeeklyCleanReminder()
    @State private var cloud = CloudStore()
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false

    var body: some View {
        Group {
            if sizeClass == .regular {
                SidebarShell()
            } else {
                TabShell()
            }
        }
        .overlay(alignment: .top) {
            NoticeBanner()
                .padding(.top, 8)
                .animation(.spring(duration: 0.35), value: library.notice)
        }
        .environment(router)
        .environment(library)
        .environment(rules)
        .environment(weekly)
        .environment(cloud)
        .environment(library.taste)
        .tint(Palette.accent)
        // Keep the reminder's text current, and open the Cleanup Plan when a reminder is tapped.
        .onChange(of: library.phase) { _, phase in
            if phase == .ready, !library.isDemo {
                Task { await weekly.recordScan(potentialBytes: library.content.storage.potentialCleanupBytes) }
            }
        }
        .task { await weekly.refreshPermission(); openFromNotificationIfPending() }
        .onReceive(NotificationCenter.default.publisher(for: .openFromNotification)) { _ in openFromNotificationIfPending() }
        #if DEBUG
        .task { router.applyDebugLaunchArguments() }
        #endif
        .onChange(of: scenePhase, initial: true) { _, phase in
            // Also covers returning from Settings after changing photo access.
            if phase == .active, hasCompletedOnboarding { library.refreshAccess() }
            if phase == .active { cloud.resumePending() }
        }
        .fullScreenCover(isPresented: showsOnboarding) {
            OnboardingView {
                hasCompletedOnboarding = true
                Task { await library.requestAccessAndScan() }
            }
            .environment(library)
        }
    }

    private func openFromNotificationIfPending() {
        guard NotificationTarget.pending == NotificationTarget.cleanupPlan else { return }
        NotificationTarget.pending = nil
        router.selectedTab = .clean
        router.popToRoot(.clean)
        router.push(.cleanupPlan)
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
    @Environment(LibraryStore.self) private var library
    @Environment(RuleStore.self) private var rules

    var body: some View {
        switch route {
        case .cleanupPlan: CleanupPlanView()
        case .similarPhotos: SimilarPhotosView()
        case .bestShot(let groupID):
            if let group = library.content.photoGroups.first(where: { $0.id == groupID }) ?? debugFirstGroup(groupID) {
                BestShotView(group: group)
            } else {
                ContentUnavailableView("Group not found", systemImage: "photo.on.rectangle.angled")
            }
        case .screenshots: ScreenshotsView()
        case .videos: VideosView()
        case .memories: MemoriesView()
        case .memory(let id): MemoryDetailView(memoryID: id)
        case .photoAccess: PhotoAccessView()
        case .notifications: NotificationsView()
        case .aiTaste: AITasteView()
        case .privacy: PrivacyView()
        case .about: AboutView()
        case .cloudOverview: CloudOverviewView()
        case .manualBackup: ManualBackupView()
        case .review(let kind): ReviewView(source: .kind(kind))
        case .receipts: ReceiptsView()
        case .storageRules: StorageRulesView()
        case .newRule: RuleEditorView(ruleID: nil, existing: nil)
        case .editRule(let id): RuleEditorView(ruleID: id, existing: rules.rules.first { $0.id == id })
        case .receiptFiling(let id): ReceiptFilingView(receiptID: id)
        case .reviewGroup(let id): ReviewView(source: .group(id))
        case .backupVerification: PlaceholderScreen(title: route.title)
        }
    }

    /// Debug launch args can't know real group IDs; `bestShot:first` opens the top group.
    private func debugFirstGroup(_ id: String) -> PhotoGroup? {
        #if DEBUG
        id == "first" ? library.content.photoGroups.first : nil
        #else
        nil
        #endif
    }
}

#Preview("iPhone") {
    RootView()
}

#Preview("iPad", traits: .landscapeLeft) {
    RootView()
}
