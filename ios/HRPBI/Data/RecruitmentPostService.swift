import CoreFoundation
import Foundation

public struct RecruitmentPostProjectSummary: Equatable, Sendable {
    public let projectId: String
    public let displayName: String

    public init(projectId: String, displayName: String) {
        self.projectId = projectId
        self.displayName = displayName
    }
}

public struct RecruitmentPostIndustrialParkSummary: Equatable, Sendable {
    public let industrialParkId: String
    public let industrialParkCode: String
    public let displayName: String
    public let active: Bool

    public init(
        industrialParkId: String,
        industrialParkCode: String,
        displayName: String,
        active: Bool
    ) {
        self.industrialParkId = industrialParkId
        self.industrialParkCode = industrialParkCode
        self.displayName = displayName
        self.active = active
    }
}

public struct RecruitmentPostProvinceSummary: Equatable, Sendable {
    public let provinceId: String
    public let provinceCode: String
    public let displayName: String

    public init(provinceId: String, provinceCode: String, displayName: String) {
        self.provinceId = provinceId
        self.provinceCode = provinceCode
        self.displayName = displayName
    }
}

public struct RecruitmentPostLaborTypeSummary: Equatable, Sendable {
    public let laborTypeKey: String
    public let displayName: String

    public init(laborTypeKey: String, displayName: String) {
        self.laborTypeKey = laborTypeKey
        self.displayName = displayName
    }
}

public struct RecruitmentPostGeneralSection: Equatable, Sendable {
    public let customerName: String
    public let investmentOrigin: String?
    public let manufacturingIndustry: String?
    public let jobDescription: String
    public let environmentTypes: [String]
    public let workPostures: [String]
    public let shiftPattern: String
    public let shiftDetails: String?
    public let managerName: String
    public let managerPhone: String
    public let laborTypes: [RecruitmentPostLaborTypeSummary]
    public let supplyScheduleText: String?
    public let expectedHeadcount: Int?
}

public struct RecruitmentPostCandidateSection: Equatable, Sendable {
    public let ageMin: Int?
    public let ageMax: Int?
    public let genderRequirement: String
    public let educationRequirement: String
    public let experienceRequired: Bool
    public let experienceDetails: String?
    public let acceptFormerCustomerWorkers: Bool
    public let entryAssessmentText: String?
    public let otherRequirementsText: String?
}

public struct RecruitmentPostCompensationSection: Equatable, Sendable {
    public let baseSalaryVnd: String?
    public let baseSalaryConditions: String?
    public let attendanceBonusVnd: String?
    public let attendanceBonusConditions: String?
    public let livingAllowanceVnd: String?
    public let livingAllowanceConditions: String?
    public let seniorityAllowanceVnd: String?
    public let seniorityAllowanceConditions: String?
    public let eyesightAllowanceVnd: String?
    public let eyesightAllowanceConditions: String?
    public let otherAllowanceText: String?
    public let averageIncomeMinVnd: String?
    public let averageIncomeMaxVnd: String?
    public let salaryPaymentSchedule: String?
    public let salaryAdvanceAvailable: Bool
    public let salaryAdvanceAmountVnd: String?
    public let salaryAdvanceSchedule: String?
    public let weeklySalaryAvailable: Bool
    public let weeklySalaryPaymentSchedule: String?
    public let dayShift8hVnd: String?
    public let dayShift11hVnd: String?
    public let nightShift8hVnd: String?
    public let nightShift11hVnd: String?
    public let sundayDay11hVnd: String?
    public let sundayNight11hVnd: String?
    public let holidayDay11hVnd: String?
    public let holidayNight11hVnd: String?
    public let ppeProvided: Bool
    public let ppeDetails: String?
    public let otherBenefits: String?
}

public struct RecruitmentPostDocumentsSection: Equatable, Sendable {
    public let requireOriginalCccdAndCopy: Bool
    public let requireCertifiedResume: Bool
    public let requireHealthCertificateWithin6Months: Bool
    public let requirePhoto3x4: Bool
    public let requireDegreeOrCertificate: Bool
    public let requireSocialInsuranceBook: Bool
    public let requireOtherDocuments: Bool
    public let otherDocumentsText: String?
}

public struct RecruitmentPostAllowedActions: Equatable, Sendable {
    public let canEdit: Bool
    public let canWithdraw: Bool
}

public struct RecruitmentPostItem: Equatable, Identifiable, Sendable {
    public var id: String { briefId }
    public let briefId: String
    public let version: Int
    public let status: String
    public let project: RecruitmentPostProjectSummary
    public let industrialPark: RecruitmentPostIndustrialParkSummary
    public let province: RecruitmentPostProvinceSummary
    public let publishedAt: String
    public let updatedAt: String
    public let withdrawnAt: String?
    public let createdByDisplayName: String
    public let updatedByDisplayName: String
    public let withdrawnByDisplayName: String?
    public let general: RecruitmentPostGeneralSection
    public let candidate: RecruitmentPostCandidateSection
    public let compensation: RecruitmentPostCompensationSection
    public let documents: RecruitmentPostDocumentsSection
    public let allowedActions: RecruitmentPostAllowedActions
}

public struct RecruitmentPostListResult: Equatable, Sendable {
    public let items: [RecruitmentPostItem]
    public let pageSize: Int
    public let hasMore: Bool
    public let nextCursor: String?
    public let snapshotToken: String
}

public struct RecruitmentPostCardViewModel: Equatable, Identifiable, Sendable {
    public var id: String { briefId }
    public let briefId: String
    public let version: Int
    public let projectName: String
    public let customerNameDisplay: String
    public let provinceName: String
    public let industrialParkName: String
    public let locationDisplay: String
    public let jobDescriptionDisplay: String
    public let laborTypesDisplay: String
    public let expectedHeadcountDisplay: String
    public let genderDisplay: String
    public let ageRangeDisplay: String
    public let genderAndAgeDisplay: String
    public let baseSalaryDisplay: String
    public let incomeRangeDisplay: String
    public let baseSalaryAndIncomeDisplay: String
    public let contactPersonDisplay: String
    public let contactPhoneDisplay: String
    public let contactDisplay: String
    public let createdByDisplayName: String
    public let updatedAtDisplay: String
}

public struct RecruitmentPostDetailGeneralViewModel: Equatable, Sendable {
    public let projectName: String
    public let industrialParkName: String
    public let provinceName: String
    public let customerNameDisplay: String
    public let investmentOriginDisplay: String
    public let manufacturingIndustryDisplay: String
    public let jobDescriptionDisplay: String
    public let environmentTypesDisplay: String
    public let workPosturesDisplay: String
    public let shiftPatternDisplay: String
    public let shiftDetailsDisplay: String
    public let contactPersonDisplay: String
    public let contactPhoneDisplay: String
    public let laborTypesDisplay: String
    public let supplyScheduleTextDisplay: String
    public let expectedHeadcountDisplay: String
}

public struct RecruitmentPostDetailCandidateViewModel: Equatable, Sendable {
    public let ageFromDisplay: String
    public let ageToDisplay: String
    public let ageRangeDisplay: String
    public let genderDisplay: String
    public let minimumEducationDisplay: String
    public let experienceRequiredDisplay: String
    public let experienceDetailsDisplay: String
    public let acceptsFormerWorkersDisplay: String
    public let entryTestFormatDisplay: String
    public let otherRequirementsDisplay: String
}

