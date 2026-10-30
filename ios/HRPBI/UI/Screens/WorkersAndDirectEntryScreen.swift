import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

// MARK: - Direct Entry ("Cấp mới") & Workers Management Screens
// Mirrors Android's `ui/screens/WorkersAndBackendPromptScreens.kt` 100%

public struct DirectEntryNewWorkersScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel

    @State private var activeQrScannerRowId: String?
    @State private var showBatchHistorySheet: Bool = false

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme

        VStack(spacing: 0) {
            // Top Action Bar
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Cấp mới hồ sơ NLĐ (\(viewModel.newWorkerDraftRows.count) dòng)")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(SemanticColors.textPrimary)
                    Text("Dữ liệu đang nhập được giữ nguyên khi chuyển menu")
                        .font(.caption2)
                        .foregroundColor(SemanticColors.successText)
                }

                Spacer()

                Button {
                    KeyboardDismissal.dismiss()
                    Task {
                        await viewModel.loadDirectEntryBatchHistory()
                        showBatchHistorySheet = true
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "clock.arrow.circlepath")
                        Text("Lịch sử")
                    }
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(theme.primaryLight)
                    .foregroundColor(theme.primary)
                    .cornerRadius(8)
                }

                Button {
                    KeyboardDismissal.dismiss()
                    viewModel.addNewWorkerDraftRow()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text("Thêm dòng")
                    }
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Color(hex: 0xF1F5F9))
                    .foregroundColor(SemanticColors.textPrimary)
                    .cornerRadius(8)
                }

                Button {
                    KeyboardDismissal.dismiss()
                    Task {
                        await viewModel.submitNewWorkerDraftBatch(confirmedDuplicates: false)
                    }
                } label: {
                    HStack(spacing: 4) {
                        if viewModel.isSubmittingNewWorkers {
                            ProgressView().tint(.white).scaleEffect(0.8)
                        } else {
                            Image(systemName: "checkmark.circle.fill")
                        }
                        Text("Lưu cấp mới")
                    }
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(theme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .disabled(viewModel.isSubmittingNewWorkers)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.white)

            Divider()

            ScrollView {
                VStack(spacing: 14) {
                    ForEach(Array(viewModel.newWorkerDraftRows.enumerated()), id: \.element.rowId) { index, draft in
                        NewWorkerDraftRowCard(
                            index: index,
                            draft: draft,
                            viewModel: viewModel,
                            onScanQrTap: {
                                KeyboardDismissal.dismiss()
                                activeQrScannerRowId = draft.rowId
                            }
                        )
                    }
                }
                .padding(14)
            }
        }
        .background(theme.background)
        .dismissKeyboardOnTapOutside()
        .onAppear {
            if viewModel.directEntryOptions.catalogProjects.isEmpty {
                Task { await viewModel.loadDirectEntryAssigneeOptions() }
            }
        }
        .sheet(isPresented: Binding(
            get: { activeQrScannerRowId != nil },
            set: { if !$0 { activeQrScannerRowId = nil } }
        )) {
            if let rowId = activeQrScannerRowId {
                CccdQrScannerSheet { rawQr, _ in
                    _ = viewModel.applyScannedCccdQr(rowId: rowId, rawQr: rawQr)
                    activeQrScannerRowId = nil
                }
            }
        }
        .sheet(isPresented: $showBatchHistorySheet) {
            DirectEntryBatchHistorySheet(viewModel: viewModel)
        }
        .alert("Phát hiện CCCD đã tồn tại trong hệ thống", isPresented: $viewModel.showDuplicateConfirmDialog) {
            Button("Huỷ kiểm tra", role: .cancel) {
                viewModel.showDuplicateConfirmDialog = false
            }
            Button("Xác nhận vẫn lưu") {
                Task {
                    await viewModel.submitNewWorkerDraftBatch(confirmedDuplicates: true)
                }
            }
        } message: {
            let summary = viewModel.pendingDuplicateConfirmCandidates
                .map { "\($0.hoTen) (\($0.cccd) - \($0.duAn))" }
                .joined(separator: "; ")
            Text("Các hồ sơ trùng CCCD: \(summary). Bạn có chắc chắn muốn tiếp tục cấp mới?")
        }
    }
}

// MARK: - Single Draft Row Card (Mirrors Android's `WorkerMultiEntryCard` 100%)

struct NewWorkerDraftRowCard: View {
    private enum DraftCameraTarget: String, Identifiable {
        case portrait
        case cccdFront
        case cccdBack
        var id: String { rawValue }
    }

    let index: Int
    let draft: NewWorkerEntryDraft
    @ObservedObject var viewModel: MiniBiViewModel
    let onScanQrTap: () -> Void

    @State private var isExpandedExtraDetails: Bool = false
    @State private var portraitPhotoPickerItem: PhotosPickerItem?
    @State private var frontPhotoPickerItem: PhotosPickerItem?
    @State private var backPhotoPickerItem: PhotosPickerItem?
    @State private var activeCameraTarget: DraftCameraTarget?
    @State private var showProjectSearchSheet: Bool = false
    @State private var showRecruiterSearchSheet: Bool = false
    @State private var showBankSearchSheet: Bool = false

