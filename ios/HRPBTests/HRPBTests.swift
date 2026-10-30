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
        XCTAssertTrue(stagingEnv.isStaging)
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

    // MARK: - 7. Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") Contract Tests

    @MainActor
    func testLeaveTabVisibilityByAuthority() throws {
        // 1. Regular employee (non-approver) -> sees only Check-in and Leave Request, NEVER Leave Approval
        let employeeAuth = LeaveAuthorityProjection(
            appUserId: "11111111-2222-4333-8444-555555555555",
            isSharedNonEmployee: false,
            canSubmitRequest: true,
            canApproveRequests: false
        )
        XCTAssertEqual(
            LeaveContract.resolveVisibleAttendanceTabs(authority: employeeAuth, errorCode: nil),
            [.checkIn, .leaveRequest]
        )

        // 2. Approver (Team Leader / Department Head / Admin / BoD) -> sees all 3 tabs
        let approverAuth = LeaveAuthorityProjection(
            appUserId: "99999999-2222-4333-8444-555555555555",
            isSharedNonEmployee: false,
            isTeamLeader: true,
            canSubmitRequest: true,
            canApproveRequests: true
        )
        XCTAssertEqual(
            LeaveContract.resolveVisibleAttendanceTabs(authority: approverAuth, errorCode: nil),
            [.checkIn, .leaveRequest, .leaveApproval]
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

        // 4. ViewModel defaults to `.checkIn` and prevents selecting `.leaveApproval` when not authorized
        let vm = MiniBiViewModel()
        XCTAssertEqual(vm.activeAttendanceTab, .checkIn)
        vm.selectAttendanceTab(.leaveApproval)
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
        XCTAssertEqual(parsedUser.allowedWorkerScopes, ["team", "managed", "recruited"])

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

        // Navigation menu order: Tổng quan - Báo cáo - Chấm công - Nhân sự - Cấp mới
        XCTAssertEqual(
            MiniBiNavDestination.allCases,
            [.dashboard, .workReports, .attendance, .workers, .newEntry]
        )
        XCTAssertEqual(
            MiniBiNavDestination.allCases.map(\.titleVi),
            ["Tổng quan", "Báo cáo", "Chấm công", "Nhân sự", "Cấp mới"]
        )

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

        let validationErrors = DirectEntryService.validateDirectEditFormFields(baseline: baseline, form: form)
        XCTAssertTrue(validationErrors.isEmpty, "Unexpected validation errors: \(validationErrors)")

        let patch = DirectEntryService.buildDirectEditPatch(baseline: baseline, form: form)
        XCTAssertNil(patch["employee_code"])
        let paymentPatch = try XCTUnwrap(patch["payment"] as? [String: Any])
        XCTAssertEqual(paymentPatch["state"] as? String, "provided")
        XCTAssertEqual(paymentPatch["bank_name"] as? String, "Techcombank")
        XCTAssertEqual(paymentPatch["account_number"] as? String, "1903667788")
        XCTAssertEqual(paymentPatch["account_holder_name"] as? String, "NGUYEN VAN AN")

        // When user modifies employee_code to an invalid format -> validation catches `ma_nld`
        var badCodeForm = form
        badCodeForm.maNld = "hrp-2026-999999"
        let badCodeErrors = DirectEntryService.validateDirectEditFormFields(baseline: baseline, form: badCodeForm)
        XCTAssertNotNil(badCodeErrors["ma_nld"])

        // Valid new employee_code `hrp-1026-001456` passes validation and is included in patch
        var validCodeForm = form
        validCodeForm.maNld = "hrp-1026-001456"
        XCTAssertTrue(DirectEntryService.validateDirectEditFormFields(baseline: baseline, form: validCodeForm).isEmpty)
        let validCodePatch = DirectEntryService.buildDirectEditPatch(baseline: baseline, form: validCodeForm)
        XCTAssertEqual(validCodePatch["employee_code"] as? String, "hrp-1026-001456")

        // VietQR Bank Catalog search & initial resolution
        XCTAssertGreaterThanOrEqual(VietQrBankCatalog.banks.count, 55)
        let resolvedKnown = VietQrBankCatalog.resolveForInitialValue("Techcombank")
        XCTAssertFalse(resolvedKnown.isCustom)
        XCTAssertEqual(resolvedKnown.selectedShortName, "Techcombank")

        let resolvedCustom = VietQrBankCatalog.resolveForInitialValue("Ngân hàng Nông Thôn X")
        XCTAssertTrue(resolvedCustom.isCustom)
        XCTAssertEqual(resolvedCustom.customBankName, "Ngân hàng Nông Thôn X")
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
