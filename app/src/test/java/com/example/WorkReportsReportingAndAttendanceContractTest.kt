package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActorScope
import com.example.data.AppActor
import com.example.data.AttendanceContract
import com.example.data.AttendanceOutboxService
import com.example.data.AttendanceOutboxStatus
import com.example.data.BackendApiException
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.WorkReportsContract
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
}
