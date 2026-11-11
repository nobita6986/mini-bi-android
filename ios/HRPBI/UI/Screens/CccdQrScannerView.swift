import SwiftUI
import AVFoundation
import Vision
import PhotosUI

// MARK: - Camera & Vision CCCD QR Scanner View
// Mirrors Android's `ui/screens/CccdQrScannerDialog.kt` 100%

public struct CccdQrScannerSheet: View {
    public let onDecoded: (String, CccdQrData) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var selectedZoomFactor: CGFloat = 1.5
    @State private var isTorchOn: Bool = false
    @State private var statusMessage: String = "Đưa mã QR ở góc trên bên phải thẻ CCCD vào khung ngắm"
    @State private var manualQrInput: String = ""
    @State private var selectedPhotoItem: PhotosPickerItem?

    public init(onDecoded: @escaping (String, CccdQrData) -> Void) {
        self.onDecoded = onDecoded
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                // Live Camera Preview with AVFoundation + Vision
                ZStack {
                    CccdCameraPreviewRepresentable(
                        zoomFactor: selectedZoomFactor,
                        isTorchOn: isTorchOn,
                        onRawQrDetected: { rawValue in
                            handleRawCandidate(rawValue)
                        }
                    )
                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusHero, style: .continuous))

                    // Dimmed surrounding cutout & 4 Corner Brackets Reticle Overlay
                    QrViewfinderOverlay(cutoutSize: 224)

                    VStack {
                        Spacer()
                        HStack(spacing: 10) {
                            ForEach([1.0, 1.5, 2.0], id: \.self) { zoom in
                                let isSelected = selectedZoomFactor == CGFloat(zoom)
                                Button {
                                    HapticsManager.selection()
                                    withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                        selectedZoomFactor = CGFloat(zoom)
                                    }
                                } label: {
                                    Text(String(format: "%.1fx", zoom))
                                        .font(.caption.weight(.bold))
                                        .monospacedDigit()
                                        .frame(width: 44, height: 44)
                                        .background(isSelected ? Color.white : Color.black.opacity(0.55))
                                        .foregroundColor(isSelected ? .black : .white)
                                        .clipShape(Circle())
                                        .contentShape(Circle())
                                }
                                .buttonStyle(HrpPressableButtonStyle())
                            }

                            Button {
                                HapticsManager.selection()
                                isTorchOn.toggle()
                            } label: {
                                Image(systemName: isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                                    .font(.system(size: 14, weight: .bold))
                                    .frame(width: 44, height: 44)
                                    .background(isTorchOn ? Color.yellow : Color.black.opacity(0.55))
                                    .foregroundColor(isTorchOn ? .black : .white)
                                    .clipShape(Circle())
                                    .contentShape(Circle())
                            }
                            .buttonStyle(HrpPressableButtonStyle())
                        }
                        .padding(.bottom, 12)
                    }
                }
                .frame(height: 320)
                .padding(.horizontal, 16)

                Text(statusMessage)
                    .font(.caption.weight(.medium))
                    .foregroundColor(SemanticColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)

                // Pick QR from Photo Library (`Vision` barcode detection on UIImage)
                PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                    HStack(spacing: 8) {
                        Image(systemName: "photo.on.rectangle.angled")
                        Text("Chọn ảnh CCCD có mã QR từ Thư viện")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(Color(hex: 0xEBF3FB))
                    .foregroundColor(Color(hex: 0x1E6BB8))
                    .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous))
                }
                .padding(.horizontal, 16)
                .onChange(of: selectedPhotoItem) { newItem in
                    guard let newItem else { return }
                    Task {
                        if let data = try? await newItem.loadTransferable(type: Data.self),
                           let raw = Self.detectQrFromImageData(data) {
                            handleRawCandidate(raw)
                        } else {
                            HapticsManager.error()
                            statusMessage = "Không tìm thấy mã QR hợp lệ trên ảnh đã chọn."
                        }
                    }
                }

                // Fallback Manual Paste / Testing Input
                VStack(alignment: .leading, spacing: 6) {
                    Text("Hoặc dán chuỗi QR CCCD trực tiếp:")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(SemanticColors.textSecondary)
                    HStack {
                        TextField("034098001234||Nguyễn Văn A|...", text: $manualQrInput)
                            .submitLabel(.done)
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundColor(SemanticColors.textPrimary)
                            .padding(10)
                            .background(SemanticColors.surfaceMuted)
                            .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                        Button("Áp dụng") {
                            KeyboardDismissal.dismiss()
                            handleRawCandidate(manualQrInput)
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 14)
                        .frame(minHeight: 40)
                        .background(Color(hex: 0x1E6BB8))
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusXS, style: .continuous))
                    }
                }
                .padding(.horizontal, 16)

                Spacer()
            }
            .padding(.top, 12)
            .dismissKeyboardOnTapOutside()
            .navigationTitle("Quét mã QR CCCD")
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
        .hrpSheetPresentation(detents: [.large])
    }

    private func handleRawCandidate(_ raw: String) {
        if let parsed = CccdQrDecoder.parse(raw) {
            HapticsManager.success()
            onDecoded(raw, parsed)
            dismiss()
        } else {
            HapticsManager.error()
            statusMessage = "Mã QR không đúng định dạng CCCD Việt Nam. Vui lòng thử lại."
        }
    }

    public static func detectQrFromImageData(_ data: Data) -> String? {
        guard let cgImage = UIImage(data: data)?.cgImage else { return nil }
        var detectedPayload: String?
        let request = VNDetectBarcodesRequest { req, _ in
            guard let results = req.results as? [VNBarcodeObservation] else { return }
            for obs in results {
                if let payload = obs.payloadStringValue, CccdQrDecoder.parse(payload) != nil {
                    detectedPayload = payload
                    break
                }
            }
            if detectedPayload == nil {
                detectedPayload = results.compactMap { $0.payloadStringValue }.first
            }
        }
        request.symbologies = [.qr]
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([request])
        return detectedPayload
    }
}

