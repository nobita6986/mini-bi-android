package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AppActor
import com.example.data.WorkReportObligationEntity
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors

@Composable
fun WorkReportsScreen(
    actor: AppActor,
    reports: List<WorkReportObligationEntity>,
    onSaveReport: (WorkReportObligationEntity, Int, Int, Int, String, Boolean) -> Unit,
    onCreateNewDailyReport: (Int, Int, Int, String, Boolean) -> Unit,
    onReviewReport: (WorkReportObligationEntity, Boolean, String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    BackHandler(onBack = onBackToDashboard)

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingObligation by remember { mutableStateOf<WorkReportObligationEntity?>(null) }
    var showCreateNewModal by remember { mutableStateOf(false) }

    val activeObligations = remember(reports) {
        reports.filter { it.obligationStatus == "ACTIVE" && it.submissionState in listOf("NOT_STARTED", "DRAFT", "REJECTED") }
    }
    val historyObligations = remember(reports) {
        reports.filter { it.submissionState != "NOT_STARTED" }
    }
    val reviewQueue = remember(reports) {
        reports.filter { it.submissionState == "PENDING_REVIEW" }
    }

    val subTabs = listOf(
        "Cần báo cáo (${activeObligations.size})",
        "Lịch sử báo cáo (${historyObligations.size})",
        "Duyệt báo cáo (${reviewQueue.size})",
        "Tổng hợp kết quả"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("work_reports_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Quick Create Daily Report Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BÁO CÁO CÔNG VIỆC HÀNG NGÀY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Quản lý & Xem Báo cáo Ngày",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Mẫu chuẩn: Kết quả tuyển dụng trong ngày (Số cuộc gọi, Ứng viên quan tâm, NLĐ đi làm)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showCreateNewModal = !showCreateNewModal },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_daily_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showCreateNewModal) "Đóng biểu mẫu tạo báo cáo mới" else "Lập Báo cáo Công việc Hàng ngày Mới",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    AnimatedVisibility(visible = showCreateNewModal) {
                        DailyWorkReportFormCard(
                            title = "Lập Báo cáo Ngày Hôm nay (2026-10-23)",
                            initialCalls = 45,
                            initialInterested = 18,
                            initialStarted = 8,
                            initialNotes = "Gọi điện tư vấn ứng viên dự án Luxshare & Foxconn, hỗ trợ nhận việc ca sáng.",
                            onCancel = { showCreateNewModal = false },
                            onSaveDraft = { calls, interested, started, notes ->
                                onCreateNewDailyReport(calls, interested, started, notes, false)
                                showCreateNewModal = false
                            },
                            onSubmitFinal = { calls, interested, started, notes ->
                                onCreateNewDailyReport(calls, interested, started, notes, true)
                                showCreateNewModal = false
                            }
                        )
                    }
                }
            }
        }

        // Sub-navigation Tabs matching `/work-reports` in `nobita6986/mini-bi`
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                subTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedTab == index)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .testTag("work_reports_subtab_$index"),
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        // Active Editor if user clicked "Mở báo cáo" on an item
        editingObligation?.let { target ->
            item {
                DailyWorkReportFormCard(
                    title = "${target.templateName} · Kỳ ${target.periodKey}",
                    initialCalls = target.callsMade,
                    initialInterested = target.interestedCandidates,
                    initialStarted = target.workersStarted,
                    initialNotes = target.notes,
                    onCancel = { editingObligation = null },
                    onSaveDraft = { calls, interested, started, notes ->
                        onSaveReport(target, calls, interested, started, notes, false)
                        editingObligation = null
                    },
                    onSubmitFinal = { calls, interested, started, notes ->
                        onSaveReport(target, calls, interested, started, notes, true)
                        editingObligation = null
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Cần báo cáo (My Active Obligations)
                if (activeObligations.isEmpty()) {
                    item {
                        EmptyReportBanner(
                            title = "Tất cả nghĩa vụ báo cáo trong ngày đã hoàn tất",
                            subtitle = "Bạn có thể chuyển sang tab 'Lịch sử báo cáo' để xem lại hoặc bấm 'Lập Báo cáo Công việc Hàng ngày Mới'."
                        )
                    }
                } else {
                    items(activeObligations, key = { it.obligationId }) { item ->
                        WorkReportObligationCard(
                            item = item,
                            showEditAction = true,
                            onOpenEdit = { editingObligation = item }
                        )
                    }
                }
            }

            1 -> {
                // Lịch sử của tôi & Toàn đơn vị
                items(historyObligations, key = { it.obligationId }) { item ->
                    WorkReportObligationCard(
                        item = item,
                        showEditAction = item.submissionState in listOf("DRAFT", "REJECTED"),
                        onOpenEdit = { editingObligation = item }
                    )
                }
            }

            2 -> {
                // Hàng đợi Duyệt báo cáo (`/work-reports/reviews`)
                if (reviewQueue.isEmpty()) {
                    item {
                        EmptyReportBanner(
                            title = "Không có báo cáo nào đang chờ duyệt",
                            subtitle = "Tất cả báo cáo gửi lên trong phạm vi quản lý đã được duyệt."
                        )
                    }
                } else {
                    items(reviewQueue, key = { it.obligationId }) { item ->
                        ReviewInboxItemCard(
                            item = item,
                            onApprove = { reason -> onReviewReport(item, true, reason) },
                            onReject = { reason -> onReviewReport(item, false, reason) }
                        )
                    }
                }
            }

            3 -> {
                // Tổng hợp báo cáo (`/work-reports/analytics`)
                item {
                    WorkReportAnalyticsSummaryCard(reports = reports)
                }
            }
        }
    }
}

