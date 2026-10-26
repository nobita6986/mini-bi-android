import Foundation

public enum BackendFailureKind: String, Equatable, Sendable {
    case unauthenticated = "UNAUTHENTICATED"
    case pendingAccess = "PENDING_ACCESS"
    case forbidden = "FORBIDDEN"
    case conflict = "CONFLICT"
    case validationError = "VALIDATION_ERROR"
    case invalidRequest = "INVALID_REQUEST"
    case notFound = "NOT_FOUND"
    case serverUnavailable = "SERVER_UNAVAILABLE"
    case network = "NETWORK"
    case unknown = "UNKNOWN"
}

public struct BackendApiException: Error, LocalizedError, Equatable, Sendable {
    public let kind: BackendFailureKind
    public let statusCode: Int
    public let backendCode: String?
    public let message: String

    public init(
        kind: BackendFailureKind,
        statusCode: Int = 0,
        backendCode: String? = nil,
        message: String
    ) {
        self.kind = kind
        self.statusCode = statusCode
        self.backendCode = backendCode
        self.message = message
    }

    public var errorDescription: String? {
        message
    }

    public var isRetryable: Bool {
        kind == .network || kind == .serverUnavailable || statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504
    }
}

public struct HttpRawResponse: Sendable {
    public let statusCode: Int
    public let headers: [String: String]
    public let data: Data

    public init(statusCode: Int, headers: [String: String] = [:], data: Data = Data()) {
        self.statusCode = statusCode
        self.headers = headers
        self.data = data
    }
}

public final class BackendHttpClient: @unchecked Sendable {
    public typealias TransportHandler = @Sendable (URLRequest) async throws -> HttpRawResponse

    public let cookieStore: KeychainCookieStore
    private let session: URLSession
    private let signedUploadSession: URLSession
    private let lock = NSLock()
    private var baseUrlInternal: String
    private let allowHttpForDebug: Bool
    public var transportOverride: TransportHandler?

    public init(
        baseUrl: String,
        cookieStore: KeychainCookieStore = KeychainCookieStore(),
        allowHttpForDebug: Bool = false,
        transportOverride: TransportHandler? = nil
    ) {
        self.cookieStore = cookieStore
        self.allowHttpForDebug = allowHttpForDebug
        self.baseUrlInternal = (try? Self.normalizeBaseUrl(baseUrl, allowHttpForDebug: allowHttpForDebug))
            ?? "https://bi.hrpartner.vn"
        self.transportOverride = transportOverride

        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        config.httpShouldSetCookies = false
        self.session = URLSession(configuration: config)

        let cleanConfig = URLSessionConfiguration.ephemeral
        cleanConfig.timeoutIntervalForRequest = 45
        cleanConfig.timeoutIntervalForResource = 90
        cleanConfig.httpShouldSetCookies = false
        self.signedUploadSession = URLSession(configuration: cleanConfig)
    }

    public var baseUrl: String {
        lock.lock()
        defer { lock.unlock() }
        return baseUrlInternal
    }

    public func updateBaseUrl(_ rawUrl: String) throws -> String {
        let normalized = try Self.normalizeBaseUrl(rawUrl, allowHttpForDebug: allowHttpForDebug)
        lock.lock()
        baseUrlInternal = normalized
        lock.unlock()
        return normalized
    }

    public func hasSessionCookies() -> Bool {
        cookieStore.hasSessionCookies()
    }

    public func clearSessionCookies() {
        cookieStore.clearAll()
    }

    // MARK: - URL Normalization & Origin