    var body: some View {
        let theme = viewModel.selectedTheme
        let options = viewModel.directEntryOptions
        let filteredRecruiters = options.catalogRecruiters.filter {
            $0.providerType.lowercased() == draft.providerType.lowercased()
        }

        VStack(alignment: .leading, spacing: 12) {
            // Card Header
            HStack {
                HStack(spacing: 6) {
                    Text("Người lao động #\(index + 1)")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(theme.primary)
                    if draft.isReadyForSubmit {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundColor(SemanticColors.successText)
                            .font(.caption)
                    }
                }

                Spacer()

                if viewModel.newWorkerDraftRows.count > 1 {
                    Button {
                        viewModel.removeNewWorkerDraftRow(rowId: draft.rowId)
                    } label: {
                        HStack(spacing: 3) {
                            Image(systemName: "trash")
                            Text("Xóa")
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.dangerText)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(SemanticColors.dangerBg)
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }

            // 1. Dự án *
            VStack(alignment: .leading, spacing: 4) {
                Text("Dự án *")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(SemanticColors.textSecondary)
                Button {
                    KeyboardDismissal.dismiss()
                    showProjectSearchSheet = true
                } label: {
                    HStack {
                        Text(draft.duAn.isEmpty ? "— Chọn Dự án —" : draft.duAn)
                            .font(.caption)
                            .foregroundColor(draft.duAn.isEmpty ? SemanticColors.textMuted : SemanticColors.textPrimary)
                            .lineLimit(1)
                        Spacer()
                        Image(systemName: "chevron.down")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)
                    }
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }

            // 2. HRP/Vendor * & Người tuyển / Vendor *
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("HRP/Vendor *")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    Menu {
                        Button("HRP") {
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { row in
                                if row.providerType != "hrp" {
                                    row.providerType = "hrp"
                                    row.recruiterId = ""
                                    row.nguoiTuyen = ""
                                }
                            }
                        }
                        Button("Vendor") {
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { row in
                                if row.providerType != "vendor" {
                                    row.providerType = "vendor"
                                    row.recruiterId = ""
                                    row.nguoiTuyen = ""
                                }
                            }
                        }
                    } label: {
                        HStack {
                            Text(draft.providerType == "vendor" ? "Vendor" : "HRP")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                    }
                }
                .frame(width: 112)

                VStack(alignment: .leading, spacing: 4) {
                    Text(draft.providerType == "vendor" ? "Nhà cung cấp (Vendor) *" : "Người tuyển *")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    Button {
                        KeyboardDismissal.dismiss()
                        showRecruiterSearchSheet = true
                    } label: {
                        HStack {
                            Text(draft.nguoiTuyen.isEmpty ? "— Chọn Người tuyển / Vendor —" : draft.nguoiTuyen)
                                .font(.caption)
                                .foregroundColor(draft.nguoiTuyen.isEmpty ? SemanticColors.textMuted : SemanticColors.textPrimary)
                                .lineLimit(1)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }

            // 3. Loại hình LĐ * & Ngày bắt đầu làm việc *
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Loại hình LĐ *")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    Menu {
                        ForEach(options.catalogLaborTypes) { lt in
                            Button(lt.displayName) {
                                viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { row in
                                    row.laborTypeKey = lt.laborTypeKey
                                    row.nganhNghe = lt.displayName
                                }
                            }
                        }
                    } label: {
                        HStack {
                            Text(draft.nganhNghe.isEmpty ? "Thời vụ" : draft.nganhNghe)
                                .font(.caption)
                                .foregroundColor(SemanticColors.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                    }
                }

                draftTextField(
                    label: "Ngày bắt đầu làm việc *",
                    placeholder: "dd/MM/yyyy",
                    value: draft.ngayVaoLam
                ) { val in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ngayVaoLam = val }
                }
            }

            // 4. Trạng thái làm việc ("Đang làm" | "Đang chờ việc")
            VStack(alignment: .leading, spacing: 4) {
                Text("Trạng thái làm việc")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(SemanticColors.textSecondary)
                Menu {
                    ForEach(["Đang làm", "Đang chờ việc"], id: \.self) { st in
                        Button(st) {
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.trangThai = st }
                        }
                    }
                } label: {
                    HStack {
                        Text(draft.trangThai.isEmpty ? "Đang làm" : draft.trangThai)
                            .font(.caption)
                            .foregroundColor(SemanticColors.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.down")
                            .font(.caption2)
                            .foregroundColor(SemanticColors.textMuted)
                    }
                    .padding(10)
                    .background(Color(hex: 0xF8FAFC))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                    )
                }
            }

            Divider()

            // 5. Thông tin CCCD & cá nhân + Quét QR CCCD
            HStack {
                Text("Thông tin CCCD & cá nhân")
                    .font(.caption.weight(.bold))
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                Button(action: onScanQrTap) {
                    HStack(spacing: 4) {
                        Image(systemName: "qrcode.viewfinder")
                        Text("Quét QR CCCD")
                    }
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(theme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .buttonStyle(.plain)
            }

            draftTextField(
                label: "Số CCCD (12 chữ số) *",
                placeholder: "Nhập đủ 12 chữ số CCCD",
                value: draft.soCccd,
                keyboard: .numberPad
            ) { val in
                let digitsOnly = String(val.filter(\.isNumber).prefix(12))
                viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.soCccd = digitsOnly }
            }

            draftTextField(
                label: "Họ và tên",
                placeholder: "Nguyễn Văn A (có thể bổ sung sau)",
                value: draft.hoTen
            ) { val in
                viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.hoTen = val }
            }

            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Giới tính")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    Menu {
                        Button("— Để trống —") {
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.gioiTinh = "" }
                        }
                        Button("Nam") {
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.gioiTinh = "Nam" }
                        }
                        Button("Nữ") {
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.gioiTinh = "Nữ" }
                        }
                    } label: {
                        HStack {
                            Text(draft.gioiTinh.isEmpty ? "— Để trống —" : draft.gioiTinh)
                                .font(.caption)
                                .foregroundColor(draft.gioiTinh.isEmpty ? SemanticColors.textMuted : SemanticColors.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
                        .padding(10)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                        )
                    }
                }

                draftTextField(
                    label: "Số điện thoại",
                    placeholder: "09...",
                    value: draft.sdt,
                    keyboard: .phonePad
                ) { val in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.sdt = val }
                }
            }

            Divider()

            // 6. Ảnh & giấy tờ đính kèm (Portrait + CCCD Front + CCCD Back + Contract)
            Text("Ảnh & giấy tờ đính kèm")
                .font(.caption.weight(.bold))
                .foregroundColor(SemanticColors.textPrimary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                // 1. Ảnh chân dung (Chụp Camera + Chọn Thư viện + Nút bỏ chọn)
                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: draft.portraitFile != nil ? "checkmark.circle.fill" : "person.crop.rectangle")
                        Text(draft.portraitFile != nil ? "Đã có chân dung" : "Ảnh chân dung")
                            .lineLimit(1)
                        if draft.portraitFile != nil {
                            Spacer(minLength: 2)
                            Button {
                                portraitPhotoPickerItem = nil
                                viewModel.removeSingleAttachmentFromDraftRow(
                                    rowId: draft.rowId,
                                    docType: DirectEntryContract.docTypeWorkerPortrait
                                )
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(draft.portraitFile != nil ? SemanticColors.successText : SemanticColors.textPrimary)

                    HStack(spacing: 4) {
                        Button {
                            activeCameraTarget = .portrait
                        } label: {
                            HStack(spacing: 2) {
                                Image(systemName: "camera.fill")
                                Text("Chụp")
                            }
                            .font(.system(size: 10, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(Color.white)
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)

                        PhotosPicker(selection: $portraitPhotoPickerItem, matching: .images) {
                            HStack(spacing: 2) {
                                Image(systemName: "photo")
                                Text("Thư viện")
                            }
                            .font(.system(size: 10, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(Color(hex: 0xEBF3FB))
                            .foregroundColor(Color(hex: 0x1E6BB8))
                            .cornerRadius(6)
                        }
                    }
                }
                .padding(7)
                .background(draft.portraitFile != nil ? SemanticColors.successBg : Color(hex: 0xF1F5F9))
                .cornerRadius(8)
                .onChange(of: portraitPhotoPickerItem) { item in
                    guard let item else { return }
                    Task {
                        if let data = try? await item.loadTransferable(type: Data.self) {
                            let att = LocalAttachmentItem(
                                fileName: "chan-dung-\(Int(Date().timeIntervalSince1970)).jpg",
                                mimeType: "image/jpeg",
                                data: data
                            )
                            viewModel.addAttachmentToDraftRow(
                                rowId: draft.rowId,
                                docType: DirectEntryContract.docTypeWorkerPortrait,
                                attachment: att
                            )
                        }
                        portraitPhotoPickerItem = nil
                    }
                }

                // 2. CCCD mặt trước (Chụp Camera + Chọn Thư viện + Nút bỏ chọn)
                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: draft.cccdFrontFile != nil ? "checkmark.circle.fill" : "creditcard")
                        Text(draft.cccdFrontFile != nil ? "Đã có CCCD trước" : "CCCD mặt trước")
                            .lineLimit(1)
                        if draft.cccdFrontFile != nil {
                            Spacer(minLength: 2)
                            Button {
                                frontPhotoPickerItem = nil
                                viewModel.removeSingleAttachmentFromDraftRow(
                                    rowId: draft.rowId,
                                    docType: DirectEntryContract.docTypeCccdFront
                                )
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(draft.cccdFrontFile != nil ? SemanticColors.successText : SemanticColors.textPrimary)

                    HStack(spacing: 4) {
                        Button {
                            activeCameraTarget = .cccdFront
                        } label: {
                            HStack(spacing: 2) {
                                Image(systemName: "camera.fill")
                                Text("Chụp")
                            }
                            .font(.system(size: 10, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(Color.white)
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)

                        PhotosPicker(selection: $frontPhotoPickerItem, matching: .images) {
                            HStack(spacing: 2) {
                                Image(systemName: "photo")
                                Text("Thư viện")
                            }
                            .font(.system(size: 10, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(Color(hex: 0xEBF3FB))
                            .foregroundColor(Color(hex: 0x1E6BB8))
                            .cornerRadius(6)
                        }
                    }
                }
                .padding(7)
                .background(draft.cccdFrontFile != nil ? SemanticColors.successBg : Color(hex: 0xF1F5F9))
                .cornerRadius(8)
                .onChange(of: frontPhotoPickerItem) { item in
                    guard let item else { return }
                    Task {
                        if let data = try? await item.loadTransferable(type: Data.self) {
                            let att = LocalAttachmentItem(
                                fileName: "cccd-truoc-\(Int(Date().timeIntervalSince1970)).jpg",
                                mimeType: "image/jpeg",
                                data: data
                            )
                            viewModel.addAttachmentToDraftRow(
                                rowId: draft.rowId,
                                docType: DirectEntryContract.docTypeCccdFront,
                                attachment: att
                            )
                            if draft.soCccd.isEmpty, let qrRaw = CccdQrScannerSheet.detectQrFromImageData(data) {
                                _ = viewModel.applyScannedCccdQr(rowId: draft.rowId, rawQr: qrRaw)
                            }
                        }
                        frontPhotoPickerItem = nil
                    }
                }

                // 3. CCCD mặt sau (Chụp Camera + Chọn Thư viện + Nút bỏ chọn)
                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: draft.cccdBackFile != nil ? "checkmark.circle.fill" : "creditcard.fill")
                        Text(draft.cccdBackFile != nil ? "Đã có CCCD sau" : "CCCD mặt sau")
                            .lineLimit(1)
                        if draft.cccdBackFile != nil {
                            Spacer(minLength: 2)
                            Button {
                                backPhotoPickerItem = nil
                                viewModel.removeSingleAttachmentFromDraftRow(
                                    rowId: draft.rowId,
                                    docType: DirectEntryContract.docTypeCccdBack
                                )
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(draft.cccdBackFile != nil ? SemanticColors.successText : SemanticColors.textPrimary)

                    HStack(spacing: 4) {
                        Button {
                            activeCameraTarget = .cccdBack
                        } label: {
                            HStack(spacing: 2) {
                                Image(systemName: "camera.fill")
                                Text("Chụp")
                            }
                            .font(.system(size: 10, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(Color.white)
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)

                        PhotosPicker(selection: $backPhotoPickerItem, matching: .images) {
                            HStack(spacing: 2) {
                                Image(systemName: "photo")
                                Text("Thư viện")
                            }
                            .font(.system(size: 10, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .background(Color(hex: 0xEBF3FB))
                            .foregroundColor(Color(hex: 0x1E6BB8))
                            .cornerRadius(6)
                        }
                    }
                }
                .padding(7)
                .background(draft.cccdBackFile != nil ? SemanticColors.successBg : Color(hex: 0xF1F5F9))
                .cornerRadius(8)
                .onChange(of: backPhotoPickerItem) { item in
                    guard let item else { return }
                    Task {
                        if let data = try? await item.loadTransferable(type: Data.self) {
                            let att = LocalAttachmentItem(
                                fileName: "cccd-sau-\(Int(Date().timeIntervalSince1970)).jpg",
                                mimeType: "image/jpeg",
                                data: data
                            )
                            viewModel.addAttachmentToDraftRow(
                                rowId: draft.rowId,
                                docType: DirectEntryContract.docTypeCccdBack,
                                attachment: att
                            )
                        }
                        backPhotoPickerItem = nil
                    }
                }
            }

            // Danh sách ảnh Chân dung / CCCD đã chọn (kèm nút bỏ chọn giống phần Hồ sơ)
            if draft.portraitFile != nil || draft.cccdFrontFile != nil || draft.cccdBackFile != nil {
                VStack(spacing: 6) {
                    if let portrait = draft.portraitFile {
                        singleAttachmentSelectedRow(
                            label: "Ảnh chân dung",
                            attachment: portrait,
                            onRemove: {
                                portraitPhotoPickerItem = nil
                                viewModel.removeSingleAttachmentFromDraftRow(
                                    rowId: draft.rowId,
                                    docType: DirectEntryContract.docTypeWorkerPortrait
                                )
                            }
                        )
                    }
                    if let front = draft.cccdFrontFile {
                        singleAttachmentSelectedRow(
                            label: "CCCD mặt trước",
                            attachment: front,
                            onRemove: {
                                frontPhotoPickerItem = nil
                                viewModel.removeSingleAttachmentFromDraftRow(
                                    rowId: draft.rowId,
                                    docType: DirectEntryContract.docTypeCccdFront
                                )
                            }
                        )
                    }
                    if let back = draft.cccdBackFile {
                        singleAttachmentSelectedRow(
                            label: "CCCD mặt sau",
                            attachment: back,
                            onRemove: {
                                backPhotoPickerItem = nil
                                viewModel.removeSingleAttachmentFromDraftRow(
                                    rowId: draft.rowId,
                                    docType: DirectEntryContract.docTypeCccdBack
                                )
                            }
                        )
                    }
                }
            }

            ContractMultiAttachmentPickerView(
                title: "Hợp đồng lao động / Hồ sơ",
                attachments: draft.contractFiles,
                onAddAttachments: { newItems in
                    for att in newItems {
                        viewModel.addAttachmentToDraftRow(
                            rowId: draft.rowId,
                            docType: DirectEntryContract.docTypeEmploymentContract,
                            attachment: att
                        )
                    }
                },
                onRemoveAttachment: { attId in
                    viewModel.removeContractAttachmentFromDraftRow(rowId: draft.rowId, attachmentId: attId)
                }
            )

            Divider()

            // 7. Collapsible Extra Details (matching Android's "Thông tin thêm: địa chỉ, ngày sinh, ngày cấp CCCD, tài khoản ngân hàng và ghi chú")
            DisclosureGroup(isExpanded: $isExpandedExtraDetails) {
                VStack(spacing: 10) {
                    draftTextField(
                        label: "Địa chỉ thường trú / quê quán",
                        placeholder: "Nhập địa chỉ...",
                        value: draft.queQuan
                    ) { val in
                        viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.queQuan = val }
                    }

                    HStack(spacing: 10) {
                        draftTextField(label: "Ngày sinh", placeholder: "dd/MM/yyyy", value: draft.ngaySinh) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ngaySinh = val }
                        }
                        draftTextField(label: "Ngày cấp CCCD", placeholder: "dd/MM/yyyy", value: draft.ngayCap) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ngayCap = val }
                        }
                    }

                    HStack(spacing: 10) {
                        draftTextField(label: "STK (Số tài khoản)", placeholder: "Nhập STK", value: draft.soTaiKhoan) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.soTaiKhoan = val }
                        }
                        draftTextField(label: "Tên chủ tài khoản", placeholder: "NGUYEN VAN A", value: draft.bankAccountHolder) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.bankAccountHolder = val }
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Ngân hàng")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(SemanticColors.textSecondary)
                        Button {
                            KeyboardDismissal.dismiss()
                            showBankSearchSheet = true
                        } label: {
                            HStack {
                                Text(draft.nganHang.isEmpty ? "— Chọn Ngân hàng —" : draft.nganHang)
                                    .font(.caption)
                                    .foregroundColor(draft.nganHang.isEmpty ? SemanticColors.textMuted : SemanticColors.textPrimary)
                                Spacer()
                                Image(systemName: "chevron.down")
                                    .font(.caption2)
                                    .foregroundColor(SemanticColors.textMuted)
                            }
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }

                    draftTextField(label: "Ghi chú", placeholder: "Ghi chú thêm...", value: draft.ghiChu) { val in
                        viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ghiChu = val }
                    }
                }
                .padding(.top, 8)
            } label: {
                Text("Thông tin thêm: địa chỉ, ngày sinh, ngày cấp CCCD, tài khoản ngân hàng và ghi chú")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(theme.primary)
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
        )
        .sheet(isPresented: $showProjectSearchSheet) {
            DirectEntrySearchablePickerSheet(
                title: "Chọn Dự án",
                placeholder: "Tìm tên dự án...",
                items: options.catalogProjects.isEmpty
                    ? options.projects.map { (id: $0, label: $0) }
                    : options.catalogProjects.map { (id: $0.projectId, label: $0.displayName) },
                allowCustomInput: true,
                onSelect: { id, label in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { row in
                        row.projectId = id
                        row.duAn = label
                    }
                }
            )
        }
        .sheet(isPresented: $showRecruiterSearchSheet) {
            DirectEntrySearchablePickerSheet(
                title: draft.providerType == "vendor" ? "Chọn Nhà cung cấp (Vendor)" : "Chọn Người tuyển (HRP)",
                placeholder: "Tìm tên hoặc mã nhân sự...",
                items: filteredRecruiters.isEmpty
                    ? options.catalogRecruiters.map { (id: $0.recruiterId, label: $0.label) }
                    : filteredRecruiters.map { (id: $0.recruiterId, label: $0.label) },
                allowCustomInput: true,
                onSelect: { id, label in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { row in
                        row.recruiterId = id
                        row.nguoiTuyen = label
                    }
                }
            )
        }
        .sheet(isPresented: $showBankSearchSheet) {
            DirectEntrySearchablePickerSheet(
                title: "Chọn Ngân hàng",
                placeholder: "Tìm tên ngân hàng (VCB, MB, Techcombank...)",
                items: options.catalogBanks.isEmpty
                    ? options.banks.map { (id: $0, label: $0) }
                    : options.catalogBanks.map { (id: $0.bankId, label: $0.displayName) },
                allowCustomInput: true,
                onSelect: { _, label in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { row in
                        row.nganHang = label
                    }
                }
            )
        }
        .sheet(item: $activeCameraTarget) { target in
            CameraPhotoCaptureSheet { capturedData in
                let ts = Int(Date().timeIntervalSince1970)
                switch target {
                case .portrait:
                    let att = LocalAttachmentItem(
                        fileName: "chup-chan-dung-\(ts).jpg",
                        mimeType: "image/jpeg",
                        data: capturedData
                    )
                    viewModel.addAttachmentToDraftRow(
                        rowId: draft.rowId,
                        docType: DirectEntryContract.docTypeWorkerPortrait,
                        attachment: att
                    )
                case .cccdFront:
                    let att = LocalAttachmentItem(
                        fileName: "chup-cccd-truoc-\(ts).jpg",
                        mimeType: "image/jpeg",
                        data: capturedData
                    )
                    viewModel.addAttachmentToDraftRow(
                        rowId: draft.rowId,
                        docType: DirectEntryContract.docTypeCccdFront,
                        attachment: att
                    )
                    if draft.soCccd.isEmpty, let qrRaw = CccdQrScannerSheet.detectQrFromImageData(capturedData) {
                        _ = viewModel.applyScannedCccdQr(rowId: draft.rowId, rawQr: qrRaw)
                    }
                case .cccdBack:
                    let att = LocalAttachmentItem(
                        fileName: "chup-cccd-sau-\(ts).jpg",
                        mimeType: "image/jpeg",
                        data: capturedData
                    )
                    viewModel.addAttachmentToDraftRow(
                        rowId: draft.rowId,
                        docType: DirectEntryContract.docTypeCccdBack,
                        attachment: att
                    )
                }
            }
        }
    }

    private func singleAttachmentSelectedRow(
        label: String,
        attachment: LocalAttachmentItem,
        onRemove: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "photo.fill")
                .foregroundColor(Color(hex: 0x1E6BB8))
                .font(.caption)
            Text("\(label): \(attachment.fileName)")
                .font(.caption2.weight(.medium))
                .foregroundColor(SemanticColors.textPrimary)
                .lineLimit(1)
            Spacer()
            Text(String(format: "%.0f KB", Double(attachment.fileSizeBytes) / 1024.0))
                .font(.caption2)
                .foregroundColor(SemanticColors.textMuted)
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(SemanticColors.dangerText)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color(hex: 0xF8FAFC))
        .cornerRadius(6)
    }

    private func draftTextField(
        label: String,
        placeholder: String,
        value: String,
        keyboard: UIKeyboardType = .default,
        onChange: @escaping (String) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundColor(SemanticColors.textSecondary)
            TextField(
                placeholder,
                text: Binding(get: { value }, set: { onChange($0) })
            )
            .keyboardType(keyboard)
            .submitLabel(.done)
            .font(.caption)
            .foregroundColor(SemanticColors.textPrimary)
            .padding(9)
            .background(Color(hex: 0xF8FAFC))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
            )
        }
    }
}