public struct RecruitmentPostDetailCompensationViewModel: Equatable, Sendable {
    public let baseSalaryVndDisplay: String
    public let baseSalaryConditionsDisplay: String
    public let attendanceBonusVndDisplay: String
    public let attendanceBonusConditionsDisplay: String
    public let livingAllowanceVndDisplay: String
    public let livingAllowanceConditionsDisplay: String
    public let seniorityAllowanceVndDisplay: String
    public let seniorityAllowanceConditionsDisplay: String
    public let eyesightAllowanceVndDisplay: String
    public let eyesightAllowanceConditionsDisplay: String
    public let otherAllowanceTextDisplay: String
    public let averageIncomeMinVndDisplay: String
    public let averageIncomeMaxVndDisplay: String
    public let incomeRangeDisplay: String
    public let salaryPaymentScheduleDisplay: String
    public let salaryAdvanceAvailableDisplay: String
    public let salaryAdvanceAmountVndDisplay: String
    public let salaryAdvanceScheduleDisplay: String
    public let weeklySalaryAvailableDisplay: String
    public let weeklySalaryPaymentScheduleDisplay: String
    public let dayShift8hVndDisplay: String
    public let dayShift11hVndDisplay: String
    public let nightShift8hVndDisplay: String
    public let nightShift11hVndDisplay: String
    public let sundayDay11hVndDisplay: String
    public let sundayNight11hVndDisplay: String
    public let holidayDay11hVndDisplay: String
    public let holidayNight11hVndDisplay: String
    public let ppeProvidedDisplay: String
    public let ppeDetailsDisplay: String
    public let otherBenefitsDisplay: String
}

public struct RecruitmentPostDetailDocumentsViewModel: Equatable, Sendable {
    public let requireOriginalCccdAndCopy: Bool
    public let requireOriginalCccdAndCopyDisplay: String
    public let requireCertifiedResume: Bool
    public let requireCertifiedResumeDisplay: String
    public let requireHealthCertificateWithin6Months: Bool
    public let requireHealthCertificateWithin6MonthsDisplay: String
    public let requirePhoto3x4: Bool
    public let requirePhoto3x4Display: String
    public let requireDegreeOrCertificate: Bool
    public let requireDegreeOrCertificateDisplay: String
    public let requireSocialInsuranceBook: Bool
    public let requireSocialInsuranceBookDisplay: String
    public let requireOtherDocuments: Bool
    public let requireOtherDocumentsDisplay: String
    public let otherDocumentsTextDisplay: String
}

public struct RecruitmentPostDetailViewModel: Equatable, Sendable {
    public let briefId: String
    public let version: Int
    public let status: String
    public let createdByDisplayName: String
    public let updatedByDisplayName: String
    public let publishedAtDisplay: String
    public let updatedAtDisplay: String
    public let section1General: RecruitmentPostDetailGeneralViewModel
    public let section2CandidateRequirements: RecruitmentPostDetailCandidateViewModel
    public let section3CompensationBenefits: RecruitmentPostDetailCompensationViewModel
    public let section4ApplicationDocuments: RecruitmentPostDetailDocumentsViewModel
}

public enum RecruitmentPostContractError: Error, LocalizedError, Equatable {
    case invalidContract(String)

    public var errorDescription: String? {
        switch self {
        case .invalidContract(let reason):
            return reason
        }
    }
}

public enum RecruitmentPostContract {
    public static let defaultPageSize = 10
    public static let maxPageSize = 10
    public static let ageMin = 15
    public static let ageMax = 70
    public static let maxExpectedHeadcount = 1_000_000
    public static let unprovidedLabel = "Chưa cung cấp"