    public static func normalizeBaseUrl(_ rawInput: String, allowHttpForDebug: Bool = false) throws -> String {
        var trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 0,
                backendCode: "EMPTY_URL",
                message: "Địa chỉ máy chủ không được để trống."
            )
        }
        if !trimmed.contains("://") {
            trimmed = "https://\(trimmed)"
        }
        guard let components = URLComponents(string: trimmed),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased(),
              !host.isEmpty
        else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 0,
                backendCode: "INVALID_URL",
                message: "Địa chỉ máy chủ không hợp lệ."
            )
        }
        if scheme != "https" && !(allowHttpForDebug && scheme == "http") {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 0,
                backendCode: "HTTPS_REQUIRED",
                message: "Bắt buộc sử dụng giao thức bảo mật HTTPS."
            )
        }
        var out = "\(scheme)://\(host)"
        if let port = components.port {
            let isDefaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
            if !isDefaultPort {
                out += ":\(port)"
            }
        }
        let cleanPath = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if !cleanPath.isEmpty {
            out += "/\(cleanPath)"
        }
        return out
    }

    public static func extractOrigin(_ baseUrl: String) -> String {
        guard let components = URLComponents(string: baseUrl),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased()
        else {
            return baseUrl
        }
        if let port = components.port {
            let isDefaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
            if !isDefaultPort {
                return "\(scheme)://\(host):\(port)"
            }
        }
        return "\(scheme)://\(host)"
    }

    // MARK: - Error Mapping

    public static let directEntryCodeMessages: [String: String] = [
        "UNAUTHENTICATED": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "SESSION_EXPIRED": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        "INVALID_CREDENTIALS": "Thông tin đăng nhập không hợp lệ.",
        "PENDING_ACCESS": "Tài khoản đang chờ cấp quyền truy cập hệ thống.",
        "ACCOUNT_INACTIVE": "Tài khoản hiện không hoạt động.",
        "FORBIDDEN": "Bạn không có quyền thực hiện thao tác này.",
        "INSUFFICIENT_SCOPE": "Tài khoản không có phạm vi dữ liệu cho thao tác này.",
        "ORIGIN_MISMATCH": "Yêu cầu bị từ chối do sai nguồn Origin.",
        "CSRF_REJECTED": "Yêu cầu bị từ chối do kiểm tra bảo mật Origin/CSRF.",
        "VERSION_CONFLICT": "Dữ liệu đã được cập nhật bởi phiên khác. Vui lòng tải lại.",
        "OPTIMISTIC_LOCK_CONFLICT": "Phiên bản hồ sơ đã thay đổi trên máy chủ. Đã tải lại bản mới nhất.",
        "WORK_REPORT_VERSION_CONFLICT": "Dữ liệu báo cáo đã thay đổi trên máy chủ (xung đột phiên bản). Vui lòng tải lại và thử lại.",
        "WORK_REPORT_DEADLINE_PASSED": "Đã quá hạn nộp báo cáo.",
        "DUPLICATE_CCCD_CONFIRMATION_REQUIRED": "Phát hiện CCCD đã tồn tại trên hệ thống. Vui lòng xác nhận trước khi gửi chính thức.",
        "INVALID_NATIONAL_ID": "Số CCCD phải gồm đúng 12 chữ số hợp lệ.",
        "FILE_TOO_LARGE": "Tệp đính kèm vượt quá dung lượng tối đa 10 MB.",
        "UNSUPPORTED_MEDIA_TYPE": "Định dạng tệp không được hỗ trợ (chỉ nhận JPG, PNG, WEBP hoặc PDF).",
        "STAGE_EXPIRED": "Tệp đính kèm tạm đã hết hạn. Vui lòng chọn lại tệp.",
        "TIME_TOKEN_EXPIRED": "Mốc thời gian máy chủ đã hết hạn. Hệ thống đang đồng bộ lại.",
        "TIME_ANCHOR_INVALID": "Mốc thời gian xác thực không hợp lệ."
    ]

    public static func mapHttpFailure(
        statusCode: Int,
        rawBody: String,
        fallbackMessage: String = ""
    ) -> BackendApiException {
        var backendCode: String? = nil
        var serverMessage: String? = nil

        if let data = rawBody.data(using: .utf8),
           let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            if let errObj = json["error"] as? [String: Any] {
                backendCode = errObj.optCleanString("code") ?? json.optCleanString("code")
                serverMessage = errObj.optCleanString("message")
                    ?? errObj.optCleanString("detail")
                    ?? json.optCleanString("message")
            } else {
                backendCode = json.optCleanString("code") ?? json.optCleanString("error_code")
                serverMessage = json.optCleanString("message")
                    ?? json.optCleanString("detail")
                    ?? (json["error"] as? String)
            }
        }

        let normCode = backendCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let mappedVi = normCode.flatMap { directEntryCodeMessages[$0] }

        let kind: BackendFailureKind
        switch statusCode {
        case 401:
            kind = .unauthenticated
        case 403:
            if normCode == "PENDING_ACCESS" || normCode == "ACCOUNT_INACTIVE" {
                kind = .pendingAccess
            } else {
                kind = .forbidden
            }
        case 404:
            kind = .notFound
        case 409:
            kind = .conflict
        case 422:
            kind = .validationError
        case 400, 413, 415:
            kind = .invalidRequest
        case 500...599:
            kind = .serverUnavailable
        default:
            kind = .unknown
        }

        let finalMessage: String
        if let mapped = mappedVi, !mapped.isEmpty {
            finalMessage = mapped
        } else if let srv = serverMessage?.trimmingCharacters(in: .whitespacesAndNewlines), !srv.isEmpty {
            finalMessage = srv
        } else if !fallbackMessage.isEmpty {
            finalMessage = fallbackMessage
        } else {
            switch kind {
            case .unauthenticated:
                finalMessage = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
            case .pendingAccess:
                finalMessage = "Tài khoản đang chờ cấp quyền truy cập."
            case .forbidden:
                finalMessage = "Bạn không có quyền thực hiện thao tác này."
            case .conflict:
                finalMessage = "Dữ liệu bị xung đột phiên bản trên máy chủ. Vui lòng tải lại."
            case .validationError, .invalidRequest:
                finalMessage = "Dữ liệu gửi lên không hợp lệ (HTTP \(statusCode))."
            case .notFound:
                finalMessage = "Không tìm thấy dữ liệu yêu cầu trên máy chủ."
            case .serverUnavailable:
                finalMessage = "Máy chủ đang bận hoặc bảo trì (HTTP \(statusCode))."
            default:
                finalMessage = "Lỗi kết nối máy chủ (HTTP \(statusCode))."
            }
        }

        return BackendApiException(
            kind: kind,
            statusCode: statusCode,
            backendCode: normCode,
            message: finalMessage
        )
    }

    // MARK: - HTTP Requests

    private func buildUrl(path: String, queryParams: [(String, String?)] = []) throws -> URL {
        let currentBase = baseUrl
        let cleanPath = path.hasPrefix("/") ? path : "/\(path)"
        let fullString = path.hasPrefix("http://") || path.hasPrefix("https://")
            ? path
            : "\(currentBase)\(cleanPath)"
        guard var components = URLComponents(string: fullString) else {
            throw BackendApiException(kind: .invalidRequest, statusCode: 0, backendCode: "INVALID_URL", message: "Đường dẫn API không hợp lệ.")
        }
        let nonEmptyParams = queryParams.compactMap { (k, v) -> URLQueryItem? in
            guard let v = v?.trimmingCharacters(in: .whitespacesAndNewlines), !v.isEmpty else { return nil }
            return URLQueryItem(name: k, value: v)
        }
        if !nonEmptyParams.isEmpty {
            var existing = components.queryItems ?? []
            existing.append(contentsOf: nonEmptyParams)
            components.queryItems = existing
        }
        guard let url = components.url else {
            throw BackendApiException(kind: .invalidRequest, statusCode: 0, backendCode: "INVALID_URL", message: "Không thể khởi tạo URL.")
        }
        return url
    }

    private func execute(_ request: URLRequest, saveCookies: Bool = true) async throws -> HttpRawResponse {
        if let override = transportOverride {
            let resp = try await override(request)
            if saveCookies, let url = request.url {
                cookieStore.saveFromResponseHeaders(resp.headers, for: url)
            }
            return resp
        }

        do {
            let (data, urlResponse) = try await session.data(for: request)
            guard let httpResp = urlResponse as? HTTPURLResponse else {
                throw BackendApiException(
                    kind: .network,
                    statusCode: 0,
                    backendCode: "NON_HTTP_RESPONSE",
                    message: "Phản hồi từ máy chủ không hợp lệ."
                )
            }
            var headers: [String: String] = [:]
            for (k, v) in httpResp.allHeaderFields {
                if let ks = k as? String, let vs = v as? String {
                    headers[ks] = vs
                }
            }
            if saveCookies, let url = request.url {
                cookieStore.saveFromResponseHeaders(headers, for: url)
            }
            return HttpRawResponse(statusCode: httpResp.statusCode, headers: headers, data: data)
        } catch let apiEx as BackendApiException {
            throw apiEx
        } catch {
            throw BackendApiException(
                kind: .network,
                statusCode: 0,
                backendCode: "NETWORK_IO_ERROR",
                message: "Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối mạng."
            )
        }
    }

    public func requestJson(
        method: String = "GET",
        path: String,
        queryParams: [(String, String?)] = [],
        jsonBody: Any? = nil,
        headers: [String: String] = [:]
    ) async throws -> [String: Any] {
        let url = try buildUrl(path: path, queryParams: queryParams)
        var req = URLRequest(url: url)
        let upperMethod = method.uppercased()
        req.httpMethod = upperMethod
        req.setValue("application/json", forHTTPHeaderField: "Accept")

        if let cookieHeader = cookieStore.cookieHeader(for: url) {
            req.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }

        if ["POST", "PUT", "PATCH", "DELETE"].contains(upperMethod) {
            req.setValue(Self.extractOrigin(baseUrl), forHTTPHeaderField: "Origin")
        }

        if let jsonBody = jsonBody {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: jsonBody, options: [])
        } else if ["POST", "PUT", "PATCH"].contains(upperMethod) {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = Data("{}".utf8)
        }

        for (k, v) in headers {
            req.setValue(v, forHTTPHeaderField: k)
        }

        let response = try await execute(req)
        let rawBody = String(data: response.data, encoding: .utf8) ?? ""
        if !(200...299).contains(response.statusCode) {
            throw Self.mapHttpFailure(statusCode: response.statusCode, rawBody: rawBody)
        }
        if response.data.isEmpty {
            return [:]
        }
        if let dict = (try? JSONSerialization.jsonObject(with: response.data)) as? [String: Any] {
            return dict
        }
        if let arr = (try? JSONSerialization.jsonObject(with: response.data)) as? [Any] {
            return ["items": arr]
        }
        return [:]
    }

    public func requestRawBytes(
        method: String = "GET",
        path: String,
        queryParams: [(String, String?)] = [],
        headers: [String: String] = [:]
    ) async throws -> (data: Data, contentType: String?, contentDisposition: String?) {
        let url = try buildUrl(path: path, queryParams: queryParams)
        var req = URLRequest(url: url)
        req.httpMethod = method.uppercased()
        if let cookieHeader = cookieStore.cookieHeader(for: url) {
            req.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }
        for (k, v) in headers {
            req.setValue(v, forHTTPHeaderField: k)
        }
        let response = try await execute(req)
        if !(200...299).contains(response.statusCode) {
            let rawText = String(data: response.data, encoding: .utf8) ?? ""
            throw Self.mapHttpFailure(statusCode: response.statusCode, rawBody: rawText)
        }
        let ct = response.headers.first { $0.key.caseInsensitiveCompare("Content-Type") == .orderedSame }?.value
        let cd = response.headers.first { $0.key.caseInsensitiveCompare("Content-Disposition") == .orderedSame }?.value
        return (response.data, ct, cd)
    }

    public func postMultipartFile(
        path: String,
        fieldName: String = "file",
        fileName: String,
        mimeType: String,
        fileBytes: Data,
        extraFields: [String: String] = [:],
        headers: [String: String] = [:]
    ) async throws -> [String: Any] {
        let url = try buildUrl(path: path)
        let boundary = "Boundary-HRPBI-\(UUID().uuidString)"
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.setValue(Self.extractOrigin(baseUrl), forHTTPHeaderField: "Origin")
        if let cookieHeader = cookieStore.cookieHeader(for: url) {
            req.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }
        for (k, v) in headers {
            req.setValue(v, forHTTPHeaderField: k)
        }

        var body = Data()
        for (k, v) in extraFields {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(k)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(v)\r\n".data(using: .utf8)!)
        }
        let safeName = fileName.replacingOccurrences(of: "\"", with: "_")
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(safeName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileBytes)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        let response = try await execute(req)
        let rawBody = String(data: response.data, encoding: .utf8) ?? ""
        if !(200...299).contains(response.statusCode) {
            throw Self.mapHttpFailure(statusCode: response.statusCode, rawBody: rawBody)
        }
        if let dict = (try? JSONSerialization.jsonObject(with: response.data)) as? [String: Any] {
            return dict
        }
        return [:]
    }

    public func putBinaryToSignedUrl(
        uploadUrl: String,
        mimeType: String,
        bytes: Data,
        requiredHeaders: [String: String] = [:]
    ) async throws {
        guard let url = URL(string: uploadUrl) else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 0,
                backendCode: "INVALID_SIGNED_URL",
                message: "Đường dẫn tải tệp lên kho lưu trữ không hợp lệ."
            )
        }
        var req = URLRequest(url: url)
        req.httpMethod = "PUT"
        req.setValue(mimeType, forHTTPHeaderField: "Content-Type")
        for (k, v) in requiredHeaders {
            req.setValue(v, forHTTPHeaderField: k)
        }
        req.httpBody = bytes

        if let override = transportOverride {
            let resp = try await override(req)
            if !(200...299).contains(resp.statusCode) {
                throw BackendApiException(
                    kind: .serverUnavailable,
                    statusCode: resp.statusCode,
                    backendCode: "R2_PUT_FAILED",
                    message: "Không thể tải tệp đính kèm lên kho lưu trữ (HTTP \(resp.statusCode))."
                )
            }
            return
        }

        do {
            let (_, urlResponse) = try await signedUploadSession.data(for: req)
            let code = (urlResponse as? HTTPURLResponse)?.statusCode ?? 0
            if !(200...299).contains(code) {
                throw BackendApiException(
                    kind: .serverUnavailable,
                    statusCode: code,
                    backendCode: "R2_PUT_FAILED",
                    message: "Không thể tải tệp đính kèm lên kho lưu trữ (HTTP \(code))."
                )
            }
        } catch let apiEx as BackendApiException {
            throw apiEx
        } catch {
            throw BackendApiException(
                kind: .network,
                statusCode: 0,
                backendCode: "R2_NETWORK_ERROR",
                message: "Lỗi kết nối mạng khi tải tệp lên kho lưu trữ."
            )
        }
    }
}
