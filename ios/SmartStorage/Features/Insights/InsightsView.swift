import Charts
import SwiftUI

/// 07 — Insights: storage forecast and this week's changes.
struct InsightsView: View {
    @Environment(AppRouter.self) private var router
    @Environment(LibraryStore.self) private var library

    private var forecast: StorageForecast { library.content.forecast }

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
                    Text(LocalizedStringKey(label))
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
                Text(fullText)
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

    private var fullText: String {
        guard let days = forecast.daysUntilFull else { return "Storage use is steady — no full date in sight.".localizedUI }
        return days > 365 ? "More than a year until full at the current pace.".localizedUI
            : localizedCount(days, one: "Estimated full in %d day", other: "Estimated full in %d days")
    }

    // Axis ranges follow the data: history minimum up to capacity, today-4w to the projection end.
    private var yDomain: ClosedRange<Double> {
        let lowest = forecast.points.map(\.usedGB).min() ?? 0
        let span = max(forecast.capacityGB - lowest, 8)
        return max(0, lowest - span * 0.15)...(forecast.capacityGB + span * 0.1)
    }

    private var xDomain: ClosedRange<Double> {
        let end = forecast.points.map(\.week).max() ?? 0
        return -4...max(end, 2)
    }

    private var yTicks: [Double] {
        let lo = yDomain.lowerBound, hi = forecast.capacityGB
        let step = max(((hi - lo) / 4).rounded(), 1)
        return stride(from: hi, through: lo, by: -step).map { $0 }
    }

    private var xTicks: [Double] {
        stride(from: -4.0, through: xDomain.upperBound, by: 2).map { $0 }
    }

    private var chart: some View {
        Chart {
            ForEach(forecast.points.filter { !$0.isProjection }) { point in
                AreaMark(x: .value("Week", point.week), yStart: .value("Base", yDomain.lowerBound), yEnd: .value("Used", point.usedGB))
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
                    Text(localizedFormat("Full · %d GB", Int(forecast.capacityGB)))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Tint.coral.foreground)
                }
        }
        .chartYScale(domain: yDomain)
        .chartXScale(domain: xDomain)
        .chartYAxis {
            AxisMarks(position: .leading, values: yTicks) { value in
                AxisGridLine().foregroundStyle(Palette.separator)
                AxisValueLabel { Text("\(Int((value.as(Double.self) ?? 0).rounded())) GB") }
            }
        }
        .chartXAxis {
            AxisMarks(values: xTicks) { value in
                AxisValueLabel { Text(weekLabel(value.as(Double.self) ?? 0)) }
            }
        }
        .accessibilityLabel("Storage forecast")
        .accessibilityValue(localizedFormat("%@ remaining. %@", forecast.remainingBytes.formattedBytes, fullText))
    }

    private func weekLabel(_ week: Double) -> String {
        switch week {
        case 0: "Today".localizedUI
        case ..<0: localizedFormat("%dw ago", Int(-week))
        default: localizedFormat("+%dw", Int(week))
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
            Text(LocalizedStringKey(text))
        }
    }
}

#Preview {
    NavigationStack { InsightsView() }
        .previewEnvironment()
}
