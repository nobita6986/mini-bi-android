package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.ImageLoader
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "mini_bi_prefs")

enum class BackendFailureKind {
    NETWORK,
    INVALID_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    RATE_LIMITED,
    SERVER_UNAVAILABLE,
    CONTRACT_INVALID
}

class BackendApiException(
    val kind: BackendFailureKind,
    val statusCode: Int?,
    val backendCode: String?,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object ActorContractParser {
    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE
    )
    private val VALID_SCOPE_KINDS = setOf("own", "team", "all")

    fun parseEnvelopeActor(json: JSONObject, displayIdentifier: String? = null): AppActor {
        if (!json.optBoolean("ok", false)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = json.optString("code").ifBlank { "CONTRACT_INVALID" },
                message = "Máy chủ trả trạng thái không hợp lệ."
            )
        }
        val actorObj = json.optJSONObject("actor") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "ACTOR_MISSING",
            message = "Thiếu thông tin phiên người dùng từ máy chủ."
        )
        return parseActorObject(actorObj, displayIdentifier)
    }

    fun parseEnvelopeActor(bodyStr: String, displayIdentifier: String? = null): AppActor {
        val json = try {
            JSONObject(bodyStr)
        } catch (e: Exception) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "CONTRACT_INVALID",
                message = "Phản hồi máy chủ không đúng định dạng JSON.",
                cause = e
            )
        }
        return parseEnvelopeActor(json, displayIdentifier)
    }

    fun parseActorObject(actorObj: JSONObject, displayIdentifier: String? = null): AppActor {
        val appUserId = actorObj.optString("app_user_id", "").trim()
        if (!UUID_REGEX.matches(appUserId)) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "ACTOR_ID_INVALID",
                message = "Mã định danh tài khoản từ máy chủ không hợp lệ."
            )
        }
        val displayName = actorObj.optString("display_name", "").trim()
        if (displayName.isBlank()) {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "DISPLAY_NAME_INVALID",
                message = "Thiếu tên hiển thị từ máy chủ."
            )
        }

        val capArr = actorObj.optJSONArray("capabilities") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "CAPABILITIES_MISSING",
            message = "Thiếu danh sách quyền hạn từ máy chủ."
        )
        val capabilities = mutableListOf<String>()
        for (i in 0 until capArr.length()) {
            val item = capArr.optString(i, "").trim()
            if (item.isBlank()) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "CAPABILITY_INVALID",
                    message = "Quyền hạn trả về không hợp lệ."
                )
            }
            capabilities.add(item)
        }

        val scopesArr = actorObj.optJSONArray("scopes") ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = 200,
            backendCode = "SCOPES_MISSING",
            message = "Thiếu phạm vi dữ liệu từ máy chủ."
        )
        val scopes = mutableListOf<ActorScope>()
        for (i in 0 until scopesArr.length()) {
            val sObj = scopesArr.optJSONObject(i) ?: throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = 200,
                backendCode = "SCOPE_INVALID",
                message = "Phạm vi dữ liệu không hợp lệ."
            )
            val kind = sObj.optString("kind", "").trim()
            val reference = sObj.optString("reference", "").trim()
            val validFrom = sObj.optString("valid_from", "").trim()
            val validTo = if (sObj.isNull("valid_to")) {
                null
            } else {
                sObj.optString("valid_to", "").trim().ifBlank { null }
            }
            if (kind !in VALID_SCOPE_KINDS || validFrom.isBlank()) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = 200,
                    backendCode = "SCOPE_INVALID",
                    message = "Phạm vi dữ liệu từ máy chủ không hợp lệ."
                )
            }
            scopes.add(
                ActorScope(
                    kind = kind,
                    reference = reference,
                    validFrom = validFrom,
                    validTo = validTo
                )
            )
        }

        val suggestion = if (!actorObj.has("self_recruiter_suggestion") || actorObj.isNull("self_recruiter_suggestion")) {
            null
        } else {
            actorObj.optString("self_recruiter_suggestion", "").trim().ifBlank { null }
        }

        return AppActor(
            appUserId = appUserId,
            displayName = displayName,
            capabilities = capabilities,
            scopes = scopes,
            selfRecruiterSuggestion = suggestion,
            displayIdentifier = displayIdentifier?.trim()?.ifBlank { null }
        )
    }

    fun serializeActor(actor: AppActor): String {
        val obj = JSONObject().apply {
            put("app_user_id", actor.appUserId)
            put("display_name", actor.displayName)
            put("capabilities", JSONArray(actor.capabilities))
            put(
                "scopes",
                JSONArray().apply {
                    actor.scopes.forEach { scope ->
                        put(
                            JSONObject().apply {
                                put("kind", scope.kind)
                                put("reference", scope.reference)
                                put("valid_from", scope.validFrom)
                                put("valid_to", scope.validTo ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
            put("self_recruiter_suggestion", actor.selfRecruiterSuggestion ?: JSONObject.NULL)
            if (!actor.displayIdentifier.isNullOrBlank()) {
                put("display_identifier", actor.displayIdentifier)
            }
        }
        return obj.toString()
    }
}

class MiniBiRepository(
    private val context: Context,
    database: MiniBiDatabase = MiniBiDatabase.getInstance(context),
    private val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    private val allowDebugUrlOverride: Boolean = BuildConfig.DEBUG,
    cookieStorageFile: File = File(context.noBackupFilesDir, "session_cookies.enc"),
    cookieCipher: CookieCipher = AndroidKeystoreAesGcmCipher(),
    nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    internal val dao = database.miniBiDao()

    companion object {
        private val KEY_DISPLAY_IDENTIFIER = stringPreferencesKey("display_identifier_hint")
        private val KEY_DEBUG_SERVER_URL = stringPreferencesKey("debug_server_url")
        private val KEY_THEME_ID = stringPreferencesKey("theme_id")

        fun computeSha256HexStrict(file: File): String {
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                throw IOException("Tệp hình ảnh không tồn tại hoặc rỗng.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    val cookieJar = EncryptedPersistentCookieJar(
        storageFile = cookieStorageFile,
        cipher = cookieCipher,
        nowMillis = nowMillis
    )

    internal val httpClient = BackendHttpClient(
        cookieJar = cookieJar,
        baseUrlProvider = { resolveEffectiveServerUrl() },
        allowCleartextHttp = allowDebugUrlOverride,
        onUnauthorizedResponse = { clearLocalSessionAndCache() }
    )

    val directEntryService = DirectEntryService(
        context = context,
        httpClient = httpClient,
        dao = dao
    )

    val workReportsAndReportingService = WorkReportsAndReportingService(
        httpClient = httpClient,
        dao = dao
    )

    val attendanceOutboxService = AttendanceOutboxService(
        context = context,
        httpClient = httpClient,
        dao = dao,
        nowMillis = nowMillis
    )

    val imageLoader: ImageLoader by lazy {
        httpClient.createImageLoader(context)
    }

    private val _activeActor = MutableStateFlow<AppActor?>(null)
    val currentActorFlow: StateFlow<AppActor?> = _activeActor.asStateFlow()

    val themeIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_ID] ?: "hr-partner"
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        if (allowDebugUrlOverride) {
            prefs[KEY_DEBUG_SERVER_URL]?.takeIf { it.isNotBlank() }
                ?: defaultServerUrl.trim().trimEnd('/')
        } else {
            defaultServerUrl.trim().trimEnd('/')
        }
    }

    val attendanceFlow: Flow<List<AttendanceEntity>> = dao.observeAllAttendance()
    val workReportsFlow: Flow<List<WorkReportObligationEntity>> = dao.observeAllWorkReports()

    suspend fun resolveEffectiveServerUrl(): String {
        val raw = if (allowDebugUrlOverride) {
            val prefs = context.dataStore.data.first()
            prefs[KEY_DEBUG_SERVER_URL]?.takeIf { it.isNotBlank() } ?: defaultServerUrl
        } else {
            defaultServerUrl
        }
        return BackendHttpClient.normalizeBaseUrl(raw, allowCleartextHttp = allowDebugUrlOverride)
    }

    suspend fun setThemeId(themeId: String) {
        context.dataStore.edit { it[KEY_THEME_ID] = themeId }
    }

    suspend fun setServerUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!allowDebugUrlOverride) {
                throw BackendApiException(
                    kind = BackendFailureKind.FORBIDDEN,
                    statusCode = null,
                    backendCode = "SERVER_URL_LOCKED",
                    message = "Bản phát hành chính thức không cho phép đổi địa chỉ máy chủ."
                )
            }
            val normalized = BackendHttpClient.normalizeBaseUrl(url, allowCleartextHttp = true)
            context.dataStore.edit { it[KEY_DEBUG_SERVER_URL] = normalized }
            Result.success(normalized)
        } catch (e: BackendApiException) {
            Result.failure(e)
        }
    }

    fun formatDisplayIdentifier(rawIdentifier: String): String {
        val trimmed = rawIdentifier.trim().lowercase(Locale.ROOT)
        val shortIdRegex = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
        return if (shortIdRegex.matches(trimmed)) {
            "$trimmed@hrpartner.vn"
        } else {
            trimmed
        }
    }

    suspend fun bootstrapSession(): Result<AppActor> = withContext(Dispatchers.IO) {
        try {
            val displayHint = context.dataStore.data.first()[KEY_DISPLAY_IDENTIFIER]
            val json = httpClient.getJson(
                pathWithQuery = "/api/auth/session",
                requireOkTrue = true,
                clearSessionOn401 = false
            )
            val actor = ActorContractParser.parseEnvelopeActor(json, displayHint)
            _activeActor.value = actor
            if (dao.getRetryableAttendanceOutboxForUser(actor.appUserId).isNotEmpty()) {
                AttendanceOutboxService.enqueueDurableOutboxWork(context)
            }
            Result.success(actor)
        } catch (e: BackendApiException) {
            if (e.kind == BackendFailureKind.UNAUTHENTICATED || e.kind == BackendFailureKind.FORBIDDEN) {
                clearLocalSessionAndCache()
            } else {
                // Never expose stale cached actor if backend session could not be verified
                _activeActor.value = null
            }
            Result.failure(e)
        } catch (e: Exception) {
            _activeActor.value = null
            Result.failure(
                BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = null,
                    backendCode = "SESSION_BOOTSTRAP_ERROR",
                    message = "Không thể xác thực phiên làm việc.",
                    cause = e
                )
            )
        }
    }

    suspend fun login(rawIdentifier: String, password: String): Result<AppActor> = withContext(Dispatchers.IO) {
        val trimmedIdentifier = rawIdentifier.trim()
        if (trimmedIdentifier.isBlank() || password.isEmpty()) {
            return@withContext Result.failure(
                BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = 400,
                    backendCode = "AUTH_REQUEST_INVALID",
                    message = "Vui lòng nhập đầy đủ tài khoản và mật khẩu."
                )
            )
        }

        try {
            val payloadJson = JSONObject().apply {
                put("email", trimmedIdentifier)
                put("password", password)
            }
            val json = httpClient.postJson(
                path = "/api/auth/login",
                bodyJson = payloadJson,
                requireOkTrue = true,
                clearSessionOn401 = false
            )
            val displayId = formatDisplayIdentifier(trimmedIdentifier)
            val actor = ActorContractParser.parseEnvelopeActor(json, displayId)
            context.dataStore.edit { prefs ->
                prefs[KEY_DISPLAY_IDENTIFIER] = displayId
            }
            _activeActor.value = actor
            if (dao.getRetryableAttendanceOutboxForUser(actor.appUserId).isNotEmpty()) {
                AttendanceOutboxService.enqueueDurableOutboxWork(context)
            }
            Result.success(actor)
        } catch (e: BackendApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = null,
                    backendCode = "UNEXPECTED_ERROR",
                    message = "Lỗi xử lý phản hồi đăng nhập.",
                    cause = e
                )
            )
        }
    }

    suspend fun clearLocalSessionAndCache() {
        _activeActor.value = null
        cookieJar.clearAll()
        dao.clearWorkReportsCache()
        dao.clearAllCachedWorkers()
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DISPLAY_IDENTIFIER)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            httpClient.postNoContent("/api/auth/logout")
        } catch (_: Exception) {
            // Even if server logout call fails due to network, always clear local sensitive state afterwards
        } finally {
            clearLocalSessionAndCache()
        }
    }

    suspend fun getCachedWorkersByScope(scope: String): List<WorkerProfileEntity> = withContext(Dispatchers.IO) {
        dao.getCachedWorkersByScope(scope)
    }
}
