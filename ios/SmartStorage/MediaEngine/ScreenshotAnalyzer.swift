import CoreGraphics
import Vision

/// Reads a screenshot on device (text + QR codes) and classifies it. The recognised text is used
/// only for classification and is never stored or sent anywhere.
struct ScreenshotAnalyzer: Sendable {
    /// Long-edge size to read at: large enough for small UI text.
    static let readSide: CGFloat = 1600

    private static let preferredLanguages = ["th-TH", "en-US"]

    func analyze(_ image: CGImage) -> ScreenshotInfo {
        let handler = VNImageRequestHandler(cgImage: image, options: [:])

        let text = VNRecognizeTextRequest()
        text.recognitionLevel = .accurate // Thai is only supported by the accurate recogniser
        text.usesLanguageCorrection = false
        if let supported = try? text.supportedRecognitionLanguages() {
            let languages = Self.preferredLanguages.filter(supported.contains)
            if !languages.isEmpty { text.recognitionLanguages = languages }
        }
        let barcodes = VNDetectBarcodesRequest()
        barcodes.symbologies = [.qr, .aztec, .pdf417] // boarding passes use Aztec/PDF417

        for request in [text, barcodes] as [VNRequest] {
            ImageAnalyzer.preferCPUOnSimulator(request)
            try? handler.perform([request])
        }
        let lines = (text.results ?? []).compactMap { $0.topCandidates(1).first?.string }
        return ScreenshotClassifier.classify(text: lines.joined(separator: "\n"), hasQRCode: !(barcodes.results ?? []).isEmpty)
    }
}
