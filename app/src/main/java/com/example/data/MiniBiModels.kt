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
data class BiBucketItem(
    val key: String,
    val display: String,
    val count: Int,
    val sharePct: Float
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
    val projectNextCursor: String? = null
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
    val nextCursor: String?
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
    val proposeChangeCode: Boolean = false,
    val directEdit: Boolean = false
)

data class StagedDocumentRef(
    val stageId: String,
    val documentType: String, // "WORKER_PORTRAIT" | "CCCD_FRONT" | "CCCD_BACK" | "EMPLOYMENT_CONTRACT"
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
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
    val genderUi: String = "", // "" | "Nam" | "Nữ" | "Khác"
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
    val birthDateUi: String = "", // DD/MM/YYYY
    val cccdIssueDateUi: String = "", // DD/MM/YYYY
    val address: String = "",
    val isExpanded: Boolean = true,
    val showOptionalSection: Boolean = false
) {
    val isValid: Boolean
        get() = projectId.isNotBlank() &&
            (providerType == "hrp" || providerType == "vendor") &&
            recruiterId.isNotBlank() &&
            laborTypeKey.isNotBlank() &&
            firstWorkDateUi.isNotBlank() &&
            fullName.trim().length >= 2 &&
            uploadingSlot == null

    val isStarted: Boolean
        get() = projectId.isNotBlank() ||
            providerType.isNotBlank() ||
            recruiterId.isNotBlank() ||
            laborTypeKey.isNotBlank() ||
            fullName.isNotBlank() ||
            genderUi.isNotBlank() ||
            phone.isNotBlank() ||
            cccd.isNotBlank() ||
            portraitStage != null ||
            cccdFrontStage != null ||
            cccdBackStage != null ||
            contractStage != null ||
            bankAccountNumber.isNotBlank() ||
            note.isNotBlank()

    fun allStageIds(): List<String> = listOfNotNull(
        portraitStage?.stageId,
        cccdFrontStage?.stageId,
        cccdBackStage?.stageId,
        contractStage?.stageId
    )
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
    val address: String?,
    val phone: String?,
    val generalNote: String?,
    val paymentState: String?,
    val bankId: String?,
    val bankName: String?,
    val accountNumber: String?,
    val accountHolderName: String?,
    val employmentStatus: String, // "ON" | "UNCONFIRMED" | "OFF"
    val employmentEffectiveDate: String,
    val employmentStatusVersion: Int,
    val portraitPreviewPath: String?,
    val portraitDownloadPath: String?,
    val documents: List<EntryDocumentItem>
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
    val sections: List<WorkReportSectionSpec>
)

data class WorkReportAnalyticsMetricItem(
    val fieldKey: String,
    val label: String,
    val valueDisplay: String
)

data class WorkReportAnalyticsBreakdownRow(
    val groupKey: String,
    val groupLabel: String,
    val submittedCount: Int,
    val obligationCount: Int,
    val complianceRatePct: Float?
)

data class WorkReportAnalyticsContributionItem(
    val obligationId: String,
    val assigneeDisplayName: String,
    val periodKey: String,
    val submissionState: String,
    val submittedAt: String?
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
    val contributions: List<WorkReportAnalyticsContributionItem>
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
    val note: String,
    val syncStatus: String,
    val statusMessage: String,
    val backendErrorCode: String? = null,
    val httpStatusCode: Int? = null,
    val serverCheckinId: String? = null,
    val serverSequenceInDay: Int? = null,
    val serverDailyCount: Int? = null,
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
    val canProposeChangeCode: Boolean = false,
    val canDirectEdit: Boolean = false
) {
    val allowedActions: WorkerAllowedActions
        get() = WorkerAllowedActions(
            view = canView,
            viewPii = canViewPii,
            viewPayment = canViewPayment,
            proposeChange = canProposeChange,
            proposeChangeCode = canProposeChangeCode,
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

    @Query("SELECT * FROM attendance_outbox WHERE syncStatus IN ('QUEUED', 'RETRYABLE_ERROR') ORDER BY epochMillis ASC")
    suspend fun getRetryableAttendanceOutbox(): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateAttendance(record: AttendanceEntity)

    @Query("SELECT * FROM cached_work_report_obligations ORDER BY periodKey DESC, dueAtIso DESC")
    fun observeAllWorkReports(): Flow<List<WorkReportObligationEntity>>

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
    version = 3,
    exportSchema = false
)
abstract class MiniBiDatabase : RoomDatabase() {
    abstract fun miniBiDao(): MiniBiDao

    companion object {
        @Volatile
        private var INSTANCE: MiniBiDatabase? = null

        fun getInstance(context: Context): MiniBiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MiniBiDatabase::class.java,
                    "mini_bi_hrpartner.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
