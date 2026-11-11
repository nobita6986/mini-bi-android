import SwiftUI
import CoreLocation
import PhotosUI

// MARK: - Attendance Screen (Check-in + Leave Request "Xin nghỉ" + Leave Approval "Duyệt nghỉ")
// Mirrors Android's `ui/screens/AttendanceScreen.kt` 100%

public final class AttendanceLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()

    @Published public var currentLocation: CLLocation?
    @Published public var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published public var locationErrorMessage: String?

    public var isPermissionDenied: Bool {
        authorizationStatus == .denied || authorizationStatus == .restricted
    }

    public override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
        authorizationStatus = manager.authorizationStatus
    }

    public func requestLocationUpdate() {
        authorizationStatus = manager.authorizationStatus
        switch manager.authorizationStatus {
        case .notDetermined:
            locationErrorMessage = "Đang xin quyền Vị trí (GPS)..."
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            locationErrorMessage = nil
            manager.startUpdatingLocation()
            if let cached = manager.location {
                currentLocation = cached
            }
        case .denied, .restricted:
            locationErrorMessage = "Chưa cấp quyền Vị trí (GPS). Vui lòng bấm 'Mở Cài đặt' để cấp quyền."
        @unknown default:
            break
        }
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            locationErrorMessage = nil
            manager.startUpdatingLocation()
            if let cached = manager.location {
                currentLocation = cached
            }
        case .denied, .restricted:
            locationErrorMessage = "Chưa cấp quyền Vị trí (GPS). Vui lòng bấm 'Mở Cài đặt' để cấp quyền."
        default:
            break
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let latest = locations.last {
            currentLocation = latest
            locationErrorMessage = nil
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if currentLocation == nil {
            locationErrorMessage = "Đang lấy vị trí GPS (\(error.localizedDescription))..."
        }
    }
}

private struct PendingLeaveDecisionSheetItem: Identifiable {
    var id: String { "\(request.requestId)-\(decision)" }
    let request: LeaveRequestProjection
    let decision: String
}

private struct PendingExplanationDecisionSheetItem: Identifiable {
    var id: String { "\(explanation.explanationId)-\(decision)" }
    let explanation: AttendanceExplanationItemProjection
    let decision: String
}

