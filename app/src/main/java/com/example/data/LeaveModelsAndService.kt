package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class AttendanceAreaTab(val key: String, val label: String) {
    CHECK_IN("CHECK_IN", "Chấm công"),
    LEAVE_REQUEST("LEAVE_REQUEST", "Xin nghỉ"),
    LEAVE_APPROVAL("LEAVE_APPROVAL", "Duyệt nghỉ")
}

data class LeaveAccountProjection(
    val appUserId: String,
    val displayName: String,
    val loginId: String?,
    val personnelCode: String?,
    val recruiterId: String?,
    val accountEnabled: Boolean
)

data class LeaveAuthorityProjection(
    val appUserId: String,
    val isSharedNonEmployee: Boolean,
    val isFullAdmin: Boolean,
    val isBod: Boolean,
    val isHrOperator: Boolean,
    val isHrReportViewer: Boolean,
    val isTeamLeader: Boolean,
    val isDepartmentApprover: Boolean,
    val canSubmitRequest: Boolean,
    val canApproveRequests: Boolean,
    val canViewMonthlyReport: Boolean,
    val canManageConfig: Boolean
)

data class LeaveRoutingProjection(
    val routeKind: String,
    val unitKind: String,
    val primaryApproverAppUserId: String?,
    val teamId: String?,
    val teamCode: String?,
    val teamName: String?,
    val fallbackReason: String?,
    val profileConfigured: Boolean
)

data class LeavePolicyProjection(
    val annualBaseUnits: Int,
    val monthlyAccrualUnits: Int,
    val defaultSeniorityYears: Int,
    val defaultSeniorityStepUnits: Int,
    val carryOverExpiryMonth: Int?,
    val carryOverExpiryDay: Int?,
    val carryOverExpiresAfterDate: String?,
    val rowVersion: Int
)

data class LeaveLotProjection(
    val lotId: String,
    val leaveYear: Int,
    val lotKind: String,
    val accrualMonth: Int?,
    val grantedUnits: Int,
    val reservedUnits: Int,
    val consumedUnits: Int,
    val expiredUnits: Int,
    val availableUnits: Int,
    val usableFromDate: String,
    val expiresAfterDate: String?,
    val sourceRef: String?
)

data class LeaveBalanceProjection(
    val leaveYear: Int,
    val asOfDate: String,
    val hasProfile: Boolean,
    val profileConfigured: Boolean,
    val employmentStatus: String,
    val unitKind: String,
    val workStartDate: String?,
    val officialEffectiveDate: String?,
    val nextSeniorityEffectiveDate: String?,
    val nextSeniorityUnits: Int,
    val totalGrantedUnits: Int,
    val carryOverGrantedUnits: Int,
    val carryOverAvailableUnits: Int,
    val monthlyAccruedUnits: Int,
    val seniorityGrantedUnits: Int,
    val openingOrAdjustmentUnits: Int,
    val availableUnits: Int,
    val reservedUnits: Int,
    val consumedUnits: Int,
    val expiredUnits: Int,
    val pendingPaidUnits: Int,
    val pendingUnpaidUnits: Int,
    val approvedUnpaidUnits: Int,
    val lots: List<LeaveLotProjection>
)

data class LeaveRequestDayProjection(
    val requestDayId: String,
    val leaveDate: String,
    val sessionCode: String,
    val halfDayUnits: Int,
    val isActive: Boolean
)

data class LeaveRequestAllocationProjection(
    val allocationId: String,
    val requestDayId: String,
    val lotId: String,
    val lotKind: String,
    val leaveDate: String,
    val allocatedUnits: Int,
    val status: String,
    val settledAt: String?,
    val releasedAt: String?
)

data class LeaveRequestProjection(
    val requestId: String,
    val appUserId: String,
    val requester: LeaveAccountProjection?,
    val status: String,
    val payTreatment: String,
    val employmentStatusSnapshot: String,
    val unitKindSnapshot: String,
    val profileConfiguredAtSubmit: Boolean,
    val requestedUnits: Int,
    val reason: String,
    val routeKind: String,
    val primaryApproverAppUserId: String?,
    val primaryApprover: LeaveAccountProjection?,
    val routedTeamId: String?,
    val routedTeamCode: String?,
    val routedTeamName: String?,
    val fallbackReason: String?,
    val submittedAt: String,
    val decidedAt: String?,
    val decidedByAppUserId: String?,
    val decidedBy: LeaveAccountProjection?,
    val decisionReason: String?,
    val cancelledAt: String?,
    val cancelledByAppUserId: String?,
    val cancellationReason: String?,
    val rowVersion: Int,
    val days: List<LeaveRequestDayProjection>,
    val allocations: List<LeaveRequestAllocationProjection>
)

data class LeaveLedgerEntryProjection(
    val ledgerEntryId: String,
    val lotId: String?,
    val requestId: String?,
    val entryType: String,
    val effectiveDate: String,
    val deltaAvailableUnits: Int,
    val deltaReservedUnits: Int,
    val deltaConsumedUnits: Int,
    val deltaExpiredUnits: Int,
    val reason: String?,
    val createdAt: String
)

data class LeaveMeSummaryProjection(
    val asOfDate: String,
    val leaveYear: Int,
    val account: LeaveAccountProjection?,
    val authority: LeaveAuthorityProjection,
    val routing: LeaveRoutingProjection,
    val policy: LeavePolicyProjection,
    val balance: LeaveBalanceProjection,
    val requests: List<LeaveRequestProjection>,
    val ledgerEntries: List<LeaveLedgerEntryProjection>
)

data class LeaveApprovalInboxProjection(
    val asOfDate: String,
    val statusFilter: String,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<LeaveRequestProjection>
)

data class LeaveDraftDayInput(
    val id: String = UUID.randomUUID().toString(),
    val leaveDate: String,
    val sessionCode: String = "FULL"
)

