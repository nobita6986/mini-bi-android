package com.example.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

data class RecruitmentPostProjectSummary(
    val projectId: String,
    val displayName: String
)

data class RecruitmentPostIndustrialParkSummary(
    val industrialParkId: String,
    val industrialParkCode: String,
    val displayName: String,
    val active: Boolean
)

data class RecruitmentPostProvinceSummary(
    val provinceId: String,
    val provinceCode: String,
    val displayName: String
)

data class RecruitmentPostLaborTypeSummary(
    val laborTypeKey: String,
    val displayName: String
)

data class RecruitmentPostGeneralSection(
    val customerName: String,
    val investmentOrigin: String?,
    val manufacturingIndustry: String?,
    val jobDescription: String,
    val environmentTypes: List<String>,
    val workPostures: List<String>,
    val shiftPattern: String,
    val shiftDetails: String?,
    val managerName: String,
    val managerPhone: String,
    val laborTypes: List<RecruitmentPostLaborTypeSummary>,
    val supplyScheduleText: String?,
    val expectedHeadcount: Int?
)

data class RecruitmentPostCandidateSection(
    val ageMin: Int?,
    val ageMax: Int?,
    val genderRequirement: String,
    val educationRequirement: String,
    val experienceRequired: Boolean,
    val experienceDetails: String?,
    val acceptFormerCustomerWorkers: Boolean,
    val entryAssessmentText: String?,
    val otherRequirementsText: String?
)

data class RecruitmentPostCompensationSection(
    val baseSalaryVnd: String?,
    val baseSalaryConditions: String?,
    val attendanceBonusVnd: String?,
    val attendanceBonusConditions: String?,
    val livingAllowanceVnd: String?,
    val livingAllowanceConditions: String?,
    val seniorityAllowanceVnd: String?,
    val seniorityAllowanceConditions: String?,
    val eyesightAllowanceVnd: String?,
    val eyesightAllowanceConditions: String?,
    val otherAllowanceText: String?,
    val averageIncomeMinVnd: String?,
    val averageIncomeMaxVnd: String?,
    val salaryPaymentSchedule: String?,
    val salaryAdvanceAvailable: Boolean,
    val salaryAdvanceAmountVnd: String?,
    val salaryAdvanceSchedule: String?,
    val weeklySalaryAvailable: Boolean,
    val weeklySalaryPaymentSchedule: String?,
    val dayShift8hVnd: String?,
    val dayShift11hVnd: String?,
    val nightShift8hVnd: String?,
    val nightShift11hVnd: String?,
    val sundayDay11hVnd: String?,
    val sundayNight11hVnd: String?,
    val holidayDay11hVnd: String?,
    val holidayNight11hVnd: String?,
    val ppeProvided: Boolean,
    val ppeDetails: String?,
    val otherBenefits: String?
)

data class RecruitmentPostDocumentsSection(
    val requireOriginalCccdAndCopy: Boolean,
    val requireCertifiedResume: Boolean,
    val requireHealthCertificateWithin6Months: Boolean,
    val requirePhoto3x4: Boolean,
    val requireDegreeOrCertificate: Boolean,
    val requireSocialInsuranceBook: Boolean,
    val requireOtherDocuments: Boolean,
    val otherDocumentsText: String?
)

data class RecruitmentPostAllowedActions(
    val canEdit: Boolean,
    val canWithdraw: Boolean
)

data class RecruitmentPostItem(
    val briefId: String,
    val version: Int,
    val status: String,
    val project: RecruitmentPostProjectSummary,
    val industrialPark: RecruitmentPostIndustrialParkSummary,
    val province: RecruitmentPostProvinceSummary,
    val publishedAt: String,
    val updatedAt: String,
    val withdrawnAt: String?,
    val createdByDisplayName: String,
    val updatedByDisplayName: String,
    val withdrawnByDisplayName: String?,
    val general: RecruitmentPostGeneralSection,
    val candidate: RecruitmentPostCandidateSection,
    val compensation: RecruitmentPostCompensationSection,
    val documents: RecruitmentPostDocumentsSection,
    val allowedActions: RecruitmentPostAllowedActions
)

data class RecruitmentPostListResult(
    val items: List<RecruitmentPostItem>,
    val pageSize: Int,
    val hasMore: Boolean,
    val nextCursor: String?,
    val snapshotToken: String,
    val industrialParks: List<RecruitmentPostIndustrialParkSummary> = emptyList()
)

data class RecruitmentPostCardViewModel(
    val briefId: String,
    val version: Int,
    val projectName: String,
    val customerNameDisplay: String,
    val provinceName: String,
    val industrialParkName: String,
    val locationDisplay: String,
    val jobDescriptionDisplay: String,
    val laborTypesDisplay: String,
    val expectedHeadcountDisplay: String,
    val genderDisplay: String,
    val ageRangeDisplay: String,
    val genderAndAgeDisplay: String,
    val baseSalaryDisplay: String,
    val incomeRangeDisplay: String,
    val baseSalaryAndIncomeDisplay: String,
    val contactPersonDisplay: String,
    val contactPhoneDisplay: String,
    val contactDisplay: String,
    val createdByDisplayName: String,
    val updatedAtDisplay: String,
    val industrialParkId: String = "",
    val laborTypes: List<RecruitmentPostLaborTypeSummary> = emptyList()
)

data class RecruitmentPostDetailGeneralViewModel(
    val projectName: String,
    val industrialParkName: String,
    val provinceName: String,
    val customerNameDisplay: String,
    val investmentOriginDisplay: String,
    val manufacturingIndustryDisplay: String,
    val jobDescriptionDisplay: String,
    val environmentTypesDisplay: String,
    val workPosturesDisplay: String,
    val shiftPatternDisplay: String,
    val shiftDetailsDisplay: String,
    val contactPersonDisplay: String,
    val contactPhoneDisplay: String,
    val laborTypesDisplay: String,
    val supplyScheduleTextDisplay: String,
    val expectedHeadcountDisplay: String
)

data class RecruitmentPostDetailCandidateViewModel(
    val ageFromDisplay: String,
    val ageToDisplay: String,
    val ageRangeDisplay: String,
    val genderDisplay: String,
    val minimumEducationDisplay: String,
    val experienceRequiredDisplay: String,
    val experienceDetailsDisplay: String,
    val acceptsFormerWorkersDisplay: String,
    val entryTestFormatDisplay: String,
    val otherRequirementsDisplay: String
)

data class RecruitmentPostDetailCompensationViewModel(
    val baseSalaryVndDisplay: String,
    val baseSalaryConditionsDisplay: String,
    val attendanceBonusVndDisplay: String,
    val attendanceBonusConditionsDisplay: String,
    val livingAllowanceVndDisplay: String,
    val livingAllowanceConditionsDisplay: String,
    val seniorityAllowanceVndDisplay: String,
    val seniorityAllowanceConditionsDisplay: String,
    val eyesightAllowanceVndDisplay: String,
    val eyesightAllowanceConditionsDisplay: String,
    val otherAllowanceTextDisplay: String,
    val averageIncomeMinVndDisplay: String,
    val averageIncomeMaxVndDisplay: String,
    val incomeRangeDisplay: String,
    val salaryPaymentScheduleDisplay: String,
    val salaryAdvanceAvailableDisplay: String,
    val salaryAdvanceAmountVndDisplay: String,
    val salaryAdvanceScheduleDisplay: String,
    val weeklySalaryAvailableDisplay: String,
    val weeklySalaryPaymentScheduleDisplay: String,
    val dayShift8hVndDisplay: String,
    val dayShift11hVndDisplay: String,
    val nightShift8hVndDisplay: String,
    val nightShift11hVndDisplay: String,
    val sundayDay11hVndDisplay: String,
    val sundayNight11hVndDisplay: String,
    val holidayDay11hVndDisplay: String,
    val holidayNight11hVndDisplay: String,
    val ppeProvidedDisplay: String,
    val ppeDetailsDisplay: String,
    val otherBenefitsDisplay: String
)

