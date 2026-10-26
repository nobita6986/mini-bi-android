import Foundation
import UIKit

public enum DirectEntryContract {
    public static let contractVersion = "worker-profile/1.2"
    public static let directEntryGenderOptions = ["Nam", "Nữ", "Khác"]
    public static let allowedCccdStandardizeFields: Set<String> = [
        "national_id",
        "display_name",
        "date_of_birth",
        "gender",
        "address",
        "national_id_issued_at"
    ]

    private static var hcmTimeZone: TimeZone {
        TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
    }

    public static func todayIsoInHcm(now: Date = Date()) -> String {
        let fmt = DateFormatter()
        fmt.calendar = Calendar(identifier: .gregorian)
        fmt.timeZone = hcmTimeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.string(from: now)
    }

    public static func todayUiInHcm(now: Date = Date()) -> String {
        let fmt = DateFormatter()
        fmt.calendar = Calendar(identifier: .gregorian)
        fmt.timeZone = hcmTimeZone
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "dd/MM/yyyy"
        return fmt.string(from: now)
    }

    public static func isValidCccd12(_ raw: String?) -> Bool {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              raw.count == 12,
              raw.allSatisfy({ $0.isNumber }),
              raw != "000000000000"
        else {
            return false
        }
        return true
    }

    public static func isCanonicalNationalId(_ raw: String?) -> Bool {
        isValidCccd12(raw)
    }

