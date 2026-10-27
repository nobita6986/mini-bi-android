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
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    // Reticle Overlay
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.green, lineWidth: 2.5)
                        .frame(width: 230, height: 230)

                    VStack {
                        Spacer()
                        HStack(spacing: 10) {
                            ForEach([1.0, 1.5, 2.0], id: \.self) { zoom in
                                Button {
                                    selectedZoomFactor = CGFloat(zoom)
                                } label: {
                                    Text(String(format: "%.1fx", zoom))
                                        .font(.caption.weight(.bold))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(selectedZoomFactor == CGFloat(zoom) ? Color.white : Color.black.opacity(0.55))
                                        .foregroundColor(selectedZoomFactor == CGFloat(zoom) ? .black : .white)
                                        .clipShape(Capsule())
                                }
                            }

                            Button {
                                isTorchOn.toggle()
                            } label: {
                                Image(systemName: isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                                    .font(.caption.weight(.bold))
                                    .padding(8)
                                    .background(isTorchOn ? Color.yellow : Color.black.opacity(0.55))
                                    .foregroundColor(isTorchOn ? .black : .white)
                                    .clipShape(Circle())
                            }
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
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(hex: 0xEBF3FB))
                    .foregroundColor(Color(hex: 0x1E6BB8))
                    .cornerRadius(12)
                }
                .padding(.horizontal, 16)
                .onChange(of: selectedPhotoItem) { newItem in
                    guard let newItem else { return }
                    Task {
                        if let data = try? await newItem.loadTransferable(type: Data.self),
                           let raw = Self.detectQrFromImageData(data) {
                            handleRawCandidate(raw)
                        } else {
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
                            .font(.caption)
                            .padding(10)
                            .background(Color(hex: 0xF8FAFC))
                            .cornerRadius(8)
                        Button("Áp dụng") {
                            handleRawCandidate(manualQrInput)
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(Color(hex: 0x1E6BB8))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    }
                }
                .padding(.horizontal, 16)

                Spacer()
            }
            .padding(.top, 12)
            .navigationTitle("Quét mã QR CCCD")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") { dismiss() }
                }
            }
        }
    }

    private func handleRawCandidate(_ raw: String) {
        if let parsed = CccdQrDecoder.parse(raw) {
            onDecoded(raw, parsed)
            dismiss()
        } else {
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