object LeaveContract {
    val SESSION_CODES = listOf("FULL", "AM", "PM")
    val REQUEST_STATUS_FILTERS = listOf(
        "PENDING",
        "APPROVED",
        "REJECTED",
        "CANCELLED",
        "WITHDRAWN",
        "ALL"
    )
    val DECISION_CODES = listOf("APPROVE", "REJECT", "CANCEL_APPROVED")

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    val LEAVE_ERROR_MESSAGES: Map<String, String> = mapOf(
        "UNAUTHENTICATED" to "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "ACTOR_NOT_AVAILABLE" to "Tài khoản hiện không khả dụng hoặc chưa được kích hoạt.",
        "CSRF_ORIGIN_MISMATCH" to "Yêu cầu bị từ chối do không hợp lệ nguồn gốc phiên. Vui lòng tải lại.",
        "LEAVE_SHARED_ACCOUNT_FORBIDDEN" to "Tài khoản dùng chung (như tài khoản Marketing chung) không thuộc đối tượng quản lý nghỉ phép cá nhân.",
        "LEAVE_IDEMPOTENCY_KEY_INVALID" to "Mã định danh yêu cầu không hợp lệ. Vui lòng thử lại.",
        "LEAVE_IDEMPOTENCY_FINGERPRINT_MISMATCH" to "Yêu cầu bị trùng mã phiên cũ với nội dung khác. Vui lòng thử lại.",
        "LEAVE_REASON_INVALID" to "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự).",
        "LEAVE_REASON_REQUIRED" to "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự).",
        "LEAVE_REQUEST_DAYS_INVALID" to "Danh sách ngày xin nghỉ không hợp lệ. Vui lòng chọn từ 1 đến 60 ngày nghỉ hợp lệ.",
        "LEAVE_REQUEST_YEAR_OUT_OF_RANGE" to "Ngày xin nghỉ phải thuộc năm nghiệp vụ hiện hành.",
        "LEAVE_REQUEST_DUPLICATE_DATE" to "Không được chọn trùng một ngày nhiều lần trong cùng một đơn xin nghỉ.",
        "LEAVE_REQUEST_OVERLAP" to "Ngày hoặc buổi xin nghỉ bị trùng với một đơn đang chờ duyệt hoặc đã được duyệt.",
        "LEAVE_INSUFFICIENT_BALANCE" to "Số dư phép khả dụng tại thời điểm ngày nghỉ không đủ để đáp ứng đơn xin nghỉ có lương.",
        "LEAVE_REQUEST_NOT_FOUND" to "Không tìm thấy đơn xin nghỉ phép.",
        "LEAVE_REQUEST_OWNERSHIP_FORBIDDEN" to "Bạn chỉ được phép rút đơn xin nghỉ phép của chính mình.",
        "LEAVE_ROW_VERSION_CONFLICT" to "Dữ liệu vừa được cập nhật bởi thao tác khác. Hệ thống đã làm mới, vui lòng kiểm tra và thử lại.",
        "LEAVE_INVALID_STATE_TRANSITION" to "Trạng thái đơn hiện tại không cho phép thực hiện thao tác này.",
        "LEAVE_APPROVAL_FORBIDDEN" to "Bạn không có thẩm quyền duyệt hoặc xử lý đơn nghỉ phép này.",
        "LEAVE_SELF_APPROVAL_FORBIDDEN" to "Người duyệt không được tự duyệt hoặc tự hủy đơn nghỉ phép của chính mình.",
        "LEAVE_DECISION_INVALID" to "Quyết định xử lý đơn không hợp lệ.",
        "LEAVE_FILTER_INVALID" to "Bộ lọc truy vấn không hợp lệ.",
        "LEAVE_ADMIN_FORBIDDEN" to "Bạn không có quyền truy cập khu vực cấu hình quản trị nghỉ phép.",
        "LEAVE_POLICY_ADMIN_FORBIDDEN" to "Chỉ Admin hoặc Ban Giám đốc mới có quyền cập nhật chính sách nghỉ phép.",
        "LEAVE_POLICY_EXPIRY_INVALID" to "Ngày/tháng hết hạn phép tồn năm trước không hợp lệ (tháng 1–12, ngày 1–31).",
        "LEAVE_TARGET_USER_NOT_FOUND" to "Không tìm thấy tài khoản nhân sự cần cấu hình.",
        "LEAVE_PROFILE_INVALID" to "Thông tin cấu hình hồ sơ nghỉ phép không hợp lệ.",
        "LEAVE_PROFILE_WORK_START_DATE_REQUIRED" to "Nhân sự Thử việc hoặc Chính thức bắt buộc phải có Ngày bắt đầu làm việc.",
        "LEAVE_PROFILE_OFFICIAL_DATE_INVALID" to "Ngày lên chính thức không hợp lệ (phải từ ngày bắt đầu làm việc trở đi).",
        "LEAVE_SENIORITY_CONFIG_FORBIDDEN" to "Chỉ Admin hoặc Ban Giám đốc mới được cấu hình ngày và số phép tăng thâm niên tiếp theo.",
        "LEAVE_DEPARTMENT_APPROVER_ADMIN_FORBIDDEN" to "Chỉ Admin hoặc Ban Giám đốc mới được cấu hình người duyệt bộ phận Marketing / IT / Kế toán.",
        "LEAVE_UNIT_KIND_INVALID" to "Đơn vị / bộ phận không hợp lệ.",
        "LEAVE_APPROVER_USER_NOT_FOUND" to "Không tìm thấy tài khoản người duyệt hoặc tài khoản đang bị vô hiệu hóa.",
        "LEAVE_MODULE_GRANT_ADMIN_FORBIDDEN" to "Chỉ Full Admin mới có quyền cấp hoặc thu hồi quyền nội bộ phân hệ nghỉ phép.",
        "LEAVE_MODULE_GRANT_INVALID" to "Thông tin cấp quyền nội bộ không hợp lệ.",
        "LEAVE_MODULE_GRANT_NOT_FOUND" to "Không tìm thấy bản ghi quyền nội bộ đang hiệu lực để thu hồi.",
        "LEAVE_YEAR_INVALID" to "Năm nghiệp vụ nghỉ phép không hợp lệ.",
        "LEAVE_OPENING_ROWS_INVALID" to "Danh sách dòng số dư đầu kỳ không hợp lệ (tối đa 300 dòng mỗi lần áp dụng).",
        "LEAVE_CARRY_OVER_EXPIRY_NOT_CONFIGURED" to "Chưa cấu hình ngày hết hạn phép chuyển năm. Vui lòng cấu hình Chính sách nghỉ phép trước khi nhập phép tồn năm trước.",
        "LEAVE_OPENING_BATCH_VALIDATION_FAILED" to "Lô số dư đầu kỳ còn dòng lỗi. Vui lòng xem trước và xử lý hết lỗi trước khi áp dụng.",
        "LEAVE_REPORT_FORBIDDEN" to "Bạn không có quyền xem hoặc xuất Báo cáo chốt công & phép tháng.",
        "LEAVE_REPORT_YEAR_MONTH_INVALID" to "Kỳ báo cáo tháng không hợp lệ (định dạng YYYY-MM).",
        "LEAVE_REPORT_EXPORT_LIMIT_EXCEEDED" to "Số lượng dòng báo cáo vượt quá giới hạn xuất một lần (5.000 nhân sự). Vui lòng lọc theo bộ phận hoặc từ khóa.",
        "LEAVE_REPORT_EXPORT_INCOMPLETE" to "Dữ liệu báo cáo thay đổi trong lúc xuất. Vui lòng thử xuất lại.",
        "LEAVE_UNAVAILABLE" to "Hệ thống quản lý nghỉ phép tạm thời không khả dụng. Vui lòng thử lại sau."
    )

