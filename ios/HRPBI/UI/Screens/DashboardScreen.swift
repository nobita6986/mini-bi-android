import SwiftUI

// MARK: - Dashboard Screen & CCCD Review Section
// Mirrors Android's `ui/screens/DashboardScreen.kt` & `ui/screens/CccdReviewSection.kt` 100%

public struct DashboardScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Namespace private var dashboardTabNamespace

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    private var hasLoadedAnyOverviewData: Bool {
        viewModel.hasLoadedDashboardInSession
            || viewModel.generalOverview.kpis.totalHeadcount > 0
            || !viewModel.generalOverview.projects.isEmpty
    }

    public var body: some View {
        let theme = viewModel.selectedTheme

        VStack(spacing: 0) {
            // Sub-tab selector
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(DashboardSubTab.allCases) { tab in
                        let isSelected = viewModel.selectedDashboardSubTab == tab
                        Button {
                            KeyboardDismissal.dismiss()
                            if !isSelected {
                                HapticsManager.selection()
                            }
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                                viewModel.selectedDashboardSubTab = tab
                            }
                        } label: {
                            Text(tab.titleVi)
                                .font(.subheadline.weight(isSelected ? .bold : .medium))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .frame(minHeight: 36)
                                .background {
                                    if isSelected {
                                        Capsule()
                                            .fill(theme.primary)
                                            .matchedGeometryEffect(id: "dashboard_subtab_pill", in: dashboardTabNamespace)
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

                    if viewModel.isLoadingDashboard && hasLoadedAnyOverviewData {
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
                        .transition(.opacity)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color.white)

            Rectangle()
                .fill(SemanticColors.borderSubtle.opacity(0.65))
                .frame(height: 0.5)

            // Date & Scope Filter Bar (for Overview / Deep / Team tabs)
            if viewModel.selectedDashboardSubTab != .cccdReview && viewModel.selectedDashboardSubTab != .duplicateCccd {
                dateAndScopeFilterBar
                Rectangle()
                    .fill(SemanticColors.borderSubtle.opacity(0.65))
                    .frame(height: 0.5)
            }

            // Tab Content
            ScrollView {
                VStack(spacing: 14) {
                    if !viewModel.dashboardErrorMessage.isEmpty && !viewModel.isLoadingDashboard {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(SemanticColors.dangerText)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Không thể tải dữ liệu báo cáo")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(SemanticColors.dangerText)
                                Text(viewModel.dashboardErrorMessage)
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            Spacer()
                            Button("Thử lại") {
                                HapticsManager.lightImpact()
                                Task { await viewModel.loadDashboardData(includeCccdTabs: false) }
                            }
                            .font(.caption.weight(.bold))
                            .foregroundColor(theme.primary)
                            .frame(minHeight: 36)
                            .contentShape(Rectangle())
                        }
                        .padding(12)
                        .background(SemanticColors.dangerBg)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                    }

                    if viewModel.isLoadingDashboard && !hasLoadedAnyOverviewData {
                        dashboardSkeletonPlaceholder(theme: theme)
                    } else {
                        switch viewModel.selectedDashboardSubTab {
                        case .overview:
                            overviewSection
                        case .deepMetrics:
                            deepMetricsSection
                        case .teamReport:
                            teamReportSection
                        case .duplicateCccd:
                            duplicateCccdSection
                        case .cccdReview:
                            CccdReviewSectionView(viewModel: viewModel)
                        }
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.loadDashboardData()
            }
        }
        .background(theme.background)
        .dismissKeyboardOnTapOutside()
        .onAppear {
            if viewModel.isAuthenticated
                && !viewModel.isLoadingDashboard
                && !viewModel.hasLoadedDashboardInSession {
                Task { await viewModel.loadDashboardData(includeCccdTabs: false) }
            }
        }
        .sheet(item: $viewModel.selectedCccdReviewItem) { item in
            CccdReviewDetailSheet(viewModel: viewModel, item: item)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
    }

    // MARK: - Skeleton Placeholder (Perceived Performance Law #1)

    private func dashboardSkeletonPlaceholder(theme: MiniBiThemePalette) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                kpiCard(title: "Tổng nhân sự", value: "1.280", subtitle: "Tuyển mới: +48", accentColor: theme.primary)
                kpiCard(title: "Đang làm việc", value: "1.045", subtitle: "Giữ chân: 81,6%", accentColor: SemanticColors.successText)
                kpiCard(title: "Đã nghỉ việc", value: "185", subtitle: "Nghỉ trong kỳ: 12", accentColor: SemanticColors.dangerText)
                kpiCard(title: "Phỏng vấn / Chờ nhận", value: "50", subtitle: "Biến động: 14,4%", accentColor: SemanticColors.warningText)
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Thống kê nhân sự theo Dự án")
                        .font(.headline)
                    Spacer()
                    Text("6 dự án")
                        .font(.caption)
                }
                ForEach(0..<3, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Dự án Sản xuất Điện tử")
                                .font(.subheadline.weight(.semibold))
                            Spacer()
                            Text("Tổng: 320")
                                .font(.caption.weight(.bold))
                        }
                        HStack(spacing: 12) {
                            Text("Đang làm: 280")
                                .font(.caption)
                            Text("Đã nghỉ: 40")
                                .font(.caption)
                        }
                    }
                    .padding(10)
                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                }
            }
            .padding(14)
            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
        }
        .redacted(reason: .placeholder)
    }

    // MARK: - Date & Scope Filter Bar

    private var dateAndScopeFilterBar: some View {
        let theme = viewModel.selectedTheme
        return VStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(DateFilterPreset.allCases) { preset in
                        let selected = viewModel.selectedDatePreset == preset
                        Button {
                            HapticsManager.selection()
                            viewModel.applyDatePreset(preset)
                        } label: {
                            Text(preset.titleVi)
                                .font(.caption.weight(selected ? .bold : .medium))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .frame(minHeight: 32)
                                .background(selected ? theme.primaryLight : SemanticColors.surfaceMuted)
                                .foregroundColor(selected ? theme.primary : SemanticColors.textSecondary)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }

                    Divider().frame(height: 18)

                    ForEach([("all", "Toàn công ty"), ("team", "Theo nhóm"), ("own", "Cá nhân")], id: \.0) { scopeCode, label in
                        let selected = viewModel.dashboardScope == scopeCode
                        Button {
                            HapticsManager.selection()
                            viewModel.dashboardScope = scopeCode
                            Task { await viewModel.loadDashboardData() }
                        } label: {
                            Text(label)
                                .font(.caption.weight(selected ? .bold : .medium))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .frame(minHeight: 32)
                                .background(selected ? theme.primary : SemanticColors.surfaceMuted)
                                .foregroundColor(selected ? .white : SemanticColors.textSecondary)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
            }
        }
        .padding(.vertical, 8)
        .background(Color.white)
    }

    // MARK: - 1. Overview Section

    private var overviewSection: some View {
        let kpis = viewModel.generalOverview.kpis
        let theme = viewModel.selectedTheme
        let hasDateFilter = viewModel.selectedDatePreset != .all

        return VStack(alignment: .leading, spacing: 14) {
            // KPI Cards Grid
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                kpiCard(
                    title: hasDateFilter ? "Tuyển mới trong kỳ" : "Tổng nhân sự",
                    value: "\(kpis.totalHeadcount)",
                    subtitle: hasDateFilter ? "Kỳ: \(viewModel.selectedDatePreset.titleVi)" : "Tuyển mới: +\(kpis.newCount)",
                    accentColor: theme.primary
                )
                kpiCard(
                    title: hasDateFilter ? "Vào làm trong kỳ" : "Đang làm việc",
                    value: "\(kpis.activeCount)",
                    subtitle: "Giữ chân: \(WorkReportsContract.formatPercentVi(kpis.retentionRate))",
                    accentColor: SemanticColors.successText
                )
                kpiCard(
                    title: hasDateFilter ? "Nghỉ trong kỳ" : "Đã nghỉ việc",
                    value: "\(kpis.inactiveCount)",
                    subtitle: "Nghỉ trong kỳ: \(kpis.resignedInPeriodCount)",
                    accentColor: SemanticColors.dangerText
                )
                kpiCard(
                    title: "Phỏng vấn / Chờ nhận",
                    value: "\(kpis.interviewCount)",
                    subtitle: "Biến động: \(WorkReportsContract.formatPercentVi(kpis.turnoverRate))",
                    accentColor: SemanticColors.warningText
                )
            }

            // Projects Breakdown Card
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(hasDateFilter ? "Thống kê Dự án (\(viewModel.selectedDatePreset.titleVi))" : "Thống kê nhân sự theo Dự án")
                        .font(.headline)
                        .foregroundColor(SemanticColors.textPrimary)
                    Spacer()
                    Text("\(max(viewModel.generalOverview.projectsTotal, viewModel.generalOverview.projects.count)) dự án")
                        .font(.caption)
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .foregroundColor(SemanticColors.textMuted)
                }

                if viewModel.generalOverview.projects.isEmpty {
                    Text("Chưa có dữ liệu dự án trong kỳ đã chọn.")
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textMuted)
                        .padding(.vertical, 8)
                } else {
                    LazyVStack(spacing: 8) {
                        ForEach(viewModel.generalOverview.projects) { proj in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(proj.projectName)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                    Spacer()
                                    Text("Tổng: \(proj.totalCount)")
                                        .font(.caption.weight(.bold))
                                        .monospacedDigit()
                                        .contentTransition(.numericText())
                                        .foregroundColor(theme.primary)
                                }
                                HStack(spacing: 12) {
                                    Label("Đang làm: \(proj.activeCount)", systemImage: "checkmark.circle.fill")
                                        .font(.caption)
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.successText)
                                    Label("Đã nghỉ: \(proj.inactiveCount)", systemImage: "xmark.circle")
                                        .font(.caption)
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.dangerText)
                                    if proj.newCount > 0 {
                                        Text("Mới: +\(proj.newCount)")
                                            .font(.caption.weight(.semibold))
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.infoText)
                                    }
                                }
                            }
                            .padding(10)
                            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                        }
                    }
                }
            }
            .padding(14)
            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

            // Recruiters & Managers Performance
            if !viewModel.generalOverview.recruiters.isEmpty {
                personPerformanceCard(
                    title: hasDateFilter ? "Hiệu suất Người tuyển dụng (\(viewModel.selectedDatePreset.titleVi))" : "Hiệu suất Người tuyển dụng",
                    rows: viewModel.generalOverview.recruiters
                )
            }
            if !viewModel.generalOverview.managers.isEmpty {
                personPerformanceCard(
                    title: hasDateFilter ? "Hiệu suất Theo Nhóm (\(viewModel.selectedDatePreset.titleVi))" : "Hiệu suất Quản lý dự án",
                    rows: viewModel.generalOverview.managers
                )
            }
        }
    }

    // MARK: - 2. Workforce Deep Metrics Section

    private var deepMetricsSection: some View {
        let deep = viewModel.workforceDeepMetrics
        let hasDateFilter = viewModel.selectedDatePreset != .all
        let periodSuffix = hasDateFilter ? " (\(viewModel.selectedDatePreset.titleVi))" : ""
        return VStack(alignment: .leading, spacing: 14) {
            bucketDistributionCard(title: "Phân bổ Thâm niên & Giữ chân\(periodSuffix)", buckets: deep.tenureBuckets)
            bucketDistributionCard(title: "Cơ cấu Theo Nhóm Tuyển dụng\(periodSuffix)", buckets: deep.ageBuckets)
            bucketDistributionCard(title: "Cơ cấu Nguồn Tuyển dụng (HRP / Vendor)\(periodSuffix)", buckets: deep.genderBuckets)
            bucketDistributionCard(title: "Biến động & Trạng thái Nhân sự\(periodSuffix)", buckets: deep.contractBuckets)
            bucketDistributionCard(title: "Phân tích Lý do nghỉ việc\(periodSuffix)", buckets: deep.resignReasonBuckets)
        }
    }

    // MARK: - 3. Team Report Section

    private var teamReportSection: some View {
        let teams = viewModel.teamReport.teams
        let theme = viewModel.selectedTheme

        return VStack(alignment: .leading, spacing: 12) {
            if teams.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "person.3.fill")
                        .font(.largeTitle)
                        .foregroundColor(SemanticColors.textMuted)
                    Text("Chưa có dữ liệu báo cáo theo nhóm.")
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textSecondary)
                }
                .frame(maxWidth: .infinity)
                .padding(24)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(teams) { team in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(team.teamName)
                                        .font(.headline)
                                        .foregroundColor(SemanticColors.textPrimary)
                                    if !team.leaderName.isEmpty {
                                        Text("Trưởng nhóm: \(team.leaderName) · \(team.memberCount) thành viên")
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textSecondary)
                                    }
                                }
                                Spacer()
                                Text(WorkReportsContract.formatPercentVi(team.activeRatePct))
                                    .font(.caption.weight(.bold))
                                    .monospacedDigit()
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(theme.primaryLight)
                                    .foregroundColor(theme.primary)
                                    .clipShape(Capsule())
                            }

                            HStack(spacing: 14) {
                                Text("Tổng NLĐ: \(team.totalWorkers)")
                                    .font(.caption.weight(.semibold))
                                    .monospacedDigit()
                                Text("Đang làm: \(team.activeWorkers)")
                                    .font(.caption)
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.successText)
                                Text("Nghỉ: \(team.inactiveWorkers)")
                                    .font(.caption)
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.dangerText)
                                Text("Mới: +\(team.newWorkers)")
                                    .font(.caption)
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.infoText)
                            }

                            if !team.members.isEmpty {
                                Divider()
                                ForEach(team.members) { member in
                                    HStack {
                                        Text(member.name)
                                            .font(.caption.weight(.medium))
                                        Spacer()
                                        Text("Đang làm \(member.activeCount)/\(member.totalCount)")
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textSecondary)
                                    }
                                }
                            }
                        }
                        .padding(14)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    }
                }
            }
        }
    }

    // MARK: - 4. Duplicate CCCD Section

    private var duplicateCccdSection: some View {
        let dupData = viewModel.duplicateCccdReport
        let theme = viewModel.selectedTheme

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(SemanticColors.textMuted)
                TextField("Tìm theo số CCCD hoặc họ tên...", text: $viewModel.duplicateSearchQuery)
                    .submitLabel(.search)
                    .foregroundColor(SemanticColors.textPrimary)
                    .onSubmit {
                        KeyboardDismissal.dismiss()
                        Task { await viewModel.loadDashboardData() }
                    }
                Button("Tìm") {
                    HapticsManager.lightImpact()
                    KeyboardDismissal.dismiss()
                    Task { await viewModel.loadDashboardData() }
                }
                .font(.caption.weight(.bold))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .frame(minHeight: 34)
                .background(theme.primary)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
            }
            .padding(10)
            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusMD)

            if dupData.groups.isEmpty {
                Text("Không phát hiện hồ sơ CCCD trùng lặp.")
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(dupData.groups) { group in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("CCCD: \(group.cccd)")
                                    .font(.subheadline.weight(.bold))
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textPrimary)
                                Spacer()
                                Text("\(group.workerCount) hồ sơ · \(group.activeCount) đang làm")
                                    .font(.caption.weight(.semibold))
                                    .monospacedDigit()
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(SemanticColors.warningBg)
                                    .foregroundColor(SemanticColors.warningText)
                                    .clipShape(Capsule())
                            }

                            ForEach(group.episodes) { ep in
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(WorkerItem.isUuidLike(ep.maNld) || ep.maNld.isEmpty ? ep.hoTen : "\(ep.hoTen) (\(ep.maNld))")
                                            .font(.caption.weight(.semibold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                        Text("Dự án: \(ep.duAn) · QL: \(ep.quanLy) · Tuyển: \(ep.nguoiTuyen)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textSecondary)
                                        Text("Trạng thái: \(ep.trangThai) · Ngày vào: \(ep.ngayVaoLam)")
                                            .font(.caption2)
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textMuted)
                                    }
                                    Spacer()
                                    if ep.canSelfWithdraw {
                                        Button {
                                            HapticsManager.warning()
                                            Task {
                                                await viewModel.withdrawDuplicateCccdEpisode(ep, cccd: group.cccd)
                                            }
                                        } label: {
                                            Text("Tự rút hồ sơ")
                                                .font(.caption2.weight(.bold))
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 5)
                                                .frame(minHeight: 32)
                                                .background(SemanticColors.dangerBg)
                                                .foregroundColor(SemanticColors.dangerText)
                                                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .padding(8)
                                .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusXS)
                            }
                        }
                        .padding(14)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    }
                }
            }
        }
    }

    // MARK: - Helper Cards

    private func kpiCard(title: String, value: String, subtitle: String, accentColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundColor(SemanticColors.textSecondary)
            Text(value)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())
                .animation(.default, value: value)
                .foregroundColor(accentColor)
            Text(subtitle)
                .font(.caption2)
                .monospacedDigit()
                .contentTransition(.numericText())
                .foregroundColor(SemanticColors.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    private func personPerformanceCard(title: String, rows: [PersonPerformanceRow]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .foregroundColor(SemanticColors.textPrimary)

            ForEach(rows.prefix(10)) { row in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.name)
                            .font(.subheadline.weight(.medium))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Tổng: \(row.totalCount) · Đang làm: \(row.activeCount) · Nghỉ: \(row.inactiveCount)")
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                    Spacer()
                    Text(WorkReportsContract.formatPercentVi(row.activeRatePct))
                        .font(.caption.weight(.bold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .foregroundColor(SemanticColors.successText)
                }
                .padding(.vertical, 4)
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    private func bucketDistributionCard(title: String, buckets: [DistributionBucket]) -> some View {
        let theme = viewModel.selectedTheme
        return VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .foregroundColor(SemanticColors.textPrimary)

            if buckets.isEmpty {
                Text("Chưa có dữ liệu thống kê.")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textMuted)
            } else {
                ForEach(buckets) { b in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(b.label)
                                .font(.caption.weight(.medium))
                            Spacer()
                            Text("\(b.count) (\(WorkReportsContract.formatPercentVi(b.percentage)))")
                                .font(.caption.weight(.semibold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .foregroundColor(theme.primary)
                        }
                        GeometryReader { geo in
                            let ratio = min(1.0, max(0.0, b.percentage > 1.0 ? b.percentage / 100.0 : b.percentage))
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(Color(hex: 0xE2E8F0))
                                    .frame(height: 6)
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(theme.primary)
                                    .frame(width: geo.size.width * ratio, height: 6)
                            }
                        }
                        .frame(height: 6)
                    }
                }
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }
}

// MARK: - CCCD Review Section (`Chuẩn hoá thông tin NLĐ`)

public struct CccdReviewSectionView: View {
    @ObservedObject var viewModel: MiniBiViewModel

    private let statusFilters: [(String, String)] = [
        ("ALL", "Tất cả"),
        ("MISMATCH", "Lệch thông tin"),
        ("PENDING_QR", "Đang chờ QR"),
        ("UNREADABLE_QR", "QR mờ/lỗi"),
        ("MISSING_QR", "Thiếu ảnh"),
        ("MATCHED", "Đã khớp")
    ]

    public var body: some View {
        let theme = viewModel.selectedTheme

        VStack(alignment: .leading, spacing: 12) {
            // Status filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(statusFilters, id: \.0) { code, label in
                        let selected = viewModel.cccdReviewFilterStatus == code
                        Button {
                            HapticsManager.selection()
                            viewModel.cccdReviewFilterStatus = code
                            Task { await viewModel.loadCccdReviewItems() }
                        } label: {
                            Text(label)
                                .font(.caption.weight(selected ? .bold : .medium))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .frame(minHeight: 32)
                                .background(selected ? theme.primary : Color.white)
                                .foregroundColor(selected ? .white : SemanticColors.textSecondary)
                                .clipShape(Capsule())
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(SemanticColors.textMuted)
                TextField("Tìm mã NLĐ, họ tên, số CCCD...", text: $viewModel.cccdReviewSearchQuery)
                    .submitLabel(.search)
                    .foregroundColor(SemanticColors.textPrimary)
                    .onSubmit {
                        KeyboardDismissal.dismiss()
                        Task { await viewModel.loadCccdReviewItems() }
                    }
                Button("Lọc") {
                    HapticsManager.lightImpact()
                    KeyboardDismissal.dismiss()
                    Task { await viewModel.loadCccdReviewItems() }
                }
                .font(.caption.weight(.bold))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .frame(minHeight: 34)
                .background(theme.primary)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
            }
            .padding(10)
            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusMD)

            if viewModel.cccdReviewItems.isEmpty {
                Text("Không có hồ sơ CCCD nào trong danh sách lọc.")
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.cccdReviewItems) { item in
                        Button {
                            HapticsManager.selection()
                            viewModel.selectedCccdReviewItem = item
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(WorkerItem.isUuidLike(item.maNld) || item.maNld.isEmpty ? item.hoTen : "\(item.hoTen) (\(item.maNld))")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                    Spacer()
                                    Text(DirectEntryContract.reviewStatusDisplayLabel(item.reviewStatus))
                                        .font(.caption2.weight(.bold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(statusBgColor(item.reviewStatus))
                                        .foregroundColor(statusTextColor(item.reviewStatus))
                                        .clipShape(Capsule())
                                }

                                Text("Dự án: \(item.duAn) · Quản lý: \(item.quanLy)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)

                                HStack(spacing: 12) {
                                    Text("CCCD nhập: \(item.enteredCccd.isEmpty ? "—" : item.enteredCccd)")
                                        .font(.caption)
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.textPrimary)
                                    Text("CCCD QR: \(item.qrCccd.isEmpty ? "—" : item.qrCccd)")
                                        .font(.caption.weight(.semibold))
                                        .monospacedDigit()
                                        .foregroundColor(theme.primary)
                                }
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                    }
                }
            }
        }
    }

    private func statusBgColor(_ status: String) -> Color {
        switch status.uppercased() {
        case DirectEntryContract.reviewStatusMatched:
            return SemanticColors.successBg
        case DirectEntryContract.reviewStatusMismatch:
            return SemanticColors.dangerBg
        default:
            return SemanticColors.warningBg
        }
    }

    private func statusTextColor(_ status: String) -> Color {
        switch status.uppercased() {
        case DirectEntryContract.reviewStatusMatched:
            return SemanticColors.successText
        case DirectEntryContract.reviewStatusMismatch:
            return SemanticColors.dangerText
        default:
            return SemanticColors.warningText
        }
    }
}

