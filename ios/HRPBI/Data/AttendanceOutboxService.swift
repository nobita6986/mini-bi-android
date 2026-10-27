import Foundation
import CryptoKit
import Network

// MARK: - Attendance Contract & Offline Outbox Service
// Mirrors Android's `data/AttendanceOutboxService.kt` 100%

public enum AttendanceContract {
    public static let statusPending = "PENDING"
    public static let statusUploading = "UPLOADING"
    public static let statusSynced = "SYNCED"
    public static let statusFailedRetryable = "FAILED_RETRYABLE"
    public static let statusFailedPermanent = "FAILED_PERMANENT"

    public static func statusDisplayLabel(_ status: String) -> String {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case statusPending:
            return "Đang chờ gửi"
        case statusUploading:
            return "Đang đồng bộ..."
        case statusSynced:
            return "Đã đồng bộ"
        case statusFailedRetryable:
            return "Chờ mạng / Thử lại"
        case statusFailedPermanent:
            return "Bị từ chối (cần chụp lại)"
        default:
            return status
        }
    }

    public static func isRetryableStatus(_ status: String) -> Bool {
        let normalized = status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return normalized == statusPending || normalized == statusFailedRetryable
    }

    public static func computeSha256Hex(_ data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

public final class AttendanceOutboxService {
    public static let shared = AttendanceOutboxService()

    private let httpClient: BackendHttpClient
    private let timeAnchorManager: AttendanceTimeAnchorManager
    private let storageDirectory: URL
    private let outboxFileUrl: URL
    private let photosDirectory: URL
    private let lock = NSLock()
    private var items: [AttendanceOutboxItem] = []
    private var pathMonitor: NWPathMonitor?
    private let monitorQueue = DispatchQueue(label: "com.aistudio.minibi.attendance.outbox.monitor")

    public var onOutboxChanged: (() -> Void)?

    public init(
        httpClient: BackendHttpClient = .shared,
        timeAnchorManager: AttendanceTimeAnchorManager = AttendanceTimeAnchorManager(),
        customStorageDirectory: URL? = nil
    ) {
        self.httpClient = httpClient
        self.timeAnchorManager = timeAnchorManager

        let baseDir: URL
        if let customStorageDirectory {
            baseDir = customStorageDirectory
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            baseDir = appSupport.appendingPathComponent("HRPBI_Attendance", isDirectory: true)
        }

        self.storageDirectory = baseDir
        self.outboxFileUrl = baseDir.appendingPathComponent("attendance_outbox_v1.json")
        self.photosDirectory = baseDir.appendingPathComponent("photos", isDirectory: true)

        try? FileManager.default.createDirectory(at: baseDir, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: photosDirectory, withIntermediateDirectories: true)

        self.items = Self.loadItems(from: outboxFileUrl)
        recoverInterruptedUploads()
    }

    public func startNetworkAutoFlush(userKeyProvider: @escaping () -> String?) {
        stopNetworkAutoFlush()
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self, path.status == .satisfied else { return }
            guard let userKey = userKeyProvider(), !userKey.isEmpty else { return }
            Task {
                _ = await self.flushRetryableOutbox(userKey: userKey)
            }
        }
        monitor.start(queue: monitorQueue)
        pathMonitor = monitor
    }

    public func stopNetworkAutoFlush() {
        pathMonitor?.cancel()
        pathMonitor = nil
    }

    public func listItemsForUser(userKey: String) -> [AttendanceOutboxItem] {
        let normalizedKey = userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        lock.lock()
        defer { lock.unlock() }
        return items
            .filter { $0.userKey.lowercased() == normalizedKey }
            .sorted { $0.createdAtEpochMs > $1.createdAtEpochMs }
    }

    public func pendingCountForUser(userKey: String) -> Int {
        listItemsForUser(userKey: userKey).filter {
            AttendanceContract.isRetryableStatus($0.status) || $0.status == AttendanceContract.statusUploading
        }.count
    }

    // MARK: - Enqueue & Immediate Attempt

    public func submitCheckIn(
        userKey: String,
        lat: Double,
        lng: Double,
        accuracyM: Double?,
        locationCapturedAt: String?,
        photoData: Data,
        mimeType: String = "image/jpeg",
        note: String = "",
        captureTimestampOverride: CapturedAttendanceTimestamp? = nil
    ) async -> (item: AttendanceOutboxItem, apiResult: MiniBiApiResult<AttendanceOutboxItem>) {
        let localId = UUID().uuidString.lowercased()
        let idempotencyKey = localId
        let capturedTime = captureTimestampOverride ?? timeAnchorManager.resolveCaptureTime()
        let photoSha256 = AttendanceContract.computeSha256Hex(photoData)

        let ext = mimeType.lowercased().contains("png") ? "png" : "jpg"
        let photoFileUrl = photosDirectory.appendingPathComponent("\(localId).\(ext)")
        try? photoData.write(to: photoFileUrl, options: .atomic)

        let initialItem = AttendanceOutboxItem(
            localId: localId,
            userKey: userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            idempotencyKey: idempotencyKey,
            clientCapturedAt: capturedTime.capturedAtIso,
            timeSource: capturedTime.timeSource,
            timeToken: capturedTime.timeToken,
            anchorSyncedAtEpochMs: capturedTime.anchorSyncedAtEpochMs,
            elapsedSinceAnchorMs: capturedTime.elapsedSinceAnchorMs,
            deviceClockSkewMs: capturedTime.deviceClockSkewMs,
            lat: lat,
            lng: lng,
            accuracyM: accuracyM,
            locationCapturedAt: locationCapturedAt ?? capturedTime.capturedAtIso,
            photoLocalPath: photoFileUrl.path,
            photoSha256: photoSha256,
            photoSizeBytes: Int64(photoData.count),
            mimeType: mimeType,
            note: note.trimmingCharacters(in: .whitespacesAndNewlines),
            status: AttendanceContract.statusPending,
            attemptCount: 0,
            createdAtEpochMs: capturedTime.capturedEpochMs
        )

        upsertItem(initialItem)
        let sendResult = await processSingleOutboxItem(localId: localId)
        let currentItem = getItem(localId: localId) ?? initialItem
        return (currentItem, sendResult)
    }

    // MARK: - Flush Retryable Outbox

    public func flushRetryableOutbox(userKey: String) async -> Int {
        let candidates = listItemsForUser(userKey: userKey).filter {
            AttendanceContract.isRetryableStatus($0.status)
        }.sorted { $0.createdAtEpochMs < $1.createdAtEpochMs }

        var syncedCount = 0
        for candidate in candidates {
            let result = await processSingleOutboxItem(localId: candidate.localId)
            if case .success(let updated, _) = result, updated.status == AttendanceContract.statusSynced {
                syncedCount += 1
            }
        }
        return syncedCount
    }

    // MARK: - Process Single Item

    public func processSingleOutboxItem(localId: String) async -> MiniBiApiResult<AttendanceOutboxItem> {
        guard var item = getItem(localId: localId) else {
            return .failure(message: "Không tìm thấy bản ghi chấm công trong hàng đợi.")
        }

        if item.status == AttendanceContract.statusSynced {
            return .success(item)
        }

        item.status = AttendanceContract.statusUploading
        item.attemptCount += 1
        upsertItem(item)

        guard let localPath = item.photoLocalPath,
              let photoData = try? Data(contentsOf: URL(fileURLWithPath: localPath)),
              !photoData.isEmpty else {
            item.status = AttendanceContract.statusFailedPermanent
            item.lastErrorMessage = "Không tìm thấy tệp ảnh gốc để tải lên. Vui lòng chụp lại."
            upsertItem(item)
            return .failure(message: item.lastErrorMessage ?? "Thiếu tệp ảnh.", errorCode: "MISSING_LOCAL_PHOTO")
        }

        // Normalize idempotencyKey to pure UUID if any legacy item had "att-" prefix
        let cleanIdempotencyKey: String = {
            if item.idempotencyKey.hasPrefix("att-") {
                return String(item.idempotencyKey.dropFirst(4))
            }
            return item.idempotencyKey
        }()

        // Build canonical `metadata` JSON object per `check-in-contract.ts` (ALLOWED_METADATA_KEYS)
        var metadataObj: [String: Any] = [
            "latitude": item.lat,
            "longitude": item.lng,
            "accuracy_meters": item.accuracyM ?? NSNull(),
            "address_hint": NSNull(),
            "client_captured_at": item.clientCapturedAt,
            "note": item.note.isEmpty ? NSNull() : item.note,
            "idempotency_key": cleanIdempotencyKey,
            "photo_sha256": item.photoSha256,
            "time_source": item.timeSource
        ]
        if item.timeSource == AttendanceTimeContract.sourceServerAnchored,
           let token = item.timeToken, !token.isEmpty,
           let elapsed = item.elapsedSinceAnchorMs, elapsed >= 0 {
            metadataObj["time_token"] = token
            metadataObj["elapsed_since_anchor_ms"] = elapsed
        } else {
            metadataObj["time_token"] = NSNull()
            metadataObj["elapsed_since_anchor_ms"] = NSNull()
        }

        guard let metadataJsonData = try? JSONSerialization.data(withJSONObject: metadataObj),
              let metadataJsonString = String(data: metadataJsonData, encoding: .utf8) else {
            item.status = AttendanceContract.statusFailedPermanent
            item.lastErrorMessage = "Không thể đóng gói dữ liệu chấm công."
            upsertItem(item)
            return .failure(message: "Không thể đóng gói dữ liệu chấm công.")
        }

        let response = await httpClient.uploadMultipart(
            path: "/api/attendance/check-in",
            fieldName: "photo",
            fileName: "\(item.localId).jpg",
            mimeType: item.mimeType,
            fileData: photoData,
            extraFields: ["metadata": metadataJsonString],
            additionalHeaders: ["Idempotency-Key": cleanIdempotencyKey]
        )

        if response.isSuccess || (response.statusCode == 409 && isAlreadyProcessedConflict(response)) {
            let recordObj = (response.jsonObject?["checkin"] as? [String: Any])
                ?? (response.jsonObject?["check_in"] as? [String: Any])
                ?? (response.jsonObject?["item"] as? [String: Any])
                ?? (response.jsonObject?["data"] as? [String: Any])
                ?? response.jsonObject

            item.status = AttendanceContract.statusSynced
            item.serverRecordId = recordObj?.optInt64("id", "check_in_id")
            item.serverReceivedAt = recordObj?.optNullableString("server_received_at", "created_at", "received_at")
                ?? AttendanceTimeAnchorManager.formatEpochMsAsIso8601Utc(Int64(Date().timeIntervalSince1970 * 1000))
            item.lastErrorCode = nil
            item.lastErrorMessage = nil

            // Clean up local photo after successful sync to save storage
            if let path = item.photoLocalPath {
                try? FileManager.default.removeItem(atPath: path)
                item.photoLocalPath = nil
            }
            upsertItem(item)
            return .success(item, statusCode: response.statusCode)
        } else {
            let isPermanent = isPermanentError(httpStatus: response.statusCode, errorCode: response.errorCode)
            item.status = isPermanent ? AttendanceContract.statusFailedPermanent : AttendanceContract.statusFailedRetryable
            item.lastErrorCode = response.errorCode
            item.lastErrorMessage = response.errorMessage ?? "Chưa thể gửi chấm công (HTTP \(response.statusCode))."
            upsertItem(item)
            return .failure(
                message: item.lastErrorMessage ?? "Lỗi gửi chấm công.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
    }

    // MARK: - Server History Sync & Report Export

    public func syncServerHistoryForUser(userKey: String, days: Int = 30) async -> MiniBiApiResult<(items: [AttendanceOutboxItem], todayCount: Int)> {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        let now = Date()
        let fromDate = cal.date(byAdding: .day, value: -max(days, 1), to: now) ?? now
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = cal.timeZone
        df.dateFormat = "yyyy-MM-dd"

        let queryItems: [URLQueryItem] = [
            URLQueryItem(name: "from_date", value: df.string(from: fromDate)),
            URLQueryItem(name: "to_date", value: df.string(from: now)),
            URLQueryItem(name: "page", value: "1"),
            URLQueryItem(name: "page_size", value: "100")
        ]

        let response = await httpClient.request(
            method: "GET",
            path: "/api/attendance/history",
            queryItems: queryItems
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được lịch sử chấm công.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let reportObj = (root["report"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let rawItems = reportObj.optObjectArray("items")
            .ifEmpty(root.optObjectArray("items"))
            .ifEmpty(root.optObjectArray("check_ins"))

        let todayServerCount = root.optInt("today_count", "today_check_in_count", defaultValue: -1)
        let normalizedKey = userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        lock.lock()
        var currentList = items
        for obj in rawItems {
            let idempKey = obj.optString("idempotency_key", "client_id")
            let serverId = obj.optInt64("id", "check_in_id")
            let capturedAt = obj.optString("client_captured_at", "captured_at", "created_at")
            let receivedAt = obj.optNullableString("server_received_at", "received_at", "created_at")
            let lat = obj.optDouble("lat", "latitude")
            let lng = obj.optDouble("lng", "longitude")
            let acc = obj.optNullableDouble("accuracy_m", "accuracy")
            let note = obj.optString("note")
            let timeSource = obj.optString("time_source", defaultValue: AttendanceTimeContract.sourceServerAnchored)
            let photoSha = obj.optString("photo_sha256")

            if let existingIdx = currentList.firstIndex(where: {
                (!idempKey.isEmpty && $0.idempotencyKey == idempKey) ||
                (serverId != nil && $0.serverRecordId == serverId)
            }) {
                currentList[existingIdx].status = AttendanceContract.statusSynced
                currentList[existingIdx].serverRecordId = serverId ?? currentList[existingIdx].serverRecordId
                currentList[existingIdx].serverReceivedAt = receivedAt ?? currentList[existingIdx].serverReceivedAt
            } else {
                let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(capturedAt)
                    ?? Int64(Date().timeIntervalSince1970 * 1000)
                let syncedItem = AttendanceOutboxItem(
                    localId: "srv-\(serverId ?? epochMs)",
                    userKey: normalizedKey,
                    idempotencyKey: idempKey.isEmpty ? "srv-\(serverId ?? epochMs)" : idempKey,
                    clientCapturedAt: capturedAt,
                    timeSource: timeSource,
                    lat: lat,
                    lng: lng,
                    accuracyM: acc,
                    photoSha256: photoSha,
                    photoSizeBytes: obj.optInt64("photo_size_bytes") ?? 0,
                    mimeType: obj.optString("mime_type", defaultValue: "image/jpeg"),
                    note: note,
                    status: AttendanceContract.statusSynced,
                    attemptCount: 1,
                    serverRecordId: serverId,
                    serverReceivedAt: receivedAt,
                    createdAtEpochMs: epochMs
                )
                currentList.append(syncedItem)
            }
        }
        items = currentList
        Self.saveItems(items, to: outboxFileUrl)
        lock.unlock()

        onOutboxChanged?()
        let userItems = listItemsForUser(userKey: userKey)
        let computedTodayCount = todayServerCount >= 0
            ? todayServerCount
            : countTodaySyncedItems(userItems)
        return .success((items: userItems, todayCount: computedTodayCount), statusCode: response.statusCode)
    }

    /// Exports an attendance report file (first tries server `/api/attendance/export` if available,
    /// otherwise generates a UTF-8 CSV with BOM from the user's synced & pending attendance records)
    /// so the user can share/save the file via iOS Share Sheet.
    public func exportAttendanceReport(userKey: String, startDate: String? = nil, endDate: String? = nil) async -> MiniBiApiResult<URL> {
        var queryItems: [URLQueryItem] = []
        if let startDate, !startDate.isEmpty {
            queryItems.append(URLQueryItem(name: "start_date", value: startDate))
        }
        if let endDate, !endDate.isEmpty {
            queryItems.append(URLQueryItem(name: "end_date", value: endDate))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/attendance/export",
            queryItems: queryItems
        )

        let timestamp = Int64(Date().timeIntervalSince1970)
        if response.isSuccess && !response.rawData.isEmpty && response.jsonObject == nil {
            let ext = (response.headers["Content-Type"] as? String)?.contains("csv") == true ? "csv" : "xlsx"
            let fileUrl = FileManager.default.temporaryDirectory.appendingPathComponent("bao-cao-cham-cong-\(timestamp).\(ext)")
            do {
                try response.rawData.write(to: fileUrl, options: .atomic)
                return .success(fileUrl, statusCode: response.statusCode)
            } catch {
                // Fall back to CSV generation below
            }
        }

        // Generate rich UTF-8 CSV with BOM from local + synced records for sharing
        let userItems = listItemsForUser(userKey: userKey)
        var lines: [String] = [
            "Mã định danh,Thời gian chấm công,Nguồn giờ,Trạng thái,Vĩ độ (Lat),Kinh độ (Lng),Sai số GPS (m),Ghi chú,Thời gian máy chủ nhận"
        ]
        for record in userItems {
            let cols = [
                record.idempotencyKey,
                AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: record.clientCapturedAt, fallbackEpochMs: record.createdAtEpochMs),
                record.timeSource,
                AttendanceContract.statusDisplayLabel(record.status),
                String(format: "%.6f", record.lat),
                String(format: "%.6f", record.lng),
                record.accuracyM.map { String(format: "%.1f", $0) } ?? "",
                record.note,
                record.serverReceivedAt.map { AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: $0) } ?? ""
            ].map { col -> String in
                let escaped = col.replacingOccurrences(of: "\"", with: "\"\"")
                return "\"\(escaped)\""
            }
            lines.append(cols.joined(separator: ","))
        }

        let csvContent = "\u{FEFF}" + lines.joined(separator: "\r\n")
        let csvUrl = FileManager.default.temporaryDirectory.appendingPathComponent("bao-cao-cham-cong-\(timestamp).csv")
        do {
            guard let data = csvContent.data(using: .utf8) else {
                return .failure(message: "Không thể tạo dữ liệu CSV báo cáo chấm công.")
            }
            try data.write(to: csvUrl, options: .atomic)
            return .success(csvUrl)
        } catch {
            return .failure(message: "Không thể lưu tệp báo cáo chấm công: \(error.localizedDescription)")
        }
    }

    public func removeRejectedItem(localId: String) {
        lock.lock()
        if let idx = items.firstIndex(where: { $0.localId == localId }) {
            if let path = items[idx].photoLocalPath {
                try? FileManager.default.removeItem(atPath: path)
            }
            items.remove(at: idx)
            Self.saveItems(items, to: outboxFileUrl)
        }
        lock.unlock()
        onOutboxChanged?()
    }

    // MARK: - Internal Upload & Storage Helpers

    private func uploadAttendancePhoto(item: AttendanceOutboxItem, photoData: Data) async -> MiniBiApiResult<String> {
        // 1. Try presigned upload URL flow `/api/attendance/photos/upload-url` or `/api/attendance/photos`
        let stageBody: [String: Any] = [
            "idempotency_key": item.idempotencyKey,
            "file_name": "\(item.localId).jpg",
            "mime_type": item.mimeType,
            "file_size_bytes": item.photoSizeBytes,
            "photo_sha256": item.photoSha256
        ]

        let stageRes = await httpClient.request(
            method: "POST",
            path: "/api/attendance/photos/upload-url",
            jsonBody: stageBody
        )

        if stageRes.isSuccess, let json = stageRes.jsonObject {
            let dataObj = (json["data"] as? [String: Any]) ?? json
            let uploadUrl = dataObj.optString("upload_url", "presigned_url", "put_url")
            let objectKey = dataObj.optString("object_key", "photo_object_key", "key")
            if !uploadUrl.isEmpty && !objectKey.isEmpty {
                var putHeaders: [String: String] = ["Content-Type": item.mimeType]
                if let customHeaders = dataObj["upload_headers"] as? [String: Any] {
                    for (k, v) in customHeaders {
                        if let s = v as? String {
                            putHeaders[k] = s
                        }
                    }
                }
                let putRes = await httpClient.putPresignedUrl(
                    uploadUrl: uploadUrl,
                    fileData: photoData,
                    contentType: item.mimeType,
                    additionalHeaders: putHeaders
                )
                if putRes.isSuccess {
                    return .success(objectKey, statusCode: putRes.statusCode)
                }
                return .failure(
                    message: putRes.errorMessage ?? "Tải ảnh chấm công lên R2 thất bại.",
                    httpStatus: putRes.statusCode,
                    errorCode: putRes.errorCode
                )
            }
        }

        // 2. Fallback to direct multipart upload `/api/attendance/photos`
        let multipartRes = await httpClient.uploadMultipart(
            path: "/api/attendance/photos",
            fieldName: "file",
            fileName: "\(item.localId).jpg",
            mimeType: item.mimeType,
            fileData: photoData,
            extraFields: [
                "idempotency_key": item.idempotencyKey,
                "photo_sha256": item.photoSha256
            ]
        )

        if multipartRes.isSuccess {
            let dataObj = (multipartRes.jsonObject?["data"] as? [String: Any]) ?? multipartRes.jsonObject ?? [:]
            let objectKey = dataObj.optString("object_key", "photo_object_key", "key", "file_path", defaultValue: item.idempotencyKey)
            return .success(objectKey, statusCode: multipartRes.statusCode)
        }

        return .failure(
            message: multipartRes.errorMessage ?? "Không thể tải ảnh chấm công lên máy chủ.",
            httpStatus: multipartRes.statusCode,
            errorCode: multipartRes.errorCode
        )
    }

    private func isPermanentError(httpStatus: Int?, errorCode: String?) -> Bool {
        guard let httpStatus else { return false }
        if httpStatus == 401 || httpStatus == 408 || httpStatus == 429 || httpStatus >= 500 || httpStatus == 0 {
            return false
        }
        if httpStatus == 400 || httpStatus == 403 || httpStatus == 422 {
            return true
        }
        if let code = errorCode?.uppercased() {
            if code.contains("INVALID_") || code.contains("REJECTED") || code.contains("TAMPER") {
                return true
            }
        }
        return false
    }

    private func isAlreadyProcessedConflict(_ response: BackendHttpResponse) -> Bool {
        let code = (response.errorCode ?? "").uppercased()
        if code.contains("ALREADY") || code.contains("DUPLICATE") || code.contains("IDEMPOTENT") {
            return true
        }
        if let obj = response.jsonObject, obj["check_in"] != nil || obj["already_recorded"] as? Bool == true {
            return true
        }
        return false
    }

    private func countTodaySyncedItems(_ list: [AttendanceOutboxItem]) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        let todayComponents = cal.dateComponents([.year, .month, .day], from: Date())

        return list.filter { item in
            guard item.status == AttendanceContract.statusSynced else { return false }
            let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(item.clientCapturedAt) ?? item.createdAtEpochMs
            let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
            let comps = cal.dateComponents([.year, .month, .day], from: date)
            return comps.year == todayComponents.year
                && comps.month == todayComponents.month
                && comps.day == todayComponents.day
        }.count
    }

    private func recoverInterruptedUploads() {
        lock.lock()
        var changed = false
        for i in items.indices {
            if items[i].status == AttendanceContract.statusUploading {
                items[i].status = AttendanceContract.statusFailedRetryable
                changed = true
            }
        }
        if changed {
            Self.saveItems(items, to: outboxFileUrl)
        }
        lock.unlock()
    }

    private func getItem(localId: String) -> AttendanceOutboxItem? {
        lock.lock()
        defer { lock.unlock() }
        return items.first(where: { $0.localId == localId })
    }

    private func upsertItem(_ item: AttendanceOutboxItem) {
        lock.lock()
        if let idx = items.firstIndex(where: { $0.localId == item.localId }) {
            items[idx] = item
        } else {
            items.append(item)
        }
        Self.saveItems(items, to: outboxFileUrl)
        lock.unlock()
        onOutboxChanged?()
    }

    private static func loadItems(from url: URL) -> [AttendanceOutboxItem] {
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([AttendanceOutboxItem].self, from: data) else {
            return []
        }
        return decoded
    }

    private static func saveItems(_ list: [AttendanceOutboxItem], to url: URL) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        try? data.write(to: url, options: .atomic)
    }
}

private extension Array {
    func ifEmpty(_ fallback: @autoclosure () -> [Element]) -> [Element] {
        isEmpty ? fallback() : self
    }
}