    public static func isUuid(_ raw: String?) -> Bool {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return false
        }
        return UUID(uuidString: raw) != nil
    }

    public static func uiDateToIsoDate(_ raw: String) throws -> String {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else {
            throw BackendApiException(
                kind: .validationError,
                statusCode: 422,
                backendCode: "INVALID_DATE",
                message: "Ngày tháng không được để trống."
            )
        }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = hcmTimeZone

        let partsSlash = clean.split(separator: "/").map(String.init)
        if partsSlash.count == 3,
           let d = Int(partsSlash[0]),
           let m = Int(partsSlash[1]),
           let y = Int(partsSlash[2]),
           (1900...2100).contains(y) {
            var comps = DateComponents()
            comps.year = y
            comps.month = m
            comps.day = d
            if let date = cal.date(from: comps),
               cal.component(.year, from: date) == y,
               cal.component(.month, from: date) == m,
               cal.component(.day, from: date) == d {
                return String(format: "%04d-%02d-%02d", y, m, d)
            }
        }

        let partsDash = clean.split(separator: "-").map(String.init)
        if partsDash.count == 3,
           let y = Int(partsDash[0]),
           let m = Int(partsDash[1]),
           let d = Int(partsDash[2]),
           (1900...2100).contains(y) {
            var comps = DateComponents()
            comps.year = y
            comps.month = m
            comps.day = d
            if let date = cal.date(from: comps),
               cal.component(.year, from: date) == y,
               cal.component(.month, from: date) == m,
               cal.component(.day, from: date) == d {
                return String(format: "%04d-%02d-%02d", y, m, d)
            }
        }

        throw BackendApiException(
            kind: .validationError,
            statusCode: 422,
            backendCode: "INVALID_DATE",
            message: "Ngày '\(clean)' không đúng định dạng thực tế DD/MM/YYYY."
        )
    }

    public static func isoDateToUiDate(_ raw: String?) -> String {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return ""
        }
        let datePart = String(raw.prefix(10))
        let parts = datePart.split(separator: "-").map(String.init)
        if parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2 {
            return "\(parts[2])/\(parts[1])/\(parts[0])"
        }
        return raw
    }

    public static func isValidUiOrIsoDate(_ raw: String) -> Bool {
        (try? uiDateToIsoDate(raw)) != nil
    }

    public static func isValidFreeDateText(_ raw: String) -> Bool {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return true }
        return clean.count <= 10 && !clean.contains(where: { $0.isNewline })
    }

    public static func formatIsoTimestampHcmUi(isoOrNull: String?, fallbackIso: String = "") -> String {
        let target = (isoOrNull?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? fallbackIso.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else { return "" }

        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var parsed = isoFormatter.date(from: target)
        if parsed == nil {
            let fallbackFmt = ISO8601DateFormatter()
            fallbackFmt.formatOptions = [.withInternetDateTime]
            parsed = fallbackFmt.date(from: target)
        }
        if let date = parsed {
            let outFmt = DateFormatter()
            outFmt.calendar = Calendar(identifier: .gregorian)
            outFmt.timeZone = hcmTimeZone
            outFmt.locale = Locale(identifier: "vi_VN")
            outFmt.dateFormat = "dd/MM/yyyy HH:mm"
            return outFmt.string(from: date)
        }
        return isoDateToUiDate(target)
    }

    public static func genderUiToCode(_ ui: String) -> String? {
        switch ui.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "nam", "male": return "male"
        case "nữ", "nu", "female": return "female"
        case "khác", "khac", "other": return "other"
        default: return nil
        }
    }

    public static func genderUiLabel(_ codeOrUi: String?) -> String {
        guard let raw = codeOrUi?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return "—"
        }
        switch raw.lowercased() {
        case "male", "nam": return "Nam"
        case "female", "nữ", "nu": return "Nữ"
        case "other", "khác", "khac": return "Khác"
        default: return raw
        }
    }

    public static func workStatusUiToCode(_ ui: String) -> String {
        switch ui.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "đang chờ việc", "unconfirmed": return "UNCONFIRMED"
        case "đã nghỉ", "off": return "OFF"
        default: return "ON"
        }
    }

    public static func workStatusUiLabel(_ code: String) -> String {
        switch code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ON": return "Đang làm"
        case "UNCONFIRMED": return "Đang chờ việc"
        case "OFF": return "Đã nghỉ"
        default: return code
        }
    }

    public static func allowedWorkStatusTargets(_ baselineStatus: String) -> [String] {
        switch baselineStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "UNCONFIRMED": return ["ON", "OFF"]
        case "ON": return ["OFF"]
        default: return []
        }
    }

    public static func selectableDirectEditStatuses(_ baselineStatus: String) -> [String] {
        switch baselineStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "OFF": return ["OFF"]
        default: return ["ON", "UNCONFIRMED", "OFF"]
        }
    }

    public static func defaultSeasonalLaborType(_ laborTypes: [LaborTypeCatalogOption]) -> LaborTypeCatalogOption? {
        if let seasonal = laborTypes.first(where: {
            $0.laborTypeKey.lowercased().contains("thoi_vu") ||
                $0.laborTypeKey.lowercased().contains("seasonal") ||
                $0.displayName.lowercased().contains("thời vụ")
        }) {
            return seasonal
        }
        return laborTypes.sorted(by: { $0.sortOrder < $1.sortOrder }).first
    }

    public static func createDefaultWorkerDraft(catalog: DirectEntryCatalog? = nil) -> NewWorkerEntryDraft {
        let defaultLabor = catalog.flatMap { defaultSeasonalLaborType($0.laborTypes) }
        return NewWorkerEntryDraft(
            laborTypeKey: defaultLabor?.laborTypeKey ?? "",
            laborTypeDisplay: defaultLabor?.displayName ?? "",
            firstWorkDateUi: todayUiInHcm(),
            employmentStatusUi: "Đang làm"
        )
    }

    public static func buildEntryPayload(from draft: NewWorkerEntryDraft, banks: [BankCatalogOption] = []) throws -> [String: Any] {
        let firstWorkIso = try uiDateToIsoDate(draft.firstWorkDateUi)
        let cccd = draft.cccd.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidCccd12(cccd) else {
            throw BackendApiException(
                kind: .validationError,
                statusCode: 422,
                backendCode: "INVALID_NATIONAL_ID",
                message: "Số CCCD bắt buộc gồm đúng 12 chữ số hợp lệ."
            )
        }
        let statusCode = workStatusUiToCode(draft.employmentStatusUi)
        var workerDetails: [String: Any] = [
            "national_id": cccd,
            "display_name": draft.fullName.trimmingCharacters(in: .whitespacesAndNewlines),
            "date_of_birth": optionalFreeDateField(draft.birthDateUi) ?? NSNull(),
            "national_id_issued_at": optionalFreeDateField(draft.cccdIssueDateUi) ?? NSNull(),
            "gender": genderUiToCode(draft.genderUi) ?? NSNull(),
            "phone": optionalTextField(draft.phone, maxLength: 64) ?? NSNull(),
            "address": optionalTextField(draft.address, maxLength: 1024) ?? NSNull()
        ]
        if let portraitStage = draft.portraitStage, !portraitStage.stageId.isEmpty {
            workerDetails["portrait_stage_id"] = portraitStage.stageId
        }

        let accNum = draft.bankAccountNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let accHolder = draft.bankAccountHolder.trimmingCharacters(in: .whitespacesAndNewlines)
        let bankRaw = draft.bankName.trimmingCharacters(in: .whitespacesAndNewlines)
        let paymentInfo: [String: Any]
        if !accNum.isEmpty && !accHolder.isEmpty && !bankRaw.isEmpty {
            let matchedBankId = banks.first(where: {
                $0.bankId.caseInsensitiveCompare(bankRaw) == .orderedSame ||
                    $0.displayName.caseInsensitiveCompare(bankRaw) == .orderedSame
            })?.bankId ?? bankRaw
            paymentInfo = [
                "state": "provided",
                "account_number": accNum,
                "account_holder_name": accHolder,
                "bank_id": matchedBankId
            ]
        } else {
            paymentInfo = ["state": "pending"]
        }

        var entry: [String: Any] = [
            "contract_version": contractVersion,
            "project_id": draft.projectId.trimmingCharacters(in: .whitespacesAndNewlines),
            "recruiter_id": draft.recruiterId.trimmingCharacters(in: .whitespacesAndNewlines),
            "labor_type": draft.laborTypeKey.trimmingCharacters(in: .whitespacesAndNewlines),
            "first_work_date": firstWorkIso,
            "employment_status": [
                "status": statusCode,
                "effective_date": firstWorkIso
            ],
            "worker_details": workerDetails,
            "payment_info": paymentInfo
        ]
        if let note = optionalTextField(draft.note, maxLength: 4000) {
            entry["general_note"] = note
        }
        return entry
    }

    public static func optionalFreeDateField(_ raw: String) -> String? {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return nil }
        return String(clean.prefix(10))
    }

    public static func optionalTextField(_ raw: String, maxLength: Int) -> String? {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return nil }
        return String(clean.prefix(maxLength))
    }

    public static func buildPaymentChangeProposal(
        state: String,
        accountNumber: String,
        bankId: String,
        accountHolderName: String
    ) throws -> [String: Any] {
        let digits = accountNumber.filter { $0.isNumber }
        guard !digits.isEmpty else {
            throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "INVALID_ACCOUNT", message: "Số tài khoản ngân hàng không được để trống.")
        }
        let cleanBank = bankId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanBank.isEmpty else {
            throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "INVALID_BANK", message: "Vui lòng chọn ngân hàng.")
        }
        let cleanHolder = accountHolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanHolder.isEmpty else {
            throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "INVALID_HOLDER", message: "Tên chủ tài khoản không được để trống.")
        }
        return [
            "state": state,
            "account_number": digits,
            "bank_id": cleanBank,
            "account_holder_name": cleanHolder
        ]
    }

    public static func buildWorkStatusChangeProposal(
        status: String,
        effectiveDateUiOrIso: String,
        leaveReason: String,
        baselineStatus: String
    ) throws -> [String: Any] {
        let target = status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let allowed = allowedWorkStatusTargets(baselineStatus)
        guard allowed.contains(target) else {
            throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "INVALID_STATUS_TRANSITION", message: "Không thể chuyển sang trạng thái đã chọn.")
        }
        let isoDate = try uiDateToIsoDate(effectiveDateUiOrIso)
        var obj: [String: Any] = [
            "status": target,
            "effective_date": isoDate
        ]
        if target == "OFF" {
            let reason = leaveReason.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !reason.isEmpty else {
                throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "LEAVE_REASON_REQUIRED", message: "Vui lòng nhập lý do nghỉ việc.")
            }
            obj["leave_reason"] = reason
        }
        return obj
    }

    public static func buildPrivilegedEditEmploymentStatus(
        status: String,
        effectiveDateUiOrIso: String,
        leaveReason: String,
        baselineStatus: String
    ) throws -> [String: Any] {
        let target = status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let selectable = selectableDirectEditStatuses(baselineStatus)
        guard selectable.contains(target) else {
            throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "INVALID_STATUS_TRANSITION", message: "Hồ sơ đã nghỉ việc không thể chuyển lại Đang làm.")
        }
        let isoDate = try uiDateToIsoDate(effectiveDateUiOrIso)
        var obj: [String: Any] = [
            "status": target,
            "effective_date": isoDate
        ]
        if target == "OFF" {
            let reason = leaveReason.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !reason.isEmpty else {
                throw BackendApiException(kind: .validationError, statusCode: 422, backendCode: "LEAVE_REASON_REQUIRED", message: "Vui lòng nhập lý do nghỉ việc.")
            }
            obj["leave_reason"] = reason
        }
        return obj
    }
}

