package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * On-device Vietnamese CCCD / Căn cước QR parser & multi-engine decoder (Google ML Kit + ZXing).
 * Executes 100% locally on the Android device (independent of backend).
 *
 * Supports:
 * - 2021 chip-based CCCD (7 pipe-separated fields on front top-right):
 *   `cccd_12_digits|cmnd_9_digits|full_name|dob_ddmmyyyy|gender|address|issue_date_ddmmyyyy`
 * - 2024+ Căn cước under Luật Căn cước 2023 (7 to 12+ pipe-separated fields on back top-right, e.g. trailing `||||`):
 *   `026086006641|135197071|Nguyễn Đức Thuận|06091986|Nam|Tổ Dân Phố Chợ Cánh, Bình Nguyên, Phú Thọ|04022026||||`
 * - Standalone 12-digit CCCD QR payload.
 */
object CccdQrDecoder {

    private val CCCD_12_REGEX = Regex("^\\d{12}$")
    private val LEGACY_ID_REGEX = Regex("^\\d{9,12}$")
    private val COMPACT_DDMMYYYY_REGEX = Regex("^(\\d{2})(\\d{2})(\\d{4})$")
    private val SLASH_DDMMYYYY_REGEX = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")
    private val ISO_YYYYMMDD_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val COMBINING_MARKS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val MOJIBAKE_HINT_REGEX = Regex("[\\u00C2\\u00C3\\u00C4\\u00C5\\u00C6\\u00E1]")

    private val ZXING_HINTS: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.ALSO_INVERTED to true,
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

