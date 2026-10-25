package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// 1. Canonical Server-Resolved Actor Session Model (Backend is sole authority)
data class ActorScope(
    val kind: String, // "own" | "team" | "all"
    val reference: String,
    val validFrom: String,
    val validTo: String?
)

data class AppActor(
    val appUserId: String,
    val displayName: String,
    val capabilities: List<String>,
    val scopes: List<ActorScope>,
    val selfRecruiterSuggestion: String?,
    val displayIdentifier: String? = null // Non-authoritative display-only login identifier
) {
    fun hasCapability(capability: String): Boolean = capabilities.contains(capability)

    fun hasScopeKind(kind: String): Boolean = scopes.any { it.kind == kind }
}

// 2. Reporting & Duplicate CCCD Projection Models (Backend authority only)
enum class DashboardDatePreset(val key: String, val label: String) {
    ALL_TIME("all-time", "Tất cả"),
    THIS_WEEK("this-week", "Tuần này"),
    THIS_MONTH("this-month", "Tháng này"),
    LAST_MONTH("last-month", "Tháng trước")
}

data class BiBucketItem(
    val key: String,
    val display: String,
    val count: Int,
    val sharePct: Float,
    val onCount: Int? = null,
    val unconfirmedCount: Int? = null,
    val offCount: Int? = null
)

data class WorkforceDeepMetrics(
    val asOfDate: String,
    val eligibleEpisodeTotal: Int,
    val onCount: Int,
    val unconfirmedCount: Int,
    val offCount: Int,
    val unknownCount: Int,
    val futureEpisodeCount: Int,
    val avgWaitingDays: Float?,
    val placementRatePct: Float?,
    val retention7dPct: Float?,
    val retention30dPct: Float?,
    val retention90dPct: Float?,
    val avgTenureDays: Float?,
    val byTeam: List<BiBucketItem>,
    val byProject: List<BiBucketItem>,
    val byRecruiter: List<BiBucketItem>,
    val byProvider: List<BiBucketItem>,
    val leaveReasons: List<BiBucketItem>,
    val projectHasMore: Boolean = false,
    val projectNextCursor: String? = null,
    val totalProjectCount: Int = 0,
    val periodFromDate: String? = null,
    val periodToDate: String? = null,
    val periodNewEpisodes: Int? = null,
    val periodOnInflow: Int? = null,
    val periodWaitingInflow: Int? = null,
    val periodOffInflow: Int? = null,
    val periodNetOnChange: Int? = null
)

data class GeneralOverviewSnapshot(
    val asOfDate: String,
    val scopeDescription: String,
    val totalProfiles: Int,
    val workingCount: Int,
    val waitingCount: Int,
    val leftCount: Int,
    val audienceKind: String = "own", // "all" | "team" | "own"
    val isPeriodFiltered: Boolean = false,
    val workingRatePct: Float? = null,
    val placementRatePct: Float? = null,
    val retention7dPct: Float? = null,
    val retention30dPct: Float? = null,
    val closingTotalProfiles: Int? = null,
    val closingWorkingCount: Int? = null,
    val closingWaitingCount: Int? = null,
    val closingLeftCount: Int? = null,
    val projectCount: Int = 0,
    val recruiterCount: Int = 0,
    val managedTotal: Int? = null,
    val recruitedTotal: Int? = null,
    val teamTotal: Int? = null,
    val activeHiringDays: Int = 0,
    val avgPerActiveDay: Float? = null,
    val peakDailyCount: Int? = null,
    val peakDateIso: String? = null,
    val leadingProject: BiBucketItem? = null,
    val byRecruiter: List<BiBucketItem> = emptyList(),
    val byProject: List<BiBucketItem> = emptyList(),
    val bySecondaryGroup: List<BiBucketItem> = emptyList(),
    val secondaryGroupTitle: String = "",
    val byProvider: List<BiBucketItem> = emptyList(),
    val periodFromDate: String? = null,
    val periodToDate: String? = null,
    val periodNewEpisodes: Int? = null,
    val periodOnInflow: Int? = null,
    val periodOffInflow: Int? = null,
    val periodNetOnChange: Int? = null
)