@Composable
private fun DailyWorkReportFormCard(
    title: String,
    initialCalls: Int,
    initialInterested: Int,
    initialStarted: Int,
    initialNotes: String,
    onCancel: () -> Unit,
    onSaveDraft: (Int, Int, Int, String) -> Unit,
    onSubmitFinal: (Int, Int, Int, String) -> Unit
) {
    var callsText by remember(initialCalls) { mutableStateOf(initialCalls.toString()) }
    var interestedText by remember(initialInterested) { mutableStateOf(initialInterested.toString()) }
    var startedText by remember(initialStarted) { mutableStateOf(initialStarted.toString()) }
    var notesText by remember(initialNotes) { mutableStateOf(initialNotes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Nhập số cuộc gọi, ứng viên quan tâm và số NLĐ đi làm thực tế",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = callsText,
                onValueChange = { callsText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Số cuộc gọi") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_calls_made")
            )
            OutlinedTextField(
                value = interestedText,
                onValueChange = { interestedText = it.filter { ch -> ch.isDigit() } },
                label = { Text("ƯV quan tâm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_interested_candidates")
            )
            OutlinedTextField(
                value = startedText,
                onValueChange = { startedText = it.filter { ch -> ch.isDigit() } },
                label = { Text("NLĐ đi làm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_workers_started")
            )
        }

        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("Nội dung công việc chi tiết trong ngày") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_report_notes")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Đóng")
            }
            OutlinedButton(
                onClick = {
                    onSaveDraft(
                        callsText.toIntOrNull() ?: 0,
                        interestedText.toIntOrNull() ?: 0,
                        startedText.toIntOrNull() ?: 0,
                        notesText
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_draft_report_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lưu nháp")
            }
            Button(
                onClick = {
                    onSubmitFinal(
                        callsText.toIntOrNull() ?: 0,
                        interestedText.toIntOrNull() ?: 0,
                        startedText.toIntOrNull() ?: 0,
                        notesText
                    )
                },
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("submit_final_report_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nộp báo cáo")
            }
        }
    }
}

