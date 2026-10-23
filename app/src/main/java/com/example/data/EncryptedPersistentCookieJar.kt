package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CookieCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

class AndroidKeystoreAesGcmCipher(
    private val keyAlias: String = "mini_bi_cookie_jar_aes256"
) : CookieCipher {

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        if (iv == null || iv.isEmpty() || iv.size > 64) {
            throw GeneralSecurityException("Invalid AES-GCM IV")
        }
        val encrypted = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + encrypted.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(encrypted, 0, out, 1 + iv.size, encrypted.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < 18) {
            throw GeneralSecurityException("Ciphertext too short")
        }
        val ivLen = ciphertext[0].toInt() and 0xFF
        if (ivLen <= 0 || ciphertext.size <= 1 + ivLen) {
            throw GeneralSecurityException("Malformed IV length")
        }
        val iv = ciphertext.copyOfRange(1, 1 + ivLen)
        val payload = ciphertext.copyOfRange(1 + ivLen, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }
}

class EncryptedPersistentCookieJar(
    private val storageFile: File,
    private val cipher: CookieCipher = AndroidKeystoreAesGcmCipher(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : CookieJar {

    private data class CookieIdentity(
        val name: String,
        val domain: String,
        val path: String,
        val hostOnly: Boolean
    ) {
        companion object {
            fun from(cookie: Cookie) = CookieIdentity(
                name = cookie.name,
                domain = cookie.domain,
                path = cookie.path,
                hostOnly = cookie.hostOnly
            )
        }
    }

    private val lock = Any()
    private var loaded = false
    private val store = LinkedHashMap<CookieIdentity, Cookie>()

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        if (!storageFile.exists() || !storageFile.isFile || storageFile.length() <= 0L) {
            return
        }
        try {
            val encrypted = storageFile.readBytes()
            val plainBytes = cipher.decrypt(encrypted)
            val arr = JSONArray(plainBytes.toString(Charsets.UTF_8))
            val now = nowMillis()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val cookie = deserializeCookie(obj) ?: continue
                if (cookie.expiresAt > now) {
                    store[CookieIdentity.from(cookie)] = cookie
                }
            }
        } catch (_: Exception) {
            // Fail closed: never use corrupted or undecryptable cookie state
            store.clear()
            runCatching { storageFile.delete() }
        }
    }

    private fun persistLocked() {
        if (store.isEmpty()) {
            runCatching { storageFile.delete() }
            return
        }
        val arr = JSONArray()
        store.values.forEach { cookie ->
            arr.put(
                JSONObject().apply {
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("expires_at", cookie.expiresAt)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("secure", cookie.secure)
                    put("http_only", cookie.httpOnly)
                    put("host_only", cookie.hostOnly)
                }
            )
        }
        val plainBytes = arr.toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encrypt(plainBytes)
        storageFile.parentFile?.mkdirs()
        val tmpFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
        tmpFile.writeBytes(encrypted)
        if (!tmpFile.renameTo(storageFile)) {
            storageFile.writeBytes(encrypted)
            runCatching { tmpFile.delete() }
        }
    }

    private fun deserializeCookie(obj: JSONObject): Cookie? {
        return try {
            val name = obj.optString("name", "")
            val value = obj.optString("value", "")
            val domain = obj.optString("domain", "")
            val path = obj.optString("path", "/")
            val expiresAt = obj.optLong("expires_at", 0L)
            val secure = obj.optBoolean("secure", false)
            val httpOnly = obj.optBoolean("http_only", false)
            val hostOnly = obj.optBoolean("host_only", false)
            if (name.isBlank() || domain.isBlank()) return null

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path.ifBlank { "/" })

            if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            ensureLoadedLocked()
            val now = nowMillis()
            val expiredKeys = store.entries.filter { it.value.expiresAt <= now }.map { it.key }
            expiredKeys.forEach { store.remove(it) }

            for (cookie in cookies) {
                val id = CookieIdentity.from(cookie)
                if (cookie.expiresAt <= now) {
                    store.remove(id)
                } else {
                    store[id] = cookie
                }
            }
            persistLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            ensureLoadedLocked()
            val now = nowMillis()
            val expiredKeys = store.entries.filter { it.value.expiresAt <= now }.map { it.key }
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { store.remove(it) }
                persistLocked()
            }
            return store.values.filter { it.matches(url) }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            loaded = true
            store.clear()
            runCatching { storageFile.delete() }
        }
    }
}

