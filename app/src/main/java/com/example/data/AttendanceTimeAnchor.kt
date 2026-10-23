package com.example.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object AttendanceTimeSource {
    const val SERVER_ANCHORED = "SERVER_ANCHORED"
    const val DEVICE_CLOCK_UNVERIFIED = "DEVICE_CLOCK_UNVERIFIED"
}

data class ServerTimeAnchor(
    val appUserId: String,
    val serverTimeIso: String,
    val serverTimeEpochMs: Long,
    val expiresAtIso: String,
    val expiresAtEpochMs: Long,
    val timeToken: String,
    val elapsedRealtimeAtAnchorMs: Long,
    val bootCountAtAnchor: Int,
    val processSessionId: String
)

enum class TimeAnchorInvalidReason {
    NONE,
    MISSING,
    ACTOR_MISMATCH,
    DEVICE_REBOOTED,
    ELAPSED_REALTIME_REGRESSED,
    EXPIRED
}

data class TimeAnchorEvaluation(
    val validAnchor: ServerTimeAnchor?,
    val invalidReason: TimeAnchorInvalidReason,
    val elapsedSinceAnchorMs: Long?,
    val estimatedServerNowEpochMs: Long?,
    val isExpiringSoon: Boolean
) {
    val isValid: Boolean get() = validAnchor != null
}

data class TimeAnchorUiState(
    val syncing: Boolean = false,
    val hasValidAnchor: Boolean = false,
    val invalidReason: TimeAnchorInvalidReason = TimeAnchorInvalidReason.MISSING,
    val serverTimeIso: String? = null,
    val expiresAtIso: String? = null,
    val lastSyncError: String? = null
)

data class CapturedAttendanceTime(
    val clientCapturedAtIso: String,
    val capturedEpochMillis: Long,
    val timeSource: String,
    val timeToken: String?,
    val elapsedSinceAnchorMs: Long?
)

object AttendanceTimeContract {
    const val ENDPOINT_TIME = "/api/attendance/time"

    // Proactive refresh window: refresh when <= 10 minutes remain (or <= 20% of total TTL for short-lived tokens)
    const val MAX_EXPIRING_SOON_WINDOW_MS = 10L * 60L * 1000L

