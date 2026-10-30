package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe sticky idempotency key store:
 * Preserves the exact Idempotency-Key across retries of the same normalized payload/intent,
 * and clears it only after server confirmation or deterministic payload change.
 */
class IntentIdempotencyStore(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val keysByIntent = ConcurrentHashMap<String, String>()

    fun resolve(intentSignature: String): String {
        return keysByIntent.computeIfAbsent(intentSignature) { keyGenerator() }
    }

    fun clear(intentSignature: String) {
        keysByIntent.remove(intentSignature)
    }

    fun shouldPreserveForRetry(error: BackendApiException): Boolean {
        return error.kind == BackendFailureKind.NETWORK ||
            error.kind == BackendFailureKind.RATE_LIMITED ||
            error.kind == BackendFailureKind.SERVER_UNAVAILABLE
    }
}

object DirectEntryContract {
    const val CONTRACT_VERSION_V1_1 = "worker-profile/1.1"
    const val CONTRACT_VERSION_V1_2 = "worker-profile/1.2"
    const val MAX_STAGE_BYTES = 10L * 1024L * 1024L

    val ALLOWED_STAGE_DOCUMENT_TYPES = setOf(
        "WORKER_PORTRAIT",
        "CCCD_FRONT",
        "CCCD_BACK",
        "EMPLOYMENT_CONTRACT"
    )

    val ALLOWED_WORKER_SCOPES = setOf(
        "managed",
        "recruited",
        "team",
        "all"
    )

    val CANONICAL_CHANGE_TARGETS = setOf(
        "ENTRY_FIELD",
        "PAYMENT",
        "WORK_STATUS"
    )

    /** Synchronized with App BI `DIRECT_ENTRY_GENDER_OPTIONS` (only Nam / Nữ selectable in UI). */
    val DIRECT_ENTRY_GENDER_OPTIONS = listOf("Nam", "Nữ")

