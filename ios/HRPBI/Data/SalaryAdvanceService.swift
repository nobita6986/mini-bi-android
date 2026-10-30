import CryptoKit
import Foundation

// MARK: - Salary Advance Models (`Ứng lương` QLDA)

public struct SalaryAdvanceManagedProjectOption: Identifiable, Equatable, Sendable {
    public var id: String { projectId }
    public let projectId: String
    public let displayName: String
    public let active: Bool
    public let hasActiveAccountant: Bool
    public let accountantUserId: String?
    public let accountantDisplayName: String?

    public var accountantDisplayNames: [String] {
        if let name = accountantDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            return [name]
        }
        return []
    }

    public init(
        projectId: String,
        displayName: String,
        active: Bool = true,
        hasActiveAccountant: Bool = true,
        accountantUserId: String? = nil,
        accountantDisplayName: String? = nil
    ) {
        self.projectId = projectId
        self.displayName = displayName
        self.active = active
        self.hasActiveAccountant = hasActiveAccountant
        self.accountantUserId = accountantUserId
        self.accountantDisplayName = accountantDisplayName
    }
}

public struct SalaryAdvanceActorContext: Equatable, Sendable {
    public let appUserId: String
    public let displayName: String
    public let authorizationDate: String
    public let isFullAdmin: Bool
    public let isBod: Bool
    public let isProjectManager: Bool
    public let isProjectAccountant: Bool
    public let isSupervisoryViewer: Bool
    public let managedProjects: [SalaryAdvanceManagedProjectOption]

    public var activeManagedProjects: [SalaryAdvanceManagedProjectOption] {
        managedProjects.filter { $0.active && !$0.projectId.isEmpty }
    }

    public init(
        appUserId: String,
        displayName: String,
        authorizationDate: String = "",
        isFullAdmin: Bool = false,
        isBod: Bool = false,
        isProjectManager: Bool = false,
        isProjectAccountant: Bool = false,
        isSupervisoryViewer: Bool = false,
        managedProjects: [SalaryAdvanceManagedProjectOption] = []
    ) {
        self.appUserId = appUserId
        self.displayName = displayName
        self.authorizationDate = authorizationDate
        self.isFullAdmin = isFullAdmin
        self.isBod = isBod
        self.isProjectManager = isProjectManager
        self.isProjectAccountant = isProjectAccountant
        self.isSupervisoryViewer = isSupervisoryViewer
        self.managedProjects = managedProjects
    }
}

public struct SalaryAdvanceWorkerBankAccountSummary: Equatable, Sendable {
    public let hasBankAccount: Bool
    public let bankId: String?
    public let institutionName: String?
    public let accountNumber: String?
    public let accountHolderName: String?
    public let isSelfOwned: Bool

    public init(
        hasBankAccount: Bool,
        bankId: String? = nil,
        institutionName: String? = nil,
        accountNumber: String? = nil,
        accountHolderName: String? = nil,
        isSelfOwned: Bool = false
    ) {
        self.hasBankAccount = hasBankAccount
        self.bankId = bankId
        self.institutionName = institutionName
        self.accountNumber = accountNumber
        self.accountHolderName = accountHolderName
        self.isSelfOwned = isSelfOwned
    }
}

public struct SalaryAdvanceWorkerRollingAdvanceSummary: Equatable, Sendable {
    public let windowDays: Int
    public let windowFromDate: String
    public let windowToDate: String
    public let totalCount: Int
    public let totalAmountVnd: String

    public init(
        windowDays: Int = 30,
        windowFromDate: String = "",
        windowToDate: String = "",
        totalCount: Int = 0,
        totalAmountVnd: String = "0"
    ) {
        self.windowDays = windowDays
        self.windowFromDate = windowFromDate
        self.windowToDate = windowToDate
        self.totalCount = totalCount
        self.totalAmountVnd = totalAmountVnd
    }
}

public struct SalaryAdvanceWorkerOption: Identifiable, Equatable, Sendable {
    public var id: String { workerEntryId }
    public let workerEntryId: String
    public let employeeCode: String
    public let displayName: String
    public let employmentStatus: String
    public let firstWorkDate: String
    public let providerType: String
    public let recruiterDisplayName: String?
    public let hasPortrait: Bool
    public let isEligible: Bool
    public let ineligibleReasons: [String]
    public let bankTransferEligible: Bool
    public let bankIneligibleReason: String?
    public let bankAccount: SalaryAdvanceWorkerBankAccountSummary?
    public let advance30dSummary: SalaryAdvanceWorkerRollingAdvanceSummary?

    public init(
        workerEntryId: String,
        employeeCode: String,
        displayName: String,
        employmentStatus: String = "ON",
        firstWorkDate: String = "",
        providerType: String = "hrp",
        recruiterDisplayName: String? = nil,
        hasPortrait: Bool = true,
        isEligible: Bool = true,
        ineligibleReasons: [String] = [],
        bankTransferEligible: Bool = false,
        bankIneligibleReason: String? = nil,
        bankAccount: SalaryAdvanceWorkerBankAccountSummary? = nil,
        advance30dSummary: SalaryAdvanceWorkerRollingAdvanceSummary? = nil
    ) {
        self.workerEntryId = workerEntryId
        self.employeeCode = employeeCode
        self.displayName = displayName
        self.employmentStatus = employmentStatus
        self.firstWorkDate = firstWorkDate
        self.providerType = providerType
        self.recruiterDisplayName = recruiterDisplayName
        self.hasPortrait = hasPortrait
        self.isEligible = isEligible
        self.ineligibleReasons = ineligibleReasons
        self.bankTransferEligible = bankTransferEligible
        self.bankIneligibleReason = bankIneligibleReason
        self.bankAccount = bankAccount
        self.advance30dSummary = advance30dSummary
    }
}

public struct SalaryAdvanceProjectWorkersPage: Equatable, Sendable {
    public let projectId: String
    public let nextCursor: String?
    public let hasMore: Bool
    public let workers: [SalaryAdvanceWorkerOption]

    public init(
        projectId: String,
        nextCursor: String? = nil,
        hasMore: Bool = false,
        workers: [SalaryAdvanceWorkerOption] = []
    ) {
        self.projectId = projectId
        self.nextCursor = nextCursor
        self.hasMore = hasMore
        self.workers = workers
    }
}

public struct SalaryAdvanceWorkerHistoryItem: Identifiable, Equatable, Sendable {
    public var id: String { requestId }
    public let requestId: String
    public let requestState: String
    public let evidenceReviewState: String
    public let projectId: String
    public let projectDisplayName: String
    public let workerEntryId: String
    public let workerEmployeeCode: String
    public let workerDisplayName: String
    public let requestedAmountVnd: String
    public let paymentMethod: String
    public let initialContent: String?
    public let createdByDisplayName: String?
    public let effectiveDate: String
    public let effectiveAt: String
    public let createdAt: String

    public init(
        requestId: String,
        requestState: String,
        evidenceReviewState: String = "NOT_UPLOADED",
        projectId: String = "",
        projectDisplayName: String = "",
        workerEntryId: String = "",
        workerEmployeeCode: String = "",
        workerDisplayName: String = "",
        requestedAmountVnd: String = "0",
        paymentMethod: String = "CASH",
        initialContent: String? = nil,
        createdByDisplayName: String? = nil,
        effectiveDate: String = "",
        effectiveAt: String = "",
        createdAt: String = ""
    ) {
        self.requestId = requestId
        self.requestState = requestState
        self.evidenceReviewState = evidenceReviewState
        self.projectId = projectId
        self.projectDisplayName = projectDisplayName
        self.workerEntryId = workerEntryId
        self.workerEmployeeCode = workerEmployeeCode
        self.workerDisplayName = workerDisplayName
        self.requestedAmountVnd = requestedAmountVnd
        self.paymentMethod = paymentMethod
        self.initialContent = initialContent
        self.createdByDisplayName = createdByDisplayName
        self.effectiveDate = effectiveDate
        self.effectiveAt = effectiveAt
        self.createdAt = createdAt
    }
}

public struct SalaryAdvanceWorkerHistoryResponse: Equatable, Sendable {
    public let workerEntryId: String
    public let workerEmployeeCode: String
    public let workerDisplayName: String
    public let periodMode: String
    public let month: String?
    public let fromDate: String?
    public let toDate: String?
    public let totalCount: Int
    public let totalAmountVnd: String
    public let nextCursor: String?
    public let hasMore: Bool
    public let items: [SalaryAdvanceWorkerHistoryItem]

    public init(
        workerEntryId: String,
        workerEmployeeCode: String,
        workerDisplayName: String,
        periodMode: String,
        month: String? = nil,
        fromDate: String? = nil,
        toDate: String? = nil,
        totalCount: Int = 0,
        totalAmountVnd: String = "0",
        nextCursor: String? = nil,
        hasMore: Bool = false,
        items: [SalaryAdvanceWorkerHistoryItem] = []
    ) {
        self.workerEntryId = workerEntryId
        self.workerEmployeeCode = workerEmployeeCode
        self.workerDisplayName = workerDisplayName
        self.periodMode = periodMode
        self.month = month
        self.fromDate = fromDate
        self.toDate = toDate
        self.totalCount = totalCount
        self.totalAmountVnd = totalAmountVnd
        self.nextCursor = nextCursor
        self.hasMore = hasMore
        self.items = items
    }
}

public struct SalaryAdvanceEligibilityDescription: Equatable, Sendable {
    public let isEligible: Bool
    public let statusLabel: String
    public let portraitStatusLabel: String
    public let reasonLabels: [String]
    public let blockingMessage: String?
}

