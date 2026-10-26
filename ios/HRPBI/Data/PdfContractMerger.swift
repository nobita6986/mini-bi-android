import Foundation
import UIKit
import PDFKit

// MARK: - Multi-Image Contract to Single PDF Merger (`UIGraphicsPDFRenderer`)
// Requirement 3.4.2:
// - 1 PDF or 1 image: send as-is (or compressed single JPEG <= 1600px, <= 10 MB)
// - 2+ images: normalize EXIF orientation, scale longest edge <= 1600px, and merge into
//   1 PDF (`hop-dong-<timestamp>.pdf`, <= 10 MB, stepping down JPEG quality if needed).

public enum ContractUploadPreparationError: LocalizedError {
    case emptyAttachments
    case invalidImageData
    case exceedsMaxSizeLimit

    public var errorDescription: String? {
        switch self {
        case .emptyAttachments:
            return "Vui lòng chọn ít nhất 1 ảnh hoặc tệp PDF hợp đồng."
        case .invalidImageData:
            return "Không thể đọc dữ liệu ảnh hợp đồng đã chọn."
        case .exceedsMaxSizeLimit:
            return "Tệp hợp đồng vượt quá giới hạn tối đa 10 MB."
        }
    }
}

public struct PreparedContractUploadPayload: Equatable, Sendable {
    public let fileName: String
    public let mimeType: String
    public let data: Data
    public let pageCount: Int
    public let wasMergedFromMultipleImages: Bool

    public init(
        fileName: String,
        mimeType: String,
        data: Data,
        pageCount: Int,
        wasMergedFromMultipleImages: Bool
    ) {
        self.fileName = fileName
        self.mimeType = mimeType
        self.data = data
        self.pageCount = pageCount
        self.wasMergedFromMultipleImages = wasMergedFromMultipleImages
    }
}

public enum PdfContractMerger {
    public static let maxUploadSizeBytes: Int = 10 * 1024 * 1024 // 10 MB
    public static let maxImageDimensionPx: CGFloat = 1600.0

