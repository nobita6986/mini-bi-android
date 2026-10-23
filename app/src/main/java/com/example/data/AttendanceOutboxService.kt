package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AttendanceCheckInServerResult(
    val serverCheckinId: String?,
    val serverSequenceInDay: Int?,
    val serverDailyCount: Int?
)

data class AttendanceFailureClassification(
    val syncStatus: String,
    val shouldAutoRetry: Boolean,
    val statusMessage: String,
    val httpStatusCode: Int?,
    val backendErrorCode: String?
)

data class AttendanceSubmissionOutcome(
    val record: AttendanceEntity,
    val isSynced: Boolean,
    val userMessage: String,
    val isError: Boolean
)

data class AttendanceOutboxFlushSummary(
    val processedCount: Int,
    val syncedCount: Int,
    val remainingRetryableCount: Int,
    val terminalFailureCount: Int
)

object AttendanceContract {
    const val ENDPOINT_CHECK_IN = "/api/attendance/check-in"
    const val UNIQUE_OUTBOX_WORK_NAME = "mini_bi_attendance_outbox_sync"

    const val MSG_SYNCED = "Đã chấm công"
    const val MSG_QUEUED_OFFLINE = "Đã lưu trên thiết bị, đang chờ đồng bộ"
    const val MSG_SYNCING = "Đang gửi chấm công lên máy chủ..."
    const val MSG_BACKEND_NOT_AVAILABLE = "Chức năng chấm công chưa được bật trên máy chủ"

