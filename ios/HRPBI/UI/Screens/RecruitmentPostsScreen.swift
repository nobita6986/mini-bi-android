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

                VStack(alignment: .leading, spacing: 2) {
                    Text("Tin tuyển dụng")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Text("Danh sách tin tuyển dụng đang hiệu lực (chỉ đọc)")
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textSecondary)
                }

                Spacer()

                Button {
                    Task { await viewModel.loadRecruitmentPosts(reset: true) }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 11, weight: .bold))
                        Text("Làm mới")
                            .font(.caption.weight(.semibold))
                    }
                    .foregroundColor(theme.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(theme.primarySoft)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .disabled(viewModel.recruitmentPostsLoading || viewModel.recruitmentPostsLoadingMore)
                .accessibilityIdentifier("recruitment_posts_refresh_button")
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.white)
            .overlay(
                Rectangle()
                    .frame(height: 1)
                    .foregroundColor(SemanticColors.borderSubtle),
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
                VStack(spacing: 12) {
                    ProgressView()
                        .tint(theme.primary)
                    Text("Đang tải tin tuyển dụng…")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                            .background(SemanticColors.dangerSoft)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }

                        ForEach(cards) { card in
                            RecruitmentPostSummaryCardView(
                                card: card,
                                theme: theme,
                                onOpenDetail: {
                                    Task { await viewModel.openRecruitmentPostDetail(briefId: card.briefId) }
                                }
                            )
                        }

                        if viewModel.recruitmentPostsHasMore {
                            Button {
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
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(theme.primary.opacity(0.45), lineWidth: 1)
                                )
                            }
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
        .background(SemanticColors.pageBackground)
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
                Button(action: onAction) {
                    Text(actionTitle)
                        .font(.caption.weight(.bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
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
                        .padding(.vertical, 7)
                        .background(theme.primarySoft)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .accessibilityIdentifier("recruitment_post_detail_btn_\(card.briefId)")
            }
        }
        .padding(14)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
        )
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
            Group {
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
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            RecruitmentDetailSectionView(title: "1. Thông tin chung dự án", theme: theme) {
                                let s1 = detail.section1General
                                RecruitmentPostMetaRow(label: "Tên dự án", value: s1.projectName)
                                RecruitmentPostMetaRow(label: "Tên khách hàng", value: s1.customerNameDisplay)
                                RecruitmentPostMetaRow(label: "Tỉnh/Thành", value: s1.provinceName)
                                RecruitmentPostMetaRow(label: "Khu công nghiệp", value: s1.industrialParkName)
                                RecruitmentPostMetaRow(label: "Vốn đầu tư", value: s1.investmentOriginDisplay)
                                RecruitmentPostMetaRow(label: "Ngành nghề sản xuất", value: s1.manufacturingIndustryDisplay)
                                RecruitmentPostMetaRow(label: "Hình thức tuyển dụng", value: s1.laborTypesDisplay)
                                RecruitmentPostMetaRow(label: "Công việc", value: s1.jobDescriptionDisplay)
                                RecruitmentPostMetaRow(label: "Môi trường làm việc", value: s1.environmentTypesDisplay)
                                RecruitmentPostMetaRow(label: "Tư thế làm việc", value: s1.workPosturesDisplay)
                                RecruitmentPostMetaRow(label: "Thời gian/ca làm", value: s1.shiftPatternDisplay)
                                RecruitmentPostMetaRow(label: "Chi tiết ca làm", value: s1.shiftDetailsDisplay)
                                RecruitmentPostMetaRow(label: "Lịch cung ứng", value: s1.supplyScheduleTextDisplay)
                                RecruitmentPostMetaRow(label: "Số lượng dự kiến", value: s1.expectedHeadcountDisplay)
                                RecruitmentPostMetaRow(label: "Người phụ trách", value: s1.contactPersonDisplay)
                                RecruitmentPostMetaRow(label: "Số điện thoại", value: s1.contactPhoneDisplay)
                            }

                            RecruitmentDetailSectionView(title: "2. Yêu cầu ứng viên", theme: theme) {
                                let s2 = detail.section2CandidateRequirements
                                RecruitmentPostMetaRow(label: "Độ tuổi", value: s2.ageRangeDisplay)
                                RecruitmentPostMetaRow(label: "Tuổi tối thiểu", value: s2.ageFromDisplay)
                                RecruitmentPostMetaRow(label: "Tuổi tối đa", value: s2.ageToDisplay)
                                RecruitmentPostMetaRow(label: "Giới tính", value: s2.genderDisplay)
                                RecruitmentPostMetaRow(label: "Trình độ học vấn", value: s2.minimumEducationDisplay)
                                RecruitmentPostMetaRow(label: "Yêu cầu kinh nghiệm", value: s2.experienceRequiredDisplay)
                                RecruitmentPostMetaRow(label: "Chi tiết kinh nghiệm", value: s2.experienceDetailsDisplay)
                                RecruitmentPostMetaRow(label: "Nhận NLĐ cũ", value: s2.acceptsFormerWorkersDisplay)
                                RecruitmentPostMetaRow(label: "Kiểm tra đầu vào", value: s2.entryTestFormatDisplay)
                                RecruitmentPostMetaRow(label: "Yêu cầu khác", value: s2.otherRequirementsDisplay)
                            }

                            RecruitmentDetailSectionView(title: "3. Lương, thưởng và phúc lợi", theme: theme) {
                                let s3 = detail.section3CompensationBenefits
                                RecruitmentPostMetaRow(label: "Lương cơ bản", value: s3.baseSalaryVndDisplay)
                                RecruitmentPostMetaRow(label: "Điều kiện lương cơ bản", value: s3.baseSalaryConditionsDisplay)
                                RecruitmentPostMetaRow(label: "Thưởng chuyên cần", value: s3.attendanceBonusVndDisplay)
                                RecruitmentPostMetaRow(label: "Điều kiện chuyên cần", value: s3.attendanceBonusConditionsDisplay)
                                RecruitmentPostMetaRow(label: "Phụ cấp đời sống", value: s3.livingAllowanceVndDisplay)
                                RecruitmentPostMetaRow(label: "Điều kiện PC đời sống", value: s3.livingAllowanceConditionsDisplay)
                                RecruitmentPostMetaRow(label: "Phụ cấp thâm niên", value: s3.seniorityAllowanceVndDisplay)
                                RecruitmentPostMetaRow(label: "Điều kiện PC thâm niên", value: s3.seniorityAllowanceConditionsDisplay)
                                RecruitmentPostMetaRow(label: "Phụ cấp độc hại/KHV", value: s3.eyesightAllowanceVndDisplay)
                                RecruitmentPostMetaRow(label: "Điều kiện PC độc hại/KHV", value: s3.eyesightAllowanceConditionsDisplay)
                                RecruitmentPostMetaRow(label: "Phụ cấp khác", value: s3.otherAllowanceTextDisplay)
                                RecruitmentPostMetaRow(label: "Thu nhập dự kiến", value: s3.incomeRangeDisplay)
                                RecruitmentPostMetaRow(label: "Ngày thanh toán lương", value: s3.salaryPaymentScheduleDisplay)
                                RecruitmentPostMetaRow(label: "Ứng lương", value: s3.salaryAdvanceAvailableDisplay)
                                RecruitmentPostMetaRow(label: "Mức ứng lương", value: s3.salaryAdvanceAmountVndDisplay)
                                RecruitmentPostMetaRow(label: "Lịch ứng lương", value: s3.salaryAdvanceScheduleDisplay)
                                RecruitmentPostMetaRow(label: "Lương tuần", value: s3.weeklySalaryAvailableDisplay)
                                RecruitmentPostMetaRow(label: "Lịch trả lương tuần", value: s3.weeklySalaryPaymentScheduleDisplay)
                                RecruitmentPostMetaRow(label: "Ca ngày 8h", value: s3.dayShift8hVndDisplay)
                                RecruitmentPostMetaRow(label: "Ca ngày 11h", value: s3.dayShift11hVndDisplay)
                                RecruitmentPostMetaRow(label: "Ca đêm 8h", value: s3.nightShift8hVndDisplay)
                                RecruitmentPostMetaRow(label: "Ca đêm 11h", value: s3.nightShift11hVndDisplay)
                                RecruitmentPostMetaRow(label: "Chủ nhật ca ngày 11h", value: s3.sundayDay11hVndDisplay)
                                RecruitmentPostMetaRow(label: "Chủ nhật ca đêm 11h", value: s3.sundayNight11hVndDisplay)
                                RecruitmentPostMetaRow(label: "Ngày lễ ca ngày 11h", value: s3.holidayDay11hVndDisplay)
                                RecruitmentPostMetaRow(label: "Ngày lễ ca đêm 11h", value: s3.holidayNight11hVndDisplay)
                                RecruitmentPostMetaRow(label: "Đồng phục/bảo hộ", value: s3.ppeProvidedDisplay)
                                RecruitmentPostMetaRow(label: "Chi tiết bảo hộ", value: s3.ppeDetailsDisplay)
                                RecruitmentPostMetaRow(label: "Phúc lợi khác", value: s3.otherBenefitsDisplay)
                            }

                            RecruitmentDetailSectionView(title: "4. Hồ sơ ứng viên cần chuẩn bị", theme: theme) {
                                let s4 = detail.section4ApplicationDocuments
                                RecruitmentPostMetaRow(label: "CCCD gốc & bản sao", value: s4.requireOriginalCccdAndCopyDisplay)
                                RecruitmentPostMetaRow(label: "Sơ yếu lý lịch công chứng", value: s4.requireCertifiedResumeDisplay)
                                RecruitmentPostMetaRow(
                                    label: "Giấy khám sức khỏe (6 tháng)",
                                    value: s4.requireHealthCertificateWithin6MonthsDisplay
                                )
                                RecruitmentPostMetaRow(label: "Ảnh 3x4", value: s4.requirePhoto3x4Display)
                                RecruitmentPostMetaRow(label: "Bằng cấp/chứng chỉ", value: s4.requireDegreeOrCertificateDisplay)
                                RecruitmentPostMetaRow(label: "Sổ BHXH", value: s4.requireSocialInsuranceBookDisplay)
                                RecruitmentPostMetaRow(label: "Giấy tờ khác", value: s4.requireOtherDocumentsDisplay)
                                RecruitmentPostMetaRow(label: "Chi tiết giấy tờ khác", value: s4.otherDocumentsTextDisplay)
                            }

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
                    .background(SemanticColors.pageBackground)
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

private struct RecruitmentDetailSectionView<Content: View>: View {
    let title: String
    let theme: MiniBiThemePalette
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundColor(theme.primary)
            Divider()
            content()
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
