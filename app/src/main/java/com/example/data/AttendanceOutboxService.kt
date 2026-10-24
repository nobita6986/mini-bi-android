package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AttendanceCheckInServerResult(
    val checkinId: String,
    val businessDate: String,
    val sequenceInDay: Int,
    val dailyCount: Int,
    val capturedAtIso: String,
    val receivedAtIso: String,
    val timeSource: String,
    val replayed: Boolean
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

data class ServerAttendanceHistoryItem(
    val checkinId: String,
    val employeeDisplayName: String,
    val businessDate: String,
    val sequenceInDay: Int,
    val capturedAtIso: String,
    val receivedAtIso: String,
    val timeSource: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val addressHint: String,
    val note: String
)

object AttendanceContract {
    const val ENDPOINT_CHECK_IN = "/api/attendance/check-in"
    const val ENDPOINT_ADMIN_ATTENDANCE_REPORT = "/api/admin/attendance/report"
    const val UNIQUE_OUTBOX_WORK_NAME = "mini_bi_attendance_outbox_sync"

    const val MSG_SYNCED = "Đã chấm công"
    const val MSG_QUEUED_OFFLINE = "Đã lưu trên thiết bị, đang chờ đồng bộ"
    const val MSG_SYNCING = "Đang gửi chấm công lên máy chủ..."
    const val MSG_BACKEND_NOT_AVAILABLE = "Chức năng chấm công chưa được bật trên máy chủ"
    const val MSG_DEVICE_TIME_UNVERIFIED = "Thời gian thiết bị — máy chủ sẽ đánh dấu cần kiểm tra"
    const val MAX_ATTENDANCE_PHOTO_BYTES = 10 * 1024 * 1024
    const val MAX_RAW_CAMERA_PHOTO_BYTES = 60L * 1024L * 1024L
    const val MAX_ATTENDANCE_PHOTO_DIMENSION_PX = 1920

    private val FORBIDDEN_BACKEND_MARKUP_SNIPPETS = listOf(
        "<script",
        "<html",
        "<?php",
        "<svg",
        "<!doctype"
    )

    private val LEGACY_GENERIC_FAILURE_MESSAGES = setOf(
        "Dữ liệu gửi lên không thỏa điều kiện nghiệp vụ.",
        "Dữ liệu gửi lên chưa hợp lệ. Vui lòng kiểm tra lại thông tin và thử lại.",
        "Yêu cầu chấm công bị máy chủ từ chối.",
        "Lỗi xử lý phản hồi chấm công."
    )

    private val FRIENDLY_ATTENDANCE_CODE_MESSAGES = mapOf(
        "PHOTO_INVALID" to "Ảnh chụp từ camera chưa đúng chuẩn hệ thống. Vui lòng bấm Chấm công để chụp lại ảnh và thử lại.",
        "PHOTO_HASH_MISMATCH" to "Ảnh chấm công bị lỗi dữ liệu khi gửi qua mạng. Vui lòng chụp lại ảnh và bấm chấm công thử lại.",
        "PHOTO_HASH_FAILED" to "Không đọc được ảnh chấm công trên máy. Vui lòng bấm Chấm công để chụp lại ảnh và thử lại.",
        "PHOTO_FILE_MISSING" to "Không tìm thấy ảnh vừa chụp trên thiết bị. Vui lòng bấm Chấm công để chụp lại ảnh và thử lại.",
        "PHOTO_PERSIST_FAILED" to "Không lưu được ảnh chấm công vào bộ nhớ máy. Vui lòng kiểm tra dung lượng trống rồi thử lại.",
        "BODY_TOO_LARGE" to "Ảnh chấm công có dung lượng quá lớn. Vui lòng chụp lại ảnh và bấm chấm công thử lại.",
        "CAPTURED_AT_FUTURE" to "Giờ ghi nhận trên máy đang nhanh hơn giờ máy chủ. Vui lòng bấm làm mới giờ chuẩn rồi chấm công thử lại.",
        "CAPTURED_AT_INVALID" to "Thời điểm chụp ảnh chưa hợp lệ. Vui lòng chụp lại ảnh và bấm chấm công thử lại.",
        "CAPTURED_AT_MISSING" to "Thiếu thời điểm chụp ảnh chấm công. Vui lòng chụp lại ảnh và bấm chấm công thử lại.",
        "ATTENDANCE_TIME_TOKEN_INVALID" to "Mốc giờ chuẩn cần được làm mới. Vui lòng bấm làm mới giờ chuẩn phía trên rồi chấm công thử lại.",
        "ATTENDANCE_TIME_TOKEN_EXPIRED" to "Mốc giờ chuẩn đã hết hạn. Vui lòng bấm làm mới giờ chuẩn phía trên rồi chấm công thử lại.",
        "ATTENDANCE_TIME_TOKEN_ACTOR_MISMATCH" to "Phiên giờ chuẩn không khớp tài khoản hiện tại. Vui lòng bấm làm mới giờ chuẩn rồi chấm công thử lại.",
        "ATTENDANCE_TIME_ELAPSED_INVALID" to "Ảnh chụp đã để chờ quá 24 giờ chưa gửi. Vui lòng chụp ảnh mới và bấm chấm công thử lại.",
        "ATTENDANCE_TIME_MISMATCH" to "Đồng hồ thiết bị lệch so với giờ chuẩn máy chủ. Vui lòng bấm làm mới giờ chuẩn rồi chấm công thử lại.",
        "ATTENDANCE_SYNC_WINDOW_EXPIRED" to "Lượt chấm công ngoại tuyến này đã quá hạn 7 ngày đồng bộ. Vui lòng chấm công lượt mới và thử lại.",
        "GPS_COORDINATES_INVALID" to "Tín hiệu vị trí GPS chưa ổn định. Vui lòng bấm làm mới GPS rồi chấm công thử lại.",
        "GPS_ACCURACY_INVALID" to "Độ chính xác GPS chưa đạt yêu cầu. Vui lòng bấm làm mới GPS ở nơi thoáng sóng rồi thử lại.",
        "IDEMPOTENCY_KEY_INVALID" to "Mã phiên gửi chấm công không hợp lệ. Vui lòng chụp lại ảnh và bấm chấm công thử lại.",
        "IDEMPOTENCY_CONFLICT" to "Phiên gửi bị trùng mã cũ. Vui lòng bấm Chấm công để chụp mới và thử lại.",
        "CONTENT_TYPE_INVALID" to "Dữ liệu ảnh gửi lên bị gián đoạn. Vui lòng chụp lại ảnh và bấm chấm công thử lại.",
        "ATTENDANCE_REQUEST_INVALID" to "Dữ liệu chấm công gửi lên chưa đầy đủ. Vui lòng chụp lại ảnh, kiểm tra GPS và thử lại.",
        "ATTENDANCE_CONFIG_REQUIRED" to "Hệ thống chấm công trên máy chủ đang bảo trì cấu hình. Vui lòng thử lại sau ít phút.",
        "ATTENDANCE_UNAVAILABLE" to "Hệ thống chấm công đang bận tạm thời. Vui lòng thử lại sau ít phút.",
        "ACTOR_NOT_AVAILABLE" to "Tài khoản hiện chưa được kích hoạt quyền chấm công. Vui lòng đăng nhập lại và thử lại, hoặc liên hệ quản trị viên."
    )

    val ALLOWED_METADATA_KEYS = setOf(
        "latitude",
        "longitude",
        "accuracy_meters",
        "address_hint",
        "client_captured_at",
        "note",
        "idempotency_key",
        "photo_sha256",
        "time_source",
        "time_token",
        "elapsed_since_anchor_ms"
    )

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )
    private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val VALID_TIME_SOURCES = setOf(
        AttendanceTimeSource.SERVER_ANCHORED,
        AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED
    )

    fun formatClientCapturedAtIso(epochMillis: Long): String {
        return AttendanceTimeContract.formatEpochMillisInHcmIso(epochMillis)
    }

    /**
     * Builds canonical metadata JSON for `POST /api/attendance/check-in`.
     * Includes server-anchored time fields (`time_source`, `time_token`, `elapsed_since_anchor_ms`)
     * and never sends `checkInType`, client-invented `sequence_in_day`, or actor authority fields.
     */
    fun buildMetadataJson(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        addressHint: String,
        clientCapturedAtIso: String,
        note: String,
        idempotencyKey: String,
        photoSha256: String,
        timeSource: String,
        timeToken: String?,
        elapsedSinceAnchorMs: Long?
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
        // Validate ISO-8601 format of client_captured_at
        AttendanceTimeContract.parseIsoInstantToEpochMillis(normalizedCapturedAt, "client_captured_at")

        val normalizedTimeSource = timeSource.trim()
        if (normalizedTimeSource !in VALID_TIME_SOURCES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "TIME_SOURCE_INVALID",
                message = "Nguồn thời gian chấm công không hợp lệ."
            )
        }

        val tokenValue: Any
        val elapsedValue: Any
        if (normalizedTimeSource == AttendanceTimeSource.SERVER_ANCHORED) {
            val cleanedToken = timeToken?.trim().orEmpty()
            if (cleanedToken.isEmpty() || elapsedSinceAnchorMs == null || elapsedSinceAnchorMs < 0L) {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 400,
                    backendCode = "SERVER_ANCHOR_METADATA_INVALID",
                    message = "Thiếu time_token hoặc elapsed_since_anchor_ms cho thời gian đối chiếu máy chủ."
                )
            }
            tokenValue = cleanedToken
            elapsedValue = elapsedSinceAnchorMs
        } else {
            tokenValue = JSONObject.NULL
            elapsedValue = JSONObject.NULL
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
            put("time_source", normalizedTimeSource)
            put("time_token", tokenValue)
            put("elapsed_since_anchor_ms", elapsedValue)
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
     * Strict single canonical contract parser for `POST /api/attendance/check-in`:
     * ```json
     * {
     *   "ok": true,
     *   "checkin": {
     *     "checkin_id": "uuid",
     *     "business_date": "2026-10-23",
     *     "sequence_in_day": 1,
     *     "daily_count": 1,
     *     "captured_at": "2026-10-23T01:20:12.000Z",
     *     "received_at": "2026-10-23T01:25:00.000Z",
     *     "time_source": "SERVER_ANCHORED",
     *     "replayed": false
     *   }
     * }
     * ```
     * Fails closed on any missing or renamed field; does not accept multi-field aliases.
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

        val checkin = json.optJSONObject("checkin") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "ATTENDANCE_CHECKIN_MISSING",
            message = "Phản hồi chấm công từ máy chủ thiếu đối tượng checkin chuẩn."
        )

        val checkinId = checkin.optString("checkin_id", "").trim()
        if (!UUID_REGEX.matches(checkinId)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ATTENDANCE_CHECKIN_ID_INVALID",
                message = "Mã checkin_id từ máy chủ không hợp lệ."
            )
        }

        val businessDate = checkin.optString("business_date", "").trim()
        if (!ISO_DATE_REGEX.matches(businessDate)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ATTENDANCE_BUSINESS_DATE_INVALID",
                message = "Trường business_date từ máy chủ không hợp lệ."
            )
        }

        if (!checkin.has("sequence_in_day") || checkin.isNull("sequence_in_day") ||
            !checkin.has("daily_count") || checkin.isNull("daily_count")
        ) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ATTENDANCE_SEQUENCE_MISSING",
                message = "Phản hồi chấm công thiếu sequence_in_day hoặc daily_count."
            )
        }
        val sequenceInDay = checkin.optInt("sequence_in_day", -1)
        val dailyCount = checkin.optInt("daily_count", -1)
        if (sequenceInDay < 1 || dailyCount < 1) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ATTENDANCE_SEQUENCE_INVALID",
                message = "Thứ tự lượt chấm công từ máy chủ không hợp lệ."
            )
        }

        val capturedAt = checkin.optString("captured_at", "").trim()
        val receivedAt = checkin.optString("received_at", "").trim()
        AttendanceTimeContract.parseIsoInstantToEpochMillis(capturedAt, "captured_at")
        AttendanceTimeContract.parseIsoInstantToEpochMillis(receivedAt, "received_at")

        val serverTimeSource = checkin.optString("time_source", "").trim()
        if (serverTimeSource !in VALID_TIME_SOURCES) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ATTENDANCE_TIME_SOURCE_INVALID",
                message = "Trường time_source từ máy chủ không hợp lệ."
            )
        }

        val rawReplayed = checkin.opt("replayed")
        if (rawReplayed !is Boolean) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ATTENDANCE_REPLAYED_INVALID",
                message = "Trường replayed từ máy chủ không hợp lệ."
            )
        }

        return AttendanceCheckInServerResult(
            checkinId = checkinId,
            businessDate = businessDate,
            sequenceInDay = sequenceInDay,
            dailyCount = dailyCount,
            capturedAtIso = capturedAt,
            receivedAtIso = receivedAt,
            timeSource = serverTimeSource,
            replayed = rawReplayed
        )
    }

    fun shiftIsoDateDays(isoDate: String, deltaDays: Int): String {
        val parts = isoDate.trim().split('-')
        if (parts.size != 3) return isoDate.trim()
        val year = parts[0].toIntOrNull() ?: return isoDate.trim()
        val month = parts[1].toIntOrNull() ?: return isoDate.trim()
        val day = parts[2].toIntOrNull() ?: return isoDate.trim()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(year, month - 1, day)
            add(java.util.Calendar.DAY_OF_MONTH, deltaDays)
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    fun parseServerAttendanceReportItems(json: JSONObject): Pair<List<ServerAttendanceHistoryItem>, Boolean> {
        if (!json.optBoolean("ok", false)) {
            return emptyList<ServerAttendanceHistoryItem>() to false
        }
        val reportObj = json.optJSONObject("report")
            ?: return emptyList<ServerAttendanceHistoryItem>() to false
        val itemsArr = reportObj.optJSONArray("items")
            ?: return emptyList<ServerAttendanceHistoryItem>() to false
        val paginationObj = reportObj.optJSONObject("pagination")
        val hasMore = paginationObj?.optBoolean("has_more", false) == true

        val parsed = buildList {
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                val checkinId = item.optString("checkin_id", "").trim()
                val businessDate = item.optString("business_date", "").trim()
                val capturedAt = item.optString("captured_at", "").trim()
                if (!UUID_REGEX.matches(checkinId) || !ISO_DATE_REGEX.matches(businessDate) || capturedAt.isEmpty()) {
                    continue
                }
                val rawTimeSource = item.optString("time_source", AttendanceTimeSource.SERVER_ANCHORED).trim()
                val resolvedTimeSource = if (rawTimeSource in VALID_TIME_SOURCES) {
                    rawTimeSource
                } else {
                    AttendanceTimeSource.SERVER_ANCHORED
                }
                val lat = if (item.has("latitude") && !item.isNull("latitude")) {
                    item.optDouble("latitude", Double.NaN).takeIf { it.isFinite() }
                } else {
                    null
                }
                val lng = if (item.has("longitude") && !item.isNull("longitude")) {
                    item.optDouble("longitude", Double.NaN).takeIf { it.isFinite() }
                } else {
                    null
                }
                val acc = if (item.has("accuracy_meters") && !item.isNull("accuracy_meters")) {
                    item.optDouble("accuracy_meters", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }?.toFloat()
                } else {
                    null
                }
                fun optCleanText(key: String, default: String = ""): String {
                    if (!item.has(key) || item.isNull(key)) return default
                    return item.optString(key, default).trim()
                }
                add(
                    ServerAttendanceHistoryItem(
                        checkinId = checkinId,
                        employeeDisplayName = optCleanText("employee_display_name"),
                        businessDate = businessDate,
                        sequenceInDay = item.optInt("sequence_in_day", 1).coerceAtLeast(1),
                        capturedAtIso = capturedAt,
                        receivedAtIso = optCleanText("received_at", capturedAt).ifBlank { capturedAt },
                        timeSource = resolvedTimeSource,
                        latitude = lat,
                        longitude = lng,
                        accuracyMeters = acc,
                        addressHint = optCleanText("address_hint"),
                        note = optCleanText("note")
                    )
                )
            }
        }
        return parsed to hasMore
    }

    fun friendlyAttendanceErrorMessage(
        backendCode: String?,
        httpStatusCode: Int? = null,
        fallbackMessage: String? = null
    ): String {
        val normalizedCode = backendCode?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null }
        if (normalizedCode != null) {
            FRIENDLY_ATTENDANCE_CODE_MESSAGES[normalizedCode]?.let { return it }
        }
        if (httpStatusCode == 413) {
            return FRIENDLY_ATTENDANCE_CODE_MESSAGES.getValue("BODY_TOO_LARGE")
        }
        val cleanedFallback = fallbackMessage?.trim().orEmpty()
        if (cleanedFallback.isEmpty() ||
            cleanedFallback in LEGACY_GENERIC_FAILURE_MESSAGES ||
            cleanedFallback.startsWith("Yêu cầu không hợp lệ (") ||
            cleanedFallback.startsWith("Phản hồi chấm công") ||
            cleanedFallback.startsWith("Máy chủ trả phản hồi") ||
            cleanedFallback.startsWith("Mã checkin_id") ||
            cleanedFallback.startsWith("Trường ")
        ) {
            return when (httpStatusCode) {
                409 -> "Lượt gửi chấm công bị xung đột phiên cũ. Vui lòng bấm Chấm công để chụp ảnh mới và thử lại."
                422 -> "Chấm công chưa thành công do ảnh chụp hoặc thời gian ghi nhận chưa khớp chuẩn hệ thống. Vui lòng bấm Chấm công để chụp lại ảnh và thử lại."
                else -> "Chấm công chưa thành công. Vui lòng kiểm tra GPS, chụp lại ảnh và bấm chấm công thử lại."
            }
        }
        val lower = cleanedFallback.lowercase(Locale.ROOT)
        return if ("thử lại" in lower || "gửi lại" in lower || "chụp lại" in lower || "đăng nhập lại" in lower) {
            cleanedFallback
        } else {
            "${cleanedFallback.trimEnd('.')} — vui lòng kiểm tra và thử lại."
        }
    }

    fun resolveDisplayStatusMessage(
        syncStatus: String,
        backendErrorCode: String?,
        httpStatusCode: Int?,
        rawStatusMessage: String
    ): String {
        return when (syncStatus) {
            AttendanceOutboxStatus.SYNCED,
            AttendanceOutboxStatus.QUEUED,
            AttendanceOutboxStatus.SYNCING,
            AttendanceOutboxStatus.RETRYABLE_ERROR,
            AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE -> rawStatusMessage
            AttendanceOutboxStatus.AUTH_REQUIRED ->
                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại rồi bấm chấm công thử lại."
            AttendanceOutboxStatus.DENIED -> {
                val code = backendErrorCode?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null }
                FRIENDLY_ATTENDANCE_CODE_MESSAGES[code]
                    ?: if ("thử lại" in rawStatusMessage.lowercase(Locale.ROOT)) {
                        rawStatusMessage
                    } else {
                        "${rawStatusMessage.ifBlank { "Tài khoản chưa có quyền thực hiện chấm công" }.trimEnd('.')}. Vui lòng đăng nhập lại và thử lại, hoặc báo quản trị viên."
                    }
            }
            else -> friendlyAttendanceErrorMessage(
                backendCode = backendErrorCode,
                httpStatusCode = httpStatusCode,
                fallbackMessage = rawStatusMessage
            )
        }
    }

    internal fun containsForbiddenBackendMarkup(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val latin1Lower = String(bytes, StandardCharsets.ISO_8859_1).lowercase(Locale.ROOT)
        return FORBIDDEN_BACKEND_MARKUP_SNIPPETS.any { needle -> latin1Lower.contains(needle) }
    }

    fun isStrictBackendSafeJpeg(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || bytes.size > MAX_ATTENDANCE_PHOTO_BYTES) return false
        if ((bytes[0].toInt() and 0xFF) != 0xFF ||
            (bytes[1].toInt() and 0xFF) != 0xD8 ||
            (bytes[2].toInt() and 0xFF) != 0xFF
        ) {
            return false
        }
        var last = bytes.size - 1
        while (last > 0 && bytes[last].toInt() == 0) {
            last--
        }
        if (last < 3 ||
            (bytes[last - 1].toInt() and 0xFF) != 0xFF ||
            (bytes[last].toInt() and 0xFF) != 0xD9
        ) {
            return false
        }
        return !containsForbiddenBackendMarkup(bytes)
    }

    /**
     * Normalizes a camera photo file into a clean, single-page, upright baseline JPEG (`<= 1920px`,
     * `<= 10 MB`, starting with `FF D8 FF` and ending strictly with `FF D9`) so OEM camera trailers
     * (Samsung Motion Photo `SEFT`, Xiaomi/OPPO XMP, Android 14+ Ultra HDR MPF gainmaps, HEIC, or 50MP+
     * sensors) never trigger `422 PHOTO_INVALID` or `413 BODY_TOO_LARGE` on the backend.
     *
     * Returns `null` when `sourceFile` is not a decodable bitmap (for example, 9-byte synthetic
     * test fixtures in unit tests), leaving those fixtures byte-for-byte unchanged.
     */
    fun normalizeAttendancePhotoBytesIfDecodable(
        sourceFile: File,
        maxDimensionPx: Int = MAX_ATTENDANCE_PHOTO_DIMENSION_PX,
        initialJpegQuality: Int = 88
    ): ByteArray? {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0L) {
            return null
        }
        if (sourceFile.length() > MAX_RAW_CAMERA_PHOTO_BYTES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 413,
                backendCode = "BODY_TOO_LARGE",
                message = friendlyAttendanceErrorMessage("BODY_TOO_LARGE", 413, null)
            )
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOpts)
        }.onFailure {
            return null
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            return null
        }

        var sampleSize = 1
        while (
            boundsOpts.outWidth / (sampleSize * 2) >= maxDimensionPx &&
            boundsOpts.outHeight / (sampleSize * 2) >= maxDimensionPx
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts)
        }.getOrNull() ?: return null
        if (decoded.width <= 0 || decoded.height <= 0) {
            decoded.recycle()
            return null
        }

        val exifOrientation = runCatching {
            ExifInterface(sourceFile.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val maxSide = maxOf(decoded.width, decoded.height)
        val scaleRatio = if (maxDimensionPx > 0 && maxSide > maxDimensionPx) {
            maxDimensionPx.toFloat() / maxSide.toFloat()
        } else {
            1f
        }

        val matrix = Matrix()
        if (scaleRatio < 1f) {
            matrix.postScale(scaleRatio, scaleRatio)
        }
        when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        val transformed = if (!matrix.isIdentity) {
            val created = Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true
            )
            if (created !== decoded) {
                decoded.recycle()
            }
            created
        } else {
            decoded
        }

        try {
            val candidateQualities = listOf(
                initialJpegQuality.coerceIn(50, 95),
                80,
                72,
                62,
                52,
                51
            ).distinct()

            for (quality in candidateQualities) {
                val baos = ByteArrayOutputStream()
                val ok = transformed.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                if (!ok) continue
                val compressed = baos.toByteArray()
                if (isStrictBackendSafeJpeg(compressed)) {
                    return compressed
                }
            }
            return null
        } finally {
            transformed.recycle()
        }
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
                statusMessage = "${error.message} (ứng dụng sẽ tự động gửi lại)",
                httpStatusCode = error.statusCode,
                backendErrorCode = error.backendCode
            )
            BackendFailureKind.UNAUTHENTICATED -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.AUTH_REQUIRED,
                shouldAutoRetry = false,
                statusMessage = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại rồi bấm chấm công thử lại.",
                httpStatusCode = error.statusCode ?: 401,
                backendErrorCode = error.backendCode ?: "AUTH_UNAUTHENTICATED"
            )
            BackendFailureKind.FORBIDDEN -> AttendanceFailureClassification(
                syncStatus = AttendanceOutboxStatus.DENIED,
                shouldAutoRetry = false,
                statusMessage = resolveDisplayStatusMessage(
                    syncStatus = AttendanceOutboxStatus.DENIED,
                    backendErrorCode = error.backendCode,
                    httpStatusCode = error.statusCode ?: 403,
                    rawStatusMessage = error.message
                ),
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
                statusMessage = friendlyAttendanceErrorMessage(
                    backendCode = error.backendCode,
                    httpStatusCode = error.statusCode,
                    fallbackMessage = error.message
                ),
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
    private val timeAnchorManager: AttendanceTimeAnchorManager,
    private val isNetworkAvailable: () -> Boolean = { isDeviceNetworkConnected(context) },
    private val idempotencyKeyGenerator: () -> String = { UUID.randomUUID().toString() },
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
     * Records a check-in attempt into the durable Room outbox with frozen capture time
     * (`SERVER_ANCHORED` when a valid BI server time anchor exists, or `DEVICE_CLOCK_UNVERIFIED`
     * when offline/expired/rebooted) and immediately attempts backend synchronization if online.
     *
     * Strict guarantees:
     * - Fails closed BEFORE saving/sending if photo file cannot be read or SHA-256 fails.
     * - Freezes `idempotencyKey`, `clientCapturedAtIso`, `timeSource`, `timeToken`, and
     *   `elapsedSinceAnchorMs` once at capture; retries NEVER recompute or overwrite them.
     * - Never fabricates SHA-256 or `sequence_in_day`.
     * - Reports `"Đã chấm công"` ONLY when backend returns valid canonical 2xx `{ ok: true, checkin: {...} }`.
     */
    suspend fun submitCheckIn(
        actor: AppActor,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        addressHint: String,
        sourcePhotoFile: File,
        sourcePhotoUri: String?,
        note: String,
        capturedTimeOverride: CapturedAttendanceTime? = null
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

        val frozenTime = capturedTimeOverride ?: timeAnchorManager.resolveCaptureTime(actor.appUserId)

        // Pre-validate canonical metadata contract before persisting to outbox
        AttendanceContract.buildMetadataJson(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            addressHint = addressHint,
            clientCapturedAtIso = frozenTime.clientCapturedAtIso,
            note = note,
            idempotencyKey = fixedIdempotencyKey,
            photoSha256 = photoSha256,
            timeSource = frozenTime.timeSource,
            timeToken = frozenTime.timeToken,
            elapsedSinceAnchorMs = frozenTime.elapsedSinceAnchorMs
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
            clientCapturedAtIso = frozenTime.clientCapturedAtIso,
            epochMillis = frozenTime.capturedEpochMillis,
            timeSource = frozenTime.timeSource,
            timeToken = frozenTime.timeToken,
            elapsedSinceAnchorMs = frozenTime.elapsedSinceAnchorMs,
            note = note.trim(),
            syncStatus = if (onlineNow) AttendanceOutboxStatus.SYNCING else AttendanceOutboxStatus.QUEUED,
            statusMessage = if (onlineNow) AttendanceContract.MSG_SYNCING else AttendanceContract.MSG_QUEUED_OFFLINE,
            backendErrorCode = null,
            httpStatusCode = null,
            serverCheckinId = null,
            serverBusinessDate = null,
            serverSequenceInDay = null,
            serverDailyCount = null,
            serverCapturedAtIso = null,
            serverReceivedAtIso = null,
            serverTimeSource = null,
            serverReplayed = null,
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

        syncExistingRecord(
            record = storedRecord,
            currentActorId = actor.appUserId,
            scheduleWorkOnFailure = true
        )
    }

    suspend fun syncRecordById(
        recordId: Long,
        currentActorId: String,
        scheduleWorkOnFailure: Boolean = true
    ): AttendanceSubmissionOutcome? = withContext(Dispatchers.IO) {
        val current = dao.getAttendanceById(recordId) ?: return@withContext null
        if (current.appUserId != currentActorId.trim()) {
            return@withContext null
        }
        if (current.syncStatus == AttendanceOutboxStatus.SYNCED) {
            return@withContext AttendanceSubmissionOutcome(
                record = current,
                isSynced = true,
                userMessage = AttendanceContract.MSG_SYNCED,
                isError = false
            )
        }
        syncExistingRecord(
            record = current,
            currentActorId = currentActorId.trim(),
            scheduleWorkOnFailure = scheduleWorkOnFailure
        )
    }

    /**
     * Synchronizes confirmed check-in history from the server (`/api/admin/attendance/report?employee_id=...`)
     * into Room `attendance_outbox` so reinstalls or multi-device logins restore server-confirmed logs
     * without duplicating locally recorded check-ins.
     *
     * Gracefully returns `0` if offline, if `actor.appUserId` is not a canonical UUID, or if the
     * actor does not hold server attendance-report access (`403 ATTENDANCE_REPORT_ACCESS_DENIED`).
     */
    suspend fun syncServerHistoryForUser(
        actor: AppActor,
        referenceTodayIso: String,
        daysBack: Int = 90,
        maxPages: Int = 3
    ): Int = withContext(Dispatchers.IO) {
        val cleanUserId = actor.appUserId.trim()
        if (!WorkReportsContract.isUuid(cleanUserId)) return@withContext 0
        if (!actor.hasScopeKind("all") && !actor.hasCapability("attendance_report_read")) return@withContext 0
        if (!isNetworkAvailable()) return@withContext 0

        val toDate = referenceTodayIso.trim().takeIf { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) }
            ?: DirectEntryContract.todayIsoInHcm()
        val safeDaysBack = daysBack.coerceIn(1, 365)
        val fromDate = AttendanceContract.shiftIsoDateDays(toDate, -safeDaysBack)
        val employeeParam = cleanUserId.lowercase(Locale.ROOT)

        val collected = mutableListOf<ServerAttendanceHistoryItem>()
        var page = 1
        val safeMaxPages = maxPages.coerceIn(1, 10)
        try {
            while (page <= safeMaxPages) {
                val path = "${AttendanceContract.ENDPOINT_ADMIN_ATTENDANCE_REPORT}" +
                    "?from_date=$fromDate&to_date=$toDate&page=$page&page_size=100&employee_id=$employeeParam"
                val json = httpClient.getJson(
                    pathWithQuery = path,
                    requireOkTrue = true,
                    clearSessionOn401 = false
                )
                val (items, hasMore) = AttendanceContract.parseServerAttendanceReportItems(json)
                collected.addAll(items)
                if (!hasMore || items.isEmpty()) break
                page += 1
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silently ignore 403/404/network errors during background history pull
            if (collected.isEmpty()) return@withContext 0
        }

        if (collected.isEmpty()) return@withContext 0

        val dailyCounts = collected
            .groupBy { it.businessDate }
            .mapValues { (_, list) ->
                maxOf(list.size, list.maxOfOrNull { it.sequenceInDay } ?: list.size)
            }

        var insertedCount = 0
        for (item in collected) {
            val existing = dao.getAttendanceByServerCheckinId(cleanUserId, item.checkinId)
            val resolvedDailyCount = dailyCounts[item.businessDate]
            if (existing != null) {
                if (existing.serverDailyCount == null && resolvedDailyCount != null) {
                    dao.updateAttendance(existing.copy(serverDailyCount = resolvedDailyCount))
                }
                continue
            }

            val epochMs = runCatching {
                AttendanceTimeContract.parseIsoInstantToEpochMillis(item.capturedAtIso, "captured_at")
            }.getOrDefault(System.currentTimeMillis())
            val clientCapturedHcmIso = AttendanceTimeContract.formatEpochMillisInHcmIso(epochMs)
            val remotePhotoUri = runCatching {
                httpClient.resolveUrl("/api/admin/attendance/checkins/${item.checkinId}/photo").toString()
            }.getOrDefault("")

            dao.insertAttendance(
                AttendanceEntity(
                    idempotencyKey = "server-sync-${item.checkinId}",
                    appUserId = cleanUserId,
                    userDisplayName = item.employeeDisplayName.ifBlank { actor.displayName },
                    latitude = item.latitude ?: 0.0,
                    longitude = item.longitude ?: 0.0,
                    accuracyMeters = item.accuracyMeters ?: 0f,
                    addressHint = item.addressHint,
                    photoFilePath = "",
                    photoUri = remotePhotoUri,
                    photoSha256 = "",
                    clientCapturedAtIso = clientCapturedHcmIso,
                    epochMillis = epochMs,
                    timeSource = item.timeSource,
                    timeToken = null,
                    elapsedSinceAnchorMs = null,
                    note = item.note,
                    syncStatus = AttendanceOutboxStatus.SYNCED,
                    statusMessage = AttendanceContract.MSG_SYNCED,
                    backendErrorCode = null,
                    httpStatusCode = 200,
                    serverCheckinId = item.checkinId,
                    serverBusinessDate = item.businessDate,
                    serverSequenceInDay = item.sequenceInDay,
                    serverDailyCount = resolvedDailyCount,
                    serverCapturedAtIso = item.capturedAtIso,
                    serverReceivedAtIso = item.receivedAtIso,
                    serverTimeSource = item.timeSource,
                    serverReplayed = false,
                    retryCount = 0
                )
            )
            insertedCount += 1
        }
        insertedCount
    }

    /**
     * Retries only records belonging to `appUserId` that are in `QUEUED` or `RETRYABLE_ERROR`.
     * Never sends another actor's queued records under the current session,
     * never calls `/api/attendance/time` to mutate captured timestamps,
     * and never retries `400`/`401`/`403`/`404`/`409`/`422`.
     */
    suspend fun flushRetryableOutbox(
        appUserId: String,
        scheduleWorkOnFailure: Boolean = true
    ): AttendanceOutboxFlushSummary = withContext(Dispatchers.IO) {
        val cleanActorId = appUserId.trim()
        if (cleanActorId.isEmpty()) {
            return@withContext AttendanceOutboxFlushSummary(
                processedCount = 0,
                syncedCount = 0,
                remainingRetryableCount = 0,
                terminalFailureCount = 0
            )
        }

        val pending = dao.getRetryableAttendanceOutboxForUser(cleanActorId)
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
            val outcome = syncExistingRecord(
                record = item,
                currentActorId = cleanActorId,
                scheduleWorkOnFailure = false
            )
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
        currentActorId: String,
        scheduleWorkOnFailure: Boolean
    ): AttendanceSubmissionOutcome {
        // Hard actor-isolation guard: NEVER send actor A's outbox item under actor B's session
        if (record.appUserId != currentActorId) {
            return AttendanceSubmissionOutcome(
                record = record,
                isSynced = false,
                userMessage = "Bản ghi thuộc tài khoản khác, không được phép gửi dưới phiên hiện tại.",
                isError = true
            )
        }

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
            // Preserve the EXACT same idempotencyKey, clientCapturedAtIso, timeSource, timeToken, and elapsedSinceAnchorMs across every retry
            val metadataJson = AttendanceContract.buildMetadataJson(
                latitude = syncingRecord.latitude,
                longitude = syncingRecord.longitude,
                accuracyMeters = syncingRecord.accuracyMeters,
                addressHint = syncingRecord.addressHint,
                clientCapturedAtIso = syncingRecord.clientCapturedAtIso,
                note = syncingRecord.note,
                idempotencyKey = syncingRecord.idempotencyKey,
                photoSha256 = verifiedSha,
                timeSource = syncingRecord.timeSource,
                timeToken = syncingRecord.timeToken,
                elapsedSinceAnchorMs = syncingRecord.elapsedSinceAnchorMs
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
                serverCheckinId = serverResult.checkinId,
                serverBusinessDate = serverResult.businessDate,
                serverSequenceInDay = serverResult.sequenceInDay,
                serverDailyCount = serverResult.dailyCount,
                serverCapturedAtIso = serverResult.capturedAtIso,
                serverReceivedAtIso = serverResult.receivedAtIso,
                serverTimeSource = serverResult.timeSource,
                serverReplayed = serverResult.replayed
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
                message = "Không đọc được ảnh chấm công vừa chụp. Vui lòng chụp lại ảnh và thử lại."
            )
        }
        val outboxDir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
        val canonicalOutboxDir = runCatching { outboxDir.canonicalPath }.getOrDefault(outboxDir.absolutePath)
        val canonicalSource = runCatching { sourceFile.canonicalPath }.getOrDefault(sourceFile.absolutePath)
        val isAlreadyInOutboxDir = canonicalSource.startsWith(canonicalOutboxDir)

        val normalizedBytes = AttendanceContract.normalizeAttendancePhotoBytesIfDecodable(sourceFile)
        val destination = if (isAlreadyInOutboxDir) {
            sourceFile
        } else {
            File(outboxDir, "checkin_${idempotencyKey}.jpg")
        }

        try {
            if (normalizedBytes != null) {
                val tmpFile = File(outboxDir, "${destination.name}.norm.tmp")
                tmpFile.writeBytes(normalizedBytes)
                if (!tmpFile.renameTo(destination)) {
                    destination.writeBytes(normalizedBytes)
                    runCatching { tmpFile.delete() }
                }
            } else if (!isAlreadyInOutboxDir) {
                sourceFile.copyTo(destination, overwrite = true)
            }
        } catch (e: IOException) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "PHOTO_PERSIST_FAILED",
                message = AttendanceContract.friendlyAttendanceErrorMessage("PHOTO_PERSIST_FAILED", null, null),
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
                Result.success()
            }
        }

        val actor = sessionRes.getOrNull() ?: return Result.success()
        // Notice: We intentionally do NOT call `/api/attendance/time` to mutate queued items;
        // each queued item preserves its original captured time & idempotency key.
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
