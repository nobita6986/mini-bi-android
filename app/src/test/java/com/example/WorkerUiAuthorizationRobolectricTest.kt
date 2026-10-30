package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActorScope
import com.example.data.AppActor
import com.example.data.AttendanceEntity
import com.example.data.AttendanceOutboxStatus
import com.example.data.AttendanceTimeSource
import com.example.data.DashboardDatePreset
import com.example.data.DirectEntryContract
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.SavedLoginAccount
import com.example.data.WorkReportFieldSpec
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportSectionSpec
import com.example.data.WorkReportsContract
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.ui.MiniBiViewModel
import com.example.ui.screens.AttendanceHistoryCard
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DirectEntryNewWorkersScreen
import com.example.ui.screens.DynamicWorkReportFormCard
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileAndPhotoSyncStatusCards
import com.example.ui.screens.WorkerOperationsRowCard
import com.example.ui.screens.WorkerPrivilegedEditDialog
import com.example.ui.screens.WorkerProposeChangeDialog
import com.example.ui.screens.WorkersAndDirectEntryScreen
import com.example.ui.screens.formatIsoToVietnamDateTime
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
import kotlin.math.abs

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
    // Item 11: Worker CTA chỉ xuất hiện theo allowed_actions (bỏ nút Đề xuất thay đổi trên thẻ)
    // -------------------------------------------------------------------------
    @Test
    fun `item 11 - worker CTAs appear strictly when server allowed_actions flags are true`() {
        val idNone = "11111111-1111-4111-8111-111111111111"
        val idProposeOnly = "22222222-2222-4222-8222-222222222222"
        val idDirectOnly = "33333333-3333-4333-8333-333333333333"
        val idBoth = "44444444-4444-4444-8444-444444444444"

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
                    WorkerOperationsRowCard(
                        worker = sampleWorker(idBoth, canView = true, canProposeChange = true, canDirectEdit = true),
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

        // Row 2: propose_change=true, direct_edit=false -> "Đề xuất thay đổi" button is removed from row card
        composeTestRule.onNodeWithTag("worker_detail_$idProposeOnly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_propose_$idProposeOnly").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_direct_edit_$idProposeOnly").assertDoesNotExist()

        // Row 3: view=false, propose_change=false, direct_edit=true
        composeTestRule.onNodeWithTag("worker_detail_$idDirectOnly").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_propose_$idDirectOnly").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_direct_edit_$idDirectOnly").assertIsDisplayed()

        // Row 4: view=true, propose_change=true, direct_edit=true -> only Xem chi tiết + Sửa trực tiếp appear
        composeTestRule.onNodeWithTag("worker_detail_$idBoth").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_propose_$idBoth").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_direct_edit_$idBoth").assertIsDisplayed()
        composeTestRule.onNodeWithText("Đề xuất thay đổi").assertDoesNotExist()

        // Brand badge helper distinguishes BI vs BI2 cleanly
        assertEquals("BI", resolveBrandBadgeLabel("https://bi.hrpartner.vn"))
        assertEquals("BI2", resolveBrandBadgeLabel("https://bi2.hrpartner.vn"))
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
        assertEquals(listOf("DA tôi quản lý", "Tôi tuyển"), realTabs.map { it.label })

        // Full-scope admin actor: ZERO "NLĐ" substring in any top scope tab label
        val fullAdminActor = AppActor(
            appUserId = "88888888-2222-4333-8444-555555555555",
            displayName = "Quản trị HRP",
            capabilities = listOf("entry_admin", "submission_read"),
            scopes = listOf(
                ActorScope("own", "self", "2026-01-01", null),
                ActorScope("team", "TEAM_NORTH", "2026-01-01", null),
                ActorScope("all", "ALL", "2026-01-01", null)
            ),
            selfRecruiterSuggestion = null
        )
        val allTabLabels = resolveVisibleWorkerTabs(fullAdminActor).map { it.label }
        assertEquals(
            listOf("DA tôi quản lý", "Tôi tuyển", "Team tôi quản lý", "Tôi đã nhập", "Toàn hệ thống"),
            allTabLabels
        )
        assertTrue(allTabLabels.none { it.contains("NLĐ", ignoreCase = true) })
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

        val activeMenu = androidx.compose.runtime.mutableStateOf("NEW_ENTRY")
        composeTestRule.setContent {
            MyApplicationTheme {
                if (activeMenu.value == "NEW_ENTRY") {
                    DirectEntryNewWorkersScreen(
                        actor = actor,
                        viewModel = vm,
                        onNavigateToWorkersList = {},
                        onBackToDashboard = {}
                    )
                } else {
                    androidx.compose.material3.Text("Màn hình menu khác")
                }
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

        val activeMenu = androidx.compose.runtime.mutableStateOf("NEW_ENTRY")
        composeTestRule.setContent {
            MyApplicationTheme {
                if (activeMenu.value == "NEW_ENTRY") {
                    DirectEntryNewWorkersScreen(
                        actor = actor,
                        viewModel = vm,
                        onNavigateToWorkersList = {},
                        onBackToDashboard = {}
                    )
                } else {
                    androidx.compose.material3.Text("Màn hình menu khác")
                }
            }
        }

        // Wait for catalog to load and default "Thời vụ" to populate
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            vm.catalog.value != null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Thời vụ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nam").assertDoesNotExist()
        composeTestRule.onNodeWithTag("row_1_scan_qr_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_WORKER_PORTRAIT_camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_WORKER_PORTRAIT_album").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_CCCD_FRONT_camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_CCCD_FRONT_album").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_CCCD_BACK_camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_CCCD_BACK_album").assertIsDisplayed()
        composeTestRule.onNodeWithTag("row_1_slot_EMPLOYMENT_CONTRACT_file").assertIsDisplayed()

        // Open gender dropdown -> allows "— Để trống —", "Nam", and "Nữ"
        composeTestRule.onNodeWithTag("row_1_gender").performClick()
        composeTestRule.onNodeWithText("— Để trống —").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nam").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nữ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nữ").performClick()
        composeTestRule.onNodeWithText("Nữ").assertIsDisplayed()

        // Type worker name
        composeTestRule.onNodeWithTag("row_1_full_name").performTextInput("Lê Văn Thử Nghiệm")
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertIsDisplayed()

        // Switch to another bottom-nav menu and return to "Cấp mới" -> in-progress draft data must remain intact
        composeTestRule.runOnIdle { activeMenu.value = "OTHER_MENU" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Màn hình menu khác").assertIsDisplayed()
        composeTestRule.runOnIdle { activeMenu.value = "NEW_ENTRY" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nữ").assertIsDisplayed()

        // Click "Làm mới dòng này" -> confirmation dialog must appear first, preserving data if cancelled
        composeTestRule.onNodeWithTag("row_1_reset_or_remove_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_reset_worker_row_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertIsDisplayed()

        // Click "Làm mới dòng này" -> confirm "Đồng ý" -> clears row and restores blank gender + "Thời vụ"
        composeTestRule.onNodeWithTag("row_1_reset_or_remove_button").performClick()
        composeTestRule.onNodeWithTag("confirm_reset_worker_row_button").performClick()
        composeTestRule.onNodeWithText("Lê Văn Thử Nghiệm").assertDoesNotExist()
        composeTestRule.onNodeWithText("Nam").assertDoesNotExist()
        composeTestRule.onNodeWithText("Nữ").assertDoesNotExist()
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

        val activeMenu = androidx.compose.runtime.mutableStateOf("NEW_ENTRY")
        composeTestRule.setContent {
            MyApplicationTheme {
                if (activeMenu.value == "NEW_ENTRY") {
                    DirectEntryNewWorkersScreen(
                        actor = actor,
                        viewModel = vm,
                        onNavigateToWorkersList = {},
                        onBackToDashboard = {}
                    )
                } else {
                    androidx.compose.material3.Text("Màn hình menu khác")
                }
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
        assertEquals("Báo cáo", com.example.ui.MainDestination.WORK_REPORTS.label)
        composeTestRule.onNodeWithTag("bottom_nav_work_reports").assertIsDisplayed()

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
                    savedAccounts = savedAccounts,
                    onRemoveSavedAccount = { removedIdentifier = it },
                    onLoginSubmit = { id, pw, rem -> submittedTriple = Triple(id, pw, rem) }
                )
            }
        }

        // Brand heading on LoginScreen displays "HRP BI" and hides server URL configuration UI
        composeTestRule.onNodeWithText("HRP BI").assertIsDisplayed()
        composeTestRule.onNodeWithText("HRPartner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toggle_debug_server_url_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("debug_server_url_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_debug_server_url_button").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cấu hình URL máy chủ", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Server Base URL", substring = true).assertDoesNotExist()

        // Most recent saved account is pre-filled automatically
        composeTestRule.onNodeWithTag("login_identifier_input")
            .assertTextContains("nv.tuyendung@hrpartner.vn")

        // Tap second saved account chip -> switches credentials
        composeTestRule.onNodeWithTag("saved_account_chip_ql.duan@hrpartner.vn").performClick()
        composeTestRule.onNodeWithTag("login_identifier_input")
            .assertTextContains("ql.duan@hrpartner.vn")

        composeTestRule.onNodeWithTag("login_submit_button").performClick()
        assertEquals(Triple("ql.duan@hrpartner.vn", "Password#2", true), submittedTriple)

        // Login form is balanced below status bar (logo top ~56dp) and submit button sits above keyboard zone
        val logoTopDp = with(composeTestRule.density) {
            composeTestRule.onNodeWithTag("login_brand_logo").fetchSemanticsNode().boundsInRoot.top.toDp().value
        }
        val submitBottomDp = with(composeTestRule.density) {
            composeTestRule.onNodeWithTag("login_submit_button").fetchSemanticsNode().boundsInRoot.bottom.toDp().value
        }
        assertTrue("Expected login_brand_logo top ($logoTopDp dp) in 40..90dp for balanced layout", logoTopDp in 40f..90f)
        assertTrue("Expected login_submit_button bottom ($submitBottomDp dp) <= 750dp above keyboard zone", submitBottomDp <= 750f)

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

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `personnel scope buttons omit NLD with uniform height and attendance rejected card shows friendly retry UX`() {
        var retriedCount = 0
        val legacyRejectedRecord = AttendanceEntity(
            id = 42L,
            idempotencyKey = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            appUserId = "11111111-2222-4333-8444-555555555555",
            userDisplayName = "vinhnt.td",
            latitude = 21.27250,
            longitude = 105.65334,
            accuracyMeters = 13.0f,
            addressHint = "7MC3+X9J, Bình Nguyên, Phú Thọ, Việt Nam",
            photoFilePath = "/data/user/0/app/files/attendance_photos/checkin_1.jpg",
            photoUri = "content://test/checkin_1.jpg",
            photoSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            clientCapturedAtIso = "2026-10-24T01:21:37.318+07:00",
            epochMillis = 1792779697318L,
            timeSource = AttendanceTimeSource.SERVER_ANCHORED,
            timeToken = "at1.sample.token",
            elapsedSinceAnchorMs = 1500L,
            note = "",
            syncStatus = AttendanceOutboxStatus.REJECTED,
            statusMessage = "Dữ liệu gửi lên không thỏa điều kiện nghiệp vụ.",
            backendErrorCode = "PHOTO_INVALID",
            httpStatusCode = 422
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceHistoryCard(
                    record = legacyRejectedRecord,
                    serverSequenceInDay = null,
                    expanded = true,
                    onClick = {},
                    onRetryCaptureClick = { retriedCount++ }
                )
            }
        }

        // Legacy technical message & English "(REJECTED)" are replaced by friendly Vietnamese + retry button
        composeTestRule.onNodeWithText("Dữ liệu gửi lên không thỏa điều kiện nghiệp vụ.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Chi tiết lượt chấm công (REJECTED):").assertDoesNotExist()
        composeTestRule.onNodeWithText("Chưa thành công").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Ảnh chụp từ camera chưa đúng chuẩn hệ thống. Vui lòng bấm Chấm công để chụp lại ảnh và thử lại."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_checkin_card_button_42").assertIsDisplayed().performClick()
        assertEquals(1, retriedCount)

        // UTC server timestamp converts cleanly to Vietnam local time (+07:00)
        assertEquals("2026-10-24 01:11:04", formatIsoToVietnamDateTime("2026-10-23T18:11:04.125Z"))
    }

    @Test
    @Config(qualifiers = "w392dp-h900dp-xxhdpi")
    fun `attendance screen omits duplicate username under header and centers enlarged camera icon between button edge and text`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"ok":false,"code":"NOT_FOUND"}""")
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_att_hdr_${System.nanoTime()}.enc")
        val savedCredsFile = File(app.cacheDir, "ui_att_saved_${System.nanoTime()}.enc")
        val anchorFile = File(app.cacheDir, "ui_att_anchor_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_att_prefs_${System.nanoTime()}.preferences_pb")
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
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, savedCredsFile, anchorFile, prefsFile)

        val actor = AppActor(
            appUserId = "11111111-2222-4333-8444-555555555555",
            displayName = "nobita6986",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceScreen(
                    actor = actor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Header shows "Ghi nhận Chấm công" without repeating actor.displayName ("nobita6986") underneath
        composeTestRule.onNodeWithText("Ghi nhận Chấm công").assertIsDisplayed()
        composeTestRule.onNodeWithText("nobita6986").assertDoesNotExist()

        // 2. Camera icon inside primary button is enlarged (~32dp) and centered midway between button left edge and centered label
        val density = composeTestRule.density.density
        val buttonBounds = composeTestRule.onNodeWithTag("primary_checkin_camera_button")
            .fetchSemanticsNode().boundsInRoot
        val iconBounds = composeTestRule.onNodeWithTag("primary_checkin_camera_icon", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val labelBounds = composeTestRule.onNodeWithTag("primary_checkin_camera_label", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        val iconWidthDp = (iconBounds.right - iconBounds.left) / density
        assertTrue("Expected enlarged camera icon (>= 30dp), got ${iconWidthDp}dp", iconWidthDp >= 30f)

        val gapFromButtonEdgePx = iconBounds.left - buttonBounds.left
        val gapToLabelPx = labelBounds.left - iconBounds.right
        assertTrue("Expected positive balanced gap (> 10px), got $gapFromButtonEdgePx", gapFromButtonEdgePx > 10f)
        assertTrue(
            "Expected camera icon centered midway between button left edge ($gapFromButtonEdgePx px) and text ($gapToLabelPx px)",
            abs(gapFromButtonEdgePx - gapToLabelPx) <= 2.5f
        )

        val buttonCenterX = (buttonBounds.left + buttonBounds.right) / 2f
        val labelCenterX = (labelBounds.left + labelBounds.right) / 2f
        assertTrue(
            "Expected button text horizontally centered (buttonCenter=$buttonCenterX, labelCenter=$labelCenterX)",
            abs(buttonCenterX - labelCenterX) <= 2.5f
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `all scope dashboard shows BC chuyen sau tab with bottom filter bar and loads lowercased wf04 team report without INVALID_FILTER`() {
        val recordedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                recordedPaths.add(path)
                val url = request.requestUrl
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
                                "display_name": "nobita6986",
                                "capabilities": [
                                  "entry_own",
                                  "entry_team",
                                  "catalog_master_manage",
                                  "reporting_team",
                                  "reporting_duplicate_cccd",
                                  "report_analytics_view"
                                ],
                                "scopes": [
                                  {
                                    "kind": "all",
                                    "reference": "all",
                                    "valid_from": "2026-01-01",
                                    "valid_to": null
                                  }
                                ],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/admin/catalog/teams") -> MockResponse()
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
                                  {"team_id": "t4", "code": "TEAM_4", "display_name": "Team 4", "active": true, "version": 1, "revision_count": 1}
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/reporting/workforce-deep") -> {
                        val hasDateFilter = url?.queryParameter("from") != null && url.queryParameter("to") != null
                        val balanceJson = if (hasDateFilter) {
                            """
                            {
                              "applicable": true,
                              "newEpisodesInPeriod": {"total": 42},
                              "byStatus": {
                                "on": {"totalInflowCount": 38, "netChange": 26},
                                "off": {"totalInflowCount": 12}
                              }
                            }
                            """.trimIndent()
                        } else {
                            """{"applicable": false}"""
                        }
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "workforce": {
                                    "status": "ready",
                                    "snapshot": {
                                      "asOfDate": "2026-10-24",
                                      "eligibleEpisodeTotal": 1810,
                                      "onCount": 1497,
                                      "unconfirmedCount": 0,
                                      "offCount": 313,
                                      "unknownCount": 0,
                                      "futureEpisodeCount": 0
                                    },
                                    "waiting": {"placementRateOfWaiting": 0.0},
                                    "retention": {"checkpoints": {"d7": {"retentionRate": 0.91}, "d30": {"retentionRate": 0.84}}},
                                    "duration": {"allEverOn": {"avgOnDaysPerEpisode": 45.2}},
                                    "balance": $balanceJson,
                                    "dimensions": {
                                      "byTeam": {
                                        "eligibleEpisodeTotal": 1810,
                                        "buckets": [
                                          {"key": "__system_vendor__", "display": "Vendor", "eligibleEpisodeCount": 702, "onCount": 600, "unconfirmedCount": 0, "offCount": 102},
                                          {"key": "team_1", "display": "Team 1", "eligibleEpisodeCount": 271, "onCount": 230, "unconfirmedCount": 0, "offCount": 41},
                                          {"key": "team_4", "display": "Team 4", "eligibleEpisodeCount": 215, "onCount": 180, "unconfirmedCount": 0, "offCount": 35}
                                        ]
                                      },
                                      "byProject": {
                                        "eligibleEpisodeTotal": 1810,
                                        "buckets": [
                                          {"key": "prj_1", "display": "Luxshare Bắc Giang", "eligibleEpisodeCount": 520}
                                        ],
                                        "pagination": {"totalCount": 12, "hasMore": false, "nextCursor": null}
                                      },
                                      "byRecruiter": {
                                        "eligibleEpisodeTotal": 1810,
                                        "buckets": [
                                          {"key": "rec_1", "display": "Nguyễn Tuyển", "eligibleEpisodeCount": 95}
                                        ]
                                      },
                                      "byProvider": {
                                        "eligibleEpisodeTotal": 1810,
                                        "buckets": [
                                          {"key": "hrp", "display": "HRP", "eligibleEpisodeCount": 1108},
                                          {"key": "vendor", "display": "Vendor", "eligibleEpisodeCount": 702}
                                        ]
                                      }
                                    },
                                    "leaveReasons": {"byCategory": []}
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/reporting/team-report") -> {
                        val bKey = url?.queryParameter("bucket_key").orEmpty()
                        val hasDateFilter = url?.queryParameter("from") != null
                        val expectedTeam1Key = WorkReportsContract.encodeTeamBucketKey("TEAM_1")
                        val expectedVendorKey = WorkReportsContract.encodeTeamBucketKey("__system_vendor__")
                        // Strict backend behavior: lowercase team_1 -> 400 INVALID_FILTER; TEAM_4 in filtered period (0 hires) -> 400 INVALID_FILTER
                        when {
                            bKey == expectedTeam1Key -> MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {
                                      "bucket": {"key": "$expectedTeam1Key", "display": "Team 1"},
                                      "report": {
                                        "recruitedTotal": ${if (hasDateFilter) 18 else 271},
                                        "byRecruiter": {"r1": {"key": "r1", "display": "Nguyễn Tuyển", "recruitedCount": ${if (hasDateFilter) 18 else 271}}},
                                        "byProject": {"p1": {"key": "p1", "display": "Luxshare Bắc Giang", "recruitedCount": ${if (hasDateFilter) 18 else 271}}},
                                        "byDate": {"2026-10-20": ${if (hasDateFilter) 18 else 271}}
                                      },
                                      "detail": {"total": ${if (hasDateFilter) 18 else 271}, "rows": [], "nextCursor": null}
                                    }
                                    """.trimIndent()
                                )
                            bKey == expectedVendorKey -> MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {
                                      "bucket": {"key": "$expectedVendorKey", "display": "Vendor"},
                                      "report": {
                                        "recruitedTotal": ${if (hasDateFilter) 24 else 702},
                                        "byRecruiter": {"rv": {"key": "rv", "display": "Vendor Đại Lý", "recruitedCount": ${if (hasDateFilter) 24 else 702}}},
                                        "byProject": {"p2": {"key": "p2", "display": "Goertek Bắc Ninh", "recruitedCount": ${if (hasDateFilter) 24 else 702}}},
                                        "byProvider": {"vendor": {"key": "vendor", "display": "Vendor", "recruitedCount": ${if (hasDateFilter) 24 else 702}}},
                                        "byDate": {"2026-10-21": ${if (hasDateFilter) 24 else 702}}
                                      },
                                      "detail": {"total": ${if (hasDateFilter) 24 else 702}, "rows": [], "nextCursor": null}
                                    }
                                    """.trimIndent()
                                )
                            else -> MockResponse()
                                .setResponseCode(400)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":false,"code":"INVALID_FILTER"}""")
                        }
                    }
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"groups":[],"total_groups":0,"total_entries":0,"page":1,"page_size":25,"has_more":false,"authorization_date":"2026-10-24","data":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_all_dash_${System.nanoTime()}.enc")
        val savedCredsFile = File(app.cacheDir, "ui_all_saved_${System.nanoTime()}.enc")
        val anchorFile = File(app.cacheDir, "ui_all_anchor_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_all_prefs_${System.nanoTime()}.preferences_pb")
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
                    .value("valid_all_sid")
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
                !vm.teamReportLoading.value
        }
        composeTestRule.waitForIdle()

        // 1. Tab 1 uses short label "BC chuyên sâu", Tab 3 uses "Báo cáo CCCD trùng" (without count suffix), and banner timeout is 5s
        assertEquals(5_000L, MiniBiViewModel.BANNER_AUTO_DISMISS_MS)
        composeTestRule.onNodeWithText("BC chuyên sâu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Báo cáo chuyên sâu").assertDoesNotExist()
        composeTestRule.onNodeWithText("Báo cáo CCCD trùng").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cảnh báo CCCD trùng", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Tỷ lệ đang làm: 82.7%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tỷ lệ vào làm", substring = true).assertDoesNotExist()
        val tab0Bounds = composeTestRule.onNodeWithTag("dashboard_tab_0").fetchSemanticsNode().boundsInRoot
        val tab1Bounds = composeTestRule.onNodeWithTag("dashboard_tab_1").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Top row tabs must have uniform height (tab0=${tab0Bounds.height}, tab1=${tab1Bounds.height})",
            abs(tab0Bounds.height - tab1Bounds.height) <= 1.5f
        )

        // 2. Refresh button + simple time filter presets sit BELOW the tab grid and fit within 360dp width without scroll
        val dashRight = composeTestRule.onNodeWithTag("dashboard_lazy_column").fetchSemanticsNode().boundsInRoot.right
        val tabGridBottom = composeTestRule.onNodeWithTag("dashboard_tab_grid").fetchSemanticsNode().boundsInRoot.bottom
        val filterBarBounds = composeTestRule.onNodeWithTag("dashboard_filter_bar").fetchSemanticsNode().boundsInRoot
        val refreshBounds = composeTestRule.onNodeWithTag("dashboard_refresh_button").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Filter & refresh bar (top=${filterBarBounds.top}) must sit below tab grid (bottom=$tabGridBottom)",
            filterBarBounds.top >= tabGridBottom - 1f
        )
        assertTrue("Refresh button must fit inside 360dp viewport", refreshBounds.right <= dashRight + 1f)
        for (preset in DashboardDatePreset.entries) {
            val chipBounds = composeTestRule.onNodeWithTag("dashboard_date_preset_${preset.key}")
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Preset ${preset.label} must fit inside 360dp viewport", chipBounds.right <= dashRight + 1f)
        }

        // 3. Project count uses pagination.totalCount (12), not pageSize (1)
        assertEquals(12, vm.generalOverview.value?.projectCount)

        // 4. Switch to "Báo cáo theo nhóm" -> auto-loads Team 1 via canonical TEAM_1 code with zero INVALID_FILTER error
        composeTestRule.onNodeWithTag("dashboard_tab_2").performClick()
        composeTestRule.waitForIdle()
        assertNull(vm.teamReportError.value)
        assertEquals("Team 1", vm.teamReport.value?.bucketDisplay)
        assertEquals(271, vm.teamReport.value?.totalRecruited)
        composeTestRule.onNodeWithTag("team_report_detail_card").assertIsDisplayed()

        // 5. Select "Tháng này" -> sends from & to to reporting endpoints and updates Team 1 + handles 0-hire Team 4 gracefully
        composeTestRule.onNodeWithTag("dashboard_date_preset_this-month").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.dashboardDatePreset.value == DashboardDatePreset.THIS_MONTH &&
                !vm.workforceLoading.value &&
                !vm.teamReportLoading.value &&
                vm.teamReport.value?.totalRecruited == 18
        }
        composeTestRule.waitForIdle()
        assertEquals(42, vm.generalOverview.value?.periodNewEpisodes)
        assertEquals(true, vm.generalOverview.value?.isPeriodFiltered)
        assertEquals(42, vm.generalOverview.value?.totalProfiles)
        assertEquals(38, vm.generalOverview.value?.workingCount)
        assertEquals(12, vm.generalOverview.value?.leftCount)
        assertEquals(2, vm.generalOverview.value?.projectCount)
        assertEquals(2, vm.generalOverview.value?.recruiterCount)
        assertEquals(2, vm.generalOverview.value?.activeHiringDays)
        assertTrue(recordedPaths.any { it.startsWith("/api/reporting/workforce-deep") && it.contains("from=") && it.contains("to=") })

        // Tapping Team 4 (0 new hires in Tháng này -> backend 400 INVALID_FILTER) renders 0-recruited Team 4 instead of error card
        composeTestRule.onNodeWithTag("team_bucket_chip_TEAM_4").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.teamReportLoading.value && vm.teamReport.value?.bucketDisplay == "Team 4"
        }
        composeTestRule.waitForIdle()
        assertNull(vm.teamReportError.value)
        assertEquals(0, vm.teamReport.value?.totalRecruited)
        assertEquals(180, vm.teamReport.value?.workingCount)

        // Switching back to Tab 0 ("Báo cáo tổng quát") renders period-filtered KPI cards instead of frozen cumulative 1810
        composeTestRule.onNodeWithTag("dashboard_tab_0").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("TUYỂN MỚI TRONG KỲ").assertIsDisplayed()
        composeTestRule.onNodeWithText("VÀO LÀM TRONG KỲ").assertIsDisplayed()
        composeTestRule.onNodeWithText("NGHỈ VIỆC TRONG KỲ").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `daily work report form keeps boolean switches separated from text and stacks action buttons cleanly on 360dp`() {
        val obligation = WorkReportObligationEntity(
            obligationId = "ob-daily-1",
            templateId = "tpl-daily",
            templateCode = "DAILY_REC",
            templateName = "Báo cáo công việc hằng ngày",
            periodType = "DAILY",
            periodKey = "2026-10-24",
            periodStartDate = "2026-10-24",
            periodEndDate = "2026-10-24",
            dueAtIso = "2026-10-24T10:00:00Z",
            isMandatory = true,
            obligationStatus = "ACTIVE",
            submissionId = null,
            submissionState = "NOT_STARTED",
            submissionVersion = null,
            obligationVersion = 1,
            schemaJsonRaw = "{}",
            payloadJsonRaw = "{}",
            latestReviewDecision = null,
            latestReviewReason = null,
            submittedAtIso = null
        )
        val sections = listOf(
            WorkReportSectionSpec(
                sectionKey = "s1",
                label = "7h30-9h30",
                description = null,
                sortOrder = 1,
                fields = listOf(
                    WorkReportFieldSpec(
                        fieldKey = "f1",
                        sectionKey = "s1",
                        sortOrder = 1,
                        label = "Hỗ trợ nhân viên quản lý đón và tập trung lao động",
                        description = null,
                        fieldType = "boolean",
                        required = true,
                        defaultValue = "false"
                    ),
                    WorkReportFieldSpec(
                        fieldKey = "f2",
                        sectionKey = "s1",
                        sortOrder = 2,
                        label = "Live 1H quá trình đón lao động, phổ biến và phỏng vấn người lao động",
                        description = null,
                        fieldType = "boolean",
                        required = true,
                        defaultValue = "false"
                    )
                )
            ),
            WorkReportSectionSpec(
                sectionKey = "s2",
                label = "16h00-17h00",
                description = null,
                sortOrder = 2,
                fields = listOf(
                    WorkReportFieldSpec(
                        fieldKey = "f3",
                        sectionKey = "s2",
                        sortOrder = 1,
                        label = "Vào link Báo cáo công việc hàng ngày; tổng hợp danh sách lao động hẹn PV ngày mai.",
                        description = null,
                        fieldType = "boolean",
                        required = true,
                        defaultValue = "true"
                    )
                )
            )
        )

        var savedDraftPayload: JSONObject? = null
        var submittedFinalPayload: JSONObject? = null
        var cancelled = false

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    DynamicWorkReportFormCard(
                        obligation = obligation,
                        sections = sections,
                        canSubmit = true,
                        onCancel = { cancelled = true },
                        onSaveDraft = { savedDraftPayload = it },
                        onSubmitFinal = { submittedFinalPayload = it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val cardBounds = composeTestRule.onNodeWithTag("dynamic_work_report_form_card")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        // 1. Every boolean Switch strictly stays to the right of its text label (zero overlap) and inside the card border
        for (key in listOf("f1", "f2", "f3")) {
            val labelBounds = composeTestRule.onNodeWithTag("dynamic_label_$key", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val switchBounds = composeTestRule.onNodeWithTag("dynamic_field_$key", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue("Expected non-zero label width for $key", labelBounds.width > 50f)
            assertTrue("Expected non-zero switch width for $key", switchBounds.width > 30f)
            assertTrue(
                "Switch $key (left=${switchBounds.left}) must not overlap label (right=${labelBounds.right})",
                switchBounds.left >= labelBounds.right + 4f
            )
            assertTrue(
                "Switch $key (right=${switchBounds.right}) must fit inside form card (right=${cardBounds.right})",
                switchBounds.right <= cardBounds.right - 8f
            )
        }

        // 2. Action buttons are stacked cleanly across 2 rows without multi-line text wrapping
        composeTestRule.onNodeWithTag("cancel_edit_report_button").performScrollTo()
        composeTestRule.waitForIdle()

        val submitBounds = composeTestRule.onNodeWithTag("submit_final_report_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val draftBounds = composeTestRule.onNodeWithTag("save_draft_report_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val cancelBounds = composeTestRule.onNodeWithTag("cancel_edit_report_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Primary 'Nộp báo cáo' row (bottom=${submitBounds.bottom}) must sit above secondary row (top=${draftBounds.top})",
            draftBounds.top >= submitBounds.bottom
        )
        assertTrue(
            "Secondary 'Lưu nháp' and 'Đóng' buttons must sit side-by-side without overlapping",
            cancelBounds.left >= draftBounds.right + 4f
        )
        assertTrue(
            "Secondary buttons must have uniform single-line height (draft=${draftBounds.height}, cancel=${cancelBounds.height})",
            kotlin.math.abs(draftBounds.height - cancelBounds.height) <= 2f
        )
        assertTrue("Submit button must stay single-line compact (${submitBounds.height}px)", submitBounds.height <= 185f)
        assertTrue("Draft button must stay single-line compact (${draftBounds.height}px)", draftBounds.height <= 175f)
        assertTrue("Cancel button must stay single-line compact (${cancelBounds.height}px)", cancelBounds.height <= 175f)

        // 3. Actions remain interactive and produce accurate JSON payloads
        composeTestRule.onNodeWithTag("dynamic_field_f1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("save_draft_report_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, savedDraftPayload?.optBoolean("f1"))

        composeTestRule.onNodeWithTag("submit_final_report_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, submittedFinalPayload?.optBoolean("f1"))
        assertEquals(false, submittedFinalPayload?.optBoolean("f2"))
        assertEquals(true, submittedFinalPayload?.optBoolean("f3"))

        composeTestRule.onNodeWithTag("cancel_edit_report_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(cancelled)
    }

    @Test
    @Config(qualifiers = "w480dp-h2800dp")
    fun `t1 new entry form - requires 12-digit CCCD with leading zeros, allows blank full name, and renders separate profile save vs photo upload status cards`() {
        val recruiterUuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
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
                                "effective_date": "2026-10-25",
                                "projects": [{"project_id": "PRJ_01", "display_name": "Dự án 1"}],
                                "recruiters": [
                                  {
                                    "recruiter_id": "$recruiterUuid",
                                    "display_name": "Nguyễn Tuyển Dụng",
                                    "personnel_code": "HRP001",
                                    "provider_type": "hrp",
                                    "label": "Nguyễn Tuyển Dụng (HRP001)"
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
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"items":[],"page_size":20,"has_more":false,"next_cursor":null}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_t1_cccd_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_t1_cccd_prefs_${System.nanoTime()}.preferences_pb")
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
            capabilities = listOf("entry_create", "submission_create"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = recruiterUuid
        )

        var retryClickedCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileAndPhotoSyncStatusCards(
                        profileSaveStatusMessage = "Đã đẩy chính thức 1 hồ sơ NLĐ lên hệ thống. Mã NLĐ: HRP0088",
                        photoUploadStatusMessage = "Tải ảnh đính kèm: thành công 0/1, lỗi 1 tệp. Bấm Thử lại để gửi tiếp.",
                        backgroundUploadActiveCount = 0,
                        hasFailedBackgroundUploads = true,
                        onRetryFailedUploads = { retryClickedCount++ }
                    )
                    DirectEntryNewWorkersScreen(
                        actor = actor,
                        viewModel = vm,
                        onNavigateToWorkersList = {},
                        onBackToDashboard = {}
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            vm.catalog.value != null
        }
        composeTestRule.waitForIdle()

        // 1. Verify separate status cards for profile save vs photo upload and retry button
        composeTestRule.onNodeWithTag("profile_save_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trạng thái lưu hồ sơ").assertIsDisplayed()
        composeTestRule.onNodeWithTag("photo_upload_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trạng thái tải ảnh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_photo_upload_button").assertIsDisplayed().performClick()
        assertEquals(1, retryClickedCount)

        // 2. Select project and provider (auto-selects recruiter) -> still disabled until 12-digit CCCD is entered
        composeTestRule.onNodeWithTag("row_1_project").performClick()
        composeTestRule.onNodeWithText("Dự án 1").performClick()
        composeTestRule.onNodeWithTag("row_1_provider").performClick()
        composeTestRule.onNodeWithText("HRP").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_valid_workers_button").assertIsNotEnabled()

        // Verify UI labels & field order: "Số CCCD (12 chữ số) *" is mandatory and sits ABOVE optional "Họ và tên" (no '*')
        composeTestRule.onNodeWithText("Số CCCD (12 chữ số) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Họ và tên *").assertDoesNotExist()
        composeTestRule.onNodeWithText("Họ và tên").assertIsDisplayed()
        val cccdTop = composeTestRule.onNodeWithTag("row_1_cccd").fetchSemanticsNode().boundsInRoot.top
        val fullNameTop = composeTestRule.onNodeWithTag("row_1_full_name").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Expected CCCD field (top=$cccdTop) above Full Name field (top=$fullNameTop)", cccdTop < fullNameTop)

        // 3. Enter 12-digit CCCD with leading zero and leave fullName blank -> row becomes valid and Save button enables
        composeTestRule.onNodeWithTag("row_1_cccd").performTextInput("079095008811")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("NLĐ 1: CCCD 079095008811").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_valid_workers_button").assertIsEnabled()
        composeTestRule.onNodeWithText("worker-profile/1.2").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w480dp-h2400dp")
    fun `dashboard cccd review tab and batch submission history timestamp render and resolve cleanly`() {
        val entryId = "33333333-4444-4555-8666-777777777777"
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/direct-entry/cccd-review") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope_kind": "own",
                              "status_filter": "NEEDS_REVIEW",
                              "limit": 25,
                              "offset": 0,
                              "total_count": 1,
                              "summary": {
                                "total_count": 4,
                                "needs_review_count": 1,
                                "mismatch_count": 1,
                                "qr_unreadable_count": 0,
                                "pending_count": 0,
                                "matched_initial_count": 2,
                                "resolved_count": 1
                              },
                              "rows": [
                                {
                                  "entry_id": "$entryId",
                                  "submission_id": "11111111-1111-4111-8111-111111111111",
                                  "submission_state": "SUBMITTED",
                                  "entry_version": 2,
                                  "employee_code": "HRP0102",
                                  "project_id": "PRJ_01",
                                  "project_display": "KCN Bắc Ninh",
                                  "first_work_date": "2026-10-25",
                                  "recruiter_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                                  "recruiter_display": "Nguyễn Tuyển Dụng",
                                  "entered_display_name": "Nguyễn Thị Minh Ánh",
                                  "entered_national_id": "001195012349",
                                  "cccd_front_qr_status": "DECODED",
                                  "cccd_back_qr_status": "NOT_UPLOADED",
                                  "qr_extracted_payload": {
                                    "cccd_12": "001195012345",
                                    "full_name": "Nguyễn Thị Minh Ánh",
                                    "gender_canonical": "FEMALE",
                                    "gender_raw": "Nữ"
                                  },
                                  "reconciliation_status": "MISMATCH",
                                  "comparison": [
                                    {
                                      "field": "national_id",
                                      "label": "Số CCCD",
                                      "entered_value": "001195012349",
                                      "qr_value": "001195012345",
                                      "status": "DIFFERENT"
                                    }
                                  ]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/submissions") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "items": [
                                {
                                  "submission_id": "99999999-8888-4777-8666-555555555555",
                                  "state": "SUBMITTED",
                                  "version": 1,
                                  "entry_count": 3,
                                  "created_at": "2026-10-25T10:24:00Z",
                                  "updated_at": "2026-10-25T10:25:00Z",
                                  "submitted_at": "2026-10-25T10:24:00Z",
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
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"groups":[],"total_groups":0,"total_entries":0,"page":1,"page_size":25,"has_more":false,"authorization_date":"2026-10-25","data":[],"items":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "ui_cccd_rev_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_cccd_rev_prefs_${System.nanoTime()}.preferences_pb")
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
            displayName = "Nguyễn Tuyển Dụng",
            capabilities = listOf("entry_read", "submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        val activeScreen = androidx.compose.runtime.mutableStateOf("DASHBOARD")
        composeTestRule.setContent {
            MyApplicationTheme {
                when (activeScreen.value) {
                    "DASHBOARD" -> com.example.ui.screens.DashboardScreen(
                        actor = actor,
                        viewModel = vm
                    )
                    "WORKERS" -> WorkersAndDirectEntryScreen(
                        actor = actor,
                        viewModel = vm,
                        onBackToDashboard = {}
                    )
                }
            }
        }

        // 1. Verify "Chuẩn hoá thông tin NLĐ" tab appears in Dashboard and loads the CCCD review queue
        composeTestRule.onNodeWithText("Chuẩn hoá thông tin NLĐ").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.cccdReviewPage.value != null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("cccd_review_toolbar_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cccd_review_row_$entryId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cccd_review_open_button_$entryId").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("cccd_review_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cccd_review_mode_standardize_qr").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("cccd_review_mode_update_cccd").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_cccd_review_dialog_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Switch to Workers screen -> "Tôi đã nhập" tab shows "Thời gian nhập: 17:24 · 25/10/2026"
        composeTestRule.runOnIdle { activeScreen.value = "WORKERS" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("worker_scope_tab_uploader").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.ownSubmissions.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Thời gian nhập: 17:24 · 25/10/2026").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w480dp-h1600dp")
    fun `work reports screen moves overdue obligations to history with red badges and read-only viewer while keeping past-due REJECTED editable`() {
        val idOverdueNotStarted = "33333333-bbbb-4222-8333-cccc44440001"
        val idOverdueDraft = "33333333-bbbb-4222-8333-cccc44440002"
        val idRejectedPastDue = "33333333-bbbb-4222-8333-cccc44440003"
        val templateId = "dddd1111-eeee-4222-8333-ffff44445555"
        val schemaJson = """
            {
              "contract_version": "work-report-schema/1.0",
              "sections": [{"section_key": "kpi", "label": "KPI Tuyển dụng", "sort_order": 1}],
              "fields": [{"field_key": "calls_made", "section_key": "kpi", "field_type": "integer", "label": "Số cuộc gọi", "required": true, "sort_order": 1}]
            }
        """.trimIndent()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/api/work-reports/obligations" -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "data": [
                                {
                                  "obligation_id": "$idOverdueNotStarted",
                                  "template_id": "$templateId",
                                  "template_code": "DAILY_REC",
                                  "template_name": "Báo cáo ngày 20/10",
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
                                  "template_name": "Báo cáo ngày 21/10",
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
                                  "template_name": "Báo cáo cần sửa lại",
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
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    path == "/api/work-reports/obligations/$idOverdueDraft" -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "data": {
                                "obligation": {
                                  "obligation_id": "$idOverdueDraft",
                                  "template_id": "$templateId",
                                  "period_type": "DAILY",
                                  "period_key": "2026-10-21",
                                  "period_start_date": "2026-10-21",
                                  "period_end_date": "2026-10-21",
                                  "due_at": "2026-10-21T17:00:00Z",
                                  "is_mandatory": true,
                                  "obligation_status": "ACTIVE",
                                  "is_overdue": true,
                                  "version": 1
                                },
                                "template": {
                                  "template_code": "DAILY_REC",
                                  "display_name": "Báo cáo ngày 21/10",
                                  "schema_json": $schemaJson
                                },
                                "submission": {
                                  "submission_id": "99991111-2222-4333-8444-555566660002",
                                  "state": "DRAFT",
                                  "version": 1,
                                  "draft_payload_json": {"calls_made": 18}
                                },
                                "revisions": [],
                                "latest_review_event": null
                              }
                            }
                            """.trimIndent()
                        )
                    path == "/api/work-reports/obligations/$idRejectedPastDue" -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "data": {
                                "obligation": {
                                  "obligation_id": "$idRejectedPastDue",
                                  "template_id": "$templateId",
                                  "period_type": "DAILY",
                                  "period_key": "2020-01-01",
                                  "period_start_date": "2020-01-01",
                                  "period_end_date": "2020-01-01",
                                  "due_at": "2020-01-01T00:00:00Z",
                                  "is_mandatory": true,
                                  "obligation_status": "ACTIVE",
                                  "is_overdue": false,
                                  "version": 1
                                },
                                "template": {
                                  "template_code": "DAILY_REC",
                                  "display_name": "Báo cáo cần sửa lại",
                                  "schema_json": $schemaJson
                                },
                                "submission": {
                                  "submission_id": "99991111-2222-4333-8444-555566660003",
                                  "state": "REJECTED",
                                  "version": 2,
                                  "draft_payload_json": {"calls_made": 9}
                                },
                                "revisions": [],
                                "latest_review_event": {"decision": "REJECTED", "reason": "Cần cập nhật lại"}
                              }
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
        val cookieFile = File(app.cacheDir, "ui_wr_overdue_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "ui_wr_overdue_prefs_${System.nanoTime()}.preferences_pb")
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
            displayName = "Nguyễn Tuyển Dụng",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.WorkReportsScreen(
                    actor = actor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workReportsLoading.value && vm.workReports.value.size == 3
        }
        composeTestRule.waitForIdle()

        // 1. Active tab ("Cần nộp (1)") contains ONLY the REJECTED obligation, not overdue items
        composeTestRule.onNodeWithText("Cần nộp (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Đã lưu / Đã nộp (3)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Báo cáo cần sửa lại").assertIsDisplayed()
        composeTestRule.onNodeWithText("Báo cáo ngày 20/10").assertDoesNotExist()
        composeTestRule.onNodeWithText("Báo cáo ngày 21/10").assertDoesNotExist()

        // REJECTED obligation with past due_at remains editable when opened
        composeTestRule.onNodeWithTag("open_edit_report_$idRejectedPastDue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_final_report_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_draft_report_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_edit_report_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Switch to History tab ("Đã lưu / Đã nộp (3)") -> shows overdue badges and read-only form viewer
        composeTestRule.onNodeWithTag("work_reports_subtab_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Quá hạn · Chưa nộp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quá hạn · Bản nháp chưa nộp").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_edit_report_$idOverdueNotStarted").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_edit_report_$idOverdueDraft").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.editingObligation.value?.obligationId == idOverdueDraft &&
                vm.editingObligation.value?.payloadJsonRaw?.contains("18") == true
        }
        composeTestRule.waitForIdle()

        // Form is open in read-only mode: shows overdue banner, hides save/submit buttons, keeps close button
        composeTestRule.onNodeWithTag("overdue_readonly_form_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_final_report_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_draft_report_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("cancel_edit_report_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    fun `attendance screen defaults to Cham cong tab, hides Duyet nghi tab for non-approver, and supports Xin nghi create and withdraw flow`() {
        val actorId = "11111111-2222-4333-8444-555555555555"
        val createdRequestId = "cccc1111-2222-4333-8444-555555555555"
        var hasCreatedRequest = false
        var currentStatus = "PENDING"
        val capturedCreatePayloads = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        val capturedWithdrawPayloads = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/leave/me") -> {
                        val requestsJson = if (hasCreatedRequest) {
                            """
                            [
                              {
                                "request_id": "$createdRequestId",
                                "app_user_id": "$actorId",
                                "requester": {
                                  "app_user_id": "$actorId",
                                  "display_name": "Nhân viên Tuyển dụng",
                                  "personnel_code": "NV-101",
                                  "account_enabled": true
                                },
                                "status": "$currentStatus",
                                "pay_treatment": "PAID_LEAVE",
                                "employment_status_snapshot": "OFFICIAL",
                                "unit_kind_snapshot": "RECRUITMENT",
                                "profile_configured_at_submit": true,
                                "requested_units": 1,
                                "reason": "Đi khám bệnh buổi sáng",
                                "route_kind": "TEAM_LEADER",
                                "routed_team_name": "Team Miền Bắc",
                                "submitted_at": "2026-10-27T02:00:00Z",
                                "row_version": 2,
                                "days": [
                                  {
                                    "request_day_id": "day-am-1",
                                    "leave_date": "2026-10-28",
                                    "session_code": "AM",
                                    "half_day_units": 1,
                                    "is_active": true
                                  }
                                ],
                                "allocations": []
                              }
                            ]
                            """.trimIndent()
                        } else {
                            "[]"
                        }
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "ok": true,
                              "data": {
                                "as_of_date": "2026-10-27",
                                "leave_year": 2026,
                                "account": {
                                  "app_user_id": "$actorId",
                                  "display_name": "Nhân viên Tuyển dụng",
                                  "personnel_code": "NV-101",
                                  "account_enabled": true
                                },
                                "authority": {
                                  "app_user_id": "$actorId",
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
                                  "team_name": "Team Miền Bắc",
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
                                  "available_units": 12,
                                  "total_granted_units": 16,
                                  "lots": []
                                },
                                "requests": $requestsJson,
                                "ledger_entries": []
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    path == "/api/leave/requests" && request.method == "POST" -> {
                        capturedCreatePayloads.add(JSONObject(request.body.readUtf8()))
                        hasCreatedRequest = true
                        currentStatus = "PENDING"
                        MockResponse().setResponseCode(201).setBody(
                            """{"ok":true,"data":{"request_id":"$createdRequestId","status":"PENDING","requested_units":1,"row_version":2,"replayed":false}}"""
                        )
                    }
                    path == "/api/leave/requests/$createdRequestId/withdraw" && request.method == "POST" -> {
                        capturedWithdrawPayloads.add(JSONObject(request.body.readUtf8()))
                        currentStatus = "WITHDRAWN"
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"data":{"request_id":"$createdRequestId","status":"WITHDRAWN","row_version":3,"replayed":false}}"""
                        )
                    }
                    else -> MockResponse().setResponseCode(200).setBody("""{"ok":true,"data":{}}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, MiniBiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cookieFile = File(app.cacheDir, "leave_non_approver_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "leave_non_approver_prefs_${System.nanoTime()}.preferences_pb")
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
            appUserId = actorId,
            displayName = "Nhân viên Tuyển dụng",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceScreen(
                    actor = actor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.myLeaveLoading.value && vm.myLeaveSummary.value != null
        }
        composeTestRule.waitForIdle()

        // 1. Default tab is "Chấm công", "Xin nghỉ" is visible, and "Duyệt nghỉ" is hidden for non-approver
        composeTestRule.onNodeWithTag("attendance_tab_checkin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("attendance_tab_leave_request").assertIsDisplayed()
        composeTestRule.onNodeWithTag("attendance_tab_leave_approval").assertDoesNotExist()
        composeTestRule.onNodeWithText("Ghi nhận Chấm công").assertIsDisplayed()

        // 2. Switch to "Xin nghỉ" tab -> displays create form at top (FIFO) and compact year switcher without future year
        composeTestRule.onNodeWithTag("attendance_tab_leave_request").performClick()
        composeTestRule.waitForIdle()

        val currentViYear = com.example.data.LeaveContract.currentVietnamLeaveYear()
        composeTestRule.onNodeWithTag("leave_year_chip_$currentViYear").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leave_year_chip_${currentViYear - 1}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leave_year_chip_${currentViYear + 1}").assertDoesNotExist()

        val fullBounds = composeTestRule.onNodeWithTag("leave_draft_session_FULL_0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val amBounds = composeTestRule.onNodeWithTag("leave_draft_session_AM_0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pmBounds = composeTestRule.onNodeWithTag("leave_draft_session_PM_0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(fullBounds.top, amBounds.top, 2f)
        assertEquals(fullBounds.top, pmBounds.top, 2f)
        assertTrue("Expected compact session row height, got ${fullBounds.height}", fullBounds.height < 140f)

        composeTestRule.onNodeWithTag("leave_request_lazy_column")
            .performScrollToNode(hasTestTag("leave_balance_summary_card"))
        composeTestRule.onNodeWithTag("leave_balance_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("6 ngày").assertIsDisplayed()

        // Select half-day morning ("AM"), select leave type ("SICK"), set date & reason, and submit
        composeTestRule.onNodeWithTag("leave_request_lazy_column")
            .performScrollToNode(hasTestTag("leave_create_form_card"))
        composeTestRule.onNodeWithTag("leave_draft_date_input_0").performTextReplacement("2026-10-28")
        composeTestRule.onNodeWithTag("leave_draft_session_AM_0").performClick()
        composeTestRule.onNodeWithTag("leave_draft_type_SICK_0").performClick()
        composeTestRule.onNodeWithTag("leave_reason_input").performTextReplacement("Đi khám bệnh buổi sáng")
        composeTestRule.onNodeWithTag("leave_submit_request_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.leaveMutationBusy.value &&
                !vm.myLeaveLoading.value &&
                vm.myLeaveSummary.value?.requests?.size == 1
        }
        composeTestRule.waitForIdle()

        assertEquals(1, capturedCreatePayloads.size)
        val createJson = capturedCreatePayloads.single()
        assertEquals("Đi khám bệnh buổi sáng", createJson.getString("reason"))
        assertEquals("AM", createJson.getJSONArray("days").getJSONObject(0).getString("session_code"))
        assertEquals("SICK", createJson.getJSONArray("days").getJSONObject(0).getString("leave_type"))

        // 3. Withdraw pending request from history
        composeTestRule.onNodeWithTag("leave_request_lazy_column")
            .performScrollToNode(hasTestTag("leave_withdraw_btn_$createdRequestId"))
        composeTestRule.onNodeWithTag("leave_withdraw_btn_$createdRequestId").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("leave_withdraw_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leave_withdraw_reason_input").performTextReplacement("Đã thu xếp xong")
        composeTestRule.onNodeWithTag("leave_withdraw_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.leaveMutationBusy.value &&
                !vm.myLeaveLoading.value &&
                vm.myLeaveSummary.value?.requests?.firstOrNull()?.status == "WITHDRAWN"
        }
        composeTestRule.waitForIdle()

        assertEquals(1, capturedWithdrawPayloads.size)
        val withdrawJson = capturedWithdrawPayloads.single()
        assertEquals(2, withdrawJson.getInt("expected_row_version"))
        assertEquals("Đã thu xếp xong", withdrawJson.getString("reason"))
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    fun `attendance screen shows Duyet nghi tab for approver and executes approve and reject with required reason`() {
        val approverId = "99999999-2222-4333-8444-555555555555"
        val subordinateId = "11111111-2222-4333-8444-555555555555"
        val reqId = "dddd1111-2222-4333-8444-555555555555"
        var currentStatus = "PENDING"
        var currentRowVersion = 4
        val capturedDecisions = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/leave/me") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "as_of_date": "2026-10-27",
                            "leave_year": 2026,
                            "account": {"app_user_id": "$approverId", "display_name": "Team Leader Bắc", "account_enabled": true},
                            "authority": {
                              "app_user_id": "$approverId",
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
                            "routing": {"route_kind": "ADMIN_BOD_DIRECT", "unit_kind": "RECRUITMENT", "profile_configured": true},
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
                    path == "/api/leave/approvals/$reqId/decision" && request.method == "POST" -> {
                        val payload = JSONObject(request.body.readUtf8())
                        capturedDecisions.add(payload)
                        val dec = payload.getString("decision")
                        currentStatus = if (dec == "REJECT") "REJECTED" else "APPROVED"
                        currentRowVersion += 1
                        MockResponse().setResponseCode(200).setBody(
                            """{"ok":true,"data":{"request_id":"$reqId","status":"$currentStatus","row_version":$currentRowVersion,"replayed":false}}"""
                        )
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
                                "request_id": "$reqId",
                                "app_user_id": "$subordinateId",
                                "requester": {
                                  "app_user_id": "$subordinateId",
                                  "display_name": "Trần Nhân Viên",
                                  "personnel_code": "NV-205",
                                  "account_enabled": true
                                },
                                "status": "$currentStatus",
                                "pay_treatment": "PAID_LEAVE",
                                "employment_status_snapshot": "OFFICIAL",
                                "unit_kind_snapshot": "RECRUITMENT",
                                "profile_configured_at_submit": true,
                                "requested_units": 2,
                                "reason": "Nghỉ phép về quê",
                                "route_kind": "TEAM_LEADER",
                                "routed_team_name": "Team Miền Bắc",
                                "submitted_at": "2026-10-27T02:10:00Z",
                                "row_version": $currentRowVersion,
                                "days": [
                                  {
                                    "request_day_id": "d-full-1",
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
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, MiniBiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cookieFile = File(app.cacheDir, "leave_approver_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "leave_approver_prefs_${System.nanoTime()}.preferences_pb")
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
            appUserId = approverId,
            displayName = "Team Leader Bắc",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("team", "TEAM_NORTH", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AttendanceScreen(
                    actor = actor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.myLeaveLoading.value && vm.myLeaveSummary.value != null
        }
        composeTestRule.waitForIdle()

        // 1. Approver sees "Duyệt nghỉ" tab and switches to it
        composeTestRule.onNodeWithTag("attendance_tab_leave_approval").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.leaveInboxLoading.value && vm.leaveApprovalInbox.value?.items?.size == 1
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Trần Nhân Viên").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leave_reject_btn_$reqId").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // 2. Reject with >2000 character reason displays validation error and does not call backend
        composeTestRule.onNodeWithTag("leave_decision_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("leave_decision_reason_input").performTextReplacement("x".repeat(2001))
        composeTestRule.onNodeWithTag("leave_decision_confirm_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("leave_decision_dialog_error").assertIsDisplayed()
        assertTrue(capturedDecisions.isEmpty())

        // 3. Enter valid rejection reason and confirm -> sends REJECT, expected_row_version = 4, and idempotency_key
        composeTestRule.onNodeWithTag("leave_decision_reason_input").performTextReplacement("Nhóm đang cao điểm tuyển dụng")
        composeTestRule.onNodeWithTag("leave_decision_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.leaveMutationBusy.value &&
                !vm.leaveInboxLoading.value &&
                capturedDecisions.size == 1
        }
        composeTestRule.waitForIdle()

        val decisionPayload = capturedDecisions.single()
        assertEquals("REJECT", decisionPayload.getString("decision"))
        assertEquals(4, decisionPayload.getInt("expected_row_version"))
        assertEquals("Nhóm đang cao điểm tuyển dụng", decisionPayload.getString("reason"))
        assertTrue(decisionPayload.getString("idempotency_key").startsWith("leave-decision-"))
    }

    // -------------------------------------------------------------------------
    // Salary Advance (Ứng lương QLDA) UI & E2E Flow Tests
    // -------------------------------------------------------------------------
    @Test
    fun `salary advance button - hidden for non-QLDA and displayed strictly between worker_scope_grid and worker_search_input for QLDA`() {
        var isPmOnServer = false
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/api/salary-advance/context" -> MockResponse().setResponseCode(200).setBody(
                        if (isPmOnServer) {
                            """
                            {
                              "ok": true,
                              "data": {
                                "app_user_id": "pm-user-01",
                                "display_name": "QLDA Kiểm Thử",
                                "authorization_date": "2026-10-30",
                                "is_full_admin": false,
                                "is_bod": false,
                                "is_project_manager": true,
                                "is_project_accountant": false,
                                "is_supervisory_viewer": false,
                                "managed_projects": [
                                  {
                                    "project_id": "PRJ_01",
                                    "display_name": "Dự án QLDA 01",
                                    "active": true,
                                    "has_active_accountant": true,
                                    "accountant_user_id": "acc-1",
                                    "accountant_display_name": "KT 1"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        } else {
                            """
                            {
                              "ok": true,
                              "data": {
                                "app_user_id": "acc-user-01",
                                "display_name": "Kế Toán Kiểm Thử",
                                "authorization_date": "2026-10-30",
                                "is_full_admin": false,
                                "is_bod": false,
                                "is_project_manager": false,
                                "is_project_accountant": true,
                                "is_supervisory_viewer": false,
                                "managed_projects": []
                              }
                            }
                            """.trimIndent()
                        }
                    )
                    path.startsWith("/api/direct-entry/catalog") -> MockResponse().setResponseCode(200).setBody(
                        """{"ok":true,"effective_date":"2026-10-30","projects":[],"recruiters":[],"banks":[],"labor_types":[]}"""
                    )
                    path.startsWith("/api/direct-entry/workers") -> MockResponse().setResponseCode(200).setBody(
                        """{"ok":true,"rows":[],"has_more":false,"next_cursor":null,"summary":{"total_workers":0,"project_count":0,"recruiter_count":0}}"""
                    )
                    else -> MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "sa_btn_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "sa_btn_prefs_${System.nanoTime()}.preferences_pb")
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

        val currentActorState = androidx.compose.runtime.mutableStateOf(
            AppActor(
                appUserId = "acc-user-01",
                displayName = "Kế Toán Kiểm Thử",
                capabilities = listOf("entry_read"),
                scopes = listOf(ActorScope("project", "PRJ_01", "2026-01-01", null)),
                selfRecruiterSuggestion = null
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = currentActorState.value,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceContextLoading.value && vm.salaryAdvanceContext.value != null
        }
        composeTestRule.waitForIdle()

        // Non-QLDA (even if Accountant) must NOT see the Ứng lương button
        composeTestRule.onNodeWithTag("salary_advance_open_button").assertDoesNotExist()

        // Switch to QLDA actor and reload context
        isPmOnServer = true
        vm.loadSalaryAdvanceContextIfNeeded(force = true)
        currentActorState.value = AppActor(
            appUserId = "pm-user-01",
            displayName = "QLDA Kiểm Thử",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("project", "PRJ_01", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.salaryAdvanceContext.value?.isProjectManager == true
        }
        composeTestRule.waitForIdle()

        val scopeGridBounds = composeTestRule.onNodeWithTag("worker_scope_grid")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val salaryAdvanceBtnBounds = composeTestRule.onNodeWithTag("salary_advance_open_button")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val searchInputBounds = composeTestRule.onNodeWithTag("worker_search_input")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Nút Ứng lương phải nằm ngay dưới hàng phạm vi nhân sự",
            salaryAdvanceBtnBounds.top >= scopeGridBounds.bottom - 1f
        )
        assertTrue(
            "Nút Ứng lương phải nằm ngay trên ô tìm kiếm NLĐ",
            salaryAdvanceBtnBounds.bottom <= searchInputBounds.top + 1f
        )
    }

    @Test
    fun `salary advance create & history flow - filters OFF and vendor, locks ineligible worker, loads history period filters, and handles idempotency retry & conflict`() {
        val capturedCreateRequests = java.util.Collections.synchronizedList(mutableListOf<Pair<String, JSONObject>>())
        val capturedHistoryPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        var nextCreateResponseMode = "SERVER_ERROR"

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/api/salary-advance/context" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "app_user_id": "pm-user-01",
                            "display_name": "QLDA Kiểm Thử",
                            "authorization_date": "2026-10-30",
                            "is_full_admin": false,
                            "is_bod": false,
                            "is_project_manager": true,
                            "is_project_accountant": false,
                            "is_supervisory_viewer": false,
                            "managed_projects": [
                              {
                                "project_id": "PRJ_01",
                                "display_name": "Dự án Điện Tử Bắc Ninh",
                                "active": true,
                                "has_active_accountant": true,
                                "accountant_user_id": "acc-01",
                                "accountant_display_name": "KT 01"
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    path.startsWith("/api/salary-advance/projects/PRJ_01/workers?") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "project_id": "PRJ_01",
                            "next_cursor": null,
                            "has_more": false,
                            "workers": [
                              {
                                "worker_entry_id": "w-off",
                                "employee_code": "hrp-1026-000099",
                                "display_name": "NLĐ Đã Nghỉ Ẩn",
                                "employment_status": "OFF",
                                "first_work_date": "2026-09-01",
                                "provider_type": "hrp",
                                "recruiter_display_name": "Tuyển dụng HRP 1",
                                "has_portrait": true,
                                "is_eligible": false,
                                "ineligible_reasons": ["WORKER_STATUS_OFF"],
                                "bank_transfer_eligible": false
                              },
                              {
                                "worker_entry_id": "w-vendor",
                                "employee_code": "hrp-1026-000098",
                                "display_name": "NLĐ Vendor Ẩn",
                                "employment_status": "ON",
                                "first_work_date": "2026-10-01",
                                "provider_type": "vendor",
                                "recruiter_display_name": "Vendor ABC",
                                "has_portrait": true,
                                "is_eligible": false,
                                "ineligible_reasons": ["WORKER_VENDOR_RECRUITED"],
                                "bank_transfer_eligible": false
                              },
                              {
                                "worker_entry_id": "w-ineligible-dup",
                                "employee_code": "hrp-1026-000002",
                                "display_name": "Phạm Thị Trùng Hồ Sơ",
                                "employment_status": "ON",
                                "first_work_date": "2026-10-03",
                                "provider_type": "hrp",
                                "recruiter_display_name": "Tuyển dụng HRP 2",
                                "has_portrait": true,
                                "is_eligible": false,
                                "ineligible_reasons": ["WORKER_DUPLICATE_CCCD"],
                                "bank_transfer_eligible": false,
                                "bank_ineligible_reason": "BANK_ACCOUNT_MISSING"
                              },
                              {
                                "worker_entry_id": "w-eligible",
                                "employee_code": "hrp-1026-000001",
                                "display_name": "Nguyễn Văn Hợp Lệ",
                                "employment_status": "ON",
                                "first_work_date": "2026-10-01",
                                "provider_type": "hrp",
                                "recruiter_display_name": "Tuyển dụng HRP 1",
                                "has_portrait": true,
                                "is_eligible": true,
                                "ineligible_reasons": [],
                                "bank_transfer_eligible": true,
                                "bank_ineligible_reason": null,
                                "bank_account": {
                                  "has_bank_account": true,
                                  "bank_id": "VCB",
                                  "institution_name": "Vietcombank",
                                  "account_number": "0123456789",
                                  "account_holder_name": "NGUYEN VAN HOP LE",
                                  "is_self_owned": true
                                },
                                "advance_30d_summary": {
                                  "window_days": 30,
                                  "window_from_date": "2026-10-01",
                                  "window_to_date": "2026-10-30",
                                  "total_count": 2,
                                  "total_amount_vnd": "1500000"
                                }
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    path.startsWith("/api/salary-advance/projects/PRJ_01/workers/w-eligible/worker-history") -> {
                        capturedHistoryPaths.add(path)
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "ok": true,
                              "data": {
                                "worker_entry_id": "w-eligible",
                                "worker_employee_code": "hrp-1026-000001",
                                "worker_display_name": "Nguyễn Văn Hợp Lệ",
                                "period_mode": "THIS_MONTH",
                                "from_date": "2026-10-01",
                                "to_date": "2026-10-30",
                                "total_count": 1,
                                "total_amount_vnd": "500000",
                                "next_cursor": null,
                                "has_more": false,
                                "items": [
                                  {
                                    "request_id": "hist-req-1",
                                    "request_state": "APPROVED",
                                    "evidence_review_state": "REVIEWED",
                                    "project_id": "PRJ_01",
                                    "project_display_name": "Dự án Điện Tử Bắc Ninh",
                                    "worker_entry_id": "w-eligible",
                                    "worker_employee_code": "hrp-1026-000001",
                                    "worker_display_name": "Nguyễn Văn Hợp Lệ",
                                    "requested_amount_vnd": "500000",
                                    "payment_method": "CASH",
                                    "initial_content": "Ứng tiền ăn tuần đầu",
                                    "created_by_display_name": "QLDA Kiểm Thử",
                                    "effective_date": "2026-10-15",
                                    "effective_at": "2026-10-15T06:00:00.000Z",
                                    "created_at": "2026-10-15T04:00:00.000Z"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    path == "/api/salary-advance/requests" && request.method == "POST" -> {
                        val headerKey = request.getHeader("Idempotency-Key").orEmpty()
                        val bodyJson = JSONObject(request.body.readUtf8())
                        capturedCreateRequests.add(headerKey to bodyJson)
                        when (nextCreateResponseMode) {
                            "SERVER_ERROR" -> MockResponse().setResponseCode(503).setBody(
                                """{"ok":false,"code":"SALARY_ADVANCE_UNAVAILABLE","message":"Unavailable"}"""
                            )
                            "CONFLICT" -> MockResponse().setResponseCode(409).setBody(
                                """{"ok":false,"code":"SALARY_ADVANCE_IDEMPOTENCY_CONFLICT","message":"Conflict"}"""
                            )
                            else -> MockResponse().setResponseCode(201).setBody(
                                """
                                {
                                  "ok": true,
                                  "data": {
                                    "replayed": false,
                                    "request": {
                                      "request_id": "req-created-1",
                                      "request_state": "OPEN",
                                      "evidence_review_state": "NOT_UPLOADED",
                                      "version": 1,
                                      "project_id": "PRJ_01",
                                      "project_display_name": "Dự án Điện Tử Bắc Ninh",
                                      "worker_entry_id": "w-eligible",
                                      "worker_employee_code": "hrp-1026-000001",
                                      "worker_display_name": "Nguyễn Văn Hợp Lệ",
                                      "requested_amount_vnd": "2000000",
                                      "payment_method": "BANK_TRANSFER",
                                      "initial_content": "Ứng lương đợt 1"
                                    }
                                  }
                                }
                                """.trimIndent()
                            )
                        }
                    }
                    path.startsWith("/api/direct-entry/catalog") -> MockResponse().setResponseCode(200).setBody(
                        """{"ok":true,"effective_date":"2026-10-30","projects":[],"recruiters":[],"banks":[],"labor_types":[]}"""
                    )
                    path.startsWith("/api/direct-entry/workers") -> MockResponse().setResponseCode(200).setBody(
                        """{"ok":true,"rows":[],"has_more":false,"next_cursor":null,"summary":{"total_workers":0,"project_count":0,"recruiter_count":0}}"""
                    )
                    else -> MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "sa_flow_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "sa_flow_prefs_${System.nanoTime()}.preferences_pb")
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

        val pmActor = AppActor(
            appUserId = "pm-user-01",
            displayName = "QLDA Kiểm Thử",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("project", "PRJ_01", "2026-01-01", null)),
            selfRecruiterSuggestion = null
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = pmActor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.salaryAdvanceContext.value?.isProjectManager == true
        }
        composeTestRule.waitForIdle()

        // 1. Open Salary Advance dialog
        composeTestRule.onNodeWithTag("salary_advance_open_button").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceWorkersLoading.value && vm.salaryAdvanceWorkers.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // OFF and vendor workers must be hidden; HRP ineligible & eligible workers must be shown
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-off").assertDoesNotExist()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-vendor").assertDoesNotExist()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-ineligible-dup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-eligible").assertIsDisplayed()

        // 2. Select ineligible worker -> shows reason and locks submit button
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-ineligible-dup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_submit_button").assertIsNotEnabled()

        // 3. Select eligible worker -> shows first work date, recruiter, 30d summary, and enables submit
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-eligible").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_worker_first_work_date").assertTextContains("01/10/2026")
        composeTestRule.onNodeWithTag("salary_advance_worker_recruiter").assertTextContains("Tuyển dụng HRP 1")
        composeTestRule.onNodeWithTag("salary_advance_worker_30d_count").assertTextContains("2 lần")
        composeTestRule.onNodeWithTag("salary_advance_worker_30d_amount").assertTextContains("1.500.000", substring = true)

        // 4. Open Worker History dialog and test period filter modes
        composeTestRule.onNodeWithTag("salary_advance_create_scroll")
            .performScrollToNode(hasTestTag("salary_advance_open_history_button"))
        composeTestRule.onNodeWithTag("salary_advance_open_history_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceHistoryLoading.value && vm.salaryAdvanceHistoryItems.value.size == 1
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_history_item_hist-req-1").assertIsDisplayed()

        // Switch history filter to LAST_30_DAYS and ALL
        composeTestRule.onNodeWithTag("salary_advance_history_mode_LAST_30_DAYS").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            capturedHistoryPaths.any { it.contains("period_mode=LAST_30_DAYS") }
        }
        composeTestRule.onNodeWithTag("salary_advance_history_close_button").performClick()
        composeTestRule.waitForIdle()

        // 5. Fill create form and verify idempotency key reuse on retry vs rotation on 409 conflict
        composeTestRule.onNodeWithTag("salary_advance_create_scroll")
            .performScrollToNode(hasTestTag("salary_advance_method_BANK_TRANSFER"))
        composeTestRule.onNodeWithTag("salary_advance_method_BANK_TRANSFER").performClick()
        composeTestRule.onNodeWithTag("salary_advance_create_scroll")
            .performScrollToNode(hasTestTag("salary_advance_amount_input"))
        composeTestRule.onNodeWithTag("salary_advance_amount_input").performTextReplacement("2000000")
        composeTestRule.onNodeWithTag("salary_advance_create_scroll")
            .performScrollToNode(hasTestTag("salary_advance_content_input"))
        composeTestRule.onNodeWithTag("salary_advance_content_input").performTextReplacement("Ứng lương đợt 1")
        composeTestRule.waitForIdle()

        // Attempt 1: 503 error -> keeps dialog open and shows error
        nextCreateResponseMode = "SERVER_ERROR"
        composeTestRule.onNodeWithTag("salary_advance_submit_button").assertIsEnabled().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceCreateSubmitting.value && capturedCreateRequests.size == 1
        }
        composeTestRule.waitForIdle()
        assertTrue(vm.salaryAdvanceDialogOpen.value)
        assertEquals(
            "Hệ thống tạm thời chưa xử lý được yêu cầu. Vui lòng thử lại.",
            vm.salaryAdvanceCreateError.value
        )

        // Attempt 2 (retry with identical payload): MUST reuse the exact same idempotency_key; returns 409 conflict
        nextCreateResponseMode = "CONFLICT"
        composeTestRule.onNodeWithTag("salary_advance_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceCreateSubmitting.value && capturedCreateRequests.size == 2
        }
        composeTestRule.waitForIdle()
        val keyAttempt1 = capturedCreateRequests[0].first
        val keyAttempt2 = capturedCreateRequests[1].first
        assertEquals("Retry sau lỗi mạng/timeout phải dùng lại idempotency_key", keyAttempt1, keyAttempt2)

        // Attempt 3 (after 409 conflict): MUST rotate to a new idempotency_key and succeed
        nextCreateResponseMode = "SUCCESS"
        composeTestRule.onNodeWithTag("salary_advance_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceCreateSubmitting.value && capturedCreateRequests.size == 3
        }
        composeTestRule.waitForIdle()
        val keyAttempt3 = capturedCreateRequests[2].first
        assertFalse("Sau lỗi 409 conflict phải xoay idempotency_key mới", keyAttempt3 == keyAttempt2)
        assertFalse("Dialog phải đóng sau khi tạo thành công", vm.salaryAdvanceDialogOpen.value)
        val submittedBody = capturedCreateRequests[2].second
        assertEquals("PRJ_01", submittedBody.getString("project_id"))
        assertEquals("w-eligible", submittedBody.getString("worker_entry_id"))
        assertEquals("2000000", submittedBody.getString("requested_amount_vnd"))
        assertEquals("BANK_TRANSFER", submittedBody.getString("payment_method"))
        assertEquals("Ứng lương đợt 1", submittedBody.getString("initial_content"))
        assertEquals(keyAttempt3, submittedBody.getString("idempotency_key"))
    }
}
