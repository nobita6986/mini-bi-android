import SwiftUI

public struct TaskReminderSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var isEditing: Bool = false
    @State private var editingId: String?
    @State private var draftTitle: String = ""
    @State private var draftHour: Int = 8
    @State private var draftMinute: Int = 0
    @State private var draftRepeatMode: TaskReminderRepeatMode = .daily
    @State private var draftSoundId: String = TaskReminderSystemSound.defaultSound.id
    @State private var reminderPendingDeletion: TaskReminderItem?

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    private var wheelTimeBinding: Binding<Date> {
        Binding<Date>(
            get: {
                var cal = Calendar(identifier: .gregorian)
                cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
                var comps = cal.dateComponents([.year, .month, .day], from: Date())
                comps.hour = min(23, max(0, draftHour))
                comps.minute = min(59, max(0, draftMinute))
                comps.second = 0
                return cal.date(from: comps) ?? Date()
            },
            set: { newDate in
                var cal = Calendar(identifier: .gregorian)
                cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
                let comps = cal.dateComponents([.hour, .minute], from: newDate)
                let nextH = min(23, max(0, comps.hour ?? 8))
                let nextM = min(59, max(0, comps.minute ?? 0))
                if nextH != draftHour || nextM != draftMinute {
                    draftHour = nextH
                    draftMinute = nextM
                }
            }
        )
    }

    public var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            Group {
                if isEditing {
                    reminderEditorView(theme: theme)
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                } else {
                    reminderListView(theme: theme)
                        .transition(.opacity)
                }
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.86), value: isEditing)
            .background(theme.background.ignoresSafeArea())
            .navigationTitle(isEditing ? (editingId == nil ? "Thêm nhắc việc" : "Chỉnh sửa nhắc việc") : "Nhắc việc")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if isEditing {
                        Button("Hủy") {
                            HapticsManager.selection()
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) {
                                isEditing = false
                            }
                        }
                    } else {
                        Button("Đóng") {
                            dismiss()
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isEditing {
                        Button("Lưu") {
                            saveDraftReminder()
                        }
                        .fontWeight(.bold)
                    } else {
                        Button {
                            openCreateForm()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "plus.circle.fill")
                                Text("Thêm")
                            }
                            .font(.subheadline.weight(.semibold))
                        }
                    }
                }
            }
            .onAppear {
                viewModel.refreshTaskReminders()
            }
            .confirmationDialog(
                "Xóa lịch nhắc việc?",
                isPresented: Binding(
                    get: { reminderPendingDeletion != nil },
                    set: { if !$0 { reminderPendingDeletion = nil } }
                ),
                titleVisibility: .visible
            ) {
                if let pending = reminderPendingDeletion {
                    Button("Xóa nhắc việc \(pending.formattedTime)", role: .destructive) {
                        HapticsManager.lightImpact()
                        viewModel.deleteTaskReminder(id: pending.id)
                        reminderPendingDeletion = nil
                    }
                }
                Button("Hủy", role: .cancel) {
                    reminderPendingDeletion = nil
                }
            } message: {
                if let pending = reminderPendingDeletion {
                    Text("Bạn có chắc chắn muốn xóa lịch nhắc \"\(pending.title)\" lúc \(pending.formattedTime)?")
                }
            }
        }
    }

    // MARK: - List View

    private func reminderListView(theme: MiniBiThemePalette) -> some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                Button {
                    openCreateForm()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus.circle.fill")
                            .font(.body.weight(.bold))
                        Text("Thêm nhắc việc")
                            .font(.subheadline.weight(.bold))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 48)
                    .background(
                        RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                            .fill(theme.primary)
                    )
                    .foregroundColor(.white)
                }
                .buttonStyle(HrpPressableButtonStyle())
                .padding(.top, 4)

                if viewModel.taskReminders.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "bell.badge")
                            .font(.system(size: 36))
                            .foregroundColor(theme.primary.opacity(0.65))
                        Text("Chưa có lịch nhắc việc nào.")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Nhấn \"Thêm nhắc việc\" để tạo hẹn giờ báo thức công việc.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 36)
                    .padding(.horizontal, 16)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                } else {
                    ForEach(viewModel.taskReminders) { item in
                        reminderRowCard(item: item, theme: theme)
                    }
                }
            }
            .padding(14)
        }
    }

    private func reminderRowCard(item: TaskReminderItem, theme: MiniBiThemePalette) -> some View {
        HStack(spacing: 12) {
            Button {
                openEditForm(item)
            } label: {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(item.formattedTime)
                            .font(.system(size: 30, weight: .bold, design: .rounded).monospacedDigit())
                            .contentTransition(.numericText())
                            .foregroundColor(item.isEnabled ? SemanticColors.textPrimary : SemanticColors.textMuted.opacity(0.6))

                        Text(item.repeatMode.labelVi)
                            .font(.caption2.weight(.bold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous)
                                    .fill(
                                        item.repeatMode == .daily
                                            ? theme.primary.opacity(0.14)
                                            : Color.orange.opacity(0.16)
                                    )
                            )
                            .foregroundColor(
                                item.repeatMode == .daily
                                    ? theme.primary
                                    : Color.orange
                            )
                    }

                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(item.isEnabled ? SemanticColors.textPrimary : SemanticColors.textMuted)
                        .lineLimit(1)

                    Text("Âm báo: \(item.soundTitle)")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Toggle(
                "",
                isOn: Binding(
                    get: { item.isEnabled },
                    set: { newValue in
                        HapticsManager.selection()
                        viewModel.setTaskReminderEnabled(id: item.id, isEnabled: newValue)
                    }
                )
            )
            .labelsHidden()
            .tint(theme.primary)

            Button(role: .destructive) {
                HapticsManager.warning()
                reminderPendingDeletion = item
            } label: {
                Image(systemName: "trash")
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.dangerText)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 14)
        .padding(.trailing, 6)
        .padding(.vertical, 10)
        .hrpCardSurface(
            cornerRadius: HrpDesignTokens.radiusCard,
            borderColor: item.isEnabled ? theme.primary.opacity(0.32) : SemanticColors.borderSubtle.opacity(0.85)
        )
    }

    // MARK: - Editor View (Apple Clock Native Wheel Picker)

    private func reminderEditorView(theme: MiniBiThemePalette) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Title Input
                VStack(alignment: .leading, spacing: 6) {
                    Text("Tiêu đề nhắc việc")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    TextField("Ví dụ: Gửi báo cáo ca sáng", text: $draftTitle)
                        .padding(11)
                        .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                        .onChange(of: draftTitle) { newValue in
                            if newValue.count > TaskReminderManager.maxTitleLength {
                                draftTitle = String(newValue.prefix(TaskReminderManager.maxTitleLength))
                            }
                        }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                // Time Picker (Native iOS Wheel DatePicker + Quick Presets)
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Thời gian báo nhắc (24h)")
                            .font(.caption.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Spacer()
                        Text(String(format: "%02d:%02d", draftHour, draftMinute))
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .contentTransition(.numericText())
                            .foregroundColor(theme.primary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous)
                                    .fill(theme.primaryLight)
                            )
                    }

                    DatePicker(
                        "",
                        selection: wheelTimeBinding,
                        displayedComponents: .hourAndMinute
                    )
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "vi_VN"))
                    .frame(maxWidth: .infinity)
                    .frame(height: 168)
                    .clipped()

                    // Quick Preset Buttons
                    HStack(spacing: 6) {
                        ForEach([(7, 30), (8, 0), (12, 0), (13, 30), (17, 30)], id: \.0) { preset in
                            let isSelected = draftHour == preset.0 && draftMinute == preset.1
                            Button {
                                HapticsManager.selection()
                                draftHour = preset.0
                                draftMinute = preset.1
                            } label: {
                                Text(String(format: "%02d:%02d", preset.0, preset.1))
                                    .font(.caption2.weight(.bold).monospacedDigit())
                                    .frame(maxWidth: .infinity, minHeight: 36)
                                    .background(
                                        RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous)
                                            .fill(isSelected ? theme.primary.opacity(0.15) : SemanticColors.surfaceMuted)
                                    )
                                    .foregroundColor(isSelected ? theme.primary : SemanticColors.textSecondary)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous)
                                            .stroke(isSelected ? theme.primary : Color.clear, lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                // Repeat Mode (DAILY / ONCE)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Chế độ lặp")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    HStack(spacing: 10) {
                        ForEach(TaskReminderRepeatMode.allCases, id: \.rawValue) { mode in
                            let selected = draftRepeatMode == mode
                            Button {
                                HapticsManager.selection()
                                draftRepeatMode = mode
                            } label: {
                                Text(mode.labelVi)
                                    .font(.subheadline.weight(selected ? .bold : .medium))
                                    .frame(maxWidth: .infinity)
                                    .frame(minHeight: 44)
                                    .background(
                                        RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                            .fill(selected ? theme.primary.opacity(0.14) : SemanticColors.surfaceMuted)
                                    )
                                    .foregroundColor(selected ? theme.primary : SemanticColors.textPrimary)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                            .stroke(selected ? theme.primary : SemanticColors.borderSubtle, lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                // System Sounds List
                VStack(alignment: .leading, spacing: 8) {
                    Text("Âm thanh báo động (Hệ thống)")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    ForEach(TaskReminderSystemSound.allSystemSounds) { sound in
                        let selected = draftSoundId == sound.id
                        Button {
                            HapticsManager.selection()
                            draftSoundId = sound.id
                            viewModel.playTaskReminderSoundPreview(soundId: sound.id)
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                                    .foregroundColor(selected ? theme.primary : SemanticColors.textMuted)
                                Text(sound.title)
                                    .font(.subheadline.weight(selected ? .bold : .regular))
                                    .foregroundColor(SemanticColors.textPrimary)
                                Spacer()
                                Image(systemName: "speaker.wave.2.fill")
                                    .font(.caption)
                                    .foregroundColor(selected ? theme.primary : SemanticColors.textMuted)
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 44)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(selected ? theme.primary.opacity(0.1) : SemanticColors.surfaceMuted)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .stroke(selected ? theme.primary : Color.clear, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                // Save & Cancel Buttons
                HStack(spacing: 10) {
                    Button {
                        HapticsManager.selection()
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) {
                            isEditing = false
                        }
                    } label: {
                        Text("Hủy")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 48)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(Color.white)
                            )
                            .foregroundColor(SemanticColors.textPrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )
                    }
                    .buttonStyle(HrpPressableButtonStyle())

                    Button {
                        saveDraftReminder()
                    } label: {
                        Text("Lưu nhắc việc")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 48)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(theme.primary)
                            )
                            .foregroundColor(.white)
                    }
                    .buttonStyle(HrpPressableButtonStyle())
                }
            }
            .padding(14)
        }
    }

    // MARK: - Actions

    private func openCreateForm() {
        HapticsManager.lightImpact()
        editingId = nil
        draftTitle = ""
        draftHour = 8
        draftMinute = 0
        draftRepeatMode = .daily
        draftSoundId = TaskReminderSystemSound.defaultSound.id
        withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) {
            isEditing = true
        }
    }

    private func openEditForm(_ item: TaskReminderItem) {
        HapticsManager.selection()
        editingId = item.id
        draftTitle = item.title
        draftHour = item.hour
        draftMinute = item.minute
        draftRepeatMode = item.repeatMode
        draftSoundId = item.soundId
        withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) {
            isEditing = true
        }
    }

    private func saveDraftReminder() {
        HapticsManager.success()
        _ = viewModel.saveTaskReminder(
            id: editingId,
            title: draftTitle,
            hour: draftHour,
            minute: draftMinute,
            repeatMode: draftRepeatMode,
            soundId: draftSoundId,
            isEnabled: true
        )
        withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) {
            isEditing = false
        }
    }
}
