import Foundation

// MARK: - Attendance Area Tabs & Leave Models / Contract / Service
// Mirrors Android's `data/LeaveModelsAndService.kt` and Backend's `src/lib/leave/leave-contract.ts` 100%

public enum AttendanceAreaTab: String, CaseIterable, Identifiable, Equatable, Sendable {
    case checkIn = "CHECK_IN"
    case leaveRequest = "LEAVE_REQUEST"
    case leaveApproval = "LEAVE_APPROVAL"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .checkIn:
            return "Chấm công"
        case .leaveRequest:
            return "Xin nghỉ"
        case .leaveApproval:
            return "Duyệt nghỉ"
        }
    }

    public var systemIconName: String {
        switch self {
        case .checkIn:
            return "camera.fill"
        case .leaveRequest:
            return "calendar.badge.plus"
        case .leaveApproval:
            return "checkmark.seal.fill"
        }
    }
}

// MARK: - Leave Projection Models

public struct LeaveAccountProjection: Equatable, Sendable {
    public let appUserId: String
    public let displayName: String
    public let loginId: String?
    public let personnelCode: String?
    public let recruiterId: String?
    public let accountEnabled: Bool

    public init(
        appUserId: String,
        displayName: String,
        loginId: String? = nil,
        personnelCode: String? = nil,
        recruiterId: String? = nil,
        accountEnabled: Bool = true
    ) {
        self.appUserId = appUserId
        self.displayName = displayName
        self.loginId = loginId
        self.personnelCode = personnelCode
        self.recruiterId = recruiterId
        self.accountEnabled = accountEnabled
    }
}

public struct LeaveAuthorityProjection: Equatable, Sendable {
    public let appUserId: String
    public let isSharedNonEmployee: Bool
    public let isFullAdmin: Bool
    public let isBod: Bool
    public let isHrOperator: Bool
    public let isHrReportViewer: Bool
    public let isTeamLeader: Bool
    public let isDepartmentApprover: Bool
    public let canSubmitRequest: Bool
    public let canApproveRequests: Bool
    public let canViewMonthlyReport: Bool
    public let canManageConfig: Bool

    public init(
        appUserId: String,
        isSharedNonEmployee: Bool = false,
        isFullAdmin: Bool = false,
        isBod: Bool = false,
        isHrOperator: Bool = false,
        isHrReportViewer: Bool = false,
        isTeamLeader: Bool = false,
        isDepartmentApprover: Bool = false,
        canSubmitRequest: Bool = true,
        canApproveRequests: Bool = false,
        canViewMonthlyReport: Bool = false,
        canManageConfig: Bool = false
    ) {
        self.appUserId = appUserId
        self.isSharedNonEmployee = isSharedNonEmployee
        self.isFullAdmin = isFullAdmin
        self.isBod = isBod
        self.isHrOperator = isHrOperator
        self.isHrReportViewer = isHrReportViewer
        self.isTeamLeader = isTeamLeader
        self.isDepartmentApprover = isDepartmentApprover
        self.canSubmitRequest = canSubmitRequest
        self.canApproveRequests = canApproveRequests
        self.canViewMonthlyReport = canViewMonthlyReport
        self.canManageConfig = canManageConfig
    }
}

public struct LeaveRoutingProjection: Equatable, Sendable {
    public let routeKind: String
    public let unitKind: String
    public let primaryApproverAppUserId: String?
    public let teamId: String?
    public let teamCode: String?
    public let teamName: String?
    public let fallbackReason: String?
    public let profileConfigured: Bool

    public init(
        routeKind: String = "ADMIN_BOD_FALLBACK",
        unitKind: String = "UNCLASSIFIED",
        primaryApproverAppUserId: String? = nil,
        teamId: String? = nil,
        teamCode: String? = nil,
        teamName: String? = nil,
        fallbackReason: String? = nil,
        profileConfigured: Bool = false
    ) {
        self.routeKind = routeKind
        self.unitKind = unitKind
        self.primaryApproverAppUserId = primaryApproverAppUserId
        self.teamId = teamId
        self.teamCode = teamCode
        self.teamName = teamName
        self.fallbackReason = fallbackReason
        self.profileConfigured = profileConfigured
    }
}

public struct LeavePolicyProjection: Equatable, Sendable {
    public let annualBaseUnits: Int
    public let monthlyAccrualUnits: Int
    public let defaultSeniorityYears: Int
    public let defaultSeniorityStepUnits: Int
    public let carryOverExpiryMonth: Int?
    public let carryOverExpiryDay: Int?
    public let carryOverExpiresAfterDate: String?
    public let rowVersion: Int

    public init(
        annualBaseUnits: Int = 24,
        monthlyAccrualUnits: Int = 2,
        defaultSeniorityYears: Int = 5,
        defaultSeniorityStepUnits: Int = 2,
        carryOverExpiryMonth: Int? = nil,
        carryOverExpiryDay: Int? = nil,
        carryOverExpiresAfterDate: String? = nil,
        rowVersion: Int = 1
    ) {
        self.annualBaseUnits = annualBaseUnits
        self.monthlyAccrualUnits = monthlyAccrualUnits
        self.defaultSeniorityYears = defaultSeniorityYears
        self.defaultSeniorityStepUnits = defaultSeniorityStepUnits
        self.carryOverExpiryMonth = carryOverExpiryMonth
        self.carryOverExpiryDay = carryOverExpiryDay
        self.carryOverExpiresAfterDate = carryOverExpiresAfterDate
        self.rowVersion = rowVersion
    }
}

public struct LeaveLotProjection: Identifiable, Equatable, Sendable {
    public var id: String { lotId }
    public let lotId: String
    public let leaveYear: Int
    public let lotKind: String
    public let accrualMonth: Int?
    public let grantedUnits: Int
    public let reservedUnits: Int
    public let consumedUnits: Int
    public let expiredUnits: Int
    public let availableUnits: Int
    public let usableFromDate: String
    public let expiresAfterDate: String?
    public let sourceRef: String?

    public init(
        lotId: String,
        leaveYear: Int,
        lotKind: String,
        accrualMonth: Int? = nil,
        grantedUnits: Int = 0,
        reservedUnits: Int = 0,
        consumedUnits: Int = 0,
        expiredUnits: Int = 0,
        availableUnits: Int = 0,
        usableFromDate: String = "",
        expiresAfterDate: String? = nil,
        sourceRef: String? = nil
    ) {
        self.lotId = lotId
        self.leaveYear = leaveYear
        self.lotKind = lotKind
        self.accrualMonth = accrualMonth
        self.grantedUnits = grantedUnits
        self.reservedUnits = reservedUnits
        self.consumedUnits = consumedUnits
        self.expiredUnits = expiredUnits
        self.availableUnits = availableUnits
        self.usableFromDate = usableFromDate
        self.expiresAfterDate = expiresAfterDate
        self.sourceRef = sourceRef
    }
}

public struct LeaveBalanceProjection: Equatable, Sendable {
    public let leaveYear: Int
    public let asOfDate: String
    public let hasProfile: Bool
    public let profileConfigured: Bool
    public let employmentStatus: String
    public let unitKind: String
    public let workStartDate: String?
    public let officialEffectiveDate: String?
    public let nextSeniorityEffectiveDate: String?
    public let nextSeniorityUnits: Int
    public let totalGrantedUnits: Int
    public let carryOverGrantedUnits: Int
    public let carryOverAvailableUnits: Int
    public let monthlyAccruedUnits: Int
    public let seniorityGrantedUnits: Int
    public let openingOrAdjustmentUnits: Int
    public let availableUnits: Int
    public let reservedUnits: Int
    public let consumedUnits: Int
    public let expiredUnits: Int
    public let pendingAnnualUnits: Int
    public let pendingNonAnnualUnits: Int
    public let approvedNonAnnualUnits: Int
    public let pendingPaidUnits: Int
    public let pendingUnpaidUnits: Int
    public let approvedUnpaidUnits: Int
    public let lots: [LeaveLotProjection]

    public init(
        leaveYear: Int,
        asOfDate: String = "",
        hasProfile: Bool = false,
        profileConfigured: Bool = false,
        employmentStatus: String = "UNCONFIGURED",
        unitKind: String = "UNCLASSIFIED",
        workStartDate: String? = nil,
        officialEffectiveDate: String? = nil,
        nextSeniorityEffectiveDate: String? = nil,
        nextSeniorityUnits: Int = 0,
        totalGrantedUnits: Int = 0,
        carryOverGrantedUnits: Int = 0,
        carryOverAvailableUnits: Int = 0,
        monthlyAccruedUnits: Int = 0,
        seniorityGrantedUnits: Int = 0,
        openingOrAdjustmentUnits: Int = 0,
        availableUnits: Int = 0,
        reservedUnits: Int = 0,
        consumedUnits: Int = 0,
        expiredUnits: Int = 0,
        pendingAnnualUnits: Int = 0,
        pendingNonAnnualUnits: Int = 0,
        approvedNonAnnualUnits: Int = 0,
        pendingPaidUnits: Int = 0,
        pendingUnpaidUnits: Int = 0,
        approvedUnpaidUnits: Int = 0,
        lots: [LeaveLotProjection] = []
    ) {
        self.leaveYear = leaveYear
        self.asOfDate = asOfDate
        self.hasProfile = hasProfile
        self.profileConfigured = profileConfigured
        self.employmentStatus = employmentStatus
        self.unitKind = unitKind
        self.workStartDate = workStartDate
        self.officialEffectiveDate = officialEffectiveDate
        self.nextSeniorityEffectiveDate = nextSeniorityEffectiveDate
        self.nextSeniorityUnits = nextSeniorityUnits
        self.totalGrantedUnits = totalGrantedUnits
        self.carryOverGrantedUnits = carryOverGrantedUnits
        self.carryOverAvailableUnits = carryOverAvailableUnits
        self.monthlyAccruedUnits = monthlyAccruedUnits
        self.seniorityGrantedUnits = seniorityGrantedUnits
        self.openingOrAdjustmentUnits = openingOrAdjustmentUnits
        self.availableUnits = availableUnits
        self.reservedUnits = reservedUnits
        self.consumedUnits = consumedUnits
        self.expiredUnits = expiredUnits
        self.pendingAnnualUnits = pendingAnnualUnits
        self.pendingNonAnnualUnits = pendingNonAnnualUnits
        self.approvedNonAnnualUnits = approvedNonAnnualUnits
        self.pendingPaidUnits = pendingPaidUnits
        self.pendingUnpaidUnits = pendingUnpaidUnits
        self.approvedUnpaidUnits = approvedUnpaidUnits
        self.lots = lots
    }
}