data class SavedLoginAccount(
    val identifier: String,
    val password: String,
    val displayName: String,
    val savedAtMillis: Long
)

class SavedCredentialsStore(
    private val storageFile: File,
    private val cipher: CookieCipher = AndroidKeystoreAesGcmCipher(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()
    private var loaded = false
    private val accounts = ArrayList<SavedLoginAccount>()

    companion object {
        private const val MAX_SAVED_ACCOUNTS = 5
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        accounts.clear()
        if (!storageFile.exists() || !storageFile.isFile || storageFile.length() <= 0L) {
            return
        }
        try {
            val encrypted = storageFile.readBytes()
            val plainBytes = cipher.decrypt(encrypted)
            val arr = JSONArray(plainBytes.toString(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("identifier", "").trim()
                val pw = obj.optString("password", "")
                val name = obj.optString("display_name", "").trim()
                val savedAt = obj.optLong("saved_at_ms", 0L)
                if (id.isNotEmpty() && pw.isNotEmpty()) {
                    accounts.add(
                        SavedLoginAccount(
                            identifier = id,
                            password = pw,
                            displayName = name.ifBlank { id },
                            savedAtMillis = savedAt
                        )
                    )
                }
            }
            accounts.sortByDescending { it.savedAtMillis }
        } catch (_: Exception) {
            accounts.clear()
            runCatching { storageFile.delete() }
        }
    }

    private fun persistLocked() {
        if (accounts.isEmpty()) {
            runCatching { storageFile.delete() }
            return
        }
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("identifier", acc.identifier)
                    put("password", acc.password)
                    put("display_name", acc.displayName)
                    put("saved_at_ms", acc.savedAtMillis)
                }
            )
        }
        val plainBytes = arr.toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encrypt(plainBytes)
        storageFile.parentFile?.mkdirs()
        val tmpFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
        tmpFile.writeBytes(encrypted)
        if (!tmpFile.renameTo(storageFile)) {
            storageFile.writeBytes(encrypted)
            runCatching { tmpFile.delete() }
        }
    }

    fun loadAll(): List<SavedLoginAccount> {
        synchronized(lock) {
            ensureLoadedLocked()
            return accounts.toList()
        }
    }

    fun saveAccount(identifier: String, password: String, displayName: String) {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty() || password.isEmpty()) return
        val keyLower = cleanId.lowercase(java.util.Locale.ROOT)
        synchronized(lock) {
            ensureLoadedLocked()
            accounts.removeAll { it.identifier.trim().lowercase(java.util.Locale.ROOT) == keyLower }
            accounts.add(
                0,
                SavedLoginAccount(
                    identifier = cleanId,
                    password = password,
                    displayName = displayName.trim().ifBlank { cleanId },
                    savedAtMillis = nowMillis()
                )
            )
            while (accounts.size > MAX_SAVED_ACCOUNTS) {
                accounts.removeAt(accounts.lastIndex)
            }
            persistLocked()
        }
    }

    fun removeAccount(identifier: String) {
        val keyLower = identifier.trim().lowercase(java.util.Locale.ROOT)
        if (keyLower.isEmpty()) return
        synchronized(lock) {
            ensureLoadedLocked()
            val changed = accounts.removeAll {
                it.identifier.trim().lowercase(java.util.Locale.ROOT) == keyLower
            }
            if (changed) {
                persistLocked()
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            loaded = true
            accounts.clear()
            runCatching { storageFile.delete() }
        }
    }
}