// MARK: - DirectEntryService

public final class DirectEntryService: @unchecked Sendable {
    private let httpClient: BackendHttpClient
    private let attachmentsDir: URL

    public init(httpClient: BackendHttpClient, attachmentsDir: URL? = nil) {
        self.httpClient = httpClient
        if let dir = attachmentsDir {
            self.attachmentsDir = dir
        } else {
            let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory())
            self.attachmentsDir = base.appendingPathComponent("worker_attachments", isDirectory: true)
        }
        try? FileManager.default.createDirectory(at: self.attachmentsDir, withIntermediateDirectories: true)
    }

    // MARK: - Catalog

    public func fetchCatalog(effectiveDateIso: String = DirectEntryContract.todayIsoInHcm()) async throws -> DirectEntryCatalog {
        let json = try await httpClient.requestJson(
            method: "GET",
            path: "/api/direct-entry/catalog",
            queryParams: [("effective_date", effectiveDateIso)]
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        let eff = dataObj.stringValue(forKeys: ["effective_date"], default: effectiveDateIso)

        let projects = ((dataObj["projects"] as? [[String: Any]]) ?? []).compactMap { item -> ProjectCatalogOption? in
            let pid = item.stringValue(forKeys: ["project_id", "id"])
            let name = item.stringValue(forKeys: ["display_name", "name"], default: pid)
            guard !pid.isEmpty else { return nil }
            return ProjectCatalogOption(projectId: pid, displayName: name)
        }

        var recruiters: [RecruiterCatalogOption] = []
        if let recArr = dataObj["recruiters"] as? [[String: Any]] {
            for item in recArr {
                let rid = item.stringValue(forKeys: ["recruiter_id", "id"])
                guard !rid.isEmpty else { continue }
                let name = item.stringValue(forKeys: ["display_name", "name"], default: rid)
                let pType = item.stringValue(forKeys: ["provider_type", "source_type"], default: "hrp").lowercased()
                let code = item.stringValue(forKeys: ["personnel_code", "employee_code"])
                let team = item.optCleanString("team_display_name") ?? item.optCleanString("team_name")
                recruiters.append(
                    RecruiterCatalogOption(
                        recruiterId: rid,
                        displayName: name,
                        providerType: pType == "vendor" ? "vendor" : "hrp",
                        personnelCode: code,
                        teamDisplayName: team
                    )
                )
            }
        }

        let banks = ((dataObj["banks"] as? [[String: Any]]) ?? []).compactMap { item -> BankCatalogOption? in
            let bid = item.stringValue(forKeys: ["bank_id", "id", "code"])
            let name = item.stringValue(forKeys: ["display_name", "short_name", "name"], default: bid)
            guard !bid.isEmpty else { return nil }
            return BankCatalogOption(bankId: bid, displayName: name)
        }

        let laborTypes = ((dataObj["labor_types"] as? [[String: Any]]) ?? []).enumerated().compactMap { idx, item -> LaborTypeCatalogOption? in
            let key = item.stringValue(forKeys: ["labor_type_key", "key", "code"])
            let name = item.stringValue(forKeys: ["display_name", "label", "name"], default: key)
            guard !key.isEmpty else { return nil }
            let sort = item.intValue(forKeys: ["sort_order"], default: idx)
            return LaborTypeCatalogOption(laborTypeKey: key, displayName: name, sortOrder: sort)
        }

        return DirectEntryCatalog(
            effectiveDate: eff,
            projects: projects,
            recruiters: recruiters,
            banks: banks,
            laborTypes: laborTypes
        )
    }

    // MARK: - Local Attachment & Multi-Image PDF Preparation

    public func prepareLocalImageAttachment(
        documentType: String,
        image: UIImage,
        fileName: String? = nil
    ) throws -> StagedDocumentRef {
        let normalized = PdfContractMerger.normalizeOrientationAndScale(image: image, maxDimension: 1600.0)
        guard let jpegData = normalized.jpegData(compressionQuality: 0.82),
              jpegData.count <= PdfContractMerger.maxUploadBytes
        else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 413,
                backendCode: "FILE_TOO_LARGE",
                message: "Ảnh đính kèm vượt quá dung lượng tối đa 10 MB."
            )
        }
        let ts = Int64(Date().timeIntervalSince1970 * 1000.0)
        let resolvedName = (fileName?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
            ?? "\(documentType.lowercased())_\(ts).jpg"
        let localUrl = attachmentsDir.appendingPathComponent("\(UUID().uuidString)_\(resolvedName)")
        try jpegData.write(to: localUrl, options: .atomic)

        return StagedDocumentRef(
            stageId: "",
            documentType: documentType,
            fileName: resolvedName,
            mimeType: "image/jpeg",
            sizeBytes: Int64(jpegData.count),
            sha256Hex: PdfContractMerger.sha256Hex(jpegData),
            localFilePath: localUrl.path,
            pageCount: 1,
            sourceImagePaths: [localUrl.path]
        )
    }

    public func prepareLocalMultiImagePdfAttachment(
        documentType: String = "EMPLOYMENT_CONTRACT",
        images: [UIImage],
        existingSourcePaths: [String] = []
    ) throws -> StagedDocumentRef {
        var allImages: [UIImage] = []
        var savedPagePaths: [String] = []

        for path in existingSourcePaths {
            if let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
               let img = UIImage(data: data) {
                allImages.append(img)
                savedPagePaths.append(path)
            }
        }

        for img in images {
            let norm = PdfContractMerger.normalizeOrientationAndScale(image: img, maxDimension: 1600.0)
            if let jpeg = norm.jpegData(compressionQuality: 0.84) {
                let pageUrl = attachmentsDir.appendingPathComponent("page_\(UUID().uuidString).jpg")
                try jpeg.write(to: pageUrl, options: .atomic)
                savedPagePaths.append(pageUrl.path)
                allImages.append(norm)
            }
        }

        let payload: PreparedContractUploadPayload
        if allImages.count == 1 {
            payload = try PdfContractMerger.prepareContractUpload(images: allImages)
        } else {
            payload = try PdfContractMerger.mergeImagesToPdf(images: allImages)
        }

        let destUrl = attachmentsDir.appendingPathComponent("\(UUID().uuidString)_\(payload.fileName)")
        try payload.data.write(to: destUrl, options: .atomic)

        return StagedDocumentRef(
            stageId: "",
            documentType: documentType,
            fileName: payload.fileName,
            mimeType: payload.mimeType,
            sizeBytes: payload.sizeBytes,
            sha256Hex: payload.sha256Hex,
            localFilePath: destUrl.path,
            pageCount: payload.pageCount,
            sourceImagePaths: savedPagePaths
        )
    }

    public func prepareLocalPdfDataAttachment(
        documentType: String = "EMPLOYMENT_CONTRACT",
        pdfData: Data,
        fileName: String
    ) throws -> StagedDocumentRef {
        let payload = try PdfContractMerger.prepareContractUpload(
            singlePdfData: pdfData,
            singlePdfFileName: fileName
        )
        let destUrl = attachmentsDir.appendingPathComponent("\(UUID().uuidString)_\(payload.fileName)")
        try payload.data.write(to: destUrl, options: .atomic)
        return StagedDocumentRef(
            stageId: "",
            documentType: documentType,
            fileName: payload.fileName,
            mimeType: payload.mimeType,
            sizeBytes: payload.sizeBytes,
            sha256Hex: payload.sha256Hex,
            localFilePath: destUrl.path,
            pageCount: payload.pageCount,
            sourceImagePaths: []
        )
    }

    public func deleteLocalAttachment(path: String?) {
        guard let path = path, !path.isEmpty else { return }
        try? FileManager.default.removeItem(atPath: path)
    }

    public func deleteLocalAttachmentRef(_ ref: StagedDocumentRef?) {
        guard let ref = ref else { return }
        deleteLocalAttachment(path: ref.localFilePath)
        for p in ref.sourceImagePaths {
            deleteLocalAttachment(path: p)
        }
    }

    // MARK: - Upload Contract File (`POST /api/workers/{ma_nld}/contract-file`)

    @discardableResult
    public func uploadWorkerContractFile(
        employeeCode: String,
        payload: PreparedContractUploadPayload
    ) async throws -> [String: Any] {
        let cleanCode = employeeCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanCode.isEmpty else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 400,
                backendCode: "EMPTY_EMPLOYEE_CODE",
                message: "Mã người lao động không hợp lệ."
            )
        }
        let encodedCode = cleanCode.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanCode
        return try await httpClient.postMultipartFile(
            path: "/api/workers/\(encodedCode)/contract-file",
            fieldName: "file",
            fileName: payload.fileName,
            mimeType: payload.mimeType,
            fileBytes: payload.data
        )
    }

    // MARK: - Stage Upload & Entry Document Upload

    public func deleteStage(stageId: String) async throws {
        guard !stageId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        _ = try await httpClient.requestJson(
            method: "DELETE",
            path: "/api/direct-entry/worker-portrait-stages/\(stageId)"
        )
    }

    @discardableResult
    public func uploadDocumentToEntry(
        entryId: String,
        initialEntryVersion: Int? = 1,
        documentType: String,
        fileBytes: Data,
        mimeType: String,
        idempotencyBaseKey: String = UUID().uuidString
    ) async throws -> Int {
        let sha256Hex = PdfContractMerger.sha256Hex(fileBytes)
        let ext: String
        switch mimeType.lowercased() {
        case "application/pdf": ext = "pdf"
        case "image/png": ext = "png"
        case "image/webp": ext = "webp"
        default: ext = "jpg"
        }
        let fileName = "\(documentType.lowercased()).\(ext)"

        var versionToUse = initialEntryVersion ?? 1
        var initResp: [String: Any]
        do {
            initResp = try await httpClient.requestJson(
                method: "POST",
                path: "/api/direct-entry/entries/\(entryId)/documents",
                jsonBody: [
                    "document_type": documentType,
                    "file_name": fileName,
                    "mime_type": mimeType,
                    "size_bytes": fileBytes.count,
                    "sha256_hex": sha256Hex,
                    "expected_entry_version": versionToUse
                ],
                headers: ["Idempotency-Key": "\(idempotencyBaseKey)-init"]
            )
        } catch let err as BackendApiException where err.kind == .conflict {
            let detail = try await getEntryDetail(entryId: entryId)
            versionToUse = detail.version
            initResp = try await httpClient.requestJson(
                method: "POST",
                path: "/api/direct-entry/entries/\(entryId)/documents",
                jsonBody: [
                    "document_type": documentType,
                    "file_name": fileName,
                    "mime_type": mimeType,
                    "size_bytes": fileBytes.count,
                    "sha256_hex": sha256Hex,
                    "expected_entry_version": versionToUse
                ],
                headers: ["Idempotency-Key": "\(idempotencyBaseKey)-init-r1"]
            )
        }

        let dataObj = (initResp["data"] as? [String: Any]) ?? initResp
        let docId = dataObj.stringValue(forKeys: ["document_id", "id"])
        let uploadUrl = dataObj.stringValue(forKeys: ["upload_url", "signed_url"])

        var requiredHeaders: [String: String] = [:]
        if let hdrObj = dataObj["required_headers"] as? [String: Any] {
            for (k, v) in hdrObj {
                if let vs = v as? String { requiredHeaders[k] = vs }
            }
        }

        if !uploadUrl.isEmpty {
            try await httpClient.putBinaryToSignedUrl(
                uploadUrl: uploadUrl,
                mimeType: mimeType,
                bytes: fileBytes,
                requiredHeaders: requiredHeaders
            )
        }

        if !docId.isEmpty {
            let finResp = try await httpClient.requestJson(
                method: "POST",
                path: "/api/direct-entry/entries/\(entryId)/documents/\(docId)/finalize",
                jsonBody: ["sha256_hex": sha256Hex],
                headers: ["Idempotency-Key": "\(idempotencyBaseKey)-fin"]
            )
            let finData = (finResp["data"] as? [String: Any]) ?? finResp
            return finData.intValue(forKeys: ["entry_version", "version"], default: versionToUse + 1)
        }
        return versionToUse + 1
    }

    // MARK: - Submissions & Duplicate CCCD Preflight

    public func submitFullProfileBatch(
        validDrafts: [NewWorkerEntryDraft],
        banks: [BankCatalogOption] = []
    ) async throws -> SubmissionBatchResult {
        guard !validDrafts.isEmpty else {
            throw BackendApiException(
                kind: .validationError,
                statusCode: 422,
                backendCode: "EMPTY_BATCH",
                message: "Chưa có dòng hồ sơ hợp lệ để lưu."
            )
        }
        let entries = try validDrafts.map { try DirectEntryContract.buildEntryPayload(from: $0, banks: banks) }
        let json = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/submissions",
            jsonBody: [
                "contract_version": DirectEntryContract.contractVersion,
                "entries": entries
            ],
            headers: ["Idempotency-Key": UUID().uuidString]
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        let submissionObj = (dataObj["submission"] as? [String: Any]) ?? dataObj
        let subId = submissionObj.stringValue(forKeys: ["submission_id", "id"])
        let state = submissionObj.stringValue(forKeys: ["state", "status"], default: "DRAFT").uppercased()
        let ver = submissionObj.intValue(forKeys: ["version"], default: 1)

        var entryIds: [String] = []
        var employeeCodes: [String] = []
        if let entryList = (dataObj["entries"] ?? submissionObj["entries"]) as? [[String: Any]] {
            for e in entryList {
                let eid = e.stringValue(forKeys: ["entry_id", "id"])
                if !eid.isEmpty { entryIds.append(eid) }
                let code = e.stringValue(forKeys: ["employee_code", "worker_code"])
                if !code.isEmpty { employeeCodes.append(code) }
            }
        }
        return SubmissionBatchResult(
            submissionId: subId,
            state: state,
            version: ver,
            entryIds: entryIds,
            employeeCodes: employeeCodes
        )
    }

    public func preflightDuplicateCccd(submissionId: String) async throws -> DuplicateCccdPreflightResult {
        let json = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/submissions/\(submissionId)/duplicate-cccd-preflight"
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        let version = dataObj.intValue(forKeys: ["submission_version", "version"], default: 1)
        let fingerprint = dataObj.stringValue(forKeys: ["duplicate_cccd_fingerprint", "fingerprint"])
        let conflictsRaw = (dataObj["conflicts"] as? [[String: Any]]) ?? []
        let conflicts = conflictsRaw.map { c in
            DuplicateCccdConflictItem(
                nationalIdMasked: c.stringValue(forKeys: ["national_id_masked", "cccd_masked", "national_id"], default: "***"),
                existingEmployeeCode: c.stringValue(forKeys: ["existing_employee_code", "employee_code"]),
                existingDisplayName: c.stringValue(forKeys: ["existing_display_name", "display_name"]),
                existingProjectDisplay: c.stringValue(forKeys: ["existing_project_display", "project_display"]),
                existingFirstWorkDate: c.stringValue(forKeys: ["existing_first_work_date", "first_work_date"])
            )
        }
        let conflictCount = dataObj.intValue(forKeys: ["conflict_count", "duplicate_cccd_count"], default: conflicts.count)
        return DuplicateCccdPreflightResult(
            submissionId: submissionId,
            version: version,
            fingerprint: fingerprint,
            conflictCount: conflictCount,
            conflicts: conflicts
        )
    }

    public func transitionSubmissionToSubmitted(
        submissionId: String,
        expectedVersion: Int,
        duplicateCccdFingerprint: String? = nil,
        duplicateCccdCount: Int? = nil
    ) async throws -> (state: String, version: Int) {
        var body: [String: Any] = [
            "target_state": "SUBMITTED",
            "expected_version": expectedVersion
        ]
        if let fp = duplicateCccdFingerprint, !fp.isEmpty {
            body["duplicate_cccd_confirmation"] = [
                "confirmed": true,
                "fingerprint": fp,
                "conflict_count": duplicateCccdCount ?? 0
            ]
        }
        let json = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/submissions/\(submissionId)/transition",
            jsonBody: body
        )
        let dataObj = (json["data"] as? [String: Any]) ?? (json["submission"] as? [String: Any]) ?? json
        let state = dataObj.stringValue(forKeys: ["state", "status"], default: "SUBMITTED").uppercased()
        let ver = dataObj.intValue(forKeys: ["version"], default: expectedVersion + 1)
        return (state, ver)
    }

    public func listOwnSubmissions(pageSize: Int = 20, cursor: String? = nil) async throws -> (items: [OwnSubmissionItem], hasMore: Bool, nextCursor: String?) {
        let json = try await httpClient.requestJson(
            method: "GET",
            path: "/api/direct-entry/submissions",
            queryParams: [
                ("limit", String(pageSize)),
                ("cursor", cursor)
            ]
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        let rawList = (dataObj["items"] as? [[String: Any]])
            ?? (dataObj["submissions"] as? [[String: Any]])
            ?? []
        let items = rawList.compactMap { row -> OwnSubmissionItem? in
            let sid = row.stringValue(forKeys: ["submission_id", "id"])
            guard !sid.isEmpty else { return nil }
            let transitions = (row["allowed_transitions"] as? [String]) ?? []
            return OwnSubmissionItem(
                submissionId: sid,
                state: row.stringValue(forKeys: ["state", "status"], default: "DRAFT").uppercased(),
                version: row.intValue(forKeys: ["version"], default: 1),
                entryCount: row.intValue(forKeys: ["entry_count", "entries_count"], default: 0),
                createdAt: row.stringValue(forKeys: ["created_at"]),
                updatedAt: row.stringValue(forKeys: ["updated_at"]),
                submittedAt: row.optCleanString("submitted_at"),
                allowedTransitions: transitions
            )
        }
        let hasMore = dataObj.boolValue(forKeys: ["has_more"], default: false)
        let nextCursor = dataObj.optCleanString("next_cursor")
        return (items, hasMore, nextCursor)
    }

    // MARK: - Workers List & Operations

    public func listWorkers(
        scope: String,
        pageSize: Int = 25,
        cursor: String? = nil,
        search: String = "",
        projectId: String? = nil,
        recruiterId: String? = nil,
        employmentStatus: String? = nil
    ) async throws -> (items: [WorkerProfileEntity], hasMore: Bool, nextCursor: String?, summary: WorkerListSummary?) {
        let json = try await httpClient.requestJson(
            method: "GET",
            path: "/api/direct-entry/workers",
            queryParams: [
                ("scope", scope),
                ("limit", String(pageSize)),
                ("cursor", cursor),
                ("search", search.trimmingCharacters(in: .whitespacesAndNewlines)),
                ("project_id", projectId),
                ("recruiter_id", recruiterId),
                ("employment_status", employmentStatus)
            ]
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        let rawItems = (dataObj["items"] as? [[String: Any]])
            ?? (dataObj["workers"] as? [[String: Any]])
            ?? []
        let items = rawItems.compactMap { row -> WorkerProfileEntity? in
            let entryId = row.stringValue(forKeys: ["entry_id", "id"])
            guard !entryId.isEmpty else { return nil }
            let actionsObj = (row["allowed_actions"] as? [String: Any]) ?? [:]
            let actions = WorkerAllowedActions(
                view: actionsObj.boolValue(forKeys: ["view"], default: true),
                proposeChange: actionsObj.boolValue(forKeys: ["propose_change"], default: false),
                directEdit: actionsObj.boolValue(forKeys: ["direct_edit", "privileged_edit"], default: false)
            )
            let firstDateRaw = row.stringValue(forKeys: ["first_work_date"])
            return WorkerProfileEntity(
                cacheKey: "\(scope):\(entryId)",
                scopeKey: scope,
                entryId: entryId,
                entryVersion: row.intValue(forKeys: ["entry_version", "version"], default: 1),
                employeeCode: row.stringValue(forKeys: ["employee_code", "worker_code"]),
                displayName: row.stringValue(forKeys: ["display_name", "full_name"]),
                projectId: row.stringValue(forKeys: ["project_id"]),
                projectDisplay: row.stringValue(forKeys: ["project_display", "project_name"]),
                recruiterId: row.stringValue(forKeys: ["recruiter_id"]),
                recruiterDisplay: row.stringValue(forKeys: ["recruiter_display", "recruiter_name"]),
                laborType: row.stringValue(forKeys: ["labor_type"]),
                laborTypeDisplay: row.stringValue(forKeys: ["labor_type_display"]),
                firstWorkDate: DirectEntryContract.isoDateToUiDate(firstDateRaw),
                employmentStatus: row.stringValue(forKeys: ["employment_status", "work_status"], default: "ON").uppercased(),
                paymentSummary: row.stringValue(forKeys: ["payment_summary"]),
                pendingRequestSummary: row.stringValue(forKeys: ["pending_request_summary"]),
                lastDecisionSummary: row.stringValue(forKeys: ["last_decision_summary"]),
                allowedActions: actions
            )
        }

        var summary: WorkerListSummary? = nil
        if let sumObj = dataObj["summary"] as? [String: Any] {
            summary = WorkerListSummary(
                totalWorkers: sumObj.intValue(forKeys: ["total_workers", "total_count"], default: items.count),
                projectCount: sumObj.intValue(forKeys: ["project_count"], default: 0),
                recruiterCount: sumObj.intValue(forKeys: ["recruiter_count"], default: 0)
            )
        }
        let hasMore = dataObj.boolValue(forKeys: ["has_more"], default: false)
        let nextCursor = dataObj.optCleanString("next_cursor")
        return (items, hasMore, nextCursor, summary)
    }

    public func getEntryDetail(entryId: String) async throws -> WorkerEntryDetail {
        let json = try await httpClient.requestJson(
            method: "GET",
            path: "/api/direct-entry/entries/\(entryId)"
        )
        let dataObj = (json["data"] as? [String: Any]) ?? (json["entry"] as? [String: Any]) ?? json
        return Self.parseEntryDetail(dataObj, fallbackEntryId: entryId)
    }

    internal static func parseEntryDetail(_ obj: [String: Any], fallbackEntryId: String = "") -> WorkerEntryDetail {
        let detailsObj = (obj["worker_details"] as? [String: Any]) ?? obj
        let statusObj = (obj["employment_status"] as? [String: Any]) ?? [:]
        let payObj = (obj["payment_info"] as? [String: Any]) ?? obj
        let docsRaw = (obj["documents"] as? [[String: Any]]) ?? []

        let docs = docsRaw.map { d in
            EntryDocumentDescriptor(
                documentType: d.stringValue(forKeys: ["document_type"]),
                uploadStatus: d.stringValue(forKeys: ["upload_status", "status"], default: "READY").uppercased(),
                fileName: d.stringValue(forKeys: ["file_name"], default: "document"),
                mimeType: d.stringValue(forKeys: ["mime_type"], default: "application/octet-stream"),
                sizeBytes: d.int64Value(forKeys: ["size_bytes"], default: 0),
                previewPath: d.optCleanString("preview_path") ?? d.optCleanString("preview_url"),
                downloadPath: d.optCleanString("download_path") ?? d.optCleanString("download_url")
            )
        }

        let firstWorkIso = obj.stringValue(forKeys: ["first_work_date"])
        let effIso = statusObj.stringValue(forKeys: ["effective_date"], default: firstWorkIso)

        return WorkerEntryDetail(
            entryId: obj.stringValue(forKeys: ["entry_id", "id"], default: fallbackEntryId),
            version: obj.intValue(forKeys: ["version", "entry_version"], default: 1),
            employeeCode: obj.stringValue(forKeys: ["employee_code", "worker_code"]),
            displayName: detailsObj.stringValue(forKeys: ["display_name", "full_name"], default: obj.stringValue(forKeys: ["display_name"])),
            projectId: obj.stringValue(forKeys: ["project_id"]),
            projectDisplay: obj.stringValue(forKeys: ["project_display", "project_name"]),
            recruiterId: obj.stringValue(forKeys: ["recruiter_id"]),
            recruiterDisplay: obj.stringValue(forKeys: ["recruiter_display", "recruiter_name"]),
            laborType: obj.stringValue(forKeys: ["labor_type"]),
            laborTypeDisplay: obj.stringValue(forKeys: ["labor_type_display"]),
            firstWorkDate: firstWorkIso,
            employmentStatus: statusObj.stringValue(forKeys: ["status"], default: obj.stringValue(forKeys: ["employment_status"], default: "ON")).uppercased(),
            employmentEffectiveDate: effIso,
            employmentLeaveReason: statusObj.optCleanString("leave_reason") ?? obj.optCleanString("leave_reason"),
            gender: detailsObj.optCleanString("gender"),
            dateOfBirth: detailsObj.optCleanString("date_of_birth"),
            phone: detailsObj.optCleanString("phone"),
            nationalId: detailsObj.optCleanString("national_id"),
            nationalIdIssuedAt: detailsObj.optCleanString("national_id_issued_at"),
            nationalIdIssuedPlace: detailsObj.optCleanString("national_id_issued_place"),
            address: detailsObj.optCleanString("address"),
            bankId: payObj.optCleanString("bank_id"),
            bankName: payObj.optCleanString("bank_display") ?? payObj.optCleanString("bank_name"),
            accountNumber: payObj.optCleanString("account_number"),
            accountHolderName: payObj.optCleanString("account_holder_name"),
            generalNote: obj.optCleanString("general_note"),
            portraitPreviewPath: obj.optCleanString("portrait_preview_path")
                ?? docs.first(where: { $0.documentType == "WORKER_PORTRAIT" })?.previewPath,
            documents: docs
        )
    }

    public func createChangeRequest(
        entryId: String,
        targetKind: String,
        expectedVersion: Int,
        proposal: [String: Any],
        reason: String?
    ) async throws -> ChangeRequestCreated {
        var body: [String: Any] = [
            "entry_id": entryId,
            "target_kind": targetKind,
            "expected_entry_version": expectedVersion,
            "proposal": proposal
        ]
        if let r = reason?.trimmingCharacters(in: .whitespacesAndNewlines), !r.isEmpty {
            body["reason"] = r
        }
        let json = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/change-requests",
            jsonBody: body
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        return ChangeRequestCreated(
            requestId: dataObj.stringValue(forKeys: ["request_id", "id"], default: UUID().uuidString),
            state: dataObj.stringValue(forKeys: ["state", "status"], default: "PENDING")
        )
    }

    public func privilegedEditEntry(
        entryId: String,
        expectedEntryVersion: Int,
        patch: [String: Any],
        reason: String?
    ) async throws -> WorkerEntryDetail {
        var body: [String: Any] = [
            "expected_entry_version": expectedEntryVersion,
            "patch": patch
        ]
        if let r = reason?.trimmingCharacters(in: .whitespacesAndNewlines), !r.isEmpty {
            body["reason"] = r
        }
        let json = try await httpClient.requestJson(
            method: "PATCH",
            path: "/api/direct-entry/entries/\(entryId)",
            jsonBody: body
        )
        let dataObj = (json["data"] as? [String: Any]) ?? (json["entry"] as? [String: Any]) ?? json
        return Self.parseEntryDetail(dataObj, fallbackEntryId: entryId)
    }

    public func exportWorkersExcel(
        scope: String,
        projectId: String? = nil,
        recruiterId: String? = nil,
        employmentStatus: String? = nil
    ) async throws -> URL {
        let (bytes, _, _) = try await httpClient.requestRawBytes(
            method: "GET",
            path: "/api/direct-entry/workers/export.xlsx",
            queryParams: [
                ("scope", scope),
                ("project_id", projectId),
                ("recruiter_id", recruiterId),
                ("employment_status", employmentStatus)
            ]
        )
        let fileName = "danh-sach-nld-\(scope)-\(DirectEntryContract.todayIsoInHcm()).xlsx"
        let fileUrl = attachmentsDir.appendingPathComponent(fileName)
        try bytes.write(to: fileUrl, options: .atomic)
        return fileUrl
    }

    public func downloadEntryDocument(downloadPath: String, fallbackFileName: String) async throws -> URL {
        let (bytes, _, _) = try await httpClient.requestRawBytes(
            method: "GET",
            path: downloadPath
        )
        let safeName = fallbackFileName.replacingOccurrences(of: "/", with: "_")
        let fileUrl = attachmentsDir.appendingPathComponent(safeName)
        try bytes.write(to: fileUrl, options: .atomic)
        return fileUrl
    }

    // MARK: - CCCD Review Queue & Resolution

    public func fetchCccdReviewQueue(
        statusFilter: String = "NEEDS_REVIEW",
        limit: Int = 25,
        offset: Int = 0
    ) async throws -> CccdReviewQueuePage {
        let json = try await httpClient.requestJson(
            method: "GET",
            path: "/api/direct-entry/cccd-review",
            queryParams: [
                ("status", statusFilter),
                ("limit", String(limit)),
                ("offset", String(offset))
            ]
        )
        let dataObj = (json["data"] as? [String: Any]) ?? json
        let sumObj = (dataObj["summary"] as? [String: Any]) ?? [:]
        let summary = CccdReviewSummary(
            needsReviewCount: sumObj.intValue(forKeys: ["needs_review_count", "needs_review"]),
            mismatchCount: sumObj.intValue(forKeys: ["mismatch_count", "mismatch"]),
            qrUnreadableCount: sumObj.intValue(forKeys: ["qr_unreadable_count", "qr_unreadable"]),
            pendingCount: sumObj.intValue(forKeys: ["pending_count", "pending"]),
            matchedInitialCount: sumObj.intValue(forKeys: ["matched_initial_count", "matched_initial"]),
            resolvedCount: sumObj.intValue(forKeys: ["resolved_count", "resolved"]),
            totalCount: sumObj.intValue(forKeys: ["total_count", "total"])
        )

        let rawRows = (dataObj["items"] as? [[String: Any]])
            ?? (dataObj["rows"] as? [[String: Any]])
            ?? []
        let rows = rawRows.compactMap { r -> CccdReviewQueueItem? in
            let entryId = r.stringValue(forKeys: ["entry_id", "id"])
            guard !entryId.isEmpty else { return nil }
            var qrPayload: CccdReviewExtractedQr? = nil
            if let qObj = r["qr_extracted_payload"] as? [String: Any] {
                qrPayload = CccdReviewExtractedQr(
                    cccd12: qObj.optCleanString("national_id") ?? qObj.optCleanString("cccd12"),
                    legacyCmnd9: qObj.optCleanString("legacy_id") ?? qObj.optCleanString("cmnd9"),
                    fullName: qObj.optCleanString("display_name") ?? qObj.optCleanString("full_name"),
                    dateOfBirthUi: qObj.optCleanString("date_of_birth").map { DirectEntryContract.isoDateToUiDate($0) },
                    genderUi: qObj.optCleanString("gender").map { DirectEntryContract.genderUiLabel($0) },
                    address: qObj.optCleanString("address"),
                    issueDateUi: qObj.optCleanString("national_id_issued_at").map { DirectEntryContract.isoDateToUiDate($0) },
                    rawQrText: qObj.optCleanString("raw_qr_text")
                )
            }

            let compRaw = (r["comparison"] as? [[String: Any]]) ?? []
            let comparison = compRaw.map { c in
                CccdReviewComparisonField(
                    field: c.stringValue(forKeys: ["field"]),
                    label: c.stringValue(forKeys: ["label"], default: c.stringValue(forKeys: ["field"])),
                    enteredValue: c.optCleanString("entered_value"),
                    qrValue: c.optCleanString("qr_value"),
                    status: c.stringValue(forKeys: ["status"], default: "MATCH").uppercased()
                )
            }

            return CccdReviewQueueItem(
                entryId: entryId,
                entryVersion: r.intValue(forKeys: ["entry_version", "version"], default: 1),
                employeeCode: r.stringValue(forKeys: ["employee_code"]),
                projectId: r.stringValue(forKeys: ["project_id"]),
                projectDisplay: r.stringValue(forKeys: ["project_display"]),
                recruiterId: r.stringValue(forKeys: ["recruiter_id"]),
                recruiterDisplay: r.stringValue(forKeys: ["recruiter_display"]),
                firstWorkDate: r.stringValue(forKeys: ["first_work_date"]),
                reconciliationStatus: r.stringValue(forKeys: ["reconciliation_status", "status"], default: "PENDING").uppercased(),
                enteredDisplayName: r.stringValue(forKeys: ["entered_display_name", "display_name"]),
                enteredNationalId: r.optCleanString("entered_national_id") ?? r.optCleanString("national_id"),
                enteredDateOfBirth: r.optCleanString("entered_date_of_birth"),
                enteredGender: r.optCleanString("entered_gender"),
                enteredAddress: r.optCleanString("entered_address"),
                enteredNationalIdIssuedAt: r.optCleanString("entered_national_id_issued_at"),
                qrExtractedPayload: qrPayload,
                cccdFrontQrStatus: r.stringValue(forKeys: ["cccd_front_qr_status"], default: "NOT_UPLOADED"),
                cccdFrontQrError: r.optCleanString("cccd_front_qr_error"),
                cccdFrontPreviewPath: r.optCleanString("cccd_front_preview_path"),
                cccdBackQrStatus: r.stringValue(forKeys: ["cccd_back_qr_status"], default: "NOT_UPLOADED"),
                cccdBackQrError: r.optCleanString("cccd_back_qr_error"),
                cccdBackPreviewPath: r.optCleanString("cccd_back_preview_path"),
                comparison: comparison,
                resolvedAt: r.optCleanString("resolved_at"),
                resolutionAction: r.optCleanString("resolution_action"),
                resolutionNote: r.optCleanString("resolution_note")
            )
        }

        let total = dataObj.intValue(forKeys: ["total_count", "total"], default: rows.count)
        return CccdReviewQueuePage(
            rows: rows,
            totalCount: total,
            limit: dataObj.intValue(forKeys: ["limit"], default: limit),
            offset: dataObj.intValue(forKeys: ["offset"], default: offset),
            summary: summary
        )
    }

    public func retryCccdQrExtraction(entryId: String, expectedEntryVersion: Int) async throws {
        _ = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/entries/\(entryId)/cccd-review/retry-qr",
            jsonBody: ["expected_entry_version": expectedEntryVersion]
        )
    }

    public func resolveCccdReviewUpdateEnteredCccd(
        entryId: String,
        expectedEntryVersion: Int,
        nationalId: String,
        applyBlankFieldsFromQr: Bool,
        reason: String?
    ) async throws {
        let cleanCccd = nationalId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard DirectEntryContract.isValidCccd12(cleanCccd) else {
            throw BackendApiException(
                kind: .validationError,
                statusCode: 422,
                backendCode: "INVALID_NATIONAL_ID",
                message: "Số CCCD cập nhật phải gồm đúng 12 chữ số hợp lệ."
            )
        }
        var body: [String: Any] = [
            "action": "UPDATE_ENTERED_CCCD",
            "expected_entry_version": expectedEntryVersion,
            "national_id": cleanCccd,
            "apply_blank_fields_from_qr": applyBlankFieldsFromQr
        ]
        if let r = reason?.trimmingCharacters(in: .whitespacesAndNewlines), !r.isEmpty {
            body["reason"] = r
        }
        _ = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/entries/\(entryId)/cccd-review/resolve",
            jsonBody: body
        )
    }

    public func resolveCccdReviewStandardizeFromQr(
        entryId: String,
        expectedEntryVersion: Int,
        fields: [String],
        reason: String?
    ) async throws {
        let validFields = fields.filter { DirectEntryContract.allowedCccdStandardizeFields.contains($0) }
        guard !validFields.isEmpty else {
            throw BackendApiException(
                kind: .validationError,
                statusCode: 422,
                backendCode: "EMPTY_FIELDS",
                message: "Vui lòng chọn ít nhất 1 trường thông tin để chuẩn hóa từ QR."
            )
        }
        var body: [String: Any] = [
            "action": "STANDARDIZE_FROM_QR",
            "expected_entry_version": expectedEntryVersion,
            "fields": validFields
        ]
        if let r = reason?.trimmingCharacters(in: .whitespacesAndNewlines), !r.isEmpty {
            body["reason"] = r
        }
        _ = try await httpClient.requestJson(
            method: "POST",
            path: "/api/direct-entry/entries/\(entryId)/cccd-review/resolve",
            jsonBody: body
        )
    }
}
