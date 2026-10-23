package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.ActorScope
import com.example.data.AppActor
import com.example.data.DirectEntryContract
import com.example.data.WorkerProfileEntity
import com.example.ui.screens.WorkerOperationsRowCard
import com.example.ui.screens.resolveVisibleWorkerTabs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h1400dp")
class WorkerUiAuthorizationRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    }
}
