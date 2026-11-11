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
    public static let surfaceMuted = Color(hex: 0xF5F4F2)
}

// MARK: - Appllama Design Tokens, Haptics & Reusable Modifiers

public enum HrpDesignTokens {
    /// Small chips, status badges (8pt continuous)
    public static let radiusXS: CGFloat = 8
    /// Inputs, secondary buttons, inner tiles (12pt continuous)
    public static let radiusMD: CGFloat = 12
    /// KPI, worker, report, and attendance cards (16pt continuous)
    public static let radiusCard: CGFloat = 16
    /// Login card, hero cards, bottom sheets (22pt continuous)
    public static let radiusHero: CGFloat = 22
}

public enum HapticsManager {
    public static func selection() {
        runOnMain {
            let generator = UISelectionFeedbackGenerator()
            generator.prepare()
            generator.selectionChanged()
        }
    }

    public static func lightImpact() {
        runOnMain {
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    public static func success() {
        runOnMain {
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(.success)
        }
    }

    public static func warning() {
        runOnMain {
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(.warning)
        }
    }

    public static func error() {
        runOnMain {
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(.error)
        }
    }

    private static func runOnMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }
}

public struct HrpPressableButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    public var scaleAmount: CGFloat

    public init(scaleAmount: CGFloat = 0.97) {
        self.scaleAmount = scaleAmount
    }

    public func makeBody(configuration: Configuration) -> some View {
        let isPressed = configuration.isPressed
        return configuration.label
            .scaleEffect(reduceMotion ? 1.0 : (isPressed ? scaleAmount : 1.0))
            .opacity(reduceMotion ? (isPressed ? 0.85 : 1.0) : 1.0)
            .brightness(reduceMotion ? 0 : (isPressed ? -0.02 : 0))
            .animation(
                reduceMotion
                    ? .easeOut(duration: 0.12)
                    : (isPressed
                        ? .easeOut(duration: 0.12)
                        : .spring(response: 0.28, dampingFraction: 0.78)),
                value: isPressed
            )
    }
}

private struct HrpSheetCornerRadiusModifier: ViewModifier {
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.presentationCornerRadius(cornerRadius)
        } else {
            content
        }
    }
}

public extension View {
    func hrpCardSurface(
        cornerRadius: CGFloat = HrpDesignTokens.radiusCard,
        fill: Color = .white,
        borderColor: Color = SemanticColors.borderSubtle.opacity(0.85),
        lineWidth: CGFloat = 0.65,
        showShadow: Bool = true
    ) -> some View {
        self
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(fill)
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: lineWidth)
            )
            .shadow(
                color: showShadow ? Color.black.opacity(0.04) : .clear,
                radius: 8,
                x: 0,
                y: 2
            )
    }

    func hrpMutedTile(
        cornerRadius: CGFloat = HrpDesignTokens.radiusMD,
        fill: Color = SemanticColors.surfaceMuted,
        borderColor: Color = SemanticColors.borderSubtle.opacity(0.7)
    ) -> some View {
        self
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(fill)
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: 0.6)
            )
    }

    func hrpSheetPresentation(
        detents: Set<PresentationDetent> = [.medium, .large],
        cornerRadius: CGFloat = HrpDesignTokens.radiusHero
    ) -> some View {
        self
            .presentationDetents(detents)
            .presentationDragIndicator(.visible)
            .modifier(HrpSheetCornerRadiusModifier(cornerRadius: cornerRadius))
    }
}

// MARK: - Shared Appllama Round 2 Components (Date Navigator, Load More, Segmented Bar, Initials Avatar)

public struct HrpDateNavigatorBar: View {
    public let selectedDateIso: String
    public let todayDateIso: String
    public let allowFutureDates: Bool
    public let theme: MiniBiThemePalette
    public let onDateSelected: (String) -> Void

    @Namespace private var dateStripNamespace
    @State private var showCalendarSheet: Bool = false
    @State private var pickerDate: Date = Date()

    public init(
        selectedDateIso: String,
        todayDateIso: String = LeaveContract.todayVietnamIsoDate(),
        allowFutureDates: Bool = false,
        theme: MiniBiThemePalette,
        onDateSelected: @escaping (String) -> Void
    ) {
        self.selectedDateIso = selectedDateIso
        self.todayDateIso = todayDateIso
        self.allowFutureDates = allowFutureDates
        self.theme = theme
        self.onDateSelected = onDateSelected
    }

