import Foundation

// MARK: - Work Reports Contract & Reporting Service
// Mirrors Android's `data/WorkReportsAndReportingService.kt` 100%

public enum WorkReportsContract {
    public static let stateNotStarted = "NOT_STARTED"
    public static let stateDraft = "DRAFT"
    public static let stateSubmitted = "SUBMITTED"
    public static let stateApproved = "APPROVED"
    public static let stateRejected = "REJECTED"

    public static let decisionApprove = "APPROVE"
    public static let decisionReject = "REJECT"

    public static let codeDeadlinePassed = "WORK_REPORT_DEADLINE_PASSED"
    public static let codeVersionConflict = "WORK_REPORT_VERSION_CONFLICT"
    public static let deadlinePassedMessageVi = "Đã quá hạn nộp báo cáo."

    public static func submissionStateDisplayLabel(_ state: String) -> String {
        switch state.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case stateNotStarted:
            return "Chưa bắt đầu"
        case stateDraft:
            return "Bản nháp"
        case stateSubmitted:
            return "Đã nộp · Chờ duyệt"
        case stateApproved:
            return "Đã phê duyệt"
        case stateRejected:
            return "Cần bổ sung / Từ chối"
        default:
            return state.isEmpty ? "—" : state
        }
    }

    public static func obligationBadgeDisplayLabel(_ obligation: WorkReportObligationItem) -> String {
        let normalizedState = obligation.submissionState.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if obligation.isOverdue {
            switch normalizedState {
            case stateNotStarted:
                return "Quá hạn · Chưa nộp"
            case stateDraft:
                return "Quá hạn · Bản nháp chưa nộp"
            default:
                break
            }
        }
        return submissionStateDisplayLabel(obligation.submissionState)
    }

    public static func isActionableState(_ state: String) -> Bool {
        let normalized = state.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return normalized == stateNotStarted || normalized == stateDraft || normalized == stateRejected
    }

    /// Relies strictly on server `isOverdue` and `submissionState`.
    /// Never locks `REJECTED` locally based on device clock vs `dueAt`.
    public static func isActionableObligation(_ obligation: WorkReportObligationItem) -> Bool {
        !obligation.isOverdue && isActionableState(obligation.submissionState)
    }

    public static func isHistoryObligation(_ obligation: WorkReportObligationItem) -> Bool {
        !isActionableObligation(obligation)
    }

    public static func isEditableObligation(_ obligation: WorkReportObligationItem) -> Bool {
        !obligation.isOverdue && isActionableState(obligation.submissionState)
    }

    public static func periodTypeDisplayLabel(_ periodType: String) -> String {
        switch periodType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "DAILY":
            return "Hàng ngày"
        case "WEEKLY":
            return "Hàng tuần"
        case "MONTHLY":
            return "Hàng tháng"
        default:
            return periodType
        }
    }

    public static func formatPeriodKeyVi(_ periodKey: String) -> String {
        let trimmed = periodKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "—" }
        // Match YYYY-MM-DD -> DD/MM/YYYY
        let parts = trimmed.split(separator: "-")
        if parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2 {
            return "\(parts[2])/\(parts[1])/\(parts[0])"
        }
        if parts.count == 2, parts[0].count == 4, parts[1].count == 2 {
            return "Tháng \(parts[1])/\(parts[0])"
        }
        return trimmed
    }

    public static func formatIsoDateTimeVi(_ rawIso: String?) -> String {
        guard let rawIso, !rawIso.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "—"
        }
        let trimmed = rawIso.trimmingCharacters(in: .whitespacesAndNewlines)
        if let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(trimmed) {
            let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
            let df = DateFormatter()
            df.locale = Locale(identifier: "vi_VN")
            df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
            df.dateFormat = "HH:mm dd/MM/yyyy"
            return df.string(from: date)
        }
        return formatPeriodKeyVi(trimmed)
    }

    public static func formatPercentVi(_ ratioOrPercent: Double) -> String {
        let pct = ratioOrPercent <= 1.0 && ratioOrPercent > 0.0 ? ratioOrPercent * 100.0 : ratioOrPercent
        return String(format: "%.1f%%", pct)
    }

    public static func parseDynamicSections(_ schemaRaw: Any?) -> [WorkReportSectionSchema] {
        let dict: [String: Any]?
        if let d = schemaRaw as? [String: Any] {
            dict = d
        } else if let str = schemaRaw as? String,
                  let data = str.data(using: .utf8),
                  let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            dict = parsed
        } else {
            dict = nil
        }

        let sectionsArray: [[String: Any]]
        if let dict {
            sectionsArray = dict.optObjectArray("sections")
                .ifEmpty(dict.optObjectArray("groups"))
                .ifEmpty(dict["fields"] != nil ? [dict] : [])
        } else if let arr = schemaRaw as? [[String: Any]] {
            sectionsArray = [["id": "default", "title": "Nội dung báo cáo", "fields": arr]]
        } else {
            sectionsArray = []
        }

        var result: [WorkReportSectionSchema] = []
        for (secIdx, secObj) in sectionsArray.enumerated() {
            let secId = secObj.optString("id", "section_id", "key", defaultValue: "sec_\(secIdx)")
            let secTitle = secObj.optString("title", "label", "name", defaultValue: "Phần \(secIdx + 1)")
            let secDesc = secObj.optNullableString("description", "subtitle")
            let rawFields = secObj.optObjectArray("fields").ifEmpty(secObj.optObjectArray("items"))

            let fields: [WorkReportFieldSchema] = rawFields.enumerated().compactMap { fIdx, fObj in
                let fieldId = fObj.optString("id", "key", "name", "field_id")
                guard !fieldId.isEmpty else { return nil }
                let label = fObj.optString("label", "title", defaultValue: fieldId)
                let type = fObj.optString("type", "field_type", defaultValue: "text").lowercased()
                let required = fObj.optBool("required", "is_required", defaultValue: false)
                let placeholder = fObj.optNullableString("placeholder", "hint")
                var options: [WorkReportFieldOption] = []
                if let optObjs = fObj["options"] as? [[String: Any]] {
                    options = optObjs.compactMap { o in
                        let val = o.optString("value", "id", "code", "label")
                        guard !val.isEmpty else { return nil }
                        let lbl = o.optString("label", "name", "title", defaultValue: val)
                        return WorkReportFieldOption(value: val, label: lbl)
                    }
                } else if let optStrs = fObj["options"] as? [String] {
                    options = optStrs.map { WorkReportFieldOption(value: $0, label: $0) }
                }
                return WorkReportFieldSchema(
                    id: fieldId.isEmpty ? "field_\(fIdx)" : fieldId,
                    label: label,
                    type: type,
                    required: required,
                    placeholder: placeholder,
                    options: options,
                    min: fObj.optNullableDouble("min", "min_value"),
                    max: fObj.optNullableDouble("max", "max_value")
                )
            }

            if !fields.isEmpty {
                result.append(
                    WorkReportSectionSchema(
                        id: secId,
                        title: secTitle,
                        description: secDesc,
                        fields: fields
                    )
                )
            }
        }

        if result.isEmpty {
            result.append(
                WorkReportSectionSchema(
                    id: "default",
                    title: "Nội dung báo cáo",
                    description: nil,
                    fields: [
                        WorkReportFieldSchema(
                            id: "summary",
                            label: "Tóm tắt công việc đã thực hiện",
                            type: "textarea",
                            required: true,
                            placeholder: "Nhập kết quả công việc trong kỳ..."
                        ),
                        WorkReportFieldSchema(
                            id: "issues",
                            label: "Khó khăn / Vướng mắc",
                            type: "textarea",
                            required: false,
                            placeholder: "Nhập khó khăn cần hỗ trợ (nếu có)..."
                        ),
                        WorkReportFieldSchema(
                            id: "next_plan",
                            label: "Kế hoạch kỳ tiếp theo",
                            type: "textarea",
                            required: false,
                            placeholder: "Nhập kế hoạch tiếp theo..."
                        )
                    ]
                )
            )
        }
        return result
    }
}