data class TeamDrillDownItem(
    val businessDate: String,
    val projectDisplay: String,
    val recruiterDisplay: String,
    val providerTypeDisplay: String,
    val employmentTypeDisplay: String,
    val recruitedCount: Int,
    val firstWorkDate: String
)

data class TeamReportSnapshot(
    val bucketKey: String,
    val bucketDisplay: String,
    val totalRecruited: Int,
    val rows: List<TeamDrillDownItem>,
    val nextCursor: String?,
    val activeRecruiterCount: Int = 0,
    val activeProjectCount: Int = 0,
    val activeHiringDays: Int = 0,
    val avgPerActiveDay: Float? = null,
    val peakDailyCount: Int? = null,
    val peakDateIso: String? = null,
    val workingCount: Int? = null,
    val waitingCount: Int? = null,
    val leftCount: Int? = null,
    val byRecruiter: List<BiBucketItem> = emptyList(),
    val byProject: List<BiBucketItem> = emptyList(),
    val byEmployment: List<BiBucketItem> = emptyList(),
    val byProvider: List<BiBucketItem> = emptyList()
)

data class DuplicateCccdEpisodeItem(
    val entryId: String,
    val entryVersion: Int,
    val employeeCode: String,
    val displayName: String,
    val projectDisplay: String,
    val firstWorkDate: String,
    val latestStatus: String,
    val active: Boolean,
    val canMarkOff: Boolean,
    val canSelfWithdraw: Boolean
)

data class DuplicateCccdGroupItem(
    val cccdLast4: String,
    val cccdLength: Int,
    val profileCount: Int,
    val projectCount: Int,
    val activeProfileCount: Int,
    val firstWorkDate: String,
    val latestFirstWorkDate: String,
    val episodes: List<DuplicateCccdEpisodeItem>
)

// 3. Direct Entry Catalog, Worker Directory, Submissions & Entry Detail Models
data class CatalogProjectOption(
    val projectId: String,
    val displayName: String
)

data class CatalogRecruiterOption(
    val recruiterId: String,
    val displayName: String,
    val personnelCode: String,
    val providerType: String, // "hrp" | "vendor"
    val vendorId: String?,
    val teamId: String?,
    val teamDisplayName: String?,
    val label: String
)

data class CatalogBankOption(
    val bankId: String,
    val displayName: String
)

data class CatalogLaborTypeOption(
    val laborTypeKey: String,
    val displayName: String,
    val sortOrder: Int
)

data class DirectEntryCatalog(
    val effectiveDate: String,
    val projects: List<CatalogProjectOption>,
    val recruiters: List<CatalogRecruiterOption>,
    val banks: List<CatalogBankOption>,
    val laborTypes: List<CatalogLaborTypeOption>
)

data class WorkerAllowedActions(
    val view: Boolean = false,
    val viewPii: Boolean = false,
    val viewPayment: Boolean = false,
    val proposeChange: Boolean = false,
    val proposeChangeCode: String? = null,
    val directEdit: Boolean = false
)

data class WorkerDirectorySummary(
    val totalWorkers: Int = 0,
    val projectCount: Int = 0,
    val recruiterCount: Int = 0
)

data class WorkerDirectoryPageResult(
    val scope: String,
    val items: List<WorkerProfileEntity>,
    val pageSize: Int,
    val hasMore: Boolean,
    val nextCursor: String?,
    val authorizationDate: String,
    val summary: WorkerDirectorySummary
)

data class OwnSubmissionsPageResult(
    val items: List<OwnSubmissionItem>,
    val pageSize: Int,
    val hasMore: Boolean,
    val nextCursor: String?
)

data class FullProfileBatchResult(
    val submissionId: String,
    val state: String,
    val version: Int,
    val entryIds: List<String>,
    val employeeCodes: List<String>
)

data class ChangeRequestCreatedSummary(
    val requestId: String,
    val state: String,
    val itemsCount: Int
)

data class StagedDocumentRef(
    val stageId: String,
    val documentType: String, // "WORKER_PORTRAIT" | "CCCD_FRONT" | "CCCD_BACK" | "EMPLOYMENT_CONTRACT"
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localFilePath: String? = null,
    val pageCount: Int = 1,
    val sourceImagePaths: List<String> = emptyList()
)

