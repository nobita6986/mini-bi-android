package com.example.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SalaryAdvanceManagedProjectOption(
    val projectId: String,
    val displayName: String,
    val active: Boolean,
    val hasActiveAccountant: Boolean,
    val accountantUserId: String?,
    val accountantDisplayName: String?
)

data class SalaryAdvanceActorContext(
    val appUserId: String,
    val displayName: String,
    val isProjectManager: Boolean,
    val isProjectAccountant: Boolean,
    val isSupervisoryViewer: Boolean,
    val isFullAdmin: Boolean,
    val isBod: Boolean,
    val managedProjects: List<SalaryAdvanceManagedProjectOption>,
    val authorizationDate: String
) {
    val activeManagedProjects: List<SalaryAdvanceManagedProjectOption>
        get() = managedProjects.filter { it.active }
}

data class SalaryAdvanceWorkerBankAccountInfo(
    val hasBankAccount: Boolean,
    val bankId: String?,
    val institutionName: String?,
    val accountNumber: String?,
    val accountNumberMasked: String?,
    val accountHolderName: String?,
    val isSelfOwned: Boolean
)

data class SalaryAdvanceWorker30dSummary(
    val windowDays: Int,
    val windowFromDate: String,
    val windowToDate: String,
    val timezone: String,
    val qualifyingRule: String,
    val totalCount: Int,
    val totalAmountVnd: String
)

data class SalaryAdvanceWorkerOption(
    val workerEntryId: String,
    val employeeCode: String,
    val displayName: String,
    val firstWorkDate: String,
    val providerType: String,
    val recruiterDisplayName: String?,
    val recruiterCode: String?,
    val hasPortrait: Boolean,
    val employmentStatus: String,
    val activeOnProjectCount: Int,
    val hasDuplicateCccd: Boolean,
    val isEligible: Boolean,
    val ineligibleReasons: List<String>,
    val bankTransferEligible: Boolean,
    val bankIneligibleReason: String?,
    val bankAccount: SalaryAdvanceWorkerBankAccountInfo?,
    val advance30dSummary: SalaryAdvanceWorker30dSummary?
)

data class SalaryAdvanceWorkerListResponse(
    val projectId: String,
    val workers: List<SalaryAdvanceWorkerOption>,
    val hasMore: Boolean,
    val nextCursor: String?
)

data class SalaryAdvanceWorkerEligibilityView(
    val isEligible: Boolean,
    val statusLabel: String,
    val reasonCodes: List<String>,
    val reasonLabels: List<String>,
    val blockingMessage: String?,
    val portraitStatusLabel: String,
    val hasPortrait: Boolean
)

data class SalaryAdvanceWorkerBankTransferView(
    val canUseBankTransfer: Boolean,
    val hasBankAccount: Boolean,
    val isSelfOwned: Boolean,
    val statusLabel: String,
    val reasonCode: String?,
    val blockingMessage: String?,
    val bankId: String?,
    val institutionName: String?,
    val accountNumberDisplay: String?,
    val accountHolderName: String?
)

data class SalaryAdvanceWorkerHistoryItem(
    val requestId: String,
    val projectId: String,
    val projectDisplayName: String,
    val workerEntryId: String,
    val workerEmployeeCode: String,
    val workerDisplayName: String,
    val requestedAmountVnd: String,
    val paymentMethod: String,
    val initialContent: String?,
    val requestState: String,
    val evidenceReviewState: String,
    val effectiveDate: String,
    val effectiveAt: String,
    val createdAt: String,
    val decidedAt: String?,
    val evidenceReviewedAt: String?,
    val createdByDisplayName: String,
    val decidedByDisplayName: String?,
    val evidenceReviewedByDisplayName: String?
)

data class SalaryAdvanceWorkerHistoryResponse(
    val requestId: String?,
    val workerEntryId: String,
    val workerEmployeeCode: String,
    val workerDisplayName: String,
    val periodMode: String,
    val month: String?,
    val fromDate: String?,
    val toDate: String?,
    val timezone: String,
    val qualifyingRule: String,
    val totalCount: Int,
    val totalAmountVnd: String,
    val items: List<SalaryAdvanceWorkerHistoryItem>,
    val hasMore: Boolean,
    val nextCursor: String?
)

data class CreateSalaryAdvancePayload(
    val projectId: String,
    val workerEntryId: String,
    val requestedAmountVnd: String,
    val paymentMethod: String,
    val initialContent: String
) {
    fun fingerprintKey(): String {
        return "$projectId|$workerEntryId|$requestedAmountVnd|$paymentMethod|$initialContent"
    }
}

sealed class CreateSalaryAdvanceValidationResult {
    data class Valid(val payload: CreateSalaryAdvancePayload) : CreateSalaryAdvanceValidationResult()
    data class Invalid(
        val message: String,
        val fieldErrors: Map<String, String>
    ) : CreateSalaryAdvanceValidationResult()
}

data class SalaryAdvanceCreatedRequestSummary(
    val requestId: String,
    val projectId: String,
    val projectDisplayName: String,
    val workerEntryId: String,
    val workerEmployeeCode: String,
    val workerDisplayName: String,
    val requestedAmountVnd: String,
    val paymentMethod: String,
    val initialContent: String,
    val requestState: String,
    val evidenceReviewState: String,
    val version: Int,
    val replayed: Boolean
)

object SalaryAdvanceContract {
    val PAYMENT_METHODS = setOf("CASH", "BANK_TRANSFER")
    val HISTORY_PERIOD_MODES = listOf(
        "THIS_MONTH" to "Tháng này",
        "LAST_30_DAYS" to "30 ngày gần nhất",
        "MONTH" to "Chọn tháng",
        "ALL" to "Toàn bộ",
        "CUSTOM_RANGE" to "Tùy chỉnh khoảng ngày"
    )

