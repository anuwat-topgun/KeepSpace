import SwiftUI

/// 15 — New Rule / Edit Rule: a guided form with a live preview of where files will land.
struct RuleEditorView: View {
    /// nil = new rule.
    let ruleID: UUID?

    @Environment(RuleStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var draft: StorageRule
    @State private var acknowledgedDelayedDeletion = false
    @State private var confirmingDelete = false
    @State private var saved = false

    init(ruleID: UUID?, existing: StorageRule?) {
        self.ruleID = ruleID
        let start = existing ?? StorageRule(name: RuleTrigger.receipt.title, trigger: .receipt, provider: .googleDrive,
                                            folderTemplate: RuleTrigger.receipt.suggestedFolder,
                                            fileNameTemplate: RuleTrigger.receipt.suggestedFileName, afterUpload: .suggestDeletion)
        _draft = State(initialValue: start)
        _acknowledgedDelayedDeletion = State(initialValue: existing?.afterUpload == .deleteAfter30Days)
    }

    private var canSave: Bool {
        draft.problems.isEmpty && (draft.afterUpload != .deleteAfter30Days || acknowledgedDelayedDeletion)
    }

    var body: some View {
        ScreenScaffold {
            ScreenHeader(title: ruleID == nil ? "New Rule" : "Edit Rule", subtitle: "Create an automatic filing rule.")
                .padding(.bottom, 8)

            pickerRow(label: "When", value: draft.trigger.title, icon: draft.trigger.systemImage, tint: draft.trigger.tint) {
                ForEach(RuleTrigger.allCases, id: \.self) { trigger in
                    Button(trigger.title) { select(trigger) }
                }
            }
            pickerRow(label: "Save to", value: draft.provider.title, icon: "externaldrive.fill.badge.icloud", tint: .blue) {
                ForEach(CloudProvider.allCases, id: \.self) { provider in
                    Button(provider.title) { draft.provider = provider }
                }
            }

            TemplateField(label: "Folder", icon: "folder.fill", tint: .blue, text: $draft.folderTemplate,
                          variables: TemplateResolver.folderVariables)
            TemplateField(label: "File name", icon: "doc.fill", tint: .purple, text: $draft.fileNameTemplate,
                          variables: TemplateResolver.fileNameVariables)

            previewCard
            problemsView

            Card(padding: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Rule name").font(.caption).foregroundStyle(Palette.textSecondary)
                    TextField("Rule name", text: $draft.name)
                        .font(Typography.cardHeadline)
                        .textInputAutocapitalization(.words)
                }
            }

            afterUploadSection
            privacyCard

            Button("Save Rule") {
                store.save(draft)
                saved.toggle()
                dismiss()
            }
            .buttonStyle(.primary)
            .disabled(!canSave)
            .sensoryFeedback(.success, trigger: saved)

            if ruleID != nil {
                Button("Delete Rule", role: .destructive) { confirmingDelete = true }
                    .buttonStyle(.secondaryOutlined)
                    .confirmationDialog("Delete this rule?", isPresented: $confirmingDelete, titleVisibility: .visible) {
                        Button("Delete Rule", role: .destructive) {
                            store.delete(draft.id)
                            dismiss()
                        }
                    } message: {
                        Text("Files already filed stay where they are.")
                    }
            }
        }
    }

    /// Follow the new trigger's suggestions only where the user hasn't customised anything.
    private func select(_ trigger: RuleTrigger) {
        let old = draft.trigger
        if draft.name == old.title { draft.name = trigger.title }
        if draft.folderTemplate == old.suggestedFolder { draft.folderTemplate = trigger.suggestedFolder }
        if draft.fileNameTemplate == old.suggestedFileName { draft.fileNameTemplate = trigger.suggestedFileName }
        draft.trigger = trigger
    }

