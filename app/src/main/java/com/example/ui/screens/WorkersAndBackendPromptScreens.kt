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
import androidx.compose.material.icons.filled.AttachFile
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
import com.example.data.NewWorkerEntryDraft
import com.example.data.WorkerProfileEntity
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import java.util.UUID

private val PROJECT_OPTIONS = listOf(
    "Luxshare Bắc Ninh",
    "Foxconn Bắc Giang",
    "Goertek Quế Võ",
    "Samsung Thái Nguyên",
    "Canon Tiên Sơn",
    "Compal Vĩnh Phúc",
    "Pegatron Hải Phòng"
)

private val HRP_RECRUITER_OPTIONS = listOf(
    "Phạm Văn Hùng",
    "Lê Thu Hà",
    "Nguyễn Thị Minh",
    "Vũ Thùy Linh",
    "Đặng Anh Tuấn"
)

private val VENDOR_OPTIONS = listOf(
    "Vendor Nhân Lực Bắc Việt",
    "Vendor Cung Ứng Kinh Bắc",
    "Vendor Đại Phát HR",
    "Vendor Thái Nguyên Pro"
)

private val LABOR_TYPE_OPTIONS = listOf("Thời vụ", "Chính thức")
private val WORK_STATUS_OPTIONS = listOf("Đang làm", "Đang chờ việc", "Đã nghỉ")
private val ENTRY_STATUS_OPTIONS = listOf("Đang làm", "Đang chờ việc")
private val GENDER_OPTIONS = listOf("Nam", "Nữ")