data class RecruitmentPostDetailDocumentsViewModel(
    val requireOriginalCccdAndCopy: Boolean,
    val requireOriginalCccdAndCopyDisplay: String,
    val requireCertifiedResume: Boolean,
    val requireCertifiedResumeDisplay: String,
    val requireHealthCertificateWithin6Months: Boolean,
    val requireHealthCertificateWithin6MonthsDisplay: String,
    val requirePhoto3x4: Boolean,
    val requirePhoto3x4Display: String,
    val requireDegreeOrCertificate: Boolean,
    val requireDegreeOrCertificateDisplay: String,
    val requireSocialInsuranceBook: Boolean,
    val requireSocialInsuranceBookDisplay: String,
    val requireOtherDocuments: Boolean,
    val requireOtherDocumentsDisplay: String,
    val otherDocumentsTextDisplay: String
)

data class RecruitmentPostDetailViewModel(
    val briefId: String,
    val version: Int,
    val status: String,
    val createdByDisplayName: String,
    val updatedByDisplayName: String,
    val publishedAtDisplay: String,
    val updatedAtDisplay: String,
    val section1General: RecruitmentPostDetailGeneralViewModel,
    val section2CandidateRequirements: RecruitmentPostDetailCandidateViewModel,
    val section3CompensationBenefits: RecruitmentPostDetailCompensationViewModel,
    val section4ApplicationDocuments: RecruitmentPostDetailDocumentsViewModel
)