    private static var vietnamCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        cal.locale = Locale(identifier: "vi_VN")
        cal.firstWeekday = 2 // Monday (T2)
        return cal
    }

    private static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private func parseIso(_ iso: String) -> Date {
        Self.isoFormatter.date(from: iso.trimmingCharacters(in: .whitespacesAndNewlines)) ?? Date()
    }

    private func formatIso(_ date: Date) -> String {
        Self.isoFormatter.string(from: date)
    }

    private var selectedDate: Date {
        parseIso(selectedDateIso)
    }

    private var monthYearLabel: String {
        let cal = Self.vietnamCalendar
        let comps = cal.dateComponents([.month, .year], from: selectedDate)
        let m = comps.month ?? 1
        let y = comps.year ?? 2026
        return "Tháng \(m), \(y)"
    }

    private struct WeekDayCell: Identifiable {
        let id: String // ISO date YYYY-MM-DD
        let weekdayShort: String
        let dayNumber: Int
        let isToday: Bool
        let isSelected: Bool
        let isDisabled: Bool
    }

    private var weekDays: [WeekDayCell] {
        let cal = Self.vietnamCalendar
        let baseDate = selectedDate
        let weekday = cal.component(.weekday, from: baseDate) // 1 = Sun, 2 = Mon, ..., 7 = Sat
        let offsetToMonday = (weekday == 1) ? -6 : (2 - weekday)
        let monday = cal.date(byAdding: .day, value: offsetToMonday, to: baseDate) ?? baseDate
        let labels = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]

        return (0..<7).map { idx in
            let d = cal.date(byAdding: .day, value: idx, to: monday) ?? baseDate
            let iso = formatIso(d)
            let dayNum = cal.component(.day, from: d)
            let disabled = !allowFutureDates && iso > todayDateIso
            return WeekDayCell(
                id: iso,
                weekdayShort: labels[idx],
                dayNumber: dayNum,
                isToday: iso == todayDateIso,
                isSelected: iso == selectedDateIso,
                isDisabled: disabled
            )
        }
    }

    private var canAdvanceWeek: Bool {
        if allowFutureDates { return true }
        return selectedDateIso < todayDateIso
    }

    private func shiftDays(_ delta: Int) {
        let cal = Self.vietnamCalendar
        guard let shifted = cal.date(byAdding: .day, value: delta, to: selectedDate) else { return }
        var targetIso = formatIso(shifted)
        if !allowFutureDates && targetIso > todayDateIso {
            targetIso = todayDateIso
        }
        guard targetIso != selectedDateIso else { return }
        HapticsManager.selection()
        onDateSelected(targetIso)
    }

    public var body: some View {
        VStack(spacing: 8) {
            // Top Row: `<` | Month/Year Picker Button | "Hôm nay" chip | `>`
            HStack(spacing: 8) {
                Button {
                    shiftDays(-7)
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(SemanticColors.textPrimary)
                        .frame(width: 34, height: 34)
                        .background(SemanticColors.surfaceMuted)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                }
                .buttonStyle(HrpPressableButtonStyle())
                .accessibilityLabel("Tuần trước")

                Button {
                    HapticsManager.selection()
                    pickerDate = selectedDate
                    showCalendarSheet = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.primary)
                        Text(monthYearLabel)
                            .font(.subheadline.weight(.bold))
                            .monospacedDigit()
                            .foregroundColor(SemanticColors.textPrimary)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(SemanticColors.textMuted)
                    }
                    .padding(.horizontal, 10)
                    .frame(minHeight: 34)
                    .background(SemanticColors.surfaceMuted.opacity(0.75))
                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                }
                .buttonStyle(HrpPressableButtonStyle())

                Spacer(minLength: 4)

                if selectedDateIso != todayDateIso {
                    Button {
                        HapticsManager.selection()
                        onDateSelected(todayDateIso)
                    } label: {
                        Text("Hôm nay")
                            .font(.caption.weight(.bold))
                            .foregroundColor(theme.primary)
                            .padding(.horizontal, 10)
                            .frame(minHeight: 30)
                            .background(theme.primaryLight)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(HrpPressableButtonStyle())
                }

                Button {
                    shiftDays(7)
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(canAdvanceWeek ? SemanticColors.textPrimary : SemanticColors.textMuted.opacity(0.45))
                        .frame(width: 34, height: 34)
                        .background(SemanticColors.surfaceMuted)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                }
                .buttonStyle(HrpPressableButtonStyle())
                .disabled(!canAdvanceWeek)
                .accessibilityLabel("Tuần sau")
            }

            // Bottom Row: 7-day strip (T2..CN)
            HStack(spacing: 6) {
                ForEach(weekDays) { cell in
                    Button {
                        guard !cell.isDisabled, cell.id != selectedDateIso else { return }
                        HapticsManager.selection()
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            onDateSelected(cell.id)
                        }
                    } label: {
                        VStack(spacing: 3) {
                            Text(cell.weekdayShort)
                                .font(.system(size: 10, weight: cell.isSelected ? .bold : .semibold))
                                .foregroundColor(
                                    cell.isSelected
                                        ? Color.white.opacity(0.9)
                                        : (cell.isDisabled ? SemanticColors.textMuted.opacity(0.45) : SemanticColors.textSecondary)
                                )
                            Text("\(cell.dayNumber)")
                                .font(.system(size: 14, weight: cell.isSelected ? .heavy : .semibold))
                                .monospacedDigit()
                                .foregroundColor(
                                    cell.isSelected
                                        ? .white
                                        : (cell.isDisabled ? SemanticColors.textMuted.opacity(0.45) : (cell.isToday ? theme.primary : SemanticColors.textPrimary))
                                )
                            Circle()
                                .fill(cell.isSelected ? Color.white : (cell.isToday ? theme.primary : Color.clear))
                                .frame(width: 4, height: 4)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .background {
                            if cell.isSelected {
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(theme.primary)
                                    .matchedGeometryEffect(id: "hrp_date_nav_active_pill", in: dateStripNamespace)
                                    .shadow(color: theme.primary.opacity(0.22), radius: 5, x: 0, y: 2)
                            } else {
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(cell.isToday ? theme.primaryLight.opacity(0.6) : SemanticColors.surfaceMuted.opacity(0.55))
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(cell.isDisabled)
                }
            }
        }
        .padding(10)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
        .sheet(isPresented: $showCalendarSheet) {
            NavigationStack {
                VStack(spacing: 16) {
                    if allowFutureDates {
                        DatePicker(
                            "Chọn ngày",
                            selection: $pickerDate,
                            displayedComponents: [.date]
                        )
                        .datePickerStyle(.graphical)
                        .environment(\.locale, Locale(identifier: "vi_VN"))
                        .environment(\.calendar, Self.vietnamCalendar)
                        .tint(theme.primary)
                        .padding(.horizontal, 12)
                    } else {
                        DatePicker(
                            "Chọn ngày",
                            selection: $pickerDate,
                            in: ...parseIso(todayDateIso),
                            displayedComponents: [.date]
                        )
                        .datePickerStyle(.graphical)
                        .environment(\.locale, Locale(identifier: "vi_VN"))
                        .environment(\.calendar, Self.vietnamCalendar)
                        .tint(theme.primary)
                        .padding(.horizontal, 12)
                    }

                    HStack(spacing: 10) {
                        Button {
                            HapticsManager.selection()
                            showCalendarSheet = false
                            onDateSelected(todayDateIso)
                        } label: {
                            Text("Về hôm nay")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(theme.primary)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(theme.primaryLight)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                        }
                        .buttonStyle(HrpPressableButtonStyle())

                        Button {
                            HapticsManager.selection()
                            let chosenIso = formatIso(pickerDate)
                            let clampedIso = (!allowFutureDates && chosenIso > todayDateIso) ? todayDateIso : chosenIso
                            showCalendarSheet = false
                            if clampedIso != selectedDateIso {
                                onDateSelected(clampedIso)
                            }
                        } label: {
                            Text("Áp dụng")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(theme.primary)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                    }
                    .padding(.horizontal, 16)

                    Spacer(minLength: 0)
                }
                .padding(.top, 8)
                .navigationTitle("Chọn ngày xem dữ liệu")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Đóng") {
                            showCalendarSheet = false
                        }
                    }
                }
            }
            .hrpSheetPresentation(detents: [.medium, .large])
        }
    }
}

