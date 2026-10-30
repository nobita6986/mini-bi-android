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
}

public struct SalaryAdvanceWorkerPortraitMeta: Equatable, Sendable {
    public let documentId: String
    public let version: Int
    public let mimeType: String
    public let sizeBytes: Int64
    public let checksumSha256: String
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
    public let allowedActions: SalaryAdvanceAllowedActions
    public let lastActivityAt: String
    public let createdAt: String
    public let updatedAt: String
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

    public static func buildWorkerPortraitPreviewPath(requestId: String, expectedDocumentId: String) -> String {
        let encodedReq = requestId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? requestId
        let encodedDoc = expectedDocumentId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? expectedDocumentId
        return "/api/salary-advance/requests/\(encodedReq)/worker-portrait?expected_document_id=\(encodedDoc)"
    }

    public static func buildEvidencePhotoPreviewPath(requestId: String, evidenceVersion: Int) -> String {
        let encodedReq = requestId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? requestId
        return "/api/salary-advance/requests/\(encodedReq)/evidence/\(evidenceVersion)/preview"
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
            canRequestInfo: obj.optBool("can_request_info"),
            canProvideInfo: obj.optBool("can_provide_info"),
            canApprove: obj.optBool("can_approve"),
            canReject: obj.optBool("can_reject"),
            canWithdraw: obj.optBool("can_withdraw"),
            canUploadCashEvidence: obj.optBool("can_upload_cash_evidence"),
            canConfirmCashEvidence: obj.optBool("can_confirm_cash_evidence"),
            canRequestEvidenceReupload: obj.optBool("can_request_evidence_reupload")
        )
    }

    public static func parseRequestSummary(_ obj: [String: Any]) -> SalaryAdvanceRequestSummary {
        let paySnap: SalaryAdvancePaymentDestinationSnapshot? = obj.optObject("payment_destination_snapshot").map { p in
            SalaryAdvancePaymentDestinationSnapshot(
                bankBin: p.optNullableString("bank_bin"),
                bankCode: p.optNullableString("bank_code"),
                bankShortName: p.optNullableString("bank_short_name"),
                bankAccountNumber: p.optNullableString("bank_account_number"),
                bankAccountHolderName: p.optNullableString("bank_account_holder_name")
            )
        }
        let portraitMeta: SalaryAdvanceWorkerPortraitMeta? = obj.optObject("worker_portrait").map { p in
            SalaryAdvanceWorkerPortraitMeta(
                available: p.optBool("available"),
                documentId: p.optNullableString("document_id"),
                version: p["version"] != nil && !(p["version"] is NSNull) ? p.optInt("version") : nil,
                mimeType: p.optNullableString("mime_type"),
                sizeBytes: p["size_bytes"] != nil && !(p["size_bytes"] is NSNull) ? Int64(p.optInt("size_bytes")) : nil
            )
        }
        let currentEvidence: SalaryAdvanceCurrentEvidenceMeta? = obj.optObject("current_evidence").map { e in
            SalaryAdvanceCurrentEvidenceMeta(
                evidenceId: e.optString("evidence_id"),
                version: max(1, e.optInt("version", defaultValue: 1)),
                uploadStatus: e.optString("upload_status", defaultValue: "READY"),
                mimeType: e.optNullableString("mime_type"),
                sizeBytes: Int64(e.optInt("size_bytes", defaultValue: 0)),
                captionNote: e.optNullableString("caption_note"),
                uploadedByUserId: e.optString("uploaded_by_user_id"),
                uploadedByDisplayName: e.optNullableString("uploaded_by_display_name"),
                uploadedAt: e.optNullableString("uploaded_at")
            )
        }
        let sumObj = obj.optObject("worker_advance_30d_summary")
        let summary30d = SalaryAdvanceWorkerAdvance30dSummary(
            count30d: max(0, sumObj?.optInt("count_30d", defaultValue: 0) ?? 0),
            totalAmountVnd30d: sumObj?.optString("total_amount_vnd_30d", defaultValue: "0") ?? "0"
        )
        let allowed = parseAllowedActions(obj.optObject("allowed_actions"))
        let accountants = obj.optObjectArray("project_accountants").compactMap { acc -> SalaryAdvanceProjectAccountantActor? in
            let uid = acc.optString("user_id")
            guard !uid.isEmpty else { return nil }
            return SalaryAdvanceProjectAccountantActor(
                userId: uid,
                username: acc.optString("username"),
                displayName: acc.optNullableString("display_name")
            )
        }

        return SalaryAdvanceRequestSummary(
            requestId: obj.optString("request_id"),
            version: max(1, obj.optInt("version", defaultValue: 1)),
            requestState: obj.optString("request_state", defaultValue: "OPEN"),
            evidenceReviewState: obj.optString("evidence_review_state", defaultValue: "NOT_UPLOADED"),
            projectId: obj.optString("project_id"),
            projectCode: obj.optNullableString("project_code"),
            projectDisplayName: obj.optString("project_display_name"),
            workerEntryId: obj.optString("worker_entry_id"),
            workerEmployeeCode: obj.optString("worker_employee_code"),
            workerDisplayName: obj.optString("worker_display_name"),
            workerStartDate: obj.optNullableString("worker_start_date"),
            workerRecruiterUserId: obj.optNullableString("worker_recruiter_user_id"),
            workerRecruiterUsername: obj.optNullableString("worker_recruiter_username"),
            workerRecruiterDisplayName: obj.optNullableString("worker_recruiter_display_name"),
            requestedAmountVnd: obj.optString("requested_amount_vnd", defaultValue: "0"),
            paymentMethod: obj.optString("payment_method", defaultValue: "CASH"),
            paymentDestinationSnapshot: paySnap,
            initialContent: obj.optString("initial_content"),
            createdByUserId: obj.optString("created_by_user_id"),
            createdByUsername: obj.optNullableString("created_by_username"),
            createdByDisplayName: obj.optNullableString("created_by_display_name"),
            decidedByUserId: obj.optNullableString("decided_by_user_id"),
            decidedByUsername: obj.optNullableString("decided_by_username"),
            decidedByDisplayName: obj.optNullableString("decided_by_display_name"),
            decidedAt: obj.optNullableString("decided_at"),
            effectiveAt: obj.optNullableString("effective_at"),
            effectiveDate: obj.optNullableString("effective_date"),
            withdrawnAt: obj.optNullableString("withdrawn_at"),
            evidenceConfirmedByUserId: obj.optNullableString("evidence_confirmed_by_user_id"),
            evidenceConfirmedByUsername: obj.optNullableString("evidence_confirmed_by_username"),
            evidenceConfirmedByDisplayName: obj.optNullableString("evidence_confirmed_by_display_name"),
            evidenceConfirmedAt: obj.optNullableString("evidence_confirmed_at"),
            lastActivityAt: obj.optString("last_activity_at"),
            createdAt: obj.optString("created_at"),
            updatedAt: obj.optString("updated_at"),
            workerPortrait: portraitMeta,
            currentEvidence: currentEvidence,
            workerAdvance30dSummary: summary30d,
            allowedActions: allowed,
            projectAccountants: accountants
        )
    }

    public static func parseRequestListResponse(_ root: [String: Any]) -> SalaryAdvanceRequestListResponse {
        let data = (root["data"] as? [String: Any]) ?? root
        let items = data.optObjectArray("items").compactMap { item -> SalaryAdvanceRequestSummary? in
            let parsed = parseRequestSummary(item)
            return parsed.requestId.isEmpty ? nil : parsed
        }
        var pmCount: Int? = nil
        var accCount: Int? = nil
        if let badge = data.optObject("badge") {
            if badge["pm_badge_count"] != nil && !(badge["pm_badge_count"] is NSNull) {
                pmCount = max(0, badge.optInt("pm_badge_count"))
            }
            if badge["accountant_badge_count"] != nil && !(badge["accountant_badge_count"] is NSNull) {
                accCount = max(0, badge.optInt("accountant_badge_count"))
            }
        }
        return SalaryAdvanceRequestListResponse(
            queue: data.optString("queue", defaultValue: "PM"),
            nextCursor: data.optNullableString("next_cursor"),
            hasMore: data.optBool("has_more"),
            items: items,
            pmBadgeCount: pmCount,
            accountantBadgeCount: accCount
        )
    }

    public static func parseRequestDetail(_ root: [String: Any]) -> SalaryAdvanceRequestDetail {
        let data = (root["data"] as? [String: Any]) ?? root
        let reqObj = data.optObject("request") ?? data
        let summary = parseRequestSummary(reqObj)
        let events = data.optObjectArray("events").compactMap { ev -> SalaryAdvanceEventItem? in
            let eid = ev.optString("event_id")
            guard !eid.isEmpty else { return nil }
            return SalaryAdvanceEventItem(
                eventId: eid,
                requestId: ev.optString("request_id", defaultValue: summary.requestId),
                eventSeq: max(1, ev.optInt("event_seq", defaultValue: 1)),
                eventType: ev.optString("event_type"),
                actorUserId: ev.optString("actor_user_id"),
                actorUsername: ev.optNullableString("actor_username"),
                actorDisplayName: ev.optNullableString("actor_display_name"),
                actorRoleContext: ev.optString("actor_role_context", defaultValue: "SYSTEM"),
                fromRequestState: ev.optNullableString("from_request_state"),
                toRequestState: ev.optNullableString("to_request_state"),
                fromEvidenceReviewState: ev.optNullableString("from_evidence_review_state"),
                toEvidenceReviewState: ev.optNullableString("to_evidence_review_state"),
                messageText: ev.optNullableString("message_text"),
                metadata: ev.optObject("metadata"),
                createdAt: ev.optString("created_at")
            )
        }
        let evidenceVersions = data.optObjectArray("evidence_versions").compactMap { ev -> SalaryAdvanceEvidenceVersionItem? in
            let evid = ev.optString("evidence_id")
            guard !evid.isEmpty else { return nil }
            return SalaryAdvanceEvidenceVersionItem(
                evidenceId: evid,
                requestId: ev.optString("request_id", defaultValue: summary.requestId),
                version: max(1, ev.optInt("version", defaultValue: 1)),
                uploadStatus: ev.optString("upload_status", defaultValue: "READY"),
                mimeType: ev.optString("mime_type", defaultValue: "image/jpeg"),
                sizeBytes: Int64(ev.optInt("size_bytes", defaultValue: 0)),
                sha256: ev.optString("sha256"),
                captionNote: ev.optNullableString("caption_note"),
                uploadedByUserId: ev.optString("uploaded_by_user_id"),
                uploadedByUsername: ev.optNullableString("uploaded_by_username"),
                uploadedByDisplayName: ev.optNullableString("uploaded_by_display_name"),
                uploadedAt: ev.optNullableString("uploaded_at"),
                createdAt: ev.optString("created_at")
            )
        }
        return SalaryAdvanceRequestDetail(
            request: summary,
            events: events,
            evidenceVersions: evidenceVersions
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
        let uploadObj = reserveData.optObject("upload") ?? reserveData
        let uploadId = uploadObj.optString("upload_id").isEmpty
            ? uploadObj.optString("evidence_id")
            : uploadObj.optString("upload_id")
        let putUrl = uploadObj.optString("put_url").isEmpty
            ? uploadObj.optString("presigned_put_url")
            : uploadObj.optString("put_url")

        guard !uploadId.isEmpty && !putUrl.isEmpty else {
            return .failure(message: "Máy chủ không trả về liên kết tải ảnh chứng từ hợp lệ.")
        }

        var extraPutHeaders: [String: String] = [:]
        if let headersObj = uploadObj.optObject("required_headers") ?? uploadObj.optObject("headers") {
            for (k, v) in headersObj {
                if let strVal = v as? String, !strVal.isEmpty, k.lowercased() != "content-type" {
                    extraPutHeaders[k] = strVal
                }
            }
        }

        let putResp = await httpClient.putPresignedUrl(
            presignedUrl: putUrl,
            data: imageData,
            contentType: mimeType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            extraHeaders: extraPutHeaders
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
