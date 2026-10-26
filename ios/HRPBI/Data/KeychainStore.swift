import Foundation
import Security

// MARK: - iOS Keychain Store & Persistent Cookie Store
// Mirrors Android's `EncryptedPersistentCookieJar.kt` and encrypted preferences

public final class KeychainStore: @unchecked Sendable {
    public static let shared = KeychainStore()

    private let serviceName: String
    private let lock = NSLock()
    private var memoryFallback: [String: Data] = [:]

    public init(serviceName: String = "com.aistudio.minibi.hrpbia.keychain") {
        self.serviceName = serviceName
    }

    public func setData(_ data: Data, forKey key: String) {
        lock.lock()
        defer { lock.unlock() }

        memoryFallback[key] = data

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        let attributesToUpdate: [String: Any] = [
            kSecValueData as String: data
        ]

        let status = SecItemUpdate(query as CFDictionary, attributesToUpdate as CFDictionary)
        if status == errSecItemNotFound {
            var addQuery = query
            addQuery[kSecValueData as String] = data
            addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            _ = SecItemAdd(addQuery as CFDictionary, nil)
        }
    }

    public func getData(forKey key: String) -> Data? {
        lock.lock()
        defer { lock.unlock() }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data {
            memoryFallback[key] = data
            return data
        }
        return memoryFallback[key]
    }

    public func setString(_ value: String, forKey key: String) {
        if let data = value.data(using: .utf8) {
            setData(data, forKey: key)
        }
    }

    public func getString(forKey key: String) -> String? {
        guard let data = getData(forKey: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    public func remove(forKey key: String) {
        lock.lock()
        defer { lock.unlock() }

        memoryFallback.removeValue(forKey: key)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        _ = SecItemDelete(query as CFDictionary)
    }

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }

        memoryFallback.removeAll()
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName
        ]
        _ = SecItemDelete(query as CFDictionary)
    }
}

// MARK: - Keychain Persistent Cookie Store

public struct PersistedCookieRecord: Codable, Equatable, Sendable {
    public let name: String
    public let value: String
    public let domain: String
    public let path: String
    public let expiresAtEpochMs: Int64
    public let secure: Bool
    public let httpOnly: Bool
    public let hostOnly: Bool

    public var identityKey: String {
        "\(domain.lowercased())|\(path)|\(name)"
    }

    public func isExpired(nowMs: Int64) -> Bool {
        nowMs >= expiresAtEpochMs
    }
}

public final class KeychainCookieStore: @unchecked Sendable {
    private static let storageKey = "persisted_session_cookies_v1"
    private static let sessionFallbackTtlMs: Int64 = 30 * 24 * 60 * 60 * 1000

    private let keychain: KeychainStore
    private let lock = NSLock()
    private var cookieCache: [String: PersistedCookieRecord] = [:]

    public init(keychain: KeychainStore = .shared) {
        self.keychain = keychain
        loadFromKeychain()
    }

    private func loadFromKeychain() {
        lock.lock()
        defer { lock.unlock() }

        guard let data = keychain.getData(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode([PersistedCookieRecord].self, from: data)
        else { return }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000.0)
        var dirty = false
        for record in decoded {
            if !record.isExpired(nowMs: nowMs) {
                cookieCache[record.identityKey] = record
            } else {
                dirty = true
            }
        }
        if dirty {
            persistLocked()
        }
    }

    private func persistLocked() {
        let records = Array(cookieCache.values)
        if let data = try? JSONEncoder().encode(records) {
            keychain.setData(data, forKey: Self.storageKey)
        }
    }

    public func saveCookies(
        _ cookies: [HTTPCookie],
        for url: URL,
        nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) {
        lock.lock()
        defer { lock.unlock() }

        let defaultHost = (url.host ?? "").lowercased()
        for cookie in cookies {
            let rawDomain = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).lowercased()
            let domain = rawDomain.isEmpty ? defaultHost : rawDomain
            let hostOnly = !cookie.domain.hasPrefix(".")
            let path = cookie.path.isEmpty ? "/" : cookie.path

            let expiresMs: Int64
            if let expDate = cookie.expiresDate {
                expiresMs = Int64(expDate.timeIntervalSince1970 * 1000.0)
            } else {
                expiresMs = nowMs + Self.sessionFallbackTtlMs
            }

            let record = PersistedCookieRecord(
                name: cookie.name,
                value: cookie.value,
                domain: domain,
                path: path,
                expiresAtEpochMs: expiresMs,
                secure: cookie.isSecure,
                httpOnly: cookie.isHTTPOnly,
                hostOnly: hostOnly
            )

            if record.isExpired(nowMs: nowMs) || cookie.value.isEmpty {
                cookieCache.removeValue(forKey: record.identityKey)
            } else {
                cookieCache[record.identityKey] = record
            }
        }
        persistLocked()
    }

    public func saveFromResponseHeaders(
        _ headerFields: [String: String],
        for url: URL,
        nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) {
        let cookies = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
        if !cookies.isEmpty {
            saveCookies(cookies, for: url, nowMs: nowMs)
        }
    }

    public func matchingCookies(
        for url: URL,
        nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) -> [PersistedCookieRecord] {
        lock.lock()
        defer { lock.unlock() }

        var expiredKeys: [String] = []
        var matches: [PersistedCookieRecord] = []

        for (key, record) in cookieCache {
            if record.isExpired(nowMs: nowMs) {
                expiredKeys.append(key)
            } else if matchesUrl(record: record, url: url) {
                matches.append(record)
            }
        }

        if !expiredKeys.isEmpty {
            for key in expiredKeys {
                cookieCache.removeValue(forKey: key)
            }
            persistLocked()
        }

        return matches
    }

    public func cookieHeader(
        for url: URL,
        nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) -> String? {
        let matching = matchingCookies(for: url, nowMs: nowMs)
        guard !matching.isEmpty else { return nil }
        return matching.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    public func hasSessionCookie(nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return cookieCache.values.contains { !$0.isExpired(nowMs: nowMs) }
    }

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }
        cookieCache.removeAll()
        keychain.remove(forKey: Self.storageKey)
    }

    private func matchesUrl(record: PersistedCookieRecord, url: URL) -> Bool {
        let isHttps = url.scheme?.lowercased() == "https"
        if record.secure && !isHttps { return false }
        let host = (url.host ?? "").lowercased()
        if !domainMatches(urlHost: host, cookieDomain: record.domain, hostOnly: record.hostOnly) {
            return false
        }
        let path = url.path.isEmpty ? "/" : url.path
        return pathMatches(urlPath: path, cookiePath: record.path)
    }

    private func domainMatches(urlHost: String, cookieDomain: String, hostOnly: Bool) -> Bool {
        if urlHost == cookieDomain { return true }
        if hostOnly { return false }
        return urlHost.hasSuffix(".\(cookieDomain)")
    }

    private func pathMatches(urlPath: String, cookiePath: String) -> Bool {
        if urlPath == cookiePath { return true }
        if urlPath.hasPrefix(cookiePath) {
            if cookiePath.hasSuffix("/") { return true }
            let nextIndex = urlPath.index(urlPath.startIndex, offsetBy: cookiePath.count)
            if nextIndex < urlPath.endIndex && urlPath[nextIndex] == "/" {
                return true
            }
        }
        return false
    }
}
