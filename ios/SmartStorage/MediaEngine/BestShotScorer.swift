import Foundation

/// How much Best Shot cares about each thing. Sums to 1. The standard mix is tuned by hand; a
/// person's own mix is learned on the device from the photos they choose to keep (`TasteProfile`).
struct ScoreWeights: Equatable, Sendable, Codable {
    var sharpness: Double
    var face: Double
    var exposure: Double

    static let standard = ScoreWeights(sharpness: 0.45, face: 0.35, exposure: 0.20)
}

/// One photo's standing inside its group, each 0...1: sharpness and faces relative to the group's best,
/// exposure absolute (1 at mid-grey). This is what a choice is learned from — no pixels, no ids.
struct ScoreFeatures: Hashable, Sendable, Codable {
    var sharpness: Double
    var face: Double
    var exposure: Double
}

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

    var sharpnessWeight = ScoreWeights.standard.sharpness
    var faceWeight = ScoreWeights.standard.face
    var exposureWeight = ScoreWeights.standard.exposure

    init() {}

    init(weights: ScoreWeights) {
        sharpnessWeight = weights.sharpness
        faceWeight = weights.face
        exposureWeight = weights.exposure
    }

    /// Each photo's relative standing; faces count only when someone in the group has one.
    func features(_ photos: [AnalyzedPhoto]) -> [ScoreFeatures] {
        let sharpness = relativeToBest(photos.map(\.features.sharpness))
        let faces = photos.map { $0.features.faceQuality }
        let faceScores = faces.contains { $0 != nil } ? relativeToBest(faces.map { $0 ?? 0 }) : photos.map { _ in 0.0 }
        return photos.indices.map { i in
            // Exposure: 1 at mid-grey, 0 at pure black/white.
            ScoreFeatures(sharpness: sharpness[i], face: faceScores[i], exposure: 1 - abs(photos[i].features.exposure - 0.5) * 2)
        }
    }

    func pick(_ photos: [AnalyzedPhoto]) -> Pick? {
        guard !photos.isEmpty else { return nil }
        // A photo the user already favourited always wins; respect explicit intent.
        if let favorite = photos.firstIndex(where: { $0.item.isFavorite }) {
            return Pick(index: favorite, reasons: [.favorite])
        }

        let scored = features(photos)
        let scores = scored.map { sharpnessWeight * $0.sharpness + faceWeight * $0.face + exposureWeight * $0.exposure }
        guard let best = scores.indices.max(by: { scores[$0] < scores[$1] }) else { return nil }

        let bestExposure = scored.map(\.exposure).max() ?? 0
        let hasFaces = photos.contains { $0.features.faceQuality != nil }
        var reasons: [Reason] = []
        if scored[best].sharpness >= 0.999 { reasons.append(.sharpest) }
        if hasFaces, scored[best].face >= 0.999 { reasons.append(.bestFaces) }
        if scored[best].exposure >= bestExposure - 0.001 { reasons.append(.bestExposure) }
        // Always give at least one honest reason: the strongest signal it won on.
        if reasons.isEmpty { reasons.append(scored[best].sharpness >= scored[best].exposure ? .sharpest : .bestExposure) }
        return Pick(index: best, reasons: reasons)
    }

    /// Scale to 0...1 relative to the group's best; all-zero inputs map to 1 so nobody is penalised.
    private func relativeToBest(_ values: [Double]) -> [Double] {
        guard let hi = values.max(), hi > 0 else { return values.map { _ in 1 } }
        return values.map { max($0, 0) / hi }
    }
}
