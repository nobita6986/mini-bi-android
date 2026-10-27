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
            if let matched = catalog.catalogLaborTypes.first(where: {
                (!explicit.isEmpty && $0.laborTypeKey.caseInsensitiveCompare(explicit) == .orderedSame) ||
                (!disp.isEmpty && $0.displayName.caseInsensitiveCompare(disp) == .orderedSame)
            }) {
                return matched.laborTypeKey
            }
            if !explicit.isEmpty && explicit != "SEASONAL" {
                return explicit
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
