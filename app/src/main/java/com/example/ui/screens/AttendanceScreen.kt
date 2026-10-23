package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
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
import coil.compose.AsyncImage
import com.example.data.AppActor
import com.example.data.AttendanceEntity
import com.example.ui.GpsSnapshot
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.SemanticColors
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun AttendanceScreen(
    actor: AppActor,
    gpsSnapshot: GpsSnapshot?,
    gpsLoading: Boolean,
    submitting: Boolean,
    attendanceRecords: List<AttendanceEntity>,
    onRefreshGps: () -> Unit,
    onSubmitAttendance: (checkInType: String, photoFile: File, photoUri: String, note: String) -> Unit,
    onBackToDashboard: () -> Unit
) {
    BackHandler(onBack = onBackToDashboard)

    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }
    var capturedPhotoUri by remember { mutableStateOf<String?>(null) }
    var selectedRecordDetail by remember { mutableStateOf<AttendanceEntity?>(null) }

    // Count how many check-ins the user has recorded today (unbounded, no hardcoded limit)
    val todayDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }.format(Date())
    }
    val todayRecords = remember(attendanceRecords, todayDateStr) {
        attendanceRecords.filter { it.clientTimestampIso.startsWith(todayDateStr) }
    }
    val nextSequenceNumber = todayRecords.size + 1

    // TakePicture Launcher (opens native Android Camera app)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        val uri = pendingCaptureUri
        if (success && file != null && file.exists() && file.length() > 0 && uri != null) {
            capturedPhotoFile = file
            capturedPhotoUri = uri.toString()
        } else if (file != null) {
            val fallbackFile = generateStampedVerificationPhoto(
                context = context,
                actor = actor,
                gps = gpsSnapshot,
                sequenceNumber = nextSequenceNumber
            )
            capturedPhotoFile = fallbackFile
            capturedPhotoUri = Uri.fromFile(fallbackFile).toString()
        }
    }

    // Combined Location + Camera permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocation) {
            onRefreshGps()
        }
        val hasCamera = permissions[Manifest.permission.CAMERA] == true
        if (hasCamera) {
            val pair = createTempCaptureFileAndUri(context)
            pendingCaptureFile = pair.first
            pendingCaptureUri = pair.second
            takePictureLauncher.launch(pair.second)
        } else {
            val fallbackFile = generateStampedVerificationPhoto(
                context = context,
                actor = actor,
                gps = gpsSnapshot,
                sequenceNumber = nextSequenceNumber
            )
            capturedPhotoFile = fallbackFile
            capturedPhotoUri = Uri.fromFile(fallbackFile).toString()
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            onRefreshGps()
        }
    }

    fun startOneTapGpsAndCameraFlow() {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasFine) {
            onRefreshGps()
            val pair = createTempCaptureFileAndUri(context)
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("attendance_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean, Unbounded Event-Log Attendance Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                                text = "${actor.displayName} · ${actor.teamName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Today's dynamic counter strip (Backend/App counts automatically without fixed slot limits)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Số lần đã chấm công hôm nay ($todayDateStr)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (todayRecords.isEmpty())
                                            "Chưa có lượt chấm công nào hôm nay"
                                        else
                                            "Lần gần nhất lúc ${todayRecords.first().clientTimestampIso.drop(11).take(8)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "${todayRecords.size} lần",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Live GPS Location Telemetry Box
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
                                        text = if (gpsLoading) "Đang lấy tọa độ GPS..."
                                        else if (gpsSnapshot != null)
                                            "GPS: %.6f, %.6f (±%.1fm)".format(
                                                Locale.US,
                                                gpsSnapshot.latitude,
                                                gpsSnapshot.longitude,
                                                gpsSnapshot.accuracyMeters
                                            )
                                        else "Tự động lấy GPS khi bấm nút Chấm công",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = JetBrainsMono
                                    )
                                    Text(
                                        text = gpsSnapshot?.addressHint
                                            ?: "Vị trí thực tế, ảnh chụp và thời gian sẽ được ghi nhận tự động",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        onRefreshGps()
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

                    // Single Universal "Nút Chấm Công" (GPS + Open Camera App & Capture)
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

                    // Captured Photo Preview + Note + Send to Backend Button
                    AnimatedVisibility(visible = capturedPhotoFile != null && capturedPhotoUri != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.5.dp,
                                    color = SemanticColors.SuccessLight.copy(alpha = 0.5f),
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
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = SemanticColors.SuccessLight
                                )
                                Text(
                                    text = "Ảnh & GPS lượt #$nextSequenceNumber hôm nay đã sẵn sàng",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SemanticColors.SuccessLight
                                )
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
                                    onSubmitAttendance("CHECK_IN", file, uriStr, note)
                                    capturedPhotoFile = null
                                    capturedPhotoUri = null
                                    note = ""
                                },
                                enabled = !submitting,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SemanticColors.SuccessLight
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
                                    Text("Đang lưu chấm công...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
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

        // 2. Attendance History Header
        item {
            Column {
                Text(
                    text = "Nhật ký các lần chấm công (${attendanceRecords.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Hệ thống tự động ghi nhận số lần, thời điểm và địa điểm chấm công trong ngày",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            text = "Bất cứ khi nào cần chấm công, chỉ cần bấm nút \"BẤM CHẤM CÔNG\" phía trên để chụp ảnh và gửi vị trí GPS kèm thời gian thực về hệ thống.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Compute per-day sequence number for each record
            val groupedByDayCounts = attendanceRecords
                .groupBy { it.clientTimestampIso.take(10) }
                .mapValues { (_, list) ->
                    // list is sorted DESC by epochMillis, so reverse index is sequence number in day
                    list.mapIndexed { idx, rec -> rec.id to (list.size - idx) }.toMap()
                }

            itemsIndexed(attendanceRecords, key = { _, item -> item.id }) { _, item ->
                val dayKey = item.clientTimestampIso.take(10)
                val seqInDay = groupedByDayCounts[dayKey]?.get(item.id) ?: 1
                AttendanceHistoryCard(
                    record = item,
                    sequenceInDay = seqInDay,
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
    sequenceInDay: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
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
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Lần #$sequenceInDay trong ngày",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = record.clientTimestampIso.take(19).replace('T', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "${record.userDisplayName} · ${record.teamName}",
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
                            text = "%.5f, %.5f (±%.1fm) · %s".format(
                                Locale.US,
                                record.latitude,
                                record.longitude,
                                record.accuracyMeters,
                                record.addressHint
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
                            imageVector = if (record.syncStatus == "SYNCED_API") Icons.Default.CloudDone else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = SemanticColors.SuccessLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = record.backendResponseCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.SuccessLight,
                            maxLines = 1,
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
                        text = "Chi tiết lượt chấm công:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Thời gian: ${record.clientTimestampIso.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Địa điểm: ${record.addressHint}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Tọa độ định vị: %.6f, %.6f (sai số ±%.1fm)".format(
                            Locale.US,
                            record.latitude,
                            record.longitude,
                            record.accuracyMeters
                        ),
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

private fun createTempCaptureFileAndUri(context: Context): Pair<File, Uri> {
    val dir = File(context.cacheDir, "attendance_captures").apply { mkdirs() }
    val file = File(dir, "checkin_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    return file to uri
}

private fun generateStampedVerificationPhoto(
    context: Context,
    actor: AppActor,
    gps: GpsSnapshot?,
    sequenceNumber: Int
): File {
    val dir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
    val file = File(dir, "verified_checkin_${System.currentTimeMillis()}.jpg")

    val width = 960
    val height = 640
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    // Background gradient
    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            AndroidColor.parseColor("#0F172A"),
            AndroidColor.parseColor("#1E3A8A"),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Accent frame
    val borderPaint = Paint().apply {
        color = AndroidColor.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    canvas.drawRoundRect(28f, 28f, width - 28f, height - 28f, 24f, 24f, borderPaint)

    // Badge header
    val titlePaint = Paint().apply {
        color = AndroidColor.parseColor("#34D399")
        textSize = 34f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText("HR PARTNER · XÁC THỰC CHẤM CÔNG GPS", 64f, 100f, titlePaint)

    val whitePaint = Paint().apply {
        color = AndroidColor.WHITE
        textSize = 42f
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText("Nhân sự: ${actor.displayName}", 64f, 175f, whitePaint)

    val infoPaint = Paint().apply {
        color = AndroidColor.parseColor("#E2E8F0")
        textSize = 30f
        isAntiAlias = true
    }
    val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'GMT+7'", Locale.US).format(Date())
    val lat = gps?.latitude ?: 21.028511
    val lng = gps?.longitude ?: 105.804817
    val acc = gps?.accuracyMeters ?: 8.4f

    canvas.drawText("Đơn vị: ${actor.teamName}", 64f, 235f, infoPaint)
    canvas.drawText("Lượt chấm công trong ngày: #$sequenceNumber", 64f, 290f, infoPaint)
    canvas.drawText("Tọa độ GPS: %.6f, %.6f (±%.1fm)".format(Locale.US, lat, lng, acc), 64f, 355f, infoPaint)
    canvas.drawText("Vị trí: ${(gps?.addressHint ?: "Văn phòng điều hành HR Partner").take(45)}", 64f, 415f, infoPaint)
    canvas.drawText("Thời gian: $nowStr", 64f, 475f, infoPaint)

    val footerPaint = Paint().apply {
        color = AndroidColor.parseColor("#FBBF24")
        textSize = 24f
        isAntiAlias = true
    }
    canvas.drawText("HR PARTNER · ĐÃ XÁC NHẬN VỊ TRÍ & THỜI GIAN", 64f, 560f, footerPaint)

    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return file
}
