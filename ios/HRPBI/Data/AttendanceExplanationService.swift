import Foundation

// MARK: - Attendance Explanation ("Giải trình chấm công") & Approval ("Xét duyệt giải trình") Models / Contract / Service
// Mirrors Android's `data/AttendanceExplanationModelsAndService.kt` and Backend's `src/lib/attendance/explanations-contract.ts` 100%

public struct AttendanceExplanationViewerProjection: Equatable, Sendable {
    public let appUserId: String
    public let canSubmitExplanation: Bool
    public let canApproveExplanations: Bool
    public let isSharedNonEmployee: Bool
    public let isFullAdmin: Bool
    public let isBod: Bool

    public init(
        appUserId: String,
        canSubmitExplanation: Bool = true,
        canApproveExplanations: Bool = false,
        isSharedNonEmployee: Bool = false,
        isFullAdmin: Bool = false,
        isBod: Bool = false
    ) {
        self.appUserId = appUserId
        self.canSubmitExplanation = canSubmitExplanation
        self.canApproveExplanations = canApproveExplanations
        self.isSharedNonEmployee = isSharedNonEmployee
        self.isFullAdmin = isFullAdmin
        self.isBod = isBod
    }
}

public struct AttendanceExplanationAccountSummaryProjection: Equatable, Sendable {
    public let appUserId: String
    public let displayName: String?
    public let loginId: String?
    public let personnelCode: String?
    public let recruiterId: String?
    public let accountEnabled: Bool

    public init(
        appUserId: String,
        displayName: String? = nil,
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

    public func resolvedDisplayName() -> String {
        if let dn = displayName?.trimmingCharacters(in: .whitespacesAndNewlines), !dn.isEmpty {
            return dn
        }
        if let lid = loginId?.trimmingCharacters(in: .whitespacesAndNewlines), !lid.isEmpty {
            return lid
        }
        if let pc = personnelCode?.trimmingCharacters(in: .whitespacesAndNewlines), !pc.isEmpty {
            return pc
        }
        return String(appUserId.prefix(8))
    }
}

public struct AttendanceExplanationRoutingProjection: Equatable, Sendable {
    public let routeKind: String
    public let unitKind: String
    public let primaryApprover: AttendanceExplanationAccountSummaryProjection?
    public let teamId: String?
    public let teamCode: String?
    public let teamName: String?
    public let fallbackReason: String?
    public let profileConfigured: Bool

    public init(
        routeKind: String = "TEAM_LEADER",
        unitKind: String = "RECRUITMENT",
        primaryApprover: AttendanceExplanationAccountSummaryProjection? = nil,
        teamId: String? = nil,
        teamCode: String? = nil,
        teamName: String? = nil,
        fallbackReason: String? = nil,
        profileConfigured: Bool = true
    ) {
        self.routeKind = routeKind
        self.unitKind = unitKind
        self.primaryApprover = primaryApprover
        self.teamId = teamId
        self.teamCode = teamCode
        self.teamName = teamName
        self.fallbackReason = fallbackReason
        self.profileConfigured = profileConfigured
    }
}

public struct AttendanceSlotCheckinItemProjection: Identifiable, Equatable, Sendable {
    public var id: String { checkinId }
    public let checkinId: String
    public let sequenceInDay: Int
    public let capturedAt: String
    public let capturedAtLocal: String
    public let timeLocal: String
    public let timeVerified: Bool
    public let assignedSlotCode: String?
    public let deltaMinutes: Int?
    public let overGraceMinutes: Int
    public let deviationStatus: String
    public let distanceTargetKind: String
    public let distanceStatus: String
    public let distanceMeters: Int?

    public init(
        checkinId: String,
        sequenceInDay: Int = 1,
        capturedAt: String = "",
        capturedAtLocal: String = "",
        timeLocal: String = "",
        timeVerified: Bool = true,
        assignedSlotCode: String? = nil,
        deltaMinutes: Int? = nil,
        overGraceMinutes: Int = 0,
        deviationStatus: String = "ON_TIME",
        distanceTargetKind: String = "NONE",
        distanceStatus: String = "UNAVAILABLE",
        distanceMeters: Int? = nil
    ) {
        self.checkinId = checkinId
        self.sequenceInDay = sequenceInDay
        self.capturedAt = capturedAt
        self.capturedAtLocal = capturedAtLocal
        self.timeLocal = timeLocal
        self.timeVerified = timeVerified
        self.assignedSlotCode = assignedSlotCode
        self.deltaMinutes = deltaMinutes
        self.overGraceMinutes = overGraceMinutes
        self.deviationStatus = deviationStatus
        self.distanceTargetKind = distanceTargetKind
        self.distanceStatus = distanceStatus
        self.distanceMeters = distanceMeters
    }
}

public struct AttendanceDaySlotExplanationSummaryProjection: Equatable, Sendable {
    public let explanationId: String
    public let status: String
    public let originalSlotStatus: String
    public let reason: String
    public let submittedAt: String
    public let submittedAtLocal: String
    public let decidedAt: String?
    public let decidedAtLocal: String?
    public let decidedByDisplayName: String?
    public let decisionNote: String
    public let rowVersion: Int

    public init(
        explanationId: String,
        status: String = "PENDING",
        originalSlotStatus: String = "MISSING",
        reason: String = "",
        submittedAt: String = "",
        submittedAtLocal: String = "",
        decidedAt: String? = nil,
        decidedAtLocal: String? = nil,
        decidedByDisplayName: String? = nil,
        decisionNote: String = "",
        rowVersion: Int = 1
    ) {
        self.explanationId = explanationId
        self.status = status
        self.originalSlotStatus = originalSlotStatus
        self.reason = reason
        self.submittedAt = submittedAt
        self.submittedAtLocal = submittedAtLocal
        self.decidedAt = decidedAt
        self.decidedAtLocal = decidedAtLocal
        self.decidedByDisplayName = decidedByDisplayName
        self.decisionNote = decisionNote
        self.rowVersion = rowVersion
    }
}

public struct AttendanceExplanationDaySlotEvaluationProjection: Identifiable, Equatable, Sendable {
    public var id: String { slotCode }
    public let slotCode: String
    public let slotIndex: Int
    public let slotLabel: String
    public let sessionCode: String
    public let targetTime: String
    public let graceBeforeMinutes: Int
    public let graceAfterMinutes: Int
    public let isLeaveCovered: Bool
    public let originalSlotStatus: String
    public let slotStatus: String
    public let eligibleForExplanation: Bool
    public let checkins: [AttendanceSlotCheckinItemProjection]
    public let explanation: AttendanceDaySlotExplanationSummaryProjection?

