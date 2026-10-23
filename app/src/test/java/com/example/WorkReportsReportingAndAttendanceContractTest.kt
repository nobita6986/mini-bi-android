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
import com.example.data.AttendanceContract
import com.example.data.AttendanceOutboxService
import com.example.data.AttendanceOutboxStatus
import com.example.data.BackendApiException
import com.example.data.BiBucketItem
import com.example.data.CatalogRecruiterOption
import com.example.data.DashboardDatePreset
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.WorkReportsContract
import com.example.data.WorkerProfileEntity
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
}
