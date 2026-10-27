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

public struct AttendanceScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @StateObject private var locationManager = AttendanceLocationManager()

    @State private var capturedPhotoData: Data?
    @State private var frozenCaptureTimestamp: CapturedAttendanceTimestamp?
    @State private var noteInput: String = ""
    @State private var isShowingCamera: Bool = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var exportedReportUrl: URL?

    // Leave Request & Approval Sheet States
    @State private var withdrawTargetRequest: LeaveRequestProjection?
    @State private var withdrawReasonInput: String = ""
    @State private var pendingDecisionItem: PendingLeaveDecisionSheetItem?
    @State private var decisionReasonInput: String = ""
    @State private var localDecisionDialogError: String?
    @State private var expandedInboxRequestIds: Set<String> = []
    @State private var datePickerTargetDay: LeaveDraftDayInput?
    @State private var datePickerSelection: Date = Date()

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
            case .leaveRequest:
                leaveRequestTabContent(theme: theme)
            case .leaveApproval:
                leaveApprovalTabContent(theme: theme)
            }
        }
        .background(theme.background)
        .onAppear {
            locationManager.requestLocationUpdate()
            Task {
                await viewModel.syncAttendanceTimeAnchor()
                await viewModel.refreshMyLeaveSummary(year: viewModel.leaveYear)
            }
        }
        .sheet(isPresented: $isShowingCamera) {
            CameraPhotoCaptureSheet { data in
                capturedPhotoData = data
                frozenCaptureTimestamp = viewModel.timeAnchorManager.resolveCaptureTime()
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
    }

    // MARK: - Top Tab Bar (`Chấm công` | `Xin nghỉ` | `Duyệt nghỉ`)

    private func attendanceTopTabBar(
        tabs: [AttendanceAreaTab],
        selectedTab: AttendanceAreaTab,
        theme: MiniBiThemePalette
    ) -> some View {
        HStack(spacing: 6) {
            ForEach(tabs) { tab in
                let isSelected = tab == selectedTab
                Button {
                    viewModel.selectAttendanceTab(tab)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: tab.systemIconName)
                            .font(.caption.weight(.bold))
                        Text(tab.label)
                            .font(.caption.weight(isSelected ? .bold : .semibold))
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .padding(.horizontal, 8)
                    .background(isSelected ? theme.primary : Color.clear)
                    .foregroundColor(isSelected ? .white : SemanticColors.textSecondary)
                    .cornerRadius(10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Color.white)
        .cornerRadius(14)
        .padding(.horizontal, 14)
        .padding(.top, 10)
        .padding(.bottom, 4)
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

                                Text(String(format: "GPS: %.5f, %.5f · Nguồn giờ: %@", item.lat, item.lng, item.timeSource))
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
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(10)
                        }
                    }
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(14)
            }
            .padding(14)
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
                ForEach(Array(viewModel.leaveDraftDays.enumerated()), id: \.element.id) { index, day in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Ngày nghỉ #\(index + 1) (YYYY-MM-DD)")
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textMuted)
                                TextField(
                                    "YYYY-MM-DD",
                                    text: Binding(
                                        get: { day.leaveDate },
                                        set: { viewModel.updateLeaveDraftDayDate(id: day.id, leaveDate: $0) }
                                    )
                                )
                                .font(.caption.weight(.semibold))
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                            }
                            .padding(8)
                            .background(Color.white)
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )

                            Button {
                                datePickerSelection = parseIsoDate(day.leaveDate) ?? Date()
                                datePickerTargetDay = day
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "calendar")
                                    Text(LeaveContract.formatIsoDateVi(day.leaveDate))
                                }
                                .font(.caption.weight(.semibold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 10)
                                .background(theme.primaryLight)
                                .foregroundColor(theme.primary)
                                .cornerRadius(8)
                            }

                            if day.sessionCode.uppercased() == "FULL" && viewModel.leaveDraftDays.count < 120 {
                                Button {
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

                Button {
                    viewModel.addLeaveDraftDay()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "plus.circle.fill")
                        Text("Thêm dòng ngày/buổi nghỉ")
                    }
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(theme.primaryLight)
                    .foregroundColor(theme.primary)
                    .cornerRadius(8)
                }
                .disabled(viewModel.isMutatingLeave || viewModel.leaveDraftDays.count >= 120)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Lý do xin nghỉ (không bắt buộc)")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.textPrimary)
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
                                            .font(.caption2.monospaced())
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
            VStack(spacing: 14) {
                DatePicker(
                    "Chọn ngày nghỉ",
                    selection: $datePickerSelection,
                    displayedComponents: [.date]
                )
                .datePickerStyle(.graphical)
                .environment(\.locale, Locale(identifier: "vi_VN"))

                Button {
                    let df = DateFormatter()
                    df.locale = Locale(identifier: "en_US_POSIX")
                    df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
                    df.dateFormat = "yyyy-MM-dd"
                    viewModel.updateLeaveDraftDayDate(
                        id: day.id,
                        leaveDate: df.string(from: datePickerSelection)
                    )
                    datePickerTargetDay = nil
                } label: {
                    Text("Chọn ngày này")
                        .font(.subheadline.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(theme.primary)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
            }
            .padding(16)
            .navigationTitle("Chọn ngày xin nghỉ")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        datePickerTargetDay = nil
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Helpers

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
