import Foundation

// MARK: - Attendance Time Contract & Server Time Anchor
// Mirrors Android's `data/AttendanceTimeAnchor.kt` 100%

public enum AttendanceTimeContract {
    public static let sourceServerAnchored = "SERVER_ANCHORED"
    public static let sourceDeviceClockUnverified = "DEVICE_CLOCK_UNVERIFIED"

    public static let maxAnchorAgeMs: Int64 = 12 * 60 * 60 * 1000 // 12 hours
    public static let refreshBeforeExpiryMs: Int64 = 15 * 60 * 1000 // 15 minutes
    public static let minElapsedToleranceMs: Int64 = -2_000
}

public struct ServerTimeAnchor: Codable, Equatable {
    public let serverEpochMsAtSync: Int64
    public let serverNowIso: String
    public let syncedElapsedRealtimeMs: Int64
    public let syncedDeviceEpochMs: Int64
    public let timeToken: String?
    public let expiresAtEpochMs: Int64
    public let bootSessionId: String

    public init(
        serverEpochMsAtSync: Int64,
        serverNowIso: String,
        syncedElapsedRealtimeMs: Int64,
        syncedDeviceEpochMs: Int64,
        timeToken: String?,
        expiresAtEpochMs: Int64,
        bootSessionId: String
    ) {
        self.serverEpochMsAtSync = serverEpochMsAtSync
        self.serverNowIso = serverNowIso
        self.syncedElapsedRealtimeMs = syncedElapsedRealtimeMs
        self.syncedDeviceEpochMs = syncedDeviceEpochMs
        self.timeToken = timeToken
        self.expiresAtEpochMs = expiresAtEpochMs
        self.bootSessionId = bootSessionId
    }
}

public enum AnchorEvaluationStatus: String, Codable, Equatable {
    case valid = "VALID"
    case missing = "MISSING"
    case rebootDetected = "REBOOT_DETECTED"
    case monotonicRollback = "MONOTONIC_ROLLBACK"
    case expired = "EXPIRED"
}

public struct AnchorEvaluationResult: Equatable {
    public let status: AnchorEvaluationStatus
    public let anchor: ServerTimeAnchor?
    public let estimatedServerEpochMs: Int64?
    public let elapsedSinceSyncMs: Int64?
    public let remainingValidMs: Int64?
    public let detailMessageVi: String

    public var isTrusted: Bool {
        status == .valid && estimatedServerEpochMs != nil
    }
}

public struct CapturedAttendanceTimestamp: Codable, Equatable {
    public let capturedAtIso: String
    public let capturedEpochMs: Int64
    public let timeSource: String
    public let timeToken: String?
    public let anchorSyncedAtEpochMs: Int64?
    public let elapsedSinceAnchorMs: Int64?
    public let deviceEpochMsAtCapture: Int64
    public let deviceClockSkewMs: Int64?
    public let statusBadgeVi: String
}

public protocol MonotonicClockProviding {
    func elapsedRealtimeMs() -> Int64
    func currentEpochMs() -> Int64
    func bootSessionId() -> String
}

public struct SystemMonotonicClockProvider: MonotonicClockProviding {
    private static let cachedBootSessionId: String = {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000.0)
        let uptimeMs = Int64(ProcessInfo.processInfo.systemUptime * 1000.0)
        let estimatedBootEpochSec = (nowMs - uptimeMs) / 10_000 // 10-second bucket for stability
        return "boot-\(estimatedBootEpochSec)"
    }()

    public init() {}

    public func elapsedRealtimeMs() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1000.0)
    }

    public func currentEpochMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000.0)
    }

    public func bootSessionId() -> String {
        Self.cachedBootSessionId
    }
}

public final class AttendanceTimeAnchorManager {
    private let httpClient: BackendHttpClient
    private let keychain: KeychainStore
    private let clock: MonotonicClockProviding
    private let lock = NSLock()
    private var cachedAnchor: ServerTimeAnchor?

