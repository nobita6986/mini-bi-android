import SwiftUI

// MARK: - Dashboard Screen & CCCD Review Section
// Mirrors Android's `ui/screens/DashboardScreen.kt` & `ui/screens/CccdReviewSection.kt` 100%

public struct DashboardScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Namespace private var dashboardTabNamespace
    @State private var visibleProjectsCount: Int = 10

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    private var hasLoadedAnyOverviewData: Bool {
        viewModel.hasLoadedDashboardInSession
    }

    public var body: some View {
        let theme = viewModel.selectedTheme
        let activeTab: DashboardSubTab = DashboardSubTab.primaryTabs.contains(viewModel.selectedDashboardSubTab)
            ? viewModel.selectedDashboardSubTab
            : .overview

        VStack(spacing: 0) {
            // 1-row 50/50 Segmented Pill Bar (`Tổng quát` & `Theo nhóm`)
            HStack(spacing: 4) {
                ForEach(DashboardSubTab.primaryTabs) { tab in
                    let isSelected = activeTab == tab
                    Button {
                        KeyboardDismissal.dismiss()
                        if !isSelected {
                            HapticsManager.selection()
                        }
                        withAnimation(.spring(response: 0.26, dampingFraction: 0.84)) {
                            viewModel.selectedDashboardSubTab = tab
                        }
                    } label: {
                        Text(tab.titleVi)
                            .font(.subheadline.weight(isSelected ? .semibold : .medium))
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background {
                                if isSelected {
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .fill(Color.white)
                                        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
                                        .matchedGeometryEffect(id: "dashboard_subtab_pill", in: dashboardTabNamespace)
                                } else {
                                    Color.clear
                                }
                            }
                            .foregroundColor(isSelected ? theme.primary : SemanticColors.textSecondary)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .frame(height: 36)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(hex: 0xF1F5F9))
            )
            .padding(.horizontal, 14)
            .padding(.top, 8)
            .padding(.bottom, 6)
            .background(Color.white)

            // Date & Scope Filter Bar
            dateAndScopeFilterBar

            Rectangle()
                .fill(SemanticColors.borderSubtle.opacity(0.65))
                .frame(height: 0.5)

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
                                viewModel.resetDashboardDisplayForFilterChange()
                                Task { await viewModel.loadDashboardData(includeCccdTabs: false, force: true) }
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
                        dashboardSkeletonPlaceholder(theme: theme, activeTab: activeTab)
                    } else if activeTab == .teamReport {
                        teamReportSection
                    } else {
                        overviewSection
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.performRefreshable {
                    await viewModel.loadDashboardData(force: true)
                }
            }
        }
        .background(theme.background)
        .dismissKeyboardOnTapOutside()
        .onAppear {
            viewModel.normalizeSelectedDashboardSubTab()
            if viewModel.dashboardScope != "all" {
                viewModel.dashboardScope = "all"
            }
            if viewModel.isAuthenticated
                && !viewModel.isLoadingDashboard
                && !viewModel.hasLoadedDashboardInSession {
                Task { await viewModel.loadDashboardData(includeCccdTabs: false) }
            }
        }
    }

    // MARK: - Skeleton Placeholder (Perceived Performance Law #1)

    private func dashboardSkeletonPlaceholder(theme: MiniBiThemePalette, activeTab: DashboardSubTab) -> some View {
        Group {
            if activeTab == .teamReport {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(0..<3, id: \.self) { _ in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Nhóm Tuyển dụng Miền Bắc")
                                        .font(.headline)
                                    Text("Trưởng nhóm: Nguyễn Văn A · 8 thành viên")
                                        .font(.caption)
                                }
                                Spacer()
                                Text("84,5%")
                                    .font(.caption.weight(.bold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(theme.primaryLight)
                                    .clipShape(Capsule())
                            }
                            HrpSegmentedBar(activeCount: 160, inactiveCount: 30)
                            HStack(spacing: 14) {
                                Text("160 đang làm")
                                    .font(.caption2)
                                Text("30 nghỉ")
                                    .font(.caption2)
                                Spacer()
                                Text("Tổng: 190")
                                    .font(.caption2.weight(.bold))
                            }
                        }
                        .padding(14)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        kpiCard(title: "Tổng nhân sự", value: "1.280", subtitle: "+48 mới", iconName: "person.3.fill", accentColor: theme.primary)
                        kpiCard(title: "Đang làm việc", value: "1.045", subtitle: "Giữ chân 81,6%", iconName: "checkmark.seal.fill", accentColor: SemanticColors.successText)
                        kpiCard(title: "Đã nghỉ việc", value: "185", subtitle: "Trong kỳ: 12", iconName: "person.crop.circle.badge.minus", accentColor: SemanticColors.dangerText)
                        kpiCard(title: "Phỏng vấn / Chờ nhận", value: "50", subtitle: "Biến động 14,4%", iconName: "clock.badge.questionmark", accentColor: SemanticColors.warningText)
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
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text("Dự án Sản xuất Điện tử")
                                        .font(.subheadline.weight(.semibold))
                                    Spacer()
                                    Text("320")
                                        .font(.subheadline.weight(.bold))
                                }
                                HrpSegmentedBar(activeCount: 280, inactiveCount: 40)
                                HStack(spacing: 12) {
                                    Text("280 đang làm")
                                        .font(.caption2)
                                    Text("40 nghỉ")
                                        .font(.caption2)
                                }
                            }
                            .padding(10)
                            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                        }
                    }
                    .padding(14)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                }
            }
        }
        .redacted(reason: .placeholder)
    }

    // MARK: - Date Filter Bar (Full-Width Period Chips)

    private var dateAndScopeFilterBar: some View {
        let theme = viewModel.selectedTheme
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(DateFilterPreset.allCases) { preset in
                    let selected = viewModel.selectedDatePreset == preset
                    Button {
                        KeyboardDismissal.dismiss()
                        HapticsManager.selection()
                        visibleProjectsCount = 10
                        viewModel.applyDatePreset(preset, reload: true)
                    } label: {
                        Text(preset.titleVi)
                            .font(.caption.weight(selected ? .bold : .medium))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .frame(minHeight: 32)
                            .background(selected ? theme.primaryLight : SemanticColors.surfaceMuted)
                            .foregroundColor(selected ? theme.primary : SemanticColors.textSecondary)
                            .overlay(
                                Capsule()
                                    .stroke(selected ? theme.primary.opacity(0.35) : Color.clear, lineWidth: 1)
                            )
                            .clipShape(Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
        }
        .padding(.vertical, 8)
        .background(Color.white)
    }

    // MARK: - 1. Overview Section

    private var overviewSection: some View {
        let kpis = viewModel.generalOverview.kpis
        let theme = viewModel.selectedTheme
        let hasDateFilter = viewModel.selectedDatePreset != .all
        let allProjects = viewModel.generalOverview.projects
        let visibleProjects = Array(allProjects.prefix(visibleProjectsCount))

        return VStack(alignment: .leading, spacing: 14) {
            // KPI Cards Grid (2x2 with Tinted SF Symbol Circle + 27pt Heavy Numbers)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                kpiCard(
                    title: hasDateFilter ? "Tuyển mới trong kỳ" : "Tổng nhân sự",
                    value: "\(kpis.totalHeadcount)",
                    subtitle: hasDateFilter ? "Kỳ: \(viewModel.selectedDatePreset.titleVi)" : "+\(kpis.newCount) mới",
                    iconName: "person.3.fill",
                    accentColor: theme.primary
                )
                kpiCard(
                    title: hasDateFilter ? "Vào làm trong kỳ" : "Đang làm việc",
                    value: "\(kpis.activeCount)",
                    subtitle: "Giữ chân \(WorkReportsContract.formatPercentVi(kpis.retentionRate))",
                    iconName: "checkmark.seal.fill",
                    accentColor: SemanticColors.successText
                )
                kpiCard(
                    title: hasDateFilter ? "Nghỉ trong kỳ" : "Đã nghỉ việc",
                    value: "\(kpis.inactiveCount)",
                    subtitle: "Trong kỳ: \(kpis.resignedInPeriodCount)",
                    iconName: "person.crop.circle.badge.minus",
                    accentColor: SemanticColors.dangerText
                )
                kpiCard(
                    title: "Phỏng vấn / Chờ nhận",
                    value: "\(kpis.interviewCount)",
                    subtitle: "Biến động \(WorkReportsContract.formatPercentVi(kpis.turnoverRate))",
                    iconName: "clock.badge.questionmark",
                    accentColor: SemanticColors.warningText
                )
            }

            // Projects Breakdown Card with HrpSegmentedBar & Load More
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(hasDateFilter ? "Thống kê Dự án (\(viewModel.selectedDatePreset.titleVi))" : "Thống kê nhân sự theo Dự án")
                        .font(.headline)
                        .foregroundColor(SemanticColors.textPrimary)
                    Spacer()
                    Text("\(max(viewModel.generalOverview.projectsTotal, allProjects.count)) dự án")
                        .font(.caption.weight(.semibold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .foregroundColor(SemanticColors.textMuted)
                }

                if allProjects.isEmpty {
                    Text("Chưa có dữ liệu dự án trong kỳ đã chọn.")
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textMuted)
                        .padding(.vertical, 8)
                } else {
                    LazyVStack(spacing: 8) {
                        ForEach(visibleProjects) { proj in
                            let retentionPct: Double = proj.totalCount > 0
                                ? (Double(proj.activeCount) * 100.0 / Double(proj.totalCount))
                                : 0
                            VStack(alignment: .leading, spacing: 7) {
                                // Row 1: Project Name left · Total right (+New badge if any)
                                HStack(spacing: 6) {
                                    Text(proj.projectName)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                        .lineLimit(1)
                                    Spacer()
                                    if proj.newCount > 0 {
                                        Text("+\(proj.newCount) mới")
                                            .font(.caption2.weight(.bold))
                                            .monospacedDigit()
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(SemanticColors.infoBg)
                                            .foregroundColor(SemanticColors.infoText)
                                            .clipShape(Capsule())
                                    }
                                    Text("\(proj.totalCount)")
                                        .font(.subheadline.weight(.bold))
                                        .monospacedDigit()
                                        .contentTransition(.numericText())
                                        .foregroundColor(theme.primary)
                                }

                                // Row 2: Visual Segmented Bar
                                HrpSegmentedBar(
                                    activeCount: proj.activeCount,
                                    inactiveCount: proj.inactiveCount
                                )

                                // Row 3: Colored Dots + Retention %
                                HStack(spacing: 12) {
                                    HStack(spacing: 4) {
                                        Circle()
                                            .fill(SemanticColors.successText)
                                            .frame(width: 6, height: 6)
                                        Text("\(proj.activeCount) đang làm")
                                            .font(.caption2.weight(.medium))
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textSecondary)
                                    }
                                    HStack(spacing: 4) {
                                        Circle()
                                            .fill(SemanticColors.dangerText.opacity(0.85))
                                            .frame(width: 6, height: 6)
                                        Text("\(proj.inactiveCount) nghỉ")
                                            .font(.caption2.weight(.medium))
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textSecondary)
                                    }
                                    Spacer()
                                    Text(WorkReportsContract.formatPercentVi(retentionPct))
                                        .font(.caption2.weight(.bold))
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.successText)
                                }
                            }
                            .padding(11)
                            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                        }
                    }

                    if allProjects.count > visibleProjectsCount {
                        HrpLoadMoreButton(
                            title: "Xem thêm dự án",
                            loadedCount: visibleProjects.count,
                            totalCount: allProjects.count,
                            isLoading: false,
                            theme: theme
                        ) {
                            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                                visibleProjectsCount += 10
                            }
                        }
                    }
                }
            }
            .padding(14)
            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

            // Recruiters & Managers Performance
            if !viewModel.generalOverview.recruiters.isEmpty {
                DashboardPersonPerformanceCard(
                    title: hasDateFilter ? "Hiệu suất Người tuyển dụng (\(viewModel.selectedDatePreset.titleVi))" : "Hiệu suất Người tuyển dụng",
                    rows: viewModel.generalOverview.recruiters,
                    theme: theme
                )
            }
            if !viewModel.generalOverview.managers.isEmpty {
                DashboardPersonPerformanceCard(
                    title: hasDateFilter ? "Hiệu suất Theo Nhóm (\(viewModel.selectedDatePreset.titleVi))" : "Hiệu suất Quản lý dự án",
                    rows: viewModel.generalOverview.managers,
                    theme: theme
                )
            }
        }
    }

    // MARK: - 2. Team Report Section

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

                            HrpSegmentedBar(
                                activeCount: team.activeWorkers,
                                inactiveCount: team.inactiveWorkers
                            )

                            HStack(spacing: 14) {
                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(SemanticColors.successText)
                                        .frame(width: 6, height: 6)
                                    Text("\(team.activeWorkers) đang làm")
                                        .font(.caption2.weight(.medium))
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.textSecondary)
                                }
                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(SemanticColors.dangerText.opacity(0.85))
                                        .frame(width: 6, height: 6)
                                    Text("\(team.inactiveWorkers) nghỉ")
                                        .font(.caption2.weight(.medium))
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.textSecondary)
                                }
                                if team.newWorkers > 0 {
                                    Text("+\(team.newWorkers) mới")
                                        .font(.caption2.weight(.bold))
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.infoText)
                                }
                                Spacer()
                                Text("Tổng: \(team.totalWorkers)")
                                    .font(.caption2.weight(.bold))
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textPrimary)
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

    // MARK: - Helper Cards

    private func kpiCard(
        title: String,
        value: String,
        subtitle: String,
        iconName: String = "chart.bar.fill",
        accentColor: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(accentColor.opacity(0.14))
                        .frame(width: 28, height: 28)
                    Image(systemName: iconName)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(accentColor)
                }
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(SemanticColors.textSecondary)
                    .lineLimit(1)
            }
            Text(value)
                .font(.system(size: 27, weight: .heavy, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())
                .animation(.default, value: value)
                .foregroundColor(accentColor)
            Text(subtitle)
                .font(.caption2.weight(.medium))
                .monospacedDigit()
                .contentTransition(.numericText())
                .foregroundColor(SemanticColors.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }
}

