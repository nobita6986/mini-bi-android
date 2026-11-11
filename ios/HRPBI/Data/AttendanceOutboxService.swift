import Foundation
import UIKit
import CryptoKit
import Network
import CoreImage
import Vision

// MARK: - Attendance Beauty Filter (Post-Capture CoreImage + Vision Portrait Enhancement)

public enum AttendanceBeautyPreset: String, CaseIterable, Identifiable, Sendable {
    case original = "ORIGINAL"
    case natural = "NATURAL"
    case brightSmooth = "BRIGHT_SMOOTH"
    case rosy = "ROSY"

    public var id: String { rawValue }

    public var labelVi: String {
        switch self {
        case .original:
            return "Gốc"
        case .natural:
            return "Tự nhiên"
        case .brightSmooth:
            return "Sáng mịn"
        case .rosy:
            return "Hồng hào"
        }
    }

    public var iconName: String {
        switch self {
        case .original:
            return "camera.aperture"
        case .natural:
            return "sparkles"
        case .brightSmooth:
            return "sun.max.fill"
        case .rosy:
            return "wand.and.stars"
        }
    }
}

public enum AttendanceBeautyFilter {
    private static let presetDefaultsKey = "hrpbi.attendance.beauty_preset.v1"
    private static let sharedCIContext: CIContext = {
        CIContext(options: [
            .useSoftwareRenderer: false,
            .priorityRequestLow: false
        ])
    }()

    public static func loadSavedPreset(defaults: UserDefaults = .standard) -> AttendanceBeautyPreset {
        guard let raw = defaults.string(forKey: presetDefaultsKey),
              let preset = AttendanceBeautyPreset(rawValue: raw) else {
            return .natural
        }
        return preset
    }

    public static func savePreset(_ preset: AttendanceBeautyPreset, defaults: UserDefaults = .standard) {
        defaults.set(preset.rawValue, forKey: presetDefaultsKey)
    }

    /// Applies the selected portrait beauty preset to a raw/normalized JPEG image.
    /// Guarantees upright orientation and bounded dimensions (`maxDimensionPx`),
    /// returning valid JPEG bytes ready for preview and SHA-256 check-in submission.
    public static func apply(
        to rawData: Data,
        preset: AttendanceBeautyPreset,
        maxDimensionPx: CGFloat = 1600,
        compressionQuality: CGFloat = 0.85
    ) -> Data {
        let normalizedData = AttendanceContract.normalizeCameraJpeg(
            rawData,
            maxDimensionPx: maxDimensionPx,
            compressionQuality: max(compressionQuality, 0.88)
        )
        guard preset != .original else {
            if compressionQuality < 0.88 {
                return AttendanceContract.normalizeCameraJpeg(
                    normalizedData,
                    maxDimensionPx: maxDimensionPx,
                    compressionQuality: compressionQuality
                )
            }
            return normalizedData
        }

        guard let uprightImage = UIImage(data: normalizedData),
              let baseCIImage = CIImage(image: uprightImage) else {
            return normalizedData
        }

        let extent = baseCIImage.extent
        guard !extent.isEmpty, extent.width > 1, extent.height > 1 else {
            return normalizedData
        }

        let params = filterParameters(for: preset)
        var working = baseCIImage

        // 1. Exposure & Backlight Shadow Recovery
        if let exposure = CIFilter(name: "CIExposureAdjust") {
            exposure.setValue(working, forKey: kCIInputImageKey)
            exposure.setValue(params.exposureEV, forKey: kCIInputEVKey)
            if let out = exposure.outputImage {
                working = out
            }
        }

        if let shadowHighlight = CIFilter(name: "CIHighlightShadowAdjust") {
            shadowHighlight.setValue(working, forKey: kCIInputImageKey)
            shadowHighlight.setValue(params.shadowAmount, forKey: "inputShadowAmount")
            shadowHighlight.setValue(params.highlightAmount, forKey: "inputHighlightAmount")
            if let out = shadowHighlight.outputImage {
                working = out
            }
        }

        // 2. Skin Smoothing (Noise reduction + soft frequency blend + face-aware mask boost)
        var denoised = working
        if let nr = CIFilter(name: "CINoiseReduction") {
            nr.setValue(working, forKey: kCIInputImageKey)
            nr.setValue(0.025, forKey: "inputNoiseLevel")
            nr.setValue(0.40, forKey: "inputSharpness")
            if let out = nr.outputImage?.cropped(to: extent) {
                denoised = out
            }
        }

        if let blur = CIFilter(name: "CIGaussianBlur") {
            blur.setValue(denoised.clampedToExtent(), forKey: kCIInputImageKey)
            blur.setValue(params.blurRadius, forKey: kCIInputRadiusKey)
            if let blurred = blur.outputImage?.cropped(to: extent) {
                if let faceMask = buildFaceSmoothingMask(for: baseCIImage, extent: extent, baseOpacity: params.smoothingBlend, facePeakOpacity: min(0.75, params.smoothingBlend + 0.18)),
                   let blendMask = CIFilter(name: "CIBlendWithMask") {
                    blendMask.setValue(blurred, forKey: kCIInputImageKey)
                    blendMask.setValue(denoised, forKey: kCIInputBackgroundImageKey)
                    blendMask.setValue(faceMask, forKey: kCIInputMaskImageKey)
                    if let out = blendMask.outputImage?.cropped(to: extent) {
                        working = out
                    }
                } else if let dissolve = CIFilter(name: "CIDissolveTransition") {
                    dissolve.setValue(denoised, forKey: kCIInputImageKey)
                    dissolve.setValue(blurred, forKey: kCIInputTargetImageKey)
                    dissolve.setValue(params.smoothingBlend, forKey: kCIInputTimeKey)
                    if let out = dissolve.outputImage?.cropped(to: extent) {
                        working = out
                    }
                }
            }
        }

        // Restore crispness on eyes, eyebrows, hair, and facial contours
        if let sharpen = CIFilter(name: "CISharpenLuminance") {
            sharpen.setValue(working, forKey: kCIInputImageKey)
            sharpen.setValue(params.sharpenLuminance, forKey: kCIInputSharpnessKey)
            sharpen.setValue(1.5, forKey: kCIInputRadiusKey)
            if let out = sharpen.outputImage?.cropped(to: extent) {
                working = out
            }
        }

        // 3. Warm / Rosy Skin Tone & Soft Contrast
        if let tempTint = CIFilter(name: "CITemperatureAndTint") {
            tempTint.setValue(working, forKey: kCIInputImageKey)
            tempTint.setValue(CIVector(x: 6500, y: 0), forKey: "inputNeutral")
            tempTint.setValue(CIVector(x: params.targetTemp, y: params.targetTint), forKey: "inputTargetNeutral")
            if let out = tempTint.outputImage?.cropped(to: extent) {
                working = out
            }
        }

        if let colorControls = CIFilter(name: "CIColorControls") {
            colorControls.setValue(working, forKey: kCIInputImageKey)
            colorControls.setValue(params.brightness, forKey: kCIInputBrightnessKey)
            colorControls.setValue(params.contrast, forKey: kCIInputContrastKey)
            colorControls.setValue(params.saturation, forKey: kCIInputSaturationKey)
            if let out = colorControls.outputImage?.cropped(to: extent) {
                working = out
            }
        }

        guard let cgImage = sharedCIContext.createCGImage(working, from: extent) else {
            return normalizedData
        }

        let filteredUIImage = UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
        if let jpeg = filteredUIImage.jpegData(compressionQuality: compressionQuality), !jpeg.isEmpty {
            return jpeg
        }
        return normalizedData
    }

