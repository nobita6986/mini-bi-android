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
            BackendHttpClient.normalizeBaseUrl("https://bi.hrpvietnam.com/api", enforceHttps: false),
            "https://bi.hrpvietnam.com"
        )
        let stagingEnv = AppEnvironmentConfig.forVariant(.staging)
        XCTAssertEqual(stagingEnv.defaultBaseUrl, "https://bi.hrpvietnam.com")
        XCTAssertEqual(stagingEnv.variant, .staging)
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

        // 5. CANCELLED obligation (from a revoked assignment) with `is_overdue = false` and `NOT_STARTED`
        //    MUST NOT appear in `actionableObligations` or `historyObligations` and MUST NOT be editable
        let cancelledNotStarted = WorkReportsAndReportingService.parseObligationItem([
            "obligation_id": 105,
            "template_name": "Báo cáo công việc hằng ngày",
            "period_type": "DAILY",
            "period_key": "2026-11-05",
            "due_at": "2026-11-05T10:00:00Z",
            "obligation_status": "CANCELLED",
            "submission_state": "NOT_STARTED",
            "is_overdue": false
        ])
        XCTAssertEqual(cancelledNotStarted.obligationStatus, "CANCELLED")
        XCTAssertFalse(WorkReportsContract.isActionableObligation(cancelledNotStarted))
        XCTAssertFalse(WorkReportsContract.isHistoryObligation(cancelledNotStarted))
        XCTAssertFalse(WorkReportsContract.isEditableObligation(cancelledNotStarted))
        XCTAssertNotNil(BackendHttpClient.codeMessagesVi["WORK_REPORT_ACCESS_DENIED"])

        // 6. Verify ViewModel partitioning between `actionableObligations` and `historyObligations`
        let vm = MiniBiViewModel()
        vm.workReportObligations = [overdueNotStarted, overdueDraft, rejectedPastDue, legacyDraft, cancelledNotStarted]
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

        // Verify canonical `/api/attendance/time` payload with `server_time` key is parsed without HTTP Date header
        mockClock.bootId = "boot-test-3"
        let canonicalParsed = try XCTUnwrap(
            manager.parseServerTimeResponse(
                root: [
                    "ok": true,
                    "server_time": "2026-11-02T14:40:00.000+07:00",
                    "expires_at": "2026-11-03T02:40:00.000+07:00",
                    "time_token": "tok-canonical-time"
                ],
                rawBody: "",
                headers: [:],
                syncedElapsedMs: 200_000,
                deviceNowMs: 1_760_000_000_000
            )
        )
        XCTAssertEqual(canonicalParsed.serverNowIso, "2026-11-02T14:40:00.000+07:00")
        XCTAssertEqual(canonicalParsed.timeToken, "tok-canonical-time")
        XCTAssertTrue(AttendanceTimeAnchorManager.formatEpochMillisInHcmIso(canonicalParsed.serverEpochMsAtSync).hasSuffix("+07:00"))
    }

    func testBuildFullProfilePayloadVendorAndEmptyDisplayNameContract() throws {
        var draft = NewWorkerEntryDraft()
        draft.projectId = "proj-minh-duc"
        draft.duAn = "Công Ty Tnhh In Điện Tử Minh Đức"
        draft.providerType = "vendor"
        draft.recruiterId = "11111111-2222-4333-8444-555555555555"
        draft.nguoiTuyen = "VD Trí Star"
        draft.laborTypeKey = "TEMPORARY"
        draft.nganhNghe = "Thời vụ"
        draft.ngayVaoLam = "02/11/2026"
        draft.trangThai = "Đang làm"
        draft.soCccd = "010207004199"
        draft.hoTen = "" // left blank intentionally when 12-digit CCCD is provided

        let payload = DirectEntryService.shared.buildFullProfileEntryPayload(draft)
        XCTAssertEqual(payload["project_id"] as? String, "proj-minh-duc")
        XCTAssertEqual(payload["provider_type"] as? String, "vendor")
        XCTAssertEqual(payload["recruiter_id"] as? String, "11111111-2222-4333-8444-555555555555")
        XCTAssertNil(payload["vendor_id"])
        XCTAssertEqual(payload["display_name"] as? String, "")
        XCTAssertFalse(payload["display_name"] is NSNull)
        XCTAssertEqual(payload["first_work_date"] as? String, "2026-11-02")
    }

    func testAttendanceOutboxPreservesLocalPhotoAndResolvesServerPhotoUrl() throws {
        let localItem = AttendanceOutboxItem(
            localId: "local-uuid-1",
            userKey: "nv001",
            idempotencyKey: "local-uuid-1",
            clientCapturedAt: "2026-11-02T14:40:00.000+07:00",
            timeSource: AttendanceTimeContract.sourceServerAnchored,
            lat: 21.0285,
            lng: 105.8542,
            photoLocalPath: "/tmp/local-uuid-1.jpg",
            photoSha256: "abc123",
            photoSizeBytes: 1024,
            status: AttendanceContract.statusSynced,
            createdAtEpochMs: 1_762_069_200_000
        )
        let serverHistoryItem = AttendanceOutboxItem(
            localId: "srv-checkin-uuid-99",
            userKey: "nv001",
            idempotencyKey: "local-uuid-1",
            clientCapturedAt: "2026-11-02T14:40:00.000+07:00",
            timeSource: AttendanceTimeContract.sourceServerAnchored,
            lat: 21.0285,
            lng: 105.8542,
            photoSha256: "abc123",
            photoSizeBytes: 1024,
            status: AttendanceContract.statusSynced,
            serverCheckinId: "checkin-uuid-99",
            createdAtEpochMs: 1_762_069_200_000
        )

        let deduped = AttendanceOutboxService.deduplicateItems([serverHistoryItem, localItem])
        XCTAssertEqual(deduped.count, 1)
        XCTAssertEqual(deduped[0].photoLocalPath, "/tmp/local-uuid-1.jpg")
        XCTAssertEqual(deduped[0].serverCheckinId, "checkin-uuid-99")
        XCTAssertEqual(
            AttendanceOutboxService.resolveRemotePhotoPath(for: deduped[0]),
            "/api/attendance/checkins/checkin-uuid-99/photo"
        )
    }

    // MARK: - 7. Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") Contract Tests

    @MainActor
    func testLeaveTabVisibilityByAuthority() throws {
        // 1. Regular employee (non-approver) -> sees Check-in, Explanation, and Leave Request (with Explanation immediately before Leave Request), NEVER approval tabs
        let employeeAuth = LeaveAuthorityProjection(
            appUserId: "11111111-2222-4333-8444-555555555555",
            isSharedNonEmployee: false,
            canSubmitRequest: true,
            canApproveRequests: false
        )
        let empTabs = LeaveContract.resolveVisibleAttendanceTabs(authority: employeeAuth, errorCode: nil)
        XCTAssertEqual(empTabs, [.checkIn, .explanation, .leaveRequest])
        XCTAssertEqual(empTabs.map(\.label), ["Chấm công", "Giải trình chấm công", "Xin nghỉ phép"])

        // 2. Approver (Team Leader / Department Head / Admin / BoD) -> sees all 5 tabs in exact order
        let approverAuth = LeaveAuthorityProjection(
            appUserId: "99999999-2222-4333-8444-555555555555",
            isSharedNonEmployee: false,
            isTeamLeader: true,
            canSubmitRequest: true,
            canApproveRequests: true
        )
        let approverTabs = LeaveContract.resolveVisibleAttendanceTabs(authority: approverAuth, errorCode: nil)
        XCTAssertEqual(
            approverTabs,
            [.checkIn, .explanation, .leaveRequest, .explanationApproval, .leaveApproval]
        )
        XCTAssertEqual(
            approverTabs.map(\.label),
            ["Chấm công", "Giải trình chấm công", "Xin nghỉ phép", "Xét duyệt giải trình", "Duyệt nghỉ"]
        )

        // 3. Shared non-employee account -> sees only Check-in
        let sharedAuth = LeaveAuthorityProjection(
            appUserId: "77777777-2222-4333-8444-555555555555",
            isSharedNonEmployee: true,
            canSubmitRequest: false,
            canApproveRequests: false
        )
        XCTAssertEqual(
            LeaveContract.resolveVisibleAttendanceTabs(authority: sharedAuth, errorCode: nil),
            [.checkIn]
        )
        XCTAssertEqual(
            LeaveContract.resolveVisibleAttendanceTabs(authority: nil, errorCode: "LEAVE_SHARED_ACCOUNT_FORBIDDEN"),
            [.checkIn]
        )
        XCTAssertEqual(
            LeaveContract.resolveVisibleAttendanceTabs(
                authority: nil,
                errorCode: nil,
                explanationErrorCode: "ATTENDANCE_EXPLANATION_ACCESS_DENIED"
            ),
            [.checkIn]
        )

        // 4. ViewModel defaults to `.checkIn` and prevents selecting `.leaveApproval` or `.explanationApproval` when not authorized
        let vm = MiniBiViewModel()
        XCTAssertEqual(vm.activeAttendanceTab, .checkIn)
        vm.selectAttendanceTab(.leaveApproval)
        XCTAssertEqual(vm.activeAttendanceTab, .checkIn)
        vm.selectAttendanceTab(.explanationApproval)
        XCTAssertEqual(vm.activeAttendanceTab, .checkIn)
    }

    func testLeaveSummaryAndApprovalInboxParsingAndUnitFormatting() throws {
        let userId = "11111111-2222-4333-8444-555555555555"
        let requestId = "aaaa1111-2222-4333-8444-555555555555"
        let meJson: [String: Any] = [
            "ok": true,
            "data": [
                "as_of_date": "2026-10-27",
                "leave_year": 2026,
                "account": [
                    "app_user_id": userId,
                    "display_name": "Nhân sự Thực Địa",
                    "login_id": "ns01",
                    "personnel_code": "NV-001",
                    "recruiter_id": "rec-01",
                    "account_enabled": true
                ],
                "authority": [
                    "app_user_id": userId,
                    "is_shared_non_employee": false,
                    "is_full_admin": false,
                    "is_bod": false,
                    "is_hr_operator": false,
                    "is_hr_report_viewer": false,
                    "is_team_leader": false,
                    "is_department_approver": false,
                    "can_submit_request": true,
                    "can_approve_requests": false,
                    "can_view_monthly_report": false,
                    "can_manage_config": false
                ],
                "routing": [
                    "route_kind": "TEAM_LEADER",
                    "unit_kind": "RECRUITMENT",
                    "primary_approver_app_user_id": "99999999-2222-4333-8444-555555555555",
                    "team_id": "team-uuid-1",
                    "team_code": "TEAM_NORTH",
                    "team_name": "Team Miền Bắc",
                    "profile_configured": true
                ],
                "policy": [
                    "annual_base_units": 24,
                    "monthly_accrual_units": 2,
                    "default_seniority_years": 5,
                    "default_seniority_step_units": 2,
                    "carry_over_expiry_month": 3,
                    "carry_over_expiry_day": 31,
                    "carry_over_expires_after_date": "2026-03-31",
                    "row_version": 2
                ],
                "balance": [
                    "leave_year": 2026,
                    "as_of_date": "2026-10-27",
                    "has_profile": true,
                    "profile_configured": true,
                    "employment_status": "OFFICIAL",
                    "unit_kind": "RECRUITMENT",
                    "work_start_date": "2025-01-01",
                    "official_effective_date": "2025-03-01",
                    "next_seniority_effective_date": "2030-01-01",
                    "next_seniority_units": 2,
                    "total_granted_units": 20,
                    "carry_over_granted_units": 2,
                    "carry_over_available_units": 0,
                    "monthly_accrued_units": 18,
                    "seniority_granted_units": 0,
                    "opening_or_adjustment_units": 0,
                    "available_units": 14,
                    "reserved_units": 2,
                    "consumed_units": 4,
                    "expired_units": 0,
                    "pending_paid_units": 3,
                    "pending_unpaid_units": 0,
                    "approved_unpaid_units": 0,
                    "lots": [
                        [
                            "lot_id": "lot-10",
                            "leave_year": 2026,
                            "lot_kind": "MONTHLY_ACCRUAL",
                            "accrual_month": 10,
                            "granted_units": 2,
                            "reserved_units": 0,
                            "consumed_units": 0,
                            "expired_units": 0,
                            "available_units": 2,
                            "usable_from_date": "2026-10-01"
                        ]
                    ]
                ],
                "requests": [
                    [
                        "request_id": requestId,
                        "app_user_id": userId,
                        "status": "PENDING",
                        "pay_treatment": "PAID_LEAVE",
                        "employment_status_snapshot": "OFFICIAL",
                        "unit_kind_snapshot": "RECRUITMENT",
                        "profile_configured_at_submit": true,
                        "requested_units": 3,
                        "reason": "Việc gia đình",
                        "route_kind": "TEAM_LEADER",
                        "routed_team_name": "Team Miền Bắc",
                        "submitted_at": "2026-10-27T01:00:00Z",
                        "row_version": 3,
                        "days": [
                            [
                                "request_day_id": "day-1",
                                "leave_date": "2026-10-28",
                                "session_code": "FULL",
                                "half_day_units": 2,
                                "is_active": true
                            ],
                            [
                                "request_day_id": "day-2",
                                "leave_date": "2026-10-29",
                                "session_code": "AM",
                                "half_day_units": 1,
                                "is_active": true
                            ]
                        ],
                        "allocations": []
                    ]
                ],
                "ledger_entries": []
            ]
        ]

        let parsedResult = LeaveContract.parseMyLeaveSummary(meJson)
        guard case .success(let summary, _) = parsedResult else {
            XCTFail("Expected parseMyLeaveSummary to succeed")
            return
        }
        XCTAssertEqual(summary.leaveYear, 2026)
        XCTAssertEqual(summary.balance.availableUnits, 14)
        XCTAssertEqual(LeaveContract.formatHalfDayUnits(summary.balance.availableUnits), "7 ngày")
        XCTAssertEqual(LeaveContract.formatHalfDayUnits(3), "1,5 ngày")
        XCTAssertEqual(LeaveContract.formatIsoDateVi("2026-10-28"), "28/10/2026")
        XCTAssertEqual(summary.balance.lots.first?.accrualMonth, 10)
        XCTAssertEqual(LeaveContract.leaveLotKindLabel("MONTHLY_ACCRUAL", accrualMonth: 10), "Phép định kỳ tháng 10")
        XCTAssertEqual(summary.requests.count, 1)
        let firstReq = try XCTUnwrap(summary.requests.first)
        XCTAssertTrue(LeaveContract.canWithdrawOwnLeaveRequest(actorAppUserId: userId, request: firstReq))
        XCTAssertFalse(LeaveContract.canWithdrawOwnLeaveRequest(actorAppUserId: "other-user", request: firstReq))

        // Parse approval inbox
        let inboxJson: [String: Any] = [
            "ok": true,
            "data": [
                "as_of_date": "2026-10-27",
                "status_filter": "PENDING",
                "total": 1,
                "limit": 50,
                "offset": 0,
                "items": summary.requests.map { req in
                    [
                        "request_id": req.requestId,
                        "app_user_id": req.appUserId,
                        "status": req.status,
                        "pay_treatment": req.payTreatment,
                        "requested_units": req.requestedUnits,
                        "reason": req.reason,
                        "row_version": req.rowVersion
                    ]
                }
            ]
        ]
        guard case .success(let inbox, _) = LeaveContract.parseApprovalInbox(inboxJson) else {
            XCTFail("Expected parseApprovalInbox to succeed")
            return
        }
        XCTAssertEqual(inbox.total, 1)
        XCTAssertEqual(inbox.items.first?.requestId, requestId)
        XCTAssertEqual(inbox.items.first?.rowVersion, 3)
    }

    func testLeavePayloadBuildersAndValidationRules() throws {
        // 1. Create request payload (`FULL`, `AM`, `PM`, with `leave_type`)
        let days = [
            LeaveDraftDayInput(leaveDate: "2026-11-02", sessionCode: "FULL", leaveType: "ANNUAL"),
            LeaveDraftDayInput(leaveDate: "2026-11-03", sessionCode: "PM", leaveType: "SICK")
        ]
        XCTAssertNil(LeaveContract.validateCreateDraftInput(days: days, reason: ""))
        let createPayload = LeaveContract.buildCreateLeaveRequestPayload(
            days: days,
            reason: "  Khám sức khỏe  ",
            idempotencyKey: "leave-create-key-1"
        )
        XCTAssertEqual(createPayload["reason"] as? String, "Khám sức khỏe")
        XCTAssertEqual(createPayload["idempotency_key"] as? String, "leave-create-key-1")
        let payloadDays = try XCTUnwrap(createPayload["days"] as? [[String: String]])
        XCTAssertEqual(payloadDays.count, 2)
        XCTAssertEqual(payloadDays[0]["leave_date"], "2026-11-02")
        XCTAssertEqual(payloadDays[0]["session_code"], "FULL")
        XCTAssertEqual(payloadDays[0]["leave_type"], "ANNUAL")
        XCTAssertEqual(payloadDays[1]["leave_date"], "2026-11-03")
        XCTAssertEqual(payloadDays[1]["session_code"], "PM")
        XCTAssertEqual(payloadDays[1]["leave_type"], "SICK")

        // Same date with same leave_type is rejected, but AM + PM with different leave_type is allowed
        let dupSameTypeDays = [
            LeaveDraftDayInput(leaveDate: "2026-11-02", sessionCode: "AM", leaveType: "ANNUAL"),
            LeaveDraftDayInput(leaveDate: "2026-11-02", sessionCode: "PM", leaveType: "ANNUAL")
        ]
        XCTAssertNotNil(LeaveContract.validateCreateDraftInput(days: dupSameTypeDays, reason: ""))

        let splitDiffTypeDays = [
            LeaveDraftDayInput(leaveDate: "2026-11-02", sessionCode: "AM", leaveType: "ANNUAL"),
            LeaveDraftDayInput(leaveDate: "2026-11-02", sessionCode: "PM", leaveType: "UNPAID")
        ]
        XCTAssertNil(LeaveContract.validateCreateDraftInput(days: splitDiffTypeDays, reason: "", leaveYear: 2026, availableUnits: 1))

        let autoSplit = LeaveContract.autoAllocateDraftDaysByBalance(
            days: [LeaveDraftDayInput(leaveDate: "2026-11-05", sessionCode: "FULL", leaveType: "ANNUAL")],
            availableUnits: 1
        )
        XCTAssertEqual(autoSplit.count, 2)
        XCTAssertEqual(autoSplit[0].sessionCode, "AM")
        XCTAssertEqual(autoSplit[0].leaveType, "ANNUAL")
        XCTAssertEqual(autoSplit[1].sessionCode, "PM")
        XCTAssertEqual(autoSplit[1].leaveType, "UNPAID")

        // Date range generator (inclusive range + optional skipSundays + reversed range rejection)
        XCTAssertEqual(LeaveContract.nextIsoDateAfter("2026-11-01"), "2026-11-02")
        let rangeResult = LeaveContract.generateDraftDaysFromRange(
            startDateIso: "2026-10-30", // Friday
            endDateIso: "2026-11-02",   // Monday (includes Sunday 2026-11-01)
            leaveType: "ANNUAL",
            skipSundays: true
        )
        XCTAssertNil(rangeResult.errorMessage)
        XCTAssertEqual(rangeResult.days.map(\.leaveDate), ["2026-10-30", "2026-10-31", "2026-11-02"])

        let reversedRange = LeaveContract.generateDraftDaysFromRange(
            startDateIso: "2026-11-05",
            endDateIso: "2026-11-02"
        )
        XCTAssertNotNil(reversedRange.errorMessage)
        XCTAssertTrue(reversedRange.days.isEmpty)

        // 2. Withdraw payload
        let withdrawPayload = LeaveContract.buildWithdrawLeaveRequestPayload(
            expectedRowVersion: 3,
            reason: "Đã thu xếp công việc",
            idempotencyKey: "leave-withdraw-key-1"
        )
        XCTAssertEqual(withdrawPayload["expected_row_version"] as? Int, 3)
        XCTAssertEqual(withdrawPayload["reason"] as? String, "Đã thu xếp công việc")
        XCTAssertEqual(withdrawPayload["idempotency_key"] as? String, "leave-withdraw-key-1")

        // 3. Approver decision validation & payload (`APPROVE`, `REJECT`, `CANCEL_APPROVED`)
        let pendingReq = LeaveRequestProjection(
            requestId: "bbbb1111-2222-4333-8444-555555555555",
            appUserId: "11111111-2222-4333-8444-555555555555",
            status: "PENDING",
            rowVersion: 2
        )
        let approverId = "99999999-2222-4333-8444-555555555555"

        // Self-approval blocked
        let selfCheck = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: pendingReq.appUserId,
            request: pendingReq,
            decision: "APPROVE",
            reason: ""
        )
        XCTAssertFalse(selfCheck.isValid)

        // REJECT allows optional empty reason, but blocks >2000 chars
        let rejectEmpty = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: approverId,
            request: pendingReq,
            decision: "REJECT",
            reason: "   "
        )
        XCTAssertTrue(rejectEmpty.isValid)

        let rejectTooLong = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: approverId,
            request: pendingReq,
            decision: "REJECT",
            reason: String(repeating: "x", count: 2001)
        )
        XCTAssertFalse(rejectTooLong.isValid)
        XCTAssertEqual(rejectTooLong.errorMessage, "Lý do xử lý không được vượt quá 2000 ký tự.")

        let rejectValid = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: approverId,
            request: pendingReq,
            decision: "REJECT",
            reason: "Thiếu nhân sự trực ca"
        )
        XCTAssertTrue(rejectValid.isValid)

        // CANCEL_APPROVED requires APPROVED status and allows optional reason
        let approvedReq = LeaveRequestProjection(
            requestId: "bbbb1111-2222-4333-8444-555555555555",
            appUserId: "11111111-2222-4333-8444-555555555555",
            status: "APPROVED",
            rowVersion: 4
        )
        let cancelEmpty = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: approverId,
            request: approvedReq,
            decision: "CANCEL_APPROVED",
            reason: ""
        )
        XCTAssertTrue(cancelEmpty.isValid)

        let cancelValid = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: approverId,
            request: approvedReq,
            decision: "CANCEL_APPROVED",
            reason: "Nhân sự đi làm lại"
        )
        XCTAssertTrue(cancelValid.isValid)

        let decisionPayload = LeaveContract.buildDecideLeaveRequestPayload(
            decision: "CANCEL_APPROVED",
            expectedRowVersion: 4,
            reason: "Nhân sự đi làm lại",
            idempotencyKey: "leave-decision-key-1"
        )
        XCTAssertEqual(decisionPayload["decision"] as? String, "CANCEL_APPROVED")
        XCTAssertEqual(decisionPayload["expected_row_version"] as? Int, 4)
        XCTAssertEqual(decisionPayload["reason"] as? String, "Nhân sự đi làm lại")
        XCTAssertEqual(decisionPayload["idempotency_key"] as? String, "leave-decision-key-1")

        // Error code mapping
        XCTAssertEqual(
            LeaveContract.errorMessageForCode("LEAVE_INSUFFICIENT_BALANCE"),
            "Số dư Phép năm khả dụng không đủ cho các ngày đăng ký loại Phép năm. Vui lòng chuyển phần ngày vượt sang Nghỉ không lương hoặc loại nghỉ phù hợp."
        )
        XCTAssertEqual(
            BackendHttpClient.codeMessagesVi["LEAVE_ROW_VERSION_CONFLICT"],
            "Dữ liệu vừa được cập nhật bởi thao tác khác. Hệ thống đã làm mới, vui lòng kiểm tra và thử lại."
        )
        XCTAssertEqual(
            BackendHttpClient.codeMessagesVi["AUTH_INVALID_CREDENTIALS"],
            "Sai thông tin đăng nhập. Vui lòng kiểm tra lại tài khoản và mật khẩu."
        )
        XCTAssertEqual(
            BackendHttpClient.codeMessagesVi["AUTH_REQUEST_INVALID"],
            "Vui lòng nhập đầy đủ tài khoản (email) và mật khẩu hợp lệ."
        )
    }

    func testAuthActorParsingAndWorkerDirectoryRowParsing() throws {
        let actorJson: [String: Any] = [
            "app_user_id": "11111111-2222-4333-8444-555555555555",
            "display_name": "Phạm Thị Sâm",
            "capabilities": ["entry_create", "entry_submit", "entry_read"],
            "scopes": [
                ["kind": "team", "project_id": NSNull(), "team_id": "team-1"]
            ]
        ]
        let parsedUser = MiniBiRepository.parseUser(
            actorJson,
            fallbackRoot: ["ok": true, "actor": actorJson],
            fallbackIdentifier: "sampt.td@hrpartner.vn"
        )
        XCTAssertEqual(parsedUser.id, "11111111-2222-4333-8444-555555555555")
        XCTAssertEqual(parsedUser.fullName, "Phạm Thị Sâm")
        XCTAssertEqual(parsedUser.role, "TEAM_LEADER")
        XCTAssertEqual(parsedUser.roleLabel, "Quản lý nhóm")
        // Missing allowed_worker_tabs in /api/auth/session fails closed to []
        XCTAssertEqual(parsedUser.allowedWorkerScopes, [])

        // Merging canonical /api/direct-entry/session with matching app_user_id populates allowedWorkerTabs
        let mergedTabs = MiniBiRepository.parseDirectEntrySessionAllowedWorkerTabs(
            from: [
                "ok": true,
                "actor": [
                    "app_user_id": "11111111-2222-4333-8444-555555555555",
                    "allowed_worker_tabs": ["team", "managed", "recruited"]
                ]
            ],
            expectedAppUserId: parsedUser.id
        )
        XCTAssertEqual(mergedTabs, ["team", "managed", "recruited"])

        let workerRowJson: [String: Any] = [
            "entry_id": "aaaa1111-2222-4333-8444-555555555555",
            "employee_code": "hrp-2026-000123",
            "display_name": "Nguyễn Văn Công",
            "project_id": "LUXSHARE-BG",
            "project_display": "Luxshare Bắc Giang",
            "first_work_date": "2026-10-01",
            "labor_type": "SEASONAL",
            "labor_type_display": "Thời vụ",
            "employment_status": "ON",
            "recruiter_id": "11111111-2222-4333-8444-555555555555",
            "recruiter_display": "Phạm Thị Sâm",
            "payment": [
                "state": "provided",
                "account_number": "190366778899",
                "bank_id": "TECHCOMBANK",
                "account_holder_name": "NGUYEN VAN CONG",
                "version": 1
            ]
        ]
        let parsedWorker = MiniBiRepository.parseWorkerItem(workerRowJson)
        XCTAssertEqual(parsedWorker.maNld, "aaaa1111-2222-4333-8444-555555555555")
        XCTAssertEqual(parsedWorker.employeeCode, "hrp-2026-000123")
        XCTAssertEqual(parsedWorker.displayEmployeeCode, "hrp-2026-000123")
        XCTAssertEqual(parsedWorker.hoTen, "Nguyễn Văn Công")
        XCTAssertEqual(parsedWorker.duAn, "Luxshare Bắc Giang")
        XCTAssertEqual(parsedWorker.nguoiTuyen, "Phạm Thị Sâm")
        XCTAssertEqual(parsedWorker.trangThai, "Đang làm việc")
        XCTAssertEqual(parsedWorker.soTaiKhoan, "190366778899")
        XCTAssertEqual(parsedWorker.nganHang, "TECHCOMBANK")

        // When worker row has UUID entry_id and no employee_code, displayEmployeeCode must be empty (never show UUID)
        let uuidOnlyWorker = MiniBiRepository.parseWorkerItem([
            "entry_id": "23850c6b-7e11-4db3-b97d-63c4b7ff64d9",
            "display_name": "Lý Minh Khôi",
            "employment_status": "ON"
        ])
        XCTAssertEqual(uuidOnlyWorker.maNld, "23850c6b-7e11-4db3-b97d-63c4b7ff64d9")
        XCTAssertEqual(uuidOnlyWorker.displayEmployeeCode, "")

        // Production schema_json parsing (top-level `sections` + `fields` with `field_key`, `section_key`, `options`)
        let prodSchema: [String: Any] = [
            "sections": [
                ["section_key": "general", "label": "Thông tin chung", "sort_order": 1]
            ],
            "fields": [
                [
                    "field_key": "interview_count",
                    "section_key": "general",
                    "label": "Số lượng phỏng vấn",
                    "field_type": "number",
                    "required": true,
                    "sort_order": 1
                ],
                [
                    "field_key": "shift_type",
                    "section_key": "general",
                    "label": "Ca làm việc",
                    "field_type": "single_select",
                    "required": true,
                    "sort_order": 2,
                    "options": [
                        ["option_key": "DAY", "label": "Ca ngày"],
                        ["option_key": "NIGHT", "label": "Ca đêm"]
                    ]
                ],
                [
                    "field_key": "has_issue",
                    "section_key": "general",
                    "label": "Có phát sinh sự cố",
                    "field_type": "boolean",
                    "required": false,
                    "sort_order": 3
                ]
            ]
        ]
        let parsedSections = WorkReportsContract.parseDynamicSections(prodSchema)
        XCTAssertEqual(parsedSections.count, 1)
        XCTAssertEqual(parsedSections[0].title, "Thông tin chung")
        XCTAssertEqual(parsedSections[0].fields.map(\.id), ["interview_count", "shift_type", "has_issue"])
        XCTAssertEqual(parsedSections[0].fields[1].options.map(\.value), ["DAY", "NIGHT"])
        XCTAssertEqual(parsedSections[0].fields[1].options.map(\.label), ["Ca ngày", "Ca đêm"])

        // Empty schema must NOT fabricate fake fields
        XCTAssertTrue(WorkReportsContract.parseDynamicSections(nil).isEmpty)

        // Typed payload builder converts string values according to field types
        let typedPayload = WorkReportsContract.buildTypedPayloadJson(
            formPayload: [
                "interview_count": "15",
                "shift_type": "DAY",
                "has_issue": "true"
            ],
            sections: parsedSections
        )
        XCTAssertEqual((typedPayload["interview_count"] as? NSNumber)?.intValue, 15)
        XCTAssertEqual(typedPayload["shift_type"] as? String, "DAY")
        XCTAssertEqual(typedPayload["has_issue"] as? Bool, true)

        // Daily work-report detail envelope parsing + boolean "Không" (false) validation & mutation body contract
        let detailEnvelope: [String: Any] = [
            "ok": true,
            "data": [
                "obligation": [
                    "obligation_id": "3a420bf0-1111-4222-8333-444455556666",
                    "template_id": "tpl-daily-1",
                    "period_type": "DAILY",
                    "period_key": "2026-10-29",
                    "due_at": "2026-10-29T16:59:59Z",
                    "submission_state": "NOT_STARTED",
                    "submission_version": NSNull(),
                    "version": 1,
                    "is_overdue": false
                ],
                "template": [
                    "template_code": "DAILY_RECRUITER",
                    "display_name": "Báo cáo công việc hằng ngày",
                    "schema_json": [
                        "sections": [
                            ["section_key": "s1", "label": "7h30-9h30", "sort_order": 1]
                        ],
                        "fields": [
                            [
                                "field_key": "guide_contract",
                                "section_key": "s1",
                                "label": "Hướng dẫn người lao động ký cam kết và hợp đồng lao động.",
                                "field_type": "boolean",
                                "required": true,
                                "sort_order": 1
                            ],
                            [
                                "field_key": "support_interview",
                                "section_key": "s1",
                                "label": "Hỗ trợ người lao động trong quá trình phỏng vấn tại hiện trường.",
                                "field_type": "boolean",
                                "required": true,
                                "sort_order": 2
                            ]
                        ]
                    ]
                ],
                "submission": NSNull(),
                "revisions": []
            ]
        ]
        let dataObj = try XCTUnwrap(detailEnvelope["data"] as? [String: Any])
        let obObj = try XCTUnwrap(dataObj["obligation"] as? [String: Any])
        let parsedDetail = WorkReportsAndReportingService.parseObligationItem(obObj, fallbackRoot: dataObj)
        XCTAssertEqual(parsedDetail.obligationIdUuid, "3a420bf0-1111-4222-8333-444455556666")
        XCTAssertEqual(parsedDetail.templateName, "Báo cáo công việc hằng ngày")
        XCTAssertEqual(parsedDetail.periodKey, "2026-10-29")
        XCTAssertEqual(parsedDetail.dueAt, "2026-10-29T16:59:59Z")
        // NOT_STARTED obligation must keep submissionVersion == nil even when obligation.version == 1
        XCTAssertNil(parsedDetail.submissionVersion)
        // Untouched boolean toggles default to "false" ("Không")
        XCTAssertEqual(parsedDetail.formPayload["guide_contract"], "false")
        XCTAssertEqual(parsedDetail.formPayload["support_interview"], "false")
        // Required boolean toggles left at "Không" ("false") must pass submit validation!
        XCTAssertNil(
            WorkReportsContract.validateFormPayloadForSubmit(
                formPayload: parsedDetail.formPayload,
                sections: parsedDetail.sections
            )
        )
        let boolPayloadJson = WorkReportsContract.buildTypedPayloadJson(
            formPayload: ["guide_contract": "false", "support_interview": "true"],
            sections: parsedDetail.sections
        )
        XCTAssertEqual(boolPayloadJson["guide_contract"] as? Bool, false)
        XCTAssertEqual(boolPayloadJson["support_interview"] as? Bool, true)
        let mutationBody = WorkReportsContract.buildSubmissionMutationBody(
            payloadJson: boolPayloadJson,
            expectedVersion: parsedDetail.submissionVersion,
            idempotencyKey: "idem-123"
        )
        XCTAssertEqual(Set(mutationBody.keys), Set(["payload_json", "expected_version", "idempotency_key"]))
        XCTAssertTrue(mutationBody["expected_version"] is NSNull)

        // AttendanceOutboxService deduplication collapses duplicate server history items into 1 entry
        let dupItems: [AttendanceOutboxItem] = (0..<15).map { _ in
            AttendanceOutboxItem(
                localId: "srv-1761708312000",
                userKey: "khanhhv.td",
                idempotencyKey: "srv-1761708312000",
                clientCapturedAt: "2026-10-29T03:25:12.000Z",
                timeSource: AttendanceTimeContract.sourceServerAnchored,
                lat: 21.31381,
                lng: 105.68087,
                photoSha256: "abc",
                photoSizeBytes: 1024,
                status: AttendanceContract.statusSynced,
                attemptCount: 1,
                serverCheckinId: "checkin-uuid-1",
                createdAtEpochMs: 1761708312000
            )
        }
        let deduplicated = AttendanceOutboxService.deduplicateItems(dupItems)
        XCTAssertEqual(deduplicated.count, 1)
        XCTAssertEqual(deduplicated.first?.serverCheckinId, "checkin-uuid-1")

        // Workforce-deep query item contract: all-time requires `mode=deep&range=all`, date-filtered uses `from` & `to`
        let allTimeQuery = WorkReportsAndReportingService.buildWorkforceDeepQueryItems(startDate: "", endDate: "")
        XCTAssertEqual(allTimeQuery, [
            URLQueryItem(name: "mode", value: "deep"),
            URLQueryItem(name: "range", value: "all")
        ])

        let periodQuery = WorkReportsAndReportingService.buildWorkforceDeepQueryItems(
            startDate: "2026-10-01",
            endDate: "2026-10-27"
        )
        XCTAssertEqual(periodQuery, [
            URLQueryItem(name: "mode", value: "deep"),
            URLQueryItem(name: "from", value: "2026-10-01"),
            URLQueryItem(name: "to", value: "2026-10-27")
        ])

        // Navigation menu order: Tổng quan - Báo cáo - Chấm công - Nhân sự - Tuyển dụng (Cấp mới moved inside Nhân sự)
        XCTAssertEqual(
            MiniBiNavDestination.mainMenuDestinations,
            [.dashboard, .workReports, .attendance, .workers, .recruitment]
        )
        XCTAssertEqual(
            MiniBiNavDestination.mainMenuDestinations.map(\.titleVi),
            ["Tổng quan", "Báo cáo", "Chấm công", "Nhân sự", "Tuyển dụng"]
        )
        XCTAssertFalse(MiniBiNavDestination.mainMenuDestinations.contains(.newEntry))
        XCTAssertTrue(MiniBiNavDestination.allCases.contains(.newEntry))
        XCTAssertEqual(MiniBiNavDestination.recruitment.screenTitleVi, "Tin tuyển dụng")

        // DirectEntry worker-profile/1.2 row payload contract
        var draftRow = NewWorkerEntryDraft()
        draftRow.hoTen = "Nguyễn Văn A"
        draftRow.soCccd = "034098001234"
        draftRow.ngaySinh = "15/08/1998"
        draftRow.gioiTinh = "Nam"
        draftRow.queQuan = "Thái Bình"
        draftRow.ngayCap = "20/10/2021"
        draftRow.sdt = "0987654321"
        draftRow.duAn = "Luxshare Bắc Giang"
        draftRow.nganhNghe = "Thời vụ"
        draftRow.nguoiTuyen = "Phạm Thị Sâm"
        draftRow.ngayVaoLam = "27/10/2026"
        draftRow.trangThai = "Đang làm"
        draftRow.nganHang = "MB Bank"
        draftRow.soTaiKhoan = "123456789"
        draftRow.ghiChu = "Ghi chú test"
        draftRow.projectId = "LUXSHARE-BG"
        draftRow.recruiterId = "11111111-2222-4333-8444-555555555555"
        draftRow.laborTypeKey = "TEMPORARY"
        draftRow.bankAccountHolder = "NGUYEN VAN A"

        let builtRow = DirectEntryService.shared.buildFullProfileEntryPayload(draftRow)
        XCTAssertEqual(builtRow["project_id"] as? String, "LUXSHARE-BG")
        XCTAssertEqual(builtRow["first_work_date"] as? String, "2026-10-27")
        XCTAssertEqual(builtRow["provider_type"] as? String, "hrp")
        XCTAssertEqual(builtRow["recruiter_id"] as? String, "11111111-2222-4333-8444-555555555555")
        XCTAssertEqual(builtRow["labor_type"] as? String, "TEMPORARY")
        XCTAssertEqual(builtRow["display_name"] as? String, "Nguyễn Văn A")
        let workerDict = try XCTUnwrap(builtRow["worker"] as? [String: Any])
        XCTAssertEqual((workerDict["gender"] as? [String: Any])?["value"] as? String, "MALE")
        XCTAssertEqual((workerDict["national_id"] as? [String: Any])?["value"] as? String, "034098001234")
        XCTAssertEqual((workerDict["date_of_birth"] as? [String: Any])?["value"] as? String, "15/08/1998")
        XCTAssertEqual((workerDict["national_id_issued_place"] as? [String: Any])?["state"] as? String, "omitted")
        let employmentDict = try XCTUnwrap(builtRow["employment"] as? [String: Any])
        XCTAssertEqual(employmentDict["initial_status"] as? String, "ON")
        let paymentDict = try XCTUnwrap(builtRow["payment"] as? [String: Any])
        XCTAssertEqual(paymentDict["bank_name"] as? String, "MB Bank")
        XCTAssertEqual(paymentDict["account_number"] as? String, "123456789")
        XCTAssertEqual(paymentDict["account_holder_name"] as? String, "NGUYEN VAN A")
    }

    // MARK: - 8. Direct Edit Worker Profile ("Sửa trực tiếp hồ sơ NLĐ") Contract Tests

    func testDirectEditValidationPatchAndLegacyEmployeeCodePreservation() throws {
        let detailJson: [String: Any] = [
            "entry": [
                "entry_id": "entry-uuid-123",
                "version": 3,
                "employee_code": "hrp-2026-001456", // Legacy YYYY format on server
                "display_name": "Nguyễn Văn An",
                "project_id": "proj-uuid-1",
                "project_display": "Công Ty TNHH Techl Vina",
                "first_work_date": "2026-10-30",
                "labor_type": "SEASONAL",
                "labor_type_display": "Thời vụ",
                "recruiter_id": "rec-uuid-1",
                "recruiter_display": "Đỗ Tuấn Anh 1",
                "employment_status": "ON",
                "allowed_actions": [
                    "view": true,
                    "propose_change": true,
                    "direct_edit": true
                ],
                "worker": [
                    "gender": ["state": "provided", "value": "MALE"],
                    "date_of_birth": ["state": "provided", "value": "1995-08-15"],
                    "national_id": ["state": "provided", "value": "034095001234"],
                    "national_id_issued_date": ["state": "omitted"],
                    "national_id_issued_place": ["state": "omitted"],
                    "address": ["state": "provided", "value": "Bắc Ninh"],
                    "phone_number": ["state": "provided", "value": "0988776655"]
                ],
                "payment": [
                    "state": "omitted"
                ],
                "documents": [
                    ["doc_type": "WORKER_PORTRAIT", "has_file": true]
                ]
            ]
        ]

        let baseline = DirectEntryService.parseWorkerEntryDetail(detailJson)
        XCTAssertEqual(baseline.entryIdUuid, "entry-uuid-123")
        XCTAssertEqual(baseline.entryVersion, 3)
        XCTAssertEqual(baseline.maNld, "hrp-2026-001456")
        XCTAssertTrue(baseline.canDirectEdit)
        XCTAssertEqual(baseline.ngaySinh, "15/08/1995")
        XCTAssertEqual(baseline.ngayVaoLam, "30/10/2026")

        // When user only adds bank info without touching legacy employee_code `hrp-2026-001456`:
        // 1. Validation MUST pass (do not block unchanged legacy employee_code!)
        // 2. Patch MUST NOT include `employee_code`, only `payment`
        var form = DirectEntryService.buildDirectEditFormInput(from: baseline)
        form.chuTaiKhoan = "NGUYEN VAN AN"
        form.soTaiKhoan = "1903667788"
        form.nganHang = "Techcombank"
        form.isCustomBank = false

        let validationErrors = DirectEntryService.validateDirectEditFormFields(baseline: baseline, input: form)
        XCTAssertTrue(validationErrors.isEmpty, "Unexpected validation errors: \(validationErrors)")

        let patch = DirectEntryService.buildDirectEditPatch(baseline: baseline, input: form)
        XCTAssertNil(patch["employee_code"])
        let paymentPatch = try XCTUnwrap(patch["payment"] as? [String: Any])
        XCTAssertEqual(paymentPatch["state"] as? String, "provided")
        XCTAssertEqual(paymentPatch["bank_name"] as? String, "Techcombank")
        XCTAssertEqual(paymentPatch["account_number"] as? String, "1903667788")
        XCTAssertEqual(paymentPatch["account_holder_name"] as? String, "NGUYEN VAN AN")

        // When user modifies employee_code to an invalid format -> validation catches `employee_code`
        var badCodeForm = form
        badCodeForm.maNld = "hrp-2026-999999"
        let badCodeErrors = DirectEntryService.validateDirectEditFormFields(baseline: baseline, input: badCodeForm)
        XCTAssertNotNil(badCodeErrors["employee_code"])

        // Valid new employee_code `hrp-1026-001456` passes validation and is included in patch
        var validCodeForm = form
        validCodeForm.maNld = "hrp-1026-001456"
        XCTAssertTrue(DirectEntryService.validateDirectEditFormFields(baseline: baseline, input: validCodeForm).isEmpty)
        let validCodePatch = DirectEntryService.buildDirectEditPatch(baseline: baseline, input: validCodeForm)
        XCTAssertEqual(validCodePatch["employee_code"] as? String, "hrp-1026-001456")

        // VietQR Bank Catalog search & initial resolution
        XCTAssertGreaterThanOrEqual(VietQrBankCatalog.banks.count, 55)
        let resolvedKnown = VietQrBankCatalog.resolveForInitialValue(bankId: nil, bankName: "Techcombank")
        XCTAssertEqual(resolvedKnown.bankId, "TCB")
        XCTAssertEqual(resolvedKnown.bankName, "Techcombank")

        let resolvedCustom = VietQrBankCatalog.resolveForInitialValue(bankId: nil, bankName: "Ngân hàng Nông Thôn X")
        XCTAssertNil(resolvedCustom.bankId)
        XCTAssertEqual(resolvedCustom.bankName, "Ngân hàng Nông Thôn X")
    }

    // MARK: - 9. Salary Advance ("Ứng lương" for QLDA) Contract Tests

    func testSalaryAdvanceContractAuthorityFilteringEligibilityAndPayload() throws {
        // 1. QLDA Entry button visibility
        let pmContext = SalaryAdvanceContract.parseContextResponse([
            "data": [
                "app_user_id": "pm-1",
                "display_name": "QLDA Bắc Giang",
                "is_project_manager": true,
                "managed_projects": [
                    [
                        "project_id": "proj-1",
                        "display_name": "Luxshare Bắc Giang",
                        "active": true,
                        "has_active_accountant": true
                    ]
                ]
            ]
        ])
        XCTAssertTrue(SalaryAdvanceContract.canShowPmSalaryAdvanceEntry(pmContext))

        let nonPmContext = SalaryAdvanceContract.parseContextResponse([
            "data": [
                "app_user_id": "rec-1",
                "is_project_manager": false,
                "managed_projects": []
            ]
        ])
        XCTAssertFalse(SalaryAdvanceContract.canShowPmSalaryAdvanceEntry(nonPmContext))

        // 2. Worker filtering & eligibility reasons
        let workersPage = SalaryAdvanceContract.parseProjectWorkersPage([
            "data": [
                "project_id": "proj-1",
                "workers": [
                    [
                        "worker_entry_id": "w-eligible",
                        "employee_code": "hrp-1026-000001",
                        "display_name": "Nguyễn Hợp Lệ",
                        "employment_status": "ON",
                        "provider_type": "hrp",
                        "recruiter_display_name": "Phạm Thị Sâm",
                        "first_work_date": "2026-10-01",
                        "has_portrait": true,
                        "is_eligible": true,
                        "ineligible_reasons": [],
                        "bank_transfer_eligible": true,
                        "advance_30d_summary": ["total_count": 1, "total_amount_vnd": "1500000"]
                    ],
                    [
                        "worker_entry_id": "w-ineligible-hrp",
                        "employee_code": "hrp-1026-000002",
                        "display_name": "Trần Trùng CCCD",
                        "employment_status": "ON",
                        "provider_type": "hrp",
                        "has_portrait": false,
                        "is_eligible": false,
                        "ineligible_reasons": ["WORKER_DUPLICATE_CCCD", "WORKER_PORTRAIT_MISSING"],
                        "bank_transfer_eligible": false,
                        "bank_ineligible_reason": "BANK_ACCOUNT_NOT_SELF_OWNED"
                    ],
                    [
                        "worker_entry_id": "w-off",
                        "employee_code": "hrp-1026-000003",
                        "display_name": "Lê Đã Nghỉ",
                        "employment_status": "OFF",
                        "provider_type": "hrp"
                    ],
                    [
                        "worker_entry_id": "w-vendor",
                        "employee_code": "hrp-1026-000004",
                        "display_name": "Phạm Vendor Tuyển",
                        "employment_status": "ON",
                        "provider_type": "vendor"
                    ]
                ],
                "next_cursor": "cursor-2",
                "has_more": true
            ]
        ])

        let selectable = SalaryAdvanceContract.filterSelectableProjectWorkers(workersPage.workers)
        // OFF and VENDOR workers must be hidden; HRP workers (both eligible and ineligible) are retained
        XCTAssertEqual(selectable.map(\.workerEntryId), ["w-eligible", "w-ineligible-hrp"])

        let ineligibleDesc = SalaryAdvanceContract.describeWorkerEligibility(selectable[1])
        XCTAssertFalse(ineligibleDesc.isEligible)
        let ineligibleReasonText = try XCTUnwrap(ineligibleDesc.blockingMessage)
        XCTAssertTrue(ineligibleReasonText.contains("trùng CCCD"))
        XCTAssertTrue(ineligibleReasonText.contains("ảnh chân dung"))

        let bankDesc = SalaryAdvanceContract.describeWorkerBankTransferEligibility(selectable[1])
        XCTAssertFalse(bankDesc.canUseBankTransfer)
        let bankReasonText = try XCTUnwrap(bankDesc.blockingMessage)
        XCTAssertTrue(bankReasonText.contains("chính chủ"))

        // 3. Create request payload & History parsing
        XCTAssertEqual(SalaryAdvanceContract.normalizePositiveVndInput("2.500.000"), "2500000")
        XCTAssertNil(SalaryAdvanceContract.normalizePositiveVndInput("0"))

        let reqPayload = try SalaryAdvanceContract.buildCreateRequestPayload(
            projectId: "proj-1",
            workerEntryId: "w-eligible",
            requestedAmountRaw: "2000000",
            paymentMethod: "BANK_TRANSFER",
            initialContent: "  Ứng lương tuần 1  ",
            idempotencyKey: "idem-sa-123456"
        )
        XCTAssertEqual(reqPayload["project_id"] as? String, "proj-1")
        XCTAssertEqual(reqPayload["worker_entry_id"] as? String, "w-eligible")
        XCTAssertEqual(reqPayload["requested_amount_vnd"] as? String, "2000000")
        XCTAssertEqual(reqPayload["payment_method"] as? String, "BANK_TRANSFER")
        XCTAssertEqual(reqPayload["initial_content"] as? String, "Ứng lương tuần 1")
        XCTAssertEqual(reqPayload["idempotency_key"] as? String, "idem-sa-123456")

        let historyResp = SalaryAdvanceContract.parseWorkerHistoryResponse([
            "data": [
                "worker_entry_id": "w-eligible",
                "worker_employee_code": "hrp-1026-000001",
                "worker_display_name": "Nguyễn Hợp Lệ",
                "period_mode": "LAST_30_DAYS",
                "total_count": 2,
                "total_amount_vnd": "3500000",
                "items": [
                    [
                        "request_id": "SA-202610-0001",
                        "created_at": "2026-10-30T03:00:00Z",
                        "requested_amount_vnd": "1500000",
                        "payment_method": "CASH",
                        "request_state": "OPEN",
                        "project_display_name": "Luxshare Bắc Giang",
                        "initial_content": "Ứng tiền ăn ở"
                    ]
                ],
                "next_cursor": "hist-cursor-2",
                "has_more": true
            ]
        ])
        XCTAssertEqual(historyResp.totalCount, 2)
        XCTAssertEqual(historyResp.totalAmountVnd, "3500000")
        XCTAssertEqual(historyResp.items.first?.requestId, "SA-202610-0001")
        XCTAssertTrue(historyResp.hasMore)

        // 4. Portrait in `entry.worker_portrait.current` (outside `entry.documents`) must populate `WORKER_PORTRAIT` in `detail.documents`
        let prodEntryWithPortraitCurrent: [String: Any] = [
            "entry": [
                "entry_id": "entry-uuid-portrait-1",
                "version": 4,
                "employee_code": "hrp-2026-002072",
                "project_id": "proj-dkt",
                "recruiter_id": "09a6060b-3c44-4a1e-8b5c-fab2dd1ccb13",
                "worker_details": [
                    "display_name": "Nguyễn Duy Minh Anh",
                    "national_id": ["state": "provided", "value": "026208007813"]
                ],
                "worker_portrait": [
                    "current": [
                        "document_id": "doc-portrait-uuid-99",
                        "version": 2,
                        "upload_status": "READY"
                    ]
                ],
                "documents": [
                    [
                        "document_id": "doc-front-uuid-1",
                        "document_type": "CCCD_FRONT",
                        "upload_status": "READY",
                        "version": 1
                    ],
                    [
                        "document_id": "doc-back-uuid-2",
                        "document_type": "CCCD_BACK",
                        "upload_status": "READY",
                        "version": 1
                    ]
                ]
            ]
        ]
        let parsedWithPortrait = DirectEntryService.parseWorkerEntryDetail(prodEntryWithPortraitCurrent)
        XCTAssertEqual(parsedWithPortrait.hoTen, "Nguyễn Duy Minh Anh")
        XCTAssertEqual(
            parsedWithPortrait.portraitPreviewPath,
            "/api/direct-entry/entries/entry-uuid-portrait-1/documents/doc-portrait-uuid-99/preview"
        )
        let portraitDoc = parsedWithPortrait.documents.first(where: { $0.docType == "WORKER_PORTRAIT" })
        XCTAssertNotNil(portraitDoc)
        XCTAssertEqual(portraitDoc?.hasFile, true)
        XCTAssertEqual(
            portraitDoc?.previewPath,
            "/api/direct-entry/entries/entry-uuid-portrait-1/documents/doc-portrait-uuid-99/preview"
        )

        // 5. Accent-insensitive worker search & Request Detail / Attention evaluation
        XCTAssertTrue(
            SalaryAdvanceContract.matchesWorkerSearchQuery(
                worker: selectable[0],
                query: "nguyen hop le",
                eligibilityFilter: "ELIGIBLE"
            )
        )
        XCTAssertFalse(
            SalaryAdvanceContract.matchesWorkerSearchQuery(
                worker: selectable[0],
                query: "nguyen hop le",
                eligibilityFilter: "INELIGIBLE"
            )
        )
    }

    // MARK: - 9. Attendance Explanation ("Giải trình chấm công") & Time Source Display Label Contract Tests

    func testAttendanceExplanationAndTimeSourceContract() throws {
        // 1. Time source display label in Vietnamese ("Máy chủ BI" instead of raw "SERVER_ANCHORED")
        XCTAssertEqual(AttendanceTimeContract.timeSourceDisplayLabel("SERVER_ANCHORED"), "Máy chủ BI")
        XCTAssertEqual(AttendanceTimeContract.timeSourceDisplayLabel("server_anchored"), "Máy chủ BI")
        XCTAssertEqual(AttendanceTimeContract.timeSourceDisplayLabel(""), "Máy chủ BI")
        XCTAssertEqual(AttendanceTimeContract.timeSourceDisplayLabel("DEVICE_CLOCK_UNVERIFIED"), "Giờ thiết bị")

        // 2. Dynamic slots (3 slots and 6 slots — never hardcoded to 4 or 5) & overview parsing
        let empId = "11111111-2222-4333-8444-555555555555"
        let leaderId = "99999999-2222-4333-8444-555555555555"
        let slot2Checkins: [[String: Any]] = [
            [
                "checkin_id": "chk-2",
                "sequence_in_day": 2,
                "captured_at": "2026-11-03T04:25:00Z",
                "captured_at_local": "2026-11-03 11:25:00",
                "time_local": "11:25",
                "time_verified": true,
                "assigned_slot_code": "SLOT_2",
                "delta_minutes": 25,
                "over_grace_minutes": 10,
                "deviation_status": "LATE",
                "distance_target_kind": "PROJECT",
                "distance_status": "IN_RANGE",
                "distance_meters": 42
            ]
        ]
        let slot6Explanation: [String: Any] = [
            "explanation_id": "exp-slot-6",
            "status": "PENDING",
            "original_slot_status": "MISSING",
            "reason": "Đi hỗ trợ ứng viên tại nhà máy",
            "submitted_at": "2026-11-03T10:30:00Z",
            "submitted_at_local": "03/11/2026 17:30",
            "decision_note": "",
            "row_version": 1
        ]
        var sixSlotsJson: [[String: Any]] = []
        for idx in 1...6 {
            let origStatus: String = idx == 2 ? "OUT_OF_GRACE" : (idx == 6 ? "MISSING" : "ON_TIME")
            let effStatus: String = idx == 2 ? "OUT_OF_GRACE" : (idx == 6 ? "EXPLAINED_PENDING" : "ON_TIME")
            var slotDict: [String: Any] = [
                "slot_code": "SLOT_\(idx)",
                "slot_index": idx,
                "slot_label": "Mốc #\(idx)",
                "session_code": idx <= 3 ? "AM" : "PM",
                "target_time": String(format: "%02d:00", 7 + idx * 2),
                "grace_before_minutes": 15,
                "grace_after_minutes": 15,
                "is_leave_covered": false,
                "original_slot_status": origStatus,
                "slot_status": effStatus,
                "eligible_for_explanation": idx == 2,
                "checkins": idx == 2 ? slot2Checkins : []
            ]
            if idx == 6 {
                slotDict["explanation"] = slot6Explanation
            }
            sixSlotsJson.append(slotDict)
        }

        let overviewItemJson: [String: Any] = [
            "explanation_id": "exp-slot-6",
            "app_user_id": empId,
            "requester": [
                "app_user_id": empId,
                "display_name": "Nhân viên Thực Địa",
                "login_id": "nv01",
                "personnel_code": "NV-01"
            ],
            "business_date": "2026-11-03",
            "slot_code": "SLOT_6",
            "slot_label": "Mốc #6",
            "session_code": "PM",
            "target_time": "19:00",
            "grace_before_minutes": 15,
            "grace_after_minutes": 15,
            "original_slot_status": "MISSING",
            "status": "PENDING",
            "reason": "Đi hỗ trợ ứng viên tại nhà máy",
            "route_kind": "TEAM_LEADER",
            "unit_kind_snapshot": "RECRUITMENT",
            "routed_team": [
                "team_id": "team-1",
                "team_code": "TD_BAC",
                "team_name": "Tuyển dụng Miền Bắc"
            ],
            "submitted_at": "2026-11-03T10:30:00Z",
            "submitted_at_local": "03/11/2026 17:30",
            "row_version": 1,
            "can_decide": false,
            "actual_checkins": [Any](),
            "events": [
                [
                    "event_id": "ev-1",
                    "event_type": "SUBMITTED",
                    "actor_app_user_id": empId,
                    "actor_display_name": "Nhân viên Thực Địa",
                    "to_status": "PENDING",
                    "note": "Đi hỗ trợ ứng viên tại nhà máy",
                    "created_at_local": "03/11/2026 17:30"
                ]
            ]
        ]
        let overviewDataJson: [String: Any] = [
            "viewer": [
                "app_user_id": empId,
                "can_submit_explanation": true,
                "can_approve_explanations": false,
                "is_shared_non_employee": false,
                "is_full_admin": false,
                "is_bod": false
            ],
            "routing": [
                "route_kind": "TEAM_LEADER",
                "unit_kind": "RECRUITMENT",
                "primary_approver": [
                    "app_user_id": leaderId,
                    "display_name": "Trưởng nhóm Tuyển dụng",
                    "login_id": "leader01",
                    "personnel_code": "TL-01",
                    "account_enabled": true
                ],
                "team_id": "team-1",
                "team_code": "TD_BAC",
                "team_name": "Tuyển dụng Miền Bắc",
                "profile_configured": true
            ],
            "today_local": "2026-11-03",
            "selected_date": "2026-11-03",
            "day_evaluation": [
                "business_date": "2026-11-03",
                "policy_profile_id_used": "77777777-3333-4444-8555-666666666666",
                "policy_profile_code_used": "KHOI_TD_MB",
                "policy_profile_name_used": "Khối Tuyển dụng Miền Bắc",
                "policy_is_default_used": false,
                "policy_version_used": 3,
                "slots": sixSlotsJson
            ],
            "pagination": [
                "limit": 50,
                "offset": 0,
                "total_rows": 1
            ],
            "items": [overviewItemJson]
        ]
        let overviewJson: [String: Any] = [
            "ok": true,
            "data": overviewDataJson
        ]

        guard case .success(let overview, _) = AttendanceExplanationContract.parseMyExplanationsOverview(overviewJson) else {
            XCTFail("Expected parseMyExplanationsOverview to succeed")
            return
        }
        XCTAssertEqual(overview.dayEvaluation.slots.count, 6)
        XCTAssertEqual(overview.dayEvaluation.policyProfileIdUsed, "77777777-3333-4444-8555-666666666666")
        XCTAssertEqual(overview.dayEvaluation.policyProfileCodeUsed, "KHOI_TD_MB")
        XCTAssertEqual(overview.dayEvaluation.policyProfileNameUsed, "Khối Tuyển dụng Miền Bắc")
        XCTAssertFalse(overview.dayEvaluation.policyIsDefaultUsed)
        XCTAssertEqual(overview.dayEvaluation.policyVersionUsed, 3)
        XCTAssertEqual(overview.dayEvaluation.resolvedProfileBadgeLabel(), "Khung giờ: Khối Tuyển dụng Miền Bắc v3 (6 mốc)")
        XCTAssertEqual(overview.routing.primaryApprover?.resolvedDisplayName(), "Trưởng nhóm Tuyển dụng")
        XCTAssertTrue(overview.dayEvaluation.slots[1].eligibleForExplanation)
        XCTAssertFalse(overview.dayEvaluation.slots[0].eligibleForExplanation)
        XCTAssertEqual(overview.dayEvaluation.slots[5].explanation?.explanationId, "exp-slot-6")
        XCTAssertEqual(
            AttendanceExplanationContract.formatDeviationBadge(overview.dayEvaluation.slots[1].checkins[0]),
            "Muộn 10p"
        )

        // 3. Submit input validation
        XCTAssertNil(
            AttendanceExplanationContract.validateSubmitInput(
                businessDate: "2026-11-03",
                slotCode: "SLOT_2",
                reason: "Kẹt xe đường cao tốc",
                todayLocal: "2026-11-03"
            )
        )
        XCTAssertEqual(
            AttendanceExplanationContract.validateSubmitInput(
                businessDate: "2026-11-04",
                slotCode: "SLOT_2",
                reason: "Kẹt xe đường cao tốc",
                todayLocal: "2026-11-03"
            ),
            AttendanceExplanationContract.errorMessagesVi["ATTENDANCE_EXPLANATION_DATE_FUTURE"]
        )
        XCTAssertEqual(
            AttendanceExplanationContract.validateSubmitInput(
                businessDate: "2026-11-03",
                slotCode: "SLOT_99",
                reason: "Kẹt xe đường cao tốc",
                todayLocal: "2026-11-03"
            ),
            AttendanceExplanationContract.errorMessagesVi["ATTENDANCE_EXPLANATION_SLOT_INVALID"]
        )
        XCTAssertEqual(
            AttendanceExplanationContract.validateSubmitInput(
                businessDate: "2026-11-03",
                slotCode: "SLOT_2",
                reason: "ab",
                todayLocal: "2026-11-03"
            ),
            AttendanceExplanationContract.errorMessagesVi["ATTENDANCE_EXPLANATION_REASON_INVALID"]
        )

        // 4. Approval inbox parsing & decision validation (authority, self-approval forbidden, conflict detection)
        let inboxJson: [String: Any] = [
            "ok": true,
            "data": [
                "viewer": [
                    "app_user_id": leaderId,
                    "can_approve_explanations": true,
                    "is_full_admin": false,
                    "is_bod": false
                ],
                "pagination": [
                    "limit": 50,
                    "offset": 0,
                    "total_rows": 1,
                    "pending_count": 1
                ],
                "items": [
                    [
                        "explanation_id": "exp-slot-6",
                        "app_user_id": empId,
                        "requester": [
                            "app_user_id": empId,
                            "display_name": "Nhân viên Thực Địa",
                            "login_id": "nv01",
                            "personnel_code": "NV-01"
                        ],
                        "business_date": "2026-11-03",
                        "slot_code": "SLOT_6",
                        "slot_label": "Mốc #6",
                        "session_code": "PM",
                        "target_time": "19:00",
                        "grace_before_minutes": 15,
                        "grace_after_minutes": 15,
                        "original_slot_status": "MISSING",
                        "status": "PENDING",
                        "reason": "Đi hỗ trợ ứng viên tại nhà máy",
                        "row_version": 2,
                        "can_decide": true
                    ]
                ]
            ]
        ]

        guard case .success(let inbox, _) = AttendanceExplanationContract.parseApprovalInbox(inboxJson) else {
            XCTFail("Expected parseApprovalInbox to succeed")
            return
        }
        XCTAssertTrue(inbox.viewer.canApproveExplanations)
        XCTAssertEqual(inbox.pagination.pendingCount, 1)
        let inboxItem = try XCTUnwrap(inbox.items.first)
        XCTAssertTrue(inboxItem.canDecide)
        XCTAssertEqual(inboxItem.rowVersion, 2)

        // Self-approval forbidden
        XCTAssertEqual(
            AttendanceExplanationContract.validateDecisionInput(
                actorAppUserId: empId,
                item: inboxItem,
                decision: "APPROVE",
                decisionNote: "Tự duyệt"
            ),
            AttendanceExplanationContract.errorMessagesVi["ATTENDANCE_EXPLANATION_SELF_APPROVAL_FORBIDDEN"]
        )
        // Authorized leader can approve/reject
        XCTAssertNil(
            AttendanceExplanationContract.validateDecisionInput(
                actorAppUserId: leaderId,
                item: inboxItem,
                decision: "APPROVE",
                decisionNote: "Xác nhận đi dự án hợp lệ"
            )
        )
        XCTAssertNil(
            AttendanceExplanationContract.validateDecisionInput(
                actorAppUserId: leaderId,
                item: inboxItem,
                decision: "REJECT",
                decisionNote: nil
            )
        )

        // Conflict detection & BackendHttpClient error dictionary
        XCTAssertTrue(AttendanceExplanationContract.isConflictError(httpStatus: 409, errorCode: "ATTENDANCE_EXPLANATION_VERSION_CONFLICT"))
        XCTAssertTrue(AttendanceExplanationContract.isConflictError(httpStatus: 400, errorCode: "ATTENDANCE_EXPLANATION_ALREADY_SUBMITTED"))
        XCTAssertFalse(AttendanceExplanationContract.isConflictError(httpStatus: 403, errorCode: "ATTENDANCE_EXPLANATION_DECIDE_DENIED"))
        XCTAssertEqual(
            BackendHttpClient.codeMessagesVi["ATTENDANCE_EXPLANATION_VERSION_CONFLICT"],
            "Giải trình đã được cập nhật bởi phiên làm việc khác. Dữ liệu đã được tải lại."
        )
    }

    func testAttendanceBeautyFilterPresetsAndPersistence() throws {
        let suiteName = "com.aistudio.minibi.tests.beauty.\(UUID().uuidString)"
        let customDefaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { customDefaults.removePersistentDomain(forName: suiteName) }

        // Default preset when none saved is .natural ("Tự nhiên")
        XCTAssertEqual(AttendanceBeautyFilter.loadSavedPreset(defaults: customDefaults), .natural)
        XCTAssertEqual(AttendanceBeautyPreset.original.labelVi, "Gốc")
        XCTAssertEqual(AttendanceBeautyPreset.natural.labelVi, "Tự nhiên")
        XCTAssertEqual(AttendanceBeautyPreset.brightSmooth.labelVi, "Sáng mịn")
        XCTAssertEqual(AttendanceBeautyPreset.rosy.labelVi, "Hồng hào")

        AttendanceBeautyFilter.savePreset(.brightSmooth, defaults: customDefaults)
        XCTAssertEqual(AttendanceBeautyFilter.loadSavedPreset(defaults: customDefaults), .brightSmooth)

        let rawJpeg = try makeSolidTestJpeg(
            color: UIColor(red: 0.72, green: 0.56, blue: 0.48, alpha: 1.0),
            size: CGSize(width: 240, height: 320)
        )

        let originalOutput = AttendanceBeautyFilter.apply(to: rawJpeg, preset: .original, maxDimensionPx: 1600)
        let naturalOutput = AttendanceBeautyFilter.apply(to: rawJpeg, preset: .natural, maxDimensionPx: 1600)
        let brightSmoothOutput = AttendanceBeautyFilter.apply(to: rawJpeg, preset: .brightSmooth, maxDimensionPx: 1600)
        let rosyOutput = AttendanceBeautyFilter.apply(to: rawJpeg, preset: .rosy, maxDimensionPx: 1600)

        for output in [originalOutput, naturalOutput, brightSmoothOutput, rosyOutput] {
            XCTAssertFalse(output.isEmpty)
            let img = try XCTUnwrap(UIImage(data: output))
            XCTAssertEqual(img.size.width, 240, accuracy: 1.0)
            XCTAssertEqual(img.size.height, 320, accuracy: 1.0)
        }

        // Beautified presets should transform the mid-tone skin color compared to original
        let originalSha = AttendanceContract.computeSha256Hex(originalOutput)
        let naturalSha = AttendanceContract.computeSha256Hex(naturalOutput)
        let brightSmoothSha = AttendanceContract.computeSha256Hex(brightSmoothOutput)
        let rosySha = AttendanceContract.computeSha256Hex(rosyOutput)

        XCTAssertNotEqual(originalSha, naturalSha)
        XCTAssertNotEqual(originalSha, brightSmoothSha)
        XCTAssertNotEqual(originalSha, rosySha)
    }

    @MainActor
    func testAttendanceOutboxRecoversStaleUploadingAndPendingCount() throws {
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("hrpbi_outbox_recovery_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let stuckUploadingItem = AttendanceOutboxItem(
            localId: "stuck-uploading-1",
            userKey: "thuynb.td",
            idempotencyKey: "stuck-uploading-1",
            clientCapturedAt: "2026-11-06T01:07:07.000Z",
            timeSource: AttendanceTimeContract.sourceServerAnchored,
            lat: 21.32016,
            lng: 105.67860,
            accuracyM: 26.9,
            photoSha256: "abc123",
            photoSizeBytes: 1024,
            status: AttendanceContract.statusUploading,
            createdAtEpochMs: 1_762_391_227_000
        )
        let retryableItem = AttendanceOutboxItem(
            localId: "retryable-2",
            userKey: "thuynb.td",
            idempotencyKey: "retryable-2",
            clientCapturedAt: "2026-11-06T01:10:00.000Z",
            timeSource: AttendanceTimeContract.sourceServerAnchored,
            lat: 21.32016,
            lng: 105.67860,
            accuracyM: 18.0,
            photoSha256: "def456",
            photoSizeBytes: 1024,
            status: AttendanceContract.statusFailedRetryable,
            createdAtEpochMs: 1_762_391_400_000
        )

        let outboxUrl = tempDir.appendingPathComponent("attendance_outbox_v1.json")
        let encoded = try JSONEncoder().encode([stuckUploadingItem, retryableItem])
        try encoded.write(to: outboxUrl, options: .atomic)

        let service = AttendanceOutboxService(customStorageDirectory: tempDir)
        let loaded = service.listItemsForUser(userKey: "thuynb.td")
        XCTAssertEqual(loaded.count, 2)
        // Stuck UPLOADING item must be recovered to FAILED_RETRYABLE so flushRetryableOutbox picks it up
        XCTAssertTrue(loaded.allSatisfy { $0.status == AttendanceContract.statusFailedRetryable })
        XCTAssertEqual(service.pendingCountForUser(userKey: "thuynb.td"), 2)

        let vm = MiniBiViewModel(attendanceOutboxService: service)
        vm.attendanceOutboxItems = loaded
        XCTAssertEqual(vm.attendancePendingCount, 2)
    }

    // MARK: - 10. Fail-Closed Worker Tabs & Authorization Regression Tests

    private struct RecordedMockRequest {
        let url: String
        let method: String
        let headers: [String: String]
        let bodyString: String
    }

    private final class MockURLProtocol: URLProtocol {
        nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?
        nonisolated(unsafe) static var pathDelaySec: [String: TimeInterval] = [:]
        private static let lock = NSLock()
        nonisolated(unsafe) private static var recordedUrlsStorage: [String] = []
        nonisolated(unsafe) private static var recordedRequestsStorage: [RecordedMockRequest] = []

        static func resetRecordedUrls() {
            lock.lock()
            recordedUrlsStorage.removeAll()
            recordedRequestsStorage.removeAll()
            pathDelaySec.removeAll()
            lock.unlock()
        }

        static func recordedUrls() -> [String] {
            lock.lock()
            defer { lock.unlock() }
            return recordedUrlsStorage
        }

        static func recordedRequests() -> [RecordedMockRequest] {
            lock.lock()
            defer { lock.unlock() }
            return recordedRequestsStorage
        }

        private static func readBodyData(from request: URLRequest) -> Data {
            if let data = request.httpBody {
                return data
            }
            guard let stream = request.httpBodyStream else {
                return Data()
            }
            stream.open()
            defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 4096)
            var result = Data()
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count > 0 {
                    result.append(buffer, count: count)
                } else {
                    break
                }
            }
            return result
        }

        override class func canInit(with request: URLRequest) -> Bool {
            true
        }

        override class func canonicalRequest(for request: URLRequest) -> URLRequest {
            request
        }

        override func startLoading() {
            let fullUrl = request.url?.absoluteString ?? ""
            let path = request.url?.path ?? ""
            let bodyData = Self.readBodyData(from: request)
            let bodyStr = String(data: bodyData, encoding: .utf8) ?? ""
            let rec = RecordedMockRequest(
                url: fullUrl,
                method: request.httpMethod ?? "GET",
                headers: request.allHTTPHeaderFields ?? [:],
                bodyString: bodyStr
            )
            Self.lock.lock()
            Self.recordedUrlsStorage.append(fullUrl)
            Self.recordedRequestsStorage.append(rec)
            let delay = Self.pathDelaySec.first(where: { fullUrl.contains($0.key) || path.contains($0.key) })?.value ?? 0
            Self.lock.unlock()

            let executeResponse = { [weak self] in
                guard let self else { return }
                guard let handler = Self.requestHandler else {
                    self.client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                    return
                }
                do {
                    var reqWithBody = self.request
                    if reqWithBody.httpBody == nil && !bodyData.isEmpty {
                        reqWithBody.httpBody = bodyData
                    }
                    let (response, data) = try handler(reqWithBody)
                    self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                    self.client?.urlProtocol(self, didLoad: data)
                    self.client?.urlProtocolDidFinishLoading(self)
                } catch {
                    self.client?.urlProtocol(self, didFailWithError: error)
                }
            }

            if delay > 0 {
                DispatchQueue.global().asyncAfter(deadline: .now() + delay, execute: executeResponse)
            } else {
                executeResponse()
            }
        }

        override func stopLoading() {}
    }

    private func makeMockHttpClient() -> BackendHttpClient {
        let keychain = KeychainStore(serviceName: "com.aistudio.minibi.authz.\(UUID().uuidString)")
        let cookieStore = KeychainCookieStore(keychain: keychain)
        return BackendHttpClient(
            baseUrl: "https://bi.hrpartner.vn",
            cookieStore: cookieStore,
            enforceHttps: true,
            protocolClasses: [MockURLProtocol.self]
        )
    }

    @MainActor
    func testRegression1_AllowedWorkerTabsEmptyFiresZeroWorkerCatalogOrSalaryRequests() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let body: String
            if path == "/api/auth/session" {
                body = """
                {"ok":true,"actor":{"app_user_id":"22222222-3333-4444-8555-666666666666","display_name":"Phạm Thành Luân","capabilities":["entry_read"],"scopes":[{"kind":"own","reference":"self"}]}}
                """
            } else if path == "/api/direct-entry/session" {
                body = """
                {"ok":true,"actor":{"app_user_id":"22222222-3333-4444-8555-666666666666","allowed_worker_tabs":[]}}
                """
            } else {
                body = """
                {"ok":true,"data":[]}
                """
            }
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo1.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let meRes = await repo.fetchCurrentUser()
        guard case .success(let user, _) = meRes else {
            XCTFail("Expected fetchCurrentUser to succeed")
            return
        }
        XCTAssertEqual(user.safeAllowedWorkerTabs, [])

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        vm.onAuthenticatedSessionEstablished(user: user)
        XCTAssertTrue(vm.visibleWorkerTabs.isEmpty)
        XCTAssertEqual(vm.selectedWorkerScope, "")

        // Navigating to .workers or calling loadWorkers / loadSalaryAdvanceContext must be blocked and fire zero requests
        vm.navigateTo(.workers)
        XCTAssertEqual(vm.currentDestination, .dashboard)
        await vm.loadWorkers(resetPage: true)
        await vm.loadSalaryAdvanceContext()

        let urls = MockURLProtocol.recordedUrls()
        XCTAssertTrue(urls.contains(where: { $0.contains("/api/direct-entry/session") }))
        XCTAssertFalse(urls.contains(where: { $0.contains("/api/direct-entry/workers") }))
        XCTAssertFalse(urls.contains(where: { $0.contains("/api/direct-entry/catalog") }))
        XCTAssertFalse(urls.contains(where: { $0.contains("/api/salary-advance/context") }))
    }

    @MainActor
    func testRegression2_AllowedWorkerTabsOnlyRecruitedCallsOnlyRecruited() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let body = """
            {"ok":true,"scope":"recruited","items":[],"summary":{"total_workers":4}}
            """
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo2.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        let recruitedUser = MiniBiUser(
            id: "33333333-4444-4555-8666-777777777777",
            username: "recruiter01",
            fullName: "Tuyển Dụng Recruited",
            permissions: ["entry_read"],
            allowedWorkerTabs: ["recruited"]
        )
        vm.currentUser = recruitedUser
        vm.isAuthenticated = true
        vm.selectedWorkerScope = vm.visibleWorkerTabs.first?.key ?? ""

        XCTAssertEqual(vm.visibleWorkerTabs.map(\.key), ["recruited"])
        XCTAssertEqual(vm.selectedWorkerScope, "recruited")

        MockURLProtocol.resetRecordedUrls()
        await vm.loadWorkers(resetPage: true)
        await vm.loadSalaryAdvanceContext()

        let urls = MockURLProtocol.recordedUrls()
        let workerUrls = urls.filter { $0.contains("/api/direct-entry/workers") }
        XCTAssertEqual(workerUrls.count, 1)
        XCTAssertTrue(workerUrls[0].contains("scope=recruited"))
        XCTAssertFalse(urls.contains(where: { $0.contains("/api/direct-entry/catalog") }))
        XCTAssertFalse(urls.contains(where: { $0.contains("/api/salary-advance/context") }))
    }

    func testRegression3_MissingOrMalformedAllowedWorkerTabsFailsClosedToEmpty() throws {
        let userId = "11111111-2222-4333-8444-555555555555"

        // Missing or NSNull -> []
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(nil), [])
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(NSNull()), [])

        // Wrong type (String, Dict, Number) -> []
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs("managed"), [])
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(["managed": true]), [])
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(123), [])

        // Non-string element, unknown scope, or duplicate element -> []
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(["managed", 123]), [])
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(["managed", "unknown_scope"]), [])
        XCTAssertEqual(MiniBiRepository.parseAllowedWorkerTabs(["recruited", "recruited"]), [])

        // Valid canonical list -> preserved in order
        XCTAssertEqual(
            MiniBiRepository.parseAllowedWorkerTabs(["managed", "recruited", "team"]),
            ["managed", "recruited", "team"]
        )

        // Mismatched app_user_id in /api/direct-entry/session -> []
        XCTAssertEqual(
            MiniBiRepository.parseDirectEntrySessionAllowedWorkerTabs(
                from: [
                    "ok": true,
                    "actor": [
                        "app_user_id": "99999999-2222-4333-8444-555555555555",
                        "allowed_worker_tabs": ["managed", "recruited"]
                    ]
                ],
                expectedAppUserId: userId
            ),
            []
        )

        // Legacy `allowed_worker_scopes` field on user object is ignored -> fails closed to []
        let legacyActor = MiniBiRepository.parseUser([
            "app_user_id": userId,
            "display_name": "Legacy Actor",
            "allowed_worker_scopes": ["all", "team", "managed", "recruited"],
            "scopes": [["kind": "all"]]
        ])
        XCTAssertEqual(legacyActor.safeAllowedWorkerTabs, [])
    }

    @MainActor
    func testRegression4_Http403DoesNotTriggerFallbackRequestToNextScope() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let body = """
            {"ok":false,"code":"WORKER_DIRECTORY_DENIED","message":"Không có quyền truy cập phạm vi này."}
            """
            let resp = HTTPURLResponse(url: url, statusCode: 403, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo4.\(UUID().uuidString)"))

        // Even when requesting scope="all" or "managed" that returns 403, repo.fetchWorkers must NOT probe team/managed/recruited!
        let res = await repo.fetchWorkers(scope: "all")
        if case .success = res {
            XCTFail("Expected 403 failure")
        }
        let workerUrls = MockURLProtocol.recordedUrls().filter { $0.contains("/api/direct-entry/workers") }
        XCTAssertEqual(workerUrls.count, 1)
        XCTAssertTrue(workerUrls[0].contains("scope=all"))
        XCTAssertFalse(workerUrls.contains(where: { $0.contains("scope=team") }))
        XCTAssertFalse(workerUrls.contains(where: { $0.contains("scope=managed") }))
        XCTAssertFalse(workerUrls.contains(where: { $0.contains("scope=recruited") }))
    }

    @MainActor
    func testRegression5_LogoutAndLoginDifferentAccountDoesNotReuseCachedPermissions() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let body = """
            {"ok":true,"data":[]}
            """
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo5.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )

        // 1. Establish session for Account A (`["managed", "recruited"]`)
        let userA = MiniBiUser(
            id: "aaaa1111-2222-4333-8444-555555555555",
            username: "pm.user",
            fullName: "Quản Lý PM",
            permissions: ["entry_read"],
            allowedWorkerTabs: ["managed", "recruited"]
        )
        vm.onAuthenticatedSessionEstablished(user: userA)
        vm.workers = [WorkerItem(maNld: "w-1", hoTen: "Công Nhân Cũ")]
        XCTAssertEqual(vm.visibleWorkerTabs.map(\.key), ["managed", "recruited"])
        XCTAssertEqual(vm.selectedWorkerScope, "managed")

        // 2. Logout -> clears user, visibleWorkerTabs, selectedWorkerScope, and cached workers
        await vm.performLogout()
        XCTAssertNil(vm.currentUser)
        XCTAssertTrue(vm.visibleWorkerTabs.isEmpty)
        XCTAssertEqual(vm.selectedWorkerScope, "")
        XCTAssertTrue(vm.workers.isEmpty)

        // 3. Establish session for Account B (`allowed_worker_tabs = []`)
        MockURLProtocol.resetRecordedUrls()
        let userB = MiniBiUser(
            id: "bbbb2222-3333-4444-8555-666666666666",
            username: "luanpt",
            fullName: "Phạm Thành Luân",
            permissions: ["entry_read"],
            allowedWorkerTabs: []
        )
        vm.onAuthenticatedSessionEstablished(user: userB)
        XCTAssertTrue(vm.visibleWorkerTabs.isEmpty)
        XCTAssertEqual(vm.selectedWorkerScope, "")
        XCTAssertTrue(vm.workers.isEmpty)

        await vm.loadWorkers(resetPage: true)
        let urls = MockURLProtocol.recordedUrls()
        XCTAssertFalse(urls.contains(where: { $0.contains("/api/direct-entry/workers") }))
    }

    @MainActor
    func testRegression6_UploaderTabCallsSubmissionsNeverWorkersOrExportScopeAll() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let body: String
            if path == "/api/direct-entry/submissions" {
                body = """
                {"ok":true,"items":[{"submission_id":"aaaa1111-2222-4333-8444-555555555555","state":"SUBMITTED","entry_count":3,"created_at":"2026-11-08T08:00:00Z"}],"has_more":true,"next_cursor":"cur-2"}
                """
            } else if path == "/api/direct-entry/workers" {
                body = """
                {"ok":true,"scope":"managed","items":[{"entry_id":"eeee1111-2222-4333-8444-555555555555","employee_code":"HRP001","display_name":"Nguyễn Văn A"}],"summary":{"total_workers":1}}
                """
            } else {
                body = """
                {"ok":true,"data":[]}
                """
            }
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo6.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )

        // Case A: allowed_worker_tabs = ["uploader", "managed"]
        let uploaderAndManagedUser = MiniBiUser(
            id: "11112222-3333-4444-8555-666666666666",
            username: "uploader.pm",
            fullName: "Người Nhập Hộ & QLDA",
            permissions: ["entry_read", "submission_read"],
            allowedWorkerTabs: ["uploader", "managed"]
        )
        vm.currentUser = uploaderAndManagedUser
        vm.isAuthenticated = true
        vm.selectedWorkerScope = "uploader"

        await vm.loadWorkers(resetPage: true)
        XCTAssertEqual(vm.ownSubmissions.count, 1)
        XCTAssertTrue(vm.ownSubmissionsHasMore)
        XCTAssertEqual(vm.ownSubmissionsNextCursor, "cur-2")

        // Attempting to export while on uploader or calling fetchWorkers(scope: "uploader") directly must fail closed
        let directFetchUploader = await repo.fetchWorkers(scope: "uploader")
        if case .success = directFetchUploader {
            XCTFail("Expected fetchWorkers(scope: uploader) to fail closed")
        }
        let exportUploader = await repo.exportWorkersExcel(scope: "uploader")
        if case .success = exportUploader {
            XCTFail("Expected exportWorkersExcel(scope: uploader) to fail closed and never convert to scope=all")
        }

        // Switching to "managed" calls workers?scope=managed
        vm.selectedWorkerScope = "managed"
        await vm.loadWorkers(resetPage: true)
        XCTAssertEqual(vm.workers.count, 1)

        // Case B: allowed_worker_tabs = ["uploader"] only
        MockURLProtocol.resetRecordedUrls()
        let uploaderOnlyUser = MiniBiUser(
            id: "22223333-4444-4555-8666-777777777777",
            username: "uploader.only",
            fullName: "Chỉ Nhập Hộ",
            permissions: ["entry_read", "submission_read"],
            allowedWorkerTabs: ["uploader"]
        )
        vm.currentUser = uploaderOnlyUser
        vm.selectedWorkerScope = ""
        await vm.loadWorkers(resetPage: true)
        XCTAssertEqual(vm.selectedWorkerScope, "uploader")

        let allUrls = MockURLProtocol.recordedUrls()
        XCTAssertTrue(allUrls.contains(where: { $0.contains("/api/direct-entry/submissions") }))
        XCTAssertFalse(allUrls.contains(where: { $0.contains("scope=uploader") }))
        XCTAssertFalse(allUrls.contains(where: { $0.contains("/api/direct-entry/workers") }))
        XCTAssertFalse(allUrls.contains(where: { $0.contains("/api/direct-entry/workers/export") }))
    }

    @MainActor
    func testRegression7_CatalogAndNewEntryNavigationPermissions() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let body = """
            {"ok":true,"projects":[{"project_id":"p1","display_name":"Dự án A"}],"recruiters":[],"banks":[],"labor_types":[]}
            """
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo7.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )

        // 1. luanpt (`allowed_worker_tabs=[]`), recruited-only, and uploader-only:
        //    neither canLoadDirectEntryCatalog (`entry_create || entry_own`) nor canAccessNewWorkerEntry (`entry_create && submission_create`)
        let unauthorizedAccounts: [MiniBiUser] = [
            MiniBiUser(id: "u-luanpt", username: "luanpt", fullName: "Phạm Thành Luân", permissions: ["entry_read"], allowedWorkerTabs: []),
            MiniBiUser(id: "u-recruited", username: "rec01", fullName: "Tuyển Dụng 01", permissions: ["entry_read"], allowedWorkerTabs: ["recruited"]),
            MiniBiUser(id: "u-uploader", username: "up01", fullName: "Nhập Hộ 01", permissions: ["entry_read", "submission_read", "submission_create"], allowedWorkerTabs: ["uploader"])
        ]

        for account in unauthorizedAccounts {
            XCTAssertFalse(account.canLoadDirectEntryCatalog, "Expected \(account.username) not to have catalog permission")
            XCTAssertFalse(account.canAccessNewWorkerEntry, "Expected \(account.username) not to access Cấp mới")

            vm.currentUser = account
            vm.isAuthenticated = true
            vm.currentDestination = .dashboard

            // Attempt navigateTo(.newEntry) and notification navigation to Cấp mới
            vm.navigateTo(.newEntry)
            XCTAssertEqual(vm.currentDestination, .dashboard)

            let notifItem = AppNotificationItem(
                id: 1,
                title: "Cấp mới",
                body: "Mở cấp mới",
                type: "DIRECT_ENTRY",
                isRead: false,
                createdAt: "2026-11-08T08:00:00Z",
                targetRoute: "/direct-entry"
            )
            await vm.handleNotificationTap(notifItem)
            XCTAssertNotEqual(vm.currentDestination, .newEntry)

            await vm.loadDirectEntryAssigneeOptions(force: true)
        }

        XCTAssertFalse(MockURLProtocol.recordedUrls().contains(where: { $0.contains("/api/direct-entry/catalog") }))

        // 2. Account with `entry_own` ONLY -> can read catalog (`entry_create || entry_own`), but CANNOT enter Cấp mới (`entry_create && submission_create`)
        let entryOwnOnly = MiniBiUser(
            id: "u-entry-own",
            username: "own01",
            fullName: "Own 01",
            permissions: ["entry_own"],
            allowedWorkerTabs: ["recruited"]
        )
        XCTAssertTrue(entryOwnOnly.canLoadDirectEntryCatalog)
        XCTAssertFalse(entryOwnOnly.canAccessNewWorkerEntry)
        vm.currentUser = entryOwnOnly
        vm.navigateTo(.newEntry)
        XCTAssertEqual(vm.currentDestination, .dashboard)

        // 3. Account with `entry_create && submission_create` -> CAN enter Cấp mới and CAN load catalog
        let fullCreator = MiniBiUser(
            id: "u-creator",
            username: "creator01",
            fullName: "Creator 01",
            permissions: ["entry_create", "submission_create"],
            allowedWorkerTabs: ["uploader"]
        )
        XCTAssertTrue(fullCreator.canLoadDirectEntryCatalog)
        XCTAssertTrue(fullCreator.canAccessNewWorkerEntry)
        vm.currentUser = fullCreator
        vm.navigateTo(.newEntry)
        XCTAssertEqual(vm.currentDestination, .newEntry)
        await vm.loadDirectEntryAssigneeOptions(force: true)
        XCTAssertTrue(MockURLProtocol.recordedUrls().contains(where: { $0.contains("/api/direct-entry/catalog") }))
    }

    @MainActor
    func testRegression8_NullUserFailClosedAndDelayedResponseFromAccountABlockedAfterSwitchToAccountB() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.pathDelaySec = [
            "/api/direct-entry/catalog": 0.25,
            "/api/salary-advance/context": 0.25,
            "/api/direct-entry/workers": 0.25
        ]
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let body: String
            if path == "/api/direct-entry/catalog" {
                body = """
                {"ok":true,"projects":[{"project_id":"proj-a","display_name":"Dự án Tài Khoản A"}],"recruiters":[],"banks":[],"labor_types":[]}
                """
            } else if path == "/api/salary-advance/context" {
                body = """
                {"ok":true,"actor":{"app_user_id":"aaaa1111-2222-4333-8444-555555555555","display_name":"Account A","is_project_manager":true,"managed_projects":[{"project_id":"proj-a","project_code":"PA","project_name":"Dự án A","is_active_assignment":true,"has_active_accountant":true}]}}
                """
            } else if path == "/api/direct-entry/workers" {
                body = """
                {"ok":true,"scope":"managed","items":[{"entry_id":"w-a-1","employee_code":"A001","display_name":"Công Nhân Của A"}],"summary":{"total_workers":1}}
                """
            } else {
                body = """
                {"ok":true,"data":[]}
                """
            }
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo8.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )

        // 1. Null user must fail closed and fire 0 requests
        vm.currentUser = nil
        vm.isAuthenticated = false
        await vm.loadDirectEntryAssigneeOptions(force: true)
        await vm.loadSalaryAdvanceContext(force: true)
        await vm.loadWorkers(resetPage: true)
        XCTAssertTrue(MockURLProtocol.recordedUrls().isEmpty)

        // 2. Login Account A and launch delayed requests
        let userA = MiniBiUser(
            id: "aaaa1111-2222-4333-8444-555555555555",
            username: "account.a",
            fullName: "Account A",
            permissions: ["entry_create", "submission_create"],
            allowedWorkerTabs: ["managed"]
        )
        vm.currentUser = userA
        vm.isAuthenticated = true
        vm.selectedWorkerScope = "managed"

        let taskCatalogA = Task { await vm.loadDirectEntryAssigneeOptions(force: true) }
        let taskSalaryA = Task { await vm.loadSalaryAdvanceContext(force: true) }
        let taskWorkersA = Task { await vm.loadWorkers(resetPage: true) }

        // Give Account A requests 50ms to start in-flight
        try await Task.sleep(nanoseconds: 50_000_000)

        // Switch session to Account B (`luanpt` with no permissions) BEFORE Account A responses return
        let userB = MiniBiUser(
            id: "bbbb2222-3333-4444-8555-666666666666",
            username: "luanpt",
            fullName: "Account B",
            permissions: ["entry_read"],
            allowedWorkerTabs: []
        )
        vm.onAuthenticatedSessionEstablished(user: userB)

        _ = await (taskCatalogA.value, taskSalaryA.value, taskWorkersA.value)
        try await Task.sleep(nanoseconds: 300_000_000)

        // Verify Account A's delayed responses NEVER committed into Account B's state
        XCTAssertEqual(vm.currentUser?.id, userB.id)
        XCTAssertTrue(vm.directEntryOptions.catalogProjects.isEmpty)
        XCTAssertNil(vm.salaryAdvanceContext)
        XCTAssertTrue(vm.workers.isEmpty)
    }

    @MainActor
    func testRegression9_OpeningWorkersCallsSalaryAdvanceContextExactlyOnceAndDeduplicatesLifecycle() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.pathDelaySec = [
            "/api/salary-advance/context": 0.10,
            "/api/direct-entry/workers": 0.10
        ]
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let body: String
            if path == "/api/salary-advance/context" {
                body = """
                {"ok":true,"actor":{"app_user_id":"aaaa1111-2222-4333-8444-555555555555","display_name":"PM User","is_project_manager":true,"managed_projects":[{"project_id":"p1","project_code":"P1","project_name":"Dự án 1","is_active_assignment":true,"has_active_accountant":true}]}}
                """
            } else {
                body = """
                {"ok":true,"scope":"managed","items":[],"summary":{"total_workers":0}}
                """
            }
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo9.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        let pmUser = MiniBiUser(
            id: "aaaa1111-2222-4333-8444-555555555555",
            username: "pm.user",
            fullName: "PM User",
            permissions: ["entry_read"],
            allowedWorkerTabs: ["managed", "recruited"]
        )
        vm.currentUser = pmUser
        vm.isAuthenticated = true
        vm.selectedWorkerScope = "managed"

        // Simulate simultaneous navigateTo(.workers) + WorkersScreen.onAppear + overlapping loadWorkers/loadSalaryAdvanceContext calls
        vm.navigateTo(.workers)
        async let c1: Void = vm.loadSalaryAdvanceContext()
        async let c2: Void = vm.loadSalaryAdvanceContext()
        async let w1: Void = vm.loadWorkers(resetPage: true)
        async let w2: Void = vm.loadWorkers(resetPage: true)
        _ = await (c1, c2, w1, w2)

        let urls = MockURLProtocol.recordedUrls()
        let salaryContextUrls = urls.filter { $0.contains("/api/salary-advance/context") }
        let workerUrls = urls.filter { $0.contains("/api/direct-entry/workers") }
        XCTAssertEqual(salaryContextUrls.count, 1, "Expected /api/salary-advance/context to be called exactly once")
        XCTAssertEqual(workerUrls.count, 1, "Expected overlapping loadWorkers(resetPage: true) to coalesce into 1 request")
    }

    @MainActor
    func testRegression10_PT409VersionConflictReloadsLatestVersionPreservesDraftAndUsesNewIdempotencyKey() async throws {
        MockURLProtocol.resetRecordedUrls()
        let entryId = "11111111-2222-4333-8444-555555555555"
        var privilegedEditAttempt = 0

        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let method = req.httpMethod ?? "GET"
            if path.hasSuffix("/privileged-edit") && method == "POST" {
                privilegedEditAttempt += 1
                if privilegedEditAttempt == 1 {
                    let body = """
                    {"ok":false,"code":"CONFLICT","message":"Phiên bản hồ sơ đã cũ."}
                    """
                    let resp = HTTPURLResponse(url: url, statusCode: 409, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                    return (resp, Data(body.utf8))
                } else {
                    let body = """
                    {"ok":true,"entry":{"entry_id":"\(entryId)","version":4,"employee_code":"hrp-1126-000009","display_name":"Nguyễn Văn Mới","project_id":"p1","project_display":"Dự án 1","recruiter_id":"22222222-3333-4444-8555-666666666666","recruiter_display":"Tuyển 1","labor_type":"OFFICIAL","first_work_date":"2026-11-01","employment_status":"ON","national_id":"001200012345","allowed_actions":{"view":true,"direct_edit":true}}}
                    """
                    let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                    return (resp, Data(body.utf8))
                }
            } else if path.contains("/api/direct-entry/entries/\(entryId)") && method == "GET" {
                let ver = privilegedEditAttempt >= 2 ? 4 : 3
                let name = privilegedEditAttempt >= 2 ? "Nguyễn Văn Mới" : "Nguyễn Văn Cũ"
                let body = """
                {"ok":true,"entry":{"entry_id":"\(entryId)","version":\(ver),"employee_code":"hrp-1126-000009","display_name":"\(name)","project_id":"p1","project_display":"Dự án 1","recruiter_id":"22222222-3333-4444-8555-666666666666","recruiter_display":"Tuyển 1","labor_type":"OFFICIAL","first_work_date":"2026-11-01","employment_status":"ON","national_id":"001200012345","allowed_actions":{"view":true,"direct_edit":true}}}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            } else {
                let body = """
                {"ok":true,"items":[]}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            }
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo10.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        vm.currentUser = MiniBiUser(
            id: "aaaa1111-2222-4333-8444-555555555555",
            username: "admin01",
            fullName: "Quản Trị Viên",
            permissions: ["entry_read", "entry_direct_edit"],
            allowedWorkerTabs: ["managed"]
        )
        vm.isAuthenticated = true
        vm.directEditBaselineDetail = WorkerEntryDetail(
            entryId: entryId,
            version: 1,
            projectId: "p1",
            projectDisplay: "Dự án 1",
            firstWorkDate: "2026-11-01",
            employeeCode: "hrp-1126-000009",
            recruiterId: "22222222-3333-4444-8555-666666666666",
            recruiterDisplay: "Tuyển 1",
            providerType: "hrp",
            laborType: "OFFICIAL",
            laborTypeDisplay: "Chính thức",
            displayName: "Nguyễn Văn Cũ",
            gender: "MALE",
            dateOfBirth: "1995-01-01",
            nationalId: "001200012345",
            nationalIdIssuedAt: "2021-01-01",
            address: "Hà Nội",
            phone: "0912345678",
            employmentStatus: "ON",
            employmentEffectiveDate: "2026-11-01",
            canDirectEdit: true
        )

        var userDraftForm = DirectEntryService.buildDirectEditFormInput(from: vm.directEditBaselineDetail!)
        userDraftForm.hoTen = "Nguyễn Văn Mới"

        // 1. First submit hits HTTP 409 CONFLICT -> must NOT blind retry, must reload server version (v3), and show conflict message
        await vm.submitDirectEditProfileAndDocuments(form: userDraftForm, stagedFiles: [:])
        let postReqsAfterFirst = MockURLProtocol.recordedRequests().filter { $0.url.contains("/privileged-edit") }
        XCTAssertEqual(postReqsAfterFirst.count, 1, "Must not blind-retry mutation on 409")
        XCTAssertTrue(postReqsAfterFirst[0].bodyString.contains("\"expected_entry_version\":1"))
        XCTAssertEqual(vm.directEditBaselineDetail?.entryVersion, 3, "Must reload latest version from server on 409")
        XCTAssertNotNil(vm.directEditErrorMessage)
        // User's draft form remains untouched ("Nguyễn Văn Mới")
        XCTAssertEqual(userDraftForm.hoTen, "Nguyễn Văn Mới")

        // 2. User reviews and confirms re-submitting -> sends refreshed expected_entry_version (3) and a NEW Idempotency-Key
        var completed = false
        await vm.submitDirectEditProfileAndDocuments(form: userDraftForm, stagedFiles: [:]) {
            completed = true
        }
        let postReqsAfterSecond = MockURLProtocol.recordedRequests().filter { $0.url.contains("/privileged-edit") }
        XCTAssertEqual(postReqsAfterSecond.count, 2)
        XCTAssertTrue(postReqsAfterSecond[1].bodyString.contains("\"expected_entry_version\":3"))
        let firstIdempKey = postReqsAfterFirst[0].headers["Idempotency-Key"] ?? ""
        let secondIdempKey = postReqsAfterSecond[1].headers["Idempotency-Key"] ?? ""
        XCTAssertFalse(firstIdempKey.isEmpty)
        XCTAssertFalse(secondIdempKey.isEmpty)
        XCTAssertNotEqual(firstIdempKey, secondIdempKey, "Re-submission after 409 must use a fresh Idempotency-Key")
        XCTAssertTrue(completed)
        XCTAssertNil(vm.directEditErrorMessage)
    }

    @MainActor
    func testRegression11_StalePaginationAndOpenDirectEditAcrossSessionsDiscarded() async throws {
        MockURLProtocol.resetRecordedUrls()
        let entryIdA = "aaaa1111-1111-4222-8333-444444444444"
        MockURLProtocol.pathDelaySec = [
            "/api/direct-entry/workers": 0.25,
            "/api/direct-entry/entries/\(entryIdA)": 0.25,
            "/api/direct-entry/submissions": 0.25
        ]
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let body: String
            if path.contains("/api/direct-entry/entries/\(entryIdA)") {
                body = """
                {"ok":true,"entry":{"entry_id":"\(entryIdA)","version":2,"employee_code":"hrp-1126-000101","display_name":"Chi Tiết Của A","project_id":"p1","project_display":"Dự án A","recruiter_id":"22222222-3333-4444-8555-666666666666","recruiter_display":"Tuyển A","labor_type":"OFFICIAL","first_work_date":"2026-11-01","employment_status":"ON","national_id":"001200012345","allowed_actions":{"view":true,"direct_edit":true}}}
                """
            } else if path == "/api/direct-entry/workers" {
                body = """
                {"ok":true,"scope":"managed","items":[{"entry_id":"\(entryIdA)","employee_code":"hrp-1126-000101","display_name":"Công Nhân Trang 2 Của A"}],"summary":{"total_workers":2}}
                """
            } else if path == "/api/direct-entry/submissions" {
                body = """
                {"ok":true,"items":[{"submission_id":"bbbb1111-2222-4333-8444-555555555555","state":"SUBMITTED","entry_count":2,"created_at":"2026-11-08T08:00:00Z"}],"has_more":false,"next_cursor":null}
                """
            } else {
                body = """
                {"ok":true,"items":[]}
                """
            }
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo11.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        let userA = MiniBiUser(
            id: "aaaa0011-2222-4333-8444-555555555555",
            username: "account.a",
            fullName: "Account A",
            permissions: ["entry_read", "entry_direct_edit", "submission_read"],
            allowedWorkerTabs: ["managed", "uploader"]
        )
        vm.currentUser = userA
        vm.isAuthenticated = true
        vm.selectedWorkerScope = "managed"
        vm.workersPage = 2
        vm.ownSubmissionsNextCursor = "cur-a-2"
        vm.ownSubmissionsHasMore = true
        // Pre-populate catalog so openDirectEdit goes straight to fetchWorkerEntryDetail
        vm.directEntryOptions = DirectEntryAssigneeOptions(
            catalogProjects: [DirectEntryCatalogProject(projectId: "p1", displayName: "Dự án A")]
        )

        let workerRowA = WorkerItem(maNld: entryIdA, hoTen: "Công Nhân A", canDirectEdit: true)
        let taskWorkersPage2A = Task { await vm.loadWorkers(resetPage: false) }
        let taskSubmissionsMoreA = Task { await vm.loadMoreOwnSubmissions() }
        let taskOpenDirectEditA = Task { await vm.openDirectEdit(workerRowA) }

        try await Task.sleep(nanoseconds: 50_000_000)

        // Switch session to Account B before Account A's pagination and openDirectEdit complete
        let userB = MiniBiUser(
            id: "bbbb0011-2222-4333-8444-555555555555",
            username: "luanpt",
            fullName: "Account B",
            permissions: ["entry_read"],
            allowedWorkerTabs: []
        )
        vm.onAuthenticatedSessionEstablished(user: userB)

        _ = await (taskWorkersPage2A.value, taskSubmissionsMoreA.value, taskOpenDirectEditA.value)
        try await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(vm.currentUser?.id, userB.id)
        XCTAssertTrue(vm.workers.isEmpty, "Account B workers must not contain page 2 data from Account A")
        XCTAssertTrue(vm.ownSubmissions.isEmpty, "Account B ownSubmissions must not contain appended items from Account A")
        XCTAssertNil(vm.directEditWorkerTarget, "Account B directEditWorkerTarget must remain nil")
        XCTAssertNil(vm.directEditBaselineDetail, "Account B directEditBaselineDetail must remain nil")
        XCTAssertFalse(vm.isLoadingWorkers)
        XCTAssertFalse(vm.isLoadingOwnSubmissions)
        XCTAssertFalse(vm.directEditLoadingDetail)
    }

    @MainActor
    func testRegression12_StalePT409ReloadAcrossSessionsDiscarded() async throws {
        MockURLProtocol.resetRecordedUrls()
        let entryIdA = "aaaa1212-2222-4333-8444-555555555555"
        let obligationIdA = "dddd1212-2222-4333-8444-555555555555"
        // Delay the GET reload endpoints triggered after 409 CONFLICT
        MockURLProtocol.pathDelaySec = [
            "/api/direct-entry/entries/\(entryIdA)": 0.25,
            "/api/work-reports/obligations/\(obligationIdA)": 0.25
        ]
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let path = url.path
            let method = req.httpMethod ?? "GET"
            if path.hasSuffix("/privileged-edit") && method == "POST" {
                let body = """
                {"ok":false,"code":"CONFLICT","message":"Version conflict"}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 409, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            } else if path.contains("/api/direct-entry/entries/\(entryIdA)") && method == "GET" {
                let body = """
                {"ok":true,"entry":{"entry_id":"\(entryIdA)","version":9,"employee_code":"hrp-1126-000109","display_name":"Hồ Sơ Mới Của A","project_id":"p1","project_display":"Dự án 1","recruiter_id":"22222222-3333-4444-8555-666666666666","recruiter_display":"Tuyển 1","labor_type":"OFFICIAL","first_work_date":"2026-11-01","employment_status":"ON","national_id":"001200012345","allowed_actions":{"view":true,"direct_edit":true}}}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            } else if path.contains("/api/work-reports/submissions/\(obligationIdA)/submit") && method == "POST" {
                let body = """
                {"ok":false,"code":"WORK_REPORT_VERSION_CONFLICT","message":"Obligation conflict"}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 409, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            } else if path.contains("/api/work-reports/obligations/\(obligationIdA)") && method == "GET" {
                let body = """
                {"ok":true,"obligation":{"obligation_id":"\(obligationIdA)","template_name":"Báo Cáo Của A v9","obligation_status":"ACTIVE","submission_state":"DRAFT","submission_version":9,"is_overdue":false}}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            } else {
                let body = """
                {"ok":true,"items":[]}
                """
                let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
                return (resp, Data(body.utf8))
            }
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo12.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        let userA = MiniBiUser(
            id: "aaaa0012-2222-4333-8444-555555555555",
            username: "account.a",
            fullName: "Account A",
            permissions: ["entry_read", "entry_direct_edit", "document_upload"],
            allowedWorkerTabs: ["managed"]
        )
        vm.currentUser = userA
        vm.isAuthenticated = true
        vm.directEditBaselineDetail = WorkerEntryDetail(
            entryId: entryIdA,
            version: 1,
            projectId: "p1",
            projectDisplay: "Dự án 1",
            firstWorkDate: "2026-11-01",
            employeeCode: "hrp-1126-000109",
            recruiterId: "22222222-3333-4444-8555-666666666666",
            recruiterDisplay: "Tuyển 1",
            providerType: "hrp",
            laborType: "OFFICIAL",
            laborTypeDisplay: "Chính thức",
            displayName: "Nguyễn Văn Cũ",
            gender: "MALE",
            dateOfBirth: "1995-01-01",
            nationalId: "001200012345",
            nationalIdIssuedAt: "2021-01-01",
            address: "Hà Nội",
            phone: "0912345678",
            employmentStatus: "ON",
            employmentEffectiveDate: "2026-11-01",
            canDirectEdit: true
        )
        vm.selectedObligationEditor = WorkReportsAndReportingService.parseObligationItem([
            "obligation_id": obligationIdA,
            "template_name": "Báo Cáo Của A",
            "obligation_status": "ACTIVE",
            "submission_state": "DRAFT",
            "submission_version": 1,
            "is_overdue": false
        ])

        var draftFormA = DirectEntryService.buildDirectEditFormInput(from: vm.directEditBaselineDetail!)
        draftFormA.hoTen = "Tên Sửa Bởi A"

        // Account A submits direct edit and work report -> both receive immediate 409 CONFLICT and enter delayed GET reload
        let taskDirectEdit409A = Task {
            await vm.submitDirectEditProfileAndDocuments(form: draftFormA, stagedFiles: [:])
        }
        let taskWorkReport409A = Task {
            await vm.submitWorkReportFinal()
        }

        try await Task.sleep(nanoseconds: 60_000_000)

        // Switch session to Account B while Account A's PT409 reload requests are still in-flight
        let userB = MiniBiUser(
            id: "bbbb0012-2222-4333-8444-555555555555",
            username: "luanpt",
            fullName: "Account B",
            permissions: ["entry_read"],
            allowedWorkerTabs: []
        )
        vm.onAuthenticatedSessionEstablished(user: userB)
        vm.dismissBanner()

        _ = await (taskDirectEdit409A.value, taskWorkReport409A.value)
        try await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(vm.currentUser?.id, userB.id)
        XCTAssertNil(vm.directEditBaselineDetail, "Account B directEditBaselineDetail must not be updated by Account A PT409 reload")
        XCTAssertNil(vm.directEditErrorMessage, "Account B directEditErrorMessage must remain nil")
        XCTAssertNil(vm.selectedObligationEditor, "Account B selectedObligationEditor must not be updated by Account A PT409 reload")
        XCTAssertNil(vm.bannerMessage, "Account B bannerMessage must not show Account A PT409 conflict banner")
    }

    @MainActor
    func testRegression13_NullUserAndMissingDocumentUploadCapabilityMakeZeroNetworkRequests() async throws {
        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.pathDelaySec = [:]
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let body = """
            {"ok":true,"items":[]}
            """
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo13.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)

        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )

        let entryId = "aaaa1313-2222-4333-8444-555555555555"
        let baseline = WorkerEntryDetail(
            entryId: entryId,
            version: 1,
            projectId: "p1",
            projectDisplay: "Dự án 1",
            firstWorkDate: "2026-11-01",
            employeeCode: "hrp-1126-000113",
            recruiterId: "22222222-3333-4444-8555-666666666666",
            recruiterDisplay: "Tuyển 1",
            providerType: "hrp",
            laborType: "OFFICIAL",
            laborTypeDisplay: "Chính thức",
            displayName: "Nguyễn Văn 13",
            gender: "MALE",
            dateOfBirth: "1995-01-01",
            nationalId: "001200012345",
            nationalIdIssuedAt: "2021-01-01",
            address: "Hà Nội",
            phone: "0912345678",
            employmentStatus: "ON",
            employmentEffectiveDate: "2026-11-01",
            canDirectEdit: true
        )
        let dummyImg = try makeSolidTestJpeg(color: .systemRed, size: CGSize(width: 200, height: 200))
        let attachment = LocalAttachmentItem(fileName: "portrait.jpg", mimeType: "image/jpeg", data: dummyImg)

        // Part 1: currentUser == nil -> openDirectEdit, submitDirectEditProfileAndDocuments, retryDirectEditDocument, uploadWorkerContract make 0 requests
        vm.currentUser = nil
        vm.isAuthenticated = false
        vm.directEditBaselineDetail = baseline
        var modifiedForm = DirectEntryService.buildDirectEditFormInput(from: baseline)
        modifiedForm.hoTen = "Tên Mới Khi Null User"

        await vm.openDirectEdit(WorkerItem(maNld: entryId, hoTen: "Nguyễn Văn 13", canDirectEdit: true))
        await vm.submitDirectEditProfileAndDocuments(
            form: modifiedForm,
            stagedFiles: [DirectEntryContract.docTypeWorkerPortrait: [attachment]]
        )
        await vm.retryDirectEditDocument(
            docType: DirectEntryContract.docTypeWorkerPortrait,
            attachments: [attachment]
        )
        _ = await vm.uploadWorkerContract(maNld: entryId, attachments: [attachment])
        XCTAssertTrue(MockURLProtocol.recordedUrls().isEmpty, "Null user must make 0 network requests")

        // Part 2: User WITHOUT `document_upload` capability -> retryDirectEditDocument, uploadWorkerContract, and doc-only submitDirectEditProfileAndDocuments make 0 requests
        MockURLProtocol.resetRecordedUrls()
        vm.currentUser = MiniBiUser(
            id: "aaaa0013-2222-4333-8444-555555555555",
            username: "nodoc.user",
            fullName: "No Doc Upload",
            permissions: ["entry_read", "entry_direct_edit"],
            allowedWorkerTabs: ["managed"]
        )
        vm.isAuthenticated = true
        vm.directEditBaselineDetail = baseline
        let unchangedForm = DirectEntryService.buildDirectEditFormInput(from: baseline)

        await vm.retryDirectEditDocument(
            docType: DirectEntryContract.docTypeWorkerPortrait,
            attachments: [attachment]
        )
        _ = await vm.uploadWorkerContract(maNld: entryId, attachments: [attachment])
        await vm.submitDirectEditProfileAndDocuments(
            form: unchangedForm,
            stagedFiles: [DirectEntryContract.docTypeWorkerPortrait: [attachment]]
        )
        XCTAssertTrue(MockURLProtocol.recordedUrls().isEmpty, "User without document_upload capability must make 0 document upload requests")
    }

    @MainActor
    func testRegression14_OwnSubmissionsLoadMoreInFlightSupersededByResetAndRapidOpenDirectEditKeepsWorkerB() async throws {
        let entryIdA = "aaaa1414-1111-4333-8444-555555555555"
        let entryIdB = "bbbb1414-2222-4333-8444-555555555555"
        let batchInitialId = "11111414-0000-4333-8444-555555555555"
        let batchOldPage2Id = "22221414-0000-4333-8444-555555555555"
        let batchFresh1Id = "33331414-0000-4333-8444-555555555555"

        MockURLProtocol.resetRecordedUrls()
        // Hold the load-more request (`cursor=cur-old-1`) and Worker A detail (`entries/<entryIdA>`)
        MockURLProtocol.pathDelaySec = [
            "cursor=cur-old-1": 0.5,
            "/api/direct-entry/entries/\(entryIdA)": 0.5
        ]
        MockURLProtocol.requestHandler = { req in
            let url = req.url!
            let fullUrl = url.absoluteString
            let path = url.path
            let body: String
            if path.contains("/api/direct-entry/submissions") {
                if fullUrl.contains("cursor=cur-old-1") {
                    body = """
                    {
                      "ok": true,
                      "items": [
                        {
                          "submission_id": "\(batchOldPage2Id)",
                          "state": "SUBMITTED",
                          "version": 1,
                          "entry_count": 3,
                          "created_at": "2026-11-08T01:00:00Z"
                        }
                      ],
                      "has_more": false,
                      "next_cursor": "cur-old-2"
                    }
                    """
                } else {
                    body = """
                    {
                      "ok": true,
                      "items": [
                        {
                          "submission_id": "\(batchFresh1Id)",
                          "state": "SUBMITTED",
                          "version": 2,
                          "entry_count": 5,
                          "created_at": "2026-11-09T02:00:00Z"
                        }
                      ],
                      "has_more": true,
                      "next_cursor": "cur-fresh-2"
                    }
                    """
                }
            } else if path.contains("/api/direct-entry/entries/\(entryIdA)") {
                body = """
                {
                  "ok": true,
                  "entry": {
                    "entry_id": "\(entryIdA)",
                    "version": 1,
                    "employee_code": "HRP-A14",
                    "display_name": "Hồ Sơ Chậm A",
                    "project_id": "p1",
                    "project_display": "Dự án 1",
                    "employment_status": "ON",
                    "can_direct_edit": true
                  }
                }
                """
            } else if path.contains("/api/direct-entry/entries/\(entryIdB)") {
                body = """
                {
                  "ok": true,
                  "entry": {
                    "entry_id": "\(entryIdB)",
                    "version": 2,
                    "employee_code": "HRP-B14",
                    "display_name": "Hồ Sơ Nhanh B",
                    "project_id": "p1",
                    "project_display": "Dự án 1",
                    "employment_status": "ON",
                    "can_direct_edit": true
                  }
                }
                """
            } else {
                body = """
                {"ok":true,"items":[]}
                """
            }
            let resp = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.repo14.\(UUID().uuidString)"))
        let directEntry = DirectEntryService(httpClient: client)
        let reporting = WorkReportsAndReportingService(httpClient: client)
        let salaryAdvance = SalaryAdvanceService(httpClient: client)
        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: directEntry,
            workReportsService: reporting,
            salaryAdvanceService: salaryAdvance
        )
        vm.currentUser = MiniBiUser(
            id: "aaaa0014-2222-4333-8444-555555555555",
            username: "uploader.14",
            fullName: "Uploader 14",
            permissions: ["entry_read", "entry_direct_edit", "submission_read"],
            allowedWorkerTabs: ["uploader", "managed"]
        )
        vm.isAuthenticated = true
        vm.directEntryOptions = DirectEntryAssigneeOptions(
            catalogProjects: [DirectEntryCatalogProject(projectId: "p1", displayName: "Dự án 1")]
        )

        // 1. Seed initial ownSubmissions state with cursor `cur-old-1`
        vm.ownSubmissions = [
            DirectEntryBatchHistoryItem(
                batchId: 1,
                submissionIdUuid: batchInitialId,
                status: "SUBMITTED",
                totalRows: 1,
                approvedRows: 1,
                pendingRows: 0,
                createdAt: "2026-11-08T00:00:00Z"
            )
        ]
        vm.ownSubmissionsNextCursor = "cur-old-1"
        vm.ownSubmissionsHasMore = true

        // Start loadMore (`cursor=cur-old-1`, held for 0.5s) and poll until request actually starts in MockURLProtocol
        let loadMoreTask = Task { await vm.loadMoreOwnSubmissions() }
        try await waitForRecordedUrl(containing: "cursor=cur-old-1")
        XCTAssertEqual(
            vm.ownSubmissions.map(\.submissionIdUuid),
            [batchInitialId],
            "Old loadMore request must be in-flight and not yet completed before reset runs"
        )

        // While loadMore is in-flight, user triggers reset ("Làm mới") -> completes immediately
        await vm.refreshOwnSubmissions(reset: true)
        XCTAssertEqual(vm.ownSubmissions.map(\.submissionIdUuid), [batchFresh1Id])
        XCTAssertEqual(vm.ownSubmissionsNextCursor, "cur-fresh-2")
        XCTAssertTrue(vm.ownSubmissionsHasMore)

        // Wait for the old loadMore response to finish and verify it does NOT append old items or overwrite cursor
        _ = await loadMoreTask.value
        try await Task.sleep(nanoseconds: 550_000_000)

        XCTAssertEqual(vm.ownSubmissions.map(\.submissionIdUuid), [batchFresh1Id], "Stale loadMore response must not append old items after reset")
        XCTAssertEqual(vm.ownSubmissionsNextCursor, "cur-fresh-2", "Stale loadMore response must not overwrite nextCursor of reset list")
        XCTAssertTrue(vm.ownSubmissionsHasMore)
        XCTAssertFalse(vm.isLoadingOwnSubmissions)
        XCTAssertNil(vm.ownSubmissionsErrorMessage)

        // 2. Rapid openDirectEdit(workerA) -> openDirectEdit(workerB) in same session: result must remain workerB
        let workerA = WorkerItem(maNld: entryIdA, hoTen: "Hồ Sơ Chậm A", canDirectEdit: true)
        let workerB = WorkerItem(maNld: entryIdB, hoTen: "Hồ Sơ Nhanh B", canDirectEdit: true)
        let openATask = Task { await vm.openDirectEdit(workerA) }
        try await waitForRecordedUrl(containing: "/api/direct-entry/entries/\(entryIdA)")
        XCTAssertNil(
            vm.directEditBaselineDetail,
            "Slow Worker A detail request must be in-flight and not yet completed before opening Worker B"
        )
        await vm.openDirectEdit(workerB)

        XCTAssertEqual(vm.directEditWorkerTarget?.maNld, entryIdB)
        XCTAssertEqual(vm.directEditBaselineDetail?.entryId, entryIdB)

        _ = await openATask.value
        try await Task.sleep(nanoseconds: 550_000_000)

        XCTAssertEqual(vm.directEditWorkerTarget?.maNld, entryIdB)
        XCTAssertEqual(vm.directEditBaselineDetail?.entryId, entryIdB)
        XCTAssertEqual(vm.directEditBaselineDetail?.displayName, "Hồ Sơ Nhanh B")
    }

    // MARK: - 23. Recruitment Posts Contract, Navigation & Fail-Closed Lifecycle (Section 9)

    func testRecruitmentPostsContractAndNavigationGate() throws {
        // 1-4. Main menu navigation & Cấp mới permission gate
        let fullUser = MiniBiUser(
            id: "aaaa0023-2222-4333-8444-555555555501",
            username: "full.user",
            fullName: "Full User",
            permissions: ["entry_create", "submission_create", "entry_read"],
            allowedWorkerTabs: ["managed", "recruited"]
        )
        let limitedUser = MiniBiUser(
            id: "aaaa0023-2222-4333-8444-555555555502",
            username: "limited.user",
            fullName: "Limited User",
            permissions: ["entry_read"],
            allowedWorkerTabs: []
        )
        XCTAssertEqual(
            MiniBiNavDestination.resolveMainNavigationDestinations(for: fullUser),
            [.dashboard, .workReports, .attendance, .workers, .recruitment]
        )
        XCTAssertEqual(
            MiniBiNavDestination.resolveMainNavigationDestinations(for: limitedUser),
            [.dashboard, .workReports, .attendance, .recruitment]
        )
        XCTAssertFalse(MiniBiNavDestination.resolveMainNavigationDestinations(for: fullUser).contains(.newEntry))
        XCTAssertTrue(fullUser.canAccessNewWorkerEntry)
        XCTAssertFalse(limitedUser.canAccessNewWorkerEntry)

        // 6 & 10. Contract parser, integer VND string formatting, created_by_display_name contact, and fail-closed validation
        let samplePostJson = Self.makeRecruitmentPostJson(
            briefId: "11111111-2222-4333-8444-555555555501",
            projectName: "Luxshare Bắc Giang",
            creatorName: "Nguyễn Người Tạo Tin",
            managerName: "Lê Quản Lý Cấm Hiển Thị"
        )
        let listBody = """
        {
          "ok": true,
          "data": {
            "items": [\(samplePostJson)],
            "page_size": 10,
            "has_more": true,
            "next_cursor": "cur-page-2",
            "snapshot_token": "snap-1"
          }
        }
        """
        let listObj = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(listBody.utf8)) as? [String: Any])
        let parsedList = try RecruitmentPostContract.parseListEnvelope(listObj)
        XCTAssertEqual(parsedList.items.count, 1)
        XCTAssertEqual(parsedList.pageSize, 10)
        XCTAssertTrue(parsedList.hasMore)
        XCTAssertEqual(parsedList.nextCursor, "cur-page-2")
        XCTAssertEqual(parsedList.snapshotToken, "snap-1")

        let card = RecruitmentPostContract.toCardViewModel(parsedList.items[0])
        XCTAssertEqual(card.contactPersonDisplay, "Nguyễn Người Tạo Tin")
        XCTAssertFalse(card.contactDisplay.contains("Lê Quản Lý Cấm Hiển Thị"))
        XCTAssertEqual(card.contactPhoneDisplay, "0988111222")
        XCTAssertEqual(card.incomeRangeDisplay, "9.500.000 đ – 14.500.000 đ")
        XCTAssertEqual(card.locationDisplay, "Bắc Giang · KCN Quang Châu")

        let detailBody = """
        {"ok":true,"data":{"post":\(samplePostJson)}}
        """
        let detailObj = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(detailBody.utf8)) as? [String: Any])
        let parsedDetail = try RecruitmentPostContract.parseDetailEnvelope(detailObj)
        let detailVm = RecruitmentPostContract.toDetailViewModel(parsedDetail)
        XCTAssertEqual(detailVm.section1General.contactPersonDisplay, "Nguyễn Người Tạo Tin")
        XCTAssertEqual(detailVm.section3CompensationBenefits.baseSalaryVndDisplay, "8.500.000 đ")
        XCTAssertEqual(detailVm.section3CompensationBenefits.seniorityAllowanceVndDisplay, "Chưa cung cấp")

        // Fail-closed: extra key, WITHDRAWN status in feed, or float money string must throw
        var extraKeyItem = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(samplePostJson.utf8)) as? [String: Any])
        extraKeyItem["unexpected_key"] = "boom"
        XCTAssertThrowsError(try RecruitmentPostContract.parseDetailEnvelope(["ok": true, "data": ["post": extraKeyItem]]))

        var withdrawnItem = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(samplePostJson.utf8)) as? [String: Any])
        withdrawnItem["status"] = "WITHDRAWN"
        withdrawnItem["withdrawn_at"] = "2026-11-10T09:00:00Z"
        withdrawnItem["withdrawn_by_display_name"] = "Admin"
        XCTAssertThrowsError(
            try RecruitmentPostContract.parseListEnvelope([
                "ok": true,
                "data": [
                    "items": [withdrawnItem],
                    "page_size": 10,
                    "has_more": false,
                    "next_cursor": NSNull(),
                    "snapshot_token": "snap-1"
                ]
            ])
        )

        var floatCompItem = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(samplePostJson.utf8)) as? [String: Any])
        var compDict = try XCTUnwrap(floatCompItem["compensation"] as? [String: Any])
        compDict["base_salary_vnd"] = "8500000.5"
        floatCompItem["compensation"] = compDict
        XCTAssertThrowsError(try RecruitmentPostContract.parseDetailEnvelope(["ok": true, "data": ["post": floatCompItem]]))
    }

    @MainActor
    func testRecruitmentPostsPaginationRefreshSnapshotRetry404AndSessionPurge() async throws {
        let brief1 = "11111111-2222-4333-8444-555555555501"
        let brief2 = "11111111-2222-4333-8444-555555555502"
        let briefSlow = "11111111-2222-4333-8444-555555555599"
        var snapshotInvalidMode = false
        var detailNotFoundMode = false
        var listResponseMode = "NORMAL"

        MockURLProtocol.resetRecordedUrls()
        MockURLProtocol.pathDelaySec = [
            "/api/recruitment-posts/\(briefSlow)": 0.4
        ]
        defer { MockURLProtocol.pathDelaySec = [:] }
        MockURLProtocol.requestHandler = { req in
            let url = try XCTUnwrap(req.url)
            let fullUrl = url.absoluteString
            let body: String
            var statusCode = 200

            if fullUrl.contains("/api/recruitment-posts?") {
                if listResponseMode == "FORBIDDEN" {
                    statusCode = 403
                    body = #"{"ok":false,"error":{"code":"ACTOR_NOT_AVAILABLE","message":"Forbidden"}}"#
                } else if listResponseMode == "NOT_FOUND_LIST" {
                    statusCode = 404
                    body = #"{"ok":false,"error":{"code":"NOT_FOUND","message":"Not found"}}"#
                } else if listResponseMode == "UNAVAILABLE_503" {
                    statusCode = 503
                    body = #"{"ok":false,"error":{"code":"RECRUITMENT_POST_UNAVAILABLE","message":"Unavailable"}}"#
                } else if fullUrl.contains("cursor=cur-p2") {
                    if snapshotInvalidMode {
                        statusCode = 400
                        body = #"{"ok":false,"error":{"code":"RECRUITMENT_POST_SNAPSHOT_INVALID","message":"Snapshot invalid"}}"#
                    } else {
                        body = """
                        {
                          "ok": true,
                          "data": {
                            "items": [
                              \(Self.makeRecruitmentPostJson(briefId: brief1, projectName: "Dự Án 1 Trùng", creatorName: "Người Tạo 1", managerName: "Quản Lý")),
                              \(Self.makeRecruitmentPostJson(briefId: brief2, projectName: "Dự Án 2 Mới", creatorName: "Người Tạo 2", managerName: "Quản Lý"))
                            ],
                            "page_size": 10,
                            "has_more": false,
                            "next_cursor": null,
                            "snapshot_token": "snap-p1"
                          }
                        }
                        """
                    }
                } else {
                    body = """
                    {
                      "ok": true,
                      "data": {
                        "items": [
                          \(Self.makeRecruitmentPostJson(briefId: brief1, projectName: "Dự Án 1", creatorName: "Người Tạo 1", managerName: "Quản Lý"))
                        ],
                        "page_size": 10,
                        "has_more": true,
                        "next_cursor": "cur-p2",
                        "snapshot_token": "snap-p1"
                      }
                    }
                    """
                }
            } else if fullUrl.contains("/api/recruitment-posts/\(briefSlow)") {
                body = """
                {"ok":true,"data":{"post":\(Self.makeRecruitmentPostJson(briefId: briefSlow, projectName: "Dự Án Chậm", creatorName: "Người Tạo Chậm", managerName: "Quản Lý"))}}
                """
            } else if fullUrl.contains("/api/recruitment-posts/\(brief1)") {
                if detailNotFoundMode {
                    statusCode = 404
                    body = #"{"ok":false,"error":{"code":"RECRUITMENT_POST_NOT_FOUND","message":"Not found"}}"#
                } else {
                    body = """
                    {"ok":true,"data":{"post":\(Self.makeRecruitmentPostJson(briefId: brief1, projectName: "Dự Án 1", creatorName: "Người Tạo 1", managerName: "Quản Lý"))}}
                    """
                }
            } else {
                body = #"{"ok":true,"items":[]}"#
            }

            let resp = HTTPURLResponse(url: url, statusCode: statusCode, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            return (resp, Data(body.utf8))
        }

        let client = makeMockHttpClient()
        let repo = MiniBiRepository(httpClient: client, keychain: KeychainStore(serviceName: "com.aistudio.minibi.rec23.\(UUID().uuidString)"))
        let vm = MiniBiViewModel(
            repository: repo,
            directEntryService: DirectEntryService(httpClient: client),
            workReportsService: WorkReportsAndReportingService(httpClient: client),
            salaryAdvanceService: SalaryAdvanceService(httpClient: client),
            recruitmentPostService: RecruitmentPostService(httpClient: client)
        )
        vm.currentUser = MiniBiUser(
            id: "aaaa0023-2222-4333-8444-555555555599",
            username: "rec.23",
            fullName: "Recruiter 23",
            permissions: ["entry_create", "submission_create"],
            allowedWorkerTabs: ["managed"]
        )
        vm.isAuthenticated = true

        // 5. Draft preservation when navigating to .newEntry from .workers
        vm.newWorkerDraftRows[0].hoTen = "Nháp Giữ Nguyên"
        vm.navigateTo(.newEntry)
        XCTAssertEqual(vm.currentDestination, .newEntry)
        XCTAssertEqual(vm.newWorkerDraftRows[0].hoTen, "Nháp Giữ Nguyên")

        // 7-8. Load page 1 and loadMore with snapshot_token + deduplication by brief_id
        await vm.loadRecruitmentPosts(reset: true)
        XCTAssertEqual(vm.recruitmentPosts.map(\.briefId), [brief1])
        XCTAssertTrue(vm.recruitmentPostsHasMore)
        XCTAssertFalse(MockURLProtocol.recordedUrls().contains(where: { $0.contains("/api/recruitment-posts/context") }))

        await vm.loadMoreRecruitmentPosts()
        XCTAssertEqual(vm.recruitmentPosts.map(\.briefId), [brief1, brief2])
        XCTAssertTrue(MockURLProtocol.recordedUrls().contains(where: { $0.contains("cursor=cur-p2") && $0.contains("snapshot_token=snap-p1") }))

        // 9. Slow detail request closed before completion does not commit state
        let slowDetailTask = Task { await vm.openRecruitmentPostDetail(briefId: briefSlow) }
        try await waitForRecordedUrl(containing: "/api/recruitment-posts/\(briefSlow)")
        vm.closeRecruitmentPostDetail()
        _ = await slowDetailTask.value
        XCTAssertNil(vm.selectedRecruitmentPostDetail)

        // 12a. 400 RECRUITMENT_POST_SNAPSHOT_INVALID resets and reloads page 1 at most once
        await vm.loadRecruitmentPosts(reset: true)
        snapshotInvalidMode = true
        await vm.loadMoreRecruitmentPosts()
        XCTAssertEqual(vm.recruitmentPosts.map(\.briefId), [brief1])

        // 12b. 404 RECRUITMENT_POST_NOT_FOUND on detail removes item and shows banner
        detailNotFoundMode = true
        await vm.openRecruitmentPostDetail(briefId: brief1)
        XCTAssertTrue(vm.recruitmentPosts.isEmpty)
        XCTAssertNil(vm.selectedRecruitmentPostDetail)
        XCTAssertEqual(vm.bannerMessage, "Tin tuyển dụng không còn hiệu lực.")

        // 12c. 403 access denied, 404 list feature unavailable, 503 retryable error
        listResponseMode = "FORBIDDEN"
        await vm.loadRecruitmentPosts(reset: true)
        XCTAssertTrue(vm.recruitmentPostsAccessDenied)

        listResponseMode = "NOT_FOUND_LIST"
        await vm.loadRecruitmentPosts(reset: true)
        XCTAssertTrue(vm.recruitmentPostsFeatureUnavailable)

        listResponseMode = "UNAVAILABLE_503"
        await vm.loadRecruitmentPosts(reset: true)
        XCTAssertNotNil(vm.recruitmentPostsError)
        XCTAssertTrue(vm.isAuthenticated)

        // 13. Session reset / logout purges all recruitment state
        await vm.performLogout()
        XCTAssertTrue(vm.recruitmentPosts.isEmpty)
        XCTAssertNil(vm.selectedRecruitmentPostDetail)
        XCTAssertFalse(vm.recruitmentPostsHasMore)
    }

    private static func makeRecruitmentPostJson(
        briefId: String,
        projectName: String,
        creatorName: String,
        managerName: String
    ) -> String {
        """
        {
          "brief_id": "\(briefId)",
          "version": 3,
          "status": "PUBLISHED",
          "project": {
            "project_id": "aaaaaaaa-1111-4222-8333-444444444401",
            "display_name": "\(projectName)"
          },
          "industrial_park": {
            "industrial_park_id": "aaaaaaaa-1111-4222-8333-444444444402",
            "industrial_park_code": "KCN_QUANG_CHAU",
            "display_name": "KCN Quang Châu",
            "active": true
          },
          "province": {
            "province_id": "aaaaaaaa-1111-4222-8333-444444444403",
            "province_code": "BAC_GIANG",
            "display_name": "Bắc Giang"
          },
          "published_at": "2026-11-10T08:30:00Z",
          "updated_at": "2026-11-10T08:30:00Z",
          "withdrawn_at": null,
          "created_by_display_name": "\(creatorName)",
          "updated_by_display_name": "\(creatorName)",
          "withdrawn_by_display_name": null,
          "general": {
            "customer_name": "Công ty TNHH Luxshare ICT",
            "investment_origin": "Trung Quốc",
            "manufacturing_industry": "Sản xuất linh kiện điện tử",
            "job_description": "Lắp ráp và kiểm tra linh kiện",
            "environment_types": ["CLEAN_ROOM", "NORMAL_ROOM"],
            "work_postures": ["SITTING", "STANDING"],
            "shift_pattern": "TWO_SHIFTS",
            "shift_details": "Ca ngày 08:00-20:00, ca đêm 20:00-08:00",
            "manager_name": "\(managerName)",
            "manager_phone": "0988111222",
            "labor_types": [
              {"labor_type_key": "thoi_vu", "display_name": "Thời vụ"},
              {"labor_type_key": "chinh_thuc", "display_name": "Chính thức"}
            ],
            "supply_schedule_text": "Tuyển liên tục",
            "expected_headcount": 150
          },
          "candidate": {
            "age_min": 18,
            "age_max": 40,
            "gender_requirement": "ANY",
            "education_requirement": "SECONDARY",
            "experience_required": false,
            "experience_details": null,
            "accept_former_customer_workers": true,
            "entry_assessment_text": "Test bảng chữ cái",
            "other_requirements_text": "Sức khỏe tốt"
          },
          "compensation": {
            "base_salary_vnd": "8500000",
            "base_salary_conditions": "Làm đủ 26 công",
            "attendance_bonus_vnd": "500000",
            "attendance_bonus_conditions": "Không nghỉ buổi nào",
            "living_allowance_vnd": "300000",
            "living_allowance_conditions": "Hỗ trợ nhà ở",
            "seniority_allowance_vnd": null,
            "seniority_allowance_conditions": null,
            "eyesight_allowance_vnd": "200000",
            "eyesight_allowance_conditions": "Bộ phận kính hiển vi",
            "other_allowance_text": "Phụ cấp phòng sạch 300.000 đ",
            "average_income_min_vnd": "9500000",
            "average_income_max_vnd": "14500000",
            "salary_payment_schedule": "Ngày 15 hàng tháng",
            "salary_advance_available": true,
            "salary_advance_amount_vnd": "1000000",
            "salary_advance_schedule": "Thứ 6 hàng tuần",
            "weekly_salary_available": false,
            "weekly_salary_payment_schedule": null,
            "day_shift_8h_vnd": "280000",
            "day_shift_11h_vnd": "410000",
            "night_shift_8h_vnd": "340000",
            "night_shift_11h_vnd": "490000",
            "sunday_day_11h_vnd": "580000",
            "sunday_night_11h_vnd": "660000",
            "holiday_day_11h_vnd": "850000",
            "holiday_night_11h_vnd": "950000",
            "ppe_provided": true,
            "ppe_details": "Cấp phát áo tĩnh điện và giày",
            "other_benefits": "Thưởng lễ tết đầy đủ"
          },
          "documents": {
            "require_original_cccd_and_copy": true,
            "require_certified_resume": false,
            "require_health_certificate_within_6_months": true,
            "require_photo_3x4": true,
            "require_degree_or_certificate": false,
            "require_social_insurance_book": false,
            "require_other_documents": true,
            "other_documents_text": "Mang bút bi xanh khi phỏng vấn"
          },
          "allowed_actions": {
            "can_edit": true,
            "can_withdraw": true
          }
        }
        """
    }

    // MARK: - Helper

    private func waitForRecordedUrl(containing substring: String, timeoutNanoseconds: UInt64 = 2_000_000_000) async throws {
        let pollInterval: UInt64 = 5_000_000
        var elapsed: UInt64 = 0
        while !MockURLProtocol.recordedUrls().contains(where: { $0.contains(substring) }) {
            if elapsed >= timeoutNanoseconds {
                XCTFail("Timed out waiting for request containing '\(substring)'; recorded: \(MockURLProtocol.recordedUrls())")
                return
            }
            try await Task.sleep(nanoseconds: pollInterval)
            elapsed += pollInterval
        }
    }

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