    public init(
        slotCode: String,
        slotIndex: Int = 1,
        slotLabel: String = "",
        sessionCode: String = "AM",
        targetTime: String = "00:00",
        graceBeforeMinutes: Int = 15,
        graceAfterMinutes: Int = 15,
        isLeaveCovered: Bool = false,
        originalSlotStatus: String = "MISSING",
        slotStatus: String = "MISSING",
        eligibleForExplanation: Bool = false,
        checkins: [AttendanceSlotCheckinItemProjection] = [],
        explanation: AttendanceDaySlotExplanationSummaryProjection? = nil
    ) {
        self.slotCode = slotCode
        self.slotIndex = slotIndex
        self.slotLabel = slotLabel
        self.sessionCode = sessionCode
        self.targetTime = targetTime
        self.graceBeforeMinutes = graceBeforeMinutes
        self.graceAfterMinutes = graceAfterMinutes
        self.isLeaveCovered = isLeaveCovered
        self.originalSlotStatus = originalSlotStatus
        self.slotStatus = slotStatus
        self.eligibleForExplanation = eligibleForExplanation
        self.checkins = checkins
        self.explanation = explanation
    }
}

public struct AttendanceExplanationDayEvaluationProjection: Equatable, Sendable {
    public let businessDate: String
    public let policyProfileIdUsed: String
    public let policyProfileCodeUsed: String
    public let policyProfileNameUsed: String
    public let policyIsDefaultUsed: Bool
    public let policyVersionUsed: Int
    public let slots: [AttendanceExplanationDaySlotEvaluationProjection]

    public init(
        businessDate: String,
        policyProfileIdUsed: String = AttendanceExplanationContract.defaultSlotPolicyProfileId,
        policyProfileCodeUsed: String = AttendanceExplanationContract.defaultSlotPolicyProfileCode,
        policyProfileNameUsed: String = AttendanceExplanationContract.defaultSlotPolicyProfileName,
        policyIsDefaultUsed: Bool = true,
        policyVersionUsed: Int = 1,
        slots: [AttendanceExplanationDaySlotEvaluationProjection] = []
    ) {
        self.businessDate = businessDate
        self.policyProfileIdUsed = policyProfileIdUsed
        self.policyProfileCodeUsed = policyProfileCodeUsed
        self.policyProfileNameUsed = policyProfileNameUsed
        self.policyIsDefaultUsed = policyIsDefaultUsed
        self.policyVersionUsed = policyVersionUsed
        self.slots = slots
    }

    public func resolvedProfileDisplayName() -> String {
        let name = policyProfileNameUsed.trimmingCharacters(in: .whitespacesAndNewlines)
        if !name.isEmpty { return name }
        let code = policyProfileCodeUsed.trimmingCharacters(in: .whitespacesAndNewlines)
        if !code.isEmpty { return code }
        return AttendanceExplanationContract.defaultSlotPolicyProfileName
    }

    public func resolvedProfileVersionLabel() -> String {
        "\(resolvedProfileDisplayName()) (v\(policyVersionUsed))"
    }

    public func resolvedProfileBadgeLabel() -> String {
        "Khung giờ: \(resolvedProfileDisplayName()) v\(policyVersionUsed) (\(slots.count) mốc)"
    }

    public func resolvedSlotScheduleSummary() -> String {
        if slots.isEmpty { return "Chưa có mốc cấu hình" }
        return slots.map { slot in
            let label = slot.slotLabel.trimmingCharacters(in: .whitespacesAndNewlines)
            let displayLabel = label.isEmpty ? slot.slotCode : label
            return "\(displayLabel): \(slot.targetTime)"
        }.joined(separator: " • ")
    }
}

public struct AttendanceExplanationAuditEventProjection: Identifiable, Equatable, Sendable {
    public var id: String { eventId }
    public let eventId: String
    public let eventType: String
    public let actorAppUserId: String?
    public let actorDisplayName: String?
    public let actorRoleContext: String
    public let fromStatus: String?
    public let toStatus: String
    public let note: String
    public let createdAt: String
    public let createdAtLocal: String

    public init(
        eventId: String,
        eventType: String,
        actorAppUserId: String? = nil,
        actorDisplayName: String? = nil,
        actorRoleContext: String = "",
        fromStatus: String? = nil,
        toStatus: String = "",
        note: String = "",
        createdAt: String = "",
        createdAtLocal: String = ""
    ) {
        self.eventId = eventId
        self.eventType = eventType
        self.actorAppUserId = actorAppUserId
        self.actorDisplayName = actorDisplayName
        self.actorRoleContext = actorRoleContext
        self.fromStatus = fromStatus
        self.toStatus = toStatus
        self.note = note
        self.createdAt = createdAt
        self.createdAtLocal = createdAtLocal
    }
}

public struct AttendanceExplanationRoutedTeamProjection: Equatable, Sendable {
    public let teamId: String?
    public let teamCode: String?
    public let teamName: String?

    public init(
        teamId: String? = nil,
        teamCode: String? = nil,
        teamName: String? = nil
    ) {
        self.teamId = teamId
        self.teamCode = teamCode
        self.teamName = teamName
    }
}

public struct AttendanceExplanationItemProjection: Identifiable, Equatable, Sendable {
    public var id: String { explanationId }
    public let explanationId: String
    public let appUserId: String
    public let requester: AttendanceExplanationAccountSummaryProjection
    public let businessDate: String
    public let slotCode: String
    public let slotIndex: Int
    public let slotLabel: String
    public let sessionCode: String
    public let targetTime: String
    public let graceBeforeMinutes: Int
    public let graceAfterMinutes: Int
    public let policyProfileIdUsed: String
    public let policyProfileCodeUsed: String
    public let policyProfileNameUsed: String
    public let policyVersionUsed: Int
    public let originalSlotStatus: String
    public let effectiveSlotStatus: String
    public let status: String
    public let reason: String
    public let routeKind: String
    public let unitKindSnapshot: String
    public let routedApprover: AttendanceExplanationAccountSummaryProjection?
    public let routedTeam: AttendanceExplanationRoutedTeamProjection
    public let fallbackReason: String?
    public let submittedAt: String
    public let submittedAtLocal: String
    public let decidedAt: String?
    public let decidedAtLocal: String?
    public let decidedBy: AttendanceExplanationAccountSummaryProjection?
    public let decisionNote: String
    public let rowVersion: Int
    public let canDecide: Bool
    public let actualCheckins: [AttendanceSlotCheckinItemProjection]
    public let events: [AttendanceExplanationAuditEventProjection]