    public static func prepareContractUploadPayload(
        attachments: [LocalAttachmentItem],
        timestampMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) throws -> PreparedContractUploadPayload {
        guard !attachments.isEmpty else {
            throw ContractUploadPreparationError.emptyAttachments
        }

        // Case 1: Single PDF attachment -> send as-is if <= 10 MB
        if attachments.count == 1 && attachments[0].isPdf {
            let pdfAtt = attachments[0]
            guard !pdfAtt.data.isEmpty && pdfAtt.data.count <= maxUploadSizeBytes else {
                throw ContractUploadPreparationError.exceedsMaxSizeLimit
            }
            let pageCount = PDFDocument(data: pdfAtt.data)?.pageCount ?? 1
            let cleanName = pdfAtt.fileName.lowercased().hasSuffix(".pdf")
                ? pdfAtt.fileName
                : "hop-dong-\(timestampMs).pdf"
            return PreparedContractUploadPayload(
                fileName: cleanName,
                mimeType: "application/pdf",
                data: pdfAtt.data,
                pageCount: max(1, pageCount),
                wasMergedFromMultipleImages: false
            )
        }

        // Case 2: Single image attachment -> send as single image (normalizing/compressing if > 10 MB)
        if attachments.count == 1 {
            let imgAtt = attachments[0]
            guard let uiImage = UIImage(data: imgAtt.data) else {
                throw ContractUploadPreparationError.invalidImageData
            }
            if imgAtt.data.count <= maxUploadSizeBytes &&
                max(uiImage.size.width, uiImage.size.height) <= maxImageDimensionPx &&
                uiImage.imageOrientation == .up {
                return PreparedContractUploadPayload(
                    fileName: imgAtt.fileName.isEmpty ? "hop-dong-\(timestampMs).jpg" : imgAtt.fileName,
                    mimeType: imgAtt.mimeType.isEmpty ? "image/jpeg" : imgAtt.mimeType,
                    data: imgAtt.data,
                    pageCount: 1,
                    wasMergedFromMultipleImages: false
                )
            }

            let normalized = normalizeOrientationAndScale(uiImage, maxDimension: maxImageDimensionPx)
            guard let jpegData = normalized.jpegData(compressionQuality: 0.82),
                  jpegData.count <= maxUploadSizeBytes else {
                throw ContractUploadPreparationError.exceedsMaxSizeLimit
            }
            return PreparedContractUploadPayload(
                fileName: imgAtt.fileName.isEmpty ? "hop-dong-\(timestampMs).jpg" : imgAtt.fileName,
                mimeType: "image/jpeg",
                data: jpegData,
                pageCount: 1,
                wasMergedFromMultipleImages: false
            )
        }

        // Case 3: 2 or more attachments -> merge all pages into 1 single PDF `hop-dong-<timestamp>.pdf` <= 10 MB
        var uiImages: [UIImage] = []
        for att in attachments {
            if att.isPdf, let doc = PDFDocument(data: att.data) {
                for pageIdx in 0..<doc.pageCount {
                    if let page = doc.page(at: pageIdx) {
                        let pageRect = page.bounds(for: .mediaBox)
                        let renderer = UIGraphicsImageRenderer(size: pageRect.size)
                        let renderedImg = renderer.image { ctx in
                            UIColor.white.setFill()
                            ctx.fill(pageRect)
                            ctx.cgContext.translateBy(x: 0, y: pageRect.size.height)
                            ctx.cgContext.scaleBy(x: 1.0, y: -1.0)
                            page.draw(with: .mediaBox, to: ctx.cgContext)
                        }
                        uiImages.append(renderedImg)
                    }
                }
            } else if let img = UIImage(data: att.data) {
                uiImages.append(img)
            }
        }

        guard !uiImages.isEmpty else {
            throw ContractUploadPreparationError.invalidImageData
        }

        let compressionSteps: [(maxDim: CGFloat, quality: CGFloat)] = [
            (maxImageDimensionPx, 0.80),
            (maxImageDimensionPx, 0.65),
            (1280.0, 0.58),
            (1024.0, 0.48),
            (800.0, 0.40)
        ]

        var mergedPdfData = Data()
        for step in compressionSteps {
            let candidate = renderMergedPdf(
                images: uiImages,
                maxDimension: step.maxDim,
                jpegQuality: step.quality
            )
            mergedPdfData = candidate
            if candidate.count <= maxUploadSizeBytes {
                break
            }
        }

        guard !mergedPdfData.isEmpty && mergedPdfData.count <= maxUploadSizeBytes else {
            throw ContractUploadPreparationError.exceedsMaxSizeLimit
        }

        return PreparedContractUploadPayload(
            fileName: "hop-dong-\(timestampMs).pdf",
            mimeType: "application/pdf",
            data: mergedPdfData,
            pageCount: uiImages.count,
            wasMergedFromMultipleImages: true
        )
    }

    public static func normalizeOrientationAndScale(
        _ image: UIImage,
        maxDimension: CGFloat = maxImageDimensionPx
    ) -> UIImage {
        let width = max(1.0, image.size.width)
        let height = max(1.0, image.size.height)
        let longest = max(width, height)
        let ratio = longest > maxDimension ? (maxDimension / longest) : 1.0
        let targetSize = CGSize(
            width: max(1.0, round(width * ratio)),
            height: max(1.0, round(height * ratio))
        )

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

    private static func renderMergedPdf(
        images: [UIImage],
        maxDimension: CGFloat,
        jpegQuality: CGFloat
    ) -> Data {
        let defaultBounds = CGRect(x: 0, y: 0, width: 595.0, height: 842.0)
        let pdfRenderer = UIGraphicsPDFRenderer(bounds: defaultBounds)
        return pdfRenderer.pdfData { context in
            for rawImage in images {
                let normalized = normalizeOrientationAndScale(rawImage, maxDimension: maxDimension)
                let pageImage: UIImage
                if let jpegData = normalized.jpegData(compressionQuality: jpegQuality),
                   let redecoded = UIImage(data: jpegData) {
                    pageImage = redecoded
                } else {
                    pageImage = normalized
                }
                let pageBounds = CGRect(
                    x: 0,
                    y: 0,
                    width: max(1.0, pageImage.size.width),
                    height: max(1.0, pageImage.size.height)
                )
                context.beginPage(withBounds: pageBounds, pageInfo: [:])
                pageImage.draw(in: pageBounds)
            }
        }
    }
}
