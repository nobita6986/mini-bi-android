import Foundation

// MARK: - Environment Variants & Versioning (`HRP BI` vs `HRP BI 2`)
// Mirrors Android's `release` and `debug` buildTypes in `app/build.gradle.kts`

public enum AppEnvironmentVariant: String, CaseIterable, Equatable, Sendable {
    case release = "release"
    case staging = "staging"
}

public struct AppEnvironmentConfig: Equatable, Sendable {
    public let variant: AppEnvironmentVariant
    public let appBrandTitle: String
    public let appBrandBadgeText: String
    public let defaultBaseUrl: String
    public let bundleIdentifier: String
    public let enforceHttps: Bool
    public let marketingVersion: String
    public let buildNumber: String

    public init(
        variant: AppEnvironmentVariant,
        appBrandTitle: String,
        appBrandBadgeText: String,
        defaultBaseUrl: String,
        bundleIdentifier: String,
        enforceHttps: Bool,
        marketingVersion: String,
        buildNumber: String
    ) {
        self.variant = variant
        self.appBrandTitle = appBrandTitle
        self.appBrandBadgeText = appBrandBadgeText
        self.defaultBaseUrl = defaultBaseUrl
        self.bundleIdentifier = bundleIdentifier
        self.enforceHttps = enforceHttps
        self.marketingVersion = marketingVersion
        self.buildNumber = buildNumber
    }

    public static func forVariant(
        _ variant: AppEnvironmentVariant,
        marketingVersion: String = "1.0.2.27102026",
        buildNumber: String = "6"
    ) -> AppEnvironmentConfig {
        switch variant {
        case .release:
            return AppEnvironmentConfig(
                variant: .release,
                appBrandTitle: "HRP BI",
                appBrandBadgeText: "BI",
                defaultBaseUrl: "https://bi.hrpartner.vn",
                bundleIdentifier: "com.aistudio.minibi.hrpbia",
                enforceHttps: true,
                marketingVersion: marketingVersion,
                buildNumber: buildNumber
            )
        case .staging:
            return AppEnvironmentConfig(
                variant: .staging,
                appBrandTitle: "HRP BI 2",
                appBrandBadgeText: "BI2",
                defaultBaseUrl: "https://bi2.hrpartner.vn",
                bundleIdentifier: "com.aistudio.minibi.hrpbia.bi2",
                enforceHttps: false,
                marketingVersion: marketingVersion,
                buildNumber: buildNumber
            )
        }
    }

    public static var current: AppEnvironmentConfig {
        fromBundle(.main)
    }