object RecruitmentPostContract {
    const val DEFAULT_PAGE_SIZE = 10
    const val MAX_PAGE_SIZE = 10
    const val AGE_MIN = 15
    const val AGE_MAX = 70
    const val MAX_EXPECTED_HEADCOUNT = 1_000_000
    const val UNPROVIDED_LABEL = "Chưa cung cấp"

    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )
    private val PROJECT_ID_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$")
    private val VND_STRING_PATTERN = Regex("^(0|[1-9][0-9]*)$")
    private val LABOR_TYPE_KEY_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_]{0,63}$")
    private val LOCATION_CODE_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
    private val ISO_TIMESTAMP_PATTERN = Regex(
        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$"
    )
    private val MAX_SAFE_VND = BigInteger.valueOf(9_007_199_254_740_991L)

    val ENVIRONMENT_TYPE_LABELS: Map<String, String> = mapOf(
        "CLEAN_ROOM" to "Phòng sạch",
        "NORMAL_ROOM" to "Phòng thường",
        "SEMI_CLEAN_ROOM" to "Phòng bán sạch"
    )

    val WORK_POSTURE_LABELS: Map<String, String> = mapOf(
        "STANDING" to "Đứng",
        "SITTING" to "Ngồi"
    )

    val SHIFT_PATTERN_LABELS: Map<String, String> = mapOf(
        "DAY_ONLY" to "Chuyên ngày",
        "TWO_SHIFTS" to "Hai ca",
        "THREE_SHIFTS" to "Ba ca",
        "OTHER" to "Khác"
    )

    val GENDER_REQUIREMENT_LABELS: Map<String, String> = mapOf(
        "FEMALE" to "Nữ",
        "MALE" to "Nam",
        "ANY" to "Nam/Nữ"
    )

    val EDUCATION_REQUIREMENT_LABELS: Map<String, String> = mapOf(
        "SECONDARY" to "THCS (Cấp 2)",
        "HIGH_SCHOOL" to "THPT (Cấp 3)",
        "NONE" to "Không yêu cầu"
    )

    private val POST_ITEM_KEYS = setOf(
        "brief_id",
        "version",
        "status",
        "project",
        "industrial_park",
        "province",
        "published_at",
        "updated_at",
        "withdrawn_at",
        "created_by_display_name",
        "updated_by_display_name",
        "withdrawn_by_display_name",
        "general",
        "candidate",
        "compensation",
        "documents",
        "allowed_actions"
    )

    private val PROJECT_KEYS = setOf("project_id", "display_name")
    private val INDUSTRIAL_PARK_KEYS = setOf("industrial_park_id", "industrial_park_code", "display_name", "active")
    private val PROVINCE_KEYS = setOf("province_id", "province_code", "display_name")
    private val LABOR_TYPE_KEYS = setOf("labor_type_key", "display_name")
    private val ALLOWED_ACTIONS_KEYS = setOf("can_edit", "can_withdraw")

    private val GENERAL_KEYS = setOf(
        "customer_name",
        "investment_origin",
        "manufacturing_industry",
        "job_description",
        "environment_types",
        "work_postures",
        "shift_pattern",
        "shift_details",
        "manager_name",
        "manager_phone",
        "labor_types",
        "supply_schedule_text",
        "expected_headcount"
    )

    private val CANDIDATE_KEYS = setOf(
        "age_min",
        "age_max",
        "gender_requirement",
        "education_requirement",
        "experience_required",
        "experience_details",
        "accept_former_customer_workers",
        "entry_assessment_text",
        "other_requirements_text"
    )

    private val COMPENSATION_KEYS = setOf(
        "base_salary_vnd",
        "base_salary_conditions",
        "attendance_bonus_vnd",
        "attendance_bonus_conditions",
        "living_allowance_vnd",
        "living_allowance_conditions",
        "seniority_allowance_vnd",
        "seniority_allowance_conditions",
        "eyesight_allowance_vnd",
        "eyesight_allowance_conditions",
        "other_allowance_text",
        "average_income_min_vnd",
        "average_income_max_vnd",
        "salary_payment_schedule",
        "salary_advance_available",
        "salary_advance_amount_vnd",
        "salary_advance_schedule",
        "weekly_salary_available",
        "weekly_salary_payment_schedule",
        "day_shift_8h_vnd",
        "day_shift_11h_vnd",
        "night_shift_8h_vnd",
        "night_shift_11h_vnd",
        "sunday_day_11h_vnd",
        "sunday_night_11h_vnd",
        "holiday_day_11h_vnd",
        "holiday_night_11h_vnd",
        "ppe_provided",
        "ppe_details",
        "other_benefits"
    )

    private val DOCUMENTS_KEYS = setOf(
        "require_original_cccd_and_copy",
        "require_certified_resume",
        "require_health_certificate_within_6_months",
        "require_photo_3x4",
        "require_degree_or_certificate",
        "require_social_insurance_book",
        "require_other_documents",
        "other_documents_text"
    )

    private val LIST_DATA_REQUIRED_KEYS = setOf(
        "items",
        "page_size",
        "has_more",
        "next_cursor",
        "snapshot_token"
    )

    private val LIST_DATA_KEYS = setOf(
        "items",
        "page_size",
        "has_more",
        "next_cursor",
        "snapshot_token",
        "industrial_parks"
    )

    private val DETAIL_DATA_KEYS = setOf("post")

    fun errorMessageForCode(code: String?): String {
        return when (code?.trim()?.uppercase(Locale.ROOT)) {
            "UNAUTHENTICATED" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            "ACTOR_NOT_AVAILABLE" -> "Tài khoản hiện không khả dụng."
            "RECRUITMENT_POST_ACCESS_DENIED" -> "Bạn không có quyền truy cập mục Tin tuyển dụng."
            "RECRUITMENT_POST_NOT_FOUND" -> "Tin tuyển dụng không còn hiệu lực."
            "RECRUITMENT_POST_SNAPSHOT_INVALID" -> "Danh sách tin tuyển dụng đã thay đổi, đang làm mới dữ liệu."
            "RECRUITMENT_POST_UNAVAILABLE" -> "Không thể tải dữ liệu tin tuyển dụng lúc này. Vui lòng thử lại."
            else -> "Không thể tải dữ liệu tin tuyển dụng lúc này. Vui lòng thử lại."
        }
    }

    fun buildListPath(
        pageSize: Int = DEFAULT_PAGE_SIZE,
        cursor: String? = null,
        snapshotToken: String? = null
    ): String {
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val cleanCursor = cursor?.trim().orEmpty()
        val cleanSnapshot = snapshotToken?.trim().orEmpty()
        val builder = "https://placeholder.local/api/recruitment-posts"
            .toHttpUrlOrNull()!!
            .newBuilder()
            .addQueryParameter("page_size", safePageSize.toString())
        if (cleanCursor.isNotEmpty() && cleanSnapshot.isNotEmpty()) {
            builder.addQueryParameter("snapshot_token", cleanSnapshot)
            builder.addQueryParameter("cursor", cleanCursor)
        }
        val built = builder.build()
        return buildString {
            append(built.encodedPath)
            built.encodedQuery?.let { query ->
                append('?')
                append(query)
            }
        }
    }

    fun buildDetailPath(briefId: String): String {
        val cleanBriefId = briefId.trim()
        val builder = "https://placeholder.local/api/recruitment-posts"
            .toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegment(cleanBriefId)
            .build()
        return builder.encodedPath
    }

    fun mergeUniqueItems(
        current: List<RecruitmentPostItem>,
        incoming: List<RecruitmentPostItem>
    ): List<RecruitmentPostItem> {
        val result = ArrayList<RecruitmentPostItem>(current.size + incoming.size)
        val indexById = HashMap<String, Int>()
        for (item in current) {
            indexById[item.briefId] = result.size
            result.add(item)
        }
        for (item in incoming) {
            val existingIdx = indexById[item.briefId]
            if (existingIdx != null) {
                result[existingIdx] = item
            } else {
                indexById[item.briefId] = result.size
                result.add(item)
            }
        }
        return result
    }

    fun formatText(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.isNotEmpty()) trimmed else UNPROVIDED_LABEL
    }

    fun formatVnd(canonicalVnd: String?): String {
        val trimmed = canonicalVnd?.trim().orEmpty()
        if (trimmed.isEmpty() || !VND_STRING_PATTERN.matches(trimmed)) {
            return UNPROVIDED_LABEL
        }
        if (trimmed == "0") {
            return "0 đ"
        }
        return SalaryAdvanceContract.formatVndAmount(trimmed)
    }

    fun formatHeadcount(headcount: Int?): String {
        if (headcount == null || headcount < 0) return UNPROVIDED_LABEL
        val nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
        return "${nf.format(headcount)} người"
    }

    fun formatAgeBound(age: Int?): String {
        if (age == null || age < AGE_MIN || age > AGE_MAX) return UNPROVIDED_LABEL
        return "$age tuổi"
    }

    fun formatAgeRange(ageMin: Int?, ageMax: Int?): String {
        val hasMin = ageMin != null && ageMin in AGE_MIN..AGE_MAX
        val hasMax = ageMax != null && ageMax in AGE_MIN..AGE_MAX
        return when {
            hasMin && hasMax -> if (ageMin == ageMax) "$ageMin tuổi" else "$ageMin – $ageMax tuổi"
            hasMin -> "Từ $ageMin tuổi"
            hasMax -> "Đến $ageMax tuổi"
            else -> UNPROVIDED_LABEL
        }
    }

    fun formatIncomeRange(minVnd: String?, maxVnd: String?): String {
        val minFormatted = formatVnd(minVnd)
        val maxFormatted = formatVnd(maxVnd)
        val hasMin = minFormatted != UNPROVIDED_LABEL
        val hasMax = maxFormatted != UNPROVIDED_LABEL
        return when {
            hasMin && hasMax -> if (minFormatted == maxFormatted) minFormatted else "$minFormatted – $maxFormatted"
            hasMin -> "Từ $minFormatted"
            hasMax -> "Đến $maxFormatted"
            else -> UNPROVIDED_LABEL
        }
    }

    fun formatTimestampVi(isoUtc: String?): String {
        val trimmed = isoUtc?.trim().orEmpty()
        if (trimmed.isEmpty()) return UNPROVIDED_LABEL
        val normalized = trimmed.replace(Regex("\\.(\\d{1,9})(?=Z|[+-])")) { match ->
            "." + match.groupValues[1].take(3).padEnd(3, '0')
        }
        val formatted = SalaryAdvanceContract.formatDateTimeVi(normalized)
        return if (formatted == "—" || formatted.isBlank()) UNPROVIDED_LABEL else formatted
    }

    fun toCardViewModel(item: RecruitmentPostItem): RecruitmentPostCardViewModel {
        val projectName = formatText(item.project.displayName)
        val customerNameDisplay = formatText(item.general.customerName)
        val provinceName = formatText(item.province.displayName)
        val industrialParkName = formatText(item.industrialPark.displayName)
        val locationDisplay = "$provinceName · $industrialParkName"
        val jobDescriptionDisplay = formatText(item.general.jobDescription)
        val laborTypesDisplay = if (item.general.laborTypes.isNotEmpty()) {
            item.general.laborTypes.joinToString(", ") { it.displayName }
        } else {
            UNPROVIDED_LABEL
        }
        val expectedHeadcountDisplay = formatHeadcount(item.general.expectedHeadcount)
        val genderDisplay = GENDER_REQUIREMENT_LABELS[item.candidate.genderRequirement] ?: UNPROVIDED_LABEL
        val ageRangeDisplay = formatAgeRange(item.candidate.ageMin, item.candidate.ageMax)
        val genderAndAgeDisplay = "$genderDisplay · Độ tuổi: $ageRangeDisplay"
        val baseSalaryDisplay = formatVnd(item.compensation.baseSalaryVnd)
        val incomeRangeDisplay = formatIncomeRange(
            item.compensation.averageIncomeMinVnd,
            item.compensation.averageIncomeMaxVnd
        )
        val baseSalaryAndIncomeDisplay = "LCB: $baseSalaryDisplay · Thu nhập: $incomeRangeDisplay"
        // Contact person MUST come from created_by_display_name, NEVER general.manager_name
        val createdByDisplayName = formatText(item.createdByDisplayName)
        val contactPhoneDisplay = formatText(item.general.managerPhone)
        val contactDisplay = "$createdByDisplayName · $contactPhoneDisplay"
        val updatedAtDisplay = formatTimestampVi(item.updatedAt)

        return RecruitmentPostCardViewModel(
            briefId = item.briefId,
            version = item.version,
            projectName = projectName,
            customerNameDisplay = customerNameDisplay,
            provinceName = provinceName,
            industrialParkName = industrialParkName,
            locationDisplay = locationDisplay,
            jobDescriptionDisplay = jobDescriptionDisplay,
            laborTypesDisplay = laborTypesDisplay,
            expectedHeadcountDisplay = expectedHeadcountDisplay,
            genderDisplay = genderDisplay,
            ageRangeDisplay = ageRangeDisplay,
            genderAndAgeDisplay = genderAndAgeDisplay,
            baseSalaryDisplay = baseSalaryDisplay,
            incomeRangeDisplay = incomeRangeDisplay,
            baseSalaryAndIncomeDisplay = baseSalaryAndIncomeDisplay,
            contactPersonDisplay = createdByDisplayName,
            contactPhoneDisplay = contactPhoneDisplay,
            contactDisplay = contactDisplay,
            createdByDisplayName = createdByDisplayName,
            updatedAtDisplay = updatedAtDisplay,
            industrialParkId = item.industrialPark.industrialParkId,
            laborTypes = item.general.laborTypes
        )
    }

    fun toDetailViewModel(item: RecruitmentPostItem): RecruitmentPostDetailViewModel {
        val environmentTypesDisplay = if (item.general.environmentTypes.isNotEmpty()) {
            item.general.environmentTypes.joinToString(", ") { code ->
                ENVIRONMENT_TYPE_LABELS[code] ?: code
            }
        } else {
            UNPROVIDED_LABEL
        }
        val workPosturesDisplay = if (item.general.workPostures.isNotEmpty()) {
            item.general.workPostures.joinToString(", ") { code ->
                WORK_POSTURE_LABELS[code] ?: code
            }
        } else {
            UNPROVIDED_LABEL
        }
        val shiftPatternDisplay = SHIFT_PATTERN_LABELS[item.general.shiftPattern] ?: UNPROVIDED_LABEL
        val laborTypesDisplay = if (item.general.laborTypes.isNotEmpty()) {
            item.general.laborTypes.joinToString(", ") { it.displayName }
        } else {
            UNPROVIDED_LABEL
        }
        val docRequiredLabel: (Boolean) -> String = { required ->
            if (required) "Có yêu cầu" else "Không"
        }
        val createdByDisplayName = formatText(item.createdByDisplayName)

        return RecruitmentPostDetailViewModel(
            briefId = item.briefId,
            version = item.version,
            status = item.status,
            createdByDisplayName = createdByDisplayName,
            updatedByDisplayName = formatText(item.updatedByDisplayName),
            publishedAtDisplay = formatTimestampVi(item.publishedAt),
            updatedAtDisplay = formatTimestampVi(item.updatedAt),
            section1General = RecruitmentPostDetailGeneralViewModel(
                projectName = formatText(item.project.displayName),
                industrialParkName = formatText(item.industrialPark.displayName),
                provinceName = formatText(item.province.displayName),
                customerNameDisplay = formatText(item.general.customerName),
                investmentOriginDisplay = formatText(item.general.investmentOrigin),
                manufacturingIndustryDisplay = formatText(item.general.manufacturingIndustry),
                jobDescriptionDisplay = formatText(item.general.jobDescription),
                environmentTypesDisplay = environmentTypesDisplay,
                workPosturesDisplay = workPosturesDisplay,
                shiftPatternDisplay = shiftPatternDisplay,
                shiftDetailsDisplay = formatText(item.general.shiftDetails),
                // Contact person MUST come from created_by_display_name, NEVER general.manager_name
                contactPersonDisplay = createdByDisplayName,
                contactPhoneDisplay = formatText(item.general.managerPhone),
                laborTypesDisplay = laborTypesDisplay,
                supplyScheduleTextDisplay = formatText(item.general.supplyScheduleText),
                expectedHeadcountDisplay = formatHeadcount(item.general.expectedHeadcount)
            ),
            section2CandidateRequirements = RecruitmentPostDetailCandidateViewModel(
                ageFromDisplay = formatAgeBound(item.candidate.ageMin),
                ageToDisplay = formatAgeBound(item.candidate.ageMax),
                ageRangeDisplay = formatAgeRange(item.candidate.ageMin, item.candidate.ageMax),
                genderDisplay = GENDER_REQUIREMENT_LABELS[item.candidate.genderRequirement] ?: UNPROVIDED_LABEL,
                minimumEducationDisplay = EDUCATION_REQUIREMENT_LABELS[item.candidate.educationRequirement]
                    ?: UNPROVIDED_LABEL,
                experienceRequiredDisplay = if (item.candidate.experienceRequired) "Có" else "Không",
                experienceDetailsDisplay = if (item.candidate.experienceRequired) {
                    formatText(item.candidate.experienceDetails)
                } else {
                    "Không yêu cầu kinh nghiệm"
                },
                acceptsFormerWorkersDisplay = if (item.candidate.acceptFormerCustomerWorkers) {
                    "Có nhận"
                } else {
                    "Không nhận"
                },
                entryTestFormatDisplay = formatText(item.candidate.entryAssessmentText),
                otherRequirementsDisplay = formatText(item.candidate.otherRequirementsText)
            ),
            section3CompensationBenefits = RecruitmentPostDetailCompensationViewModel(
                baseSalaryVndDisplay = formatVnd(item.compensation.baseSalaryVnd),
                baseSalaryConditionsDisplay = formatText(item.compensation.baseSalaryConditions),
                attendanceBonusVndDisplay = formatVnd(item.compensation.attendanceBonusVnd),
                attendanceBonusConditionsDisplay = formatText(item.compensation.attendanceBonusConditions),
                livingAllowanceVndDisplay = formatVnd(item.compensation.livingAllowanceVnd),
                livingAllowanceConditionsDisplay = formatText(item.compensation.livingAllowanceConditions),
                seniorityAllowanceVndDisplay = formatVnd(item.compensation.seniorityAllowanceVnd),
                seniorityAllowanceConditionsDisplay = formatText(item.compensation.seniorityAllowanceConditions),
                eyesightAllowanceVndDisplay = formatVnd(item.compensation.eyesightAllowanceVnd),
                eyesightAllowanceConditionsDisplay = formatText(item.compensation.eyesightAllowanceConditions),
                otherAllowanceTextDisplay = formatText(item.compensation.otherAllowanceText),
                averageIncomeMinVndDisplay = formatVnd(item.compensation.averageIncomeMinVnd),
                averageIncomeMaxVndDisplay = formatVnd(item.compensation.averageIncomeMaxVnd),
                incomeRangeDisplay = formatIncomeRange(
                    item.compensation.averageIncomeMinVnd,
                    item.compensation.averageIncomeMaxVnd
                ),
                salaryPaymentScheduleDisplay = formatText(item.compensation.salaryPaymentSchedule),
                salaryAdvanceAvailableDisplay = if (item.compensation.salaryAdvanceAvailable) "Có" else "Không",
                salaryAdvanceAmountVndDisplay = if (item.compensation.salaryAdvanceAvailable) {
                    formatVnd(item.compensation.salaryAdvanceAmountVnd)
                } else {
                    UNPROVIDED_LABEL
                },
                salaryAdvanceScheduleDisplay = if (item.compensation.salaryAdvanceAvailable) {
                    formatText(item.compensation.salaryAdvanceSchedule)
                } else {
                    UNPROVIDED_LABEL
                },
                weeklySalaryAvailableDisplay = if (item.compensation.weeklySalaryAvailable) "Có" else "Không",
                weeklySalaryPaymentScheduleDisplay = if (item.compensation.weeklySalaryAvailable) {
                    formatText(item.compensation.weeklySalaryPaymentSchedule)
                } else {
                    UNPROVIDED_LABEL
                },
                dayShift8hVndDisplay = formatVnd(item.compensation.dayShift8hVnd),
                dayShift11hVndDisplay = formatVnd(item.compensation.dayShift11hVnd),
                nightShift8hVndDisplay = formatVnd(item.compensation.nightShift8hVnd),
                nightShift11hVndDisplay = formatVnd(item.compensation.nightShift11hVnd),
                sundayDay11hVndDisplay = formatVnd(item.compensation.sundayDay11hVnd),
                sundayNight11hVndDisplay = formatVnd(item.compensation.sundayNight11hVnd),
                holidayDay11hVndDisplay = formatVnd(item.compensation.holidayDay11hVnd),
                holidayNight11hVndDisplay = formatVnd(item.compensation.holidayNight11hVnd),
                ppeProvidedDisplay = if (item.compensation.ppeProvided) "Có cấp phát" else "Không",
                ppeDetailsDisplay = formatText(item.compensation.ppeDetails),
                otherBenefitsDisplay = formatText(item.compensation.otherBenefits)
            ),
            section4ApplicationDocuments = RecruitmentPostDetailDocumentsViewModel(
                requireOriginalCccdAndCopy = item.documents.requireOriginalCccdAndCopy,
                requireOriginalCccdAndCopyDisplay = docRequiredLabel(item.documents.requireOriginalCccdAndCopy),
                requireCertifiedResume = item.documents.requireCertifiedResume,
                requireCertifiedResumeDisplay = docRequiredLabel(item.documents.requireCertifiedResume),
                requireHealthCertificateWithin6Months = item.documents.requireHealthCertificateWithin6Months,
                requireHealthCertificateWithin6MonthsDisplay = docRequiredLabel(
                    item.documents.requireHealthCertificateWithin6Months
                ),
                requirePhoto3x4 = item.documents.requirePhoto3x4,
                requirePhoto3x4Display = docRequiredLabel(item.documents.requirePhoto3x4),
                requireDegreeOrCertificate = item.documents.requireDegreeOrCertificate,
                requireDegreeOrCertificateDisplay = docRequiredLabel(item.documents.requireDegreeOrCertificate),
                requireSocialInsuranceBook = item.documents.requireSocialInsuranceBook,
                requireSocialInsuranceBookDisplay = docRequiredLabel(item.documents.requireSocialInsuranceBook),
                requireOtherDocuments = item.documents.requireOtherDocuments,
                requireOtherDocumentsDisplay = docRequiredLabel(item.documents.requireOtherDocuments),
                otherDocumentsTextDisplay = if (item.documents.requireOtherDocuments) {
                    formatText(item.documents.otherDocumentsText)
                } else {
                    "Không yêu cầu giấy tờ khác"
                }
            )
        )
    }

    fun parseListEnvelope(root: JSONObject): RecruitmentPostListResult {
        if (!root.has("ok") || root.opt("ok") != true) {
            throw contractInvalid("Phản hồi danh sách tin tuyển dụng thiếu cờ ok=true.")
        }
        val data = root.optJSONObject("data")
            ?: throw contractInvalid("Phản hồi danh sách tin tuyển dụng thiếu data.")
        return parseListData(data)
    }

    fun parseDetailEnvelope(root: JSONObject): RecruitmentPostItem {
        if (!root.has("ok") || root.opt("ok") != true) {
            throw contractInvalid("Phản hồi chi tiết tin tuyển dụng thiếu cờ ok=true.")
        }
        val data = root.optJSONObject("data")
            ?: throw contractInvalid("Phản hồi chi tiết tin tuyển dụng thiếu data.")
        return parseDetailData(data)
    }

    fun parseListData(data: JSONObject): RecruitmentPostListResult {
        val actualKeys = HashSet<String>()
        val iter = data.keys()
        while (iter.hasNext()) {
            actualKeys.add(iter.next())
        }
        if (!actualKeys.containsAll(LIST_DATA_REQUIRED_KEYS) || !LIST_DATA_KEYS.containsAll(actualKeys)) {
            throw contractInvalid("Cấu trúc list data không khớp contract.")
        }
        val itemsArr = data.optJSONArray("items")
            ?: throw contractInvalid("Trường items phải là mảng.")
        val pageSize = requirePositiveInt(data, "page_size", 1, MAX_PAGE_SIZE)
        val hasMore = requireBoolean(data, "has_more")
        val nextCursor = requireNullableNonEmptyString(data, "next_cursor")
        val snapshotToken = requireNonEmptyString(data, "snapshot_token")

        if (hasMore && nextCursor == null) {
            throw contractInvalid("has_more=true nhưng thiếu next_cursor.")
        }
        if (!hasMore && nextCursor != null) {
            throw contractInvalid("has_more=false nhưng next_cursor khác null.")
        }
        if (itemsArr.length() > pageSize) {
            throw contractInvalid("Số phần tử vượt quá page_size.")
        }

        val items = ArrayList<RecruitmentPostItem>(itemsArr.length())
        for (i in 0 until itemsArr.length()) {
            val itemObj = itemsArr.optJSONObject(i)
                ?: throw contractInvalid("Phần tử items[$i] không phải object.")
            val parsedItem = parsePostItem(itemObj)
            if (parsedItem.status != "PUBLISHED") {
                throw contractInvalid("Danh sách feed chỉ chấp nhận tin PUBLISHED.")
            }
            items.add(parsedItem)
        }

        val industrialParks = if (data.has("industrial_parks") && !data.isNull("industrial_parks")) {
            val parksArr = data.optJSONArray("industrial_parks")
                ?: throw contractInvalid("Trường industrial_parks phải là mảng.")
            val list = ArrayList<RecruitmentPostIndustrialParkSummary>(parksArr.length())
            for (i in 0 until parksArr.length()) {
                val parkObj = parksArr.optJSONObject(i)
                    ?: throw contractInvalid("Phần tử industrial_parks[$i] không phải object.")
                list.add(parseIndustrialParkSummary(parkObj, strictExactKeys = false))
            }
            list
        } else {
            emptyList()
        }

        return RecruitmentPostListResult(
            items = items,
            pageSize = pageSize,
            hasMore = hasMore,
            nextCursor = nextCursor,
            snapshotToken = snapshotToken,
            industrialParks = industrialParks
        )
    }

    private fun parseIndustrialParkSummary(
        parkObj: JSONObject,
        strictExactKeys: Boolean = true
    ): RecruitmentPostIndustrialParkSummary {
        if (strictExactKeys) {
            requireExactKeys(parkObj, INDUSTRIAL_PARK_KEYS, "industrial_park")
        }
        val industrialParkCode = requireNonEmptyString(parkObj, "industrial_park_code")
        if (!LOCATION_CODE_PATTERN.matches(industrialParkCode)) {
            throw contractInvalid("industrial_park_code không hợp lệ.")
        }
        val active = if (parkObj.has("active") && !parkObj.isNull("active")) {
            requireBoolean(parkObj, "active")
        } else if (!strictExactKeys) {
            true
        } else {
            requireBoolean(parkObj, "active")
        }
        return RecruitmentPostIndustrialParkSummary(
            industrialParkId = requireUuid(parkObj, "industrial_park_id"),
            industrialParkCode = industrialParkCode,
            displayName = requireNonEmptyString(parkObj, "display_name"),
            active = active
        )
    }

    fun parseDetailData(data: JSONObject): RecruitmentPostItem {
        requireExactKeys(data, DETAIL_DATA_KEYS, "detail data")
        val postObj = data.optJSONObject("post")
            ?: throw contractInvalid("Trường post không hợp lệ.")
        return parsePostItem(postObj)
    }

    fun parsePostItem(obj: JSONObject): RecruitmentPostItem {
        requireExactKeys(obj, POST_ITEM_KEYS, "post item")
        val briefId = requireUuid(obj, "brief_id")
        val version = requirePositiveInt(obj, "version", 1, Int.MAX_VALUE)
        val status = requireNonEmptyString(obj, "status")
        if (status != "PUBLISHED" && status != "WITHDRAWN") {
            throw contractInvalid("status không hợp lệ: $status")
        }

        val projectObj = obj.optJSONObject("project")
            ?: throw contractInvalid("Thiếu project.")
        requireExactKeys(projectObj, PROJECT_KEYS, "project")
        val project = RecruitmentPostProjectSummary(
            projectId = requireProjectId(projectObj, "project_id"),
            displayName = requireNonEmptyString(projectObj, "display_name")
        )

        val parkObj = obj.optJSONObject("industrial_park")
            ?: throw contractInvalid("Thiếu industrial_park.")
        requireExactKeys(parkObj, INDUSTRIAL_PARK_KEYS, "industrial_park")
        val industrialParkCode = requireNonEmptyString(parkObj, "industrial_park_code")
        if (!LOCATION_CODE_PATTERN.matches(industrialParkCode)) {
            throw contractInvalid("industrial_park_code không hợp lệ.")
        }
        val industrialPark = RecruitmentPostIndustrialParkSummary(
            industrialParkId = requireUuid(parkObj, "industrial_park_id"),
            industrialParkCode = industrialParkCode,
            displayName = requireNonEmptyString(parkObj, "display_name"),
            active = requireBoolean(parkObj, "active")
        )

        val provinceObj = obj.optJSONObject("province")
            ?: throw contractInvalid("Thiếu province.")
        requireExactKeys(provinceObj, PROVINCE_KEYS, "province")
        val provinceCode = requireNonEmptyString(provinceObj, "province_code")
        if (!LOCATION_CODE_PATTERN.matches(provinceCode)) {
            throw contractInvalid("province_code không hợp lệ.")
        }
        val province = RecruitmentPostProvinceSummary(
            provinceId = requireUuid(provinceObj, "province_id"),
            provinceCode = provinceCode,
            displayName = requireNonEmptyString(provinceObj, "display_name")
        )

        val publishedAt = requireIsoTimestamp(obj, "published_at")
        val updatedAt = requireIsoTimestamp(obj, "updated_at")
        val withdrawnAtRaw = requireNullableNonEmptyString(obj, "withdrawn_at")
        if (withdrawnAtRaw != null && !ISO_TIMESTAMP_PATTERN.matches(withdrawnAtRaw)) {
            throw contractInvalid("withdrawn_at không đúng định dạng ISO.")
        }
        val createdByDisplayName = requireNonEmptyString(obj, "created_by_display_name")
        val updatedByDisplayName = requireNonEmptyString(obj, "updated_by_display_name")
        val withdrawnByDisplayName = requireNullableNonEmptyString(obj, "withdrawn_by_display_name")

        if (status == "PUBLISHED" && (withdrawnAtRaw != null || withdrawnByDisplayName != null)) {
            throw contractInvalid("Tin PUBLISHED không được có thông tin withdrawn.")
        }
        if (status == "WITHDRAWN" && (withdrawnAtRaw == null || withdrawnByDisplayName == null)) {
            throw contractInvalid("Tin WITHDRAWN bắt buộc có thông tin withdrawn.")
        }

        val generalObj = obj.optJSONObject("general")
            ?: throw contractInvalid("Thiếu general.")
        val general = parseGeneralSection(generalObj)

        val candidateObj = obj.optJSONObject("candidate")
            ?: throw contractInvalid("Thiếu candidate.")
        val candidate = parseCandidateSection(candidateObj)

        val compensationObj = obj.optJSONObject("compensation")
            ?: throw contractInvalid("Thiếu compensation.")
        val compensation = parseCompensationSection(compensationObj)

        val documentsObj = obj.optJSONObject("documents")
            ?: throw contractInvalid("Thiếu documents.")
        val documents = parseDocumentsSection(documentsObj)

        val allowedObj = obj.optJSONObject("allowed_actions")
            ?: throw contractInvalid("Thiếu allowed_actions.")
        requireExactKeys(allowedObj, ALLOWED_ACTIONS_KEYS, "allowed_actions")
        val allowedActions = RecruitmentPostAllowedActions(
            canEdit = requireBoolean(allowedObj, "can_edit"),
            canWithdraw = requireBoolean(allowedObj, "can_withdraw")
        )

        return RecruitmentPostItem(
            briefId = briefId,
            version = version,
            status = status,
            project = project,
            industrialPark = industrialPark,
            province = province,
            publishedAt = publishedAt,
            updatedAt = updatedAt,
            withdrawnAt = withdrawnAtRaw,
            createdByDisplayName = createdByDisplayName,
            updatedByDisplayName = updatedByDisplayName,
            withdrawnByDisplayName = withdrawnByDisplayName,
            general = general,
            candidate = candidate,
            compensation = compensation,
            documents = documents,
            allowedActions = allowedActions
        )
    }

    private fun parseGeneralSection(obj: JSONObject): RecruitmentPostGeneralSection {
        requireExactKeys(obj, GENERAL_KEYS, "general")
        val customerName = requireNonEmptyString(obj, "customer_name")
        val investmentOrigin = requireNullableNonEmptyString(obj, "investment_origin")
        val manufacturingIndustry = requireNullableNonEmptyString(obj, "manufacturing_industry")
        val jobDescription = requireNonEmptyString(obj, "job_description")

        val envArr = obj.optJSONArray("environment_types")
            ?: throw contractInvalid("environment_types phải là mảng.")
        if (envArr.length() > ENVIRONMENT_TYPE_LABELS.size) {
            throw contractInvalid("environment_types vượt quá số lượng tối đa.")
        }
        val environmentTypes = ArrayList<String>(envArr.length())
        val seenEnv = HashSet<String>()
        for (i in 0 until envArr.length()) {
            val raw = envArr.opt(i) as? String ?: throw contractInvalid("environment_types[$i] phải là chuỗi.")
            val trimmed = raw.trim()
            if (trimmed !in ENVIRONMENT_TYPE_LABELS.keys || !seenEnv.add(trimmed)) {
                throw contractInvalid("environment_types[$i] không hợp lệ hoặc bị trùng.")
            }
            environmentTypes.add(trimmed)
        }

        val postureArr = obj.optJSONArray("work_postures")
            ?: throw contractInvalid("work_postures phải là mảng.")
        if (postureArr.length() > WORK_POSTURE_LABELS.size) {
            throw contractInvalid("work_postures vượt quá số lượng tối đa.")
        }
        val workPostures = ArrayList<String>(postureArr.length())
        val seenPosture = HashSet<String>()
        for (i in 0 until postureArr.length()) {
            val raw = postureArr.opt(i) as? String ?: throw contractInvalid("work_postures[$i] phải là chuỗi.")
            val trimmed = raw.trim()
            if (trimmed !in WORK_POSTURE_LABELS.keys || !seenPosture.add(trimmed)) {
                throw contractInvalid("work_postures[$i] không hợp lệ hoặc bị trùng.")
            }
            workPostures.add(trimmed)
        }

        val shiftPattern = requireNonEmptyString(obj, "shift_pattern")
        if (shiftPattern !in SHIFT_PATTERN_LABELS.keys) {
            throw contractInvalid("shift_pattern không hợp lệ.")
        }
        val shiftDetails = requireNullableNonEmptyString(obj, "shift_details")
        if (shiftPattern == "OTHER" && shiftDetails == null) {
            throw contractInvalid("shift_pattern=OTHER bắt buộc có shift_details.")
        }

        val managerName = requireNonEmptyString(obj, "manager_name")
        val managerPhone = requireNonEmptyString(obj, "manager_phone")

        val laborArr = obj.optJSONArray("labor_types")
            ?: throw contractInvalid("labor_types phải là mảng.")
        if (laborArr.length() == 0) {
            throw contractInvalid("labor_types không được rỗng.")
        }
        val laborTypes = ArrayList<RecruitmentPostLaborTypeSummary>(laborArr.length())
        val seenLaborKeys = HashSet<String>()
        for (i in 0 until laborArr.length()) {
            val ltObj = laborArr.optJSONObject(i)
                ?: throw contractInvalid("labor_types[$i] phải là object.")
            requireExactKeys(ltObj, LABOR_TYPE_KEYS, "labor_types[$i]")
            val key = requireNonEmptyString(ltObj, "labor_type_key")
            if (!LABOR_TYPE_KEY_PATTERN.matches(key) || !seenLaborKeys.add(key)) {
                throw contractInvalid("labor_type_key không hợp lệ hoặc bị trùng.")
            }
            val displayName = requireNonEmptyString(ltObj, "display_name")
            laborTypes.add(RecruitmentPostLaborTypeSummary(laborTypeKey = key, displayName = displayName))
        }

        val supplyScheduleText = requireNullableNonEmptyString(obj, "supply_schedule_text")
        val expectedHeadcount = requireNullableInt(obj, "expected_headcount", 0, MAX_EXPECTED_HEADCOUNT)

        return RecruitmentPostGeneralSection(
            customerName = customerName,
            investmentOrigin = investmentOrigin,
            manufacturingIndustry = manufacturingIndustry,
            jobDescription = jobDescription,
            environmentTypes = environmentTypes,
            workPostures = workPostures,
            shiftPattern = shiftPattern,
            shiftDetails = shiftDetails,
            managerName = managerName,
            managerPhone = managerPhone,
            laborTypes = laborTypes,
            supplyScheduleText = supplyScheduleText,
            expectedHeadcount = expectedHeadcount
        )
    }

    private fun parseCandidateSection(obj: JSONObject): RecruitmentPostCandidateSection {
        requireExactKeys(obj, CANDIDATE_KEYS, "candidate")
        val ageMin = requireNullableInt(obj, "age_min", AGE_MIN, AGE_MAX)
        val ageMax = requireNullableInt(obj, "age_max", AGE_MIN, AGE_MAX)
        if (ageMin != null && ageMax != null && ageMax < ageMin) {
            throw contractInvalid("age_max không được nhỏ hơn age_min.")
        }

        val genderRequirement = requireNonEmptyString(obj, "gender_requirement")
        if (genderRequirement !in GENDER_REQUIREMENT_LABELS.keys) {
            throw contractInvalid("gender_requirement không hợp lệ.")
        }

        val educationRequirement = requireNonEmptyString(obj, "education_requirement")
        if (educationRequirement !in EDUCATION_REQUIREMENT_LABELS.keys) {
            throw contractInvalid("education_requirement không hợp lệ.")
        }

        val experienceRequired = requireBoolean(obj, "experience_required")
        val experienceDetails = requireNullableNonEmptyString(obj, "experience_details")
        if (experienceRequired && experienceDetails == null) {
            throw contractInvalid("experience_required=true nhưng thiếu experience_details.")
        }
        if (!experienceRequired && experienceDetails != null) {
            throw contractInvalid("experience_required=false nhưng experience_details khác null.")
        }

        val acceptFormerCustomerWorkers = requireBoolean(obj, "accept_former_customer_workers")
        val entryAssessmentText = requireNullableNonEmptyString(obj, "entry_assessment_text")
        val otherRequirementsText = requireNullableNonEmptyString(obj, "other_requirements_text")

        return RecruitmentPostCandidateSection(
            ageMin = ageMin,
            ageMax = ageMax,
            genderRequirement = genderRequirement,
            educationRequirement = educationRequirement,
            experienceRequired = experienceRequired,
            experienceDetails = experienceDetails,
            acceptFormerCustomerWorkers = acceptFormerCustomerWorkers,
            entryAssessmentText = entryAssessmentText,
            otherRequirementsText = otherRequirementsText
        )
    }

    private fun parseCompensationSection(obj: JSONObject): RecruitmentPostCompensationSection {
        requireExactKeys(obj, COMPENSATION_KEYS, "compensation")
        val baseSalaryVnd = requireNullableVndString(obj, "base_salary_vnd")
        val baseSalaryConditions = requireNullableNonEmptyString(obj, "base_salary_conditions")
        val attendanceBonusVnd = requireNullableVndString(obj, "attendance_bonus_vnd")
        val attendanceBonusConditions = requireNullableNonEmptyString(obj, "attendance_bonus_conditions")
        val livingAllowanceVnd = requireNullableVndString(obj, "living_allowance_vnd")
        val livingAllowanceConditions = requireNullableNonEmptyString(obj, "living_allowance_conditions")
        val seniorityAllowanceVnd = requireNullableVndString(obj, "seniority_allowance_vnd")
        val seniorityAllowanceConditions = requireNullableNonEmptyString(obj, "seniority_allowance_conditions")
        val eyesightAllowanceVnd = requireNullableVndString(obj, "eyesight_allowance_vnd")
        val eyesightAllowanceConditions = requireNullableNonEmptyString(obj, "eyesight_allowance_conditions")
        val otherAllowanceText = requireNullableNonEmptyString(obj, "other_allowance_text")
        val averageIncomeMinVnd = requireNullableVndString(obj, "average_income_min_vnd")
        val averageIncomeMaxVnd = requireNullableVndString(obj, "average_income_max_vnd")
        if (averageIncomeMinVnd != null && averageIncomeMaxVnd != null) {
            if (BigInteger(averageIncomeMaxVnd) < BigInteger(averageIncomeMinVnd)) {
                throw contractInvalid("average_income_max_vnd nhỏ hơn average_income_min_vnd.")
            }
        }
        val salaryPaymentSchedule = requireNullableNonEmptyString(obj, "salary_payment_schedule")
        val salaryAdvanceAvailable = requireBoolean(obj, "salary_advance_available")
        val salaryAdvanceAmountVnd = requireNullableVndString(obj, "salary_advance_amount_vnd")
        val salaryAdvanceSchedule = requireNullableNonEmptyString(obj, "salary_advance_schedule")
        if (!salaryAdvanceAvailable && (salaryAdvanceAmountVnd != null || salaryAdvanceSchedule != null)) {
            throw contractInvalid("salary_advance_available=false nhưng có thông tin ứng lương.")
        }
        val weeklySalaryAvailable = requireBoolean(obj, "weekly_salary_available")
        val weeklySalaryPaymentSchedule = requireNullableNonEmptyString(obj, "weekly_salary_payment_schedule")
        if (!weeklySalaryAvailable && weeklySalaryPaymentSchedule != null) {
            throw contractInvalid("weekly_salary_available=false nhưng có lịch trả lương tuần.")
        }
        val dayShift8hVnd = requireNullableVndString(obj, "day_shift_8h_vnd")
        val dayShift11hVnd = requireNullableVndString(obj, "day_shift_11h_vnd")
        val nightShift8hVnd = requireNullableVndString(obj, "night_shift_8h_vnd")
        val nightShift11hVnd = requireNullableVndString(obj, "night_shift_11h_vnd")
        val sundayDay11hVnd = requireNullableVndString(obj, "sunday_day_11h_vnd")
        val sundayNight11hVnd = requireNullableVndString(obj, "sunday_night_11h_vnd")
        val holidayDay11hVnd = requireNullableVndString(obj, "holiday_day_11h_vnd")
        val holidayNight11hVnd = requireNullableVndString(obj, "holiday_night_11h_vnd")
        val ppeProvided = requireBoolean(obj, "ppe_provided")
        val ppeDetails = requireNullableNonEmptyString(obj, "ppe_details")
        val otherBenefits = requireNullableNonEmptyString(obj, "other_benefits")

        return RecruitmentPostCompensationSection(
            baseSalaryVnd = baseSalaryVnd,
            baseSalaryConditions = baseSalaryConditions,
            attendanceBonusVnd = attendanceBonusVnd,
            attendanceBonusConditions = attendanceBonusConditions,
            livingAllowanceVnd = livingAllowanceVnd,
            livingAllowanceConditions = livingAllowanceConditions,
            seniorityAllowanceVnd = seniorityAllowanceVnd,
            seniorityAllowanceConditions = seniorityAllowanceConditions,
            eyesightAllowanceVnd = eyesightAllowanceVnd,
            eyesightAllowanceConditions = eyesightAllowanceConditions,
            otherAllowanceText = otherAllowanceText,
            averageIncomeMinVnd = averageIncomeMinVnd,
            averageIncomeMaxVnd = averageIncomeMaxVnd,
            salaryPaymentSchedule = salaryPaymentSchedule,
            salaryAdvanceAvailable = salaryAdvanceAvailable,
            salaryAdvanceAmountVnd = salaryAdvanceAmountVnd,
            salaryAdvanceSchedule = salaryAdvanceSchedule,
            weeklySalaryAvailable = weeklySalaryAvailable,
            weeklySalaryPaymentSchedule = weeklySalaryPaymentSchedule,
            dayShift8hVnd = dayShift8hVnd,
            dayShift11hVnd = dayShift11hVnd,
            nightShift8hVnd = nightShift8hVnd,
            nightShift11hVnd = nightShift11hVnd,
            sundayDay11hVnd = sundayDay11hVnd,
            sundayNight11hVnd = sundayNight11hVnd,
            holidayDay11hVnd = holidayDay11hVnd,
            holidayNight11hVnd = holidayNight11hVnd,
            ppeProvided = ppeProvided,
            ppeDetails = ppeDetails,
            otherBenefits = otherBenefits
        )
    }

    private fun parseDocumentsSection(obj: JSONObject): RecruitmentPostDocumentsSection {
        requireExactKeys(obj, DOCUMENTS_KEYS, "documents")
        val requireOriginalCccdAndCopy = requireBoolean(obj, "require_original_cccd_and_copy")
        val requireCertifiedResume = requireBoolean(obj, "require_certified_resume")
        val requireHealthCertificateWithin6Months = requireBoolean(obj, "require_health_certificate_within_6_months")
        val requirePhoto3x4 = requireBoolean(obj, "require_photo_3x4")
        val requireDegreeOrCertificate = requireBoolean(obj, "require_degree_or_certificate")
        val requireSocialInsuranceBook = requireBoolean(obj, "require_social_insurance_book")
        val requireOtherDocuments = requireBoolean(obj, "require_other_documents")
        val otherDocumentsText = requireNullableNonEmptyString(obj, "other_documents_text")

        if (requireOtherDocuments && otherDocumentsText == null) {
            throw contractInvalid("require_other_documents=true nhưng thiếu other_documents_text.")
        }
        if (!requireOtherDocuments && otherDocumentsText != null) {
            throw contractInvalid("require_other_documents=false nhưng other_documents_text khác null.")
        }

        return RecruitmentPostDocumentsSection(
            requireOriginalCccdAndCopy = requireOriginalCccdAndCopy,
            requireCertifiedResume = requireCertifiedResume,
            requireHealthCertificateWithin6Months = requireHealthCertificateWithin6Months,
            requirePhoto3x4 = requirePhoto3x4,
            requireDegreeOrCertificate = requireDegreeOrCertificate,
            requireSocialInsuranceBook = requireSocialInsuranceBook,
            requireOtherDocuments = requireOtherDocuments,
            otherDocumentsText = otherDocumentsText
        )
    }

    private fun requireExactKeys(obj: JSONObject, expectedKeys: Set<String>, label: String) {
        val actualKeys = HashSet<String>()
        val iter = obj.keys()
        while (iter.hasNext()) {
            actualKeys.add(iter.next())
        }
        if (actualKeys != expectedKeys) {
            throw contractInvalid("Cấu trúc $label không khớp contract.")
        }
    }

    private fun requireNonEmptyString(obj: JSONObject, key: String): String {
        if (!obj.has(key) || obj.isNull(key)) {
            throw contractInvalid("Thiếu trường chuỗi bắt buộc $key.")
        }
        val raw = obj.opt(key)
        if (raw !is String) {
            throw contractInvalid("Trường $key phải là kiểu String.")
        }
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw contractInvalid("Trường $key không được rỗng.")
        }
        return trimmed
    }

    private fun requireNullableNonEmptyString(obj: JSONObject, key: String): String? {
        if (!obj.has(key)) {
            throw contractInvalid("Thiếu trường $key.")
        }
        if (obj.isNull(key)) {
            return null
        }
        val raw = obj.opt(key)
        if (raw !is String) {
            throw contractInvalid("Trường $key phải là String hoặc null.")
        }
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return trimmed
    }

    private fun requireProjectId(obj: JSONObject, key: String): String {
        val value = requireNonEmptyString(obj, key)
        if (!PROJECT_ID_PATTERN.matches(value)) {
            throw contractInvalid("Trường $key không đúng định dạng project_id.")
        }
        return value
    }

    private fun requireUuid(obj: JSONObject, key: String): String {
        val value = requireNonEmptyString(obj, key)
        if (!UUID_PATTERN.matches(value)) {
            throw contractInvalid("Trường $key không đúng định dạng UUID.")
        }
        return value.lowercase(Locale.ROOT)
    }

    private fun requireIsoTimestamp(obj: JSONObject, key: String): String {
        val value = requireNonEmptyString(obj, key)
        if (!ISO_TIMESTAMP_PATTERN.matches(value)) {
            throw contractInvalid("Trường $key không đúng định dạng thời gian ISO.")
        }
        return value
    }

    private fun requireBoolean(obj: JSONObject, key: String): Boolean {
        if (!obj.has(key) || obj.isNull(key)) {
            throw contractInvalid("Thiếu trường boolean $key.")
        }
        val raw = obj.opt(key)
        if (raw !is Boolean) {
            throw contractInvalid("Trường $key phải là kiểu Boolean.")
        }
        return raw
    }

    private fun requirePositiveInt(obj: JSONObject, key: String, min: Int, max: Int): Int {
        val v = requireNullableInt(obj, key, min, max)
            ?: throw contractInvalid("Trường $key không được null.")
        return v
    }

    private fun requireNullableInt(obj: JSONObject, key: String, min: Int, max: Int): Int? {
        if (!obj.has(key)) {
            throw contractInvalid("Thiếu trường $key.")
        }
        if (obj.isNull(key)) {
            return null
        }
        val raw = obj.opt(key)
        val intVal = when (raw) {
            is Int -> raw
            is Long -> {
                if (raw < Int.MIN_VALUE || raw > Int.MAX_VALUE) {
                    throw contractInvalid("Trường $key vượt phạm vi số nguyên.")
                }
                raw.toInt()
            }
            else -> throw contractInvalid("Trường $key phải là số nguyên.")
        }
        if (intVal < min || intVal > max) {
            throw contractInvalid("Trường $key nằm ngoài khoảng [$min, $max].")
        }
        return intVal
    }

    private fun requireNullableVndString(obj: JSONObject, key: String): String? {
        val str = requireNullableNonEmptyString(obj, key) ?: return null
        if (str.length > 16 || !VND_STRING_PATTERN.matches(str)) {
            throw contractInvalid("Trường tiền tệ $key không hợp lệ.")
        }
        val big = runCatching { BigInteger(str) }.getOrNull()
            ?: throw contractInvalid("Trường tiền tệ $key không hợp lệ.")
        if (big < BigInteger.ZERO || big > MAX_SAFE_VND) {
            throw contractInvalid("Trường tiền tệ $key vượt giới hạn an toàn.")
        }
        return str
    }

    private fun contractInvalid(reason: String): BackendApiException {
        return BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "RECRUITMENT_POST_UNAVAILABLE",
            message = errorMessageForCode("RECRUITMENT_POST_UNAVAILABLE"),
            cause = IllegalArgumentException(reason)
        )
    }
}

class RecruitmentPostService(
    private val httpClient: BackendHttpClient
) {
    suspend fun listRecruitmentPosts(
        pageSize: Int = RecruitmentPostContract.DEFAULT_PAGE_SIZE,
        cursor: String? = null,
        snapshotToken: String? = null
    ): RecruitmentPostListResult {
        val path = RecruitmentPostContract.buildListPath(
            pageSize = pageSize,
            cursor = cursor,
            snapshotToken = snapshotToken
        )
        val root = httpClient.getJson(path)
        return RecruitmentPostContract.parseListEnvelope(root)
    }

    suspend fun getRecruitmentPostDetail(briefId: String): RecruitmentPostItem {
        val cleanBriefId = briefId.trim()
        if (cleanBriefId.isEmpty()) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 400,
                backendCode = "RECRUITMENT_POST_INPUT_INVALID",
                message = RecruitmentPostContract.errorMessageForCode("RECRUITMENT_POST_INPUT_INVALID")
            )
        }
        val path = RecruitmentPostContract.buildDetailPath(cleanBriefId)
        val root = httpClient.getJson(path)
        return RecruitmentPostContract.parseDetailEnvelope(root)
    }
}
