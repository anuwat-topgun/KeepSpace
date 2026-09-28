import Foundation

/// When the Weekly Smart Clean reminder fires. Weekdays are ISO (1 = Monday … 7 = Sunday) on both
/// platforms; iOS calendars count from Sunday, hence `calendarWeekday`. Pure, for tests. Mirrors WeeklyClean.kt.
struct WeeklyCleanSchedule: Equatable, Sendable {
    var weekday = 7
    var hour = 10
    var minute = 0

    /// `Calendar` weekday: 1 = Sunday … 7 = Saturday.
    var calendarWeekday: Int { weekday % 7 + 1 }

    /// The next time this slot occurs strictly after `date`.
    func nextFire(after date: Date, calendar: Calendar = .current) -> Date {
        var parts = DateComponents()
        parts.weekday = calendarWeekday
        parts.hour = hour
        parts.minute = minute
        return calendar.nextDate(after: date, matching: parts, matchingPolicy: .nextTime) ?? date.addingTimeInterval(7 * 86_400)
    }
}

/// The notification text, from what the last scan found. Never mentions files or people.
enum WeeklyCleanMessage {
    static let title = "Weekly Smart Clean"
    /// Below this the reminder doesn't promise savings.
    static let meaningfulBytes: Int64 = 50_000_000
    /// A scan older than this may no longer reflect the library.
    static let staleAfter: TimeInterval = 30 * 86_400

    static func body(potentialBytes: Int64?, lastScan: Date?, now: Date = .now) -> String {
        guard let potentialBytes, let lastScan, now.timeIntervalSince(lastScan) <= staleAfter else {
            return "Open KeepSpace to look for space you can recover."
        }
        if potentialBytes >= meaningfulBytes {
            return "About \(potentialBytes.formattedBytes) can be cleaned up. Review the safest items first."
        }
        return "Your library looks tidy. Tap to check again."
    }
}
