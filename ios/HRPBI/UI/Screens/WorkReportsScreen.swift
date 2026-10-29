import SwiftUI

// MARK: - Work Reports Screen & Overdue Contract UI
// Mirrors Android's `ui/screens/WorkReportsScreen.kt` 100%

public struct WorkReportsScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme

        VStack(spacing: 0) {
            // Sub-tab selector
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(WorkReportsSubTab.allCases) { tab in
                        let isSelected = viewModel.selectedWorkReportsSubTab == tab
                        let countBadge = badgeCount(for: tab)
                        Button {
                            KeyboardDismissal.dismiss()
                            viewModel.selectedWorkReportsSubTab = tab
                        } label: {
                            HStack(spacing: 6) {
                                Text(tab.titleVi)
                                    .font(.subheadline.weight(isSelected ? .bold : .medium))
                                if countBadge > 0 {
                                    Text("\(countBadge)")
                                        .font(.caption2.weight(.bold))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(isSelected ? Color.white.opacity(0.24) : theme.primaryLight)
                                        .foregroundColor(isSelected ? .white : theme.primary)
                                        .clipShape(Capsule())
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(isSelected ? theme.primary : Color.white)
                            .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
                            .clipShape(Capsule())
                            .overlay(
                                Capsule()
                                    .stroke(isSelected ? theme.primary : SemanticColors.borderSubtle, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color.white)

            Divider()

            ScrollView {
                VStack(spacing: 12) {
                    if viewModel.isLoadingWorkReports {
                        ProgressView("Đang tải danh sách báo cáo công việc...")
                            .padding(.vertical, 18)
                    }

                    switch viewModel.selectedWorkReportsSubTab {
                    case .actionable:
                        obligationsListSection(
                            items: viewModel.actionableObligations,
                            emptyText: "Bạn đã hoàn thành tất cả báo cáo công việc cần nộp!"
                        )
                    case .history:
                        obligationsListSection(
                            items: viewModel.historyObligations,
                            emptyText: "Chưa có lịch sử báo cáo công việc đã lưu / đã nộp / quá hạn."
                        )
                    case .reviewQueue:
                        reviewQueueSection
                    case .analytics:
                        analyticsSection
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.loadWorkReportsData()
            }
        }
        .background(theme.background)
        .dismissKeyboardOnTapOutside()
        .onAppear {
            if viewModel.workReportObligations.isEmpty {
                Task { await viewModel.loadWorkReportsData() }
            }
        }
        .sheet(item: $viewModel.selectedObligationEditor) { editor in
            WorkReportEditorSheet(viewModel: viewModel, obligation: editor)
        }
        .sheet(item: $viewModel.selectedReviewDetail) { review in
            WorkReportReviewDetailSheet(viewModel: viewModel, review: review)
        }
    }

    private func badgeCount(for tab: WorkReportsSubTab) -> Int {
        switch tab {
        case .actionable:
            return viewModel.actionableObligations.count
        case .history:
            return viewModel.historyObligations.count
        case .reviewQueue:
            return viewModel.workReportReviews.count
        case .analytics:
            return 0
        }
    }

    // MARK: - 1 & 2. Obligations List (`Cần nộp` & `Lịch sử / Đã lưu / Đã nộp`)

    private func obligationsListSection(items: [WorkReportObligationItem], emptyText: String) -> some View {
        let theme = viewModel.selectedTheme

        return VStack(spacing: 12) {
            if items.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "doc.text.magnifyingglass")
                        .font(.largeTitle)
                        .foregroundColor(SemanticColors.textMuted)
                    Text(emptyText)
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(28)
                .background(Color.white)
                .cornerRadius(14)
            } else {
                ForEach(items) { ob in
                    let badgeText = WorkReportsContract.obligationBadgeDisplayLabel(ob)
                    let isEditable = WorkReportsContract.isEditableObligation(ob)

                    Button {
                        Task {
                            await viewModel.openObligationEditor(ob)
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(ob.templateName)
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                    Text("\(WorkReportsContract.periodTypeDisplayLabel(ob.periodType)) · Kỳ: \(WorkReportsContract.formatPeriodKeyVi(ob.periodKey))")
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textSecondary)
                                }
                                Spacer()
                                Text(badgeText)
                                    .font(.caption2.weight(.bold))
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 4)
                                    .background(obligationBadgeBg(ob))
                                    .foregroundColor(obligationBadgeFg(ob))
                                    .overlay(
                                        Capsule()
                                            .stroke(obligationBadgeBorder(ob), lineWidth: 1)
                                    )
                                    .clipShape(Capsule())
                            }

                            HStack(spacing: 12) {
                                if let dueAt = ob.dueAt, !dueAt.isEmpty {
                                    Label("Hạn nộp: \(WorkReportsContract.formatIsoDateTimeVi(dueAt))", systemImage: "calendar.badge.clock")
                                        .font(.caption2)
                                        .foregroundColor(ob.isOverdue ? SemanticColors.dangerText : SemanticColors.textMuted)
                                }
                                Spacer()
                                Text(isEditable ? "Mở để nhập / nộp →" : "Xem nội dung (Chỉ xem) →")
                                    .font(.caption2.weight(.bold))
                                    .foregroundColor(isEditable ? theme.primary : SemanticColors.textSecondary)
                            }

                            if let comment = ob.reviewComment, !comment.isEmpty {
                                HStack(spacing: 6) {
                                    Image(systemName: "bubble.left.and.exclamationmark.bubble.right.fill")
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.warningText)
                                    Text("Ý kiến người duyệt: \(comment)")
                                        .font(.caption2.weight(.medium))
                                        .foregroundColor(SemanticColors.warningText)
                                }
                                .padding(8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(SemanticColors.warningBg)
                                .cornerRadius(8)
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(ob.isOverdue ? SemanticColors.dangerBorder : SemanticColors.borderSubtle, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func obligationBadgeBg(_ ob: WorkReportObligationItem) -> Color {
        if ob.isOverdue {
            return SemanticColors.dangerBg
        }
        switch ob.submissionState.uppercased() {
        case WorkReportsContract.stateApproved, "COMPLETED":
            return SemanticColors.successBg
        case WorkReportsContract.stateSubmitted, "PENDING_REVIEW":
            return SemanticColors.infoBg
        case WorkReportsContract.stateRejected:
            return SemanticColors.dangerBg
        case WorkReportsContract.stateDraft:
            return SemanticColors.warningBg
        default:
            return Color(hex: 0xF1F5F9)
        }
    }

    private func obligationBadgeFg(_ ob: WorkReportObligationItem) -> Color {
        if ob.isOverdue {
            return SemanticColors.dangerText
        }
        switch ob.submissionState.uppercased() {
        case WorkReportsContract.stateApproved, "COMPLETED":
            return SemanticColors.successText
        case WorkReportsContract.stateSubmitted, "PENDING_REVIEW":
            return SemanticColors.infoText
        case WorkReportsContract.stateRejected:
            return SemanticColors.dangerText
        case WorkReportsContract.stateDraft:
            return SemanticColors.warningText
        default:
            return SemanticColors.textSecondary
        }
    }

    private func obligationBadgeBorder(_ ob: WorkReportObligationItem) -> Color {
        if ob.isOverdue {
            return SemanticColors.dangerBorder
        }
        return Color.clear
    }

    // MARK: - 3. Review Queue Section (`Chờ duyệt`)

    private var reviewQueueSection: some View {
        let theme = viewModel.selectedTheme
        return VStack(spacing: 12) {
            if viewModel.workReportReviews.isEmpty {
                Text("Không có báo cáo công việc nào đang chờ bạn phê duyệt.")
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .background(Color.white)
                    .cornerRadius(14)
            } else {
                ForEach(viewModel.workReportReviews) { rev in
                    Button {
                        viewModel.selectedReviewDetail = rev
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(rev.templateName)
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(SemanticColors.textPrimary)
                                Spacer()
                                Text("Kỳ \(WorkReportsContract.formatPeriodKeyVi(rev.periodKey))")
                                    .font(.caption2.weight(.bold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(theme.primaryLight)
                                    .foregroundColor(theme.primary)
                                    .clipShape(Capsule())
                            }
                            Text("Người nộp: \(rev.reporterName) · Đơn vị: \(rev.unitName)")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Text("Thời gian nộp: \(WorkReportsContract.formatIsoDateTimeVi(rev.submittedAt))")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white)
                        .cornerRadius(14)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - 4. Analytics Summary Section (`Tổng hợp báo cáo`)

    private var analyticsSection: some View {
        let kpis = viewModel.workReportAnalytics.kpis
        let theme = viewModel.selectedTheme

        return VStack(alignment: .leading, spacing: 14) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                analyticsKpiCard("Tổng nghĩa vụ", "\(kpis.totalObligations)", theme.primary)
                analyticsKpiCard("Đã nộp / Hoàn thành", "\(kpis.submittedCount) (\(WorkReportsContract.formatPercentVi(kpis.completionRate)))", SemanticColors.successText)
                analyticsKpiCard("Đã phê duyệt", "\(kpis.approvedCount)", SemanticColors.infoText)
                analyticsKpiCard("Quá hạn nộp", "\(kpis.overdueCount)", SemanticColors.dangerText)
            }

            if !viewModel.workReportAnalytics.byUnit.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Tỷ lệ hoàn thành theo Đơn vị")
                        .font(.headline)
                    ForEach(viewModel.workReportAnalytics.byUnit) { row in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.groupLabel)
                                    .font(.subheadline.weight(.semibold))
                                Text("Đã nộp: \(row.submittedCount)/\(row.totalObligations) · Quá hạn: \(row.overdueCount)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            Spacer()
                            Text(WorkReportsContract.formatPercentVi(row.completionRate))
                                .font(.caption.weight(.bold))
                                .foregroundColor(theme.primary)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
            }
        }
    }

    private func analyticsKpiCard(_ title: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundColor(SemanticColors.textSecondary)
            Text(value)
                .font(.headline.weight(.bold))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.white)
        .cornerRadius(12)
    }
}

// MARK: - Work Report Dynamic Form Editor Sheet (Enforces Read-Only Lock When `isOverdue == true`)

public struct WorkReportEditorSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    let obligation: WorkReportObligationItem
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        let currentOb = viewModel.selectedObligationEditor ?? obligation
        let isReadOnly = !WorkReportsContract.isEditableObligation(currentOb)
        let badgeLabel = WorkReportsContract.obligationBadgeDisplayLabel(currentOb)
        let theme = viewModel.selectedTheme

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Header Metadata & Overdue / Read-Only Banner
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(currentOb.templateName)
                                .font(.title3.weight(.bold))
                            Spacer()
                            Text(badgeLabel)
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(currentOb.isOverdue ? SemanticColors.dangerBg : theme.primaryLight)
                                .foregroundColor(currentOb.isOverdue ? SemanticColors.dangerText : theme.primary)
                                .clipShape(Capsule())
                        }

                        let displayPeriodKey = currentOb.periodKey.isEmpty ? obligation.periodKey : currentOb.periodKey
                        let displayDueAt = currentOb.dueAt ?? obligation.dueAt
                        Text("Kỳ báo cáo: \(WorkReportsContract.formatPeriodKeyVi(displayPeriodKey)) · Hạn nộp: \(WorkReportsContract.formatIsoDateTimeVi(displayDueAt))")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)

                        if currentOb.isOverdue {
                            HStack(spacing: 8) {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                                Text("Đã quá hạn nộp báo cáo. Biểu mẫu đang ở chế độ chỉ xem (quá hạn nộp).")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(SemanticColors.dangerText)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.dangerBg)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(SemanticColors.dangerBorder, lineWidth: 1)
                            )
                            .cornerRadius(8)
                        } else if isReadOnly {
                            HStack(spacing: 8) {
                                Image(systemName: "eye.fill")
                                    .foregroundColor(SemanticColors.infoText)
                                Text("Biểu mẫu đã nộp đang ở chế độ chỉ xem.")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.infoText)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.infoBg)
                            .cornerRadius(8)
                        }

                        if let rejectNote = currentOb.reviewComment, !rejectNote.isEmpty {
                            HStack(spacing: 8) {
                                Image(systemName: "exclamationmark.bubble.fill")
                                    .foregroundColor(SemanticColors.warningText)
                                Text("Yêu cầu bổ sung từ người duyệt: \(rejectNote)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.warningText)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.warningBg)
                            .cornerRadius(8)
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(14)

                    // Dynamic Sections & Fields
                    if currentOb.sections.isEmpty {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("Đang tải biểu mẫu báo cáo từ máy chủ...")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(Color.white)
                        .cornerRadius(14)
                    }

                    ForEach(currentOb.sections) { section in
                        VStack(alignment: .leading, spacing: 12) {
                            Text(section.title)
                                .font(.headline)
                                .foregroundColor(SemanticColors.textPrimary)

                            if let desc = section.description, !desc.isEmpty {
                                Text(desc)
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }

                            ForEach(section.fields) { field in
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack(spacing: 4) {
                                        Text(field.label)
                                            .font(.caption.weight(.semibold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                        if field.required && !field.isBooleanType {
                                            Text("*")
                                                .font(.caption.weight(.bold))
                                                .foregroundColor(SemanticColors.dangerText)
                                        }
                                    }

                                    if let hint = field.placeholder, !hint.isEmpty, field.type != "text" && field.type != "number" {
                                        Text(hint)
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    }

                                    let binding = Binding<String>(
                                        get: { viewModel.editorFieldValues[field.id] ?? "" },
                                        set: { newVal in
                                            guard !isReadOnly else { return }
                                            viewModel.editorFieldValues[field.id] = newVal
                                        }
                                    )

                                    switch field.type.lowercased() {
                                    case "textarea", "multiline", "long_text":
                                        TextEditor(text: binding)
                                            .disabled(isReadOnly)
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .scrollContentBackground(.hidden)
                                            .frame(minHeight: 88)
                                            .padding(6)
                                            .background(isReadOnly ? Color(hex: 0xF1F5F9) : Color(hex: 0xF8FAFC))
                                            .cornerRadius(8)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                            )

                                    case "boolean", "bool", "checkbox", "switch":
                                        let isChecked = ["true", "1", "yes", "có"].contains(binding.wrappedValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
                                        Toggle(isOn: Binding<Bool>(
                                            get: { isChecked },
                                            set: { newBool in
                                                guard !isReadOnly else { return }
                                                viewModel.editorFieldValues[field.id] = newBool ? "true" : "false"
                                            }
                                        )) {
                                            Text(isChecked ? "Có / Đạt" : "Không")
                                                .font(.subheadline)
                                                .foregroundColor(SemanticColors.textPrimary)
                                        }
                                        .disabled(isReadOnly)
                                        .tint(theme.primary)
                                        .padding(10)
                                        .background(isReadOnly ? Color(hex: 0xF1F5F9) : Color(hex: 0xF8FAFC))
                                        .cornerRadius(8)

                                    case "single_select", "select", "dropdown", "radio":
                                        if !field.options.isEmpty {
                                            Menu {
                                                if !field.required {
                                                    Button("— Để trống —") {
                                                        if !isReadOnly { viewModel.editorFieldValues[field.id] = "" }
                                                    }
                                                }
                                                ForEach(field.options) { opt in
                                                    Button(opt.label) {
                                                        if !isReadOnly { viewModel.editorFieldValues[field.id] = opt.value }
                                                    }
                                                }
                                            } label: {
                                                let currentVal = binding.wrappedValue
                                                let currentLabel = field.options.first(where: { $0.value == currentVal })?.label
                                                    ?? (currentVal.isEmpty ? "— Chọn \(field.label) —" : currentVal)
                                                HStack {
                                                    Text(currentLabel)
                                                        .font(.subheadline)
                                                        .foregroundColor(currentVal.isEmpty ? SemanticColors.textMuted : SemanticColors.textPrimary)
                                                    Spacer()
                                                    Image(systemName: "chevron.up.chevron.down")
                                                        .font(.caption)
                                                        .foregroundColor(SemanticColors.textMuted)
                                                }
                                                .padding(10)
                                                .background(isReadOnly ? Color(hex: 0xF1F5F9) : Color(hex: 0xF8FAFC))
                                                .cornerRadius(8)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                                )
                                            }
                                            .disabled(isReadOnly)
                                        } else {
                                            TextField(field.placeholder ?? "Nhập nội dung...", text: binding)
                                                .submitLabel(.done)
                                                .disabled(isReadOnly)
                                                .foregroundColor(SemanticColors.textPrimary)
                                                .padding(10)
                                                .background(isReadOnly ? Color(hex: 0xF1F5F9) : Color(hex: 0xF8FAFC))
                                                .cornerRadius(8)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                                )
                                        }

                                    case "multi_select", "multiselect":
                                        let selectedSet = Set(
                                            binding.wrappedValue
                                                .split(separator: ",")
                                                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                                                .filter { !$0.isEmpty }
                                        )
                                        VStack(alignment: .leading, spacing: 6) {
                                            ForEach(field.options) { opt in
                                                let isSel = selectedSet.contains(opt.value)
                                                Button {
                                                    KeyboardDismissal.dismiss()
                                                    guard !isReadOnly else { return }
                                                    var next = selectedSet
                                                    if isSel { next.remove(opt.value) } else { next.insert(opt.value) }
                                                    let ordered = field.options.map(\.value).filter { next.contains($0) }
                                                    viewModel.editorFieldValues[field.id] = ordered.joined(separator: ",")
                                                } label: {
                                                    HStack(spacing: 8) {
                                                        Image(systemName: isSel ? "checkmark.square.fill" : "square")
                                                            .foregroundColor(isSel ? theme.primary : SemanticColors.textMuted)
                                                        Text(opt.label)
                                                            .font(.subheadline)
                                                            .foregroundColor(SemanticColors.textPrimary)
                                                        Spacer()
                                                    }
                                                    .padding(.vertical, 6)
                                                    .padding(.horizontal, 10)
                                                    .background(isSel ? theme.primaryLight.opacity(0.4) : Color(hex: 0xF8FAFC))
                                                    .cornerRadius(8)
                                                }
                                                .buttonStyle(.plain)
                                                .disabled(isReadOnly)
                                            }
                                        }

                                    case "date":
                                        TextField(field.placeholder ?? "YYYY-MM-DD hoặc DD/MM/YYYY", text: binding)
                                            .submitLabel(.done)
                                            .disabled(isReadOnly)
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .padding(10)
                                            .background(isReadOnly ? Color(hex: 0xF1F5F9) : Color(hex: 0xF8FAFC))
                                            .cornerRadius(8)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                            )

                                    default:
                                        TextField(field.placeholder ?? "Nhập nội dung...", text: binding)
                                            .submitLabel(.done)
                                            .disabled(isReadOnly)
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .keyboardType(field.type == "number" ? .decimalPad : .default)
                                            .padding(10)
                                            .background(isReadOnly ? Color(hex: 0xF1F5F9) : Color(hex: 0xF8FAFC))
                                            .cornerRadius(8)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                            )
                                    }
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(14)
                    }

                    // Action Buttons (Completely locked/hidden when `isReadOnly == true`)
                    if !isReadOnly {
                        HStack(spacing: 12) {
                            Button {
                                KeyboardDismissal.dismiss()
                                Task {
                                    await viewModel.saveWorkReportDraft()
                                }
                            } label: {
                                HStack {
                                    Spacer()
                                    Text("Lưu nháp")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(theme.primary)
                                    Spacer()
                                }
                                .padding(.vertical, 13)
                                .background(theme.primaryLight)
                                .cornerRadius(12)
                            }
                            .disabled(viewModel.isSavingWorkReport)

                            Button {
                                KeyboardDismissal.dismiss()
                                Task {
                                    await viewModel.submitWorkReportFinal()
                                }
                            } label: {
                                HStack {
                                    Spacer()
                                    if viewModel.isSavingWorkReport {
                                        ProgressView().tint(.white).padding(.trailing, 6)
                                    }
                                    Text("Nộp báo cáo")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(.white)
                                    Spacer()
                                }
                                .padding(.vertical, 13)
                                .background(theme.primary)
                                .cornerRadius(12)
                            }
                            .disabled(viewModel.isSavingWorkReport)
                        }
                    }
                }
                .padding(14)
            }
            .background(theme.background)
            .dismissKeyboardOnTapOutside()
            .navigationTitle(isReadOnly ? "Xem báo cáo công việc" : "Lập báo cáo công việc")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        KeyboardDismissal.dismiss()
                        viewModel.closeObligationEditor()
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Work Report Review Decision Sheet

struct WorkReportReviewDetailSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    let review: WorkReportReviewItem
    @Environment(\.dismiss) private var dismiss
    @State private var commentInput: String = ""

    var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(review.templateName)
                            .font(.title3.weight(.bold))
                        Text("Người nộp: \(review.reporterName) · Đơn vị: \(review.unitName)")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                        Text("Kỳ: \(WorkReportsContract.formatPeriodKeyVi(review.periodKey))")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(theme.primary)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .cornerRadius(12)

                    ForEach(review.sections) { sec in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(sec.title)
                                .font(.headline)
                            ForEach(sec.fields) { f in
                                let rawVal = review.formPayload[f.id] ?? ""
                                let displayVal: String = {
                                    if f.isBooleanType {
                                        let lower = rawVal.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                                        return ["true", "1", "yes", "có"].contains(lower) ? "Có / Đạt" : "Không"
                                    }
                                    return rawVal.isEmpty ? "—" : rawVal
                                }()
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(f.label)
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(SemanticColors.textSecondary)
                                    Text(displayVal)
                                        .font(.subheadline)
                                        .padding(8)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(Color(hex: 0xF8FAFC))
                                        .cornerRadius(8)
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Nhận xét phê duyệt / Lý do từ chối")
                            .font(.subheadline.weight(.bold))
                        TextField("Nhập ý kiến phản hồi...", text: $commentInput)
                            .submitLabel(.done)
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        HStack(spacing: 12) {
                            Button {
                                KeyboardDismissal.dismiss()
                                Task {
                                    await viewModel.submitWorkReportReviewDecision(
                                        review: review,
                                        decision: WorkReportsContract.decisionReject,
                                        comment: commentInput
                                    )
                                    dismiss()
                                }
                            } label: {
                                Text("Yêu cầu bổ sung")
                                    .font(.subheadline.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(SemanticColors.dangerBg)
                                    .foregroundColor(SemanticColors.dangerText)
                                    .cornerRadius(10)
                            }

                            Button {
                                KeyboardDismissal.dismiss()
                                Task {
                                    await viewModel.submitWorkReportReviewDecision(
                                        review: review,
                                        decision: WorkReportsContract.decisionApprove,
                                        comment: commentInput
                                    )
                                    dismiss()
                                }
                            } label: {
                                Text("Phê duyệt")
                                    .font(.subheadline.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(SemanticColors.successText)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)
                }
                .padding(14)
            }
            .background(theme.background)
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Duyệt báo cáo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        KeyboardDismissal.dismiss()
                        dismiss()
                    }
                }
            }
        }
    }
}
