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

        // Actor with missing/empty allowed_worker_tabs fails closed to [] even if capabilities/scopes exist
        val ungrantedActor = AppActor(
            appUserId = "00000000-2222-4333-8444-555555555555",
            displayName = "Luan PT",
            capabilities = listOf("entry_read", "submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = emptyList()
        )
        assertTrue(resolveVisibleWorkerTabs(ungrantedActor).isEmpty())

        // Own-only actor ("Admin" in displayName must NOT grant team or all tabs!)
        val ownOnlyActor = AppActor(
            appUserId = "11111111-2222-4333-8444-555555555555",
            displayName = "Admin Team Leader Fake Name",
            capabilities = listOf("entry_read", "submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited", "uploader")
        )
        val tabs = resolveVisibleWorkerTabs(ownOnlyActor).map { it.key }
        assertEquals(listOf("managed", "recruited", "uploader"), tabs)
        assertFalse(tabs.contains("team"))
        assertFalse(tabs.contains("all"))

        // Real backend actor with server-confirmed ["managed", "recruited"]
        val realBackendActor = AppActor(
            appUserId = "66666666-2222-4333-8444-555555555555",
            displayName = "Tuyển dụng HRP",
            capabilities = emptyList(),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited")
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
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited", "team", "uploader", "all")
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
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited")
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
        composeTestRule.onNodeWithTag("go_to_new_entry_from_workers").assertIsDisplayed()
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

        // Wait for catalog to load and default "Thời vụ" to populate
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
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
        val tabActor = AppActor(
            appUserId = "77777777-4444-4333-8444-555555555555",
            displayName = "Quản Lý Tab",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited")
        )

        composeTestRule.setContent {
            val loading = vm.workerLoading.collectAsState().value
            val err = vm.workerError.collectAsState().value
            androidx.compose.material3.Text("loading=$loading err=$err")
        }

        composeTestRule.runOnIdle {
            vm.selectWorkerTab("managed", actorOverride = tabActor)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            managedStarted.count == 0L
        }

        // Immediately switch tab while first coroutine is in-flight -> cancels job #1
        composeTestRule.runOnIdle {
            vm.selectWorkerTab("recruited", actorOverride = tabActor)
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
                    path.startsWith("/api/direct-entry/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "11111111-2222-4333-8444-555555555555",
                                "allowed_worker_tabs": ["managed", "recruited"]
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
        vm.navigateTo(com.example.ui.MainDestination.DASHBOARD)
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
            displayIdentifier = "vinhnt.td",
            allowedWorkerTabs = listOf("managed", "recruited", "team", "uploader")
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
                    path.startsWith("/api/direct-entry/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "99999999-8888-4777-8666-555555555555",
                                "allowed_worker_tabs": ["managed", "recruited", "team", "uploader"]
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
        vm.navigateTo(com.example.ui.MainDestination.DASHBOARD)
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
        // Catalog is not eagerly fetched at session resume, so fallback scope/team labels are used.
        val overview = vm.generalOverview.value!!
        assertEquals(3, overview.totalProfiles)
        assertEquals(2, overview.workingCount)
        assertEquals(1, overview.waitingCount)
        assertEquals(0, overview.leftCount)
        assertEquals(2, overview.activeHiringDays)
        assertEquals("Nhóm & dự án phụ trách", overview.scopeDescription)
        composeTestRule.onNodeWithTag("general_overview_section").assertIsDisplayed()

        // 3. Switch to "Báo cáo theo nhóm" -> auto-displays Team Leader's own team report with ZERO manual code input!
        composeTestRule.onNodeWithTag("dashboard_tab_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Mã nhóm tuyển dụng").assertDoesNotExist()
        composeTestRule.onNodeWithTag("team_bucket_picker_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("team_report_detail_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nhóm của tôi").assertIsDisplayed()
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
        assertEquals("Nhóm của tôi", vm.teamReport.value?.bucketDisplay)

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
        vm.navigateTo(com.example.ui.MainDestination.DASHBOARD)
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
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
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
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited", "uploader")
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
                selfRecruiterSuggestion = null,
                allowedWorkerTabs = listOf("managed")
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
        val pmActorForButton = AppActor(
            appUserId = "pm-user-01",
            displayName = "QLDA Kiểm Thử",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("project", "PRJ_01", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed")
        )
        currentActorState.value = pmActorForButton
        vm.loadSalaryAdvanceContextIfNeeded(force = true, actor = pmActorForButton)

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
                    path.startsWith("/api/salary-advance/requests?") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "queue": "PM",
                            "next_cursor": null,
                            "has_more": false,
                            "badge": {
                              "pm_actionable_count": 1,
                              "pm_needs_info_count": 1,
                              "pm_retake_required_count": 0
                            },
                            "requests": [
                              {
                                "request_id": "req-created-1",
                                "request_state": "NEEDS_INFO",
                                "evidence_review_state": "NOT_UPLOADED",
                                "version": 2,
                                "project_id": "PRJ_01",
                                "project_display_name": "Dự án Điện Tử Bắc Ninh",
                                "worker_entry_id": "w-eligible",
                                "worker_employee_code": "hrp-1026-000001",
                                "worker_display_name": "Nguyễn Văn Hợp Lệ",
                                "requested_amount_vnd": "2000000",
                                "payment_method": "BANK_TRANSFER",
                                "initial_content": "Ứng lương đợt 1",
                                "created_by_display_name": "QLDA Kiểm Thử",
                                "effective_date": "2026-10-30",
                                "created_at": "2026-10-30T08:00:00.000Z",
                                "updated_at": "2026-10-30T08:05:00.000Z"
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    path == "/api/salary-advance/requests/req-created-1" && request.method == "GET" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "data": {
                            "request": {
                              "request_id": "req-created-1",
                              "request_state": "NEEDS_INFO",
                              "evidence_review_state": "NOT_UPLOADED",
                              "version": 2,
                              "project_id": "PRJ_01",
                              "project_display_name": "Dự án Điện Tử Bắc Ninh",
                              "worker_entry_id": "w-eligible",
                              "worker_employee_code": "hrp-1026-000001",
                              "worker_display_name": "Nguyễn Văn Hợp Lệ",
                              "worker_first_work_date": "2026-10-01",
                              "worker_recruiter_display_name": "Tuyển dụng HRP 1",
                              "requested_amount_vnd": "2000000",
                              "payment_method": "BANK_TRANSFER",
                              "initial_content": "Ứng lương đợt 1",
                              "created_by_display_name": "QLDA Kiểm Thử",
                              "effective_date": "2026-10-30",
                              "created_at": "2026-10-30T08:00:00.000Z",
                              "updated_at": "2026-10-30T08:05:00.000Z",
                              "project_accountant": {
                                "user_id": "acc-01",
                                "display_name": "Kế toán Bắc Ninh"
                              },
                              "advance_30d_summary": {
                                "window_days": 30,
                                "window_from_date": "2026-10-01",
                                "window_to_date": "2026-10-30",
                                "total_count": 2,
                                "total_amount_vnd": "1500000"
                              },
                              "allowed_actions": {
                                "can_post_message": true,
                                "can_provide_info": true,
                                "can_withdraw": true,
                                "can_upload_evidence": true
                              }
                            },
                            "events": [
                              {
                                "event_id": "ev-1",
                                "event_type": "NEEDS_INFO_REQUESTED",
                                "actor_role": "ACCOUNTANT",
                                "actor_display_name": "Kế toán Bắc Ninh",
                                "message_text": "Vui lòng xác nhận số ngày công tuần này",
                                "created_at": "2026-10-30T08:05:00.000Z"
                              }
                            ],
                            "evidence_versions": []
                          }
                        }
                        """.trimIndent()
                    )
                    path == "/api/salary-advance/requests/req-created-1/messages" && request.method == "POST" -> MockResponse().setResponseCode(200).setBody(
                        """{"ok":true,"data":{"replayed":false}}"""
                    )
                    path == "/api/salary-advance/requests/req-created-1/actions" && request.method == "POST" -> MockResponse().setResponseCode(200).setBody(
                        """{"ok":true,"data":{"replayed":false}}"""
                    )
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
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited")
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

        // 1. Open standalone Salary Advance workspace (replaces workers_lazy_column instead of popup dialog)
        composeTestRule.onNodeWithTag("salary_advance_open_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_workspace_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workers_lazy_column").assertDoesNotExist()

        // Search-first UX: before typing a search query, worker cards must NOT be proactively rendered
        composeTestRule.onNodeWithTag("salary_advance_worker_search_prompt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-eligible").assertDoesNotExist()

        // Type search query to look up workers in the project
        composeTestRule.onNodeWithTag("salary_advance_worker_search_input").performTextReplacement("hrp-1026")
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceWorkersLoading.value && vm.salaryAdvanceWorkers.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // OFF and vendor workers must be hidden; HRP ineligible & eligible workers matching search must be shown
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-off").assertDoesNotExist()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-vendor").assertDoesNotExist()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-ineligible-dup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-eligible").assertIsDisplayed()

        // 2. Select ineligible worker -> shows reason and locks submit button
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-ineligible-dup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_submit_button").assertIsNotEnabled()

        // 3. Select eligible worker -> auto-collapses worker list into compact bar, shows first work date, recruiter, 30d summary, and enables submit
        composeTestRule.onNodeWithTag("salary_advance_worker_item_w-eligible").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_worker_collapsed_bar").assertIsDisplayed()
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

        // Attempt 1: 503 error -> keeps workspace open and shows error
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

        // Attempt 3 (after 409 conflict): MUST rotate to a new idempotency_key, succeed, and transition to DETAIL tab in standalone workspace
        nextCreateResponseMode = "SUCCESS"
        composeTestRule.onNodeWithTag("salary_advance_submit_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.salaryAdvanceCreateSubmitting.value &&
                capturedCreateRequests.size == 3 &&
                vm.salaryAdvanceWorkspaceTab.value == "DETAIL" &&
                !vm.salaryAdvanceDetailLoading.value
        }
        composeTestRule.waitForIdle()
        val keyAttempt3 = capturedCreateRequests[2].first
        assertFalse("Sau lỗi 409 conflict phải xoay idempotency_key mới", keyAttempt3 == keyAttempt2)
        assertTrue("Workspace vẫn mở ở tab DETAIL sau khi tạo thành công", vm.salaryAdvanceDialogOpen.value)
        assertEquals("DETAIL", vm.salaryAdvanceWorkspaceTab.value)
        assertEquals("req-created-1", vm.salaryAdvanceSelectedRequestId.value)
        composeTestRule.onNodeWithTag("salary_advance_detail_scroll").assertIsDisplayed()
        val submittedBody = capturedCreateRequests[2].second
        assertEquals("PRJ_01", submittedBody.getString("project_id"))
        assertEquals("w-eligible", submittedBody.getString("worker_entry_id"))
        assertEquals("2000000", submittedBody.getString("requested_amount_vnd"))
        assertEquals("BANK_TRANSFER", submittedBody.getString("payment_method"))
        assertEquals("Ứng lương đợt 1", submittedBody.getString("initial_content"))
        assertEquals(keyAttempt3, submittedBody.getString("idempotency_key"))

        // 6. Switch to LIST tab -> verify collapsible filter is closed by default (only search input shown), then opens when toggled
        composeTestRule.onNodeWithTag("salary_advance_tab_requests").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_list_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("salary_advance_list_project_filter").assertDoesNotExist()
        composeTestRule.onNodeWithTag("salary_advance_list_toggle_filter_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("salary_advance_list_project_filter").assertIsDisplayed()

        // 7. Bottom navigation to Nhân sự (or system Back) returns to Nhân sự list
        composeTestRule.runOnIdle {
            vm.navigateTo(com.example.ui.MainDestination.WORKERS)
        }
        composeTestRule.waitForIdle()
        assertFalse(vm.salaryAdvanceDialogOpen.value)
        composeTestRule.onNodeWithTag("workers_lazy_column").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w480dp-h2400dp")
    fun `direct edit dialog supports all 4 groups, per-file error retry without re-submitting profile, and document_upload permission gating`() {
        val baseWorker = sampleWorker(
            entryId = "aaaaaaaa-1111-4222-8333-444444444444",
            canView = true,
            canProposeChange = false,
            canDirectEdit = true
        )
        val detail = WorkerEntryDetail(
            entryId = baseWorker.entryId,
            submissionId = "bbbbbbbb-1111-4222-8333-444444444444",
            version = 4,
            projectId = "PRJ_01",
            firstWorkDate = "2026-10-01",
            employeeCode = "hrp-1026-000101",
            recruiterId = "cccccccc-1111-4222-8333-444444444444",
            teamId = "TEAM_01",
            providerType = "hrp",
            laborType = "TEMPORARY",
            laborTypeDisplay = "Thời vụ",
            displayName = "Phạm Văn Gốc",
            gender = "MALE",
            dateOfBirth = "1998-05-12",
            nationalId = "001098012345",
            nationalIdIssuedAt = "2022-01-15",
            nationalIdIssuedPlace = "Cục CS QLHC",
            address = "Hà Nội",
            phone = "0912345678",
            generalNote = null,
            paymentState = "provided",
            bankId = "VCB",
            bankName = "Vietcombank",
            rawBankName = "Vietcombank",
            accountNumber = "0011001234567",
            accountHolderName = "PHAM VAN GOC",
            employmentStatus = "ON",
            employmentEffectiveDate = "2026-10-01",
            employmentStatusVersion = 1,
            employmentLeaveReason = null,
            portraitPreviewPath = null,
            portraitDownloadPath = null,
            documents = emptyList(),
            workerDetailsStates = mapOf(
                "gender" to "provided",
                "date_of_birth" to "provided",
                "national_id" to "provided",
                "national_id_issued_at" to "provided",
                "national_id_issued_place" to "provided",
                "address" to "provided",
                "phone" to "provided"
            ),
            fileVersions = mapOf("WORKER_PORTRAIT" to 1)
        )

        var capturedVersion = -1
        var capturedPatch: JSONObject? = null
        var retriedDocType: String? = null
        val canUploadState = androidx.compose.runtime.mutableStateOf(true)
        val profileSavedBannerState = androidx.compose.runtime.mutableStateOf<String?>(
            "Thông tin hồ sơ (cá nhân, công việc, ngân hàng): Đã lưu thành công."
        )
        val fileStatusesState = androidx.compose.runtime.mutableStateOf(
            mapOf(
                "WORKER_PORTRAIT" to com.example.data.DirectEditFileStatus(
                    documentType = "WORKER_PORTRAIT",
                    state = com.example.data.DirectEditFileState.SAVED,
                    message = "Ảnh chân dung NLĐ: Đã tải lên và lưu thành công (v2).",
                    documentVersion = 2
                ),
                "CCCD_BACK" to com.example.data.DirectEditFileStatus(
                    documentType = "CCCD_BACK",
                    state = com.example.data.DirectEditFileState.ERROR,
                    message = "CCCD mặt sau: Kho lưu trữ tạm thời gián đoạn. Vui lòng bấm Thử lại tệp này.",
                    stagedRef = com.example.data.StagedDocumentRef(
                        stageId = "",
                        documentType = "CCCD_BACK",
                        fileName = "back.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 2048L,
                        localFilePath = "/tmp/mock_back.jpg"
                    )
                )
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkerPrivilegedEditDialog(
                    worker = baseWorker,
                    baselineDetail = detail,
                    catalog = null,
                    busy = false,
                    error = null,
                    onDismiss = {},
                    onSubmit = { ver, patch, _ ->
                        capturedVersion = ver
                        capturedPatch = patch
                    },
                    renderInDialog = false,
                    canUploadDocuments = canUploadState.value,
                    profileSaveMessage = profileSavedBannerState.value,
                    fileStatuses = fileStatusesState.value,
                    onRetryDocument = { docType, _ ->
                        retriedDocType = docType
                    }
                )
            }
        }

        // All 4 sections and profile save status banner are displayed
        composeTestRule.onNodeWithTag("direct_edit_profile_save_status").assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_section_work").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_section_personal").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_section_payment").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_section_documents").performScrollTo().assertIsDisplayed()

        // Individual file retry button is shown ONLY for failed CCCD_BACK and does not trigger onSubmit (/privileged-edit)
        composeTestRule.onNodeWithTag("direct_edit_doc_retry_CCCD_BACK").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("CCCD_BACK", retriedDocType)
        assertEquals(-1, capturedVersion)

        // Edit personal, work, and payment fields and submit
        composeTestRule.onNodeWithTag("direct_edit_employee_code_input").performScrollTo().performTextReplacement("hrp-1026-000202")
        composeTestRule.onNodeWithTag("direct_edit_display_name_input").performScrollTo().performTextReplacement("Phạm Văn Đã Sửa")
        composeTestRule.onNodeWithTag("direct_edit_account_number_input").performScrollTo().performTextReplacement("9988776655")
        composeTestRule.onNodeWithTag("direct_edit_bank_query_input").performScrollTo().performTextReplacement("Ngân hàng Tham Chiếu Mới")
        composeTestRule.onNodeWithTag("direct_edit_save_button").performClick()

        assertEquals(4, capturedVersion)
        assertTrue(capturedPatch != null)
        assertEquals("hrp-1026-000202", capturedPatch!!.getString("employee_code"))
        assertEquals("Phạm Văn Đã Sửa", capturedPatch!!.getJSONObject("worker_details").getString("display_name"))
        val paymentObj = capturedPatch!!.getJSONObject("payment")
        assertEquals("provided", paymentObj.getString("state"))
        assertEquals("9988776655", paymentObj.getString("account_number"))
        assertTrue(paymentObj.isNull("bank_id"))
        assertEquals("Ngân hàng Tham Chiếu Mới", paymentObj.getString("bank_name"))

        // When actor lacks document_upload capability, file upload controls are hidden and denial notice is shown
        composeTestRule.runOnIdle { canUploadState.value = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("direct_edit_doc_upload_denied_notice").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_edit_doc_retry_CCCD_BACK").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    fun `attendance explanation UI places Giai trinh cham cong before Xin nghi phep, hides approval for regular employee, and supports dynamic slots submit and approver decision`() {
        val employeeId = "11111111-2222-4333-8444-555555555555"
        val approverId = "99999999-2222-4333-8444-555555555555"
        val explanationId = "eeee1111-2222-4333-8444-555555555555"

        var canApprove = false
        var slotSubmitted = false
        var decisionStatus = "PENDING"
        var decisionRowVersion = 2
        val capturedSubmitBodies = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        val capturedDecisionBodies = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())

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
                            "as_of_date": "2026-11-03",
                            "leave_year": 2026,
                            "account": {"app_user_id": "$employeeId", "display_name": "Nhân Viên A", "account_enabled": true},
                            "authority": {
                              "app_user_id": "$employeeId",
                              "is_shared_non_employee": false,
                              "is_full_admin": false,
                              "is_bod": false,
                              "is_hr_operator": false,
                              "is_hr_report_viewer": false,
                              "is_team_leader": $canApprove,
                              "is_department_approver": false,
                              "can_submit_request": true,
                              "can_approve_requests": $canApprove,
                              "can_view_monthly_report": false,
                              "can_manage_config": false
                            },
                            "routing": {"route_kind": "TEAM_LEADER", "unit_kind": "RECRUITMENT", "profile_configured": true},
                            "policy": {"annual_base_units": 24, "monthly_accrual_units": 2, "row_version": 1},
                            "balance": {
                              "leave_year": 2026,
                              "as_of_date": "2026-11-03",
                              "has_profile": true,
                              "profile_configured": true,
                              "employment_status": "OFFICIAL",
                              "unit_kind": "RECRUITMENT",
                              "available_units": 12,
                              "total_granted_units": 16,
                              "lots": []
                            },
                            "requests": [],
                            "ledger_entries": []
                          }
                        }
                        """.trimIndent()
                    )
                    path == "/api/attendance/explanations" && request.method == "POST" -> {
                        val payload = JSONObject(request.body.readUtf8())
                        capturedSubmitBodies.add(payload)
                        slotSubmitted = true
                        MockResponse().setResponseCode(201).setBody(
                            """
                            {
                              "ok": true,
                              "replayed": false,
                              "explanation": {
                                "explanation_id": "$explanationId",
                                "app_user_id": "$employeeId",
                                "requester": {"app_user_id": "$employeeId", "display_name": "Nhân Viên A", "account_enabled": true},
                                "business_date": "2026-11-03",
                                "slot_code": "SLOT_3",
                                "slot_label": "Mốc chiều 3",
                                "session_code": "PM",
                                "target_time": "17:30",
                                "grace_before_minutes": 15,
                                "grace_after_minutes": 15,
                                "original_slot_status": "MISSING",
                                "status": "PENDING",
                                "reason": "${payload.getString("reason")}",
                                "route_kind": "TEAM_LEADER",
                                "unit_kind_snapshot": "RECRUITMENT",
                                "routed_team": {"team_id": "t-1", "team_code": "MB", "team_name": "Team Miền Bắc"},
                                "submitted_at": "2026-11-03T10:00:00Z",
                                "submitted_at_local": "03/11/2026 17:00",
                                "decision_note": "",
                                "row_version": 1,
                                "can_decide": false,
                                "actual_checkins": [],
                                "events": []
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    path == "/api/attendance/explanations/approvals/$explanationId/decision" && request.method == "POST" -> {
                        val payload = JSONObject(request.body.readUtf8())
                        capturedDecisionBodies.add(payload)
                        decisionStatus = if (payload.getString("decision") == "APPROVE") "APPROVED" else "REJECTED"
                        decisionRowVersion += 1
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "ok": true,
                              "replayed": false,
                              "explanation": {
                                "explanation_id": "$explanationId",
                                "app_user_id": "22222222-3333-4444-8555-666666666666",
                                "requester": {"app_user_id": "22222222-3333-4444-8555-666666666666", "display_name": "Lê Thành Viên", "account_enabled": true},
                                "business_date": "2026-11-03",
                                "slot_code": "SLOT_3",
                                "slot_label": "Mốc chiều 3",
                                "session_code": "PM",
                                "target_time": "17:30",
                                "grace_before_minutes": 15,
                                "grace_after_minutes": 15,
                                "original_slot_status": "MISSING",
                                "status": "$decisionStatus",
                                "reason": "Hỗ trợ ứng viên tại nhà máy",
                                "route_kind": "TEAM_LEADER",
                                "unit_kind_snapshot": "RECRUITMENT",
                                "routed_team": {"team_id": "t-1", "team_code": "MB", "team_name": "Team Miền Bắc"},
                                "submitted_at": "2026-11-03T10:00:00Z",
                                "submitted_at_local": "03/11/2026 17:00",
                                "decision_note": "${payload.optString("decision_note", "")}",
                                "row_version": $decisionRowVersion,
                                "can_decide": false,
                                "actual_checkins": [],
                                "events": []
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    path.startsWith("/api/attendance/explanations/approvals") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "viewer": {
                            "app_user_id": "$approverId",
                            "can_approve_explanations": true,
                            "is_full_admin": false,
                            "is_bod": false
                          },
                          "pagination": {"limit": 50, "offset": 0, "total_rows": 1, "pending_count": 1},
                          "items": [
                            {
                              "explanation_id": "$explanationId",
                              "app_user_id": "22222222-3333-4444-8555-666666666666",
                              "requester": {
                                "app_user_id": "22222222-3333-4444-8555-666666666666",
                                "display_name": "Lê Thành Viên",
                                "personnel_code": "NV-309",
                                "account_enabled": true
                              },
                              "business_date": "2026-11-03",
                              "slot_code": "SLOT_3",
                              "slot_label": "Mốc chiều 3",
                              "session_code": "PM",
                              "target_time": "17:30",
                              "grace_before_minutes": 15,
                              "grace_after_minutes": 15,
                              "original_slot_status": "MISSING",
                              "status": "$decisionStatus",
                              "reason": "Hỗ trợ ứng viên tại nhà máy",
                              "route_kind": "TEAM_LEADER",
                              "unit_kind_snapshot": "RECRUITMENT",
                              "routed_team": {"team_id": "t-1", "team_code": "MB", "team_name": "Team Miền Bắc"},
                              "submitted_at": "2026-11-03T10:00:00Z",
                              "submitted_at_local": "03/11/2026 17:00",
                              "decision_note": "",
                              "row_version": $decisionRowVersion,
                              "can_decide": ${decisionStatus == "PENDING"},
                              "actual_checkins": [],
                              "events": []
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                    path.startsWith("/api/attendance/explanations") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "ok": true,
                          "viewer": {
                            "app_user_id": "${if (canApprove) approverId else employeeId}",
                            "can_submit_explanation": true,
                            "can_approve_explanations": $canApprove,
                            "is_shared_non_employee": false,
                            "is_full_admin": false,
                            "is_bod": false
                          },
                          "routing": {
                            "route_kind": "TEAM_LEADER",
                            "unit_kind": "RECRUITMENT",
                            "primary_approver": {
                              "app_user_id": "$approverId",
                              "display_name": "Trưởng Nhóm Bắc",
                              "account_enabled": true
                            },
                            "team_id": "t-1",
                            "team_code": "MB",
                            "team_name": "Team Miền Bắc",
                            "fallback_reason": null,
                            "profile_configured": true
                          },
                          "today_local": "2026-11-03",
                          "selected_date": "2026-11-03",
                          "day_evaluation": {
                            "business_date": "2026-11-03",
                            "policy_profile_id_used": "77777777-3333-4444-8555-666666666666",
                            "policy_profile_code_used": "CA_3_MOC_VP",
                            "policy_profile_name_used": "Bảng công 3 mốc Khối Văn phòng",
                            "policy_is_default_used": false,
                            "policy_version_used": 2,
                            "slots": [
                              {
                                "slot_code": "SLOT_1",
                                "slot_index": 1,
                                "slot_label": "Mốc sáng 1",
                                "session_code": "AM",
                                "target_time": "08:00",
                                "grace_before_minutes": 15,
                                "grace_after_minutes": 15,
                                "is_leave_covered": false,
                                "original_slot_status": "ON_TIME",
                                "slot_status": "ON_TIME",
                                "eligible_for_explanation": false,
                                "checkins": [],
                                "explanation": null
                              },
                              {
                                "slot_code": "SLOT_2",
                                "slot_index": 2,
                                "slot_label": "Mốc trưa 2",
                                "session_code": "PM",
                                "target_time": "13:30",
                                "grace_before_minutes": 15,
                                "grace_after_minutes": 15,
                                "is_leave_covered": false,
                                "original_slot_status": "ON_TIME",
                                "slot_status": "ON_TIME",
                                "eligible_for_explanation": false,
                                "checkins": [],
                                "explanation": null
                              },
                              {
                                "slot_code": "SLOT_3",
                                "slot_index": 3,
                                "slot_label": "Mốc chiều 3",
                                "session_code": "PM",
                                "target_time": "17:30",
                                "grace_before_minutes": 15,
                                "grace_after_minutes": 15,
                                "is_leave_covered": false,
                                "original_slot_status": "MISSING",
                                "slot_status": "${if (slotSubmitted) "EXPLAINED_PENDING" else "MISSING"}",
                                "eligible_for_explanation": ${!slotSubmitted},
                                "checkins": [],
                                "explanation": ${
                                    if (slotSubmitted) {
                                        """{"explanation_id":"$explanationId","status":"PENDING","original_slot_status":"MISSING","reason":"Kẹt xe tại cầu Thanh Trì","submitted_at":"2026-11-03T10:00:00Z","submitted_at_local":"03/11/2026 17:00","decision_note":"","row_version":1}"""
                                    } else {
                                        "null"
                                    }
                                }
                              }
                            ]
                          },
                          "pagination": {"limit": 50, "offset": 0, "total_rows": ${if (slotSubmitted) 1 else 0}},
                          "items": []
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
        val cookieFile = File(app.cacheDir, "exp_ui_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "exp_ui_prefs_${System.nanoTime()}.preferences_pb")
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
            appUserId = employeeId,
            displayName = "Nhân Viên A",
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
            !vm.myExplanationsLoading.value && vm.myExplanationsOverview.value != null
        }
        composeTestRule.waitForIdle()

        // 1. Verify menu order: Chấm công -> Giải trình chấm công -> Xin nghỉ phép; Xét duyệt giải trình is hidden for unauthorized user
        val checkinBounds = composeTestRule.onNodeWithTag("attendance_tab_checkin").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val explanationBounds = composeTestRule.onNodeWithTag("attendance_tab_explanation").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val leaveReqBounds = composeTestRule.onNodeWithTag("attendance_tab_leave_request").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(checkinBounds.left < explanationBounds.left)
        assertTrue(explanationBounds.left < leaveReqBounds.left)
        composeTestRule.onNodeWithTag("attendance_tab_explanation_approval").assertDoesNotExist()

        // 2. Open "Giải trình chấm công" tab, verify assigned attendance policy profile card, dynamic 3-slot configuration and submit explanation for SLOT_3
        composeTestRule.onNodeWithTag("attendance_tab_explanation").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("explanation_policy_profile_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bảng chấm công áp dụng: Bảng công 3 mốc Khối Văn phòng").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nhóm cấu hình riêng").assertIsDisplayed()

        val slotHeaderTitleBounds = composeTestRule.onNodeWithText("Các mốc chấm công ngày 2026-11-03")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val slotBadgeBounds = composeTestRule.onNodeWithTag("explanation_dynamic_slot_count_badge")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Slot count badge must be placed on its own row below the header title instead of squeezed horizontally",
            slotBadgeBounds.top >= slotHeaderTitleBounds.bottom - 1f
        )
        composeTestRule.onNodeWithTag("explanation_lazy_column")
            .performScrollToNode(hasTestTag("explanation_open_submit_btn_SLOT_3"))
        composeTestRule.onNodeWithTag("explanation_open_submit_btn_SLOT_3").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("explanation_submit_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explanation_reason_input").performTextReplacement("Kẹt xe tại cầu Thanh Trì")
        composeTestRule.onNodeWithTag("explanation_submit_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.explanationMutationBusy.value &&
                !vm.myExplanationsLoading.value &&
                capturedSubmitBodies.size == 1 &&
                vm.myExplanationsOverview.value?.dayEvaluation?.slots
                    ?.find { it.slotCode == "SLOT_3" }
                    ?.slotStatus == "EXPLAINED_PENDING"
        }
        composeTestRule.waitForIdle()

        val submittedPayload = capturedSubmitBodies.single()
        assertEquals("2026-11-03", submittedPayload.getString("business_date"))
        assertEquals("SLOT_3", submittedPayload.getString("slot_code"))
        assertEquals("Kẹt xe tại cầu Thanh Trì", submittedPayload.getString("reason"))

        // 3. Grant approval authority on server and refresh -> "Xét duyệt giải trình" tab appears on row 2 and approver can decide
        composeTestRule.runOnIdle {
            canApprove = true
            vm.refreshMyLeaveSummary()
            vm.refreshMyAttendanceExplanations(businessDate = "2026-11-03")
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.myExplanationsLoading.value &&
                vm.myExplanationsOverview.value?.viewer?.canApproveExplanations == true &&
                composeTestRule.onAllNodes(hasTestTag("attendance_tab_explanation_approval"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        val row1CheckinBounds = composeTestRule.onNodeWithTag("attendance_tab_checkin").fetchSemanticsNode().boundsInRoot
        val row2ExpApprovalBounds = composeTestRule.onNodeWithTag("attendance_tab_explanation_approval").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Approval tabs must be placed on the second row below personal tabs",
            row2ExpApprovalBounds.top >= row1CheckinBounds.bottom - 1f
        )

        composeTestRule.onNodeWithTag("attendance_tab_explanation_approval").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.explanationInboxLoading.value && vm.explanationApprovalInbox.value?.items?.size == 1
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Lê Thành Viên").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explanation_approval_lazy_column")
            .performScrollToNode(hasTestTag("explanation_approve_btn_$explanationId"))
        composeTestRule.onNodeWithTag("explanation_approve_btn_$explanationId").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("explanation_decision_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explanation_decision_note_input").performTextReplacement("Đã xác nhận lịch công tác")
        composeTestRule.onNodeWithTag("explanation_decision_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.explanationMutationBusy.value &&
                !vm.explanationInboxLoading.value &&
                capturedDecisionBodies.size == 1
        }
        composeTestRule.waitForIdle()

        val decisionJson = capturedDecisionBodies.single()
        assertEquals("APPROVE", decisionJson.getString("decision"))
        assertEquals(2, decisionJson.getInt("expected_row_version"))
        assertEquals("Đã xác nhận lịch công tác", decisionJson.getString("decision_note"))
    }

    @Test
    fun `regression 1 - allowed_worker_tabs empty yields zero tabs hides Workers nav and fires zero worker catalog or salary requests`() {
        val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths.add(path)
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "22222222-3333-4444-8555-666666666666",
                                "display_name": "Phạm Thành Luân",
                                "capabilities": ["entry_read", "submission_read"],
                                "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "22222222-3333-4444-8555-666666666666",
                                "allowed_worker_tabs": []
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
        val cookieFile = File(app.cacheDir, "reg1_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg1_prefs_${System.nanoTime()}.preferences_pb")
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = JvmAes256GcmCookieCipher()
        )
        repo.cookieJar.saveFromResponse(
            server.url("/api/auth/session"),
            listOf(
                Cookie.Builder()
                    .name("mini_bi_sid")
                    .value("sid_luanpt")
                    .hostOnlyDomain(server.hostName)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 3_600_000L)
                    .build()
            )
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, prefsFile)

        composeTestRule.setContent {
            MyApplicationTheme {
                MiniBiAppRoot(viewModel = vm)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.sessionBootstrapping.value && vm.currentActor.value != null
        }
        composeTestRule.waitForIdle()

        val actor = vm.currentActor.value!!
        assertTrue(actor.safeAllowedWorkerTabs.isEmpty())
        assertTrue(resolveVisibleWorkerTabs(actor).isEmpty())
        assertEquals("", vm.activeWorkerTabKey.value)

        // Bottom nav entry "Nhân sự" (WORKERS) is hidden when allowed_worker_tabs=[]
        composeTestRule.onNodeWithTag("bottom_nav_workers").assertDoesNotExist()

        // Attempting to navigate or load workers directly does NOT switch screen or fire worker/catalog/salary requests
        composeTestRule.runOnIdle {
            vm.navigateTo(com.example.ui.MainDestination.WORKERS)
            vm.ensureInitialWorkerTabLoaded(actor)
            vm.selectWorkerTab("managed")
            vm.selectWorkerTab("recruited")
            vm.loadSalaryAdvanceContextIfNeeded(force = true, actor = actor)
        }
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.MainDestination.ATTENDANCE, vm.currentDestination.value)

        val snapshotPaths = requestedPaths.toList()
        assertTrue(snapshotPaths.any { it.startsWith("/api/direct-entry/session") })
        assertTrue("Must NEVER call /api/direct-entry/workers when allowed_worker_tabs=[]",
            snapshotPaths.none { it.startsWith("/api/direct-entry/workers") })
        assertTrue("Must NEVER call /api/direct-entry/catalog when allowed_worker_tabs=[]",
            snapshotPaths.none { it.startsWith("/api/direct-entry/catalog") })
        assertTrue("Must NEVER call /api/salary-advance/context when allowed_worker_tabs=[]",
            snapshotPaths.none { it.startsWith("/api/salary-advance/context") })
    }

    @Test
    fun `regression 2 - allowed_worker_tabs with only recruited displays only recruited tab and requests only scope recruited`() {
        val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths.add(path)
                return when {
                    path.startsWith("/api/direct-entry/workers") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "recruited",
                              "items": [],
                              "summary": {"total_workers": 5, "project_count": 1, "recruiter_count": 1},
                              "page_size": 25,
                              "has_more": false,
                              "next_cursor": null
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
        val cookieFile = File(app.cacheDir, "reg2_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg2_prefs_${System.nanoTime()}.preferences_pb")
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

        val recruitedOnlyActor = AppActor(
            appUserId = "33333333-4444-4555-8666-777777777777",
            displayName = "Tuyển Dụng Chỉ Recruited",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("recruited")
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = recruitedOnlyActor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workerLoading.value && requestedPaths.any { it.startsWith("/api/direct-entry/workers") }
        }
        composeTestRule.waitForIdle()

        // Only "recruited" tab is displayed
        composeTestRule.onNodeWithTag("worker_scope_tab_recruited").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_scope_tab_managed").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_scope_tab_team").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_scope_tab_uploader").assertDoesNotExist()
        composeTestRule.onNodeWithTag("worker_scope_tab_all").assertDoesNotExist()

        val workerPaths = requestedPaths.filter { it.startsWith("/api/direct-entry/workers") }
        assertTrue(workerPaths.isNotEmpty())
        assertTrue("Only scope=recruited may be requested: $workerPaths",
            workerPaths.all { it.contains("scope=recruited") })
        assertTrue("Must NOT request catalog on Workers screen entry",
            requestedPaths.none { it.startsWith("/api/direct-entry/catalog") })
        assertTrue("Must NOT request salary-advance/context when actor does not have managed tab",
            requestedPaths.none { it.startsWith("/api/salary-advance/context") })
    }

    @Test
    fun `regression 3 - missing wrong-type unknown-element duplicate or mismatched app_user_id in allowed_worker_tabs fails closed to empty list`() {
        val userId = "11111111-2222-4333-8444-555555555555"

        // Missing field -> []
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(null))
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(JSONObject.NULL))

        // Wrong type (String, Object, Number, Boolean) -> []
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs("managed"))
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(JSONObject("""{"managed":true}""")))
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(123))

        // Array with non-string element -> []
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(org.json.JSONArray("""["managed", 123]""")))

        // Array with unknown scope -> []
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(org.json.JSONArray("""["managed", "super_admin"]""")))

        // Array with duplicate scope -> []
        assertEquals(emptyList<String>(), com.example.data.ActorContractParser.parseAllowedWorkerTabs(org.json.JSONArray("""["recruited", "recruited"]""")))

        // Valid canonical array -> preserved in server order
        assertEquals(
            listOf("managed", "recruited", "team"),
            com.example.data.ActorContractParser.parseAllowedWorkerTabs(org.json.JSONArray("""["managed", "recruited", "team"]"""))
        )

        // Mismatched app_user_id in /api/direct-entry/session -> fails closed to []
        val mismatchedSession = JSONObject(
            """
            {
              "ok": true,
              "actor": {
                "app_user_id": "99999999-2222-4333-8444-555555555555",
                "allowed_worker_tabs": ["managed", "recruited"]
              }
            }
            """.trimIndent()
        )
        assertEquals(
            emptyList<String>(),
            com.example.data.ActorContractParser.parseDirectEntrySessionAllowedWorkerTabs(mismatchedSession, userId)
        )

        // Matching app_user_id in /api/direct-entry/session -> returns canonical tabs
        val matchingSession = JSONObject(
            """
            {
              "ok": true,
              "actor": {
                "app_user_id": "$userId",
                "allowed_worker_tabs": ["recruited", "uploader"]
              }
            }
            """.trimIndent()
        )
        assertEquals(
            listOf("recruited", "uploader"),
            com.example.data.ActorContractParser.parseDirectEntrySessionAllowedWorkerTabs(matchingSession, userId)
        )
    }

    @Test
    fun `regression 4 - HTTP 403 on worker scope does not trigger fallback request to next scope`() {
        val workerRequests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/api/direct-entry/workers")) {
                    workerRequests.add(path)
                    return MockResponse()
                        .setResponseCode(403)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"WORKER_DIRECTORY_DENIED","message":"Không có quyền truy cập phạm vi này."}""")
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"ok":true,"data":[]}""")
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg4_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg4_prefs_${System.nanoTime()}.preferences_pb")
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
            appUserId = "44444444-5555-4666-8777-888888888888",
            displayName = "Quản Lý Dự Án",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited")
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = actor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            workerRequests.isNotEmpty() && !vm.workerLoading.value
        }
        composeTestRule.waitForIdle()

        // Must have made ONLY 1 request for scope=managed, and MUST NOT fall back to scope=recruited!
        assertEquals(1, workerRequests.size)
        assertTrue(workerRequests.single().contains("scope=managed"))
        assertFalse(workerRequests.any { it.contains("scope=recruited") })
        assertEquals("managed", vm.activeWorkerTabKey.value)
    }

    @Test
    fun `regression 5 - logout and login with a different account clears cached worker tabs selected scope and cached data of previous account`() {
        var currentLoginAccount = "pm_account"
        val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths.add(path)
                return when {
                    path.startsWith("/api/auth/login") -> {
                        val isPm = currentLoginAccount == "pm_account"
                        val userId = if (isPm) "aaaa1111-2222-4333-8444-555555555555" else "bbbb2222-3333-4444-8555-666666666666"
                        val name = if (isPm) "Quản Lý PM" else "Luân Không Quyền"
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .addHeader("Set-Cookie", "mini_bi_sid=sid_$currentLoginAccount; Path=/; HttpOnly")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "display_name": "$name",
                                    "capabilities": ["entry_read"],
                                    "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                    "self_recruiter_suggestion": null
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/session") -> {
                        val isPm = currentLoginAccount == "pm_account"
                        val userId = if (isPm) "aaaa1111-2222-4333-8444-555555555555" else "bbbb2222-3333-4444-8555-666666666666"
                        val tabsJson = if (isPm) """["managed", "recruited"]""" else """[]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "allowed_worker_tabs": $tabsJson
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/workers") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "managed",
                              "items": [
                                {
                                  "entry_id": "99999999-1111-4222-8333-444444444444",
                                  "entry_version": 1,
                                  "submission_state": "SUBMITTED",
                                  "employee_code": "HRP888",
                                  "display_name": "Công Nhân Của PM",
                                  "project_id": "PRJ_PM",
                                  "project_display": "Dự Án PM",
                                  "first_work_date": "2026-10-10",
                                  "labor_type": "SEASONAL",
                                  "labor_type_display": "Thời vụ",
                                  "employment_status": "ON",
                                  "recruiter_id": "aaaa1111-2222-4333-8444-555555555555",
                                  "recruiter_display": "Quản Lý PM",
                                  "allowed_actions": {"view": true}
                                }
                              ],
                              "summary": {"total_workers": 1, "project_count": 1, "recruiter_count": 1},
                              "page_size": 25,
                              "has_more": false,
                              "next_cursor": null
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
        val cookieFile = File(app.cacheDir, "reg5_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg5_prefs_${System.nanoTime()}.preferences_pb")
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

        // 1. Login as PM account (`allowed_worker_tabs = ["managed", "recruited"]`)
        vm.login("pm@hrpartner.vn", "secret123", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa1111-2222-4333-8444-555555555555"
        }
        assertEquals(listOf("managed", "recruited"), vm.currentActor.value?.safeAllowedWorkerTabs)
        assertEquals("managed", vm.activeWorkerTabKey.value)

        // 2. Logout -> clears actor, allowed_worker_tabs, activeWorkerTabKey, and cached state
        vm.logout()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.currentActor.value == null
        }
        assertEquals("", vm.activeWorkerTabKey.value)
        assertNull(vm.salaryAdvanceContext.value)

        // 3. Login as second account with `allowed_worker_tabs = []`
        requestedPaths.clear()
        currentLoginAccount = "luan_account"
        vm.login("luanpt@hrpartner.vn", "secret456", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "bbbb2222-3333-4444-8555-666666666666"
        }

        val secondActor = vm.currentActor.value!!
        assertTrue(secondActor.safeAllowedWorkerTabs.isEmpty())
        assertTrue(resolveVisibleWorkerTabs(secondActor).isEmpty())
        assertEquals("", vm.activeWorkerTabKey.value)
        assertTrue(
            "Second account with allowed_worker_tabs=[] must never inherit previous account's worker scopes or fire worker requests",
            requestedPaths.none { it.startsWith("/api/direct-entry/workers") }
        )
    }

    @Test
    fun `regression 6 - uploader tab uses own submissions endpoint and never calls workers with scope uploader or export with scope all`() {
        val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths.add(path)
                return when {
                    path.startsWith("/api/direct-entry/submissions") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "items": [
                                {
                                  "submission_id": "11111111-2222-4333-8444-555555555555",
                                  "state": "SUBMITTED",
                                  "version": 1,
                                  "entry_count": 2,
                                  "created_at": "2026-10-25T08:00:00Z",
                                  "updated_at": "2026-10-25T08:05:00Z",
                                  "submitted_at": "2026-10-25T08:05:00Z",
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
                    path.startsWith("/api/direct-entry/workers") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "scope": "managed",
                              "items": [],
                              "summary": {"total_workers": 0, "project_count": 0, "recruiter_count": 0},
                              "page_size": 25,
                              "has_more": false,
                              "next_cursor": null
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"data":{}}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg6_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg6_prefs_${System.nanoTime()}.preferences_pb")
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

        // Case A: allowed_worker_tabs = ["uploader", "managed"]
        val uploaderAndManagedActor = AppActor(
            appUserId = "66661111-2222-4333-8444-555555555555",
            displayName = "Uploader Và Managed",
            capabilities = listOf("submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("uploader", "managed")
        )
        val currentActorState = androidx.compose.runtime.mutableStateOf(uploaderAndManagedActor)

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
            !vm.workerLoading.value && vm.ownSubmissions.value.size == 1
        }
        composeTestRule.waitForIdle()

        assertEquals("uploader", vm.activeWorkerTabKey.value)
        assertTrue(requestedPaths.any { it.startsWith("/api/direct-entry/submissions") })
        assertTrue(
            "Must NEVER call /api/direct-entry/workers?scope=uploader: $requestedPaths",
            requestedPaths.none { it.contains("scope=uploader") }
        )
        assertTrue(
            "Must NEVER export with scope=all: $requestedPaths",
            requestedPaths.none { it.contains("workers/export") && it.contains("scope=all") }
        )

        // Case B: allowed_worker_tabs = ["uploader"] only
        requestedPaths.clear()
        val uploaderOnlyActor = AppActor(
            appUserId = "66662222-2222-4333-8444-555555555555",
            displayName = "Chỉ Uploader",
            capabilities = listOf("submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("uploader")
        )
        currentActorState.value = uploaderOnlyActor
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workerLoading.value && requestedPaths.any { it.startsWith("/api/direct-entry/submissions") }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("worker_scope_tab_uploader").assertIsDisplayed()
        composeTestRule.onNodeWithTag("worker_scope_tab_managed").assertDoesNotExist()
        composeTestRule.onNodeWithTag("export_workers_excel_button").assertDoesNotExist()
        assertTrue(
            "Uploader-only actor must NEVER request /api/direct-entry/workers: $requestedPaths",
            requestedPaths.none { it.startsWith("/api/direct-entry/workers") }
        )
    }

    @Test
    fun `regression 7 - catalog requires entry_create or entry_own and Cap moi requires entry_create and submission_create while null actor fails closed`() {
        val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestedPaths.add(request.path.orEmpty())
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"ok":true,"data":{},"items":[],"has_more":false,"next_cursor":null}""")
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg7_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg7_prefs_${System.nanoTime()}.preferences_pb")
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

        // 1. Null actor fails closed -> zero catalog or salary-advance/context calls
        requestedPaths.clear()
        assertNull(vm.currentActor.value)
        vm.loadCatalogIfNeeded(force = true)
        vm.loadSalaryAdvanceContextIfNeeded(force = true)
        vm.refreshOwnSubmissionsForHistory()
        vm.selectWorkerTab("managed")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(
            "Null actor must never call catalog or salary-advance/context: $requestedPaths",
            requestedPaths.none {
                it.startsWith("/api/direct-entry/catalog") || it.startsWith("/api/salary-advance/context")
            }
        )

        // 2. Contract predicates for luanpt, recruited-only, uploader-only, entry_own-only, and full creator
        val luanptActor = AppActor(
            appUserId = "77770001-2222-4333-8444-555555555555",
            displayName = "Phạm Thành Luân",
            capabilities = listOf("entry_read", "submission_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = emptyList()
        )
        val recruitedOnlyActor = AppActor(
            appUserId = "77770002-2222-4333-8444-555555555555",
            displayName = "Chỉ Recruited",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("recruited")
        )
        val uploaderOnlyActor = AppActor(
            appUserId = "77770003-2222-4333-8444-555555555555",
            displayName = "Chỉ Uploader",
            capabilities = listOf("submission_read", "submission_create"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("uploader")
        )
        val entryOwnOnlyActor = AppActor(
            appUserId = "77770004-2222-4333-8444-555555555555",
            displayName = "Chỉ Entry Own",
            capabilities = listOf("entry_own"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("recruited")
        )
        val fullCreatorActor = AppActor(
            appUserId = "77770005-2222-4333-8444-555555555555",
            displayName = "Đủ Quyền Cấp Mới",
            capabilities = listOf("entry_create", "submission_create"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("uploader")
        )

        assertFalse(luanptActor.canLoadDirectEntryCatalog())
        assertFalse(luanptActor.canAccessNewEntryScreen())
        assertFalse(recruitedOnlyActor.canLoadDirectEntryCatalog())
        assertFalse(recruitedOnlyActor.canAccessNewEntryScreen())
        assertFalse(uploaderOnlyActor.canLoadDirectEntryCatalog())
        assertFalse(uploaderOnlyActor.canAccessNewEntryScreen())
        assertTrue("entry_own is allowed to read catalog per server contract", entryOwnOnlyActor.canLoadDirectEntryCatalog())
        assertFalse("entry_own without entry_create && submission_create cannot enter Cấp mới", entryOwnOnlyActor.canAccessNewEntryScreen())
        assertTrue(fullCreatorActor.canLoadDirectEntryCatalog())
        assertTrue(fullCreatorActor.canAccessNewEntryScreen())

        // 3. Rendering DirectEntryNewWorkersScreen for luanpt, recruited-only, or uploader-only blocks UI and never calls catalog
        val currentScreenActor = androidx.compose.runtime.mutableStateOf(luanptActor)
        composeTestRule.setContent {
            MyApplicationTheme {
                DirectEntryNewWorkersScreen(
                    actor = currentScreenActor.value,
                    viewModel = vm,
                    onNavigateToWorkersList = {},
                    onBackToDashboard = {}
                )
            }
        }
        for (deniedActor in listOf(luanptActor, recruitedOnlyActor, uploaderOnlyActor)) {
            currentScreenActor.value = deniedActor
            composeTestRule.runOnIdle {
                vm.loadCatalogIfNeeded(force = true, actor = deniedActor)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Bạn không có quyền truy cập chức năng Cấp mới hồ sơ NLĐ.").assertIsDisplayed()
        }
        assertTrue(
            "luanpt, recruited-only, and uploader-only must NEVER request /api/direct-entry/catalog: $requestedPaths",
            requestedPaths.none { it.startsWith("/api/direct-entry/catalog") }
        )
    }

    @Test
    fun `regression 8 - delayed response from Account A after logout and login Account B never commits into Account B state`() {
        var activeAccount = "account_a"
        val accountACatalogStarted = java.util.concurrent.CountDownLatch(1)
        val releaseAccountACatalog = java.util.concurrent.CountDownLatch(1)

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> {
                        val isA = activeAccount == "account_a"
                        val userId = if (isA) "aaaa0001-2222-4333-8444-555555555555" else "bbbb0002-2222-4333-8444-555555555555"
                        val caps = if (isA) """["entry_create", "submission_create"]""" else """["entry_read"]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .addHeader("Set-Cookie", "mini_bi_sid=sid_$activeAccount; Path=/; HttpOnly")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "display_name": "${if (isA) "Account A" else "Account B"}",
                                    "capabilities": $caps,
                                    "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                    "self_recruiter_suggestion": null
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/session") -> {
                        val isA = activeAccount == "account_a"
                        val userId = if (isA) "aaaa0001-2222-4333-8444-555555555555" else "bbbb0002-2222-4333-8444-555555555555"
                        val tabs = if (isA) """["managed", "uploader"]""" else """[]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "allowed_worker_tabs": $tabs
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/catalog") -> {
                        accountACatalogStarted.countDown()
                        releaseAccountACatalog.await(5, TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "catalog": {
                                    "effective_date": "2026-10-30",
                                    "projects": [{"project_id": "PRJ_STALE_A", "display_name": "Dự án Của A"}],
                                    "recruiters": [],
                                    "banks": [],
                                    "labor_types": []
                                  }
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
        val cookieFile = File(app.cacheDir, "reg8_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg8_prefs_${System.nanoTime()}.preferences_pb")
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

        // 1. Login as Account A and start an in-flight catalog request
        vm.login("account_a@hrpartner.vn", "passA", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa0001-2222-4333-8444-555555555555"
        }
        vm.loadCatalogIfNeeded(effectiveDateIso = "2026-10-30", force = true)
        assertTrue(
            "Expected Account A catalog request to start in-flight",
            accountACatalogStarted.await(5, TimeUnit.SECONDS)
        )

        // 2. While Account A catalog request is still in-flight, logout and login as Account B
        vm.logout()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.currentActor.value == null
        }
        activeAccount = "account_b"
        vm.login("account_b@hrpartner.vn", "passB", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "bbbb0002-2222-4333-8444-555555555555"
        }

        // 3. Release Account A's delayed HTTP response -> must NOT overwrite Account B's state
        releaseAccountACatalog.countDown()
        Thread.sleep(250)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals("bbbb0002-2222-4333-8444-555555555555", vm.currentActor.value?.appUserId)
        assertNull("Delayed catalog response from Account A must never commit into Account B state", vm.catalog.value)
    }

    @Test
    fun `regression 9 - overlapping lifecycle calls deduplicate in-flight requests to count 1 per endpoint user and params`() {
        val salaryContextCount = java.util.concurrent.atomic.AtomicInteger(0)
        val workersManagedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val catalogCount = java.util.concurrent.atomic.AtomicInteger(0)

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/salary-advance/context") -> {
                        salaryContextCount.incrementAndGet()
                        MockResponse()
                            .setBodyDelay(200, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "data": {
                                    "app_user_id": "99990001-2222-4333-8444-555555555555",
                                    "display_name": "PM Dedupe",
                                    "authorization_date": "2026-10-30",
                                    "is_full_admin": false,
                                    "is_bod": false,
                                    "is_project_manager": true,
                                    "is_project_accountant": false,
                                    "is_supervisory_viewer": false,
                                    "managed_projects": []
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/workers") && path.contains("scope=managed") -> {
                        workersManagedCount.incrementAndGet()
                        MockResponse()
                            .setBodyDelay(200, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "scope": "managed",
                                  "items": [],
                                  "summary": {"total_workers": 0, "project_count": 0, "recruiter_count": 0},
                                  "page_size": 25,
                                  "has_more": false,
                                  "next_cursor": null
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/catalog") -> {
                        catalogCount.incrementAndGet()
                        MockResponse()
                            .setBodyDelay(200, TimeUnit.MILLISECONDS)
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "catalog": {
                                    "effective_date": "2026-10-30",
                                    "projects": [],
                                    "recruiters": [],
                                    "banks": [],
                                    "labor_types": []
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"data":{}}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg9_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg9_prefs_${System.nanoTime()}.preferences_pb")
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
            appUserId = "99990001-2222-4333-8444-555555555555",
            displayName = "PM Dedupe",
            capabilities = listOf("entry_create", "submission_create"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "recruited")
        )

        // Fire simultaneous overlapping triggers (simulating navigateTo + LaunchedEffect + duplicate callers)
        composeTestRule.setContent {
            MyApplicationTheme {
                WorkersAndDirectEntryScreen(
                    actor = pmActor,
                    viewModel = vm,
                    onBackToDashboard = {}
                )
            }
        }
        composeTestRule.runOnIdle {
            vm.loadSalaryAdvanceContextIfNeeded(actor = pmActor)
            vm.loadSalaryAdvanceContextIfNeeded(actor = pmActor)
            vm.ensureInitialWorkerTabLoaded(pmActor)
            vm.selectWorkerTab("managed", actorOverride = pmActor)
            vm.loadCatalogIfNeeded(effectiveDateIso = "2026-10-30", actor = pmActor)
            vm.loadCatalogIfNeeded(effectiveDateIso = "2026-10-30", actor = pmActor)
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workerLoading.value &&
                !vm.salaryAdvanceContextLoading.value &&
                !vm.catalogLoading.value &&
                vm.salaryAdvanceContext.value != null &&
                vm.catalog.value != null
        }
        composeTestRule.waitForIdle()

        assertEquals("Expected exactly 1 in-flight request to /api/salary-advance/context", 1, salaryContextCount.get())
        assertEquals("Expected exactly 1 in-flight request to /api/direct-entry/workers?scope=managed", 1, workersManagedCount.get())
        assertEquals("Expected exactly 1 in-flight request to /api/direct-entry/catalog", 1, catalogCount.get())
    }

    @Test
    @Config(qualifiers = "w480dp-h2400dp")
    fun `regression 10 - PT409 version conflict on direct edit never blind-retries reloads latest version preserves user draft and rotates idempotency key on re-confirm`() {
        val entryId = "aaaaaaaa-9999-4222-8333-444444444444"
        var currentServerEntryVersion = 3
        var shouldReturnConflict = true
        val capturedEditRequests = java.util.Collections.synchronizedList(mutableListOf<Pair<String, JSONObject>>())

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .addHeader("Set-Cookie", "mini_bi_sid=sid_reg10; Path=/; HttpOnly")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "aaaa0010-2222-4333-8444-555555555555",
                                "display_name": "Admin Direct Edit",
                                "capabilities": ["entry_read", "entry_direct_edit", "document_upload"],
                                "scopes": [{"kind": "all", "reference": "global", "valid_from": "2026-01-01", "valid_to": null}],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "aaaa0010-2222-4333-8444-555555555555",
                                "allowed_worker_tabs": ["managed"]
                              }
                            }
                            """.trimIndent()
                        )
                    path == "/api/direct-entry/entries/$entryId" && request.method == "GET" -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "entry": {
                                "entry_id": "$entryId",
                                "submission_id": "bbbbbbbb-1111-4222-8333-444444444444",
                                "version": $currentServerEntryVersion,
                                "project_id": "PRJ_01",
                                "first_work_date": "2026-10-01",
                                "employee_code": "hrp-1026-000101",
                                "recruiter_id": "cccccccc-1111-4222-8333-444444444444",
                                "team_id": "TEAM_01",
                                "provider_type": "hrp",
                                "labor_type": "TEMPORARY",
                                "labor_type_display": "Thời vụ",
                                "worker_details": {
                                  "display_name": "Tên Trên Máy Chủ v$currentServerEntryVersion",
                                  "gender": {"state": "provided", "value": "MALE"},
                                  "national_id": {"state": "provided", "value": "001098012345"}
                                },
                                "payment": {"state": "empty"},
                                "employment_status": {
                                  "status": "ON",
                                  "effective_date": "2026-10-01",
                                  "version": 1
                                },
                                "documents": []
                              }
                            }
                            """.trimIndent()
                        )
                    path == "/api/direct-entry/entries/$entryId/privileged-edit" && request.method == "POST" -> {
                        val idempKey = request.getHeader("Idempotency-Key").orEmpty()
                        val bodyJson = JSONObject(request.body.readUtf8())
                        capturedEditRequests.add(idempKey to bodyJson)
                        if (shouldReturnConflict) {
                            currentServerEntryVersion = 7
                            MockResponse()
                                .setResponseCode(409)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":false,"code":"CONFLICT","message":"Version conflict"}""")
                        } else {
                            currentServerEntryVersion = 8
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":true,"entry_id":"$entryId","version":8}""")
                        }
                    }
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
        val cookieFile = File(app.cacheDir, "reg10_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg10_prefs_${System.nanoTime()}.preferences_pb")
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

        val workerRow = sampleWorker(
            entryId = entryId,
            canView = true,
            canProposeChange = false,
            canDirectEdit = true
        ).copy(entryVersion = 3)

        // Authenticate session first so currentActor has entry_direct_edit capability
        vm.login("admin_reg10@hrpartner.vn", "pass10", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa0010-2222-4333-8444-555555555555"
        }

        // 1. Open Direct Edit -> loads baseline version 3
        vm.openDirectEdit(workerRow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.directEditTarget.value?.second?.version == 3
        }

        composeTestRule.setContent {
            val targetPair = vm.directEditTarget.collectAsState().value
            val busy = vm.workerMutationBusy.collectAsState().value
            val error = vm.workerMutationError.collectAsState().value
            MyApplicationTheme {
                if (targetPair != null) {
                    WorkerPrivilegedEditDialog(
                        worker = targetPair.first,
                        baselineDetail = targetPair.second,
                        catalog = null,
                        busy = busy,
                        error = error,
                        onDismiss = { vm.closeDirectEdit() },
                        onSubmit = { ver, patch, reason ->
                            vm.submitDirectEdit(entryId, ver, patch, reason)
                        },
                        renderInDialog = false
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 2. User edits display_name to "Nguyễn Văn Bản Nháp Giữ Nguyên" and clicks Save -> server returns 409 CONFLICT
        composeTestRule.onNodeWithTag("direct_edit_display_name_input")
            .performScrollTo()
            .performTextReplacement("Nguyễn Văn Bản Nháp Giữ Nguyên")
        composeTestRule.onNodeWithTag("direct_edit_save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workerMutationBusy.value &&
                capturedEditRequests.size == 1 &&
                vm.directEditTarget.value?.second?.version == 7
        }
        composeTestRule.waitForIdle()

        // Must NOT blind-retry (only 1 POST request sent), must reload version 7, keep dialog open, and preserve user's draft input
        assertEquals("Must not automatically blind-retry on 409 CONFLICT", 1, capturedEditRequests.size)
        assertEquals(3, capturedEditRequests[0].second.getInt("expected_entry_version"))
        assertEquals(7, vm.directEditTarget.value?.second?.version)
        assertTrue(vm.workerMutationError.value.orEmpty().contains("thay đổi trên máy chủ"))
        composeTestRule.onNodeWithTag("direct_edit_display_name_input")
            .assertTextContains("Nguyễn Văn Bản Nháp Giữ Nguyên")

        // 3. User reviews and confirms Save again -> sends refreshed expected_entry_version = 7 and a fresh Idempotency-Key
        shouldReturnConflict = false
        composeTestRule.onNodeWithTag("direct_edit_save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.workerMutationBusy.value && capturedEditRequests.size == 2
        }
        composeTestRule.waitForIdle()

        val firstKey = capturedEditRequests[0].first
        val secondKey = capturedEditRequests[1].first
        assertTrue(firstKey.isNotBlank() && secondKey.isNotBlank())
        assertFalse("Re-submission after 409 must use a fresh Idempotency-Key", firstKey == secondKey)
        assertEquals(
            "Re-submission after user review must send refreshed expected_entry_version = 7",
            7,
            capturedEditRequests[1].second.getInt("expected_entry_version")
        )
        assertEquals(
            "Nguyễn Văn Bản Nháp Giữ Nguyên",
            capturedEditRequests[1].second.getJSONObject("patch").getJSONObject("worker_details").getString("display_name")
        )
    }

    @Test
    fun `regression 11 - delayed loadMoreWorkersOrSubmissions openEntryDetail and openDirectEdit from Account A never mutate Account B state`() {
        var activeAccount = "account_a"
        var initialLoadDone = false
        val loadMoreStarted = java.util.concurrent.CountDownLatch(1)
        val openDetailStarted = java.util.concurrent.CountDownLatch(1)
        val releaseAccountAResponses = java.util.concurrent.CountDownLatch(1)
        val entryIdA = "aaaaaaaa-1111-4222-8333-444444444444"

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> {
                        val isA = activeAccount == "account_a"
                        val userId = if (isA) "aaaa0011-2222-4333-8444-555555555555" else "bbbb0011-2222-4333-8444-555555555555"
                        val caps = if (isA) """["entry_read", "entry_direct_edit", "document_upload"]""" else """["entry_read"]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .addHeader("Set-Cookie", "mini_bi_sid=sid_$activeAccount; Path=/; HttpOnly")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "display_name": "${if (isA) "Account A" else "Account B"}",
                                    "capabilities": $caps,
                                    "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                    "self_recruiter_suggestion": null
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/session") -> {
                        val isA = activeAccount == "account_a"
                        val userId = if (isA) "aaaa0011-2222-4333-8444-555555555555" else "bbbb0011-2222-4333-8444-555555555555"
                        val tabs = if (isA) """["managed"]""" else """[]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "allowed_worker_tabs": $tabs
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/workers") && path.contains("cursor=cur-a-page-2") -> {
                        loadMoreStarted.countDown()
                        releaseAccountAResponses.await(5, TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "scope": "managed",
                                  "items": [
                                    {
                                      "entry_id": "eeeeeeee-2222-4222-8333-444444444444",
                                      "submission_id": "bbbbbbbb-2222-4222-8333-444444444444",
                                      "entry_version": 1,
                                      "employee_code": "hrp-1026-000202",
                                      "display_name": "Công Nhân Trang 2 Của A",
                                      "project_id": "PRJ_01",
                                      "project_display": "Dự án A",
                                      "recruiter_id": "cccccccc-1111-4222-8333-444444444444",
                                      "recruiter_display": "Tuyển A",
                                      "provider_type": "hrp",
                                      "labor_type": "TEMPORARY",
                                      "labor_type_display": "Thời vụ",
                                      "first_work_date": "2026-10-01",
                                      "employment_status": "ON",
                                      "employment_effective_date": "2026-10-01",
                                      "employment_status_version": 1,
                                      "allowed_actions": {"view": true, "propose_change": false, "direct_edit": true}
                                    }
                                  ],
                                  "summary": {"total_workers": 2, "project_count": 1, "recruiter_count": 1},
                                  "page_size": 25,
                                  "has_more": false,
                                  "next_cursor": null
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/workers") -> {
                        initialLoadDone = true
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "scope": "managed",
                                  "items": [
                                    {
                                      "entry_id": "$entryIdA",
                                      "submission_id": "bbbbbbbb-1111-4222-8333-444444444444",
                                      "entry_version": 1,
                                      "employee_code": "hrp-1026-000101",
                                      "display_name": "Công Nhân Trang 1 Của A",
                                      "project_id": "PRJ_01",
                                      "project_display": "Dự án A",
                                      "recruiter_id": "cccccccc-1111-4222-8333-444444444444",
                                      "recruiter_display": "Tuyển A",
                                      "provider_type": "hrp",
                                      "labor_type": "TEMPORARY",
                                      "labor_type_display": "Thời vụ",
                                      "first_work_date": "2026-10-01",
                                      "employment_status": "ON",
                                      "employment_effective_date": "2026-10-01",
                                      "employment_status_version": 1,
                                      "allowed_actions": {"view": true, "propose_change": false, "direct_edit": true}
                                    }
                                  ],
                                  "summary": {"total_workers": 2, "project_count": 1, "recruiter_count": 1},
                                  "page_size": 25,
                                  "has_more": true,
                                  "next_cursor": "cur-a-page-2"
                                }
                                """.trimIndent()
                            )
                    }
                    path == "/api/direct-entry/entries/$entryIdA" && request.method == "GET" -> {
                        openDetailStarted.countDown()
                        releaseAccountAResponses.await(5, TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "entry": {
                                    "entry_id": "$entryIdA",
                                    "submission_id": "bbbbbbbb-1111-4222-8333-444444444444",
                                    "version": 2,
                                    "project_id": "PRJ_01",
                                    "first_work_date": "2026-10-01",
                                    "employee_code": "hrp-1026-000101",
                                    "recruiter_id": "cccccccc-1111-4222-8333-444444444444",
                                    "provider_type": "hrp",
                                    "labor_type": "TEMPORARY",
                                    "worker_details": {"display_name": "Chi Tiết Của A"},
                                    "payment": {"state": "empty"},
                                    "employment_status": {"status": "ON", "effective_date": "2026-10-01", "version": 1},
                                    "documents": []
                                  }
                                }
                                """.trimIndent()
                            )
                    }
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
        val cookieFile = File(app.cacheDir, "reg11_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg11_prefs_${System.nanoTime()}.preferences_pb")
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

        // 1. Login Account A and load initial page 1 (which sets workerNextCursor = "cur-a-page-2")
        vm.login("account_a@hrpartner.vn", "passA", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value &&
                vm.currentActor.value?.appUserId == "aaaa0011-2222-4333-8444-555555555555" &&
                vm.currentActor.value?.allowedWorkerTabs == listOf("managed")
        }
        vm.ensureInitialWorkerTabLoaded(vm.currentActor.value!!)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            initialLoadDone && vm.workerRows.value.size == 1 && vm.workerHasMore.value
        }

        val workerRowA = vm.workerRows.value.first()

        // 2. Account A starts loadMoreWorkersOrSubmissions AND openEntryDetail / openDirectEdit (held in-flight)
        vm.loadMoreWorkersOrSubmissions()
        vm.openEntryDetail(entryIdA)
        vm.openDirectEdit(workerRowA)
        assertTrue("Expected Account A loadMore to start", loadMoreStarted.await(5, TimeUnit.SECONDS))
        assertTrue("Expected Account A openEntryDetail/openDirectEdit to start", openDetailStarted.await(5, TimeUnit.SECONDS))

        // 3. Logout Account A and login Account B before Account A responses complete
        vm.logout()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.currentActor.value == null
        }
        activeAccount = "account_b"
        vm.login("account_b@hrpartner.vn", "passB", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "bbbb0011-2222-4333-8444-555555555555"
        }

        // 4. Release Account A's delayed load-more and detail responses -> Account B state must contain zero data from A
        releaseAccountAResponses.countDown()
        Thread.sleep(250)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals("bbbb0011-2222-4333-8444-555555555555", vm.currentActor.value?.appUserId)
        assertTrue("Account B workerRows must not contain appended items from Account A", vm.workerRows.value.isEmpty())
        assertNull("Account B selectedEntryDetail must be null", vm.selectedEntryDetail.value)
        assertNull("Account B directEditTarget must be null", vm.directEditTarget.value)
        assertFalse("Account B workerLoading must be false", vm.workerLoading.value)
        assertFalse("Account B workerMutationBusy must be false", vm.workerMutationBusy.value)
    }

    @Test
    fun `regression 12 - delayed PT409 reload for direct edit and work report from Account A never mutates Account B state`() {
        var activeAccount = "account_a"
        val entryIdA = "aaaaaaaa-1212-4222-8333-444444444444"
        val obligationIdA = "dddddddd-1212-4222-8333-444444444444"
        var holdReloads = false
        val entryReloadStarted = java.util.concurrent.CountDownLatch(1)
        val obligationReloadStarted = java.util.concurrent.CountDownLatch(1)
        val releaseReloads = java.util.concurrent.CountDownLatch(1)

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> {
                        val isA = activeAccount == "account_a"
                        val userId = if (isA) "aaaa0012-2222-4333-8444-555555555555" else "bbbb0012-2222-4333-8444-555555555555"
                        val caps = if (isA) """["entry_read", "entry_direct_edit", "document_upload"]""" else """["entry_read"]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .addHeader("Set-Cookie", "mini_bi_sid=sid_$activeAccount; Path=/; HttpOnly")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "display_name": "${if (isA) "Account A" else "Account B"}",
                                    "capabilities": $caps,
                                    "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                    "self_recruiter_suggestion": null
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/direct-entry/session") -> {
                        val isA = activeAccount == "account_a"
                        val userId = if (isA) "aaaa0012-2222-4333-8444-555555555555" else "bbbb0012-2222-4333-8444-555555555555"
                        val tabs = if (isA) """["managed"]""" else """[]"""
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "$userId",
                                    "allowed_worker_tabs": $tabs
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path == "/api/direct-entry/entries/$entryIdA/privileged-edit" && request.method == "POST" -> MockResponse()
                        .setResponseCode(409)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"CONFLICT","message":"Version conflict"}""")
                    path == "/api/direct-entry/entries/$entryIdA" && request.method == "GET" -> {
                        if (holdReloads) {
                            entryReloadStarted.countDown()
                            releaseReloads.await(5, TimeUnit.SECONDS)
                        }
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "entry": {
                                    "entry_id": "$entryIdA",
                                    "submission_id": "bbbbbbbb-1212-4222-8333-444444444444",
                                    "version": ${if (holdReloads) 9 else 3},
                                    "project_id": "PRJ_01",
                                    "first_work_date": "2026-10-01",
                                    "employee_code": "hrp-1026-000101",
                                    "recruiter_id": "cccccccc-1111-4222-8333-444444444444",
                                    "provider_type": "hrp",
                                    "labor_type": "TEMPORARY",
                                    "worker_details": {"display_name": "Hồ Sơ Của A"},
                                    "payment": {"state": "empty"},
                                    "employment_status": {"status": "ON", "effective_date": "2026-10-01", "version": 1},
                                    "documents": []
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path == "/api/work-reports/submissions/$obligationIdA/submit" && request.method == "POST" -> MockResponse()
                        .setResponseCode(409)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"CONFLICT","message":"Obligation conflict"}""")
                    path == "/api/work-reports/obligations/$obligationIdA" && request.method == "GET" -> {
                        if (holdReloads) {
                            obligationReloadStarted.countDown()
                            releaseReloads.await(5, TimeUnit.SECONDS)
                        }
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "data": {
                                    "obligation": {
                                      "obligation_id": "$obligationIdA",
                                      "template_id": "dddd1111-eeee-4222-8333-ffff44445555",
                                      "period_type": "DAILY",
                                      "period_key": "2026-10-30",
                                      "period_start_date": "2026-10-30",
                                      "period_end_date": "2026-10-30",
                                      "due_at": "2026-12-31T17:00:00Z",
                                      "is_mandatory": true,
                                      "obligation_status": "ACTIVE",
                                      "version": 5,
                                      "is_overdue": false
                                    },
                                    "template": {
                                      "template_code": "TPL_A",
                                      "display_name": "Báo Cáo Của A",
                                      "schema_json": {"contract_version": "work-report-schema/1.0", "sections": [], "fields": []}
                                    },
                                    "submission": {
                                      "submission_id": "99991111-2222-4333-8444-555566667777",
                                      "state": "DRAFT",
                                      "version": 5,
                                      "draft_payload_json": {}
                                    },
                                    "revisions": [],
                                    "latest_review_event": null
                                  }
                                }
                                """.trimIndent()
                            )
                    }
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
        val cookieFile = File(app.cacheDir, "reg12_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg12_prefs_${System.nanoTime()}.preferences_pb")
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

        // 1. Login Account A and open Direct Edit + Work Report Obligation Editor
        vm.login("account_a@hrpartner.vn", "passA", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa0012-2222-4333-8444-555555555555"
        }

        val workerRow = sampleWorker(entryId = entryIdA, canView = true, canProposeChange = false, canDirectEdit = true)
        val obligationEntityA = WorkReportObligationEntity(
            obligationId = obligationIdA,
            templateId = "dddd1111-eeee-4222-8333-ffff44445555",
            templateCode = "TPL_A",
            templateName = "Báo Cáo Của A",
            periodType = "DAILY",
            periodKey = "2026-10-30",
            periodStartDate = "2026-10-30",
            periodEndDate = "2026-10-30",
            dueAtIso = "2026-12-31T17:00:00Z",
            isMandatory = true,
            obligationStatus = "ACTIVE",
            submissionId = "99991111-2222-4333-8444-555566667777",
            submissionState = "DRAFT",
            submissionVersion = 1,
            obligationVersion = 1,
            schemaJsonRaw = "{}",
            payloadJsonRaw = "{}",
            latestReviewDecision = null,
            latestReviewReason = null,
            submittedAtIso = null
        )
        vm.openDirectEdit(workerRow)
        vm.openObligationEditor(obligationEntityA)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.directEditTarget.value?.second?.version == 3 &&
                vm.editingObligation.value?.obligationVersion == 5
        }

        // 2. Hold PT409 reload requests, then trigger submitDirectEdit and submitWorkReportFinal (both get 409 CONFLICT)
        holdReloads = true
        val patch = JSONObject().put("worker_details", JSONObject().put("display_name", "Sửa Bởi A"))
        vm.submitDirectEdit(entryIdA, 3, patch, null)
        vm.submitWorkReportFinal(obligationEntityA, JSONObject())

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            entryReloadStarted.count == 0L && obligationReloadStarted.count == 0L
        }

        // 3. Switch session to Account B while PT409 reload requests are still held in-flight
        vm.logout()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.currentActor.value == null
        }
        activeAccount = "account_b"
        vm.login("account_b@hrpartner.vn", "passB", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "bbbb0012-2222-4333-8444-555555555555"
        }
        vm.clearBanner()

        // 4. Release PT409 reload responses -> Account B's editor/detail/banner/error must remain untouched
        releaseReloads.countDown()
        Thread.sleep(250)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals("bbbb0012-2222-4333-8444-555555555555", vm.currentActor.value?.appUserId)
        assertNull("Account B directEditTarget must remain null", vm.directEditTarget.value)
        assertNull("Account B editingObligation must remain null", vm.editingObligation.value)
        assertNull("Account B workerMutationError must remain null", vm.workerMutationError.value)
        assertNull("Account B workReportsError must remain null", vm.workReportsError.value)
        assertNull("Account B statusBannerMessage must remain null", vm.statusBannerMessage.value)
    }

    @Test
    fun `regression 13 - null actor and missing capabilities fail closed with 0 network requests for retryDirectEditDocument submitDirectEdit openDirectEdit openEntryDetail and loadMore`() {
        val recordedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        var currentCaps = """["entry_read", "entry_direct_edit"]"""

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                recordedPaths.add("${request.method} $path")
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .addHeader("Set-Cookie", "mini_bi_sid=sid_reg13; Path=/; HttpOnly")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "aaaa0013-2222-4333-8444-555555555555",
                                "display_name": "Actor No Doc Upload",
                                "capabilities": $currentCaps,
                                "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "aaaa0013-2222-4333-8444-555555555555",
                                "allowed_worker_tabs": ["managed"]
                              }
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
        val cookieFile = File(app.cacheDir, "reg13_cookies_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg13_prefs_${System.nanoTime()}.preferences_pb")
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

        val entryId = "aaaaaaaa-1313-4222-8333-444444444444"
        val workerRow = sampleWorker(entryId = entryId, canView = true, canProposeChange = true, canDirectEdit = true)
        val tempFile = File(app.cacheDir, "reg13_test_doc_${System.nanoTime()}.jpg").apply {
            writeBytes(ByteArray(128) { 1 })
        }
        val stagedPortrait = com.example.data.StagedDocumentRef(
            stageId = "",
            documentType = "WORKER_PORTRAIT",
            fileName = "portrait.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 128L,
            localFilePath = tempFile.absolutePath
        )

        // Part 1: currentActor == null -> 0 network requests across all direct-edit / detail / pagination / retry operations
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.sessionBootstrapping.value
        }
        assertNull(vm.currentActor.value)
        recordedPaths.clear()
        vm.attachDirectEditStagedDocument("WORKER_PORTRAIT", stagedPortrait)
        vm.openEntryDetail(entryId)
        vm.openDirectEdit(workerRow)
        vm.openProposeChange(workerRow)
        vm.submitDirectEdit(entryId, 1, JSONObject().put("worker_details", JSONObject().put("display_name", "Test")), null)
        vm.retryDirectEditDocument("WORKER_PORTRAIT")
        vm.loadMoreWorkersOrSubmissions()
        Thread.sleep(150)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(
            "When currentActor == null, privileged-edit / retry / upload / detail / loadMore must make 0 network requests, but saw: $recordedPaths",
            recordedPaths.isEmpty()
        )

        // Part 2: Actor logged in WITHOUT document_upload capability -> retryDirectEditDocument and document-only submitDirectEdit make 0 network requests
        vm.login("nodoc@hrpartner.vn", "pass13", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa0013-2222-4333-8444-555555555555"
        }
        assertFalse(vm.currentActor.value!!.hasCapability("document_upload"))

        val tempFile2 = File(app.cacheDir, "reg13_test_doc2_${System.nanoTime()}.jpg").apply {
            writeBytes(ByteArray(128) { 1 })
        }
        val stagedPortrait2 = com.example.data.StagedDocumentRef(
            stageId = "",
            documentType = "WORKER_PORTRAIT",
            fileName = "portrait2.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 128L,
            localFilePath = tempFile2.absolutePath
        )
        recordedPaths.clear()
        vm.attachDirectEditStagedDocument("WORKER_PORTRAIT", stagedPortrait2)
        vm.retryDirectEditDocument("WORKER_PORTRAIT")
        vm.submitDirectEdit(entryId, 1, JSONObject(), null)
        Thread.sleep(150)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val uploadOrEditRequests = recordedPaths.filter {
            it.contains("/documents") || it.contains("/privileged-edit") || it.contains("/entries/")
        }
        assertTrue(
            "Actor without document_upload must make 0 document upload / retry requests, but saw: $uploadOrEditRequests",
            uploadOrEditRequests.isEmpty()
        )
        tempFile.delete()
        tempFile2.delete()
    }

    @Test
    @Config(qualifiers = "w480dp-h2400dp")
    fun `regression 14 - rapid worker switches in same session keep latest worker B for openEntryDetail openProposeChange and openDirectEdit and close invalidates pending`() {
        val entryIdA = "aaaa1414-1111-4333-8444-555555555555"
        val entryIdB = "bbbb1414-2222-4333-8444-555555555555"
        val entryIdC = "cccc1414-3333-4333-8444-555555555555"

        val releaseWorkerALatch = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val releaseWorkerCLatch = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val entryAStarted = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val entryCStarted = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))

        fun entryJson(entryId: String, version: Int, code: String, name: String): String =
            """
            {
              "ok": true,
              "entry": {
                "entry_id": "$entryId",
                "submission_id": "bbbbbbbb-1414-4222-8333-444444444444",
                "version": $version,
                "project_id": "p1",
                "first_work_date": "2026-10-01",
                "employee_code": "$code",
                "recruiter_id": "cccccccc-1111-4222-8333-444444444444",
                "provider_type": "hrp",
                "labor_type": "TEMPORARY",
                "worker_details": {"display_name": "$name"},
                "payment": {"state": "empty"},
                "employment_status": {"status": "ON", "effective_date": "2026-10-01", "version": 1},
                "documents": []
              }
            }
            """.trimIndent()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .addHeader("Set-Cookie", "mini_bi_sid=sid_reg14; Path=/; HttpOnly")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "aaaa0014-2222-4333-8444-555555555555",
                                "display_name": "Account 14",
                                "capabilities": ["entry_read", "entry_direct_edit", "entry_propose_change", "document_upload"],
                                "scopes": [{"kind": "own", "reference": "self", "valid_from": "2026-01-01", "valid_to": null}],
                                "self_recruiter_suggestion": null
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/direct-entry/session") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "actor": {
                                "app_user_id": "aaaa0014-2222-4333-8444-555555555555",
                                "allowed_worker_tabs": ["managed"]
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
                              "data": {
                                "effective_date": "2026-11-09",
                                "projects": [{"project_id": "p1", "code": "P1", "display_name": "Dự án 1"}],
                                "recruiters": [],
                                "banks": [],
                                "labor_types": []
                              }
                            }
                            """.trimIndent()
                        )
                    path.contains("/api/direct-entry/entries/$entryIdA") -> {
                        entryAStarted.get().countDown()
                        releaseWorkerALatch.get().await(5, java.util.concurrent.TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(entryJson(entryIdA, 1, "hrp-1026-000114", "Hồ Sơ Chậm A"))
                    }
                    path.contains("/api/direct-entry/entries/$entryIdB") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(entryJson(entryIdB, 2, "hrp-1026-000214", "Hồ Sơ Nhanh B"))
                    path.contains("/api/direct-entry/entries/$entryIdC") -> {
                        entryCStarted.get().countDown()
                        releaseWorkerCLatch.get().await(5, java.util.concurrent.TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(entryJson(entryIdC, 3, "hrp-1026-000314", "Hồ Sơ Đã Đóng C"))
                    }
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
        val cookieFile = File(app.cacheDir, "reg14_cookie_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg14_prefs_${System.nanoTime()}.preferences_pb")
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

        vm.login("user14@hrpartner.vn", "pass14", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa0014-2222-4333-8444-555555555555"
        }

        val workerA = sampleWorker(entryId = entryIdA, canView = true, canProposeChange = true, canDirectEdit = true)
        val workerB = sampleWorker(entryId = entryIdB, canView = true, canProposeChange = true, canDirectEdit = true)
        val workerC = sampleWorker(entryId = entryIdC, canView = true, canProposeChange = true, canDirectEdit = true)

        // 1. openEntryDetail: A slow, B fast -> final state must remain B after A finishes
        entryAStarted.set(java.util.concurrent.CountDownLatch(1))
        releaseWorkerALatch.set(java.util.concurrent.CountDownLatch(1))
        vm.openEntryDetail(entryIdA)
        assertTrue("Expected openEntryDetail(A) request to start", entryAStarted.get().await(5, java.util.concurrent.TimeUnit.SECONDS))
        vm.openEntryDetail(entryIdB)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.selectedEntryDetail.value?.entryId == entryIdB
        }
        releaseWorkerALatch.get().countDown()
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("openEntryDetail must keep B and ignore late A", entryIdB, vm.selectedEntryDetail.value?.entryId)
        assertEquals("Hồ Sơ Nhanh B", vm.selectedEntryDetail.value?.displayName)

        // 2. openProposeChange: A slow, B fast -> final state must remain B after A finishes
        entryAStarted.set(java.util.concurrent.CountDownLatch(1))
        releaseWorkerALatch.set(java.util.concurrent.CountDownLatch(1))
        vm.openProposeChange(workerA)
        assertTrue("Expected openProposeChange(A) request to start", entryAStarted.get().await(5, java.util.concurrent.TimeUnit.SECONDS))
        vm.openProposeChange(workerB)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.proposeChangeTarget.value?.first?.entryId == entryIdB &&
                vm.proposeChangeTarget.value?.second?.entryId == entryIdB
        }
        releaseWorkerALatch.get().countDown()
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("openProposeChange must keep B and ignore late A", entryIdB, vm.proposeChangeTarget.value?.first?.entryId)
        assertEquals(entryIdB, vm.proposeChangeTarget.value?.second?.entryId)

        // 3. openDirectEdit: A slow, B fast -> final state must remain B after A finishes
        entryAStarted.set(java.util.concurrent.CountDownLatch(1))
        releaseWorkerALatch.set(java.util.concurrent.CountDownLatch(1))
        vm.openDirectEdit(workerA)
        assertTrue("Expected openDirectEdit(A) request to start", entryAStarted.get().await(5, java.util.concurrent.TimeUnit.SECONDS))
        vm.openDirectEdit(workerB)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.directEditTarget.value?.first?.entryId == entryIdB &&
                vm.directEditTarget.value?.second?.entryId == entryIdB
        }
        releaseWorkerALatch.get().countDown()
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("openDirectEdit must keep B and ignore late A", entryIdB, vm.directEditTarget.value?.first?.entryId)
        assertEquals(entryIdB, vm.directEditTarget.value?.second?.entryId)
        assertEquals("Hồ Sơ Nhanh B", vm.directEditTarget.value?.second?.displayName)

        // 4. closeEntryDetail / closeProposeChange / closeDirectEdit must invalidate in-flight requests
        entryCStarted.set(java.util.concurrent.CountDownLatch(3))
        releaseWorkerCLatch.set(java.util.concurrent.CountDownLatch(1))
        vm.openEntryDetail(entryIdC)
        vm.openProposeChange(workerC)
        vm.openDirectEdit(workerC)
        assertTrue("Expected C requests to start", entryCStarted.get().await(5, java.util.concurrent.TimeUnit.SECONDS))
        vm.closeEntryDetail()
        vm.closeProposeChange()
        vm.closeDirectEdit()
        releaseWorkerCLatch.get().countDown()
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNull("Closed entryDetail must remain null after pending C finishes", vm.selectedEntryDetail.value)
        assertNull("Closed proposeChangeTarget must remain null after pending C finishes", vm.proposeChangeTarget.value)
        assertNull("Closed directEditTarget must remain null after pending C finishes", vm.directEditTarget.value)
    }

    @Test
    @Config(qualifiers = "w480dp-h1800dp")
    fun `regression 15 - main menu replaces Cap moi with Tuyen dung and Workers screen gates Cap moi button by entry_create and submission_create while preserving draft`() {
        val fullActor = AppActor(
            appUserId = "aaaa0015-2222-4333-8444-555555555551",
            displayName = "Tuyển Dụng Đủ Quyền",
            capabilities = listOf("entry_create", "submission_create", "entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("managed", "uploader")
        )
        val noNewEntryActor = AppActor(
            appUserId = "aaaa0015-2222-4333-8444-555555555552",
            displayName = "Không Quyền Cấp Mới",
            capabilities = listOf("entry_read"),
            scopes = listOf(ActorScope("own", "self", "2026-01-01", null)),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = listOf("recruited")
        )
        val noWorkerTabsActor = AppActor(
            appUserId = "aaaa0015-2222-4333-8444-555555555553",
            displayName = "Không Tab Nhân Sự",
            capabilities = emptyList(),
            scopes = emptyList(),
            selfRecruiterSuggestion = null,
            allowedWorkerTabs = emptyList()
        )

        // 1-3. Main menu no longer contains NEW_ENTRY; RECRUITMENT replaces it for all authenticated users
        assertEquals(
            listOf(
                com.example.ui.MainDestination.DASHBOARD,
                com.example.ui.MainDestination.WORK_REPORTS,
                com.example.ui.MainDestination.ATTENDANCE,
                com.example.ui.MainDestination.WORKERS,
                com.example.ui.MainDestination.RECRUITMENT
            ),
            com.example.ui.resolveMainNavigationDestinations(fullActor)
        )
        assertEquals(
            listOf(
                com.example.ui.MainDestination.DASHBOARD,
                com.example.ui.MainDestination.WORK_REPORTS,
                com.example.ui.MainDestination.ATTENDANCE,
                com.example.ui.MainDestination.WORKERS,
                com.example.ui.MainDestination.RECRUITMENT
            ),
            com.example.ui.resolveMainNavigationDestinations(noNewEntryActor)
        )
        assertEquals(
            listOf(
                com.example.ui.MainDestination.DASHBOARD,
                com.example.ui.MainDestination.WORK_REPORTS,
                com.example.ui.MainDestination.ATTENDANCE,
                com.example.ui.MainDestination.RECRUITMENT
            ),
            com.example.ui.resolveMainNavigationDestinations(noWorkerTabsActor)
        )
        assertFalse(com.example.ui.resolveMainNavigationDestinations(fullActor).contains(com.example.ui.MainDestination.NEW_ENTRY))

        // 4-5. Workers screen button gating & draft preservation when opening NEW_ENTRY from Workers
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg15_cookie_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg15_prefs_${System.nanoTime()}.preferences_pb")
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

        val currentActorState = androidx.compose.runtime.mutableStateOf(noNewEntryActor)
        val showNewEntryScreen = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            MyApplicationTheme {
                val actor = currentActorState.value
                if (showNewEntryScreen.value) {
                    DirectEntryNewWorkersScreen(
                        actor = actor,
                        viewModel = vm,
                        onNavigateToWorkersList = {
                            vm.navigateTo(com.example.ui.MainDestination.WORKERS)
                            showNewEntryScreen.value = false
                        },
                        onBackToDashboard = {
                            vm.navigateTo(com.example.ui.MainDestination.DASHBOARD)
                            showNewEntryScreen.value = false
                        }
                    )
                } else {
                    WorkersAndDirectEntryScreen(
                        actor = actor,
                        viewModel = vm,
                        onBackToDashboard = {},
                        onSelectScopeTab = {},
                        onOpenNewEntryScreen = {
                            vm.navigateTo(com.example.ui.MainDestination.NEW_ENTRY)
                            showNewEntryScreen.value = true
                        }
                    )
                }
            }
        }

        // Without entry_create && submission_create -> button does not exist
        composeTestRule.onNodeWithTag("go_to_new_entry_from_workers").assertDoesNotExist()

        // With entry_create && submission_create -> button exists, and tapping it navigates to NEW_ENTRY while preserving draft
        vm.ensureInitialNewWorkerDraftRow()
        vm.newWorkerDraftRows[0] = vm.newWorkerDraftRows[0].copy(fullName = "Hồ Sơ Nháp Đang Giữ")
        composeTestRule.runOnIdle {
            currentActorState.value = fullActor
            vm.navigateTo(com.example.ui.MainDestination.WORKERS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("go_to_new_entry_from_workers").assertIsDisplayed()
        composeTestRule.onNodeWithTag("go_to_new_entry_from_workers").performClick()
        composeTestRule.waitForIdle()
        assertTrue(showNewEntryScreen.value)
        assertEquals("Hồ Sơ Nháp Đang Giữ", vm.newWorkerDraftRows.first().fullName)
    }

    @Test
    @Config(qualifiers = "w480dp-h2200dp")
    fun `regression 16 - recruitment posts pagination, snapshot retry once, stale response guard, 404 detail removal, read-only UI, and session purge`() {
        val brief1 = "11111111-2222-4333-8444-555555555501"
        val brief2 = "11111111-2222-4333-8444-555555555502"
        val briefSlow = "11111111-2222-4333-8444-555555555599"
        val slowDetailStarted = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val releaseSlowDetail = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val snapshotInvalidMode = java.util.concurrent.atomic.AtomicBoolean(false)
        val notFoundDetailMode = java.util.concurrent.atomic.AtomicBoolean(false)
        val recordedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())

        fun buildPostJson(briefId: String, projectName: String, creatorName: String): String = """
            {
              "brief_id": "$briefId",
              "version": 1,
              "status": "PUBLISHED",
              "project": {
                "project_id": "aaaaaaaa-1111-4222-8333-444444444401",
                "display_name": "$projectName"
              },
              "industrial_park": {
                "industrial_park_id": "aaaaaaaa-1111-4222-8333-444444444402",
                "industrial_park_code": "KCN_YEN_PHONG",
                "display_name": "KCN Yên Phong",
                "active": true
              },
              "province": {
                "province_id": "aaaaaaaa-1111-4222-8333-444444444403",
                "province_code": "BAC_NINH",
                "display_name": "Bắc Ninh"
              },
              "published_at": "2026-11-10T08:00:00Z",
              "updated_at": "2026-11-10T08:00:00Z",
              "withdrawn_at": null,
              "created_by_display_name": "$creatorName",
              "updated_by_display_name": "$creatorName",
              "withdrawn_by_display_name": null,
              "general": {
                "customer_name": "Công ty Điện tử A",
                "investment_origin": null,
                "manufacturing_industry": "Điện tử",
                "job_description": "Vận hành máy",
                "environment_types": ["CLEAN_ROOM"],
                "work_postures": ["SITTING"],
                "shift_pattern": "TWO_SHIFTS",
                "shift_details": "Ca 12h",
                "manager_name": "Tên Quản Lý Cấm Hiển Thị",
                "manager_phone": "0912345678",
                "labor_types": [{"labor_type_key": "thoi_vu", "display_name": "Thời vụ"}],
                "supply_schedule_text": null,
                "expected_headcount": 100
              },
              "candidate": {
                "age_min": 18,
                "age_max": 38,
                "gender_requirement": "ANY",
                "education_requirement": "NONE",
                "experience_required": false,
                "experience_details": null,
                "accept_former_customer_workers": true,
                "entry_assessment_text": null,
                "other_requirements_text": null
              },
              "compensation": {
                "base_salary_vnd": "8500000",
                "base_salary_conditions": null,
                "attendance_bonus_vnd": null,
                "attendance_bonus_conditions": null,
                "living_allowance_vnd": null,
                "living_allowance_conditions": null,
                "seniority_allowance_vnd": null,
                "seniority_allowance_conditions": null,
                "eyesight_allowance_vnd": null,
                "eyesight_allowance_conditions": null,
                "other_allowance_text": null,
                "average_income_min_vnd": "9000000",
                "average_income_max_vnd": "13000000",
                "salary_payment_schedule": "Ngày 15",
                "salary_advance_available": true,
                "salary_advance_amount_vnd": "1000000",
                "salary_advance_schedule": "Thứ 6",
                "weekly_salary_available": false,
                "weekly_salary_payment_schedule": null,
                "day_shift_8h_vnd": null,
                "day_shift_11h_vnd": null,
                "night_shift_8h_vnd": null,
                "night_shift_11h_vnd": null,
                "sunday_day_11h_vnd": null,
                "sunday_night_11h_vnd": null,
                "holiday_day_11h_vnd": null,
                "holiday_night_11h_vnd": null,
                "ppe_provided": false,
                "ppe_details": null,
                "other_benefits": null
              },
              "documents": {
                "require_original_cccd_and_copy": true,
                "require_certified_resume": false,
                "require_health_certificate_within_6_months": false,
                "require_photo_3x4": true,
                "require_degree_or_certificate": false,
                "require_social_insurance_book": false,
                "require_other_documents": false,
                "other_documents_text": null
              },
              "allowed_actions": {
                "can_edit": true,
                "can_withdraw": true
              }
            }
        """.trimIndent()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                recordedPaths.add(path)
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Set-Cookie", "mini_bi_session=sess-16; Path=/; HttpOnly")
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "user": {"id": "aaaa0016-2222-4333-8444-555555555555", "username": "rec.16", "fullName": "Recruiter 16", "role": "STAFF"},
                              "actor": {
                                "app_user_id": "aaaa0016-2222-4333-8444-555555555555",
                                "display_name": "Recruiter 16",
                                "capabilities": [],
                                "scopes": [],
                                "self_recruiter_suggestion": null,
                                "allowed_worker_tabs": []
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/recruitment-posts?") -> {
                        if (path.contains("cursor=cur-p2")) {
                            if (snapshotInvalidMode.get()) {
                                MockResponse()
                                    .setResponseCode(400)
                                    .setHeader("Content-Type", "application/json")
                                    .setBody("""{"ok":false,"error":{"code":"RECRUITMENT_POST_SNAPSHOT_INVALID","message":"Snapshot expired"}}""")
                            } else {
                                MockResponse()
                                    .setResponseCode(200)
                                    .setHeader("Content-Type", "application/json")
                                    .setBody(
                                        """
                                        {
                                          "ok": true,
                                          "data": {
                                            "items": [
                                              ${buildPostJson(brief1, "Dự Án 1 Trùng", "Người Tạo 1")},
                                              ${buildPostJson(brief2, "Dự Án 2 Mới", "Người Tạo 2")}
                                            ],
                                            "page_size": 10,
                                            "has_more": false,
                                            "next_cursor": null,
                                            "snapshot_token": "snap-p1"
                                          }
                                        }
                                        """.trimIndent()
                                    )
                            }
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """
                                    {
                                      "ok": true,
                                      "data": {
                                        "items": [${buildPostJson(brief1, "Dự Án 1", "Người Tạo Chính Xác")}],
                                        "page_size": 10,
                                        "has_more": true,
                                        "next_cursor": "cur-p2",
                                        "snapshot_token": "snap-p1"
                                      }
                                    }
                                    """.trimIndent()
                                )
                        }
                    }
                    path.contains("/api/recruitment-posts/$briefSlow") -> {
                        slowDetailStarted.get().countDown()
                        releaseSlowDetail.get().await(5, java.util.concurrent.TimeUnit.SECONDS)
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"ok":true,"data":{"post":${buildPostJson(briefSlow, "Dự Án Chậm", "Người Tạo Chậm")}}}""")
                    }
                    path.contains("/api/recruitment-posts/$brief1") -> {
                        if (notFoundDetailMode.get()) {
                            MockResponse()
                                .setResponseCode(404)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":false,"error":{"code":"RECRUITMENT_POST_NOT_FOUND","message":"Not found"}}""")
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":true,"data":{"post":${buildPostJson(brief1, "Dự Án 1", "Người Tạo Chính Xác")}}}""")
                        }
                    }
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"items":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg16_cookie_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg16_prefs_${System.nanoTime()}.preferences_pb")
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

        vm.login("rec16@hrpartner.vn", "pass16", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "aaaa0016-2222-4333-8444-555555555555"
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.RecruitmentPostsScreen(viewModel = vm)
            }
        }

        // Navigate to RECRUITMENT -> loads page 1 with page_size=10 and no cursor/snapshot_token
        vm.navigateTo(com.example.ui.MainDestination.RECRUITMENT)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPosts.value.size == 1 && !vm.recruitmentPostsLoading.value
        }
        composeTestRule.waitForIdle()

        assertTrue(recordedPaths.contains("/api/recruitment-posts?page_size=10"))
        assertFalse(recordedPaths.any { it.contains("/api/recruitment-posts/context") })

        // Card displays created_by_display_name, NEVER manager_name, and has no edit/withdraw/create buttons
        composeTestRule.onNodeWithText("Người Tạo Chính Xác", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Tên Quản Lý Cấm Hiển Thị", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Đăng tin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Chỉnh sửa").assertDoesNotExist()
        composeTestRule.onNodeWithText("Thu hồi").assertDoesNotExist()

        // Load more sends snapshot_token from page 1 + cursor, and deduplicates brief1
        vm.loadMoreRecruitmentPosts()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPosts.value.size == 2 && !vm.recruitmentPostsLoadingMore.value
        }
        assertTrue(recordedPaths.any { it.contains("cursor=cur-p2") && it.contains("snapshot_token=snap-p1") })
        assertEquals(listOf(brief1, brief2), vm.recruitmentPosts.value.map { it.briefId })

        // Open detail for brief1 -> renders 4 sections and created_by_display_name, then close
        vm.openRecruitmentPostDetail(brief1)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.selectedRecruitmentPostDetail.value?.briefId == brief1
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recruitment_post_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Thông tin chung dự án").assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Yêu cầu ứng viên").assertIsDisplayed()
        composeTestRule.onNodeWithText("3. Lương, thưởng và phúc lợi").assertIsDisplayed()
        composeTestRule.onNodeWithText("4. Hồ sơ ứng viên cần chuẩn bị").performScrollTo().assertIsDisplayed()
        vm.closeRecruitmentPostDetail()
        composeTestRule.waitForIdle()
        assertNull(vm.selectedRecruitmentPostDetail.value)

        // Slow detail request closed before completion must not commit state
        vm.openRecruitmentPostDetail(briefSlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            slowDetailStarted.get().count == 0L
        }
        vm.closeRecruitmentPostDetail()
        releaseSlowDetail.get().countDown()
        Thread.sleep(150)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNull(vm.selectedRecruitmentPostDetail.value)

        // 400 RECRUITMENT_POST_SNAPSHOT_INVALID on loadMore resets and reloads page 1 at most once
        vm.loadRecruitmentPosts(reset = true)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPosts.value.size == 1 && !vm.recruitmentPostsLoading.value
        }
        snapshotInvalidMode.set(true)
        val countBeforeInvalid = recordedPaths.size
        vm.loadMoreRecruitmentPosts()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            recordedPaths.size >= countBeforeInvalid + 2 && !vm.recruitmentPostsLoading.value
        }
        assertEquals(1, vm.recruitmentPosts.value.size)

        // 404 RECRUITMENT_POST_NOT_FOUND on detail removes item from list and shows banner
        notFoundDetailMode.set(true)
        vm.openRecruitmentPostDetail(brief1)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPosts.value.none { it.briefId == brief1 }
        }
        assertNull(vm.selectedRecruitmentPostDetail.value)
        assertEquals("Tin tuyển dụng không còn hiệu lực.", vm.statusBannerMessage.value)

        // Logout / session reset purges all recruitment state
        vm.logout()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.currentActor.value == null
        }
        assertTrue(vm.recruitmentPosts.value.isEmpty())
        assertNull(vm.selectedRecruitmentPostDetail.value)
        assertFalse(vm.recruitmentPostsHasMore.value)
    }

    @Test
    fun `regression 17 - recruitment posts 403 access denied, 404 list feature unavailable, 503 retryable error, and cross-session stale response isolation`() {
        val userAId = "aaaa0017-2222-4333-8444-555555555501"
        val userBId = "bbbb0017-2222-4333-8444-555555555502"
        val briefA = "11111111-2222-4333-8444-555555555571"
        val responseMode = java.util.concurrent.atomic.AtomicReference("HOLD_USER_A")
        val listAStarted = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))
        val releaseListA = java.util.concurrent.atomic.AtomicReference(java.util.concurrent.CountDownLatch(1))

        val samplePostJson = """
            {
              "brief_id": "$briefA",
              "version": 1,
              "status": "PUBLISHED",
              "project": {
                "project_id": "aaaaaaaa-1111-4222-8333-444444444401",
                "display_name": "Dự Án Của A"
              },
              "industrial_park": {
                "industrial_park_id": "aaaaaaaa-1111-4222-8333-444444444402",
                "industrial_park_code": "KCN_A",
                "display_name": "KCN A",
                "active": true
              },
              "province": {
                "province_id": "aaaaaaaa-1111-4222-8333-444444444403",
                "province_code": "BAC_NINH",
                "display_name": "Bắc Ninh"
              },
              "published_at": "2026-11-10T08:00:00Z",
              "updated_at": "2026-11-10T08:00:00Z",
              "withdrawn_at": null,
              "created_by_display_name": "Người Tạo A",
              "updated_by_display_name": "Người Tạo A",
              "withdrawn_by_display_name": null,
              "general": {
                "customer_name": "Công ty A",
                "investment_origin": null,
                "manufacturing_industry": "Điện tử",
                "job_description": "Sản xuất",
                "environment_types": ["CLEAN_ROOM"],
                "work_postures": ["SITTING"],
                "shift_pattern": "TWO_SHIFTS",
                "shift_details": "Ca 12h",
                "manager_name": "Quản Lý A",
                "manager_phone": "0900111222",
                "labor_types": [{"labor_type_key": "thoi_vu", "display_name": "Thời vụ"}],
                "supply_schedule_text": null,
                "expected_headcount": 50
              },
              "candidate": {
                "age_min": 18,
                "age_max": 35,
                "gender_requirement": "ANY",
                "education_requirement": "NONE",
                "experience_required": false,
                "experience_details": null,
                "accept_former_customer_workers": false,
                "entry_assessment_text": null,
                "other_requirements_text": null
              },
              "compensation": {
                "base_salary_vnd": "8000000",
                "base_salary_conditions": null,
                "attendance_bonus_vnd": null,
                "attendance_bonus_conditions": null,
                "living_allowance_vnd": null,
                "living_allowance_conditions": null,
                "seniority_allowance_vnd": null,
                "seniority_allowance_conditions": null,
                "eyesight_allowance_vnd": null,
                "eyesight_allowance_conditions": null,
                "other_allowance_text": null,
                "average_income_min_vnd": "8000000",
                "average_income_max_vnd": "12000000",
                "salary_payment_schedule": "Ngày 10",
                "salary_advance_available": false,
                "salary_advance_amount_vnd": null,
                "salary_advance_schedule": null,
                "weekly_salary_available": false,
                "weekly_salary_payment_schedule": null,
                "day_shift_8h_vnd": null,
                "day_shift_11h_vnd": null,
                "night_shift_8h_vnd": null,
                "night_shift_11h_vnd": null,
                "sunday_day_11h_vnd": null,
                "sunday_night_11h_vnd": null,
                "holiday_day_11h_vnd": null,
                "holiday_night_11h_vnd": null,
                "ppe_provided": false,
                "ppe_details": null,
                "other_benefits": null
              },
              "documents": {
                "require_original_cccd_and_copy": true,
                "require_certified_resume": false,
                "require_health_certificate_within_6_months": false,
                "require_photo_3x4": false,
                "require_degree_or_certificate": false,
                "require_social_insurance_book": false,
                "require_other_documents": false,
                "other_documents_text": null
              },
              "allowed_actions": {
                "can_edit": false,
                "can_withdraw": false
              }
            }
        """.trimIndent()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> {
                        val bodyStr = request.body.readUtf8()
                        val isUserB = bodyStr.contains("userB")
                        val uid = if (isUserB) userBId else userAId
                        val uname = if (isUserB) "userB" else "userA"
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Set-Cookie", "mini_bi_session=sess-$uname; Path=/; HttpOnly")
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "user": {"id": "$uid", "username": "$uname", "fullName": "$uname", "role": "STAFF"},
                                  "actor": {
                                    "app_user_id": "$uid",
                                    "display_name": "$uname",
                                    "capabilities": [],
                                    "scopes": [],
                                    "self_recruiter_suggestion": null,
                                    "allowed_worker_tabs": []
                                  }
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/recruitment-posts") -> {
                        when (responseMode.get()) {
                            "HOLD_USER_A" -> {
                                listAStarted.get().countDown()
                                releaseListA.get().await(5, java.util.concurrent.TimeUnit.SECONDS)
                                MockResponse()
                                    .setResponseCode(200)
                                    .setHeader("Content-Type", "application/json")
                                    .setBody(
                                        """
                                        {
                                          "ok": true,
                                          "data": {
                                            "items": [$samplePostJson],
                                            "page_size": 10,
                                            "has_more": false,
                                            "next_cursor": null,
                                            "snapshot_token": "snap-a"
                                          }
                                        }
                                        """.trimIndent()
                                    )
                            }
                            "FORBIDDEN" -> MockResponse()
                                .setResponseCode(403)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":false,"error":{"code":"ACTOR_NOT_AVAILABLE","message":"Forbidden"}}""")
                            "NOT_FOUND_LIST" -> MockResponse()
                                .setResponseCode(404)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":false,"error":{"code":"NOT_FOUND","message":"Endpoint not found"}}""")
                            "UNAVAILABLE_503" -> MockResponse()
                                .setResponseCode(503)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":false,"error":{"code":"RECRUITMENT_POST_UNAVAILABLE","message":"Temporarily unavailable"}}""")
                            else -> MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"ok":true,"data":{"items":[],"page_size":10,"has_more":false,"next_cursor":null,"snapshot_token":"snap-b"}}""")
                        }
                    }
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"items":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg17_cookie_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg17_prefs_${System.nanoTime()}.preferences_pb")
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

        // Login User A and start recruitment posts fetch (held in flight)
        vm.login("userA@hrpartner.vn", "passA", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == userAId
        }
        vm.loadRecruitmentPosts(reset = true)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            listAStarted.get().count == 0L
        }

        // Switch to User B before User A's response completes -> User A's response must NOT leak into User B
        responseMode.set("EMPTY_B")
        vm.login("userB@hrpartner.vn", "passB", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == userBId
        }
        releaseListA.get().countDown()
        Thread.sleep(150)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Stale response from User A must not populate User B state", vm.recruitmentPosts.value.isEmpty())

        // 403 ACTOR_NOT_AVAILABLE sets accessDenied and purges state
        responseMode.set("FORBIDDEN")
        vm.loadRecruitmentPosts(reset = true)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPostsAccessDenied.value && !vm.recruitmentPostsLoading.value
        }
        assertTrue(vm.recruitmentPosts.value.isEmpty())

        // 404 on list endpoint sets featureUnavailable (not empty list)
        responseMode.set("NOT_FOUND_LIST")
        vm.loadRecruitmentPosts(reset = true)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPostsFeatureUnavailable.value && !vm.recruitmentPostsLoading.value
        }

        // 503 RECRUITMENT_POST_UNAVAILABLE sets error message while keeping user logged in
        responseMode.set("UNAVAILABLE_503")
        vm.loadRecruitmentPosts(reset = true)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPostsError.value != null && !vm.recruitmentPostsLoading.value
        }
        assertEquals(userBId, vm.currentActor.value?.appUserId)
    }

    @Test
    fun `regression 18 - topbar bell reminder button replaces topbar logout, preserves drawer logout, opens reminder dialog, and supports unlimited DAILY and ONCE reminders with auto-disable`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val singletonManager = com.example.data.TaskReminderManager.getInstance(app)
        singletonManager.getReminders().forEach { singletonManager.deleteReminder(it.id) }

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") || path.startsWith("/api/direct-entry/session") ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {
                                  "ok": true,
                                  "actor": {
                                    "app_user_id": "11111111-2222-4333-8444-555555555555",
                                    "display_name": "Nguyễn Văn Chuông",
                                    "capabilities": ["entry_read", "entry_create", "submission_create"],
                                    "scopes": [{"kind": "own", "reference": "11111111-2222-4333-8444-555555555555", "valid_from": "2026-01-01", "valid_to": null}],
                                    "allowed_worker_tabs": ["uploader"]
                                  }
                                }
                                """.trimIndent()
                            )
                    else ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"ok":true,"data":{}}""")
                }
            }
        }
        server.start()

        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "reg18_cookie_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "reg18_prefs_${System.nanoTime()}.preferences_pb")
        val cipher = JvmAes256GcmCookieCipher()
        val repo = MiniBiRepository(
            context = app,
            database = db,
            defaultServerUrl = server.url("/").toString().trimEnd('/'),
            allowDebugUrlOverride = true,
            cookieStorageFile = cookieFile,
            preferencesStorageFile = prefsFile,
            cookieCipher = cipher
        )
        repo.cookieJar.saveFromResponse(
            server.url("/api/auth/session"),
            listOf(
                Cookie.Builder()
                    .name("mini_bi_sid")
                    .value("valid_bell_sid")
                    .hostOnlyDomain(server.hostName)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 3_600_000L)
                    .build()
            )
        )
        val vm = MiniBiViewModel(app, repo)
        registerTestResourcesCleanup(vm, db, server, cookieFile, prefsFile)

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    MiniBiAppRoot(viewModel = vm)
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                !vm.sessionBootstrapping.value && vm.currentActor.value != null
            }
            composeTestRule.waitForIdle()

            // 1. Top bar displays bell reminder button and removes topbar logout button
            composeTestRule.onNodeWithTag("topbar_bell_reminder_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("topbar_logout_button").assertDoesNotExist()
            composeTestRule.onNodeWithTag("topbar_bell_reminder_badge").assertDoesNotExist()

            // 2. Clicking the bell button opens TaskReminderDialog and creates a reminder via UI
            composeTestRule.onNodeWithTag("topbar_bell_reminder_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("task_reminder_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("task_reminder_empty_state").assertIsDisplayed()

            composeTestRule.onNodeWithTag("task_reminder_add_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("task_reminder_title_input").performTextReplacement("Gửi báo cáo ca sáng")
            composeTestRule.onNodeWithTag("task_reminder_hour_input").performTextReplacement("07")
            composeTestRule.onNodeWithTag("task_reminder_minute_input").performTextReplacement("30")
            composeTestRule.onNodeWithTag("task_reminder_mode_once").performClick()
            composeTestRule.onNodeWithTag("task_reminder_sound_option_0").performClick()
            composeTestRule.onNodeWithTag("task_reminder_save_button").performClick()
            composeTestRule.waitForIdle()

            val uiCreatedList = singletonManager.getReminders()
            assertEquals(1, uiCreatedList.size)
            val uiCreated = uiCreatedList.single()
            assertEquals("Gửi báo cáo ca sáng", uiCreated.title)
            assertEquals("07:30", uiCreated.formattedTime)
            assertEquals(com.example.data.TaskReminderRepeatMode.ONCE, uiCreated.repeatMode)
            assertTrue(uiCreated.isEnabled)

            composeTestRule.onNodeWithTag("task_reminder_close_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("topbar_bell_reminder_badge").assertIsDisplayed()

            // Verify drawer logout button is preserved in the left navigation drawer
            composeTestRule.onNodeWithTag("open_drawer_menu_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("drawer_logout_button").assertIsDisplayed()

            // 3. Direct unit verification of TaskReminderManager: unlimited reminders, DAILY vs ONCE, and ONCE auto-disable
            val fakeClock = java.util.concurrent.atomic.AtomicLong(1_762_732_800_000L) // Fixed epoch
            val isolatedManager = com.example.data.TaskReminderManager(
                context = app,
                prefsName = "test_reminders_${System.nanoTime()}",
                clockMillis = { fakeClock.get() },
                scheduleSystemAlarms = false
            )
            val sounds = isolatedManager.querySystemSounds()
            assertTrue("System sounds must always include at least the system default option", sounds.isNotEmpty())
            assertEquals(com.example.data.TaskReminderManager.DEFAULT_SOUND_URI, sounds.first().uri)

            // Create 12 reminders (proving no limit) with blank and custom titles
            for (idx in 0 until 12) {
                isolatedManager.saveReminder(
                    title = if (idx == 0) "   " else "Nhắc việc #$idx",
                    hour = (20 - idx).coerceIn(0, 23),
                    minute = (idx * 5) % 60,
                    repeatMode = if (idx % 2 == 0) com.example.data.TaskReminderRepeatMode.DAILY else com.example.data.TaskReminderRepeatMode.ONCE,
                    soundUri = sounds.first().uri,
                    soundTitle = sounds.first().title,
                    isEnabled = true
                )
            }
            val allSaved = isolatedManager.getReminders()
            assertEquals(12, allSaved.size)
            // Verify ascending time sort (hour * 60 + minute)
            for (i in 0 until allSaved.size - 1) {
                val curMinutes = allSaved[i].hour * 60 + allSaved[i].minute
                val nextMinutes = allSaved[i + 1].hour * 60 + allSaved[i + 1].minute
                assertTrue("Reminders must be sorted by time ascending", curMinutes <= nextMinutes)
            }
            // Blank title defaults to "Nhắc việc"
            assertTrue(allSaved.any { it.title == "Nhắc việc" })

            // Verify ONCE reminder auto-disables after alarm triggers or when clock passes nextTriggerEpochMs
            val onceItem = allSaved.first { it.repeatMode == com.example.data.TaskReminderRepeatMode.ONCE }
            val dailyItem = allSaved.first { it.repeatMode == com.example.data.TaskReminderRepeatMode.DAILY }

            isolatedManager.onAlarmTriggered(onceItem.id)
            assertFalse(
                "ONCE reminder must automatically turn isEnabled=false after firing",
                isolatedManager.getReminders().first { it.id == onceItem.id }.isEnabled
            )

            val prevDailyTrigger = dailyItem.nextTriggerEpochMs
            isolatedManager.onAlarmTriggered(dailyItem.id)
            val updatedDaily = isolatedManager.getReminders().first { it.id == dailyItem.id }
            assertTrue("DAILY reminder must stay enabled after firing", updatedDaily.isEnabled)
            assertTrue("DAILY reminder must advance nextTriggerEpochMs to next day", updatedDaily.nextTriggerEpochMs > prevDailyTrigger)

            // Re-enable ONCE reminder, then advance clock past its nextTriggerEpochMs -> reconcileExpiredOnceReminders disables it
            val reEnabledOnce = isolatedManager.setReminderEnabled(onceItem.id, true)!!
            assertTrue(reEnabledOnce.isEnabled)
            fakeClock.set(reEnabledOnce.nextTriggerEpochMs + 1_000L)
            val afterExpiry = isolatedManager.reconcileExpiredOnceReminders()
            assertFalse(
                "ONCE reminder must automatically disable when nextTriggerEpochMs <= now",
                afterExpiry.first { it.id == onceItem.id }.isEnabled
            )
        } finally {
            singletonManager.getReminders().forEach { singletonManager.deleteReminder(it.id) }
        }
    }

    @Test
    fun `phase 1 ui overhaul - HrpInitialsAvatar extracts Vietnamese initials accurately across 2-word, 3-plus-word, 1-word, and blank inputs`() {
        // 2 words
        assertEquals("LH", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("Lê Hoa"))
        assertEquals("PH", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("Phạm Hùng"))

        // 3+ words (Vietnamese diacritics & Đ/đ)
        assertEquals("KH", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("KIỀU VIỆT HÀ"))
        assertEquals("ĐT", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("Đỗ Đức Thuận"))
        assertEquals("NK", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("Nguyễn Thị Minh Khai (HRP012)"))

        // 1 word / handle / email
        assertEquals("NO", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("nobita6986"))
        assertEquals("MI", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("Minh"))
        assertEquals("QL", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("ql.duan@hrpartner.vn"))

        // Empty / blank / symbols
        assertEquals("NL", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials(""))
        assertEquals("NL", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("   "))
        assertEquals("NL", com.example.ui.components.HrpInitialsAvatarDefaults.extractInitials("---"))

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.HrpInitialsAvatar(fullName = "Đỗ Đức Thuận")
            }
        }
        composeTestRule.onNodeWithText("ĐT").assertIsDisplayed()
    }

    @Test
    fun `phase 1 ui overhaul - WorkerOperationsRowCard hides Ma NLD on list card while WorkerEntryDetailDialog displays full Ma NLD`() {
        val entryId = "55555555-6666-4777-8888-999999999901"
        val worker = sampleWorker(
            entryId = entryId,
            canView = true,
            canProposeChange = false,
            canDirectEdit = true
        ).copy(
            employeeCode = "hrp-2026-000189",
            displayName = "Đỗ Đức Thuận",
            employmentStatus = "ON",
            projectDisplay = "Samsung Điện Tử Bắc Ninh",
            recruiterDisplay = "Nguyễn Thị Hoa",
            firstWorkDate = "2026-10-15"
        )
        val detail = WorkerEntryDetail(
            entryId = entryId,
            submissionId = "sub-01",
            version = 1,
            projectId = "PRJ_01",
            firstWorkDate = "2026-10-15",
            employeeCode = "hrp-2026-000189",
            recruiterId = "REC_01",
            teamId = "TEAM_01",
            providerType = "hrp",
            laborType = "TEMPORARY",
            laborTypeDisplay = "Thời vụ",
            displayName = "Đỗ Đức Thuận",
            gender = "MALE",
            dateOfBirth = "1998-05-12",
            nationalId = "001098012345",
            nationalIdIssuedAt = "2022-04-01",
            nationalIdIssuedPlace = "Cục Cảnh sát QLHC",
            address = "Yên Phong, Bắc Ninh",
            phone = "0988112233",
            generalNote = null,
            paymentState = "READY",
            bankId = "VCB",
            bankName = "Vietcombank",
            accountNumber = "0451000998877",
            accountHolderName = "DO DUC THUAN",
            employmentStatus = "ON",
            employmentEffectiveDate = "2026-10-15",
            employmentStatusVersion = 1,
            portraitPreviewPath = null,
            portraitDownloadPath = null,
            documents = emptyList()
        )

        val showDetail = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            MyApplicationTheme {
                WorkerOperationsRowCard(
                    worker = worker,
                    onViewDetail = { showDetail.value = true },
                    onProposeChange = {},
                    onDirectEdit = {}
                )
                if (showDetail.value) {
                    com.example.ui.screens.WorkerEntryDetailDialog(
                        detail = detail,
                        onDismiss = { showDetail.value = false }
                    )
                }
            }
        }

        // Outer list card displays name, avatar initials, status, project, recruiter, and firstWorkDate, WITHOUT "Mã NLĐ:"
        composeTestRule.onNodeWithText("Đỗ Đức Thuận").assertIsDisplayed()
        composeTestRule.onNodeWithText("ĐT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Đang làm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dự án: Samsung Điện Tử Bắc Ninh").assertIsDisplayed()
        composeTestRule.onNodeWithText("Người tuyển: Nguyễn Thị Hoa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ngày đầu: 2026-10-15").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mã NLĐ:", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("hrp-2026-000189", substring = true).assertDoesNotExist()

        // Open detail dialog -> full Mã NLĐ chip and grouped sections are displayed
        composeTestRule.onNodeWithTag("worker_detail_$entryId").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Mã NLĐ: hrp-2026-000189").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cá nhân & Định danh").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dự án & Tuyển dụng").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `phase 1 ui overhaul - RecruitmentPostsScreen highlights salary and contact person on card and keeps sections 1 to 3 in initial viewport on 800dp screen`() {
        val briefId = "22222222-3333-4444-8555-666666666601"
        val postJson = """
            {
              "brief_id": "$briefId",
              "version": 1,
              "status": "PUBLISHED",
              "project": {
                "project_id": "bbbbbbbb-1111-4222-8333-444444444401",
                "display_name": "Dự Án Luxshare Bắc Giang"
              },
              "industrial_park": {
                "industrial_park_id": "bbbbbbbb-1111-4222-8333-444444444402",
                "industrial_park_code": "KCN_QUANG_CHAU",
                "display_name": "KCN Quang Châu",
                "active": true
              },
              "province": {
                "province_id": "bbbbbbbb-1111-4222-8333-444444444403",
                "province_code": "BAC_GIANG",
                "display_name": "Bắc Giang"
              },
              "published_at": "2026-11-11T08:00:00Z",
              "updated_at": "2026-11-11T08:30:00Z",
              "withdrawn_at": null,
              "created_by_display_name": "Người Phụ Trách Tuyển Dụng",
              "updated_by_display_name": "Người Phụ Trách Tuyển Dụng",
              "withdrawn_by_display_name": null,
              "general": {
                "customer_name": "Luxshare-ICT",
                "investment_origin": null,
                "manufacturing_industry": "Linh kiện điện tử",
                "job_description": "Lắp ráp linh kiện tai nghe trong phòng sạch",
                "environment_types": ["CLEAN_ROOM"],
                "work_postures": ["SITTING"],
                "shift_pattern": "TWO_SHIFTS",
                "shift_details": "Ca 12h luân phiên",
                "manager_name": "Quản Lý Nội Bộ Ẩn",
                "manager_phone": "0912345678",
                "labor_types": [{"labor_type_key": "thoi_vu", "display_name": "Thời vụ"}],
                "supply_schedule_text": null,
                "expected_headcount": 250
              },
              "candidate": {
                "age_min": 18,
                "age_max": 40,
                "gender_requirement": "ANY",
                "education_requirement": "NONE",
                "experience_required": false,
                "experience_details": null,
                "accept_former_customer_workers": true,
                "entry_assessment_text": null,
                "other_requirements_text": null
              },
              "compensation": {
                "base_salary_vnd": "8500000",
                "base_salary_conditions": null,
                "attendance_bonus_vnd": "500000",
                "attendance_bonus_conditions": null,
                "living_allowance_vnd": null,
                "living_allowance_conditions": null,
                "seniority_allowance_vnd": null,
                "seniority_allowance_conditions": null,
                "eyesight_allowance_vnd": null,
                "eyesight_allowance_conditions": null,
                "other_allowance_text": null,
                "average_income_min_vnd": "9500000",
                "average_income_max_vnd": "13500000",
                "salary_payment_schedule": "Ngày 15 hàng tháng",
                "salary_advance_available": true,
                "salary_advance_amount_vnd": "1000000",
                "salary_advance_schedule": "Thứ 6 hàng tuần",
                "weekly_salary_available": false,
                "weekly_salary_payment_schedule": null,
                "day_shift_8h_vnd": "260000",
                "day_shift_11h_vnd": "390000",
                "night_shift_8h_vnd": null,
                "night_shift_11h_vnd": null,
                "sunday_day_11h_vnd": null,
                "sunday_night_11h_vnd": null,
                "holiday_day_11h_vnd": null,
                "holiday_night_11h_vnd": null,
                "ppe_provided": true,
                "ppe_details": "Áo phòng sạch",
                "other_benefits": null
              },
              "documents": {
                "require_original_cccd_and_copy": true,
                "require_certified_resume": false,
                "require_health_certificate_within_6_months": false,
                "require_photo_3x4": true,
                "require_degree_or_certificate": false,
                "require_social_insurance_book": false,
                "require_other_documents": false,
                "other_documents_text": null
              },
              "allowed_actions": {
                "can_edit": false,
                "can_withdraw": false
              }
            }
        """.trimIndent()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/auth/session") -> MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":false,"code":"UNAUTHENTICATED","message":"Unauthenticated"}""")
                    path.startsWith("/api/auth/login") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Set-Cookie", "mini_bi_session=sess-p1; Path=/; HttpOnly")
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "user": {"id": "bbbb0016-2222-4333-8444-555555555555", "username": "rec.p1", "fullName": "Recruiter P1", "role": "STAFF"},
                              "actor": {
                                "app_user_id": "bbbb0016-2222-4333-8444-555555555555",
                                "display_name": "Recruiter P1",
                                "capabilities": [],
                                "scopes": [],
                                "self_recruiter_suggestion": null,
                                "allowed_worker_tabs": []
                              }
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/api/recruitment-posts?") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "ok": true,
                              "data": {
                                "items": [$postJson],
                                "page_size": 10,
                                "has_more": false,
                                "next_cursor": null,
                                "snapshot_token": "snap-p1"
                              }
                            }
                            """.trimIndent()
                        )
                    path.contains("/api/recruitment-posts/$briefId") -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"data":{"post":$postJson}}""")
                    else -> MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"ok":true,"items":[]}""")
                }
            }
        }
        server.start()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = buildTestDb(app)
        val cookieFile = File(app.cacheDir, "p1_rec_cookie_${System.nanoTime()}.enc")
        val prefsFile = File(app.cacheDir, "p1_rec_prefs_${System.nanoTime()}.preferences_pb")
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

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.sessionBootstrapping.value
        }

        vm.login("rec.p1@hrpartner.vn", "pass123", rememberAccount = false)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vm.loginBusy.value && vm.currentActor.value?.appUserId == "bbbb0016-2222-4333-8444-555555555555"
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.RecruitmentPostsScreen(viewModel = vm)
            }
        }

        vm.navigateTo(com.example.ui.MainDestination.RECRUITMENT)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.recruitmentPosts.value.size == 1 && !vm.recruitmentPostsLoading.value
        }
        composeTestRule.waitForIdle()

        // Outer summary card highlights salary/income and displays contactPersonDisplay (never manager_name)
        composeTestRule.onNodeWithText("Dự Án Luxshare Bắc Giang").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.500.000", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("9.500.000", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Phụ trách: Người Phụ Trách Tuyển Dụng").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quản Lý Nội Bộ Ẩn", substring = true).assertDoesNotExist()

        // Open detail dialog -> sections 1, 2, and 3 MUST be displayed simultaneously in the initial 800dp viewport without scrolling
        composeTestRule.onNodeWithTag("recruitment_post_detail_btn_$briefId").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.selectedRecruitmentPostDetail.value?.briefId == briefId
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recruitment_post_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Thông tin chung dự án").assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Yêu cầu ứng viên").assertIsDisplayed()
        composeTestRule.onNodeWithText("3. Lương, thưởng và phúc lợi").assertIsDisplayed()
        composeTestRule.onNodeWithText("4. Hồ sơ ứng viên cần chuẩn bị").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("5. Đơn giá ca làm & bảo hộ").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("6. Thông tin đăng tải & cập nhật").performScrollTo().assertIsDisplayed()
    }
}
