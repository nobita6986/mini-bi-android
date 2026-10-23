package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import kotlin.math.roundToInt

data class DuplicateCccdReportPage(
    val page: Int,
    val pageSize: Int,
    val totalGroups: Int,
    val totalEntries: Int,
    val hasMore: Boolean,
    val authorizationDate: String,
    val groups: List<DuplicateCccdGroupItem>
)

object WorkReportsContract {
    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )
    private val CANONICAL_TEAM_BUCKET_REGEX = Regex("^team\\.[A-Za-z0-9_-]+$")
    private val VALID_SUBMISSION_STATES = setOf(
        "NOT_STARTED",
        "DRAFT",
        "COMPLETED",
        "PENDING_REVIEW",
        "APPROVED",
        "REJECTED"
    )
    private val VALID_OBLIGATION_STATUSES = setOf("ACTIVE", "EXEMPTED", "CANCELLED")

    fun requireUuid(value: String, fieldLabel: String): String {
        val trimmed = value.trim()
        if (!UUID_REGEX.matches(trimmed)) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "UUID_INVALID",
                message = "$fieldLabel không đúng định dạng định danh máy chủ."
            )
        }
        return trimmed
    }

    fun isUuid(value: String): Boolean = UUID_REGEX.matches(value.trim())

    fun encodeTeamBucketKey(rawTeamCodeOrBucketKey: String): String {
        val trimmed = rawTeamCodeOrBucketKey.trim()
        if (trimmed.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "INVALID_FILTER",
                message = "Vui lòng chọn mã nhóm hợp lệ."
            )
        }
        if (trimmed == "unknown" || CANONICAL_TEAM_BUCKET_REGEX.matches(trimmed)) {
            return trimmed
        }
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(trimmed.toByteArray(Charsets.UTF_8))
        return "team.$encoded"
    }

    fun parseDynamicSections(schemaJsonRaw: String): List<WorkReportSectionSpec> {
        if (schemaJsonRaw.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(schemaJsonRaw)
            val sectionsArr = root.optJSONArray("sections") ?: JSONArray()
            val topFieldsArr = root.optJSONArray("fields") ?: JSONArray()

            fun parseField(f: JSONObject, fallbackSectionKey: String, fallbackOrder: Int): WorkReportFieldSpec {
                val optsArr = f.optJSONArray("options")
                val options = mutableListOf<Pair<Int, WorkReportFieldOption>>()
                if (optsArr != null) {
                    for (j in 0 until optsArr.length()) {
                        val o = optsArr.optJSONObject(j) ?: continue
                        if (!o.optBoolean("active", true)) continue
                        val optKey = o.optString("option_key", o.optString("value", "")).trim()
                        if (optKey.isEmpty()) continue
                        val optLabel = o.optString("label", optKey).trim().ifEmpty { optKey }
                        val optSort = o.optInt("sort_order", j)
                        options.add(optSort to WorkReportFieldOption(value = optKey, label = optLabel))
                    }
                }
                val rawDefault = f.opt("default_value")
                val defaultStr = when (rawDefault) {
                    null, JSONObject.NULL -> null
                    is JSONArray -> buildList {
                        for (idx in 0 until rawDefault.length()) {
                            val v = rawDefault.optString(idx, "").trim()
                            if (v.isNotEmpty()) add(v)
                        }
                    }.joinToString(",").ifEmpty { null }
                    else -> rawDefault.toString().ifBlank { null }
                }
                val fKey = f.optString("field_key", f.optString("key", "")).trim()
                return WorkReportFieldSpec(
                    fieldKey = fKey,
                    sectionKey = f.optString("section_key", fallbackSectionKey).ifBlank { fallbackSectionKey },
                    sortOrder = f.optInt("sort_order", fallbackOrder),
                    label = f.optString("label", fKey).ifBlank { fKey },
                    description = f.optNullableText("description"),
                    fieldType = f.optString("field_type", f.optString("type", "text")).ifBlank { "text" },
                    required = f.optBoolean("required", false),
                    defaultValue = defaultStr,
                    options = options.sortedBy { it.first }.map { it.second }
                )
            }

            val topLevelFields = mutableListOf<WorkReportFieldSpec>()
            for (i in 0 until topFieldsArr.length()) {
                val f = topFieldsArr.optJSONObject(i) ?: continue
                val parsed = parseField(f, "default", i)
                if (parsed.fieldKey.isNotEmpty()) topLevelFields.add(parsed)
            }

            if (sectionsArr.length() == 0) {
                if (topLevelFields.isEmpty()) emptyList()
                else listOf(
                    WorkReportSectionSpec(
                        sectionKey = "default",
                        label = "Nội dung báo cáo",
                        description = null,
                        sortOrder = 0,
                        fields = topLevelFields.sortedBy { it.sortOrder }
                    )
                )
            } else {
                val sections = mutableListOf<WorkReportSectionSpec>()
                for (i in 0 until sectionsArr.length()) {
                    val s = sectionsArr.optJSONObject(i) ?: continue
                    val sKey = s.optString("section_key", s.optString("key", "section_$i")).ifBlank { "section_$i" }
                    val nestedFieldsArr = s.optJSONArray("fields")
                    val sectionFields = if (nestedFieldsArr != null && nestedFieldsArr.length() > 0) {
                        buildList {
                            for (k in 0 until nestedFieldsArr.length()) {
                                val nf = nestedFieldsArr.optJSONObject(k) ?: continue
                                val parsed = parseField(nf, sKey, k)
                                if (parsed.fieldKey.isNotEmpty()) add(parsed)
                            }
                        }.sortedBy { it.sortOrder }
                    } else {
                        topLevelFields.filter { it.sectionKey == sKey }.sortedBy { it.sortOrder }
                    }
                    sections.add(
                        WorkReportSectionSpec(
                            sectionKey = sKey,
                            label = s.optString("label", "Mục ${i + 1}").ifBlank { "Mục ${i + 1}" },
                            description = s.optNullableText("description"),
                            sortOrder = s.optInt("sort_order", i),
                            fields = sectionFields
                        )
                    )
                }
                sections.sortedBy { it.sortOrder }
            }
        }.getOrDefault(emptyList())
    }

    fun parseObligationItem(
        obj: JSONObject,
        cachedEntity: WorkReportObligationEntity? = null
    ): WorkReportObligationEntity {
        val obligationId = obj.optString("obligation_id", "").trim()
        val templateId = obj.optString("template_id", "").trim()
        if (!UUID_REGEX.matches(obligationId) || !UUID_REGEX.matches(templateId)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "WORK_REPORT_CONTRACT_INVALID",
                message = "Dữ liệu nghĩa vụ báo cáo từ máy chủ không đúng hợp đồng."
            )
        }
        val obligationStatus = obj.optString("obligation_status", "").trim()
        val submissionState = obj.optString("submission_state", "").trim()
        if (obligationStatus !in VALID_OBLIGATION_STATUSES || submissionState !in VALID_SUBMISSION_STATES) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "WORK_REPORT_STATE_INVALID",
                message = "Trạng thái báo cáo từ máy chủ không hợp lệ."
            )
        }
        val submissionVersion = if (obj.isNull("submission_version") || !obj.has("submission_version")) {
            null
        } else {
            obj.getInt("submission_version")
        }
        val schemaObj = obj.optJSONObject("schema_json") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORK_REPORT_SCHEMA_MISSING",
            message = "Thiếu cấu trúc biểu mẫu báo cáo từ máy chủ."
        )
        val keepCachedDetail = cachedEntity != null && cachedEntity.submissionVersion == submissionVersion
        return WorkReportObligationEntity(
            obligationId = obligationId,
            templateId = templateId,
            templateCode = obj.optString("template_code", ""),
            templateName = obj.optString("template_name", ""),
            periodType = obj.optString("period_type", ""),
            periodKey = obj.optString("period_key", ""),
            periodStartDate = obj.optString("period_start_date", ""),
            periodEndDate = obj.optString("period_end_date", ""),
            dueAtIso = obj.optString("due_at", ""),
            isMandatory = obj.optBoolean("is_mandatory", true),
            obligationStatus = obligationStatus,
            submissionId = obj.optNullableText("submission_id"),
            submissionState = submissionState,
            submissionVersion = submissionVersion,
            obligationVersion = obj.optInt("version", 1),
            schemaJsonRaw = schemaObj.toString(),
            payloadJsonRaw = if (keepCachedDetail) cachedEntity!!.payloadJsonRaw else "{}",
            latestReviewDecision = if (keepCachedDetail) cachedEntity!!.latestReviewDecision else null,
            latestReviewReason = if (keepCachedDetail) cachedEntity!!.latestReviewReason else null,
            submittedAtIso = if (keepCachedDetail) cachedEntity!!.submittedAtIso else null
        )
    }

    fun parseObligationDetailEnvelope(
        envelope: JSONObject,
        fallback: WorkReportObligationEntity? = null
    ): WorkReportObligationEntity {
        val data = envelope.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORK_REPORT_DETAIL_MISSING",
            message = "Phản hồi chi tiết báo cáo thiếu dữ liệu hợp lệ."
        )
        val obligationObj = data.optJSONObject("obligation") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORK_REPORT_OBLIGATION_MISSING",
            message = "Phản hồi chi tiết báo cáo thiếu thông tin nghĩa vụ."
        )
        val templateObj = data.optJSONObject("template") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORK_REPORT_TEMPLATE_MISSING",
            message = "Phản hồi chi tiết báo cáo thiếu cấu trúc mẫu."
        )
        val schemaObj = templateObj.optJSONObject("schema_json") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORK_REPORT_SCHEMA_MISSING",
            message = "Phản hồi chi tiết báo cáo thiếu schema_json."
        )

        val submissionObj = if (data.isNull("submission")) null else data.optJSONObject("submission")
        val revisionsArr = data.optJSONArray("revisions") ?: JSONArray()
        var latestRevisionNo = Int.MIN_VALUE
        var latestRevisionPayload: JSONObject? = null
        for (i in 0 until revisionsArr.length()) {
            val rev = revisionsArr.optJSONObject(i) ?: continue
            val revNo = rev.optInt("revision_no", i)
            val payload = rev.optJSONObject("payload_json")
            if (payload != null && revNo >= latestRevisionNo) {
                latestRevisionNo = revNo
                latestRevisionPayload = payload
            }
        }

        val draftPayload = if (submissionObj != null && !submissionObj.isNull("draft_payload_json")) {
            submissionObj.optJSONObject("draft_payload_json")
        } else {
            null
        }
        val effectivePayload = draftPayload ?: latestRevisionPayload ?: JSONObject()

        val latestReviewObj = if (data.isNull("latest_review_event")) {
            null
        } else {
            data.optJSONObject("latest_review_event")
        }

        val obligationId = obligationObj.optString("obligation_id", fallback?.obligationId.orEmpty()).trim()
        val templateId = obligationObj.optString("template_id", fallback?.templateId.orEmpty()).trim()
        if (!UUID_REGEX.matches(obligationId) || !UUID_REGEX.matches(templateId)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "WORK_REPORT_ID_INVALID",
                message = "Mã nghĩa vụ báo cáo từ máy chủ không hợp lệ."
            )
        }

        val resolvedState = submissionObj?.optString("state", "")?.ifBlank { null }
            ?: obligationObj.optString("submission_state", "").ifBlank { null }
            ?: fallback?.submissionState
            ?: "NOT_STARTED"

        val resolvedVersion = if (submissionObj != null && !submissionObj.isNull("version")) {
            submissionObj.getInt("version")
        } else if (!obligationObj.isNull("submission_version") && obligationObj.has("submission_version")) {
            obligationObj.getInt("submission_version")
        } else {
            null
        }

        return WorkReportObligationEntity(
            obligationId = obligationId,
            templateId = templateId,
            templateCode = templateObj.optString("template_code", fallback?.templateCode.orEmpty()),
            templateName = templateObj.optString("display_name", fallback?.templateName.orEmpty()),
            periodType = obligationObj.optString("period_type", fallback?.periodType.orEmpty()),
            periodKey = obligationObj.optString("period_key", fallback?.periodKey.orEmpty()),
            periodStartDate = obligationObj.optString("period_start_date", fallback?.periodStartDate.orEmpty()),
            periodEndDate = obligationObj.optString("period_end_date", fallback?.periodEndDate.orEmpty()),
            dueAtIso = obligationObj.optString("due_at", fallback?.dueAtIso.orEmpty()),
            isMandatory = obligationObj.optBoolean("is_mandatory", fallback?.isMandatory ?: true),
            obligationStatus = obligationObj.optString("obligation_status", fallback?.obligationStatus ?: "ACTIVE"),
            submissionId = submissionObj?.optNullableText("submission_id") ?: fallback?.submissionId,
            submissionState = resolvedState,
            submissionVersion = resolvedVersion,
            obligationVersion = obligationObj.optInt("version", fallback?.obligationVersion ?: 1),
            schemaJsonRaw = schemaObj.toString(),
            payloadJsonRaw = effectivePayload.toString(),
            latestReviewDecision = latestReviewObj?.optNullableText("decision"),
            latestReviewReason = latestReviewObj?.optNullableText("reason"),
            submittedAtIso = submissionObj?.optNullableText("latest_submitted_at")
                ?: submissionObj?.optNullableText("first_submitted_at")
        )
    }

    fun buildSubmissionMutationBody(
        payloadJson: JSONObject,
        expectedVersion: Int?,
        idempotencyKey: String
    ): JSONObject {
        if (expectedVersion != null && expectedVersion < 1) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "WORK_REPORT_PAYLOAD_INVALID",
                message = "Phiên bản báo cáo (expected_version) không hợp lệ."
            )
        }
        return JSONObject().apply {
            put("payload_json", payloadJson)
            put("expected_version", expectedVersion ?: JSONObject.NULL)
            put("idempotency_key", idempotencyKey.trim())
        }
    }

    fun buildReviewDecisionBody(
        approve: Boolean,
        reason: String?,
        expectedVersion: Int,
        idempotencyKey: String
    ): JSONObject {
        if (expectedVersion < 1) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "WORK_REPORT_REVIEW_INVALID",
                message = "Phiên bản bài nộp cần duyệt không hợp lệ."
            )
        }
        val decision = if (approve) "APPROVED" else "REJECTED"
        val normalizedReason = reason?.trim().orEmpty()
        if (!approve && normalizedReason.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "WORK_REPORT_REVIEW_REASON_REQUIRED",
                message = "Vui lòng nhập lý do khi yêu cầu sửa báo cáo."
            )
        }
        return JSONObject().apply {
            put("decision", decision)
            put("reason", if (normalizedReason.isEmpty()) JSONObject.NULL else normalizedReason)
            put("expected_version", expectedVersion)
            put("idempotency_key", idempotencyKey.trim())
        }
    }

    fun buildPayloadDisplaySummary(
        sections: List<WorkReportSectionSpec>,
        payloadObj: JSONObject?
    ): Map<String, String> {
        if (payloadObj == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val fieldsByKey = sections.flatMap { it.fields }.associateBy { it.fieldKey }
        if (fieldsByKey.isNotEmpty()) {
            fieldsByKey.values.forEach { spec ->
                if (!payloadObj.has(spec.fieldKey) || payloadObj.isNull(spec.fieldKey)) return@forEach
                val raw = payloadObj.opt(spec.fieldKey)
                val formatted = formatFieldValue(spec, raw)
                if (formatted.isNotBlank()) {
                    result[spec.label] = formatted
                }
            }
        } else {
            val keys = payloadObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!payloadObj.isNull(k)) {
                    result[k] = payloadObj.opt(k)?.toString().orEmpty()
                }
            }
        }
        return result
    }

    private fun formatFieldValue(spec: WorkReportFieldSpec, raw: Any?): String {
        if (raw == null || raw == JSONObject.NULL) return ""
        val optionMap = spec.options.associate { it.value to it.label }
        return when (spec.fieldType) {
            "boolean" -> if (raw.toString().equals("true", ignoreCase = true)) "Có" else "Không"
            "single_select" -> {
                val k = raw.toString()
                optionMap[k] ?: k
            }
            "multi_select" -> {
                if (raw is JSONArray) {
                    buildList {
                        for (i in 0 until raw.length()) {
                            val k = raw.optString(i, "")
                            if (k.isNotEmpty()) add(optionMap[k] ?: k)
                        }
                    }.joinToString(", ")
                } else {
                    raw.toString()
                }
            }
            else -> raw.toString()
        }
    }

    fun employmentStatusDisplayLabel(statusCode: String): String = when (statusCode.trim().uppercase(Locale.ROOT)) {
        "ON" -> "Đang làm"
        "UNCONFIRMED" -> "Đang chờ việc"
        "OFF" -> "Đã nghỉ"
        else -> statusCode
    }
}