    const val SUMMARY_30D_RULE_NOTICE =
        "Chỉ tính các yêu cầu ứng lương đã được Kế toán chấp thuận và đã xác nhận đối chiếu chứng từ nhận tiền (APPROVED + REVIEWED), tính theo ngày hoàn tất đối chiếu chứng từ (múi giờ Asia/Ho_Chi_Minh)."

    private val UUID_PATTERN =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val ISO_DATE_PATTERN = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val UI_DATE_PATTERN = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")
    private val ISO_MONTH_PATTERN = Regex("^(\\d{4})-(0[1-9]|1[0-2])$")
    private val UI_MONTH_PATTERN = Regex("^(0[1-9]|1[0-2])/(\\d{4})$")
    private val MAX_PG_BIGINT = BigInteger("9223372036854775807")
    private val VIETNAM_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private fun isValidCalendarDate(year: Int, month: Int, day: Int): Boolean {
        if (year < 1900 || year > 2100 || month !in 1..12 || day !in 1..31) return false
        val cal = Calendar.getInstance(VIETNAM_TIMEZONE, Locale.US)
        cal.isLenient = false
        cal.clear()
        cal.set(year, month - 1, day)
        return runCatching { cal.timeInMillis }.isSuccess
    }

    val ERROR_MESSAGES: Map<String, String> = mapOf(
        "UNAUTHENTICATED" to "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "SALARY_ADVANCE_UNAUTHENTICATED" to "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "ACTOR_NOT_AVAILABLE" to "Tài khoản của bạn hiện chưa sẵn sàng để sử dụng phân hệ Ứng lương.",
        "SALARY_ADVANCE_ACCOUNT_UNAVAILABLE" to "Tài khoản của bạn hiện chưa sẵn sàng để sử dụng phân hệ Ứng lương.",
        "SALARY_ADVANCE_ACCESS_DENIED" to "Bạn không có quyền thực hiện thao tác này hoặc không được phân công vào dự án tương ứng.",
        "SALARY_ADVANCE_PROJECT_NOT_FOUND" to "Không tìm thấy yêu cầu ứng lương hoặc dữ liệu liên quan.",
        "SALARY_ADVANCE_WORKER_NOT_FOUND" to "Không tìm thấy yêu cầu ứng lương hoặc dữ liệu liên quan.",
        "SALARY_ADVANCE_REQUEST_NOT_FOUND" to "Không tìm thấy yêu cầu ứng lương hoặc dữ liệu liên quan.",
        "SALARY_ADVANCE_EVIDENCE_NOT_FOUND" to "Không tìm thấy yêu cầu ứng lương hoặc dữ liệu liên quan.",
        "SALARY_ADVANCE_WORKER_PORTRAIT_NOT_FOUND" to "Không tìm thấy yêu cầu ứng lương hoặc dữ liệu liên quan.",
        "SALARY_ADVANCE_NOT_FOUND" to "Không tìm thấy yêu cầu ứng lương hoặc dữ liệu liên quan.",
        "SALARY_ADVANCE_PROJECT_INACTIVE" to "Dự án được chọn hiện không còn ở trạng thái hoạt động.",
        "SALARY_ADVANCE_PROJECT_ACCOUNTANT_UNASSIGNED" to "Dự án này chưa có Kế toán dự án đang hoạt động. Vui lòng liên hệ Admin cấu hình Kế toán dự án trước khi gửi yêu cầu.",
        "SALARY_ADVANCE_WORKER_PROJECT_MISMATCH" to "Người lao động được chọn không thuộc phạm vi dự án này hoặc chưa có công hợp lệ.",
        "SALARY_ADVANCE_WORKER_NOT_IN_PROJECT" to "Người lao động được chọn không thuộc phạm vi dự án này hoặc chưa có công hợp lệ.",
        "SALARY_ADVANCE_WORKER_INELIGIBLE_STATUS" to "Người lao động hiện không ở trạng thái đang làm việc (ON) nên không đủ điều kiện ứng lương.",
        "SALARY_ADVANCE_WORKER_VENDOR_RECRUITED" to "Người lao động do Vendor tuyển dụng không thuộc diện tạo yêu cầu ứng lương trên hệ thống.",
        "SALARY_ADVANCE_WORKER_MULTI_PROJECT_ON" to "Người lao động đang ở trạng thái làm việc (ON) tại từ 2 dự án trở lên nên không đủ điều kiện ứng lương.",
        "SALARY_ADVANCE_WORKER_DUPLICATE_CCCD" to "Hồ sơ người lao động đang thuộc nhóm trùng CCCD cần rà soát nên tạm thời bị chặn ứng lương.",
        "SALARY_ADVANCE_PAYMENT_METHOD_INVALID" to "Vui lòng chọn phương thức nhận tiền hợp lệ (Tiền mặt hoặc Chuyển khoản).",
        "SALARY_ADVANCE_BANK_ACCOUNT_MISSING" to "Hồ sơ người lao động chưa có đầy đủ thông tin tài khoản ngân hàng để nhận chuyển khoản.",
        "SALARY_ADVANCE_BANK_ACCOUNT_NOT_SELF_OWNED" to "Tên chủ tài khoản ngân hàng không trùng khớp với họ tên người lao động (không chính chủ). Không thể tạo yêu cầu chuyển khoản.",
        "SALARY_ADVANCE_AMOUNT_INVALID" to "Số tiền ứng lương không hợp lệ (phải là số nguyên dương VNĐ).",
        "SALARY_ADVANCE_CONTENT_INVALID" to "Nội dung tin nhắn hoặc ghi chú không hợp lệ.",
        "SALARY_ADVANCE_NOTE_INVALID" to "Nội dung tin nhắn hoặc ghi chú không hợp lệ.",
        "SALARY_ADVANCE_MESSAGE_REQUIRED" to "Vui lòng nhập nội dung trao đổi.",
        "SALARY_ADVANCE_REASON_REQUIRED" to "Vui lòng nhập lý do từ chối yêu cầu ứng lương.",
        "SALARY_ADVANCE_STATE_CONFLICT" to "Trạng thái yêu cầu ứng lương đã thay đổi và không còn cho phép thao tác này. Hệ thống đã tải lại dữ liệu mới nhất.",
        "SALARY_ADVANCE_VERSION_CONFLICT" to "Yêu cầu ứng lương vừa được người dùng khác cập nhật. Hệ thống đã tải lại phiên bản mới nhất.",
        "SALARY_ADVANCE_IDEMPOTENCY_INVALID" to "Thiếu hoặc sai định dạng mã chống gửi trùng (Idempotency-Key). Vui lòng thử lại.",
        "SALARY_ADVANCE_IDEMPOTENCY_REQUIRED" to "Thiếu hoặc sai định dạng mã chống gửi trùng (Idempotency-Key). Vui lòng thử lại.",
        "SALARY_ADVANCE_IDEMPOTENCY_CONFLICT" to "Thao tác đang được xử lý hoặc mã chống gửi trùng bị xung đột. Hệ thống đã làm mới trạng thái.",
        "SALARY_ADVANCE_QUERY_INVALID" to "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.",
        "SALARY_ADVANCE_PROJECT_INVALID" to "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.",
        "SALARY_ADVANCE_WORKER_INVALID" to "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.",
        "SALARY_ADVANCE_REQUEST_INVALID" to "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.",
        "SALARY_ADVANCE_PAYLOAD_INVALID" to "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.",
        "SALARY_ADVANCE_UNAVAILABLE" to "Hệ thống tạm thời chưa xử lý được yêu cầu. Vui lòng thử lại."
    )

