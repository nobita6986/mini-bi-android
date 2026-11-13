package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Safety flag: while BI backend has not enabled `/api/bulletins`, keep this `false`
 * so the Android app never sends any HTTP request to `/api/bulletins` during startup,
 * login, or when opening the HRPartner Bulletin sheet.
 */
const val BULLETIN_REMOTE_API_ENABLED: Boolean = false

enum class HrpBulletinCategory(
    val wireKey: String,
    val labelVi: String
) {
    ALL("ALL", "Tất cả"),
    HR_NEWS("HR_NEWS", "Tin nhân sự"),
    HOLIDAY_SCHEDULE("HOLIDAY_SCHEDULE", "Lịch nghỉ lễ"),
    INTERNAL_POLICY("INTERNAL_POLICY", "Vận hành"),
    GENERAL("GENERAL", "Thông báo chung");

    companion object {
        val filterCategories: List<HrpBulletinCategory> = listOf(
            ALL,
            HR_NEWS,
            HOLIDAY_SCHEDULE,
            INTERNAL_POLICY
        )

        fun fromWireKey(raw: String?): HrpBulletinCategory {
            val cleaned = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return when (cleaned) {
                "ALL" -> ALL
                "HR_NEWS", "HR", "NEWS", "RECRUITMENT_NEWS" -> HR_NEWS
                "HOLIDAY_SCHEDULE", "HOLIDAY", "LEAVE_SCHEDULE" -> HOLIDAY_SCHEDULE
                "INTERNAL_POLICY", "OPERATIONS", "OPERATION", "POLICY", "SOP" -> INTERNAL_POLICY
                "GENERAL", "" -> GENERAL
                else -> entries.firstOrNull { it.wireKey.equals(cleaned, ignoreCase = true) } ?: GENERAL
            }
        }
    }
}

enum class HrpBulletinAttachmentKind(val wireKey: String) {
    IMAGE("IMAGE"),
    PDF("PDF");

    companion object {
        fun fromWireKeyOrMime(
            rawKind: String?,
            mimeType: String? = null,
            fileName: String? = null
        ): HrpBulletinAttachmentKind {
            val cleanedKind = rawKind?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (cleanedKind == "PDF") return PDF
            if (cleanedKind == "IMAGE" || cleanedKind == "IMG" || cleanedKind == "PHOTO") return IMAGE
            val cleanedMime = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (cleanedMime.contains("pdf")) return PDF
            if (cleanedMime.startsWith("image/")) return IMAGE
            val cleanedName = fileName?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (cleanedName.endsWith(".pdf")) return PDF
            return IMAGE
        }
    }
}

data class HrpBulletinAttachment(
    val attachmentId: String,
    val kind: HrpBulletinAttachmentKind,
    val fileName: String,
    val title: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val pageCount: Int? = null,
    val sha256: String? = null
) {
    val formattedFileSize: String
        get() = HrpBulletinContract.formatFileSize(fileSizeBytes)
}

data class HrpBulletinItem(
    val bulletinId: String,
    val title: String,
    val category: HrpBulletinCategory,
    val categoryDisplay: String,
    val badgeText: String?,
    val isPinned: Boolean,
    val isRead: Boolean,
    val publishedAtIso: String?,
    val publishedDateDisplay: String,
    val authorDisplay: String,
    val summary: String,
    val contentSections: List<String>,
    val imageCount: Int,
    val pdfCount: Int,
    val attachments: List<HrpBulletinAttachment>
)

