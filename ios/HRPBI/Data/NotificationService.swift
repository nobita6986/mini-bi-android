import Foundation

// MARK: - Real-time SSE Notification Service & Core Repository
// Mirrors Android's `data/MiniBiRepository.kt` notification stream, auth, dashboard, and worker APIs

public final class NotificationService {
    public static let shared = NotificationService()

    private let httpClient: BackendHttpClient
    private var streamTask: Task<Void, Never>?

    public init(httpClient: BackendHttpClient = .shared) {
        self.httpClient = httpClient
    }

    public func fetchNotifications(limit: Int = 50, unreadOnly: Bool = false) async -> MiniBiApiResult<(items: [AppNotificationItem], unreadCount: Int)> {
        var queryItems = [URLQueryItem(name: "limit", value: String(limit))]
        if unreadOnly {
            queryItems.append(URLQueryItem(name: "unread_only", value: "true"))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/notifications",
            queryItems: queryItems
        )
        if response.statusCode == 404 {
            return .success((items: [], unreadCount: 0), statusCode: 200)
        }
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được danh sách thông báo.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let dataObj = (root["data"] as? [String: Any]) ?? root
        let rawList = dataObj.optObjectArray("notifications")
            .ifEmpty(dataObj.optObjectArray("items"))

        let items = rawList.enumerated().map { idx, obj in
            Self.parseNotificationItem(obj, fallbackIndex: idx)
        }
        let unreadCount = dataObj.optInt(
            "unread_count",
            "unread",
            defaultValue: items.filter { !$0.isRead }.count
        )
        return .success((items: items, unreadCount: unreadCount), statusCode: response.statusCode)
    }

    public func markAsRead(notificationId: Int64) async -> MiniBiApiResult<Bool> {
        let response = await httpClient.request(
            method: "POST",
            path: "/api/notifications/\(notificationId)/read",
            jsonBody: [:]
        )
        if response.isSuccess {
            return .success(true, statusCode: response.statusCode)
        }
        // Fallback PATCH if server uses PATCH
        let patchRes = await httpClient.request(
            method: "PATCH",
            path: "/api/notifications/\(notificationId)/read",
            jsonBody: [:]
        )
        guard patchRes.isSuccess else {
            return .failure(
                message: patchRes.errorMessage ?? "Không thể đánh dấu đã đọc.",
                httpStatus: patchRes.statusCode,
                errorCode: patchRes.errorCode
            )
        }
        return .success(true, statusCode: patchRes.statusCode)
    }

    public func markAllAsRead() async -> MiniBiApiResult<Bool> {
        let response = await httpClient.request(
            method: "POST",
            path: "/api/notifications/read-all",
            jsonBody: [:]
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không thể đánh dấu tất cả đã đọc.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(true, statusCode: response.statusCode)
    }

    // MARK: - SSE Stream (`GET /api/notifications/stream`)

    public func startSseStream(
        onEvent: @escaping @Sendable (AppNotificationItem?) -> Void
    ) {
        stopSseStream()
        streamTask = Task { [weak self] in
            guard let self else { return }
            var retryDelayNs: UInt64 = 3_000_000_000
            while !Task.isCancelled {
                do {
                    guard let request = try? self.httpClient.makeSseRequest(path: "/api/notifications/stream") else {
                        break
                    }
                    let (bytes, response) = try await self.httpClient.sseSession.bytes(for: request)
                    guard let httpResponse = response as? HTTPURLResponse else {
                        break
                    }
                    if httpResponse.statusCode == 404 || httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {
                        // Endpoint not enabled or unauthenticated; stop polling SSE
                        break
                    }
                    guard (200...299).contains(httpResponse.statusCode) else {
                        try? await Task.sleep(nanoseconds: retryDelayNs)
                        retryDelayNs = min(retryDelayNs * 2, 30_000_000_000)
                        continue
                    }

                    retryDelayNs = 3_000_000_000
                    var currentDataLines: [String] = []

                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                        if trimmed.isEmpty {
                            if !currentDataLines.isEmpty {
                                let payloadText = currentDataLines.joined(separator: "\n")
                                currentDataLines.removeAll()
                                if let data = payloadText.data(using: .utf8),
                                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                                    let notifObj = (json["notification"] as? [String: Any])
                                        ?? (json["item"] as? [String: Any])
                                        ?? json
                                    if notifObj["id"] != nil || notifObj["notification_id"] != nil || notifObj["title"] != nil || notifObj["message"] != nil {
                                        let parsed = Self.parseNotificationItem(notifObj, fallbackIndex: 0)
                                        onEvent(parsed)
                                    } else {
                                        onEvent(nil)
                                    }
                                } else {
                                    onEvent(nil)
                                }
                            }
                            continue
                        }

                        if trimmed.hasPrefix("data:") {
                            let value = String(trimmed.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                            if value != "[DONE]" && value != "ping" && value != "heartbeat" {
                                currentDataLines.append(value)
                            }
                        }
                    }
                    if Task.isCancelled { break }
                    try? await Task.sleep(nanoseconds: retryDelayNs)
                } catch {
                    if Task.isCancelled { break }
                    try? await Task.sleep(nanoseconds: retryDelayNs)
                    retryDelayNs = min(retryDelayNs * 2, 30_000_000_000)
                }
            }
        }
    }

