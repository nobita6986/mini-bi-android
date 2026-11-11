import SwiftUI

public struct RecruitmentPostsScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme
        let cards = viewModel.recruitmentPosts.map { RecruitmentPostContract.toCardViewModel($0) }

        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "megaphone.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(theme.primary)

                Text("Tin tuyển dụng")
                    .font(.subheadline.weight(.bold))
                    .foregroundColor(SemanticColors.textPrimary)

                Spacer()

                Button {
                    HapticsManager.lightImpact()
                    Task { await viewModel.loadRecruitmentPosts(reset: true) }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 11, weight: .bold))
                        Text("Làm mới")
                            .font(.caption.weight(.semibold))
                    }
                    .foregroundColor(theme.primary)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 44)
                    .background(theme.primaryLight)
                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(HrpPressableButtonStyle())
                .disabled(viewModel.recruitmentPostsLoading || viewModel.recruitmentPostsLoadingMore)
                .accessibilityIdentifier("recruitment_posts_refresh_button")
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Color.white)
            .overlay(
                Rectangle()
                    .frame(height: 0.5)
                    .foregroundColor(SemanticColors.borderSubtle.opacity(0.65)),
                alignment: .bottom
            )

            if viewModel.recruitmentPostsAccessDenied {
                RecruitmentPostStateView(
                    iconName: "lock.shield",
                    title: "Từ chối truy cập",
                    message: viewModel.recruitmentPostsError ?? "Bạn không có quyền truy cập mục Tin tuyển dụng.",
                    actionTitle: nil,
                    theme: theme,
                    onAction: nil
                )
            } else if viewModel.recruitmentPostsFeatureUnavailable {
                RecruitmentPostStateView(
                    iconName: "exclamationmark.triangle",
                    title: "Tính năng tạm không khả dụng",
                    message: viewModel.recruitmentPostsError ?? "Tính năng Tin tuyển dụng hiện tạm thời không khả dụng.",
                    actionTitle: "Thử lại",
                    theme: theme,
                    onAction: {
                        Task { await viewModel.loadRecruitmentPosts(reset: true) }
                    }
                )
            } else if viewModel.recruitmentPostsLoading && cards.isEmpty {
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(0..<2, id: \.self) { _ in
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Dự án Sản xuất Linh kiện Điện tử")
                                    .font(.headline.weight(.bold))
                                HStack(spacing: 8) {
                                    Text("LCB: 5.500.000 đ")
                                        .font(.caption.weight(.semibold))
                                    Text("·")
                                    Text("Thu nhập: 9.500.000 - 13.000.000 đ")
                                        .font(.caption.weight(.bold))
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(SemanticColors.surfaceMuted)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                                Text("KCN Bắc Thăng Long · Hà Nội")
                                    .font(.caption)
                                HStack(spacing: 6) {
                                    Text("Thời vụ / Chính thức")
                                        .font(.caption2.weight(.semibold))
                                    Text("SL: 150 người")
                                        .font(.caption2.weight(.semibold))
                                    Spacer()
                                }
                            }
                            .padding(14)
                            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                        }
                    }
                    .padding(14)
                    .redacted(reason: .placeholder)
                }
            } else if let error = viewModel.recruitmentPostsError, cards.isEmpty {
                RecruitmentPostStateView(
                    iconName: "wifi.exclamationmark",
                    title: "Không thể tải danh sách",
                    message: error,
                    actionTitle: "Thử lại",
                    theme: theme,
                    onAction: {
                        Task { await viewModel.loadRecruitmentPosts(reset: true) }
                    }
                )
            } else if cards.isEmpty {
                RecruitmentPostStateView(
                    iconName: "megaphone",
                    title: "Chưa có tin tuyển dụng",
                    message: "Hiện chưa có tin tuyển dụng nào đang hiệu lực.",
                    actionTitle: "Làm mới",
                    theme: theme,
                    onAction: {
                        Task { await viewModel.loadRecruitmentPosts(reset: true) }
                    }
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if let inlineError = viewModel.recruitmentPostsError, !inlineError.isEmpty {
                            HStack(spacing: 8) {
                                Text(inlineError)
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.dangerText)
                                Spacer()
                                Button("Thử lại") {
                                    HapticsManager.lightImpact()
                                    Task {
                                        if viewModel.recruitmentPostsHasMore {
                                            await viewModel.loadMoreRecruitmentPosts()
                                        } else {
                                            await viewModel.loadRecruitmentPosts(reset: true)
                                        }
                                    }
                                }
                                .font(.caption.weight(.bold))
                                .foregroundColor(theme.primary)
                            }
                            .padding(12)
                            .background(SemanticColors.dangerBg)
                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                        }

                        ForEach(cards) { card in
                            RecruitmentPostSummaryCardView(
                                card: card,
                                theme: theme,
                                onOpenDetail: {
                                    HapticsManager.selection()
                                    Task { await viewModel.openRecruitmentPostDetail(briefId: card.briefId) }
                                }
                            )
                        }

                        if viewModel.recruitmentPostsHasMore {
                            HrpLoadMoreButton(
                                loadedCount: cards.count,
                                totalCount: nil,
                                isLoading: viewModel.recruitmentPostsLoadingMore,
                                theme: theme
                            ) {
                                Task { await viewModel.loadMoreRecruitmentPosts() }
                            }
                            .disabled(viewModel.recruitmentPostsLoading || viewModel.recruitmentPostsLoadingMore)
                            .accessibilityIdentifier("recruitment_posts_load_more_button")
                        }
                    }
                    .padding(14)
                }
                .refreshable {
                    await viewModel.performRefreshable {
                        await viewModel.loadRecruitmentPosts(reset: true)
                    }
                }
            }
        }
        .background(theme.background)
        .task {
            if viewModel.recruitmentPosts.isEmpty
                && !viewModel.recruitmentPostsLoading
                && !viewModel.recruitmentPostsAccessDenied
                && !viewModel.recruitmentPostsFeatureUnavailable {
                await viewModel.loadRecruitmentPosts(reset: true)
            }
        }
        .sheet(
            isPresented: Binding(
                get: { viewModel.recruitmentPostDetailBriefId != nil },
                set: { isPresented in
                    if !isPresented {
                        viewModel.closeRecruitmentPostDetail()
                    }
                }
            )
        ) {
            RecruitmentPostDetailSheet(viewModel: viewModel)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
    }
}

