import Foundation

/// What Best Shot has learned about a person's taste, from the photos they chose to keep.
///
/// Every time someone keeps one photo out of a group, the chosen photo "beats" each of the others.
/// A tiny pairwise logistic model nudges the three weights toward whatever made the chosen photo
/// stand out (Bradley–Terry with a pull back to the standard mix). Only the weights and a count are
/// kept — never photos, ids, or what was in them — and it is trusted gradually, so a handful of
/// choices can't wreck the recommendations. Pure, for tests. Mirrors TasteModel.kt.
struct TasteProfile: Equatable, Sendable, Codable {
    var learned = ScoreWeights.standard
    var decisions = 0

    /// Choices after which the learned mix is used in full.
    static let fullTrustAfter = 20
    /// How sharply a score gap turns into a preference; tuned so a 0.5 gap in one feature is decisive.
    private static let steepness = 6.0
    private static let learningRate = 0.15
    /// Pull toward the standard mix on every step, so weights can't run away.
    private static let pullToStandard = 0.02
    /// No feature is ever ignored completely.
    private static let floor = 0.03

    /// 0 (no choices yet) … 1 (fully trusted).
    var confidence: Double { min(1, Double(decisions) / Double(Self.fullTrustAfter)) }

    /// The mix Best Shot uses: the standard one, moving toward the learned one as trust grows.
    var effective: ScoreWeights {
        let c = confidence
        let s = ScoreWeights.standard
        return ScoreWeights(sharpness: s.sharpness + (learned.sharpness - s.sharpness) * c,
                            face: s.face + (learned.face - s.face) * c,
                            exposure: s.exposure + (learned.exposure - s.exposure) * c)
    }

    /// Learns from one decision: the photo at `chosen` was kept out of the group described by `features`.
    mutating func learn(chosen: Int, among features: [ScoreFeatures]) {
        guard features.count > 1, features.indices.contains(chosen) else { return }
        var w = [learned.sharpness, learned.face, learned.exposure]
        let winner = features[chosen]
        let step = Self.learningRate / Double(features.count - 1) // groups of any size weigh the same
        for (index, other) in features.enumerated() where index != chosen {
            let d = [winner.sharpness - other.sharpness, winner.face - other.face, winner.exposure - other.exposure]
            let z = Self.steepness * zip(w, d).reduce(0) { $0 + $1.0 * $1.1 }
            let miss = 1 - 1 / (1 + exp(-z)) // how surprised the model was
            for k in w.indices { w[k] += step * miss * d[k] }
        }
        let standard = [ScoreWeights.standard.sharpness, ScoreWeights.standard.face, ScoreWeights.standard.exposure]
        for k in w.indices { w[k] = max(Self.floor, w[k] + Self.pullToStandard * (standard[k] - w[k])) }
        let total = w.reduce(0, +)
        learned = ScoreWeights(sharpness: w[0] / total, face: w[1] / total, exposure: w[2] / total)
        decisions += 1
    }
}
