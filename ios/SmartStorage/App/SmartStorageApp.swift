import SwiftUI
import UserNotifications
import GoogleSignIn

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
}

@main
struct SmartStorageApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    /// Kept alive for the app's lifetime: the notification center only holds its delegate weakly.
    private let notifications = NotificationHandler()
    @State private var language = AppLanguageStore()

    init() {
        UNUserNotificationCenter.current().delegate = notifications
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(language)
                .environment(\.locale, language.locale)
        }
    }
}
