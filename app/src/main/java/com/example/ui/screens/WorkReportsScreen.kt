package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActor
import com.example.data.DirectEntryContract
import com.example.data.WorkReportAnalyticsSnapshot
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportReviewItem
import com.example.data.WorkReportSectionSpec
import com.example.data.WorkReportTemplateOption
import com.example.data.WorkReportsContract
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import org.json.JSONArray
import org.json.JSONObject

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
        reports.filter {
            it.obligationStatus == "ACTIVE" &&
                it.submissionState in listOf("NOT_STARTED", "DRAFT", "REJECTED")
        }
    }
    val historyObligations = remember(reports) {
        reports.filter { it.submissionState != "NOT_STARTED" }
    }

    val tabDefs = buildList {
        add("active" to "Cần nộp (${activeObligations.size})")
        add("history" to "Đã lưu / Đã nộp (${historyObligations.size})")
        if (canReview) {
            add("review" to "Chờ duyệt (${reviewQueue.size})")
        }
        if (canViewAnalytics) {
            add("analytics" to "Tổng hợp báo cáo")
        }
    }
    val safeTab = selectedTab.coerceIn(0, (tabDefs.size - 1).coerceAtLeast(0))
    val currentTabKey = tabDefs.getOrNull(safeTab)?.first ?: "active"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("work_reports_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BÁO CÁO CÔNG VIỆC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { viewModel.refreshWorkReportsData(actor) },
                            enabled = !loading && !mutationBusy,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Làm mới", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(
                        text = "Báo cáo định kỳ",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Điền báo cáo công việc được giao, duyệt báo cáo nhóm và theo dõi kết quả tổng hợp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = safeTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                indicator = {},
                divider = {}
            ) {
                tabDefs.forEachIndexed { index, (key, title) ->
                    Tab(
                        selected = safeTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (safeTab == index)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .testTag("work_reports_subtab_$key"),
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (safeTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (safeTab == index)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        if (loading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Đang tải dữ liệu báo cáo…")
                    }
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
                val isEditableObligation = target.obligationStatus == "ACTIVE" &&
                    target.submissionState in listOf("NOT_STARTED", "DRAFT", "REJECTED")
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
                if (activeObligations.isEmpty() && !loading) {
                    item {
                        EmptyReportBanner(
                            title = "Bạn đã hoàn thành hết báo cáo đến hạn",
                            subtitle = "Khi có kỳ báo cáo mới cần nộp, biểu mẫu sẽ hiển thị tại đây."
                        )
                    }
                } else {
                    items(activeObligations, key = { it.obligationId }) { item ->
                        WorkReportObligationCard(
                            item = item,
                            showEditAction = item.obligationStatus == "ACTIVE",
                            onOpenEdit = { viewModel.openObligationEditor(item) }
                        )
                    }
                }
            }

            "history" -> {
                if (historyObligations.isEmpty() && !loading) {
                    item {
                        EmptyReportBanner(
                            title = "Chưa có báo cáo đã lưu hoặc đã nộp",
                            subtitle = "Các bản nháp và báo cáo bạn đã gửi sẽ được lưu tại đây."
                        )
                    }
                } else {
                    items(historyObligations, key = { it.obligationId }) { item ->
                        WorkReportObligationCard(
                            item = item,
                            showEditAction = item.obligationStatus == "ACTIVE" &&
                                item.submissionState in listOf("DRAFT", "REJECTED"),
                            onOpenEdit = { viewModel.openObligationEditor(item) }
                        )
                    }
                }
            }

            "review" -> {
                if (reviewQueue.isEmpty() && !loading) {
                    item {
                        EmptyReportBanner(
                            title = "Không có báo cáo chờ duyệt",
                            subtitle = "Hiện chưa có báo cáo nào từ thành viên trong nhóm cần bạn phê duyệt."
                        )
                    }
                } else {
                    items(reviewQueue, key = { it.obligationId }) { item ->
                        ReviewInboxItemCard(
                            item = item,
                            onApprove = { reason ->
                                if (onReviewDecisionAction != null) {
                                    onReviewDecisionAction(item, true, reason)
                                } else {
                                    viewModel.decideWorkReportReview(item, true, reason)
                                }
                            },
                            onReject = { reason ->
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
                                viewModel.loadWorkReportAnalytics(
                                    templateId = tid,
                                    templateNameHint = tname,
                                    groupBy = activeGroupBy,
                                    finality = activeFinality
                                )
                            },
                            onSelectFinality = { nextFinality ->
                                if (activeTemplateId.isNotBlank() && nextFinality != activeFinality) {
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
    val fieldValues = remember(obligation.obligationId, obligation.payloadJsonRaw) {
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
            text = "${obligation.templateName.ifBlank { "Báo cáo công việc" }} · Kỳ ${WorkReportsContract.formatPeriodKeyVi(obligation.periodKey)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (sections.isEmpty()) {
            Text(
                text = "Đang tải nội dung biểu mẫu báo cáo…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sections.forEach { section ->
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                section.fields.forEach { field ->
                    val currentVal = fieldValues[field.fieldKey].orEmpty()
                    val labelWithReq = if (field.required) "${field.label} *" else field.label
                    when (field.fieldType) {
                        "boolean" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(labelWithReq, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = currentVal.equals("true", ignoreCase = true),
                                    onCheckedChange = { checked ->
                                        fieldValues[field.fieldKey] = checked.toString()
                                    }
                                )
                            }
                        }

                        "single_select" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(labelWithReq, style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    field.options.forEach { opt ->
                                        FilterChip(
                                            selected = currentVal == opt.value,
                                            onClick = { fieldValues[field.fieldKey] = opt.value },
                                            label = { Text(opt.label) }
                                        )
                                    }
                                }
                            }
                        }

                        "multi_select" -> {
                            val selectedSet = currentVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(labelWithReq, style = MaterialTheme.typography.labelMedium)
                                field.options.forEach { opt ->
                                    val isChecked = opt.value in selectedSet
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                val updated = if (checked) selectedSet + opt.value else selectedSet - opt.value
                                                fieldValues[field.fieldKey] = updated.joinToString(",")
                                            }
                                        )
                                        Text(opt.label, style = MaterialTheme.typography.bodySmall)
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

        formValidationError?.let { errText ->
            Text(
                text = errText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

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
            if (canSubmit) {
                OutlinedButton(
                    onClick = {
                        formValidationError = null
                        onSaveDraft(buildPayloadJson(strictForSubmit = false))
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
                        formValidationError = null
                        runCatching { buildPayloadJson(strictForSubmit = true) }
                            .onSuccess { payload -> onSubmitFinal(payload) }
                            .onFailure { err -> formValidationError = err.message ?: "Dữ liệu biểu mẫu chưa hợp lệ." }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("submit_final_report_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nộp báo cáo")
                }
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
    val badgeColor = submissionStateColor(item.submissionState)
    val stateLabel = WorkReportsContract.submissionStateDisplayLabel(item.submissionState)
    val actionLabel = when (item.submissionState) {
        "DRAFT" -> "Tiếp tục điền"
        "REJECTED" -> "Sửa & nộp lại"
        else -> "Làm báo cáo"
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
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.templateName.ifBlank { "Báo cáo công việc" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Kỳ báo cáo: ${WorkReportsContract.formatPeriodKeyVi(item.periodKey)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Hạn nộp: ${WorkReportsContract.formatIsoDateTimeVi(item.dueAtIso)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!item.submittedAtIso.isNullOrBlank()) {
                        Text(
                            text = "Đã gửi lúc: ${WorkReportsContract.formatIsoDateTimeVi(item.submittedAtIso)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!item.latestReviewReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Ghi chú người duyệt: ${item.latestReviewReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                        modifier = Modifier.testTag("open_edit_report_${item.obligationId}")
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(actionLabel)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val teamSuffix = item.teamDisplayName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    Text(
                        text = "${item.assigneeDisplayName}$teamSuffix",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${item.templateName.ifBlank { "Báo cáo công việc" }} · Kỳ ${WorkReportsContract.formatPeriodKeyVi(item.periodKey)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Nộp lúc: ${WorkReportsContract.formatIsoDateTimeVi(item.submittedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val statusTint = if (item.isLate) SemanticColors.WarningLight else SemanticColors.RunningLight
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusTint.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (item.isLate) "Nộp muộn" else "Chờ duyệt",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusTint,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (item.submissionVersion > 1) {
                        Text(
                            text = "Lần gửi ${item.submissionVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.payloadSummary.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.payloadSummary.forEach { (k, v) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
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
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
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
                    Text("Đồng ý duyệt", color = Color.White)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = availableTemplates.first().displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
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
                        onClick = { onSelectFinality("final") },
                        label = { Text("Đã chốt (Đã duyệt)") }
                    )
                    FilterChip(
                        selected = selectedFinality == "provisional_included",
                        enabled = enabled,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

            // Highlight Banner: Tiến độ nộp báo cáo (single-line percentage, clear Vietnamese)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
                Text(
                    text = WorkReportsContract.formatPercentVi(snapshot.complianceRatePct),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Clear 6-tile KPI Grid synced with App BI
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
                HorizontalDivider()
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMono,
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
                HorizontalDivider()
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
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp)
                                )
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = WorkReportsContract.formatPercentVi(row.complianceRatePct),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = JetBrainsMono,
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
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.overdueMissingCount > 0)
                                    SemanticColors.ErrorLight
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (row.metricsSummary.isNotEmpty()) {
                                Text(
                                    text = row.metricsSummary.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (snapshot.contributions.isNotEmpty()) {
                HorizontalDivider()
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val teamPart = contrib.teamDisplayName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                                Text(
                                    text = "${contrib.assigneeDisplayName}$teamPart",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val submittedPart = contrib.submittedAt?.let {
                                    " · Nộp ${WorkReportsContract.formatIsoDateTimeVi(it)}"
                                }.orEmpty()
                                Text(
                                    text = "Kỳ ${WorkReportsContract.formatPeriodKeyVi(contrib.periodKey)}$submittedPart",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = stateColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = stateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = stateColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
            style = MaterialTheme.typography.titleMedium,
            fontFamily = JetBrainsMono,
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
