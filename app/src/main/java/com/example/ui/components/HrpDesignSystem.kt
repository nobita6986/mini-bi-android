package com.example.ui.components

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SemanticColors
import com.example.ui.theme.tabularNums
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object HrpDesignTokens {
    val RadiusXS: Dp = 8.dp
    val RadiusMD: Dp = 12.dp
    val RadiusCard: Dp = 16.dp
    val RadiusHero: Dp = 22.dp
    val CardPadding: Dp = 14.dp
}

object HrpHaptics {
    fun tick(hapticFeedback: HapticFeedback?) {
        if (hapticFeedback == null) return
        runCatching {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

/**
 * Single-layer crisp card surface aligned with iOS `hrpCardSurface`:
 * pure white surface in light mode, 16.dp rounded corners, 1.dp hairline border, and whisper-light elevation.
 */
@Composable
fun Modifier.hrpCardSurface(
    cornerRadius: Dp = HrpDesignTokens.RadiusCard,
    containerColor: Color? = null,
    borderColor: Color? = null,
    shadowElevation: Dp = 0.5.dp
): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    val isLightSurface = colorScheme.surface.luminance() > 0.5f
    val resolvedContainer = containerColor ?: if (isLightSurface) {
        SemanticColors.SurfaceLight
    } else {
        colorScheme.surface
    }
    val resolvedBorder = borderColor ?: if (isLightSurface) {
        SemanticColors.SurfaceBorder.copy(alpha = 0.85f)
    } else {
        colorScheme.outline.copy(alpha = 0.72f)
    }
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.04f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )
            } else {
                Modifier
            }
        )
        .background(color = resolvedContainer, shape = shape)
        .border(width = 1.dp, color = resolvedBorder, shape = shape)
        .clip(shape)
}

/**
 * Secondary inner tile aligned with iOS `hrpMutedTile`:
 * soft `SurfaceSubtle` fill (`#F1F5F9` in light mode), `12.dp` rounded corners, no heavy border.
 */
@Composable
fun Modifier.hrpMutedTile(
    cornerRadius: Dp = HrpDesignTokens.RadiusMD,
    containerColor: Color? = null
): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    val isLightSurface = colorScheme.surface.luminance() > 0.5f
    val resolvedContainer = containerColor ?: if (isLightSurface) {
        SemanticColors.SurfaceSubtle
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .background(color = resolvedContainer, shape = shape)
        .clip(shape)
}

/**
 * Subtle spring press scale (`0.98f`) synchronized with an InteractionSource.
 */
fun Modifier.hrpPressableScale(
    interactionSource: InteractionSource? = null,
    targetScale: Float = 0.98f,
    enabled: Boolean = true
): Modifier = composed {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) targetScale else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "hrp_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Unified status pill (`999.dp` capsule, bold `labelSmall`, optional leading icon).
 */
@Composable
fun HrpStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HrpStatusBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    HrpStatusBadge(
        text = text,
        containerColor = tint.copy(alpha = 0.14f),
        contentColor = tint,
        modifier = modifier,
        borderColor = tint.copy(alpha = 0.28f),
        icon = icon
    )
}

data class HrpAvatarPalette(
    val container: Color,
    val content: Color,
    val border: Color
)

object HrpInitialsAvatarDefaults {
    private val VI_LOCALE: Locale = Locale.forLanguageTag("vi-VN")
    private val PAREN_SUFFIX_REGEX = Regex("\\([^)]*\\)")
    private val WHITESPACE_REGEX = Regex("\\s+")

    val PastelPalettes: List<HrpAvatarPalette> = listOf(
        HrpAvatarPalette(
            container = Color(0xFFFFEDD5),
            content = Color(0xFFC2410C),
            border = Color(0xFFFDBA74)
        ),
        HrpAvatarPalette(
            container = Color(0xFFDBEAFE),
            content = Color(0xFF1D4ED8),
            border = Color(0xFF93C5FD)
        ),
        HrpAvatarPalette(
            container = Color(0xFFD1FAE5),
            content = Color(0xFF047857),
            border = Color(0xFF6EE7B7)
        ),
        HrpAvatarPalette(
            container = Color(0xFFEDE9FE),
            content = Color(0xFF6D28D9),
            border = Color(0xFFC4B5FD)
        ),
        HrpAvatarPalette(
            container = Color(0xFFE0F2FE),
            content = Color(0xFF0369A1),
            border = Color(0xFF7DD3FC)
        ),
        HrpAvatarPalette(
            container = Color(0xFFFEF3C7),
            content = Color(0xFFB45309),
            border = Color(0xFFFCD34D)
        ),
        HrpAvatarPalette(
            container = Color(0xFFFCE7F3),
            content = Color(0xFFBE185D),
            border = Color(0xFFF9A8D4)
        )
    )

