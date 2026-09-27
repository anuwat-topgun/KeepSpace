import CoreGraphics
import Vision

/// Reads a screenshot (or a photo of a paper receipt) on device (text + QR codes) and classifies it. The recognised text is used
/// only for classification and is never stored or sent anywhere.
struct ScreenshotAnalyzer: Sendable {
    /// Long-edge size to read at: large enough for small UI text.
    static let readSide: CGFloat = 1600

    private static let preferredLanguages = ["th-TH", "en-US"]

    /// For camera photos (`isPhoto`) only receipts matter, so barcodes are skipped, and printed
    /// rows are rebuilt from the text's position (labels and amounts come back as separate columns).
    func analyze(_ image: CGImage, orientation: CGImagePropertyOrientation = .up, isPhoto: Bool = false) -> ScreenshotInfo {
        let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])

        let text = VNRecognizeTextRequest()
        text.recognitionLevel = .accurate // Thai is only supported by the accurate recogniser
        text.usesLanguageCorrection = false
        if let supported = try? text.supportedRecognitionLanguages() {
            let languages = Self.preferredLanguages.filter(supported.contains)
            if !languages.isEmpty { text.recognitionLanguages = languages }
        }
        let barcodes = VNDetectBarcodesRequest()
        barcodes.symbologies = [.qr, .aztec, .pdf417] // boarding passes use Aztec/PDF417

        for request in (isPhoto ? [text] : [text, barcodes]) as [VNRequest] {
            ImageAnalyzer.preferCPUOnSimulator(request)
            try? handler.perform([request])
        }
        let observations = text.results ?? []
        let lines: [String]
        if isPhoto {
            // Normalised coordinates are for the upright image; scale to its pixels so angles are true.
            let sideways = [.left, .leftMirrored, .right, .rightMirrored].contains(orientation)
            let width = CGFloat(sideways ? image.height : image.width)
            let height = CGFloat(sideways ? image.width : image.height)
            func pixel(_ p: CGPoint) -> CGPoint { CGPoint(x: p.x * width, y: (1 - p.y) * height) }
            lines = TextLayout.rows(observations.compactMap { observation in
                observation.topCandidates(1).first.map {
                    TextLayout.Fragment(text: $0.string, topLeft: pixel(observation.topLeft), topRight: pixel(observation.topRight),
                                        bottomRight: pixel(observation.bottomRight), bottomLeft: pixel(observation.bottomLeft))
                }
            })
        } else {
            lines = observations.compactMap { $0.topCandidates(1).first?.string }
        }
        return ScreenshotClassifier.classify(text: lines.joined(separator: "\n"), hasQRCode: !(barcodes.results ?? []).isEmpty)
    }
}
