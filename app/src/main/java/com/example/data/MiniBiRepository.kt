package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val VALID_SCOPE_KINDS = setOf("own", "team", "all")

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
            val validTo = if (sObj.isNull("valid_to")) null else sObj.optString("valid_to", "").trim().ifBlank { null }
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

    fun deserializeCachedActor(serialized: String): AppActor? {
        return try {
            val obj = JSONObject(serialized)
            val displayIdentifier = obj.optString("display_identifier", "").trim().ifBlank { null }
            parseActorObject(obj, displayIdentifier)
        } catch (_: Exception) {
            null
        }
    }
}

class MiniBiRepository(
    private val context: Context,
    database: MiniBiDatabase = MiniBiDatabase.getInstance(context)
) {

    internal val dao = database.miniBiDao()

    companion object {
        private val KEY_ACTOR_JSON = stringPreferencesKey("actor_projection_json")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_THEME_ID = stringPreferencesKey("theme_id")

        const val DEFAULT_SERVER_URL = "https://bi.hrpartner.vn"

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

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies.toMutableList()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    val themeIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_ID] ?: "hr-partner"
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val currentActorFlow: Flow<AppActor?> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_ACTOR_JSON]
        if (raw.isNullOrBlank()) null else ActorContractParser.deserializeCachedActor(raw)
    }

    val attendanceFlow: Flow<List<AttendanceEntity>> = dao.observeAllAttendance()
    val workReportsFlow: Flow<List<WorkReportObligationEntity>> = dao.observeAllWorkReports()

    suspend fun setThemeId(themeId: String) {
        context.dataStore.edit { it[KEY_THEME_ID] = themeId }
    }

    suspend fun setServerUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        if (cleaned.isNotBlank()) {
            context.dataStore.edit { it[KEY_SERVER_URL] = cleaned }
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

    private fun extractOrigin(baseUrl: String): String {
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: throw BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = null,
            backendCode = "SERVER_URL_INVALID",
            message = "Địa chỉ máy chủ không hợp lệ."
        )
        val defaultPort = HttpUrl.defaultPort(httpUrl.scheme)
        return if (httpUrl.port == defaultPort) {
            "${httpUrl.scheme}://${httpUrl.host}"
        } else {
            "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}"
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

        val serverUrl = serverUrlFlow.first().trim().trimEnd('/')
        try {
            val origin = extractOrigin(serverUrl)
            val payloadJson = JSONObject().apply {
                put("email", trimmedIdentifier)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url("$serverUrl/api/auth/login")
                .header("Content-Type", "application/json")
                .header("Origin", origin)
                .post(payloadJson.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val code = runCatching { JSONObject(bodyStr).optString("code").ifBlank { null } }.getOrNull()
                    return@withContext Result.failure(mapHttpError(response.code, code))
                }
                val displayId = formatDisplayIdentifier(trimmedIdentifier)
                val actor = ActorContractParser.parseEnvelopeActor(bodyStr, displayId)
                saveSession(actor)
                return@withContext Result.success(actor)
            }
        } catch (e: BackendApiException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(
                BackendApiException(
                    kind = BackendFailureKind.NETWORK,
                    statusCode = null,
                    backendCode = "NETWORK_ERROR",
                    message = "Không có kết nối mạng tới máy chủ.",
                    cause = e
                )
            )
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

    internal fun mapHttpError(statusCode: Int, backendCode: String?): BackendApiException {
        return when {
            statusCode == 401 -> BackendApiException(
                kind = BackendFailureKind.UNAUTHENTICATED,
                statusCode = 401,
                backendCode = backendCode ?: "AUTH_INVALID_CREDENTIALS",
                message = "Sai thông tin đăng nhập hoặc phiên đã hết hạn."
            )
            statusCode == 403 -> BackendApiException(
                kind = BackendFailureKind.FORBIDDEN,
                statusCode = 403,
                backendCode = backendCode ?: "ACCOUNT_NOT_AVAILABLE",
                message = "Tài khoản không khả dụng hoặc không có quyền thực hiện thao tác này."
            )
            statusCode == 404 -> BackendApiException(
                kind = BackendFailureKind.NOT_FOUND,
                statusCode = 404,
                backendCode = backendCode ?: "NOT_FOUND",
                message = "Chức năng hoặc dữ liệu không tồn tại trên máy chủ."
            )
            statusCode == 409 -> BackendApiException(
                kind = BackendFailureKind.CONFLICT,
                statusCode = 409,
                backendCode = backendCode ?: "VERSION_CONFLICT",
                message = "Dữ liệu đã thay đổi trên máy chủ. Vui lòng tải lại."
            )
            statusCode == 422 -> BackendApiException(
                kind = BackendFailureKind.VALIDATION,
                statusCode = 422,
                backendCode = backendCode ?: "VALIDATION_FAILED",
                message = "Dữ liệu gửi lên không thỏa điều kiện nghiệp vụ."
            )
            statusCode == 429 -> BackendApiException(
                kind = BackendFailureKind.RATE_LIMITED,
                statusCode = 429,
                backendCode = backendCode ?: "RATE_LIMITED",
                message = "Thao tác quá nhanh. Vui lòng thử lại sau ít giây."
            )
            statusCode in 500..599 -> BackendApiException(
                kind = BackendFailureKind.SERVER_UNAVAILABLE,
                statusCode = statusCode,
                backendCode = backendCode ?: "SERVER_UNAVAILABLE",
                message = "Máy chủ tạm thời không khả dụng. Vui lòng thử lại sau."
            )
            else -> BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = statusCode,
                backendCode = backendCode ?: "REQUEST_INVALID",
                message = "Yêu cầu không hợp lệ (${backendCode ?: statusCode})."
            )
        }
    }

    internal suspend fun saveSession(actor: AppActor) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTOR_JSON] = ActorContractParser.serializeActor(actor)
        }
    }

    suspend fun clearLocalSessionAndCache() {
        cookieStore.clear()
        dao.clearWorkReportsCache()
        dao.clearAllCachedWorkers()
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACTOR_JSON)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val serverUrl = serverUrlFlow.first().trim().trimEnd('/')
        runCatching {
            val origin = extractOrigin(serverUrl)
            val request = Request.Builder()
                .url("$serverUrl/api/auth/logout")
                .header("Origin", origin)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            okHttpClient.newCall(request).execute().close()
        }
        clearLocalSessionAndCache()
    }
}
