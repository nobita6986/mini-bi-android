package com.example.ui.screens

import android.Manifest
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AppActor
import com.example.data.AttendanceContract
import com.example.data.AttendanceEntity
import com.example.data.AttendanceOutboxStatus
import com.example.data.AttendanceTimeSource
import com.example.data.CapturedAttendanceTime
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

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

    // 1. Refresh server time anchor whenever Attendance screen is opened, and fetch GPS if granted
    LaunchedEffect(actor.appUserId) {
        viewModel.syncAttendanceTimeAnchor(actor = actor, force = true)
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("attendance_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                        Text(
                            text = actor.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            timeAnchorState.hasValidAnchor ->
                                                "Đối chiếu thời gian đơn điệu từ máy chủ (hiệu lực đến ${timeAnchorState.expiresAtIso?.take(16)?.replace('T', ' ') ?: "--"})"
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
                                                "Xác nhận gần nhất lúc ${displayIso.drop(11).take(8)}"
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("primary_checkin_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (capturedPhotoFile == null)
                                "BẤM CHẤM CÔNG (Lấy GPS & Chụp Ảnh)"
                            else
                                "Chụp lại Ảnh Chấm Công",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
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
                                    .height(52.dp)
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
                    }
                )
            }
        }
    }
}

@Composable
private fun AttendanceHistoryCard(
    record: AttendanceEntity,
    serverSequenceInDay: Int?,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val effectiveTimeSource = record.serverTimeSource ?: record.timeSource
    val isDeviceClockUnverified = effectiveTimeSource == AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED

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
            "Từ chối quyền"
        else ->
            "Bị từ chối"
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
                        Text(
                            text = "%.5f, %.5f (±%.1fm)".format(
                                Locale.US,
                                record.latitude,
                                record.longitude,
                                record.accuracyMeters
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = record.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            maxLines = 2,
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
                        text = "Chi tiết lượt chấm công (${record.syncStatus}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isDeviceClockUnverified) {
                            "• Nguồn thời gian: ${AttendanceContract.MSG_DEVICE_TIME_UNVERIFIED}"
                        } else {
                            "• Nguồn thời gian: Giờ chuẩn máy chủ xác thực (SERVER_ANCHORED)"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Thời gian chụp khai báo: ${record.clientCapturedAtIso}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (record.serverCheckinId != null) {
                        Text(
                            text = "• Ngày nghiệp vụ máy chủ: ${record.serverBusinessDate ?: "--"} (Lượt #${record.serverSequenceInDay ?: "-"} / Tổng ${record.serverDailyCount ?: "-"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Giờ máy chủ ghi nhận: ${record.serverCapturedAtIso ?: "--"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Giờ máy chủ tiếp nhận: ${record.serverReceivedAtIso ?: "--"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (record.serverReplayed == true) {
                            Text(
                                text = "• Chống trùng: Phản hồi phát lại an toàn (replayed)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        text = "• Địa điểm gợi ý: ${record.addressHint}",
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
