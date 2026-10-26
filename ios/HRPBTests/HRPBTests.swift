import XCTest
import UIKit
import PDFKit
@testable import HRPBI

final class HRPBTests: XCTestCase {

    // MARK: - 1. Environment Configuration & URL Normalization Tests

    func testEnvironmentVariantsAndNormalization() throws {
        XCTAssertEqual(
            BackendHttpClient.normalizeBaseUrl("https://bi.hrpartner.vn/", enforceHttps: true),
            "https://bi.hrpartner.vn"
        )
        XCTAssertEqual(
            BackendHttpClient.normalizeBaseUrl("https://bi2.hrpartner.vn/api", enforceHttps: false),
            "https://bi2.hrpartner.vn"
        )
        // Release enforces HTTPS
        XCTAssertNil(
            BackendHttpClient.normalizeBaseUrl("http://bi.hrpartner.vn", enforceHttps: true)
        )
        XCTAssertEqual(
            BackendHttpClient.extractOrigin(from: URL(string: "https://bi.hrpartner.vn/api/workers?page=1")!),
            "https://bi.hrpartner.vn"
        )
    }

    func testKeychainCookieStoreAndSavedLoginAccounts() throws {
        let keychain = KeychainStore(serviceName: "com.aistudio.minibi.test.\(UUID().uuidString)")
        let repo = MiniBiRepository(keychain: keychain)

        repo.saveLoginAccount(identifier: "nv001", password: "secret1", displayName: "Nguyễn Văn A", role: "Quản lý")
        repo.saveLoginAccount(identifier: "nv002", password: "secret2", displayName: "Trần Thị B", role: "Tuyển dụng")

        let accounts = repo.getSavedLoginAccounts()
        XCTAssertEqual(accounts.count, 2)
        XCTAssertEqual(accounts.first?.identifier, "nv002")

        repo.removeSavedLoginAccount(identifier: "nv001")
        XCTAssertEqual(repo.getSavedLoginAccounts().count, 1)
        XCTAssertEqual(repo.getSavedLoginAccounts().first?.identifier, "nv002")
    }

    // MARK: - 2. Vietnamese CCCD QR Decoder Tests

    func testCccdQrDecoder2021And2024Formats() throws {
        // 7-segment 2021 CCCD QR
        let raw2021 = "034098001234|123456789|Nguyễn Văn An|15081998|Nam|Thái Bình, Việt Nam|20102021"
        let parsed2021 = try XCTUnwrap(CccdQrDecoder.parse(raw2021))
        XCTAssertEqual(parsed2021.soCccd, "034098001234")
        XCTAssertEqual(parsed2021.soCmndCu, "123456789")
        XCTAssertEqual(parsed2021.hoTen, "Nguyễn Văn An")
        XCTAssertEqual(parsed2021.ngaySinh, "15/08/1998")
        XCTAssertEqual(parsed2021.gioiTinh, "Nam")
        XCTAssertEqual(parsed2021.queQuan, "Thái Bình, Việt Nam")
        XCTAssertEqual(parsed2021.ngayCap, "20/10/2021")

        // 11-segment 2024+ Căn cước QR with trailing empty segments
        let raw2024 = "001200012345||Lê Thị Hoa|02092000|Nữ|Cầu Giấy, Hà Nội|15072024||||"
        let parsed2024 = try XCTUnwrap(CccdQrDecoder.parse(raw2024))
        XCTAssertEqual(parsed2024.soCccd, "001200012345")
        XCTAssertEqual(parsed2024.hoTen, "Lê Thị Hoa")
        XCTAssertEqual(parsed2024.ngaySinh, "02/09/2000")
        XCTAssertEqual(parsed2024.gioiTinh, "Nữ")
        XCTAssertEqual(parsed2024.queQuan, "Cầu Giấy, Hà Nội")
        XCTAssertEqual(parsed2024.ngayCap, "15/07/2024")

        // Apply to NewWorkerEntryDraft
        let draft = NewWorkerEntryDraft()
        let updated = CccdQrDecoder.applyToDraft(draft, qrData: parsed2024)
        XCTAssertEqual(updated.soCccd, "001200012345")
        XCTAssertEqual(updated.hoTen, "Lê Thị Hoa")
        XCTAssertEqual(updated.ngaySinh, "02/09/2000")
        XCTAssertEqual(updated.gioiTinh, "Nữ")
        XCTAssertEqual(updated.queQuan, "Cầu Giấy, Hà Nội")
        XCTAssertEqual(updated.ngayCap, "15/07/2024")
    }

    // MARK: - 3. Multi-Image-to-PDF Contract Merger Tests (`UIGraphicsPDFRenderer`)