    fun errorMessageForCode(code: String?, fallbackMessage: String? = null): String {
        val cleanCode = code?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanCode != null) {
            LEAVE_ERROR_MESSAGES[cleanCode]?.let { return it }
        }
        return fallbackMessage?.takeIf { it.isNotBlank() }
            ?: "Không thể hoàn tất thao tác nghỉ phép lúc này. Vui lòng thử lại."
    }

    fun isValidUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return UUID_REGEX.matches(value.trim())
    }

    fun isValidIsoDate(value: String?): Boolean {
        val trimmed = value?.trim() ?: return false
        if (!ISO_DATE_REGEX.matches(trimmed)) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(trimmed) ?: return false
            sdf.format(parsed) == trimmed
        } catch (_: Exception) {
            false
        }
    }

    fun halfDayUnitsToDays(units: Int): Double {
        return units / 2.0
    }

    fun formatHalfDayUnits(units: Int, includeSuffix: Boolean = true): String {
        val days = halfDayUnitsToDays(units)
        val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
        val formatted = nf.format(days)
        return if (includeSuffix) "$formatted ngày" else formatted
    }

    fun formatSignedHalfDayUnits(unitsDelta: Int): String {
        if (unitsDelta == 0) return "0 ngày"
        val sign = if (unitsDelta > 0) "+" else "-"
        return "$sign${formatHalfDayUnits( kotlin.math.abs(unitsDelta), true)}"
    }

    fun formatIsoDateVi(isoDate: String?): String {
        val trimmed = isoDate?.trim().orEmpty()
        if (!ISO_DATE_REGEX.matches(trimmed)) return "Chưa đặt"
        val parts = trimmed.split("-")
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    fun leaveSessionLabel(sessionCode: String): String = when (sessionCode.trim().uppercase(Locale.ROOT)) {
        "FULL" -> "Cả ngày (1 ngày)"
        "AM" -> "Buổi sáng (0,5 ngày)"
        "PM" -> "Buổi chiều (0,5 ngày)"
        else -> sessionCode
    }

    fun leaveSessionCompactLabel(sessionCode: String): String = when (sessionCode.trim().uppercase(Locale.ROOT)) {
        "FULL" -> "Cả ngày (1,0)"
        "AM" -> "Sáng (0,5)"
        "PM" -> "Chiều (0,5)"
        else -> sessionCode
    }

    fun leaveSessionShortLabel(sessionCode: String): String = when (sessionCode.trim().uppercase(Locale.ROOT)) {
        "FULL" -> "Cả ngày"
        "AM" -> "Buổi sáng"
        "PM" -> "Buổi chiều"
        else -> sessionCode
    }

    fun selectableHistoryLeaveYears(currentYear: Int = currentVietnamLeaveYear()): List<Int> {
        return listOf(currentYear, currentYear - 1)
    }

    fun leaveSessionHalfDayUnits(sessionCode: String): Int {
        return if (sessionCode.trim().uppercase(Locale.ROOT) == "FULL") 2 else 1
    }

    fun leaveEmploymentStatusLabel(status: String): String = when (status.trim().uppercase(Locale.ROOT)) {
        "OFFICIAL" -> "Chính thức"
        "PROBATION" -> "Thử việc"
        "UNCONFIGURED" -> "Chưa cấu hình"
        else -> status
    }

    fun leaveUnitKindLabel(unitKind: String): String = when (unitKind.trim().uppercase(Locale.ROOT)) {
        "RECRUITMENT" -> "Tuyển dụng"
        "MARKETING" -> "Marketing"
        "IT" -> "IT"
        "ACCOUNTING" -> "Kế toán"
        "MANAGEMENT_BOD" -> "Ban Giám đốc / Quản lý"
        "UNCLASSIFIED" -> "Chưa phân loại"
        else -> unitKind
    }

    fun leaveRequestStatusLabel(status: String): String = when (status.trim().uppercase(Locale.ROOT)) {
        "PENDING" -> "Chờ duyệt"
        "APPROVED" -> "Đã duyệt"
        "REJECTED" -> "Từ chối"
        "CANCELLED" -> "Đã hủy sau duyệt"
        "WITHDRAWN" -> "Đã tự rút"
        "ALL" -> "Tất cả"
        else -> status
    }

    fun leavePayTreatmentLabel(payTreatment: String): String = when (payTreatment.trim().uppercase(Locale.ROOT)) {
        "PAID_LEAVE" -> "Nghỉ phép có lương"
        "UNPAID_PROBATION" -> "Nghỉ không lương (Thử việc)"
        "UNPAID_UNCONFIGURED_PROFILE" -> "Nghỉ không lương (Chưa cấu hình hồ sơ)"
        else -> payTreatment
    }

    fun leaveRouteKindLabel(
        routeKind: String,
        teamName: String? = null,
        unitKind: String? = null
    ): String = when (routeKind.trim().uppercase(Locale.ROOT)) {
        "TEAM_LEADER" -> if (!teamName.isNullOrBlank()) "Team Leader ($teamName)" else "Team Leader"
        "DEPARTMENT_HEAD" -> if (!unitKind.isNullOrBlank()) {
            "Trưởng bộ phận (${leaveUnitKindLabel(unitKind)})"
        } else {
            "Trưởng bộ phận"
        }
        "ADMIN_BOD_DIRECT" -> "Admin / Ban Giám đốc (trực tiếp)"
        "ADMIN_BOD_FALLBACK" -> "Admin / Ban Giám đốc (hàng đợi dự phòng)"
        else -> routeKind
    }

    fun leaveFallbackReasonLabel(fallbackReason: String?): String? {
        val clean = fallbackReason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (clean) {
            "DEPARTMENT_APPROVER_NOT_CONFIGURED" -> "Bộ phận chưa cấu hình người duyệt hiện hành"
            "TEAM_WITHOUT_ACTIVE_LEADER" -> "Team chưa có Team Leader đang hiệu lực"
            "TEAM_MULTIPLE_LEADERS_OR_SELF" -> "Team có nhiều Team Leader hoặc trùng người tạo đơn"
            "RECRUITER_WITHOUT_ACTIVE_TEAM" -> "Nhân sự chưa thuộc Team đang hoạt động"
            "RECRUITER_MULTI_ACTIVE_TEAMS" -> "Nhân sự đang thuộc nhiều Team cùng lúc"
            "PROFILE_UNCONFIGURED_OR_UNMAPPED" -> "Hồ sơ nghỉ phép chưa cấu hình đầy đủ hoặc chưa ánh xạ nhân sự"
            "UNMAPPED_UNIT_OR_RECRUITER" -> "Chưa ánh xạ đơn vị hoặc hồ sơ nhân sự tuyển dụng"
            else -> clean
        }
    }

    fun leaveLotKindLabel(lotKind: String, accrualMonth: Int? = null): String =
        when (lotKind.trim().uppercase(Locale.ROOT)) {
            "CARRY_OVER" -> "Phép tồn năm trước"
            "MONTHLY_ACCRUAL" -> if (accrualMonth != null && accrualMonth > 0) {
                "Phép định kỳ tháng %02d".format(Locale.US, accrualMonth)
            } else {
                "Phép định kỳ tháng"
            }
            "PROBATION_CATCHUP" -> if (accrualMonth != null && accrualMonth > 0) {
                "Phép tính bù thử việc tháng %02d".format(Locale.US, accrualMonth)
            } else {
                "Phép tính bù thử việc"
            }
            "SENIORITY_GRANT" -> "Phép thâm niên"
            "OPENING_CURRENT_YEAR" -> "Số dư đầu kỳ năm hiện tại"
            "MANUAL_ADJUSTMENT" -> "Điều chỉnh thủ công"
            else -> lotKind
        }

    fun leaveLedgerEntryTypeLabel(entryType: String): String =
        when (entryType.trim().uppercase(Locale.ROOT)) {
            "GRANT_MONTHLY_ACCRUAL" -> "Cộng phép định kỳ tháng"
            "GRANT_PROBATION_CATCHUP" -> "Cộng bù phép thời gian thử việc"
            "GRANT_SENIORITY" -> "Cộng phép thâm niên"
            "GRANT_OPENING_CARRY_OVER" -> "Khởi tạo phép tồn năm trước"
            "GRANT_OPENING_CURRENT_YEAR" -> "Khởi tạo số dư đầu kỳ năm hiện tại"
            "GRANT_MANUAL_ADJUSTMENT" -> "Điều chỉnh số dư phép"
            "RESERVE" -> "Giữ chỗ phép (đơn ngày tương lai đã duyệt)"
            "SETTLE_RESERVE_TO_CONSUME" -> "Chốt phép giữ chỗ sang đã sử dụng"
            "CONSUME" -> "Trừ phép trực tiếp (đơn đã duyệt)"
            "RELEASE_RESERVE" -> "Hoàn lại phép giữ chỗ (hủy đơn)"
            "REVERSAL_CONSUME" -> "Hoàn lại phép đã trừ (hủy đơn đã duyệt)"
            "EXPIRE_CARRY_OVER" -> "Hết hạn phép tồn năm trước"
            else -> entryType
        }

    fun leaveDecisionActionLabel(decision: String): String =
        when (decision.trim().uppercase(Locale.ROOT)) {
            "APPROVE" -> "Duyệt đơn nghỉ phép"
            "REJECT" -> "Từ chối đơn nghỉ phép"
            "CANCEL_APPROVED" -> "Hủy đơn đã duyệt (hoàn phép)"
            else -> decision
        }

    fun currentVietnamLeaveYear(nowMillis: Long = System.currentTimeMillis()): Int {
        val sdf = SimpleDateFormat("yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        return sdf.format(Date(nowMillis)).toIntOrNull() ?: 2026
    }

    fun todayVietnamIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
        return sdf.format(Date(nowMillis))
    }

    fun newClientIdempotencyKey(prefix: String): String {
        return "${prefix.trim()}-${UUID.randomUUID()}"
    }

    /**
     * Resolves visible tabs inside the Attendance area strictly from server-returned authority.
     * - CHECK_IN ("Chấm công") is always visible and default.
     * - LEAVE_REQUEST ("Xin nghỉ") is visible unless backend explicitly marks the account as shared/ineligible.
     * - LEAVE_APPROVAL ("Duyệt nghỉ") is visible ONLY when backend confirms `can_approve_requests = true`
     *   and `!is_shared_non_employee`.
     */
    fun resolveVisibleAttendanceTabs(
        authority: LeaveAuthorityProjection?,
        errorCode: String? = null
    ): List<AttendanceAreaTab> {
        val tabs = mutableListOf(AttendanceAreaTab.CHECK_IN)
        val isSharedOrForbidden = errorCode == "LEAVE_SHARED_ACCOUNT_FORBIDDEN" ||
            authority?.isSharedNonEmployee == true ||
            authority?.canSubmitRequest == false
        if (!isSharedOrForbidden) {
            tabs.add(AttendanceAreaTab.LEAVE_REQUEST)
        }
        if (authority != null && !authority.isSharedNonEmployee && authority.canApproveRequests) {
            tabs.add(AttendanceAreaTab.LEAVE_APPROVAL)
        }
        return tabs
    }

    fun canWithdrawOwnLeaveRequest(
        actorAppUserId: String?,
        request: LeaveRequestProjection
    ): Boolean {
        if (actorAppUserId.isNullOrBlank()) return false
        return request.appUserId.equals(actorAppUserId.trim(), ignoreCase = true) &&
            request.status.equals("PENDING", ignoreCase = true)
    }

    fun validateApproverDecisionInput(
        actorAppUserId: String?,
        request: LeaveRequestProjection,
        decision: String,
        reason: String
    ): Pair<Boolean, String?> {
        val cleanActorId = actorAppUserId?.trim().orEmpty()
        if (cleanActorId.isEmpty()) {
            return false to "Chưa xác định được người duyệt hiện tại."
        }
        if (cleanActorId.equals(request.appUserId.trim(), ignoreCase = true)) {
            return false to "Người duyệt không được tự duyệt hoặc tự hủy đơn nghỉ phép của chính mình."
        }
        val normalizedDecision = decision.trim().uppercase(Locale.ROOT)
        val normalizedStatus = request.status.trim().uppercase(Locale.ROOT)
        return when (normalizedDecision) {
            "APPROVE" -> {
                if (normalizedStatus != "PENDING") {
                    false to "Chỉ có thể duyệt đơn đang ở trạng thái Chờ duyệt."
                } else {
                    true to null
                }
            }
            "REJECT" -> {
                if (normalizedStatus != "PENDING") {
                    false to "Chỉ có thể từ chối đơn đang ở trạng thái Chờ duyệt."
                } else if (reason.trim().isEmpty()) {
                    false to "Bắt buộc nhập lý do khi từ chối đơn nghỉ phép."
                } else if (reason.trim().length > 2000) {
                    false to "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự)."
                } else {
                    true to null
                }
            }
            "CANCEL_APPROVED" -> {
                if (normalizedStatus != "APPROVED") {
                    false to "Chỉ có thể hủy đơn đã ở trạng thái Đã duyệt để hoàn lại phép."
                } else if (reason.trim().isEmpty()) {
                    false to "Bắt buộc nhập lý do khi hủy đơn nghỉ phép đã duyệt."
                } else if (reason.trim().length > 2000) {
                    false to "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự)."
                } else {
                    true to null
                }
            }
            else -> false to "Quyết định xử lý đơn không hợp lệ."
        }
    }

    fun validateCreateDraftInput(
        days: List<LeaveDraftDayInput>,
        reason: String
    ): String? {
        if (days.isEmpty()) {
            return "Vui lòng thêm ít nhất 1 ngày xin nghỉ."
        }
        if (days.size > 60) {
            return "Mỗi đơn chỉ được đăng ký tối đa 60 ngày nghỉ."
        }
        val seenDates = mutableSetOf<String>()
        val duplicateDates = mutableListOf<String>()
        for (row in days) {
            val dateStr = row.leaveDate.trim()
            val sessionStr = row.sessionCode.trim().uppercase(Locale.ROOT)
            if (!isValidIsoDate(dateStr)) {
                return "Vui lòng chọn đầy đủ ngày nghỉ hợp lệ (YYYY-MM-DD)."
            }
            if (sessionStr !in SESSION_CODES) {
                return "Vui lòng chọn buổi nghỉ hợp lệ (Cả ngày, Buổi sáng hoặc Buổi chiều)."
            }
            if (!seenDates.add(dateStr)) {
                if (dateStr !in duplicateDates) {
                    duplicateDates.add(dateStr)
                }
            }
        }
        if (duplicateDates.isNotEmpty()) {
            return "Không được chọn trùng một ngày nhiều lần trong cùng một đơn xin nghỉ (${duplicateDates.joinToString(", ") { formatIsoDateVi(it) }})."
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            return "Vui lòng nhập lý do xin nghỉ phép."
        }
        if (trimmedReason.length > 2000) {
            return "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự)."
        }
        return null
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").trim().ifEmpty { null }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key)
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }

    fun parseAccountProjection(obj: JSONObject?): LeaveAccountProjection? {
        if (obj == null) return null
        val appUserId = obj.optString("app_user_id", "").trim()
        if (appUserId.isEmpty()) return null
        return LeaveAccountProjection(
            appUserId = appUserId,
            displayName = obj.optString("display_name", "").trim(),
            loginId = obj.optNullableString("login_id"),
            personnelCode = obj.optNullableString("personnel_code"),
            recruiterId = obj.optNullableString("recruiter_id"),
            accountEnabled = obj.optBoolean("account_enabled", true)
        )
    }

    fun parseAuthorityProjection(obj: JSONObject?): LeaveAuthorityProjection {
        if (obj == null) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "LEAVE_AUTHORITY_MISSING",
                message = "Phản hồi máy chủ thiếu thông tin phân quyền nghỉ phép."
            )
        }
        val appUserId = obj.optString("app_user_id", "").trim()
        val isShared = obj.optBoolean("is_shared_non_employee", false)
        return LeaveAuthorityProjection(
            appUserId = appUserId,
            isSharedNonEmployee = isShared,
            isFullAdmin = obj.optBoolean("is_full_admin", false),
            isBod = obj.optBoolean("is_bod", false),
            isHrOperator = obj.optBoolean("is_hr_operator", false),
            isHrReportViewer = obj.optBoolean("is_hr_report_viewer", false),
            isTeamLeader = obj.optBoolean("is_team_leader", false),
            isDepartmentApprover = obj.optBoolean("is_department_approver", false),
            canSubmitRequest = obj.optBoolean("can_submit_request", !isShared),
            canApproveRequests = obj.optBoolean("can_approve_requests", false),
            canViewMonthlyReport = obj.optBoolean("can_view_monthly_report", false),
            canManageConfig = obj.optBoolean("can_manage_config", false)
        )
    }

    fun parseRoutingProjection(obj: JSONObject?): LeaveRoutingProjection {
        val safe = obj ?: JSONObject()
        return LeaveRoutingProjection(
            routeKind = safe.optString("route_kind", "ADMIN_BOD_FALLBACK").trim(),
            unitKind = safe.optString("unit_kind", "UNCLASSIFIED").trim(),
            primaryApproverAppUserId = safe.optNullableString("primary_approver_app_user_id"),
            teamId = safe.optNullableString("team_id"),
            teamCode = safe.optNullableString("team_code"),
            teamName = safe.optNullableString("team_name"),
            fallbackReason = safe.optNullableString("fallback_reason"),
            profileConfigured = safe.optBoolean("profile_configured", false)
        )
    }

    fun parsePolicyProjection(obj: JSONObject?): LeavePolicyProjection {
        val safe = obj ?: JSONObject()
        return LeavePolicyProjection(
            annualBaseUnits = safe.optInt("annual_base_units", 24),
            monthlyAccrualUnits = safe.optInt("monthly_accrual_units", 2),
            defaultSeniorityYears = safe.optInt("default_seniority_years", 5),
            defaultSeniorityStepUnits = safe.optInt("default_seniority_step_units", 2),
            carryOverExpiryMonth = safe.optNullableInt("carry_over_expiry_month"),
            carryOverExpiryDay = safe.optNullableInt("carry_over_expiry_day"),
            carryOverExpiresAfterDate = safe.optNullableString("carry_over_expires_after_date"),
            rowVersion = safe.optInt("row_version", 1)
        )
    }

    fun parseBalanceProjection(obj: JSONObject?, defaultYear: Int): LeaveBalanceProjection {
        val safe = obj ?: JSONObject()
        val lotsArr = safe.optJSONArray("lots") ?: JSONArray()
        val lots = buildList {
            for (i in 0 until lotsArr.length()) {
                val item = lotsArr.optJSONObject(i) ?: continue
                add(
                    LeaveLotProjection(
                        lotId = item.optString("lot_id", "").trim(),
                        leaveYear = item.optInt("leave_year", defaultYear),
                        lotKind = item.optString("lot_kind", "").trim(),
                        accrualMonth = item.optNullableInt("accrual_month"),
                        grantedUnits = item.optInt("granted_units", 0),
                        reservedUnits = item.optInt("reserved_units", 0),
                        consumedUnits = item.optInt("consumed_units", 0),
                        expiredUnits = item.optInt("expired_units", 0),
                        availableUnits = item.optInt("available_units", 0),
                        usableFromDate = item.optString("usable_from_date", "").trim(),
                        expiresAfterDate = item.optNullableString("expires_after_date"),
                        sourceRef = item.optNullableString("source_ref")
                    )
                )
            }
        }
        return LeaveBalanceProjection(
            leaveYear = safe.optInt("leave_year", defaultYear),
            asOfDate = safe.optString("as_of_date", "").trim(),
            hasProfile = safe.optBoolean("has_profile", false),
            profileConfigured = safe.optBoolean("profile_configured", false),
            employmentStatus = safe.optString("employment_status", "UNCONFIGURED").trim(),
            unitKind = safe.optString("unit_kind", "UNCLASSIFIED").trim(),
            workStartDate = safe.optNullableString("work_start_date"),
            officialEffectiveDate = safe.optNullableString("official_effective_date"),
            nextSeniorityEffectiveDate = safe.optNullableString("next_seniority_effective_date"),
            nextSeniorityUnits = safe.optInt("next_seniority_units", 0),
            totalGrantedUnits = safe.optInt("total_granted_units", 0),
            carryOverGrantedUnits = safe.optInt("carry_over_granted_units", 0),
            carryOverAvailableUnits = safe.optInt("carry_over_available_units", 0),
            monthlyAccruedUnits = safe.optInt("monthly_accrued_units", 0),
            seniorityGrantedUnits = safe.optInt("seniority_granted_units", 0),
            openingOrAdjustmentUnits = safe.optInt("opening_or_adjustment_units", 0),
            availableUnits = safe.optInt("available_units", 0),
            reservedUnits = safe.optInt("reserved_units", 0),
            consumedUnits = safe.optInt("consumed_units", 0),
            expiredUnits = safe.optInt("expired_units", 0),
            pendingPaidUnits = safe.optInt("pending_paid_units", 0),
            pendingUnpaidUnits = safe.optInt("pending_unpaid_units", 0),
            approvedUnpaidUnits = safe.optInt("approved_unpaid_units", 0),
            lots = lots
        )
    }

    fun parseRequestProjection(obj: JSONObject): LeaveRequestProjection {
        val daysArr = obj.optJSONArray("days") ?: JSONArray()
        val days = buildList {
            for (i in 0 until daysArr.length()) {
                val d = daysArr.optJSONObject(i) ?: continue
                val sessionCode = d.optString("session_code", "FULL").trim().uppercase(Locale.ROOT)
                add(
                    LeaveRequestDayProjection(
                        requestDayId = d.optString("request_day_id", "").trim(),
                        leaveDate = d.optString("leave_date", "").trim(),
                        sessionCode = sessionCode,
                        halfDayUnits = d.optInt("half_day_units", leaveSessionHalfDayUnits(sessionCode)),
                        isActive = d.optBoolean("is_active", true)
                    )
                )
            }
        }
        val allocArr = obj.optJSONArray("allocations") ?: JSONArray()
        val allocations = buildList {
            for (i in 0 until allocArr.length()) {
                val a = allocArr.optJSONObject(i) ?: continue
                add(
                    LeaveRequestAllocationProjection(
                        allocationId = a.optString("allocation_id", "").trim(),
                        requestDayId = a.optString("request_day_id", "").trim(),
                        lotId = a.optString("lot_id", "").trim(),
                        lotKind = a.optString("lot_kind", "").trim(),
                        leaveDate = a.optString("leave_date", "").trim(),
                        allocatedUnits = a.optInt("allocated_units", 0),
                        status = a.optString("status", "").trim(),
                        settledAt = a.optNullableString("settled_at"),
                        releasedAt = a.optNullableString("released_at")
                    )
                )
            }
        }
        return LeaveRequestProjection(
            requestId = obj.optString("request_id", "").trim(),
            appUserId = obj.optString("app_user_id", "").trim(),
            requester = parseAccountProjection(obj.optJSONObject("requester")),
            status = obj.optString("status", "PENDING").trim(),
            payTreatment = obj.optString("pay_treatment", "PAID_LEAVE").trim(),
            employmentStatusSnapshot = obj.optString("employment_status_snapshot", "UNCONFIGURED").trim(),
            unitKindSnapshot = obj.optString("unit_kind_snapshot", "UNCLASSIFIED").trim(),
            profileConfiguredAtSubmit = obj.optBoolean("profile_configured_at_submit", false),
            requestedUnits = obj.optInt("requested_units", days.sumOf { it.halfDayUnits }),
            reason = obj.optString("reason", "").trim(),
            routeKind = obj.optString("route_kind", "").trim(),
            primaryApproverAppUserId = obj.optNullableString("primary_approver_app_user_id"),
            primaryApprover = parseAccountProjection(obj.optJSONObject("primary_approver")),
            routedTeamId = obj.optNullableString("routed_team_id"),
            routedTeamCode = obj.optNullableString("routed_team_code"),
            routedTeamName = obj.optNullableString("routed_team_name"),
            fallbackReason = obj.optNullableString("fallback_reason"),
            submittedAt = obj.optString("submitted_at", "").trim(),
            decidedAt = obj.optNullableString("decided_at"),
            decidedByAppUserId = obj.optNullableString("decided_by_app_user_id"),
            decidedBy = parseAccountProjection(obj.optJSONObject("decided_by")),
            decisionReason = obj.optNullableString("decision_reason"),
            cancelledAt = obj.optNullableString("cancelled_at"),
            cancelledByAppUserId = obj.optNullableString("cancelled_by_app_user_id"),
            cancellationReason = obj.optNullableString("cancellation_reason"),
            rowVersion = obj.optInt("row_version", 1),
            days = days,
            allocations = allocations
        )
    }

    fun parseMyLeaveSummary(envelopeJson: JSONObject): LeaveMeSummaryProjection {
        val dataObj = envelopeJson.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "LEAVE_DATA_MISSING",
            message = "Phản hồi máy chủ thiếu dữ liệu hồ sơ nghỉ phép."
        )
        val leaveYear = dataObj.optInt("leave_year", currentVietnamLeaveYear())
        val authority = parseAuthorityProjection(dataObj.optJSONObject("authority"))
        val requestsArr = dataObj.optJSONArray("requests") ?: JSONArray()
        val requests = buildList {
            for (i in 0 until requestsArr.length()) {
                val r = requestsArr.optJSONObject(i) ?: continue
                add(parseRequestProjection(r))
            }
        }
        val ledgerArr = dataObj.optJSONArray("ledger_entries") ?: JSONArray()
        val ledgerEntries = buildList {
            for (i in 0 until ledgerArr.length()) {
                val l = ledgerArr.optJSONObject(i) ?: continue
                add(
                    LeaveLedgerEntryProjection(
                        ledgerEntryId = l.optString("ledger_entry_id", "").trim(),
                        lotId = l.optNullableString("lot_id"),
                        requestId = l.optNullableString("request_id"),
                        entryType = l.optString("entry_type", "").trim(),
                        effectiveDate = l.optString("effective_date", "").trim(),
                        deltaAvailableUnits = l.optInt("delta_available_units", 0),
                        deltaReservedUnits = l.optInt("delta_reserved_units", 0),
                        deltaConsumedUnits = l.optInt("delta_consumed_units", 0),
                        deltaExpiredUnits = l.optInt("delta_expired_units", 0),
                        reason = l.optNullableString("reason"),
                        createdAt = l.optString("created_at", "").trim()
                    )
                )
            }
        }
        return LeaveMeSummaryProjection(
            asOfDate = dataObj.optString("as_of_date", "").trim(),
            leaveYear = leaveYear,
            account = parseAccountProjection(dataObj.optJSONObject("account")),
            authority = authority,
            routing = parseRoutingProjection(dataObj.optJSONObject("routing")),
            policy = parsePolicyProjection(dataObj.optJSONObject("policy")),
            balance = parseBalanceProjection(dataObj.optJSONObject("balance"), leaveYear),
            requests = requests,
            ledgerEntries = ledgerEntries
        )
    }

    fun parseApprovalInbox(envelopeJson: JSONObject): LeaveApprovalInboxProjection {
        val dataObj = envelopeJson.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "LEAVE_INBOX_DATA_MISSING",
            message = "Phản hồi máy chủ thiếu dữ liệu hộp thư duyệt nghỉ phép."
        )
        val itemsArr = dataObj.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                add(parseRequestProjection(item))
            }
        }
        return LeaveApprovalInboxProjection(
            asOfDate = dataObj.optString("as_of_date", "").trim(),
            statusFilter = dataObj.optString("status_filter", "PENDING").trim(),
            total = dataObj.optInt("total", items.size),
            limit = dataObj.optInt("limit", 50),
            offset = dataObj.optInt("offset", 0),
            items = items
        )
    }

    fun buildCreateLeaveRequestPayload(
        days: List<LeaveDraftDayInput>,
        reason: String,
        idempotencyKey: String = newClientIdempotencyKey("leave-create")
    ): JSONObject {
        val daysArr = JSONArray()
        for (d in days) {
            daysArr.put(
                JSONObject().apply {
                    put("leave_date", d.leaveDate.trim())
                    put("session_code", d.sessionCode.trim().uppercase(Locale.ROOT))
                }
            )
        }
        return JSONObject().apply {
            put("days", daysArr)
            put("reason", reason.trim())
            put("idempotency_key", idempotencyKey.trim())
        }
    }

    fun buildWithdrawLeaveRequestPayload(
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = newClientIdempotencyKey("leave-withdraw")
    ): JSONObject {
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        return JSONObject().apply {
            put("expected_row_version", expectedRowVersion)
            put("reason", cleanReason ?: JSONObject.NULL)
            put("idempotency_key", idempotencyKey.trim())
        }
    }

    fun buildDecideLeaveRequestPayload(
        decision: String,
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = newClientIdempotencyKey("leave-decision")
    ): JSONObject {
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        return JSONObject().apply {
            put("decision", decision.trim().uppercase(Locale.ROOT))
            put("expected_row_version", expectedRowVersion)
            put("reason", cleanReason ?: JSONObject.NULL)
            put("idempotency_key", idempotencyKey.trim())
        }
    }
}