    private func pickerRow<Items: View>(label: String, value: String, icon: String, tint: Tint, @ViewBuilder items: () -> Items) -> some View {
        Menu {
            items()
        } label: {
            Card(padding: 16) {
                ListTile(systemImage: icon, tint: tint, title: label, subtitle: value)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(label): \(value)")
    }

    private var previewCard: some View {
        let preview = draft.preview()
        return Card(style: .info, padding: 16) {
            VStack(alignment: .leading, spacing: 8) {
                SectionLabel("Preview")
                Label(preview.rule.provider.title, systemImage: "externaldrive.fill.badge.icloud")
                Text(preview.fullPath)
                    .font(.system(.subheadline, design: .monospaced))
                    .foregroundStyle(Palette.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                if draft.trigger == .receipt {
                    Text("Example: a ฿3,450 receipt from Central Department Store, today.")
                        .font(.caption)
                        .foregroundStyle(Palette.textSecondary)
                }
            }
            .font(Typography.metadata)
            .foregroundStyle(Palette.textSecondary)
        }
    }

    @ViewBuilder
    private var problemsView: some View {
        ForEach(Array(draft.problems.enumerated()), id: \.offset) { _, problem in
            Label(message(for: problem), systemImage: "exclamationmark.triangle.fill")
                .font(Typography.metadata)
                .foregroundStyle(Tint.coral.foreground)
        }
    }

    private func message(for problem: StorageRule.Problem) -> String {
        switch problem {
        case .emptyFolder: "Add a folder to save into."
        case .emptyFileName: "Add a file name."
        case .unknownVariables(let names): localizedFormat("Not a known variable: %@. Tap a chip to insert one.", names.joined(separator: ", "))
        }
    }

    private var afterUploadSection: some View {
        Card(padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                ListTile(systemImage: "gearshape.fill", tint: .coral, title: "After upload",
                         subtitle: "What should happen to the original file?", showsChevron: false)
                ForEach(AfterUploadAction.allCases, id: \.self) { action in
                    let selected = draft.afterUpload == action
                    Button {
                        withAnimation(.spring(duration: 0.3)) { draft.afterUpload = action }
                    } label: {
                        Card(style: selected ? .selected : .plain, padding: 14) {
                            HStack(spacing: 14) {
                                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                    .font(.title2)
                                    .foregroundStyle(selected ? Palette.accent : Palette.textSecondary.opacity(0.6))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(action.title.localizedUI).font(.system(.body, weight: .semibold)).foregroundStyle(Palette.textPrimary)
                                    Text(action.detail.localizedUI).font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
                if draft.afterUpload == .deleteAfter30Days {
                    // Spec §7.8: delayed deletion must be explicit and conservative.
                    Toggle(isOn: $acknowledgedDelayedDeletion) {
                        Text("I understand originals will be suggested for deletion 30 days after each verified upload.")
                            .font(Typography.metadata)
                            .foregroundStyle(Palette.textPrimary)
                    }
                    .tint(Palette.accent)
                    .padding(.horizontal, 4)
                }
            }
        }
    }

    private var privacyCard: some View {
        Card(style: .info) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Tint.blue.foreground)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Classification happens on your device.").font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                    Text("Files are analyzed and categorized on this device. Nothing is sent to the cloud until you back up.")
                        .font(Typography.metadata).foregroundStyle(Palette.textSecondary)
                }
            }
        }
    }
}

/// Template text field with tappable variable chips.
private struct TemplateField: View {
    let label: String
    let icon: String
    let tint: Tint
    @Binding var text: String
    let variables: [String]

    var body: some View {
        Card(padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    IconTile(systemName: icon, tint: tint, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(LocalizedStringKey(label)).font(Typography.cardHeadline).foregroundStyle(Palette.textPrimary)
                        // Paths get long; wrap instead of scrolling out of view.
                        TextField(label, text: $text, axis: .vertical)
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(Palette.textSecondary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(variables, id: \.self) { variable in
                            Button(variable) { text += variable }
                            .font(.system(.caption, design: .monospaced, weight: .semibold))
                            .foregroundStyle(Palette.accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Palette.icyBlue, in: Capsule())
                            .accessibilityLabel(localizedFormat("Insert %@", variable))
                        }
                    }
                }
            }
        }
    }
}

#Preview {
    NavigationStack { RuleEditorView(ruleID: nil, existing: nil) }
        .previewEnvironment()
}
