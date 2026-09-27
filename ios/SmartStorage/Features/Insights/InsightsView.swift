import Charts
import SwiftUI

/// 07 — Insights: storage forecast and this week's changes.
struct InsightsView: View {
    @Environment(AppRouter.self) private var router
    private let forecast = MockData.forecast

    var body: some View {
        ScreenScaffold(maxWidth: Metrics.wideContentWidth) {
            ScreenHeader(title: "Insights", subtitle: "Understand how your storage changes over time.")
                .padding(.bottom, 8)

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: Metrics.stackSpacing) {
                    ForecastCard(forecast: forecast).frame(minWidth: 520)
                    VStack(spacing: Metrics.stackSpacing) {
                        weeklyCard
                        smartCleanCard
                    }
                    .frame(width: 360)
                }
                VStack(spacing: Metrics.stackSpacing) {
                    ForecastCard(forecast: forecast)
                    weeklyCard
                    smartCleanCard
                }
            }
        }
    }

    private var weeklyCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 14) {
                SectionLabel("This week")
                weeklyRow(icon: "photo.on.rectangle.angled", tint: .coral, value: "\(forecast.photosAddedThisWeek)", label: "photos added") {
                    router.push(.similarPhotos)
                }
                Divider()
                weeklyRow(icon: "video.fill", tint: .purple, value: "\(forecast.videosAddedThisWeek)", label: "videos added") {
                    router.push(.videos)
                }
                Divider()
                Button {
                    router.selectedTab = .clean
                } label: {
                    ListTile(
                        systemImage: "sparkles",
                        tint: .teal,
                        title: forecast.potentialCleanupBytes.formattedBytes,
                        subtitle: "Potential cleanup"
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func weeklyRow(icon: String, tint: Tint, value: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                IconTile(systemName: icon, tint: tint)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(value)
                        .font(Typography.metric)
                        .foregroundStyle(Palette.textPrimary)
                    Text(label)
                        .font(Typography.body)
                        .foregroundStyle(Palette.textSecondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(.body, weight: .semibold))
                    .foregroundStyle(Palette.textSecondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var smartCleanCard: some View {
        Button {
            router.selectedTab = .clean
        } label: {
            HStack(spacing: 16) {
                Image(systemName: "sparkles")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(.white.opacity(0.2), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("Run Weekly Smart Clean")
                        .font(.system(.headline, weight: .bold))
                    Text("Find and remove unnecessary files.")
                        .font(Typography.metadata)
                        .opacity(0.9)
                }
                Spacer(minLength: 8)
                Image(systemName: "arrow.right")
                    .font(.system(.title3, weight: .semibold))
            }
            .foregroundStyle(.white)
            .padding(18)
            .background(Palette.accentGradient, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct ForecastCard: View {
    let forecast: StorageForecast

    var body: some View {
        Card(style: .hero) {
            VStack(alignment: .leading, spacing: 10) {
                SectionLabel("Storage forecast")
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(forecast.remainingBytes.formattedBytes)
                        .font(Typography.metricLarge)
                        .foregroundStyle(Palette.textPrimary)
                    Text("remaining")
                        .font(.system(.title2))
                        .foregroundStyle(Palette.textPrimary.opacity(0.8))
                }
                Text("Estimated full in \(forecast.daysUntilFull) days")
                    .font(Typography.body)
                    .foregroundStyle(Palette.textSecondary)

                chart
                    .frame(height: 220)
                    .padding(.top, 8)

                HStack(spacing: 16) {
                    legend(dashed: false, text: "Used")
                    legend(dashed: true, text: "Forecast")
                }
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
            }
        }
    }

    private var chart: some View {
        Chart {
            ForEach(forecast.points.filter { !$0.isProjection }) { point in
                AreaMark(x: .value("Week", point.week), yStart: .value("Base", 220), yEnd: .value("Used", point.usedGB))
                    .foregroundStyle(
                        LinearGradient(colors: [Palette.accent.opacity(0.25), Palette.accent.opacity(0.02)], startPoint: .top, endPoint: .bottom)
                    )
                    .interpolationMethod(.monotone)
            }
            ForEach(forecast.points) { point in
                LineMark(x: .value("Week", point.week), y: .value("Used", point.usedGB), series: .value("Series", point.isProjection ? "Forecast" : "Used"))
                    .foregroundStyle(Palette.accent)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: point.isProjection ? [6, 5] : []))
                    .interpolationMethod(.monotone)
            }
            ForEach(forecast.points.filter { !$0.isProjection }) { point in
                PointMark(x: .value("Week", point.week), y: .value("Used", point.usedGB))
                    .foregroundStyle(Palette.accent)
                    .symbolSize(point.week == 0 ? 90 : 30)
            }
            RuleMark(y: .value("Capacity", forecast.capacityGB))
                .foregroundStyle(Tint.coral.foreground.opacity(0.7))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                .annotation(position: .top, alignment: .leading) {
                    Text("Full · \(Int(forecast.capacityGB)) GB")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Tint.coral.foreground)
                }
        }
        .chartYScale(domain: 220...262)
        .chartXScale(domain: -4...7)
        .chartYAxis {
            AxisMarks(position: .leading, values: [224, 232, 240, 248, 256]) { value in
                AxisGridLine().foregroundStyle(Palette.separator)
                AxisValueLabel { Text("\(value.as(Int.self) ?? 0) GB") }
            }
        }
        .chartXAxis {
            AxisMarks(values: [-4, -2, 0, 2, 4, 6]) { value in
                AxisValueLabel { Text(weekLabel(value.as(Double.self) ?? 0)) }
            }
        }
        .accessibilityLabel("Storage forecast")
        .accessibilityValue("\(forecast.remainingBytes.formattedBytes) remaining, full in about \(forecast.daysUntilFull) days")
    }

    private func weekLabel(_ week: Double) -> String {
        switch week {
        case 0: "Today"
        case ..<0: "\(Int(-week))w ago"
        default: "+\(Int(week))w"
        }
    }

    private func legend(dashed: Bool, text: String) -> some View {
        HStack(spacing: 6) {
            Path { path in
                path.move(to: CGPoint(x: 0, y: 1))
                path.addLine(to: CGPoint(x: 18, y: 1))
            }
            .stroke(Palette.accent, style: StrokeStyle(lineWidth: 2.5, dash: dashed ? [4, 3] : []))
            .frame(width: 18, height: 2)
            Text(text)
        }
    }
}

#Preview {
    NavigationStack { InsightsView() }
        .environment(AppRouter())
}
