import Foundation

/// Groups photos taken close together in time that also look alike.
///
/// Photos are walked in capture order; a photo joins the current group when it was taken within
/// `maxGap` of the previous photo *and* its feature print is within `maxDistance` of any member.
/// Comparing only neighbours in time keeps this O(n·k) instead of O(n²) over the whole library.
struct SimilarityGrouper: Sendable {
    var maxGap: TimeInterval = 120
    var maxDistance: Float = 0.55
    var minGroupSize = 2

    func groups(from photos: [AnalyzedPhoto]) -> [[AnalyzedPhoto]] {
        guard !Self.printsLookDegenerate(photos) else { return [] }
        let sorted = photos.sorted { $0.item.creationDate < $1.item.creationDate }
        var result: [[AnalyzedPhoto]] = []
        var current: [AnalyzedPhoto] = []

        for photo in sorted {
            if let last = current.last,
               photo.item.creationDate.timeIntervalSince(last.item.creationDate) <= maxGap,
               current.contains(where: { $0.features.distance(to: photo.features) <= maxDistance }) {
                current.append(photo)
            } else {
                if current.count >= minGroupSize { result.append(current) }
                current = [photo]
            }
        }
        if current.count >= minGroupSize { result.append(current) }
        return result
    }

    /// (Near-)identical prints across almost every photo mean the model isn't really running
    /// (e.g. the iOS Simulator's CPU fallback). Grouping on that would lump unrelated shots,
    /// so similarity is skipped rather than guessed.
    static func printsLookDegenerate(_ photos: [AnalyzedPhoto]) -> Bool {
        let withPrints = photos.filter { !$0.features.featurePrint.isEmpty }
        guard withPrints.count >= 4, let first = withPrints.first else { return false }
        let alike = withPrints.filter { $0.features.distance(to: first.features) < 0.01 }.count
        return Double(alike) >= Double(withPrints.count) * 0.9
    }
}