class LeaveService(
    private val httpClient: BackendHttpClient
) {
    private fun rethrowMappedLeaveError(e: BackendApiException): Nothing {
        val mappedMessage = LeaveContract.LEAVE_ERROR_MESSAGES[e.backendCode]
        if (mappedMessage != null && mappedMessage != e.message) {
            throw BackendApiException(
                kind = e.kind,
                statusCode = e.statusCode,
                backendCode = e.backendCode,
                message = mappedMessage,
                cause = e
            )
        }
        throw e
    }

    suspend fun fetchMyLeaveSummary(leaveYear: Int? = null): LeaveMeSummaryProjection =
        withContext(Dispatchers.IO) {
            try {
                val path = if (leaveYear != null) {
                    "/api/leave/me?year=$leaveYear"
                } else {
                    "/api/leave/me"
                }
                val json = httpClient.getJson(pathWithQuery = path, requireOkTrue = true)
                LeaveContract.parseMyLeaveSummary(json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                rethrowMappedLeaveError(e)
            }
        }

    suspend fun createLeaveRequest(
        days: List<LeaveDraftDayInput>,
        reason: String,
        idempotencyKey: String = LeaveContract.newClientIdempotencyKey("leave-create")
    ): String = withContext(Dispatchers.IO) {
        try {
            val payload = LeaveContract.buildCreateLeaveRequestPayload(
                days = days,
                reason = reason,
                idempotencyKey = idempotencyKey
            )
            val json = httpClient.postJson(
                path = "/api/leave/requests",
                bodyJson = payload,
                requireOkTrue = true
            )
            val dataObj = json.optJSONObject("data")
            dataObj?.optString("request_id", "").orEmpty().trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            rethrowMappedLeaveError(e)
        }
    }

    suspend fun withdrawLeaveRequest(
        requestId: String,
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = LeaveContract.newClientIdempotencyKey("leave-withdraw")
    ): String = withContext(Dispatchers.IO) {
        val cleanId = requestId.trim().lowercase(Locale.ROOT)
        if (!LeaveContract.isValidUuid(cleanId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "INVALID_REQUEST",
                message = "Mã đơn xin nghỉ phép không hợp lệ."
            )
        }
        try {
            val payload = LeaveContract.buildWithdrawLeaveRequestPayload(
                expectedRowVersion = expectedRowVersion,
                reason = reason,
                idempotencyKey = idempotencyKey
            )
            val json = httpClient.postJson(
                path = "/api/leave/requests/$cleanId/withdraw",
                bodyJson = payload,
                requireOkTrue = true
            )
            val dataObj = json.optJSONObject("data")
            dataObj?.optString("status", "WITHDRAWN").orEmpty().trim().ifEmpty { "WITHDRAWN" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            rethrowMappedLeaveError(e)
        }
    }

    suspend fun fetchApprovalInbox(
        statusFilter: String = "PENDING",
        limit: Int = 50,
        offset: Int = 0
    ): LeaveApprovalInboxProjection = withContext(Dispatchers.IO) {
        val cleanStatus = statusFilter.trim().uppercase(Locale.ROOT).let {
            if (it in LeaveContract.REQUEST_STATUS_FILTERS) it else "PENDING"
        }
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        try {
            val path = "/api/leave/approvals?status=$cleanStatus&limit=$safeLimit&offset=$safeOffset"
            val json = httpClient.getJson(pathWithQuery = path, requireOkTrue = true)
            LeaveContract.parseApprovalInbox(json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            rethrowMappedLeaveError(e)
        }
    }

    suspend fun decideLeaveRequest(
        requestId: String,
        decision: String,
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = LeaveContract.newClientIdempotencyKey("leave-decision")
    ): String = withContext(Dispatchers.IO) {
        val cleanId = requestId.trim().lowercase(Locale.ROOT)
        if (!LeaveContract.isValidUuid(cleanId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "INVALID_REQUEST",
                message = "Mã đơn xin nghỉ phép không hợp lệ."
            )
        }
        val cleanDecision = decision.trim().uppercase(Locale.ROOT)
        if (cleanDecision !in LeaveContract.DECISION_CODES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "LEAVE_DECISION_INVALID",
                message = LeaveContract.errorMessageForCode("LEAVE_DECISION_INVALID")
            )
        }
        try {
            val payload = LeaveContract.buildDecideLeaveRequestPayload(
                decision = cleanDecision,
                expectedRowVersion = expectedRowVersion,
                reason = reason,
                idempotencyKey = idempotencyKey
            )
            val json = httpClient.postJson(
                path = "/api/leave/approvals/$cleanId/decision",
                bodyJson = payload,
                requireOkTrue = true
            )
            val dataObj = json.optJSONObject("data")
            dataObj?.optString("status", "").orEmpty().trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            rethrowMappedLeaveError(e)
        }
    }
}