    private val WORKER_INELIGIBLE_REASON_LABELS = mapOf(
        "WORKER_STATUS_OFF" to "NLĐ đã nghỉ việc (OFF)",
        "WORKER_STATUS_UNCONFIRMED" to "Trạng thái làm việc chưa xác nhận (UNCONFIRMED)",
        "WORKER_VENDOR_RECRUITED" to "Người lao động do Vendor tuyển dụng không thuộc diện ứng lương trên BI",
        "WORKER_MULTI_PROJECT_ON" to "NLĐ đang ON tại từ 2 dự án trở lên",
        "WORKER_DUPLICATE_CCCD" to "Hồ sơ thuộc nhóm trùng CCCD cần rà soát"
    )

    private val BANK_INELIGIBLE_REASON_LABELS = mapOf(
        "BANK_ACCOUNT_MISSING" to "Hồ sơ NLĐ chưa có đầy đủ thông tin tài khoản ngân hàng",
        "BANK_ACCOUNT_NOT_SELF_OWNED" to "Tên chủ tài khoản ngân hàng không trùng khớp với họ tên NLĐ (không chính chủ)"
    )

    private val CONFLICT_ERROR_CODES = setOf(
        "SALARY_ADVANCE_VERSION_CONFLICT",
        "SALARY_ADVANCE_STATE_CONFLICT",
        "SALARY_ADVANCE_EVIDENCE_VERSION_CONFLICT",
        "SALARY_ADVANCE_WORKER_PORTRAIT_VERSION_CONFLICT",
        "SALARY_ADVANCE_IDEMPOTENCY_CONFLICT"
    )

    fun canShowPmSalaryAdvanceEntry(context: SalaryAdvanceActorContext?): Boolean {
        if (context == null) return false
        return context.isProjectManager && context.activeManagedProjects.isNotEmpty()
    }

    fun errorMessageForCode(code: String?): String {
        if (code.isNullOrBlank()) {
            return ERROR_MESSAGES.getValue("SALARY_ADVANCE_UNAVAILABLE")
        }
        return ERROR_MESSAGES[code.trim()] ?: ERROR_MESSAGES.getValue("SALARY_ADVANCE_UNAVAILABLE")
    }

    fun isConflictError(statusCode: Int?, backendCode: String?): Boolean {
        if (statusCode == 409) return true
        return backendCode != null && backendCode in CONFLICT_ERROR_CODES
    }

