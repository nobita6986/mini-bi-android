package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * On-device Vietnamese CCCD QR parser & multi-pass ZXing decoder.
 * Synchronized with Backend `src/lib/direct-entry/cccd-qr-contract.ts` and `cccd-qr-decoder.ts`.
 *
 * Standard Vietnamese chip-based CCCD QR payload format (7 pipe-separated fields):
 *   `cccd_12_digits|cmnd_9_digits|full_name|dob_ddmmyyyy|gender|address|issue_date_ddmmyyyy`
 */
object CccdQrDecoder {

    private val CCCD_12_REGEX = Regex("^\\d{12}$")
    private val LEGACY_ID_REGEX = Regex("^\\d{9,12}$")
    private val COMPACT_DDMMYYYY_REGEX = Regex("^(\\d{2})(\\d{2})(\\d{4})$")
    private val SLASH_DDMMYYYY_REGEX = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")
    private val ISO_YYYYMMDD_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val COMBINING_MARKS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private val ZXING_HINTS: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    private fun stripControlAndCollapseSpaces(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val code = ch.code
            if (code in 0..31 || code == 127) {
                sb.append(' ')
            } else {
                sb.append(ch)
            }
        }
        return sb.toString().replace(MULTI_SPACE_REGEX, " ").trim()
    }

    private fun isValidCalendarDay(day: Int, month: Int, year: Int): Boolean {
        if (year !in 1900..2100 || month !in 1..12 || day !in 1..31) return false
        return try {
            val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
            }
            cal.timeInMillis
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun normalizeQrDateToUi(raw: String): String? {
        val trimmed = stripControlAndCollapseSpaces(raw)
        if (trimmed.isEmpty()) return null

        COMPACT_DDMMYYYY_REGEX.matchEntire(trimmed)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            if (!isValidCalendarDay(d, mo, y)) return null
            return "%02d/%02d/%04d".format(Locale.US, d, mo, y)
        }

        SLASH_DDMMYYYY_REGEX.matchEntire(trimmed)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            if (!isValidCalendarDay(d, mo, y)) return null
            return "%02d/%02d/%04d".format(Locale.US, d, mo, y)
        }

        ISO_YYYYMMDD_REGEX.matchEntire(trimmed)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            if (!isValidCalendarDay(d, mo, y)) return null
            return "%02d/%02d/%04d".format(Locale.US, d, mo, y)
        }

        return null
    }

    internal fun normalizeQrGender(raw: String): Pair<String, String>? {
        val cleaned = stripControlAndCollapseSpaces(raw)
        if (cleaned.isEmpty()) return null
        val folded = COMBINING_MARKS_REGEX.replace(
            Normalizer.normalize(cleaned, Normalizer.Form.NFD),
            ""
        ).replace('đ', 'd').replace('Đ', 'D').lowercase(Locale.ROOT)

        return when (folded) {
            "nam", "male", "m" -> "MALE" to "Nam"
            "nu", "female", "f" -> "FEMALE" to "Nữ"
            else -> null
        }
    }

    /**
     * Parses raw text from a Vietnamese CCCD QR code.
     * Supports both standard 7-segment pipe-delimited payload and standalone 12-digit CCCD QR text.
     */
    fun parseCccdQrPayload(rawText: String): CccdDecodedQrPayload? {
        val trimmed = rawText.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty() || trimmed.length > 2048) return null

        val parts = trimmed.split("|")
        if (parts.size < 7) {
            val singleDigits = stripControlAndCollapseSpaces(trimmed)
            if (CCCD_12_REGEX.matches(singleDigits) && singleDigits != "000000000000") {
                return CccdDecodedQrPayload(cccd12 = singleDigits)
            }
            return null
        }
        if (parts.size > 8) return null
        if (parts.size == 8 && stripControlAndCollapseSpaces(parts[7]).isNotEmpty()) {
            return null
        }

        val cccd12 = stripControlAndCollapseSpaces(parts[0])
        if (!CCCD_12_REGEX.matches(cccd12) || cccd12 == "000000000000") {
            return null
        }

        val legacyRaw = stripControlAndCollapseSpaces(parts[1])
        val legacyId9 = legacyRaw.takeIf { LEGACY_ID_REGEX.matches(it) }

        val fullNameRaw = stripControlAndCollapseSpaces(parts[2])
        val fullName = fullNameRaw
            .takeIf { it.isNotEmpty() && !DirectEntryContract.isFormulaLikeCell(it) }
            ?.take(256)

        val dateOfBirthUi = normalizeQrDateToUi(parts[3])
        val genderPair = normalizeQrGender(parts[4])

        val addressRaw = stripControlAndCollapseSpaces(parts[5])
        val address = addressRaw
            .takeIf { it.isNotEmpty() && !DirectEntryContract.isFormulaLikeCell(it) }
            ?.take(1024)

        val issueDateUi = normalizeQrDateToUi(parts[6])

        return CccdDecodedQrPayload(
            cccd12 = cccd12,
            legacyId9 = legacyId9,
            fullName = fullName,
            dateOfBirthUi = dateOfBirthUi,
            genderCanonical = genderPair?.first,
            genderUi = genderPair?.second,
            address = address,
            issueDateUi = issueDateUi
        )
    }

    /**
     * Applies decoded CCCD QR fields to a `NewWorkerEntryDraft`.
     * When `overwriteExisting` is true (e.g. user explicitly clicks "Quét QR CCCD"), all non-null
     * QR fields are applied. When false (e.g. background auto-decode on CCCD photo selection),
     * only currently blank fields are filled so user edits are never overwritten.
     */
    fun applyQrPayloadToDraft(
        draft: NewWorkerEntryDraft,
        qr: CccdDecodedQrPayload,
        overwriteExisting: Boolean = true
    ): NewWorkerEntryDraft {
        val nextCccd = if (overwriteExisting || draft.cccd.isBlank()) qr.cccd12 else draft.cccd
        val nextName = if (!qr.fullName.isNullOrBlank() && (overwriteExisting || draft.fullName.isBlank())) {
            qr.fullName
        } else {
            draft.fullName
        }
        val nextDob = if (!qr.dateOfBirthUi.isNullOrBlank() && (overwriteExisting || draft.birthDateUi.isBlank())) {
            qr.dateOfBirthUi
        } else {
            draft.birthDateUi
        }
        val nextGender = if (!qr.genderUi.isNullOrBlank() && (overwriteExisting || draft.genderUi.isBlank())) {
            qr.genderUi
        } else {
            draft.genderUi
        }
        val nextAddress = if (!qr.address.isNullOrBlank() && (overwriteExisting || draft.address.isBlank())) {
            qr.address
        } else {
            draft.address
        }
        val nextIssueDate = if (!qr.issueDateUi.isNullOrBlank() && (overwriteExisting || draft.cccdIssueDateUi.isBlank())) {
            qr.issueDateUi
        } else {
            draft.cccdIssueDateUi
        }
        val hasOptionalAutoFilled = !qr.dateOfBirthUi.isNullOrBlank() ||
            !qr.genderUi.isNullOrBlank() ||
            !qr.address.isNullOrBlank() ||
            !qr.issueDateUi.isNullOrBlank()

        return draft.copy(
            cccd = nextCccd,
            fullName = nextName,
            birthDateUi = nextDob,
            genderUi = nextGender,
            address = nextAddress,
            cccdIssueDateUi = nextIssueDate,
            showOptionalSection = draft.showOptionalSection || hasOptionalAutoFilled
        )
    }

    fun decodeFromRgbaPixels(pixels: IntArray, width: Int, height: Int): CccdDecodedQrPayload? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val reader = MultiFormatReader().apply { setHints(ZXING_HINTS) }
        val source = RGBLuminanceSource(width, height, pixels)

        // 1. Standard HybridBinarizer
        runCatching {
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            parseCccdQrPayload(result.text)?.let { return it }
        }
        reader.reset()

        // 2. GlobalHistogramBinarizer
        runCatching {
            val result = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
            parseCccdQrPayload(result.text)?.let { return it }
        }
        reader.reset()

        // 3. Inverted luminance
        runCatching {
            val inverted = source.invert()
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted)))
            parseCccdQrPayload(result.text)?.let { return it }
        }
        reader.reset()

        return null
    }

    private fun tryDecodeBitmapPass(bitmap: Bitmap, thresholds: IntArray = intArrayOf()): CccdDecodedQrPayload? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        decodeFromRgbaPixels(pixels, w, h)?.let { return it }

        if (thresholds.isNotEmpty()) {
            val bwPixels = IntArray(w * h)
            for (thresh in thresholds) {
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val lum = (r * 77 + g * 150 + b * 29) shr 8
                    bwPixels[i] = if (lum < thresh) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                decodeFromRgbaPixels(bwPixels, w, h)?.let { return it }
            }
        }
        return null
    }

    private fun cropAndScale(
        src: Bitmap,
        leftRatio: Float,
        topRatio: Float,
        widthRatio: Float,
        heightRatio: Float,
        targetLongEdge: Int
    ): Bitmap? {
        val x = (src.width * leftRatio).toInt().coerceIn(0, src.width - 1)
        val y = (src.height * topRatio).toInt().coerceIn(0, src.height - 1)
        val cw = (src.width * widthRatio).toInt().coerceIn(1, src.width - x)
        val ch = (src.height * heightRatio).toInt().coerceIn(1, src.height - y)
        if (cw < 16 || ch < 16) return null
        return runCatching {
            val cropped = Bitmap.createBitmap(src, x, y, cw, ch)
            val maxSide = maxOf(cw, ch)
            if (maxSide < targetLongEdge) {
                val scale = targetLongEdge.toFloat() / maxSide.toFloat()
                val sw = (cw * scale).toInt().coerceAtLeast(1)
                val sh = (ch * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(cropped, sw, sh, true)
                if (scaled !== cropped) cropped.recycle()
                scaled
            } else {
                cropped
            }
        }.getOrNull()
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap? {
        return runCatching {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }.getOrNull()
    }

    /**
     * Multi-pass QR decoder on an Android `Bitmap`:
     * 1. Full frame
     * 2. Top-right quadrant crop (where Vietnamese CCCD QR is located) + threshold passes
     * 3. Tight top-right crop + threshold passes
     * 4. Top-left quadrant crop (in case image is flipped/rotated)
     * 5. 90° / 180° / 270° rotated frames
     */
    fun decodeFromBitmap(bitmap: Bitmap): CccdDecodedQrPayload? {
        // Pass 1: Full frame
        tryDecodeBitmapPass(bitmap, intArrayOf(128))?.let { return it }

        // Pass 2: Top-right quadrant (standard CCCD front QR position)
        cropAndScale(bitmap, 0.45f, 0.0f, 0.55f, 0.55f, 960)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(115, 140, 165))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 3: Tight top-right crop
        cropAndScale(bitmap, 0.55f, 0.02f, 0.43f, 0.46f, 960)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(110, 135, 160))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 4: Top-left quadrant
        cropAndScale(bitmap, 0.0f, 0.0f, 0.55f, 0.55f, 960)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(120, 145))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 5: Rotated orientations (90, 180, 270 degrees) for phone camera photos taken sideways
        for (angle in floatArrayOf(90f, 180f, 270f)) {
            val rotated = rotateBitmap(bitmap, angle) ?: continue
            try {
                tryDecodeBitmapPass(rotated)?.let { return it }
                cropAndScale(rotated, 0.45f, 0.0f, 0.55f, 0.55f, 900)?.let { tr ->
                    try {
                        tryDecodeBitmapPass(tr, intArrayOf(125, 150))?.let { return it }
                    } finally {
                        if (tr !== rotated) tr.recycle()
                    }
                }
            } finally {
                if (rotated !== bitmap) rotated.recycle()
            }
        }

        return null
    }

    fun decodeFromImageBytes(imageBytes: ByteArray): CccdDecodedQrPayload? {
        if (imageBytes.isEmpty()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOpts.outWidth / (sampleSize * 2) >= 2000 && boundsOpts.outHeight / (sampleSize * 2) >= 2000) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts) ?: return null
        return try {
            decodeFromBitmap(bmp)
        } finally {
            bmp.recycle()
        }
    }

    suspend fun decodeFromUri(context: Context, uri: Uri): CccdDecodedQrPayload? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            decodeFromImageBytes(bytes)
        }.getOrNull()
    }
}
