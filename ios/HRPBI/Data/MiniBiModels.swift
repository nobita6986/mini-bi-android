import Foundation

// MARK: - Environment & Versioning Configuration

public enum AppEnvironmentVariant: String, CaseIterable, Equatable, Sendable {
    case release = "release"
    case staging = "staging"
}

public struct AppEnvironmentConfig: Equatable, Sendable {
    public let variant: AppEnvironmentVariant
    public let appDisplayName: String
    public let loginBrandTitle: String
    public let brandBadgeLabel: String
    public let defaultServerUrl: String
    public let bundleIdentifier: String
    public let allowDebugUrlOverride: Bool
    public let marketingVersion: String
    public let buildNumber: String

    public static func forVariant(
        _ variant: AppEnvironmentVariant,
        marketingVersion: String = "1.0.1.27102026",
        buildNumber: String = "1"
    ) -> AppEnvironmentConfig {
        switch variant {
        case .release:
            return AppEnvironmentConfig(
                variant: .release,
                appDisplayName: "HRP BI",
                loginBrandTitle: "HRP BI",
                brandBadgeLabel: "BI",
                defaultServerUrl: "https://bi.hrpartner.vn",
                bundleIdentifier: "com.aistudio.minibi.hrpbia",
                allowDebugUrlOverride: false,
                marketingVersion: marketingVersion,
                buildNumber: buildNumber
            )
        case .staging:
            return AppEnvironmentConfig(
                variant: .staging,
                appDisplayName: "HRP BI 2",
                loginBrandTitle: "HRP BI 2",
                brandBadgeLabel: "BI2",
                defaultServerUrl: "https://bi2.hrpartner.vn",
                bundleIdentifier: "com.aistudio.minibi.hrpbia.bi2",
                allowDebugUrlOverride: true,
                marketingVersion: marketingVersion,
                buildNumber: buildNumber
            )
        }
    }

