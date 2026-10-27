import SwiftUI
import UIKit

// MARK: - Universal Keyboard Dismissal Utilities

public enum KeyboardDismissal {
    public static func dismiss() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }
}

private final class NonCancelingKeyboardDismissGesture: UITapGestureRecognizer, UIGestureRecognizerDelegate {
    override init(target: Any?, action: Selector?) {
        super.init(target: target, action: action)
        self.cancelsTouchesInView = false
        self.delaysTouchesBegan = false
        self.delaysTouchesEnded = false
        self.delegate = self
    }

    @objc func handleTap(_ recognizer: UITapGestureRecognizer) {
        if recognizer.state == .ended {
            KeyboardDismissal.dismiss()
        }
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        var current: UIView? = touch.view
        while let view = current {
            if view is UITextField || view is UITextView {
                return false
            }
            let typeName = String(describing: type(of: view))
            if typeName.contains("Keyboard") || typeName.contains("InputSet") {
                return false
            }
            current = view.superview
        }
        return true
    }
}

private struct WindowTapKeyboardDismissInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = PassthroughInstallerView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        (uiView as? PassthroughInstallerView)?.installIfNeeded()
    }

    private final class PassthroughInstallerView: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            installIfNeeded()
        }

        func installIfNeeded() {
            if let win = self.window {
                attachRecognizer(to: win)
            }
            var responder: UIResponder? = self.next
            while let r = responder {
                if let vc = r as? UIViewController, let rootView = vc.view {
                    attachRecognizer(to: rootView)
                    break
                }
                responder = r.next
            }
        }

        private func attachRecognizer(to targetView: UIView) {
            let alreadyInstalled = (targetView.gestureRecognizers ?? []).contains {
                $0 is NonCancelingKeyboardDismissGesture
            }
            guard !alreadyInstalled else { return }
            let gesture = NonCancelingKeyboardDismissGesture(target: nil, action: nil)
            gesture.addTarget(gesture, action: #selector(NonCancelingKeyboardDismissGesture.handleTap(_:)))
            targetView.addGestureRecognizer(gesture)
        }
    }
}

public extension View {
    func dismissKeyboardOnTapOutside() -> some View {
        self
            .background(WindowTapKeyboardDismissInstaller().frame(width: 0, height: 0))
            .scrollDismissesKeyboard(.interactively)
    }
}

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
    public let topBar: Color
    public let topBarBorder: Color

    public init(
        id: String,
        name: String,
        primary: Color,
        primaryLight: Color,
        primaryDark: Color,
        accent: Color,
        background: Color,
        surface: Color,
        topBar: Color = Color(hex: 0xFFF8F3),
        topBarBorder: Color = Color(hex: 0xF2DFD0)
    ) {
        self.id = id
        self.name = name
        self.primary = primary
        self.primaryLight = primaryLight
        self.primaryDark = primaryDark
        self.accent = accent
        self.background = background
        self.surface = surface
        self.topBar = topBar
        self.topBarBorder = topBarBorder
    }
}

public enum MiniBiThemes {
    public static let hrPartner = MiniBiThemePalette(
        id: "hr-partner",
        name: "HRP Brand Orange (Mặc định)",
        primary: Color(hex: 0xEA580C),
        primaryLight: Color(hex: 0xFFF1E6),
        primaryDark: Color(hex: 0x9A3412),
        accent: Color(hex: 0xC2410C),
        background: Color(hex: 0xF8F7F5),
        surface: .white,
        topBar: Color(hex: 0xFFF8F3),
        topBarBorder: Color(hex: 0xF2DFD0)
    )

    public static let indigoCorporate = MiniBiThemePalette(
        id: "indigo-corporate",
        name: "Indigo Corporate",
        primary: Color(hex: 0x3949AB),
        primaryLight: Color(hex: 0xE8EAF6),
        primaryDark: Color(hex: 0x1A237E),
        accent: Color(hex: 0x303F9F),
        background: Color(hex: 0xF4F6FA),
        surface: .white,
        topBar: Color(hex: 0xF7F8FC),
        topBarBorder: Color(hex: 0xD9DFF0)
    )

    public static let oceanTrust = MiniBiThemePalette(
        id: "ocean-trust",
        name: "Ocean Trust",
        primary: Color(hex: 0x0077B6),
        primaryLight: Color(hex: 0xE0F4FF),
        primaryDark: Color(hex: 0x023E8A),
        accent: Color(hex: 0x005F92),
        background: Color(hex: 0xF2F8FC),
        surface: .white,
        topBar: Color(hex: 0xF4FAFE),
        topBarBorder: Color(hex: 0xCCE5F6)
    )

    public static let emeraldGrowth = MiniBiThemePalette(
        id: "emerald-growth",
        name: "Emerald Growth",
        primary: Color(hex: 0x047857),
        primaryLight: Color(hex: 0xE6F7F1),
        primaryDark: Color(hex: 0x064E3B),
        accent: Color(hex: 0x065F46),
        background: Color(hex: 0xF2F7F5),
        surface: .white,
        topBar: Color(hex: 0xF4FAF7),
        topBarBorder: Color(hex: 0xCCE8DC)
    )

    public static let executiveGold = MiniBiThemePalette(
        id: "executive-gold",
        name: "Executive Gold",
        primary: Color(hex: 0xB45309),
        primaryLight: Color(hex: 0xFFF3DC),
        primaryDark: Color(hex: 0x78350F),
        accent: Color(hex: 0x92400E),
        background: Color(hex: 0xF8F6F2),
        surface: .white,
        topBar: Color(hex: 0xFCF8F2),
        topBarBorder: Color(hex: 0xEDE0CC)
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