public struct LeaveRequestDayProjection: Identifiable, Equatable, Sendable {
    public var id: String { requestDayId.isEmpty ? "\(leaveDate)-\(sessionCode)-\(leaveType)" : requestDayId }
    public let requestDayId: String
    public let leaveDate: String
    public let sessionCode: String
    public let leaveType: String
    public let halfDayUnits: Int
    public let isActive: Bool

    public init(
        requestDayId: String = "",
        leaveDate: String,
        sessionCode: String = "FULL",
        leaveType: String = "ANNUAL",
        halfDayUnits: Int = 2,
        isActive: Bool = true
    ) {
        self.requestDayId = requestDayId
        self.leaveDate = leaveDate
        self.sessionCode = sessionCode
        self.leaveType = leaveType
        self.halfDayUnits = halfDayUnits
        self.isActive = isActive
    }
}

public struct LeaveRequestTypeBreakdownProjection: Identifiable, Equatable, Sendable {
    public var id: String { leaveType }
    public let leaveType: String
    public let halfDayUnits: Int

    public init(leaveType: String, halfDayUnits: Int) {
        self.leaveType = leaveType
        self.halfDayUnits = halfDayUnits
    }
}

public struct LeaveRequestAllocationProjection: Identifiable, Equatable, Sendable {
    public var id: String { allocationId }
    public let allocationId: String
    public let requestDayId: String
    public let lotId: String
    public let lotKind: String
    public let leaveDate: String
    public let allocatedUnits: Int
    public let status: String
    public let settledAt: String?
    public let releasedAt: String?

    public init(
        allocationId: String,
        requestDayId: String,
        lotId: String,
        lotKind: String,
        leaveDate: String,
        allocatedUnits: Int,
        status: String,
        settledAt: String? = nil,
        releasedAt: String? = nil
    ) {
        self.allocationId = allocationId
        self.requestDayId = requestDayId
        self.lotId = lotId
        self.lotKind = lotKind
        self.leaveDate = leaveDate
        self.allocatedUnits = allocatedUnits
        self.status = status
        self.settledAt = settledAt
        self.releasedAt = releasedAt
    }
}

public struct LeaveRequestProjection: Identifiable, Equatable, Sendable {
    public var id: String { requestId }
    public let requestId: String
    public let appUserId: String
    public let requester: LeaveAccountProjection?
    public let status: String
    public let payTreatment: String
    public let leaveTypes: [String]
    public let typeBreakdown: [LeaveRequestTypeBreakdownProjection]
    public let requestedAnnualUnits: Int
    public let requestedNonAnnualUnits: Int
    public let employmentStatusSnapshot: String
    public let unitKindSnapshot: String
    public let profileConfiguredAtSubmit: Bool
    public let requestedUnits: Int
    public let reason: String
    public let routeKind: String
    public let primaryApproverAppUserId: String?
    public let primaryApprover: LeaveAccountProjection?
    public let routedTeamId: String?
    public let routedTeamCode: String?
    public let routedTeamName: String?
    public let fallbackReason: String?
    public let submittedAt: String
    public let decidedAt: String?
    public let decidedByAppUserId: String?
    public let decidedBy: LeaveAccountProjection?
    public let decisionReason: String?
    public let cancelledAt: String?
    public let cancelledByAppUserId: String?
    public let cancellationReason: String?
    public let rowVersion: Int
    public let days: [LeaveRequestDayProjection]
    public let allocations: [LeaveRequestAllocationProjection]

    public init(
        requestId: String,
        appUserId: String,
        requester: LeaveAccountProjection? = nil,
        status: String = "PENDING",
        payTreatment: String = "PAID_LEAVE",
        leaveTypes: [String] = [],
        typeBreakdown: [LeaveRequestTypeBreakdownProjection] = [],
        requestedAnnualUnits: Int = 0,
        requestedNonAnnualUnits: Int = 0,
        employmentStatusSnapshot: String = "UNCONFIGURED",
        unitKindSnapshot: String = "UNCLASSIFIED",
        profileConfiguredAtSubmit: Bool = false,
        requestedUnits: Int = 2,
        reason: String = "",
        routeKind: String = "",
        primaryApproverAppUserId: String? = nil,
        primaryApprover: LeaveAccountProjection? = nil,
        routedTeamId: String? = nil,
        routedTeamCode: String? = nil,
        routedTeamName: String? = nil,
        fallbackReason: String? = nil,
        submittedAt: String = "",
        decidedAt: String? = nil,
        decidedByAppUserId: String? = nil,
        decidedBy: LeaveAccountProjection? = nil,
        decisionReason: String? = nil,
        cancelledAt: String? = nil,
        cancelledByAppUserId: String? = nil,
        cancellationReason: String? = nil,
        rowVersion: Int = 1,
        days: [LeaveRequestDayProjection] = [],
        allocations: [LeaveRequestAllocationProjection] = []
    ) {
        self.requestId = requestId
        self.appUserId = appUserId
        self.requester = requester
        self.status = status
        self.payTreatment = payTreatment
        self.leaveTypes = leaveTypes
        self.typeBreakdown = typeBreakdown
        self.requestedAnnualUnits = requestedAnnualUnits
        self.requestedNonAnnualUnits = requestedNonAnnualUnits
        self.employmentStatusSnapshot = employmentStatusSnapshot
        self.unitKindSnapshot = unitKindSnapshot
        self.profileConfiguredAtSubmit = profileConfiguredAtSubmit
        self.requestedUnits = requestedUnits
        self.reason = reason
        self.routeKind = routeKind
        self.primaryApproverAppUserId = primaryApproverAppUserId
        self.primaryApprover = primaryApprover
        self.routedTeamId = routedTeamId
        self.routedTeamCode = routedTeamCode
        self.routedTeamName = routedTeamName
        self.fallbackReason = fallbackReason
        self.submittedAt = submittedAt
        self.decidedAt = decidedAt
        self.decidedByAppUserId = decidedByAppUserId
        self.decidedBy = decidedBy
        self.decisionReason = decisionReason
        self.cancelledAt = cancelledAt
        self.cancelledByAppUserId = cancelledByAppUserId
        self.cancellationReason = cancellationReason
        self.rowVersion = rowVersion
        self.days = days
        self.allocations = allocations
    }
}

public struct LeaveLedgerEntryProjection: Identifiable, Equatable, Sendable {
    public var id: String { ledgerEntryId }
    public let ledgerEntryId: String
    public let lotId: String?
    public let requestId: String?
    public let entryType: String
    public let effectiveDate: String
    public let deltaAvailableUnits: Int
    public let deltaReservedUnits: Int
    public let deltaConsumedUnits: Int
    public let deltaExpiredUnits: Int
    public let reason: String?
    public let createdAt: String

    public init(
        ledgerEntryId: String,
        lotId: String? = nil,
        requestId: String? = nil,
        entryType: String,
        effectiveDate: String,
        deltaAvailableUnits: Int = 0,
        deltaReservedUnits: Int = 0,
        deltaConsumedUnits: Int = 0,
        deltaExpiredUnits: Int = 0,
        reason: String? = nil,
        createdAt: String = ""
    ) {
        self.ledgerEntryId = ledgerEntryId
        self.lotId = lotId
        self.requestId = requestId
        self.entryType = entryType
        self.effectiveDate = effectiveDate
        self.deltaAvailableUnits = deltaAvailableUnits
        self.deltaReservedUnits = deltaReservedUnits
        self.deltaConsumedUnits = deltaConsumedUnits
        self.deltaExpiredUnits = deltaExpiredUnits
        self.reason = reason
        self.createdAt = createdAt
    }
}

public struct LeaveMeSummaryProjection: Equatable, Sendable {
    public let asOfDate: String
    public let leaveYear: Int
    public let account: LeaveAccountProjection?
    public let authority: LeaveAuthorityProjection
    public let routing: LeaveRoutingProjection
    public let policy: LeavePolicyProjection
    public let balance: LeaveBalanceProjection
    public let requests: [LeaveRequestProjection]
    public let ledgerEntries: [LeaveLedgerEntryProjection]

    public init(
        asOfDate: String,
        leaveYear: Int,
        account: LeaveAccountProjection? = nil,
        authority: LeaveAuthorityProjection,
        routing: LeaveRoutingProjection = LeaveRoutingProjection(),
        policy: LeavePolicyProjection = LeavePolicyProjection(),
        balance: LeaveBalanceProjection,
        requests: [LeaveRequestProjection] = [],
        ledgerEntries: [LeaveLedgerEntryProjection] = []
    ) {
        self.asOfDate = asOfDate
        self.leaveYear = leaveYear
        self.account = account
        self.authority = authority
        self.routing = routing
        self.policy = policy
        self.balance = balance
        self.requests = requests
        self.ledgerEntries = ledgerEntries
    }
}

public struct LeaveApprovalInboxProjection: Equatable, Sendable {
    public let asOfDate: String
    public let statusFilter: String
    public let total: Int
    public let limit: Int
    public let offset: Int
    public let items: [LeaveRequestProjection]

    public init(
        asOfDate: String = "",
        statusFilter: String = "PENDING",
        total: Int = 0,
        limit: Int = 50,
        offset: Int = 0,
        items: [LeaveRequestProjection] = []
    ) {
        self.asOfDate = asOfDate
        self.statusFilter = statusFilter
        self.total = total
        self.limit = limit
        self.offset = offset
        self.items = items
    }
}

public struct LeaveDraftDayInput: Identifiable, Equatable, Sendable {
    public let id: String
    public var leaveDate: String
    public var sessionCode: String
    public var leaveType: String

    public init(
        id: String = UUID().uuidString,
        leaveDate: String,
        sessionCode: String = "FULL",
        leaveType: String = "ANNUAL"
    ) {
        self.id = id
        self.leaveDate = leaveDate
        self.sessionCode = sessionCode
        self.leaveType = leaveType
    }
}

public struct LeaveRequestDraftEvaluation: Equatable, Sendable {
    public let isValid: Bool
    public let errorMessage: String?
    public let totalUnits: Int
    public let annualUnits: Int
    public let nonAnnualUnits: Int
    public let excessAnnualUnits: Int
    public let canSplitExcessAnnualToUnpaid: Bool
    public let typeBreakdown: [LeaveRequestTypeBreakdownProjection]