// MARK: - CCCD Review Detail Sheet

public struct CccdReviewDetailSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    let item: CccdReviewItem
    @Environment(\.dismiss) private var dismiss
    @State private var correctedCccdInput: String = ""
    @State private var reviewNoteInput: String = ""

    public var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Header info
                    VStack(alignment: .leading, spacing: 6) {
                        Text(WorkerItem.isUuidLike(item.maNld) || item.maNld.isEmpty ? item.hoTen : "\(item.hoTen) (\(item.maNld))")
                            .font(.title3.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Dự án: \(item.duAn) · Quản lý: \(item.quanLy) · Tuyển: \(item.nguoiTuyen)")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                        Text("Trạng thái: \(DirectEntryContract.reviewStatusDisplayLabel(item.reviewStatus))")
                            .font(.caption.weight(.bold))
                            .foregroundColor(theme.primary)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .cornerRadius(12)

                    // Front / Back CCCD Preview Links
                    if item.frontPreviewUrl != nil || item.backPreviewUrl != nil {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Ảnh CCCD Mặt trước / Mặt sau")
                                .font(.subheadline.weight(.bold))
                            HStack(spacing: 12) {
                                if let frontUrlStr = item.frontPreviewUrl, let url = URL(string: frontUrlStr) {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let image):
                                            image.resizable().scaledToFit()
                                        default:
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 8).fill(Color(hex: 0xF1F5F9))
                                                Text("Mặt trước CCCD").font(.caption)
                                            }
                                        }
                                    }
                                    .frame(height: 130)
                                    .cornerRadius(8)
                                }
                                if let backUrlStr = item.backPreviewUrl, let url = URL(string: backUrlStr) {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let image):
                                            image.resizable().scaledToFit()
                                        default:
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 8).fill(Color(hex: 0xF1F5F9))
                                                Text("Mặt sau CCCD").font(.caption)
                                            }
                                        }
                                    }
                                    .frame(height: 130)
                                    .cornerRadius(8)
                                }
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white)
                        .cornerRadius(12)
                    }

                    // Comparison Table
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Đối chiếu dữ liệu nhập & dữ liệu QR CCCD")
                            .font(.subheadline.weight(.bold))

                        ForEach(item.comparisons) { row in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(row.label)
                                        .font(.caption.weight(.bold))
                                    Spacer()
                                    Text(row.status)
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(row.status == "MATCH" ? SemanticColors.successText : SemanticColors.warningText)
                                }
                                HStack {
                                    Text("Đã nhập: \(row.enteredValue.isEmpty ? "—" : row.enteredValue)")
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textSecondary)
                                    Spacer()
                                    Text("Từ QR: \(row.qrValue.isEmpty ? "—" : row.qrValue)")
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(theme.primary)
                                }
                            }
                            .padding(8)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)

                    // Actions: Standardize from QR or Update Entered CCCD
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Thao tác phê duyệt / chuẩn hoá")
                            .font(.subheadline.weight(.bold))

                        TextField("Số CCCD chuẩn (12 chữ số)...", text: $correctedCccdInput)
                            .submitLabel(.done)
                            .foregroundColor(SemanticColors.textPrimary)
                            .keyboardType(.numberPad)
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        TextField("Ghi chú duyệt / lý do...", text: $reviewNoteInput)
                            .submitLabel(.done)
                            .foregroundColor(SemanticColors.textPrimary)
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        HStack(spacing: 10) {
                            Button {
                                KeyboardDismissal.dismiss()
                                Task {
                                    let ok = await viewModel.resolveCccdReview(
                                        entryId: item.entryId,
                                        action: DirectEntryContract.resolveActionStandardizeFromQr,
                                        note: reviewNoteInput
                                    )
                                    if ok {
                                        dismiss()
                                    }
                                }
                            } label: {
                                Text("Chuẩn hoá theo QR")
                                    .font(.caption.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(theme.primary)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }

                            Button {
                                KeyboardDismissal.dismiss()
                                Task {
                                    let ok = await viewModel.resolveCccdReview(
                                        entryId: item.entryId,
                                        action: DirectEntryContract.resolveActionUpdateEnteredCccd,
                                        correctedCccd: correctedCccdInput.isEmpty ? item.enteredCccd : correctedCccdInput,
                                        note: reviewNoteInput
                                    )
                                    if ok {
                                        dismiss()
                                    }
                                }
                            } label: {
                                Text("Cập nhật CCCD nhập")
                                    .font(.caption.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(SemanticColors.successText)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }
                        }

                        Button {
                            KeyboardDismissal.dismiss()
                            Task {
                                await viewModel.retryCccdQrExtraction(entryId: item.entryId)
                            }
                        } label: {
                            HStack {
                                Image(systemName: "qrcode.viewfinder")
                                Text("Quét / Trích xuất lại QR trên máy chủ")
                            }
                            .font(.caption.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(theme.primaryLight)
                            .foregroundColor(theme.primary)
                            .cornerRadius(10)
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
            .navigationTitle("Chi tiết duyệt CCCD")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        KeyboardDismissal.dismiss()
                        dismiss()
                    }
                }
            }
            .onAppear {
                correctedCccdInput = item.enteredCccd
            }
        }
    }
}
