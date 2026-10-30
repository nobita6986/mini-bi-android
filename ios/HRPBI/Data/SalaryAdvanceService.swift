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

// MARK: - Salary Advance Contract (`SalaryAdvanceContract`)

public enum SalaryAdvanceContract {
    public static let paymentMethods: [String] = ["CASH", "BANK_TRANSFER"]

    public static let historyPeriodModes: [(key: String, label: String)] = [
        ("THIS_MONTH", "Tháng này"),
        ("LAST_30_DAYS", "30 ngày gần nhất"),
        ("MONTH", "Chọn tháng"),
        ("ALL", "Toàn bộ"),
        ("CUSTOM_RANGE", "Khoảng ngày")
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
            return "Chờ xử lý"
        case "APPROVED":
            return "Đã duyệt"
        case "REJECTED":
            return "Từ chối"
        default:
            return state
        }
    }

    public static func evidenceReviewStateShortLabel(_ state: String) -> String {
        switch state.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "NOT_UPLOADED":
            return "Chưa tải chứng từ"
        case "UPLOADED_PENDING_REVIEW":
            return "Đã tải chứng từ"
        case "REVIEWED":
            return "Đã hậu kiểm"
        default:
            return state
        }
    }

    public static func formatWorkDateVi(_ rawDate: String) -> String {
        let trimmed = rawDate.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "—" }
        let ui = DirectEntryContract.isoDateToUiDate(trimmed)
        return ui.isEmpty ? trimmed : ui
    }

    public static func formatWorkerRecruiterLabel(_ worker: SalaryAdvanceWorkerOption) -> String {
        let rec = worker.recruiterDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !rec.isEmpty {
            return rec
        }
        return worker.providerType.lowercased() == "vendor" ? "Vendor" : "HRP"
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
            return "0 ₫"
        }
        var reversedChars: [Character] = []
        for (idx, ch) in clean.reversed().enumerated() {
            if idx > 0 && idx % 3 == 0 {
                reversedChars.append(".")
            }
            reversedChars.append(ch)
        }
        return "\(String(reversedChars.reversed())) ₫"
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
