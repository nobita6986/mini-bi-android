import Foundation

// MARK: - Backend HTTP Response & Client
// Mirrors Android's `data/BackendHttpClient.kt` 100%

public struct BackendHttpResponse: @unchecked Sendable {
    public let statusCode: Int
    public let headers: [AnyHashable: Any]
    public let rawData: Data
    public let rawBody: String
    public let jsonObject: [String: Any]?
    public let errorCode: String?
    public let errorMessage: String?

    public var isSuccess: Bool {
        (200...299).contains(statusCode)
    }

    public init(
        statusCode: Int,
        headers: [AnyHashable: Any] = [:],
        rawData: Data = Data(),
        rawBody: String = "",
        jsonObject: [String: Any]? = nil,
        errorCode: String? = nil,
        errorMessage: String? = nil
    ) {
        self.statusCode = statusCode
        self.headers = headers
        self.rawData = rawData
        self.rawBody = rawBody
        self.jsonObject = jsonObject
        self.errorCode = errorCode
        self.errorMessage = errorMessage
    }
}

public final class BackendHttpClient: @unchecked Sendable {
    public static let shared = BackendHttpClient()

    public let cookieStore: KeychainCookieStore
    public let sseSession: URLSession
    private let apiSession: URLSession
    private let presignedSession: URLSession
    private let lock = NSLock()
    private var baseUrlInternal: String
    private let enforceHttps: Bool

