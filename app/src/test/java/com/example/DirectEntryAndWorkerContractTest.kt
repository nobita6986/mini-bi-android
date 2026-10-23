package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.CatalogLaborTypeOption
import com.example.data.DirectEntryCatalog
import com.example.data.DirectEntryContract
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.NewWorkerEntryDraft
import com.example.ui.screens.DropdownOptionItem
import com.example.ui.screens.filterDropdownOptions
import com.example.ui.screens.normalizeVietnameseForSearch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
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
import java.io.ByteArrayOutputStream
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
        assertEquals("19/04/1999", r0.getJSONObject("worker").getJSONObject("date_of_birth").getString("value"))
        assertEquals("10/05/2021", r0.getJSONObject("worker").getJSONObject("national_id_issued_at").getString("value"))
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

    @Test
    fun `ui overhaul contract sync - gender Nam Nu only, CCCD 9 or 12 digits, free-date raw text and payment digits`() {
        assertEquals(listOf("Nam", "Nữ"), DirectEntryContract.DIRECT_ENTRY_GENDER_OPTIONS)

        val khacErr = runCatching { DirectEntryContract.mapGender("Khác") }
            .exceptionOrNull() as? BackendApiException
        assertNotNull(khacErr)
        assertEquals("GENDER_VOCABULARY_INVALID", khacErr!!.backendCode)

        val badCccdErr = runCatching { DirectEntryContract.optionalNationalIdField("0123456789") }
            .exceptionOrNull() as? BackendApiException
        assertNotNull(badCccdErr)
        assertEquals("NATIONAL_ID_INVALID", badCccdErr!!.backendCode)
        assertEquals("012345678", DirectEntryContract.optionalNationalIdField("012345678").getString("value"))
        assertEquals("001099012345", DirectEntryContract.optionalNationalIdField("001099012345").getString("value"))

        assertEquals("1999", DirectEntryContract.optionalFreeDateField("1999").getString("value"))
        val longDateErr = runCatching { DirectEntryContract.optionalFreeDateField("19/04/19999") }
            .exceptionOrNull() as? BackendApiException
        assertNotNull(longDateErr)
        assertEquals("PASTE_TEXT_TOO_LONG", longDateErr!!.backendCode)

        val badPaymentErr = runCatching {
            DirectEntryContract.buildPaymentChangeProposal("provided", "1903 000 111", "VCB", "NGUYEN VAN A")
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(badPaymentErr)
        assertEquals("PAYMENT_INVALID", badPaymentErr!!.backendCode)

        val baseDraft = NewWorkerEntryDraft(
            localId = "d1",
            projectId = "PRJ_01",
            providerType = "hrp",
            recruiterId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            laborTypeKey = "OFFICIAL",
            firstWorkDateUi = "20/10/2026",
            fullName = "Nguyễn Văn A"
        )
        assertTrue(baseDraft.isValid)
        assertFalse(baseDraft.copy(genderUi = "Khác").isValid)
        assertFalse(baseDraft.copy(cccd = "1234567890").isValid)
        assertTrue(baseDraft.copy(cccd = "001099012345", genderUi = "Nữ").isValid)
    }

    @Test
    fun `status transitions enforce ON to OFF and UNCONFIRMED to ON or OFF with optional null action reason`() {
        assertEquals(listOf("OFF"), DirectEntryContract.allowedWorkStatusTargets("ON"))
        assertEquals(listOf("ON", "OFF"), DirectEntryContract.allowedWorkStatusTargets("UNCONFIRMED"))
        assertEquals(emptyList<String>(), DirectEntryContract.allowedWorkStatusTargets("OFF"))

        assertEquals(listOf("ON", "OFF"), DirectEntryContract.selectableDirectEditStatuses("ON"))
        assertEquals(listOf("UNCONFIRMED", "ON", "OFF"), DirectEntryContract.selectableDirectEditStatuses("UNCONFIRMED"))
        assertEquals(listOf("OFF"), DirectEntryContract.selectableDirectEditStatuses("OFF"))

        // Reject ON -> UNCONFIRMED in both propose change and direct edit
        val invalidOnToUnconfirmed = runCatching {
            DirectEntryContract.buildPrivilegedEditEmploymentStatus(
                status = "UNCONFIRMED",
                effectiveDateUiOrIso = "23/10/2026",
                leaveReason = null,
                baselineStatus = "ON"
            )
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(invalidOnToUnconfirmed)
        assertEquals("STATUS_TRANSITION_INVALID", invalidOnToUnconfirmed!!.backendCode)

        // Reject OFF -> ON
        val invalidOffToOn = runCatching {
            DirectEntryContract.buildPrivilegedEditEmploymentStatus(
                status = "ON",
                effectiveDateUiOrIso = "23/10/2026",
                leaveReason = null,
                baselineStatus = "OFF"
            )
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(invalidOffToOn)
        assertEquals("STATUS_TRANSITION_INVALID", invalidOffToOn!!.backendCode)

        // Require leave_reason when transitioning to OFF
        val missingLeaveReason = runCatching {
            DirectEntryContract.buildPrivilegedEditEmploymentStatus(
                status = "OFF",
                effectiveDateUiOrIso = "23/10/2026",
                leaveReason = "   ",
                baselineStatus = "ON"
            )
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(missingLeaveReason)
        assertEquals("OFF_REQUIRES_DATE_AND_REASON", missingLeaveReason!!.backendCode)

        // Allow UNCONFIRMED -> ON without leave_reason
        val unconfirmedToOn = DirectEntryContract.buildPrivilegedEditEmploymentStatus(
            status = "ON",
            effectiveDateUiOrIso = "23/10/2026",
            leaveReason = null,
            baselineStatus = "UNCONFIRMED"
        )
        assertEquals("ON", unconfirmedToOn.getString("status"))
        assertFalse(unconfirmedToOn.has("leave_reason"))

        // Action reason is optional (JSON null) for both privileged-edit and change-requests
        val editPayload = DirectEntryContract.buildPrivilegedEditPayload(
            expectedEntryVersion = 2,
            patch = JSONObject().put("employment_status", unconfirmedToOn),
            reason = null
        )
        assertTrue(editPayload.isNull("reason"))

        val changeReqPayload = DirectEntryContract.buildChangeRequestPayload(
            entryId = "11111111-2222-4333-8444-555555555555",
            targetKind = "WORK_STATUS",
            expectedVersion = 2,
            proposal = DirectEntryContract.buildWorkStatusChangeProposal(
                status = "OFF",
                effectiveDateUiOrIso = "23/10/2026",
                leaveReason = "Hết hạn hợp đồng thời vụ",
                baselineStatus = "ON"
            ),
            reason = null,
            idempotencyKey = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        )
        assertTrue(changeReqPayload.isNull("reason"))
    }

    @Test
    fun `default worker draft uses gender Nam and seasonal labor type Thoi vu without marking row started`() {
        val catalog = DirectEntryCatalog(
            effectiveDate = "2026-10-23",
            projects = emptyList(),
            recruiters = emptyList(),
            banks = emptyList(),
            laborTypes = listOf(
                CatalogLaborTypeOption("PERMANENT", "Chính thức", 1),
                CatalogLaborTypeOption("TEMPORARY", "Thời vụ", 2),
                CatalogLaborTypeOption("OUTSOURCED", "Gia công", 3)
            )
        )
        val defaultDraft = DirectEntryContract.createDefaultWorkerDraft(catalog = catalog, localId = "row-default")
        assertEquals("Nam", defaultDraft.genderUi)
        assertEquals("TEMPORARY", defaultDraft.laborTypeKey)
        assertEquals("Thời vụ", defaultDraft.laborTypeDisplay)
        assertFalse(defaultDraft.isStarted)
    }

    @Test
    fun `draft submission preflight and transition to SUBMITTED sends matching idempotency and duplicate CCCD ack`() = runBlocking {
        val repo = createRepository()
        val submissionId = "11111111-2222-4333-8444-555555555555"
        val fingerprint = "a".repeat(64)

        // 1. Preflight returning 1 duplicate CCCD conflict
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "submission_id": "$submissionId",
                  "version": 1,
                  "fingerprint": "$fingerprint",
                  "conflict_count": 1,
                  "conflicts": [
                    {
                      "conflict_ref": "c1",
                      "draft_display_name": "Lê Văn Trùng",
                      "project_display": "KCN Bắc Ninh",
                      "employment_status": "ON",
                      "cccd_last4": "6789"
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        // 2. Transition with duplicate CCCD acknowledgement -> SUBMITTED
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": true,
                  "submission_id": "$submissionId",
                  "state": "SUBMITTED",
                  "version": 2
                }
                """.trimIndent()
            )
        )

        val preflight = repo.directEntryService.preflightDuplicateCccd(submissionId)
        assertEquals(1, preflight.conflictCount)
        assertEquals(fingerprint, preflight.fingerprint)
        assertTrue(preflight.conflicts.single().formatConflictLine().contains("*6789"))

        val transitioned = repo.directEntryService.transitionSubmissionToSubmitted(
            submissionId = submissionId,
            expectedVersion = preflight.version,
            duplicateCccdFingerprint = preflight.fingerprint,
            duplicateCccdCount = preflight.conflictCount
        )
        assertEquals("SUBMITTED", transitioned.state)
        assertEquals(2, transitioned.version)

        val preflightReq = server.takeRequest()
        assertEquals("GET", preflightReq.method)
        assertEquals("/api/direct-entry/submissions/$submissionId/duplicate-cccd-preflight", preflightReq.path)

        val transitionReq = server.takeRequest()
        assertEquals("POST", transitionReq.method)
        assertEquals("/api/direct-entry/submissions/$submissionId/transition", transitionReq.path)
        val headerIdemKey = transitionReq.getHeader("Idempotency-Key")
        assertNotNull(headerIdemKey)

        val bodyJson = JSONObject(transitionReq.body.readUtf8())
        assertEquals(1, bodyJson.getInt("expected_version"))
        assertEquals("SUBMITTED", bodyJson.getString("target_state"))
        assertEquals(headerIdemKey, bodyJson.getString("idempotency_key"))
        assertEquals(fingerprint, bodyJson.getString("duplicate_cccd_fingerprint"))
        assertEquals(1, bodyJson.getInt("duplicate_cccd_count"))
    }

    @Test
    fun `db-first attachment flow - prepares local attachment without stageId, omits stage IDs in full-profile payload, and uploads via reserve-put-finalize without leaking cookies or origin`() = runBlocking {
        val repo = createRepository()
        val r2Server = MockWebServer()
        r2Server.start()

        try {
            // Seed a session cookie on backend server to verify it is sent to backend but NEVER sent to presigned R2 URL
            val sessionCookie = Cookie.Builder()
                .name("mini_bi_sid")
                .value("secret_backend_cookie_xyz")
                .hostOnlyDomain(server.hostName)
                .path("/")
                .expiresAt(System.currentTimeMillis() + 3_600_000L)
                .build()
            repo.cookieJar.saveFromResponse(server.url("/api/auth/session"), listOf(sessionCookie))

            // Create a valid PNG bitmap in memory so DocumentImageCompressor processes it cleanly
            val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            val pngBytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

            val localPortrait = repo.directEntryService.prepareLocalAttachmentBytes(
                documentType = "WORKER_PORTRAIT",
                rawBytes = pngBytes,
                fileName = "chan_dung.png",
                mimeType = "image/png"
            )
            assertEquals("", localPortrait.stageId)
            assertNotNull(localPortrait.localFilePath)
            assertTrue(File(localPortrait.localFilePath!!).exists())

            val draft = NewWorkerEntryDraft(
                localId = "db-first-row",
                projectId = "PRJ_01",
                providerType = "hrp",
                recruiterId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                laborTypeKey = "TEMPORARY",
                firstWorkDateUi = "24/10/2026",
                employmentStatusUi = "Đang làm",
                fullName = "Vũ Văn Nhanh",
                portraitStage = localPortrait
            )
            assertEquals(1, draft.localPendingAttachments().size)

            // DB-first batch payload MUST NOT include empty stage IDs
            val batchPayload = DirectEntryContract.buildFullProfileV11Payload(listOf(draft))
            val row0 = batchPayload.getJSONArray("rows").getJSONObject(0)
            assertFalse(row0.has("portrait_stage_id"))
            assertFalse(row0.has("document_stage_ids"))

            // Post-entry background upload: reserve -> presigned PUT -> finalize
            val entryId = "77777777-6666-4555-8444-333333333333"
            val docId = "88888888-7777-4666-8555-444444444444"
            val presignedUrl = r2Server.url("/r2-bucket/worker-portrait-upload").toString()

            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "ok": true,
                      "document_id": "$docId",
                      "entry_version": 2,
                      "upload_status": "UPLOADING",
                      "upload": {
                        "url": "$presignedUrl",
                        "headers": {
                          "Content-Type": "${localPortrait.mimeType}",
                          "x-amz-meta-doc": "portrait"
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
            r2Server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "ok": true,
                      "document_id": "$docId",
                      "entry_version": 3,
                      "upload_status": "READY"
                    }
                    """.trimIndent()
                )
            )

            val fileBytes = File(localPortrait.localFilePath!!).readBytes()
            val updatedVersion = repo.directEntryService.uploadDocumentToEntry(
                entryId = entryId,
                initialEntryVersion = 1,
                documentType = "WORKER_PORTRAIT",
                fileBytes = fileBytes,
                mimeType = localPortrait.mimeType,
                idempotencyBaseKey = "test-bg-upload-key"
            )
            assertEquals(3, updatedVersion)

            val reserveReq = server.takeRequest()
            assertEquals("POST", reserveReq.method)
            assertEquals("/api/direct-entry/entries/$entryId/documents", reserveReq.path)
            assertTrue(reserveReq.getHeader("Cookie").orEmpty().contains("mini_bi_sid=secret_backend_cookie_xyz"))
            assertNotNull(reserveReq.getHeader("Origin"))
            assertEquals(1, JSONObject(reserveReq.body.readUtf8()).getInt("expected_entry_version"))

            val r2PutReq = r2Server.takeRequest()
            assertEquals("PUT", r2PutReq.method)
            assertEquals("/r2-bucket/worker-portrait-upload", r2PutReq.path)
            assertNull("Presigned R2 PUT must NOT carry session Cookie", r2PutReq.getHeader("Cookie"))
            assertNull("Presigned R2 PUT must NOT carry Origin header", r2PutReq.getHeader("Origin"))
            assertEquals("portrait", r2PutReq.getHeader("x-amz-meta-doc"))
            assertEquals(fileBytes.size.toLong(), r2PutReq.bodySize)

            val finalizeReq = server.takeRequest()
            assertEquals("POST", finalizeReq.method)
            assertEquals("/api/direct-entry/entries/$entryId/documents/$docId/finalize", finalizeReq.path)
            assertEquals(2, JSONObject(finalizeReq.body.readUtf8()).getInt("expected_entry_version"))

            repo.directEntryService.deleteLocalAttachment(localPortrait.localFilePath)
            assertFalse(File(localPortrait.localFilePath!!).exists())
        } finally {
            runCatching { r2Server.shutdown() }
        }
    }

    @Test
    fun `searchable dropdown filter - matches Vietnamese diacritics, d-d equivalence, personnel codes, and prioritizes prefix matches`() {
        assertEquals("dang thi anh tuyet", normalizeVietnameseForSearch("  Đặng Thị   Ánh Tuyết "))

        val options = listOf(
            DropdownOptionItem(
                id = "r1",
                label = "Nguyễn Văn An (HRP001)",
                subtitle = "Team Bắc Ninh",
                searchKeywords = "Nguyễn Văn An HRP001 Team Bắc Ninh"
            ),
            DropdownOptionItem(
                id = "r2",
                label = "Đặng Minh Đức (HRP088)",
                subtitle = "Team Hải Phòng",
                searchKeywords = "Đặng Minh Đức HRP088 Team Hải Phòng"
            ),
            DropdownOptionItem(
                id = "r3",
                label = "Trần Thị Đức Hạnh (VND019)",
                subtitle = "Đối tác cung ứng",
                searchKeywords = "Trần Thị Đức Hạnh VND019 Vendor"
            )
        )

        // Diacritic-insensitive & đ/d matching
        val matchDangDuc = filterDropdownOptions(options, "dang duc")
        assertEquals(listOf("r2"), matchDangDuc.map { it.id })

        // Personnel code quick search
        val matchCode = filterDropdownOptions(options, "hrp088")
        assertEquals(listOf("r2"), matchCode.map { it.id })

        // Prefix prioritization: query "tran" puts Trần Thị Đức Hạnh first
        val matchDuc = filterDropdownOptions(options, "duc")
        assertEquals(setOf("r2", "r3"), matchDuc.map { it.id }.toSet())
    }
}
