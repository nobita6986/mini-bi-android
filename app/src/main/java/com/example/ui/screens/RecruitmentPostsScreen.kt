package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.RecruitmentPostCardViewModel
import com.example.data.RecruitmentPostContract
import com.example.data.RecruitmentPostDetailViewModel
import com.example.data.RecruitmentPostItem
import com.example.ui.MiniBiViewModel

@Composable
fun RecruitmentPostsScreen(
    viewModel: MiniBiViewModel
) {
    val items by viewModel.recruitmentPosts.collectAsState()
    val loading by viewModel.recruitmentPostsLoading.collectAsState()
    val loadingMore by viewModel.recruitmentPostsLoadingMore.collectAsState()
    val error by viewModel.recruitmentPostsError.collectAsState()
    val hasMore by viewModel.recruitmentPostsHasMore.collectAsState()
    val accessDenied by viewModel.recruitmentPostsAccessDenied.collectAsState()
    val featureUnavailable by viewModel.recruitmentPostsFeatureUnavailable.collectAsState()
    val selectedDetail by viewModel.selectedRecruitmentPostDetail.collectAsState()
    val detailLoading by viewModel.recruitmentPostDetailLoading.collectAsState()
    val detailError by viewModel.recruitmentPostDetailError.collectAsState()
    val detailBriefId by viewModel.recruitmentPostDetailBriefId.collectAsState()

    val cardModels = remember(items) {
        items.map { RecruitmentPostContract.toCardViewModel(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recruitment_posts_screen")
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Tin tuyển dụng",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Danh sách tin tuyển dụng đang hiệu lực (chỉ đọc)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.loadRecruitmentPosts(reset = true) },
                    enabled = !loading && !loadingMore,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("recruitment_posts_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Làm mới", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        when {
            accessDenied -> {
                RecruitmentPostStatusBox(
                    title = "Từ chối truy cập",
                    message = error ?: "Bạn không có quyền truy cập mục Tin tuyển dụng.",
                    actionLabel = null,
                    onAction = null,
                    testTag = "recruitment_posts_access_denied"
                )
            }
            featureUnavailable -> {
                RecruitmentPostStatusBox(
                    title = "Tính năng tạm không khả dụng",
                    message = error ?: "Tính năng Tin tuyển dụng hiện tạm thời không khả dụng.",
                    actionLabel = "Thử lại",
                    onAction = { viewModel.loadRecruitmentPosts(reset = true) },
                    testTag = "recruitment_posts_feature_unavailable"
                )
            }
            loading && cardModels.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("recruitment_posts_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Đang tải tin tuyển dụng…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            error != null && cardModels.isEmpty() -> {
                RecruitmentPostStatusBox(
                    title = "Không thể tải danh sách",
                    message = error.orEmpty(),
                    actionLabel = "Thử lại",
                    onAction = { viewModel.loadRecruitmentPosts(reset = true) },
                    testTag = "recruitment_posts_error_box"
                )
            }
            cardModels.isEmpty() -> {
                RecruitmentPostStatusBox(
                    title = "Chưa có tin tuyển dụng",
                    message = "Hiện chưa có tin tuyển dụng nào đang hiệu lực.",
                    actionLabel = "Làm mới",
                    onAction = { viewModel.loadRecruitmentPosts(reset = true) },
                    testTag = "recruitment_posts_empty_box"
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("recruitment_posts_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!error.isNullOrBlank()) {
                        item(key = "inline_error") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recruitment_posts_inline_error")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = error.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            if (hasMore) {
                                                viewModel.loadMoreRecruitmentPosts()
                                            } else {
                                                viewModel.loadRecruitmentPosts(reset = true)
                                            }
                                        },
                                        modifier = Modifier.testTag("recruitment_posts_retry_button")
                                    ) {
                                        Text("Thử lại")
                                    }
                                }
                            }
                        }
                    }

                    items(cardModels, key = { it.briefId }) { card ->
                        RecruitmentPostSummaryCard(
                            card = card,
                            onOpenDetail = { viewModel.openRecruitmentPostDetail(card.briefId) }
                        )
                    }

                    if (hasMore) {
                        item(key = "load_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.loadMoreRecruitmentPosts() },
                                    enabled = !loading && !loadingMore,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recruitment_posts_load_more_button")
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Đang tải thêm…")
                                    } else {
                                        Text("Tải thêm")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!detailBriefId.isNullOrBlank()) {
        RecruitmentPostDetailDialog(
            briefId = detailBriefId.orEmpty(),
            item = selectedDetail,
            loading = detailLoading,
            error = detailError,
            onRetry = { viewModel.openRecruitmentPostDetail(detailBriefId.orEmpty()) },
            onDismiss = { viewModel.closeRecruitmentPostDetail() }
        )
    }
}

@Composable
private fun RecruitmentPostStatusBox(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.testTag("recruitment_posts_retry_button")
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecruitmentPostSummaryCard(
    card: RecruitmentPostCardViewModel,
    onOpenDetail: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recruitment_post_card_${card.briefId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = card.projectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Khách hàng: ${card.customerNameDisplay}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = card.locationDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = "Công việc: ${card.jobDescriptionDisplay}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            RecruitmentCardMetaRow("Hình thức tuyển dụng", card.laborTypesDisplay)
            RecruitmentCardMetaRow("Số lượng dự kiến", card.expectedHeadcountDisplay)
            RecruitmentCardMetaRow("Giới tính & độ tuổi", card.genderAndAgeDisplay)
            RecruitmentCardMetaRow("Lương & thu nhập", card.baseSalaryAndIncomeDisplay)
            RecruitmentCardMetaRow("Người phụ trách", card.contactPersonDisplay)
            RecruitmentCardMetaRow("Số điện thoại", card.contactPhoneDisplay)
            RecruitmentCardMetaRow("Cập nhật gần nhất", card.updatedAtDisplay)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.testTag("recruitment_post_detail_btn_${card.briefId}")
                ) {
                    Text("Xem chi tiết")
                }
            }
        }
    }
}

