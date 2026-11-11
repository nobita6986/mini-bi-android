package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActor
import com.example.data.AttendanceExplanationContract
import com.example.data.AttendanceTimeContract
import com.example.data.DirectEntryContract
import com.example.data.LeaveContract
import com.example.data.WorkReportAnalyticsSnapshot
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportReviewItem
import com.example.data.WorkReportSectionSpec
import com.example.data.WorkReportTemplateOption
import com.example.data.WorkReportsContract
import com.example.ui.MiniBiViewModel
import com.example.ui.components.HrpDesignTokens
import com.example.ui.components.HrpHaptics
import com.example.ui.components.HrpInitialsAvatar
import com.example.ui.components.HrpSegmentedTabGroup
import com.example.ui.components.HrpSegmentedTabItem
import com.example.ui.components.HrpSkeletonCard
import com.example.ui.components.HrpStatusBadge
import com.example.ui.components.HrpWeekStripDateSelector
import com.example.ui.components.hrpCardSurface
import com.example.ui.components.hrpMutedTile
import com.example.ui.theme.SemanticColors
import com.example.ui.theme.tabularNums
import org.json.JSONArray
import org.json.JSONObject

private val WORK_REPORT_ISO_DATE_REGEX = Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b")

private fun extractVietnamIsoDateOrNull(rawIso: String?): String? {
    val trimmed = rawIso?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val hcmDate = runCatching {
        val ms = AttendanceTimeContract.parseIsoInstantToEpochMillis(trimmed, "work_report_iso")
        AttendanceTimeContract.formatEpochMillisInHcmIso(ms).take(10)
    }.getOrNull()
    if (hcmDate != null && WORK_REPORT_ISO_DATE_REGEX.matches(hcmDate)) {
        return hcmDate
    }
    return WORK_REPORT_ISO_DATE_REGEX.find(trimmed)?.value
}

internal fun matchesObligationFilterDate(
    item: WorkReportObligationEntity,
    filterDateIso: String
): Boolean {
    if (item.periodKey.contains(filterDateIso)) return true
    if (item.dueAtIso.startsWith(filterDateIso)) return true
    if (extractVietnamIsoDateOrNull(item.dueAtIso) == filterDateIso) return true
    if (item.submittedAtIso?.startsWith(filterDateIso) == true) return true
    if (extractVietnamIsoDateOrNull(item.submittedAtIso) == filterDateIso) return true
    return false
}