    public static func fromBundle(_ bundle: Bundle) -> AppEnvironmentConfig {
        let info = bundle.infoDictionary ?? [:]
        let displayVer = (info["APP_DISPLAY_VERSION"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let marketing = (info["CFBundleShortVersionString"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let build = (info["CFBundleVersion"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedMarketing = (displayVer?.isEmpty == false)
            ? displayVer!
            : ((marketing?.isEmpty == false) ? marketing! : "1.0.2.27102026")
        let resolvedBuild = (build?.isEmpty == false) ? build! : "6"

        let bundleId = (bundle.bundleIdentifier ?? (info["CFBundleIdentifier"] as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let badge = (info["BRAND_BADGE_LABEL"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let host = (info["DEFAULT_SERVER_HOST"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        #if BI2_STAGING
        let compiledStaging = true
        #else
        let compiledStaging = false
        #endif

        let isStaging = compiledStaging
            || bundleId.hasSuffix(".bi2")
            || badge.uppercased() == "BI2"
            || host.lowercased().hasPrefix("bi2.")

        return forVariant(
            isStaging ? .staging : .release,
            marketingVersion: resolvedMarketing,
            buildNumber: resolvedBuild
        )
    }
}

// MARK: - Generic API Result Envelope

public enum MiniBiApiResult<T> {
    case success(T, statusCode: Int = 200)
    case failure(
        message: String,
        httpStatus: Int? = nil,
        errorCode: String? = nil,
        rawBody: String? = nil
    )
}

// MARK: - Auth & Session Models

public struct SavedLoginAccount: Codable, Equatable, Identifiable, Sendable {
    public var id: String { identifier.lowercased() }
    public let identifier: String
    public let password: String
    public let displayName: String
    public let role: String
    public let updatedAtEpochMs: Int64

    public init(
        identifier: String,
        password: String,
        displayName: String = "",
        role: String = "",
        updatedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.identifier = identifier
        self.password = password
        self.displayName = displayName.isEmpty ? identifier : displayName
        self.role = role
        self.updatedAtEpochMs = updatedAtEpochMs
    }
}

public struct MiniBiUser: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let username: String
    public let fullName: String
    public let role: String
    public let roleLabel: String
    public let email: String
    public let phone: String
    public let teamName: String
    public let permissions: [String]
    public let allowedWorkerScopes: [String]

    public init(
        id: String,
        username: String,
        fullName: String,
        role: String = "USER",
        roleLabel: String = "Nhân sự",
        email: String = "",
        phone: String = "",
        teamName: String = "",
        permissions: [String] = [],
        allowedWorkerScopes: [String] = ["all", "managed", "recruited", "team", "uploader"]
    ) {
        self.id = id
        self.username = username
        self.fullName = fullName
        self.role = role
        self.roleLabel = roleLabel
        self.email = email
        self.phone = phone
        self.teamName = teamName
        self.permissions = permissions
        self.allowedWorkerScopes = allowedWorkerScopes
    }
}

// MARK: - Navigation & Tab Enums

public enum MiniBiNavDestination: String, CaseIterable, Identifiable, Equatable, Sendable {
    case dashboard = "dashboard"
    case workReports = "work_reports"
    case attendance = "attendance"
    case workers = "workers"
    case newEntry = "new_entry"

    public var id: String { rawValue }

    public var titleVi: String {
        switch self {
        case .dashboard:
            return "Tổng quan"
        case .workReports:
            return "Báo cáo"
        case .attendance:
            return "Chấm công"
        case .workers:
            return "Nhân sự"
        case .newEntry:
            return "Cấp mới"
        }
    }

    public var systemIconName: String {
        switch self {
        case .dashboard:
            return "chart.bar.xaxis"
        case .workReports:
            return "doc.text.fill"
        case .attendance:
            return "camera.viewfinder"
        case .workers:
            return "person.3.fill"
        case .newEntry:
            return "person.badge.plus"
        }
    }
}

public enum DashboardSubTab: String, CaseIterable, Identifiable, Equatable, Sendable {
    case overview = "overview"
    case deepMetrics = "deep_metrics"
    case teamReport = "team_report"
    case duplicateCccd = "duplicate_cccd"
    case cccdReview = "cccd_review"

    public var id: String { rawValue }

    public var titleVi: String {
        switch self {
        case .overview:
            return "Tổng quát"
        case .deepMetrics:
            return "Chuyên sâu"
        case .teamReport:
            return "Theo nhóm"
        case .duplicateCccd:
            return "CCCD trùng"
        case .cccdReview:
            return "Duyệt CCCD"
        }
    }
}

public enum DateFilterPreset: String, CaseIterable, Identifiable, Equatable, Sendable {
    case all = "all"
    case thisWeek = "this_week"
    case thisMonth = "this_month"
    case lastMonth = "last_month"

    public var id: String { rawValue }

    public var titleVi: String {
        switch self {
        case .all:
            return "Toàn thời gian"
        case .thisWeek:
            return "Tuần này"
        case .thisMonth:
            return "Tháng này"
        case .lastMonth:
            return "Tháng trước"
        }
    }
}

public enum WorkReportsSubTab: String, CaseIterable, Identifiable, Equatable, Sendable {
    case actionable = "actionable"
    case history = "history"
    case reviewQueue = "review_queue"
    case analytics = "analytics"

    public var id: String { rawValue }

    public var titleVi: String {
        switch self {
        case .actionable:
            return "Cần nộp"
        case .history:
            return "Lịch sử / Đã lưu / Đã nộp"
        case .reviewQueue:
            return "Chờ duyệt"
        case .analytics:
            return "Tổng hợp"
        }
    }
}

// MARK: - Dashboard & CCCD Review Models

public struct GeneralOverviewKpis: Equatable, Sendable {
    public let totalHeadcount: Int
    public let activeCount: Int
    public let inactiveCount: Int
    public let interviewCount: Int
    public let newCount: Int
    public let resignedInPeriodCount: Int
    public let retentionRate: Double
    public let turnoverRate: Double

    public init(
        totalHeadcount: Int = 0,
        activeCount: Int = 0,
        inactiveCount: Int = 0,
        interviewCount: Int = 0,
        newCount: Int = 0,
        resignedInPeriodCount: Int = 0,
        retentionRate: Double = 0.0,
        turnoverRate: Double = 0.0
    ) {
        self.totalHeadcount = totalHeadcount
        self.activeCount = activeCount
        self.inactiveCount = inactiveCount
        self.interviewCount = interviewCount
        self.newCount = newCount
        self.resignedInPeriodCount = resignedInPeriodCount
        self.retentionRate = retentionRate
        self.turnoverRate = turnoverRate
    }
}

public struct ProjectBreakdownRow: Identifiable, Equatable, Sendable {
    public let id: String
    public let projectName: String
    public let totalCount: Int
    public let activeCount: Int
    public let inactiveCount: Int
    public let newCount: Int
    public let activeSharePct: Double

    public init(
        id: String,
        projectName: String,
        totalCount: Int = 0,
        activeCount: Int = 0,
        inactiveCount: Int = 0,
        newCount: Int = 0,
        activeSharePct: Double = 0.0
    ) {
        self.id = id
        self.projectName = projectName
        self.totalCount = totalCount
        self.activeCount = activeCount
        self.inactiveCount = inactiveCount
        self.newCount = newCount
        self.activeSharePct = activeSharePct
    }
}

public struct PersonPerformanceRow: Identifiable, Equatable, Sendable {
    public let id: String
    public let code: String
    public let name: String
    public let totalCount: Int
    public let activeCount: Int
    public let inactiveCount: Int
    public let activeRatePct: Double

    public init(
        id: String,
        code: String = "",
        name: String,
        totalCount: Int = 0,
        activeCount: Int = 0,
        inactiveCount: Int = 0,
        activeRatePct: Double = 0.0
    ) {
        self.id = id
        self.code = code
        self.name = name
        self.totalCount = totalCount
        self.activeCount = activeCount
        self.inactiveCount = inactiveCount
        self.activeRatePct = activeRatePct
    }
}

public struct GeneralOverviewData: Equatable, Sendable {
    public let scope: String
    public let kpis: GeneralOverviewKpis
    public let projects: [ProjectBreakdownRow]
    public let projectsTotal: Int
    public let projectsPage: Int
    public let projectsTotalPages: Int
    public let recruiters: [PersonPerformanceRow]
    public let managers: [PersonPerformanceRow]

    public init(
        scope: String = "all",
        kpis: GeneralOverviewKpis = GeneralOverviewKpis(),
        projects: [ProjectBreakdownRow] = [],
        projectsTotal: Int = 0,
        projectsPage: Int = 1,
        projectsTotalPages: Int = 1,
        recruiters: [PersonPerformanceRow] = [],
        managers: [PersonPerformanceRow] = []
    ) {
        self.scope = scope
        self.kpis = kpis
        self.projects = projects
        self.projectsTotal = projectsTotal
        self.projectsPage = projectsPage
        self.projectsTotalPages = projectsTotalPages
        self.recruiters = recruiters
        self.managers = managers
    }
}

public struct DistributionBucket: Identifiable, Equatable, Sendable {
    public let id: String
    public let label: String
    public let count: Int
    public let percentage: Double

    public init(id: String, label: String, count: Int = 0, percentage: Double = 0.0) {
        self.id = id
        self.label = label
        self.count = count
        self.percentage = percentage
    }
}

public struct WorkforceDeepMetricsData: Equatable, Sendable {
    public let tenureBuckets: [DistributionBucket]
    public let ageBuckets: [DistributionBucket]
    public let genderBuckets: [DistributionBucket]
    public let contractBuckets: [DistributionBucket]
    public let resignReasonBuckets: [DistributionBucket]

    public init(
        tenureBuckets: [DistributionBucket] = [],
        ageBuckets: [DistributionBucket] = [],
        genderBuckets: [DistributionBucket] = [],
        contractBuckets: [DistributionBucket] = [],
        resignReasonBuckets: [DistributionBucket] = []
    ) {
        self.tenureBuckets = tenureBuckets
        self.ageBuckets = ageBuckets
        self.genderBuckets = genderBuckets
        self.contractBuckets = contractBuckets
        self.resignReasonBuckets = resignReasonBuckets
    }
}

public struct TeamSummaryRow: Identifiable, Equatable, Sendable {
    public let id: String
    public let teamCode: String
    public let teamName: String
    public let leaderName: String
    public let memberCount: Int
    public let totalWorkers: Int
    public let activeWorkers: Int
    public let inactiveWorkers: Int
    public let newWorkers: Int
    public let activeRatePct: Double
    public let members: [PersonPerformanceRow]

    public init(
        id: String,
        teamCode: String,
        teamName: String,
        leaderName: String = "",
        memberCount: Int = 0,
        totalWorkers: Int = 0,
        activeWorkers: Int = 0,
        inactiveWorkers: Int = 0,
        newWorkers: Int = 0,
        activeRatePct: Double = 0.0,
        members: [PersonPerformanceRow] = []
    ) {
        self.id = id
        self.teamCode = teamCode
        self.teamName = teamName
        self.leaderName = leaderName
        self.memberCount = memberCount
        self.totalWorkers = totalWorkers
        self.activeWorkers = activeWorkers
        self.inactiveWorkers = inactiveWorkers
        self.newWorkers = newWorkers
        self.activeRatePct = activeRatePct
        self.members = members
    }
}

public struct TeamReportData: Equatable, Sendable {
    public let teams: [TeamSummaryRow]

    public init(teams: [TeamSummaryRow] = []) {
        self.teams = teams
    }
}

public struct DuplicateCccdEpisode: Identifiable, Equatable, Sendable {
    public let id: String
    public let episodeId: Int64
    public let entryIdUuid: String
    public let entryVersion: Int
    public let maNld: String
    public let hoTen: String
    public let duAn: String
    public let quanLy: String
    public let nguoiTuyen: String
    public let trangThai: String
    public let ngayVaoLam: String
    public let canSelfWithdraw: Bool

    public init(
        id: String,
        episodeId: Int64,
        entryIdUuid: String = "",
        entryVersion: Int = 1,
        maNld: String = "",
        hoTen: String = "",
        duAn: String = "",
        quanLy: String = "",
        nguoiTuyen: String = "",
        trangThai: String = "",
        ngayVaoLam: String = "",
        canSelfWithdraw: Bool = false
    ) {
        self.id = id
        self.episodeId = episodeId
        self.entryIdUuid = entryIdUuid.isEmpty ? id : entryIdUuid
        self.entryVersion = entryVersion
        self.maNld = maNld
        self.hoTen = hoTen
        self.duAn = duAn
        self.quanLy = quanLy
        self.nguoiTuyen = nguoiTuyen
        self.trangThai = trangThai
        self.ngayVaoLam = ngayVaoLam
        self.canSelfWithdraw = canSelfWithdraw
    }
}

public struct DuplicateCccdGroup: Identifiable, Equatable, Sendable {
    public let id: String
    public let cccd: String
    public let workerCount: Int
    public let activeCount: Int
    public let episodes: [DuplicateCccdEpisode]

    public init(
        id: String,
        cccd: String,
        workerCount: Int = 0,
        activeCount: Int = 0,
        episodes: [DuplicateCccdEpisode] = []
    ) {
        self.id = id
        self.cccd = cccd
        self.workerCount = workerCount
        self.activeCount = activeCount
        self.episodes = episodes
    }
}

public struct DuplicateCccdReportData: Equatable, Sendable {
    public let totalGroups: Int
    public let groups: [DuplicateCccdGroup]
    public let page: Int
    public let totalPages: Int

    public init(
        totalGroups: Int = 0,
        groups: [DuplicateCccdGroup] = [],
        page: Int = 1,
        totalPages: Int = 1
    ) {
        self.totalGroups = totalGroups
        self.groups = groups
        self.page = page
        self.totalPages = totalPages
    }
}

public struct CccdComparisonRow: Identifiable, Equatable, Sendable {
    public var id: String { field }
    public let field: String
    public let label: String
    public let enteredValue: String
    public let qrValue: String
    public let status: String

    public init(
        field: String,
        label: String,
        enteredValue: String = "",
        qrValue: String = "",
        status: String = "MATCH"
    ) {
        self.field = field
        self.label = label
        self.enteredValue = enteredValue
        self.qrValue = qrValue
        self.status = status
    }
}

public struct CccdReviewItem: Identifiable, Equatable, Sendable {
    public var id: Int64 { entryId }
    public let entryId: Int64
    public let entryIdUuid: String
    public let entryVersion: Int
    public let maNld: String
    public let hoTen: String
    public let duAn: String
    public let quanLy: String
    public let nguoiTuyen: String
    public let reviewStatus: String
    public let enteredCccd: String
    public let qrCccd: String
    public let frontPreviewUrl: String?
    public let backPreviewUrl: String?
    public let comparisons: [CccdComparisonRow]

    public init(
        entryId: Int64,
        entryIdUuid: String = "",
        entryVersion: Int = 1,
        maNld: String = "",
        hoTen: String = "",
        duAn: String = "",
        quanLy: String = "",
        nguoiTuyen: String = "",
        reviewStatus: String = "PENDING_QR",
        enteredCccd: String = "",
        qrCccd: String = "",
        frontPreviewUrl: String? = nil,
        backPreviewUrl: String? = nil,
        comparisons: [CccdComparisonRow] = []
    ) {
        self.entryId = entryId
        self.entryIdUuid = entryIdUuid.isEmpty ? String(entryId) : entryIdUuid
        self.entryVersion = entryVersion
        self.maNld = maNld
        self.hoTen = hoTen
        self.duAn = duAn
        self.quanLy = quanLy
        self.nguoiTuyen = nguoiTuyen
        self.reviewStatus = reviewStatus
        self.enteredCccd = enteredCccd
        self.qrCccd = qrCccd
        self.frontPreviewUrl = frontPreviewUrl
        self.backPreviewUrl = backPreviewUrl
        self.comparisons = comparisons
    }
}

// MARK: - Direct Entry ("Cấp mới") & Workers Models

public struct DirectEntryCatalogProject: Identifiable, Equatable, Sendable {
    public var id: String { projectId }
    public let projectId: String
    public let displayName: String

    public init(projectId: String, displayName: String) {
        self.projectId = projectId
        self.displayName = displayName
    }
}

public struct DirectEntryCatalogRecruiter: Identifiable, Equatable, Sendable {
    public var id: String { recruiterId }
    public let recruiterId: String
    public let displayName: String
    public let personnelCode: String
    public let providerType: String
    public let vendorId: String?
    public let teamId: String?
    public let teamDisplayName: String?
    public let label: String

    public init(
        recruiterId: String,
        displayName: String,
        personnelCode: String = "",
        providerType: String = "hrp",
        vendorId: String? = nil,
        teamId: String? = nil,
        teamDisplayName: String? = nil,
        label: String = ""
    ) {
        self.recruiterId = recruiterId
        self.displayName = displayName
        self.personnelCode = personnelCode
        self.providerType = providerType.lowercased() == "vendor" ? "vendor" : "hrp"
        self.vendorId = vendorId
        self.teamId = teamId
        self.teamDisplayName = teamDisplayName
        if !label.isEmpty {
            self.label = label
        } else if !personnelCode.isEmpty {
            self.label = "\(displayName) (\(personnelCode))"
        } else {
            self.label = displayName
        }
    }
}

public struct DirectEntryCatalogBank: Identifiable, Equatable, Sendable {
    public var id: String { bankId }
    public let bankId: String
    public let displayName: String

    public init(bankId: String, displayName: String) {
        self.bankId = bankId
        self.displayName = displayName
    }
}

public struct DirectEntryCatalogLaborType: Identifiable, Equatable, Sendable {
    public var id: String { laborTypeKey }
    public let laborTypeKey: String
    public let displayName: String
    public let sortOrder: Int

    public init(laborTypeKey: String, displayName: String, sortOrder: Int = 100) {
        self.laborTypeKey = laborTypeKey
        self.displayName = displayName
        self.sortOrder = sortOrder
    }
}

public struct LocalAttachmentItem: Identifiable, Equatable, Sendable {
    public let id: String
    public let fileName: String
    public let mimeType: String
    public let data: Data

    public var fileSizeBytes: Int {
        data.count
    }

    public var isPdf: Bool {
        mimeType.lowercased().contains("pdf") || fileName.lowercased().hasSuffix(".pdf")
    }

    public init(
        id: String = UUID().uuidString,
        fileName: String,
        mimeType: String,
        data: Data
    ) {
        self.id = id
        self.fileName = fileName
        self.mimeType = mimeType
        self.data = data
    }
}

public struct NewWorkerEntryDraft: Identifiable, Equatable, Sendable {
    public var id: String { rowId }
    public var rowId: String

    // Canonical Android fields + backward-compatible iOS properties
    public var projectId: String
    public var duAn: String // projectDisplay
    public var providerType: String // "hrp" | "vendor"
    public var recruiterId: String
    public var nguoiTuyen: String // recruiterDisplay
    public var laborTypeKey: String
    public var nganhNghe: String // laborTypeDisplay
    public var ngayVaoLam: String // firstWorkDateUi (dd/MM/yyyy)
    public var trangThai: String // employmentStatusUi ("Đang làm" | "Đang chờ việc")
    public var soCccd: String // cccd (12 digits)
    public var hoTen: String // fullName
    public var gioiTinh: String // genderUi ("" | "Nam" | "Nữ")
    public var sdt: String // phone
    public var queQuan: String // address
    public var ngaySinh: String // birthDateUi (dd/MM/yyyy)
    public var ngayCap: String // cccdIssueDateUi (dd/MM/yyyy)
    public var soTaiKhoan: String // bankAccountNumber
    public var bankAccountHolder: String // bankAccountHolder
    public var nganHang: String // bankName
    public var ghiChu: String // note
    public var quanLy: String // legacy/optional manager display
    public var portraitFile: LocalAttachmentItem?
    public var cccdFrontFile: LocalAttachmentItem?
    public var cccdBackFile: LocalAttachmentItem?
    public var contractFiles: [LocalAttachmentItem]

    public static func defaultTodayDateUi() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        formatter.dateFormat = "dd/MM/yyyy"
        return formatter.string(from: Date())
    }

    public var isReadyForSubmit: Bool {
        let hasProject = !duAn.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !projectId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let cccdDigits = soCccd.filter(\.isNumber)
        let hasIdentity = cccdDigits.count == 12 || !hoTen.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return hasProject && hasIdentity && !trangThai.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    public func validateAndroidContractError() -> String? {
        if duAn.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && projectId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "Vui lòng chọn Dự án."
        }
        if nguoiTuyen.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && recruiterId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "Vui lòng chọn Người tuyển / Vendor."
        }
        if nganhNghe.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && laborTypeKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "Vui lòng chọn Loại hình lao động."
        }
        if ngayVaoLam.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "Vui lòng chọn Ngày bắt đầu làm việc."
        }
        let cccdDigits = soCccd.filter(\.isNumber)
        if cccdDigits.count != 12 {
            return "Số CCCD bắt buộc gồm đúng 12 chữ số."
        }
        return nil
    }

    public init(
        rowId: String = UUID().uuidString,
        projectId: String = "",
        hoTen: String = "",
        soCccd: String = "",
        duAn: String = "",
        providerType: String = "hrp",
        recruiterId: String = "",
        laborTypeKey: String = "SEASONAL",
        trangThai: String = "Đang làm",
        sdt: String = "",
        ngaySinh: String = "",
        gioiTinh: String = "",
        ngayCap: String = "",
        ngayVaoLam: String = NewWorkerEntryDraft.defaultTodayDateUi(),
        quanLy: String = "",
        nguoiTuyen: String = "",
        nganhNghe: String = "Thời vụ",
        queQuan: String = "",
        ghiChu: String = "",
        soTaiKhoan: String = "",
        bankAccountHolder: String = "",
        nganHang: String = "",
        portraitFile: LocalAttachmentItem? = nil,
        cccdFrontFile: LocalAttachmentItem? = nil,
        cccdBackFile: LocalAttachmentItem? = nil,
        contractFiles: [LocalAttachmentItem] = []
    ) {
        self.rowId = rowId
        self.projectId = projectId
        self.hoTen = hoTen
        self.soCccd = soCccd
        self.duAn = duAn
        self.providerType = providerType.lowercased() == "vendor" ? "vendor" : "hrp"
        self.recruiterId = recruiterId
        self.laborTypeKey = laborTypeKey.isEmpty ? "SEASONAL" : laborTypeKey
        self.trangThai = trangThai
        self.sdt = sdt
        self.ngaySinh = ngaySinh
        self.gioiTinh = gioiTinh
        self.ngayCap = ngayCap
        self.ngayVaoLam = ngayVaoLam.isEmpty ? Self.defaultTodayDateUi() : ngayVaoLam
        self.quanLy = quanLy
        self.nguoiTuyen = nguoiTuyen
        self.nganhNghe = nganhNghe.isEmpty ? "Thời vụ" : nganhNghe
        self.queQuan = queQuan
        self.ghiChu = ghiChu
        self.soTaiKhoan = soTaiKhoan
        self.bankAccountHolder = bankAccountHolder
        self.nganHang = nganHang
        self.portraitFile = portraitFile
        self.cccdFrontFile = cccdFrontFile
        self.cccdBackFile = cccdBackFile
        self.contractFiles = contractFiles
    }
}

public struct DirectEntryAssigneeOptions: Equatable, Sendable {
    public let projects: [String]
    public let managers: [String]
    public let recruiters: [String]
    public let banks: [String]
    public let catalogProjects: [DirectEntryCatalogProject]
    public let catalogRecruiters: [DirectEntryCatalogRecruiter]
    public let catalogBanks: [DirectEntryCatalogBank]
    public let catalogLaborTypes: [DirectEntryCatalogLaborType]

    public static let fallbackLaborTypes: [DirectEntryCatalogLaborType] = [
        DirectEntryCatalogLaborType(laborTypeKey: "SEASONAL", displayName: "Thời vụ", sortOrder: 10),
        DirectEntryCatalogLaborType(laborTypeKey: "OFFICIAL", displayName: "Chính thức", sortOrder: 20)
    ]

    public init(
        projects: [String] = [],
        managers: [String] = [],
        recruiters: [String] = [],
        banks: [String] = [],
        catalogProjects: [DirectEntryCatalogProject] = [],
        catalogRecruiters: [DirectEntryCatalogRecruiter] = [],
        catalogBanks: [DirectEntryCatalogBank] = [],
        catalogLaborTypes: [DirectEntryCatalogLaborType] = DirectEntryAssigneeOptions.fallbackLaborTypes
    ) {
        self.projects = projects
        self.managers = managers
        self.recruiters = recruiters
        self.banks = banks
        self.catalogProjects = catalogProjects
        self.catalogRecruiters = catalogRecruiters
        self.catalogBanks = catalogBanks
        self.catalogLaborTypes = catalogLaborTypes.isEmpty ? Self.fallbackLaborTypes : catalogLaborTypes
    }
}

public struct DirectEntryBatchHistoryItem: Identifiable, Equatable, Sendable {
    public var id: Int64 { batchId }
    public let batchId: Int64
    public let submissionIdUuid: String
    public let status: String
    public let totalRows: Int
    public let approvedRows: Int
    public let pendingRows: Int
    public let createdAt: String

    public init(
        batchId: Int64,
        submissionIdUuid: String = "",
        status: String = "COMPLETED",
        totalRows: Int = 0,
        approvedRows: Int = 0,
        pendingRows: Int = 0,
        createdAt: String = ""
    ) {
        self.batchId = batchId
        self.submissionIdUuid = submissionIdUuid
        self.status = status
        self.totalRows = totalRows
        self.approvedRows = approvedRows
        self.pendingRows = pendingRows
        self.createdAt = createdAt
    }
}

public struct DirectEntryDuplicateCandidate: Identifiable, Equatable, Sendable {
    public let id: String
    public let cccd: String
    public let hoTen: String
    public let duAn: String
    public let maNld: String
    public let trangThai: String

    public init(
        id: String = UUID().uuidString,
        cccd: String,
        hoTen: String,
        duAn: String,
        maNld: String = "",
        trangThai: String = ""
    ) {
        self.id = id
        self.cccd = cccd
        self.hoTen = hoTen
        self.duAn = duAn
        self.maNld = maNld
        self.trangThai = trangThai
    }
}

public struct DirectEntryBatchSubmitOutcome: Equatable, Sendable {
    public let batchId: Int64
    public let submissionIdUuid: String
    public let createdCount: Int
    public let entryIdByRowId: [String: Int64]
    public let entryIdUuidByRowId: [String: String]

    public init(
        batchId: Int64 = 0,
        submissionIdUuid: String = "",
        createdCount: Int,
        entryIdByRowId: [String: Int64] = [:],
        entryIdUuidByRowId: [String: String] = [:]
    ) {
        self.batchId = batchId
        self.submissionIdUuid = submissionIdUuid
        self.createdCount = createdCount
        self.entryIdByRowId = entryIdByRowId
        self.entryIdUuidByRowId = entryIdUuidByRowId
    }
}

public struct WorkerItem: Identifiable, Equatable, Sendable {
    public var id: String { maNld }
    public let maNld: String
    public let employeeCode: String
    public let entryVersion: Int
    public let hoTen: String
    public let duAn: String
    public let quanLy: String
    public let nguoiTuyen: String
    public let ngayVaoLam: String
    public let ngayNghiViec: String
    public let trangThai: String
    public let sdt: String
    public let soCccd: String
    public let ngaySinh: String
    public let gioiTinh: String
    public let queQuan: String
    public let ngayCap: String
    public let nganhNghe: String
    public let nhom: String
    public let soTaiKhoan: String
    public let nganHang: String
    public let chuTaiKhoan: String
    public let ghiChu: String
    public let lyDoNghi: String
    public let phanLoaiNghi: String
    public var contractFileUrl: String?
    public var cccdFrontUrl: String?
    public var cccdBackUrl: String?
    public let uploaderCode: String

    public static func isUuidLike(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count == 36 && trimmed.filter({ $0 == "-" }).count == 4 {
            return true
        }
        return UUID(uuidString: trimmed) != nil
    }

    public var displayEmployeeCode: String {
        let cleanEmp = employeeCode.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanEmp.isEmpty && !Self.isUuidLike(cleanEmp) {
            return cleanEmp
        }
        let cleanMa = maNld.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanMa.isEmpty && !Self.isUuidLike(cleanMa) {
            return cleanMa
        }
        return ""
    }

    public init(
        maNld: String,
        employeeCode: String = "",
        entryVersion: Int = 1,
        hoTen: String = "",
        duAn: String = "",
        quanLy: String = "",
        nguoiTuyen: String = "",
        ngayVaoLam: String = "",
        ngayNghiViec: String = "",
        trangThai: String = "Đang làm việc",
        sdt: String = "",
        soCccd: String = "",
        ngaySinh: String = "",
        gioiTinh: String = "",
        queQuan: String = "",
        ngayCap: String = "",
        nganhNghe: String = "",
        nhom: String = "",
        soTaiKhoan: String = "",
        nganHang: String = "",
        chuTaiKhoan: String = "",
        ghiChu: String = "",
        lyDoNghi: String = "",
        phanLoaiNghi: String = "",
        contractFileUrl: String? = nil,
        cccdFrontUrl: String? = nil,
        cccdBackUrl: String? = nil,
        uploaderCode: String = ""
    ) {
        self.maNld = maNld
        self.employeeCode = employeeCode
        self.entryVersion = entryVersion
        self.hoTen = hoTen
        self.duAn = duAn
        self.quanLy = quanLy
        self.nguoiTuyen = nguoiTuyen
        self.ngayVaoLam = ngayVaoLam
        self.ngayNghiViec = ngayNghiViec
        self.trangThai = trangThai
        self.sdt = sdt
        self.soCccd = soCccd
        self.ngaySinh = ngaySinh
        self.gioiTinh = gioiTinh
        self.queQuan = queQuan
        self.ngayCap = ngayCap
        self.nganhNghe = nganhNghe
        self.nhom = nhom
        self.soTaiKhoan = soTaiKhoan
        self.nganHang = nganHang
        self.chuTaiKhoan = chuTaiKhoan
        self.ghiChu = ghiChu
        self.lyDoNghi = lyDoNghi
        self.phanLoaiNghi = phanLoaiNghi
        self.contractFileUrl = contractFileUrl
        self.cccdFrontUrl = cccdFrontUrl
        self.cccdBackUrl = cccdBackUrl
        self.uploaderCode = uploaderCode
    }
}

// MARK: - Attendance Offline Outbox Model

public struct AttendanceOutboxItem: Identifiable, Codable, Equatable, Sendable {
    public var id: String { localId }
    public let localId: String
    public let userKey: String
    public let idempotencyKey: String
    public let clientCapturedAt: String
    public let timeSource: String
    public var timeToken: String?
    public var anchorSyncedAtEpochMs: Int64?
    public var elapsedSinceAnchorMs: Int64?
    public var deviceClockSkewMs: Int64?
    public let lat: Double
    public let lng: Double
    public let accuracyM: Double?
    public var locationCapturedAt: String?
    public var photoLocalPath: String?
    public let photoSha256: String
    public let photoSizeBytes: Int64
    public let mimeType: String
    public let note: String
    public var uploadedObjectKey: String?
    public var status: String
    public var attemptCount: Int
    public var lastErrorCode: String?
    public var lastErrorMessage: String?
    public var serverRecordId: Int64?
    public var serverCheckinId: String?
    public var serverReceivedAt: String?
    public let createdAtEpochMs: Int64

    public init(
        localId: String,
        userKey: String,
        idempotencyKey: String,
        clientCapturedAt: String,
        timeSource: String,
        timeToken: String? = nil,
        anchorSyncedAtEpochMs: Int64? = nil,
        elapsedSinceAnchorMs: Int64? = nil,
        deviceClockSkewMs: Int64? = nil,
        lat: Double,
        lng: Double,
        accuracyM: Double? = nil,
        locationCapturedAt: String? = nil,
        photoLocalPath: String? = nil,
        photoSha256: String,
        photoSizeBytes: Int64,
        mimeType: String = "image/jpeg",
        note: String = "",
        uploadedObjectKey: String? = nil,
        status: String = "PENDING",
        attemptCount: Int = 0,
        lastErrorCode: String? = nil,
        lastErrorMessage: String? = nil,
        serverRecordId: Int64? = nil,
        serverCheckinId: String? = nil,
        serverReceivedAt: String? = nil,
        createdAtEpochMs: Int64
    ) {
        self.localId = localId
        self.userKey = userKey
        self.idempotencyKey = idempotencyKey
        self.clientCapturedAt = clientCapturedAt
        self.timeSource = timeSource
        self.timeToken = timeToken
        self.anchorSyncedAtEpochMs = anchorSyncedAtEpochMs
        self.elapsedSinceAnchorMs = elapsedSinceAnchorMs
        self.deviceClockSkewMs = deviceClockSkewMs
        self.lat = lat
        self.lng = lng
        self.accuracyM = accuracyM
        self.locationCapturedAt = locationCapturedAt
        self.photoLocalPath = photoLocalPath
        self.photoSha256 = photoSha256
        self.photoSizeBytes = photoSizeBytes
        self.mimeType = mimeType
        self.note = note
        self.uploadedObjectKey = uploadedObjectKey
        self.status = status
        self.attemptCount = attemptCount
        self.lastErrorCode = lastErrorCode
        self.lastErrorMessage = lastErrorMessage
        self.serverRecordId = serverRecordId
        self.serverCheckinId = serverCheckinId
        self.serverReceivedAt = serverReceivedAt
        self.createdAtEpochMs = createdAtEpochMs
    }
}

// MARK: - Work Reports & Overdue Models

public struct WorkReportFieldOption: Identifiable, Equatable, Sendable {
    public var id: String { value }
    public let value: String
    public let label: String

    public init(value: String, label: String) {
        self.value = value
        self.label = label
    }
}

public struct WorkReportFieldSchema: Identifiable, Equatable, Sendable {
    public let id: String
    public let label: String
    public let type: String
    public let required: Bool
    public let placeholder: String?
    public let defaultValue: String?
    public let options: [WorkReportFieldOption]
    public let min: Double?
    public let max: Double?

    public var isBooleanType: Bool {
        ["boolean", "bool", "checkbox", "switch"].contains(type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
    }

    public init(
        id: String,
        label: String,
        type: String = "text",
        required: Bool = false,
        placeholder: String? = nil,
        defaultValue: String? = nil,
        options: [WorkReportFieldOption] = [],
        min: Double? = nil,
        max: Double? = nil
    ) {
        self.id = id
        self.label = label
        self.type = type
        self.required = required
        self.placeholder = placeholder
        self.defaultValue = defaultValue
        self.options = options
        self.min = min
        self.max = max
    }
}

public struct WorkReportSectionSchema: Identifiable, Equatable, Sendable {
    public let id: String
    public let title: String
    public let description: String?
    public let fields: [WorkReportFieldSchema]

    public init(
        id: String,
        title: String,
        description: String? = nil,
        fields: [WorkReportFieldSchema]
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.fields = fields
    }
}

public struct WorkReportObligationItem: Identifiable, Equatable, Sendable {
    public let id: Int64
    public var obligationIdUuid: String
    public var submissionId: Int64?
    public var submissionIdUuid: String?
    public var submissionVersion: Int?
    public var templateCode: String
    public var templateName: String
    public var periodType: String
    public var periodKey: String
    public var dueAt: String?
    public var submissionState: String
    public var isOverdue: Bool
    public var version: Int
    public var submittedAt: String?
    public var reviewedAt: String?
    public var reviewerName: String?
    public var reviewComment: String?
    public var unitName: String?
    public var sections: [WorkReportSectionSchema]
    public var formPayload: [String: String]

    public init(
        id: Int64,
        obligationIdUuid: String = "",
        submissionId: Int64? = nil,
        submissionIdUuid: String? = nil,
        submissionVersion: Int? = nil,
        templateCode: String = "",
        templateName: String,
        periodType: String = "WEEKLY",
        periodKey: String = "",
        dueAt: String? = nil,
        submissionState: String = "NOT_STARTED",
        isOverdue: Bool = false,
        version: Int = 1,
        submittedAt: String? = nil,
        reviewedAt: String? = nil,
        reviewerName: String? = nil,
        reviewComment: String? = nil,
        unitName: String? = nil,
        sections: [WorkReportSectionSchema] = [],
        formPayload: [String: String] = [:]
    ) {
        self.id = id
        self.obligationIdUuid = obligationIdUuid.isEmpty ? String(id) : obligationIdUuid
        self.submissionId = submissionId
        self.submissionIdUuid = submissionIdUuid
        self.submissionVersion = submissionVersion
        self.templateCode = templateCode
        self.templateName = templateName
        self.periodType = periodType
        self.periodKey = periodKey
        self.dueAt = dueAt
        self.submissionState = submissionState
        self.isOverdue = isOverdue
        self.version = version
        self.submittedAt = submittedAt
        self.reviewedAt = reviewedAt
        self.reviewerName = reviewerName
        self.reviewComment = reviewComment
        self.unitName = unitName
        self.sections = sections
        self.formPayload = formPayload
    }
}

public struct WorkReportReviewItem: Identifiable, Equatable, Sendable {
    public let id: Int64
    public let submissionIdUuid: String
    public let obligationId: Int64
    public let obligationIdUuid: String
    public let templateName: String
    public let periodType: String
    public let periodKey: String
    public let reporterName: String
    public let reporterCode: String
    public let unitName: String
    public let submittedAt: String?
    public let submissionState: String
    public let version: Int
    public let sections: [WorkReportSectionSchema]
    public let formPayload: [String: String]

    public init(
        id: Int64,
        submissionIdUuid: String = "",
        obligationId: Int64,
        obligationIdUuid: String = "",
        templateName: String,
        periodType: String = "WEEKLY",
        periodKey: String = "",
        reporterName: String = "",
        reporterCode: String = "",
        unitName: String = "",
        submittedAt: String? = nil,
        submissionState: String = "SUBMITTED",
        version: Int = 1,
        sections: [WorkReportSectionSchema] = [],
        formPayload: [String: String] = [:]
    ) {
        self.id = id
        self.submissionIdUuid = submissionIdUuid.isEmpty ? String(id) : submissionIdUuid
        self.obligationId = obligationId
        self.obligationIdUuid = obligationIdUuid.isEmpty ? String(obligationId) : obligationIdUuid
        self.templateName = templateName
        self.periodType = periodType
        self.periodKey = periodKey
        self.reporterName = reporterName
        self.reporterCode = reporterCode
        self.unitName = unitName
        self.submittedAt = submittedAt
        self.submissionState = submissionState
        self.version = version
        self.sections = sections
        self.formPayload = formPayload
    }
}

public struct WorkReportTemplateOption: Identifiable, Equatable, Sendable {
    public var id: String { code }
    public let code: String
    public let name: String
    public let periodType: String

    public init(code: String, name: String, periodType: String = "WEEKLY") {
        self.code = code
        self.name = name
        self.periodType = periodType
    }
}

public struct WorkReportAnalyticsKpis: Equatable, Sendable {
    public let totalObligations: Int
    public let submittedCount: Int
    public let approvedCount: Int
    public let rejectedCount: Int
    public let overdueCount: Int
    public let completionRate: Double
    public let onTimeRate: Double

    public init(
        totalObligations: Int = 0,
        submittedCount: Int = 0,
        approvedCount: Int = 0,
        rejectedCount: Int = 0,
        overdueCount: Int = 0,
        completionRate: Double = 0.0,
        onTimeRate: Double = 0.0
    ) {
        self.totalObligations = totalObligations
        self.submittedCount = submittedCount
        self.approvedCount = approvedCount
        self.rejectedCount = rejectedCount
        self.overdueCount = overdueCount
        self.completionRate = completionRate
        self.onTimeRate = onTimeRate
    }
}

public struct WorkReportAnalyticsGroupRow: Identifiable, Equatable, Sendable {
    public let id: String
    public let groupLabel: String
    public let totalObligations: Int
    public let submittedCount: Int
    public let approvedCount: Int
    public let overdueCount: Int
    public let completionRate: Double

    public init(
        id: String,
        groupLabel: String,
        totalObligations: Int = 0,
        submittedCount: Int = 0,
        approvedCount: Int = 0,
        overdueCount: Int = 0,
        completionRate: Double = 0.0
    ) {
        self.id = id
        self.groupLabel = groupLabel
        self.totalObligations = totalObligations
        self.submittedCount = submittedCount
        self.approvedCount = approvedCount
        self.overdueCount = overdueCount
        self.completionRate = completionRate
    }
}

public struct WorkReportsAnalyticsSnapshot: Equatable, Sendable {
    public let kpis: WorkReportAnalyticsKpis
    public let byUnit: [WorkReportAnalyticsGroupRow]
    public let byTemplate: [WorkReportAnalyticsGroupRow]

    public init(
        kpis: WorkReportAnalyticsKpis = WorkReportAnalyticsKpis(),
        byUnit: [WorkReportAnalyticsGroupRow] = [],
        byTemplate: [WorkReportAnalyticsGroupRow] = []
    ) {
        self.kpis = kpis
        self.byUnit = byUnit
        self.byTemplate = byTemplate
    }
}

// MARK: - Real-time & In-App Notification Model

public struct AppNotificationItem: Identifiable, Equatable, Sendable {
    public let id: Int64
    public let title: String
    public let body: String
    public let type: String
    public var isRead: Bool
    public let createdAt: String
    public let targetRoute: String?
    public let targetEntityId: String?
    public let metadata: [String: String]

    public init(
        id: Int64,
        title: String,
        body: String = "",
        type: String = "GENERAL",
        isRead: Bool = false,
        createdAt: String = "",
        targetRoute: String? = nil,
        targetEntityId: String? = nil,
        metadata: [String: String] = [:]
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.type = type
        self.isRead = isRead
        self.createdAt = createdAt
        self.targetRoute = targetRoute
        self.targetEntityId = targetEntityId
        self.metadata = metadata
    }
}

// MARK: - Safe Dictionary JSON Extraction Extensions

public extension Dictionary where Key == String, Value == Any {
    func optString(_ keys: String..., defaultValue: String = "") -> String {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let s = raw as? String {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { return trimmed }
            } else if let n = raw as? NSNumber {
                return n.stringValue
            }
        }
        return defaultValue
    }

    func optNullableString(_ keys: String...) -> String? {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let s = raw as? String {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty && trimmed.lowercased() != "null" {
                    return trimmed
                }
            } else if let n = raw as? NSNumber {
                return n.stringValue
            }
        }
        return nil
    }

    func optInt(_ keys: String..., defaultValue: Int = 0) -> Int {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let i = raw as? Int { return i }
            if let n = raw as? NSNumber { return n.intValue }
            if let s = raw as? String, let parsed = Int(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return parsed
            }
        }
        return defaultValue
    }

    func optInt64(_ keys: String...) -> Int64? {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let i64 = raw as? Int64 { return i64 }
            if let i = raw as? Int { return Int64(i) }
            if let n = raw as? NSNumber { return n.int64Value }
            if let s = raw as? String {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if let parsed = Int64(trimmed) {
                    return parsed
                }
                if !trimmed.isEmpty && trimmed.lowercased() != "null" {
                    var hash: UInt64 = 14695981039346656037
                    for byte in trimmed.utf8 {
                        hash ^= UInt64(byte)
                        hash = hash &* 1099511628211
                    }
                    let positive = Int64(hash & 0x3FFFFFFFFFFFFFFF)
                    return positive == 0 ? 1 : positive
                }
            }
        }
        return nil
    }

    func optDouble(_ keys: String..., defaultValue: Double = 0.0) -> Double {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let d = raw as? Double { return d }
            if let n = raw as? NSNumber { return n.doubleValue }
            if let s = raw as? String, let parsed = Double(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return parsed
            }
        }
        return defaultValue
    }

    func optNullableDouble(_ keys: String...) -> Double? {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let d = raw as? Double { return d }
            if let n = raw as? NSNumber { return n.doubleValue }
            if let s = raw as? String, let parsed = Double(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return parsed
            }
        }
        return nil
    }

    func optBool(_ keys: String..., defaultValue: Bool = false) -> Bool {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let b = raw as? Bool { return b }
            if let n = raw as? NSNumber { return n.boolValue }
            if let s = raw as? String {
                let lower = s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if ["true", "1", "yes"].contains(lower) { return true }
                if ["false", "0", "no"].contains(lower) { return false }
            }
        }
        return defaultValue
    }

    func optStringArray(_ keys: String...) -> [String] {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let arr = raw as? [String] {
                return arr.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
            }
            if let anyArr = raw as? [Any] {
                let mapped = anyArr.compactMap { elem -> String? in
                    if let s = elem as? String {
                        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
                        return t.isEmpty ? nil : t
                    }
                    if let n = elem as? NSNumber {
                        return n.stringValue
                    }
                    return nil
                }
                if !mapped.isEmpty { return mapped }
            }
        }
        return []
    }

    func optObjectArray(_ keys: String...) -> [[String: Any]] {
        for key in keys {
            guard let raw = self[key], !(raw is NSNull) else { continue }
            if let arr = raw as? [[String: Any]] {
                return arr
            }
            if let anyArr = raw as? [Any] {
                let dicts = anyArr.compactMap { $0 as? [String: Any] }
                if !dicts.isEmpty { return dicts }
            }
        }
        return []
    }
}