    private val ISO_INSTANT_REGEX = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|([+-])(\\d{2}):?(\\d{2}))$"
    )

    fun parseIsoInstantToEpochMillis(rawIso: String, fieldName: String = "timestamp"): Long {
        val cleaned = rawIso.trim()
        val match = ISO_INSTANT_REGEX.matchEntire(cleaned) ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "TIME_ISO_INVALID",
            message = "Trường thời gian '$fieldName' không đúng định dạng ISO-8601 hợp lệ."
        )

        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val hour = match.groupValues[4].toInt()
        val minute = match.groupValues[5].toInt()
        val second = match.groupValues[6].toInt()
        val fractionRaw = match.groupValues[7]
        val millis = if (fractionRaw.isEmpty()) {
            0
        } else {
            fractionRaw.take(3).padEnd(3, '0').toInt()
        }

        val tzPart = match.groupValues[8]
        val offsetMillis = if (tzPart == "Z") {
            0L
        } else {
            val sign = if (match.groupValues[9] == "-") -1L else 1L
            val offHours = match.groupValues[10].toInt()
            val offMinutes = match.groupValues[11].toInt()
            if (offHours > 23 || offMinutes > 59) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "TIME_OFFSET_INVALID",
                    message = "Độ lệch múi giờ trong '$fieldName' không hợp lệ."
                )
            }
            sign * ((offHours * 3600_000L) + (offMinutes * 60_000L))
        }

        val utcEpoch = try {
            val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, second)
                set(Calendar.MILLISECOND, millis)
            }
            cal.timeInMillis
        } catch (e: Exception) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "TIME_CALENDAR_INVALID",
                message = "Giá trị lịch ngày giờ trong '$fieldName' không hợp lệ.",
                cause = e
            )
        }

        return utcEpoch - offsetMillis
    }

    fun formatEpochMillisInHcmIso(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        return sdf.format(Date(epochMillis))
    }

    fun parseTimeAnchorResponse(
        json: JSONObject,
        appUserId: String,
        receivedElapsedRealtimeMs: Long,
        bootCount: Int,
        processSessionId: String
    ): ServerTimeAnchor {
        if (!json.optBoolean("ok", false)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = json.optString("code").ifBlank { "TIME_ANCHOR_OK_FALSE" },
                message = "Phản hồi thời gian chuẩn từ máy chủ thiếu cờ xác nhận hợp lệ."
            )
        }
        if (receivedElapsedRealtimeMs < 0L) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ELAPSED_REALTIME_INVALID",
                message = "Bộ đếm thời gian đơn điệu trên thiết bị không hợp lệ."
            )
        }

        val serverTimeIso = json.optString("server_time", "").trim()
        val expiresAtIso = json.optString("expires_at", "").trim()
        val timeToken = if (json.isNull("time_token")) "" else json.optString("time_token", "").trim()

        if (serverTimeIso.isEmpty() || expiresAtIso.isEmpty() || timeToken.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "TIME_ANCHOR_FIELDS_MISSING",
                message = "Máy chủ trả thiếu server_time, expires_at hoặc time_token."
            )
        }

        val serverTimeEpochMs = parseIsoInstantToEpochMillis(serverTimeIso, "server_time")
        val expiresAtEpochMs = parseIsoInstantToEpochMillis(expiresAtIso, "expires_at")
        if (expiresAtEpochMs <= serverTimeEpochMs) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "TIME_ANCHOR_EXPIRY_INVALID",
                message = "Thời hạn hiệu lực mốc giờ máy chủ không hợp lệ."
            )
        }

        return ServerTimeAnchor(
            appUserId = appUserId.trim(),
            serverTimeIso = serverTimeIso,
            serverTimeEpochMs = serverTimeEpochMs,
            expiresAtIso = expiresAtIso,
            expiresAtEpochMs = expiresAtEpochMs,
            timeToken = timeToken,
            elapsedRealtimeAtAnchorMs = receivedElapsedRealtimeMs,
            bootCountAtAnchor = bootCount,
            processSessionId = processSessionId
        )
    }

    fun computeExpiringSoonThresholdMs(serverTimeEpochMs: Long, expiresAtEpochMs: Long): Long {
        val ttlMs = (expiresAtEpochMs - serverTimeEpochMs).coerceAtLeast(1L)
        return minOf(MAX_EXPIRING_SOON_WINDOW_MS, (ttlMs / 5L).coerceAtLeast(1_000L))
    }
}