    private struct FilterParameters {
        let exposureEV: CGFloat
        let shadowAmount: CGFloat
        let highlightAmount: CGFloat
        let blurRadius: CGFloat
        let smoothingBlend: CGFloat
        let sharpenLuminance: CGFloat
        let targetTemp: CGFloat
        let targetTint: CGFloat
        let brightness: CGFloat
        let contrast: CGFloat
        let saturation: CGFloat
    }

    private static func filterParameters(for preset: AttendanceBeautyPreset) -> FilterParameters {
        switch preset {
        case .original:
            return FilterParameters(
                exposureEV: 0.0,
                shadowAmount: 0.0,
                highlightAmount: 1.0,
                blurRadius: 0.0,
                smoothingBlend: 0.0,
                sharpenLuminance: 0.0,
                targetTemp: 6500,
                targetTint: 0,
                brightness: 0.0,
                contrast: 1.0,
                saturation: 1.0
            )
        case .natural:
            return FilterParameters(
                exposureEV: 0.16,
                shadowAmount: 0.25,
                highlightAmount: 0.92,
                blurRadius: 2.8,
                smoothingBlend: 0.34,
                sharpenLuminance: 0.32,
                targetTemp: 6320,
                targetTint: -6,
                brightness: 0.015,
                contrast: 0.975,
                saturation: 1.04
            )
        case .brightSmooth:
            return FilterParameters(
                exposureEV: 0.30,
                shadowAmount: 0.38,
                highlightAmount: 0.88,
                blurRadius: 4.2,
                smoothingBlend: 0.50,
                sharpenLuminance: 0.38,
                targetTemp: 6380,
                targetTint: -8,
                brightness: 0.030,
                contrast: 0.960,
                saturation: 1.02
            )
        case .rosy:
            return FilterParameters(
                exposureEV: 0.22,
                shadowAmount: 0.30,
                highlightAmount: 0.90,
                blurRadius: 3.2,
                smoothingBlend: 0.40,
                sharpenLuminance: 0.34,
                targetTemp: 6100,
                targetTint: -18,
                brightness: 0.020,
                contrast: 0.975,
                saturation: 1.10
            )
        }
    }

    /// Detects faces via Vision and builds a soft radial mask that boosts skin smoothing
    /// on facial regions while keeping a baseline smoothing level across the rest of the frame.
    private static func buildFaceSmoothingMask(
        for image: CIImage,
        extent: CGRect,
        baseOpacity: CGFloat,
        facePeakOpacity: CGFloat
    ) -> CIImage? {
        let request = VNDetectFaceRectanglesRequest()
        let handler = VNImageRequestHandler(ciImage: image, options: [:])
        do {
            try handler.perform([request])
        } catch {
            return nil
        }

        guard let faces = request.results, !faces.isEmpty else {
            return nil
        }

        let baseColor = CIColor(red: baseOpacity, green: baseOpacity, blue: baseOpacity, alpha: 1.0)
        let peakColor = CIColor(red: facePeakOpacity, green: facePeakOpacity, blue: facePeakOpacity, alpha: 1.0)
        var compositeMask = CIImage(color: baseColor).cropped(to: extent)

        for face in faces {
            let bbox = face.boundingBox
            let centerX = extent.minX + bbox.midX * extent.width
            let centerY = extent.minY + bbox.midY * extent.height
            let faceRadius = max(bbox.width * extent.width, bbox.height * extent.height) * 0.72
            guard faceRadius > 8,
                  let radial = CIFilter(name: "CIRadialGradient") else {
                continue
            }
            radial.setValue(CIVector(x: centerX, y: centerY), forKey: "inputCenter")
            radial.setValue(faceRadius * 0.45, forKey: "inputRadius0")
            radial.setValue(faceRadius * 1.15, forKey: "inputRadius1")
            radial.setValue(peakColor, forKey: "inputColor0")
            radial.setValue(baseColor, forKey: "inputColor1")
            if let faceGradient = radial.outputImage?.cropped(to: extent),
               let lighten = CIFilter(name: "CIMaximumCompositing") {
                lighten.setValue(faceGradient, forKey: kCIInputImageKey)
                lighten.setValue(compositeMask, forKey: kCIInputBackgroundImageKey)
                if let merged = lighten.outputImage?.cropped(to: extent) {
                    compositeMask = merged
                }
            }
        }

        return compositeMask
    }
}

// MARK: - Attendance Contract & Offline Outbox Service
// Mirrors Android's `data/AttendanceOutboxService.kt` 100%

public enum AttendanceContract {
    public static let statusPending = "PENDING"
    public static let statusUploading = "UPLOADING"
    public static let statusSynced = "SYNCED"
    public static let statusFailedRetryable = "FAILED_RETRYABLE"
    public static let statusFailedPermanent = "FAILED_PERMANENT"