    /**
     * Repairs UTF-8 strings that were decoded as ISO-8859-1 when a QR code omits an ECI header.
     */
    internal fun repairUtf8MojibakeIfNeeded(raw: String): String {
        if (raw.isEmpty() || !MOJIBAKE_HINT_REGEX.containsMatchIn(raw)) return raw
        if (raw.any { it.code > 0xFF }) return raw
        return runCatching {
            val bytes = raw.toByteArray(Charsets.ISO_8859_1)
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains('\uFFFD')) raw else utf8
        }.getOrDefault(raw)
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
     * Parses raw text from a Vietnamese CCCD / Căn cước QR code.
     * Supports:
     * - 7-segment pipe-delimited payload (2021 CCCD)
     * - 8..15+ segment pipe-delimited payload (2024/2026 Căn cước with trailing `||||` or extra segments)
     * - Partial pipe-delimited payload starting with 12-digit CCCD
     * - Standalone 12-digit CCCD QR text
     */
    fun parseCccdQrPayload(rawText: String): CccdDecodedQrPayload? {
        val repaired = repairUtf8MojibakeIfNeeded(rawText.trim().removePrefix("\uFEFF"))
        if (repaired.isEmpty() || repaired.length > 4096) return null

        val parts = repaired.split("|")
        if (parts.size == 1) {
            val singleDigits = stripControlAndCollapseSpaces(repaired)
            if (CCCD_12_REGEX.matches(singleDigits) && singleDigits != "000000000000") {
                return CccdDecodedQrPayload(cccd12 = singleDigits)
            }
            return null
        }

        val cccd12 = stripControlAndCollapseSpaces(parts[0])
        if (!CCCD_12_REGEX.matches(cccd12) || cccd12 == "000000000000") {
            return null
        }

        val legacyRaw = parts.getOrNull(1)?.let { stripControlAndCollapseSpaces(it) }.orEmpty()
        val legacyId9 = legacyRaw.takeIf { LEGACY_ID_REGEX.matches(it) }

        val fullNameRaw = parts.getOrNull(2)?.let { stripControlAndCollapseSpaces(it) }.orEmpty()
        val fullName = fullNameRaw
            .takeIf { it.isNotEmpty() && !DirectEntryContract.isFormulaLikeCell(it) }
            ?.take(256)

        val dateOfBirthUi = parts.getOrNull(3)?.let { normalizeQrDateToUi(it) }
        val genderPair = parts.getOrNull(4)?.let { normalizeQrGender(it) }

        val addressRaw = parts.getOrNull(5)?.let { stripControlAndCollapseSpaces(it) }.orEmpty()
        val address = addressRaw
            .takeIf { it.isNotEmpty() && !DirectEntryContract.isFormulaLikeCell(it) }
            ?.take(1024)

        val issueDateUi = parts.getOrNull(6)?.let { normalizeQrDateToUi(it) }

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

    internal fun parseFromMlKitBarcode(barcode: Barcode): CccdDecodedQrPayload? {
        barcode.rawValue?.let { raw ->
            parseCccdQrPayload(raw)?.let { return it }
        }
        barcode.rawBytes?.let { bytes ->
            val utf8 = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
            if (!utf8.isNullOrBlank()) {
                parseCccdQrPayload(utf8)?.let { return it }
            }
        }
        barcode.displayValue?.let { display ->
            parseCccdQrPayload(display)?.let { return it }
        }
        return null
    }

    /**
     * Applies decoded CCCD QR fields to a `NewWorkerEntryDraft`.
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

        // Contrast-stretched grayscale pass for low-contrast / guilloche background cards
        var minLum = 255
        var maxLum = 0
        val lumArray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            lumArray[i] = lum
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val bwPixels = IntArray(w * h)
        if (maxLum > minLum + 20) {
            val range = maxLum - minLum
            for (i in lumArray.indices) {
                val stretched = ((lumArray[i] - minLum) * 255 / range).coerceIn(0, 255)
                bwPixels[i] = (0xFF shl 24) or (stretched shl 16) or (stretched shl 8) or stretched
            }
            decodeFromRgbaPixels(bwPixels, w, h)?.let { return it }
        }

        if (thresholds.isNotEmpty()) {
            for (thresh in thresholds) {
                for (i in lumArray.indices) {
                    bwPixels[i] = if (lumArray[i] < thresh) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                decodeFromRgbaPixels(bwPixels, w, h)?.let { return it }
            }
        }
        return null
    }

    internal fun cropAndScale(
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

    internal fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap? {
        if (degrees % 360f == 0f) return src
        return runCatching {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }.getOrNull()
    }

    /**
     * Synchronous multi-pass ZXing QR decoder on an Android `Bitmap`:
     * 1. Full frame
     * 2. Top-right quadrant crop (2021 CCCD front & 2024 Căn cước back QR position)
     * 3. Tight top-right crop
     * 4. Center crop (camera viewfinder position)
     * 5. Top-left / Bottom-left / Bottom-right quadrants
     * 6. 90° / 180° / 270° rotated frames
     */
    fun decodeFromBitmap(bitmap: Bitmap): CccdDecodedQrPayload? {
        // Pass 1: Full frame
        tryDecodeBitmapPass(bitmap, intArrayOf(128))?.let { return it }

        // Pass 2: Top-right quadrant (standard CCCD front & Căn cước back QR position)
        cropAndScale(bitmap, 0.45f, 0.0f, 0.55f, 0.60f, 1000)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(115, 140, 165))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 3: Tight top-right crop
        cropAndScale(bitmap, 0.58f, 0.02f, 0.40f, 0.55f, 1000)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(110, 135, 160))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 4: Center crop (viewfinder region)
        cropAndScale(bitmap, 0.15f, 0.15f, 0.70f, 0.70f, 1000)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(115, 140, 165))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 5: Top-left quadrant
        cropAndScale(bitmap, 0.0f, 0.0f, 0.55f, 0.60f, 1000)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(120, 145))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 6: Rotated orientations (90, 180, 270 degrees) for phone camera photos taken sideways
        for (angle in floatArrayOf(90f, 180f, 270f)) {
            val rotated = rotateBitmap(bitmap, angle) ?: continue
            try {
                tryDecodeBitmapPass(rotated)?.let { return it }
                cropAndScale(rotated, 0.45f, 0.0f, 0.55f, 0.60f, 960)?.let { tr ->
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

    internal fun createMlKitScannerOrNull(): BarcodeScanner? {
        return runCatching {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(options)
        }.getOrNull()
    }

    internal suspend fun awaitBarcodesOrNull(task: Task<List<Barcode>>): List<Barcode>? =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { list ->
                if (cont.isActive) cont.resume(list)
            }.addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }.addOnCanceledListener {
                if (cont.isActive) cont.resume(null)
            }
        }

    private suspend fun tryMlKitOnBitmap(scanner: BarcodeScanner, bitmap: Bitmap, rotationDegrees: Int = 0): CccdDecodedQrPayload? {
        val image = runCatching { InputImage.fromBitmap(bitmap, rotationDegrees) }.getOrNull() ?: return null
        val barcodes = runCatching { awaitBarcodesOrNull(scanner.process(image)) }.getOrNull().orEmpty()
        for (barcode in barcodes) {
            parseFromMlKitBarcode(barcode)?.let { return it }
        }
        return null
    }

    /**
     * Asynchronous multi-engine decoder (Google ML Kit Barcode Scanning + Multi-pass ZXing fallback).
     */
    suspend fun decodeFromBitmapAsync(bitmap: Bitmap): CccdDecodedQrPayload? = withContext(Dispatchers.Default) {
        val scanner = createMlKitScannerOrNull()
        if (scanner != null) {
            try {
                // 1. Full frame across orientations
                for (rot in intArrayOf(0, 90, 180, 270)) {
                    tryMlKitOnBitmap(scanner, bitmap, rot)?.let { return@withContext it }
                }

                // 2. Top-right quadrant upscaled (standard CCCD/Căn cước QR corner)
                val cropSpecs = arrayOf(
                    floatArrayOf(0.45f, 0.0f, 0.55f, 0.60f),
                    floatArrayOf(0.55f, 0.0f, 0.45f, 0.55f),
                    floatArrayOf(0.15f, 0.15f, 0.70f, 0.70f),
                    floatArrayOf(0.22f, 0.22f, 0.56f, 0.56f),
                    floatArrayOf(0.0f, 0.0f, 0.55f, 0.60f),
                    floatArrayOf(0.45f, 0.40f, 0.55f, 0.60f),
                    floatArrayOf(0.0f, 0.40f, 0.55f, 0.60f)
                )
                for (spec in cropSpecs) {
                    val cropped = cropAndScale(bitmap, spec[0], spec[1], spec[2], spec[3], 1200) ?: continue
                    try {
                        tryMlKitOnBitmap(scanner, cropped, 0)?.let { return@withContext it }
                    } finally {
                        if (cropped !== bitmap) cropped.recycle()
                    }
                }
            } finally {
                runCatching { scanner.close() }
            }
        }

        // Fallback to multi-pass ZXing
        decodeFromBitmap(bitmap)
    }

    fun decodeFromImageBytes(imageBytes: ByteArray): CccdDecodedQrPayload? {
        if (imageBytes.isEmpty()) return null
        val bmp = decodeBitmapFromBytes(imageBytes) ?: return null
        return try {
            decodeFromBitmap(bmp)
        } finally {
            bmp.recycle()
        }
    }

    internal fun decodeBitmapFromBytes(imageBytes: ByteArray, maxDimension: Int = 2560): Bitmap? {
        if (imageBytes.isEmpty()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOpts.outWidth / (sampleSize * 2) >= maxDimension &&
            boundsOpts.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts)
    }

    suspend fun decodeFromUri(context: Context, uri: Uri): CccdDecodedQrPayload? = withContext(Dispatchers.IO) {
        // 1. Try ML Kit directly from Uri (handles EXIF orientation metadata natively)
        val scanner = createMlKitScannerOrNull()
        if (scanner != null) {
            try {
                val inputImage = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
                if (inputImage != null) {
                    val barcodes = runCatching { awaitBarcodesOrNull(scanner.process(inputImage)) }.getOrNull().orEmpty()
                    for (barcode in barcodes) {
                        parseFromMlKitBarcode(barcode)?.let { return@withContext it }
                    }
                }
            } finally {
                runCatching { scanner.close() }
            }
        }

        // 2. Decode full bitmap and run multi-crop ML Kit + ZXing passes
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val bmp = decodeBitmapFromBytes(bytes) ?: return@withContext null
            try {
                decodeFromBitmapAsync(bmp)
            } finally {
                bmp.recycle()
            }
        }.getOrNull()
    }
}