public struct HrpLoadMoreButton: View {
    public let title: String
    public let loadedCount: Int?
    public let totalCount: Int?
    public let isLoading: Bool
    public let theme: MiniBiThemePalette
    public let action: () -> Void

    public init(
        title: String = "Tải thêm",
        loadedCount: Int? = nil,
        totalCount: Int? = nil,
        isLoading: Bool = false,
        theme: MiniBiThemePalette,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.loadedCount = loadedCount
        self.totalCount = totalCount
        self.isLoading = isLoading
        self.theme = theme
        self.action = action
    }

    private var resolvedLabel: String {
        if let loaded = loadedCount, let total = totalCount, total > loaded {
            return "\(title) (đang hiện \(loaded) / \(total))"
        }
        return title
    }

    public var body: some View {
        Button {
            guard !isLoading else { return }
            HapticsManager.lightImpact()
            action()
        } label: {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.85)
                        .tint(theme.primary)
                    Text("Đang tải thêm…")
                        .font(.subheadline.weight(.semibold))
                } else {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 14, weight: .semibold))
                    Text(resolvedLabel)
                        .font(.subheadline.weight(.semibold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                }
            }
            .foregroundColor(theme.primary)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                    .stroke(theme.primary.opacity(0.38), lineWidth: 0.9)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(HrpPressableButtonStyle())
        .disabled(isLoading)
    }
}