    public init(
        isValid: Bool,
        errorMessage: String? = nil,
        totalUnits: Int = 0,
        annualUnits: Int = 0,
        nonAnnualUnits: Int = 0,
        excessAnnualUnits: Int = 0,
        canSplitExcessAnnualToUnpaid: Bool = false,
        typeBreakdown: [LeaveRequestTypeBreakdownProjection] = []
    ) {
        self.isValid = isValid
        self.errorMessage = errorMessage
        self.totalUnits = totalUnits
        self.annualUnits = annualUnits
        self.nonAnnualUnits = nonAnnualUnits
        self.excessAnnualUnits = excessAnnualUnits
        self.canSplitExcessAnnualToUnpaid = canSplitExcessAnnualToUnpaid
        self.typeBreakdown = typeBreakdown
    }
}

// MARK: - Leave Contract

public enum LeaveContract {
    public static let sessionCodes: [String] = ["FULL", "AM", "PM"]
    public static let leaveTypes: [String] = ["ANNUAL", "BEREAVEMENT", "MARRIAGE", "SICK", "UNPAID"]
    public static let leaveTypeMaxUnitsPerRequest: [String: Int] = [
        "BEREAVEMENT": 6,
        "MARRIAGE": 6,
        "SICK": 14,
        "UNPAID": 14
    ]
    public static let requestStatusFilters: [String] = [
        "PENDING",
        "APPROVED",
        "REJECTED",
        "CANCELLED",
        "WITHDRAWN",
        "ALL"
    ]
    public static let decisionCodes: [String] = ["APPROVE", "REJECT", "CANCEL_APPROVED"]

    public static let leaveErrorMessagesVi: [String: String] = [
        "UNAUTHENTICATED": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "ACTOR_NOT_AVAILABLE": "Tài khoản hiện không khả dụng hoặc chưa được kích hoạt.",
        "CSRF_ORIGIN_MISMATCH": "Yêu cầu bị từ chối do không hợp lệ nguồn gốc phiên. Vui lòng tải lại.",
        "LEAVE_SHARED_ACCOUNT_FORBIDDEN": "Tài khoản dùng chung (như tài khoản Marketing chung) không thuộc đối tượng quản lý nghỉ phép cá nhân.",
        "LEAVE_IDEMPOTENCY_KEY_INVALID": "Mã định danh yêu cầu không hợp lệ. Vui lòng thử lại.",
        "LEAVE_IDEMPOTENCY_FINGERPRINT_MISMATCH": "Yêu cầu bị trùng mã phiên cũ với nội dung khác. Vui lòng thử lại.",
        "LEAVE_REASON_INVALID": "Lý do hoặc ghi chú không hợp lệ (tối đa 2.000 ký tự).",
        "LEAVE_REASON_REQUIRED": "Lý do hoặc ghi chú không hợp lệ (tối đa 2.000 ký tự).",
        "LEAVE_REQUEST_DAYS_INVALID": "Danh sách ngày xin nghỉ không hợp lệ. Vui lòng chọn từ 1 đến 120 dòng ngày/buổi nghỉ hợp lệ.",
        "LEAVE_REQUEST_YEAR_OUT_OF_RANGE": "Ngày xin nghỉ phải thuộc năm nghiệp vụ hiện hành.",
        "LEAVE_REQUEST_DUPLICATE_DATE": "Không được chọn trùng cùng ngày và buổi nghỉ trong cùng một đơn xin nghỉ.",
        "LEAVE_DATE_SESSION_CONFLICT": "Trong cùng một ngày chỉ được tách tối đa 1 buổi Sáng + 1 buổi Chiều với 2 loại nghỉ khác nhau (hoặc chọn Cả ngày nếu cùng loại nghỉ).",
        "LEAVE_TYPE_INVALID": "Loại ngày nghỉ không hợp lệ.",
        "LEAVE_TYPE_LIMIT_EXCEEDED": "Số ngày nghỉ theo loại đã chọn vượt giới hạn tối đa cho phép trên mỗi đơn (Nghỉ tang/cưới tối đa 3 ngày/đơn; Nghỉ ốm/không lương tối đa 7 ngày/đơn).",
        "LEAVE_REQUEST_OVERLAP": "Ngày hoặc buổi xin nghỉ bị trùng với một đơn đang chờ duyệt hoặc đã được duyệt.",
        "LEAVE_INSUFFICIENT_BALANCE": "Số dư Phép năm khả dụng không đủ cho các ngày đăng ký loại Phép năm. Vui lòng chuyển phần ngày vượt sang Nghỉ không lương hoặc loại nghỉ phù hợp.",
        "LEAVE_REQUEST_NOT_FOUND": "Không tìm thấy đơn xin nghỉ phép.",
        "LEAVE_REQUEST_OWNERSHIP_FORBIDDEN": "Bạn chỉ được phép rút đơn xin nghỉ phép của chính mình.",
        "LEAVE_ROW_VERSION_CONFLICT": "Dữ liệu vừa được cập nhật bởi thao tác khác. Hệ thống đã làm mới, vui lòng kiểm tra và thử lại.",
        "LEAVE_INVALID_STATE_TRANSITION": "Trạng thái đơn hiện tại không cho phép thực hiện thao tác này.",
        "LEAVE_APPROVAL_FORBIDDEN": "Bạn không có thẩm quyền duyệt hoặc xử lý đơn nghỉ phép này.",
        "LEAVE_SELF_APPROVAL_FORBIDDEN": "Người duyệt không được tự duyệt hoặc tự hủy đơn nghỉ phép của chính mình.",
        "LEAVE_DECISION_INVALID": "Quyết định xử lý đơn không hợp lệ.",
        "LEAVE_FILTER_INVALID": "Bộ lọc truy vấn không hợp lệ.",
        "LEAVE_ADMIN_FORBIDDEN": "Bạn không có quyền truy cập khu vực cấu hình quản trị nghỉ phép.",
        "LEAVE_POLICY_ADMIN_FORBIDDEN": "Chỉ Admin hoặc Ban Giám đốc mới có quyền cập nhật chính sách nghỉ phép.",
        "LEAVE_POLICY_EXPIRY_INVALID": "Ngày/tháng hết hạn phép tồn năm trước không hợp lệ (tháng 1–12, ngày 1–31).",
        "LEAVE_TARGET_USER_NOT_FOUND": "Không tìm thấy tài khoản nhân sự cần cấu hình.",
        "LEAVE_PROFILE_INVALID": "Thông tin cấu hình hồ sơ nghỉ phép không hợp lệ.",
        "LEAVE_PROFILE_WORK_START_DATE_REQUIRED": "Nhân sự Thử việc hoặc Chính thức bắt buộc phải có Ngày bắt đầu làm việc.",
        "LEAVE_PROFILE_OFFICIAL_DATE_INVALID": "Ngày lên chính thức không hợp lệ (phải từ ngày bắt đầu làm việc trở đi).",
        "LEAVE_SENIORITY_CONFIG_FORBIDDEN": "Chỉ Admin hoặc Ban Giám đốc mới được cấu hình ngày và số phép tăng thâm niên tiếp theo.",
        "LEAVE_DEPARTMENT_APPROVER_ADMIN_FORBIDDEN": "Chỉ Admin hoặc Ban Giám đốc mới được cấu hình người duyệt bộ phận Marketing / IT / Kế toán.",
        "LEAVE_UNIT_KIND_INVALID": "Đơn vị / bộ phận không hợp lệ.",
        "LEAVE_APPROVER_USER_NOT_FOUND": "Không tìm thấy tài khoản người duyệt hoặc tài khoản đang bị vô hiệu hóa.",
        "LEAVE_MODULE_GRANT_ADMIN_FORBIDDEN": "Chỉ Full Admin mới có quyền cấp hoặc thu hồi quyền nội bộ phân hệ nghỉ phép.",
        "LEAVE_MODULE_GRANT_INVALID": "Thông tin cấp quyền nội bộ không hợp lệ.",
        "LEAVE_MODULE_GRANT_NOT_FOUND": "Không tìm thấy bản ghi quyền nội bộ đang hiệu lực để thu hồi.",
        "LEAVE_YEAR_INVALID": "Năm nghiệp vụ nghỉ phép không hợp lệ.",
        "LEAVE_OPENING_ROWS_INVALID": "Danh sách dòng số dư đầu kỳ không hợp lệ (tối đa 300 dòng mỗi lần áp dụng).",
        "LEAVE_CARRY_OVER_EXPIRY_NOT_CONFIGURED": "Chưa cấu hình ngày hết hạn phép chuyển năm. Vui lòng cấu hình Chính sách nghỉ phép trước khi nhập phép tồn năm trước.",
        "LEAVE_OPENING_BATCH_VALIDATION_FAILED": "Lô số dư đầu kỳ còn dòng lỗi. Vui lòng xem trước và xử lý hết lỗi trước khi áp dụng.",
        "LEAVE_REPORT_FORBIDDEN": "Bạn không có quyền xem hoặc xuất Báo cáo chốt công & phép tháng.",
        "LEAVE_REPORT_YEAR_MONTH_INVALID": "Kỳ báo cáo tháng không hợp lệ (định dạng YYYY-MM).",
        "LEAVE_REPORT_EXPORT_LIMIT_EXCEEDED": "Số lượng dòng báo cáo vượt quá giới hạn xuất một lần (5.000 nhân sự). Vui lòng lọc theo bộ phận hoặc từ khóa.",
        "LEAVE_REPORT_EXPORT_INCOMPLETE": "Dữ liệu báo cáo thay đổi trong lúc xuất. Vui lòng thử xuất lại.",
        "LEAVE_UNAVAILABLE": "Hệ thống quản lý nghỉ phép tạm thời không khả dụng. Vui lòng thử lại sau."
    ]

    public static func errorMessageForCode(_ code: String?, fallbackMessage: String? = nil) -> String {
        if let cleanCode = code?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
           !cleanCode.isEmpty,
           let mapped = leaveErrorMessagesVi[cleanCode] {
            return mapped
        }
        if let fallback = fallbackMessage?.trimmingCharacters(in: .whitespacesAndNewlines),
           !fallback.isEmpty {
            return fallback
        }
        return "Không thể hoàn tất thao tác nghỉ phép lúc này. Vui lòng thử lại."
    }

