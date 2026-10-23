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
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActor
import com.example.data.WorkReportAnalyticsSnapshot
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportReviewItem
import com.example.data.WorkReportSectionSpec
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

    val canReview = actor.hasCapability("report_review")
    val canViewAnalytics = actor.hasCapability("report_analytics_view")
    val canSubmit = actor.hasCapability("report_submit")

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingObligation by remember { mutableStateOf<WorkReportObligationEntity?>(null) }

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
        add("active" to "Cần báo cáo (${activeObligations.size})")
        add("history" to "Lịch sử báo cáo (${historyObligations.size})")
        if (canReview) {
            add("review" to "Duyệt báo cáo (${reviewQueue.size})")
        }
        if (canViewAnalytics) {
            add("analytics" to "Tổng hợp kết quả")
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
                    Text(
                        text = "BÁO CÁO CÔNG VIỆC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Nghĩa vụ & Phê duyệt Báo cáo",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Biểu mẫu được kết xuất động theo cấu hình mẫu báo cáo từ máy chủ.",
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
                        Text("Đang đồng bộ danh sách nghĩa vụ báo cáo…")
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
                DynamicWorkReportFormCard(
                    obligation = target,
                    sections = sections,
                    canSubmit = canSubmit,
                    onCancel = { editingObligation = null },
                    onSaveDraft = { payload ->
                        onSaveDraftAction?.invoke(target, payload)
                        editingObligation = null
                    },
                    onSubmitFinal = { payload ->
                        onSubmitFinalAction?.invoke(target, payload)
                        editingObligation = null
                    }
                )
            }
        }

        when (currentTabKey) {
            "active" -> {
                if (activeObligations.isEmpty() && !loading) {
                    item {
                        EmptyReportBanner(
                            title = "Chưa có nghĩa vụ báo cáo cần thực hiện",
                            subtitle = "Các nghĩa vụ báo cáo do máy chủ phân công sẽ xuất hiện tại đây."
                        )
                    }
                } else {
                    items(activeObligations, key = { it.obligationId }) { item ->
                        WorkReportObligationCard(
                            item = item,
                            showEditAction = canSubmit,
                            onOpenEdit = { editingObligation = item }
                        )
                    }
                }
            }

            "history" -> {
                if (historyObligations.isEmpty() && !loading) {
                    item {
                        EmptyReportBanner(
                            title = "Chưa có lịch sử báo cáo",
                            subtitle = "Các bản nháp và báo cáo đã nộp lên máy chủ sẽ hiển thị tại đây."
                        )
                    }
                } else {
                    items(historyObligations, key = { it.obligationId }) { item ->
                        WorkReportObligationCard(
                            item = item,
                            showEditAction = canSubmit && item.submissionState in listOf("DRAFT", "REJECTED"),
                            onOpenEdit = { editingObligation = item }
                        )
                    }
                }
            }

            "review" -> {
                if (reviewQueue.isEmpty() && !loading) {
                    item {
                        EmptyReportBanner(
                            title = "Không có báo cáo nào đang chờ duyệt",
                            subtitle = "Hàng đợi phê duyệt từ máy chủ hiện đang trống."
                        )
                    }
                } else {
                    items(reviewQueue, key = { it.obligationId }) { item ->
                        ReviewInboxItemCard(
                            item = item,
                            onApprove = { reason -> onReviewDecisionAction?.invoke(item, true, reason) },
                            onReject = { reason -> onReviewDecisionAction?.invoke(item, false, reason) }
                        )
                    }
                }
            }

            "analytics" -> {
                item {
                    val snap = analyticsSnapshot
                    if (snap == null) {
                        EmptyReportBanner(
                            title = "Chưa có dữ liệu tổng hợp",
                            subtitle = "Dữ liệu phân tích báo cáo công việc theo mẫu sẽ hiển thị khi có mẫu báo cáo hợp lệ."
                        )
                    } else {
                        WorkReportAnalyticsSummaryCard(snapshot = snap)
                    }
                }
            }
        }
    }
}

