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
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    private val CCCD_12_WORD_REGEX = Regex("\\b(\\d{12})\\b")
    private val FLEX_DMY_REGEX = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})\\b")
    private val VIET_NGAY_THANG_NAM_REGEX = Regex("ngay\\s+(\\d{1,2})\\s+thang\\s+(\\d{1,2})\\s+nam\\s+(\\d{4})")
    private val MRZ_CHEVRON_SPLIT_REGEX = Regex("<+")
    private val MRZ_LINE2_REGEX = Regex("^([0-9OQDILZSBG]{6})([0-9OQDILZSBG])([MF<])([0-9OQDILZSBG]{6})([0-9OQDILZSBG]).*VNM.*$")
    private val MRZ_LINE2_CANDIDATE_REGEX = Regex("^[0-9OQDILZSBG]{6}[0-9OQDILZSBG][MF<][0-9OQDILZSBG]{6,}.*")
    private val OCR_NO_DIGITS_REGEX = Regex(".*\\d.*")
    private val OCR_NON_ALPHA_ONLY_REGEX = Regex("^[\\p{L}\\s'.-]+$")
    private val OCR_DIACRITIC_REGEX = Regex("[\\u00C0-\\u024F\\u1E00-\\u1EFF]")

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

    /**
     * Suppresses the cyan/teal guilloche security pattern on 2021 chip CCCD cards.
     * Cyan pixels have high Green and Blue channels and low Red, so taking `max(G, B)`
     * merges teal patterning into white while keeping black QR ink dark, then stretches contrast.
     */
    internal fun buildGreenBlueStretchedPixels(pixels: IntArray): IntArray {
        val gbArray = IntArray(pixels.size)
        var minGb = 255
        var maxGb = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gb = maxOf(g, b)
            gbArray[i] = gb
            if (gb < minGb) minGb = gb
            if (gb > maxGb) maxGb = gb
        }
        val out = IntArray(pixels.size)
        val range = maxGb - minGb
        for (i in gbArray.indices) {
            val v = if (range > 10) {
                ((gbArray[i] - minGb) * 255 / range).coerceIn(0, 255)
            } else {
                gbArray[i]
            }
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return out
    }

    internal fun createGreenBlueEnhancedBitmap(src: Bitmap): Bitmap? {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val gbPixels = buildGreenBlueStretchedPixels(pixels)
            Bitmap.createBitmap(gbPixels, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    internal fun tryDecodeBitmapPass(bitmap: Bitmap, thresholds: IntArray = intArrayOf()): CccdDecodedQrPayload? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        decodeFromRgbaPixels(pixels, w, h)?.let { return it }

        // Green-Blue max(G, B) contrast-stretched pass to suppress 2021 CCCD cyan guilloche
        val gbPixels = buildGreenBlueStretchedPixels(pixels)
        decodeFromRgbaPixels(gbPixels, w, h)?.let { return it }

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
            for (thresh in thresholds) {
                for (i in gbPixels.indices) {
                    val v = gbPixels[i] and 0xFF
                    bwPixels[i] = if (v < thresh) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
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

        // Pass 5b: Bottom-Right quadrant (portrait / 90°/270° sideways CCCD photos before rotation)
        cropAndScale(bitmap, 0.45f, 0.40f, 0.55f, 0.60f, 1100)?.let { cropped ->
            try {
                tryDecodeBitmapPass(cropped, intArrayOf(120, 145))?.let { return it }
            } finally {
                if (cropped !== bitmap) cropped.recycle()
            }
        }

        // Pass 5c: Bottom-Left quadrant (portrait / 90°/270° sideways CCCD photos before rotation)
        cropAndScale(bitmap, 0.0f, 0.40f, 0.55f, 0.60f, 1100)?.let { cropped ->
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

    internal fun createMlKitTextRecognizerOrNull(): TextRecognizer? {
        return runCatching {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }

    internal suspend fun awaitTextOrNull(task: Task<Text>): Text? =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { recognized ->
                if (cont.isActive) cont.resume(recognized)
            }.addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }.addOnCanceledListener {
                if (cont.isActive) cont.resume(null)
            }
        }

    /**
     * Computes the ICAO Doc 9303 Modulo-10 check digit using repeating weights [7, 3, 1].
     * Returns -1 if any character is outside `[0-9A-Z<]`.
     */
    internal fun computeMrzCheckDigit(field: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        for (i in field.indices) {
            val ch = field[i]
            val value = when {
                ch in '0'..'9' -> ch - '0'
                ch in 'A'..'Z' -> (ch - 'A') + 10
                ch == '<' -> 0
                else -> return -1
            }
            sum += value * weights[i % 3]
        }
        return sum % 10
    }

    private fun fixMrzDigits(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                'O', 'Q', 'D' -> sb.append('0')
                'I', 'L', '|' -> sb.append('1')
                'Z' -> sb.append('2')
                'S' -> sb.append('5')
                'G' -> sb.append('6')
                'B' -> sb.append('8')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun foldVietnameseLower(raw: String): String {
        return COMBINING_MARKS_REGEX.replace(
            Normalizer.normalize(raw, Normalizer.Form.NFD),
            ""
        ).replace('đ', 'd').replace('Đ', 'D').lowercase(Locale.ROOT)
    }

    private fun normalizeCompactMrzLine(raw: String): String {
        return stripControlAndCollapseSpaces(raw)
            .uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace('«', '<')
            .replace('‹', '<')
    }

    private fun isMrzLikeLine(rawLine: String): Boolean {
        val compact = normalizeCompactMrzLine(rawLine)
        if (compact.length < 10) return false
        if (compact.startsWith("IDVNM")) return true
        val idVnmIdx = compact.indexOf("IDVNM")
        if (idVnmIdx >= 0 && compact.length - idVnmIdx >= 15) return true
        if (compact.contains("<<<")) return true
        if (MRZ_LINE2_REGEX.matches(compact)) return true
        if (MRZ_LINE2_CANDIDATE_REGEX.matches(compact) && compact.contains("VNM")) return true
        return false
    }

    private data class ParsedMrzBlock(
        val cccd12: String,
        val dateOfBirthUi: String?,
        val genderPair: Pair<String, String>?,
        val fullName: String?
    )

    private data class MrzParseOutcome(
        val block: ParsedMrzBlock?,
        val hasCorruptedMrz: Boolean
    )

    private fun deriveCenturyAndGenderFromCccd(cccd12: String, sexChar: Char): Pair<Int?, Pair<String, String>?> {
        val centuryAndGender = when (cccd12.getOrNull(3)) {
            '0' -> 1900 to ("MALE" to "Nam")
            '1' -> 1900 to ("FEMALE" to "Nữ")
            '2' -> 2000 to ("MALE" to "Nam")
            '3' -> 2000 to ("FEMALE" to "Nữ")
            '4' -> 2100 to ("MALE" to "Nam")
            '5' -> 2100 to ("FEMALE" to "Nữ")
            else -> null
        }
        if (centuryAndGender != null) {
            return centuryAndGender.first to centuryAndGender.second
        }
        val fallbackGender = when (sexChar) {
            'M' -> "MALE" to "Nam"
            'F' -> "FEMALE" to "Nữ"
            else -> null
        }
        return null to fallbackGender
    }

    private fun parseMrzBlock(lines: List<String>): MrzParseOutcome {
        val compactLines = lines.map { normalizeCompactMrzLine(it) }
        var line1Idx = -1
        var line1Sliced = ""
        for (i in compactLines.indices) {
            val idx = compactLines[i].indexOf("IDVNM")
            if (idx >= 0) {
                val slice = compactLines[i].substring(idx)
                if (slice.length >= 15) {
                    line1Idx = i
                    line1Sliced = slice
                    break
                }
            }
        }

        val line2CandidateIdx = compactLines.indexOfFirst { c ->
            !c.contains("IDVNM") && (MRZ_LINE2_REGEX.matches(c) || (MRZ_LINE2_CANDIDATE_REGEX.matches(c) && c.contains("VNM")))
        }

        if (line1Idx < 0) {
            return MrzParseOutcome(block = null, hasCorruptedMrz = line2CandidateIdx >= 0)
        }
        if (line1Sliced.length < 27) {
            return MrzParseOutcome(block = null, hasCorruptedMrz = true)
        }

        val doc9 = fixMrzDigits(line1Sliced.substring(5, 14))
        val docCheckChar = fixMrzDigits(line1Sliced.substring(14, 15))
        val cccd12 = fixMrzDigits(line1Sliced.substring(15, 27))
        if (!doc9.all { it in '0'..'9' } ||
            docCheckChar.length != 1 || docCheckChar[0] !in '0'..'9' ||
            !CCCD_12_REGEX.matches(cccd12) || cccd12 == "000000000000" ||
            computeMrzCheckDigit(doc9) != (docCheckChar[0] - '0') ||
            cccd12.takeLast(9) != doc9
        ) {
            return MrzParseOutcome(block = null, hasCorruptedMrz = true)
        }

        var dobUi: String? = null
        var genderPair: Pair<String, String>? = null
        var line2Idx = -1

        if (line2CandidateIdx >= 0) {
            val line2Compact = compactLines[line2CandidateIdx]
            val match = MRZ_LINE2_REGEX.matchEntire(line2Compact)
                ?: return MrzParseOutcome(block = null, hasCorruptedMrz = true)
            val dob6 = fixMrzDigits(match.groupValues[1])
            val dobCheck = fixMrzDigits(match.groupValues[2])
            val sexChar = match.groupValues[3].firstOrNull() ?: '<'
            if (!dob6.all { it in '0'..'9' } || dobCheck.length != 1 || dobCheck[0] !in '0'..'9' ||
                computeMrzCheckDigit(dob6) != (dobCheck[0] - '0')
            ) {
                return MrzParseOutcome(block = null, hasCorruptedMrz = true)
            }
            line2Idx = line2CandidateIdx
            val (century, derivedGender) = deriveCenturyAndGenderFromCccd(cccd12, sexChar)
            genderPair = derivedGender
            if (century != null) {
                val year = century + dob6.substring(0, 2).toInt()
                val month = dob6.substring(2, 4).toInt()
                val day = dob6.substring(4, 6).toInt()
                if (isValidCalendarDay(day, month, year)) {
                    dobUi = "%02d/%02d/%04d".format(Locale.US, day, month, year)
                }
            }
        } else {
            val (_, derivedGender) = deriveCenturyAndGenderFromCccd(cccd12, '<')
            genderPair = derivedGender
        }

        var mrzName: String? = null
        val nameSearchStart = if (line2Idx >= 0) line2Idx + 1 else line1Idx + 1
        for (i in nameSearchStart until compactLines.size) {
            val c = compactLines[i]
            if (c.contains("<<") && !c.startsWith("IDVNM") && c.all { it in 'A'..'Z' || it == '<' }) {
                val cleaned = c.replace(MRZ_CHEVRON_SPLIT_REGEX, " ")
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trim()
                if (cleaned.length >= 3 && cleaned.contains(' ') && !DirectEntryContract.isFormulaLikeCell(cleaned)) {
                    mrzName = cleaned.take(256)
                }
                break
            }
        }
        if (mrzName == null) {
            for (c in compactLines) {
                if (c.contains("<<") && !c.startsWith("IDVNM") && !c.contains("VNM") &&
                    c.all { it in 'A'..'Z' || it == '<' }
                ) {
                    val cleaned = c.replace(MRZ_CHEVRON_SPLIT_REGEX, " ")
                        .replace(MULTI_SPACE_REGEX, " ")
                        .trim()
                    if (cleaned.length >= 3 && cleaned.contains(' ') && !DirectEntryContract.isFormulaLikeCell(cleaned)) {
                        mrzName = cleaned.take(256)
                        break
                    }
                }
            }
        }

        return MrzParseOutcome(
            block = ParsedMrzBlock(
                cccd12 = cccd12,
                dateOfBirthUi = dobUi,
                genderPair = genderPair,
                fullName = mrzName
            ),
            hasCorruptedMrz = false
        )
    }

    private fun extractDateFromLineText(rawLine: String): String? {
        FLEX_DMY_REGEX.find(rawLine)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            if (isValidCalendarDay(d, mo, y)) {
                return "%02d/%02d/%04d".format(Locale.US, d, mo, y)
            }
        }
        val folded = foldVietnameseLower(rawLine)
        VIET_NGAY_THANG_NAM_REGEX.find(folded)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            if (isValidCalendarDay(d, mo, y)) {
                return "%02d/%02d/%04d".format(Locale.US, d, mo, y)
            }
        }
        return null
    }

    private fun isKnownOcrFieldLabel(foldedLine: String): Boolean {
        return foldedLine.contains("ho va ten") ||
            foldedLine.contains("full name") ||
            foldedLine.contains("ngay sinh") ||
            foldedLine.contains("date of birth") ||
            foldedLine.contains("gioi tinh") ||
            foldedLine.contains("quoc tich") ||
            foldedLine.contains("nationality") ||
            foldedLine.contains("que quan") ||
            foldedLine.contains("place of origin") ||
            foldedLine.contains("noi thuong tru") ||
            foldedLine.contains("place of residence") ||
            foldedLine.contains("co gia tri") ||
            foldedLine.contains("date of expiry") ||
            foldedLine.contains("dac diem") ||
            foldedLine.contains("personal identification") ||
            foldedLine.contains("ngay, thang, nam") ||
            foldedLine.contains("date, month, year") ||
            foldedLine.contains("ngay cap") ||
            foldedLine.contains("cuc truong") ||
            foldedLine.contains("can cuoc cong dan") ||
            foldedLine.contains("so / no") ||
            foldedLine.contains("so/no")
    }

    private fun extractAfterLabelOnSameLine(line: String, labelEndHintFolded: List<String>): String {
        val colonIdx = line.indexOf(':')
        if (colonIdx >= 0 && colonIdx + 1 < line.length) {
            return stripControlAndCollapseSpaces(line.substring(colonIdx + 1))
        }
        val folded = foldVietnameseLower(line)
        for (hint in labelEndHintFolded) {
            val idx = folded.indexOf(hint)
            if (idx >= 0) {
                val endIdx = idx + hint.length
                if (endIdx < line.length) {
                    val tail = stripControlAndCollapseSpaces(line.substring(endIdx).trimStart(':', '-', ' '))
                    if (tail.isNotEmpty()) return tail
                }
            }
        }
        return ""
    }

    /**
     * Parses ICAO Doc 9303 TD1 MRZ (`IDVNM...`) and Vietnamese front/back OCR text into a
     * `CccdDecodedQrPayload`.
     *
     * When `requireMrz = true` (live camera MRZ fallback), strictly requires a valid Modulo-10
     * MRZ block. When `requireMrz = false` (gallery/attachment OCR fallback), combines MRZ
     * (preferring Vietnamese accented full name from front OCR when available) or structured
     * front/back OCR fields, while returning `null` if any MRZ checksum is invalid and no valid
     * front `Số / No.` label supplies the CCCD number.
     */
    fun parseCccdFromRecognizedText(rawText: String, requireMrz: Boolean = false): CccdDecodedQrPayload? {
        if (rawText.isBlank()) return null
        val allLines = rawText.lines()
            .map { stripControlAndCollapseSpaces(it) }
            .filter { it.isNotEmpty() }
        if (allLines.isEmpty()) return null

        val mrzOutcome = parseMrzBlock(allLines)
        val mrz = mrzOutcome.block
        if (requireMrz && mrz == null) return null

        val nonMrzLines = allLines.filter { !isMrzLikeLine(it) }

        var ocrCccd12: String? = null
        var ocrCccdHadExplicitLabel = false
        for (i in nonMrzLines.indices) {
            val folded = foldVietnameseLower(nonMrzLines[i])
            if (folded.contains("so / no") || folded.contains("so/no") ||
                folded.startsWith("so:") || folded.startsWith("no.") || folded.startsWith("no:")
            ) {
                for (offset in 0..2) {
                    val candidateLine = nonMrzLines.getOrNull(i + offset) ?: break
                    val match = CCCD_12_WORD_REGEX.find(candidateLine)
                    if (match != null && match.groupValues[1] != "000000000000") {
                        ocrCccd12 = match.groupValues[1]
                        ocrCccdHadExplicitLabel = true
                        break
                    }
                }
                if (ocrCccd12 != null) break
            }
        }

        if (mrzOutcome.hasCorruptedMrz && ocrCccd12 == null) {
            return null
        }

        val hasDocumentContext = nonMrzLines.any { line ->
            val f = foldVietnameseLower(line)
            f.contains("can cuoc") || f.contains("cong dan") || f.contains("identity card") ||
                f.contains("ho va ten") || f.contains("full name") || f.contains("ngay sinh")
        }
        if (ocrCccd12 == null && !mrzOutcome.hasCorruptedMrz && hasDocumentContext) {
            for (line in nonMrzLines) {
                val match = CCCD_12_WORD_REGEX.find(line)
                if (match != null && match.groupValues[1] != "000000000000") {
                    ocrCccd12 = match.groupValues[1]
                    break
                }
            }
        }

        var ocrFullName: String? = null
        for (i in nonMrzLines.indices) {
            val folded = foldVietnameseLower(nonMrzLines[i])
            if (folded.contains("ho va ten") || folded.contains("full name")) {
                val sameLine = extractAfterLabelOnSameLine(nonMrzLines[i], listOf("full name", "ho va ten"))
                    .takeIf { candidate ->
                        candidate.length >= 3 &&
                            !OCR_NO_DIGITS_REGEX.matches(candidate) &&
                            OCR_NON_ALPHA_ONLY_REGEX.matches(candidate) &&
                            !isKnownOcrFieldLabel(foldVietnameseLower(candidate))
                    }
                if (sameLine != null) {
                    if (!DirectEntryContract.isFormulaLikeCell(sameLine)) {
                        ocrFullName = sameLine.take(256)
                    }
                    break
                }
                val nextLine = nonMrzLines.getOrNull(i + 1)?.trim().orEmpty()
                if (nextLine.length >= 3 &&
                    !OCR_NO_DIGITS_REGEX.matches(nextLine) &&
                    OCR_NON_ALPHA_ONLY_REGEX.matches(nextLine) &&
                    !isKnownOcrFieldLabel(foldVietnameseLower(nextLine)) &&
                    !DirectEntryContract.isFormulaLikeCell(nextLine)
                ) {
                    val secondLine = nonMrzLines.getOrNull(i + 2)?.trim().orEmpty()
                    val combined = if (secondLine.length in 2..30 &&
                        !OCR_NO_DIGITS_REGEX.matches(secondLine) &&
                        OCR_NON_ALPHA_ONLY_REGEX.matches(secondLine) &&
                        !isKnownOcrFieldLabel(foldVietnameseLower(secondLine)) &&
                        !nextLine.contains(' ')
                    ) {
                        "$nextLine $secondLine"
                    } else {
                        nextLine
                    }
                    ocrFullName = combined.take(256)
                    break
                }
            }
        }

        var ocrDobUi: String? = null
        for (i in nonMrzLines.indices) {
            val folded = foldVietnameseLower(nonMrzLines[i])
            if (folded.contains("ngay sinh") || folded.contains("date of birth")) {
                for (offset in 0..1) {
                    val line = nonMrzLines.getOrNull(i + offset) ?: break
                    val date = extractDateFromLineText(line)
                    if (date != null) {
                        ocrDobUi = date
                        break
                    }
                }
                if (ocrDobUi != null) break
            }
        }

        var ocrGenderPair: Pair<String, String>? = null
        for (i in nonMrzLines.indices) {
            val folded = foldVietnameseLower(nonMrzLines[i])
            if (folded.contains("gioi tinh") || Regex("\\bsex\\b").containsMatchIn(folded)) {
                val sameLineTail = extractAfterLabelOnSameLine(nonMrzLines[i], listOf("sex", "gioi tinh"))
                val candidates = listOf(sameLineTail, nonMrzLines.getOrNull(i + 1).orEmpty())
                for (cand in candidates) {
                    val words = cand.split(' ', '/', ',', ';').filter { it.isNotBlank() }
                    for (w in words) {
                        val parsed = normalizeQrGender(w)
                        if (parsed != null) {
                            ocrGenderPair = parsed
                            break
                        }
                    }
                    if (ocrGenderPair != null) break
                }
                if (ocrGenderPair != null) break
            }
        }

        fun extractAddressAfterKeyword(primaryKeys: List<String>, hintLabels: List<String>): String? {
            for (i in nonMrzLines.indices) {
                val folded = foldVietnameseLower(nonMrzLines[i])
                if (primaryKeys.any { folded.contains(it) }) {
                    val parts = mutableListOf<String>()
                    val tail = extractAfterLabelOnSameLine(nonMrzLines[i], hintLabels)
                    if (tail.isNotEmpty() && !isKnownOcrFieldLabel(foldVietnameseLower(tail))) {
                        parts.add(tail)
                    }
                    for (offset in 1..2) {
                        val next = nonMrzLines.getOrNull(i + offset) ?: break
                        val nextFolded = foldVietnameseLower(next)
                        if (isKnownOcrFieldLabel(nextFolded)) break
                        parts.add(next)
                    }
                    val joined = parts.joinToString(", ")
                        .replace(", ,", ",")
                        .replace(MULTI_SPACE_REGEX, " ")
                        .trim()
                        .trimEnd(',')
                    if (joined.length >= 4 && !DirectEntryContract.isFormulaLikeCell(joined)) {
                        return joined.take(1024)
                    }
                }
            }
            return null
        }

        val ocrAddress = extractAddressAfterKeyword(
            primaryKeys = listOf("noi thuong tru", "place of residence"),
            hintLabels = listOf("place of residence", "noi thuong tru")
        ) ?: extractAddressAfterKeyword(
            primaryKeys = listOf("que quan", "place of origin"),
            hintLabels = listOf("place of origin", "que quan")
        )

        var ocrIssueDateUi: String? = null
        val dobForExclusion = mrz?.dateOfBirthUi ?: ocrDobUi
        for (i in nonMrzLines.indices) {
            val folded = foldVietnameseLower(nonMrzLines[i])
            if (folded.contains("co gia tri") || folded.contains("date of expiry")) continue
            if (folded.contains("date, month, year") ||
                folded.contains("ngay, thang, nam") ||
                folded.contains("ngay cap") ||
                folded.contains("date of issue")
            ) {
                for (offset in listOf(0, 1, 2, -1)) {
                    val idx = i + offset
                    if (idx !in nonMrzLines.indices) continue
                    val candLine = nonMrzLines[idx]
                    val candFolded = foldVietnameseLower(candLine)
                    if (candFolded.contains("co gia tri") || candFolded.contains("date of expiry")) continue
                    val extracted = extractDateFromLineText(candLine)
                    if (extracted != null && extracted != dobForExclusion) {
                        ocrIssueDateUi = extracted
                        break
                    }
                }
                if (ocrIssueDateUi != null) break
            }
        }

        val finalCccd12 = mrz?.cccd12 ?: ocrCccd12 ?: return null
        if (mrz == null &&
            ocrFullName == null &&
            ocrDobUi == null &&
            ocrGenderPair == null &&
            ocrAddress == null &&
            ocrIssueDateUi == null &&
            !ocrCccdHadExplicitLabel
        ) {
            return null
        }

        val finalFullName = ocrFullName ?: mrz?.fullName
        val finalDobUi = mrz?.dateOfBirthUi ?: ocrDobUi
        val finalGenderPair = mrz?.genderPair ?: ocrGenderPair

        return CccdDecodedQrPayload(
            cccd12 = finalCccd12,
            fullName = finalFullName,
            dateOfBirthUi = finalDobUi,
            genderCanonical = finalGenderPair?.first,
            genderUi = finalGenderPair?.second,
            address = ocrAddress,
            issueDateUi = ocrIssueDateUi
        )
    }

    internal suspend fun decodeOcrFallbackFromBitmap(bitmap: Bitmap): CccdDecodedQrPayload? =
        withContext(Dispatchers.Default) {
            val recognizer = createMlKitTextRecognizerOrNull() ?: return@withContext null
            var bestPayload: CccdDecodedQrPayload? = null
            var bestScore = -1
            try {
                for (rot in intArrayOf(0, 90, 270)) {
                    val image = runCatching { InputImage.fromBitmap(bitmap, rot) }.getOrNull() ?: continue
                    val recognized = runCatching { awaitTextOrNull(recognizer.process(image)) }.getOrNull() ?: continue
                    val candidate = parseCccdFromRecognizedText(recognized.text, requireMrz = false) ?: continue
                    val score = listOfNotNull(
                        candidate.fullName,
                        candidate.dateOfBirthUi,
                        candidate.genderUi,
                        candidate.address,
                        candidate.issueDateUi
                    ).size * 2 + if (!candidate.fullName.isNullOrBlank() && OCR_DIACRITIC_REGEX.containsMatchIn(candidate.fullName)) 3 else 0
                    if (score > bestScore) {
                        bestScore = score
                        bestPayload = candidate
                    }
                    if (!candidate.fullName.isNullOrBlank() &&
                        OCR_DIACRITIC_REGEX.containsMatchIn(candidate.fullName) &&
                        !candidate.dateOfBirthUi.isNullOrBlank() &&
                        (!candidate.issueDateUi.isNullOrBlank() || !candidate.address.isNullOrBlank())
                    ) {
                        return@withContext candidate
                    }
                }
            } finally {
                runCatching { recognizer.close() }
            }
            bestPayload
        }

    /**
     * Asynchronous multi-engine decoder:
     * 1. Google ML Kit Barcode Scanning (full frame + crops + cyan-filtered crops)
     * 2. Multi-pass ZXing QR fallback (with Green-Blue `max(G, B)` channel + all 4 corners + rotations)
     * 3. ML Kit Text Recognition OCR fallback (ICAO 9303 TD1 MRZ + Vietnamese front/back text)
     */
    suspend fun decodeFromBitmapAsync(bitmap: Bitmap): CccdDecodedQrPayload? = withContext(Dispatchers.Default) {
        val scanner = createMlKitScannerOrNull()
        if (scanner != null) {
            try {
                // 1. Full frame across orientations
                for (rot in intArrayOf(0, 90, 180, 270)) {
                    tryMlKitOnBitmap(scanner, bitmap, rot)?.let { return@withContext it }
                }

                // 2. Corner and center crops upscaled (standard & sideways CCCD/Căn cước QR)
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
                        val gbCropped = createGreenBlueEnhancedBitmap(cropped)
                        if (gbCropped != null) {
                            try {
                                tryMlKitOnBitmap(scanner, gbCropped, 0)?.let { return@withContext it }
                            } finally {
                                if (gbCropped !== cropped) gbCropped.recycle()
                            }
                        }
                    } finally {
                        if (cropped !== bitmap) cropped.recycle()
                    }
                }
            } finally {
                runCatching { scanner.close() }
            }
        }

        // Fallback to multi-pass ZXing (includes max(G, B) + 4 quadrants + rotations)
        decodeFromBitmap(bitmap)?.let { return@withContext it }

        // Tier 2 fallback: MRZ (ICAO 9303) + Vietnamese OCR
        decodeOcrFallbackFromBitmap(bitmap)
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