private struct RecruitmentPostStateView: View {
    let iconName: String
    let title: String
    let message: String
    let actionTitle: String?
    let theme: MiniBiThemePalette
    let onAction: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.system(size: 32, weight: .semibold))
                .foregroundColor(SemanticColors.textMuted)
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundColor(SemanticColors.textPrimary)
                .multilineTextAlignment(.center)
            Text(message)
                .font(.caption)
                .foregroundColor(SemanticColors.textSecondary)
                .multilineTextAlignment(.center)
            if let actionTitle, let onAction {
                Button {
                    HapticsManager.lightImpact()
                    onAction()
                } label: {
                    Text(actionTitle)
                        .font(.caption.weight(.bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .frame(minHeight: 40)
                        .background(theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                }
                .buttonStyle(HrpPressableButtonStyle())
                .accessibilityIdentifier("recruitment_posts_retry_button")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }
}

private struct RecruitmentPostSummaryCardView: View {
    let card: RecruitmentPostCardViewModel
    let theme: MiniBiThemePalette
    let onOpenDetail: () -> Void

    private var locationAndCustomerLine: String {
        let customer = card.customerNameDisplay.trimmingCharacters(in: .whitespacesAndNewlines)
        let project = card.projectName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !customer.isEmpty,
           customer != RecruitmentPostContract.unprovidedLabel,
           customer.caseInsensitiveCompare(project) != .orderedSame {
            return "\(card.locationDisplay) · \(customer)"
        }
        return card.locationDisplay
    }

    var body: some View {
        Button(action: onOpenDetail) {
            VStack(alignment: .leading, spacing: 8) {
                // Row 1: Full-width Project Name
                Text(card.projectName)
                    .font(.headline.weight(.bold))
                    .foregroundColor(theme.primary)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // Row 2: Full-width Base Salary & Income Range (Never truncated)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) {
                        HStack(spacing: 4) {
                            Text("LCB:")
                                .font(.caption.weight(.medium))
                                .foregroundColor(SemanticColors.textSecondary)
                            Text(card.baseSalaryDisplay)
                                .font(.caption.weight(.semibold))
                                .monospacedDigit()
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        .fixedSize(horizontal: true, vertical: false)

                        Text("·")
                            .font(.caption.weight(.bold))
                            .foregroundColor(SemanticColors.textMuted)

                        HStack(spacing: 4) {
                            Text("Thu nhập:")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.successText)
                            Text(card.incomeRangeDisplay)
                                .font(.caption.weight(.bold))
                                .monospacedDigit()
                                .foregroundColor(SemanticColors.successText)
                        }
                        .fixedSize(horizontal: true, vertical: false)

                        Spacer(minLength: 0)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 4) {
                            Text("LCB:")
                                .font(.caption.weight(.medium))
                                .foregroundColor(SemanticColors.textSecondary)
                            Text(card.baseSalaryDisplay)
                                .font(.caption.weight(.semibold))
                                .monospacedDigit()
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        HStack(spacing: 4) {
                            Text("Thu nhập:")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.successText)
                            Text(card.incomeRangeDisplay)
                                .font(.caption.weight(.bold))
                                .monospacedDigit()
                                .foregroundColor(SemanticColors.successText)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(SemanticColors.successBg.opacity(0.65))
                .overlay(
                    RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous)
                        .stroke(SemanticColors.successText.opacity(0.18), lineWidth: 0.8)
                )
                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))

                // Row 3: Location + Summary Chips
                HStack(spacing: 6) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textMuted)
                    Text(locationAndCustomerLine)
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                        .lineLimit(1)
                }

                HStack(spacing: 6) {
                    Text(card.laborTypesDisplay)
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(SemanticColors.surfaceMuted)
                        .foregroundColor(SemanticColors.textPrimary)
                        .clipShape(Capsule())
                        .lineLimit(1)

                    Text("SL: \(card.expectedHeadcountDisplay)")
                        .font(.caption2.weight(.semibold))
                        .monospacedDigit()
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(theme.primaryLight)
                        .foregroundColor(theme.primary)
                        .clipShape(Capsule())
                        .lineLimit(1)

                    Spacer(minLength: 4)

                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textMuted)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
        }
        .buttonStyle(HrpPressableButtonStyle())
        .accessibilityIdentifier("recruitment_post_detail_btn_\(card.briefId)")
    }
}