data class HrpBulletinListResult(
    val items: List<HrpBulletinItem>,
    val unreadCount: Int,
    val pageSize: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

object HrpBulletinContract {
    const val REMOTE_API_ENABLED: Boolean = BULLETIN_REMOTE_API_ENABLED
    const val DEFAULT_PAGE_SIZE: Int = 10

    fun formatFileSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        return when {
            safeBytes >= 1_048_576L -> {
                val mb = safeBytes.toDouble() / 1_048_576.0
                String.format(Locale.US, "%.1f MB", mb)
            }
            safeBytes >= 1_024L -> {
                val kb = ((safeBytes + 512L) / 1_024L).coerceAtLeast(1L)
                "$kb KB"
            }
            else -> "$safeBytes B"
        }
    }

    fun formatIsoToDisplayDate(publishedAtIso: String?, fallback: String = ""): String {
        val cleaned = publishedAtIso?.trim().orEmpty()
        if (cleaned.length >= 10 && cleaned[4] == '-' && cleaned[7] == '-') {
            val yyyy = cleaned.substring(0, 4)
            val mm = cleaned.substring(5, 7)
            val dd = cleaned.substring(8, 10)
            if (yyyy.all { it.isDigit() } && mm.all { it.isDigit() } && dd.all { it.isDigit() }) {
                return "$dd-$mm-$yyyy"
            }
        }
        return fallback.ifBlank { cleaned }
    }

    fun buildListPath(
        category: HrpBulletinCategory? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        cursor: String? = null
    ): String {
        val safePageSize = pageSize.coerceIn(1, 50)
        val params = mutableListOf<String>()
        if (category != null && category != HrpBulletinCategory.ALL) {
            params += "category=${encodeQueryParam(category.wireKey)}"
        }
        params += "page_size=$safePageSize"
        val cleanCursor = cursor?.trim().orEmpty()
        if (cleanCursor.isNotEmpty()) {
            params += "cursor=${encodeQueryParam(cleanCursor)}"
        }
        return "/api/bulletins?${params.joinToString("&")}"
    }

    fun buildDetailPath(bulletinId: String): String {
        val cleanId = bulletinId.trim()
        require(cleanId.isNotEmpty()) { "bulletinId must not be blank" }
        return "/api/bulletins/${encodeQueryParam(cleanId)}"
    }

    fun buildMarkReadPath(bulletinId: String): String {
        val cleanId = bulletinId.trim()
        require(cleanId.isNotEmpty()) { "bulletinId must not be blank" }
        return "/api/bulletins/${encodeQueryParam(cleanId)}/read"
    }

    private fun encodeQueryParam(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    fun sortBulletins(items: List<HrpBulletinItem>): List<HrpBulletinItem> {
        return items.sortedWith(
            compareByDescending<HrpBulletinItem> { it.isPinned }
                .thenByDescending { it.publishedAtIso.orEmpty() }
        )
    }

    fun parseListResponse(bodyStr: String): HrpBulletinListResult {
        val root = runCatching { JSONObject(bodyStr) }.getOrElse {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = null,
                backendCode = "BULLETIN_LIST_INVALID_JSON",
                message = "Phản hồi danh sách bản tin không hợp lệ."
            )
        }
        if (root.has("ok") && !root.optBoolean("ok", false)) {
            val (code, msg) = BackendHttpClient.extractErrorCodeAndMessage(bodyStr)
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = code ?: "BULLETIN_LIST_REJECTED",
                message = msg ?: "Không thể tải danh sách bản tin."
            )
        }
        val dataObj = root.optJSONObject("data") ?: root
        val itemsArr = dataObj.optJSONArray("items") ?: JSONArray()
        val parsedItems = mutableListOf<HrpBulletinItem>()
        for (i in 0 until itemsArr.length()) {
            val itemObj = itemsArr.optJSONObject(i) ?: continue
            parsedItems += parseBulletinItemObject(itemObj)
        }
        val sortedItems = sortBulletins(parsedItems)
        val unreadCount = if (dataObj.has("unread_count") && !dataObj.isNull("unread_count")) {
            dataObj.optInt("unread_count", sortedItems.count { !it.isRead }).coerceAtLeast(0)
        } else {
            sortedItems.count { !it.isRead }
        }
        val pageSize = dataObj.optInt("page_size", DEFAULT_PAGE_SIZE).coerceAtLeast(1)
        val hasMore = dataObj.optBoolean("has_more", false)
        val nextCursor = dataObj.optCleanString("next_cursor")
        return HrpBulletinListResult(
            items = sortedItems,
            unreadCount = unreadCount,
            pageSize = pageSize,
            hasMore = hasMore,
            nextCursor = nextCursor
        )
    }

    fun parseDetailResponse(bodyStr: String): HrpBulletinItem {
        val root = runCatching { JSONObject(bodyStr) }.getOrElse {
            throw BackendApiException(
                kind = BackendFailureKind.CONTRACT_INVALID,
                statusCode = null,
                backendCode = "BULLETIN_DETAIL_INVALID_JSON",
                message = "Phản hồi chi tiết bản tin không hợp lệ."
            )
        }
        if (root.has("ok") && !root.optBoolean("ok", false)) {
            val (code, msg) = BackendHttpClient.extractErrorCodeAndMessage(bodyStr)
            throw BackendApiException(
                kind = BackendFailureKind.INVALID_REQUEST,
                statusCode = null,
                backendCode = code ?: "BULLETIN_DETAIL_REJECTED",
                message = msg ?: "Không thể tải chi tiết bản tin."
            )
        }
        val dataObj = root.optJSONObject("data") ?: root
        val itemObj = dataObj.optJSONObject("item")
            ?: dataObj.optJSONObject("bulletin")
            ?: dataObj
        return parseBulletinItemObject(itemObj)
    }

    fun parseMarkReadResponse(bodyStr: String): Boolean {
        val root = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return false
        return !root.has("ok") || root.optBoolean("ok", false)
    }

    private fun JSONObject.optCleanString(vararg keys: String): String? {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val value = optString(key).trim()
                if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                    return value
                }
            }
        }
        return null
    }

    internal fun parseBulletinItemObject(obj: JSONObject): HrpBulletinItem {
        val bulletinId = obj.optCleanString("bulletin_id", "id").orEmpty()
        require(bulletinId.isNotEmpty()) { "bulletin_id is required" }

        val title = obj.optCleanString("title") ?: "Bản tin HRPartner"
        val rawCategory = obj.optCleanString("category", "category_key").orEmpty()
        val category = HrpBulletinCategory.fromWireKey(rawCategory)
        val categoryDisplay = obj.optCleanString("category_display", "category_label")
            ?: category.labelVi

        val badgeText = obj.optCleanString("badge_text", "badge")

        val isPinned = obj.optBoolean("is_pinned", obj.optBoolean("pinned", false))
        val isRead = obj.optBoolean("is_read", obj.optBoolean("read", false))

        val publishedAtIso = obj.optCleanString("published_at", "published_at_iso")

        val rawPublishedDateDisplay = obj.optCleanString("published_date_display", "published_date").orEmpty()
        val publishedDateDisplay = if (rawPublishedDateDisplay.isNotEmpty()) {
            rawPublishedDateDisplay
        } else {
            formatIsoToDisplayDate(publishedAtIso, fallback = "13-11-2026")
        }

        val authorDisplay = obj.optCleanString("author_display", "author_name", "department_name")
            ?: "HRPartner"

        val summary = obj.optCleanString("summary", "excerpt").orEmpty()

        val attachments = mutableListOf<HrpBulletinAttachment>()
        val attachmentsArr = obj.optJSONArray("attachments")
        if (attachmentsArr != null) {
            for (i in 0 until attachmentsArr.length()) {
                val attObj = attachmentsArr.optJSONObject(i) ?: continue
                attachments += parseAttachmentObject(attObj, index = i)
            }
        }

        val contentSections = mutableListOf<String>()
        val sectionsArr = obj.optJSONArray("content_sections") ?: obj.optJSONArray("sections")
        if (sectionsArr != null) {
            for (i in 0 until sectionsArr.length()) {
                val secItem = sectionsArr.opt(i)
                val secText = when (secItem) {
                    is JSONObject -> secItem.optString("body")
                        .ifBlank { secItem.optString("text") }
                        .ifBlank { secItem.optString("content") }
                        .trim()
                    is String -> secItem.trim()
                    else -> secItem?.toString()?.trim().orEmpty()
                }
                if (secText.isNotEmpty()) {
                    contentSections += secText
                }
            }
        }
        if (contentSections.isEmpty()) {
            val rawBody = obj.optString("content")
                .ifBlank { obj.optString("body") }
                .trim()
            if (rawBody.isNotEmpty()) {
                rawBody.split("\n\n", "\r\n\r\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { contentSections += it }
            }
        }
        if (contentSections.isEmpty() && summary.isNotEmpty()) {
            contentSections += summary
        }

        val derivedImageCount = attachments.count { it.kind == HrpBulletinAttachmentKind.IMAGE }
        val derivedPdfCount = attachments.count { it.kind == HrpBulletinAttachmentKind.PDF }
        val imageCount = if (obj.has("image_count") && !obj.isNull("image_count")) {
            obj.optInt("image_count", derivedImageCount).coerceAtLeast(0)
        } else {
            derivedImageCount
        }
        val pdfCount = if (obj.has("pdf_count") && !obj.isNull("pdf_count")) {
            obj.optInt("pdf_count", derivedPdfCount).coerceAtLeast(0)
        } else {
            derivedPdfCount
        }

        return HrpBulletinItem(
            bulletinId = bulletinId,
            title = title,
            category = category,
            categoryDisplay = categoryDisplay,
            badgeText = badgeText,
            isPinned = isPinned,
            isRead = isRead,
            publishedAtIso = publishedAtIso,
            publishedDateDisplay = publishedDateDisplay,
            authorDisplay = authorDisplay,
            summary = summary,
            contentSections = contentSections,
            imageCount = imageCount,
            pdfCount = pdfCount,
            attachments = attachments
        )
    }

    internal fun parseAttachmentObject(obj: JSONObject, index: Int = 0): HrpBulletinAttachment {
        val attachmentId = obj.optCleanString("attachment_id", "id") ?: "att-$index"
        val fileName = obj.optCleanString("file_name", "filename") ?: "$attachmentId.bin"
        val mimeType = obj.optCleanString("mime_type", "content_type")
            ?: if (fileName.lowercase(Locale.ROOT).endsWith(".pdf")) "application/pdf" else "image/png"
        val kind = HrpBulletinAttachmentKind.fromWireKeyOrMime(
            rawKind = obj.optCleanString("kind", "type").orEmpty(),
            mimeType = mimeType,
            fileName = fileName
        )
        val title = obj.optCleanString("title", "display_name") ?: fileName
        val fileSizeBytes = obj.optLong("file_size_bytes", obj.optLong("size_bytes", 0L)).coerceAtLeast(0L)
        val url = obj.optCleanString("url", "download_url").orEmpty()
        val thumbnailUrl = obj.optCleanString("thumbnail_url")
        val width = if (obj.has("width") && !obj.isNull("width")) obj.optInt("width").takeIf { it > 0 } else null
        val height = if (obj.has("height") && !obj.isNull("height")) obj.optInt("height").takeIf { it > 0 } else null
        val pageCount = if (obj.has("page_count") && !obj.isNull("page_count")) obj.optInt("page_count").takeIf { it > 0 } else null
        val sha256 = obj.optCleanString("sha256")?.lowercase(Locale.ROOT)

        return HrpBulletinAttachment(
            attachmentId = attachmentId,
            kind = kind,
            fileName = fileName,
            title = title,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            url = url,
            thumbnailUrl = thumbnailUrl,
            width = width,
            height = height,
            pageCount = pageCount,
            sha256 = sha256
        )
    }

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    fun computeFileSha256Hex(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            val digest = md.digest()
            buildString(digest.size * 2) {
                for (b in digest) {
                    append(String.format(Locale.US, "%02x", b))
                }
            }
        }.getOrNull()
    }

    fun resolveCachedAttachmentFile(context: Context, attachment: HrpBulletinAttachment): File {
        val cacheDir = File(context.cacheDir, "bulletins").apply { mkdirs() }
        val safeName = attachment.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment.bin" }
        val safeId = attachment.attachmentId.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "att" }
        return File(cacheDir, "${safeId}_$safeName")
    }

    fun isCachedAttachmentValid(file: File, expectedSha256: String?): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false
        val cleanExpected = expectedSha256?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (cleanExpected.isEmpty()) return true
        val actual = computeFileSha256Hex(file)?.lowercase(Locale.ROOT) ?: return false
        return actual == cleanExpected
    }

    fun storeAttachmentBytesInCache(
        context: Context,
        attachment: HrpBulletinAttachment,
        bytes: ByteArray
    ): File {
        val expectedSha = attachment.sha256?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (expectedSha.isNotEmpty()) {
            val actualSha = computeSha256Hex(bytes)
            if (actualSha != expectedSha) {
                throw BackendApiException(
                    kind = BackendFailureKind.CONTRACT_INVALID,
                    statusCode = null,
                    backendCode = "BULLETIN_ATTACHMENT_HASH_MISMATCH",
                    message = "Tệp đính kèm không khớp mã kiểm tra toàn vẹn (SHA-256)."
                )
            }
        }
        val targetFile = resolveCachedAttachmentFile(context, attachment)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        FileOutputStream(tempFile).use { out ->
            out.write(bytes)
            out.flush()
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
        return targetFile
    }

    suspend fun ensureAttachmentLocalFile(
        context: Context,
        attachment: HrpBulletinAttachment,
        httpClient: BackendHttpClient? = null
    ): File = withContext(Dispatchers.IO) {
        val cachedFile = resolveCachedAttachmentFile(context, attachment)
        if (isCachedAttachmentValid(cachedFile, attachment.sha256)) {
            return@withContext cachedFile
        }
        if (attachment.url.startsWith("mockup://") || !BULLETIN_REMOTE_API_ENABLED) {
            return@withContext ensureMockupAttachmentFile(context, attachment)
        }
        val client = httpClient ?: throw BackendApiException(
            kind = BackendFailureKind.NETWORK,
            statusCode = null,
            backendCode = "BULLETIN_HTTP_CLIENT_MISSING",
            message = "Chưa khởi tạo kết nối tải tệp đính kèm."
        )
        val (downloadedBytes, _) = client.getBinary(attachment.url)
        storeAttachmentBytesInCache(context, attachment, downloadedBytes)
    }

    /**
     * Default mockup items aligned 100% with iOS (`v1.1.1 Build 163`).
     */
    val defaultMockupItems: List<HrpBulletinItem> = listOf(
        HrpBulletinItem(
            bulletinId = "hrp-bulletin-2026-11-13-hr-news",
            title = "Tin nhân sự ngày 13-11-2026",
            category = HrpBulletinCategory.HR_NEWS,
            categoryDisplay = HrpBulletinCategory.HR_NEWS.labelVi,
            badgeText = "Mới",
            isPinned = true,
            isRead = false,
            publishedAtIso = "2026-11-13T08:00:00Z",
            publishedDateDisplay = "13-11-2026",
            authorDisplay = "Ban Nhân sự & Tuyển dụng HRPartner",
            summary = "Cập nhật kế hoạch điều phối nhân sự các dự án trọng điểm tuần 2 tháng 11-2026, chuẩn hóa hồ sơ đầu vào và tiến độ tuyển dụng tại các KCN Vĩnh Phúc, Bắc Ninh.",
            contentSections = listOf(
                "1. Tiến độ nhân sự tuần 2 tháng 11-2026: Các tổ dự án tại KCN Khai Quang, KCN Bá Thiện 1 và KCN Thăng Long Vĩnh Phúc duy trì tỷ lệ đi làm ổn định trên 96%. Đội ngũ quản lý dự án phối hợp chặt chẽ cùng bộ phận Tuyển dụng để bổ sung kịp thời chỉ tiêu thời vụ và chính thức.",
                "2. Chuẩn hóa hồ sơ người lao động đầu vào: Khi tiếp nhận ứng viên mới tại mục Nhập mới hoặc cập nhật chứng từ tại mục Nhân sự, đề nghị đối chiếu đầy đủ CCCD gắn chip, ảnh chân dung rõ nét và thông tin tài khoản ngân hàng chính chủ nhằm đảm bảo tiến độ chi trả lương, tạm ứng.",
                "3. Phối hợp hỗ trợ người lao động tại nhà máy: Quản lý hiện trường chủ động nắm bắt tình hình làm việc ca ngày/ca đêm, hướng dẫn công nhân mới quy định an toàn lao động và liên hệ ngay Ban Nhân sự khi phát sinh vướng mắc."
            ),
            imageCount = 1,
            pdfCount = 0,
            attachments = listOf(
                HrpBulletinAttachment(
                    attachmentId = "att-hr-news-infographic-20261113",
                    kind = HrpBulletinAttachmentKind.IMAGE,
                    fileName = "infographic-nhan-su-13-11-2026.png",
                    title = "Sơ đồ điều phối & chỉ tiêu nhân sự các KCN ngày 13-11-2026",
                    mimeType = "image/png",
                    fileSizeBytes = 184_320L,
                    url = "mockup://bulletin/att-hr-news-infographic-20261113",
                    width = 1200,
                    height = 800
                )
            )
        ),
        HrpBulletinItem(
            bulletinId = "hrp-bulletin-2026-11-13-holiday-vn-culture",
            title = "Lịch nghỉ lễ ngày Văn hóa Việt Nam",
            category = HrpBulletinCategory.HOLIDAY_SCHEDULE,
            categoryDisplay = HrpBulletinCategory.HOLIDAY_SCHEDULE.labelVi,
            badgeText = "Lịch nghỉ lễ",
            isPinned = true,
            isRead = false,
            publishedAtIso = "2026-11-13T07:30:00Z",
            publishedDateDisplay = "13-11-2026",
            authorDisplay = "Phòng Hành chính - Nhân sự HRPartner",
            summary = "Thông báo lịch nghỉ lễ Ngày Văn hóa Việt Nam, phương án bố trí trực điều hành dự án tại các Khu công nghiệp và hướng dẫn chấm công trong ngày lễ.",
            contentSections = listOf(
                "1. Thời gian nghỉ lễ: Toàn thể cán bộ nhân viên khối Văn phòng và các bộ phận không thuộc diện trực ca chuyên trách được nghỉ lễ theo quy định. Các tổ dự án vận hành theo lịch sản xuất đặc thù của nhà máy khách hàng sẽ thực hiện bố trí ca trực luân phiên.",
                "2. Chế độ chấm công & tính lương ngày lễ: Nhân sự và công nhân làm việc trong ngày lễ theo lịch phân công được hệ thống ghi nhận hệ số công ngày lễ theo đúng chính sách hiện hành của HRPartner. Vui lòng thực hiện chấm công GPS kèm ảnh chụp tại điểm làm việc đúng khung giờ ca.",
                "3. Đầu mối trực hỗ trợ 24/7: Các Trưởng nhóm và Quản lý dự án trực ca theo dõi danh sách phân công tại tệp đính kèm bên dưới để kịp thời phối hợp điều hành tại từng KCN."
            ),
            imageCount = 1,
            pdfCount = 1,
            attachments = listOf(
                HrpBulletinAttachment(
                    attachmentId = "att-holiday-vn-culture-pdf-2026",
                    kind = HrpBulletinAttachmentKind.PDF,
                    fileName = "Thong-bao-nghi-le-Van-hoa-VN-2026.pdf",
                    title = "Công văn thông báo lịch nghỉ lễ Ngày Văn hóa Việt Nam 2026",
                    mimeType = "application/pdf",
                    fileSizeBytes = 245_760L,
                    url = "mockup://bulletin/att-holiday-vn-culture-pdf-2026",
                    pageCount = 2
                ),
                HrpBulletinAttachment(
                    attachmentId = "att-holiday-duty-roster-img-20261113",
                    kind = HrpBulletinAttachmentKind.IMAGE,
                    fileName = "lich-truc-kcn-13-11.png",
                    title = "Bảng phân công trực điều hành các KCN dịp lễ",
                    mimeType = "image/png",
                    fileSizeBytes = 162_800L,
                    url = "mockup://bulletin/att-holiday-duty-roster-img-20261113",
                    width = 1200,
                    height = 800
                )
            )
        ),
        HrpBulletinItem(
            bulletinId = "hrp-bulletin-2026-11-10-attendance-policy",
            title = "Hướng dẫn rà soát bảng công & giải trình chấm công tháng 11-2026",
            category = HrpBulletinCategory.INTERNAL_POLICY,
            categoryDisplay = HrpBulletinCategory.INTERNAL_POLICY.labelVi,
            badgeText = null,
            isPinned = false,
            isRead = false,
            publishedAtIso = "2026-11-10T09:00:00Z",
            publishedDateDisplay = "10-11-2026",
            authorDisplay = "Ban Vận hành HRPartner",
            summary = "Quy trình đối soát nhật ký chấm công theo ngày, thời hạn gửi giải trình bổ sung và lưu ý đồng bộ giờ chuẩn máy chủ trước khi chốt kỳ công.",
            contentSections = listOf(
                "1. Tra cứu nhật ký chấm công theo từng ngày: Nhân sự mở mục Chấm công, chọn ngày cần kiểm tra trên thanh lịch tuần để đối chiếu giờ Vào ca / Ra ca và trạng thái ghi nhận trên hệ thống.",
                "2. Thời hạn gửi giải trình chấm công: Trường hợp quên chấm công hoặc thiết bị mất kết nối tại thời điểm qua cổng nhà máy, nhân sự chuyển sang tab Giải trình, chọn đúng ngày phát sinh và gửi lý do kèm người duyệt trước thời điểm khóa sổ.",
                "3. Phê duyệt kịp thời trên ứng dụng: Quản lý trực tiếp kiểm tra danh sách chờ duyệt hằng ngày để đảm bảo dữ liệu bảng công và báo cáo công việc luôn chính xác, minh bạch."
            ),
            imageCount = 0,
            pdfCount = 1,
            attachments = listOf(
                HrpBulletinAttachment(
                    attachmentId = "att-attendance-guide-pdf-202611",
                    kind = HrpBulletinAttachmentKind.PDF,
                    fileName = "Huong-dan-ra-soat-bang-cong-va-giai-trinh-T11-2026.pdf",
                    title = "Tài liệu hướng dẫn rà soát bảng công & giải trình tháng 11-2026",
                    mimeType = "application/pdf",
                    fileSizeBytes = 156_400L,
                    url = "mockup://bulletin/att-attendance-guide-pdf-202611",
                    pageCount = 1
                )
            )
        )
    )

    /**
     * Generates lightweight, crisp sample `.pdf` (via `PdfDocument`) or `.png` (via `Bitmap` + `Canvas`)
     * into `context.cacheDir/bulletin_mockups/` on demand when the user opens a mockup attachment.
     */
    fun ensureMockupAttachmentFile(context: Context, attachment: HrpBulletinAttachment): File {
        val mockupDir = File(context.cacheDir, "bulletin_mockups").apply { mkdirs() }
        val safeFileName = attachment.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "${attachment.attachmentId}.${if (attachment.kind == HrpBulletinAttachmentKind.PDF) "pdf" else "png"}" }
        val outFile = File(mockupDir, safeFileName)
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile
        }
        return when (attachment.kind) {
            HrpBulletinAttachmentKind.PDF -> generateSamplePdfFile(outFile, attachment)
            HrpBulletinAttachmentKind.IMAGE -> generateSampleImageFile(outFile, attachment)
        }
    }

    private fun generateSamplePdfFile(outFile: File, attachment: HrpBulletinAttachment): File {
        val totalPages = (attachment.pageCount ?: 1).coerceIn(1, 5)
        val pageWidth = 1240
        val pageHeight = 1754
        val pdfDocument = PdfDocument()
        try {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#EA580C") }
            val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#0F172A") }
            val mutedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#475569")
                textSize = 26f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 30f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val docTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#C2410C")
                textSize = 42f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E3A8A")
                textSize = 32f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E293B")
                textSize = 28f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#DC2626")
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val stampTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#DC2626")
                textSize = 24f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            for (pageIndex in 0 until totalPages) {
                val pageNumber = pageIndex + 1
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // White page canvas & top orange brand bar
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 24f, brandPaint)

                // Official header block
                canvas.drawText("CÔNG TY CỔ PHẦN HRPARTNER VIỆT NAM", 80f, 100f, headerTitlePaint)
                canvas.drawText("Số: 112/2026/TB-HRP", 80f, 142f, mutedTextPaint)

                val rightHeaderPaint = Paint(headerTitlePaint).apply { textAlign = Paint.Align.RIGHT }
                val rightSubPaint = Paint(mutedTextPaint).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", pageWidth - 80f, 100f, rightHeaderPaint)
                canvas.drawText("Độc lập - Tự do - Hạnh phúc", pageWidth - 80f, 142f, rightSubPaint)
                canvas.drawText("Hà Nội, ngày 13 tháng 11 năm 2026", pageWidth - 80f, 184f, rightSubPaint)

                // Divider line
                canvas.drawLine(80f, 215f, pageWidth - 80f, 215f, borderPaint)

                if (pageNumber == 1) {
                    canvas.drawText("THÔNG BÁO NỘI BỘ HRPARTNER", pageWidth / 2f, 290f, docTitlePaint)
                    val cleanTitle = attachment.title.ifBlank { "Văn bản nội bộ HRPartner" }
                    drawWrappedText(
                        canvas = canvas,
                        text = cleanTitle,
                        x = 100f,
                        startY = 345f,
                        maxWidth = pageWidth - 200f,
                        lineHeight = 42f,
                        paint = Paint(sectionHeaderPaint).apply { color = AndroidColor.parseColor("#0F172A") }
                    )

                    val cardRect = RectF(80f, 430f, pageWidth - 80f, 1380f)
                    canvas.drawRoundRect(cardRect, 20f, 20f, boxFillPaint)
                    canvas.drawRoundRect(cardRect, 20f, 20f, borderPaint)

                    var cursorY = 495f
                    val paragraphs = if (attachment.fileName.contains("nghi-le", ignoreCase = true)) {
                        listOf(
                            "I. ĐỐI TƯỢNG & PHẠM VI ÁP DỤNG" to "Áp dụng đối với toàn bộ Cán bộ nhân viên khối Văn phòng, Quản lý dự án, Tuyển dụng viên và các Tổ dự án trực thuộc hệ thống HRPartner Việt Nam.",
                            "II. LỊCH NGHỈ LỄ & PHƯƠNG ÁN TRỰC ĐIỀU HÀNH" to "1. Khối Văn phòng nghỉ lễ theo quy định hiện hành.\n2. Khối Vận hành dự án tại các KCN Khai Quang, Bá Thiện 1, Thăng Long Vĩnh Phúc, Quế Võ và Yên Phong bố trí ca trực luân phiên đảm bảo hỗ trợ người lao động 24/7.",
                            "III. QUY ĐỊNH CHẤM CÔNG TRONG NGÀY LỄ" to "Nhân sự làm việc theo lịch trực thực hiện chấm công GPS kèm ảnh chụp thực tế trên ứng dụng HRPartner BI để hệ thống tự động ghi nhận phụ cấp và hệ số công ngày lễ."
                        )
                    } else {
                        listOf(
                            "I. QUY TRÌNH RÀ SOÁT NHẬT KÝ CHẤM CÔNG" to "1. Hằng ngày, nhân sự mở mục Chấm công trên ứng dụng HRPartner BI và chọn từng ngày trên thanh lịch tuần để đối chiếu giờ Vào ca / Ra ca.\n2. Chỉ truy vấn theo ngày đang chọn để phản hồi tức thì và đồng bộ chuẩn giờ máy chủ.",
                            "II. HƯỚNG DẪN GỬI GIẢI TRÌNH BỔ SUNG" to "Trường hợp quên chấm công hoặc lỗi kết nối tại cổng nhà máy, nhân sự chuyển sang tab Giải trình, chọn ngày phát sinh trên lịch tuần, nhập lý do chi tiết và chọn Quản lý phê duyệt.",
                            "III. THỜI HẠN KHÓA SỔ BẢNG CÔNG THÁNG 11-2026" to "Mọi yêu cầu giải trình chấm công và báo cáo công việc cần hoàn tất phê duyệt trước thời điểm chốt kỳ công hằng tháng."
                        )
                    }

                    for ((heading, content) in paragraphs) {
                        canvas.drawText(heading, 120f, cursorY, sectionHeaderPaint)
                        cursorY += 46f
                        cursorY = drawWrappedText(
                            canvas = canvas,
                            text = content,
                            x = 120f,
                            startY = cursorY,
                            maxWidth = pageWidth - 240f,
                            lineHeight = 42f,
                            paint = bodyPaint
                        ) + 44f
                    }
                } else {
                    canvas.drawText("PHỤ LỤC: LỊCH TRỰC & ĐẦU MỐI ĐIỀU HÀNH DỰ ÁN", pageWidth / 2f, 290f, docTitlePaint)
                    val tableRect = RectF(80f, 350f, pageWidth - 80f, 1120f)
                    canvas.drawRoundRect(tableRect, 20f, 20f, boxFillPaint)
                    canvas.drawRoundRect(tableRect, 20f, 20f, borderPaint)

                    val rows = listOf(
                        Triple("KCN Khai Quang (Vĩnh Phúc)", "Tổ Dự án 01 · Ca Ngày & Đêm", "Trực 24/7"),
                        Triple("KCN Bá Thiện 1 (Vĩnh Phúc)", "Tổ Dự án 02 · Ca Ngày & Đêm", "Trực 24/7"),
                        Triple("KCN Thăng Long Vĩnh Phúc", "Tổ Dự án 03 · Ca Hành chính", "Trực điều phối"),
                        Triple("KCN Quế Võ & Yên Phong", "Tổ Dự án Bắc Ninh · Ca Luân phiên", "Trực 24/7"),
                        Triple("Bộ phận Hỗ trợ Kỹ thuật BI", "Trực xử lý đồng bộ bảng công", "Hotline nội bộ")
                    )
                    var rowY = 430f
                    for ((park, team, mode) in rows) {
                        canvas.drawText(park, 120f, rowY, sectionHeaderPaint)
                        canvas.drawText("$team  ·  Chế độ: $mode", 120f, rowY + 42f, bodyPaint)
                        canvas.drawLine(120f, rowY + 72f, pageWidth - 120f, rowY + 72f, borderPaint)
                        rowY += 135f
                    }
                }

                // Official Red Seal & Signature on the last page
                if (pageNumber == totalPages) {
                    val sealCenterX = pageWidth - 290f
                    val sealCenterY = 1500f
                    canvas.drawCircle(sealCenterX, sealCenterY, 92f, stampPaint)
                    canvas.drawCircle(sealCenterX, sealCenterY, 80f, stampPaint)
                    canvas.drawText("HRPARTNER", sealCenterX, sealCenterY - 8f, stampTextPaint)
                    canvas.drawText("ĐÃ KÝ DUYỆT", sealCenterX, sealCenterY + 26f, stampTextPaint)
                    canvas.drawText("BAN GIÁM ĐỐC HRPARTNER", sealCenterX, sealCenterY - 125f, stampTextPaint)
                }

                // Page number footer
                val footerPaint = Paint(mutedTextPaint).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(
                    "Tài liệu nội bộ HRPartner · Trang $pageNumber / $totalPages",
                    pageWidth / 2f,
                    pageHeight - 55f,
                    footerPaint
                )
                navyPaint.alpha = 255
                pdfDocument.finishPage(page)
            }

            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
                out.flush()
            }
        } catch (_: Throwable) {
            writeMinimalPdfFallback(outFile, attachment, totalPages)
        } finally {
            runCatching { pdfDocument.close() }
        }
        if (!outFile.exists() || outFile.length() <= 512L) {
            writeMinimalPdfFallback(outFile, attachment, totalPages)
        }
        return outFile
    }

    private fun writeMinimalPdfFallback(
        outFile: File,
        attachment: HrpBulletinAttachment,
        totalPages: Int
    ) {
        val safeTitle = attachment.title.replace("(", "\\(").replace(")", "\\)")
        val safeFile = attachment.fileName.replace("(", "\\(").replace(")", "\\)")
        val pdfText = buildString {
            appendLine("%PDF-1.4")
            appendLine("% HRPartner Official Internal Bulletin Document")
            appendLine("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj")
            val kids = (1..totalPages).joinToString(" ") { "${it * 2 + 1} 0 R" }
            appendLine("2 0 obj << /Type /Pages /Kids [$kids] /Count $totalPages >> endobj")
            for (page in 1..totalPages) {
                val pageObjId = page * 2 + 1
                val streamObjId = page * 2 + 2
                val streamBody = buildString {
                    appendLine("BT")
                    appendLine("/F1 16 Tf 50 780 Td (CONG TY CO PHAN HRPARTNER VIET NAM) Tj")
                    appendLine("0 -28 Td ($safeTitle) Tj")
                    appendLine("0 -22 Td (Tep dinh kem: $safeFile - Trang $page / $totalPages) Tj")
                    appendLine("0 -22 Td (Tai lieu luu hanh noi bo tren ung dung HRPartner BI.) Tj")
                    appendLine("0 -22 Td (Ap dung cho Khoi Van phong va cac To Du an KCN Vinh Phuc, Bac Ninh.) Tj")
                    appendLine("0 -22 Td (Moi nhan su thuc hien cham cong GPS va bao cao cong viec dung quy dinh.) Tj")
                    appendLine("0 -22 Td (Ban Giam doc HRPartner Viet Nam da ky duyet va ban hanh.) Tj")
                    appendLine("ET")
                }
                appendLine(
                    "$pageObjId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents $streamObjId 0 R >> endobj"
                )
                appendLine("$streamObjId 0 obj << /Length ${streamBody.toByteArray(Charsets.UTF_8).size} >> stream")
                append(streamBody)
                appendLine("endstream endobj")
            }
            appendLine("trailer << /Root 1 0 R >>")
            appendLine("%%EOF")
        }
        outFile.writeBytes(pdfText.toByteArray(Charsets.UTF_8))
    }

    private fun generateSampleImageFile(outFile: File, attachment: HrpBulletinAttachment): File {
        val width = (attachment.width ?: 1200).coerceIn(600, 1600)
        val height = (attachment.height ?: 800).coerceIn(400, 1200)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#F8FAFC") }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#EA580C") }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val whiteBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val whiteSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#FFEDD5")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#0F172A")
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#EA580C")
            textSize = 44f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#334155")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), 140f, headerPaint)
        canvas.drawText("BẢN TIN NỘI BỘ HRPARTNER · INFOGRAPHIC", 48f, 66f, whiteBoldPaint)
        canvas.drawText(attachment.title.take(68), 48f, 108f, whiteSubPaint)

        val cards = if (attachment.fileName.contains("lich-truc", ignoreCase = true)) {
            listOf(
                Triple("KCN Khai Quang", "100% Quân số trực", "Đầu mối: Tổ Dự án 01 · Ca Ngày/Đêm"),
                Triple("KCN Bá Thiện 1", "100% Quân số trực", "Đầu mối: Tổ Dự án 02 · Ca Ngày/Đêm"),
                Triple("KCN Thăng Long VP", "98.5% Chấm công chuẩn", "Đầu mối: Tổ Dự án 03 · Hỗ trợ tại cổng"),
                Triple("Tổng đài Nội bộ HRP", "Trực hỗ trợ 24/7", "Phòng Hành chính - Nhân sự & Vận hành")
            )
        } else {
            listOf(
                Triple("Tỷ lệ đi làm tuần 2", "96.8%", "Ổn định tại các KCN Vĩnh Phúc & Bắc Ninh"),
                Triple("Hồ sơ chuẩn hóa CCCD", "100%", "Đối chiếu đầy đủ CCCD gắn chip & ngân hàng"),
                Triple("KCN Khai Quang & Bá Thiện", "Đạt chỉ tiêu", "Bổ sung kịp thời nhân sự thời vụ & chính thức"),
                Triple("Đồng bộ Chấm công BI", "Thời gian thực", "Xác thực giờ chuẩn máy chủ & toạ độ GPS")
            )
        }

        val colWidth = (width - 144f) / 2f
        val rowHeight = (height - 230f) / 2f
        cards.forEachIndexed { idx, (label, metric, desc) ->
            val col = idx % 2
            val row = idx / 2
            val left = 48f + col * (colWidth + 48f)
            val top = 175f + row * (rowHeight + 28f)
            val rect = RectF(left, top, left + colWidth, top + rowHeight - 12f)
            canvas.drawRoundRect(rect, 24f, 24f, cardPaint)
            canvas.drawRoundRect(rect, 24f, 24f, borderPaint)
            canvas.drawText(label, left + 28f, top + 58f, titlePaint)
            canvas.drawText(metric, left + 28f, top + 125f, metricPaint)
            canvas.drawText(desc, left + 28f, top + 178f, bodyPaint)
        }

        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        bitmap.recycle()
        return outFile
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float {
        var currentY = startY
        val paragraphs = text.split("\n")
        for (paragraph in paragraphs) {
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                currentY += lineHeight
                continue
            }
            var currentLine = words.first()
            for (i in 1 until words.size) {
                val candidate = "$currentLine ${words[i]}"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = candidate
                } else {
                    canvas.drawText(currentLine, x, currentY, paint)
                    currentY += lineHeight
                    currentLine = words[i]
                }
            }
            canvas.drawText(currentLine, x, currentY, paint)
            currentY += lineHeight
        }
        return currentY
    }
}

