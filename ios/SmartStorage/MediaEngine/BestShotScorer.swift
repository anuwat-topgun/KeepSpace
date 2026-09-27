import Foundation

/// Picks the keeper in a group of similar photos and explains why.
///
/// Sharpness and face quality are scored relative to the best in the group (value / max), so a
/// 2% sharpness edge stays a 2% edge instead of being stretched to the full range. Only reasons
/// that are actually true for the pick are reported, so the explanation never overclaims.
struct BestShotScorer: Sendable {
    struct Pick: Sendable {
        let index: Int
        let reasons: [Reason]
    }

    enum Reason: Sendable, Equatable {
        case sharpest, bestFaces, bestExposure, favorite
    }

    var sharpnessWeight = 0.45
    var faceWeight = 0.35
    var exposureWeight = 0.20

    func pick(_ photos: [AnalyzedPhoto]) -> Pick? {
        guard !photos.isEmpty else { return nil }
        // A photo the user already favourited always wins; respect explicit intent.
        if let favorite = photos.firstIndex(where: { $0.item.isFavorite }) {
            return Pick(index: favorite, reasons: [.favorite])
        }

        let sharpness = relativeToBest(photos.map(\.features.sharpness))
        let faces = photos.map { $0.features.faceQuality }
        let hasFaces = faces.contains { $0 != nil }
        let faceScores = relativeToBest(faces.map { $0 ?? 0 })
        // Exposure: 1 at mid-grey, 0 at pure black/white.
        let exposure = photos.map { 1 - abs($0.features.exposure - 0.5) * 2 }
        let bestExposure = exposure.max() ?? 0

        let scores = photos.indices.map { i in
            sharpnessWeight * sharpness[i]
                + (hasFaces ? faceWeight * faceScores[i] : 0)
                + exposureWeight * exposure[i]
        }
        guard let best = scores.indices.max(by: { scores[$0] < scores[$1] }) else { return nil }

        var reasons: [Reason] = []
        if sharpness[best] >= 0.999 { reasons.append(.sharpest) }
        if hasFaces, faceScores[best] >= 0.999 { reasons.append(.bestFaces) }
        if exposure[best] >= bestExposure - 0.001 { reasons.append(.bestExposure) }
        // Always give at least one honest reason: the strongest signal it won on.
        if reasons.isEmpty { reasons.append(sharpness[best] >= exposure[best] ? .sharpest : .bestExposure) }
        return Pick(index: best, reasons: reasons)
    }

    /// Scale to 0...1 relative to the group's best; all-zero inputs map to 1 so nobody is penalised.
    private func relativeToBest(_ values: [Double]) -> [Double] {
        guard let hi = values.max(), hi > 0 else { return values.map { _ in 1 } }
        return values.map { max($0, 0) / hi }
    }
}