    private static let keyStoredAnchorJson = "attendance_time_anchor_v1"

    public init(
        httpClient: BackendHttpClient = .shared,
        keychain: KeychainStore = .shared,
        clock: MonotonicClockProviding = SystemMonotonicClockProvider()
    ) {
        self.httpClient = httpClient
        self.keychain = keychain
        self.clock = clock
        self.cachedAnchor = Self.loadFromStorage(keychain: keychain)
    }

    @discardableResult
    public func syncTimeAnchor() async -> MiniBiApiResult<ServerTimeAnchor> {
        let elapsedBeforeRequest = clock.elapsedRealtimeMs()
        let response = await httpClient.request(method: "GET", path: "/api/attendance/time")
        let elapsedAfterRequest = clock.elapsedRealtimeMs()
        let deviceNowMs = clock.currentEpochMs()

        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không thể đồng bộ giờ chuẩn từ máy chủ.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let rttMs = max(0, elapsedAfterRequest - elapsedBeforeRequest)
        let syncedElapsedMs = elapsedBeforeRequest + (rttMs / 2)

        guard let parsed = parseServerTimeResponse(
            root: response.jsonObject,
            rawBody: response.rawBody,
            headers: response.headers,
            syncedElapsedMs: syncedElapsedMs,
            deviceNowMs: deviceNowMs
        ) else {
            return .failure(
                message: "Phản hồi giờ máy chủ không hợp lệ.",
                httpStatus: response.statusCode,
                errorCode: "INVALID_TIME_PAYLOAD"
            )
        }

        saveAnchor(parsed)
        return .success(parsed, statusCode: response.statusCode)
    }

    public func evaluateAnchor() -> AnchorEvaluationResult {
        lock.lock()
        let anchor = cachedAnchor
        lock.unlock()

        guard let anchor else {
            return AnchorEvaluationResult(
                status: .missing,
                anchor: nil,
                estimatedServerEpochMs: nil,
                elapsedSinceSyncMs: nil,
                remainingValidMs: nil,
                detailMessageVi: "Chưa đồng bộ giờ máy chủ (sẽ ghi nhận giờ thiết bị tạm thời)."
            )
        }

        let currentBootId = clock.bootSessionId()
        if anchor.bootSessionId != currentBootId {
            return AnchorEvaluationResult(
                status: .rebootDetected,
                anchor: anchor,
                estimatedServerEpochMs: nil,
                elapsedSinceSyncMs: nil,
                remainingValidMs: nil,
                detailMessageVi: "Thiết bị vừa khởi động lại. Cần đồng bộ lại giờ máy chủ."
            )
        }

        let nowElapsedMs = clock.elapsedRealtimeMs()
        let deltaElapsedMs = nowElapsedMs - anchor.syncedElapsedRealtimeMs
        if deltaElapsedMs < AttendanceTimeContract.minElapsedToleranceMs {
            return AnchorEvaluationResult(
                status: .monotonicRollback,
                anchor: anchor,
                estimatedServerEpochMs: nil,
                elapsedSinceSyncMs: deltaElapsedMs,
                remainingValidMs: nil,
                detailMessageVi: "Bộ đếm thời gian hệ thống bị đặt lại. Cần đồng bộ lại giờ máy chủ."
            )
        }

        let safeElapsedMs = max(0, deltaElapsedMs)
        let estimatedServerMs = anchor.serverEpochMsAtSync + safeElapsedMs
        let effectiveExpiryMs = min(
            anchor.expiresAtEpochMs,
            anchor.serverEpochMsAtSync + AttendanceTimeContract.maxAnchorAgeMs
        )
        let remainingMs = effectiveExpiryMs - estimatedServerMs
        if remainingMs <= 0 {
            return AnchorEvaluationResult(
                status: .expired,
                anchor: anchor,
                estimatedServerEpochMs: estimatedServerMs,
                elapsedSinceSyncMs: safeElapsedMs,
                remainingValidMs: 0,
                detailMessageVi: "Mốc giờ máy chủ đã hết hạn. Đang dùng giờ thiết bị tạm thời."
            )
        }

        return AnchorEvaluationResult(
            status: .valid,
            anchor: anchor,
            estimatedServerEpochMs: estimatedServerMs,
            elapsedSinceSyncMs: safeElapsedMs,
            remainingValidMs: remainingMs,
            detailMessageVi: "Đã đồng bộ giờ chuẩn máy chủ."
        )
    }