private struct DashboardPersonPerformanceCard: View {
    let title: String
    let rows: [PersonPerformanceRow]
    let theme: MiniBiThemePalette
    @State private var visibleCount: Int = 10

    var body: some View {
        let visibleRows = Array(rows.prefix(visibleCount))
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title)
                    .font(.headline)
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                Text("\(rows.count)")
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
                    .foregroundColor(SemanticColors.textMuted)
            }

            LazyVStack(spacing: 8) {
                ForEach(visibleRows) { row in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(row.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                                .lineLimit(1)
                            Spacer()
                            Text("\(row.totalCount)")
                                .font(.subheadline.weight(.bold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .foregroundColor(theme.primary)
                        }

                        HrpSegmentedBar(
                            activeCount: row.activeCount,
                            inactiveCount: row.inactiveCount
                        )

                        HStack(spacing: 12) {
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(SemanticColors.successText)
                                    .frame(width: 6, height: 6)
                                Text("\(row.activeCount) đang làm")
                                    .font(.caption2.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(SemanticColors.dangerText.opacity(0.85))
                                    .frame(width: 6, height: 6)
                                Text("\(row.inactiveCount) nghỉ")
                                    .font(.caption2.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            Spacer()
                            Text(WorkReportsContract.formatPercentVi(row.activeRatePct))
                                .font(.caption2.weight(.bold))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                                .foregroundColor(SemanticColors.successText)
                        }
                    }
                    .padding(10)
                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                }
            }

            if rows.count > visibleCount {
                HrpLoadMoreButton(
                    title: "Xem thêm",
                    loadedCount: visibleRows.count,
                    totalCount: rows.count,
                    isLoading: false,
                    theme: theme
                ) {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                        visibleCount += 10
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

                    if viewModel.cccdReviewItems.count < viewModel.cccdReviewTotal {
                        HrpLoadMoreButton(
                            title: "Tải thêm hồ sơ duyệt CCCD",
                            loadedCount: viewModel.cccdReviewItems.count,
                            totalCount: viewModel.cccdReviewTotal,
                            isLoading: viewModel.isLoadingMoreCccdReview,
                            theme: theme
                        ) {
                            Task { await viewModel.loadMoreCccdReviewItems() }
                        }
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
