import CoreGraphics
import Foundation

/// Rebuilds printed rows from recognised text fragments. Receipts print labels and amounts in
/// columns ("TOTAL ........ 456.00"), and OCR often returns each column separately, so the
/// amount loses its label. Photos are also rarely straight, so rows are found along the
/// dominant text angle rather than the image axis. Pure, for tests.
enum TextLayout {
    /// One recognised line with its corners in pixels, y pointing down.
    struct Fragment: Sendable, Equatable {
        let text: String
        let topLeft: CGPoint
        let topRight: CGPoint
        let bottomRight: CGPoint
        let bottomLeft: CGPoint
    }

    static func rows(_ fragments: [Fragment]) -> [String] {
        guard !fragments.isEmpty else { return [] }
        // Dominant skew from the widest lines: OCR engines report short fragments ("456.00") as
        // axis-aligned boxes, so only long lines show the page's real tilt.
        func width(_ f: Fragment) -> CGFloat { hypot(f.topRight.x - f.topLeft.x, f.topRight.y - f.topLeft.y) }
        let widest = fragments.map(width).max() ?? 0
        let angles = fragments.filter { width($0) >= widest * 0.5 }
            .map { atan2($0.topRight.y - $0.topLeft.y, $0.topRight.x - $0.topLeft.x) }.sorted()
        let angle = angles[angles.count / 2]
        let (sine, cosine) = (sin(angle), cos(angle))

        struct Placed { let text: String; let x: CGFloat; let y: CGFloat; let height: CGFloat }
        let placed = fragments.map { f -> Placed in
            let cx = (f.topLeft.x + f.topRight.x + f.bottomRight.x + f.bottomLeft.x) / 4
            let cy = (f.topLeft.y + f.topRight.y + f.bottomRight.y + f.bottomLeft.y) / 4
            // Rotate into the text's own frame so a row has one y.
            return Placed(text: f.text, x: cosine * cx + sine * cy, y: -sine * cx + cosine * cy,
                          height: hypot(f.bottomLeft.x - f.topLeft.x, f.bottomLeft.y - f.topLeft.y))
        }.sorted { $0.y < $1.y }

        var rows: [[Placed]] = []
        for item in placed {
            if let last = rows.last {
                let rowY = last.map(\.y).reduce(0, +) / CGFloat(last.count)
                let rowHeight = last.map(\.height).max() ?? 0
                if abs(item.y - rowY) <= max(item.height, rowHeight) * 0.5 {
                    rows[rows.count - 1].append(item)
                    continue
                }
            }
            rows.append([item])
        }
        return rows.map { $0.sorted { $0.x < $1.x }.map(\.text).joined(separator: "  ") }
    }
}