public struct AttendanceScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @StateObject private var locationManager = AttendanceLocationManager()
    @Environment(\.scenePhase) private var scenePhase

    @State private var rawCapturedPhotoData: Data?
    @State private var capturedPhotoData: Data?
    @State private var selectedBeautyPreset: AttendanceBeautyPreset = AttendanceBeautyFilter.loadSavedPreset()
    @State private var isShowingCapturedPhotoZoomSheet: Bool = false
    @State private var frozenCaptureTimestamp: CapturedAttendanceTimestamp?
    @State private var noteInput: String = ""
    @State private var isShowingCamera: Bool = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var exportedReportUrl: URL?
    @State private var selectedPreviewOutboxItem: AttendanceOutboxItem?
    @State private var isShowingTechnicalInfoSheet: Bool = false
    @State private var visibleMyLeaveRequestsCount: Int = 10
    @State private var liveClockTick: Int64 = 0
    @State private var lastAutoTimeSyncAttemptSec: TimeInterval = 0
    @Namespace private var attendanceTabNamespace

    private let liveClockTimer = Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()

    // Leave Request & Approval Sheet States
    @State private var withdrawTargetRequest: LeaveRequestProjection?
    @State private var withdrawReasonInput: String = ""
    @State private var pendingDecisionItem: PendingLeaveDecisionSheetItem?
    @State private var decisionReasonInput: String = ""
    @State private var localDecisionDialogError: String?
    @State private var expandedInboxRequestIds: Set<String> = []
    @State private var datePickerTargetDay: LeaveDraftDayInput?
    @State private var datePickerSelection: Date = Date()
    @State private var datePickerModeIsRange: Bool = false
    @State private var isShowingRangePickerSheet: Bool = false
    @State private var rangeStartDate: Date = Date()
    @State private var rangeEndDate: Date = Date()
    @State private var rangeLeaveType: String = "ANNUAL"
    @State private var rangeSkipSundays: Bool = false
    @State private var rangeErrorMessage: String?

    // Attendance Explanation ("Giải trình chấm công") & Approval ("Xét duyệt giải trình") Sheet States
    @State private var pendingExplanationSubmitSlot: AttendanceExplanationDaySlotEvaluationProjection?
    @State private var explanationReasonInput: String = ""
    @State private var localExplanationSubmitError: String?
    @State private var pendingExplanationDecisionItem: PendingExplanationDecisionSheetItem?
    @State private var explanationDecisionNoteInput: String = ""
    @State private var localExplanationDecisionError: String?
    @State private var expandedMyExplanationIds: Set<String> = []
    @State private var expandedInboxExplanationIds: Set<String> = []
    @State private var isShowingExplanationDatePickerSheet: Bool = false
    @State private var explanationDatePickerSelection: Date = Date()
    @State private var isShowingExplanationPolicyInfoSheet: Bool = false

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme
        let visibleTabs = viewModel.resolveVisibleAttendanceTabs()
        let effectiveTab = visibleTabs.contains(viewModel.activeAttendanceTab)
            ? viewModel.activeAttendanceTab
            : .checkIn

        VStack(spacing: 0) {
            attendanceTopTabBar(
                tabs: visibleTabs,
                selectedTab: effectiveTab,
                theme: theme
            )

            switch effectiveTab {
            case .checkIn:
                checkInTabContent(theme: theme)
            case .explanation:
                explanationTabContent(theme: theme)
            case .leaveRequest:
                leaveRequestTabContent(theme: theme)
            case .explanationApproval:
                explanationApprovalTabContent(theme: theme)
            case .leaveApproval:
                leaveApprovalTabContent(theme: theme)
            }
        }
        .background(theme.background)
        .dismissKeyboardOnTapOutside()
        .onAppear {
            locationManager.requestLocationUpdate()
            lastAutoTimeSyncAttemptSec = Date().timeIntervalSince1970
            Task {
                await viewModel.loadInitialAttendanceScreenDataIfNeeded()
            }
        }
        .onChange(of: scenePhase) { newPhase in
            guard newPhase == .active else { return }
            locationManager.requestLocationUpdate()
            lastAutoTimeSyncAttemptSec = Date().timeIntervalSince1970
            Task {
                _ = await viewModel.syncAttendanceTimeAnchorOnly(force: false, isAutomatic: true)
            }
        }
        .onReceive(liveClockTimer) { now in
            liveClockTick &+= 1
            viewModel.refreshAttendanceTimeEvaluation()
            let eval = viewModel.attendanceTimeEvaluation
            let nowSec = now.timeIntervalSince1970
            let needsTimeAnchorRefresh = !eval.isTrusted || (eval.remainingValidMs ?? 0) <= AttendanceTimeContract.refreshBeforeExpiryMs
            if needsTimeAnchorRefresh
                && viewModel.canAttemptTimeAnchorAutoSync()
                && (nowSec - lastAutoTimeSyncAttemptSec >= 15.0) {
                lastAutoTimeSyncAttemptSec = nowSec
                Task {
                    _ = await viewModel.syncAttendanceTimeAnchorOnly(force: false, isAutomatic: true)
                }
            }
        }
        .sheet(isPresented: $isShowingCamera) {
            CameraPhotoCaptureSheet(preferredCameraDevice: .front) { data in
                let normalizedRaw = AttendanceContract.normalizeCameraJpeg(data, maxDimensionPx: 1600, compressionQuality: 0.90)
                rawCapturedPhotoData = normalizedRaw
                capturedPhotoData = AttendanceBeautyFilter.apply(to: normalizedRaw, preset: selectedBeautyPreset)
                frozenCaptureTimestamp = viewModel.timeAnchorManager.resolveCaptureTime()
                if !viewModel.timeAnchorManager.evaluateAnchor().isTrusted {
                    Task {
                        _ = await viewModel.syncAttendanceTimeAnchorOnly(force: false, isAutomatic: true)
                        if viewModel.timeAnchorManager.evaluateAnchor().isTrusted {
                            frozenCaptureTimestamp = viewModel.timeAnchorManager.resolveCaptureTime()
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $isShowingCapturedPhotoZoomSheet) {
            capturedPhotoBeautyPreviewSheet(theme: theme)
                .hrpSheetPresentation(detents: [.large])
        }
        .sheet(isPresented: Binding(
            get: { exportedReportUrl != nil },
            set: { if !$0 { exportedReportUrl = nil } }
        )) {
            if let url = exportedReportUrl {
                ActivityShareSheet(activityItems: [url])
            }
        }
        .sheet(item: $withdrawTargetRequest) { target in
            withdrawConfirmationSheet(target: target, theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(item: $pendingDecisionItem) { item in
            approverDecisionSheet(item: item, theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(item: $datePickerTargetDay) { day in
            leaveDatePickerSheet(day: day, theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(isPresented: $isShowingRangePickerSheet) {
            leaveDateRangePickerSheet(theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(item: $pendingExplanationSubmitSlot) { slot in
            explanationSubmitSheet(slot: slot, theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(item: $pendingExplanationDecisionItem) { item in
            explanationDecisionSheet(item: item, theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(isPresented: $isShowingExplanationDatePickerSheet) {
            explanationDatePickerSheet(theme: theme)
                .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(isPresented: $isShowingExplanationPolicyInfoSheet) {
            explanationPolicyInfoSheet(theme: theme)
                .hrpSheetPresentation(detents: [.medium])
        }
        .sheet(item: $selectedPreviewOutboxItem) { previewItem in
            AttendancePhotoPreviewSheet(
                item: previewItem,
                outboxService: viewModel.attendanceOutboxService,
                theme: theme
            )
            .hrpSheetPresentation(detents: [.medium, .large])
        }
        .sheet(isPresented: $isShowingTechnicalInfoSheet) {
            attendanceTechnicalInfoSheet(theme: theme)
                .hrpSheetPresentation(detents: [.medium])
        }
    }

    // MARK: - Top Tab Bar (`Chấm công` | `Giải trình chấm công` | `Xin nghỉ phép` | `Xét duyệt giải trình` | `Duyệt nghỉ`)

    @ViewBuilder
    private func attendanceTopTabBar(
        tabs: [AttendanceAreaTab],
        selectedTab: AttendanceAreaTab,
        theme: MiniBiThemePalette
    ) -> some View {
        let approvalTabs = tabs.filter {
            $0 == .explanationApproval || $0 == .leaveApproval
        }
        let personalTabs = tabs.filter {
            $0 != .explanationApproval && $0 != .leaveApproval
        }
        let tabRows: [[AttendanceAreaTab]] = {
            if !personalTabs.isEmpty && !approvalTabs.isEmpty {
                return [personalTabs, approvalTabs]
            } else if tabs.count > 3 {
                return [Array(tabs.prefix(3)), Array(tabs.dropFirst(3))]
            } else {
                return [tabs]
            }
        }()

        VStack(spacing: 4) {
            ForEach(Array(tabRows.enumerated()), id: \.offset) { _, rowTabs in
                let isThreeColumnRow = rowTabs.count >= 3
                HStack(spacing: 4) {
                    ForEach(rowTabs) { tab in
                        attendanceTopTabButton(
                            tab: tab,
                            isSelected: tab == selectedTab,
                            isCompactRow: isThreeColumnRow,
                            theme: theme
                        )
                    }
                }
            }
        }
        .padding(4)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 4)
    }

    private func attendanceTopTabButton(
        tab: AttendanceAreaTab,
        isSelected: Bool,
        isCompactRow: Bool,
        theme: MiniBiThemePalette
    ) -> some View {
        Button {
            KeyboardDismissal.dismiss()
            if !isSelected {
                HapticsManager.selection()
            }
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                viewModel.selectAttendanceTab(tab)
            }
        } label: {
            HStack(spacing: isCompactRow ? 3 : 5) {
                Image(systemName: tab.systemIconName)
                    .font(.system(size: isCompactRow ? 10 : 11, weight: .bold))
                Text(tab.label)
                    .font(.system(size: isCompactRow ? 11.5 : 12.5, weight: isSelected ? .bold : .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            .frame(maxWidth: .infinity, minHeight: 38)
            .padding(.horizontal, isCompactRow ? 4 : 8)
            .background {
                if isSelected {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(theme.primary)
                        .matchedGeometryEffect(id: "attendance_tab_pill", in: attendanceTabNamespace)
                }
            }
            .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Tab 1: Check-In Content (Hero Clock Card + Pending Outbox + Date Navigator Timeline)

    private static let heroClockTimeFormatter: DateFormatter = {
        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "HH:mm:ss"
        return df
    }()

    private static let heroClockDateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.locale = Locale(identifier: "vi_VN")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "EEEE, dd/MM/yyyy"
        return df
    }()

    private func formatHeroLiveClock(epochMs: Int64) -> (time: String, date: String) {
        let d = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let rawDate = Self.heroClockDateFormatter.string(from: d)
        let capDate = rawDate.prefix(1).uppercased() + rawDate.dropFirst()
        return (Self.heroClockTimeFormatter.string(from: d), capDate)
    }

    private func checkInTabContent(theme: MiniBiThemePalette) -> some View {
        let eval = viewModel.attendanceTimeEvaluation
        let _ = liveClockTick
        let resolvedNow = viewModel.timeAnchorManager.resolveCaptureTime()
        let heroClock = formatHeroLiveClock(epochMs: resolvedNow.capturedEpochMs)
        let pendingOutboxItems = viewModel.attendanceOutboxItems.filter {
            $0.status != AttendanceContract.statusSynced
        }
        let selectedDateSyncedItems = viewModel.attendanceOutboxItems.filter {
            $0.status == AttendanceContract.statusSynced &&
                AttendanceOutboxService.vietnamDateIso(for: $0) == viewModel.selectedAttendanceHistoryDate
        }

        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 1. Hero Check-in Card (Unified Live Clock + Status Pills + Primary CTA)
                VStack(spacing: 14) {
                    // Top Row: Date Label + Technical Info Popover/Sheet Button
                    HStack {
                        HStack(spacing: 6) {
                            Image(systemName: "calendar.badge.clock")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(theme.primary)
                            Text(heroClock.date)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        Spacer()
                        Button {
                            HapticsManager.selection()
                            isShowingTechnicalInfoSheet = true
                        } label: {
                            Image(systemName: "info.circle")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Thông số kỹ thuật giờ và GPS")
                    }
                    .padding(.bottom, -8)

                    // Center Hero Clock (`36pt`, `.bold`, `.monospacedDigit()`)
                    Text(heroClock.time)
                        .font(.system(size: 36, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .animation(.default, value: liveClockTick)
                        .foregroundColor(SemanticColors.textPrimary)

                    // Compact Status Pills Row (Time Anchor Pill + GPS Pill)
                    HStack(spacing: 8) {
                        Button {
                            HapticsManager.lightImpact()
                            Task { await viewModel.syncAttendanceTimeAnchor() }
                        } label: {
                            HStack(spacing: 5) {
                                Circle()
                                    .fill(eval.isTrusted ? SemanticColors.successText : SemanticColors.warningText)
                                    .frame(width: 7, height: 7)
                                Text(eval.isTrusted ? "Giờ chuẩn" : "Giờ tạm")
                                    .font(.caption2.weight(.bold))
                                    .foregroundColor(eval.isTrusted ? SemanticColors.successText : SemanticColors.warningText)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(eval.isTrusted ? SemanticColors.successBg : SemanticColors.warningBg)
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)

                        if let loc = locationManager.currentLocation {
                            Button {
                                HapticsManager.selection()
                                locationManager.requestLocationUpdate()
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "location.fill")
                                        .font(.system(size: 10, weight: .bold))
                                    Text(String(format: "GPS ±%.0fm", max(1, loc.horizontalAccuracy)))
                                        .font(.caption2.weight(.semibold))
                                        .monospacedDigit()
                                }
                                .foregroundColor(theme.primary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(theme.primaryLight)
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        } else if locationManager.isPermissionDenied {
                            Button {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "location.slash.fill")
                                        .font(.system(size: 10, weight: .bold))
                                    Text("Cấp quyền GPS")
                                        .font(.caption2.weight(.bold))
                                }
                                .foregroundColor(SemanticColors.dangerText)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(SemanticColors.dangerBg)
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        } else {
                            Button {
                                HapticsManager.selection()
                                locationManager.requestLocationUpdate()
                            } label: {
                                HStack(spacing: 4) {
                                    ProgressView()
                                        .scaleEffect(0.65)
                                    Text("Đang lấy GPS…")
                                        .font(.caption2.weight(.medium))
                                }
                                .foregroundColor(SemanticColors.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(SemanticColors.surfaceMuted)
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    // Captured Photo Preview & Note (Progressive Disclosure when photo is taken)
                    if let photoData = capturedPhotoData, let uiImg = UIImage(data: photoData) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 12) {
                                Button {
                                    HapticsManager.selection()
                                    isShowingCapturedPhotoZoomSheet = true
                                } label: {
                                    ZStack(alignment: .bottomTrailing) {
                                        Image(uiImage: uiImg)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 72, height: 72)
                                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                                        Image(systemName: "magnifyingglass.circle.fill")
                                            .font(.system(size: 17))
                                            .foregroundColor(.white)
                                            .shadow(radius: 2)
                                            .padding(4)
                                    }
                                }
                                .buttonStyle(.plain)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Đã khoá mốc giờ chụp")
                                        .font(.caption.weight(.bold))
                                        .foregroundColor(SemanticColors.successText)
                                    if let frozen = frozenCaptureTimestamp {
                                        Text(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: frozen.capturedAtIso, fallbackEpochMs: frozen.capturedEpochMs))
                                            .font(.caption.weight(.semibold))
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textPrimary)
                                    }
                                    Button {
                                        HapticsManager.lightImpact()
                                        isShowingCamera = true
                                    } label: {
                                        Label("Chụp lại ảnh", systemImage: "camera.rotate")
                                            .font(.caption2.weight(.bold))
                                            .foregroundColor(theme.primary)
                                    }
                                    .buttonStyle(.plain)
                                }
                                Spacer()
                                Button {
                                    HapticsManager.selection()
                                    rawCapturedPhotoData = nil
                                    capturedPhotoData = nil
                                    frozenCaptureTimestamp = nil
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(SemanticColors.dangerText)
                                        .frame(width: 40, height: 40)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }

                            beautyPresetSelectorRow(theme: theme)

                            TextField("Ghi chú ca làm, địa điểm (tuỳ chọn)…", text: $noteInput)
                                .font(.caption)
                                .foregroundColor(SemanticColors.textPrimary)
                                .padding(10)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous)
                                        .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                )
                        }
                        .padding(10)
                        .background(SemanticColors.successBg.opacity(0.42))
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))

                        let canSubmitAttendance = locationManager.currentLocation != nil && !viewModel.isSubmittingAttendance
                        Button {
                            guard let loc = locationManager.currentLocation else {
                                HapticsManager.error()
                                locationManager.requestLocationUpdate()
                                viewModel.showBanner("Đang lấy tọa độ GPS. Vui lòng bật Vị trí và đợi GPS định vị xong rồi bấm chấm công.", isError: true)
                                return
                            }
                            HapticsManager.success()
                            let lat = loc.coordinate.latitude
                            let lng = loc.coordinate.longitude
                            let acc = loc.horizontalAccuracy >= 0 ? loc.horizontalAccuracy : 15.0
                            let ts = frozenCaptureTimestamp ?? viewModel.timeAnchorManager.resolveCaptureTime()

                            Task {
                                await viewModel.submitAttendanceCheckIn(
                                    lat: lat,
                                    lng: lng,
                                    accuracyM: acc,
                                    photoData: photoData,
                                    note: noteInput,
                                    frozenTimestamp: ts
                                )
                                rawCapturedPhotoData = nil
                                capturedPhotoData = nil
                                frozenCaptureTimestamp = nil
                                noteInput = ""
                            }
                        } label: {
                            HStack(spacing: 8) {
                                if viewModel.isSubmittingAttendance {
                                    ProgressView().tint(.white)
                                } else {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 16, weight: .bold))
                                }
                                Text(viewModel.isSubmittingAttendance ? "Đang xử lý chấm công..." : "Ghi nhận chấm công")
                                    .font(.subheadline.weight(.bold))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .background(!canSubmitAttendance ? Color.gray.opacity(0.65) : SemanticColors.successText)
                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                        .disabled(!canSubmitAttendance)
                    } else {
                        // Primary Hero Action Button ("CHẤM CÔNG NGAY" + "Hôm nay: X lượt" centered 2-line VStack)
                        Button {
                            HapticsManager.lightImpact()
                            locationManager.requestLocationUpdate()
                            if !viewModel.timeAnchorManager.evaluateAnchor().isTrusted {
                                Task { _ = await viewModel.syncAttendanceTimeAnchorOnly(force: false, isAutomatic: true) }
                            }
                            isShowingCamera = true
                        } label: {
                            VStack(alignment: .center, spacing: 4) {
                                HStack(spacing: 8) {
                                    Image(systemName: "camera.fill")
                                        .font(.system(size: 18, weight: .bold))
                                    Text("CHẤM CÔNG NGAY")
                                        .font(.system(size: 17, weight: .heavy, design: .rounded))
                                        .tracking(0.4)
                                        .lineLimit(1)
                                }
                                Text("Hôm nay: \(viewModel.todayServerAttendanceCount) lượt")
                                    .font(.caption.weight(.semibold))
                                    .monospacedDigit()
                                    .contentTransition(.numericText())
                                    .foregroundColor(Color.white.opacity(0.92))
                                    .lineLimit(1)
                            }
                            .multilineTextAlignment(.center)
                            .padding(.vertical, 12)
                            .padding(.horizontal, 16)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 62)
                            .background(theme.primary)
                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                    }
                }
                .padding(16)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                // 2. Dedicated Offline Outbox Section (Always visible at top if any record is pending/failed)
                if !pendingOutboxItems.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            HStack(spacing: 6) {
                                Image(systemName: "icloud.and.arrow.up.fill")
                                    .foregroundColor(SemanticColors.warningText)
                                Text("Hàng đợi chờ đồng bộ (\(pendingOutboxItems.count))")
                                    .font(.subheadline.weight(.bold))
                                    .monospacedDigit()
                                    .foregroundColor(SemanticColors.textPrimary)
                            }
                            Spacer()
                            Button {
                                HapticsManager.lightImpact()
                                Task { await viewModel.flushAttendanceOutboxNow() }
                            } label: {
                                HStack(spacing: 4) {
                                    if viewModel.isSyncingAttendance {
                                        ProgressView().scaleEffect(0.7)
                                    } else {
                                        Image(systemName: "arrow.clockwise")
                                    }
                                    Text("Gửi bù")
                                }
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .frame(minHeight: 34)
                                .background(theme.primaryLight)
                                .foregroundColor(theme.primary)
                                .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                            }
                            .buttonStyle(HrpPressableButtonStyle())
                        }

                        LazyVStack(spacing: 8) {
                            ForEach(pendingOutboxItems) { item in
                                HStack(spacing: 10) {
                                    AttendanceOutboxPhotoThumbnailView(
                                        item: item,
                                        outboxService: viewModel.attendanceOutboxService,
                                        size: 44
                                    )
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: item.clientCapturedAt, fallbackEpochMs: item.createdAtEpochMs))
                                            .font(.caption.weight(.bold))
                                            .monospacedDigit()
                                            .foregroundColor(SemanticColors.textPrimary)
                                        if let err = item.lastErrorMessage, !err.isEmpty {
                                            Text(err)
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.dangerText)
                                                .lineLimit(2)
                                        } else if !item.note.isEmpty {
                                            Text(item.note)
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.textSecondary)
                                                .lineLimit(1)
                                        }
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 4) {
                                        Text(AttendanceContract.statusDisplayLabel(item.status))
                                            .font(.caption2.weight(.bold))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(outboxBgColor(item.status))
                                            .foregroundColor(outboxTextColor(item.status))
                                            .clipShape(Capsule())
                                        if item.status == AttendanceContract.statusFailedPermanent {
                                            Button("Xoá") {
                                                HapticsManager.warning()
                                                viewModel.attendanceOutboxService.removeRejectedItem(localId: item.localId)
                                            }
                                            .font(.caption2.weight(.bold))
                                            .foregroundColor(SemanticColors.dangerText)
                                        }
                                    }
                                }
                                .padding(10)
                                .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    HapticsManager.selection()
                                    selectedPreviewOutboxItem = item
                                }
                            }
                        }
                    }
                    .padding(14)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                }

                // 3. Date Navigator Bar + Clean Vertical Timeline History
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Lịch sử chấm công")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Spacer()
                        Button {
                            HapticsManager.lightImpact()
                            Task { _ = await viewModel.refreshAttendanceHistoryOnly(force: true) }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.clockwise")
                                Text("Làm mới")
                            }
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10)
                            .frame(minHeight: 32)
                            .background(theme.primaryLight)
                            .foregroundColor(theme.primary)
                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                        }
                        .buttonStyle(HrpPressableButtonStyle())

                        Button {
                            HapticsManager.lightImpact()
                            Task {
                                let userKey = viewModel.currentUser?.username ?? "guest"
                                let res = await viewModel.attendanceOutboxService.exportAttendanceReport(
                                    userKey: userKey,
                                    startDate: viewModel.selectedAttendanceHistoryDate,
                                    endDate: viewModel.selectedAttendanceHistoryDate
                                )
                                switch res {
                                case .success(let url, _):
                                    exportedReportUrl = url
                                case .failure(let msg, _, _, _):
                                    viewModel.showBanner(msg, isError: true)
                                }
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "square.and.arrow.up")
                                Text("Xuất BC")
                            }
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10)
                            .frame(minHeight: 32)
                            .background(SemanticColors.surfaceMuted)
                            .foregroundColor(SemanticColors.textPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                    }

                    HrpDateNavigatorBar(
                        selectedDateIso: viewModel.selectedAttendanceHistoryDate,
                        todayDateIso: LeaveContract.todayVietnamIsoDate(),
                        allowFutureDates: false,
                        theme: theme,
                        onDateSelected: { newDate in
                            viewModel.selectAttendanceHistoryDate(newDate)
                        }
                    )

                    if selectedDateSyncedItems.isEmpty {
                        HStack(spacing: 8) {
                            Image(systemName: "clock.badge.questionmark")
                                .font(.system(size: 18))
                                .foregroundColor(SemanticColors.textMuted)
                            Text("Không có lượt chấm công nào trong ngày \(LeaveContract.formatIsoDateVi(viewModel.selectedAttendanceHistoryDate)).")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(selectedDateSyncedItems.enumerated()), id: \.element.id) { idx, item in
                                let isLast = idx == selectedDateSyncedItems.count - 1
                                HStack(alignment: .top, spacing: 12) {
                                    // Left Column: HH:mm + vertical timeline connector
                                    VStack(spacing: 4) {
                                        Text(AttendanceOutboxService.formatVietnamTimeHHmm(for: item))
                                            .font(.system(size: 15, weight: .bold, design: .rounded))
                                            .monospacedDigit()
                                            .foregroundColor(theme.primary)
                                            .frame(width: 48, alignment: .leading)
                                        if !isLast {
                                            Rectangle()
                                                .fill(theme.primary.opacity(0.22))
                                                .frame(width: 2, height: 32)
                                        }
                                    }

                                    // Middle Column: 44x44pt Squircle Thumbnail + Short Note
                                    AttendanceOutboxPhotoThumbnailView(
                                        item: item,
                                        outboxService: viewModel.attendanceOutboxService,
                                        size: 44
                                    )

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(item.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Đã chấm công" : item.note)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .lineLimit(1)
                                        Text("Chạm để xem ảnh & vị trí GPS")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    }

                                    Spacer(minLength: 4)

                                    // Right Column: Compact Status Badge + Chevron
                                    HStack(spacing: 6) {
                                        Text(AttendanceContract.statusDisplayLabel(item.status))
                                            .font(.caption2.weight(.bold))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(outboxBgColor(item.status))
                                            .foregroundColor(outboxTextColor(item.status))
                                            .clipShape(Capsule())
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 11, weight: .semibold))
                                            .foregroundColor(SemanticColors.textMuted)
                                    }
                                    .fixedSize(horizontal: true, vertical: false)
                                }
                                .padding(.vertical, 10)
                                .padding(.horizontal, 12)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    HapticsManager.selection()
                                    selectedPreviewOutboxItem = item
                                }
                                if !isLast {
                                    Divider().padding(.leading, 64)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                        if viewModel.attendanceHistoryHasMore {
                            HrpLoadMoreButton(
                                title: "Tải thêm lịch sử ngày này",
                                loadedCount: selectedDateSyncedItems.count,
                                totalCount: viewModel.attendanceHistoryTotalCount > selectedDateSyncedItems.count ? viewModel.attendanceHistoryTotalCount : nil,
                                isLoading: viewModel.isLoadingMoreAttendanceHistory,
                                theme: theme
                            ) {
                                Task { await viewModel.loadMoreAttendanceHistory() }
                            }
                        }
                    }
                }
            }
            .padding(14)
        }
        .refreshable {
            locationManager.requestLocationUpdate()
            await viewModel.syncAttendanceTimeAnchor()
            _ = await viewModel.refreshAttendanceHistoryOnly(force: true)
        }
    }

    @ViewBuilder
    private func attendanceTechnicalInfoSheet(theme: MiniBiThemePalette) -> some View {
        let eval = viewModel.attendanceTimeEvaluation
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Image(systemName: eval.isTrusted ? "clock.badge.checkmark.fill" : "clock.badge.exclamationmark.fill")
                            .foregroundColor(eval.isTrusted ? SemanticColors.successText : SemanticColors.warningText)
                        Text(eval.isTrusted ? "Đã neo giờ chuẩn máy chủ" : "Đang dùng giờ thiết bị tạm thời")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(eval.isTrusted ? SemanticColors.successText : SemanticColors.warningText)
                    }
                    Text(eval.detailMessageVi)
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundColor(SemanticColors.textSecondary)

                    Button {
                        HapticsManager.lightImpact()
                        Task { await viewModel.syncAttendanceTimeAnchor() }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text("Đồng bộ giờ máy chủ ngay")
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .frame(minHeight: 38)
                        .background(theme.primaryLight)
                        .foregroundColor(theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                    }
                    .buttonStyle(HrpPressableButtonStyle())
                    .padding(.top, 4)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Image(systemName: "location.fill")
                            .foregroundColor(theme.primary)
                        Text("Thông số định vị GPS")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                    }
                    if let loc = locationManager.currentLocation {
                        Text(String(format: "Vĩ độ / Kinh độ: %.6f, %.6f", loc.coordinate.latitude, loc.coordinate.longitude))
                            .font(.caption.weight(.semibold))
                            .monospacedDigit()
                            .foregroundColor(SemanticColors.textPrimary)
                        Text(String(format: "Sai số ngang: ±%.1f m", max(0, loc.horizontalAccuracy)))
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundColor(SemanticColors.textSecondary)
                    } else {
                        Text(locationManager.locationErrorMessage ?? "Đang định vị GPS…")
                            .font(.caption)
                            .foregroundColor(locationManager.isPermissionDenied ? SemanticColors.dangerText : SemanticColors.textSecondary)
                    }

                    Button {
                        HapticsManager.selection()
                        locationManager.requestLocationUpdate()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "location.magnifyingglass")
                            Text("Lấy lại toạ độ GPS")
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .frame(minHeight: 38)
                        .background(SemanticColors.surfaceMuted)
                        .foregroundColor(SemanticColors.textPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                    }
                    .buttonStyle(HrpPressableButtonStyle())
                    .padding(.top, 4)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                Spacer()
            }
            .padding(16)
            .background(theme.background)
            .navigationTitle("Chi tiết giờ chuẩn & GPS")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Đóng") {
                        isShowingTechnicalInfoSheet = false
                    }
                }
            }
        }
    }

    // MARK: - Beauty Filter Selector & Full-Screen Preview Sheet

    private func applyBeautyPreset(_ preset: AttendanceBeautyPreset) {
        HapticsManager.selection()
        selectedBeautyPreset = preset
        AttendanceBeautyFilter.savePreset(preset)
        guard let rawData = rawCapturedPhotoData else { return }
        capturedPhotoData = AttendanceBeautyFilter.apply(to: rawData, preset: preset)
    }

    @ViewBuilder
    private func beautyPresetSelectorRow(theme: MiniBiThemePalette) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                Image(systemName: "sparkles")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(theme.primary)
                Text("Chế độ làm đẹp:")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                Text(selectedBeautyPreset.labelVi)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(theme.primary)
            }

            HStack(spacing: 6) {
                ForEach(AttendanceBeautyPreset.allCases) { preset in
                    let isSelected = selectedBeautyPreset == preset
                    Button {
                        applyBeautyPreset(preset)
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: preset.iconName)
                                .font(.system(size: 10, weight: .semibold))
                            Text(preset.labelVi)
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                        .background(isSelected ? theme.primary : Color.white)
                        .foregroundColor(isSelected ? .white : SemanticColors.textPrimary)
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(isSelected ? theme.primary : SemanticColors.borderSubtle, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private func capturedPhotoBeautyPreviewSheet(theme: MiniBiThemePalette) -> some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let photoData = capturedPhotoData, let uiImg = UIImage(data: photoData) {
                    Image(uiImage: uiImg)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: 440)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                        .padding(.horizontal, 16)
                }

                VStack(alignment: .leading, spacing: 10) {
                    beautyPresetSelectorRow(theme: theme)

                    if let frozen = frozenCaptureTimestamp {
                        HStack(spacing: 6) {
                            Image(systemName: "clock.badge.checkmark.fill")
                                .foregroundColor(SemanticColors.successText)
                            Text("Giờ chụp đã khoá: \(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: frozen.capturedAtIso, fallbackEpochMs: frozen.capturedEpochMs))")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
                .padding(.horizontal, 16)

                Spacer()
            }
            .padding(.top, 12)
            .background(theme.background)
            .navigationTitle("Xem trước ảnh chấm công")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Xong") {
                        isShowingCapturedPhotoZoomSheet = false
                    }
                    .font(.subheadline.weight(.bold))
                }
            }
        }
    }

    // MARK: - Tab 2: Leave Request ("Xin nghỉ") Content

    private func leaveRequestTabContent(theme: MiniBiThemePalette) -> some View {
        let currentViYear = LeaveContract.currentVietnamLeaveYear()
        let yearOptions = LeaveContract.selectableHistoryLeaveYears(currentYear: currentViYear)
        let totalDraftHalfDayUnits = viewModel.leaveDraftDays.reduce(0) {
            $0 + LeaveContract.leaveSessionHalfDayUnits($1.sessionCode)
        }

        return ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let err = viewModel.myLeaveError, !err.isEmpty {
                    HStack {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(SemanticColors.dangerText)
                        Spacer()
                        Button("Thử lại") {
                            Task { await viewModel.refreshMyLeaveSummary(year: viewModel.leaveYear) }
                        }
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.dangerText)
                    }
                    .padding(12)
                    .background(SemanticColors.dangerBg)
                    .cornerRadius(12)
                }

                if viewModel.isLoadingMyLeave && viewModel.myLeaveSummary == nil {
                    HStack(spacing: 10) {
                        Spacer()
                        ProgressView()
                        Text("Đang tải số dư phép và lịch sử đơn từ máy chủ...")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                        Spacer()
                    }
                    .padding(24)
                    .background(Color.white)
                    .cornerRadius(14)
                }

                if let summary = viewModel.myLeaveSummary {
                    leaveCreateFormCard(
                        summary: summary,
                        totalDraftHalfDayUnits: totalDraftHalfDayUnits,
                        theme: theme
                    )
                }

                // Compact History & Balance Year Switcher Card
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("TỒN PHÉP & LỊCH SỬ NGHỈ")
                            .font(.caption2.weight(.bold))
                            .foregroundColor(theme.primary)
                            .lineLimit(1)
                        Text("Tra cứu năm \(String(viewModel.leaveYear))")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                            .lineLimit(1)
                        Text("Chọn năm trước để xem lịch sử nghỉ & tồn công phép")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    HStack(spacing: 4) {
                        HStack(spacing: 3) {
                            ForEach(yearOptions, id: \.self) { y in
                                let isYearSelected = viewModel.leaveYear == y
                                Button {
                                    viewModel.setLeaveYear(y)
                                } label: {
                                    Text(String(y))
                                        .font(.caption.weight(isYearSelected ? .bold : .medium))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(isYearSelected ? theme.primary : Color.clear)
                                        .foregroundColor(isYearSelected ? .white : SemanticColors.textSecondary)
                                        .cornerRadius(7)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(3)
                        .background(Color(hex: 0xF1F5F9))
                        .cornerRadius(9)

                        Button {
                            Task { await viewModel.refreshMyLeaveSummary(year: viewModel.leaveYear) }
                        } label: {
                            if viewModel.isLoadingMyLeave {
                                ProgressView()
                                    .scaleEffect(0.75)
                                    .frame(width: 28, height: 28)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .font(.caption.weight(.bold))
                                    .frame(width: 28, height: 28)
                                    .background(theme.primaryLight)
                                    .foregroundColor(theme.primary)
                                    .cornerRadius(8)
                            }
                        }
                        .disabled(viewModel.isLoadingMyLeave)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Color.white)
                .cornerRadius(14)

                if let summary = viewModel.myLeaveSummary {
                    leaveBalanceAndRoutingCard(summary: summary, theme: theme)
                    leaveMyRequestHistoryCard(summary: summary, theme: theme)
                    if !summary.balance.lots.isEmpty {
                        leaveEntitlementLotsCard(summary: summary)
                    }
                }
            }
            .padding(14)
        }
        .refreshable {
            await viewModel.refreshMyLeaveSummary(year: viewModel.leaveYear)
        }
        .onAppear {
            viewModel.ensureInitialLeaveDraftDay()
            if viewModel.myLeaveSummary == nil && !viewModel.isLoadingMyLeave {
                Task { await viewModel.refreshMyLeaveSummary(year: viewModel.leaveYear) }
            }
        }
    }

    private func leaveBalanceAndRoutingCard(
        summary: LeaveMeSummaryProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        let balance = summary.balance
        let routing = summary.routing

        return VStack(alignment: .leading, spacing: 12) {
            Text("Số dư phép & Thông tin cá nhân (tính đến \(LeaveContract.formatIsoDateVi(summary.asOfDate)))")
                .font(.subheadline.weight(.bold))
                .foregroundColor(SemanticColors.textPrimary)

            HStack(spacing: 10) {
                leaveStatMetricTile(
                    title: "Phép khả dụng",
                    value: LeaveContract.formatHalfDayUnits(balance.availableUnits),
                    subtitle: "Phép tồn còn dùng: \(LeaveContract.formatHalfDayUnits(balance.carryOverAvailableUnits))",
                    accentColor: SemanticColors.successText,
                    bgColor: SemanticColors.successBg.opacity(0.55)
                )
                leaveStatMetricTile(
                    title: "Tổng phép được cấp",
                    value: LeaveContract.formatHalfDayUnits(balance.totalGrantedUnits),
                    subtitle: "Định kỳ: \(LeaveContract.formatHalfDayUnits(balance.monthlyAccruedUnits)) • Thâm niên: \(LeaveContract.formatHalfDayUnits(balance.seniorityGrantedUnits))",
                    accentColor: theme.primary,
                    bgColor: theme.primaryLight
                )
            }

            HStack(spacing: 10) {
                let totalPendingUnits = balance.pendingAnnualUnits + balance.pendingNonAnnualUnits
                leaveStatMetricTile(
                    title: "Giữ chỗ / Chờ duyệt",
                    value: LeaveContract.formatHalfDayUnits(balance.reservedUnits + totalPendingUnits),
                    subtitle: "Giữ chỗ phép năm: \(LeaveContract.formatHalfDayUnits(balance.reservedUnits)) • Chờ duyệt: \(LeaveContract.formatHalfDayUnits(totalPendingUnits))",
                    accentColor: SemanticColors.warningText,
                    bgColor: SemanticColors.warningBg.opacity(0.6)
                )
                leaveStatMetricTile(
                    title: "Đã sử dụng",
                    value: LeaveContract.formatHalfDayUnits(balance.consumedUnits),
                    subtitle: "Hết hạn: \(LeaveContract.formatHalfDayUnits(balance.expiredUnits)) • Ngoài phép năm: \(LeaveContract.formatHalfDayUnits(balance.approvedNonAnnualUnits))",
                    accentColor: SemanticColors.textSecondary,
                    bgColor: Color(hex: 0xF1F5F9)
                )
            }

            VStack(alignment: .leading, spacing: 5) {
                Text("Trạng thái nhân sự: \(LeaveContract.leaveEmploymentStatusLabel(balance.employmentStatus)) • Đơn vị: \(LeaveContract.leaveUnitKindLabel(balance.unitKind))")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(SemanticColors.textPrimary)
                Text("Luồng duyệt đơn: \(LeaveContract.leaveRouteKindLabel(routing.routeKind, teamName: routing.teamName, unitKind: routing.unitKind))")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textPrimary)
                Text("Ngày vào làm: \(LeaveContract.formatIsoDateVi(balance.workStartDate)) • Lên chính thức: \(LeaveContract.formatIsoDateVi(balance.officialEffectiveDate))")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textSecondary)

                if let nextSeniorityDate = balance.nextSeniorityEffectiveDate, balance.nextSeniorityUnits > 0 {
                    Text("Mốc tăng phép thâm niên tiếp theo: \(LeaveContract.formatIsoDateVi(nextSeniorityDate)) (+\(LeaveContract.formatHalfDayUnits(balance.nextSeniorityUnits)))")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }
                if let carryOverExp = summary.policy.carryOverExpiresAfterDate {
                    Text("Hạn sử dụng phép tồn năm trước: đến hết \(LeaveContract.formatIsoDateVi(carryOverExp))")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(hex: 0xF8FAFC))
            .cornerRadius(12)

            if !balance.profileConfigured || balance.employmentStatus.uppercased() == "PROBATION" {
                Text(
                    !balance.profileConfigured
                        ? "Hồ sơ nghỉ phép của bạn chưa được cấu hình đầy đủ trạng thái Chính thức nên số dư Phép năm hiện bằng 0. Bạn vẫn có thể chọn các loại nghỉ ngoài phép năm (Nghỉ tang, Nghỉ cưới, Nghỉ ốm, Nghỉ không lương)."
                        : "Bạn đang trong giai đoạn Thử việc nên số dư Phép năm hiện bằng 0 (phép định kỳ sẽ cộng bù khi lên Chính thức). Trong thời gian thử việc, vui lòng chọn các loại nghỉ ngoài phép năm (Nghỉ không lương, Nghỉ ốm, Nghỉ cưới, Nghỉ tang)."
                )
                .font(.caption)
                .foregroundColor(SemanticColors.warningText)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(SemanticColors.warningBg)
                .cornerRadius(10)
            }

            if let fallbackLabel = LeaveContract.leaveFallbackReasonLabel(routing.fallbackReason) {
                Text("Lưu ý định tuyến duyệt: \(fallbackLabel). Đơn xin nghỉ sẽ được chuyển thẳng tới hàng đợi Admin / Ban Giám đốc.")
                    .font(.caption)
                    .foregroundColor(SemanticColors.infoText)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(SemanticColors.infoBg.opacity(0.65))
                    .cornerRadius(10)
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
    }

    private func leaveStatMetricTile(
        title: String,
        value: String,
        subtitle: String,
        accentColor: Color,
        bgColor: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundColor(SemanticColors.textSecondary)
            Text(value)
                .font(.headline.weight(.bold))
                .foregroundColor(accentColor)
            Text(subtitle)
                .font(.caption2)
                .foregroundColor(SemanticColors.textMuted)
                .lineLimit(2)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(bgColor)
        .cornerRadius(12)
    }

    private func leaveCreateFormCard(
        summary: LeaveMeSummaryProjection,
        totalDraftHalfDayUnits: Int,
        theme: MiniBiThemePalette
    ) -> some View {
        let draftEval = LeaveContract.evaluateLeaveRequestDraft(
            days: viewModel.leaveDraftDays,
            reason: viewModel.leaveDraftReason,
            leaveYear: summary.leaveYear,
            availableUnits: summary.balance.availableUnits
        )

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Tạo đơn xin nghỉ phép")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Text("Chọn ngày, buổi và loại nghỉ (Phép năm trừ FIFO • Hỗ trợ tách 2 buổi khác loại trong cùng ngày)")
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textSecondary)
                }
                Spacer()
                Text("Tổng: \(LeaveContract.formatHalfDayUnits(totalDraftHalfDayUnits))")
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(theme.primaryLight)
                    .foregroundColor(theme.primary)
                    .cornerRadius(8)
            }

            if !summary.authority.canSubmitRequest || summary.authority.isSharedNonEmployee {
                Text("Tài khoản hiện tại không thuộc đối tượng tạo đơn xin nghỉ phép cá nhân.")
                    .font(.caption)
                    .foregroundColor(SemanticColors.dangerText)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(SemanticColors.dangerBg)
                    .cornerRadius(10)
            } else {
                Button {
                    KeyboardDismissal.dismiss()
                    let firstIso = viewModel.leaveDraftDays.first?.leaveDate ?? LeaveContract.todayVietnamIsoDate()
                    let lastIso = viewModel.leaveDraftDays.last?.leaveDate ?? firstIso
                    rangeStartDate = parseIsoDate(firstIso) ?? Date()
                    rangeEndDate = max(rangeStartDate, parseIsoDate(lastIso) ?? rangeStartDate)
                    rangeLeaveType = viewModel.leaveDraftDays.first?.leaveType
                        ?? (summary.balance.availableUnits <= 0 ? "UNPAID" : "ANNUAL")
                    rangeErrorMessage = nil
                    isShowingRangePickerSheet = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.subheadline.weight(.bold))
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Chọn nhanh theo dải ngày (Từ ngày → Đến ngày)")
                                .font(.caption.weight(.bold))
                            Text("Tự động tạo danh sách nhiều ngày nghỉ liên tiếp chỉ với 1 chạm")
                                .font(.caption2)
                                .opacity(0.85)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.primaryLight)
                    .foregroundColor(theme.primary)
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(theme.primary.opacity(0.35), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .disabled(viewModel.isMutatingLeave)

                ForEach(Array(viewModel.leaveDraftDays.enumerated()), id: \.element.id) { index, day in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Button {
                                KeyboardDismissal.dismiss()
                                datePickerModeIsRange = false
                                datePickerSelection = parseIsoDate(day.leaveDate) ?? Date()
                                rangeStartDate = datePickerSelection
                                rangeEndDate = max(
                                    rangeStartDate,
                                    parseIsoDate(viewModel.leaveDraftDays.last?.leaveDate ?? day.leaveDate) ?? datePickerSelection
                                )
                                rangeLeaveType = day.leaveType
                                rangeErrorMessage = nil
                                datePickerTargetDay = day
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: "calendar")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(theme.primary)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Ngày nghỉ #\(index + 1) (Chạm chọn lịch)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                        Text("\(LeaveContract.formatIsoDateVi(day.leaveDate)) (\(day.leaveDate))")
                                            .font(.caption.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                    }
                                    Spacer(minLength: 4)
                                    Image(systemName: "chevron.down")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(theme.primary)
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white)
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(theme.primary.opacity(0.35), lineWidth: 1)
                                )
                            }
                            .buttonStyle(.plain)

                            if day.sessionCode.uppercased() == "FULL" && viewModel.leaveDraftDays.count < 120 {
                                Button {
                                    KeyboardDismissal.dismiss()
                                    viewModel.splitLeaveDraftDayToHalfDays(id: day.id)
                                } label: {
                                    Text("Tách 2 buổi")
                                        .font(.caption2.weight(.semibold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 10)
                                        .background(Color.white)
                                        .foregroundColor(theme.primary)
                                        .cornerRadius(8)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(theme.primary.opacity(0.45), lineWidth: 1)
                                        )
                                }
                                .disabled(viewModel.isMutatingLeave)
                            }

                            if viewModel.leaveDraftDays.count > 1 {
                                Button {
                                    KeyboardDismissal.dismiss()
                                    viewModel.removeLeaveDraftDay(id: day.id)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.caption.weight(.bold))
                                        .foregroundColor(SemanticColors.dangerText)
                                        .padding(10)
                                        .background(SemanticColors.dangerBg)
                                        .cornerRadius(8)
                                }
                                .disabled(viewModel.isMutatingLeave)
                            }
                        }

                        HStack(spacing: 6) {
                            ForEach(LeaveContract.sessionCodes, id: \.self) { code in
                                let isSelected = day.sessionCode.uppercased() == code
                                Button {
                                    KeyboardDismissal.dismiss()
                                    viewModel.updateLeaveDraftDaySession(id: day.id, sessionCode: code)
                                } label: {
                                    Text(LeaveContract.leaveSessionCompactLabel(code))
                                        .font(.caption.weight(isSelected ? .bold : .medium))
                                        .lineLimit(1)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 7)
                                        .frame(maxWidth: .infinity)
                                        .background(isSelected ? theme.primary : Color.white)
                                        .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
                                        .cornerRadius(8)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(isSelected ? theme.primary : SemanticColors.borderSubtle, lineWidth: 1)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Loại nghỉ: \(LeaveContract.leaveTypeLabel(day.leaveType))")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(LeaveContract.leaveTypes, id: \.self) { typeCode in
                                        let isSelected = day.leaveType.uppercased() == typeCode
                                        Button {
                                            KeyboardDismissal.dismiss()
                                            viewModel.updateLeaveDraftDayType(id: day.id, leaveType: typeCode)
                                        } label: {
                                            Text(LeaveContract.leaveTypeShortLabel(typeCode))
                                                .font(.caption.weight(isSelected ? .bold : .medium))
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 6)
                                                .background(isSelected ? theme.primaryLight : Color.white)
                                                .foregroundColor(isSelected ? theme.primary : SemanticColors.textSecondary)
                                                .cornerRadius(8)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .stroke(isSelected ? theme.primary : SemanticColors.borderSubtle, lineWidth: 1)
                                                )
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(12)
                }

                VStack(alignment: .leading, spacing: 6) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(draftEval.typeBreakdown) { tb in
                                Text("\(LeaveContract.leaveTypeShortLabel(tb.leaveType)): \(LeaveContract.formatHalfDayUnits(tb.halfDayUnits))")
                                    .font(.caption2.weight(.semibold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(tb.leaveType == "ANNUAL" ? theme.primaryLight : Color(hex: 0xE2E8F0))
                                    .foregroundColor(tb.leaveType == "ANNUAL" ? theme.primary : SemanticColors.textPrimary)
                                    .cornerRadius(6)
                            }
                            Text("• Phép năm khả dụng: \(LeaveContract.formatHalfDayUnits(summary.balance.availableUnits))")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                    }
                    if draftEval.nonAnnualUnits > 0 {
                        Text("Các ngày nghỉ ngoài phép năm (Nghỉ tang, Nghỉ cưới, Nghỉ ốm, Nghỉ không lương) không trừ vào số dư phép năm khả dụng.")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(10)

                if draftEval.canSplitExcessAnnualToUnpaid {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Số ngày Phép năm đang chọn (\(LeaveContract.formatHalfDayUnits(draftEval.annualUnits))) vượt quá số dư khả dụng (\(LeaveContract.formatHalfDayUnits(summary.balance.availableUnits))). Bạn có thể tách phần vượt (\(LeaveContract.formatHalfDayUnits(draftEval.excessAnnualUnits))) sang Nghỉ không lương.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.warningText)
                        Button {
                            KeyboardDismissal.dismiss()
                            viewModel.autoAllocateLeaveDraftDaysByBalance()
                        } label: {
                            Text("Tự động chia Phép năm + Nghỉ không lương")
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(Color.white)
                                .foregroundColor(SemanticColors.warningText)
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(SemanticColors.warningText.opacity(0.45), lineWidth: 1)
                                )
                        }
                        .disabled(viewModel.isMutatingLeave)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(SemanticColors.warningBg)
                    .cornerRadius(10)
                }

                HStack(spacing: 8) {
                    Button {
                        KeyboardDismissal.dismiss()
                        viewModel.addLeaveDraftDay()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle.fill")
                            Text("Thêm 1 ngày kế tiếp")
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(theme.primaryLight)
                        .foregroundColor(theme.primary)
                        .cornerRadius(8)
                    }
                    .disabled(viewModel.isMutatingLeave || viewModel.leaveDraftDays.count >= 120)

                    Button {
                        KeyboardDismissal.dismiss()
                        let firstIso = viewModel.leaveDraftDays.first?.leaveDate ?? LeaveContract.todayVietnamIsoDate()
                        let lastIso = viewModel.leaveDraftDays.last?.leaveDate ?? firstIso
                        rangeStartDate = parseIsoDate(firstIso) ?? Date()
                        rangeEndDate = max(rangeStartDate, parseIsoDate(lastIso) ?? rangeStartDate)
                        rangeLeaveType = viewModel.leaveDraftDays.first?.leaveType
                            ?? (summary.balance.availableUnits <= 0 ? "UNPAID" : "ANNUAL")
                        rangeErrorMessage = nil
                        isShowingRangePickerSheet = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "calendar.badge.plus")
                            Text("Chọn dải ngày")
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.white)
                        .foregroundColor(theme.primary)
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(theme.primary.opacity(0.45), lineWidth: 1)
                        )
                    }
                    .disabled(viewModel.isMutatingLeave)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Lý do xin nghỉ (không bắt buộc)")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Spacer()
                        Button("Ẩn bàn phím") {
                            KeyboardDismissal.dismiss()
                        }
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(theme.primary)
                    }
                    TextField(
                        "Nhập lý do hoặc ghi chú đơn xin nghỉ (nếu có)...",
                        text: Binding(
                            get: { viewModel.leaveDraftReason },
                            set: { viewModel.updateLeaveDraftReason($0) }
                        ),
                        axis: .vertical
                    )
                    .lineLimit(2...5)
                    .font(.caption)
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                    )
                }

                if let mutErr = viewModel.leaveMutationError, !mutErr.isEmpty {
                    Text(mutErr)
                        .font(.caption)
                        .foregroundColor(SemanticColors.dangerText)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(SemanticColors.dangerBg)
                        .cornerRadius(10)
                }

                Button {
                    KeyboardDismissal.dismiss()
                    Task { await viewModel.submitLeaveRequest() }
                } label: {
                    HStack {
                        Spacer()
                        if viewModel.isMutatingLeave {
                            ProgressView().tint(.white).padding(.trailing, 6)
                        } else {
                            Image(systemName: "paperplane.fill")
                        }
                        Text(viewModel.isMutatingLeave ? "Đang gửi đơn xin nghỉ..." : "Gửi đơn xin nghỉ phép")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                    }
                    .foregroundColor(.white)
                    .padding(.vertical, 12)
                    .background(viewModel.isMutatingLeave ? Color.gray : theme.primary)
                    .cornerRadius(12)
                }
                .disabled(viewModel.isMutatingLeave)
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
    }

    private func leaveMyRequestHistoryCard(
        summary: LeaveMeSummaryProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        let actorId = viewModel.currentLeaveActorAppUserId
        let visibleRequests = Array(summary.requests.prefix(visibleMyLeaveRequestsCount))

        return VStack(alignment: .leading, spacing: 10) {
            Text("Lịch sử đơn xin nghỉ của tôi (\(summary.requests.count))")
                .font(.subheadline.weight(.bold))
                .foregroundColor(SemanticColors.textPrimary)

            if summary.requests.isEmpty {
                Text("Chưa có đơn xin nghỉ phép nào trong năm nghiệp vụ này.")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textSecondary)
                    .padding(.vertical, 6)
            } else {
                ForEach(visibleRequests) { req in
                    let canWithdraw = LeaveContract.canWithdrawOwnLeaveRequest(
                        actorAppUserId: actorId,
                        request: req
                    )
                    let breakdown = LeaveContract.resolveRequestTypeBreakdown(req)
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    Text(LeaveContract.leaveRequestStatusLabel(req.status))
                                        .font(.caption2.weight(.bold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(leaveStatusBgColor(req.status))
                                        .foregroundColor(leaveStatusTextColor(req.status))
                                        .cornerRadius(6)

                                    ForEach(breakdown) { tb in
                                        Text("\(LeaveContract.leaveTypeShortLabel(tb.leaveType)): \(LeaveContract.formatHalfDayUnits(tb.halfDayUnits))")
                                            .font(.caption2.weight(.semibold))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(theme.primaryLight)
                                            .foregroundColor(theme.primary)
                                            .cornerRadius(6)
                                    }
                                }
                            }
                            Spacer()
                            Text(LeaveContract.formatHalfDayUnits(req.requestedUnits))
                                .font(.caption.weight(.bold))
                                .foregroundColor(theme.primary)
                        }

                        let daysSummary = req.days.map {
                            "\(LeaveContract.formatIsoDateVi($0.leaveDate)) (\(LeaveContract.leaveSessionShortLabel($0.sessionCode)) · \(LeaveContract.leaveTypeShortLabel($0.leaveType)))"
                        }.joined(separator: ", ")

                        Text("Ngày nghỉ: \(daysSummary)")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(SemanticColors.textPrimary)
                        let cleanReason = req.reason.trimmingCharacters(in: .whitespacesAndNewlines)
                        Text("Lý do: \(cleanReason.isEmpty ? "Không ghi lý do" : cleanReason)")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Luồng duyệt: \(LeaveContract.leaveRouteKindLabel(req.routeKind, teamName: req.routedTeamName, unitKind: req.unitKindSnapshot)) • Gửi lúc: \(WorkReportsContract.formatIsoDateTimeVi(req.submittedAt))")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)

                        if let decisionReason = req.decisionReason, !decisionReason.isEmpty {
                            Text("Ghi chú người duyệt: \(decisionReason)")
                                .font(.caption)
                                .foregroundColor(leaveStatusTextColor(req.status))
                        }
                        if let cancelReason = req.cancellationReason, !cancelReason.isEmpty {
                            Text("Lý do hủy/rút: \(cancelReason)")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                        }

                        if canWithdraw {
                            HStack {
                                Spacer()
                                Button {
                                    withdrawReasonInput = ""
                                    withdrawTargetRequest = req
                                } label: {
                                    HStack(spacing: 4) {
                                        Image(systemName: "arrow.uturn.backward")
                                        Text("Rút đơn")
                                    }
                                    .font(.caption.weight(.bold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(SemanticColors.dangerBg)
                                    .foregroundColor(SemanticColors.dangerText)
                                    .cornerRadius(8)
                                }
                                .disabled(viewModel.isMutatingLeave)
                            }
                        }
                    }
                    .padding(12)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(12)
                }

                if summary.requests.count > visibleMyLeaveRequestsCount {
                    HrpLoadMoreButton(
                        title: "Tải thêm đơn xin nghỉ",
                        loadedCount: visibleRequests.count,
                        totalCount: summary.requests.count,
                        isLoading: false,
                        theme: theme
                    ) {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                            visibleMyLeaveRequestsCount += 10
                        }
                    }
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
    }

    private func leaveEntitlementLotsCard(summary: LeaveMeSummaryProjection) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Chi tiết các khoản phép năm \(String(summary.leaveYear))")
                .font(.subheadline.weight(.bold))
                .foregroundColor(SemanticColors.textPrimary)

            ForEach(summary.balance.lots) { lot in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(LeaveContract.leaveLotKindLabel(lot.lotKind, accrualMonth: lot.accrualMonth))
                            .font(.caption.weight(.semibold))
                            .foregroundColor(SemanticColors.textPrimary)
                        let expirySuffix = lot.expiresAfterDate.map { " • Hết hạn sau \(LeaveContract.formatIsoDateVi($0))" } ?? ""
                        Text("Hiệu lực từ \(LeaveContract.formatIsoDateVi(lot.usableFromDate))\(expirySuffix)")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("Còn: \(LeaveContract.formatHalfDayUnits(lot.availableUnits))")
                            .font(.caption.weight(.bold))
                            .foregroundColor(SemanticColors.successText)
                        Text("Cấp \(LeaveContract.formatHalfDayUnits(lot.grantedUnits, includeSuffix: false)) • Dùng \(LeaveContract.formatHalfDayUnits(lot.consumedUnits, includeSuffix: false))")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)
                    }
                }
                .padding(10)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(10)
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
    }

    // MARK: - Tab 3: Leave Approval ("Duyệt nghỉ") Content

    @ViewBuilder
    private func leaveApprovalTabContent(theme: MiniBiThemePalette) -> some View {
        if let authority = viewModel.myLeaveSummary?.authority,
           authority.canApproveRequests && !authority.isSharedNonEmployee {
            let inbox = viewModel.leaveApprovalInbox
            let items = inbox?.items ?? []
            let actorId = viewModel.currentLeaveActorAppUserId ?? ""

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Header & Filter Card
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("PHÊ DUYỆT NGHỈ PHÉP THEO PHẠM VI")
                                    .font(.caption2.weight(.bold))
                                    .foregroundColor(theme.primary)
                                Text("Danh sách đơn nghỉ phép (\(inbox?.total ?? 0))")
                                    .font(.headline.weight(.bold))
                                    .foregroundColor(SemanticColors.textPrimary)
                            }
                            Spacer()
                            Button {
                                Task { await viewModel.refreshLeaveApprovalInbox() }
                            } label: {
                                if viewModel.isLoadingLeaveInbox {
                                    ProgressView()
                                        .scaleEffect(0.75)
                                        .frame(width: 28, height: 28)
                                } else {
                                    Image(systemName: "arrow.clockwise")
                                        .font(.caption.weight(.bold))
                                        .frame(width: 28, height: 28)
                                        .background(theme.primaryLight)
                                        .foregroundColor(theme.primary)
                                        .cornerRadius(8)
                                }
                            }
                            .disabled(viewModel.isLoadingLeaveInbox)
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(LeaveContract.requestStatusFilters, id: \.self) { code in
                                    let isSelected = viewModel.leaveInboxStatusFilter == code
                                    Button {
                                        viewModel.setLeaveInboxFilter(code)
                                    } label: {
                                        Text(LeaveContract.leaveRequestStatusLabel(code))
                                            .font(.caption.weight(isSelected ? .bold : .medium))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(isSelected ? theme.primary : Color(hex: 0xF1F5F9))
                                            .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
                                            .cornerRadius(8)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(14)

                    if let mutErr = viewModel.leaveMutationError, !mutErr.isEmpty {
                        Text(mutErr)
                            .font(.caption)
                            .foregroundColor(SemanticColors.dangerText)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.dangerBg)
                            .cornerRadius(12)
                    }

                    if let inbErr = viewModel.leaveInboxError, !inbErr.isEmpty {
                        HStack {
                            Text(inbErr)
                                .font(.caption)
                                .foregroundColor(SemanticColors.dangerText)
                            Spacer()
                            Button("Thử lại") {
                                Task { await viewModel.refreshLeaveApprovalInbox() }
                            }
                            .font(.caption.weight(.bold))
                            .foregroundColor(SemanticColors.dangerText)
                        }
                        .padding(12)
                        .background(SemanticColors.dangerBg)
                        .cornerRadius(12)
                    }

                    if viewModel.isLoadingLeaveInbox && items.isEmpty {
                        HStack(spacing: 10) {
                            Spacer()
                            ProgressView()
                            Text("Đang tải danh sách đơn cần duyệt...")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Spacer()
                        }
                        .padding(24)
                        .background(Color.white)
                        .cornerRadius(14)
                    } else if items.isEmpty {
                        VStack(spacing: 8) {
                            Image(systemName: "checkmark.seal")
                                .font(.title2)
                                .foregroundColor(theme.primary)
                            Text("Không có đơn nghỉ phép nào trong bộ lọc hiện tại")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(24)
                        .background(Color.white)
                        .cornerRadius(14)
                    } else {
                        ForEach(items) { req in
                            let isExpanded = expandedInboxRequestIds.contains(req.requestId)
                            let isOwnRequest = !actorId.isEmpty && req.appUserId.caseInsensitiveCompare(actorId) == .orderedSame

                            VStack(alignment: .leading, spacing: 8) {
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        let displayName = req.requester?.displayName.isEmpty == false
                                            ? req.requester!.displayName
                                            : req.appUserId
                                        Text(displayName)
                                            .font(.subheadline.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)

                                        let metaParts: [String] = [
                                            req.requester?.personnelCode.flatMap { $0.isEmpty ? nil : "Mã NV: \($0)" },
                                            LeaveContract.leaveEmploymentStatusLabel(req.employmentStatusSnapshot),
                                            LeaveContract.leaveUnitKindLabel(req.unitKindSnapshot)
                                        ].compactMap { $0 }
                                        Text(metaParts.joined(separator: " • "))
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 4) {
                                        Text(LeaveContract.leaveRequestStatusLabel(req.status))
                                            .font(.caption2.weight(.bold))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(leaveStatusBgColor(req.status))
                                            .foregroundColor(leaveStatusTextColor(req.status))
                                            .cornerRadius(6)
                                        Text(LeaveContract.formatHalfDayUnits(req.requestedUnits))
                                            .font(.caption.weight(.bold))
                                            .foregroundColor(theme.primary)
                                    }
                                }

                                let breakdown = LeaveContract.resolveRequestTypeBreakdown(req)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 6) {
                                        ForEach(breakdown) { tb in
                                            Text("\(LeaveContract.leaveTypeShortLabel(tb.leaveType)): \(LeaveContract.formatHalfDayUnits(tb.halfDayUnits))")
                                                .font(.caption2.weight(.semibold))
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(theme.primaryLight)
                                                .foregroundColor(theme.primary)
                                                .cornerRadius(6)
                                        }
                                    }
                                }

                                let daysText = req.days.map {
                                    "\(LeaveContract.formatIsoDateVi($0.leaveDate)) (\(LeaveContract.leaveSessionShortLabel($0.sessionCode)) · \(LeaveContract.leaveTypeShortLabel($0.leaveType)))"
                                }.joined(separator: ", ")

                                Text("Ngày nghỉ: \(daysText)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.textPrimary)
                                let cleanReason = req.reason.trimmingCharacters(in: .whitespacesAndNewlines)
                                Text("Lý do xin nghỉ: \(cleanReason.isEmpty ? "Không ghi lý do" : cleanReason)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textPrimary)
                                Text("Định tuyến: \(LeaveContract.leaveRouteKindLabel(req.routeKind, teamName: req.routedTeamName, unitKind: req.unitKindSnapshot))")
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textMuted)

                                Button {
                                    if isExpanded {
                                        expandedInboxRequestIds.remove(req.requestId)
                                    } else {
                                        expandedInboxRequestIds.insert(req.requestId)
                                    }
                                } label: {
                                    Text(isExpanded ? "Thu gọn chi tiết" : "Xem chi tiết đơn")
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(theme.primary)
                                }
                                .buttonStyle(.plain)

                                if isExpanded {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Mã đơn: #\(String(req.requestId.prefix(8))) • Phiên bản: v\(req.rowVersion)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textSecondary)
                                        Text("Thời điểm gửi: \(WorkReportsContract.formatIsoDateTimeVi(req.submittedAt))")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textSecondary)
                                        if let fb = LeaveContract.leaveFallbackReasonLabel(req.fallbackReason) {
                                            Text("Lý do chuyển dự phòng: \(fb)")
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.textSecondary)
                                        }
                                        if let decider = req.decidedBy {
                                            Text("Người xử lý: \(decider.displayName) (\(WorkReportsContract.formatIsoDateTimeVi(req.decidedAt)))")
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.textSecondary)
                                        }
                                        if let decReason = req.decisionReason, !decReason.isEmpty {
                                            Text("Lý do quyết định: \(decReason)")
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.textPrimary)
                                        }
                                        if let cancelReason = req.cancellationReason, !cancelReason.isEmpty {
                                            Text("Lý do hủy/rút: \(cancelReason)")
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.textSecondary)
                                        }
                                    }
                                    .padding(10)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color(hex: 0xF8FAFC))
                                    .cornerRadius(10)
                                }

                                Divider()

                                if isOwnRequest {
                                    Text("Đơn của chính bạn (không tự duyệt hoặc tự hủy trên hộp thư phê duyệt)")
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textMuted)
                                } else {
                                    HStack(spacing: 8) {
                                        Spacer()
                                        let statusUpper = req.status.uppercased()
                                        if statusUpper == "PENDING" {
                                            Button {
                                                decisionReasonInput = ""
                                                localDecisionDialogError = nil
                                                viewModel.clearLeaveMutationError()
                                                pendingDecisionItem = PendingLeaveDecisionSheetItem(
                                                    request: req,
                                                    decision: "REJECT"
                                                )
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "xmark")
                                                    Text("Từ chối")
                                                }
                                                .font(.caption.weight(.bold))
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(SemanticColors.dangerBg)
                                                .foregroundColor(SemanticColors.dangerText)
                                                .cornerRadius(8)
                                            }
                                            .disabled(viewModel.isMutatingLeave)

                                            Button {
                                                decisionReasonInput = ""
                                                localDecisionDialogError = nil
                                                viewModel.clearLeaveMutationError()
                                                pendingDecisionItem = PendingLeaveDecisionSheetItem(
                                                    request: req,
                                                    decision: "APPROVE"
                                                )
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "checkmark.circle.fill")
                                                    Text("Duyệt đơn")
                                                }
                                                .font(.caption.weight(.bold))
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(SemanticColors.successText)
                                                .foregroundColor(.white)
                                                .cornerRadius(8)
                                            }
                                            .disabled(viewModel.isMutatingLeave)
                                        } else if statusUpper == "APPROVED" {
                                            Button {
                                                decisionReasonInput = ""
                                                localDecisionDialogError = nil
                                                viewModel.clearLeaveMutationError()
                                                pendingDecisionItem = PendingLeaveDecisionSheetItem(
                                                    request: req,
                                                    decision: "CANCEL_APPROVED"
                                                )
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "arrow.uturn.backward")
                                                    Text("Hủy đã duyệt (Hoàn phép)")
                                                }
                                                .font(.caption.weight(.bold))
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(SemanticColors.dangerBg)
                                                .foregroundColor(SemanticColors.dangerText)
                                                .cornerRadius(8)
                                            }
                                            .disabled(viewModel.isMutatingLeave)
                                        }
                                    }
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(14)
                        }

                        if let total = inbox?.total, items.count < total {
                            HrpLoadMoreButton(
                                title: "Tải thêm đơn cần duyệt",
                                loadedCount: items.count,
                                totalCount: total,
                                isLoading: viewModel.isLoadingMoreLeaveInbox,
                                theme: theme
                            ) {
                                Task { await viewModel.loadMoreLeaveApprovalInbox() }
                            }
                        }
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.refreshLeaveApprovalInbox()
            }
            .onAppear {
                if viewModel.leaveApprovalInbox == nil && !viewModel.isLoadingLeaveInbox {
                    Task { await viewModel.refreshLeaveApprovalInbox() }
                }
            }
        } else {
            EmptyView()
        }
    }

    // MARK: - Sheets (Withdraw, Approver Decision & Date Picker)

    private func withdrawConfirmationSheet(
        target: LeaveRequestProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Bạn muốn rút đơn xin nghỉ \(LeaveContract.formatHalfDayUnits(target.requestedUnits)) đang chờ duyệt?")
                    .font(.subheadline)
                    .foregroundColor(SemanticColors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Ghi chú rút đơn (tùy chọn)")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    TextField("Nhập lý do rút đơn nếu cần...", text: $withdrawReasonInput)
                        .font(.caption)
                        .submitLabel(.done)
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                }

                HStack(spacing: 10) {
                    Button("Đóng") {
                        KeyboardDismissal.dismiss()
                        withdrawTargetRequest = nil
                    }
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(Color(hex: 0xF1F5F9))
                    .foregroundColor(SemanticColors.textPrimary)
                    .cornerRadius(10)
                    .disabled(viewModel.isMutatingLeave)

                    Button {
                        KeyboardDismissal.dismiss()
                        let cleanReason = withdrawReasonInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        Task {
                            await viewModel.withdrawLeaveRequest(
                                request: target,
                                reason: cleanReason.isEmpty ? nil : cleanReason,
                                onSuccess: {
                                    withdrawTargetRequest = nil
                                    withdrawReasonInput = ""
                                }
                            )
                        }
                    } label: {
                        Text(viewModel.isMutatingLeave ? "Đang rút..." : "Xác nhận rút đơn")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(SemanticColors.dangerText)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                    .disabled(viewModel.isMutatingLeave)
                }
                Spacer()
            }
            .padding(18)
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Rút đơn xin nghỉ phép")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.height(260)])
    }

    private func approverDecisionSheet(
        item: PendingLeaveDecisionSheetItem,
        theme: MiniBiThemePalette
    ) -> some View {
        let req = item.request
        let decision = item.decision
        let breakdownText = LeaveContract.resolveRequestTypeBreakdown(req).map {
            "\(LeaveContract.leaveTypeShortLabel($0.leaveType)): \(LeaveContract.formatHalfDayUnits($0.halfDayUnits))"
        }.joined(separator: " • ")

        return NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Nhân sự: \(req.requester?.displayName ?? req.appUserId) • Số ngày: \(LeaveContract.formatHalfDayUnits(req.requestedUnits))")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(SemanticColors.textPrimary)
                if !breakdownText.isEmpty {
                    Text("Loại nghỉ: \(breakdownText)")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Lý do / Ghi chú phản hồi (không bắt buộc)")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    TextField(
                        "Nhập ghi chú phản hồi (nếu có)...",
                        text: Binding(
                            get: { decisionReasonInput },
                            set: {
                                decisionReasonInput = $0
                                localDecisionDialogError = nil
                            }
                        )
                    )
                    .font(.caption)
                    .submitLabel(.done)
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                    )
                }

                if let err = localDecisionDialogError ?? viewModel.leaveMutationError, !err.isEmpty {
                    Text(err)
                        .font(.caption)
                        .foregroundColor(SemanticColors.dangerText)
                }

                HStack(spacing: 10) {
                    Button("Đóng") {
                        KeyboardDismissal.dismiss()
                        pendingDecisionItem = nil
                        localDecisionDialogError = nil
                    }
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(Color(hex: 0xF1F5F9))
                    .foregroundColor(SemanticColors.textPrimary)
                    .cornerRadius(10)
                    .disabled(viewModel.isMutatingLeave)

                    Button {
                        KeyboardDismissal.dismiss()
                        let actorId = viewModel.currentLeaveActorAppUserId
                        let validation = LeaveContract.validateApproverDecisionInput(
                            actorAppUserId: actorId,
                            request: req,
                            decision: decision,
                            reason: decisionReasonInput
                        )
                        guard validation.isValid else {
                            localDecisionDialogError = validation.errorMessage
                            return
                        }
                        let cleanReason = decisionReasonInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        Task {
                            await viewModel.decideLeaveRequest(
                                request: req,
                                decision: decision,
                                reason: cleanReason.isEmpty ? nil : cleanReason,
                                onSuccess: {
                                    pendingDecisionItem = nil
                                    decisionReasonInput = ""
                                    localDecisionDialogError = nil
                                }
                            )
                        }
                    } label: {
                        let btnLabel: String = {
                            if viewModel.isMutatingLeave { return "Đang xử lý..." }
                            if decision == "APPROVE" { return "Xác nhận duyệt" }
                            if decision == "REJECT" { return "Xác nhận từ chối" }
                            return "Xác nhận hủy duyệt"
                        }()
                        Text(btnLabel)
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(decision == "APPROVE" ? SemanticColors.successText : SemanticColors.dangerText)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                    .disabled(viewModel.isMutatingLeave)
                }
                Spacer()
            }
            .padding(18)
            .dismissKeyboardOnTapOutside()
            .navigationTitle(LeaveContract.leaveDecisionActionLabel(decision))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.height(290)])
    }

    private func leaveDatePickerSheet(
        day: LeaveDraftDayInput,
        theme: MiniBiThemePalette
    ) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Mode Switcher: Chọn 1 ngày vs Chọn dải ngày (Từ → Đến)
                    HStack(spacing: 6) {
                        Button {
                            datePickerModeIsRange = false
                            rangeErrorMessage = nil
                        } label: {
                            Text("Chọn 1 ngày")
                                .font(.caption.weight(!datePickerModeIsRange ? .bold : .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(!datePickerModeIsRange ? theme.primary : Color.clear)
                                .foregroundColor(!datePickerModeIsRange ? .white : SemanticColors.textSecondary)
                                .cornerRadius(8)
                        }
                        .buttonStyle(.plain)

                        Button {
                            datePickerModeIsRange = true
                            rangeErrorMessage = nil
                        } label: {
                            Text("Chọn dải ngày (Từ → Đến)")
                                .font(.caption.weight(datePickerModeIsRange ? .bold : .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(datePickerModeIsRange ? theme.primary : Color.clear)
                                .foregroundColor(datePickerModeIsRange ? .white : SemanticColors.textSecondary)
                                .cornerRadius(8)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(4)
                    .background(Color(hex: 0xF1F5F9))
                    .cornerRadius(10)

                    if !datePickerModeIsRange {
                        DatePicker(
                            "Chọn ngày nghỉ",
                            selection: $datePickerSelection,
                            displayedComponents: [.date]
                        )
                        .datePickerStyle(.graphical)
                        .environment(\.locale, Locale(identifier: "vi_VN"))

                        Button {
                            viewModel.updateLeaveDraftDayDate(
                                id: day.id,
                                leaveDate: formatDateIso(datePickerSelection)
                            )
                            datePickerTargetDay = nil
                        } label: {
                            Text("Áp dụng ngày \(LeaveContract.formatIsoDateVi(formatDateIso(datePickerSelection)))")
                                .font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(theme.primary)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                        }
                    } else {
                        dateRangePickerFormContent(
                            theme: theme,
                            onApplied: {
                                datePickerTargetDay = nil
                            }
                        )
                    }
                }
                .padding(16)
            }
            .navigationTitle(datePickerModeIsRange ? "Chọn dải ngày nghỉ" : "Chọn ngày xin nghỉ")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        datePickerTargetDay = nil
                    }
                }
            }
        }
        .presentationDetents([.large])
    }

    private func leaveDateRangePickerSheet(theme: MiniBiThemePalette) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    dateRangePickerFormContent(
                        theme: theme,
                        onApplied: {
                            isShowingRangePickerSheet = false
                        }
                    )
                }
                .padding(16)
            }
            .navigationTitle("Chọn dải ngày nghỉ phép")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        isShowingRangePickerSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func dateRangePickerFormContent(
        theme: MiniBiThemePalette,
        onApplied: @escaping () -> Void
    ) -> some View {
        let startIso = formatDateIso(rangeStartDate)
        let endIso = formatDateIso(rangeEndDate)
        let preview = LeaveContract.generateDraftDaysFromRange(
            startDateIso: startIso,
            endDateIso: endIso,
            leaveType: rangeLeaveType,
            sessionCode: "FULL",
            skipSundays: rangeSkipSundays
        )

        return VStack(alignment: .leading, spacing: 14) {
            Text("Chọn ngày bắt đầu và ngày kết thúc để tự động tạo danh sách các ngày xin nghỉ liên tiếp. Sau khi áp dụng, bạn vẫn có thể chỉnh sửa từng buổi (Sáng/Chiều) hoặc tách 2 buổi trên từng ngày.")
                .font(.caption)
                .foregroundColor(SemanticColors.textSecondary)

            VStack(spacing: 10) {
                HStack {
                    Text("Từ ngày bắt đầu")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Spacer()
                    DatePicker(
                        "Từ ngày",
                        selection: Binding(
                            get: { rangeStartDate },
                            set: { newStart in
                                rangeStartDate = newStart
                                if rangeEndDate < newStart {
                                    rangeEndDate = newStart
                                }
                                rangeErrorMessage = nil
                            }
                        ),
                        displayedComponents: [.date]
                    )
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "vi_VN"))
                }
                .padding(12)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                )

                HStack {
                    Text("Đến ngày kết thúc")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Spacer()
                    DatePicker(
                        "Đến ngày",
                        selection: Binding(
                            get: { rangeEndDate },
                            set: { newEnd in
                                rangeEndDate = newEnd
                                rangeErrorMessage = nil
                            }
                        ),
                        in: rangeStartDate...,
                        displayedComponents: [.date]
                    )
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "vi_VN"))
                }
                .padding(12)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                )
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Loại nghỉ mặc định cho dải ngày")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(SemanticColors.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(LeaveContract.leaveTypes, id: \.self) { typeCode in
                            let isSelected = rangeLeaveType.uppercased() == typeCode
                            Button {
                                rangeLeaveType = typeCode
                            } label: {
                                Text(LeaveContract.leaveTypeShortLabel(typeCode))
                                    .font(.caption.weight(isSelected ? .bold : .medium))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(isSelected ? theme.primaryLight : Color(hex: 0xF8FAFC))
                                    .foregroundColor(isSelected ? theme.primary : SemanticColors.textSecondary)
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(isSelected ? theme.primary : SemanticColors.borderSubtle, lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            Toggle(isOn: $rangeSkipSundays) {
                Text("Bỏ qua ngày Chủ nhật trong dải ngày")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(SemanticColors.textPrimary)
            }
            .tint(theme.primary)
            .padding(10)
            .background(Color(hex: 0xF8FAFC))
            .cornerRadius(10)

            if let err = rangeErrorMessage ?? preview.errorMessage {
                Text(err)
                    .font(.caption)
                    .foregroundColor(SemanticColors.dangerText)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(SemanticColors.dangerBg)
                    .cornerRadius(8)
            } else {
                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(SemanticColors.successText)
                    Text("Sẽ tạo \(preview.days.count) ngày nghỉ (\(LeaveContract.formatIsoDateVi(startIso)) → \(LeaveContract.formatIsoDateVi(endIso))) • Loại: \(LeaveContract.leaveTypeShortLabel(rangeLeaveType))")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.successText)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(SemanticColors.successBg)
                .cornerRadius(8)
            }

            Button {
                let ok = viewModel.applyLeaveDraftDateRange(
                    startDateIso: startIso,
                    endDateIso: endIso,
                    leaveType: rangeLeaveType,
                    skipSundays: rangeSkipSundays
                )
                if ok {
                    onApplied()
                } else {
                    rangeErrorMessage = viewModel.leaveMutationError
                }
            } label: {
                Text(preview.days.isEmpty ? "Áp dụng dải ngày" : "Áp dụng dải ngày (\(preview.days.count) ngày)")
                    .font(.subheadline.weight(.bold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(preview.days.isEmpty ? Color.gray : theme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .disabled(preview.days.isEmpty)
        }
    }

    private func formatDateIso(_ date: Date) -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: date)
    }

    // MARK: - Tab 2: Attendance Explanation ("Giải trình") Content

    @ViewBuilder
    private func explanationTabContent(theme: MiniBiThemePalette) -> some View {
        let overview = viewModel.myExplanationsOverview
        let selectedDate = viewModel.explanationSelectedDate
        let todayIso = overview?.todayLocal ?? AttendanceExplanationContract.todayVietnamIsoDate()

        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let err = viewModel.myExplanationsError, !err.isEmpty {
                    HStack(spacing: 10) {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(SemanticColors.dangerText)
                        Spacer()
                        Button("Thử lại") {
                            Task { await viewModel.refreshMyAttendanceExplanations(businessDate: selectedDate) }
                        }
                        .font(.caption.weight(.bold))
                        .foregroundColor(SemanticColors.dangerText)
                    }
                    .padding(12)
                    .background(SemanticColors.dangerBg)
                    .cornerRadius(12)
                }

                if let mutErr = viewModel.explanationMutationError,
                   !mutErr.isEmpty,
                   pendingExplanationSubmitSlot == nil {
                    Text(mutErr)
                        .font(.caption)
                        .foregroundColor(SemanticColors.dangerText)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(SemanticColors.dangerBg)
                        .cornerRadius(12)
                }

                // Compact Header & Date Navigator Card
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .center, spacing: 8) {
                        Text("Giải trình chấm công")
                            .font(.headline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)

                        Button {
                            HapticsManager.selection()
                            isShowingExplanationPolicyInfoSheet = true
                        } label: {
                            Image(systemName: "info.circle")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(theme.primary)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                        .accessibilityLabel("Thông tin quy chế giải trình")

                        Spacer()

                        Button {
                            HapticsManager.lightImpact()
                            Task { await viewModel.refreshMyAttendanceExplanations(businessDate: selectedDate) }
                        } label: {
                            if viewModel.isLoadingMyExplanations {
                                ProgressView()
                                    .scaleEffect(0.75)
                                    .frame(width: 36, height: 36)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .font(.caption.weight(.bold))
                                    .frame(width: 36, height: 36)
                                    .background(theme.primaryLight)
                                    .foregroundColor(theme.primary)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                        .disabled(viewModel.isLoadingMyExplanations || viewModel.isMutatingExplanation)
                    }

                    // Shared Date Navigator Bar for Explanation Business Date
                    HrpDateNavigatorBar(
                        selectedDateIso: selectedDate,
                        todayDateIso: todayIso,
                        allowFutureDates: false,
                        theme: theme,
                        onDateSelected: { newDate in
                            viewModel.setExplanationSelectedDate(newDate)
                        }
                    )

                    if let currentOverview = overview {
                        let dayEval = currentOverview.dayEvaluation
                        let approverText = AttendanceExplanationContract.friendlyApproverSummary(
                            routing: currentOverview.routing
                        )
                        let profileText = AttendanceExplanationContract.friendlyProfileSummary(
                            dayEval: dayEval
                        )

                        HStack(spacing: 8) {
                            Image(systemName: "person.badge.shield.checkmark")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(theme.primary)
                            Text("Người duyệt: \(approverText)")
                                .font(.caption.weight(.medium))
                                .foregroundColor(SemanticColors.textPrimary)
                                .lineLimit(1)
                            Spacer(minLength: 6)
                            Text(profileText)
                                .font(.caption2.weight(.bold).monospacedDigit())
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(theme.primary.opacity(0.12))
                                .foregroundColor(theme.primary)
                                .clipShape(Capsule())
                        }
                        .padding(.horizontal, 10)
                        .frame(minHeight: 34)
                        .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                    }
                }
                .padding(14)
                .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                if viewModel.isLoadingMyExplanations && overview == nil {
                    HStack(spacing: 10) {
                        Spacer()
                        ProgressView()
                        Text("Đang tải các mốc chấm công...")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                        Spacer()
                    }
                    .padding(24)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                }

                if let currentOverview = overview {
                    let dayEval = currentOverview.dayEvaluation

                    // Dynamic Day Slots Header
                    Text("Mốc chấm công ngày \(LeaveContract.formatIsoDateVi(dayEval.businessDate))")
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundColor(SemanticColors.textPrimary)
                        .padding(.horizontal, 2)

                    if dayEval.slots.isEmpty {
                        Text("Không có mốc chấm công nào được cấu hình cho ngày này.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    } else {
                        ForEach(dayEval.slots) { slot in
                            explanationDaySlotCard(
                                slot: slot,
                                canSubmitExplanation: currentOverview.viewer.canSubmitExplanation,
                                theme: theme
                            )
                        }
                    }

                    // My Explanation History Section
                    Text("Lịch sử giải trình đã gửi (\(currentOverview.pagination.totalRows))")
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundColor(SemanticColors.textPrimary)
                        .padding(.top, 4)
                        .padding(.horizontal, 2)

                    if currentOverview.items.isEmpty {
                        Text("Bạn chưa gửi giải trình chấm công nào trong ngày này.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    } else {
                        ForEach(currentOverview.items) { item in
                            explanationMyHistoryCard(item: item, theme: theme)
                        }

                        if currentOverview.items.count < currentOverview.pagination.totalRows {
                            HrpLoadMoreButton(
                                title: "Tải thêm lịch sử giải trình",
                                loadedCount: currentOverview.items.count,
                                totalCount: currentOverview.pagination.totalRows,
                                isLoading: viewModel.isLoadingMoreMyExplanations,
                                theme: theme
                            ) {
                                Task { await viewModel.loadMoreMyExplanations() }
                            }
                        }
                    }
                }
            }
            .padding(14)
        }
        .refreshable {
            await viewModel.refreshMyAttendanceExplanations(businessDate: selectedDate)
        }
        .onAppear {
            if viewModel.myExplanationsOverview == nil && !viewModel.isLoadingMyExplanations {
                Task { await viewModel.refreshMyAttendanceExplanations(businessDate: selectedDate) }
            }
        }
    }

    private func explanationDaySlotCard(
        slot: AttendanceExplanationDaySlotEvaluationProjection,
        canSubmitExplanation: Bool,
        theme: MiniBiThemePalette
    ) -> some View {
        let friendlyTitle = AttendanceExplanationContract.friendlySlotTitle(
            slotLabel: slot.slotLabel,
            slotCode: slot.slotCode,
            slotIndex: slot.slotIndex
        )
        let validRange = AttendanceExplanationContract.friendlyValidTimeRange(
            targetTime: slot.targetTime,
            graceBeforeMinutes: slot.graceBeforeMinutes,
            graceAfterMinutes: slot.graceAfterMinutes
        )
        let shortSession = slot.sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "PM"
            ? "Chiều"
            : "Sáng"

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                // Left column: Target time highlight box (60pt)
                VStack(spacing: 2) {
                    Text(slot.targetTime)
                        .font(.system(size: 16, weight: .bold, design: .rounded).monospacedDigit())
                        .foregroundColor(theme.primary)
                        .lineLimit(1)
                    Text(shortSession)
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                        .lineLimit(1)
                }
                .frame(width: 60)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(theme.primaryLight.opacity(0.85))
                )

                // Middle column: Friendly title + valid range & actual check-in line
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(friendlyTitle)
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                            .lineLimit(1)
                        Text("· \(validRange)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(SemanticColors.textSecondary)
                            .lineLimit(1)
                    }

                    if let firstCheckin = slot.checkins.first {
                        let devLabel = AttendanceExplanationContract.formatDeviationBadge(firstCheckin)
                        let timeText = firstCheckin.timeLocal.isEmpty ? firstCheckin.capturedAtLocal : firstCheckin.timeLocal
                        let devSuffix = (devLabel?.isEmpty == false) ? " · \(devLabel!)" : ""
                        Text("Đã chấm lúc \(timeText)\(devSuffix)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(SemanticColors.textPrimary)
                            .lineLimit(1)
                    } else {
                        let emptySlotHint: String = {
                            switch slot.slotStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
                            case "UPCOMING", "NOT_APPLICABLE":
                                return "Chưa đến giờ chấm"
                            case "LEAVE_COVERED", "LEAVE_EXEMPT":
                                return "Được miễn chấm"
                            default:
                                return "Chưa ghi nhận chấm công"
                            }
                        }()
                        Text(emptySlotHint)
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 4)

                // Right column: Compact status capsule
                Text(AttendanceExplanationContract.compactSlotStatusBadge(slot.slotStatus))
                    .font(.caption2.weight(.bold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(explanationSlotStatusBgColor(slot.slotStatus))
                    .foregroundColor(explanationSlotStatusTextColor(slot.slotStatus))
                    .clipShape(Capsule())
            }

            if let existingExp = slot.explanation {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Giải trình đã gửi · \(AttendanceExplanationContract.explanationStatusLabel(existingExp.status))")
                        .font(.caption.weight(.bold))
                        .foregroundColor(leaveStatusTextColor(existingExp.status))
                    Text("Lý do: \(existingExp.reason)")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textPrimary)
                    let deciderName = existingExp.decidedByDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    let cleanNote = existingExp.decisionNote.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !deciderName.isEmpty || !cleanNote.isEmpty {
                        let noteSuffix = cleanNote.isEmpty ? "" : " · Phản hồi: \(cleanNote)"
                        Text("Người duyệt: \(deciderName.isEmpty ? "--" : deciderName)\(noteSuffix)")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(leaveStatusBgColor(existingExp.status))
                )
            } else if slot.eligibleForExplanation && canSubmitExplanation {
                Button {
                    HapticsManager.lightImpact()
                    viewModel.clearExplanationMutationError()
                    localExplanationSubmitError = nil
                    explanationReasonInput = ""
                    pendingExplanationSubmitSlot = slot
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "paperplane.fill")
                        Text("Gửi giải trình")
                    }
                    .font(.caption.weight(.bold))
                    .frame(maxWidth: .infinity, minHeight: 38)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(theme.primary)
                    )
                    .foregroundColor(.white)
                }
                .buttonStyle(HrpPressableButtonStyle())
                .disabled(viewModel.isMutatingExplanation)
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    private func explanationMyHistoryCard(
        item: AttendanceExplanationItemProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        let isExpanded = expandedMyExplanationIds.contains(item.explanationId)
        let cleanDecisionNote = item.decisionNote.trimmingCharacters(in: .whitespacesAndNewlines)
        let friendlyTitle = AttendanceExplanationContract.friendlySlotTitle(
            slotLabel: item.slotLabel,
            slotCode: item.slotCode,
            slotIndex: item.slotIndex
        )

        return VStack(alignment: .leading, spacing: 8) {
            // Line 1: Formatted date · Friendly slot title (targetTime) + Status Capsule
            HStack(alignment: .center, spacing: 8) {
                Text("\(LeaveContract.formatIsoDateVi(item.businessDate)) · \(friendlyTitle) (\(item.targetTime))")
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                Text(AttendanceExplanationContract.explanationStatusLabel(item.status))
                    .font(.caption2.weight(.bold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(leaveStatusBgColor(item.status))
                    .foregroundColor(leaveStatusTextColor(item.status))
                    .clipShape(Capsule())
            }

            // Line 2: Compact original cause tag
            HStack(spacing: 5) {
                Image(systemName: "clock.badge.exclamationmark")
                    .font(.caption2)
                Text(AttendanceExplanationContract.originalSlotStatusLabel(item.originalSlotStatus))
                    .font(.caption2.weight(.semibold))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(explanationSlotStatusBgColor(item.originalSlotStatus))
            .foregroundColor(explanationSlotStatusTextColor(item.originalSlotStatus))
            .clipShape(Capsule())

            // Line 3: Highlighted Reason Tile
            Text("Lý do: \(item.reason)")
                .font(.caption)
                .foregroundColor(SemanticColors.textPrimary)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)

            // Line 4: Decision feedback (only when decided)
            if item.decidedBy != nil || !cleanDecisionNote.isEmpty || (item.decidedAtLocal?.isEmpty == false) {
                let decider = item.decidedBy?.resolvedDisplayName() ?? "--"
                let decidedTime = (item.decidedAtLocal ?? item.decidedAt ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let timeSuffix = decidedTime.isEmpty ? "" : " · \(decidedTime)"
                VStack(alignment: .leading, spacing: 3) {
                    Text("Người duyệt: \(decider)\(timeSuffix)")
                        .font(.caption2.weight(.semibold).monospacedDigit())
                        .foregroundColor(SemanticColors.textPrimary)
                    if !cleanDecisionNote.isEmpty {
                        Text("Phản hồi: \(cleanDecisionNote)")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color(hex: 0xF8FAFC))
                )
            }

            Button {
                HapticsManager.selection()
                if isExpanded {
                    expandedMyExplanationIds.remove(item.explanationId)
                } else {
                    expandedMyExplanationIds.insert(item.explanationId)
                }
            } label: {
                Text(isExpanded ? "Thu gọn" : "Xem lịch sử xử lý")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(theme.primary)
            }
            .buttonStyle(.plain)

            if isExpanded {
                VStack(alignment: .leading, spacing: 4) {
                    Divider()
                    let subTime = item.submittedAtLocal.isEmpty ? item.submittedAt : item.submittedAtLocal
                    Text("Gửi lúc: \(subTime)")
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(SemanticColors.textSecondary)
                    ForEach(item.events) { ev in
                        let evTime = ev.createdAtLocal.isEmpty ? ev.createdAt : ev.createdAtLocal
                        let actorName = ev.actorDisplayName ?? "Hệ thống"
                        let cleanEvNote = ev.note.trimmingCharacters(in: .whitespacesAndNewlines)
                        let evNoteSuffix = cleanEvNote.isEmpty ? "" : ": \(cleanEvNote)"
                        Text("• \(AttendanceExplanationContract.auditEventTypeLabel(ev.eventType)) bởi \(actorName) (\(evTime))\(evNoteSuffix)")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                }
            }
        }
        .padding(14)
        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
    }

    // MARK: - Tab 4: Attendance Explanation Approval ("Duyệt giải trình") Content

    @ViewBuilder
    private func explanationApprovalTabContent(theme: MiniBiThemePalette) -> some View {
        let canApprove: Bool = {
            if let iv = viewModel.explanationApprovalInbox?.viewer {
                return iv.canApproveExplanations
            }
            if let ov = viewModel.myExplanationsOverview?.viewer {
                return ov.canApproveExplanations && !ov.isSharedNonEmployee
            }
            if let auth = viewModel.myLeaveSummary?.authority {
                return auth.canApproveRequests && !auth.isSharedNonEmployee
            }
            return false
        }()

        if canApprove {
            let inbox = viewModel.explanationApprovalInbox
            let items = inbox?.items ?? []
            let pendingCount = inbox?.pagination.pendingCount ?? 0
            let actorId = viewModel.currentExplanationActorAppUserId ?? ""

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Header & Status Filter Card
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(alignment: .center, spacing: 8) {
                            Text("Duyệt giải trình")
                                .font(.headline.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)

                            Spacer()

                            Text("Chờ duyệt: \(pendingCount)")
                                .font(.caption.weight(.bold).monospacedDigit())
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(SemanticColors.warningBg)
                                .foregroundColor(SemanticColors.warningText)
                                .clipShape(Capsule())

                            Button {
                                HapticsManager.lightImpact()
                                Task { await viewModel.refreshExplanationApprovalInbox() }
                            } label: {
                                if viewModel.isLoadingExplanationInbox {
                                    ProgressView()
                                        .scaleEffect(0.75)
                                        .frame(width: 36, height: 36)
                                } else {
                                    Image(systemName: "arrow.clockwise")
                                        .font(.caption.weight(.bold))
                                        .frame(width: 36, height: 36)
                                        .background(theme.primaryLight)
                                        .foregroundColor(theme.primary)
                                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                }
                            }
                            .buttonStyle(HrpPressableButtonStyle())
                            .disabled(viewModel.isLoadingExplanationInbox)
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(AttendanceExplanationContract.approvalStatusFilters, id: \.self) { code in
                                    let isSelected = viewModel.explanationInboxStatusFilter == code
                                    Button {
                                        HapticsManager.selection()
                                        viewModel.setExplanationInboxFilter(code)
                                    } label: {
                                        Text(AttendanceExplanationContract.statusFilterLabel(code))
                                            .font(.caption.weight(isSelected ? .bold : .medium))
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 7)
                                            .background(isSelected ? theme.primary : Color(hex: 0xF1F5F9))
                                            .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
                                            .clipShape(Capsule())
                                    }
                                    .buttonStyle(HrpPressableButtonStyle())
                                }
                            }
                        }
                    }
                    .padding(14)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)

                    if let inbErr = viewModel.explanationInboxError, !inbErr.isEmpty {
                        HStack {
                            Text(inbErr)
                                .font(.caption)
                                .foregroundColor(SemanticColors.dangerText)
                            Spacer()
                            Button("Thử lại") {
                                Task { await viewModel.refreshExplanationApprovalInbox() }
                            }
                            .font(.caption.weight(.bold))
                            .foregroundColor(SemanticColors.dangerText)
                        }
                        .padding(12)
                        .background(SemanticColors.dangerBg)
                        .cornerRadius(12)
                    }

                    if let mutErr = viewModel.explanationMutationError,
                       !mutErr.isEmpty,
                       pendingExplanationDecisionItem == nil {
                        Text(mutErr)
                            .font(.caption)
                            .foregroundColor(SemanticColors.dangerText)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.dangerBg)
                            .cornerRadius(12)
                    }

                    if viewModel.isLoadingExplanationInbox && inbox == nil {
                        HStack(spacing: 10) {
                            Spacer()
                            ProgressView()
                            Text("Đang tải danh sách giải trình cần duyệt...")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Spacer()
                        }
                        .padding(24)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    } else if items.isEmpty {
                        VStack(spacing: 8) {
                            Image(systemName: "checkmark.seal")
                                .font(.title2)
                                .foregroundColor(theme.primary)
                            Text("Không có giải trình chấm công nào trong bộ lọc hiện tại.")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(24)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    } else {
                        ForEach(items) { item in
                            let isExpanded = expandedInboxExplanationIds.contains(item.explanationId)
                            let isSelfRequest = !actorId.isEmpty &&
                                item.appUserId.caseInsensitiveCompare(actorId) == .orderedSame
                            let canAct = item.canDecide &&
                                item.status.uppercased() == "PENDING" &&
                                !isSelfRequest
                            let requesterName = item.requester.resolvedDisplayName()
                            let requesterSub = AttendanceExplanationContract.friendlyRequesterSubtitle(
                                requester: item.requester,
                                teamName: item.routedTeam.teamName
                            )
                            let friendlyTitle = AttendanceExplanationContract.friendlySlotTitle(
                                slotLabel: item.slotLabel,
                                slotCode: item.slotCode,
                                slotIndex: item.slotIndex
                            )

                            VStack(alignment: .leading, spacing: 10) {
                                // Block 1: Requester Header with Initials Avatar
                                HStack(alignment: .center, spacing: 10) {
                                    HrpInitialsAvatar(
                                        fullName: requesterName,
                                        tint: theme.primary,
                                        size: 38
                                    )

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(requesterName)
                                            .font(.subheadline.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .lineLimit(1)
                                        if !requesterSub.isEmpty {
                                            Text(requesterSub)
                                                .font(.caption)
                                                .foregroundColor(SemanticColors.textSecondary)
                                                .lineLimit(1)
                                        }
                                    }

                                    Spacer(minLength: 6)

                                    Text(AttendanceExplanationContract.explanationStatusLabel(item.status))
                                        .font(.caption2.weight(.bold))
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)
                                        .padding(.horizontal, 9)
                                        .padding(.vertical, 4)
                                        .background(leaveStatusBgColor(item.status))
                                        .foregroundColor(leaveStatusTextColor(item.status))
                                        .clipShape(Capsule())
                                }

                                // Block 2: Shift & Attendance Status Strip (.hrpMutedTile)
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack(alignment: .center, spacing: 6) {
                                        Image(systemName: "calendar.badge.clock")
                                            .font(.caption.weight(.semibold))
                                            .foregroundColor(theme.primary)
                                        Text("\(LeaveContract.formatIsoDateVi(item.businessDate)) · \(friendlyTitle) (\(item.targetTime))")
                                            .font(.subheadline.weight(.semibold).monospacedDigit())
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .lineLimit(1)
                                        Spacer(minLength: 4)
                                        Text(AttendanceExplanationContract.originalSlotStatusLabel(item.originalSlotStatus))
                                            .font(.caption2.weight(.bold))
                                            .lineLimit(1)
                                            .fixedSize(horizontal: true, vertical: false)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(explanationSlotStatusBgColor(item.originalSlotStatus))
                                            .foregroundColor(explanationSlotStatusTextColor(item.originalSlotStatus))
                                            .clipShape(Capsule())
                                    }

                                    if let firstCheckin = item.actualCheckins.first {
                                        let dev = AttendanceExplanationContract.formatDeviationBadge(firstCheckin)
                                        let timeText = firstCheckin.timeLocal.isEmpty ? firstCheckin.capturedAtLocal : firstCheckin.timeLocal
                                        let devSuffix = (dev?.isEmpty == false) ? " (\(dev!))" : ""
                                        Text("Thực tế: Chấm lúc \(timeText)\(devSuffix)")
                                            .font(.caption.monospacedDigit())
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .lineLimit(1)
                                    } else {
                                        Text("Thực tế: Chưa có lượt chấm công")
                                            .font(.caption)
                                            .foregroundColor(SemanticColors.textSecondary)
                                            .lineLimit(1)
                                    }
                                }
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)

                                // Block 3: Highlighted Reason Box
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("LÝ DO GIẢI TRÌNH")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(theme.primary)
                                    Text(item.reason)
                                        .font(.subheadline.weight(.medium))
                                        .foregroundColor(SemanticColors.textPrimary)
                                }
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(theme.primaryLight.opacity(0.45))
                                )

                                // Block 4: Decision Feedback (if decided) & Bottom Action Bar
                                let cleanDecNote = item.decisionNote.trimmingCharacters(in: .whitespacesAndNewlines)
                                if item.decidedBy != nil || !cleanDecNote.isEmpty {
                                    let deciderName = item.decidedBy?.resolvedDisplayName() ?? "--"
                                    let noteSuffix = cleanDecNote.isEmpty ? "" : " · \(cleanDecNote)"
                                    Text("Người duyệt: \(deciderName)\(noteSuffix)")
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textSecondary)
                                }

                                HStack(alignment: .center, spacing: 10) {
                                    Button {
                                        HapticsManager.selection()
                                        if isExpanded {
                                            expandedInboxExplanationIds.remove(item.explanationId)
                                        } else {
                                            expandedInboxExplanationIds.insert(item.explanationId)
                                        }
                                    } label: {
                                        Text(isExpanded ? "Thu gọn" : "Chi tiết")
                                            .font(.caption.weight(.semibold))
                                            .foregroundColor(theme.primary)
                                            .padding(.vertical, 6)
                                    }
                                    .buttonStyle(.plain)

                                    Spacer()

                                    if isSelfRequest {
                                        Text("Đơn của bạn (không tự duyệt)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    } else if canAct {
                                        HStack(spacing: 8) {
                                            Button {
                                                HapticsManager.lightImpact()
                                                viewModel.clearExplanationMutationError()
                                                localExplanationDecisionError = nil
                                                explanationDecisionNoteInput = ""
                                                pendingExplanationDecisionItem = PendingExplanationDecisionSheetItem(
                                                    explanation: item,
                                                    decision: "REJECT"
                                                )
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "xmark")
                                                    Text("Từ chối")
                                                }
                                                .font(.caption.weight(.bold))
                                                .padding(.horizontal, 14)
                                                .frame(minHeight: 38)
                                                .background(
                                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                        .fill(SemanticColors.dangerBg)
                                                )
                                                .foregroundColor(SemanticColors.dangerText)
                                            }
                                            .buttonStyle(HrpPressableButtonStyle())
                                            .disabled(viewModel.isMutatingExplanation)

                                            Button {
                                                HapticsManager.lightImpact()
                                                viewModel.clearExplanationMutationError()
                                                localExplanationDecisionError = nil
                                                explanationDecisionNoteInput = ""
                                                pendingExplanationDecisionItem = PendingExplanationDecisionSheetItem(
                                                    explanation: item,
                                                    decision: "APPROVE"
                                                )
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "checkmark.circle.fill")
                                                    Text("Duyệt")
                                                }
                                                .font(.caption.weight(.bold))
                                                .padding(.horizontal, 16)
                                                .frame(minHeight: 38)
                                                .background(
                                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                        .fill(SemanticColors.successText)
                                                )
                                                .foregroundColor(.white)
                                            }
                                            .buttonStyle(HrpPressableButtonStyle())
                                            .disabled(viewModel.isMutatingExplanation)
                                        }
                                    }
                                }

                                if isExpanded {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Divider()
                                        let subTime = item.submittedAtLocal.isEmpty ? item.submittedAt : item.submittedAtLocal
                                        Text("Gửi lúc: \(subTime)")
                                            .font(.caption2.monospacedDigit())
                                            .foregroundColor(SemanticColors.textSecondary)
                                        if !item.events.isEmpty {
                                            ForEach(item.events) { ev in
                                                let evTime = ev.createdAtLocal.isEmpty ? ev.createdAt : ev.createdAtLocal
                                                let actorName = ev.actorDisplayName ?? "Hệ thống"
                                                let cleanEvNote = ev.note.trimmingCharacters(in: .whitespacesAndNewlines)
                                                let evNoteSuffix = cleanEvNote.isEmpty ? "" : ": \(cleanEvNote)"
                                                Text("• \(AttendanceExplanationContract.auditEventTypeLabel(ev.eventType)) — \(actorName) (\(evTime))\(evNoteSuffix)")
                                                    .font(.caption2)
                                                    .foregroundColor(SemanticColors.textSecondary)
                                            }
                                        }
                                    }
                                    .padding(10)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                                }
                            }
                            .padding(14)
                            .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                        }

                        if let pagination = inbox?.pagination, items.count < pagination.totalRows {
                            HrpLoadMoreButton(
                                title: "Tải thêm giải trình cần duyệt",
                                loadedCount: items.count,
                                totalCount: pagination.totalRows,
                                isLoading: viewModel.isLoadingMoreExplanationInbox,
                                theme: theme
                            ) {
                                Task { await viewModel.loadMoreExplanationApprovalInbox() }
                            }
                        }
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.refreshExplanationApprovalInbox()
            }
            .onAppear {
                if viewModel.explanationApprovalInbox == nil && !viewModel.isLoadingExplanationInbox {
                    Task { await viewModel.refreshExplanationApprovalInbox() }
                }
            }
        } else {
            EmptyView()
        }
    }

    // MARK: - Attendance Explanation Sheets (Policy Info, Submit, Decision, Date Picker)

    private func explanationPolicyInfoSheet(theme: MiniBiThemePalette) -> some View {
        let overview = viewModel.myExplanationsOverview

        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Quy định giải trình chấm công")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Giải trình được duyệt xác nhận lượt chấm công muộn hoặc bị thiếu là hợp lệ trên bảng tổng hợp chấm công.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)

                    if let currentOverview = overview {
                        let dayEval = currentOverview.dayEvaluation
                        let approverText = AttendanceExplanationContract.friendlyApproverSummary(
                            routing: currentOverview.routing
                        )
                        let profileText = AttendanceExplanationContract.friendlyProfileSummary(
                            dayEval: dayEval
                        )

                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Người duyệt")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Spacer()
                                Text(approverText)
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(SemanticColors.textPrimary)
                            }
                            Divider()
                            HStack {
                                Text("Khung chấm công")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Spacer()
                                Text(profileText)
                                    .font(.caption.weight(.bold).monospacedDigit())
                                    .foregroundColor(theme.primary)
                            }
                            if !dayEval.slots.isEmpty {
                                Divider()
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Các mốc giờ trong ngày")
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textSecondary)
                                    ForEach(dayEval.slots) { slot in
                                        let slotTitle = AttendanceExplanationContract.friendlySlotTitle(
                                            slotLabel: slot.slotLabel,
                                            slotCode: slot.slotCode,
                                            slotIndex: slot.slotIndex
                                        )
                                        let validRange = AttendanceExplanationContract.friendlyValidTimeRange(
                                            targetTime: slot.targetTime,
                                            graceBeforeMinutes: slot.graceBeforeMinutes,
                                            graceAfterMinutes: slot.graceAfterMinutes
                                        )
                                        HStack {
                                            Text(slotTitle)
                                                .font(.caption.weight(.semibold))
                                                .foregroundColor(SemanticColors.textPrimary)
                                            Spacer()
                                            Text("\(slot.targetTime) (\(validRange))")
                                                .font(.caption.monospacedDigit())
                                                .foregroundColor(SemanticColors.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(12)
                        .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusCard)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Thông tin giải trình")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        isShowingExplanationPolicyInfoSheet = false
                    }
                }
            }
        }
    }

    private func explanationSubmitSheet(
        slot: AttendanceExplanationDaySlotEvaluationProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        let targetDate = viewModel.myExplanationsOverview?.dayEvaluation.businessDate ?? viewModel.explanationSelectedDate
        let todayLocal = viewModel.myExplanationsOverview?.todayLocal
        let cleanReasonCount = explanationReasonInput.trimmingCharacters(in: .whitespacesAndNewlines).count
        let friendlyTitle = AttendanceExplanationContract.friendlySlotTitle(
            slotLabel: slot.slotLabel,
            slotCode: slot.slotCode,
            slotIndex: slot.slotIndex
        )
        let validRange = AttendanceExplanationContract.friendlyValidTimeRange(
            targetTime: slot.targetTime,
            graceBeforeMinutes: slot.graceBeforeMinutes,
            graceAfterMinutes: slot.graceAfterMinutes
        )
        let sessionText = AttendanceExplanationContract.sessionLabel(slot.sessionCode)

        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .center, spacing: 8) {
                            Text("Ngày \(LeaveContract.formatIsoDateVi(targetDate)) · \(friendlyTitle) (\(slot.targetTime))")
                                .font(.subheadline.weight(.bold).monospacedDigit())
                                .foregroundColor(SemanticColors.textPrimary)
                            Spacer()
                            Text(AttendanceExplanationContract.originalSlotStatusLabel(slot.originalSlotStatus))
                                .font(.caption2.weight(.bold))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(explanationSlotStatusBgColor(slot.originalSlotStatus))
                                .foregroundColor(explanationSlotStatusTextColor(slot.originalSlotStatus))
                                .clipShape(Capsule())
                        }
                        Text("Khung giờ hợp lệ: \(validRange) · \(sessionText)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Lý do giải trình (3 - 500 ký tự) *")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(SemanticColors.textSecondary)
                        TextField(
                            "Nhập lý do thực tế (ví dụ: kẹt xe, đi công tác tại dự án, lỗi mạng...)",
                            text: Binding(
                                get: { explanationReasonInput },
                                set: {
                                    explanationReasonInput = $0
                                    localExplanationSubmitError = nil
                                }
                            ),
                            axis: .vertical
                        )
                        .lineLimit(3...6)
                        .font(.caption)
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                        .disabled(viewModel.isMutatingExplanation)

                        Text("\(cleanReasonCount)/\(AttendanceExplanationContract.reasonMaxLength) ký tự")
                            .font(.caption2.monospacedDigit())
                            .foregroundColor(SemanticColors.textMuted)
                    }

                    if let err = localExplanationSubmitError ?? viewModel.explanationMutationError, !err.isEmpty {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(SemanticColors.dangerText)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.dangerBg)
                            .cornerRadius(8)
                    }

                    HStack(spacing: 10) {
                        Button("Hủy") {
                            KeyboardDismissal.dismiss()
                            pendingExplanationSubmitSlot = nil
                            localExplanationSubmitError = nil
                        }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color(hex: 0xF1F5F9))
                        )
                        .foregroundColor(SemanticColors.textPrimary)
                        .disabled(viewModel.isMutatingExplanation)

                        Button {
                            KeyboardDismissal.dismiss()
                            if let validationError = AttendanceExplanationContract.validateSubmitInput(
                                businessDate: targetDate,
                                slotCode: slot.slotCode,
                                reason: explanationReasonInput,
                                todayLocal: todayLocal
                            ) {
                                localExplanationSubmitError = validationError
                                return
                            }
                            Task {
                                await viewModel.submitAttendanceExplanation(
                                    businessDate: targetDate,
                                    slotCode: slot.slotCode,
                                    reason: explanationReasonInput,
                                    onSuccess: {
                                        pendingExplanationSubmitSlot = nil
                                        explanationReasonInput = ""
                                        localExplanationSubmitError = nil
                                    }
                                )
                            }
                        } label: {
                            Text(viewModel.isMutatingExplanation ? "Đang gửi..." : "Gửi giải trình")
                                .font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity, minHeight: 46)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(theme.primary)
                                )
                                .foregroundColor(.white)
                        }
                        .disabled(viewModel.isMutatingExplanation)
                    }
                }
                .padding(18)
            }
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Gửi giải trình chấm công")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private func explanationDecisionSheet(
        item: PendingExplanationDecisionSheetItem,
        theme: MiniBiThemePalette
    ) -> some View {
        let targetItem = item.explanation
        let decision = item.decision
        let requesterName = targetItem.requester.resolvedDisplayName()
        let friendlyTitle = AttendanceExplanationContract.friendlySlotTitle(
            slotLabel: targetItem.slotLabel,
            slotCode: targetItem.slotCode,
            slotIndex: targetItem.slotIndex
        )

        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(requesterName)
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("\(LeaveContract.formatIsoDateVi(targetItem.businessDate)) · \(friendlyTitle) (\(targetItem.targetTime))")
                            .font(.caption.weight(.semibold).monospacedDigit())
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("LÝ DO GIẢI TRÌNH")
                            .font(.caption2.weight(.bold))
                            .foregroundColor(theme.primary)
                        Text("Lý do: \(targetItem.reason)")
                            .font(.subheadline.weight(.medium))
                            .foregroundColor(SemanticColors.textPrimary)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(theme.primaryLight.opacity(0.45))
                    )

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Ghi chú phản hồi (tối đa 500 ký tự)")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(SemanticColors.textSecondary)
                        TextField(
                            "Nhập nội dung phản hồi cho nhân viên (nếu có)...",
                            text: Binding(
                                get: { explanationDecisionNoteInput },
                                set: {
                                    explanationDecisionNoteInput = $0
                                    localExplanationDecisionError = nil
                                }
                            ),
                            axis: .vertical
                        )
                        .lineLimit(2...4)
                        .font(.caption)
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                        .disabled(viewModel.isMutatingExplanation)
                    }

                    if let err = localExplanationDecisionError ?? viewModel.explanationMutationError, !err.isEmpty {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(SemanticColors.dangerText)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.dangerBg)
                            .cornerRadius(8)
                    }

                    HStack(spacing: 10) {
                        Button("Đóng") {
                            KeyboardDismissal.dismiss()
                            pendingExplanationDecisionItem = nil
                            localExplanationDecisionError = nil
                        }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color(hex: 0xF1F5F9))
                        )
                        .foregroundColor(SemanticColors.textPrimary)
                        .disabled(viewModel.isMutatingExplanation)

                        Button {
                            KeyboardDismissal.dismiss()
                            let actorId = viewModel.currentExplanationActorAppUserId
                            if let validationError = AttendanceExplanationContract.validateDecisionInput(
                                actorAppUserId: actorId,
                                item: targetItem,
                                decision: decision,
                                decisionNote: explanationDecisionNoteInput
                            ) {
                                localExplanationDecisionError = validationError
                                return
                            }
                            let cleanNote = explanationDecisionNoteInput.trimmingCharacters(in: .whitespacesAndNewlines)
                            Task {
                                await viewModel.decideAttendanceExplanation(
                                    item: targetItem,
                                    decision: decision,
                                    decisionNote: cleanNote.isEmpty ? nil : cleanNote,
                                    onSuccess: {
                                        pendingExplanationDecisionItem = nil
                                        explanationDecisionNoteInput = ""
                                        localExplanationDecisionError = nil
                                    }
                                )
                            }
                        } label: {
                            let labelText: String = {
                                if viewModel.isMutatingExplanation { return "Đang xử lý..." }
                                return decision == "APPROVE" ? "Xác nhận duyệt" : "Xác nhận từ chối"
                            }()
                            Text(labelText)
                                .font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity, minHeight: 46)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(decision == "APPROVE" ? SemanticColors.successText : SemanticColors.dangerText)
                                )
                                .foregroundColor(.white)
                        }
                        .disabled(viewModel.isMutatingExplanation)
                    }
                }
                .padding(18)
            }
            .dismissKeyboardOnTapOutside()
            .navigationTitle(decision == "APPROVE" ? "Duyệt giải trình chấm công" : "Từ chối giải trình chấm công")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.height(340), .medium])
    }

    private func explanationDatePickerSheet(theme: MiniBiThemePalette) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                DatePicker(
                    "Chọn ngày chấm công",
                    selection: $explanationDatePickerSelection,
                    in: ...Date(),
                    displayedComponents: [.date]
                )
                .datePickerStyle(.graphical)
                .environment(\.locale, Locale(identifier: "vi_VN"))

                Button {
                    let chosenIso = formatDateIso(explanationDatePickerSelection)
                    viewModel.setExplanationSelectedDate(chosenIso)
                    isShowingExplanationDatePickerSheet = false
                } label: {
                    let chosenIso = formatDateIso(explanationDatePickerSelection)
                    Text("Xem ngày \(LeaveContract.formatIsoDateVi(chosenIso))")
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(theme.primary)
                        )
                        .foregroundColor(.white)
                }
                Spacer()
            }
            .padding(16)
            .navigationTitle("Chọn ngày chấm công")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        isShowingExplanationDatePickerSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Helpers

    private func explanationSlotStatusBgColor(_ slotStatus: String) -> Color {
        switch slotStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ON_TIME", "EXPLAINED_APPROVED":
            return SemanticColors.successBg
        case "OUT_OF_GRACE", "EXPLAINED_PENDING":
            return SemanticColors.warningBg
        case "MISSING", "EXPLAINED_REJECTED":
            return SemanticColors.dangerBg
        default:
            return Color(hex: 0xF1F5F9)
        }
    }

    private func explanationSlotStatusTextColor(_ slotStatus: String) -> Color {
        switch slotStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "ON_TIME", "EXPLAINED_APPROVED":
            return SemanticColors.successText
        case "OUT_OF_GRACE", "EXPLAINED_PENDING":
            return SemanticColors.warningText
        case "MISSING", "EXPLAINED_REJECTED":
            return SemanticColors.dangerText
        default:
            return SemanticColors.textSecondary
        }
    }

    private func parseIsoDate(_ iso: String) -> Date? {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy-MM-dd"
        return df.date(from: iso.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    private func leaveStatusBgColor(_ status: String) -> Color {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "APPROVED":
            return SemanticColors.successBg
        case "PENDING":
            return SemanticColors.warningBg
        case "REJECTED", "CANCELLED":
            return SemanticColors.dangerBg
        default:
            return Color(hex: 0xF1F5F9)
        }
    }

    private func leaveStatusTextColor(_ status: String) -> Color {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "APPROVED":
            return SemanticColors.successText
        case "PENDING":
            return SemanticColors.warningText
        case "REJECTED", "CANCELLED":
            return SemanticColors.dangerText
        default:
            return SemanticColors.textSecondary
        }
    }

    private func outboxBgColor(_ status: String) -> Color {
        switch status.uppercased() {
        case AttendanceContract.statusSynced:
            return SemanticColors.successBg
        case AttendanceContract.statusFailedPermanent:
            return SemanticColors.dangerBg
        default:
            return SemanticColors.warningBg
        }
    }

    private func outboxTextColor(_ status: String) -> Color {
        switch status.uppercased() {
        case AttendanceContract.statusSynced:
            return SemanticColors.successText
        case AttendanceContract.statusFailedPermanent:
            return SemanticColors.dangerText
        default:
            return SemanticColors.warningText
        }
    }
}

