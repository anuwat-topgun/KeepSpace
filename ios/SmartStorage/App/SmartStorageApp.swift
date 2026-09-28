import SwiftUI
import UserNotifications

@main
struct SmartStorageApp: App {
    /// Kept alive for the app's lifetime: the notification center only holds its delegate weakly.
    private let notifications = NotificationHandler()

    init() {
        UNUserNotificationCenter.current().delegate = notifications
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