    public init(
        explanationId: String,
        appUserId: String,
        requester: AttendanceExplanationAccountSummaryProjection,
        businessDate: String,
        slotCode: String,
        slotIndex: Int = 1,
        slotLabel: String = "",
        sessionCode: String = "AM",
        targetTime: String = "00:00",
        graceBeforeMinutes: Int = 15,
        graceAfterMinutes: Int = 15,
        policyProfileIdUsed: String = AttendanceExplanationContract.defaultSlotPolicyProfileId,
        policyProfileCodeUsed: String = AttendanceExplanationContract.defaultSlotPolicyProfileCode,
        policyProfileNameUsed: String = AttendanceExplanationContract.defaultSlotPolicyProfileName,
        policyVersionUsed: Int = 1,
        originalSlotStatus: String = "MISSING",
        effectiveSlotStatus: String = "",
        status: String = "PENDING",
        reason: String = "",
        routeKind: String = "TEAM_LEADER",
        unitKindSnapshot: String = "RECRUITMENT",
        routedApprover: AttendanceExplanationAccountSummaryProjection? = nil,
        routedTeam: AttendanceExplanationRoutedTeamProjection = AttendanceExplanationRoutedTeamProjection(),
        fallbackReason: String? = nil,
        submittedAt: String = "",
        submittedAtLocal: String = "",
        decidedAt: String? = nil,
        decidedAtLocal: String? = nil,
        decidedBy: AttendanceExplanationAccountSummaryProjection? = nil,
        decisionNote: String = "",
        rowVersion: Int = 1,
        canDecide: Bool = false,
        actualCheckins: [AttendanceSlotCheckinItemProjection] = [],
        events: [AttendanceExplanationAuditEventProjection] = []
    ) {
        self.explanationId = explanationId
        self.appUserId = appUserId
        self.requester = requester
        self.businessDate = businessDate
        self.slotCode = slotCode
        self.slotIndex = slotIndex
        self.slotLabel = slotLabel
        self.sessionCode = sessionCode
        self.targetTime = targetTime
        self.graceBeforeMinutes = graceBeforeMinutes
        self.graceAfterMinutes = graceAfterMinutes
        self.policyProfileIdUsed = policyProfileIdUsed
        self.policyProfileCodeUsed = policyProfileCodeUsed
        self.policyProfileNameUsed = policyProfileNameUsed
        self.policyVersionUsed = policyVersionUsed
        self.originalSlotStatus = originalSlotStatus
        self.effectiveSlotStatus = effectiveSlotStatus
        self.status = status
        self.reason = reason
        self.routeKind = routeKind
        self.unitKindSnapshot = unitKindSnapshot
        self.routedApprover = routedApprover
        self.routedTeam = routedTeam
        self.fallbackReason = fallbackReason
        self.submittedAt = submittedAt
        self.submittedAtLocal = submittedAtLocal
        self.decidedAt = decidedAt
        self.decidedAtLocal = decidedAtLocal
        self.decidedBy = decidedBy
        self.decisionNote = decisionNote
        self.rowVersion = rowVersion
        self.canDecide = canDecide
        self.actualCheckins = actualCheckins
        self.events = events
    }

    public func resolvedProfileDisplayName() -> String {
        let name = policyProfileNameUsed.trimmingCharacters(in: .whitespacesAndNewlines)
        if !name.isEmpty { return name }
        let code = policyProfileCodeUsed.trimmingCharacters(in: .whitespacesAndNewlines)
        if !code.isEmpty { return code }
        return AttendanceExplanationContract.defaultSlotPolicyProfileName
    }

    public func resolvedProfileVersionLabel() -> String {
        "\(resolvedProfileDisplayName()) (v\(policyVersionUsed))"
    }
}

public struct AttendanceExplanationPaginationProjection: Equatable, Sendable {
    public let limit: Int
    public let offset: Int
    public let totalRows: Int

    public init(limit: Int = 50, offset: Int = 0, totalRows: Int = 0) {
        self.limit = limit
        self.offset = offset
        self.totalRows = totalRows
    }
}

public struct AttendanceMyExplanationsOverviewProjection: Equatable, Sendable {
    public let viewer: AttendanceExplanationViewerProjection
    public let routing: AttendanceExplanationRoutingProjection
    public let todayLocal: String
    public let selectedDate: String
    public let dayEvaluation: AttendanceExplanationDayEvaluationProjection
    public let pagination: AttendanceExplanationPaginationProjection
    public let items: [AttendanceExplanationItemProjection]

    public init(
        viewer: AttendanceExplanationViewerProjection,
        routing: AttendanceExplanationRoutingProjection,
        todayLocal: String,
        selectedDate: String,
        dayEvaluation: AttendanceExplanationDayEvaluationProjection,
        pagination: AttendanceExplanationPaginationProjection,
        items: [AttendanceExplanationItemProjection]
    ) {
        self.viewer = viewer
        self.routing = routing
        self.todayLocal = todayLocal
        self.selectedDate = selectedDate
        self.dayEvaluation = dayEvaluation
        self.pagination = pagination
        self.items = items
    }
}

public struct AttendanceExplanationApprovalInboxViewerProjection: Equatable, Sendable {
    public let appUserId: String
    public let canApproveExplanations: Bool
    public let isFullAdmin: Bool
    public let isBod: Bool

    public init(
        appUserId: String,
        canApproveExplanations: Bool = false,
        isFullAdmin: Bool = false,
        isBod: Bool = false
    ) {
        self.appUserId = appUserId
        self.canApproveExplanations = canApproveExplanations
        self.isFullAdmin = isFullAdmin
        self.isBod = isBod
    }
}

public struct AttendanceExplanationApprovalInboxPaginationProjection: Equatable, Sendable {
    public let limit: Int
    public let offset: Int
    public let totalRows: Int
    public let pendingCount: Int

    public init(limit: Int = 50, offset: Int = 0, totalRows: Int = 0, pendingCount: Int = 0) {
        self.limit = limit
        self.offset = offset
        self.totalRows = totalRows
        self.pendingCount = pendingCount
    }
}

public struct AttendanceExplanationApprovalInboxProjection: Equatable, Sendable {
    public let viewer: AttendanceExplanationApprovalInboxViewerProjection
    public let pagination: AttendanceExplanationApprovalInboxPaginationProjection
    public let items: [AttendanceExplanationItemProjection]

    public init(
        viewer: AttendanceExplanationApprovalInboxViewerProjection,
        pagination: AttendanceExplanationApprovalInboxPaginationProjection,
        items: [AttendanceExplanationItemProjection]
    ) {
        self.viewer = viewer
        self.pagination = pagination
        self.items = items
    }
}

public struct SubmitAttendanceExplanationResultProjection: Equatable, Sendable {
    public let replayed: Bool
    public let explanation: AttendanceExplanationItemProjection

    public init(replayed: Bool, explanation: AttendanceExplanationItemProjection) {
        self.replayed = replayed
        self.explanation = explanation
    }
}

public struct DecideAttendanceExplanationResultProjection: Equatable, Sendable {
    public let replayed: Bool
    public let explanation: AttendanceExplanationItemProjection

    public init(replayed: Bool, explanation: AttendanceExplanationItemProjection) {
        self.replayed = replayed
        self.explanation = explanation
    }
}

// MARK: - Attendance Explanation Contract

public enum AttendanceExplanationContract {
    public static let reasonMinLength = 3
    public static let reasonMaxLength = 500
    public static let decisionNoteMaxLength = 500
    public static let defaultPageLimit = 50

    public static let defaultSlotPolicyProfileId = "00000000-0000-4000-8000-000000000001"
    public static let defaultSlotPolicyProfileCode = "DEFAULT"
    public static let defaultSlotPolicyProfileName = "Mặc định"

    public static let approvalStatusFilters: [String] = ["PENDING", "APPROVED", "REJECTED", "ALL"]
    public static let decisionActions: Set<String> = ["APPROVE", "REJECT"]