struct DirectEntrySearchablePickerSheet: View {
    let title: String
    let placeholder: String
    let items: [(id: String, label: String)]
    let allowCustomInput: Bool
    let onSelect: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var searchText: String = ""

    var body: some View {
        let filtered = searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? items
            : items.filter {
                $0.label.localizedCaseInsensitiveContains(searchText) ||
                $0.id.localizedCaseInsensitiveContains(searchText)
            }

        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(SemanticColors.textMuted)
                    TextField(placeholder, text: $searchText)
                        .submitLabel(.search)
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textPrimary)
                }
                .padding(10)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(10)
                .padding(12)

                List {
                    if allowCustomInput && !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && filtered.isEmpty {
                        Button {
                            KeyboardDismissal.dismiss()
                            let val = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                            onSelect(val, val)
                            dismiss()
                        } label: {
                            Text("Sử dụng giá trị: \"\(searchText.trimmingCharacters(in: .whitespacesAndNewlines))\"")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }
                    }

                    ForEach(Array(filtered.enumerated()), id: \.offset) { _, item in
                        Button {
                            KeyboardDismissal.dismiss()
                            onSelect(item.id, item.label)
                            dismiss()
                        } label: {
                            HStack {
                                Text(item.label)
                                    .font(.subheadline)
                                    .foregroundColor(SemanticColors.textPrimary)
                                Spacer()
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
            .dismissKeyboardOnTapOutside()
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        KeyboardDismissal.dismiss()
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Reusable Multi-Image + PDF Contract Picker (Camera + Photo Library + Files -> 1 PDF)

public struct ContractMultiAttachmentPickerView: View {
    public let title: String
    public let attachments: [LocalAttachmentItem]
    public let onAddAttachments: ([LocalAttachmentItem]) -> Void
    public let onRemoveAttachment: (String) -> Void

    @State private var selectedPhotoItems: [PhotosPickerItem] = []
    @State private var isShowingCamera: Bool = false
    @State private var isShowingFileImporter: Bool = false

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.caption.weight(.bold))
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                if attachments.count >= 2 {
                    Text("Ghép \(attachments.count) ảnh → 1 PDF (≤10MB)")
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(SemanticColors.infoBg)
                        .foregroundColor(SemanticColors.infoText)
                        .clipShape(Capsule())
                } else if attachments.count == 1 {
                    Text("1 tệp đính kèm")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.successText)
                }
            }

            HStack(spacing: 8) {
                PhotosPicker(
                    selection: $selectedPhotoItems,
                    maxSelectionCount: 20,
                    matching: .images
                ) {
                    HStack(spacing: 4) {
                        Image(systemName: "photo.stack")
                        Text("Chọn nhiều ảnh")
                    }
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0xEBF3FB))
                    .foregroundColor(Color(hex: 0x1E6BB8))
                    .cornerRadius(8)
                }
                .onChange(of: selectedPhotoItems) { newItems in
                    guard !newItems.isEmpty else { return }
                    Task {
                        var loaded: [LocalAttachmentItem] = []
                        for (idx, item) in newItems.enumerated() {
                            if let data = try? await item.loadTransferable(type: Data.self) {
                                loaded.append(
                                    LocalAttachmentItem(
                                        fileName: "anh-hop-dong-\(Int(Date().timeIntervalSince1970))-\(idx + 1).jpg",
                                        mimeType: "image/jpeg",
                                        data: data
                                    )
                                )
                            }
                        }
                        if !loaded.isEmpty {
                            onAddAttachments(loaded)
                        }
                        selectedPhotoItems = []
                    }
                }

                Button {
                    isShowingCamera = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "camera.fill")
                        Text("Chụp thêm")
                    }
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0xF1F5F9))
                    .foregroundColor(SemanticColors.textPrimary)
                    .cornerRadius(8)
                }
                .buttonStyle(.plain)

                Button {
                    isShowingFileImporter = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "doc.badge.plus")
                        Text("Tệp PDF/Ảnh")
                    }
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0xF1F5F9))
                    .foregroundColor(SemanticColors.textPrimary)
                    .cornerRadius(8)
                }
                .buttonStyle(.plain)
            }

            // Selected Attachments List with Remove button
            if !attachments.isEmpty {
                VStack(spacing: 6) {
                    ForEach(Array(attachments.enumerated()), id: \.element.id) { idx, att in
                        HStack(spacing: 8) {
                            Image(systemName: att.isPdf ? "doc.richtext.fill" : "photo.fill")
                                .foregroundColor(att.isPdf ? SemanticColors.dangerText : Color(hex: 0x1E6BB8))
                                .font(.caption)
                            Text("Trang \(idx + 1): \(att.fileName)")
                                .font(.caption2.weight(.medium))
                                .lineLimit(1)
                            Spacer()
                            Text(String(format: "%.0f KB", Double(att.fileSizeBytes) / 1024.0))
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                            Button {
                                onRemoveAttachment(att.id)
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .background(Color(hex: 0xF8FAFC))
                        .cornerRadius(6)
                    }
                }
            }
        }
        .sheet(isPresented: $isShowingCamera) {
            CameraPhotoCaptureSheet { capturedData in
                let att = LocalAttachmentItem(
                    fileName: "chup-hop-dong-\(Int(Date().timeIntervalSince1970)).jpg",
                    mimeType: "image/jpeg",
                    data: capturedData
                )
                onAddAttachments([att])
            }
        }
        .fileImporter(
            isPresented: $isShowingFileImporter,
            allowedContentTypes: [.pdf, .image],
            allowsMultipleSelection: true
        ) { result in
            guard case .success(let urls) = result else { return }
            var loaded: [LocalAttachmentItem] = []
            for url in urls {
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url) {
                    let ext = url.pathExtension.lowercased()
                    let mime = ext == "pdf" ? "application/pdf" : (ext == "png" ? "image/png" : "image/jpeg")
                    loaded.append(
                        LocalAttachmentItem(
                            fileName: url.lastPathComponent,
                            mimeType: mime,
                            data: data
                        )
                    )
                }
            }
            if !loaded.isEmpty {
                onAddAttachments(loaded)
            }
        }
    }
}