    private static let uuidRegex = try! NSRegularExpression(
        pattern: "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )
    private static let projectIdRegex = try! NSRegularExpression(
        pattern: "^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$"
    )
    private static let vndStringRegex = try! NSRegularExpression(pattern: "^(0|[1-9][0-9]*)$")
    private static let laborTypeKeyRegex = try! NSRegularExpression(pattern: "^[A-Za-z0-9][A-Za-z0-9_]{0,63}$")
    private static let locationCodeRegex = try! NSRegularExpression(pattern: "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
    private static let isoTimestampRegex = try! NSRegularExpression(
        pattern: "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$"
    )
    private static let isoFractionRegex = try! NSRegularExpression(
        pattern: "\\.(\\d{1,9})(?=Z|[+-])"
    )
    private static let maxSafeVndString = "9007199254740991"

    public static let environmentTypeLabels: [String: String] = [
        "CLEAN_ROOM": "Phòng sạch",
        "NORMAL_ROOM": "Phòng thường",
        "SEMI_CLEAN_ROOM": "Phòng bán sạch"
    ]

    public static let workPostureLabels: [String: String] = [
        "STANDING": "Đứng",
        "SITTING": "Ngồi"
    ]

    public static let shiftPatternLabels: [String: String] = [
        "DAY_ONLY": "Chuyên ngày",
        "TWO_SHIFTS": "Hai ca",
        "THREE_SHIFTS": "Ba ca",
        "OTHER": "Khác"
    ]

    public static let genderRequirementLabels: [String: String] = [
        "FEMALE": "Nữ",
        "MALE": "Nam",
        "ANY": "Cặp Nam/Nữ"
    ]

    public static let educationRequirementLabels: [String: String] = [
        "SECONDARY": "THCS (Cấp 2)",
        "HIGH_SCHOOL": "THPT (Cấp 3)",
        "NONE": "Không yêu cầu"
    ]

    private static let postItemKeys: Set<String> = [
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
    ]

    private static let projectKeys: Set<String> = ["project_id", "display_name"]
    private static let industrialParkKeys: Set<String> = [
        "industrial_park_id",
        "industrial_park_code",
        "display_name",
        "active"
    ]
    private static let provinceKeys: Set<String> = ["province_id", "province_code", "display_name"]
    private static let laborTypeKeys: Set<String> = ["labor_type_key", "display_name"]
    private static let allowedActionsKeys: Set<String> = ["can_edit", "can_withdraw"]

    private static let generalKeys: Set<String> = [
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
    ]

    private static let candidateKeys: Set<String> = [
        "age_min",
        "age_max",
        "gender_requirement",
        "education_requirement",
        "experience_required",
        "experience_details",
        "accept_former_customer_workers",
        "entry_assessment_text",
        "other_requirements_text"
    ]

    private static let compensationKeys: Set<String> = [
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
    ]

    private static let documentsKeys: Set<String> = [
        "require_original_cccd_and_copy",
        "require_certified_resume",
        "require_health_certificate_within_6_months",
        "require_photo_3x4",
        "require_degree_or_certificate",
        "require_social_insurance_book",
        "require_other_documents",
        "other_documents_text"
    ]

    private static let listDataKeys: Set<String> = [
        "items",
        "page_size",
        "has_more",
        "next_cursor",
        "snapshot_token"
    ]

    private static let detailDataKeys: Set<String> = ["post"]

    public static func errorMessageForCode(_ code: String?) -> String {
        switch code?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "UNAUTHENTICATED":
            return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        case "ACTOR_NOT_AVAILABLE":
            return "Tài khoản hiện không khả dụng."
        case "RECRUITMENT_POST_ACCESS_DENIED":
            return "Bạn không có quyền truy cập mục Tin tuyển dụng."
        case "RECRUITMENT_POST_NOT_FOUND":
            return "Tin tuyển dụng không còn hiệu lực."
        case "RECRUITMENT_POST_SNAPSHOT_INVALID":
            return "Danh sách tin tuyển dụng đã thay đổi, đang làm mới dữ liệu."
        case "RECRUITMENT_POST_UNAVAILABLE":
            return "Không thể tải dữ liệu tin tuyển dụng lúc này. Vui lòng thử lại."
        default:
            return "Không thể tải dữ liệu tin tuyển dụng lúc này. Vui lòng thử lại."
        }
    }

    public static func buildListPath(
        pageSize: Int = defaultPageSize,
        cursor: String? = nil,
        snapshotToken: String? = nil
    ) -> String {
        let safePageSize = min(max(pageSize, 1), maxPageSize)
        var components = URLComponents()
        components.path = "/api/recruitment-posts"
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "page_size", value: String(safePageSize))
        ]
        let cleanCursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let cleanSnapshot = snapshotToken?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !cleanCursor.isEmpty && !cleanSnapshot.isEmpty {
            queryItems.append(URLQueryItem(name: "snapshot_token", value: cleanSnapshot))
            queryItems.append(URLQueryItem(name: "cursor", value: cleanCursor))
        }
        components.queryItems = queryItems
        if let query = components.percentEncodedQuery, !query.isEmpty {
            return "\(components.path)?\(query)"
        }
        return components.path
    }

    public static func buildDetailPath(briefId: String) -> String {
        let clean = briefId.trimmingCharacters(in: .whitespacesAndNewlines)
        let encoded = clean.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? clean
        return "/api/recruitment-posts/\(encoded)"
    }

    public static func mergeUniqueItems(
        current: [RecruitmentPostItem],
        incoming: [RecruitmentPostItem]
    ) -> [RecruitmentPostItem] {
        var result: [RecruitmentPostItem] = []
        var indexById: [String: Int] = [:]
        for item in current {
            indexById[item.briefId] = result.count
            result.append(item)
        }
        for item in incoming {
            if let existingIdx = indexById[item.briefId] {
                result[existingIdx] = item
            } else {
                indexById[item.briefId] = result.count
                result.append(item)
            }
        }
        return result
    }

    public static func formatText(_ raw: String?) -> String {
        let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? unprovidedLabel : trimmed
    }

    public static func formatVnd(_ canonicalVnd: String?) -> String {
        let trimmed = canonicalVnd?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmed.isEmpty, matchesRegex(trimmed, vndStringRegex) else {
            return unprovidedLabel
        }
        return SalaryAdvanceContract.formatVndAmount(trimmed)
    }

    public static func formatHeadcount(_ headcount: Int?) -> String {
        guard let headcount, headcount >= 0 else { return unprovidedLabel }
        let formatted = SalaryAdvanceContract.formatVndAmount(String(headcount))
        let numberPart = formatted.hasSuffix(" đ")
            ? String(formatted.dropLast(2))
            : String(headcount)
        return "\(numberPart) người"
    }

    public static func formatAgeBound(_ age: Int?) -> String {
        guard let age, age >= ageMin, age <= ageMax else { return unprovidedLabel }
        return "\(age) tuổi"
    }

    public static func formatAgeRange(ageMin minVal: Int?, ageMax maxVal: Int?) -> String {
        let hasMin = minVal != nil && minVal! >= ageMin && minVal! <= ageMax
        let hasMax = maxVal != nil && maxVal! >= ageMin && maxVal! <= ageMax
        if hasMin && hasMax {
            return minVal == maxVal ? "\(minVal!) tuổi" : "\(minVal!) – \(maxVal!) tuổi"
        }
        if hasMin {
            return "Từ \(minVal!) tuổi"
        }
        if hasMax {
            return "Đến \(maxVal!) tuổi"
        }
        return unprovidedLabel
    }

    public static func formatIncomeRange(minVnd: String?, maxVnd: String?) -> String {
        let minFormatted = formatVnd(minVnd)
        let maxFormatted = formatVnd(maxVnd)
        let hasMin = minFormatted != unprovidedLabel
        let hasMax = maxFormatted != unprovidedLabel
        if hasMin && hasMax {
            return minFormatted == maxFormatted ? minFormatted : "\(minFormatted) – \(maxFormatted)"
        }
        if hasMin {
            return "Từ \(minFormatted)"
        }
        if hasMax {
            return "Đến \(maxFormatted)"
        }
        return unprovidedLabel
    }

    public static func formatTimestampVi(_ isoUtc: String?) -> String {
        let trimmed = isoUtc?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmed.isEmpty else { return unprovidedLabel }
        let normalized = normalizeIsoFractionToMillis(trimmed)
        let formatted = SalaryAdvanceContract.formatDateTimeVi(normalized)
        return (formatted == "—" || formatted.isEmpty) ? unprovidedLabel : formatted
    }

    private static func normalizeIsoFractionToMillis(_ iso: String) -> String {
        let nsRange = NSRange(iso.startIndex..<iso.endIndex, in: iso)
        guard let match = isoFractionRegex.firstMatch(in: iso, options: [], range: nsRange),
              let digitsRange = Range(match.range(at: 1), in: iso),
              let fullMatchRange = Range(match.range(at: 0), in: iso) else {
            return iso
        }
        let digits = String(iso[digitsRange])
        let prefix3 = String(digits.prefix(3))
        let padded3 = prefix3.padding(toLength: 3, withPad: "0", startingAt: 0)
        var copy = iso
        copy.replaceSubrange(fullMatchRange, with: ".\(padded3)")
        return copy
    }

    public static func toCardViewModel(_ item: RecruitmentPostItem) -> RecruitmentPostCardViewModel {
        let projectName = formatText(item.project.displayName)
        let customerNameDisplay = formatText(item.general.customerName)
        let provinceName = formatText(item.province.displayName)
        let industrialParkName = formatText(item.industrialPark.displayName)
        let locationDisplay = "\(provinceName) · \(industrialParkName)"
        let jobDescriptionDisplay = formatText(item.general.jobDescription)
        let laborTypesDisplay = item.general.laborTypes.isEmpty
            ? unprovidedLabel
            : item.general.laborTypes.map(\.displayName).joined(separator: ", ")
        let expectedHeadcountDisplay = formatHeadcount(item.general.expectedHeadcount)
        let genderDisplay = genderRequirementLabels[item.candidate.genderRequirement] ?? unprovidedLabel
        let ageRangeDisplay = formatAgeRange(ageMin: item.candidate.ageMin, ageMax: item.candidate.ageMax)
        let genderAndAgeDisplay = "\(genderDisplay) · Độ tuổi: \(ageRangeDisplay)"
        let baseSalaryDisplay = formatVnd(item.compensation.baseSalaryVnd)
        let incomeRangeDisplay = formatIncomeRange(
            minVnd: item.compensation.averageIncomeMinVnd,
            maxVnd: item.compensation.averageIncomeMaxVnd
        )
        let baseSalaryAndIncomeDisplay = "LCB: \(baseSalaryDisplay) · Thu nhập: \(incomeRangeDisplay)"
        // Contact person MUST come from created_by_display_name, NEVER general.manager_name
        let createdByDisplayName = formatText(item.createdByDisplayName)
        let contactPhoneDisplay = formatText(item.general.managerPhone)
        let contactDisplay = "\(createdByDisplayName) · \(contactPhoneDisplay)"
        let updatedAtDisplay = formatTimestampVi(item.updatedAt)

        return RecruitmentPostCardViewModel(
            briefId: item.briefId,
            version: item.version,
            projectName: projectName,
            customerNameDisplay: customerNameDisplay,
            provinceName: provinceName,
            industrialParkName: industrialParkName,
            locationDisplay: locationDisplay,
            jobDescriptionDisplay: jobDescriptionDisplay,
            laborTypesDisplay: laborTypesDisplay,
            expectedHeadcountDisplay: expectedHeadcountDisplay,
            genderDisplay: genderDisplay,
            ageRangeDisplay: ageRangeDisplay,
            genderAndAgeDisplay: genderAndAgeDisplay,
            baseSalaryDisplay: baseSalaryDisplay,
            incomeRangeDisplay: incomeRangeDisplay,
            baseSalaryAndIncomeDisplay: baseSalaryAndIncomeDisplay,
            contactPersonDisplay: createdByDisplayName,
            contactPhoneDisplay: contactPhoneDisplay,
            contactDisplay: contactDisplay,
            createdByDisplayName: createdByDisplayName,
            updatedAtDisplay: updatedAtDisplay
        )
    }

    public static func toDetailViewModel(_ item: RecruitmentPostItem) -> RecruitmentPostDetailViewModel {
        let environmentTypesDisplay = item.general.environmentTypes.isEmpty
            ? unprovidedLabel
            : item.general.environmentTypes.map { environmentTypeLabels[$0] ?? $0 }.joined(separator: ", ")
        let workPosturesDisplay = item.general.workPostures.isEmpty
            ? unprovidedLabel
            : item.general.workPostures.map { workPostureLabels[$0] ?? $0 }.joined(separator: ", ")
        let shiftPatternDisplay = shiftPatternLabels[item.general.shiftPattern] ?? unprovidedLabel
        let laborTypesDisplay = item.general.laborTypes.isEmpty
            ? unprovidedLabel
            : item.general.laborTypes.map(\.displayName).joined(separator: ", ")
        let docRequiredLabel: (Bool) -> String = { $0 ? "Có yêu cầu" : "Không" }
        let createdByDisplayName = formatText(item.createdByDisplayName)

        return RecruitmentPostDetailViewModel(
            briefId: item.briefId,
            version: item.version,
            status: item.status,
            createdByDisplayName: createdByDisplayName,
            updatedByDisplayName: formatText(item.updatedByDisplayName),
            publishedAtDisplay: formatTimestampVi(item.publishedAt),
            updatedAtDisplay: formatTimestampVi(item.updatedAt),
            section1General: RecruitmentPostDetailGeneralViewModel(
                projectName: formatText(item.project.displayName),
                industrialParkName: formatText(item.industrialPark.displayName),
                provinceName: formatText(item.province.displayName),
                customerNameDisplay: formatText(item.general.customerName),
                investmentOriginDisplay: formatText(item.general.investmentOrigin),
                manufacturingIndustryDisplay: formatText(item.general.manufacturingIndustry),
                jobDescriptionDisplay: formatText(item.general.jobDescription),
                environmentTypesDisplay: environmentTypesDisplay,
                workPosturesDisplay: workPosturesDisplay,
                shiftPatternDisplay: shiftPatternDisplay,
                shiftDetailsDisplay: formatText(item.general.shiftDetails),
                // Contact person MUST come from created_by_display_name, NEVER general.manager_name
                contactPersonDisplay: createdByDisplayName,
                contactPhoneDisplay: formatText(item.general.managerPhone),
                laborTypesDisplay: laborTypesDisplay,
                supplyScheduleTextDisplay: formatText(item.general.supplyScheduleText),
                expectedHeadcountDisplay: formatHeadcount(item.general.expectedHeadcount)
            ),
            section2CandidateRequirements: RecruitmentPostDetailCandidateViewModel(
                ageFromDisplay: formatAgeBound(item.candidate.ageMin),
                ageToDisplay: formatAgeBound(item.candidate.ageMax),
                ageRangeDisplay: formatAgeRange(ageMin: item.candidate.ageMin, ageMax: item.candidate.ageMax),
                genderDisplay: genderRequirementLabels[item.candidate.genderRequirement] ?? unprovidedLabel,
                minimumEducationDisplay: educationRequirementLabels[item.candidate.educationRequirement] ?? unprovidedLabel,
                experienceRequiredDisplay: item.candidate.experienceRequired ? "Có" : "Không",
                experienceDetailsDisplay: item.candidate.experienceRequired
                    ? formatText(item.candidate.experienceDetails)
                    : "Không yêu cầu kinh nghiệm",
                acceptsFormerWorkersDisplay: item.candidate.acceptFormerCustomerWorkers ? "Có nhận" : "Không nhận",
                entryTestFormatDisplay: formatText(item.candidate.entryAssessmentText),
                otherRequirementsDisplay: formatText(item.candidate.otherRequirementsText)
            ),
            section3CompensationBenefits: RecruitmentPostDetailCompensationViewModel(
                baseSalaryVndDisplay: formatVnd(item.compensation.baseSalaryVnd),
                baseSalaryConditionsDisplay: formatText(item.compensation.baseSalaryConditions),
                attendanceBonusVndDisplay: formatVnd(item.compensation.attendanceBonusVnd),
                attendanceBonusConditionsDisplay: formatText(item.compensation.attendanceBonusConditions),
                livingAllowanceVndDisplay: formatVnd(item.compensation.livingAllowanceVnd),
                livingAllowanceConditionsDisplay: formatText(item.compensation.livingAllowanceConditions),
                seniorityAllowanceVndDisplay: formatVnd(item.compensation.seniorityAllowanceVnd),
                seniorityAllowanceConditionsDisplay: formatText(item.compensation.seniorityAllowanceConditions),
                eyesightAllowanceVndDisplay: formatVnd(item.compensation.eyesightAllowanceVnd),
                eyesightAllowanceConditionsDisplay: formatText(item.compensation.eyesightAllowanceConditions),
                otherAllowanceTextDisplay: formatText(item.compensation.otherAllowanceText),
                averageIncomeMinVndDisplay: formatVnd(item.compensation.averageIncomeMinVnd),
                averageIncomeMaxVndDisplay: formatVnd(item.compensation.averageIncomeMaxVnd),
                incomeRangeDisplay: formatIncomeRange(
                    minVnd: item.compensation.averageIncomeMinVnd,
                    maxVnd: item.compensation.averageIncomeMaxVnd
                ),
                salaryPaymentScheduleDisplay: formatText(item.compensation.salaryPaymentSchedule),
                salaryAdvanceAvailableDisplay: item.compensation.salaryAdvanceAvailable ? "Có" : "Không",
                salaryAdvanceAmountVndDisplay: item.compensation.salaryAdvanceAvailable
                    ? formatVnd(item.compensation.salaryAdvanceAmountVnd)
                    : unprovidedLabel,
                salaryAdvanceScheduleDisplay: item.compensation.salaryAdvanceAvailable
                    ? formatText(item.compensation.salaryAdvanceSchedule)
                    : unprovidedLabel,
                weeklySalaryAvailableDisplay: item.compensation.weeklySalaryAvailable ? "Có" : "Không",
                weeklySalaryPaymentScheduleDisplay: item.compensation.weeklySalaryAvailable
                    ? formatText(item.compensation.weeklySalaryPaymentSchedule)
                    : unprovidedLabel,
                dayShift8hVndDisplay: formatVnd(item.compensation.dayShift8hVnd),
                dayShift11hVndDisplay: formatVnd(item.compensation.dayShift11hVnd),
                nightShift8hVndDisplay: formatVnd(item.compensation.nightShift8hVnd),
                nightShift11hVndDisplay: formatVnd(item.compensation.nightShift11hVnd),
                sundayDay11hVndDisplay: formatVnd(item.compensation.sundayDay11hVnd),
                sundayNight11hVndDisplay: formatVnd(item.compensation.sundayNight11hVnd),
                holidayDay11hVndDisplay: formatVnd(item.compensation.holidayDay11hVnd),
                holidayNight11hVndDisplay: formatVnd(item.compensation.holidayNight11hVnd),
                ppeProvidedDisplay: item.compensation.ppeProvided ? "Có cấp phát" : "Không",
                ppeDetailsDisplay: formatText(item.compensation.ppeDetails),
                otherBenefitsDisplay: formatText(item.compensation.otherBenefits)
            ),
            section4ApplicationDocuments: RecruitmentPostDetailDocumentsViewModel(
                requireOriginalCccdAndCopy: item.documents.requireOriginalCccdAndCopy,
                requireOriginalCccdAndCopyDisplay: docRequiredLabel(item.documents.requireOriginalCccdAndCopy),
                requireCertifiedResume: item.documents.requireCertifiedResume,
                requireCertifiedResumeDisplay: docRequiredLabel(item.documents.requireCertifiedResume),
                requireHealthCertificateWithin6Months: item.documents.requireHealthCertificateWithin6Months,
                requireHealthCertificateWithin6MonthsDisplay: docRequiredLabel(
                    item.documents.requireHealthCertificateWithin6Months
                ),
                requirePhoto3x4: item.documents.requirePhoto3x4,
                requirePhoto3x4Display: docRequiredLabel(item.documents.requirePhoto3x4),
                requireDegreeOrCertificate: item.documents.requireDegreeOrCertificate,
                requireDegreeOrCertificateDisplay: docRequiredLabel(item.documents.requireDegreeOrCertificate),
                requireSocialInsuranceBook: item.documents.requireSocialInsuranceBook,
                requireSocialInsuranceBookDisplay: docRequiredLabel(item.documents.requireSocialInsuranceBook),
                requireOtherDocuments: item.documents.requireOtherDocuments,
                requireOtherDocumentsDisplay: docRequiredLabel(item.documents.requireOtherDocuments),
                otherDocumentsTextDisplay: item.documents.requireOtherDocuments
                    ? formatText(item.documents.otherDocumentsText)
                    : "Không yêu cầu giấy tờ khác"
            )
        )
    }

    public static func parseListEnvelope(_ root: [String: Any]) throws -> RecruitmentPostListResult {
        guard try requireBoolean(root, "ok") == true else {
            throw RecruitmentPostContractError.invalidContract("Phản hồi danh sách thiếu ok=true.")
        }
        guard let data = root["data"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Phản hồi danh sách thiếu data.")
        }
        return try parseListData(data)
    }

    public static func parseDetailEnvelope(_ root: [String: Any]) throws -> RecruitmentPostItem {
        guard try requireBoolean(root, "ok") == true else {
            throw RecruitmentPostContractError.invalidContract("Phản hồi chi tiết thiếu ok=true.")
        }
        guard let data = root["data"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Phản hồi chi tiết thiếu data.")
        }
        return try parseDetailData(data)
    }

    public static func parseListData(_ data: [String: Any]) throws -> RecruitmentPostListResult {
        try requireExactKeys(data, listDataKeys, label: "list data")
        guard let rawItems = data["items"] as? [Any] else {
            throw RecruitmentPostContractError.invalidContract("Trường items phải là mảng.")
        }
        let pageSize = try requirePositiveInt(data, "page_size", min: 1, max: maxPageSize)
        let hasMore = try requireBoolean(data, "has_more")
        let nextCursor = try requireNullableNonEmptyString(data, "next_cursor")
        let snapshotToken = try requireNonEmptyString(data, "snapshot_token")

        if hasMore && nextCursor == nil {
            throw RecruitmentPostContractError.invalidContract("has_more=true nhưng thiếu next_cursor.")
        }
        if !hasMore && nextCursor != nil {
            throw RecruitmentPostContractError.invalidContract("has_more=false nhưng next_cursor khác null.")
        }
        if rawItems.count > pageSize {
            throw RecruitmentPostContractError.invalidContract("Số lượng items vượt quá page_size.")
        }

        var items: [RecruitmentPostItem] = []
        items.reserveCapacity(rawItems.count)
        for (idx, rawElem) in rawItems.enumerated() {
            guard let itemObj = rawElem as? [String: Any] else {
                throw RecruitmentPostContractError.invalidContract("items[\(idx)] không phải object.")
            }
            let parsed = try parsePostItem(itemObj)
            if parsed.status != "PUBLISHED" {
                throw RecruitmentPostContractError.invalidContract("Danh sách feed chỉ chấp nhận tin PUBLISHED.")
            }
            items.append(parsed)
        }

        return RecruitmentPostListResult(
            items: items,
            pageSize: pageSize,
            hasMore: hasMore,
            nextCursor: nextCursor,
            snapshotToken: snapshotToken
        )
    }

    public static func parseDetailData(_ data: [String: Any]) throws -> RecruitmentPostItem {
        try requireExactKeys(data, detailDataKeys, label: "detail data")
        guard let postObj = data["post"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Trường post không hợp lệ.")
        }
        return try parsePostItem(postObj)
    }

    public static func parsePostItem(_ obj: [String: Any]) throws -> RecruitmentPostItem {
        try requireExactKeys(obj, postItemKeys, label: "post item")
        let briefId = try requireUuid(obj, "brief_id")
        let version = try requirePositiveInt(obj, "version", min: 1, max: Int.max)
        let status = try requireNonEmptyString(obj, "status")
        guard status == "PUBLISHED" || status == "WITHDRAWN" else {
            throw RecruitmentPostContractError.invalidContract("status không hợp lệ: \(status)")
        }

        guard let projectObj = obj["project"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu project.")
        }
        try requireExactKeys(projectObj, projectKeys, label: "project")
        let project = RecruitmentPostProjectSummary(
            projectId: try requireProjectId(projectObj, "project_id"),
            displayName: try requireNonEmptyString(projectObj, "display_name")
        )

        guard let parkObj = obj["industrial_park"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu industrial_park.")
        }
        try requireExactKeys(parkObj, industrialParkKeys, label: "industrial_park")
        let parkCode = try requireNonEmptyString(parkObj, "industrial_park_code")
        guard matchesRegex(parkCode, locationCodeRegex) else {
            throw RecruitmentPostContractError.invalidContract("industrial_park_code không hợp lệ.")
        }
        let industrialPark = RecruitmentPostIndustrialParkSummary(
            industrialParkId: try requireUuid(parkObj, "industrial_park_id"),
            industrialParkCode: parkCode,
            displayName: try requireNonEmptyString(parkObj, "display_name"),
            active: try requireBoolean(parkObj, "active")
        )

        guard let provinceObj = obj["province"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu province.")
        }
        try requireExactKeys(provinceObj, provinceKeys, label: "province")
        let provinceCode = try requireNonEmptyString(provinceObj, "province_code")
        guard matchesRegex(provinceCode, locationCodeRegex) else {
            throw RecruitmentPostContractError.invalidContract("province_code không hợp lệ.")
        }
        let province = RecruitmentPostProvinceSummary(
            provinceId: try requireUuid(provinceObj, "province_id"),
            provinceCode: provinceCode,
            displayName: try requireNonEmptyString(provinceObj, "display_name")
        )

        let publishedAt = try requireIsoTimestamp(obj, "published_at")
        let updatedAt = try requireIsoTimestamp(obj, "updated_at")
        let withdrawnAt = try requireNullableNonEmptyString(obj, "withdrawn_at")
        if let withdrawnAt, !matchesRegex(withdrawnAt, isoTimestampRegex) {
            throw RecruitmentPostContractError.invalidContract("withdrawn_at không đúng định dạng ISO.")
        }
        let createdByDisplayName = try requireNonEmptyString(obj, "created_by_display_name")
        let updatedByDisplayName = try requireNonEmptyString(obj, "updated_by_display_name")
        let withdrawnByDisplayName = try requireNullableNonEmptyString(obj, "withdrawn_by_display_name")

        if status == "PUBLISHED" && (withdrawnAt != nil || withdrawnByDisplayName != nil) {
            throw RecruitmentPostContractError.invalidContract("Tin PUBLISHED không được có thông tin withdrawn.")
        }
        if status == "WITHDRAWN" && (withdrawnAt == nil || withdrawnByDisplayName == nil) {
            throw RecruitmentPostContractError.invalidContract("Tin WITHDRAWN bắt buộc có thông tin withdrawn.")
        }

        guard let generalObj = obj["general"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu general.")
        }
        let general = try parseGeneralSection(generalObj)

        guard let candidateObj = obj["candidate"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu candidate.")
        }
        let candidate = try parseCandidateSection(candidateObj)

        guard let compensationObj = obj["compensation"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu compensation.")
        }
        let compensation = try parseCompensationSection(compensationObj)

        guard let documentsObj = obj["documents"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu documents.")
        }
        let documents = try parseDocumentsSection(documentsObj)

        guard let allowedObj = obj["allowed_actions"] as? [String: Any] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu allowed_actions.")
        }
        try requireExactKeys(allowedObj, allowedActionsKeys, label: "allowed_actions")
        let allowedActions = RecruitmentPostAllowedActions(
            canEdit: try requireBoolean(allowedObj, "can_edit"),
            canWithdraw: try requireBoolean(allowedObj, "can_withdraw")
        )

        return RecruitmentPostItem(
            briefId: briefId,
            version: version,
            status: status,
            project: project,
            industrialPark: industrialPark,
            province: province,
            publishedAt: publishedAt,
            updatedAt: updatedAt,
            withdrawnAt: withdrawnAt,
            createdByDisplayName: createdByDisplayName,
            updatedByDisplayName: updatedByDisplayName,
            withdrawnByDisplayName: withdrawnByDisplayName,
            general: general,
            candidate: candidate,
            compensation: compensation,
            documents: documents,
            allowedActions: allowedActions
        )
    }

    private static func parseGeneralSection(_ obj: [String: Any]) throws -> RecruitmentPostGeneralSection {
        try requireExactKeys(obj, generalKeys, label: "general")
        let customerName = try requireNonEmptyString(obj, "customer_name")
        let investmentOrigin = try requireNullableNonEmptyString(obj, "investment_origin")
        let manufacturingIndustry = try requireNullableNonEmptyString(obj, "manufacturing_industry")
        let jobDescription = try requireNonEmptyString(obj, "job_description")

        guard let rawEnv = obj["environment_types"] as? [Any] else {
            throw RecruitmentPostContractError.invalidContract("environment_types phải là mảng.")
        }
        if rawEnv.count > environmentTypeLabels.count {
            throw RecruitmentPostContractError.invalidContract("environment_types vượt quá số lượng tối đa.")
        }
        var environmentTypes: [String] = []
        var seenEnv = Set<String>()
        for elem in rawEnv {
            guard let str = elem as? String else {
                throw RecruitmentPostContractError.invalidContract("Phần tử environment_types phải là chuỗi.")
            }
            let trimmed = str.trimmingCharacters(in: .whitespacesAndNewlines)
            guard environmentTypeLabels[trimmed] != nil, seenEnv.insert(trimmed).inserted else {
                throw RecruitmentPostContractError.invalidContract("environment_types không hợp lệ hoặc bị trùng.")
            }
            environmentTypes.append(trimmed)
        }

        guard let rawPostures = obj["work_postures"] as? [Any] else {
            throw RecruitmentPostContractError.invalidContract("work_postures phải là mảng.")
        }
        if rawPostures.count > workPostureLabels.count {
            throw RecruitmentPostContractError.invalidContract("work_postures vượt quá số lượng tối đa.")
        }
        var workPostures: [String] = []
        var seenPostures = Set<String>()
        for elem in rawPostures {
            guard let str = elem as? String else {
                throw RecruitmentPostContractError.invalidContract("Phần tử work_postures phải là chuỗi.")
            }
            let trimmed = str.trimmingCharacters(in: .whitespacesAndNewlines)
            guard workPostureLabels[trimmed] != nil, seenPostures.insert(trimmed).inserted else {
                throw RecruitmentPostContractError.invalidContract("work_postures không hợp lệ hoặc bị trùng.")
            }
            workPostures.append(trimmed)
        }

        let shiftPattern = try requireNonEmptyString(obj, "shift_pattern")
        guard shiftPatternLabels[shiftPattern] != nil else {
            throw RecruitmentPostContractError.invalidContract("shift_pattern không hợp lệ.")
        }
        let shiftDetails = try requireNullableNonEmptyString(obj, "shift_details")
        if shiftPattern == "OTHER" && shiftDetails == nil {
            throw RecruitmentPostContractError.invalidContract("shift_pattern=OTHER bắt buộc có shift_details.")
        }

        let managerName = try requireNonEmptyString(obj, "manager_name")
        let managerPhone = try requireNonEmptyString(obj, "manager_phone")

        guard let rawLaborTypes = obj["labor_types"] as? [Any], !rawLaborTypes.isEmpty else {
            throw RecruitmentPostContractError.invalidContract("labor_types phải là mảng không rỗng.")
        }
        var laborTypes: [RecruitmentPostLaborTypeSummary] = []
        var seenLaborKeys = Set<String>()
        for elem in rawLaborTypes {
            guard let ltObj = elem as? [String: Any] else {
                throw RecruitmentPostContractError.invalidContract("Phần tử labor_types phải là object.")
            }
            try requireExactKeys(ltObj, laborTypeKeys, label: "labor_types item")
            let key = try requireNonEmptyString(ltObj, "labor_type_key")
            guard matchesRegex(key, laborTypeKeyRegex), seenLaborKeys.insert(key).inserted else {
                throw RecruitmentPostContractError.invalidContract("labor_type_key không hợp lệ hoặc bị trùng.")
            }
            let displayName = try requireNonEmptyString(ltObj, "display_name")
            laborTypes.append(RecruitmentPostLaborTypeSummary(laborTypeKey: key, displayName: displayName))
        }

        let supplyScheduleText = try requireNullableNonEmptyString(obj, "supply_schedule_text")
        let expectedHeadcount = try requireNullableInt(obj, "expected_headcount", min: 0, max: maxExpectedHeadcount)

        return RecruitmentPostGeneralSection(
            customerName: customerName,
            investmentOrigin: investmentOrigin,
            manufacturingIndustry: manufacturingIndustry,
            jobDescription: jobDescription,
            environmentTypes: environmentTypes,
            workPostures: workPostures,
            shiftPattern: shiftPattern,
            shiftDetails: shiftDetails,
            managerName: managerName,
            managerPhone: managerPhone,
            laborTypes: laborTypes,
            supplyScheduleText: supplyScheduleText,
            expectedHeadcount: expectedHeadcount
        )
    }

    private static func parseCandidateSection(_ obj: [String: Any]) throws -> RecruitmentPostCandidateSection {
        try requireExactKeys(obj, candidateKeys, label: "candidate")
        let minVal = try requireNullableInt(obj, "age_min", min: ageMin, max: ageMax)
        let maxVal = try requireNullableInt(obj, "age_max", min: ageMin, max: ageMax)
        if let minVal, let maxVal, maxVal < minVal {
            throw RecruitmentPostContractError.invalidContract("age_max nhỏ hơn age_min.")
        }

        let genderRequirement = try requireNonEmptyString(obj, "gender_requirement")
        guard genderRequirementLabels[genderRequirement] != nil else {
            throw RecruitmentPostContractError.invalidContract("gender_requirement không hợp lệ.")
        }

        let educationRequirement = try requireNonEmptyString(obj, "education_requirement")
        guard educationRequirementLabels[educationRequirement] != nil else {
            throw RecruitmentPostContractError.invalidContract("education_requirement không hợp lệ.")
        }

        let experienceRequired = try requireBoolean(obj, "experience_required")
        let experienceDetails = try requireNullableNonEmptyString(obj, "experience_details")
        if experienceRequired && experienceDetails == nil {
            throw RecruitmentPostContractError.invalidContract("experience_required=true nhưng thiếu experience_details.")
        }
        if !experienceRequired && experienceDetails != nil {
            throw RecruitmentPostContractError.invalidContract("experience_required=false nhưng experience_details khác null.")
        }

        let acceptFormerCustomerWorkers = try requireBoolean(obj, "accept_former_customer_workers")
        let entryAssessmentText = try requireNullableNonEmptyString(obj, "entry_assessment_text")
        let otherRequirementsText = try requireNullableNonEmptyString(obj, "other_requirements_text")

        return RecruitmentPostCandidateSection(
            ageMin: minVal,
            ageMax: maxVal,
            genderRequirement: genderRequirement,
            educationRequirement: educationRequirement,
            experienceRequired: experienceRequired,
            experienceDetails: experienceDetails,
            acceptFormerCustomerWorkers: acceptFormerCustomerWorkers,
            entryAssessmentText: entryAssessmentText,
            otherRequirementsText: otherRequirementsText
        )
    }

    private static func parseCompensationSection(_ obj: [String: Any]) throws -> RecruitmentPostCompensationSection {
        try requireExactKeys(obj, compensationKeys, label: "compensation")
        let baseSalaryVnd = try requireNullableVndString(obj, "base_salary_vnd")
        let baseSalaryConditions = try requireNullableNonEmptyString(obj, "base_salary_conditions")
        let attendanceBonusVnd = try requireNullableVndString(obj, "attendance_bonus_vnd")
        let attendanceBonusConditions = try requireNullableNonEmptyString(obj, "attendance_bonus_conditions")
        let livingAllowanceVnd = try requireNullableVndString(obj, "living_allowance_vnd")
        let livingAllowanceConditions = try requireNullableNonEmptyString(obj, "living_allowance_conditions")
        let seniorityAllowanceVnd = try requireNullableVndString(obj, "seniority_allowance_vnd")
        let seniorityAllowanceConditions = try requireNullableNonEmptyString(obj, "seniority_allowance_conditions")
        let eyesightAllowanceVnd = try requireNullableVndString(obj, "eyesight_allowance_vnd")
        let eyesightAllowanceConditions = try requireNullableNonEmptyString(obj, "eyesight_allowance_conditions")
        let otherAllowanceText = try requireNullableNonEmptyString(obj, "other_allowance_text")
        let averageIncomeMinVnd = try requireNullableVndString(obj, "average_income_min_vnd")
        let averageIncomeMaxVnd = try requireNullableVndString(obj, "average_income_max_vnd")
        if let minStr = averageIncomeMinVnd, let maxStr = averageIncomeMaxVnd {
            if compareCanonicalVndStrings(maxStr, minStr) < 0 {
                throw RecruitmentPostContractError.invalidContract("average_income_max_vnd nhỏ hơn average_income_min_vnd.")
            }
        }
        let salaryPaymentSchedule = try requireNullableNonEmptyString(obj, "salary_payment_schedule")
        let salaryAdvanceAvailable = try requireBoolean(obj, "salary_advance_available")
        let salaryAdvanceAmountVnd = try requireNullableVndString(obj, "salary_advance_amount_vnd")
        let salaryAdvanceSchedule = try requireNullableNonEmptyString(obj, "salary_advance_schedule")
        if !salaryAdvanceAvailable && (salaryAdvanceAmountVnd != nil || salaryAdvanceSchedule != nil) {
            throw RecruitmentPostContractError.invalidContract("salary_advance_available=false nhưng có thông tin ứng lương.")
        }
        let weeklySalaryAvailable = try requireBoolean(obj, "weekly_salary_available")
        let weeklySalaryPaymentSchedule = try requireNullableNonEmptyString(obj, "weekly_salary_payment_schedule")
        if !weeklySalaryAvailable && weeklySalaryPaymentSchedule != nil {
            throw RecruitmentPostContractError.invalidContract("weekly_salary_available=false nhưng có lịch trả lương tuần.")
        }
        let dayShift8hVnd = try requireNullableVndString(obj, "day_shift_8h_vnd")
        let dayShift11hVnd = try requireNullableVndString(obj, "day_shift_11h_vnd")
        let nightShift8hVnd = try requireNullableVndString(obj, "night_shift_8h_vnd")
        let nightShift11hVnd = try requireNullableVndString(obj, "night_shift_11h_vnd")
        let sundayDay11hVnd = try requireNullableVndString(obj, "sunday_day_11h_vnd")
        let sundayNight11hVnd = try requireNullableVndString(obj, "sunday_night_11h_vnd")
        let holidayDay11hVnd = try requireNullableVndString(obj, "holiday_day_11h_vnd")
        let holidayNight11hVnd = try requireNullableVndString(obj, "holiday_night_11h_vnd")
        let ppeProvided = try requireBoolean(obj, "ppe_provided")
        let ppeDetails = try requireNullableNonEmptyString(obj, "ppe_details")
        let otherBenefits = try requireNullableNonEmptyString(obj, "other_benefits")

        return RecruitmentPostCompensationSection(
            baseSalaryVnd: baseSalaryVnd,
            baseSalaryConditions: baseSalaryConditions,
            attendanceBonusVnd: attendanceBonusVnd,
            attendanceBonusConditions: attendanceBonusConditions,
            livingAllowanceVnd: livingAllowanceVnd,
            livingAllowanceConditions: livingAllowanceConditions,
            seniorityAllowanceVnd: seniorityAllowanceVnd,
            seniorityAllowanceConditions: seniorityAllowanceConditions,
            eyesightAllowanceVnd: eyesightAllowanceVnd,
            eyesightAllowanceConditions: eyesightAllowanceConditions,
            otherAllowanceText: otherAllowanceText,
            averageIncomeMinVnd: averageIncomeMinVnd,
            averageIncomeMaxVnd: averageIncomeMaxVnd,
            salaryPaymentSchedule: salaryPaymentSchedule,
            salaryAdvanceAvailable: salaryAdvanceAvailable,
            salaryAdvanceAmountVnd: salaryAdvanceAmountVnd,
            salaryAdvanceSchedule: salaryAdvanceSchedule,
            weeklySalaryAvailable: weeklySalaryAvailable,
            weeklySalaryPaymentSchedule: weeklySalaryPaymentSchedule,
            dayShift8hVnd: dayShift8hVnd,
            dayShift11hVnd: dayShift11hVnd,
            nightShift8hVnd: nightShift8hVnd,
            nightShift11hVnd: nightShift11hVnd,
            sundayDay11hVnd: sundayDay11hVnd,
            sundayNight11hVnd: sundayNight11hVnd,
            holidayDay11hVnd: holidayDay11hVnd,
            holidayNight11hVnd: holidayNight11hVnd,
            ppeProvided: ppeProvided,
            ppeDetails: ppeDetails,
            otherBenefits: otherBenefits
        )
    }

    private static func parseDocumentsSection(_ obj: [String: Any]) throws -> RecruitmentPostDocumentsSection {
        try requireExactKeys(obj, documentsKeys, label: "documents")
        let requireOriginalCccdAndCopy = try requireBoolean(obj, "require_original_cccd_and_copy")
        let requireCertifiedResume = try requireBoolean(obj, "require_certified_resume")
        let requireHealthCertificateWithin6Months = try requireBoolean(obj, "require_health_certificate_within_6_months")
        let requirePhoto3x4 = try requireBoolean(obj, "require_photo_3x4")
        let requireDegreeOrCertificate = try requireBoolean(obj, "require_degree_or_certificate")
        let requireSocialInsuranceBook = try requireBoolean(obj, "require_social_insurance_book")
        let requireOtherDocuments = try requireBoolean(obj, "require_other_documents")
        let otherDocumentsText = try requireNullableNonEmptyString(obj, "other_documents_text")

        if requireOtherDocuments && otherDocumentsText == nil {
            throw RecruitmentPostContractError.invalidContract("require_other_documents=true nhưng thiếu other_documents_text.")
        }
        if !requireOtherDocuments && otherDocumentsText != nil {
            throw RecruitmentPostContractError.invalidContract("require_other_documents=false nhưng other_documents_text khác null.")
        }

        return RecruitmentPostDocumentsSection(
            requireOriginalCccdAndCopy: requireOriginalCccdAndCopy,
            requireCertifiedResume: requireCertifiedResume,
            requireHealthCertificateWithin6Months: requireHealthCertificateWithin6Months,
            requirePhoto3x4: requirePhoto3x4,
            requireDegreeOrCertificate: requireDegreeOrCertificate,
            requireSocialInsuranceBook: requireSocialInsuranceBook,
            requireOtherDocuments: requireOtherDocuments,
            otherDocumentsText: otherDocumentsText
        )
    }

    private static func requireExactKeys(_ obj: [String: Any], _ expectedKeys: Set<String>, label: String) throws {
        let actualKeys = Set(obj.keys)
        if actualKeys != expectedKeys {
            throw RecruitmentPostContractError.invalidContract("Cấu trúc \(label) không khớp contract.")
        }
    }

    private static func requireNonEmptyString(_ obj: [String: Any], _ key: String) throws -> String {
        guard let raw = obj[key], !(raw is NSNull) else {
            throw RecruitmentPostContractError.invalidContract("Thiếu trường chuỗi bắt buộc \(key).")
        }
        guard let str = raw as? String else {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) phải là String.")
        }
        let trimmed = str.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) không được rỗng.")
        }
        return trimmed
    }

    private static func requireNullableNonEmptyString(_ obj: [String: Any], _ key: String) throws -> String? {
        guard let raw = obj[key] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu trường \(key).")
        }
        if raw is NSNull {
            return nil
        }
        guard let str = raw as? String else {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) phải là String hoặc null.")
        }
        let trimmed = str.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return nil
        }
        return trimmed
    }

    private static func requireProjectId(_ obj: [String: Any], _ key: String) throws -> String {
        let value = try requireNonEmptyString(obj, key)
        guard matchesRegex(value, projectIdRegex) else {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) không đúng định dạng project_id.")
        }
        return value
    }

    private static func requireUuid(_ obj: [String: Any], _ key: String) throws -> String {
        let value = try requireNonEmptyString(obj, key)
        guard matchesRegex(value, uuidRegex) else {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) không đúng định dạng UUID.")
        }
        return value.lowercased()
    }

    private static func requireIsoTimestamp(_ obj: [String: Any], _ key: String) throws -> String {
        let value = try requireNonEmptyString(obj, key)
        guard matchesRegex(value, isoTimestampRegex) else {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) không đúng định dạng thời gian ISO.")
        }
        return value
    }

    private static func requireBoolean(_ obj: [String: Any], _ key: String) throws -> Bool {
        guard let raw = obj[key], !(raw is NSNull) else {
            throw RecruitmentPostContractError.invalidContract("Thiếu trường boolean \(key).")
        }
        if let num = raw as? NSNumber, CFGetTypeID(num) == CFBooleanGetTypeID() {
            return num.boolValue
        }
        if let boolVal = raw as? Bool {
            return boolVal
        }
        throw RecruitmentPostContractError.invalidContract("Trường \(key) phải là kiểu Bool.")
    }

    private static func requirePositiveInt(_ obj: [String: Any], _ key: String, min: Int, max: Int) throws -> Int {
        guard let val = try requireNullableInt(obj, key, min: min, max: max) else {
            throw RecruitmentPostContractError.invalidContract("Trường \(key) không được null.")
        }
        return val
    }

    private static func requireNullableInt(_ obj: [String: Any], _ key: String, min: Int, max: Int) throws -> Int? {
        guard let raw = obj[key] else {
            throw RecruitmentPostContractError.invalidContract("Thiếu trường \(key).")
        }
        if raw is NSNull {
            return nil
        }
        if let num = raw as? NSNumber {
            if CFGetTypeID(num) == CFBooleanGetTypeID() {
                throw RecruitmentPostContractError.invalidContract("Trường \(key) phải là số nguyên, không phải Bool.")
            }
            let doubleVal = num.doubleValue
            if !doubleVal.isFinite || floor(doubleVal) != doubleVal {
                throw RecruitmentPostContractError.invalidContract("Trường \(key) phải là số nguyên.")
            }
            let intVal = num.intValue
            if intVal < min || intVal > max {
                throw RecruitmentPostContractError.invalidContract("Trường \(key) nằm ngoài khoảng [\(min), \(max)].")
            }
            return intVal
        }
        throw RecruitmentPostContractError.invalidContract("Trường \(key) phải là số nguyên.")
    }

    private static func requireNullableVndString(_ obj: [String: Any], _ key: String) throws -> String? {
        guard let str = try requireNullableNonEmptyString(obj, key) else {
            return nil
        }
        guard str.count <= 16,
              matchesRegex(str, vndStringRegex),
              compareCanonicalVndStrings(str, maxSafeVndString) <= 0 else {
            throw RecruitmentPostContractError.invalidContract("Trường tiền tệ \(key) không hợp lệ.")
        }
        return str
    }

    private static func compareCanonicalVndStrings(_ a: String, _ b: String) -> Int {
        if a.count != b.count {
            return a.count < b.count ? -1 : 1
        }
        if a == b { return 0 }
        return a < b ? -1 : 1
    }

    private static func matchesRegex(_ text: String, _ regex: NSRegularExpression) -> Bool {
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.firstMatch(in: text, options: [], range: range) != nil
    }
}

