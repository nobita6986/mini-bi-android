package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AppActor
import com.example.data.AttendanceAreaTab
import com.example.data.AttendanceContract
import com.example.data.AttendanceEntity
import com.example.data.AttendanceOutboxStatus
import com.example.data.AttendanceTimeContract
import com.example.data.AttendanceTimeSource
import com.example.data.CapturedAttendanceTime
import com.example.data.LeaveApprovalInboxProjection
import com.example.data.LeaveContract
import com.example.data.LeaveDraftDayInput
import com.example.data.LeaveMeSummaryProjection
import com.example.data.LeaveRequestProjection
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onBackToDashboard: () -> Unit,
    onSubmitAttendanceAction: ((photoFile: File, photoUri: String, note: String, capturedTime: CapturedAttendanceTime?) -> Unit)? = null,
    onRetryOutboxAction: (() -> Unit)? = null
) {
    BackHandler(onBack = onBackToDashboard)

    val gpsSnapshot by viewModel.gpsSnapshot.collectAsStateWithLifecycle()
    val gpsLoading by viewModel.gpsLoading.collectAsStateWithLifecycle()
    val gpsError by viewModel.gpsError.collectAsStateWithLifecycle()
    val submitting by viewModel.submittingAttendance.collectAsStateWithLifecycle()
    val timeAnchorState by viewModel.timeAnchorState.collectAsStateWithLifecycle()
    val allRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeAttendanceTab.collectAsStateWithLifecycle()
    val myLeaveSummary by viewModel.myLeaveSummary.collectAsStateWithLifecycle()
    val myLeaveLoading by viewModel.myLeaveLoading.collectAsStateWithLifecycle()
    val leaveInboxLoading by viewModel.leaveInboxLoading.collectAsStateWithLifecycle()
    val myLeaveErrorCode by viewModel.myLeaveErrorCode.collectAsStateWithLifecycle()

    val visibleTabs = remember(myLeaveSummary?.authority, myLeaveErrorCode) {
        LeaveContract.resolveVisibleAttendanceTabs(
            authority = myLeaveSummary?.authority,
            errorCode = myLeaveErrorCode
        )
    }
    val effectiveTab = remember(activeTab, visibleTabs) {
        if (activeTab in visibleTabs) activeTab else AttendanceAreaTab.CHECK_IN
    }

    val attendanceRecords = remember(allRecords, actor.appUserId) {
        allRecords.filter { it.appUserId == actor.appUserId }
    }

    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }
    var capturedPhotoUri by remember { mutableStateOf<String?>(null) }
    var capturedPhotoTime by remember { mutableStateOf<CapturedAttendanceTime?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var selectedRecordDetail by remember { mutableStateOf<AttendanceEntity?>(null) }

    val todayDateStr = remember(actor.appUserId, timeAnchorState.hasValidAnchor, timeAnchorState.serverTimeIso) {
        viewModel.resolveReferenceTodayIsoHcm(actor.appUserId)
    }
    val todayRecords = remember(attendanceRecords, todayDateStr) {
        attendanceRecords.filter { record ->
            val effectiveDate = record.serverBusinessDate ?: record.clientCapturedAtIso.take(10)
            effectiveDate == todayDateStr
        }
    }
    val syncedTodayRecords = remember(todayRecords) {
        todayRecords.filter { it.syncStatus == AttendanceOutboxStatus.SYNCED }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        if (success && file != null && file.exists() && file.length() > 0 && uri != null) {
            // Freeze capture time immediately upon camera return using BI server anchor + elapsedRealtime (or unverified fallback)
            capturedPhotoTime = viewModel.snapshotAttendanceCaptureTime(actor.appUserId)
            capturedPhotoFile = file
            capturedPhotoUri = uri.toString()
            captureError = null
        } else {
            file?.delete()
            capturedPhotoTime = null
            captureError = "Chưa chụp được ảnh xác thực từ camera thiết bị."
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocation) {
            viewModel.fetchCurrentGpsLocation()
        } else {
            captureError = "Vui lòng cấp quyền Vị trí GPS thực tế để thực hiện chấm công."
        }
        val hasCamera = permissions[Manifest.permission.CAMERA] == true
        if (hasCamera) {
            viewModel.syncAttendanceTimeAnchor(actor = actor, force = false)
            val pair = createPersistentCaptureFileAndUri(context)
            pendingCaptureFile = pair.first
            pendingCaptureUri = pair.second
            takePictureLauncher.launch(pair.second)
        } else {
            captureError = "Vui lòng cấp quyền Camera để chụp ảnh chấm công thực tế."
        }
    }

    // 1. Refresh server time anchor, server attendance history & leave authority/summary whenever Attendance screen is opened, and fetch GPS if granted
    LaunchedEffect(actor.appUserId) {
        viewModel.syncAttendanceTimeAnchor(actor = actor, force = true)
        viewModel.syncAttendanceHistoryFromServer(actor = actor)
        viewModel.refreshMyLeaveSummary()
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.fetchCurrentGpsLocation()
        }
    }

    // 2. Proactively refresh server time anchor when approaching expiration (`isExpiringSoon`)
    LaunchedEffect(actor.appUserId, timeAnchorState.expiresAtIso, timeAnchorState.hasValidAnchor) {
        if (timeAnchorState.hasValidAnchor) {
            val waitMs = viewModel.millisUntilTimeAnchorRefresh(actor.appUserId)
            if (waitMs != null) {
                if (waitMs > 0L) {
                    delay(waitMs)
                }
                viewModel.syncAttendanceTimeAnchor(actor = actor, force = false)
            }
        }
    }

    fun startOneTapGpsAndCameraFlow() {
        captureError = null
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasFine) {
            viewModel.syncAttendanceTimeAnchor(actor = actor, force = false)
            viewModel.fetchCurrentGpsLocation()
            val pair = createPersistentCaptureFileAndUri(context)
            pendingCaptureFile = pair.first
            pendingCaptureUri = pair.second
            takePictureLauncher.launch(pair.second)
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
                )
            )
        }
    }

    val retryableCount = remember(attendanceRecords) {
        attendanceRecords.count {
            it.syncStatus == AttendanceOutboxStatus.QUEUED ||
                it.syncStatus == AttendanceOutboxStatus.RETRYABLE_ERROR
        }
    }

    val isAttendanceRefreshing = when (effectiveTab) {
        AttendanceAreaTab.CHECK_IN -> timeAnchorState.syncing
        AttendanceAreaTab.LEAVE_REQUEST -> myLeaveLoading
        AttendanceAreaTab.LEAVE_APPROVAL -> leaveInboxLoading
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AttendanceAreaTopTabBar(
            tabs = visibleTabs,
            selectedTab = effectiveTab,
            onSelectTab = { viewModel.selectAttendanceTab(it) }
        )

        PullToRefreshBox(
            isRefreshing = isAttendanceRefreshing,
            onRefresh = {
                when (effectiveTab) {
                    AttendanceAreaTab.CHECK_IN -> {
                        viewModel.syncAttendanceTimeAnchor(actor = actor, force = true)
                        viewModel.syncAttendanceHistoryFromServer(actor = actor)
                        viewModel.refreshMyLeaveSummary()
                    }
                    AttendanceAreaTab.LEAVE_REQUEST -> {
                        viewModel.refreshMyLeaveSummary()
                    }
                    AttendanceAreaTab.LEAVE_APPROVAL -> {
                        viewModel.refreshLeaveApprovalInbox()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
        when (effectiveTab) {
            AttendanceAreaTab.CHECK_IN -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("attendance_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = "CHẤM CÔNG ĐỊNH VỊ & HÌNH ẢNH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ghi nhận Chấm công",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }

                    // Server-time anchor verification status banner
                    val anchorTint = when {
                        timeAnchorState.hasValidAnchor -> SemanticColors.SuccessLight
                        timeAnchorState.syncing -> MaterialTheme.colorScheme.primary
                        else -> SemanticColors.WarningLight
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = anchorTint.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attendance_time_anchor_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when {
                                        timeAnchorState.hasValidAnchor -> Icons.Default.Verified
                                        timeAnchorState.syncing -> Icons.Default.AccessTime
                                        else -> Icons.Default.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = anchorTint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = when {
                                            timeAnchorState.hasValidAnchor ->
                                                "Giờ chuẩn máy chủ BI đã xác thực"
                                            timeAnchorState.syncing ->
                                                "Đang đồng bộ mốc giờ chuẩn từ máy chủ..."
                                            else ->
                                                AttendanceContract.MSG_DEVICE_TIME_UNVERIFIED
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = anchorTint
                                    )
                                    Text(
                                        text = when {
                                            timeAnchorState.hasValidAnchor -> {
                                                val expiresVi = timeAnchorState.expiresAtIso
                                                    ?.let { formatIsoToVietnamDateTime(it).take(16) }
                                                    ?: "--"
                                                "Đối chiếu thời gian đơn điệu từ máy chủ (hiệu lực đến $expiresVi)"
                                            }
                                            timeAnchorState.syncing ->
                                                "Đang lấy mốc thời gian tham chiếu từ máy chủ BI"
                                            else ->
                                                timeAnchorState.lastSyncError
                                                    ?: "Chưa có mốc giờ máy chủ hợp lệ; lượt chấm công vẫn được lưu vào hàng đợi bền vững."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    viewModel.syncAttendanceTimeAnchor(actor = actor, force = true)
                                    viewModel.syncAttendanceHistoryFromServer(actor = actor)
                                },
                                enabled = !timeAnchorState.syncing,
                                modifier = Modifier.testTag("refresh_time_anchor_button")
                            ) {
                                if (timeAnchorState.syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Làm mới mốc giờ máy chủ"
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Đã chấm công trên máy chủ hôm nay ($todayDateStr)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when {
                                            syncedTodayRecords.isNotEmpty() -> {
                                                val latest = syncedTodayRecords.first()
                                                val displayIso = latest.serverCapturedAtIso ?: latest.clientCapturedAtIso
                                                val displayTimeVi = formatIsoToVietnamDateTime(displayIso).drop(11).take(8)
                                                "Xác nhận gần nhất lúc $displayTimeVi"
                                            }
                                            todayRecords.isNotEmpty() ->
                                                "Chưa có lượt nào được máy chủ xác nhận (${todayRecords.size} bản ghi tại máy)"
                                            else ->
                                                "Chưa có lượt chấm công nào hôm nay"
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "${syncedTodayRecords.size} lần",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                ) {
                                    Icon(
                                        imageVector = if (gpsSnapshot != null) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                                        contentDescription = "GPS",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = when {
                                            gpsLoading -> "Đang lấy tọa độ GPS thực tế..."
                                            gpsSnapshot != null -> "GPS: %.6f, %.6f (±%.1fm)".format(
                                                Locale.US,
                                                gpsSnapshot!!.latitude,
                                                gpsSnapshot!!.longitude,
                                                gpsSnapshot!!.accuracyMeters
                                            )
                                            else -> "Chưa có tọa độ GPS thực tế"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = JetBrainsMono
                                    )
                                    Text(
                                        text = gpsSnapshot?.addressHint
                                            ?: gpsError
                                            ?: "Yêu cầu bật GPS thiết bị và chụp ảnh thật từ camera",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (gpsError != null && gpsSnapshot == null)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.fetchCurrentGpsLocation()
                                    } else {
                                        permissionsLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("refresh_gps_button")
                            ) {
                                if (gpsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Làm mới GPS"
                                    )
                                }
                            }
                        }
                    }

                    captureError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { startOneTapGpsAndCameraFlow() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .testTag("primary_checkin_camera_button")
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val maxLabelWidth = (this.maxWidth - 88.dp).coerceAtLeast(120.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("primary_checkin_camera_icon")
                                    )
                                }
                                Text(
                                    text = if (capturedPhotoFile == null) {
                                        "BẤM CHẤM CÔNG\n(Lấy GPS & Chụp Ảnh)"
                                    } else {
                                        "Chụp lại Ảnh Chấm Công"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(lineHeight = 21.sp),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .widthIn(max = maxLabelWidth)
                                        .testTag("primary_checkin_camera_label")
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    AnimatedVisibility(visible = capturedPhotoFile != null && capturedPhotoUri != null) {
                        val isServerAnchoredPreview =
                            capturedPhotoTime?.timeSource == AttendanceTimeSource.SERVER_ANCHORED
                        val previewAccent = if (isServerAnchoredPreview) {
                            SemanticColors.SuccessLight
                        } else {
                            SemanticColors.WarningLight
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.5.dp,
                                    color = previewAccent.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isServerAnchoredPreview) {
                                        Icons.Default.Verified
                                    } else {
                                        Icons.Default.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = previewAccent
                                )
                                Column {
                                    Text(
                                        text = if (isServerAnchoredPreview) {
                                            "Đã khóa thời điểm chụp theo giờ chuẩn máy chủ"
                                        } else {
                                            AttendanceContract.MSG_DEVICE_TIME_UNVERIFIED
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = previewAccent
                                    )
                                    capturedPhotoTime?.let { frozen ->
                                        Text(
                                            text = "Thời điểm chụp đã cố định: ${frozen.clientCapturedAtIso.take(19).replace('T', ' ')}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMono,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            AsyncImage(
                                model = capturedPhotoUri,
                                contentDescription = "Ảnh chấm công vừa chụp",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Ghi chú (tùy chọn)") },
                                placeholder = { Text("Nhập ghi chú nếu cần...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("attendance_note_input")
                            )

                            Button(
                                onClick = {
                                    val file = capturedPhotoFile ?: return@Button
                                    val uriStr = capturedPhotoUri ?: return@Button
                                    val frozenTime = capturedPhotoTime
                                    if (gpsSnapshot == null) {
                                        captureError = "Chưa có tọa độ GPS thực tế. Vui lòng bấm làm mới GPS."
                                        return@Button
                                    }
                                    if (onSubmitAttendanceAction != null) {
                                        onSubmitAttendanceAction.invoke(file, uriStr, note, frozenTime)
                                    } else {
                                        viewModel.submitAttendanceCheckIn(file, uriStr, note, frozenTime)
                                    }
                                    capturedPhotoFile = null
                                    capturedPhotoUri = null
                                    capturedPhotoTime = null
                                    note = ""
                                },
                                enabled = !submitting && gpsSnapshot != null,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = previewAccent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("send_attendance_to_backend_button")
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Đang gửi chấm công lên máy chủ...")
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Xác Nhận Gửi Chấm Công",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nhật ký & Hàng đợi đồng bộ (${attendanceRecords.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Hiển thị trạng thái xác nhận thực tế từ máy chủ hoặc hàng đợi ngoại tuyến",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (retryableCount > 0) {
                    OutlinedButton(
                        onClick = {
                            if (onRetryOutboxAction != null) {
                                onRetryOutboxAction.invoke()
                            } else {
                                viewModel.retryAttendanceOutbox()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("retry_attendance_outbox_button")
                    ) {
                        Text("Đồng bộ ($retryableCount)")
                    }
                }
            }
        }

        if (attendanceRecords.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Chưa có lượt chấm công nào",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Bấm nút chụp ảnh & định vị GPS phía trên để gửi yêu cầu chấm công về máy chủ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(attendanceRecords, key = { _, item -> item.id }) { _, item ->
                AttendanceHistoryCard(
                    record = item,
                    serverSequenceInDay = item.serverSequenceInDay,
                    expanded = selectedRecordDetail?.id == item.id,
                    onClick = {
                        selectedRecordDetail = if (selectedRecordDetail?.id == item.id) null else item
                    },
                    onRetryCaptureClick = {
                        startOneTapGpsAndCameraFlow()
                    }
                )
            }
        }
                }
            }
            AttendanceAreaTab.LEAVE_REQUEST -> {
                LeaveRequestTabContent(
                    actor = actor,
                    viewModel = viewModel
                )
            }
            AttendanceAreaTab.LEAVE_APPROVAL -> {
                LeaveApprovalTabContent(
                    actor = actor,
                    viewModel = viewModel
                )
            }
        }
        }
    }
}

@Composable
internal fun AttendanceHistoryCard(
    record: AttendanceEntity,
    serverSequenceInDay: Int?,
    expanded: Boolean,
    onClick: () -> Unit,
    onRetryCaptureClick: (() -> Unit)? = null
) {
    val effectiveTimeSource = record.serverTimeSource ?: record.timeSource
    val isDeviceClockUnverified = effectiveTimeSource == AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED
    val displayStatusMessage = remember(
        record.syncStatus,
        record.backendErrorCode,
        record.httpStatusCode,
        record.statusMessage
    ) {
        AttendanceContract.resolveDisplayStatusMessage(
            syncStatus = record.syncStatus,
            backendErrorCode = record.backendErrorCode,
            httpStatusCode = record.httpStatusCode,
            rawStatusMessage = record.statusMessage
        )
    }

    val statusColor = when (record.syncStatus) {
        AttendanceOutboxStatus.SYNCED -> SemanticColors.SuccessLight
        AttendanceOutboxStatus.QUEUED,
        AttendanceOutboxStatus.SYNCING,
        AttendanceOutboxStatus.RETRYABLE_ERROR -> SemanticColors.WarningLight
        else -> SemanticColors.ErrorLight
    }
    val statusIcon = when (record.syncStatus) {
        AttendanceOutboxStatus.SYNCED -> Icons.Default.CloudDone
        AttendanceOutboxStatus.QUEUED,
        AttendanceOutboxStatus.SYNCING,
        AttendanceOutboxStatus.RETRYABLE_ERROR -> Icons.Default.CloudUpload
        AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE -> Icons.Default.CloudOff
        else -> Icons.Default.ErrorOutline
    }
    val badgeLabel = when {
        record.syncStatus == AttendanceOutboxStatus.SYNCED && serverSequenceInDay != null ->
            "Lần #$serverSequenceInDay trong ngày"
        record.syncStatus == AttendanceOutboxStatus.SYNCED ->
            "Đã chấm công"
        record.syncStatus == AttendanceOutboxStatus.QUEUED ->
            "Đang chờ đồng bộ"
        record.syncStatus == AttendanceOutboxStatus.SYNCING ->
            "Đang gửi..."
        record.syncStatus == AttendanceOutboxStatus.RETRYABLE_ERROR ->
            "Chờ tự động thử lại"
        record.syncStatus == AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE ->
            "Chưa bật trên máy chủ"
        record.syncStatus == AttendanceOutboxStatus.AUTH_REQUIRED ->
            "Cần đăng nhập lại"
        record.syncStatus == AttendanceOutboxStatus.DENIED ->
            "Chưa đủ quyền"
        else ->
            "Chưa thành công"
    }
    val expandedStatusTitle = when (record.syncStatus) {
        AttendanceOutboxStatus.SYNCED -> "Đã xác nhận"
        AttendanceOutboxStatus.QUEUED -> "Đang chờ đồng bộ"
        AttendanceOutboxStatus.SYNCING -> "Đang gửi lên máy chủ"
        AttendanceOutboxStatus.RETRYABLE_ERROR -> "Đang chờ tự động gửi lại"
        AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE -> "Chưa bật trên máy chủ"
        AttendanceOutboxStatus.AUTH_REQUIRED -> "Cần đăng nhập lại"
        AttendanceOutboxStatus.DENIED -> "Chưa đủ quyền"
        else -> "Chưa thành công — vui lòng thử lại"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = record.photoUri,
                    contentDescription = "Ảnh chấm công",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = record.clientCapturedAtIso.take(19).replace('T', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = record.userDisplayName,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        val locationSummary = if (
                            record.photoFilePath.isBlank() &&
                            record.latitude == 0.0 &&
                            record.longitude == 0.0 &&
                            record.accuracyMeters == 0f
                        ) {
                            record.addressHint.ifBlank { "Đã ghi nhận trên máy chủ" }
                        } else {
                            "%.5f, %.5f (±%.1fm)".format(
                                Locale.US,
                                record.latitude,
                                record.longitude,
                                record.accuracyMeters
                            )
                        }
                        Text(
                            text = locationSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(14.dp)
                        )
                        Text(
                            text = displayStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Honest time-source display: never display DEVICE_CLOCK_UNVERIFIED as server-verified time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isDeviceClockUnverified) {
                                Icons.Default.WarningAmber
                            } else {
                                Icons.Default.Verified
                            },
                            contentDescription = null,
                            tint = if (isDeviceClockUnverified) {
                                SemanticColors.WarningLight
                            } else {
                                SemanticColors.SuccessLight
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isDeviceClockUnverified) {
                                AttendanceContract.MSG_DEVICE_TIME_UNVERIFIED
                            } else {
                                "Giờ đối chiếu máy chủ"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDeviceClockUnverified) {
                                SemanticColors.WarningLight
                            } else {
                                SemanticColors.SuccessLight
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (record.syncStatus == AttendanceOutboxStatus.REJECTED && onRetryCaptureClick != null) {
                        OutlinedButton(
                            onClick = onRetryCaptureClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .heightIn(min = 34.dp)
                                .testTag("retry_checkin_card_button_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chụp lại & Thử lại",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Chi tiết lượt chấm công ($expandedStatusTitle):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isDeviceClockUnverified) {
                            "• Nguồn thời gian: ${AttendanceContract.MSG_DEVICE_TIME_UNVERIFIED}"
                        } else {
                            "• Nguồn thời gian: Giờ chuẩn máy chủ xác thực"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Thời điểm chụp: ${formatIsoToVietnamDateTime(record.clientCapturedAtIso)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (record.serverCheckinId != null) {
                        Text(
                            text = "• Ngày nghiệp vụ máy chủ: ${record.serverBusinessDate ?: "--"} (Lượt #${record.serverSequenceInDay ?: "-"} / Tổng ${record.serverDailyCount ?: "-"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Giờ máy chủ ghi nhận: ${record.serverCapturedAtIso?.let { formatIsoToVietnamDateTime(it) } ?: "--"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Giờ máy chủ tiếp nhận: ${record.serverReceivedAtIso?.let { formatIsoToVietnamDateTime(it) } ?: "--"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (record.serverReplayed == true) {
                            Text(
                                text = "• Chống trùng: Phản hồi phát lại an toàn",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        text = "• Địa điểm gợi ý: ${record.addressHint.ifBlank { "Không có" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (record.note.isNotBlank()) {
                        Text(
                            text = "• Ghi chú: ${record.note}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

internal fun formatIsoToVietnamDateTime(iso: String): String {
    val trimmed = iso.trim()
    if (trimmed.isEmpty()) return "--"
    return runCatching {
        val epochMs = AttendanceTimeContract.parseIsoInstantToEpochMillis(trimmed, "display_iso")
        AttendanceTimeContract.formatEpochMillisInHcmIso(epochMs).take(19).replace('T', ' ')
    }.getOrElse {
        trimmed.take(19).replace('T', ' ')
    }
}

private fun createPersistentCaptureFileAndUri(context: Context): Pair<File, Uri> {
    val dir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
    val file = File(dir, "checkin_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    return file to uri
}

@Composable
private fun AttendanceAreaTopTabBar(
    tabs: List<AttendanceAreaTab>,
    selectedTab: AttendanceAreaTab,
    onSelectTab: (AttendanceAreaTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("attendance_area_tab_row"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val tag = when (tab) {
                    AttendanceAreaTab.CHECK_IN -> "attendance_tab_checkin"
                    AttendanceAreaTab.LEAVE_REQUEST -> "attendance_tab_leave_request"
                    AttendanceAreaTab.LEAVE_APPROVAL -> "attendance_tab_leave_approval"
                }
                val icon = when (tab) {
                    AttendanceAreaTab.CHECK_IN -> Icons.Default.PhotoCamera
                    AttendanceAreaTab.LEAVE_REQUEST -> Icons.AutoMirrored.Filled.EventNote
                    AttendanceAreaTab.LEAVE_APPROVAL -> Icons.AutoMirrored.Filled.FactCheck
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab) }
                        .testTag(tag)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveRequestTabContent(
    actor: AppActor,
    viewModel: MiniBiViewModel
) {
    val context = LocalContext.current
    val leaveYear by viewModel.leaveYear.collectAsStateWithLifecycle()
    val myLeaveSummary by viewModel.myLeaveSummary.collectAsStateWithLifecycle()
    val myLeaveLoading by viewModel.myLeaveLoading.collectAsStateWithLifecycle()
    val myLeaveError by viewModel.myLeaveError.collectAsStateWithLifecycle()
    val leaveDraftReason by viewModel.leaveDraftReason.collectAsStateWithLifecycle()
    val leaveMutationBusy by viewModel.leaveMutationBusy.collectAsStateWithLifecycle()
    val leaveMutationError by viewModel.leaveMutationError.collectAsStateWithLifecycle()
    val draftDays = viewModel.leaveDraftDays

    var withdrawDialogTarget by remember { mutableStateOf<LeaveRequestProjection?>(null) }
    var withdrawReasonInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.ensureInitialLeaveDraftDay()
        if (myLeaveSummary == null && !myLeaveLoading) {
            viewModel.refreshMyLeaveSummary(year = leaveYear)
        }
    }

    val totalDraftHalfDayUnits = draftDays.sumOf {
        LeaveContract.leaveSessionHalfDayUnits(it.sessionCode)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("leave_request_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (myLeaveError != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_summary_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = myLeaveError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.refreshMyLeaveSummary(year = leaveYear) },
                            modifier = Modifier.testTag("leave_summary_retry_button")
                        ) {
                            Text("Thử lại")
                        }
                    }
                }
            }
        }

        val summary = myLeaveSummary
        if (myLeaveLoading && summary == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Đang tải số dư phép và lịch sử đơn từ máy chủ...")
                    }
                }
            }
        }

        if (summary != null) {
            item {
                LeaveCreateRequestFormCard(
                    summary = summary,
                    draftDays = draftDays,
                    totalDraftHalfDayUnits = totalDraftHalfDayUnits,
                    reason = leaveDraftReason,
                    busy = leaveMutationBusy,
                    errorMessage = leaveMutationError,
                    onAddDay = { viewModel.addLeaveDraftDay() },
                    onRemoveDay = { viewModel.removeLeaveDraftDay(it) },
                    onUpdateDayDate = { id, dateStr -> viewModel.updateLeaveDraftDayDate(id, dateStr) },
                    onOpenDatePicker = { day ->
                        openIsoDatePicker(
                            context = context,
                            initialIsoDate = day.leaveDate,
                            onDatePicked = { picked ->
                                viewModel.updateLeaveDraftDayDate(day.id, picked)
                            }
                        )
                    },
                    onUpdateDaySession = { id, session -> viewModel.updateLeaveDraftDaySession(id, session) },
                    onUpdateDayType = { id, leaveType -> viewModel.updateLeaveDraftDayType(id, leaveType) },
                    onSplitDayToHalfDays = { id -> viewModel.splitLeaveDraftDayToHalfDays(id) },
                    onAutoAllocateByBalance = { viewModel.autoAllocateLeaveDraftDaysByBalance() },
                    onApplyDateRange = { startIso, endIso, leaveType, skipSundays ->
                        viewModel.applyLeaveDraftDateRange(
                            startDateIso = startIso,
                            endDateIso = endIso,
                            leaveType = leaveType,
                            skipSundays = skipSundays
                        )
                    },
                    onChooseAnyIsoDate = { initialIso, onPicked ->
                        openIsoDatePicker(
                            context = context,
                            initialIsoDate = initialIso,
                            onDatePicked = onPicked
                        )
                    },
                    onUpdateReason = { viewModel.updateLeaveDraftReason(it) },
                    onSubmit = { viewModel.submitLeaveRequest() }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TỒN PHÉP & LỊCH SỬ NGHỈ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tra cứu năm $leaveYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Chọn năm trước để xem lịch sử nghỉ & tồn công phép",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentViYear = remember { LeaveContract.currentVietnamLeaveYear() }
                        val selectableYears = remember(currentViYear) {
                            LeaveContract.selectableHistoryLeaveYears(currentViYear)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                selectableYears.forEach { y ->
                                    val isSelected = leaveYear == y
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setLeaveYear(y) }
                                            .testTag("leave_year_chip_$y")
                                    ) {
                                        Text(
                                            text = "$y",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.refreshMyLeaveSummary(year = leaveYear) },
                            enabled = !myLeaveLoading,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("leave_refresh_summary_button")
                        ) {
                            if (myLeaveLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Làm mới thông tin phép",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (summary != null) {
            item {
                LeaveBalanceAndRoutingSummaryCard(summary = summary)
            }

            item {
                LeaveMyRequestHistoryCard(
                    actorAppUserId = actor.appUserId,
                    requests = summary.requests,
                    busy = leaveMutationBusy,
                    onRequestWithdraw = { req ->
                        withdrawReasonInput = ""
                        withdrawDialogTarget = req
                    }
                )
            }

            if (summary.balance.lots.isNotEmpty()) {
                item {
                    LeaveEntitlementLotsCard(summary = summary)
                }
            }
        }
    }

    val targetToWithdraw = withdrawDialogTarget
    if (targetToWithdraw != null) {
        Dialog(
            onDismissRequest = {
                if (!leaveMutationBusy) {
                    withdrawDialogTarget = null
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .testTag("leave_withdraw_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Rút đơn xin nghỉ phép",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bạn muốn rút đơn xin nghỉ ${LeaveContract.formatHalfDayUnits(targetToWithdraw.requestedUnits)} đang chờ duyệt?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = withdrawReasonInput,
                        onValueChange = { withdrawReasonInput = it },
                        label = { Text("Ghi chú rút đơn (tùy chọn)") },
                        placeholder = { Text("Nhập lý do rút đơn nếu cần...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leave_withdraw_reason_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { withdrawDialogTarget = null },
                            enabled = !leaveMutationBusy,
                            modifier = Modifier.testTag("leave_withdraw_cancel_button")
                        ) {
                            Text("Đóng")
                        }
                        Button(
                            onClick = {
                                viewModel.withdrawLeaveRequest(
                                    request = targetToWithdraw,
                                    reason = withdrawReasonInput.trim().ifEmpty { null },
                                    onSuccess = {
                                        withdrawDialogTarget = null
                                        withdrawReasonInput = ""
                                    }
                                )
                            },
                            enabled = !leaveMutationBusy,
                            modifier = Modifier.testTag("leave_withdraw_confirm_button")
                        ) {
                            Text(if (leaveMutationBusy) "Đang rút..." else "Xác nhận rút đơn")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveBalanceAndRoutingSummaryCard(
    summary: LeaveMeSummaryProjection
) {
    val balance = summary.balance
    val routing = summary.routing
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leave_balance_summary_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Số dư phép & Thông tin cá nhân (tính đến ${LeaveContract.formatIsoDateVi(summary.asOfDate)})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LeaveStatMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Phép khả dụng",
                    value = LeaveContract.formatHalfDayUnits(balance.availableUnits),
                    subtitle = "Phép tồn còn dùng: ${LeaveContract.formatHalfDayUnits(balance.carryOverAvailableUnits)}",
                    accentColor = SemanticColors.SuccessLight,
                    testTag = "leave_metric_available"
                )
                LeaveStatMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Tổng phép được cấp",
                    value = LeaveContract.formatHalfDayUnits(balance.totalGrantedUnits),
                    subtitle = "Định kỳ: ${LeaveContract.formatHalfDayUnits(balance.monthlyAccruedUnits)} • Thâm niên: ${LeaveContract.formatHalfDayUnits(balance.seniorityGrantedUnits)}",
                    accentColor = MaterialTheme.colorScheme.primary,
                    testTag = "leave_metric_granted"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val totalPendingUnits = balance.pendingAnnualUnits + balance.pendingNonAnnualUnits
                LeaveStatMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Giữ chỗ / Chờ duyệt",
                    value = LeaveContract.formatHalfDayUnits(balance.reservedUnits + totalPendingUnits),
                    subtitle = "Giữ chỗ phép năm: ${LeaveContract.formatHalfDayUnits(balance.reservedUnits)} • Chờ duyệt: ${LeaveContract.formatHalfDayUnits(totalPendingUnits)}",
                    accentColor = SemanticColors.WarningLight,
                    testTag = "leave_metric_reserved_pending"
                )
                LeaveStatMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Đã sử dụng",
                    value = LeaveContract.formatHalfDayUnits(balance.consumedUnits),
                    subtitle = "Hết hạn: ${LeaveContract.formatHalfDayUnits(balance.expiredUnits)} • Ngoài phép năm: ${LeaveContract.formatHalfDayUnits(balance.approvedNonAnnualUnits)}",
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "leave_metric_consumed"
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leave_routing_info_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Trạng thái nhân sự: ${LeaveContract.leaveEmploymentStatusLabel(balance.employmentStatus)} • Đơn vị: ${LeaveContract.leaveUnitKindLabel(balance.unitKind)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Luồng duyệt đơn: ${LeaveContract.leaveRouteKindLabel(routing.routeKind, routing.teamName, routing.unitKind)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Ngày vào làm: ${LeaveContract.formatIsoDateVi(balance.workStartDate)} • Lên chính thức: ${LeaveContract.formatIsoDateVi(balance.officialEffectiveDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (balance.nextSeniorityEffectiveDate != null && balance.nextSeniorityUnits > 0) {
                        Text(
                            text = "Mốc tăng phép thâm niên tiếp theo: ${LeaveContract.formatIsoDateVi(balance.nextSeniorityEffectiveDate)} (+${LeaveContract.formatHalfDayUnits(balance.nextSeniorityUnits)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (summary.policy.carryOverExpiresAfterDate != null) {
                        Text(
                            text = "Hạn sử dụng phép tồn năm trước: đến hết ${LeaveContract.formatIsoDateVi(summary.policy.carryOverExpiresAfterDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!balance.profileConfigured || balance.employmentStatus.equals("PROBATION", ignoreCase = true)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SemanticColors.WarningLight.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_probation_or_unconfigured_banner")
                ) {
                    Text(
                        text = if (!balance.profileConfigured) {
                            "Hồ sơ nghỉ phép của bạn chưa được cấu hình đầy đủ trạng thái Chính thức nên số dư Phép năm hiện bằng 0. Bạn vẫn có thể chọn các loại nghỉ ngoài phép năm (Nghỉ tang, Nghỉ cưới, Nghỉ ốm, Nghỉ không lương)."
                        } else {
                            "Bạn đang trong giai đoạn Thử việc nên số dư Phép năm hiện bằng 0 (phép định kỳ sẽ cộng bù khi lên Chính thức). Trong thời gian thử việc, vui lòng chọn các loại nghỉ ngoài phép năm (Nghỉ không lương, Nghỉ ốm, Nghỉ cưới, Nghỉ tang)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SemanticColors.WarningLight,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LeaveContract.leaveFallbackReasonLabel(routing.fallbackReason)?.let { fallbackLabel ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Lưu ý định tuyến duyệt: $fallbackLabel. Đơn xin nghỉ sẽ được chuyển thẳng tới hàng đợi Admin / Ban Giám đốc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaveStatMetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    testTag: String
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LeaveCreateRequestFormCard(
    summary: LeaveMeSummaryProjection,
    draftDays: List<LeaveDraftDayInput>,
    totalDraftHalfDayUnits: Int,
    reason: String,
    busy: Boolean,
    errorMessage: String?,
    onAddDay: () -> Unit,
    onRemoveDay: (String) -> Unit,
    onUpdateDayDate: (String, String) -> Unit,
    onOpenDatePicker: (LeaveDraftDayInput) -> Unit,
    onUpdateDaySession: (String, String) -> Unit,
    onUpdateDayType: (String, String) -> Unit,
    onSplitDayToHalfDays: (String) -> Unit,
    onAutoAllocateByBalance: () -> Unit,
    onApplyDateRange: (String, String, String, Boolean) -> Boolean = { _, _, _, _ -> false },
    onChooseAnyIsoDate: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onUpdateReason: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val draftEval = remember(draftDays, reason, summary.leaveYear, summary.balance.availableUnits) {
        LeaveContract.evaluateLeaveRequestDraft(
            days = draftDays,
            reason = reason,
            leaveYear = summary.leaveYear,
            availableUnits = summary.balance.availableUnits
        )
    }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var rangeStartIso by remember { mutableStateOf(draftDays.firstOrNull()?.leaveDate ?: summary.asOfDate) }
    var rangeEndIso by remember {
        mutableStateOf(
            draftDays.lastOrNull()?.leaveDate
                ?: LeaveContract.nextIsoDateAfter(draftDays.firstOrNull()?.leaveDate ?: summary.asOfDate)
        )
    }
    var rangeLeaveType by remember {
        mutableStateOf(
            draftDays.firstOrNull()?.leaveType
                ?: if (summary.balance.availableUnits > 0) "ANNUAL" else "UNPAID"
        )
    }
    var rangeSkipSundays by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leave_create_form_card"),
        shape = RoundedCornerShape(18.dp),
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
                        text = "Tạo đơn xin nghỉ phép",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Chọn ngày, buổi và loại nghỉ (Phép năm trừ FIFO • Hỗ trợ tách 2 buổi khác loại trong cùng ngày)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Tổng: ${LeaveContract.formatHalfDayUnits(totalDraftHalfDayUnits)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("leave_draft_total_units_badge")
                    )
                }
            }

            if (!summary.authority.canSubmitRequest || summary.authority.isSharedNonEmployee) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tài khoản hiện tại không thuộc đối tượng tạo đơn xin nghỉ phép cá nhân.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                return@Column
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy) {
                        val firstIso = draftDays.firstOrNull()?.leaveDate ?: summary.asOfDate
                        val lastIso = draftDays.lastOrNull()?.leaveDate ?: LeaveContract.nextIsoDateAfter(firstIso)
                        rangeStartIso = firstIso
                        rangeEndIso = if (lastIso >= firstIso) lastIso else LeaveContract.nextIsoDateAfter(firstIso)
                        rangeLeaveType = draftDays.firstOrNull()?.leaveType
                            ?: if (summary.balance.availableUnits > 0) "ANNUAL" else "UNPAID"
                        showDateRangeDialog = true
                    }
                    .testTag("leave_open_date_range_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chọn nhanh theo dải ngày (Từ ngày → Đến ngày)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tự động tạo danh sách nhiều ngày nghỉ liên tiếp chỉ trong 1 lần chọn",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            draftDays.forEachIndexed { index, day ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_draft_day_row_$index")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = day.leaveDate,
                                onValueChange = { onUpdateDayDate(day.id, it) },
                                label = { Text("Ngày nghỉ #${index + 1} (YYYY-MM-DD)") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onOpenDatePicker(day) },
                                        modifier = Modifier.testTag("leave_draft_date_picker_btn_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Chọn ngày nghỉ"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("leave_draft_date_input_$index")
                            )
                            if (day.sessionCode.equals("FULL", ignoreCase = true) && draftDays.size < 120) {
                                OutlinedButton(
                                    onClick = { onSplitDayToHalfDays(day.id) },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("leave_draft_split_btn_$index")
                                ) {
                                    Text(
                                        text = "Tách 2 buổi",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (draftDays.size > 1) {
                                IconButton(
                                    onClick = { onRemoveDay(day.id) },
                                    enabled = !busy,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("leave_draft_remove_day_btn_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Xóa ngày nghỉ",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LeaveContract.SESSION_CODES.forEach { code ->
                                val isSelected = day.sessionCode.equals(code, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable(enabled = !busy) { onUpdateDaySession(day.id, code) }
                                        .testTag("leave_draft_session_${code}_$index")
                                ) {
                                    Text(
                                        text = LeaveContract.leaveSessionCompactLabel(code),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Loại nghỉ: ${LeaveContract.leaveTypeLabel(day.leaveType)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LeaveContract.LEAVE_TYPES.forEach { typeCode ->
                                    val isSelected = day.leaveType.equals(typeCode, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable(enabled = !busy) { onUpdateDayType(day.id, typeCode) }
                                            .testTag("leave_draft_type_${typeCode}_$index")
                                    ) {
                                        Text(
                                            text = LeaveContract.leaveTypeShortLabel(typeCode),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leave_draft_breakdown_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        draftEval.typeBreakdown.forEach { tb ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (tb.leaveType == "ANNUAL") {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = "${LeaveContract.leaveTypeShortLabel(tb.leaveType)}: ${LeaveContract.formatHalfDayUnits(tb.halfDayUnits)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (tb.leaveType == "ANNUAL") {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "• Phép năm khả dụng: ${LeaveContract.formatHalfDayUnits(summary.balance.availableUnits)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (draftEval.nonAnnualUnits > 0) {
                        Text(
                            text = "Các ngày nghỉ ngoài phép năm (Nghỉ tang, Nghỉ cưới, Nghỉ ốm, Nghỉ không lương) không trừ vào số dư phép năm khả dụng.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (draftEval.canSplitExcessAnnualToUnpaid) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SemanticColors.WarningLight.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_auto_split_balance_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Số ngày Phép năm đang chọn (${LeaveContract.formatHalfDayUnits(draftEval.annualUnits)}) vượt quá số dư khả dụng (${LeaveContract.formatHalfDayUnits(summary.balance.availableUnits)}). Bạn có thể tách phần vượt (${LeaveContract.formatHalfDayUnits(draftEval.excessAnnualUnits)}) sang Nghỉ không lương.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SemanticColors.WarningLight
                        )
                        OutlinedButton(
                            onClick = onAutoAllocateByBalance,
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("leave_auto_split_balance_button")
                        ) {
                            Text("Tự động chia Phép năm + Nghỉ không lương")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddDay,
                    enabled = !busy && draftDays.size < 120,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("leave_add_day_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thêm 1 ngày kế tiếp")
                }

                OutlinedButton(
                    onClick = {
                        val firstIso = draftDays.firstOrNull()?.leaveDate ?: summary.asOfDate
                        val lastIso = draftDays.lastOrNull()?.leaveDate ?: LeaveContract.nextIsoDateAfter(firstIso)
                        rangeStartIso = firstIso
                        rangeEndIso = if (lastIso >= firstIso) lastIso else LeaveContract.nextIsoDateAfter(firstIso)
                        showDateRangeDialog = true
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("leave_add_date_range_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chọn dải ngày")
                }
            }

            if (showDateRangeDialog) {
                val (previewDays, previewErr) = remember(rangeStartIso, rangeEndIso, rangeLeaveType, rangeSkipSundays) {
                    LeaveContract.generateDraftDaysFromRange(
                        startDateIso = rangeStartIso,
                        endDateIso = rangeEndIso,
                        leaveType = rangeLeaveType,
                        sessionCode = "FULL",
                        skipSundays = rangeSkipSundays
                    )
                }
                Dialog(
                    onDismissRequest = { showDateRangeDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .testTag("leave_date_range_dialog"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Chọn dải ngày nghỉ phép",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = rangeStartIso,
                                    onValueChange = { rangeStartIso = it.trim() },
                                    label = { Text("Từ ngày") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            onChooseAnyIsoDate(rangeStartIso) { picked ->
                                                rangeStartIso = picked
                                                if (rangeEndIso < picked) {
                                                    rangeEndIso = picked
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = "Chọn từ ngày")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("leave_range_start_input")
                                )
                                OutlinedTextField(
                                    value = rangeEndIso,
                                    onValueChange = { rangeEndIso = it.trim() },
                                    label = { Text("Đến ngày") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            onChooseAnyIsoDate(rangeEndIso) { picked ->
                                                rangeEndIso = picked
                                            }
                                        }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = "Chọn đến ngày")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("leave_range_end_input")
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LeaveContract.LEAVE_TYPES.forEach { typeCode ->
                                    FilterChip(
                                        selected = rangeLeaveType == typeCode,
                                        onClick = { rangeLeaveType = typeCode },
                                        label = { Text(LeaveContract.leaveTypeShortLabel(typeCode)) }
                                    )
                                }
                            }

                            FilterChip(
                                selected = rangeSkipSundays,
                                onClick = { rangeSkipSundays = !rangeSkipSundays },
                                label = { Text("Bỏ qua ngày Chủ nhật trong dải ngày") }
                            )

                            if (previewErr != null) {
                                Text(
                                    text = previewErr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "Sẽ tạo ${previewDays.size} ngày nghỉ (${LeaveContract.formatHalfDayUnits(previewDays.size * 2)}): từ ${LeaveContract.formatIsoDateVi(rangeStartIso)} đến ${LeaveContract.formatIsoDateVi(rangeEndIso)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SemanticColors.SuccessLight
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                TextButton(onClick = { showDateRangeDialog = false }) {
                                    Text("Đóng")
                                }
                                Button(
                                    onClick = {
                                        if (onApplyDateRange(rangeStartIso, rangeEndIso, rangeLeaveType, rangeSkipSundays)) {
                                            showDateRangeDialog = false
                                        }
                                    },
                                    enabled = previewErr == null && previewDays.isNotEmpty(),
                                    modifier = Modifier.testTag("leave_range_apply_button")
                                ) {
                                    Text("Áp dụng ${previewDays.size} ngày")
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = reason,
                onValueChange = onUpdateReason,
                label = { Text("Lý do xin nghỉ (không bắt buộc)") },
                placeholder = { Text("Nhập lý do hoặc ghi chú đơn xin nghỉ (nếu có)...") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leave_reason_input")
            )

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_mutation_error_banner")
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("leave_submit_request_button")
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang gửi đơn xin nghỉ...")
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gửi đơn xin nghỉ phép")
                }
            }
        }
    }
}

@Composable
private fun LeaveMyRequestHistoryCard(
    actorAppUserId: String,
    requests: List<LeaveRequestProjection>,
    busy: Boolean,
    onRequestWithdraw: (LeaveRequestProjection) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leave_my_requests_section"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lịch sử đơn xin nghỉ của tôi (${requests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (requests.isEmpty()) {
                Text(
                    text = "Chưa có đơn xin nghỉ phép nào trong năm nghiệp vụ này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                requests.forEach { req ->
                    val statusColor = leaveStatusColor(req.status)
                    val canWithdraw = LeaveContract.canWithdrawOwnLeaveRequest(actorAppUserId, req)
                    val breakdown = LeaveContract.resolveRequestTypeBreakdown(req)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leave_my_request_card_${req.requestId}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statusColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = LeaveContract.leaveRequestStatusLabel(req.status),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    breakdown.forEach { tb ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "${LeaveContract.leaveTypeShortLabel(tb.leaveType)}: ${LeaveContract.formatHalfDayUnits(tb.halfDayUnits)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = LeaveContract.formatHalfDayUnits(req.requestedUnits),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val daysSummary = req.days.joinToString(", ") { d ->
                                "${LeaveContract.formatIsoDateVi(d.leaveDate)} (${LeaveContract.leaveSessionShortLabel(d.sessionCode)} · ${LeaveContract.leaveTypeShortLabel(d.leaveType)})"
                            }
                            Text(
                                text = "Ngày nghỉ: $daysSummary",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Lý do: ${req.reason.ifBlank { "Không ghi lý do" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Luồng duyệt: ${LeaveContract.leaveRouteKindLabel(req.routeKind, req.routedTeamName, req.unitKindSnapshot)} • Gửi lúc: ${formatIsoToVietnamDateTime(req.submittedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!req.decisionReason.isNullOrBlank()) {
                                Text(
                                    text = "Ghi chú người duyệt: ${req.decisionReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                            }
                            if (!req.cancellationReason.isNullOrBlank()) {
                                Text(
                                    text = "Lý do hủy/rút: ${req.cancellationReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canWithdraw) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onRequestWithdraw(req) },
                                        enabled = !busy,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("leave_withdraw_btn_${req.requestId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rút đơn")
                                    }
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
private fun LeaveEntitlementLotsCard(
    summary: LeaveMeSummaryProjection
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leave_lots_section"),
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
            Text(
                text = "Chi tiết các khoản phép năm ${summary.leaveYear}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            summary.balance.lots.forEach { lot ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = LeaveContract.leaveLotKindLabel(lot.lotKind, lot.accrualMonth),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Hiệu lực từ ${LeaveContract.formatIsoDateVi(lot.usableFromDate)}" +
                                    (lot.expiresAfterDate?.let { " • Hết hạn sau ${LeaveContract.formatIsoDateVi(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Còn: ${LeaveContract.formatHalfDayUnits(lot.availableUnits)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = SemanticColors.SuccessLight
                            )
                            Text(
                                text = "Cấp ${LeaveContract.formatHalfDayUnits(lot.grantedUnits, false)} • Dùng ${LeaveContract.formatHalfDayUnits(lot.consumedUnits, false)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PendingApproverDecisionDialogState(
    val request: LeaveRequestProjection,
    val decision: String
)

@Composable
private fun LeaveApprovalTabContent(
    actor: AppActor,
    viewModel: MiniBiViewModel
) {
    val myLeaveSummary by viewModel.myLeaveSummary.collectAsStateWithLifecycle()
    val statusFilter by viewModel.leaveInboxStatusFilter.collectAsStateWithLifecycle()
    val inbox by viewModel.leaveApprovalInbox.collectAsStateWithLifecycle()
    val inboxLoading by viewModel.leaveInboxLoading.collectAsStateWithLifecycle()
    val inboxError by viewModel.leaveInboxError.collectAsStateWithLifecycle()
    val mutationBusy by viewModel.leaveMutationBusy.collectAsStateWithLifecycle()
    val mutationError by viewModel.leaveMutationError.collectAsStateWithLifecycle()

    val authority = myLeaveSummary?.authority
    if (authority == null || !authority.canApproveRequests || authority.isSharedNonEmployee) {
        return
    }

    var expandedRequestIds by remember { mutableStateOf(setOf<String>()) }
    var decisionDialogState by remember { mutableStateOf<PendingApproverDecisionDialogState?>(null) }
    var decisionReasonInput by remember { mutableStateOf("") }
    var localDialogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusFilter) {
        if (inbox == null && !inboxLoading) {
            viewModel.refreshLeaveApprovalInbox(statusFilter = statusFilter)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("leave_approval_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PHÊ DUYỆT NGHỈ PHÉP THEO PHẠM VI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Danh sách đơn nghỉ phép (${inbox?.total ?: 0})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshLeaveApprovalInbox() },
                            enabled = !inboxLoading,
                            modifier = Modifier.testTag("leave_inbox_refresh_button")
                        ) {
                            if (inboxLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Làm mới hộp thư duyệt nghỉ"
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("leave_inbox_filter_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LeaveContract.REQUEST_STATUS_FILTERS.forEach { code ->
                            FilterChip(
                                selected = statusFilter == code,
                                onClick = { viewModel.setLeaveInboxFilter(code) },
                                label = { Text(LeaveContract.leaveRequestStatusLabel(code)) },
                                modifier = Modifier.testTag("leave_inbox_filter_$code")
                            )
                        }
                    }
                }
            }
        }

        if (!mutationError.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_mutation_error_banner")
                ) {
                    Text(
                        text = mutationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (!inboxError.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_inbox_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = inboxError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.refreshLeaveApprovalInbox() },
                            modifier = Modifier.testTag("leave_inbox_retry_button")
                        ) {
                            Text("Thử lại")
                        }
                    }
                }
            }
        }

        val items = inbox?.items.orEmpty()
        if (inboxLoading && items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Đang tải danh sách đơn cần duyệt...")
                    }
                }
            }
        } else if (items.isEmpty()) {
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Không có đơn nghỉ phép nào trong bộ lọc hiện tại",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        } else {
            itemsIndexed(items, key = { _, req -> req.requestId }) { _, req ->
                val isExpanded = req.requestId in expandedRequestIds
                val isOwnRequest = req.appUserId.equals(actor.appUserId, ignoreCase = true)
                val statusColor = leaveStatusColor(req.status)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("leave_inbox_item_card_${req.requestId}"),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.requester?.displayName?.ifBlank { req.appUserId } ?: req.appUserId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val subMeta = buildList {
                                    req.requester?.personnelCode?.takeIf { it.isNotBlank() }?.let { add("Mã NV: $it") }
                                    add(LeaveContract.leaveEmploymentStatusLabel(req.employmentStatusSnapshot))
                                    add(LeaveContract.leaveUnitKindLabel(req.unitKindSnapshot))
                                }.joinToString(" • ")
                                Text(
                                    text = subMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = LeaveContract.leaveRequestStatusLabel(req.status),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = LeaveContract.formatHalfDayUnits(req.requestedUnits),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val breakdown = LeaveContract.resolveRequestTypeBreakdown(req)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            breakdown.forEach { tb ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                ) {
                                    Text(
                                        text = "${LeaveContract.leaveTypeShortLabel(tb.leaveType)}: ${LeaveContract.formatHalfDayUnits(tb.halfDayUnits)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val daysText = req.days.joinToString(", ") { d ->
                            "${LeaveContract.formatIsoDateVi(d.leaveDate)} (${LeaveContract.leaveSessionShortLabel(d.sessionCode)} · ${LeaveContract.leaveTypeShortLabel(d.leaveType)})"
                        }
                        Text(
                            text = "Ngày nghỉ: $daysText",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lý do xin nghỉ: ${req.reason.ifBlank { "Không ghi lý do" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Định tuyến: ${LeaveContract.leaveRouteKindLabel(req.routeKind, req.routedTeamName, req.unitKindSnapshot)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = {
                                expandedRequestIds = if (isExpanded) {
                                    expandedRequestIds - req.requestId
                                } else {
                                    expandedRequestIds + req.requestId
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.testTag("leave_inbox_detail_toggle_${req.requestId}")
                        ) {
                            Text(if (isExpanded) "Thu gọn chi tiết" else "Xem chi tiết đơn")
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("leave_inbox_detail_panel_${req.requestId}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Mã đơn: #${req.requestId.take(8)} • Phiên bản: v${req.rowVersion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMono
                                    )
                                    Text(
                                        text = "Thời điểm gửi: ${formatIsoToVietnamDateTime(req.submittedAt)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    LeaveContract.leaveFallbackReasonLabel(req.fallbackReason)?.let { fb ->
                                        Text(
                                            text = "Lý do chuyển dự phòng: $fb",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    req.decidedBy?.let { decider ->
                                        Text(
                                            text = "Người xử lý: ${decider.displayName} (${req.decidedAt?.let { formatIsoToVietnamDateTime(it) } ?: "--"})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!req.decisionReason.isNullOrBlank()) {
                                        Text(
                                            text = "Lý do quyết định: ${req.decisionReason}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (!req.cancellationReason.isNullOrBlank()) {
                                        Text(
                                            text = "Lý do hủy/rút: ${req.cancellationReason}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        if (isOwnRequest) {
                            Text(
                                text = "Đơn của chính bạn (không tự duyệt hoặc tự hủy trên hộp thư phê duyệt)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("leave_self_approval_notice_${req.requestId}")
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (req.status.equals("PENDING", ignoreCase = true)) {
                                    OutlinedButton(
                                        onClick = {
                                            decisionReasonInput = ""
                                            localDialogError = null
                                            viewModel.clearLeaveMutationError()
                                            decisionDialogState = PendingApproverDecisionDialogState(
                                                request = req,
                                                decision = "REJECT"
                                            )
                                        },
                                        enabled = !mutationBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("leave_reject_btn_${req.requestId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Từ chối", color = MaterialTheme.colorScheme.error)
                                    }
                                    Button(
                                        onClick = {
                                            decisionReasonInput = ""
                                            localDialogError = null
                                            viewModel.clearLeaveMutationError()
                                            decisionDialogState = PendingApproverDecisionDialogState(
                                                request = req,
                                                decision = "APPROVE"
                                            )
                                        },
                                        enabled = !mutationBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SemanticColors.SuccessLight
                                        ),
                                        modifier = Modifier.testTag("leave_approve_btn_${req.requestId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Duyệt đơn", color = Color.White)
                                    }
                                } else if (req.status.equals("APPROVED", ignoreCase = true)) {
                                    OutlinedButton(
                                        onClick = {
                                            decisionReasonInput = ""
                                            localDialogError = null
                                            viewModel.clearLeaveMutationError()
                                            decisionDialogState = PendingApproverDecisionDialogState(
                                                request = req,
                                                decision = "CANCEL_APPROVED"
                                            )
                                        },
                                        enabled = !mutationBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("leave_cancel_approved_btn_${req.requestId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hủy đã duyệt (Hoàn phép)", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeDecisionState = decisionDialogState
    if (activeDecisionState != null) {
        val req = activeDecisionState.request
        val decision = activeDecisionState.decision
        val breakdownText = LeaveContract.resolveRequestTypeBreakdown(req).joinToString(" • ") {
            "${LeaveContract.leaveTypeShortLabel(it.leaveType)}: ${LeaveContract.formatHalfDayUnits(it.halfDayUnits)}"
        }

        Dialog(
            onDismissRequest = {
                if (!mutationBusy) {
                    decisionDialogState = null
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .testTag("leave_decision_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = LeaveContract.leaveDecisionActionLabel(decision),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Nhân sự: ${req.requester?.displayName ?: req.appUserId} • Số ngày: ${LeaveContract.formatHalfDayUnits(req.requestedUnits)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (breakdownText.isNotBlank()) {
                        Text(
                            text = "Loại nghỉ: $breakdownText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = decisionReasonInput,
                        onValueChange = {
                            decisionReasonInput = it
                            localDialogError = null
                        },
                        label = {
                            Text("Lý do / Ghi chú phản hồi (không bắt buộc)")
                        },
                        placeholder = {
                            Text("Nhập ghi chú phản hồi (nếu có)...")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leave_decision_reason_input")
                    )
                    val effectiveErr = localDialogError ?: mutationError
                    if (!effectiveErr.isNullOrBlank()) {
                        Text(
                            text = effectiveErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("leave_decision_dialog_error")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                decisionDialogState = null
                                localDialogError = null
                            },
                            enabled = !mutationBusy,
                            modifier = Modifier.testTag("leave_decision_cancel_button")
                        ) {
                            Text("Đóng")
                        }
                        Button(
                            onClick = {
                                val (valid, validationMsg) = LeaveContract.validateApproverDecisionInput(
                                    actorAppUserId = actor.appUserId,
                                    request = req,
                                    decision = decision,
                                    reason = decisionReasonInput
                                )
                                if (!valid) {
                                    localDialogError = validationMsg
                                    return@Button
                                }
                                viewModel.decideLeaveRequest(
                                    request = req,
                                    decision = decision,
                                    reason = decisionReasonInput.trim().ifEmpty { null },
                                    onSuccess = {
                                        decisionDialogState = null
                                        decisionReasonInput = ""
                                        localDialogError = null
                                    }
                                )
                            },
                            enabled = !mutationBusy,
                            modifier = Modifier.testTag("leave_decision_confirm_button")
                        ) {
                            Text(
                                when {
                                    mutationBusy -> "Đang xử lý..."
                                    decision == "APPROVE" -> "Xác nhận duyệt"
                                    decision == "REJECT" -> "Xác nhận từ chối"
                                    else -> "Xác nhận hủy duyệt"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun leaveStatusColor(status: String): Color =
    when (status.trim().uppercase(Locale.ROOT)) {
        "APPROVED" -> SemanticColors.SuccessLight
        "PENDING" -> SemanticColors.WarningLight
        "REJECTED", "CANCELLED" -> SemanticColors.ErrorLight
        else -> Color(0xFF64748B)
    }

private fun openIsoDatePicker(
    context: Context,
    initialIsoDate: String,
    onDatePicked: (String) -> Unit
) {
    val parts = initialIsoDate.trim().split("-")
    val cal = Calendar.getInstance()
    val year = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
    val monthZeroBased = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
    val dayOfMonth = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, pickedYear, pickedMonthZeroBased, pickedDay ->
            val formatted = "%04d-%02d-%02d".format(
                Locale.US,
                pickedYear,
                pickedMonthZeroBased + 1,
                pickedDay
            )
            onDatePicked(formatted)
        },
        year,
        monthZeroBased.coerceIn(0, 11),
        dayOfMonth.coerceIn(1, 31)
    ).show()
}