public struct SalaryAdvanceBankStatusDescription: Equatable, Sendable {
    public let canUseBankTransfer: Bool
    public let statusLabel: String
    public let blockingMessage: String?
    public let institutionName: String?
    public let accountNumberDisplay: String?
    public let accountHolderName: String?
}

public struct SalaryAdvancePaymentDestinationSnapshot: Equatable, Sendable {
    public let bankId: String?
    public let institutionName: String
    public let accountNumber: String
    public let accountNumberMasked: String
    public let accountHolderName: String

    public var bankShortName: String? { institutionName.isEmpty ? nil : institutionName }
    public var bankCode: String? { bankId }
    public var bankAccountNumber: String? { accountNumberMasked.isEmpty ? (accountNumber.isEmpty ? nil : accountNumber) : accountNumberMasked }
    public var bankAccountHolderName: String? { accountHolderName.isEmpty ? nil : accountHolderName }
}

public struct SalaryAdvanceWorkerPortraitMeta: Equatable, Sendable {
    public let documentId: String
    public let version: Int
    public let mimeType: String
    public let sizeBytes: Int64
    public let checksumSha256: String

    public var available: Bool { !documentId.isEmpty }
}

public struct SalaryAdvanceCurrentEvidenceMeta: Equatable, Sendable {
    public let evidenceVersion: Int
    public let evidenceKind: String
    public let mimeType: String
    public let sizeBytes: Int64
    public let checksumSha256: String
    public let uploadedByUserId: String
    public let uploadedByDisplayName: String
    public let uploadedAt: String
    public let reviewedByUserId: String?
    public let reviewedByDisplayName: String?
    public let reviewedAt: String?
    public let reviewNote: String?
    public let reviewedWorkerPortraitDocumentId: String?
    public let reviewedWorkerPortraitVersion: Int?

    public var version: Int { evidenceVersion }
}

public struct SalaryAdvanceAllowedActions: Equatable, Sendable {
    public let canPostMessage: Bool
    public let canProvideInfo: Bool
    public let canWithdraw: Bool
    public let canUploadEvidence: Bool
    public let canRequestInfo: Bool
    public let canApprove: Bool
    public let canReject: Bool
    public let canMarkHandledExternally: Bool
    public let canReviewEvidence: Bool

    public var canUploadCashEvidence: Bool { canUploadEvidence }

    public init(
        canPostMessage: Bool = false,
        canProvideInfo: Bool = false,
        canWithdraw: Bool = false,
        canUploadEvidence: Bool = false,
        canRequestInfo: Bool = false,
        canApprove: Bool = false,
        canReject: Bool = false,
        canMarkHandledExternally: Bool = false,
        canReviewEvidence: Bool = false
    ) {
        self.canPostMessage = canPostMessage
        self.canProvideInfo = canProvideInfo
        self.canWithdraw = canWithdraw
        self.canUploadEvidence = canUploadEvidence
        self.canRequestInfo = canRequestInfo
        self.canApprove = canApprove
        self.canReject = canReject
        self.canMarkHandledExternally = canMarkHandledExternally
        self.canReviewEvidence = canReviewEvidence
    }
}

public struct SalaryAdvanceProjectAccountantActor: Equatable, Sendable {
    public let appUserId: String
    public let displayName: String

    public var userId: String { appUserId }
    public var username: String? { nil }
}

public struct SalaryAdvanceRequestSummary: Identifiable, Equatable, Sendable {
    public var id: String { requestId }
    public let requestId: String
    public let projectId: String
    public let projectDisplayName: String
    public let workerEntryId: String
    public let workerEmployeeCode: String
    public let workerDisplayName: String
    public let workerFirstWorkDate: String?
    public let workerRecruiterDisplayName: String?
    public let workerRecruiterCode: String?
    public let workerProviderType: String?
    public let workerPortrait: SalaryAdvanceWorkerPortraitMeta?
    public let requestedAmountVnd: String
    public let paymentMethod: String
    public let paymentDestinationSnapshot: SalaryAdvancePaymentDestinationSnapshot?
    public let initialContent: String
    public let requestState: String
    public let evidenceReviewState: String
    public let version: Int
    public let createdByUserId: String
    public let createdByDisplayName: String
    public let decidedByUserId: String?
    public let decidedByDisplayName: String?
    public let decidedAt: String?
    public let decisionNote: String?
    public let handledExternallyByUserId: String?
    public let handledExternallyByDisplayName: String?
    public let handledExternallyAt: String?
    public let handledExternallyNote: String?
    public let withdrawnByUserId: String?
    public let withdrawnByDisplayName: String?
    public let withdrawnAt: String?
    public let withdrawnNote: String?
    public let currentEvidence: SalaryAdvanceCurrentEvidenceMeta?
    public let currentProjectAccountant: SalaryAdvanceProjectAccountantActor?
    public let workerAdvance30dSummary: SalaryAdvanceWorkerRollingAdvanceSummary
    public let allowedActions: SalaryAdvanceAllowedActions
    public let lastActivityAt: String
    public let createdAt: String
    public let updatedAt: String

    public var workerStartDate: String? { workerFirstWorkDate }
    public var workerRecruiterUsername: String? { workerRecruiterCode }
    public var createdByUsername: String? { nil }
    public var projectAccountants: [SalaryAdvanceProjectAccountantActor] {
        if let acc = currentProjectAccountant {
            return [acc]
        }
        return []
    }
}

public struct SalaryAdvanceRequestListResponse: Equatable, Sendable {
    public let requests: [SalaryAdvanceRequestSummary]
    public let hasMore: Bool
    public let nextCursor: String?
    public let badgeCount: Int?
    public let badgeCountCapped: Bool
}

public struct SalaryAdvanceEventItem: Identifiable, Equatable, Sendable {
    public var id: String { eventId }
    public let eventId: String
    public let eventSeq: Int
    public let eventType: String
    public let actorUserId: String
    public let actorDisplayName: String
    public let actorRole: String
    public let fromRequestState: String?
    public let toRequestState: String
    public let fromEvidenceReviewState: String?
    public let toEvidenceReviewState: String
    public let messageText: String?
    public let evidenceVersion: Int?
    public let evidenceKind: String?
    public let evidenceMimeType: String?
    public let evidenceSizeBytes: Int64?
    public let evidenceChecksumSha256: String?
    public let reviewedWorkerPortraitDocumentId: String?
    public let reviewedWorkerPortraitVersion: Int?
    public let createdAt: String

    public var actorUsername: String? { nil }
    public var actorRoleContext: String { actorRole }
}

public struct SalaryAdvanceEvidenceVersionItem: Identifiable, Equatable, Sendable {
    public var id: Int { evidenceVersion }
    public let evidenceVersion: Int
    public let evidenceKind: String
    public let eventId: String
    public let mimeType: String
    public let sizeBytes: Int64
    public let checksumSha256: String
    public let captionNote: String?
    public let uploadedByUserId: String
    public let uploadedByDisplayName: String
    public let uploadedAt: String

    public var version: Int { evidenceVersion }
}

public struct SalaryAdvanceRequestDetail: Equatable, Sendable {
    public let request: SalaryAdvanceRequestSummary
    public let events: [SalaryAdvanceEventItem]
    public let evidenceVersions: [SalaryAdvanceEvidenceVersionItem]
    public let workerAdvance30dSummary: SalaryAdvanceWorkerRollingAdvanceSummary?
    public let replayed: Bool
}

public struct SalaryAdvanceRequestAttentionView: Equatable, Sendable {
    public let needsPmInfoResponse: Bool
    public let needsPmEvidenceUpload: Bool
    public let needsPmAttention: Bool
    public let pmAttentionReason: String?

    public var pmNeedsAction: Bool { needsPmAttention }
    public var pmReasonLabel: String? { pmAttentionReason }
}

// MARK: - Salary Advance Contract (`SalaryAdvanceContract`)

public enum SalaryAdvanceContract {
    public static let paymentMethodCash = "CASH"
    public static let paymentMethodBankTransfer = "BANK_TRANSFER"
    public static let paymentMethods: [String] = [paymentMethodCash, paymentMethodBankTransfer]
    public static let evidenceMaxBytes: Int = 10 * 1024 * 1024 // 10 MB

    public static let requestStateFilterOptions: [(key: String, label: String)] = [
        ("ALL", "Tất cả trạng thái"),
        ("OPEN", "Đang mở"),
        ("NEEDS_INFO", "Cần bổ sung"),
        ("APPROVED", "Đã chấp thuận"),
        ("REJECTED", "Đã từ chối"),
        ("WITHDRAWN", "Đã rút"),
        ("HANDLED_EXTERNALLY", "Xử lý ngoài BI")
    ]

    public static let evidenceReviewStateFilterOptions: [(key: String, label: String)] = [
        ("ALL", "Tất cả đối chiếu"),
        ("NOT_UPLOADED", "Chưa có ảnh"),
        ("PENDING_REVIEW", "Chờ đối chiếu"),
        ("REVIEWED", "Đã đối chiếu"),
        ("RETAKE_REQUIRED", "Cần gửi lại ảnh")
    ]

    public static let workerPickerEligibilityFilterOptions: [(key: String, label: String)] = [
        ("ALL", "Tất cả"),
        ("ELIGIBLE", "Đủ điều kiện"),
        ("INELIGIBLE", "Chưa đủ ĐK")
    ]

    public static let periodModeCurrentMonth = "THIS_MONTH"
    public static let periodModeLast30Days = "LAST_30_DAYS"
    public static let periodModeMonth = "MONTH"
    public static let periodModeAll = "ALL"
    public static let periodModeCustomRange = "CUSTOM_RANGE"

    public static let historyPeriodModes: [(key: String, label: String)] = [
        (periodModeCurrentMonth, "Tháng này"),
        (periodModeLast30Days, "30 ngày gần nhất"),
        (periodModeMonth, "Chọn tháng"),
        (periodModeAll, "Toàn bộ"),
        (periodModeCustomRange, "Khoảng ngày")
    ]

