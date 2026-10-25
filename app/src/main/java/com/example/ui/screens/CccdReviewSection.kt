package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.CccdReviewComparisonField
import com.example.data.CccdReviewQueueItem
import com.example.data.CccdReviewQueuePage
import com.example.data.CccdReviewStatusFilter
import com.example.data.CccdReviewSummary
import com.example.data.DirectEntryContract
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import java.io.File

internal fun reconciliationStatusUiLabel(status: String): String = when (status.trim().uppercase()) {
    "MISMATCH" -> "Lệch CCCD"
    "QR_UNREADABLE" -> "Không đọc được QR"
    "PENDING" -> "Chờ đọc QR"
    "MATCHED_INITIAL" -> "Khớp ban đầu"
    "RESOLVED" -> "Đã xử lý"
    else -> status
}

internal fun reconciliationStatusColor(status: String): Color = when (status.trim().uppercase()) {
    "MISMATCH" -> SemanticColors.ErrorLight
    "QR_UNREADABLE" -> SemanticColors.WarningLight
    "PENDING" -> Color(0xFF64748B)
    "MATCHED_INITIAL", "RESOLVED" -> SemanticColors.SuccessLight
    else -> Color(0xFF64748B)
}

internal fun qrScanStatusUiLabel(status: String): String = when (status.trim().uppercase()) {
    "DECODED" -> "Đã đọc QR"
    "UNREADABLE" -> "Không đọc được QR"
    "PENDING" -> "Chờ xử lý"
    "NOT_UPLOADED" -> "Chưa tải ảnh"
    else -> status
}

internal fun comparisonStatusUiLabel(status: String): String = when (status.trim().uppercase()) {
    "MATCH" -> "Khớp"
    "DIFFERENT" -> "Khác nhau"
    "ENTERED_BLANK" -> "Trống trên hồ sơ"
    "QR_MISSING" -> "Chưa có từ QR"
    else -> status
}

private fun countForStatusFilter(summary: CccdReviewSummary, filter: CccdReviewStatusFilter): Int = when (filter) {
    CccdReviewStatusFilter.NEEDS_REVIEW -> summary.needsReviewCount
    CccdReviewStatusFilter.MISMATCH -> summary.mismatchCount
    CccdReviewStatusFilter.QR_UNREADABLE -> summary.qrUnreadableCount
    CccdReviewStatusFilter.PENDING -> summary.pendingCount
    CccdReviewStatusFilter.MATCHED_INITIAL -> summary.matchedInitialCount
    CccdReviewStatusFilter.RESOLVED -> summary.resolvedCount
    CccdReviewStatusFilter.ALL -> summary.totalCount
}

