package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class AttendanceExplanationViewerProjection(
    val appUserId: String,
    val canSubmitExplanation: Boolean,
    val canApproveExplanations: Boolean,
    val isSharedNonEmployee: Boolean,
    val isFullAdmin: Boolean,
    val isBod: Boolean
)

data class AttendanceExplanationAccountSummaryProjection(
    val appUserId: String,
    val displayName: String?,
    val loginId: String?,
    val personnelCode: String?,
    val recruiterId: String?,
    val accountEnabled: Boolean
) {
    fun resolvedDisplayName(): String {
        return displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: loginId?.trim()?.takeIf { it.isNotEmpty() }
            ?: personnelCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: appUserId.take(8)
    }
}

data class AttendanceExplanationRoutingProjection(
    val routeKind: String,
    val unitKind: String,
    val primaryApprover: AttendanceExplanationAccountSummaryProjection?,
    val teamId: String?,
    val teamCode: String?,
    val teamName: String?,
    val fallbackReason: String?,
    val profileConfigured: Boolean
)

data class AttendanceSlotCheckinItemProjection(
    val checkinId: String,
    val sequenceInDay: Int,
    val capturedAt: String,
    val capturedAtLocal: String,
    val timeLocal: String,
    val timeVerified: Boolean,
    val assignedSlotCode: String?,
    val deltaMinutes: Int?,
    val overGraceMinutes: Int,
    val deviationStatus: String,
    val distanceTargetKind: String,
    val distanceStatus: String,
    val distanceMeters: Int?
)

data class AttendanceDaySlotExplanationSummaryProjection(
    val explanationId: String,
    val status: String,
    val originalSlotStatus: String,
    val reason: String,
    val submittedAt: String,
    val submittedAtLocal: String,
    val decidedAt: String?,
    val decidedAtLocal: String?,
    val decidedByDisplayName: String?,
    val decisionNote: String,
    val rowVersion: Int
)

data class AttendanceExplanationDaySlotEvaluationProjection(
    val slotCode: String,
    val slotIndex: Int,
    val slotLabel: String,
    val sessionCode: String,
    val targetTime: String,
    val graceBeforeMinutes: Int,
    val graceAfterMinutes: Int,
    val isLeaveCovered: Boolean,
    val originalSlotStatus: String,
    val slotStatus: String,
    val eligibleForExplanation: Boolean,
    val checkins: List<AttendanceSlotCheckinItemProjection>,
    val explanation: AttendanceDaySlotExplanationSummaryProjection?
)

data class AttendanceExplanationDayEvaluationProjection(
    val businessDate: String,
    val policyVersionUsed: Int,
    val slots: List<AttendanceExplanationDaySlotEvaluationProjection>,
    val policyProfileIdUsed: String = AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_ID,
    val policyProfileCodeUsed: String = AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_CODE,
    val policyProfileNameUsed: String = AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_NAME,
    val policyIsDefaultUsed: Boolean = true
) {
    fun resolvedProfileDisplayName(): String =
        policyProfileNameUsed.trim().ifEmpty { AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_NAME }

    fun resolvedProfileVersionLabel(): String =
        "${resolvedProfileDisplayName()} v$policyVersionUsed"

    fun resolvedProfileBadgeLabel(): String =
        "Khung giờ: ${resolvedProfileDisplayName()} v$policyVersionUsed (${slots.size} mốc)"

    fun resolvedSlotScheduleSummary(): String {
        if (slots.isEmpty()) return "Chưa có mốc giờ cấu hình"
        return slots.joinToString(" • ") { "${it.slotLabel} (${it.targetTime})" }
    }
}

data class AttendanceExplanationAuditEventProjection(
    val eventId: String,
    val eventType: String,
    val actorAppUserId: String?,
    val actorDisplayName: String?,
    val actorRoleContext: String,
    val fromStatus: String?,
    val toStatus: String,
    val note: String,
    val createdAt: String,
    val createdAtLocal: String
)

data class AttendanceExplanationRoutedTeamProjection(
    val teamId: String?,
    val teamCode: String?,
    val teamName: String?
)

data class AttendanceExplanationItemProjection(
    val explanationId: String,
    val appUserId: String,
    val requester: AttendanceExplanationAccountSummaryProjection,
    val businessDate: String,
    val slotCode: String,
    val slotLabel: String,
    val sessionCode: String,
    val targetTime: String,
    val graceBeforeMinutes: Int,
    val graceAfterMinutes: Int,
    val originalSlotStatus: String,
    val status: String,
    val reason: String,
    val routeKind: String,
    val unitKindSnapshot: String,
    val routedApprover: AttendanceExplanationAccountSummaryProjection?,
    val routedTeam: AttendanceExplanationRoutedTeamProjection,
    val fallbackReason: String?,
    val submittedAt: String,
    val submittedAtLocal: String,
    val decidedAt: String?,
    val decidedAtLocal: String?,
    val decidedBy: AttendanceExplanationAccountSummaryProjection?,
    val decisionNote: String,
    val rowVersion: Int,
    val canDecide: Boolean,
    val actualCheckins: List<AttendanceSlotCheckinItemProjection>,
    val events: List<AttendanceExplanationAuditEventProjection>,
    val slotIndex: Int = 1,
    val policyProfileIdUsed: String = AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_ID,
    val policyProfileCodeUsed: String = AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_CODE,
    val policyProfileNameUsed: String = AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_NAME,
    val policyVersionUsed: Int = 1,
    val effectiveSlotStatus: String = if (status.equals("APPROVED", ignoreCase = true)) {
        "EXPLAINED_APPROVED"
    } else {
        originalSlotStatus
    }
) {
    fun resolvedProfileDisplayName(): String =
        policyProfileNameUsed.trim().ifEmpty { AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_NAME }

    fun resolvedProfileVersionLabel(): String =
        "${resolvedProfileDisplayName()} v$policyVersionUsed"
}

