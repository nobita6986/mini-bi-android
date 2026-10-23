package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalMiniBiPalette = staticCompositionLocalOf { MiniBiThemes.first() }

@Composable
fun MyApplicationTheme(
    themeId: String = "hr-partner",
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = MiniBiThemes.find { it.id == themeId } ?: MiniBiThemes.first()

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.darkPrimary,
            onPrimary = Color(0xFF0F172A),
            primaryContainer = palette.darkPrimary.copy(alpha = 0.2f),
            onPrimaryContainer = Color(0xFFE2E8F0),
            secondary = palette.darkSecondary,
            onSecondary = Color(0xFF0F172A),
            secondaryContainer = palette.darkSecondary.copy(alpha = 0.2f),
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = palette.darkAccent,
            onTertiary = Color(0xFF0F172A),
            background = palette.darkBg,
            onBackground = Color(0xFFE2E8F0),
            surface = palette.darkSurface,
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF27272A),
            onSurfaceVariant = Color(0xFFA1A1AA),
            outline = Color(0xFF3F3F46),
            error = SemanticColors.ErrorDark,
            onError = Color(0xFF0F172A)
        )
    } else {
        lightColorScheme(
            primary = palette.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = palette.lightPrimaryContainer,
            onPrimaryContainer = palette.lightOnPrimaryContainer,
            secondary = palette.lightSecondary,
            onSecondary = Color.White,
            secondaryContainer = palette.lightSecondary.copy(alpha = 0.12f),
            onSecondaryContainer = palette.lightSecondary,
            tertiary = palette.lightAccent,
            onTertiary = Color.White,
            background = palette.lightBg,
            onBackground = Color(0xFF0F172A),
            surface = palette.lightSurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF3F4F6),
            onSurfaceVariant = Color(0xFF5B6B7F),
            outline = Color(0xFFE5E7EB),
            outlineVariant = palette.lightTopBarBorder,
            error = SemanticColors.ErrorLight,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalMiniBiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