    public static let errorMessagesVi: [String: String] = [
        "ATTENDANCE_EXPLANATION_ACCESS_DENIED": "Tài khoản không thuộc diện gửi hoặc tra cứu giải trình chấm công.",
        "ATTENDANCE_EXPLANATION_SUBMIT_DENIED": "Tài khoản không có quyền gửi giải trình chấm công.",
        "ATTENDANCE_EXPLANATION_APPROVAL_ACCESS_DENIED": "Bạn không có quyền truy cập hộp thư duyệt giải trình chấm công.",
        "ATTENDANCE_EXPLANATION_DECIDE_DENIED": "Bạn không thuộc thẩm quyền duyệt giải trình này.",
        "ATTENDANCE_EXPLANATION_DECISION_DENIED": "Bạn không có quyền xử lý giải trình chấm công này.",
        "ATTENDANCE_EXPLANATION_SELF_APPROVAL_FORBIDDEN": "Không được tự duyệt giải trình chấm công của chính mình.",
        "ATTENDANCE_EXPLANATION_NOT_FOUND": "Không tìm thấy giải trình chấm công tương ứng.",
        "ATTENDANCE_EXPLANATION_ALREADY_SUBMITTED": "Mốc chấm công này đã có giải trình. Mỗi mốc chỉ được gửi giải trình một lần.",
        "ATTENDANCE_EXPLANATION_ALREADY_EXISTS": "Mốc chấm công này đã có giải trình (mỗi mốc trong ngày chỉ được gửi 1 giải trình).",
        "ATTENDANCE_EXPLANATION_SLOT_NOT_ELIGIBLE": "Mốc chấm công này không ở trạng thái thiếu chấm hoặc ngoài khung cho phép để gửi giải trình.",
        "ATTENDANCE_EXPLANATION_SLOT_INVALID": "Mốc chấm công cần giải trình không hợp lệ.",
        "ATTENDANCE_EXPLANATION_DATE_INVALID": "Ngày nghiệp vụ giải trình không hợp lệ.",
        "ATTENDANCE_EXPLANATION_DATE_FUTURE": "Chưa thể gửi giải trình cho ngày làm việc trong tương lai.",
        "ATTENDANCE_EXPLANATION_FUTURE_DATE_NOT_ALLOWED": "Không thể gửi giải trình cho ngày trong tương lai.",
        "ATTENDANCE_EXPLANATION_APPROVER_UNCONFIGURED": "Chưa cấu hình người duyệt cho bộ phận/nhóm của bạn. Vui lòng liên hệ Nhân sự hoặc Admin.",
        "ATTENDANCE_EXPLANATION_REASON_INVALID": "Lý do giải trình phải từ 3 đến 500 ký tự.",
        "ATTENDANCE_EXPLANATION_NOTE_INVALID": "Ghi chú phản hồi không được vượt quá 500 ký tự.",
        "ATTENDANCE_EXPLANATION_DECISION_INVALID": "Quyết định duyệt giải trình không hợp lệ.",
        "ATTENDANCE_EXPLANATION_VERSION_CONFLICT": "Giải trình đã được cập nhật bởi phiên làm việc khác. Dữ liệu đã được tải lại.",
        "ATTENDANCE_EXPLANATION_STATE_INVALID": "Giải trình đã được xử lý trước đó và không còn ở trạng thái chờ duyệt.",
        "ATTENDANCE_EXPLANATION_ALREADY_DECIDED": "Giải trình đã được xử lý trước đó và không còn ở trạng thái chờ duyệt.",
        "IDEMPOTENCY_KEY_INVALID": "Khóa chống gửi trùng (Idempotency-Key) không hợp lệ.",
        "IDEMPOTENCY_CONFLICT": "Khóa chống gửi trùng đã được dùng cho một thao tác khác.",
        "ATTENDANCE_REPORT_UNAVAILABLE": "Dịch vụ giải trình chấm công tạm thời gián đoạn. Vui lòng thử lại sau."
    ]

    public static func errorMessageForCode(_ code: String?, fallbackMessage: String? = nil) -> String {
        if let clean = code?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
           let msg = errorMessagesVi[clean] {
            return msg
        }
        if let fb = fallbackMessage?.trimmingCharacters(in: .whitespacesAndNewlines), !fb.isEmpty {
            return fb
        }
        return "Không thể thực hiện thao tác giải trình chấm công."
    }

    public static func isConflictError(httpStatus: Int?, errorCode: String?) -> Bool {
        let code = errorCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return httpStatus == 409 ||
            code == "ATTENDANCE_EXPLANATION_VERSION_CONFLICT" ||
            code == "ATTENDANCE_EXPLANATION_STATE_INVALID" ||
            code == "ATTENDANCE_EXPLANATION_ALREADY_DECIDED" ||
            code == "ATTENDANCE_EXPLANATION_ALREADY_SUBMITTED" ||
            code == "ATTENDANCE_EXPLANATION_ALREADY_EXISTS" ||
            code == "ATTENDANCE_EXPLANATION_SLOT_NOT_ELIGIBLE" ||
            code == "IDEMPOTENCY_CONFLICT"
    }

    public static func newIdempotencyKey() -> String {
        UUID().uuidString.lowercased()
    }

    public static func isValidIdempotencyKey(_ key: String?) -> Bool {
        guard let clean = key?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !clean.isEmpty else {
            return false
        }
        let pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        return clean.range(of: pattern, options: .regularExpression) != nil
    }

