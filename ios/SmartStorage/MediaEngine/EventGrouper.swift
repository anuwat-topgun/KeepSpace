import Foundation

/// One photo or video as the event grouper sees it.
struct EventCandidate: Sendable, Equatable {
    let id: String
    let date: Date
    var isVideo = false
    var latitude: Double? = nil
    var longitude: Double? = nil
    /// Top scene label from the on-device classifier (iOS only).
    var sceneLabel: String? = nil
}

/// A trip or event found in the library. Titles come from dates, scene labels and distance —
/// all computed on the device; places are never looked up online.
struct DetectedEvent: Sendable, Equatable, Identifiable {
    enum Kind: Sendable { case trip, event }

    let id: String
    let kind: Kind
    let title: String
    let start: Date
    let end: Date
    /// Capture order.
    let itemIDs: [String]
    let photoCount: Int
    let videoCount: Int
    /// Rounded distance from home for trips, when locations allow it.
    var distanceKm: Int? = nil
}

/// Groups photos into events (a burst of photos on one occasion) and trips (days spent away from
/// home). Pure and deterministic, for tests. Mirrors EventGrouper.kt.
struct EventGrouper: Sendable {
    /// A pause longer than this starts a new session.
    var sessionGap: TimeInterval = 6 * 3600
    /// Away sessions this close together belong to the same trip.
    var tripGap: TimeInterval = 48 * 3600
    /// "Away" means at least this far from home.
    var awayKm = 80.0
    var minTripItems = 20
    /// A themed session (birthday, concert…) needs fewer shots to count than an ordinary busy day.
    var minThemedEventItems = 12
    var minEventItems = 30
    /// Located items needed before a home can be inferred.
    var minLocatedForHome = 10

    func events(from items: [EventCandidate], calendar: Calendar = .current) -> [DetectedEvent] {
        let sorted = items.sorted { $0.date < $1.date || ($0.date == $1.date && $0.id < $1.id) }
        guard !sorted.isEmpty else { return [] }
        let home = Self.home(of: sorted, minimum: minLocatedForHome)

        // 1. Sessions: runs of shots without a long pause.
        var sessions: [[EventCandidate]] = []
        for item in sorted {
            if let last = sessions.last?.last, item.date.timeIntervalSince(last.date) <= sessionGap {
                sessions[sessions.count - 1].append(item)
            } else {
                sessions.append([item])
            }
        }

        // 2. Trips: consecutive sessions spent away from home.
        var result: [DetectedEvent] = []
        var index = 0
        while index < sessions.count {
            guard let home, isAway(sessions[index], home: home) else {
                if let event = event(from: sessions[index], calendar: calendar) { result.append(event) }
                index += 1
                continue
            }
            var trip = sessions[index]
            index += 1
            while index < sessions.count, let first = sessions[index].first, let last = trip.last,
                  first.date.timeIntervalSince(last.date) <= tripGap, isAway(sessions[index], home: home) {
                trip += sessions[index]
                index += 1
            }
            if trip.count >= minTripItems {
                result.append(self.trip(from: trip, home: home, calendar: calendar))
            } else if let event = event(from: trip, calendar: calendar) {
                result.append(event)
            }
        }
        return result.sorted { $0.start > $1.start }
    }

    // MARK: - Pieces

    struct Coordinate: Equatable, Sendable {
        let latitude: Double
        let longitude: Double
    }

    /// Where life happens: the ~25 km grid cell with shots on the most different days (a photo-heavy
    /// trip has more shots than home, but home wins on days), averaged.
    static func home(of items: [EventCandidate], minimum: Int) -> Coordinate? {
        let located = items.compactMap { item -> (Coordinate, Int)? in
            guard let lat = item.latitude, let lon = item.longitude else { return nil }
            return (Coordinate(latitude: lat, longitude: lon), Int((item.date.timeIntervalSince1970 / 86_400).rounded(.down)))
        }
        guard located.count >= minimum else { return nil }
        func cell(_ c: Coordinate) -> String { "\(Int((c.latitude * 4).rounded(.down))):\(Int((c.longitude * 4).rounded(.down)))" }
        let cells = Dictionary(grouping: located, by: { cell($0.0) })
        func days(_ members: [(Coordinate, Int)]) -> Int { Set(members.map(\.1)).count }
        guard let busiest = cells.max(by: { a, b in
            (days(a.value), a.value.count, b.key) < (days(b.value), b.value.count, a.key)
        })?.value else { return nil }
        return Coordinate(latitude: busiest.map(\.0.latitude).reduce(0, +) / Double(busiest.count),
                          longitude: busiest.map(\.0.longitude).reduce(0, +) / Double(busiest.count))
    }

