package com.example

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActorScope
import com.example.data.AppActor
import com.example.data.DirectEntryContract
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.SavedLoginAccount
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.ui.MiniBiViewModel
import com.example.ui.screens.DirectEntryNewWorkersScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.WorkerOperationsRowCard
import com.example.ui.screens.WorkerPrivilegedEditDialog
import com.example.ui.screens.WorkerProposeChangeDialog
import com.example.ui.screens.WorkersAndDirectEntryScreen
import com.example.ui.screens.resolveVisibleWorkerTabs
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.Cookie
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h1400dp")
class WorkerUiAuthorizationRobolectricTest {

    private val pendingCleanups = mutableListOf<() -> Unit>()

    private fun registerCleanup(block: () -> Unit) {
        pendingCleanups.add(block)
    }

    private fun registerTestResourcesCleanup(
        vm: MiniBiViewModel,
        db: MiniBiDatabase,
        server: MockWebServer? = null,
        vararg tempFiles: File
    ) {
        registerCleanup {
            vm.cancelScopeForTest()
            runCatching { server?.shutdown() }
            val vmJob = vm.viewModelScope.coroutineContext[Job]
            var attempts = 0
            while (vmJob?.children?.any { !it.isCompleted } == true && attempts < 50) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10)
                attempts++
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            runCatching { db.close() }
            for (file in tempFiles) {
                runCatching { file.delete() }
            }
        }
    }

    private fun buildTestDb(app: Application): MiniBiDatabase =
        Room.inMemoryDatabaseBuilder(app, MiniBiDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
            .build()

    private val outerCleanupRule = object : ExternalResource() {
        override fun after() {
            val actions = pendingCleanups.toList()
            pendingCleanups.clear()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            for (action in actions) {
                runCatching { action() }
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
    }

    private val innerComposeRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(outerCleanupRule)
        .around(
            TestRule { base, _ ->
                object : Statement() {
                    override fun evaluate() {
                        try {
                            base.evaluate()
                        } catch (t: Throwable) {
                            if (t.suppressedExceptions.isNotEmpty()) {
                                val details = t.suppressedExceptions.joinToString("\n---\n") {
                                    it.stackTraceToString()
                                }
                                throw AssertionError("${t.message}\nSuppressed details:\n$details", t)
                            }
                            throw t
                        }
                    }
                }
            }
        )
        .around(innerComposeRule)

    private val composeTestRule
        get() = innerComposeRule

    private fun sampleWorker(
        entryId: String,
        canView: Boolean,
        canProposeChange: Boolean,
        canDirectEdit: Boolean
    ) = WorkerProfileEntity(
        cacheKey = "managed:$entryId",
        scopeKey = "managed",
        entryId = entryId,
        entryVersion = 2,
        submissionState = "SUBMITTED",
        employeeCode = "HRP0101",
        displayName = "Nguyễn Văn Kiểm Thử",
        projectId = "PRJ_01",
        projectDisplay = "Dự án Kiểm Thử",
        firstWorkDate = "2026-10-01",
        laborType = "OFFICIAL",
        laborTypeDisplay = "Chính thức",
        employmentStatus = "ON",
        recruiterId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
        recruiterDisplay = "Tuyển dụng A",
        paymentSummary = "",
        pendingRequestSummary = "",
        lastDecisionSummary = "",
        isProjectManager = true,
        canView = canView,
        canProposeChange = canProposeChange,
        canDirectEdit = canDirectEdit
    )

    // -------------------------------------------------------------------------
    // Item 11: Worker CTA chỉ xuất hiện theo allowed_actions
    // -------------------------------------------------------------------------
    @Test
    fun `item 11 - worker CTAs appear strictly when server allowed_actions flags are true`() {
        val idNone = "11111111-1111-4111-8111-111111111111"
        val idProposeOnly = "22222222-2222-4222-8222-222222222222"
        val idDirectOnly = "33333333-3333-4333-8333-333333333333"

        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    WorkerOperationsRowCard(
                        worker = sampleWorker(idNone, canView = true, canProposeChange = false, canDirectEdit = false),
                        onViewDetail = {},
                        onProposeChange = {},
                        onDirectEdit = {}
                    )
                    WorkerOperationsRowCard(
                        worker = sampleWorker(idProposeOnly, canView = true, canProposeChange = true, canDirectEdit = false),
                        onViewDetail = {},
                        onProposeChange = {},
                        onDirectEdit = {}
                    )
                    WorkerOperationsRowCard(
                        worker = sampleWorker(idDirectOnly, canView = false, canProposeChange = false, canDirectEdit = true),
                        onViewDetail = {},
                        onProposeChange = {},
                        onDirectEdit = {}
                    )
                }
            }
        }

        // Row 1: view=true, propose_change=false, direct_edit=false
        composeTestRule.onNodeWithTag("worker_detail_$idNone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_propose_$idNone").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_direct_edit_$idNone").assertDoesNotExist()

        // Row 2: propose_change=true, direct_edit=false
        composeTestRule.onNodeWithTag("worker_propose_$idProposeOnly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_direct_edit_$idProposeOnly").assertDoesNotExist()

        // Row 3: view=false, propose_change=false, direct_edit=true
        composeTestRule.onNodeWithTag("worker_detail_$idDirectOnly").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_propose_$idDirectOnly").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_direct_edit_$idDirectOnly").assertIsDisplayed()
    }

    @Test
    fun `item 11b - directory parser defaults missing allowed_actions flags to false and tabs respect server scopes`() {
        val pageJson = JSONObject(
            """
            {
              "ok": true,
              "scope": "managed",
              "items": [
                {
                  "entry_id": "44444444-4444-4444-8444-444444444444",
                  "entry_version": 1,
                  "submission_state": "SUBMITTED",
                  "employee_code": "HRP0202",
                  "display_name": "Trần Văn B",
                  "project_id": "PRJ_1",
                  "project_display": "Dự án 1",
                  "first_work_date": "2026-10-10",
                  "labor_type": "OFFICIAL",
                  "labor_type_display": "Chính thức",
                  "employment_status": "ON",
                  "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                  "recruiter_display": "Rec",
                  "allowed_actions": {
                    "view": true
                  }
                }
              ],
              "page_size": 25,
              "has_more": false,
              "next_cursor": null
            }
            """.trimIndent()
        )
        val parsed = DirectEntryContract.parseWorkerDirectoryPage(pageJson, "managed")
        val row = parsed.items.single()
        assertTrue(row.allowedActions.view)
        assertFalse(row.allowedActions.proposeChange)
        assertFalse(row.allowedActions.directEdit)

        // Own-only actor ("Admin" in displayName must NOT grant team or all tabs!)
        val ownOnlyActor = AppActor(
            appUserId = "11111111-2222-4333-8444-555555555555",
            displayName = "Admin Team Leader Fake Name",
            capabilities = listOf("entry_read", "submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )
        val tabs = resolveVisibleWorkerTabs(ownOnlyActor).map { it.key }
        assertEquals(listOf("managed", "recruited", "uploader"), tabs)
        assertFalse(tabs.contains("team"))
        assertFalse(tabs.contains("all"))

        // Real backend actor without phantom "entry_read" still sees managed + recruited scopes
        val realBackendActor = AppActor(
            appUserId = "66666666-2222-4333-8444-555555555555",
            displayName = "Tuyển dụng HRP",
            capabilities = emptyList(),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )
        val realTabs = resolveVisibleWorkerTabs(realBackendActor)
        assertEquals(listOf("managed", "recruited"), realTabs.map { it.key })
        assertEquals(listOf("NLĐ dự án tôi quản lý", "NLĐ mà tôi tuyển"), realTabs.map { it.label })
    }

    @Test
    fun `ui overhaul - personnel screen removes top worker card and cap moi button while showing recruited and managed tabs`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_auth_test_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_auth_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = "http://127.0.0.1:1",
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, null, cookieFile, prefsFile)
        val actor = AppActor(
            appUserId = "77777777-2222-4333-8444-555555555555",
            displayName = "Nguyễn Tuyển Dụng",
            capabilities = listOf("entry_create", "submission_create"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = actor,
                    viewModel = vm,
                    onBackToDashboard = {},
                    onSelectScopeTab = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Người lao động").assertDoesNotExist()
        composeTestRule.onNodeWithTag("go_to_new_entry_from_workers").assertDoesNotExist()
        composeTestRule.onNodeWithTag("export_workers_excel_button").assertDoesNotExist()
        composeTestRule.onNodeWithText("Xuất Excel").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_scope_tab_managed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_scope_tab_recruited").assertIsDisplayed()
    }

    @Test
    fun `ui polish - long project name in worker card wraps compactly without squeezing date into vertical blank gap`() {
        val longProjectWorker = sampleWorker(
            entryId = "99999999-2222-4333-8444-555555555555",
            canView = true,
            canProposeChange = false,
            canDirectEdit = false
        ).copy(
            projectDisplay = "Công Ty Cổ Phần Điện Tử Mentech Việt Nam Khu Công Nghiệp Bình Dương",
            recruiterDisplay = "Nguyễn Thị Tuyển Dụng Chuyên Trách Khu Vực Phía Bắc"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .testTag("worker_card_container")
                ) {
                    WorkerOperationsRowCard(
                        worker = longProjectWorker,
                        onViewDetail = {},
                        onProposeChange = {},
                        onDirectEdit = {}
                    )
                }
            }
        }

        val projectBounds = composeTestRule
            .onNodeWithText("Dự án: Công Ty Cổ Phần Điện Tử Mentech Việt Nam Khu Công Nghiệp Bình Dương")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val recruiterBounds = composeTestRule
            .onNodeWithText("Người tuyển: Nguyễn Thị Tuyển Dụng Chuyên Trách Khu Vực Phía Bắc")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val dateBounds = composeTestRule
            .onNodeWithText("Ngày đầu: 2026-10-01")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        val gapDp = with(composeTestRule.density) { (recruiterBounds.top - projectBounds.bottom).toDp().value }
        val dateWidthDp = with(composeTestRule.density) { dateBounds.width.toDp().value }
        val dateHeightDp = with(composeTestRule.density) { dateBounds.height.toDp().value }

        assertTrue("Expected zero huge blank gap below project row, actual=${gapDp}dp", gapDp < 16f)
        assertTrue("Expected non-zero horizontal width for first work date, actual=${dateWidthDp}dp", dateWidthDp > 60f)
        assertTrue("Expected single-line height for first work date, actual=${dateHeightDp}dp", dateHeightDp < 60f)
    }

    @Test
    fun `ui overhaul - direct entry new workers screen removes top multi-entry card and keeps bottom add worker button`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_new_entry_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_new_entry_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = "http://127.0.0.1:1",
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, null, cookieFile, prefsFile)
        val actor = AppActor(
            appUserId = "88888888-2222-4333-8444-555555555555",
            displayName = "Nguyễn Nhập Liệu",
            capabilities = listOf("entry_create", "submission_create"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DirectEntryNewWorkersScreen(
                    actor = actor,
                    viewModel = vm,
                    onNavigateToWorkersList = {},
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Nhập nhiều NLĐ").assertDoesNotExist()
        composeTestRule.onNodeWithTag("top_add_worker_row_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("direct_entry_new_workers_lazy_column")
            .performScrollToNode(hasTestTag("bottom_add_worker_row_button"))
        composeTestRule.onNodeWithTag("bottom_add_worker_row_button").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w480dp-h2600dp")
    fun `new entry screen defaults gender Nam and labor Thoi vu, removes blank gender, and confirms before reset`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/direct-entry/catalog") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "catalog": {
                                "effective_date": "2026-10-23",
                                "projects": [{"project_id": "PRJ_01", "display_name": "Dự án 1"}],
                                "recruiters": [],
                                "banks": [],
                                "labor_types": [
                                  {"labor_type_key": "OFFICIAL", "display_name": "Chính thức", "sort_order": 10},
                                  {"labor_type_key": "TEMPORARY", "display_name": "Thời vụ", "sort_order": 20}
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"error":{"code":"AUTH_UNAUTHENTICATED","message":"Unauthenticated"}}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_defaults_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_defaults_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, prefsFile)
        val actor = AppActor(
            appUserId = "88888888-3333-4333-8444-555555555555",
            displayName = "Nguyễn Nhập Liệu",
            capabilities = emptyList(),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DirectEntryNewWorkersScreen(
                    actor = actor,
                    viewModel = vm,
                    onNavigateToWorkersList = {},
                    onBackToDashboard = {}
                )
            }
        }

        // Wait for catalog to load and default "Thời vụ" to populate
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            vm.catalog.value != null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Thời vụ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nam").assertIsDisplayed()

        // Open gender dropdown -> only "Nam" and "Nữ", no "Bỏ trống"
        composeTestRule.onNodeWithTag("row_1_gender").performClick()
        composeTestRule.onNodeWithText("Nữ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bỏ trống").assertDoesNotExist()
        composeTestRule.onNodeWithText("Nữ").performClick()

        // Type worker name
        composeTestRule.onNodeWithTag("row_1_full_name").performTextInput("Lê Văn Thử Nghiệm")
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertIsDisplayed()

        // Click "Làm mới dòng này" -> confirmation dialog must appear first, preserving data if cancelled
        composeTestRule.onNodeWithTag("row_1_reset_or_remove_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_reset_worker_row_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertIsDisplayed()

        // Click "Làm mới dòng này" -> confirm "Đồng ý" -> clears row and restores defaults ("Nam", "Thời vụ")
        composeTestRule.onNodeWithTag("row_1_reset_or_remove_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_button").performClick()
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertDoesNotExist()
        composeTestRule.onNodeWithText("Nam").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thời vụ").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w480dp-h1400dp")
    fun `privileged edit and propose change dialogs enforce status transition rules and omit action reason fields`() {
        val baseWorker = sampleWorker(
            entryId = "aaaaaaaa-1111-4222-8333-444444444444",
            canView = true,
            canProposeChange = true,
            canDirectEdit = true
        )
        val detailOn = WorkerEntryDetail(
            entryId = baseWorker.entryId,
            submissionId = "bbbbbbbb-1111-4222-8333-444444444444",
            version = 2,
            projectId = "PRJ_01",
            firstWorkDate = "2026-10-01",
            employeeCode = "HRP0101",
            recruiterId = baseWorker.recruiterId,
            teamId = "TEAM_01",
            providerType = "hrp",
            laborType = "TEMPORARY",
            laborTypeDisplay = "Thời vụ",
            displayName = baseWorker.displayName,
            gender = "MALE",
            dateOfBirth = null,
            nationalId = null,
            address = null,
            phone = null,
            generalNote = null,
            paymentState = "empty",
            bankId = null,
            bankName = null,
            accountNumber = null,
            accountHolderName = null,
            employmentStatus = "ON",
            employmentEffectiveDate = "2026-10-01",
            employmentStatusVersion = 1,
            employmentLeaveReason = null,
            portraitPreviewPath = null,
            portraitDownloadPath = null,
            documents = emptyList()
        )

        val activeDialog = androidx.compose.runtime.mutableStateOf("EDIT")

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    when (activeDialog.value) {
                        "EDIT" -> WorkerPrivilegedEditDialog(
                            worker = baseWorker,
                            baselineDetail = detailOn,
                            catalog = null,
                            busy = false,
                            error = null,
                            onDismiss = {},
                            onSubmit = { _, _, _ -> },
                            renderInDialog = false
                        )
                        "PROPOSE" -> WorkerProposeChangeDialog(
                            worker = baseWorker.copy(employmentStatus = "UNCONFIRMED"),
                            baselineDetail = detailOn.copy(employmentStatus = "UNCONFIRMED"),
                            catalog = null,
                            busy = false,
                            error = null,
                            onDismiss = {},
                            onSubmit = { _, _, _, _ -> },
                            renderInDialog = false
                        )
                    }
                }
            }
        }

        // Direct edit from ON: keeps current ON + allows OFF, hides UNCONFIRMED, omits "Lý do chỉnh sửa"
        composeTestRule.onNodeWithTag("direct_edit_status_chip_ON").assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_status_chip_OFF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_status_chip_UNCONFIRMED").assertDoesNotExist()
        composeTestRule.onNodeWithText("Lý do chỉnh sửa").assertDoesNotExist()
        composeTestRule.onNodeWithTag("direct_edit_leave_reason_input").assertDoesNotExist()

        // Selecting OFF reveals "Lý do nghỉ *"
        composeTestRule.onNodeWithTag("direct_edit_status_chip_OFF").performClick()
        composeTestRule.onNodeWithTag("direct_edit_leave_reason_input").assertIsDisplayed()

        // Switch to Propose Change from UNCONFIRMED
        composeTestRule.runOnIdle { activeDialog.value = "PROPOSE" }
        composeTestRule.onNodeWithText("Lý do đề xuất").assertDoesNotExist()
        composeTestRule.onNodeWithTag("propose_target_WORK_STATUS").performClick()

        // From UNCONFIRMED: allows ON and OFF, hides UNCONFIRMED
        composeTestRule.onNodeWithTag("propose_status_chip_ON").assertIsDisplayed()
        composeTestRule.onNodeWithTag("propose_status_chip_OFF").assertIsDisplayed()
        composeTestRule.onNodeWithTag("propose_status_chip_UNCONFIRMED").assertDoesNotExist()
        composeTestRule.onNodeWithTag("propose_status_chip_OFF").performClick()
        composeTestRule.onNodeWithTag("propose_leave_reason_input").assertIsDisplayed()
    }

    @Test
    fun `cancelled worker tab coroutine never sets StandaloneCoroutine was cancelled error`() {
        val managedStarted = java.util.concurrent.CountDownLatch(1)
        val recruitedServed = java.util.concurrent.CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("scope=managed") -> {
                        managedStarted.countDown()
                        MockResponse()
                            .setBodyDelay(350, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "scope": "managed",
                                  "items": [],
                                  "page_size": 25,
                                  "has_more": false,
                                  "next_cursor": null
                                }
                                """.trimIndent()
                            )
                    }
                    path.contains("scope=recruited") -> {
                        recruitedServed.countDown()
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "scope": "recruited",
                                  "items": [],
                                  "page_size": 25,
                                  "has_more": false,
                                  "next_cursor": null
                                }
                                """.trimIndent()
                            )
                    }
                    else -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"error":{"code":"AUTH_UNAUTHENTICATED","message":"Unauthenticated"}}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "cancel_tab_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "cancel_tab_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, prefsFile)

        composeTestRule.setContent {
            val loading = vm.workerLoading.collectAsState().value
            val err = vm.workerError.collectAsState().value
            androidx.compose.material3.Text("loading=$loading err=$err")
        }

        composeTestRule.runOnIdle {
            vm.selectWorkerTab("managed")
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            managedStarted.count == 0L
        }

        // Immediately switch tab while first coroutine is in-flight -> cancels job #1
        composeTestRule.runOnIdle {
            vm.selectWorkerTab("recruited")
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            recruitedServed.count == 0L && !vm.workerLoading.value
        }
        Thread.sleep(450)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        assertNull(vm.workerError.value)
    }

    @Test
    @Config(qualifiers = "w480dp-h2600dp")
    fun `searchable recruiter picker, outside tap focus clearing, larger cancel button in reset dialog, and Vietnamese localization`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/direct-entry/catalog") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "catalog": {
                                "effective_date": "2026-10-23",
                                "projects": [{"project_id": "PRJ_01", "display_name": "Samsung Bắc Ninh"}],
                                "recruiters": [
                                  {
                                    "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-111111111111",
                                    "display_name": "Đặng Văn Tuyển",
                                    "personnel_code": "HRP088",
                                    "provider_type": "hrp",
                                    "team_display_name": "Team Bắc Ninh",
                                    "label": "Đặng Văn Tuyển (HRP088)"
                                  },
                                  {
                                    "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-222222222222",
                                    "display_name": "Nguyễn Thị Hoa",
                                    "personnel_code": "HRP012",
                                    "provider_type": "hrp",
                                    "team_display_name": "Team Hải Phòng",
                                    "label": "Nguyễn Thị Hoa (HRP012)"
                                  }
                                ],
                                "banks": [],
                                "labor_types": [
                                  {"labor_type_key": "TEMPORARY", "display_name": "Thời vụ", "sort_order": 10}
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"error":{"code":"AUTH_UNAUTHENTICATED","message":"Unauthenticated"}}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_search_focus_${System.nanoTime()}.enc")
        val savedCredsFile = File(app.cacheDir, "ui_saved_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_search_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            savedCredentialsStorageFile = savedCredsFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, savedCredsFile, prefsFile)
        val actor = AppActor(
            appUserId = "88888888-4444-4333-8444-555555555555",
            displayName = "Nguyễn Nhập Liệu",
            capabilities = emptyList(),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DirectEntryNewWorkersScreen(
                    actor = actor,
                    viewModel = vm,
                    onNavigateToWorkersList = {},
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.sessionBootstrapping.value && vm.catalog.value != null
        }
        composeTestRule.waitForIdle()

        // 1. Vietnamese localization check: zero English "Stage" / "worker-profile/1.1" / "MiB"
        composeTestRule.onNodeWithText("Stage", substring = true, ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("worker-profile/1.1", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("MiB", substring = true).assertDoesNotExist()

        // 2. Focus clearing when tapping outside text input
        composeTestRule.onNodeWithTag("row_1_full_name").performClick()
        composeTestRule.onNodeWithTag("row_1_full_name").performTextInput("Hồ Văn Nam")
        composeTestRule.onNodeWithTag("row_1_full_name").assertIsFocused()

        // Tap non-input header area -> highlight/focus on row_1_full_name must clear immediately
        composeTestRule.onNodeWithText("Nhập liệu trực tiếp").performClick()
        composeTestRule.onNodeWithTag("row_1_full_name").assertIsNotFocused()

        // 3. Searchable Recruiter Picker with Vietnamese diacritic-insensitive quick filtering
        composeTestRule.onNodeWithTag("row_1_provider").performClick()
        composeTestRule.onNodeWithText("HRP").performClick()

        composeTestRule.onNodeWithTag("row_1_recruiter").performClick()
        composeTestRule.onNodeWithTag("row_1_recruiter_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_recruiter_search_input").performTextInput("nguyen hoa")

        composeTestRule.onNodeWithTag("row_1_recruiter_option_aaaaaaaa-bbbb-4ccc-8ddd-222222222222").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_recruiter_option_aaaaaaaa-bbbb-4ccc-8ddd-111111111111").assertDoesNotExist()

        composeTestRule.onNodeWithTag("row_1_recruiter_option_aaaaaaaa-bbbb-4ccc-8ddd-222222222222").performClick()
        composeTestRule.onNodeWithTag("row_1_recruiter_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Nguyễn Thị Hoa (HRP012)").assertIsDisplayed()

        // 4. Reset confirmation dialog: Cancel ("Hủy") button is larger in both width & height than Confirm ("Đồng ý")
        composeTestRule.onNodeWithTag("row_1_reset_or_remove_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_dialog").assertIsDisplayed()

        val cancelBounds = composeTestRule.onNodeWithTag("cancel_reset_worker_row_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val confirmBounds = composeTestRule.onNodeWithTag("confirm_reset_worker_row_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected Hủy button width (${cancelBounds.width}) > Đồng ý button width (${confirmBounds.width})",
            cancelBounds.width > confirmBounds.width
        )
        assertTrue(
            "Expected Hủy button height (${cancelBounds.height}) > Đồng ý button height (${confirmBounds.height})",
            cancelBounds.height > confirmBounds.height
        )
        composeTestRule.onNodeWithTag("cancel_reset_worker_row_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w480dp-h1400dp")
    fun `dashboard removes Trung tam Dieu hanh card, renders Bao cao tong quat, and floating banner overlay never shifts content layout`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "11111111-2222-4333-8444-555555555555",
                                "display_name": "Trần Quản Lý",
                                "capabilities": ["entry_read"],
                                "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/duplicate-cccd-report") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "page": 1,
                              "page_size": 25,
                              "total_groups": 0,
                              "total_entries": 0,
                              "has_more": false,
                              "authorization_date": "2026-10-23",
                              "groups": []
                            }
                            """.trimIndent()
                        )
                    path.contains("scope=managed") || path.contains("scope=recruited") -> {
                        val scope = if (path.contains("scope=managed")) "managed" else "recruited"
                        val entryId = if (scope == "managed") {
                            "44444444-4444-4444-8444-444444444444"
                        } else {
                            "55555555-5555-4555-8555-555555555555"
                        }
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "scope": "$scope",
                                  "items": [
                                    {
                                      "entry_id": "$entryId",
                                      "entry_version": 1,
                                      "submission_state": "SUBMITTED",
                                      "employee_code": "HRP0999",
                                      "display_name": "Nguyễn Văn A",
                                      "project_id": "PRJ_1",
                                      "project_display": "Samsung Bắc Ninh",
                                      "first_work_date": "2026-10-10",
                                      "labor_type": "TEMPORARY",
                                      "labor_type_display": "Thời vụ",
                                      "employment_status": "ON",
                                      "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                                      "recruiter_display": "Trần Quản Lý",
                                      "allowed_actions": {"view": true}
                                    }
                                  ],
                                  "summary": {"total_workers": 1, "project_count": 1, "recruiter_count": 1},
                                  "page_size": 100,
                                  "has_more": false,
                                  "next_cursor": null
                                }
                                """.trimIndent()
                            )
                    }
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"data":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_dash_overlay_${System.nanoTime()}.enc")
        val savedCredsFile = File(app.cacheDir, "ui_dash_saved_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_dash_prefs_${System.nanoTime()}.preferences_pb")
        val cipher = JvmAes256GcmCookieCipher()
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            savedCredentialsStorageFile = savedCredsFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = cipher
        )
        // Pre-seed session cookie so MiniBiViewModel bootstrap authenticates directly into Dashboard
        repo.cookieJar.saveFromResponse(
            server.url("/api/auth/session"),
            listOf(
                Cookie.Builder()
                    .name("mini_bi_sid")
                    .value("valid_test_sid")
                    .hostOnlyDomain(server.hostName)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 3_600_000L)
                    .build()
            )
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, savedCredsFile, prefsFile)

        composeTestRule.setContent {
            MyApplicationTheme {
                MiniBiAppRoot(viewModel = vm)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.sessionBootstrapping.value && vm.generalOverview.value != null
        }
        composeTestRule.waitForIdle()

        // 1. Executive card removed & General Overview rendered
        composeTestRule.onNodeWithText("Trung tâm Điều hành Tuyển dụng").assertDoesNotExist()
        composeTestRule.onNodeWithText("Chưa hỗ trợ trên ứng dụng").assertDoesNotExist()
        composeTestRule.onNodeWithTag("general_overview_section").assertIsDisplayed()
        composeTestRule.onNodeWithText("Báo cáo Tổng quát Tuyển dụng").assertIsDisplayed()

        // 2. Measure content Y position WITHOUT banner
        composeTestRule.runOnIdle { vm.clearBanner() }
        composeTestRule.waitForIdle()
        val layerTopWithoutBanner = composeTestRule.onNodeWithTag("main_screen_content_layer")
            .fetchSemanticsNode().boundsInRoot.top
        val overviewTopWithoutBanner = composeTestRule.onNodeWithTag("general_overview_section")
            .fetchSemanticsNode().boundsInRoot.top

        // 3. Show "Xin chào..." banner -> appears in floating overlay layer with ZERO layout shift underneath
        composeTestRule.runOnIdle { vm.showBanner("Xin chào Trần Quản Lý!") }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("floating_status_banner_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Xin chào Trần Quản Lý!").assertIsDisplayed()

        val layerTopWithBanner = composeTestRule.onNodeWithTag("main_screen_content_layer")
            .fetchSemanticsNode().boundsInRoot.top
        val overviewTopWithBanner = composeTestRule.onNodeWithTag("general_overview_section")
            .fetchSemanticsNode().boundsInRoot.top

        assertEquals(layerTopWithoutBanner, layerTopWithBanner, 0.5f)
        assertEquals(overviewTopWithoutBanner, overviewTopWithBanner, 0.5f)

        // 4. Dismiss banner -> still zero Y shift
        composeTestRule.onNodeWithTag("dismiss_status_banner_button").performClick()
        composeTestRule.waitForIdle()
        val overviewTopAfterDismiss = composeTestRule.onNodeWithTag("general_overview_section")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(overviewTopWithoutBanner, overviewTopAfterDismiss, 0.5f)
    }

    @Test
    fun `login screen auto-prefills saved account and supports quick chip switching and removal`() {
        val savedAccounts = listOf(
            SavedLoginAccount(
                identifier = "nv.tuyendung@hrpartner.vn",
                password = "Password#1",
                displayName = "Nhân Viên Tuyển Dụng",
                savedAtMillis = 2_000L
            ),
            SavedLoginAccount(
                identifier = "ql.duan@hrpartner.vn",
                password = "Password#2",
                displayName = "Quản Lý Dự Án",
                savedAtMillis = 1_000L
            )
        )
        var submittedTriple: Triple<String, String, Boolean>? = null
        var removedIdentifier: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                LoginScreen(
                    busy = false,
                    error = null,
                    serverUrl = "https://bi2.hrpartner.vn",
                    canConfigureServerUrl = false,
                    onUpdateServerUrl = {},
                    savedAccounts = savedAccounts,
                    onRemoveSavedAccount = { removedIdentifier = it },
                    onLoginSubmit = { id, pw, rem -> submittedTriple = Triple(id, pw, rem) }
                )
            }
        }

        // Brand heading on LoginScreen displays "HRP BI"
        composeTestRule.onNodeWithText("HRP BI").assertIsDisplayed()
        composeTestRule.onNodeWithText("HRPartner").assertDoesNotExist()

        // Most recent saved account is pre-filled automatically
        composeTestRule.onNodeWithTag("login_identifier_input")
            .assertTextContains("nv.tuyendung@hrpartner.vn")

        // Tap second saved account chip -> switches credentials
        composeTestRule.onNodeWithTag("saved_account_chip_ql.duan@hrpartner.vn").performClick()
        composeTestRule.onNodeWithTag("login_identifier_input")
            .assertTextContains("ql.duan@hrpartner.vn")

        composeTestRule.onNodeWithTag("login_submit_button").performClick()
        assertEquals(Triple("ql.duan@hrpartner.vn", "Password#2", true), submittedTriple)

        // Tap remove button on chip -> triggers removal callback
        composeTestRule.onNodeWithTag("remove_saved_account_ql.duan@hrpartner.vn").performClick()
        assertEquals("ql.duan@hrpartner.vn", removedIdentifier)
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun `worker scope buttons on narrow 360dp phone are all displayed simultaneously without horizontal scroll`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/direct-entry/catalog") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"projects":[],"recruiters":[],"labor_types":[]}""")
                    path.startsWith("/api/direct-entry/workers") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "managed",
                              "items": [],
                              "summary": {"total_workers": 154, "project_count": 3, "recruiter_count": 5},
                              "page_size": 25,
                              "has_more": false,
                              "next_cursor": null
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"items":[],"has_more":false,"next_cursor":null}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_wscope_${System.nanoTime()}.enc")
        val savedCredsFile = File(app.cacheDir, "ui_wscope_saved_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_wscope_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            savedCredentialsStorageFile = savedCredsFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, savedCredsFile, prefsFile)

        // Real backend Team Leader capabilities (no phantom "entry_read")
        val teamLeaderActor = AppActor(
            appUserId = "99999999-8888-4777-8666-555555555555",
            displayName = "Nguyễn Thành Vinh",
            capabilities = listOf(
                "entry_own",
                "entry_team",
                "entry_create",
                "submission_create",
                "entry_propose_change",
                "reporting_team",
                "reporting_duplicate_cccd"
            ),
            scopes = listOf(
                ActorScope(
                    kind = "team",
                    reference = "11111111-2222-4333-8444-555555555555",
                    validFrom = "2026-01-01",
                    validTo = null
                )
            ),
            selfRecruiterSuggestion = null,
            displayIdentifier = "vinhnt.td"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = teamLeaderActor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workerLoading.value && !vm.catalogLoading.value
        }
        composeTestRule.waitForIdle()

        val rootWidth = composeTestRule.onNodeWithTag("workers_lazy_column")
            .fetchSemanticsNode().boundsInRoot.right

        // All 4 scope buttons MUST be visible simultaneously inside the 360dp viewport with zero horizontal scroll
        val expectedTags = listOf(
            "worker_scope_tab_managed",
            "worker_scope_tab_recruited",
            "worker_scope_tab_team",
            "worker_scope_tab_uploader"
        )
        for (tag in expectedTags) {
            val node = composeTestRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Button $tag left (${bounds.left}) must be >= 0", bounds.left >= 0f)
            assertTrue(
                "Button $tag right (${bounds.right}) must fit within screen ($rootWidth) without scroll",
                bounds.right <= rootWidth + 1f
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun `team leader dashboard on 360dp phone loads real general overview auto-loads own team report without code input and renders compact duplicate cccd toolbar`() {
        val teamUuid = "11111111-2222-4333-8444-555555555555"
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "99999999-8888-4777-8666-555555555555",
                                "display_name": "Nguyễn Thành Vinh",
                                "capabilities": [
                                  "entry_own",
                                  "entry_team",
                                  "entry_create",
                                  "submission_create",
                                  "entry_propose_change",
                                  "reporting_team",
                                  "reporting_duplicate_cccd",
                                  "attendance_checkin"
                                ],
                                "scopes": [
                                  {
                                    "kind": "team",
                                    "reference": "$teamUuid",
                                    "valid_from": "2026-01-01",
                                    "valid_to": null
                                  }
                                ],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/catalog") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "catalog": {
                                "effective_date": "2026-10-23",
                                "projects": [
                                  {"project_id": "PRJ_LUX", "display_name": "Luxshare Bắc Giang"}
                                ],
                                "recruiters": [
                                  {
                                    "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee1",
                                    "display_name": "Nguyễn Thành Vinh",
                                    "personnel_code": "HRP101",
                                    "provider_type": "hrp",
                                    "team_id": "$teamUuid",
                                    "team_display_name": "Nhóm Vinh Tuyển Dụng",
                                    "label": "Nguyễn Thành Vinh (HRP101)"
                                  },
                                  {
                                    "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee2",
                                    "display_name": "Lê Tuyển Viên",
                                    "personnel_code": "HRP102",
                                    "provider_type": "hrp",
                                    "team_id": "$teamUuid",
                                    "team_display_name": "Nhóm Vinh Tuyển Dụng",
                                    "label": "Lê Tuyển Viên (HRP102)"
                                  }
                                ],
                                "banks": [],
                                "labor_types": [
                                  {"labor_type_key": "SEASONAL", "display_name": "Thời vụ", "sort_order": 10}
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    path.contains("scope=team") && path.contains("cursor=cur_page_2") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "team",
                              "authorization_date": "2026-10-23",
                              "items": [
                                {
                                  "entry_id": "33333333-3333-4333-8333-333333333333",
                                  "entry_version": 1,
                                  "submission_state": "SUBMITTED",
                                  "employee_code": "HRP0003",
                                  "display_name": "Phạm Văn Ba",
                                  "project_id": "PRJ_LUX",
                                  "project_display": "Luxshare Bắc Giang",
                                  "first_work_date": "2026-10-21",
                                  "labor_type": "SEASONAL",
                                  "labor_type_display": "Thời vụ",
                                  "employment_status": "UNCONFIRMED",
                                  "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee2",
                                  "recruiter_display": "Lê Tuyển Viên",
                                  "allowed_actions": {"view": true}
                                }
                              ],
                              "summary": {"total_workers": 3, "project_count": 1, "recruiter_count": 2},
                              "page_size": 100,
                              "has_more": false,
                              "next_cursor": null
                            }
                            """.trimIndent()
                        )
                    path.contains("scope=team") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "team",
                              "authorization_date": "2026-10-23",
                              "items": [
                                {
                                  "entry_id": "11111111-1111-4111-8111-111111111111",
                                  "entry_version": 1,
                                  "submission_state": "SUBMITTED",
                                  "employee_code": "HRP0001",
                                  "display_name": "Nguyễn Văn Một",
                                  "project_id": "PRJ_LUX",
                                  "project_display": "Luxshare Bắc Giang",
                                  "first_work_date": "2026-10-20",
                                  "labor_type": "SEASONAL",
                                  "labor_type_display": "Thời vụ",
                                  "employment_status": "ON",
                                  "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee1",
                                  "recruiter_display": "Nguyễn Thành Vinh",
                                  "allowed_actions": {"view": true}
                                },
                                {
                                  "entry_id": "22222222-2222-4222-8222-222222222222",
                                  "entry_version": 1,
                                  "submission_state": "SUBMITTED",
                                  "employee_code": "HRP0002",
                                  "display_name": "Trần Thị Hai",
                                  "project_id": "PRJ_LUX",
                                  "project_display": "Luxshare Bắc Giang",
                                  "first_work_date": "2026-10-20",
                                  "labor_type": "SEASONAL",
                                  "labor_type_display": "Thời vụ",
                                  "employment_status": "ON",
                                  "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee1",
                                  "recruiter_display": "Nguyễn Thành Vinh",
                                  "allowed_actions": {"view": true}
                                }
                              ],
                              "summary": {"total_workers": 3, "project_count": 1, "recruiter_count": 2},
                              "page_size": 100,
                              "has_more": true,
                              "next_cursor": "cur_page_2"
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/workers") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "managed",
                              "authorization_date": "2026-10-23",
                              "items": [],
                              "summary": {"total_workers": 0, "project_count": 0, "recruiter_count": 0},
                              "page_size": 100,
                              "has_more": false,
                              "next_cursor": null
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/duplicate-cccd-report") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "page": 1,
                              "page_size": 25,
                              "total_groups": 30,
                              "total_entries": 60,
                              "has_more": true,
                              "authorization_date": "2026-10-23",
                              "groups": [
                                {
                                  "cccd_last4": "6147",
                                  "cccd_length": 12,
                                  "profile_count": 2,
                                  "project_count": 2,
                                  "active_profile_count": 1,
                                  "first_work_date": "2026-10-14",
                                  "latest_first_work_date": "2026-10-21",
                                  "episodes": [
                                    {
                                      "entry_id": "77777777-7777-4777-8777-777777777777",
                                      "entry_version": 1,
                                      "employee_code": "HRP26100181",
                                      "display_name": "Trần Văn Trùng",
                                      "project_display": "Samkwang Bắc Giang",
                                      "first_work_date": "2026-10-21",
                                      "latest_status": "UNCONFIRMED",
                                      "active": false,
                                      "can_mark_off": false,
                                      "can_self_withdraw": false
                                    }
                                  ]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"data":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_tl_dash_${System.nanoTime()}.enc")
        val savedCredsFile = File(app.cacheDir, "ui_tl_saved_${System.nanoTime()}.enc")
        val anchorFile = File(app.cacheDir, "ui_tl_anchor_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_tl_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            timeAnchorStorageFile = anchorFile,
            savedCredentialsStorageFile = savedCredsFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        repo.cookieJar.saveFromResponse(
            server.url("/api/auth/session"),
            listOf(
                Cookie.Builder()
                    .name("mini_bi_sid")
                    .value("valid_tl_sid")
                    .hostOnlyDomain(server.hostName)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 3_600_000L)
                    .build()
            )
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, savedCredsFile, anchorFile, prefsFile)

        composeTestRule.setContent {
            MyApplicationTheme {
                MiniBiAppRoot(viewModel = vm)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.sessionBootstrapping.value &&
                vm.generalOverview.value != null &&
                vm.teamReport.value != null &&
                vm.duplicateGroups.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 1. All 3 Dashboard tabs fit on 360dp screen simultaneously without horizontal scroll
        val dashWidth = composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .fetchSemanticsNode().boundsInRoot.right
        for (idx in 0..2) {
            val tabNode = composeTestRule.onNodeWithTag("dashboard_tab_$idx")
            tabNode.assertIsDisplayed()
            assertTrue(tabNode.fetchSemanticsNode().boundsInRoot.right <= dashWidth + 1f)
        }

        // 2. General Overview has real paginated counts (3 total, 2 ON, 1 UNCONFIRMED), NOT 0 NLĐ!
        val overview = vm.generalOverview.value!!
        assertEquals(3, overview.totalProfiles)
        assertEquals(2, overview.workingCount)
        assertEquals(1, overview.waitingCount)
        assertEquals(0, overview.leftCount)
        assertEquals(2, overview.activeHiringDays)
        assertEquals("Nhóm Vinh Tuyển Dụng", overview.scopeDescription)
        composeTestRule.onNodeWithTag("general_overview_section").assertIsDisplayed()

        // 3. Switch to "Báo cáo theo nhóm" -> auto-displays Team Leader's own team report with ZERO manual code input!
        composeTestRule.onNodeWithTag("dashboard_tab_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Mã nhóm tuyển dụng").assertDoesNotExist()
        composeTestRule.onNodeWithTag("team_bucket_picker_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("team_report_detail_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nhóm Vinh Tuyển Dụng").assertIsDisplayed()
        assertEquals(3, vm.teamReport.value!!.totalRecruited)
        assertEquals(2, vm.teamReport.value!!.activeRecruiterCount)

        // Attempting to load another team code fails closed for non-admin Team Leader
        val ownSnapshotBefore = vm.teamReport.value
        composeTestRule.runOnIdle { vm.loadTeamReport("OTHER_TEAM_CODE") }
        composeTestRule.waitForIdle()
        assertEquals("Bạn chỉ được xem báo cáo của nhóm mình quản lý.", vm.teamReportError.value)
        composeTestRule.runOnIdle { vm.loadTeamReport(ownSnapshotBefore!!.bucketKey) }
        composeTestRule.waitForIdle()
        assertNull(vm.teamReportError.value)
        assertEquals("Nhóm Vinh Tuyển Dụng", vm.teamReport.value?.bucketDisplay)

        // 4. Switch to "Cảnh báo CCCD trùng" -> compact toolbar, zero vertical button stretching / blank gap!
        composeTestRule.onNodeWithTag("dashboard_tab_2").performClick()
        composeTestRule.waitForIdle()

        val toolbarBounds = composeTestRule.onNodeWithTag("duplicate_cccd_toolbar")
            .fetchSemanticsNode().boundsInRoot
        val density = composeTestRule.density.density
        val toolbarHeightDp = (toolbarBounds.bottom - toolbarBounds.top) / density
        assertTrue(
            "Duplicate CCCD toolbar height ($toolbarHeightDp dp) must be compact (< 130dp), not vertically stretched",
            toolbarHeightDp < 130f
        )

        val firstGroupTop = composeTestRule.onNodeWithText("CCCD đuôi ***6147 (2 hồ sơ)")
            .fetchSemanticsNode().boundsInRoot.top
        val gapBetweenToolbarAndGroupTextDp = (firstGroupTop - toolbarBounds.bottom) / density
        assertTrue(
            "Gap between toolbar and first duplicate card title ($gapBetweenToolbarAndGroupTextDp dp) must be compact (< 40dp)",
            gapBetweenToolbarAndGroupTextDp < 40f
        )
    }
}