public final class RecruitmentPostService: @unchecked Sendable {
    public static let shared = RecruitmentPostService(httpClient: MiniBiRepository.shared.httpClient)

    private let httpClient: BackendHttpClient

    public init(httpClient: BackendHttpClient) {
        self.httpClient = httpClient
    }

    public func listRecruitmentPosts(
        pageSize: Int = RecruitmentPostContract.defaultPageSize,
        cursor: String? = nil,
        snapshotToken: String? = nil
    ) async -> MiniBiApiResult<RecruitmentPostListResult> {
        let path = RecruitmentPostContract.buildListPath(
            pageSize: pageSize,
            cursor: cursor,
            snapshotToken: snapshotToken
        )
        let response = await httpClient.request(method: "GET", path: path)
        guard response.isSuccess, let root = response.jsonObject else {
            let code = response.errorCode
            return .failure(
                message: RecruitmentPostContract.errorMessageForCode(code),
                httpStatus: response.statusCode > 0 ? response.statusCode : nil,
                errorCode: code
            )
        }
        do {
            let parsed = try RecruitmentPostContract.parseListEnvelope(root)
            return .success(parsed, statusCode: response.statusCode)
        } catch {
            return .failure(
                message: RecruitmentPostContract.errorMessageForCode("RECRUITMENT_POST_UNAVAILABLE"),
                httpStatus: response.statusCode,
                errorCode: "RECRUITMENT_POST_UNAVAILABLE"
            )
        }
    }

