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
                                activeQrScannerRowId = draft.rowId
                            }
                        )
                    }
                }
                .padding(14)
            }
        }
        .background(theme.background)
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

// MARK: - Single Draft Row Card

struct NewWorkerDraftRowCard: View {
    let index: Int
    let draft: NewWorkerEntryDraft
    @ObservedObject var viewModel: MiniBiViewModel
    let onScanQrTap: () -> Void

    @State private var isExpandedDetails: Bool = true
    @State private var frontPhotoPickerItem: PhotosPickerItem?
    @State private var backPhotoPickerItem: PhotosPickerItem?

    var body: some View {
        let theme = viewModel.selectedTheme

        VStack(alignment: .leading, spacing: 12) {
            // Card Header
            HStack {
                HStack(spacing: 6) {
                    Text("Dòng #\(index + 1)")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(theme.primary)
                    if draft.isReadyForSubmit {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundColor(SemanticColors.successText)
                            .font(.caption)
                    }
                }

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

                if viewModel.newWorkerDraftRows.count > 1 {
                    Button {
                        viewModel.removeNewWorkerDraftRow(rowId: draft.rowId)
                    } label: {
                        Image(systemName: "trash")
                            .foregroundColor(SemanticColors.dangerText)
                            .padding(6)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Primary Fields (`Họ tên *`, `Số CCCD`, `Dự án *`, `Trạng thái *`)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                draftTextField(label: "Họ và tên *", placeholder: "Nguyễn Văn A", value: draft.hoTen) { val in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.hoTen = val }
                }
                draftTextField(label: "Số CCCD (12 số)", placeholder: "034098...", value: draft.soCccd, keyboard: .numberPad) { val in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.soCccd = val }
                }
                draftTextField(label: "Dự án *", placeholder: "Nhập tên dự án", value: draft.duAn) { val in
                    viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.duAn = val }
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Trạng thái *")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    Menu {
                        ForEach(["Đang làm việc", "Phỏng vấn", "Đã nghỉ việc"], id: \.self) { st in
                            Button(st) {
                                viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.trangThai = st }
                            }
                        }
                    } label: {
                        HStack {
                            Text(draft.trangThai.isEmpty ? "Chọn trạng thái" : draft.trangThai)
                                .font(.caption)
                                .foregroundColor(SemanticColors.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                                .foregroundColor(SemanticColors.textMuted)
                        }
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

            // Expandable Personal & Assignment Fields
            DisclosureGroup(isExpanded: $isExpandedDetails) {
                VStack(spacing: 10) {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        draftTextField(label: "Số điện thoại", placeholder: "09...", value: draft.sdt, keyboard: .phonePad) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.sdt = val }
                        }
                        draftTextField(label: "Ngày sinh", placeholder: "dd/MM/yyyy", value: draft.ngaySinh) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ngaySinh = val }
                        }
                        draftTextField(label: "Giới tính", placeholder: "Nam / Nữ", value: draft.gioiTinh) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.gioiTinh = val }
                        }
                        draftTextField(label: "Ngày cấp CCCD", placeholder: "dd/MM/yyyy", value: draft.ngayCap) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ngayCap = val }
                        }
                        draftTextField(label: "Ngày vào làm", placeholder: "dd/MM/yyyy", value: draft.ngayVaoLam) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ngayVaoLam = val }
                        }
                        draftTextField(label: "Quản lý", placeholder: "Mã/Tên quản lý", value: draft.quanLy) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.quanLy = val }
                        }
                        draftTextField(label: "Người tuyển", placeholder: "Mã/Tên người tuyển", value: draft.nguoiTuyen) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.nguoiTuyen = val }
                        }
                        draftTextField(label: "Ngành nghề", placeholder: "Công nhân / Kỹ thuật...", value: draft.nganhNghe) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.nganhNghe = val }
                        }
                        draftTextField(label: "Số tài khoản", placeholder: "STK ngân hàng", value: draft.soTaiKhoan) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.soTaiKhoan = val }
                        }
                        draftTextField(label: "Ngân hàng", placeholder: "Vietcombank, MB...", value: draft.nganHang) { val in
                            viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.nganHang = val }
                        }
                    }

                    draftTextField(label: "Quê quán / Địa chỉ thường trú", placeholder: "Nhập địa chỉ...", value: draft.queQuan) { val in
                        viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.queQuan = val }
                    }
                    draftTextField(label: "Ghi chú", placeholder: "Ghi chú thêm...", value: draft.ghiChu) { val in
                        viewModel.updateNewWorkerDraftRow(rowId: draft.rowId) { $0.ghiChu = val }
                    }
                }
                .padding(.top, 8)
            } label: {
                Text("Thông tin cá nhân, Quản lý & Ngân hàng")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(SemanticColors.textSecondary)
            }

            Divider()

            // CCCD Front / Back Pickers
            HStack(spacing: 10) {
                PhotosPicker(selection: $frontPhotoPickerItem, matching: .images) {
                    HStack(spacing: 4) {
                        Image(systemName: draft.cccdFrontFile != nil ? "checkmark.circle.fill" : "creditcard")
                        Text(draft.cccdFrontFile != nil ? "Đã chọn CCCD trước" : "Ảnh CCCD trước")
                    }
                    .font(.caption2.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(draft.cccdFrontFile != nil ? SemanticColors.successBg : Color(hex: 0xF1F5F9))
                    .foregroundColor(draft.cccdFrontFile != nil ? SemanticColors.successText : SemanticColors.textPrimary)
                    .cornerRadius(8)
                }
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
                            // Also try auto-decoding QR from front CCCD photo if CCCD is still blank!
                            if draft.soCccd.isEmpty, let qrRaw = CccdQrScannerSheet.detectQrFromImageData(data) {
                                _ = viewModel.applyScannedCccdQr(rowId: draft.rowId, rawQr: qrRaw)
                            }
                        }
                    }
                }

                PhotosPicker(selection: $backPhotoPickerItem, matching: .images) {
                    HStack(spacing: 4) {
                        Image(systemName: draft.cccdBackFile != nil ? "checkmark.circle.fill" : "creditcard.fill")
                        Text(draft.cccdBackFile != nil ? "Đã chọn CCCD sau" : "Ảnh CCCD sau")
                    }
                    .font(.caption2.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(draft.cccdBackFile != nil ? SemanticColors.successBg : Color(hex: 0xF1F5F9))
                    .foregroundColor(draft.cccdBackFile != nil ? SemanticColors.successText : SemanticColors.textPrimary)
                    .cornerRadius(8)
                }
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
                    }
                }
            }

            // Multi-Image / PDF Contract Attachment Section (merges 2+ images into 1 PDF)
            ContractMultiAttachmentPickerView(
                title: "Hồ sơ Hợp đồng / Thẻ an toàn",
                attachments: draft.contractFiles,
                onAddAttachments: { newItems in
                    for att in newItems {
                        viewModel.addAttachmentToDraftRow(
                            rowId: draft.rowId,
                            docType: DirectEntryContract.docTypeSafetyCard,
                            attachment: att
                        )
                    }
                },
                onRemoveAttachment: { attId in
                    viewModel.removeContractAttachmentFromDraftRow(rowId: draft.rowId, attachmentId: attId)
                }
            )
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(SemanticColors.borderSubtle, lineWidth: 1)
        )
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
            .font(.caption)
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

            // Search & Export Bar
            HStack(spacing: 8) {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(SemanticColors.textMuted)
                    TextField("Tìm họ tên, mã NLĐ, CCCD, SĐT...", text: $viewModel.workerSearchQuery)
                        .font(.caption)
                        .onSubmit {
                            Task { await viewModel.loadWorkers(resetPage: true) }
                        }
                }
                .padding(9)
                .background(Color(hex: 0xF8FAFC))
                .cornerRadius(8)

                Button("Tìm") {
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
                            Button {
                                viewModel.selectedWorkerDetail = worker
                            } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack {
                                        Text("\(worker.hoTen) (\(worker.maNld))")
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
                                }
                                .padding(14)
                                .background(Color.white)
                                .cornerRadius(12)
                            }
                            .buttonStyle(.plain)
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
        .onAppear {
            if viewModel.workers.isEmpty {
                Task { await viewModel.loadWorkers(resetPage: true) }
            }
        }
        .sheet(item: $viewModel.selectedWorkerDetail) { worker in
            WorkerDetailSheet(viewModel: viewModel, worker: worker)
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

// MARK: - Worker Detail & Contract Multi-Image Upload Sheet (`POST /api/workers/{ma_nld}/contract-file`)

struct WorkerDetailSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    let worker: WorkerItem
    @Environment(\.dismiss) private var dismiss

    @State private var stagedContractAttachments: [LocalAttachmentItem] = []
    @State private var proposeReason: String = ""
    @State private var editedPhone: String = ""
    @State private var editedProject: String = ""
    @State private var editedStatus: String = ""

    var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Profile Summary
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(worker.hoTen) (\(worker.maNld))")
                            .font(.title3.weight(.bold))
                        Text("Dự án: \(worker.duAn) · Trạng thái: \(worker.trangThai)")
                            .font(.subheadline)
                            .foregroundColor(SemanticColors.textSecondary)
                        Divider()
                        Group {
                            detailRow("Số CCCD", worker.soCccd)
                            detailRow("Ngày sinh / Giới tính", "\(worker.ngaySinh) · \(worker.gioiTinh)")
                            detailRow("Quê quán", worker.queQuan)
                            detailRow("Số điện thoại", worker.sdt)
                            detailRow("Quản lý / Người tuyển", "\(worker.quanLy) / \(worker.nguoiTuyen)")
                            detailRow("Ngày vào làm", worker.ngayVaoLam)
                            detailRow("Ngân hàng", "\(worker.soTaiKhoan) - \(worker.nganHang) (\(worker.chuTaiKhoan))")
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)

                    // Multi-Image / PDF Contract Upload (`POST /api/workers/{ma_nld}/contract-file`)
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Hồ sơ Hợp đồng lao động")
                                .font(.headline)
                            Spacer()
                            if let existingUrl = worker.contractFileUrl, !existingUrl.isEmpty {
                                Label("Đã có tệp HĐ", systemImage: "checkmark.seal.fill")
                                    .font(.caption.weight(.bold))
                                    .foregroundColor(SemanticColors.successText)
                            }
                        }

                        Text("Chọn 1 file PDF/ảnh hoặc chọn nhiều ảnh hợp đồng (tự động xoay chuẩn EXIF, thu phóng ≤1600px và ghép thành 1 file PDF ≤10MB trước khi tải lên).")
                            .font(.caption)
                            .foregroundColor(SemanticColors.textSecondary)

                        ContractMultiAttachmentPickerView(
                            title: "Danh sách ảnh / tệp hợp đồng đã chọn",
                            attachments: stagedContractAttachments,
                            onAddAttachments: { newItems in
                                stagedContractAttachments.append(contentsOf: newItems)
                            },
                            onRemoveAttachment: { id in
                                stagedContractAttachments.removeAll(where: { $0.id == id })
                            }
                        )

                        Button {
                            Task {
                                let ok = await viewModel.uploadWorkerContract(
                                    maNld: worker.maNld,
                                    attachments: stagedContractAttachments
                                )
                                if ok {
                                    stagedContractAttachments.removeAll()
                                }
                            }
                        } label: {
                            HStack {
                                Spacer()
                                if viewModel.isUploadingWorkerContract {
                                    ProgressView().tint(.white).padding(.trailing, 6)
                                }
                                Text(
                                    stagedContractAttachments.count >= 2
                                    ? "Ghép \(stagedContractAttachments.count) ảnh thành 1 PDF & Tải lên"
                                    : "Tải lên hồ sơ hợp đồng"
                                )
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(.white)
                                Spacer()
                            }
                            .padding(.vertical, 12)
                            .background(stagedContractAttachments.isEmpty ? Color.gray : theme.primary)
                            .cornerRadius(10)
                        }
                        .disabled(stagedContractAttachments.isEmpty || viewModel.isUploadingWorkerContract)
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)

                    // Quick Edit / Propose Change Card
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Cập nhật / Đề xuất điều chỉnh thông tin")
                            .font(.headline)

                        TextField("Số điện thoại", text: $editedPhone)
                            .padding(9)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        TextField("Dự án", text: $editedProject)
                            .padding(9)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        TextField("Trạng thái", text: $editedStatus)
                            .padding(9)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        TextField("Lý do đề xuất chỉnh sửa...", text: $proposeReason)
                            .padding(9)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)

                        HStack(spacing: 10) {
                            Button {
                                Task {
                                    let res = await viewModel.repository.proposeWorkerChange(
                                        maNld: worker.maNld,
                                        changes: [
                                            "sdt": editedPhone,
                                            "du_an": editedProject,
                                            "trang_thai": editedStatus
                                        ],
                                        reason: proposeReason
                                    )
                                    switch res {
                                    case .success:
                                        viewModel.showBanner("Đã gửi đề xuất chỉnh sửa hồ sơ \(worker.maNld).")
                                        dismiss()
                                    case .failure(let msg, _, _, _):
                                        viewModel.showBanner(msg, isError: true)
                                    }
                                }
                            } label: {
                                Text("Gửi đề xuất")
                                    .font(.caption.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 11)
                                    .background(theme.primaryLight)
                                    .foregroundColor(theme.primary)
                                    .cornerRadius(10)
                            }

                            Button {
                                Task {
                                    let res = await viewModel.repository.directEditWorker(
                                        maNld: worker.maNld,
                                        updates: [
                                            "sdt": editedPhone,
                                            "du_an": editedProject,
                                            "trang_thai": editedStatus
                                        ]
                                    )
                                    switch res {
                                    case .success:
                                        viewModel.showBanner("Đã cập nhật trực tiếp hồ sơ \(worker.maNld).")
                                        await viewModel.loadWorkers(resetPage: false)
                                        dismiss()
                                    case .failure(let msg, _, _, _):
                                        viewModel.showBanner(msg, isError: true)
                                    }
                                }
                            } label: {
                                Text("Lưu trực tiếp")
                                    .font(.caption.weight(.bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 11)
                                    .background(theme.primary)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)
                }
                .padding(14)
            }
            .background(theme.background)
            .navigationTitle("Hồ sơ NLĐ")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") { dismiss() }
                }
            }
            .onAppear {
                editedPhone = worker.sdt
                editedProject = worker.duAn
                editedStatus = worker.trangThai
            }
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.caption)
                .foregroundColor(SemanticColors.textMuted)
            Spacer()
            Text(value.isEmpty ? "—" : value)
                .font(.caption.weight(.medium))
                .foregroundColor(SemanticColors.textPrimary)
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
