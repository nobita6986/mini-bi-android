import SwiftUI
import CoreLocation
import PhotosUI

// MARK: - Attendance Screen (Monotonic Server Time Anchor + GPS + Offline Outbox + Report Share)
// Mirrors Android's `ui/screens/AttendanceScreen.kt` 100%

public final class AttendanceLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()

    @Published public var currentLocation: CLLocation?
    @Published public var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published public var locationErrorMessage: String?

    public override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        authorizationStatus = manager.authorizationStatus
    }

    public func requestLocationUpdate() {
        locationErrorMessage = nil
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        case .denied, .restricted:
            locationErrorMessage = "Ứng dụng chưa được cấp quyền Vị trí (GPS). Vui lòng bật trong Cài đặt."
        @unknown default:
            break
        }
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        if authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let latest = locations.last {
            currentLocation = latest
            locationErrorMessage = nil
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        locationErrorMessage = "Đang lấy vị trí GPS (\(error.localizedDescription))..."
    }
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

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme
        let eval = viewModel.attendanceTimeEvaluation

        ScrollView {
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
                                Text(String(format: "Sai số GPS: ±%.1f m", max(0, loc.horizontalAccuracy)))
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textMuted)
                            }
                        } else {
                            Text(locationManager.locationErrorMessage ?? "Đang định vị GPS...")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        Spacer()
                        Button("Lấy lại GPS") {
                            locationManager.requestLocationUpdate()
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(theme.primary)
                    }
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(10)

                    // Photo Capture Row (Freezes timestamp immediately upon capture)
                    HStack(spacing: 10) {
                        Button {
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

                        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                            HStack(spacing: 6) {
                                Image(systemName: "photo")
                                Text("Chọn ảnh")
                            }
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .background(theme.primaryLight)
                            .foregroundColor(theme.primary)
                            .cornerRadius(10)
                        }
                        .onChange(of: selectedPhotoItem) { newItem in
                            guard let newItem else { return }
                            Task {
                                if let data = try? await newItem.loadTransferable(type: Data.self) {
                                    capturedPhotoData = data
                                    frozenCaptureTimestamp = viewModel.timeAnchorManager.resolveCaptureTime()
                                }
                            }
                        }
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
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )

                    Button {
                        guard let photoData = capturedPhotoData else {
                            viewModel.showBanner("Vui lòng chụp ảnh xác thực trước khi chấm công.", isError: true)
                            return
                        }
                        let lat = locationManager.currentLocation?.coordinate.latitude ?? 21.027763
                        let lng = locationManager.currentLocation?.coordinate.longitude ?? 105.834160
                        let acc = locationManager.currentLocation?.horizontalAccuracy
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
                        .background(capturedPhotoData == nil ? Color.gray : SemanticColors.successText)
                        .cornerRadius(12)
                    }
                    .disabled(capturedPhotoData == nil || viewModel.isSubmittingAttendance)
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
        .background(theme.background)
        .onAppear {
            locationManager.requestLocationUpdate()
            Task { await viewModel.syncAttendanceTimeAnchor() }
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