    public static let codeMessagesVi: [String: String] = [
        "UNAUTHENTICATED": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "SESSION_EXPIRED": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "ACTOR_NOT_AVAILABLE": "Tài khoản hiện không khả dụng hoặc chưa được kích hoạt.",
        "CSRF_ORIGIN_MISMATCH": "Yêu cầu bị từ chối do không hợp lệ nguồn gốc phiên. Vui lòng tải lại.",
        "INVALID_CREDENTIALS": "Thông tin đăng nhập không hợp lệ.",
        "PENDING_ACCESS": "Tài khoản đang chờ cấp quyền truy cập hệ thống.",
        "ACCOUNT_INACTIVE": "Tài khoản hiện không hoạt động.",
        "FORBIDDEN": "Bạn không có quyền thực hiện thao tác này.",
        "INSUFFICIENT_SCOPE": "Tài khoản không có phạm vi dữ liệu cho thao tác này.",
        "ORIGIN_MISMATCH": "Yêu cầu bị từ chối do sai nguồn Origin.",
        "CSRF_REJECTED": "Yêu cầu bị từ chối do kiểm tra bảo mật Origin/CSRF.",
        "VERSION_CONFLICT": "Dữ liệu đã được cập nhật bởi phiên khác. Vui lòng tải lại.",
        "OPTIMISTIC_LOCK_CONFLICT": "Phiên bản hồ sơ đã thay đổi trên máy chủ. Đã tải lại bản mới nhất.",
        "WORK_REPORT_VERSION_CONFLICT": "Báo cáo đã được cập nhật ở phiên làm việc khác. Đã tải lại phiên bản mới nhất.",
        "WORK_REPORT_DEADLINE_PASSED": "Đã quá hạn nộp báo cáo.",
        "DUPLICATE_CCCD_CONFIRMATION_REQUIRED": "Phát hiện CCCD đã tồn tại trên hệ thống. Vui lòng xác nhận trước khi gửi chính thức.",
        "INVALID_NATIONAL_ID": "Số CCCD phải gồm đúng 12 chữ số hợp lệ.",
        "FILE_TOO_LARGE": "Tệp đính kèm vượt quá dung lượng tối đa 10 MB.",
        "UNSUPPORTED_MEDIA_TYPE": "Định dạng tệp không được hỗ trợ (chỉ nhận JPG, PNG, WEBP hoặc PDF).",
        "LEAVE_SHARED_ACCOUNT_FORBIDDEN": "Tài khoản dùng chung (như tài khoản Marketing chung) không thuộc đối tượng quản lý nghỉ phép cá nhân.",
        "LEAVE_IDEMPOTENCY_KEY_INVALID": "Mã định danh yêu cầu không hợp lệ. Vui lòng thử lại.",
        "LEAVE_IDEMPOTENCY_FINGERPRINT_MISMATCH": "Yêu cầu bị trùng mã phiên cũ với nội dung khác. Vui lòng thử lại.",
        "LEAVE_REASON_INVALID": "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự).",
        "LEAVE_REASON_REQUIRED": "Vui lòng nhập lý do hợp lệ (tối đa 2.000 ký tự).",
        "LEAVE_REQUEST_DAYS_INVALID": "Danh sách ngày xin nghỉ không hợp lệ. Vui lòng chọn từ 1 đến 60 ngày nghỉ hợp lệ.",
        "LEAVE_REQUEST_YEAR_OUT_OF_RANGE": "Ngày xin nghỉ phải thuộc năm nghiệp vụ hiện hành.",
        "LEAVE_REQUEST_DUPLICATE_DATE": "Không được chọn trùng một ngày nhiều lần trong cùng một đơn xin nghỉ.",
        "LEAVE_REQUEST_OVERLAP": "Ngày hoặc buổi xin nghỉ bị trùng với một đơn đang chờ duyệt hoặc đã được duyệt.",
        "LEAVE_INSUFFICIENT_BALANCE": "Số dư phép khả dụng tại thời điểm ngày nghỉ không đủ để đáp ứng đơn xin nghỉ có lương.",
        "LEAVE_REQUEST_NOT_FOUND": "Không tìm thấy đơn xin nghỉ phép.",
        "LEAVE_REQUEST_OWNERSHIP_FORBIDDEN": "Bạn chỉ được phép rút đơn xin nghỉ phép của chính mình.",
        "LEAVE_ROW_VERSION_CONFLICT": "Dữ liệu vừa được cập nhật bởi thao tác khác. Hệ thống đã làm mới, vui lòng kiểm tra và thử lại.",
        "LEAVE_INVALID_STATE_TRANSITION": "Trạng thái đơn hiện tại không cho phép thực hiện thao tác này.",
        "LEAVE_APPROVAL_FORBIDDEN": "Bạn không có thẩm quyền duyệt hoặc xử lý đơn nghỉ phép này.",
        "LEAVE_SELF_APPROVAL_FORBIDDEN": "Người duyệt không được tự duyệt hoặc tự hủy đơn nghỉ phép của chính mình.",
        "LEAVE_DECISION_INVALID": "Quyết định xử lý đơn không hợp lệ.",
        "LEAVE_FILTER_INVALID": "Bộ lọc truy vấn không hợp lệ.",
        "LEAVE_ADMIN_FORBIDDEN": "Bạn không có quyền truy cập khu vực cấu hình quản trị nghỉ phép.",
        "LEAVE_POLICY_ADMIN_FORBIDDEN": "Chỉ Admin hoặc Ban Giám đốc mới có quyền cập nhật chính sách nghỉ phép.",
        "LEAVE_POLICY_EXPIRY_INVALID": "Ngày/tháng hết hạn phép tồn năm trước không hợp lệ (tháng 1–12, ngày 1–31).",
        "LEAVE_TARGET_USER_NOT_FOUND": "Không tìm thấy tài khoản nhân sự cần cấu hình.",
        "LEAVE_PROFILE_INVALID": "Thông tin cấu hình hồ sơ nghỉ phép không hợp lệ.",
        "LEAVE_PROFILE_WORK_START_DATE_REQUIRED": "Nhân sự Thử việc hoặc Chính thức bắt buộc phải có Ngày bắt đầu làm việc.",
        "LEAVE_PROFILE_OFFICIAL_DATE_INVALID": "Ngày lên chính thức không hợp lệ (phải từ ngày bắt đầu làm việc trở đi).",
        "LEAVE_SENIORITY_CONFIG_FORBIDDEN": "Chỉ Admin hoặc Ban Giám đốc mới được cấu hình ngày và số phép tăng thâm niên tiếp theo.",
        "LEAVE_DEPARTMENT_APPROVER_ADMIN_FORBIDDEN": "Chỉ Admin hoặc Ban Giám đốc mới được cấu hình người duyệt bộ phận Marketing / IT / Kế toán.",
        "LEAVE_UNIT_KIND_INVALID": "Đơn vị / bộ phận không hợp lệ.",
        "LEAVE_APPROVER_USER_NOT_FOUND": "Không tìm thấy tài khoản người duyệt hoặc tài khoản đang bị vô hiệu hóa.",
        "LEAVE_MODULE_GRANT_ADMIN_FORBIDDEN": "Chỉ Full Admin mới có quyền cấp hoặc thu hồi quyền nội bộ phân hệ nghỉ phép.",
        "LEAVE_MODULE_GRANT_INVALID": "Thông tin cấp quyền nội bộ không hợp lệ.",
        "LEAVE_MODULE_GRANT_NOT_FOUND": "Không tìm thấy bản ghi quyền nội bộ đang hiệu lực để thu hồi.",
        "LEAVE_YEAR_INVALID": "Năm nghiệp vụ nghỉ phép không hợp lệ.",
        "LEAVE_OPENING_ROWS_INVALID": "Danh sách dòng số dư đầu kỳ không hợp lệ (tối đa 300 dòng mỗi lần áp dụng).",
        "LEAVE_CARRY_OVER_EXPIRY_NOT_CONFIGURED": "Chưa cấu hình ngày hết hạn phép chuyển năm. Vui lòng cấu hình Chính sách nghỉ phép trước khi nhập phép tồn năm trước.",
        "LEAVE_OPENING_BATCH_VALIDATION_FAILED": "Lô số dư đầu kỳ còn dòng lỗi. Vui lòng xem trước và xử lý hết lỗi trước khi áp dụng.",
        "LEAVE_REPORT_FORBIDDEN": "Bạn không có quyền xem hoặc xuất Báo cáo chốt công & phép tháng.",
        "LEAVE_REPORT_YEAR_MONTH_INVALID": "Kỳ báo cáo tháng không hợp lệ (định dạng YYYY-MM).",
        "LEAVE_REPORT_EXPORT_LIMIT_EXCEEDED": "Số lượng dòng báo cáo vượt quá giới hạn xuất một lần (5.000 nhân sự). Vui lòng lọc theo bộ phận hoặc từ khóa.",
        "LEAVE_REPORT_EXPORT_INCOMPLETE": "Dữ liệu báo cáo thay đổi trong lúc xuất. Vui lòng thử xuất lại.",
        "LEAVE_UNAVAILABLE": "Hệ thống quản lý nghỉ phép tạm thời không khả dụng. Vui lòng thử lại sau."
    ]