    public static func canShowPmSalaryAdvanceEntry(_ context: SalaryAdvanceActorContext?) -> Bool {
        guard let context else { return false }
        return context.isProjectManager && !context.activeManagedProjects.isEmpty
    }

    public static func shouldHideProjectWorkerFromSelection(_ worker: SalaryAdvanceWorkerOption) -> Bool {
        if worker.employmentStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "OFF" {
            return true
        }
        if worker.providerType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "vendor" {
            return true
        }
        if worker.ineligibleReasons.contains("WORKER_STATUS_OFF") ||
            worker.ineligibleReasons.contains("WORKER_VENDOR_RECRUITED") {
            return true
        }
        return false
    }

    public static func filterSelectableProjectWorkers(_ workers: [SalaryAdvanceWorkerOption]) -> [SalaryAdvanceWorkerOption] {
        workers.filter { !shouldHideProjectWorkerFromSelection($0) }
    }

    public static func workerIneligibleReasonLabel(_ code: String) -> String {
        switch code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "WORKER_STATUS_OFF":
            return "Đang ở trạng thái Đã nghỉ việc"
        case "WORKER_STATUS_UNCONFIRMED":
            return "Chưa xác nhận đi làm (Đang chờ việc)"
        case "WORKER_VENDOR_RECRUITED":
            return "Thuộc nguồn tuyển dụng Vendor"
        case "WORKER_PORTRAIT_MISSING":
            return "Thiếu ảnh chân dung NLĐ"
        case "WORKER_DUPLICATE_CCCD":
            return "Hồ sơ đang bị trùng CCCD chưa chuẩn hóa"
        case "WORKER_MULTI_PROJECT_ON":
            return "Đang làm việc ở nhiều hơn 1 dự án"
        default:
            return "Chưa đủ điều kiện ứng lương"
        }
    }

    public static func bankIneligibleReasonLabel(_ code: String?) -> String? {
        switch code?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "BANK_ACCOUNT_MISSING":
            return "Hồ sơ NLĐ chưa có thông tin tài khoản ngân hàng"
        case "BANK_ACCOUNT_NUMBER_MISSING":
            return "Hồ sơ NLĐ thiếu số tài khoản ngân hàng"
        case "BANK_ACCOUNT_INSTITUTION_MISSING":
            return "Hồ sơ NLĐ thiếu tên ngân hàng"
        case "BANK_ACCOUNT_HOLDER_MISSING":
            return "Hồ sơ NLĐ thiếu tên chủ tài khoản"
        case "BANK_ACCOUNT_NOT_SELF_OWNED":
            return "Tên chủ tài khoản không trùng khớp chính chủ với họ tên NLĐ"
        default:
            return nil
        }
    }

    public static func describeWorkerEligibility(_ worker: SalaryAdvanceWorkerOption) -> SalaryAdvanceEligibilityDescription {
        var reasons = worker.ineligibleReasons.map { workerIneligibleReasonLabel($0) }
        if !worker.hasPortrait && !worker.ineligibleReasons.contains("WORKER_PORTRAIT_MISSING") {
            reasons.append("Thiếu ảnh chân dung NLĐ")
        }
        let portraitStatusLabel = worker.hasPortrait ? "Đã có ảnh chân dung" : "Thiếu ảnh chân dung"
        if worker.isEligible && reasons.isEmpty {
            return SalaryAdvanceEligibilityDescription(
                isEligible: true,
                statusLabel: "Đủ điều kiện ứng lương",
                portraitStatusLabel: portraitStatusLabel,
                reasonLabels: [],
                blockingMessage: nil
            )
        }
        let joined = reasons.isEmpty ? "Chưa đủ điều kiện ứng lương" : reasons.joined(separator: "; ")
        return SalaryAdvanceEligibilityDescription(
            isEligible: false,
            statusLabel: "Chưa đủ điều kiện",
            portraitStatusLabel: portraitStatusLabel,
            reasonLabels: reasons,
            blockingMessage: "Không thể gửi yêu cầu ứng lương: \(joined)."
        )
    }

    public static func describeWorkerBankTransferEligibility(_ worker: SalaryAdvanceWorkerOption) -> SalaryAdvanceBankStatusDescription {
        let bank = worker.bankAccount
        let reasonText = bankIneligibleReasonLabel(worker.bankIneligibleReason)
        if worker.bankTransferEligible {
            return SalaryAdvanceBankStatusDescription(
                canUseBankTransfer: true,
                statusLabel: "Đủ điều kiện chuyển khoản chính chủ",
                blockingMessage: nil,
                institutionName: bank?.institutionName,
                accountNumberDisplay: bank?.accountNumber,
                accountHolderName: bank?.accountHolderName
            )
        }
        let detail = reasonText ?? "Hồ sơ ngân hàng chưa đủ điều kiện chuyển khoản chính chủ"
        return SalaryAdvanceBankStatusDescription(
            canUseBankTransfer: false,
            statusLabel: "Chưa đủ điều kiện chuyển khoản",
            blockingMessage: "Không thể chọn Chuyển khoản: \(detail). Vui lòng chọn Tiền mặt hoặc cập nhật tài khoản ngân hàng chính chủ.",
            institutionName: bank?.institutionName,
            accountNumberDisplay: bank?.accountNumber,
            accountHolderName: bank?.accountHolderName
        )
    }

    public static func paymentMethodLabel(_ method: String) -> String {
        switch method.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "CASH":
            return "Tiền mặt"
        case "BANK_TRANSFER":
            return "Chuyển khoản"
        default:
            return method
        }
    }

    public static func requestStateShortLabel(_ state: String) -> String {
        switch state.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "OPEN":
            return "Đang mở"
        case "NEEDS_INFO":
            return "Cần bổ sung"
        case "APPROVED":
            return "Đã chấp thuận"
        case "REJECTED":
            return "Đã từ chối"
        case "WITHDRAWN":
            return "Đã rút"
        case "HANDLED_EXTERNALLY":
            return "Xử lý ngoài BI"
        default:
            return state
        }
    }

    public static func evidenceReviewStateShortLabel(_ state: String) -> String {
        switch state.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "NOT_UPLOADED":
            return "Chưa có ảnh"
        case "PENDING_REVIEW", "UPLOADED_PENDING_REVIEW":
            return "Chờ đối chiếu"
        case "REVIEWED":
            return "Đã đối chiếu"
        case "RETAKE_REQUIRED":
            return "Cần gửi lại ảnh"
        default:
            return state
        }
    }

    public static func eventTypeLabel(_ eventType: String?) -> String {
        switch eventType?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "REQUEST_CREATED":
            return "QLDA tạo yêu cầu ứng lương"
        case "MESSAGE_POSTED":
            return "Gửi tin nhắn trao đổi"
        case "INFO_REQUESTED":
            return "Kế toán yêu cầu bổ sung thông tin"
        case "INFO_PROVIDED":
            return "QLDA đã bổ sung thông tin"
        case "REQUEST_APPROVED":
            return "Kế toán chấp thuận ứng lương"
        case "REQUEST_REJECTED":
            return "Kế toán từ chối yêu cầu"
        case "REQUEST_WITHDRAWN":
            return "QLDA đã rút yêu cầu"
        case "HANDLED_EXTERNALLY_MARKED":
            return "Đánh dấu xử lý ngoài hệ thống"
        case "EVIDENCE_UPLOADED":
            return "Tải lên ảnh chứng từ nhận tiền"
        case "EVIDENCE_REVIEWED":
            return "Kế toán xác nhận đã đối chiếu chứng từ"
        case "EVIDENCE_RETAKE_REQUESTED":
            return "Kế toán yêu cầu gửi lại ảnh chứng từ"
        default:
            return eventType ?? ""
        }
    }

    public static func actorRoleLabel(_ role: String?) -> String {
        switch role?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "PROJECT_MANAGER":
            return "Quản lý dự án (QLDA)"
        case "PROJECT_ACCOUNTANT":
            return "Kế toán dự án"
        default:
            return role ?? ""
        }
    }

    public static func resolveActorDisplayName(_ raw: String?, fallbackLabel: String = "Chưa cập nhật tên") -> String {
        let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmed.isEmpty || WorkerItem.isUuidLike(trimmed) {
            return fallbackLabel
        }
        return trimmed
    }

    public static func resolveActorDisplayName(
        displayName: String?,
        username: String? = nil,
        fallback: String = "Chưa cập nhật tên"
    ) -> String {
        let safeDisplay = displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !safeDisplay.isEmpty && !WorkerItem.isUuidLike(safeDisplay) {
            return safeDisplay
        }
        let safeUser = username?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !safeUser.isEmpty && !WorkerItem.isUuidLike(safeUser) {
            return safeUser
        }
        return fallback
    }

    public static func formatWorkerRecruiterLabel(
        recruiterDisplayName: String?,
        recruiterCode: String? = nil,
        providerType: String? = nil,
        fallbackLabel: String = "Chưa cập nhật"
    ) -> String {
        let safeName = (recruiterDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap {
            ($0.isEmpty || WorkerItem.isUuidLike($0)) ? nil : $0
        }
        let safeCode = (recruiterCode?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap {
            ($0.isEmpty || WorkerItem.isUuidLike($0)) ? nil : $0
        }
        let isVendor = providerType?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "vendor"
        guard var base = safeName ?? safeCode else {
            return isVendor ? "Vendor (Chưa cập nhật tên)" : fallbackLabel
        }
        if let safeName, let safeCode,
           safeName.caseInsensitiveCompare(safeCode) != .orderedSame,
           !safeName.localizedCaseInsensitiveContains(safeCode) {
            base = "\(safeName) (\(safeCode))"
        }
        if isVendor && !base.localizedCaseInsensitiveContains("vendor") {
            return "\(base) · Vendor"
        }
        return base
    }

    public static func formatWorkDateVi(_ rawDate: String?) -> String {
        let trimmed = rawDate?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmed.isEmpty else { return "—" }
        let ui = DirectEntryContract.isoDateToUiDate(trimmed)
        return ui.isEmpty ? trimmed : ui
    }

    public static func formatDateTimeVi(_ isoTimestamp: String?) -> String {
        let trimmed = isoTimestamp?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmed.isEmpty else { return "—" }
        if let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(trimmed) {
            let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
            let df = DateFormatter()
            df.locale = Locale(identifier: "vi_VN")
            df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
            df.dateFormat = "dd/MM/yyyy HH:mm"
            return df.string(from: date)
        }
        return trimmed
    }

    public static func formatFileSizeBytes(_ sizeBytes: Int64?) -> String {
        guard let sizeBytes, sizeBytes > 0 else { return "0 B" }
        if sizeBytes < 1024 { return "\(sizeBytes) B" }
        if sizeBytes < 1024 * 1024 { return String(format: "%.1f KB", Double(sizeBytes) / 1024.0) }
        return String(format: "%.2f MB", Double(sizeBytes) / (1024.0 * 1024.0))
    }

    public static func sha256Hex(_ data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public static func evaluateRequestAttention(_ item: SalaryAdvanceRequestSummary) -> SalaryAdvanceRequestAttentionView {
        let state = item.requestState.uppercased()
        let evState = item.evidenceReviewState.uppercased()
        let isCash = item.paymentMethod.uppercased() != "BANK_TRANSFER"
        let needsPmInfoResponse = state == "NEEDS_INFO"
        let needsPmEvidenceUpload = isCash && state == "APPROVED" && (evState == "NOT_UPLOADED" || evState == "RETAKE_REQUIRED")
        let needsPmAttention = needsPmInfoResponse || needsPmEvidenceUpload
        let pmAttentionReason: String? = {
            if needsPmInfoResponse {
                return "Cần bổ sung thông tin theo yêu cầu của Kế toán"
            }
            if state == "APPROVED" && isCash && evState == "RETAKE_REQUIRED" {
                return "Cần chụp/tải lại ảnh NLĐ nhận tiền mặt"
            }
            if state == "APPROVED" && isCash && evState == "NOT_UPLOADED" {
                return "Đã duyệt — chờ QLDA tải ảnh NLĐ nhận tiền mặt"
            }
            return nil
        }()
        return SalaryAdvanceRequestAttentionView(
            needsPmInfoResponse: needsPmInfoResponse,
            needsPmEvidenceUpload: needsPmEvidenceUpload,
            needsPmAttention: needsPmAttention,
            pmAttentionReason: pmAttentionReason
        )
    }

    public static func normalizeVietnameseForSearch(_ raw: String?) -> String {
        let text = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !text.isEmpty else { return "" }
        return text
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "vi_VN"))
            .replacingOccurrences(of: "đ", with: "d")
            .replacingOccurrences(of: "Đ", with: "d")
            .lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    public static func matchesWorkerSearchQuery(
        worker: SalaryAdvanceWorkerOption,
        query: String?,
        eligibilityFilter: String = "ALL"
    ) -> Bool {
        let cleanFilter = eligibilityFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cleanFilter == "ELIGIBLE" || cleanFilter == "INELIGIBLE" {
            let isEligible = describeWorkerEligibility(worker).isEligible
            if cleanFilter == "ELIGIBLE" && !isEligible { return false }
            if cleanFilter == "INELIGIBLE" && isEligible { return false }
        }
        let normalizedQuery = normalizeVietnameseForSearch(query)
        if normalizedQuery.isEmpty { return true }
        let tokens = normalizedQuery.split(separator: " ").map(String.init).filter { !$0.isEmpty }
        if tokens.isEmpty { return true }
        let searchable = [
            normalizeVietnameseForSearch(worker.displayName),
            normalizeVietnameseForSearch(worker.employeeCode),
            normalizeVietnameseForSearch(worker.recruiterDisplayName)
        ].joined(separator: " ")
        return tokens.allSatisfy { searchable.contains($0) }
    }

    public static func buildWorkerPortraitPreviewPath(requestId: String, expectedDocumentId: String? = nil) -> String {
        let encodedReq = requestId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? requestId
        if let docId = expectedDocumentId?.trimmingCharacters(in: .whitespacesAndNewlines), !docId.isEmpty {
            let encodedDoc = docId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? docId
            return "/api/salary-advance/requests/\(encodedReq)/worker-portrait?expected_document_id=\(encodedDoc)"
        }
        return "/api/salary-advance/requests/\(encodedReq)/worker-portrait"
    }

    public static func buildEvidencePhotoPreviewPath(requestId: String, evidenceVersion: Int) -> String {
        let encodedReq = requestId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? requestId
        return "/api/salary-advance/requests/\(encodedReq)/evidence/\(evidenceVersion)/preview"
    }

    public static func buildEvidencePhotoPreviewPath(requestId: String, version: Int) -> String {
        buildEvidencePhotoPreviewPath(requestId: requestId, evidenceVersion: version)
    }

    public static func isConflictError(statusCode: Int?, errorCode: String?) -> Bool {
        if statusCode == 409 { return true }
        let clean = errorCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        return [
            "SALARY_ADVANCE_VERSION_CONFLICT",
            "SALARY_ADVANCE_STATE_CONFLICT",
            "SALARY_ADVANCE_EVIDENCE_VERSION_CONFLICT",
            "SALARY_ADVANCE_WORKER_PORTRAIT_VERSION_CONFLICT",
            "SALARY_ADVANCE_IDEMPOTENCY_CONFLICT"
        ].contains(clean)
    }

    public static func formatWorkerRecruiterLabel(_ worker: SalaryAdvanceWorkerOption) -> String {
        formatWorkerRecruiterLabel(
            recruiterDisplayName: worker.recruiterDisplayName,
            recruiterCode: nil,
            providerType: worker.providerType,
            fallbackLabel: worker.providerType.lowercased() == "vendor" ? "Vendor" : "HRP"
        )
    }

    public static func normalizePositiveVndInput(_ raw: String) -> String? {
        let digitsOnly = raw.filter { $0.isNumber || !$0.isWhitespace && $0 != "." && $0 != "," }
        let trimmed = digitsOnly.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.allSatisfy(\.isNumber) else {
            return nil
        }
        let strippedLeadingZeros = String(trimmed.drop(while: { $0 == "0" }))
        guard !strippedLeadingZeros.isEmpty, strippedLeadingZeros.count <= 15 else {
            return nil
        }
        return strippedLeadingZeros
    }

    public static func formatVndAmount(_ rawDigits: String) -> String {
        let digits = rawDigits.trimmingCharacters(in: .whitespacesAndNewlines).filter(\.isNumber)
        let clean = String(digits.drop(while: { $0 == "0" }))
        if clean.isEmpty {
            return "0 đ"
        }
        var reversedChars: [Character] = []
        for (idx, ch) in clean.reversed().enumerated() {
            if idx > 0 && idx % 3 == 0 {
                reversedChars.append(".")
            }
            reversedChars.append(ch)
        }
        return "\(String(reversedChars.reversed())) đ"
    }

    public static func buildCreateRequestPayload(
        projectId: String,
        workerEntryId: String,
        requestedAmountRaw: String,
        paymentMethod: String,
        initialContent: String,
        idempotencyKey: String
    ) throws -> [String: Any] {
        let cleanProject = projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanProject.isEmpty else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Vui lòng chọn dự án quản lý."])
        }
        let cleanWorker = workerEntryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanWorker.isEmpty else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Vui lòng chọn người lao động cần ứng lương."])
        }
        guard let normalizedAmount = normalizePositiveVndInput(requestedAmountRaw) else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Số tiền ứng phải là số nguyên dương VND hợp lệ (lớn hơn 0)."])
        }
        let cleanMethod = paymentMethod.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard paymentMethods.contains(cleanMethod) else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Phương thức nhận tiền không hợp lệ (chỉ hỗ trợ Tiền mặt hoặc Chuyển khoản)."])
        }
        let cleanContent = initialContent.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanContent.isEmpty else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Vui lòng nhập nội dung đề nghị ứng lương."])
        }
        guard cleanContent.count <= 2000 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Nội dung đề nghị ứng lương tối đa 2.000 ký tự."])
        }
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanIdem.count >= 8 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mã chống gửi trùng (idempotency_key) không hợp lệ."])
        }

        return [
            "project_id": cleanProject,
            "worker_entry_id": cleanWorker,
            "requested_amount_vnd": normalizedAmount,
            "payment_method": cleanMethod,
            "initial_content": cleanContent,
            "idempotency_key": cleanIdem
        ]
    }

    public static func buildPostMessagePayload(
        expectedVersion: Int,
        messageText: String,
        idempotencyKey: String
    ) throws -> [String: Any] {
        let cleanText = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanText.isEmpty else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Vui lòng nhập nội dung tin nhắn trao đổi."])
        }
        guard cleanText.count <= 2000 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Nội dung tin nhắn tối đa 2.000 ký tự."])
        }
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanIdem.count >= 8 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mã chống gửi trùng không hợp lệ."])
        }
        return [
            "expected_version": max(1, expectedVersion),
            "message_text": cleanText,
            "idempotency_key": cleanIdem
        ]
    }

    public static func buildTransitionActionPayload(
        action: String,
        expectedVersion: Int,
        messageText: String?,
        idempotencyKey: String
    ) throws -> [String: Any] {
        let cleanAction = action.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !cleanAction.isEmpty else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Thao tác xử lý không hợp lệ."])
        }
        let cleanMsg = messageText?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if cleanAction == "PROVIDE_INFO" && cleanMsg.isEmpty {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Vui lòng nhập nội dung phản hồi bổ sung thông tin cho Kế toán."])
        }
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanIdem.count >= 8 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mã chống gửi trùng không hợp lệ."])
        }
        return [
            "action": cleanAction,
            "expected_version": max(1, expectedVersion),
            "message_text": cleanMsg.isEmpty ? NSNull() : cleanMsg,
            "idempotency_key": cleanIdem
        ]
    }

    public static func buildReserveEvidenceUploadPayload(
        expectedVersion: Int,
        mimeType: String,
        sizeBytes: Int64,
        sha256: String,
        captionNote: String?,
        idempotencyKey: String
    ) throws -> [String: Any] {
        let cleanMime = mimeType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard ["image/jpeg", "image/png", "image/webp"].contains(cleanMime) else {
            throw NSError(domain: "SalaryAdvance", code: 422, userInfo: [NSLocalizedDescriptionKey: "Ảnh chứng từ chỉ hỗ trợ định dạng JPG, PNG hoặc WEBP."])
        }
        guard sizeBytes > 0 && sizeBytes <= Int64(evidenceMaxBytes) else {
            throw NSError(domain: "SalaryAdvance", code: 422, userInfo: [NSLocalizedDescriptionKey: "Dung lượng ảnh chứng từ vượt quá giới hạn 10 MB."])
        }
        let cleanSha = sha256.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanCaption = captionNote?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanIdem.count >= 8 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mã chống gửi trùng không hợp lệ."])
        }
        return [
            "expected_version": max(1, expectedVersion),
            "mime_type": cleanMime,
            "size_bytes": sizeBytes,
            "checksum_sha256": cleanSha,
            "caption_note": cleanCaption.isEmpty ? NSNull() : cleanCaption,
            "idempotency_key": cleanIdem
        ]
    }

    public static func buildFinalizeEvidenceUploadPayload(
        expectedVersion: Int,
        idempotencyKey: String
    ) throws -> [String: Any] {
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanIdem.count >= 8 else {
            throw NSError(domain: "SalaryAdvance", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mã chống gửi trùng không hợp lệ."])
        }
        return [
            "expected_version": max(1, expectedVersion),
            "idempotency_key": cleanIdem
        ]
    }

    public static func describeSalaryAdvanceError(code: String?, httpStatus: Int, fallback: String?) -> String {
        switch code?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "SALARY_ADVANCE_PROJECT_MANAGER_REQUIRED":
            return "Chỉ Quản lý dự án (QLDA) đang phụ trách dự án này mới được tạo yêu cầu ứng lương."
        case "SALARY_ADVANCE_PROJECT_INACTIVE":
            return "Dự án đã chọn không còn hoạt động."
        case "SALARY_ADVANCE_ACCOUNTANT_REQUIRED":
            return "Dự án hiện chưa có Kế toán phụ trách đang hoạt động nên không thể tạo yêu cầu ứng lương."
        case "SALARY_ADVANCE_WORKER_NOT_FOUND":
            return "Không tìm thấy người lao động thuộc dự án đã chọn."
        case "SALARY_ADVANCE_WORKER_INELIGIBLE":
            return "Người lao động chưa đủ điều kiện ứng lương (phải đang làm việc, do HRP tuyển, có ảnh chân dung, không trùng CCCD và không đang làm ở nhiều dự án)."
        case "SALARY_ADVANCE_WORKER_DUPLICATE_CCCD":
            return "Hồ sơ NLĐ đang thuộc nhóm trùng CCCD chưa chuẩn hóa nên chưa được phép ứng lương."
        case "SALARY_ADVANCE_WORKER_MULTI_PROJECT_ON":
            return "NLĐ đang ở trạng thái Đang làm tại nhiều hơn 1 dự án nên chưa được phép ứng lương."
        case "SALARY_ADVANCE_BANK_TRANSFER_INELIGIBLE":
            return "Không đủ điều kiện chọn Chuyển khoản: NLĐ phải có tài khoản ngân hàng chính chủ (trùng họ tên NLĐ)."
        case "SALARY_ADVANCE_IDEMPOTENCY_CONFLICT", "SALARY_ADVANCE_VERSION_CONFLICT":
            return "Yêu cầu vừa thay đổi hoặc bị trùng phiên gửi trước đó. Đã làm mới mã phiên, vui lòng bấm gửi lại."
        case "SALARY_ADVANCE_HISTORY_FILTER_INVALID":
            return "Bộ lọc kỳ lịch sử ứng lương không hợp lệ. Vui lòng kiểm tra lại tháng hoặc khoảng ngày."
        case "SALARY_ADVANCE_UNAVAILABLE":
            return "Hệ thống tạm thời chưa xử lý được yêu cầu. Vui lòng thử lại."
        default:
            if httpStatus == 409 {
                return "Yêu cầu bị xung đột phiên gửi. Đã làm mới mã phiên, vui lòng thử lại."
            }
            if httpStatus >= 500 {
                return "Hệ thống tạm thời chưa xử lý được yêu cầu. Vui lòng thử lại."
            }
            return fallback ?? "Không thể thực hiện thao tác ứng lương."
        }
    }

    // MARK: - JSON Parsers

    public static func parseContextResponse(_ root: [String: Any]) -> SalaryAdvanceActorContext {
        let data = (root["data"] as? [String: Any]) ?? root
        let projects = data.optObjectArray("managed_projects").compactMap { item -> SalaryAdvanceManagedProjectOption? in
            let pId = item.optString("project_id")
            guard !pId.isEmpty else { return nil }
            return SalaryAdvanceManagedProjectOption(
                projectId: pId,
                displayName: item.optString("display_name", defaultValue: pId),
                active: item.optBool("active", defaultValue: true),
                hasActiveAccountant: item.optBool("has_active_accountant", defaultValue: false),
                accountantUserId: item.optNullableString("accountant_user_id"),
                accountantDisplayName: item.optNullableString("accountant_display_name")
            )
        }
        return SalaryAdvanceActorContext(
            appUserId: data.optString("app_user_id"),
            displayName: data.optString("display_name"),
            authorizationDate: data.optString("authorization_date"),
            isFullAdmin: data.optBool("is_full_admin"),
            isBod: data.optBool("is_bod"),
            isProjectManager: data.optBool("is_project_manager"),
            isProjectAccountant: data.optBool("is_project_accountant"),
            isSupervisoryViewer: data.optBool("is_supervisory_viewer"),
            managedProjects: projects
        )
    }

    public static func parseProjectWorkersPage(_ root: [String: Any]) -> SalaryAdvanceProjectWorkersPage {
        let data = (root["data"] as? [String: Any]) ?? root
        let workers = data.optObjectArray("workers").compactMap { w -> SalaryAdvanceWorkerOption? in
            let entryId = w.optString("worker_entry_id")
            guard !entryId.isEmpty else { return nil }
            let bankAccount: SalaryAdvanceWorkerBankAccountSummary? = {
                guard let bankObj = w["bank_account"] as? [String: Any] else { return nil }
                return SalaryAdvanceWorkerBankAccountSummary(
                    hasBankAccount: bankObj.optBool("has_bank_account"),
                    bankId: bankObj.optNullableString("bank_id"),
                    institutionName: bankObj.optNullableString("institution_name"),
                    accountNumber: bankObj.optNullableString("account_number"),
                    accountHolderName: bankObj.optNullableString("account_holder_name"),
                    isSelfOwned: bankObj.optBool("is_self_owned")
                )
            }()
            let summary30d: SalaryAdvanceWorkerRollingAdvanceSummary? = {
                guard let sumObj = w["advance_30d_summary"] as? [String: Any] else { return nil }
                return SalaryAdvanceWorkerRollingAdvanceSummary(
                    windowDays: sumObj.optInt("window_days", defaultValue: 30),
                    windowFromDate: sumObj.optString("window_from_date"),
                    windowToDate: sumObj.optString("window_to_date"),
                    totalCount: max(0, sumObj.optInt("total_count", defaultValue: 0)),
                    totalAmountVnd: sumObj.optString("total_amount_vnd", defaultValue: "0")
                )
            }()
            return SalaryAdvanceWorkerOption(
                workerEntryId: entryId,
                employeeCode: w.optString("employee_code"),
                displayName: w.optString("display_name", defaultValue: "(Chưa có họ tên)"),
                employmentStatus: w.optString("employment_status", defaultValue: "ON"),
                firstWorkDate: w.optString("first_work_date"),
                providerType: w.optString("provider_type", defaultValue: "hrp"),
                recruiterDisplayName: w.optNullableString("recruiter_display_name"),
                hasPortrait: w.optBool("has_portrait", defaultValue: false),
                isEligible: w.optBool("is_eligible", defaultValue: false),
                ineligibleReasons: w.optStringArray("ineligible_reasons"),
                bankTransferEligible: w.optBool("bank_transfer_eligible", defaultValue: false),
                bankIneligibleReason: w.optNullableString("bank_ineligible_reason"),
                bankAccount: bankAccount,
                advance30dSummary: summary30d
            )
        }
        return SalaryAdvanceProjectWorkersPage(
            projectId: data.optString("project_id"),
            nextCursor: data.optNullableString("next_cursor"),
            hasMore: data.optBool("has_more"),
            workers: workers
        )
    }

    public static func parseWorkerHistoryResponse(_ root: [String: Any]) -> SalaryAdvanceWorkerHistoryResponse {
        let data = (root["data"] as? [String: Any]) ?? root
        let items = data.optObjectArray("items").compactMap { item -> SalaryAdvanceWorkerHistoryItem? in
            let reqId = item.optString("request_id")
            guard !reqId.isEmpty else { return nil }
            return SalaryAdvanceWorkerHistoryItem(
                requestId: reqId,
                requestState: item.optString("request_state", defaultValue: "OPEN"),
                evidenceReviewState: item.optString("evidence_review_state", defaultValue: "NOT_UPLOADED"),
                projectId: item.optString("project_id"),
                projectDisplayName: item.optString("project_display_name"),
                workerEntryId: item.optString("worker_entry_id"),
                workerEmployeeCode: item.optString("worker_employee_code"),
                workerDisplayName: item.optString("worker_display_name"),
                requestedAmountVnd: item.optString("requested_amount_vnd", defaultValue: "0"),
                paymentMethod: item.optString("payment_method", defaultValue: "CASH"),
                initialContent: item.optNullableString("initial_content"),
                createdByDisplayName: item.optNullableString("created_by_display_name"),
                effectiveDate: item.optString("effective_date"),
                effectiveAt: item.optString("effective_at"),
                createdAt: item.optString("created_at")
            )
        }
        return SalaryAdvanceWorkerHistoryResponse(
            workerEntryId: data.optString("worker_entry_id"),
            workerEmployeeCode: data.optString("worker_employee_code"),
            workerDisplayName: data.optString("worker_display_name"),
            periodMode: data.optString("period_mode", defaultValue: "THIS_MONTH"),
            month: data.optNullableString("month"),
            fromDate: data.optNullableString("from_date"),
            toDate: data.optNullableString("to_date"),
            totalCount: max(0, data.optInt("total_count", defaultValue: items.count)),
            totalAmountVnd: data.optString("total_amount_vnd", defaultValue: "0"),
            nextCursor: data.optNullableString("next_cursor"),
            hasMore: data.optBool("has_more"),
            items: items
        )
    }

    public static func parseAllowedActions(_ obj: [String: Any]?) -> SalaryAdvanceAllowedActions {
        guard let obj = obj else { return SalaryAdvanceAllowedActions() }
        return SalaryAdvanceAllowedActions(
            canPostMessage: obj.optBool("can_post_message"),
            canProvideInfo: obj.optBool("can_provide_info"),
            canWithdraw: obj.optBool("can_withdraw"),
            canUploadEvidence: obj.optBool("can_upload_evidence", "can_upload_cash_evidence"),
            canRequestInfo: obj.optBool("can_request_info"),
            canApprove: obj.optBool("can_approve"),
            canReject: obj.optBool("can_reject"),
            canMarkHandledExternally: obj.optBool("can_mark_handled_externally"),
            canReviewEvidence: obj.optBool("can_review_evidence", "can_confirm_cash_evidence")
        )
    }

    public static func parseRolling30dSummary(_ obj: [String: Any]?) -> SalaryAdvanceWorkerRollingAdvanceSummary? {
        guard let obj = obj else { return nil }
        let windowDays = obj.optInt("window_days", defaultValue: 30)
        let fromDate = obj.optString("window_from_date")
        let toDate = obj.optString("window_to_date")
        let totalCount = max(0, obj.optInt("total_count", "count_30d", defaultValue: 0))
        let totalAmount = obj.optString("total_amount_vnd", "total_amount_vnd_30d", defaultValue: "0")
        return SalaryAdvanceWorkerRollingAdvanceSummary(
            windowDays: windowDays > 0 ? windowDays : 30,
            windowFromDate: fromDate,
            windowToDate: toDate,
            totalCount: totalCount,
            totalAmountVnd: totalAmount.isEmpty ? "0" : totalAmount
        )
    }

    public static func parseRequestSummary(_ obj: [String: Any], fallback30d: SalaryAdvanceWorkerRollingAdvanceSummary? = nil) -> SalaryAdvanceRequestSummary {
        let portraitMeta: SalaryAdvanceWorkerPortraitMeta? = {
            guard let p = obj["worker_portrait"] as? [String: Any] else { return nil }
            let docId = p.optString("document_id")
            guard !docId.isEmpty else { return nil }
            return SalaryAdvanceWorkerPortraitMeta(
                documentId: docId,
                version: max(1, p.optInt("version", defaultValue: 1)),
                mimeType: p.optString("mime_type", defaultValue: "image/jpeg"),
                sizeBytes: p.optInt64("size_bytes") ?? 0,
                checksumSha256: p.optString("checksum_sha256")
            )
        }()

        let currentEvidence: SalaryAdvanceCurrentEvidenceMeta? = {
            guard let ev = obj["current_evidence"] as? [String: Any] else { return nil }
            let ver = ev.optInt("evidence_version", "version", defaultValue: 0)
            guard ver > 0 else { return nil }
            let revVer = ev.optInt("reviewed_worker_portrait_version", defaultValue: 0)
            return SalaryAdvanceCurrentEvidenceMeta(
                evidenceVersion: ver,
                evidenceKind: ev.optString("evidence_kind", defaultValue: "CASH_HANDOVER_PHOTO"),
                mimeType: ev.optString("mime_type", defaultValue: "image/jpeg"),
                sizeBytes: ev.optInt64("size_bytes") ?? 0,
                checksumSha256: ev.optString("checksum_sha256"),
                uploadedByUserId: ev.optString("uploaded_by_user_id"),
                uploadedByDisplayName: ev.optString("uploaded_by_display_name"),
                uploadedAt: ev.optString("uploaded_at"),
                reviewedByUserId: ev.optNullableString("reviewed_by_user_id"),
                reviewedByDisplayName: ev.optNullableString("reviewed_by_display_name"),
                reviewedAt: ev.optNullableString("reviewed_at"),
                reviewNote: ev.optNullableString("review_note"),
                reviewedWorkerPortraitDocumentId: ev.optNullableString("reviewed_worker_portrait_document_id"),
                reviewedWorkerPortraitVersion: revVer > 0 ? revVer : nil
            )
        }()

        let paySnap: SalaryAdvancePaymentDestinationSnapshot? = {
            guard let s = obj["payment_destination_snapshot"] as? [String: Any] else { return nil }
            let inst = s.optString("institution_name", "bank_short_name")
            let acct = s.optString("account_number", "bank_account_number")
            let holder = s.optString("account_holder_name", "bank_account_holder_name")
            if inst.isEmpty && acct.isEmpty && holder.isEmpty { return nil }
            let masked = s.optString("account_number_masked")
            return SalaryAdvancePaymentDestinationSnapshot(
                bankId: s.optNullableString("bank_id", "bank_code"),
                institutionName: inst,
                accountNumber: acct,
                accountNumberMasked: masked.isEmpty ? acct : masked,
                accountHolderName: holder
            )
        }()

        let currentProjectAccountant: SalaryAdvanceProjectAccountantActor? = {
            if let a = obj["current_project_accountant"] as? [String: Any] {
                let userId = a.optString("app_user_id", "user_id")
                let name = a.optString("display_name")
                if !userId.isEmpty || !name.isEmpty {
                    return SalaryAdvanceProjectAccountantActor(appUserId: userId, displayName: name)
                }
            }
            if let firstAcc = obj.optObjectArray("project_accountants").first {
                let userId = firstAcc.optString("app_user_id", "user_id")
                let name = firstAcc.optString("display_name")
                if !userId.isEmpty || !name.isEmpty {
                    return SalaryAdvanceProjectAccountantActor(appUserId: userId, displayName: name)
                }
            }
            return nil
        }()

        let summary30d = parseRolling30dSummary(obj["worker_advance_30d_summary"] as? [String: Any])
            ?? fallback30d
            ?? SalaryAdvanceWorkerRollingAdvanceSummary()

        return SalaryAdvanceRequestSummary(
            requestId: obj.optString("request_id"),
            projectId: obj.optString("project_id"),
            projectDisplayName: obj.optString("project_display_name"),
            workerEntryId: obj.optString("worker_entry_id"),
            workerEmployeeCode: obj.optString("worker_employee_code"),
            workerDisplayName: obj.optString("worker_display_name"),
            workerFirstWorkDate: obj.optNullableString("worker_first_work_date", "worker_start_date"),
            workerRecruiterDisplayName: obj.optNullableString("worker_recruiter_display_name"),
            workerRecruiterCode: obj.optNullableString("worker_recruiter_code", "worker_recruiter_username"),
            workerProviderType: obj.optNullableString("worker_provider_type"),
            workerPortrait: portraitMeta,
            requestedAmountVnd: obj.optString("requested_amount_vnd", defaultValue: "0"),
            paymentMethod: obj.optString("payment_method", defaultValue: "CASH"),
            paymentDestinationSnapshot: paySnap,
            initialContent: obj.optString("initial_content"),
            requestState: obj.optString("request_state", defaultValue: "OPEN"),
            evidenceReviewState: obj.optString("evidence_review_state", defaultValue: "NOT_UPLOADED"),
            version: max(1, obj.optInt("version", defaultValue: 1)),
            createdByUserId: obj.optString("created_by_user_id"),
            createdByDisplayName: obj.optString("created_by_display_name"),
            decidedByUserId: obj.optNullableString("decided_by_user_id"),
            decidedByDisplayName: obj.optNullableString("decided_by_display_name"),
            decidedAt: obj.optNullableString("decided_at"),
            decisionNote: obj.optNullableString("decision_note"),
            handledExternallyByUserId: obj.optNullableString("handled_externally_by_user_id"),
            handledExternallyByDisplayName: obj.optNullableString("handled_externally_by_display_name"),
            handledExternallyAt: obj.optNullableString("handled_externally_at"),
            handledExternallyNote: obj.optNullableString("handled_externally_note"),
            withdrawnByUserId: obj.optNullableString("withdrawn_by_user_id"),
            withdrawnByDisplayName: obj.optNullableString("withdrawn_by_display_name"),
            withdrawnAt: obj.optNullableString("withdrawn_at"),
            withdrawnNote: obj.optNullableString("withdrawn_note"),
            currentEvidence: currentEvidence,
            currentProjectAccountant: currentProjectAccountant,
            workerAdvance30dSummary: summary30d,
            allowedActions: parseAllowedActions(obj["allowed_actions"] as? [String: Any]),
            lastActivityAt: obj.optString("last_activity_at"),
            createdAt: obj.optString("created_at"),
            updatedAt: obj.optString("updated_at")
        )
    }

    public static func parseRequestListResponse(_ root: [String: Any]) -> SalaryAdvanceRequestListResponse {
        let data = (root["data"] as? [String: Any]) ?? root
        let rawList = data.optObjectArray("requests", "items")
        let requests = rawList.compactMap { item -> SalaryAdvanceRequestSummary? in
            let parsed = parseRequestSummary(item)
            return (parsed.requestId.isEmpty || parsed.projectId.isEmpty || parsed.workerEntryId.isEmpty) ? nil : parsed
        }
        var badgeCount: Int? = nil
        if data["badge_count"] != nil && !(data["badge_count"] is NSNull) {
            badgeCount = max(0, data.optInt("badge_count", defaultValue: 0))
        } else if let badgeObj = data["badge"] as? [String: Any],
                  badgeObj["pm_badge_count"] != nil && !(badgeObj["pm_badge_count"] is NSNull) {
            badgeCount = max(0, badgeObj.optInt("pm_badge_count", defaultValue: 0))
        }
        return SalaryAdvanceRequestListResponse(
            requests: requests,
            hasMore: data.optBool("has_more"),
            nextCursor: data.optNullableString("next_cursor"),
            badgeCount: badgeCount,
            badgeCountCapped: data.optBool("badge_count_capped")
        )
    }

    public static func parseRequestDetail(_ root: [String: Any]) -> SalaryAdvanceRequestDetail {
        let data = (root["data"] as? [String: Any]) ?? root
        let reqObj = (data["request"] as? [String: Any]) ?? data
        let rootSummary30d = parseRolling30dSummary(data["worker_advance_30d_summary"] as? [String: Any])
        let summary = parseRequestSummary(reqObj, fallback30d: rootSummary30d)

        let events = data.optObjectArray("events").enumerated().compactMap { idx, ev -> SalaryAdvanceEventItem? in
            let eid = ev.optString("event_id")
            guard !eid.isEmpty else { return nil }
            let evVer = ev.optInt("evidence_version", defaultValue: 0)
            let revVer = ev.optInt("reviewed_worker_portrait_version", defaultValue: 0)
            let evSize = ev.optInt64("evidence_size_bytes") ?? 0
            return SalaryAdvanceEventItem(
                eventId: eid,
                eventSeq: max(1, ev.optInt("event_seq", defaultValue: idx + 1)),
                eventType: ev.optString("event_type"),
                actorUserId: ev.optString("actor_user_id"),
                actorDisplayName: ev.optString("actor_display_name"),
                actorRole: ev.optString("actor_role", "actor_role_context"),
                fromRequestState: ev.optNullableString("from_request_state"),
                toRequestState: ev.optString("to_request_state"),
                fromEvidenceReviewState: ev.optNullableString("from_evidence_review_state"),
                toEvidenceReviewState: ev.optString("to_evidence_review_state"),
                messageText: ev.optNullableString("message_text"),
                evidenceVersion: evVer > 0 ? evVer : nil,
                evidenceKind: ev.optNullableString("evidence_kind"),
                evidenceMimeType: ev.optNullableString("evidence_mime_type"),
                evidenceSizeBytes: evSize > 0 ? evSize : nil,
                evidenceChecksumSha256: ev.optNullableString("evidence_checksum_sha256"),
                reviewedWorkerPortraitDocumentId: ev.optNullableString("reviewed_worker_portrait_document_id"),
                reviewedWorkerPortraitVersion: revVer > 0 ? revVer : nil,
                createdAt: ev.optString("created_at")
            )
        }.sorted { $0.eventSeq < $1.eventSeq }
        let evidenceVersions = data.optObjectArray("evidence_versions").compactMap { ev -> SalaryAdvanceEvidenceVersionItem? in
            let ver = ev.optInt("evidence_version", "version", defaultValue: 0)
            guard ver > 0 else { return nil }
            return SalaryAdvanceEvidenceVersionItem(
                evidenceVersion: ver,
                evidenceKind: ev.optString("evidence_kind", defaultValue: "CASH_HANDOVER_PHOTO"),
                eventId: ev.optString("event_id", "evidence_id", defaultValue: "ev-\(ver)"),
                mimeType: ev.optString("mime_type", defaultValue: "image/jpeg"),
                sizeBytes: ev.optInt64("size_bytes") ?? 0,
                checksumSha256: ev.optString("checksum_sha256", "sha256"),
                captionNote: ev.optNullableString("caption_note"),
                uploadedByUserId: ev.optString("uploaded_by_user_id"),
                uploadedByDisplayName: ev.optString("uploaded_by_display_name"),
                uploadedAt: ev.optString("uploaded_at")
            )
        }.sorted { $0.evidenceVersion > $1.evidenceVersion }

        return SalaryAdvanceRequestDetail(
            request: summary,
            events: events,
            evidenceVersions: evidenceVersions,
            workerAdvance30dSummary: rootSummary30d ?? summary.workerAdvance30dSummary,
            replayed: data.optBool("replayed")
        )
    }
}