class AttendanceTimeAnchorManager(
    private val context: Context,
    private val httpClient: BackendHttpClient,
    private val storageFile: File = File(context.noBackupFilesDir, "attendance_time_anchor.enc"),
    private val cipher: CookieCipher = AndroidKeystoreAesGcmCipher(),
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val deviceWallClockMs: () -> Long = { System.currentTimeMillis() },
    private val bootCountProvider: () -> Int = { readSystemBootCount(context) },
    private val processSessionId: String = PROCESS_SESSION_ID
) {

    companion object {
        private val PROCESS_SESSION_ID: String = UUID.randomUUID().toString()

        fun readSystemBootCount(context: Context): Int {
            return runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            }.getOrDefault(-1)
        }
    }

    private val stateLock = Any()
    private val networkMutex = Mutex()
    private var diskLoaded = false
    private var cachedAnchor: ServerTimeAnchor? = null

    private val _uiState = MutableStateFlow(TimeAnchorUiState())
    val uiState: StateFlow<TimeAnchorUiState> = _uiState.asStateFlow()

    /**
     * Evaluates the stored time anchor strictly using monotonic `elapsedRealtime` + `bootCount`
     * without consulting `System.currentTimeMillis()`.
     * Automatically purges invalidated anchors (rebooted, regressed, or expired).
     */
    fun evaluateAnchor(appUserId: String): TimeAnchorEvaluation {
        synchronized(stateLock) {
            ensureLoadedLocked()
            val anchor = cachedAnchor ?: return TimeAnchorEvaluation(
                validAnchor = null,
                invalidReason = TimeAnchorInvalidReason.MISSING,
                elapsedSinceAnchorMs = null,
                estimatedServerNowEpochMs = null,
                isExpiringSoon = true
            ).also { publishStateLocked(it, lastError = _uiState.value.lastSyncError) }

            if (anchor.appUserId != appUserId.trim()) {
                return TimeAnchorEvaluation(
                    validAnchor = null,
                    invalidReason = TimeAnchorInvalidReason.ACTOR_MISMATCH,
                    elapsedSinceAnchorMs = null,
                    estimatedServerNowEpochMs = null,
                    isExpiringSoon = true
                ).also { publishStateLocked(it, lastError = null) }
            }

            // 1. Reboot detection across process restarts via Settings.Global.BOOT_COUNT
            val currentBootCount = bootCountProvider()
            val sameProcess = anchor.processSessionId == processSessionId
            val rebootDetected = when {
                anchor.bootCountAtAnchor >= 0 && currentBootCount >= 0 ->
                    currentBootCount != anchor.bootCountAtAnchor
                !sameProcess ->
                    // Persisted from an older process without verifiable BOOT_COUNT: fail closed
                    true
                else ->
                    false
            }
            if (rebootDetected) {
                clearAnchorLocked()
                return TimeAnchorEvaluation(
                    validAnchor = null,
                    invalidReason = TimeAnchorInvalidReason.DEVICE_REBOOTED,
                    elapsedSinceAnchorMs = null,
                    estimatedServerNowEpochMs = null,
                    isExpiringSoon = true
                ).also { publishStateLocked(it, lastError = null) }
            }

            // 2. Monotonic clock non-regression check
            val nowElapsedMs = elapsedRealtimeMs()
            if (nowElapsedMs < anchor.elapsedRealtimeAtAnchorMs) {
                clearAnchorLocked()
                return TimeAnchorEvaluation(
                    validAnchor = null,
                    invalidReason = TimeAnchorInvalidReason.ELAPSED_REALTIME_REGRESSED,
                    elapsedSinceAnchorMs = null,
                    estimatedServerNowEpochMs = null,
                    isExpiringSoon = true
                ).also { publishStateLocked(it, lastError = null) }
            }

            // 3. Expiry check computed strictly from server_time + elapsed_since_anchor_ms
            val elapsedSinceAnchorMs = nowElapsedMs - anchor.elapsedRealtimeAtAnchorMs
            val estimatedServerNowEpochMs = anchor.serverTimeEpochMs + elapsedSinceAnchorMs
            if (estimatedServerNowEpochMs >= anchor.expiresAtEpochMs) {
                clearAnchorLocked()
                return TimeAnchorEvaluation(
                    validAnchor = null,
                    invalidReason = TimeAnchorInvalidReason.EXPIRED,
                    elapsedSinceAnchorMs = null,
                    estimatedServerNowEpochMs = null,
                    isExpiringSoon = true
                ).also { publishStateLocked(it, lastError = null) }
            }

            val remainingMs = anchor.expiresAtEpochMs - estimatedServerNowEpochMs
            val thresholdMs = AttendanceTimeContract.computeExpiringSoonThresholdMs(
                anchor.serverTimeEpochMs,
                anchor.expiresAtEpochMs
            )
            val isExpiringSoon = remainingMs <= thresholdMs

            return TimeAnchorEvaluation(
                validAnchor = anchor,
                invalidReason = TimeAnchorInvalidReason.NONE,
                elapsedSinceAnchorMs = elapsedSinceAnchorMs,
                estimatedServerNowEpochMs = estimatedServerNowEpochMs,
                isExpiringSoon = isExpiringSoon
            ).also { publishStateLocked(it, lastError = null) }
        }
    }

    /**
     * Computes the remaining milliseconds before proactive refresh is needed for UI timers,
     * or 0L if immediate refresh is needed.
     */
    fun millisUntilProactiveRefresh(appUserId: String): Long? {
        val eval = evaluateAnchor(appUserId)
        val anchor = eval.validAnchor ?: return null
        val estimatedNow = eval.estimatedServerNowEpochMs ?: return null
        val thresholdMs = AttendanceTimeContract.computeExpiringSoonThresholdMs(
            anchor.serverTimeEpochMs,
            anchor.expiresAtEpochMs
        )
        val refreshAtEpochMs = anchor.expiresAtEpochMs - thresholdMs
        return (refreshAtEpochMs - estimatedNow).coerceAtLeast(0L)
    }

    /**
     * Freezes capture timestamp at the moment an attendance photo is taken.
     * - If a valid non-expired, non-rebooted anchor exists: uses `server_time + elapsed_since_anchor_ms`
     *   with `SERVER_ANCHORED`, `time_token`, and `elapsed_since_anchor_ms` (never touching `System.currentTimeMillis()`).
     * - Otherwise: falls back to `DEVICE_CLOCK_UNVERIFIED` with `null` token and `null` elapsed_since_anchor_ms
     *   so the check-in is never lost offline.
     */
    fun resolveCaptureTime(appUserId: String): CapturedAttendanceTime {
        val eval = evaluateAnchor(appUserId)
        val anchor = eval.validAnchor
        val elapsedSince = eval.elapsedSinceAnchorMs
        val serverAlignedEpochMs = eval.estimatedServerNowEpochMs

        return if (anchor != null && elapsedSince != null && serverAlignedEpochMs != null) {
            CapturedAttendanceTime(
                clientCapturedAtIso = AttendanceTimeContract.formatEpochMillisInHcmIso(serverAlignedEpochMs),
                capturedEpochMillis = serverAlignedEpochMs,
                timeSource = AttendanceTimeSource.SERVER_ANCHORED,
                timeToken = anchor.timeToken,
                elapsedSinceAnchorMs = elapsedSince
            )
        } else {
            val deviceEpochMs = deviceWallClockMs()
            CapturedAttendanceTime(
                clientCapturedAtIso = AttendanceTimeContract.formatEpochMillisInHcmIso(deviceEpochMs),
                capturedEpochMillis = deviceEpochMs,
                timeSource = AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED,
                timeToken = null,
                elapsedSinceAnchorMs = null
            )
        }
    }

    /**
     * Calls `GET /api/attendance/time` when forced (after login) or when anchor is missing,
     * invalidated, rebooted, expired, or expiring soon.
     */
    suspend fun syncTimeAnchor(
        appUserId: String,
        force: Boolean = false
    ): Result<ServerTimeAnchor> = withContext(Dispatchers.IO) {
        val cleanUserId = appUserId.trim()
        if (cleanUserId.isEmpty()) {
            return@withContext Result.failure(
                BackendApiException(
                    kind = BackendFailureKind.UNAUTHENTICATED,
                    statusCode = 401,
                    backendCode = "ACTOR_REQUIRED",
                    message = "Cần phiên đăng nhập hợp lệ để lấy mốc giờ máy chủ."
                )
            )
        }

        networkMutex.withLock {
            val preEval = evaluateAnchor(cleanUserId)
            if (!force && preEval.validAnchor != null && !preEval.isExpiringSoon) {
                return@withLock Result.success(preEval.validAnchor)
            }

            _uiState.value = _uiState.value.copy(syncing = true, lastSyncError = null)
            try {
                val json = httpClient.getJson(
                    pathWithQuery = AttendanceTimeContract.ENDPOINT_TIME,
                    requireOkTrue = true,
                    clearSessionOn401 = true
                )
                val receivedElapsedMs = elapsedRealtimeMs()
                val bootCount = bootCountProvider()
                val newAnchor = AttendanceTimeContract.parseTimeAnchorResponse(
                    json = json,
                    appUserId = cleanUserId,
                    receivedElapsedRealtimeMs = receivedElapsedMs,
                    bootCount = bootCount,
                    processSessionId = processSessionId
                )
                synchronized(stateLock) {
                    cachedAnchor = newAnchor
                    diskLoaded = true
                    persistAnchorLocked(newAnchor)
                    val eval = TimeAnchorEvaluation(
                        validAnchor = newAnchor,
                        invalidReason = TimeAnchorInvalidReason.NONE,
                        elapsedSinceAnchorMs = 0L,
                        estimatedServerNowEpochMs = newAnchor.serverTimeEpochMs,
                        isExpiringSoon = false
                    )
                    publishStateLocked(eval, lastError = null)
                }
                Result.success(newAnchor)
            } catch (e: BackendApiException) {
                synchronized(stateLock) {
                    if (e.kind == BackendFailureKind.UNAUTHENTICATED) {
                        clearAnchorLocked()
                    }
                    val postEval = evaluateAnchor(cleanUserId)
                    publishStateLocked(postEval, lastError = e.message)
                }
                Result.failure(e)
            } catch (e: Exception) {
                val msg = e.message ?: "Không thể đồng bộ mốc thời gian máy chủ."
                synchronized(stateLock) {
                    val postEval = evaluateAnchor(cleanUserId)
                    publishStateLocked(postEval, lastError = msg)
                }
                Result.failure(
                    BackendApiException(
                        kind = BackendFailureKind.CONTRACT_INVALID,
                        statusCode = null,
                        backendCode = "TIME_SYNC_FAILED",
                        message = msg,
                        cause = e
                    )
                )
            }
        }
    }

    fun clearAnchor() {
        synchronized(stateLock) {
            clearAnchorLocked()
            _uiState.value = TimeAnchorUiState(
                syncing = false,
                hasValidAnchor = false,
                invalidReason = TimeAnchorInvalidReason.MISSING
            )
        }
    }

    private fun clearAnchorLocked() {
        diskLoaded = true
        cachedAnchor = null
        runCatching { storageFile.delete() }
    }

    private fun ensureLoadedLocked() {
        if (diskLoaded) return
        diskLoaded = true
        if (!storageFile.exists() || !storageFile.isFile || storageFile.length() <= 0L) {
            return
        }
        try {
            val plainBytes = cipher.decrypt(storageFile.readBytes())
            val obj = JSONObject(plainBytes.toString(Charsets.UTF_8))
            val appUserId = obj.optString("app_user_id", "").trim()
            val serverTimeIso = obj.optString("server_time", "").trim()
            val serverTimeEpochMs = obj.optLong("server_time_epoch_ms", -1L)
            val expiresAtIso = obj.optString("expires_at", "").trim()
            val expiresAtEpochMs = obj.optLong("expires_at_epoch_ms", -1L)
            val timeToken = obj.optString("time_token", "").trim()
            val elapsedAtAnchor = obj.optLong("elapsed_realtime_at_anchor_ms", -1L)
            val bootCount = obj.optInt("boot_count_at_anchor", -1)
            val savedProcessId = obj.optString("process_session_id", "").trim()

            if (appUserId.isEmpty() ||
                serverTimeIso.isEmpty() ||
                expiresAtIso.isEmpty() ||
                timeToken.isEmpty() ||
                serverTimeEpochMs <= 0L ||
                expiresAtEpochMs <= serverTimeEpochMs ||
                elapsedAtAnchor < 0L
            ) {
                clearAnchorLocked()
                return
            }

            cachedAnchor = ServerTimeAnchor(
                appUserId = appUserId,
                serverTimeIso = serverTimeIso,
                serverTimeEpochMs = serverTimeEpochMs,
                expiresAtIso = expiresAtIso,
                expiresAtEpochMs = expiresAtEpochMs,
                timeToken = timeToken,
                elapsedRealtimeAtAnchorMs = elapsedAtAnchor,
                bootCountAtAnchor = bootCount,
                processSessionId = savedProcessId
            )
        } catch (_: Exception) {
            // Fail closed on corrupted or undecryptable anchor file
            clearAnchorLocked()
        }
    }

    private fun persistAnchorLocked(anchor: ServerTimeAnchor) {
        runCatching {
            val json = JSONObject().apply {
                put("app_user_id", anchor.appUserId)
                put("server_time", anchor.serverTimeIso)
                put("server_time_epoch_ms", anchor.serverTimeEpochMs)
                put("expires_at", anchor.expiresAtIso)
                put("expires_at_epoch_ms", anchor.expiresAtEpochMs)
                put("time_token", anchor.timeToken)
                put("elapsed_realtime_at_anchor_ms", anchor.elapsedRealtimeAtAnchorMs)
                put("boot_count_at_anchor", anchor.bootCountAtAnchor)
                put("process_session_id", anchor.processSessionId)
            }
            val encrypted = cipher.encrypt(json.toString().toByteArray(Charsets.UTF_8))
            storageFile.parentFile?.mkdirs()
            val tmpFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
            tmpFile.writeBytes(encrypted)
            if (!tmpFile.renameTo(storageFile)) {
                storageFile.writeBytes(encrypted)
                runCatching { tmpFile.delete() }
            }
        }
    }

    private fun publishStateLocked(eval: TimeAnchorEvaluation, lastError: String?) {
        _uiState.value = TimeAnchorUiState(
            syncing = false,
            hasValidAnchor = eval.validAnchor != null,
            invalidReason = eval.invalidReason,
            serverTimeIso = eval.validAnchor?.serverTimeIso,
            expiresAtIso = eval.validAnchor?.expiresAtIso,
            lastSyncError = lastError
        )
    }
}