    /**
     * Smart Vietnamese & username initials extractor aligned with iOS `HrpInitialsAvatar.extractInitials`:
     * - 3+ words: `"KIỀU VIỆT HÀ"` -> `"KH"`, `"Đỗ Đức Thuận"` -> `"ĐT"`
     * - 2 words: `"Lê Hoa"` -> `"LH"`
     * - 1 word / handle / email: `"nobita6986"` -> `"NO"`, `"Minh"` -> `"MI"`, `"ql.duan@hrpartner.vn"` -> `"QL"`
     * - Blank / empty: `""` -> `"NL"`
     */
    fun extractInitials(fullName: String): String {
        val rawTrimmed = fullName.trim()
        if (rawTrimmed.isEmpty()) return "NL"

        val withoutParen = PAREN_SUFFIX_REGEX.replace(rawTrimmed, " ").trim()
        val candidate = withoutParen.ifEmpty { rawTrimmed }

        val words = candidate
            .split(WHITESPACE_REGEX)
            .map { token -> token.trim { !it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

        if (words.isEmpty()) return "NL"

        if (words.size == 1) {
            val single = words[0]
            val handlePart = single.substringBefore('@').ifEmpty { single }
            val alnum = handlePart.filter { it.isLetterOrDigit() }
            val prefix = (alnum.ifEmpty { single }).take(2)
            return if (prefix.isEmpty()) "NL" else prefix.uppercase(VI_LOCALE)
        }

        val firstChar = words.first().firstOrNull()?.toString().orEmpty()
        val lastChar = words.last().firstOrNull()?.toString().orEmpty()
        val combined = (firstChar + lastChar).uppercase(VI_LOCALE)
        return combined.ifEmpty { "NL" }
    }

    fun paletteFor(fullName: String): HrpAvatarPalette {
        val normalized = fullName.trim().lowercase(VI_LOCALE)
        if (normalized.isEmpty()) return PastelPalettes.first()
        var hash = 0
        for (ch in normalized) {
            hash = 31 * hash + ch.code
        }
        val idx = abs(hash) % PastelPalettes.size
        return PastelPalettes[idx]
    }
}

/**
 * Deterministic pastel initials avatar supporting Vietnamese names and handles.
 */
@Composable
fun HrpInitialsAvatar(
    fullName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp
) {
    val initials = remember(fullName) { HrpInitialsAvatarDefaults.extractInitials(fullName) }
    val palette = remember(fullName) { HrpInitialsAvatarDefaults.paletteFor(fullName) }
    val effectiveRadius = minOf(cornerRadius, size * 0.34f)
    val shape = RoundedCornerShape(effectiveRadius)
    val fontSizeSp = (size.value * 0.35f).coerceIn(10f, 20f).sp

    Box(
        modifier = modifier
            .size(size)
            .background(color = palette.container, shape = shape)
            .border(width = 0.8.dp, color = palette.border.copy(alpha = 0.7f), shape = shape)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = palette.content,
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun shouldUseStaticSkeleton(): Boolean {
    if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
        return true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return try {
            !ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            true
        }
    }
    return false
}

/**
 * Cold-start skeleton placeholder card.
 *
 * CRITICAL ROBOLECTRIC GUARD:
 * In Jetpack Compose Robolectric tests, `rememberInfiniteTransition` prevents `waitForIdle()`
 * from settling and hangs until timeout. When running under Robolectric or with animators disabled,
 * this component renders static alpha placeholder blocks with zero infinite animations.
 */
@Composable
fun HrpSkeletonCard(
    modifier: Modifier = Modifier,
    lineCount: Int = 3,
    showAvatar: Boolean = false
) {
    val isRobolectric = remember { shouldUseStaticSkeleton() }
    val baseColor = SemanticColors.SurfaceBorder

    val brush: Brush = if (isRobolectric) {
        val staticColor = baseColor.copy(alpha = 0.55f)
        Brush.linearGradient(colors = listOf(staticColor, staticColor))
    } else {
        val transition = rememberInfiniteTransition(label = "hrp_skeleton_shimmer")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 900f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "hrp_skeleton_translate"
        )
        Brush.linearGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.42f),
                baseColor.copy(alpha = 0.82f),
                baseColor.copy(alpha = 0.42f)
            ),
            start = Offset(translateAnim - 300f, 0f),
            end = Offset(translateAnim, 180f)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showAvatar) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                if (showAvatar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.36f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(brush)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }

        val bodyLines = (lineCount - 1).coerceAtLeast(1)
        for (idx in 0 until bodyLines) {
            val widthFraction = when (idx) {
                bodyLines - 1 -> 0.72f
                else -> 0.94f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun HrpSkeletonCard(
    lines: Int,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false
) {
    HrpSkeletonCard(
        modifier = modifier,
        lineCount = lines,
        showAvatar = showAvatar
    )
}

/**
 * API-24-safe ISO `yyyy-MM-dd` calendar helpers (UTC noon arithmetic avoids DST/midnight shifts).
 */
object HrpIsoDateUtils {
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val UTC_TZ: TimeZone = TimeZone.getTimeZone("UTC")
    private val VN_TZ: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    val WEEKDAY_LABELS_VI: List<String> = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

    fun isValidIsoDate(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (!ISO_DATE_REGEX.matches(trimmed)) return false
        return parseUtcNoonCalendar(trimmed) != null
    }

    fun todayIsoVietnam(nowEpochMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(VN_TZ, Locale.US).apply { timeInMillis = nowEpochMs }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun parseUtcNoonCalendar(isoDate: String): Calendar? {
        val trimmed = isoDate.trim()
        if (!ISO_DATE_REGEX.matches(trimmed)) return null
        val parts = trimmed.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val day = parts.getOrNull(2)?.toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val cal = Calendar.getInstance(UTC_TZ, Locale.US).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }
        return try {
            cal.timeInMillis
            cal
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatCalendarIso(cal: Calendar): String = String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )

    fun mondayOfWeek(isoDate: String?, fallbackIso: String): String {
        val cal = (isoDate?.let { parseUtcNoonCalendar(it) }
            ?: parseUtcNoonCalendar(fallbackIso)
            ?: parseUtcNoonCalendar(todayIsoVietnam()))!!
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offsetFromMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -offsetFromMonday)
        return formatCalendarIso(cal)
    }

    fun addDays(isoDate: String, deltaDays: Int): String {
        val cal = parseUtcNoonCalendar(isoDate) ?: return isoDate
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return formatCalendarIso(cal)
    }

    fun dayOfMonth(isoDate: String): Int {
        val cal = parseUtcNoonCalendar(isoDate) ?: return 1
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    fun weekdayShortLabelVi(isoDate: String): String {
        val cal = parseUtcNoonCalendar(isoDate) ?: return "T2"
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            Calendar.SUNDAY -> "CN"
            else -> "T2"
        }
    }

    fun resolveSlidingWindowEnd(
        selectedIso: String?,
        currentWindowEnd: String?,
        todayIso: String
    ): String {
        if (selectedIso == null) {
            return if (currentWindowEnd != null && currentWindowEnd <= todayIso) {
                currentWindowEnd
            } else {
                todayIso
            }
        }
        val clampedSelected = minOf(selectedIso, todayIso)
        if (currentWindowEnd != null) {
            val safeEnd = minOf(currentWindowEnd, todayIso)
            val winStart = addDays(safeEnd, -6)
            if (clampedSelected in winStart..safeEnd) {
                return safeEnd
            }
        }
        return if (clampedSelected >= addDays(todayIso, -6)) {
            todayIso
        } else {
            clampedSelected
        }
    }

    fun formatMonthYearVi(isoDate: String): String {
        val cal = parseUtcNoonCalendar(isoDate) ?: return isoDate
        return String.format(
            Locale.US,
            "Tháng %02d, %04d",
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }

    fun splitYearMonthDay(isoDate: String, fallbackIso: String): Triple<Int, Int, Int> {
        val cal = (parseUtcNoonCalendar(isoDate)
            ?: parseUtcNoonCalendar(fallbackIso)
            ?: parseUtcNoonCalendar(todayIsoVietnam()))!!
        return Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun endOfDayLocalMillis(isoDate: String): Long? {
        val parts = isoDate.trim().split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val day = parts.getOrNull(2)?.toIntOrNull() ?: return null
        return runCatching {
            Calendar.getInstance().apply {
                set(year, month - 1, day, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        }.getOrNull()
    }
}

/**
 * Shared 7-day (`T2` .. `CN`) week strip date selector aligned with iOS `WeekStripDateSelector`.
 *
 * Supports both single-date selection (`showAllOption = false`) and list date filtering
 * (`showAllOption = true`, where `allDatesSelected = true` / `selectedDateIso = null` shows all records).
 */
@Composable
fun HrpWeekStripDateSelector(
    selectedDateIso: String?,
    todayIso: String,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowFutureDates: Boolean = false,
    showAllOption: Boolean = false,
    showTodayChip: Boolean = true,
    allDatesSelected: Boolean = selectedDateIso == null,
    onSelectAllDates: (() -> Unit)? = null,
    markedDates: Set<String> = emptySet(),
    wrapInCardSurface: Boolean = true,
    tagPrefix: String = "hrp_week_strip"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val safeTodayIso = remember(todayIso) {
        if (HrpIsoDateUtils.isValidIsoDate(todayIso)) todayIso.trim() else HrpIsoDateUtils.todayIsoVietnam()
    }
    val validSelectedIso = remember(selectedDateIso, safeTodayIso, allowFutureDates) {
        selectedDateIso?.trim()
            ?.takeIf { HrpIsoDateUtils.isValidIsoDate(it) }
            ?.let { if (!allowFutureDates && it > safeTodayIso) safeTodayIso else it }
    }

    var displayedMondayIso by remember {
        mutableStateOf(HrpIsoDateUtils.mondayOfWeek(validSelectedIso ?: safeTodayIso, safeTodayIso))
    }
    var windowEndIso by remember {
        mutableStateOf(
            HrpIsoDateUtils.resolveSlidingWindowEnd(
                selectedIso = validSelectedIso,
                currentWindowEnd = null,
                todayIso = safeTodayIso
            )
        )
    }

    LaunchedEffect(validSelectedIso, safeTodayIso, allowFutureDates) {
        if (allowFutureDates) {
            if (validSelectedIso != null) {
                displayedMondayIso = HrpIsoDateUtils.mondayOfWeek(validSelectedIso, safeTodayIso)
            }
        } else {
            windowEndIso = HrpIsoDateUtils.resolveSlidingWindowEnd(
                selectedIso = validSelectedIso,
                currentWindowEnd = windowEndIso,
                todayIso = safeTodayIso
            )
        }
    }

    val weekDates = remember(allowFutureDates, displayedMondayIso, windowEndIso) {
        if (allowFutureDates) {
            (0..6).map { offset -> HrpIsoDateUtils.addDays(displayedMondayIso, offset) }
        } else {
            (6 downTo 0).map { offset -> HrpIsoDateUtils.addDays(windowEndIso, -offset) }
        }
    }
    val headerAnchorIso = remember(allowFutureDates, displayedMondayIso, windowEndIso, validSelectedIso, weekDates) {
        if (validSelectedIso != null && validSelectedIso in weekDates) {
            validSelectedIso
        } else if (allowFutureDates) {
            HrpIsoDateUtils.addDays(displayedMondayIso, 3)
        } else {
            windowEndIso
        }
    }
    val monthHeaderLabel = remember(headerAnchorIso) {
        HrpIsoDateUtils.formatMonthYearVi(headerAnchorIso)
    }
    val nextWeekMondayIso = remember(displayedMondayIso) {
        HrpIsoDateUtils.addDays(displayedMondayIso, 7)
    }
    val canAdvanceWeek = if (allowFutureDates) true else windowEndIso < safeTodayIso
    val isTodayActive = !allDatesSelected && validSelectedIso == safeTodayIso

    val containerModifier = if (wrapInCardSurface) {
        modifier
            .fillMaxWidth()
            .hrpCardSurface(cornerRadius = HrpDesignTokens.RadiusCard)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(tagPrefix)
    } else {
        modifier
            .fillMaxWidth()
            .testTag(tagPrefix)
    }

    Column(
        modifier = containerModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SemanticColors.SurfaceSubtle,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable {
                            HrpHaptics.tick(haptic)
                            if (allowFutureDates) {
                                displayedMondayIso = HrpIsoDateUtils.addDays(displayedMondayIso, -7)
                            } else {
                                windowEndIso = HrpIsoDateUtils.addDays(windowEndIso, -7)
                            }
                        }
                        .testTag("${tagPrefix}_prev_week")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Tuần trước",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = SemanticColors.SurfaceSubtle,
                    border = BorderStroke(1.dp, SemanticColors.SurfaceBorder.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            HrpHaptics.tick(haptic)
                            val (initYear, initMonthZero, initDay) = HrpIsoDateUtils.splitYearMonthDay(
                                validSelectedIso ?: safeTodayIso,
                                safeTodayIso
                            )
                            val dialog = DatePickerDialog(
                                context,
                                { _, year, monthZeroBased, dayOfMonth ->
                                    val picked = String.format(
                                        Locale.US,
                                        "%04d-%02d-%02d",
                                        year,
                                        monthZeroBased + 1,
                                        dayOfMonth
                                    )
                                    val clamped = if (!allowFutureDates && picked > safeTodayIso) {
                                        safeTodayIso
                                    } else {
                                        picked
                                    }
                                    if (allowFutureDates) {
                                        displayedMondayIso = HrpIsoDateUtils.mondayOfWeek(clamped, safeTodayIso)
                                    } else {
                                        windowEndIso = HrpIsoDateUtils.resolveSlidingWindowEnd(
                                            selectedIso = clamped,
                                            currentWindowEnd = null,
                                            todayIso = safeTodayIso
                                        )
                                    }
                                    onSelectDate(clamped)
                                },
                                initYear,
                                initMonthZero,
                                initDay
                            )
                            if (!allowFutureDates) {
                                HrpIsoDateUtils.endOfDayLocalMillis(safeTodayIso)?.let { maxMs ->
                                    dialog.datePicker.maxDate = maxMs
                                }
                            }
                            dialog.show()
                        }
                        .testTag("${tagPrefix}_month_picker")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = monthHeaderLabel,
                            style = MaterialTheme.typography.labelMedium.tabularNums(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showAllOption) {
                    Surface(
                        shape = CircleShape,
                        color = if (allDatesSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SemanticColors.SurfaceSubtle
                        },
                        border = if (allDatesSelected) {
                            null
                        } else {
                            BorderStroke(1.dp, SemanticColors.SurfaceBorder)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                HrpHaptics.tick(haptic)
                                onSelectAllDates?.invoke()
                            }
                            .testTag("${tagPrefix}_all_chip")
                    ) {
                        Text(
                            text = "Tất cả",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (allDatesSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                if (showTodayChip) {
                    Surface(
                        shape = CircleShape,
                        color = if (isTodayActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            SemanticColors.SurfaceSubtle
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isTodayActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                SemanticColors.SurfaceBorder
                            }
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                HrpHaptics.tick(haptic)
                                displayedMondayIso = HrpIsoDateUtils.mondayOfWeek(safeTodayIso, safeTodayIso)
                                windowEndIso = safeTodayIso
                                onSelectDate(safeTodayIso)
                            }
                            .testTag("${tagPrefix}_today_chip")
                    ) {
                        Text(
                            text = "Hôm nay",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isTodayActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = SemanticColors.SurfaceSubtle,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .alpha(if (canAdvanceWeek) 1f else 0.35f)
                        .clickable(enabled = canAdvanceWeek) {
                            HrpHaptics.tick(haptic)
                            if (allowFutureDates) {
                                displayedMondayIso = nextWeekMondayIso
                            } else {
                                windowEndIso = minOf(HrpIsoDateUtils.addDays(windowEndIso, 7), safeTodayIso)
                            }
                        }
                        .testTag("${tagPrefix}_next_week")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Tuần kế tiếp",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            weekDates.forEach { dayIso ->
                val weekdayLabel = HrpIsoDateUtils.weekdayShortLabelVi(dayIso)
                val dayNum = HrpIsoDateUtils.dayOfMonth(dayIso)
                val isSelected = !allDatesSelected && validSelectedIso == dayIso
                val isToday = dayIso == safeTodayIso
                val isFutureLocked = !allowFutureDates && dayIso > safeTodayIso
                val isMarked = dayIso in markedDates
                val cellShape = RoundedCornerShape(HrpDesignTokens.RadiusMD)

                val bgColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> SemanticColors.SurfaceSubtle.copy(alpha = 0.55f)
                }
                val borderModifier = if (!isSelected && isToday) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = cellShape
                    )
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (isFutureLocked) 0.36f else 1f)
                        .clip(cellShape)
                        .background(bgColor, cellShape)
                        .then(borderModifier)
                        .clickable(enabled = !isFutureLocked) {
                            HrpHaptics.tick(haptic)
                            if (showAllOption && isSelected && onSelectAllDates != null) {
                                onSelectAllDates()
                            } else {
                                onSelectDate(dayIso)
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                        .testTag("${tagPrefix}_day_$dayIso"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = weekdayLabel,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                    Text(
                        text = dayNum.toString(),
                        style = MaterialTheme.typography.labelLarge.tabularNums(),
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                    val dotColor = when {
                        isSelected && (isMarked || isToday) -> MaterialTheme.colorScheme.onPrimary
                        isMarked -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(dotColor, CircleShape)
                    )
                }
            }
        }
    }
}

data class HrpSegmentedTabItem(
    val key: String,
    val label: String,
    val testTag: String? = null,
    val badgeCount: Int? = null,
    val showZeroBadge: Boolean = false,
    val icon: ImageVector? = null
)

@Composable
fun HrpSegmentedTabRow(
    items: List<HrpSegmentedTabItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    tabHeight: Dp = 34.dp
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val trackShape = RoundedCornerShape(11.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(trackShape)
            .background(SemanticColors.SurfaceSubtle, trackShape)
            .border(1.dp, SemanticColors.SurfaceBorder.copy(alpha = 0.65f), trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            HrpSegmentedTabCell(
                item = item,
                selected = selected,
                tabHeight = tabHeight,
                compactText = items.size >= 3,
                onClick = {
                    HrpHaptics.tick(haptic)
                    onSelect(item.key)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun HrpSegmentedTabGroup(
    items: List<HrpSegmentedTabItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxItemsPerRow: Int = 3,
    tabHeight: Dp = 34.dp
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val trackShape = RoundedCornerShape(11.dp)
    val effectiveRowSize = when {
        items.size <= maxItemsPerRow -> items.size
        items.size == 4 -> 2
        else -> maxItemsPerRow.coerceAtLeast(1)
    }
    val rows = items.chunked(effectiveRowSize)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(SemanticColors.SurfaceSubtle, trackShape)
            .border(1.dp, SemanticColors.SurfaceBorder.copy(alpha = 0.65f), trackShape)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { item ->
                    val selected = item.key == selectedKey
                    HrpSegmentedTabCell(
                        item = item,
                        selected = selected,
                        tabHeight = tabHeight,
                        compactText = rowItems.size >= 3,
                        onClick = {
                            HrpHaptics.tick(haptic)
                            onSelect(item.key)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun HrpSegmentedTabCell(
    item: HrpSegmentedTabItem,
    selected: Boolean,
    tabHeight: Dp,
    compactText: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabShape = RoundedCornerShape(8.dp)
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        SemanticColors.TextSecondary
    }
    val showBadge = item.badgeCount != null && (item.badgeCount > 0 || item.showZeroBadge)
    val baseModifier = modifier
        .heightIn(min = tabHeight)
        .let { m -> if (!item.testTag.isNullOrBlank()) m.testTag(item.testTag) else m }

    Surface(
        selected = selected,
        onClick = onClick,
        shape = tabShape,
        color = if (selected) SemanticColors.SurfaceLight else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        border = if (selected) BorderStroke(0.5.dp, SemanticColors.SurfaceBorder) else null,
        modifier = baseModifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compactText) 4.dp else 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (compactText) 11.5.sp else 12.5.sp
                ),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showBadge) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        SemanticColors.SurfaceBorder.copy(alpha = 0.75f)
                    }
                ) {
                    Text(
                        text = item.badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.tabularNums().copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SemanticColors.TextSecondary
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

data class HrpFilterChipItem(
    val key: String,
    val label: String,
    val testTag: String? = null
)

@Composable
fun HrpCompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val haptic = LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(percent = 50)
    val chipModifier = modifier
        .height(32.dp)
        .let { m -> if (!testTag.isNullOrBlank()) m.testTag(testTag) else m }

    Surface(
        selected = selected,
        onClick = {
            HrpHaptics.tick(haptic)
            onClick()
        },
        shape = pillShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            SemanticColors.SurfaceLight
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                SemanticColors.SurfaceBorder
            }
        ),
        modifier = chipModifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    SemanticColors.TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HrpCompactFilterChipRow(
    items: List<HrpFilterChipItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    equalWeight: Boolean = false
) {
    if (items.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            HrpCompactFilterChip(
                label = item.label,
                selected = selected,
                onClick = { onSelect(item.key) },
                testTag = item.testTag,
                modifier = if (equalWeight) Modifier.weight(1f) else Modifier
            )
        }
    }
}