// MARK: - Attendance Outbox Photo Thumbnail & Full-Screen Preview Sheet (Mirrors Android `AttendanceHistoryCard`)

private struct AttendanceOutboxPhotoThumbnailView: View {
    let item: AttendanceOutboxItem
    let outboxService: AttendanceOutboxService
    var size: CGFloat = 52

    @State private var loadedImage: UIImage?
    @State private var isLoadingRemote: Bool = false

    var body: some View {
        ZStack {
            if let uiImage = resolvedImmediateImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipped()
            } else if isLoadingRemote {
                ProgressView()
                    .scaleEffect(0.75)
                    .frame(width: size, height: size)
            } else {
                VStack(spacing: 2) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: size * 0.28, weight: .semibold))
                        .foregroundColor(SemanticColors.textMuted)
                    Text("Ảnh")
                        .font(.system(size: max(8, size * 0.17), weight: .semibold))
                        .foregroundColor(SemanticColors.textMuted)
                }
                .frame(width: size, height: size)
            }
        }
        .frame(width: size, height: size)
        .background(Color(hex: 0xE2E8F0))
        .clipShape(RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                .stroke(SemanticColors.borderSubtle, lineWidth: 0.8)
        )
        .task(id: "\(item.localId)-\(item.serverCheckinId ?? "")-\(item.photoLocalPath ?? "")") {
            if loadedImage != nil { return }
            if let localData = outboxService.loadLocalPhotoData(for: item),
               let img = UIImage(data: localData) {
                loadedImage = img
                return
            }
            guard AttendanceOutboxService.resolveRemotePhotoPath(for: item) != nil else { return }
            isLoadingRemote = true
            let remoteData = await outboxService.loadPhotoData(for: item)
            isLoadingRemote = false
            if let remoteData, let img = UIImage(data: remoteData) {
                loadedImage = img
            }
        }
    }

    private var resolvedImmediateImage: UIImage? {
        if let loadedImage {
            return loadedImage
        }
        if let localData = outboxService.loadLocalPhotoData(for: item),
           let img = UIImage(data: localData) {
            return img
        }
        return nil
    }
}