    val ALLOWED_METADATA_KEYS = setOf(
        "latitude",
        "longitude",
        "accuracy_meters",
        "address_hint",
        "client_captured_at",
        "note",
        "idempotency_key",
        "photo_sha256"
    )

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )
    private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

    fun formatClientCapturedAtIso(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        return sdf.format(Date(epochMillis))
    }

    /**
     * Builds canonical metadata JSON for `POST /api/attendance/check-in`.
     * Never sends `checkInType`, client-invented `sequence_in_day`, or actor authority fields.
     */
    fun buildMetadataJson(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        addressHint: String,
        clientCapturedAtIso: String,
        note: String,
        idempotencyKey: String,
        photoSha256: String
    ): JSONObject {
        if (!latitude.isFinite() || latitude < -90.0 || latitude > 90.0 ||
            !longitude.isFinite() || longitude < -180.0 || longitude > 180.0
        ) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "GPS_COORDINATES_INVALID",
                message = "Tọa độ GPS không hợp lệ. Vui lòng cập nhật lại vị trí."
            )
        }
        if (!accuracyMeters.isFinite() || accuracyMeters < 0f) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "GPS_ACCURACY_INVALID",
                message = "Độ chính xác GPS không hợp lệ."
            )
        }
        val normalizedIdempotencyKey = idempotencyKey.trim()
        if (!UUID_REGEX.matches(normalizedIdempotencyKey)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "IDEMPOTENCY_KEY_INVALID",
                message = "Khóa chống trùng chấm công không đúng chuẩn UUID."
            )
        }
        val normalizedSha = photoSha256.trim().lowercase(Locale.ROOT)
        if (!SHA256_HEX_REGEX.matches(normalizedSha)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "PHOTO_HASH_INVALID",
                message = "Mã kiểm tra toàn vẹn ảnh chấm công không hợp lệ."
            )
        }
        val normalizedCapturedAt = clientCapturedAtIso.trim()
        if (normalizedCapturedAt.isBlank()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "CAPTURED_AT_MISSING",
                message = "Thiếu thời điểm chụp ảnh chấm công."
            )
        }

        return JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy_meters", accuracyMeters.toDouble())
            put("address_hint", addressHint.trim())
            put("client_captured_at", normalizedCapturedAt)
            put("note", note.trim())
            put("idempotency_key", normalizedIdempotencyKey)
            put("photo_sha256", normalizedSha)
        }
    }

    fun buildMultipartBody(
        metadataJson: JSONObject,
        photoFile: File,
        photoMimeType: String = "image/jpeg"
    ): MultipartBody {
        if (!photoFile.exists() || !photoFile.isFile || photoFile.length() <= 0L) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "PHOTO_FILE_MISSING",
                message = "Không đọc được tệp ảnh chấm công trên thiết bị."
            )
        }
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", metadataJson.toString())
            .addFormDataPart(
                "photo",
                photoFile.name.ifBlank { "checkin.jpg" },
                photoFile.asRequestBody(photoMimeType.toMediaType())
            )
            .build()
    }

    /**
     * Parses canonical 2xx response from `POST /api/attendance/check-in`.
     * Never fabricates `sequence_in_day` or `daily_count` if server omits them.
     */
    fun parseCheckInSuccessResponse(json: JSONObject): AttendanceCheckInServerResult {
        if (!json.optBoolean("ok", false)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = json.optString("code").ifBlank { "CONTRACT_INVALID" },
                message = "Máy chủ trả phản hồi chấm công thiếu cờ xác nhận hợp lệ."
            )
        }

        val container = json.optJSONObject("checkin")
            ?: json.optJSONObject("attendance")
            ?: json.optJSONObject("data")
            ?: json

        val serverId = listOf("checkin_id", "attendance_id", "id")
            .firstNotNullOfOrNull { key ->
                if (container.has(key) && !container.isNull(key)) {
                    container.optString(key, "").trim().ifBlank { null }
                } else {
                    null
                }
            }

        val seqInDay = if (container.has("sequence_in_day") && !container.isNull("sequence_in_day")) {
            val parsed = container.optInt("sequence_in_day", -1)
            parsed.takeIf { it > 0 }
        } else {
            null
        }

        val dailyCount = if (container.has("daily_count") && !container.isNull("daily_count")) {
            val parsed = container.optInt("daily_count", -1)
            parsed.takeIf { it > 0 }
        } else {
            null
        }

        return AttendanceCheckInServerResult(
            serverCheckinId = serverId,
            serverSequenceInDay = seqInDay,
            serverDailyCount = dailyCount
        )
    }

    /**
     * Strict failure classifier:
     * ONLY network, timeout, 429, and 5xx are retryable (`RETRYABLE_ERROR`).
     * 400, 401, 403, 404, 409, 422, and malformed contract NEVER auto-retry.
     */
    fun classifyFailure(error: BackendApiException): AttendanceFailureClassification {
        return when (error.kind) {
            BackendFailureKind.NETWORK -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.RETRYABLE_ERROR,
                shouldAutoRetry = true,
                statusMessage = MSG_QUEUED_OFFLINE,
                httpStatusCode = error.statusCode,
                backendErrorCode = error.backendCode ?: "NETWORK_ERROR"
            )
            BackendFailureKind.RATE_LIMITED,
            BackendFailureKind.SERVER_UNAVAILABLE -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.RETRYABLE_ERROR,
                shouldAutoRetry = true,
                statusMessage = "${error.message} (đang chờ tự động đồng bộ lại)",
                httpStatusCode = error.statusCode,
                backendErrorCode = error.backendCode
            )
            BackendFailureKind.UNAUTHENTICATED -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.AUTH_REQUIRED,
                shouldAutoRetry = false,
                statusMessage = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.",
                httpStatusCode = error.statusCode ?: 401,
                backendErrorCode = error.backendCode ?: "AUTH_UNAUTHENTICATED"
            )
            BackendFailureKind.FORBIDDEN -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.DENIED,
                shouldAutoRetry = false,
                statusMessage = error.message.ifBlank {
                    "Tài khoản không khả dụng hoặc không có quyền chấm công."
                },
                httpStatusCode = error.statusCode ?: 403,
                backendErrorCode = error.backendCode ?: "FORBIDDEN"
            )
            BackendFailureKind.NOT_FOUND -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE,
                shouldAutoRetry = false,
                statusMessage = MSG_BACKEND_NOT_AVAILABLE,
                httpStatusCode = error.statusCode ?: 404,
                backendErrorCode = error.backendCode ?: "NOT_FOUND"
            )
            BackendFailureKind.INVALID_REQUEST,
            BackendFailureKind.CONFLICT,
            BackendFailureKind.VALIDATION,
            BackendFailureKind.CONTRACT_INVALID -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.REJECTED,
                shouldAutoRetry = false,
                statusMessage = error.message.ifBlank {
                    "Yêu cầu chấm công bị máy chủ từ chối."
                },
                httpStatusCode = error.statusCode,
                backendErrorCode = error.backendCode ?: "REJECTED"
            )
        }
    }
}

