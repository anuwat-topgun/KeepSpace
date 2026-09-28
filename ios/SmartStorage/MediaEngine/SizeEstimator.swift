import Foundation

/// A rough file size from what PhotoKit exposes publicly (pixels, duration, file type). Only a stand-in:
/// shown for the first moments of a scan and for originals that live in iCloud only. Everything else gets
/// its exact size from `LibraryEngine` (photos: the bytes PhotoKit delivers; videos: the tracks' data length).
/// Pure, for tests.
enum SizeEstimator {
    /// Typical bytes per pixel by format, for a phone photo.
    private static func bytesPerPixel(fileName: String?) -> Double {
        switch (fileName as NSString?)?.pathExtension.lowercased() {
        case "heic", "heif": 0.17
        case "jpg", "jpeg": 0.33
        case "png": 0.6
        default: 0.3
        }
    }

    static func estimate(isVideo: Bool, pixelWidth: Int, pixelHeight: Int, duration: TimeInterval, fileName: String?) -> Int64 {
        let pixels = Double(max(pixelWidth, 0)) * Double(max(pixelHeight, 0))
        if isVideo {
            // ~0.07 bits per pixel per frame at 30 fps, about what HEVC phone video uses (1080p ≈ 4 Mbit/s).
            let bytesPerSecond = pixels * 30 * 0.07 / 8
            return max(1, Int64(bytesPerSecond * max(duration, 0)))
        }
        return max(1, Int64(pixels * bytesPerPixel(fileName: fileName)))
    }
}