    /** Synchronized with App BI `DIRECT_EDIT_DOCUMENT_TYPES` (`worker-operations-model.ts`). */
    val DIRECT_EDIT_DOCUMENT_TYPES = listOf(
        DirectEditDocumentTypeSpec(
            type = "WORKER_PORTRAIT",
            label = "Ảnh NLĐ",
            acceptMimeTypes = listOf("image/jpeg", "image/png")
        ),
        DirectEditDocumentTypeSpec(
            type = "CCCD_FRONT",
            label = "CCCD mặt trước",
            acceptMimeTypes = listOf("image/jpeg", "image/png", "application/pdf")
        ),
        DirectEditDocumentTypeSpec(
            type = "CCCD_BACK",
            label = "CCCD mặt sau",
            acceptMimeTypes = listOf("image/jpeg", "image/png", "application/pdf")
        ),
        DirectEditDocumentTypeSpec(
            type = "EMPLOYMENT_CONTRACT",
            label = "Hợp đồng lao động",
            acceptMimeTypes = listOf("image/jpeg", "image/png", "application/pdf")
        )
    )

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )

    private val ISO_DATE_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val DMY_FLEX_DATE_REGEX = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$")
    private val CANONICAL_NATIONAL_ID_REGEX = Regex("^(\\d{9}|\\d{12})$")
    private val CCCD_12_DIGITS_REGEX = Regex("^\\d{12}$")
    private val PAYMENT_ACCOUNT_DIGITS_REGEX = Regex("^\\d{1,64}$")
    private val SAFE_CATALOG_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val EMPLOYEE_CODE_REGEX = Regex("^hrp-(\\d{4})-(\\d{6})$")
    private val LABOR_TYPE_KEY_REGEX = Regex("^[A-Z][A-Z0-9_]{0,63}$")
    private val COMBINING_DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun isUuid(raw: String): Boolean = UUID_REGEX.matches(raw.trim())

    fun isValidUiOrIsoDate(rawDate: String): Boolean =
        runCatching { uiDateToIsoDate(rawDate) }.isSuccess

    fun isCanonicalNationalId(raw: String): Boolean =
        CANONICAL_NATIONAL_ID_REGEX.matches(raw.trim())

    fun isValidCccd12(raw: String): Boolean {
        val trimmed = raw.trim()
        return CCCD_12_DIGITS_REGEX.matches(trimmed) && trimmed != "000000000000"
    }

    fun hasControlCharacter(raw: String): Boolean =
        raw.any { it.code < 32 || it.code == 127 }

    fun isFormulaLikeCell(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotEmpty() && trimmed[0] in charArrayOf('=', '+', '-', '@')
    }

    fun isValidFreeDateText(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isEmpty() || (trimmed.length <= 10 && !isFormulaLikeCell(trimmed))
    }

    fun genderUiLabel(canonicalOrNull: String?): String {
        return when (canonicalOrNull?.trim()?.uppercase(Locale.ROOT)) {
            "MALE", "NAM" -> "Nam"
            "FEMALE", "NỮ" -> "Nữ"
            "OTHER", "KHÁC" -> "Khác"
            null, "" -> "—"
            else -> canonicalOrNull
        }
    }

    fun workStatusUiLabel(status: String?): String {
        return when (status?.trim()?.uppercase(Locale.ROOT)) {
            "ON" -> "Đang làm"
            "UNCONFIRMED" -> "Đang chờ việc"
            "OFF" -> "Đã nghỉ"
            null, "" -> "—"
            else -> status
        }
    }

    fun documentTypeUiLabel(documentType: String?): String {
        return when (documentType?.trim()?.uppercase(Locale.ROOT)) {
            "WORKER_PORTRAIT" -> "Ảnh chân dung"
            "CCCD_FRONT" -> "CCCD mặt trước"
            "CCCD_BACK" -> "CCCD mặt sau"
            "EMPLOYMENT_CONTRACT" -> "Hợp đồng lao động"
            else -> "Tài liệu đính kèm"
        }
    }

    fun allowedWorkStatusTargets(currentStatus: String?): List<String> {
        return when (currentStatus?.trim()?.uppercase(Locale.ROOT)) {
            "UNCONFIRMED" -> listOf("ON", "OFF")
            "ON" -> listOf("OFF")
            else -> emptyList()
        }
    }

    /**
     * Selectable status chips in Direct Edit dialog:
     * - ON (Đang làm): keep ON (when editing other fields) or transition ONLY to OFF (Đã nghỉ).
     * - UNCONFIRMED (Đang chờ việc): keep UNCONFIRMED, or transition to ON (Đang làm) / OFF (Đã nghỉ).
     * - OFF (Đã nghỉ): locked at OFF (cannot transition back to ON or UNCONFIRMED).
     */
    fun selectableDirectEditStatuses(currentStatus: String?): List<String> {
        return when (currentStatus?.trim()?.uppercase(Locale.ROOT)) {
            "UNCONFIRMED" -> listOf("UNCONFIRMED", "ON", "OFF")
            "ON" -> listOf("ON", "OFF")
            "OFF" -> listOf("OFF")
            else -> emptyList()
        }
    }

    fun defaultSeasonalLaborType(laborTypes: List<CatalogLaborTypeOption>): CatalogLaborTypeOption? {
        if (laborTypes.isEmpty()) return null
        val sorted = laborTypes.sortedBy { it.sortOrder }
        return sorted.firstOrNull {
            it.displayName.trim().equals("Thời vụ", ignoreCase = true) ||
                it.laborTypeKey.trim().equals("TEMPORARY", ignoreCase = true)
        } ?: sorted.firstOrNull {
            it.displayName.contains("thời vụ", ignoreCase = true) ||
                it.laborTypeKey.contains("SEASONAL", ignoreCase = true) ||
                it.laborTypeKey.contains("TEMP", ignoreCase = true)
        } ?: sorted.firstOrNull()
    }

    fun createDefaultWorkerDraft(
        catalog: DirectEntryCatalog? = null,
        localId: String = UUID.randomUUID().toString()
    ): NewWorkerEntryDraft {
        val defaultLabor = catalog?.let { defaultSeasonalLaborType(it.laborTypes) }
        return NewWorkerEntryDraft(
            localId = localId,
            laborTypeKey = defaultLabor?.laborTypeKey.orEmpty(),
            laborTypeDisplay = defaultLabor?.displayName.orEmpty(),
            firstWorkDateUi = todayUiInHcm(),
            employmentStatusUi = "Đang làm",
            genderUi = ""
        )
    }

    fun todayIsoInHcm(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }.format(Date())
    }

    fun todayUiInHcm(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }.format(Date())
    }

    private fun validateStrictCalendarDate(year: Int, month: Int, day: Int) {
        if (year < 1900 || year > 2100) {
            throw IllegalArgumentException("Year out of bounds: $year")
        }
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        // Trigger strict validation (rejects e.g. 2026-02-29 or month 13)
        cal.timeInMillis
    }

    fun uiDateToIsoDate(rawDate: String): String {
        val cleaned = rawDate.trim()
        if (cleaned.isEmpty()) {
            throw invalid("PROFILE_DATE_INVALID", "Ngày tháng không được để trống.")
        }
        return try {
            val (year, month, day) = if (cleaned.contains("/")) {
                val parts = cleaned.split("/")
                if (parts.size != 3 || parts[2].length != 4) {
                    throw IllegalArgumentException("Expected DD/MM/YYYY")
                }
                Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else {
                val match = ISO_DATE_REGEX.matchEntire(cleaned)
                    ?: throw IllegalArgumentException("Expected YYYY-MM-DD")
                Triple(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt()
                )
            }
            validateStrictCalendarDate(year, month, day)
            "%04d-%02d-%02d".format(Locale.US, year, month, day)
        } catch (e: Exception) {
            throw invalid("PROFILE_DATE_INVALID", "Ngày '$cleaned' không đúng định dạng DD/MM/YYYY hợp lệ.", e)
        }
    }

    fun isoDateToUiDate(isoDate: String): String {
        val cleaned = isoDate.trim()
        return try {
            val match = ISO_DATE_REGEX.matchEntire(cleaned) ?: return cleaned
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            validateStrictCalendarDate(year, month, day)
            "%02d/%02d/%04d".format(Locale.US, day, month, year)
        } catch (_: Exception) {
            cleaned
        }
    }

    fun mapProviderType(rawProvider: String): String {
        return when (rawProvider.trim().lowercase(Locale.ROOT)) {
            "hrp" -> "hrp"
            "vendor" -> "vendor"
            else -> throw invalid("BATCH_INVALID", "Nguồn tuyển dụng phải là HRP hoặc Vendor.")
        }
    }

    fun mapGender(genderUi: String): JSONObject {
        val trimmed = genderUi.trim()
        if (trimmed.isEmpty()) {
            return JSONObject().put("state", "omitted")
        }
        val canonical = when (trimmed) {
            "Nam", "MALE" -> "MALE"
            "Nữ", "FEMALE" -> "FEMALE"
            else -> throw invalid("GENDER_VOCABULARY_INVALID", "Giới tính chỉ hỗ trợ Nam hoặc Nữ (theo hợp đồng App BI).")
        }
        return JSONObject()
            .put("state", "provided")
            .put("value", canonical)
    }

    fun mapInitialEmploymentStatus(statusUi: String): JSONObject {
        val canonical = when (statusUi.trim()) {
            "Đang làm", "ON" -> "ON"
            "Đang chờ việc", "UNCONFIRMED" -> "UNCONFIRMED"
            else -> throw invalid("BATCH_INVALID", "Trạng thái khởi tạo chỉ hỗ trợ Đang làm hoặc Đang chờ việc.")
        }
        return JSONObject().put("initial_status", canonical)
    }

    fun optionalTextField(raw: String, maxLength: Int = Int.MAX_VALUE): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return JSONObject().put("state", "omitted")
        }
        if (trimmed.length > maxLength) {
            throw invalid("BATCH_INVALID", "Nội dung nhập vượt quá độ dài tối đa ($maxLength ký tự).")
        }
        return JSONObject()
            .put("state", "provided")
            .put("value", trimmed)
    }

    /**
     * Synchronized with App BI `optionalFreeDate` (`full-profile-contract.ts:152` & `worker-profile-paste.ts:236`):
     * `date_of_birth` and `national_id_issued_at` are plain text up to 10 characters; raw text is preserved
     * without ISO date conversion.
     */
    fun optionalFreeDateField(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return JSONObject().put("state", "omitted")
        }
        if (isFormulaLikeCell(trimmed)) {
            throw invalid("PASTE_FORMULA_CELL", "Trường ngày không chấp nhận ký tự công thức đầu dòng.")
        }
        if (trimmed.length > 10) {
            throw invalid("PASTE_TEXT_TOO_LONG", "Trường ngày tối đa 10 ký tự (ví dụ DD/MM/YYYY).")
        }
        return JSONObject()
            .put("state", "provided")
            .put("value", trimmed)
    }

    fun optionalNationalIdField(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return JSONObject().put("state", "omitted")
        }
        if (!isCanonicalNationalId(trimmed)) {
            throw invalid("NATIONAL_ID_INVALID", "Số CCCD/CMND phải gồm đúng 9 hoặc 12 chữ số.")
        }
        return JSONObject()
            .put("state", "provided")
            .put("value", trimmed)
    }

    fun requiredNationalId12Field(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (!isValidCccd12(trimmed)) {
            throw invalid("NATIONAL_ID_INVALID", "Số CCCD bắt buộc gồm đúng 12 chữ số hợp lệ (giữ nguyên số 0 ở đầu).")
        }
        return JSONObject()
            .put("state", "provided")
            .put("value", trimmed)
    }

    fun optionalDateField(uiOrIsoDate: String): JSONObject {
        val trimmed = uiOrIsoDate.trim()
        return if (trimmed.isEmpty()) {
            JSONObject().put("state", "omitted")
        } else {
            JSONObject()
                .put("state", "provided")
                .put("value", uiDateToIsoDate(trimmed))
        }
    }

    fun buildAccountMetadataPayment(
        accountNumber: String,
        bankName: String,
        accountHolderName: String
    ): Any {
        val acc = accountNumber.trim()
        val bank = bankName.trim()
        val holder = accountHolderName.trim()
        if (acc.isEmpty() && bank.isEmpty() && holder.isEmpty()) {
            return JSONObject.NULL
        }
        if (acc.length > 64 || hasControlCharacter(acc) ||
            bank.length > 256 || hasControlCharacter(bank) ||
            holder.length > 256 || hasControlCharacter(holder)
        ) {
            throw invalid("PAYMENT_DETAILS_INVALID", "Thông tin tài khoản ngân hàng không hợp lệ hoặc vượt quá giới hạn ký tự.")
        }
        return JSONObject().apply {
            put("state", "provided")
            if (acc.isNotEmpty()) put("account_number", acc)
            if (bank.isNotEmpty()) put("bank_name", bank)
            if (holder.isNotEmpty()) put("account_holder_name", holder)
        }
    }

    fun buildFullProfileV11Payload(drafts: List<NewWorkerEntryDraft>): JSONObject =
        buildFullProfileV12Payload(drafts)

    fun buildFullProfileV12Payload(drafts: List<NewWorkerEntryDraft>): JSONObject {
        if (drafts.isEmpty()) {
            throw invalid("BATCH_SIZE_INVALID", "Cần ít nhất 1 hồ sơ hợp lệ để gửi lên máy chủ.")
        }
        val rowsArray = JSONArray()
        for (draft in drafts) {
            val projectId = draft.projectId.trim()
            val recruiterId = draft.recruiterId.trim()
            val laborType = draft.laborTypeKey.trim()
            val displayName = draft.fullName.trim()

            if (!SAFE_CATALOG_ID_REGEX.matches(projectId) ||
                !UUID_REGEX.matches(recruiterId) ||
                laborType.isEmpty() ||
                draft.fullName.length > 256 ||
                isFormulaLikeCell(displayName)
            ) {
                throw invalid("BATCH_INVALID", "Vui lòng chọn đầy đủ Dự án, Người tuyển, Loại hình LĐ từ danh mục (Họ tên không bắt buộc, tối đa 256 ký tự).")
            }

            val rowObj = JSONObject().apply {
                put("project_id", projectId)
                put("first_work_date", uiDateToIsoDate(draft.firstWorkDateUi))
                put("provider_type", mapProviderType(draft.providerType))
                put("recruiter_id", recruiterId)
                put("labor_type", laborType)
                put("display_name", displayName)
                put(
                    "worker",
                    JSONObject().apply {
                        put("gender", mapGender(draft.genderUi))
                        put("date_of_birth", optionalFreeDateField(draft.birthDateUi))
                        put("national_id", requiredNationalId12Field(draft.cccd))
                        put("national_id_issued_at", optionalFreeDateField(draft.cccdIssueDateUi))
                        put("national_id_issued_place", optionalTextField(draft.cccdIssuePlace, 256))
                        put("address", optionalTextField(draft.address, 1024))
                        put("phone", optionalTextField(draft.phone, 64))
                    }
                )
                put("general_note", optionalTextField(draft.note, 4000))
                put(
                    "payment",
                    buildAccountMetadataPayment(
                        accountNumber = draft.bankAccountNumber,
                        bankName = draft.bankName,
                        accountHolderName = draft.bankAccountHolder
                    )
                )
                put("employment", mapInitialEmploymentStatus(draft.employmentStatusUi))

                draft.portraitStage?.stageId?.trim()?.takeIf { it.isNotEmpty() }?.let { portraitStageId ->
                    put("portrait_stage_id", portraitStageId)
                }

                val docStages = JSONObject()
                draft.cccdFrontStage?.stageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    docStages.put("CCCD_FRONT", it)
                }
                draft.cccdBackStage?.stageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    docStages.put("CCCD_BACK", it)
                }
                draft.contractStage?.stageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    docStages.put("EMPLOYMENT_CONTRACT", it)
                }
                if (docStages.length() > 0) {
                    put("document_stage_ids", docStages)
                }
            }
            rowsArray.put(rowObj)
        }
        return JSONObject()
            .put("contract_version", CONTRACT_VERSION_V1_2)
            .put("rows", rowsArray)
    }

    fun buildChangeRequestPayload(
        entryId: String,
        targetKind: String,
        expectedVersion: Int,
        proposal: JSONObject,
        reason: String?,
        idempotencyKey: String
    ): JSONObject {
        if (!UUID_REGEX.matches(entryId.trim())) {
            throw invalid("CHANGE_REQUEST_INVALID", "Mã hồ sơ (entry_id) không hợp lệ.")
        }
        if (targetKind !in CANONICAL_CHANGE_TARGETS) {
            throw invalid("CHANGE_REQUEST_INVALID", "Loại đề xuất thay đổi không hợp lệ: $targetKind")
        }
        if (expectedVersion < 1) {
            throw invalid("CHANGE_REQUEST_INVALID", "Phiên bản đối chiếu không hợp lệ.")
        }
        if (proposal.length() == 0) {
            throw invalid("CHANGE_REQUEST_INVALID", "Nội dung đề xuất chưa có thay đổi.")
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length > 128 || hasControlCharacter(idempotencyKey)) {
            throw invalid("CHANGE_REQUEST_INVALID", "Thiếu khóa chống trùng lặp (idempotency_key).")
        }
        if (reason != null && reason.length > 4000) {
            throw invalid("CHANGE_REQUEST_INVALID", "Lý do đề xuất tối đa 4000 ký tự.")
        }
        val normalizedReason = reason?.trim()?.ifEmpty { null }
        val itemObj = JSONObject()
            .put("entry_id", entryId.trim())
            .put("target_kind", targetKind)
            .put("expected_version", expectedVersion)
            .put("proposal", proposal)

        return JSONObject()
            .put("items", JSONArray().put(itemObj))
            .put("reason", normalizedReason ?: JSONObject.NULL)
            .put("idempotency_key", idempotencyKey)
    }

    fun buildPaymentChangeProposal(
        state: String,
        accountNumber: String?,
        bankId: String?,
        accountHolderName: String?
    ): JSONObject {
        return if (state == "provided") {
            val acc = accountNumber?.trim().orEmpty()
            val bank = bankId?.trim().orEmpty()
            val holder = accountHolderName?.trim().orEmpty()
            if (acc.isEmpty() || bank.isEmpty() || holder.isEmpty()) {
                throw invalid("PAYMENT_INVALID", "Cần nhập đủ Số tài khoản, Ngân hàng từ danh mục và Chủ tài khoản.")
            }
            if (!PAYMENT_ACCOUNT_DIGITS_REGEX.matches(acc)) {
                throw invalid("PAYMENT_INVALID", "Số tài khoản chỉ được gồm chữ số (1–64 chữ số).")
            }
            if (!SAFE_CATALOG_ID_REGEX.matches(bank)) {
                throw invalid("PAYMENT_INVALID", "Mã ngân hàng không hợp lệ.")
            }
            if (holder.length > 256 || hasControlCharacter(holder)) {
                throw invalid("PAYMENT_INVALID", "Tên chủ tài khoản không hợp lệ (tối đa 256 ký tự).")
            }
            JSONObject()
                .put("state", "provided")
                .put("account_number", acc)
                .put("bank_id", bank)
                .put("account_holder_name", holder)
        } else {
            JSONObject()
                .put("state", state)
                .put("account_number", JSONObject.NULL)
                .put("bank_id", JSONObject.NULL)
                .put("account_holder_name", JSONObject.NULL)
        }
    }

    fun buildWorkStatusChangeProposal(
        status: String,
        effectiveDateUiOrIso: String,
        leaveReason: String?,
        baselineStatus: String? = null,
        baselineEffectiveDateIso: String? = null
    ): JSONObject {
        if (status !in setOf("ON", "UNCONFIRMED", "OFF")) {
            throw invalid("STATUS_INVALID", "Trạng thái làm việc không hợp lệ.")
        }
        if (baselineStatus != null) {
            val allowed = allowedWorkStatusTargets(baselineStatus)
            if (status !in allowed) {
                throw invalid(
                    "STATUS_TRANSITION_INVALID",
                    "Chuyển trạng thái từ ${workStatusUiLabel(baselineStatus)} sang ${workStatusUiLabel(status)} không được máy chủ hỗ trợ."
                )
            }
        }
        val isoDate = uiDateToIsoDate(effectiveDateUiOrIso)
        if (!baselineEffectiveDateIso.isNullOrBlank() && isoDate < baselineEffectiveDateIso) {
            throw invalid("STATUS_DATE_INVALID", "Ngày hiệu lực không được nhỏ hơn ngày hiệu lực hiện tại ($baselineEffectiveDateIso).")
        }
        return if (status == "OFF") {
            val reasonText = leaveReason?.trim().orEmpty()
            if (reasonText.isEmpty() || reasonText.length > 4000) {
                throw invalid("STATUS_REASON_REQUIRED", "Trạng thái Đã nghỉ bắt buộc nhập lý do nghỉ (tối đa 4000 ký tự).")
            }
            JSONObject()
                .put("status", "OFF")
                .put("effective_date", isoDate)
                .put("leave_reason", reasonText)
        } else {
            JSONObject()
                .put("status", status)
                .put("effective_date", isoDate)
                .put("leave_reason", JSONObject.NULL)
        }
    }

    fun buildPrivilegedEditPayload(
        expectedEntryVersion: Int,
        patch: JSONObject,
        reason: String?
    ): JSONObject {
        if (expectedEntryVersion < 1) {
            throw invalid("PRIVILEGED_EDIT_INVALID", "Phiên bản hồ sơ không hợp lệ.")
        }
        if (patch.length() == 0) {
            throw invalid("PRIVILEGED_EDIT_INVALID", "Chưa có trường thay đổi nào để cập nhật.")
        }
        if (reason != null && reason.length > 1000) {
            throw invalid("PRIVILEGED_EDIT_INVALID", "Lý do chỉnh sửa trực tiếp tối đa 1000 ký tự.")
        }
        val normalizedReason = reason?.trim()?.ifEmpty { null }
        return JSONObject()
            .put("expected_entry_version", expectedEntryVersion)
            .put("patch", patch)
            .put("reason", normalizedReason ?: JSONObject.NULL)
    }

    fun buildPrivilegedEditEmploymentStatus(
        status: String,
        effectiveDateUiOrIso: String,
        leaveReason: String?,
        baselineStatus: String? = null
    ): JSONObject {
        if (status !in setOf("ON", "UNCONFIRMED", "OFF")) {
            throw invalid("PRIVILEGED_EDIT_INVALID", "Trạng thái làm việc không hợp lệ.")
        }
        val normalizedBaseline = baselineStatus?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (normalizedBaseline != null && status != normalizedBaseline) {
            val allowed = allowedWorkStatusTargets(normalizedBaseline)
            if (status !in allowed) {
                throw invalid(
                    "STATUS_TRANSITION_INVALID",
                    "Chuyển trạng thái từ ${workStatusUiLabel(normalizedBaseline)} sang ${workStatusUiLabel(status)} không được phép."
                )
            }
        }
        val isoDate = uiDateToIsoDate(effectiveDateUiOrIso)
        return if (status == "OFF") {
            val reasonText = leaveReason?.trim().orEmpty()
            if (reasonText.isEmpty() || reasonText.length > 4000) {
                throw invalid("OFF_REQUIRES_DATE_AND_REASON", "Trạng thái Đã nghỉ bắt buộc nhập lý do nghỉ (tối đa 4000 ký tự).")
            }
            JSONObject()
                .put("status", "OFF")
                .put("effective_date", isoDate)
                .put("leave_reason", reasonText)
        } else {
            // Note: privileged-edit-api.ts rejects leave_reason unless undefined when status != "OFF"
            JSONObject()
                .put("status", status)
                .put("effective_date", isoDate)
        }
    }

    /**
     * Canonical VietQR bank catalog synchronized with App BI `vietqr-bank-catalog.ts` (all 60 entries).
     */
    val VIETQR_BANK_CATALOG: List<VietQrBankCatalogDefinition> = listOf(
        VietQrBankCatalogDefinition("ICB", "ICB", "VietinBank", "Ngân hàng TMCP Công thương Việt Nam", "970415", listOf("CTG", "VIETINBANK")),
        VietQrBankCatalogDefinition("VCB", "VCB", "Vietcombank", "Ngân hàng TMCP Ngoại Thương Việt Nam", "970436", listOf("VIETCOMBANK")),
        VietQrBankCatalogDefinition("BIDV", "BIDV", "BIDV", "Ngân hàng TMCP Đầu tư và Phát triển Việt Nam", "970418"),
        VietQrBankCatalogDefinition("VBA", "VBA", "Agribank", "Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam", "970405", listOf("AGRIBANK", "VBARD")),
        VietQrBankCatalogDefinition("OCB", "OCB", "OCB", "Ngân hàng TMCP Phương Đông", "970448", listOf("PHUONG DONG")),
        VietQrBankCatalogDefinition("MB", "MB", "MBBank", "Ngân hàng TMCP Quân đội", "970422", listOf("MBBANK", "QUAN DOI")),
        VietQrBankCatalogDefinition("TCB", "TCB", "Techcombank", "Ngân hàng TMCP Kỹ thương Việt Nam", "970407", listOf("TECHCOMBANK", "KY THUONG")),
        VietQrBankCatalogDefinition("ACB", "ACB", "ACB", "Ngân hàng TMCP Á Châu", "970416", listOf("A CHAU")),
        VietQrBankCatalogDefinition("VPB", "VPB", "VPBank", "Ngân hàng TMCP Việt Nam Thịnh Vượng", "970432", listOf("VPBANK", "VIET NAM THINH VUONG")),
        VietQrBankCatalogDefinition("TPB", "TPB", "TPBank", "Ngân hàng TMCP Tiên Phong", "970423", listOf("TPBANK", "TIEN PHONG")),
        VietQrBankCatalogDefinition("STB", "STB", "Sacombank", "Ngân hàng TMCP Sài Gòn Thương Tín", "970403", listOf("SACOMBANK", "SAI GON THUONG TIN")),
        VietQrBankCatalogDefinition("HDB", "HDB", "HDBank", "Ngân hàng TMCP Phát triển Thành phố Hồ Chí Minh", "970437", listOf("HDBANK")),
        VietQrBankCatalogDefinition("VCCB", "VCCB", "VietCapitalBank", "Ngân hàng TMCP Bản Việt", "970454", listOf("BVBANK", "BAN VIET", "VIETCAPITALBANK")),
        VietQrBankCatalogDefinition("SCB", "SCB", "SCB", "Ngân hàng TMCP Sài Gòn", "970429", listOf("SAI GON")),
        VietQrBankCatalogDefinition("VIB", "VIB", "VIB", "Ngân hàng TMCP Quốc tế Việt Nam", "970441", listOf("QUOC TE VIET NAM")),
        VietQrBankCatalogDefinition("SHB", "SHB", "SHB", "Ngân hàng TMCP Sài Gòn - Hà Nội", "970443", listOf("SAI GON HA NOI")),
        VietQrBankCatalogDefinition("EIB", "EIB", "Eximbank", "Ngân hàng TMCP Xuất Nhập khẩu Việt Nam", "970431", listOf("EXIMBANK", "XUAT NHAP KHAU")),
        VietQrBankCatalogDefinition("MSB", "MSB", "MSB", "Ngân hàng TMCP Hàng Hải Việt Nam", "970426", listOf("MARITIMEBANK", "HANG HAI")),
        VietQrBankCatalogDefinition("CAKE", "CAKE", "CAKE", "TMCP Việt Nam Thịnh Vượng - Ngân hàng số CAKE by VPBank", "546034", listOf("CAKE BY VPBANK")),
        VietQrBankCatalogDefinition("UBANK", "Ubank", "Ubank", "TMCP Việt Nam Thịnh Vượng - Ngân hàng số Ubank by VPBank", "546035", listOf("UBANK BY VPBANK")),
        VietQrBankCatalogDefinition("VIKKI", "VIKKI", "Vikki", "Ngân hàng TNHH MTV Số Vikki", "970406", listOf("DONGABANK", "DAB", "DONG A", "VIKKI BY HDBANK")),
        VietQrBankCatalogDefinition("TIMO", "TIMO", "Timo", "Ngân hàng số Timo by Ban Viet Bank (Timo by Ban Viet Bank)", "963388", listOf("TIMO BY BAN VIET BANK")),
        VietQrBankCatalogDefinition("VNPTMONEY", "VNPTMONEY", "VNPTMoney", "Trung tâm Dịch vụ tài chính số VNPT - Chi nhánh Tổng công ty truyền thông", "971011", listOf("VNPT MONEY")),
        VietQrBankCatalogDefinition("VIETTEL_PAY", "VIETTEL PAY", "ViettelMoney", "Tổng Công ty Dịch vụ số Viettel - Chi nhánh tập đoàn công nghiệp viễn thông Quân Đội", "971005", listOf("VIETTELMONEY", "VIETTEL MONEY", "VIETTELPAY")),
        VietQrBankCatalogDefinition("SGICB", "SGICB", "SaigonBank", "Ngân hàng TMCP Sài Gòn Công Thương", "970400", listOf("SAIGONBANK", "SAI GON CONG THUONG")),
        VietQrBankCatalogDefinition("BAB", "BAB", "BacABank", "Ngân hàng TMCP Bắc Á", "970409", listOf("BACABANK", "BAC A")),
        VietQrBankCatalogDefinition("PVCB", "PVCB", "PVcomBank", "Ngân hàng TMCP Đại Chúng Việt Nam", "970412", listOf("PVCOMBANK", "DAI CHUNG")),
        VietQrBankCatalogDefinition("PVDB", "PVDB", "PVcomBank Pay", "Ngân hàng TMCP Đại Chúng Việt Nam Ngân hàng số", "971133", listOf("PVCOMBANK PAY")),
        VietQrBankCatalogDefinition("PBVN", "PBVN", "PublicBank", "Ngân hàng TNHH MTV Public Việt Nam", "970439", listOf("PUBLICBANK", "PUBLIC BANK", "VID PUBLIC")),
        VietQrBankCatalogDefinition("MBV", "MBV", "MBV", "Ngân hàng TNHH MTV Việt Nam Hiện Đại", "970414", listOf("OCEANBANK", "DAI DUONG", "VIET NAM HIEN DAI")),
        VietQrBankCatalogDefinition("NCB", "NCB", "NCB", "Ngân hàng TMCP Quốc Dân", "970419", listOf("QUOC DAN")),
        VietQrBankCatalogDefinition("SHBVN", "SHBVN", "ShinhanBank", "Ngân hàng TNHH MTV Shinhan Việt Nam", "970424", listOf("SHINHAN", "SHINHANBANK")),
        VietQrBankCatalogDefinition("ABB", "ABB", "ABBANK", "Ngân hàng TMCP An Bình", "970425", listOf("ABBANK", "AN BINH")),
        VietQrBankCatalogDefinition("VAB", "VAB", "VietABank", "Ngân hàng TMCP Việt Á", "970427", listOf("VIETABANK", "VIET A")),
        VietQrBankCatalogDefinition("NAB", "NAB", "NamABank", "Ngân hàng TMCP Nam Á", "970428", listOf("NAMABANK", "NAM A")),
        VietQrBankCatalogDefinition("PGB", "PGB", "PGBank", "Ngân hàng TMCP Thịnh vượng và Phát triển", "970430", listOf("PGBANK", "XANG DAU PETROLIMEX")),
        VietQrBankCatalogDefinition("VIETBANK", "VIETBANK", "VietBank", "Ngân hàng TMCP Việt Nam Thương Tín", "970433", listOf("VIET NAM THUONG TIN")),
        VietQrBankCatalogDefinition("BVB", "BVB", "BaoVietBank", "Ngân hàng TMCP Bảo Việt", "970438", listOf("BAOVIETBANK", "BAO VIET")),
        VietQrBankCatalogDefinition("SEAB", "SEAB", "SeABank", "Ngân hàng TMCP Đông Nam Á", "970440", listOf("SEABANK", "DONG NAM A")),
        VietQrBankCatalogDefinition("COOPBANK", "COOPBANK", "COOPBANK", "Ngân hàng Hợp tác xã Việt Nam", "970446", listOf("HOP TAC XA")),
        VietQrBankCatalogDefinition("LPB", "LPB", "LPBank", "Ngân hàng TMCP Lộc Phát Việt Nam", "970449", listOf("LPBANK", "LIENVIETPOSTBANK", "LOC PHAT", "BUU DIEN LIEN VIET")),
        VietQrBankCatalogDefinition("KLB", "KLB", "KienLongBank", "Ngân hàng TMCP Kiên Long", "970452", listOf("KIENLONGBANK", "KIEN LONG")),
        VietQrBankCatalogDefinition("KBANK", "KBank", "KBank", "Ngân hàng Đại chúng TNHH Kasikornbank", "668888", listOf("KASIKORNBANK", "KBANK HCM")),
        VietQrBankCatalogDefinition("KBHN", "KBHN", "KookminHN", "Ngân hàng Kookmin - Chi nhánh Hà Nội", "970462", listOf("KOOKMIN HN", "KOOKMIN HA NOI")),
        VietQrBankCatalogDefinition("KBHCM", "KBHCM", "KookminHCM", "Ngân hàng Kookmin - Chi nhánh Thành phố Hồ Chí Minh", "970463", listOf("KOOKMIN HCM", "KOOKMIN HO CHI MINH")),
        VietQrBankCatalogDefinition("KEBHANAHCM", "KEBHANAHCM", "KEBHanaHCM", "Ngân hàng KEB Hana – Chi nhánh Thành phố Hồ Chí Minh", "970466", listOf("KEB HANA HCM")),
        VietQrBankCatalogDefinition("KEBHANAHN", "KEBHANAHN", "KEBHanaHN", "Ngân hàng KEB Hana – Chi nhánh Hà Nội", "970467", listOf("KEBHN", "KEB HANA HN", "KEB HANA HA NOI")),
        VietQrBankCatalogDefinition("MAFC", "MAFC", "MAFC", "Công ty Tài chính TNHH MTV Mirae Asset (Việt Nam)", "977777", listOf("MIRAE ASSET")),
        VietQrBankCatalogDefinition("CITIBANK", "CITIBANK", "Citibank", "Ngân hàng Citibank, N.A. - Chi nhánh Hà Nội", "533948", listOf("CITI")),
        VietQrBankCatalogDefinition("VBSP", "VBSP", "VBSP", "Ngân hàng Chính sách Xã hội", "999888", listOf("CHINH SACH XA HOI")),
        VietQrBankCatalogDefinition("CBB", "CBB", "CBBank", "Ngân hàng Thương mại TNHH MTV Xây dựng Việt Nam", "970444", listOf("CBBANK", "XAY DUNG", "VNCB")),
        VietQrBankCatalogDefinition("CIMB", "CIMB", "CIMB", "Ngân hàng TNHH MTV CIMB Việt Nam", "422589", listOf("CIMB VIET NAM")),
        VietQrBankCatalogDefinition("DBS", "DBS", "DBSBank", "DBS Bank Ltd - Chi nhánh Thành phố Hồ Chí Minh", "796500", listOf("DBSBANK")),
        VietQrBankCatalogDefinition("GPB", "GPB", "GPBank", "Ngân hàng Thương mại TNHH MTV Dầu Khí Toàn Cầu", "970408", listOf("GPBANK", "DAU KHI TOAN CAU")),
        VietQrBankCatalogDefinition("HLBVN", "HLBVN", "HongLeong", "Ngân hàng TNHH MTV Hong Leong Việt Nam", "970442", listOf("HONGLEONG", "HONG LEONG")),
        VietQrBankCatalogDefinition("HSBC", "HSBC", "HSBC", "Ngân hàng TNHH MTV HSBC (Việt Nam)", "458761"),
        VietQrBankCatalogDefinition("IBKHN", "IBK - HN", "IBKHN", "Ngân hàng Công nghiệp Hàn Quốc - Chi nhánh Hà Nội", "970455", listOf("IBK", "IBK HN")),
        VietQrBankCatalogDefinition("IBKHCM", "IBK - HCM", "IBKHCM", "Ngân hàng Công nghiệp Hàn Quốc - Chi nhánh TP. Hồ Chí Minh", "970456", listOf("IBK HCM")),
        VietQrBankCatalogDefinition("IVB", "IVB", "IndovinaBank", "Ngân hàng TNHH Indovina", "970434", listOf("INDOVINABANK", "INDOVINA")),
        VietQrBankCatalogDefinition("NHB_HN", "NHB HN", "Nonghyup", "Ngân hàng Nonghyup - Chi nhánh Hà Nội", "801011", listOf("NONGHYUP", "NHB")),
        VietQrBankCatalogDefinition("SCVN", "SCVN", "StandardChartered", "Ngân hàng TNHH MTV Standard Chartered Bank Việt Nam", "970410", listOf("STANDARD CHARTERED", "STANDARDCHARTERED")),
        VietQrBankCatalogDefinition("UOB", "UOB", "UnitedOverseas", "Ngân hàng United Overseas - Chi nhánh TP. Hồ Chí Minh", "970458", listOf("UNITED OVERSEAS", "UNITEDOVERSEAS")),
        VietQrBankCatalogDefinition("VRB", "VRB", "VRB", "Ngân hàng Liên doanh Việt - Nga", "970421", listOf("VIET NGA")),
        VietQrBankCatalogDefinition("WVN", "WVN", "Woori", "Ngân hàng TNHH MTV Woori Việt Nam", "970457", listOf("WRB", "WOORI", "WOORIBANK")),
        VietQrBankCatalogDefinition("LIOBANK", "LioBank", "Liobank", "Ngân hàng số Liobank by OCB", "963369", listOf("LIOBANK BY OCB")),
        VietQrBankCatalogDefinition("UMEE", "UMEE", "Umee", "Ngân hàng số Umee by Kienlongbank", "963399", listOf("UMEE BY KIENLONGBANK")),
        VietQrBankCatalogDefinition("VDB", "VDB", "VDB", "Ngân hàng Phát triển Việt Nam", "970460", listOf("PHAT TRIEN VIET NAM")),
        VietQrBankCatalogDefinition("BNPHN", "BNPHN", "BNPParibasHN", "Ngân hàng BNP Paribas - Chi nhánh Hà Nội", "963668", listOf("BNP PARIBAS HN")),
        VietQrBankCatalogDefinition("BNPHCM", "BNPHCM", "BNPParibasHCM", "Ngân hàng BNP Paribas - Chi nhánh TP. Hồ Chí Minh", "963666", listOf("BNP PARIBAS HCM")),
        VietQrBankCatalogDefinition("CUB", "cathay", "CathayBank", "Ngân hàng Cathay United Bank - Chi nhánh TP. Hồ Chí Minh", "963366", listOf("CATHAY", "CATHAY UNITED BANK")),
        VietQrBankCatalogDefinition("CFC", "CFC", "VietCredit", "Công ty Tài chính Cổ phần Tín Việt", "970465", listOf("VIETCREDIT", "TIN VIET")),
        VietQrBankCatalogDefinition("ZALOPAY", "ZALOPAY", "ZaloPay", "Ví điện tử ZaloPay", "963386"),
        VietQrBankCatalogDefinition("VETC", "VETC", "VETC", "Ví điện tử VETC", "963368"),
        VietQrBankCatalogDefinition("MOMO", "MOMO", "MoMo", "Ví điện tử MoMo", "971025"),
        VietQrBankCatalogDefinition("VNPAY", "VNPAY", "VNPAY", "Ví điện tử VNPAY", "971018")
    )

    internal fun normalizeBankLookupToken(value: String): String {
        if (value.isBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return COMBINING_DIACRITICS_REGEX.replace(decomposed, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun buildBankOptionLabel(
        shortName: String,
        bankCode: String,
        officialName: String
    ): String {
        val trimmedShort = shortName.trim()
        val trimmedCode = bankCode.trim()
        val trimmedOfficial = officialName.trim()
        val prefix = if (trimmedShort.equals(trimmedCode, ignoreCase = true)) {
            trimmedShort
        } else {
            "$trimmedShort ($trimmedCode)"
        }
        return if (trimmedOfficial.isEmpty() || trimmedOfficial.equals(trimmedShort, ignoreCase = true)) {
            prefix
        } else {
            "$prefix — $trimmedOfficial"
        }
    }

    private fun matchesVietQrDefinition(
        existing: CatalogBankOption,
        def: VietQrBankCatalogDefinition
    ): Boolean {
        val idNorm = normalizeBankLookupToken(existing.bankId)
        val nameNorm = normalizeBankLookupToken(existing.displayName)
        val defTokens = buildList {
            add(def.bankId)
            add(def.bankCode)
            add(def.shortName)
            add(def.officialName)
            addAll(def.aliases)
        }.map { normalizeBankLookupToken(it) }
        return idNorm in defTokens || (nameNorm.isNotEmpty() && nameNorm in defTokens)
    }

    fun mergeVietQrBankCatalog(
        existingBanks: List<CatalogBankOption> = emptyList()
    ): List<MergedBankCatalogOption> {
        val result = mutableListOf<MergedBankCatalogOption>()
        val matchedVietQrIds = mutableSetOf<String>()
        val seenBankIds = mutableSetOf<String>()

        for (existing in existingBanks) {
            val bankId = existing.bankId.trim()
            if (bankId.isEmpty() || !seenBankIds.add(bankId)) continue

            val matchedDef = VIETQR_BANK_CATALOG.firstOrNull { def ->
                def.bankId !in matchedVietQrIds && matchesVietQrDefinition(existing, def)
            }
            if (matchedDef != null) {
                matchedVietQrIds.add(matchedDef.bankId)
                val label = buildBankOptionLabel(
                    shortName = matchedDef.shortName,
                    bankCode = matchedDef.bankCode,
                    officialName = matchedDef.officialName
                )
                val keywords = buildList {
                    add(bankId)
                    add(existing.displayName)
                    add(matchedDef.bankId)
                    add(matchedDef.bankCode)
                    add(matchedDef.shortName)
                    add(matchedDef.officialName)
                    add(matchedDef.bin)
                    addAll(matchedDef.aliases)
                }.joinToString(" ")
                result.add(
                    MergedBankCatalogOption(
                        bankId = bankId,
                        bankCode = matchedDef.bankCode,
                        shortName = matchedDef.shortName,
                        officialName = matchedDef.officialName,
                        displayName = existing.displayName.trim().ifEmpty { matchedDef.shortName },
                        label = label,
                        keywords = keywords
                    )
                )
            } else {
                val displayName = existing.displayName.trim().ifEmpty { bankId }
                result.add(
                    MergedBankCatalogOption(
                        bankId = bankId,
                        bankCode = bankId,
                        shortName = displayName,
                        officialName = displayName,
                        displayName = displayName,
                        label = if (displayName.equals(bankId, ignoreCase = true)) {
                            displayName
                        } else {
                            "$displayName ($bankId)"
                        },
                        keywords = "$bankId $displayName"
                    )
                )
            }
        }

        for (def in VIETQR_BANK_CATALOG) {
            if (def.bankId in matchedVietQrIds || !seenBankIds.add(def.bankId)) continue
            val label = buildBankOptionLabel(
                shortName = def.shortName,
                bankCode = def.bankCode,
                officialName = def.officialName
            )
            val keywords = buildList {
                add(def.bankId)
                add(def.bankCode)
                add(def.shortName)
                add(def.officialName)
                add(def.bin)
                addAll(def.aliases)
            }.joinToString(" ")
            result.add(
                MergedBankCatalogOption(
                    bankId = def.bankId,
                    bankCode = def.bankCode,
                    shortName = def.shortName,
                    officialName = def.officialName,
                    displayName = def.shortName,
                    label = label,
                    keywords = keywords
                )
            )
        }
        return result
    }

    fun resolveBankCatalogOption(
        options: List<MergedBankCatalogOption>,
        rawInput: String?
    ): MergedBankCatalogOption? {
        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        options.firstOrNull { it.bankId == trimmed }?.let { return it }

        val norm = normalizeBankLookupToken(trimmed)
        if (norm.isEmpty()) return null

        for (opt in options) {
            if (normalizeBankLookupToken(opt.bankId) == norm ||
                normalizeBankLookupToken(opt.bankCode) == norm ||
                normalizeBankLookupToken(opt.shortName) == norm ||
                normalizeBankLookupToken(opt.officialName) == norm ||
                normalizeBankLookupToken(opt.displayName) == norm ||
                normalizeBankLookupToken(opt.label) == norm
            ) {
                return opt
            }
        }

        val defMatch = VIETQR_BANK_CATALOG.firstOrNull { def ->
            def.aliases.any { alias -> normalizeBankLookupToken(alias) == norm }
        }
        if (defMatch != null) {
            return options.firstOrNull { opt ->
                opt.bankId == defMatch.bankId ||
                    normalizeBankLookupToken(opt.bankCode) == normalizeBankLookupToken(defMatch.bankCode)
            }
        }
        return null
    }

    fun formatBankReferenceDisplay(
        bankId: String?,
        bankName: String?,
        existingBanks: List<CatalogBankOption> = emptyList()
    ): String? {
        val merged = mergeVietQrBankCatalog(existingBanks)
        if (!bankId.isNullOrBlank()) {
            val matched = resolveBankCatalogOption(merged, bankId)
            if (matched != null) return matched.shortName
        }
        if (!bankName.isNullOrBlank()) return bankName.trim()
        if (!bankId.isNullOrBlank()) return bankId.trim()
        return null
    }

    fun initialDirectEditBankQueryText(
        baseline: WorkerEntryDetail,
        catalogBanks: List<CatalogBankOption> = emptyList()
    ): String {
        if (baseline.paymentState != "provided") return ""
        val merged = mergeVietQrBankCatalog(catalogBanks)
        if (!baseline.bankId.isNullOrBlank()) {
            val matched = resolveBankCatalogOption(merged, baseline.bankId)
            if (matched != null) return matched.shortName
        }
        if (!baseline.rawBankName.isNullOrBlank()) {
            return baseline.rawBankName.trim()
        }
        return baseline.bankId?.trim().orEmpty()
    }

    /**
     * Normalizes a stored raw date string (e.g. "DD/MM/YYYY", "DD-MM-YYYY", or "YYYY-MM-DD")
     * into "YYYY-MM-DD" when it is a valid calendar date, matching App BI `toDirectEditDateInputValue`.
     */
    fun toDirectEditDateInputValue(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (ISO_DATE_REGEX.matches(trimmed) && isValidUiOrIsoDate(trimmed)) {
            return trimmed
        }
        val dmyMatch = DMY_FLEX_DATE_REGEX.matchEntire(trimmed)
        if (dmyMatch != null) {
            val day = dmyMatch.groupValues[1].padStart(2, '0')
            val month = dmyMatch.groupValues[2].padStart(2, '0')
            val year = dmyMatch.groupValues[3]
            val isoCandidate = "$year-$month-$day"
            if (isValidUiOrIsoDate(isoCandidate)) {
                return isoCandidate
            }
        }
        return trimmed
    }

    fun createInitialDirectEditFormInput(
        detail: WorkerEntryDetail,
        catalogBanks: List<CatalogBankOption> = emptyList()
    ): DirectEditFormInput {
        val dobNormalized = detail.dateOfBirth?.let { isoDateToUiDate(toDirectEditDateInputValue(it)) }.orEmpty()
        val issuedAtNormalized = detail.nationalIdIssuedAt?.let { isoDateToUiDate(toDirectEditDateInputValue(it)) }.orEmpty()
        val paymentProvided = detail.paymentState == "provided"
        return DirectEditFormInput(
            displayName = detail.displayName,
            gender = detail.gender?.trim()?.uppercase(Locale.ROOT).orEmpty(),
            dateOfBirth = dobNormalized,
            nationalId = detail.nationalId?.trim().orEmpty(),
            nationalIdIssuedAt = issuedAtNormalized,
            nationalIdIssuedPlace = detail.nationalIdIssuedPlace?.trim().orEmpty(),
            address = detail.address?.trim().orEmpty(),
            phone = detail.phone?.trim().orEmpty(),
            employeeCode = detail.employeeCode,
            projectId = detail.projectId,
            firstWorkDate = isoDateToUiDate(detail.firstWorkDate),
            recruiterId = detail.recruiterId,
            laborType = detail.laborType,
            employmentStatus = detail.employmentStatus,
            employmentEffectiveDate = isoDateToUiDate(detail.employmentEffectiveDate),
            employmentLeaveReason = detail.employmentLeaveReason.orEmpty(),
            accountNumber = if (paymentProvided) detail.accountNumber?.trim().orEmpty() else "",
            bankId = if (paymentProvided) detail.bankId?.trim().orEmpty() else "",
            bankQueryText = initialDirectEditBankQueryText(detail, catalogBanks),
            accountHolderName = if (paymentProvided) detail.accountHolderName?.trim().orEmpty() else "",
            reason = ""
        )
    }

    private fun baselineOptionalFieldStateAndValue(
        baseline: WorkerEntryDetail,
        key: String
    ): Pair<String?, String?> {
        val rawValue = when (key) {
            "gender" -> baseline.gender
            "date_of_birth" -> baseline.dateOfBirth
            "national_id" -> baseline.nationalId
            "national_id_issued_at" -> baseline.nationalIdIssuedAt
            "national_id_issued_place" -> baseline.nationalIdIssuedPlace
            "address" -> baseline.address
            "phone" -> baseline.phone
            else -> null
        }?.trim()?.ifEmpty { null }

        val explicitState = baseline.workerDetailsStates[key]?.trim()?.ifEmpty { null }
        val resolvedState = when {
            explicitState != null -> explicitState
            rawValue != null -> "provided"
            key in setOf("date_of_birth", "national_id", "address", "phone") -> "omitted"
            else -> null
        }
        return resolvedState to rawValue
    }

    private fun resolveDirectEditOptionalField(
        key: String,
        baseState: String?,
        baseValue: String?,
        rawInput: String,
        isDateField: Boolean
    ): JSONObject? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            if (baseState == "provided") {
                return JSONObject().put("state", "intentionally_blank")
            }
            if (baseState == null) {
                return if (key in setOf("date_of_birth", "national_id", "address", "phone")) {
                    JSONObject().put("state", "omitted")
                } else {
                    null
                }
            }
            return JSONObject().put("state", baseState)
        }

        if (baseState == "provided" && baseValue != null) {
            if (isDateField && toDirectEditDateInputValue(baseValue) == toDirectEditDateInputValue(trimmed)) {
                return JSONObject().put("state", "provided").put("value", baseValue)
            }
            if (baseValue.trim() == trimmed) {
                return JSONObject().put("state", "provided").put("value", baseValue)
            }
        }

        val normalizedValue = if (isDateField) {
            val iso = toDirectEditDateInputValue(trimmed)
            if (!ISO_DATE_REGEX.matches(iso) || !isValidUiOrIsoDate(iso)) {
                throw invalid("WORKER_INVALID", "Ngày nhập vào không đúng định dạng hợp lệ (DD/MM/YYYY hoặc YYYY-MM-DD).")
            }
            iso
        } else {
            trimmed
        }
        return JSONObject().put("state", "provided").put("value", normalizedValue)
    }

    /**
     * Pure builder for `worker_details` in Direct Edit (`mode === "correction"`),
     * matching App BI `buildDirectEditWorkerDetailsProposal`:
     * - Untouched empty fields (`omitted`, `unknown`, `intentionally_blank`, or absent)
     *   keep their exact baseline state.
     * - Existing `provided` fields cleared to empty transition to `intentionally_blank`.
     * - Returns `null` when no field in `worker_details` changed compared to `baseline`.
     */
    fun buildDirectEditWorkerDetailsProposal(
        baseline: WorkerEntryDetail,
        form: DirectEditFormInput
    ): JSONObject? {
        val (genderBaseState, genderBaseVal) = baselineOptionalFieldStateAndValue(baseline, "gender")
        val (dobBaseState, dobBaseVal) = baselineOptionalFieldStateAndValue(baseline, "date_of_birth")
        val (nidBaseState, nidBaseVal) = baselineOptionalFieldStateAndValue(baseline, "national_id")
        val (issuedAtBaseState, issuedAtBaseVal) = baselineOptionalFieldStateAndValue(baseline, "national_id_issued_at")
        val (issuedPlaceBaseState, issuedPlaceBaseVal) = baselineOptionalFieldStateAndValue(baseline, "national_id_issued_place")
        val (addressBaseState, addressBaseVal) = baselineOptionalFieldStateAndValue(baseline, "address")
        val (phoneBaseState, phoneBaseVal) = baselineOptionalFieldStateAndValue(baseline, "phone")

        val normalizedGenderInput = when (val g = form.gender.trim()) {
            "Nam", "NAM", "MALE" -> "MALE"
            "Nữ", "NỮ", "FEMALE" -> "FEMALE"
            "Khác", "KHÁC", "OTHER" -> "OTHER"
            "" -> ""
            else -> g
        }

        val genderResolved = resolveDirectEditOptionalField("gender", genderBaseState, genderBaseVal, normalizedGenderInput, false)
        val dobResolved = resolveDirectEditOptionalField("date_of_birth", dobBaseState, dobBaseVal, form.dateOfBirth, true)
            ?: JSONObject().put("state", "omitted")
        val nationalIdResolved = resolveDirectEditOptionalField("national_id", nidBaseState, nidBaseVal, form.nationalId, false)
            ?: JSONObject().put("state", "omitted")
        val issuedAtResolved = resolveDirectEditOptionalField("national_id_issued_at", issuedAtBaseState, issuedAtBaseVal, form.nationalIdIssuedAt, true)
        val issuedPlaceResolved = resolveDirectEditOptionalField("national_id_issued_place", issuedPlaceBaseState, issuedPlaceBaseVal, form.nationalIdIssuedPlace, false)
        val addressResolved = resolveDirectEditOptionalField("address", addressBaseState, addressBaseVal, form.address, false)
            ?: JSONObject().put("state", "omitted")
        val phoneResolved = resolveDirectEditOptionalField("phone", phoneBaseState, phoneBaseVal, form.phone, false)
            ?: JSONObject().put("state", "omitted")

        fun fieldChanged(resolved: JSONObject?, baseState: String?, baseVal: String?, isRequiredEnvelopeKey: Boolean): Boolean {
            val effectiveBaseState = baseState ?: if (isRequiredEnvelopeKey) "omitted" else null
            if (resolved == null && effectiveBaseState == null) return false
            if (resolved == null || effectiveBaseState == null) return true
            val resState = resolved.optString("state", "")
            if (resState != effectiveBaseState) return true
            if (resState == "provided") {
                return resolved.optString("value", "") != baseVal.orEmpty()
            }
            return false
        }

        val cleanDisplayName = form.displayName.trim()
        val anyChanged = cleanDisplayName != baseline.displayName.trim() ||
            fieldChanged(genderResolved, genderBaseState, genderBaseVal, false) ||
            fieldChanged(dobResolved, dobBaseState, dobBaseVal, true) ||
            fieldChanged(nationalIdResolved, nidBaseState, nidBaseVal, true) ||
            fieldChanged(issuedAtResolved, issuedAtBaseState, issuedAtBaseVal, false) ||
            fieldChanged(issuedPlaceResolved, issuedPlaceBaseState, issuedPlaceBaseVal, false) ||
            fieldChanged(addressResolved, addressBaseState, addressBaseVal, true) ||
            fieldChanged(phoneResolved, phoneBaseState, phoneBaseVal, true)

        if (!anyChanged) return null

        if (cleanDisplayName.isEmpty() || cleanDisplayName.length > 256 || isFormulaLikeCell(cleanDisplayName) || hasControlCharacter(cleanDisplayName)) {
            throw invalid("WORKER_INVALID", "Họ và tên người lao động không được để trống (tối đa 256 ký tự).")
        }
        if (genderResolved != null && genderResolved.optString("state") == "provided") {
            val gVal = genderResolved.optString("value", "")
            if (gVal !in setOf("MALE", "FEMALE", "OTHER")) {
                throw invalid("WORKER_INVALID", "Giới tính không thuộc danh mục cho phép (Nam, Nữ, Khác).")
            }
        }
        if (dobResolved.optString("state") == "provided") {
            val v = dobResolved.optString("value", "")
            if (v.isEmpty() || v.length > 10 || isFormulaLikeCell(v) || hasControlCharacter(v)) {
                throw invalid("WORKER_INVALID", "Ngày sinh không hợp lệ.")
            }
        }
        if (nationalIdResolved.optString("state") == "provided") {
            val v = nationalIdResolved.optString("value", "")
            if (!isCanonicalNationalId(v) || v == "000000000000" || v == "000000000") {
                throw invalid("NATIONAL_ID_INVALID", "Số CCCD/CMND phải gồm đúng 9 hoặc 12 chữ số hợp lệ.")
            }
        }
        if (issuedAtResolved != null && issuedAtResolved.optString("state") == "provided") {
            val v = issuedAtResolved.optString("value", "")
            if (v.isEmpty() || v.length > 10 || isFormulaLikeCell(v) || hasControlCharacter(v)) {
                throw invalid("WORKER_INVALID", "Ngày cấp CCCD không hợp lệ.")
            }
        }
        if (issuedPlaceResolved != null && issuedPlaceResolved.optString("state") == "provided") {
            val v = issuedPlaceResolved.optString("value", "")
            if (v.isEmpty() || v.length > 256 || isFormulaLikeCell(v) || hasControlCharacter(v)) {
                throw invalid("WORKER_INVALID", "Nơi cấp CCCD không hợp lệ (tối đa 256 ký tự).")
            }
        }
        if (addressResolved.optString("state") == "provided") {
            val v = addressResolved.optString("value", "")
            if (v.length > 1024 || isFormulaLikeCell(v) || hasControlCharacter(v)) {
                throw invalid("WORKER_INVALID", "Địa chỉ không hợp lệ (tối đa 1024 ký tự).")
            }
        }
        if (phoneResolved.optString("state") == "provided") {
            val v = phoneResolved.optString("value", "")
            if (v.length > 64 || isFormulaLikeCell(v) || hasControlCharacter(v)) {
                throw invalid("WORKER_INVALID", "Số điện thoại không hợp lệ (tối đa 64 ký tự).")
            }
        }

        return JSONObject().apply {
            put("display_name", cleanDisplayName)
            if (genderResolved != null) put("gender", genderResolved)
            put("date_of_birth", dobResolved)
            put("national_id", nationalIdResolved)
            if (issuedAtResolved != null) put("national_id_issued_at", issuedAtResolved)
            if (issuedPlaceResolved != null) put("national_id_issued_place", issuedPlaceResolved)
            put("address", addressResolved)
            put("phone", phoneResolved)
        }
    }

    /**
     * Pure builder for `payment` patch in Direct Edit (`mode === "correction"`),
     * matching App BI `buildDirectEditPaymentPatch`.
     * Returns `null` when unchanged.
     */
    fun buildDirectEditPaymentPatch(
        baseline: WorkerEntryDetail,
        accountNumber: String,
        bankId: String,
        bankQueryText: String,
        accountHolderName: String,
        catalogBanks: List<CatalogBankOption> = emptyList()
    ): JSONObject? {
        val trimmedAccount = accountNumber.trim()
        val trimmedHolder = accountHolderName.trim()
        val trimmedBankId = bankId.trim()
        val trimmedBankText = bankQueryText.trim()

        val allEmpty = trimmedAccount.isEmpty() &&
            trimmedHolder.isEmpty() &&
            trimmedBankId.isEmpty() &&
            trimmedBankText.isEmpty()

        if (allEmpty) {
            if (baseline.paymentState != "provided") {
                return null
            }
            return JSONObject()
                .put("state", "intentionally_blank")
                .put("account_number", JSONObject.NULL)
                .put("bank_id", JSONObject.NULL)
                .put("bank_name", JSONObject.NULL)
                .put("account_holder_name", JSONObject.NULL)
        }

        if (trimmedAccount.isNotEmpty() && !PAYMENT_ACCOUNT_DIGITS_REGEX.matches(trimmedAccount)) {
            throw invalid("PAYMENT_INVALID", "Số tài khoản ngân hàng chỉ gồm chữ số (tối đa 64 ký tự).")
        }
        if (trimmedHolder.isNotEmpty() && (trimmedHolder.length > 256 || hasControlCharacter(trimmedHolder) || isFormulaLikeCell(trimmedHolder))) {
            throw invalid("PAYMENT_INVALID", "Tên chủ tài khoản không hợp lệ (tối đa 256 ký tự).")
        }

        val mergedBanks = mergeVietQrBankCatalog(catalogBanks)
        val initialBankText = initialDirectEditBankQueryText(baseline, catalogBanks)

        var resolvedBankId: String? = null
        var resolvedBankName: String? = null

        if (trimmedBankText.isNotEmpty() || trimmedBankId.isNotEmpty()) {
            if (baseline.paymentState == "provided" &&
                trimmedBankId == baseline.bankId.orEmpty() &&
                trimmedBankText == initialBankText
            ) {
                resolvedBankId = baseline.bankId?.trim()?.ifEmpty { null }
                resolvedBankName = baseline.rawBankName?.trim()?.ifEmpty { null }
                    ?: resolvedBankId?.let { resolveBankCatalogOption(mergedBanks, it)?.shortName }
            } else {
                val selectedById = if (trimmedBankId.isNotEmpty()) {
                    resolveBankCatalogOption(mergedBanks, trimmedBankId)
                } else null
                val matchedByText = if (trimmedBankText.isNotEmpty()) {
                    resolveBankCatalogOption(mergedBanks, trimmedBankText)
                } else null

                if (selectedById != null && (trimmedBankText.isEmpty() || matchedByText?.bankId == selectedById.bankId)) {
                    resolvedBankId = selectedById.bankId
                    resolvedBankName = selectedById.shortName
                } else if (matchedByText != null) {
                    resolvedBankId = matchedByText.bankId
                    resolvedBankName = matchedByText.shortName
                } else if (trimmedBankText.isNotEmpty()) {
                    if (trimmedBankText.length > 256 || hasControlCharacter(trimmedBankText) || isFormulaLikeCell(trimmedBankText)) {
                        throw invalid("PAYMENT_INVALID", "Tên ngân hàng tham chiếu không hợp lệ (tối đa 256 ký tự).")
                    }
                    resolvedBankId = null
                    resolvedBankName = trimmedBankText
                } else if (trimmedBankId.isNotEmpty()) {
                    if (!SAFE_CATALOG_ID_REGEX.matches(trimmedBankId)) {
                        throw invalid("PAYMENT_INVALID", "Mã ngân hàng không hợp lệ.")
                    }
                    resolvedBankId = trimmedBankId
                    resolvedBankName = null
                }
            }
        }

        val nextAccountNumber = trimmedAccount.ifEmpty { null }
        val nextAccountHolder = trimmedHolder.ifEmpty { null }

        if (baseline.paymentState == "provided") {
            val baseAccount = baseline.accountNumber?.trim()?.ifEmpty { null }
            val baseBankId = baseline.bankId?.trim()?.ifEmpty { null }
            val baseBankName = baseline.rawBankName?.trim()?.ifEmpty { null }
            val baseHolder = baseline.accountHolderName?.trim()?.ifEmpty { null }
            val bankUnchanged = baseBankId == resolvedBankId &&
                (resolvedBankId != null || baseBankName == resolvedBankName)
            if (baseAccount == nextAccountNumber && bankUnchanged && baseHolder == nextAccountHolder) {
                return null
            }
        }

        return JSONObject().apply {
            put("state", "provided")
            put("account_number", nextAccountNumber ?: JSONObject.NULL)
            put("bank_id", resolvedBankId ?: JSONObject.NULL)
            if (resolvedBankName != null || resolvedBankId == null) {
                put("bank_name", resolvedBankName ?: JSONObject.NULL)
            }
            put("account_holder_name", nextAccountHolder ?: JSONObject.NULL)
        }
    }

    /**
     * Builds the complete `patch` object for `POST /api/direct-entry/entries/{entryId}/privileged-edit`
     * across all 3 data groups (Cá nhân `worker_details`, Công việc `project_id`/`first_work_date`/`employee_code`/`recruiter_id`/`labor_type`/`employment_status`, Ngân hàng `payment`).
     */
    fun buildDirectEditPatch(
        baseline: WorkerEntryDetail,
        form: DirectEditFormInput,
        catalogBanks: List<CatalogBankOption> = emptyList()
    ): JSONObject {
        val patch = JSONObject()

        val cleanProjectId = form.projectId.trim()
        if (cleanProjectId != baseline.projectId.trim()) {
            if (!SAFE_CATALOG_ID_REGEX.matches(cleanProjectId)) {
                throw invalid("WORKER_INVALID", "Dự án đã chọn không hợp lệ.")
            }
            patch.put("project_id", cleanProjectId)
        }

        val isoFirstWorkDate = uiDateToIsoDate(form.firstWorkDate)
        if (isoFirstWorkDate != baseline.firstWorkDate.trim()) {
            patch.put("first_work_date", isoFirstWorkDate)
        }

        val cleanEmployeeCode = form.employeeCode.trim()
        if (cleanEmployeeCode != baseline.employeeCode.trim()) {
            if (!EMPLOYEE_CODE_REGEX.matches(cleanEmployeeCode)) {
                throw invalid("WORKER_INVALID", "Mã người lao động phải đúng định dạng chuẩn hrp-YYYY-XXXXXX (ví dụ hrp-2026-000123).")
            }
            patch.put("employee_code", cleanEmployeeCode)
        }

        val cleanRecruiterId = form.recruiterId.trim()
        if (cleanRecruiterId != baseline.recruiterId.trim()) {
            if (!UUID_REGEX.matches(cleanRecruiterId)) {
                throw invalid("WORKER_INVALID", "Người tuyển đã chọn không hợp lệ.")
            }
            patch.put("recruiter_id", cleanRecruiterId)
        }

        val cleanLaborType = form.laborType.trim()
        if (cleanLaborType != baseline.laborType.trim()) {
            if (!LABOR_TYPE_KEY_REGEX.matches(cleanLaborType)) {
                throw invalid("WORKER_INVALID", "Loại hình lao động đã chọn không hợp lệ.")
            }
            patch.put("labor_type", cleanLaborType)
        }

        val workerDetailsPatch = buildDirectEditWorkerDetailsProposal(baseline, form)
        if (workerDetailsPatch != null) {
            patch.put("worker_details", workerDetailsPatch)
        }

        val paymentPatch = buildDirectEditPaymentPatch(
            baseline = baseline,
            accountNumber = form.accountNumber,
            bankId = form.bankId,
            bankQueryText = form.bankQueryText,
            accountHolderName = form.accountHolderName,
            catalogBanks = catalogBanks
        )
        if (paymentPatch != null) {
            patch.put("payment", paymentPatch)
        }

        val cleanStatus = form.employmentStatus.trim()
        if (cleanStatus.isNotEmpty()) {
            val isoEff = uiDateToIsoDate(form.employmentEffectiveDate)
            val statusChanged = cleanStatus != baseline.employmentStatus.trim() ||
                isoEff != baseline.employmentEffectiveDate.trim() ||
                (cleanStatus == "OFF" && form.employmentLeaveReason.trim() != baseline.employmentLeaveReason.orEmpty().trim())
            if (statusChanged) {
                patch.put(
                    "employment_status",
                    buildPrivilegedEditEmploymentStatus(
                        status = cleanStatus,
                        effectiveDateUiOrIso = isoEff,
                        leaveReason = form.employmentLeaveReason,
                        baselineStatus = baseline.employmentStatus
                    )
                )
            }
        }

        return patch
    }

    /**
     * Applies a saved `privileged-edit` `patch` and `newVersion` onto a local `WorkerEntryDetail` baseline
     * so that subsequent document uploads or retries within the same open dialog see the updated baseline
     * without re-submitting already-saved profile fields.
     */
    fun applyDirectEditPatchToDetail(
        baseline: WorkerEntryDetail,
        patch: JSONObject,
        newVersion: Int
    ): WorkerEntryDetail {
        var next = baseline.copy(version = newVersion)
        if (patch.has("project_id") && !patch.isNull("project_id")) {
            next = next.copy(projectId = patch.optString("project_id", next.projectId))
        }
        if (patch.has("first_work_date") && !patch.isNull("first_work_date")) {
            next = next.copy(firstWorkDate = patch.optString("first_work_date", next.firstWorkDate))
        }
        if (patch.has("employee_code") && !patch.isNull("employee_code")) {
            next = next.copy(employeeCode = patch.optString("employee_code", next.employeeCode))
        }
        if (patch.has("recruiter_id") && !patch.isNull("recruiter_id")) {
            next = next.copy(recruiterId = patch.optString("recruiter_id", next.recruiterId))
        }
        if (patch.has("labor_type") && !patch.isNull("labor_type")) {
            next = next.copy(laborType = patch.optString("labor_type", next.laborType))
        }
        val wd = patch.optJSONObject("worker_details")
        if (wd != null) {
            val nextStates = LinkedHashMap(next.workerDetailsStates)
            for (k in listOf("gender", "date_of_birth", "national_id", "national_id_issued_at", "national_id_issued_place", "address", "phone")) {
                val fObj = wd.optJSONObject(k)
                if (fObj != null) {
                    val st = fObj.optString("state", "").trim()
                    if (st.isNotEmpty()) nextStates[k] = st
                }
            }
            next = next.copy(
                displayName = wd.optString("display_name", next.displayName),
                gender = wd.extractOptionalFieldValue("gender"),
                dateOfBirth = wd.extractOptionalFieldValue("date_of_birth"),
                nationalId = wd.extractOptionalFieldValue("national_id"),
                nationalIdIssuedAt = wd.extractOptionalFieldValue("national_id_issued_at"),
                nationalIdIssuedPlace = wd.extractOptionalFieldValue("national_id_issued_place"),
                address = wd.extractOptionalFieldValue("address"),
                phone = wd.extractOptionalFieldValue("phone"),
                workerDetailsStates = nextStates
            )
        }
        val pay = patch.optJSONObject("payment")
        if (pay != null) {
            val st = pay.optNullableString("state")
            val bId = pay.optNullableString("bank_id")
            val rawBName = pay.optNullableString("bank_name")
            next = next.copy(
                paymentState = st,
                bankId = bId,
                rawBankName = rawBName,
                bankName = rawBName ?: bId,
                accountNumber = pay.optNullableString("account_number"),
                accountHolderName = pay.optNullableString("account_holder_name")
            )
        }
        val emp = patch.optJSONObject("employment_status")
        if (emp != null) {
            next = next.copy(
                employmentStatus = emp.optString("status", next.employmentStatus),
                employmentEffectiveDate = emp.optString("effective_date", next.employmentEffectiveDate),
                employmentLeaveReason = emp.optNullableString("leave_reason")
            )
        }
        return next
    }

    fun directEditDocumentLabel(docType: String): String {
        return when (docType.trim().uppercase(Locale.ROOT)) {
            "WORKER_PORTRAIT" -> "Ảnh NLĐ"
            "CCCD_FRONT" -> "CCCD mặt trước"
            "CCCD_BACK" -> "CCCD mặt sau"
            "EMPLOYMENT_CONTRACT" -> "Hợp đồng lao động"
            else -> "Tài liệu đính kèm"
        }
    }

    fun directEditDocumentErrorMessage(docType: String, code: String?): String {
        val label = directEditDocumentLabel(docType)
        return when (code) {
            "DOCUMENT_FILE_REQUIRED" -> "$label: Chưa chọn tệp."
            "DOCUMENT_SIZE_INVALID", "DOCUMENT_TOO_LARGE" -> "$label: Dung lượng tệp phải lớn hơn 0 và tối đa 10 MB."
            "DOCUMENT_MIME_INVALID" -> if (docType == "WORKER_PORTRAIT") {
                "$label: Chỉ chấp nhận ảnh JPEG hoặc PNG."
            } else {
                "$label: Chỉ chấp nhận JPEG, PNG hoặc PDF."
            }
            "DOCUMENT_DENIED", "ACTOR_NOT_AVAILABLE" -> "$label: Tài khoản không còn quyền tải tài liệu này."
            "DOCUMENT_VERSION_CONFLICT", "VERSION_CONFLICT" -> "$label: Hồ sơ vừa được cập nhật ở nơi khác (lệch phiên bản). Vui lòng bấm Thử lại tệp này."
            "DOCUMENT_CHECKSUM_MISMATCH", "DOCUMENT_OBJECT_MISSING", "DOCUMENT_SCAN_REJECTED" -> "$label: Tệp tải lên chưa hợp lệ trên kho lưu trữ. Vui lòng chọn lại hoặc bấm Thử lại."
            "DOCUMENT_STORAGE_UNAVAILABLE", "DOCUMENT_RESERVE_UNAVAILABLE", "DOCUMENT_FINALIZE_UNAVAILABLE" -> "$label: Kho lưu trữ tạm thời gián đoạn. Vui lòng bấm Thử lại tệp này."
            else -> "$label: Không tải được tệp lên máy chủ. Vui lòng bấm Thử lại tệp này."
        }
    }

    fun parseCatalogResponse(json: JSONObject): DirectEntryCatalog {
        val catObj = json.optJSONObject("catalog") ?: throw contractInvalid("CATALOG_MISSING")
        val effectiveDate = catObj.optString("effective_date", "").trim()
        if (effectiveDate.isEmpty()) throw contractInvalid("CATALOG_DATE_MISSING")

        val projectsArr = catObj.optJSONArray("projects") ?: throw contractInvalid("CATALOG_PROJECTS_MISSING")
        val projects = buildList {
            for (i in 0 until projectsArr.length()) {
                val p = projectsArr.optJSONObject(i) ?: throw contractInvalid("CATALOG_PROJECT_INVALID")
                val id = p.optString("project_id", "").trim()
                val name = p.optString("display_name", "").trim()
                if (id.isEmpty() || name.isEmpty()) throw contractInvalid("CATALOG_PROJECT_INVALID")
                add(CatalogProjectOption(projectId = id, displayName = name))
            }
        }

        val recruitersArr = catObj.optJSONArray("recruiters") ?: throw contractInvalid("CATALOG_RECRUITERS_MISSING")
        val recruiters = buildList {
            for (i in 0 until recruitersArr.length()) {
                val r = recruitersArr.optJSONObject(i) ?: throw contractInvalid("CATALOG_RECRUITER_INVALID")
                val id = r.optString("recruiter_id", "").trim()
                val name = r.optString("display_name", "").trim()
                val code = r.optString("personnel_code", "").trim()
                val providerType = r.optString("provider_type", "").trim()
                val label = r.optString("label", "").trim().ifEmpty { name }
                if (!UUID_REGEX.matches(id) || name.isEmpty() || (providerType != "hrp" && providerType != "vendor")) {
                    throw contractInvalid("CATALOG_RECRUITER_INVALID")
                }
                add(
                    CatalogRecruiterOption(
                        recruiterId = id,
                        displayName = name,
                        personnelCode = code,
                        providerType = providerType,
                        vendorId = r.optNullableString("vendor_id"),
                        teamId = r.optNullableString("team_id"),
                        teamDisplayName = r.optNullableString("team_display_name"),
                        label = label
                    )
                )
            }
        }

        val banksArr = catObj.optJSONArray("banks") ?: throw contractInvalid("CATALOG_BANKS_MISSING")
        val banks = buildList {
            for (i in 0 until banksArr.length()) {
                val b = banksArr.optJSONObject(i) ?: throw contractInvalid("CATALOG_BANK_INVALID")
                val id = b.optString("bank_id", "").trim()
                val name = b.optString("display_name", "").trim()
                if (id.isEmpty() || name.isEmpty()) throw contractInvalid("CATALOG_BANK_INVALID")
                add(CatalogBankOption(bankId = id, displayName = name))
            }
        }

        val laborArr = catObj.optJSONArray("labor_types") ?: throw contractInvalid("CATALOG_LABOR_TYPES_MISSING")
        val laborTypes = buildList {
            for (i in 0 until laborArr.length()) {
                val l = laborArr.optJSONObject(i) ?: throw contractInvalid("CATALOG_LABOR_TYPE_INVALID")
                val key = l.optString("labor_type_key", "").trim()
                val name = l.optString("display_name", "").trim()
                val sortOrder = l.optInt("sort_order", i)
                if (key.isEmpty() || name.isEmpty()) throw contractInvalid("CATALOG_LABOR_TYPE_INVALID")
                add(CatalogLaborTypeOption(laborTypeKey = key, displayName = name, sortOrder = sortOrder))
            }
        }.sortedBy { it.sortOrder }

        return DirectEntryCatalog(
            effectiveDate = effectiveDate,
            projects = projects,
            recruiters = recruiters,
            banks = banks,
            laborTypes = laborTypes
        )
    }

    fun parseStageUploadResponse(json: JSONObject, fallbackFileName: String): StagedDocumentRef {
        val stageId = json.optString("stage_id", "").trim()
        val docType = json.optString("document_type", "").trim()
        val mimeType = json.optString("mime_type", "").trim()
        val sizeBytes = json.optLong("size_bytes", -1L)
        val status = json.optString("status", "").trim()
        if (!UUID_REGEX.matches(stageId) || docType !in ALLOWED_STAGE_DOCUMENT_TYPES || sizeBytes <= 0L || status != "STAGED") {
            throw contractInvalid("STAGE_RESPONSE_INVALID")
        }
        return StagedDocumentRef(
            stageId = stageId,
            documentType = docType,
            fileName = fallbackFileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
    }

    fun parseFullProfileBatchResponse(json: JSONObject): FullProfileBatchResult {
        val submissionId = json.optString("submission_id", "").trim()
        val state = json.optString("state", "").trim()
        val version = json.optInt("version", 0)
        val entryIdsArr = json.optJSONArray("entry_ids") ?: throw contractInvalid("BATCH_ENTRY_IDS_MISSING")
        if (!UUID_REGEX.matches(submissionId) || state.isEmpty() || version < 1 || entryIdsArr.length() < 1) {
            throw contractInvalid("BATCH_RESPONSE_INVALID")
        }
        val entryIds = buildList {
            for (i in 0 until entryIdsArr.length()) {
                val eid = entryIdsArr.optString(i, "").trim()
                if (!UUID_REGEX.matches(eid)) throw contractInvalid("BATCH_ENTRY_ID_INVALID")
                add(eid)
            }
        }
        val employeeCodes = buildList {
            val codesArr = json.optJSONArray("employee_codes")
            if (codesArr != null) {
                for (i in 0 until codesArr.length()) {
                    val code = codesArr.optString(i, "").trim()
                    if (code.isNotEmpty()) add(code)
                }
            }
        }
        return FullProfileBatchResult(
            submissionId = submissionId,
            state = state,
            version = version,
            entryIds = entryIds,
            employeeCodes = employeeCodes
        )
    }

    fun parseWorkerDirectoryPage(json: JSONObject, requestedScope: String): WorkerDirectoryPageResult {
        val scope = json.optString("scope", "").trim()
        if (scope != requestedScope || scope !in ALLOWED_WORKER_SCOPES) {
            throw contractInvalid("WORKER_SCOPE_MISMATCH")
        }
        val itemsArr = json.optJSONArray("items") ?: throw contractInvalid("WORKER_ITEMS_MISSING")
        val rows = buildList {
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: throw contractInvalid("WORKER_ITEM_INVALID")
                val entryId = item.optString("entry_id", "").trim()
                val entryVersion = item.optInt("entry_version", 0)
                if (!UUID_REGEX.matches(entryId) || entryVersion < 1) {
                    throw contractInvalid("WORKER_ENTRY_INVALID")
                }
                val actionsObj = item.optJSONObject("allowed_actions")
                    ?: throw contractInvalid("WORKER_ALLOWED_ACTIONS_MISSING")

                val paymentObj = item.optJSONObject("payment")
                val paymentSummary = if (paymentObj != null && paymentObj.optString("state") == "provided") {
                    listOfNotNull(
                        paymentObj.optNullableString("bank_name") ?: paymentObj.optNullableString("bank_id"),
                        paymentObj.optNullableString("account_number")
                    ).joinToString(" · ")
                } else {
                    ""
                }

                val pendingObj = item.optJSONObject("pending_request")
                val pendingSummary = if (pendingObj != null) {
                    "Đang chờ duyệt"
                } else {
                    ""
                }

                val decisionObj = item.optJSONObject("last_decision")
                val decisionSummary = if (decisionObj != null) {
                    val rawState = decisionObj.optString("state", "").trim()
                    val vnDecision = when (rawState.uppercase(Locale.ROOT)) {
                        "APPROVED" -> "Đã duyệt"
                        "REJECTED" -> "Từ chối"
                        "CANCELLED" -> "Đã hủy"
                        "PENDING" -> "Đang chờ duyệt"
                        else -> rawState
                    }
                    if (vnDecision.isNotEmpty()) "Quyết định gần nhất: $vnDecision" else ""
                } else {
                    ""
                }

                add(
                    WorkerProfileEntity(
                        cacheKey = "$scope:$entryId",
                        scopeKey = scope,
                        entryId = entryId,
                        entryVersion = entryVersion,
                        submissionState = item.optString("submission_state", ""),
                        employeeCode = item.optString("employee_code", ""),
                        displayName = item.optString("display_name", ""),
                        projectId = item.optString("project_id", ""),
                        projectDisplay = item.optString("project_display", ""),
                        firstWorkDate = item.optString("first_work_date", ""),
                        laborType = item.optString("labor_type", ""),
                        laborTypeDisplay = item.optString("labor_type_display", ""),
                        employmentStatus = item.optString("employment_status", ""),
                        recruiterId = item.optString("recruiter_id", ""),
                        recruiterDisplay = item.optString("recruiter_display", ""),
                        paymentSummary = paymentSummary,
                        pendingRequestSummary = pendingSummary,
                        lastDecisionSummary = decisionSummary,
                        isProjectManager = item.optBoolean("is_project_manager", false),
                        canView = actionsObj.optBoolean("view", false),
                        canViewPii = actionsObj.optBoolean("view_pii", false),
                        canViewPayment = actionsObj.optBoolean("view_payment", false),
                        canProposeChange = actionsObj.optBoolean("propose_change", false),
                        proposeChangeCode = actionsObj.optNullableString("propose_change_code"),
                        canDirectEdit = actionsObj.optBoolean("direct_edit", false)
                    )
                )
            }
        }

        val summaryObj = json.optJSONObject("summary")
        val summary = WorkerDirectorySummary(
            totalWorkers = summaryObj?.optInt("total_workers", rows.size) ?: rows.size,
            projectCount = summaryObj?.optInt("project_count", 0) ?: 0,
            recruiterCount = summaryObj?.optInt("recruiter_count", 0) ?: 0
        )

        return WorkerDirectoryPageResult(
            scope = scope,
            items = rows,
            pageSize = json.optInt("page_size", 25),
            hasMore = json.optBoolean("has_more", false),
            nextCursor = json.optNullableString("next_cursor"),
            authorizationDate = json.optString("authorization_date", ""),
            summary = summary
        )
    }

    fun parseOwnSubmissionsPage(json: JSONObject): OwnSubmissionsPageResult {
        val itemsArr = json.optJSONArray("items") ?: throw contractInvalid("SUBMISSIONS_ITEMS_MISSING")
        val items = buildList {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.optJSONObject(i) ?: throw contractInvalid("SUBMISSION_ITEM_INVALID")
                val submissionId = obj.optString("submission_id", "").trim()
                val version = obj.optInt("version", 0)
                if (!UUID_REGEX.matches(submissionId) || version < 1) {
                    throw contractInvalid("SUBMISSION_ITEM_INVALID")
                }
                val transitionsArr = obj.optJSONArray("allowed_transitions")
                val transitions = buildList {
                    if (transitionsArr != null) {
                        for (j in 0 until transitionsArr.length()) {
                            val t = transitionsArr.optString(j, "").trim()
                            if (t.isNotEmpty()) add(t)
                        }
                    }
                }
                add(
                    OwnSubmissionItem(
                        submissionId = submissionId,
                        state = obj.optString("state", ""),
                        version = version,
                        entryCount = obj.optInt("entry_count", 0),
                        createdAt = obj.optString("created_at", ""),
                        updatedAt = obj.optString("updated_at", ""),
                        submittedAt = obj.optNullableString("submitted_at"),
                        allowedTransitions = transitions,
                        projectScoped = obj.optBoolean("project_scoped", false)
                    )
                )
            }
        }
        return OwnSubmissionsPageResult(
            items = items,
            pageSize = json.optInt("page_size", 20),
            hasMore = json.optBoolean("has_more", false),
            nextCursor = json.optNullableString("next_cursor")
        )
    }

    fun parseEntryDetailResponse(json: JSONObject): WorkerEntryDetail {
        val entry = json.optJSONObject("entry") ?: throw contractInvalid("ENTRY_DETAIL_MISSING")
        val entryId = entry.optString("entry_id", "").trim()
        val submissionId = entry.optString("submission_id", "").trim()
        val version = entry.optInt("version", 0)
        if (!UUID_REGEX.matches(entryId) || !UUID_REGEX.matches(submissionId) || version < 1) {
            throw contractInvalid("ENTRY_DETAIL_INVALID")
        }

        val workerObj = entry.optJSONObject("worker_details") ?: throw contractInvalid("WORKER_DETAILS_MISSING")
        if (!workerObj.has("display_name") || workerObj.isNull("display_name")) {
            throw contractInvalid("WORKER_DISPLAY_NAME_MISSING")
        }
        val displayName = workerObj.optString("display_name", "").trim()
        if (displayName.length > 256) throw contractInvalid("WORKER_DISPLAY_NAME_MISSING")

        val generalNote = when (val noteRaw = entry.opt("general_note")) {
            is String -> noteRaw.ifBlank { null }
            is JSONObject -> if (noteRaw.optString("state") == "provided") {
                noteRaw.optNullableString("value")
            } else if (noteRaw.optBoolean("present", false)) {
                "(Đã ẩn theo phân quyền)"
            } else {
                null
            }
            else -> null
        }

        val paymentObj = entry.optJSONObject("payment")
        val paymentState = paymentObj?.optNullableString("state")
        val paymentVersion = paymentObj?.optInt("version", 0)?.takeIf { it > 0 }
        val bankId = paymentObj?.optNullableString("bank_id")
        val rawBankName = paymentObj?.optNullableString("bank_name")
        val bankName = rawBankName ?: bankId
        val accountNumber = paymentObj?.optNullableString("account_number")
        val accountHolderName = paymentObj?.optNullableString("account_holder_name")

        val workerDetailsStates = buildMap {
            for (field in listOf(
                "gender",
                "date_of_birth",
                "national_id",
                "national_id_issued_at",
                "national_id_issued_place",
                "address",
                "phone"
            )) {
                workerObj.extractOptionalFieldState(field)?.let { put(field, it) }
            }
        }

        val statusObj = entry.optJSONObject("employment_status") ?: throw contractInvalid("EMPLOYMENT_STATUS_MISSING")
        val statusCode = statusObj.optString("status", "").trim()
        val effectiveDate = statusObj.optString("effective_date", "").trim()
        val statusVersion = statusObj.optInt("version", 1)
        val leaveReason = statusObj.optNullableString("leave_reason")

        val portraitObj = entry.optJSONObject("worker_portrait")
        val portraitCurrent = portraitObj?.optJSONObject("current")
        val portraitDocId = portraitCurrent?.optNullableString("document_id")?.takeIf { UUID_REGEX.matches(it) }
        val portraitPreviewPath = portraitDocId?.let { "/api/direct-entry/entries/$entryId/documents/$it/preview" }
        val portraitDownloadPath = portraitDocId?.let { "/api/direct-entry/entries/$entryId/documents/$it/download" }

        val docsArr = entry.optJSONArray("documents") ?: JSONArray()
        val documents = buildList {
            for (i in 0 until docsArr.length()) {
                val doc = docsArr.optJSONObject(i) ?: continue
                val docId = doc.optString("document_id", "").trim()
                if (!UUID_REGEX.matches(docId)) continue
                val docType = doc.optString("document_type", "").trim()
                val uploadStatus = doc.optString("upload_status", "").trim()
                val isReady = uploadStatus == "READY"
                val previewPath = when {
                    !isReady -> null
                    docType == "WORKER_PORTRAIT" -> "/api/direct-entry/entries/$entryId/documents/$docId/preview"
                    (docType == "CCCD_FRONT" || docType == "CCCD_BACK") &&
                        doc.optString("mime_type", "").startsWith("image/") ->
                        "/api/direct-entry/entries/$entryId/documents/$docId/cccd-preview"
                    else -> null
                }
                val downloadPath = if (isReady) {
                    "/api/direct-entry/entries/$entryId/documents/$docId/download"
                } else {
                    null
                }
                add(
                    EntryDocumentItem(
                        documentId = docId,
                        documentType = docType,
                        version = doc.optInt("version", 1),
                        sizeBytes = doc.optLong("size_bytes", 0L),
                        mimeType = doc.optString("mime_type", ""),
                        uploadStatus = uploadStatus,
                        scanStatus = doc.optString("scan_status", ""),
                        validationStatus = doc.optString("validation_status", ""),
                        previewPath = previewPath,
                        downloadPath = downloadPath
                    )
                )
            }
        }

        val fileVersions = buildMap {
            for (spec in DIRECT_EDIT_DOCUMENT_TYPES) {
                val readyMatches = documents.filter { it.documentType == spec.type && it.uploadStatus == "READY" }
                if (readyMatches.isNotEmpty()) {
                    val maxVer = readyMatches.maxOf { it.version.coerceAtLeast(1) }
                    put(spec.type, maxVer)
                } else if (spec.type == "WORKER_PORTRAIT" && portraitDocId != null && portraitCurrent != null) {
                    put("WORKER_PORTRAIT", portraitCurrent.optInt("version", 1).coerceAtLeast(1))
                }
            }
        }

        return WorkerEntryDetail(
            entryId = entryId,
            submissionId = submissionId,
            version = version,
            projectId = entry.optString("project_id", ""),
            firstWorkDate = entry.optString("first_work_date", ""),
            employeeCode = entry.optString("employee_code", ""),
            recruiterId = entry.optString("recruiter_id", ""),
            teamId = entry.optString("team_id", ""),
            providerType = entry.optString("provider_type", ""),
            laborType = entry.optString("labor_type", ""),
            laborTypeDisplay = entry.optString("labor_type_display", ""),
            displayName = displayName,
            gender = workerObj.extractOptionalFieldValue("gender"),
            dateOfBirth = workerObj.extractOptionalFieldValue("date_of_birth"),
            nationalId = workerObj.extractOptionalFieldValue("national_id"),
            nationalIdIssuedAt = workerObj.extractOptionalFieldValue("national_id_issued_at"),
            nationalIdIssuedPlace = workerObj.extractOptionalFieldValue("national_id_issued_place"),
            address = workerObj.extractOptionalFieldValue("address"),
            phone = workerObj.extractOptionalFieldValue("phone"),
            generalNote = generalNote,
            paymentState = paymentState,
            paymentVersion = paymentVersion,
            bankId = bankId,
            bankName = bankName,
            rawBankName = rawBankName,
            accountNumber = accountNumber,
            accountHolderName = accountHolderName,
            employmentStatus = statusCode,
            employmentEffectiveDate = effectiveDate,
            employmentStatusVersion = statusVersion,
            employmentLeaveReason = leaveReason,
            portraitPreviewPath = portraitPreviewPath,
            portraitDownloadPath = portraitDownloadPath,
            documents = documents,
            workerDetailsStates = workerDetailsStates,
            fileVersions = fileVersions
        )
    }

    fun parseChangeRequestCreatedResponse(json: JSONObject): ChangeRequestCreatedSummary {
        val requestId = json.optString("request_id", "").trim()
        val state = json.optString("state", "").trim()
        val items = json.optInt("items", 0)
        if (!UUID_REGEX.matches(requestId) || state != "PENDING" || items < 1) {
            throw contractInvalid("CHANGE_REQUEST_RESPONSE_INVALID")
        }
        return ChangeRequestCreatedSummary(
            requestId = requestId,
            state = state,
            itemsCount = items
        )
    }

    private val SHA256_LOWER_HEX_REGEX = Regex("^[0-9a-f]{64}$")
    private val LAST4_DIGITS_REGEX = Regex("^[0-9]{4}$")

    fun buildSubmissionTransitionPayload(
        expectedVersion: Int,
        targetState: String,
        idempotencyKey: String,
        duplicateCccdFingerprint: String? = null,
        duplicateCccdCount: Int? = null
    ): JSONObject {
        if (expectedVersion < 1) {
            throw invalid("SUBMISSION_TRANSITION_INVALID", "Phiên bản đợt nhập không hợp lệ.")
        }
        if (targetState !in setOf("DRAFT", "SUBMITTED")) {
            throw invalid("SUBMISSION_TRANSITION_INVALID", "Trạng thái chuyển đổi không hợp lệ: $targetState")
        }
        if (!UUID_REGEX.matches(idempotencyKey.trim())) {
            throw invalid("IDEMPOTENCY_KEY_INVALID", "Khóa chống gửi trùng không hợp lệ.")
        }
        val payload = JSONObject()
            .put("expected_version", expectedVersion)
            .put("target_state", targetState)
            .put("idempotency_key", idempotencyKey.trim())

        if (duplicateCccdFingerprint != null || duplicateCccdCount != null) {
            val cleanFp = duplicateCccdFingerprint?.trim().orEmpty()
            val count = duplicateCccdCount ?: 0
            if (targetState != "SUBMITTED" || !SHA256_LOWER_HEX_REGEX.matches(cleanFp) || count !in 1..1000) {
                throw invalid("SUBMISSION_TRANSITION_INVALID", "Xác nhận trùng CCCD không hợp lệ.")
            }
            payload.put("duplicate_cccd_fingerprint", cleanFp)
            payload.put("duplicate_cccd_count", count)
        }
        return payload
    }

    fun parseDuplicateCccdPreflightResponse(
        json: JSONObject,
        expectedSubmissionId: String
    ): DuplicateCccdPreflightResult {
        val submissionId = json.optString("submission_id", "").trim()
        val version = json.optInt("version", 0)
        val fingerprint = json.optString("fingerprint", "").trim()
        val conflictCount = json.optInt("conflict_count", -1)
        val conflictsArr = json.optJSONArray("conflicts") ?: throw contractInvalid("PREFLIGHT_CONFLICTS_MISSING")

        if (!UUID_REGEX.matches(submissionId) ||
            submissionId != expectedSubmissionId.trim() ||
            version < 1 ||
            !SHA256_LOWER_HEX_REGEX.matches(fingerprint) ||
            conflictCount !in 0..1000 ||
            conflictsArr.length() > conflictCount
        ) {
            throw contractInvalid("PREFLIGHT_RESPONSE_INVALID")
        }

        val conflicts = buildList {
            for (i in 0 until conflictsArr.length()) {
                val c = conflictsArr.optJSONObject(i) ?: throw contractInvalid("PREFLIGHT_CONFLICT_ITEM_INVALID")
                val ref = c.optString("conflict_ref", "").trim()
                val draftName = c.optString("draft_display_name", "")
                val projDisplay = c.optNullableString("project_display")
                val empStatus = c.optString("employment_status", "").trim()
                val last4 = c.optString("cccd_last4", "").trim()
                if (ref.isEmpty() || empStatus !in setOf("ON", "UNCONFIRMED") || !LAST4_DIGITS_REGEX.matches(last4)) {
                    throw contractInvalid("PREFLIGHT_CONFLICT_ITEM_INVALID")
                }
                add(
                    DuplicateCccdPreflightConflict(
                        conflictRef = ref,
                        draftDisplayName = draftName,
                        projectDisplay = projDisplay,
                        employmentStatus = empStatus,
                        cccdLast4 = last4
                    )
                )
            }
        }
        return DuplicateCccdPreflightResult(
            submissionId = submissionId,
            version = version,
            fingerprint = fingerprint,
            conflictCount = conflictCount,
            conflicts = conflicts
        )
    }

    fun parseSubmissionTransitionResponse(
        json: JSONObject,
        expectedSubmissionId: String
    ): SubmissionTransitionResult {
        val submissionId = json.optString("submission_id", "").trim()
        val state = json.optString("state", "").trim()
        val version = json.optInt("version", 0)
        if (!UUID_REGEX.matches(submissionId) ||
            submissionId != expectedSubmissionId.trim() ||
            state !in setOf("DRAFT", "REVIEW", "SUBMITTED") ||
            version < 1
        ) {
            throw contractInvalid("SUBMISSION_TRANSITION_RESPONSE_INVALID")
        }
        return SubmissionTransitionResult(
            submissionId = submissionId,
            state = state,
            version = version
        )
    }

    val ALLOWED_CCCD_REVIEW_STATUS_FILTERS = setOf(
        "NEEDS_REVIEW",
        "MISMATCH",
        "QR_UNREADABLE",
        "PENDING",
        "MATCHED_INITIAL",
        "RESOLVED",
        "ALL"
    )

    val ALLOWED_CCCD_STANDARDIZE_FIELDS = setOf(
        "national_id",
        "display_name",
        "date_of_birth",
        "gender",
        "address",
        "national_id_issued_at"
    )

    fun formatIsoTimestampHcmUi(isoOrNull: String?, fallbackIso: String? = null): String {
        val raw = isoOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackIso?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ""
        val epochMs = runCatching { AttendanceTimeContract.parseIsoInstantToEpochMillis(raw) }.getOrNull()
        if (epochMs != null) {
            return SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }.format(Date(epochMs))
        }
        val dateOnly = raw.take(10)
        return if (ISO_DATE_REGEX.matches(dateOnly)) isoDateToUiDate(dateOnly) else raw
    }

    fun parseCccdReviewQueueResponse(json: JSONObject): CccdReviewQueuePage {
        val scopeKind = json.optString("scope_kind", "own").trim()
        val statusFilter = json.optString("status_filter", "NEEDS_REVIEW").trim()
        val limit = json.optInt("limit", 25).coerceIn(1, 100)
        val offset = json.optInt("offset", 0).coerceAtLeast(0)
        val totalCount = json.optInt("total_count", 0).coerceAtLeast(0)

        val summaryObj = json.optJSONObject("summary")
        val summary = CccdReviewSummary(
            totalCount = summaryObj?.optInt("total_count", totalCount) ?: totalCount,
            needsReviewCount = summaryObj?.optInt("needs_review_count", 0) ?: 0,
            mismatchCount = summaryObj?.optInt("mismatch_count", 0) ?: 0,
            qrUnreadableCount = summaryObj?.optInt("qr_unreadable_count", 0) ?: 0,
            pendingCount = summaryObj?.optInt("pending_count", 0) ?: 0,
            matchedInitialCount = summaryObj?.optInt("matched_initial_count", 0) ?: 0,
            resolvedCount = summaryObj?.optInt("resolved_count", 0) ?: 0
        )

        val rowsArr = json.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (i in 0 until rowsArr.length()) {
                val r = rowsArr.optJSONObject(i) ?: continue
                val entryId = r.optString("entry_id", "").trim()
                if (!UUID_REGEX.matches(entryId)) continue
                val qrObj = r.optJSONObject("qr_extracted_payload")
                val qrPayload = if (qrObj != null) {
                    val cccd12 = qrObj.optString("cccd_12", "").trim()
                    if (cccd12.isNotEmpty()) {
                        val gCanon = qrObj.optNullableString("gender_canonical")
                        val gUi = qrObj.optNullableString("gender_raw") ?: when (gCanon) {
                            "MALE" -> "Nam"
                            "FEMALE" -> "Nữ"
                            else -> null
                        }
                        CccdDecodedQrPayload(
                            cccd12 = cccd12,
                            legacyId9 = qrObj.optNullableString("legacy_id_9"),
                            fullName = qrObj.optNullableString("full_name"),
                            dateOfBirthUi = qrObj.optNullableString("date_of_birth_ui"),
                            genderCanonical = gCanon,
                            genderUi = gUi,
                            address = qrObj.optNullableString("address"),
                            issueDateUi = qrObj.optNullableString("issue_date_ui")
                        )
                    } else null
                } else null

                val compArr = r.optJSONArray("comparison") ?: JSONArray()
                val comparison = buildList {
                    for (j in 0 until compArr.length()) {
                        val c = compArr.optJSONObject(j) ?: continue
                        val fieldKey = c.optString("field", "").trim()
                        if (fieldKey.isEmpty()) continue
                        add(
                            CccdReviewComparisonField(
                                field = fieldKey,
                                label = c.optString("label", fieldKey),
                                enteredValue = c.optNullableString("entered_value"),
                                qrValue = c.optNullableString("qr_value"),
                                status = c.optString("status", "QR_MISSING").trim()
                            )
                        )
                    }
                }

                add(
                    CccdReviewQueueItem(
                        entryId = entryId,
                        submissionId = r.optString("submission_id", "").trim(),
                        submissionState = r.optString("submission_state", "").trim(),
                        entryVersion = r.optInt("entry_version", 1).coerceAtLeast(1),
                        employeeCode = r.optString("employee_code", "").trim(),
                        projectId = r.optString("project_id", "").trim(),
                        projectDisplay = r.optString("project_display", "").trim(),
                        firstWorkDate = r.optString("first_work_date", "").trim(),
                        recruiterId = r.optString("recruiter_id", "").trim(),
                        recruiterDisplay = r.optString("recruiter_display", "").trim(),
                        enteredDisplayName = r.optString("entered_display_name", "").trim(),
                        enteredNationalId = r.optNullableString("entered_national_id"),
                        enteredDateOfBirth = r.optNullableString("entered_date_of_birth"),
                        enteredGender = r.optNullableString("entered_gender"),
                        enteredAddress = r.optNullableString("entered_address"),
                        enteredNationalIdIssuedAt = r.optNullableString("entered_national_id_issued_at"),
                        cccdFrontDocumentId = r.optNullableString("cccd_front_document_id"),
                        cccdFrontUploadStatus = r.optNullableString("cccd_front_upload_status"),
                        cccdFrontQrStatus = r.optString("cccd_front_qr_status", "NOT_UPLOADED").trim(),
                        cccdFrontPreviewPath = r.optNullableString("cccd_front_preview_path"),
                        cccdBackDocumentId = r.optNullableString("cccd_back_document_id"),
                        cccdBackUploadStatus = r.optNullableString("cccd_back_upload_status"),
                        cccdBackQrStatus = r.optString("cccd_back_qr_status", "NOT_UPLOADED").trim(),
                        cccdBackPreviewPath = r.optNullableString("cccd_back_preview_path"),
                        qrExtractedPayload = qrPayload,
                        reconciliationStatus = r.optString("reconciliation_status", "PENDING").trim(),
                        resolutionAction = r.optNullableString("resolution_action"),
                        resolvedAt = r.optNullableString("resolved_at"),
                        resolvedByUserId = r.optNullableString("resolved_by_user_id"),
                        resolutionNote = r.optNullableString("resolution_note"),
                        comparison = comparison
                    )
                )
            }
        }

        return CccdReviewQueuePage(
            scopeKind = scopeKind,
            statusFilter = statusFilter,
            limit = limit,
            offset = offset,
            totalCount = totalCount,
            summary = summary,
            rows = rows
        )
    }

    fun buildCccdReviewUpdateEnteredCccdPayload(
        expectedEntryVersion: Int,
        nationalId: String,
        applyBlankFieldsFromQr: Boolean = true,
        reason: String? = null
    ): JSONObject {
        if (expectedEntryVersion < 1) {
            throw invalid("EXPECTED_VERSION_INVALID", "Phiên bản hồ sơ không hợp lệ.")
        }
        val cleanCccd = nationalId.trim()
        if (!isValidCccd12(cleanCccd)) {
            throw invalid("NATIONAL_ID_INVALID", "Số CCCD cần sửa phải gồm đúng 12 chữ số hợp lệ (giữ nguyên số 0 ở đầu).")
        }
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanReason != null && (cleanReason.length > 500 || hasControlCharacter(cleanReason))) {
            throw invalid("REASON_INVALID", "Ghi chú xử lý không hợp lệ (tối đa 500 ký tự).")
        }
        return JSONObject()
            .put("action", "UPDATE_ENTERED_CCCD")
            .put("expected_entry_version", expectedEntryVersion)
            .put("national_id", cleanCccd)
            .put("apply_blank_fields_from_qr", applyBlankFieldsFromQr)
            .put("reason", cleanReason ?: JSONObject.NULL)
    }

    fun buildCccdReviewStandardizeFromQrPayload(
        expectedEntryVersion: Int,
        fields: List<String>,
        reason: String? = null
    ): JSONObject {
        if (expectedEntryVersion < 1) {
            throw invalid("EXPECTED_VERSION_INVALID", "Phiên bản hồ sơ không hợp lệ.")
        }
        val distinctFields = fields.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinctFields.isEmpty() || distinctFields.any { it !in ALLOWED_CCCD_STANDARDIZE_FIELDS }) {
            throw invalid("STANDARDIZE_FIELDS_INVALID", "Vui lòng chọn ít nhất 1 trường hợp lệ để chuẩn hóa từ QR.")
        }
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanReason != null && (cleanReason.length > 500 || hasControlCharacter(cleanReason))) {
            throw invalid("REASON_INVALID", "Ghi chú xử lý không hợp lệ (tối đa 500 ký tự).")
        }
        val fieldsArr = JSONArray()
        distinctFields.forEach { fieldsArr.put(it) }
        return JSONObject()
            .put("action", "STANDARDIZE_FROM_QR")
            .put("expected_entry_version", expectedEntryVersion)
            .put("fields", fieldsArr)
            .put("reason", cleanReason ?: JSONObject.NULL)
    }

    fun parseCccdReviewResolveResponse(
        json: JSONObject,
        expectedEntryId: String
    ): CccdReviewResolveResult {
        val entryId = json.optString("entry_id", "").trim()
        val entryVersion = json.optInt("entry_version", 0)
        val action = json.optString("action", "").trim()
        val reconciliationStatus = json.optString("reconciliation_status", "").trim()
        if (!UUID_REGEX.matches(entryId) || entryId != expectedEntryId.trim() || entryVersion < 1) {
            throw contractInvalid("CCCD_REVIEW_RESOLVE_RESPONSE_INVALID")
        }
        val updatedArr = json.optJSONArray("updated_fields") ?: JSONArray()
        val updatedFields = buildList {
            for (i in 0 until updatedArr.length()) {
                val f = updatedArr.optString(i, "").trim()
                if (f.isNotEmpty()) add(f)
            }
        }
        return CccdReviewResolveResult(
            entryId = entryId,
            entryVersion = entryVersion,
            action = action,
            reconciliationStatus = reconciliationStatus,
            updatedFields = updatedFields
        )
    }

    fun parseCccdQrRetryResponse(
        json: JSONObject,
        expectedEntryId: String
    ): CccdQrRetryResult {
        val entryId = json.optString("entry_id", "").trim()
        val entryVersion = json.optInt("entry_version", 0)
        val reconciliationStatus = json.optString("reconciliation_status", "PENDING").trim()
        val frontQrStatus = json.optString("front_qr_status", "NOT_UPLOADED").trim()
        val backQrStatus = json.optString("back_qr_status", "NOT_UPLOADED").trim()
        if (!UUID_REGEX.matches(entryId) || entryId != expectedEntryId.trim() || entryVersion < 1) {
            throw contractInvalid("CCCD_QR_RETRY_RESPONSE_INVALID")
        }
        val filledArr = json.optJSONArray("auto_filled_fields") ?: JSONArray()
        val autoFilled = buildList {
            for (i in 0 until filledArr.length()) {
                val f = filledArr.optString(i, "").trim()
                if (f.isNotEmpty()) add(f)
            }
        }
        return CccdQrRetryResult(
            entryId = entryId,
            entryVersion = entryVersion,
            reconciliationStatus = reconciliationStatus,
            frontQrStatus = frontQrStatus,
            backQrStatus = backQrStatus,
            autoFilledFields = autoFilled
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").trim().ifEmpty { null }
    }

    private fun JSONObject.extractOptionalFieldValue(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is JSONObject -> {
                if (raw.optString("state") == "provided") {
                    raw.optNullableString("value")
                } else {
                    null
                }
            }
            is String -> raw.trim().ifEmpty { null }
            else -> null
        }
    }

    private fun JSONObject.extractOptionalFieldState(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is JSONObject -> raw.optNullableString("state")
            is String -> if (raw.trim().isNotEmpty()) "provided" else "omitted"
            else -> null
        }
    }

    private fun invalid(code: String, message: String, cause: Throwable? = null): BackendApiException {
        return BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = 400,
            backendCode = code,
            message = message,
            cause = cause
        )
    }

    private fun contractInvalid(code: String): BackendApiException {
        return BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = code,
            message = "Dữ liệu máy chủ trả về không đúng hợp đồng ($code)."
        )
    }
}

