package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.CccdDecodedQrPayload
import com.example.data.CccdQrDecoder
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeCameraFrame(
    imageProxy: ImageProxy,
    mlKitScanner: BarcodeScanner?,
    resolvedOnce: AtomicBoolean,
    frameCounter: AtomicInteger,
    mainExecutor: Executor,
    onDecoded: (CccdDecodedQrPayload) -> Unit
) {
    if (resolvedOnce.get()) {
        imageProxy.close()
        return
    }

    val frameIndex = frameCounter.incrementAndGet()
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

    // Prepare an upscaled center viewfinder crop on every 2nd frame for tiny CCCD QR codes
    val centerCropBitmap = if (frameIndex % 2 == 0) {
        runCatching {
            val rawBmp = imageProxy.toBitmap()
            val rotated = CccdQrDecoder.rotateBitmap(rawBmp, rotationDegrees.toFloat()) ?: rawBmp
            val cropped = CccdQrDecoder.cropAndScale(rotated, 0.16f, 0.16f, 0.68f, 0.68f, 1000)
            if (rotated !== rawBmp && rotated !== cropped) rotated.recycle()
            if (rawBmp !== cropped) rawBmp.recycle()
            cropped
        }.getOrNull()
    } else {
        null
    }

    val mediaImage = imageProxy.image
    if (mlKitScanner != null && mediaImage != null) {
        val fullInput = runCatching { InputImage.fromMediaImage(mediaImage, rotationDegrees) }.getOrNull()
        if (fullInput != null) {
            mlKitScanner.process(fullInput)
                .addOnSuccessListener { barcodes ->
                    if (!resolvedOnce.get()) {
                        for (barcode in barcodes) {
                            val decoded = CccdQrDecoder.parseFromMlKitBarcode(barcode)
                            if (decoded != null && resolvedOnce.compareAndSet(false, true)) {
                                mainExecutor.execute { onDecoded(decoded) }
                                break
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    try {
                        imageProxy.close()
                    } catch (_: Exception) {
                    }
                    if (centerCropBitmap != null) {
                        if (!resolvedOnce.get()) {
                            val cropInput = runCatching { InputImage.fromBitmap(centerCropBitmap, 0) }.getOrNull()
                            if (cropInput != null) {
                                mlKitScanner.process(cropInput)
                                    .addOnSuccessListener { cropBarcodes ->
                                        if (!resolvedOnce.get()) {
                                            for (barcode in cropBarcodes) {
                                                val decoded = CccdQrDecoder.parseFromMlKitBarcode(barcode)
                                                if (decoded != null && resolvedOnce.compareAndSet(false, true)) {
                                                    mainExecutor.execute { onDecoded(decoded) }
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        if (!resolvedOnce.get()) {
                                            runCatching {
                                                val zxingDecoded = CccdQrDecoder.decodeFromBitmap(centerCropBitmap)
                                                if (zxingDecoded != null && resolvedOnce.compareAndSet(false, true)) {
                                                    mainExecutor.execute { onDecoded(zxingDecoded) }
                                                }
                                            }
                                        }
                                        runCatching { centerCropBitmap.recycle() }
                                    }
                            } else {
                                runCatching { centerCropBitmap.recycle() }
                            }
                        } else {
                            runCatching { centerCropBitmap.recycle() }
                        }
                    }
                }
            return
        }
    }

    // Fallback when ML Kit MediaImage path is unavailable
    try {
        if (!resolvedOnce.get()) {
            val targetBmp = centerCropBitmap ?: runCatching { imageProxy.toBitmap() }.getOrNull()
            if (targetBmp != null) {
                try {
                    val decoded = CccdQrDecoder.decodeFromBitmap(targetBmp)
                    if (decoded != null && resolvedOnce.compareAndSet(false, true)) {
                        mainExecutor.execute { onDecoded(decoded) }
                    }
                } finally {
                    if (targetBmp !== centerCropBitmap) {
                        runCatching { targetBmp.recycle() }
                    }
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        runCatching { centerCropBitmap?.recycle() }
        runCatching { imageProxy.close() }
    }
}

@Composable
fun CccdQrScannerDialog(
    onDismiss: () -> Unit,
    onDecoded: (CccdDecodedQrPayload) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var statusMessage by remember {
        mutableStateOf("Đưa mã QR trên thẻ CCCD/Căn cước vào khung xanh (giữ cách 12–18 cm để lấy nét rõ) hoặc chọn ảnh từ Album.")
    }
    var isError by remember { mutableStateOf(false) }
    var decodingFromGallery by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var selectedZoomRatio by remember { mutableFloatStateOf(1.5f) }
    var torchEnabled by remember { mutableStateOf(false) }
    val resolvedOnce = remember { AtomicBoolean(false) }
    val frameCounter = remember { AtomicInteger(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            isError = true
            statusMessage = "Chưa cấp quyền Máy ảnh. Bạn có thể bấm 'Chọn ảnh có QR từ Album' bên dưới."
        }
    }

    val galleryQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            decodingFromGallery = true
            isError = false
            statusMessage = "Đang phân tích mã QR từ ảnh đã chọn…"
            scope.launch {
                val decoded = CccdQrDecoder.decodeFromUri(context, uri)
                decodingFromGallery = false
                if (decoded != null) {
                    if (resolvedOnce.compareAndSet(false, true)) {
                        onDecoded(decoded)
                    }
                } else {
                    isError = true
                    statusMessage = "Không tìm thấy mã QR CCCD hợp lệ trong ảnh đã chọn. Hãy chọn ảnh rõ nét hơn hoặc quét trực tiếp."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            runCatching { permissionLauncher.launch(Manifest.permission.CAMERA) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("cccd_qr_scanner_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Quét mã QR CCCD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tự động điền Số CCCD, Họ tên, Ngày sinh, Giới tính, Địa chỉ, Ngày cấp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_cccd_qr_scanner_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                if (hasCameraPermission) {
                    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                    val mlKitScanner = remember { CccdQrDecoder.createMlKitScannerOrNull() }
                    val previewView = remember {
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }

                    DisposableEffect(lifecycleOwner, hasCameraPermission) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val mainExecutor = ContextCompat.getMainExecutor(context)
                        val listener = Runnable {
                            runCatching {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val resolutionSelector = ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            Size(1920, 1080),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .build()
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(resolutionSelector)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    analyzeCameraFrame(
                                        imageProxy = imageProxy,
                                        mlKitScanner = mlKitScanner,
                                        resolvedOnce = resolvedOnce,
                                        frameCounter = frameCounter,
                                        mainExecutor = mainExecutor,
                                        onDecoded = onDecoded
                                    )
                                }

                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                boundCamera = camera

                                // Apply 1.5x initial zoom so user can hold CCCD at optimal 12-18cm focus distance
                                runCatching {
                                    val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 2.0f
                                    val targetZoom = selectedZoomRatio.coerceIn(1.0f, maxZoom)
                                    camera.cameraControl.setZoomRatio(targetZoom)
                                }

                                // Continuous center auto-focus on the QR viewfinder box
                                runCatching {
                                    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                                    val centerPoint = factory.createPoint(0.5f, 0.5f)
                                    val focusAction = FocusMeteringAction.Builder(
                                        centerPoint,
                                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                    ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                    camera.cameraControl.startFocusAndMetering(focusAction)
                                }
                            }.onFailure {
                                isError = true
                                statusMessage = "Không thể khởi tạo camera trên thiết bị. Vui lòng chọn ảnh có QR từ Album."
                            }
                        }
                        cameraProviderFuture.addListener(listener, mainExecutor)

                        onDispose {
                            boundCamera = null
                            runCatching {
                                if (cameraProviderFuture.isDone) {
                                    cameraProviderFuture.get().unbindAll()
                                }
                            }
                            runCatching { analysisExecutor.shutdownNow() }
                            runCatching { mlKitScanner?.close() }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(275.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                // Tap to re-trigger center focus on the QR box
                                boundCamera?.let { cam ->
                                    runCatching {
                                        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                                        val centerPoint = factory.createPoint(0.5f, 0.5f)
                                        val focusAction = FocusMeteringAction.Builder(
                                            centerPoint,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        cam.cameraControl.startFocusAndMetering(focusAction)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .border(
                                    width = 2.5.dp,
                                    color = Color(0xFF22C55E),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )

                        // Bottom overlay bar for quick zoom (1x / 1.5x / 2x) and Flash toggle
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1.0f to "1x", 1.5f to "1.5x", 2.0f to "2x").forEach { (ratio, label) ->
                                    val selected = kotlin.math.abs(selectedZoomRatio - ratio) < 0.1f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) {
                                            Color(0xFF22C55E)
                                        } else {
                                            Color.Black.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.clickable {
                                            selectedZoomRatio = ratio
                                            boundCamera?.let { cam ->
                                                runCatching {
                                                    val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 2.0f
                                                    cam.cameraControl.setZoomRatio(ratio.coerceIn(1.0f, maxZoom))
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (torchEnabled) Color(0xFFF59E0B) else Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable {
                                        val next = !torchEnabled
                                        torchEnabled = next
                                        runCatching { boundCamera?.cameraControl?.enableTorch(next) }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                            contentDescription = "Đèn trợ sáng",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (torchEnabled) "Tắt đèn" else "Bật đèn",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Cần cấp quyền Máy ảnh để quét mã QR trực tiếp.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cấp quyền Máy ảnh")
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (decodingFromGallery) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { galleryQrLauncher.launch("image/*") },
                        enabled = !decodingFromGallery,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("pick_qr_image_from_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chọn ảnh có QR từ Album", style = MaterialTheme.typography.labelMedium)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
}