@Composable
private fun RecruitmentCardMetaRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun RecruitmentPostDetailDialog(
    briefId: String,
    item: RecruitmentPostItem?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val detail: RecruitmentPostDetailViewModel? = remember(item) {
        item?.let { RecruitmentPostContract.toDetailViewModel(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .testTag("recruitment_post_detail_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = detail?.section1General?.projectName ?: "Chi tiết tin tuyển dụng",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("recruitment_post_detail_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider()

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                                .testTag("recruitment_post_detail_loading"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .testTag("recruitment_post_detail_error"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.testTag("recruitment_post_detail_retry_btn")
                            ) {
                                Text("Thử lại")
                            }
                        }
                    }
                    detail != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RecruitmentDetailSectionCard(title = "1. Thông tin chung dự án") {
                                val s1 = detail.section1General
                                RecruitmentCardMetaRow("Tên dự án", s1.projectName)
                                RecruitmentCardMetaRow("Tên khách hàng", s1.customerNameDisplay)
                                RecruitmentCardMetaRow("Tỉnh/Thành", s1.provinceName)
                                RecruitmentCardMetaRow("Khu công nghiệp", s1.industrialParkName)
                                RecruitmentCardMetaRow("Vốn đầu tư", s1.investmentOriginDisplay)
                                RecruitmentCardMetaRow("Ngành nghề sản xuất", s1.manufacturingIndustryDisplay)
                                RecruitmentCardMetaRow("Hình thức tuyển dụng", s1.laborTypesDisplay)
                                RecruitmentCardMetaRow("Công việc", s1.jobDescriptionDisplay)
                                RecruitmentCardMetaRow("Môi trường làm việc", s1.environmentTypesDisplay)
                                RecruitmentCardMetaRow("Tư thế làm việc", s1.workPosturesDisplay)
                                RecruitmentCardMetaRow("Thời gian/ca làm", s1.shiftPatternDisplay)
                                RecruitmentCardMetaRow("Chi tiết ca làm", s1.shiftDetailsDisplay)
                                RecruitmentCardMetaRow("Lịch cung ứng", s1.supplyScheduleTextDisplay)
                                RecruitmentCardMetaRow("Số lượng dự kiến", s1.expectedHeadcountDisplay)
                                RecruitmentCardMetaRow("Người phụ trách", s1.contactPersonDisplay)
                                RecruitmentCardMetaRow("Số điện thoại", s1.contactPhoneDisplay)
                            }

                            RecruitmentDetailSectionCard(title = "2. Yêu cầu ứng viên") {
                                val s2 = detail.section2CandidateRequirements
                                RecruitmentCardMetaRow("Độ tuổi", s2.ageRangeDisplay)
                                RecruitmentCardMetaRow("Tuổi tối thiểu", s2.ageFromDisplay)
                                RecruitmentCardMetaRow("Tuổi tối đa", s2.ageToDisplay)
                                RecruitmentCardMetaRow("Giới tính", s2.genderDisplay)
                                RecruitmentCardMetaRow("Trình độ học vấn", s2.minimumEducationDisplay)
                                RecruitmentCardMetaRow("Yêu cầu kinh nghiệm", s2.experienceRequiredDisplay)
                                RecruitmentCardMetaRow("Chi tiết kinh nghiệm", s2.experienceDetailsDisplay)
                                RecruitmentCardMetaRow("Nhận NLĐ cũ", s2.acceptsFormerWorkersDisplay)
                                RecruitmentCardMetaRow("Kiểm tra đầu vào", s2.entryTestFormatDisplay)
                                RecruitmentCardMetaRow("Yêu cầu khác", s2.otherRequirementsDisplay)
                            }

                            RecruitmentDetailSectionCard(title = "3. Lương, thưởng và phúc lợi") {
                                val s3 = detail.section3CompensationBenefits
                                RecruitmentCardMetaRow("Lương cơ bản", s3.baseSalaryVndDisplay)
                                RecruitmentCardMetaRow("Điều kiện lương cơ bản", s3.baseSalaryConditionsDisplay)
                                RecruitmentCardMetaRow("Thưởng chuyên cần", s3.attendanceBonusVndDisplay)
                                RecruitmentCardMetaRow("Điều kiện chuyên cần", s3.attendanceBonusConditionsDisplay)
                                RecruitmentCardMetaRow("Phụ cấp đời sống", s3.livingAllowanceVndDisplay)
                                RecruitmentCardMetaRow("Điều kiện PC đời sống", s3.livingAllowanceConditionsDisplay)
                                RecruitmentCardMetaRow("Phụ cấp thâm niên", s3.seniorityAllowanceVndDisplay)
                                RecruitmentCardMetaRow("Điều kiện PC thâm niên", s3.seniorityAllowanceConditionsDisplay)
                                RecruitmentCardMetaRow("Phụ cấp độc hại/KHV", s3.eyesightAllowanceVndDisplay)
                                RecruitmentCardMetaRow("Điều kiện PC độc hại/KHV", s3.eyesightAllowanceConditionsDisplay)
                                RecruitmentCardMetaRow("Phụ cấp khác", s3.otherAllowanceTextDisplay)
                                RecruitmentCardMetaRow("Thu nhập dự kiến", s3.incomeRangeDisplay)
                                RecruitmentCardMetaRow("Ngày thanh toán lương", s3.salaryPaymentScheduleDisplay)
                                RecruitmentCardMetaRow("Ứng lương", s3.salaryAdvanceAvailableDisplay)
                                RecruitmentCardMetaRow("Mức ứng lương", s3.salaryAdvanceAmountVndDisplay)
                                RecruitmentCardMetaRow("Lịch ứng lương", s3.salaryAdvanceScheduleDisplay)
                                RecruitmentCardMetaRow("Lương tuần", s3.weeklySalaryAvailableDisplay)
                                RecruitmentCardMetaRow("Lịch trả lương tuần", s3.weeklySalaryPaymentScheduleDisplay)
                                RecruitmentCardMetaRow("Ca ngày 8h", s3.dayShift8hVndDisplay)
                                RecruitmentCardMetaRow("Ca ngày 11h", s3.dayShift11hVndDisplay)
                                RecruitmentCardMetaRow("Ca đêm 8h", s3.nightShift8hVndDisplay)
                                RecruitmentCardMetaRow("Ca đêm 11h", s3.nightShift11hVndDisplay)
                                RecruitmentCardMetaRow("Chủ nhật ca ngày 11h", s3.sundayDay11hVndDisplay)
                                RecruitmentCardMetaRow("Chủ nhật ca đêm 11h", s3.sundayNight11hVndDisplay)
                                RecruitmentCardMetaRow("Ngày lễ ca ngày 11h", s3.holidayDay11hVndDisplay)
                                RecruitmentCardMetaRow("Ngày lễ ca đêm 11h", s3.holidayNight11hVndDisplay)
                                RecruitmentCardMetaRow("Đồng phục/bảo hộ", s3.ppeProvidedDisplay)
                                RecruitmentCardMetaRow("Chi tiết bảo hộ", s3.ppeDetailsDisplay)
                                RecruitmentCardMetaRow("Phúc lợi khác", s3.otherBenefitsDisplay)
                            }

                            RecruitmentDetailSectionCard(title = "4. Hồ sơ ứng viên cần chuẩn bị") {
                                val s4 = detail.section4ApplicationDocuments
                                RecruitmentCardMetaRow("CCCD gốc & bản sao", s4.requireOriginalCccdAndCopyDisplay)
                                RecruitmentCardMetaRow("Sơ yếu lý lịch công chứng", s4.requireCertifiedResumeDisplay)
                                RecruitmentCardMetaRow(
                                    "Giấy khám sức khỏe (6 tháng)",
                                    s4.requireHealthCertificateWithin6MonthsDisplay
                                )
                                RecruitmentCardMetaRow("Ảnh 3x4", s4.requirePhoto3x4Display)
                                RecruitmentCardMetaRow("Bằng cấp/chứng chỉ", s4.requireDegreeOrCertificateDisplay)
                                RecruitmentCardMetaRow("Sổ BHXH", s4.requireSocialInsuranceBookDisplay)
                                RecruitmentCardMetaRow("Giấy tờ khác", s4.requireOtherDocumentsDisplay)
                                RecruitmentCardMetaRow("Chi tiết giấy tờ khác", s4.otherDocumentsTextDisplay)
                            }

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recruitment_post_detail_metadata")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Người đăng: ${detail.createdByDisplayName} · ${detail.publishedAtDisplay}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Người cập nhật: ${detail.updatedByDisplayName} · ${detail.updatedAtDisplay}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecruitmentDetailSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}