// ============================================================================
// 1. MÀN HÌNH "CẤP MỚI" (`/direct-entry` - Nhập liệu trực tiếp kiểu Biểu mẫu)
// ============================================================================
@Composable
fun DirectEntryNewWorkersScreen(
    onSaveValidWorkers: (List<NewWorkerEntryDraft>) -> Unit,
    onNavigateToWorkersList: () -> Unit,
    onBackToDashboard: () -> Unit
) {
    BackHandler(onBack = onBackToDashboard)

    var showBatchHistoryDialog by remember { mutableStateOf(false) }

    val draftRows = remember {
        mutableStateListOf(
            NewWorkerEntryDraft(localId = UUID.randomUUID().toString())
        )
    }

    // Track which row & slot is currently picking a file
    var pendingFileTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = pendingFileTarget
        if (uri != null && target != null) {
            val (rowId, slotKey) = target
            val index = draftRows.indexOfFirst { it.localId == rowId }
            if (index >= 0) {
                val current = draftRows[index]
                val fileLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "tep_ho_so_${System.currentTimeMillis()}.jpg"
                draftRows[index] = when (slotKey) {
                    "portrait" -> current.copy(portraitFileName = fileLabel, portraitUri = uri.toString())
                    "cccd_front" -> current.copy(cccdFrontFileName = fileLabel, cccdFrontUri = uri.toString())
                    "cccd_back" -> current.copy(cccdBackFileName = fileLabel, cccdBackUri = uri.toString())
                    "contract" -> current.copy(contractFileName = fileLabel, contractUri = uri.toString())
                    else -> current
                }
            }
        }
        pendingFileTarget = null
    }

    fun pickDocumentForRow(rowId: String, slotKey: String) {
        pendingFileTarget = rowId to slotKey
        filePickerLauncher.launch("*/*")
    }

    val validCount = draftRows.count { it.isValid }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("direct_entry_new_workers_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Header Card matching `/direct-entry` screenshot
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
                                text = "Dữ liệu chỉ được lưu sau khi máy chủ xác nhận.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Batch bar ("Đợt nhập liệu | 1 đã tải | 1 đang gửi | Mở")
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
                                        text = "1 đã tải",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = SemanticColors.WarningLight.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${draftRows.size} đang nhập",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SemanticColors.WarningLight,
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

        // 2. Multi-Entry Form Container Card ("Nhập nhiều NLĐ")
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
                            text = "Hồ sơ hợp lệ được lưu trước; dòng còn lỗi và tệp đã chọn vẫn được giữ lại để xử lý tiếp.",
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

        // 3. Individual Worker Entry Cards (Collapsible)
        itemsIndexed(draftRows, key = { _, item -> item.localId }) { index, row ->
            WorkerMultiEntryCard(
                rowNumber = index + 1,
                draft = row,
                canRemove = draftRows.size > 1,
                onUpdate = { updated -> draftRows[index] = updated },
                onRemove = {
                    if (draftRows.size > 1) {
                        draftRows.removeAt(index)
                    } else {
                        draftRows[0] = NewWorkerEntryDraft(localId = UUID.randomUUID().toString())
                    }
                },
                onPickFile = { slotKey -> pickDocumentForRow(row.localId, slotKey) }
            )
        }

        // 4. Bottom Action Bar ("+ Thêm NLĐ nữa" & "Lưu các NLĐ hợp lệ")
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
                                if (validRows.isNotEmpty()) {
                                    onSaveValidWorkers(validRows)
                                    val remaining = draftRows.filter { !it.isValid && it.isStarted }
                                    draftRows.clear()
                                    if (remaining.isNotEmpty()) {
                                        draftRows.addAll(remaining)
                                    } else {
                                        draftRows.add(NewWorkerEntryDraft(localId = UUID.randomUUID().toString()))
                                    }
                                    onNavigateToWorkersList()
                                }
                            },
                            enabled = validCount > 0,
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
                            text = "Điền các trường có dấu * (Dự án, HRP/Vendor, Người tuyển, Loại hình LĐ, Ngày bắt đầu, Họ và tên) để bật nút Lưu.",
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
            title = { Text("Đợt nhập liệu gần đây") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Đợt #2026-10-23-A: Đã tải lên 4 hồ sơ NLĐ (Luxshare Bắc Ninh, Foxconn Bắc Giang) — Hoàn tất.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Đợt hiện tại: Đang soạn ${draftRows.size} dòng trên biểu mẫu di động.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
    canRemove: Boolean,
    onUpdate: (NewWorkerEntryDraft) -> Unit,
    onRemove: () -> Unit,
    onPickFile: (slotKey: String) -> Unit
) {
    val statusText = when {
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

    val recruiterChoices = remember(draft.providerType) {
        when (draft.providerType) {
            "HRP" -> HRP_RECRUITER_OPTIONS
            "Vendor" -> VENDOR_OPTIONS
            else -> emptyList()
        }
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
            // Collapsible Row Header ("▼ NLĐ 1: Chưa nhập họ tên · Chưa nhập")
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

                    // =========================================================================
                    // 10 Ô NHẬP CHÍNH (9 ô gốc của Biểu mẫu + Ô chọn Giới tính đưa lên chính)
                    // =========================================================================

                    // 1. Dự án *
                    FormSelectDropdownField(
                        label = "Dự án *",
                        selectedValue = draft.projectDisplay,
                        placeholder = "Chọn dự án",
                        options = PROJECT_OPTIONS,
                        onSelect = { onUpdate(draft.copy(projectDisplay = it)) },
                        testTag = "row_${rowNumber}_project"
                    )

                    // 2. HRP/Vendor *
                    FormSelectDropdownField(
                        label = "HRP/Vendor *",
                        selectedValue = draft.providerType,
                        placeholder = "Chọn",
                        options = listOf("HRP", "Vendor"),
                        onSelect = { newProvider ->
                            val defaultRecruiter = if (newProvider == "HRP") HRP_RECRUITER_OPTIONS.first() else VENDOR_OPTIONS.first()
                            onUpdate(draft.copy(providerType = newProvider, recruiterOrVendor = defaultRecruiter))
                        },
                        testTag = "row_${rowNumber}_provider"
                    )

                    // 3. Người tuyển / Vendor *
                    FormSelectDropdownField(
                        label = "Người tuyển / Vendor *",
                        selectedValue = draft.recruiterOrVendor,
                        placeholder = if (draft.providerType.isBlank()) "Chọn HRP/Vendor trước" else "Chọn người tuyển / đối tác",
                        options = recruiterChoices,
                        enabled = draft.providerType.isNotBlank(),
                        onSelect = { onUpdate(draft.copy(recruiterOrVendor = it)) },
                        testTag = "row_${rowNumber}_recruiter"
                    )

                    // 4. Loại hình LĐ *
                    FormSelectDropdownField(
                        label = "Loại hình LĐ *",
                        selectedValue = draft.laborType,
                        placeholder = "Chọn",
                        options = LABOR_TYPE_OPTIONS,
                        onSelect = { onUpdate(draft.copy(laborType = it)) },
                        testTag = "row_${rowNumber}_labor_type"
                    )

                    // 5. Ngày bắt đầu làm việc *
                    OutlinedTextField(
                        value = draft.firstWorkDate,
                        onValueChange = { onUpdate(draft.copy(firstWorkDate = it)) },
                        label = { Text("Ngày bắt đầu làm việc *") },
                        placeholder = { Text("DD/MM/YYYY") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_start_date")
                    )

                    // 6. Trạng thái làm việc
                    FormSelectDropdownField(
                        label = "Trạng thái làm việc",
                        selectedValue = draft.employmentStatus,
                        placeholder = "Đang làm",
                        options = ENTRY_STATUS_OPTIONS,
                        onSelect = { onUpdate(draft.copy(employmentStatus = it)) },
                        testTag = "row_${rowNumber}_status"
                    )

                    // 7. Họ và tên *
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

                    // 8. Giới tính (Được đưa lên khu vực ô nhập chính theo đúng yêu cầu)
                    FormSelectDropdownField(
                        label = "Giới tính",
                        selectedValue = draft.gender,
                        placeholder = "Chọn Nam / Nữ",
                        options = GENDER_OPTIONS,
                        onSelect = { onUpdate(draft.copy(gender = it)) },
                        testTag = "row_${rowNumber}_gender"
                    )

                    // 9. Số điện thoại
                    OutlinedTextField(
                        value = draft.phone,
                        onValueChange = { onUpdate(draft.copy(phone = it.filter { ch -> ch.isDigit() }.take(11))) },
                        label = { Text("Số điện thoại") },
                        placeholder = { Text("09xxxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_phone")
                    )

                    // 10. Số CCCD
                    OutlinedTextField(
                        value = draft.cccd,
                        onValueChange = { onUpdate(draft.copy(cccd = it.filter { ch -> ch.isDigit() }.take(12))) },
                        label = { Text("Số CCCD") },
                        placeholder = { Text("Nhập 12 chữ số CCCD") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_cccd")
                    )

                    // =========================================================================
                    // KHU VỰC TẢI HỒ SƠ LÊN (Giữ nguyên 4 ô upload ảnh & hồ sơ như bản Web)
                    // =========================================================================
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
                                text = "Tải hồ sơ lên",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            DocumentUploadSlotCard(
                                title = "Ảnh NLĐ",
                                subtitle = "Ảnh NLĐ (JPEG/PNG, tối đa 10 MiB)",
                                emptyHint = "Chưa chọn ảnh cho dòng này.",
                                selectedFileName = draft.portraitFileName,
                                onPickClick = { onPickFile("portrait") },
                                onClearClick = { onUpdate(draft.copy(portraitFileName = "", portraitUri = "")) }
                            )

                            DocumentUploadSlotCard(
                                title = "CCCD mặt trước",
                                subtitle = "CCCD mặt trước (ảnh hoặc PDF, tối đa 10 MiB)",
                                emptyHint = "Chưa chọn tệp cho dòng này.",
                                selectedFileName = draft.cccdFrontFileName,
                                onPickClick = { onPickFile("cccd_front") },
                                onClearClick = { onUpdate(draft.copy(cccdFrontFileName = "", cccdFrontUri = "")) }
                            )

                            DocumentUploadSlotCard(
                                title = "CCCD mặt sau",
                                subtitle = "CCCD mặt sau (ảnh hoặc PDF, tối đa 10 MiB)",
                                emptyHint = "Chưa chọn tệp cho dòng này.",
                                selectedFileName = draft.cccdBackFileName,
                                onPickClick = { onPickFile("cccd_back") },
                                onClearClick = { onUpdate(draft.copy(cccdBackFileName = "", cccdBackUri = "")) }
                            )

                            DocumentUploadSlotCard(
                                title = "Hợp đồng",
                                subtitle = "Hợp đồng (ảnh hoặc PDF, tối đa 10 MiB)",
                                emptyHint = "Chưa chọn tệp cho dòng này.",
                                selectedFileName = draft.contractFileName,
                                onPickClick = { onPickFile("contract") },
                                onClearClick = { onUpdate(draft.copy(contractFileName = "", contractUri = "")) }
                            )

                            Text(
                                text = "Tệp đang ở bản nháp; máy chủ sẽ gắn vào hồ sơ khi lưu thành công.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // =========================================================================
                    // THÔNG TIN THÊM (Tài khoản ngân hàng, ghi chú và thông tin cá nhân)
                    // =========================================================================
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
                                        placeholder = { Text("Vietcombank, Techcombank, MB Bank...") },
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
                                            value = draft.birthDate,
                                            onValueChange = { onUpdate(draft.copy(birthDate = it)) },
                                            label = { Text("Ngày sinh") },
                                            placeholder = { Text("DD/MM/YYYY") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = draft.cccdIssueDate,
                                            onValueChange = { onUpdate(draft.copy(cccdIssueDate = it)) },
                                            label = { Text("Ngày cấp CCCD") },
                                            placeholder = { Text("DD/MM/YYYY") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
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

                    // Row Delete Action ("Xóa dòng này")
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
private fun FormSelectDropdownField(
    label: String,
    selectedValue: String,
    placeholder: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
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
                        text = selectedValue.ifBlank { placeholder },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedValue.isNotBlank()) MaterialTheme.colorScheme.onSurface
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
                        text = { Text(option) },
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
    emptyHint: String,
    selectedFileName: String,
    onPickClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val hasFile = selectedFileName.isNotBlank()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasFile) SemanticColors.SuccessLight.copy(alpha = 0.5f)
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
            if (hasFile) {
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
                        text = "Đã chọn: $selectedFileName",
                        style = MaterialTheme.typography.labelMedium,
                        color = SemanticColors.SuccessLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickClick,
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
                    Text(if (hasFile) "Đổi tệp" else "Chọn tệp", style = MaterialTheme.typography.labelMedium)
                }

                if (hasFile) {
                    TextButton(
                        onClick = onClearClick,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Bỏ chọn", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. MÀN HÌNH "NHÂN SỰ" (`/direct-entry/workers` - Quản lý Người Lao Động)
// ============================================================================
private data class WorkerRelationScopeTab(
    val key: String,
    val label: String
)

private val WORKER_SCOPE_TABS = listOf(
    WorkerRelationScopeTab("managed", "Dự án tôi quản lý"),
    WorkerRelationScopeTab("recruited", "Người tôi tuyển"),
    WorkerRelationScopeTab("team", "Team tôi quản lý"),
    WorkerRelationScopeTab("uploader", "Tôi đã nhập"),
    WorkerRelationScopeTab("all", "Toàn bộ NLĐ")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkersAndDirectEntryScreen(
    workers: List<WorkerProfileEntity>,
    onOpenNewEntryScreen: () -> Unit,
    onProposeWorkerChange: (WorkerProfileEntity, String, String, String) -> Unit,
    onDirectEditWorker: (
        worker: WorkerProfileEntity,
        fullName: String,
        gender: String,
        projectDisplay: String,
        employmentStatus: String,
        phoneFull: String,
        cccdFull: String,
        bankName: String,
        bankAccountNumber: String,
        bankAccountHolder: String,
        note: String
    ) -> Unit,
    onShowMessage: (String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    BackHandler(onBack = onBackToDashboard)

    // Prioritize "Dự án tôi quản lý" (`managed`, index 0) by default per user instructions
    var selectedScopeTabIndex by remember { mutableIntStateOf(0) }
    val activeScopeKey = WORKER_SCOPE_TABS[selectedScopeTabIndex].key

    var searchQuery by remember { mutableStateOf("") }
    var selectedProjectFilter by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) }

    // Dialog states for the 3 Web actions: "Xem chi tiết", "Đề xuất thay đổi", "Sửa trực tiếp"
    var detailWorker by remember { mutableStateOf<WorkerProfileEntity?>(null) }
    var proposeWorker by remember { mutableStateOf<WorkerProfileEntity?>(null) }
    var directEditTarget by remember { mutableStateOf<WorkerProfileEntity?>(null) }

    val availableProjects = remember(workers) {
        workers.map { it.projectDisplay }.distinct().sorted()
    }

    val filteredWorkers = remember(workers, activeScopeKey, searchQuery, selectedProjectFilter, selectedStatusFilter) {
        workers.filter { w ->
            val matchScope = when (activeScopeKey) {
                "managed" -> w.scopeRelation == "managed"
                "recruited" -> w.scopeRelation == "recruited" || w.recruiterDisplay.contains("Hùng") || w.recruiterDisplay.contains("Hà")
                "team" -> w.scopeRelation in listOf("managed", "team", "recruited")
                "uploader" -> w.scopeRelation == "uploader" || w.lastDecisionSummary.contains("Vừa cấp mới")
                else -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                w.fullName.contains(searchQuery, ignoreCase = true) ||
                w.workerCode.contains(searchQuery, ignoreCase = true) ||
                w.projectDisplay.contains(searchQuery, ignoreCase = true) ||
                w.recruiterDisplay.contains(searchQuery, ignoreCase = true) ||
                w.bankName.contains(searchQuery, ignoreCase = true)
            val matchProject = selectedProjectFilter == null || w.projectDisplay == selectedProjectFilter
            val matchStatus = selectedStatusFilter == null || w.employmentStatus == selectedStatusFilter
            matchScope && matchQuery && matchProject && matchStatus
        }
    }

    val distinctProjectCount = remember(filteredWorkers) {
        filteredWorkers.map { it.projectDisplay }.distinct().size
    }
    val distinctRecruiterCount = remember(filteredWorkers) {
        filteredWorkers.map { it.recruiterDisplay }.distinct().size
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("workers_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Card + Scope Tabs ("Dự án tôi quản lý" prioritized first)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Người lao động",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Quản lý danh sách NLĐ theo dự án, theo dõi trạng thái làm việc và tài khoản ngân hàng.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action buttons: Cấp mới NLĐ + Xuất Excel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        OutlinedButton(
                            onClick = {
                                onShowMessage("Đã xuất danh sách ${filteredWorkers.size} người lao động ra tệp Excel (.xlsx).")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("export_workers_excel_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xuất Excel")
                        }
                    }

                    // Relation Scope Tabs (`Dự án tôi quản lý` default selected)
                    ScrollableTabRow(
                        selectedTabIndex = selectedScopeTabIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        WORKER_SCOPE_TABS.forEachIndexed { idx, tab ->
                            val selected = selectedScopeTabIndex == idx
                            Tab(
                                selected = selected,
                                onClick = { selectedScopeTabIndex = idx },
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

        // 2. Summary Strip matching `/direct-entry/workers` (Tổng số NLĐ | Số dự án | Số người tuyển)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatBox(
                    modifier = Modifier.weight(1f),
                    label = "TỔNG SỐ NLĐ",
                    value = "${filteredWorkers.size}"
                )
                SummaryStatBox(
                    modifier = Modifier.weight(1f),
                    label = "SỐ DỰ ÁN",
                    value = "$distinctProjectCount"
                )
                SummaryStatBox(
                    modifier = Modifier.weight(1f),
                    label = "NGƯỜI TUYỂN",
                    value = "$distinctRecruiterCount"
                )
            }
        }

        // 3. Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Tìm theo họ tên, mã NLĐ, dự án, người tuyển...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("worker_search_input")
                )

                // Status Filter Chips + Project Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedStatusFilter == null,
                        onClick = { selectedStatusFilter = null },
                        label = { Text("Mọi trạng thái") }
                    )
                    WORK_STATUS_OPTIONS.forEach { st ->
                        FilterChip(
                            selected = selectedStatusFilter == st,
                            onClick = {
                                selectedStatusFilter = if (selectedStatusFilter == st) null else st
                            },
                            label = { Text(st) }
                        )
                    }
                    availableProjects.forEach { proj ->
                        FilterChip(
                            selected = selectedProjectFilter == proj,
                            onClick = {
                                selectedProjectFilter = if (selectedProjectFilter == proj) null else proj
                            },
                            label = { Text(proj) }
                        )
                    }
                    if (selectedStatusFilter != null || selectedProjectFilter != null || searchQuery.isNotBlank()) {
                        TextButton(
                            onClick = {
                                selectedStatusFilter = null
                                selectedProjectFilter = null
                                searchQuery = ""
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

        // 4. Worker List matching `/direct-entry/workers` table rows & actions
        if (filteredWorkers.isEmpty()) {
            item {
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
                        Text(
                            text = "Không có người lao động phù hợp bộ lọc",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Thử chuyển tab phạm vi hoặc bấm 'Cấp mới NLĐ' để thêm hồ sơ người lao động mới.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredWorkers, key = { it.workerCode }) { worker ->
                WorkerOperationsRowCard(
                    worker = worker,
                    onViewDetail = { detailWorker = worker },
                    onProposeChange = { proposeWorker = worker },
                    onDirectEdit = { directEditTarget = worker }
                )
            }
        }
    }

    // =========================================================================
    // DIALOG 1: XEM CHI TIẾT (`WorkerDetailDialog` trên Web)
    // =========================================================================
    detailWorker?.let { w ->
        AlertDialog(
            onDismissRequest = { detailWorker = null },
            title = {
                Column {
                    Text(
                        text = w.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mã NLĐ: ${w.workerCode} · Giới tính: ${w.gender}",
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
                    DetailItemRow("Dự án quản lý", w.projectDisplay)
                    DetailItemRow("Nguồn & Loại hình", "${w.providerType} · ${w.laborType}")
                    DetailItemRow("Người tuyển", "${w.recruiterDisplay} (${w.teamDisplay})")
                    DetailItemRow("Ngày đầu đi làm", w.firstWorkDate)
                    DetailItemRow("Trạng thái làm việc", w.employmentStatus)
                    HorizontalDivider()
                    DetailItemRow("Số điện thoại", w.phoneFull.ifBlank { w.phoneMasked })
                    DetailItemRow("Số CCCD", w.cccdFull.ifBlank { w.cccdMasked })
                    DetailItemRow("Ngày sinh / Ngày cấp", "${w.birthDate.ifBlank { "—" }} · Cấp: ${w.cccdIssueDate.ifBlank { "—" }}")
                    DetailItemRow("Địa chỉ", w.address.ifBlank { "—" })
                    HorizontalDivider()
                    DetailItemRow("Ngân hàng", w.bankName)
                    DetailItemRow("Số tài khoản (STK)", w.bankAccountNumber.ifBlank { w.bankAccountMasked })
                    DetailItemRow("Chủ tài khoản", w.bankAccountHolder.ifBlank { w.fullName.uppercase() })
                    HorizontalDivider()
                    Text(
                        text = "Hồ sơ đính kèm:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    DetailItemRow("Ảnh NLĐ", w.portraitUri.ifBlank { "Chưa tải lên" })
                    DetailItemRow("CCCD mặt trước", w.cccdFrontUri.ifBlank { "Chưa tải lên" })
                    DetailItemRow("CCCD mặt sau", w.cccdBackUri.ifBlank { "Chưa tải lên" })
                    DetailItemRow("Hợp đồng lao động", w.contractUri.ifBlank { "Chưa tải lên" })
                    if (w.note.isNotBlank()) {
                        HorizontalDivider()
                        DetailItemRow("Ghi chú", w.note)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { detailWorker = null }) {
                    Text("Đóng")
                }
            }
        )
    }

    // =========================================================================
    // DIALOG 2: ĐỀ XUẤT THAY ĐỔI (`ProposeDrawer` trên Web: Hồ sơ / Ngân hàng / Trạng thái)
    // =========================================================================
    proposeWorker?.let { w ->
        var selectedTargetTab by remember { mutableIntStateOf(0) }
        val targetLabels = listOf(
            "Thông tin người lao động",
            "Thông tin tài khoản ngân hàng",
            "Trạng thái làm việc"
        )
        var proposedValue by remember(selectedTargetTab, w) {
            mutableStateOf(
                when (selectedTargetTab) {
                    0 -> "${w.fullName} - SĐT: ${w.phoneFull.ifBlank { w.phoneMasked }}"
                    1 -> "${w.bankAccountNumber.ifBlank { w.bankAccountMasked }} - ${w.bankName} (${w.bankAccountHolder.ifBlank { w.fullName.uppercase() }})"
                    else -> w.employmentStatus
                }
            )
        }
        var selectedNewStatus by remember { mutableStateOf(w.employmentStatus) }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { proposeWorker = null },
            title = {
                Column {
                    Text(
                        text = "Đề xuất thay đổi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${w.fullName} (${w.workerCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Chọn nhóm thông tin cần gửi yêu cầu phê duyệt:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    targetLabels.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = selectedTargetTab == idx,
                            onClick = { selectedTargetTab = idx },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (selectedTargetTab == 2) {
                        FormSelectDropdownField(
                            label = "Trạng thái làm việc đề xuất",
                            selectedValue = selectedNewStatus,
                            placeholder = "Chọn trạng thái",
                            options = WORK_STATUS_OPTIONS,
                            onSelect = {
                                selectedNewStatus = it
                                proposedValue = "Đổi trạng thái sang: $it"
                            },
                            testTag = "propose_status_dropdown"
                        )
                    } else {
                        OutlinedTextField(
                            value = proposedValue,
                            onValueChange = { proposedValue = it },
                            label = { Text("Nội dung đề xuất mới") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Lý do đề xuất thay đổi *") },
                        placeholder = { Text("Nhập lý do cụ thể để bộ phận duyệt kiểm tra...") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { proposeWorker = null }) {
                    Text("Hủy")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onProposeWorkerChange(
                            w,
                            targetLabels[selectedTargetTab],
                            proposedValue,
                            reason.ifBlank { "Cập nhật thông tin thực tế tại dự án" }
                        )
                        proposeWorker = null
                    }
                ) {
                    Text("Gửi đề xuất")
                }
            }
        )
    }

    // =========================================================================
    // DIALOG 3: SỬA TRỰC TIẾP (`Sửa trực tiếp` trên Web)
    // =========================================================================
    directEditTarget?.let { w ->
        var fullName by remember(w) { mutableStateOf(w.fullName) }
        var gender by remember(w) { mutableStateOf(w.gender) }
        var projectDisplay by remember(w) { mutableStateOf(w.projectDisplay) }
        var employmentStatus by remember(w) { mutableStateOf(w.employmentStatus) }
        var phoneFull by remember(w) { mutableStateOf(w.phoneFull.ifBlank { w.phoneMasked }) }
        var cccdFull by remember(w) { mutableStateOf(w.cccdFull.ifBlank { w.cccdMasked }) }
        var bankName by remember(w) { mutableStateOf(w.bankName) }
        var bankAccountNumber by remember(w) { mutableStateOf(w.bankAccountNumber.ifBlank { w.bankAccountMasked }) }
        var bankAccountHolder by remember(w) { mutableStateOf(w.bankAccountHolder.ifBlank { w.fullName.uppercase() }) }
        var note by remember(w) { mutableStateOf(w.note) }

        AlertDialog(
            onDismissRequest = { directEditTarget = null },
            title = {
                Column {
                    Text(
                        text = "Sửa trực tiếp hồ sơ NLĐ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mã NLĐ: ${w.workerCode}",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Họ và tên") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FormSelectDropdownField(
                        label = "Giới tính",
                        selectedValue = gender,
                        placeholder = "Chọn giới tính",
                        options = GENDER_OPTIONS,
                        onSelect = { gender = it },
                        testTag = "direct_edit_gender"
                    )
                    FormSelectDropdownField(
                        label = "Dự án",
                        selectedValue = projectDisplay,
                        placeholder = "Chọn dự án",
                        options = PROJECT_OPTIONS,
                        onSelect = { projectDisplay = it },
                        testTag = "direct_edit_project"
                    )
                    FormSelectDropdownField(
                        label = "Trạng thái làm việc",
                        selectedValue = employmentStatus,
                        placeholder = "Chọn trạng thái",
                        options = WORK_STATUS_OPTIONS,
                        onSelect = { employmentStatus = it },
                        testTag = "direct_edit_status"
                    )
                    OutlinedTextField(
                        value = phoneFull,
                        onValueChange = { phoneFull = it },
                        label = { Text("Số điện thoại") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cccdFull,
                        onValueChange = { cccdFull = it },
                        label = { Text("Số CCCD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Ngân hàng") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankAccountNumber,
                        onValueChange = { bankAccountNumber = it },
                        label = { Text("Số tài khoản (STK)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankAccountHolder,
                        onValueChange = { bankAccountHolder = it },
                        label = { Text("Chủ tài khoản") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Ghi chú") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { directEditTarget = null }) {
                    Text("Hủy")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDirectEditWorker(
                            w,
                            fullName,
                            gender,
                            projectDisplay,
                            employmentStatus,
                            phoneFull,
                            cccdFull,
                            bankName,
                            bankAccountNumber,
                            bankAccountHolder,
                            note
                        )
                        directEditTarget = null
                    }
                ) {
                    Text("Lưu thay đổi")
                }
            }
        )
    }
}

@Composable
private fun SummaryStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkerOperationsRowCard(
    worker: WorkerProfileEntity,
    onViewDetail: () -> Unit,
    onProposeChange: () -> Unit,
    onDirectEdit: () -> Unit
) {
    val statusColor = when (worker.employmentStatus) {
        "Đang làm" -> SemanticColors.SuccessLight
        "Đang chờ việc" -> SemanticColors.WarningLight
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
            // Top Row: Name + Employee Code + Employment Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = worker.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = worker.gender,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Mã NLĐ: ${worker.workerCode}",
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
                        text = worker.employmentStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Info Grid matching Web columns: Dự án | Ngày đầu tiên | Người tuyển | Trạng thái & Ngân hàng
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
                        text = "${worker.providerType} · ${worker.laborType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Ngân hàng: ${worker.bankAccountMasked} · ${worker.bankName}" +
                        if (worker.bankAccountHolder.isNotBlank()) " · ${worker.bankAccountHolder}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Request / Approval status row (`Yêu cầu` column on Web)
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // 3 Web Action Buttons (`RowActionCluster`: Xem chi tiết | Đề xuất thay đổi | Sửa trực tiếp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetail,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("worker_detail_${worker.workerCode}")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xem chi tiết", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onProposeChange,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("worker_propose_${worker.workerCode}")
                ) {
                    Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Đề xuất thay đổi", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onDirectEdit,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("worker_direct_edit_${worker.workerCode}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sửa trực tiếp", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(label: String, value: String) {
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