// MARK: - Camera Photo Capture Sheet

public struct CameraPhotoCaptureSheet: UIViewControllerRepresentable {
    public let onPhotoCaptured: (Data) -> Void
    @Environment(\.dismiss) private var dismiss

    public init(onPhotoCaptured: @escaping (Data) -> Void) {
        self.onPhotoCaptured = onPhotoCaptured
    }

    public func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        if UIImagePickerController.isSourceTypeAvailable(.camera) {
            picker.sourceType = .camera
        } else {
            picker.sourceType = .photoLibrary
        }
        picker.delegate = context.coordinator
        return picker
    }

    public func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    public func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    public final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CameraPhotoCaptureSheet
        init(parent: CameraPhotoCaptureSheet) { self.parent = parent }

        public func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage,
               let data = image.jpegData(compressionQuality: 0.85) {
                parent.onPhotoCaptured(data)
            }
            parent.dismiss()
        }

        public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

// MARK: - Direct Entry Batch History Sheet

struct DirectEntryBatchHistorySheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if viewModel.directEntryHistoryBatches.isEmpty {
                    Text("Chưa có lịch sử lô cấp mới.")
                        .foregroundColor(SemanticColors.textSecondary)
                } else {
                    ForEach(viewModel.directEntryHistoryBatches) { batch in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("Lô #\(batch.batchId)")
                                    .font(.subheadline.weight(.bold))
                                Spacer()
                                Text(batch.status)
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.successText)
                            }
                            Text("Tổng dòng: \(batch.totalRows) · Đã duyệt: \(batch.approvedRows) · Chờ: \(batch.pendingRows)")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                            Text("Thời gian: \(WorkReportsContract.formatIsoDateTimeVi(batch.createdAt))")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle("Lịch sử lô cấp mới")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Workers Management Screen ("Nhân sự")

public struct WorkersScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @State private var exportedShareUrl: URL?

    private let scopes: [(String, String)] = [
        ("all", "Tất cả"),
        ("managed", "Đang quản lý"),
        ("recruited", "Đã tuyển"),
        ("team", "Theo nhóm"),
        ("uploader", "Đã tải lên")
    ]

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme

        VStack(spacing: 0) {
            // Scope Chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(scopes, id: \.0) { code, label in
                        let selected = viewModel.selectedWorkerScope == code
                        Button {
                            viewModel.selectedWorkerScope = code
                            Task { await viewModel.loadWorkers(resetPage: true) }
                        } label: {
                            Text(label)
                                .font(.caption.weight(selected ? .bold : .medium))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(selected ? theme.primary : Color(hex: 0xF1F5F9))
                                .foregroundColor(selected ? .white : SemanticColors.textSecondary)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color.white)

            // QLDA Salary Advance Entry Button (Right below scope chips, right above search input)
            if viewModel.canShowPmSalaryAdvanceButton {
                HStack {
                    Button {
                        KeyboardDismissal.dismiss()
                        Task { await viewModel.openSalaryAdvanceSheet() }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "banknote.fill")
                            Text("Ứng lương")
                                .font(.caption.weight(.bold))
                            Spacer()
                            Text("Tạo đề nghị ứng lương cho NLĐ dự án quản lý")
                                .font(.caption2)
                                .opacity(0.9)
                            Image(systemName: "chevron.right")
                                .font(.caption2.weight(.bold))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        .background(theme.primary)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 8)
                .background(Color.white)
            }

            // Search & Export Bar
            HStack(spacing: 8) {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(SemanticColors.textMuted)
                    TextField("Tìm họ tên, mã NLĐ, CCCD, SĐT...", text: $viewModel.workerSearchQuery)
                        .submitLabel(.search)
                        .font(.caption)
                        .foregroundColor(SemanticColors.textPrimary)
                        .onSubmit {
                            KeyboardDismissal.dismiss()
                            Task { await viewModel.loadWorkers(resetPage: true) }
                        }
                }
                .padding(9)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(8)

                Button("Tìm") {
                    KeyboardDismissal.dismiss()
                    Task { await viewModel.loadWorkers(resetPage: true) }
                }
                .font(.caption.weight(.bold))
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(theme.primary)
                .foregroundColor(.white)
                .cornerRadius(8)

                Button {
                    Task {
                        let res = await viewModel.repository.exportWorkersExcel(
                            scope: viewModel.selectedWorkerScope,
                            search: viewModel.workerSearchQuery,
                            project: viewModel.workerProjectFilter
                        )
                        if case .success(let url, _) = res {
                            exportedShareUrl = url
                        } else if case .failure(let msg, _, _, _) = res {
                            viewModel.showBanner(msg, isError: true)
                        }
                    }
                } label: {
                    Image(systemName: "square.and.arrow.up")
                        .font(.caption.weight(.bold))
                        .padding(9)
                        .background(theme.primaryLight)
                        .foregroundColor(theme.primary)
                        .cornerRadius(8)
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 10)
            .background(Color.white)

            Divider()

            // Workers List
            ScrollView {
                VStack(spacing: 10) {
                    if viewModel.isLoadingWorkers {
                        ProgressView("Đang tải danh sách nhân sự...")
                            .padding(.vertical, 20)
                    } else if viewModel.workers.isEmpty {
                        Text("Không tìm thấy hồ sơ người lao động phù hợp.")
                            .font(.subheadline)
                            .foregroundColor(SemanticColors.textSecondary)
                            .padding(.vertical, 32)
                    } else {
                        ForEach(viewModel.workers) { worker in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(worker.displayEmployeeCode.isEmpty ? worker.hoTen : "\(worker.hoTen) (\(worker.displayEmployeeCode))")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                    Spacer()
                                    Text(worker.trangThai)
                                        .font(.caption2.weight(.bold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(worker.trangThai.contains("Đang") ? SemanticColors.successBg : SemanticColors.warningBg)
                                        .foregroundColor(worker.trangThai.contains("Đang") ? SemanticColors.successText : SemanticColors.warningText)
                                        .clipShape(Capsule())
                                }

                                Text("Dự án: \(worker.duAn) · Quản lý: \(worker.quanLy) · Tuyển: \(worker.nguoiTuyen)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)

                                HStack(spacing: 12) {
                                    Text("CCCD: \(worker.soCccd.isEmpty ? "—" : worker.soCccd)")
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textMuted)
                                    Text("SĐT: \(worker.sdt.isEmpty ? "—" : worker.sdt)")
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textMuted)
                                    Spacer()
                                    if worker.contractFileUrl != nil {
                                        Label("Có HĐ", systemImage: "doc.text.fill")
                                            .font(.caption2.weight(.semibold))
                                            .foregroundColor(SemanticColors.successText)
                                    }
                                }

                                Divider()

                                HStack(spacing: 8) {
                                    if worker.canView {
                                        Button {
                                            KeyboardDismissal.dismiss()
                                            viewModel.selectedWorkerDetail = worker
                                        } label: {
                                            HStack(spacing: 4) {
                                                Image(systemName: "eye")
                                                Text("Xem chi tiết")
                                            }
                                            .font(.caption.weight(.semibold))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 7)
                                            .background(Color(hex: 0xF1F5F9))
                                            .foregroundColor(SemanticColors.textPrimary)
                                            .cornerRadius(8)
                                        }
                                        .buttonStyle(.plain)
                                    }

                                    if worker.canDirectEdit {
                                        Button {
                                            KeyboardDismissal.dismiss()
                                            Task { await viewModel.openDirectEdit(worker) }
                                        } label: {
                                            HStack(spacing: 4) {
                                                Image(systemName: "square.and.pencil")
                                                Text("Sửa trực tiếp")
                                            }
                                            .font(.caption.weight(.bold))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 7)
                                            .background(theme.primary)
                                            .foregroundColor(.white)
                                            .cornerRadius(8)
                                        }
                                        .buttonStyle(.plain)
                                    }

                                    Spacer()
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)
                        }
                    }
                }
                .padding(14)
            }
            .refreshable {
                await viewModel.loadWorkers(resetPage: false)
            }
        }
        .background(theme.background)
        .dismissKeyboardOnTapOutside()
        .onAppear {
            if viewModel.workers.isEmpty {
                Task { await viewModel.loadWorkers(resetPage: true) }
            }
            if viewModel.salaryAdvanceContext == nil {
                Task { await viewModel.loadSalaryAdvanceContext() }
            }
        }
        .sheet(item: $viewModel.selectedWorkerDetail) { worker in
            WorkerDetailSheet(viewModel: viewModel, worker: worker)
        }
        .sheet(item: $viewModel.directEditWorkerTarget) { worker in
            WorkerDirectEditSheet(viewModel: viewModel, worker: worker)
        }
        .sheet(isPresented: $viewModel.salaryAdvanceSheetOpen) {
            SalaryAdvanceCreateRequestSheet(viewModel: viewModel)
        }
        .sheet(isPresented: Binding(
            get: { exportedShareUrl != nil },
            set: { if !$0 { exportedShareUrl = nil } }
        )) {
            if let url = exportedShareUrl {
                ActivityShareSheet(activityItems: [url])
            }
        }
    }
}

// MARK: - Worker Detail Sheet (Strictly Read-Only — "Xem chi tiết")

struct WorkerDetailSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    let worker: WorkerItem
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(worker.displayEmployeeCode.isEmpty ? worker.hoTen : "\(worker.hoTen) (\(worker.displayEmployeeCode))")
                            .font(.title3.weight(.bold))
                            .foregroundColor(SemanticColors.textPrimary)
                        Text("Dự án: \(worker.duAn) · Trạng thái: \(worker.trangThai)")
                            .font(.subheadline)
                            .foregroundColor(SemanticColors.textSecondary)
                        Divider()
                        Group {
                            detailRow("Mã NLĐ", worker.displayEmployeeCode)
                            detailRow("Họ và tên", worker.hoTen)
                            detailRow("Số CCCD", worker.soCccd)
                            detailRow("Ngày sinh", worker.ngaySinh)
                            detailRow("Giới tính", worker.gioiTinh)
                            detailRow("Ngày cấp CCCD", worker.ngayCapCccd)
                            detailRow("Nơi cấp CCCD", worker.noiCapCccd)
                            detailRow("Địa chỉ / Quê quán", worker.queQuan)
                            detailRow("Số điện thoại", worker.sdt)
                        }
                        Divider()
                        Group {
                            detailRow("Dự án", worker.duAn)
                            detailRow("Loại hình lao động", worker.nganhNghe)
                            detailRow("Quản lý", worker.quanLy)
                            detailRow("Người tuyển", worker.nguoiTuyen)
                            detailRow("Ngày vào làm", worker.ngayVaoLam)
                            detailRow("Trạng thái", worker.trangThai)
                            if !worker.employmentEffectiveDate.isEmpty {
                                detailRow("Ngày hiệu lực", worker.employmentEffectiveDate)
                            }
                        }
                        Divider()
                        Group {
                            detailRow("Chủ tài khoản", worker.chuTaiKhoan)
                            detailRow("Số tài khoản", worker.soTaiKhoan)
                            detailRow("Ngân hàng", worker.nganHang)
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)
                }
                .padding(14)
            }
            .background(theme.background)
            .navigationTitle("Chi tiết hồ sơ NLĐ")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                if worker.soCccd.isEmpty && !worker.maNld.isEmpty {
                    Task {
                        let detailRes = await viewModel.repository.fetchWorkerDetail(maNld: worker.maNld)
                        if case .success(let detailed, _) = detailRes {
                            viewModel.selectedWorkerDetail = detailed
                        }
                    }
                }
            }
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundColor(SemanticColors.textMuted)
            Spacer()
            Text(value.isEmpty ? "—" : value)
                .font(.caption.weight(.medium))
                .foregroundColor(SemanticColors.textPrimary)
                .multilineTextAlignment(.trailing)
        }
    }
}

