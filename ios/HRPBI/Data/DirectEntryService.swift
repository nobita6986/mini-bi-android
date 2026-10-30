import Foundation
import CryptoKit

// MARK: - Direct Entry ("Cấp mới") & CCCD Review Service
// Mirrors Android's `data/DirectEntryService.kt` 100%

public enum DirectEntryContract {
    public static let docTypeWorkerPortrait = "WORKER_PORTRAIT"
    public static let docTypeCccdFront = "CCCD_FRONT"
    public static let docTypeCccdBack = "CCCD_BACK"
    public static let docTypeEmploymentContract = "EMPLOYMENT_CONTRACT"
    public static let docTypeSafetyCard = "SAFETY_CARD"

    public static let reviewStatusMatched = "MATCHED"
    public static let reviewStatusMismatch = "MISMATCH"
    public static let reviewStatusPendingQr = "PENDING_QR"
    public static let reviewStatusUnreadableQr = "UNREADABLE_QR"
    public static let reviewStatusMissingQr = "MISSING_QR"

    public static let resolveActionStandardizeFromQr = "STANDARDIZE_FROM_QR"
    public static let resolveActionUpdateEnteredCccd = "UPDATE_ENTERED_CCCD"

    public static func isValidCccd12(_ raw: String?) -> Bool {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              trimmed.count == 12,
              trimmed.allSatisfy({ $0.isNumber }),
              trimmed != "000000000000"
        else {
            return false
        }
        return true
    }

    public static func uiDateToIsoDate(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        if trimmed.contains("/") {
            let parts = trimmed.split(separator: "/")
            if parts.count == 3 {
                let d = String(parts[0]).paddingLeftZeros(count: 2)
                let m = String(parts[1]).paddingLeftZeros(count: 2)
                let y = String(parts[2])
                if y.count == 4 {
                    return "\(y)-\(m)-\(d)"
                }
            }
        }
        if trimmed.contains("-") {
            let parts = trimmed.split(separator: "-")
            if parts.count == 3 {
                if parts[0].count == 4 {
                    return trimmed
                }
                if parts[2].count == 4 {
                    let d = String(parts[0]).paddingLeftZeros(count: 2)
                    let m = String(parts[1]).paddingLeftZeros(count: 2)
                    return "\(parts[2])-\(m)-\(d)"
                }
            }
        }
        return trimmed
    }

    public static func isoDateToUiDate(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let prefix10 = String(trimmed.prefix(10))
        if prefix10.contains("-") {
            let parts = prefix10.split(separator: "-")
            if parts.count == 3 {
                if parts[0].count == 4 {
                    let y = String(parts[0])
                    let m = String(parts[1]).paddingLeftZeros(count: 2)
                    let d = String(parts[2]).paddingLeftZeros(count: 2)
                    return "\(d)/\(m)/\(y)"
                }
                if parts[2].count == 4 {
                    let d = String(parts[0]).paddingLeftZeros(count: 2)
                    let m = String(parts[1]).paddingLeftZeros(count: 2)
                    let y = String(parts[2])
                    return "\(d)/\(m)/\(y)"
                }
            }
        }
        return trimmed
    }

    public static func reviewStatusDisplayLabel(_ status: String) -> String {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case reviewStatusMatched, "MATCHED_INITIAL", "RESOLVED":
            return "Đã khớp CCCD"
        case reviewStatusMismatch:
            return "Lệch thông tin"
        case reviewStatusPendingQr, "PENDING":
            return "Đang chờ đọc QR"
        case reviewStatusUnreadableQr, "QR_UNREADABLE":
            return "QR mờ / không đọc được"
        case reviewStatusMissingQr:
            return "Thiếu ảnh CCCD"
        default:
            return status.isEmpty ? "Chờ kiểm tra" : status
        }
    }
}

private extension String {
    func paddingLeftZeros(count: Int) -> String {
        if self.count >= count { return self }
        return String(repeating: "0", count: count - self.count) + self
    }
}

public final class DirectEntryService: @unchecked Sendable {
    public static let shared = DirectEntryService()

    private let httpClient: BackendHttpClient
    private var lastLoadedCatalog: DirectEntryAssigneeOptions = DirectEntryAssigneeOptions()

    public init(httpClient: BackendHttpClient = .shared) {
        self.httpClient = httpClient
    }

    // MARK: - 1. CCCD Review Queue (`GET /api/direct-entry/cccd-review`)