data class PendingEntryAttachmentTask(
    val entryId: String,
    val workerDisplayName: String,
    val documentType: String,
    val fileName: String,
    val mimeType: String,
    val localFilePath: String,
    val idempotencyBaseKey: String,
    val sourceImagePaths: List<String> = emptyList()
)

data class NewWorkerEntryDraft(
    val localId: String,
    val projectId: String = "",
    val projectDisplay: String = "",
    val providerType: String = "", // "hrp" | "vendor"
    val recruiterId: String = "",
    val recruiterDisplay: String = "",
    val laborTypeKey: String = "",
    val laborTypeDisplay: String = "",
    val firstWorkDateUi: String = "", // DD/MM/YYYY
    val employmentStatusUi: String = "Đang làm", // "Đang làm" | "Đang chờ việc"
    val fullName: String = "",
    val genderUi: String = "", // "" (chưa chọn) | "Nam" | "Nữ"
    val phone: String = "",
    val cccd: String = "",
    val cccdIssuePlace: String = "",
    val portraitStage: StagedDocumentRef? = null,
    val cccdFrontStage: StagedDocumentRef? = null,
    val cccdBackStage: StagedDocumentRef? = null,
    val contractStage: StagedDocumentRef? = null,
    val uploadingSlot: String? = null,
    val slotError: String? = null,
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val bankName: String = "",
    val note: String = "",
    val birthDateUi: String = "", // Free text <= 10 chars (e.g. DD/MM/YYYY)
    val cccdIssueDateUi: String = "", // Free text <= 10 chars (e.g. DD/MM/YYYY)
    val address: String = "",
    val isExpanded: Boolean = true,
    val showOptionalSection: Boolean = false
) {
    val isValid: Boolean
        get() {
            val providerNorm = providerType.trim().lowercase()
            return projectId.isNotBlank() &&
                (providerNorm == "hrp" || providerNorm == "vendor") &&
                DirectEntryContract.isUuid(recruiterId) &&
                laborTypeKey.isNotBlank() &&
                DirectEntryContract.isValidUiOrIsoDate(firstWorkDateUi) &&
                fullName.length <= 256 &&
                !DirectEntryContract.isFormulaLikeCell(fullName) &&
                (genderUi.isBlank() || genderUi.trim() in setOf("Nam", "Nữ", "MALE", "FEMALE")) &&
                DirectEntryContract.isValidCccd12(cccd) &&
                DirectEntryContract.isValidFreeDateText(birthDateUi) &&
                DirectEntryContract.isValidFreeDateText(cccdIssueDateUi) &&
                phone.trim().length <= 64 &&
                address.trim().length <= 1024 &&
                note.trim().length <= 4000 &&
                bankAccountNumber.trim().length <= 64 &&
                !DirectEntryContract.hasControlCharacter(bankAccountNumber) &&
                bankAccountHolder.trim().length <= 256 &&
                !DirectEntryContract.hasControlCharacter(bankAccountHolder) &&
                bankName.trim().length <= 256 &&
                !DirectEntryContract.hasControlCharacter(bankName) &&
                uploadingSlot == null
        }

    val isStarted: Boolean
        get() = projectId.isNotBlank() ||
            providerType.isNotBlank() ||
            recruiterId.isNotBlank() ||
            fullName.isNotBlank() ||
            phone.isNotBlank() ||
            cccd.isNotBlank() ||
            cccdIssuePlace.isNotBlank() ||
            birthDateUi.isNotBlank() ||
            cccdIssueDateUi.isNotBlank() ||
            address.isNotBlank() ||
            portraitStage != null ||
            cccdFrontStage != null ||
            cccdBackStage != null ||
            contractStage != null ||
            bankAccountNumber.isNotBlank() ||
            bankAccountHolder.isNotBlank() ||
            bankName.isNotBlank() ||
            note.isNotBlank()

    fun allStageIds(): List<String> = listOfNotNull(
        portraitStage?.stageId,
        cccdFrontStage?.stageId,
        cccdBackStage?.stageId,
        contractStage?.stageId
    ).map { it.trim() }.filter { it.isNotEmpty() }

    fun allAttachments(): List<StagedDocumentRef> = listOfNotNull(
        portraitStage,
        cccdFrontStage,
        cccdBackStage,
        contractStage
    )

    fun localPendingAttachments(): List<StagedDocumentRef> = allAttachments().filter {
        it.stageId.isBlank() && !it.localFilePath.isNullOrBlank()
    }
}