class WorkReportsAndReportingService(
    private val httpClient: BackendHttpClient,
    private val dao: MiniBiDao,
    private val idempotencyStore: IntentIdempotencyStore = IntentIdempotencyStore()
) {

    // =========================================================================
    // E1. Work Report Obligations & Detail
    // =========================================================================

    suspend fun fetchMyObligations(status: String? = null): List<WorkReportObligationEntity> =
        withContext(Dispatchers.IO) {
            val path = if (status != null) {
                require(status in setOf("ACTIVE", "EXEMPTED", "CANCELLED")) {
                    "Trạng thái lọc nghĩa vụ không hợp lệ."
                }
                "/api/work-reports/obligations?status=${encodeParam(status)}"
            } else {
                "/api/work-reports/obligations"
            }

            val json = httpClient.getJson(pathWithQuery = path, requireOkTrue = true)
            val dataArr = json.optJSONArray("data") ?: throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "WORK_REPORT_LIST_INVALID",
                message = "Phản hồi danh sách nghĩa vụ báo cáo không hợp lệ."
            )

            val existingById = runCatching {
                dao.getCachedWorkReportsSnapshot().associateBy { it.obligationId }
            }.getOrDefault(emptyMap())

            val parsed = buildList {
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.optJSONObject(i) ?: throw BackendApiException(
                        kind = BackendFailureKind.CONTRACT_INVALID,
                        statusCode = 200,
                        backendCode = "WORK_REPORT_ITEM_INVALID",
                        message = " Phần tử nghĩa vụ báo cáo không đúng định dạng."
                    )
                    val id = obj.optString("obligation_id", "")
                    add(WorkReportsContract.parseObligationItem(obj, existingById[id]))
                }
            }

            if (status == null) {
                dao.clearWorkReportsCache()
                if (parsed.isNotEmpty()) {
                    dao.replaceWorkReportsCache(parsed)
                }
            } else if (parsed.isNotEmpty()) {
                dao.replaceWorkReportsCache(parsed)
            }
            parsed
        }

    suspend fun fetchObligationDetail(
        obligationId: String,
        fallback: WorkReportObligationEntity? = null
    ): WorkReportObligationEntity = withContext(Dispatchers.IO) {
        val validId = WorkReportsContract.requireUuid(obligationId, "Mã nghĩa vụ báo cáo")
        val json = httpClient.getJson(
            pathWithQuery = "/api/work-reports/obligations/$validId",
            requireOkTrue = true
        )
        val entity = WorkReportsContract.parseObligationDetailEnvelope(json, fallback)
        dao.upsertWorkReportCache(entity)
        entity
    }

    // =========================================================================
    // E2. Save Draft & Submit (Strict OCC + Idempotency + Canonical Refetch)
    // =========================================================================

    suspend fun saveDraft(
        obligationId: String,
        expectedVersion: Int?,
        payloadJson: JSONObject,
        fallback: WorkReportObligationEntity? = null
    ): WorkReportObligationEntity = writeSubmissionAction(
        action = "draft",
        obligationId = obligationId,
        expectedVersion = expectedVersion,
        payloadJson = payloadJson,
        fallback = fallback
    )

    suspend fun submitReport(
        obligationId: String,
        expectedVersion: Int?,
        payloadJson: JSONObject,
        fallback: WorkReportObligationEntity? = null
    ): WorkReportObligationEntity = writeSubmissionAction(
        action = "submit",
        obligationId = obligationId,
        expectedVersion = expectedVersion,
        payloadJson = payloadJson,
        fallback = fallback
    )

    private suspend fun writeSubmissionAction(
        action: String,
        obligationId: String,
        expectedVersion: Int?,
        payloadJson: JSONObject,
        fallback: WorkReportObligationEntity?
    ): WorkReportObligationEntity = withContext(Dispatchers.IO) {
        val validId = WorkReportsContract.requireUuid(obligationId, "Mã nghĩa vụ báo cáo")
        val fingerprint = "wr:$action:$validId:v=${expectedVersion ?: "null"}:${payloadJson.toString().hashCode()}"
        val idempotencyKey = idempotencyStore.resolve(fingerprint)
        val body = WorkReportsContract.buildSubmissionMutationBody(
            payloadJson = payloadJson,
            expectedVersion = expectedVersion,
            idempotencyKey = idempotencyKey
        )

        try {
            httpClient.postJson(
                path = "/api/work-reports/submissions/$validId/$action",
                bodyJson = body,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(fingerprint)
            // Canonical authority comes strictly from server refetch — never fabricate state locally
            val refreshedDetail = fetchObligationDetail(validId, fallback)
            runCatching { fetchMyObligations(status = null) }
            dao.upsertWorkReportCache(refreshedDetail)
            refreshedDetail
        } catch (e: BackendApiException) {
            if (!isRetryableFailure(e.kind)) {
                idempotencyStore.clear(fingerprint)
            }
            throw e
        }
    }

    // =========================================================================
    // E3. Work Report Reviews
    // =========================================================================

    suspend fun fetchPendingReviews(
        templateId: String? = null,
        cursor: String? = null,
        pageSize: Int = 50
    ): List<WorkReportReviewItem> = withContext(Dispatchers.IO) {
        val safeSize = pageSize.coerceIn(1, 100)
        val queryParts = mutableListOf("page_size=$safeSize")
        if (!templateId.isNullOrBlank()) {
            val validTemplateId = WorkReportsContract.requireUuid(templateId, "Mã mẫu báo cáo")
            queryParts.add("template_id=${encodeParam(validTemplateId)}")
        }
        if (!cursor.isNullOrBlank()) {
            queryParts.add("cursor=${encodeParam(cursor.trim())}")
        }

        val json = httpClient.getJson(
            pathWithQuery = "/api/work-reports/reviews?${queryParts.joinToString("&")}",
            requireOkTrue = true
        )
        val itemsArr = json.optJSONArray("items")
            ?: json.optJSONObject("data")?.optJSONArray("items")
            ?: throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "WORK_REPORT_REVIEW_LIST_INVALID",
                message = "Phản hồi danh sách chờ duyệt không đúng định dạng."
            )

        val baseItems = mutableListOf<WorkReportReviewItem>()
        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.optJSONObject(i) ?: continue
            val obligationId = obj.optString("obligation_id", "").trim()
            val submissionId = obj.optString("submission_id", "").trim()
            if (!WorkReportsContract.isUuid(obligationId) || !WorkReportsContract.isUuid(submissionId)) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "WORK_REPORT_REVIEW_ITEM_INVALID",
                    message = "Phần tử chờ duyệt thiếu định danh hợp lệ."
                )
            }
            baseItems.add(
                WorkReportReviewItem(
                    obligationId = obligationId,
                    submissionId = submissionId,
                    submissionVersion = obj.optInt("submission_version", 1),
                    submissionState = "PENDING_REVIEW",
                    templateId = obj.optString("template_id", ""),
                    templateName = obj.optString("template_name", ""),
                    periodKey = obj.optString("period_key", ""),
                    assigneeUserId = obj.optString("assignee_app_user_id", ""),
                    assigneeDisplayName = obj.optString("assignee_display_name", ""),
                    teamDisplayName = obj.optNullableText("team_display_name"),
                    submittedAt = obj.optNullableText("submitted_at"),
                    payloadSummary = emptyMap(),
                    sections = emptyList()
                )
            )
        }

        // Enrich visible pending review items with canonical submission payload via GET /api/work-reports/reviews/{obligationId}
        baseItems.mapIndexed { index, base ->
            if (index < 15) {
                runCatching { fetchReviewSubmissionDetail(base.obligationId, base) }.getOrDefault(base)
            } else {
                base
            }
        }
    }

    suspend fun fetchReviewSubmissionDetail(
        obligationId: String,
        fallback: WorkReportReviewItem? = null
    ): WorkReportReviewItem = withContext(Dispatchers.IO) {
        val validId = WorkReportsContract.requireUuid(obligationId, "Mã nghĩa vụ duyệt")
        val json = httpClient.getJson(
            pathWithQuery = "/api/work-reports/reviews/$validId",
            requireOkTrue = true
        )
        val detail = json.optJSONObject("detail")
            ?: json.optJSONObject("data")
            ?: throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "WORK_REPORT_REVIEW_DETAIL_INVALID",
                message = "Phản hồi chi tiết duyệt báo cáo không đúng hợp đồng."
            )

        val obligationObj = detail.optJSONObject("obligation") ?: JSONObject()
        val templateObj = detail.optJSONObject("template") ?: JSONObject()
        val submissionObj = detail.optJSONObject("submission") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORK_REPORT_REVIEW_SUBMISSION_MISSING",
            message = "Bài nộp cần duyệt không tồn tại trên máy chủ."
        )
        val schemaRaw = templateObj.optJSONObject("schema_json")?.toString().orEmpty()
        val sections = WorkReportsContract.parseDynamicSections(schemaRaw)

        val revisionsArr = detail.optJSONArray("revisions") ?: JSONArray()
        var latestRevNo = Int.MIN_VALUE
        var latestPayload: JSONObject? = null
        for (i in 0 until revisionsArr.length()) {
            val rev = revisionsArr.optJSONObject(i) ?: continue
            val revNo = rev.optInt("revision_no", i)
            val p = rev.optJSONObject("payload_json")
            if (p != null && revNo >= latestRevNo) {
                latestRevNo = revNo
                latestPayload = p
            }
        }
        val activePayload = latestPayload ?: submissionObj.optJSONObject("draft_payload_json")
        val summaryMap = WorkReportsContract.buildPayloadDisplaySummary(sections, activePayload)

        WorkReportReviewItem(
            obligationId = obligationObj.optString("obligation_id", fallback?.obligationId ?: validId),
            submissionId = submissionObj.optString("submission_id", fallback?.submissionId.orEmpty()),
            submissionVersion = submissionObj.optInt("version", fallback?.submissionVersion ?: 1),
            submissionState = submissionObj.optString("state", fallback?.submissionState ?: "PENDING_REVIEW"),
            templateId = obligationObj.optString("template_id", fallback?.templateId.orEmpty()),
            templateName = templateObj.optString("display_name", fallback?.templateName.orEmpty()),
            periodKey = obligationObj.optString("period_key", fallback?.periodKey.orEmpty()),
            assigneeUserId = obligationObj.optString("assignee_app_user_id", fallback?.assigneeUserId.orEmpty()),
            assigneeDisplayName = obligationObj.optString("assignee_display_name", fallback?.assigneeDisplayName.orEmpty()),
            teamDisplayName = obligationObj.optNullableText("team_display_name") ?: fallback?.teamDisplayName,
            submittedAt = submissionObj.optNullableText("latest_submitted_at")
                ?: submissionObj.optNullableText("first_submitted_at")
                ?: fallback?.submittedAt,
            payloadSummary = summaryMap,
            sections = sections
        )
    }

    suspend fun decideReview(
        obligationId: String,
        approve: Boolean,
        reason: String?,
        expectedVersion: Int
    ) = withContext(Dispatchers.IO) {
        val validId = WorkReportsContract.requireUuid(obligationId, "Mã nghĩa vụ duyệt")
        val fingerprint = "wr:review:$validId:$approve:v=$expectedVersion:${reason?.trim().orEmpty().hashCode()}"
        val idempotencyKey = idempotencyStore.resolve(fingerprint)
        val body = WorkReportsContract.buildReviewDecisionBody(
            approve = approve,
            reason = reason,
            expectedVersion = expectedVersion,
            idempotencyKey = idempotencyKey
        )

        try {
            httpClient.postJson(
                path = "/api/work-reports/reviews/$validId/decision",
                bodyJson = body,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(fingerprint)
        } catch (e: BackendApiException) {
            if (!isRetryableFailure(e.kind)) {
                idempotencyStore.clear(fingerprint)
            }
            throw e
        }
    }

    // =========================================================================
    // E4. Work Report Analytics (overview + breakdown + contributions)
    // =========================================================================

    suspend fun fetchAnalyticsSnapshot(
        templateId: String,
        templateNameHint: String = "",
        groupBy: String = "team",
        fromDate: String? = null,
        toDate: String? = null
    ): WorkReportAnalyticsSnapshot = withContext(Dispatchers.IO) {
        val validTemplateId = WorkReportsContract.requireUuid(templateId, "Mã mẫu báo cáo")
        val safeGroupBy = if (groupBy in setOf("team", "assignee", "period")) groupBy else "team"
        val baseParams = buildList {
            add("template_id=${encodeParam(validTemplateId)}")
            add("finality=final")
            if (!fromDate.isNullOrBlank()) add("from=${encodeParam(fromDate.trim())}")
            if (!toDate.isNullOrBlank()) add("to=${encodeParam(toDate.trim())}")
        }.joinToString("&")

        val overviewJson = httpClient.getJson(
            pathWithQuery = "/api/work-reports/analytics/overview?$baseParams",
            requireOkTrue = true
        )
        val breakdownJson = httpClient.getJson(
            pathWithQuery = "/api/work-reports/analytics/breakdown?$baseParams&group_by=$safeGroupBy&page=1&page_size=50",
            requireOkTrue = true
        )
        val contributionsJson = httpClient.getJson(
            pathWithQuery = "/api/work-reports/analytics/contributions?$baseParams&limit=50",
            requireOkTrue = true
        )

        val overviewData = overviewJson.optJSONObject("data") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "ANALYTICS_OVERVIEW_INVALID",
            message = "Dữ liệu tổng quan phân tích báo cáo không hợp lệ."
        )
        val complianceObj = overviewData.optJSONObject("compliance") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "ANALYTICS_COMPLIANCE_INVALID",
            message = "Thiếu thông tin tỷ lệ hoàn thành báo cáo."
        )

        val dueCount = complianceObj.optInt("due_count", 0)
        val completedCount = complianceObj.optInt("completed_count", 0)
        val onTimeCount = complianceObj.optInt("on_time_count", 0)
        val completionRatePct = complianceObj.optNullableRatePercent("completion_rate")

        val metricsArr = overviewData.optJSONArray("metrics") ?: JSONArray()
        val metricItems = buildList {
            for (i in 0 until metricsArr.length()) {
                val m = metricsArr.optJSONObject(i) ?: continue
                val key = m.optString("metric_key", "")
                val label = m.optString("label", key)
                val unit = m.optString("unit", "").trim()
                val sumVal = if (m.isNull("sum_value")) null else m.optDouble("sum_value")
                val avgVal = if (m.isNull("avg_value")) null else m.optDouble("avg_value")
                val formattedVal = when {
                    sumVal != null -> formatNumberCompact(sumVal) + if (unit.isNotEmpty()) " $unit" else ""
                    avgVal != null -> "TB " + formatNumberCompact(avgVal) + if (unit.isNotEmpty()) " $unit" else ""
                    else -> "—"
                }
                add(
                    WorkReportAnalyticsMetricItem(
                        fieldKey = key,
                        label = label,
                        valueDisplay = formattedVal
                    )
                )
            }
        }

        val breakdownData = breakdownJson.optJSONObject("data") ?: JSONObject()
        val breakdownArr = breakdownData.optJSONArray("items") ?: JSONArray()
        val breakdownRows = buildList {
            for (i in 0 until breakdownArr.length()) {
                val row = breakdownArr.optJSONObject(i) ?: continue
                val groupId = row.optNullableText("group_id") ?: "group_$i"
                val groupLabel = row.optString("group_label", "Không xác định")
                add(
                    WorkReportAnalyticsBreakdownRow(
                        groupKey = groupId,
                        groupLabel = groupLabel,
                        submittedCount = row.optInt("completed_count", 0),
                        obligationCount = row.optInt("due_count", 0),
                        complianceRatePct = row.optNullableRatePercent("completion_rate")
                    )
                )
            }
        }

        val contribData = contributionsJson.optJSONObject("data") ?: JSONObject()
        val contribArr = contribData.optJSONArray("items") ?: JSONArray()
        val contributionItems = buildList {
            for (i in 0 until contribArr.length()) {
                val c = contribArr.optJSONObject(i) ?: continue
                add(
                    WorkReportAnalyticsContributionItem(
                        obligationId = c.optString("obligation_id", ""),
                        assigneeDisplayName = c.optString("assignee_display_name", ""),
                        periodKey = c.optString("period_key", ""),
                        submissionState = c.optString("state", ""),
                        submittedAt = c.optNullableText("submitted_at")
                    )
                )
            }
        }

        val scopeLabel = overviewData.optJSONObject("effective_scope")?.optString("label", "").orEmpty()
        val resolvedTitle = when {
            templateNameHint.isNotBlank() && scopeLabel.isNotBlank() -> "$templateNameHint ($scopeLabel)"
            templateNameHint.isNotBlank() -> templateNameHint
            scopeLabel.isNotBlank() -> "Mẫu ${validTemplateId.take(8)} ($scopeLabel)"
            else -> "Mẫu ${validTemplateId.take(8)}"
        }

        WorkReportAnalyticsSnapshot(
            templateId = validTemplateId,
            templateName = resolvedTitle,
            totalObligations = dueCount,
            submittedCount = completedCount,
            approvedCount = onTimeCount,
            complianceRatePct = completionRatePct,
            metrics = metricItems,
            breakdownRows = breakdownRows,
            contributions = contributionItems
        )
    }

    // =========================================================================
    // F1. Reporting Reads: Workforce Deep + Team Report + Team Drill-Down
    // =========================================================================

    suspend fun fetchWorkforceDeep(
        fromDate: String? = null,
        toDate: String? = null
    ): WorkforceDeepMetrics = withContext(Dispatchers.IO) {
        val params = mutableListOf("mode=deep")
        if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
        if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")

        val json = httpClient.getJson(
            pathWithQuery = "/api/reporting/workforce-deep?${params.joinToString("&")}",
            requireOkTrue = true
        )
        val wf = json.optJSONObject("workforce") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORKFORCE_DEEP_MISSING",
            message = "Phản hồi báo cáo chuyên sâu thiếu khối workforce."
        )
        if (wf.optString("status") != "ready") {
            throw BackendApiException(
                kind = BackendFailureKind.SERVER_UNAVAILABLE,
                statusCode = 503,
                backendCode = "WORKFORCE_UNAVAILABLE",
                message = "Báo cáo chuyên sâu lực lượng lao động tạm thời chưa sẵn sàng."
            )
        }

        val snapshot = wf.optJSONObject("snapshot") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORKFORCE_SNAPSHOT_MISSING",
            message = "Thiếu dữ liệu snapshot lực lượng lao động."
        )
        val eligibleTotal = snapshot.optInt("eligibleEpisodeTotal", 0)

        val waitingObj = wf.optJSONObject("waiting")
        val avgWaitingDays = waitingObj
            ?.optJSONObject("allWaiting")
            ?.optNullableFloat1("avgWaitingDaysPerEpisode")
        val placementRatePct = waitingObj?.optNullableRatePercent("placementRateOfWaiting")

        val retentionCheckpoints = wf.optJSONObject("retention")?.optJSONObject("checkpoints")
        val d7Pct = retentionCheckpoints?.optJSONObject("d7")?.optNullableRatePercent("retentionRate")
        val d30Pct = retentionCheckpoints?.optJSONObject("d30")?.optNullableRatePercent("retentionRate")
        val d90Pct = retentionCheckpoints?.optJSONObject("d90")?.optNullableRatePercent("retentionRate")

        val avgTenureDays = wf.optJSONObject("duration")
            ?.optJSONObject("allEverOn")
            ?.optNullableFloat1("avgOnDaysPerEpisode")

        val dimensions = wf.optJSONObject("dimensions")
        val byTeam = parseDimensionGroupBuckets(dimensions?.optJSONObject("byTeam"), eligibleTotal)
        val byProjectObj = dimensions?.optJSONObject("byProject")
        val byProject = parseDimensionGroupBuckets(byProjectObj, eligibleTotal)
        val projectPagination = byProjectObj?.optJSONObject("pagination")
        val byRecruiter = parseDimensionGroupBuckets(dimensions?.optJSONObject("byRecruiter"), eligibleTotal)
        val byProvider = parseDimensionGroupBuckets(dimensions?.optJSONObject("byProvider"), eligibleTotal)

        val leaveReasonsArr = wf.optJSONObject("leaveReasons")?.optJSONArray("byCategory") ?: JSONArray()
        val leaveReasons = buildList {
            for (i in 0 until leaveReasonsArr.length()) {
                val cat = leaveReasonsArr.optJSONObject(i) ?: continue
                val count = cat.optInt("episodeCount", 0)
                if (count <= 0) continue
                add(
                    BiBucketItem(
                        key = cat.optString("key", "reason_$i"),
                        display = cat.optString("label", ""),
                        count = count,
                        sharePct = cat.optNullableRatePercent("shareOfOffRate") ?: 0f
                    )
                )
            }
        }

        WorkforceDeepMetrics(
            asOfDate = snapshot.optString("asOfDate", ""),
            eligibleEpisodeTotal = eligibleTotal,
            onCount = snapshot.optInt("onCount", 0),
            unconfirmedCount = snapshot.optInt("unconfirmedCount", 0),
            offCount = snapshot.optInt("offCount", 0),
            unknownCount = snapshot.optInt("unknownCount", 0),
            futureEpisodeCount = snapshot.optInt("futureEpisodeCount", 0),
            avgWaitingDays = avgWaitingDays,
            placementRatePct = placementRatePct,
            retention7dPct = d7Pct,
            retention30dPct = d30Pct,
            retention90dPct = d90Pct,
            avgTenureDays = avgTenureDays,
            byTeam = byTeam,
            byProject = byProject,
            byRecruiter = byRecruiter,
            byProvider = byProvider,
            leaveReasons = leaveReasons,
            projectHasMore = projectPagination?.optBoolean("hasMore", false) ?: false,
            projectNextCursor = projectPagination?.optNullableText("nextCursor")
        )
    }

    suspend fun fetchWorkforceProjectPage(
        cursor: String,
        eligibleTotal: Int,
        fromDate: String? = null,
        toDate: String? = null
    ): Triple<List<BiBucketItem>, Boolean, String?> = withContext(Dispatchers.IO) {
        val params = mutableListOf(
            "mode=project_page",
            "cursor=${encodeParam(cursor.trim())}"
        )
        if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
        if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")

        val json = httpClient.getJson(
            pathWithQuery = "/api/reporting/workforce-deep?${params.joinToString("&")}",
            requireOkTrue = true
        )
        val pageObj = json.optJSONObject("projectPage") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "WORKFORCE_PROJECT_PAGE_INVALID",
            message = "Phản hồi phân trang dự án không hợp lệ."
        )
        val rowsArr = pageObj.optJSONArray("rows") ?: JSONArray()
        val items = parseBucketArray(rowsArr, eligibleTotal)
        Triple(
            items,
            pageObj.optBoolean("hasMore", false),
            pageObj.optNullableText("nextCursor")
        )
    }

    suspend fun fetchTeamReport(
        bucketKeyOrTeamCode: String,
        fromDate: String? = null,
        toDate: String? = null,
        limit: Int = 25
    ): TeamReportSnapshot = withContext(Dispatchers.IO) {
        val bucketKey = WorkReportsContract.encodeTeamBucketKey(bucketKeyOrTeamCode)
        val params = mutableListOf(
            "bucket_key=${encodeParam(bucketKey)}",
            "limit=${limit.coerceIn(1, 100)}"
        )
        if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
        if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")

        // Note: GET /api/reporting/team-report returns { report, workforce, bucket, detail } without `ok: true`
        val json = httpClient.getJson(
            pathWithQuery = "/api/reporting/team-report?${params.joinToString("&")}",
            requireOkTrue = false
        )
        val bucketObj = json.optJSONObject("bucket") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "TEAM_REPORT_BUCKET_MISSING",
            message = "Phản hồi báo cáo nhóm thiếu thông tin nhóm."
        )
        val detailObj = json.optJSONObject("detail") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "TEAM_REPORT_DETAIL_MISSING",
            message = "Phản hồi báo cáo nhóm thiếu chi tiết dòng."
        )
        val rowsArr = detailObj.optJSONArray("rows") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "TEAM_REPORT_ROWS_MISSING",
            message = "Phản hồi báo cáo nhóm thiếu danh sách dòng."
        )
        val reportObj = if (json.isNull("report")) null else json.optJSONObject("report")
        val totalRecruited = reportObj?.optInt("recruitedTotal", detailObj.optInt("total", 0))
            ?: detailObj.optInt("total", 0)

        TeamReportSnapshot(
            bucketKey = bucketObj.optString("key", bucketKey),
            bucketDisplay = bucketObj.optString("display", bucketKey),
            totalRecruited = totalRecruited,
            rows = parseTeamRows(rowsArr),
            nextCursor = detailObj.optNullableText("nextCursor")
        )
    }

    suspend fun fetchTeamDrillDownPage(
        bucketKeyOrTeamCode: String,
        cursor: String,
        fromDate: String? = null,
        toDate: String? = null,
        limit: Int = 25
    ): TeamReportSnapshot = withContext(Dispatchers.IO) {
        val bucketKey = WorkReportsContract.encodeTeamBucketKey(bucketKeyOrTeamCode)
        val params = mutableListOf(
            "bucket_key=${encodeParam(bucketKey)}",
            "cursor=${encodeParam(cursor.trim())}",
            "limit=${limit.coerceIn(1, 100)}"
        )
        if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
        if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")

        // Note: GET /api/reporting/team-drill-down returns { bucket, rows, nextCursor, total, applied, legacy_aggregate_total } without `ok: true`
        val json = httpClient.getJson(
            pathWithQuery = "/api/reporting/team-drill-down?${params.joinToString("&")}",
            requireOkTrue = false
        )
        val bucketObj = json.optJSONObject("bucket") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "TEAM_DRILL_DOWN_BUCKET_MISSING",
            message = "Phản hồi phân trang nhóm thiếu thông tin nhóm."
        )
        val rowsArr = json.optJSONArray("rows") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "TEAM_DRILL_DOWN_ROWS_MISSING",
            message = "Phản hồi phân trang nhóm thiếu danh sách dòng."
        )

        TeamReportSnapshot(
            bucketKey = bucketObj.optString("key", bucketKey),
            bucketDisplay = bucketObj.optString("display", bucketKey),
            totalRecruited = json.optInt("total", 0),
            rows = parseTeamRows(rowsArr),
            nextCursor = json.optNullableText("nextCursor")
        )
    }

    // =========================================================================
    // F2. Duplicate CCCD Report & Self-Withdraw
    // =========================================================================

    suspend fun fetchDuplicateCccdReport(
        page: Int = 1,
        pageSize: Int = 25,
        managedOnly: Boolean = false
    ): DuplicateCccdReportPage = withContext(Dispatchers.IO) {
        val safePage = page.coerceIn(1, 1000)
        val safePageSize = pageSize.coerceIn(1, 100)
        val path = "/api/direct-entry/duplicate-cccd-report?page=$safePage&page_size=$safePageSize&managed_only=$managedOnly"

        val json = httpClient.getJson(pathWithQuery = path, requireOkTrue = true)
        val groupsArr = json.optJSONArray("groups") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "DUPLICATE_CCCD_GROUPS_MISSING",
            message = "Phản hồi báo cáo CCCD trùng thiếu danh sách nhóm."
        )

        val groups = buildList {
            for (i in 0 until groupsArr.length()) {
                val g = groupsArr.optJSONObject(i) ?: throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "DUPLICATE_CCCD_GROUP_INVALID",
                    message = "Nhóm CCCD trùng không đúng định dạng hợp đồng."
                )
                val epsArr = g.optJSONArray("episodes") ?: JSONArray()
                val episodes = buildList {
                    for (j in 0 until epsArr.length()) {
                        val ep = epsArr.optJSONObject(j) ?: continue
                        val entryId = ep.optString("entry_id", "").trim()
                        if (!WorkReportsContract.isUuid(entryId)) {
                            throw BackendApiException(
                                kind = BackendFailureKind.CONTRACT_INVALID,
                                statusCode = 200,
                                backendCode = "DUPLICATE_CCCD_EPISODE_INVALID",
                                message = "Hồ sơ trong nhóm CCCD trùng thiếu entry_id hợp lệ."
                            )
                        }
                        val statusRaw = ep.optString("latest_status", "")
                        add(
                            DuplicateCccdEpisodeItem(
                                entryId = entryId,
                                entryVersion = ep.optInt("entry_version", 1),
                                employeeCode = ep.optNullableText("employee_code") ?: "—",
                                displayName = ep.optNullableText("display_name") ?: "—",
                                projectDisplay = ep.optNullableText("project_display") ?: "—",
                                firstWorkDate = ep.optNullableText("first_work_date") ?: "—",
                                latestStatus = WorkReportsContract.employmentStatusDisplayLabel(statusRaw),
                                active = ep.optBoolean("active", false),
                                canMarkOff = ep.optBoolean("can_mark_off", false),
                                canSelfWithdraw = ep.optBoolean("can_self_withdraw", false)
                            )
                        )
                    }
                }
                add(
                    DuplicateCccdGroupItem(
                        cccdLast4 = g.optString("cccd_last4", ""),
                        cccdLength = g.optInt("cccd_length", 12),
                        profileCount = g.optInt("profile_count", episodes.size),
                        projectCount = g.optInt("project_count", 0),
                        activeProfileCount = g.optInt("active_profile_count", 0),
                        firstWorkDate = g.optNullableText("first_work_date").orEmpty(),
                        latestFirstWorkDate = g.optNullableText("latest_first_work_date").orEmpty(),
                        episodes = episodes
                    )
                )
            }
        }

        DuplicateCccdReportPage(
            page = json.optInt("page", safePage),
            pageSize = json.optInt("page_size", safePageSize),
            totalGroups = json.optInt("total_groups", groups.size),
            totalEntries = json.optInt("total_entries", 0),
            hasMore = json.optBoolean("has_more", false),
            authorizationDate = json.optString("authorization_date", ""),
            groups = groups
        )
    }

    suspend fun selfWithdrawDuplicateCccd(
        entryId: String,
        expectedEntryVersion: Int,
        reason: String? = null
    ) = withContext(Dispatchers.IO) {
        val validEntryId = WorkReportsContract.requireUuid(entryId, "Mã hồ sơ cần rút trùng")
        if (expectedEntryVersion < 1) {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = 400,
                backendCode = "SELF_WITHDRAW_INVALID",
                message = "Phiên bản hồ sơ cần rút trùng không hợp lệ."
            )
        }
        val normalizedReason = reason?.trim().orEmpty()
        val fingerprint = "dup:withdraw:$validEntryId:v=$expectedEntryVersion:${normalizedReason.hashCode()}"
        val idempotencyKey = idempotencyStore.resolve(fingerprint)

        // Exact 4 keys enforced by DUPLICATE_CCCD_SELF_WITHDRAW_BODY_KEYS — never send authority or action flags
        val body = JSONObject().apply {
            put("entry_id", validEntryId)
            put("expected_entry_version", expectedEntryVersion)
            put("idempotency_key", idempotencyKey)
            put("reason", if (normalizedReason.isEmpty()) JSONObject.NULL else normalizedReason)
        }

        try {
            httpClient.postJson(
                path = "/api/direct-entry/duplicate-cccd-report/self-withdraw",
                bodyJson = body,
                idempotencyKey = idempotencyKey,
                requireOkTrue = true
            )
            idempotencyStore.clear(fingerprint)
        } catch (e: BackendApiException) {
            if (!isRetryableFailure(e.kind)) {
                idempotencyStore.clear(fingerprint)
            }
            throw e
        }
    }

    private fun parseDimensionGroupBuckets(groupObj: JSONObject?, eligibleTotal: Int): List<BiBucketItem> {
        if (groupObj == null) return emptyList()
        val arr = groupObj.optJSONArray("buckets") ?: return emptyList()
        return parseBucketArray(arr, groupObj.optInt("eligibleEpisodeTotal", eligibleTotal))
    }

    private fun parseBucketArray(arr: JSONArray, denominator: Int): List<BiBucketItem> = buildList {
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            val count = b.optInt("eligibleEpisodeCount", 0)
            val sharePct = if (denominator > 0) {
                ((count.toDouble() / denominator.toDouble()) * 1000.0).roundToInt() / 10f
            } else {
                0f
            }
            add(
                BiBucketItem(
                    key = b.optString("key", "bucket_$i"),
                    display = b.optString("display", ""),
                    count = count,
                    sharePct = sharePct
                )
            )
        }
    }

    private fun parseTeamRows(rowsArr: JSONArray): List<TeamDrillDownItem> = buildList {
        for (i in 0 until rowsArr.length()) {
            val r = rowsArr.optJSONObject(i) ?: throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "TEAM_ROW_INVALID",
                message = "Dòng chi tiết báo cáo nhóm không hợp lệ."
            )
            add(
                TeamDrillDownItem(
                    businessDate = r.optString("business_date", ""),
                    projectDisplay = r.optString("project_display", ""),
                    recruiterDisplay = r.optString("recruiter_display", ""),
                    providerTypeDisplay = r.optString("provider_type_display", ""),
                    employmentTypeDisplay = r.optString("employment_type_display", ""),
                    recruitedCount = r.optInt("recruited_count", 0),
                    firstWorkDate = r.optString("first_work_date", "")
                )
            )
        }
    }

    private fun isRetryableFailure(kind: BackendFailureKind): Boolean =
        kind == BackendFailureKind.NETWORK ||
            kind == BackendFailureKind.RATE_LIMITED ||
            kind == BackendFailureKind.SERVER_UNAVAILABLE

    private fun encodeParam(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun formatNumberCompact(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }
}

private fun JSONObject.optNullableText(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key, "").trim().ifBlank { null }
}

private fun JSONObject.optNullableFloat1(key: String): Float? {
    if (!has(key) || isNull(key)) return null
    val d = optDouble(key, Double.NaN)
    if (d.isNaN() || d.isInfinite()) return null
    return ((d * 10.0).roundToInt()) / 10f
}

private fun JSONObject.optNullableRatePercent(key: String): Float? {
    if (!has(key) || isNull(key)) return null
    val d = optDouble(key, Double.NaN)
    if (d.isNaN() || d.isInfinite()) return null
    return ((d * 1000.0).roundToInt()) / 10f
}