    public init(
        baseUrl: String? = nil,
        cookieStore: KeychainCookieStore = KeychainCookieStore(),
        enforceHttps: Bool = AppEnvironmentConfig.current.enforceHttps
    ) {
        let defaultUrl = baseUrl ?? AppEnvironmentConfig.current.defaultBaseUrl
        self.cookieStore = cookieStore
        self.enforceHttps = enforceHttps
        self.baseUrlInternal = Self.normalizeBaseUrl(defaultUrl, enforceHttps: enforceHttps)
            ?? AppEnvironmentConfig.current.defaultBaseUrl

        let apiConfig = URLSessionConfiguration.ephemeral
        apiConfig.timeoutIntervalForRequest = 30
        apiConfig.timeoutIntervalForResource = 60
        apiConfig.httpShouldSetCookies = false
        self.apiSession = URLSession(configuration: apiConfig)

        let sseConfig = URLSessionConfiguration.ephemeral
        sseConfig.timeoutIntervalForRequest = 300
        sseConfig.timeoutIntervalForResource = 3600
        sseConfig.httpShouldSetCookies = false
        self.sseSession = URLSession(configuration: sseConfig)

        let presignedConfig = URLSessionConfiguration.ephemeral
        presignedConfig.timeoutIntervalForRequest = 60
        presignedConfig.timeoutIntervalForResource = 120
        presignedConfig.httpShouldSetCookies = false
        self.presignedSession = URLSession(configuration: presignedConfig)
    }

    public var baseUrl: String {
        lock.lock()
        defer { lock.unlock() }
        return baseUrlInternal
    }

    @discardableResult
    public func setBaseUrl(_ rawUrl: String) -> Bool {
        guard let normalized = Self.normalizeBaseUrl(rawUrl, enforceHttps: enforceHttps) else {
            return false
        }
        lock.lock()
        baseUrlInternal = normalized
        lock.unlock()
        return true
    }

    // MARK: - URL Normalization & Origin Extraction