// MARK: - Worker Direct Edit Sheet ("Sửa trực tiếp hồ sơ NLĐ")

struct WorkerDirectEditSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    let worker: WorkerItem
    @Environment(\.dismiss) private var dismiss

    @State private var form: DirectEditFormInput = DirectEditFormInput()
    @State private var syncedEntryVersion: Int = -1
    @State private var stagedFiles: [String: [LocalAttachmentItem]] = [:]

    @State private var showProjectPicker: Bool = false
    @State private var showRecruiterPicker: Bool = false
    @State private var showLaborTypePicker: Bool = false
    @State private var showBankPicker: Bool = false

    @State private var portraitPhotoItem: PhotosPickerItem?
    @State private var cccdFrontPhotoItem: PhotosPickerItem?
    @State private var cccdBackPhotoItem: PhotosPickerItem?
    @State private var activeCameraDocType: String?

    var body: some View {
        let theme = viewModel.selectedTheme
        let detail = viewModel.directEditBaselineDetail

        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        // Header subtitle
                        VStack(alignment: .leading, spacing: 4) {
                            let code = detail?.maNld ?? worker.displayEmployeeCode
                            let ver = detail?.entryVersion ?? worker.entryVersion
                            Text("\(detail?.hoTen ?? worker.hoTen)\(code.isEmpty ? "" : " (\(code))") · v\(ver)")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)
                        }

                        // Error Banner + Detailed Per-Field Error Breakdown
                        if let errorMsg = viewModel.directEditErrorMessage, !errorMsg.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(errorMsg)
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(SemanticColors.dangerText)
                                if !viewModel.directEditFieldErrors.isEmpty {
                                    ForEach(Array(viewModel.directEditFieldErrors.keys.sorted()), id: \.self) { key in
                                        if let fieldErr = viewModel.directEditFieldErrors[key] {
                                            Text("• \(fieldErr)")
                                                .font(.caption2)
                                                .foregroundColor(SemanticColors.dangerText)
                                        }
                                    }
                                }
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(SemanticColors.dangerBg)
                            .cornerRadius(10)
                        }

                        if let savedMsg = viewModel.directEditProfileSavedMessage, !savedMsg.isEmpty {
                            Text(savedMsg)
                                .font(.caption.weight(.bold))
                                .foregroundColor(SemanticColors.successText)
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(SemanticColors.successBg)
                                .cornerRadius(10)
                        }

                        if viewModel.directEditLoadingDetail && detail == nil {
                            HStack {
                                Spacer()
                                ProgressView("Đang tải dữ liệu mới nhất từ máy chủ...")
                                    .padding(.vertical, 32)
                                Spacer()
                            }
                        } else if let detail {
                            // Section 1: Thông tin cá nhân
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Thông tin cá nhân")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(theme.primary)

                                directEditTextField("Họ và tên *", text: $form.hoTen, error: viewModel.directEditFieldErrors["display_name"] ?? viewModel.directEditFieldErrors["ho_ten"])

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Giới tính")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(SemanticColors.textSecondary)
                                    HStack(spacing: 8) {
                                        ForEach(["Nam", "Nữ", "Khác"], id: \.self) { g in
                                            let selected = form.gioiTinh.caseInsensitiveCompare(g) == .orderedSame
                                            Button {
                                                form.gioiTinh = selected ? "" : g
                                            } label: {
                                                Text(g)
                                                    .font(.caption.weight(selected ? .bold : .medium))
                                                    .padding(.horizontal, 12)
                                                    .padding(.vertical, 7)
                                                    .background(selected ? theme.primary : Color(hex: 0xF1F5F9))
                                                    .foregroundColor(selected ? .white : SemanticColors.textPrimary)
                                                    .cornerRadius(8)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }

                                directEditTextField(
                                    "Ngày sinh (DD/MM/YYYY)",
                                    text: $form.ngaySinh,
                                    placeholder: "VD: 15/08/1995",
                                    error: viewModel.directEditFieldErrors["date_of_birth"] ?? viewModel.directEditFieldErrors["ngay_sinh"]
                                )
                                directEditTextField(
                                    "Số CCCD (12 chữ số)",
                                    text: $form.soCccd,
                                    keyboard: .numberPad,
                                    error: viewModel.directEditFieldErrors["national_id_number"] ?? viewModel.directEditFieldErrors["so_cccd"]
                                )
                                directEditTextField(
                                    "Ngày cấp CCCD (DD/MM/YYYY)",
                                    text: $form.ngayCapCccd,
                                    placeholder: "VD: 20/10/2021",
                                    error: viewModel.directEditFieldErrors["national_id_issue_date"] ?? viewModel.directEditFieldErrors["ngay_cap_cccd"]
                                )
                                directEditTextField(
                                    "Nơi cấp CCCD",
                                    text: $form.noiCapCccd,
                                    error: viewModel.directEditFieldErrors["noi_cap_cccd"]
                                )
                                directEditTextField(
                                    "Địa chỉ / Quê quán",
                                    text: $form.queQuan,
                                    error: viewModel.directEditFieldErrors["que_quan"]
                                )
                                directEditTextField(
                                    "Số điện thoại",
                                    text: $form.sdt,
                                    keyboard: .phonePad,
                                    error: viewModel.directEditFieldErrors["sdt"]
                                )
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)

                            // Section 2: Thông tin công việc
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Thông tin công việc")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(theme.primary)

                                directEditTextField(
                                    "Mã NLĐ (hrp-MMYY-XXXXXX) *",
                                    text: $form.maNld,
                                    placeholder: "VD: hrp-1026-000123",
                                    error: viewModel.directEditFieldErrors["employee_code"] ?? viewModel.directEditFieldErrors["ma_nld"]
                                )

                                directEditPickerButton(
                                    label: "Dự án",
                                    value: form.duAn.isEmpty ? "Chọn dự án..." : form.duAn,
                                    error: viewModel.directEditFieldErrors["du_an"]
                                ) {
                                    KeyboardDismissal.dismiss()
                                    showProjectPicker = true
                                }

                                directEditPickerButton(
                                    label: "Loại hình lao động",
                                    value: form.nganhNghe.isEmpty ? "Chọn loại hình lao động..." : form.nganhNghe,
                                    error: viewModel.directEditFieldErrors["nganh_nghe"]
                                ) {
                                    KeyboardDismissal.dismiss()
                                    showLaborTypePicker = true
                                }

                                directEditPickerButton(
                                    label: "Người tuyển",
                                    value: form.nguoiTuyen.isEmpty ? "Chọn người tuyển..." : form.nguoiTuyen,
                                    error: viewModel.directEditFieldErrors["nguoi_tuyen"]
                                ) {
                                    KeyboardDismissal.dismiss()
                                    showRecruiterPicker = true
                                }

                                directEditTextField(
                                    "Ngày bắt đầu (DD/MM/YYYY)",
                                    text: $form.ngayVaoLam,
                                    placeholder: "VD: 01/10/2026",
                                    error: viewModel.directEditFieldErrors["started_on"] ?? viewModel.directEditFieldErrors["ngay_vao_lam"]
                                )

                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Trạng thái làm việc")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(SemanticColors.textSecondary)
                                    HStack(spacing: 8) {
                                        ForEach(["Đang làm", "Đã nghỉ"], id: \.self) { st in
                                            let isSelected = (st == "Đã nghỉ" && form.trangThai.lowercased().contains("nghỉ")) ||
                                                (st == "Đang làm" && !form.trangThai.lowercased().contains("nghỉ"))
                                            Button {
                                                form.trangThai = st
                                                form.employmentStatusCode = st == "Đã nghỉ" ? "OFF" : "ON"
                                            } label: {
                                                Text(st)
                                                    .font(.caption.weight(isSelected ? .bold : .medium))
                                                    .padding(.horizontal, 14)
                                                    .padding(.vertical, 8)
                                                    .background(isSelected ? theme.primaryLight : Color(hex: 0xF8FAFC))
                                                    .foregroundColor(isSelected ? theme.primary : SemanticColors.textSecondary)
                                                    .cornerRadius(8)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }

                                if form.trangThai.lowercased().contains("nghỉ") || form.employmentStatusCode.uppercased() == "OFF" {
                                    directEditTextField(
                                        "Ngày hiệu lực nghỉ việc (DD/MM/YYYY) *",
                                        text: $form.employmentEffectiveDate,
                                        placeholder: "VD: 30/10/2026",
                                        error: viewModel.directEditFieldErrors["effective_date"] ?? viewModel.directEditFieldErrors["employment_effective_date"]
                                    )
                                    directEditTextField(
                                        "Lý do nghỉ việc *",
                                        text: $form.offReason,
                                        placeholder: "Nhập lý do nghỉ việc...",
                                        error: viewModel.directEditFieldErrors["reason"]
                                    )
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)

                            // Section 3: Thông tin ngân hàng
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Thông tin ngân hàng")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(theme.primary)

                                directEditTextField(
                                    "Tên chủ tài khoản",
                                    text: $form.chuTaiKhoan,
                                    error: viewModel.directEditFieldErrors["payment"] ?? viewModel.directEditFieldErrors["chu_tai_khoan"]
                                )
                                directEditTextField(
                                    "Số tài khoản",
                                    text: $form.soTaiKhoan,
                                    error: viewModel.directEditFieldErrors["so_tai_khoan"]
                                )

                                let bankLabel: String = {
                                    if form.isCustomBank {
                                        return "Khác (nhập tên ngân hàng tham chiếu)"
                                    }
                                    if !form.nganHang.isEmpty {
                                        return form.nganHang
                                    }
                                    return "Chọn ngân hàng VietQR..."
                                }()

                                directEditPickerButton(
                                    label: "Ngân hàng",
                                    value: bankLabel,
                                    error: nil
                                ) {
                                    KeyboardDismissal.dismiss()
                                    showBankPicker = true
                                }

                                if form.isCustomBank {
                                    directEditTextField(
                                        "Tên ngân hàng tham chiếu *",
                                        text: $form.customBankName,
                                        placeholder: "Nhập tên ngân hàng...",
                                        error: viewModel.directEditFieldErrors["payment"] ?? viewModel.directEditFieldErrors["ngan_hang"]
                                    )
                                } else if let bankErr = viewModel.directEditFieldErrors["payment"] ?? viewModel.directEditFieldErrors["ngan_hang"] {
                                    Text(bankErr)
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.dangerText)
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)

                            // Section 4: Hồ sơ đính kèm (4 loại tài liệu)
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Hồ sơ đính kèm")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(theme.primary)

                                directEditSingleImageDocCard(
                                    title: "1. Ảnh chân dung NLĐ",
                                    docType: DirectEntryContract.docTypeWorkerPortrait,
                                    existingDoc: detail.documents.first(where: { $0.docType == DirectEntryContract.docTypeWorkerPortrait }),
                                    pickerItem: $portraitPhotoItem
                                )

                                directEditSingleImageDocCard(
                                    title: "2. CCCD mặt trước",
                                    docType: DirectEntryContract.docTypeCccdFront,
                                    existingDoc: detail.documents.first(where: { $0.docType == DirectEntryContract.docTypeCccdFront }),
                                    pickerItem: $cccdFrontPhotoItem
                                )

                                directEditSingleImageDocCard(
                                    title: "3. CCCD mặt sau",
                                    docType: DirectEntryContract.docTypeCccdBack,
                                    existingDoc: detail.documents.first(where: { $0.docType == DirectEntryContract.docTypeCccdBack }),
                                    pickerItem: $cccdBackPhotoItem
                                )

                                VStack(alignment: .leading, spacing: 8) {
                                    let contractDoc = detail.documents.first(where: { $0.docType == DirectEntryContract.docTypeEmploymentContract })
                                    HStack {
                                        Text("4. Hợp đồng lao động")
                                            .font(.caption.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                        Spacer()
                                        Text(contractDoc?.hasFile == true ? "Đã có tệp trên hệ thống" : "Chưa có tệp")
                                            .font(.caption2.weight(.semibold))
                                            .foregroundColor(contractDoc?.hasFile == true ? SemanticColors.successText : SemanticColors.textMuted)
                                    }

                                    ContractMultiAttachmentPickerView(
                                        title: "Chọn ảnh / PDF hợp đồng mới",
                                        attachments: stagedFiles[DirectEntryContract.docTypeEmploymentContract] ?? [],
                                        onAddAttachments: { added in
                                            var cur = stagedFiles[DirectEntryContract.docTypeEmploymentContract] ?? []
                                            cur.append(contentsOf: added)
                                            stagedFiles[DirectEntryContract.docTypeEmploymentContract] = cur
                                        },
                                        onRemoveAttachment: { id in
                                            var cur = stagedFiles[DirectEntryContract.docTypeEmploymentContract] ?? []
                                            cur.removeAll(where: { $0.id == id })
                                            stagedFiles[DirectEntryContract.docTypeEmploymentContract] = cur
                                        }
                                    )

                                    directEditFileStatusRow(docType: DirectEntryContract.docTypeEmploymentContract)
                                }
                                .padding(10)
                                .background(Color(hex: 0xF8FAFC))
                                .cornerRadius(8)
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)
                        }
                    }
                    .padding(14)
                }

                Divider()

                // Bottom Action Bar
                HStack(spacing: 12) {
                    Button {
                        KeyboardDismissal.dismiss()
                        viewModel.closeDirectEdit()
                        dismiss()
                    } label: {
                        Text("Hủy")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color(hex: 0xF1F5F9))
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(10)
                    }
                    .buttonStyle(.plain)

                    Button {
                        KeyboardDismissal.dismiss()
                        Task {
                            await viewModel.submitDirectEditProfileAndDocuments(
                                form: form,
                                stagedFiles: stagedFiles,
                                onAllComplete: {
                                    dismiss()
                                }
                            )
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if viewModel.directEditSavingProfile {
                                ProgressView().tint(.white).scaleEffect(0.85)
                            }
                            Text("Lưu thay đổi")
                                .font(.subheadline.weight(.bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(theme.primary)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.directEditLoadingDetail || viewModel.directEditSavingProfile || detail == nil)
                }
                .padding(14)
                .background(Color.white)
            }
            .background(theme.background)
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Sửa trực tiếp hồ sơ")
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: viewModel.directEditBaselineDetail) { newDetail in
                guard let newDetail else { return }
                if syncedEntryVersion != newDetail.entryVersion {
                    syncedEntryVersion = newDetail.entryVersion
                    form = DirectEntryService.buildDirectEditFormInput(from: newDetail)
                }
            }
            .onAppear {
                if let detail = viewModel.directEditBaselineDetail, syncedEntryVersion != detail.entryVersion {
                    syncedEntryVersion = detail.entryVersion
                    form = DirectEntryService.buildDirectEditFormInput(from: detail)
                }
            }
            .sheet(isPresented: $showProjectPicker) {
                let projectItems: [(id: String, label: String)] = {
                    if !viewModel.directEntryOptions.catalogProjects.isEmpty {
                        return viewModel.directEntryOptions.catalogProjects.map { ($0.projectId, $0.displayName) }
                    }
                    return viewModel.directEntryOptions.projects.map { ($0, $0) }
                }()
                DirectEntrySearchablePickerSheet(
                    title: "Chọn dự án",
                    placeholder: "Tìm tên dự án...",
                    items: projectItems,
                    allowCustomInput: false
                ) { selectedId, selectedLabel in
                    form.projectId = selectedId
                    form.duAn = selectedLabel
                }
            }
            .sheet(isPresented: $showLaborTypePicker) {
                let laborItems: [(id: String, label: String)] = viewModel.directEntryOptions.catalogLaborTypes.map {
                    ($0.laborTypeKey, $0.displayName)
                }
                DirectEntrySearchablePickerSheet(
                    title: "Chọn loại hình lao động",
                    placeholder: "Tìm loại hình lao động...",
                    items: laborItems,
                    allowCustomInput: false
                ) { selectedKey, selectedLabel in
                    form.laborTypeKey = selectedKey
                    form.nganhNghe = selectedLabel
                }
            }
            .sheet(isPresented: $showRecruiterPicker) {
                let recruiterItems: [(id: String, label: String)] = viewModel.directEntryOptions.catalogRecruiters.map {
                    ($0.recruiterId, $0.label)
                }
                DirectEntrySearchablePickerSheet(
                    title: "Chọn người tuyển",
                    placeholder: "Tìm tên người tuyển hoặc mã...",
                    items: recruiterItems,
                    allowCustomInput: false
                ) { selectedId, selectedLabel in
                    form.recruiterId = selectedId
                    let cleanName = selectedLabel.components(separatedBy: " · ").first ?? selectedLabel
                    form.nguoiTuyen = cleanName.replacingOccurrences(of: "Vendor: ", with: "")
                }
            }
            .sheet(isPresented: $showBankPicker) {
                var bankItems: [(id: String, label: String)] = VietQrBankCatalog.banks.map {
                    ($0.shortName, "\($0.shortName) — \($0.fullName)")
                }
                bankItems.insert(("__CUSTOM__", "Khác (nhập tên ngân hàng tham chiếu)"), at: 0)
                return DirectEntrySearchablePickerSheet(
                    title: "Chọn ngân hàng VietQR",
                    placeholder: "Tìm tên viết tắt hoặc tên đầy đủ ngân hàng...",
                    items: bankItems,
                    allowCustomInput: true
                ) { selectedId, _ in
                    if selectedId == "__CUSTOM__" {
                        form.isCustomBank = true
                        form.bankId = nil
                        form.bankName = ""
                    } else if let matched = VietQrBankCatalog.banks.first(where: { $0.shortName == selectedId }) {
                        form.isCustomBank = false
                        form.bankId = matched.id
                        form.bankName = matched.shortName
                    } else {
                        form.isCustomBank = true
                        form.bankId = nil
                        form.customBankName = selectedId
                    }
                }
            }
            .sheet(isPresented: Binding(
                get: { activeCameraDocType != nil },
                set: { if !$0 { activeCameraDocType = nil } }
            )) {
                if let docType = activeCameraDocType {
                    CameraPhotoCaptureSheet { data in
                        let item = LocalAttachmentItem(
                            fileName: "chup-\(docType.lowercased())-\(Int(Date().timeIntervalSince1970)).jpg",
                            mimeType: "image/jpeg",
                            data: data
                        )
                        stagedFiles[docType] = [item]
                        activeCameraDocType = nil
                    }
                }
            }
        }
    }

    private func directEditTextField(
        _ label: String,
        text: Binding<String>,
        placeholder: String = "",
        keyboard: UIKeyboardType = .default,
        error: String? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundColor(error != nil ? SemanticColors.dangerText : SemanticColors.textSecondary)
            TextField(placeholder.isEmpty ? label : placeholder, text: text)
                .keyboardType(keyboard)
                .submitLabel(.done)
                .font(.subheadline)
                .foregroundColor(SemanticColors.textPrimary)
                .padding(10)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(error != nil ? SemanticColors.dangerText : SemanticColors.borderSubtle, lineWidth: 1)
                )
            if let error, !error.isEmpty {
                Text(error)
                    .font(.caption2)
                    .foregroundColor(SemanticColors.dangerText)
            }
        }
    }

    private func directEditPickerButton(
        label: String,
        value: String,
        error: String?,
        action: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundColor(error != nil ? SemanticColors.dangerText : SemanticColors.textSecondary)
            Button(action: action) {
                HStack {
                    Text(value)
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textMuted)
                }
                .padding(10)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(error != nil ? SemanticColors.dangerText : SemanticColors.borderSubtle, lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
            if let error, !error.isEmpty {
                Text(error)
                    .font(.caption2)
                    .foregroundColor(SemanticColors.dangerText)
            }
        }
    }

    private func directEditSingleImageDocCard(
        title: String,
        docType: String,
        existingDoc: WorkerDocumentSummary?,
        pickerItem: Binding<PhotosPickerItem?>
    ) -> some View {
        let currentStaged = stagedFiles[docType]?.first
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title)
                    .font(.caption.weight(.bold))
                    .foregroundColor(SemanticColors.textPrimary)
                Spacer()
                Text(existingDoc?.hasFile == true ? "Đã có tệp trên hệ thống" : "Chưa có tệp")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(existingDoc?.hasFile == true ? SemanticColors.successText : SemanticColors.textMuted)
            }

            HStack(spacing: 8) {
                PhotosPicker(selection: pickerItem, matching: .images) {
                    HStack(spacing: 4) {
                        Image(systemName: "photo")
                        Text("Chọn ảnh")
                    }
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0xEBF3FB))
                    .foregroundColor(Color(hex: 0x1E6BB8))
                    .cornerRadius(8)
                }
                .onChange(of: pickerItem.wrappedValue) { newItem in
                    guard let newItem else { return }
                    Task {
                        if let data = try? await newItem.loadTransferable(type: Data.self) {
                            let att = LocalAttachmentItem(
                                fileName: "\(docType.lowercased())-\(Int(Date().timeIntervalSince1970)).jpg",
                                mimeType: "image/jpeg",
                                data: data
                            )
                            stagedFiles[docType] = [att]
                        }
                        pickerItem.wrappedValue = nil
                    }
                }

                Button {
                    activeCameraDocType = docType
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "camera.fill")
                        Text("Chụp ảnh")
                    }
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color(hex: 0xF1F5F9))
                    .foregroundColor(SemanticColors.textPrimary)
                    .cornerRadius(8)
                }
                .buttonStyle(.plain)

                if let currentStaged {
                    Text(currentStaged.fileName)
                        .font(.caption2)
                        .foregroundColor(SemanticColors.textSecondary)
                        .lineLimit(1)
                    Spacer()
                    Button {
                        stagedFiles[docType] = []
                        viewModel.directEditFileStatuses.removeValue(forKey: docType)
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(SemanticColors.dangerText)
                    }
                    .buttonStyle(.plain)
                }
            }

            directEditFileStatusRow(docType: docType)
        }
        .padding(10)
        .background(Color(hex: 0xF8FAFC))
        .cornerRadius(8)
    }

    @ViewBuilder
    private func directEditFileStatusRow(docType: String) -> some View {
        if let st = viewModel.directEditFileStatuses[docType], st.state != .idle {
            HStack(spacing: 8) {
                switch st.state {
                case .uploading:
                    ProgressView().scaleEffect(0.75)
                    Text(st.message.isEmpty ? "Đang tải tệp..." : st.message)
                        .font(.caption2)
                        .foregroundColor(SemanticColors.infoText)
                case .uploaded:
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(SemanticColors.successText)
                        .font(.caption)
                    Text(st.message.isEmpty ? "Đã tải tệp thành công." : st.message)
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.successText)
                case .error:
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(SemanticColors.dangerText)
                        .font(.caption)
                    Text(st.message.isEmpty ? "Lỗi tải tệp." : st.message)
                        .font(.caption2)
                        .foregroundColor(SemanticColors.dangerText)
                    Spacer()
                    if let files = stagedFiles[docType], !files.isEmpty {
                        Button("Thử lại") {
                            Task {
                                await viewModel.retryDirectEditDocument(docType: docType, attachments: files)
                            }
                        }
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(SemanticColors.dangerBg)
                        .foregroundColor(SemanticColors.dangerText)
                        .cornerRadius(6)
                    }
                case .idle:
                    EmptyView()
                }
            }
        }
    }
}