/**
 * Manages per-user (`appUserId`) bulletin read state in local `SharedPreferences`
 * and provides reactive bulletin items + unread counts while `BULLETIN_REMOTE_API_ENABLED == false`
 * (and seamlessly merges remote items when enabled in the future).
 */
class HrpBulletinManager(
    context: Context,
    prefsName: String = PREFS_NAME
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _readIdsVersionFlow = MutableStateFlow(0L)
    val readIdsVersionFlow: StateFlow<Long> = _readIdsVersionFlow.asStateFlow()

    fun getReadBulletinIds(appUserId: String?): Set<String> {
        val key = prefKeyForUser(appUserId)
        synchronized(lock) {
            val raw = prefs.getStringSet(key, emptySet()).orEmpty()
            return raw.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    fun getBulletinsForActor(
        appUserId: String?,
        category: HrpBulletinCategory = HrpBulletinCategory.ALL,
        seedItems: List<HrpBulletinItem> = HrpBulletinContract.defaultMockupItems
    ): List<HrpBulletinItem> {
        val readIds = getReadBulletinIds(appUserId)
        val merged = HrpBulletinContract.sortBulletins(
            seedItems.map { item ->
                item.copy(isRead = item.isRead || readIds.contains(item.bulletinId))
            }
        )
        return if (category == HrpBulletinCategory.ALL) {
            merged
        } else {
            merged.filter { it.category == category }
        }
    }

    fun unreadCountForActor(
        appUserId: String?,
        seedItems: List<HrpBulletinItem> = HrpBulletinContract.defaultMockupItems
    ): Int {
        val readIds = getReadBulletinIds(appUserId)
        return seedItems.count { !it.isRead && !readIds.contains(it.bulletinId) }
    }

    fun markBulletinRead(appUserId: String?, bulletinId: String) {
        val cleanId = bulletinId.trim()
        if (cleanId.isEmpty()) return
        val key = prefKeyForUser(appUserId)
        synchronized(lock) {
            val existing = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
            if (existing.add(cleanId)) {
                prefs.edit().putStringSet(key, existing).apply()
                _readIdsVersionFlow.value = _readIdsVersionFlow.value + 1L
            }
        }
    }

    fun clearAllForTest() {
        synchronized(lock) {
            prefs.edit().clear().commit()
            _readIdsVersionFlow.value = _readIdsVersionFlow.value + 1L
        }
    }

    private fun prefKeyForUser(appUserId: String?): String {
        val cleanUser = appUserId?.trim()?.ifEmpty { "anonymous" } ?: "anonymous"
        return "read_bulletin_ids_$cleanUser"
    }

    companion object {
        const val PREFS_NAME = "hrp_bulletin_read_state_prefs"

        @Volatile
        private var instance: HrpBulletinManager? = null

        fun getInstance(context: Context): HrpBulletinManager {
            return instance ?: synchronized(this) {
                instance ?: HrpBulletinManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
