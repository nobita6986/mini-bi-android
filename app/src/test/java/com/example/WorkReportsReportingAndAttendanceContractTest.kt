package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActorScope
import com.example.data.AppActor
import com.example.data.AttendanceAreaTab
import com.example.data.AttendanceContract
import com.example.data.AttendanceOutboxService
import com.example.data.AttendanceOutboxStatus
import com.example.data.BackendApiException
import com.example.data.BiBucketItem
import com.example.data.CatalogRecruiterOption
import com.example.data.DashboardDatePreset
import com.example.data.LeaveContract
import com.example.data.LeaveDraftDayInput
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.WorkReportsContract
import com.example.data.WorkerProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkReportsReportingAndAttendanceContractTest {

    private lateinit var context: Context
    private lateinit var database: MiniBiDatabase
    private lateinit var server: MockWebServer
    private lateinit var cookieFile: File
    private lateinit var timeAnchorFile: File

    private val testActor = AppActor(
        appUserId = "11111111-2222-4333-8444-555555555555",
        displayName = "Nhân sự Thực Địa",
        capabilities = listOf("entry_read", "report_review", "report_analytics_view"),
        scopes = listOf(ActorScope("team", "TEAM_NORTH", "2026-01-01", null)),
        selfRecruiterSuggestion = null
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MiniBiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        cookieFile = File(context.cacheDir, "wr_att_cookies_${System.nanoTime()}.enc")
        timeAnchorFile = File(context.cacheDir, "wr_att_anchor_${System.nanoTime()}.enc")
    }

    @After
    fun tearDown() {
        database.close()
        runCatching { server.shutdown() }
        runCatching { cookieFile.delete() }
        runCatching { timeAnchorFile.delete() }
    }

    private fun createRepository(): MiniBiRepository {
        return MiniBiRepository(
            context = context,
            database = database,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            timeAnchorStorageFile = timeAnchorFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
    }

    private fun createValidPhotoFile(name: String = "photo_${System.nanoTime()}.jpg"): File {
        val f = File(context.cacheDir, name)
        f.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5))
        return f
    }

    // -------------------------------------------------------------------------
    // Item 15: Work Report draft/submit/review dùng đúng endpoint và version
    // -------------------------------------------------------------------------
    @Test
    fun `item 15 - work report draft submit and review call exact endpoints with expected_version and canonical state`() = runBlocking {
        val repo = createRepository()
        val obligationId = "aaaa1111-bbbb-4222-8333-cccc44445555"
        val templateId = "dddd1111-eeee-4222-8333-ffff44445555"

        val dynamicSchema = """
            {
              "contract_version": "work-report-schema/1.0",
              "sections": [{"section_key": "kpi", "label": "KPI Tuyển dụng", "sort_order": 1}],
              "fields": [
                {"field_key": "calls_made", "section_key": "kpi", "field_type": "integer", "label": "Số cuộc gọi", "required": true, "sort_order": 1},
                {
                  "field_key": "channel",
                  "section_key": "kpi",
                  "field_type": "single_select",
                  "label": "Kênh chính",
                  "required": true,
                  "sort_order": 2,
                  "options": [
                    {"option_key": "FB", "label": "Facebook", "active": true, "sort_order": 1},
                    {"option_key": "OLD", "label": "Kênh cũ", "active": false, "sort_order": 2}
                  ]
                }
              ]
            }
        """.trimIndent()

        // Verify dynamic schema parser filters inactive options and preserves canonical keys
        val sections = WorkReportsContract.parseDynamicSections(dynamicSchema)
        assertEquals(1, sections.size)
        assertEquals(2, sections[0].fields.size)
        assertEquals(listOf("FB"), sections[0].fields[1].options.map { it.value })

        // 1. Save Draft: POST /api/work-reports/submissions/{obligationId}/draft
        //    followed by canonical GET /api/work-reports/obligations/{obligationId}
        //    and refresh GET /api/work-reports/obligations
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"data":{"version":1}}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "data": {
                    "obligation": {
                      "obligation_id": "$obligationId",
                      "template_id": "$templateId",
                      "period_type": "DAILY",
                      "period_key": "2026-10-23",
                      "period_start_date": "2026-10-23",
                      "period_end_date": "2026-10-23",
                      "due_at": "2026-10-23T17:00:00Z",
                      "is_mandatory": true,
                      "obligation_status": "ACTIVE",
                      "version": 1
                    },
                    "template": {
                      "template_code": "DAILY_REC",
                      "display_name": "Báo cáo ngày",
                      "schema_json": $dynamicSchema
                    },
                    "submission": {
                      "submission_id": "99991111-2222-4333-8444-555566667777",
                      "state": "DRAFT",
                      "version": 1,
                      "draft_payload_json": {"calls_made": 25, "channel": "FB"}
                    },
                    "revisions": [],
                    "latest_review_event": null
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"data":[]}"""))

        val draftPayload = JSONObject().put("calls_made", 25).put("channel", "FB")
        val savedDraft = repo.workReportsAndReportingService.saveDraft(
            obligationId = obligationId,
            expectedVersion = null,
            payloadJson = draftPayload
        )
        assertEquals("DRAFT", savedDraft.submissionState)
        assertEquals(1, savedDraft.submissionVersion)

        val draftPostReq = server.takeRequest()
        assertEquals("POST", draftPostReq.method)
        assertEquals("/api/work-reports/submissions/$obligationId/draft", draftPostReq.path)
        val draftBody = JSONObject(draftPostReq.body.readUtf8())
        assertTrue(draftBody.isNull("expected_version"))
        assertEquals(draftPostReq.getHeader("Idempotency-Key"), draftBody.getString("idempotency_key"))
        assertEquals(25, draftBody.getJSONObject("payload_json").getInt("calls_made"))
        server.takeRequest() // detail refetch
        server.takeRequest() // list refetch

        // 2. Submit: POST /api/work-reports/submissions/{obligationId}/submit with expected_version = 1
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"data":{"version":2}}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "data": {
                    "obligation": {
                      "obligation_id": "$obligationId",
                      "template_id": "$templateId",
                      "period_type": "DAILY",
                      "period_key": "2026-10-23",
                      "period_start_date": "2026-10-23",
                      "period_end_date": "2026-10-23",
                      "due_at": "2026-10-23T17:00:00Z",
                      "is_mandatory": true,
                      "obligation_status": "ACTIVE",
                      "version": 1
                    },
                    "template": {
                      "template_code": "DAILY_REC",
                      "display_name": "Báo cáo ngày",
                      "schema_json": $dynamicSchema
                    },
                    "submission": {
                      "submission_id": "99991111-2222-4333-8444-555566667777",
                      "state": "PENDING_REVIEW",
                      "version": 2,
                      "draft_payload_json": null,
                      "latest_submitted_at": "2026-10-23T10:00:00Z"
                    },
                    "revisions": [{"revision_no": 1, "payload_json": {"calls_made": 25, "channel": "FB"}}],
                    "latest_review_event": null
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"data":[]}"""))

        val submitted = repo.workReportsAndReportingService.submitReport(
            obligationId = obligationId,
            expectedVersion = 1,
            payloadJson = draftPayload
        )
        assertEquals("PENDING_REVIEW", submitted.submissionState)
        assertEquals(2, submitted.submissionVersion)

        val submitPostReq = server.takeRequest()
        assertEquals("POST", submitPostReq.method)
        assertEquals("/api/work-reports/submissions/$obligationId/submit", submitPostReq.path)
        assertEquals(1, JSONObject(submitPostReq.body.readUtf8()).getInt("expected_version"))
        server.takeRequest()
        server.takeRequest()

        // 3. Review decision: POST /api/work-reports/reviews/{obligationId}/decision with expected_version = 2
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        repo.workReportsAndReportingService.decideReview(
            obligationId = obligationId,
            approve = false,
            reason = "Cần bổ sung số lượng hồ sơ hẹn phỏng vấn",
            expectedVersion = 2
        )
        val reviewReq = server.takeRequest()
        assertEquals("POST", reviewReq.method)
        assertEquals("/api/work-reports/reviews/$obligationId/decision", reviewReq.path)
        val reviewBody = JSONObject(reviewReq.body.readUtf8())
        assertEquals("REJECTED", reviewBody.getString("decision"))
        assertEquals(2, reviewBody.getInt("expected_version"))
        assertEquals("Cần bổ sung số lượng hồ sơ hẹn phỏng vấn", reviewBody.getString("reason"))

        // Team bucket key wire encoding verification
        assertEquals("team.VEVBTV9OT1JUSA", WorkReportsContract.encodeTeamBucketKey("TEAM_NORTH"))
        assertEquals("unknown", WorkReportsContract.encodeTeamBucketKey("unknown"))
    }

    @Test
    fun `item 15b - work report analytics preserves 0-to-100 completion_rate without 100x bug and never exposes raw uuid title`() = runBlocking {
        val repo = createRepository()
        val templateId = "f13f8d6d-1111-4222-8333-444455556666"

        val overviewBody = """
            {
              "ok": true,
              "data": {
                "as_of": "2026-10-23T10:00:00Z",
                "effective_scope": {"kind": "team", "label": "Team 1"},
                "compliance": {
                  "due_count": 7,
                  "completed_count": 1,
                  "on_time_count": 1,
                  "late_submitted_count": 0,
                  "pending_due_count": 4,
                  "overdue_missing_count": 2,
                  "exempted_count": 0,
                  "pending_review_count": 1,
                  "completion_rate": 14.29
                },
                "metrics": [
                  {
                    "metric_key": "calls_made",
                    "label": "Số cuộc gọi",
                    "unit": "cuộc",
                    "compatible_group": "integer",
                    "sum_value": 35,
                    "avg_value": 35,
                    "min_value": 35,
                    "max_value": 35,
                    "count_valid": 1,
                    "missing_value_count": 0
                  }
                ]
              }
            }
        """.trimIndent()
        val breakdownBody = """
            {
              "ok": true,
              "data": {
                "as_of": "2026-10-23T10:00:00Z",
                "effective_scope": {"kind": "team", "label": "Team 1"},
                "group_by": "team",
                "page": 1,
                "page_size": 50,
                "total_groups": 1,
                "items": [
                  {
                    "group_id": "aaaa1111-bbbb-4222-8333-cccc44445555",
                    "group_label": "Team 1",
                    "due_count": 7,
                    "completed_count": 1,
                    "on_time_count": 1,
                    "late_submitted_count": 0,
                    "pending_due_count": 4,
                    "overdue_missing_count": 2,
                    "exempted_count": 0,
                    "pending_review_count": 1,
                    "completion_rate": 14.29,
                    "metrics": [
                      {
                        "metric_key": "calls_made",
                        "label": "Số cuộc gọi",
                        "unit": "cuộc",
                        "compatible_group": "integer",
                        "sum_value": 35,
                        "avg_value": 35,
                        "min_value": 35,
                        "max_value": 35,
                        "count_valid": 1,
                        "missing_value_count": 0
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
        val contribBody = """
            {
              "ok": true,
              "data": {
                "as_of": "2026-10-23T10:00:00Z",
                "effective_scope": {"kind": "team", "label": "Team 1"},
                "next_cursor": null,
                "items": [
                  {
                    "obligation_id": "bbbb1111-cccc-4222-8333-dddd44445555",
                    "submission_id": "cccc1111-dddd-4222-8333-eeee44445555",
                    "revision_no": 1,
                    "assignee_app_user_id": "11111111-2222-4333-8444-555555555555",
                    "assignee_display_name": "Nguyễn Thị Vinh",
                    "snapshot_team_id": null,
                    "team_display_name": "Team 1",
                    "period_key": "2026-10-23",
                    "state": "APPROVED",
                    "submitted_at": "2026-10-23T09:30:00Z",
                    "is_late": false,
                    "has_post_deadline_amendment": false,
                    "metric_contribution_value": null
                  }
                ]
              }
            }
        """.trimIndent()

        // 1. With known templateNameHint -> exact human-readable name & 14.29% (never 1429.0%!)
        server.enqueue(MockResponse().setResponseCode(200).setBody(overviewBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(breakdownBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(contribBody))

        val snapNamed = repo.workReportsAndReportingService.fetchAnalyticsSnapshot(
            templateId = templateId,
            templateNameHint = "Báo cáo công việc hằng ngày",
            groupBy = "team",
            finality = "final"
        )
        assertEquals("Báo cáo công việc hằng ngày", snapNamed.templateName)
        assertEquals("Team 1", snapNamed.scopeLabel)
        assertEquals(7, snapNamed.totalObligations)
        assertEquals(1, snapNamed.submittedCount)
        assertEquals(1, snapNamed.approvedCount)
        assertEquals(2, snapNamed.overdueMissingCount)
        assertEquals(1, snapNamed.pendingReviewCount)
        assertEquals(14.29f, snapNamed.complianceRatePct!!, 0.01f)
        assertEquals("14,29%", WorkReportsContract.formatPercentVi(snapNamed.complianceRatePct))
        assertEquals(14.29f, snapNamed.breakdownRows.first().complianceRatePct!!, 0.01f)
        assertEquals(listOf("Số cuộc gọi: 35 cuộc"), snapNamed.breakdownRows.first().metricsSummary)

        // 2. Even if templateNameHint is empty, fallback must be human Vietnamese ("Báo cáo định kỳ"), NEVER "Mẫu f13f8d6d"
        server.enqueue(MockResponse().setResponseCode(200).setBody(overviewBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(breakdownBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(contribBody))

        val snapUnnamed = repo.workReportsAndReportingService.fetchAnalyticsSnapshot(
            templateId = templateId,
            templateNameHint = ""
        )
        assertEquals("Báo cáo định kỳ", snapUnnamed.templateName)
        assertFalse(snapUnnamed.templateName.contains("f13f8d6d"))

        // 3. Vietnamese display formatters
        assertEquals("Đã duyệt", WorkReportsContract.submissionStateDisplayLabel("APPROVED"))
        assertEquals("Chờ duyệt", WorkReportsContract.submissionStateDisplayLabel("PENDING_REVIEW"))
        assertEquals("23/10/2026", WorkReportsContract.formatPeriodKeyVi("2026-10-23"))
        assertEquals("16:30 23/10/2026", WorkReportsContract.formatIsoDateTimeVi("2026-10-23T09:30:00Z"))
    }

    // -------------------------------------------------------------------------
    // Item 16: Attendance giữ nguyên idempotency key qua retry và không tạo SHA giả
    // -------------------------------------------------------------------------
    @Test
    fun `item 16 - attendance preserves exact idempotency key across retries and fails closed on missing photo`() = runBlocking {
        val repo = createRepository()
        val scheduleCounter = AtomicInteger(0)

        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = { scheduleCounter.incrementAndGet() }
        )

        // Missing photo file MUST fail closed before DB insert or HTTP request (never fabricate fake SHA!)
        val missingPhoto = File(context.cacheDir, "does_not_exist_${System.nanoTime()}.jpg")
        val missingErr = runCatching {
            service.submitCheckIn(
                actor = testActor,
                latitude = 21.0285,
                longitude = 105.8542,
                accuracyMeters = 8.5f,
                addressHint = "Hà Nội",
                sourcePhotoFile = missingPhoto,
                sourcePhotoUri = null,
                note = ""
            )
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(missingErr)
        assertEquals("PHOTO_HASH_FAILED", missingErr!!.backendCode)
        assertEquals(0, server.requestCount)
        assertTrue(database.miniBiDao().getRetryableAttendanceOutbox().isEmpty())

        // Valid photo: Attempt 1 gets 503 -> Attempt 2 gets 429 -> Attempt 3 gets 200 OK
        val photoFile = createValidPhotoFile()
        val expectedSha = MiniBiRepository.computeSha256HexStrict(photoFile)

        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"ok":false,"code":"TEMP_DOWN"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"ok":false,"code":"RATE_LIMITED"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "checkin": {
                    "checkin_id": "dddd9999-eeee-4888-8777-ffff66665555",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 3,
                    "daily_count": 3,
                    "captured_at": "2026-10-23T01:20:12.000Z",
                    "received_at": "2026-10-23T01:25:00.000Z",
                    "time_source": "DEVICE_CLOCK_UNVERIFIED",
                    "replayed": false
                  }
                }
                """.trimIndent()
            )
        )

        val firstOutcome = service.submitCheckIn(
            actor = testActor,
            latitude = 21.028511,
            longitude = 105.854444,
            accuracyMeters = 6.2f,
            addressHint = "Hoàn Kiếm, Hà Nội",
            sourcePhotoFile = photoFile,
            sourcePhotoUri = "content://test/checkin1.jpg",
            note = "Ca sáng"
        )
        assertFalse(firstOutcome.isSynced)
        assertEquals(AttendanceOutboxStatus.RETRYABLE_ERROR, firstOutcome.record.syncStatus)
        assertNull(firstOutcome.record.serverSequenceInDay) // Never client-invented!
        assertEquals(1, scheduleCounter.get())

        val fixedKey = firstOutcome.record.idempotencyKey

        // Retry #1 (hits 429)
        val flush1 = service.flushRetryableOutbox(appUserId = testActor.appUserId)
        assertEquals(0, flush1.syncedCount)
        assertEquals(1, flush1.remainingRetryableCount)

        // Retry #2 (hits 200 OK)
        val flush2 = service.flushRetryableOutbox(appUserId = testActor.appUserId)
        assertEquals(1, flush2.syncedCount)
        assertEquals(0, flush2.remainingRetryableCount)

        val finalRecord = database.miniBiDao().getAttendanceById(firstOutcome.record.id)!!
        assertEquals(AttendanceOutboxStatus.SYNCED, finalRecord.syncStatus)
        assertEquals("Đã chấm công", finalRecord.statusMessage)
        assertEquals(3, finalRecord.serverSequenceInDay)
        assertEquals(fixedKey, finalRecord.idempotencyKey)

        // Verify all 3 HTTP requests carried the EXACT same Idempotency-Key & canonical metadata
        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        val req3 = server.takeRequest()

        assertEquals(fixedKey, req1.getHeader("Idempotency-Key"))
        assertEquals(fixedKey, req2.getHeader("Idempotency-Key"))
        assertEquals(fixedKey, req3.getHeader("Idempotency-Key"))
        assertNotNull(req3.getHeader("Origin"))

        val multipartText = req3.body.readUtf8()
        assertTrue(multipartText.contains("\"idempotency_key\":\"$fixedKey\""))
        assertTrue(multipartText.contains("\"photo_sha256\":\"$expectedSha\""))
        assertFalse(multipartText.contains("checkInType"))
        assertFalse(multipartText.contains("sequence_in_day"))
    }

    // -------------------------------------------------------------------------
    // Item 17: Attendance 404 không bị coi là thành công
    // -------------------------------------------------------------------------
    @Test
    fun `item 17 - attendance 404 maps to BACKEND_NOT_AVAILABLE with honest message and never auto-retries`() = runBlocking {
        val repo = createRepository()
        val scheduleCounter = AtomicInteger(0)

        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = { scheduleCounter.incrementAndGet() }
        )

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"ok":false,"code":"NOT_FOUND"}"""))

        val outcome = service.submitCheckIn(
            actor = testActor,
            latitude = 10.7769,
            longitude = 106.7009,
            accuracyMeters = 10f,
            addressHint = "Quận 1, TP.HCM",
            sourcePhotoFile = createValidPhotoFile(),
            sourcePhotoUri = null,
            note = ""
        )

        assertFalse("404 must NEVER be treated as synced", outcome.isSynced)
        assertTrue(outcome.isError)
        assertEquals(AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE, outcome.record.syncStatus)
        assertEquals("Chức năng chấm công chưa được bật trên máy chủ", outcome.record.statusMessage)
        assertEquals("Chức năng chấm công chưa được bật trên máy chủ", outcome.userMessage)
        assertEquals(0, scheduleCounter.get())
        assertTrue(database.miniBiDao().getRetryableAttendanceOutbox().isEmpty())
    }

    // -------------------------------------------------------------------------
    // Item 18: Network / timeout / 429 / 5xx mới được đưa vào retry queue
    // -------------------------------------------------------------------------
    @Test
    fun `item 18 - only network offline 429 and 5xx enter retry queue while 400 401 403 404 409 422 do not`() = runBlocking {
        val repo = createRepository()
        var online = false
        val scheduleCounter = AtomicInteger(0)

        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { online },
            outboxWorkScheduler = { scheduleCounter.incrementAndGet() }
        )

        // 1. Offline before send -> QUEUED + scheduled
        val offlineOutcome = service.submitCheckIn(
            actor = testActor,
            latitude = 21.0,
            longitude = 105.8,
            accuracyMeters = 5f,
            addressHint = "Offline site",
            sourcePhotoFile = createValidPhotoFile("off.jpg"),
            sourcePhotoUri = null,
            note = ""
        )
        assertEquals(AttendanceOutboxStatus.QUEUED, offlineOutcome.record.syncStatus)
        assertEquals(AttendanceContract.MSG_QUEUED_OFFLINE, offlineOutcome.userMessage)
        assertEquals(1, scheduleCounter.get())

        // Go online and test socket disconnect -> RETRYABLE_ERROR
        online = true
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val netDropOutcome = service.submitCheckIn(
            actor = testActor,
            latitude = 21.0,
            longitude = 105.8,
            accuracyMeters = 5f,
            addressHint = "Drop site",
            sourcePhotoFile = createValidPhotoFile("drop.jpg"),
            sourcePhotoUri = null,
            note = ""
        )
        assertEquals(AttendanceOutboxStatus.RETRYABLE_ERROR, netDropOutcome.record.syncStatus)
        assertEquals(2, scheduleCounter.get())

        // Test terminal non-retryable HTTP statuses: 400, 401, 403, 404, 409, 422
        val nonRetryableCases = listOf(
            400 to AttendanceOutboxStatus.REJECTED,
            401 to AttendanceOutboxStatus.AUTH_REQUIRED,
            403 to AttendanceOutboxStatus.DENIED,
            404 to AttendanceOutboxStatus.BACKEND_NOT_AVAILABLE,
            409 to AttendanceOutboxStatus.REJECTED,
            422 to AttendanceOutboxStatus.REJECTED
        )

        val scheduledBeforeTerminal = scheduleCounter.get()
        for ((httpCode, expectedStatus) in nonRetryableCases) {
            server.enqueue(MockResponse().setResponseCode(httpCode).setBody("""{"ok":false,"code":"ERR_$httpCode"}"""))
            val res = service.submitCheckIn(
                actor = testActor,
                latitude = 21.0,
                longitude = 105.8,
                accuracyMeters = 5f,
                addressHint = "Case $httpCode",
                sourcePhotoFile = createValidPhotoFile("c_$httpCode.jpg"),
                sourcePhotoUri = null,
                note = ""
            )
            assertEquals("Unexpected status for HTTP $httpCode", expectedStatus, res.record.syncStatus)
            assertFalse(res.isSynced)
        }

        // Scheduler must NOT have been called for any of 400/401/403/404/409/422
        assertEquals(scheduledBeforeTerminal, scheduleCounter.get())

        // Only the 2 retryable records (offline QUEUED + network drop RETRYABLE_ERROR) are in retryable outbox!
        val retryableIds = database.miniBiDao().getRetryableAttendanceOutbox().map { it.id }.toSet()
        assertEquals(setOf(offlineOutcome.record.id, netDropOutcome.record.id), retryableIds)
    }

    @Test
    fun `web bi reporting summary and team report aggregates align with server contract`() = runBlocking {
        // 1. Pure hiring-day summary matches Web BI p1-dashboard-summary.ts
        val daySummary = WorkReportsContract.computeHiringDaySummary(
            byDateCounts = mapOf(
                "2026-10-12" to 4,
                "2026-10-10" to 4,
                "2026-10-11" to 1,
                "2026-10-09" to 0
            ),
            totalCount = 9
        )
        assertEquals(3, daySummary.activeHiringDays)
        assertEquals(3.0f, daySummary.avgPerActiveDay!!, 0.01f)
        assertEquals(4, daySummary.peakDailyCount)
        assertEquals("2026-10-10", daySummary.peakDateIso) // earliest date wins tie

        // 2. fetchTeamReport parses full Web BI report aggregates + detail rows
        val repo = createRepository()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "bucket": {
                        "key": "team.VEVBTV8wMQ",
                        "display": "Nhóm Tuyển dụng Miền Bắc"
                      },
                      "report": {
                        "recruitedTotal": 5,
                        "byRecruiter": {
                          "rec.1": {"key": "rec.1", "display": "Nguyễn Văn Tuyển", "recruitedCount": 3},
                          "rec.2": {"key": "rec.2", "display": "Lê Thị Tuyển", "recruitedCount": 2}
                        },
                        "byProject": {
                          "prj.1": {"key": "prj.1", "display": "Luxshare Bắc Giang", "recruitedCount": 5}
                        },
                        "byEmployment": {
                          "emp.tv": {"key": "emp.tv", "display": "Thời vụ", "recruitedCount": 4},
                          "emp.ct": {"key": "emp.ct", "display": "Chính thức", "recruitedCount": 1}
                        },
                        "byProvider": {
                          "prv.hrp": {"key": "prv.hrp", "display": "HRP", "recruitedCount": 5}
                        },
                        "byDate": {
                          "2026-10-20": 3,
                          "2026-10-21": 2
                        }
                      },
                      "detail": {
                        "total": 5,
                        "rows": [
                          {
                            "businessDate": "2026-10-21",
                            "projectDisplay": "Luxshare Bắc Giang",
                            "recruiterDisplay": "Nguyễn Văn Tuyển",
                            "providerTypeDisplay": "HRP",
                            "employmentTypeDisplay": "Thời vụ",
                            "recruitedCount": 2,
                            "firstWorkDate": "2026-10-21"
                          }
                        ],
                        "nextCursor": null
                      }
                    }
                    """.trimIndent()
                )
        )

        val snap = repo.workReportsAndReportingService.fetchTeamReport("TEAM_01")
        assertEquals("Nhóm Tuyển dụng Miền Bắc", snap.bucketDisplay)
        assertEquals(5, snap.totalRecruited)
        assertEquals(2, snap.activeRecruiterCount)
        assertEquals(1, snap.activeProjectCount)
        assertEquals(2, snap.activeHiringDays)
        assertEquals(2.5f, snap.avgPerActiveDay!!, 0.01f)
        assertEquals(3, snap.peakDailyCount)
        assertEquals("2026-10-20", snap.peakDateIso)
        assertEquals(2, snap.byRecruiter.size)
        assertEquals("Nguyễn Văn Tuyển", snap.byRecruiter.first().display)
        assertEquals(60.0f, snap.byRecruiter.first().sharePct, 0.01f)
        assertEquals(2, snap.byEmployment.size)
        assertEquals(1, snap.byProvider.size)

        // 3. buildTeamReportFromWorkerRows builds Web-BI team report from server-authorized scope=team rows
        val catalogMap = mapOf(
            "rec_a" to CatalogRecruiterOption(
                recruiterId = "rec_a",
                displayName = "Vinh NT",
                personnelCode = "HRP01",
                providerType = "hrp",
                vendorId = null,
                teamId = "11111111-2222-4333-8444-555555555555",
                teamDisplayName = "Team Vinh",
                label = "Vinh NT (HRP01)"
            ),
            "rec_b" to CatalogRecruiterOption(
                recruiterId = "rec_b",
                displayName = "Vendor Cộng Tác",
                personnelCode = "VND02",
                providerType = "vendor",
                vendorId = "vnd_1",
                teamId = "11111111-2222-4333-8444-555555555555",
                teamDisplayName = "Team Vinh",
                label = "Vendor Cộng Tác (VND02)"
            )
        )
        val workerRows = listOf(
            WorkerProfileEntity(
                cacheKey = "team:e1",
                scopeKey = "team",
                entryId = "e1",
                entryVersion = 1,
                submissionState = "SUBMITTED",
                employeeCode = "HRP1001",
                displayName = "Công Nhân 1",
                projectId = "p1",
                projectDisplay = "Goertek Bắc Ninh",
                firstWorkDate = "2026-10-15",
                laborType = "SEASONAL",
                laborTypeDisplay = "Thời vụ",
                employmentStatus = "ON",
                recruiterId = "rec_a",
                recruiterDisplay = "Vinh NT",
                paymentSummary = "",
                pendingRequestSummary = "",
                lastDecisionSummary = "",
                isProjectManager = false,
                canView = true,
                canProposeChange = true,
                canDirectEdit = false
            ),
            WorkerProfileEntity(
                cacheKey = "team:e2",
                scopeKey = "team",
                entryId = "e2",
                entryVersion = 1,
                submissionState = "SUBMITTED",
                employeeCode = "HRP1002",
                displayName = "Công Nhân 2",
                projectId = "p1",
                projectDisplay = "Goertek Bắc Ninh",
                firstWorkDate = "2026-10-15",
                laborType = "OFFICIAL",
                laborTypeDisplay = "Chính thức",
                employmentStatus = "OFF",
                recruiterId = "rec_b",
                recruiterDisplay = "Vendor Cộng Tác",
                paymentSummary = "",
                pendingRequestSummary = "",
                lastDecisionSummary = "",
                isProjectManager = false,
                canView = true,
                canProposeChange = false,
                canDirectEdit = false
            )
        )
        val built = WorkReportsContract.buildTeamReportFromWorkerRows(
            bucketKey = "grant:11111111-2222-4333-8444-555555555555",
            bucketDisplay = "Team Vinh",
            rows = workerRows,
            recruiterCatalogById = catalogMap
        )
        assertEquals(2, built.totalRecruited)
        assertEquals(1, built.workingCount)
        assertEquals(0, built.waitingCount)
        assertEquals(1, built.leftCount)
        assertEquals(2, built.activeRecruiterCount)
        assertEquals(1, built.activeProjectCount)
        assertEquals(1, built.activeHiringDays)
        assertEquals(2.0f, built.avgPerActiveDay!!, 0.01f)
        assertEquals(2, built.byProvider.size)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `camera jpeg with OEM MotionPhoto trailer and oversized dimensions is normalized to strict backend-safe JPEG`() = runBlocking {
        val repo = createRepository()
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = {}
        )

        // Simulate a 2400x1800 OEM camera photo saved directly inside `attendance_photos`
        // with trailing Samsung MotionPhoto `SEFH...SEFT` + `<svg>` bytes appended after 0xFF 0xD9
        val outboxDir = File(context.filesDir, "attendance_photos").apply { mkdirs() }
        val cameraCaptureFile = File(outboxDir, "checkin_oem_${System.nanoTime()}.jpg")
        val bmp = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(34, 85, 136))
        val jpegOut = java.io.ByteArrayOutputStream()
        assertTrue(bmp.compress(Bitmap.CompressFormat.JPEG, 92, jpegOut))
        bmp.recycle()

        val rawJpegBytes = jpegOut.toByteArray()
        val oemTrailer = "\u0000MotionPhoto_Data<svg>SEFH\u0001\u0002SEFT".toByteArray(Charsets.ISO_8859_1)
        val rawCameraWithTrailer = rawJpegBytes + oemTrailer
        assertFalse(
            "Unprocessed OEM camera bytes with trailing SEFT/<svg> would fail backend PHOTO_INVALID",
            AttendanceContract.isStrictBackendSafeJpeg(rawCameraWithTrailer)
        )
        cameraCaptureFile.writeBytes(rawCameraWithTrailer)

        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "ok": true,
                  "checkin": {
                    "checkin_id": "eeee9999-eeee-4888-8777-ffff66665555",
                    "business_date": "2026-10-24",
                    "sequence_in_day": 2,
                    "daily_count": 2,
                    "captured_at": "2026-10-23T18:21:37.318Z",
                    "received_at": "2026-10-23T18:21:38.000Z",
                    "time_source": "DEVICE_CLOCK_UNVERIFIED",
                    "replayed": false
                  }
                }
                """.trimIndent()
            )
        )

        val outcome = service.submitCheckIn(
            actor = testActor,
            latitude = 21.27250,
            longitude = 105.65334,
            accuracyMeters = 13.0f,
            addressHint = "Bình Nguyên, Phú Thọ",
            sourcePhotoFile = cameraCaptureFile,
            sourcePhotoUri = "content://test/oem_checkin.jpg",
            note = ""
        )

        assertTrue(outcome.isSynced)
        val persistedBytes = File(outcome.record.photoFilePath).readBytes()
        assertTrue(
            "Normalized photo persisted in outbox must strictly start with FF D8 FF, end with FF D9, and contain zero OEM trailer/markup",
            AttendanceContract.isStrictBackendSafeJpeg(persistedBytes)
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(persistedBytes, 0, persistedBytes.size, bounds)
        assertTrue(bounds.outWidth in 1..AttendanceContract.MAX_ATTENDANCE_PHOTO_DIMENSION_PX)
        assertTrue(bounds.outHeight in 1..AttendanceContract.MAX_ATTENDANCE_PHOTO_DIMENSION_PX)

        val expectedNormalizedSha = MiniBiRepository.computeSha256HexStrict(File(outcome.record.photoFilePath))
        assertEquals(expectedNormalizedSha, outcome.record.photoSha256)
        val recordedReq = server.takeRequest()
        assertTrue(recordedReq.body.readUtf8().contains("\"photo_sha256\":\"$expectedNormalizedSha\""))
    }

    @Test
    fun `attendance 422 and 413 error codes map to friendly Vietnamese messages reminding user to retry`() = runBlocking {
        val repo = createRepository()
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = {}
        )

        val errorCases = listOf(
            Triple(422, """{"ok":false,"code":"PHOTO_INVALID"}""", "PHOTO_INVALID"),
            Triple(422, """{"ok":false,"error":{"code":"ATTENDANCE_TIME_MISMATCH"}}""", "ATTENDANCE_TIME_MISMATCH"),
            Triple(413, """{"ok":false,"code":"BODY_TOO_LARGE"}""", "BODY_TOO_LARGE"),
            Triple(422, """{"ok":false,"code":"UNMAPPED_FUTURE_CODE"}""", "UNMAPPED_FUTURE_CODE")
        )

        for ((status, body, expectedCode) in errorCases) {
            server.enqueue(MockResponse().setResponseCode(status).setBody(body))
            val res = service.submitCheckIn(
                actor = testActor,
                latitude = 21.2725,
                longitude = 105.6533,
                accuracyMeters = 12f,
                addressHint = "Phú Thọ",
                sourcePhotoFile = createValidPhotoFile("err_${expectedCode}.jpg"),
                sourcePhotoUri = null,
                note = ""
            )
            assertFalse(res.isSynced)
            assertEquals(AttendanceOutboxStatus.REJECTED, res.record.syncStatus)
            assertEquals(expectedCode, res.record.backendErrorCode)
            assertFalse(
                "Must never surface cryptic generic message for $expectedCode",
                res.userMessage.contains("không thỏa điều kiện nghiệp vụ")
            )
            assertTrue(
                "Friendly error message must remind user to retry (`thử lại`): ${res.userMessage}",
                res.userMessage.lowercase().contains("thử lại")
            )
        }
    }

    @Test
    fun `dashboard date presets and lowercased wf04 team report codes resolve cleanly without INVALID_FILTER`() = runBlocking {
        // 1. Fixed epoch: 2026-10-24T01:30:00Z -> Saturday 2026-10-24 08:30:00 in Asia/Ho_Chi_Minh
        val fixedEpochMs = 1_792_805_400_000L
        assertEquals(
            null to null,
            WorkReportsContract.resolveDatePresetRange(DashboardDatePreset.ALL_TIME, fixedEpochMs)
        )
        assertEquals(
            "2026-10-19" to "2026-10-24",
            WorkReportsContract.resolveDatePresetRange(DashboardDatePreset.THIS_WEEK, fixedEpochMs)
        )
        assertEquals(
            "2026-10-01" to "2026-10-24",
            WorkReportsContract.resolveDatePresetRange(DashboardDatePreset.THIS_MONTH, fixedEpochMs)
        )
        assertEquals(
            "2026-09-01" to "2026-09-30",
            WorkReportsContract.resolveDatePresetRange(DashboardDatePreset.LAST_MONTH, fixedEpochMs)
        )

        val repo = createRepository()

        // 2. fetchAdminTeamCodeMap parses canonical team codes from /api/admin/catalog/teams
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "ok": true,
                      "list": {
                        "authorization_date": "2026-10-24",
                        "include_inactive": true,
                        "search": "",
                        "page": 1,
                        "page_size": 100,
                        "total": 2,
                        "teams": [
                          {"team_id": "t1", "code": "TEAM_1", "display_name": "Team 1", "active": true, "version": 1, "revision_count": 1},
                          {"team_id": "t5", "code": "TEAM_5", "display_name": "Team 5", "active": true, "version": 1, "revision_count": 1}
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
        val codeMap = repo.workReportsAndReportingService.fetchAdminTeamCodeMap()
        assertEquals("TEAM_1", codeMap["team_1"])
        assertEquals("TEAM_5", codeMap["display:team 5"])
        assertEquals("__system_vendor__", codeMap["__system_vendor__"])
        assertEquals(
            "/api/admin/catalog/teams?include_inactive=true&page=1&page_size=100",
            server.takeRequest().path
        )

        // 3. Read-only BoD without preloaded catalog map calling fetchTeamReport("team_1"):
        //    First attempt ("team_1") -> 400 INVALID_FILTER; second attempt ("TEAM_1") -> 200 OK
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"ok":false,"code":"INVALID_FILTER"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "bucket": {"key": "team.VEVBTV8x", "display": "Team 1"},
                      "report": {"recruitedTotal": 14, "byDate": {"2026-10-20": 14}},
                      "detail": {"total": 14, "rows": [], "nextCursor": null}
                    }
                    """.trimIndent()
                )
        )
        val resolvedSnap = repo.workReportsAndReportingService.fetchTeamReport(
            bucketKeyOrTeamCode = "team_1",
            knownBucketHint = BiBucketItem(
                key = "team_1",
                display = "Team 1",
                count = 271,
                sharePct = 15.0f,
                onCount = 220,
                unconfirmedCount = 11,
                offCount = 40
            )
        )
        assertEquals("team.VEVBTV8x", resolvedSnap.bucketKey)
        assertEquals("Team 1", resolvedSnap.bucketDisplay)
        assertEquals(14, resolvedSnap.totalRecruited)
        assertEquals(220, resolvedSnap.workingCount)
        assertEquals("/api/reporting/team-report?bucket_key=team.dGVhbV8x&limit=25", server.takeRequest().path)
        assertEquals("/api/reporting/team-report?bucket_key=team.VEVBTV8x&limit=25", server.takeRequest().path)

        // 4. Date-filtered team with 0 new hires in [from, to] returns graceful 0-recruited snapshot instead of INVALID_FILTER
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"ok":false,"code":"INVALID_FILTER"}""")
        )
        val zeroPeriodSnap = repo.workReportsAndReportingService.fetchTeamReport(
            bucketKeyOrTeamCode = "team_5",
            fromDate = "2026-10-19",
            toDate = "2026-10-24",
            knownTeamCodeMap = codeMap,
            knownBucketHint = BiBucketItem(
                key = "TEAM_5",
                display = "Team 5",
                count = 70,
                sharePct = 3.9f,
                onCount = 58,
                unconfirmedCount = 2,
                offCount = 10
            )
        )
        assertEquals("team.VEVBTV81", zeroPeriodSnap.bucketKey)
        assertEquals("Team 5", zeroPeriodSnap.bucketDisplay)
        assertEquals(0, zeroPeriodSnap.totalRecruited)
        assertEquals(58, zeroPeriodSnap.workingCount)
        assertEquals(2, zeroPeriodSnap.waitingCount)
        assertEquals(10, zeroPeriodSnap.leftCount)
        assertEquals(
            "/api/reporting/team-report?bucket_key=team.VEVBTV81&limit=25&from=2026-10-19&to=2026-10-24",
            server.takeRequest().path
        )
    }

    @Test
    fun `server attendance history sync restores checkins into empty Room DB after reinstall and deduplicates`() = runBlocking {
        val repo = createRepository()
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = {}
        )
        val reportPayload = """
            {
              "ok": true,
              "report": {
                "summary": {"total_checkins": 2, "distinct_employees": 1, "unverified_time_count": 0},
                "pagination": {"page": 1, "page_size": 100, "total_items": 2, "total_pages": 1, "has_more": false},
                "items": [
                  {
                    "checkin_id": "aaaa1111-bbbb-4222-8333-cccc44445555",
                    "employee_id": "11111111-2222-4333-8444-555555555555",
                    "employee_display_name": "Quản trị viên BI",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 2,
                    "captured_at": "2026-10-23T10:15:00.000Z",
                    "received_at": "2026-10-23T10:15:01.000Z",
                    "time_source": "SERVER_ANCHORED",
                    "latitude": null,
                    "longitude": null,
                    "accuracy_meters": null,
                    "address_hint": null,
                    "note": null
                  },
                  {
                    "checkin_id": "aaaa1111-bbbb-4222-8333-cccc44446666",
                    "employee_id": "11111111-2222-4333-8444-555555555555",
                    "employee_display_name": "Quản trị viên BI",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 1,
                    "captured_at": "2026-10-23T01:05:00.000Z",
                    "received_at": "2026-10-23T01:05:02.000Z",
                    "time_source": "SERVER_ANCHORED",
                    "latitude": 21.02851,
                    "longitude": 105.85417,
                    "accuracy_meters": 8.5,
                    "address_hint": "Hoàn Kiếm, Hà Nội",
                    "note": "Ca sáng"
                  }
                ]
              }
            }
        """.trimIndent()

        // A regular team actor can restore only their own history through the self-scoped API.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(reportPayload)
        )
        val inserted = service.syncServerHistoryForUser(testActor, "2026-10-24", daysBack = 30)
        assertEquals(2, inserted)
        val firstReq = server.takeRequest()
        assertEquals(
            "/api/attendance/history?from_date=2026-09-24&to_date=2026-10-24&page=1&page_size=100",
            firstReq.path
        )

        val restored = database.miniBiDao().observeAttendanceForUser(testActor.appUserId).first()
        assertEquals(2, restored.size)
        val latest = restored.first { it.serverSequenceInDay == 2 }
        assertEquals(AttendanceOutboxStatus.SYNCED, latest.syncStatus)
        assertEquals(2, latest.serverDailyCount)
        assertTrue(latest.photoUri.endsWith("/api/attendance/checkins/aaaa1111-bbbb-4222-8333-cccc44445555/photo"))
        assertEquals("", latest.addressHint)
        assertEquals("", latest.note)

        val earliest = restored.first { it.serverSequenceInDay == 1 }
        assertEquals("Hoàn Kiếm, Hà Nội", earliest.addressHint)
        assertEquals("Ca sáng", earliest.note)
        assertEquals(2, earliest.serverDailyCount)

        // 3. Re-syncing is strictly idempotent by serverCheckinId
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(reportPayload)
        )
        val secondInserted = service.syncServerHistoryForUser(testActor, "2026-10-24", daysBack = 30)
        assertEquals(0, secondInserted)
        assertEquals(2, database.miniBiDao().observeAttendanceForUser(testActor.appUserId).first().size)
    }

    @Test
    fun `work report obligations parse is_overdue with backward compatibility and classify active vs history tabs without locking REJECTED past due_at`() = runBlocking {
        val repo = createRepository()
        val templateId = "dddd1111-eeee-4222-8333-ffff44445555"
        val idOverdueNotStarted = "11111111-bbbb-4222-8333-cccc44440001"
        val idOverdueDraft = "11111111-bbbb-4222-8333-cccc44440002"
        val idRejectedPastDue = "11111111-bbbb-4222-8333-cccc44440003"
        val idLegacyNoFlag = "11111111-bbbb-4222-8333-cccc44440004"

        val schemaJson = """
            {
              "contract_version": "work-report-schema/1.0",
              "sections": [{"section_key": "kpi", "label": "KPI", "sort_order": 1}],
              "fields": [{"field_key": "calls_made", "section_key": "kpi", "field_type": "integer", "label": "Số cuộc gọi", "required": true, "sort_order": 1}]
            }
        """.trimIndent()

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "data": [
                    {
                      "obligation_id": "$idOverdueNotStarted",
                      "template_id": "$templateId",
                      "template_code": "DAILY_REC",
                      "template_name": "Báo cáo ngày",
                      "period_type": "DAILY",
                      "period_key": "2026-10-20",
                      "period_start_date": "2026-10-20",
                      "period_end_date": "2026-10-20",
                      "due_at": "2026-10-20T17:00:00Z",
                      "is_mandatory": true,
                      "obligation_status": "ACTIVE",
                      "is_overdue": true,
                      "submission_id": null,
                      "submission_state": "NOT_STARTED",
                      "submission_version": null,
                      "version": 1,
                      "schema_json": $schemaJson
                    },
                    {
                      "obligation_id": "$idOverdueDraft",
                      "template_id": "$templateId",
                      "template_code": "DAILY_REC",
                      "template_name": "Báo cáo ngày",
                      "period_type": "DAILY",
                      "period_key": "2026-10-21",
                      "period_start_date": "2026-10-21",
                      "period_end_date": "2026-10-21",
                      "due_at": "2026-10-21T17:00:00Z",
                      "is_mandatory": true,
                      "obligation_status": "ACTIVE",
                      "is_overdue": true,
                      "submission_id": "99991111-2222-4333-8444-555566660002",
                      "submission_state": "DRAFT",
                      "submission_version": 1,
                      "version": 1,
                      "schema_json": $schemaJson
                    },
                    {
                      "obligation_id": "$idRejectedPastDue",
                      "template_id": "$templateId",
                      "template_code": "DAILY_REC",
                      "template_name": "Báo cáo ngày",
                      "period_type": "DAILY",
                      "period_key": "2020-01-01",
                      "period_start_date": "2020-01-01",
                      "period_end_date": "2020-01-01",
                      "due_at": "2020-01-01T00:00:00Z",
                      "is_mandatory": true,
                      "obligation_status": "ACTIVE",
                      "is_overdue": false,
                      "submission_id": "99991111-2222-4333-8444-555566660003",
                      "submission_state": "REJECTED",
                      "submission_version": 2,
                      "version": 1,
                      "schema_json": $schemaJson
                    },
                    {
                      "obligation_id": "$idLegacyNoFlag",
                      "template_id": "$templateId",
                      "template_code": "DAILY_REC",
                      "template_name": "Báo cáo ngày",
                      "period_type": "DAILY",
                      "period_key": "2026-10-25",
                      "period_start_date": "2026-10-25",
                      "period_end_date": "2026-10-25",
                      "due_at": "2026-10-25T17:00:00Z",
                      "is_mandatory": true,
                      "obligation_status": "ACTIVE",
                      "submission_id": null,
                      "submission_state": "NOT_STARTED",
                      "submission_version": null,
                      "version": 1,
                      "schema_json": $schemaJson
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val obligations = repo.workReportsAndReportingService.fetchMyObligations(status = null)
        assertEquals(4, obligations.size)

        val overdueNotStarted = obligations.first { it.obligationId == idOverdueNotStarted }
        val overdueDraft = obligations.first { it.obligationId == idOverdueDraft }
        val rejectedPastDue = obligations.first { it.obligationId == idRejectedPastDue }
        val legacyNoFlag = obligations.first { it.obligationId == idLegacyNoFlag }

        assertTrue(overdueNotStarted.isOverdue)
        assertFalse(WorkReportsContract.isActionableObligation(overdueNotStarted))
        assertTrue(WorkReportsContract.isHistoryObligation(overdueNotStarted))
        assertFalse(WorkReportsContract.isEditableObligation(overdueNotStarted))
        assertEquals("Quá hạn · Chưa nộp", WorkReportsContract.obligationBadgeDisplayLabel(overdueNotStarted))

        assertTrue(overdueDraft.isOverdue)
        assertFalse(WorkReportsContract.isActionableObligation(overdueDraft))
        assertTrue(WorkReportsContract.isHistoryObligation(overdueDraft))
        assertFalse(WorkReportsContract.isEditableObligation(overdueDraft))
        assertEquals("Quá hạn · Bản nháp chưa nộp", WorkReportsContract.obligationBadgeDisplayLabel(overdueDraft))

        // REJECTED with due_at in 2020 but server is_overdue=false must remain actionable & editable
        assertFalse(rejectedPastDue.isOverdue)
        assertTrue(WorkReportsContract.isActionableObligation(rejectedPastDue))
        assertTrue(WorkReportsContract.isHistoryObligation(rejectedPastDue))
        assertTrue(WorkReportsContract.isEditableObligation(rejectedPastDue))
        assertEquals("Cần sửa lại", WorkReportsContract.obligationBadgeDisplayLabel(rejectedPastDue))

        // Legacy backend without is_overdue field defaults to false and stays actionable
        assertFalse(legacyNoFlag.isOverdue)
        assertTrue(WorkReportsContract.isActionableObligation(legacyNoFlag))
        assertFalse(WorkReportsContract.isHistoryObligation(legacyNoFlag))
        assertTrue(WorkReportsContract.isEditableObligation(legacyNoFlag))
        assertEquals("Chưa làm", WorkReportsContract.obligationBadgeDisplayLabel(legacyNoFlag))

        val activeIds = obligations.filter { WorkReportsContract.isActionableObligation(it) }.map { it.obligationId }
        val historyIds = obligations.filter { WorkReportsContract.isHistoryObligation(it) }.map { it.obligationId }
        assertEquals(listOf(idRejectedPastDue, idLegacyNoFlag), activeIds)
        assertEquals(listOf(idOverdueNotStarted, idOverdueDraft, idRejectedPastDue), historyIds)
    }

    @Test
    fun `work report 409 WORK_REPORT_DEADLINE_PASSED maps distinctly from version conflict and locks form while reloading list`() = runBlocking {
        val deadlineErr = com.example.data.BackendHttpClient.mapHttpError(
            statusCode = 409,
            backendCode = "WORK_REPORT_DEADLINE_PASSED",
            backendMessage = "Work report deadline has passed"
        )
        assertEquals(com.example.data.BackendFailureKind.CONFLICT, deadlineErr.kind)
        assertEquals(409, deadlineErr.statusCode)
        assertEquals("WORK_REPORT_DEADLINE_PASSED", deadlineErr.backendCode)
        assertEquals("Đã quá hạn nộp báo cáo.", deadlineErr.message)

        val versionErr = com.example.data.BackendHttpClient.mapHttpError(
            statusCode = 409,
            backendCode = "WORK_REPORT_VERSION_CONFLICT",
            backendMessage = "Version conflict"
        )
        assertEquals(com.example.data.BackendFailureKind.CONFLICT, versionErr.kind)
        assertEquals("WORK_REPORT_VERSION_CONFLICT", versionErr.backendCode)
        assertFalse(versionErr.message.contains("Đã quá hạn nộp báo cáo."))

        val obligationId = "22222222-bbbb-4222-8333-cccc44440099"
        val legacyObligationId = "22222222-bbbb-4222-8333-cccc44440088"
        val templateId = "dddd1111-eeee-4222-8333-ffff44445555"
        val schemaJson = """
            {
              "contract_version": "work-report-schema/1.0",
              "sections": [{"section_key": "kpi", "label": "KPI", "sort_order": 1}],
              "fields": [{"field_key": "calls_made", "section_key": "kpi", "field_type": "integer", "label": "Số cuộc gọi", "required": true, "sort_order": 1}]
            }
        """.trimIndent()

        var returnVersionConflict = false
        var detailVersion = 1
        val recordedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                recordedPaths.add("${request.method} $path")
                return when (path) {
                    "/api/work-reports/obligations/$obligationId" -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "data": {
                                "obligation": {
                                  "obligation_id": "$obligationId",
                                  "template_id": "$templateId",
                                  "period_type": "DAILY",
                                  "period_key": "2026-10-24",
                                  "period_start_date": "2026-10-24",
                                  "period_end_date": "2026-10-24",
                                  "due_at": "2026-10-24T17:00:00Z",
                                  "is_mandatory": true,
                                  "obligation_status": "ACTIVE",
                                  "is_overdue": false,
                                  "version": 1
                                },
                                "template": {
                                  "template_code": "DAILY_REC",
                                  "display_name": "Báo cáo ngày",
                                  "schema_json": $schemaJson
                                },
                                "submission": {
                                  "submission_id": "99991111-2222-4333-8444-555566660099",
                                  "state": "DRAFT",
                                  "version": $detailVersion,
                                  "draft_payload_json": {"calls_made": 12}
                                },
                                "revisions": [],
                                "latest_review_event": null
                              }
                            }
                            """.trimIndent()
                        )
                    "/api/work-reports/submissions/$obligationId/draft",
                    "/api/work-reports/submissions/$obligationId/submit" -> {
                        val code = if (returnVersionConflict) "WORK_REPORT_VERSION_CONFLICT" else "WORK_REPORT_DEADLINE_PASSED"
                        MockResponse()
                            .setResponseCode(409)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"ok":false,"code":"$code","message":"Conflict"}""")
                    }
                    "/api/work-reports/submissions/$legacyObligationId/draft" -> MockResponse()
                        .setResponseCode(409)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"WORK_REPORT_DEADLINE_PASSED","message":"Deadline passed"}""")
                    "/api/work-reports/obligations" -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "data": [
                                {
                                  "obligation_id": "$obligationId",
                                  "template_id": "$templateId",
                                  "template_code": "DAILY_REC",
                                  "template_name": "Báo cáo ngày",
                                  "period_type": "DAILY",
                                  "period_key": "2026-10-24",
                                  "period_start_date": "2026-10-24",
                                  "period_end_date": "2026-10-24",
                                  "due_at": "2026-10-24T17:00:00Z",
                                  "is_mandatory": true,
                                  "obligation_status": "ACTIVE",
                                  "is_overdue": true,
                                  "submission_id": "99991111-2222-4333-8444-555566660099",
                                  "submission_state": "DRAFT",
                                  "submission_version": 1,
                                  "version": 1,
                                  "schema_json": $schemaJson
                                },
                                {
                                  "obligation_id": "$legacyObligationId",
                                  "template_id": "$templateId",
                                  "template_code": "DAILY_REC",
                                  "template_name": "Báo cáo ngày cũ",
                                  "period_type": "DAILY",
                                  "period_key": "2026-10-23",
                                  "period_start_date": "2026-10-23",
                                  "period_end_date": "2026-10-23",
                                  "due_at": "2026-10-23T17:00:00Z",
                                  "is_mandatory": true,
                                  "obligation_status": "ACTIVE",
                                  "submission_id": null,
                                  "submission_state": "NOT_STARTED",
                                  "submission_version": null,
                                  "version": 1,
                                  "schema_json": $schemaJson
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"AUTH_UNAUTHENTICATED"}""")
                }
            }
        }

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = createRepository()
        val vm = com.example.ui.MiniBiViewModel(app, repo)
        try {
            val initialObligation = com.example.data.WorkReportObligationEntity(
                obligationId = obligationId,
                templateId = templateId,
                templateCode = "DAILY_REC",
                templateName = "Báo cáo ngày",
                periodType = "DAILY",
                periodKey = "2026-10-24",
                periodStartDate = "2026-10-24",
                periodEndDate = "2026-10-24",
                dueAtIso = "2026-10-24T17:00:00Z",
                isMandatory = true,
                obligationStatus = "ACTIVE",
                submissionId = "99991111-2222-4333-8444-555566660099",
                submissionState = "DRAFT",
                submissionVersion = 1,
                obligationVersion = 1,
                schemaJsonRaw = schemaJson,
                payloadJsonRaw = """{"calls_made":12}""",
                latestReviewDecision = null,
                latestReviewReason = null,
                submittedAtIso = null,
                isOverdue = false
            )
            database.miniBiDao().upsertWorkReportCache(initialObligation)

            // 1. Open obligation editor -> fetches detail
            vm.openObligationEditor(initialObligation)
            var attempts = 0
            while (!recordedPaths.contains("GET /api/work-reports/obligations/$obligationId") && attempts < 80) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertFalse(vm.editingObligation.value!!.isOverdue)
            assertTrue(WorkReportsContract.isEditableObligation(vm.editingObligation.value!!))

            // 2. Non-deadline 409 WORK_REPORT_VERSION_CONFLICT -> refetches detail, updates version, does NOT lock as overdue
            returnVersionConflict = true
            detailVersion = 2
            recordedPaths.clear()
            vm.submitWorkReportFinal(vm.editingObligation.value!!, JSONObject().put("calls_made", 14))
            attempts = 0
            while (
                (vm.workReportMutationBusy.value ||
                    !recordedPaths.contains("POST /api/work-reports/submissions/$obligationId/submit") ||
                    !recordedPaths.contains("GET /api/work-reports/obligations/$obligationId")) &&
                attempts < 100
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertFalse(recordedPaths.contains("GET /api/work-reports/obligations"))
            assertFalse(vm.statusBannerMessage.value.orEmpty().contains("Đã quá hạn nộp báo cáo."))
            assertFalse(vm.editingObligation.value!!.isOverdue)
            assertEquals(2, vm.editingObligation.value!!.submissionVersion)
            assertTrue(WorkReportsContract.isEditableObligation(vm.editingObligation.value!!))

            // 3. Submit report -> server rejects with 409 WORK_REPORT_DEADLINE_PASSED, followed by obligations list reload
            returnVersionConflict = false
            recordedPaths.clear()
            vm.submitWorkReportFinal(vm.editingObligation.value!!, JSONObject().put("calls_made", 15))
            attempts = 0
            while (
                (vm.workReportMutationBusy.value ||
                    !recordedPaths.contains("POST /api/work-reports/submissions/$obligationId/submit") ||
                    !recordedPaths.contains("GET /api/work-reports/obligations")) &&
                attempts < 100
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            assertTrue(recordedPaths.contains("POST /api/work-reports/submissions/$obligationId/submit"))
            assertTrue(recordedPaths.contains("GET /api/work-reports/obligations"))
            assertEquals("Đã quá hạn nộp báo cáo.", vm.statusBannerMessage.value)
            assertTrue(vm.statusBannerIsError.value)

            val lockedEditing = vm.editingObligation.value
            assertNotNull(lockedEditing)
            assertTrue(lockedEditing!!.isOverdue)
            assertFalse(WorkReportsContract.isEditableObligation(lockedEditing))

            val cachedAfterSubmit409 = database.miniBiDao().getCachedWorkReportsSnapshot().first { it.obligationId == obligationId }
            assertTrue(cachedAfterSubmit409.isOverdue)
            assertFalse(WorkReportsContract.isActionableObligation(cachedAfterSubmit409))
            assertTrue(WorkReportsContract.isHistoryObligation(cachedAfterSubmit409))
            assertEquals("Quá hạn · Bản nháp chưa nộp", WorkReportsContract.obligationBadgeDisplayLabel(cachedAfterSubmit409))

            // 4. Legacy backend omits is_overdue on list reload, but saveWorkReportDraft 409 WORK_REPORT_DEADLINE_PASSED still locks & moves to history
            val legacyInitial = database.miniBiDao().getCachedWorkReportsSnapshot().first { it.obligationId == legacyObligationId }
            assertFalse(legacyInitial.isOverdue)
            recordedPaths.clear()
            vm.saveWorkReportDraft(legacyInitial, JSONObject().put("calls_made", 5))
            attempts = 0
            while (
                (vm.workReportMutationBusy.value ||
                    !recordedPaths.contains("POST /api/work-reports/submissions/$legacyObligationId/draft") ||
                    !recordedPaths.contains("GET /api/work-reports/obligations")) &&
                attempts < 100
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            assertEquals("Đã quá hạn nộp báo cáo.", vm.statusBannerMessage.value)
            assertTrue(vm.editingObligation.value!!.isOverdue)
            val cachedLegacyAfterDraft409 = database.miniBiDao().getCachedWorkReportsSnapshot().first { it.obligationId == legacyObligationId }
            assertTrue(cachedLegacyAfterDraft409.isOverdue)
            assertFalse(WorkReportsContract.isActionableObligation(cachedLegacyAfterDraft409))
            assertTrue(WorkReportsContract.isHistoryObligation(cachedLegacyAfterDraft409))
            assertEquals("Quá hạn · Chưa nộp", WorkReportsContract.obligationBadgeDisplayLabel(cachedLegacyAfterDraft409))
        } finally {
            vm.cancelScopeForTest()
        }
    }

    // -------------------------------------------------------------------------
    // Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") Contract Tests
    // -------------------------------------------------------------------------
    @Test
    fun `leave contract and service parse summary, create full and half day requests, withdraw pending request, and map business errors`() = runBlocking {
        val repo = createRepository()
        val requestId = "aaaa1111-2222-4333-8444-555555555555"

        val mePayload = """
            {
              "ok": true,
              "data": {
                "as_of_date": "2026-10-27",
                "leave_year": 2026,
                "account": {
                  "app_user_id": "${testActor.appUserId}",
                  "display_name": "Nhân sự Thực Địa",
                  "login_id": "ns01",
                  "personnel_code": "NV-001",
                  "recruiter_id": "rec-01",
                  "account_enabled": true
                },
                "authority": {
                  "app_user_id": "${testActor.appUserId}",
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
                },
                "routing": {
                  "route_kind": "TEAM_LEADER",
                  "unit_kind": "RECRUITMENT",
                  "primary_approver_app_user_id": "99999999-2222-4333-8444-555555555555",
                  "team_id": "team-uuid-1",
                  "team_code": "TEAM_NORTH",
                  "team_name": "Team Miền Bắc",
                  "fallback_reason": null,
                  "profile_configured": true
                },
                "policy": {
                  "annual_base_units": 24,
                  "monthly_accrual_units": 2,
                  "default_seniority_years": 5,
                  "default_seniority_step_units": 2,
                  "carry_over_expiry_month": 3,
                  "carry_over_expiry_day": 31,
                  "carry_over_expires_after_date": "2026-03-31",
                  "row_version": 2
                },
                "balance": {
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
                    {
                      "lot_id": "lot-10",
                      "leave_year": 2026,
                      "lot_kind": "MONTHLY_ACCRUAL",
                      "accrual_month": 10,
                      "granted_units": 2,
                      "reserved_units": 0,
                      "consumed_units": 0,
                      "expired_units": 0,
                      "available_units": 2,
                      "usable_from_date": "2026-10-01",
                      "expires_after_date": null,
                      "source_ref": null
                    }
                  ]
                },
                "requests": [
                  {
                    "request_id": "$requestId",
                    "app_user_id": "${testActor.appUserId}",
                    "requester": {
                      "app_user_id": "${testActor.appUserId}",
                      "display_name": "Nhân sự Thực Địa",
                      "login_id": "ns01",
                      "personnel_code": "NV-001",
                      "recruiter_id": "rec-01",
                      "account_enabled": true
                    },
                    "status": "PENDING",
                    "pay_treatment": "PAID_LEAVE",
                    "employment_status_snapshot": "OFFICIAL",
                    "unit_kind_snapshot": "RECRUITMENT",
                    "profile_configured_at_submit": true,
                    "requested_units": 3,
                    "reason": "Việc gia đình",
                    "route_kind": "TEAM_LEADER",
                    "primary_approver_app_user_id": "99999999-2222-4333-8444-555555555555",
                    "primary_approver": null,
                    "routed_team_id": "team-uuid-1",
                    "routed_team_code": "TEAM_NORTH",
                    "routed_team_name": "Team Miền Bắc",
                    "fallback_reason": null,
                    "submitted_at": "2026-10-27T01:00:00Z",
                    "decided_at": null,
                    "decided_by_app_user_id": null,
                    "decided_by": null,
                    "decision_reason": null,
                    "cancelled_at": null,
                    "cancelled_by_app_user_id": null,
                    "cancellation_reason": null,
                    "row_version": 3,
                    "days": [
                      {
                        "request_day_id": "day-1",
                        "leave_date": "2026-10-28",
                        "session_code": "FULL",
                        "half_day_units": 2,
                        "is_active": true
                      },
                      {
                        "request_day_id": "day-2",
                        "leave_date": "2026-10-29",
                        "session_code": "AM",
                        "half_day_units": 1,
                        "is_active": true
                      }
                    ],
                    "allocations": []
                  }
                ],
                "ledger_entries": []
              }
            }
        """.trimIndent()

        // 1. Fetch personal leave summary
        server.enqueue(MockResponse().setResponseCode(200).setBody(mePayload))
        val summary = repo.leaveService.fetchMyLeaveSummary(leaveYear = 2026)
        val getMeReq = server.takeRequest()
        assertEquals("GET", getMeReq.method)
        assertEquals("/api/leave/me?year=2026", getMeReq.path)
        assertEquals(14, summary.balance.availableUnits)
        assertEquals("7 ngày", LeaveContract.formatHalfDayUnits(summary.balance.availableUnits))
        assertEquals("1,5 ngày", LeaveContract.formatHalfDayUnits(summary.requests.first().requestedUnits))

        // Tab visibility for non-approver: only CHECK_IN and LEAVE_REQUEST, NEVER LEAVE_APPROVAL
        val nonApproverTabs = LeaveContract.resolveVisibleAttendanceTabs(summary.authority, null)
        assertEquals(listOf(AttendanceAreaTab.CHECK_IN, AttendanceAreaTab.LEAVE_REQUEST), nonApproverTabs)

        // 2. Create leave request with 1 full day (FULL, ANNUAL) and 1 afternoon half-day (PM, SICK)
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"ok":true,"data":{"request_id":"$requestId","status":"PENDING","requested_units":3,"row_version":1,"replayed":false}}"""
            )
        )
        val createdId = repo.leaveService.createLeaveRequest(
            days = listOf(
                LeaveDraftDayInput(leaveDate = "2026-11-02", sessionCode = "FULL", leaveType = "ANNUAL"),
                LeaveDraftDayInput(leaveDate = "2026-11-03", sessionCode = "PM", leaveType = "SICK")
            ),
            reason = "",
            idempotencyKey = "leave-create-test-key-001"
        )
        assertEquals(requestId, createdId)
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        assertEquals("/api/leave/requests", createReq.path)
        val createBody = JSONObject(createReq.body.readUtf8())
        assertTrue(createBody.isNull("reason"))
        assertEquals("leave-create-test-key-001", createBody.getString("idempotency_key"))
        val sentDays = createBody.getJSONArray("days")
        assertEquals(2, sentDays.length())
        assertEquals("2026-11-02", sentDays.getJSONObject(0).getString("leave_date"))
        assertEquals("FULL", sentDays.getJSONObject(0).getString("session_code"))
        assertEquals("ANNUAL", sentDays.getJSONObject(0).getString("leave_type"))
        assertEquals("2026-11-03", sentDays.getJSONObject(1).getString("leave_date"))
        assertEquals("PM", sentDays.getJSONObject(1).getString("session_code"))
        assertEquals("SICK", sentDays.getJSONObject(1).getString("leave_type"))

        // 2b. Validate per-type limits, same-date split rules, and auto-allocate excess ANNUAL -> UNPAID
        val sameDateSameTypeErr = LeaveContract.validateCreateDraftInput(
            days = listOf(
                LeaveDraftDayInput(leaveDate = "2026-11-05", sessionCode = "AM", leaveType = "ANNUAL"),
                LeaveDraftDayInput(leaveDate = "2026-11-05", sessionCode = "PM", leaveType = "ANNUAL")
            ),
            reason = ""
        )
        assertEquals(
            "Ngày 2026-11-05: 2 buổi cùng loại nghỉ phải chọn 'Cả ngày' thay vì tách 2 dòng.",
            sameDateSameTypeErr
        )
        val sameDateDifferentTypeValid = LeaveContract.validateCreateDraftInput(
            days = listOf(
                LeaveDraftDayInput(leaveDate = "2026-11-05", sessionCode = "AM", leaveType = "ANNUAL"),
                LeaveDraftDayInput(leaveDate = "2026-11-05", sessionCode = "PM", leaveType = "UNPAID")
            ),
            reason = "",
            leaveYear = 2026,
            availableUnits = 1
        )
        assertNull(sameDateDifferentTypeValid)
        val allocatedSplit = LeaveContract.autoAllocateDraftDaysByBalance(
            days = listOf(LeaveDraftDayInput(leaveDate = "2026-11-06", sessionCode = "FULL", leaveType = "ANNUAL")),
            availableUnits = 1
        )
        assertEquals(2, allocatedSplit.size)
        assertEquals("AM", allocatedSplit[0].sessionCode)
        assertEquals("ANNUAL", allocatedSplit[0].leaveType)
        assertEquals("PM", allocatedSplit[1].sessionCode)
        assertEquals("UNPAID", allocatedSplit[1].leaveType)

        // 3. Withdraw pending request sends expected_row_version and idempotency_key
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"ok":true,"data":{"request_id":"$requestId","status":"WITHDRAWN","row_version":4,"replayed":false}}"""
            )
        )
        val withdrawnStatus = repo.leaveService.withdrawLeaveRequest(
            requestId = requestId,
            expectedRowVersion = 3,
            reason = "Đã sắp xếp được công việc",
            idempotencyKey = "leave-withdraw-test-key-001"
        )
        assertEquals("WITHDRAWN", withdrawnStatus)
        val withdrawReq = server.takeRequest()
        assertEquals("POST", withdrawReq.method)
        assertEquals("/api/leave/requests/$requestId/withdraw", withdrawReq.path)
        val withdrawBody = JSONObject(withdrawReq.body.readUtf8())
        assertEquals(3, withdrawBody.getInt("expected_row_version"))
        assertEquals("Đã sắp xếp được công việc", withdrawBody.getString("reason"))
        assertEquals("leave-withdraw-test-key-001", withdrawBody.getString("idempotency_key"))

        // 4. Business error mapping (e.g. 409 LEAVE_INSUFFICIENT_BALANCE)
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"ok":false,"error":{"code":"LEAVE_INSUFFICIENT_BALANCE","message":"Insufficient balance"}}"""
            )
        )
        val err = runCatching {
            repo.leaveService.createLeaveRequest(
                days = listOf(LeaveDraftDayInput(leaveDate = "2026-11-10", sessionCode = "FULL")),
                reason = "Nghỉ phép"
            )
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(err)
        assertEquals("LEAVE_INSUFFICIENT_BALANCE", err!!.backendCode)
        assertEquals(
            "Số dư Phép năm khả dụng không đủ cho các ngày đăng ký loại Phép năm. Vui lòng chuyển phần ngày vượt sang Nghỉ không lương hoặc loại nghỉ phù hợp.",
            err.message
        )
    }

    @Test
    fun `leave approval service and viewmodel enforce authority, required rejection and cancellation reasons, and reload latest state on 409 row version conflict`() = runBlocking {
        val approverActor = AppActor(
            appUserId = "99999999-2222-4333-8444-555555555555",
            displayName = "Trưởng Nhóm Tuyển Dụng",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("team", "TEAM_NORTH", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )
        val subordinateRequestId = "bbbb1111-2222-4333-8444-555555555555"
        var currentRowVersion = AtomicInteger(2)
        var currentStatus = "PENDING"
        var returnConflictOnNextDecision = false
        val recordedCalls = java.util.Collections.synchronizedList(mutableListOf<String>())

        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                recordedCalls.add("${request.method} $path")
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "actor": {
                            "app_user_id": "${approverActor.appUserId}",
                            "display_name": "${approverActor.displayName}",
                            "capabilities": ["entry_read"],
                            "scopes": [{"kind":"team","reference":"TEAM_NORTH","valid_from":"2026-01-01","valid_to":null}]
                          }
                        }
                        """.trimIndent()
                    )
                    path.startsWith("/api/leave/me") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "as_of_date": "2026-10-27",
                            "leave_year": 2026,
                            "account": {
                              "app_user_id": "${approverActor.appUserId}",
                              "display_name": "${approverActor.displayName}",
                              "account_enabled": true
                            },
                            "authority": {
                              "app_user_id": "${approverActor.appUserId}",
                              "is_shared_non_employee": false,
                              "is_full_admin": false,
                              "is_bod": false,
                              "is_hr_operator": false,
                              "is_hr_report_viewer": false,
                              "is_team_leader": true,
                              "is_department_approver": false,
                              "can_submit_request": true,
                              "can_approve_requests": true,
                              "can_view_monthly_report": false,
                              "can_manage_config": false
                            },
                            "routing": {
                              "route_kind": "ADMIN_BOD_DIRECT",
                              "unit_kind": "RECRUITMENT",
                              "profile_configured": true
                            },
                            "policy": {"annual_base_units": 24, "monthly_accrual_units": 2, "row_version": 1},
                            "balance": {
                              "leave_year": 2026,
                              "as_of_date": "2026-10-27",
                              "has_profile": true,
                              "profile_configured": true,
                              "employment_status": "OFFICIAL",
                              "unit_kind": "RECRUITMENT",
                              "available_units": 16,
                              "total_granted_units": 20,
                              "lots": []
                            },
                            "requests": [],
                            "ledger_entries": []
                          }
                        }
                        """.trimIndent()
                    )
                    path.startsWith("/api/leave/approvals/$subordinateRequestId/decision") -> {
                        if (returnConflictOnNextDecision) {
                            returnConflictOnNextDecision = false
                            currentRowVersion.set(5)
                            MockResponse().setResponseCode(409).setBody(
                                """{"ok":false,"error":{"code":"LEAVE_ROW_VERSION_CONFLICT","message":"Row version conflict"}}"""
                            )
                        } else {
                            val body = JSONObject(request.body.readUtf8())
                            val dec = body.getString("decision")
                            currentStatus = when (dec) {
                                "APPROVE" -> "APPROVED"
                                "REJECT" -> "REJECTED"
                                "CANCEL_APPROVED" -> "CANCELLED"
                                else -> currentStatus
                            }
                            val nextVer = currentRowVersion.incrementAndGet()
                            MockResponse().setResponseCode(200).setBody(
                                """{"ok":true,"data":{"request_id":"$subordinateRequestId","status":"$currentStatus","row_version":$nextVer,"replayed":false}}"""
                            )
                        }
                    }
                    path.startsWith("/api/leave/approvals") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "as_of_date": "2026-10-27",
                            "status_filter": "PENDING",
                            "total": 1,
                            "limit": 50,
                            "offset": 0,
                            "items": [
                              {
                                "request_id": "$subordinateRequestId",
                                "app_user_id": "${testActor.appUserId}",
                                "requester": {
                                  "app_user_id": "${testActor.appUserId}",
                                  "display_name": "Nhân viên cấp dưới",
                                  "personnel_code": "NV-099",
                                  "account_enabled": true
                                },
                                "status": "$currentStatus",
                                "pay_treatment": "PAID_LEAVE",
                                "employment_status_snapshot": "OFFICIAL",
                                "unit_kind_snapshot": "RECRUITMENT",
                                "profile_configured_at_submit": true,
                                "requested_units": 2,
                                "reason": "Nghỉ việc riêng",
                                "route_kind": "TEAM_LEADER",
                                "routed_team_name": "Team Miền Bắc",
                                "submitted_at": "2026-10-27T01:30:00Z",
                                "row_version": ${currentRowVersion.get()},
                                "days": [
                                  {
                                    "request_day_id": "d-1",
                                    "leave_date": "2026-10-30",
                                    "session_code": "FULL",
                                    "half_day_units": 2,
                                    "is_active": true
                                  }
                                ],
                                "allocations": []
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(200).setBody("""{"ok":true,"data":{}}""")
                }
            }
        }

        val repo = createRepository()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.MiniBiViewModel(app, repo)
        try {
            var attempts = 0
            while (vm.sessionBootstrapping.value && attempts < 100) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }

            vm.navigateTo(com.example.ui.MainDestination.ATTENDANCE)
            attempts = 0
            while ((vm.myLeaveLoading.value || vm.myLeaveSummary.value == null) && attempts < 100) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // Default tab on Attendance navigation is CHECK_IN, and approver sees all 3 tabs
            assertEquals(AttendanceAreaTab.CHECK_IN, vm.activeAttendanceTab.value)
            assertEquals(
                listOf(AttendanceAreaTab.CHECK_IN, AttendanceAreaTab.LEAVE_REQUEST, AttendanceAreaTab.LEAVE_APPROVAL),
                vm.resolveVisibleAttendanceTabs()
            )

            // Switch to LEAVE_APPROVAL tab -> loads inbox
            vm.selectAttendanceTab(AttendanceAreaTab.LEAVE_APPROVAL)
            attempts = 0
            while ((vm.leaveInboxLoading.value || vm.leaveApprovalInbox.value == null) && attempts < 100) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            val initialItem = vm.leaveApprovalInbox.value!!.items.single()
            assertEquals(2, initialItem.rowVersion)

            // Reason exceeding 2000 characters is blocked client-side before hitting network
            recordedCalls.clear()
            vm.decideLeaveRequest(initialItem, decision = "REJECT", reason = "a".repeat(2001))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals("Lý do xử lý không được vượt quá 2000 ký tự.", vm.leaveMutationError.value)
            assertTrue(recordedCalls.none { it.contains("/decision") })

            // Simulate 409 LEAVE_ROW_VERSION_CONFLICT -> ViewModel reloads latest inbox (rowVersion = 5)
            returnConflictOnNextDecision = true
            recordedCalls.clear()
            vm.decideLeaveRequest(initialItem, decision = "APPROVE", reason = null)
            attempts = 0
            while (
                (vm.leaveMutationBusy.value ||
                    vm.leaveInboxLoading.value ||
                    vm.leaveApprovalInbox.value?.items?.firstOrNull()?.rowVersion != 5) &&
                attempts < 100
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            assertEquals(
                "Dữ liệu vừa được cập nhật bởi thao tác khác. Hệ thống đã làm mới, vui lòng kiểm tra và thử lại.",
                vm.leaveMutationError.value
            )
            val refreshedAfterConflict = vm.leaveApprovalInbox.value!!.items.single()
            assertEquals(5, refreshedAfterConflict.rowVersion)

            // Approve with refreshed rowVersion = 5 succeeds -> status becomes APPROVED, rowVersion = 6
            vm.decideLeaveRequest(refreshedAfterConflict, decision = "APPROVE", reason = "Đồng ý duyệt")
            attempts = 0
            while (
                (vm.leaveMutationBusy.value ||
                    vm.leaveInboxLoading.value ||
                    vm.leaveApprovalInbox.value?.items?.firstOrNull()?.status != "APPROVED") &&
                attempts < 100
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            val approvedItem = vm.leaveApprovalInbox.value!!.items.single()
            assertEquals("APPROVED", approvedItem.status)
            assertEquals(6, approvedItem.rowVersion)

            // CANCEL_APPROVED with optional empty reason succeeds -> status becomes CANCELLED
            vm.decideLeaveRequest(approvedItem, decision = "CANCEL_APPROVED", reason = "")
            attempts = 0
            while (
                (vm.leaveMutationBusy.value ||
                    vm.leaveInboxLoading.value ||
                    vm.leaveApprovalInbox.value?.items?.firstOrNull()?.status != "CANCELLED") &&
                attempts < 100
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals("CANCELLED", vm.leaveApprovalInbox.value!!.items.single().status)
        } finally {
            vm.cancelScopeForTest()
        }
    }
}
