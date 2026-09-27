import Foundation

/// Picks the camera photos worth reading as paper receipts. Reading text is slow, so only photos
/// that look like a document get the full OCR pass; everything else is skipped. Pure, for tests.
enum PaperReceiptDetector {
    /// Classifier confidence that alone makes a photo worth reading.
    static let documentScore = 0.15
    /// Text lines that make a photo worth reading even when the classifier is unsure. Thresholds are
    /// generous: a false candidate only costs one OCR pass and is then ignored unless it reads as a receipt.
    /// (The quick line count reads Latin script only, so Thai receipts lean on the classifier.)
    static let textLines = 5
    /// Long-edge size to read photos at: receipt print is small in a camera shot.
    static let readSide: CGFloat = 2048

    static func isCandidate(_ photo: AnalyzedPhoto) -> Bool {
        let features = photo.features
        // People photos with a sign in the background aren't receipts.
        guard photo.item.kind == .photo, features.faceCount == 0 else { return false }
        return features.documentScore >= documentScore || features.textLines >= textLines
    }
}