object DocumentImageCompressor {
    const val MAX_RAW_PICKER_BYTES = 20L * 1024L * 1024L

    fun compressIfImage(
        rawBytes: ByteArray,
        mimeType: String,
        fileName: String,
        maxDimensionPx: Int = 1600,
        jpegQuality: Int = 82
    ): Triple<ByteArray, String, String> {
        if (rawBytes.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 413,
                backendCode = "DOCUMENT_SIZE_INVALID",
                message = "Kích thước tệp phải lớn hơn 0 và không vượt quá 10 MB."
            )
        }
        if (mimeType == "application/pdf") {
            if (rawBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 413,
                    backendCode = "DOCUMENT_SIZE_INVALID",
                    message = "Kích thước tệp PDF không được vượt quá 10 MB."
                )
            }
            return Triple(rawBytes, mimeType, fileName)
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            if (rawBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 413,
                    backendCode = "DOCUMENT_SIZE_INVALID",
                    message = "Kích thước tệp ảnh không được vượt quá 10 MB."
                )
            }
            return Triple(rawBytes, mimeType, fileName)
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
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
        if (decoded == null) {
            if (rawBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 413,
                    backendCode = "DOCUMENT_SIZE_INVALID",
                    message = "Kích thước tệp ảnh không được vượt quá 10 MB."
                )
            }
            return Triple(rawBytes, mimeType, fileName)
        }

        val maxSide = maxOf(decoded.width, decoded.height)
        val scaled = if (maxSide > maxDimensionPx && maxDimensionPx > 0) {
            val ratio = maxDimensionPx.toFloat() / maxSide.toFloat()
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val created = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (created !== decoded) {
                decoded.recycle()
            }
            created
        } else {
            decoded
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(50, 95), baos)
        scaled.recycle()
        val compressed = baos.toByteArray()

        if (compressed.isNotEmpty() &&
            compressed.size <= DirectEntryContract.MAX_STAGE_BYTES &&
            (compressed.size < rawBytes.size || rawBytes.size > DirectEntryContract.MAX_STAGE_BYTES)
        ) {
            val baseName = fileName.substringBeforeLast('.', fileName).ifBlank { "photo" }
            return Triple(compressed, "image/jpeg", "$baseName.jpg")
        }

        if (rawBytes.size <= DirectEntryContract.MAX_STAGE_BYTES) {
            return Triple(rawBytes, mimeType, fileName)
        }

        throw BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = 413,
            backendCode = "DOCUMENT_SIZE_INVALID",
            message = "Kích thước tệp ảnh sau khi tối ưu vẫn vượt quá 10 MB."
        )
    }

    data class CompressedJpegPage(
        val jpegBytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int
    )

    internal fun applyExifOrientation(bitmap: Bitmap, rawBytes: ByteArray): Bitmap {
        return runCatching {
            val exif = ExifInterface(ByteArrayInputStream(rawBytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        }.getOrDefault(bitmap)
    }

    fun compressImageToJpegPage(
        rawBytes: ByteArray,
        maxDimensionPx: Int = 1600,
        jpegQuality: Int = 80
    ): CompressedJpegPage {
        if (rawBytes.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 413,
                backendCode = "DOCUMENT_SIZE_INVALID",
                message = "Kích thước tệp ảnh phải lớn hơn 0."
            )
        }
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 415,
                backendCode = "DOCUMENT_MIME_INVALID",
                message = "Tệp đã chọn không phải là hình ảnh hợp lệ."
            )
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
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 415,
                backendCode = "DOCUMENT_MIME_INVALID",
                message = "Không thể giải mã hình ảnh đã chọn."
            )

        val oriented = applyExifOrientation(decoded, rawBytes)
        val maxSide = maxOf(oriented.width, oriented.height)
        val scaled = if (maxSide > maxDimensionPx && maxDimensionPx > 0) {
            val ratio = maxDimensionPx.toFloat() / maxSide.toFloat()
            val targetW = (oriented.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (oriented.height * ratio).toInt().coerceAtLeast(1)
            val created = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            if (created !== oriented) {
                oriented.recycle()
            }
            created
        } else {
            oriented
        }

        val finalW = scaled.width.coerceAtLeast(1)
        val finalH = scaled.height.coerceAtLeast(1)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(45, 92), baos)
        scaled.recycle()
        val compressed = baos.toByteArray()
        return CompressedJpegPage(
            jpegBytes = compressed,
            widthPx = finalW,
            heightPx = finalH
        )
    }

    fun extractJpegDimensions(jpegBytes: ByteArray): Pair<Int, Int> {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, boundsOpts)
        if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
            return boundsOpts.outWidth to boundsOpts.outHeight
        }
        return 595 to 842
    }

    fun buildPdfFromJpegPages(pages: List<CompressedJpegPage>): ByteArray {
        if (pages.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "NO_FILES_SELECTED",
                message = "Chưa có trang ảnh nào để tạo PDF."
            )
        }
        val out = ByteArrayOutputStream()
        fun writeAscii(text: String) {
            out.write(text.toByteArray(Charsets.US_ASCII))
        }

        // PDF 1.4 header + binary marker
        writeAscii("%PDF-1.4\n")
        out.write(byteArrayOf(0x25.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A.toByte()))

        val totalObjs = 3 + pages.size * 3
        val offsets = IntArray(totalObjs)

        // Object 1: Catalog
        offsets[1] = out.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Object 2: Pages
        val kidsRefs = pages.indices.joinToString(" ") { idx -> "${3 + idx * 3} 0 R" }
        offsets[2] = out.size()
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [ $kidsRefs ] /Count ${pages.size} >>\nendobj\n")

        for (i in pages.indices) {
            val page = pages[i]
            val pageObjNum = 3 + i * 3
            val contentObjNum = 4 + i * 3
            val imgObjNum = 5 + i * 3

            val wPx = page.widthPx.coerceAtLeast(1)
            val hPx = page.heightPx.coerceAtLeast(1)
            val pageW: Int
            val pageH: Int
            if (hPx >= wPx) {
                pageW = 595
                pageH = ((595.0 * hPx) / wPx.toDouble()).toInt().coerceIn(200, 1684)
            } else {
                pageW = 842
                pageH = ((842.0 * hPx) / wPx.toDouble()).toInt().coerceIn(200, 1684)
            }

            // Page object
            offsets[pageObjNum] = out.size()
            writeAscii(
                "$pageObjNum 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 $pageW $pageH ] " +
                    "/Resources << /XObject << /Im0 $imgObjNum 0 R >> >> " +
                    "/Contents $contentObjNum 0 R >>\n" +
                    "endobj\n"
            )

            // Content stream object
            val contentBytes = "q\n$pageW 0 0 $pageH 0 0 cm\n/Im0 Do\nQ\n".toByteArray(Charsets.US_ASCII)
            offsets[contentObjNum] = out.size()
            writeAscii("$contentObjNum 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
            out.write(contentBytes)
            writeAscii("endstream\nendobj\n")

            // Image XObject (DCTDecode)
            offsets[imgObjNum] = out.size()
            writeAscii(
                "$imgObjNum 0 obj\n" +
                    "<< /Type /XObject /Subtype /Image /Width $wPx /Height $hPx " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                    "/Length ${page.jpegBytes.size} >>\n" +
                    "stream\n"
            )
            out.write(page.jpegBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = out.size()
        writeAscii("xref\n0 $totalObjs\n")
        writeAscii("0000000000 65535 f \r\n")
        for (objId in 1 until totalObjs) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \r\n", offsets[objId]))
        }
        writeAscii(
            "trailer\n" +
                "<< /Size $totalObjs /Root 1 0 R >>\n" +
                "startxref\n" +
                "$xrefOffset\n" +
                "%%EOF\n"
        )
        return out.toByteArray()
    }

    fun mergeRawImagesToPdfBytes(
        rawImages: List<ByteArray>,
        maxDimensionPx: Int = 1600,
        jpegQuality: Int = 80
    ): ByteArray {
        if (rawImages.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "NO_FILES_SELECTED",
                message = "Chưa chọn ảnh nào để gộp PDF."
            )
        }
        val firstPassPages = rawImages.map { raw ->
            compressImageToJpegPage(raw, maxDimensionPx = maxDimensionPx, jpegQuality = jpegQuality)
        }
        var pdfBytes = buildPdfFromJpegPages(firstPassPages)
        if (pdfBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
            val secondPassPages = rawImages.map { raw ->
                compressImageToJpegPage(raw, maxDimensionPx = 1200, jpegQuality = 65)
            }
            pdfBytes = buildPdfFromJpegPages(secondPassPages)
        }
        if (pdfBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 413,
                backendCode = "DOCUMENT_SIZE_INVALID",
                message = "Tổng dung lượng tệp PDF sau khi gộp vượt quá giới hạn 10 MB. Vui lòng giảm số lượng ảnh."
            )
        }
        return pdfBytes
    }
}

