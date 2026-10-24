package com.example.data

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class PeriodAllScopeRecruitmentSummary(
    val totalRecruited: Int,
    val byRecruiter: List<BiBucketItem>,
    val byProject: List<BiBucketItem>,
    val byTeam: List<BiBucketItem>,
    val byProvider: List<BiBucketItem>,
    val hiringSummary: WorkReportsContract.HiringDaySummary
)

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
        val encoded = Base64.encodeToString(
            trimmed.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "team.$encoded"
    }

    fun decodeTeamBucketCode(rawTeamCodeOrBucketKey: String): String {
        val trimmed = rawTeamCodeOrBucketKey.trim()
        if (!trimmed.startsWith("team.") || !CANONICAL_TEAM_BUCKET_REGEX.matches(trimmed)) {
            return trimmed
        }
        val b64 = trimmed.removePrefix("team.")
        val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
        return runCatching {
            String(Base64.decode(padded, Base64.URL_SAFE), Charsets.UTF_8).trim().ifEmpty { trimmed }
        }.getOrDefault(trimmed)
    }

    fun candidateTeamCodes(rawKey: String): List<String> {
        val trimmed = decodeTeamBucketCode(rawKey).trim()
        if (trimmed.isEmpty() || trimmed == "unknown") return listOf(trimmed).filter { it.isNotEmpty() }
        if (trimmed.equals("__system_vendor__", ignoreCase = true)) {
            return listOf("__system_vendor__")
        }
        val titleCased = buildString(trimmed.length) {
            var capitalizeNext = true
            for (ch in trimmed.lowercase(Locale.ROOT)) {
                if (ch == '_' || ch == '-' || ch == ' ') {
                    append(ch)
                    capitalizeNext = true
                } else if (capitalizeNext && ch.isLetter()) {
                    append(ch.titlecaseChar())
                    capitalizeNext = false
                } else {
                    append(ch)
                    capitalizeNext = false
                }
            }
        }
        return listOf(
            trimmed,
            trimmed.uppercase(Locale.ROOT),
            titleCased,
            trimmed.lowercase(Locale.ROOT)
        ).filter { it.isNotBlank() }.distinct()
    }

    fun resolveDatePresetRange(
        preset: DashboardDatePreset,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Pair<String?, String?> {
        if (preset == DashboardDatePreset.ALL_TIME) return null to null
        val tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        val todayCal = Calendar.getInstance(tz, Locale.US).apply {
            timeInMillis = nowEpochMillis
        }
        fun formatIso(cal: Calendar): String = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        return when (preset) {
            DashboardDatePreset.ALL_TIME -> null to null
            DashboardDatePreset.THIS_WEEK -> {
                val startCal = (todayCal.clone() as Calendar).apply {
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val offsetFromMonday = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
                    add(Calendar.DAY_OF_MONTH, offsetFromMonday)
                }
                formatIso(startCal) to formatIso(todayCal)
            }
            DashboardDatePreset.THIS_MONTH -> {
                val startCal = (todayCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                formatIso(startCal) to formatIso(todayCal)
            }
            DashboardDatePreset.LAST_MONTH -> {
                val startCal = (todayCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                val endCal = (startCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                formatIso(startCal) to formatIso(endCal)
            }
        }
    }

    data class HiringDaySummary(
        val activeHiringDays: Int,
        val avgPerActiveDay: Float?,
        val peakDailyCount: Int?,
        val peakDateIso: String?
    )

    fun computeHiringDaySummary(
        byDateCounts: Map<String, Int>,
        totalCount: Int
    ): HiringDaySummary {
        val activeEntries = byDateCounts.entries.filter { it.key.isNotBlank() && it.value > 0 }
        if (activeEntries.isEmpty() || totalCount <= 0) {
            return HiringDaySummary(
                activeHiringDays = 0,
                avgPerActiveDay = null,
                peakDailyCount = null,
                peakDateIso = null
            )
        }
        val activeDays = activeEntries.size
        val avg = ((totalCount.toDouble() / activeDays.toDouble()) * 10.0).roundToInt() / 10f
        val peakCount = activeEntries.maxOf { it.value }
        val peakDate = activeEntries
            .filter { it.value == peakCount }
            .minOfOrNull { it.key }
        return HiringDaySummary(
            activeHiringDays = activeDays,
            avgPerActiveDay = avg,
            peakDailyCount = peakCount,
            peakDateIso = peakDate
        )
    }

    fun parseReportingBucketRecord(
        recordObj: JSONObject?,
        denominator: Int
    ): List<BiBucketItem> {
        if (recordObj == null) return emptyList()
        val items = mutableListOf<BiBucketItem>()
        val keys = recordObj.keys()
        while (keys.hasNext()) {
            val mapKey = keys.next()
            val bucketObj = recordObj.optJSONObject(mapKey) ?: continue
            val count = bucketObj.optInt("recruitedCount", 0)
            if (count <= 0) continue
            val key = bucketObj.optString("key", mapKey).ifBlank { mapKey }
            val display = bucketObj.optString("display", key).ifBlank { key }
            val sharePct = if (denominator > 0) {
                ((count.toDouble() / denominator.toDouble()) * 1000.0).roundToInt() / 10f
            } else {
                0f
            }
            items.add(
                BiBucketItem(
                    key = key,
                    display = display,
                    count = count,
                    sharePct = sharePct
                )
            )
        }
        return items.sortedWith(
            compareByDescending<BiBucketItem> { it.count }
                .thenBy { it.display }
        )
    }

    fun buildBucketsFromKeyLabels(
        pairs: Iterable<Pair<String, String>>,
        denominator: Int
    ): List<BiBucketItem> {
        val grouped = linkedMapOf<String, Pair<String, Int>>()
        for ((rawKey, rawLabel) in pairs) {
            val key = rawKey.trim().ifEmpty { "unknown" }
            val label = rawLabel.trim().ifEmpty { "Chưa xác định" }
            val existing = grouped[key]
            grouped[key] = if (existing == null) {
                label to 1
            } else {
                existing.first to (existing.second + 1)
            }
        }
        return grouped.entries
            .map { (key, value) ->
                val count = value.second
                val sharePct = if (denominator > 0) {
                    ((count.toDouble() / denominator.toDouble()) * 1000.0).roundToInt() / 10f
                } else {
                    0f
                }
                BiBucketItem(
                    key = key,
                    display = value.first,
                    count = count,
                    sharePct = sharePct
                )
            }
            .sortedWith(
                compareByDescending<BiBucketItem> { it.count }
                    .thenBy { it.display }
            )
    }

    fun buildTeamReportFromWorkerRows(
        bucketKey: String,
        bucketDisplay: String,
        rows: List<WorkerProfileEntity>,
        recruiterCatalogById: Map<String, CatalogRecruiterOption> = emptyMap(),
        totalWorkersOverride: Int? = null
    ): TeamReportSnapshot {
        val total = maxOf(rows.size, totalWorkersOverride ?: 0)
        val denominator = rows.size.coerceAtLeast(1)

        val byRecruiter = if (rows.isEmpty()) {
            emptyList()
        } else {
            buildBucketsFromKeyLabels(
                pairs = rows.map {
                    it.recruiterId.ifBlank { "unknown" } to it.recruiterDisplay.ifBlank { "Chưa rõ người tuyển" }
                },
                denominator = denominator
            )
        }

        val byProject = if (rows.isEmpty()) {
            emptyList()
        } else {
            buildBucketsFromKeyLabels(
                pairs = rows.map {
                    it.projectId.ifBlank { "unknown" } to it.projectDisplay.ifBlank { "Chưa rõ dự án" }
                },
                denominator = denominator
            )
        }

        val byEmployment = if (rows.isEmpty()) {
            emptyList()
        } else {
            buildBucketsFromKeyLabels(
                pairs = rows.map {
                    it.laborType.ifBlank { "unknown" } to it.laborTypeDisplay.ifBlank { "Loại hình khác" }
                },
                denominator = denominator
            )
        }

        val byProvider = if (rows.isEmpty() || recruiterCatalogById.isEmpty()) {
            emptyList()
        } else {
            buildBucketsFromKeyLabels(
                pairs = rows.map { row ->
                    val providerRaw = recruiterCatalogById[row.recruiterId]?.providerType?.lowercase(Locale.ROOT)
                    when (providerRaw) {
                        "hrp" -> "hrp" to "HRP"
                        "vendor" -> "vendor" to "Vendor"
                        else -> "unknown" to "Chưa phân loại"
                    }
                },
                denominator = denominator
            )
        }

        val byDateCounts = rows
            .filter { it.firstWorkDate.isNotBlank() }
            .groupingBy { it.firstWorkDate.trim() }
            .eachCount()
        val hiringSummary = computeHiringDaySummary(byDateCounts, rows.size)

        val workingCount = rows.count { it.employmentStatus.equals("ON", ignoreCase = true) }
        val waitingCount = rows.count { it.employmentStatus.equals("UNCONFIRMED", ignoreCase = true) }
        val leftCount = rows.count { it.employmentStatus.equals("OFF", ignoreCase = true) }

        val sortedDetailRows = rows
            .sortedWith(
                compareByDescending<WorkerProfileEntity> { it.firstWorkDate }
                    .thenBy { it.recruiterDisplay }
                    .thenBy { it.entryId }
            )
            .map { row ->
                val providerLabel = when (recruiterCatalogById[row.recruiterId]?.providerType?.lowercase(Locale.ROOT)) {
                    "hrp" -> "HRP"
                    "vendor" -> "Vendor"
                    else -> "HRP"
                }
                TeamDrillDownItem(
                    businessDate = row.firstWorkDate,
                    projectDisplay = row.projectDisplay,
                    recruiterDisplay = row.recruiterDisplay,
                    providerTypeDisplay = providerLabel,
                    employmentTypeDisplay = row.laborTypeDisplay,
                    recruitedCount = 1,
                    firstWorkDate = row.firstWorkDate
                )
            }

        return TeamReportSnapshot(
            bucketKey = bucketKey,
            bucketDisplay = bucketDisplay.ifBlank { "Nhóm phụ trách" },
            totalRecruited = total,
            rows = sortedDetailRows,
            nextCursor = null,
            activeRecruiterCount = byRecruiter.count { it.count > 0 },
            activeProjectCount = byProject.count { it.count > 0 },
            activeHiringDays = hiringSummary.activeHiringDays,
            avgPerActiveDay = hiringSummary.avgPerActiveDay,
            peakDailyCount = hiringSummary.peakDailyCount,
            peakDateIso = hiringSummary.peakDateIso,
            workingCount = workingCount,
            waitingCount = waitingCount,
            leftCount = leftCount,
            byRecruiter = byRecruiter,
            byProject = byProject,
            byEmployment = byEmployment,
            byProvider = byProvider
        )
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

    fun submissionStateDisplayLabel(stateCode: String): String = when (stateCode.trim().uppercase(Locale.ROOT)) {
        "APPROVED" -> "Đã duyệt"
        "COMPLETED" -> "Hoàn thành"
        "PENDING_REVIEW", "SUBMITTED" -> "Chờ duyệt"
        "REJECTED" -> "Cần sửa lại"
        "DRAFT" -> "Bản nháp"
        "NOT_STARTED" -> "Chưa làm"
        "" -> "—"
        else -> "Đang xử lý"
    }

    fun formatPercentVi(pct: Float?): String {
        if (pct == null || pct.isNaN() || pct.isInfinite()) return "—"
        val rounded = (pct.toDouble() * 100.0).roundToInt() / 100.0
        val raw = String.format(Locale.US, "%.2f", rounded)
            .trimEnd('0')
            .trimEnd('.')
            .replace('.', ',')
        return "$raw%"
    }

    private val ISO_DATE_ONLY_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val ISO_MONTH_REGEX = Regex("^(\\d{4})-(\\d{2})$")
    private val ISO_WEEK_REGEX = Regex("^(\\d{4})-W(\\d{1,2})$", RegexOption.IGNORE_CASE)

    fun formatPeriodKeyVi(periodKey: String): String {
        val trimmed = periodKey.trim()
        if (trimmed.isEmpty()) return "—"
        ISO_DATE_ONLY_REGEX.matchEntire(trimmed)?.let { m ->
            return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
        }
        ISO_MONTH_REGEX.matchEntire(trimmed)?.let { m ->
            return "Tháng ${m.groupValues[2]}/${m.groupValues[1]}"
        }
        ISO_WEEK_REGEX.matchEntire(trimmed)?.let { m ->
            return "Tuần ${m.groupValues[2]}/${m.groupValues[1]}"
        }
        return trimmed
    }

    fun formatIsoDateTimeVi(isoRaw: String?): String {
        val trimmed = isoRaw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "—"
        runCatching {
            val epochMs = AttendanceTimeContract.parseIsoInstantToEpochMillis(trimmed)
            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
            return sdf.format(Date(epochMs))
        }
        ISO_DATE_ONLY_REGEX.matchEntire(trimmed)?.let { m ->
            return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
        }
        return trimmed.take(16).replace('T', ' ')
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
    // E3. Work Report Reviews & Selectable Templates
    // =========================================================================

    suspend fun fetchSelectableTemplates(actor: AppActor): List<WorkReportTemplateOption> =
        withContext(Dispatchers.IO) {
            val discovered = linkedMapOf<String, String>()
            if (actor.hasCapability("report_assign")) {
                runCatching {
                    val json = httpClient.getJson(
                        pathWithQuery = "/api/work-reports/assignments",
                        requireOkTrue = true
                    )
                    val arr = json.optJSONArray("templates")
                        ?: json.optJSONObject("data")?.optJSONArray("templates")
                        ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i) ?: continue
                        val id = t.optString("template_id", "").trim()
                        val name = t.optString("display_name", "").trim()
                        if (WorkReportsContract.isUuid(id) && name.isNotEmpty()) {
                            discovered[id] = name
                        }
                    }
                }
            }
            if (discovered.isEmpty() && actor.hasCapability("report_template_manage")) {
                runCatching {
                    val json = httpClient.getJson(
                        pathWithQuery = "/api/work-reports/templates",
                        requireOkTrue = true
                    )
                    val arr = json.optJSONArray("items")
                        ?: json.optJSONObject("data")?.optJSONArray("items")
                        ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i) ?: continue
                        val status = t.optString("status", "ACTIVE").trim()
                        if (status == "ARCHIVED") continue
                        val id = t.optString("template_id", "").trim()
                        val name = t.optString("display_name", "").trim()
                        if (WorkReportsContract.isUuid(id) && name.isNotEmpty()) {
                            discovered[id] = name
                        }
                    }
                }
            }
            discovered.map { (id, name) -> WorkReportTemplateOption(templateId = id, displayName = name) }
        }

    suspend fun fetchPendingReviews(
        templateId: String? = null,
        cursor: String? = null,
        pageSize: Int = 50
    ): List<WorkReportReviewItem> = withContext(Dispatchers.IO) {
        val safeSize = pageSize.coerceIn(1, 100)
        val queryParts = mutableListOf("page_size=$safeSize")
        if (!templateId.isNullOrBlank()) {
            val validTemplateId = WorkReportsContract.requireUuid(templateId, "Mẫu báo cáo")
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
            if (!WorkReportsContract.isUuid(obligationId) ||
                (submissionId.isNotEmpty() && !WorkReportsContract.isUuid(submissionId))
            ) {
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
                    submissionVersion = obj.optInt("submission_version", obj.optInt("version", 1)),
                    submissionState = obj.optString("state", "PENDING_REVIEW").ifBlank { "PENDING_REVIEW" },
                    templateId = obj.optString("template_id", ""),
                    templateName = obj.optString("template_name", ""),
                    periodKey = obj.optString("period_key", ""),
                    assigneeUserId = obj.optString("assignee_app_user_id", ""),
                    assigneeDisplayName = obj.optNullableText("assignee_display_name")
                        ?: obj.optNullableText("assignee_label")
                        ?: "Nhân sự",
                    teamDisplayName = obj.optNullableText("team_display_name")
                        ?: obj.optNullableText("team_label"),
                    submittedAt = obj.optNullableText("submitted_at"),
                    payloadSummary = emptyMap(),
                    sections = emptyList(),
                    dueAt = obj.optNullableText("due_at"),
                    isLate = obj.optBoolean("is_late", false)
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
        val validId = WorkReportsContract.requireUuid(obligationId, "Báo cáo cần duyệt")
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

        val currentRevisionPayload = detail.optJSONObject("current_revision")?.optJSONObject("payload_json")
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
        val activePayload = currentRevisionPayload
            ?: latestPayload
            ?: submissionObj.optJSONObject("draft_payload_json")
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
            assigneeDisplayName = obligationObj.optNullableText("assignee_display_name")
                ?: detail.optNullableText("assignee_display_name")
                ?: fallback?.assigneeDisplayName
                ?: "Nhân sự",
            teamDisplayName = obligationObj.optNullableText("team_display_name")
                ?: detail.optNullableText("team_display_name")
                ?: fallback?.teamDisplayName,
            submittedAt = submissionObj.optNullableText("latest_submitted_at")
                ?: submissionObj.optNullableText("first_submitted_at")
                ?: fallback?.submittedAt,
            payloadSummary = summaryMap,
            sections = sections,
            dueAt = obligationObj.optNullableText("due_at") ?: fallback?.dueAt,
            isLate = submissionObj.optBoolean("is_late", fallback?.isLate ?: false)
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
        finality: String = "final",
        fromDate: String? = null,
        toDate: String? = null
    ): WorkReportAnalyticsSnapshot = withContext(Dispatchers.IO) {
        val validTemplateId = WorkReportsContract.requireUuid(templateId, "Mẫu báo cáo")
        val safeGroupBy = if (groupBy in setOf("team", "assignee", "period")) groupBy else "team"
        val safeFinality = if (finality in setOf("final", "provisional_included")) finality else "final"
        val baseParams = buildList {
            add("template_id=${encodeParam(validTemplateId)}")
            add("finality=$safeFinality")
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
            message = "Dữ liệu tổng hợp báo cáo không hợp lệ."
        )
        val complianceObj = overviewData.optJSONObject("compliance") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "ANALYTICS_COMPLIANCE_INVALID",
            message = "Thiếu thông tin tiến độ nộp báo cáo."
        )

        val dueCount = complianceObj.optInt("due_count", 0)
        val completedCount = complianceObj.optInt("completed_count", 0)
        val onTimeCount = complianceObj.optInt("on_time_count", 0)
        val overdueMissingCount = complianceObj.optInt("overdue_missing_count", 0)
        val lateSubmittedCount = complianceObj.optInt("late_submitted_count", 0)
        val pendingDueCount = complianceObj.optInt("pending_due_count", 0)
        val pendingReviewCount = complianceObj.optInt("pending_review_count", 0)
        // Backend `completion_rate` is ALREADY on a 0..100 percentage scale (e.g. 1/7 -> 14.29)
        val completionRatePct = complianceObj.optNullablePercentAlreadyScaled("completion_rate")

        val metricsArr = overviewData.optJSONArray("metrics") ?: JSONArray()
        val metricItems = buildList {
            for (i in 0 until metricsArr.length()) {
                val m = metricsArr.optJSONObject(i) ?: continue
                val key = m.optString("metric_key", "")
                val label = m.optString("label", key)
                val unit = m.optString("unit", "").trim()
                val sumVal = if (m.isNull("sum_value")) null else m.optDouble("sum_value")
                val avgVal = if (m.isNull("avg_value")) null else m.optDouble("avg_value")
                val countValid = m.optInt("count_valid", 0)
                val formattedVal = when {
                    sumVal != null -> formatNumberCompact(sumVal) + if (unit.isNotEmpty()) " $unit" else ""
                    avgVal != null -> "TB " + formatNumberCompact(avgVal) + if (unit.isNotEmpty()) " $unit" else ""
                    else -> "—"
                }
                val subtitleParts = buildList {
                    if (sumVal != null && avgVal != null) {
                        add("Trung bình: ${formatNumberCompact(avgVal)}${if (unit.isNotEmpty()) " $unit" else ""}")
                    }
                    if (countValid > 0) {
                        add("Từ $countValid báo cáo")
                    }
                }
                add(
                    WorkReportAnalyticsMetricItem(
                        fieldKey = key,
                        label = label,
                        valueDisplay = formattedVal,
                        subtitleDisplay = subtitleParts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
                val groupLabel = row.optString("group_label", "Chưa phân nhóm")
                val rowMetricsArr = row.optJSONArray("metrics") ?: JSONArray()
                val rowMetricSummaries = buildList {
                    for (k in 0 until rowMetricsArr.length()) {
                        val rm = rowMetricsArr.optJSONObject(k) ?: continue
                        if (rm.isNull("sum_value")) continue
                        val rmLabel = rm.optString("label", "")
                        val rmUnit = rm.optString("unit", "").trim()
                        val rmSum = formatNumberCompact(rm.optDouble("sum_value", 0.0))
                        if (rmLabel.isNotBlank()) {
                            add("$rmLabel: $rmSum${if (rmUnit.isNotEmpty()) " $rmUnit" else ""}")
                        }
                    }
                }
                add(
                    WorkReportAnalyticsBreakdownRow(
                        groupKey = groupId,
                        groupLabel = groupLabel,
                        submittedCount = row.optInt("completed_count", 0),
                        obligationCount = row.optInt("due_count", 0),
                        complianceRatePct = row.optNullablePercentAlreadyScaled("completion_rate"),
                        overdueMissingCount = row.optInt("overdue_missing_count", 0),
                        metricsSummary = rowMetricSummaries
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
                        assigneeDisplayName = c.optString("assignee_display_name", "Nhân sự"),
                        periodKey = c.optString("period_key", ""),
                        submissionState = c.optString("state", ""),
                        submittedAt = c.optNullableText("submitted_at"),
                        teamDisplayName = c.optNullableText("team_display_name"),
                        isLate = c.optBoolean("is_late", false)
                    )
                )
            }
        }

        val scopeLabel = overviewData.optJSONObject("effective_scope")?.optString("label", "")?.trim().orEmpty()
        val cleanHint = templateNameHint.trim()
            .takeUnless { Regex("^Mẫu\\s+[0-9a-fA-F]{8}\\b.*").matches(it) }
            .orEmpty()
        val resolvedTitle = cleanHint.ifBlank { "Báo cáo định kỳ" }

        WorkReportAnalyticsSnapshot(
            templateId = validTemplateId,
            templateName = resolvedTitle,
            totalObligations = dueCount,
            submittedCount = completedCount,
            approvedCount = onTimeCount,
            complianceRatePct = completionRatePct,
            metrics = metricItems,
            breakdownRows = breakdownRows,
            contributions = contributionItems,
            scopeLabel = scopeLabel,
            finality = safeFinality,
            groupBy = safeGroupBy,
            overdueMissingCount = overdueMissingCount,
            lateSubmittedCount = lateSubmittedCount,
            pendingDueCount = pendingDueCount,
            pendingReviewCount = pendingReviewCount
        )
    }

    // =========================================================================
    // F1. Reporting Reads: Workforce Deep + Team Report + Team Drill-Down
    // =========================================================================

    suspend fun fetchAdminTeamCodeMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = linkedMapOf(
            "__system_vendor__" to "__system_vendor__",
            "display:vendor" to "__system_vendor__"
        )
        try {
            var page = 1
            while (page <= 5) {
                val json = httpClient.getJson(
                    pathWithQuery = "/api/admin/catalog/teams?include_inactive=true&page=$page&page_size=100",
                    requireOkTrue = true
                )
                val listObj = json.optJSONObject("list") ?: break
                val teamsArr = listObj.optJSONArray("teams") ?: break
                if (teamsArr.length() == 0) break
                for (i in 0 until teamsArr.length()) {
                    val t = teamsArr.optJSONObject(i) ?: continue
                    val code = t.optString("code", "").trim()
                    if (code.isBlank()) continue
                    result[code.lowercase(Locale.ROOT)] = code
                    val displayName = t.optString("display_name", "").trim()
                    if (displayName.isNotBlank()) {
                        result.putIfAbsent("display:${displayName.lowercase(Locale.ROOT)}", code)
                    }
                    val teamId = t.optString("team_id", "").trim()
                    if (teamId.isNotBlank()) {
                        result.putIfAbsent("id:${teamId.lowercase(Locale.ROOT)}", code)
                    }
                }
                val total = listObj.optInt("total", teamsArr.length())
                if (page * 100 >= total) break
                page++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Actors without catalog_master_manage fall back to deterministic casing candidates
        }
        result
    }

    suspend fun fetchWorkforceDeep(
        fromDate: String? = null,
        toDate: String? = null
    ): WorkforceDeepMetrics = withContext(Dispatchers.IO) {
        val params = mutableListOf("mode=deep")
        if (fromDate.isNullOrBlank() && toDate.isNullOrBlank()) {
            params.add("range=all")
        } else {
            if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
            if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")
        }

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
        val totalProjectCount = projectPagination?.optInt("totalCount", 0)?.takeIf { it > 0 }
            ?: byProject.count { it.key != "unknown" }.coerceAtLeast(byProject.size)
        val byRecruiter = parseDimensionGroupBuckets(dimensions?.optJSONObject("byRecruiter"), eligibleTotal)
        val byProvider = parseDimensionGroupBuckets(dimensions?.optJSONObject("byProvider"), eligibleTotal)

        val balanceObj = wf.optJSONObject("balance")
        val balanceApplicable = balanceObj?.optBoolean("applicable", false) == true
        val byStatusObj = if (balanceApplicable) balanceObj?.optJSONObject("byStatus") else null
        val periodNewEpisodes = if (balanceApplicable) {
            balanceObj?.optJSONObject("newEpisodesInPeriod")?.optInt("total", 0)
        } else {
            null
        }
        val periodOnInflow = byStatusObj?.optJSONObject("on")?.optInt("totalInflowCount", 0)
        val periodWaitingInflow = byStatusObj?.optJSONObject("unconfirmed")?.optInt("totalInflowCount", 0)
        val periodOffInflow = byStatusObj?.optJSONObject("off")?.optInt("totalInflowCount", 0)
        val periodNetOnChange = byStatusObj?.optJSONObject("on")?.optInt("netChange", 0)

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
            projectNextCursor = projectPagination?.optNullableText("nextCursor"),
            totalProjectCount = totalProjectCount,
            periodFromDate = if (balanceApplicable) fromDate?.trim()?.ifBlank { null } else null,
            periodToDate = if (balanceApplicable) toDate?.trim()?.ifBlank { null } else null,
            periodNewEpisodes = periodNewEpisodes,
            periodOnInflow = periodOnInflow,
            periodWaitingInflow = periodWaitingInflow,
            periodOffInflow = periodOffInflow,
            periodNetOnChange = periodNetOnChange
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
        if (fromDate.isNullOrBlank() && toDate.isNullOrBlank()) {
            params.add("range=all")
        } else {
            if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
            if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")
        }

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
        limit: Int = 25,
        knownTeamCodeMap: Map<String, String> = emptyMap(),
        knownBucketHint: BiBucketItem? = null
    ): TeamReportSnapshot = withContext(Dispatchers.IO) {
        val trimmedRaw = bucketKeyOrTeamCode.trim()
        if (trimmedRaw.isEmpty()) {
            WorkReportsContract.encodeTeamBucketKey("")
        }
        val decodedCode = WorkReportsContract.decodeTeamBucketCode(trimmedRaw).trim()
        val hintDisplayLower = knownBucketHint?.display?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        fun resolveFromCatalog(map: Map<String, String>): String? =
            map[decodedCode.lowercase(Locale.ROOT)]
                ?: hintDisplayLower?.let { map["display:$it"] }

        val mappedInitial = resolveFromCatalog(knownTeamCodeMap)
        val orderedCandidates = if (!mappedInitial.isNullOrBlank()) {
            listOf(mappedInitial)
        } else {
            WorkReportsContract.candidateTeamCodes(decodedCode)
        }

        fun enrichWithBucketHint(snap: TeamReportSnapshot): TeamReportSnapshot {
            if (knownBucketHint == null) return snap
            return snap.copy(
                workingCount = snap.workingCount ?: knownBucketHint.onCount,
                waitingCount = snap.waitingCount ?: knownBucketHint.unconfirmedCount,
                leftCount = snap.leftCount ?: knownBucketHint.offCount
            )
        }

        var lastInvalidFilter: BackendApiException? = null
        val triedCodes = linkedSetOf<String>()

        for (candidate in orderedCandidates) {
            triedCodes.add(candidate)
            try {
                return@withContext enrichWithBucketHint(
                    requestTeamReportOnce(
                        bucketKeyOrTeamCode = candidate,
                        fromDate = fromDate,
                        toDate = toDate,
                        limit = limit
                    )
                )
            } catch (e: BackendApiException) {
                if (e.statusCode == 400 && e.backendCode == "INVALID_FILTER") {
                    lastInvalidFilter = e
                } else {
                    throw e
                }
            }
        }

        // Fallback 1: if catalog map was not preloaded, probe admin team catalog once
        var effectiveMap = knownTeamCodeMap
        if (effectiveMap.isEmpty()) {
            effectiveMap = fetchAdminTeamCodeMap()
            val catalogCode = resolveFromCatalog(effectiveMap)
            if (!catalogCode.isNullOrBlank() && triedCodes.add(catalogCode)) {
                try {
                    return@withContext enrichWithBucketHint(
                        requestTeamReportOnce(
                            bucketKeyOrTeamCode = catalogCode,
                            fromDate = fromDate,
                            toDate = toDate,
                            limit = limit
                        )
                    )
                } catch (e: BackendApiException) {
                    if (e.statusCode == 400 && e.backendCode == "INVALID_FILTER") {
                        lastInvalidFilter = e
                    } else {
                        throw e
                    }
                }
            }
        }

        // Fallback 2: backend team-report returns 400 INVALID_FILTER when a valid team has 0 new hires in [fromDate, toDate]
        val hasDateFilter = !fromDate.isNullOrBlank() || !toDate.isNullOrBlank()
        if (hasDateFilter && knownBucketHint != null) {
            val catalogVerifiedCode = resolveFromCatalog(effectiveMap)
            if (!catalogVerifiedCode.isNullOrBlank()) {
                return@withContext TeamReportSnapshot(
                    bucketKey = WorkReportsContract.encodeTeamBucketKey(catalogVerifiedCode),
                    bucketDisplay = knownBucketHint.display.ifBlank { catalogVerifiedCode },
                    totalRecruited = 0,
                    rows = emptyList(),
                    nextCursor = null,
                    workingCount = knownBucketHint.onCount,
                    waitingCount = knownBucketHint.unconfirmedCount,
                    leftCount = knownBucketHint.offCount
                )
            }
            for (probeCandidate in triedCodes) {
                val allTimeSnap = try {
                    requestTeamReportOnce(
                        bucketKeyOrTeamCode = probeCandidate,
                        fromDate = null,
                        toDate = null,
                        limit = 1
                    )
                } catch (_: BackendApiException) {
                    null
                }
                if (allTimeSnap != null) {
                    return@withContext TeamReportSnapshot(
                        bucketKey = allTimeSnap.bucketKey,
                        bucketDisplay = allTimeSnap.bucketDisplay.ifBlank {
                            knownBucketHint.display.ifBlank { probeCandidate }
                        },
                        totalRecruited = 0,
                        rows = emptyList(),
                        nextCursor = null,
                        workingCount = knownBucketHint.onCount,
                        waitingCount = knownBucketHint.unconfirmedCount,
                        leftCount = knownBucketHint.offCount
                    )
                }
            }
        }

        throw lastInvalidFilter ?: BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = 400,
            backendCode = "INVALID_FILTER",
            message = "Vui lòng chọn mã nhóm hợp lệ."
        )
    }

    private suspend fun requestTeamReportOnce(
        bucketKeyOrTeamCode: String,
        fromDate: String?,
        toDate: String?,
        limit: Int
    ): TeamReportSnapshot {
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
        val parsedRows = parseTeamRows(rowsArr)

        val byRecruiter = WorkReportsContract.parseReportingBucketRecord(
            reportObj?.optJSONObject("byRecruiter"),
            totalRecruited
        )
        val byProject = WorkReportsContract.parseReportingBucketRecord(
            reportObj?.optJSONObject("byProject"),
            totalRecruited
        )
        val byEmployment = WorkReportsContract.parseReportingBucketRecord(
            reportObj?.optJSONObject("byEmployment"),
            totalRecruited
        )
        val byProvider = WorkReportsContract.parseReportingBucketRecord(
            reportObj?.optJSONObject("byProvider"),
            totalRecruited
        )

        val byDateCounts = buildMap {
            val byDateObj = reportObj?.optJSONObject("byDate")
            if (byDateObj != null) {
                val keys = byDateObj.keys()
                while (keys.hasNext()) {
                    val dateKey = keys.next()
                    val count = byDateObj.optInt(dateKey, 0)
                    if (count > 0) put(dateKey, count)
                }
            } else {
                for (row in parsedRows) {
                    if (row.businessDate.isNotBlank() && row.recruitedCount > 0) {
                        put(row.businessDate, (get(row.businessDate) ?: 0) + row.recruitedCount)
                    }
                }
            }
        }
        val hiringSummary = WorkReportsContract.computeHiringDaySummary(byDateCounts, totalRecruited)

        val resolvedRecruiterCount = if (byRecruiter.isNotEmpty()) {
            byRecruiter.count { it.count > 0 }
        } else {
            parsedRows.map { it.recruiterDisplay }.filter { it.isNotBlank() }.distinct().size
        }
        val resolvedProjectCount = if (byProject.isNotEmpty()) {
            byProject.count { it.count > 0 }
        } else {
            parsedRows.map { it.projectDisplay }.filter { it.isNotBlank() }.distinct().size
        }

        val wfSnapshot = json.optJSONObject("workforce")?.optJSONObject("snapshot")
        return TeamReportSnapshot(
            bucketKey = bucketObj.optString("key", bucketKey),
            bucketDisplay = bucketObj.optString("display", bucketKey).ifBlank { "Nhóm tuyển dụng" },
            totalRecruited = totalRecruited,
            rows = parsedRows,
            nextCursor = detailObj.optNullableText("nextCursor"),
            activeRecruiterCount = resolvedRecruiterCount,
            activeProjectCount = resolvedProjectCount,
            activeHiringDays = hiringSummary.activeHiringDays,
            avgPerActiveDay = hiringSummary.avgPerActiveDay,
            peakDailyCount = hiringSummary.peakDailyCount,
            peakDateIso = hiringSummary.peakDateIso,
            workingCount = if (wfSnapshot != null && wfSnapshot.has("onCount")) wfSnapshot.optInt("onCount", 0) else null,
            waitingCount = if (wfSnapshot != null && wfSnapshot.has("unconfirmedCount")) wfSnapshot.optInt("unconfirmedCount", 0) else null,
            leftCount = if (wfSnapshot != null && wfSnapshot.has("offCount")) wfSnapshot.optInt("offCount", 0) else null,
            byRecruiter = byRecruiter,
            byProject = byProject,
            byEmployment = byEmployment,
            byProvider = byProvider
        )
    }

    private data class RawBucketPeriodReport(
        val bucketKey: String,
        val bucketDisplay: String,
        val totalRecruited: Int,
        val byRecruiter: List<BiBucketItem>,
        val byProject: List<BiBucketItem>,
        val byProvider: List<BiBucketItem>,
        val byDateCounts: Map<String, Int>
    )

    suspend fun fetchAggregatedPeriodRecruitmentForAllScope(
        teamBuckets: List<BiBucketItem>,
        knownTeamCodeMap: Map<String, String> = emptyMap(),
        fromDate: String?,
        toDate: String?
    ): PeriodAllScopeRecruitmentSummary? = withContext(Dispatchers.IO) {
        if (fromDate.isNullOrBlank() && toDate.isNullOrBlank()) return@withContext null

        val distinctHints = linkedMapOf<String, BiBucketItem>()
        for (b in teamBuckets) {
            val decoded = WorkReportsContract.decodeTeamBucketCode(b.key).trim()
            if (decoded.isNotEmpty()) {
                distinctHints.putIfAbsent(decoded.lowercase(Locale.ROOT), b)
            }
        }
        distinctHints.putIfAbsent(
            "unknown",
            BiBucketItem(key = "unknown", display = "Không xác định", count = 0, sharePct = 0f)
        )

        val results = coroutineScope {
            distinctHints.values.map { hint ->
                async {
                    fetchSingleBucketPeriodRawOrNull(
                        hint = hint,
                        knownTeamCodeMap = knownTeamCodeMap,
                        fromDate = fromDate,
                        toDate = toDate
                    )
                }
            }.awaitAll()
        }

        val validBuckets = results.filterNotNull()
        if (validBuckets.isEmpty()) return@withContext null
        val totalRecruited = validBuckets.sumOf { it.totalRecruited }

        fun mergeBucketLists(lists: List<List<BiBucketItem>>, denominator: Int): List<BiBucketItem> {
            val acc = linkedMapOf<String, Pair<String, Int>>()
            for (list in lists) {
                for (item in list) {
                    if (item.count <= 0) continue
                    val prev = acc[item.key]
                    acc[item.key] = if (prev == null) {
                        item.display to item.count
                    } else {
                        prev.first to (prev.second + item.count)
                    }
                }
            }
            return acc.entries
                .map { (k, pair) ->
                    val count = pair.second
                    val sharePct = if (denominator > 0) {
                        ((count.toDouble() / denominator.toDouble()) * 1000.0).roundToInt() / 10f
                    } else {
                        0f
                    }
                    BiBucketItem(
                        key = k,
                        display = pair.first,
                        count = count,
                        sharePct = sharePct
                    )
                }
                .sortedWith(compareByDescending<BiBucketItem> { it.count }.thenBy { it.display })
        }

        val mergedByDate = linkedMapOf<String, Int>()
        for (bucket in validBuckets) {
            for ((dateIso, cnt) in bucket.byDateCounts) {
                if (cnt > 0) {
                    mergedByDate[dateIso] = (mergedByDate[dateIso] ?: 0) + cnt
                }
            }
        }

        val byTeam = validBuckets
            .filter { it.totalRecruited > 0 }
            .map { b ->
                val sharePct = if (totalRecruited > 0) {
                    ((b.totalRecruited.toDouble() / totalRecruited.toDouble()) * 1000.0).roundToInt() / 10f
                } else {
                    0f
                }
                BiBucketItem(
                    key = WorkReportsContract.decodeTeamBucketCode(b.bucketKey),
                    display = b.bucketDisplay,
                    count = b.totalRecruited,
                    sharePct = sharePct
                )
            }
            .sortedWith(compareByDescending<BiBucketItem> { it.count }.thenBy { it.display })

        PeriodAllScopeRecruitmentSummary(
            totalRecruited = totalRecruited,
            byRecruiter = mergeBucketLists(validBuckets.map { it.byRecruiter }, totalRecruited),
            byProject = mergeBucketLists(validBuckets.map { it.byProject }, totalRecruited),
            byTeam = byTeam,
            byProvider = mergeBucketLists(validBuckets.map { it.byProvider }, totalRecruited),
            hiringSummary = WorkReportsContract.computeHiringDaySummary(mergedByDate, totalRecruited)
        )
    }

    private suspend fun fetchSingleBucketPeriodRawOrNull(
        hint: BiBucketItem,
        knownTeamCodeMap: Map<String, String>,
        fromDate: String?,
        toDate: String?
    ): RawBucketPeriodReport? {
        val decodedCode = WorkReportsContract.decodeTeamBucketCode(hint.key).trim()
        if (decodedCode.isEmpty()) return null
        val hintDisplayLower = hint.display.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
        val mapped = knownTeamCodeMap[decodedCode.lowercase(Locale.ROOT)]
            ?: hintDisplayLower?.let { knownTeamCodeMap["display:$it"] }
        val candidates = if (!mapped.isNullOrBlank()) {
            listOf(mapped)
        } else {
            WorkReportsContract.candidateTeamCodes(decodedCode)
        }

        for (candidate in candidates) {
            try {
                val bucketKey = WorkReportsContract.encodeTeamBucketKey(candidate)
                val params = mutableListOf(
                    "bucket_key=${encodeParam(bucketKey)}",
                    "limit=1"
                )
                if (!fromDate.isNullOrBlank()) params.add("from=${encodeParam(fromDate.trim())}")
                if (!toDate.isNullOrBlank()) params.add("to=${encodeParam(toDate.trim())}")

                val json = httpClient.getJson(
                    pathWithQuery = "/api/reporting/team-report?${params.joinToString("&")}",
                    requireOkTrue = false,
                    clearSessionOn401 = false
                )
                val bucketObj = json.optJSONObject("bucket") ?: return null
                val detailObj = json.optJSONObject("detail")
                val reportObj = if (json.isNull("report")) null else json.optJSONObject("report")
                val totalRecruited = reportObj?.optInt("recruitedTotal", detailObj?.optInt("total", 0) ?: 0)
                    ?: detailObj?.optInt("total", 0)
                    ?: 0
                val byDateCounts = buildMap {
                    val byDateObj = reportObj?.optJSONObject("byDate")
                    if (byDateObj != null) {
                        val keys = byDateObj.keys()
                        while (keys.hasNext()) {
                            val dateKey = keys.next()
                            val count = byDateObj.optInt(dateKey, 0)
                            if (count > 0) put(dateKey, count)
                        }
                    }
                }
                return RawBucketPeriodReport(
                    bucketKey = bucketObj.optString("key", bucketKey),
                    bucketDisplay = bucketObj.optString("display", hint.display).ifBlank { hint.display },
                    totalRecruited = totalRecruited,
                    byRecruiter = WorkReportsContract.parseReportingBucketRecord(
                        reportObj?.optJSONObject("byRecruiter"),
                        totalRecruited
                    ),
                    byProject = WorkReportsContract.parseReportingBucketRecord(
                        reportObj?.optJSONObject("byProject"),
                        totalRecruited
                    ),
                    byProvider = WorkReportsContract.parseReportingBucketRecord(
                        reportObj?.optJSONObject("byProvider"),
                        totalRecruited
                    ),
                    byDateCounts = byDateCounts
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                if (e.statusCode == 400 && e.backendCode == "INVALID_FILTER") {
                    continue
                }
                return null
            } catch (_: Exception) {
                return null
            }
        }
        return null
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
                    sharePct = sharePct,
                    onCount = if (b.has("onCount") && !b.isNull("onCount")) b.optInt("onCount", 0) else null,
                    unconfirmedCount = if (b.has("unconfirmedCount") && !b.isNull("unconfirmedCount")) b.optInt("unconfirmedCount", 0) else null,
                    offCount = if (b.has("offCount") && !b.isNull("offCount")) b.optInt("offCount", 0) else null
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
                    businessDate = r.optString("business_date", "").ifBlank { r.optString("businessDate", "") },
                    projectDisplay = r.optString("project_display", "").ifBlank { r.optString("projectDisplay", "") },
                    recruiterDisplay = r.optString("recruiter_display", "").ifBlank { r.optString("recruiterDisplay", "") },
                    providerTypeDisplay = r.optString("provider_type_display", "").ifBlank { r.optString("providerTypeDisplay", "") },
                    employmentTypeDisplay = r.optString("employment_type_display", "").ifBlank { r.optString("employmentTypeDisplay", "") },
                    recruitedCount = if (r.has("recruited_count")) r.optInt("recruited_count", 0) else r.optInt("recruitedCount", 0),
                    firstWorkDate = r.optString("first_work_date", "").ifBlank { r.optString("firstWorkDate", "") }
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

private fun JSONObject.optNullablePercentAlreadyScaled(key: String): Float? {
    if (!has(key) || isNull(key)) return null
    val d = optDouble(key, Double.NaN)
    if (d.isNaN() || d.isInfinite()) return null
    return ((d * 100.0).roundToInt()) / 100f
}
