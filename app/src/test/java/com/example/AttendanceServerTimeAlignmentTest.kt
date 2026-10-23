package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActorScope
import com.example.data.AppActor
import com.example.data.AttendanceContract
import com.example.data.AttendanceOutboxService
import com.example.data.AttendanceOutboxStatus
import com.example.data.AttendanceTimeAnchorManager
import com.example.data.AttendanceTimeSource
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.TimeAnchorInvalidReason
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttendanceServerTimeAlignmentTest {

    private lateinit var context: Context
    private lateinit var database: MiniBiDatabase
    private lateinit var server: MockWebServer
    private lateinit var cookieFile: File
    private lateinit var anchorFile: File
    private lateinit var cipher: JvmAes256GcmCookieCipher

    private val actorA = AppActor(
        appUserId = "11111111-2222-4333-8444-aaaaaaaaaaaa",
        displayName = "Nhân sự Ca Sáng",
        capabilities = listOf("entry_read"),
        scopes = listOf(ActorScope("own", "11111111-2222-4333-8444-aaaaaaaaaaaa", "2026-01-01", null)),
        selfRecruiterSuggestion = null
    )

    private val actorB = AppActor(
        appUserId = "99999999-8888-4777-8666-bbbbbbbbbbbb",
        displayName = "Nhân sự Ca Chiều",
        capabilities = listOf("entry_read"),
        scopes = listOf(ActorScope("own", "99999999-8888-4777-8666-bbbbbbbbbbbb", "2026-01-01", null)),
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
        cookieFile = File(context.cacheDir, "time_align_cookies_${System.nanoTime()}.enc")
        anchorFile = File(context.cacheDir, "time_align_anchor_${System.nanoTime()}.enc")
        cipher = JvmAes256GcmCookieCipher()
    }

    @After
    fun tearDown() {
        database.close()
        runCatching { server.shutdown() }
        runCatching { cookieFile.delete() }
        runCatching { anchorFile.delete() }
    }

    private fun createRepository(nowMillis: () -> Long = { System.currentTimeMillis() }): MiniBiRepository {
        return MiniBiRepository(
            context = context,
            database = database,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            timeAnchorStorageFile = anchorFile,
            cookieCipher = cipher,
            nowMillis = nowMillis
        )
    }

    private fun createPhotoFile(name: String = "att_${System.nanoTime()}.jpg"): File {
        val file = File(context.cacheDir, name)
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 8, 7, 6))
        return file
    }

    private fun extractMultipartMetadataJson(rawMultipartBody: String): JSONObject {
        val marker = "Content-Disposition: form-data; name=\"metadata\""
        val markerIdx = rawMultipartBody.indexOf(marker)
        assertTrue("Multipart body missing metadata part", markerIdx >= 0)
        val afterHeadersIdx = rawMultipartBody.indexOf("\r\n\r\n", markerIdx)
        assertTrue("Multipart metadata headers malformed", afterHeadersIdx >= 0)
        val contentStart = afterHeadersIdx + 4
        val contentEnd = rawMultipartBody.indexOf("\r\n--", contentStart)
        val jsonText = rawMultipartBody.substring(contentStart, contentEnd).trim()
        return JSONObject(jsonText)
    }

    // -------------------------------------------------------------------------
    // 1. Canonical GET /api/attendance/time + encrypted storage + monotonic math
    // -------------------------------------------------------------------------
    @Test
    fun `server time anchor encrypts token at rest and computes SERVER_ANCHORED time strictly from elapsedRealtime ignoring tampered phone clock`() = runBlocking {
        // Tamper phone wall clock to year 2099 to prove System.currentTimeMillis() is NEVER used when anchored
        val fakePhoneWallClockMs = AtomicLong(4_070_908_800_000L) // 2099-01-01T00:00:00Z
        val fakeElapsedRealtimeMs = AtomicLong(100_000L)
        val fakeBootCount = AtomicInteger(7)

        val repo = createRepository(nowMillis = { fakePhoneWallClockMs.get() })
        val anchorManager = AttendanceTimeAnchorManager(
            context = context,
            httpClient = repo.httpClient,
            storageFile = anchorFile,
            cipher = cipher,
            elapsedRealtimeMs = { fakeElapsedRealtimeMs.get() },
            deviceWallClockMs = { fakePhoneWallClockMs.get() },
            bootCountProvider = { fakeBootCount.get() },
            processSessionId = "proc-1"
        )
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = anchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = {}
        )

        val opaqueToken = "bi_time_tok_v1_secret_987654321"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T01:15:00.000Z",
                  "expires_at": "2026-10-24T01:15:00.000Z",
                  "time_token": "$opaqueToken"
                }
                """.trimIndent()
            )
        )

        val syncRes = anchorManager.syncTimeAnchor(appUserId = actorA.appUserId, force = true)
        assertTrue(syncRes.isSuccess)
        assertTrue(anchorManager.uiState.value.hasValidAnchor)

        val timeGetReq = server.takeRequest()
        assertEquals("GET", timeGetReq.method)
        assertEquals("/api/attendance/time", timeGetReq.path)
        assertEquals("application/json", timeGetReq.getHeader("Accept"))
        assertNull(timeGetReq.getHeader("X-Client-Platform"))

        // Verify encrypted persistence on disk: raw file must NOT contain plain-text token
        assertTrue(anchorFile.exists() && anchorFile.length() > 0L)
        val rawDiskText = anchorFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("time_token must never be stored in plain text", rawDiskText.contains(opaqueToken))

        // Advance monotonic elapsedRealtime by exactly 312,000 ms (5m 12s) while phone wall clock jumps wildly
        fakeElapsedRealtimeMs.set(412_000L)
        fakePhoneWallClockMs.set(946_684_800_000L) // 2000-01-01T00:00:00Z

        val photo = createPhotoFile()
        val expectedSha = MiniBiRepository.computeSha256HexStrict(photo)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "checkin": {
                    "checkin_id": "aaaa1111-bbbb-4222-8333-cccc44445555",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 1,
                    "daily_count": 1,
                    "captured_at": "2026-10-23T01:20:12.000Z",
                    "received_at": "2026-10-23T01:25:00.000Z",
                    "time_source": "SERVER_ANCHORED",
                    "replayed": false
                  }
                }
                """.trimIndent()
            )
        )

        val outcome = service.submitCheckIn(
            actor = actorA,
            latitude = 21.0285,
            longitude = 105.8542,
            accuracyMeters = 12.5f,
            addressHint = "Hà Nội",
            sourcePhotoFile = photo,
            sourcePhotoUri = "content://test/photo1.jpg",
            note = ""
        )

        assertTrue(outcome.isSynced)
        assertEquals(AttendanceOutboxStatus.SYNCED, outcome.record.syncStatus)
        assertEquals("2026-10-23T08:20:12.000+07:00", outcome.record.clientCapturedAtIso)
        assertEquals(AttendanceTimeSource.SERVER_ANCHORED, outcome.record.timeSource)
        assertEquals(312_000L, outcome.record.elapsedSinceAnchorMs)
        assertEquals("2026-10-23", outcome.record.serverBusinessDate)
        assertEquals(1, outcome.record.serverSequenceInDay)
        assertEquals(1, outcome.record.serverDailyCount)
        assertEquals("2026-10-23T01:20:12.000Z", outcome.record.serverCapturedAtIso)
        assertEquals("2026-10-23T01:25:00.000Z", outcome.record.serverReceivedAtIso)
        assertEquals(AttendanceTimeSource.SERVER_ANCHORED, outcome.record.serverTimeSource)
        assertEquals(false, outcome.record.serverReplayed)

        val checkInReq = server.takeRequest()
        assertEquals("POST", checkInReq.method)
        assertEquals("/api/attendance/check-in", checkInReq.path)
        assertNull(checkInReq.getHeader("X-Client-Platform"))
        assertNotNull(checkInReq.getHeader("Origin"))

        val metadata = extractMultipartMetadataJson(checkInReq.body.readUtf8())
        val actualKeys = metadata.keys().asSequence().toSet()
        assertEquals(AttendanceContract.ALLOWED_METADATA_KEYS, actualKeys)
        assertEquals("2026-10-23T08:20:12.000+07:00", metadata.getString("client_captured_at"))
        assertEquals(AttendanceTimeSource.SERVER_ANCHORED, metadata.getString("time_source"))
        assertEquals(opaqueToken, metadata.getString("time_token"))
        assertEquals(312_000L, metadata.getLong("elapsed_since_anchor_ms"))
        assertEquals(expectedSha, metadata.getString("photo_sha256"))
        assertEquals(outcome.record.idempotencyKey, metadata.getString("idempotency_key"))
    }

    // -------------------------------------------------------------------------
    // 2. Reboot detection, monotonic regression, expiry, proactive refresh, logout
    // -------------------------------------------------------------------------
    @Test
    fun `anchor invalidates on reboot BOOT_COUNT change, elapsedRealtime regression, expiry, and logout`() = runBlocking {
        val elapsedMs = AtomicLong(500_000L)
        val bootCount = AtomicInteger(12)
        val repo = createRepository()

        val manager1 = AttendanceTimeAnchorManager(
            context = context,
            httpClient = repo.httpClient,
            storageFile = anchorFile,
            cipher = cipher,
            elapsedRealtimeMs = { elapsedMs.get() },
            bootCountProvider = { bootCount.get() },
            processSessionId = "proc-alpha"
        )

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T01:00:00.000Z",
                  "expires_at": "2026-10-23T02:00:00.000Z",
                  "time_token": "tok_hourly_1"
                }
                """.trimIndent()
            )
        )
        assertTrue(manager1.syncTimeAnchor(actorA.appUserId, force = true).isSuccess)
        server.takeRequest()

        // Process restart WITHOUT reboot (same bootCount = 12, elapsed = 560_000L): anchor remains valid!
        elapsedMs.set(560_000L)
        val managerAfterAppRestart = AttendanceTimeAnchorManager(
            context = context,
            httpClient = repo.httpClient,
            storageFile = anchorFile,
            cipher = cipher,
            elapsedRealtimeMs = { elapsedMs.get() },
            bootCountProvider = { bootCount.get() },
            processSessionId = "proc-beta"
        )
        val validEval = managerAfterAppRestart.evaluateAnchor(actorA.appUserId)
        assertTrue(validEval.isValid)
        assertEquals(60_000L, validEval.elapsedSinceAnchorMs)
        assertFalse(validEval.isExpiringSoon)

        // Approaching expiry (52 minutes elapsed out of 60 minutes TTL -> 8m left <= 10m threshold): isExpiringSoon = true
        elapsedMs.set(500_000L + 52L * 60_000L)
        val expiringSoonEval = managerAfterAppRestart.evaluateAnchor(actorA.appUserId)
        assertTrue(expiringSoonEval.isValid)
        assertTrue(expiringSoonEval.isExpiringSoon)

        // Device REBOOT (bootCount increments 12 -> 13, elapsedRealtime resets to 15_000L): invalidated & purged!
        bootCount.set(13)
        elapsedMs.set(15_000L)
        val rebootedManager = AttendanceTimeAnchorManager(
            context = context,
            httpClient = repo.httpClient,
            storageFile = anchorFile,
            cipher = cipher,
            elapsedRealtimeMs = { elapsedMs.get() },
            bootCountProvider = { bootCount.get() },
            processSessionId = "proc-gamma"
        )
        val rebootEval = rebootedManager.evaluateAnchor(actorA.appUserId)
        assertFalse(rebootEval.isValid)
        assertEquals(TimeAnchorInvalidReason.DEVICE_REBOOTED, rebootEval.invalidReason)
        assertFalse("Rebooted anchor file must be deleted", anchorFile.exists())

        // Re-sync new anchor under bootCount = 13 at elapsed = 200_000L, then test monotonic regression
        elapsedMs.set(200_000L)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T03:00:00.000Z",
                  "expires_at": "2026-10-23T04:00:00.000Z",
                  "time_token": "tok_hourly_2"
                }
                """.trimIndent()
            )
        )
        assertTrue(rebootedManager.syncTimeAnchor(actorA.appUserId, force = true).isSuccess)
        server.takeRequest()

        // Monotonic regression (199_999L < 200_000L) -> invalidated!
        elapsedMs.set(199_999L)
        val regressedEval = rebootedManager.evaluateAnchor(actorA.appUserId)
        assertFalse(regressedEval.isValid)
        assertEquals(TimeAnchorInvalidReason.ELAPSED_REALTIME_REGRESSED, regressedEval.invalidReason)

        // Re-sync and test exact TTL expiration (elapsed advances by 60m -> estimatedNow == expires_at)
        elapsedMs.set(300_000L)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T03:00:00.000Z",
                  "expires_at": "2026-10-23T04:00:00.000Z",
                  "time_token": "tok_hourly_3"
                }
                """.trimIndent()
            )
        )
        assertTrue(rebootedManager.syncTimeAnchor(actorA.appUserId, force = true).isSuccess)
        server.takeRequest()

        elapsedMs.set(300_000L + 3_600_000L)
        val expiredEval = rebootedManager.evaluateAnchor(actorA.appUserId)
        assertFalse(expiredEval.isValid)
        assertEquals(TimeAnchorInvalidReason.EXPIRED, expiredEval.invalidReason)

        // Sync via repository's own manager and verify logout clears anchor
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T05:00:00.000Z",
                  "expires_at": "2026-10-24T05:00:00.000Z",
                  "time_token": "tok_repo_session"
                }
                """.trimIndent()
            )
        )
        assertTrue(repo.attendanceTimeAnchorManager.syncTimeAnchor(actorA.appUserId, force = true).isSuccess)
        server.takeRequest()
        assertTrue(repo.attendanceTimeAnchorManager.uiState.value.hasValidAnchor)

        server.enqueue(MockResponse().setResponseCode(204))
        repo.logout()
        server.takeRequest()
        assertFalse(repo.attendanceTimeAnchorManager.uiState.value.hasValidAnchor)
        assertFalse(anchorFile.exists())
    }

    // -------------------------------------------------------------------------
    // 3. Fallback when no valid anchor exists -> DEVICE_CLOCK_UNVERIFIED
    // -------------------------------------------------------------------------
    @Test
    fun `missing valid anchor stores check-in durably with DEVICE_CLOCK_UNVERIFIED and null token and elapsed`() = runBlocking {
        // 2026-10-23T09:05:00.000+07:00 == 2026-10-23T02:05:00.000Z == 1792721100000L
        val deviceClockEpochMs = 1_792_721_100_000L
        var online = false
        val repo = createRepository(nowMillis = { deviceClockEpochMs })
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { online },
            outboxWorkScheduler = {}
        )

        val queuedOutcome = service.submitCheckIn(
            actor = actorA,
            latitude = 10.7769,
            longitude = 106.7009,
            accuracyMeters = 9.0f,
            addressHint = "TP.HCM",
            sourcePhotoFile = createPhotoFile("unverified.jpg"),
            sourcePhotoUri = null,
            note = "Chưa có mạng lấy giờ server"
        )

        assertFalse(queuedOutcome.isSynced)
        assertEquals(AttendanceOutboxStatus.QUEUED, queuedOutcome.record.syncStatus)
        assertEquals(AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED, queuedOutcome.record.timeSource)
        assertNull(queuedOutcome.record.timeToken)
        assertNull(queuedOutcome.record.elapsedSinceAnchorMs)
        assertEquals("2026-10-23T09:05:00.000+07:00", queuedOutcome.record.clientCapturedAtIso)

        // Come back online and flush outbox
        online = true
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "checkin": {
                    "checkin_id": "bbbb2222-cccc-4333-8444-dddd55556666",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 1,
                    "daily_count": 1,
                    "captured_at": "2026-10-23T02:05:00.000Z",
                    "received_at": "2026-10-23T02:10:00.000Z",
                    "time_source": "DEVICE_CLOCK_UNVERIFIED",
                    "replayed": false
                  }
                }
                """.trimIndent()
            )
        )

        val summary = service.flushRetryableOutbox(appUserId = actorA.appUserId)
        assertEquals(1, summary.syncedCount)

        val req = server.takeRequest()
        val metadata = extractMultipartMetadataJson(req.body.readUtf8())
        assertEquals(AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED, metadata.getString("time_source"))
        assertTrue("time_token must be JSON null when unverified", metadata.isNull("time_token"))
        assertTrue("elapsed_since_anchor_ms must be JSON null when unverified", metadata.isNull("elapsed_since_anchor_ms"))
        assertEquals("2026-10-23T09:05:00.000+07:00", metadata.getString("client_captured_at"))
    }

    // -------------------------------------------------------------------------
    // 4. Retry immutability: idempotency_key & captured time never mutate
    // -------------------------------------------------------------------------
    @Test
    fun `retries preserve exact idempotency_key, client_captured_at, time_source, time_token, and elapsed_since_anchor_ms even after anchor refresh`() = runBlocking {
        val elapsedMs = AtomicLong(10_000L)
        val wallClockMs = AtomicLong(1_792_700_000_000L)
        val repo = createRepository(nowMillis = { wallClockMs.get() })
        val anchorManager = AttendanceTimeAnchorManager(
            context = context,
            httpClient = repo.httpClient,
            storageFile = anchorFile,
            cipher = cipher,
            elapsedRealtimeMs = { elapsedMs.get() },
            deviceWallClockMs = { wallClockMs.get() },
            bootCountProvider = { 5 },
            processSessionId = "proc-retry"
        )
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = anchorManager,
            isNetworkAvailable = { true },
            outboxWorkScheduler = {}
        )

        // Anchor #1 at elapsed = 10_000L
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T01:15:00.000Z",
                  "expires_at": "2026-10-24T01:15:00.000Z",
                  "time_token": "tok_original_capture"
                }
                """.trimIndent()
            )
        )
        assertTrue(anchorManager.syncTimeAnchor(actorA.appUserId, force = true).isSuccess)
        server.takeRequest()

        // Capture photo 312_000ms later; initial POST gets 502 Bad Gateway
        elapsedMs.set(322_000L)
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"ok":false,"code":"BAD_GATEWAY"}"""))

        val attempt1 = service.submitCheckIn(
            actor = actorA,
            latitude = 21.0285,
            longitude = 105.8542,
            accuracyMeters = 7.0f,
            addressHint = "Hà Nội",
            sourcePhotoFile = createPhotoFile("retry_imm.jpg"),
            sourcePhotoUri = null,
            note = "Ca 1"
        )
        assertEquals(AttendanceOutboxStatus.RETRYABLE_ERROR, attempt1.record.syncStatus)
        val reqAttempt1 = server.takeRequest()
        val metaAttempt1 = extractMultipartMetadataJson(reqAttempt1.body.readUtf8())

        // Later: 2 hours pass AND a brand-new server time anchor (tok_refreshed_later) is fetched!
        elapsedMs.set(7_522_000L)
        wallClockMs.set(1_792_800_000_000L)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "server_time": "2026-10-23T03:15:00.000Z",
                  "expires_at": "2026-10-24T03:15:00.000Z",
                  "time_token": "tok_refreshed_later"
                }
                """.trimIndent()
            )
        )
        assertTrue(anchorManager.syncTimeAnchor(actorA.appUserId, force = true).isSuccess)
        server.takeRequest()

        // Flush queued record: MUST NOT call /api/attendance/time and MUST send original frozen values!
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "checkin": {
                    "checkin_id": "cccc3333-dddd-4444-8555-eeee66667777",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 1,
                    "daily_count": 1,
                    "captured_at": "2026-10-23T01:20:12.000Z",
                    "received_at": "2026-10-23T03:16:00.000Z",
                    "time_source": "SERVER_ANCHORED",
                    "replayed": true
                  }
                }
                """.trimIndent()
            )
        )

        val flushSummary = service.flushRetryableOutbox(appUserId = actorA.appUserId)
        assertEquals(1, flushSummary.syncedCount)
        assertEquals("Flush must only send 1 POST /api/attendance/check-in and never call /api/attendance/time", 1, server.requestCount - 3)

        val reqRetry = server.takeRequest()
        assertEquals("/api/attendance/check-in", reqRetry.path)
        assertEquals(reqAttempt1.getHeader("Idempotency-Key"), reqRetry.getHeader("Idempotency-Key"))

        val metaRetry = extractMultipartMetadataJson(reqRetry.body.readUtf8())
        assertEquals(metaAttempt1.getString("idempotency_key"), metaRetry.getString("idempotency_key"))
        assertEquals("2026-10-23T08:20:12.000+07:00", metaRetry.getString("client_captured_at"))
        assertEquals("SERVER_ANCHORED", metaRetry.getString("time_source"))
        assertEquals("tok_original_capture", metaRetry.getString("time_token"))
        assertEquals(312_000L, metaRetry.getLong("elapsed_since_anchor_ms"))
    }

    // -------------------------------------------------------------------------
    // 5. Strict single canonical check-in response parser (no multi-field aliases)
    // -------------------------------------------------------------------------
    @Test
    fun `canonical check-in parser rejects legacy aliases and incomplete checkin payloads fail-closed`() {
        val legacyPayloads = listOf(
            // Legacy root wrapper "attendance" instead of "checkin"
            """
            {
              "ok": true,
              "attendance": {
                "checkin_id": "aaaa1111-bbbb-4222-8333-cccc44445555",
                "business_date": "2026-10-23",
                "sequence_in_day": 1,
                "daily_count": 1,
                "captured_at": "2026-10-23T01:20:12.000Z",
                "received_at": "2026-10-23T01:25:00.000Z",
                "time_source": "SERVER_ANCHORED",
                "replayed": false
              }
            }
            """.trimIndent(),
            // Legacy field name "id" instead of "checkin_id"
            """
            {
              "ok": true,
              "checkin": {
                "id": "aaaa1111-bbbb-4222-8333-cccc44445555",
                "business_date": "2026-10-23",
                "sequence_in_day": 1,
                "daily_count": 1,
                "captured_at": "2026-10-23T01:20:12.000Z",
                "received_at": "2026-10-23T01:25:00.000Z",
                "time_source": "SERVER_ANCHORED",
                "replayed": false
              }
            }
            """.trimIndent(),
            // Missing business_date / timestamps / time_source / replayed
            """
            {
              "ok": true,
              "checkin": {
                "checkin_id": "aaaa1111-bbbb-4222-8333-cccc44445555",
                "sequence_in_day": 1,
                "daily_count": 1
              }
            }
            """.trimIndent()
        )

        for (raw in legacyPayloads) {
            val err = runCatching {
                AttendanceContract.parseCheckInSuccessResponse(JSONObject(raw))
            }.exceptionOrNull() as? BackendApiException
            assertNotNull("Expected CONTRACT_INVALID for payload: $raw", err)
            assertEquals(BackendFailureKind.CONTRACT_INVALID, err!!.kind)
        }
    }

    // -------------------------------------------------------------------------
    // 6. Cross-actor isolation on logout/re-login
    // -------------------------------------------------------------------------
    @Test
    fun `logout keeps actor A outbox durable and never sends actor A check-in under actor B session`() = runBlocking {
        var online = false
        val repo = createRepository()
        val service = AttendanceOutboxService(
            context = context,
            httpClient = repo.httpClient,
            dao = database.miniBiDao(),
            timeAnchorManager = repo.attendanceTimeAnchorManager,
            isNetworkAvailable = { online },
            outboxWorkScheduler = {}
        )

        // Actor A queues offline check-in
        val actorAQueued = service.submitCheckIn(
            actor = actorA,
            latitude = 21.0285,
            longitude = 105.8542,
            accuracyMeters = 6.0f,
            addressHint = "Hà Nội",
            sourcePhotoFile = createPhotoFile("actor_a.jpg"),
            sourcePhotoUri = null,
            note = "Của Actor A"
        )
        assertEquals(AttendanceOutboxStatus.QUEUED, actorAQueued.record.syncStatus)

        // Actor A logs out
        online = true
        server.enqueue(MockResponse().setResponseCode(204))
        repo.logout()
        server.takeRequest()

        // Actor A's outbox item is preserved in Room (not lost!)
        val preserved = database.miniBiDao().getAttendanceById(actorAQueued.record.id)
        assertNotNull(preserved)
        assertEquals(AttendanceOutboxStatus.QUEUED, preserved!!.syncStatus)

        // Actor B logs in and flushes outbox: ZERO requests sent for Actor A's record!
        val beforeFlushReqCount = server.requestCount
        val actorBFlush = service.flushRetryableOutbox(appUserId = actorB.appUserId)
        assertEquals(0, actorBFlush.processedCount)
        assertEquals(0, actorBFlush.syncedCount)
        assertNull(service.syncRecordById(recordId = actorAQueued.record.id, currentActorId = actorB.appUserId))
        assertEquals(beforeFlushReqCount, server.requestCount)

        // Actor A logs back in and flushes outbox: Actor A's record now syncs cleanly!
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "checkin": {
                    "checkin_id": "eeee4444-ffff-4555-8666-111122223333",
                    "business_date": "2026-10-23",
                    "sequence_in_day": 1,
                    "daily_count": 1,
                    "captured_at": "2026-10-23T01:20:12.000Z",
                    "received_at": "2026-10-23T01:30:00.000Z",
                    "time_source": "DEVICE_CLOCK_UNVERIFIED",
                    "replayed": false
                  }
                }
                """.trimIndent()
            )
        )
        val actorAFlush = service.flushRetryableOutbox(appUserId = actorA.appUserId)
        assertEquals(1, actorAFlush.syncedCount)
        assertEquals(AttendanceOutboxStatus.SYNCED, database.miniBiDao().getAttendanceById(actorAQueued.record.id)!!.syncStatus)
    }
}
