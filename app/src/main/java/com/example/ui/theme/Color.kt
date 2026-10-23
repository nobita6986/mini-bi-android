package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class MiniBiPalette(
    val id: String,
    val name: String,
    val subtitle: String,
    val lightBg: Color,
    val lightSurface: Color,
    val lightTopBar: Color,
    val lightTopBarBorder: Color,
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightAccent: Color,
    val darkBg: Color,
    val darkSurface: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkAccent: Color,
    val chartColors: List<Color>
)

object SemanticColors {
    val SuccessLight = Color(0xFF047857)
    val SuccessDark = Color(0xFF34D399)
    val WarningLight = Color(0xFFB45309)
    val WarningDark = Color(0xFFFBBF24)
    val ErrorLight = Color(0xFFBE123C)
    val ErrorDark = Color(0xFFFB7185)
    val RunningLight = Color(0xFF0369A1)
    val RunningDark = Color(0xFF38BDF8)
    val SlateLight = Color(0xFF64748B)
    val SlateDark = Color(0xFF94A3B8)
}

val MiniBiThemes = listOf(
    MiniBiPalette(
        id = "hr-partner",
        name = "HRPartner",
        subtitle = "HRP Brand Orange",
        lightBg = Color(0xFFF8F7F5),
        lightSurface = Color(0xFFFFFFFF),
        lightTopBar = Color(0xFFFFF8F3),
        lightTopBarBorder = Color(0xFFF2DFD0),
        lightPrimary = Color(0xFFEA580C),
        lightPrimaryContainer = Color(0xFFFFF1E6),
        lightOnPrimaryContainer = Color(0xFF9A3412),
        lightSecondary = Color(0xFF1E3A8A),
        lightAccent = Color(0xFFC2410C),
        darkBg = Color(0xFF14110F),
        darkSurface = Color(0xFF1E1916),
        darkPrimary = Color(0xFFFB923C),
        darkSecondary = Color(0xFF60A5FA),
        darkAccent = Color(0xFFF97316),
        chartColors = listOf(
            Color(0xFFEA580C),
            Color(0xFF1E3A8A),
            Color(0xFF0284C7),
            Color(0xFFE11D48),
            Color(0xFF059669),
            Color(0xFFD97706),
            Color(0xFF7C3AED),
            Color(0xFF475569)
        )
    ),
    MiniBiPalette(
        id = "indigo-corporate",
        name = "Indigo Corporate",
        subtitle = "Indigo & Sky Corporate",
        lightBg = Color(0xFFF7F9FF),
        lightSurface = Color(0xFFFFFFFF),
        lightTopBar = Color(0xFFFFFFFF),
        lightTopBarBorder = Color(0xFFE2E8F0),
        lightPrimary = Color(0xFF333F99),
        lightPrimaryContainer = Color(0xFFE5E9FA),
        lightOnPrimaryContainer = Color(0xFF232C6E),
        lightSecondary = Color(0xFF0284C7),
        lightAccent = Color(0xFFC2410C),
        darkBg = Color(0xFF0B1220),
        darkSurface = Color(0xFF0F172A),
        darkPrimary = Color(0xFF818CF8),
        darkSecondary = Color(0xFF38BDF8),
        darkAccent = Color(0xFFFB923C),
        chartColors = listOf(
            Color(0xFF3745A5),
            Color(0xFF0284C7),
            Color(0xFFEA580C),
            Color(0xFFE11D48),
            Color(0xFF059669),
            Color(0xFF7C3AED),
            Color(0xFFCA8A04),
            Color(0xFF475569)
        )
    ),
    MiniBiPalette(
        id = "ocean-trust",
        name = "Ocean Trust",
        subtitle = "Deep Sea & Teal",
        lightBg = Color(0xFFF0F9FF),
        lightSurface = Color(0xFFFFFFFF),
        lightTopBar = Color(0xFFF7FCFF),
        lightTopBarBorder = Color(0xFFDBEAFE),
        lightPrimary = Color(0xFF075985),
        lightPrimaryContainer = Color(0xFFE0F2FE),
        lightOnPrimaryContainer = Color(0xFF0C4A6E),
        lightSecondary = Color(0xFF0E7490),
        lightAccent = Color(0xFF0F766E),
        darkBg = Color(0xFF081420),
        darkSurface = Color(0xFF0F1E2E),
        darkPrimary = Color(0xFF38BDF8),
        darkSecondary = Color(0xFF22D3EE),
        darkAccent = Color(0xFF2DD4BF),
        chartColors = listOf(
            Color(0xFF0369A1),
            Color(0xFF0891B2),
            Color(0xFF0F766E),
            Color(0xFF2563EB),
            Color(0xFF65A30D),
            Color(0xFF9333EA),
            Color(0xFFD97706),
            Color(0xFF475569)
        )
    ),
    MiniBiPalette(
        id = "emerald-growth",
        name = "Emerald Growth",
        subtitle = "Emerald & Royal Blue",
        lightBg = Color(0xFFF0FDF4),
        lightSurface = Color(0xFFFFFFFF),
        lightTopBar = Color(0xFFF7FEF9),
        lightTopBarBorder = Color(0xFFDCFCE7),
        lightPrimary = Color(0xFF047857),
        lightPrimaryContainer = Color(0xFFD1FAE5),
        lightOnPrimaryContainer = Color(0xFF065F46),
        lightSecondary = Color(0xFF0F766E),
        lightAccent = Color(0xFF1D4ED8),
        darkBg = Color(0xFF071712),
        darkSurface = Color(0xFF0D241C),
        darkPrimary = Color(0xFF34D399),
        darkSecondary = Color(0xFF2DD4BF),
        darkAccent = Color(0xFF60A5FA),
        chartColors = listOf(
            Color(0xFF059669),
            Color(0xFF0D9488),
            Color(0xFF2563EB),
            Color(0xFF65A30D),
            Color(0xFF7C3AED),
            Color(0xFFCA8A04),
            Color(0xFFDC2626),
            Color(0xFF475569)
        )
    ),
    MiniBiPalette(
        id = "executive-gold",
        name = "Executive Gold",
        subtitle = "Slate Navy & Amber Gold",
        lightBg = Color(0xFFF8FAFC),
        lightSurface = Color(0xFFFFFFFF),
        lightTopBar = Color(0xFFFFFFFF),
        lightTopBarBorder = Color(0xFFE2E8F0),
        lightPrimary = Color(0xFF0F172A),
        lightPrimaryContainer = Color(0xFFE2E8F0),
        lightOnPrimaryContainer = Color(0xFF0F172A),
        lightSecondary = Color(0xFF334155),
        lightAccent = Color(0xFFB45309),
        darkBg = Color(0xFF090D16),
        darkSurface = Color(0xFF111827),
        darkPrimary = Color(0xFFFBBF24),
        darkSecondary = Color(0xFF94A3B8),
        darkAccent = Color(0xFFF59E0B),
        chartColors = listOf(
            Color(0xFF1E3A8A),
            Color(0xFF0F766E),
            Color(0xFFB45309),
            Color(0xFF7C3AED),
            Color(0xFFBE123C),
            Color(0xFF0369A1),
            Color(0xFF4D7C0F),
            Color(0xFF64748B)
        )
    )
)
