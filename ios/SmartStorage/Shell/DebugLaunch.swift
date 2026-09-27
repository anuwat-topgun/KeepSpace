#if DEBUG
import Foundation

/// Debug-only deep links for screenshot runs and UI checks, e.g.
/// `xcrun simctl launch <device> com.keepspace.app -hasCompletedOnboarding YES -debugTab library -debugRoute bestShot:beach`
extension AppRouter {
    func applyDebugLaunchArguments(_ defaults: UserDefaults = .standard) {
        if let tab = defaults.string(forKey: "debugTab").flatMap(AppTab.init(rawValue:)) {
            selectedTab = tab
        }
        if let value = defaults.string(forKey: "debugRoute"), let route = Route(debugName: value) {
            push(route)
        }
    }
}

private extension Route {
    init?(debugName: String) {
        let parts = debugName.split(separator: ":", maxSplits: 1).map(String.init)
        switch parts[0] {
        case "cleanupPlan": self = .cleanupPlan
        case "similarPhotos": self = .similarPhotos
        case "bestShot": self = .bestShot(groupID: parts.count > 1 ? parts[1] : "beach")
        case "screenshots": self = .screenshots
        case "videos": self = .videos
        case "memories": self = .memories
        case "review": self = .review(parts.count > 1 ? ReviewKind(debugName: parts[1]) ?? .similar : .similar)
        case "cloudOverview": self = .cloudOverview
        case "manualBackup": self = .manualBackup
        case "storageRules": self = .storageRules
        case "newRule": self = .newRule
        case "receiptFiling": self = .receiptFiling
        case "backupVerification": self = .backupVerification
        default: return nil
        }
    }
}
#endif
