import Foundation
import Security

// MARK: - Keychain Raw Storage with Thread-Safe Simulator/Test Fallback

public final class KeychainStore: @unchecked Sendable {
    public static let shared = KeychainStore(service: "com.aistudio.minibi.hrpbia.keychain")

    private let service: String
    private let lock = NSLock()
    private var memoryFallback: [String: Data] = [:]
    private let forceMemoryOnly: Bool

    public init(service: String, forceMemoryOnly: Bool = false) {
        self.service = service
        self.forceMemoryOnly = forceMemoryOnly
    }

    public func setData(_ data: Data, forKey key: String) {
        lock.lock()
        defer { lock.unlock() }

        memoryFallback[key] = data
        guard !forceMemoryOnly else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
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

    public func data(forKey key: String) -> Data? {
        lock.lock()
        defer { lock.unlock() }

        if !forceMemoryOnly {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
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
        }
        return memoryFallback[key]
    }

    public func setString(_ value: String, forKey key: String) {
        if let data = value.data(using: .utf8) {
            setData(data, forKey: key)
        }
    }

    public func string(forKey key: String) -> String? {
        guard let data = data(forKey: key) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    public func removeValue(forKey key: String) {
        lock.lock()
        defer { lock.unlock() }

        memoryFallback.removeValue(forKey: key)
        guard !forceMemoryOnly else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        _ = SecItemDelete(query as CFDictionary)
    }

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }

        memoryFallback.removeAll()
        guard !forceMemoryOnly else { return }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service
        ]
        _ = SecItemDelete(query as CFDictionary)
    }
}

// MARK: - Persistent Cookie Store (Equivalent to Android EncryptedPersistentCookieJar)

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
    private static let storageKey = "persisted_cookies_v1"
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

        guard let data = keychain.data(forKey: Self.storageKey),
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

    public func saveCookies(_ cookies: [HTTPCookie], for url: URL, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)) {
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

    public func saveFromResponseHeaders(_ headerFields: [String: String], for url: URL, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)) {
        let cookies = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
        if !cookies.isEmpty {
            saveCookies(cookies, for: url, nowMs: nowMs)
        }
    }

    public func matchingCookies(for url: URL, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)) -> [PersistedCookieRecord] {
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

    public func cookieHeader(for url: URL, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)) -> String? {
        let matching = matchingCookies(for: url, nowMs: nowMs)
        guard !matching.isEmpty else { return nil }
        return matching.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    public func hasSessionCookies(nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return cookieCache.values.contains { !$0.isExpired(nowMs: nowMs) }
    }

    public func clearAll() {
        lock.lock()
        defer { lock.unlock() }
        cookieCache.removeAll()
        keychain.removeValue(forKey: Self.storageKey)
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

    internal func domainMatches(urlHost: String, cookieDomain: String, hostOnly: Bool) -> Bool {
        if urlHost == cookieDomain { return true }
        if hostOnly { return false }
        return urlHost.hasSuffix(".\(cookieDomain)")
    }

    internal func pathMatches(urlPath: String, cookiePath: String) -> Bool {
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

// MARK: - Saved Credentials & Session Keychain Store

public final class SavedCredentialsStore: @unchecked Sendable {
    private static let savedAccountsKey = "saved_login_accounts_v1"
    private static let cachedActorKey = "cached_session_actor_v1"
    private static let customServerUrlKey = "custom_server_url_v1"
    private static let selectedThemeIdKey = "selected_theme_id_v1"
    private static let darkModeEnabledKey = "dark_mode_enabled_v1"
    public static let maxSavedAccounts = 5

    private let keychain: KeychainStore
    private let lock = NSLock()

    public init(keychain: KeychainStore = .shared) {
        self.keychain = keychain
    }

    public func loadSavedAccounts() -> [SavedLoginAccount] {
        lock.lock()
        defer { lock.unlock() }

        guard let data = keychain.data(forKey: Self.savedAccountsKey),
              let list = try? JSONDecoder().decode([SavedLoginAccount].self, from: data)
        else {
            return []
        }
        return list
            .filter { !$0.identifier.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !$0.password.isEmpty }
            .sorted { $0.lastUsedEpochMs > $1.lastUsedEpochMs }
            .prefix(Self.maxSavedAccounts)
            .map { $0 }
    }

    @discardableResult
    public func saveAccount(
        identifier: String,
        password: String,
        displayName: String = "",
        nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) -> [SavedLoginAccount] {
        let cleanId = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty && !password.isEmpty else {
            return loadSavedAccounts()
        }
        let existing = loadSavedAccounts().filter {
            $0.identifier.caseInsensitiveCompare(cleanId) != .orderedSame
        }
        let resolvedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? cleanId : displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let entry = SavedLoginAccount(
            identifier: cleanId,
            password: password,
            displayName: resolvedName,
            lastUsedEpochMs: nowEpochMs
        )
        let updated = ([entry] + existing).prefix(Self.maxSavedAccounts).map { $0 }
        persistAccounts(updated)
        return updated
    }

    @discardableResult
    public func removeAccount(identifier: String) -> [SavedLoginAccount] {
        let cleanId = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        let updated = loadSavedAccounts().filter {
            $0.identifier.caseInsensitiveCompare(cleanId) != .orderedSame
        }
        persistAccounts(updated)
        return updated
    }

    public func clearSavedAccounts() {
        lock.lock()
        defer { lock.unlock() }
        keychain.removeValue(forKey: Self.savedAccountsKey)
    }

    private func persistAccounts(_ accounts: [SavedLoginAccount]) {
        lock.lock()
        defer { lock.unlock() }
        if let data = try? JSONEncoder().encode(accounts) {
            keychain.setData(data, forKey: Self.savedAccountsKey)
        }
    }

    public func saveActorSnapshot(_ actor: AppActor?) {
        lock.lock()
        defer { lock.unlock() }
        guard let actor = actor else {
            keychain.removeValue(forKey: Self.cachedActorKey)
            return
        }
        if let data = try? JSONEncoder().encode(actor) {
            keychain.setData(data, forKey: Self.cachedActorKey)
        }
    }

    public func loadActorSnapshot() -> AppActor? {
        lock.lock()
        defer { lock.unlock() }
        guard let data = keychain.data(forKey: Self.cachedActorKey) else { return nil }
        return try? JSONDecoder().decode(AppActor.self, from: data)
    }

    public func saveCustomServerUrl(_ url: String?) {
        lock.lock()
        defer { lock.unlock() }
        guard let url = url, !url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            keychain.removeValue(forKey: Self.customServerUrlKey)
            return
        }
        keychain.setString(url.trimmingCharacters(in: .whitespacesAndNewlines), forKey: Self.customServerUrlKey)
    }

    public func loadCustomServerUrl() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return keychain.string(forKey: Self.customServerUrlKey)
    }

    public func saveThemePreferences(themeId: String, isDarkMode: Bool) {
        lock.lock()
        defer { lock.unlock() }
        keychain.setString(themeId, forKey: Self.selectedThemeIdKey)
        keychain.setString(isDarkMode ? "true" : "false", forKey: Self.darkModeEnabledKey)
    }

    public func loadThemeId() -> String {
        lock.lock()
        defer { lock.unlock() }
        return keychain.string(forKey: Self.selectedThemeIdKey) ?? "hr-partner"
    }

    public func loadDarkModeEnabled() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return keychain.string(forKey: Self.darkModeEnabledKey) == "true"
    }
}