private fun matchesReviewItemFilterDate(
    item: WorkReportReviewItem,
    filterDateIso: String
): Boolean {
    if (item.periodKey.contains(filterDateIso)) return true
    if (item.submittedAt?.startsWith(filterDateIso) == true) return true
    if (extractVietnamIsoDateOrNull(item.submittedAt) == filterDateIso) return true
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkReportsScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onBackToDashboard: () -> Unit,
    onSaveDraftAction: ((WorkReportObligationEntity, JSONObject) -> Unit)? = null,
    onSubmitFinalAction: ((WorkReportObligationEntity, JSONObject) -> Unit)? = null,
    onReviewDecisionAction: ((WorkReportReviewItem, Boolean, String) -> Unit)? = null
) {
    BackHandler(onBack = onBackToDashboard)

    val haptic = LocalHapticFeedback.current
    val reports by viewModel.workReports.collectAsStateWithLifecycle()
    val loading by viewModel.workReportsLoading.collectAsStateWithLifecycle()
    val error by viewModel.workReportsError.collectAsStateWithLifecycle()
    val reviewQueue by viewModel.reviewQueue.collectAsStateWithLifecycle()
    val analyticsSnapshot by viewModel.analyticsSnapshot.collectAsStateWithLifecycle()
    val serverTemplates by viewModel.availableReportTemplates.collectAsStateWithLifecycle()
    val editingObligation by viewModel.editingObligation.collectAsStateWithLifecycle()
    val mutationBusy by viewModel.workReportMutationBusy.collectAsStateWithLifecycle()

    LaunchedEffect(actor.appUserId) {
        viewModel.refreshWorkReportsData(actor)
    }

    val hasTeamOrAllScope = actor.hasScopeKind("team") || actor.hasScopeKind("all")
    val canReview = actor.hasCapability("report_review") && hasTeamOrAllScope
    val canViewAnalytics = actor.hasCapability("report_analytics_view") && hasTeamOrAllScope

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilterDate by remember { mutableStateOf<String?>(null) }
    val todayIso = remember { AttendanceExplanationContract.todayVietnamIsoDate() }

    val availableTemplates = remember(reports, reviewQueue, serverTemplates, analyticsSnapshot) {
        val map = linkedMapOf<String, String>()
        reports.forEach {
            if (it.templateId.isNotBlank() && it.templateName.isNotBlank()) {
                map[it.templateId] = it.templateName
            }
        }
        reviewQueue.forEach {
            if (it.templateId.isNotBlank() && it.templateName.isNotBlank()) {
                map.putIfAbsent(it.templateId, it.templateName)
            }
        }
        serverTemplates.forEach {
            if (it.templateId.isNotBlank() && it.displayName.isNotBlank()) {
                map.putIfAbsent(it.templateId, it.displayName)
            }
        }
        analyticsSnapshot?.let { snap ->
            if (snap.templateId.isNotBlank() && snap.templateName.isNotBlank()) {
                map.putIfAbsent(snap.templateId, snap.templateName)
            }
        }
        map.map { (tid, name) -> WorkReportTemplateOption(templateId = tid, displayName = name) }
    }

    val activeObligations = remember(reports) {
        reports.filter { WorkReportsContract.isActionableObligation(it) }
    }
    val historyObligations = remember(reports) {
        reports.filter { WorkReportsContract.isHistoryObligation(it) }
    }

    val filteredActiveObligations = remember(activeObligations, selectedFilterDate) {
        val filterDate = selectedFilterDate
        if (filterDate == null) activeObligations
        else activeObligations.filter { matchesObligationFilterDate(it, filterDate) }
    }
    val filteredHistoryObligations = remember(historyObligations, selectedFilterDate) {
        val filterDate = selectedFilterDate
        if (filterDate == null) historyObligations
        else historyObligations.filter { matchesObligationFilterDate(it, filterDate) }
    }
    val filteredReviewQueue = remember(reviewQueue, selectedFilterDate) {
        val filterDate = selectedFilterDate
        if (filterDate == null) reviewQueue
        else reviewQueue.filter { matchesReviewItemFilterDate(it, filterDate) }
    }

    val tabItems = buildList {
        add(
            HrpSegmentedTabItem(
                key = "active",
                label = "Cần nộp",
                testTag = "work_reports_subtab_obligations",
                badgeCount = activeObligations.size,
                showZeroBadge = true
            )
        )
        add(
            HrpSegmentedTabItem(
                key = "history",
                label = "Đã nộp",
                testTag = "work_reports_subtab_history",
                badgeCount = historyObligations.size,
                showZeroBadge = true
            )
        )
        if (canReview) {
            add(
                HrpSegmentedTabItem(
                    key = "review",
                    label = "Chờ duyệt",
                    testTag = "work_reports_subtab_review",
                    badgeCount = reviewQueue.size,
                    showZeroBadge = true
                )
            )
        }
        if (canViewAnalytics) {
            add(
                HrpSegmentedTabItem(
                    key = "analytics",
                    label = "Tổng hợp",
                    testTag = "work_reports_subtab_analytics"
                )
            )
        }
    }
    val safeTab = selectedTab.coerceIn(0, (tabItems.size - 1).coerceAtLeast(0))
    val currentTabKey = tabItems.getOrNull(safeTab)?.key ?: "active"

    val currentTabMarkedDates = remember(currentTabKey, activeObligations, historyObligations, reviewQueue) {
        val dates = linkedSetOf<String>()
        when (currentTabKey) {
            "active" -> activeObligations.forEach { item ->
                WORK_REPORT_ISO_DATE_REGEX.find(item.periodKey)?.value?.let { dates.add(it) }
                extractVietnamIsoDateOrNull(item.dueAtIso)?.let { dates.add(it) }
            }
            "history" -> historyObligations.forEach { item ->
                WORK_REPORT_ISO_DATE_REGEX.find(item.periodKey)?.value?.let { dates.add(it) }
                extractVietnamIsoDateOrNull(item.dueAtIso)?.let { dates.add(it) }
            }
            "review" -> reviewQueue.forEach { item ->
                WORK_REPORT_ISO_DATE_REGEX.find(item.periodKey)?.value?.let { dates.add(it) }
                extractVietnamIsoDateOrNull(item.submittedAt)?.let { dates.add(it) }
            }
        }
        dates
    }

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { viewModel.refreshWorkReportsData(actor) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("work_reports_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HrpSegmentedTabGroup(
                        items = tabItems,
                        selectedKey = currentTabKey,
                        onSelect = { key ->
                            val idx = tabItems.indexOfFirst { it.key == key }
                            if (idx >= 0) {
                                selectedTab = idx
                            }
                        },
                        maxItemsPerRow = 3,
                        tabHeight = if (tabItems.size >= 4) 32.dp else 34.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("work_reports_tab_grid")
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SemanticColors.SurfaceLight,
                        shadowElevation = 0.5.dp,
                        border = BorderStroke(1.dp, SemanticColors.SurfaceBorder)
                    ) {
                        IconButton(
                            onClick = {
                                HrpHaptics.tick(haptic)
                                viewModel.refreshWorkReportsData(actor)
                            },
                            enabled = !loading && !mutationBusy,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("work_reports_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Làm mới báo cáo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            if (currentTabKey != "analytics") {
                item {
                    HrpWeekStripDateSelector(
                        selectedDateIso = selectedFilterDate ?: todayIso,
                        todayIso = todayIso,
                        allowFutureDates = true,
                        showAllOption = true,
                        allDatesSelected = selectedFilterDate == null,
                        markedDates = currentTabMarkedDates,
                        tagPrefix = "work_reports_week_strip",
                        onSelectDate = { picked ->
                            selectedFilterDate = picked
                        },
                        onSelectAllDates = {
                            selectedFilterDate = null
                        }
                    )
                }
            }

            if (loading && reports.isEmpty() && reviewQueue.isEmpty() && analyticsSnapshot == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HrpSkeletonCard(lines = 3)
                        HrpSkeletonCard(lines = 3)
                    }
                }
            }

            error?.let { errMsg ->
                item {
                    EmptyReportBanner(
                        title = "Không thể tải dữ liệu báo cáo",
                        subtitle = errMsg
                    )
                }
            }

            editingObligation?.let { target ->
                item {
                    val sections = remember(target.schemaJsonRaw) {
                        parseDynamicSections(target.schemaJsonRaw)
                    }
                    val isEditableObligation = WorkReportsContract.isEditableObligation(target)
                    DynamicWorkReportFormCard(
                        obligation = target,
                        sections = sections,
                        canSubmit = isEditableObligation && !mutationBusy,
                        onCancel = { viewModel.closeObligationEditor() },
                        onSaveDraft = { payload ->
                            if (onSaveDraftAction != null) {
                                onSaveDraftAction(target, payload)
                                viewModel.closeObligationEditor()
                            } else {
                                viewModel.saveWorkReportDraft(target, payload)
                            }
                        },
                        onSubmitFinal = { payload ->
                            if (onSubmitFinalAction != null) {
                                onSubmitFinalAction(target, payload)
                                viewModel.closeObligationEditor()
                            } else {
                                viewModel.submitWorkReportFinal(target, payload)
                            }
                        }
                    )
                }
            }

            when (currentTabKey) {
                "active" -> {
                    if (filteredActiveObligations.isEmpty() && !loading) {
                        item {
                            val filterDate = selectedFilterDate
                            if (filterDate != null && activeObligations.isNotEmpty()) {
                                EmptyReportBanner(
                                    title = "Không có báo cáo cần nộp ngày ${LeaveContract.formatIsoDateVi(filterDate)}",
                                    subtitle = "Bấm 'Tất cả' trên thanh lịch tuần để xem toàn bộ báo cáo cần nộp."
                                )
                            } else {
                                EmptyReportBanner(
                                    title = "Bạn đã hoàn thành hết báo cáo đến hạn",
                                    subtitle = "Khi có kỳ báo cáo mới cần nộp, biểu mẫu sẽ hiển thị tại đây."
                                )
                            }
                        }
                    } else {
                        items(filteredActiveObligations, key = { it.obligationId }) { item ->
                            val isOpenInEditor = editingObligation?.obligationId == item.obligationId
                            WorkReportObligationCard(
                                item = item,
                                showEditAction = item.obligationStatus == "ACTIVE",
                                isCurrentlyEditing = isOpenInEditor,
                                onOpenEdit = {
                                    HrpHaptics.tick(haptic)
                                    if (isOpenInEditor) {
                                        viewModel.closeObligationEditor()
                                    } else {
                                        viewModel.openObligationEditor(item)
                                    }
                                }
                            )
                        }
                    }
                }

                "history" -> {
                    if (filteredHistoryObligations.isEmpty() && !loading) {
                        item {
                            val filterDate = selectedFilterDate
                            if (filterDate != null && historyObligations.isNotEmpty()) {
                                EmptyReportBanner(
                                    title = "Không có báo cáo ngày ${LeaveContract.formatIsoDateVi(filterDate)}",
                                    subtitle = "Bấm 'Tất cả' trên thanh lịch tuần để xem toàn bộ lịch sử báo cáo."
                                )
                            } else {
                                EmptyReportBanner(
                                    title = "Chưa có báo cáo đã lưu hoặc đã nộp",
                                    subtitle = "Các bản nháp và báo cáo bạn đã gửi sẽ được lưu tại đây."
                                )
                            }
                        }
                    } else {
                        items(filteredHistoryObligations, key = { it.obligationId }) { item ->
                            val isOpenInEditor = editingObligation?.obligationId == item.obligationId
                            WorkReportObligationCard(
                                item = item,
                                showEditAction = item.isOverdue || (
                                    item.obligationStatus == "ACTIVE" &&
                                        item.submissionState in listOf("DRAFT", "REJECTED")
                                ),
                                isCurrentlyEditing = isOpenInEditor,
                                onOpenEdit = {
                                    HrpHaptics.tick(haptic)
                                    if (isOpenInEditor) {
                                        viewModel.closeObligationEditor()
                                    } else {
                                        viewModel.openObligationEditor(item)
                                    }
                                }
                            )
                        }
                    }
                }

                "review" -> {
                    if (filteredReviewQueue.isEmpty() && !loading) {
                        item {
                            val filterDate = selectedFilterDate
                            if (filterDate != null && reviewQueue.isNotEmpty()) {
                                EmptyReportBanner(
                                    title = "Không có báo cáo chờ duyệt ngày ${LeaveContract.formatIsoDateVi(filterDate)}",
                                    subtitle = "Bấm 'Tất cả' trên thanh lịch tuần để xem toàn bộ hàng đợi duyệt."
                                )
                            } else {
                                EmptyReportBanner(
                                    title = "Không có báo cáo chờ duyệt",
                                    subtitle = "Hiện chưa có báo cáo nào từ thành viên trong nhóm cần bạn phê duyệt."
                                )
                            }
                        }
                    } else {
                        items(filteredReviewQueue, key = { it.obligationId }) { item ->
                            ReviewInboxItemCard(
                                item = item,
                                onApprove = { reason ->
                                    HrpHaptics.tick(haptic)
                                    if (onReviewDecisionAction != null) {
                                        onReviewDecisionAction(item, true, reason)
                                    } else {
                                        viewModel.decideWorkReportReview(item, true, reason)
                                    }
                                },
                                onReject = { reason ->
                                    HrpHaptics.tick(haptic)
                                    if (onReviewDecisionAction != null) {
                                        onReviewDecisionAction(item, false, reason)
                                    } else {
                                        viewModel.decideWorkReportReview(item, false, reason)
                                    }
                                }
                            )
                        }
                    }
                }

                "analytics" -> {
                    if (availableTemplates.isNotEmpty() || analyticsSnapshot != null) {
                        item {
                            val activeTemplateId = analyticsSnapshot?.templateId
                                ?: availableTemplates.firstOrNull()?.templateId.orEmpty()
                            val activeTemplateName = availableTemplates
                                .firstOrNull { it.templateId == activeTemplateId }
                                ?.displayName
                                ?: analyticsSnapshot?.templateName.orEmpty()
                            val activeFinality = analyticsSnapshot?.finality ?: "final"
                            val activeGroupBy = analyticsSnapshot?.groupBy ?: "team"

                            AnalyticsFilterCard(
                                availableTemplates = availableTemplates,
                                selectedTemplateId = activeTemplateId,
                                selectedFinality = activeFinality,
                                selectedGroupBy = activeGroupBy,
                                enabled = !loading,
                                onSelectTemplate = { tid, tname ->
                                    HrpHaptics.tick(haptic)
                                    viewModel.loadWorkReportAnalytics(
                                        templateId = tid,
                                        templateNameHint = tname,
                                        groupBy = activeGroupBy,
                                        finality = activeFinality
                                    )
                                },
                                onSelectFinality = { nextFinality ->
                                    if (activeTemplateId.isNotBlank() && nextFinality != activeFinality) {
                                        HrpHaptics.tick(haptic)
                                        viewModel.loadWorkReportAnalytics(
                                            templateId = activeTemplateId,
                                            templateNameHint = activeTemplateName,
                                            groupBy = activeGroupBy,
                                            finality = nextFinality
                                        )
                                    }
                                },
                                onSelectGroupBy = { nextGroupBy ->
                                    if (activeTemplateId.isNotBlank() && nextGroupBy != activeGroupBy) {
                                        HrpHaptics.tick(haptic)
                                        viewModel.loadWorkReportAnalytics(
                                            templateId = activeTemplateId,
                                            templateNameHint = activeTemplateName,
                                            groupBy = nextGroupBy,
                                            finality = activeFinality
                                        )
                                    }
                                }
                            )
                        }
                    }
                    item {
                        val snap = analyticsSnapshot
                        if (snap == null && !loading) {
                            EmptyReportBanner(
                                title = "Chưa có số liệu tổng hợp",
                                subtitle = "Chưa ghi nhận mẫu báo cáo đang hoạt động trong phạm vi quản lý của bạn."
                            )
                        } else if (snap != null) {
                            WorkReportAnalyticsSummaryCard(snapshot = snap)
                        }
                    }
                }
            }
        }
    }
}

