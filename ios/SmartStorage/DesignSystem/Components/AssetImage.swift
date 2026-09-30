import Photos
import SwiftUI

/// Shows a library asset's thumbnail, or the gradient placeholder for demo content / while loading.
struct AssetImage: View {
    let assetID: String?
    var fallback: ThumbnailStyle = .mountain
    var variant: Int = 0
    var cornerRadius: CGFloat = 12
    var symbolScale: CGFloat = 0.3
    /// Fill for compact thumbnails; fit for previews where the whole portrait must remain visible.
    var contentMode: ContentMode = .fill
    /// Which part of the image to keep when cropping (receipts and screenshots read from the top).
    var cropAlignment: Alignment = .center

    @State private var image: UIImage?
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                if contentMode == .fit { Color.black.opacity(0.92) }
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                        .frame(width: proxy.size.width, height: proxy.size.height, alignment: cropAlignment)
                        .clipped()
                        .transition(.opacity)
                } else {
                    MediaThumbnail(style: fallback, variant: variant, cornerRadius: 0, symbolScale: symbolScale)
                }
            }
            .task(id: TaskKey(id: assetID, size: proxy.size)) {
                await load(side: max(proxy.size.width, proxy.size.height))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .accessibilityHidden(true)
    }

    private struct TaskKey: Equatable {
        let id: String?
        let size: CGSize
    }

    private func load(side: CGFloat) async {
        guard let assetID, side > 0,
              let asset = PHAsset.fetchAssets(withLocalIdentifiers: [assetID], options: nil).firstObject
        else { return }
        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = false
        let target = CGSize(width: side * displayScale, height: side * displayScale)
        // Opportunistic delivery may call back twice (fast degraded, then final); keep the latest.
        for await next in Self.images(for: asset, target: target, contentMode: contentMode, options: options) {
            withAnimation(.easeOut(duration: 0.15)) { image = next }
        }
    }

    private static func images(for asset: PHAsset, target: CGSize, contentMode: ContentMode, options: PHImageRequestOptions) -> AsyncStream<UIImage> {
        AsyncStream { continuation in
            let requestMode: PHImageContentMode = contentMode == .fit ? .aspectFit : .aspectFill
            let id = PHImageManager.default().requestImage(for: asset, targetSize: target, contentMode: requestMode, options: options) { image, info in
                if let image { continuation.yield(image) }
                let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
                if !degraded { continuation.finish() }
            }
            continuation.onTermination = { _ in PHImageManager.default().cancelImageRequest(id) }
        }
    }
}