    public static func current(bundle: Bundle = .main) -> AppEnvironmentConfig {
        let info = bundle.infoDictionary ?? [:]
        let marketing = (info["CFBundleShortVersionString"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let build = (info["CFBundleVersion"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedMarketing = (marketing?.isEmpty == false) ? marketing! : "1.0.1.27102026"
        let resolvedBuild = (build?.isEmpty == false) ? build! : "1"

        let bundleId = (bundle.bundleIdentifier ?? (info["CFBundleIdentifier"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let badge = (info["BRAND_BADGE_LABEL"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let host = (info["DEFAULT_SERVER_HOST"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        #if BI2_STAGING
        let defaultVariant: AppEnvironmentVariant = .staging
        #else
        let defaultVariant: AppEnvironmentVariant = .release
        #endif

        let isStaging = bundleId.hasSuffix(".bi2")
            || badge.uppercased() == "BI2"
            || host.lowercased().hasPrefix("bi2.")
            || defaultVariant == .staging

        return forVariant(
            isStaging ? .staging : .release,
            marketingVersion: resolvedMarketing,
            buildNumber: resolvedBuild
        )
    }
}

// MARK: - Saved Login Account

public struct SavedLoginAccount: Codable, Equatable, Identifiable, Sendable {
    public var id: String { identifier.lowercased() }
    public let identifier: String
    public let password: String
    public let displayName: String
    public let lastUsedEpochMs: Int64

    public init(
        identifier: String,
        password: String,
        displayName: String = "",
        lastUsedEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) {
        self.identifier = identifier
        self.password = password
        self.displayName = displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? identifier : displayName
        self.lastUsedEpochMs = lastUsedEpochMs
    }
}

// MARK: - Actor & Session Authorization Contract

public struct AppActor: Codable, Equatable, Sendable {
    public let appUserId: String
    public let email: String
    public let displayName: String
    public let status: String
    public let roleKeys: Set<String>
    public let capabilities: Set<String>
    public let scopeKinds: Set<String>
    public let teamBucketKeys: Set<String>
    public let projectIds: Set<String>
    public let recruiterIds: Set<String>
    public let selfRecruiterSuggestion: String?
    public let isPendingAccess: Bool

    public init(
        appUserId: String,
        email: String,
        displayName: String,
        status: String,
        roleKeys: Set<String>,
        capabilities: Set<String>,
        scopeKinds: Set<String>,
        teamBucketKeys: Set<String> = [],
        projectIds: Set<String> = [],
        recruiterIds: Set<String> = [],
        selfRecruiterSuggestion: String? = nullString,
        isPendingAccess: Bool = false
    ) {
        self.appUserId = appUserId
        self.email = email
        self.displayName = displayName
        self.status = status
        self.roleKeys = roleKeys
        self.capabilities = capabilities
        self.scopeKinds = scopeKinds
        self.teamBucketKeys = teamBucketKeys
        self.projectIds = projectIds
        self.recruiterIds = recruiterIds
        self.selfRecruiterSuggestion = selfRecruiterSuggestion
        self.isPendingAccess = isPendingAccess
    }

    private static let nullString: String? = nil

    public var isActive: Bool {
        status.caseInsensitiveCompare("active") == .orderedSame && !isPendingAccess
    }

    public func hasCapability(_ key: String) -> Bool {
        capabilities.contains(key)
    }

    public func hasScopeKind(_ kind: String) -> Bool {
        scopeKinds.contains(kind)
    }

    public var isAdminRole: Bool {
        roleKeys.contains("system_admin") || roleKeys.contains("admin") ||
            (hasCapability("admin_access") && hasScopeKind("all"))
    }

    public var isDirectorRole: Bool {
        roleKeys.contains("director") || (hasScopeKind("all") && !isAdminRole)
    }

    public var isLeaderRole: Bool {
        roleKeys.contains("leader") || roleKeys.contains("team_leader") || hasScopeKind("team")
    }

    public var canAccessNewWorkerEntry: Bool {
        hasCapability("submission_create") || hasCapability("entry_admin")
    }

    public var canViewWorkersScreen: Bool {
        hasCapability("submission_read") ||
            hasCapability("submission_create") ||
            hasCapability("entry_admin") ||
            hasCapability("change_propose") ||
            hasCapability("change_review") ||
            !scopeKinds.isEmpty
    }

    public var displayRoleLabelVi: String {
        if roleKeys.contains("system_admin") || roleKeys.contains("admin") { return "Quản trị hệ thống" }
        if roleKeys.contains("director") { return "Giám đốc / Ban điều hành" }
        if roleKeys.contains("leader") || roleKeys.contains("team_leader") { return "Trưởng nhóm (Leader)" }
        if roleKeys.contains("project_manager") { return "Quản lý dự án" }
        if roleKeys.contains("recruiter") || roleKeys.contains("specialist") { return "Chuyên viên" }
        if isAdminRole { return "Quản trị hệ thống" }
        if isDirectorRole { return "Ban điều hành" }
        if isLeaderRole { return "Trưởng nhóm" }
        return "Nhân sự vận hành"
    }

    public var primaryScopeDisplayLabelVi: String {
        if hasScopeKind("all") { return "Toàn hệ thống" }
        if hasScopeKind("team") { return "Theo nhóm quản lý" }
        if hasScopeKind("project") { return "Theo dự án phụ trách" }
        if hasScopeKind("self_recruiter") { return "Theo hồ sơ tự tuyển" }
        if hasScopeKind("uploader") { return "Theo đợt tự nhập" }
        return "Cá nhân"
    }

    public static func parseFromSessionPayload(_ root: [String: Any]) -> AppActor? {
        if let authenticated = root["authenticated"] as? Bool, !authenticated {
            return nil
        }
        let actorObj = (root["actor"] as? [String: Any])
            ?? (root["user"] as? [String: Any])
            ?? root
        return parseActorObject(actorObj)
    }

    public static func parseActorObject(_ obj: [String: Any]) -> AppActor? {
        let userId = obj.stringValue(forKeys: ["app_user_id", "user_id", "id"]).trimmingCharacters(in: .whitespacesAndNewlines)
        let email = obj.stringValue(forKeys: ["email", "identifier", "username"]).trimmingCharacters(in: .whitespacesAndNewlines)
        if userId.isEmpty && email.isEmpty {
            return nil
        }
        let fallbackName = email.split(separator: "@").first.map(String.init) ?? userId
        let displayName = obj.stringValue(forKeys: ["display_name", "full_name", "name"], default: fallbackName)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let status = obj.stringValue(forKeys: ["status"], default: "active")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let pendingAccess = (obj["is_pending_access"] as? Bool) ?? (status != "active")

        let roleKeys = parseStringSet(obj["role_keys"] ?? obj["roles"])
        let capabilities = parseStringSet(obj["capabilities"])

        var scopeKinds = Set<String>()
        var teamKeys = Set<String>()
        var projectIds = Set<String>()
        var recruiterIds = Set<String>()

        if let scopesArr = obj["scopes"] as? [Any] {
            for item in scopesArr {
                if let sObj = item as? [String: Any] {
                    let kind = sObj.stringValue(forKeys: ["scope_kind", "kind"]).trimmingCharacters(in: .whitespacesAndNewlines)
                    let valStr = sObj.stringValue(forKeys: ["scope_value", "value"]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if !kind.isEmpty {
                        scopeKinds.insert(kind)
                        switch kind {
                        case "team":
                            if !valStr.isEmpty { teamKeys.insert(valStr) }
                        case "project":
                            if !valStr.isEmpty { projectIds.insert(valStr) }
                        case "self_recruiter":
                            if !valStr.isEmpty { recruiterIds.insert(valStr) }
                        default:
                            break
                        }
                    }
                } else if let str = item as? String {
                    let clean = str.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !clean.isEmpty { scopeKinds.insert(clean) }
                }
            }
        }
        scopeKinds.formUnion(parseStringSet(obj["scope_kinds"]))
        teamKeys.formUnion(parseStringSet(obj["team_bucket_keys"]))
        projectIds.formUnion(parseStringSet(obj["project_ids"]))
        recruiterIds.formUnion(parseStringSet(obj["recruiter_ids"]))

        let selfRecruiter = obj.optCleanString("self_recruiter_id") ?? recruiterIds.first

        return AppActor(
            appUserId: userId.isEmpty ? email : userId,
            email: email,
            displayName: displayName.isEmpty ? fallbackName : displayName,
            status: status,
            roleKeys: roleKeys,
            capabilities: capabilities,
            scopeKinds: scopeKinds,
            teamBucketKeys: teamKeys,
            projectIds: projectIds,
            recruiterIds: recruiterIds,
            selfRecruiterSuggestion: selfRecruiter,
            isPendingAccess: pendingAccess
        )
    }

    private static func parseStringSet(_ raw: Any?) -> Set<String> {
        guard let arr = raw as? [Any] else { return [] }
        var out = Set<String>()
        for el in arr {
            if let s = el as? String {
                let clean = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { out.insert(clean) }
            }
        }
        return out
    }
}

// MARK: - Main Navigation Destinations

public enum MainDestination: String, CaseIterable, Equatable, Sendable {
    case dashboard = "DASHBOARD"
    case newEntry = "NEW_ENTRY"
    case workers = "WORKERS"
    case attendance = "ATTENDANCE"
    case workReports = "WORK_REPORTS"

    public var labelVi: String {
        switch self {
        case .dashboard: return "Tổng quan"
        case .newEntry: return "Cấp mới"
        case .workers: return "Nhân sự"
        case .attendance: return "Chấm công"
        case .workReports: return "Báo cáo"
        }
    }

    public var sfSymbolName: String {
        switch self {
        case .dashboard: return "chart.bar.xaxis"
        case .newEntry: return "person.badge.plus"
        case .workers: return "person.3.fill"
        case .attendance: return "camera.viewfinder"
        case .workReports: return "doc.text.fill"
        }
    }

    public static func allowedDestinations(for actor: AppActor) -> [MainDestination] {
        var list: [MainDestination] = [.dashboard]
        if actor.canAccessNewWorkerEntry {
            list.append(.newEntry)
        }
        if actor.canViewWorkersScreen {
            list.append(.workers)
        }
        list.append(.attendance)
        list.append(.workReports)
        return list
    }

    public static func coerceDestination(_ requested: MainDestination, for actor: AppActor?) -> MainDestination {
        guard let actor = actor else { return .dashboard }
        let allowed = allowedDestinations(for: actor)
        return allowed.contains(requested) ? requested : (allowed.first ?? .dashboard)
    }
}

// MARK: - Dashboard Models

public enum DashboardDatePreset: String, CaseIterable, Equatable, Sendable {
    case allTime = "all"
    case thisWeek = "this_week"
    case thisMonth = "this_month"
    case lastMonth = "last_month"

    public var key: String { rawValue }

    public var label: String {
        switch self {
        case .allTime: return "Toàn kỳ"
        case .thisWeek: return "Tuần này"
        case .thisMonth: return "Tháng này"
        case .lastMonth: return "Tháng trước"
        }
    }

    public func resolveDateRange(referenceDate: Date = Date()) -> (fromIso: String, toIso: String)? {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        cal.firstWeekday = 2 // Monday
        let fmt = DateFormatter()
        fmt.calendar = cal
        fmt.timeZone = cal.timeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"

        switch self {
        case .allTime:
            return nil
        case .thisWeek:
            let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: referenceDate)
            guard let monday = cal.date(from: comps) else { return nil }
            return (fmt.string(from: monday), fmt.string(from: referenceDate))
        case .thisMonth:
            let comps = cal.dateComponents([.year, .month], from: referenceDate)
            guard let firstDay = cal.date(from: comps) else { return nil }
            return (fmt.string(from: firstDay), fmt.string(from: referenceDate))
        case .lastMonth:
            let comps = cal.dateComponents([.year, .month], from: referenceDate)
            guard let firstOfThisMonth = cal.date(from: comps),
                  let lastOfPrevMonth = cal.date(byAdding: .day, value: -1, to: firstOfThisMonth),
                  let firstOfPrevMonth = cal.date(from: cal.dateComponents([.year, .month], from: lastOfPrevMonth))
            else { return nil }
            return (fmt.string(from: firstOfPrevMonth), fmt.string(from: lastOfPrevMonth))
        }
    }
}

public struct BiBucketItem: Equatable, Identifiable, Sendable {
    public var id: String { key.isEmpty ? label : key }
    public let key: String
    public let label: String
    public let subtitle: String?
    public let totalCount: Int
    public let onCount: Int
    public let offCount: Int
    public let sharePct: Double?

    public init(
        key: String = "",
        label: String,
        subtitle: String? = nil,
        totalCount: Int,
        onCount: Int = 0,
        offCount: Int = 0,
        sharePct: Double? = nil
    ) {
        self.key = key
        self.label = label
        self.subtitle = subtitle
        self.totalCount = totalCount
        self.onCount = onCount
        self.offCount = offCount
        self.sharePct = sharePct
    }
}

public struct GeneralOverviewSnapshot: Equatable, Sendable {
    public let audienceKind: String
    public let asOfDate: String
    public let scopeDescription: String
    public let totalWorkers: Int
    public let activeWorkers: Int
    public let inactiveWorkers: Int
    public let unconfirmedWorkers: Int
    public let retentionRatePct: Double
    public let projectCount: Int
    public let recruiterCount: Int
    public let hrpTotal: Int
    public let hrpActive: Int
    public let hrpSharePct: Double
    public let vendorTotal: Int
    public let vendorActive: Int
    public let vendorSharePct: Double
    public let teamBuckets: [BiBucketItem]
    public let topProjects: [BiBucketItem]
    public let topRecruiters: [BiBucketItem]
    public let managedTotal: Int?
    public let managedActive: Int?
    public let recruitedTotal: Int?
    public let recruitedActive: Int?
    public let teamTotal: Int?
    public let teamActive: Int?
    public let isPeriodFiltered: Bool
    public let periodFromDate: String?
    public let periodToDate: String?
    public let periodNewEpisodes: Int?
    public let periodOnInflow: Int?
    public let periodOffInflow: Int?
    public let periodNetOnChange: Int?
    public let startActiveCount: Int?
    public let startInactiveCount: Int?
    public let endActiveCount: Int?
    public let endInactiveCount: Int?
}

public struct WorkforceDeepMetrics: Equatable, Sendable {
    public let asOfDate: String
    public let totalTracked: Int
    public let activeCount: Int
    public let inactiveCount: Int
    public let unconfirmedCount: Int
    public let activeRatePct: Double
    public let genderBuckets: [BiBucketItem]
    public let ageBuckets: [BiBucketItem]
    public let tenureBuckets: [BiBucketItem]
    public let projectScaleBuckets: [BiBucketItem]
    public let projectsHasMore: Bool
    public let projectsNextPage: Int
    public let isPeriodFiltered: Bool
    public let periodFromDate: String?
    public let periodToDate: String?
    public let periodNewEpisodes: Int?
    public let periodOnInflow: Int?
    public let periodOffInflow: Int?
    public let periodNetOnChange: Int?
}

public struct TeamReportSnapshot: Equatable, Sendable {
    public let bucketKey: String
    public let bucketDisplay: String
    public let asOfDate: String
    public let totalWorkers: Int
    public let activeWorkers: Int
    public let inactiveWorkers: Int
    public let unconfirmedWorkers: Int
    public let retentionRatePct: Double
    public let recruiterBreakdown: [BiBucketItem]
    public let projectBreakdown: [BiBucketItem]
    public let drillDownPage: Int
    public let drillDownHasMore: Bool
    public let isPeriodFiltered: Bool
    public let periodFromDate: String?
    public let periodToDate: String?
    public let periodNewEpisodes: Int?
    public let periodOnInflow: Int?
    public let periodOffInflow: Int?
    public let periodNetOnChange: Int?
}

public struct DuplicateCccdEpisodeRow: Equatable, Identifiable, Sendable {
    public var id: String { "\(entryId)_\(employeeCode)" }
    public let entryId: String
    public let entryVersion: Int
    public let employeeCode: String
    public let displayName: String
    public let projectDisplay: String
    public let recruiterDisplay: String
    public let firstWorkDate: String
    public let employmentStatus: String
    public let canSelfWithdraw: Bool
}

public struct DuplicateCccdGroupItem: Equatable, Identifiable, Sendable {
    public var id: String { "\(cccdLast4)_\(firstWorkDate)" }
    public let cccdLast4: String
    public let firstWorkDate: String
    public let episodeCount: Int
    public let episodes: [DuplicateCccdEpisodeRow]
}

public struct DuplicateCccdReportPage: Equatable, Sendable {
    public let groups: [DuplicateCccdGroupItem]
    public let page: Int
    public let hasMore: Bool
}

// MARK: - CCCD Review Models

public enum CccdReviewStatusFilter: String, CaseIterable, Equatable, Sendable {
    case needsReview = "NEEDS_REVIEW"
    case mismatch = "MISMATCH"
    case qrUnreadable = "QR_UNREADABLE"
    case pending = "PENDING"
    case matchedInitial = "MATCHED_INITIAL"
    case resolved = "RESOLVED"
    case all = "ALL"

    public var code: String { rawValue }

    public var label: String {
        switch self {
        case .needsReview: return "Cần xử lý"
        case .mismatch: return "Lệch CCCD"
        case .qrUnreadable: return "Lỗi đọc QR"
        case .pending: return "Chờ đọc QR"
        case .matchedInitial: return "Khớp ban đầu"
        case .resolved: return "Đã xử lý"
        case .all: return "Tất cả"
        }
    }
}

public struct CccdReviewSummary: Equatable, Sendable {
    public let needsReviewCount: Int
    public let mismatchCount: Int
    public let qrUnreadableCount: Int
    public let pendingCount: Int
    public let matchedInitialCount: Int
    public let resolvedCount: Int
    public let totalCount: Int

    public init(
        needsReviewCount: Int = 0,
        mismatchCount: Int = 0,
        qrUnreadableCount: Int = 0,
        pendingCount: Int = 0,
        matchedInitialCount: Int = 0,
        resolvedCount: Int = 0,
        totalCount: Int = 0
    ) {
        self.needsReviewCount = needsReviewCount
        self.mismatchCount = mismatchCount
        self.qrUnreadableCount = qrUnreadableCount
        self.pendingCount = pendingCount
        self.matchedInitialCount = matchedInitialCount
        self.resolvedCount = resolvedCount
        self.totalCount = totalCount
    }
}

public struct CccdReviewExtractedQr: Equatable, Sendable {
    public let cccd12: String?
    public let legacyCmnd9: String?
    public let fullName: String?
    public let dateOfBirthUi: String?
    public let genderUi: String?
    public let address: String?
    public let issueDateUi: String?
    public let rawQrText: String?
}

public struct CccdReviewComparisonField: Equatable, Identifiable, Sendable {
    public var id: String { field }
    public let field: String
    public let label: String
    public let enteredValue: String?
    public let qrValue: String?
    public let status: String
}

public struct CccdReviewQueueItem: Equatable, Identifiable, Sendable {
    public var id: String { entryId }
    public let entryId: String
    public let entryVersion: Int
    public let employeeCode: String
    public let projectId: String
    public let projectDisplay: String
    public let recruiterId: String
    public let recruiterDisplay: String
    public let firstWorkDate: String
    public let reconciliationStatus: String
    public let enteredDisplayName: String
    public let enteredNationalId: String?
    public let enteredDateOfBirth: String?
    public let enteredGender: String?
    public let enteredAddress: String?
    public let enteredNationalIdIssuedAt: String?
    public let qrExtractedPayload: CccdReviewExtractedQr?
    public let cccdFrontQrStatus: String
    public let cccdFrontQrError: String?
    public let cccdFrontPreviewPath: String?
    public let cccdBackQrStatus: String
    public let cccdBackQrError: String?
    public let cccdBackPreviewPath: String?
    public let comparison: [CccdReviewComparisonField]
    public let resolvedAt: String?
    public let resolutionAction: String?
    public let resolutionNote: String?
}

public struct CccdReviewQueuePage: Equatable, Sendable {
    public let rows: [CccdReviewQueueItem]
    public let totalCount: Int
    public let limit: Int
    public let offset: Int
    public let summary: CccdReviewSummary
}

// MARK: - Direct Entry & Workers Models

public struct ProjectCatalogOption: Equatable, Identifiable, Sendable {
    public var id: String { projectId }
    public let projectId: String
    public let displayName: String
}

public struct RecruiterCatalogOption: Equatable, Identifiable, Sendable {
    public var id: String { recruiterId }
    public let recruiterId: String
    public let displayName: String
    public let providerType: String // "hrp" | "vendor"
    public let personnelCode: String
    public let teamDisplayName: String?

    public var label: String {
        if personnelCode.isEmpty { return displayName }
        return "\(displayName) (\(personnelCode))"
    }
}

public struct BankCatalogOption: Equatable, Identifiable, Sendable {
    public var id: String { bankId }
    public let bankId: String
    public let displayName: String
}

public struct LaborTypeCatalogOption: Equatable, Identifiable, Sendable {
    public var id: String { laborTypeKey }
    public let laborTypeKey: String
    public let displayName: String
    public let sortOrder: Int
}

public struct DirectEntryCatalog: Equatable, Sendable {
    public let effectiveDate: String
    public let projects: [ProjectCatalogOption]
    public let recruiters: [RecruiterCatalogOption]
    public let banks: [BankCatalogOption]
    public let laborTypes: [LaborTypeCatalogOption]
}

public struct StagedDocumentRef: Codable, Equatable, Sendable {
    public let stageId: String
    public let documentType: String
    public let fileName: String
    public let mimeType: String
    public let sizeBytes: Int64
    public let sha256Hex: String
    public let localFilePath: String?
    public let pageCount: Int
    public let sourceImagePaths: [String]

    public init(
        stageId: String = "",
        documentType: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Int64,
        sha256Hex: String,
        localFilePath: String? = nil,
        pageCount: Int = 1,
        sourceImagePaths: [String] = []
    ) {
        self.stageId = stageId
        self.documentType = documentType
        self.fileName = fileName
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.sha256Hex = sha256Hex
        self.localFilePath = localFilePath
        self.pageCount = pageCount
        self.sourceImagePaths = sourceImagePaths
    }
}

public struct NewWorkerEntryDraft: Equatable, Identifiable, Sendable {
    public var id: String { localId }
    public var localId: String
    public var projectId: String
    public var projectDisplay: String
    public var providerType: String // "hrp" | "vendor" | ""
    public var recruiterId: String
    public var recruiterDisplay: String
    public var laborTypeKey: String
    public var laborTypeDisplay: String
    public var firstWorkDateUi: String // DD/MM/YYYY
    public var employmentStatusUi: String // "Đang làm" | "Đang chờ việc"
    public var cccd: String
    public var fullName: String
    public var genderUi: String
    public var phone: String
    public var address: String
    public var birthDateUi: String
    public var cccdIssueDateUi: String
    public var bankAccountNumber: String
    public var bankAccountHolder: String
    public var bankName: String
    public var note: String
    public var portraitStage: StagedDocumentRef?
    public var cccdFrontStage: StagedDocumentRef?
    public var cccdBackStage: StagedDocumentRef?
    public var contractStage: StagedDocumentRef?
    public var uploadingSlot: String?
    public var slotError: String?
    public var isExpanded: Bool
    public var showOptionalSection: Bool

    public init(
        localId: String = UUID().uuidString,
        projectId: String = "",
        projectDisplay: String = "",
        providerType: String = "",
        recruiterId: String = "",
        recruiterDisplay: String = "",
        laborTypeKey: String = "",
        laborTypeDisplay: String = "",
        firstWorkDateUi: String = DirectEntryContract.todayUiInHcm(),
        employmentStatusUi: String = "Đang làm",
        cccd: String = "",
        fullName: String = "",
        genderUi: String = "",
        phone: String = "",
        address: String = "",
        birthDateUi: String = "",
        cccdIssueDateUi: String = "",
        bankAccountNumber: String = "",
        bankAccountHolder: String = "",
        bankName: String = "",
        note: String = "",
        portraitStage: StagedDocumentRef? = nil,
        cccdFrontStage: StagedDocumentRef? = nil,
        cccdBackStage: StagedDocumentRef? = nil,
        contractStage: StagedDocumentRef? = nil,
        uploadingSlot: String? = nil,
        slotError: String? = nil,
        isExpanded: Bool = true,
        showOptionalSection: Bool = false
    ) {
        self.localId = localId
        self.projectId = projectId
        self.projectDisplay = projectDisplay
        self.providerType = providerType
        self.recruiterId = recruiterId
        self.recruiterDisplay = recruiterDisplay
        self.laborTypeKey = laborTypeKey
        self.laborTypeDisplay = laborTypeDisplay
        self.firstWorkDateUi = firstWorkDateUi
        self.employmentStatusUi = employmentStatusUi
        self.cccd = cccd
        self.fullName = fullName
        self.genderUi = genderUi
        self.phone = phone
        self.address = address
        self.birthDateUi = birthDateUi
        self.cccdIssueDateUi = cccdIssueDateUi
        self.bankAccountNumber = bankAccountNumber
        self.bankAccountHolder = bankAccountHolder
        self.bankName = bankName
        self.note = note
        self.portraitStage = portraitStage
        self.cccdFrontStage = cccdFrontStage
        self.cccdBackStage = cccdBackStage
        self.contractStage = contractStage
        self.uploadingSlot = uploadingSlot
        self.slotError = slotError
        self.isExpanded = isExpanded
        self.showOptionalSection = showOptionalSection
    }

    public var isStarted: Bool {
        !projectId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !providerType.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !recruiterId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !cccd.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            portraitStage != nil ||
            cccdFrontStage != nil ||
            cccdBackStage != nil ||
            contractStage != nil
    }

    public var isValid: Bool {
        !projectId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !providerType.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !recruiterId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !laborTypeKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            DirectEntryContract.isValidUiOrIsoDate(firstWorkDateUi) &&
            DirectEntryContract.isValidCccd12(cccd) &&
            DirectEntryContract.isValidFreeDateText(birthDateUi) &&
            DirectEntryContract.isValidFreeDateText(cccdIssueDateUi) &&
            uploadingSlot == nil
    }

    public func allAttachments() -> [StagedDocumentRef] {
        [portraitStage, cccdFrontStage, cccdBackStage, contractStage].compactMap { $0 }
    }

    public func localPendingAttachments() -> [StagedDocumentRef] {
        allAttachments().filter { ($0.localFilePath?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false) }
    }
}

public struct PendingEntryAttachmentTask: Equatable, Sendable {
    public let entryId: String
    public let workerDisplayName: String
    public let documentType: String
    public let fileName: String
    public let mimeType: String
    public let localFilePath: String
    public let idempotencyBaseKey: String
    public let sourceImagePaths: [String]
}

public struct OwnSubmissionItem: Equatable, Identifiable, Sendable {
    public var id: String { submissionId }
    public let submissionId: String
    public let state: String
    public let version: Int
    public let entryCount: Int
    public let createdAt: String
    public let updatedAt: String
    public let submittedAt: String?
    public let allowedTransitions: [String]
}

public struct SubmissionBatchResult: Equatable, Sendable {
    public let submissionId: String
    public let state: String
    public let version: Int
    public let entryIds: [String]
    public let employeeCodes: [String]
}

public struct DuplicateCccdConflictItem: Equatable, Sendable {
    public let nationalIdMasked: String
    public let existingEmployeeCode: String
    public let existingDisplayName: String
    public let existingProjectDisplay: String
    public let existingFirstWorkDate: String

    public func formatConflictLine() -> String {
        "• CCCD \(nationalIdMasked): trùng với \(existingDisplayName) (\(existingEmployeeCode)) tại \(existingProjectDisplay) — ngày vào \(DirectEntryContract.isoDateToUiDate(existingFirstWorkDate))"
    }
}

public struct DuplicateCccdPreflightResult: Equatable, Sendable {
    public let submissionId: String
    public let version: Int
    public let fingerprint: String
    public let conflictCount: Int
    public let conflicts: [DuplicateCccdConflictItem]
}

public struct PendingDuplicateCccdSubmission: Equatable, Sendable {
    public let entryCount: Int
    public let employeeCodes: [String]
    public let preflight: DuplicateCccdPreflightResult
    public let navigateToWorkersOnConfirm: Bool
    public let pendingAttachments: [PendingEntryAttachmentTask]
}

public struct WorkerAllowedActions: Codable, Equatable, Sendable {
    public let view: Bool
    public let proposeChange: Bool
    public let directEdit: Bool

    public init(view: Bool = true, proposeChange: Bool = false, directEdit: Bool = false) {
        self.view = view
        self.proposeChange = proposeChange
        self.directEdit = directEdit
    }
}

public struct WorkerListSummary: Equatable, Sendable {
    public let totalWorkers: Int
    public let projectCount: Int
    public let recruiterCount: Int
}

public struct WorkerProfileEntity: Codable, Equatable, Identifiable, Sendable {
    public var id: String { cacheKey }
    public let cacheKey: String
    public let scopeKey: String
    public let entryId: String
    public let entryVersion: Int
    public let employeeCode: String
    public let displayName: String
    public let projectId: String
    public let projectDisplay: String
    public let recruiterId: String
    public let recruiterDisplay: String
    public let laborType: String
    public let laborTypeDisplay: String
    public let firstWorkDate: String
    public let employmentStatus: String
    public let paymentSummary: String
    public let pendingRequestSummary: String
    public let lastDecisionSummary: String
    public let allowedActions: WorkerAllowedActions
}

public struct EntryDocumentDescriptor: Equatable, Identifiable, Sendable {
    public var id: String { "\(documentType)_\(fileName)" }
    public let documentType: String
    public let uploadStatus: String
    public let fileName: String
    public let mimeType: String
    public let sizeBytes: Int64
    public let previewPath: String?
    public let downloadPath: String?
}

public struct WorkerEntryDetail: Equatable, Sendable {
    public let entryId: String
    public let version: Int
    public let employeeCode: String
    public let displayName: String
    public let projectId: String
    public let projectDisplay: String
    public let recruiterId: String
    public let recruiterDisplay: String
    public let laborType: String
    public let laborTypeDisplay: String
    public let firstWorkDate: String
    public let employmentStatus: String
    public let employmentEffectiveDate: String
    public let employmentLeaveReason: String?
    public let gender: String?
    public let dateOfBirth: String?
    public let phone: String?
    public let nationalId: String?
    public let nationalIdIssuedAt: String?
    public let nationalIdIssuedPlace: String?
    public let address: String?
    public let bankId: String?
    public let bankName: String?
    public let accountNumber: String?
    public let accountHolderName: String?
    public let generalNote: String?
    public let portraitPreviewPath: String?
    public let documents: [EntryDocumentDescriptor]
}

public struct ChangeRequestCreated: Equatable, Sendable {
    public let requestId: String
    public let state: String
}

// MARK: - Attendance Models

public enum AttendanceOutboxStatus {
    public static let QUEUED = "QUEUED"
    public static let UPLOADING_PHOTO = "UPLOADING_PHOTO"
    public static let SUBMITTING_CHECKIN = "SUBMITTING_CHECKIN"
    public static let SYNCED = "SYNCED"
    public static let RETRYABLE_ERROR = "RETRYABLE_ERROR"
    public static let TERMINAL_FAILURE = "TERMINAL_FAILURE"
}

public enum AttendanceTimeSource {
    public static let SERVER_ANCHORED = "SERVER_ANCHORED"
    public static let DEVICE_CLOCK_UNVERIFIED = "DEVICE_CLOCK_UNVERIFIED"
}

public struct ServerTimeAnchor: Codable, Equatable, Sendable {
    public let appUserId: String
    public let serverTimeIso: String
    public let serverEpochMs: Int64
    public let anchorElapsedRealtimeMs: Int64
    public let bootCountOrSessionId: String
    public let timeToken: String
    public let maxValidSeconds: Int64
    public let syncedAtDeviceWallMs: Int64
    public let expiresAtIso: String
}

public struct CapturedAttendanceTime: Codable, Equatable, Sendable {
    public let clientCapturedAtIso: String
    public let timeSource: String
    public let timeToken: String?
    public let elapsedSinceAnchorMs: Int64?
    public let serverAnchorTimeIso: String?
}

public struct TimeAnchorUiState: Equatable, Sendable {
    public var hasValidAnchor: Bool
    public var syncing: Bool
    public var serverTimeIso: String?
    public var expiresAtIso: String?
    public var lastSyncError: String?

    public init(
        hasValidAnchor: Bool = false,
        syncing: Bool = false,
        serverTimeIso: String? = nil,
        expiresAtIso: String? = nil,
        lastSyncError: String? = nil
    ) {
        self.hasValidAnchor = hasValidAnchor
        self.syncing = syncing
        self.serverTimeIso = serverTimeIso
        self.expiresAtIso = expiresAtIso
        self.lastSyncError = lastSyncError
    }
}

public struct AttendanceEntity: Codable, Equatable, Identifiable, Sendable {
    public var id: String { idempotencyKey }
    public var idempotencyKey: String
    public var appUserId: String
    public var actorDisplayName: String
    public var latitude: Double
    public var longitude: Double
    public var accuracyMeters: Float
    public var addressHint: String
    public var photoLocalPath: String?
    public var photoSha256: String
    public var photoObjectId: String?
    public var note: String
    public var clientCapturedAtIso: String
    public var timeSource: String
    public var timeToken: String?
    public var elapsedSinceAnchorMs: Int64?
    public var syncStatus: String
    public var statusMessage: String
    public var retryCount: Int
    public var serverCheckInId: String?
    public var serverCapturedAtIso: String?
    public var serverBusinessDate: String?
}

public struct GpsSnapshot: Equatable, Sendable {
    public let latitude: Double
    public let longitude: Double
    public let accuracyMeters: Float
    public let addressHint: String
}

public struct AttendanceSubmitOutcome: Equatable, Sendable {
    public let isSynced: Bool
    public let userMessage: String
    public let record: AttendanceEntity
}

public struct AttendanceOutboxFlushSummary: Equatable, Sendable {
    public let processedCount: Int
    public let syncedCount: Int
    public let remainingRetryableCount: Int
    public let terminalFailureCount: Int
}

// MARK: - Work Reports Models

public struct WorkReportFieldOption: Codable, Equatable, Sendable {
    public let value: String
    public let label: String
}

public struct WorkReportFieldSpec: Codable, Equatable, Identifiable, Sendable {
    public var id: String { fieldKey }
    public let fieldKey: String
    public let label: String
    public let fieldType: String // "text" | "textarea" | "integer" | "decimal" | "boolean" | "date" | "single_select" | "multi_select"
    public let required: Bool
    public let description: String?
    public let defaultValue: String?
    public let options: [WorkReportFieldOption]
}

public struct WorkReportSectionSpec: Codable, Equatable, Identifiable, Sendable {
    public var id: String { sectionKey }
    public let sectionKey: String
    public let label: String
    public let description: String?
    public let fields: [WorkReportFieldSpec]
}

public struct WorkReportTemplateOption: Codable, Equatable, Identifiable, Sendable {
    public var id: String { templateId }
    public let templateId: String
    public let displayName: String
}

public struct WorkReportObligationEntity: Codable, Equatable, Identifiable, Sendable {
    public var id: String { obligationId }
    public let obligationId: String
    public let templateId: String
    public let templateName: String
    public let periodKey: String
    public let dueAtIso: String
    public let obligationStatus: String // "ACTIVE" | "CANCELLED"
    public let submissionState: String // "NOT_STARTED" | "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "APPROVED" | "REJECTED"
    public let submissionVersion: Int
    public let isOverdue: Bool
    public let isLate: Bool
    public let submittedAtIso: String?
    public let latestReviewReason: String?
    public let schemaJsonRaw: String
    public let payloadJsonRaw: String

    public func withOverdue(_ overdue: Bool, payloadJsonRaw: String? = nil) -> WorkReportObligationEntity {
        WorkReportObligationEntity(
            obligationId: obligationId,
            templateId: templateId,
            templateName: templateName,
            periodKey: periodKey,
            dueAtIso: dueAtIso,
            obligationStatus: obligationStatus,
            submissionState: submissionState,
            submissionVersion: submissionVersion,
            isOverdue: overdue,
            isLate: isLate,
            submittedAtIso: submittedAtIso,
            latestReviewReason: latestReviewReason,
            schemaJsonRaw: schemaJsonRaw,
            payloadJsonRaw: payloadJsonRaw ?? self.payloadJsonRaw
        )
    }
}

public struct WorkReportReviewItem: Equatable, Identifiable, Sendable {
    public var id: String { obligationId }
    public let obligationId: String
    public let templateId: String
    public let templateName: String
    public let periodKey: String
    public let assigneeUserId: String
    public let assigneeDisplayName: String
    public let teamDisplayName: String?
    public let submittedAt: String
    public let submissionVersion: Int
    public let isLate: Bool
    public let payloadSummary: [(String, String)]

    public static func == (lhs: WorkReportReviewItem, rhs: WorkReportReviewItem) -> Bool {
        lhs.obligationId == rhs.obligationId &&
            lhs.submissionVersion == rhs.submissionVersion &&
            lhs.submittedAt == rhs.submittedAt
    }
}

public struct AnalyticsMetricDisplay: Equatable, Identifiable, Sendable {
    public var id: String { key }
    public let key: String
    public let label: String
    public let valueDisplay: String
    public let subtitleDisplay: String?
}

public struct AnalyticsGroupRow: Equatable, Identifiable, Sendable {
    public var id: String { groupKey }
    public let groupKey: String
    public let groupLabel: String
    public let totalObligations: Int
    public let submittedCount: Int
    public let approvedCount: Int
    public let overdueMissingCount: Int
    public let complianceRatePct: Double
}

public struct WorkReportAnalyticsSnapshot: Equatable, Sendable {
    public let templateId: String
    public let templateName: String
    public let finality: String
    public let groupBy: String
    public let scopeLabel: String
    public let totalObligations: Int
    public let submittedCount: Int
    public let approvedCount: Int
    public let pendingReviewCount: Int
    public let overdueMissingCount: Int
    public let lateSubmittedCount: Int
    public let complianceRatePct: Double
    public let metrics: [AnalyticsMetricDisplay]
    public let groupRows: [AnalyticsGroupRow]
}

// MARK: - Real-time Notifications (SSE & REST)

public struct AppNotificationItem: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let body: String
    public let category: String
    public let createdAtIso: String
    public var isRead: Bool
    public let targetDestination: String?
    public let targetEntityId: String?

    public init(
        id: String,
        title: String,
        body: String,
        category: String = "SYSTEM",
        createdAtIso: String,
        isRead: Bool = false,
        targetDestination: String? = nil,
        targetEntityId: String? = nil
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.category = category
        self.createdAtIso = createdAtIso
        self.isRead = isRead
        self.targetDestination = targetDestination
        self.targetEntityId = targetEntityId
    }

    public var resolvedMainDestination: MainDestination {
        let upper = (targetDestination ?? category).trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if upper.contains("WORK_REPORT") || upper.contains("OBLIGATION") || upper.contains("REPORT") {
            return .workReports
        }
        if upper.contains("ATTENDANCE") || upper.contains("CHECKIN") || upper.contains("CHECK_IN") {
            return .attendance
        }
        if upper.contains("CCCD") || upper.contains("DASHBOARD") || upper.contains("DUPLICATE") {
            return .dashboard
        }
        if upper.contains("NEW_ENTRY") || upper.contains("STAGE") {
            return .newEntry
        }
        if upper.contains("WORKER") || upper.contains("ENTRY") || upper.contains("SUBMISSION") || upper.contains("CHANGE") {
            return .workers
        }
        return .dashboard
    }
}

// MARK: - JSON Dictionary Parsing Helpers

public extension Dictionary where Key == String, Value == Any {
    func optCleanString(_ key: String) -> String? {
        guard let raw = self[key] else { return nil }
        if raw is NSNull { return nil }
        if let s = raw as? String {
            let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty || trimmed.lowercased() == "null" { return nil }
            return trimmed
        }
        if let n = raw as? NSNumber {
            return n.stringValue
        }
        return nil
    }

    func stringValue(forKeys keys: [String], default fallback: String = "") -> String {
        for k in keys {
            if let v = optCleanString(k) {
                return v
            }
        }
        return fallback
    }

    func intValue(forKeys keys: [String], default fallback: Int = 0) -> Int {
        for k in keys {
            if let v = self[k] {
                if let i = v as? Int { return i }
                if let n = v as? NSNumber { return n.intValue }
                if let s = v as? String, let parsed = Int(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
                    return parsed
                }
            }
        }
        return fallback
    }

    func int64Value(forKeys keys: [String], default fallback: Int64 = 0) -> Int64 {
        for k in keys {
            if let v = self[k] {
                if let i = v as? Int64 { return i }
                if let i = v as? Int { return Int64(i) }
                if let n = v as? NSNumber { return n.int64Value }
                if let s = v as? String, let parsed = Int64(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
                    return parsed
                }
            }
        }
        return fallback
    }

    func doubleValue(forKeys keys: [String], default fallback: Double = 0.0) -> Double {
        for k in keys {
            if let v = self[k] {
                if let d = v as? Double { return d }
                if let n = v as? NSNumber { return n.doubleValue }
                if let s = v as? String, let parsed = Double(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
                    return parsed
                }
            }
        }
        return fallback
    }

    func boolValue(forKeys keys: [String], default fallback: Bool = false) -> Bool {
        for k in keys {
            if let v = self[k] {
                if let b = v as? Bool { return b }
                if let n = v as? NSNumber { return n.boolValue }
                if let s = v as? String {
                    let lower = s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    if lower == "true" || lower == "1" { return true }
                    if lower == "false" || lower == "0" { return false }
                }
            }
        }
        return fallback
    }
}
