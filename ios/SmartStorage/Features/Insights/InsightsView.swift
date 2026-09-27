import SwiftUI

/// 07 — Insights. Forecast chart and weekly summary land in step 4.
struct InsightsView: View {
    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Insights", subtitle: "Understand how your storage changes over time.")
                .padding(.bottom, 8)

            Card(style: .hero) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel("Storage forecast")
                    Text("Coming soon")
                        .font(Typography.metric)
                        .foregroundStyle(Palette.textPrimary)
                }
            }
        }
    }
}