    public func fetchCccdReviewList(
        status: String = "ALL",
        search: String = "",
        page: Int = 1,
        pageSize: Int = 30
    ) async -> MiniBiApiResult<(items: [CccdReviewItem], total: Int)> {
        let safeLimit = min(max(pageSize, 1), 100)
        let safeOffset = max(0, (max(page, 1) - 1) * safeLimit)
        let normalizedStatus: String = {
            let s = status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if s.isEmpty || s == "ALL" { return "NEEDS_REVIEW" }
            return s
        }()

        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "status", value: normalizedStatus),
            URLQueryItem(name: "limit", value: String(safeLimit)),
            URLQueryItem(name: "offset", value: String(safeOffset))
        ]
        if !search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: search.trimmingCharacters(in: .whitespacesAndNewlines)))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/direct-entry/cccd-review",
            queryItems: queryItems
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success((items: [], total: 0), statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được danh sách duyệt CCCD.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let rawList = dataObj.optObjectArray("items")
            .ifEmpty(dataObj.optObjectArray("rows"))
            .ifEmpty(dataObj.optObjectArray("entries"))

        let items = rawList.enumerated().map { idx, obj in
            Self.parseCccdReviewItem(obj, fallbackIndex: idx)
        }
        let total = dataObj.optInt("total", "total_count", defaultValue: items.count)
        return .success((items: items, total: total), statusCode: response.statusCode)
    }

    public func resolveCccdReview(
        entryId: Int64,
        action: String,
        correctedCccd: String? = nil,
        note: String = ""
    ) async -> MiniBiApiResult<CccdReviewItem> {
        await resolveCccdReview(
            entryIdUuid: String(entryId),
            expectedEntryVersion: 1,
            action: action,
            correctedCccd: correctedCccd,
            note: note
        )
    }

    public func resolveCccdReview(
        entryIdUuid: String,
        expectedEntryVersion: Int = 1,
        action: String,
        correctedCccd: String? = nil,
        note: String = ""
    ) async -> MiniBiApiResult<CccdReviewItem> {
        var body: [String: Any] = [
            "action": action,
            "expected_entry_version": max(1, expectedEntryVersion),
            "note": note,
            "resolution_note": note
        ]
        if let correctedCccd, !correctedCccd.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let clean = correctedCccd.trimmingCharacters(in: .whitespacesAndNewlines)
            body["corrected_cccd"] = clean
            body["entered_national_id"] = clean
            body["national_id"] = clean
        }

        let encoded = entryIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? entryIdUuid
        var response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/entries/\(encoded)/cccd-review/resolve",
            jsonBody: body
        )
        if response.statusCode == 404 {
            response = await httpClient.request(
                method: "POST",
                path: "/api/direct-entry/cccd-review/\(encoded)/resolve",
                jsonBody: body
            )
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không thể cập nhật kết quả duyệt CCCD.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let obj = (root["item"] as? [String: Any])
            ?? (root["entry"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        return .success(Self.parseCccdReviewItem(obj, fallbackIndex: 0), statusCode: response.statusCode)
    }

    public func retryCccdQrExtraction(entryId: Int64) async -> MiniBiApiResult<CccdReviewItem> {
        await retryCccdQrExtraction(entryIdUuid: String(entryId))
    }

    public func retryCccdQrExtraction(entryIdUuid: String) async -> MiniBiApiResult<CccdReviewItem> {
        let encoded = entryIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? entryIdUuid
        var response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/entries/\(encoded)/cccd-review/retry-qr",
            jsonBody: [:]
        )
        if response.statusCode == 404 {
            response = await httpClient.request(
                method: "POST",
                path: "/api/direct-entry/cccd-review/\(encoded)/retry-qr",
                jsonBody: [:]
            )
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không thể trích xuất lại mã QR CCCD.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let obj = (root["item"] as? [String: Any])
            ?? (root["entry"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        return .success(Self.parseCccdReviewItem(obj, fallbackIndex: 0), statusCode: response.statusCode)
    }

    // MARK: - 2. Direct Entry ("Cấp mới") Options, Duplicate Check, Batch Submit & Document Upload

    public func fetchAssigneeOptions() async -> MiniBiApiResult<DirectEntryAssigneeOptions> {
        let effectiveDate = LeaveContract.todayVietnamIsoDate()
        let response = await httpClient.request(
            method: "GET",
            path: "/api/direct-entry/catalog",
            queryItems: [URLQueryItem(name: "effective_date", value: effectiveDate)]
        )
        if response.isSuccess, let root = response.jsonObject {
            let dataObj = (root["catalog"] as? [String: Any])
                ?? (root["data"] as? [String: Any])
                ?? root

            var catalogProjects: [DirectEntryCatalogProject] = []
            if let projObjs = dataObj["projects"] as? [[String: Any]] {
                for p in projObjs {
                    if p.keys.contains("is_active") && !p.optBool("is_active", defaultValue: true) { continue }
                    let pid = p.optString("project_id", "id", "code")
                    let name = p.optString("display_name", "name", "project_display", defaultValue: pid)
                    if !pid.isEmpty && !name.isEmpty {
                        catalogProjects.append(DirectEntryCatalogProject(projectId: pid, displayName: name))
                    }
                }
            }
            catalogProjects.sort { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }

            var catalogRecruiters: [DirectEntryCatalogRecruiter] = []
            if let recObjs = dataObj["recruiters"] as? [[String: Any]] {
                for r in recObjs {
                    if r.keys.contains("is_active") && !r.optBool("is_active", defaultValue: true) { continue }
                    let rid = r.optString("recruiter_id", "id", "code")
                    let name = r.optString("display_name", "name", "recruiter_display", defaultValue: rid)
                    let pCode = r.optString("personnel_code", "employee_code", "code")
                    let pType = r.optString("provider_type", defaultValue: "hrp").lowercased() == "vendor" ? "vendor" : "hrp"
                    let vendorId = r.optNullableString("vendor_id")
                    let teamId = r.optNullableString("team_id")
                    let teamDisplay = r.optNullableString("team_display_name", "team_name")
                    if !rid.isEmpty && !name.isEmpty {
                        catalogRecruiters.append(
                            DirectEntryCatalogRecruiter(
                                recruiterId: rid,
                                displayName: name,
                                personnelCode: pCode,
                                providerType: pType,
                                vendorId: vendorId,
                                teamId: teamId,
                                teamDisplayName: teamDisplay
                            )
                        )
                    }
                }
            }
            catalogRecruiters.sort { $0.label.localizedCaseInsensitiveCompare($1.label) == .orderedAscending }

            var catalogBanks: [DirectEntryCatalogBank] = []
            if let bankObjs = dataObj["banks"] as? [[String: Any]] {
                for b in bankObjs {
                    let bid = b.optString("bank_id", "id", "code")
                    let name = b.optString("display_name", "name", "bank_name", defaultValue: bid)
                    if !bid.isEmpty && !name.isEmpty {
                        catalogBanks.append(DirectEntryCatalogBank(bankId: bid, displayName: name))
                    }
                }
            }
            catalogBanks.sort { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }

            var catalogLaborTypes: [DirectEntryCatalogLaborType] = []
            if let ltObjs = dataObj["labor_types"] as? [[String: Any]] {
                for lt in ltObjs {
                    if lt.keys.contains("is_active") && !lt.optBool("is_active", defaultValue: true) { continue }
                    let key = lt.optString("labor_type_key", "key", "code")
                    let name = lt.optString("display_name", "name", "label", defaultValue: key)
                    let order = lt.optInt("sort_order", defaultValue: 100)
                    if !key.isEmpty && !name.isEmpty {
                        catalogLaborTypes.append(DirectEntryCatalogLaborType(laborTypeKey: key, displayName: name, sortOrder: order))
                    }
                }
            }
            catalogLaborTypes.sort { $0.sortOrder < $1.sortOrder }
            if catalogLaborTypes.isEmpty {
                catalogLaborTypes = DirectEntryAssigneeOptions.fallbackLaborTypes
            }

            var projects = catalogProjects.map(\.displayName)
            if projects.isEmpty {
                projects = dataObj.optStringArray("projects", "du_an")
            }

            var recruiters = catalogRecruiters.map(\.label)
            if recruiters.isEmpty {
                recruiters = dataObj.optStringArray("recruiters", "nguoi_tuyen")
            }

            var banks = catalogBanks.map(\.displayName)
            if banks.isEmpty {
                banks = dataObj.optStringArray("banks", "ngan_hang")
            }

            var managers = dataObj.optStringArray("managers", "quan_ly")
            if managers.isEmpty, let mgrObjs = dataObj["managers"] as? [[String: Any]] {
                managers = mgrObjs.compactMap { m in
                    let label = m.optString("display_name", "name", "manager_display", "manager_id")
                    return label.isEmpty ? nil : label
                }
            }

            let options = DirectEntryAssigneeOptions(
                projects: projects,
                managers: managers,
                recruiters: recruiters,
                banks: banks,
                catalogProjects: catalogProjects,
                catalogRecruiters: catalogRecruiters,
                catalogBanks: catalogBanks,
                catalogLaborTypes: catalogLaborTypes
            )
            self.lastLoadedCatalog = options
            return .success(options, statusCode: response.statusCode)
        }
        return .success(lastLoadedCatalog)
    }

    public func fetchBatchHistory() async -> MiniBiApiResult<[DirectEntryBatchHistoryItem]> {
        let response = await httpClient.request(
            method: "GET",
            path: "/api/direct-entry/submissions",
            queryItems: [URLQueryItem(name: "page_size", value: "20")]
        )
        if response.statusCode == 403 || response.statusCode == 404 {
            return .success([], statusCode: response.statusCode)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được lịch sử lô cấp mới.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let rawList = dataObj.optObjectArray("items")
            .ifEmpty(dataObj.optObjectArray("submissions"))
            .ifEmpty(dataObj.optObjectArray("batches"))

        let items = rawList.enumerated().map { idx, obj in
            let subUuid = obj.optString("submission_id", "batch_id", "id")
            return DirectEntryBatchHistoryItem(
                batchId: obj.optInt64("submission_id", "batch_id", "id") ?? Int64(idx + 1),
                submissionIdUuid: subUuid,
                status: obj.optString("state", "status", defaultValue: "SUBMITTED"),
                totalRows: obj.optInt("entry_count", "total_rows", "total"),
                approvedRows: obj.optInt("submitted_count", "approved_rows", "approved_count"),
                pendingRows: obj.optInt("draft_count", "pending_rows", "pending_count"),
                createdAt: obj.optString("submitted_at", "created_at", "updated_at")
            )
        }
        return .success(items, statusCode: response.statusCode)
    }

    public func checkDuplicates(drafts: [NewWorkerEntryDraft]) async -> MiniBiApiResult<[DirectEntryDuplicateCandidate]> {
        let cccds = drafts.map { $0.soCccd.filter(\.isNumber) }.filter { $0.count == 12 }
        guard !cccds.isEmpty else {
            return .success([])
        }

        let body: [String: Any] = ["cccds": cccds]
        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/check-duplicates",
            jsonBody: body
        )
        guard response.isSuccess else {
            return .success([])
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let rawList = dataObj.optObjectArray("duplicates")
            .ifEmpty(dataObj.optObjectArray("conflicts"))
            .ifEmpty(dataObj.optObjectArray("items"))

        let items = rawList.enumerated().map { idx, obj in
            DirectEntryDuplicateCandidate(
                id: "dup_\(idx)",
                cccd: obj.optString("cccd", "so_cccd", "national_id"),
                hoTen: obj.optString("ho_ten", "display_name", "full_name"),
                duAn: obj.optString("du_an", "project_name", "project"),
                maNld: obj.optString("ma_nld", "employee_code"),
                trangThai: obj.optString("trang_thai", "status")
            )
        }
        return .success(items, statusCode: response.statusCode)
    }

    public func buildFullProfileEntryPayload(_ d: NewWorkerEntryDraft) -> [String: Any] {
        let catalog = self.lastLoadedCatalog

        let resolvedProjectId: String = {
            let explicit = d.projectId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !explicit.isEmpty { return explicit }
            let disp = d.duAn.trimmingCharacters(in: .whitespacesAndNewlines)
            if let matched = catalog.catalogProjects.first(where: { $0.displayName.caseInsensitiveCompare(disp) == .orderedSame || $0.projectId == disp }) {
                return matched.projectId
            }
            return disp
        }()

        let resolvedRecruiterId: String = {
            let explicit = d.recruiterId.trimmingCharacters(in: .whitespacesAndNewlines)
            if !explicit.isEmpty { return explicit }
            let disp = d.nguoiTuyen.trimmingCharacters(in: .whitespacesAndNewlines)
            if let matched = catalog.catalogRecruiters.first(where: {
                $0.label.caseInsensitiveCompare(disp) == .orderedSame ||
                $0.displayName.caseInsensitiveCompare(disp) == .orderedSame ||
                $0.recruiterId == disp
            }) {
                return matched.recruiterId
            }
            return disp
        }()

        let resolvedLaborType: String = {
            let explicit = d.laborTypeKey.trimmingCharacters(in: .whitespacesAndNewlines)
            let disp = d.nganhNghe.trimmingCharacters(in: .whitespacesAndNewlines)
            if !explicit.isEmpty, let matchedByKey = catalog.catalogLaborTypes.first(where: {
                $0.laborTypeKey.caseInsensitiveCompare(explicit) == .orderedSame
            }) {
                return matchedByKey.laborTypeKey
            }
            if !explicit.isEmpty && explicit != "SEASONAL" {
                return explicit
            }
            if !disp.isEmpty, let matchedByDisplay = catalog.catalogLaborTypes.first(where: {
                $0.displayName.caseInsensitiveCompare(disp) == .orderedSame
            }) {
                return matchedByDisplay.laborTypeKey
            }
            if disp.lowercased().contains("chính thức") {
                if let official = catalog.catalogLaborTypes.first(where: {
                    $0.displayName.lowercased().contains("chính thức") ||
                    $0.laborTypeKey.lowercased().contains("official") ||
                    $0.laborTypeKey.lowercased().contains("chinh_thuc")
                }) {
                    return official.laborTypeKey
                }
                return "OFFICIAL"
            }
            if let seasonal = catalog.catalogLaborTypes.first(where: {
                $0.displayName.lowercased().contains("thời vụ") ||
                $0.laborTypeKey.uppercased() == "TEMPORARY" ||
                $0.laborTypeKey.uppercased().contains("SEASONAL") ||
                $0.laborTypeKey.uppercased().contains("TEMP")
            }) ?? catalog.catalogLaborTypes.first {
                return seasonal.laborTypeKey
            }
            return !explicit.isEmpty ? explicit : "TEMPORARY"
        }()

        let firstWorkDateIso: String = {
            let iso = DirectEntryContract.uiDateToIsoDate(d.ngayVaoLam)
            return iso.isEmpty ? LeaveContract.todayVietnamIsoDate() : iso
        }()

        let providerType: String = {
            let raw = d.providerType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            return raw == "vendor" ? "vendor" : "hrp"
        }()

        let statusUpper: String = {
            let st = d.trangThai.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if st.contains("chờ") || st.contains("unconfirmed") || st.contains("phỏng vấn") {
                return "UNCONFIRMED"
            }
            return "ON"
        }()

        func optionalTextField(_ value: String, maxLength: Int = 500) -> [String: Any] {
            let clean = String(value.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxLength))
            return clean.isEmpty ? ["state": "omitted"] : ["state": "provided", "value": clean]
        }

        func optionalFreeDateField(_ value: String) -> [String: Any] {
            let clean = String(value.trimmingCharacters(in: .whitespacesAndNewlines).prefix(10))
            return clean.isEmpty ? ["state": "omitted"] : ["state": "provided", "value": clean]
        }

        let genderField: [String: Any] = {
            let g = d.gioiTinh.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if g == "nam" || g == "male" {
                return ["state": "provided", "value": "MALE"]
            }
            if g == "nữ" || g == "nu" || g == "female" {
                return ["state": "provided", "value": "FEMALE"]
            }
            return ["state": "omitted"]
        }()

        let cccdDigits = d.soCccd.filter(\.isNumber)
        let cleanDisplayName: Any = {
            let trimmed = d.hoTen.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? NSNull() : String(trimmed.prefix(256))
        }()

        let workerObj: [String: Any] = [
            "gender": genderField,
            "date_of_birth": optionalFreeDateField(d.ngaySinh),
            "national_id": [
                "state": "provided",
                "value": cccdDigits
            ],
            "national_id_issued_at": optionalFreeDateField(d.ngayCap),
            "national_id_issued_place": ["state": "omitted"],
            "address": optionalTextField(d.queQuan, maxLength: 500),
            "phone": optionalTextField(d.sdt, maxLength: 50)
        ]

        let bankName = d.nganHang.trimmingCharacters(in: .whitespacesAndNewlines)
        let accNum = d.soTaiKhoan.trimmingCharacters(in: .whitespacesAndNewlines)
        let holderName = d.bankAccountHolder.trimmingCharacters(in: .whitespacesAndNewlines)

        let paymentValue: Any = {
            if bankName.isEmpty && accNum.isEmpty && holderName.isEmpty {
                return NSNull()
            }
            var dict: [String: Any] = ["state": "provided"]
            if !accNum.isEmpty { dict["account_number"] = String(accNum.prefix(64)) }
            if !bankName.isEmpty { dict["bank_name"] = String(bankName.prefix(256)) }
            if !holderName.isEmpty { dict["account_holder_name"] = String(holderName.prefix(256)) }
            return dict
        }()

        var rowObj: [String: Any] = [
            "project_id": resolvedProjectId,
            "first_work_date": firstWorkDateIso,
            "provider_type": providerType,
            "labor_type": resolvedLaborType,
            "display_name": cleanDisplayName,
            "worker": workerObj,
            "general_note": optionalTextField(d.ghiChu, maxLength: 500),
            "payment": paymentValue,
            "employment": [
                "initial_status": statusUpper
            ]
        ]

        if providerType == "hrp" {
            rowObj["recruiter_id"] = resolvedRecruiterId
        } else {
            rowObj["vendor_id"] = resolvedRecruiterId
        }

        return rowObj
    }

    public func submitBatch(
        drafts: [NewWorkerEntryDraft],
        confirmDuplicates: Bool = false
    ) async -> MiniBiApiResult<DirectEntryBatchSubmitOutcome> {
        if self.lastLoadedCatalog.catalogProjects.isEmpty && self.lastLoadedCatalog.catalogLaborTypes.isEmpty {
            _ = await fetchAssigneeOptions()
        }

        let fullProfileRows = drafts.map { buildFullProfileEntryPayload($0) }
        let fullProfileBody: [String: Any] = [
            "contract_version": "worker-profile/1.2",
            "rows": fullProfileRows
        ]
        let batchIdempotencyKey = UUID().uuidString.lowercased()

        var response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/batches/full-profile",
            jsonBody: fullProfileBody,
            additionalHeaders: ["Idempotency-Key": batchIdempotencyKey]
        )

        // Fallback to `/api/direct-entry/batches` if mock/legacy server returns 404
        if response.statusCode == 404 {
            let legacyRows: [[String: Any]] = drafts.map { d in
                [
                    "row_id": d.rowId,
                    "ho_ten": d.hoTen.trimmingCharacters(in: .whitespacesAndNewlines),
                    "so_cccd": d.soCccd.trimmingCharacters(in: .whitespacesAndNewlines),
                    "du_an": d.duAn.trimmingCharacters(in: .whitespacesAndNewlines),
                    "trang_thai": d.trangThai.trimmingCharacters(in: .whitespacesAndNewlines)
                ]
            }
            response = await httpClient.request(
                method: "POST",
                path: "/api/direct-entry/batches",
                jsonBody: [
                    "rows": legacyRows,
                    "entries": legacyRows,
                    "confirm_duplicates": confirmDuplicates
                ],
                additionalHeaders: ["Idempotency-Key": batchIdempotencyKey]
            )
        }

        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Lưu lô cấp mới thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["submission"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let submissionIdUuid = dataObj.optString("submission_id", "batch_id", "id")
            .ifEmpty(root.optString("submission_id", "batch_id", "id"))
        let batchId = dataObj.optInt64("submission_id", "batch_id", "id")
            ?? root.optInt64("submission_id", "batch_id", "id")
            ?? 0
        let submissionVersion = max(1, dataObj.optInt("version", "submission_version", defaultValue: root.optInt("version", defaultValue: 1)))

        var entryIdByRowId: [String: Int64] = [:]
        var entryIdUuidByRowId: [String: String] = [:]

        // Parse `entry_ids: ["<uuid>", ...]` returned by `/api/direct-entry/batches/full-profile`
        let entryIdStringArray: [String] = {
            if let arr = dataObj["entry_ids"] as? [String], !arr.isEmpty { return arr }
            if let arr = root["entry_ids"] as? [String], !arr.isEmpty { return arr }
            return []
        }()

        if !entryIdStringArray.isEmpty {
            for (idx, entryUuid) in entryIdStringArray.enumerated() where idx < drafts.count {
                let cleanUuid = entryUuid.trimmingCharacters(in: .whitespacesAndNewlines)
                let rowId = drafts[idx].rowId
                if !cleanUuid.isEmpty && !rowId.isEmpty {
                    entryIdUuidByRowId[rowId] = cleanUuid
                    entryIdByRowId[rowId] = Int64(idx + 1)
                }
            }
        }

        let createdEntries = root.optObjectArray("entries")
            .ifEmpty(dataObj.optObjectArray("entries"))
            .ifEmpty(dataObj.optObjectArray("rows"))

        for (idx, entryObj) in createdEntries.enumerated() {
            let entryIdInt = entryObj.optInt64("entry_id", "id") ?? Int64(idx + 1)
            let entryUuid = entryObj.optString("entry_id", "id", defaultValue: String(entryIdInt))
            let rowId = entryObj.optString(
                "client_row_id",
                "row_id",
                defaultValue: idx < drafts.count ? drafts[idx].rowId : ""
            )
            if !rowId.isEmpty {
                entryIdByRowId[rowId] = entryIdInt
                entryIdUuidByRowId[rowId] = entryUuid
            }
        }

        // Transition submission from DRAFT -> SUBMITTED (mirrors `DirectEntryService.kt:transitionSubmissionToSubmitted`)
        if !submissionIdUuid.isEmpty {
            let encodedSub = submissionIdUuid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? submissionIdUuid
            var expectedVersion = submissionVersion
            var duplicateFingerprint: String? = nil
            var duplicateConflictCount: Int? = nil

            let preflightRes = await httpClient.request(
                method: "GET",
                path: "/api/direct-entry/submissions/\(encodedSub)/duplicate-cccd-preflight"
            )
            if preflightRes.isSuccess, let pfRoot = preflightRes.jsonObject {
                let pfObj = (pfRoot["preflight"] as? [String: Any])
                    ?? (pfRoot["data"] as? [String: Any])
                    ?? pfRoot
                expectedVersion = max(1, pfObj.optInt("version", defaultValue: submissionVersion))
                let fp = pfObj.optString("fingerprint")
                let cCount = pfObj.optInt(
                    "conflict_count",
                    defaultValue: (pfObj["conflicts"] as? [[String: Any]])?.count ?? 0
                )
                if cCount > 0 && !fp.isEmpty {
                    duplicateFingerprint = fp
                    duplicateConflictCount = cCount
                }
            }

            let transitionIdemKey = UUID().uuidString.lowercased()
            var transitionBody: [String: Any] = [
                "expected_version": expectedVersion,
                "target_state": "SUBMITTED",
                "idempotency_key": transitionIdemKey
            ]
            if let fp = duplicateFingerprint, let cCount = duplicateConflictCount, cCount > 0 {
                transitionBody["duplicate_cccd_fingerprint"] = fp
                transitionBody["duplicate_cccd_count"] = cCount
            }

            let transitionRes = await httpClient.request(
                method: "POST",
                path: "/api/direct-entry/submissions/\(encodedSub)/transition",
                jsonBody: transitionBody,
                additionalHeaders: ["Idempotency-Key": transitionIdemKey]
            )
            if !transitionRes.isSuccess && transitionRes.statusCode != 404 {
                return .failure(
                    message: transitionRes.errorMessage ?? "Chuyển trạng thái gửi duyệt lô cấp mới thất bại.",
                    httpStatus: transitionRes.statusCode,
                    errorCode: transitionRes.errorCode
                )
            }
        }

        let createdTotal = max(drafts.count, max(entryIdStringArray.count, createdEntries.count))
        let outcome = DirectEntryBatchSubmitOutcome(
            batchId: batchId,
            submissionIdUuid: submissionIdUuid,
            createdCount: createdTotal,
            entryIdByRowId: entryIdByRowId,
            entryIdUuidByRowId: entryIdUuidByRowId
        )
        return .success(outcome, statusCode: response.statusCode)
    }

    public func uploadEntryDocumentAttachments(
        entryId: Int64,
        docType: String,
        attachments: [LocalAttachmentItem]
    ) async -> MiniBiApiResult<String> {
        await uploadEntryDocumentAttachments(
            entryIdUuid: String(entryId),
            docType: docType,
            attachments: attachments
        )
    }

    public func uploadEntryDocumentAttachments(
        entryIdUuid: String,
        docType: String,
        attachments: [LocalAttachmentItem],
        initialEntryVersion: Int? = nil
    ) async -> MiniBiApiResult<String> {
        let outcome = await uploadEntryDocumentAttachmentsWithVersion(
            entryIdUuid: entryIdUuid,
            docType: docType,
            attachments: attachments,
            initialEntryVersion: initialEntryVersion
        )
        return outcome.result
    }

    public func uploadEntryDocumentAttachmentsWithVersion(
        entryIdUuid: String,
        docType: String,
        attachments: [LocalAttachmentItem],
        initialEntryVersion: Int? = nil
    ) async -> (result: MiniBiApiResult<String>, nextEntryVersion: Int) {
        let fallbackVersion = max(1, initialEntryVersion ?? 1)
        guard !attachments.isEmpty else {
            return (.failure(message: "Không có tệp đính kèm để tải lên."), fallbackVersion)
        }

        let prepared: PreparedContractUploadPayload
        do {
            prepared = try PdfContractMerger.prepareContractUploadPayload(attachments: attachments)
        } catch {
            return (.failure(message: error.localizedDescription), fallbackVersion)
        }

        let cleanEntryId = entryIdUuid.trimmingCharacters(in: .whitespacesAndNewlines)
        let encodedEntryId = cleanEntryId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanEntryId

        var expectedEntryVersion = fallbackVersion
        if initialEntryVersion == nil {
            let detailRes = await httpClient.request(
                method: "GET",
                path: "/api/direct-entry/entries/\(encodedEntryId)"
            )
            if detailRes.isSuccess, let dRoot = detailRes.jsonObject {
                let entryObj = (dRoot["entry"] as? [String: Any]) ?? (dRoot["data"] as? [String: Any]) ?? dRoot
                expectedEntryVersion = max(1, entryObj.optInt("version", "entry_version", defaultValue: 1))
            }
        }

        let baseIdemKey = UUID().uuidString.lowercased()

        func reserveSlot(version: Int, idemSuffix: String) async -> BackendHttpResponse {
            let initBody: [String: Any] = [
                "document_type": docType,
                "expected_entry_version": version,
                "size_bytes": prepared.data.count,
                "mime_type": prepared.mimeType,
                "reason": NSNull()
            ]
            return await httpClient.request(
                method: "POST",
                path: "/api/direct-entry/entries/\(encodedEntryId)/documents",
                jsonBody: initBody,
                additionalHeaders: ["Idempotency-Key": "\(baseIdemKey):\(idemSuffix)"]
            )
        }

        // Step 1: Reserve R2 presigned upload slot (`POST /api/direct-entry/entries/{entryId}/documents`)
        var initRes = await reserveSlot(version: expectedEntryVersion, idemSuffix: "reserve")
        if initRes.statusCode == 409 {
            let detailRes = await httpClient.request(
                method: "GET",
                path: "/api/direct-entry/entries/\(encodedEntryId)"
            )
            if detailRes.isSuccess, let dRoot = detailRes.jsonObject {
                let entryObj = (dRoot["entry"] as? [String: Any]) ?? (dRoot["data"] as? [String: Any]) ?? dRoot
                let latestVer = max(1, entryObj.optInt("version", "entry_version", defaultValue: expectedEntryVersion))
                expectedEntryVersion = latestVer
                initRes = await reserveSlot(version: latestVer, idemSuffix: "reserve:v\(latestVer)")
            }
        }

        if initRes.isSuccess, let root = initRes.jsonObject {
            let docObj = (root["document"] as? [String: Any]) ?? root
            let docId = root.optString("document_id", "doc_id", "id")
                .ifEmpty(docObj.optString("document_id", "doc_id", "id"))
            let reservedEntryVersion = max(
                expectedEntryVersion,
                root.optInt("entry_version", defaultValue: docObj.optInt("entry_version", defaultValue: expectedEntryVersion))
            )
            let uploadObj = (root["upload"] as? [String: Any]) ?? (docObj["upload"] as? [String: Any])
            let uploadUrl = uploadObj?.optString("url", "upload_url") ?? ""

            if !docId.isEmpty && !uploadUrl.isEmpty {
                var extraHeaders: [String: String] = [:]
                if let headersDict = uploadObj?["headers"] as? [String: Any] {
                    for (k, v) in headersDict {
                        if let s = v as? String { extraHeaders[k] = s }
                    }
                }
                let putRes = await httpClient.putPresignedUrl(
                    uploadUrl: uploadUrl,
                    fileData: prepared.data,
                    contentType: prepared.mimeType,
                    additionalHeaders: extraHeaders
                )
                guard putRes.isSuccess else {
                    return (
                        .failure(
                            message: putRes.errorMessage ?? "Tải tệp lên kho lưu trữ thất bại.",
                            httpStatus: putRes.statusCode,
                            errorCode: putRes.errorCode
                        ),
                        reservedEntryVersion
                    )
                }

                let encodedDocId = docId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? docId
                let finalizeRes = await httpClient.request(
                    method: "POST",
                    path: "/api/direct-entry/entries/\(encodedEntryId)/documents/\(encodedDocId)/finalize",
                    jsonBody: [
                        "expected_entry_version": reservedEntryVersion
                    ],
                    additionalHeaders: ["Idempotency-Key": "\(baseIdemKey):finalize"]
                )
                guard finalizeRes.isSuccess else {
                    return (
                        .failure(
                            message: finalizeRes.errorMessage ?? "Xác nhận tệp đính kèm thất bại.",
                            httpStatus: finalizeRes.statusCode,
                            errorCode: finalizeRes.errorCode
                        ),
                        reservedEntryVersion
                    )
                }
                httpClient.invalidateBinaryCache()
                let finalizedVersion = finalizeRes.jsonObject?.optInt("entry_version", defaultValue: reservedEntryVersion) ?? reservedEntryVersion
                return (.success(docId, statusCode: finalizeRes.statusCode), max(reservedEntryVersion, finalizedVersion))
            }

            let fileUrl = docObj.optString("file_url", "url", "document_id", "doc_id", defaultValue: prepared.fileName)
            return (.success(fileUrl, statusCode: initRes.statusCode), reservedEntryVersion)
        }

        // Fallback to multipart upload for legacy/test servers
        let response = await httpClient.uploadMultipart(
            path: "/api/direct-entry/entries/\(encodedEntryId)/documents",
            fieldName: "file",
            fileName: prepared.fileName,
            mimeType: prepared.mimeType,
            fileData: prepared.data,
            extraFields: ["doc_type": docType, "document_type": docType]
        )
        guard response.isSuccess else {
            return (
                .failure(
                    message: response.errorMessage ?? initRes.errorMessage ?? "Tải tệp hồ sơ cấp mới thất bại.",
                    httpStatus: response.statusCode,
                    errorCode: response.errorCode ?? initRes.errorCode
                ),
                expectedEntryVersion
            )
        }

        let fileUrl = response.jsonObject?.optString("file_url", "url", defaultValue: prepared.fileName) ?? prepared.fileName
        return (.success(fileUrl, statusCode: response.statusCode), expectedEntryVersion)
    }

    // MARK: - Direct Edit Worker Profile (GET Detail, Patch Builder, Field Validation, Privileged Edit)

    private static let employeeCodeRegex = try! NSRegularExpression(pattern: #"^hrp-(0[1-9]|1[0-2])\d{2}-\d{6}$"#, options: [.caseInsensitive])
    private static let safeCatalogIdRegex = try! NSRegularExpression(pattern: #"^[A-Za-z0-9_.\-]{1,128}$"#)
    private static let laborTypeKeyRegex = try! NSRegularExpression(pattern: #"^[A-Za-z0-9_\-]{1,64}$"#)
    private static let paymentAccountDigitsRegex = try! NSRegularExpression(pattern: #"^\d{1,64}$"#)

    private static func matchesRegex(_ regex: NSRegularExpression, _ text: String) -> Bool {
        let range = NSRange(location: 0, length: text.utf16.count)
        return regex.firstMatch(in: text, options: [], range: range) != nil
    }

    private static func isValidCccd9Or12(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.allSatisfy(\.isNumber) && (trimmed.count == 9 || trimmed.count == 12) else {
            return false
        }
        return trimmed != "000000000" && trimmed != "000000000000"
    }

    private static func isValidCccd12(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.allSatisfy(\.isNumber) && trimmed.count == 12 && trimmed != "000000000000"
    }

    public static func parseWorkerEntryDetail(_ root: [String: Any]) -> WorkerEntryDetail {
        let entryObj = (root["entry"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root

        let entryId = entryObj.optString("entry_id", "id")
        let submissionId = entryObj.optString("submission_id")
        let version = max(1, entryObj.optInt("version", "entry_version", defaultValue: 1))
        let catalog = DirectEntryService.shared.lastLoadedCatalog
        let projectObj = entryObj["project"] as? [String: Any]
        let projectId = projectObj?.optString("project_id", "id") ?? entryObj.optString("project_id")
        let rawProjectDisplay = projectObj?.optString("display_name", "display", "name")
            ?? entryObj.optString("project_display", "project_name", "du_an")
        let projectDisplay: String = {
            if (rawProjectDisplay.isEmpty || rawProjectDisplay == projectId),
               let matchedProj = catalog?.projects.first(where: { $0.id.caseInsensitiveCompare(projectId) == .orderedSame }) {
                return matchedProj.name
            }
            return rawProjectDisplay.isEmpty ? projectId : rawProjectDisplay
        }()
        let firstWorkDate = entryObj.optString("first_work_date", "ngay_vao_lam")
        let employeeCode = entryObj.optString("employee_code", "ma_nld")
        let recruiterObj = entryObj["recruiter"] as? [String: Any]
        let recruiterId = recruiterObj?.optString("recruiter_id", "id") ?? entryObj.optString("recruiter_id")
        let rawRecruiterDisplay = recruiterObj?.optString("display_name", "display", "name")
            ?? entryObj.optString("recruiter_display", "recruiter_name", "nguoi_tuyen")
        let recruiterDisplay: String = {
            if !rawRecruiterDisplay.isEmpty && !WorkerItem.isUuidLike(rawRecruiterDisplay) {
                return rawRecruiterDisplay
            }
            if !recruiterId.isEmpty,
               let matchedRec = catalog?.recruiters.first(where: { $0.id.caseInsensitiveCompare(recruiterId) == .orderedSame }) {
                return matchedRec.name
            }
            return WorkerItem.isUuidLike(rawRecruiterDisplay) ? "" : rawRecruiterDisplay
        }()
        let teamId = entryObj.optNullableString("team_id")
        let providerType = entryObj.optString("provider_type", defaultValue: "hrp")
        let laborType = entryObj.optString("labor_type", defaultValue: "thoi_vu")
        let rawLaborTypeDisplay = entryObj.optString("labor_type_display", defaultValue: laborType)
        let laborTypeDisplay: String = {
            if let matchedLt = catalog?.laborTypes.first(where: { $0.id.caseInsensitiveCompare(laborType) == .orderedSame }) {
                return matchedLt.name
            }
            let lower = rawLaborTypeDisplay.lowercased()
            if lower == "thoi_vu" || lower == "temporary" { return "Thời vụ" }
            if lower == "chinh_thuc" || lower == "official" || lower == "permanent" { return "Chính thức" }
            return rawLaborTypeDisplay
        }()
        let allowedObj = entryObj["allowed_actions"] as? [String: Any]
        let canDirectEdit = allowedObj?.optBool("direct_edit", defaultValue: true)
            ?? entryObj.optBool("can_direct_edit", defaultValue: true)

        let wdObj = (entryObj["worker_details"] as? [String: Any])
            ?? (entryObj["worker"] as? [String: Any])
            ?? [:]
        let displayName = wdObj.optString("display_name")
            .ifEmpty(entryObj.optString("display_name", "ho_ten", "full_name"))

        func extractTriState(_ dict: [String: Any], _ key: String, fallbackKey: String? = nil) -> (state: String, value: String?) {
            let raw = dict[key] ?? (fallbackKey.flatMap { dict[$0] })
            guard let raw, !(raw is NSNull) else {
                return ("omitted", nil)
            }
            if let sub = raw as? [String: Any] {
                let st = sub.optString("state", defaultValue: "omitted")
                let val = sub.optNullableString("value")
                return (st, st == "provided" ? val : nil)
            }
            if let s = raw as? String {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? ("omitted", nil) : ("provided", trimmed)
            }
            return ("omitted", nil)
        }

        var wdStates: [String: String] = [:]
        let genderPair = extractTriState(wdObj, "gender")
        wdStates["gender"] = genderPair.state
        let dobPair = extractTriState(wdObj, "date_of_birth")
        wdStates["date_of_birth"] = dobPair.state
        let nidPair = extractTriState(wdObj, "national_id")
        wdStates["national_id"] = nidPair.state
        let issuedAtPair = extractTriState(wdObj, "national_id_issued_at", fallbackKey: "national_id_issued_date")
        wdStates["national_id_issued_at"] = issuedAtPair.state
        let issuedPlacePair = extractTriState(wdObj, "national_id_issued_place")
        wdStates["national_id_issued_place"] = issuedPlacePair.state
        let addressPair = extractTriState(wdObj, "address")
        wdStates["address"] = addressPair.state
        let phonePair = extractTriState(wdObj, "phone", fallbackKey: "phone_number")
        wdStates["phone"] = phonePair.state

        let notePair = extractTriState(entryObj, "general_note")
        let generalNote = notePair.value ?? entryObj.optNullableString("general_note", "note")

        let paymentObj = (entryObj["payment"] as? [String: Any])
            ?? (wdObj["payment"] as? [String: Any])
        let paymentState = paymentObj?.optString("state", defaultValue: "empty") ?? "empty"
        let bankId = paymentObj?.optNullableString("bank_id")
        let rawBankName = paymentObj?.optNullableString("bank_name")
        let bankDisplay = paymentObj?.optNullableString("bank_display", "bank_name", "bank_id")
        let accountNumber = paymentObj?.optNullableString("account_number")
        let accountHolderName = paymentObj?.optNullableString("account_holder_name")

        let empObj = entryObj["employment_status"] as? [String: Any]
        let employmentStatus = (empObj?.optString("status") ?? entryObj.optString("employment_status", defaultValue: "ON")).uppercased()
        let employmentEffectiveDate = empObj?.optString("effective_date")
            ?? entryObj.optString("employment_effective_date", defaultValue: firstWorkDate)
        let employmentStatusVersion = max(1, empObj?.optInt("version", defaultValue: 1) ?? 1)
        let employmentLeaveReason = empObj?.optNullableString("leave_reason")
            ?? entryObj.optNullableString("employment_leave_reason", "leave_reason")

        let portraitWrapper = entryObj["worker_portrait"] as? [String: Any]
        let currentPortraitObj = (portraitWrapper?["current"] as? [String: Any]) ?? portraitWrapper
        let portraitDocId = currentPortraitObj?.optString("document_id", "doc_id", "id") ?? ""
        let portraitVer = max(1, currentPortraitObj?.optInt("file_version", "version", defaultValue: 1) ?? 1)
        let portraitUpStatus = (currentPortraitObj?.optString("upload_status", "status", defaultValue: "READY") ?? "READY").uppercased()
        let isPortraitReady = !portraitDocId.isEmpty && (portraitUpStatus == "READY" || portraitUpStatus == "UPLOADED")
        let computedPortraitPreviewPath: String? = (!entryId.isEmpty && isPortraitReady)
            ? "/api/direct-entry/entries/\(entryId)/documents/\(portraitDocId)/preview"
            : nil
        let computedPortraitDownloadPath: String? = (!entryId.isEmpty && isPortraitReady)
            ? "/api/direct-entry/entries/\(entryId)/documents/\(portraitDocId)/download"
            : nil

        var docs: [WorkerDocumentSummary] = []
        var fileVers: [String: Int] = [:]
        for d in entryObj.optObjectArray("documents") {
            let dType = d.optString("document_type", "doc_type", "type").uppercased()
            guard !dType.isEmpty else { continue }
            let docId = d.optString("document_id", "doc_id", "id")
            let fVer = max(1, d.optInt("file_version", "version", defaultValue: 1))
            fileVers[dType] = fVer
            let upStatus = d.optString("upload_status", "status", defaultValue: "READY").uppercased()
            let isReady = upStatus == "READY" || upStatus == "UPLOADED"
            let mime = d.optString("mime_type").lowercased()
            let computedPreviewPath: String? = {
                guard !entryId.isEmpty && !docId.isEmpty && isReady else { return nil }
                if dType == "WORKER_PORTRAIT" {
                    return "/api/direct-entry/entries/\(entryId)/documents/\(docId)/preview"
                }
                if (dType == "CCCD_FRONT" || dType == "CCCD_BACK") && (mime.isEmpty || mime.hasPrefix("image/")) {
                    return "/api/direct-entry/entries/\(entryId)/documents/\(docId)/cccd-preview"
                }
                return nil
            }()
            let computedDownloadPath: String? = (!entryId.isEmpty && !docId.isEmpty && isReady)
                ? "/api/direct-entry/entries/\(entryId)/documents/\(docId)/download"
                : nil

            let dlPath = d.optNullableString("download_path", "download_url", "file_url") ?? computedDownloadPath
            let pvPath = d.optNullableString("preview_path", "preview_url") ?? computedPreviewPath
            let hasFile = d.optBool("has_file", defaultValue: dlPath != nil || pvPath != nil || isReady)
            docs.append(
                WorkerDocumentSummary(
                    documentType: dType,
                    uploadStatus: upStatus,
                    fileVersion: fVer,
                    downloadPath: dlPath,
                    previewPath: pvPath,
                    hasFile: hasFile
                )
            )
        }

        let explicitPortraitPreview = entryObj.optNullableString("portrait_preview_path")
        let explicitPortraitDownload = entryObj.optNullableString("portrait_download_path")
        let portraitPreviewPath = explicitPortraitPreview
            ?? computedPortraitPreviewPath
            ?? docs.first(where: { $0.documentType == "WORKER_PORTRAIT" })?.previewPath
        let portraitDownloadPath = explicitPortraitDownload
            ?? computedPortraitDownloadPath
            ?? docs.first(where: { $0.documentType == "WORKER_PORTRAIT" })?.downloadPath

        if !docs.contains(where: { $0.documentType == "WORKER_PORTRAIT" && $0.hasFile }) &&
            (isPortraitReady || portraitPreviewPath != nil || portraitDownloadPath != nil) {
            docs.removeAll { $0.documentType == "WORKER_PORTRAIT" }
            docs.insert(
                WorkerDocumentSummary(
                    documentType: "WORKER_PORTRAIT",
                    uploadStatus: "READY",
                    fileVersion: portraitVer,
                    downloadPath: portraitDownloadPath,
                    previewPath: portraitPreviewPath,
                    hasFile: true
                ),
                at: 0
            )
            fileVers["WORKER_PORTRAIT"] = portraitVer
        }

        return WorkerEntryDetail(
            entryId: entryId,
            submissionId: submissionId,
            version: version,
            projectId: projectId,
            projectDisplay: projectDisplay,
            firstWorkDate: firstWorkDate,
            employeeCode: employeeCode,
            recruiterId: recruiterId,
            recruiterDisplay: recruiterDisplay,
            teamId: teamId,
            providerType: providerType,
            laborType: laborType,
            laborTypeDisplay: laborTypeDisplay,
            displayName: displayName,
            gender: genderPair.value ?? entryObj.optNullableString("gender"),
            dateOfBirth: dobPair.value ?? entryObj.optNullableString("date_of_birth"),
            nationalId: nidPair.value ?? entryObj.optNullableString("national_id"),
            nationalIdIssuedAt: issuedAtPair.value ?? entryObj.optNullableString("national_id_issued_at", "national_id_issued_date"),
            nationalIdIssuedPlace: issuedPlacePair.value ?? entryObj.optNullableString("national_id_issued_place"),
            address: addressPair.value ?? entryObj.optNullableString("address"),
            phone: phonePair.value ?? entryObj.optNullableString("phone", "phone_number"),
            generalNote: generalNote,
            paymentState: paymentState,
            bankId: bankId,
            bankName: bankDisplay,
            rawBankName: rawBankName,
            accountNumber: accountNumber,
            accountHolderName: accountHolderName,
            employmentStatus: employmentStatus,
            employmentEffectiveDate: employmentEffectiveDate,
            employmentStatusVersion: employmentStatusVersion,
            employmentLeaveReason: employmentLeaveReason,
            portraitPreviewPath: portraitPreviewPath,
            portraitDownloadPath: portraitDownloadPath,
            documents: docs,
            workerDetailsStates: wdStates,
            fileVersions: fileVers,
            canDirectEdit: canDirectEdit
        )
    }

    public static func buildDirectEditFormInput(from detail: WorkerEntryDetail) -> DirectEditFormInput {
        let resolvedBank = VietQrBankCatalog.resolveForInitialValue(
            bankId: detail.bankId,
            bankName: detail.rawBankName ?? detail.bankName
        )
        return DirectEditFormInput(
            employeeCode: detail.employeeCode,
            projectId: detail.projectId,
            projectDisplay: detail.projectDisplay,
            firstWorkDateUi: DirectEntryContract.isoDateToUiDate(detail.firstWorkDate),
            recruiterId: detail.recruiterId,
            recruiterDisplay: detail.recruiterDisplay,
            laborTypeKey: detail.laborType,
            laborTypeDisplay: detail.laborTypeDisplay,
            statusCode: detail.employmentStatus,
            statusEffectiveDateUi: DirectEntryContract.isoDateToUiDate(
                detail.employmentEffectiveDate.isEmpty ? detail.firstWorkDate : detail.employmentEffectiveDate
            ),
            leaveReason: detail.employmentLeaveReason ?? "",
            displayName: detail.displayName,
            gender: detail.gender?.uppercased() ?? "",
            dateOfBirthUi: DirectEntryContract.isoDateToUiDate(detail.dateOfBirth ?? ""),
            nationalId: detail.nationalId ?? "",
            nationalIdIssuedAtUi: DirectEntryContract.isoDateToUiDate(detail.nationalIdIssuedAt ?? ""),
            nationalIdIssuedPlace: detail.nationalIdIssuedPlace ?? "",
            address: detail.address ?? "",
            phone: detail.phone ?? "",
            accountHolderName: detail.accountHolderName ?? "",
            accountNumber: detail.accountNumber ?? "",
            bankId: resolvedBank.bankId,
            bankName: resolvedBank.bankName,
            isCustomBank: resolvedBank.bankId == nil && !resolvedBank.bankName.isEmpty
        )
    }

    public static func validateDirectEditFormFields(
        baseline: WorkerEntryDetail,
        input: DirectEditFormInput
    ) -> [String: String] {
        var errors: [String: String] = [:]

        let cleanEmployeeCode = input.employeeCode.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanEmployeeCode != baseline.employeeCode.trimmingCharacters(in: .whitespacesAndNewlines) &&
            !matchesRegex(employeeCodeRegex, cleanEmployeeCode) {
            errors["employee_code"] = "Mã NLĐ sai định dạng. Hãy nhập theo mẫu hrp-MMYY-XXXXXX (ví dụ: hrp-1026-001456)."
        }

        let cleanProjectId = input.projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanProjectId != baseline.projectId.trimmingCharacters(in: .whitespacesAndNewlines) &&
            !matchesRegex(safeCatalogIdRegex, cleanProjectId) {
            errors["project_id"] = "Vui lòng chọn một Dự án hợp lệ từ danh sách."
        }

        let cleanLaborType = input.laborTypeKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanLaborType != baseline.laborType.trimmingCharacters(in: .whitespacesAndNewlines) &&
            !matchesRegex(laborTypeKeyRegex, cleanLaborType) {
            errors["labor_type"] = "Vui lòng chọn Loại hình lao động hợp lệ từ danh sách."
        }

        let cleanRecruiterId = input.recruiterId.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanRecruiterId != baseline.recruiterId.trimmingCharacters(in: .whitespacesAndNewlines) &&
            UUID(uuidString: cleanRecruiterId) == nil {
            errors["recruiter_id"] = "Vui lòng chọn Người tuyển hợp lệ từ danh sách."
        }

        let isoFirstWork = DirectEntryContract.uiDateToIsoDate(input.firstWorkDateUi)
        if isoFirstWork.isEmpty {
            errors["first_work_date"] = "Ngày bắt đầu không hợp lệ. Hãy nhập đúng DD/MM/YYYY (ví dụ: 30/10/2026)."
        }

        let cleanStatus = input.statusCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if !cleanStatus.isEmpty {
            let isoEff = DirectEntryContract.uiDateToIsoDate(input.statusEffectiveDateUi)
            if isoEff.isEmpty {
                errors["employment_effective_date"] = "Ngày hiệu lực không hợp lệ. Hãy nhập đúng DD/MM/YYYY (ví dụ: 30/10/2026)."
            } else {
                let statusChanged = cleanStatus != baseline.employmentStatus.uppercased() ||
                    isoEff != baseline.employmentEffectiveDate ||
                    (cleanStatus == "OFF" && input.leaveReason.trimmingCharacters(in: .whitespacesAndNewlines) != (baseline.employmentLeaveReason ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
                if statusChanged && cleanStatus == "OFF" && input.leaveReason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    errors["employment_leave_reason"] = "Khi chọn trạng thái Đã nghỉ, bắt buộc phải nhập Lý do nghỉ việc."
                }
            }
        }

        let cleanDisplayName = input.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNationalId = input.nationalId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanNationalId.isEmpty && !isValidCccd9Or12(cleanNationalId) {
            errors["national_id"] = "Số CCCD/CMND phải gồm đúng 9 hoặc 12 chữ số hợp lệ (hiện đang nhập \(cleanNationalId.count) chữ số), hoặc xóa trống."
        }

        let hasValid12Cccd = isValidCccd12(cleanNationalId)
        if cleanDisplayName.isEmpty && !hasValid12Cccd && cleanDisplayName != baseline.displayName.trimmingCharacters(in: .whitespacesAndNewlines) {
            errors["display_name"] = "Họ và tên không được để trống (trừ khi đã nhập đủ 12 chữ số CCCD hợp lệ)."
        } else if cleanDisplayName.count > 256 {
            errors["display_name"] = "Họ và tên không hợp lệ (tối đa 256 ký tự)."
        }

        let cleanDob = input.dateOfBirthUi.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanDob.isEmpty {
            let baseDobUi = DirectEntryContract.isoDateToUiDate(baseline.dateOfBirth ?? "")
            if cleanDob != baseDobUi && DirectEntryContract.uiDateToIsoDate(cleanDob).isEmpty {
                errors["date_of_birth"] = "Ngày sinh không hợp lệ. Hãy nhập đúng DD/MM/YYYY (ví dụ: 15/05/1995) hoặc xóa trống."
            }
        }

        let cleanIssuedAt = input.nationalIdIssuedAtUi.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanIssuedAt.isEmpty {
            let baseIssuedUi = DirectEntryContract.isoDateToUiDate(baseline.nationalIdIssuedAt ?? "")
            if cleanIssuedAt != baseIssuedUi && DirectEntryContract.uiDateToIsoDate(cleanIssuedAt).isEmpty {
                errors["national_id_issued_at"] = "Ngày cấp CCCD không hợp lệ. Hãy nhập đúng DD/MM/YYYY (ví dụ: 20/08/2021) hoặc xóa trống."
            }
        }

        let cleanAccount = input.accountNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanAccount.isEmpty && !matchesRegex(paymentAccountDigitsRegex, cleanAccount) {
            errors["account_number"] = "Số tài khoản chỉ được chứa chữ số (1–64 chữ số), không chứa dấu cách hay ký tự chữ."
        }

        let cleanBankName = input.bankName.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanBankName.count > 256 {
            errors["bank_query_text"] = "Tên ngân hàng không hợp lệ (tối đa 256 ký tự)."
        }

        let cleanHolder = input.accountHolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanHolder.count > 256 {
            errors["account_holder_name"] = "Tên chủ tài khoản không hợp lệ (tối đa 256 ký tự)."
        }

        return errors
    }

    public static func buildDirectEditPatch(
        baseline: WorkerEntryDetail,
        input: DirectEditFormInput,
        canEditEmploymentStatus: Bool = true
    ) -> [String: Any] {
        var patch: [String: Any] = [:]

        let cleanProjectId = input.projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanProjectId.isEmpty && cleanProjectId != baseline.projectId.trimmingCharacters(in: .whitespacesAndNewlines) {
            patch["project_id"] = cleanProjectId
        }

        let isoFirstWork = DirectEntryContract.uiDateToIsoDate(input.firstWorkDateUi)
        if !isoFirstWork.isEmpty && isoFirstWork != baseline.firstWorkDate.trimmingCharacters(in: .whitespacesAndNewlines) {
            patch["first_work_date"] = isoFirstWork
        }

        let cleanEmployeeCode = input.employeeCode.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanEmployeeCode.isEmpty && cleanEmployeeCode != baseline.employeeCode.trimmingCharacters(in: .whitespacesAndNewlines) {
            patch["employee_code"] = cleanEmployeeCode.lowercased()
        }

        let cleanRecruiterId = input.recruiterId.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanRecruiterId.isEmpty && cleanRecruiterId != baseline.recruiterId.trimmingCharacters(in: .whitespacesAndNewlines) {
            patch["recruiter_id"] = cleanRecruiterId
        }

        let cleanLaborType = input.laborTypeKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanLaborType.isEmpty && cleanLaborType != baseline.laborType.trimmingCharacters(in: .whitespacesAndNewlines) {
            patch["labor_type"] = cleanLaborType
        }

        // Personal section (`worker_details`)
        func resolveOptionalField(
            fieldKey: String,
            baselineValue: String?,
            rawInput: String,
            isDate: Bool
        ) -> (dict: [String: Any]?, changed: Bool) {
            let baseState = baseline.workerDetailsStates[fieldKey] ?? ((baselineValue?.isEmpty == false) ? "provided" : "omitted")
            let baseVal = baselineValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
            let normalizedVal: String = {
                if trimmed.isEmpty { return "" }
                if isDate {
                    let iso = DirectEntryContract.uiDateToIsoDate(trimmed)
                    return iso.isEmpty ? trimmed : iso
                }
                return trimmed
            }()

            if normalizedVal.isEmpty {
                if baseState == "provided" {
                    return (["state": "intentionally_blank"], true)
                }
                return (["state": baseState], false)
            }
            if baseState == "provided" && normalizedVal == baseVal {
                return (["state": "provided", "value": normalizedVal], false)
            }
            return (["state": "provided", "value": normalizedVal], true)
        }

        let normalizedGender: String = {
            let g = input.gender.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if g == "NAM" || g == "MALE" { return "MALE" }
            if g == "NỮ" || g == "NU" || g == "FEMALE" { return "FEMALE" }
            return ""
        }()

        let genderRes = resolveOptionalField(fieldKey: "gender", baselineValue: baseline.gender, rawInput: normalizedGender, isDate: false)
        let dobRes = resolveOptionalField(fieldKey: "date_of_birth", baselineValue: baseline.dateOfBirth, rawInput: input.dateOfBirthUi, isDate: true)
        let nidRes = resolveOptionalField(fieldKey: "national_id", baselineValue: baseline.nationalId, rawInput: input.nationalId, isDate: false)
        let issuedAtRes = resolveOptionalField(fieldKey: "national_id_issued_at", baselineValue: baseline.nationalIdIssuedAt, rawInput: input.nationalIdIssuedAtUi, isDate: true)
        let issuedPlaceRes = resolveOptionalField(fieldKey: "national_id_issued_place", baselineValue: baseline.nationalIdIssuedPlace, rawInput: input.nationalIdIssuedPlace, isDate: false)
        let addressRes = resolveOptionalField(fieldKey: "address", baselineValue: baseline.address, rawInput: input.address, isDate: false)
        let phoneRes = resolveOptionalField(fieldKey: "phone", baselineValue: baseline.phone, rawInput: input.phone, isDate: false)

        let cleanDisplayName = input.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let personalChanged = cleanDisplayName != baseline.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            || genderRes.changed || dobRes.changed || nidRes.changed
            || issuedAtRes.changed || issuedPlaceRes.changed || addressRes.changed || phoneRes.changed

        if personalChanged {
            var wd: [String: Any] = [
                "display_name": cleanDisplayName,
                "date_of_birth": dobRes.dict ?? ["state": "omitted"],
                "national_id": nidRes.dict ?? ["state": "omitted"],
                "address": addressRes.dict ?? ["state": "omitted"],
                "phone": phoneRes.dict ?? ["state": "omitted"]
            ]
            if let gDict = genderRes.dict { wd["gender"] = gDict }
            if let iaDict = issuedAtRes.dict { wd["national_id_issued_at"] = iaDict }
            if let ipDict = issuedPlaceRes.dict { wd["national_id_issued_place"] = ipDict }
            patch["worker_details"] = wd
        }

        // Payment section (`payment`)
        let trimmedAccount = input.accountNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedHolder = input.accountHolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedBankId = input.bankId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let trimmedBankName = input.bankName.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmedAccount.isEmpty && trimmedHolder.isEmpty && trimmedBankId.isEmpty && trimmedBankName.isEmpty {
            if baseline.paymentState == "provided" {
                patch["payment"] = [
                    "state": "intentionally_blank",
                    "account_number": NSNull(),
                    "bank_id": NSNull(),
                    "bank_name": NSNull(),
                    "account_holder_name": NSNull()
                ]
            }
        } else {
            let resolvedBank = VietQrBankCatalog.resolveForInitialValue(
                bankId: trimmedBankId.isEmpty ? nil : trimmedBankId,
                bankName: trimmedBankName.isEmpty ? nil : trimmedBankName
            )
            let nextBankId = resolvedBank.bankId
            let nextBankName = resolvedBank.bankName.isEmpty ? nil : resolvedBank.bankName
            let nextAcc: String? = trimmedAccount.isEmpty ? nil : trimmedAccount
            let nextHolder: String? = trimmedHolder.isEmpty ? nil : trimmedHolder

            let baseAcc = baseline.accountNumber?.trimmingCharacters(in: .whitespacesAndNewlines)
            let baseBankId = baseline.bankId?.trimmingCharacters(in: .whitespacesAndNewlines)
            let baseBankName = (baseline.rawBankName ?? baseline.bankName)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let baseHolder = baseline.accountHolderName?.trimmingCharacters(in: .whitespacesAndNewlines)

            let bankSame = (baseBankId ?? "") == (nextBankId ?? "") &&
                (nextBankId != nil || (baseBankName ?? "") == (nextBankName ?? ""))
            let paymentUnchanged = baseline.paymentState == "provided"
                && (baseAcc ?? "") == (nextAcc ?? "")
                && bankSame
                && (baseHolder ?? "") == (nextHolder ?? "")

            if !paymentUnchanged {
                var payDict: [String: Any] = [
                    "state": "provided",
                    "account_number": nextAcc ?? NSNull(),
                    "bank_id": nextBankId ?? NSNull(),
                    "account_holder_name": nextHolder ?? NSNull()
                ]
                if nextBankName != nil || nextBankId == nil {
                    payDict["bank_name"] = nextBankName ?? NSNull()
                }
                patch["payment"] = payDict
            }
        }

        // Employment status (`employment_status`)
        if canEditEmploymentStatus {
            let cleanStatus = input.statusCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let isoEff = DirectEntryContract.uiDateToIsoDate(input.statusEffectiveDateUi)
            let cleanLeaveReason = input.leaveReason.trimmingCharacters(in: .whitespacesAndNewlines)
            let statusChanged = !cleanStatus.isEmpty && (
                cleanStatus != baseline.employmentStatus.uppercased() ||
                (!isoEff.isEmpty && isoEff != baseline.employmentEffectiveDate) ||
                (cleanStatus == "OFF" && cleanLeaveReason != (baseline.employmentLeaveReason ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
            )
            if statusChanged {
                var empDict: [String: Any] = [
                    "status": cleanStatus,
                    "effective_date": isoEff
                ]
                if cleanStatus == "OFF" {
                    empDict["leave_reason"] = cleanLeaveReason
                } else {
                    empDict["leave_reason"] = NSNull()
                }
                patch["employment_status"] = empDict
            }
        }

        return patch
    }

    public static func describePrivilegedEditInvalidError(patch: [String: Any]) -> String {
        let keys = Set(patch.keys)
        if keys == ["payment"] {
            return "Không lưu được Thông tin tài khoản ngân hàng: Máy chủ từ chối giá trị ngân hàng hoặc thiếu thông tin. Hãy kiểm tra Số tài khoản (chỉ gồm chữ số), chọn một Ngân hàng từ danh sách gợi ý VietQR (ví dụ: Vietcombank, BIDV, Techcombank...) và nhập Tên chủ tài khoản."
        }
        var hints: [String] = []
        if keys.contains("employee_code") {
            let codeVal = patch["employee_code"] as? String ?? ""
            hints.append("• Mã NLĐ (\"\(codeVal)\"): Phải đúng định dạng hrp-XXXX-XXXXXX (ví dụ hrp-2026-001456).")
        }
        if keys.contains("first_work_date") || keys.contains("project_id") || keys.contains("recruiter_id") || keys.contains("labor_type") {
            hints.append("• Thông tin công việc: Kiểm tra Ngày bắt đầu (DD/MM/YYYY), Dự án, Người tuyển và Loại hình lao động phải đang hoạt động hợp lệ.")
        }
        if keys.contains("employment_status") {
            hints.append("• Trạng thái làm việc: Kiểm tra Ngày hiệu lực (DD/MM/YYYY) và nhập Lý do nghỉ nếu chọn Đã nghỉ.")
        }
        if keys.contains("worker_details") {
            hints.append("• Thông tin cá nhân: Kiểm tra Họ và tên, Số CCCD (đúng 9 hoặc 12 chữ số), Ngày sinh và Ngày cấp CCCD (DD/MM/YYYY).")
        }
        if keys.contains("payment") {
            hints.append("• Thông tin tài khoản ngân hàng: Kiểm tra Số tài khoản (chỉ gồm chữ số), chọn Ngân hàng từ danh sách gợi ý VietQR và nhập Tên chủ tài khoản.")
        }
        if hints.isEmpty {
            return "Dữ liệu chỉnh sửa chưa hợp lệ. Vui lòng kiểm tra lại các trường vừa thay đổi."
        }
        return "Không lưu được do trường vừa sửa chưa hợp lệ:\n" + hints.joined(separator: "\n")
    }

    public func fetchWorkerEntryDetail(entryId: String) async -> MiniBiApiResult<WorkerEntryDetail> {
        let cleanId = entryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không tìm thấy mã định danh hồ sơ NLĐ.")
        }
        if lastLoadedCatalog == nil {
            _ = await fetchCatalogOptions()
        }
        let encoded = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let response = await httpClient.request(
            method: "GET",
            path: "/api/direct-entry/entries/\(encoded)"
        )
        guard response.isSuccess, let root = response.jsonObject else {
            return .failure(
                message: response.errorMessage ?? "Không thể tải chi tiết hồ sơ NLĐ từ máy chủ.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        let detail = Self.parseWorkerEntryDetail(root)
        return .success(detail, statusCode: response.statusCode)
    }

    public func submitPrivilegedEdit(
        entryId: String,
        expectedEntryVersion: Int,
        patch: [String: Any],
        reason: String? = nil
    ) async -> MiniBiApiResult<WorkerEntryDetail> {
        let cleanId = entryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            return .failure(message: "Không tìm thấy mã định danh hồ sơ NLĐ.")
        }
        let encoded = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
        let idemKey = UUID().uuidString.lowercased()
        var body: [String: Any] = [
            "expected_entry_version": max(1, expectedEntryVersion),
            "patch": patch
        ]
        if let r = reason?.trimmingCharacters(in: .whitespacesAndNewlines), !r.isEmpty {
            body["reason"] = r
        } else {
            body["reason"] = NSNull()
        }

        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/entries/\(encoded)/privileged-edit",
            jsonBody: body,
            additionalHeaders: ["Idempotency-Key": idemKey]
        )
        guard response.isSuccess else {
            if response.errorCode == "PRIVILEGED_EDIT_INVALID" {
                return .failure(
                    message: Self.describePrivilegedEditInvalidError(patch: patch),
                    httpStatus: response.statusCode,
                    errorCode: response.errorCode
                )
            }
            return .failure(
                message: response.errorMessage ?? "Cập nhật trực tiếp hồ sơ thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        if let root = response.jsonObject,
           root["entry"] != nil || root["worker_details"] != nil {
            let updated = Self.parseWorkerEntryDetail(root)
            if !updated.entryId.isEmpty {
                return .success(updated, statusCode: response.statusCode)
            }
        }
        return await fetchWorkerEntryDetail(entryId: cleanId)
    }

    // MARK: - Parser

    public static func parseCccdReviewItem(_ obj: [String: Any], fallbackIndex: Int) -> CccdReviewItem {
        let entryIdUuid = obj.optString("entry_id", "id")
        let entryId = obj.optInt64("entry_id", "id") ?? Int64(fallbackIndex + 1)
        let entryVersion = obj.optInt("entry_version", "version", defaultValue: 1)
        let rawEmp = obj.optString("employee_code", "ma_nld", "worker_code")
        let maNld = WorkerItem.isUuidLike(rawEmp) ? "" : rawEmp
        let hoTen = obj.optString("ho_ten", "entered_display_name", "display_name", "full_name")
        let duAn = obj.optString("du_an", "project_display", "project_name")
        let quanLy = obj.optString("quan_ly", "manager_name")
        let nguoiTuyen = obj.optString("nguoi_tuyen", "recruiter_display", "recruiter_name")
        let reviewStatus = obj.optString("review_status", "reconciliation_status", "status", defaultValue: "PENDING_QR")
        let enteredCccd = obj.optString("entered_cccd", "entered_national_id", "so_cccd")
        let qrObj = obj["qr_extracted_payload"] as? [String: Any]
        let qrCccd = obj.optString("qr_cccd", "qr_national_id", defaultValue: qrObj?.optString("cccd_12", "national_id") ?? "")
        let frontUrl = obj.optNullableString("front_preview_url", "cccd_front_preview_path", "cccd_front_url")
        let backUrl = obj.optNullableString("back_preview_url", "cccd_back_preview_path", "cccd_back_url")

        let compRaw = obj.optObjectArray("comparisons").ifEmpty(obj.optObjectArray("comparison"))
        var comparisons: [CccdComparisonRow] = compRaw.map { c in
            CccdComparisonRow(
                field: c.optString("field", "key", defaultValue: UUID().uuidString),
                label: c.optString("label", "title", defaultValue: "Trường thông tin"),
                enteredValue: c.optString("entered_value", "entered"),
                qrValue: c.optString("qr_value", "qr"),
                status: c.optString("status", defaultValue: "MATCH")
            )
        }

        if comparisons.isEmpty {
            comparisons = [
                CccdComparisonRow(
                    field: "so_cccd",
                    label: "Số CCCD",
                    enteredValue: enteredCccd,
                    qrValue: qrCccd,
                    status: (!enteredCccd.isEmpty && enteredCccd == qrCccd) ? "MATCH" : "DIFFERENT"
                ),
                CccdComparisonRow(
                    field: "ho_ten",
                    label: "Họ và tên",
                    enteredValue: hoTen,
                    qrValue: qrObj?.optString("full_name", "display_name") ?? "",
                    status: "MATCH"
                )
            ]
        }

        return CccdReviewItem(
            entryId: entryId,
            entryIdUuid: entryIdUuid.isEmpty ? String(entryId) : entryIdUuid,
            entryVersion: entryVersion,
            maNld: maNld,
            hoTen: hoTen,
            duAn: duAn,
            quanLy: quanLy,
            nguoiTuyen: nguoiTuyen,
            reviewStatus: reviewStatus,
            enteredCccd: enteredCccd,
            qrCccd: qrCccd,
            frontPreviewUrl: frontUrl,
            backPreviewUrl: backUrl,
            comparisons: comparisons
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
