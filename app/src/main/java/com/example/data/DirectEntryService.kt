package com.example.data

import android.content.Context
import android.net.Uri
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

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )

    private val ISO_DATE_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

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
            "Khác", "OTHER" -> "OTHER"
            else -> throw invalid("GENDER_VOCABULARY_INVALID", "Giới tính không hợp lệ: $trimmed")
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

    fun optionalTextField(raw: String): JSONObject {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) {
            JSONObject().put("state", "omitted")
        } else {
            JSONObject()
                .put("state", "provided")
                .put("value", trimmed)
        }
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
        return JSONObject().apply {
            put("state", "provided")
            if (acc.isNotEmpty()) put("account_number", acc)
            if (bank.isNotEmpty()) put("bank_name", bank)
            if (holder.isNotEmpty()) put("account_holder_name", holder)
        }
    }

    fun buildFullProfileV11Payload(drafts: List<NewWorkerEntryDraft>): JSONObject {
        if (drafts.isEmpty()) {
            throw invalid("BATCH_SIZE_INVALID", "Cần ít nhất 1 hồ sơ hợp lệ để gửi lên máy chủ.")
        }
        val rowsArray = JSONArray()
        for (draft in drafts) {
            val projectId = draft.projectId.trim()
            val recruiterId = draft.recruiterId.trim()
            val laborType = draft.laborTypeKey.trim()
            val displayName = draft.fullName.trim()

            if (projectId.isEmpty() || !UUID_REGEX.matches(recruiterId) || laborType.isEmpty() || displayName.length < 2) {
                throw invalid("BATCH_INVALID", "Vui lòng chọn đầy đủ Dự án, Người tuyển, Loại hình LĐ từ danh mục và nhập Họ tên.")
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
                        put("date_of_birth", optionalDateField(draft.birthDateUi))
                        put("national_id", optionalTextField(draft.cccd))
                        put("national_id_issued_at", optionalDateField(draft.cccdIssueDateUi))
                        put("national_id_issued_place", optionalTextField(draft.cccdIssuePlace))
                        put("address", optionalTextField(draft.address))
                        put("phone", optionalTextField(draft.phone))
                    }
                )
                put("general_note", optionalTextField(draft.note))
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
            .put("contract_version", CONTRACT_VERSION_V1_1)
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
        if (idempotencyKey.isBlank()) {
            throw invalid("CHANGE_REQUEST_INVALID", "Thiếu khóa chống trùng lặp (idempotency_key).")
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
        leaveReason: String?
    ): JSONObject {
        if (status !in setOf("ON", "UNCONFIRMED", "OFF")) {
            throw invalid("STATUS_INVALID", "Trạng thái làm việc không hợp lệ.")
        }
        val isoDate = uiDateToIsoDate(effectiveDateUiOrIso)
        return if (status == "OFF") {
            val reasonText = leaveReason?.trim().orEmpty()
            if (reasonText.isEmpty()) {
                throw invalid("STATUS_REASON_REQUIRED", "Trạng thái Đã nghỉ bắt buộc nhập lý do nghỉ.")
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
        val normalizedReason = reason?.trim()?.ifEmpty { null }
        return JSONObject()
            .put("expected_entry_version", expectedEntryVersion)
            .put("patch", patch)
            .put("reason", normalizedReason ?: JSONObject.NULL)
    }

    fun buildPrivilegedEditEmploymentStatus(
        status: String,
        effectiveDateUiOrIso: String,
        leaveReason: String?
    ): JSONObject {
        if (status !in setOf("ON", "UNCONFIRMED", "OFF")) {
            throw invalid("PRIVILEGED_EDIT_INVALID", "Trạng thái làm việc không hợp lệ.")
        }
        val isoDate = uiDateToIsoDate(effectiveDateUiOrIso)
        return if (status == "OFF") {
            val reasonText = leaveReason?.trim().orEmpty()
            if (reasonText.isEmpty()) {
                throw invalid("OFF_REQUIRES_DATE_AND_REASON", "Trạng thái Đã nghỉ bắt buộc nhập lý do nghỉ.")
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
                    "Đang chờ duyệt (${pendingObj.optString("state", "PENDING")})"
                } else {
                    ""
                }

                val decisionObj = item.optJSONObject("last_decision")
                val decisionSummary = if (decisionObj != null) {
                    "Quyết định gần nhất: ${decisionObj.optString("state", "")}"
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
        val displayName = workerObj.optString("display_name", "").trim()
        if (displayName.isEmpty()) throw contractInvalid("WORKER_DISPLAY_NAME_MISSING")

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
        val bankName = paymentObj?.optNullableString("bank_name") ?: bankId
        val accountNumber = paymentObj?.optNullableString("account_number")
        val accountHolderName = paymentObj?.optNullableString("account_holder_name")

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
            accountNumber = accountNumber,
            accountHolderName = accountHolderName,
            employmentStatus = statusCode,
            employmentEffectiveDate = effectiveDate,
            employmentStatusVersion = statusVersion,
            employmentLeaveReason = leaveReason,
            portraitPreviewPath = portraitPreviewPath,
            portraitDownloadPath = portraitDownloadPath,
            documents = documents
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

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                total += read
                if (total > DirectEntryContract.MAX_STAGE_BYTES) {
                    throw BackendApiException(
                        kind = BackendFailureKind.INVALID_REQUEST,
                        statusCode = 413,
                        backendCode = "DOCUMENT_TOO_LARGE",
                        message = "Tệp đính kèm vượt quá giới hạn 10 MB."
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

        uploadStageBytes(
            documentType = documentType,
            fileBytes = bytes,
            fileName = displayName,
            mimeType = normalizedMime
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

    suspend fun submitFullProfileBatch(
        drafts: List<NewWorkerEntryDraft>
    ): FullProfileBatchResult = withContext(Dispatchers.IO) {
        val payload = DirectEntryContract.buildFullProfileV11Payload(drafts)
        val intentSignature = "full_profile_v1_1:${payload}"
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
        reason: String?
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
            // Always refetch authoritative entry detail from backend after mutation
            getEntryDetail(cleanId)
        } catch (e: BackendApiException) {
            if (!idempotencyStore.shouldPreserveForRetry(e)) {
                idempotencyStore.clear(intentSignature)
            }
            throw e
        }
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
        outFile
    }
}