data class AttendanceExplanationPaginationProjection(
    val limit: Int,
    val offset: Int,
    val totalRows: Int
)

data class AttendanceMyExplanationsOverviewProjection(
    val viewer: AttendanceExplanationViewerProjection,
    val routing: AttendanceExplanationRoutingProjection,
    val todayLocal: String,
    val selectedDate: String,
    val dayEvaluation: AttendanceExplanationDayEvaluationProjection,
    val pagination: AttendanceExplanationPaginationProjection,
    val items: List<AttendanceExplanationItemProjection>
)

data class AttendanceExplanationApprovalInboxViewerProjection(
    val appUserId: String,
    val canApproveExplanations: Boolean,
    val isFullAdmin: Boolean,
    val isBod: Boolean
)

data class AttendanceExplanationApprovalInboxPaginationProjection(
    val limit: Int,
    val offset: Int,
    val totalRows: Int,
    val pendingCount: Int
)

data class AttendanceExplanationApprovalInboxProjection(
    val viewer: AttendanceExplanationApprovalInboxViewerProjection,
    val pagination: AttendanceExplanationApprovalInboxPaginationProjection,
    val items: List<AttendanceExplanationItemProjection>
)

data class SubmitAttendanceExplanationResultProjection(
    val replayed: Boolean,
    val explanation: AttendanceExplanationItemProjection
)

data class DecideAttendanceExplanationResultProjection(
    val replayed: Boolean,
    val explanation: AttendanceExplanationItemProjection
)

object AttendanceExplanationContract {
    const val REASON_MIN_LENGTH = 3
    const val REASON_MAX_LENGTH = 500
    const val DECISION_NOTE_MAX_LENGTH = 500
    const val DEFAULT_PAGE_LIMIT = 50

    const val DEFAULT_SLOT_POLICY_PROFILE_ID = "00000000-0000-4000-8000-000000000001"
    const val DEFAULT_SLOT_POLICY_PROFILE_CODE = "DEFAULT"
    const val DEFAULT_SLOT_POLICY_PROFILE_NAME = "Mặc định"

    val APPROVAL_STATUS_FILTERS = listOf("PENDING", "APPROVED", "REJECTED", "ALL")
    val DECISION_ACTIONS = setOf("APPROVE", "REJECT")

    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val SLOT_CODE_REGEX = Regex("^SLOT_(?:[1-9]|1[0-2])$")
    private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    val ERROR_MESSAGES: Map<String, String> = mapOf(
        "ATTENDANCE_EXPLANATION_ACCESS_DENIED" to "Tài khoản không thuộc diện gửi hoặc tra cứu giải trình chấm công.",
        "ATTENDANCE_EXPLANATION_SUBMIT_DENIED" to "Tài khoản không có quyền gửi giải trình chấm công.",
        "ATTENDANCE_EXPLANATION_APPROVAL_ACCESS_DENIED" to "Bạn không có quyền truy cập hộp thư duyệt giải trình chấm công.",
        "ATTENDANCE_EXPLANATION_DECIDE_DENIED" to "Bạn không thuộc thẩm quyền duyệt giải trình này.",
        "ATTENDANCE_EXPLANATION_DECISION_DENIED" to "Bạn không có quyền xử lý giải trình chấm công này.",
        "ATTENDANCE_EXPLANATION_SELF_APPROVAL_FORBIDDEN" to "Không được tự duyệt giải trình chấm công của chính mình.",
        "ATTENDANCE_EXPLANATION_NOT_FOUND" to "Không tìm thấy giải trình chấm công tương ứng.",
        "ATTENDANCE_EXPLANATION_ALREADY_SUBMITTED" to "Mốc chấm công này đã có giải trình. Mỗi mốc chỉ được gửi giải trình một lần.",
        "ATTENDANCE_EXPLANATION_ALREADY_EXISTS" to "Mốc chấm công này đã có giải trình (mỗi mốc trong ngày chỉ được gửi 1 giải trình).",
        "ATTENDANCE_EXPLANATION_SLOT_NOT_ELIGIBLE" to "Mốc chấm công này không ở trạng thái thiếu chấm hoặc ngoài khung cho phép để gửi giải trình.",
        "ATTENDANCE_EXPLANATION_SLOT_INVALID" to "Mốc chấm công cần giải trình không hợp lệ.",
        "ATTENDANCE_EXPLANATION_DATE_INVALID" to "Ngày nghiệp vụ giải trình không hợp lệ.",
        "ATTENDANCE_EXPLANATION_DATE_FUTURE" to "Chưa thể gửi giải trình cho ngày làm việc trong tương lai.",
        "ATTENDANCE_EXPLANATION_FUTURE_DATE_NOT_ALLOWED" to "Không thể gửi giải trình cho ngày trong tương lai.",
        "ATTENDANCE_EXPLANATION_APPROVER_UNCONFIGURED" to "Chưa cấu hình người duyệt cho bộ phận/nhóm của bạn. Vui lòng liên hệ Nhân sự hoặc Admin.",
        "ATTENDANCE_EXPLANATION_REASON_INVALID" to "Lý do giải trình phải từ 3 đến 500 ký tự.",
        "ATTENDANCE_EXPLANATION_NOTE_INVALID" to "Ghi chú phản hồi không được vượt quá 500 ký tự.",
        "ATTENDANCE_EXPLANATION_DECISION_INVALID" to "Quyết định duyệt giải trình không hợp lệ.",
        "ATTENDANCE_EXPLANATION_VERSION_CONFLICT" to "Giải trình đã được cập nhật bởi phiên làm việc khác. Dữ liệu đã được tải lại.",
        "ATTENDANCE_EXPLANATION_STATE_INVALID" to "Giải trình đã được xử lý trước đó và không còn ở trạng thái chờ duyệt.",
        "ATTENDANCE_EXPLANATION_ALREADY_DECIDED" to "Giải trình đã được xử lý trước đó và không còn ở trạng thái chờ duyệt.",
        "IDEMPOTENCY_KEY_INVALID" to "Khóa chống gửi trùng (Idempotency-Key) không hợp lệ.",
        "IDEMPOTENCY_CONFLICT" to "Khóa chống gửi trùng đã được dùng cho một thao tác khác.",
        "ATTENDANCE_REPORT_UNAVAILABLE" to "Dịch vụ giải trình chấm công tạm thời gián đoạn. Vui lòng thử lại sau."
    )

