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

// 1. User / Actor Session Model
data class AppActor(
    val appUserId: String,
    val email: String,
    val displayName: String,
    val roleLabel: String,
    val audienceScope: String, // "all" (BoD), "team" (Team Leader), "own" (Recruiter)
    val teamName: String,
    val capabilities: List<String>
)

// 2. BI Reporting Models
data class BiRecruitmentFact(
    val id: String,
    val businessDate: String, // YYYY-MM-DD
    val projectKey: String,
    val projectDisplay: String,
    val recruiterKey: String,
    val recruiterDisplay: String,
    val providerTypeKey: String, // "hrp" | "vendor"
    val providerTypeDisplay: String, // "HRP" | "Vendor"
    val employmentTypeKey: String, // "thời vụ" | "chính thức"
    val employmentTypeDisplay: String, // "Thời vụ" | "Chính thức"
    val teamCode: String,
    val teamDisplayName: String,
    val recruitedCount: Int
)

data class BiBucketItem(
    val key: String,
    val display: String,
    val count: Int,
    val sharePct: Float
)

data class ProjectProviderMixItem(
    val projectKey: String,
    val projectDisplay: String,
    val total: Int,
    val hrpCount: Int,
    val vendorCount: Int,
    val hrpShare: Float,
    val vendorShare: Float
)

data class WorkforceDeepMetrics(
    val activeHeadcount: Int,
    val workingToday: Int,
    val waitingOnboarding: Int,
    val resignedThisMonth: Int,
    val placementRatePct: Float,
    val avgWaitingDays: Float,
    val retention7dPct: Float,
    val retention30dPct: Float,
    val avgTenureDays: Float,
    val leaveReasons: List<BiBucketItem>
)

data class DuplicateCccdAlert(
    val cccdMasked: String,
    val workerName: String,
    val projectA: String,
    val recruiterA: String,
    val projectB: String,
    val recruiterB: String,
    val detectedDate: String,
    val status: String // "ACTIVE_CONFLICT" | "PENDING_WITHDRAW" | "RESOLVED"
)

// 3. Room Entities for Attendance, Daily Work Reports, and Direct-Entry Worker Profiles
@Entity(tableName = "attendance_records")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordUuid: String,
    val appUserId: String,
    val userDisplayName: String,
    val teamName: String,
    val checkInType: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val addressHint: String,
    val photoUri: String,
    val photoSha256: String,
    val clientTimestampIso: String,
    val epochMillis: Long,
    val note: String,
    val syncStatus: String,
    val backendResponseCode: String
)

@Entity(tableName = "work_report_obligations")
data class WorkReportObligationEntity(
    @PrimaryKey val obligationId: String,
    val templateCode: String,
    val templateName: String,
    val periodKey: String, // e.g. "2026-10-23"
    val periodStartDate: String,
    val periodEndDate: String,
    val dueAtIso: String,
    val assigneeUserId: String,
    val assigneeDisplayName: String,
    val teamDisplayName: String,
    val obligationStatus: String, // "ACTIVE" | "COMPLETED"
    val submissionState: String, // "NOT_STARTED" | "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "REJECTED"
    val callsMade: Int,
    val interestedCandidates: Int,
    val workersStarted: Int,
    val notes: String,
    val submittedAtIso: String?,
    val reviewerName: String?,
    val reviewDecision: String?,
    val reviewReason: String?,
    val isLate: Boolean,
    val version: Int
)