// MARK: - UIKit AVCaptureSession Wrapper

struct CccdCameraPreviewRepresentable: UIViewControllerRepresentable {
    let zoomFactor: CGFloat
    let isTorchOn: Bool
    let onRawQrDetected: (String) -> Void

    func makeUIViewController(context: Context) -> CccdCameraViewController {
        let vc = CccdCameraViewController()
        vc.onRawQrDetected = onRawQrDetected
        return vc
    }

    func updateUIViewController(_ uiViewController: CccdCameraViewController, context: Context) {
        uiViewController.applyZoomAndTorch(zoomFactor: zoomFactor, isTorchOn: isTorchOn)
    }
}

final class CccdCameraViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onRawQrDetected: ((String) -> Void)?

    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var videoDevice: AVCaptureDevice?
    private var hasDeliveredResult = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureCaptureSession()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }

    private func configureCaptureSession() {
        guard AVCaptureDevice.authorizationStatus(for: .video) != .restricted else { return }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              captureSession.canAddInput(input) else {
            return
        }

        videoDevice = device
        captureSession.beginConfiguration()
        if captureSession.canSetSessionPreset(.high) {
            captureSession.sessionPreset = .high
        }
        captureSession.addInput(input)

        let metadataOutput = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(metadataOutput) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            if metadataOutput.availableMetadataObjectTypes.contains(.qr) {
                metadataOutput.metadataObjectTypes = [.qr]
            }
        }
        captureSession.commitConfiguration()

        let layer = AVCaptureVideoPreviewLayer(session: captureSession)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        previewLayer = layer

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }

    func applyZoomAndTorch(zoomFactor: CGFloat, isTorchOn: Bool) {
        guard let device = videoDevice else { return }
        do {
            try device.lockForConfiguration()
            let clampedZoom = min(max(1.0, zoomFactor), min(4.0, device.activeFormat.videoMaxZoomFactor))
            device.videoZoomFactor = clampedZoom
            if device.hasTorch {
                device.torchMode = isTorchOn ? .on : .off
            }
            device.unlockForConfiguration()
        } catch {
            // Ignore configuration lock error on simulator
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !hasDeliveredResult else { return }
        for obj in metadataObjects {
            if let readable = obj as? AVMetadataMachineReadableCodeObject,
               let rawValue = readable.stringValue,
               CccdQrDecoder.parse(rawValue) != nil {
                hasDeliveredResult = true
                captureSession.stopRunning()
                onRawQrDetected?(rawValue)
                break
            }
        }
    }
}

// MARK: - Viewfinder Overlay with Dimmed Cutout & 4 Corner Brackets

private struct QrViewfinderOverlay: View {
    let cutoutSize: CGFloat

    var body: some View {
        GeometryReader { geo in
            let rect = CGRect(
                x: (geo.size.width - cutoutSize) / 2,
                y: (geo.size.height - cutoutSize) / 2 - 14,
                width: cutoutSize,
                height: cutoutSize
            )

            ZStack {
                Path { path in
                    path.addRect(CGRect(origin: .zero, size: geo.size))
                    path.addRoundedRect(
                        in: rect,
                        cornerSize: CGSize(width: HrpDesignTokens.radiusCard, height: HrpDesignTokens.radiusCard)
                    )
                }
                .fill(Color.black.opacity(0.42), style: FillStyle(eoFill: true))

                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusCard, style: .continuous)
                    .stroke(Color.white.opacity(0.32), lineWidth: 1)
                    .frame(width: cutoutSize, height: cutoutSize)
                    .position(x: rect.midX, y: rect.midY)

                QrCornerBracketsShape(cornerRadius: HrpDesignTokens.radiusCard, armLength: 28)
                    .stroke(Color(hex: 0x22C55E), style: StrokeStyle(lineWidth: 3.5, lineCap: .round, lineJoin: .round))
                    .frame(width: cutoutSize, height: cutoutSize)
                    .position(x: rect.midX, y: rect.midY)
            }
        }
        .allowsHitTesting(false)
        .clipShape(RoundedRectangle(cornerRadius: HrpDesignTokens.radiusHero, style: .continuous))
    }
}

private struct QrCornerBracketsShape: Shape {
    let cornerRadius: CGFloat
    let armLength: CGFloat

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let r = min(cornerRadius, min(rect.width, rect.height) / 4)
        let l = min(armLength, min(rect.width, rect.height) / 2 - r)

        // Top-Left
        p.move(to: CGPoint(x: rect.minX, y: rect.minY + r + l))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r))
        p.addQuadCurve(to: CGPoint(x: rect.minX + r, y: rect.minY), control: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.minX + r + l, y: rect.minY))

        // Top-Right
        p.move(to: CGPoint(x: rect.maxX - r - l, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        p.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.minY + r), control: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + r + l))

        // Bottom-Right
        p.move(to: CGPoint(x: rect.maxX, y: rect.maxY - r - l))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
        p.addQuadCurve(to: CGPoint(x: rect.maxX - r, y: rect.maxY), control: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.maxX - r - l, y: rect.maxY))

        // Bottom-Left
        p.move(to: CGPoint(x: rect.minX + r + l, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
        p.addQuadCurve(to: CGPoint(x: rect.minX, y: rect.maxY - r), control: CGPoint(x: rect.minX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - r - l))

        return p
    }
}
