package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AppActor
import com.example.data.CccdQrDecoder
import com.example.data.DirectEntryCatalog
import com.example.data.DirectEntryContract
import com.example.data.NewWorkerEntryDraft
import com.example.data.OwnSubmissionItem
import com.example.data.PendingDuplicateCccdSubmission
import com.example.data.SalaryAdvanceActorContext
import com.example.data.SalaryAdvanceContract
import com.example.data.SalaryAdvanceManagedProjectOption
import com.example.data.SalaryAdvanceWorkerHistoryItem
import com.example.data.SalaryAdvanceWorkerHistoryResponse
import com.example.data.SalaryAdvanceWorkerOption
import com.example.data.StagedDocumentRef
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

private val ENTRY_STATUS_OPTIONS = listOf("Đang làm", "Đang chờ việc")
private val GENDER_OPTIONS = DirectEntryContract.DIRECT_ENTRY_GENDER_OPTIONS
private val WORK_STATUS_FILTER_OPTIONS = listOf(
    "ON" to "Đang làm",
    "UNCONFIRMED" to "Đang chờ việc",
    "OFF" to "Đã nghỉ"
)

internal fun submissionStateUiLabel(state: String): String = when (state.trim().uppercase()) {
    "DRAFT" -> "Bản nháp (chưa gửi chính thức)"
    "REVIEW" -> "Đang chờ duyệt"
    "SUBMITTED" -> "Đã gửi chính thức"
    else -> state
}

private fun sanitizeSingleLineInput(raw: String, maxLen: Int): String =
    raw.filter { ch -> ch.code >= 32 && ch.code != 127 }.take(maxLen)

private fun sanitizeMultiLineInput(raw: String, maxLen: Int): String =
    raw.filter { ch -> (ch.code >= 32 && ch.code != 127) || ch == '\n' || ch == '\t' }.take(maxLen)

private fun sanitizePhoneInput(raw: String): String =
    raw.filter { ch -> ch.isDigit() || ch == '+' || ch == '-' || ch == ' ' || ch == '.' || ch == '(' || ch == ')' }
        .take(64)

data class DropdownOptionItem(
    val id: String,
    val label: String,
    val subtitle: String = "",
    val searchKeywords: String = ""
)

private val COMBINING_DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
private val MULTI_SPACE_REGEX = Regex("\\s+")

internal fun normalizeVietnameseForSearch(raw: String): String {
    if (raw.isBlank()) return ""
    val decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
    return COMBINING_DIACRITICS_REGEX.replace(decomposed, "")
        .replace('đ', 'd')
        .replace('Đ', 'd')
        .lowercase(Locale.ROOT)
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()
}

