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
                                Text("Khách hàng: Công ty TNHH Điện tử Việt Nam")
                                    .font(.subheadline.weight(.semibold))
                                Text("KCN Bắc Thăng Long · Hà Nội")
                                    .font(.caption)
                                Divider()
                                Text("Công việc: Lắp ráp và kiểm tra ngoại quan linh kiện điện tử")
                                    .font(.footnote)
                                RecruitmentPostMetaRow(label: "Hình thức tuyển dụng", value: "Thời vụ / Chính thức")
                                RecruitmentPostMetaRow(label: "Số lượng dự kiến", value: "150 người")
                                RecruitmentPostMetaRow(label: "Lương & thu nhập", value: "9.500.000 - 13.000.000 đ")
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
                            Button {
                                HapticsManager.lightImpact()
                                Task { await viewModel.loadMoreRecruitmentPosts() }
                            } label: {
                                HStack(spacing: 8) {
                                    if viewModel.recruitmentPostsLoadingMore {
                                        ProgressView()
                                            .scaleEffect(0.85)
                                            .tint(theme.primary)
                                        Text("Đang tải thêm…")
                                    } else {
                                        Text("Tải thêm")
                                    }
                                }
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(theme.primary)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                        .stroke(theme.primary.opacity(0.45), lineWidth: 1)
                                )
                            }
                            .buttonStyle(HrpPressableButtonStyle())
                            .disabled(viewModel.recruitmentPostsLoading || viewModel.recruitmentPostsLoadingMore)
                            .accessibilityIdentifier("recruitment_posts_load_more_button")
                        }
                    }
                    .padding(14)
                }
                .refreshable {
                    await viewModel.loadRecruitmentPosts(reset: true)
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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(card.projectName)
                .font(.headline.weight(.bold))
                .foregroundColor(theme.primary)

            Text("Khách hàng: \(card.customerNameDisplay)")
                .font(.subheadline.weight(.semibold))
                .foregroundColor(SemanticColors.textPrimary)

            Text(card.locationDisplay)
                .font(.caption)
                .foregroundColor(SemanticColors.textSecondary)

            Divider()

            Text("Công việc: \(card.jobDescriptionDisplay)")
                .font(.footnote)
                .foregroundColor(SemanticColors.textPrimary)
                .lineLimit(3)

            RecruitmentPostMetaRow(label: "Hình thức tuyển dụng", value: card.laborTypesDisplay)
            RecruitmentPostMetaRow(label: "Số lượng dự kiến", value: card.expectedHeadcountDisplay)
            RecruitmentPostMetaRow(label: "Giới tính & độ tuổi", value: card.genderAndAgeDisplay)
            RecruitmentPostMetaRow(label: "Lương & thu nhập", value: card.baseSalaryAndIncomeDisplay)
            RecruitmentPostMetaRow(label: "Người phụ trách", value: card.contactPersonDisplay)
            RecruitmentPostMetaRow(label: "Số điện thoại", value: card.contactPhoneDisplay)
            RecruitmentPostMetaRow(label: "Cập nhật gần nhất", value: card.updatedAtDisplay)

            HStack {
                Spacer()
                Button(action: onOpenDetail) {
                    Text("Xem chi tiết")
                        .font(.caption.weight(.bold))
                        .foregroundColor(theme.primary)
                        .padding(.horizontal, 12)
                        .frame(minHeight: 36)
                        .background(theme.primaryLight)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                }
                .buttonStyle(HrpPressableButtonStyle())
                .accessibilityIdentifier("recruitment_post_detail_btn_\(card.briefId)")
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
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
                            RecruitmentDetailSectionView(
                                title: "1. Thông tin chung dự án",
                                theme: theme,
                                rows: [
                                    ("Tên dự án", s1.projectName),
                                    ("Tên khách hàng", s1.customerNameDisplay),
                                    ("Tỉnh/Thành", s1.provinceName),
                                    ("Khu công nghiệp", s1.industrialParkName),
                                    ("Vốn đầu tư", s1.investmentOriginDisplay),
                                    ("Ngành nghề sản xuất", s1.manufacturingIndustryDisplay),
                                    ("Hình thức tuyển dụng", s1.laborTypesDisplay),
                                    ("Công việc", s1.jobDescriptionDisplay),
                                    ("Môi trường làm việc", s1.environmentTypesDisplay),
                                    ("Tư thế làm việc", s1.workPosturesDisplay),
                                    ("Thời gian/ca làm", s1.shiftPatternDisplay),
                                    ("Chi tiết ca làm", s1.shiftDetailsDisplay),
                                    ("Lịch cung ứng", s1.supplyScheduleTextDisplay),
                                    ("Số lượng dự kiến", s1.expectedHeadcountDisplay),
                                    ("Người phụ trách", s1.contactPersonDisplay),
                                    ("Số điện thoại", s1.contactPhoneDisplay)
                                ]
                            )

                            RecruitmentDetailSectionView(
                                title: "2. Yêu cầu ứng viên",
                                theme: theme,
                                rows: [
                                    ("Độ tuổi", s2.ageRangeDisplay),
                                    ("Tuổi tối thiểu", s2.ageFromDisplay),
                                    ("Tuổi tối đa", s2.ageToDisplay),
                                    ("Giới tính", s2.genderDisplay),
                                    ("Trình độ học vấn", s2.minimumEducationDisplay),
                                    ("Yêu cầu kinh nghiệm", s2.experienceRequiredDisplay),
                                    ("Chi tiết kinh nghiệm", s2.experienceDetailsDisplay),
                                    ("Nhận NLĐ cũ", s2.acceptsFormerWorkersDisplay),
                                    ("Kiểm tra đầu vào", s2.entryTestFormatDisplay),
                                    ("Yêu cầu khác", s2.otherRequirementsDisplay)
                                ]
                            )

                            RecruitmentDetailSectionView(
                                title: "3. Lương, thưởng và phúc lợi",
                                theme: theme,
                                rows: [
                                    ("Lương cơ bản", s3.baseSalaryVndDisplay),
                                    ("Điều kiện lương cơ bản", s3.baseSalaryConditionsDisplay),
                                    ("Thưởng chuyên cần", s3.attendanceBonusVndDisplay),
                                    ("Điều kiện chuyên cần", s3.attendanceBonusConditionsDisplay),
                                    ("Phụ cấp đời sống", s3.livingAllowanceVndDisplay),
                                    ("Điều kiện PC đời sống", s3.livingAllowanceConditionsDisplay),
                                    ("Phụ cấp thâm niên", s3.seniorityAllowanceVndDisplay),
                                    ("Điều kiện PC thâm niên", s3.seniorityAllowanceConditionsDisplay),
                                    ("Phụ cấp độc hại/KHV", s3.eyesightAllowanceVndDisplay),
                                    ("Điều kiện PC độc hại/KHV", s3.eyesightAllowanceConditionsDisplay),
                                    ("Phụ cấp khác", s3.otherAllowanceTextDisplay),
                                    ("Thu nhập dự kiến", s3.incomeRangeDisplay),
                                    ("Ngày thanh toán lương", s3.salaryPaymentScheduleDisplay),
                                    ("Ứng lương", s3.salaryAdvanceAvailableDisplay),
                                    ("Mức ứng lương", s3.salaryAdvanceAmountVndDisplay),
                                    ("Lịch ứng lương", s3.salaryAdvanceScheduleDisplay),
                                    ("Lương tuần", s3.weeklySalaryAvailableDisplay),
                                    ("Lịch trả lương tuần", s3.weeklySalaryPaymentScheduleDisplay),
                                    ("Ca ngày 8h", s3.dayShift8hVndDisplay),
                                    ("Ca ngày 11h", s3.dayShift11hVndDisplay),
                                    ("Ca đêm 8h", s3.nightShift8hVndDisplay),
                                    ("Ca đêm 11h", s3.nightShift11hVndDisplay),
                                    ("Chủ nhật ca ngày 11h", s3.sundayDay11hVndDisplay),
                                    ("Chủ nhật ca đêm 11h", s3.sundayNight11hVndDisplay),
                                    ("Ngày lễ ca ngày 11h", s3.holidayDay11hVndDisplay),
                                    ("Ngày lễ ca đêm 11h", s3.holidayNight11hVndDisplay),
                                    ("Đồng phục/bảo hộ", s3.ppeProvidedDisplay),
                                    ("Chi tiết bảo hộ", s3.ppeDetailsDisplay),
                                    ("Phúc lợi khác", s3.otherBenefitsDisplay)
                                ]
                            )

                            RecruitmentDetailSectionView(
                                title: "4. Hồ sơ ứng viên cần chuẩn bị",
                                theme: theme,
                                rows: [
                                    ("CCCD gốc & bản sao", s4.requireOriginalCccdAndCopyDisplay),
                                    ("Sơ yếu lý lịch công chứng", s4.requireCertifiedResumeDisplay),
                                    ("Giấy khám sức khỏe (6 tháng)", s4.requireHealthCertificateWithin6MonthsDisplay),
                                    ("Ảnh 3x4", s4.requirePhoto3x4Display),
                                    ("Bằng cấp/chứng chỉ", s4.requireDegreeOrCertificateDisplay),
                                    ("Sổ BHXH", s4.requireSocialInsuranceBookDisplay),
                                    ("Giấy tờ khác", s4.requireOtherDocumentsDisplay),
                                    ("Chi tiết giấy tờ khác", s4.otherDocumentsTextDisplay)
                                ]
                            )

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Người đăng: \(detail.createdByDisplayName) · \(detail.publishedAtDisplay)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Text("Người cập nhật: \(detail.updatedByDisplayName) · \(detail.updatedAtDisplay)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )
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
}

private struct RecruitmentDetailSectionView: View {
    let title: String
    let theme: MiniBiThemePalette
    let rows: [(String, String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundColor(theme.primary)
            Divider()
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                RecruitmentPostMetaRow(label: row.0, value: row.1)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
        )
    }
}
