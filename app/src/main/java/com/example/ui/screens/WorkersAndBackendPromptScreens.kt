package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.DirectEntryCatalog
import com.example.data.NewWorkerEntryDraft
import com.example.data.OwnSubmissionItem
import com.example.data.StagedDocumentRef
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import java.util.UUID

private val ENTRY_STATUS_OPTIONS = listOf("Đang làm", "Đang chờ việc")
private val GENDER_OPTIONS = listOf("Nam", "Nữ", "Khác")
private val WORK_STATUS_FILTER_OPTIONS = listOf(
    "ON" to "Đang làm",
    "UNCONFIRMED" to "Đang chờ việc",
    "OFF" to "Đã nghỉ"
)

data class DropdownOptionItem(
    val id: String,
    val label: String
)

// ============================================================================
// 1. MÀN HÌNH "CẤP MỚI" (`/direct-entry` - Full-profile v1.1)
// ============================================================================
@Composable
fun DirectEntryNewWorkersScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onNavigateToWorkersList: () -> Unit,
    onBackToDashboard: () -> Unit,
    onUploadStageFile: ((rowId: String, documentType: String, uri: Uri, onStaged: (StagedDocumentRef) -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onDeleteStageId: ((stageId: String) -> Unit)? = null,
    onSubmitValidDrafts: ((validRows: List<NewWorkerEntryDraft>, onSuccess: () -> Unit) -> Unit)? = null
) {
    BackHandler(onBack = onBackToDashboard)

    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val catalogLoading by viewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogError by viewModel.catalogError.collectAsStateWithLifecycle()
    val ownSubmissions by viewModel.ownSubmissions.collectAsStateWithLifecycle()

    var showBatchHistoryDialog by remember { mutableStateOf(false) }

    val draftRows = remember {
        mutableStateListOf(
            NewWorkerEntryDraft(localId = UUID.randomUUID().toString())
        )
    }

    var pendingFileTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = pendingFileTarget
        pendingFileTarget = null
        if (uri != null && target != null) {
            val (rowId, documentType) = target
            val index = draftRows.indexOfFirst { it.localId == rowId }
            if (index >= 0) {
                val existingStageId = when (documentType) {
                    "WORKER_PORTRAIT" -> draftRows[index].portraitStage?.stageId
                    "CCCD_FRONT" -> draftRows[index].cccdFrontStage?.stageId
                    "CCCD_BACK" -> draftRows[index].cccdBackStage?.stageId
                    "EMPLOYMENT_CONTRACT" -> draftRows[index].contractStage?.stageId
                    else -> null
                }
                if (onUploadStageFile != null) {
                    draftRows[index] = draftRows[index].copy(uploadingSlot = documentType, slotError = null)
                    onUploadStageFile(
                        rowId,
                        documentType,
                        uri,
                        { staged ->
                            val currentIdx = draftRows.indexOfFirst { it.localId == rowId }
                            if (currentIdx >= 0) {
                                existingStageId?.let { oldId -> onDeleteStageId?.invoke(oldId) }
                                val cur = draftRows[currentIdx]
                                draftRows[currentIdx] = when (documentType) {
                                    "WORKER_PORTRAIT" -> cur.copy(portraitStage = staged, uploadingSlot = null, slotError = null)
                                    "CCCD_FRONT" -> cur.copy(cccdFrontStage = staged, uploadingSlot = null, slotError = null)
                                    "CCCD_BACK" -> cur.copy(cccdBackStage = staged, uploadingSlot = null, slotError = null)
                                    "EMPLOYMENT_CONTRACT" -> cur.copy(contractStage = staged, uploadingSlot = null, slotError = null)
                                    else -> cur.copy(uploadingSlot = null)
                                }
                            }
                        },
                        { errMsg ->
                            val currentIdx = draftRows.indexOfFirst { it.localId == rowId }
                            if (currentIdx >= 0) {
                                draftRows[currentIdx] = draftRows[currentIdx].copy(
                                    uploadingSlot = null,
                                    slotError = errMsg
                                )
                            }
                        }
                    )
                } else {
                    draftRows[index] = draftRows[index].copy(
                        slotError = "Chức năng tải hồ sơ tạm lên máy chủ chưa sẵn sàng."
                    )
                }
            }
        }
    }

    fun pickDocumentForRow(rowId: String, documentType: String) {
        pendingFileTarget = rowId to documentType
        val mimeFilter = if (documentType == "WORKER_PORTRAIT") "image/*" else "*/*"
        filePickerLauncher.launch(mimeFilter)
    }

    val validCount = draftRows.count { it.isValid }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("direct_entry_new_workers_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Nhập liệu trực tiếp",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${draftRows.size} dòng",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Dữ liệu chỉ được lưu sau khi máy chủ xác nhận (worker-profile/1.1).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (catalogLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Đang tải danh mục dự án & người tuyển từ máy chủ…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    catalogError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Đợt nhập liệu",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "${ownSubmissions.size} đã gửi",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showBatchHistoryDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Mở", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nhập nhiều NLĐ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mã NLĐ được máy chủ tự động cấp phát khi lưu hồ sơ thành công.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            draftRows.add(NewWorkerEntryDraft(localId = UUID.randomUUID().toString()))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("top_add_worker_row_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thêm NLĐ")
                    }
                }
            }
        }

        itemsIndexed(draftRows, key = { _, item -> item.localId }) { index, row ->
            WorkerMultiEntryCard(
                rowNumber = index + 1,
                draft = row,
                catalog = catalog,
                selfRecruiterId = actor.selfRecruiterSuggestion,
                canRemove = draftRows.size > 1,
                onUpdate = { updated -> draftRows[index] = updated },
                onRemove = {
                    row.allStageIds().forEach { stageId -> onDeleteStageId?.invoke(stageId) }
                    if (draftRows.size > 1) {
                        draftRows.removeAt(index)
                    } else {
                        draftRows[0] = NewWorkerEntryDraft(localId = UUID.randomUUID().toString())
                    }
                },
                onPickFile = { docType -> pickDocumentForRow(row.localId, docType) },
                onClearStage = { docType, stageId ->
                    onDeleteStageId?.invoke(stageId)
                    val cur = draftRows[index]
                    draftRows[index] = when (docType) {
                        "WORKER_PORTRAIT" -> cur.copy(portraitStage = null)
                        "CCCD_FRONT" -> cur.copy(cccdFrontStage = null)
                        "CCCD_BACK" -> cur.copy(cccdBackStage = null)
                        "EMPLOYMENT_CONTRACT" -> cur.copy(contractStage = null)
                        else -> cur
                    }
                }
            )
        }

        item {
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                draftRows.add(NewWorkerEntryDraft(localId = UUID.randomUUID().toString()))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("bottom_add_worker_row_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thêm NLĐ nữa")
                        }

                        Button(
                            onClick = {
                                val validRows = draftRows.filter { it.isValid }
                                if (validRows.isNotEmpty() && onSubmitValidDrafts != null) {
                                    onSubmitValidDrafts(validRows) {
                                        val remaining = draftRows.filter { !it.isValid && it.isStarted }
                                        draftRows.clear()
                                        if (remaining.isNotEmpty()) {
                                            draftRows.addAll(remaining)
                                        } else {
                                            draftRows.add(NewWorkerEntryDraft(localId = UUID.randomUUID().toString()))
                                        }
                                        onNavigateToWorkersList()
                                    }
                                }
                            },
                            enabled = validCount > 0 && onSubmitValidDrafts != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("save_valid_workers_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (validCount > 0) "Lưu các NLĐ hợp lệ ($validCount)" else "Lưu các NLĐ hợp lệ",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (validCount == 0) {
                        Text(
                            text = "Điền các trường có dấu * từ danh mục máy chủ (Dự án, HRP/Vendor, Người tuyển, Loại hình LĐ, Ngày bắt đầu DD/MM/YYYY, Họ và tên) để bật nút Lưu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showBatchHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showBatchHistoryDialog = false },
            title = { Text("Lịch sử đợt nhập liệu từ máy chủ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ownSubmissions.isEmpty()) {
                        Text(
                            text = "Chưa có đợt nhập liệu nào được ghi nhận trên máy chủ.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ownSubmissions.take(10).forEach { sub ->
                            Text(
                                text = "• Đợt ${sub.submissionId.take(8)}…: ${sub.entryCount} hồ sơ (${sub.state})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchHistoryDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}

@Composable
private fun WorkerMultiEntryCard(
    rowNumber: Int,
    draft: NewWorkerEntryDraft,
    catalog: DirectEntryCatalog?,
    selfRecruiterId: String?,
    canRemove: Boolean,
    onUpdate: (NewWorkerEntryDraft) -> Unit,
    onRemove: () -> Unit,
    onPickFile: (documentType: String) -> Unit,
    onClearStage: (documentType: String, stageId: String) -> Unit
) {
    val statusText = when {
        draft.uploadingSlot != null -> "Đang tải tệp lên máy chủ…"
        !draft.isStarted -> "Chưa nhập"
        draft.isValid -> "Hợp lệ"
        else -> "Đang nhập (thiếu trường *)"
    }
    val statusColor = when {
        !draft.isStarted -> MaterialTheme.colorScheme.onSurfaceVariant
        draft.isValid -> SemanticColors.SuccessLight
        else -> SemanticColors.WarningLight
    }
    val headerName = draft.fullName.trim().ifBlank { "Chưa nhập họ tên" }

    val projectOptions = remember(catalog) {
        catalog?.projects?.map { DropdownOptionItem(it.projectId, it.displayName) }.orEmpty()
    }
    val providerOptions = listOf(
        DropdownOptionItem("hrp", "HRP"),
        DropdownOptionItem("vendor", "Vendor")
    )
    val recruiterOptions = remember(catalog, draft.providerType) {
        catalog?.recruiters
            ?.filter { it.providerType == draft.providerType }
            ?.map { DropdownOptionItem(it.recruiterId, it.label) }
            .orEmpty()
    }
    val laborTypeOptions = remember(catalog) {
        catalog?.laborTypes
            ?.sortedBy { it.sortOrder }
            ?.map { DropdownOptionItem(it.laborTypeKey, it.displayName) }
            .orEmpty()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (draft.isValid) SemanticColors.SuccessLight.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdate(draft.copy(isExpanded = !draft.isExpanded)) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (draft.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "NLĐ $rowNumber: $headerName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            AnimatedVisibility(visible = draft.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    KeyedSelectDropdownField(
                        label = "Dự án *",
                        selectedLabel = draft.projectDisplay,
                        placeholder = if (projectOptions.isEmpty()) "Đang chờ danh mục dự án" else "Chọn dự án",
                        options = projectOptions,
                        enabled = projectOptions.isNotEmpty(),
                        onSelect = { opt ->
                            onUpdate(draft.copy(projectId = opt.id, projectDisplay = opt.label))
                        },
                        testTag = "row_${rowNumber}_project"
                    )

                    KeyedSelectDropdownField(
                        label = "HRP/Vendor *",
                        selectedLabel = when (draft.providerType) {
                            "hrp" -> "HRP"
                            "vendor" -> "Vendor"
                            else -> ""
                        },
                        placeholder = "Chọn nguồn tuyển",
                        options = providerOptions,
                        onSelect = { opt ->
                            val matchingRecruiters = catalog?.recruiters?.filter { it.providerType == opt.id }.orEmpty()
                            val suggested = matchingRecruiters.firstOrNull { it.recruiterId == selfRecruiterId }
                                ?: matchingRecruiters.firstOrNull()
                            onUpdate(
                                draft.copy(
                                    providerType = opt.id,
                                    recruiterId = suggested?.recruiterId.orEmpty(),
                                    recruiterDisplay = suggested?.label.orEmpty()
                                )
                            )
                        },
                        testTag = "row_${rowNumber}_provider"
                    )

                    KeyedSelectDropdownField(
                        label = "Người tuyển / Vendor *",
                        selectedLabel = draft.recruiterDisplay,
                        placeholder = if (draft.providerType.isBlank()) "Chọn HRP/Vendor trước" else "Chọn người tuyển / đối tác",
                        options = recruiterOptions,
                        enabled = draft.providerType.isNotBlank() && recruiterOptions.isNotEmpty(),
                        onSelect = { opt ->
                            onUpdate(draft.copy(recruiterId = opt.id, recruiterDisplay = opt.label))
                        },
                        testTag = "row_${rowNumber}_recruiter"
                    )

                    KeyedSelectDropdownField(
                        label = "Loại hình LĐ *",
                        selectedLabel = draft.laborTypeDisplay,
                        placeholder = if (laborTypeOptions.isEmpty()) "Đang chờ danh mục loại hình" else "Chọn loại hình LĐ",
                        options = laborTypeOptions,
                        enabled = laborTypeOptions.isNotEmpty(),
                        onSelect = { opt ->
                            onUpdate(draft.copy(laborTypeKey = opt.id, laborTypeDisplay = opt.label))
                        },
                        testTag = "row_${rowNumber}_labor_type"
                    )

                    OutlinedTextField(
                        value = draft.firstWorkDateUi,
                        onValueChange = { onUpdate(draft.copy(firstWorkDateUi = it)) },
                        label = { Text("Ngày bắt đầu làm việc *") },
                        placeholder = { Text("DD/MM/YYYY") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_start_date")
                    )

                    KeyedSelectDropdownField(
                        label = "Trạng thái làm việc",
                        selectedLabel = draft.employmentStatusUi,
                        placeholder = "Đang làm",
                        options = ENTRY_STATUS_OPTIONS.map { DropdownOptionItem(it, it) },
                        onSelect = { opt -> onUpdate(draft.copy(employmentStatusUi = opt.id)) },
                        testTag = "row_${rowNumber}_status"
                    )

                    OutlinedTextField(
                        value = draft.fullName,
                        onValueChange = { onUpdate(draft.copy(fullName = it)) },
                        label = { Text("Họ và tên *") },
                        placeholder = { Text("Nhập họ và tên người lao động") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_full_name")
                    )

                    KeyedSelectDropdownField(
                        label = "Giới tính",
                        selectedLabel = draft.genderUi,
                        placeholder = "Bỏ trống hoặc chọn Nam / Nữ / Khác",
                        options = listOf(DropdownOptionItem("", "Bỏ trống")) + GENDER_OPTIONS.map { DropdownOptionItem(it, it) },
                        onSelect = { opt -> onUpdate(draft.copy(genderUi = opt.id)) },
                        testTag = "row_${rowNumber}_gender"
                    )

                    OutlinedTextField(
                        value = draft.phone,
                        onValueChange = { onUpdate(draft.copy(phone = it.filter { ch -> ch.isDigit() }.take(12))) },
                        label = { Text("Số điện thoại") },
                        placeholder = { Text("09xxxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_phone")
                    )

                    OutlinedTextField(
                        value = draft.cccd,
                        onValueChange = { onUpdate(draft.copy(cccd = it.filter { ch -> ch.isDigit() }.take(12))) },
                        label = { Text("Số CCCD") },
                        placeholder = { Text("Nhập 9 hoặc 12 chữ số CCCD") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_cccd")
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Tải hồ sơ lên máy chủ (Stage)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            draft.slotError?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            DocumentUploadSlotCard(
                                title = "Ảnh NLĐ",
                                subtitle = "WORKER_PORTRAIT (JPEG/PNG, tối đa 10 MiB)",
                                staged = draft.portraitStage,
                                uploading = draft.uploadingSlot == "WORKER_PORTRAIT",
                                onPickClick = { onPickFile("WORKER_PORTRAIT") },
                                onClearClick = {
                                    draft.portraitStage?.let { onClearStage("WORKER_PORTRAIT", it.stageId) }
                                }
                            )

                            DocumentUploadSlotCard(
                                title = "CCCD mặt trước",
                                subtitle = "CCCD_FRONT (ảnh hoặc PDF, tối đa 10 MiB)",
                                staged = draft.cccdFrontStage,
                                uploading = draft.uploadingSlot == "CCCD_FRONT",
                                onPickClick = { onPickFile("CCCD_FRONT") },
                                onClearClick = {
                                    draft.cccdFrontStage?.let { onClearStage("CCCD_FRONT", it.stageId) }
                                }
                            )

                            DocumentUploadSlotCard(
                                title = "CCCD mặt sau",
                                subtitle = "CCCD_BACK (ảnh hoặc PDF, tối đa 10 MiB)",
                                staged = draft.cccdBackStage,
                                uploading = draft.uploadingSlot == "CCCD_BACK",
                                onPickClick = { onPickFile("CCCD_BACK") },
                                onClearClick = {
                                    draft.cccdBackStage?.let { onClearStage("CCCD_BACK", it.stageId) }
                                }
                            )

                            DocumentUploadSlotCard(
                                title = "Hợp đồng",
                                subtitle = "EMPLOYMENT_CONTRACT (ảnh hoặc PDF, tối đa 10 MiB)",
                                staged = draft.contractStage,
                                uploading = draft.uploadingSlot == "EMPLOYMENT_CONTRACT",
                                onPickClick = { onPickFile("EMPLOYMENT_CONTRACT") },
                                onClearClick = {
                                    draft.contractStage?.let { onClearStage("EMPLOYMENT_CONTRACT", it.stageId) }
                                }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUpdate(draft.copy(showOptionalSection = !draft.showOptionalSection))
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (draft.showOptionalSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Thông tin thêm: tài khoản ngân hàng, ghi chú và thông tin cá nhân",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            AnimatedVisibility(visible = draft.showOptionalSection) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .padding(bottom = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                    OutlinedTextField(
                                        value = draft.bankAccountNumber,
                                        onValueChange = { onUpdate(draft.copy(bankAccountNumber = it)) },
                                        label = { Text("STK (Số tài khoản)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.bankAccountHolder,
                                        onValueChange = { onUpdate(draft.copy(bankAccountHolder = it)) },
                                        label = { Text("Tên chủ tài khoản") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.bankName,
                                        onValueChange = { onUpdate(draft.copy(bankName = it)) },
                                        label = { Text("Ngân hàng") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.note,
                                        onValueChange = { onUpdate(draft.copy(note = it)) },
                                        label = { Text("Ghi chú") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draft.birthDateUi,
                                            onValueChange = { onUpdate(draft.copy(birthDateUi = it)) },
                                            label = { Text("Ngày sinh") },
                                            placeholder = { Text("DD/MM/YYYY") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = draft.cccdIssueDateUi,
                                            onValueChange = { onUpdate(draft.copy(cccdIssueDateUi = it)) },
                                            label = { Text("Ngày cấp CCCD") },
                                            placeholder = { Text("DD/MM/YYYY") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = draft.cccdIssuePlace,
                                        onValueChange = { onUpdate(draft.copy(cccdIssuePlace = it)) },
                                        label = { Text("Nơi cấp CCCD") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.address,
                                        onValueChange = { onUpdate(draft.copy(address = it)) },
                                        label = { Text("Địa chỉ thường trú / quê quán") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        OutlinedButton(
                            onClick = onRemove,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (canRemove) "Xóa dòng này" else "Làm mới dòng này")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeyedSelectDropdownField(
    label: String,
    selectedLabel: String,
    placeholder: String,
    options: List<DropdownOptionItem>,
    enabled: Boolean = true,
    onSelect: (DropdownOptionItem) -> Unit,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = enabled) { expanded = true }
                    .testTag(testTag)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedLabel.ifBlank { placeholder },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedLabel.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentUploadSlotCard(
    title: String,
    subtitle: String,
    staged: StagedDocumentRef?,
    uploading: Boolean,
    onPickClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val hasStage = staged != null

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasStage) SemanticColors.SuccessLight.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                uploading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Đang tải tệp lên stage máy chủ…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                hasStage -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SemanticColors.SuccessLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Đã stage: ${staged!!.fileName} (${staged.stageId.take(8)}…)",
                            style = MaterialTheme.typography.labelMedium,
                            color = SemanticColors.SuccessLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Chưa tải tệp lên máy chủ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickClick,
                    enabled = !uploading,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (hasStage) "Đổi tệp" else "Chọn & Tải lên", style = MaterialTheme.typography.labelMedium)
                }

                if (hasStage && !uploading) {
                    TextButton(
                        onClick = onClearClick,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Xóa stage", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. MÀN HÌNH "NHÂN SỰ" (`/direct-entry/workers` + `submissions`)
// ============================================================================
data class WorkerScopeTabSpec(
    val key: String,
    val label: String
)

fun resolveVisibleWorkerTabs(actor: AppActor): List<WorkerScopeTabSpec> {
    return buildList {
        add(WorkerScopeTabSpec("managed", "Dự án tôi quản lý"))
        add(WorkerScopeTabSpec("recruited", "Người tôi tuyển"))
        if (actor.hasScopeKind("team") || actor.hasCapability("entry_team")) {
            add(WorkerScopeTabSpec("team", "Team tôi quản lý"))
        }
        add(WorkerScopeTabSpec("uploader", "Tôi đã nhập"))
        if (actor.hasScopeKind("all") && (actor.hasCapability("entry_admin") || actor.hasCapability("change_review"))) {
            add(WorkerScopeTabSpec("all", "Toàn bộ NLĐ"))
        }
    }
}

@Composable
fun WorkersAndDirectEntryScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onOpenNewEntryScreen: () -> Unit,
    onBackToDashboard: () -> Unit,
    onSelectScopeTab: ((scopeKey: String) -> Unit)? = null,
    onSearchAndFilterChange: ((search: String, projectId: String?, status: String?) -> Unit)? = null,
    onExportWorkersExcel: ((scopeKey: String, projectId: String?, status: String?) -> Unit)? = null,
    onOpenEntryDetail: ((entryId: String) -> Unit)? = null,
    onCloseEntryDetail: (() -> Unit)? = null,
    onOpenProposeChange: ((WorkerProfileEntity) -> Unit)? = null,
    onOpenDirectEdit: ((WorkerProfileEntity) -> Unit)? = null
) {
    BackHandler(onBack = onBackToDashboard)

    val workers by viewModel.workerRows.collectAsStateWithLifecycle()
    val workerLoading by viewModel.workerLoading.collectAsStateWithLifecycle()
    val workerError by viewModel.workerError.collectAsStateWithLifecycle()
    val ownSubmissions by viewModel.ownSubmissions.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedEntryDetail.collectAsStateWithLifecycle()

    val visibleTabs = remember(actor) { resolveVisibleWorkerTabs(actor) }
    var selectedScopeTabIndex by remember { mutableIntStateOf(0) }
    val safeTabIdx = selectedScopeTabIndex.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
    val activeScopeKey = visibleTabs.getOrNull(safeTabIdx)?.key ?: "managed"

    var searchQuery by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedStatusCode by remember { mutableStateOf<String?>(null) }

    val canCreateEntry = actor.hasCapability("entry_create") && actor.hasCapability("submission_create")
    val isSubmissionTab = activeScopeKey == "uploader"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("workers_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Người lao động",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dữ liệu và quyền thao tác trên từng hồ sơ do máy chủ quyết định.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canCreateEntry) {
                            Button(
                                onClick = onOpenNewEntryScreen,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("go_to_new_entry_from_workers")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cấp mới NLĐ")
                            }
                        }
                        if (!isSubmissionTab && onExportWorkersExcel != null) {
                            OutlinedButton(
                                onClick = {
                                    onExportWorkersExcel(activeScopeKey, selectedProjectId, selectedStatusCode)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("export_workers_excel_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Xuất Excel")
                            }
                        }
                    }

                    ScrollableTabRow(
                        selectedTabIndex = safeTabIdx,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        visibleTabs.forEachIndexed { idx, tab ->
                            val selected = safeTabIdx == idx
                            Tab(
                                selected = selected,
                                onClick = {
                                    selectedScopeTabIndex = idx
                                    onSelectScopeTab?.invoke(tab.key)
                                },
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                    .testTag("worker_scope_tab_${tab.key}"),
                                text = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!isSubmissionTab) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            onSearchAndFilterChange?.invoke(q, selectedProjectId, selectedStatusCode)
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Tìm theo họ tên, mã NLĐ…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("worker_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedStatusCode == null,
                            onClick = {
                                selectedStatusCode = null
                                onSearchAndFilterChange?.invoke(searchQuery, selectedProjectId, null)
                            },
                            label = { Text("Mọi trạng thái") }
                        )
                        WORK_STATUS_FILTER_OPTIONS.forEach { (code, label) ->
                            FilterChip(
                                selected = selectedStatusCode == code,
                                onClick = {
                                    val next = if (selectedStatusCode == code) null else code
                                    selectedStatusCode = next
                                    onSearchAndFilterChange?.invoke(searchQuery, selectedProjectId, next)
                                },
                                label = { Text(label) }
                            )
                        }
                        catalog?.projects?.forEach { proj ->
                            FilterChip(
                                selected = selectedProjectId == proj.projectId,
                                onClick = {
                                    val next = if (selectedProjectId == proj.projectId) null else proj.projectId
                                    selectedProjectId = next
                                    onSearchAndFilterChange?.invoke(searchQuery, next, selectedStatusCode)
                                },
                                label = { Text(proj.displayName) }
                            )
                        }
                        if (selectedStatusCode != null || selectedProjectId != null || searchQuery.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    selectedStatusCode = null
                                    selectedProjectId = null
                                    searchQuery = ""
                                    onSearchAndFilterChange?.invoke("", null, null)
                                }
                            ) {
                                Icon(Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa lọc")
                            }
                        }
                    }
                }
            }
        }

        if (workerLoading) {
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
                        Text("Đang tải dữ liệu từ máy chủ…")
                    }
                }
            }
        }

        workerError?.let { err ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }

        if (isSubmissionTab) {
            if (ownSubmissions.isEmpty() && !workerLoading) {
                item {
                    EmptyWorkersCard(
                        title = "Chưa có lịch sử đợt nhập liệu",
                        subtitle = "Các đợt hồ sơ do bạn tải lên máy chủ sẽ hiển thị tại đây."
                    )
                }
            } else {
                items(ownSubmissions, key = { it.submissionId }) { sub ->
                    OwnSubmissionRowCard(item = sub)
                }
            }
        } else {
            if (workers.isEmpty() && !workerLoading) {
                item {
                    EmptyWorkersCard(
                        title = "Không có hồ sơ người lao động",
                        subtitle = "Chưa có hồ sơ phù hợp trong phạm vi đang chọn từ máy chủ."
                    )
                }
            } else {
                items(workers, key = { it.cacheKey }) { worker ->
                    WorkerOperationsRowCard(
                        worker = worker,
                        onViewDetail = { onOpenEntryDetail?.invoke(worker.entryId) },
                        onProposeChange = { onOpenProposeChange?.invoke(worker) },
                        onDirectEdit = { onOpenDirectEdit?.invoke(worker) }
                    )
                }
            }
        }
    }

    selectedDetail?.let { detail ->
        WorkerEntryDetailDialog(
            detail = detail,
            onDismiss = { onCloseEntryDetail?.invoke() }
        )
    }
}

@Composable
private fun EmptyWorkersCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OwnSubmissionRowCard(item: OwnSubmissionItem) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Đợt #${item.submissionId.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = item.state,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "Số lượng NLĐ: ${item.entryCount} · Phiên bản: v${item.version}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Cập nhật: ${item.updatedAt.take(19).replace('T', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkerOperationsRowCard(
    worker: WorkerProfileEntity,
    onViewDetail: () -> Unit,
    onProposeChange: () -> Unit,
    onDirectEdit: () -> Unit
) {
    val actions = worker.allowedActions
    val statusLabel = when (worker.employmentStatus) {
        "ON" -> "Đang làm"
        "UNCONFIRMED" -> "Đang chờ việc"
        "OFF" -> "Đã nghỉ"
        else -> worker.employmentStatus
    }
    val statusColor = when (worker.employmentStatus) {
        "ON" -> SemanticColors.SuccessLight
        "UNCONFIRMED" -> SemanticColors.WarningLight
        else -> MaterialTheme.colorScheme.error
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worker.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mã NLĐ: ${worker.employeeCode}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dự án: ${worker.projectDisplay}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ngày đầu: ${worker.firstWorkDate}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Người tuyển: ${worker.recruiterDisplay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = worker.laborTypeDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (worker.paymentSummary.isNotBlank()) {
                    Text(
                        text = "Thanh toán: ${worker.paymentSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (worker.pendingRequestSummary.isNotBlank() || worker.lastDecisionSummary.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (worker.pendingRequestSummary.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SemanticColors.WarningLight.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = worker.pendingRequestSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = SemanticColors.WarningLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (worker.lastDecisionSummary.isNotBlank()) {
                        Text(
                            text = worker.lastDecisionSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (actions.view || actions.proposeChange || actions.directEdit) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (actions.view) {
                        OutlinedButton(
                            onClick = onViewDetail,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("worker_detail_${worker.entryId}")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xem chi tiết", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (actions.proposeChange) {
                        OutlinedButton(
                            onClick = onProposeChange,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("worker_propose_${worker.entryId}")
                        ) {
                            Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đề xuất thay đổi", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (actions.directEdit) {
                        Button(
                            onClick = onDirectEdit,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("worker_direct_edit_${worker.entryId}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sửa trực tiếp", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkerEntryDetailDialog(
    detail: WorkerEntryDetail,
    onDismiss: () -> Unit,
    onDownloadDocument: ((downloadPath: String) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = detail.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mã NLĐ: ${detail.employeeCode} · Phiên bản v${detail.version}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailItemRow("Ngày đầu đi làm", detail.firstWorkDate)
                DetailItemRow("Loại hình LĐ", detail.laborTypeDisplay)
                DetailItemRow("Trạng thái", "${detail.employmentStatus} (${detail.employmentEffectiveDate})")
                HorizontalDivider()
                DetailItemRow("Giới tính", detail.gender ?: "—")
                DetailItemRow("Ngày sinh", detail.dateOfBirth ?: "—")
                DetailItemRow("Số điện thoại", detail.phone ?: "—")
                DetailItemRow("Số CCCD", detail.nationalId ?: "—")
                DetailItemRow("Địa chỉ", detail.address ?: "—")
                HorizontalDivider()
                DetailItemRow("Ngân hàng", detail.bankName ?: "—")
                DetailItemRow("Số tài khoản", detail.accountNumber ?: "—")
                DetailItemRow("Chủ tài khoản", detail.accountHolderName ?: "—")
                if (!detail.generalNote.isNullOrBlank()) {
                    HorizontalDivider()
                    DetailItemRow("Ghi chú", detail.generalNote)
                }
                if (detail.documents.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Tài liệu đính kèm trên máy chủ:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    detail.documents.forEach { doc ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${doc.documentType} (${doc.uploadStatus})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            val dl = doc.downloadPath
                            if (!dl.isNullOrBlank() && onDownloadDocument != null) {
                                TextButton(onClick = { onDownloadDocument(dl) }) {
                                    Text("Tải về")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
internal fun DetailItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f)
        )
    }
}
