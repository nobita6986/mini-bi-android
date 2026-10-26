import Foundation

// MARK: - Vietnamese CCCD QR Decoder (2021 7-segment & 2024+ 11+ segment Căn cước formats)
// Mirrors Android's `data/CccdQrDecoder.kt` 100%

public struct CccdQrData: Equatable, Sendable {
    public let soCccd: String
    public let soCmndCu: String
    public let hoTen: String
    public let ngaySinh: String
    public let gioiTinh: String
    public let queQuan: String
    public let ngayCap: String
    public let rawQr: String

    public init(
        soCccd: String,
        soCmndCu: String = "",
        hoTen: String = "",
        ngaySinh: String = "",
        gioiTinh: String = "",
        queQuan: String = "",
        ngayCap: String = "",
        rawQr: String = ""
    ) {
        self.soCccd = soCccd
        self.soCmndCu = soCmndCu
        self.hoTen = hoTen
        self.ngaySinh = ngaySinh
        self.gioiTinh = gioiTinh
        self.queQuan = queQuan
        self.ngayCap = ngayCap
        self.rawQr = rawQr
    }
}

public enum CccdQrDecoder {
    private static let mojibakeMarkers = ["Ã", "Â", "Ä", "Å", "Æ", "á»", "áº", "Æ°", "Æ¡", "Ä‘", "Ä\u{0090}"]

    public static func parse(_ rawText: String?) -> CccdQrData? {
        guard let rawText else { return nil }
        let repaired = repairUtf8MojibakeIfNeeded(rawText)
            .replacingOccurrences(of: "\u{FEFF}", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !repaired.isEmpty else { return nil }

        if repaired.contains("|") {
            let parts = repaired.components(separatedBy: "|").map {
                $0.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if let first = parts.first {
                let cccdDigits = first.filter { $0.isNumber }
                if DirectEntryContract.isValidCccd12(cccdDigits) {
                    let cmndCu = parts.count > 1 ? parts[1] : ""
                    let hoTen = parts.count > 2 ? normalizeSpaces(parts[2]) : ""
                    let ngaySinh = parts.count > 3 ? normalizeDateToUi(parts[3]) : ""
                    let gioiTinh = parts.count > 4 ? normalizeGenderToUi(parts[4]) : ""
                    let queQuan = parts.count > 5 ? normalizeSpaces(parts[5]) : ""
                    let ngayCap = parts.count > 6 ? normalizeDateToUi(parts[6]) : ""

                    return CccdQrData(
                        soCccd: cccdDigits,
                        soCmndCu: cmndCu,
                        hoTen: hoTen,
                        ngaySinh: ngaySinh,
                        gioiTinh: gioiTinh,
                        queQuan: queQuan,
                        ngayCap: ngayCap,
                        rawQr: repaired
                    )
                }
            }
        }

        let standaloneDigits = repaired.filter { $0.isNumber }
        if standaloneDigits.count == 12 && DirectEntryContract.isValidCccd12(standaloneDigits) {
            return CccdQrData(soCccd: standaloneDigits, rawQr: repaired)
        }

        return nil
    }

    public static func applyToDraft(_ draft: NewWorkerEntryDraft, qrData: CccdQrData) -> NewWorkerEntryDraft {
        var copy = draft
        copy.soCccd = qrData.soCccd
        if !qrData.hoTen.isEmpty {
            copy.hoTen = qrData.hoTen
        }
        if !qrData.ngaySinh.isEmpty {
            copy.ngaySinh = qrData.ngaySinh
        }
        if !qrData.gioiTinh.isEmpty {
            copy.gioiTinh = qrData.gioiTinh
        }
        if !qrData.queQuan.isEmpty {
            copy.queQuan = qrData.queQuan
        }
        if !qrData.ngayCap.isEmpty {
            copy.ngayCap = qrData.ngayCap
        }
        return copy
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

    public static func normalizeDateToUi(_ raw: String) -> String {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }

        let digits = clean.filter { $0.isNumber }
        if digits.count == 8 {
            let d = Int(digits.prefix(2)) ?? 0
            let m = Int(digits.dropFirst(2).prefix(2)) ?? 0
            let y = Int(digits.suffix(4)) ?? 0
            if (1...31).contains(d) && (1...12).contains(m) && (1900...2100).contains(y) {
                return String(format: "%02d/%02d/%04d", d, m, y)
            }
            let yFirst = Int(digits.prefix(4)) ?? 0
            let mFirst = Int(digits.dropFirst(4).prefix(2)) ?? 0
            let dFirst = Int(digits.suffix(2)) ?? 0
            if (1900...2100).contains(yFirst) && (1...12).contains(mFirst) && (1...31).contains(dFirst) {
                return String(format: "%02d/%02d/%04d", dFirst, mFirst, yFirst)
            }
        }

        let partsSlash = clean.split(separator: "/")
        if partsSlash.count == 3,
           let d = Int(partsSlash[0]),
           let m = Int(partsSlash[1]),
           let y = Int(partsSlash[2]),
           (1...31).contains(d), (1...12).contains(m), (1900...2100).contains(y) {
            return String(format: "%02d/%02d/%04d", d, m, y)
        }

        let partsDash = clean.split(separator: "-")
        if partsDash.count == 3,
           let y = Int(partsDash[0]),
           let m = Int(partsDash[1]),
           let d = Int(partsDash[2]),
           (1...31).contains(d), (1...12).contains(m), (1900...2100).contains(y) {
            return String(format: "%02d/%02d/%04d", d, m, y)
        }

        return clean
    }

    public static func normalizeGenderToUi(_ raw: String) -> String {
        let lower = normalizeSpaces(raw).lowercased()
        guard !lower.isEmpty else { return "" }
        if lower == "nam" || lower == "male" || lower == "m" {
            return "Nam"
        }
        if lower == "nữ" || lower == "nu" || lower == "female" || lower == "f" {
            return "Nữ"
        }
        if lower == "khác" || lower == "khac" || lower == "other" {
            return "Khác"
        }
        return raw
    }

    private static func normalizeSpaces(_ s: String) -> String {
        s.components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}
