import SwiftUI

/// Notifications: the opt-in Weekly Smart Clean reminder.
struct NotificationsView: View {
    @Environment(WeeklyCleanReminder.self) private var weekly
    @Environment(\.openURL) private var openURL
    @State private var sent = false

    private static let days = [(1, "Monday"), (2, "Tuesday"), (3, "Wednesday"), (4, "Thursday"), (5, "Friday"), (6, "Saturday"), (7, "Sunday")]

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: "Notifications", subtitle: "A weekly nudge to keep your storage healthy.")
                .padding(.bottom, 8)

            Card(padding: 16) {
                Toggle(isOn: Binding(get: { weekly.isEnabled }, set: { on in Task { await weekly.setEnabled(on) } })) {
                    ListTile(systemImage: "bell.badge.fill", tint: .purple, title: "Weekly Smart Clean",
                             subtitle: "A reminder to review the safest cleanup suggestions.", showsChevron: false)
                }
                .tint(Palette.accent)
            }

            if weekly.permission == .denied {
                Card(style: .info) {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Notifications are turned off for KeepSpace in iOS Settings.", systemImage: "exclamationmark.triangle.fill")
                            .font(Typography.metadata)
                            .foregroundStyle(Tint.amber.foreground)
                        Button("Open iOS Settings") {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                        }
                        .buttonStyle(.secondaryOutlined)
                    }
                }
            }

            if weekly.isEnabled {
                Card(padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        Picker("Day", selection: Binding(get: { weekly.schedule.weekday }, set: { day in
                            var s = weekly.schedule; s.weekday = day; Task { await weekly.setSchedule(s) }
                        })) {
                            ForEach(Self.days, id: \.0) { Text($0.1).tag($0.0) }
                        }
                        DatePicker("Time", selection: Binding(get: { time }, set: { new in
                            let parts = Calendar.current.dateComponents([.hour, .minute], from: new)
                            var s = weekly.schedule; s.hour = parts.hour ?? 10; s.minute = parts.minute ?? 0
                            Task { await weekly.setSchedule(s) }
                        }), displayedComponents: .hourAndMinute)
                    }
                }

                Button("Send a Test Notification") {
                    Task { await weekly.sendTest(); sent.toggle() }
                }
                .buttonStyle(.secondaryOutlined)
                .sensoryFeedback(.success, trigger: sent)
            }

            Card(style: .info) {
                Label("The reminder quotes what KeepSpace found the last time it scanned. It doesn't scan your photos in the background, and nothing is sent anywhere.",
                      systemImage: "lock.shield")
                    .font(Typography.metadata)
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .task { await weekly.refreshPermission() }
    }

    private var time: Date {
        Calendar.current.date(from: DateComponents(hour: weekly.schedule.hour, minute: weekly.schedule.minute)) ?? .now
    }
}

#Preview {
    NavigationStack { NotificationsView() }.previewEnvironment()
}