class AttendanceOutboxService(
    private val context: Context,
    private val httpClient: BackendHttpClient,
    private val dao: MiniBiDao,
    private val isNetworkAvailable: () -> Boolean = { isDeviceNetworkConnected(context) },
    private val idempotencyKeyGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val outboxWorkScheduler: () -> Unit = { enqueueDurableOutboxWork(context) }
) {

    companion object {
        fun isDeviceNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun enqueueDurableOutboxWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<AttendanceSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    AttendanceContract.UNIQUE_OUTBOX_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    workRequest
                )
            }
        }
    }

    /**
     * Records a check-in attempt into the durable Room outbox and immediately attempts
     * backend synchronization if network is available.
     *
     * Strict guarantees:
     * - Fails closed BEFORE saving/sending if photo file cannot be read or SHA-256 fails.
     * - Never fabricates SHA-256 or `sequence_in_day`.
     * - Preserves fixed `idempotencyKey` across all future retries.
     * - Reports `"Đã chấm công"` ONLY when backend returns valid 2xx `{ ok: true }`.
     * - Reports `"Đã lưu trên thiết bị, đang chờ đồng bộ"` when queued offline.
     * - Reports `"Chức năng chấm công chưa được bật trên máy chủ"` on HTTP 404 without retrying.
     */
    suspend fun submitCheckIn(
        actor: AppActor,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        addressHint: String,
        sourcePhotoFile: File,
        sourcePhotoUri: String?,
        note: String
    ): AttendanceSubmissionOutcome = withContext(Dispatchers.IO) {
        val fixedIdempotencyKey = idempotencyKeyGenerator()
        val persistedFile = ensurePhotoPersistedInOutboxDir(sourcePhotoFile, fixedIdempotencyKey)

        // Strict SHA-256 verification: abort immediately if file read or hashing fails
        val photoSha256 = try {
            MiniBiRepository.computeSha256HexStrict(persistedFile)
        } catch (e: IOException) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "PHOTO_HASH_FAILED",
                message = "Không thể đọc và tính mã toàn vẹn SHA-256 của ảnh chấm công. Đã hủy gửi yêu cầu.",
                cause = e
            )
        }

        val capturedEpochMillis = nowMillis()
        val capturedAtIso = AttendanceContract.formatClientCapturedAtIso(capturedEpochMillis)

        // Pre-validate canonical metadata contract before persisting to outbox
        AttendanceContract.buildMetadataJson(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            addressHint = addressHint,
            clientCapturedAtIso = capturedAtIso,
            note = note,
            idempotencyKey = fixedIdempotencyKey,
            photoSha256 = photoSha256
        )

        val resolvedPhotoUri = resolvePersistedPhotoUri(persistedFile, sourcePhotoUri)
        val onlineNow = isNetworkAvailable()

        val initialEntity = AttendanceEntity(
            idempotencyKey = fixedIdempotencyKey,
            appUserId = actor.appUserId,
            userDisplayName = actor.displayName,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            addressHint = addressHint.trim(),
            photoFilePath = persistedFile.absolutePath,
            photoUri = resolvedPhotoUri,
            photoSha256 = photoSha256,
            clientCapturedAtIso = capturedAtIso,
            epochMillis = capturedEpochMillis,
            note = note.trim(),
            syncStatus = if (onlineNow) AttendanceOutboxStatus.SYNCING else AttendanceOutboxStatus.QUEUED,
            statusMessage = if (onlineNow) AttendanceContract.MSG_SYNCING else AttendanceContract.MSG_QUEUED_OFFLINE,
            backendErrorCode = null,
            httpStatusCode = null,
            serverCheckinId = null,
            serverSequenceInDay = null,
            serverDailyCount = null,
            retryCount = 0
        )

        val insertedId = dao.insertAttendance(initialEntity)
        val storedRecord = initialEntity.copy(id = insertedId)

        if (!onlineNow) {
            outboxWorkScheduler()
            return@withContext AttendanceSubmissionOutcome(
                record = storedRecord,
                isSynced = false,
                userMessage = AttendanceContract.MSG_QUEUED_OFFLINE,
                isError = false
            )
        }

        syncExistingRecord(storedRecord, scheduleWorkOnFailure = true)
    }

    suspend fun syncRecordById(
        recordId: Long,
        scheduleWorkOnFailure: Boolean = true
    ): AttendanceSubmissionOutcome? = withContext(Dispatchers.IO) {
        val current = dao.getAttendanceById(recordId) ?: return@withContext null
        if (current.syncStatus == AttendanceOutboxStatus.SYNCED) {
            return@withContext AttendanceSubmissionOutcome(
                record = current,
                isSynced = true,
                userMessage = AttendanceContract.MSG_SYNCED,
                isError = false
            )
        }
        syncExistingRecord(current, scheduleWorkOnFailure = scheduleWorkOnFailure)
    }

    /**
     * Retries only records in `QUEUED` or `RETRYABLE_ERROR`.
     * Records in `BACKEND_NOT_AVAILABLE` (404), `AUTH_REQUIRED` (401), `DENIED` (403),
     * or `REJECTED` (400/409/422) are never automatically retried.
     */
    suspend fun flushRetryableOutbox(
        appUserId: String? = null,
        scheduleWorkOnFailure: Boolean = true
    ): AttendanceOutboxFlushSummary = withContext(Dispatchers.IO) {
        val pending = if (!appUserId.isNullOrBlank()) {
            dao.getRetryableAttendanceOutboxForUser(appUserId)
        } else {
            dao.getRetryableAttendanceOutbox()
        }
        if (pending.isEmpty()) {
            return@withContext AttendanceOutboxFlushSummary(
                processedCount = 0,
                syncedCount = 0,
                remainingRetryableCount = 0,
                terminalFailureCount = 0
            )
        }

        if (!isNetworkAvailable()) {
            if (scheduleWorkOnFailure) {
                outboxWorkScheduler()
            }
            return@withContext AttendanceOutboxFlushSummary(
                processedCount = 0,
                syncedCount = 0,
                remainingRetryableCount = pending.size,
                terminalFailureCount = 0
            )
        }

        var syncedCount = 0
        var remainingRetryableCount = 0
        var terminalFailureCount = 0

        for (item in pending) {
            val outcome = syncExistingRecord(item, scheduleWorkOnFailure = false)
            when (outcome.record.syncStatus) {
                AttendanceOutboxStatus.SYNCED -> syncedCount++
                AttendanceOutboxStatus.QUEUED,
                AttendanceOutboxStatus.RETRYABLE_ERROR -> remainingRetryableCount++
                else -> terminalFailureCount++
            }
        }

        if (remainingRetryableCount > 0 && scheduleWorkOnFailure) {
            outboxWorkScheduler()
        }

        AttendanceOutboxFlushSummary(
            processedCount = pending.size,
            syncedCount = syncedCount,
            remainingRetryableCount = remainingRetryableCount,
            terminalFailureCount = terminalFailureCount
        )
    }

    private suspend fun syncExistingRecord(
        record: AttendanceEntity,
        scheduleWorkOnFailure: Boolean
    ): AttendanceSubmissionOutcome {
        val photoFile = File(record.photoFilePath)
        val verifiedSha = try {
            MiniBiRepository.computeSha256HexStrict(photoFile)
        } catch (_: IOException) {
            val rejected = record.copy(
                syncStatus = AttendanceOutboxStatus.REJECTED,
                statusMessage = "Tệp ảnh chấm công trên thiết bị không còn nguyên vẹn. Đã dừng gửi.",
                backendErrorCode = "PHOTO_HASH_FAILED",
                httpStatusCode = null
            )
            dao.updateAttendance(rejected)
            return AttendanceSubmissionOutcome(
                record = rejected,
                isSynced = false,
                userMessage = rejected.statusMessage,
                isError = true
            )
        }

        if (!verifiedSha.equals(record.photoSha256, ignoreCase = true)) {
            val rejected = record.copy(
                syncStatus = AttendanceOutboxStatus.REJECTED,
                statusMessage = "Ảnh chấm công không khớp mã toàn vẹn SHA-256 ban đầu. Đã dừng gửi.",
                backendErrorCode = "PHOTO_HASH_MISMATCH",
                httpStatusCode = null
            )
            dao.updateAttendance(rejected)
            return AttendanceSubmissionOutcome(
                record = rejected,
                isSynced = false,
                userMessage = rejected.statusMessage,
                isError = true
            )
        }

        val syncingRecord = if (record.syncStatus != AttendanceOutboxStatus.SYNCING) {
            record.copy(
                syncStatus = AttendanceOutboxStatus.SYNCING,
                statusMessage = AttendanceContract.MSG_SYNCING
            ).also { dao.updateAttendance(it) }
        } else {
            record
        }

        return try {
            // Preserve the EXACT same idempotencyKey across every retry
            val metadataJson = AttendanceContract.buildMetadataJson(
                latitude = syncingRecord.latitude,
                longitude = syncingRecord.longitude,
                accuracyMeters = syncingRecord.accuracyMeters,
                addressHint = syncingRecord.addressHint,
                clientCapturedAtIso = syncingRecord.clientCapturedAtIso,
                note = syncingRecord.note,
                idempotencyKey = syncingRecord.idempotencyKey,
                photoSha256 = verifiedSha
            )
            val multipartBody = AttendanceContract.buildMultipartBody(
                metadataJson = metadataJson,
                photoFile = photoFile
            )
            val responseJson = httpClient.postMultipartJson(
                path = AttendanceContract.ENDPOINT_CHECK_IN,
                multipartBody = multipartBody,
                idempotencyKey = syncingRecord.idempotencyKey,
                requireOkTrue = true,
                clearSessionOn401 = true
            )
            val serverResult = AttendanceContract.parseCheckInSuccessResponse(responseJson)
            val synced = syncingRecord.copy(
                syncStatus = AttendanceOutboxStatus.SYNCED,
                statusMessage = AttendanceContract.MSG_SYNCED,
                backendErrorCode = null,
                httpStatusCode = 200,
                serverCheckinId = serverResult.serverCheckinId,
                serverSequenceInDay = serverResult.serverSequenceInDay,
                serverDailyCount = serverResult.serverDailyCount
            )
            dao.updateAttendance(synced)
            AttendanceSubmissionOutcome(
                record = synced,
                isSynced = true,
                userMessage = AttendanceContract.MSG_SYNCED,
                isError = false
            )
        } catch (e: BackendApiException) {
            val classified = AttendanceContract.classifyFailure(e)
            val updated = syncingRecord.copy(
                syncStatus = classified.syncStatus,
                statusMessage = classified.statusMessage,
                backendErrorCode = classified.backendErrorCode,
                httpStatusCode = classified.httpStatusCode,
                retryCount = if (classified.shouldAutoRetry) syncingRecord.retryCount + 1 else syncingRecord.retryCount
            )
            dao.updateAttendance(updated)
            if (classified.shouldAutoRetry && scheduleWorkOnFailure) {
                outboxWorkScheduler()
            }
            AttendanceSubmissionOutcome(
                record = updated,
                isSynced = false,
                userMessage = classified.statusMessage,
                isError = true
            )
        } catch (e: Exception) {
            val unexpected = syncingRecord.copy(
                syncStatus = AttendanceOutboxStatus.REJECTED,
                statusMessage = e.message?.takeIf { it.isNotBlank() } ?: "Lỗi xử lý phản hồi chấm công.",
                backendErrorCode = "CLIENT_UNEXPECTED_ERROR"
            )
            dao.updateAttendance(unexpected)
            AttendanceSubmissionOutcome(
                record = unexpected,
                isSynced = false,
                userMessage = unexpected.statusMessage,
                isError = true
            )
        }
    }

    private fun ensurePhotoPersistedInOutboxDir(sourceFile: File, idempotencyKey: String): File {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0L) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "PHOTO_HASH_FAILED",
                message = "Không thể đọc tệp ảnh chấm công. Vui lòng chụp lại ảnh."
            )
        }
        val outboxDir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
        val canonicalOutboxDir = runCatching { outboxDir.canonicalPath }.getOrDefault(outboxDir.absolutePath)
        val canonicalSource = runCatching { sourceFile.canonicalPath }.getOrDefault(sourceFile.absolutePath)
        if (canonicalSource.startsWith(canonicalOutboxDir)) {
            return sourceFile
        }
        val destination = File(outboxDir, "checkin_${idempotencyKey}.jpg")
        try {
            sourceFile.copyTo(destination, overwrite = true)
        } catch (e: IOException) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "PHOTO_PERSIST_FAILED",
                message = "Không thể lưu trữ ảnh chấm công vào bộ nhớ an toàn trên thiết bị.",
                cause = e
            )
        }
        return destination
    }

    private fun resolvePersistedPhotoUri(persistedFile: File, fallbackUri: String?): String {
        if (!fallbackUri.isNullOrBlank()) {
            return fallbackUri
        }
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                persistedFile
            ).toString()
        }.getOrElse {
            Uri.fromFile(persistedFile).toString()
        }
    }
}

class AttendanceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = MiniBiRepository(applicationContext)
        // Require a valid server session before attempting authenticated outbox sync
        val sessionRes = repository.bootstrapSession()
        if (sessionRes.isFailure) {
            val err = sessionRes.exceptionOrNull() as? BackendApiException
            return if (err != null &&
                (err.kind == BackendFailureKind.NETWORK ||
                    err.kind == BackendFailureKind.RATE_LIMITED ||
                    err.kind == BackendFailureKind.SERVER_UNAVAILABLE)
            ) {
                Result.retry()
            } else {
                // Unauthenticated (401) or non-retryable session error: mark queued items AUTH_REQUIRED?
                // Or simply stop background retry until user logs in again.
                Result.success()
            }
        }

        val actor = sessionRes.getOrNull() ?: return Result.success()
        val summary = repository.attendanceOutboxService.flushRetryableOutbox(
            appUserId = actor.appUserId,
            scheduleWorkOnFailure = false
        )
        return if (summary.remainingRetryableCount > 0) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