    fun isConflictError(statusCode: Int?, backendCode: String?): Boolean {
        val code = backendCode?.trim()?.uppercase(Locale.ROOT)
        return statusCode == 409 ||
            code == "ATTENDANCE_EXPLANATION_VERSION_CONFLICT" ||
            code == "ATTENDANCE_EXPLANATION_STATE_INVALID" ||
            code == "ATTENDANCE_EXPLANATION_ALREADY_DECIDED" ||
            code == "ATTENDANCE_EXPLANATION_ALREADY_SUBMITTED" ||
            code == "ATTENDANCE_EXPLANATION_ALREADY_EXISTS" ||
            code == "ATTENDANCE_EXPLANATION_SLOT_NOT_ELIGIBLE" ||
            code == "IDEMPOTENCY_CONFLICT"
    }

    fun newIdempotencyKey(): String {
        return UUID.randomUUID().toString().lowercase(Locale.ROOT)
    }

    fun isValidIdempotencyKey(key: String?): Boolean {
        val clean = key?.trim()?.lowercase(Locale.ROOT) ?: return false
        return UUID_REGEX.matches(clean)
    }

    fun todayVietnamIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        return sdf.format(Date(nowMillis))
    }

    fun isValidIsoDate(dateStr: String?): Boolean {
        val clean = dateStr?.trim() ?: return false
        if (!ISO_DATE_REGEX.matches(clean)) return false
        val parts = clean.split("-")
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        if (year < 2000 || year > 2100 || month !in 1..12 || day !in 1..31) return false
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(clean) != null
        }.getOrDefault(false)
    }

    fun isValidSlotCode(slotCode: String?): Boolean {
        val clean = slotCode?.trim()?.uppercase(Locale.ROOT) ?: return false
        return SLOT_CODE_REGEX.matches(clean)
    }

    fun validateSubmitInput(
        businessDate: String,
        slotCode: String,
        reason: String,
        todayLocal: String? = null
    ): String? {
        val cleanDate = businessDate.trim()
        if (!isValidIsoDate(cleanDate)) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_DATE_INVALID")
        }
        val referenceToday = todayLocal?.trim()?.takeIf { isValidIsoDate(it) } ?: todayVietnamIsoDate()
        if (cleanDate > referenceToday) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_DATE_FUTURE")
        }
        if (!isValidSlotCode(slotCode)) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_SLOT_INVALID")
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < REASON_MIN_LENGTH ||
            cleanReason.length > REASON_MAX_LENGTH ||
            CONTROL_CHARS_REGEX.containsMatchIn(cleanReason)
        ) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_REASON_INVALID")
        }
        return null
    }

    fun validateDecisionInput(
        actorAppUserId: String?,
        item: AttendanceExplanationItemProjection,
        decision: String,
        decisionNote: String?
    ): String? {
        val cleanActorId = actorAppUserId?.trim().orEmpty()
        if (cleanActorId.isNotEmpty() && cleanActorId.equals(item.appUserId.trim(), ignoreCase = true)) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_SELF_APPROVAL_FORBIDDEN")
        }
        if (!item.canDecide) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_DECIDE_DENIED")
        }
        if (!item.status.equals("PENDING", ignoreCase = true)) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_STATE_INVALID")
        }
        val cleanDecision = decision.trim().uppercase(Locale.ROOT)
        if (cleanDecision !in DECISION_ACTIONS) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_DECISION_INVALID")
        }
        val cleanNote = decisionNote?.trim().orEmpty()
        if (cleanNote.length > DECISION_NOTE_MAX_LENGTH || CONTROL_CHARS_REGEX.containsMatchIn(cleanNote)) {
            return ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_NOTE_INVALID")
        }
        return null
    }

    fun explanationStatusLabel(status: String): String =
        when (status.trim().uppercase(Locale.ROOT)) {
            "PENDING" -> "Chờ duyệt"
            "APPROVED" -> "Đã duyệt"
            "REJECTED" -> "Từ chối"
            else -> status
        }

    fun originalSlotStatusLabel(originalSlotStatus: String): String =
        when (originalSlotStatus.trim().uppercase(Locale.ROOT)) {
            "MISSING" -> "Thiếu chấm công"
            "OUT_OF_GRACE" -> "Ngoài khung giờ"
            "ON_TIME" -> "Đúng giờ"
            "LEAVE_COVERED", "LEAVE_EXEMPT" -> "Miễn chấm (nghỉ được duyệt)"
            "UPCOMING", "NOT_APPLICABLE" -> "Chưa đến mốc / Không áp dụng"
            else -> originalSlotStatus
        }

    fun slotStatusLabel(slotStatus: String): String =
        when (slotStatus.trim().uppercase(Locale.ROOT)) {
            "ON_TIME" -> "Đúng giờ"
            "OUT_OF_GRACE" -> "Ngoài khung giờ"
            "MISSING" -> "Thiếu chấm công"
            "LEAVE_COVERED", "LEAVE_EXEMPT" -> "Miễn chấm (nghỉ được duyệt)"
            "UPCOMING", "NOT_APPLICABLE" -> "Chưa đến mốc"
            "EXPLAINED_PENDING" -> "Đang chờ duyệt giải trình"
            "EXPLAINED_APPROVED" -> "Giải trình được duyệt"
            "EXPLAINED_REJECTED" -> "Giải trình bị từ chối"
            else -> slotStatus
        }

    fun sessionLabel(sessionCode: String): String =
        when (sessionCode.trim().uppercase(Locale.ROOT)) {
            "AM" -> "Buổi sáng"
            "PM" -> "Buổi chiều"
            else -> sessionCode
        }

    fun formatDeviationBadge(checkin: AttendanceSlotCheckinItemProjection): String? {
        val minutes = if (checkin.overGraceMinutes > 0) {
            checkin.overGraceMinutes
        } else {
            kotlin.math.abs(checkin.deltaMinutes ?: 0)
        }
        return when (checkin.deviationStatus.trim().uppercase(Locale.ROOT)) {
            "ON_TIME" -> "Đúng giờ"
            "EARLY" -> "Sớm ${minutes}p"
            "LATE" -> "Muộn ${minutes}p"
            else -> null
        }
    }

    fun formatGraceWindow(
        targetTime: String,
        graceBeforeMinutes: Int,
        graceAfterMinutes: Int
    ): String {
        return "$targetTime (-${graceBeforeMinutes}p / +${graceAfterMinutes}p)"
    }

    fun auditEventTypeLabel(eventType: String): String =
        when (eventType.trim().uppercase(Locale.ROOT)) {
            "SUBMITTED" -> "Gửi giải trình"
            "APPROVED" -> "Duyệt giải trình"
            "REJECTED" -> "Từ chối giải trình"
            else -> eventType
        }

    fun statusFilterLabel(filter: String): String =
        when (filter.trim().uppercase(Locale.ROOT)) {
            "PENDING" -> "Chờ duyệt"
            "APPROVED" -> "Đã duyệt"
            "REJECTED" -> "Từ chối"
            "ALL" -> "Tất cả"
            else -> filter
        }
}

