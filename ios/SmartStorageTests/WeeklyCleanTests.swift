import Foundation
import Testing
@testable import SmartStorage

struct WeeklyCleanTests {
    private var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Bangkok")!
        return c
    }

    private func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    @Test func isoWeekdayMapsToCalendarWeekday() {
        #expect(WeeklyCleanSchedule(weekday: 7).calendarWeekday == 1) // Sunday
        #expect(WeeklyCleanSchedule(weekday: 1).calendarWeekday == 2) // Monday
        #expect(WeeklyCleanSchedule(weekday: 6).calendarWeekday == 7) // Saturday
    }

    @Test func nextFireIsTheNextMatchingSlot() {
        // 28 Sep 2026 is a Monday.
        let schedule = WeeklyCleanSchedule(weekday: 7, hour: 10, minute: 30) // Sunday 10:30
        #expect(schedule.nextFire(after: date(2026, 9, 28, 9), calendar: calendar) == date(2026, 10, 4, 10, 30))
        // Exactly on the slot → the following week.
        #expect(schedule.nextFire(after: date(2026, 10, 4, 10, 30), calendar: calendar) == date(2026, 10, 11, 10, 30))
        // Same day, earlier than the slot → today.
        #expect(schedule.nextFire(after: date(2026, 10, 4, 8), calendar: calendar) == date(2026, 10, 4, 10, 30))
    }

    @Test func messageDependsOnWhatTheLastScanFound() {
        let now = date(2026, 9, 28, 12)
        #expect(WeeklyCleanMessage.body(potentialBytes: 2_300_000_000, lastScan: now.addingTimeInterval(-86_400), now: now)
            .contains("2.3 GB"))
        #expect(WeeklyCleanMessage.body(potentialBytes: 1_000_000, lastScan: now.addingTimeInterval(-86_400), now: now)
            .contains("tidy"))
        // Never scanned, or a scan too old to trust: no numbers.
        #expect(!WeeklyCleanMessage.body(potentialBytes: nil, lastScan: nil, now: now).contains("GB"))
        #expect(!WeeklyCleanMessage.body(potentialBytes: 5_000_000_000, lastScan: now.addingTimeInterval(-40 * 86_400), now: now).contains("GB"))
    }
}