    public static func normalizeBaseUrl(_ rawInput: String, enforceHttps: Bool = true) -> String? {
        var trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if !trimmed.contains("://") {
            trimmed = "https://\(trimmed)"
        }
        guard let components = URLComponents(string: trimmed),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased(),
              !host.isEmpty
        else {
            return nil
        }

        if enforceHttps && scheme != "https" {
            return nil
        }
        if scheme != "https" && scheme != "http" {
            return nil
        }

        var result = "\(scheme)://\(host)"
        if let port = components.port {
            let isDefault = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
            if !isDefault {
                result += ":\(port)"
            }
        }

        var path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if path.lowercased() == "api" {
            path = ""
        } else if path.lowercased().hasSuffix("/api") {
            path = String(path.dropLast(4)).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
        if !path.isEmpty {
            result += "/\(path)"
        }
        return result
    }

    public static func extractOrigin(from url: URL) -> String? {
        guard let scheme = url.scheme?.lowercased(),
              let host = url.host?.lowercased(),
              !host.isEmpty
        else {
            return nil
        }
        if let port = url.port {
            let isDefault = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
            if !isDefault {
                return "\(scheme)://\(host):\(port)"
            }
        }
        return "\(scheme)://\(host)"
    }

    // MARK: - Core JSON / Raw Request

    public func request(
        method: String = "GET",
        path: String,
        queryItems: [URLQueryItem] = [],
        jsonBody: Any? = nil,
        additionalHeaders: [String: String] = [:]
    ) async -> BackendHttpResponse {
        guard let url = buildUrl(path: path, queryItems: queryItems) else {
            return BackendHttpResponse(
                statusCode: 0,
                errorCode: "INVALID_URL",
                errorMessage: "Đường dẫn máy chủ không hợp lệ."
            )
        }

        var req = URLRequest(url: url)
        let upperMethod = method.uppercased()
        req.httpMethod = upperMethod
        req.setValue("application/json", forHTTPHeaderField: "Accept")

        if let cookieHeader = cookieStore.cookieHeader(for: url) {
            req.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }

        if ["POST", "PUT", "PATCH", "DELETE"].contains(upperMethod),
           let origin = Self.extractOrigin(from: url) {
            req.setValue(origin, forHTTPHeaderField: "Origin")
        }

        if let jsonBody {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: jsonBody, options: [])
        }

        for (k, v) in additionalHeaders {
            req.setValue(v, forHTTPHeaderField: k)
        }

        return await executeRequest(req, session: apiSession, saveCookies: true)
    }

    // MARK: - Multipart File Upload (`POST /api/workers/{ma_nld}/contract-file`, etc.)