    func testPdfContractMergerSingleAndMultipleImages() throws {
        let img1Data = try makeSolidTestJpeg(color: .systemBlue, size: CGSize(width: 1800, height: 2400))
        let img2Data = try makeSolidTestJpeg(color: .systemGreen, size: CGSize(width: 1200, height: 1600))
        let img3Data = try makeSolidTestJpeg(color: .systemOrange, size: CGSize(width: 900, height: 1200))

        let att1 = LocalAttachmentItem(fileName: "page1.jpg", mimeType: "image/jpeg", data: img1Data)
        let att2 = LocalAttachmentItem(fileName: "page2.jpg", mimeType: "image/jpeg", data: img2Data)
        let att3 = LocalAttachmentItem(fileName: "page3.jpg", mimeType: "image/jpeg", data: img3Data)

        // Case A: Single image -> sent as single JPEG without merging into PDF
        let singlePayload = try PdfContractMerger.prepareContractUploadPayload(attachments: [att1])
        XCTAssertFalse(singlePayload.wasMergedFromMultipleImages)
        XCTAssertEqual(singlePayload.pageCount, 1)
        XCTAssertEqual(singlePayload.mimeType, "image/jpeg")
        XCTAssertLessThanOrEqual(singlePayload.data.count, PdfContractMerger.maxUploadSizeBytes)

        // Case B: 3 images -> automatically merged into 1 single PDF <= 10 MB with 3 pages
        let mergedPayload = try PdfContractMerger.prepareContractUploadPayload(
            attachments: [att1, att2, att3],
            timestampMs: 1730000000000
        )
        XCTAssertTrue(mergedPayload.wasMergedFromMultipleImages)
        XCTAssertEqual(mergedPayload.fileName, "hop-dong-1730000000000.pdf")
        XCTAssertEqual(mergedPayload.mimeType, "application/pdf")
        XCTAssertEqual(mergedPayload.pageCount, 3)
        XCTAssertLessThanOrEqual(mergedPayload.data.count, PdfContractMerger.maxUploadSizeBytes)

        let pdfHeader = String(data: mergedPayload.data.prefix(5), encoding: .ascii)
        XCTAssertEqual(pdfHeader, "%PDF-")

        let pdfDoc = try XCTUnwrap(PDFDocument(data: mergedPayload.data))
        XCTAssertEqual(pdfDoc.pageCount, 3)
    }

    // MARK: - 4. Preserve `newWorkerDraftRows` Across Menu/Tab Switches Tests

    @MainActor
    func testNewWorkerDraftRowsPreservedAcrossTabNavigation() throws {
        let vm = MiniBiViewModel()

        // Navigate to "Cấp mới" and enter partial data + contract attachments
        vm.navigateTo(.newEntry)
        let firstRowId = try XCTUnwrap(vm.newWorkerDraftRows.first?.rowId)
        vm.updateNewWorkerDraftRow(rowId: firstRowId) { draft in
            draft.hoTen = "Phạm Văn Hoàng"
            draft.soCccd = "034099008877"
            draft.duAn = "Samsung Bắc Ninh"
            draft.sdt = "0988776655"
        }

        let dummyContract = LocalAttachmentItem(
            fileName: "hopdong1.jpg",
            mimeType: "image/jpeg",
            data: Data([0xFF, 0xD8, 0xFF, 0xD9])
        )
        vm.addAttachmentToDraftRow(
            rowId: firstRowId,
            docType: DirectEntryContract.docTypeSafetyCard,
            attachment: dummyContract
        )

        // Add a second draft row via QR scan
        vm.addNewWorkerDraftRow()
        let secondRowId = vm.newWorkerDraftRows[1].rowId
        let scanned = vm.applyScannedCccdQr(
            rowId: secondRowId,
            rawQr: "036095001122||Đặng Thị Mai|10121995|Nữ|Nam Định|01012022"
        )
        XCTAssertTrue(scanned)

        // Switch across all other menus (`Tổng quan`, `Nhân sự`, `Chấm công`, `Báo cáo CV`) and back to `Cấp mới`
        vm.navigateTo(.dashboard)
        vm.navigateTo(.workers)
        vm.navigateTo(.attendance)
        vm.navigateTo(.workReports)
        vm.navigateTo(.newEntry)

        // Verify 100% of draft data and attachments remain intact
        XCTAssertEqual(vm.newWorkerDraftRows.count, 2)
        XCTAssertEqual(vm.newWorkerDraftRows[0].hoTen, "Phạm Văn Hoàng")
        XCTAssertEqual(vm.newWorkerDraftRows[0].soCccd, "034099008877")
        XCTAssertEqual(vm.newWorkerDraftRows[0].duAn, "Samsung Bắc Ninh")
        XCTAssertEqual(vm.newWorkerDraftRows[0].contractFiles.count, 1)
        XCTAssertEqual(vm.newWorkerDraftRows[1].hoTen, "Đặng Thị Mai")
        XCTAssertEqual(vm.newWorkerDraftRows[1].soCccd, "036095001122")
        // Second row also inherited project from first row
        XCTAssertEqual(vm.newWorkerDraftRows[1].duAn, "Samsung Bắc Ninh")
    }