    public static func isValidUuid(_ value: String?) -> Bool {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else {
            return false
        }
        return UUID(uuidString: trimmed) != nil
    }

    public static func isValidIsoDate(_ value: String?) -> Bool {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines),
              trimmed.count == 10 else {
            return false
        }
        let parts = trimmed.split(separator: "-")
        guard parts.count == 3,
              parts[0].count == 4,
              parts[1].count == 2,
              parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isNumber) }) else {
            return false
        }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.dateFormat = "yyyy-MM-dd"
        guard let parsed = df.date(from: trimmed) else {
            return false
        }
        return df.string(from: parsed) == trimmed
    }

    public static func halfDayUnitsToDays(_ units: Int) -> Double {
        Double(units) / 2.0
    }

    public static func formatHalfDayUnits(_ units: Int, includeSuffix: Bool = true) -> String {
        let days = halfDayUnitsToDays(units)
        let nf = NumberFormatter()
        nf.locale = Locale(identifier: "vi_VN")
        nf.numberStyle = .decimal
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 1
        let formatted = nf.string(from: NSNumber(value: days)) ?? String(format: "%.1f", days)
        return includeSuffix ? "\(formatted) ngày" : formatted
    }

    public static func formatSignedHalfDayUnits(_ unitsDelta: Int) -> String {
        if unitsDelta == 0 { return "0 ngày" }
        let sign = unitsDelta > 0 ? "+" : "-"
        return "\(sign)\(formatHalfDayUnits(abs(unitsDelta), includeSuffix: true))"
    }

    public static func formatIsoDateVi(_ isoDate: String?) -> String {
        guard let trimmed = isoDate?.trimmingCharacters(in: .whitespacesAndNewlines),
              isValidIsoDate(trimmed) else {
            return "Chưa đặt"
        }
        let parts = trimmed.split(separator: "-")
        return "\(parts[2])/\(parts[1])/\(parts[0])"
    }

    public static func leaveSessionLabel(_ sessionCode: String) -> String {
        switch sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "FULL":
            return "Cả ngày (1 ngày)"
        case "AM":
            return "Buổi sáng (0,5 ngày)"
        case "PM":
            return "Buổi chiều (0,5 ngày)"
        default:
            return sessionCode
        }
    }

    public static func leaveSessionCompactLabel(_ sessionCode: String) -> String {
        switch sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "FULL":
            return "Cả ngày (1,0)"
        case "AM":
            return "Sáng (0,5)"
        case "PM":
            return "Chiều (0,5)"
        default:
            return sessionCode
        }
    }

    public static func selectableHistoryLeaveYears(currentYear: Int = currentVietnamLeaveYear()) -> [Int] {
        [currentYear, currentYear - 1]
    }

    public static func leaveSessionShortLabel(_ sessionCode: String) -> String {
        switch sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "FULL":
            return "Cả ngày"
        case "AM":
            return "Buổi sáng"
        case "PM":
            return "Buổi chiều"
        default:
            return sessionCode
        }
    }

    public static func leaveSessionHalfDayUnits(_ sessionCode: String) -> Int {
        sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "FULL" ? 2 : 1
    }

    public static func leaveEmploymentStatusLabel(_ status: String) -> String {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "OFFICIAL":
            return "Chính thức"
        case "PROBATION":
            return "Thử việc"
        case "UNCONFIGURED":
            return "Chưa cấu hình"
        default:
            return status
        }
    }

    public static func leaveUnitKindLabel(_ unitKind: String) -> String {
        switch unitKind.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "RECRUITMENT":
            return "Tuyển dụng"
        case "MARKETING":
            return "Marketing"
        case "IT":
            return "IT"
        case "ACCOUNTING":
            return "Kế toán"
        case "MANAGEMENT_BOD":
            return "Ban Giám đốc / Quản lý"
        case "UNCLASSIFIED":
            return "Chưa phân loại"
        default:
            return unitKind
        }
    }

    public static func leaveRequestStatusLabel(_ status: String) -> String {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "PENDING":
            return "Chờ duyệt"
        case "APPROVED":
            return "Đã duyệt"
        case "REJECTED":
            return "Từ chối"
        case "CANCELLED":
            return "Đã hủy sau duyệt"
        case "WITHDRAWN":
            return "Đã tự rút"
        case "ALL":
            return "Tất cả"
        default:
            return status
        }
    }

    public static func leaveTypeShortLabel(_ leaveType: String) -> String {
        switch leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ANNUAL":
            return "Phép năm"
        case "BEREAVEMENT":
            return "Nghỉ tang"
        case "MARRIAGE":
            return "Nghỉ cưới"
        case "SICK":
            return "Nghỉ ốm"
        case "UNPAID":
            return "Nghỉ không lương"
        default:
            return leaveType
        }
    }

    public static func leaveTypeLabel(_ leaveType: String) -> String {
        switch leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ANNUAL":
            return "Phép năm"
        case "BEREAVEMENT":
            return "Nghỉ tang (tối đa 3 ngày/đơn)"
        case "MARRIAGE":
            return "Nghỉ cưới (tối đa 3 ngày/đơn)"
        case "SICK":
            return "Nghỉ ốm (tối đa 7 ngày/đơn)"
        case "UNPAID":
            return "Nghỉ không lương (tối đa 7 ngày/đơn)"
        default:
            return leaveType
        }
    }

    public static func leavePayTreatmentLabel(_ payTreatment: String) -> String {
        switch payTreatment.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "PAID_LEAVE":
            return "Nghỉ phép có lương"
        case "UNPAID_PROBATION":
            return "Nghỉ không lương (Thử việc)"
        case "UNPAID_UNCONFIGURED_PROFILE":
            return "Nghỉ không lương (Chưa cấu hình hồ sơ)"
        default:
            return payTreatment
        }
    }

    public static func resolveRequestTypeBreakdown(
        _ request: LeaveRequestProjection
    ) -> [LeaveRequestTypeBreakdownProjection] {
        if !request.typeBreakdown.isEmpty {
            return request.typeBreakdown
        }
        return resolveRequestTypeBreakdownFromDays(request.days)
    }

    public static func resolveRequestTypeBreakdownFromDays(
        _ days: [LeaveRequestDayProjection]
    ) -> [LeaveRequestTypeBreakdownProjection] {
        var totals: [String: Int] = [:]
        for d in days {
            let normType = d.leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let key = leaveTypes.contains(normType) ? normType : "ANNUAL"
            let units = d.halfDayUnits > 0 ? d.halfDayUnits : leaveSessionHalfDayUnits(d.sessionCode)
            totals[key, default: 0] += units
        }
        return leaveTypes.compactMap { type in
            guard let u = totals[type], u > 0 else { return nil }
            return LeaveRequestTypeBreakdownProjection(leaveType: type, halfDayUnits: u)
        }
    }

    public static func autoAllocateDraftDaysByBalance(
        days: [LeaveDraftDayInput],
        availableUnits: Int
    ) -> [LeaveDraftDayInput] {
        var remainingAnnual = max(0, availableUnits)
        var result: [LeaveDraftDayInput] = []
        for row in days {
            let normType = row.leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let safeType = leaveTypes.contains(normType) ? normType : "ANNUAL"
            if safeType != "ANNUAL" {
                result.append(row)
                continue
            }
            let normSession = row.sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let units = leaveSessionHalfDayUnits(normSession)
            if remainingAnnual >= units {
                remainingAnnual -= units
                result.append(row)
            } else if remainingAnnual == 1 && normSession == "FULL" {
                remainingAnnual = 0
                var amRow = row
                amRow.sessionCode = "AM"
                amRow.leaveType = "ANNUAL"
                result.append(amRow)
                result.append(
                    LeaveDraftDayInput(
                        leaveDate: row.leaveDate,
                        sessionCode: "PM",
                        leaveType: "UNPAID"
                    )
                )
            } else {
                var unpaidRow = row
                unpaidRow.leaveType = "UNPAID"
                result.append(unpaidRow)
            }
        }
        return result
    }

    public static func evaluateLeaveRequestDraft(
        days: [LeaveDraftDayInput],
        reason: String,
        leaveYear: Int? = nil,
        availableUnits: Int? = nil
    ) -> LeaveRequestDraftEvaluation {
        if days.isEmpty {
            return LeaveRequestDraftEvaluation(
                isValid: false,
                errorMessage: "Vui lòng chọn ít nhất 1 dòng ngày/buổi nghỉ."
            )
        }
        if days.count > 120 {
            return LeaveRequestDraftEvaluation(
                isValid: false,
                errorMessage: "Mỗi đơn chỉ được tối đa 120 dòng ngày/buổi nghỉ."
            )
        }

        struct DayEntry {
            let sessionCode: String
            let leaveType: String
            let halfDayUnits: Int
        }
        var byDateOrder: [String] = []
        var byDateEntries: [String: [DayEntry]] = [:]
        var byTypeUnits: [String: Int] = [:]
        var totalUnits = 0

        for (index, row) in days.enumerated() {
            let dateStr = row.leaveDate.trimmingCharacters(in: .whitespacesAndNewlines)
            let sessionStr = row.sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let typeStr = row.leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let rowNo = index + 1

            if !isValidIsoDate(dateStr) {
                return LeaveRequestDraftEvaluation(
                    isValid: false,
                    errorMessage: "Dòng ngày nghỉ #\(rowNo) chưa đúng định dạng YYYY-MM-DD."
                )
            }
            if let expectedYear = leaveYear {
                let rowYear = Int(dateStr.prefix(4))
                if rowYear != nil && rowYear != expectedYear {
                    return LeaveRequestDraftEvaluation(
                        isValid: false,
                        errorMessage: "Ngày nghỉ \(dateStr) không thuộc năm đang chọn (\(expectedYear))."
                    )
                }
            }
            if !sessionCodes.contains(sessionStr) {
                return LeaveRequestDraftEvaluation(
                    isValid: false,
                    errorMessage: "Buổi nghỉ ở dòng #\(rowNo) không hợp lệ."
                )
            }
            if !leaveTypes.contains(typeStr) {
                return LeaveRequestDraftEvaluation(
                    isValid: false,
                    errorMessage: "Loại nghỉ ở dòng #\(rowNo) không hợp lệ."
                )
            }

            let units = leaveSessionHalfDayUnits(sessionStr)
            totalUnits += units
            byTypeUnits[typeStr, default: 0] += units

            if byDateEntries[dateStr] == nil {
                byDateOrder.append(dateStr)
                byDateEntries[dateStr] = []
            }
            byDateEntries[dateStr]?.append(
                DayEntry(sessionCode: sessionStr, leaveType: typeStr, halfDayUnits: units)
            )
        }

        let breakdown: [LeaveRequestTypeBreakdownProjection] = leaveTypes.compactMap { type in
            guard let u = byTypeUnits[type], u > 0 else { return nil }
            return LeaveRequestTypeBreakdownProjection(leaveType: type, halfDayUnits: u)
        }
        let annualUnits = byTypeUnits["ANNUAL"] ?? 0
        let nonAnnualUnits = totalUnits - annualUnits
        let excessAnnualUnits: Int = {
            guard let avail = availableUnits else { return 0 }
            return max(0, annualUnits - max(0, avail))
        }()
        let currentUnpaidUnits = byTypeUnits["UNPAID"] ?? 0
        let canSplitExcessAnnualToUnpaid =
            excessAnnualUnits > 0 && (currentUnpaidUnits + excessAnnualUnits <= 14)

        for dateStr in byDateOrder {
            guard let entries = byDateEntries[dateStr] else { continue }
            if entries.count > 2 {
                return LeaveRequestDraftEvaluation(
                    isValid: false,
                    errorMessage: "Ngày \(dateStr) đang có quá 2 dòng buổi nghỉ.",
                    totalUnits: totalUnits,
                    annualUnits: annualUnits,
                    nonAnnualUnits: nonAnnualUnits,
                    excessAnnualUnits: excessAnnualUnits,
                    canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                    typeBreakdown: breakdown
                )
            }
            if entries.count == 2 {
                let first = entries[0]
                let second = entries[1]
                if first.sessionCode == "FULL" || second.sessionCode == "FULL" {
                    return LeaveRequestDraftEvaluation(
                        isValid: false,
                        errorMessage: "Ngày \(dateStr) đã chọn 'Cả ngày' nên không thể thêm buổi thứ 2.",
                        totalUnits: totalUnits,
                        annualUnits: annualUnits,
                        nonAnnualUnits: nonAnnualUnits,
                        excessAnnualUnits: excessAnnualUnits,
                        canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                        typeBreakdown: breakdown
                    )
                }
                if first.sessionCode == second.sessionCode {
                    return LeaveRequestDraftEvaluation(
                        isValid: false,
                        errorMessage: "Ngày \(dateStr) bị trùng buổi (\(leaveSessionShortLabel(first.sessionCode))).",
                        totalUnits: totalUnits,
                        annualUnits: annualUnits,
                        nonAnnualUnits: nonAnnualUnits,
                        excessAnnualUnits: excessAnnualUnits,
                        canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                        typeBreakdown: breakdown
                    )
                }
                if first.leaveType == second.leaveType {
                    return LeaveRequestDraftEvaluation(
                        isValid: false,
                        errorMessage: "Ngày \(dateStr): 2 buổi cùng loại nghỉ phải chọn 'Cả ngày' thay vì tách 2 dòng.",
                        totalUnits: totalUnits,
                        annualUnits: annualUnits,
                        nonAnnualUnits: nonAnnualUnits,
                        excessAnnualUnits: excessAnnualUnits,
                        canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                        typeBreakdown: breakdown
                    )
                }
            }
        }

        for type in leaveTypes {
            guard let maxUnits = leaveTypeMaxUnitsPerRequest[type] else { continue }
            let typeUnits = byTypeUnits[type] ?? 0
            if typeUnits > maxUnits {
                return LeaveRequestDraftEvaluation(
                    isValid: false,
                    errorMessage: "Loại '\(leaveTypeShortLabel(type))' vượt giới hạn tối đa \(formatHalfDayUnits(maxUnits))/đơn (đang chọn \(formatHalfDayUnits(typeUnits))).",
                    totalUnits: totalUnits,
                    annualUnits: annualUnits,
                    nonAnnualUnits: nonAnnualUnits,
                    excessAnnualUnits: excessAnnualUnits,
                    canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                    typeBreakdown: breakdown
                )
            }
        }

        if let avail = availableUnits, excessAnnualUnits > 0 {
            let safeAvail = max(0, avail)
            return LeaveRequestDraftEvaluation(
                isValid: false,
                errorMessage: "Số ngày Phép năm đang chọn (\(formatHalfDayUnits(annualUnits))) vượt quá số dư phép năm khả dụng (\(formatHalfDayUnits(safeAvail))). Vui lòng chuyển \(formatHalfDayUnits(excessAnnualUnits)) vượt sang Nghỉ không lương hoặc loại nghỉ phù hợp.",
                totalUnits: totalUnits,
                annualUnits: annualUnits,
                nonAnnualUnits: nonAnnualUnits,
                excessAnnualUnits: excessAnnualUnits,
                canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                typeBreakdown: breakdown
            )
        }

        let trimmedReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedReason.count > 2000 {
            return LeaveRequestDraftEvaluation(
                isValid: false,
                errorMessage: "Lý do xin nghỉ không được vượt quá 2000 ký tự.",
                totalUnits: totalUnits,
                annualUnits: annualUnits,
                nonAnnualUnits: nonAnnualUnits,
                excessAnnualUnits: excessAnnualUnits,
                canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
                typeBreakdown: breakdown
            )
        }

        return LeaveRequestDraftEvaluation(
            isValid: true,
            errorMessage: nil,
            totalUnits: totalUnits,
            annualUnits: annualUnits,
            nonAnnualUnits: nonAnnualUnits,
            excessAnnualUnits: excessAnnualUnits,
            canSplitExcessAnnualToUnpaid: canSplitExcessAnnualToUnpaid,
            typeBreakdown: breakdown
        )
    }

    public static func leaveRouteKindLabel(
        _ routeKind: String,
        teamName: String? = nil,
        unitKind: String? = nil
    ) -> String {
        switch routeKind.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "TEAM_LEADER":
            if let teamName = teamName?.trimmingCharacters(in: .whitespacesAndNewlines), !teamName.isEmpty {
                return "Team Leader (\(teamName))"
            }
            return "Team Leader"
        case "DEPARTMENT_HEAD":
            if let unitKind = unitKind?.trimmingCharacters(in: .whitespacesAndNewlines), !unitKind.isEmpty {
                return "Trưởng bộ phận (\(leaveUnitKindLabel(unitKind)))"
            }
            return "Trưởng bộ phận"
        case "ADMIN_BOD_DIRECT":
            return "Admin / Ban Giám đốc (trực tiếp)"
        case "ADMIN_BOD_FALLBACK":
            return "Admin / Ban Giám đốc (hàng đợi dự phòng)"
        default:
            return routeKind
        }
    }

    public static func leaveFallbackReasonLabel(_ fallbackReason: String?) -> String? {
        guard let clean = fallbackReason?.trimmingCharacters(in: .whitespacesAndNewlines),
              !clean.isEmpty else {
            return nil
        }
        switch clean {
        case "DEPARTMENT_APPROVER_NOT_CONFIGURED":
            return "Bộ phận chưa cấu hình người duyệt hiện hành"
        case "TEAM_WITHOUT_ACTIVE_LEADER":
            return "Team chưa có Team Leader đang hiệu lực"
        case "TEAM_MULTIPLE_LEADERS_OR_SELF":
            return "Team có nhiều Team Leader hoặc trùng người tạo đơn"
        case "RECRUITER_WITHOUT_ACTIVE_TEAM":
            return "Nhân sự chưa thuộc Team đang hoạt động"
        case "RECRUITER_MULTI_ACTIVE_TEAMS":
            return "Nhân sự đang thuộc nhiều Team cùng lúc"
        case "PROFILE_UNCONFIGURED_OR_UNMAPPED":
            return "Hồ sơ nghỉ phép chưa cấu hình đầy đủ hoặc chưa ánh xạ nhân sự"
        case "UNMAPPED_UNIT_OR_RECRUITER":
            return "Chưa ánh xạ đơn vị hoặc hồ sơ nhân sự tuyển dụng"
        default:
            return clean
        }
    }

    public static func leaveLotKindLabel(_ lotKind: String, accrualMonth: Int? = nil) -> String {
        switch lotKind.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "CARRY_OVER":
            return "Phép tồn năm trước"
        case "MONTHLY_ACCRUAL":
            if let m = accrualMonth, m > 0 {
                return String(format: "Phép định kỳ tháng %02d", m)
            }
            return "Phép định kỳ tháng"
        case "PROBATION_CATCHUP":
            if let m = accrualMonth, m > 0 {
                return String(format: "Phép tính bù thử việc tháng %02d", m)
            }
            return "Phép tính bù thử việc"
        case "SENIORITY_GRANT":
            return "Phép thâm niên"
        case "OPENING_CURRENT_YEAR":
            return "Số dư đầu kỳ năm hiện tại"
        case "MANUAL_ADJUSTMENT":
            return "Điều chỉnh thủ công"
        default:
            return lotKind
        }
    }

    public static func leaveLedgerEntryTypeLabel(_ entryType: String) -> String {
        switch entryType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "GRANT_MONTHLY_ACCRUAL":
            return "Cộng phép định kỳ tháng"
        case "GRANT_PROBATION_CATCHUP":
            return "Cộng bù phép thời gian thử việc"
        case "GRANT_SENIORITY":
            return "Cộng phép thâm niên"
        case "GRANT_OPENING_CARRY_OVER":
            return "Khởi tạo phép tồn năm trước"
        case "GRANT_OPENING_CURRENT_YEAR":
            return "Khởi tạo số dư đầu kỳ năm hiện tại"
        case "GRANT_MANUAL_ADJUSTMENT":
            return "Điều chỉnh số dư phép"
        case "RESERVE":
            return "Giữ chỗ phép (đơn ngày tương lai đã duyệt)"
        case "SETTLE_RESERVE_TO_CONSUME":
            return "Chốt phép giữ chỗ sang đã sử dụng"
        case "CONSUME":
            return "Trừ phép trực tiếp (đơn đã duyệt)"
        case "RELEASE_RESERVE":
            return "Hoàn lại phép giữ chỗ (hủy đơn)"
        case "REVERSAL_CONSUME":
            return "Hoàn lại phép đã trừ (hủy đơn đã duyệt)"
        case "EXPIRE_CARRY_OVER":
            return "Hết hạn phép tồn năm trước"
        default:
            return entryType
        }
    }

    public static func leaveDecisionActionLabel(_ decision: String) -> String {
        switch decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "APPROVE":
            return "Duyệt đơn nghỉ phép"
        case "REJECT":
            return "Từ chối đơn nghỉ phép"
        case "CANCEL_APPROVED":
            return "Hủy đơn đã duyệt (hoàn phép)"
        default:
            return decision
        }
    }

    public static func currentVietnamLeaveYear(now: Date = Date()) -> Int {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy"
        return Int(df.string(from: now)) ?? 2026
    }

    public static func todayVietnamIsoDate(now: Date = Date()) -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: now)
    }

    public static func nextIsoDateAfter(_ isoDate: String, fallbackDate: String? = nil) -> String {
        let trimmed = isoDate.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidIsoDate(trimmed) else {
            if let fallback = fallbackDate, isValidIsoDate(fallback) {
                return fallback
            }
            return todayVietnamIsoDate()
        }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.dateFormat = "yyyy-MM-dd"
        guard let parsed = df.date(from: trimmed) else {
            return trimmed
        }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let next = cal.date(byAdding: .day, value: 1, to: parsed) ?? parsed
        return df.string(from: next)
    }

    public static func generateDraftDaysFromRange(
        startDateIso: String,
        endDateIso: String,
        leaveType: String = "ANNUAL",
        sessionCode: String = "FULL",
        skipSundays: Bool = false
    ) -> (days: [LeaveDraftDayInput], errorMessage: String?) {
        let startTrimmed = startDateIso.trimmingCharacters(in: .whitespacesAndNewlines)
        let endTrimmed = endDateIso.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidIsoDate(startTrimmed) && isValidIsoDate(endTrimmed) else {
            return ([], "Vui lòng chọn Từ ngày và Đến ngày hợp lệ (YYYY-MM-DD).")
        }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.dateFormat = "yyyy-MM-dd"
        guard let startDate = df.date(from: startTrimmed),
              let endDate = df.date(from: endTrimmed) else {
            return ([], "Vui lòng chọn Từ ngày và Đến ngày hợp lệ.")
        }
        if endDate < startDate {
            return ([], "Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu.")
        }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let diffDays = (cal.dateComponents([.day], from: startDate, to: endDate).day ?? 0) + 1
        if diffDays > 60 {
            return ([], "Dải ngày nghỉ không được vượt quá 60 ngày trong một lần chọn.")
        }
        let normType = leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let safeType = leaveTypes.contains(normType) ? normType : "ANNUAL"
        let normSession = sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let safeSession = sessionCodes.contains(normSession) ? normSession : "FULL"

        var rows: [LeaveDraftDayInput] = []
        var cursor = startDate
        while cursor <= endDate {
            let weekday = cal.component(.weekday, from: cursor)
            if !(skipSundays && weekday == 1) {
                rows.append(
                    LeaveDraftDayInput(
                        leaveDate: df.string(from: cursor),
                        sessionCode: safeSession,
                        leaveType: safeType
                    )
                )
            }
            guard let next = cal.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
        }
        if rows.isEmpty {
            return ([], "Dải ngày đã chọn chỉ có Chủ nhật. Vui lòng chọn khoảng ngày khác hoặc tắt tùy chọn bỏ qua Chủ nhật.")
        }
        return (rows, nil)
    }

    public static func newClientIdempotencyKey(_ prefix: String) -> String {
        let cleanPrefix = prefix.trimmingCharacters(in: .whitespacesAndNewlines)
        return "\(cleanPrefix)-\(UUID().uuidString.lowercased())"
    }

    /// Resolves visible tabs inside the Attendance area strictly from server-returned authority.
    /// - `.checkIn` ("Chấm công") is always visible and default.
    /// - `.leaveRequest` ("Xin nghỉ") is visible unless backend explicitly marks the account as shared/ineligible.
    /// - `.leaveApproval` ("Duyệt nghỉ") is visible ONLY when backend confirms `can_approve_requests = true`
    ///   and `!is_shared_non_employee`.
    public static func resolveVisibleAttendanceTabs(
        authority: LeaveAuthorityProjection?,
        errorCode: String? = nil
    ) -> [AttendanceAreaTab] {
        var tabs: [AttendanceAreaTab] = [.checkIn]
        let normalizedError = errorCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let isSharedOrForbidden = normalizedError == "LEAVE_SHARED_ACCOUNT_FORBIDDEN"
            || authority?.isSharedNonEmployee == true
            || authority?.canSubmitRequest == false
        if !isSharedOrForbidden {
            tabs.append(.leaveRequest)
        }
        if let authority, !authority.isSharedNonEmployee && authority.canApproveRequests {
            tabs.append(.leaveApproval)
        }
        return tabs
    }

    public static func canWithdrawOwnLeaveRequest(
        actorAppUserId: String?,
        request: LeaveRequestProjection
    ) -> Bool {
        guard let actorId = actorAppUserId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !actorId.isEmpty else {
            return false
        }
        return request.appUserId.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(actorId) == .orderedSame
            && request.status.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare("PENDING") == .orderedSame
    }

    public static func validateApproverDecisionInput(
        actorAppUserId: String?,
        request: LeaveRequestProjection,
        decision: String,
        reason: String
    ) -> (isValid: Bool, errorMessage: String?) {
        let cleanActorId = actorAppUserId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if cleanActorId.isEmpty {
            return (false, "Chưa xác định được người duyệt hiện tại.")
        }
        if cleanActorId.caseInsensitiveCompare(request.appUserId.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame {
            return (false, "Người duyệt không được tự duyệt hoặc tự hủy đơn nghỉ phép của chính mình.")
        }
        let normalizedDecision = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let normalizedStatus = request.status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let trimmedReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedReason.count > 2000 {
            return (false, "Lý do xử lý không được vượt quá 2000 ký tự.")
        }

        switch normalizedDecision {
        case "APPROVE":
            if normalizedStatus != "PENDING" {
                return (false, "Chỉ có thể duyệt đơn đang ở trạng thái Chờ duyệt.")
            }
            return (true, nil)
        case "REJECT":
            if normalizedStatus != "PENDING" {
                return (false, "Chỉ có thể từ chối đơn đang ở trạng thái Chờ duyệt.")
            }
            return (true, nil)
        case "CANCEL_APPROVED":
            if normalizedStatus != "APPROVED" {
                return (false, "Chỉ có thể hủy đơn đã ở trạng thái Đã duyệt để hoàn lại phép.")
            }
            return (true, nil)
        default:
            return (false, "Quyết định xử lý đơn không hợp lệ.")
        }
    }

    public static func validateCreateDraftInput(
        days: [LeaveDraftDayInput],
        reason: String,
        leaveYear: Int? = nil,
        availableUnits: Int? = nil
    ) -> String? {
        evaluateLeaveRequestDraft(
            days: days,
            reason: reason,
            leaveYear: leaveYear,
            availableUnits: availableUnits
        ).errorMessage
    }

    // MARK: - JSON Parsing Helpers

    private static func optNullableInt(_ dict: [String: Any], key: String) -> Int? {
        guard let raw = dict[key], !(raw is NSNull) else { return nil }
        if let i = raw as? Int { return i }
        if let n = raw as? NSNumber { return n.intValue }
        if let s = raw as? String {
            return Int(s.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return nil
    }

    public static func parseAccountProjection(_ obj: [String: Any]?) -> LeaveAccountProjection? {
        guard let obj else { return nil }
        let appUserId = obj.optString("app_user_id")
        guard !appUserId.isEmpty else { return nil }
        return LeaveAccountProjection(
            appUserId: appUserId,
            displayName: obj.optString("display_name"),
            loginId: obj.optNullableString("login_id"),
            personnelCode: obj.optNullableString("personnel_code"),
            recruiterId: obj.optNullableString("recruiter_id"),
            accountEnabled: obj.optBool("account_enabled", defaultValue: true)
        )
    }

    public static func parseAuthorityProjection(_ obj: [String: Any]?) -> LeaveAuthorityProjection? {
        guard let obj else { return nil }
        let appUserId = obj.optString("app_user_id")
        let isShared = obj.optBool("is_shared_non_employee", defaultValue: false)
        return LeaveAuthorityProjection(
            appUserId: appUserId,
            isSharedNonEmployee: isShared,
            isFullAdmin: obj.optBool("is_full_admin", defaultValue: false),
            isBod: obj.optBool("is_bod", defaultValue: false),
            isHrOperator: obj.optBool("is_hr_operator", defaultValue: false),
            isHrReportViewer: obj.optBool("is_hr_report_viewer", defaultValue: false),
            isTeamLeader: obj.optBool("is_team_leader", defaultValue: false),
            isDepartmentApprover: obj.optBool("is_department_approver", defaultValue: false),
            canSubmitRequest: obj.optBool("can_submit_request", defaultValue: !isShared),
            canApproveRequests: obj.optBool("can_approve_requests", defaultValue: false),
            canViewMonthlyReport: obj.optBool("can_view_monthly_report", defaultValue: false),
            canManageConfig: obj.optBool("can_manage_config", defaultValue: false)
        )
    }

    public static func parseRoutingProjection(_ obj: [String: Any]?) -> LeaveRoutingProjection {
        let safe = obj ?? [:]
        return LeaveRoutingProjection(
            routeKind: safe.optString("route_kind", defaultValue: "ADMIN_BOD_FALLBACK"),
            unitKind: safe.optString("unit_kind", defaultValue: "UNCLASSIFIED"),
            primaryApproverAppUserId: safe.optNullableString("primary_approver_app_user_id"),
            teamId: safe.optNullableString("team_id"),
            teamCode: safe.optNullableString("team_code"),
            teamName: safe.optNullableString("team_name"),
            fallbackReason: safe.optNullableString("fallback_reason"),
            profileConfigured: safe.optBool("profile_configured", defaultValue: false)
        )
    }

    public static func parsePolicyProjection(_ obj: [String: Any]?) -> LeavePolicyProjection {
        let safe = obj ?? [:]
        return LeavePolicyProjection(
            annualBaseUnits: safe.optInt("annual_base_units", defaultValue: 24),
            monthlyAccrualUnits: safe.optInt("monthly_accrual_units", defaultValue: 2),
            defaultSeniorityYears: safe.optInt("default_seniority_years", defaultValue: 5),
            defaultSeniorityStepUnits: safe.optInt("default_seniority_step_units", defaultValue: 2),
            carryOverExpiryMonth: optNullableInt(safe, key: "carry_over_expiry_month"),
            carryOverExpiryDay: optNullableInt(safe, key: "carry_over_expiry_day"),
            carryOverExpiresAfterDate: safe.optNullableString("carry_over_expires_after_date"),
            rowVersion: safe.optInt("row_version", defaultValue: 1)
        )
    }

    public static func parseBalanceProjection(_ obj: [String: Any]?, defaultYear: Int) -> LeaveBalanceProjection {
        let safe = obj ?? [:]
        let lotsArr = safe.optObjectArray("lots")
        let lots: [LeaveLotProjection] = lotsArr.map { item in
            LeaveLotProjection(
                lotId: item.optString("lot_id"),
                leaveYear: item.optInt("leave_year", defaultValue: defaultYear),
                lotKind: item.optString("lot_kind"),
                accrualMonth: optNullableInt(item, key: "accrual_month"),
                grantedUnits: item.optInt("granted_units", defaultValue: 0),
                reservedUnits: item.optInt("reserved_units", defaultValue: 0),
                consumedUnits: item.optInt("consumed_units", defaultValue: 0),
                expiredUnits: item.optInt("expired_units", defaultValue: 0),
                availableUnits: item.optInt("available_units", defaultValue: 0),
                usableFromDate: item.optString("usable_from_date"),
                expiresAfterDate: item.optNullableString("expires_after_date"),
                sourceRef: item.optNullableString("source_ref")
            )
        }
        let pendingPaid = safe.optInt("pending_paid_units", defaultValue: 0)
        let pendingUnpaid = safe.optInt("pending_unpaid_units", defaultValue: 0)
        let approvedUnpaid = safe.optInt("approved_unpaid_units", defaultValue: 0)
        let pendingAnnual = safe.optInt("pending_annual_units", defaultValue: pendingPaid)
        let pendingNonAnnual = safe.optInt("pending_non_annual_units", defaultValue: pendingUnpaid)
        let approvedNonAnnual = safe.optInt("approved_non_annual_units", defaultValue: approvedUnpaid)
        return LeaveBalanceProjection(
            leaveYear: safe.optInt("leave_year", defaultValue: defaultYear),
            asOfDate: safe.optString("as_of_date"),
            hasProfile: safe.optBool("has_profile", defaultValue: false),
            profileConfigured: safe.optBool("profile_configured", defaultValue: false),
            employmentStatus: safe.optString("employment_status", defaultValue: "UNCONFIGURED"),
            unitKind: safe.optString("unit_kind", defaultValue: "UNCLASSIFIED"),
            workStartDate: safe.optNullableString("work_start_date"),
            officialEffectiveDate: safe.optNullableString("official_effective_date"),
            nextSeniorityEffectiveDate: safe.optNullableString("next_seniority_effective_date"),
            nextSeniorityUnits: safe.optInt("next_seniority_units", defaultValue: 0),
            totalGrantedUnits: safe.optInt("total_granted_units", defaultValue: 0),
            carryOverGrantedUnits: safe.optInt("carry_over_granted_units", defaultValue: 0),
            carryOverAvailableUnits: safe.optInt("carry_over_available_units", defaultValue: 0),
            monthlyAccruedUnits: safe.optInt("monthly_accrued_units", defaultValue: 0),
            seniorityGrantedUnits: safe.optInt("seniority_granted_units", defaultValue: 0),
            openingOrAdjustmentUnits: safe.optInt("opening_or_adjustment_units", defaultValue: 0),
            availableUnits: safe.optInt("available_units", defaultValue: 0),
            reservedUnits: safe.optInt("reserved_units", defaultValue: 0),
            consumedUnits: safe.optInt("consumed_units", defaultValue: 0),
            expiredUnits: safe.optInt("expired_units", defaultValue: 0),
            pendingAnnualUnits: pendingAnnual,
            pendingNonAnnualUnits: pendingNonAnnual,
            approvedNonAnnualUnits: approvedNonAnnual,
            pendingPaidUnits: pendingPaid,
            pendingUnpaidUnits: pendingUnpaid,
            approvedUnpaidUnits: approvedUnpaid,
            lots: lots
        )
    }

    public static func parseRequestProjection(_ obj: [String: Any]) -> LeaveRequestProjection {
        let daysArr = obj.optObjectArray("days")
        let days: [LeaveRequestDayProjection] = daysArr.map { d in
            let sessionCode = d.optString("session_code", defaultValue: "FULL").uppercased()
            let rawType = d.optString("leave_type", defaultValue: "ANNUAL").uppercased()
            let leaveType = leaveTypes.contains(rawType) ? rawType : "ANNUAL"
            return LeaveRequestDayProjection(
                requestDayId: d.optString("request_day_id"),
                leaveDate: d.optString("leave_date"),
                sessionCode: sessionCode,
                leaveType: leaveType,
                halfDayUnits: d.optInt("half_day_units", defaultValue: leaveSessionHalfDayUnits(sessionCode)),
                isActive: d.optBool("is_active", defaultValue: true)
            )
        }
        let breakdownArr = obj.optObjectArray("type_breakdown")
        let parsedBreakdown: [LeaveRequestTypeBreakdownProjection] = breakdownArr.compactMap { tb in
            let t = tb.optString("leave_type").uppercased()
            let u = tb.optInt("half_day_units", defaultValue: 0)
            guard leaveTypes.contains(t), u > 0 else { return nil }
            return LeaveRequestTypeBreakdownProjection(leaveType: t, halfDayUnits: u)
        }
        let effectiveBreakdown = parsedBreakdown.isEmpty
            ? resolveRequestTypeBreakdownFromDays(days)
            : parsedBreakdown
        let leaveTypesArr = (obj["leave_types"] as? [Any])?.compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() } ?? []
        let parsedLeaveTypes = leaveTypesArr.filter { leaveTypes.contains($0) }
        let effectiveLeaveTypes = parsedLeaveTypes.isEmpty
            ? effectiveBreakdown.map(\.leaveType)
            : parsedLeaveTypes
        let allocArr = obj.optObjectArray("allocations")
        let allocations: [LeaveRequestAllocationProjection] = allocArr.map { a in
            LeaveRequestAllocationProjection(
                allocationId: a.optString("allocation_id"),
                requestDayId: a.optString("request_day_id"),
                lotId: a.optString("lot_id"),
                lotKind: a.optString("lot_kind"),
                leaveDate: a.optString("leave_date"),
                allocatedUnits: a.optInt("allocated_units", defaultValue: 0),
                status: a.optString("status"),
                settledAt: a.optNullableString("settled_at"),
                releasedAt: a.optNullableString("released_at")
            )
        }
        let defaultRequestedUnits = days.reduce(0) { $0 + $1.halfDayUnits }
        let defaultAnnualUnits = effectiveBreakdown.filter { $0.leaveType == "ANNUAL" }.reduce(0) { $0 + $1.halfDayUnits }
        let defaultNonAnnualUnits = max(0, defaultRequestedUnits - defaultAnnualUnits)
        return LeaveRequestProjection(
            requestId: obj.optString("request_id"),
            appUserId: obj.optString("app_user_id"),
            requester: parseAccountProjection(obj["requester"] as? [String: Any]),
            status: obj.optString("status", defaultValue: "PENDING"),
            payTreatment: obj.optString("pay_treatment", defaultValue: "PAID_LEAVE"),
            leaveTypes: effectiveLeaveTypes,
            typeBreakdown: effectiveBreakdown,
            requestedAnnualUnits: obj.optInt("requested_annual_units", defaultValue: defaultAnnualUnits),
            requestedNonAnnualUnits: obj.optInt("requested_non_annual_units", defaultValue: defaultNonAnnualUnits),
            employmentStatusSnapshot: obj.optString("employment_status_snapshot", defaultValue: "UNCONFIGURED"),
            unitKindSnapshot: obj.optString("unit_kind_snapshot", defaultValue: "UNCLASSIFIED"),
            profileConfiguredAtSubmit: obj.optBool("profile_configured_at_submit", defaultValue: false),
            requestedUnits: obj.optInt("requested_units", defaultValue: defaultRequestedUnits),
            reason: obj.optString("reason"),
            routeKind: obj.optString("route_kind"),
            primaryApproverAppUserId: obj.optNullableString("primary_approver_app_user_id"),
            primaryApprover: parseAccountProjection(obj["primary_approver"] as? [String: Any]),
            routedTeamId: obj.optNullableString("routed_team_id"),
            routedTeamCode: obj.optNullableString("routed_team_code"),
            routedTeamName: obj.optNullableString("routed_team_name"),
            fallbackReason: obj.optNullableString("fallback_reason"),
            submittedAt: obj.optString("submitted_at"),
            decidedAt: obj.optNullableString("decided_at"),
            decidedByAppUserId: obj.optNullableString("decided_by_app_user_id"),
            decidedBy: parseAccountProjection(obj["decided_by"] as? [String: Any]),
            decisionReason: obj.optNullableString("decision_reason"),
            cancelledAt: obj.optNullableString("cancelled_at"),
            cancelledByAppUserId: obj.optNullableString("cancelled_by_app_user_id"),
            cancellationReason: obj.optNullableString("cancellation_reason"),
            rowVersion: obj.optInt("row_version", defaultValue: 1),
            days: days,
            allocations: allocations
        )
    }

    public static func parseMyLeaveSummary(_ envelopeJson: [String: Any]) -> MiniBiApiResult<LeaveMeSummaryProjection> {
        guard let dataObj = envelopeJson["data"] as? [String: Any] else {
            return .failure(
                message: "Phản hồi máy chủ thiếu dữ liệu hồ sơ nghỉ phép.",
                httpStatus: 200,
                errorCode: "LEAVE_DATA_MISSING"
            )
        }
        guard let authority = parseAuthorityProjection(dataObj["authority"] as? [String: Any]) else {
            return .failure(
                message: "Phản hồi máy chủ thiếu thông tin phân quyền nghỉ phép.",
                httpStatus: 200,
                errorCode: "LEAVE_AUTHORITY_MISSING"
            )
        }
        let leaveYear = dataObj.optInt("leave_year", defaultValue: currentVietnamLeaveYear())
        let requests = dataObj.optObjectArray("requests").map { parseRequestProjection($0) }
        let ledgerEntries = dataObj.optObjectArray("ledger_entries").map { l in
            LeaveLedgerEntryProjection(
                ledgerEntryId: l.optString("ledger_entry_id"),
                lotId: l.optNullableString("lot_id"),
                requestId: l.optNullableString("request_id"),
                entryType: l.optString("entry_type"),
                effectiveDate: l.optString("effective_date"),
                deltaAvailableUnits: l.optInt("delta_available_units", defaultValue: 0),
                deltaReservedUnits: l.optInt("delta_reserved_units", defaultValue: 0),
                deltaConsumedUnits: l.optInt("delta_consumed_units", defaultValue: 0),
                deltaExpiredUnits: l.optInt("delta_expired_units", defaultValue: 0),
                reason: l.optNullableString("reason"),
                createdAt: l.optString("created_at")
            )
        }
        let summary = LeaveMeSummaryProjection(
            asOfDate: dataObj.optString("as_of_date"),
            leaveYear: leaveYear,
            account: parseAccountProjection(dataObj["account"] as? [String: Any]),
            authority: authority,
            routing: parseRoutingProjection(dataObj["routing"] as? [String: Any]),
            policy: parsePolicyProjection(dataObj["policy"] as? [String: Any]),
            balance: parseBalanceProjection(dataObj["balance"] as? [String: Any], defaultYear: leaveYear),
            requests: requests,
            ledgerEntries: ledgerEntries
        )
        return .success(summary)
    }

    public static func parseApprovalInbox(_ envelopeJson: [String: Any]) -> MiniBiApiResult<LeaveApprovalInboxProjection> {
        guard let dataObj = envelopeJson["data"] as? [String: Any] else {
            return .failure(
                message: "Phản hồi máy chủ thiếu dữ liệu hộp thư duyệt nghỉ phép.",
                httpStatus: 200,
                errorCode: "LEAVE_INBOX_DATA_MISSING"
            )
        }
        let items = dataObj.optObjectArray("items").map { parseRequestProjection($0) }
        let inbox = LeaveApprovalInboxProjection(
            asOfDate: dataObj.optString("as_of_date"),
            statusFilter: dataObj.optString("status_filter", defaultValue: "PENDING"),
            total: dataObj.optInt("total", defaultValue: items.count),
            limit: dataObj.optInt("limit", defaultValue: 50),
            offset: dataObj.optInt("offset", defaultValue: 0),
            items: items
        )
        return .success(inbox)
    }

    // MARK: - Payload Builders

    public static func buildCreateLeaveRequestPayload(
        days: [LeaveDraftDayInput],
        reason: String?,
        idempotencyKey: String = newClientIdempotencyKey("leave-create")
    ) -> [String: Any] {
        let daysPayload: [[String: String]] = days.map { d in
            let rawType = d.leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let cleanType = leaveTypes.contains(rawType) ? rawType : "ANNUAL"
            return [
                "leave_date": d.leaveDate.trimmingCharacters(in: .whitespacesAndNewlines),
                "session_code": d.sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
                "leave_type": cleanType
            ]
        }
        let cleanReason = reason?.trimmingCharacters(in: .whitespacesAndNewlines)
        let reasonValue: Any = (cleanReason?.isEmpty == false) ? cleanReason! : NSNull()
        return [
            "days": daysPayload,
            "reason": reasonValue,
            "idempotency_key": idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        ]
    }

    public static func buildWithdrawLeaveRequestPayload(
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = newClientIdempotencyKey("leave-withdraw")
    ) -> [String: Any] {
        let cleanReason = reason?.trimmingCharacters(in: .whitespacesAndNewlines)
        let reasonValue: Any = (cleanReason?.isEmpty == false) ? cleanReason! : NSNull()
        return [
            "expected_row_version": expectedRowVersion,
            "reason": reasonValue,
            "idempotency_key": idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        ]
    }

    public static func buildDecideLeaveRequestPayload(
        decision: String,
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = newClientIdempotencyKey("leave-decision")
    ) -> [String: Any] {
        let cleanReason = reason?.trimmingCharacters(in: .whitespacesAndNewlines)
        let reasonValue: Any = (cleanReason?.isEmpty == false) ? cleanReason! : NSNull()
        return [
            "decision": decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
            "expected_row_version": expectedRowVersion,
            "reason": reasonValue,
            "idempotency_key": idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        ]
    }
}

// MARK: - Leave Service (`GET /api/leave/me`, `POST /api/leave/requests`, `GET /api/leave/approvals`, etc.)

public final class LeaveService: @unchecked Sendable {
    public static let shared = LeaveService()

    private let httpClient: BackendHttpClient

    public init(httpClient: BackendHttpClient = .shared) {
        self.httpClient = httpClient
    }

    public func fetchMyLeaveSummary(leaveYear: Int? = nil) async -> MiniBiApiResult<LeaveMeSummaryProjection> {
        var queryItems: [URLQueryItem] = []
        if let leaveYear {
            queryItems.append(URLQueryItem(name: "year", value: String(leaveYear)))
        }
        let response = await httpClient.request(
            method: "GET",
            path: "/api/leave/me",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: LeaveContract.errorMessageForCode(response.errorCode, fallbackMessage: response.errorMessage),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        if let ok = root["ok"] as? Bool, !ok {
            let errObj = root["error"] as? [String: Any]
            let code = errObj?.optNullableString("code") ?? root.optNullableString("code")
            let msg = errObj?.optNullableString("message") ?? root.optNullableString("message")
            return .failure(
                message: LeaveContract.errorMessageForCode(code, fallbackMessage: msg),
                httpStatus: response.statusCode,
                errorCode: code,
                rawBody: response.rawBody
            )
        }
        return LeaveContract.parseMyLeaveSummary(root)
    }

    public func createLeaveRequest(
        days: [LeaveDraftDayInput],
        reason: String,
        idempotencyKey: String = LeaveContract.newClientIdempotencyKey("leave-create")
    ) async -> MiniBiApiResult<String> {
        let payload = LeaveContract.buildCreateLeaveRequestPayload(
            days: days,
            reason: reason,
            idempotencyKey: idempotencyKey
        )
        let response = await httpClient.request(
            method: "POST",
            path: "/api/leave/requests",
            jsonBody: payload
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: LeaveContract.errorMessageForCode(response.errorCode, fallbackMessage: response.errorMessage),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let requestId = dataObj.optString("request_id")
        return .success(requestId, statusCode: response.statusCode)
    }

    public func withdrawLeaveRequest(
        requestId: String,
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = LeaveContract.newClientIdempotencyKey("leave-withdraw")
    ) async -> MiniBiApiResult<String> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard LeaveContract.isValidUuid(cleanId) else {
            return .failure(
                message: "Mã đơn xin nghỉ phép không hợp lệ.",
                httpStatus: 400,
                errorCode: "INVALID_REQUEST"
            )
        }
        let payload = LeaveContract.buildWithdrawLeaveRequestPayload(
            expectedRowVersion: expectedRowVersion,
            reason: reason,
            idempotencyKey: idempotencyKey
        )
        let response = await httpClient.request(
            method: "POST",
            path: "/api/leave/requests/\(cleanId)/withdraw",
            jsonBody: payload
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: LeaveContract.errorMessageForCode(response.errorCode, fallbackMessage: response.errorMessage),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let status = dataObj.optString("status", defaultValue: "WITHDRAWN")
        return .success(status.isEmpty ? "WITHDRAWN" : status, statusCode: response.statusCode)
    }

    public func fetchApprovalInbox(
        statusFilter: String = "PENDING",
        limit: Int = 50,
        offset: Int = 0
    ) async -> MiniBiApiResult<LeaveApprovalInboxProjection> {
        let upperStatus = statusFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanStatus = LeaveContract.requestStatusFilters.contains(upperStatus) ? upperStatus : "PENDING"
        let safeLimit = min(max(limit, 1), 200)
        let safeOffset = max(offset, 0)

        let queryItems: [URLQueryItem] = [
            URLQueryItem(name: "status", value: cleanStatus),
            URLQueryItem(name: "limit", value: String(safeLimit)),
            URLQueryItem(name: "offset", value: String(safeOffset))
        ]
        let response = await httpClient.request(
            method: "GET",
            path: "/api/leave/approvals",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: LeaveContract.errorMessageForCode(response.errorCode, fallbackMessage: response.errorMessage),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        if let ok = root["ok"] as? Bool, !ok {
            let errObj = root["error"] as? [String: Any]
            let code = errObj?.optNullableString("code") ?? root.optNullableString("code")
            let msg = errObj?.optNullableString("message") ?? root.optNullableString("message")
            return .failure(
                message: LeaveContract.errorMessageForCode(code, fallbackMessage: msg),
                httpStatus: response.statusCode,
                errorCode: code,
                rawBody: response.rawBody
            )
        }
        return LeaveContract.parseApprovalInbox(root)
    }

    public func decideLeaveRequest(
        requestId: String,
        decision: String,
        expectedRowVersion: Int,
        reason: String?,
        idempotencyKey: String = LeaveContract.newClientIdempotencyKey("leave-decision")
    ) async -> MiniBiApiResult<String> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard LeaveContract.isValidUuid(cleanId) else {
            return .failure(
                message: "Mã đơn xin nghỉ phép không hợp lệ.",
                httpStatus: 400,
                errorCode: "INVALID_REQUEST"
            )
        }
        let cleanDecision = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard LeaveContract.decisionCodes.contains(cleanDecision) else {
            return .failure(
                message: LeaveContract.errorMessageForCode("LEAVE_DECISION_INVALID"),
                httpStatus: 400,
                errorCode: "LEAVE_DECISION_INVALID"
            )
        }
        let payload = LeaveContract.buildDecideLeaveRequestPayload(
            decision: cleanDecision,
            expectedRowVersion: expectedRowVersion,
            reason: reason,
            idempotencyKey: idempotencyKey
        )
        let response = await httpClient.request(
            method: "POST",
            path: "/api/leave/approvals/\(cleanId)/decision",
            jsonBody: payload
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: LeaveContract.errorMessageForCode(response.errorCode, fallbackMessage: response.errorMessage),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let status = dataObj.optString("status")
        return .success(status, statusCode: response.statusCode)
    }
}