@Composable
fun CccdReviewToolbarAndFilterCard(
    page: CccdReviewQueuePage?,
    selectedFilterCode: String,
    loading: Boolean,
    onSelectFilter: (String) -> Unit,
    onRefresh: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    val summary = page?.summary ?: CccdReviewSummary()
    val offset = page?.offset ?: 0
    val limit = page?.limit ?: 25
    val total = page?.totalCount ?: 0
    val currentPage = (offset / limit.coerceAtLeast(1)) + 1
    val hasPrev = offset > 0
    val hasNext = offset + (page?.rows?.size ?: 0) < total

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cccd_review_toolbar_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chuẩn hoá thông tin NLĐ (Đối chiếu QR CCCD)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "So sánh thông tin đã nhập với dữ liệu giải mã từ QR trên ảnh CCCD; sửa CCCD hoặc chuẩn hóa trực tiếp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.testTag("cccd_review_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Làm mới danh sách",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CccdReviewStatusFilter.entries.forEach { filter ->
                    val selected = selectedFilterCode.equals(filter.code, ignoreCase = true)
                    val count = countForStatusFilter(summary, filter)
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(filter.code) },
                        label = {
                            Text(
                                text = "${filter.label} ($count)",
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("cccd_review_filter_${filter.code}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tổng: $total hồ sơ · Trang $currentPage",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasPrev || hasNext) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onPreviousPage,
                            enabled = !loading && hasPrev,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Trang trước", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onNextPage,
                            enabled = !loading && hasNext,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Trang sau", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CccdReviewQueueItemCard(
    item: CccdReviewQueueItem,
    onOpenReview: (CccdReviewQueueItem) -> Unit
) {
    val badgeColor = reconciliationStatusColor(item.reconciliationStatus)
    val badgeLabel = reconciliationStatusUiLabel(item.reconciliationStatus)
    val qrCccd = item.qrExtractedPayload?.cccd12
    val qrName = item.qrExtractedPayload?.fullName

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cccd_review_row_${item.entryId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.enteredDisplayName.ifBlank { "(Chưa nhập họ tên)" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.employeeCode.ifBlank { "—" }} · ${item.projectDisplay.ifBlank { item.projectId }} · Ngày vào: ${DirectEntryContract.isoDateToUiDate(item.firstWorkDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.recruiterDisplay.isNotBlank()) {
                        Text(
                            text = "Người tuyển: ${item.recruiterDisplay}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CCCD đã nhập:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.enteredNationalId ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CCCD giải mã từ QR:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = qrCccd ?: "Chưa đọc được QR",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                qrCccd == null -> SemanticColors.WarningLight
                                qrCccd != item.enteredNationalId -> SemanticColors.ErrorLight
                                else -> SemanticColors.SuccessLight
                            }
                        )
                    }
                    if (!qrName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Họ tên từ QR:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = qrName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "Ảnh CCCD: Mặt trước (${qrScanStatusUiLabel(item.cccdFrontQrStatus)}) · Mặt sau (${qrScanStatusUiLabel(item.cccdBackQrStatus)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onOpenReview(item) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("cccd_review_open_button_${item.entryId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Xem & xử lý", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun CccdReviewDetailDialog(
    item: CccdReviewQueueItem,
    serverUrl: String,
    mutating: Boolean,
    mutationError: String?,
    onDismiss: () -> Unit,
    onRetryQr: (entryId: String, expectedVersion: Int) -> Unit,
    onReuploadDocument: (entryId: String, documentType: String, uri: Uri, expectedVersion: Int) -> Unit,
    onResolveUpdateEnteredCccd: (entryId: String, expectedVersion: Int, nationalId: String, applyBlankFieldsFromQr: Boolean, reason: String?) -> Unit,
    onResolveStandardizeFromQr: (entryId: String, expectedVersion: Int, fields: List<String>, reason: String?) -> Unit
) {
    val context = LocalContext.current
    val qrPayload = item.qrExtractedPayload
    val hasQrPayload = qrPayload != null

    // Resolution Mode: "UPDATE_CCCD" | "STANDARDIZE_QR"
    var activeMode by remember(item.entryId) {
        mutableStateOf(if (hasQrPayload) "STANDARDIZE_QR" else "UPDATE_CCCD")
    }
    var editedCccd by remember(item.entryId, item.enteredNationalId) {
        mutableStateOf(item.enteredNationalId.orEmpty())
    }
    var applyBlankFromQr by remember(item.entryId) { mutableStateOf(true) }
    var actionReason by remember(item.entryId) { mutableStateOf("") }
    var confirmingAction by remember(item.entryId, activeMode) { mutableStateOf(false) }

    val availableQrFields = remember(item) {
        item.comparison
            .filter { !it.qrValue.isNullOrBlank() && it.field in DirectEntryContract.ALLOWED_CCCD_STANDARDIZE_FIELDS }
            .map { it.field }
    }
    val selectedStandardizeFields = remember(item.entryId, availableQrFields) {
        mutableStateListOf<String>().apply {
            val defaultSelection = item.comparison
                .filter {
                    !it.qrValue.isNullOrBlank() &&
                        (it.status == "DIFFERENT" || it.status == "ENTERED_BLANK") &&
                        it.field in DirectEntryContract.ALLOWED_CCCD_STANDARDIZE_FIELDS
                }
                .map { it.field }
            if (defaultSelection.isNotEmpty()) {
                addAll(defaultSelection)
            } else {
                addAll(availableQrFields)
            }
        }
    }

    // Re-upload CCCD Front / Back via Album OR Camera
    var pendingDocType by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val docType = pendingDocType
        pendingDocType = null
        if (uri != null && docType != null) {
            onReuploadDocument(item.entryId, docType, uri, item.entryVersion)
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val docType = pendingDocType
        val camUri = pendingCameraUri
        pendingDocType = null
        pendingCameraUri = null
        if (success && camUri != null && docType != null) {
            onReuploadDocument(item.entryId, docType, camUri, item.entryVersion)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        val docType = pendingDocType
        if (granted && docType != null) {
            runCatching {
                val captureDir = File(context.cacheDir, "worker_captures").apply { mkdirs() }
                val photoFile = File(captureDir, "cccd_review_${docType.lowercase()}_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                pendingCameraUri = uri
                cameraCaptureLauncher.launch(uri)
            }
        } else {
            pendingDocType = null
        }
    }

    fun startCameraReupload(documentType: String) {
        pendingDocType = documentType
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            runCatching {
                val captureDir = File(context.cacheDir, "worker_captures").apply { mkdirs() }
                val photoFile = File(captureDir, "cccd_review_${documentType.lowercase()}_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                pendingCameraUri = uri
                cameraCaptureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun startGalleryReupload(documentType: String) {
        pendingDocType = documentType
        galleryPickerLauncher.launch("image/*")
    }

    Dialog(
        onDismissRequest = { if (!mutating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .testTag("cccd_review_detail_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xử lý & Chuẩn hóa thông tin NLĐ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.enteredDisplayName.ifBlank { "(Chưa nhập họ tên)" }} · ${item.employeeCode} · v${item.entryVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (!mutating) onDismiss() },
                        modifier = Modifier.testTag("close_cccd_review_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                // 1. Comparison Table (6 fields)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1. Bảng đối chiếu thông tin Đã nhập & Giải mã từ QR",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        item.comparison.forEach { comp ->
                            CccdComparisonFieldRow(comp = comp)
                        }
                    }
                }

                // 2. CCCD Images & Re-upload / Retry QR
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Ảnh CCCD & Giải mã lại QR",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { onRetryQr(item.entryId, item.entryVersion) },
                                enabled = !mutating,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("cccd_review_retry_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Đọc lại QR", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        CccdDocumentPreviewAndReuploadCard(
                            title = "CCCD Mặt trước",
                            qrStatus = item.cccdFrontQrStatus,
                            previewUrl = item.cccdFrontPreviewPath?.let { "${serverUrl.trimEnd('/')}$it" },
                            enabled = !mutating,
                            onCaptureCamera = { startCameraReupload("CCCD_FRONT") },
                            onPickGallery = { startGalleryReupload("CCCD_FRONT") }
                        )

                        CccdDocumentPreviewAndReuploadCard(
                            title = "CCCD Mặt sau",
                            qrStatus = item.cccdBackQrStatus,
                            previewUrl = item.cccdBackPreviewPath?.let { "${serverUrl.trimEnd('/')}$it" },
                            enabled = !mutating,
                            onCaptureCamera = { startCameraReupload("CCCD_BACK") },
                            onPickGallery = { startGalleryReupload("CCCD_BACK") }
                        )
                    }
                }

                // 3. Direct Resolution Actions
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "3. Thao tác chuẩn hóa & xử lý trực tiếp",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = activeMode == "STANDARDIZE_QR",
                                onClick = {
                                    activeMode = "STANDARDIZE_QR"
                                    confirmingAction = false
                                },
                                label = { Text("Chuẩn hóa từ QR", maxLines = 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cccd_review_mode_standardize_qr")
                            )
                            FilterChip(
                                selected = activeMode == "UPDATE_CCCD",
                                onClick = {
                                    activeMode = "UPDATE_CCCD"
                                    confirmingAction = false
                                },
                                label = { Text("Sửa CCCD đã nhập", maxLines = 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cccd_review_mode_update_cccd")
                            )
                        }

                        if (activeMode == "STANDARDIZE_QR") {
                            if (!hasQrPayload || availableQrFields.isEmpty()) {
                                Text(
                                    text = "Hồ sơ này chưa có dữ liệu giải mã từ QR. Vui lòng tải lại ảnh CCCD rõ nét và bấm 'Đọc lại QR', hoặc chuyển sang tab 'Sửa CCCD đã nhập'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SemanticColors.WarningLight
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedStandardizeFields.clear()
                                            item.comparison
                                                .filter {
                                                    !it.qrValue.isNullOrBlank() &&
                                                        it.status == "ENTERED_BLANK" &&
                                                        it.field in DirectEntryContract.ALLOWED_CCCD_STANDARDIZE_FIELDS
                                                }
                                                .forEach { selectedStandardizeFields.add(it.field) }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Chỉ chọn trường trống", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedStandardizeFields.clear()
                                            selectedStandardizeFields.addAll(availableQrFields)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Chọn tất cả từ QR", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                item.comparison.forEach { comp ->
                                    val canSelect = !comp.qrValue.isNullOrBlank() &&
                                        comp.field in DirectEntryContract.ALLOWED_CCCD_STANDARDIZE_FIELDS
                                    val isChecked = comp.field in selectedStandardizeFields
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = canSelect && !mutating) {
                                                if (isChecked) {
                                                    selectedStandardizeFields.remove(comp.field)
                                                } else {
                                                    selectedStandardizeFields.add(comp.field)
                                                }
                                                confirmingAction = false
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            enabled = canSelect && !mutating,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (comp.field !in selectedStandardizeFields) {
                                                        selectedStandardizeFields.add(comp.field)
                                                    }
                                                } else {
                                                    selectedStandardizeFields.remove(comp.field)
                                                }
                                                confirmingAction = false
                                            },
                                            modifier = Modifier.testTag("cccd_standardize_checkbox_${comp.field}")
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = comp.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Hiện tại: ${comp.enteredValue ?: "—"}  →  QR: ${comp.qrValue ?: "—"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = actionReason,
                                    onValueChange = { actionReason = it.take(500) },
                                    label = { Text("Ghi chú xử lý (tùy chọn)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cccd_review_reason_input")
                                )

                                if (!confirmingAction) {
                                    Button(
                                        onClick = { confirmingAction = true },
                                        enabled = !mutating && selectedStandardizeFields.isNotEmpty(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("cccd_review_prepare_standardize_button")
                                    ) {
                                        Text("Chuẩn hóa ${selectedStandardizeFields.size} trường đã chọn từ QR")
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { confirmingAction = false },
                                            enabled = !mutating,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Text("Quay lại")
                                        }
                                        Button(
                                            onClick = {
                                                onResolveStandardizeFromQr(
                                                    item.entryId,
                                                    item.entryVersion,
                                                    selectedStandardizeFields.toList(),
                                                    actionReason.trim().ifEmpty { null }
                                                )
                                            },
                                            enabled = !mutating && selectedStandardizeFields.isNotEmpty(),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(44.dp)
                                                .testTag("cccd_review_confirm_standardize_button")
                                        ) {
                                            if (mutating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text("Xác nhận chuẩn hóa")
                                        }
                                    }
                                }
                            }
                        } else {
                            val isValidEditedCccd = DirectEntryContract.isValidCccd12(editedCccd)
                            OutlinedTextField(
                                value = editedCccd,
                                onValueChange = { raw ->
                                    editedCccd = raw.filter { it.isDigit() }.take(12)
                                    confirmingAction = false
                                },
                                label = { Text("Số CCCD đúng (12 chữ số) *") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = editedCccd.isNotEmpty() && !isValidEditedCccd,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cccd_review_edit_cccd_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !mutating) {
                                        applyBlankFromQr = !applyBlankFromQr
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = applyBlankFromQr,
                                    enabled = !mutating,
                                    onCheckedChange = { applyBlankFromQr = it }
                                )
                                Text(
                                    text = "Tự động bổ sung các trường cá nhân đang trống từ QR nếu CCCD mới khớp với QR",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            OutlinedTextField(
                                value = actionReason,
                                onValueChange = { actionReason = it.take(500) },
                                label = { Text("Ghi chú xử lý (tùy chọn)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cccd_review_reason_input")
                            )

                            if (!confirmingAction) {
                                Button(
                                    onClick = { confirmingAction = true },
                                    enabled = !mutating && isValidEditedCccd,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("cccd_review_prepare_update_cccd_button")
                                ) {
                                    Text("Cập nhật CCCD & đối chiếu lại")
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { confirmingAction = false },
                                        enabled = !mutating,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Text("Quay lại")
                                    }
                                    Button(
                                        onClick = {
                                            onResolveUpdateEnteredCccd(
                                                item.entryId,
                                                item.entryVersion,
                                                editedCccd,
                                                applyBlankFromQr,
                                                actionReason.trim().ifEmpty { null }
                                            )
                                        },
                                        enabled = !mutating && isValidEditedCccd,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(44.dp)
                                            .testTag("cccd_review_confirm_update_cccd_button")
                                    ) {
                                        if (mutating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Xác nhận cập nhật CCCD")
                                    }
                                }
                            }
                        }

                        mutationError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CccdComparisonFieldRow(comp: CccdReviewComparisonField) {
    val statusColor = when (comp.status) {
        "MATCH" -> SemanticColors.SuccessLight
        "DIFFERENT" -> SemanticColors.ErrorLight
        "ENTERED_BLANK" -> SemanticColors.WarningLight
        else -> Color(0xFF64748B)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comp.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = comparisonStatusUiLabel(comp.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Đã nhập",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = comp.enteredValue?.ifBlank { "—" } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Từ QR CCCD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = comp.qrValue?.ifBlank { "—" } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (comp.status == "DIFFERENT") SemanticColors.ErrorLight else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CccdDocumentPreviewAndReuploadCard(
    title: String,
    qrStatus: String,
    previewUrl: String?,
    enabled: Boolean,
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = qrScanStatusUiLabel(qrStatus),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!previewUrl.isNullOrBlank()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Text(
                    text = "Chưa có ảnh $title trên máy chủ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCaptureCamera,
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chụp ảnh mới", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onPickGallery,
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chọn từ Album", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
