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
        case stateSubmitted, "PENDING_REVIEW":
            return "Đã nộp · Chờ duyệt"
        case stateApproved, "COMPLETED":
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

        func parseFieldObject(_ fObj: [String: Any], fallbackIndex: Int) -> (sectionKey: String, sortOrder: Int, field: WorkReportFieldSchema)? {
            let fieldId = fObj.optString("field_key", "id", "key", "name", "field_id")
            guard !fieldId.isEmpty else { return nil }
            let sectionKey = fObj.optString("section_key", "section_id", "group_key", defaultValue: "default")
            let sortOrder = fObj.optInt("sort_order", "order", defaultValue: (fallbackIndex + 1) * 10)
            let label = fObj.optString("label", "title", "name", defaultValue: fieldId)
            let type = fObj.optString("field_type", "type", defaultValue: "text").lowercased()
            let required = fObj.optBool("required", "is_required", defaultValue: false)
            let placeholder = fObj.optNullableString("description", "placeholder", "hint", "help_text")
            let defaultValue: String? = {
                if let rawDefault = fObj["default_value"], !(rawDefault is NSNull) {
                    if let b = rawDefault as? Bool {
                        return b ? "true" : "false"
                    } else if let n = rawDefault as? NSNumber {
                        if CFGetTypeID(n) == CFBooleanGetTypeID() {
                            return n.boolValue ? "true" : "false"
                        }
                        return n.stringValue
                    } else if let s = rawDefault as? String {
                        return s
                    }
                }
                return nil
            }()
            var options: [WorkReportFieldOption] = []
            if let optObjs = fObj["options"] as? [[String: Any]] {
                options = optObjs.compactMap { o in
                    let val = o.optString("option_key", "value", "id", "code", "key", "label")
                    guard !val.isEmpty else { return nil }
                    let lbl = o.optString("label", "name", "title", "value", defaultValue: val)
                    return WorkReportFieldOption(value: val, label: lbl)
                }
            } else if let optStrs = fObj["options"] as? [String] {
                options = optStrs.map { WorkReportFieldOption(value: $0, label: $0) }
            }
            let schema = WorkReportFieldSchema(
                id: fieldId,
                label: label,
                type: type,
                required: required,
                placeholder: placeholder,
                defaultValue: defaultValue,
                options: options,
                min: fObj.optNullableDouble("min", "min_value"),
                max: fObj.optNullableDouble("max", "max_value")
            )
            return (sectionKey, sortOrder, schema)
        }

        if let dict {
            let rawSections = dict.optObjectArray("sections").ifEmpty(dict.optObjectArray("groups"))
            let topLevelFieldsRaw = dict.optObjectArray("fields").ifEmpty(dict.optObjectArray("items"))

            var parsedTopFields: [(sectionKey: String, sortOrder: Int, field: WorkReportFieldSchema)] = []
            for (idx, fObj) in topLevelFieldsRaw.enumerated() {
                if let item = parseFieldObject(fObj, fallbackIndex: idx) {
                    parsedTopFields.append(item)
                }
            }

            var result: [WorkReportSectionSchema] = []
            var consumedFieldIds: Set<String> = []

            let sortedSections = rawSections.enumerated().sorted { lhs, rhs in
                let lOrder = lhs.element.optInt("sort_order", "order", defaultValue: (lhs.offset + 1) * 10)
                let rOrder = rhs.element.optInt("sort_order", "order", defaultValue: (rhs.offset + 1) * 10)
                return lOrder == rOrder ? lhs.offset < rhs.offset : lOrder < rOrder
            }

            for (secIdx, secObj) in sortedSections {
                let secId = secObj.optString("section_key", "id", "section_id", "key", defaultValue: "sec_\(secIdx)")
                let secTitle = secObj.optString("label", "title", "name", defaultValue: "Phần \(secIdx + 1)")
                let secDesc = secObj.optNullableString("description", "subtitle")

                var secFieldsWithOrder: [(sortOrder: Int, field: WorkReportFieldSchema)] = []
                let nestedRaw = secObj.optObjectArray("fields").ifEmpty(secObj.optObjectArray("items"))
                for (fIdx, fObj) in nestedRaw.enumerated() {
                    if let parsed = parseFieldObject(fObj, fallbackIndex: fIdx) {
                        secFieldsWithOrder.append((parsed.sortOrder, parsed.field))
                        consumedFieldIds.insert(parsed.field.id)
                    }
                }

                for topField in parsedTopFields where topField.sectionKey == secId && !consumedFieldIds.contains(topField.field.id) {
                    secFieldsWithOrder.append((topField.sortOrder, topField.field))
                    consumedFieldIds.insert(topField.field.id)
                }

                let orderedFields = secFieldsWithOrder.sorted { $0.sortOrder < $1.sortOrder }.map(\.field)
                if !orderedFields.isEmpty {
                    result.append(
                        WorkReportSectionSchema(
                            id: secId,
                            title: secTitle,
                            description: secDesc,
                            fields: orderedFields
                        )
                    )
                }
            }

            let remainingFields = parsedTopFields
                .filter { !consumedFieldIds.contains($0.field.id) }
                .sorted { $0.sortOrder < $1.sortOrder }
                .map(\.field)

            if !remainingFields.isEmpty {
                if result.count == 1 && rawSections.count == 1 {
                    let only = result[0]
                    result[0] = WorkReportSectionSchema(
                        id: only.id,
                        title: only.title,
                        description: only.description,
                        fields: only.fields + remainingFields
                    )
                } else {
                    result.append(
                        WorkReportSectionSchema(
                            id: "default",
                            title: "Nội dung báo cáo",
                            description: nil,
                            fields: remainingFields
                        )
                    )
                }
            }

            return result
        }

        if let arr = schemaRaw as? [[String: Any]] {
            let fields = arr.enumerated().compactMap { idx, fObj in
                parseFieldObject(fObj, fallbackIndex: idx)?.field
            }
            if !fields.isEmpty {
                return [
                    WorkReportSectionSchema(
                        id: "default",
                        title: "Nội dung báo cáo",
                        description: nil,
                        fields: fields
                    )
                ]
            }
        }

        return []
    }

    public static func normalizeIsoDateInput(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let dashParts = trimmed.split(separator: "-")
        if dashParts.count == 3, dashParts[0].count == 4, dashParts[1].count == 2, dashParts[2].count == 2,
           let m = Int(dashParts[1]), (1...12).contains(m),
           let d = Int(dashParts[2]), (1...31).contains(d) {
            return trimmed
        }
        let slashParts = trimmed.split(separator: "/")
        if slashParts.count == 3, slashParts[2].count == 4,
           let d = Int(slashParts[0]), (1...31).contains(d),
           let m = Int(slashParts[1]), (1...12).contains(m) {
            let dd = String(format: "%02d", d)
            let mm = String(format: "%02d", m)
            return "\(slashParts[2])-\(mm)-\(dd)"
        }
        return nil
    }

    /// Validates dynamic work-report form fields before final submit (`Nộp báo cáo`).
    /// Note: Boolean toggle fields (`Có / Đạt` vs `Không`) are always valid whether `true` or `false` (`Không`),
    /// matching Android's `DynamicWorkReportFormCard.buildPayloadJson(strictForSubmit = true)`.
    public static func validateFormPayloadForSubmit(
        formPayload: [String: String],
        sections: [WorkReportSectionSchema]
    ) -> String? {
        let allFields = sections.flatMap(\.fields)
        for field in allFields {
            let raw = (formPayload[field.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let typeLower = field.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let isBoolean = field.isBooleanType
            if field.required && raw.isEmpty && !isBoolean {
                return "Vui lòng nhập trường bắt buộc: \(field.label)"
            }
            if raw.isEmpty { continue }
            switch typeLower {
            case "integer":
                if Int64(raw) == nil {
                    return "Trường \"\(field.label)\" phải là số nguyên hợp lệ."
                }
            case "decimal", "number", "numeric", "float":
                let normalized = raw.replacingOccurrences(of: ",", with: ".")
                if let d = Double(normalized), d.isFinite {
                    // valid
                } else {
                    return "Trường \"\(field.label)\" phải là số hợp lệ."
                }
            case "date":
                if normalizeIsoDateInput(raw) == nil {
                    return "Trường \"\(field.label)\" phải có định dạng ngày YYYY-MM-DD hoặc DD/MM/YYYY."
                }
            case "single_select", "select", "dropdown", "radio":
                if !field.options.isEmpty && !field.options.contains(where: { $0.value == raw }) {
                    return "Giá trị đã chọn cho \"\(field.label)\" không hợp lệ."
                }
            case "multi_select", "multiselect":
                let items = raw
                    .split(separator: ",")
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
                if field.required && items.isEmpty {
                    return "Vui lòng chọn ít nhất 1 mục cho: \(field.label)"
                }
            default:
                break
            }
        }
        return nil
    }

    public static func buildTypedPayloadJson(
        formPayload: [String: String],
        sections: [WorkReportSectionSchema]
    ) -> [String: Any] {
        var payloadJson: [String: Any] = [:]
        var processedKeys: Set<String> = []

        for sec in sections {
            for field in sec.fields {
                let key = field.id
                processedKeys.insert(key)
                let rawVal = formPayload[key] ?? field.defaultValue ?? ""
                let trimmed = rawVal.trimmingCharacters(in: .whitespacesAndNewlines)
                let fieldType = field.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

                switch fieldType {
                case "boolean", "bool", "checkbox", "switch":
                    let lower = trimmed.lowercased()
                    payloadJson[key] = ["true", "1", "yes", "có"].contains(lower)

                case "integer":
                    if trimmed.isEmpty { continue }
                    if let intVal = Int64(trimmed) {
                        payloadJson[key] = intVal
                    }

                case "number", "numeric", "decimal", "float":
                    if trimmed.isEmpty { continue }
                    let normalized = trimmed.replacingOccurrences(of: ",", with: ".")
                    if let intVal = Int64(normalized), !normalized.contains(".") {
                        payloadJson[key] = intVal
                    } else if let dblVal = Double(normalized), dblVal.isFinite {
                        payloadJson[key] = dblVal
                    }

                case "date":
                    if trimmed.isEmpty { continue }
                    payloadJson[key] = normalizeIsoDateInput(trimmed) ?? trimmed

                case "multi_select", "multiselect":
                    if trimmed.isEmpty {
                        if field.required {
                            payloadJson[key] = [String]()
                        }
                    } else if trimmed.hasPrefix("["),
                              let data = trimmed.data(using: .utf8),
                              let arr = try? JSONSerialization.jsonObject(with: data) as? [String] {
                        payloadJson[key] = arr.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
                    } else {
                        let parts = trimmed
                            .split(separator: ",")
                            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                            .filter { !$0.isEmpty }
                        payloadJson[key] = parts
                    }

                default:
                    if !trimmed.isEmpty {
                        payloadJson[key] = trimmed
                    }
                }
            }
        }

        // Preserve any extra/untyped keys when sections is empty or contains custom keys
        for (key, rawVal) in formPayload where !processedKeys.contains(key) {
            let trimmed = rawVal.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                payloadJson[key] = trimmed
            }
        }
        return payloadJson
    }

    public static func buildSubmissionMutationBody(
        payloadJson: [String: Any],
        expectedVersion: Int?,
        idempotencyKey: String
    ) -> [String: Any] {
        return [
            "payload_json": payloadJson,
            "expected_version": expectedVersion ?? NSNull(),
            "idempotency_key": idempotencyKey
        ]
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
        await fetchObligationDetail(obligationIdUuid: String(obligationId))
    }

    public func fetchObligationDetail(
        obligationIdUuid: String,
        fallback: WorkReportObligationItem? = nil
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        let encoded = obligationIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? obligationIdUuid
        let response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/obligations/\(encoded)"
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được chi tiết báo cáo công việc.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let obObj = (dataObj["obligation"] as? [String: Any])
            ?? (root["obligation"] as? [String: Any])
            ?? (root["item"] as? [String: Any])
            ?? dataObj

        let parsed = Self.parseObligationItem(obObj, fallbackRoot: dataObj, fallback: fallback)
        return .success(parsed, statusCode: response.statusCode)
    }

    // MARK: - 2. Save Draft & Submit Report (with `WORK_REPORT_DEADLINE_PASSED` handling)

    public func saveDraft(
        submissionId: Int64,
        version: Int,
        formPayload: [String: String]
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        await saveDraft(
            obligationIdUuid: String(submissionId),
            expectedVersion: version > 0 ? version : nil,
            formPayload: formPayload,
            sections: []
        )
    }

    public func saveDraft(
        obligationIdUuid: String,
        expectedVersion: Int?,
        formPayload: [String: String],
        sections: [WorkReportSectionSchema] = [],
        fallback: WorkReportObligationItem? = nil
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        let typedPayload = WorkReportsContract.buildTypedPayloadJson(formPayload: formPayload, sections: sections)
        let idempotencyKey = UUID().uuidString.lowercased()
        let body = WorkReportsContract.buildSubmissionMutationBody(
            payloadJson: typedPayload,
            expectedVersion: expectedVersion,
            idempotencyKey: idempotencyKey
        )

        let encoded = obligationIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? obligationIdUuid
        let response = await httpClient.request(
            method: "POST",
            path: "/api/work-reports/submissions/\(encoded)/draft",
            jsonBody: body,
            additionalHeaders: ["Idempotency-Key": idempotencyKey]
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

        if case .success(let refreshed, _) = await fetchObligationDetail(obligationIdUuid: obligationIdUuid, fallback: fallback) {
            return .success(refreshed, statusCode: response.statusCode)
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let obj = (dataObj["obligation"] as? [String: Any])
            ?? (dataObj["submission"] as? [String: Any])
            ?? (root["submission"] as? [String: Any])
            ?? (root["obligation"] as? [String: Any])
            ?? dataObj
        var item = Self.parseObligationItem(obj, fallbackRoot: dataObj, fallback: fallback)
        if item.sections.isEmpty && !sections.isEmpty {
            item.sections = sections
        }
        if item.formPayload.isEmpty {
            item.formPayload = formPayload
        }
        return .success(item, statusCode: response.statusCode)
    }

    public func submitReport(
        submissionId: Int64,
        version: Int,
        formPayload: [String: String]
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        await submitReport(
            obligationIdUuid: String(submissionId),
            expectedVersion: version > 0 ? version : nil,
            formPayload: formPayload,
            sections: []
        )
    }

    public func submitReport(
        obligationIdUuid: String,
        expectedVersion: Int?,
        formPayload: [String: String],
        sections: [WorkReportSectionSchema] = [],
        fallback: WorkReportObligationItem? = nil
    ) async -> MiniBiApiResult<WorkReportObligationItem> {
        let typedPayload = WorkReportsContract.buildTypedPayloadJson(formPayload: formPayload, sections: sections)
        let idempotencyKey = UUID().uuidString.lowercased()
        let body = WorkReportsContract.buildSubmissionMutationBody(
            payloadJson: typedPayload,
            expectedVersion: expectedVersion,
            idempotencyKey: idempotencyKey
        )

        let encoded = obligationIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? obligationIdUuid
        let response = await httpClient.request(
            method: "POST",
            path: "/api/work-reports/submissions/\(encoded)/submit",
            jsonBody: body,
            additionalHeaders: ["Idempotency-Key": idempotencyKey]
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

        if case .success(let refreshed, _) = await fetchObligationDetail(obligationIdUuid: obligationIdUuid, fallback: fallback) {
            return .success(refreshed, statusCode: response.statusCode)
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let obj = (dataObj["obligation"] as? [String: Any])
            ?? (dataObj["submission"] as? [String: Any])
            ?? (root["submission"] as? [String: Any])
            ?? (root["obligation"] as? [String: Any])
            ?? dataObj
        var item = Self.parseObligationItem(obj, fallbackRoot: dataObj, fallback: fallback)
        if item.sections.isEmpty && !sections.isEmpty {
            item.sections = sections
        }
        if item.formPayload.isEmpty {
            item.formPayload = formPayload
        }
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

    public func fetchPendingReviews(status: String = "SUBMITTED") async -> MiniBiApiResult<[WorkReportReviewItem]> {
        let resolvedState = status.uppercased() == "PENDING" ? "SUBMITTED" : status.uppercased()
        let response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/reviews",
            queryItems: [
                URLQueryItem(name: "state", value: resolvedState),
                URLQueryItem(name: "page", value: "1"),
                URLQueryItem(name: "page_size", value: "50")
            ]
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
        let rawList = root.optObjectArray("items")
            .ifEmpty(root.optObjectArray("data"))
            .ifEmpty(root.optObjectArray("reviews"))
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
        await decideReview(
            submissionIdUuid: String(submissionId),
            decision: decision,
            comment: comment,
            expectedVersion: expectedVersion
        )
    }

    public func decideReview(
        submissionIdUuid: String,
        decision: String,
        comment: String,
        expectedVersion: Int
    ) async -> MiniBiApiResult<Bool> {
        let normalizedDecision: String = {
            let upper = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if upper == "APPROVE" || upper == "APPROVED" { return "APPROVED" }
            if upper == "REJECT" || upper == "REJECTED" { return "REJECTED" }
            return upper
        }()
        let cleanComment = comment.trimmingCharacters(in: .whitespacesAndNewlines)
        let idempotencyKey = UUID().uuidString.lowercased()
        let body: [String: Any] = [
            "decision": normalizedDecision,
            "reason": cleanComment.isEmpty ? NSNull() : cleanComment,
            "expected_version": max(1, expectedVersion),
            "idempotency_key": idempotencyKey
        ]
        let encoded = submissionIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? submissionIdUuid
        let response = await httpClient.request(
            method: "POST",
            path: "/api/work-reports/reviews/\(encoded)/decision",
            jsonBody: body,
            additionalHeaders: ["Idempotency-Key": idempotencyKey]
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
        var response = await httpClient.request(method: "GET", path: "/api/work-reports/templates/selectable")
        if response.statusCode == 404 {
            response = await httpClient.request(method: "GET", path: "/api/work-reports/templates")
        }
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
        let rawList = root.optObjectArray("items")
            .ifEmpty(root.optObjectArray("data"))
            .ifEmpty(root.optObjectArray("templates"))
            .ifEmpty((root["data"] as? [String: Any])?.optObjectArray("templates") ?? [])

        let items = rawList.compactMap { obj -> WorkReportTemplateOption? in
            let code = obj.optString("code", "template_code", "template_id", "id")
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

        var response = await httpClient.request(
            method: "GET",
            path: "/api/work-reports/analytics/overview",
            queryItems: queryItems
        )
        if response.statusCode == 404 {
            response = await httpClient.request(
                method: "GET",
                path: "/api/work-reports/analytics/summary",
                queryItems: queryItems
            )
        }
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
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let kpiObj = (dataObj["summary"] as? [String: Any]) ?? (dataObj["kpis"] as? [String: Any]) ?? dataObj

        let totalOb = kpiObj.optInt("expected_count", "total_obligations", "total")
        let submittedCount = kpiObj.optInt("submitted_count", "submitted")
        let approvedCount = kpiObj.optInt("approved_count", "approved")
        let rejectedCount = kpiObj.optInt("rejected_count", "rejected")
        let overdueCount = kpiObj.optInt("overdue_count", "overdue")
        let completionRate = kpiObj.optDouble("completion_rate", "submitted_rate")
        let onTimeRate = kpiObj.optDouble("approval_rate", "on_time_rate", "ontime_rate")

        let byUnitRaw = dataObj.optObjectArray("by_unit").ifEmpty(dataObj.optObjectArray("units"))
        let byUnit: [WorkReportAnalyticsGroupRow] = byUnitRaw.enumerated().map { idx, u in
            WorkReportAnalyticsGroupRow(
                id: u.optString("org_unit_id", "unit_code", "id", defaultValue: "unit_\(idx)"),
                groupLabel: u.optString("unit_name", "label", "name", defaultValue: "Đơn vị \(idx + 1)"),
                totalObligations: u.optInt("expected_count", "total_obligations", "total"),
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
                totalObligations: t.optInt("expected_count", "total_obligations", "total"),
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

    public static func buildWorkforceDeepQueryItems(startDate: String?, endDate: String?) -> [URLQueryItem] {
        var queryItems: [URLQueryItem] = [URLQueryItem(name: "mode", value: "deep")]
        let cleanStart = (startDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEnd = (endDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanStart.isEmpty && cleanEnd.isEmpty {
            queryItems.append(URLQueryItem(name: "range", value: "all"))
        } else {
            if !cleanStart.isEmpty {
                queryItems.append(URLQueryItem(name: "from", value: cleanStart))
            }
            if !cleanEnd.isEmpty {
                queryItems.append(URLQueryItem(name: "to", value: cleanEnd))
            }
        }
        return queryItems
    }

    private static func normalizeWorkerDateIso(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let prefix10 = String(trimmed.prefix(10))
        let parts = prefix10.split(separator: "/")
        if parts.count == 3, parts[0].count == 2, parts[1].count == 2, parts[2].count == 4 {
            return "\(parts[2])-\(parts[1])-\(parts[0])"
        }
        return prefix10
    }

    private func fetchMergedWorkersForScopedFallback(
        preferredScope: String,
        startDate: String? = nil,
        endDate: String? = nil
    ) async -> (items: [WorkerItem], total: Int, statusCode: Int)? {
        let norm = preferredScope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let scopesToTry: [String] = {
            switch norm {
            case "", "all":
                return ["all", "team", "managed", "recruited"]
            case "team":
                return ["team", "managed", "recruited"]
            case "own", "personal":
                return ["managed", "recruited"]
            default:
                return [norm, "team", "managed", "recruited"]
            }
        }()

        let cleanStart = (startDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEnd = (endDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let hasDateFilter = !cleanStart.isEmpty || !cleanEnd.isEmpty

        var seenIds: Set<String> = []
        var merged: [WorkerItem] = []
        var reportedTotal = 0
        var anySuccessStatus: Int?

        for sc in scopesToTry {
            let res = await MiniBiRepository.shared.fetchWorkers(scope: sc, pageSize: 100)
            if case .success(let wData, let status) = res {
                anySuccessStatus = status
                reportedTotal = max(reportedTotal, wData.total)
                for item in wData.items where !seenIds.contains(item.maNld) {
                    seenIds.insert(item.maNld)
                    merged.append(item)
                }
                if sc == "all" || (sc == "team" && norm == "team" && !wData.items.isEmpty) {
                    break
                }
            }
        }

        guard let statusCode = anySuccessStatus else { return nil }

        if hasDateFilter {
            let filtered = merged.filter { item in
                let dateIso = Self.normalizeWorkerDateIso(item.ngayVaoLam)
                guard !dateIso.isEmpty else { return false }
                if !cleanStart.isEmpty && dateIso < cleanStart { return false }
                if !cleanEnd.isEmpty && dateIso > cleanEnd { return false }
                return true
            }
            return (filtered, filtered.count, statusCode)
        }

        return (merged, max(reportedTotal, merged.count), statusCode)
    }

    public func fetchGeneralOverview(
        scope: String = "all",
        startDate: String? = nil,
        endDate: String? = nil,
        projectFilter: String = "ACTIVE",
        projectSearch: String = "",
        projectsPage: Int = 1,
        projectsPageSize: Int = 20
    ) async -> MiniBiApiResult<GeneralOverviewData> {
        let normalizedScope = scope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanStart = (startDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEnd = (endDate ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let hasDateFilter = !cleanStart.isEmpty || !cleanEnd.isEmpty
        let queryItems = Self.buildWorkforceDeepQueryItems(startDate: startDate, endDate: endDate)

        if normalizedScope == "all" || normalizedScope.isEmpty {
            let response = await httpClient.request(
                method: "GET",
                path: "/api/reporting/workforce-deep",
                queryItems: queryItems
            )

            if response.isSuccess, let root = response.jsonObject {
                if let wfObj = root["workforce"] as? [String: Any] {
                    let snapshotObj = (wfObj["snapshot"] as? [String: Any]) ?? [:]
                    let balanceObj = (wfObj["balance"] as? [String: Any]) ?? [:]
                    let balanceApplicable = balanceObj.optBool("applicable", defaultValue: false)
                    let newEpObj = (balanceObj["new_episodes_in_period"] as? [String: Any])
                        ?? (balanceObj["newEpisodesInPeriod"] as? [String: Any]) ?? [:]
                    let byStatusObj = (balanceObj["by_status"] as? [String: Any])
                        ?? (balanceObj["byStatus"] as? [String: Any]) ?? [:]
                    let onFlowObj = (byStatusObj["on"] as? [String: Any]) ?? [:]
                    let offFlowObj = (byStatusObj["off"] as? [String: Any]) ?? [:]
                    let waitingFlowObj = (byStatusObj["unconfirmed"] as? [String: Any]) ?? [:]
                    let retentionObj = (wfObj["retention"] as? [String: Any]) ?? [:]
                    let checkpointsObj = (retentionObj["checkpoints"] as? [String: Any]) ?? [:]
                    let d30Obj = (checkpointsObj["d30"] as? [String: Any]) ?? [:]

                    let snapshotTotal = snapshotObj.optInt("eligible_episode_total", "eligibleEpisodeTotal", "total")
                    let snapshotOn = snapshotObj.optInt("on_count", "onCount", "active_count")
                    let snapshotOff = snapshotObj.optInt("off_count", "offCount", "inactive_count")
                    let snapshotUnconfirmed = snapshotObj.optInt("unconfirmed_count", "unconfirmedCount", "interview_count")
                    let newCount = newEpObj.optInt("total", "count")
                    let resignedInPeriodCount = offFlowObj.optInt("total_inflow_count", "totalInflowCount", "count")

                    let totalWorkers: Int
                    let activeCount: Int
                    let inactiveCount: Int
                    let unconfirmedCount: Int
                    if hasDateFilter && balanceApplicable {
                        totalWorkers = newCount
                        activeCount = onFlowObj.optInt("total_inflow_count", "totalInflowCount", "count")
                        inactiveCount = resignedInPeriodCount
                        unconfirmedCount = waitingFlowObj.optInt("total_inflow_count", "totalInflowCount", "count")
                    } else {
                        totalWorkers = snapshotTotal
                        activeCount = snapshotOn
                        inactiveCount = snapshotOff
                        unconfirmedCount = snapshotUnconfirmed
                    }

                    let d30RetentionRatio = d30Obj.optNullableDouble("retention_rate", "retentionRate")
                    let retentionRate: Double = {
                        if let r = d30RetentionRatio {
                            return r <= 1.0 && r > 0.0 ? r * 100.0 : r
                        }
                        return snapshotTotal > 0 ? (Double(snapshotOn) * 100.0 / Double(snapshotTotal)) : 0.0
                    }()
                    let turnoverRate = snapshotTotal > 0
                        ? (Double(snapshotOff) * 100.0 / Double(snapshotTotal))
                        : 0.0

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

                    let dimsObj = (wfObj["dimensions"] as? [String: Any]) ?? [:]
                    let byProjectObj = (dimsObj["by_project"] as? [String: Any])
                        ?? (dimsObj["byProject"] as? [String: Any]) ?? [:]
                    let projectsBuckets = byProjectObj.optObjectArray("buckets")
                    let projectPaginationObj = (byProjectObj["pagination"] as? [String: Any]) ?? [:]
                    let totalProjectCount = max(
                        projectPaginationObj.optInt("total_count", "totalCount"),
                        projectsBuckets.count
                    )

                    let projects: [ProjectBreakdownRow] = projectsBuckets.enumerated().map { idx, p in
                        let name = p.optString("display", "project_display", "du_an", "project_name", "name", "key", defaultValue: "Dự án #\(idx + 1)")
                        let pTotal = p.optInt("eligible_episode_count", "eligibleEpisodeCount", "total_workers", "total")
                        let pActive = p.optInt("on_count", "onCount", "active_count")
                        let pInactive = p.optInt("off_count", "offCount", "inactive_count")
                        let pNew = p.optInt("unconfirmed_count", "unconfirmedCount", "new_count")
                        let share = pTotal > 0 ? (Double(pActive) * 100.0 / Double(pTotal)) : 0.0
                        return ProjectBreakdownRow(
                            id: p.optString("key", defaultValue: "\(name)_\(idx)"),
                            projectName: name,
                            totalCount: pTotal,
                            activeCount: pActive,
                            inactiveCount: pInactive,
                            newCount: pNew,
                            activeSharePct: share
                        )
                    }

                    let byRecruiterObj = (dimsObj["by_recruiter"] as? [String: Any])
                        ?? (dimsObj["byRecruiter"] as? [String: Any]) ?? [:]
                    let recruitersBuckets = byRecruiterObj.optObjectArray("buckets")
                    let recruiters: [PersonPerformanceRow] = recruitersBuckets.enumerated().map { idx, r in
                        let rTotal = r.optInt("eligible_episode_count", "eligibleEpisodeCount", "total_workers", "total")
                        let rActive = r.optInt("on_count", "onCount", "active_count")
                        let rInactive = r.optInt("off_count", "offCount", "inactive_count")
                        let rate = rTotal > 0 ? (Double(rActive) * 100.0 / Double(rTotal)) : 0.0
                        let key = r.optString("key", "recruiter_id", "code", defaultValue: "rec_\(idx)")
                        return PersonPerformanceRow(
                            id: key,
                            code: key,
                            name: r.optString("display", "recruiter_display", "name", "full_name", defaultValue: "NV #\(idx + 1)"),
                            totalCount: rTotal,
                            activeCount: rActive,
                            inactiveCount: rInactive,
                            activeRatePct: rate
                        )
                    }

                    let byTeamObj = (dimsObj["by_team"] as? [String: Any])
                        ?? (dimsObj["byTeam"] as? [String: Any]) ?? [:]
                    let teamBuckets = byTeamObj.optObjectArray("buckets")
                    let managers: [PersonPerformanceRow] = teamBuckets.enumerated().map { idx, m in
                        let mTotal = m.optInt("eligible_episode_count", "eligibleEpisodeCount", "total_workers", "total")
                        let mActive = m.optInt("on_count", "onCount", "active_count")
                        let mInactive = m.optInt("off_count", "offCount", "inactive_count")
                        let rate = mTotal > 0 ? (Double(mActive) * 100.0 / Double(mTotal)) : 0.0
                        let key = m.optString("key", "team_id", "code", defaultValue: "team_\(idx)")
                        return PersonPerformanceRow(
                            id: key,
                            code: key,
                            name: m.optString("display", "team_display", "name", defaultValue: "Nhóm #\(idx + 1)"),
                            totalCount: mTotal,
                            activeCount: mActive,
                            inactiveCount: mInactive,
                            activeRatePct: rate
                        )
                    }

                    return .success(
                        GeneralOverviewData(
                            scope: scope,
                            kpis: kpis,
                            projects: projects,
                            projectsTotal: totalProjectCount,
                            projectsPage: projectsPage,
                            projectsTotalPages: 1,
                            recruiters: recruiters,
                            managers: managers
                        ),
                        statusCode: response.statusCode
                    )
                }

                // Legacy/unit-test fallback format (`root["report"]` / `root["kpis"]`)
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

                let projectsRaw = reportObj.optObjectArray("by_project").ifEmpty(reportObj.optObjectArray("projects"))
                let projects: [ProjectBreakdownRow] = projectsRaw.enumerated().map { idx, p in
                    let name = p.optString("project_display", "du_an", "project_name", "name", "project_id", defaultValue: "Dự án #\(idx + 1)")
                    let pTotal = p.optInt("total_workers", "total_count", "total")
                    let pActive = p.optInt("on_count", "active_count", "active")
                    let pInactive = p.optInt("off_count", "inactive_count", "inactive")
                    let pNew = p.optInt("new_count", "new")
                    let share = pTotal > 0 ? (Double(pActive) * 100.0 / Double(pTotal)) : p.optDouble("active_share_pct", "active_rate")
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

                let overview = GeneralOverviewData(
                    scope: scope,
                    kpis: GeneralOverviewKpis(
                        totalHeadcount: totalWorkers,
                        activeCount: activeCount,
                        inactiveCount: inactiveCount,
                        interviewCount: unconfirmedCount,
                        newCount: newCount,
                        resignedInPeriodCount: resignedInPeriodCount,
                        retentionRate: retentionRate,
                        turnoverRate: turnoverRate
                    ),
                    projects: projects,
                    projectsTotal: projects.count,
                    projectsPage: projectsPage,
                    projectsTotalPages: 1,
                    recruiters: recruiters,
                    managers: []
                )
                return .success(overview, statusCode: response.statusCode)
            }
        }

        // Scoped or non-Admin fallback: aggregate from `/api/direct-entry/workers`
        if let fallbackData = await fetchMergedWorkersForScopedFallback(
            preferredScope: scope,
            startDate: startDate,
            endDate: endDate
        ) {
            let items = fallbackData.items
            let isWorking: (WorkerItem) -> Bool = { w in
                let st = w.trangThai.lowercased()
                return !st.contains("nghỉ") && !st.contains("off") && !st.contains("chưa")
            }
            let isOff: (WorkerItem) -> Bool = { w in
                let st = w.trangThai.lowercased()
                return st.contains("nghỉ") || st == "off"
            }
            let total = max(fallbackData.total, items.count)
            let active = items.filter { isWorking($0) }.count
            let inactive = items.filter { isOff($0) }.count
            let interview = max(0, items.count - active - inactive)
            let retention = items.isEmpty ? 0.0 : (Double(active) * 100.0 / Double(items.count))
            let turnover = items.isEmpty ? 0.0 : (Double(inactive) * 100.0 / Double(items.count))

            var byProject: [String: (total: Int, active: Int, inactive: Int)] = [:]
            var byRecruiter: [String: (total: Int, active: Int, inactive: Int)] = [:]
            for w in items {
                let pName = w.duAn.isEmpty ? "Chưa phân dự án" : w.duAn
                var pCur = byProject[pName] ?? (0, 0, 0)
                pCur.total += 1
                if isWorking(w) { pCur.active += 1 } else if isOff(w) { pCur.inactive += 1 }
                byProject[pName] = pCur

                let rName = w.nguoiTuyen.isEmpty ? "Chưa gán người tuyển" : w.nguoiTuyen
                var rCur = byRecruiter[rName] ?? (0, 0, 0)
                rCur.total += 1
                if isWorking(w) { rCur.active += 1 } else if isOff(w) { rCur.inactive += 1 }
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
                    activeSharePct: kv.value.total > 0 ? (Double(kv.value.active) * 100.0 / Double(kv.value.total)) : 0.0
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
                        newCount: hasDateFilter ? total : 0,
                        resignedInPeriodCount: hasDateFilter ? inactive : 0,
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
                statusCode: fallbackData.statusCode
            )
        }

        return .failure(message: "Không tải được báo cáo tổng quát.")
    }

    public func fetchWorkforceDeepMetrics(
        scope: String = "all",
        startDate: String? = nil,
        endDate: String? = nil
    ) async -> MiniBiApiResult<WorkforceDeepMetricsData> {
        let queryItems = Self.buildWorkforceDeepQueryItems(startDate: startDate, endDate: endDate)

        let response = await httpClient.request(
            method: "GET",
            path: "/api/reporting/workforce-deep",
            queryItems: queryItems
        )

        if response.isSuccess, let root = response.jsonObject {
            if let wfObj = root["workforce"] as? [String: Any] {
                let snapshotObj = (wfObj["snapshot"] as? [String: Any]) ?? [:]
                let totalEp = max(1, snapshotObj.optInt("eligible_episode_total", "eligibleEpisodeTotal", defaultValue: 1))
                let onCount = snapshotObj.optInt("on_count", "onCount")
                let offCount = snapshotObj.optInt("off_count", "offCount")
                let unconfirmedCount = snapshotObj.optInt("unconfirmed_count", "unconfirmedCount")

                let retentionObj = (wfObj["retention"] as? [String: Any]) ?? [:]
                let checkpointsObj = (retentionObj["checkpoints"] as? [String: Any]) ?? [:]
                var tenureBuckets: [DistributionBucket] = []
                for (key, title) in [("d7", "Giữ chân sau 7 ngày (D7)"), ("d30", "Giữ chân sau 30 ngày (D30)"), ("d90", "Giữ chân sau 90 ngày (D90)")] {
                    if let cp = checkpointsObj[key] as? [String: Any] {
                        let retained = cp.optInt("retained_count", "retainedCount")
                        let rawRate = cp.optDouble("retention_rate", "retentionRate")
                        let pct = rawRate <= 1.0 && rawRate > 0.0 ? rawRate * 100.0 : rawRate
                        tenureBuckets.append(DistributionBucket(id: key, label: title, count: retained, percentage: pct))
                    }
                }

                let dimsObj = (wfObj["dimensions"] as? [String: Any]) ?? [:]
                let byProviderObj = (dimsObj["by_provider"] as? [String: Any]) ?? (dimsObj["byProvider"] as? [String: Any]) ?? [:]
                let providerBucketsRaw = byProviderObj.optObjectArray("buckets")
                let providerBuckets: [DistributionBucket] = providerBucketsRaw.enumerated().map { idx, b in
                    let cnt = b.optInt("eligible_episode_count", "eligibleEpisodeCount", "on_count")
                    let pct = Double(cnt) * 100.0 / Double(totalEp)
                    return DistributionBucket(
                        id: b.optString("key", defaultValue: "prov_\(idx)"),
                        label: b.optString("display", "label", "key", defaultValue: "Nguồn \(idx + 1)"),
                        count: cnt,
                        percentage: pct
                    )
                }

                let byTeamObj = (dimsObj["by_team"] as? [String: Any]) ?? (dimsObj["byTeam"] as? [String: Any]) ?? [:]
                let teamBucketsRaw = byTeamObj.optObjectArray("buckets")
                let teamBuckets: [DistributionBucket] = teamBucketsRaw.enumerated().map { idx, b in
                    let cnt = b.optInt("eligible_episode_count", "eligibleEpisodeCount", "on_count")
                    let pct = Double(cnt) * 100.0 / Double(totalEp)
                    return DistributionBucket(
                        id: b.optString("key", defaultValue: "team_\(idx)"),
                        label: b.optString("display", "label", "key", defaultValue: "Nhóm \(idx + 1)"),
                        count: cnt,
                        percentage: pct
                    )
                }

                let statusBuckets: [DistributionBucket] = [
                    DistributionBucket(id: "ON", label: "Đang làm việc (ON)", count: onCount, percentage: Double(onCount) * 100.0 / Double(totalEp)),
                    DistributionBucket(id: "UNCONFIRMED", label: "Chờ nhận việc (UNCONFIRMED)", count: unconfirmedCount, percentage: Double(unconfirmedCount) * 100.0 / Double(totalEp)),
                    DistributionBucket(id: "OFF", label: "Đã nghỉ việc (OFF)", count: offCount, percentage: Double(offCount) * 100.0 / Double(totalEp))
                ]

                let leaveReasonsObj = (wfObj["leave_reasons"] as? [String: Any]) ?? (wfObj["leaveReasons"] as? [String: Any]) ?? [:]
                let leaveCatsRaw = leaveReasonsObj.optObjectArray("by_category").ifEmpty(leaveReasonsObj.optObjectArray("byCategory"))
                let resignBuckets: [DistributionBucket] = leaveCatsRaw.enumerated().map { idx, c in
                    let cnt = c.optInt("episode_count", "episodeCount", "count")
                    let rawShare = c.optDouble("share_of_off_rate", "shareOfOffRate", "percentage")
                    let pct = rawShare <= 1.0 && rawShare > 0.0 ? rawShare * 100.0 : rawShare
                    return DistributionBucket(
                        id: c.optString("key", defaultValue: "leave_\(idx)"),
                        label: c.optString("label", "display", "key", defaultValue: "Lý do \(idx + 1)"),
                        count: cnt,
                        percentage: pct
                    )
                }

                return .success(
                    WorkforceDeepMetricsData(
                        tenureBuckets: tenureBuckets,
                        ageBuckets: teamBuckets,
                        genderBuckets: providerBuckets,
                        contractBuckets: statusBuckets,
                        resignReasonBuckets: resignBuckets
                    ),
                    statusCode: response.statusCode
                )
            }

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

        if let fallbackData = await fetchMergedWorkersForScopedFallback(
            preferredScope: scope,
            startDate: startDate,
            endDate: endDate
        ) {
            let items = fallbackData.items
            let total = max(1, items.count)
            var byStatus: [String: Int] = [:]
            var byLaborType: [String: Int] = [:]
            for w in items {
                byStatus[w.trangThai, default: 0] += 1
                let lt = w.nganhNghe.isEmpty ? "Thời vụ" : w.nganhNghe
                byLaborType[lt, default: 0] += 1
            }
            let contractBuckets = byStatus.sorted { $0.value > $1.value }.enumerated().map { idx, kv in
                DistributionBucket(id: "st_\(idx)", label: kv.key, count: kv.value, percentage: Double(kv.value) * 100.0 / Double(total))
            }
            let genderBuckets = byLaborType.sorted { $0.value > $1.value }.enumerated().map { idx, kv in
                DistributionBucket(id: "lt_\(idx)", label: kv.key, count: kv.value, percentage: Double(kv.value) * 100.0 / Double(total))
            }
            return .success(
                WorkforceDeepMetricsData(
                    tenureBuckets: [],
                    ageBuckets: [],
                    genderBuckets: genderBuckets,
                    contractBuckets: contractBuckets,
                    resignReasonBuckets: []
                ),
                statusCode: fallbackData.statusCode
            )
        }

        return .success(WorkforceDeepMetricsData(), statusCode: response.statusCode)
    }

    public func fetchTeamReport(
        startDate: String? = nil,
        endDate: String? = nil,
        teamCode: String? = nil
    ) async -> MiniBiApiResult<TeamReportData> {
        let queryItems = Self.buildWorkforceDeepQueryItems(startDate: startDate, endDate: endDate)
        let deepResponse = await httpClient.request(
            method: "GET",
            path: "/api/reporting/workforce-deep",
            queryItems: queryItems
        )

        if deepResponse.isSuccess,
           let root = deepResponse.jsonObject,
           let wfObj = root["workforce"] as? [String: Any],
           let dimsObj = wfObj["dimensions"] as? [String: Any] {
            let byTeamObj = (dimsObj["by_team"] as? [String: Any]) ?? (dimsObj["byTeam"] as? [String: Any]) ?? [:]
            let teamBuckets = byTeamObj.optObjectArray("buckets")

            let teams: [TeamSummaryRow] = teamBuckets.enumerated().map { idx, b in
                let key = b.optString("key", "team_id", defaultValue: "team_\(idx)")
                let name = b.optString("display", "team_name", "name", defaultValue: key)
                let tTotal = b.optInt("eligible_episode_count", "eligibleEpisodeCount", "total")
                let tActive = b.optInt("on_count", "onCount", "active")
                let tInactive = b.optInt("off_count", "offCount", "inactive")
                let tNew = b.optInt("unconfirmed_count", "unconfirmedCount")
                let tRate = tTotal > 0 ? (Double(tActive) * 100.0 / Double(tTotal)) : 0.0
                return TeamSummaryRow(
                    id: key,
                    teamCode: key,
                    teamName: name,
                    leaderName: "",
                    memberCount: 0,
                    totalWorkers: tTotal,
                    activeWorkers: tActive,
                    inactiveWorkers: tInactive,
                    newWorkers: tNew,
                    activeRatePct: tRate,
                    members: []
                )
            }
            if !teams.isEmpty {
                return .success(TeamReportData(teams: teams), statusCode: deepResponse.statusCode)
            }
        }

        // Fallback for Team Leader / Scoped accounts: build team summary from `team` scope workers
        if let fallbackData = await fetchMergedWorkersForScopedFallback(
            preferredScope: "team",
            startDate: startDate,
            endDate: endDate
        ), !fallbackData.items.isEmpty {
            let items = fallbackData.items
            var byRecruiter: [String: (total: Int, active: Int, inactive: Int)] = [:]
            var activeTotal = 0
            var inactiveTotal = 0
            for w in items {
                let st = w.trangThai.lowercased()
                let isWork = !st.contains("nghỉ") && !st.contains("off") && !st.contains("chưa")
                let isOff = st.contains("nghỉ") || st == "off"
                if isWork { activeTotal += 1 } else if isOff { inactiveTotal += 1 }
                let rec = w.nguoiTuyen.isEmpty ? "Chưa gán người tuyển" : w.nguoiTuyen
                var cur = byRecruiter[rec] ?? (0, 0, 0)
                cur.total += 1
                if isWork { cur.active += 1 } else if isOff { cur.inactive += 1 }
                byRecruiter[rec] = cur
            }
            let members = byRecruiter.sorted { $0.value.total > $1.value.total }.enumerated().map { idx, kv in
                PersonPerformanceRow(
                    id: "mem_\(idx)",
                    code: kv.key,
                    name: kv.key,
                    totalCount: kv.value.total,
                    activeCount: kv.value.active,
                    inactiveCount: kv.value.inactive,
                    activeRatePct: kv.value.total > 0 ? (Double(kv.value.active) * 100.0 / Double(kv.value.total)) : 0.0
                )
            }
            let total = items.count
            let teamRow = TeamSummaryRow(
                id: "my_team",
                teamCode: "TEAM",
                teamName: "Nhóm phụ trách",
                leaderName: "",
                memberCount: members.count,
                totalWorkers: total,
                activeWorkers: activeTotal,
                inactiveWorkers: inactiveTotal,
                newWorkers: max(0, total - activeTotal - inactiveTotal),
                activeRatePct: total > 0 ? (Double(activeTotal) * 100.0 / Double(total)) : 0.0,
                members: members
            )
            return .success(TeamReportData(teams: [teamRow]), statusCode: fallbackData.statusCode)
        }

        return .success(TeamReportData(teams: []), statusCode: 200)
    }

    public func fetchDuplicateCccdReport(
        search: String = "",
        page: Int = 1,
        pageSize: Int = 25
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
        let groupsRaw = dataObj.optObjectArray("items").ifEmpty(dataObj.optObjectArray("groups"))

        let groups: [DuplicateCccdGroup] = groupsRaw.enumerated().map { idx, g in
            let last4 = g.optString("cccd_last4")
            let cccd = g.optString("national_id", "cccd", "so_cccd", defaultValue: last4.isEmpty ? "group_\(idx)" : "••••••••\(last4)")
            let epsRaw = g.optObjectArray("episodes").ifEmpty(g.optObjectArray("records")).ifEmpty(g.optObjectArray("entries"))
            let episodes: [DuplicateCccdEpisode] = epsRaw.enumerated().map { eIdx, e in
                let entryIdUuid = e.optString("entry_id", "id")
                let epId = e.optInt64("episode_id", "entry_id", "id") ?? Int64(idx * 1000 + eIdx + 1)
                let entryVer = e.optInt("entry_version", "version", defaultValue: 1)
                let empCode = e.optString("employee_code", "ma_nld", "worker_code")
                return DuplicateCccdEpisode(
                    id: entryIdUuid.isEmpty ? "\(cccd)_\(epId)" : entryIdUuid,
                    episodeId: epId,
                    entryIdUuid: entryIdUuid,
                    entryVersion: entryVer,
                    maNld: empCode.isEmpty ? entryIdUuid : empCode,
                    hoTen: e.optString("display_name", "ho_ten", "full_name"),
                    duAn: e.optString("project_display", "du_an", "project_name"),
                    quanLy: e.optString("quan_ly", "manager_name"),
                    nguoiTuyen: e.optString("recruiter_display", "nguoi_tuyen", "recruiter_name"),
                    trangThai: e.optString("latest_status", "employment_status", "trang_thai", "status"),
                    ngayVaoLam: e.optString("first_work_date", "ngay_vao_lam", "start_date"),
                    canSelfWithdraw: e.optBool("can_self_withdraw", "allow_withdraw", defaultValue: false)
                )
            }
            return DuplicateCccdGroup(
                id: "\(cccd)_\(idx)",
                cccd: cccd,
                workerCount: g.optInt("profile_count", "worker_count", "count", defaultValue: episodes.count),
                activeCount: g.optInt("active_profile_count", "active_count", "on_count", "active"),
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
        maNld: String,
        entryVersion: Int = 1
    ) async -> MiniBiApiResult<Bool> {
        let idempotencyKey = UUID().uuidString.lowercased()
        let body: [String: Any] = [
            "entry_id": maNld,
            "expected_entry_version": max(1, entryVersion),
            "idempotency_key": idempotencyKey,
            "reason": NSNull()
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

    private static func extractFormPayloadStrings(_ rawDict: [String: Any]) -> [String: String] {
        var formPayload: [String: String] = [:]
        for (k, v) in rawDict {
            guard !(v is NSNull) else { continue }
            if let s = v as? String {
                formPayload[k] = s
            } else if let b = v as? Bool {
                formPayload[k] = b ? "true" : "false"
            } else if let n = v as? NSNumber {
                if CFGetTypeID(n) == CFBooleanGetTypeID() {
                    formPayload[k] = n.boolValue ? "true" : "false"
                } else {
                    formPayload[k] = n.stringValue
                }
            } else if let arr = v as? [Any] {
                let strs = arr.compactMap { elem -> String? in
                    if let s = elem as? String { return s }
                    if let n = elem as? NSNumber { return n.stringValue }
                    return nil
                }
                formPayload[k] = strs.joined(separator: ",")
            }
        }
        return formPayload
    }

    private static func deterministicIdHash(_ rawUuid: String) -> Int64 {
        let trimmed = rawUuid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return 0 }
        var hash: UInt64 = 14695981039346656037
        for byte in trimmed.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1099511628211
        }
        return Int64(hash & 0x7FFF_FFFF_FFFF_FFFF)
    }

    public static func parseObligationItem(
        _ obj: [String: Any],
        fallbackRoot: [String: Any]? = nil,
        fallback: WorkReportObligationItem? = nil
    ) -> WorkReportObligationItem {
        let dataRoot = (fallbackRoot?["data"] as? [String: Any])
            ?? (obj["data"] as? [String: Any])
            ?? fallbackRoot
            ?? obj
        let obObj = (obj["obligation"] as? [String: Any])
            ?? (dataRoot["obligation"] as? [String: Any])
            ?? obj

        let obligationIdUuid = obObj.optString("obligation_id", "id")
            .ifEmpty(dataRoot.optString("obligation_id", "id"))
            .ifEmpty(fallback?.obligationIdUuid ?? "")
        let obligationId: Int64 = obObj.optInt64("obligation_id", "id")
            ?? dataRoot.optInt64("obligation_id", "id")
            ?? (fallback?.id != 0 ? fallback?.id : nil)
            ?? deterministicIdHash(obligationIdUuid)

        let templateObj = (obObj["template"] as? [String: Any])
            ?? (dataRoot["template"] as? [String: Any])
        let submissionObj = (obObj["submission"] as? [String: Any])
            ?? (dataRoot["submission"] as? [String: Any])
        let latestReviewObj = (obObj["latest_review_event"] as? [String: Any])
            ?? (dataRoot["latest_review_event"] as? [String: Any])
        let revisionsArr = dataRoot.optObjectArray("revisions")
            .ifEmpty(obObj.optObjectArray("revisions"))

        let submissionIdUuid = obObj.optNullableString("submission_id")
            ?? submissionObj?.optNullableString("submission_id", "id")
            ?? dataRoot.optNullableString("submission_id")
            ?? fallback?.submissionIdUuid
        let submissionId = obObj.optInt64("submission_id")
            ?? submissionObj?.optInt64("id", "submission_id")
            ?? dataRoot.optInt64("submission_id")
            ?? fallback?.submissionId

        let templateCode = obObj.optString(
            "template_code",
            defaultValue: templateObj?.optString("template_code", "code")
                .ifEmpty(fallback?.templateCode ?? "") ?? ""
        )
        let templateName = obObj.optString(
            "template_display_name",
            "template_name",
            "display_name",
            "title",
            defaultValue: templateObj?.optString("display_name", "template_display_name", "name", "title")
                .ifEmpty(fallback?.templateName ?? "Báo cáo công việc")
                ?? "Báo cáo công việc"
        )
        let periodType = obObj.optString(
            "period_type",
            defaultValue: templateObj?.optString("period_type")
                .ifEmpty(fallback?.periodType ?? "WEEKLY")
                ?? "WEEKLY"
        )
        let periodKey = obObj.optString("period_key", "period")
            .ifEmpty(fallback?.periodKey ?? "")
        let dueAt = obObj.optNullableString("due_at", "deadline_at")
            ?? fallback?.dueAt
        let submissionState = (
            submissionObj?.optNullableString("state", "submission_state", "status")
            ?? obObj.optNullableString("submission_state", "state", "status")
            ?? fallback?.submissionState
            ?? WorkReportsContract.stateNotStarted
        ).uppercased()

        // Strictly read `is_overdue` from server (backward compatible default `false`)
        let isOverdue = obObj.optBool(
            "is_overdue",
            defaultValue: dataRoot.optBool("is_overdue", defaultValue: fallback?.isOverdue ?? false)
        )

        // Never fall back to `obObj["version"]` because `obligation.version` is 1 even when `submission_version` is null!
        let explicitSubVersion: Int? = {
            if let v = submissionObj?.optInt("version", "submission_version", defaultValue: -1), v > 0 {
                return v
            }
            if let v = obObj.optInt("submission_version", defaultValue: -1) as Int?, v > 0 {
                return v
            }
            return nil
        }()
        let version = explicitSubVersion ?? obObj.optInt("version", defaultValue: fallback?.version ?? 1)

        let submittedAt = submissionObj?.optNullableString("submitted_at", "last_submitted_at", "updated_at")
            ?? obObj.optNullableString("submitted_at", "last_submitted_at")
            ?? fallback?.submittedAt
        let reviewedAt = latestReviewObj?.optNullableString("created_at", "reviewed_at")
            ?? submissionObj?.optNullableString("reviewed_at")
            ?? obObj.optNullableString("reviewed_at")
            ?? fallback?.reviewedAt
        let reviewerName = latestReviewObj?.optNullableString("actor_display_name", "reviewer_name", "reviewed_by_name")
            ?? obObj.optNullableString("reviewer_name", "reviewed_by_name", "reviewer_display_name")
            ?? submissionObj?.optNullableString("reviewer_name", "reviewed_by_name", "reviewer_display_name")
            ?? fallback?.reviewerName
        let reviewComment = latestReviewObj?.optNullableString("reason", "review_note", "comment")
            ?? obObj.optNullableString("latest_rejection_reason", "review_note", "review_comment", "reject_reason", "comment")
            ?? submissionObj?.optNullableString("latest_rejection_reason", "review_note", "review_comment", "reject_reason", "comment")
            ?? fallback?.reviewComment
        let unitName = obObj.optNullableString("org_unit_name", "unit_name", "department_name")
            ?? fallback?.unitName

        let schemaRaw: Any? = obObj["schema_json"]
            ?? obObj["schema"]
            ?? obObj["form_schema"]
            ?? templateObj?["schema_json"]
            ?? templateObj?["schema"]
            ?? templateObj?["form_schema"]
            ?? dataRoot["schema_json"]
            ?? dataRoot["schema"]
            ?? dataRoot["form_schema"]
        let parsedSections = WorkReportsContract.parseDynamicSections(schemaRaw)
        let sections = parsedSections.isEmpty ? (fallback?.sections ?? []) : parsedSections

        let latestRevisionPayload: [String: Any]? = {
            guard !revisionsArr.isEmpty else { return nil }
            let bestRev = revisionsArr.max {
                $0.optInt("revision_no", defaultValue: 0) < $1.optInt("revision_no", defaultValue: 0)
            }
            return (bestRev?["payload_json"] as? [String: Any])
                ?? (bestRev?["payload"] as? [String: Any])
        }()

        let rawPayloadDict = (submissionObj?["draft_payload_json"] as? [String: Any])
            ?? (submissionObj?["payload_json"] as? [String: Any])
            ?? (submissionObj?["form_payload"] as? [String: Any])
            ?? (submissionObj?["payload"] as? [String: Any])
            ?? (submissionObj?["answers"] as? [String: Any])
            ?? latestRevisionPayload
            ?? (obObj["draft_payload_json"] as? [String: Any])
            ?? (obObj["payload_json"] as? [String: Any])
            ?? (obObj["form_payload"] as? [String: Any])
            ?? (obObj["payload"] as? [String: Any])
            ?? (obObj["answers"] as? [String: Any])
            ?? [:]

        var formPayload = Self.extractFormPayloadStrings(rawPayloadDict)
        if formPayload.isEmpty, let fbPayload = fallback?.formPayload, !fbPayload.isEmpty {
            formPayload = fbPayload
        }

        // Seed schema defaults (and "false" for boolean toggles so untouched "Không" toggles are always explicit)
        for sec in sections {
            for field in sec.fields where formPayload[field.id] == nil {
                if let def = field.defaultValue, !def.isEmpty {
                    formPayload[field.id] = def
                } else if field.isBooleanType {
                    formPayload[field.id] = "false"
                }
            }
        }

        return WorkReportObligationItem(
            id: obligationId,
            obligationIdUuid: obligationIdUuid.isEmpty ? String(obligationId) : obligationIdUuid,
            submissionId: submissionId,
            submissionIdUuid: submissionIdUuid,
            submissionVersion: explicitSubVersion,
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
        let submissionIdUuid = obj.optString("submission_id", "id")
        let obligationIdUuid = obj.optString("obligation_id", defaultValue: submissionIdUuid)
        let submissionId = obj.optInt64("submission_id", "id") ?? deterministicIdHash(submissionIdUuid)
        let obligationId = obj.optInt64("obligation_id") ?? deterministicIdHash(obligationIdUuid)
        let templateName = obj.optString("template_display_name", "template_name", "display_name", "title", defaultValue: "Báo cáo công việc")
        let periodType = obj.optString("period_type", defaultValue: "WEEKLY")
        let periodKey = obj.optString("period_key", "period")
        let reporterName = obj.optString("subject_display_name", "reporter_name", "author_name", "user_name", defaultValue: "Nhân sự")
        let reporterCode = obj.optString("subject_user_id", "reporter_code", "author_code", "username")
        let unitName = obj.optString("org_unit_name", "unit_name", "department_name")
        let submittedAt = obj.optNullableString("submitted_at", "last_submitted_at", "created_at")
        let submissionState = obj.optString("submission_state", "state", "status", defaultValue: WorkReportsContract.stateSubmitted)
        let version = max(1, obj.optInt("submission_version", "version", defaultValue: 1))
        let sections = WorkReportsContract.parseDynamicSections(
            obj["schema_json"] ?? obj["schema"] ?? obj["form_schema"] ?? (obj["template"] as? [String: Any])?["schema_json"]
        )

        let rawPayloadDict = (obj["payload_json"] as? [String: Any])
            ?? (obj["draft_payload_json"] as? [String: Any])
            ?? (obj["form_payload"] as? [String: Any])
            ?? (obj["payload"] as? [String: Any])
            ?? (obj["answers"] as? [String: Any])
            ?? [:]
        let formPayload = Self.extractFormPayloadStrings(rawPayloadDict)

        return WorkReportReviewItem(
            id: submissionId,
            submissionIdUuid: submissionIdUuid.isEmpty ? String(submissionId) : submissionIdUuid,
            obligationId: obligationId,
            obligationIdUuid: obligationIdUuid.isEmpty ? String(obligationId) : obligationIdUuid,
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

private extension String {
    func ifEmpty(_ fallback: @autoclosure () -> String) -> String {
        isEmpty ? fallback() : self
    }
}

private extension Array {
    func ifEmpty(_ fallback: @autoclosure () -> [Element]) -> [Element] {
        isEmpty ? fallback() : self
    }
}
