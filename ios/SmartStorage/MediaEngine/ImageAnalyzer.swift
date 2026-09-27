import CoreGraphics
import CoreML
import Foundation
import Vision

/// Runs the on-device analysis for one image. Everything here stays on the device:
/// Vision models ship with the OS and nothing is uploaded.
struct ImageAnalyzer: Sendable {
    /// Side length for the grayscale sharpness/exposure pass. Small enough to be fast,
    /// large enough that real blur still shows up in the Laplacian.
    static let measureSide = 256

    func analyze(_ image: CGImage, orientation: CGImagePropertyOrientation = .up) -> ImageFeatures {
        let (sharpness, exposure) = Self.sharpnessAndExposure(of: image)
        let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])

        let printRequest = VNGenerateImageFeaturePrintRequest()
        let faceRequest = VNDetectFaceCaptureQualityRequest()
        let classifyRequest = VNClassifyImageRequest()
        // Perform separately so one failing request can't discard the others' results.
        for request in [printRequest, faceRequest, classifyRequest] as [VNRequest] {
            Self.preferCPUOnSimulator(request)
            try? handler.perform([request])
        }

        let faces = faceRequest.results ?? []
        let labels = classifyRequest.results ?? []
        // Counts lines of text to spot paper receipts; the text itself is discarded. Only photos
        // without people can be receipts, so the rest skip it. (Text rectangles find almost
        // nothing at this size; the fast recogniser does.)
        var textLines = 0
        if faces.isEmpty {
            let textRequest = VNRecognizeTextRequest()
            textRequest.recognitionLevel = .fast
            textRequest.usesLanguageCorrection = false
            Self.preferCPUOnSimulator(textRequest)
            try? handler.perform([textRequest])
            textLines = textRequest.results?.count ?? 0
        }
        return ImageFeatures(
            featurePrint: printRequest.results?.first.map(Self.floats) ?? [],
            sharpness: sharpness,
            exposure: exposure,
            faceQuality: faces.compactMap { $0.faceCaptureQuality.map(Double.init) }.max(),
            faceCount: faces.count,
            sceneLabel: Self.sceneLabel(from: labels),
            textLines: textLines,
            documentScore: Double(labels.filter { Self.documentLabels.contains($0.identifier) }.map(\.confidence).max() ?? 0)
        )
    }

    /// Most confident specific label. Very generic taxonomy nodes are skipped so group titles
    /// read "Beach" rather than "Outdoor".
    /// Classifier labels that mean "a piece of paper worth reading".
    private static let documentLabels: Set<String> = ["receipt", "document", "printed_page"]

    private static let genericLabels: Set<String> = ["outdoor", "indoor", "structure", "people", "adult", "consumable", "material"]

    private static func sceneLabel(from observations: [VNClassificationObservation]) -> String? {
        observations
            .filter { $0.confidence >= 0.3 && !genericLabels.contains($0.identifier) }
            .max(by: { $0.confidence < $1.confidence })?
            .identifier
    }

    /// The iOS Simulator can't create GPU/Neural Engine contexts for Vision models
    /// ("Failed to create espresso context"); run on CPU there. Devices keep the fast path.
    static func preferCPUOnSimulator(_ request: VNRequest) {
        #if targetEnvironment(simulator)
        if let devices = try? request.supportedComputeStageDevices {
            for (stage, options) in devices {
                if let cpu = options.first(where: { if case .cpu = $0 { true } else { false } }) {
                    request.setComputeDevice(cpu, for: stage)
                }
            }
        }
        #endif
    }

    private static func floats(from observation: VNFeaturePrintObservation) -> [Float] {
        guard observation.elementType == .float else { return [] }
        return observation.data.withUnsafeBytes { Array($0.bindMemory(to: Float.self).prefix(observation.elementCount)) }
    }

    /// Variance of the 4-neighbour Laplacian (classic blur metric) plus mean luminance,
    /// computed on a downscaled 8-bit grayscale copy.
    static func sharpnessAndExposure(of image: CGImage) -> (sharpness: Double, exposure: Double) {
        let side = measureSide
        var pixels = [UInt8](repeating: 0, count: side * side)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: side,
                height: side,
                bitsPerComponent: 8,
                bytesPerRow: side,
                space: CGColorSpaceCreateDeviceGray(),
                bitmapInfo: CGImageAlphaInfo.none.rawValue
            ) else { return false }
            context.interpolationQuality = .medium
            context.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return (0, 0.5) }
        return (laplacianVariance(pixels, side: side), Double(pixels.reduce(0) { $0 + Int($1) }) / Double(pixels.count) / 255)
    }

    static func laplacianVariance(_ pixels: [UInt8], side: Int) -> Double {
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0.0
        for y in 1..<(side - 1) {
            for x in 1..<(side - 1) {
                let i = y * side + x
                let value = Double(pixels[i - 1]) + Double(pixels[i + 1]) + Double(pixels[i - side]) + Double(pixels[i + side])
                    - 4 * Double(pixels[i])
                sum += value
                sumSquares += value * value
                count += 1
            }
        }
        let mean = sum / count
        return sumSquares / count - mean * mean
    }
}