internal fun parseDynamicSections(schemaJsonRaw: String): List<WorkReportSectionSpec> =
    WorkReportsContract.parseDynamicSections(schemaJsonRaw)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DynamicWorkReportFormCard(
    obligation: WorkReportObligationEntity,
    sections: List<WorkReportSectionSpec>,
    canSubmit: Boolean,
    onCancel: () -> Unit,
    onSaveDraft: (JSONObject) -> Unit,
    onSubmitFinal: (JSONObject) -> Unit
) {
    val fieldValues = remember(obligation.obligationId) {
        val map = mutableStateMapOf<String, String>()
        val existing = runCatching { JSONObject(obligation.payloadJsonRaw) }.getOrNull()
        sections.flatMap { it.fields }.forEach { field ->
            val rawVal = existing?.opt(field.fieldKey)
            map[field.fieldKey] = when (rawVal) {
                null, JSONObject.NULL -> field.defaultValue.orEmpty()
                is JSONArray -> buildList {
                    for (i in 0 until rawVal.length()) add(rawVal.optString(i))
                }.joinToString(",")
                else -> rawVal.toString()
            }
        }
        map
    }

    var formValidationError by remember(obligation.obligationId) { mutableStateOf<String?>(null) }

    fun buildPayloadJson(strictForSubmit: Boolean): JSONObject {
        val obj = JSONObject()
        sections.flatMap { it.fields }.forEach { field ->
            val raw = fieldValues[field.fieldKey].orEmpty().trim()
            if (strictForSubmit && field.required && raw.isEmpty() && field.fieldType != "boolean") {
                throw IllegalArgumentException("Vui lòng nhập mục bắt buộc '${field.label}'.")
            }
            when (field.fieldType) {
                "integer" -> if (raw.isNotEmpty()) {
                    val parsed = raw.toLongOrNull()
                    if (parsed != null) {
                        obj.put(field.fieldKey, parsed)
                    } else if (strictForSubmit) {
                        throw IllegalArgumentException("Mục '${field.label}' phải là số nguyên hợp lệ.")
                    }
                }
                "decimal" -> if (raw.isNotEmpty()) {
                    val parsed = raw.replace(',', '.').toDoubleOrNull()
                    if (parsed != null && parsed.isFinite()) {
                        obj.put(field.fieldKey, parsed)
                    } else if (strictForSubmit) {
                        throw IllegalArgumentException("Mục '${field.label}' phải là số hợp lệ.")
                    }
                }
                "date" -> if (raw.isNotEmpty()) {
                    val iso = runCatching { DirectEntryContract.uiDateToIsoDate(raw) }.getOrNull()
                    if (iso != null) {
                        obj.put(field.fieldKey, iso)
                    } else if (strictForSubmit) {
                        throw IllegalArgumentException("Ngày '${field.label}' cần nhập theo dạng DD/MM/YYYY.")
                    }
                }
                "boolean" -> obj.put(field.fieldKey, raw.equals("true", ignoreCase = true))
                "multi_select" -> {
                    val items = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (strictForSubmit && field.required && items.isEmpty()) {
                        throw IllegalArgumentException("Vui lòng chọn ít nhất 1 mục cho '${field.label}'.")
                    }
                    val arr = JSONArray()
                    items.forEach { arr.put(it) }
                    obj.put(field.fieldKey, arr)
                }
                else -> if (raw.isNotEmpty() || field.required) obj.put(field.fieldKey, raw)
            }
        }
        return obj
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface(cornerRadius = HrpDesignTokens.RadiusCard)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                shape = RoundedCornerShape(HrpDesignTokens.RadiusCard)
            )
            .testTag("dynamic_work_report_form_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "Kỳ ${WorkReportsContract.formatPeriodKeyVi(obligation.periodKey)}",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = obligation.templateName.ifBlank { "Báo cáo công việc" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng biểu mẫu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (obligation.isOverdue) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SemanticColors.ErrorLight.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SemanticColors.ErrorLight.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overdue_readonly_form_banner")
                ) {
                    Text(
                        text = "Biểu mẫu đang ở chế độ chỉ xem (quá hạn nộp).",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = SemanticColors.ErrorLight,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            if (sections.isEmpty()) {
                Text(
                    text = "Đang tải nội dung biểu mẫu báo cáo…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sections.forEach { section ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        if (!section.description.isNullOrBlank()) {
                            Text(
                                text = section.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        section.fields.forEach { field ->
                            val currentVal = fieldValues[field.fieldKey].orEmpty()
                            val labelWithReq = if (field.required) "${field.label} *" else field.label
                            when (field.fieldType) {
                                "boolean" -> {
                                    val isChecked = currentVal.equals("true", ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isChecked) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                                        },
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isChecked) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable(enabled = canSubmit) {
                                                formValidationError = null
                                                fieldValues[field.fieldKey] = (!isChecked).toString()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = labelWithReq,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isChecked) FontWeight.Medium else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.testTag("dynamic_label_${field.fieldKey}")
                                                )
                                                if (!field.description.isNullOrBlank()) {
                                                    Text(
                                                        text = field.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Switch(
                                                checked = isChecked,
                                                enabled = canSubmit,
                                                onCheckedChange = { checked ->
                                                    formValidationError = null
                                                    fieldValues[field.fieldKey] = checked.toString()
                                                },
                                                modifier = Modifier.testTag("dynamic_field_${field.fieldKey}")
                                            )
                                        }
                                    }
                                }

                                "single_select" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = labelWithReq,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            field.options.forEach { opt ->
                                                FilterChip(
                                                    selected = currentVal == opt.value,
                                                    enabled = canSubmit,
                                                    onClick = {
                                                        formValidationError = null
                                                        fieldValues[field.fieldKey] = opt.value
                                                    },
                                                    label = { Text(opt.label) }
                                                )
                                            }
                                        }
                                    }
                                }

                                "multi_select" -> {
                                    val selectedSet = currentVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = labelWithReq,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        field.options.forEach { opt ->
                                            val isChecked = opt.value in selectedSet
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable(enabled = canSubmit) {
                                                        formValidationError = null
                                                        val updated = if (!isChecked) selectedSet + opt.value else selectedSet - opt.value
                                                        fieldValues[field.fieldKey] = updated.joinToString(",")
                                                    }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    enabled = canSubmit,
                                                    onCheckedChange = { checked ->
                                                        formValidationError = null
                                                        val updated = if (checked) selectedSet + opt.value else selectedSet - opt.value
                                                        fieldValues[field.fieldKey] = updated.joinToString(",")
                                                    }
                                                )
                                                Text(
                                                    text = opt.label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    val kbType = when (field.fieldType) {
                                        "integer" -> KeyboardType.Number
                                        "decimal" -> KeyboardType.Decimal
                                        else -> KeyboardType.Text
                                    }
                                    OutlinedTextField(
                                        value = currentVal,
                                        enabled = canSubmit,
                                        onValueChange = { next ->
                                            formValidationError = null
                                            val sanitized = when (field.fieldType) {
                                                "integer" -> next.filterIndexed { idx, ch ->
                                                    ch.isDigit() || (idx == 0 && ch == '-')
                                                }.take(20)
                                                "decimal" -> next.replace(',', '.').filterIndexed { idx, ch ->
                                                    ch.isDigit() || ch == '.' || (idx == 0 && ch == '-')
                                                }.take(24)
                                                "date" -> next.filter { ch -> ch.code >= 32 && ch.code != 127 }.take(10)
                                                "textarea" -> next.filter { ch ->
                                                    (ch.code >= 32 && ch.code != 127) || ch == '\n' || ch == '\t'
                                                }.take(4000)
                                                else -> next.filter { ch -> ch.code >= 32 && ch.code != 127 }.take(1000)
                                            }
                                            fieldValues[field.fieldKey] = sanitized
                                        },
                                        label = { Text(labelWithReq) },
                                        placeholder = if (field.fieldType == "date") {
                                            { Text("DD/MM/YYYY") }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = field.fieldType != "textarea",
                                        minLines = if (field.fieldType == "textarea") 2 else 1,
                                        keyboardOptions = KeyboardOptions(keyboardType = kbType),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("dynamic_field_${field.fieldKey}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            formValidationError?.let { errText ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (canSubmit) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            formValidationError = null
                            runCatching { buildPayloadJson(strictForSubmit = true) }
                                .onSuccess { payload -> onSubmitFinal(payload) }
                                .onFailure { err ->
                                    formValidationError = err.message ?: "Dữ liệu biểu mẫu chưa hợp lệ."
                                }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("submit_final_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nộp báo cáo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                formValidationError = null
                                onSaveDraft(buildPayloadJson(strictForSubmit = false))
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("save_draft_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lưu nháp",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("cancel_edit_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Đóng",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("cancel_edit_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Đóng",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkReportObligationCard(
    item: WorkReportObligationEntity,
    showEditAction: Boolean,
    isCurrentlyEditing: Boolean = false,
    onOpenEdit: () -> Unit
) {
    val isOverdueBadge = item.isOverdue &&
        item.submissionState.trim().uppercase() in setOf("NOT_STARTED", "DRAFT")
    val badgeColor = if (isOverdueBadge) {
        SemanticColors.ErrorLight
    } else {
        submissionStateColor(item.submissionState)
    }
    val stateLabel = WorkReportsContract.obligationBadgeDisplayLabel(item)
    val actionLabel = when {
        isCurrentlyEditing -> "Thu gọn biểu mẫu"
        item.isOverdue && item.submissionState == "DRAFT" -> "Xem bản nháp"
        item.isOverdue -> "Xem nội dung"
        item.submissionState == "DRAFT" -> "Tiếp tục điền"
        item.submissionState == "REJECTED" -> "Sửa & nộp lại"
        else -> "Làm báo cáo"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HrpDesignTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = item.templateName.ifBlank { "Báo cáo công việc" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kỳ báo cáo: ${WorkReportsContract.formatPeriodKeyVi(item.periodKey)}",
                        style = MaterialTheme.typography.bodySmall.tabularNums(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Hạn nộp: ${WorkReportsContract.formatIsoDateTimeVi(item.dueAtIso)}",
                        style = MaterialTheme.typography.bodySmall.tabularNums(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!item.submittedAtIso.isNullOrBlank()) {
                        Text(
                            text = "Đã gửi lúc: ${WorkReportsContract.formatIsoDateTimeVi(item.submittedAtIso)}",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HrpStatusBadge(
                    text = stateLabel,
                    tint = badgeColor
                )
            }

            if (!item.latestReviewReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ghi chú người duyệt: ${item.latestReviewReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (showEditAction) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onOpenEdit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("open_edit_report_${item.obligationId}")
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyEditing) Icons.Default.Close else Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = actionLabel,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewInboxItemCard(
    item: WorkReportReviewItem,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    var reviewNote by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HrpDesignTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HrpInitialsAvatar(
                        fullName = item.assigneeDisplayName,
                        size = 40.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val teamSuffix = item.teamDisplayName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                        Text(
                            text = "${item.assigneeDisplayName}$teamSuffix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.templateName.ifBlank { "Báo cáo công việc" }} · Kỳ ${WorkReportsContract.formatPeriodKeyVi(item.periodKey)}",
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Nộp lúc: ${WorkReportsContract.formatIsoDateTimeVi(item.submittedAt)}",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val statusTint = if (item.isLate) SemanticColors.WarningLight else SemanticColors.RunningLight
                    HrpStatusBadge(
                        text = if (item.isLate) "Nộp muộn" else "Chờ duyệt",
                        tint = statusTint
                    )
                    if (item.submissionVersion > 1) {
                        Text(
                            text = "Lần gửi ${item.submissionVersion}",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.payloadSummary.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hrpMutedTile(cornerRadius = 12.dp)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item.payloadSummary.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = reviewNote,
                onValueChange = { next ->
                    reviewNote = next.filter { ch -> ch.code >= 32 && ch.code != 127 }.take(2000)
                },
                label = { Text("Nhận xét hoặc lý do yêu cầu sửa (bắt buộc khi trả lại)") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onReject(reviewNote) },
                    enabled = reviewNote.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SemanticColors.ErrorLight
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Yêu cầu sửa",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { onApprove(reviewNote) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SemanticColors.SuccessLight,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Đồng ý duyệt",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalyticsFilterCard(
    availableTemplates: List<WorkReportTemplateOption>,
    selectedTemplateId: String,
    selectedFinality: String,
    selectedGroupBy: String,
    enabled: Boolean,
    onSelectTemplate: (String, String) -> Unit,
    onSelectFinality: (String) -> Unit,
    onSelectGroupBy: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HrpDesignTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (availableTemplates.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Mẫu báo cáo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableTemplates.forEach { opt ->
                            FilterChip(
                                selected = selectedTemplateId == opt.templateId,
                                enabled = enabled,
                                shape = CircleShape,
                                onClick = { onSelectTemplate(opt.templateId, opt.displayName) },
                                label = { Text(opt.displayName) }
                            )
                        }
                    }
                }
            } else if (availableTemplates.size == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mẫu báo cáo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HrpStatusBadge(
                        text = availableTemplates.first().displayName,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Loại số liệu",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFinality == "final",
                        enabled = enabled,
                        shape = CircleShape,
                        onClick = { onSelectFinality("final") },
                        label = { Text("Đã chốt (Đã duyệt)") }
                    )
                    FilterChip(
                        selected = selectedFinality == "provisional_included",
                        enabled = enabled,
                        shape = CircleShape,
                        onClick = { onSelectFinality("provisional_included") },
                        label = { Text("Tạm tính (Gồm chờ duyệt)") }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Xem chi tiết theo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "team" to "Theo nhóm",
                        "assignee" to "Theo nhân viên",
                        "period" to "Theo kỳ"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedGroupBy == key,
                            enabled = enabled,
                            shape = CircleShape,
                            onClick = { onSelectGroupBy(key) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkReportAnalyticsSummaryCard(snapshot: WorkReportAnalyticsSnapshot) {
    val scopeDescription = buildList {
        if (snapshot.scopeLabel.isNotBlank()) add("Phạm vi: ${snapshot.scopeLabel}")
        add(if (snapshot.finality == "provisional_included") "Số liệu tạm tính" else "Số liệu đã chốt")
    }.joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HrpDesignTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = snapshot.templateName.ifBlank { "Báo cáo định kỳ" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (scopeDescription.isNotBlank()) {
                    Text(
                        text = scopeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Tiến độ nộp báo cáo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Đã hoàn thành ${snapshot.submittedCount} / ${snapshot.totalObligations} lượt báo cáo được giao",
                        style = MaterialTheme.typography.bodySmall.tabularNums(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
                Text(
                    text = WorkReportsContract.formatPercentVi(snapshot.complianceRatePct),
                    style = MaterialTheme.typography.headlineSmall.tabularNums(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComplianceStatTile(
                        label = "Phải nộp",
                        value = snapshot.totalObligations.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    ComplianceStatTile(
                        label = "Đã hoàn thành",
                        value = snapshot.submittedCount.toString(),
                        accentColor = SemanticColors.SuccessLight,
                        modifier = Modifier.weight(1f)
                    )
                    ComplianceStatTile(
                        label = "Đúng hạn",
                        value = snapshot.approvedCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComplianceStatTile(
                        label = "Quá hạn chưa nộp",
                        value = snapshot.overdueMissingCount.toString(),
                        accentColor = if (snapshot.overdueMissingCount > 0) SemanticColors.ErrorLight else null,
                        modifier = Modifier.weight(1f)
                    )
                    ComplianceStatTile(
                        label = "Chờ duyệt",
                        value = snapshot.pendingReviewCount.toString(),
                        accentColor = if (snapshot.pendingReviewCount > 0) SemanticColors.RunningLight else null,
                        modifier = Modifier.weight(1f)
                    )
                    ComplianceStatTile(
                        label = "Nộp muộn",
                        value = snapshot.lateSubmittedCount.toString(),
                        accentColor = if (snapshot.lateSubmittedCount > 0) SemanticColors.WarningLight else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (snapshot.metrics.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "Chỉ tiêu công việc tổng hợp",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.metrics.forEach { metric ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hrpMutedTile(cornerRadius = 12.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = metric.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                metric.subtitleDisplay?.let { sub ->
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = metric.valueDisplay,
                                style = MaterialTheme.typography.titleMedium.tabularNums(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (snapshot.breakdownRows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val breakdownTitle = when (snapshot.groupBy) {
                    "assignee" -> "Chi tiết theo nhân viên"
                    "period" -> "Chi tiết theo kỳ báo cáo"
                    else -> "Chi tiết theo nhóm"
                }
                Text(
                    text = breakdownTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.breakdownRows.forEach { row ->
                        val displayLabel = if (snapshot.groupBy == "period") {
                            WorkReportsContract.formatPeriodKeyVi(row.groupLabel)
                        } else {
                            row.groupLabel
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hrpMutedTile(cornerRadius = 12.dp)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = WorkReportsContract.formatPercentVi(row.complianceRatePct),
                                    style = MaterialTheme.typography.labelLarge.tabularNums(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            val countsInfo = buildString {
                                append("Đã hoàn thành ${row.submittedCount} / ${row.obligationCount} phải nộp")
                                if (row.overdueMissingCount > 0) {
                                    append(" · Quá hạn ${row.overdueMissingCount}")
                                }
                            }
                            Text(
                                text = countsInfo,
                                style = MaterialTheme.typography.bodySmall.tabularNums(),
                                color = if (row.overdueMissingCount > 0)
                                    SemanticColors.ErrorLight
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (row.metricsSummary.isNotEmpty()) {
                                Text(
                                    text = row.metricsSummary.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall.tabularNums(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (snapshot.contributions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "Báo cáo đã nộp gần đây",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.contributions.take(10).forEach { contrib ->
                        val stateColor = submissionStateColor(contrib.submissionState)
                        val stateLabel = WorkReportsContract.submissionStateDisplayLabel(contrib.submissionState) +
                            if (contrib.isLate) " · Muộn" else ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hrpMutedTile(cornerRadius = 10.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HrpInitialsAvatar(
                                    fullName = contrib.assigneeDisplayName,
                                    size = 32.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val teamPart = contrib.teamDisplayName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                                    Text(
                                        text = "${contrib.assigneeDisplayName}$teamPart",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val submittedPart = contrib.submittedAt?.let {
                                        " · Nộp ${WorkReportsContract.formatIsoDateTimeVi(it)}"
                                    }.orEmpty()
                                    Text(
                                        text = "Kỳ ${WorkReportsContract.formatPeriodKeyVi(contrib.periodKey)}$submittedPart",
                                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HrpStatusBadge(
                                text = stateLabel,
                                tint = stateColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComplianceStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    Column(
        modifier = modifier
            .hrpMutedTile(cornerRadius = 12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabularNums(),
            fontWeight = FontWeight.Bold,
            color = accentColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun submissionStateColor(stateCode: String): Color = when (stateCode.trim().uppercase()) {
    "APPROVED", "COMPLETED" -> SemanticColors.SuccessLight
    "PENDING_REVIEW", "SUBMITTED" -> SemanticColors.RunningLight
    "REJECTED" -> SemanticColors.ErrorLight
    else -> SemanticColors.WarningLight
}

@Composable
private fun EmptyReportBanner(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