// MARK: - Salary Advance Create Request Sheet ("Ứng lương" for QLDA)

struct SalaryAdvanceCreateRequestSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let theme = viewModel.selectedTheme
        let projects = viewModel.salaryAdvanceContext?.activeManagedProjects ?? []
        let selectedWorker = viewModel.selectedSalaryAdvanceWorker

        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if let err = viewModel.salaryAdvanceCreateError, !err.isEmpty {
                            Text(err)
                                .font(.caption.weight(.bold))
                                .foregroundColor(SemanticColors.dangerText)
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(SemanticColors.dangerBg)
                                .cornerRadius(10)
                        }

                        // Step 1: Chọn dự án quản lý
                        VStack(alignment: .leading, spacing: 8) {
                            Text("1. Dự án quản lý")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(theme.primary)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(projects) { project in
                                        let isSelected = viewModel.salaryAdvanceSelectedProjectId == project.projectId
                                        Button {
                                            KeyboardDismissal.dismiss()
                                            Task { await viewModel.selectSalaryAdvanceProject(project.projectId) }
                                        } label: {
                                            Text(project.displayName)
                                                .font(.caption.weight(isSelected ? .bold : .medium))
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(isSelected ? theme.primary : Color(hex: 0xF1F5F9))
                                                .foregroundColor(isSelected ? .white : SemanticColors.textPrimary)
                                                .cornerRadius(8)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)

                        // Step 2: Tìm & chọn NLĐ thuộc dự án
                        VStack(alignment: .leading, spacing: 10) {
                            Text("2. Chọn người lao động")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(theme.primary)

                            HStack {
                                Image(systemName: "magnifyingglass")
                                    .foregroundColor(SemanticColors.textMuted)
                                TextField(
                                    "Tìm theo tên hoặc mã NLĐ...",
                                    text: Binding(
                                        get: { viewModel.salaryAdvanceWorkerSearch },
                                        set: { viewModel.updateSalaryAdvanceWorkerSearch($0) }
                                    )
                                )
                                .submitLabel(.search)
                                .font(.subheadline)
                                .foregroundColor(SemanticColors.textPrimary)
                            }
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                            if let workerErr = viewModel.salaryAdvanceWorkersError, !workerErr.isEmpty {
                                Text(workerErr)
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.dangerText)
                            }

                            if viewModel.salaryAdvanceWorkersLoading {
                                HStack {
                                    Spacer()
                                    ProgressView("Đang tải danh sách NLĐ...")
                                        .padding(.vertical, 16)
                                    Spacer()
                                }
                            } else if viewModel.salaryAdvanceWorkers.isEmpty {
                                Text("Không có người lao động phù hợp trong dự án này.")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                                    .padding(.vertical, 8)
                            } else {
                                VStack(spacing: 8) {
                                    ForEach(viewModel.salaryAdvanceWorkers) { item in
                                        let isSelected = viewModel.salaryAdvanceSelectedWorkerId == item.workerEntryId
                                        let eligibilityDesc = SalaryAdvanceContract.describeWorkerEligibility(item)
                                        let recLabel = SalaryAdvanceContract.formatWorkerRecruiterLabel(item)
                                        let workDateLabel = SalaryAdvanceContract.formatWorkDateVi(item.firstWorkDate)
                                        Button {
                                            KeyboardDismissal.dismiss()
                                            viewModel.selectSalaryAdvanceWorker(item.workerEntryId)
                                        } label: {
                                            VStack(alignment: .leading, spacing: 4) {
                                                HStack {
                                                    Text(item.employeeCode.isEmpty ? item.displayName : "\(item.displayName) (\(item.employeeCode))")
                                                        .font(.caption.weight(.bold))
                                                        .foregroundColor(SemanticColors.textPrimary)
                                                    Spacer()
                                                    Text(eligibilityDesc.isEligible ? "Đủ điều kiện" : "Chưa đủ ĐK")
                                                        .font(.caption2.weight(.bold))
                                                        .padding(.horizontal, 8)
                                                        .padding(.vertical, 3)
                                                        .background(eligibilityDesc.isEligible ? SemanticColors.successBg : SemanticColors.warningBg)
                                                        .foregroundColor(eligibilityDesc.isEligible ? SemanticColors.successText : SemanticColors.warningText)
                                                        .clipShape(Capsule())
                                                }
                                                Text("Người tuyển: \(recLabel) · Ngày vào: \(workDateLabel)")
                                                    .font(.caption2)
                                                    .foregroundColor(SemanticColors.textSecondary)
                                                if !eligibilityDesc.isEligible, let blockingMsg = eligibilityDesc.blockingMessage {
                                                    Text(blockingMsg)
                                                        .font(.caption2.weight(.semibold))
                                                        .foregroundColor(SemanticColors.dangerText)
                                                }
                                            }
                                            .padding(10)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(isSelected ? theme.primaryLight : Color(hex: 0xF8FAFC))
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .stroke(isSelected ? theme.primary : SemanticColors.borderSubtle, lineWidth: isSelected ? 1.5 : 1)
                                            )
                                            .cornerRadius(8)
                                        }
                                        .buttonStyle(.plain)
                                    }

                                    if viewModel.salaryAdvanceWorkersHasMore {
                                        Button {
                                            Task { await viewModel.loadMoreSalaryAdvanceWorkers() }
                                        } label: {
                                            HStack {
                                                if viewModel.salaryAdvanceWorkersLoadingMore {
                                                    ProgressView().scaleEffect(0.8)
                                                }
                                                Text("Tải thêm NLĐ")
                                                    .font(.caption.weight(.bold))
                                            }
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8)
                                            .background(Color(hex: 0xF1F5F9))
                                            .foregroundColor(theme.primary)
                                            .cornerRadius(8)
                                        }
                                        .buttonStyle(.plain)
                                        .disabled(viewModel.salaryAdvanceWorkersLoadingMore)
                                    }
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)

                        // Step 3: Thông tin NLĐ & Tóm tắt ứng 30 ngày gần nhất + Nút xem lịch sử
                        if let worker = selectedWorker {
                            let eligibilityDesc = SalaryAdvanceContract.describeWorkerEligibility(worker)
                            let bankDesc = SalaryAdvanceContract.describeWorkerBankTransferEligibility(worker)
                            let sumCount = worker.advance30dSummary?.totalCount ?? 0
                            let sumAmountRaw = worker.advance30dSummary?.totalAmountVnd ?? "0"

                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text("Thông tin & Lịch sử ứng lương")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(theme.primary)
                                    Spacer()
                                    Button {
                                        KeyboardDismissal.dismiss()
                                        Task { await viewModel.openSalaryAdvanceWorkerHistory() }
                                    } label: {
                                        HStack(spacing: 4) {
                                            Image(systemName: "clock.arrow.circlepath")
                                            Text("Xem lịch sử ứng")
                                        }
                                        .font(.caption.weight(.bold))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(theme.primaryLight)
                                        .foregroundColor(theme.primary)
                                        .cornerRadius(8)
                                    }
                                    .buttonStyle(.plain)
                                }

                                Text("Họ tên: \(worker.displayName) (\(worker.employeeCode))")
                                    .font(.caption.weight(.semibold))
                                Text("Ngày đầu tiên làm việc: \(SalaryAdvanceContract.formatWorkDateVi(worker.firstWorkDate))")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)
                                Text("Người tuyển: \(SalaryAdvanceContract.formatWorkerRecruiterLabel(worker))")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textSecondary)

                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Số lần ứng (30 ngày)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                        Text("\(sumCount) lần")
                                            .font(.subheadline.weight(.bold))
                                            .foregroundColor(SemanticColors.textPrimary)
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text("Tổng tiền (30 ngày)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                        Text(SalaryAdvanceContract.formatVndAmount(sumAmountRaw))
                                            .font(.subheadline.weight(.bold))
                                            .foregroundColor(theme.primary)
                                    }
                                }
                                .padding(10)
                                .background(Color(hex: 0xF8FAFC))
                                .cornerRadius(8)

                                if !eligibilityDesc.isEligible, let reason = eligibilityDesc.blockingMessage {
                                    Text(reason)
                                        .font(.caption.weight(.bold))
                                        .foregroundColor(SemanticColors.dangerText)
                                        .padding(10)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(SemanticColors.dangerBg)
                                        .cornerRadius(8)
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)

                            // Step 4: Tạo yêu cầu ứng lương
                            VStack(alignment: .leading, spacing: 10) {
                                Text("3. Thông tin đề nghị ứng lương")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(theme.primary)

                                Text("Phương thức nhận tiền")
                                    .font(.caption2.weight(.semibold))
                                    .foregroundColor(SemanticColors.textSecondary)

                                HStack(spacing: 8) {
                                    let isCash = viewModel.salaryAdvancePaymentMethod == SalaryAdvanceContract.paymentMethodCash
                                    Button {
                                        viewModel.updateSalaryAdvancePaymentMethod(SalaryAdvanceContract.paymentMethodCash)
                                    } label: {
                                        Text("Tiền mặt")
                                            .font(.caption.weight(isCash ? .bold : .medium))
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 9)
                                            .background(isCash ? theme.primary : Color(hex: 0xF1F5F9))
                                            .foregroundColor(isCash ? .white : SemanticColors.textPrimary)
                                            .cornerRadius(8)
                                    }
                                    .buttonStyle(.plain)

                                    let isBank = viewModel.salaryAdvancePaymentMethod == SalaryAdvanceContract.paymentMethodBankTransfer
                                    Button {
                                        viewModel.updateSalaryAdvancePaymentMethod(SalaryAdvanceContract.paymentMethodBankTransfer)
                                    } label: {
                                        Text("Chuyển khoản")
                                            .font(.caption.weight(isBank ? .bold : .medium))
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 9)
                                            .background(isBank ? theme.primary : Color(hex: 0xF1F5F9))
                                            .foregroundColor(isBank ? .white : SemanticColors.textPrimary)
                                            .cornerRadius(8)
                                    }
                                    .buttonStyle(.plain)
                                }

                                if viewModel.salaryAdvancePaymentMethod == SalaryAdvanceContract.paymentMethodBankTransfer {
                                    if bankDesc.canUseBankTransfer {
                                        Text("Tài khoản thụ hưởng: \(bankDesc.institutionName ?? "—") · \(bankDesc.accountNumberDisplay ?? "—") · \(bankDesc.accountHolderName ?? "—")")
                                            .font(.caption)
                                            .foregroundColor(SemanticColors.successText)
                                    } else if let bankReason = bankDesc.blockingMessage {
                                        Text(bankReason)
                                            .font(.caption.weight(.semibold))
                                            .foregroundColor(SemanticColors.dangerText)
                                    }
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Số tiền đề nghị (VNĐ) *")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(SemanticColors.textSecondary)
                                    TextField(
                                        "Nhập số tiền nguyên dương (VD: 2000000)",
                                        text: Binding(
                                            get: { viewModel.salaryAdvanceAmountRaw },
                                            set: { viewModel.updateSalaryAdvanceAmount($0) }
                                        )
                                    )
                                    .keyboardType(.numberPad)
                                    .submitLabel(.done)
                                    .font(.subheadline)
                                    .padding(10)
                                    .background(Color(hex: 0xF8FAFC))
                                    .cornerRadius(8)
                                    if let parsed = SalaryAdvanceContract.normalizePositiveVndInput(viewModel.salaryAdvanceAmountRaw) {
                                        Text(" Hiển thị: \(SalaryAdvanceContract.formatVndAmount(parsed))")
                                            .font(.caption2.weight(.semibold))
                                            .foregroundColor(theme.primary)
                                    }
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Nội dung đề nghị *")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(SemanticColors.textSecondary)
                                    TextField(
                                        "Nhập lý do / nội dung đề nghị ứng lương...",
                                        text: Binding(
                                            get: { viewModel.salaryAdvanceInitialContent },
                                            set: { viewModel.updateSalaryAdvanceInitialContent($0) }
                                        )
                                    )
                                    .submitLabel(.done)
                                    .font(.subheadline)
                                    .padding(10)
                                    .background(Color(hex: 0xF8FAFC))
                                    .cornerRadius(8)
                                }
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)
                        }
                    }
                    .padding(14)
                }

                Divider()

                let canSubmit: Bool = {
                    guard let worker = selectedWorker,
                          SalaryAdvanceContract.describeWorkerEligibility(worker).isEligible,
                          !viewModel.salaryAdvanceCreateSubmitting,
                          SalaryAdvanceContract.normalizePositiveVndInput(viewModel.salaryAdvanceAmountRaw) != nil,
                          !viewModel.salaryAdvanceInitialContent.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                        return false
                    }
                    if viewModel.salaryAdvancePaymentMethod == SalaryAdvanceContract.paymentMethodBankTransfer &&
                        !SalaryAdvanceContract.describeWorkerBankTransferEligibility(worker).canUseBankTransfer {
                        return false
                    }
                    return true
                }()

                HStack(spacing: 12) {
                    Button {
                        KeyboardDismissal.dismiss()
                        viewModel.closeSalaryAdvanceSheet()
                        dismiss()
                    } label: {
                        Text("Đóng")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color(hex: 0xF1F5F9))
                            .foregroundColor(SemanticColors.textPrimary)
                            .cornerRadius(10)
                    }
                    .buttonStyle(.plain)

                    Button {
                        KeyboardDismissal.dismiss()
                        Task { await viewModel.submitSalaryAdvanceRequest() }
                    } label: {
                        HStack(spacing: 6) {
                            if viewModel.salaryAdvanceCreateSubmitting {
                                ProgressView().tint(.white).scaleEffect(0.85)
                            }
                            Text("Gửi đề nghị ứng lương")
                                .font(.subheadline.weight(.bold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(canSubmit ? theme.primary : Color.gray.opacity(0.45))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canSubmit)
                }
                .padding(14)
                .background(Color.white)
            }
            .background(theme.background)
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Tạo đề nghị ứng lương")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $viewModel.salaryAdvanceHistoryOpen) {
                SalaryAdvanceWorkerHistorySheet(viewModel: viewModel)
            }
        }
    }
}