    public func resolveCaptureTime() -> CapturedAttendanceTimestamp {
        let eval = evaluateAnchor()
        let deviceNowMs = clock.currentEpochMs()

        if eval.isTrusted, let anchor = eval.anchor, let serverEpochMs = eval.estimatedServerEpochMs {
            let elapsedMs = max(0, eval.elapsedSinceSyncMs ?? 0)
            let skewMs = deviceNowMs - serverEpochMs
            return CapturedAttendanceTimestamp(
                capturedAtIso: Self.formatEpochMsAsIso8601Utc(serverEpochMs),
                capturedEpochMs: serverEpochMs,
                timeSource: AttendanceTimeContract.sourceServerAnchored,
                timeToken: anchor.timeToken,
                anchorSyncedAtEpochMs: anchor.serverEpochMsAtSync,
                elapsedSinceAnchorMs: elapsedMs,
                deviceEpochMsAtCapture: deviceNowMs,
                deviceClockSkewMs: skewMs,
                statusBadgeVi: "Giờ chuẩn máy chủ"
            )
        }

        return CapturedAttendanceTimestamp(
            capturedAtIso: Self.formatEpochMsAsIso8601Utc(deviceNowMs),
            capturedEpochMs: deviceNowMs,
            timeSource: AttendanceTimeContract.sourceDeviceClockUnverified,
            timeToken: nil,
            anchorSyncedAtEpochMs: eval.anchor?.serverEpochMsAtSync,
            elapsedSinceAnchorMs: eval.elapsedSinceSyncMs,
            deviceEpochMsAtCapture: deviceNowMs,
            deviceClockSkewMs: nil,
            statusBadgeVi: "Giờ thiết bị (chờ xác thực máy chủ)"
        )
    }

    public func millisUntilProactiveRefresh() -> Int64 {
        let eval = evaluateAnchor()
        guard eval.isTrusted, let remaining = eval.remainingValidMs else {
            return 0
        }
        return max(30_000, remaining - AttendanceTimeContract.refreshBeforeExpiryMs)
    }

    public func clearAnchor() {
        lock.lock()
        cachedAnchor = nil
        lock.unlock()
        keychain.remove(forKey: Self.keyStoredAnchorJson)
    }

    public func seedAnchorForTesting(_ anchor: ServerTimeAnchor) {
        saveAnchor(anchor)
    }

    // MARK: - Internal Helpers