    fun newIdempotencyKey(prefix: String = "sa-create"): String {
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "").take(20).ifBlank { "sa" }
        return "$safePrefix-${UUID.randomUUID()}"
    }

    fun isProjectWorkerHiddenFromPicker(worker: SalaryAdvanceWorkerOption?): Boolean {
        if (worker == null) return true
        if (worker.employmentStatus.equals("OFF", ignoreCase = true)) return true
        if (worker.providerType.equals("vendor", ignoreCase = true)) return true
        if (
            worker.ineligibleReasons.any {
                it.equals("WORKER_STATUS_OFF", ignoreCase = true) ||
                    it.equals("WORKER_VENDOR_RECRUITED", ignoreCase = true)
            }
        ) {
            return true
        }
        return false
    }

    fun filterSelectableProjectWorkers(
        workers: List<SalaryAdvanceWorkerOption>
    ): List<SalaryAdvanceWorkerOption> {
        return workers.filterNot { isProjectWorkerHiddenFromPicker(it) }
    }

    fun getWorkerIneligibleReasonLabel(reason: String): String {
        return WORKER_INELIGIBLE_REASON_LABELS[reason.trim()] ?: reason
    }

    fun getBankIneligibleReasonLabel(reason: String): String {
        return BANK_INELIGIBLE_REASON_LABELS[reason.trim()] ?: reason
    }

    /**
     * Uses backend-provided eligibility flags/reasons as the single source of truth.
     * Never recalculates duplicate CCCD or permissions on the client.
     */
    fun describeWorkerEligibility(worker: SalaryAdvanceWorkerOption): SalaryAdvanceWorkerEligibilityView {
        val reasons = worker.ineligibleReasons.distinct()
        val isEligible = worker.isEligible && reasons.isEmpty()
        val reasonLabels = reasons.map { getWorkerIneligibleReasonLabel(it) }
        val portraitStatusLabel = if (worker.hasPortrait) {
            "Đã có ảnh chân dung"
        } else {
            "Thiếu ảnh chân dung hồ sơ"
        }

        if (!isEligible) {
            val effectiveReasonLabels = if (reasonLabels.isNotEmpty()) {
                reasonLabels
            } else {
                listOf("Không đủ điều kiện nghiệp vụ theo máy chủ")
            }
            val joined = effectiveReasonLabels.joinToString("; ")
            return SalaryAdvanceWorkerEligibilityView(
                isEligible = false,
                statusLabel = "Không đủ điều kiện",
                reasonCodes = reasons,
                reasonLabels = effectiveReasonLabels,
                blockingMessage = "Không thể ứng lương cho NLĐ này: $joined.",
                portraitStatusLabel = portraitStatusLabel,
                hasPortrait = worker.hasPortrait
            )
        }

        return SalaryAdvanceWorkerEligibilityView(
            isEligible = true,
            statusLabel = "Đủ điều kiện (ON)",
            reasonCodes = emptyList(),
            reasonLabels = emptyList(),
            blockingMessage = null,
            portraitStatusLabel = portraitStatusLabel,
            hasPortrait = worker.hasPortrait
        )
    }

    /**
     * Uses backend-returned `bankTransferEligible`, `bankIneligibleReason`, and `bankAccount`
     * snapshot directly without performing client-side account-holder name verification.
     */
    fun describeWorkerBankTransferEligibility(
        worker: SalaryAdvanceWorkerOption
    ): SalaryAdvanceWorkerBankTransferView {
        val acct = worker.bankAccount
        val institutionName = acct?.institutionName?.trim()?.takeIf { it.isNotEmpty() }
        val accountNumber = acct?.accountNumber?.trim()?.takeIf { it.isNotEmpty() }
        val accountNumberMasked = acct?.accountNumberMasked?.trim()?.takeIf { it.isNotEmpty() }
            ?: accountNumber
        val accountHolderName = acct?.accountHolderName?.trim()?.takeIf { it.isNotEmpty() }
        val bankId = acct?.bankId?.trim()?.takeIf { it.isNotEmpty() }
        val hasBankAccount = acct?.hasBankAccount ?: (accountNumber != null && accountHolderName != null)
        val isSelfOwned = acct?.isSelfOwned ?: false

        if (worker.bankTransferEligible) {
            return SalaryAdvanceWorkerBankTransferView(
                canUseBankTransfer = true,
                hasBankAccount = hasBankAccount,
                isSelfOwned = true,
                statusLabel = "Tài khoản chính chủ hợp lệ",
                reasonCode = null,
                blockingMessage = null,
                bankId = bankId,
                institutionName = institutionName,
                accountNumberDisplay = accountNumberMasked ?: accountNumber,
                accountHolderName = accountHolderName
            )
        }

        val reasonCode = when {
            worker.bankIneligibleReason == "BANK_ACCOUNT_NOT_SELF_OWNED" -> "BANK_ACCOUNT_NOT_SELF_OWNED"
            worker.bankIneligibleReason == "BANK_ACCOUNT_MISSING" -> "BANK_ACCOUNT_MISSING"
            hasBankAccount && !isSelfOwned -> "BANK_ACCOUNT_NOT_SELF_OWNED"
            else -> "BANK_ACCOUNT_MISSING"
        }

        return SalaryAdvanceWorkerBankTransferView(
            canUseBankTransfer = false,
            hasBankAccount = hasBankAccount,
            isSelfOwned = isSelfOwned,
            statusLabel = if (reasonCode == "BANK_ACCOUNT_NOT_SELF_OWNED") {
                "Tài khoản không chính chủ"
            } else {
                "Thiếu tài khoản ngân hàng"
            },
            reasonCode = reasonCode,
            blockingMessage = "${getBankIneligibleReasonLabel(reasonCode)}.",
            bankId = bankId,
            institutionName = institutionName,
            accountNumberDisplay = accountNumberMasked ?: accountNumber,
            accountHolderName = accountHolderName
        )
    }

    fun normalizePositiveVndInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (!Regex("^[0-9.,\\s_]+$").matches(trimmed)) return null
        val digitsOnly = trimmed.replace(Regex("[.,\\s_]"), "")
        if (!Regex("^[1-9][0-9]{0,18}$").matches(digitsOnly)) return null
        val parsed = runCatching { BigInteger(digitsOnly) }.getOrNull() ?: return null
        if (parsed < BigInteger.ONE || parsed > MAX_PG_BIGINT) return null
        return digitsOnly
    }

    fun formatVndAmount(amountRaw: String?): String {
        val trimmed = amountRaw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "0 ₫"
        val digits = if (trimmed.all { it.isDigit() }) trimmed else normalizePositiveVndInput(trimmed)
        if (digits.isNullOrEmpty()) return "0 ₫"
        val parsed = runCatching { BigInteger(digits) }.getOrNull() ?: return "0 ₫"
        if (parsed <= BigInteger.ZERO) return "0 ₫"
        val nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
        return "${nf.format(parsed)} ₫"
    }

    fun formatWorkDateVi(isoDate: String?, fallbackLabel: String = "Chưa cập nhật"): String {
        val trimmed = isoDate?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallbackLabel
        val match = ISO_DATE_PATTERN.matchEntire(trimmed) ?: return fallbackLabel
        val year = match.groupValues[1].toIntOrNull() ?: return fallbackLabel
        val month = match.groupValues[2].toIntOrNull() ?: return fallbackLabel
        val day = match.groupValues[3].toIntOrNull() ?: return fallbackLabel
        if (!isValidCalendarDate(year, month, day)) return fallbackLabel
        return "${match.groupValues[3]}/${match.groupValues[2]}/${match.groupValues[1]}"
    }

    fun formatDateTimeVi(isoTimestamp: String?): String {
        val trimmed = isoTimestamp?.trim().orEmpty()
        if (trimmed.isEmpty()) return "—"
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (pattern.endsWith("'Z'")) {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
            val parsedDate = runCatching { parser.parse(trimmed) }.getOrNull()
            if (parsedDate != null) {
                val outFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply {
                    timeZone = VIETNAM_TIMEZONE
                }
                return outFmt.format(parsedDate)
            }
        }
        return trimmed
    }

    private fun sanitizeNonUuidLabel(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || UUID_PATTERN.matches(trimmed)) return null
        return trimmed
    }

    fun resolveActorDisplayName(raw: String?, fallbackLabel: String = "Chưa cập nhật tên"): String {
        return sanitizeNonUuidLabel(raw) ?: fallbackLabel
    }

    fun formatWorkerRecruiterLabel(
        worker: SalaryAdvanceWorkerOption,
        fallbackLabel: String = "Chưa cập nhật"
    ): String = formatWorkerRecruiterLabel(
        recruiterDisplayName = worker.recruiterDisplayName,
        recruiterCode = worker.recruiterCode,
        providerType = worker.providerType,
        fallbackLabel = fallbackLabel
    )

    fun formatWorkerRecruiterLabel(
        recruiterDisplayName: String?,
        recruiterCode: String?,
        providerType: String?,
        fallbackLabel: String = "Chưa cập nhật"
    ): String {
        val safeName = sanitizeNonUuidLabel(recruiterDisplayName)
        val safeCode = sanitizeNonUuidLabel(recruiterCode)
        val isVendor = providerType.equals("vendor", ignoreCase = true)
        var base = safeName ?: safeCode
        if (base == null) {
            return if (isVendor) "Vendor (Chưa cập nhật tên)" else fallbackLabel
        }
        if (
            safeName != null &&
            safeCode != null &&
            !safeName.equals(safeCode, ignoreCase = true) &&
            !safeName.contains(safeCode, ignoreCase = true)
        ) {
            base = "$safeName ($safeCode)"
        }
        return if (isVendor && !base.contains("vendor", ignoreCase = true)) {
            "$base · Vendor"
        } else {
            base
        }
    }

    fun normalizeIsoDateInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        ISO_DATE_PATTERN.matchEntire(trimmed)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val d = m.groupValues[3].toIntOrNull() ?: return null
            if (isValidCalendarDate(y, mo, d)) {
                return trimmed
            }
            return null
        }
        UI_DATE_PATTERN.matchEntire(trimmed)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return null
            val mo = m.groupValues[2].toIntOrNull() ?: return null
            val y = m.groupValues[3].toIntOrNull() ?: return null
            if (isValidCalendarDate(y, mo, d)) {
                return "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}"
            }
            return null
        }
        return null
    }

    fun normalizeIsoMonthInput(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (ISO_MONTH_PATTERN.matches(trimmed)) return trimmed
        UI_MONTH_PATTERN.matchEntire(trimmed)?.let { m ->
            return "${m.groupValues[2]}-${m.groupValues[1]}"
        }
        return null
    }

    fun paymentMethodLabel(method: String?): String {
        return if (method.equals("BANK_TRANSFER", ignoreCase = true)) {
            "Chuyển khoản"
        } else {
            "Tiền mặt"
        }
    }

    fun requestStateShortLabel(state: String?): String {
        return when (state?.uppercase(Locale.ROOT)) {
            "OPEN" -> "Đang mở"
            "NEEDS_INFO" -> "Cần bổ sung"
            "APPROVED" -> "Đã chấp thuận"
            "REJECTED" -> "Đã từ chối"
            "WITHDRAWN" -> "Đã rút"
            "HANDLED_EXTERNALLY" -> "Xử lý ngoài BI"
            else -> state.orEmpty()
        }
    }

    fun evidenceReviewStateShortLabel(state: String?): String {
        return when (state?.uppercase(Locale.ROOT)) {
            "NOT_UPLOADED" -> "Chưa có ảnh"
            "PENDING_REVIEW" -> "Chờ đối chiếu"
            "REVIEWED" -> "Đã đối chiếu"
            "RETAKE_REQUIRED" -> "Cần gửi lại ảnh"
            else -> state.orEmpty()
        }
    }

    fun validateCreateDraft(
        projectId: String,
        hasActiveAccountant: Boolean?,
        workerEntryId: String,
        selectedWorker: SalaryAdvanceWorkerOption?,
        paymentMethod: String,
        requestedAmountRaw: String,
        initialContent: String
    ): CreateSalaryAdvanceValidationResult {
        val fieldErrors = linkedMapOf<String, String>()
        val cleanProjectId = projectId.trim()
        if (cleanProjectId.isEmpty()) {
            fieldErrors["projectId"] = "Vui lòng chọn dự án."
        } else if (hasActiveAccountant == false) {
            fieldErrors["projectId"] =
                "Dự án này chưa được gán Kế toán dự án đang hoạt động. Vui lòng liên hệ Admin cấu hình trước khi tạo yêu cầu."
        }

        val cleanWorkerId = workerEntryId.trim()
        if (cleanWorkerId.isEmpty()) {
            fieldErrors["workerEntryId"] = "Vui lòng chọn người lao động thuộc dự án."
        } else if (selectedWorker != null) {
            val eligibility = describeWorkerEligibility(selectedWorker)
            if (!eligibility.isEligible) {
                fieldErrors["workerEntryId"] =
                    eligibility.blockingMessage ?: "Người lao động không đủ điều kiện ứng lương."
            }
        }

        val cleanMethod = paymentMethod.trim().uppercase(Locale.ROOT)
        if (cleanMethod !in PAYMENT_METHODS) {
            fieldErrors["paymentMethod"] =
                "Vui lòng chọn phương thức nhận tiền (Tiền mặt hoặc Chuyển khoản)."
        } else if (cleanMethod == "BANK_TRANSFER" && selectedWorker != null) {
            val bankEligibility = describeWorkerBankTransferEligibility(selectedWorker)
            if (!bankEligibility.canUseBankTransfer) {
                fieldErrors["paymentMethod"] =
                    bankEligibility.blockingMessage
                        ?: "Người lao động chưa có tài khoản ngân hàng chính chủ hợp lệ để nhận chuyển khoản."
            }
        }

        val normalizedAmount = normalizePositiveVndInput(requestedAmountRaw)
        if (normalizedAmount == null) {
            fieldErrors["requestedAmountRaw"] = "Số tiền ứng phải là số nguyên dương (VNĐ)."
        }

        val cleanContent = initialContent.trim()
        if (cleanContent.isEmpty()) {
            fieldErrors["initialContent"] = "Vui lòng nhập nội dung đề nghị ứng lương."
        } else if (cleanContent.length > 2000) {
            fieldErrors["initialContent"] =
                "Nội dung đề nghị ứng lương không được vượt quá 2.000 ký tự."
        }

        if (fieldErrors.isNotEmpty() || normalizedAmount == null) {
            val firstError = fieldErrors.values.firstOrNull()
                ?: "Dữ liệu tạo yêu cầu ứng lương chưa hợp lệ."
            return CreateSalaryAdvanceValidationResult.Invalid(
                message = firstError,
                fieldErrors = fieldErrors
            )
        }

        return CreateSalaryAdvanceValidationResult.Valid(
            payload = CreateSalaryAdvancePayload(
                projectId = cleanProjectId,
                workerEntryId = cleanWorkerId,
                requestedAmountVnd = normalizedAmount,
                paymentMethod = cleanMethod,
                initialContent = cleanContent
            )
        )
    }

    fun buildProjectWorkersPath(
        projectId: String,
        search: String? = null,
        cursor: String? = null,
        pageSize: Int = 30
    ): String {
        val base = "https://placeholder.local/api/salary-advance/projects".toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegment(projectId.trim())
            .addPathSegment("workers")
            .addQueryParameter("page_size", pageSize.coerceIn(1, 100).toString())
        val cleanSearch = search?.trim().orEmpty()
        if (cleanSearch.isNotEmpty()) {
            base.addQueryParameter("search", cleanSearch.take(120))
        }
        val cleanCursor = cursor?.trim().orEmpty()
        if (cleanCursor.isNotEmpty()) {
            base.addQueryParameter("cursor", cleanCursor)
        }
        val built = base.build()
        return "${built.encodedPath}?${built.encodedQuery}"
    }

    fun buildProjectWorkerHistoryPath(
        projectId: String,
        workerEntryId: String,
        periodMode: String,
        month: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        cursor: String? = null,
        pageSize: Int = 20
    ): String {
        val cleanMode = periodMode.trim().uppercase(Locale.ROOT).ifEmpty { "THIS_MONTH" }
        val base = "https://placeholder.local/api/salary-advance/projects".toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegment(projectId.trim())
            .addPathSegment("workers")
            .addPathSegment(workerEntryId.trim())
            .addPathSegment("worker-history")
            .addQueryParameter("period_mode", cleanMode)
            .addQueryParameter("page_size", pageSize.coerceIn(1, 100).toString())

        if (cleanMode == "MONTH" && !month.isNullOrBlank()) {
            base.addQueryParameter("month", month.trim())
        }
        if (cleanMode == "CUSTOM_RANGE") {
            if (!fromDate.isNullOrBlank()) {
                base.addQueryParameter("from_date", fromDate.trim())
            }
            if (!toDate.isNullOrBlank()) {
                base.addQueryParameter("to_date", toDate.trim())
            }
        }
        if (!cursor.isNullOrBlank()) {
            base.addQueryParameter("cursor", cursor.trim())
        }
        val built = base.build()
        return "${built.encodedPath}?${built.encodedQuery}"
    }

    fun parseActorContext(data: JSONObject): SalaryAdvanceActorContext {
        val managedArr = data.optJSONArray("managed_projects") ?: JSONArray()
        val managedProjects = buildList {
            for (i in 0 until managedArr.length()) {
                val obj = managedArr.optJSONObject(i) ?: continue
                val projectId = obj.optString("project_id").trim()
                val displayName = obj.optString("display_name").trim()
                if (projectId.isEmpty() || displayName.isEmpty()) continue
                add(
                    SalaryAdvanceManagedProjectOption(
                        projectId = projectId,
                        displayName = displayName,
                        active = obj.optBoolean("active", false),
                        hasActiveAccountant = obj.optBoolean("has_active_accountant", false),
                        accountantUserId = obj.optString("accountant_user_id").trim().ifEmpty { null },
                        accountantDisplayName = obj.optString("accountant_display_name").trim().ifEmpty { null }
                    )
                )
            }
        }

        return SalaryAdvanceActorContext(
            appUserId = data.optString("app_user_id").trim(),
            displayName = data.optString("display_name").trim(),
            isProjectManager = data.optBoolean("is_project_manager", false),
            isProjectAccountant = data.optBoolean("is_project_accountant", false),
            isSupervisoryViewer = data.optBoolean("is_supervisory_viewer", false),
            isFullAdmin = data.optBoolean("is_full_admin", false),
            isBod = data.optBoolean("is_bod", false),
            managedProjects = managedProjects,
            authorizationDate = data.optString("authorization_date").trim()
        )
    }

    fun parseWorker30dSummary(obj: JSONObject?): SalaryAdvanceWorker30dSummary? {
        if (obj == null) return null
        val windowDays = obj.optInt("window_days", 0)
        val fromDate = obj.optString("window_from_date").trim()
        val toDate = obj.optString("window_to_date").trim()
        val totalCount = obj.optInt("total_count", -1)
        val totalAmount = obj.optString("total_amount_vnd").trim()
        if (
            windowDays <= 0 ||
            !ISO_DATE_PATTERN.matches(fromDate) ||
            !ISO_DATE_PATTERN.matches(toDate) ||
            totalCount < 0 ||
            !totalAmount.all { it.isDigit() } ||
            totalAmount.isEmpty()
        ) {
            return null
        }
        return SalaryAdvanceWorker30dSummary(
            windowDays = windowDays,
            windowFromDate = fromDate,
            windowToDate = toDate,
            timezone = obj.optString("timezone").trim().ifEmpty { "Asia/Ho_Chi_Minh" },
            qualifyingRule = obj.optString("qualifying_rule").trim().ifEmpty { "APPROVED_AND_EVIDENCE_REVIEWED" },
            totalCount = totalCount,
            totalAmountVnd = totalAmount
        )
    }

    fun parseWorkerListResponse(data: JSONObject): SalaryAdvanceWorkerListResponse {
        val workersArr = data.optJSONArray("workers") ?: JSONArray()
        val workers = buildList {
            for (i in 0 until workersArr.length()) {
                val obj = workersArr.optJSONObject(i) ?: continue
                val workerEntryId = obj.optString("worker_entry_id").trim()
                val employeeCode = obj.optString("employee_code").trim()
                if (workerEntryId.isEmpty() || employeeCode.isEmpty()) continue

                val reasonsArr = obj.optJSONArray("ineligible_reasons") ?: JSONArray()
                val reasons = buildList {
                    for (j in 0 until reasonsArr.length()) {
                        val r = reasonsArr.optString(j).trim()
                        if (r.isNotEmpty()) add(r)
                    }
                }

                val bankObj = obj.optJSONObject("bank_account")
                val bankAccount = bankObj?.let {
                    SalaryAdvanceWorkerBankAccountInfo(
                        hasBankAccount = it.optBoolean("has_bank_account", false),
                        bankId = it.optString("bank_id").trim().ifEmpty { null },
                        institutionName = it.optString("institution_name").trim().ifEmpty { null },
                        accountNumber = it.optString("account_number").trim().ifEmpty { null },
                        accountNumberMasked = it.optString("account_number_masked").trim().ifEmpty { null },
                        accountHolderName = it.optString("account_holder_name").trim().ifEmpty { null },
                        isSelfOwned = it.optBoolean("is_self_owned", false)
                    )
                }

                val hasExplicitBankEligible = obj.has("bank_transfer_eligible") && !obj.isNull("bank_transfer_eligible")
                val bankTransferEligible = if (hasExplicitBankEligible) {
                    obj.optBoolean("bank_transfer_eligible", false)
                } else {
                    bankAccount?.hasBankAccount == true && bankAccount.isSelfOwned
                }

                add(
                    SalaryAdvanceWorkerOption(
                        workerEntryId = workerEntryId,
                        employeeCode = employeeCode,
                        displayName = obj.optString("display_name").trim(),
                        firstWorkDate = obj.optString("first_work_date").trim(),
                        providerType = obj.optString("provider_type", "hrp").trim().ifEmpty { "hrp" },
                        recruiterDisplayName = obj.optString("recruiter_display_name").trim().ifEmpty { null },
                        recruiterCode = obj.optString("recruiter_code").trim().ifEmpty { null },
                        hasPortrait = obj.optBoolean("has_portrait", false),
                        employmentStatus = obj.optString("employment_status", "ON").trim().ifEmpty { "ON" },
                        activeOnProjectCount = obj.optInt("active_on_project_count", 1),
                        hasDuplicateCccd = obj.optBoolean("has_duplicate_cccd", false),
                        isEligible = obj.optBoolean("is_eligible", reasons.isEmpty()),
                        ineligibleReasons = reasons,
                        bankTransferEligible = bankTransferEligible,
                        bankIneligibleReason = obj.optString("bank_ineligible_reason").trim().ifEmpty { null },
                        bankAccount = bankAccount,
                        advance30dSummary = parseWorker30dSummary(obj.optJSONObject("advance_30d_summary"))
                    )
                )
            }
        }

        return SalaryAdvanceWorkerListResponse(
            projectId = data.optString("project_id").trim(),
            workers = filterSelectableProjectWorkers(workers),
            hasMore = data.optBoolean("has_more", false),
            nextCursor = data.optString("next_cursor").trim().ifEmpty { null }
        )
    }

    fun parseWorkerHistoryResponse(data: JSONObject): SalaryAdvanceWorkerHistoryResponse {
        val itemsArr = data.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.optJSONObject(i) ?: continue
                val requestId = obj.optString("request_id").trim()
                if (requestId.isEmpty()) continue
                add(
                    SalaryAdvanceWorkerHistoryItem(
                        requestId = requestId,
                        projectId = obj.optString("project_id").trim(),
                        projectDisplayName = obj.optString("project_display_name").trim(),
                        workerEntryId = obj.optString("worker_entry_id").trim(),
                        workerEmployeeCode = obj.optString("worker_employee_code").trim(),
                        workerDisplayName = obj.optString("worker_display_name").trim(),
                        requestedAmountVnd = obj.optString("requested_amount_vnd").trim(),
                        paymentMethod = obj.optString("payment_method", "CASH").trim().ifEmpty { "CASH" },
                        initialContent = obj.optString("initial_content").trim().ifEmpty { null },
                        requestState = obj.optString("request_state").trim(),
                        evidenceReviewState = obj.optString("evidence_review_state").trim(),
                        effectiveDate = obj.optString("effective_date").trim(),
                        effectiveAt = obj.optString("effective_at").trim(),
                        createdAt = obj.optString("created_at").trim(),
                        decidedAt = obj.optString("decided_at").trim().ifEmpty { null },
                        evidenceReviewedAt = obj.optString("evidence_reviewed_at").trim().ifEmpty { null },
                        createdByDisplayName = obj.optString("created_by_display_name").trim(),
                        decidedByDisplayName = obj.optString("decided_by_display_name").trim().ifEmpty { null },
                        evidenceReviewedByDisplayName = obj.optString("evidence_reviewed_by_display_name").trim().ifEmpty { null }
                    )
                )
            }
        }

        return SalaryAdvanceWorkerHistoryResponse(
            requestId = if (data.isNull("request_id")) null else data.optString("request_id").trim().ifEmpty { null },
            workerEntryId = data.optString("worker_entry_id").trim(),
            workerEmployeeCode = data.optString("worker_employee_code").trim(),
            workerDisplayName = data.optString("worker_display_name").trim(),
            periodMode = data.optString("period_mode", "THIS_MONTH").trim().ifEmpty { "THIS_MONTH" },
            month = if (data.isNull("month")) null else data.optString("month").trim().ifEmpty { null },
            fromDate = if (data.isNull("from_date")) null else data.optString("from_date").trim().ifEmpty { null },
            toDate = if (data.isNull("to_date")) null else data.optString("to_date").trim().ifEmpty { null },
            timezone = data.optString("timezone", "Asia/Ho_Chi_Minh").trim().ifEmpty { "Asia/Ho_Chi_Minh" },
            qualifyingRule = data.optString("qualifying_rule", "APPROVED_AND_EVIDENCE_REVIEWED").trim(),
            totalCount = data.optInt("total_count", 0).coerceAtLeast(0),
            totalAmountVnd = data.optString("total_amount_vnd", "0").trim().ifEmpty { "0" },
            items = items,
            hasMore = data.optBoolean("has_more", false),
            nextCursor = if (data.isNull("next_cursor")) null else data.optString("next_cursor").trim().ifEmpty { null }
        )
    }

    fun parseCreatedRequestResponse(data: JSONObject): SalaryAdvanceCreatedRequestSummary {
        val reqObj = data.optJSONObject("request") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "SALARY_ADVANCE_PAYLOAD_INVALID",
            message = errorMessageForCode("SALARY_ADVANCE_PAYLOAD_INVALID")
        )
        val requestId = reqObj.optString("request_id").trim()
        if (requestId.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "SALARY_ADVANCE_PAYLOAD_INVALID",
                message = errorMessageForCode("SALARY_ADVANCE_PAYLOAD_INVALID")
            )
        }
        return SalaryAdvanceCreatedRequestSummary(
            requestId = requestId,
            projectId = reqObj.optString("project_id").trim(),
            projectDisplayName = reqObj.optString("project_display_name").trim(),
            workerEntryId = reqObj.optString("worker_entry_id").trim(),
            workerEmployeeCode = reqObj.optString("worker_employee_code").trim(),
            workerDisplayName = reqObj.optString("worker_display_name").trim(),
            requestedAmountVnd = reqObj.optString("requested_amount_vnd").trim(),
            paymentMethod = reqObj.optString("payment_method", "CASH").trim().ifEmpty { "CASH" },
            initialContent = reqObj.optString("initial_content").trim(),
            requestState = reqObj.optString("request_state", "OPEN").trim(),
            evidenceReviewState = reqObj.optString("evidence_review_state", "NOT_UPLOADED").trim(),
            version = reqObj.optInt("version", 1),
            replayed = data.optBoolean("replayed", false)
        )
    }
}