// MARK: - Salary Advance Service (`SalaryAdvanceService`)

public final class SalaryAdvanceService {
    public static let shared = SalaryAdvanceService()

    private let httpClient: BackendHttpClient

    public init(httpClient: BackendHttpClient = .shared) {
        self.httpClient = httpClient
    }

    public func fetchContext() async -> MiniBiApiResult<SalaryAdvanceActorContext> {
        let response = await httpClient.request(
            method: "GET",
            path: "/api/salary-advance/context"
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseContextResponse(root), statusCode: response.statusCode)
    }

    public func listProjectWorkers(
        projectId: String,
        search: String = "",
        cursor: String? = nil,
        pageSize: Int = 30
    ) async -> MiniBiApiResult<SalaryAdvanceProjectWorkersPage> {
        let cleanProject = projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanProject.isEmpty else {
            return .failure(message: "Vui lòng chọn dự án quản lý.")
        }
        let encodedProject = cleanProject.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanProject
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "page_size", value: String(min(100, max(1, pageSize))))
        ]
        let cleanSearch = search.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanSearch.isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: cleanSearch))
        }
        if let cleanCursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanCursor.isEmpty {
            queryItems.append(URLQueryItem(name: "cursor", value: cleanCursor))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/salary-advance/projects/\(encodedProject)/workers",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseProjectWorkersPage(root), statusCode: response.statusCode)
    }

    public func createRequest(
        projectId: String,
        workerEntryId: String,
        requestedAmountRaw: String,
        paymentMethod: String,
        initialContent: String,
        idempotencyKey: String
    ) async -> MiniBiApiResult<String> {
        let payload: [String: Any]
        do {
            payload = try SalaryAdvanceContract.buildCreateRequestPayload(
                projectId: projectId,
                workerEntryId: workerEntryId,
                requestedAmountRaw: requestedAmountRaw,
                paymentMethod: paymentMethod,
                initialContent: initialContent,
                idempotencyKey: idempotencyKey
            )
        } catch {
            return .failure(message: error.localizedDescription)
        }

        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let response = await httpClient.request(
            method: "POST",
            path: "/api/salary-advance/requests",
            jsonBody: payload,
            additionalHeaders: ["Idempotency-Key": cleanIdem]
        )
        guard response.isSuccess else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        let data = (response.jsonObject?["data"] as? [String: Any]) ?? response.jsonObject ?? [:]
        let reqObj = (data["request"] as? [String: Any]) ?? data
        let reqId = reqObj.optString("request_id", defaultValue: "created")
        return .success(reqId, statusCode: response.statusCode)
    }

    public func listRequests(
        queue: String = "PM",
        projectFilter: String = "ALL",
        requestStateFilter: String = "ALL",
        evidenceStateFilter: String = "ALL",
        search: String = "",
        cursor: String? = nil,
        pageSize: Int = 20,
        includeBadge: Bool = true
    ) async -> MiniBiApiResult<SalaryAdvanceRequestListResponse> {
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "queue", value: queue.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()),
            URLQueryItem(name: "project_id", value: projectFilter.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "ALL" : projectFilter.trimmingCharacters(in: .whitespacesAndNewlines)),
            URLQueryItem(name: "request_state", value: requestStateFilter.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "ALL" : requestStateFilter.trimmingCharacters(in: .whitespacesAndNewlines)),
            URLQueryItem(name: "evidence_review_state", value: evidenceStateFilter.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "ALL" : evidenceStateFilter.trimmingCharacters(in: .whitespacesAndNewlines)),
            URLQueryItem(name: "page_size", value: String(min(100, max(1, pageSize)))),
            URLQueryItem(name: "include_badge", value: includeBadge ? "1" : "0")
        ]
        let cleanSearch = search.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanSearch.isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: cleanSearch))
        }
        if let cleanCursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanCursor.isEmpty {
            queryItems.append(URLQueryItem(name: "cursor", value: cleanCursor))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/salary-advance/requests",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseRequestListResponse(root), statusCode: response.statusCode)
    }

    public func getRequestDetail(requestId: String) async -> MiniBiApiResult<SalaryAdvanceRequestDetail> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không xác định được mã yêu cầu ứng lương.")
        }
        let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let response = await httpClient.request(
            method: "GET",
            path: "/api/salary-advance/requests/\(encodedId)"
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseRequestDetail(root), statusCode: response.statusCode)
    }

    public func postMessage(
        requestId: String,
        expectedVersion: Int,
        messageText: String,
        idempotencyKey: String
    ) async -> MiniBiApiResult<SalaryAdvanceRequestDetail> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không xác định được mã yêu cầu ứng lương.")
        }
        let payload: [String: Any]
        do {
            payload = try SalaryAdvanceContract.buildPostMessagePayload(
                expectedVersion: expectedVersion,
                messageText: messageText,
                idempotencyKey: idempotencyKey
            )
        } catch {
            return .failure(message: error.localizedDescription)
        }

        let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let response = await httpClient.request(
            method: "POST",
            path: "/api/salary-advance/requests/\(encodedId)/messages",
            jsonBody: payload,
            additionalHeaders: ["Idempotency-Key": cleanIdem]
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseRequestDetail(root), statusCode: response.statusCode)
    }

    public func submitTransitionAction(
        requestId: String,
        action: String,
        expectedVersion: Int,
        messageText: String?,
        idempotencyKey: String
    ) async -> MiniBiApiResult<SalaryAdvanceRequestDetail> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không xác định được mã yêu cầu ứng lương.")
        }
        let payload: [String: Any]
        do {
            payload = try SalaryAdvanceContract.buildTransitionActionPayload(
                action: action,
                expectedVersion: expectedVersion,
                messageText: messageText,
                idempotencyKey: idempotencyKey
            )
        } catch {
            return .failure(message: error.localizedDescription)
        }

        let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let cleanIdem = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let response = await httpClient.request(
            method: "POST",
            path: "/api/salary-advance/requests/\(encodedId)/actions",
            jsonBody: payload,
            additionalHeaders: ["Idempotency-Key": cleanIdem]
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseRequestDetail(root), statusCode: response.statusCode)
    }

    public func uploadEvidenceBytes(
        requestId: String,
        expectedVersion: Int,
        imageData: Data,
        mimeType: String,
        captionNote: String?,
        reserveIdempotencyKey: String,
        finalizeIdempotencyKey: String
    ) async -> MiniBiApiResult<SalaryAdvanceRequestDetail> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không xác định được mã yêu cầu ứng lương.")
        }
        let sha256HexStr = SalaryAdvanceContract.sha256Hex(imageData)
        let reservePayload: [String: Any]
        do {
            reservePayload = try SalaryAdvanceContract.buildReserveEvidenceUploadPayload(
                expectedVersion: expectedVersion,
                mimeType: mimeType,
                sizeBytes: Int64(imageData.count),
                sha256: sha256HexStr,
                captionNote: captionNote,
                idempotencyKey: reserveIdempotencyKey
            )
        } catch {
            return .failure(message: error.localizedDescription)
        }

        let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let cleanReserveIdem = reserveIdempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let reserveResp = await httpClient.request(
            method: "POST",
            path: "/api/salary-advance/requests/\(encodedId)/evidence/reserve",
            jsonBody: reservePayload,
            additionalHeaders: ["Idempotency-Key": cleanReserveIdem]
        )
        guard reserveResp.isSuccess, let reserveRoot = reserveResp.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: reserveResp.errorCode,
                    httpStatus: reserveResp.statusCode,
                    fallback: reserveResp.errorMessage
                ),
                httpStatus: reserveResp.statusCode,
                errorCode: reserveResp.errorCode
            )
        }

        let reserveData = (reserveRoot["data"] as? [String: Any]) ?? reserveRoot
        let uploadObj = (reserveData["upload"] as? [String: Any]) ?? reserveData
        let uploadId = reserveData.optString("upload_id", "evidence_id").isEmpty
            ? uploadObj.optString("upload_id", "evidence_id")
            : reserveData.optString("upload_id", "evidence_id")
        let putUrl = uploadObj.optString("url", "put_url", "presigned_put_url")

        guard !uploadId.isEmpty && !putUrl.isEmpty else {
            return .failure(message: "Máy chủ không trả về liên kết tải ảnh chứng từ hợp lệ.")
        }

        var extraPutHeaders: [String: String] = [:]
        if let headersObj = (uploadObj["required_headers"] as? [String: Any]) ?? (uploadObj["headers"] as? [String: Any]) {
            for (k, v) in headersObj {
                if let strVal = v as? String, !strVal.isEmpty, k.lowercased() != "content-type" {
                    extraPutHeaders[k] = strVal
                }
            }
        }

        let putResp = await httpClient.putPresignedUrl(
            uploadUrl: putUrl,
            fileData: imageData,
            contentType: mimeType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            additionalHeaders: extraPutHeaders
        )
        guard putResp.isSuccess else {
            return .failure(
                message: putResp.errorMessage ?? "Không thể tải ảnh chứng từ lên kho lưu trữ (HTTP \(putResp.statusCode)).",
                httpStatus: putResp.statusCode
            )
        }

        let finalizePayload: [String: Any]
        do {
            finalizePayload = try SalaryAdvanceContract.buildFinalizeEvidenceUploadPayload(
                expectedVersion: expectedVersion,
                idempotencyKey: finalizeIdempotencyKey
            )
        } catch {
            return .failure(message: error.localizedDescription)
        }

        let encodedUploadId = uploadId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? uploadId
        let cleanFinalizeIdem = finalizeIdempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalizeResp = await httpClient.request(
            method: "POST",
            path: "/api/salary-advance/requests/\(encodedId)/evidence/\(encodedUploadId)/finalize",
            jsonBody: finalizePayload,
            additionalHeaders: ["Idempotency-Key": cleanFinalizeIdem]
        )
        guard finalizeResp.isSuccess, let finalizeRoot = finalizeResp.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: finalizeResp.errorCode,
                    httpStatus: finalizeResp.statusCode,
                    fallback: finalizeResp.errorMessage
                ),
                httpStatus: finalizeResp.statusCode,
                errorCode: finalizeResp.errorCode
            )
        }
        httpClient.invalidateBinaryCache()
        return .success(SalaryAdvanceContract.parseRequestDetail(finalizeRoot), statusCode: finalizeResp.statusCode)
    }

    public func fetchWorkerHistoryForRequest(
        requestId: String,
        periodMode: String = "THIS_MONTH",
        month: String? = nil,
        fromDate: String? = nil,
        toDate: String? = nil,
        cursor: String? = nil,
        pageSize: Int = 20
    ) async -> MiniBiApiResult<SalaryAdvanceWorkerHistoryResponse> {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không xác định được mã yêu cầu ứng lương.")
        }
        let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let cleanMode = periodMode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "period_mode", value: cleanMode),
            URLQueryItem(name: "page_size", value: String(min(100, max(1, pageSize))))
        ]
        if cleanMode == "MONTH", let m = month?.trimmingCharacters(in: .whitespacesAndNewlines), !m.isEmpty {
            queryItems.append(URLQueryItem(name: "month", value: m))
        }
        if cleanMode == "CUSTOM_RANGE" {
            let fIso = DirectEntryContract.uiDateToIsoDate(fromDate ?? "")
            let tIso = DirectEntryContract.uiDateToIsoDate(toDate ?? "")
            if !fIso.isEmpty { queryItems.append(URLQueryItem(name: "from_date", value: fIso)) }
            if !tIso.isEmpty { queryItems.append(URLQueryItem(name: "to_date", value: tIso)) }
        }
        if let cleanCursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanCursor.isEmpty {
            queryItems.append(URLQueryItem(name: "cursor", value: cleanCursor))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/salary-advance/requests/\(encodedId)/worker-history",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseWorkerHistoryResponse(root), statusCode: response.statusCode)
    }

    public func fetchWorkerHistoryForPm(
        projectId: String,
        workerEntryId: String,
        periodMode: String = "THIS_MONTH",
        month: String? = nil,
        fromDate: String? = nil,
        toDate: String? = nil,
        cursor: String? = nil,
        pageSize: Int = 20
    ) async -> MiniBiApiResult<SalaryAdvanceWorkerHistoryResponse> {
        let cleanProject = projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanWorker = workerEntryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanProject.isEmpty && !cleanWorker.isEmpty else {
            return .failure(message: "Không xác định được dự án hoặc người lao động.")
        }
        let encodedProject = cleanProject.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanProject
        let encodedWorker = cleanWorker.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanWorker
        let cleanMode = periodMode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "period_mode", value: cleanMode),
            URLQueryItem(name: "page_size", value: String(min(100, max(1, pageSize))))
        ]
        if cleanMode == "MONTH", let m = month?.trimmingCharacters(in: .whitespacesAndNewlines), !m.isEmpty {
            queryItems.append(URLQueryItem(name: "month", value: m))
        }
        if cleanMode == "CUSTOM_RANGE" {
            let fIso = DirectEntryContract.uiDateToIsoDate(fromDate ?? "")
            let tIso = DirectEntryContract.uiDateToIsoDate(toDate ?? "")
            if !fIso.isEmpty { queryItems.append(URLQueryItem(name: "from_date", value: fIso)) }
            if !tIso.isEmpty { queryItems.append(URLQueryItem(name: "to_date", value: tIso)) }
        }
        if let cleanCursor = cursor?.trimmingCharacters(in: .whitespacesAndNewlines), !cleanCursor.isEmpty {
            queryItems.append(URLQueryItem(name: "cursor", value: cleanCursor))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/salary-advance/projects/\(encodedProject)/workers/\(encodedWorker)/worker-history",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: SalaryAdvanceContract.describeSalaryAdvanceError(
                    code: response.errorCode,
                    httpStatus: response.statusCode,
                    fallback: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(SalaryAdvanceContract.parseWorkerHistoryResponse(root), statusCode: response.statusCode)
    }
}