public struct HrpSegmentedBar: View {
    public let activeCount: Int
    public let inactiveCount: Int
    public let otherCount: Int
    public let height: CGFloat

    public init(
        activeCount: Int,
        inactiveCount: Int,
        otherCount: Int = 0,
        height: CGFloat = 6
    ) {
        self.activeCount = max(0, activeCount)
        self.inactiveCount = max(0, inactiveCount)
        self.otherCount = max(0, otherCount)
        self.height = height
    }

    public var body: some View {
        let total = max(1, activeCount + inactiveCount + otherCount)
        GeometryReader { geo in
            let w = geo.size.width
            let activeWidth = w * CGFloat(activeCount) / CGFloat(total)
            let inactiveWidth = w * CGFloat(inactiveCount) / CGFloat(total)
            let otherWidth = max(0, w - activeWidth - inactiveWidth)

            HStack(spacing: 0) {
                if activeCount + inactiveCount + otherCount == 0 {
                    Rectangle()
                        .fill(SemanticColors.borderSubtle.opacity(0.6))
                        .frame(width: w, height: height)
                } else {
                    if activeCount > 0 {
                        Rectangle()
                            .fill(SemanticColors.successText)
                            .frame(width: activeWidth, height: height)
                    }
                    if inactiveCount > 0 {
                        Rectangle()
                            .fill(SemanticColors.dangerText.opacity(0.85))
                            .frame(width: inactiveWidth, height: height)
                    }
                    if otherCount > 0 {
                        Rectangle()
                            .fill(SemanticColors.warningText)
                            .frame(width: otherWidth, height: height)
                    }
                }
            }
            .clipShape(Capsule())
        }
        .frame(height: height)
    }
}

public struct HrpInitialsAvatar: View {
    public let fullName: String
    public let tint: Color
    public let size: CGFloat

    public init(fullName: String, tint: Color, size: CGFloat = 40) {
        self.fullName = fullName
        self.tint = tint
        self.size = size
    }

    public static func extractInitials(from rawName: String) -> String {
        let words = rawName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
        guard !words.isEmpty else { return "NL" }
        if words.count == 1 {
            return String(words[0].prefix(2)).uppercased()
        }
        let firstChar = words.first?.first.map { String($0) } ?? ""
        let lastChar = words.last?.first.map { String($0) } ?? ""
        return (firstChar + lastChar).uppercased()
    }

    public var body: some View {
        let initials = Self.extractInitials(from: fullName)
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
                .fill(tint.opacity(0.14))
            RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
                .stroke(tint.opacity(0.26), lineWidth: 0.8)
            Text(initials)
                .font(.system(size: size * 0.36, weight: .bold, design: .rounded))
                .foregroundColor(tint)
        }
        .frame(width: size, height: size)
    }
}

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
