import CryptoKit
import Foundation
import PDFKit
import UIKit

public struct PreparedContractUploadPayload: Equatable, Sendable {
    public let fileName: String
    public let mimeType: String
    public let data: Data
    public let pageCount: Int
    public let sha256Hex: String
    public let isMergedPdf: Bool

    public var sizeBytes: Int64 {
        Int64(data.count)
    }
}

public enum PdfContractMerger {
    public static let maxUploadBytes: Int = 10 * 1024 * 1024 // 10 MB
    public static let defaultMaxImageDimension: CGFloat = 1600.0
    public static let fallbackMaxImageDimension: CGFloat = 1200.0

    public static func sha256Hex(_ data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    /// Normalizes EXIF `imageOrientation` to `.up` and scales the longest edge to `<= maxDimension`.
    public static func normalizeOrientationAndScale(
        image: UIImage,
        maxDimension: CGFloat = defaultMaxImageDimension
    ) -> UIImage {
        let pixelWidth = max(image.size.width, 1.0)
        let pixelHeight = max(image.size.height, 1.0)
        let longest = max(pixelWidth, pixelHeight)

        let scaleFactor: CGFloat = longest > maxDimension ? (maxDimension / longest) : 1.0
        let targetSize = CGSize(
            width: max(round(pixelWidth * scaleFactor), 1.0),
            height: max(round(pixelHeight * scaleFactor), 1.0)
        )

        if image.imageOrientation == .up && scaleFactor == 1.0 && image.scale == 1.0 {
            return image
        }

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: targetSize))
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    /// Merges multiple images into a single PDF (`hop-dong-<timestamp>.pdf`) using `UIGraphicsPDFRenderer`,
    /// automatically stepping down JPEG compression quality and dimension if needed so the PDF is `<= 10 MB`.
    public static func mergeImagesToPdf(
        images: [UIImage],
        timestampMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0),
        maxBytes: Int = maxUploadBytes
    ) throws -> PreparedContractUploadPayload {
        guard !images.isEmpty else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 400,
                backendCode: "EMPTY_IMAGES",
                message: "Vui lòng chọn ít nhất 1 ảnh để tạo tệp hợp đồng."
            )
        }

        let attemptConfigs: [(maxDim: CGFloat, quality: CGFloat)] = [
            (defaultMaxImageDimension, 0.80),
            (defaultMaxImageDimension, 0.65),
            (fallbackMaxImageDimension, 0.60),
            (1000.0, 0.50),
            (800.0, 0.42)
        ]

        var lastRenderedData = Data()
        for config in attemptConfigs {
            let rendered = renderPdfWithUIGraphicsRenderer(
                images: images,
                maxDimension: config.maxDim,
                jpegQuality: config.quality
            )
            lastRenderedData = rendered
            if rendered.count <= maxBytes {
                break
            }
        }

        guard !lastRenderedData.isEmpty, lastRenderedData.count <= maxBytes else {
            throw BackendApiException(
                kind: .invalidRequest,
                statusCode: 413,
                backendCode: "FILE_TOO_LARGE",
                message: "Tệp PDF hợp đồng sau khi ghép vẫn vượt quá giới hạn 10 MB. Vui lòng giảm số lượng trang ảnh."
            )
        }

        let fileName = "hop-dong-\(timestampMs).pdf"
        return PreparedContractUploadPayload(
            fileName: fileName,
            mimeType: "application/pdf",
            data: lastRenderedData,
            pageCount: images.count,
            sha256Hex: sha256Hex(lastRenderedData),
            isMergedPdf: true
        )
    }

    private static func renderPdfWithUIGraphicsRenderer(
        images: [UIImage],
        maxDimension: CGFloat,
        jpegQuality: CGFloat
    ) -> Data {
        let defaultBounds = CGRect(x: 0, y: 0, width: 595.0, height: 842.0)
        let pdfRenderer = UIGraphicsPDFRenderer(bounds: defaultBounds)
        return pdfRenderer.pdfData { context in
            for rawImage in images {
                let normalized = normalizeOrientationAndScale(image: rawImage, maxDimension: maxDimension)
                let compressedImage: UIImage
                if let jpegData = normalized.jpegData(compressionQuality: jpegQuality),
                   let redecoded = UIImage(data: jpegData) {
                    compressedImage = redecoded
                } else {
                    compressedImage = normalized
                }

                let pageWidth = max(compressedImage.size.width, 1.0)
                let pageHeight = max(compressedImage.size.height, 1.0)
                let pageBounds = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)
                context.beginPage(withBounds: pageBounds, pageInfo: [:])
                compressedImage.draw(in: pageBounds)
            }
        }
    }

    /// Prepares contract upload payload according to Requirement 3.4.2:
    /// - If 1 PDF file is provided: sends the PDF as-is (verifying `<= 10 MB`).
    /// - If 1 single image is provided: sends as a single image file (`.jpg`/`.png`/`.webp`, optimizing if `> 10 MB`).
    /// - If 2+ images are provided: normalizes orientation, scales `<= 1600px`, and merges into a single PDF (`hop-dong-<timestamp>.pdf` `<= 10 MB`).
    public static func prepareContractUpload(
        singlePdfData: Data? = nil,
        singlePdfFileName: String? = nil,
        images: [UIImage] = [],
        singleImageOriginalData: Data? = nil,
        singleImageFileName: String? = nil,
        singleImageMimeType: String? = nil,
        timestampMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) throws -> PreparedContractUploadPayload {
        if let pdfData = singlePdfData {
            guard !pdfData.isEmpty else {
                throw BackendApiException(
                    kind: .invalidRequest,
                    statusCode: 400,
                    backendCode: "EMPTY_FILE",
                    message: "Tệp PDF rỗng hoặc không hợp lệ."
                )
            }
            guard pdfData.count <= maxUploadBytes else {
                throw BackendApiException(
                    kind: .invalidRequest,
                    statusCode: 413,
                    backendCode: "FILE_TOO_LARGE",
                    message: "Tệp PDF vượt quá dung lượng tối đa 10 MB."
                )
            }
            let rawName = (singlePdfFileName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedName = rawName.isEmpty ? "hop-dong-\(timestampMs).pdf" : (rawName.lowercased().hasSuffix(".pdf") ? rawName : "\(rawName).pdf")
            let pageCount = PDFDocument(data: pdfData)?.pageCount ?? 1
            return PreparedContractUploadPayload(
                fileName: resolvedName,
                mimeType: "application/pdf",
                data: pdfData,
                pageCount: max(pageCount, 1),
                sha256Hex: sha256Hex(pdfData),
                isMergedPdf: false
            )
        }

        if images.count == 1 {
            let img = images[0]
            if let orig = singleImageOriginalData,
               !orig.isEmpty,
               orig.count <= maxUploadBytes {
                let rawMime = (singleImageMimeType ?? "image/jpeg").lowercased()
                let resolvedMime = ["image/jpeg", "image/png", "image/webp"].contains(rawMime) ? rawMime : "image/jpeg"
                let ext: String
                switch resolvedMime {
                case "image/png": ext = "png"
                case "image/webp": ext = "webp"
                default: ext = "jpg"
                }
                let rawName = (singleImageFileName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let resolvedName = rawName.isEmpty ? "hop-dong-\(timestampMs).\(ext)" : rawName
                return PreparedContractUploadPayload(
                    fileName: resolvedName,
                    mimeType: resolvedMime,
                    data: orig,
                    pageCount: 1,
                    sha256Hex: sha256Hex(orig),
                    isMergedPdf: false
                )
            }

            let normalized = normalizeOrientationAndScale(image: img, maxDimension: defaultMaxImageDimension)
            guard let jpegData = normalized.jpegData(compressionQuality: 0.82),
                  jpegData.count <= maxUploadBytes
            else {
                throw BackendApiException(
                    kind: .invalidRequest,
                    statusCode: 413,
                    backendCode: "FILE_TOO_LARGE",
                    message: "Ảnh hợp đồng vượt quá dung lượng tối đa 10 MB."
                )
            }
            let rawName = (singleImageFileName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedName = rawName.isEmpty ? "hop-dong-\(timestampMs).jpg" : rawName
            return PreparedContractUploadPayload(
                fileName: resolvedName,
                mimeType: "image/jpeg",
                data: jpegData,
                pageCount: 1,
                sha256Hex: sha256Hex(jpegData),
                isMergedPdf: false
            )
        }

        return try mergeImagesToPdf(images: images, timestampMs: timestampMs)
    }
}
