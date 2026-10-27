import Foundation

// MARK: - Direct Entry ("Cấp mới") & CCCD Review Service
// Mirrors Android's `data/DirectEntryService.kt` 100%

public enum DirectEntryContract {
    public static let docTypeCccdFront = "CCCD_FRONT"
    public static let docTypeCccdBack = "CCCD_BACK"
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

public final class DirectEntryService: @unchecked Sendable {
    public static let shared = DirectEntryService()

    private let httpClient: BackendHttpClient

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
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "page_size", value: String(pageSize))
        ]
        if !status.isEmpty && status.uppercased() != "ALL" {
            queryItems.append(URLQueryItem(name: "status", value: status))
        }
        if !search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: search.trimmingCharacters(in: .whitespacesAndNewlines)))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/direct-entry/cccd-review",
            queryItems: queryItems
        )
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
        var body: [String: Any] = [
            "action": action,
            "note": note,
            "resolution_note": note
        ]
        if let correctedCccd, !correctedCccd.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            body["corrected_cccd"] = correctedCccd.trimmingCharacters(in: .whitespacesAndNewlines)
            body["national_id"] = correctedCccd.trimmingCharacters(in: .whitespacesAndNewlines)
        }

        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/cccd-review/\(entryId)/resolve",
            jsonBody: body
        )
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
        return .success(Self.parseCccdReviewItem(obj, fallbackIndex: Int(entryId)), statusCode: response.statusCode)
    }

    public func retryCccdQrExtraction(entryId: Int64) async -> MiniBiApiResult<CccdReviewItem> {
        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/cccd-review/\(entryId)/retry-qr",
            jsonBody: [:]
        )
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
        return .success(Self.parseCccdReviewItem(obj, fallbackIndex: Int(entryId)), statusCode: response.statusCode)
    }

    // MARK: - 2. Direct Entry ("Cấp mới") Options, Duplicate Check, Batch Submit & Document Upload

    public func fetchAssigneeOptions() async -> MiniBiApiResult<DirectEntryAssigneeOptions> {
        let response = await httpClient.request(method: "GET", path: "/api/direct-entry/options")
        if response.isSuccess, let root = response.jsonObject {
            let dataObj = (root["data"] as? [String: Any]) ?? root
            return .success(
                DirectEntryAssigneeOptions(
                    projects: dataObj.optStringArray("projects", "du_an"),
                    managers: dataObj.optStringArray("managers", "quan_ly"),
                    recruiters: dataObj.optStringArray("recruiters", "nguoi_tuyen"),
                    banks: dataObj.optStringArray("banks", "ngan_hang")
                ),
                statusCode: response.statusCode
            )
        }
        return .success(DirectEntryAssigneeOptions())
    }

    public func fetchBatchHistory() async -> MiniBiApiResult<[DirectEntryBatchHistoryItem]> {
        let response = await httpClient.request(method: "GET", path: "/api/direct-entry/batches")
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được lịch sử lô cấp mới.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let rawList = dataObj.optObjectArray("batches")
            .ifEmpty(dataObj.optObjectArray("items"))

        let items = rawList.enumerated().map { idx, obj in
            DirectEntryBatchHistoryItem(
                batchId: obj.optInt64("batch_id", "id") ?? Int64(idx + 1),
                status: obj.optString("status", "state", defaultValue: "COMPLETED"),
                totalRows: obj.optInt("total_rows", "entry_count", "total"),
                approvedRows: obj.optInt("approved_rows", "approved_count"),
                pendingRows: obj.optInt("pending_rows", "pending_count"),
                createdAt: obj.optString("created_at", "submitted_at")
            )
        }
        return .success(items, statusCode: response.statusCode)
    }

    public func checkDuplicates(drafts: [NewWorkerEntryDraft]) async -> MiniBiApiResult<[DirectEntryDuplicateCandidate]> {
        let cccds = drafts.map { $0.soCccd.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
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

    public func submitBatch(
        drafts: [NewWorkerEntryDraft],
        confirmDuplicates: Bool = false
    ) async -> MiniBiApiResult<DirectEntryBatchSubmitOutcome> {
        let rowsPayload: [[String: Any]] = drafts.map { d in
            [
                "row_id": d.rowId,
                "ho_ten": d.hoTen.trimmingCharacters(in: .whitespacesAndNewlines),
                "so_cccd": d.soCccd.trimmingCharacters(in: .whitespacesAndNewlines),
                "du_an": d.duAn.trimmingCharacters(in: .whitespacesAndNewlines),
                "trang_thai": d.trangThai.trimmingCharacters(in: .whitespacesAndNewlines),
                "sdt": d.sdt.trimmingCharacters(in: .whitespacesAndNewlines),
                "ngay_sinh": d.ngaySinh.trimmingCharacters(in: .whitespacesAndNewlines),
                "gioi_tinh": d.gioiTinh.trimmingCharacters(in: .whitespacesAndNewlines),
                "ngay_cap": d.ngayCap.trimmingCharacters(in: .whitespacesAndNewlines),
                "ngay_vao_lam": d.ngayVaoLam.trimmingCharacters(in: .whitespacesAndNewlines),
                "quan_ly": d.quanLy.trimmingCharacters(in: .whitespacesAndNewlines),
                "nguoi_tuyen": d.nguoiTuyen.trimmingCharacters(in: .whitespacesAndNewlines),
                "nganh_nghe": d.nganhNghe.trimmingCharacters(in: .whitespacesAndNewlines),
                "que_quan": d.queQuan.trimmingCharacters(in: .whitespacesAndNewlines),
                "ghi_chu": d.ghiChu.trimmingCharacters(in: .whitespacesAndNewlines),
                "so_tai_khoan": d.soTaiKhoan.trimmingCharacters(in: .whitespacesAndNewlines),
                "ngan_hang": d.nganHang.trimmingCharacters(in: .whitespacesAndNewlines)
            ]
        }

        let body: [String: Any] = [
            "rows": rowsPayload,
            "entries": rowsPayload,
            "confirm_duplicates": confirmDuplicates
        ]

        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/batches",
            jsonBody: body
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Lưu lô cấp mới thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let batchId = dataObj.optInt64("batch_id", "id") ?? 0
        let createdEntries = dataObj.optObjectArray("entries").ifEmpty(dataObj.optObjectArray("rows"))

        var entryIdByRowId: [String: Int64] = [:]
        for (idx, entryObj) in createdEntries.enumerated() {
            let entryId = entryObj.optInt64("entry_id", "id") ?? Int64(idx + 1)
            let rowId = entryObj.optString(
                "row_id",
                defaultValue: idx < drafts.count ? drafts[idx].rowId : ""
            )
            if !rowId.isEmpty {
                entryIdByRowId[rowId] = entryId
            }
        }

        let outcome = DirectEntryBatchSubmitOutcome(
            batchId: batchId,
            createdCount: max(drafts.count, createdEntries.count),
            entryIdByRowId: entryIdByRowId
        )
        return .success(outcome, statusCode: response.statusCode)
    }

    public func uploadEntryDocumentAttachments(
        entryId: Int64,
        docType: String,
        attachments: [LocalAttachmentItem]
    ) async -> MiniBiApiResult<String> {
        guard !attachments.isEmpty else {
            return .failure(message: "Không có tệp đính kèm để tải lên.")
        }

        let prepared: PreparedContractUploadPayload
        do {
            prepared = try PdfContractMerger.prepareContractUploadPayload(attachments: attachments)
        } catch {
            return .failure(message: error.localizedDescription)
        }

        let response = await httpClient.uploadMultipart(
            path: "/api/direct-entry/entries/\(entryId)/documents",
            fieldName: "file",
            fileName: prepared.fileName,
            mimeType: prepared.mimeType,
            fileData: prepared.data,
            extraFields: ["doc_type": docType, "document_type": docType]
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Tải tệp hồ sơ cấp mới thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let fileUrl = response.jsonObject?.optString("file_url", "url", defaultValue: prepared.fileName) ?? prepared.fileName
        return .success(fileUrl, statusCode: response.statusCode)
    }

    // MARK: - Parser

    public static func parseCccdReviewItem(_ obj: [String: Any], fallbackIndex: Int) -> CccdReviewItem {
        let entryId = obj.optInt64("entry_id", "id") ?? Int64(fallbackIndex + 1)
        let maNld = obj.optString("ma_nld", "employee_code", "worker_code")
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

private extension Array {
    func ifEmpty(_ fallback: @autoclosure () -> [Element]) -> [Element] {
        isEmpty ? fallback() : self
    }
}