data class OwnSubmissionItem(
    val submissionId: String,
    val state: String,
    val version: Int,
    val entryCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val submittedAt: String?,
    val allowedTransitions: List<String>,
    val projectScoped: Boolean
)

data class DuplicateCccdPreflightConflict(
    val conflictRef: String,
    val draftDisplayName: String,
    val projectDisplay: String?,
    val employmentStatus: String,
    val cccdLast4: String
) {
    fun formatConflictLine(): String {
        val safeName = draftDisplayName.trim().ifEmpty { "trong bản nháp" }
        val subject = projectDisplay?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { "tại dự án $it" }
            ?: "trong hệ thống"
        val statusSuffix = if (employmentStatus == "ON") {
            "đang làm việc $subject"
        } else {
            "một hồ sơ $subject (Đang chờ việc)"
        }
        return "• NLĐ $safeName (*$cccdLast4) trùng CCCD với NLĐ $statusSuffix."
    }
}

data class DuplicateCccdPreflightResult(
    val submissionId: String,
    val version: Int,
    val fingerprint: String,
    val conflictCount: Int,
    val conflicts: List<DuplicateCccdPreflightConflict>
)

data class SubmissionTransitionResult(
    val submissionId: String,
    val state: String,
    val version: Int
)

data class PendingDuplicateCccdSubmission(
    val entryCount: Int,
    val employeeCodes: List<String>,
    val preflight: DuplicateCccdPreflightResult,
    val navigateToWorkersOnConfirm: Boolean,
    val pendingAttachments: List<PendingEntryAttachmentTask> = emptyList()
)

data class EntryDocumentItem(
    val documentId: String,
    val documentType: String,
    val version: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val uploadStatus: String,
    val scanStatus: String,
    val validationStatus: String,
    val previewPath: String?,
    val downloadPath: String?
)

data class WorkerEntryDetail(
    val entryId: String,
    val submissionId: String,
    val version: Int,
    val projectId: String,
    val firstWorkDate: String, // YYYY-MM-DD
    val employeeCode: String,
    val recruiterId: String,
    val teamId: String,
    val providerType: String, // "hrp" | "vendor"
    val laborType: String,
    val laborTypeDisplay: String,
    val displayName: String,
    val gender: String?, // "MALE" | "FEMALE" | "OTHER" | null
    val dateOfBirth: String?,
    val nationalId: String?,
    val nationalIdIssuedAt: String? = null,
    val nationalIdIssuedPlace: String? = null,
    val address: String?,
    val phone: String?,
    val generalNote: String?,
    val paymentState: String?,
    val paymentVersion: Int? = null,
    val bankId: String?,
    val bankName: String?,
    val accountNumber: String?,
    val accountHolderName: String?,
    val employmentStatus: String, // "ON" | "UNCONFIRMED" | "OFF"
    val employmentEffectiveDate: String,
    val employmentStatusVersion: Int,
    val employmentLeaveReason: String? = null,
    val portraitPreviewPath: String?,
    val portraitDownloadPath: String?,
    val documents: List<EntryDocumentItem>
)

data class CccdDecodedQrPayload(
    val cccd12: String,
    val legacyId9: String? = null,
    val fullName: String? = null,
    val dateOfBirthUi: String? = null,
    val genderCanonical: String? = null,
    val genderUi: String? = null,
    val address: String? = null,
    val issueDateUi: String? = null
)

enum class CccdReviewStatusFilter(val code: String, val label: String) {
    NEEDS_REVIEW("NEEDS_REVIEW", "Cần Review"),
    MISMATCH("MISMATCH", "Lệch CCCD"),
    QR_UNREADABLE("QR_UNREADABLE", "Không đọc được QR"),
    PENDING("PENDING", "Chờ đọc QR"),
    MATCHED_INITIAL("MATCHED_INITIAL", "Khớp ban đầu"),
    RESOLVED("RESOLVED", "Đã xử lý"),
    ALL("ALL", "Tất cả")
}