    /// Great-circle distance in kilometres.
    static func distanceKm(_ a: Coordinate, _ b: Coordinate) -> Double {
        let r = 6371.0
        let dLat = (b.latitude - a.latitude) * .pi / 180
        let dLon = (b.longitude - a.longitude) * .pi / 180
        let h = sin(dLat / 2) * sin(dLat / 2)
            + cos(a.latitude * .pi / 180) * cos(b.latitude * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1, h.squareRoot()))
    }

    private func distances(_ items: [EventCandidate], home: Coordinate) -> [Double] {
        items.compactMap { item in
            guard let lat = item.latitude, let lon = item.longitude else { return nil }
            return Self.distanceKm(home, Coordinate(latitude: lat, longitude: lon))
        }
    }

    /// Most located shots in the session are far from home.
    private func isAway(_ session: [EventCandidate], home: Coordinate) -> Bool {
        let far = distances(session, home: home)
        guard !far.isEmpty else { return false }
        return Double(far.filter { $0 >= awayKm }.count) >= Double(far.count) / 2
    }

    private func trip(from items: [EventCandidate], home: Coordinate, calendar: Calendar) -> DetectedEvent {
        let start = items.first!.date, end = items.last!.date
        let days = (calendar.dateComponents([.day], from: calendar.startOfDay(for: start), to: calendar.startOfDay(for: end)).day ?? 0) + 1
        let distance = distances(items, home: home).sorted()
        let median = distance.isEmpty ? nil : distance[distance.count / 2]
        let title: String
        if let theme = EventTheme.dominant(in: items) {
            title = theme.tripTitle
        } else if days == 1 {
            title = "Day Trip"
        } else if days <= 3, Self.touchesWeekend(start: start, days: days, calendar: calendar) {
            title = "Weekend Trip"
        } else {
            title = "\(days)-Day Trip"
        }
        return makeEvent(.trip, title: title, items: items, distanceKm: median.map { Int(($0 / 10).rounded() * 10) })
    }

    private func event(from items: [EventCandidate], calendar: Calendar) -> DetectedEvent? {
        let theme = EventTheme.dominant(in: items)
        guard items.count >= (theme == nil ? minEventItems : minThemedEventItems) else { return nil }
        let title = theme?.eventTitle(at: items.first!.date, calendar: calendar)
            ?? items.first!.date.formatted(.dateTime.weekday(.wide).day().month(.abbreviated))
        return makeEvent(.event, title: title, items: items, distanceKm: nil)
    }

    private func makeEvent(_ kind: DetectedEvent.Kind, title: String, items: [EventCandidate], distanceKm: Int?) -> DetectedEvent {
        let videos = items.filter(\.isVideo).count
        return DetectedEvent(id: items.first!.id, kind: kind, title: title, start: items.first!.date, end: items.last!.date,
                             itemIDs: items.map(\.id), photoCount: items.count - videos, videoCount: videos, distanceKm: distanceKm)
    }

    private static func touchesWeekend(start: Date, days: Int, calendar: Calendar) -> Bool {
        (0..<days).contains { offset in
            calendar.date(byAdding: .day, value: offset, to: start).map(calendar.isDateInWeekend) ?? false
        }
    }
}

/// What an occasion is about, from the scene labels of its photos.
enum EventTheme: CaseIterable, Sendable {
    case wedding, birthday, concert, sports, christmas, fireworks, graduation, beach, snow, hiking, camping, themePark, zoo, museum

    var labels: Set<String> {
        switch self {
        case .wedding: ["wedding", "bride", "bridesmaid", "wedding_dress", "wedding_cake"]
        case .birthday: ["birthday_cake", "cake", "cupcake", "cake_regular"]
        case .concert: ["concert"]
        case .sports: ["stadium", "sport", "motorsport"]
        case .christmas: ["christmas_tree", "christmas_decoration"]
        case .fireworks: ["fireworks"]
        case .graduation: ["graduation"]
        case .beach: ["beach"]
        case .snow: ["snow", "skiing", "snowboarding", "ski_equipment", "winter_sport", "snowman"]
        case .hiking: ["hiking", "mountain"]
        case .camping: ["camping"]
        case .themePark: ["amusement_park"]
        case .zoo: ["zoo", "aquarium"]
        case .museum: ["museum"]
        }
    }

    func eventTitle(at date: Date, calendar: Calendar) -> String {
        switch self {
        case .wedding: "Wedding"
        case .birthday: "Birthday Party"
        case .concert: "Concert Night"
        case .sports: "Game Day"
        case .christmas: "Christmas"
        case .fireworks:
            // Fireworks around the turn of the year are New Year's Eve.
            [12, 1].contains(calendar.component(.month, from: date)) ? "New Year's Eve" : "Fireworks Night"
        case .graduation: "Graduation"
        case .beach: "Beach Day"
        case .snow: "Snow Day"
        case .hiking: "Hiking Day"
        case .camping: "Camping"
        case .themePark: "Theme Park"
        case .zoo: "Zoo Day"
        case .museum: "Museum Visit"
        }
    }

    var tripTitle: String {
        switch self {
        case .beach: "Beach Trip"
        case .snow: "Snow Trip"
        case .hiking: "Mountain Trip"
        case .camping: "Camping Trip"
        case .wedding: "Wedding Trip"
        default: "Trip"
        }
    }

    /// The theme covering the most shots, if it is clearly present (≥ 2 shots and ≥ 10 %).
    static func dominant(in items: [EventCandidate]) -> EventTheme? {
        let labels = items.compactMap(\.sceneLabel)
        let counts = allCases.map { theme in (theme, labels.filter(theme.labels.contains).count) }
        guard let best = counts.max(by: { $0.1 < $1.1 }), best.1 >= 2, Double(best.1) >= Double(items.count) * 0.1 else { return nil }
        return best.0
    }
}