internal fun filterDropdownOptions(
    options: List<DropdownOptionItem>,
    query: String
): List<DropdownOptionItem> {
    val normalizedQuery = normalizeVietnameseForSearch(query)
    if (normalizedQuery.isEmpty()) return options
    val tokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return options

    val matched = options.filter { option ->
        val searchable = normalizeVietnameseForSearch(
            "${option.label} ${option.subtitle} ${option.searchKeywords}"
        )
        tokens.all { token -> searchable.contains(token) }
    }
    return matched.sortedBy { option ->
        val normLabel = normalizeVietnameseForSearch(option.label)
        if (normLabel.startsWith(normalizedQuery)) 0 else 1
    }
}

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

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val catalogLoading by viewModel.catalogLoading.collectAsStateWithLifecycle()
    val catalogError by viewModel.catalogError.collectAsStateWithLifecycle()
    val ownSubmissions by viewModel.ownSubmissions.collectAsStateWithLifecycle()
    val mutationBusy by viewModel.workerMutationBusy.collectAsStateWithLifecycle()
    val mutationError by viewModel.workerMutationError.collectAsStateWithLifecycle()
    val pendingDuplicateSubmission by viewModel.pendingDuplicateCccdSubmission.collectAsStateWithLifecycle()
    val profileSaveStatusMessage by viewModel.profileSaveStatusMessage.collectAsStateWithLifecycle()
    val photoUploadStatusMessage by viewModel.photoUploadStatusMessage.collectAsStateWithLifecycle()
    val backgroundUploadActiveCount by viewModel.backgroundUploadActiveCount.collectAsStateWithLifecycle()
    val hasFailedBackgroundUploads by viewModel.hasFailedBackgroundUploads.collectAsStateWithLifecycle()

    val effectiveUploadStageFile: (String, String, Uri, (StagedDocumentRef) -> Unit, (String) -> Unit) -> Unit =
        onUploadStageFile ?: { _, documentType, uri, onStaged, onError ->
            viewModel.prepareLocalStageDocument(documentType, uri, onStaged, onError)
        }
    val effectiveDiscardRef: (StagedDocumentRef?) -> Unit = { ref ->
        if (ref != null) {
            if (onDeleteStageId != null) {
                if (ref.stageId.isNotBlank()) onDeleteStageId(ref.stageId)
            } else {
                viewModel.discardDraftAttachment(ref)
            }
        }
    }

    LaunchedEffect(actor.appUserId) {
        viewModel.loadCatalogIfNeeded()
        if (actor.hasCapability("submission_read") || actor.hasCapability("submission_create")) {
            viewModel.refreshOwnSubmissionsForHistory()
        }
    }

    var showBatchHistoryDialog by remember { mutableStateOf(false) }
    var scanningQrRowId by remember { mutableStateOf<String?>(null) }

    val draftRows = remember(viewModel) {
        viewModel.ensureInitialNewWorkerDraftRow(catalog)
        viewModel.newWorkerDraftRows
    }
    if (draftRows.isEmpty()) {
        viewModel.ensureInitialNewWorkerDraftRow(catalog)
    }

    LaunchedEffect(catalog) {
        val loadedCatalog = catalog ?: return@LaunchedEffect
        val defaultLabor = DirectEntryContract.defaultSeasonalLaborType(loadedCatalog.laborTypes) ?: return@LaunchedEffect
        for (i in draftRows.indices) {
            val cur = draftRows[i]
            if (cur.laborTypeKey.isBlank()) {
                draftRows[i] = cur.copy(
                    laborTypeKey = defaultLabor.laborTypeKey,
                    laborTypeDisplay = defaultLabor.displayName
                )
            }
        }
    }

    fun attachDocumentUriToRow(rowId: String, documentType: String, uri: Uri) {
        val index = draftRows.indexOfFirst { it.localId == rowId }
        if (index < 0) return
        val existingRef = when (documentType) {
            "WORKER_PORTRAIT" -> draftRows[index].portraitStage
            "CCCD_FRONT" -> draftRows[index].cccdFrontStage
            "CCCD_BACK" -> draftRows[index].cccdBackStage
            "EMPLOYMENT_CONTRACT" -> draftRows[index].contractStage
            else -> null
        }
        draftRows[index] = draftRows[index].copy(uploadingSlot = documentType, slotError = null)
        effectiveUploadStageFile(
            rowId,
            documentType,
            uri,
            { staged ->
                val currentIdx = draftRows.indexOfFirst { it.localId == rowId }
                if (currentIdx >= 0) {
                    effectiveDiscardRef(existingRef)
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
    }

    fun attachMultiImagesAsPdfToRow(rowId: String, documentType: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val index = draftRows.indexOfFirst { it.localId == rowId }
        if (index < 0) return
        if (onUploadStageFile != null) {
            attachDocumentUriToRow(rowId, documentType, uris.first())
            return
        }
        val existingRef = when (documentType) {
            "EMPLOYMENT_CONTRACT" -> draftRows[index].contractStage
            else -> null
        }
        draftRows[index] = draftRows[index].copy(uploadingSlot = documentType, slotError = null)
        viewModel.prepareLocalMultiImagePdfStageDocument(
            documentType = documentType,
            uris = uris,
            existingRef = existingRef,
            onPrepared = { staged ->
                val currentIdx = draftRows.indexOfFirst { it.localId == rowId }
                if (currentIdx >= 0) {
                    if (existingRef != null && existingRef.stageId.isNotBlank()) {
                        effectiveDiscardRef(existingRef.copy(localFilePath = null, sourceImagePaths = emptyList()))
                    }
                    val cur = draftRows[currentIdx]
                    draftRows[currentIdx] = when (documentType) {
                        "EMPLOYMENT_CONTRACT" -> cur.copy(contractStage = staged, uploadingSlot = null, slotError = null)
                        else -> cur.copy(uploadingSlot = null)
                    }
                }
            },
            onError = { errMsg ->
                val currentIdx = draftRows.indexOfFirst { it.localId == rowId }
                if (currentIdx >= 0) {
                    draftRows[currentIdx] = draftRows[currentIdx].copy(
                        uploadingSlot = null,
                        slotError = errMsg
                    )
                }
            }
        )
    }

    var pendingFileTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingMultiImageTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = pendingFileTarget
        pendingFileTarget = null
        if (uri != null && target != null) {
            attachDocumentUriToRow(target.first, target.second, uri)
        }
    }

    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val target = pendingMultiImageTarget
        pendingMultiImageTarget = null
        if (uris.isNotEmpty() && target != null) {
            attachMultiImagesAsPdfToRow(target.first, target.second, uris)
        }
    }

    var pendingCameraCapture by remember { mutableStateOf<Triple<String, String, Uri>?>(null) }
    var pendingCameraPermissionTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = pendingCameraCapture
        pendingCameraCapture = null
        if (success && capture != null) {
            if (capture.second == "EMPLOYMENT_CONTRACT") {
                attachMultiImagesAsPdfToRow(capture.first, capture.second, listOf(capture.third))
            } else {
                attachDocumentUriToRow(capture.first, capture.second, capture.third)
            }
        }
    }

    fun startCameraForRowNow(rowId: String, documentType: String) {
        runCatching {
            val dir = File(context.cacheDir, "worker_captures").apply { mkdirs() }
            val file = File(dir, "capture_${documentType.lowercase(Locale.ROOT)}_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraCapture = Triple(rowId, documentType, uri)
            cameraLauncher.launch(uri)
        }.onFailure { err ->
            val currentIdx = draftRows.indexOfFirst { it.localId == rowId }
            if (currentIdx >= 0) {
                draftRows[currentIdx] = draftRows[currentIdx].copy(
                    slotError = "Không thể mở máy ảnh: ${err.message.orEmpty()}"
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingCameraPermissionTarget
        pendingCameraPermissionTarget = null
        if (granted && target != null) {
            startCameraForRowNow(target.first, target.second)
        }
    }

    fun pickDocumentForRow(rowId: String, documentType: String) {
        focusManager.clearFocus()
        keyboardController?.hide()
        pendingFileTarget = rowId to documentType
        val mimeFilter = if (documentType == "EMPLOYMENT_CONTRACT") "application/pdf" else "image/*"
        filePickerLauncher.launch(mimeFilter)
    }

    fun pickMultipleImagesForRow(rowId: String, documentType: String) {
        focusManager.clearFocus()
        keyboardController?.hide()
        pendingMultiImageTarget = rowId to documentType
        multiImagePickerLauncher.launch("image/*")
    }

    fun capturePhotoForRow(rowId: String, documentType: String) {
        focusManager.clearFocus()
        keyboardController?.hide()
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            startCameraForRowNow(rowId, documentType)
        } else {
            pendingCameraPermissionTarget = rowId to documentType
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val validCount = draftRows.count { it.isValid }
    val draftBatchCount = ownSubmissions.count { it.state == "DRAFT" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
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
                                text = "Dữ liệu hồ sơ được lưu ngay lên hệ thống; ảnh và giấy tờ đính kèm được tối ưu và tự động tải ngầm.",
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
                                    color = if (draftBatchCount > 0) {
                                        SemanticColors.WarningLight.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ) {
                                    Text(
                                        text = if (draftBatchCount > 0) {
                                            "${ownSubmissions.size} đợt ($draftBatchCount nháp)"
                                        } else {
                                            "${ownSubmissions.size} đợt"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (draftBatchCount > 0) {
                                            SemanticColors.WarningLight
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    showBatchHistoryDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Mở", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    ProfileAndPhotoSyncStatusCards(
                        profileSaveStatusMessage = profileSaveStatusMessage,
                        photoUploadStatusMessage = photoUploadStatusMessage,
                        backgroundUploadActiveCount = backgroundUploadActiveCount,
                        hasFailedBackgroundUploads = hasFailedBackgroundUploads,
                        onRetryFailedUploads = { viewModel.retryFailedBackgroundUploads() }
                    )
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
                onUpdate = { updated ->
                    draftRows[index] = updated
                    runCatching { DirectEntryContract.uiDateToIsoDate(updated.firstWorkDateUi) }
                        .getOrNull()
                        ?.let { iso -> viewModel.loadCatalogIfNeeded(iso) }
                },
                onRemove = {
                    row.allAttachments().forEach { ref -> effectiveDiscardRef(ref) }
                    if (draftRows.size > 1) {
                        draftRows.removeAt(index)
                    } else {
                        draftRows[0] = DirectEntryContract.createDefaultWorkerDraft(catalog = catalog)
                    }
                },
                onPickFile = { docType -> pickDocumentForRow(row.localId, docType) },
                onPickMultipleImages = { docType -> pickMultipleImagesForRow(row.localId, docType) },
                onCaptureCamera = { docType -> capturePhotoForRow(row.localId, docType) },
                onScanQrClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    scanningQrRowId = row.localId
                },
                onClearStage = { docType, ref ->
                    effectiveDiscardRef(ref)
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
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                draftRows.add(
                                    DirectEntryContract.createDefaultWorkerDraft(catalog = catalog)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("bottom_add_worker_row_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thêm NLĐ")
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                val validRows = draftRows.filter { it.isValid }
                                if (validRows.isNotEmpty() && !mutationBusy) {
                                    val clearSavedRows: () -> Unit = {
                                        val remaining = draftRows.filter { !it.isValid && it.isStarted }
                                        draftRows.clear()
                                        if (remaining.isNotEmpty()) {
                                            draftRows.addAll(remaining)
                                        } else {
                                            draftRows.add(
                                                DirectEntryContract.createDefaultWorkerDraft(catalog = catalog)
                                            )
                                        }
                                    }
                                    if (onSubmitValidDrafts != null) {
                                        onSubmitValidDrafts(validRows) {
                                            clearSavedRows()
                                            onNavigateToWorkersList()
                                        }
                                    } else {
                                        viewModel.submitFullProfileDrafts(
                                            validDrafts = validRows,
                                            onDraftsSaved = clearSavedRows,
                                            onSubmittedSuccess = onNavigateToWorkersList
                                        )
                                    }
                                }
                            },
                            enabled = validCount > 0 && !mutationBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("save_valid_workers_button")
                        ) {
                            if (mutationBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (validCount > 0) "Lưu các NLĐ hợp lệ ($validCount)" else "Lưu các NLĐ hợp lệ",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    mutationError?.let { errMsg ->
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (validCount == 0) {
                        Text(
                            text = "Điền các trường có dấu * từ danh mục máy chủ (Dự án, HRP/Vendor, Người tuyển, Loại hình LĐ, Ngày bắt đầu DD/MM/YYYY, Số CCCD 12 chữ số) để bật nút Lưu.",
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ownSubmissions.isEmpty()) {
                        Text(
                            text = "Chưa có đợt nhập liệu nào được ghi nhận trên máy chủ.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ownSubmissions.take(15).forEach { sub ->
                            val canSubmitDraft = sub.state == "DRAFT" || "SUBMITTED" in sub.allowedTransitions
                            val formattedEntryTime = DirectEntryContract.formatIsoTimestampHcmUi(
                                isoOrNull = sub.submittedAt,
                                fallbackIso = sub.createdAt.ifBlank { sub.updatedAt }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Đợt #${sub.submissionId.take(8)} · ${sub.entryCount} NLĐ",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (formattedEntryTime.isNotBlank()) {
                                        Text(
                                            text = "Thời gian nhập: $formattedEntryTime",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMono,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = submissionStateUiLabel(sub.state),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (sub.state == "DRAFT") {
                                            SemanticColors.WarningLight
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (canSubmitDraft) {
                                    Button(
                                        onClick = {
                                            showBatchHistoryDialog = false
                                            viewModel.submitExistingDraftSubmission(sub)
                                        },
                                        enabled = !mutationBusy,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Gửi chính thức", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
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

    scanningQrRowId?.let { targetRowId ->
        CccdQrScannerDialog(
            onDismiss = { scanningQrRowId = null },
            onDecoded = { qr ->
                val idx = draftRows.indexOfFirst { it.localId == targetRowId }
                if (idx >= 0) {
                    draftRows[idx] = CccdQrDecoder.applyQrPayloadToDraft(
                        draft = draftRows[idx],
                        qr = qr,
                        overwriteExisting = true
                    )
                    viewModel.showBanner("Đã quét mã QR CCCD (${qr.cccd12}) và điền thông tin NLĐ.")
                }
                scanningQrRowId = null
            }
        )
    }

    pendingDuplicateSubmission?.let { pending ->
        DuplicateCccdSubmitConfirmDialog(
            pending = pending,
            busy = mutationBusy,
            error = mutationError,
            onDismissKeepDraft = { viewModel.dismissDuplicateCccdConfirmation() },
            onConfirmSubmit = {
                viewModel.confirmDuplicateCccdSubmission(onNavigateToWorkers = onNavigateToWorkersList)
            }
        )
    }
}

@Composable
internal fun DuplicateCccdSubmitConfirmDialog(
    pending: PendingDuplicateCccdSubmission,
    busy: Boolean,
    error: String?,
    onDismissKeepDraft: () -> Unit,
    onConfirmSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismissKeepDraft() },
        modifier = Modifier.testTag("duplicate_cccd_confirm_dialog"),
        title = {
            Text(
                text = "Phát hiện CCCD đã tồn tại trên hệ thống",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Đợt nhập có ${pending.preflight.conflictCount} cảnh báo trùng CCCD. Bạn vẫn muốn xác nhận gửi chính thức lên hệ thống?",
                    style = MaterialTheme.typography.bodySmall
                )
                pending.preflight.conflicts.forEach { conflict ->
                    Text(
                        text = conflict.formatConflictLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = SemanticColors.WarningLight
                    )
                }
                error?.let { errMsg ->
                    Text(
                        text = errMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissKeepDraft,
                enabled = !busy,
                modifier = Modifier.testTag("keep_draft_duplicate_cccd_button")
            ) {
                Text("Giữ ở bản nháp")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSubmit,
                enabled = !busy,
                modifier = Modifier.testTag("confirm_submit_duplicate_cccd_button")
            ) {
                Text(if (busy) "Đang gửi…" else "Xác nhận gửi chính thức")
            }
        }
    )
}

@Composable
internal fun ProfileAndPhotoSyncStatusCards(
    profileSaveStatusMessage: String?,
    photoUploadStatusMessage: String?,
    backgroundUploadActiveCount: Int,
    hasFailedBackgroundUploads: Boolean,
    onRetryFailedUploads: () -> Unit
) {
    if (!profileSaveStatusMessage.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SemanticColors.SuccessLight.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, SemanticColors.SuccessLight.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_save_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SemanticColors.SuccessLight,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trạng thái lưu hồ sơ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SemanticColors.SuccessLight
                    )
                    Text(
                        text = profileSaveStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    val showPhotoCard = backgroundUploadActiveCount > 0 ||
        hasFailedBackgroundUploads ||
        !photoUploadStatusMessage.isNullOrBlank()
    if (showPhotoCard) {
        val statusColor = when {
            hasFailedBackgroundUploads -> SemanticColors.WarningLight
            backgroundUploadActiveCount > 0 -> MaterialTheme.colorScheme.primary
            else -> SemanticColors.SuccessLight
        }
        val resolvedPhotoMsg = photoUploadStatusMessage
            ?: if (backgroundUploadActiveCount > 0) {
                "Đang tải ngầm $backgroundUploadActiveCount ảnh & giấy tờ đính kèm…"
            } else {
                "Ảnh đính kèm chưa hoàn tất tải lên."
            }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("photo_upload_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (backgroundUploadActiveCount > 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Icon(
                        imageVector = if (hasFailedBackgroundUploads) Icons.Default.FileUpload else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trạng thái tải ảnh",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = resolvedPhotoMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (hasFailedBackgroundUploads) {
                    OutlinedButton(
                        onClick = onRetryFailedUploads,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("retry_photo_upload_button")
                    ) {
                        Text("Thử lại tải ảnh", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
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
    onPickMultipleImages: (documentType: String) -> Unit = {},
    onCaptureCamera: (documentType: String) -> Unit = {},
    onScanQrClick: () -> Unit = {},
    onClearStage: (documentType: String, ref: StagedDocumentRef) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val statusText = when {
        draft.uploadingSlot != null -> "Đang xử lý tệp đính kèm…"
        !draft.isStarted -> "Chưa nhập"
        draft.isValid -> "Hợp lệ"
        else -> "Đang nhập (thiếu trường *)"
    }
    val statusColor = when {
        !draft.isStarted -> MaterialTheme.colorScheme.onSurfaceVariant
        draft.isValid -> SemanticColors.SuccessLight
        else -> SemanticColors.WarningLight
    }
    val headerName = draft.fullName.trim().ifBlank {
        if (draft.cccd.isNotBlank()) "CCCD ${draft.cccd.trim()}" else "Chưa nhập họ tên"
    }

    val projectOptions = remember(catalog) {
        catalog?.projects?.map { proj ->
            DropdownOptionItem(
                id = proj.projectId,
                label = proj.displayName,
                searchKeywords = "${proj.displayName} ${proj.projectId}"
            )
        }.orEmpty()
    }
    val providerOptions = listOf(
        DropdownOptionItem("hrp", "HRP"),
        DropdownOptionItem("vendor", "Vendor")
    )
    val recruiterOptions = remember(catalog, draft.providerType) {
        catalog?.recruiters
            ?.filter { it.providerType == draft.providerType }
            ?.map { rec ->
                val metaParts = listOfNotNull(
                    rec.personnelCode.takeIf { it.isNotBlank() },
                    rec.teamDisplayName?.takeIf { it.isNotBlank() }
                )
                DropdownOptionItem(
                    id = rec.recruiterId,
                    label = rec.label,
                    subtitle = metaParts.joinToString(" · "),
                    searchKeywords = listOfNotNull(
                        rec.displayName,
                        rec.personnelCode,
                        rec.teamDisplayName,
                        rec.label
                    ).joinToString(" ")
                )
            }
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
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onUpdate(draft.copy(isExpanded = !draft.isExpanded))
                    }
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

                    SearchableSelectDropdownField(
                        label = "Dự án *",
                        selectedLabel = draft.projectDisplay,
                        placeholder = if (projectOptions.isEmpty()) "Đang chờ danh mục dự án" else "Chọn dự án",
                        searchPlaceholder = "Nhập tên dự án để tìm nhanh…",
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

                    SearchableSelectDropdownField(
                        label = "Người tuyển / Vendor *",
                        selectedLabel = draft.recruiterDisplay,
                        placeholder = if (draft.providerType.isBlank()) "Chọn HRP/Vendor trước" else "Chọn hoặc nhập tên người tuyển",
                        searchPlaceholder = "Nhập tên, mã nhân sự hoặc nhóm để tìm nhanh…",
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

                    val startDateInvalid = draft.firstWorkDateUi.isNotBlank() &&
                        !DirectEntryContract.isValidUiOrIsoDate(draft.firstWorkDateUi)
                    val cccdInvalid = draft.cccd.isNotEmpty() &&
                        !DirectEntryContract.isValidCccd12(draft.cccd)

                    OutlinedTextField(
                        value = draft.firstWorkDateUi,
                        onValueChange = { onUpdate(draft.copy(firstWorkDateUi = sanitizeSingleLineInput(it, 10))) },
                        label = { Text("Ngày bắt đầu làm việc *") },
                        placeholder = { Text("DD/MM/YYYY") },
                        isError = startDateInvalid,
                        supportingText = if (startDateInvalid) {
                            { Text("Nhập đúng ngày thực tế định dạng DD/MM/YYYY") }
                        } else null,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thông tin CCCD & cá nhân",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = onScanQrClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("row_${rowNumber}_scan_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quét QR CCCD", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedTextField(
                        value = draft.cccd,
                        onValueChange = { onUpdate(draft.copy(cccd = it.filter { ch -> ch.isDigit() }.take(12))) },
                        label = { Text("Số CCCD (12 chữ số) *") },
                        placeholder = { Text("Nhập đúng 12 chữ số CCCD") },
                        isError = cccdInvalid,
                        supportingText = if (cccdInvalid) {
                            { Text("Số CCCD bắt buộc gồm đúng 12 chữ số (giữ nguyên số 0 ở đầu)") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_cccd")
                    )

                    OutlinedTextField(
                        value = draft.fullName,
                        onValueChange = { onUpdate(draft.copy(fullName = sanitizeSingleLineInput(it, 256))) },
                        label = { Text("Họ và tên") },
                        placeholder = { Text("Không bắt buộc (hệ thống tự điền từ QR CCCD nếu để trống)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_full_name")
                    )

                    KeyedSelectDropdownField(
                        label = "Giới tính",
                        selectedLabel = draft.genderUi,
                        placeholder = "",
                        options = listOf(DropdownOptionItem("", "— Để trống —")) +
                            GENDER_OPTIONS.map { DropdownOptionItem(it, it) },
                        onSelect = { opt -> onUpdate(draft.copy(genderUi = opt.id)) },
                        testTag = "row_${rowNumber}_gender"
                    )

                    OutlinedTextField(
                        value = draft.phone,
                        onValueChange = { onUpdate(draft.copy(phone = sanitizePhoneInput(it))) },
                        label = { Text("Số điện thoại") },
                        placeholder = { Text("09xxxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("row_${rowNumber}_phone")
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
                                text = "Ảnh & giấy tờ đính kèm",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Chọn ảnh từ Album hoặc chụp trực tiếp bằng Máy ảnh (tối đa 10 MB mỗi tệp).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            draft.slotError?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            DocumentUploadSlotCard(
                                title = "Ảnh chân dung NLĐ",
                                subtitle = "Định dạng JPG hoặc PNG (tối đa 10 MB)",
                                staged = draft.portraitStage,
                                uploading = draft.uploadingSlot == "WORKER_PORTRAIT",
                                onPickClick = { onPickFile("WORKER_PORTRAIT") },
                                onCaptureCameraClick = { onCaptureCamera("WORKER_PORTRAIT") },
                                slotTestTagPrefix = "row_${rowNumber}_slot_WORKER_PORTRAIT",
                                onClearClick = {
                                    draft.portraitStage?.let { onClearStage("WORKER_PORTRAIT", it) }
                                }
                            )

                            DocumentUploadSlotCard(
                                title = "CCCD mặt trước",
                                subtitle = "Chụp trực tiếp hoặc chọn ảnh từ Album (tối đa 10 MB)",
                                staged = draft.cccdFrontStage,
                                uploading = draft.uploadingSlot == "CCCD_FRONT",
                                onPickClick = { onPickFile("CCCD_FRONT") },
                                onCaptureCameraClick = { onCaptureCamera("CCCD_FRONT") },
                                slotTestTagPrefix = "row_${rowNumber}_slot_CCCD_FRONT",
                                onClearClick = {
                                    draft.cccdFrontStage?.let { onClearStage("CCCD_FRONT", it) }
                                }
                            )

                            DocumentUploadSlotCard(
                                title = "CCCD mặt sau",
                                subtitle = "Chụp trực tiếp hoặc chọn ảnh từ Album (tối đa 10 MB)",
                                staged = draft.cccdBackStage,
                                uploading = draft.uploadingSlot == "CCCD_BACK",
                                onPickClick = { onPickFile("CCCD_BACK") },
                                onCaptureCameraClick = { onCaptureCamera("CCCD_BACK") },
                                slotTestTagPrefix = "row_${rowNumber}_slot_CCCD_BACK",
                                onClearClick = {
                                    draft.cccdBackStage?.let { onClearStage("CCCD_BACK", it) }
                                }
                            )

                            DocumentUploadSlotCard(
                                title = "Hợp đồng lao động / Hồ sơ",
                                subtitle = "Chụp/chọn nhiều trang ảnh (tự gộp 1 file PDF) hoặc chọn tệp PDF (tối đa 10 MB)",
                                staged = draft.contractStage,
                                uploading = draft.uploadingSlot == "EMPLOYMENT_CONTRACT",
                                onPickClick = { onPickFile("EMPLOYMENT_CONTRACT") },
                                onCaptureCameraClick = { onCaptureCamera("EMPLOYMENT_CONTRACT") },
                                onPickMultipleImagesClick = { onPickMultipleImages("EMPLOYMENT_CONTRACT") },
                                slotTestTagPrefix = "row_${rowNumber}_slot_EMPLOYMENT_CONTRACT",
                                onClearClick = {
                                    draft.contractStage?.let { onClearStage("EMPLOYMENT_CONTRACT", it) }
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
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
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
                                    text = "Thông tin thêm: địa chỉ, ngày sinh, ngày cấp CCCD, tài khoản ngân hàng và ghi chú",
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
                                        value = draft.address,
                                        onValueChange = { onUpdate(draft.copy(address = sanitizeSingleLineInput(it, 1024))) },
                                        label = { Text("Địa chỉ thường trú / quê quán") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draft.birthDateUi,
                                            onValueChange = { onUpdate(draft.copy(birthDateUi = sanitizeSingleLineInput(it, 10))) },
                                            label = { Text("Ngày sinh") },
                                            placeholder = { Text("DD/MM/YYYY") },
                                            isError = !DirectEntryContract.isValidFreeDateText(draft.birthDateUi),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = draft.cccdIssueDateUi,
                                            onValueChange = { onUpdate(draft.copy(cccdIssueDateUi = sanitizeSingleLineInput(it, 10))) },
                                            label = { Text("Ngày cấp CCCD") },
                                            placeholder = { Text("DD/MM/YYYY") },
                                            isError = !DirectEntryContract.isValidFreeDateText(draft.cccdIssueDateUi),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = draft.bankAccountNumber,
                                        onValueChange = { onUpdate(draft.copy(bankAccountNumber = sanitizeSingleLineInput(it, 64))) },
                                        label = { Text("STK (Số tài khoản)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.bankAccountHolder,
                                        onValueChange = { onUpdate(draft.copy(bankAccountHolder = sanitizeSingleLineInput(it, 256))) },
                                        label = { Text("Tên chủ tài khoản") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.bankName,
                                        onValueChange = { onUpdate(draft.copy(bankName = sanitizeSingleLineInput(it, 256))) },
                                        label = { Text("Ngân hàng") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draft.note,
                                        onValueChange = { onUpdate(draft.copy(note = sanitizeMultiLineInput(it, 4000))) },
                                        label = { Text("Ghi chú") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    var showResetConfirmDialog by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                showResetConfirmDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("row_${rowNumber}_reset_or_remove_button")
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

                    if (showResetConfirmDialog) {
                        val cancelFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            runCatching { cancelFocusRequester.requestFocus() }
                        }
                        AlertDialog(
                            onDismissRequest = { showResetConfirmDialog = false },
                            modifier = Modifier.testTag("confirm_reset_worker_row_dialog"),
                            title = {
                                Text(
                                    text = if (canRemove) "Xác nhận xóa dòng nhập liệu" else "Xác nhận làm mới dòng này",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = if (canRemove) {
                                        "Dòng nhập liệu của NLĐ $rowNumber sẽ bị xóa nếu tiếp tục. Bấm Hủy để giữ lại dữ liệu đang nhập, hoặc bấm Đồng ý để xóa."
                                    } else {
                                        "Dữ liệu đang nhập ở dòng này sẽ bị xóa nếu tiếp tục. Bấm Hủy để giữ lại dữ liệu đang nhập, hoặc bấm Đồng ý để làm mới."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            dismissButton = null,
                            confirmButton = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            showResetConfirmDialog = false
                                            onRemove()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("confirm_reset_worker_row_button")
                                    ) {
                                        Text(
                                            text = "Đồng ý",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }

                                    Button(
                                        onClick = { showResetConfirmDialog = false },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                                        modifier = Modifier
                                            .weight(1.75f)
                                            .height(50.dp)
                                            .focusRequester(cancelFocusRequester)
                                            .testTag("cancel_reset_worker_row_button")
                                    ) {
                                        Text(
                                            text = "Hủy",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        )
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
                    .clickable(enabled = enabled) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        expanded = true
                    }
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
internal fun SearchableSelectDropdownField(
    label: String,
    selectedLabel: String,
    placeholder: String,
    searchPlaceholder: String = "Nhập từ khóa để tìm nhanh…",
    options: List<DropdownOptionItem>,
    enabled: Boolean = true,
    onSelect: (DropdownOptionItem) -> Unit,
    testTag: String
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(enabled = enabled) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    expanded = !expanded
                }
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
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            var searchQuery by remember { mutableStateOf("") }
            val searchFocusRequester = remember { FocusRequester() }
            val filteredOptions = remember(options, searchQuery) {
                filterDropdownOptions(options, searchQuery)
            }
            val visibleOptions = remember(filteredOptions) {
                filteredOptions.take(60)
            }

            LaunchedEffect(Unit) {
                runCatching { searchFocusRequester.requestFocus() }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tìm nhanh: ${label.removeSuffix("*").trim()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                expanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Đóng")
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = sanitizeSingleLineInput(it, 120) },
                        placeholder = { Text(searchPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa từ khóa")
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .testTag("${testTag}_search_input")
                    )

                    if (filteredOptions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Không tìm thấy kết quả phù hợp.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .testTag("${testTag}_options_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            visibleOptions.forEach { option ->
                                val isSelected = selectedLabel.isNotBlank() && option.label == selectedLabel
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        Color.Transparent
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onSelect(option)
                                            expanded = false
                                        }
                                        .testTag("${testTag}_option_${option.id}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (option.subtitle.isNotBlank()) {
                                            Text(
                                                text = option.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (filteredOptions.size > visibleOptions.size) {
                                Text(
                                    text = "Hiển thị ${visibleOptions.size}/${filteredOptions.size} kết quả — nhập thêm ký tự để lọc chính xác hơn.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentUploadSlotCard(
    title: String,
    subtitle: String,
    staged: StagedDocumentRef?,
    uploading: Boolean,
    onPickClick: () -> Unit,
    onCaptureCameraClick: (() -> Unit)? = null,
    onPickMultipleImagesClick: (() -> Unit)? = null,
    slotTestTagPrefix: String = "",
    onClearClick: () -> Unit
) {
    val hasAttachment = staged != null
    val hasMergedImagePages = staged?.sourceImagePaths?.isNotEmpty() == true

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasAttachment) SemanticColors.SuccessLight.copy(alpha = 0.5f)
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
                        Text(
                            text = if (onPickMultipleImagesClick != null) {
                                "Đang nén ảnh và ghép thành tệp PDF…"
                            } else {
                                "Đang chuẩn bị và tối ưu tệp đính kèm…"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                hasAttachment -> {
                    val sizeKb = (staged!!.sizeBytes / 1024L).coerceAtLeast(1L)
                    val sizeLabel = if (sizeKb >= 1024L) {
                        String.format(Locale.US, "%.1f MB", staged.sizeBytes / (1024.0 * 1024.0))
                    } else {
                        "$sizeKb KB"
                    }
                    val detailLabel = if (staged.mimeType == "application/pdf" && hasMergedImagePages) {
                        "${staged.pageCount} trang PDF · $sizeLabel"
                    } else {
                        sizeLabel
                    }
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
                            text = "Đã đính kèm: ${staged.fileName} ($detailLabel)",
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
                        text = "Chưa chọn tệp đính kèm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onPickMultipleImagesClick != null) {
                    if (onCaptureCameraClick != null) {
                        OutlinedButton(
                            onClick = onCaptureCameraClick,
                            enabled = !uploading,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .let { mod ->
                                    if (slotTestTagPrefix.isNotBlank()) mod.testTag("${slotTestTagPrefix}_camera") else mod
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasMergedImagePages) "Chụp thêm trang" else "Chụp ảnh",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onPickMultipleImagesClick,
                        enabled = !uploading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .let { mod ->
                                if (slotTestTagPrefix.isNotBlank()) mod.testTag("${slotTestTagPrefix}_multi_album") else mod
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasMergedImagePages) "Thêm ảnh (Album)" else "Chọn nhiều ảnh",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    OutlinedButton(
                        onClick = onPickClick,
                        enabled = !uploading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .let { mod ->
                                if (slotTestTagPrefix.isNotBlank()) mod.testTag("${slotTestTagPrefix}_file") else mod
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasAttachment && !hasMergedImagePages) "Đổi PDF" else "Chọn PDF",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else if (onCaptureCameraClick != null) {
                    OutlinedButton(
                        onClick = onCaptureCameraClick,
                        enabled = !uploading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .let { mod ->
                                if (slotTestTagPrefix.isNotBlank()) mod.testTag("${slotTestTagPrefix}_camera") else mod
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chụp ảnh", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onPickClick,
                        enabled = !uploading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .let { mod ->
                                if (slotTestTagPrefix.isNotBlank()) mod.testTag("${slotTestTagPrefix}_album") else mod
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasAttachment) "Đổi ảnh (Album)" else "Chọn từ Album",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onPickClick,
                        enabled = !uploading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .let { mod ->
                                if (slotTestTagPrefix.isNotBlank()) mod.testTag("${slotTestTagPrefix}_file") else mod
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (hasAttachment) "Đổi tệp" else "Chọn tệp", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (hasAttachment && !uploading) {
                    TextButton(
                        onClick = onClearClick,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Gỡ tệp", color = MaterialTheme.colorScheme.error)
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
        add(WorkerScopeTabSpec("managed", "DA tôi quản lý"))
        add(WorkerScopeTabSpec("recruited", "Tôi tuyển"))
        if (actor.hasScopeKind("team")) {
            add(WorkerScopeTabSpec("team", "Team tôi quản lý"))
        }
        if (actor.hasCapability("submission_read") || actor.hasCapability("submission_create")) {
            add(WorkerScopeTabSpec("uploader", "Tôi đã nhập"))
        }
        if (
            actor.hasScopeKind("all") &&
            (actor.hasCapability("entry_admin") || actor.hasCapability("change_review"))
        ) {
            add(WorkerScopeTabSpec("all", "Toàn hệ thống"))
        }
    }
}

private fun shareOrViewCachedFile(context: android.content.Context, file: File, mimeType: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }
}

@Composable
fun WorkersAndDirectEntryScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onOpenNewEntryScreen: () -> Unit = {},
    onBackToDashboard: () -> Unit,
    onSelectScopeTab: ((scopeKey: String) -> Unit)? = null,
    onSearchAndFilterChange: ((search: String, projectId: String?, status: String?) -> Unit)? = null,
    onExportWorkersExcel: ((scopeKey: String, projectId: String?, status: String?) -> Unit)? = null,
    onOpenEntryDetail: ((entryId: String) -> Unit)? = null,
    onCloseEntryDetail: (() -> Unit)? = null,
    onOpenProposeChange: ((WorkerProfileEntity) -> Unit)? = null,
    onOpenDirectEdit: ((WorkerProfileEntity) -> Unit)? = null,
    onOpenSalaryAdvance: (() -> Unit)? = null
) {
    BackHandler(onBack = onBackToDashboard)

    val context = LocalContext.current
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val workers by viewModel.workerRows.collectAsStateWithLifecycle()
    val workerLoading by viewModel.workerLoading.collectAsStateWithLifecycle()
    val workerError by viewModel.workerError.collectAsStateWithLifecycle()
    val ownSubmissions by viewModel.ownSubmissions.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedEntryDetail.collectAsStateWithLifecycle()
    val activeTabKeyFromVm by viewModel.activeWorkerTabKey.collectAsStateWithLifecycle()
    val workerHasMore by viewModel.workerHasMore.collectAsStateWithLifecycle()
    val submissionsHasMore by viewModel.submissionsHasMore.collectAsStateWithLifecycle()
    val workerSummary by viewModel.workerSummary.collectAsStateWithLifecycle()
    val proposeTarget by viewModel.proposeChangeTarget.collectAsStateWithLifecycle()
    val directEditTarget by viewModel.directEditTarget.collectAsStateWithLifecycle()
    val mutationBusy by viewModel.workerMutationBusy.collectAsStateWithLifecycle()
    val mutationError by viewModel.workerMutationError.collectAsStateWithLifecycle()
    val pendingDuplicateSubmission by viewModel.pendingDuplicateCccdSubmission.collectAsStateWithLifecycle()

    val salaryAdvanceContext by viewModel.salaryAdvanceContext.collectAsStateWithLifecycle()
    val salaryAdvanceDialogOpen by viewModel.salaryAdvanceDialogOpen.collectAsStateWithLifecycle()
    val salaryAdvanceSelectedProjectId by viewModel.salaryAdvanceSelectedProjectId.collectAsStateWithLifecycle()
    val salaryAdvanceWorkerSearch by viewModel.salaryAdvanceWorkerSearch.collectAsStateWithLifecycle()
    val salaryAdvanceWorkers by viewModel.salaryAdvanceWorkers.collectAsStateWithLifecycle()
    val salaryAdvanceWorkersLoading by viewModel.salaryAdvanceWorkersLoading.collectAsStateWithLifecycle()
    val salaryAdvanceWorkersLoadingMore by viewModel.salaryAdvanceWorkersLoadingMore.collectAsStateWithLifecycle()
    val salaryAdvanceWorkersError by viewModel.salaryAdvanceWorkersError.collectAsStateWithLifecycle()
    val salaryAdvanceWorkersHasMore by viewModel.salaryAdvanceWorkersHasMore.collectAsStateWithLifecycle()
    val salaryAdvanceSelectedWorkerId by viewModel.salaryAdvanceSelectedWorkerId.collectAsStateWithLifecycle()
    val salaryAdvancePaymentMethod by viewModel.salaryAdvancePaymentMethod.collectAsStateWithLifecycle()
    val salaryAdvanceAmountRaw by viewModel.salaryAdvanceAmountRaw.collectAsStateWithLifecycle()
    val salaryAdvanceInitialContent by viewModel.salaryAdvanceInitialContent.collectAsStateWithLifecycle()
    val salaryAdvanceCreateSubmitting by viewModel.salaryAdvanceCreateSubmitting.collectAsStateWithLifecycle()
    val salaryAdvanceCreateError by viewModel.salaryAdvanceCreateError.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryOpen by viewModel.salaryAdvanceHistoryOpen.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryPeriodMode by viewModel.salaryAdvanceHistoryPeriodMode.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryMonth by viewModel.salaryAdvanceHistoryMonth.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryFromDate by viewModel.salaryAdvanceHistoryFromDate.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryToDate by viewModel.salaryAdvanceHistoryToDate.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryLoading by viewModel.salaryAdvanceHistoryLoading.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryLoadingMore by viewModel.salaryAdvanceHistoryLoadingMore.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryError by viewModel.salaryAdvanceHistoryError.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryResponse by viewModel.salaryAdvanceHistoryResponse.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryItems by viewModel.salaryAdvanceHistoryItems.collectAsStateWithLifecycle()
    val salaryAdvanceHistoryHasMore by viewModel.salaryAdvanceHistoryHasMore.collectAsStateWithLifecycle()

    val visibleTabs = remember(actor) { resolveVisibleWorkerTabs(actor) }
    val safeTabIdx = visibleTabs.indexOfFirst { it.key == activeTabKeyFromVm }.takeIf { it >= 0 } ?: 0
    val activeScopeKey = visibleTabs.getOrNull(safeTabIdx)?.key ?: "managed"
    val canShowSalaryAdvance = remember(salaryAdvanceContext) {
        SalaryAdvanceContract.canShowPmSalaryAdvanceEntry(salaryAdvanceContext)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedRecruiterId by remember { mutableStateOf<String?>(null) }
    var selectedStatusCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actor.appUserId) {
        viewModel.loadCatalogIfNeeded()
        viewModel.loadSalaryAdvanceContextIfNeeded(actor = actor)
        viewModel.ensureInitialWorkerTabLoaded(actor)
    }

    val effectiveSelectTab: (String) -> Unit = onSelectScopeTab ?: { scopeKey ->
        viewModel.selectWorkerTab(
            tabKey = scopeKey,
            search = searchQuery,
            projectId = selectedProjectId,
            recruiterId = selectedRecruiterId,
            employmentStatus = selectedStatusCode
        )
    }
    val effectiveFilterChange: (String, String?, String?, String?) -> Unit = { q, proj, rec, status ->
        if (onSearchAndFilterChange != null) {
            onSearchAndFilterChange(q, proj, status)
        } else {
            viewModel.selectWorkerTab(
                tabKey = activeScopeKey,
                search = q,
                projectId = proj,
                recruiterId = rec,
                employmentStatus = status
            )
        }
    }
    val effectiveOpenDetail: (String) -> Unit = onOpenEntryDetail ?: { viewModel.openEntryDetail(it) }
    val effectiveCloseDetail: () -> Unit = onCloseEntryDetail ?: { viewModel.closeEntryDetail() }
    val effectiveOpenPropose: (WorkerProfileEntity) -> Unit = onOpenProposeChange ?: { viewModel.openProposeChange(it) }
    val effectiveOpenDirectEdit: (WorkerProfileEntity) -> Unit = onOpenDirectEdit ?: { viewModel.openDirectEdit(it) }
    val effectiveOpenSalaryAdvance: () -> Unit = onOpenSalaryAdvance ?: { viewModel.openSalaryAdvanceDialog() }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSubmissionTab = activeScopeKey == "uploader"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .testTag("workers_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (visibleTabs.isNotEmpty()) {
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
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("worker_scope_grid"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleTabs.chunked(2).forEach { rowTabs ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowTabs.forEach { tab ->
                                        val selected = tab.key == activeScopeKey
                                        Surface(
                                            selected = selected,
                                            onClick = {
                                                focusManager.clearFocus()
                                                effectiveSelectTab(tab.key)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .heightIn(min = 42.dp)
                                                .testTag("worker_scope_tab_${tab.key}"),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            },
                                            border = if (selected) {
                                                null
                                            } else {
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                )
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = tab.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (canShowSalaryAdvance) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    effectiveOpenSalaryAdvance()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("salary_advance_open_button")
                            ) {
                                Text(
                                    text = "Ứng lương",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isSubmissionTab) {
                            Text(
                                text = if (workerSummary != null) {
                                    "Tổng: ${workerSummary!!.totalWorkers} NLĐ · ${workerSummary!!.projectCount} dự án · ${workerSummary!!.recruiterCount} người tuyển"
                                } else {
                                    "Chọn phạm vi để xem danh sách NLĐ từ máy chủ"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (!isSubmissionTab && visibleTabs.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            effectiveFilterChange(q, selectedProjectId, selectedRecruiterId, selectedStatusCode)
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
                                effectiveFilterChange(searchQuery, selectedProjectId, selectedRecruiterId, null)
                            },
                            label = { Text("Mọi trạng thái") }
                        )
                        WORK_STATUS_FILTER_OPTIONS.forEach { (code, label) ->
                            FilterChip(
                                selected = selectedStatusCode == code,
                                onClick = {
                                    val next = if (selectedStatusCode == code) null else code
                                    selectedStatusCode = next
                                    effectiveFilterChange(searchQuery, selectedProjectId, selectedRecruiterId, next)
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
                                    effectiveFilterChange(searchQuery, next, selectedRecruiterId, selectedStatusCode)
                                },
                                label = { Text(proj.displayName) }
                            )
                        }
                        catalog?.recruiters?.take(12)?.forEach { rec ->
                            FilterChip(
                                selected = selectedRecruiterId == rec.recruiterId,
                                onClick = {
                                    val next = if (selectedRecruiterId == rec.recruiterId) null else rec.recruiterId
                                    selectedRecruiterId = next
                                    effectiveFilterChange(searchQuery, selectedProjectId, next, selectedStatusCode)
                                },
                                label = { Text("NT: ${rec.displayName}") }
                            )
                        }
                        if (selectedStatusCode != null || selectedProjectId != null || selectedRecruiterId != null || searchQuery.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    selectedStatusCode = null
                                    selectedProjectId = null
                                    selectedRecruiterId = null
                                    searchQuery = ""
                                    effectiveFilterChange("", null, null, null)
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
                    OwnSubmissionRowCard(
                        item = sub,
                        busy = mutationBusy,
                        onSubmitDraft = { viewModel.submitExistingDraftSubmission(sub) }
                    )
                }
                if (submissionsHasMore) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.loadMoreWorkersOrSubmissions() },
                            enabled = !workerLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("load_more_submissions_button")
                        ) {
                            Text("Tải thêm đợt nhập liệu")
                        }
                    }
                }
            }
        } else {
            if (workers.isEmpty() && !workerLoading) {
                item {
                    val (emptyTitle, emptySubtitle) = when (activeScopeKey) {
                        "managed" -> "Chưa có NLĐ ở dự án bạn quản lý" to
                            "Danh sách NLĐ thuộc các dự án bạn phụ trách trên máy chủ sẽ hiển thị tại đây."
                        "recruited" -> "Chưa có NLĐ mà bạn tuyển" to
                            "Danh sách NLĐ do bạn trực tiếp tuyển dụng trên máy chủ sẽ hiển thị tại đây."
                        else -> "Không có hồ sơ người lao động" to
                            "Chưa có hồ sơ phù hợp trong phạm vi đang chọn từ máy chủ."
                    }
                    EmptyWorkersCard(
                        title = emptyTitle,
                        subtitle = emptySubtitle
                    )
                }
            } else {
                items(workers, key = { it.cacheKey }) { worker ->
                    WorkerOperationsRowCard(
                        worker = worker,
                        onViewDetail = { effectiveOpenDetail(worker.entryId) },
                        onProposeChange = { effectiveOpenPropose(worker) },
                        onDirectEdit = { effectiveOpenDirectEdit(worker) }
                    )
                }
                if (workerHasMore) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.loadMoreWorkersOrSubmissions() },
                            enabled = !workerLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("load_more_workers_button")
                        ) {
                            Text("Tải thêm hồ sơ")
                        }
                    }
                }
            }
        }
    }

    selectedDetail?.let { detail ->
        WorkerEntryDetailDialog(
            detail = detail,
            serverBaseUrl = serverUrl,
            onDismiss = effectiveCloseDetail,
            onDownloadDocument = { downloadPath ->
                viewModel.downloadEntryDocument(
                    downloadPath = downloadPath,
                    fallbackFileName = "${detail.employeeCode}_attachment"
                ) { file ->
                    shareOrViewCachedFile(context, file, "*/*")
                }
            }
        )
    }

    proposeTarget?.let { (worker, detailBaseline) ->
        WorkerProposeChangeDialog(
            worker = worker,
            baselineDetail = detailBaseline,
            catalog = catalog,
            busy = mutationBusy,
            error = mutationError,
            onDismiss = { viewModel.closeProposeChange() },
            onSubmit = { targetKind, expectedVer, proposal, reason ->
                viewModel.submitProposeChange(
                    entryId = worker.entryId,
                    targetKind = targetKind,
                    expectedVersion = expectedVer,
                    proposal = proposal,
                    reason = reason
                )
            }
        )
    }

    directEditTarget?.let { (worker, detailBaseline) ->
        WorkerPrivilegedEditDialog(
            worker = worker,
            baselineDetail = detailBaseline,
            catalog = catalog,
            busy = mutationBusy,
            error = mutationError,
            onDismiss = { viewModel.closeDirectEdit() },
            onSubmit = { expectedEntryVersion, patch, reason ->
                viewModel.submitDirectEdit(
                    entryId = worker.entryId,
                    expectedEntryVersion = expectedEntryVersion,
                    patch = patch,
                    reason = reason
                )
            }
        )
    }

    pendingDuplicateSubmission?.let { pending ->
        DuplicateCccdSubmitConfirmDialog(
            pending = pending,
            busy = mutationBusy,
            error = mutationError,
            onDismissKeepDraft = { viewModel.dismissDuplicateCccdConfirmation() },
            onConfirmSubmit = { viewModel.confirmDuplicateCccdSubmission() }
        )
    }

    if (salaryAdvanceDialogOpen && canShowSalaryAdvance && salaryAdvanceContext != null) {
        if (salaryAdvanceHistoryOpen) {
            val selectedWorkerForHistory = salaryAdvanceWorkers.firstOrNull {
                it.workerEntryId == salaryAdvanceSelectedWorkerId
            }
            SalaryAdvanceWorkerHistoryDialog(
                selectedWorker = selectedWorkerForHistory,
                periodMode = salaryAdvanceHistoryPeriodMode,
                monthInput = salaryAdvanceHistoryMonth,
                fromDateInput = salaryAdvanceHistoryFromDate,
                toDateInput = salaryAdvanceHistoryToDate,
                loading = salaryAdvanceHistoryLoading,
                loadingMore = salaryAdvanceHistoryLoadingMore,
                error = salaryAdvanceHistoryError,
                historyResponse = salaryAdvanceHistoryResponse,
                historyItems = salaryAdvanceHistoryItems,
                hasMore = salaryAdvanceHistoryHasMore,
                onSelectPeriodMode = { viewModel.selectSalaryAdvanceHistoryPeriodMode(it) },
                onUpdateMonth = { viewModel.updateSalaryAdvanceHistoryMonth(it) },
                onUpdateDateRange = { from, to -> viewModel.updateSalaryAdvanceHistoryDateRange(from, to) },
                onApplyCustomRange = { viewModel.applySalaryAdvanceHistoryCustomRange() },
                onLoadMore = { viewModel.loadMoreSalaryAdvanceWorkerHistory() },
                onDismiss = { viewModel.closeSalaryAdvanceWorkerHistory() }
            )
        } else {
            SalaryAdvanceCreateRequestDialog(
                context = salaryAdvanceContext!!,
                selectedProjectId = salaryAdvanceSelectedProjectId,
                workerSearch = salaryAdvanceWorkerSearch,
                workers = salaryAdvanceWorkers,
                workersLoading = salaryAdvanceWorkersLoading,
                workersLoadingMore = salaryAdvanceWorkersLoadingMore,
                workersError = salaryAdvanceWorkersError,
                workersHasMore = salaryAdvanceWorkersHasMore,
                selectedWorkerId = salaryAdvanceSelectedWorkerId,
                paymentMethod = salaryAdvancePaymentMethod,
                amountRaw = salaryAdvanceAmountRaw,
                initialContent = salaryAdvanceInitialContent,
                submitting = salaryAdvanceCreateSubmitting,
                createError = salaryAdvanceCreateError,
                onSelectProject = { viewModel.selectSalaryAdvanceProject(it) },
                onWorkerSearchChange = { viewModel.updateSalaryAdvanceWorkerSearch(it) },
                onLoadMoreWorkers = { viewModel.loadMoreSalaryAdvanceWorkers() },
                onSelectWorker = { viewModel.selectSalaryAdvanceWorker(it) },
                onSelectPaymentMethod = { viewModel.updateSalaryAdvancePaymentMethod(it) },
                onAmountChange = { viewModel.updateSalaryAdvanceAmountRaw(it) },
                onInitialContentChange = { viewModel.updateSalaryAdvanceInitialContent(it) },
                onOpenWorkerHistory = { viewModel.openSalaryAdvanceWorkerHistory() },
                onSubmit = { viewModel.submitSalaryAdvanceRequest() },
                onDismiss = { viewModel.closeSalaryAdvanceDialog() }
            )
        }
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
private fun OwnSubmissionRowCard(
    item: OwnSubmissionItem,
    busy: Boolean = false,
    onSubmitDraft: () -> Unit = {}
) {
    val canSubmitDraft = item.state == "DRAFT" || "SUBMITTED" in item.allowedTransitions
    val badgeColor = when (item.state) {
        "SUBMITTED" -> SemanticColors.SuccessLight
        "DRAFT" -> SemanticColors.WarningLight
        else -> MaterialTheme.colorScheme.primary
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = submissionStateUiLabel(item.state),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            val formattedEntryTime = DirectEntryContract.formatIsoTimestampHcmUi(
                isoOrNull = item.submittedAt,
                fallbackIso = item.createdAt.ifBlank { item.updatedAt }
            )
            Text(
                text = "Số lượng NLĐ: ${item.entryCount} · Lần cập nhật #${item.version}",
                style = MaterialTheme.typography.bodySmall
            )
            if (formattedEntryTime.isNotBlank()) {
                Text(
                    text = "Thời gian nhập: $formattedEntryTime",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Cập nhật: ${DirectEntryContract.formatIsoTimestampHcmUi(item.updatedAt).ifBlank { item.updatedAt.take(19).replace('T', ' ') }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canSubmitDraft) {
                Text(
                    text = "Đợt này đang ở Bản nháp nên chưa hiển thị trong danh sách Nhân sự. Bấm Gửi chính thức để đẩy lên hệ thống.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SemanticColors.WarningLight
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onSubmitDraft,
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("submit_draft_submission_${item.submissionId}")
                    ) {
                        Text(if (busy) "Đang gửi…" else "Gửi chính thức")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkerOperationsRowCard(
    worker: WorkerProfileEntity,
    onViewDetail: () -> Unit,
    onProposeChange: () -> Unit = {},
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
                        text = worker.displayName.ifBlank { "Chưa cập nhật họ tên" },
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
                Text(
                    text = "Dự án: ${worker.projectDisplay.trim()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                if (worker.recruiterDisplay.isNotBlank()) {
                    Text(
                        text = "Người tuyển: ${worker.recruiterDisplay.trim()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (worker.firstWorkDate.isNotBlank() || worker.laborTypeDisplay.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (worker.firstWorkDate.isNotBlank()) {
                                "Ngày đầu: ${worker.firstWorkDate.trim()}"
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            modifier = Modifier.weight(1f)
                        )
                        if (worker.laborTypeDisplay.isNotBlank()) {
                            Text(
                                text = worker.laborTypeDisplay.trim(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (worker.paymentSummary.isNotBlank()) {
                    Text(
                        text = "Thanh toán: ${worker.paymentSummary.trim()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (worker.pendingRequestSummary.isNotBlank() || worker.lastDecisionSummary.isNotBlank()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (worker.pendingRequestSummary.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SemanticColors.WarningLight.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = worker.pendingRequestSummary.trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SemanticColors.WarningLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (worker.lastDecisionSummary.isNotBlank()) {
                        Text(
                            text = worker.lastDecisionSummary.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (actions.view || actions.directEdit) {
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
    serverBaseUrl: String = "",
    onDismiss: () -> Unit,
    onDownloadDocument: ((downloadPath: String) -> Unit)? = null
) {
    val cleanBase = serverBaseUrl.trim().trimEnd('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = detail.displayName.ifBlank { "Chưa cập nhật họ tên" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mã NLĐ: ${detail.employeeCode}",
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
                if (!detail.portraitPreviewPath.isNullOrBlank() && cleanBase.isNotEmpty()) {
                    val portraitUrl = "$cleanBase${detail.portraitPreviewPath}"
                    var portraitLoadFailed by remember(portraitUrl) { mutableStateOf(false) }
                    if (!portraitLoadFailed) {
                        AsyncImage(
                            model = portraitUrl,
                            contentDescription = "Ảnh chân dung ${detail.displayName}",
                            contentScale = ContentScale.Crop,
                            onError = { portraitLoadFailed = true },
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        )
                    }
                }
                DetailItemRow("Ngày đầu đi làm", detail.firstWorkDate.trim().ifEmpty { "—" })
                DetailItemRow("Loại hình LĐ", detail.laborTypeDisplay.trim().ifEmpty { "—" })
                DetailItemRow("Trạng thái", "${DirectEntryContract.workStatusUiLabel(detail.employmentStatus)} (${detail.employmentEffectiveDate.trim()})")
                if (!detail.employmentLeaveReason.isNullOrBlank()) {
                    DetailItemRow("Lý do nghỉ", detail.employmentLeaveReason.trim())
                }
                HorizontalDivider()
                DetailItemRow("Giới tính", DirectEntryContract.genderUiLabel(detail.gender))
                DetailItemRow("Ngày sinh", detail.dateOfBirth?.trim()?.ifEmpty { null } ?: "—")
                DetailItemRow("Số điện thoại", detail.phone?.trim()?.ifEmpty { null } ?: "—")
                DetailItemRow("Số CCCD", detail.nationalId?.trim()?.ifEmpty { null } ?: "—")
                DetailItemRow("Ngày cấp CCCD", detail.nationalIdIssuedAt?.trim()?.ifEmpty { null } ?: "—")
                if (!detail.nationalIdIssuedPlace.isNullOrBlank()) {
                    DetailItemRow("Nơi cấp CCCD", detail.nationalIdIssuedPlace.trim())
                }
                DetailItemRow("Địa chỉ", detail.address?.trim()?.ifEmpty { null } ?: "—")
                HorizontalDivider()
                DetailItemRow("Ngân hàng", detail.bankName?.trim()?.ifEmpty { null } ?: "—")
                DetailItemRow("Số tài khoản", detail.accountNumber?.trim()?.ifEmpty { null } ?: "—")
                DetailItemRow("Chủ tài khoản", detail.accountHolderName?.trim()?.ifEmpty { null } ?: "—")
                if (!detail.generalNote.isNullOrBlank()) {
                    HorizontalDivider()
                    DetailItemRow("Ghi chú", detail.generalNote.trim())
                }
                if (detail.documents.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Tài liệu đính kèm trên máy chủ:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    detail.documents.forEach { doc ->
                        val docTypeLabel = when (doc.documentType) {
                            "WORKER_PORTRAIT" -> "Ảnh chân dung"
                            "CCCD_FRONT" -> "CCCD mặt trước"
                            "CCCD_BACK" -> "CCCD mặt sau"
                            "EMPLOYMENT_CONTRACT" -> "Hợp đồng lao động"
                            else -> doc.documentType
                        }
                        val docStatusLabel = when (doc.uploadStatus) {
                            "READY" -> "Sẵn sàng"
                            "QUEUED" -> "Đang tải lên"
                            "STAGED" -> "Đang xử lý"
                            "FAILED" -> "Lỗi tải lên"
                            else -> "Đang đồng bộ"
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$docTypeLabel ($docStatusLabel)",
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
                            if (!doc.previewPath.isNullOrBlank() && cleanBase.isNotEmpty()) {
                                val docPreviewUrl = "$cleanBase${doc.previewPath}"
                                var docPreviewFailed by remember(docPreviewUrl) { mutableStateOf(false) }
                                if (!docPreviewFailed) {
                                    AsyncImage(
                                        model = docPreviewUrl,
                                        contentDescription = docTypeLabel,
                                        contentScale = ContentScale.Fit,
                                        onError = { docPreviewFailed = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    )
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
internal fun WorkerProposeChangeDialog(
    worker: WorkerProfileEntity,
    baselineDetail: WorkerEntryDetail?,
    catalog: DirectEntryCatalog?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (targetKind: String, expectedVersion: Int, proposal: JSONObject, reason: String?) -> Unit,
    renderInDialog: Boolean = true
) {
    val expectedVersion = baselineDetail?.version ?: worker.entryVersion
    var selectedTargetKind by remember { mutableStateOf("ENTRY_FIELD") }

    // ENTRY_FIELD fields
    var projectId by remember(baselineDetail, worker) {
        mutableStateOf(baselineDetail?.projectId ?: worker.projectId)
    }
    var firstWorkDateUi by remember(baselineDetail, worker) {
        mutableStateOf(DirectEntryContract.isoDateToUiDate(baselineDetail?.firstWorkDate ?: worker.firstWorkDate))
    }
    var laborTypeKey by remember(baselineDetail, worker) {
        mutableStateOf(baselineDetail?.laborType ?: worker.laborType)
    }
    var displayName by remember(baselineDetail, worker) {
        mutableStateOf(baselineDetail?.displayName ?: worker.displayName)
    }
    var phone by remember(baselineDetail) {
        mutableStateOf(baselineDetail?.phone.orEmpty())
    }

    // PAYMENT fields
    var bankId by remember(baselineDetail, catalog) {
        mutableStateOf(baselineDetail?.bankId ?: catalog?.banks?.firstOrNull()?.bankId.orEmpty())
    }
    var accountNumber by remember(baselineDetail) {
        mutableStateOf(baselineDetail?.accountNumber.orEmpty())
    }
    var accountHolderName by remember(baselineDetail, worker) {
        mutableStateOf(baselineDetail?.accountHolderName ?: worker.displayName)
    }

    // WORK_STATUS fields
    val baselineStatus = baselineDetail?.employmentStatus ?: worker.employmentStatus
    val allowedStatusCodes = remember(baselineStatus) {
        DirectEntryContract.allowedWorkStatusTargets(baselineStatus)
    }
    var targetStatus by remember(baselineStatus) {
        mutableStateOf(allowedStatusCodes.firstOrNull().orEmpty())
    }
    var effectiveDateUi by remember { mutableStateOf(DirectEntryContract.todayUiInHcm()) }
    var leaveReason by remember { mutableStateOf("") }

    var localError by remember { mutableStateOf<String?>(null) }

    val targetOptions = listOf(
        "ENTRY_FIELD" to "Thông tin hồ sơ",
        "PAYMENT" to "Tài khoản NH",
        "WORK_STATUS" to "Trạng thái LĐ"
    )

    val dialogBody: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text("Đề xuất thay đổi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${worker.displayName} (${worker.employeeCode}) · v$expectedVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    targetOptions.forEach { (kind, label) ->
                        FilterChip(
                            selected = selectedTargetKind == kind,
                            onClick = {
                                selectedTargetKind = kind
                                localError = null
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("propose_target_$kind")
                        )
                    }
                }

                when (selectedTargetKind) {
                    "ENTRY_FIELD" -> {
                        if (catalog != null && catalog.projects.isNotEmpty()) {
                            KeyedSelectDropdownField(
                                label = "Dự án",
                                selectedLabel = catalog.projects.firstOrNull { it.projectId == projectId }?.displayName ?: projectId,
                                placeholder = "Chọn dự án",
                                options = catalog.projects.map { DropdownOptionItem(it.projectId, it.displayName) },
                                onSelect = { projectId = it.id },
                                testTag = "propose_project_dropdown"
                            )
                        }
                        if (catalog != null && catalog.laborTypes.isNotEmpty()) {
                            KeyedSelectDropdownField(
                                label = "Loại hình lao động",
                                selectedLabel = catalog.laborTypes.firstOrNull { it.laborTypeKey == laborTypeKey }?.displayName ?: laborTypeKey,
                                placeholder = "Chọn loại hình LĐ",
                                options = catalog.laborTypes.map { DropdownOptionItem(it.laborTypeKey, it.displayName) },
                                onSelect = { laborTypeKey = it.id },
                                testTag = "propose_labor_type_dropdown"
                            )
                        }
                        OutlinedTextField(
                            value = firstWorkDateUi,
                            onValueChange = { firstWorkDateUi = sanitizeSingleLineInput(it, 10) },
                            label = { Text("Ngày bắt đầu (DD/MM/YYYY)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = sanitizeSingleLineInput(it, 256) },
                            label = { Text("Họ và tên") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (baselineDetail != null) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = sanitizePhoneInput(it) },
                                label = { Text("Số điện thoại") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    "PAYMENT" -> {
                        if (catalog != null && catalog.banks.isNotEmpty()) {
                            KeyedSelectDropdownField(
                                label = "Ngân hàng *",
                                selectedLabel = catalog.banks.firstOrNull { it.bankId == bankId }?.displayName.orEmpty(),
                                placeholder = "Chọn ngân hàng",
                                options = catalog.banks.map { DropdownOptionItem(it.bankId, it.displayName) },
                                onSelect = { bankId = it.id },
                                testTag = "propose_bank_dropdown"
                            )
                        } else {
                            OutlinedTextField(
                                value = bankId,
                                onValueChange = { bankId = sanitizeSingleLineInput(it, 128) },
                                label = { Text("Mã ngân hàng *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it.filter { ch -> ch.isDigit() }.take(64) },
                            label = { Text("Số tài khoản (chỉ chữ số) *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = accountHolderName,
                            onValueChange = { accountHolderName = sanitizeSingleLineInput(it, 256) },
                            label = { Text("Tên chủ tài khoản *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    "WORK_STATUS" -> {
                        Text(
                            text = "Hiện tại: ${DirectEntryContract.workStatusUiLabel(baselineStatus)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (allowedStatusCodes.isEmpty()) {
                            Text(
                                text = "Hồ sơ đã nghỉ việc, không thể chuyển trạng thái sang Đang làm hoặc Đang chờ việc.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WORK_STATUS_FILTER_OPTIONS
                                    .filter { (code, _) -> code in allowedStatusCodes }
                                    .forEach { (code, label) ->
                                        FilterChip(
                                            selected = targetStatus == code,
                                            onClick = { targetStatus = code },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("propose_status_chip_$code")
                                        )
                                    }
                            }
                            OutlinedTextField(
                                value = effectiveDateUi,
                                onValueChange = { effectiveDateUi = sanitizeSingleLineInput(it, 10) },
                                label = { Text("Ngày hiệu lực (DD/MM/YYYY) *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (targetStatus == "OFF") {
                                OutlinedTextField(
                                    value = leaveReason,
                                    onValueChange = { leaveReason = sanitizeMultiLineInput(it, 4000) },
                                    label = { Text("Lý do nghỉ *") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("propose_leave_reason_input")
                                )
                            }
                        }
                    }
                }

                (localError ?: error)?.let { errMsg ->
                    Text(
                        text = errMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text("Hủy")
                    }
                    Button(
                        enabled = !busy && !(selectedTargetKind == "WORK_STATUS" && allowedStatusCodes.isEmpty()),
                        onClick = {
                            localError = null
                            try {
                                val proposal = when (selectedTargetKind) {
                                    "ENTRY_FIELD" -> {
                                        JSONObject().apply {
                                            val baseProj = baselineDetail?.projectId ?: worker.projectId
                                            if (projectId.isNotBlank() && projectId != baseProj) {
                                                put("project_id", projectId.trim())
                                            }
                                            val isoFirst = DirectEntryContract.uiDateToIsoDate(firstWorkDateUi)
                                            val baseFirst = baselineDetail?.firstWorkDate ?: worker.firstWorkDate
                                            if (isoFirst != baseFirst) {
                                                put("first_work_date", isoFirst)
                                            }
                                            val baseLabor = baselineDetail?.laborType ?: worker.laborType
                                            if (laborTypeKey.isNotBlank() && laborTypeKey != baseLabor) {
                                                put("labor_type", laborTypeKey.trim())
                                            }
                                            if (baselineDetail != null &&
                                                (displayName.trim() != baselineDetail.displayName || phone.trim() != baselineDetail.phone.orEmpty())
                                            ) {
                                                val cleanName = displayName.trim()
                                                if (cleanName.isEmpty() || cleanName.length > 256) {
                                                    throw IllegalArgumentException("Họ và tên NLĐ không được để trống (tối đa 256 ký tự).")
                                                }
                                                val safeNationalId = baselineDetail.nationalId?.trim()
                                                    ?.takeIf { DirectEntryContract.isCanonicalNationalId(it) }
                                                    .orEmpty()
                                                put(
                                                    "worker_details",
                                                    JSONObject().apply {
                                                        put("display_name", cleanName)
                                                        put("date_of_birth", DirectEntryContract.optionalFreeDateField(baselineDetail.dateOfBirth.orEmpty()))
                                                        put("national_id", DirectEntryContract.optionalNationalIdField(safeNationalId))
                                                        put("address", DirectEntryContract.optionalTextField(baselineDetail.address.orEmpty(), maxLength = 1024))
                                                        put("phone", DirectEntryContract.optionalTextField(phone, maxLength = 64))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    "PAYMENT" -> DirectEntryContract.buildPaymentChangeProposal(
                                        state = "provided",
                                        accountNumber = accountNumber,
                                        bankId = bankId,
                                        accountHolderName = accountHolderName
                                    )
                                    "WORK_STATUS" -> DirectEntryContract.buildWorkStatusChangeProposal(
                                        status = targetStatus,
                                        effectiveDateUiOrIso = effectiveDateUi,
                                        leaveReason = leaveReason,
                                        baselineStatus = baselineStatus,
                                        baselineEffectiveDateIso = baselineDetail?.employmentEffectiveDate
                                    )
                                    else -> JSONObject()
                                }
                                if (proposal.length() == 0) {
                                    localError = "Chưa có trường nào thay đổi so với dữ liệu gốc."
                                    return@Button
                                }
                                onSubmit(selectedTargetKind, expectedVersion, proposal, null)
                            } catch (e: Exception) {
                                localError = e.message ?: "Dữ liệu đề xuất chưa hợp lệ."
                            }
                        }
                    ) {
                        Text(if (busy) "Đang gửi…" else "Gửi đề xuất")
                    }
                }
            }
        }
    }
    if (renderInDialog) {
        Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
            dialogBody()
        }
    } else {
        dialogBody()
    }
}

@Composable
internal fun WorkerPrivilegedEditDialog(
    worker: WorkerProfileEntity,
    baselineDetail: WorkerEntryDetail,
    catalog: DirectEntryCatalog?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (expectedEntryVersion: Int, patch: JSONObject, reason: String?) -> Unit,
    renderInDialog: Boolean = true
) {
    var projectId by remember(baselineDetail) { mutableStateOf(baselineDetail.projectId) }
    var firstWorkDateUi by remember(baselineDetail) {
        mutableStateOf(DirectEntryContract.isoDateToUiDate(baselineDetail.firstWorkDate))
    }
    var laborTypeKey by remember(baselineDetail) { mutableStateOf(baselineDetail.laborType) }
    val selectableStatusCodes = remember(baselineDetail.employmentStatus) {
        DirectEntryContract.selectableDirectEditStatuses(baselineDetail.employmentStatus)
    }
    var statusCode by remember(baselineDetail) { mutableStateOf(baselineDetail.employmentStatus) }
    var statusEffectiveDateUi by remember(baselineDetail) {
        mutableStateOf(DirectEntryContract.isoDateToUiDate(baselineDetail.employmentEffectiveDate))
    }
    var leaveReason by remember(baselineDetail) { mutableStateOf(baselineDetail.employmentLeaveReason.orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }

    val dialogBody: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text("Sửa trực tiếp hồ sơ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${baselineDetail.displayName} (${baselineDetail.employeeCode}) · v${baselineDetail.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                if (catalog != null && catalog.projects.isNotEmpty()) {
                    KeyedSelectDropdownField(
                        label = "Dự án",
                        selectedLabel = catalog.projects.firstOrNull { it.projectId == projectId }?.displayName ?: projectId,
                        placeholder = "Chọn dự án",
                        options = catalog.projects.map { DropdownOptionItem(it.projectId, it.displayName) },
                        onSelect = { projectId = it.id },
                        testTag = "edit_project_dropdown"
                    )
                }
                if (catalog != null && catalog.laborTypes.isNotEmpty()) {
                    KeyedSelectDropdownField(
                        label = "Loại hình lao động",
                        selectedLabel = catalog.laborTypes.firstOrNull { it.laborTypeKey == laborTypeKey }?.displayName ?: laborTypeKey,
                        placeholder = "Chọn loại hình LĐ",
                        options = catalog.laborTypes.map { DropdownOptionItem(it.laborTypeKey, it.displayName) },
                        onSelect = { laborTypeKey = it.id },
                        testTag = "edit_labor_type_dropdown"
                    )
                }
                OutlinedTextField(
                    value = firstWorkDateUi,
                    onValueChange = { firstWorkDateUi = sanitizeSingleLineInput(it, 10) },
                    label = { Text("Ngày bắt đầu (DD/MM/YYYY)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Trạng thái làm việc", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WORK_STATUS_FILTER_OPTIONS
                        .filter { (code, _) -> code in selectableStatusCodes }
                        .forEach { (code, label) ->
                            FilterChip(
                                selected = statusCode == code,
                                onClick = {
                                    if (code != statusCode) {
                                        statusCode = code
                                        val baselineEffUi = DirectEntryContract.isoDateToUiDate(
                                            baselineDetail.employmentEffectiveDate
                                        )
                                        if (code != baselineDetail.employmentStatus &&
                                            statusEffectiveDateUi == baselineEffUi &&
                                            DirectEntryContract.todayIsoInHcm() >= baselineDetail.employmentEffectiveDate
                                        ) {
                                            statusEffectiveDateUi = DirectEntryContract.todayUiInHcm()
                                        }
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("direct_edit_status_chip_$code")
                            )
                        }
                }
                if (baselineDetail.employmentStatus == "OFF") {
                    Text(
                        text = "Hồ sơ đã nghỉ việc, không thể chuyển trạng thái sang Đang làm hoặc Đang chờ việc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = statusEffectiveDateUi,
                    onValueChange = { statusEffectiveDateUi = sanitizeSingleLineInput(it, 10) },
                    label = { Text("Ngày hiệu lực trạng thái (DD/MM/YYYY)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (statusCode == "OFF") {
                    OutlinedTextField(
                        value = leaveReason,
                        onValueChange = { leaveReason = sanitizeMultiLineInput(it, 4000) },
                        label = { Text("Lý do nghỉ *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("direct_edit_leave_reason_input")
                    )
                }
                (localError ?: error)?.let { errMsg ->
                    Text(
                        text = errMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text("Hủy")
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            localError = null
                            try {
                                val patch = JSONObject()
                                if (projectId.isNotBlank() && projectId != baselineDetail.projectId) {
                                    patch.put("project_id", projectId.trim())
                                }
                                val isoFirst = DirectEntryContract.uiDateToIsoDate(firstWorkDateUi)
                                if (isoFirst != baselineDetail.firstWorkDate) {
                                    patch.put("first_work_date", isoFirst)
                                }
                                if (laborTypeKey.isNotBlank() && laborTypeKey != baselineDetail.laborType) {
                                    patch.put("labor_type", laborTypeKey.trim())
                                }
                                val isoEff = DirectEntryContract.uiDateToIsoDate(statusEffectiveDateUi)
                                if (statusCode != baselineDetail.employmentStatus ||
                                    isoEff != baselineDetail.employmentEffectiveDate ||
                                    (statusCode == "OFF" && leaveReason.trim() != baselineDetail.employmentLeaveReason.orEmpty())
                                ) {
                                    patch.put(
                                        "employment_status",
                                        DirectEntryContract.buildPrivilegedEditEmploymentStatus(
                                            status = statusCode,
                                            effectiveDateUiOrIso = isoEff,
                                            leaveReason = leaveReason,
                                            baselineStatus = baselineDetail.employmentStatus
                                        )
                                    )
                                }
                                if (patch.length() == 0) {
                                    localError = "Chưa có trường nào thay đổi so với dữ liệu gốc."
                                    return@Button
                                }
                                onSubmit(baselineDetail.version, patch, null)
                            } catch (e: Exception) {
                                localError = e.message ?: "Dữ liệu chỉnh sửa chưa hợp lệ."
                            }
                        }
                    ) {
                        Text(if (busy) "Đang lưu…" else "Lưu thay đổi")
                    }
                }
            }
        }
    }
    if (renderInDialog) {
        Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
            dialogBody()
        }
    } else {
        dialogBody()
    }
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

// ============================================================================
// 3. ỨNG LƯƠNG QLDA (`SalaryAdvanceCreateRequestDialog` + `SalaryAdvanceWorkerHistoryDialog`)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SalaryAdvanceCreateRequestDialog(
    context: SalaryAdvanceActorContext,
    selectedProjectId: String,
    workerSearch: String,
    workers: List<SalaryAdvanceWorkerOption>,
    workersLoading: Boolean,
    workersLoadingMore: Boolean,
    workersError: String?,
    workersHasMore: Boolean,
    selectedWorkerId: String,
    paymentMethod: String,
    amountRaw: String,
    initialContent: String,
    submitting: Boolean,
    createError: String?,
    onSelectProject: (String) -> Unit,
    onWorkerSearchChange: (String) -> Unit,
    onLoadMoreWorkers: () -> Unit,
    onSelectWorker: (String) -> Unit,
    onSelectPaymentMethod: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onInitialContentChange: (String) -> Unit,
    onOpenWorkerHistory: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    renderInDialog: Boolean = true
) {
    val activeProjects = remember(context) { context.activeManagedProjects }
    val selectedProject = remember(activeProjects, selectedProjectId) {
        activeProjects.firstOrNull { it.projectId == selectedProjectId }
    }
    val visibleWorkers = remember(workers) {
        SalaryAdvanceContract.filterSelectableProjectWorkers(workers)
    }
    val selectedWorker = remember(visibleWorkers, selectedWorkerId) {
        visibleWorkers.firstOrNull { it.workerEntryId == selectedWorkerId }
    }
    val workerEligibility = remember(selectedWorker) {
        selectedWorker?.let { SalaryAdvanceContract.describeWorkerEligibility(it) }
    }
    val bankStatus = remember(selectedWorker) {
        selectedWorker?.let { SalaryAdvanceContract.describeWorkerBankTransferEligibility(it) }
    }
    val parsedAmountVnd = remember(amountRaw) {
        SalaryAdvanceContract.normalizePositiveVndInput(amountRaw)
    }

    val isSubmitLockedByServerEligibility =
        (selectedProject != null && !selectedProject.hasActiveAccountant) ||
            (selectedWorker != null && workerEligibility != null && !workerEligibility.isEligible) ||
            (paymentMethod == "BANK_TRANSFER" && selectedWorker != null && bankStatus != null && !bankStatus.canUseBankTransfer)

    val dialogBody: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (renderInDialog) 0.95f else 1f)
                .testTag("salary_advance_create_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tạo yêu cầu ứng lương",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dành cho Quản lý dự án (QLDA) đối với NLĐ thuộc dự án đang quản lý.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (!submitting) onDismiss() },
                        enabled = !submitting,
                        modifier = Modifier.testTag("salary_advance_close_icon_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("salary_advance_create_scroll"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Chọn dự án quản lý
                    KeyedSelectDropdownField(
                        label = "Dự án quản lý *",
                        selectedLabel = selectedProject?.displayName.orEmpty(),
                        placeholder = "Chọn dự án quản lý",
                        options = activeProjects.map { proj ->
                            val suffix = if (!proj.hasActiveAccountant) " (Chưa có KT phụ trách)" else ""
                            DropdownOptionItem(
                                id = proj.projectId,
                                label = "${proj.displayName}$suffix"
                            )
                        },
                        enabled = !submitting && activeProjects.isNotEmpty(),
                        onSelect = { onSelectProject(it.id) },
                        testTag = "salary_advance_project_dropdown"
                    )

                    if (selectedProject != null && !selectedProject.hasActiveAccountant) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("salary_advance_project_accountant_warning")
                        ) {
                            Text(
                                text = "Dự án đang chọn hiện chưa có Kế toán phụ trách đang hoạt động nên không thể gửi yêu cầu ứng lương.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 2. Tìm kiếm và chọn NLĐ thuộc dự án (phân trang theo server)
                    OutlinedTextField(
                        value = workerSearch,
                        onValueChange = { onWorkerSearchChange(sanitizeSingleLineInput(it, 120)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (workerSearch.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onWorkerSearchChange("") },
                                    enabled = !submitting
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa tìm kiếm")
                                }
                            }
                        } else null,
                        label = { Text("Tìm NLĐ theo họ tên hoặc mã NLĐ…") },
                        singleLine = true,
                        enabled = !submitting && selectedProjectId.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("salary_advance_worker_search_input")
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("salary_advance_worker_list")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Danh sách NLĐ thuộc dự án (${visibleWorkers.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            when {
                                workersLoading -> {
                                    Row(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = "Đang tải danh sách NLĐ từ máy chủ…",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                workersError != null -> {
                                    Text(
                                        text = workersError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("salary_advance_workers_error")
                                    )
                                }

                                visibleWorkers.isEmpty() -> {
                                    Text(
                                        text = "Không có người lao động HRP đang làm việc hoặc chờ việc khớp điều kiện tìm kiếm.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(vertical = 8.dp)
                                            .testTag("salary_advance_workers_empty")
                                    )
                                }

                                else -> {
                                    visibleWorkers.forEach { worker ->
                                        val isSelected = worker.workerEntryId == selectedWorkerId
                                        val itemEligibility = SalaryAdvanceContract.describeWorkerEligibility(worker)
                                        val badgeColor = if (itemEligibility.isEligible) {
                                            SemanticColors.SuccessLight
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !submitting) {
                                                    onSelectWorker(worker.workerEntryId)
                                                }
                                                .testTag("salary_advance_worker_item_${worker.workerEntryId}")
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = worker.displayName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Mã NLĐ: ${worker.employeeCode} · ${DirectEntryContract.workStatusUiLabel(worker.employmentStatus)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontFamily = JetBrainsMono,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = badgeColor.copy(alpha = 0.14f)
                                                    ) {
                                                        Text(
                                                            text = itemEligibility.statusLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = badgeColor,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = "Ngày đầu làm việc: ${SalaryAdvanceContract.formatWorkDateVi(worker.firstWorkDate)} · Người tuyển: ${SalaryAdvanceContract.formatWorkerRecruiterLabel(worker)} · ${itemEligibility.portraitStatusLabel}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                if (!itemEligibility.isEligible) {
                                                    Text(
                                                        text = itemEligibility.blockingMessage ?: itemEligibility.reasonLabels.joinToString("; "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.testTag("salary_advance_worker_ineligible_reason_${worker.workerEntryId}")
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (workersHasMore) {
                                        OutlinedButton(
                                            onClick = onLoadMoreWorkers,
                                            enabled = !workersLoadingMore && !submitting,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("salary_advance_load_more_workers_button")
                                        ) {
                                            Text(if (workersLoadingMore) "Đang tải thêm NLĐ…" else "Tải thêm NLĐ")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Thông tin NLĐ đã chọn + Tóm tắt 30 ngày gần nhất + Nút Xem lịch sử ứng lương
                    if (selectedWorker != null && workerEligibility != null) {
                        val summary30d = selectedWorker.advance30dSummary
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("salary_advance_selected_worker_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Thông tin NLĐ: ${selectedWorker.displayName} (${selectedWorker.employeeCode})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Ngày đầu tiên làm việc:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SalaryAdvanceContract.formatWorkDateVi(selectedWorker.firstWorkDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JetBrainsMono,
                                        modifier = Modifier.testTag("salary_advance_worker_first_work_date")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Người tuyển:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SalaryAdvanceContract.formatWorkerRecruiterLabel(selectedWorker),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("salary_advance_worker_recruiter")
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                                Text(
                                    text = "Tóm tắt ứng trong 30 ngày gần nhất",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Số lần ứng (30 ngày):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${summary30d?.totalCount ?: 0} lần",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMono,
                                        modifier = Modifier.testTag("salary_advance_worker_30d_count")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tổng tiền ứng (30 ngày):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = SalaryAdvanceContract.formatVndAmount(summary30d?.totalAmountVnd ?: "0"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JetBrainsMono,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("salary_advance_worker_30d_amount")
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenWorkerHistory,
                                    enabled = !submitting,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("salary_advance_open_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Xem lịch sử ứng lương")
                                }

                                val bannerColor = if (workerEligibility.isEligible) {
                                    SemanticColors.SuccessLight
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bannerColor.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("salary_advance_worker_eligibility_banner")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = workerEligibility.statusLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = bannerColor
                                        )
                                        Text(
                                            text = workerEligibility.blockingMessage
                                                ?: workerEligibility.portraitStatusLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = bannerColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Phương thức nhận tiền (CASH / BANK_TRANSFER)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Phương thức nhận tiền *",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SalaryAdvanceContract.PAYMENT_METHODS.forEach { method ->
                                val label = SalaryAdvanceContract.paymentMethodLabel(method)
                                FilterChip(
                                    selected = paymentMethod == method,
                                    onClick = { onSelectPaymentMethod(method) },
                                    enabled = !submitting,
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("salary_advance_method_$method")
                                )
                            }
                        }

                        if (paymentMethod == "BANK_TRANSFER" && selectedWorker != null && bankStatus != null) {
                            val bankColor = if (bankStatus.canUseBankTransfer) {
                                SemanticColors.SuccessLight
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bankColor.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("salary_advance_bank_status_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = bankStatus.statusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = bankColor
                                    )
                                    val detailText = bankStatus.blockingMessage
                                        ?: "Tài khoản ngân hàng chính chủ đủ điều kiện nhận chuyển khoản."
                                    Text(
                                        text = detailText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = bankColor
                                    )
                                    if (!bankStatus.institutionName.isNullOrBlank()) {
                                        Text(
                                            text = "Ngân hàng: ${bankStatus.institutionName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (!bankStatus.accountHolderName.isNullOrBlank() || !bankStatus.accountNumberDisplay.isNullOrBlank()) {
                                        Text(
                                            text = "Chủ TK: ${bankStatus.accountHolderName.orEmpty()} · Số TK: ${bankStatus.accountNumberDisplay.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMono
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Số tiền ứng nguyên dương (VND)
                    OutlinedTextField(
                        value = amountRaw,
                        onValueChange = { raw ->
                            onAmountChange(raw.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == ' ' }.take(24))
                        },
                        label = { Text("Số tiền ứng (VND) *") },
                        supportingText = if (parsedAmountVnd != null) {
                            { Text("Sẽ gửi: ${SalaryAdvanceContract.formatVndAmount(parsedAmountVnd)}") }
                        } else null,
                        singleLine = true,
                        enabled = !submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("salary_advance_amount_input")
                    )

                    // 6. Nội dung đề nghị
                    OutlinedTextField(
                        value = initialContent,
                        onValueChange = { onInitialContentChange(sanitizeMultiLineInput(it, 2000)) },
                        label = { Text("Nội dung đề nghị *") },
                        placeholder = { Text("Nhập lý do và chi tiết đề nghị ứng lương…") },
                        enabled = !submitting,
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("salary_advance_content_input")
                    )

                    if (!createError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("salary_advance_create_error")
                        ) {
                            Text(
                                text = createError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !submitting,
                        modifier = Modifier.testTag("salary_advance_cancel_button")
                    ) {
                        Text("Đóng")
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = !submitting && !isSubmitLockedByServerEligibility,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("salary_advance_submit_button")
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang gửi…")
                        } else {
                            Text("Gửi yêu cầu ứng lương")
                        }
                    }
                }
            }
        }
    }

    if (renderInDialog) {
        Dialog(
            onDismissRequest = { if (!submitting) onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            dialogBody()
        }
    } else {
        dialogBody()
    }
}

@Composable
internal fun SalaryAdvanceWorkerHistoryDialog(
    selectedWorker: SalaryAdvanceWorkerOption?,
    periodMode: String,
    monthInput: String,
    fromDateInput: String,
    toDateInput: String,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    historyResponse: SalaryAdvanceWorkerHistoryResponse?,
    historyItems: List<SalaryAdvanceWorkerHistoryItem>,
    hasMore: Boolean,
    onSelectPeriodMode: (String) -> Unit,
    onUpdateMonth: (String) -> Unit,
    onUpdateDateRange: (String, String) -> Unit,
    onApplyCustomRange: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    renderInDialog: Boolean = true
) {
    val workerDisplayName = historyResponse?.workerDisplayName?.ifBlank { null }
        ?: selectedWorker?.displayName.orEmpty()
    val workerEmployeeCode = historyResponse?.workerEmployeeCode?.ifBlank { null }
        ?: selectedWorker?.employeeCode.orEmpty()

    val dialogBody: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (renderInDialog) 0.95f else 1f)
                .testTag("salary_advance_history_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lịch sử ứng lương NLĐ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (workerDisplayName.isNotBlank() || workerEmployeeCode.isNotBlank()) {
                            Text(
                                text = "$workerDisplayName ($workerEmployeeCode)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("salary_advance_history_close_icon_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bộ lọc kỳ tương tự Web
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SalaryAdvanceContract.HISTORY_PERIOD_MODES.forEach { (modeKey, modeLabel) ->
                            FilterChip(
                                selected = periodMode == modeKey,
                                onClick = { onSelectPeriodMode(modeKey) },
                                label = { Text(modeLabel) },
                                modifier = Modifier.testTag("salary_advance_history_mode_$modeKey")
                            )
                        }
                    }

                    if (periodMode == "MONTH") {
                        OutlinedTextField(
                            value = monthInput,
                            onValueChange = { onUpdateMonth(sanitizeSingleLineInput(it, 10)) },
                            label = { Text("Chọn tháng (YYYY-MM)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("salary_advance_history_month_input")
                        )
                    }

                    if (periodMode == "CUSTOM_RANGE") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = fromDateInput,
                                onValueChange = { onUpdateDateRange(sanitizeSingleLineInput(it, 12), toDateInput) },
                                label = { Text("Từ ngày") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("salary_advance_history_from_date_input")
                            )
                            OutlinedTextField(
                                value = toDateInput,
                                onValueChange = { onUpdateDateRange(fromDateInput, sanitizeSingleLineInput(it, 12)) },
                                label = { Text("Đến ngày") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("salary_advance_history_to_date_input")
                            )
                        }
                        OutlinedButton(
                            onClick = onApplyCustomRange,
                            enabled = !loading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("salary_advance_history_apply_range_button")
                        ) {
                            Text("Áp dụng khoảng ngày")
                        }
                    }

                    if (historyResponse != null) {
                        val periodDisplay = when {
                            !historyResponse.fromDate.isNullOrBlank() && !historyResponse.toDate.isNullOrBlank() ->
                                "${SalaryAdvanceContract.formatWorkDateVi(historyResponse.fromDate)} - ${SalaryAdvanceContract.formatWorkDateVi(historyResponse.toDate)}"
                            !historyResponse.month.isNullOrBlank() -> historyResponse.month
                            else -> SalaryAdvanceContract.HISTORY_PERIOD_MODES.firstOrNull { it.first == historyResponse.periodMode }?.second
                                ?: historyResponse.periodMode
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("salary_advance_history_summary_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Kỳ: $periodDisplay",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tổng số lần ứng: ${historyResponse.totalCount} lần · Tổng tiền: ${SalaryAdvanceContract.formatVndAmount(historyResponse.totalAmountVnd)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMono
                                )
                            }
                        }
                    }

                    when {
                        loading -> {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Đang tải lịch sử ứng lương…", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        error != null -> {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("salary_advance_history_error")
                            )
                        }

                        historyItems.isEmpty() -> {
                            Text(
                                text = "Chưa có yêu cầu ứng lương nào trong kỳ đã chọn.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("salary_advance_history_empty")
                            )
                        }

                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("salary_advance_history_list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                historyItems.forEach { item ->
                                    val statusColor = when (item.requestState) {
                                        "APPROVED" -> SemanticColors.SuccessLight
                                        "REJECTED" -> MaterialTheme.colorScheme.error
                                        else -> SemanticColors.WarningLight
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("salary_advance_history_item_${item.requestId}")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "#${item.requestId.take(8)}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = JetBrainsMono
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = statusColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "${SalaryAdvanceContract.requestStateShortLabel(item.requestState)} · ${SalaryAdvanceContract.evidenceReviewStateShortLabel(item.evidenceReviewState)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = statusColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Số tiền: ${SalaryAdvanceContract.formatVndAmount(item.requestedAmountVnd)} · ${SalaryAdvanceContract.paymentMethodLabel(item.paymentMethod)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Dự án: ${item.projectDisplayName} · Người tạo: ${SalaryAdvanceContract.resolveActorDisplayName(item.createdByDisplayName)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val formattedTime = SalaryAdvanceContract.formatDateTimeVi(
                                                item.effectiveAt.ifBlank { item.createdAt }
                                            )
                                            Text(
                                                text = "Thời gian: $formattedTime",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMono,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!item.initialContent.isNullOrBlank()) {
                                                Text(
                                                    text = "Nội dung: ${item.initialContent}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }

                                if (hasMore) {
                                    OutlinedButton(
                                        onClick = onLoadMore,
                                        enabled = !loadingMore,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("salary_advance_history_load_more_button")
                                    ) {
                                        Text(if (loadingMore) "Đang tải thêm…" else "Tải thêm lịch sử")
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("salary_advance_history_close_button")
                    ) {
                        Text("Đóng")
                    }
                }
            }
        }
    }

    if (renderInDialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            dialogBody()
        }
    } else {
        dialogBody()
    }
}
