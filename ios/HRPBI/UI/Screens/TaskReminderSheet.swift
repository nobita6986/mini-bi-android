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

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            Group {
                if isEditing {
                    reminderEditorView(theme: theme)
                } else {
                    reminderListView(theme: theme)
                }
            }
            .background(theme.background.ignoresSafeArea())
            .navigationTitle(isEditing ? (editingId == nil ? "Thêm nhắc việc" : "Chỉnh sửa nhắc việc") : "Nhắc việc")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if isEditing {
                        Button("Hủy") {
                            isEditing = false
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
        }
    }

    // MARK: - List View

    private func reminderListView(theme: MiniBiThemePalette) -> some View {
        ScrollView {
            VStack(spacing: 12) {
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
                    .frame(height: 46)
                    .background(theme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
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
                    .background(Color.white)
                    .cornerRadius(14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(theme.topBarBorder, lineWidth: 1)
                    )
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
                    HStack(spacing: 8) {
                        Text(item.formattedTime)
                            .font(.system(size: 22, weight: .heavy, design: .rounded))
                            .foregroundColor(item.isEnabled ? theme.primary : SemanticColors.textMuted)

                        Text(item.repeatMode.labelVi)
                            .font(.caption2.weight(.bold))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(
                                item.repeatMode == .daily
                                    ? theme.primary.opacity(0.14)
                                    : Color.orange.opacity(0.16)
                            )
                            .foregroundColor(
                                item.repeatMode == .daily
                                    ? theme.primary
                                    : Color.orange
                            )
                            .cornerRadius(6)
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
            }
            .buttonStyle(.plain)

            Toggle(
                "",
                isOn: Binding(
                    get: { item.isEnabled },
                    set: { newValue in
                        viewModel.setTaskReminderEnabled(id: item.id, isEnabled: newValue)
                    }
                )
            )
            .labelsHidden()
            .tint(theme.primary)

            Button(role: .destructive) {
                viewModel.deleteTaskReminder(id: item.id)
            } label: {
                Image(systemName: "trash")
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.dangerText)
                    .padding(8)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(
                    item.isEnabled ? theme.primary.opacity(0.32) : theme.topBarBorder,
                    lineWidth: 1
                )
        )
    }

    // MARK: - Editor View

    private func reminderEditorView(theme: MiniBiThemePalette) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Title Input
                VStack(alignment: .leading, spacing: 6) {
                    Text("Tiêu đề nhắc việc")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    TextField("Ví dụ: Gửi báo cáo ca sáng", text: $draftTitle)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: draftTitle) { newValue in
                            if newValue.count > TaskReminderManager.maxTitleLength {
                                draftTitle = String(newValue.prefix(TaskReminderManager.maxTitleLength))
                            }
                        }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(theme.topBarBorder, lineWidth: 1)
                )

                // Time Picker (24h HH:mm)
                VStack(alignment: .leading, spacing: 10) {
                    Text("Thời gian báo nhắc (24h)")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Giờ")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textSecondary)
                            Picker("Giờ", selection: $draftHour) {
                                ForEach(0..<24, id: \.self) { h in
                                    Text(String(format: "%02d giờ", h)).tag(h)
                                }
                            }
                            .pickerStyle(.menu)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 4)
                            .background(theme.background)
                            .cornerRadius(8)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Phút")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textSecondary)
                            Picker("Phút", selection: $draftMinute) {
                                ForEach(0..<60, id: \.self) { m in
                                    Text(String(format: "%02d phút", m)).tag(m)
                                }
                            }
                            .pickerStyle(.menu)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 4)
                            .background(theme.background)
                            .cornerRadius(8)
                        }
                    }

                    // Quick Preset Buttons
                    HStack(spacing: 6) {
                        ForEach([(7, 30), (8, 0), (12, 0), (17, 0), (20, 0)], id: \.0) { preset in
                            let isSelected = draftHour == preset.0 && draftMinute == preset.1
                            Button {
                                draftHour = preset.0
                                draftMinute = preset.1
                            } label: {
                                Text(String(format: "%02d:%02d", preset.0, preset.1))
                                    .font(.caption2.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 7)
                                    .background(isSelected ? theme.primary.opacity(0.15) : theme.background)
                                    .foregroundColor(isSelected ? theme.primary : SemanticColors.textSecondary)
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                                            .stroke(isSelected ? theme.primary : Color.clear, lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(theme.topBarBorder, lineWidth: 1)
                )

                // Repeat Mode (DAILY / ONCE)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Chế độ lặp")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    HStack(spacing: 10) {
                        ForEach(TaskReminderRepeatMode.allCases, id: \.rawValue) { mode in
                            let selected = draftRepeatMode == mode
                            Button {
                                draftRepeatMode = mode
                            } label: {
                                Text(mode.labelVi)
                                    .font(.subheadline.weight(selected ? .bold : .medium))
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(selected ? theme.primary.opacity(0.14) : theme.background)
                                    .foregroundColor(selected ? theme.primary : SemanticColors.textPrimary)
                                    .cornerRadius(10)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .stroke(selected ? theme.primary : theme.topBarBorder, lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(theme.topBarBorder, lineWidth: 1)
                )

                // System Sounds List
                VStack(alignment: .leading, spacing: 8) {
                    Text("Âm thanh báo động (Hệ thống)")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)

                    ForEach(TaskReminderSystemSound.allSystemSounds) { sound in
                        let selected = draftSoundId == sound.id
                        Button {
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
                            .padding(.vertical, 10)
                            .background(selected ? theme.primary.opacity(0.1) : theme.background)
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(selected ? theme.primary : Color.clear, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(theme.topBarBorder, lineWidth: 1)
                )

                // Save & Cancel Buttons
                HStack(spacing: 10) {
                    Button {
                        isEditing = false
                    } label: {
                        Text("Hủy")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(Color.white)
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(theme.topBarBorder, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)

                    Button {
                        saveDraftReminder()
                    } label: {
                        Text("Lưu nhắc việc")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(theme.primary)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(14)
        }
    }

    // MARK: - Actions

    private func openCreateForm() {
        editingId = nil
        draftTitle = ""
        draftHour = 8
        draftMinute = 0
        draftRepeatMode = .daily
        draftSoundId = TaskReminderSystemSound.defaultSound.id
        isEditing = true
    }

    private func openEditForm(_ item: TaskReminderItem) {
        editingId = item.id
        draftTitle = item.title
        draftHour = item.hour
        draftMinute = item.minute
        draftRepeatMode = item.repeatMode
        draftSoundId = item.soundId
        isEditing = true
    }

    private func saveDraftReminder() {
        _ = viewModel.saveTaskReminder(
            id: editingId,
            title: draftTitle,
            hour: draftHour,
            minute: draftMinute,
            repeatMode: draftRepeatMode,
            soundId: draftSoundId,
            isEnabled: true
        )
        isEditing = false
    }
}