public final class WorkReportsAndReportingService {
    public static let shared = WorkReportsAndReportingService()

    private let httpClient: BackendHttpClient

    public init(httpClient: BackendHttpClient = .shared) {
        self.httpClient = httpClient
    }

    // MARK: - 1. Work Report Obligations (`GET /api/work-reports/obligations`)

    public func fetchMyObligations(
        submissionState: String? = nil,
        periodKey: String? = nil
    ) async -> MiniBiApiResult<[WorkReportObligationItem]> {
        var queryItems: [URLQueryItem] = []
        if let submissionState, !submissionState.isEmpty {
            queryItems.append(URLQueryItem(name: "submission_state", value: submissionState))
        }
        if let periodKey, !periodKey.isEmpty {
            queryItems.append(URLQueryItem(name: "period_key", value: periodKey))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/obligations",
            queryItems: queryItems
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được danh sách báo cáo công việc.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let itemsArr = root.optObjectArray("data")
            .ifEmpty(root.optObjectArray("obligations"))
            .ifEmpty(root.optObjectArray("items"))
            .ifEmpty((root["data"] as? [String: Any])?.optObjectArray("obligations") ?? [])
            .ifEmpty((root["data"] as? [String: Any])?.optObjectArray("items") ?? [])

        let parsed = itemsArr.map { Self.parseObligationItem($0) }
        return .success(parsed, statusCode: response.statusCode)
    }

    public func fetchObligationDetail(obligationId: Int64) async -> MiniBiApiResult<WorkReportObligationItem> {
        let response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/obligations/\(obligationId)"
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được chi tiết báo cáo công việc.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let obj = (root["obligation"] as? [String: Any])
            ?? (root["item"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root

        let parsed = Self.parseObligationItem(obj, fallbackRoot: root)
        return .success(parsed, statusCode: response.statusCode)
    }

    // MARK: - 2. Save Draft & Submit Report (with `WORK_REPORT_DEADLINE_PASSED` handling)

    public func saveDraft(
        submissionId: Int64,
        version: Int,
        formPayload: [String: String]
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        let body: [String: Any] = [
            "version": version,
            "payload": formPayload,
            "form_payload": formPayload,
            "answers": formPayload
        ]

        let response = await httpClient.request(
            method: "POST",
            path: "/api/work-reports/submissions/\(submissionId)/draft",
            jsonBody: body
        )

        if response.statusCode == 409 {
            return Self.handleWorkReportConflictResponse(response)
        }

        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Lưu nháp báo cáo thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let obj = (root["submission"] as? [String: Any])
            ?? (root["obligation"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let item = Self.parseObligationItem(obj, fallbackRoot: root)
        return .success(item, statusCode: response.statusCode)
    }

    public func submitReport(
        submissionId: Int64,
        version: Int,
        formPayload: [String: String]
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        let body: [String: Any] = [
            "version": version,
            "payload": formPayload,
            "form_payload": formPayload,
            "answers": formPayload
        ]

        let response = await httpClient.request(
            method: "POST",
            path: "/api/work-reports/submissions/\(submissionId)/submit",
            jsonBody: body
        )

        if response.statusCode == 409 {
            return Self.handleWorkReportConflictResponse(response)
        }

        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Nộp báo cáo thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let obj = (root["submission"] as? [String: Any])
            ?? (root["obligation"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let item = Self.parseObligationItem(obj, fallbackRoot: root)
        return .success(item, statusCode: response.statusCode)
    }

    /// Distinguishes `WORK_REPORT_DEADLINE_PASSED` from `WORK_REPORT_VERSION_CONFLICT` on HTTP 409.
    public static func handleWorkReportConflictResponse<T>(_ response: BackendHttpResponse) -> MiniBiApiResult<T> {
        let rawCode = (response.errorCode ?? response.jsonObject?.optString("code", "error_code") ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()

        if rawCode == WorkReportsContract.codeDeadlinePassed {
            return .failure(
                message: WorkReportsContract.deadlinePassedMessageVi,
                httpStatus: 409,
                errorCode: WorkReportsContract.codeDeadlinePassed
            )
        }

        let resolvedConflictCode = rawCode.isEmpty ? WorkReportsContract.codeVersionConflict : rawCode
        let conflictMessage = response.errorMessage
            ?? "Báo cáo đã được cập nhật ở phiên làm việc khác. Đã tải lại phiên bản mới nhất."
        return .failure(
            message: conflictMessage,
            httpStatus: 409,
            errorCode: resolvedConflictCode
        )
    }

    // MARK: - 3. Reviews & Templates & Analytics

    public func fetchPendingReviews(status: String = "PENDING") async -> MiniBiApiResult<[WorkReportReviewItem]> {
        let response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/reviews",
            queryItems: [URLQueryItem(name: "status", value: status)]
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success([], statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được danh sách báo cáo chờ duyệt.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let rawList = root.optObjectArray("data")
            .ifEmpty(root.optObjectArray("reviews"))
            .ifEmpty(root.optObjectArray("items"))
            .ifEmpty((root["data"] as? [String: Any])?.optObjectArray("reviews") ?? [])
            .ifEmpty((root["data"] as? [String: Any])?.optObjectArray("items") ?? [])

        let items = rawList.map { Self.parseReviewItem($0) }
        return .success(items, statusCode: response.statusCode)
    }

    public func decideReview(
        submissionId: Int64,
        decision: String,
        comment: String,
        expectedVersion: Int
    ) async -> MiniBiApiResult<Bool> {
        let body: [String: Any] = [
            "decision": decision,
            "comment": comment,
            "review_comment": comment,
            "version": expectedVersion
        ]
        let response = await httpClient.request(
            method: "POST",
            path: "/api/work-reports/submissions/\(submissionId)/review",
            jsonBody: body
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Xử lý duyệt báo cáo thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(true, statusCode: response.statusCode)
    }

    public func fetchSelectableTemplates() async -> MiniBiApiResult<[WorkReportTemplateOption]> {
        let response = await httpClient.request(method: "GET", path: "/api/work-reports/templates")
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success([], statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được danh sách biểu mẫu báo cáo.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        let root = response.jsonObject ?? [:]
        let rawList = root.optObjectArray("data")
            .ifEmpty(root.optObjectArray("templates"))
            .ifEmpty(root.optObjectArray("items"))
            .ifEmpty((root["data"] as? [String: Any])?.optObjectArray("templates") ?? [])

        let items = rawList.compactMap { obj -> WorkReportTemplateOption? in
            let code = obj.optString("code", "template_code", "id")
            guard !code.isEmpty else { return nil }
            let name = obj.optString("name", "template_name", "title", defaultValue: code)
            let periodType = obj.optString("period_type", defaultValue: "WEEKLY")
            return WorkReportTemplateOption(code: code, name: name, periodType: periodType)
        }
        return .success(items, statusCode: response.statusCode)
    }

    public func fetchAnalyticsSnapshot(
        templateCode: String? = nil,
        periodType: String? = nil,
        periodKey: String? = nil
    ) async -> MiniBiApiResult<WorkReportsAnalyticsSnapshot> {
        var queryItems: [URLQueryItem] = []
        if let templateCode, !templateCode.isEmpty {
            queryItems.append(URLQueryItem(name: "template_code", value: templateCode))
        }
        if let periodType, !periodType.isEmpty {
            queryItems.append(URLQueryItem(name: "period_type", value: periodType))
        }
        if let periodKey, !periodKey.isEmpty {
            queryItems.append(URLQueryItem(name: "period_key", value: periodKey))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/analytics/summary",
            queryItems: queryItems
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success(WorkReportsAnalyticsSnapshot(), statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được thống kê tổng hợp báo cáo công việc.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? (root["summary"] as? [String: Any]) ?? root

        let kpiObj = (dataObj["kpis"] as? [String: Any]) ?? dataObj
        let totalOb = kpiObj.optInt("total_obligations", "total")
        let submittedCount = kpiObj.optInt("submitted_count", "submitted")
        let approvedCount = kpiObj.optInt("approved_count", "approved")
        let rejectedCount = kpiObj.optInt("rejected_count", "rejected")
        let overdueCount = kpiObj.optInt("overdue_count", "overdue")
        let completionRate = kpiObj.optDouble("completion_rate", "submitted_rate")
        let onTimeRate = kpiObj.optDouble("on_time_rate", "ontime_rate")

        let byUnitRaw = dataObj.optObjectArray("by_unit").ifEmpty(dataObj.optObjectArray("units"))
        let byUnit: [WorkReportAnalyticsGroupRow] = byUnitRaw.enumerated().map { idx, u in
            WorkReportAnalyticsGroupRow(
                id: u.optString("unit_code", "id", defaultValue: "unit_\(idx)"),
                groupLabel: u.optString("unit_name", "label", "name", defaultValue: "Đơn vị \(idx + 1)"),
                totalObligations: u.optInt("total_obligations", "total"),
                submittedCount: u.optInt("submitted_count", "submitted"),
                approvedCount: u.optInt("approved_count", "approved"),
                overdueCount: u.optInt("overdue_count", "overdue"),
                completionRate: u.optDouble("completion_rate")
            )
        }

        let byTemplateRaw = dataObj.optObjectArray("by_template").ifEmpty(dataObj.optObjectArray("templates"))
        let byTemplate: [WorkReportAnalyticsGroupRow] = byTemplateRaw.enumerated().map { idx, t in
            WorkReportAnalyticsGroupRow(
                id: t.optString("template_code", "id", defaultValue: "tpl_\(idx)"),
                groupLabel: t.optString("template_name", "label", "name", defaultValue: "Mẫu \(idx + 1)"),
                totalObligations: t.optInt("total_obligations", "total"),
                submittedCount: t.optInt("submitted_count", "submitted"),
                approvedCount: t.optInt("approved_count", "approved"),
                overdueCount: t.optInt("overdue_count", "overdue"),
                completionRate: t.optDouble("completion_rate")
            )
        }

        let snapshot = WorkReportsAnalyticsSnapshot(
            kpis: WorkReportAnalyticsKpis(
                totalObligations: totalOb,
                submittedCount: submittedCount,
                approvedCount: approvedCount,
                rejectedCount: rejectedCount,
                overdueCount: overdueCount,
                completionRate: completionRate,
                onTimeRate: onTimeRate
            ),
            byUnit: byUnit,
            byTemplate: byTemplate
        )
        return .success(snapshot, statusCode: response.statusCode)
    }

    // MARK: - 4. Dashboard Overview & Workforce Deep Metrics & Team & Duplicate CCCD

    public func fetchGeneralOverview(
        scope: String = "all",
        startDate: String? = nil,
        endDate: String? = nil,
        projectFilter: String = "ACTIVE",
        projectSearch: String = "",
        projectsPage: Int = 1,
        projectsPageSize: Int = 20
    ) async -> MiniBiApiResult<GeneralOverviewData> {
        var queryItems: [URLQueryItem] = [URLQueryItem(name: "mode", value: "deep")]
        let cleanStart = (startDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEnd = (endDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanStart.isEmpty && !cleanEnd.isEmpty {
            queryItems.append(URLQueryItem(name: "from", value: cleanStart))
            queryItems.append(URLQueryItem(name: "to", value: cleanEnd))
        } else {
            queryItems.append(URLQueryItem(name: "range", value: "all"))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/reporting/workforce-deep",
            queryItems: queryItems
        )

        if response.isSuccess, let root = response.jsonObject {
            let reportObj = (root["report"] as? [String: Any])
                ?? (root["data"] as? [String: Any])
                ?? root
            let kpisObj = (reportObj["kpis"] as? [String: Any])
                ?? (reportObj["summary"] as? [String: Any])
                ?? reportObj

            let totalWorkers = kpisObj.optInt("total_workers", "total_headcount", "total")
            let activeCount = kpisObj.optInt("on_count", "active_count", "working_count", "active")
            let inactiveCount = kpisObj.optInt("off_count", "inactive_count", "resigned_count", "inactive")
            let unconfirmedCount = kpisObj.optInt("unconfirmed_count", "interview_count", "interview")
            let newCount = kpisObj.optInt("new_in_period_count", "new_count", "new_workers")
            let resignedInPeriodCount = kpisObj.optInt("off_in_period_count", "resigned_in_period_count", "resigned_period")
            let retentionRate = totalWorkers > 0
                ? (Double(activeCount) * 100.0 / Double(totalWorkers))
                : kpisObj.optDouble("retention_rate", "active_rate")
            let turnoverRate = totalWorkers > 0
                ? (Double(inactiveCount) * 100.0 / Double(totalWorkers))
                : kpisObj.optDouble("turnover_rate")

            let kpis = GeneralOverviewKpis(
                totalHeadcount: totalWorkers,
                activeCount: activeCount,
                inactiveCount: inactiveCount,
                interviewCount: unconfirmedCount,
                newCount: newCount,
                resignedInPeriodCount: resignedInPeriodCount,
                retentionRate: retentionRate,
                turnoverRate: turnoverRate
            )

            let projectsRaw = reportObj.optObjectArray("by_project").ifEmpty(reportObj.optObjectArray("projects"))
            let projects: [ProjectBreakdownRow] = projectsRaw.enumerated().map { idx, p in
                let name = p.optString("project_display", "du_an", "project_name", "name", "project_id", defaultValue: "Dự án #\(idx + 1)")
                let pTotal = p.optInt("total_workers", "total_count", "total")
                let pActive = p.optInt("on_count", "active_count", "active")
                let pInactive = p.optInt("off_count", "inactive_count", "inactive")
                let pNew = p.optInt("new_count", "new")
                let share = totalWorkers > 0 ? (Double(pActive) * 100.0 / Double(max(activeCount, 1))) : p.optDouble("active_share_pct", "active_rate")
                return ProjectBreakdownRow(
                    id: "\(name)_\(idx)",
                    projectName: name,
                    totalCount: pTotal,
                    activeCount: pActive,
                    inactiveCount: pInactive,
                    newCount: pNew,
                    activeSharePct: share
                )
            }

            let recruitersRaw = reportObj.optObjectArray("by_recruiter").ifEmpty(reportObj.optObjectArray("recruiters"))
            let recruiters: [PersonPerformanceRow] = recruitersRaw.enumerated().map { idx, r in
                let rTotal = r.optInt("total_workers", "total_count", "total")
                let rActive = r.optInt("on_count", "active_count", "active")
                let rInactive = r.optInt("off_count", "inactive_count", "inactive")
                let rate = rTotal > 0 ? (Double(rActive) * 100.0 / Double(rTotal)) : r.optDouble("active_rate_pct", "active_rate")
                return PersonPerformanceRow(
                    id: r.optString("recruiter_id", "code", "username", defaultValue: "rec_\(idx)"),
                    code: r.optString("recruiter_id", "code", "username"),
                    name: r.optString("recruiter_display", "name", "full_name", "nguoi_tuyen", defaultValue: "NV #\(idx + 1)"),
                    totalCount: rTotal,
                    activeCount: rActive,
                    inactiveCount: rInactive,
                    activeRatePct: rate
                )
            }

            let managersRaw = reportObj.optObjectArray("by_manager").ifEmpty(reportObj.optObjectArray("managers"))
            let managers: [PersonPerformanceRow] = managersRaw.enumerated().map { idx, m in
                let mTotal = m.optInt("total_workers", "total_count", "total")
                let mActive = m.optInt("on_count", "active_count", "active")
                let mInactive = m.optInt("off_count", "inactive_count", "inactive")
                let rate = mTotal > 0 ? (Double(mActive) * 100.0 / Double(mTotal)) : m.optDouble("active_rate_pct", "active_rate")
                return PersonPerformanceRow(
                    id: m.optString("manager_id", "code", "username", defaultValue: "mgr_\(idx)"),
                    code: m.optString("manager_id", "code", "username"),
                    name: m.optString("manager_display", "name", "full_name", "quan_ly", defaultValue: "QL #\(idx + 1)"),
                    totalCount: mTotal,
                    activeCount: mActive,
                    inactiveCount: mInactive,
                    activeRatePct: rate
                )
            }

            let overview = GeneralOverviewData(
                scope: scope,
                kpis: kpis,
                projects: projects,
                projectsTotal: projects.count,
                projectsPage: projectsPage,
                projectsTotalPages: 1,
                recruiters: recruiters,
                managers: managers
            )
            return .success(overview, statusCode: response.statusCode)
        }

        // Fallback for non-`all` scope users (HTTP 403/404 on `/api/reporting/workforce-deep`): aggregate from `/api/direct-entry/workers`
        let workersRes = await MiniBiRepository.shared.fetchWorkers(scope: scope, pageSize: 100)
        if case .success(let wData, let statusCode) = workersRes {
            let items = wData.items
            let total = max(wData.total, items.count)
            let active = items.filter { $0.isWorking }.count
            let inactive = items.filter { $0.trangThai.lowercased().contains("nghỉ") }.count
            let interview = max(0, items.count - active - inactive)
            let retention = items.isEmpty ? 0.0 : (Double(active) * 100.0 / Double(items.count))
            let turnover = items.isEmpty ? 0.0 : (Double(inactive) * 100.0 / Double(items.count))

            var byProject: [String: (total: Int, active: Int, inactive: Int)] = [:]
            var byRecruiter: [String: (total: Int, active: Int, inactive: Int)] = [:]
            for w in items {
                let pName = w.duAn.isEmpty ? "Chưa phân dự án" : w.duAn
                var pCur = byProject[pName] ?? (0, 0, 0)
                pCur.total += 1
                if w.isWorking { pCur.active += 1 } else { pCur.inactive += 1 }
                byProject[pName] = pCur

                let rName = w.nguoiTuyen.isEmpty ? "Chưa gán người tuyển" : w.nguoiTuyen
                var rCur = byRecruiter[rName] ?? (0, 0, 0)
                rCur.total += 1
                if w.isWorking { rCur.active += 1 } else { rCur.inactive += 1 }
                byRecruiter[rName] = rCur
            }

            let projects = byProject.sorted { $0.value.total > $1.value.total }.enumerated().map { idx, kv in
                ProjectBreakdownRow(
                    id: "proj_\(idx)",
                    projectName: kv.key,
                    totalCount: kv.value.total,
                    activeCount: kv.value.active,
                    inactiveCount: kv.value.inactive,
                    newCount: 0,
                    activeSharePct: active > 0 ? (Double(kv.value.active) * 100.0 / Double(active)) : 0.0
                )
            }
            let recruiters = byRecruiter.sorted { $0.value.total > $1.value.total }.enumerated().map { idx, kv in
                PersonPerformanceRow(
                    id: "rec_\(idx)",
                    code: kv.key,
                    name: kv.key,
                    totalCount: kv.value.total,
                    activeCount: kv.value.active,
                    inactiveCount: kv.value.inactive,
                    activeRatePct: kv.value.total > 0 ? (Double(kv.value.active) * 100.0 / Double(kv.value.total)) : 0.0
                )
            }

            return .success(
                GeneralOverviewData(
                    scope: scope,
                    kpis: GeneralOverviewKpis(
                        totalHeadcount: total,
                        activeCount: active,
                        inactiveCount: inactive,
                        interviewCount: interview,
                        newCount: 0,
                        resignedInPeriodCount: 0,
                        retentionRate: retention,
                        turnoverRate: turnover
                    ),
                    projects: projects,
                    projectsTotal: projects.count,
                    projectsPage: 1,
                    projectsTotalPages: 1,
                    recruiters: recruiters,
                    managers: []
                ),
                statusCode: statusCode
            )
        }

        return .failure(
            message: response.errorMessage ?? "Không tải được báo cáo tổng quát.",
            httpStatus: response.statusCode,
            errorCode: response.errorCode
        )
    }

    public func fetchWorkforceDeepMetrics(
        scope: String = "all",
        startDate: String? = nil,
        endDate: String? = nil
    ) async -> MiniBiApiResult<WorkforceDeepMetricsData> {
        var queryItems: [URLQueryItem] = [URLQueryItem(name: "mode", value: "deep")]
        let cleanStart = (startDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEnd = (endDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanStart.isEmpty && !cleanEnd.isEmpty {
            queryItems.append(URLQueryItem(name: "from", value: cleanStart))
            queryItems.append(URLQueryItem(name: "to", value: cleanEnd))
        } else {
            queryItems.append(URLQueryItem(name: "range", value: "all"))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/reporting/workforce-deep",
            queryItems: queryItems
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success(WorkforceDeepMetricsData(), statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được báo cáo chuyên sâu.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["report"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root

        func parseBuckets(_ key: String) -> [DistributionBucket] {
            let arr = dataObj.optObjectArray(key)
            return arr.enumerated().map { idx, b in
                DistributionBucket(
                    id: "\(key)_\(idx)",
                    label: b.optString("label", "name", "bucket", "labor_type_display", "project_display", defaultValue: "Nhóm \(idx + 1)"),
                    count: b.optInt("count", "value", "total", "total_workers"),
                    percentage: b.optDouble("percentage", "pct", "share")
                )
            }
        }

        let deepData = WorkforceDeepMetricsData(
            tenureBuckets: parseBuckets("tenure_buckets").ifEmpty(parseBuckets("tenure")).ifEmpty(parseBuckets("by_tenure")),
            ageBuckets: parseBuckets("age_buckets").ifEmpty(parseBuckets("age")).ifEmpty(parseBuckets("by_age")),
            genderBuckets: parseBuckets("gender_buckets").ifEmpty(parseBuckets("gender")).ifEmpty(parseBuckets("by_labor_type")),
            contractBuckets: parseBuckets("contract_buckets").ifEmpty(parseBuckets("contract_status")).ifEmpty(parseBuckets("by_status")),
            resignReasonBuckets: parseBuckets("resign_reason_buckets").ifEmpty(parseBuckets("resign_reasons"))
        )
        return .success(deepData, statusCode: response.statusCode)
    }

    public func fetchTeamReport(
        startDate: String? = nil,
        endDate: String? = nil,
        teamCode: String? = nil
    ) async -> MiniBiApiResult<TeamReportData> {
        let response = await httpClient.request(
            method: "GET",
            path: "/api/reporting/team-report"
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success(TeamReportData(teams: []), statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được báo cáo theo nhóm.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["report"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let teamsRaw = dataObj.optObjectArray("teams").ifEmpty(dataObj.optObjectArray("items"))

        let teams: [TeamSummaryRow] = teamsRaw.enumerated().map { idx, t in
            let code = t.optString("team_code", "team_id", "code", "id", defaultValue: "team_\(idx)")
            let membersRaw = t.optObjectArray("members").ifEmpty(t.optObjectArray("recruiters"))
            let members: [PersonPerformanceRow] = membersRaw.enumerated().map { mIdx, m in
                let mTotal = m.optInt("total_workers", "total_count", "total")
                let mActive = m.optInt("on_count", "active_count", "active")
                let mInactive = m.optInt("off_count", "inactive_count", "inactive")
                let rate = mTotal > 0 ? (Double(mActive) * 100.0 / Double(mTotal)) : m.optDouble("active_rate_pct", "active_rate")
                return PersonPerformanceRow(
                    id: m.optString("recruiter_id", "code", "username", defaultValue: "\(code)_m_\(mIdx)"),
                    code: m.optString("recruiter_id", "code", "username"),
                    name: m.optString("recruiter_display", "display_name", "name", "full_name", defaultValue: "Thành viên \(mIdx + 1)"),
                    totalCount: mTotal,
                    activeCount: mActive,
                    inactiveCount: mInactive,
                    activeRatePct: rate
                )
            }
            let tTotal = t.optInt("total_workers", "total_count", "total")
            let tActive = t.optInt("on_count", "active_workers", "active_count", "active")
            let tInactive = t.optInt("off_count", "inactive_workers", "inactive_count", "inactive")
            let tRate = tTotal > 0 ? (Double(tActive) * 100.0 / Double(tTotal)) : t.optDouble("active_rate_pct", "active_rate")
            return TeamSummaryRow(
                id: code,
                teamCode: code,
                teamName: t.optString("team_name", "name", defaultValue: code),
                leaderName: t.optString("leader_display", "leader_name", "leader"),
                memberCount: t.optInt("member_count", defaultValue: members.count),
                totalWorkers: tTotal,
                activeWorkers: tActive,
                inactiveWorkers: tInactive,
                newWorkers: t.optInt("new_workers", "new_count"),
                activeRatePct: tRate,
                members: members
            )
        }

        return .success(TeamReportData(teams: teams), statusCode: response.statusCode)
    }

    public func fetchDuplicateCccdReport(
        search: String = "",
        page: Int = 1,
        pageSize: Int = 20
    ) async -> MiniBiApiResult<DuplicateCccdReportData> {
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "page", value: String(max(page, 1))),
            URLQueryItem(name: "page_size", value: String(min(max(pageSize, 1), 100))),
            URLQueryItem(name: "managed_only", value: "false")
        ]
        let cleanSearch = search.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanSearch.isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: cleanSearch))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/direct-entry/duplicate-cccd-report",
            queryItems: queryItems
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success(DuplicateCccdReportData(), statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được báo cáo CCCD trùng.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["report"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let groupsRaw = dataObj.optObjectArray("groups").ifEmpty(dataObj.optObjectArray("items"))

        let groups: [DuplicateCccdGroup] = groupsRaw.enumerated().map { idx, g in
            let cccd = g.optString("national_id", "cccd", "so_cccd", defaultValue: "group_\(idx)")
            let epsRaw = g.optObjectArray("episodes").ifEmpty(g.optObjectArray("records")).ifEmpty(g.optObjectArray("entries"))
            let episodes: [DuplicateCccdEpisode] = epsRaw.enumerated().map { eIdx, e in
                let epId = e.optInt64("episode_id", "id") ?? Int64(idx * 1000 + eIdx)
                return DuplicateCccdEpisode(
                    id: "\(cccd)_\(epId)",
                    episodeId: epId,
                    maNld: e.optString("entry_id", "employee_code", "ma_nld", "worker_code"),
                    hoTen: e.optString("display_name", "ho_ten", "full_name"),
                    duAn: e.optString("project_display", "du_an", "project_name"),
                    quanLy: e.optString("quan_ly", "manager_name"),
                    nguoiTuyen: e.optString("recruiter_display", "nguoi_tuyen", "recruiter_name"),
                    trangThai: e.optString("employment_status", "trang_thai", "status"),
                    ngayVaoLam: e.optString("first_work_date", "ngay_vao_lam", "start_date"),
                    canSelfWithdraw: e.optBool("can_self_withdraw", "allow_withdraw", defaultValue: false)
                )
            }
            return DuplicateCccdGroup(
                id: cccd,
                cccd: cccd,
                workerCount: g.optInt("worker_count", "count", defaultValue: episodes.count),
                activeCount: g.optInt("active_count", "on_count", "active"),
                episodes: episodes
            )
        }

        let totalGroups = dataObj.optInt("total_groups", "total", defaultValue: groups.count)
        let currentPage = dataObj.optInt("page", defaultValue: page)
        let totalPages = max(1, dataObj.optInt("total_pages", defaultValue: 1))

        return .success(
            DuplicateCccdReportData(
                totalGroups: totalGroups,
                groups: groups,
                page: currentPage,
                totalPages: totalPages
            ),
            statusCode: response.statusCode
        )
    }

    public func selfWithdrawDuplicateEpisode(
        episodeId: Int64,
        cccd: String,
        maNld: String
    ) async -> MiniBiApiResult<Bool> {
        let body: [String: Any] = [
            "entry_id": maNld,
            "episode_id": episodeId,
            "cccd": cccd,
            "ma_nld": maNld
        ]
        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/duplicate-cccd-report/self-withdraw",
            jsonBody: body
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Rút hồ sơ trùng thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(true, statusCode: response.statusCode)
    }

    // MARK: - Parsers

    public static func parseObligationItem(
        _ obj: [String: Any],
        fallbackRoot: [String: Any]? = nil
    ) -> WorkReportObligationItem {
        let obligationId = obj.optInt64("obligation_id", "id")
            ?? fallbackRoot?.optInt64("obligation_id", "id")
            ?? 0
        let submissionId = obj.optInt64("submission_id")
            ?? (obj["submission"] as? [String: Any])?.optInt64("id", "submission_id")
            ?? fallbackRoot?.optInt64("submission_id")

        let templateObj = (obj["template"] as? [String: Any]) ?? (fallbackRoot?["template"] as? [String: Any])
        let submissionObj = (obj["submission"] as? [String: Any]) ?? (fallbackRoot?["submission"] as? [String: Any])

        let templateCode = obj.optString("template_code", defaultValue: templateObj?.optString("code", "template_code") ?? "")
        let templateName = obj.optString("template_name", "title", defaultValue: templateObj?.optString("name", "title") ?? "Báo cáo công việc")
        let periodType = obj.optString("period_type", defaultValue: templateObj?.optString("period_type") ?? "WEEKLY")
        let periodKey = obj.optString("period_key", "period")
        let dueAt = obj.optNullableString("due_at", "deadline_at")
        let submissionState = obj.optString(
            "submission_state",
            "state",
            "status",
            defaultValue: submissionObj?.optString("state", "status", defaultValue: WorkReportsContract.stateNotStarted)
                ?? WorkReportsContract.stateNotStarted
        ).uppercased()

        // Strictly read `is_overdue` from server (backward compatible default `false`)
        let isOverdue = obj.optBool(
            "is_overdue",
            defaultValue: fallbackRoot?.optBool("is_overdue", defaultValue: false) ?? false
        )

        let version = obj.optInt(
            "version",
            "submission_version",
            defaultValue: submissionObj?.optInt("version", defaultValue: 1) ?? 1
        )

        let submittedAt = obj.optNullableString("submitted_at")
            ?? submissionObj?.optNullableString("submitted_at")
        let reviewedAt = obj.optNullableString("reviewed_at")
            ?? submissionObj?.optNullableString("reviewed_at")
        let reviewerName = obj.optNullableString("reviewer_name", "reviewed_by_name")
            ?? submissionObj?.optNullableString("reviewer_name", "reviewed_by_name")
        let reviewComment = obj.optNullableString("review_comment", "reject_reason", "comment")
            ?? submissionObj?.optNullableString("review_comment", "reject_reason", "comment")
        let unitName = obj.optNullableString("unit_name", "department_name")

        let schemaRaw: Any? = obj["schema"]
            ?? obj["form_schema"]
            ?? templateObj?["schema"]
            ?? templateObj?["form_schema"]
            ?? fallbackRoot?["schema"]
            ?? fallbackRoot?["form_schema"]
        let sections = WorkReportsContract.parseDynamicSections(schemaRaw)

        let rawPayloadDict = (obj["form_payload"] as? [String: Any])
            ?? (obj["payload"] as? [String: Any])
            ?? (obj["answers"] as? [String: Any])
            ?? (submissionObj?["form_payload"] as? [String: Any])
            ?? (submissionObj?["payload"] as? [String: Any])
            ?? (submissionObj?["answers"] as? [String: Any])
            ?? [:]

        var formPayload: [String: String] = [:]
        for (k, v) in rawPayloadDict {
            if let s = v as? String {
                formPayload[k] = s
            } else if let n = v as? NSNumber {
                formPayload[k] = n.stringValue
            }
        }

        return WorkReportObligationItem(
            id: obligationId,
            submissionId: submissionId,
            templateCode: templateCode,
            templateName: templateName,
            periodType: periodType,
            periodKey: periodKey,
            dueAt: dueAt,
            submissionState: submissionState,
            isOverdue: isOverdue,
            version: max(1, version),
            submittedAt: submittedAt,
            reviewedAt: reviewedAt,
            reviewerName: reviewerName,
            reviewComment: reviewComment,
            unitName: unitName,
            sections: sections,
            formPayload: formPayload
        )
    }

    public static func parseReviewItem(_ obj: [String: Any]) -> WorkReportReviewItem {
        let submissionId = obj.optInt64("submission_id", "id") ?? 0
        let obligationId = obj.optInt64("obligation_id") ?? submissionId
        let templateName = obj.optString("template_name", "title", defaultValue: "Báo cáo công việc")
        let periodType = obj.optString("period_type", defaultValue: "WEEKLY")
        let periodKey = obj.optString("period_key", "period")
        let reporterName = obj.optString("reporter_name", "author_name", "user_name", defaultValue: "Nhân sự")
        let reporterCode = obj.optString("reporter_code", "author_code", "username")
        let unitName = obj.optString("unit_name", "department_name")
        let submittedAt = obj.optNullableString("submitted_at", "created_at")
        let submissionState = obj.optString("submission_state", "state", "status", defaultValue: WorkReportsContract.stateSubmitted)
        let version = max(1, obj.optInt("version", defaultValue: 1))
        let sections = WorkReportsContract.parseDynamicSections(obj["schema"] ?? obj["form_schema"])

        let rawPayloadDict = (obj["form_payload"] as? [String: Any])
            ?? (obj["payload"] as? [String: Any])
            ?? (obj["answers"] as? [String: Any])
            ?? [:]
        var formPayload: [String: String] = [:]
        for (k, v) in rawPayloadDict {
            if let s = v as? String {
                formPayload[k] = s
            } else if let n = v as? NSNumber {
                formPayload[k] = n.stringValue
            }
        }

        return WorkReportReviewItem(
            id: submissionId,
            obligationId: obligationId,
            templateName: templateName,
            periodType: periodType,
            periodKey: periodKey,
            reporterName: reporterName,
            reporterCode: reporterCode,
            unitName: unitName,
            submittedAt: submittedAt,
            submissionState: submissionState,
            version: version,
            sections: sections,
            formPayload: formPayload
        )
    }
}

private extension Array {
    func ifEmpty(_ fallback: @autoclosure () -> [Element]) -> [Element] {
        isEmpty ? fallback() : self
    }
}