data class CccdReviewSummary(
    val totalCount: Int = 0,
    val needsReviewCount: Int = 0,
    val mismatchCount: Int = 0,
    val qrUnreadableCount: Int = 0,
    val pendingCount: Int = 0,
    val matchedInitialCount: Int = 0,
    val resolvedCount: Int = 0
)

data class CccdReviewComparisonField(
    val field: String,
    val label: String,
    val enteredValue: String?,
    val qrValue: String?,
    val status: String // "MATCH" | "DIFFERENT" | "ENTERED_BLANK" | "QR_MISSING"
)

data class CccdReviewQueueItem(
    val entryId: String,
    val submissionId: String,
    val submissionState: String,
    val entryVersion: Int,
    val employeeCode: String,
    val projectId: String,
    val projectDisplay: String,
    val firstWorkDate: String,
    val recruiterId: String,
    val recruiterDisplay: String,
    val enteredDisplayName: String,
    val enteredNationalId: String?,
    val enteredDateOfBirth: String?,
    val enteredGender: String?,
    val enteredAddress: String?,
    val enteredNationalIdIssuedAt: String?,
    val cccdFrontDocumentId: String?,
    val cccdFrontUploadStatus: String?,
    val cccdFrontQrStatus: String,
    val cccdFrontPreviewPath: String?,
    val cccdBackDocumentId: String?,
    val cccdBackUploadStatus: String?,
    val cccdBackQrStatus: String,
    val cccdBackPreviewPath: String?,
    val qrExtractedPayload: CccdDecodedQrPayload?,
    val reconciliationStatus: String,
    val resolutionAction: String?,
    val resolvedAt: String?,
    val resolvedByUserId: String?,
    val resolutionNote: String?,
    val comparison: List<CccdReviewComparisonField>
)

data class CccdReviewQueuePage(
    val scopeKind: String,
    val statusFilter: String,
    val limit: Int,
    val offset: Int,
    val totalCount: Int,
    val summary: CccdReviewSummary,
    val rows: List<CccdReviewQueueItem>
)

data class CccdReviewResolveResult(
    val entryId: String,
    val entryVersion: Int,
    val action: String,
    val reconciliationStatus: String,
    val updatedFields: List<String>
)

data class CccdQrRetryResult(
    val entryId: String,
    val entryVersion: Int,
    val reconciliationStatus: String,
    val frontQrStatus: String,
    val backQrStatus: String,
    val autoFilledFields: List<String>
)

// 4. Work Reports Dynamic Schema & Models
data class WorkReportFieldOption(
    val value: String,
    val label: String
)

data class WorkReportFieldSpec(
    val fieldKey: String,
    val sectionKey: String,
    val sortOrder: Int,
    val label: String,
    val description: String?,
    val fieldType: String, // "text" | "textarea" | "integer" | "decimal" | "date" | "boolean" | "single_select" | "multi_select"
    val required: Boolean,
    val defaultValue: String?,
    val options: List<WorkReportFieldOption> = emptyList()
)

data class WorkReportSectionSpec(
    val sectionKey: String,
    val label: String,
    val description: String?,
    val sortOrder: Int,
    val fields: List<WorkReportFieldSpec>
)

data class WorkReportTemplateOption(
    val templateId: String,
    val displayName: String
)

data class WorkReportReviewItem(
    val obligationId: String,
    val submissionId: String,
    val submissionVersion: Int,
    val submissionState: String,
    val templateId: String,
    val templateName: String,
    val periodKey: String,
    val assigneeUserId: String,
    val assigneeDisplayName: String,
    val teamDisplayName: String?,
    val submittedAt: String?,
    val payloadSummary: Map<String, String>,
    val sections: List<WorkReportSectionSpec>,
    val dueAt: String? = null,
    val isLate: Boolean = false
)

data class WorkReportAnalyticsMetricItem(
    val fieldKey: String,
    val label: String,
    val valueDisplay: String,
    val subtitleDisplay: String? = null
)