private struct AttendancePhotoPreviewSheet: View {
    let item: AttendanceOutboxItem
    let outboxService: AttendanceOutboxService
    let theme: MiniBiThemePalette

    @Environment(\.dismiss) private var dismiss
    @State private var loadedImage: UIImage?
    @State private var isLoading: Bool = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ZStack {
                        if let uiImage = loadedImage ?? outboxService.loadLocalPhotoData(for: item).flatMap(UIImage.init(data:)) {
                            Image(uiImage: uiImage)
                                .resizable()
                                .scaledToFit()
                                .frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        } else if isLoading {
                            VStack(spacing: 10) {
                                ProgressView()
                                Text("Đang tải ảnh chấm công...")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            .frame(maxWidth: .infinity, minHeight: 240)
                        } else {
                            VStack(spacing: 8) {
                                Image(systemName: "photo.badge.exclamationmark")
                                    .font(.system(size: 32))
                                    .foregroundColor(SemanticColors.textMuted)
                                Text("Không tải được ảnh chấm công cho bản ghi này.")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                            }
                            .frame(maxWidth: .infinity, minHeight: 200)
                        }
                    }
                    .background(Color.white)
                    .cornerRadius(14)

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Thời gian chấm công")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Spacer()
                            Text(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: item.clientCapturedAt, fallbackEpochMs: item.createdAtEpochMs))
                                .font(.caption.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        Divider()
                        HStack {
                            Text("Trạng thái")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Spacer()
                            Text(AttendanceContract.statusDisplayLabel(item.status))
                                .font(.caption.weight(.bold))
                                .foregroundColor(theme.primary)
                        }
                        Divider()
                        HStack {
                            Text("Tọa độ GPS")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Spacer()
                            Text(String(format: "%.6f, %.6f", item.lat, item.lng))
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        Divider()
                        HStack {
                            Text("Nguồn thời gian")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Spacer()
                            Text(AttendanceTimeContract.timeSourceDisplayLabel(item.timeSource))
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        if !item.note.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Ghi chú")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Text(item.note)
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textPrimary)
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(14)
                }
                .padding(14)
            }
            .background(theme.background)
            .navigationTitle("Chi tiết lượt chấm công")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        dismiss()
                    }
                }
            }
            .task {
                if let localData = outboxService.loadLocalPhotoData(for: item),
                   let img = UIImage(data: localData) {
                    loadedImage = img
                    return
                }
                isLoading = true
                let data = await outboxService.loadPhotoData(for: item)
                isLoading = false
                if let data, let img = UIImage(data: data) {
                    loadedImage = img
                }
            }
        }
    }
}