class AttendanceExplanationService(
    private val httpClient: BackendHttpClient
) {
    suspend fun fetchMyExplanationsOverview(
        businessDate: String? = null,
        limit: Int = AttendanceExplanationContract.DEFAULT_PAGE_LIMIT,
        offset: Int = 0
    ): AttendanceMyExplanationsOverviewProjection = withContext(Dispatchers.IO) {
        try {
            val safeLimit = limit.coerceIn(1, 200)
            val safeOffset = offset.coerceAtLeast(0)
            val queryParts = mutableListOf<String>()
            val cleanDate = businessDate?.trim()?.takeIf { AttendanceExplanationContract.isValidIsoDate(it) }
            if (cleanDate != null) {
                queryParts.add("business_date=$cleanDate")
            }
            queryParts.add("limit=$safeLimit")
            queryParts.add("offset=$safeOffset")
            val path = "/api/attendance/explanations?${queryParts.joinToString("&")}"
            val json = httpClient.getJson(
                pathWithQuery = path,
                requireOkTrue = true,
                clearSessionOn401 = true
            )
            parseMyExplanationsOverview(json)
        } catch (e: BackendApiException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = null,
                backendCode = "ATTENDANCE_EXPLANATION_OVERVIEW_PARSE_ERROR",
                message = "Dữ liệu giải trình chấm công từ máy chủ không hợp lệ.",
                cause = e
            )
        }
    }

    suspend fun submitExplanation(
        businessDate: String,
        slotCode: String,
        reason: String,
        idempotencyKey: String = AttendanceExplanationContract.newIdempotencyKey()
    ): SubmitAttendanceExplanationResultProjection = withContext(Dispatchers.IO) {
        val cleanDate = businessDate.trim()
        val cleanSlot = slotCode.trim().uppercase(Locale.ROOT)
        val cleanReason = reason.trim()
        val cleanKey = idempotencyKey.trim().lowercase(Locale.ROOT).let {
            if (AttendanceExplanationContract.isValidIdempotencyKey(it)) {
                it
            } else {
                AttendanceExplanationContract.newIdempotencyKey()
            }
        }
        val validationError = AttendanceExplanationContract.validateSubmitInput(
            businessDate = cleanDate,
            slotCode = cleanSlot,
            reason = cleanReason
        )
        if (validationError != null) {
            throw BackendApiException(
                kind = BackendFailureKind.VALIDATION,
                statusCode = 400,
                backendCode = "ATTENDANCE_EXPLANATION_VALIDATION_FAILED",
                message = validationError
            )
        }

        val body = JSONObject().apply {
            put("business_date", cleanDate)
            put("slot_code", cleanSlot)
            put("reason", cleanReason)
            put("idempotency_key", cleanKey)
        }
        try {
            val json = httpClient.postJson(
                path = "/api/attendance/explanations",
                bodyJson = body,
                idempotencyKey = cleanKey,
                requireOkTrue = true,
                clearSessionOn401 = true
            )
            val explanationObj = json.optJSONObject("explanation")
                ?: throw IllegalArgumentException("Missing explanation in submit response")
            SubmitAttendanceExplanationResultProjection(
                replayed = json.optBoolean("replayed", false),
                explanation = parseExplanationItem(explanationObj)
            )
        } catch (e: BackendApiException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = null,
                backendCode = "ATTENDANCE_EXPLANATION_SUBMIT_PARSE_ERROR",
                message = "Phản hồi gửi giải trình chấm công từ máy chủ không hợp lệ.",
                cause = e
            )
        }
    }

    suspend fun fetchApprovalInbox(
        statusFilter: String = "PENDING",
        limit: Int = AttendanceExplanationContract.DEFAULT_PAGE_LIMIT,
        offset: Int = 0
    ): AttendanceExplanationApprovalInboxProjection = withContext(Dispatchers.IO) {
        val cleanFilter = statusFilter.trim().uppercase(Locale.ROOT).let {
            if (it in AttendanceExplanationContract.APPROVAL_STATUS_FILTERS) it else "PENDING"
        }
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        try {
            val path = "/api/attendance/explanations/approvals?status=$cleanFilter&limit=$safeLimit&offset=$safeOffset"
            val json = httpClient.getJson(
                pathWithQuery = path,
                requireOkTrue = true,
                clearSessionOn401 = true
            )
            parseApprovalInbox(json)
        } catch (e: BackendApiException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = null,
                backendCode = "ATTENDANCE_EXPLANATION_INBOX_PARSE_ERROR",
                message = "Dữ liệu hộp thư xét duyệt giải trình từ máy chủ không hợp lệ.",
                cause = e
            )
        }
    }

    suspend fun decideExplanation(
        explanationId: String,
        decision: String,
        expectedRowVersion: Int,
        decisionNote: String?,
        idempotencyKey: String = AttendanceExplanationContract.newIdempotencyKey()
    ): DecideAttendanceExplanationResultProjection = withContext(Dispatchers.IO) {
        val cleanId = explanationId.trim()
        val cleanDecision = decision.trim().uppercase(Locale.ROOT)
        val cleanNote = decisionNote?.trim().orEmpty()
        if (cleanId.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.VALIDATION,
                statusCode = 400,
                backendCode = "ATTENDANCE_EXPLANATION_NOT_FOUND",
                message = AttendanceExplanationContract.ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_NOT_FOUND")
            )
        }
        if (cleanDecision !in AttendanceExplanationContract.DECISION_ACTIONS) {
            throw BackendApiException(
                kind = BackendFailureKind.VALIDATION,
                statusCode = 400,
                backendCode = "ATTENDANCE_EXPLANATION_DECISION_INVALID",
                message = AttendanceExplanationContract.ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_DECISION_INVALID")
            )
        }
        if (cleanNote.length > AttendanceExplanationContract.DECISION_NOTE_MAX_LENGTH) {
            throw BackendApiException(
                kind = BackendFailureKind.VALIDATION,
                statusCode = 400,
                backendCode = "ATTENDANCE_EXPLANATION_NOTE_INVALID",
                message = AttendanceExplanationContract.ERROR_MESSAGES.getValue("ATTENDANCE_EXPLANATION_NOTE_INVALID")
            )
        }
        val cleanKey = idempotencyKey.trim().lowercase(Locale.ROOT).let {
            if (AttendanceExplanationContract.isValidIdempotencyKey(it)) {
                it
            } else {
                AttendanceExplanationContract.newIdempotencyKey()
            }
        }
        val body = JSONObject().apply {
            put("decision", cleanDecision)
            put("expected_row_version", expectedRowVersion.coerceAtLeast(1))
            put("decision_note", if (cleanNote.isEmpty()) JSONObject.NULL else cleanNote)
            put("idempotency_key", cleanKey)
        }
        try {
            val json = httpClient.postJson(
                path = "/api/attendance/explanations/approvals/$cleanId/decision",
                bodyJson = body,
                idempotencyKey = cleanKey,
                requireOkTrue = true,
                clearSessionOn401 = true
            )
            val explanationObj = json.optJSONObject("explanation")
                ?: throw IllegalArgumentException("Missing explanation in decision response")
            DecideAttendanceExplanationResultProjection(
                replayed = json.optBoolean("replayed", false),
                explanation = parseExplanationItem(explanationObj)
            )
        } catch (e: BackendApiException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = null,
                backendCode = "ATTENDANCE_EXPLANATION_DECISION_PARSE_ERROR",
                message = "Phản hồi xét duyệt giải trình chấm công từ máy chủ không hợp lệ.",
                cause = e
            )
        }
    }

    companion object {
        private fun JSONObject.optNullableString(key: String): String? {
            if (isNull(key)) return null
            return optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }
        }

        private fun parseAccountSummary(obj: JSONObject): AttendanceExplanationAccountSummaryProjection {
            return AttendanceExplanationAccountSummaryProjection(
                appUserId = obj.optString("app_user_id", "").trim(),
                displayName = obj.optNullableString("display_name"),
                loginId = obj.optNullableString("login_id"),
                personnelCode = obj.optNullableString("personnel_code"),
                recruiterId = obj.optNullableString("recruiter_id"),
                accountEnabled = obj.optBoolean("account_enabled", true)
            )
        }

        private fun parseSlotCheckin(obj: JSONObject): AttendanceSlotCheckinItemProjection {
            return AttendanceSlotCheckinItemProjection(
                checkinId = obj.optString("checkin_id", "").trim(),
                sequenceInDay = obj.optInt("sequence_in_day", 1),
                capturedAt = obj.optString("captured_at", "").trim(),
                capturedAtLocal = obj.optString("captured_at_local", "").trim(),
                timeLocal = obj.optString("time_local", "").trim(),
                timeVerified = obj.optBoolean("time_verified", true),
                assignedSlotCode = obj.optNullableString("assigned_slot_code"),
                deltaMinutes = if (obj.isNull("delta_minutes")) null else obj.optInt("delta_minutes"),
                overGraceMinutes = obj.optInt("over_grace_minutes", 0),
                deviationStatus = obj.optString("deviation_status", "ON_TIME").trim(),
                distanceTargetKind = obj.optString("distance_target_kind", "NONE").trim(),
                distanceStatus = obj.optString("distance_status", "UNAVAILABLE").trim(),
                distanceMeters = if (obj.isNull("distance_meters")) null else obj.optInt("distance_meters")
            )
        }

        private fun parseSlotCheckinList(arr: JSONArray?): List<AttendanceSlotCheckinItemProjection> {
            if (arr == null) return emptyList()
            val list = mutableListOf<AttendanceSlotCheckinItemProjection>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list.add(parseSlotCheckin(item))
            }
            return list
        }

        private fun parseDaySlotExplanationSummary(
            obj: JSONObject?
        ): AttendanceDaySlotExplanationSummaryProjection? {
            if (obj == null) return null
            val id = obj.optString("explanation_id", "").trim()
            if (id.isEmpty()) return null
            return AttendanceDaySlotExplanationSummaryProjection(
                explanationId = id,
                status = obj.optString("status", "PENDING").trim(),
                originalSlotStatus = obj.optString("original_slot_status", "MISSING").trim(),
                reason = obj.optString("reason", "").trim(),
                submittedAt = obj.optString("submitted_at", "").trim(),
                submittedAtLocal = obj.optString("submitted_at_local", "").trim(),
                decidedAt = obj.optNullableString("decided_at"),
                decidedAtLocal = obj.optNullableString("decided_at_local"),
                decidedByDisplayName = obj.optNullableString("decided_by_display_name"),
                decisionNote = obj.optString("decision_note", "").trim(),
                rowVersion = obj.optInt("row_version", 1).coerceAtLeast(1)
            )
        }

        private fun parseDaySlotEvaluation(
            obj: JSONObject
        ): AttendanceExplanationDaySlotEvaluationProjection {
            return AttendanceExplanationDaySlotEvaluationProjection(
                slotCode = obj.optString("slot_code", "").trim(),
                slotIndex = obj.optInt("slot_index", 1),
                slotLabel = obj.optString("slot_label", obj.optString("slot_code", "")).trim(),
                sessionCode = obj.optString("session_code", "AM").trim(),
                targetTime = obj.optString("target_time", "00:00").trim(),
                graceBeforeMinutes = obj.optInt("grace_before_minutes", 15),
                graceAfterMinutes = obj.optInt("grace_after_minutes", 15),
                isLeaveCovered = obj.optBoolean("is_leave_covered", false),
                originalSlotStatus = obj.optString("original_slot_status", "MISSING").trim(),
                slotStatus = obj.optString("slot_status", "MISSING").trim(),
                eligibleForExplanation = obj.optBoolean("eligible_for_explanation", false),
                checkins = parseSlotCheckinList(obj.optJSONArray("checkins")),
                explanation = if (obj.isNull("explanation")) {
                    null
                } else {
                    parseDaySlotExplanationSummary(obj.optJSONObject("explanation"))
                }
            )
        }

        private fun parseAuditEvent(obj: JSONObject): AttendanceExplanationAuditEventProjection {
            val actorObj = if (obj.isNull("actor")) null else obj.optJSONObject("actor")
            val actorSummary = actorObj?.let { parseAccountSummary(it) }
            return AttendanceExplanationAuditEventProjection(
                eventId = obj.optNullableString("audit_event_id")
                    ?: obj.optString("event_id", "").trim(),
                eventType = obj.optNullableString("action")
                    ?: obj.optString("event_type", "").trim(),
                actorAppUserId = actorSummary?.appUserId?.takeIf { it.isNotEmpty() }
                    ?: obj.optNullableString("actor_app_user_id"),
                actorDisplayName = actorSummary?.resolvedDisplayName()?.takeIf { it.isNotEmpty() }
                    ?: obj.optNullableString("actor_display_name"),
                actorRoleContext = obj.optString("actor_role_context", "").trim(),
                fromStatus = obj.optNullableString("before_status")
                    ?: obj.optNullableString("from_status"),
                toStatus = obj.optNullableString("after_status")
                    ?: obj.optString("to_status", "").trim(),
                note = obj.optString("note", "").trim(),
                createdAt = obj.optString("created_at", "").trim(),
                createdAtLocal = obj.optString("created_at_local", "").trim()
            )
        }

        internal fun parseExplanationItem(obj: JSONObject): AttendanceExplanationItemProjection {
            val requesterObj = obj.optJSONObject("requester") ?: JSONObject()
            val routedApproverObj = when {
                !obj.isNull("primary_approver") -> obj.optJSONObject("primary_approver")
                !obj.isNull("routed_approver") -> obj.optJSONObject("routed_approver")
                else -> null
            }
            val routedTeamObj = obj.optJSONObject("routed_team") ?: JSONObject()
            val decidedByObj = if (obj.isNull("decided_by")) null else obj.optJSONObject("decided_by")
            val eventsArr = obj.optJSONArray("audit_events") ?: obj.optJSONArray("events")
            val events = mutableListOf<AttendanceExplanationAuditEventProjection>()
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    val evObj = eventsArr.optJSONObject(i) ?: continue
                    events.add(parseAuditEvent(evObj))
                }
            }
            val checkinsArr = obj.optJSONArray("related_checkins") ?: obj.optJSONArray("actual_checkins")
            val slotCode = obj.optString("slot_code", "").trim()
            val defaultSlotIndex = slotCode.removePrefix("SLOT_").toIntOrNull()?.coerceIn(1, 12) ?: 1
            val status = obj.optString("status", "PENDING").trim()
            val originalSlotStatus = obj.optString("original_slot_status", "MISSING").trim()
            val policyProfileIdUsed = obj.optNullableString("policy_profile_id_used")
                ?.lowercase(Locale.ROOT)
                ?: AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_ID
            val policyProfileCodeUsed = obj.optNullableString("policy_profile_code_used")
                ?: AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_CODE
            val policyProfileNameUsed = obj.optNullableString("policy_profile_name_used")
                ?: AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_NAME
            val policyVersionUsed = obj.optInt("policy_version_used", 1).coerceAtLeast(1)
            val effectiveSlotStatus = obj.optNullableString("effective_slot_status")
                ?: if (status.equals("APPROVED", ignoreCase = true)) "EXPLAINED_APPROVED" else originalSlotStatus

            return AttendanceExplanationItemProjection(
                explanationId = obj.optString("explanation_id", "").trim(),
                appUserId = obj.optString("app_user_id", requesterObj.optString("app_user_id", "")).trim(),
                requester = parseAccountSummary(requesterObj),
                businessDate = obj.optString("business_date", "").trim(),
                slotCode = slotCode,
                slotLabel = obj.optString("slot_label", slotCode).trim(),
                sessionCode = obj.optString("session_code", "AM").trim(),
                targetTime = obj.optString("target_time", "00:00").trim(),
                graceBeforeMinutes = obj.optInt("grace_before_minutes", 15),
                graceAfterMinutes = obj.optInt("grace_after_minutes", 15),
                originalSlotStatus = originalSlotStatus,
                status = status,
                reason = obj.optString("reason", "").trim(),
                routeKind = obj.optString("route_kind", "TEAM_LEADER").trim(),
                unitKindSnapshot = obj.optString("unit_kind_snapshot", "RECRUITMENT").trim(),
                routedApprover = routedApproverObj?.let { parseAccountSummary(it) },
                routedTeam = AttendanceExplanationRoutedTeamProjection(
                    teamId = routedTeamObj.optNullableString("team_id"),
                    teamCode = routedTeamObj.optNullableString("team_code"),
                    teamName = routedTeamObj.optNullableString("team_name")
                ),
                fallbackReason = obj.optNullableString("fallback_reason"),
                submittedAt = obj.optString("submitted_at", "").trim(),
                submittedAtLocal = obj.optString("submitted_at_local", "").trim(),
                decidedAt = obj.optNullableString("decided_at"),
                decidedAtLocal = obj.optNullableString("decided_at_local"),
                decidedBy = decidedByObj?.let { parseAccountSummary(it) },
                decisionNote = obj.optString("decision_note", "").trim(),
                rowVersion = obj.optInt("row_version", 1).coerceAtLeast(1),
                canDecide = obj.optBoolean("can_decide", false),
                actualCheckins = parseSlotCheckinList(checkinsArr),
                events = events,
                slotIndex = obj.optInt("slot_index", defaultSlotIndex).coerceIn(1, 12),
                policyProfileIdUsed = policyProfileIdUsed,
                policyProfileCodeUsed = policyProfileCodeUsed,
                policyProfileNameUsed = policyProfileNameUsed,
                policyVersionUsed = policyVersionUsed,
                effectiveSlotStatus = effectiveSlotStatus
            )
        }

        internal fun parseMyExplanationsOverview(
            json: JSONObject
        ): AttendanceMyExplanationsOverviewProjection {
            val viewerObj = json.optJSONObject("viewer")
                ?: throw IllegalArgumentException("Missing viewer in /api/attendance/explanations response")
            val routingObj = json.optJSONObject("routing") ?: JSONObject()
            val primaryApproverObj = if (routingObj.isNull("primary_approver")) {
                null
            } else {
                routingObj.optJSONObject("primary_approver")
            }
            val dayEvalObj = json.optJSONObject("day_evaluation") ?: JSONObject()
            val slotsArr = dayEvalObj.optJSONArray("slots")
            val slots = mutableListOf<AttendanceExplanationDaySlotEvaluationProjection>()
            if (slotsArr != null) {
                for (i in 0 until slotsArr.length()) {
                    val slotObj = slotsArr.optJSONObject(i) ?: continue
                    slots.add(parseDaySlotEvaluation(slotObj))
                }
            }
            slots.sortBy { it.slotIndex }

            val paginationObj = json.optJSONObject("pagination") ?: JSONObject()
            val itemsArr = json.optJSONArray("items")
            val items = mutableListOf<AttendanceExplanationItemProjection>()
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.optJSONObject(i) ?: continue
                    items.add(parseExplanationItem(itemObj))
                }
            }

            val todayLocal = json.optString("today_local", AttendanceExplanationContract.todayVietnamIsoDate()).trim()
            val selectedDate = json.optString("selected_date", todayLocal).trim()
            val evalProfileId = dayEvalObj.optNullableString("policy_profile_id_used")
                ?.lowercase(Locale.ROOT)
                ?: AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_ID
            val evalProfileCode = dayEvalObj.optNullableString("policy_profile_code_used")
                ?: AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_CODE
            val evalProfileName = dayEvalObj.optNullableString("policy_profile_name_used")
                ?: AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_NAME
            val evalIsDefault = if (dayEvalObj.has("policy_is_default_used") && !dayEvalObj.isNull("policy_is_default_used")) {
                dayEvalObj.optBoolean("policy_is_default_used", true)
            } else {
                evalProfileCode.equals(AttendanceExplanationContract.DEFAULT_SLOT_POLICY_PROFILE_CODE, ignoreCase = true)
            }

            return AttendanceMyExplanationsOverviewProjection(
                viewer = AttendanceExplanationViewerProjection(
                    appUserId = viewerObj.optString("app_user_id", "").trim(),
                    canSubmitExplanation = viewerObj.optBoolean("can_submit_explanation", true),
                    canApproveExplanations = viewerObj.optBoolean("can_approve_explanations", false),
                    isSharedNonEmployee = viewerObj.optBoolean("is_shared_non_employee", false),
                    isFullAdmin = viewerObj.optBoolean("is_full_admin", false),
                    isBod = viewerObj.optBoolean("is_bod", false)
                ),
                routing = AttendanceExplanationRoutingProjection(
                    routeKind = routingObj.optString("route_kind", "TEAM_LEADER").trim(),
                    unitKind = routingObj.optString("unit_kind", "RECRUITMENT").trim(),
                    primaryApprover = primaryApproverObj?.let { parseAccountSummary(it) },
                    teamId = routingObj.optNullableString("team_id"),
                    teamCode = routingObj.optNullableString("team_code"),
                    teamName = routingObj.optNullableString("team_name"),
                    fallbackReason = routingObj.optNullableString("fallback_reason"),
                    profileConfigured = routingObj.optBoolean("profile_configured", true)
                ),
                todayLocal = todayLocal,
                selectedDate = selectedDate,
                dayEvaluation = AttendanceExplanationDayEvaluationProjection(
                    businessDate = dayEvalObj.optString("business_date", selectedDate).trim(),
                    policyVersionUsed = dayEvalObj.optInt("policy_version_used", 1).coerceAtLeast(1),
                    slots = slots,
                    policyProfileIdUsed = evalProfileId,
                    policyProfileCodeUsed = evalProfileCode,
                    policyProfileNameUsed = evalProfileName,
                    policyIsDefaultUsed = evalIsDefault
                ),
                pagination = AttendanceExplanationPaginationProjection(
                    limit = paginationObj.optInt("limit", AttendanceExplanationContract.DEFAULT_PAGE_LIMIT),
                    offset = paginationObj.optInt("offset", 0),
                    totalRows = paginationObj.optInt("total_rows", items.size)
                ),
                items = items
            )
        }

        internal fun parseApprovalInbox(
            json: JSONObject
        ): AttendanceExplanationApprovalInboxProjection {
            val viewerObj = json.optJSONObject("viewer")
                ?: throw IllegalArgumentException("Missing viewer in /api/attendance/explanations/approvals response")
            val paginationObj = json.optJSONObject("pagination") ?: JSONObject()
            val itemsArr = json.optJSONArray("items")
            val items = mutableListOf<AttendanceExplanationItemProjection>()
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.optJSONObject(i) ?: continue
                    items.add(parseExplanationItem(itemObj))
                }
            }
            return AttendanceExplanationApprovalInboxProjection(
                viewer = AttendanceExplanationApprovalInboxViewerProjection(
                    appUserId = viewerObj.optString("app_user_id", "").trim(),
                    canApproveExplanations = viewerObj.optBoolean("can_approve_explanations", false),
                    isFullAdmin = viewerObj.optBoolean("is_full_admin", false),
                    isBod = viewerObj.optBoolean("is_bod", false)
                ),
                pagination = AttendanceExplanationApprovalInboxPaginationProjection(
                    limit = paginationObj.optInt("limit", AttendanceExplanationContract.DEFAULT_PAGE_LIMIT),
                    offset = paginationObj.optInt("offset", 0),
                    totalRows = paginationObj.optInt("total_rows", items.size),
                    pendingCount = paginationObj.optInt(
                        "pending_count",
                        items.count { it.status.equals("PENDING", ignoreCase = true) }
                    )
                ),
                items = items
            )
        }
    }
}