    public func getRecruitmentPostDetail(briefId: String) async -> MiniBiApiResult<RecruitmentPostItem> {
        let cleanBriefId = briefId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanBriefId.isEmpty else {
            return .failure(
                message: RecruitmentPostContract.errorMessageForCode("RECRUITMENT_POST_INPUT_INVALID"),
                httpStatus: 400,
                errorCode: "RECRUITMENT_POST_INPUT_INVALID"
            )
        }
        let path = RecruitmentPostContract.buildDetailPath(briefId: cleanBriefId)
        let response = await httpClient.request(method: "GET", path: path)
        guard response.isSuccess, let root = response.jsonObject else {
            let code = response.errorCode
            return .failure(
                message: RecruitmentPostContract.errorMessageForCode(code),
                httpStatus: response.statusCode > 0 ? response.statusCode : nil,
                errorCode: code
            )
        }
        do {
            let parsed = try RecruitmentPostContract.parseDetailEnvelope(root)
            return .success(parsed, statusCode: response.statusCode)
        } catch {
            return .failure(
                message: RecruitmentPostContract.errorMessageForCode("RECRUITMENT_POST_UNAVAILABLE"),
                httpStatus: response.statusCode,
                errorCode: "RECRUITMENT_POST_UNAVAILABLE"
            )
        }
    }
}
