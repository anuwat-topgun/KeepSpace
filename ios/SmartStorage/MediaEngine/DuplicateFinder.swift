import Foundation

/// Finds byte-for-byte identical files. The platform layer supplies hashes; Android and iOS videos
/// hash only equal-size candidates, while iOS stills get a hash during their public-API size pass.
/// Pure, for tests.
enum DuplicateFinder {
    struct Group: Sendable, Equatable {
        /// The copy that stays: a favourite, else the oldest.
        let keeper: MediaItem
        /// The other copies, safe to remove.
        let copies: [MediaItem]

        var reclaimableBytes: Int64 { copies.reduce(0) { $0 + $1.bytes } }
    }

    /// Files worth hashing: same size and same kind (still vs. video) as at least one other file.
    static func candidates(_ items: [MediaItem]) -> [MediaItem] {
        Dictionary(grouping: items.filter { $0.bytes > 0 }, by: { SizeKey(bytes: $0.bytes, isVideo: $0.isVideo) })
            .values.filter { $0.count > 1 }.flatMap { $0 }
    }

    /// Groups of identical files from their content hashes (items without a hash are ignored).
    static func groups(_ items: [MediaItem], hashes: [String: String]) -> [Group] {
        let hashed = items.compactMap { item in hashes[item.id].map { (key: "\(item.bytes):\($0)", item: item) } }
        return Dictionary(grouping: hashed, by: \.key).values.compactMap { members in
            guard members.count > 1 else { return nil }
            let sorted = members.map(\.item).sorted { a, b in
                if a.isFavorite != b.isFavorite { return a.isFavorite }
                if a.creationDate != b.creationDate { return a.creationDate < b.creationDate }
                return a.id < b.id
            }
            return Group(keeper: sorted[0], copies: Array(sorted.dropFirst()))
        }
        .sorted { $0.reclaimableBytes != $1.reclaimableBytes ? $0.reclaimableBytes > $1.reclaimableBytes : $0.keeper.id < $1.keeper.id }
    }

    private struct SizeKey: Hashable {
        let bytes: Int64
        let isVideo: Bool
    }
}