internal fun parseDynamicSections(schemaJsonRaw: String): List<WorkReportSectionSpec> {
    if (schemaJsonRaw.isBlank()) return emptyList()
    return runCatching {
        val root = JSONObject(schemaJsonRaw)
        val sectionsArr = root.optJSONArray("sections") ?: JSONArray()
        val fieldsArr = root.optJSONArray("fields") ?: JSONArray()

        val allFields = mutableListOf<com.example.data.WorkReportFieldSpec>()
        for (i in 0 until fieldsArr.length()) {
            val f = fieldsArr.optJSONObject(i) ?: continue
            val optsArr = f.optJSONArray("options")
            val options = mutableListOf<com.example.data.WorkReportFieldOption>()
            if (optsArr != null) {
                for (j in 0 until optsArr.length()) {
                    val o = optsArr.optJSONObject(j) ?: continue
                    options.add(
                        com.example.data.WorkReportFieldOption(
                            value = o.optString("value", ""),
                            label = o.optString("label", o.optString("value", ""))
                        )
                    )
                }
            }
            allFields.add(
                com.example.data.WorkReportFieldSpec(
                    fieldKey = f.optString("key", f.optString("field_key", "")),
                    sectionKey = f.optString("section_key", "default"),
                    sortOrder = f.optInt("sort_order", i),
                    label = f.optString("label", f.optString("key", "")),
                    description = f.optString("description", "").ifBlank { null },
                    fieldType = f.optString("type", f.optString("field_type", "text")),
                    required = f.optBoolean("required", false),
                    defaultValue = if (f.isNull("default_value")) null else f.optString("default_value", "").ifBlank { null },
                    options = options
                )
            )
        }

        if (sectionsArr.length() == 0) {
            if (allFields.isEmpty()) emptyList()
            else listOf(
                WorkReportSectionSpec(
                    sectionKey = "default",
                    label = "Nội dung báo cáo",
                    description = null,
                    sortOrder = 0,
                    fields = allFields.sortedBy { it.sortOrder }
                )
            )
        } else {
            val result = mutableListOf<WorkReportSectionSpec>()
            for (i in 0 until sectionsArr.length()) {
                val s = sectionsArr.optJSONObject(i) ?: continue
                val sKey = s.optString("key", s.optString("section_key", "section_$i"))
                val nestedFieldsArr = s.optJSONArray("fields")
                val sectionFields = if (nestedFieldsArr != null && nestedFieldsArr.length() > 0) {
                    val nested = mutableListOf<com.example.data.WorkReportFieldSpec>()
                    for (k in 0 until nestedFieldsArr.length()) {
                        val nf = nestedFieldsArr.optJSONObject(k) ?: continue
                        val optsArr = nf.optJSONArray("options")
                        val options = mutableListOf<com.example.data.WorkReportFieldOption>()
                        if (optsArr != null) {
                            for (j in 0 until optsArr.length()) {
                                val o = optsArr.optJSONObject(j) ?: continue
                                options.add(
                                    com.example.data.WorkReportFieldOption(
                                        value = o.optString("value", ""),
                                        label = o.optString("label", o.optString("value", ""))
                                    )
                                )
                            }
                        }
                        nested.add(
                            com.example.data.WorkReportFieldSpec(
                                fieldKey = nf.optString("key", nf.optString("field_key", "")),
                                sectionKey = sKey,
                                sortOrder = nf.optInt("sort_order", k),
                                label = nf.optString("label", nf.optString("key", "")),
                                description = nf.optString("description", "").ifBlank { null },
                                fieldType = nf.optString("type", nf.optString("field_type", "text")),
                                required = nf.optBoolean("required", false),
                                defaultValue = if (nf.isNull("default_value")) null else nf.optString("default_value", "").ifBlank { null },
                                options = options
                            )
                        )
                    }
                    nested.sortedBy { it.sortOrder }
                } else {
                    allFields.filter { it.sectionKey == sKey }.sortedBy { it.sortOrder }
                }
                result.add(
                    WorkReportSectionSpec(
                        sectionKey = sKey,
                        label = s.optString("label", "Mục ${i + 1}"),
                        description = s.optString("description", "").ifBlank { null },
                        sortOrder = s.optInt("sort_order", i),
                        fields = sectionFields
                    )
                )
            }
            result.sortedBy { it.sortOrder }
        }
    }.getOrDefault(emptyList())
}

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

    fun buildPayloadJson(): JSONObject {
        val obj = JSONObject()
        sections.flatMap { it.fields }.forEach { field ->
            val raw = fieldValues[field.fieldKey].orEmpty().trim()
            when (field.fieldType) {
                "integer" -> if (raw.isNotEmpty()) obj.put(field.fieldKey, raw.toLongOrNull() ?: 0L)
                "decimal" -> if (raw.isNotEmpty()) obj.put(field.fieldKey, raw.toDoubleOrNull() ?: 0.0)
                "boolean" -> obj.put(field.fieldKey, raw.equals("true", ignoreCase = true))
                "multi_select" -> {
                    val arr = JSONArray()
                    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it) }
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
            text = "${obligation.templateName} · Kỳ ${obligation.periodKey}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (sections.isEmpty()) {
            Text(
                text = "Máy chủ chưa trả cấu trúc trường dữ liệu chi tiết cho nghĩa vụ này.",
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
                                onValueChange = { fieldValues[field.fieldKey] = it },
                                label = { Text(labelWithReq) },
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
                    onClick = { onSaveDraft(buildPayloadJson()) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_draft_report_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lưu nháp")
                }
                Button(
                    onClick = { onSubmitFinal(buildPayloadJson()) },
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
    val badgeColor = when (item.submissionState) {
        "APPROVED" -> SemanticColors.SuccessLight
        "PENDING_REVIEW", "SUBMITTED" -> SemanticColors.RunningLight
        "REJECTED" -> SemanticColors.ErrorLight
        else -> SemanticColors.WarningLight
    }
    val stateLabel = when (item.submissionState) {
        "APPROVED" -> "ĐÃ DUYỆT"
        "PENDING_REVIEW", "SUBMITTED" -> "CHỜ DUYỆT"
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
                        text = "Hạn nộp: ${item.dueAtIso.take(16).replace('T', ' ')}",
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

            if (!item.latestReviewReason.isNullOrBlank()) {
                Text(
                    text = "Nhận xét duyệt: ${item.latestReviewReason}",
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
                        Text("Mở biểu mẫu báo cáo")
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.assigneeDisplayName} · Kỳ ${item.periodKey}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${item.templateName} · Nộp lúc: ${item.submittedAt?.take(16)?.replace('T', ' ') ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SemanticColors.RunningLight.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Phiên bản v${item.submissionVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticColors.RunningLight,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
                        Text(
                            text = "• $k: $v",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = reviewNote,
                onValueChange = { reviewNote = it },
                label = { Text("Nhận xét phê duyệt / lý do trả lại *") },
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
                    Text("Phê duyệt", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WorkReportAnalyticsSummaryCard(snapshot: WorkReportAnalyticsSnapshot) {
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
                text = "Tổng hợp Kết quả: ${snapshot.templateName}",
                style = MaterialTheme.typography.titleMedium
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
                    Text("Tỷ lệ hoàn thành nghĩa vụ", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${snapshot.submittedCount} / ${snapshot.totalObligations} nghĩa vụ đã nộp",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    snapshot.complianceRatePct?.let { "${it}%" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (snapshot.metrics.isNotEmpty()) {
                HorizontalDivider()
                snapshot.metrics.forEach { metric ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(metric.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            metric.valueDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (snapshot.breakdownRows.isNotEmpty()) {
                HorizontalDivider()
                snapshot.breakdownRows.forEach { row ->
                    Text(
                        text = "• ${row.groupLabel}: ${row.submittedCount}/${row.obligationCount} báo cáo",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