class DirectEntryService(
    private val context: Context,
    private val httpClient: BackendHttpClient,
    private val dao: MiniBiDao,
    val idempotencyStore: IntentIdempotencyStore = IntentIdempotencyStore()
) {

    suspend fun fetchCatalog(
        effectiveDateIso: String = DirectEntryContract.todayIsoInHcm()
    ): DirectEntryCatalog = withContext(Dispatchers.IO) {
        val json = httpClient.getJson(
            pathWithQuery = "/api/direct-entry/catalog?effective_date=$effectiveDateIso",
            requireOkTrue = true
        )
        DirectEntryContract.parseCatalogResponse(json)
    }

    suspend fun uploadStageBytes(
        documentType: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): StagedDocumentRef = withContext(Dispatchers.IO) {
        if (documentType !in DirectEntryContract.ALLOWED_STAGE_DOCUMENT_TYPES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "DOCUMENT_TYPE_INVALID",
                message = "Loại tài liệu tải lên không hợp lệ: $documentType"
            )
        }
        val allowedMimes = if (documentType == "WORKER_PORTRAIT") {
            setOf("image/jpeg", "image/png")
        } else {
            setOf("image/jpeg", "image/png", "application/pdf")
        }
        if (mimeType !in allowedMimes) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 415,
                backendCode = "DOCUMENT_MIME_INVALID",
                message = if (documentType == "WORKER_PORTRAIT") {
                    "Ảnh chân dung chỉ chấp nhận JPEG hoặc PNG."
                } else {
                    "Tài liệu chỉ chấp nhận JPEG, PNG hoặc PDF."
                }
            )
        }
        if (fileBytes.isEmpty() || fileBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 413,
                backendCode = "DOCUMENT_SIZE_INVALID",
                message = "Kích thước tệp phải lớn hơn 0 và không vượt quá 10 MB."
            )
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName.ifBlank { "document.bin" },
                fileBytes.toRequestBody(mimeType.toMediaType())
            )
            .addFormDataPart("document_type", documentType)
            .build()

        val json = httpClient.postMultipartJson(
            path = "/api/direct-entry/worker-portrait-stages",
            multipartBody = multipartBody,
            idempotencyKey = idempotencyKey,
            requireOkTrue = true
        )
        DirectEntryContract.parseStageUploadResponse(json, fileName)
    }

    suspend fun uploadStageUri(
        documentType: String,
        uri: Uri
    ): StagedDocumentRef = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        var displayName = "attachment"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                    displayName = cursor.getString(nameIdx).ifBlank { "attachment" }
                }
            }
        }
        val normalizedMime = when {
            rawMime == "image/jpg" -> "image/jpeg"
            rawMime in setOf("image/jpeg", "image/png", "application/pdf") -> rawMime
            displayName.lowercase(Locale.ROOT).endsWith(".png") -> "image/png"
            displayName.lowercase(Locale.ROOT).endsWith(".pdf") -> "application/pdf"
            else -> "image/jpeg"
        }

        val maxAllowedReadBytes = if (normalizedMime == "application/pdf") {
            DirectEntryContract.MAX_STAGE_BYTES
        } else {
            DocumentImageCompressor.MAX_RAW_PICKER_BYTES
        }

        val rawBytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                total += read
                if (total > maxAllowedReadBytes) {
                    throw BackendApiException(
                        kind = BackendFailureKind.INVALID_REQUEST,
                        statusCode = 413,
                        backendCode = "DOCUMENT_TOO_LARGE",
                        message = "Tệp đính kèm vượt quá giới hạn dung lượng cho phép."
                    )
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = 400,
            backendCode = "FILE_READ_FAILED",
            message = "Không thể đọc tệp đã chọn từ thiết bị."
        )

        val (compressedBytes, outMime, outName) = DocumentImageCompressor.compressIfImage(
            rawBytes = rawBytes,
            mimeType = normalizedMime,
            fileName = displayName
        )

        uploadStageBytes(
            documentType = documentType,
            fileBytes = compressedBytes,
            fileName = outName,
            mimeType = outMime
        )
    }

    suspend fun deleteStage(stageId: String) = withContext(Dispatchers.IO) {
        val trimmed = stageId.trim()
        if (trimmed.isEmpty()) return@withContext
        val body = JSONObject().put("stage_id", trimmed)
        httpClient.deleteWithJsonNoContent(
            path = "/api/direct-entry/worker-portrait-stages",
            bodyJson = body
        )
    }

    suspend fun prepareLocalAttachmentBytes(
        documentType: String,
        rawBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): StagedDocumentRef = withContext(Dispatchers.IO) {
        if (documentType !in DirectEntryContract.ALLOWED_STAGE_DOCUMENT_TYPES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "DOCUMENT_TYPE_INVALID",
                message = "Loại tài liệu đính kèm không hợp lệ."
            )
        }
        val allowedMimes = if (documentType == "WORKER_PORTRAIT") {
            setOf("image/jpeg", "image/png")
        } else {
            setOf("image/jpeg", "image/png", "application/pdf")
        }
        if (mimeType !in allowedMimes) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 415,
                backendCode = "DOCUMENT_MIME_INVALID",
                message = if (documentType == "WORKER_PORTRAIT") {
                    "Ảnh chân dung chỉ chấp nhận JPEG hoặc PNG."
                } else {
                    "Tài liệu chỉ chấp nhận JPEG, PNG hoặc PDF."
                }
            )
        }
        val (outBytes, outMime, outName) = DocumentImageCompressor.compressIfImage(
            rawBytes = rawBytes,
            mimeType = mimeType,
            fileName = fileName.ifBlank { "attachment" }
        )
        val pendingDir = File(context.cacheDir, "pending_worker_docs").apply { mkdirs() }
        val tempFile = File(pendingDir, "${UUID.randomUUID()}.bin")
        tempFile.writeBytes(outBytes)
        StagedDocumentRef(
            stageId = "",
            documentType = documentType,
            fileName = outName.ifBlank { "attachment" },
            mimeType = outMime,
            sizeBytes = outBytes.size.toLong(),
            localFilePath = tempFile.absolutePath
        )
    }

    suspend fun prepareLocalAttachmentUri(
        documentType: String,
        uri: Uri
    ): StagedDocumentRef = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        var displayName = "attachment"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                    displayName = cursor.getString(nameIdx).ifBlank { "attachment" }
                }
            }
        }
        val normalizedMime = when {
            rawMime == "image/jpg" -> "image/jpeg"
            rawMime in setOf("image/jpeg", "image/png", "application/pdf") -> rawMime
            displayName.lowercase(Locale.ROOT).endsWith(".png") -> "image/png"
            displayName.lowercase(Locale.ROOT).endsWith(".pdf") -> "application/pdf"
            else -> "image/jpeg"
        }
        val maxAllowedReadBytes = if (normalizedMime == "application/pdf") {
            DirectEntryContract.MAX_STAGE_BYTES
        } else {
            DocumentImageCompressor.MAX_RAW_PICKER_BYTES
        }
        val rawBytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                total += read
                if (total > maxAllowedReadBytes) {
                    throw BackendApiException(
                        kind = BackendFailureKind.INVALID_REQUEST,
                        statusCode = 413,
                        backendCode = "DOCUMENT_TOO_LARGE",
                        message = "Tệp đính kèm vượt quá giới hạn dung lượng cho phép."
                    )
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = 400,
            backendCode = "FILE_READ_FAILED",
            message = "Không thể đọc tệp đã chọn từ thiết bị."
        )
        prepareLocalAttachmentBytes(
            documentType = documentType,
            rawBytes = rawBytes,
            fileName = displayName,
            mimeType = normalizedMime
        )
    }

    suspend fun prepareLocalMultiImagePdfAttachment(
        documentType: String,
        uris: List<Uri>,
        existingRef: StagedDocumentRef? = null
    ): StagedDocumentRef = withContext(Dispatchers.IO) {
        if (documentType !in DirectEntryContract.ALLOWED_STAGE_DOCUMENT_TYPES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "DOCUMENT_TYPE_INVALID",
                message = "Loại tài liệu đính kèm không hợp lệ."
            )
        }
        if (uris.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "NO_FILES_SELECTED",
                message = "Chưa chọn tệp ảnh nào để đính kèm."
            )
        }
        val resolver = context.contentResolver

        // If user selected a single PDF file, handle it via single-file PDF path
        if (uris.size == 1) {
            val singleUri = uris.first()
            val rawMime = resolver.getType(singleUri)?.lowercase(Locale.ROOT).orEmpty()
            var displayName = ""
            resolver.query(singleUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        displayName = cursor.getString(idx).orEmpty()
                    }
                }
            }
            if (rawMime == "application/pdf" || displayName.lowercase(Locale.ROOT).endsWith(".pdf")) {
                deleteLocalAttachmentRef(existingRef)
                return@withContext prepareLocalAttachmentUri(documentType, singleUri)
            }
        }

        val pagesDir = File(context.cacheDir, "pending_worker_docs/pages").apply { mkdirs() }
        val existingPagePaths = existingRef?.sourceImagePaths
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && File(it).exists() }
            .orEmpty()

        val existingPages = existingPagePaths.mapNotNull { path ->
            runCatching {
                val bytes = File(path).readBytes()
                if (bytes.isEmpty()) return@mapNotNull null
                val (w, h) = DocumentImageCompressor.extractJpegDimensions(bytes)
                path to DocumentImageCompressor.CompressedJpegPage(bytes, w, h)
            }.getOrNull()
        }

        val newCreatedFiles = mutableListOf<File>()
        try {
            val newPages = uris.map { uri ->
                val rawMime = resolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
                if (rawMime == "application/pdf") {
                    throw BackendApiException(
                        kind = BackendFailureKind.INVALID_REQUEST,
                        statusCode = 415,
                        backendCode = "DOCUMENT_MIME_INVALID",
                        message = "Khi chọn nhiều trang ảnh, vui lòng chỉ chọn ảnh JPG/PNG (hoặc bấm 'Chọn PDF' nếu đã có sẵn file PDF)."
                    )
                }
                val rawBytes = resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        total += read
                        if (total > DocumentImageCompressor.MAX_RAW_PICKER_BYTES) {
                            throw BackendApiException(
                                kind = BackendFailureKind.INVALID_REQUEST,
                                statusCode = 413,
                                backendCode = "DOCUMENT_TOO_LARGE",
                                message = "Một trong các ảnh đã chọn vượt quá giới hạn dung lượng 20 MB."
                            )
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                } ?: throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 400,
                    backendCode = "FILE_READ_FAILED",
                    message = "Không thể đọc tệp ảnh đã chọn từ thiết bị."
                )

                val page = DocumentImageCompressor.compressImageToJpegPage(rawBytes)
                val pageFile = File(pagesDir, "${UUID.randomUUID()}.jpg")
                pageFile.writeBytes(page.jpegBytes)
                newCreatedFiles.add(pageFile)
                pageFile.absolutePath to page
            }

            val allEntries = existingPages + newPages
            var allPages = allEntries.map { it.second }
            var pdfBytes = DocumentImageCompressor.buildPdfFromJpegPages(allPages)

            if (pdfBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
                allPages = allEntries.map { (path, page) ->
                    val recompressed = DocumentImageCompressor.compressImageToJpegPage(
                        rawBytes = page.jpegBytes,
                        maxDimensionPx = 1200,
                        jpegQuality = 65
                    )
                    runCatching { File(path).writeBytes(recompressed.jpegBytes) }
                    recompressed
                }
                pdfBytes = DocumentImageCompressor.buildPdfFromJpegPages(allPages)
            }

            if (pdfBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 413,
                    backendCode = "DOCUMENT_SIZE_INVALID",
                    message = "Tổng dung lượng tệp PDF (${allPages.size} trang) vượt quá giới hạn 10 MB. Vui lòng giảm bớt số trang ảnh."
                )
            }

            val pendingDir = File(context.cacheDir, "pending_worker_docs").apply { mkdirs() }
            val pdfFile = File(pendingDir, "${UUID.randomUUID()}.pdf")
            pdfFile.writeBytes(pdfBytes)

            // Delete previous merged PDF file while keeping existing page JPEGs in pagesDir
            deleteLocalAttachment(existingRef?.localFilePath)

            val totalPages = allPages.size
            StagedDocumentRef(
                stageId = "",
                documentType = documentType,
                fileName = "ho_so_${totalPages}_trang.pdf",
                mimeType = "application/pdf",
                sizeBytes = pdfBytes.size.toLong(),
                localFilePath = pdfFile.absolutePath,
                pageCount = totalPages,
                sourceImagePaths = allEntries.map { it.first }
            )
        } catch (e: Exception) {
            newCreatedFiles.forEach { runCatching { it.delete() } }
            throw e
        }
    }

    suspend fun deleteLocalAttachment(localFilePath: String?) = withContext(Dispatchers.IO) {
        val cleanPath = localFilePath?.trim().orEmpty()
        if (cleanPath.isEmpty()) return@withContext
        runCatching {
            val file = File(cleanPath)
            val cacheRoot = context.cacheDir.canonicalPath
            if (file.exists() && file.canonicalPath.startsWith(cacheRoot)) {
                file.delete()
            }
        }
    }

    suspend fun deleteLocalAttachmentRef(ref: StagedDocumentRef?) = withContext(Dispatchers.IO) {
        if (ref == null) return@withContext
        deleteLocalAttachment(ref.localFilePath)
        ref.sourceImagePaths.forEach { pagePath ->
            deleteLocalAttachment(pagePath)
        }
    }

    /**
     * Post-entry document upload matching App BI `document-api.ts` & `document-finalize-api.ts`:
     * 1. Reserve document slot (`POST /api/direct-entry/entries/{entryId}/documents`)
     * 2. Binary PUT to presigned R2 URL (`upload.url`) without cookies or Origin header
     * 3. Finalize document (`POST /api/direct-entry/entries/{entryId}/documents/{docId}/finalize`)
     * Returns the updated authoritative `entry_version` for sequential chaining on the same entry.
     */
    suspend fun uploadDocumentToEntry(
        entryId: String,
        initialEntryVersion: Int? = null,
        documentType: String,
        fileBytes: ByteArray,
        mimeType: String,
        idempotencyBaseKey: String = UUID.randomUUID().toString()
    ): Int = withContext(Dispatchers.IO) {
        val cleanEntryId = entryId.trim()
        if (!DirectEntryContract.isUuid(cleanEntryId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "ENTRY_ID_INVALID",
                message = "Mã hồ sơ người lao động không hợp lệ."
            )
        }
        if (documentType !in DirectEntryContract.ALLOWED_STAGE_DOCUMENT_TYPES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "DOCUMENT_TYPE_INVALID",
                message = "Loại tài liệu đính kèm không hợp lệ."
            )
        }
        val allowedMimes = if (documentType == "WORKER_PORTRAIT") {
            setOf("image/jpeg", "image/png")
        } else {
            setOf("image/jpeg", "image/png", "application/pdf")
        }
        if (mimeType !in allowedMimes) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 415,
                backendCode = "DOCUMENT_MIME_INVALID",
                message = "Định dạng tệp đính kèm không được hỗ trợ."
            )
        }
        if (fileBytes.isEmpty() || fileBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 413,
                backendCode = "DOCUMENT_SIZE_INVALID",
                message = "Kích thước tệp phải lớn hơn 0 và không vượt quá 10 MB."
            )
        }

        fun buildReserveBody(expectedVersion: Int): JSONObject {
            return JSONObject()
                .put("document_type", documentType)
                .put("expected_entry_version", expectedVersion)
                .put("size_bytes", fileBytes.size)
                .put("mime_type", mimeType)
                .put("reason", JSONObject.NULL)
        }

        val initialVersion = initialEntryVersion?.takeIf { it >= 1 } ?: getEntryDetail(cleanEntryId).version
        val reservePath = "/api/direct-entry/entries/$cleanEntryId/documents"
        val reserveJson = try {
            httpClient.postJson(
                path = reservePath,
                bodyJson = buildReserveBody(initialVersion),
                idempotencyKey = "$idempotencyBaseKey:reserve",
                requireOkTrue = true
            )
        } catch (conflictErr: BackendApiException) {
            if (conflictErr.kind != BackendFailureKind.CONFLICT) {
                throw conflictErr
            }
            val latestVersion = getEntryDetail(cleanEntryId).version
            httpClient.postJson(
                path = reservePath,
                bodyJson = buildReserveBody(latestVersion),
                idempotencyKey = "$idempotencyBaseKey:reserve:r1",
                requireOkTrue = true
            )
        }

        val documentId = reserveJson.optString("document_id", "").trim()
        val reservedEntryVersion = reserveJson.optInt("entry_version", 0)
        val uploadStatus = reserveJson.optString("upload_status", "").trim()
        if (!DirectEntryContract.isUuid(documentId) || reservedEntryVersion < 1) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "DOCUMENT_RESERVE_INVALID",
                message = "Phản hồi khởi tạo tải tài liệu từ máy chủ không hợp lệ."
            )
        }

        val uploadObj = if (reserveJson.has("upload") && !reserveJson.isNull("upload")) {
            reserveJson.optJSONObject("upload")
        } else {
            null
        }

        if (uploadObj != null) {
            val signedUrl = uploadObj.optString("url", "").trim()
            if (signedUrl.isEmpty()) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "DOCUMENT_UPLOAD_URL_MISSING",
                    message = "Máy chủ không trả về đường dẫn tải tệp đính kèm."
                )
            }
            val headersMap = linkedMapOf<String, String>()
            val headersJson = uploadObj.optJSONObject("headers")
            if (headersJson != null) {
                val keys = headersJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = headersJson.optString(key, "")
                    if (key.isNotBlank() && value.isNotEmpty()) {
                        headersMap[key] = value
                    }
                }
            }
            httpClient.putBinaryToSignedUrl(
                signedUrl = signedUrl,
                headers = headersMap,
                bytes = fileBytes,
                fallbackMimeType = mimeType
            )
        } else if (uploadStatus == "READY") {
            return@withContext reservedEntryVersion
        }

        val finalizeBody = JSONObject().put("expected_entry_version", reservedEntryVersion)
        val finalizeJson = httpClient.postJson(
            path = "/api/direct-entry/entries/$cleanEntryId/documents/$documentId/finalize",
            bodyJson = finalizeBody,
            idempotencyKey = "$idempotencyBaseKey:finalize",
            requireOkTrue = true
        )
        val finalizedVersion = finalizeJson.optInt("entry_version", 0)
        val finalStatus = finalizeJson.optString("upload_status", "").trim()
        if (finalizedVersion < 1 || finalStatus != "READY") {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "DOCUMENT_FINALIZE_INVALID",
                message = "Xác nhận hoàn tất tải tài liệu trên máy chủ không hợp lệ."
            )
        }
        finalizedVersion
    }

    suspend fun submitFullProfileBatch(
        drafts: List<NewWorkerEntryDraft>
    ): FullProfileBatchResult = withContext(Dispatchers.IO) {
        val payload = DirectEntryContract.buildFullProfileV12Payload(drafts)
        val intentSignature = "full_profile_v1_2:${payload}"
        val idempotencyKey = idempotencyStore.resolve(intentSignature)
        try {
            val responseJson = httpClient.postJson(
                path = "/api/direct-entry/batches/full-profile",
                bodyJson = payload,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(intentSignature)
            DirectEntryContract.parseFullProfileBatchResponse(responseJson)
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
    }

    suspend fun listWorkers(
        scope: String,
        pageSize: Int = 25,
        cursor: String? = null,
        search: String? = null,
        projectId: String? = null,
        recruiterId: String? = null,
        employmentStatus: String? = null
    ): WorkerDirectoryPageResult = withContext(Dispatchers.IO) {
        if (scope !in DirectEntryContract.ALLOWED_WORKER_SCOPES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "WORKER_SCOPE_INVALID",
                message = "Phạm vi danh sách người lao động không hợp lệ: $scope"
            )
        }
        val urlBuilder = "https://placeholder.local/api/direct-entry/workers".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("scope", scope)
            .addQueryParameter("page_size", pageSize.coerceIn(1, 100).toString())

        cursor?.trim()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("cursor", it) }
        search?.trim()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("search", it) }
        projectId?.trim()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("project_id", it) }
        recruiterId?.trim()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("recruiter_id", it) }
        employmentStatus?.trim()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("employment_status", it) }

        val built = urlBuilder.build()
        val pathWithQuery = built.encodedPath + (built.encodedQuery?.let { "?$it" } ?: "")

        val json = httpClient.getJson(pathWithQuery = pathWithQuery, requireOkTrue = true)
        val page = DirectEntryContract.parseWorkerDirectoryPage(json, requestedScope = scope)

        // Update Room read-cache when loading initial unfiltered page
        if (cursor == null && search.isNullOrBlank() && projectId.isNullOrBlank() && recruiterId.isNullOrBlank() && employmentStatus.isNullOrBlank()) {
            dao.clearCachedWorkersByScope(scope)
            if (page.items.isNotEmpty()) {
                dao.insertCachedWorkers(page.items)
            }
        }
        page
    }

    suspend fun listOwnSubmissions(
        pageSize: Int = 20,
        cursor: String? = null
    ): OwnSubmissionsPageResult = withContext(Dispatchers.IO) {
        val urlBuilder = "https://placeholder.local/api/direct-entry/submissions".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page_size", pageSize.coerceIn(1, 100).toString())
        cursor?.trim()?.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("cursor", it) }
        val built = urlBuilder.build()
        val pathWithQuery = built.encodedPath + (built.encodedQuery?.let { "?$it" } ?: "")

        val json = httpClient.getJson(pathWithQuery = pathWithQuery, requireOkTrue = true)
        DirectEntryContract.parseOwnSubmissionsPage(json)
    }

    suspend fun preflightDuplicateCccd(
        submissionId: String
    ): DuplicateCccdPreflightResult = withContext(Dispatchers.IO) {
        val cleanId = submissionId.trim()
        if (!DirectEntryContract.isUuid(cleanId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "SUBMISSION_ID_INVALID",
                message = "Mã đợt nhập liệu không hợp lệ."
            )
        }
        val json = httpClient.getJson(
            pathWithQuery = "/api/direct-entry/submissions/$cleanId/duplicate-cccd-preflight",
            requireOkTrue = true
        )
        DirectEntryContract.parseDuplicateCccdPreflightResponse(json, expectedSubmissionId = cleanId)
    }

    suspend fun transitionSubmissionToSubmitted(
        submissionId: String,
        expectedVersion: Int,
        duplicateCccdFingerprint: String? = null,
        duplicateCccdCount: Int? = null
    ): SubmissionTransitionResult = withContext(Dispatchers.IO) {
        val cleanId = submissionId.trim()
        if (!DirectEntryContract.isUuid(cleanId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "SUBMISSION_ID_INVALID",
                message = "Mã đợt nhập liệu không hợp lệ."
            )
        }
        val ackTag = if (duplicateCccdFingerprint != null && duplicateCccdCount != null) {
            ":ack:${duplicateCccdFingerprint.trim()}:$duplicateCccdCount"
        } else {
            ""
        }
        val intentSignature = "submission_transition:$cleanId:SUBMITTED:v$expectedVersion$ackTag"
        val idempotencyKey = idempotencyStore.resolve(intentSignature)
        val payload = DirectEntryContract.buildSubmissionTransitionPayload(
            expectedVersion = expectedVersion,
            targetState = "SUBMITTED",
            idempotencyKey = idempotencyKey,
            duplicateCccdFingerprint = duplicateCccdFingerprint,
            duplicateCccdCount = duplicateCccdCount
        )
        try {
            val responseJson = httpClient.postJson(
                path = "/api/direct-entry/submissions/$cleanId/transition",
                bodyJson = payload,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(intentSignature)
            DirectEntryContract.parseSubmissionTransitionResponse(responseJson, expectedSubmissionId = cleanId)
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
    }

    suspend fun getEntryDetail(entryId: String): WorkerEntryDetail = withContext(Dispatchers.IO) {
        val cleanId = entryId.trim()
        val json = httpClient.getJson(
            pathWithQuery = "/api/direct-entry/entries/$cleanId",
            requireOkTrue = true
        )
        DirectEntryContract.parseEntryDetailResponse(json)
    }

    suspend fun createChangeRequest(
        entryId: String,
        targetKind: String,
        expectedVersion: Int,
        proposal: JSONObject,
        reason: String?
    ): ChangeRequestCreatedSummary = withContext(Dispatchers.IO) {
        val intentSignature = "change_request:$entryId:$targetKind:$expectedVersion:$proposal:${reason.orEmpty().trim()}"
        val idempotencyKey = idempotencyStore.resolve(intentSignature)
        val payload = DirectEntryContract.buildChangeRequestPayload(
            entryId = entryId,
            targetKind = targetKind,
            expectedVersion = expectedVersion,
            proposal = proposal,
            reason = reason,
            idempotencyKey = idempotencyKey
        )
        try {
            val json = httpClient.postJson(
                path = "/api/direct-entry/change-requests",
                bodyJson = payload,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(intentSignature)
            DirectEntryContract.parseChangeRequestCreatedResponse(json)
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
    }

    suspend fun privilegedEditEntry(
        entryId: String,
        expectedEntryVersion: Int,
        patch: JSONObject,
        reason: String?,
        baselineFallback: WorkerEntryDetail? = null
    ): WorkerEntryDetail = withContext(Dispatchers.IO) {
        val cleanId = entryId.trim()
        val payload = DirectEntryContract.buildPrivilegedEditPayload(
            expectedEntryVersion = expectedEntryVersion,
            patch = patch,
            reason = reason
        )
        val intentSignature = "privileged_edit:$cleanId:$payload"
        val idempotencyKey = idempotencyStore.resolve(intentSignature)
        try {
            val res = httpClient.postJson(
                path = "/api/direct-entry/entries/$cleanId/privileged-edit",
                bodyJson = payload,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            val returnedEntryId = res.optString("entry_id", "").trim()
            val newVersion = res.optInt("version", 0)
            if (returnedEntryId != cleanId || newVersion < 1) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "PRIVILEGED_EDIT_RESPONSE_INVALID",
                    message = "Phản hồi sửa trực tiếp từ máy chủ không hợp lệ."
                )
            }
            idempotencyStore.clear(intentSignature)
            // Always refetch authoritative entry detail from backend after mutation; fall back to local patch application if refetch fails
            runCatching { getEntryDetail(cleanId) }.getOrElse { err ->
                if (baselineFallback != null) {
                    DirectEntryContract.applyDirectEditPatchToDetail(baselineFallback, patch, newVersion)
                } else {
                    throw err
                }
            }
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
    }

    /**
     * Direct Edit single-document upload matching Web `uploadDirectEditDocument` in `workers.js`:
     * - Reserve -> PUT to presigned URL -> Finalize
     * - Increments `entryVersion` by 1 if finalize returns HTTP 422
     * - Never logs file names, presigned URLs, storage keys, CCCD, or bank account numbers.
     */
    suspend fun uploadDirectEditDocument(
        entryId: String,
        expectedEntryVersion: Int,
        documentType: String,
        fileBytes: ByteArray,
        mimeType: String,
        reason: String? = null,
        idempotencyBaseKey: String = UUID.randomUUID().toString()
    ): DirectEditSingleDocumentUploadResult = withContext(Dispatchers.IO) {
        val currentEntryVersion = expectedEntryVersion.coerceAtLeast(1)
        val label = DirectEntryContract.directEditDocumentLabel(documentType)
        val cleanEntryId = entryId.trim()
        if (!DirectEntryContract.isUuid(cleanEntryId)) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = currentEntryVersion,
                message = "$label: Mã hồ sơ người lao động không hợp lệ.",
                code = "ENTRY_ID_INVALID"
            )
        }
        if (fileBytes.isEmpty()) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = currentEntryVersion,
                message = "$label: Tệp rỗng hoặc không đọc được.",
                code = "DOCUMENT_SIZE_INVALID"
            )
        }
        if (fileBytes.size > DirectEntryContract.MAX_STAGE_BYTES) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = currentEntryVersion,
                message = "$label: Tệp vượt quá giới hạn 10 MB.",
                code = "DOCUMENT_SIZE_INVALID"
            )
        }
        val cleanMime = mimeType.trim().lowercase(Locale.ROOT)
        val allowedMimes = if (documentType == "WORKER_PORTRAIT") {
            setOf("image/jpeg", "image/png")
        } else {
            setOf("image/jpeg", "image/png", "application/pdf")
        }
        if (cleanMime !in allowedMimes) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = currentEntryVersion,
                message = if (documentType == "WORKER_PORTRAIT") {
                    "$label: Chỉ chấp nhận JPEG hoặc PNG."
                } else {
                    "$label: Chỉ chấp nhận JPEG, PNG hoặc PDF."
                },
                code = "DOCUMENT_MIME_INVALID"
            )
        }

        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        val reserveBody = JSONObject()
            .put("document_type", documentType)
            .put("expected_entry_version", currentEntryVersion)
            .put("size_bytes", fileBytes.size)
            .put("mime_type", cleanMime)
            .put("reason", cleanReason ?: JSONObject.NULL)

        val reserveJson = try {
            httpClient.postJson(
                path = "/api/direct-entry/entries/$cleanEntryId/documents",
                bodyJson = reserveBody,
                idempotencyKey = "$idempotencyBaseKey:reserve",
                requireOkTrue = true
            )
        } catch (e: BackendApiException) {
            val code = e.backendCode?.trim().orEmpty()
            val msg = if (code.isNotEmpty()) {
                DirectEntryContract.directEditDocumentErrorMessage(documentType, code)
            } else if (e.kind == BackendFailureKind.NETWORK) {
                "$label: Không kết nối được máy chủ khi khởi tạo tải tệp."
            } else {
                DirectEntryContract.directEditDocumentErrorMessage(documentType, null)
            }
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = currentEntryVersion,
                message = msg,
                code = code.ifEmpty { null }
            )
        } catch (_: Throwable) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = currentEntryVersion,
                message = "$label: Không kết nối được máy chủ khi khởi tạo tải tệp."
            )
        }

        val documentId = reserveJson.optString("document_id", "").trim()
        val reservedEntryVersion = reserveJson.optInt("entry_version", currentEntryVersion).takeIf { it >= 1 }
            ?: currentEntryVersion
        val uploadObj = if (reserveJson.has("upload") && !reserveJson.isNull("upload")) {
            reserveJson.optJSONObject("upload")
        } else {
            null
        }
        val signedUrl = uploadObj?.optString("url", "")?.trim().orEmpty()
        if (!DirectEntryContract.isUuid(documentId) || signedUrl.isEmpty()) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = reservedEntryVersion,
                message = "$label: Máy chủ không trả về phiên tải tệp hợp lệ.",
                code = "DOCUMENT_RESERVE_INVALID"
            )
        }

        val headersMap = linkedMapOf<String, String>()
        val headersJson = uploadObj?.optJSONObject("headers")
        if (headersJson != null) {
            val keys = headersJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = headersJson.optString(key, "")
                if (key.isNotBlank() && value.isNotEmpty()) {
                    headersMap[key] = value
                }
            }
        }

        try {
            httpClient.putBinaryToSignedUrl(
                signedUrl = signedUrl,
                headers = headersMap,
                bytes = fileBytes,
                fallbackMimeType = cleanMime
            )
        } catch (e: BackendApiException) {
            val msg = if (e.kind == BackendFailureKind.NETWORK) {
                "$label: Tải tệp lên kho lưu trữ thất bại. Vui lòng bấm Thử lại tệp này."
            } else {
                "$label: Kho lưu trữ từ chối tệp tải lên. Vui lòng bấm Thử lại tệp này."
            }
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = reservedEntryVersion,
                message = msg,
                code = e.backendCode
            )
        } catch (_: Throwable) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = reservedEntryVersion,
                message = "$label: Tải tệp lên kho lưu trữ thất bại. Vui lòng bấm Thử lại tệp này."
            )
        }

        val finalizeBody = JSONObject().put("expected_entry_version", reservedEntryVersion)
        val finalizeJson = try {
            httpClient.postJson(
                path = "/api/direct-entry/entries/$cleanEntryId/documents/$documentId/finalize",
                bodyJson = finalizeBody,
                idempotencyKey = "$idempotencyBaseKey:finalize",
                requireOkTrue = true
            )
        } catch (e: BackendApiException) {
            val code = e.backendCode?.trim().orEmpty()
            val nextEntryVersion = if (e.statusCode == 422) reservedEntryVersion + 1 else reservedEntryVersion
            val msg = if (code.isNotEmpty()) {
                DirectEntryContract.directEditDocumentErrorMessage(documentType, code)
            } else if (e.kind == BackendFailureKind.NETWORK) {
                "$label: Không xác nhận được tệp sau khi tải lên. Vui lòng bấm Thử lại tệp này."
            } else {
                DirectEntryContract.directEditDocumentErrorMessage(documentType, null)
            }
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = nextEntryVersion,
                message = msg,
                code = code.ifEmpty { null }
            )
        } catch (_: Throwable) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = reservedEntryVersion,
                message = "$label: Không xác nhận được tệp sau khi tải lên. Vui lòng bấm Thử lại tệp này."
            )
        }

        val finalizedEntryVersion = finalizeJson.optInt("entry_version", reservedEntryVersion).takeIf { it >= 1 }
            ?: reservedEntryVersion
        val finalizedDocVersion = finalizeJson.optInt("version", 1).coerceAtLeast(1)
        DirectEditSingleDocumentUploadResult(
            ok = true,
            entryVersion = finalizedEntryVersion,
            documentVersion = finalizedDocVersion,
            documentId = documentId
        )
    }

    suspend fun uploadDirectEditDocument(
        entryId: String,
        expectedEntryVersion: Int,
        documentType: String,
        stagedRef: StagedDocumentRef,
        reason: String? = null
    ): DirectEditSingleDocumentUploadResult = withContext(Dispatchers.IO) {
        val label = DirectEntryContract.directEditDocumentLabel(documentType)
        val path = stagedRef.localFilePath?.trim().orEmpty()
        if (path.isEmpty()) {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = expectedEntryVersion.coerceAtLeast(1),
                message = "$label: Tệp rỗng hoặc không đọc được.",
                code = "FILE_READ_FAILED"
            )
        }
        val fileBytes = runCatching { File(path).readBytes() }.getOrElse {
            return@withContext DirectEditSingleDocumentUploadResult(
                ok = false,
                entryVersion = expectedEntryVersion.coerceAtLeast(1),
                message = "$label: Tệp rỗng hoặc không đọc được.",
                code = "FILE_READ_FAILED"
            )
        }
        uploadDirectEditDocument(
            entryId = entryId,
            expectedEntryVersion = expectedEntryVersion,
            documentType = documentType,
            fileBytes = fileBytes,
            mimeType = stagedRef.mimeType,
            reason = reason
        )
    }

    suspend fun exportWorkersExcel(
        scope: String,
        projectId: String? = null,
        recruiterId: String? = null,
        employmentStatus: String? = null,
        reason: String? = null
    ): File = withContext(Dispatchers.IO) {
        if (scope !in DirectEntryContract.ALLOWED_WORKER_SCOPES) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "WORKER_EXPORT_SCOPE_INVALID",
                message = "Chỉ hỗ trợ xuất Excel cho danh sách người lao động."
            )
        }
        val body = JSONObject().apply {
            put("scope", scope)
            put("project_id", projectId?.trim()?.ifEmpty { null } ?: JSONObject.NULL)
            put("recruiter_id", recruiterId?.trim()?.ifEmpty { null } ?: JSONObject.NULL)
            put("employment_status", employmentStatus?.trim()?.ifEmpty { null } ?: JSONObject.NULL)
            put("reason", reason?.trim()?.ifEmpty { null } ?: JSONObject.NULL)
        }
        val (bytes, _) = httpClient.postForBinary(
            path = "/api/direct-entry/workers/export",
            bodyJson = body
        )
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "workers-${scope}-${DirectEntryContract.todayIsoInHcm()}.xlsx")
        outFile.writeBytes(bytes)
        outFile
    }

    suspend fun downloadEntryDocument(
        downloadPath: String,
        fallbackFileName: String
    ): File = withContext(Dispatchers.IO) {
        val (bytes, contentType) = httpClient.getBinary(downloadPath)
        val ext = when {
            contentType?.contains("pdf") == true -> ".pdf"
            contentType?.contains("png") == true -> ".png"
            contentType?.contains("jpeg") == true || contentType?.contains("jpg") == true -> ".jpg"
            else -> ""
        }
        val safeName = fallbackFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document" }
        val finalName = if (ext.isNotEmpty() && !safeName.lowercase(Locale.ROOT).endsWith(ext)) {
            "$safeName$ext"
        } else {
            safeName
        }
        val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val outFile = File(downloadDir, finalName)
        outFile.writeBytes(bytes)
        return@withContext outFile
    }

    suspend fun fetchCccdReviewQueue(
        statusFilter: String = "NEEDS_REVIEW",
        limit: Int = 25,
        offset: Int = 0
    ): CccdReviewQueuePage = withContext(Dispatchers.IO) {
        val normalizedStatus = statusFilter.trim().uppercase(Locale.ROOT).let {
            if (it in DirectEntryContract.ALLOWED_CCCD_REVIEW_STATUS_FILTERS) it else "NEEDS_REVIEW"
        }
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val pathWithQuery = "/api/direct-entry/cccd-review?status=$normalizedStatus&limit=$safeLimit&offset=$safeOffset"
        val json = httpClient.getJson(pathWithQuery = pathWithQuery, requireOkTrue = true)
        DirectEntryContract.parseCccdReviewQueueResponse(json)
    }

    suspend fun resolveCccdReviewUpdateEnteredCccd(
        entryId: String,
        expectedEntryVersion: Int,
        nationalId: String,
        applyBlankFieldsFromQr: Boolean = true,
        reason: String? = null
    ): CccdReviewResolveResult = withContext(Dispatchers.IO) {
        val cleanEntryId = entryId.trim()
        if (!DirectEntryContract.isUuid(cleanEntryId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "ENTRY_ID_INVALID",
                message = "Mã hồ sơ người lao động không hợp lệ."
            )
        }
        val payload = DirectEntryContract.buildCccdReviewUpdateEnteredCccdPayload(
            expectedEntryVersion = expectedEntryVersion,
            nationalId = nationalId,
            applyBlankFieldsFromQr = applyBlankFieldsFromQr,
            reason = reason
        )
        val intentSignature = "cccd_review_update:$cleanEntryId:$payload"
        val idempotencyKey = idempotencyStore.resolve(intentSignature)
        try {
            val json = httpClient.postJson(
                path = "/api/direct-entry/entries/$cleanEntryId/cccd-review",
                bodyJson = payload,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(intentSignature)
            DirectEntryContract.parseCccdReviewResolveResponse(json, cleanEntryId)
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
    }

    suspend fun resolveCccdReviewStandardizeFromQr(
        entryId: String,
        expectedEntryVersion: Int,
        fields: List<String>,
        reason: String? = null
    ): CccdReviewResolveResult = withContext(Dispatchers.IO) {
        val cleanEntryId = entryId.trim()
        if (!DirectEntryContract.isUuid(cleanEntryId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "ENTRY_ID_INVALID",
                message = "Mã hồ sơ người lao động không hợp lệ."
            )
        }
        val payload = DirectEntryContract.buildCccdReviewStandardizeFromQrPayload(
            expectedEntryVersion = expectedEntryVersion,
            fields = fields,
            reason = reason
        )
        val intentSignature = "cccd_review_standardize:$cleanEntryId:$payload"
        val idempotencyKey = idempotencyStore.resolve(intentSignature)
        try {
            val json = httpClient.postJson(
                path = "/api/direct-entry/entries/$cleanEntryId/cccd-review",
                bodyJson = payload,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(intentSignature)
            DirectEntryContract.parseCccdReviewResolveResponse(json, cleanEntryId)
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
    }

    suspend fun retryCccdQrExtraction(
        entryId: String,
        expectedEntryVersion: Int,
        applyBlankFields: Boolean = true
    ): CccdQrRetryResult = withContext(Dispatchers.IO) {
        val cleanEntryId = entryId.trim()
        if (!DirectEntryContract.isUuid(cleanEntryId)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "ENTRY_ID_INVALID",
                message = "Mã hồ sơ người lao động không hợp lệ."
            )
        }
        val body = JSONObject()
            .put("apply_blank_fields", applyBlankFields)
            .put("expected_entry_version", expectedEntryVersion.coerceAtLeast(1))
        val json = httpClient.postJson(
            path = "/api/direct-entry/entries/$cleanEntryId/cccd-qr",
            bodyJson = body,
            idempotencyKey = UUID.randomUUID().toString(),
            requireOkTrue = true
        )
        DirectEntryContract.parseCccdQrRetryResponse(json, cleanEntryId)
    }

    suspend fun reuploadCccdDocumentAndRetryQr(
        entryId: String,
        documentType: String,
        uri: Uri,
        initialEntryVersion: Int
    ): CccdQrRetryResult = withContext(Dispatchers.IO) {
        if (documentType != "CCCD_FRONT" && documentType != "CCCD_BACK") {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "DOCUMENT_TYPE_INVALID",
                message = "Chỉ hỗ trợ tải lại ảnh CCCD mặt trước hoặc mặt sau."
            )
        }
        val prepared = prepareLocalAttachmentUri(documentType, uri)
        try {
            val localFile = File(prepared.localFilePath ?: "")
            val fileBytes = localFile.readBytes()
            val updatedVersion = uploadDocumentToEntry(
                entryId = entryId,
                initialEntryVersion = initialEntryVersion,
                documentType = documentType,
                fileBytes = fileBytes,
                mimeType = prepared.mimeType,
                idempotencyBaseKey = UUID.randomUUID().toString()
            )
            retryCccdQrExtraction(
                entryId = entryId,
                expectedEntryVersion = updatedVersion,
                applyBlankFields = true
            )
        } finally {
            deleteLocalAttachment(prepared.localFilePath)
        }
    }
}