@Composable
private fun WorkReportObligationCard(
    item: WorkReportObligationEntity,
    showEditAction: Boolean,
    onOpenEdit: () -> Unit
) {
    val badgeColor = when (item.submissionState) {
        "APPROVED" -> SemanticColors.SuccessLight
        "PENDING_REVIEW" -> SemanticColors.RunningLight
        "REJECTED" -> SemanticColors.ErrorLight
        else -> SemanticColors.WarningLight
    }
    val stateLabel = when (item.submissionState) {
        "APPROVED" -> "ĐÃ DUYỆT"
        "PENDING_REVIEW" -> "CHỜ DUYỆT"
        "REJECTED" -> "YÊU CẦU SỬA"
        "DRAFT" -> "BẢN NHÁP"
        else -> "CHƯA BẮT ĐẦU"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.templateName} · Kỳ ${item.periodKey}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Nhân sự: ${item.assigneeDisplayName} · ${item.teamDisplayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Metrics Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricPill("Số cuộc gọi", "${item.callsMade}", "cuộc")
                MetricPill("ƯV quan tâm", "${item.interestedCandidates}", "người")
                MetricPill("NLĐ đi làm", "${item.workersStarted}", "người")
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = "Nội dung công việc: ${item.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (item.reviewerName != null) {
                Text(
                    text = "Phản hồi duyệt (${item.reviewerName}): ${item.reviewReason ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor
                )
            }

            if (showEditAction) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onOpenEdit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("open_edit_report_${item.obligationId}")
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cập nhật & Nộp báo cáo")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewInboxItemCard(
    item: WorkReportObligationEntity,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    var reviewNote by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${item.assigneeDisplayName} · Kỳ ${item.periodKey}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${item.teamDisplayName} · Nộp lúc: ${item.submittedAtIso?.take(16)?.replace('T', ' ') ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SemanticColors.RunningLight.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Chờ duyệt (Lần ${item.version})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticColors.RunningLight,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricPill("Cuộc gọi", "${item.callsMade}", "cuộc")
                MetricPill("Quan tâm", "${item.interestedCandidates}", "người")
                MetricPill("NLĐ đi làm", "${item.workersStarted}", "người")
            }

            Text(
                text = "Ghi chú công việc: ${item.notes}",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = reviewNote,
                onValueChange = { reviewNote = it },
                label = { Text("Nhận xét phê duyệt / lý do trả lại") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onReject(reviewNote) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yêu cầu sửa")
                }
                Button(
                    onClick = { onApprove(reviewNote) },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticColors.SuccessLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phê duyệt", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WorkReportAnalyticsSummaryCard(reports: List<WorkReportObligationEntity>) {
    val totalCalls = reports.sumOf { it.callsMade }
    val totalInterested = reports.sumOf { it.interestedCandidates }
    val totalStarted = reports.sumOf { it.workersStarted }
    val completedCount = reports.count { it.submissionState in listOf("APPROVED", "PENDING_REVIEW") }
    val compliancePct = if (reports.isNotEmpty()) (completedCount * 100 / reports.size) else 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tổng hợp Kết quả Báo cáo Công việc",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Tổng hợp số liệu báo cáo hàng ngày theo từng nhóm và tỷ lệ hoàn thành báo cáo đúng hạn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tỷ lệ hoàn thành nghĩa vụ báo cáo", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$completedCount / ${reports.size} báo cáo đã nộp",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "$compliancePct%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            val byTeam = reports.groupBy { it.teamDisplayName }
            byTeam.forEach { (teamName, list) ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(teamName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "• Tổng cuộc gọi: ${list.sumOf { it.callsMade }} cuộc  |  ƯV quan tâm: ${list.sumOf { it.interestedCandidates }}  |  NLĐ đi làm: ${list.sumOf { it.workersStarted }} người",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricPill("TỔNG CUỘC GỌI", "$totalCalls", "cuộc")
                MetricPill("TỔNG QUAN TÂM", "$totalInterested", "ứng viên")
                MetricPill("TỔNG ĐI LÀM", "$totalStarted", "NLĐ")
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyReportBanner(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AssignmentTurnedIn,
                contentDescription = null,
                tint = SemanticColors.SuccessLight,
                modifier = Modifier.size(32.dp)
            )
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