data class WorkReportAnalyticsBreakdownRow(
    val groupKey: String,
    val groupLabel: String,
    val submittedCount: Int,
    val obligationCount: Int,
    val complianceRatePct: Float?,
    val overdueMissingCount: Int = 0,
    val metricsSummary: List<String> = emptyList()
)

data class WorkReportAnalyticsContributionItem(
    val obligationId: String,
    val assigneeDisplayName: String,
    val periodKey: String,
    val submissionState: String,
    val submittedAt: String?,
    val teamDisplayName: String? = null,
    val isLate: Boolean = false
)

data class WorkReportAnalyticsSnapshot(
    val templateId: String,
    val templateName: String,
    val totalObligations: Int,
    val submittedCount: Int,
    val approvedCount: Int,
    val complianceRatePct: Float?,
    val metrics: List<WorkReportAnalyticsMetricItem>,
    val breakdownRows: List<WorkReportAnalyticsBreakdownRow>,
    val contributions: List<WorkReportAnalyticsContributionItem>,
    val scopeLabel: String = "",
    val finality: String = "final",
    val groupBy: String = "team",
    val overdueMissingCount: Int = 0,
    val lateSubmittedCount: Int = 0,
    val pendingDueCount: Int = 0,
    val pendingReviewCount: Int = 0
)

// 5. Room Entities (Strictly Cache + Attendance Outbox — NEVER authority)
object AttendanceOutboxStatus {
    const val QUEUED = "QUEUED"
    const val SYNCING = "SYNCING"
    const val SYNCED = "SYNCED"
    const val RETRYABLE_ERROR = "RETRYABLE_ERROR"
    const val AUTH_REQUIRED = "AUTH_REQUIRED"
    const val DENIED = "DENIED"
    const val REJECTED = "REJECTED"
    const val BACKEND_NOT_AVAILABLE = "BACKEND_NOT_AVAILABLE"
}

@Entity(tableName = "attendance_outbox")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,
    val appUserId: String,
    val userDisplayName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val addressHint: String,
    val photoFilePath: String,
    val photoUri: String,
    val photoSha256: String,
    val clientCapturedAtIso: String,
    val epochMillis: Long,
    val timeSource: String = AttendanceTimeSource.DEVICE_CLOCK_UNVERIFIED,
    val timeToken: String? = null,
    val elapsedSinceAnchorMs: Long? = null,
    val note: String,
    val syncStatus: String,
    val statusMessage: String,
    val backendErrorCode: String? = null,
    val httpStatusCode: Int? = null,
    val serverCheckinId: String? = null,
    val serverBusinessDate: String? = null,
    val serverSequenceInDay: Int? = null,
    val serverDailyCount: Int? = null,
    val serverCapturedAtIso: String? = null,
    val serverReceivedAtIso: String? = null,
    val serverTimeSource: String? = null,
    val serverReplayed: Boolean? = null,
    val retryCount: Int = 0
)

@Entity(tableName = "cached_work_report_obligations")
data class WorkReportObligationEntity(
    @PrimaryKey val obligationId: String,
    val templateId: String,
    val templateCode: String,
    val templateName: String,
    val periodType: String,
    val periodKey: String,
    val periodStartDate: String,
    val periodEndDate: String,
    val dueAtIso: String,
    val isMandatory: Boolean,
    val obligationStatus: String,
    val submissionId: String?,
    val submissionState: String,
    val submissionVersion: Int?,
    val obligationVersion: Int,
    val schemaJsonRaw: String,
    val payloadJsonRaw: String,
    val latestReviewDecision: String?,
    val latestReviewReason: String?,
    val submittedAtIso: String?
)

