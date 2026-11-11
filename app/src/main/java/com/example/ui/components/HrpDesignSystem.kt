package com.example.ui.components

import android.animation.ValueAnimator
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
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SemanticColors
import java.util.Locale
import kotlin.math.abs

object HrpDesignTokens {
    val RadiusXS: Dp = 8.dp
    val RadiusMD: Dp = 12.dp
    val RadiusCard: Dp = 16.dp
    val RadiusHero: Dp = 22.dp
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
