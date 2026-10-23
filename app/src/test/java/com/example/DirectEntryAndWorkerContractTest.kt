package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.DirectEntryContract
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.NewWorkerEntryDraft
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectEntryAndWorkerContractTest {

    private lateinit var context: Context
    private lateinit var database: MiniBiDatabase
    private lateinit var server: MockWebServer
    private lateinit var cookieFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MiniBiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        cookieFile = File(context.cacheDir, "de_cookies_${System.nanoTime()}.enc")
    }

    @After
    fun tearDown() {
        database.close()
        runCatching { server.shutdown() }
        runCatching { cookieFile.delete() }
    }

    private fun createRepository(): MiniBiRepository {
        return MiniBiRepository(
            context = context,
            database = database,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
    }

    // -------------------------------------------------------------------------
    // Item 8: Full-profile v1.1 mapping giới tính / ngày / provider / status đúng
    // -------------------------------------------------------------------------
    @Test
    fun `item 8 - full-profile v1_1 maps gender date provider status and omits forbidden fields`() = runBlocking {
        val repo = createRepository()
        val recruiterUuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"

        val drafts = listOf(
            NewWorkerEntryDraft(
                localId = "row-1",
                projectId = "PRJ_SAMSUNG_01",
                projectDisplay = "Samsung Bắc Ninh",
                providerType = "HRP",
                recruiterId = recruiterUuid,
                recruiterDisplay = "Nguyễn Văn Tuyển",
                laborTypeKey = "SEASONAL_3M",
                laborTypeDisplay = "Thời vụ 3 tháng",
                firstWorkDateUi = "05/11/2026",
                employmentStatusUi = "Đang làm",
                fullName = "Phạm Văn Nam",
                genderUi = "Nam",
                birthDateUi = "19/04/1999",
                cccd = "001099012345",
                cccdIssueDateUi = "10/05/2021",
                cccdIssuePlace = "Cục CSQLHC"
            ),
            NewWorkerEntryDraft(
                localId = "row-2",
                projectId = "PRJ_CANON_02",
                providerType = "Vendor",
                recruiterId = recruiterUuid,
                laborTypeKey = "OFFICIAL",
                firstWorkDateUi = "12/11/2026",
                employmentStatusUi = "Đang chờ việc",
                fullName = "Trần Thị Nữ",
                genderUi = "Nữ"
            ),
            NewWorkerEntryDraft(
                localId = "row-3",
                projectId = "PRJ_CANON_02",
                providerType = "hrp",
                recruiterId = recruiterUuid,
                laborTypeKey = "OFFICIAL",
                firstWorkDateUi = "15/11/2026",
                employmentStatusUi = "Đang làm",
                fullName = "Lê Minh An",
                genderUi = ""
            )
        )

        // First attempt hits 503 -> sticky idempotency key must stay identical on retry!
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"ok":false,"code":"TEMP_UNAVAILABLE"}"""))
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "ok": true,
                  "submission_id": "11111111-1111-4111-8111-111111111111",
                  "state": "SUBMITTED",
                  "version": 1,
                  "entry_ids": [
                    "22222222-2222-4222-8222-222222222221",
                    "22222222-2222-4222-8222-222222222222",
                    "22222222-2222-4222-8222-222222222223"
                  ],
                  "employee_codes": ["HRP0001", "HRP0002", "HRP0003"]
                }
                """.trimIndent()
            )
        )

        val firstAttempt = runCatching { repo.directEntryService.submitFullProfileBatch(drafts) }
        assertTrue(firstAttempt.isFailure)

        val secondAttempt = repo.directEntryService.submitFullProfileBatch(drafts)
        assertEquals(3, secondAttempt.entryIds.size)

        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        assertEquals("/api/direct-entry/batches/full-profile", req1.path)
        assertEquals(req1.getHeader("Idempotency-Key"), req2.getHeader("Idempotency-Key"))
        assertNotNull(req2.getHeader("Origin"))

        val wireBody = JSONObject(req2.body.readUtf8())
        assertEquals("worker-profile/1.1", wireBody.getString("contract_version"))
        assertFalse(wireBody.has("actor"))
        assertFalse(wireBody.has("scopes"))
        assertFalse(wireBody.has("capabilities"))

        val rows = wireBody.getJSONArray("rows")
        assertEquals(3, rows.length())

        val r0 = rows.getJSONObject(0)
        assertEquals("PRJ_SAMSUNG_01", r0.getString("project_id"))
        assertEquals("2026-11-05", r0.getString("first_work_date"))
        assertEquals("hrp", r0.getString("provider_type"))
        assertEquals(recruiterUuid, r0.getString("recruiter_id"))
        assertEquals("SEASONAL_3M", r0.getString("labor_type"))
        assertEquals("ON", r0.getJSONObject("employment").getString("initial_status"))
        assertEquals("provided", r0.getJSONObject("worker").getJSONObject("gender").getString("state"))
        assertEquals("MALE", r0.getJSONObject("worker").getJSONObject("gender").getString("value"))
        assertEquals("1999-04-19", r0.getJSONObject("worker").getJSONObject("date_of_birth").getString("value"))
        assertFalse(r0.has("workerId"))
        assertFalse(r0.has("employee_code"))
        assertFalse(r0.has("optionalDetails"))

        val r1 = rows.getJSONObject(1)
        assertEquals("vendor", r1.getString("provider_type"))
        assertEquals("UNCONFIRMED", r1.getJSONObject("employment").getString("initial_status"))
        assertEquals("FEMALE", r1.getJSONObject("worker").getJSONObject("gender").getString("value"))

        val r2 = rows.getJSONObject(2)
        assertEquals("omitted", r2.getJSONObject("worker").getJSONObject("gender").getString("state"))
        assertFalse(r2.getJSONObject("worker").getJSONObject("gender").has("value"))
    }

    // -------------------------------------------------------------------------
    // Item 9: Upload bốn document types và dùng đúng stage ID + DELETE stage
    // -------------------------------------------------------------------------
    @Test
    fun `item 9 - uploads all four document types to stage endpoint attaches stage IDs and deletes discarded stage`() = runBlocking {
        val repo = createRepository()
        val stageMap = linkedMapOf(
            "WORKER_PORTRAIT" to ("11111111-aaaa-4111-8111-111111111111" to "image/jpeg"),
            "CCCD_FRONT" to ("22222222-bbbb-4222-8222-222222222222" to "image/png"),
            "CCCD_BACK" to ("33333333-cccc-4333-8333-333333333333" to "image/jpeg"),
            "EMPLOYMENT_CONTRACT" to ("44444444-dddd-4444-8444-444444444444" to "application/pdf")
        )

        val uploadedRefs = stageMap.map { (docType, pair) ->
            val (stageId, mime) = pair
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "ok": true,
                      "stage_id": "$stageId",
                      "document_type": "$docType",
                      "mime_type": "$mime",
                      "size_bytes": 128,
                      "status": "STAGED"
                    }
                    """.trimIndent()
                )
            )
            val staged = repo.directEntryService.uploadStageBytes(
                documentType = docType,
                fileBytes = ByteArray(128) { 0x42 },
                fileName = "${docType.lowercase()}.bin",
                mimeType = mime
            )
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/direct-entry/worker-portrait-stages", req.path)
            assertNotNull(req.getHeader("Idempotency-Key"))
            assertNotNull(req.getHeader("Origin"))
            val bodyText = req.body.readUtf8()
            assertTrue(bodyText.contains("name=\"document_type\""))
            assertTrue(bodyText.contains(docType))
            assertTrue(bodyText.contains("name=\"file\""))
            docType to staged
        }.toMap()

        val draft = NewWorkerEntryDraft(
            localId = "staged-row",
            projectId = "PRJ_01",
            providerType = "hrp",
            recruiterId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            laborTypeKey = "OFFICIAL",
            firstWorkDateUi = "20/10/2026",
            employmentStatusUi = "Đang làm",
            fullName = "Hoàng Văn Tài",
            portraitStage = uploadedRefs["WORKER_PORTRAIT"],
            cccdFrontStage = uploadedRefs["CCCD_FRONT"],
            cccdBackStage = uploadedRefs["CCCD_BACK"],
            contractStage = uploadedRefs["EMPLOYMENT_CONTRACT"]
        )

        val builtPayload = DirectEntryContract.buildFullProfileV11Payload(listOf(draft))
        val rowJson = builtPayload.getJSONArray("rows").getJSONObject(0)
        assertEquals("11111111-aaaa-4111-8111-111111111111", rowJson.getString("portrait_stage_id"))
        val docStages = rowJson.getJSONObject("document_stage_ids")
        assertEquals("22222222-bbbb-4222-8222-222222222222", docStages.getString("CCCD_FRONT"))
        assertEquals("33333333-cccc-4333-8333-333333333333", docStages.getString("CCCD_BACK"))
        assertEquals("44444444-dddd-4444-8444-444444444444", docStages.getString("EMPLOYMENT_CONTRACT"))

        // Discard stage -> DELETE /api/direct-entry/worker-portrait-stages
        server.enqueue(MockResponse().setResponseCode(204))
        repo.directEntryService.deleteStage("11111111-aaaa-4111-8111-111111111111")
        val delReq = server.takeRequest()
        assertEquals("DELETE", delReq.method)
        assertEquals("/api/direct-entry/worker-portrait-stages", delReq.path)
        assertEquals("11111111-aaaa-4111-8111-111111111111", JSONObject(delReq.body.readUtf8()).getString("stage_id"))
    }

    // -------------------------------------------------------------------------
    // Item 10: uploader gọi submissions, không gọi workers
    // -------------------------------------------------------------------------
    @Test
    fun `item 10 - uploader scope is rejected on workers endpoint and uses GET direct-entry submissions`() = runBlocking {
        val repo = createRepository()

        // Calling listWorkers("uploader") is strictly forbidden fail-closed
        val invalidScopeErr = runCatching {
            repo.directEntryService.listWorkers(scope = "uploader")
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(invalidScopeErr)
        assertEquals("WORKER_SCOPE_INVALID", invalidScopeErr!!.backendCode)
        assertEquals(0, server.requestCount)

        // Own submissions uses GET /api/direct-entry/submissions
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "items": [
                    {
                      "submission_id": "99999999-8888-4777-8666-555555555555",
                      "state": "SUBMITTED",
                      "version": 2,
                      "entry_count": 4,
                      "created_at": "2026-10-20T08:00:00Z",
                      "updated_at": "2026-10-20T08:05:00Z",
                      "submitted_at": "2026-10-20T08:05:00Z",
                      "allowed_transitions": [],
                      "project_scoped": true
                    }
                  ],
                  "page_size": 20,
                  "has_more": false,
                  "next_cursor": null
                }
                """.trimIndent()
            )
        )
        val submissionsPage = repo.directEntryService.listOwnSubmissions(pageSize = 20)
        assertEquals(1, submissionsPage.items.size)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.startsWith("/api/direct-entry/submissions"))
        assertFalse(req.path!!.contains("/api/direct-entry/workers"))
    }

    // -------------------------------------------------------------------------
    // Item 12: Worker detail dùng entryId (/api/direct-entry/entries/{entryId})
    // -------------------------------------------------------------------------
    @Test
    fun `item 12 - worker detail calls GET entries by entryId and uses backend preview download paths`() = runBlocking {
        val repo = createRepository()
        val entryId = "77777777-6666-4555-8444-333333333333"
        val docId = "88888888-7777-4666-8555-444444444444"

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "entry": {
                    "entry_id": "$entryId",
                    "submission_id": "11111111-2222-4333-8444-555555555555",
                    "version": 3,
                    "project_id": "PRJ_01",
                    "first_work_date": "2026-10-15",
                    "employee_code": "HRP9001",
                    "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                    "team_id": "TEAM_A",
                    "provider_type": "hrp",
                    "labor_type": "OFFICIAL",
                    "labor_type_display": "Chính thức",
                    "worker_details": {
                      "display_name": "Đỗ Thị Mai",
                      "gender": {"state": "provided", "value": "FEMALE"}
                    },
                    "employment_status": {
                      "status": "ON",
                      "effective_date": "2026-10-15",
                      "version": 1
                    },
                    "documents": [
                      {
                        "document_id": "$docId",
                        "document_type": "CCCD_FRONT",
                        "version": 1,
                        "size_bytes": 2048,
                        "mime_type": "image/jpeg",
                        "upload_status": "READY",
                        "scan_status": "CLEAN",
                        "validation_status": "VALID"
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val detail = repo.directEntryService.getEntryDetail(entryId)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/direct-entry/entries/$entryId", req.path)
        assertFalse(req.path!!.contains("/workers/"))
        assertEquals("HRP9001", detail.employeeCode)
        assertEquals("/api/direct-entry/entries/$entryId/documents/$docId/cccd-preview", detail.documents[0].previewPath)
        assertEquals("/api/direct-entry/entries/$entryId/documents/$docId/download", detail.documents[0].downloadPath)
    }

    // -------------------------------------------------------------------------
    // Item 13: Change request dùng canonical target kinds
    // -------------------------------------------------------------------------
    @Test
    fun `item 13 - change request sends canonical ENTRY_FIELD PAYMENT WORK_STATUS and rejects legacy target kinds`() = runBlocking {
        val repo = createRepository()
        val entryId = "77777777-6666-4555-8444-333333333333"

        // Legacy names must be rejected fail-closed before hitting network
        for (legacyKind in listOf("worker_info", "bank_account", "employment_status")) {
            val err = runCatching {
                repo.directEntryService.createChangeRequest(
                    entryId = entryId,
                    targetKind = legacyKind,
                    expectedVersion = 1,
                    proposal = JSONObject().put("display_name", "Test"),
                    reason = "Sửa"
                )
            }.exceptionOrNull() as? BackendApiException
            assertNotNull("Legacy target kind '$legacyKind' must be rejected", err)
        }
        assertEquals(0, server.requestCount)

        // Canonical ENTRY_FIELD, PAYMENT, WORK_STATUS
        val canonicalCases = listOf(
            "ENTRY_FIELD" to JSONObject().put("display_name", "Đỗ Thị Mai Anh"),
            "PAYMENT" to DirectEntryContract.buildPaymentChangeProposal("provided", "1903000111", "VCB", "DO THI MAI ANH"),
            "WORK_STATUS" to DirectEntryContract.buildWorkStatusChangeProposal("OFF", "22/10/2026", "Nghỉ hết hợp đồng")
        )

        for ((targetKind, proposal) in canonicalCases) {
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "ok": true,
                      "request_id": "55555555-4444-4333-8222-111111111111",
                      "state": "PENDING",
                      "items": 1
                    }
                    """.trimIndent()
                )
            )
            val created = repo.directEntryService.createChangeRequest(
                entryId = entryId,
                targetKind = targetKind,
                expectedVersion = 3,
                proposal = proposal,
                reason = "Cập nhật nghiệp vụ"
            )
            assertEquals("PENDING", created.state)

            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/direct-entry/change-requests", req.path)
            val body = JSONObject(req.body.readUtf8())
            assertEquals(req.getHeader("Idempotency-Key"), body.getString("idempotency_key"))
            assertFalse(body.has("actor"))
            assertFalse(body.has("authority"))
            val item = body.getJSONArray("items").getJSONObject(0)
            assertEquals(entryId, item.getString("entry_id"))
            assertEquals(targetKind, item.getString("target_kind"))
            assertEquals(3, item.getInt("expected_version"))
        }
    }

    // -------------------------------------------------------------------------
    // Item 14: Privileged edit gọi đúng endpoint và refetch detail từ backend
    // -------------------------------------------------------------------------
    @Test
    fun `item 14 - privileged edit calls POST entries privileged-edit and refetches canonical detail`() = runBlocking {
        val repo = createRepository()
        val entryId = "77777777-6666-4555-8444-333333333333"

        // 1. Response for POST /api/direct-entry/entries/{entryId}/privileged-edit
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "entry_id": "$entryId",
                  "version": 4
                }
                """.trimIndent()
            )
        )
        // 2. Response for mandatory follow-up GET /api/direct-entry/entries/{entryId}
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "entry": {
                    "entry_id": "$entryId",
                    "submission_id": "11111111-2222-4333-8444-555555555555",
                    "version": 4,
                    "project_id": "PRJ_NEW",
                    "first_work_date": "2026-10-15",
                    "employee_code": "HRP9001",
                    "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                    "team_id": "TEAM_A",
                    "provider_type": "hrp",
                    "labor_type": "OFFICIAL",
                    "labor_type_display": "Chính thức",
                    "worker_details": {
                      "display_name": "Tên Đã Sửa Từ Server"
                    },
                    "employment_status": {
                      "status": "ON",
                      "effective_date": "2026-10-15",
                      "version": 2
                    },
                    "documents": []
                  }
                }
                """.trimIndent()
            )
        )

        val patch = JSONObject().put(
            "worker_details",
            JSONObject().put("display_name", "Tên Đã Sửa Từ Server")
        )
        val refreshed = repo.directEntryService.privilegedEditEntry(
            entryId = entryId,
            expectedEntryVersion = 3,
            patch = patch,
            reason = "Đính chính họ tên"
        )

        assertEquals(4, refreshed.version)
        assertEquals("Tên Đã Sửa Từ Server", refreshed.displayName)

        val mutationReq = server.takeRequest()
        assertEquals("POST", mutationReq.method)
        assertEquals("/api/direct-entry/entries/$entryId/privileged-edit", mutationReq.path)
        assertNotNull(mutationReq.getHeader("Origin"))
        val mutationBody = JSONObject(mutationReq.body.readUtf8())
        assertEquals(3, mutationBody.getInt("expected_entry_version"))

        val refetchReq = server.takeRequest()
        assertEquals("GET", refetchReq.method)
        assertEquals("/api/direct-entry/entries/$entryId", refetchReq.path)
    }
}