    // MARK: - 5. Work Reports `is_overdue` Classification & `WORK_REPORT_DEADLINE_PASSED` Tests

    @MainActor
    func testWorkReportsOverdueClassificationAndRejectedNotLockedByDeviceClock() throws {
        // 1. NOT_STARTED + is_overdue = true -> "Quá hạn · Chưa nộp", moved to History, read-only
        let overdueNotStarted = WorkReportsAndReportingService.parseObligationItem([
            "obligation_id": 101,
            "template_name": "Báo cáo tuần tuyển dụng",
            "period_type": "WEEKLY",
            "period_key": "2026-W42",
            "due_at": "2026-10-18T17:00:00Z",
            "submission_state": "NOT_STARTED",
            "is_overdue": true
        ])
        XCTAssertTrue(overdueNotStarted.isOverdue)
        XCTAssertEqual(WorkReportsContract.obligationBadgeDisplayLabel(overdueNotStarted), "Quá hạn · Chưa nộp")
        XCTAssertFalse(WorkReportsContract.isActionableObligation(overdueNotStarted))
        XCTAssertTrue(WorkReportsContract.isHistoryObligation(overdueNotStarted))
        XCTAssertFalse(WorkReportsContract.isEditableObligation(overdueNotStarted))

        // 2. DRAFT + is_overdue = true -> "Quá hạn · Bản nháp chưa nộp", moved to History, read-only
        let overdueDraft = WorkReportsAndReportingService.parseObligationItem([
            "obligation_id": 102,
            "template_name": "Báo cáo ngày nhân sự",
            "period_type": "DAILY",
            "period_key": "2026-10-20",
            "due_at": "2026-10-20T17:00:00Z",
            "submission_state": "DRAFT",
            "is_overdue": true
        ])
        XCTAssertTrue(overdueDraft.isOverdue)
        XCTAssertEqual(WorkReportsContract.obligationBadgeDisplayLabel(overdueDraft), "Quá hạn · Bản nháp chưa nộp")
        XCTAssertFalse(WorkReportsContract.isActionableObligation(overdueDraft))
        XCTAssertTrue(WorkReportsContract.isHistoryObligation(overdueDraft))
        XCTAssertFalse(WorkReportsContract.isEditableObligation(overdueDraft))

        // 3. REJECTED with past `due_at` but server `is_overdue = false` -> MUST remain editable in "Cần nộp"!
        let rejectedPastDue = WorkReportsAndReportingService.parseObligationItem([
            "obligation_id": 103,
            "template_name": "Báo cáo tháng vận hành",
            "period_type": "MONTHLY",
            "period_key": "2020-01",
            "due_at": "2020-01-31T17:00:00Z",
            "submission_state": "REJECTED",
            "is_overdue": false
        ])
        XCTAssertFalse(rejectedPastDue.isOverdue)
        XCTAssertTrue(WorkReportsContract.isActionableObligation(rejectedPastDue))
        XCTAssertFalse(WorkReportsContract.isHistoryObligation(rejectedPastDue))
        XCTAssertTrue(WorkReportsContract.isEditableObligation(rejectedPastDue))

        // 4. Backward compatibility: server payload without `is_overdue` defaults to `false`
        let legacyDraft = WorkReportsAndReportingService.parseObligationItem([
            "obligation_id": 104,
            "template_name": "Báo cáo cũ",
            "submission_state": "DRAFT"
        ])
        XCTAssertFalse(legacyDraft.isOverdue)
        XCTAssertTrue(WorkReportsContract.isActionableObligation(legacyDraft))

        // 5. Verify ViewModel partitioning between `actionableObligations` and `historyObligations`
        let vm = MiniBiViewModel()
        vm.workReportObligations = [overdueNotStarted, overdueDraft, rejectedPastDue, legacyDraft]
        XCTAssertEqual(vm.actionableObligations.map(\.id), [103, 104])
        XCTAssertEqual(vm.historyObligations.map(\.id), [101, 102])
    }