// MARK: - Salary Advance Worker History Sheet (PM Modal)

struct SalaryAdvanceWorkerHistorySheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Environment(\.dismiss) private var dismiss

    private let periodModes: [(String, String)] = [
        (SalaryAdvanceContract.periodModeLast30Days, "30 ngày gần nhất"),
        (SalaryAdvanceContract.periodModeCurrentMonth, "Tháng này"),
        (SalaryAdvanceContract.periodModeMonth, "Chọn tháng"),
        (SalaryAdvanceContract.periodModeAll, "Toàn bộ"),
        (SalaryAdvanceContract.periodModeCustomRange, "Khoảng ngày"),
    ]

    var body: some View {
        let theme = viewModel.selectedTheme
        let worker = viewModel.selectedSalaryAdvanceWorker

        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if let worker {
                            Text("NLĐ: \(worker.displayName) (\(worker.employeeCode))")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)
                        }

                        // Period Filter Chips
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(periodModes, id: \.0) { mode, label in
                                    let selected = viewModel.salaryAdvanceHistoryPeriodMode == mode
                                    Button {
                                        KeyboardDismissal.dismiss()
                                        Task { await viewModel.selectSalaryAdvanceHistoryPeriodMode(mode) }
                                    } label: {
                                        Text(label)
                                            .font(.caption.weight(selected ? .bold : .medium))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 7)
                                            .background(selected ? theme.primary : Color(hex: 0xF1F5F9))
                                            .foregroundColor(selected ? .white : SemanticColors.textPrimary)
                                            .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        if viewModel.salaryAdvanceHistoryPeriodMode == SalaryAdvanceContract.periodModeMonth {
                            HStack(spacing: 8) {
                                TextField("YYYY-MM (VD: 2026-10)", text: $viewModel.salaryAdvanceHistoryMonth)
                                    .font(.caption)
                                    .padding(9)
                                    .background(Color.white)
                                    .cornerRadius(8)
                                Button("Áp dụng") {
                                    KeyboardDismissal.dismiss()
                                    Task {
                                        await viewModel.selectSalaryAdvanceHistoryPeriodMode(
                                            SalaryAdvanceContract.periodModeMonth,
                                            month: viewModel.salaryAdvanceHistoryMonth
                                        )
                                    }
                                }
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 9)
                                .background(theme.primary)
                                .foregroundColor(.white)
                                .cornerRadius(8)
                            }
                        }

                        if viewModel.salaryAdvanceHistoryPeriodMode == SalaryAdvanceContract.periodModeCustomRange {
                            HStack(spacing: 8) {
                                TextField("Từ DD/MM/YYYY", text: $viewModel.salaryAdvanceHistoryFromDate)
                                    .font(.caption)
                                    .padding(9)
                                    .background(Color.white)
                                    .cornerRadius(8)
                                TextField("Đến DD/MM/YYYY", text: $viewModel.salaryAdvanceHistoryToDate)
                                    .font(.caption)
                                    .padding(9)
                                    .background(Color.white)
                                    .cornerRadius(8)
                                Button("Lọc") {
                                    KeyboardDismissal.dismiss()
                                    Task {
                                        await viewModel.applySalaryAdvanceHistoryCustomRange(
                                            fromDate: viewModel.salaryAdvanceHistoryFromDate,
                                            toDate: viewModel.salaryAdvanceHistoryToDate
                                        )
                                    }
                                }
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 9)
                                .background(theme.primary)
                                .foregroundColor(.white)
                                .cornerRadius(8)
                            }
                        }

                        if let err = viewModel.salaryAdvanceHistoryError, !err.isEmpty {
                            Text(err)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.dangerText)
                        }

                        if let summary = viewModel.salaryAdvanceHistoryResponse {
                            HStack {
                                Text("Tổng số lần: \(summary.totalCount) lần")
                                    .font(.caption.weight(.bold))
                                Spacer()
                                Text("Tổng tiền: \(SalaryAdvanceContract.formatVndAmount(summary.totalAmountVnd))")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(theme.primary)
                            }
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                        }

                        if viewModel.salaryAdvanceHistoryLoading {
                            HStack {
                                Spacer()
                                ProgressView("Đang tải lịch sử ứng lương...")
                                    .padding(.vertical, 24)
                                Spacer()
                            }
                        } else if viewModel.salaryAdvanceHistoryItems.isEmpty {
                            Text("Chưa có lịch sử ứng lương trong kỳ đã chọn.")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textSecondary)
                                .padding(.vertical, 20)
                        } else {
                            ForEach(viewModel.salaryAdvanceHistoryItems) { item in
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(item.requestId.isEmpty ? "Đề nghị ứng" : item.requestId)
                                            .font(.caption.weight(.bold))
                                        Spacer()
                                        Text("\(SalaryAdvanceContract.requestStateShortLabel(item.requestState)) · \(SalaryAdvanceContract.evidenceReviewStateShortLabel(item.evidenceReviewState))")
                                            .font(.caption2.weight(.bold))
                                            .foregroundColor(theme.primary)
                                    }
                                    Text("Số tiền: \(SalaryAdvanceContract.formatVndAmount(item.requestedAmountVnd)) · \(SalaryAdvanceContract.paymentMethodLabel(item.paymentMethod))")
                                        .font(.caption.weight(.semibold))
                                    Text("Dự án: \(item.projectDisplayName) · Ngày gửi: \(WorkReportsContract.formatIsoDateTimeVi(item.createdAt))")
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textSecondary)
                                    if let content = item.initialContent, !content.isEmpty {
                                        Text("Nội dung: \(content)")
                                            .font(.caption2)
                                            .foregroundColor(SemanticColors.textMuted)
                                    }
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white)
                                .cornerRadius(10)
                            }

                            if viewModel.salaryAdvanceHistoryHasMore {
                                Button {
                                    Task { await viewModel.loadMoreSalaryAdvanceWorkerHistory() }
                                } label: {
                                    HStack {
                                        if viewModel.salaryAdvanceHistoryLoadingMore {
                                            ProgressView().scaleEffect(0.8)
                                        }
                                        Text("Tải thêm lịch sử")
                                            .font(.caption.weight(.bold))
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Color.white)
                                    .foregroundColor(theme.primary)
                                    .cornerRadius(8)
                                }
                                .buttonStyle(.plain)
                                .disabled(viewModel.salaryAdvanceHistoryLoadingMore)
                            }
                        }
                    }
                    .padding(14)
                }
            }
            .background(theme.background)
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Lịch sử ứng lương")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") {
                        viewModel.closeSalaryAdvanceWorkerHistory()
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - iOS Share Sheet Helper

public struct ActivityShareSheet: UIViewControllerRepresentable {
    public let activityItems: [Any]

    public init(activityItems: [Any]) {
        self.activityItems = activityItems
    }

    public func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