    public func stopSseStream() {
        streamTask?.cancel()
        streamTask = nil
    }

    public static func parseNotificationItem(_ obj: [String: Any], fallbackIndex: Int) -> AppNotificationItem {
        let title = obj.optString("title", "subject", defaultValue: "Thông báo hệ thống")
        let body = obj.optString("body", "message", "content")
        let type = obj.optString("type", "event_type", "category", defaultValue: "GENERAL")
        let isRead = obj.optBool("is_read", "read", defaultValue: obj["read_at"] != nil && !(obj["read_at"] is NSNull))
        let createdAt = obj.optString("created_at", "timestamp")

        let id: Int64 = {
            if let numericId = obj.optInt64("id", "notification_id") {
                return numericId
            }
            let rawStrKey = obj.optString("id", "notification_id", "event_id")
            let seed = rawStrKey.isEmpty ? "\(type)|\(title)|\(body)|\(createdAt)|\(fallbackIndex)" : rawStrKey
            var hash: UInt64 = 14695981039346656037
            for byte in seed.utf8 {
                hash ^= UInt64(byte)
                hash &*= 1099511628211
            }
            return Int64(hash & 0x7FFF_FFFF_FFFF_FFFF)
        }()

        let metaObj = (obj["metadata"] as? [String: Any]) ?? (obj["payload"] as? [String: Any]) ?? obj
        let targetRoute = metaObj.optNullableString("target_route", "route", "screen", "action_url")
            ?? obj.optNullableString("target_route", "route", "screen", "action_url")
        let targetEntityId = metaObj.optNullableString("target_id", "entity_id", "ma_nld", "obligation_id", "entry_id", "submission_id")
            ?? obj.optNullableString("target_id", "entity_id", "ma_nld", "obligation_id", "entry_id", "submission_id")

        var metadata: [String: String] = [:]
        for (k, v) in metaObj {
            if let s = v as? String {
                metadata[k] = s
            } else if let n = v as? NSNumber {
                metadata[k] = n.stringValue
            }
        }

        return AppNotificationItem(
            id: id,
            title: title,
            body: body,
            type: type,
            isRead: isRead,
            createdAt: createdAt,
            targetRoute: targetRoute,
            targetEntityId: targetEntityId,
            metadata: metadata
        )
    }
}

// MARK: - Core MiniBiRepository (Auth, Dashboard Summary, Workers, Contract Upload)

public final class MiniBiRepository {
    public static let shared = MiniBiRepository()

    public let httpClient: BackendHttpClient
    public let keychain: KeychainStore

    private static let keySavedLoginAccounts = "saved_login_accounts_v1"
    private static let keySelectedThemeId = "selected_theme_id_v1"
    private static let keySelectedEnvVariant = "selected_env_variant_v1"
    private static let maxSavedAccounts = 5

    public init(
        httpClient: BackendHttpClient = .shared,
        keychain: KeychainStore = .shared
    ) {
        self.httpClient = httpClient
        self.keychain = keychain
    }

    // MARK: - Saved Login Accounts in iOS Keychain