    func testWorkReportDeadlinePassed409VsVersionConflict409() throws {
        // HTTP 409 with `code = "WORK_REPORT_DEADLINE_PASSED"`
        let deadlineResponse = BackendHttpResponse(
            statusCode: 409,
            headers: [:],
            rawData: Data(),
            rawBody: "{\"code\":\"WORK_REPORT_DEADLINE_PASSED\",\"message\":\"Deadline passed\"}",
            jsonObject: ["code": "WORK_REPORT_DEADLINE_PASSED", "message": "Deadline passed"],
            errorCode: "WORK_REPORT_DEADLINE_PASSED",
            errorMessage: "Deadline passed"
        )
        let deadlineResult: MiniBiApiResult<WorkReportObligationItem> =
            WorkReportsAndReportingService.handleWorkReportConflictResponse(deadlineResponse)

        if case .failure(let message, let httpStatus, let errorCode, _) = deadlineResult {
            XCTAssertEqual(httpStatus, 409)
            XCTAssertEqual(errorCode, "WORK_REPORT_DEADLINE_PASSED")
            XCTAssertEqual(message, "Đã quá hạn nộp báo cáo.")
        } else {
            XCTFail("Expected failure for WORK_REPORT_DEADLINE_PASSED")
        }

        // HTTP 409 with version conflict (`WORK_REPORT_VERSION_CONFLICT`)
        let versionConflictResponse = BackendHttpResponse(
            statusCode: 409,
            headers: [:],
            rawData: Data(),
            rawBody: "{\"code\":\"WORK_REPORT_VERSION_CONFLICT\",\"message\":\"Xung đột phiên bản\"}",
            jsonObject: ["code": "WORK_REPORT_VERSION_CONFLICT", "message": "Xung đột phiên bản"],
            errorCode: "WORK_REPORT_VERSION_CONFLICT",
            errorMessage: "Xung đột phiên bản"
        )
        let conflictResult: MiniBiApiResult<WorkReportObligationItem> =
            WorkReportsAndReportingService.handleWorkReportConflictResponse(versionConflictResponse)

        if case .failure(let message, let httpStatus, let errorCode, _) = conflictResult {
            XCTAssertEqual(httpStatus, 409)
            XCTAssertEqual(errorCode, "WORK_REPORT_VERSION_CONFLICT")
            XCTAssertNotEqual(message, "Đã quá hạn nộp báo cáo.")
        } else {
            XCTFail("Expected failure for WORK_REPORT_VERSION_CONFLICT")
        }
    }

    // MARK: - 6. Attendance Time Anchor Monotonic Clock Tests

    func testAttendanceTimeAnchorMonotonicEvaluation() throws {
        final class MockClock: MonotonicClockProviding {
            var uptimeMs: Int64 = 100_000
            var epochMs: Int64 = 1_760_000_000_000
            var bootId: String = "boot-test-1"

            func elapsedRealtimeMs() -> Int64 { uptimeMs }
            func currentEpochMs() -> Int64 { epochMs }
            func bootSessionId() -> String { bootId }
        }

        let mockClock = MockClock()
        let keychain = KeychainStore(serviceName: "com.aistudio.minibi.anchor.\(UUID().uuidString)")
        let manager = AttendanceTimeAnchorManager(keychain: keychain, clock: mockClock)

        // Initially missing
        XCTAssertEqual(manager.evaluateAnchor().status, .missing)

        // Seed valid anchor
        let anchor = ServerTimeAnchor(
            serverEpochMsAtSync: 1_760_000_000_000,
            serverNowIso: "2025-10-09T08:53:20.000Z",
            syncedElapsedRealtimeMs: 100_000,
            syncedDeviceEpochMs: 1_760_000_000_000,
            timeToken: "tok-123",
            expiresAtEpochMs: 1_760_000_000_000 + 3_600_000,
            bootSessionId: "boot-test-1"
        )
        manager.seedAnchorForTesting(anchor)

        // Advance monotonic clock by 30 seconds, even if user tampered with device wall clock (+5 hours)
        mockClock.uptimeMs = 130_000
        mockClock.epochMs = 1_760_000_000_000 + (5 * 3_600_000)

        let captured = manager.resolveCaptureTime()
        XCTAssertEqual(captured.timeSource, AttendanceTimeContract.sourceServerAnchored)
        XCTAssertEqual(captured.capturedEpochMs, 1_760_000_030_000)
        XCTAssertEqual(captured.elapsedSinceAnchorMs, 30_000)

        // Simulate device reboot -> must detect reboot and fall back to DEVICE_CLOCK_UNVERIFIED
        mockClock.bootId = "boot-test-2"
        XCTAssertEqual(manager.evaluateAnchor().status, .rebootDetected)
        XCTAssertEqual(manager.resolveCaptureTime().timeSource, AttendanceTimeContract.sourceDeviceClockUnverified)
    }

    // MARK: - Helper

    private func makeSolidTestJpeg(color: UIColor, size: CGSize) throws -> Data {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let img = renderer.image { ctx in
            color.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
        }
        return try XCTUnwrap(img.jpegData(compressionQuality: 0.85))
    }
}