class SalaryAdvanceService(
    private val httpClient: BackendHttpClient
) {
    suspend fun getContext(): SalaryAdvanceActorContext {
        val root = httpClient.getJson("/api/salary-advance/context")
        val data = root.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "SALARY_ADVANCE_PAYLOAD_INVALID",
            message = SalaryAdvanceContract.errorMessageForCode("SALARY_ADVANCE_PAYLOAD_INVALID")
        )
        return SalaryAdvanceContract.parseActorContext(data)
    }

    suspend fun listProjectWorkers(
        projectId: String,
        search: String? = null,
        cursor: String? = null,
        pageSize: Int = 30
    ): SalaryAdvanceWorkerListResponse {
        val path = SalaryAdvanceContract.buildProjectWorkersPath(
            projectId = projectId,
            search = search,
            cursor = cursor,
            pageSize = pageSize
        )
        val root = httpClient.getJson(path)
        val data = root.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "SALARY_ADVANCE_PAYLOAD_INVALID",
            message = SalaryAdvanceContract.errorMessageForCode("SALARY_ADVANCE_PAYLOAD_INVALID")
        )
        return SalaryAdvanceContract.parseWorkerListResponse(data)
    }

    suspend fun listProjectWorkerHistory(
        projectId: String,
        workerEntryId: String,
        periodMode: String,
        month: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        cursor: String? = null,
        pageSize: Int = 20
    ): SalaryAdvanceWorkerHistoryResponse {
        val path = SalaryAdvanceContract.buildProjectWorkerHistoryPath(
            projectId = projectId,
            workerEntryId = workerEntryId,
            periodMode = periodMode,
            month = month,
            fromDate = fromDate,
            toDate = toDate,
            cursor = cursor,
            pageSize = pageSize
        )
        val root = httpClient.getJson(path)
        val data = root.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "SALARY_ADVANCE_PAYLOAD_INVALID",
            message = SalaryAdvanceContract.errorMessageForCode("SALARY_ADVANCE_PAYLOAD_INVALID")
        )
        return SalaryAdvanceContract.parseWorkerHistoryResponse(data)
    }

    suspend fun createRequest(
        payload: CreateSalaryAdvancePayload,
        idempotencyKey: String
    ): SalaryAdvanceCreatedRequestSummary {
        val cleanKey = idempotencyKey.trim()
        val body = JSONObject().apply {
            put("project_id", payload.projectId)
            put("worker_entry_id", payload.workerEntryId)
            put("requested_amount_vnd", payload.requestedAmountVnd)
            put("payment_method", payload.paymentMethod)
            put("initial_content", payload.initialContent)
            put("idempotency_key", cleanKey)
        }
        val root = httpClient.postJson(
            path = "/api/salary-advance/requests",
            bodyJson = body,
            idempotencyKey = cleanKey
        )
        val data = root.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "SALARY_ADVANCE_PAYLOAD_INVALID",
            message = SalaryAdvanceContract.errorMessageForCode("SALARY_ADVANCE_PAYLOAD_INVALID")
        )
        return SalaryAdvanceContract.parseCreatedRequestResponse(data)
    }
}
