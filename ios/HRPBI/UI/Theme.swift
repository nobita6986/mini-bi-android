import SwiftUI

// MARK: - Mini BI Theme Palettes & Semantic Colors
// Mirrors Android's `ui/theme/Color.kt` 100%

public struct MiniBiThemePalette: Identifiable, Equatable {
    public let id: String
    public let name: String
    public let primary: Color
    public let primaryLight: Color
    public let primaryDark: Color
    public let accent: Color
    public let background: Color
    public let surface: Color
}

public enum MiniBiThemes {
    public static let hrPartner = MiniBiThemePalette(
        id: "hr-partner",
        name: "HR Partner (Mặc định)",
        primary: Color(hex: 0x1E6BB8),
        primaryLight: Color(hex: 0xEBF3FB),
        primaryDark: Color(hex: 0x144B82),
        accent: Color(hex: 0x0EA5E9),
        background: Color(hex: 0xF4F7FB),
        surface: .white
    )

    public static let indigoCorporate = MiniBiThemePalette(
        id: "indigo-corporate",
        name: "Indigo Corporate",
        primary: Color(hex: 0x4F46E5),
        primaryLight: Color(hex: 0xEEF2FF),
        primaryDark: Color(hex: 0x3730A3),
        accent: Color(hex: 0x6366F1),
        background: Color(hex: 0xF5F6FA),
        surface: .white
    )

    public static let oceanTrust = MiniBiThemePalette(
        id: "ocean-trust",
        name: "Ocean Trust",
        primary: Color(hex: 0x0284C7),
        primaryLight: Color(hex: 0xE0F2FE),
        primaryDark: Color(hex: 0x0369A1),
        accent: Color(hex: 0x06B6D4),
        background: Color(hex: 0xF0F9FF),
        surface: .white
    )

    public static let emeraldGrowth = MiniBiThemePalette(
        id: "emerald-growth",
        name: "Emerald Growth",
        primary: Color(hex: 0x059669),
        primaryLight: Color(hex: 0xECFDF5),
        primaryDark: Color(hex: 0x047857),
        accent: Color(hex: 0x10B981),
        background: Color(hex: 0xF2FBF7),
        surface: .white
    )

    public static let executiveGold = MiniBiThemePalette(
        id: "executive-gold",
        name: "Executive Gold",
        primary: Color(hex: 0xB45309),
        primaryLight: Color(hex: 0xFEF3C7),
        primaryDark: Color(hex: 0x92400E),
        accent: Color(hex: 0xD97706),
        background: Color(hex: 0xFAF8F5),
        surface: .white
    )

    public static let all: [MiniBiThemePalette] = [
        hrPartner,
        indigoCorporate,
        oceanTrust,
        emeraldGrowth,
        executiveGold
    ]

    public static func byId(_ id: String?) -> MiniBiThemePalette {
        guard let id else { return hrPartner }
        return all.first(where: { $0.id == id }) ?? hrPartner
    }
}

public enum SemanticColors {
    public static let successText = Color(hex: 0x166534)
    public static let successBg = Color(hex: 0xDCFCE7)
    public static let successBorder = Color(hex: 0x86EFAC)

    public static let warningText = Color(hex: 0x92400E)
    public static let warningBg = Color(hex: 0xFEF3C7)
    public static let warningBorder = Color(hex: 0xFDE68A)

    public static let dangerText = Color(hex: 0x991B1B)
    public static let dangerBg = Color(hex: 0xFEE2E2)
    public static let dangerBorder = Color(hex: 0xFCA5A5)

    public static let infoText = Color(hex: 0x1E40AF)
    public static let infoBg = Color(hex: 0xDBEAFE)
    public static let infoBorder = Color(hex: 0x93C5FD)

    public static let textPrimary = Color(hex: 0x0F172A)
    public static let textSecondary = Color(hex: 0x475569)
    public static let textMuted = Color(hex: 0x64748B)
    public static let borderSubtle = Color(hex: 0xE2E8F0)
}

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