private struct RecruitmentPostMetaRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text("\(label):")
                .font(.caption)
                .foregroundColor(SemanticColors.textSecondary)
                .frame(width: 128, alignment: .leading)
            Text(value)
                .font(.caption.weight(.medium))
                .monospacedDigit()
                .foregroundColor(SemanticColors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct RecruitmentPostDetailSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel

    private func isProvided(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed != RecruitmentPostContract.unprovidedLabel
    }

    private func dialablePhoneUrl(from rawPhone: String) -> URL? {
        guard isProvided(rawPhone) else { return nil }
        let digitsAndPlus = rawPhone.filter { $0.isNumber || $0 == "+" }
        guard digitsAndPlus.count >= 7 else { return nil }
        return URL(string: "tel://\(digitsAndPlus)")
    }

    var body: some View {
        let theme = viewModel.selectedTheme
        let detail = viewModel.selectedRecruitmentPostDetail.map {
            RecruitmentPostContract.toDetailViewModel($0)
        }

        NavigationStack {
            ZStack {
                theme.background.ignoresSafeArea()

                if viewModel.recruitmentPostDetailLoading {
                    VStack(spacing: 12) {
                        ProgressView()
                            .tint(theme.primary)
                        Text("Đang tải chi tiết tin tuyển dụng…")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let error = viewModel.recruitmentPostDetailError {
                    RecruitmentPostStateView(
                        iconName: "exclamationmark.circle",
                        title: "Không thể tải chi tiết",
                        message: error,
                        actionTitle: "Thử lại",
                        theme: theme,
                        onAction: {
                            if let briefId = viewModel.recruitmentPostDetailBriefId {
                                Task { await viewModel.openRecruitmentPostDetail(briefId: briefId) }
                            }
                        }
                    )
                } else if let detail {
                    let s1 = detail.section1General
                    let s2 = detail.section2CandidateRequirements
                    let s3 = detail.section3CompensationBenefits
                    let s4 = detail.section4ApplicationDocuments

                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            // 1. Hero Highlight & Compensation Card
                            heroOverviewCard(s1: s1, s2: s2, s3: s3, theme: theme)

                            // 2. Job Description & Work Environment Card
                            jobAndEnvironmentCard(s1: s1, theme: theme)

                            // 3. Compensation, Allowances & Shift Rates Card
                            compensationAndShiftRatesCard(s3: s3, theme: theme)

                            // 4. Candidate Requirements Card (De-duplicated)
                            candidateRequirementsCard(s2: s2, theme: theme)

                            // 5. Application Documents Visual Checklist Card
                            applicationDocumentsCard(s4: s4, theme: theme)

                            // 6. Compact Footer Metadata Tile
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Người đăng: \(detail.createdByDisplayName) · \(detail.publishedAtDisplay)")
                                    .font(.caption)
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textSecondary)
                                Text("Cập nhật: \(detail.updatedByDisplayName) · \(detail.updatedAtDisplay)")
                                    .font(.caption)
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                        }
                        .padding(14)
                    }
                }
            }
            .navigationTitle(detail?.section1General.projectName ?? "Chi tiết tin tuyển dụng")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        viewModel.closeRecruitmentPostDetail()
                    }
                }
            }
        }
    }

    // MARK: - 1. Hero Card

    @ViewBuilder
    private func heroOverviewCard(
        s1: RecruitmentPostDetailGeneralViewModel,
        s2: RecruitmentPostDetailCandidateViewModel,
        s3: RecruitmentPostDetailCompensationViewModel,
        theme: MiniBiThemePalette
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(s1.projectName)
                    .font(.title3.weight(.bold))
                    .foregroundColor(theme.primary)
                    .fixedSize(horizontal: false, vertical: true)

                if isProvided(s1.customerNameDisplay)
                    && s1.customerNameDisplay.caseInsensitiveCompare(s1.projectName) != .orderedSame {
                    Text(s1.customerNameDisplay)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(SemanticColors.textPrimary)
                }

                HStack(spacing: 6) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textMuted)
                    Text("\(s1.industrialParkName) · \(s1.provinceName)")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }
            }

            HStack(spacing: 6) {
                Text(s1.laborTypesDisplay)
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(theme.primaryLight)
                    .foregroundColor(theme.primary)
                    .clipShape(Capsule())

                if isProvided(s1.investmentOriginDisplay) {
                    Text("Vốn: \(s1.investmentOriginDisplay)")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(SemanticColors.surfaceMuted)
                        .foregroundColor(SemanticColors.textSecondary)
                        .clipShape(Capsule())
                }
            }

            // Highlight Income & Base Salary Box
            VStack(alignment: .leading, spacing: 6) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("THU NHẬP DỰ KIẾN")
                        .font(.caption2.weight(.bold))
                        .foregroundColor(SemanticColors.successText.opacity(0.85))
                    Text(s3.incomeRangeDisplay)
                        .font(.title3.weight(.heavy))
                        .monospacedDigit()
                        .foregroundColor(SemanticColors.successText)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Divider().overlay(SemanticColors.successText.opacity(0.18))

                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("Lương cơ bản:")
                        .font(.caption.weight(.medium))
                        .foregroundColor(SemanticColors.textSecondary)
                    Text(s3.baseSalaryVndDisplay)
                        .font(.subheadline.weight(.bold))
                        .monospacedDigit()
                        .foregroundColor(SemanticColors.textPrimary)
                }

                if isProvided(s3.baseSalaryConditionsDisplay) {
                    Text(s3.baseSalaryConditionsDisplay)
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(SemanticColors.successBg)
            .overlay(
                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                    .stroke(SemanticColors.successText.opacity(0.22), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))

            // 2x2 Core Metrics Grid
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                heroMetricTile(
                    icon: "person.3.fill",
                    label: "Số lượng tuyển",
                    value: s1.expectedHeadcountDisplay,
                    tint: theme.primary
                )
                heroMetricTile(
                    icon: "person.text.rectangle.fill",
                    label: "Độ tuổi & Giới tính",
                    value: "\(s2.ageRangeDisplay) · \(s2.genderDisplay)",
                    tint: theme.primary
                )
                heroMetricTile(
                    icon: "clock.fill",
                    label: "Ca làm việc",
                    value: s1.shiftPatternDisplay,
                    tint: theme.primary
                )
                heroMetricTile(
                    icon: "graduationcap.fill",
                    label: "Học vấn & KN",
                    value: "\(s2.minimumEducationDisplay) · KN: \(s2.experienceRequiredDisplay)",
                    tint: theme.primary
                )
            }

            // Contact Footer Bar (Strictly uses s1.contactPersonDisplay from created_by_display_name)
            HStack(spacing: 8) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.subheadline)
                    .foregroundColor(theme.primary)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Người phụ trách")
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textMuted)
                    Text(s1.contactPersonDisplay)
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)
                }
                Spacer()
                if let phoneUrl = dialablePhoneUrl(from: s1.contactPhoneDisplay) {
                    Link(destination: phoneUrl) {
                        HStack(spacing: 5) {
                            Image(systemName: "phone.fill")
                                .font(.caption2.weight(.bold))
                            Text(s1.contactPhoneDisplay)
                                .font(.caption.weight(.bold))
                                .monospacedDigit()
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(theme.primaryLight)
                        .foregroundColor(theme.primary)
                        .clipShape(Capsule())
                    }
                } else {
                    HStack(spacing: 5) {
                        Image(systemName: "phone.fill")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)
                        Text(s1.contactPhoneDisplay)
                            .font(.caption.weight(.medium))
                            .monospacedDigit()
                            .foregroundColor(SemanticColors.textSecondary)
                            .textSelection(.enabled)
                    }
                }
            }
            .padding(10)
            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    private func heroMetricTile(icon: String, label: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.caption2.weight(.bold))
                    .foregroundColor(tint)
                Text(label)
                    .font(.caption2)
                    .foregroundColor(SemanticColors.textSecondary)
                    .lineLimit(1)
            }
            Text(value)
                .font(.caption.weight(.bold))
                .monospacedDigit()
                .foregroundColor(SemanticColors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
    }

    // MARK: - 2. Job & Work Environment Card

    @ViewBuilder
    private func jobAndEnvironmentCard(
        s1: RecruitmentPostDetailGeneralViewModel,
        theme: MiniBiThemePalette
    ) -> some View {
        let optionalRows: [(String, String)] = [
            ("Môi trường làm việc", s1.environmentTypesDisplay),
            ("Tư thế làm việc", s1.workPosturesDisplay),
            ("Chi tiết ca làm", s1.shiftDetailsDisplay),
            ("Lịch cung ứng", s1.supplyScheduleTextDisplay),
            ("Ngành nghề SX", s1.manufacturingIndustryDisplay)
        ].filter { isProvided($0.1) }

        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(icon: "briefcase.fill", title: "Công việc & Môi trường làm việc", theme: theme)

            VStack(alignment: .leading, spacing: 4) {
                Text("Mô tả công việc")
                    .font(.caption2.weight(.bold))
                    .foregroundColor(SemanticColors.textMuted)
                Text(s1.jobDescriptionDisplay)
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)

            ForEach(Array(optionalRows.enumerated()), id: \.offset) { _, row in
                RecruitmentPostMetaRow(label: row.0, value: row.1)
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    // MARK: - 3. Compensation, Allowances & Shift Rates Card

    @ViewBuilder
    private func compensationAndShiftRatesCard(
        s3: RecruitmentPostDetailCompensationViewModel,
        theme: MiniBiThemePalette
    ) -> some View {
        let allowances: [(title: String, amount: String, condition: String)] = [
            ("Thưởng chuyên cần", s3.attendanceBonusVndDisplay, s3.attendanceBonusConditionsDisplay),
            ("Phụ cấp đời sống", s3.livingAllowanceVndDisplay, s3.livingAllowanceConditionsDisplay),
            ("Phụ cấp thâm niên", s3.seniorityAllowanceVndDisplay, s3.seniorityAllowanceConditionsDisplay),
            ("Phụ cấp độc hại/KHV", s3.eyesightAllowanceVndDisplay, s3.eyesightAllowanceConditionsDisplay),
            ("Phụ cấp khác", s3.otherAllowanceTextDisplay, RecruitmentPostContract.unprovidedLabel)
        ].filter { isProvided($0.amount) || isProvided($0.condition) }

        let shiftRates: [(label: String, value: String)] = [
            ("Ca ngày 8h", s3.dayShift8hVndDisplay),
            ("Ca ngày 11h", s3.dayShift11hVndDisplay),
            ("Ca đêm 8h", s3.nightShift8hVndDisplay),
            ("Ca đêm 11h", s3.nightShift11hVndDisplay),
            ("Chủ nhật ngày 11h", s3.sundayDay11hVndDisplay),
            ("Chủ nhật đêm 11h", s3.sundayNight11hVndDisplay),
            ("Ngày lễ ngày 11h", s3.holidayDay11hVndDisplay),
            ("Ngày lễ đêm 11h", s3.holidayNight11hVndDisplay)
        ].filter { isProvided($0.value) }

        let salaryAdvanceSummary: String = {
            if s3.salaryAdvanceAvailableDisplay == "Không" {
                return "Không"
            }
            let details = [s3.salaryAdvanceAmountVndDisplay, s3.salaryAdvanceScheduleDisplay].filter { isProvided($0) }
            return details.isEmpty ? s3.salaryAdvanceAvailableDisplay : "\(s3.salaryAdvanceAvailableDisplay) (\(details.joined(separator: " · ")))"
        }()

        let weeklySalarySummary: String = {
            if s3.weeklySalaryAvailableDisplay == "Không" {
                return "Không"
            }
            if isProvided(s3.weeklySalaryPaymentScheduleDisplay) {
                return "\(s3.weeklySalaryAvailableDisplay) (\(s3.weeklySalaryPaymentScheduleDisplay))"
            }
            return s3.weeklySalaryAvailableDisplay
        }()

        let ppeSummary: String = {
            if isProvided(s3.ppeDetailsDisplay) {
                return "\(s3.ppeProvidedDisplay) · \(s3.ppeDetailsDisplay)"
            }
            return s3.ppeProvidedDisplay
        }()

        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(icon: "banknote.fill", title: "Lương, Phụ cấp & Đơn giá ca", theme: theme)

            // Sub-group 1: Allowances & Bonuses
            VStack(alignment: .leading, spacing: 8) {
                Text("Phụ cấp & Thưởng")
                    .font(.caption.weight(.bold))
                    .foregroundColor(SemanticColors.textSecondary)

                if allowances.isEmpty {
                    Text("Chưa cung cấp phụ cấp chi tiết.")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textMuted)
                } else {
                    ForEach(Array(allowances.enumerated()), id: \.offset) { _, item in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(alignment: .top) {
                                Text(item.title)
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.textPrimary)
                                Spacer()
                                if isProvided(item.amount) {
                                    Text(item.amount)
                                        .font(.caption.weight(.bold))
                                        .monospacedDigit()
                                        .foregroundColor(SemanticColors.successText)
                                }
                            }
                            if isProvided(item.condition) {
                                Text(item.condition)
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                        }
                        .padding(8)
                        .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusXS)
                    }
                }
            }

            // Sub-group 2: Shift Rates 2-column grid
            if !shiftRates.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Đơn giá theo ca")
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.textSecondary)

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        ForEach(Array(shiftRates.enumerated()), id: \.offset) { _, rate in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(rate.label)
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Text(rate.value)
                                    .font(.caption.weight(.bold))
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textPrimary)
                            }
                            .padding(8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusXS)
                        }
                    }
                }
            }

            // Sub-group 3: Payment & Benefits
            Divider()
            VStack(alignment: .leading, spacing: 6) {
                RecruitmentPostMetaRow(label: "Thanh toán lương", value: s3.salaryPaymentScheduleDisplay)
                RecruitmentPostMetaRow(label: "Ứng lương", value: salaryAdvanceSummary)
                RecruitmentPostMetaRow(label: "Lương tuần", value: weeklySalarySummary)
                RecruitmentPostMetaRow(label: "Đồng phục/bảo hộ", value: ppeSummary)
                if isProvided(s3.otherBenefitsDisplay) {
                    RecruitmentPostMetaRow(label: "Phúc lợi khác", value: s3.otherBenefitsDisplay)
                }
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    // MARK: - 4. Candidate Requirements Card (De-duplicated)

    @ViewBuilder
    private func candidateRequirementsCard(
        s2: RecruitmentPostDetailCandidateViewModel,
        theme: MiniBiThemePalette
    ) -> some View {
        let experienceSummary: String = {
            if s2.experienceRequiredDisplay == "Không" {
                return "Không yêu cầu kinh nghiệm"
            }
            if isProvided(s2.experienceDetailsDisplay) {
                return "\(s2.experienceRequiredDisplay) · \(s2.experienceDetailsDisplay)"
            }
            return s2.experienceRequiredDisplay
        }()

        let acceptsFormerPositive = s2.acceptsFormerWorkersDisplay == "Có"

        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(icon: "person.badge.shield.checkmark.fill", title: "Yêu cầu ứng viên", theme: theme)

            RecruitmentPostMetaRow(label: "Độ tuổi", value: s2.ageRangeDisplay)
            RecruitmentPostMetaRow(label: "Giới tính", value: s2.genderDisplay)
            RecruitmentPostMetaRow(label: "Trình độ học vấn", value: s2.minimumEducationDisplay)
            RecruitmentPostMetaRow(label: "Kinh nghiệm", value: experienceSummary)

            HStack(alignment: .center, spacing: 8) {
                Text("Nhận NLĐ cũ:")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textSecondary)
                    .frame(width: 128, alignment: .leading)
                Text(s2.acceptsFormerWorkersDisplay)
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(acceptsFormerPositive ? SemanticColors.successBg : SemanticColors.warningBg)
                    .foregroundColor(acceptsFormerPositive ? SemanticColors.successText : SemanticColors.warningText)
                    .clipShape(Capsule())
                Spacer()
            }

            if isProvided(s2.entryTestFormatDisplay) {
                RecruitmentPostMetaRow(label: "Kiểm tra đầu vào", value: s2.entryTestFormatDisplay)
            }
            if isProvided(s2.otherRequirementsDisplay) {
                RecruitmentPostMetaRow(label: "Yêu cầu khác", value: s2.otherRequirementsDisplay)
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    // MARK: - 5. Application Documents Visual Checklist Card

    @ViewBuilder
    private func applicationDocumentsCard(
        s4: RecruitmentPostDetailDocumentsViewModel,
        theme: MiniBiThemePalette
    ) -> some View {
        let otherDocLabel: String = {
            if s4.requireOtherDocuments && isProvided(s4.otherDocumentsTextDisplay) {
                return "Giấy tờ khác: \(s4.otherDocumentsTextDisplay)"
            }
            return "Giấy tờ khác"
        }()

        let allDocs: [(label: String, required: Bool)] = [
            ("CCCD gốc & bản sao", s4.requireOriginalCccdAndCopy),
            ("Sơ yếu lý lịch công chứng", s4.requireCertifiedResume),
            ("Giấy khám sức khỏe (6 tháng)", s4.requireHealthCertificateWithin6Months),
            ("Ảnh 3x4", s4.requirePhoto3x4),
            ("Bằng cấp/chứng chỉ", s4.requireDegreeOrCertificate),
            ("Sổ BHXH", s4.requireSocialInsuranceBook),
            (otherDocLabel, s4.requireOtherDocuments)
        ]
        let requiredDocs = allDocs.filter { $0.required }
        let optionalDocs = allDocs.filter { !$0.required }

        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(icon: "checklist", title: "Hồ sơ cần chuẩn bị", theme: theme)

            if requiredDocs.isEmpty {
                Text("Không yêu cầu hồ sơ bắt buộc.")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textSecondary)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(requiredDocs.enumerated()), id: \.offset) { _, doc in
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.subheadline)
                                .foregroundColor(SemanticColors.successText)
                            Text(doc.label)
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                    }
                }
            }

            if !optionalDocs.isEmpty {
                Divider()
                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: "minus.circle")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textMuted)
                    Text("Không bắt buộc: \(optionalDocs.map(\.label).joined(separator: ", "))")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    private func sectionHeader(icon: String, title: String, theme: MiniBiThemePalette) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline.weight(.bold))
                .foregroundColor(theme.primary)
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundColor(theme.primary)
        }
    }
}