    public func getSavedLoginAccounts() -> [SavedLoginAccount] {
        guard let data = keychain.getData(forKey: Self.keySavedLoginAccounts),
              let decoded = try? JSONDecoder().decode([SavedLoginAccount].self, from: data) else {
            return []
        }
        return decoded.sorted { $0.updatedAtEpochMs > $1.updatedAtEpochMs }
    }

    public func saveLoginAccount(identifier: String, password: String, displayName: String, role: String = "") {
        let trimmedId = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedId.isEmpty && !password.isEmpty else { return }

        var current = getSavedLoginAccounts().filter {
            $0.identifier.lowercased() != trimmedId.lowercased()
        }
        let entry = SavedLoginAccount(
            identifier: trimmedId,
            password: password,
            displayName: displayName.isEmpty ? trimmedId : displayName,
            role: role,
            updatedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        current.insert(entry, at: 0)
        if current.count > Self.maxSavedAccounts {
            current = Array(current.prefix(Self.maxSavedAccounts))
        }
        if let data = try? JSONEncoder().encode(current) {
            keychain.setData(data, forKey: Self.keySavedLoginAccounts)
        }
    }

    public func removeSavedLoginAccount(identifier: String) {
        let trimmedId = identifier.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let filtered = getSavedLoginAccounts().filter {
            $0.identifier.lowercased() != trimmedId
        }
        if let data = try? JSONEncoder().encode(filtered) {
            keychain.setData(data, forKey: Self.keySavedLoginAccounts)
        }
    }

    // MARK: - Theme & Environment Persistence

    public func getSelectedThemeId() -> String {
        keychain.getString(forKey: Self.keySelectedThemeId) ?? "hr-partner"
    }

    public func setSelectedThemeId(_ themeId: String) {
        keychain.setString(themeId, forKey: Self.keySelectedThemeId)
    }

    public func getSelectedEnvironmentVariant() -> AppEnvironmentVariant? {
        guard let raw = keychain.getString(forKey: Self.keySelectedEnvVariant) else {
            return nil
        }
        return AppEnvironmentVariant(rawValue: raw)
    }

    public func setSelectedEnvironmentVariant(_ variant: AppEnvironmentVariant) {
        keychain.setString(variant.rawValue, forKey: Self.keySelectedEnvVariant)
    }

    // MARK: - Auth APIs

    public func login(identifier: String, password: String) async -> MiniBiApiResult<MiniBiUser> {
        let trimmedId = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        // Strictly send only "email" and "password" (2 keys) per backend `projectCredentials` contract
        let body: [String: Any] = [
            "email": trimmedId,
            "password": password
        ]

        let response = await httpClient.request(
            method: "POST",
            path: "/api/auth/login",
            jsonBody: body
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Đăng nhập thất bại. Vui lòng kiểm tra tài khoản hoặc mật khẩu.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let userObj = (root["actor"] as? [String: Any])
            ?? (root["user"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root

        let user = Self.parseUser(userObj, fallbackRoot: root, fallbackIdentifier: trimmedId)
        if !user.username.isEmpty || !user.fullName.isEmpty || !user.id.isEmpty {
            return .success(user, statusCode: response.statusCode)
        }

        return await fetchCurrentUser()
    }

    public func fetchCurrentUser() async -> MiniBiApiResult<MiniBiUser> {
        let response = await httpClient.request(method: "GET", path: "/api/auth/session")
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Phiên đăng nhập đã hết hạn.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        let root = response.jsonObject ?? [:]
        let userObj = (root["actor"] as? [String: Any])
            ?? (root["user"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let user = Self.parseUser(userObj, fallbackRoot: root)
        return .success(user, statusCode: response.statusCode)
    }

    public func logout() async {
        _ = await httpClient.request(method: "POST", path: "/api/auth/logout", jsonBody: nil)
        httpClient.cookieStore.clearAll()
    }

    // MARK: - Workers Management APIs

    public func fetchWorkers(
        scope: String = "all",
        search: String = "",
        project: String = "",
        status: String = "",
        manager: String = "",
        recruiter: String = "",
        page: Int = 1,
        pageSize: Int = 30
    ) async -> MiniBiApiResult<(items: [WorkerItem], total: Int, totalPages: Int)> {
        let normalizedScope: String = {
            let s = scope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            switch s {
            case "recruited", "managed", "team", "all": return s
            default: return "all"
            }
        }()

        // Try requested scope first; if "all" returns 403 WORKER_DIRECTORY_DENIED, fallback to narrower allowed scopes
        let candidateScopes: [String] = normalizedScope == "all"
            ? ["all", "team", "managed", "recruited"]
            : [normalizedScope]

        var lastResponse: BackendHttpResponse?
        for candidateScope in candidateScopes {
            var queryItems: [URLQueryItem] = [
                URLQueryItem(name: "scope", value: candidateScope),
                URLQueryItem(name: "page_size", value: String(min(max(pageSize, 1), 100)))
            ]
            let cleanSearch = search.trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleanSearch.isEmpty {
                queryItems.append(URLQueryItem(name: "search", value: cleanSearch))
            }
            let cleanProject = project.trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleanProject.isEmpty {
                queryItems.append(URLQueryItem(name: "project_id", value: cleanProject))
            }
            let cleanStatus = status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if ["ON", "OFF", "UNCONFIRMED"].contains(cleanStatus) {
                queryItems.append(URLQueryItem(name: "employment_status", value: cleanStatus))
            }

            let response = await httpClient.request(
                method: "GET",
                path: "/api/direct-entry/workers",
                queryItems: queryItems
            )
            lastResponse = response
            if response.isSuccess {
                let root = response.jsonObject ?? [:]
                let dataObj = (root["data"] as? [String: Any]) ?? root
                let rawList = dataObj.optObjectArray("items")
                    .ifEmpty(dataObj.optObjectArray("workers"))

                let items = rawList.map { Self.parseWorkerItem($0) }
                let summaryObj = dataObj["summary"] as? [String: Any]
                let total = summaryObj?.optInt("total_workers", defaultValue: items.count)
                    ?? dataObj.optInt("total", "total_count", defaultValue: items.count)
                let totalPages = max(1, dataObj.optInt("total_pages", defaultValue: (total + pageSize - 1) / max(1, pageSize)))
                return .success((items: items, total: total, totalPages: totalPages), statusCode: response.statusCode)
            }
            if response.statusCode != 403 {
                break
            }
        }

        let failedResponse = lastResponse
        return .failure(
            message: failedResponse?.errorMessage ?? "Không tải được danh sách người lao động.",
            httpStatus: failedResponse?.statusCode,
            errorCode: failedResponse?.errorCode
        )
    }

    public func fetchWorkerDetail(maNld: String) async -> MiniBiApiResult<WorkerItem> {
        let encoded = maNld.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? maNld
        let response = await httpClient.request(method: "GET", path: "/api/direct-entry/entries/\(encoded)")
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được chi tiết hồ sơ NLĐ.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        let root = response.jsonObject ?? [:]
        let obj = (root["entry"] as? [String: Any])
            ?? (root["worker"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        return .success(Self.parseWorkerItem(obj), statusCode: response.statusCode)
    }

    /// Uploads contract file (single file or merged PDF from multiple images) to `POST /api/direct-entry/entries/{entryId}/documents`
    public func uploadWorkerContractAttachments(
        maNld: String,
        attachments: [LocalAttachmentItem]
    ) async -> MiniBiApiResult<String> {
        guard !attachments.isEmpty else {
            return .failure(message: "Vui lòng chọn ít nhất 1 ảnh hoặc tệp PDF hợp đồng.")
        }

        return await DirectEntryService.shared.uploadEntryDocumentAttachments(
            entryIdUuid: maNld,
            docType: DirectEntryContract.docTypeEmploymentContract,
            attachments: attachments,
            initialEntryVersion: nil
        )
    }

    public func proposeWorkerChange(
        maNld: String,
        changes: [String: String],
        reason: String
    ) async -> MiniBiApiResult<Bool> {
        let encoded = maNld.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? maNld
        let body: [String: Any] = [
            "changes": changes,
            "reason": reason
        ]
        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/workers/\(encoded)/change-requests",
            jsonBody: body
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Gửi đề xuất chỉnh sửa thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        return .success(true, statusCode: response.statusCode)
    }

    public func directEditWorker(
        maNld: String,
        updates: [String: String]
    ) async -> MiniBiApiResult<WorkerItem> {
        let encoded = maNld.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? maNld
        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/workers/\(encoded)/direct-edit",
            jsonBody: updates
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Cập nhật thông tin NLĐ thất bại.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
        let root = response.jsonObject ?? [:]
        let obj = (root["entry"] as? [String: Any])
            ?? (root["worker"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        return .success(Self.parseWorkerItem(obj), statusCode: response.statusCode)
    }

    public func exportWorkersExcel(scope: String = "all", search: String = "", project: String = "") async -> MiniBiApiResult<URL> {
        let validScope: String = {
            let s = scope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            return ["recruited", "managed", "team", "all"].contains(s) ? s : "all"
        }()
        let cleanProject = project.trimmingCharacters(in: .whitespacesAndNewlines)
        let body: [String: Any] = [
            "scope": validScope,
            "project_id": cleanProject.isEmpty ? NSNull() : cleanProject,
            "recruiter_id": NSNull(),
            "employment_status": NSNull(),
            "reason": NSNull()
        ]

        let response = await httpClient.request(
            method: "POST",
            path: "/api/direct-entry/workers/export",
            jsonBody: body
        )
        guard response.isSuccess && !response.rawData.isEmpty else {
            return .failure(
                message: response.errorMessage ?? "Không thể xuất danh sách NLĐ.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let timestamp = Int64(Date().timeIntervalSince1970)
        let fileUrl = FileManager.default.temporaryDirectory.appendingPathComponent("danh-sach-nld-\(timestamp).xlsx")
        do {
            try response.rawData.write(to: fileUrl, options: .atomic)
            return .success(fileUrl, statusCode: response.statusCode)
        } catch {
            return .failure(message: "Không thể lưu tệp xuất Excel: \(error.localizedDescription)")
        }
    }

    // MARK: - Parsers

    public static func parseUser(
        _ obj: [String: Any],
        fallbackRoot: [String: Any]? = nil,
        fallbackIdentifier: String = ""
    ) -> MiniBiUser {
        let id = obj.optString("app_user_id", "id", "user_id", "username", defaultValue: fallbackIdentifier)
        let username = obj.optString("username", "login_id", "email", "app_user_id", "user_code", "code", defaultValue: fallbackIdentifier.isEmpty ? id : fallbackIdentifier)
        let fullName = obj.optString("display_name", "full_name", "name", defaultValue: username)

        var capabilities = obj.optStringArray("capabilities")
        if capabilities.isEmpty {
            capabilities = obj.optStringArray("permissions")
        }
        if capabilities.isEmpty, let rootPerms = fallbackRoot?.optStringArray("capabilities"), !rootPerms.isEmpty {
            capabilities = rootPerms
        }
        if capabilities.isEmpty, let rootPerms = fallbackRoot?.optStringArray("permissions"), !rootPerms.isEmpty {
            capabilities = rootPerms
        }

        // Extract scope kinds from `scopes` array of objects (`[{"kind": "all"|"team"|"own", ...}]`) or string array
        var rawScopeKinds: [String] = []
        if let scopeDicts = obj["scopes"] as? [[String: Any]] {
            for s in scopeDicts {
                let kind = s.optString("kind").lowercased()
                if !kind.isEmpty { rawScopeKinds.append(kind) }
            }
        } else {
            rawScopeKinds = obj.optStringArray("scopes").map { $0.lowercased() }
        }

        var allowedWorkerScopes = obj.optStringArray("allowed_worker_scopes")
        if allowedWorkerScopes.isEmpty {
            if rawScopeKinds.contains("all") {
                allowedWorkerScopes = ["all", "team", "managed", "recruited"]
            } else if rawScopeKinds.contains("team") {
                allowedWorkerScopes = ["team", "managed", "recruited"]
            } else if !rawScopeKinds.isEmpty {
                allowedWorkerScopes = ["managed", "recruited"]
            } else {
                allowedWorkerScopes = ["managed", "recruited", "team", "all"]
            }
        }

        let derivedRole: String = {
            let explicit = obj.optString("role", "role_code")
            if !explicit.isEmpty { return explicit }
            if capabilities.contains("entry_admin") && rawScopeKinds.contains("all") {
                return "ADMIN"
            }
            if rawScopeKinds.contains("team") {
                return "TEAM_LEADER"
            }
            return "USER"
        }()

        let derivedRoleLabel: String = {
            let explicit = obj.optString("role_label", "role_name")
            if !explicit.isEmpty { return explicit }
            switch derivedRole {
            case "ADMIN": return "Quản trị viên"
            case "TEAM_LEADER": return "Quản lý nhóm"
            default: return "Nhân sự"
            }
        }()

        let email = obj.optString("email", defaultValue: fallbackIdentifier.contains("@") ? fallbackIdentifier : "")
        let phone = obj.optString("phone", "sdt")
        let teamName = obj.optString("team_name", "department", "unit_name")

        return MiniBiUser(
            id: id.isEmpty ? username : id,
            username: username,
            fullName: fullName,
            role: derivedRole,
            roleLabel: derivedRoleLabel,
            email: email,
            phone: phone,
            teamName: teamName,
            permissions: capabilities,
            allowedWorkerScopes: allowedWorkerScopes
        )
    }

    private static func extractProvidedFieldValue(_ dict: [String: Any]?, _ keys: String...) -> String {
        guard let dict else { return "" }
        for key in keys {
            guard let raw = dict[key], !(raw is NSNull) else { continue }
            if let s = raw as? String {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty && trimmed.lowercased() != "null" { return trimmed }
            } else if let n = raw as? NSNumber {
                return n.stringValue
            } else if let nested = raw as? [String: Any] {
                let val = nested.optString("value", "display", "label", "status", "name")
                if !val.isEmpty && val.lowercased() != "null" { return val }
            }
        }
        return ""
    }

    public static func parseWorkerItem(_ obj: [String: Any]) -> WorkerItem {
        let empStatusDict = obj["employment_status"] as? [String: Any]
        let employmentStatusRaw = (empStatusDict?.optString("status") ?? obj.optString("employment_status", "trang_thai", "status")).uppercased()
        let trangThaiDisplay: String = {
            switch employmentStatusRaw {
            case "ON": return "Đang làm việc"
            case "OFF": return "Nghỉ việc"
            case "UNCONFIRMED": return "Chưa xác nhận"
            default:
                let raw = obj.optString("trang_thai", "status")
                return raw.isEmpty ? "Đang làm việc" : raw
            }
        }()

        let workerDetailsObj = obj["worker_details"] as? [String: Any]
        let paymentObj = (obj["payment"] as? [String: Any]) ?? (workerDetailsObj?["payment"] as? [String: Any])
        let piiObj = obj["pii"] as? [String: Any]
        let projectObj = obj["project"] as? [String: Any]
        let recruiterObj = obj["recruiter"] as? [String: Any]
        let managerObj = obj["manager"] as? [String: Any]

        let rawEmployeeCode = obj.optString("employee_code", "ma_nld", "worker_code")
        let cleanEmployeeCode = WorkerItem.isUuidLike(rawEmployeeCode) ? "" : rawEmployeeCode
        let entryId = obj.optString("entry_id", "id", "employee_code", "ma_nld", "worker_code")
        let entryVersion = obj.optInt("entry_version", "version", defaultValue: 1)

        let projectName = projectObj?.optString("display", "display_name", "name", "project_id")
            ?? obj.optString("project_display", "du_an", "project_name", "project", "project_id")
        let recruiterName = recruiterObj?.optString("display", "display_name", "name", "recruiter_id")
            ?? obj.optString("recruiter_display", "nguoi_tuyen", "recruiter_name", "recruiter", "recruiter_id")
        let managerName = managerObj?.optString("display", "display_name", "name", "manager_id")
            ?? obj.optString("quan_ly", "manager_name", "manager")

        let phone = Self.extractProvidedFieldValue(workerDetailsObj, "phone", "phone_number", "sdt")
            .ifEmpty(obj.optString("phone_number", "sdt", "so_dien_thoai", "phone"))
        let cccd = Self.extractProvidedFieldValue(workerDetailsObj, "national_id", "so_cccd", "cccd")
            .ifEmpty(piiObj?.optString("national_id", "so_cccd") ?? obj.optString("national_id", "so_cccd", "cccd", "id_number"))
        let birthDate = Self.extractProvidedFieldValue(workerDetailsObj, "date_of_birth", "birth_date", "ngay_sinh")
            .ifEmpty(piiObj?.optString("birth_date", "ngay_sinh") ?? obj.optString("birth_date", "ngay_sinh", "dob"))
        let rawGender = Self.extractProvidedFieldValue(workerDetailsObj, "gender", "gioi_tinh")
            .ifEmpty(obj.optString("gioi_tinh", "gender"))
        let genderDisplay: String = {
            switch rawGender.lowercased() {
            case "male", "nam": return "Nam"
            case "female", "nu", "nữ": return "Nữ"
            default: return rawGender
            }
        }()
        let address = Self.extractProvidedFieldValue(workerDetailsObj, "address", "permanent_address", "que_quan")
            .ifEmpty(piiObj?.optString("permanent_address", "que_quan") ?? obj.optString("permanent_address", "que_quan", "dia_chi", "hometown"))
        let issueDate = Self.extractProvidedFieldValue(workerDetailsObj, "national_id_issued_at", "id_issue_date", "ngay_cap")
            .ifEmpty(piiObj?.optString("id_issue_date", "ngay_cap") ?? obj.optString("id_issue_date", "ngay_cap", "issue_date"))

        let accountNumber = Self.extractProvidedFieldValue(paymentObj, "account_number", "so_tai_khoan")
            .ifEmpty(obj.optString("account_number", "so_tai_khoan", "stk", "bank_account"))
        let bankName = Self.extractProvidedFieldValue(paymentObj, "bank_display", "bank_name", "bank_id", "ngan_hang")
            .ifEmpty(obj.optString("bank_id", "ngan_hang", "bank_name"))
        let accountHolder = Self.extractProvidedFieldValue(paymentObj, "account_holder_name", "chu_tai_khoan")
            .ifEmpty(obj.optString("account_holder_name", "chu_tai_khoan", "bank_holder"))

        var contractUrl = obj.optNullableString("contract_file_url", "hop_dong_url", "contract_file")
        var frontUrl = obj.optNullableString("cccd_front_url", "mat_truoc_cccd_url")
        var backUrl = obj.optNullableString("cccd_back_url", "mat_sau_cccd_url")
        for doc in obj.optObjectArray("documents") {
            let docType = doc.optString("doc_type", "type").uppercased()
            let url = doc.optNullableString("download_url", "file_url", "url", "file_name")
            if let url {
                if docType == "CCCD_FRONT" && frontUrl == nil { frontUrl = url }
                else if docType == "CCCD_BACK" && backUrl == nil { backUrl = url }
                else if (docType == "EMPLOYMENT_CONTRACT" || docType == "SAFETY_CARD") && contractUrl == nil { contractUrl = url }
            }
        }

        let offDate = empStatusDict?.optString("effective_date") ?? ""

        return WorkerItem(
            maNld: entryId,
            employeeCode: cleanEmployeeCode,
            entryVersion: entryVersion,
            hoTen: obj.optString("display_name", "ho_ten", "full_name", "name"),
            duAn: projectName,
            quanLy: managerName,
            nguoiTuyen: recruiterName,
            ngayVaoLam: obj.optString("first_work_date", "ngay_vao_lam", "start_date"),
            ngayNghiViec: obj.optString("ngay_nghi_viec", "resign_date", "off_date").ifEmpty(employmentStatusRaw == "OFF" ? offDate : ""),
            trangThai: trangThaiDisplay,
            sdt: phone,
            soCccd: cccd,
            ngaySinh: birthDate,
            gioiTinh: genderDisplay,
            queQuan: address,
            ngayCap: issueDate,
            nganhNghe: obj.optString("labor_type_display", "labor_type", "nganh_nghe", "job_title"),
            nhom: obj.optString("nhom", "team_name", "team_display"),
            soTaiKhoan: accountNumber,
            nganHang: bankName,
            chuTaiKhoan: accountHolder,
            ghiChu: Self.extractProvidedFieldValue(workerDetailsObj, "note", "ghi_chu").ifEmpty(obj.optString("note", "ghi_chu")),
            lyDoNghi: obj.optString("ly_do_nghi", "resign_reason", "leave_reason"),
            phanLoaiNghi: obj.optString("phan_loai_nghi", "resign_category", "leave_reason_category"),
            contractFileUrl: contractUrl,
            cccdFrontUrl: frontUrl,
            cccdBackUrl: backUrl,
            uploaderCode: cleanEmployeeCode.ifEmpty(obj.optString("uploader_code", "created_by"))
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