    public static func todayVietnamIsoDate(now: Date = Date()) -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: now)
    }

    public static func isValidIsoDate(_ dateStr: String?) -> Bool {
        guard let clean = dateStr?.trimmingCharacters(in: .whitespacesAndNewlines),
              clean.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil else {
            return false
        }
        let parts = clean.split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]), (2000...2100).contains(year),
              let month = Int(parts[1]), (1...12).contains(month),
              let day = Int(parts[2]), (1...31).contains(day) else {
            return false
        }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        guard let date = cal.date(from: comps) else { return false }
        let check = cal.dateComponents([.year, .month, .day], from: date)
        return check.year == year && check.month == month && check.day == day
    }

    public static func isValidSlotCode(_ slotCode: String?) -> Bool {
        guard let clean = slotCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else {
            return false
        }
        return clean.range(of: "^SLOT_(?:[1-9]|1[0-2])$", options: .regularExpression) != nil
    }

    private static func containsDisallowedControlChars(_ text: String) -> Bool {
        for scalar in text.unicodeScalars {
            let v = scalar.value
            if (v <= 0x08) || v == 0x0B || v == 0x0C || (v >= 0x0E && v <= 0x1F) || v == 0x7F {
                return true
            }
        }
        return false
    }

    public static func validateSubmitInput(
        businessDate: String,
        slotCode: String,
        reason: String,
        todayLocal: String? = nil
    ) -> String? {
        let cleanDate = businessDate.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidIsoDate(cleanDate) else {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_DATE_INVALID"]
        }
        let refToday: String = {
            if let tl = todayLocal?.trimmingCharacters(in: .whitespacesAndNewlines), isValidIsoDate(tl) {
                return tl
            }
            return todayVietnamIsoDate()
        }()
        if cleanDate > refToday {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_DATE_FUTURE"]
        }
        guard isValidSlotCode(slotCode) else {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_SLOT_INVALID"]
        }
        let cleanReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanReason.count < reasonMinLength ||
            cleanReason.count > reasonMaxLength ||
            containsDisallowedControlChars(cleanReason) {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_REASON_INVALID"]
        }
        return nil
    }

    public static func validateDecisionInput(
        actorAppUserId: String?,
        item: AttendanceExplanationItemProjection,
        decision: String,
        decisionNote: String?
    ) -> String? {
        let cleanActor = actorAppUserId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !cleanActor.isEmpty &&
            cleanActor.caseInsensitiveCompare(item.appUserId.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_SELF_APPROVAL_FORBIDDEN"]
        }
        if !item.canDecide {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_DECIDE_DENIED"]
        }
        if item.status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() != "PENDING" {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_STATE_INVALID"]
        }
        let cleanDecision = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if !decisionActions.contains(cleanDecision) {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_DECISION_INVALID"]
        }
        let cleanNote = decisionNote?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if cleanNote.count > decisionNoteMaxLength || containsDisallowedControlChars(cleanNote) {
            return errorMessagesVi["ATTENDANCE_EXPLANATION_NOTE_INVALID"]
        }
        return nil
    }

    public static func explanationStatusLabel(_ status: String) -> String {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "PENDING":
            return "Chờ duyệt"
        case "APPROVED":
            return "Đã duyệt"
        case "REJECTED":
            return "Từ chối"
        default:
            return status
        }
    }

    public static func originalSlotStatusLabel(_ originalSlotStatus: String) -> String {
        switch originalSlotStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "MISSING":
            return "Thiếu chấm công"
        case "OUT_OF_GRACE":
            return "Ngoài khung giờ"
        case "ON_TIME":
            return "Đúng giờ"
        case "LEAVE_COVERED", "LEAVE_EXEMPT":
            return "Miễn chấm (nghỉ được duyệt)"
        case "UPCOMING", "NOT_APPLICABLE":
            return "Chưa đến mốc / Không áp dụng"
        default:
            return originalSlotStatus
        }
    }

    public static func slotStatusLabel(_ slotStatus: String) -> String {
        switch slotStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ON_TIME":
            return "Đúng giờ"
        case "OUT_OF_GRACE":
            return "Ngoài khung giờ"
        case "MISSING":
            return "Thiếu chấm công"
        case "LEAVE_COVERED", "LEAVE_EXEMPT":
            return "Miễn chấm (nghỉ được duyệt)"
        case "UPCOMING", "NOT_APPLICABLE":
            return "Chưa đến mốc"
        case "EXPLAINED_PENDING":
            return "Đang chờ duyệt giải trình"
        case "EXPLAINED_APPROVED":
            return "Giải trình được duyệt"
        case "EXPLAINED_REJECTED":
            return "Giải trình bị từ chối"
        default:
            return slotStatus
        }
    }

    public static func sessionLabel(_ sessionCode: String) -> String {
        switch sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "AM":
            return "Buổi sáng"
        case "PM":
            return "Buổi chiều"
        default:
            return sessionCode
        }
    }

    public static func formatDeviationBadge(_ checkin: AttendanceSlotCheckinItemProjection) -> String? {
        let minutes = checkin.overGraceMinutes > 0
            ? checkin.overGraceMinutes
            : abs(checkin.deltaMinutes ?? 0)
        switch checkin.deviationStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ON_TIME":
            return "Đúng giờ"
        case "EARLY":
            return "Sớm \(minutes)p"
        case "LATE":
            return "Muộn \(minutes)p"
        default:
            return nil
        }
    }

    public static func formatGraceWindow(
        targetTime: String,
        graceBeforeMinutes: Int,
        graceAfterMinutes: Int
    ) -> String {
        "\(targetTime) (-\(graceBeforeMinutes)p / +\(graceAfterMinutes)p)"
    }

    public static func auditEventTypeLabel(_ eventType: String) -> String {
        switch eventType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "SUBMITTED":
            return "Gửi giải trình"
        case "APPROVED":
            return "Duyệt giải trình"
        case "REJECTED":
            return "Từ chối giải trình"
        default:
            return eventType
        }
    }

    public static func statusFilterLabel(_ filter: String) -> String {
        switch filter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "PENDING":
            return "Chờ duyệt"
        case "APPROVED":
            return "Đã duyệt"
        case "REJECTED":
            return "Từ chối"
        case "ALL":
            return "Tất cả"
        default:
            return filter
        }
    }

    // MARK: - JSON Parsing

    public static func parseAccountSummary(_ obj: [String: Any]) -> AttendanceExplanationAccountSummaryProjection {
        AttendanceExplanationAccountSummaryProjection(
            appUserId: obj.optString("app_user_id"),
            displayName: obj.optNullableString("display_name"),
            loginId: obj.optNullableString("login_id"),
            personnelCode: obj.optNullableString("personnel_code"),
            recruiterId: obj.optNullableString("recruiter_id"),
            accountEnabled: obj.optBool("account_enabled", defaultValue: true)
        )
    }

    public static func parseSlotCheckin(_ obj: [String: Any]) -> AttendanceSlotCheckinItemProjection {
        let deltaMin: Int? = {
            if obj["delta_minutes"] is NSNull || obj["delta_minutes"] == nil {
                return nil
            }
            return obj.optInt("delta_minutes")
        }()
        let distMeters: Int? = {
            if obj["distance_meters"] is NSNull || obj["distance_meters"] == nil {
                return nil
            }
            return obj.optInt("distance_meters")
        }()
        return AttendanceSlotCheckinItemProjection(
            checkinId: obj.optString("checkin_id"),
            sequenceInDay: obj.optInt("sequence_in_day", defaultValue: 1),
            capturedAt: obj.optString("captured_at"),
            capturedAtLocal: obj.optString("captured_at_local"),
            timeLocal: obj.optString("time_local"),
            timeVerified: obj.optBool("time_verified", defaultValue: true),
            assignedSlotCode: obj.optNullableString("assigned_slot_code"),
            deltaMinutes: deltaMin,
            overGraceMinutes: obj.optInt("over_grace_minutes", defaultValue: 0),
            deviationStatus: obj.optString("deviation_status", defaultValue: "ON_TIME"),
            distanceTargetKind: obj.optString("distance_target_kind", defaultValue: "NONE"),
            distanceStatus: obj.optString("distance_status", defaultValue: "UNAVAILABLE"),
            distanceMeters: distMeters
        )
    }

    public static func parseDaySlotExplanationSummary(_ obj: [String: Any]?) -> AttendanceDaySlotExplanationSummaryProjection? {
        guard let obj else { return nil }
        let id = obj.optString("explanation_id")
        guard !id.isEmpty else { return nil }
        return AttendanceDaySlotExplanationSummaryProjection(
            explanationId: id,
            status: obj.optString("status", defaultValue: "PENDING"),
            originalSlotStatus: obj.optString("original_slot_status", defaultValue: "MISSING"),
            reason: obj.optString("reason"),
            submittedAt: obj.optString("submitted_at"),
            submittedAtLocal: obj.optString("submitted_at_local"),
            decidedAt: obj.optNullableString("decided_at"),
            decidedAtLocal: obj.optNullableString("decided_at_local"),
            decidedByDisplayName: obj.optNullableString("decided_by_display_name"),
            decisionNote: obj.optString("decision_note"),
            rowVersion: max(1, obj.optInt("row_version", defaultValue: 1))
        )
    }

    public static func parseDaySlotEvaluation(_ obj: [String: Any]) -> AttendanceExplanationDaySlotEvaluationProjection {
        let code = obj.optString("slot_code")
        let label = obj.optString("slot_label", defaultValue: code)
        let checkins = obj.optObjectArray("checkins").map { parseSlotCheckin($0) }
        let explanationObj = obj["explanation"] as? [String: Any]
        return AttendanceExplanationDaySlotEvaluationProjection(
            slotCode: code,
            slotIndex: obj.optInt("slot_index", defaultValue: 1),
            slotLabel: label,
            sessionCode: obj.optString("session_code", defaultValue: "AM"),
            targetTime: obj.optString("target_time", defaultValue: "00:00"),
            graceBeforeMinutes: obj.optInt("grace_before_minutes", defaultValue: 15),
            graceAfterMinutes: obj.optInt("grace_after_minutes", defaultValue: 15),
            isLeaveCovered: obj.optBool("is_leave_covered", defaultValue: false),
            originalSlotStatus: obj.optString("original_slot_status", defaultValue: "MISSING"),
            slotStatus: obj.optString("slot_status", defaultValue: "MISSING"),
            eligibleForExplanation: obj.optBool("eligible_for_explanation", defaultValue: false),
            checkins: checkins,
            explanation: parseDaySlotExplanationSummary(explanationObj)
        )
    }

    public static func parseAuditEvent(_ obj: [String: Any]) -> AttendanceExplanationAuditEventProjection {
        let actorObj = obj["actor"] as? [String: Any]
        return AttendanceExplanationAuditEventProjection(
            eventId: obj.optString("event_id", defaultValue: obj.optString("audit_event_id")),
            eventType: obj.optString("event_type", defaultValue: obj.optString("action")),
            actorAppUserId: obj.optNullableString("actor_app_user_id") ?? actorObj?.optNullableString("app_user_id"),
            actorDisplayName: obj.optNullableString("actor_display_name")
                ?? actorObj?.optNullableString("display_name")
                ?? actorObj?.optNullableString("login_id"),
            actorRoleContext: obj.optString("actor_role_context"),
            fromStatus: obj.optNullableString("from_status") ?? obj.optNullableString("before_status"),
            toStatus: obj.optString("to_status", defaultValue: obj.optString("after_status")),
            note: obj.optString("note"),
            createdAt: obj.optString("created_at"),
            createdAtLocal: obj.optString("created_at_local")
        )
    }

    public static func parseExplanationItem(_ obj: [String: Any]) -> AttendanceExplanationItemProjection {
        let requesterObj = (obj["requester"] as? [String: Any]) ?? [:]
        let routedApproverObj = (obj["routed_approver"] as? [String: Any])
            ?? (obj["primary_approver"] as? [String: Any])
        let routedTeamObj = (obj["routed_team"] as? [String: Any]) ?? [:]
        let decidedByObj = obj["decided_by"] as? [String: Any]
        let rawEvents = obj.optObjectArray("events")
        let eventsSource = rawEvents.isEmpty ? obj.optObjectArray("audit_events") : rawEvents
        let events = eventsSource.map { parseAuditEvent($0) }
        let rawCheckins = obj.optObjectArray("actual_checkins")
        let checkinsSource = rawCheckins.isEmpty ? obj.optObjectArray("related_checkins") : rawCheckins
        let actualCheckins = checkinsSource.map { parseSlotCheckin($0) }
        let slotCode = obj.optString("slot_code")
        let parsedSlotIndex: Int = {
            if let suffix = Int(slotCode.replacingOccurrences(of: "SLOT_", with: "", options: .caseInsensitive)) {
                return suffix
            }
            return 1
        }()
        let slotIndex = max(1, obj.optInt("slot_index", defaultValue: parsedSlotIndex))
        let profileCode = obj.optString(
            "policy_profile_code_used",
            defaultValue: defaultSlotPolicyProfileCode
        ).uppercased()
        let profileName = obj.optString(
            "policy_profile_name_used",
            defaultValue: profileCode == defaultSlotPolicyProfileCode ? defaultSlotPolicyProfileName : profileCode
        )
        return AttendanceExplanationItemProjection(
            explanationId: obj.optString("explanation_id"),
            appUserId: obj.optString("app_user_id", defaultValue: requesterObj.optString("app_user_id")),
            requester: parseAccountSummary(requesterObj),
            businessDate: obj.optString("business_date"),
            slotCode: slotCode,
            slotIndex: slotIndex,
            slotLabel: obj.optString("slot_label", defaultValue: slotCode),
            sessionCode: obj.optString("session_code", defaultValue: "AM"),
            targetTime: obj.optString("target_time", defaultValue: "00:00"),
            graceBeforeMinutes: obj.optInt("grace_before_minutes", defaultValue: 15),
            graceAfterMinutes: obj.optInt("grace_after_minutes", defaultValue: 15),
            policyProfileIdUsed: obj.optString(
                "policy_profile_id_used",
                defaultValue: defaultSlotPolicyProfileId
            ),
            policyProfileCodeUsed: profileCode,
            policyProfileNameUsed: profileName,
            policyVersionUsed: max(1, obj.optInt("policy_version_used", defaultValue: 1)),
            originalSlotStatus: obj.optString("original_slot_status", defaultValue: "MISSING"),
            effectiveSlotStatus: obj.optString("effective_slot_status"),
            status: obj.optString("status", defaultValue: "PENDING"),
            reason: obj.optString("reason"),
            routeKind: obj.optString("route_kind", defaultValue: "TEAM_LEADER"),
            unitKindSnapshot: obj.optString("unit_kind_snapshot", defaultValue: "RECRUITMENT"),
            routedApprover: routedApproverObj.map { parseAccountSummary($0) },
            routedTeam: AttendanceExplanationRoutedTeamProjection(
                teamId: routedTeamObj.optNullableString("team_id"),
                teamCode: routedTeamObj.optNullableString("team_code"),
                teamName: routedTeamObj.optNullableString("team_name")
            ),
            fallbackReason: obj.optNullableString("fallback_reason"),
            submittedAt: obj.optString("submitted_at"),
            submittedAtLocal: obj.optString("submitted_at_local"),
            decidedAt: obj.optNullableString("decided_at"),
            decidedAtLocal: obj.optNullableString("decided_at_local"),
            decidedBy: decidedByObj.map { parseAccountSummary($0) },
            decisionNote: obj.optString("decision_note"),
            rowVersion: max(1, obj.optInt("row_version", defaultValue: 1)),
            canDecide: obj.optBool("can_decide", defaultValue: false),
            actualCheckins: actualCheckins,
            events: events
        )
    }

    public static func parseMyExplanationsOverview(
        _ root: [String: Any]
    ) -> MiniBiApiResult<AttendanceMyExplanationsOverviewProjection> {
        let dataObj = (root["data"] as? [String: Any]) ?? root
        guard let viewerObj = dataObj["viewer"] as? [String: Any] else {
            return .failure(
                message: "Dữ liệu giải trình chấm công từ máy chủ không hợp lệ.",
                errorCode: "ATTENDANCE_EXPLANATION_OVERVIEW_PARSE_ERROR"
            )
        }
        let routingObj = (dataObj["routing"] as? [String: Any]) ?? [:]
        let primaryApproverObj = routingObj["primary_approver"] as? [String: Any]
        let dayEvalObj = (dataObj["day_evaluation"] as? [String: Any]) ?? [:]
        let slots = dayEvalObj.optObjectArray("slots")
            .map { parseDaySlotEvaluation($0) }
            .sorted { $0.slotIndex < $1.slotIndex }
        let paginationObj = (dataObj["pagination"] as? [String: Any]) ?? [:]
        let items = dataObj.optObjectArray("items").map { parseExplanationItem($0) }
        let todayLocal = dataObj.optString("today_local", defaultValue: todayVietnamIsoDate())
        let selectedDate = dataObj.optString("selected_date", defaultValue: todayLocal)

        let evalProfileId = dayEvalObj.optString(
            "policy_profile_id_used",
            defaultValue: defaultSlotPolicyProfileId
        )
        let evalProfileCode = dayEvalObj.optString(
            "policy_profile_code_used",
            defaultValue: defaultSlotPolicyProfileCode
        ).uppercased()
        let evalProfileName = dayEvalObj.optString(
            "policy_profile_name_used",
            defaultValue: evalProfileCode == defaultSlotPolicyProfileCode ? defaultSlotPolicyProfileName : evalProfileCode
        )
        let evalIsDefault: Bool = {
            if dayEvalObj["policy_is_default_used"] != nil && !(dayEvalObj["policy_is_default_used"] is NSNull) {
                return dayEvalObj.optBool("policy_is_default_used", defaultValue: true)
            }
            return evalProfileCode == defaultSlotPolicyProfileCode || evalProfileId == defaultSlotPolicyProfileId
        }()

        let overview = AttendanceMyExplanationsOverviewProjection(
            viewer: AttendanceExplanationViewerProjection(
                appUserId: viewerObj.optString("app_user_id"),
                canSubmitExplanation: viewerObj.optBool("can_submit_explanation", defaultValue: true),
                canApproveExplanations: viewerObj.optBool("can_approve_explanations", defaultValue: false),
                isSharedNonEmployee: viewerObj.optBool("is_shared_non_employee", defaultValue: false),
                isFullAdmin: viewerObj.optBool("is_full_admin", defaultValue: false),
                isBod: viewerObj.optBool("is_bod", defaultValue: false)
            ),
            routing: AttendanceExplanationRoutingProjection(
                routeKind: routingObj.optString("route_kind", defaultValue: "TEAM_LEADER"),
                unitKind: routingObj.optString("unit_kind", defaultValue: "RECRUITMENT"),
                primaryApprover: primaryApproverObj.map { parseAccountSummary($0) },
                teamId: routingObj.optNullableString("team_id"),
                teamCode: routingObj.optNullableString("team_code"),
                teamName: routingObj.optNullableString("team_name"),
                fallbackReason: routingObj.optNullableString("fallback_reason"),
                profileConfigured: routingObj.optBool("profile_configured", defaultValue: true)
            ),
            todayLocal: todayLocal,
            selectedDate: selectedDate,
            dayEvaluation: AttendanceExplanationDayEvaluationProjection(
                businessDate: dayEvalObj.optString("business_date", defaultValue: selectedDate),
                policyProfileIdUsed: evalProfileId,
                policyProfileCodeUsed: evalProfileCode,
                policyProfileNameUsed: evalProfileName,
                policyIsDefaultUsed: evalIsDefault,
                policyVersionUsed: max(1, dayEvalObj.optInt("policy_version_used", defaultValue: 1)),
                slots: slots
            ),
            pagination: AttendanceExplanationPaginationProjection(
                limit: paginationObj.optInt("limit", defaultValue: defaultPageLimit),
                offset: paginationObj.optInt("offset", defaultValue: 0),
                totalRows: paginationObj.optInt("total_rows", defaultValue: items.count)
            ),
            items: items
        )
        return .success(overview)
    }

    public static func parseApprovalInbox(
        _ root: [String: Any]
    ) -> MiniBiApiResult<AttendanceExplanationApprovalInboxProjection> {
        let dataObj = (root["data"] as? [String: Any]) ?? root
        guard let viewerObj = dataObj["viewer"] as? [String: Any] else {
            return .failure(
                message: "Dữ liệu hộp thư xét duyệt giải trình từ máy chủ không hợp lệ.",
                errorCode: "ATTENDANCE_EXPLANATION_INBOX_PARSE_ERROR"
            )
        }
        let paginationObj = (dataObj["pagination"] as? [String: Any]) ?? [:]
        let items = dataObj.optObjectArray("items").map { parseExplanationItem($0) }
        let defaultPendingCount = items.filter { $0.status.uppercased() == "PENDING" }.count

        let inbox = AttendanceExplanationApprovalInboxProjection(
            viewer: AttendanceExplanationApprovalInboxViewerProjection(
                appUserId: viewerObj.optString("app_user_id"),
                canApproveExplanations: viewerObj.optBool("can_approve_explanations", defaultValue: false),
                isFullAdmin: viewerObj.optBool("is_full_admin", defaultValue: false),
                isBod: viewerObj.optBool("is_bod", defaultValue: false)
            ),
            pagination: AttendanceExplanationApprovalInboxPaginationProjection(
                limit: paginationObj.optInt("limit", defaultValue: defaultPageLimit),
                offset: paginationObj.optInt("offset", defaultValue: 0),
                totalRows: paginationObj.optInt("total_rows", defaultValue: items.count),
                pendingCount: paginationObj.optInt("pending_count", defaultValue: defaultPendingCount)
            ),
            items: items
        )
        return .success(inbox)
    }
}