    public static func statusDisplayLabel(_ status: String) -> String {
        switch status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case statusPending:
            return "Đang chờ gửi"
        case statusUploading:
            return "Đang đồng bộ..."
        case statusSynced:
            return "Đã đồng bộ"
        case statusFailedRetryable:
            return "Chờ mạng / Thử lại"
        case statusFailedPermanent:
            return "Bị từ chối (cần chụp lại)"
        default:
            return status
        }
    }

    public static func isRetryableStatus(_ status: String) -> Bool {
        let normalized = status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return normalized == statusPending || normalized == statusFailedRetryable
    }

    public static func computeSha256Hex(_ data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public static func normalizeCameraJpeg(_ rawData: Data, maxDimensionPx: CGFloat = 1600, compressionQuality: CGFloat = 0.82) -> Data {
        guard let image = UIImage(data: rawData),
              image.size.width > 0,
              image.size.height > 0 else {
            return rawData
        }
        let maxSide = max(image.size.width, image.size.height)
        let targetSize: CGSize
        if maxSide > maxDimensionPx {
            let ratio = maxDimensionPx / maxSide
            targetSize = CGSize(
                width: max(1, floor(image.size.width * ratio)),
                height: max(1, floor(image.size.height * ratio))
            )
        } else {
            targetSize = image.size
        }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        let rendered = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
        if let jpeg = rendered.jpegData(compressionQuality: compressionQuality), !jpeg.isEmpty {
            return jpeg
        }
        return rawData
    }
}

public struct AttendanceOutboxFlushSummary: Equatable {
    public let processedCount: Int
    public let syncedCount: Int
    public let retryableFailureCount: Int
    public let remainingRetryableCount: Int
    public let lastFailureHttpStatus: Int?

    public init(
        processedCount: Int,
        syncedCount: Int,
        retryableFailureCount: Int,
        remainingRetryableCount: Int,
        lastFailureHttpStatus: Int? = nil
    ) {
        self.processedCount = processedCount
        self.syncedCount = syncedCount
        self.retryableFailureCount = retryableFailureCount
        self.remainingRetryableCount = remainingRetryableCount
        self.lastFailureHttpStatus = lastFailureHttpStatus
    }
}

public final class AttendanceOutboxService {
    public static let shared = AttendanceOutboxService()

    private let httpClient: BackendHttpClient
    private let timeAnchorManager: AttendanceTimeAnchorManager
    private let storageDirectory: URL
    private let outboxFileUrl: URL
    private let photosDirectory: URL
    private let lock = NSLock()
    private var items: [AttendanceOutboxItem] = []
    private var inFlightLocalIds: Set<String> = []
    private var pathMonitor: NWPathMonitor?
    private let monitorQueue = DispatchQueue(label: "com.aistudio.minibi.attendance.outbox.monitor")

    public var onOutboxChanged: (() -> Void)?

    public init(
        httpClient: BackendHttpClient = .shared,
        timeAnchorManager: AttendanceTimeAnchorManager = .shared,
        customStorageDirectory: URL? = nil
    ) {
        self.httpClient = httpClient
        self.timeAnchorManager = timeAnchorManager

        let baseDir: URL
        if let customStorageDirectory {
            baseDir = customStorageDirectory
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            baseDir = appSupport.appendingPathComponent("HRPBI_Attendance", isDirectory: true)
        }

        self.storageDirectory = baseDir
        self.outboxFileUrl = baseDir.appendingPathComponent("attendance_outbox_v1.json")
        self.photosDirectory = baseDir.appendingPathComponent("photos", isDirectory: true)

        try? FileManager.default.createDirectory(at: baseDir, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: photosDirectory, withIntermediateDirectories: true)

        let loaded = Self.loadItems(from: outboxFileUrl)
        let deduplicated = Self.deduplicateItems(loaded)
        self.items = deduplicated
        if deduplicated.count != loaded.count {
            Self.saveItems(deduplicated, to: outboxFileUrl)
        }
        recoverInterruptedUploads()
    }