    public func uploadMultipart(
        path: String,
        fieldName: String = "file",
        fileName: String,
        mimeType: String,
        fileData: Data,
        extraFields: [String: String] = [:]
    ) async -> BackendHttpResponse {
        guard let url = buildUrl(path: path, queryItems: []) else {
            return BackendHttpResponse(
                statusCode: 0,
                errorCode: "INVALID_URL",
                errorMessage: "Đường dẫn tải tệp không hợp lệ."
            )
        }

        let boundary = "Boundary-HRPBI-\(UUID().uuidString)"
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        if let origin = Self.extractOrigin(from: url) {
            req.setValue(origin, forHTTPHeaderField: "Origin")
        }
        if let cookieHeader = cookieStore.cookieHeader(for: url) {
            req.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }

        var body = Data()
        for (k, v) in extraFields {
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"\(k)\"\r\n\r\n".utf8))
            body.append(Data("\(v)\r\n".utf8))
        }
        let safeFileName = fileName.replacingOccurrences(of: "\"", with: "_")
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(safeFileName)\"\r\n".utf8))
        body.append(Data("Content-Type: \(mimeType)\r\n\r\n".utf8))
        body.append(fileData)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        req.httpBody = body

        return await executeRequest(req, session: apiSession, saveCookies: true)
    }

    // MARK: - Presigned R2 PUT Upload (Cookie-free & Origin-free)

    public func putPresignedUrl(
        uploadUrl: String,
        fileData: Data,
        contentType: String,
        additionalHeaders: [String: String] = [:]
    ) async -> BackendHttpResponse {
        guard let url = URL(string: uploadUrl) else {
            return BackendHttpResponse(
                statusCode: 0,
                errorCode: "INVALID_PRESIGNED_URL",
                errorMessage: "Đường dẫn tải tệp lên kho lưu trữ không hợp lệ."
            )
        }

        var req = URLRequest(url: url)
        req.httpMethod = "PUT"
        req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        for (k, v) in additionalHeaders {
            req.setValue(v, forHTTPHeaderField: k)
        }
        req.httpBody = fileData

        return await executeRequest(req, session: presignedSession, saveCookies: false)
    }

    // MARK: - Real-time SSE Stream Request (`GET /api/notifications/stream`)

    public func makeSseRequest(path: String = "/api/notifications/stream") throws -> URLRequest {
        guard let url = buildUrl(path: path, queryItems: []) else {
            throw URLError(.badURL)
        }
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        if let cookieHeader = cookieStore.cookieHeader(for: url) {
            req.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }
        return req
    }

    // MARK: - Internal Helpers

    private func buildUrl(path: String, queryItems: [URLQueryItem]) -> URL? {
        let currentBase = baseUrl
        let cleanPath = path.hasPrefix("/") ? path : "/\(path)"
        let fullStr = (path.hasPrefix("https://") || path.hasPrefix("http://"))
            ? path
            : "\(currentBase)\(cleanPath)"
        guard var components = URLComponents(string: fullStr) else {
            return nil
        }
        if !queryItems.isEmpty {
            var existing = components.queryItems ?? []
            existing.append(contentsOf: queryItems)
            components.queryItems = existing
        }
        return components.url
    }

    private func executeRequest(
        _ request: URLRequest,
        session: URLSession,
        saveCookies: Bool
    ) async -> BackendHttpResponse {
        do {
            let (data, urlResponse) = try await session.data(for: request)
            guard let httpResp = urlResponse as? HTTPURLResponse else {
                return BackendHttpResponse(
                    statusCode: 0,
                    errorCode: "NON_HTTP_RESPONSE",
                    errorMessage: "Phản hồi từ máy chủ không hợp lệ."
                )
            }

            if saveCookies, let url = request.url {
                var stringHeaders: [String: String] = [:]
                for (k, v) in httpResp.allHeaderFields {
                    if let ks = k as? String, let vs = v as? String {
                        stringHeaders[ks] = vs
                    }
                }
                cookieStore.saveFromResponseHeaders(stringHeaders, for: url)
            }

            let rawBody = String(data: data, encoding: .utf8) ?? ""
            var parsedDict: [String: Any]?
            if !data.isEmpty,
               let json = try? JSONSerialization.jsonObject(with: data, options: []) {
                if let dict = json as? [String: Any] {
                    parsedDict = dict
                } else if let arr = json as? [[String: Any]] {
                    parsedDict = ["items": arr]
                }
            }

            let isSuccess = (200...299).contains(httpResp.statusCode)
            let (errCode, errMsg) = isSuccess
                ? (nil, nil)
                : Self.extractErrorDetails(statusCode: httpResp.statusCode, jsonObject: parsedDict, rawBody: rawBody)

            return BackendHttpResponse(
                statusCode: httpResp.statusCode,
                headers: httpResp.allHeaderFields,
                rawData: data,
                rawBody: rawBody,
                jsonObject: parsedDict,
                errorCode: errCode,
                errorMessage: errMsg
            )
        } catch {
            return BackendHttpResponse(
                statusCode: 0,
                errorCode: "NETWORK_ERROR",
                errorMessage: "Không thể kết nối máy chủ (\(error.localizedDescription))."
            )
        }
    }

    private static func extractErrorDetails(
        statusCode: Int,
        jsonObject: [String: Any]?,
        rawBody: String
    ) -> (code: String?, message: String?) {
        var code: String?
        var serverMsg: String?

        if let jsonObject {
            if let errDict = jsonObject["error"] as? [String: Any] {
                code = errDict.optNullableString("code", "error_code")
                    ?? jsonObject.optNullableString("code", "error_code")
                serverMsg = errDict.optNullableString("message", "detail")
                    ?? jsonObject.optNullableString("message", "detail")
            } else {
                code = jsonObject.optNullableString("code", "error_code")
                serverMsg = jsonObject.optNullableString("message", "detail")
                    ?? (jsonObject["error"] as? String)
            }
        }

        let normalizedCode = code?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if let normalizedCode, let mapped = codeMessagesVi[normalizedCode] {
            return (normalizedCode, mapped)
        }
        if let serverMsg, !serverMsg.isEmpty {
            return (normalizedCode, serverMsg)
        }
        return (normalizedCode, "Yêu cầu thất bại (HTTP \(statusCode)).")
    }
}