@Entity(tableName = "worker_profiles")
data class WorkerProfileEntity(
    @PrimaryKey val workerCode: String,
    val fullName: String,
    val gender: String = "Nam", // "Nam" | "Nữ"
    val cccdMasked: String,
    val cccdFull: String = "",
    val phoneMasked: String,
    val phoneFull: String = "",
    val projectDisplay: String,
    val recruiterDisplay: String,
    val teamDisplay: String,
    val providerType: String, // "HRP" | "Vendor"
    val laborType: String, // "Chính thức" | "Thời vụ"
    val employmentStatus: String, // "Đang làm" | "Đang chờ việc" | "Đã nghỉ"
    val firstWorkDate: String, // DD/MM/YYYY or YYYY-MM-DD
    val bankName: String,
    val bankAccountMasked: String,
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val note: String = "",
    val birthDate: String = "",
    val cccdIssueDate: String = "",
    val address: String = "",
    val portraitUri: String = "",
    val cccdFrontUri: String = "",
    val cccdBackUri: String = "",
    val contractUri: String = "",
    val scopeRelation: String = "managed", // "managed" | "recruited" | "team" | "uploader"
    val pendingRequestSummary: String = "",
    val lastDecisionSummary: String = "",
    val canProposeChange: Boolean = true,
    val canDirectEdit: Boolean = true
)

// Draft row model for "Cấp mới" (Direct Entry Multi-Entry Form)
data class NewWorkerEntryDraft(
    val localId: String,
    val projectDisplay: String = "",
    val providerType: String = "", // "HRP" | "Vendor"
    val recruiterOrVendor: String = "",
    val laborType: String = "", // "Thời vụ" | "Chính thức"
    val firstWorkDate: String = "23/10/2026",
    val employmentStatus: String = "Đang làm", // "Đang làm" | "Đang chờ việc"
    val fullName: String = "",
    val gender: String = "", // "Nam" | "Nữ"
    val phone: String = "",
    val cccd: String = "",
    val portraitFileName: String = "",
    val portraitUri: String = "",
    val cccdFrontFileName: String = "",
    val cccdFrontUri: String = "",
    val cccdBackFileName: String = "",
    val cccdBackUri: String = "",
    val contractFileName: String = "",
    val contractUri: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val bankName: String = "",
    val note: String = "",
    val birthDate: String = "",
    val cccdIssueDate: String = "",
    val address: String = "",
    val isExpanded: Boolean = true,
    val showOptionalSection: Boolean = false
) {
    val isValid: Boolean
        get() = projectDisplay.isNotBlank() &&
            providerType.isNotBlank() &&
            recruiterOrVendor.isNotBlank() &&
            laborType.isNotBlank() &&
            firstWorkDate.isNotBlank() &&
            fullName.trim().length >= 2

    val isStarted: Boolean
        get() = projectDisplay.isNotBlank() ||
            providerType.isNotBlank() ||
            recruiterOrVendor.isNotBlank() ||
            laborType.isNotBlank() ||
            fullName.isNotBlank() ||
            gender.isNotBlank() ||
            phone.isNotBlank() ||
            cccd.isNotBlank() ||
            portraitFileName.isNotBlank() ||
            cccdFrontFileName.isNotBlank() ||
            cccdBackFileName.isNotBlank() ||
            contractFileName.isNotBlank() ||
            bankAccountNumber.isNotBlank() ||
            note.isNotBlank()
}

@Dao
interface MiniBiDao {
    @Query("SELECT * FROM attendance_records ORDER BY epochMillis DESC")
    fun observeAllAttendance(): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceEntity): Long

    @Query("UPDATE attendance_records SET syncStatus = :status, backendResponseCode = :code WHERE id = :id")
    suspend fun updateAttendanceSync(id: Long, status: String, code: String)

    @Query("SELECT * FROM work_report_obligations ORDER BY periodKey DESC, dueAtIso DESC")
    fun observeAllWorkReports(): Flow<List<WorkReportObligationEntity>>

    @Query("SELECT COUNT(*) FROM work_report_obligations")
    suspend fun countWorkReports(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkReports(items: List<WorkReportObligationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkReport(item: WorkReportObligationEntity)

    @Query("SELECT * FROM worker_profiles ORDER BY workerCode DESC")
    fun observeAllWorkers(): Flow<List<WorkerProfileEntity>>

    @Query("SELECT COUNT(*) FROM worker_profiles")
    suspend fun countWorkers(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkers(workers: List<WorkerProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorker(worker: WorkerProfileEntity)
}

@Database(
    entities = [
        AttendanceEntity::class,
        WorkReportObligationEntity::class,
        WorkerProfileEntity::class
    ],
    version = 2,
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
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
