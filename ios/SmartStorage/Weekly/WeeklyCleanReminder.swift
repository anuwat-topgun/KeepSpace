import Foundation
import Observation
@preconcurrency import UserNotifications

/// Weekly Smart Clean reminder: opt-in, a repeating local notification whose text comes from the
/// last completed scan. There's no silent background scan — the number is as fresh as the last time
/// the app looked, and the screen says so. Nothing leaves the device.
@MainActor
@Observable
final class WeeklyCleanReminder {
    enum Permission { case unknown, allowed, denied }

    private(set) var isEnabled: Bool
    private(set) var schedule: WeeklyCleanSchedule
    private(set) var permission: Permission = .unknown
    private(set) var lastScan: Date?
    private(set) var potentialBytes: Int64?

    static let requestID = "weeklySmartClean"
    private let defaults: UserDefaults
    private let center: UNUserNotificationCenter

    init(defaults: UserDefaults = .standard, center: UNUserNotificationCenter = .current()) {
        self.defaults = defaults
        self.center = center
        isEnabled = defaults.bool(forKey: Key.enabled)
        schedule = WeeklyCleanSchedule(weekday: defaults.object(forKey: Key.weekday) as? Int ?? 7,
                                       hour: defaults.object(forKey: Key.hour) as? Int ?? 10,
                                       minute: defaults.object(forKey: Key.minute) as? Int ?? 0)
        lastScan = defaults.object(forKey: Key.lastScan) as? Date
        potentialBytes = defaults.object(forKey: Key.bytes) as? Int64
    }

    func refreshPermission() async {
        switch await center.notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: permission = .allowed
        case .denied: permission = .denied
        default: permission = .unknown
        }
        if isEnabled, permission == .allowed { await reschedule() }
    }

    /// Turning it on asks for permission (once); it stays off if the person says no.
    func setEnabled(_ on: Bool) async {
        guard on else {
            isEnabled = false
            defaults.set(false, forKey: Key.enabled)
            center.removePendingNotificationRequests(withIdentifiers: [Self.requestID])
            return
        }
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        permission = granted ? .allowed : .denied
        isEnabled = granted
        defaults.set(granted, forKey: Key.enabled)
        if granted { await reschedule() }
    }

    func setSchedule(_ new: WeeklyCleanSchedule) async {
        schedule = new
        defaults.set(new.weekday, forKey: Key.weekday)
        defaults.set(new.hour, forKey: Key.hour)
        defaults.set(new.minute, forKey: Key.minute)
        if isEnabled { await reschedule() }
    }

    /// Called after every completed scan so the next reminder quotes the latest result.
    func recordScan(potentialBytes bytes: Int64, at date: Date = .now) async {
        potentialBytes = bytes
        lastScan = date
        defaults.set(bytes, forKey: Key.bytes)
        defaults.set(date, forKey: Key.lastScan)
        if isEnabled, permission == .allowed { await reschedule() }
    }

    /// Fires in a few seconds, so the notification can be seen without waiting for the weekly slot.
    func sendTest() async {
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional || status == .ephemeral else { return }
        let request = UNNotificationRequest(identifier: "weeklySmartClean.test", content: content(),
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false))
        try? await center.add(request)
    }

    private func reschedule() async {
        center.removePendingNotificationRequests(withIdentifiers: [Self.requestID])
        var parts = DateComponents()
        parts.weekday = schedule.calendarWeekday
        parts.hour = schedule.hour
        parts.minute = schedule.minute
        let request = UNNotificationRequest(identifier: Self.requestID, content: content(),
                                            trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: true))
        try? await center.add(request)
    }

    private func content() -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = WeeklyCleanMessage.title
        content.body = WeeklyCleanMessage.body(potentialBytes: potentialBytes, lastScan: lastScan)
        content.sound = .default
        content.userInfo = ["open": NotificationTarget.cleanupPlan]
        return content
    }

    private enum Key {
        static let enabled = "weeklyClean.enabled"
        static let weekday = "weeklyClean.weekday"
        static let hour = "weeklyClean.hour"
        static let minute = "weeklyClean.minute"
        static let lastScan = "weeklyClean.lastScan"
        static let bytes = "weeklyClean.potentialBytes"
    }
}

/// What a tapped notification should open.
enum NotificationTarget {
    static let cleanupPlan = "cleanupPlan"
    /// Set when a tap arrives before the UI can react (cold launch); the root view consumes it.
    @MainActor static var pending: String?
}

/// Shows reminders while the app is open and routes taps to the Cleanup Plan.
final class NotificationHandler: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let target = response.notification.request.content.userInfo["open"] as? String else { return }
        await MainActor.run {
            NotificationTarget.pending = target
            NotificationCenter.default.post(name: .openFromNotification, object: nil)
        }
    }
}

extension Notification.Name {
    static let openFromNotification = Notification.Name("keepspace.openFromNotification")
}