    private func parseServerTimeResponse(
        root: [String: Any]?,
        rawBody: String,
        headers: [AnyHashable: Any],
        syncedElapsedMs: Int64,
        deviceNowMs: Int64
    ) -> ServerTimeAnchor? {
        let payload = (root?["data"] as? [String: Any])
            ?? (root?["time"] as? [String: Any])
            ?? root

        var serverEpochMs: Int64?
        var serverNowIso = ""
        var timeToken: String?
        var expiresAtMs: Int64?

        if let payload {
            if let epochVal = payload.optInt64("server_epoch_ms", "epoch_ms", "timestamp_ms", "now_ms"), epochVal > 0 {
                serverEpochMs = epochVal
            } else if let epochSec = payload.optInt64("server_epoch_sec", "epoch_sec", "timestamp"), epochSec > 0 {
                serverEpochMs = epochSec > 10_000_000_000 ? epochSec : epochSec * 1000
            }

            serverNowIso = payload.optString("server_now", "server_time_iso", "now_iso", "iso", "timestamp_iso", "now")
            if serverEpochMs == nil && !serverNowIso.isEmpty {
                serverEpochMs = Self.parseIso8601ToEpochMs(serverNowIso)
            }

            timeToken = payload.optNullableString("time_token", "token", "signature", "signed_time_token")

            if let expMs = payload.optInt64("expires_at_epoch_ms", "expires_at_ms"), expMs > 0 {
                expiresAtMs = expMs
            } else if let expIso = payload.optNullableString("expires_at", "expires_at_iso") {
                expiresAtMs = Self.parseIso8601ToEpochMs(expIso)
            } else if let ttlSec = payload.optInt64("ttl_seconds", "max_age_seconds"), ttlSec > 0, let baseMs = serverEpochMs {
                expiresAtMs = baseMs + (ttlSec * 1000)
            }
        }

        if serverEpochMs == nil {
            for (k, v) in headers {
                if String(describing: k).lowercased() == "date", let dateHeader = v as? String {
                    serverEpochMs = Self.parseHttpDateHeaderToEpochMs(dateHeader)
                    break
                }
            }
        }

        guard let resolvedEpochMs = serverEpochMs, resolvedEpochMs > 1_577_836_800_000 else {
            return nil
        }

        let resolvedIso = serverNowIso.isEmpty ? Self.formatEpochMsAsIso8601Utc(resolvedEpochMs) : serverNowIso
        let resolvedExpiry = expiresAtMs ?? (resolvedEpochMs + AttendanceTimeContract.maxAnchorAgeMs)

        return ServerTimeAnchor(
            serverEpochMsAtSync: resolvedEpochMs,
            serverNowIso: resolvedIso,
            syncedElapsedRealtimeMs: syncedElapsedMs,
            syncedDeviceEpochMs: deviceNowMs,
            timeToken: timeToken,
            expiresAtEpochMs: resolvedExpiry,
            bootSessionId: clock.bootSessionId()
        )
    }

    private func saveAnchor(_ anchor: ServerTimeAnchor) {
        lock.lock()
        cachedAnchor = anchor
        lock.unlock()
        if let data = try? JSONEncoder().encode(anchor) {
            keychain.setData(data, forKey: Self.keyStoredAnchorJson)
        }
    }

    private static func loadFromStorage(keychain: KeychainStore) -> ServerTimeAnchor? {
        guard let data = keychain.getData(forKey: keyStoredAnchorJson) else {
            return nil
        }
        return try? JSONDecoder().decode(ServerTimeAnchor.self, from: data)
    }

    public static func formatEpochMsAsIso8601Utc(_ epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    public static func formatDisplayDateTimeVi(isoString: String, fallbackEpochMs: Int64? = nil) -> String {
        let epochMs = parseIso8601ToEpochMs(isoString) ?? fallbackEpochMs
        guard let epochMs else {
            return isoString.isEmpty ? "—" : isoString
        }
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "vi_VN")
        formatter.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        formatter.dateFormat = "HH:mm:ss - dd/MM/yyyy"
        return formatter.string(from: date)
    }

    public static func parseIso8601ToEpochMs(_ raw: String) -> Int64? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        let f1 = ISO8601DateFormatter()
        f1.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = f1.date(from: trimmed) {
            return Int64(date.timeIntervalSince1970 * 1000.0)
        }

        let f2 = ISO8601DateFormatter()
        f2.formatOptions = [.withInternetDateTime]
        if let date = f2.date(from: trimmed) {
            return Int64(date.timeIntervalSince1970 * 1000.0)
        }

        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        for pattern in ["yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss"] {
            df.dateFormat = pattern
            if let date = df.date(from: trimmed) {
                return Int64(date.timeIntervalSince1970 * 1000.0)
            }
        }
        return nil
    }

    public static func parseHttpDateHeaderToEpochMs(_ raw: String) -> Int64? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        if let date = df.date(from: trimmed) {
            return Int64(date.timeIntervalSince1970 * 1000.0)
        }
        return nil
    }
}