@Entity(tableName = "cached_worker_directory_rows")
data class WorkerProfileEntity(
    @PrimaryKey val cacheKey: String, // "$scopeKey:$entryId"
    val scopeKey: String, // "managed" | "recruited" | "team" | "all"
    val entryId: String,
    val entryVersion: Int,
    val submissionState: String,
    val employeeCode: String,
    val displayName: String,
    val projectId: String,
    val projectDisplay: String,
    val firstWorkDate: String, // YYYY-MM-DD
    val laborType: String,
    val laborTypeDisplay: String,
    val employmentStatus: String, // "ON" | "UNCONFIRMED" | "OFF"
    val recruiterId: String,
    val recruiterDisplay: String,
    val paymentSummary: String,
    val pendingRequestSummary: String,
    val lastDecisionSummary: String,
    val isProjectManager: Boolean,
    val canView: Boolean = false,
    val canViewPii: Boolean = false,
    val canViewPayment: Boolean = false,
    val canProposeChange: Boolean = false,
    val proposeChangeCode: String? = null,
    val canDirectEdit: Boolean = false
) {
    val allowedActions: WorkerAllowedActions
        get() = WorkerAllowedActions(
            view = canView,
            viewPii = canViewPii,
            viewPayment = canViewPayment,
            proposeChange = canProposeChange,
            proposeChangeCode = proposeChangeCode,
            directEdit = canDirectEdit
        )
}

@Dao
interface MiniBiDao {
    @Query("SELECT * FROM attendance_outbox WHERE appUserId = :appUserId ORDER BY epochMillis DESC")
    fun observeAttendanceForUser(appUserId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_outbox ORDER BY epochMillis DESC")
    fun observeAllAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_outbox WHERE id = :id LIMIT 1")
    suspend fun getAttendanceById(id: Long): AttendanceEntity?

    @Query("SELECT * FROM attendance_outbox WHERE appUserId = :appUserId AND serverCheckinId = :serverCheckinId LIMIT 1")
    suspend fun getAttendanceByServerCheckinId(appUserId: String, serverCheckinId: String): AttendanceEntity?

    @Query("SELECT * FROM attendance_outbox WHERE syncStatus IN ('QUEUED', 'RETRYABLE_ERROR') ORDER BY epochMillis ASC")
    suspend fun getRetryableAttendanceOutbox(): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_outbox WHERE appUserId = :appUserId AND syncStatus IN ('QUEUED', 'RETRYABLE_ERROR') ORDER BY epochMillis ASC")
    suspend fun getRetryableAttendanceOutboxForUser(appUserId: String): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateAttendance(record: AttendanceEntity)

    @Query("SELECT * FROM cached_work_report_obligations ORDER BY periodKey DESC, dueAtIso DESC")
    fun observeAllWorkReports(): Flow<List<WorkReportObligationEntity>>

    @Query("SELECT * FROM cached_work_report_obligations ORDER BY periodKey DESC, dueAtIso DESC")
    suspend fun getCachedWorkReportsSnapshot(): List<WorkReportObligationEntity>

    @Query("DELETE FROM cached_work_report_obligations")
    suspend fun clearWorkReportsCache()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceWorkReportsCache(items: List<WorkReportObligationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkReportCache(item: WorkReportObligationEntity)

    @Query("SELECT * FROM cached_worker_directory_rows WHERE scopeKey = :scopeKey")
    suspend fun getCachedWorkersByScope(scopeKey: String): List<WorkerProfileEntity>

    @Query("DELETE FROM cached_worker_directory_rows WHERE scopeKey = :scopeKey")
    suspend fun clearCachedWorkersByScope(scopeKey: String)

    @Query("DELETE FROM cached_worker_directory_rows")
    suspend fun clearAllCachedWorkers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedWorkers(workers: List<WorkerProfileEntity>)
}

@Database(
    entities = [
        AttendanceEntity::class,
        WorkReportObligationEntity::class,
        WorkerProfileEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MiniBiDatabase : RoomDatabase() {
    abstract fun miniBiDao(): MiniBiDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN timeSource TEXT NOT NULL DEFAULT 'DEVICE_CLOCK_UNVERIFIED'")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN timeToken TEXT")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN elapsedSinceAnchorMs INTEGER")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN serverBusinessDate TEXT")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN serverCapturedAtIso TEXT")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN serverReceivedAtIso TEXT")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN serverTimeSource TEXT")
                db.execSQL("ALTER TABLE attendance_outbox ADD COLUMN serverReplayed INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: MiniBiDatabase? = null

        fun getInstance(context: Context): MiniBiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MiniBiDatabase::class.java,
                    "mini_bi_hrpartner.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