    public func startNetworkAutoFlush(
        userKeyProvider: @escaping () -> String?,
        customFlushHandler: (() async -> Void)? = nil
    ) {
        stopNetworkAutoFlush()
        let monitor = NWPathMonitor()
        var hasObservedInitialPath = false
        var wasSatisfied = false
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let isNowSatisfied = (path.status == .satisfied)
            if !hasObservedInitialPath {
                hasObservedInitialPath = true
                wasSatisfied = isNowSatisfied
                return
            }
            let transitionedToOnline = !wasSatisfied && isNowSatisfied
            wasSatisfied = isNowSatisfied
            guard transitionedToOnline else { return }
            guard let userKey = userKeyProvider(), !userKey.isEmpty else { return }
            Task {
                if let customFlushHandler {
                    await customFlushHandler()
                } else {
                    let summary = await self.flushRetryableOutboxWithSummary(userKey: userKey)
                    if summary.syncedCount > 0 {
                        _ = await self.syncServerHistoryForUser(userKey: userKey)
                    }
                }
            }
        }
        monitor.start(queue: monitorQueue)
        pathMonitor = monitor
    }

    public func stopNetworkAutoFlush() {
        pathMonitor?.cancel()
        pathMonitor = nil
    }

    public func listItemsForUser(userKey: String) -> [AttendanceOutboxItem] {
        let normalizedKey = userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        lock.lock()
        defer { lock.unlock() }
        return items
            .filter { $0.userKey.lowercased() == normalizedKey }
            .sorted { $0.createdAtEpochMs > $1.createdAtEpochMs }
    }

    public func pendingCountForUser(userKey: String) -> Int {
        listItemsForUser(userKey: userKey).filter {
            AttendanceContract.isRetryableStatus($0.status) || $0.status == AttendanceContract.statusUploading
        }.count
    }

    public func todaySyncedCountForUser(userKey: String) -> Int {
        countTodaySyncedItems(listItemsForUser(userKey: userKey))
    }

    // MARK: - Enqueue & Immediate Attempt

    public func submitCheckIn(
        userKey: String,
        lat: Double,
        lng: Double,
        accuracyM: Double?,
        locationCapturedAt: String?,
        photoData: Data,
        mimeType: String = "image/jpeg",
        note: String = "",
        captureTimestampOverride: CapturedAttendanceTimestamp? = nil
    ) async -> (item: AttendanceOutboxItem, apiResult: MiniBiApiResult<AttendanceOutboxItem>) {
        let localId = UUID().uuidString.lowercased()
        let idempotencyKey = localId
        let capturedTime = captureTimestampOverride ?? timeAnchorManager.resolveCaptureTime()
        let normalizedPhotoData = AttendanceContract.normalizeCameraJpeg(photoData)
        let photoSha256 = AttendanceContract.computeSha256Hex(normalizedPhotoData)

        let ext = "jpg"
        let photoFileUrl = photosDirectory.appendingPathComponent("\(localId).\(ext)")
        try? normalizedPhotoData.write(to: photoFileUrl, options: .atomic)

        let safeAccuracy: Double = {
            if let acc = accuracyM, acc.isFinite && acc >= 0 {
                return acc
            }
            return 15.0
        }()

        let initialItem = AttendanceOutboxItem(
            localId: localId,
            userKey: userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            idempotencyKey: idempotencyKey,
            clientCapturedAt: capturedTime.capturedAtIso,
            timeSource: capturedTime.timeSource,
            timeToken: capturedTime.timeToken,
            anchorSyncedAtEpochMs: capturedTime.anchorSyncedAtEpochMs,
            elapsedSinceAnchorMs: capturedTime.elapsedSinceAnchorMs,
            deviceClockSkewMs: capturedTime.deviceClockSkewMs,
            lat: lat,
            lng: lng,
            accuracyM: safeAccuracy,
            locationCapturedAt: locationCapturedAt ?? capturedTime.capturedAtIso,
            photoLocalPath: photoFileUrl.path,
            photoSha256: photoSha256,
            photoSizeBytes: Int64(normalizedPhotoData.count),
            mimeType: "image/jpeg",
            note: note.trimmingCharacters(in: .whitespacesAndNewlines),
            status: AttendanceContract.statusPending,
            attemptCount: 0,
            createdAtEpochMs: capturedTime.capturedEpochMs
        )

        upsertItem(initialItem)
        let sendResult = await processSingleOutboxItem(localId: localId)
        if case .success = sendResult {
            _ = await flushRetryableOutbox(userKey: userKey)
        }
        let currentItem = getItem(localId: localId) ?? initialItem
        return (currentItem, sendResult)
    }

    // MARK: - Flush Retryable Outbox

    public func flushRetryableOutboxWithSummary(userKey: String) async -> AttendanceOutboxFlushSummary {
        recoverInterruptedUploads()
        let candidates = listItemsForUser(userKey: userKey).filter {
            AttendanceContract.isRetryableStatus($0.status)
        }.sorted { $0.createdAtEpochMs < $1.createdAtEpochMs }

        var processedCount = 0
        var syncedCount = 0
        var retryableFailureCount = 0
        var lastFailureHttpStatus: Int?

        for candidate in candidates {
            processedCount += 1
            let result = await processSingleOutboxItem(localId: candidate.localId)
            switch result {
            case .success(let updated, _):
                if updated.status == AttendanceContract.statusSynced {
                    syncedCount += 1
                }
            case .failure(_, let httpStatus, _, _):
                let current = getItem(localId: candidate.localId)
                let isRetryable = current.map { AttendanceContract.isRetryableStatus($0.status) }
                    ?? AttendanceRetryBackoffPolicy.isRetryableFailure(httpStatus: httpStatus)
                if isRetryable {
                    retryableFailureCount += 1
                    lastFailureHttpStatus = httpStatus
                    break
                }
            }
        }

        let remainingRetryableCount = pendingCountForUser(userKey: userKey)
        return AttendanceOutboxFlushSummary(
            processedCount: processedCount,
            syncedCount: syncedCount,
            retryableFailureCount: retryableFailureCount,
            remainingRetryableCount: remainingRetryableCount,
            lastFailureHttpStatus: lastFailureHttpStatus
        )
    }

    public func flushRetryableOutbox(userKey: String) async -> Int {
        let summary = await flushRetryableOutboxWithSummary(userKey: userKey)
        return summary.syncedCount
    }

    // MARK: - Process Single Item

    public func processSingleOutboxItem(localId: String) async -> MiniBiApiResult<AttendanceOutboxItem> {
        lock.lock()
        if inFlightLocalIds.contains(localId) {
            let current = items.first(where: { $0.localId == localId })
            lock.unlock()
            if let current {
                return .success(current)
            }
            return .failure(message: "Bản ghi chấm công đang được đồng bộ.")
        }
        inFlightLocalIds.insert(localId)
        lock.unlock()
        defer {
            lock.lock()
            inFlightLocalIds.remove(localId)
            lock.unlock()
        }

        guard var item = getItem(localId: localId) else {
            return .failure(message: "Không tìm thấy bản ghi chấm công trong hàng đợi.")
        }

        if item.status == AttendanceContract.statusSynced {
            return .success(item)
        }

        item.status = AttendanceContract.statusUploading
        item.attemptCount += 1
        upsertItem(item)

        guard let localPath = item.photoLocalPath,
              let photoData = try? Data(contentsOf: URL(fileURLWithPath: localPath)),
              !photoData.isEmpty else {
            item.status = AttendanceContract.statusFailedPermanent
            item.lastErrorMessage = "Không tìm thấy tệp ảnh gốc để tải lên. Vui lòng chụp lại."
            upsertItem(item)
            return .failure(message: item.lastErrorMessage ?? "Thiếu tệp ảnh.", errorCode: "MISSING_LOCAL_PHOTO")
        }

        // Normalize idempotencyKey to pure UUID if any legacy item had "att-" prefix
        let cleanIdempotencyKey: String = {
            if item.idempotencyKey.hasPrefix("att-") {
                return String(item.idempotencyKey.dropFirst(4))
            }
            return item.idempotencyKey
        }()

        // Build canonical `metadata` JSON object per `check-in-contract.ts` (ALLOWED_METADATA_KEYS)
        // Mirrors Android's `AttendanceOutboxService.buildMetadataJson` 100%:
        // accuracy_meters is always a finite non-negative Double, address_hint & note are non-null Strings,
        // and time_token / elapsed_since_anchor_ms are only included when SERVER_ANCHORED.
        let safeAccuracy: Double = {
            if let acc = item.accuracyM, acc.isFinite && acc >= 0 {
                return acc
            }
            return 15.0
        }()
        var metadataObj: [String: Any] = [
            "latitude": item.lat,
            "longitude": item.lng,
            "accuracy_meters": safeAccuracy,
            "address_hint": "",
            "client_captured_at": item.clientCapturedAt,
            "note": item.note,
            "idempotency_key": cleanIdempotencyKey,
            "photo_sha256": item.photoSha256,
            "time_source": item.timeSource
        ]
        if item.timeSource == AttendanceTimeContract.sourceServerAnchored,
           let token = item.timeToken, !token.isEmpty,
           let elapsed = item.elapsedSinceAnchorMs, elapsed >= 0 {
            metadataObj["time_token"] = token
            metadataObj["elapsed_since_anchor_ms"] = elapsed
        }

        guard let metadataJsonData = try? JSONSerialization.data(withJSONObject: metadataObj),
              let metadataJsonString = String(data: metadataJsonData, encoding: .utf8) else {
            item.status = AttendanceContract.statusFailedPermanent
            item.lastErrorMessage = "Không thể đóng gói dữ liệu chấm công."
            upsertItem(item)
            return .failure(message: "Không thể đóng gói dữ liệu chấm công.")
        }

        let response = await httpClient.uploadMultipart(
            path: "/api/attendance/check-in",
            fieldName: "photo",
            fileName: "\(item.localId).jpg",
            mimeType: item.mimeType,
            fileData: photoData,
            extraFields: ["metadata": metadataJsonString],
            additionalHeaders: ["Idempotency-Key": cleanIdempotencyKey]
        )

        if response.isSuccess || (response.statusCode == 409 && isAlreadyProcessedConflict(response)) {
            let recordObj = (response.jsonObject?["checkin"] as? [String: Any])
                ?? (response.jsonObject?["check_in"] as? [String: Any])
                ?? (response.jsonObject?["item"] as? [String: Any])
                ?? (response.jsonObject?["data"] as? [String: Any])
                ?? response.jsonObject

            item.status = AttendanceContract.statusSynced
            item.serverRecordId = recordObj?.optInt64("checkin_id", "id", "check_in_id")
            item.serverCheckinId = recordObj?.optNullableString("checkin_id", "check_in_id", "id")
            item.serverReceivedAt = recordObj?.optNullableString("server_received_at", "created_at", "received_at")
                ?? AttendanceTimeAnchorManager.formatEpochMsAsIso8601Utc(Int64(Date().timeIntervalSince1970 * 1000))
            item.lastErrorCode = nil
            item.lastErrorMessage = nil

            // Keep local photo file on disk so the user immediately sees the check-in photo in the attendance list,
            // matching Android's `AttendanceOutboxService.kt` (`persistedFile`).
            upsertItem(item)
            return .success(item, statusCode: response.statusCode)
        } else {
            let isPermanent = isPermanentError(httpStatus: response.statusCode, errorCode: response.errorCode)
            item.status = isPermanent ? AttendanceContract.statusFailedPermanent : AttendanceContract.statusFailedRetryable
            item.lastErrorCode = response.errorCode
            item.lastErrorMessage = response.errorMessage ?? "Chưa thể gửi chấm công (HTTP \(response.statusCode))."
            upsertItem(item)
            return .failure(
                message: item.lastErrorMessage ?? "Lỗi gửi chấm công.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }
    }

    // MARK: - Server History Sync & Report Export

    public private(set) var lastHistorySyncHasMore: Bool = false
    public private(set) var lastHistorySyncTotalCount: Int = 0

    public static func vietnamDateIso(for item: AttendanceOutboxItem) -> String {
        let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(item.clientCapturedAt) ?? item.createdAtEpochMs
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: date)
    }

    public static func formatVietnamTimeHHmm(for item: AttendanceOutboxItem) -> String {
        let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(item.clientCapturedAt) ?? item.createdAtEpochMs
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "HH:mm"
        return df.string(from: date)
    }

    public func syncServerHistoryForUser(
        userKey: String,
        fromDate: String? = nil,
        toDate: String? = nil,
        page: Int = 1,
        pageSize: Int = 15,
        days: Int = 0
    ) async -> MiniBiApiResult<(items: [AttendanceOutboxItem], todayCount: Int)> {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        let now = Date()
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = cal.timeZone
        df.dateFormat = "yyyy-MM-dd"
        let todayStr = df.string(from: now)

        let resolvedFromDate: String
        let resolvedToDate: String
        if let f = fromDate?.trimmingCharacters(in: .whitespacesAndNewlines), !f.isEmpty {
            resolvedFromDate = f
            let t = toDate?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            resolvedToDate = t.isEmpty ? f : t
        } else if days > 0 {
            let start = cal.date(byAdding: .day, value: -days, to: now) ?? now
            resolvedFromDate = df.string(from: start)
            resolvedToDate = todayStr
        } else {
            resolvedFromDate = todayStr
            resolvedToDate = todayStr
        }

        let safePage = max(1, page)
        let safePageSize = max(1, min(pageSize, 100))
        let queryItems: [URLQueryItem] = [
            URLQueryItem(name: "from_date", value: resolvedFromDate),
            URLQueryItem(name: "to_date", value: resolvedToDate),
            URLQueryItem(name: "page", value: "\(safePage)"),
            URLQueryItem(name: "page_size", value: "\(safePageSize)")
        ]

        let response = await httpClient.request(
            method: "GET",
            path: "/api/attendance/history",
            queryItems: queryItems
        )
        guard response.isSuccess else {
            return .failure(
                message: response.errorMessage ?? "Không tải được lịch sử chấm công.",
                httpStatus: response.statusCode,
                errorCode: response.errorCode
            )
        }

        let root = response.jsonObject ?? [:]
        let reportObj = (root["report"] as? [String: Any])
            ?? (root["data"] as? [String: Any])
            ?? root
        let rawItems = reportObj.optObjectArray("items")
            .ifEmpty(root.optObjectArray("items"))
            .ifEmpty(root.optObjectArray("check_ins"))

        let totalRows = reportObj.optInt(
            "total", "total_rows", "total_count",
            defaultValue: root.optInt("total", "total_rows", "total_count", defaultValue: -1)
        )
        let totalPages = reportObj.optInt(
            "total_pages",
            defaultValue: root.optInt("total_pages", defaultValue: -1)
        )
        let explicitHasMore = (reportObj["has_more"] as? Bool) ?? (root["has_more"] as? Bool)
        let resolvedHasMore: Bool
        if let explicitHasMore {
            resolvedHasMore = explicitHasMore
        } else if totalPages > 0 {
            resolvedHasMore = safePage < totalPages
        } else if totalRows >= 0 {
            resolvedHasMore = (safePage * safePageSize) < totalRows
        } else {
            resolvedHasMore = rawItems.count >= safePageSize
        }

        let todayServerCount = root.optInt("today_count", "today_check_in_count", defaultValue: -1)
        let normalizedKey = userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        lock.lock()
        lastHistorySyncHasMore = resolvedHasMore
        lastHistorySyncTotalCount = totalRows >= 0 ? totalRows : rawItems.count
        var currentList = items
        for obj in rawItems {
            let idempKey = obj.optString("idempotency_key", "client_id")
            let checkinId = obj.optString("checkin_id", "check_in_id", "id")
            let serverId = obj.optInt64("checkin_id", "id", "check_in_id")
            let capturedAt = obj.optString("client_captured_at", "captured_at", "created_at")
            let receivedAt = obj.optNullableString("server_received_at", "received_at", "created_at")
            let lat = obj.optDouble("lat", "latitude")
            let lng = obj.optDouble("lng", "longitude")
            let acc = obj.optNullableDouble("accuracy_m", "accuracy_meters", "accuracy")
            let note = obj.optString("note")
            let timeSource = obj.optString("time_source", defaultValue: AttendanceTimeContract.sourceServerAnchored)
            let photoSha = obj.optString("photo_sha256")
            let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(capturedAt)
                ?? Int64(Date().timeIntervalSince1970 * 1000)
            let stableSrvKey = !checkinId.isEmpty
                ? "srv-\(checkinId)"
                : "srv-\(serverId.map(String.init) ?? String(epochMs))"

            if let existingIdx = currentList.firstIndex(where: { existing in
                guard existing.userKey.lowercased() == normalizedKey else { return false }
                if !idempKey.isEmpty && existing.idempotencyKey == idempKey { return true }
                if !checkinId.isEmpty && (existing.serverCheckinId == checkinId || existing.localId == "srv-\(checkinId)" || existing.idempotencyKey == "srv-\(checkinId)") { return true }
                if let serverId, existing.serverRecordId == serverId { return true }
                if existing.localId == stableSrvKey || existing.localId == "srv-\(epochMs)" || existing.idempotencyKey == "srv-\(epochMs)" { return true }
                if !capturedAt.isEmpty && existing.clientCapturedAt == capturedAt { return true }
                if existing.status == AttendanceContract.statusSynced && abs(existing.createdAtEpochMs - epochMs) <= 2_000 { return true }
                return false
            }) {
                currentList[existingIdx].status = AttendanceContract.statusSynced
                currentList[existingIdx].serverRecordId = serverId ?? currentList[existingIdx].serverRecordId
                if !checkinId.isEmpty {
                    currentList[existingIdx].serverCheckinId = checkinId
                }
                currentList[existingIdx].serverReceivedAt = receivedAt ?? currentList[existingIdx].serverReceivedAt
            } else {
                let syncedItem = AttendanceOutboxItem(
                    localId: stableSrvKey,
                    userKey: normalizedKey,
                    idempotencyKey: idempKey.isEmpty ? stableSrvKey : idempKey,
                    clientCapturedAt: capturedAt,
                    timeSource: timeSource,
                    lat: lat,
                    lng: lng,
                    accuracyM: acc,
                    photoSha256: photoSha,
                    photoSizeBytes: obj.optInt64("photo_size_bytes") ?? 0,
                    mimeType: obj.optString("mime_type", defaultValue: "image/jpeg"),
                    note: note,
                    status: AttendanceContract.statusSynced,
                    attemptCount: 1,
                    serverRecordId: serverId,
                    serverCheckinId: checkinId.isEmpty ? nil : checkinId,
                    serverReceivedAt: receivedAt,
                    createdAtEpochMs: epochMs
                )
                currentList.append(syncedItem)
            }
        }
        items = Self.deduplicateItems(currentList)
        Self.saveItems(items, to: outboxFileUrl)
        lock.unlock()

        onOutboxChanged?()
        let userItems = listItemsForUser(userKey: userKey)
        let computedTodayCount = todayServerCount >= 0
            ? todayServerCount
            : countTodaySyncedItems(userItems)
        return .success((items: userItems, todayCount: computedTodayCount), statusCode: response.statusCode)
    }

    /// Exports an attendance report file (first tries server `/api/attendance/export` if available,
    /// otherwise generates a UTF-8 CSV with BOM from the user's synced & pending attendance records)
    /// so the user can share/save the file via iOS Share Sheet.
    public func exportAttendanceReport(userKey: String, startDate: String? = nil, endDate: String? = nil) async -> MiniBiApiResult<URL> {
        var queryItems: [URLQueryItem] = []
        if let startDate, !startDate.isEmpty {
            queryItems.append(URLQueryItem(name: "start_date", value: startDate))
        }
        if let endDate, !endDate.isEmpty {
            queryItems.append(URLQueryItem(name: "end_date", value: endDate))
        }

        let response = await httpClient.request(
            method: "GET",
            path: "/api/attendance/export",
            queryItems: queryItems
        )

        let timestamp = Int64(Date().timeIntervalSince1970)
        if response.isSuccess && !response.rawData.isEmpty && response.jsonObject == nil {
            let ext = (response.headers["Content-Type"] as? String)?.contains("csv") == true ? "csv" : "xlsx"
            let fileUrl = FileManager.default.temporaryDirectory.appendingPathComponent("bao-cao-cham-cong-\(timestamp).\(ext)")
            do {
                try response.rawData.write(to: fileUrl, options: .atomic)
                return .success(fileUrl, statusCode: response.statusCode)
            } catch {
                // Fall back to CSV generation below
            }
        }

        // Generate rich UTF-8 CSV with BOM from local + synced records for sharing
        let userItems = listItemsForUser(userKey: userKey)
        var lines: [String] = [
            "Mã định danh,Thời gian chấm công,Nguồn giờ,Trạng thái,Vĩ độ (Lat),Kinh độ (Lng),Sai số GPS (m),Ghi chú,Thời gian máy chủ nhận"
        ]
        for record in userItems {
            let cols = [
                record.idempotencyKey,
                AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: record.clientCapturedAt, fallbackEpochMs: record.createdAtEpochMs),
                AttendanceTimeContract.timeSourceDisplayLabel(record.timeSource),
                AttendanceContract.statusDisplayLabel(record.status),
                String(format: "%.6f", record.lat),
                String(format: "%.6f", record.lng),
                record.accuracyM.map { String(format: "%.1f", $0) } ?? "",
                record.note,
                record.serverReceivedAt.map { AttendanceTimeAnchorManager.formatDisplayDateTimeVi(isoString: $0) } ?? ""
            ].map { col -> String in
                let escaped = col.replacingOccurrences(of: "\"", with: "\"\"")
                return "\"\(escaped)\""
            }
            lines.append(cols.joined(separator: ","))
        }

        let csvContent = "\u{FEFF}" + lines.joined(separator: "\r\n")
        let csvUrl = FileManager.default.temporaryDirectory.appendingPathComponent("bao-cao-cham-cong-\(timestamp).csv")
        do {
            guard let data = csvContent.data(using: .utf8) else {
                return .failure(message: "Không thể tạo dữ liệu CSV báo cáo chấm công.")
            }
            try data.write(to: csvUrl, options: .atomic)
            return .success(csvUrl)
        } catch {
            return .failure(message: "Không thể lưu tệp báo cáo chấm công: \(error.localizedDescription)")
        }
    }

    public func removeRejectedItem(localId: String) {
        lock.lock()
        if let idx = items.firstIndex(where: { $0.localId == localId }) {
            if let path = items[idx].photoLocalPath {
                try? FileManager.default.removeItem(atPath: path)
            }
            items.remove(at: idx)
            Self.saveItems(items, to: outboxFileUrl)
        }
        lock.unlock()
        onOutboxChanged?()
    }

    // MARK: - Attendance Photo Loading (Local Outbox File + Server `/api/attendance/checkins/{checkinId}/photo`)

    public func loadLocalPhotoData(for item: AttendanceOutboxItem) -> Data? {
        if let rawPath = item.photoLocalPath?.trimmingCharacters(in: .whitespacesAndNewlines), !rawPath.isEmpty {
            if let data = try? Data(contentsOf: URL(fileURLWithPath: rawPath)), !data.isEmpty {
                return data
            }
            // Fallback to current container's photosDirectory in case the iOS sandbox UUID changed after an app update
            let fileName = URL(fileURLWithPath: rawPath).lastPathComponent
            if !fileName.isEmpty {
                let rebasedUrl = photosDirectory.appendingPathComponent(fileName)
                if let data = try? Data(contentsOf: rebasedUrl), !data.isEmpty {
                    return data
                }
            }
        }
        let byLocalIdUrl = photosDirectory.appendingPathComponent("\(item.localId).jpg")
        if let data = try? Data(contentsOf: byLocalIdUrl), !data.isEmpty {
            return data
        }
        return nil
    }

    public static func resolveRemotePhotoPath(for item: AttendanceOutboxItem) -> String? {
        if let cid = item.serverCheckinId?.trimmingCharacters(in: .whitespacesAndNewlines), !cid.isEmpty {
            let encoded = cid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cid
            return "/api/attendance/checkins/\(encoded)/photo"
        }
        if item.localId.hasPrefix("srv-") {
            let cid = String(item.localId.dropFirst(4)).trimmingCharacters(in: .whitespacesAndNewlines)
            if !cid.isEmpty {
                let encoded = cid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cid
                return "/api/attendance/checkins/\(encoded)/photo"
            }
        }
        return nil
    }

    public func loadPhotoData(for item: AttendanceOutboxItem) async -> Data? {
        if let localData = loadLocalPhotoData(for: item) {
            return localData
        }
        guard let remotePath = Self.resolveRemotePhotoPath(for: item) else {
            return nil
        }
        let resp = await httpClient.fetchBinaryData(path: remotePath, useCache: true)
        guard resp.isSuccess && !resp.rawData.isEmpty else {
            return nil
        }
        // Cache downloaded photo locally for instant future display
        let cachedUrl = photosDirectory.appendingPathComponent("\(item.localId).jpg")
        try? resp.rawData.write(to: cachedUrl, options: .atomic)
        return resp.rawData
    }

    // MARK: - Internal Upload & Storage Helpers

    private func uploadAttendancePhoto(item: AttendanceOutboxItem, photoData: Data) async -> MiniBiApiResult<String> {
        // 1. Try presigned upload URL flow `/api/attendance/photos/upload-url` or `/api/attendance/photos`
        let stageBody: [String: Any] = [
            "idempotency_key": item.idempotencyKey,
            "file_name": "\(item.localId).jpg",
            "mime_type": item.mimeType,
            "file_size_bytes": item.photoSizeBytes,
            "photo_sha256": item.photoSha256
        ]

        let stageRes = await httpClient.request(
            method: "POST",
            path: "/api/attendance/photos/upload-url",
            jsonBody: stageBody
        )

        if stageRes.isSuccess, let json = stageRes.jsonObject {
            let dataObj = (json["data"] as? [String: Any]) ?? json
            let uploadUrl = dataObj.optString("upload_url", "presigned_url", "put_url")
            let objectKey = dataObj.optString("object_key", "photo_object_key", "key")
            if !uploadUrl.isEmpty && !objectKey.isEmpty {
                var putHeaders: [String: String] = ["Content-Type": item.mimeType]
                if let customHeaders = dataObj["upload_headers"] as? [String: Any] {
                    for (k, v) in customHeaders {
                        if let s = v as? String {
                            putHeaders[k] = s
                        }
                    }
                }
                let putRes = await httpClient.putPresignedUrl(
                    uploadUrl: uploadUrl,
                    fileData: photoData,
                    contentType: item.mimeType,
                    additionalHeaders: putHeaders
                )
                if putRes.isSuccess {
                    return .success(objectKey, statusCode: putRes.statusCode)
                }
                return .failure(
                    message: putRes.errorMessage ?? "Tải ảnh chấm công lên R2 thất bại.",
                    httpStatus: putRes.statusCode,
                    errorCode: putRes.errorCode
                )
            }
        }

        // 2. Fallback to direct multipart upload `/api/attendance/photos`
        let multipartRes = await httpClient.uploadMultipart(
            path: "/api/attendance/photos",
            fieldName: "file",
            fileName: "\(item.localId).jpg",
            mimeType: item.mimeType,
            fileData: photoData,
            extraFields: [
                "idempotency_key": item.idempotencyKey,
                "photo_sha256": item.photoSha256
            ]
        )

        if multipartRes.isSuccess {
            let dataObj = (multipartRes.jsonObject?["data"] as? [String: Any]) ?? multipartRes.jsonObject ?? [:]
            let objectKey = dataObj.optString("object_key", "photo_object_key", "key", "file_path", defaultValue: item.idempotencyKey)
            return .success(objectKey, statusCode: multipartRes.statusCode)
        }

        return .failure(
            message: multipartRes.errorMessage ?? "Không thể tải ảnh chấm công lên máy chủ.",
            httpStatus: multipartRes.statusCode,
            errorCode: multipartRes.errorCode
        )
    }

    private func isPermanentError(httpStatus: Int?, errorCode: String?) -> Bool {
        guard let httpStatus else { return false }
        if httpStatus == 408 || httpStatus == 429 || httpStatus >= 500 || httpStatus == 0 {
            return false
        }
        if httpStatus == 400 || httpStatus == 401 || httpStatus == 403 || httpStatus == 404 || httpStatus == 409 || httpStatus == 413 || httpStatus == 422 {
            return true
        }
        if let code = errorCode?.uppercased() {
            if code.contains("INVALID_") || code.contains("REJECTED") || code.contains("TAMPER") {
                return true
            }
        }
        return false
    }

    private func isAlreadyProcessedConflict(_ response: BackendHttpResponse) -> Bool {
        let code = (response.errorCode ?? "").uppercased()
        if code.contains("ALREADY") || code.contains("DUPLICATE") || code.contains("IDEMPOTENT") {
            return true
        }
        if let obj = response.jsonObject, obj["check_in"] != nil || obj["checkin"] != nil || obj["already_recorded"] as? Bool == true {
            return true
        }
        return false
    }

    private func countTodaySyncedItems(_ list: [AttendanceOutboxItem]) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        let todayComponents = cal.dateComponents([.year, .month, .day], from: Date())

        return Self.deduplicateItems(list).filter { item in
            guard item.status == AttendanceContract.statusSynced else { return false }
            let epochMs = AttendanceTimeAnchorManager.parseIso8601ToEpochMs(item.clientCapturedAt) ?? item.createdAtEpochMs
            let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
            let comps = cal.dateComponents([.year, .month, .day], from: date)
            return comps.year == todayComponents.year
                && comps.month == todayComponents.month
                && comps.day == todayComponents.day
        }.count
    }

    public static func deduplicateItems(_ list: [AttendanceOutboxItem]) -> [AttendanceOutboxItem] {
        var result: [AttendanceOutboxItem] = []
        for item in list {
            let normUser = item.userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if let existingIdx = result.firstIndex(where: { existing in
                guard existing.userKey.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == normUser else {
                    return false
                }
                if existing.localId == item.localId { return true }
                if !item.idempotencyKey.isEmpty && existing.idempotencyKey == item.idempotencyKey { return true }
                if let cid = item.serverCheckinId, !cid.isEmpty, existing.serverCheckinId == cid { return true }
                if let sid = item.serverRecordId, existing.serverRecordId == sid { return true }
                if !item.clientCapturedAt.isEmpty && existing.clientCapturedAt == item.clientCapturedAt { return true }
                if existing.status == AttendanceContract.statusSynced &&
                    item.status == AttendanceContract.statusSynced &&
                    abs(existing.createdAtEpochMs - item.createdAtEpochMs) <= 2_000 {
                    return true
                }
                return false
            }) {
                var merged = result[existingIdx]
                // Prefer non-srv local item identity when merging a local item and a server-synced copy
                if merged.localId.hasPrefix("srv-") && !item.localId.hasPrefix("srv-") {
                    var preferred = item
                    if merged.status == AttendanceContract.statusSynced {
                        preferred.status = AttendanceContract.statusSynced
                    }
                    preferred.photoLocalPath = item.photoLocalPath ?? merged.photoLocalPath
                    preferred.serverRecordId = item.serverRecordId ?? merged.serverRecordId
                    preferred.serverCheckinId = item.serverCheckinId ?? merged.serverCheckinId
                    preferred.serverReceivedAt = item.serverReceivedAt ?? merged.serverReceivedAt
                    merged = preferred
                } else {
                    if item.status == AttendanceContract.statusSynced {
                        merged.status = AttendanceContract.statusSynced
                    }
                    merged.photoLocalPath = merged.photoLocalPath ?? item.photoLocalPath
                    merged.serverRecordId = merged.serverRecordId ?? item.serverRecordId
                    merged.serverCheckinId = merged.serverCheckinId ?? item.serverCheckinId
                    merged.serverReceivedAt = merged.serverReceivedAt ?? item.serverReceivedAt
                }
                result[existingIdx] = merged
            } else {
                result.append(item)
            }
        }
        return result
    }

    public func recoverInterruptedUploads() {
        lock.lock()
        var changed = false
        for i in items.indices {
            if items[i].status == AttendanceContract.statusUploading && !inFlightLocalIds.contains(items[i].localId) {
                items[i].status = AttendanceContract.statusFailedRetryable
                changed = true
            }
        }
        if changed {
            Self.saveItems(items, to: outboxFileUrl)
        }
        lock.unlock()
        if changed {
            onOutboxChanged?()
        }
    }

    private func getItem(localId: String) -> AttendanceOutboxItem? {
        lock.lock()
        defer { lock.unlock() }
        return items.first(where: { $0.localId == localId })
    }

    private func upsertItem(_ item: AttendanceOutboxItem) {
        lock.lock()
        if let idx = items.firstIndex(where: { $0.localId == item.localId }) {
            items[idx] = item
        } else {
            items.append(item)
        }
        items = Self.deduplicateItems(items)
        Self.saveItems(items, to: outboxFileUrl)
        lock.unlock()
        onOutboxChanged?()
    }

    private static func loadItems(from url: URL) -> [AttendanceOutboxItem] {
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([AttendanceOutboxItem].self, from: data) else {
            return []
        }
        return decoded
    }

    private static func saveItems(_ list: [AttendanceOutboxItem], to url: URL) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        try? data.write(to: url, options: .atomic)
    }
}

private extension Array {
    func ifEmpty(_ fallback: @autoclosure () -> [Element]) -> [Element] {
        isEmpty ? fallback() : self
    }
}
