import Foundation
import UIKit
import Vision

public struct CccdDecodedQrPayload: Equatable, Sendable {
    public let cccd12: String
    public let legacyCmnd9: String?
    public let fullName: String
    public let dateOfBirthUi: String
    public let genderUi: String
    public let address: String
    public let issueDateUi: String
    public let rawQrText: String

    public init(
        cccd12: String,
        legacyCmnd9: String? = nil,
        fullName: String = "",
        dateOfBirthUi: String = "",
        genderUi: String = "",
        address: String = "",
        issueDateUi: String = "",
        rawQrText: String = ""
    ) {
        self.cccd12 = cccd12
        self.legacyCmnd9 = legacyCmnd9
        self.fullName = fullName
        self.dateOfBirthUi = dateOfBirthUi
        self.genderUi = genderUi
        self.address = address
        self.issueDateUi = issueDateUi
        self.rawQrText = rawQrText
    }
}

public enum CccdQrDecoder {
    private static let mojibakeMarkers = ["Ã", "Â", "Ä", "Å", "Æ", "á»", "áº", "Æ°", "Æ¡", "Ä‘", "Ä\u{0090}"]

    public static func parseRawQrString(_ rawText: String?) -> CccdDecodedQrPayload? {
        guard let rawText = rawText else { return nil }
        let repaired = repairUtf8MojibakeIfNeeded(rawText)
            .replacingOccurrences(of: "\u{FEFF}", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !repaired.isEmpty else { return nil }

        if repaired.contains("|") {
            let parts = repaired.components(separatedBy: "|").map {
                $0.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if let first = parts.first {
                let digits = first.filter { $0.isNumber }
                if DirectEntryContract.isValidCccd12(digits) {
                    let cmnd9 = parts.count > 1 && !parts[1].isEmpty ? parts[1] : nil
                    let fullName = parts.count > 2 ? normalizeSpaces(parts[2]) : ""
                    let dobUi = parts.count > 3 ? normalizeQrDateToUi(parts[3]) : ""
                    let genderUi = parts.count > 4 ? normalizeQrGenderToUi(parts[4]) : ""
                    let address = parts.count > 5 ? normalizeSpaces(parts[5]) : ""
                    let issueDateUi = parts.count > 6 ? normalizeQrDateToUi(parts[6]) : ""

                    return CccdDecodedQrPayload(
                        cccd12: digits,
                        legacyCmnd9: cmnd9,
                        fullName: fullName,
                        dateOfBirthUi: dobUi,
                        genderUi: genderUi,
                        address: address,
                        issueDateUi: issueDateUi,
                        rawQrText: repaired
                    )
                }
            }
        }

        let standaloneDigits = repaired.trimmingCharacters(in: .whitespacesAndNewlines)
        if standaloneDigits.count == 12 && DirectEntryContract.isValidCccd12(standaloneDigits) {
            return CccdDecodedQrPayload(
                cccd12: standaloneDigits,
                rawQrText: repaired
            )
        }

        return nil
    }

    public static func repairUtf8MojibakeIfNeeded(_ input: String) -> String {
        let hasMarker = mojibakeMarkers.contains { input.contains($0) }
        guard hasMarker else { return input }

        if let isoBytes = input.data(using: .isoLatin1),
           let utf8Str = String(data: isoBytes, encoding: .utf8),
           !utf8Str.contains("\u{FFFD}") {
            return utf8Str
        }
        if let winBytes = input.data(using: .windowsCP1252),
           let utf8Str = String(data: winBytes, encoding: .utf8),
           !utf8Str.contains("\u{FFFD}") {
            return utf8Str
        }
        return input
    }

    public static func normalizeQrDateToUi(_ raw: String) -> String {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }

        let digits = clean.filter { $0.isNumber }
        if digits.count == 8 {
            let p1 = Int(digits.prefix(2)) ?? 0
            let p2 = Int(digits.dropFirst(2).prefix(2)) ?? 0
            let y1 = Int(digits.suffix(4)) ?? 0
            if (1...31).contains(p1) && (1...12).contains(p2) && (1900...2100).contains(y1) {
                return String(format: "%02d/%02d/%04d", p1, p2, y1)
            }
            let yFirst = Int(digits.prefix(4)) ?? 0
            let mFirst = Int(digits.dropFirst(4).prefix(2)) ?? 0
            let dFirst = Int(digits.suffix(2)) ?? 0
            if (1900...2100).contains(yFirst) && (1...12).contains(mFirst) && (1...31).contains(dFirst) {
                return String(format: "%02d/%02d/%04d", dFirst, mFirst, yFirst)
            }
        }

        let uiConverted = DirectEntryContract.isoDateToUiDate(clean)
        if DirectEntryContract.isValidUiOrIsoDate(uiConverted) {
            return uiConverted
        }
        return String(clean.prefix(10))
    }

    public static func normalizeQrGenderToUi(_ raw: String) -> String {
        let clean = normalizeSpaces(raw).lowercased()
        guard !clean.isEmpty else { return "" }
        if clean == "nam" || clean == "male" || clean == "m" {
            return "Nam"
        }
        if clean == "nữ" || clean == "nu" || clean == "female" || clean == "f" {
            return "Nữ"
        }
        if clean == "khác" || clean == "khac" || clean == "other" {
            return "Khác"
        }
        return ""
    }

    public static func applyQrPayloadToDraft(
        draft: NewWorkerEntryDraft,
        qr: CccdDecodedQrPayload,
        overwriteExisting: Bool = true
    ) -> NewWorkerEntryDraft {
        var updated = draft
        updated.cccd = qr.cccd12

        if !qr.fullName.isEmpty && (overwriteExisting || updated.fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            updated.fullName = qr.fullName
        }
        if !qr.genderUi.isEmpty && (overwriteExisting || updated.genderUi.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            updated.genderUi = qr.genderUi
        }
        if !qr.dateOfBirthUi.isEmpty && (overwriteExisting || updated.birthDateUi.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            updated.birthDateUi = qr.dateOfBirthUi
        }
        if !qr.address.isEmpty && (overwriteExisting || updated.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            updated.address = qr.address
        }
        if !qr.issueDateUi.isEmpty && (overwriteExisting || updated.cccdIssueDateUi.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            updated.cccdIssueDateUi = qr.issueDateUi
        }
        if !qr.dateOfBirthUi.isEmpty || !qr.address.isEmpty || !qr.issueDateUi.isEmpty {
            updated.showOptionalSection = true
        }
        return updated
    }

    public static func decodeFromImageData(_ data: Data) -> CccdDecodedQrPayload? {
        guard let image = UIImage(data: data), let cgImage = image.cgImage else {
            return nil
        }
        return decodeFromCGImage(cgImage)
    }

    public static func decodeFromCGImage(_ cgImage: CGImage) -> CccdDecodedQrPayload? {
        var found: CccdDecodedQrPayload?
        let request = VNDetectBarcodesRequest { req, _ in
            guard let results = req.results as? [VNBarcodeObservation] else { return }
            for obs in results {
                if let payload = obs.payloadStringValue,
                   let decoded = parseRawQrString(payload) {
                    found = decoded
                    break
                }
            }
        }
        request.symbologies = [.qr]
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([request])
        return found
    }

    private static func normalizeSpaces(_ s: String) -> String {
        s.components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}
