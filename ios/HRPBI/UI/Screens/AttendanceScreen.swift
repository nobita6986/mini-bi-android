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

    @State private var capturedPhotoData: Data?
    @State private var frozenCaptureTimestamp: CapturedAttendanceTimestamp?
    @State private var noteInput: String = ""
    @State private var isShowingCamera: Bool = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var exportedReportUrl: URL?
    @State private var selectedPreviewOutboxItem: AttendanceOutboxItem?
    @State private var liveClockTick: Int64 = 0
    @State private var lastAutoTimeSyncAttemptSec: TimeInterval = 0

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
                await viewModel.syncAttendanceTimeAnchor()
                await viewModel.refreshMyLeaveSummary(year: viewModel.leaveYear)
                await viewModel.refreshMyAttendanceExplanations()
            }
        }
        .onChange(of: scenePhase) { newPhase in
            guard newPhase == .active else { return }
            locationManager.requestLocationUpdate()
            lastAutoTimeSyncAttemptSec = Date().timeIntervalSince1970
            Task {
                await viewModel.syncAttendanceTimeAnchor()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            locationManager.requestLocationUpdate()
            lastAutoTimeSyncAttemptSec = Date().timeIntervalSince1970
            Task {
                await viewModel.syncAttendanceTimeAnchor()
            }
        }
        .onReceive(liveClockTimer) { now in
            liveClockTick &+= 1
            viewModel.refreshAttendanceTimeEvaluation()
            let eval = viewModel.attendanceTimeEvaluation
            let nowSec = now.timeIntervalSince1970
            let needsSync = !eval.isTrusted || (eval.remainingValidMs ?? 0) <= AttendanceTimeContract.refreshBeforeExpiryMs
            if needsSync && (nowSec - lastAutoTimeSyncAttemptSec >= 15.0) {
                lastAutoTimeSyncAttemptSec = nowSec
                Task {
                    await viewModel.syncAttendanceTimeAnchor()
                }
            }
        }
        .sheet(isPresented: $isShowingCamera) {
            CameraPhotoCaptureSheet { data in
                capturedPhotoData = data
                frozenCaptureTimestamp = viewModel.timeAnchorManager.resolveCaptureTime()
                if !viewModel.timeAnchorManager.evaluateAnchor().isTrusted {
                    Task {
                        await viewModel.syncAttendanceTimeAnchor()
                        if viewModel.timeAnchorManager.evaluateAnchor().isTrusted {
                            frozenCaptureTimestamp = viewModel.timeAnchorManager.resolveCaptureTime()
                        }
                    }
                }
            }
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
        }
        .sheet(item: $pendingDecisionItem) { item in
            approverDecisionSheet(item: item, theme: theme)
        }
        .sheet(item: $datePickerTargetDay) { day in
            leaveDatePickerSheet(day: day, theme: theme)
        }
        .sheet(isPresented: $isShowingRangePickerSheet) {
            leaveDateRangePickerSheet(theme: theme)
        }
        .sheet(item: $pendingExplanationSubmitSlot) { slot in
            explanationSubmitSheet(slot: slot, theme: theme)
        }
        .sheet(item: $pendingExplanationDecisionItem) { item in
            explanationDecisionSheet(item: item, theme: theme)
        }
        .sheet(isPresented: $isShowingExplanationDatePickerSheet) {
            explanationDatePickerSheet(theme: theme)
        }
        .sheet(item: $selectedPreviewOutboxItem) { previewItem in
            AttendancePhotoPreviewSheet(
                item: previewItem,
                outboxService: viewModel.attendanceOutboxService,
                theme: theme
            )
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
        .background(Color.white)
        .cornerRadius(14)
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
            viewModel.selectAttendanceTab(tab)
        } label: {
            HStack(spacing: isCompactRow ? 3 : 5) {
                Image(systemName: tab.systemIconName)
                    .font(.system(size: isCompactRow ? 10 : 11, weight: .bold))
                Text(tab.label)
                    .font(.system(size: isCompactRow ? 11.5 : 12.5, weight: isSelected ? .bold : .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .padding(.horizontal, isCompactRow ? 4 : 8)
            .background(isSelected ? theme.primary : Color.clear)
            .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
            .cornerRadius(10)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Tab 1: Check-In Content (100% Preserved)

    private func checkInTabContent(theme: MiniBiThemePalette) -> some View {
        let eval = viewModel.attendanceTimeEvaluation

        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 1. Server Time Anchor & Today Count Card
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        HStack(spacing: 6) {
                            Image(systemName: eval.isTrusted ? "clock.badge.checkmark.fill" : "clock.badge.exclamationmark.fill")
                                .foregroundColor(eval.isTrusted ? SemanticColors.successText : SemanticColors.warningText)
                            Text(eval.isTrusted ? "Đã neo giờ chuẩn máy chủ" : "Đang dùng giờ thiết bị tạm thời")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(eval.isTrusted ? SemanticColors.successText : SemanticColors.warningText)
                        }
                        Spacer()
                        Button {
                            Task { await viewModel.syncAttendanceTimeAnchor() }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                Text("Đồng bộ giờ")
                            }
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(theme.primaryLight)
                            .foregroundColor(theme.primary)
                            .cornerRadius(8)
                        }
                    }

                    Text(eval.detailMessageVi)
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)

                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            let _ = liveClockTick
                            Text("Giờ chuẩn hiện tại")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                            let resolved = viewModel.timeAnchorManager.resolveCaptureTime()
                            Text(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: resolved.capturedAtIso, fallbackEpochMs: resolved.capturedEpochMs))
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("Đã chấm công hôm nay")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                            Text("\(viewModel.todayServerAttendanceCount) lượt")
                                .font(.headline.weight(.heavy))
                                .foregroundColor(theme.primary)
                        }
                    }
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(10)
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)

                // 2. GPS & Photo Check-in Card
                VStack(alignment: .leading, spacing: 12) {
                    Text("Tạo lượt chấm công mới")
                        .font(.headline)
                        .foregroundColor(SemanticColors.textPrimary)

                    // GPS Coordinate Row
                    HStack {
                        Image(systemName: "location.fill")
                            .foregroundColor(theme.primary)
                        if let loc = locationManager.currentLocation {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(String(format: "Toạ độ: %.6f, %.6f", loc.coordinate.latitude, loc.coordinate.longitude))
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.textPrimary)
                                Text(String(format: "Sai số GPS: ±%.1f m", max(0, loc.horizontalAccuracy)))
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textMuted)
                            }
                        } else {
                            Text(locationManager.locationErrorMessage ?? "Đang định vị GPS...")
                                .font(.caption)
                                .foregroundColor(locationManager.isPermissionDenied ? SemanticColors.dangerText : SemanticColors.textSecondary)
                        }
                        Spacer()
                        if locationManager.isPermissionDenied {
                            Button("Mở Cài đặt") {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                            }
                            .font(.caption.weight(.bold))
                            .foregroundColor(SemanticColors.dangerText)
                        } else {
                            Button("Lấy lại GPS") {
                                locationManager.requestLocationUpdate()
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundColor(theme.primary)
                        }
                    }
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(10)

                    // Photo Capture Row (Live Camera Only — matches Android 100%, freezes timestamp immediately upon capture)
                    Button {
                        locationManager.requestLocationUpdate()
                        Task { await viewModel.syncAttendanceTimeAnchor() }
                        isShowingCamera = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "camera.fill")
                            Text(capturedPhotoData == nil ? "Chụp ảnh chấm công" : "Chụp lại ảnh")
                        }
                        .font(.caption.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(theme.primary)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }

                    if let photoData = capturedPhotoData, let uiImg = UIImage(data: photoData) {
                        HStack(spacing: 12) {
                            Image(uiImage: uiImg)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 72, height: 72)
                                .clipShape(RoundedRectangle(cornerRadius: 10))

                            VStack(alignment: .leading, spacing: 4) {
                                Text("Đã khoá mốc thời gian chụp")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(SemanticColors.successText)
                                if let frozen = frozenCaptureTimestamp {
                                    Text(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: frozen.capturedAtIso, fallbackEpochMs: frozen.capturedEpochMs))
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textPrimary)
                                    Text(frozen.statusBadgeVi)
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textMuted)
                                }
                            }
                            Spacer()
                            Button {
                                capturedPhotoData = nil
                                frozenCaptureTimestamp = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                            }
                        }
                        .padding(10)
                        .background(SemanticColors.successBg.opacity(0.45))
                        .cornerRadius(10)
                    }

                    TextField("Ghi chú chấm công (ca làm, địa điểm, dự án...)", text: $noteInput)
                        .font(.caption)
                        .foregroundColor(SemanticColors.textPrimary)
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )

                    let canSubmitAttendance = capturedPhotoData != nil && locationManager.currentLocation != nil && !viewModel.isSubmittingAttendance

                    Button {
                        guard let photoData = capturedPhotoData else {
                            viewModel.showBanner("Vui lòng chụp ảnh xác thực trước khi chấm công.", isError: true)
                            return
                        }
                        guard let loc = locationManager.currentLocation else {
                            locationManager.requestLocationUpdate()
                            viewModel.showBanner("Đang lấy tọa độ GPS. Vui lòng bật Vị trí và đợi GPS định vị xong rồi bấm chấm công.", isError: true)
                            return
                        }
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
                            capturedPhotoData = nil
                            frozenCaptureTimestamp = nil
                            noteInput = ""
                        }
                    } label: {
                        HStack {
                            Spacer()
                            if viewModel.isSubmittingAttendance {
                                ProgressView().tint(.white).padding(.trailing, 6)
                            }
                            Text(viewModel.isSubmittingAttendance ? "Đang xử lý chấm công..." : "Ghi nhận chấm công")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(.white)
                            Spacer()
                        }
                        .padding(.vertical, 13)
                        .background(!canSubmitAttendance ? Color.gray : SemanticColors.successText)
                        .cornerRadius(12)
                    }
                    .disabled(!canSubmitAttendance)
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)

                // 3. Offline Outbox & Synced History + Export Report
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Hàng đợi & Lịch sử chấm công (\(viewModel.attendanceOutboxItems.count))")
                            .font(.headline)
                            .foregroundColor(SemanticColors.textPrimary)
                        Spacer()

                        Button {
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
                            .background(theme.primaryLight)
                            .foregroundColor(theme.primary)
                            .cornerRadius(8)
                        }

                        Button {
                            Task {
                                let userKey = viewModel.currentUser?.username ?? "guest"
                                let res = await viewModel.attendanceOutboxService.exportAttendanceReport(userKey: userKey)
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
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color(hex: 0xF1F5F9))
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(8)
                        }
                    }

                    if viewModel.attendanceOutboxItems.isEmpty {
                        Text("Chưa có bản ghi chấm công nào trên thiết bị.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .padding(.vertical, 12)
                    } else {
                        ForEach(viewModel.attendanceOutboxItems) { item in
                            HStack(alignment: .top, spacing: 12) {
                                Button {
                                    selectedPreviewOutboxItem = item
                                } label: {
                                    AttendanceOutboxPhotoThumbnailView(
                                        item: item,
                                        outboxService: viewModel.attendanceOutboxService
                                    )
                                }
                                .buttonStyle(.plain)

                                VStack(alignment: .leading, spacing: 5) {
                                    HStack {
                                        Text(AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: item.clientCapturedAt, fallbackEpochMs: item.createdAtEpochMs))
                                            .font(.caption.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                        Spacer()
                                        Text(AttendanceContract.statusDisplayLabel(item.status))
                                            .font(.caption2.weight(.bold))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(outboxBgColor(item.status))
                                            .foregroundColor(outboxTextColor(item.status))
                                            .clipShape(Capsule())
                                    }

                                    Text(String(format: "GPS: %.5f, %.5f · Nguồn giờ: %@", item.lat, item.lng, AttendanceTimeContract.timeSourceDisplayLabel(item.timeSource)))
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textSecondary)

                                    if !item.note.isEmpty {
                                        Text("Ghi chú: \(item.note)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    }

                                    if let err = item.lastErrorMessage, !err.isEmpty {
                                        HStack {
                                            Text(err)
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.dangerText)
                                            Spacer()
                                            if item.status == AttendanceContract.statusFailedPermanent {
                                                Button("Xoá") {
                                                    viewModel.attendanceOutboxService.removeRejectedItem(localId: item.localId)
                                                }
                                                .font(.caption2.weight(.bold))
                                                .foregroundColor(SemanticColors.dangerText)
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(12)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                selectedPreviewOutboxItem = item
                            }
                        }
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
            }
            .padding(14)
        }
        .refreshable {
            locationManager.requestLocationUpdate()
            await viewModel.syncAttendanceTimeAnchor()
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
                ForEach(summary.requests) { req in
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

    // MARK: - Tab 2: Attendance Explanation ("Giải trình chấm công") Content

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

                // Header & Date Selector Card
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("GIẢI TRÌNH CHẤM CÔNG CÁ NHÂN")
                                .font(.caption2.weight(.bold))
                                .foregroundColor(theme.primary)
                            Text("Giải trình chấm công")
                                .font(.headline.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                        Spacer()
                        Button {
                            Task { await viewModel.refreshMyAttendanceExplanations(businessDate: selectedDate) }
                        } label: {
                            if viewModel.isLoadingMyExplanations {
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
                        .disabled(viewModel.isLoadingMyExplanations || viewModel.isMutatingExplanation)
                    }

                    Text("Giải trình được duyệt chỉ xác nhận lượt chấm muộn/bị thiếu là hợp lệ trên bảng tổng hợp; ứng dụng không tự tạo lượt chấm công giả hoặc quyết định chế tài nhân sự.")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)

                    // Business Date Selector Row
                    HStack(spacing: 8) {
                        Button {
                            viewModel.shiftExplanationSelectedDate(days: -1)
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.caption.weight(.bold))
                                .frame(width: 34, height: 36)
                                .background(Color(hex: 0xF1F5F9))
                                .foregroundColor(SemanticColors.textPrimary)
                                .cornerRadius(8)
                        }
                        .disabled(viewModel.isLoadingMyExplanations)

                        Button {
                            explanationDatePickerSelection = parseIsoDate(selectedDate) ?? Date()
                            isShowingExplanationDatePickerSheet = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "calendar")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(theme.primary)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text("Ngày nghiệp vụ")
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(SemanticColors.textMuted)
                                    Text("\(LeaveContract.formatIsoDateVi(selectedDate)) (\(selectedDate))")
                                        .font(.caption.weight(.bold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)

                        Button {
                            viewModel.shiftExplanationSelectedDate(days: 1)
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.bold))
                                .frame(width: 34, height: 36)
                                .background(Color(hex: 0xF1F5F9))
                                .foregroundColor(selectedDate >= todayIso ? SemanticColors.textMuted : SemanticColors.textPrimary)
                                .cornerRadius(8)
                        }
                        .disabled(viewModel.isLoadingMyExplanations || selectedDate >= todayIso)

                        Button {
                            viewModel.setExplanationSelectedDate(todayIso)
                        } label: {
                            Text("Hôm nay")
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 10)
                                .frame(height: 36)
                                .background(theme.primaryLight)
                                .foregroundColor(theme.primary)
                                .cornerRadius(8)
                        }
                        .disabled(viewModel.isLoadingMyExplanations)
                    }

                    if let currentOverview = overview {
                        let dayEval = currentOverview.dayEvaluation
                        let profileModeBadge = dayEval.policyIsDefaultUsed
                            ? "Mặc định công ty"
                            : "Nhóm cấu hình riêng"

                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .center, spacing: 8) {
                                Text("Bảng chấm công áp dụng: \(dayEval.resolvedProfileDisplayName())")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(theme.primary)
                                Spacer()
                                Text(profileModeBadge)
                                    .font(.caption2.weight(.semibold))
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(theme.primary.opacity(0.12))
                                    .foregroundColor(theme.primary)
                                    .cornerRadius(6)
                            }
                            Text("Mã cấu hình: \(dayEval.policyProfileCodeUsed) • Phiên bản v\(dayEval.policyVersionUsed) • \(dayEval.slots.count) mốc/ngày")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Text("Khung giờ chuẩn: \(dayEval.resolvedSlotScheduleSummary())")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.primaryLight.opacity(0.65))
                        .cornerRadius(10)

                        let routing = currentOverview.routing
                        let approverName = routing.primaryApprover?.resolvedDisplayName() ?? "Hàng đợi Admin / BGĐ"
                        let routeLabel = LeaveContract.leaveRouteKindLabel(
                            routing.routeKind,
                            teamName: routing.teamName,
                            unitKind: routing.unitKind
                        )
                        let fallbackLabel = LeaveContract.leaveFallbackReasonLabel(routing.fallbackReason)

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Luồng xét duyệt: \(routeLabel)")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                            Text("Người duyệt chính: \(approverName)")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            if let fallbackLabel, !fallbackLabel.isEmpty {
                                Text("Lưu ý định tuyến: \(fallbackLabel)")
                                    .font(.caption2.weight(.semibold))
                                    .foregroundColor(SemanticColors.warningText)
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(10)
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)

                if viewModel.isLoadingMyExplanations && overview == nil {
                    HStack(spacing: 10) {
                        Spacer()
                        ProgressView()
                        Text("Đang tải đánh giá mốc chấm công và lịch sử giải trình...")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                        Spacer()
                    }
                    .padding(24)
                    .background(Color.white)
                    .cornerRadius(14)
                }

                if let currentOverview = overview {
                    let dayEval = currentOverview.dayEvaluation

                    // Dynamic Day Slots Header
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Các mốc chấm công ngày \(dayEval.businessDate)")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text(dayEval.resolvedProfileBadgeLabel())
                            .font(.caption2.weight(.bold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(theme.primaryLight)
                            .foregroundColor(theme.primary)
                            .cornerRadius(8)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 2)

                    if dayEval.slots.isEmpty {
                        Text("Không có mốc chấm công nào được cấu hình cho ngày này.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.white)
                            .cornerRadius(14)
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
                    Text("Lịch sử giải trình chấm công của tôi (\(currentOverview.pagination.totalRows))")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)
                        .padding(.top, 4)
                        .padding(.horizontal, 2)

                    if currentOverview.items.isEmpty {
                        Text("Bạn chưa gửi giải trình chấm công nào.")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                            .padding(16)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.white)
                            .cornerRadius(14)
                    } else {
                        ForEach(currentOverview.items) { item in
                            explanationMyHistoryCard(item: item, theme: theme)
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
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(slot.slotLabel) (\(slot.slotCode))")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Text("\(AttendanceExplanationContract.sessionLabel(slot.sessionCode)) • Khung giờ: \(AttendanceExplanationContract.formatGraceWindow(targetTime: slot.targetTime, graceBeforeMinutes: slot.graceBeforeMinutes, graceAfterMinutes: slot.graceAfterMinutes))")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }
                Spacer()
                Text(AttendanceExplanationContract.slotStatusLabel(slot.slotStatus))
                    .font(.caption2.weight(.bold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(explanationSlotStatusBgColor(slot.slotStatus))
                    .foregroundColor(explanationSlotStatusTextColor(slot.slotStatus))
                    .cornerRadius(8)
            }

            if slot.checkins.isEmpty {
                Text("Chưa có lượt chấm công gắn vào mốc này.")
                    .font(.caption)
                    .foregroundColor(SemanticColors.textSecondary)
            } else {
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(slot.checkins) { checkin in
                        let devLabel = AttendanceExplanationContract.formatDeviationBadge(checkin)
                        let timeText = checkin.timeLocal.isEmpty ? checkin.capturedAtLocal : checkin.timeLocal
                        let devSuffix = (devLabel?.isEmpty == false) ? " (\(devLabel!))" : ""
                        let distSuffix = checkin.distanceMeters.map { " • \($0)m" } ?? ""
                        Text("• Lần #\(checkin.sequenceInDay) lúc \(timeText)\(devSuffix)\(distSuffix)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(SemanticColors.textPrimary)
                    }
                }
            }

            if let existingExp = slot.explanation {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Giải trình đã gửi: \(AttendanceExplanationContract.explanationStatusLabel(existingExp.status)) (v\(existingExp.rowVersion))")
                        .font(.caption.weight(.bold))
                        .foregroundColor(leaveStatusTextColor(existingExp.status))
                    Text("Lý do: \(existingExp.reason)")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textPrimary)
                    let deciderName = existingExp.decidedByDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    let cleanNote = existingExp.decisionNote.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !deciderName.isEmpty || !cleanNote.isEmpty {
                        let noteSuffix = cleanNote.isEmpty ? "" : " • Phản hồi: \(cleanNote)"
                        Text("Người duyệt: \(deciderName.isEmpty ? "--" : deciderName)\(noteSuffix)")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(leaveStatusBgColor(existingExp.status))
                .cornerRadius(10)
            } else if slot.eligibleForExplanation && canSubmitExplanation {
                HStack {
                    Spacer()
                    Button {
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
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(theme.primary)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    }
                    .disabled(viewModel.isMutatingExplanation)
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
    }

    private func explanationMyHistoryCard(
        item: AttendanceExplanationItemProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        let isExpanded = expandedMyExplanationIds.contains(item.explanationId)
        let cleanDecisionNote = item.decisionNote.trimmingCharacters(in: .whitespacesAndNewlines)

        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text("\(item.businessDate) • \(item.slotLabel) (\(item.targetTime))")
                    .font(.subheadline.weight(.bold))
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                Text(AttendanceExplanationContract.explanationStatusLabel(item.status))
                    .font(.caption2.weight(.bold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(leaveStatusBgColor(item.status))
                    .foregroundColor(leaveStatusTextColor(item.status))
                    .cornerRadius(8)
            }

            Text("Bảng chấm công: \(item.resolvedProfileVersionLabel()) • Trạng thái mốc gốc: \(AttendanceExplanationContract.originalSlotStatusLabel(item.originalSlotStatus))")
                .font(.caption2)
                .foregroundColor(SemanticColors.textSecondary)

            Text("Lý do giải trình: \(item.reason)")
                .font(.caption)
                .foregroundColor(SemanticColors.textPrimary)

            if item.decidedBy != nil || !cleanDecisionNote.isEmpty || (item.decidedAtLocal?.isEmpty == false) {
                let decider = item.decidedBy?.resolvedDisplayName() ?? "--"
                let decidedTime = item.decidedAtLocal ?? item.decidedAt ?? "--"
                VStack(alignment: .leading, spacing: 2) {
                    Text("Người duyệt: \(decider) (\(decidedTime))")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Text("Nội dung phản hồi: \(cleanDecisionNote.isEmpty ? "Không có ghi chú phản hồi" : cleanDecisionNote)")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textSecondary)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(10)
            }

            Button {
                if isExpanded {
                    expandedMyExplanationIds.remove(item.explanationId)
                } else {
                    expandedMyExplanationIds.insert(item.explanationId)
                }
            } label: {
                Text(isExpanded ? "Thu gọn chi tiết" : "Xem chi tiết")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(theme.primary)
            }
            .buttonStyle(.plain)

            if isExpanded {
                VStack(alignment: .leading, spacing: 4) {
                    Divider()
                    let subTime = item.submittedAtLocal.isEmpty ? item.submittedAt : item.submittedAtLocal
                    Text("Phiên bản bản ghi: v\(item.rowVersion) • Gửi lúc: \(subTime)")
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textSecondary)
                    ForEach(item.events) { ev in
                        let evTime = ev.createdAtLocal.isEmpty ? ev.createdAt : ev.createdAtLocal
                        let actorName = ev.actorDisplayName ?? "Hệ thống"
                        Text("• \(AttendanceExplanationContract.auditEventTypeLabel(ev.eventType)) bởi \(actorName) (\(evTime)): \(ev.note)")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textSecondary)
                    }
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
    }

    // MARK: - Tab 4: Attendance Explanation Approval ("Xét duyệt giải trình") Content

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
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("HỘP THƯ PHÊ DUYỆT GIẢI TRÌNH")
                                    .font(.caption2.weight(.bold))
                                    .foregroundColor(theme.primary)
                                Text("Xét duyệt giải trình")
                                    .font(.headline.weight(.bold))
                                    .foregroundColor(SemanticColors.textPrimary)
                            }
                            Spacer()
                            Text("Chờ duyệt: \(pendingCount)")
                                .font(.caption.weight(.bold))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(SemanticColors.warningBg)
                                .foregroundColor(SemanticColors.warningText)
                                .cornerRadius(8)

                            Button {
                                Task { await viewModel.refreshExplanationApprovalInbox() }
                            } label: {
                                if viewModel.isLoadingExplanationInbox {
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
                            .disabled(viewModel.isLoadingExplanationInbox)
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(AttendanceExplanationContract.approvalStatusFilters, id: \.self) { code in
                                    let isSelected = viewModel.explanationInboxStatusFilter == code
                                    Button {
                                        viewModel.setExplanationInboxFilter(code)
                                    } label: {
                                        Text(AttendanceExplanationContract.statusFilterLabel(code))
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
                            Text("Đang tải hộp thư xét duyệt giải trình...")
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
                            Text("Không có giải trình chấm công nào trong bộ lọc hiện tại.")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(24)
                        .background(Color.white)
                        .cornerRadius(14)
                    } else {
                        ForEach(items) { item in
                            let isExpanded = expandedInboxExplanationIds.contains(item.explanationId)
                            let isSelfRequest = !actorId.isEmpty &&
                                item.appUserId.caseInsensitiveCompare(actorId) == .orderedSame
                            let canAct = item.canDecide &&
                                item.status.uppercased() == "PENDING" &&
                                !isSelfRequest

                            VStack(alignment: .leading, spacing: 8) {
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.requester.resolvedDisplayName())
                                            .font(.subheadline.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                        let subMeta = [
                                            item.requester.personnelCode,
                                            item.requester.loginId,
                                            item.routedTeam.teamName
                                        ]
                                        .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                                        .filter { !$0.isEmpty }
                                        .joined(separator: " • ")
                                        if !subMeta.isEmpty {
                                            Text(subMeta)
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.textMuted)
                                        }
                                    }
                                    Spacer()
                                    Text(AttendanceExplanationContract.explanationStatusLabel(item.status))
                                        .font(.caption2.weight(.bold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(leaveStatusBgColor(item.status))
                                        .foregroundColor(leaveStatusTextColor(item.status))
                                        .cornerRadius(8)
                                }

                                Text("Ngày: \(item.businessDate) • \(item.slotLabel) (\(item.slotCode)) • Khung giờ: \(AttendanceExplanationContract.formatGraceWindow(targetTime: item.targetTime, graceBeforeMinutes: item.graceBeforeMinutes, graceAfterMinutes: item.graceAfterMinutes))")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.textPrimary)

                                Text("Bảng chấm công: \(item.resolvedProfileVersionLabel()) • Trạng thái gốc: \(AttendanceExplanationContract.originalSlotStatusLabel(item.originalSlotStatus))")
                                    .font(.caption2.weight(.semibold))
                                    .foregroundColor(explanationSlotStatusTextColor(item.originalSlotStatus))

                                if item.actualCheckins.isEmpty {
                                    Text("Chấm công thực tế: Chưa có lượt chấm ở mốc này")
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textSecondary)
                                } else {
                                    VStack(alignment: .leading, spacing: 2) {
                                        ForEach(item.actualCheckins) { checkin in
                                            let dev = AttendanceExplanationContract.formatDeviationBadge(checkin)
                                            let timeText = checkin.timeLocal.isEmpty ? checkin.capturedAtLocal : checkin.timeLocal
                                            let devSuffix = (dev?.isEmpty == false) ? " (\(dev!))" : ""
                                            Text("Chấm công thực tế: Lần #\(checkin.sequenceInDay) lúc \(timeText)\(devSuffix)")
                                                .font(.caption.monospacedDigit())
                                                .foregroundColor(SemanticColors.textPrimary)
                                        }
                                    }
                                }

                                Text("Lý do nhân viên giải trình: \(item.reason)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textPrimary)

                                let cleanDecNote = item.decisionNote.trimmingCharacters(in: .whitespacesAndNewlines)
                                if item.decidedBy != nil || !cleanDecNote.isEmpty {
                                    let deciderName = item.decidedBy?.resolvedDisplayName() ?? "--"
                                    Text("Phản hồi xét duyệt (\(deciderName)): \(cleanDecNote.isEmpty ? "Không có ghi chú" : cleanDecNote)")
                                        .font(.caption)
                                        .foregroundColor(SemanticColors.textSecondary)
                                }

                                HStack {
                                    Button {
                                        if isExpanded {
                                            expandedInboxExplanationIds.remove(item.explanationId)
                                        } else {
                                            expandedInboxExplanationIds.insert(item.explanationId)
                                        }
                                    } label: {
                                        Text(isExpanded ? "Thu gọn chi tiết" : "Xem chi tiết")
                                            .font(.caption.weight(.semibold))
                                            .foregroundColor(theme.primary)
                                    }
                                    .buttonStyle(.plain)

                                    Spacer()

                                    if isSelfRequest {
                                        Text("Giải trình của chính bạn (không tự duyệt)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    } else if canAct {
                                        HStack(spacing: 8) {
                                            Button {
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
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(SemanticColors.dangerBg)
                                                .foregroundColor(SemanticColors.dangerText)
                                                .cornerRadius(8)
                                            }
                                            .disabled(viewModel.isMutatingExplanation)

                                            Button {
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
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(SemanticColors.successText)
                                                .foregroundColor(.white)
                                                .cornerRadius(8)
                                            }
                                            .disabled(viewModel.isMutatingExplanation)
                                        }
                                    }
                                }

                                if isExpanded {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Divider()
                                        let routeLabel = LeaveContract.leaveRouteKindLabel(
                                            item.routeKind,
                                            teamName: item.routedTeam.teamName,
                                            unitKind: item.unitKindSnapshot
                                        )
                                        Text("Luồng định tuyến: \(routeLabel) • Phiên bản: v\(item.rowVersion)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textSecondary)
                                        if let fb = LeaveContract.leaveFallbackReasonLabel(item.fallbackReason), !fb.isEmpty {
                                            Text("Lý do dự phòng: \(fb)")
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.warningText)
                                        }
                                        if !item.events.isEmpty {
                                            Text("Lịch sử xử lý:")
                                                .font(.caption2.weight(.semibold))
                                                .foregroundColor(SemanticColors.textPrimary)
                                            ForEach(item.events) { ev in
                                                let evTime = ev.createdAtLocal.isEmpty ? ev.createdAt : ev.createdAtLocal
                                                let actorName = ev.actorDisplayName ?? "Hệ thống"
                                                Text("• \(AttendanceExplanationContract.auditEventTypeLabel(ev.eventType)) — \(actorName) (\(evTime)): \(ev.note)")
                                                    .font(.caption2)
                                                    .foregroundColor(SemanticColors.textSecondary)
                                            }
                                        }
                                    }
                                    .padding(10)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color(hex: 0xF8FAFC))
                                    .cornerRadius(10)
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(14)
                        }

                        if let pagination = inbox?.pagination, pagination.totalRows > pagination.limit {
                            let currentPage = viewModel.explanationInboxPage
                            let totalPages = max(1, Int(ceil(Double(pagination.totalRows) / Double(max(1, pagination.limit)))))
                            HStack {
                                Button("Trang trước") {
                                    viewModel.setExplanationInboxPage(currentPage - 1)
                                }
                                .font(.caption.weight(.bold))
                                .disabled(currentPage <= 1 || viewModel.isLoadingExplanationInbox)
                                Spacer()
                                Text("Trang \(currentPage)/\(totalPages) (\(pagination.totalRows) bản ghi)")
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Spacer()
                                Button("Trang sau") {
                                    viewModel.setExplanationInboxPage(currentPage + 1)
                                }
                                .font(.caption.weight(.bold))
                                .disabled(currentPage >= totalPages || viewModel.isLoadingExplanationInbox)
                            }
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(12)
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

    // MARK: - Attendance Explanation Sheets (Submit, Decision, Date Picker)

    private func explanationSubmitSheet(
        slot: AttendanceExplanationDaySlotEvaluationProjection,
        theme: MiniBiThemePalette
    ) -> some View {
        let targetDate = viewModel.myExplanationsOverview?.dayEvaluation.businessDate ?? viewModel.explanationSelectedDate
        let todayLocal = viewModel.myExplanationsOverview?.todayLocal
        let cleanReasonCount = explanationReasonInput.trimmingCharacters(in: .whitespacesAndNewlines).count

        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Ngày nghiệp vụ: \(targetDate)")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(SemanticColors.textPrimary)
                        if let dayEval = viewModel.myExplanationsOverview?.dayEvaluation {
                            Text("Bảng chấm công: \(dayEval.resolvedProfileVersionLabel())")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        Text("Mốc: \(slot.slotLabel) (\(slot.slotCode) • \(AttendanceExplanationContract.sessionLabel(slot.sessionCode)))")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Khung giờ chuẩn: \(AttendanceExplanationContract.formatGraceWindow(targetTime: slot.targetTime, graceBeforeMinutes: slot.graceBeforeMinutes, graceAfterMinutes: slot.graceAfterMinutes))")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)
                        Text("Trạng thái gốc: \(AttendanceExplanationContract.originalSlotStatusLabel(slot.originalSlotStatus))")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(explanationSlotStatusTextColor(slot.originalSlotStatus))
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(10)

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
                            .font(.caption2)
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
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(Color(hex: 0xF1F5F9))
                        .foregroundColor(SemanticColors.textPrimary)
                        .cornerRadius(10)
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
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .background(theme.primary)
                                .foregroundColor(.white)
                                .cornerRadius(10)
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

        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Nhân sự: \(targetItem.requester.resolvedDisplayName()) • Ngày \(targetItem.businessDate) • \(targetItem.slotLabel) (\(targetItem.resolvedProfileVersionLabel()) • v\(targetItem.rowVersion))")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)

                    Text("Lý do nhân viên: \(targetItem.reason)")
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textPrimary)

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
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(Color(hex: 0xF1F5F9))
                        .foregroundColor(SemanticColors.textPrimary)
                        .cornerRadius(10)
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
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .background(decision == "APPROVE" ? SemanticColors.successText : SemanticColors.dangerText)
                                .foregroundColor(.white)
                                .cornerRadius(10)
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
        .presentationDetents([.height(320), .medium])
    }

    private func explanationDatePickerSheet(theme: MiniBiThemePalette) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                DatePicker(
                    "Chọn ngày nghiệp vụ",
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
                    Text("Xem ngày \(LeaveContract.formatIsoDateVi(chosenIso)) (\(chosenIso))")
                        .font(.subheadline.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(theme.primary)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                Spacer()
            }
            .padding(16)
            .navigationTitle("Chọn ngày nghiệp vụ")
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

    @State private var loadedImage: UIImage?
    @State private var isLoadingRemote: Bool = false

    var body: some View {
        ZStack {
            if let uiImage = resolvedImmediateImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 68, height: 68)
                    .clipped()
            } else if isLoadingRemote {
                ProgressView()
                    .scaleEffect(0.75)
                    .frame(width: 68, height: 68)
            } else {
                VStack(spacing: 3) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(SemanticColors.textMuted)
                    Text("Ảnh CC")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(SemanticColors.textMuted)
                }
                .frame(width: 68, height: 68)
            }
        }
        .frame(width: 68, height: 68)
        .background(Color(hex: 0xE2E8F0))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
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
