import SwiftUI

/// The five top-level destinations (design handoff §3).
enum AppTab: String, CaseIterable, Identifiable, Hashable, Sendable {
    case home, clean, library, insights, settings

    var id: Self { self }

    var title: String {
        switch self {
        case .home: "Home"
        case .clean: "Clean"
        case .library: "Library"
        case .insights: "Insights"
        case .settings: "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house.fill"
        case .clean: "sparkle"
        case .library: "photo.stack.fill"
        case .insights: "chart.bar.fill"
        case .settings: "gearshape.fill"
        }
    }
}

/// Screens pushed inside a tab's navigation stack.
enum Route: Hashable, Sendable {
    // v1.0
    case cleanupPlan
    case similarPhotos
    case bestShot(groupID: String)
    case screenshots
    case videos
    case memories
    case memory(id: String)
    case photoAccess
    case privacy
    case about
    case review(ReviewKind)
    case reviewGroup(groupID: String)
    // v1.1
    case cloudOverview
    case manualBackup
    // v1.2
    case storageRules
    case newRule
    case editRule(id: UUID)
    // v1.3
    case receipts
    case receiptFiling(id: String)
    case backupVerification

    var title: String {
        switch self {
        case .cleanupPlan: "Cleanup Plan"
        case .similarPhotos: "Similar Photos"
        case .bestShot: "Best Shot"
        case .screenshots: "Screenshots"
        case .videos: "Videos"
        case .memories: "Memories"
        case .memory: "Memory"
        case .photoAccess: "Photo Access"
        case .privacy: "Privacy & Security"
        case .about: "About"
        case .review(let kind): kind.title
        case .reviewGroup: "Review Group"
        case .cloudOverview: "Cloud"
        case .manualBackup: "Back Up Now"
        case .storageRules: "Storage Rules"
        case .newRule: "New Rule"
        case .editRule: "Edit Rule"
        case .receipts, .receiptFiling: "Receipt Filing"
        case .backupVerification: "Backed Up"
        }
    }
}

/// Owns tab selection and one navigation path per tab. Shared by the phone tab bar and the
/// iPad sidebar so state survives size-class changes (rotation, Split View, Stage Manager).
@MainActor
@Observable
final class AppRouter {
    var selectedTab: AppTab = .home
    /// iPad sidebar visibility; lives here so it survives size-class changes like the paths do.
    var sidebarVisibility: NavigationSplitViewVisibility = .all
    private var paths: [AppTab: NavigationPath] = [:]

    func path(for tab: AppTab) -> Binding<NavigationPath> {
        Binding(
            get: { self.paths[tab] ?? NavigationPath() },
            set: { self.paths[tab] = $0 }
        )
    }

    func push(_ route: Route) {
        paths[selectedTab, default: NavigationPath()].append(route)
    }

    /// Re-selecting the active tab pops to its root, matching system behaviour.
    func popToRoot(_ tab: AppTab) {
        paths[tab] = NavigationPath()
    }
}