// MARK: - Attendance Explanation Service

public final class AttendanceExplanationService: @unchecked Sendable {
    public static let shared = AttendanceExplanationService()

    private let httpClient: BackendHttpClient

    public init(httpClient: BackendHttpClient = .shared) {
        self.httpClient = httpClient
    }

    public func fetchMyExplanationsOverview(
        businessDate: String? = nil,
        limit: Int = AttendanceExplanationContract.defaultPageLimit,
        offset: Int = 0
    ) async -> MiniBiApiResult<AttendanceMyExplanationsOverviewProjection> {
        let safeLimit = min(200, max(1, limit))
        let safeOffset = max(0, offset)
        var queryItems: [URLQueryItem] = []
        if let cleanDate = businessDate?.trimmingCharacters(in: .whitespacesAndNewlines),
           AttendanceExplanationContract.isValidIsoDate(cleanDate) {
            queryItems.append(URLQueryItem(name: "business_date", value: cleanDate))
        }
        queryItems.append(URLQueryItem(name: "limit", value: String(safeLimit)))
        queryItems.append(URLQueryItem(name: "offset", value: String(safeOffset)))

        let response = await httpClient.request(
            method: "GET",
            path: "/api/attendance/explanations",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode(
                    response.errorCode,
                    fallbackMessage: response.errorMessage
                ),
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
                message: AttendanceExplanationContract.errorMessageForCode(code, fallbackMessage: msg),
                httpStatus: response.statusCode,
                errorCode: code,
                rawBody: response.rawBody
            )
        }
        return AttendanceExplanationContract.parseMyExplanationsOverview(root)
    }

    public func submitExplanation(
        businessDate: String,
        slotCode: String,
        reason: String,
        idempotencyKey: String = AttendanceExplanationContract.newIdempotencyKey()
    ) async -> MiniBiApiResult<SubmitAttendanceExplanationResultProjection> {
        let cleanDate = businessDate.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSlot = slotCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanKey: String = {
            let trimmed = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            return AttendanceExplanationContract.isValidIdempotencyKey(trimmed)
                ? trimmed
                : AttendanceExplanationContract.newIdempotencyKey()
        }()

        if let validationError = AttendanceExplanationContract.validateSubmitInput(
            businessDate: cleanDate,
            slotCode: cleanSlot,
            reason: cleanReason
        ) {
            return .failure(
                message: validationError,
                httpStatus: 400,
                errorCode: "ATTENDANCE_EXPLANATION_VALIDATION_FAILED"
            )
        }

        let payload: [String: Any] = [
            "business_date": cleanDate,
            "slot_code": cleanSlot,
            "reason": cleanReason,
            "idempotency_key": cleanKey
        ]

        let response = await httpClient.request(
            method: "POST",
            path: "/api/attendance/explanations",
            jsonBody: payload,
            additionalHeaders: ["Idempotency-Key": cleanKey]
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode(
                    response.errorCode,
                    fallbackMessage: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        let dataObj = (root["data"] as? [String: Any]) ?? root
        guard let explanationObj = dataObj["explanation"] as? [String: Any] else {
            return .failure(
                message: "Phản hồi gửi giải trình chấm công từ máy chủ không hợp lệ.",
                httpStatus: response.statusCode,
                errorCode: "ATTENDANCE_EXPLANATION_SUBMIT_PARSE_ERROR"
            )
        }
        return .success(
            SubmitAttendanceExplanationResultProjection(
                replayed: dataObj.optBool("replayed", defaultValue: false),
                explanation: AttendanceExplanationContract.parseExplanationItem(explanationObj)
            ),
            statusCode: response.statusCode
        )
    }

    public func fetchApprovalInbox(
        statusFilter: String = "PENDING",
        limit: Int = AttendanceExplanationContract.defaultPageLimit,
        offset: Int = 0
    ) async -> MiniBiApiResult<AttendanceExplanationApprovalInboxProjection> {
        let cleanFilter: String = {
            let upper = statusFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return AttendanceExplanationContract.approvalStatusFilters.contains(upper) ? upper : "PENDING"
        }()
        let safeLimit = min(200, max(1, limit))
        let safeOffset = max(0, offset)
        let queryItems: [URLQueryItem] = [
            URLQueryItem(name: "status", value: cleanFilter),
            URLQueryItem(name: "limit", value: String(safeLimit)),
            URLQueryItem(name: "offset", value: String(safeOffset))
        ]

        let response = await httpClient.request(
            method: "GET",
            path: "/api/attendance/explanations/approvals",
            queryItems: queryItems
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode(
                    response.errorCode,
                    fallbackMessage: response.errorMessage
                ),
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
                message: AttendanceExplanationContract.errorMessageForCode(code, fallbackMessage: msg),
                httpStatus: response.statusCode,
                errorCode: code,
                rawBody: response.rawBody
            )
        }
        return AttendanceExplanationContract.parseApprovalInbox(root)
    }

    public func decideExplanation(
        explanationId: String,
        decision: String,
        expectedRowVersion: Int,
        decisionNote: String?,
        idempotencyKey: String = AttendanceExplanationContract.newIdempotencyKey()
    ) async -> MiniBiApiResult<DecideAttendanceExplanationResultProjection> {
        let cleanId = explanationId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDecision = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cleanNote = decisionNote?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if cleanId.isEmpty {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode("ATTENDANCE_EXPLANATION_NOT_FOUND"),
                httpStatus: 400,
                errorCode: "ATTENDANCE_EXPLANATION_NOT_FOUND"
            )
        }
        if !AttendanceExplanationContract.decisionActions.contains(cleanDecision) {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode("ATTENDANCE_EXPLANATION_DECISION_INVALID"),
                httpStatus: 400,
                errorCode: "ATTENDANCE_EXPLANATION_DECISION_INVALID"
            )
        }
        if cleanNote.count > AttendanceExplanationContract.decisionNoteMaxLength {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode("ATTENDANCE_EXPLANATION_NOTE_INVALID"),
                httpStatus: 400,
                errorCode: "ATTENDANCE_EXPLANATION_NOTE_INVALID"
            )
        }

        let cleanKey: String = {
            let trimmed = idempotencyKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            return AttendanceExplanationContract.isValidIdempotencyKey(trimmed)
                ? trimmed
                : AttendanceExplanationContract.newIdempotencyKey()
        }()

        let payload: [String: Any] = [
            "decision": cleanDecision,
            "expected_row_version": max(1, expectedRowVersion),
            "decision_note": cleanNote.isEmpty ? NSNull() : cleanNote,
            "idempotency_key": cleanKey
        ]

        let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let response = await httpClient.request(
            method: "POST",
            path: "/api/attendance/explanations/approvals/\(encodedId)/decision",
            jsonBody: payload,
            additionalHeaders: ["Idempotency-Key": cleanKey]
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: AttendanceExplanationContract.errorMessageForCode(
                    response.errorCode,
                    fallbackMessage: response.errorMessage
                ),
                httpStatus: response.statusCode,
                errorCode: response.errorCode,
                rawBody: response.rawBody
            )
        }
        let dataObj = (root["data"] as? [String: Any]) ?? root
        guard let explanationObj = dataObj["explanation"] as? [String: Any] else {
            return .failure(
                message: "Phản hồi xét duyệt giải trình chấm công từ máy chủ không hợp lệ.",
                httpStatus: response.statusCode,
                errorCode: "ATTENDANCE_EXPLANATION_DECISION_PARSE_ERROR"
            )
        }
        return .success(
            DecideAttendanceExplanationResultProjection(
                replayed: dataObj.optBool("replayed", defaultValue: false),
                explanation: AttendanceExplanationContract.parseExplanationItem(explanationObj)
            ),
            statusCode: response.statusCode
        )
    }
}
