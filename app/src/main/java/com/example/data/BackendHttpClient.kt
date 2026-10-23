package com.example.data

import android.content.Context
import coil.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class BackendHttpClient(
    val cookieJar: CookieJar,
    private val baseUrlProvider: suspend () -> String,
    private val allowCleartextHttp: Boolean,
    private val onUnauthorizedResponse: (suspend () -> Unit)? = null
) {

    companion object {
        private val MUTATION_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val DIRECT_ENTRY_CODE_MESSAGES = mapOf(
            "WORKER_ACTIVE_EPISODE_EXISTS" to "CCCD đã có hồ sơ làm việc đang hoạt động. Hãy kết thúc hồ sơ cũ trước khi tạo đợt làm việc mới.",
            "WORKER_EPISODE_REOPEN_FORBIDDEN" to "Không thể mở lại hồ sơ đã kết thúc. Hãy tạo một đợt làm việc mới.",
            "DUPLICATE_CCCD_CONFIRMATION_REQUIRED" to "Phát hiện CCCD đã tồn tại trên hệ thống. Vui lòng kiểm tra và xác nhận gửi chính thức.",
            "PORTRAIT_PROMOTION_PENDING" to "Tệp đính kèm đang được máy chủ đồng bộ hoàn tất. Hãy bấm Lưu lại một lần nữa (không tạo trùng hồ sơ).",
            "NATIONAL_ID_DUPLICATE" to "Số CCCD/CMND bị trùng trong cùng nhóm dòng nhập.",
            "NATIONAL_ID_INVALID" to "Số CCCD/CMND phải gồm đúng 9 hoặc 12 chữ số.",
            "RECRUITER_MEMBERSHIP_INVALID" to "Người tuyển chưa có nhóm/nguồn tuyển hợp lệ cho ngày bắt đầu làm việc.",
            "PROJECT_NOT_ACTIVE" to "Dự án đã chọn không còn hoạt động trong danh mục máy chủ.",
            "RECRUITER_NOT_ACTIVE" to "Người tuyển đã chọn không còn hoạt động trong danh mục máy chủ.",
            "BANK_NOT_ACTIVE" to "Ngân hàng đã chọn không còn hoạt động trong danh mục máy chủ.",
            "OFF_REQUIRES_DATE_AND_REASON" to "Trạng thái Đã nghỉ bắt buộc có ngày nghỉ và lý do nghỉ việc.",
            "GENDER_VOCABULARY_INVALID" to "Giới tính không thuộc danh mục cho phép.",
            "BATCH_INVALID" to "Máy chủ từ chối dữ liệu hồ sơ. Vui lòng kiểm tra lại các trường bắt buộc và danh mục."
        )

        fun extractOrigin(httpUrl: HttpUrl): String {
            val defaultPort = HttpUrl.defaultPort(httpUrl.scheme)
            return if (httpUrl.port == defaultPort) {
                "${httpUrl.scheme}://${httpUrl.host}"
            } else {
                "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}"
            }
        }

        fun extractOrigin(rawUrl: String): String {
            val parsed = rawUrl.trim().toHttpUrlOrNull() ?: throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "SERVER_URL_INVALID",
                message = "Địa chỉ máy chủ không hợp lệ."
            )
            return extractOrigin(parsed)
        }

        fun normalizeBaseUrl(rawUrl: String, allowCleartextHttp: Boolean): String {
            val cleaned = rawUrl.trim().trimEnd('/')
            val parsed = cleaned.toHttpUrlOrNull() ?: throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "SERVER_URL_INVALID",
                message = "Địa chỉ máy chủ không hợp lệ."
            )
            if (parsed.scheme != "https" && parsed.scheme != "http") {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = null,
                    backendCode = "SERVER_URL_SCHEME_INVALID",
                    message = "Giao thức máy chủ không hợp lệ."
                )
            }
            if (!allowCleartextHttp && parsed.scheme != "https") {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = null,
                    backendCode = "HTTPS_REQUIRED",
                    message = "Bản phát hành chính thức chỉ cho phép kết nối HTTPS bảo mật."
                )
            }
            val origin = extractOrigin(parsed)
            val encodedPath = parsed.encodedPath.trimEnd('/')
            return if (encodedPath.isEmpty() || encodedPath == "/") {
                origin
            } else {
                "$origin$encodedPath"
            }
        }

        fun mapHttpError(
            statusCode: Int,
            backendCode: String?,
            backendMessage: String? = null
        ): BackendApiException {
            val knownBusinessMsg = backendCode?.let { DIRECT_ENTRY_CODE_MESSAGES[it] }
            return when {
                statusCode == 401 -> BackendApiException(
                    kind = BackendFailureKind.UNAUTHENTICATED,
                    statusCode = 401,
                    backendCode = backendCode ?: "AUTH_UNAUTHENTICATED",
                    message = when (backendCode) {
                        "AUTH_INVALID_CREDENTIALS" -> "Sai thông tin đăng nhập. Vui lòng kiểm tra lại tài khoản và mật khẩu."
                        else -> "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                    }
                )
                statusCode == 403 -> BackendApiException(
                    kind = BackendFailureKind.FORBIDDEN,
                    statusCode = 403,
                    backendCode = backendCode ?: "FORBIDDEN",
                    message = when (backendCode) {
                        "ACCOUNT_NOT_AVAILABLE" -> "Tài khoản hiện không khả dụng trên hệ thống."
                        "CSRF_REJECTED" -> "Yêu cầu bị từ chối do nguồn gửi (Origin) không hợp lệ."
                        else -> knownBusinessMsg
                            ?: backendMessage?.takeIf { it.isNotBlank() }
                            ?: "Tài khoản không khả dụng hoặc không có quyền thực hiện thao tác này."
                    }
                )
                statusCode == 404 -> BackendApiException(
                    kind = BackendFailureKind.NOT_FOUND,
                    statusCode = 404,
                    backendCode = backendCode ?: "NOT_FOUND",
                    message = knownBusinessMsg
                        ?: backendMessage?.takeIf { it.isNotBlank() }
                        ?: "Chức năng hoặc dữ liệu không tồn tại trên máy chủ."
                )
                statusCode == 409 -> BackendApiException(
                    kind = BackendFailureKind.CONFLICT,
                    statusCode = 409,
                    backendCode = backendCode ?: "VERSION_CONFLICT",
                    message = knownBusinessMsg
                        ?: backendMessage?.takeIf { it.isNotBlank() }
                        ?: "Dữ liệu đã thay đổi trên máy chủ (xung đột phiên bản). Vui lòng tải lại."
                )
                statusCode == 422 -> BackendApiException(
                    kind = BackendFailureKind.VALIDATION,
                    statusCode = 422,
                    backendCode = backendCode ?: "VALIDATION_FAILED",
                    message = knownBusinessMsg
                        ?: backendMessage?.takeIf { it.isNotBlank() }
                        ?: "Dữ liệu gửi lên không thỏa điều kiện nghiệp vụ."
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
                    message = knownBusinessMsg
                        ?: "Máy chủ tạm thời không khả dụng. Vui lòng thử lại sau."
                )
                else -> BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = statusCode,
                    backendCode = backendCode ?: "REQUEST_INVALID",
                    message = knownBusinessMsg
                        ?: backendMessage?.takeIf { it.isNotBlank() }
                        ?: "Yêu cầu không hợp lệ (${backendCode ?: statusCode})."
                )
            }
        }
    }

    private val securityAndOriginInterceptor = Interceptor { chain ->
        val original = chain.request()
        if (!allowCleartextHttp && original.url.scheme != "https") {
            throw IOException("HTTPS_REQUIRED")
        }
        val builder = original.newBuilder()
            .removeHeader("X-Client-Platform")

        if (original.method.uppercase() in MUTATION_METHODS) {
            builder.header("Origin", extractOrigin(original.url))
        }
        chain.proceed(builder.build())
    }

    // Read/write timeouts allow multi-document R2 staging + sequential server-side promotion on full-profile batches
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .addInterceptor(securityAndOriginInterceptor)
        .build()

    // Dedicated cookie-free, Origin-free client for presigned R2 object storage PUT uploads
    private val externalStorageHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context.applicationContext)
            .okHttpClient(okHttpClient)
            .build()
    }

    suspend fun resolveUrl(relativeOrPath: String): HttpUrl {
        val base = normalizeBaseUrl(baseUrlProvider(), allowCleartextHttp)
        val normalizedPath = if (relativeOrPath.startsWith("/")) relativeOrPath else "/$relativeOrPath"
        return "$base$normalizedPath".toHttpUrlOrNull() ?: throw BackendApiException(
            kind = BackendFailureKind.INVALID_REQUEST,
            statusCode = null,
            backendCode = "URL_BUILD_INVALID",
            message = "Không thể tạo đường dẫn truy vấn máy chủ."
        )
    }

    suspend fun getJson(
        pathWithQuery: String,
        requireOkTrue: Boolean = true,
        clearSessionOn401: Boolean = true
    ): JSONObject {
        val url = resolveUrl(pathWithQuery)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        return executeJson(request, requireOkTrue = requireOkTrue, clearSessionOn401 = clearSessionOn401)
    }

    suspend fun postJson(
        path: String,
        bodyJson: JSONObject,
        idempotencyKey: String? = null,
        requireOkTrue: Boolean = true,
        clearSessionOn401: Boolean = true
    ): JSONObject {
        val url = resolveUrl(path)
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (!idempotencyKey.isNullOrBlank()) {
            builder.header("Idempotency-Key", idempotencyKey)
        }
        return executeJson(builder.build(), requireOkTrue = requireOkTrue, clearSessionOn401 = clearSessionOn401)
    }

    suspend fun postMultipartJson(
        path: String,
        multipartBody: RequestBody,
        idempotencyKey: String? = null,
        requireOkTrue: Boolean = true,
        clearSessionOn401: Boolean = true
    ): JSONObject {
        val url = resolveUrl(path)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(multipartBody)
        if (!idempotencyKey.isNullOrBlank()) {
            builder.header("Idempotency-Key", idempotencyKey)
        }
        return executeJson(builder.build(), requireOkTrue = requireOkTrue, clearSessionOn401 = clearSessionOn401)
    }

    suspend fun postNoContent(path: String) {
        val url = resolveUrl(path)
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        executeNoContent(request)
    }

    suspend fun deleteWithJsonNoContent(path: String, bodyJson: JSONObject) {
        val url = resolveUrl(path)
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .delete(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeNoContent(request)
    }

    suspend fun postForBinary(
        path: String,
        bodyJson: JSONObject
    ): Pair<ByteArray, String?> {
        val url = resolveUrl(path)
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeBinary(request)
    }

    suspend fun getBinary(path: String): Pair<ByteArray, String?> {
        val url = resolveUrl(path)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return executeBinary(request)
    }

    suspend fun putBinaryToSignedUrl(
        signedUrl: String,
        headers: Map<String, String>,
        bytes: ByteArray,
        fallbackMimeType: String
    ) = withContext(Dispatchers.IO) {
        val parsedUrl = signedUrl.trim().toHttpUrlOrNull() ?: throw BackendApiException(
            kind = BackendFailureKind.CONTRACT_INVALID,
            statusCode = null,
            backendCode = "SIGNED_UPLOAD_URL_INVALID",
            message = "Đường dẫn tải tệp lên kho lưu trữ không hợp lệ."
        )
        if (!allowCleartextHttp && parsedUrl.scheme != "https") {
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = "HTTPS_REQUIRED",
                message = "Bản phát hành chính thức chỉ cho phép kết nối HTTPS bảo mật."
            )
        }
        val contentTypeHeader = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: fallbackMimeType

        val builder = Request.Builder()
            .url(parsedUrl)
            .put(bytes.toRequestBody(contentTypeHeader.toMediaType()))
        headers.forEach { (k, v) ->
            if (k.isNotBlank()) {
                builder.header(k, v)
            }
        }
        try {
            externalStorageHttpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw BackendApiException(
                        kind = BackendFailureKind.SERVER_UNAVAILABLE,
                        statusCode = response.code,
                        backendCode = "SIGNED_UPLOAD_PUT_FAILED",
                        message = "Tải ảnh/giấy tờ lên kho lưu trữ thất bại (HTTP ${response.code})."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_TIMEOUT",
                message = "Tải ảnh/giấy tờ quá thời gian chờ mạng.",
                cause = e
            )
        } catch (e: IOException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_ERROR",
                message = "Mất kết nối mạng khi tải ảnh/giấy tờ đính kèm.",
                cause = e
            )
        }
    }

    suspend fun executeJson(
        request: Request,
        requireOkTrue: Boolean = true,
        clearSessionOn401: Boolean = true
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errObj = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val code = errObj?.optString("code")?.ifBlank { null }
                    val msg = errObj?.optString("message")?.ifBlank { null }
                    if (response.code == 401 && clearSessionOn401) {
                        onUnauthorizedResponse?.invoke()
                    }
                    throw mapHttpError(response.code, code, msg)
                }
                val parsed = try {
                    JSONObject(bodyStr)
                } catch (e: Exception) {
                    throw BackendApiException(
                        kind = BackendFailureKind.CONTRACT_INVALID,
                        statusCode = response.code,
                        backendCode = "CONTRACT_INVALID",
                        message = "Phản hồi máy chủ không đúng định dạng hợp đồng JSON.",
                        cause = e
                    )
                }
                if (requireOkTrue && !parsed.optBoolean("ok", false)) {
                    throw BackendApiException(
                        kind = BackendFailureKind.CONTRACT_INVALID,
                        statusCode = response.code,
                        backendCode = parsed.optString("code").ifBlank { "CONTRACT_INVALID" },
                        message = "Máy chủ trả dữ liệu thiếu cờ xác nhận hợp lệ."
                    )
                }
                parsed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_TIMEOUT",
                message = "Máy chủ xử lý quá thời gian chờ. Vui lòng bấm gửi lại (hệ thống tự chống tạo trùng).",
                cause = e
            )
        } catch (e: InterruptedIOException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_TIMEOUT",
                message = "Máy chủ xử lý quá thời gian chờ. Vui lòng bấm gửi lại (hệ thống tự chống tạo trùng).",
                cause = e
            )
        } catch (e: IOException) {
            if (e.message == "HTTPS_REQUIRED") {
                throw BackendApiException(
                    kind = BackendFailureKind.INVALID_REQUEST,
                    statusCode = null,
                    backendCode = "HTTPS_REQUIRED",
                    message = "Bản phát hành chính thức chỉ cho phép kết nối HTTPS bảo mật.",
                    cause = e
                )
            }
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_ERROR",
                message = "Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng.",
                cause = e
            )
        }
    }

    private suspend fun executeNoContent(request: Request) = withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errObj = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val code = errObj?.optString("code")?.ifBlank { null }
                    val msg = errObj?.optString("message")?.ifBlank { null }
                    throw mapHttpError(response.code, code, msg)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_TIMEOUT",
                message = "Máy chủ xử lý quá thời gian chờ. Vui lòng thử lại.",
                cause = e
            )
        } catch (e: IOException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_ERROR",
                message = "Không thể kết nối tới máy chủ.",
                cause = e
            )
        }
    }

    private suspend fun executeBinary(request: Request): Pair<ByteArray, String?> = withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val errObj = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val code = errObj?.optString("code")?.ifBlank { null }
                    val msg = errObj?.optString("message")?.ifBlank { null }
                    if (response.code == 401) {
                        onUnauthorizedResponse?.invoke()
                    }
                    throw mapHttpError(response.code, code, msg)
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type")
                bytes to contentType
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_TIMEOUT",
                message = "Tải tệp quá thời gian chờ. Vui lòng thử lại.",
                cause = e
            )
        } catch (e: IOException) {
            throw BackendApiException(
                kind = BackendFailureKind.NETWORK,
                statusCode = null,
                backendCode = "NETWORK_ERROR",
                message = "Không thể tải tệp từ máy chủ.",
                cause = e
            )
        }
    }
}
