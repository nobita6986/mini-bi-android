import SwiftUI

// MARK: - Work Reports Screen & Overdue Contract UI
// Mirrors Android's `ui/screens/WorkReportsScreen.kt` 100%

public struct WorkReportsScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Namespace private var workReportsTabNamespace
    @State private var visibleActionableCount: Int = 15
    @State private var visibleHistoryCount: Int = 15

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    private var hasLoadedAnyWorkReportsData: Bool {
        !viewModel.workReportObligations.isEmpty || !viewModel.workReportReviews.isEmpty
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
                            if !isSelected {
                                HapticsManager.selection()
                            }
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                                viewModel.selectedWorkReportsSubTab = tab
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Text(tab.titleVi)
                                    .font(.subheadline.weight(isSelected ? .bold : .medium))
                                if countBadge > 0 {
                                    Text("\(countBadge)")
                                        .font(.caption2.weight(.bold))
                                        .monospacedDigit()
                                        .contentTransition(.numericText())
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(isSelected ? Color.white.opacity(0.24) : theme.primaryLight)
                                        .foregroundColor(isSelected ? .white : theme.primary)
                                        .clipShape(Capsule())
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .frame(minHeight: 36)
                            .background {
                                if isSelected {
                                    Capsule()
                                        .fill(theme.primary)
                                        .matchedGeometryEffect(id: "work_reports_subtab_pill", in: workReportsTabNamespace)
                                } else {
                                    Capsule()
                                        .fill(SemanticColors.surfaceMuted)
                                }
                            }
                            .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
                            .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }

                    if viewModel.isLoadingWorkReports && hasLoadedAnyWorkReportsData {
                        HStack(spacing: 6) {
                            ProgressView()
                                .scaleEffect(0.75)
                            Text("Đồng bộ...")
                                .font(.caption2.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(SemanticColors.surfaceMuted)
                        .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color.white)

            Rectangle()
                .fill(SemanticColors.borderSubtle.opacity(0.65))
                .frame(height: 0.5)

            ScrollView {
                VStack(spacing: 12) {
                    if viewModel.isLoadingWorkReports && !hasLoadedAnyWorkReportsData {
                        workReportsSkeletonPlaceholder(theme: theme)
                    } else {
                        switch viewModel.selectedWorkReportsSubTab {
                        case .actionable:
                            actionableObligationsSection
                        case .history:
                            historyObligationsSection
                        case .reviewQueue:
                            reviewQueueSection
                        case .analytics:
                            analyticsSection
                        }
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.performRefreshable {
                    await viewModel.loadWorkReportsData(force: true)
                }
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
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(item: $viewModel.selectedReviewDetail) { review in
            WorkReportReviewDetailSheet(viewModel: viewModel, review: review)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
    }

    private func workReportsSkeletonPlaceholder(theme: MiniBiThemePalette) -> some View {
        VStack(spacing: 12) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Báo cáo kết quả công việc hàng ngày")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                        Text("Chưa nộp")
                            .font(.caption2.weight(.bold))
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(SemanticColors.surfaceMuted)
                            .clipShape(Capsule())
                    }
                    HStack {
                        Text("11/11/2026 · Hạn: 17:30 11/11/2026")
                            .font(.caption)
                        Spacer()
                    }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
            }
        }
        .redacted(reason: .placeholder)
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

    private var actionableObligationsSection: some View {
        let items = viewModel.actionableObligations
        let visibleItems = Array(items.prefix(visibleActionableCount))
        return VStack(spacing: 12) {
            obligationsListSection(
                items: visibleItems,
                emptyText: "Bạn đã hoàn thành tất cả báo cáo công việc cần nộp!"
            )
            if items.count > visibleActionableCount {
                HrpLoadMoreButton(
                    loadedCount: visibleItems.count,
                    totalCount: items.count,
                    isLoading: false,
                    theme: viewModel.selectedTheme
                ) {
                    visibleActionableCount += 15
                }
            }
        }
    }

    private var filteredHistoryObligations: [WorkReportObligationItem] {
        let all = viewModel.historyObligations
        guard viewModel.workReportHistoryFilterByDate else { return all }
        let targetDate = viewModel.workReportHistorySelectedDate
        return all.filter { ob in
            if ob.periodKey == targetDate || ob.periodKey.hasPrefix(targetDate) {
                return true
            }
            if let dueAt = ob.dueAt, dueAt.hasPrefix(targetDate) {
                return true
            }
            if let submittedAt = ob.submittedAt, submittedAt.hasPrefix(targetDate) {
                return true
            }
            return false
        }
    }

    private var historyObligationsSection: some View {
        let theme = viewModel.selectedTheme
        let items = filteredHistoryObligations
        let visibleItems = Array(items.prefix(visibleHistoryCount))

        return VStack(spacing: 12) {
            HStack(spacing: 8) {
                Text("Lọc lịch sử")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(SemanticColors.textSecondary)
                Spacer()
                HStack(spacing: 4) {
                    Button {
                        HapticsManager.selection()
                        visibleHistoryCount = 15
                        viewModel.setWorkReportHistoryFilterMode(byDate: false)
                    } label: {
                        Text("Tất cả kỳ")
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(!viewModel.workReportHistoryFilterByDate ? theme.primary : Color.clear)
                            .foregroundColor(!viewModel.workReportHistoryFilterByDate ? .white : SemanticColors.textSecondary)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)

                    Button {
                        HapticsManager.selection()
                        visibleHistoryCount = 15
                        viewModel.setWorkReportHistoryFilterMode(byDate: true)
                    } label: {
                        Text("Theo ngày")
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(viewModel.workReportHistoryFilterByDate ? theme.primary : Color.clear)
                            .foregroundColor(viewModel.workReportHistoryFilterByDate ? .white : SemanticColors.textSecondary)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(3)
                .background(SemanticColors.surfaceMuted)
                .clipShape(Capsule())
            }

            if viewModel.workReportHistoryFilterByDate {
                HrpDateNavigatorBar(
                    selectedDateIso: viewModel.workReportHistorySelectedDate,
                    theme: theme
                ) { dateIso in
                    visibleHistoryCount = 15
                    viewModel.selectWorkReportHistoryDate(dateIso)
                }
            }

            obligationsListSection(
                items: visibleItems,
                emptyText: viewModel.workReportHistoryFilterByDate
                    ? "Không có báo cáo công việc nào trong ngày đã chọn."
                    : "Chưa có lịch sử báo cáo công việc đã lưu / đã nộp / quá hạn."
            )

            if items.count > visibleHistoryCount {
                HrpLoadMoreButton(
                    loadedCount: visibleItems.count,
                    totalCount: items.count,
                    isLoading: false,
                    theme: theme
                ) {
                    visibleHistoryCount += 15
                }
            }
        }
    }

    private func obligationsListSection(items: [WorkReportObligationItem], emptyText: String) -> some View {
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
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(items) { ob in
                        let badgeText = WorkReportsContract.obligationBadgeDisplayLabel(ob)

                        Button {
                            HapticsManager.selection()
                            Task {
                                await viewModel.openObligationEditor(ob)
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack(alignment: .top, spacing: 8) {
                                    Text(ob.templateName)
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                        .multilineTextAlignment(.leading)
                                    Spacer(minLength: 6)
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

                                HStack(spacing: 6) {
                                    Image(systemName: "calendar")
                                        .font(.caption2)
                                        .foregroundColor(ob.isOverdue ? SemanticColors.dangerText : SemanticColors.textMuted)
                                    let dueDisplay: String = {
                                        if let dueAt = ob.dueAt, !dueAt.isEmpty {
                                            return " · Hạn: \(WorkReportsContract.formatIsoDateTimeVi(dueAt))"
                                        }
                                        return ""
                                    }()
                                    Text("\(WorkReportsContract.formatPeriodKeyVi(ob.periodKey))\(dueDisplay)")
                                        .font(.caption)
                                        .monospacedDigit()
                                        .foregroundColor(ob.isOverdue ? SemanticColors.dangerText : SemanticColors.textSecondary)
                                        .lineLimit(1)

                                    Spacer(minLength: 4)

                                    Image(systemName: "chevron.right")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(SemanticColors.textMuted)
                                }

                                if let comment = ob.reviewComment, !comment.isEmpty {
                                    HStack(spacing: 6) {
                                        Image(systemName: "bubble.left.and.exclamationmark.bubble.right.fill")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.warningText)
                                        Text(comment)
                                            .font(.caption2.weight(.medium))
                                            .foregroundColor(SemanticColors.warningText)
                                            .lineLimit(2)
                                    }
                                    .padding(8)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(SemanticColors.warningBg)
                                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                                }
                            }
                            .padding(14)
                            .hrpCardSurface(
                                cornerRadius: HrpDesignTokens.radiusCard,
                                borderColor: ob.isOverdue ? SemanticColors.dangerBorder : SemanticColors.borderSubtle.opacity(0.65)
                            )
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                    }
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
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.workReportReviews) { rev in
                        Button {
                            HapticsManager.selection()
                            viewModel.selectedReviewDetail = rev
                        } label: {
                            HStack(spacing: 10) {
                                HrpInitialsAvatar(
                                    fullName: rev.reporterName,
                                    tint: theme.primary,
                                    size: 38
                                )

                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(rev.templateName)
                                            .font(.subheadline.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .lineLimit(1)
                                        Spacer(minLength: 6)
                                        Text(WorkReportsContract.formatPeriodKeyVi(rev.periodKey))
                                            .font(.caption2.weight(.bold))
                                            .monospacedDigit()
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(theme.primaryLight)
                                            .foregroundColor(theme.primary)
                                            .clipShape(Capsule())
                                    }

                                    HStack(spacing: 6) {
                                        Text("\(rev.reporterName) · \(rev.unitName)")
                                            .font(.caption)
                                            .foregroundColor(SemanticColors.textSecondary)
                                            .lineLimit(1)
                                        Spacer(minLength: 4)
                                        Text(WorkReportsContract.formatIsoDateTimeVi(rev.submittedAt))
                                            .font(.caption2)
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textMuted)
                                            .lineLimit(1)
                                        Image(systemName: "chevron.right")
                                            .font(.caption2.weight(.semibold))
                                            .foregroundColor(SemanticColors.textMuted)
                                    }
                                }
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                    }
                }

                if viewModel.workReportReviewsHasMore {
                    HrpLoadMoreButton(
                        loadedCount: viewModel.workReportReviews.count,
                        totalCount: nil,
                        isLoading: viewModel.isLoadingMoreWorkReportReviews,
                        theme: theme
                    ) {
                        Task { await viewModel.loadMoreWorkReportReviews() }
                    }
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
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(row.groupLabel)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(SemanticColors.textPrimary)
                                Spacer()
                                Text(WorkReportsContract.formatPercentVi(row.completionRate))
                                    .font(.caption.weight(.bold))
                                    .monospacedDigit()
                                    .foregroundColor(theme.primary)
                            }
                            HrpSegmentedBar(
                                activeCount: row.submittedCount,
                                inactiveCount: row.overdueCount,
                                otherCount: max(0, row.totalObligations - row.submittedCount - row.overdueCount),
                                height: 6
                            )
                            Text("Đã nộp \(row.submittedCount)/\(row.totalObligations) · Quá hạn \(row.overdueCount)")
                                .font(.caption2)
                                .monospacedDigit()
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
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
                .monospacedDigit()
                .contentTransition(.numericText())
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
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
                        } else if currentOb.obligationStatus.uppercased() != WorkReportsContract.obligationStatusActive {
                            HStack(spacing: 8) {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(SemanticColors.warningText)
                                Text(WorkReportsContract.obligationInactiveMessageVi)
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.warningText)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.warningBg)
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
                                HapticsManager.lightImpact()
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
                                .frame(minHeight: 46)
                                .background(theme.primaryLight)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                            }
                            .buttonStyle(HrpPressableButtonStyle())
                            .disabled(viewModel.isSavingWorkReport)

                            Button {
                                HapticsManager.success()
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
                                .frame(minHeight: 46)
                                .background(theme.primary)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                            }
                            .buttonStyle(HrpPressableButtonStyle())
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
                            .monospacedDigit()
                            .foregroundColor(theme.primary)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

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
                                        .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusXS)
                                }
                            }
                        }
                        .padding(14)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Nhận xét phê duyệt / Lý do từ chối")
                            .font(.subheadline.weight(.bold))
                        TextField("Nhập ý kiến phản hồi...", text: $commentInput)
                            .submitLabel(.done)
                            .padding(10)
                            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusXS)

                        HStack(spacing: 12) {
                            Button {
                                HapticsManager.warning()
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
                                    .frame(maxWidth: .infinity, minHeight: 46)
                                    .background(SemanticColors.dangerBg)
                                    .foregroundColor(SemanticColors.dangerText)
                                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                            }
                            .buttonStyle(HrpPressableButtonStyle())

                            Button {
                                HapticsManager.success()
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
                                    .frame(maxWidth: .infinity, minHeight: 46)
                                    .background(SemanticColors.successText)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                            }
                            .buttonStyle(HrpPressableButtonStyle())
                        }
                    }
                    .padding(14)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
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
