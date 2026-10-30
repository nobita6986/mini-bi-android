package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.BuildConfig
import com.example.data.AppActor
import com.example.data.AttendanceAreaTab
import com.example.data.AttendanceEntity
import com.example.data.AttendanceTimeContract
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.BiBucketItem
import com.example.data.CapturedAttendanceTime
import com.example.data.CccdReviewQueueItem
import com.example.data.CccdReviewQueuePage
import com.example.data.CccdReviewStatusFilter
import com.example.data.CccdReviewSummary
import com.example.data.DashboardDatePreset
import com.example.data.DirectEditFileState
import com.example.data.DirectEditFileStatus
import com.example.data.DirectEntryCatalog
import com.example.data.DirectEntryContract
import com.example.data.DuplicateCccdGroupItem
import com.example.data.GeneralOverviewSnapshot
import com.example.data.LeaveApprovalInboxProjection
import com.example.data.LeaveContract
import com.example.data.LeaveDraftDayInput
import com.example.data.LeaveMeSummaryProjection
import com.example.data.LeaveRequestProjection
import com.example.data.MiniBiRepository
import com.example.data.NewWorkerEntryDraft
import com.example.data.OwnSubmissionItem
import com.example.data.PendingDuplicateCccdSubmission
import com.example.data.PendingEntryAttachmentTask
import com.example.data.SavedLoginAccount
import com.example.data.StagedDocumentRef
import com.example.data.TeamReportSnapshot
import com.example.data.TimeAnchorUiState
import com.example.data.WorkReportAnalyticsSnapshot
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportReviewItem
import com.example.data.WorkReportTemplateOption
import com.example.data.WorkReportsContract
import com.example.data.WorkerDirectorySummary
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.data.WorkforceDeepMetrics
import com.example.ui.screens.resolveVisibleWorkerTabs
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class MainDestination(val route: String, val label: String) {
    DASHBOARD("dashboard", "Tổng quan"),
    WORK_REPORTS("work_reports", "Báo cáo"),
    ATTENDANCE("attendance", "Chấm công"),
    WORKERS("workers", "Nhân sự"),
    NEW_ENTRY("new_entry", "Cấp mới")
}

data class GpsSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val addressHint: String,
    val capturedAtMillis: Long = System.currentTimeMillis()
)

class MiniBiViewModel internal constructor(
    application: Application,
    internal val repository: MiniBiRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, MiniBiRepository(application))

    val imageLoader: ImageLoader
        get() = repository.imageLoader

    val themeId: StateFlow<String> = repository.themeIdFlow
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "hr-partner")

    val serverUrl: StateFlow<String> = repository.serverUrlFlow
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BuildConfig.DEFAULT_SERVER_URL)

    val currentActor: StateFlow<AppActor?> = repository.currentActorFlow

    val savedAccounts: StateFlow<List<SavedLoginAccount>> = repository.savedAccountsFlow

    private val _sessionBootstrapping = MutableStateFlow(true)
    val sessionBootstrapping: StateFlow<Boolean> = _sessionBootstrapping.asStateFlow()

    private val _currentDestination = MutableStateFlow(MainDestination.DASHBOARD)
    val currentDestination: StateFlow<MainDestination> = _currentDestination.asStateFlow()

    // Login state
    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Status Banner state
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _statusBannerIsError = MutableStateFlow(false)
    val statusBannerIsError: StateFlow<Boolean> = _statusBannerIsError.asStateFlow()

    private var bannerAutoDismissJob: Job? = null

    // Reporting & Dashboard States (Backend authority only; zero seed data)
    private val _generalOverview = MutableStateFlow<GeneralOverviewSnapshot?>(null)
    val generalOverview: StateFlow<GeneralOverviewSnapshot?> = _generalOverview.asStateFlow()

    private val _generalOverviewLoading = MutableStateFlow(false)
    val generalOverviewLoading: StateFlow<Boolean> = _generalOverviewLoading.asStateFlow()

    private val _generalOverviewError = MutableStateFlow<String?>(null)
    val generalOverviewError: StateFlow<String?> = _generalOverviewError.asStateFlow()

    private val _workforceDeep = MutableStateFlow<WorkforceDeepMetrics?>(null)
    val workforceDeep: StateFlow<WorkforceDeepMetrics?> = _workforceDeep.asStateFlow()

    private val _workforceLoading = MutableStateFlow(false)
    val workforceLoading: StateFlow<Boolean> = _workforceLoading.asStateFlow()

    private val _workforceError = MutableStateFlow<String?>(null)
    val workforceError: StateFlow<String?> = _workforceError.asStateFlow()

    private val _teamReport = MutableStateFlow<TeamReportSnapshot?>(null)
    val teamReport: StateFlow<TeamReportSnapshot?> = _teamReport.asStateFlow()

    private val _teamReportLoading = MutableStateFlow(false)
    val teamReportLoading: StateFlow<Boolean> = _teamReportLoading.asStateFlow()

    private val _teamReportError = MutableStateFlow<String?>(null)
    val teamReportError: StateFlow<String?> = _teamReportError.asStateFlow()

    private val _teamScopeOptions = MutableStateFlow<List<BiBucketItem>>(emptyList())
    val teamScopeOptions: StateFlow<List<BiBucketItem>> = _teamScopeOptions.asStateFlow()

    private val _dashboardDatePreset = MutableStateFlow(DashboardDatePreset.ALL_TIME)
    val dashboardDatePreset: StateFlow<DashboardDatePreset> = _dashboardDatePreset.asStateFlow()

    private var cachedAdminTeamCodeMap: Map<String, String>? = null
    private var cachedTeamScopeWorkers: List<WorkerProfileEntity> = emptyList()
    private var cachedTeamTotalSummary: Int = 0

    private val _duplicateGroups = MutableStateFlow<List<DuplicateCccdGroupItem>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateCccdGroupItem>> = _duplicateGroups.asStateFlow()

    private val _duplicateLoading = MutableStateFlow(false)
    val duplicateLoading: StateFlow<Boolean> = _duplicateLoading.asStateFlow()

    private val _duplicateError = MutableStateFlow<String?>(null)
    val duplicateError: StateFlow<String?> = _duplicateError.asStateFlow()

    private val _duplicatePage = MutableStateFlow(1)
    val duplicatePage: StateFlow<Int> = _duplicatePage.asStateFlow()

    private val _duplicateHasMore = MutableStateFlow(false)
    val duplicateHasMore: StateFlow<Boolean> = _duplicateHasMore.asStateFlow()

    private val _duplicateManagedOnly = MutableStateFlow(false)
    val duplicateManagedOnly: StateFlow<Boolean> = _duplicateManagedOnly.asStateFlow()

    // CCCD Review & Normalization Queue States (Backend authority only)
    private val _cccdReviewPage = MutableStateFlow<CccdReviewQueuePage?>(null)
    val cccdReviewPage: StateFlow<CccdReviewQueuePage?> = _cccdReviewPage.asStateFlow()

    private val _cccdReviewLoading = MutableStateFlow(false)
    val cccdReviewLoading: StateFlow<Boolean> = _cccdReviewLoading.asStateFlow()

    private val _cccdReviewError = MutableStateFlow<String?>(null)
    val cccdReviewError: StateFlow<String?> = _cccdReviewError.asStateFlow()

    private val _cccdReviewStatusFilter = MutableStateFlow(CccdReviewStatusFilter.NEEDS_REVIEW.code)
    val cccdReviewStatusFilter: StateFlow<String> = _cccdReviewStatusFilter.asStateFlow()

    private val _cccdReviewOffset = MutableStateFlow(0)
    val cccdReviewOffset: StateFlow<Int> = _cccdReviewOffset.asStateFlow()

    private val _cccdReviewMutating = MutableStateFlow(false)
    val cccdReviewMutating: StateFlow<Boolean> = _cccdReviewMutating.asStateFlow()

    private val _cccdReviewMutationError = MutableStateFlow<String?>(null)
    val cccdReviewMutationError: StateFlow<String?> = _cccdReviewMutationError.asStateFlow()

    // Direct Entry & Worker Directory States (Backend authority only; zero seed data)
    private val _catalog = MutableStateFlow<DirectEntryCatalog?>(null)
    val catalog: StateFlow<DirectEntryCatalog?> = _catalog.asStateFlow()

    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    private val _workerRows = MutableStateFlow<List<WorkerProfileEntity>>(emptyList())
    val workerRows: StateFlow<List<WorkerProfileEntity>> = _workerRows.asStateFlow()

    private val _workerLoading = MutableStateFlow(false)
    val workerLoading: StateFlow<Boolean> = _workerLoading.asStateFlow()

    private val _workerError = MutableStateFlow<String?>(null)
    val workerError: StateFlow<String?> = _workerError.asStateFlow()

    private val _ownSubmissions = MutableStateFlow<List<OwnSubmissionItem>>(emptyList())
    val ownSubmissions: StateFlow<List<OwnSubmissionItem>> = _ownSubmissions.asStateFlow()

    private val _activeWorkerTabKey = MutableStateFlow("managed")
    val activeWorkerTabKey: StateFlow<String> = _activeWorkerTabKey.asStateFlow()

    private val _workerHasMore = MutableStateFlow(false)
    val workerHasMore: StateFlow<Boolean> = _workerHasMore.asStateFlow()

    private val _workerNextCursor = MutableStateFlow<String?>(null)

    private val _submissionsHasMore = MutableStateFlow(false)
    val submissionsHasMore: StateFlow<Boolean> = _submissionsHasMore.asStateFlow()

    private val _submissionsNextCursor = MutableStateFlow<String?>(null)

    private val _workerSummary = MutableStateFlow<WorkerDirectorySummary?>(null)
    val workerSummary: StateFlow<WorkerDirectorySummary?> = _workerSummary.asStateFlow()

    private val _selectedEntryDetail = MutableStateFlow<WorkerEntryDetail?>(null)
    val selectedEntryDetail: StateFlow<WorkerEntryDetail?> = _selectedEntryDetail.asStateFlow()

    private val _proposeChangeTarget = MutableStateFlow<Pair<WorkerProfileEntity, WorkerEntryDetail?>?>(null)
    val proposeChangeTarget: StateFlow<Pair<WorkerProfileEntity, WorkerEntryDetail?>?> = _proposeChangeTarget.asStateFlow()

    private val _directEditTarget = MutableStateFlow<Pair<WorkerProfileEntity, WorkerEntryDetail>?>(null)
    val directEditTarget: StateFlow<Pair<WorkerProfileEntity, WorkerEntryDetail>?> = _directEditTarget.asStateFlow()

    private val _directEditProfileSaveMessage = MutableStateFlow<String?>(null)
    val directEditProfileSaveMessage: StateFlow<String?> = _directEditProfileSaveMessage.asStateFlow()

    private val _directEditFileStatuses = MutableStateFlow<Map<String, DirectEditFileStatus>>(emptyMap())
    val directEditFileStatuses: StateFlow<Map<String, DirectEditFileStatus>> = _directEditFileStatuses.asStateFlow()

    private val _workerMutationBusy = MutableStateFlow(false)
    val workerMutationBusy: StateFlow<Boolean> = _workerMutationBusy.asStateFlow()

    private val _workerMutationError = MutableStateFlow<String?>(null)
    val workerMutationError: StateFlow<String?> = _workerMutationError.asStateFlow()

    private val _pendingDuplicateCccdSubmission = MutableStateFlow<PendingDuplicateCccdSubmission?>(null)
    val pendingDuplicateCccdSubmission: StateFlow<PendingDuplicateCccdSubmission?> =
        _pendingDuplicateCccdSubmission.asStateFlow()

    private val _backgroundUploadActiveCount = MutableStateFlow(0)
    val backgroundUploadActiveCount: StateFlow<Int> = _backgroundUploadActiveCount.asStateFlow()

    private val _failedBackgroundUploads = MutableStateFlow<List<PendingEntryAttachmentTask>>(emptyList())
    private val _hasFailedBackgroundUploads = MutableStateFlow(false)
    val hasFailedBackgroundUploads: StateFlow<Boolean> = _hasFailedBackgroundUploads.asStateFlow()

    private val _profileSaveStatusMessage = MutableStateFlow<String?>(null)
    val profileSaveStatusMessage: StateFlow<String?> = _profileSaveStatusMessage.asStateFlow()

    private val _photoUploadStatusMessage = MutableStateFlow<String?>(null)
    val photoUploadStatusMessage: StateFlow<String?> = _photoUploadStatusMessage.asStateFlow()

    private val _pendingOfflineProfileDrafts = MutableStateFlow<List<NewWorkerEntryDraft>>(emptyList())
    val pendingOfflineProfileDrafts: StateFlow<List<NewWorkerEntryDraft>> = _pendingOfflineProfileDrafts.asStateFlow()

    val newWorkerDraftRows = mutableStateListOf<NewWorkerEntryDraft>()

    fun ensureInitialNewWorkerDraftRow(catalog: DirectEntryCatalog? = _catalog.value) {
        if (newWorkerDraftRows.isEmpty()) {
            newWorkerDraftRows.add(DirectEntryContract.createDefaultWorkerDraft(catalog = catalog))
        }
    }

    private val backgroundUploadMutex = Mutex()

    private var workerFetchJob: Job? = null
    private var workerFetchToken: Long = 0L
    private var lastSearchQuery: String = ""
    private var lastFilterProjectId: String? = null
    private var lastFilterRecruiterId: String? = null
    private var lastFilterStatus: String? = null

    // Work Reports States (Backend authority only; zero seed data)
    val workReports: StateFlow<List<WorkReportObligationEntity>> = repository.workReportsFlow
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _workReportsLoading = MutableStateFlow(false)
    val workReportsLoading: StateFlow<Boolean> = _workReportsLoading.asStateFlow()

    private val _workReportsError = MutableStateFlow<String?>(null)
    val workReportsError: StateFlow<String?> = _workReportsError.asStateFlow()

    private val _reviewQueue = MutableStateFlow<List<WorkReportReviewItem>>(emptyList())
    val reviewQueue: StateFlow<List<WorkReportReviewItem>> = _reviewQueue.asStateFlow()

    private val _analyticsSnapshot = MutableStateFlow<WorkReportAnalyticsSnapshot?>(null)
    val analyticsSnapshot: StateFlow<WorkReportAnalyticsSnapshot?> = _analyticsSnapshot.asStateFlow()

    private val _availableReportTemplates = MutableStateFlow<List<WorkReportTemplateOption>>(emptyList())
    val availableReportTemplates: StateFlow<List<WorkReportTemplateOption>> = _availableReportTemplates.asStateFlow()

    private val _editingObligation = MutableStateFlow<WorkReportObligationEntity?>(null)
    val editingObligation: StateFlow<WorkReportObligationEntity?> = _editingObligation.asStateFlow()

    private val _workReportMutationBusy = MutableStateFlow(false)
    val workReportMutationBusy: StateFlow<Boolean> = _workReportMutationBusy.asStateFlow()

    // Attendance GPS & Outbox States (Zero fake GPS fallback)
    private val _gpsSnapshot = MutableStateFlow<GpsSnapshot?>(null)
    val gpsSnapshot: StateFlow<GpsSnapshot?> = _gpsSnapshot.asStateFlow()

    private val _gpsLoading = MutableStateFlow(false)
    val gpsLoading: StateFlow<Boolean> = _gpsLoading.asStateFlow()

    private val _gpsError = MutableStateFlow<String?>(null)
    val gpsError: StateFlow<String?> = _gpsError.asStateFlow()

    private val _submittingAttendance = MutableStateFlow(false)
    val submittingAttendance: StateFlow<Boolean> = _submittingAttendance.asStateFlow()

    val timeAnchorState: StateFlow<TimeAnchorUiState> = repository.attendanceTimeAnchorManager.uiState

    val attendanceRecords: StateFlow<List<AttendanceEntity>> = repository.attendanceFlow
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Leave Request & Leave Approval States (Backend authority only; zero client-side role inference)
    private val _activeAttendanceTab = MutableStateFlow(AttendanceAreaTab.CHECK_IN)
    val activeAttendanceTab: StateFlow<AttendanceAreaTab> = _activeAttendanceTab.asStateFlow()

    private val _leaveYear = MutableStateFlow(LeaveContract.currentVietnamLeaveYear())
    val leaveYear: StateFlow<Int> = _leaveYear.asStateFlow()

    private val _myLeaveSummary = MutableStateFlow<LeaveMeSummaryProjection?>(null)
    val myLeaveSummary: StateFlow<LeaveMeSummaryProjection?> = _myLeaveSummary.asStateFlow()

    private val _myLeaveLoading = MutableStateFlow(false)
    val myLeaveLoading: StateFlow<Boolean> = _myLeaveLoading.asStateFlow()

    private val _myLeaveError = MutableStateFlow<String?>(null)
    val myLeaveError: StateFlow<String?> = _myLeaveError.asStateFlow()

    private val _myLeaveErrorCode = MutableStateFlow<String?>(null)
    val myLeaveErrorCode: StateFlow<String?> = _myLeaveErrorCode.asStateFlow()

    val leaveDraftDays = mutableStateListOf<LeaveDraftDayInput>()

    private val _leaveDraftReason = MutableStateFlow("")
    val leaveDraftReason: StateFlow<String> = _leaveDraftReason.asStateFlow()

    private val _leaveMutationBusy = MutableStateFlow(false)
    val leaveMutationBusy: StateFlow<Boolean> = _leaveMutationBusy.asStateFlow()

    private val _leaveMutationError = MutableStateFlow<String?>(null)
    val leaveMutationError: StateFlow<String?> = _leaveMutationError.asStateFlow()

    private val _leaveInboxStatusFilter = MutableStateFlow("PENDING")
    val leaveInboxStatusFilter: StateFlow<String> = _leaveInboxStatusFilter.asStateFlow()

    private val _leaveInboxPage = MutableStateFlow(1)
    val leaveInboxPage: StateFlow<Int> = _leaveInboxPage.asStateFlow()

    private val _leaveApprovalInbox = MutableStateFlow<LeaveApprovalInboxProjection?>(null)
    val leaveApprovalInbox: StateFlow<LeaveApprovalInboxProjection?> = _leaveApprovalInbox.asStateFlow()

    private val _leaveInboxLoading = MutableStateFlow(false)
    val leaveInboxLoading: StateFlow<Boolean> = _leaveInboxLoading.asStateFlow()

    private val _leaveInboxError = MutableStateFlow<String?>(null)
    val leaveInboxError: StateFlow<String?> = _leaveInboxError.asStateFlow()

    init {
        bootstrapSession()
    }

    internal fun cancelScopeForTest() {
        clearBanner()
        workerFetchJob?.cancel()
        workerFetchJob = null
        viewModelScope.cancel()
        repository.closeForTest()
    }

    fun bootstrapSession() {
        _sessionBootstrapping.value = true
        viewModelScope.launch {
            try {
                val res = repository.bootstrapSession()
                _sessionBootstrapping.value = false
                res.onSuccess { actor ->
                    syncAttendanceTimeAnchor(actor = actor, force = false)
                    syncAttendanceHistoryFromServer(actor = actor)
                    refreshDashboardData(actor)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _sessionBootstrapping.value = false
            }
        }
    }

    fun navigateTo(destination: MainDestination) {
        _currentDestination.value = destination
        val actor = currentActor.value ?: return
        when (destination) {
            MainDestination.DASHBOARD -> {
                if ((_generalOverview.value == null && !_generalOverviewLoading.value) ||
                    (_duplicateGroups.value.isEmpty() && !_duplicateLoading.value)
                ) {
                    refreshDashboardData(actor)
                }
            }
            MainDestination.WORK_REPORTS -> {
                refreshWorkReportsData(actor)
            }
            MainDestination.NEW_ENTRY -> {
                loadCatalogIfNeeded()
                if (actor.hasCapability("submission_read") || actor.hasCapability("submission_create")) {
                    refreshOwnSubmissionsForHistory()
                }
            }
            MainDestination.WORKERS -> {
                loadCatalogIfNeeded()
                ensureInitialWorkerTabLoaded(actor)
            }
            MainDestination.ATTENDANCE -> {
                _activeAttendanceTab.value = AttendanceAreaTab.CHECK_IN
                syncAttendanceTimeAnchor(actor = actor, force = false)
                syncAttendanceHistoryFromServer(actor = actor)
                refreshMyLeaveSummary(year = _leaveYear.value)
            }
        }
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            runCatching { repository.setThemeId(themeId) }
                .onFailure { it.rethrowIfCancelled() }
        }
    }

    private fun Throwable.rethrowIfCancelled() {
        if (this is CancellationException) throw this
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            repository.setServerUrl(url)
                .onSuccess { showBanner("Đã cập nhật URL máy chủ: $it") }
                .onFailure { err ->
                    err.rethrowIfCancelled()
                    showBanner(err.message ?: "URL máy chủ không hợp lệ.", isError = true)
                }
        }
    }

    fun clearBanner() {
        bannerAutoDismissJob?.cancel()
        bannerAutoDismissJob = null
        _statusBannerMessage.value = null
        _statusBannerIsError.value = false
    }

    fun showBanner(message: String, isError: Boolean = false) {
        bannerAutoDismissJob?.cancel()
        _statusBannerMessage.value = message
        _statusBannerIsError.value = isError
        bannerAutoDismissJob = viewModelScope.launch {
            delay(BANNER_AUTO_DISMISS_MS)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
                _statusBannerIsError.value = false
            }
        }
    }

    fun login(identifier: String, password: String, rememberAccount: Boolean = true) {
        if (_loginBusy.value) return
        _loginBusy.value = true
        _loginError.value = null
        viewModelScope.launch {
            try {
                val result = repository.login(
                    rawIdentifier = identifier,
                    password = password,
                    rememberAccount = rememberAccount
                )
                result.onSuccess { actor ->
                    _loginError.value = null
                    showBanner("Xin chào ${actor.displayName}")
                    syncAttendanceTimeAnchor(actor = actor, force = true)
                    syncAttendanceHistoryFromServer(actor = actor)
                    refreshDashboardData(actor)
                }.onFailure { err ->
                    err.rethrowIfCancelled()
                    _loginError.value = (err as? BackendApiException)?.message
                        ?: err.message
                        ?: "Không thể kết nối đăng nhập. Vui lòng thử lại."
                }
            } finally {
                _loginBusy.value = false
            }
        }
    }

    fun removeSavedAccount(identifier: String) {
        viewModelScope.launch {
            repository.removeSavedAccount(identifier)
        }
    }

    fun logout() {
        clearBanner()
        ++workerFetchToken
        workerFetchJob?.cancel()
        workerFetchJob = null
        viewModelScope.launch {
            repository.logout()
            _currentDestination.value = MainDestination.DASHBOARD
            _generalOverview.value = null
            _generalOverviewLoading.value = false
            _generalOverviewError.value = null
            _workforceDeep.value = null
            _workforceError.value = null
            _teamReport.value = null
            _teamReportError.value = null
            _teamScopeOptions.value = emptyList()
            _dashboardDatePreset.value = DashboardDatePreset.ALL_TIME
            cachedAdminTeamCodeMap = null
            cachedTeamScopeWorkers = emptyList()
            cachedTeamTotalSummary = 0
            _duplicateGroups.value = emptyList()
            _duplicateError.value = null
            _duplicatePage.value = 1
            _duplicateHasMore.value = false
            _duplicateManagedOnly.value = false
            _cccdReviewPage.value = null
            _cccdReviewLoading.value = false
            _cccdReviewError.value = null
            _cccdReviewStatusFilter.value = CccdReviewStatusFilter.NEEDS_REVIEW.code
            _cccdReviewOffset.value = 0
            _cccdReviewMutating.value = false
            _cccdReviewMutationError.value = null
            _catalog.value = null
            _workerRows.value = emptyList()
            _ownSubmissions.value = emptyList()
            _workerSummary.value = null
            _workerLoading.value = false
            _workerError.value = null
            _workerHasMore.value = false
            _workerNextCursor.value = null
            _submissionsHasMore.value = false
            _submissionsNextCursor.value = null
            _selectedEntryDetail.value = null
            _proposeChangeTarget.value = null
            _directEditTarget.value = null
            _workerMutationError.value = null
            _pendingDuplicateCccdSubmission.value = null
            _failedBackgroundUploads.value = emptyList()
            _hasFailedBackgroundUploads.value = false
            _backgroundUploadActiveCount.value = 0
            _profileSaveStatusMessage.value = null
            _photoUploadStatusMessage.value = null
            _pendingOfflineProfileDrafts.value = emptyList()
            newWorkerDraftRows.toList()
                .flatMap { it.allAttachments() }
                .forEach { discardDraftAttachment(it) }
            newWorkerDraftRows.clear()
            _editingObligation.value = null
            _workReportsError.value = null
            _reviewQueue.value = emptyList()
            _analyticsSnapshot.value = null
            _availableReportTemplates.value = emptyList()
            _activeAttendanceTab.value = AttendanceAreaTab.CHECK_IN
            _leaveYear.value = LeaveContract.currentVietnamLeaveYear()
            _myLeaveSummary.value = null
            _myLeaveLoading.value = false
            _myLeaveError.value = null
            _myLeaveErrorCode.value = null
            leaveDraftDays.clear()
            _leaveDraftReason.value = ""
            _leaveMutationBusy.value = false
            _leaveMutationError.value = null
            _leaveInboxStatusFilter.value = "PENDING"
            _leaveInboxPage.value = 1
            _leaveApprovalInbox.value = null
            _leaveInboxLoading.value = false
            _leaveInboxError.value = null
        }
    }

    // =========================================================================
    // Reporting, Dashboard & Duplicate CCCD Actions (Backend authority only)
    // =========================================================================

    private val teamGrantUuidRegex = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    private data class PaginatedScopeWorkers(
        val items: List<WorkerProfileEntity>,
        val summary: WorkerDirectorySummary,
        val authorizationDate: String
    )

    private fun filterWorkersByDateRange(
        items: List<WorkerProfileEntity>,
        fromDate: String?,
        toDate: String?
    ): List<WorkerProfileEntity> {
        if (fromDate.isNullOrBlank() && toDate.isNullOrBlank()) return items
        return items.filter { row ->
            val d = row.firstWorkDate.trim()
            d.isNotBlank() &&
                (fromDate.isNullOrBlank() || d >= fromDate) &&
                (toDate.isNullOrBlank() || d <= toDate)
        }
    }

    private fun buildGrantedTeamSnapshotFromCache(
        actor: AppActor,
        teamRef: String,
        fromDate: String? = null,
        toDate: String? = null
    ): TeamReportSnapshot {
        val recruiterMap = _catalog.value?.recruiters.orEmpty().associateBy { it.recruiterId }
        val teamGrants = actor.scopes
            .filter { it.kind == "team" && !it.reference.isNullOrBlank() }
            .mapNotNull { it.reference?.trim() }
            .distinct()
        val matchedTeamName = recruiterMap.values
            .firstOrNull { it.teamId == teamRef && !it.teamDisplayName.isNullOrBlank() }
            ?.teamDisplayName
            ?.trim()
        val bucketDisplay = matchedTeamName
            ?: if (teamGrants.size <= 1) "Nhóm của tôi" else "Nhóm phụ trách"

        val canFilterByCatalogTeam = teamGrants.size > 1 &&
            recruiterMap.values.any { it.teamId == teamRef }
        val baseRows = if (canFilterByCatalogTeam) {
            cachedTeamScopeWorkers.filter { row ->
                recruiterMap[row.recruiterId]?.teamId == teamRef
            }
        } else {
            cachedTeamScopeWorkers
        }
        val scopedRows = filterWorkersByDateRange(baseRows, fromDate, toDate)
        val hasDateFilter = !fromDate.isNullOrBlank() || !toDate.isNullOrBlank()
        val totalOverride = if (!hasDateFilter && teamGrants.size <= 1) cachedTeamTotalSummary else null
        return WorkReportsContract.buildTeamReportFromWorkerRows(
            bucketKey = "grant:$teamRef",
            bucketDisplay = bucketDisplay,
            rows = scopedRows,
            recruiterCatalogById = recruiterMap,
            totalWorkersOverride = totalOverride
        )
    }

    fun selectDashboardDatePreset(
        preset: DashboardDatePreset,
        actor: AppActor? = currentActor.value
    ) {
        if (_dashboardDatePreset.value == preset &&
            _generalOverview.value != null &&
            _generalOverviewError.value == null
        ) {
            return
        }
        _dashboardDatePreset.value = preset
        refreshDashboardData(actor)
    }

    fun refreshDashboardData(actor: AppActor? = currentActor.value) {
        val resolvedActor = actor ?: return
        refreshDuplicateCccdReport(page = _duplicatePage.value, managedOnly = _duplicateManagedOnly.value)
        if (resolvedActor.hasScopeKind("all")) {
            refreshWorkforceDeep()
        } else {
            _workforceDeep.value = null
            _workforceError.value = null
            refreshScopedGeneralOverview(resolvedActor)
        }
    }

    fun refreshWorkforceDeep(fromDate: String? = null, toDate: String? = null) {
        val actor = currentActor.value ?: return
        if (!actor.hasScopeKind("all")) {
            refreshScopedGeneralOverview(actor)
            return
        }
        val (effectiveFrom, effectiveTo) = if (fromDate != null || toDate != null) {
            fromDate to toDate
        } else {
            WorkReportsContract.resolveDatePresetRange(_dashboardDatePreset.value)
        }
        _workforceLoading.value = true
        _workforceError.value = null
        _generalOverviewLoading.value = true
        _generalOverviewError.value = null
        viewModelScope.launch {
            try {
                if (cachedAdminTeamCodeMap == null && actor.hasCapability("catalog_master_manage")) {
                    cachedAdminTeamCodeMap = repository.workReportsAndReportingService.fetchAdminTeamCodeMap()
                }
                val codeMap = cachedAdminTeamCodeMap.orEmpty()
                val rawMetrics = repository.workReportsAndReportingService.fetchWorkforceDeep(
                    fromDate = effectiveFrom,
                    toDate = effectiveTo
                )
                val normalizedTeams = rawMetrics.byTeam.map { b ->
                    val canonicalCode = codeMap[b.key.lowercase(Locale.ROOT)]
                        ?: codeMap["display:${b.display.trim().lowercase(Locale.ROOT)}"]
                        ?: b.key
                    if (canonicalCode != b.key) b.copy(key = canonicalCode) else b
                }
                val metrics = rawMetrics.copy(byTeam = normalizedTeams)
                _workforceDeep.value = metrics
                _teamScopeOptions.value = normalizedTeams

                val hasDateFilter = !effectiveFrom.isNullOrBlank() || !effectiveTo.isNullOrBlank()
                val closingWorkingRatePct = if (metrics.eligibleEpisodeTotal > 0) {
                    ((metrics.onCount.toDouble() / metrics.eligibleEpisodeTotal.toDouble()) * 1000.0)
                        .roundToInt() / 10f
                } else {
                    null
                }

                if (!hasDateFilter) {
                    val leadingProject = metrics.byProject.firstOrNull { it.key != "unknown" }
                        ?: metrics.byProject.firstOrNull()
                    val resolvedProjectCount = metrics.totalProjectCount.takeIf { it > 0 }
                        ?: metrics.byProject.count { it.key != "unknown" }.coerceAtLeast(metrics.byProject.size)

                    _generalOverview.value = GeneralOverviewSnapshot(
                        asOfDate = metrics.asOfDate,
                        scopeDescription = "Toàn hệ thống",
                        totalProfiles = metrics.eligibleEpisodeTotal,
                        workingCount = metrics.onCount,
                        waitingCount = metrics.unconfirmedCount,
                        leftCount = metrics.offCount,
                        audienceKind = "all",
                        isPeriodFiltered = false,
                        workingRatePct = closingWorkingRatePct,
                        placementRatePct = metrics.placementRatePct,
                        retention7dPct = metrics.retention7dPct,
                        retention30dPct = metrics.retention30dPct,
                        projectCount = resolvedProjectCount,
                        recruiterCount = metrics.byRecruiter.count { it.key != "unknown" }.coerceAtLeast(metrics.byRecruiter.size),
                        leadingProject = leadingProject,
                        byRecruiter = metrics.byRecruiter.take(10),
                        byProject = metrics.byProject.take(10),
                        bySecondaryGroup = normalizedTeams.take(10),
                        secondaryGroupTitle = "Cơ cấu theo nhóm tuyển dụng",
                        byProvider = metrics.byProvider.take(6),
                        periodFromDate = metrics.periodFromDate,
                        periodToDate = metrics.periodToDate,
                        periodNewEpisodes = metrics.periodNewEpisodes,
                        periodOnInflow = metrics.periodOnInflow,
                        periodOffInflow = metrics.periodOffInflow,
                        periodNetOnChange = metrics.periodNetOnChange
                    )
                } else {
                    val periodAgg = repository.workReportsAndReportingService
                        .fetchAggregatedPeriodRecruitmentForAllScope(
                            teamBuckets = normalizedTeams,
                            knownTeamCodeMap = codeMap,
                            fromDate = effectiveFrom,
                            toDate = effectiveTo
                        )
                    val periodByProject = periodAgg?.byProject.orEmpty()
                    val periodByRecruiter = periodAgg?.byRecruiter.orEmpty()
                    val periodByTeam = periodAgg?.byTeam.orEmpty()
                    val periodByProvider = periodAgg?.byProvider.orEmpty()
                    val leadingPeriodProject = periodByProject.firstOrNull { it.key != "unknown" }
                        ?: periodByProject.firstOrNull()

                    _generalOverview.value = GeneralOverviewSnapshot(
                        asOfDate = metrics.asOfDate,
                        scopeDescription = "Toàn hệ thống",
                        totalProfiles = metrics.periodNewEpisodes ?: periodAgg?.totalRecruited ?: 0,
                        workingCount = metrics.periodOnInflow ?: 0,
                        waitingCount = metrics.periodWaitingInflow ?: 0,
                        leftCount = metrics.periodOffInflow ?: 0,
                        audienceKind = "all",
                        isPeriodFiltered = true,
                        workingRatePct = closingWorkingRatePct,
                        placementRatePct = metrics.placementRatePct,
                        retention7dPct = metrics.retention7dPct,
                        retention30dPct = metrics.retention30dPct,
                        closingTotalProfiles = metrics.eligibleEpisodeTotal,
                        closingWorkingCount = metrics.onCount,
                        closingWaitingCount = metrics.unconfirmedCount,
                        closingLeftCount = metrics.offCount,
                        projectCount = periodByProject.count { it.key != "unknown" && it.count > 0 },
                        recruiterCount = periodByRecruiter.count { it.key != "unknown" && it.count > 0 },
                        activeHiringDays = periodAgg?.hiringSummary?.activeHiringDays ?: 0,
                        avgPerActiveDay = periodAgg?.hiringSummary?.avgPerActiveDay,
                        peakDailyCount = periodAgg?.hiringSummary?.peakDailyCount,
                        peakDateIso = periodAgg?.hiringSummary?.peakDateIso,
                        leadingProject = leadingPeriodProject,
                        byRecruiter = periodByRecruiter.take(10),
                        byProject = periodByProject.take(10),
                        bySecondaryGroup = periodByTeam.take(10),
                        secondaryGroupTitle = "Cơ cấu theo nhóm tuyển dụng",
                        byProvider = periodByProvider.take(6),
                        periodFromDate = effectiveFrom ?: metrics.periodFromDate,
                        periodToDate = effectiveTo ?: metrics.periodToDate,
                        periodNewEpisodes = metrics.periodNewEpisodes,
                        periodOnInflow = metrics.periodOnInflow,
                        periodOffInflow = metrics.periodOffInflow,
                        periodNetOnChange = metrics.periodNetOnChange
                    )
                }
                _generalOverviewLoading.value = false

                val prevReport = _teamReport.value
                val matchedExistingBucket = if (prevReport != null) {
                    normalizedTeams.firstOrNull { b ->
                        b.key == prevReport.bucketKey ||
                            b.display == prevReport.bucketDisplay ||
                            runCatching { WorkReportsContract.encodeTeamBucketKey(b.key) == prevReport.bucketKey }
                                .getOrDefault(false)
                    }
                } else {
                    null
                }
                val targetBucket = matchedExistingBucket
                    ?: normalizedTeams.firstOrNull { it.key != "unknown" && it.key != "__system_vendor__" }
                    ?: normalizedTeams.firstOrNull { it.key != "unknown" }
                    ?: normalizedTeams.firstOrNull()
                if (targetBucket != null) {
                    loadTeamReport(targetBucket.key, effectiveFrom, effectiveTo)
                }
            } catch (e: BackendApiException) {
                if (e.kind == BackendFailureKind.FORBIDDEN) {
                    _workforceError.value = e.message
                    refreshScopedGeneralOverview(actor)
                } else {
                    _workforceError.value = e.message
                    _generalOverviewError.value = e.message
                    _generalOverviewLoading.value = false
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = e.message ?: "Không thể tải báo cáo lực lượng lao động."
                _workforceError.value = msg
                _generalOverviewError.value = msg
                _generalOverviewLoading.value = false
            } finally {
                _workforceLoading.value = false
            }
        }
    }

    private fun refreshScopedGeneralOverview(actor: AppActor) {
        val isTeamActor = actor.hasScopeKind("team")
        _generalOverviewLoading.value = true
        _generalOverviewError.value = null
        if (isTeamActor) {
            _teamReportLoading.value = true
            _teamReportError.value = null
        } else {
            _teamReport.value = null
            _teamScopeOptions.value = emptyList()
            cachedTeamScopeWorkers = emptyList()
            cachedTeamTotalSummary = 0
        }
        viewModelScope.launch {
            try {
                if (_catalog.value == null) {
                    runCatching {
                        _catalog.value = repository.directEntryService.fetchCatalog()
                    }.onFailure { it.rethrowIfCancelled() }
                }
                val recruiterMap = _catalog.value?.recruiters.orEmpty().associateBy { it.recruiterId }

                var firstError: Exception? = null
                suspend fun fetchAllScopePagesOrNull(
                    scopeName: String,
                    maxPages: Int = 10
                ): PaginatedScopeWorkers? {
                    return try {
                        val firstPage = repository.directEntryService.listWorkers(
                            scope = scopeName,
                            cursor = null,
                            pageSize = 100
                        )
                        val deduped = linkedMapOf<String, WorkerProfileEntity>()
                        firstPage.items.forEach { deduped.putIfAbsent(it.entryId, it) }
                        var hasMore = firstPage.hasMore
                        var nextCursor = firstPage.nextCursor
                        val seenCursors = mutableSetOf<String>()
                        var pagesLoaded = 1
                        while (hasMore && !nextCursor.isNullOrBlank() && pagesLoaded < maxPages && seenCursors.add(nextCursor)) {
                            val page = try {
                                repository.directEntryService.listWorkers(
                                    scope = scopeName,
                                    cursor = nextCursor,
                                    pageSize = 100
                                )
                            } catch (err: Exception) {
                                err.rethrowIfCancelled()
                                break
                            }
                            page.items.forEach { deduped.putIfAbsent(it.entryId, it) }
                            hasMore = page.hasMore
                            nextCursor = page.nextCursor
                            pagesLoaded += 1
                        }
                        PaginatedScopeWorkers(
                            items = deduped.values.toList(),
                            summary = firstPage.summary,
                            authorizationDate = firstPage.authorizationDate
                        )
                    } catch (err: BackendApiException) {
                        if (err.kind != BackendFailureKind.FORBIDDEN) {
                            if (firstError == null) firstError = err
                        }
                        null
                    } catch (err: Exception) {
                        err.rethrowIfCancelled()
                        if (firstError == null) firstError = err
                        null
                    }
                }

                val teamPage = if (isTeamActor) fetchAllScopePagesOrNull("team") else null
                val managedPage = fetchAllScopePagesOrNull("managed")
                val recruitedPage = fetchAllScopePagesOrNull("recruited")

                if (teamPage == null && managedPage == null && recruitedPage == null && firstError != null) {
                    throw firstError!!
                }

                val (presetFrom, presetTo) = WorkReportsContract.resolveDatePresetRange(_dashboardDatePreset.value)
                val hasPresetFilter = !presetFrom.isNullOrBlank() || !presetTo.isNullOrBlank()

                val mergedByEntry = linkedMapOf<String, WorkerProfileEntity>()
                teamPage?.items?.forEach { mergedByEntry.putIfAbsent(it.entryId, it) }
                managedPage?.items?.forEach { mergedByEntry.putIfAbsent(it.entryId, it) }
                recruitedPage?.items?.forEach { mergedByEntry.putIfAbsent(it.entryId, it) }
                val rows = filterWorkersByDateRange(mergedByEntry.values.toList(), presetFrom, presetTo)

                val workingCount = rows.count { it.employmentStatus.equals("ON", ignoreCase = true) }
                val waitingCount = rows.count { it.employmentStatus.equals("UNCONFIRMED", ignoreCase = true) }
                val leftCount = rows.count { it.employmentStatus.equals("OFF", ignoreCase = true) }

                val totalProfiles = if (hasPresetFilter) {
                    rows.size
                } else {
                    maxOf(
                        rows.size,
                        teamPage?.summary?.totalWorkers ?: 0,
                        managedPage?.summary?.totalWorkers ?: 0,
                        recruitedPage?.summary?.totalWorkers ?: 0
                    )
                }

                val workingRatePct = if (rows.isNotEmpty()) {
                    (((workingCount.toDouble() / rows.size.toDouble()) * 1000.0).roundToInt()) / 10f
                } else {
                    null
                }

                val denominator = rows.size.coerceAtLeast(1)
                val byProject = if (rows.isNotEmpty()) {
                    WorkReportsContract.buildBucketsFromKeyLabels(
                        pairs = rows.map {
                            it.projectId.ifBlank { "unknown" } to it.projectDisplay.ifBlank { "Chưa rõ dự án" }
                        },
                        denominator = denominator
                    ).take(10)
                } else {
                    emptyList()
                }
                val leadingProject = byProject.firstOrNull { it.key != "unknown" }
                    ?: byProject.firstOrNull()

                val byRecruiter = if (rows.isNotEmpty()) {
                    WorkReportsContract.buildBucketsFromKeyLabels(
                        pairs = rows.map {
                            it.recruiterId.ifBlank { "unknown" } to it.recruiterDisplay.ifBlank { "Chưa rõ người tuyển" }
                        },
                        denominator = denominator
                    ).take(10)
                } else {
                    emptyList()
                }

                val byLaborType = if (rows.isNotEmpty()) {
                    WorkReportsContract.buildBucketsFromKeyLabels(
                        pairs = rows.map {
                            it.laborType.ifBlank { "unknown" } to it.laborTypeDisplay.ifBlank { "Loại hình khác" }
                        },
                        denominator = denominator
                    ).take(10)
                } else {
                    emptyList()
                }

                val byProvider = if (rows.isNotEmpty() && recruiterMap.isNotEmpty()) {
                    WorkReportsContract.buildBucketsFromKeyLabels(
                        pairs = rows.map { row ->
                            when (recruiterMap[row.recruiterId]?.providerType?.lowercase(Locale.ROOT)) {
                                "hrp" -> "hrp" to "HRP"
                                "vendor" -> "vendor" to "Vendor"
                                else -> "unknown" to "Chưa phân loại"
                            }
                        },
                        denominator = denominator
                    )
                } else {
                    emptyList()
                }

                val byDateCounts = rows
                    .filter { it.firstWorkDate.isNotBlank() }
                    .groupingBy { it.firstWorkDate.trim() }
                    .eachCount()
                val hiringSummary = WorkReportsContract.computeHiringDaySummary(byDateCounts, rows.size)

                val distinctProjectsInRows = rows.map { it.projectId }.filter { it.isNotBlank() }.distinct().size
                val projectCount = if (hasPresetFilter) {
                    distinctProjectsInRows
                } else {
                    maxOf(
                        distinctProjectsInRows,
                        teamPage?.summary?.projectCount ?: 0,
                        managedPage?.summary?.projectCount ?: 0,
                        recruitedPage?.summary?.projectCount ?: 0
                    )
                }
                val distinctRecruitersInRows = rows.map { it.recruiterId }.filter { it.isNotBlank() }.distinct().size
                val recruiterCount = if (hasPresetFilter) {
                    distinctRecruitersInRows
                } else {
                    maxOf(
                        distinctRecruitersInRows,
                        teamPage?.summary?.recruiterCount ?: 0,
                        managedPage?.summary?.recruiterCount ?: 0,
                        recruitedPage?.summary?.recruiterCount ?: 0
                    )
                }
                val asOfDate = teamPage?.authorizationDate?.ifBlank { null }
                    ?: managedPage?.authorizationDate?.ifBlank { null }
                    ?: recruitedPage?.authorizationDate?.ifBlank { null }
                    ?: DirectEntryContract.todayIsoInHcm()

                val teamGrants = actor.scopes
                    .filter { it.kind == "team" && !it.reference.isNullOrBlank() }
                    .mapNotNull { it.reference?.trim() }
                    .distinct()
                val grantedTeamNames = teamGrants.mapNotNull { ref ->
                    recruiterMap.values
                        .firstOrNull { it.teamId == ref && !it.teamDisplayName.isNullOrBlank() }
                        ?.teamDisplayName
                        ?.trim()
                }.distinct()

                val scopeDescription = if (isTeamActor) {
                    when {
                        grantedTeamNames.size == 1 && (managedPage?.summary?.totalWorkers ?: 0) == 0 ->
                            grantedTeamNames.first()
                        grantedTeamNames.isNotEmpty() ->
                            "${grantedTeamNames.joinToString(", ")} & dự án phụ trách"
                        else -> "Nhóm & dự án phụ trách"
                    }
                } else {
                    "Cá nhân & dự án phụ trách"
                }

                val filteredManagedTotal = managedPage?.let {
                    if (hasPresetFilter) filterWorkersByDateRange(it.items, presetFrom, presetTo).size else it.summary.totalWorkers
                }
                val filteredRecruitedTotal = recruitedPage?.let {
                    if (hasPresetFilter) filterWorkersByDateRange(it.items, presetFrom, presetTo).size else it.summary.totalWorkers
                }
                val filteredTeamTotal = teamPage?.let {
                    if (hasPresetFilter) filterWorkersByDateRange(it.items, presetFrom, presetTo).size else it.summary.totalWorkers
                }

                _generalOverview.value = GeneralOverviewSnapshot(
                    asOfDate = asOfDate,
                    scopeDescription = scopeDescription,
                    totalProfiles = totalProfiles,
                    workingCount = workingCount,
                    waitingCount = waitingCount,
                    leftCount = leftCount,
                    audienceKind = if (isTeamActor) "team" else "own",
                    isPeriodFiltered = hasPresetFilter,
                    workingRatePct = workingRatePct,
                    placementRatePct = null,
                    projectCount = projectCount,
                    recruiterCount = recruiterCount,
                    managedTotal = filteredManagedTotal,
                    recruitedTotal = filteredRecruitedTotal,
                    teamTotal = filteredTeamTotal,
                    activeHiringDays = hiringSummary.activeHiringDays,
                    avgPerActiveDay = hiringSummary.avgPerActiveDay,
                    peakDailyCount = hiringSummary.peakDailyCount,
                    peakDateIso = hiringSummary.peakDateIso,
                    leadingProject = leadingProject,
                    byRecruiter = byRecruiter,
                    byProject = byProject,
                    bySecondaryGroup = byLaborType,
                    secondaryGroupTitle = "Cơ cấu theo loại hình lao động",
                    byProvider = byProvider,
                    periodFromDate = if (hasPresetFilter) presetFrom else null,
                    periodToDate = if (hasPresetFilter) presetTo else null,
                    periodNewEpisodes = if (hasPresetFilter) rows.size else null,
                    periodOnInflow = if (hasPresetFilter) workingCount else null,
                    periodOffInflow = if (hasPresetFilter) leftCount else null
                )

                if (isTeamActor) {
                    cachedTeamScopeWorkers = teamPage?.items.orEmpty()
                    cachedTeamTotalSummary = teamPage?.summary?.totalWorkers ?: cachedTeamScopeWorkers.size

                    when {
                        teamGrants.isEmpty() -> {
                            _teamScopeOptions.value = emptyList()
                            val filteredTeamRows = filterWorkersByDateRange(cachedTeamScopeWorkers, presetFrom, presetTo)
                            _teamReport.value = WorkReportsContract.buildTeamReportFromWorkerRows(
                                bucketKey = "grant:team",
                                bucketDisplay = "Nhóm của tôi",
                                rows = filteredTeamRows,
                                recruiterCatalogById = recruiterMap,
                                totalWorkersOverride = if (hasPresetFilter) null else cachedTeamTotalSummary
                            )
                        }

                        teamGrants.all { teamGrantUuidRegex.matches(it) } -> {
                            val grantSnapshots = teamGrants.map { ref ->
                                buildGrantedTeamSnapshotFromCache(actor, ref, presetFrom, presetTo)
                            }
                            _teamScopeOptions.value = if (grantSnapshots.size > 1) {
                                grantSnapshots.map { snap ->
                                    BiBucketItem(
                                        key = snap.bucketKey,
                                        display = snap.bucketDisplay,
                                        count = snap.totalRecruited,
                                        sharePct = 100f
                                    )
                                }
                            } else {
                                emptyList()
                            }
                            val existingKey = _teamReport.value?.bucketKey
                            _teamReport.value = grantSnapshots.firstOrNull { it.bucketKey == existingKey }
                                ?: grantSnapshots.first()
                        }

                        else -> {
                            val selectedGrant = teamGrants.first()
                            _teamScopeOptions.value = if (teamGrants.size > 1) {
                                teamGrants.map { ref ->
                                    BiBucketItem(
                                        key = ref,
                                        display = ref,
                                        count = 0,
                                        sharePct = 0f
                                    )
                                }
                            } else {
                                emptyList()
                            }
                            try {
                                _teamReport.value = repository.workReportsAndReportingService.fetchTeamReport(
                                    bucketKeyOrTeamCode = selectedGrant,
                                    fromDate = presetFrom,
                                    toDate = presetTo
                                )
                            } catch (teamErr: Exception) {
                                teamErr.rethrowIfCancelled()
                                if (teamPage != null) {
                                    _teamReport.value = buildGrantedTeamSnapshotFromCache(
                                        actor = actor,
                                        teamRef = selectedGrant,
                                        fromDate = presetFrom,
                                        toDate = presetTo
                                    )
                                } else {
                                    _teamReport.value = null
                                    _teamReportError.value = (teamErr as? BackendApiException)?.message
                                        ?: teamErr.message
                                        ?: "Không thể tải báo cáo theo nhóm."
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải báo cáo tổng quát từ máy chủ."
                _generalOverviewError.value = msg
                if (isTeamActor && _teamReport.value == null) {
                    _teamReportError.value = msg
                }
            } finally {
                _generalOverviewLoading.value = false
                if (isTeamActor) {
                    _teamReportLoading.value = false
                }
            }
        }
    }

    fun loadMoreWorkforceProjects() {
        val current = _workforceDeep.value ?: return
        val cursor = current.projectNextCursor ?: return
        if (!current.projectHasMore || _workforceLoading.value) return
        val (presetFrom, presetTo) = WorkReportsContract.resolveDatePresetRange(_dashboardDatePreset.value)
        viewModelScope.launch {
            _workforceLoading.value = true
            try {
                val (moreRows, hasMore, nextCursor) = repository.workReportsAndReportingService
                    .fetchWorkforceProjectPage(
                        cursor = cursor,
                        eligibleTotal = current.eligibleEpisodeTotal,
                        fromDate = presetFrom,
                        toDate = presetTo
                    )
                _workforceDeep.value = current.copy(
                    byProject = current.byProject + moreRows,
                    projectHasMore = hasMore,
                    projectNextCursor = nextCursor
                )
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải thêm danh sách dự án.",
                    isError = true
                )
            } finally {
                _workforceLoading.value = false
            }
        }
    }

    fun loadTeamReport(
        bucketKeyOrTeamCode: String,
        fromDate: String? = null,
        toDate: String? = null
    ) {
        val actor = currentActor.value ?: return
        if (!actor.hasScopeKind("all") && !actor.hasScopeKind("team")) return
        val trimmedKey = bucketKeyOrTeamCode.trim()
        if (trimmedKey.isEmpty()) return

        val (effectiveFrom, effectiveTo) = if (fromDate != null || toDate != null) {
            fromDate to toDate
        } else {
            WorkReportsContract.resolveDatePresetRange(_dashboardDatePreset.value)
        }
        val hasPresetFilter = !effectiveFrom.isNullOrBlank() || !effectiveTo.isNullOrBlank()

        if (trimmedKey.startsWith("grant:")) {
            val teamRef = trimmedKey.removePrefix("grant:").trim()
            val allowed = actor.scopes.any { scope ->
                scope.kind == "team" && (
                    scope.reference?.trim() == teamRef ||
                        (teamRef == "team" && scope.reference.isNullOrBlank())
                    )
            }
            if (!allowed) {
                _teamReportError.value = "Bạn không có quyền xem báo cáo của nhóm này."
                return
            }
            _teamReportError.value = null
            _teamReport.value = if (teamRef == "team") {
                val recruiterMap = _catalog.value?.recruiters.orEmpty().associateBy { it.recruiterId }
                val filteredRows = filterWorkersByDateRange(cachedTeamScopeWorkers, effectiveFrom, effectiveTo)
                WorkReportsContract.buildTeamReportFromWorkerRows(
                    bucketKey = "grant:team",
                    bucketDisplay = "Nhóm của tôi",
                    rows = filteredRows,
                    recruiterCatalogById = recruiterMap,
                    totalWorkersOverride = if (hasPresetFilter) null else cachedTeamTotalSummary
                )
            } else {
                buildGrantedTeamSnapshotFromCache(actor, teamRef, effectiveFrom, effectiveTo)
            }
            return
        }

        if (!actor.hasScopeKind("all")) {
            val grantedRefs = actor.scopes
                .filter { it.kind == "team" && !it.reference.isNullOrBlank() }
                .mapNotNull { it.reference?.trim() }
            val matchedUuidGrant = grantedRefs.firstOrNull { ref ->
                teamGrantUuidRegex.matches(ref) && (
                    ref == trimmedKey ||
                        runCatching { WorkReportsContract.encodeTeamBucketKey(ref) == trimmedKey }.getOrDefault(false)
                    )
            }
            if (matchedUuidGrant != null) {
                _teamReportError.value = null
                _teamReport.value = buildGrantedTeamSnapshotFromCache(actor, matchedUuidGrant, effectiveFrom, effectiveTo)
                return
            }
            val matchesGrantedCode = grantedRefs.isEmpty() || grantedRefs.any { ref ->
                ref == trimmedKey ||
                    runCatching { WorkReportsContract.encodeTeamBucketKey(ref) == trimmedKey }.getOrDefault(false)
            }
            if (!matchesGrantedCode) {
                _teamReport.value = null
                _teamReportError.value = "Bạn chỉ được xem báo cáo của nhóm mình quản lý."
                return
            }
        }

        val decodedRequested = WorkReportsContract.decodeTeamBucketCode(trimmedKey)
        val matchedBucketHint = _teamScopeOptions.value.firstOrNull { b ->
            b.key == trimmedKey ||
                WorkReportsContract.decodeTeamBucketCode(b.key).equals(decodedRequested, ignoreCase = true) ||
                runCatching { WorkReportsContract.encodeTeamBucketKey(b.key) == trimmedKey }.getOrDefault(false)
        }

        _teamReportLoading.value = true
        _teamReportError.value = null
        viewModelScope.launch {
            try {
                if (cachedAdminTeamCodeMap == null &&
                    actor.hasScopeKind("all") &&
                    actor.hasCapability("catalog_master_manage")
                ) {
                    cachedAdminTeamCodeMap = repository.workReportsAndReportingService.fetchAdminTeamCodeMap()
                }
                val snap = repository.workReportsAndReportingService.fetchTeamReport(
                    bucketKeyOrTeamCode = trimmedKey,
                    fromDate = effectiveFrom,
                    toDate = effectiveTo,
                    knownTeamCodeMap = cachedAdminTeamCodeMap.orEmpty(),
                    knownBucketHint = matchedBucketHint
                )
                val resolvedCanonicalCode = WorkReportsContract.decodeTeamBucketCode(snap.bucketKey)
                if (resolvedCanonicalCode.isNotBlank() && resolvedCanonicalCode != "unknown") {
                    val updatedMap = LinkedHashMap(cachedAdminTeamCodeMap.orEmpty())
                    updatedMap[resolvedCanonicalCode.lowercase(Locale.ROOT)] = resolvedCanonicalCode
                    cachedAdminTeamCodeMap = updatedMap
                }
                _teamReport.value = snap
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _teamReport.value = null
                _teamReportError.value = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải báo cáo theo nhóm."
            } finally {
                _teamReportLoading.value = false
            }
        }
    }

    fun loadMoreTeamDrillDown() {
        val current = _teamReport.value ?: return
        val cursor = current.nextCursor ?: return
        if (_teamReportLoading.value) return
        val (presetFrom, presetTo) = WorkReportsContract.resolveDatePresetRange(_dashboardDatePreset.value)
        _teamReportLoading.value = true
        viewModelScope.launch {
            try {
                val next = repository.workReportsAndReportingService.fetchTeamDrillDownPage(
                    bucketKeyOrTeamCode = current.bucketKey,
                    cursor = cursor,
                    fromDate = presetFrom,
                    toDate = presetTo
                )
                _teamReport.value = current.copy(
                    rows = current.rows + next.rows,
                    nextCursor = next.nextCursor
                )
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải thêm chi tiết nhóm.",
                    isError = true
                )
            } finally {
                _teamReportLoading.value = false
            }
        }
    }

    fun refreshDuplicateCccdReport(
        page: Int = _duplicatePage.value,
        managedOnly: Boolean = _duplicateManagedOnly.value
    ) {
        _duplicatePage.value = page.coerceIn(1, 1000)
        _duplicateManagedOnly.value = managedOnly
        _duplicateLoading.value = true
        _duplicateError.value = null
        viewModelScope.launch {
            try {
                val report = repository.workReportsAndReportingService.fetchDuplicateCccdReport(
                    page = _duplicatePage.value,
                    pageSize = 25,
                    managedOnly = managedOnly
                )
                _duplicateGroups.value = report.groups
                _duplicatePage.value = report.page
                _duplicateHasMore.value = report.hasMore
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _duplicateGroups.value = emptyList()
                _duplicateHasMore.value = false
                _duplicateError.value = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải danh sách cảnh báo CCCD trùng."
            } finally {
                _duplicateLoading.value = false
            }
        }
    }

    fun selfWithdrawDuplicateEpisode(
        entryId: String,
        expectedVersion: Int,
        reason: String? = null
    ) {
        if (_duplicateLoading.value) return
        viewModelScope.launch {
            try {
                repository.workReportsAndReportingService.selfWithdrawDuplicateCccd(
                    entryId = entryId,
                    expectedEntryVersion = expectedVersion,
                    reason = reason
                )
                showBanner("Đã rút hồ sơ trùng CCCD thành công.")
                refreshDuplicateCccdReport(page = _duplicatePage.value, managedOnly = _duplicateManagedOnly.value)
            } catch (e: BackendApiException) {
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT || e.kind == BackendFailureKind.NOT_FOUND) {
                    refreshDuplicateCccdReport(page = _duplicatePage.value, managedOnly = _duplicateManagedOnly.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(e.message ?: "Không thể rút hồ sơ trùng CCCD.", isError = true)
            }
        }
    }

    fun clearCccdReviewMutationError() {
        _cccdReviewMutationError.value = null
    }

    fun refreshCccdReviewQueue(
        statusFilter: String = _cccdReviewStatusFilter.value,
        offset: Int = _cccdReviewOffset.value,
        limit: Int = 25
    ) {
        val safeStatus = statusFilter.trim().uppercase(Locale.ROOT).ifEmpty {
            CccdReviewStatusFilter.NEEDS_REVIEW.code
        }
        val safeOffset = offset.coerceAtLeast(0)
        _cccdReviewStatusFilter.value = safeStatus
        _cccdReviewOffset.value = safeOffset
        _cccdReviewLoading.value = true
        _cccdReviewError.value = null
        viewModelScope.launch {
            try {
                val page = repository.directEntryService.fetchCccdReviewQueue(
                    statusFilter = safeStatus,
                    limit = limit,
                    offset = safeOffset
                )
                _cccdReviewPage.value = page
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _cccdReviewError.value = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải danh sách chuẩn hóa thông tin NLĐ."
            } finally {
                _cccdReviewLoading.value = false
            }
        }
    }

    fun resolveCccdReviewUpdateEnteredCccd(
        entryId: String,
        expectedEntryVersion: Int,
        nationalId: String,
        applyBlankFieldsFromQr: Boolean,
        reason: String?,
        onResolved: () -> Unit = {}
    ) {
        if (_cccdReviewMutating.value) return
        _cccdReviewMutating.value = true
        _cccdReviewMutationError.value = null
        viewModelScope.launch {
            try {
                val res = repository.directEntryService.resolveCccdReviewUpdateEnteredCccd(
                    entryId = entryId,
                    expectedEntryVersion = expectedEntryVersion,
                    nationalId = nationalId,
                    applyBlankFieldsFromQr = applyBlankFieldsFromQr,
                    reason = reason
                )
                showBanner(
                    "Đã cập nhật số CCCD và đối chiếu lại thành công (${res.updatedFields.size} trường cập nhật)."
                )
                refreshCccdReviewQueue()
                onResolved()
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể cập nhật số CCCD."
                _cccdReviewMutationError.value = msg
                showBanner(msg, isError = true)
                if ((e as? BackendApiException)?.kind == BackendFailureKind.CONFLICT) {
                    refreshCccdReviewQueue()
                }
            } finally {
                _cccdReviewMutating.value = false
            }
        }
    }

    fun resolveCccdReviewStandardizeFromQr(
        entryId: String,
        expectedEntryVersion: Int,
        fields: List<String>,
        reason: String?,
        onResolved: () -> Unit = {}
    ) {
        if (_cccdReviewMutating.value) return
        _cccdReviewMutating.value = true
        _cccdReviewMutationError.value = null
        viewModelScope.launch {
            try {
                val res = repository.directEntryService.resolveCccdReviewStandardizeFromQr(
                    entryId = entryId,
                    expectedEntryVersion = expectedEntryVersion,
                    fields = fields,
                    reason = reason
                )
                showBanner(
                    "Đã chuẩn hóa ${res.updatedFields.size} trường thông tin từ QR thành công."
                )
                refreshCccdReviewQueue()
                onResolved()
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể chuẩn hóa thông tin từ QR."
                _cccdReviewMutationError.value = msg
                showBanner(msg, isError = true)
                if ((e as? BackendApiException)?.kind == BackendFailureKind.CONFLICT) {
                    refreshCccdReviewQueue()
                }
            } finally {
                _cccdReviewMutating.value = false
            }
        }
    }

    fun retryCccdQrExtraction(
        entryId: String,
        expectedEntryVersion: Int,
        onCompleted: () -> Unit = {}
    ) {
        if (_cccdReviewMutating.value) return
        _cccdReviewMutating.value = true
        _cccdReviewMutationError.value = null
        viewModelScope.launch {
            try {
                val res = repository.directEntryService.retryCccdQrExtraction(
                    entryId = entryId,
                    expectedEntryVersion = expectedEntryVersion,
                    applyBlankFields = true
                )
                val statusLabel = when (res.reconciliationStatus) {
                    "MATCHED_INITIAL" -> "Khớp ban đầu"
                    "MISMATCH" -> "Lệch CCCD"
                    "QR_UNREADABLE" -> "Không đọc được QR"
                    "RESOLVED" -> "Đã xử lý"
                    else -> res.reconciliationStatus
                }
                showBanner("Đã đọc lại QR & đối chiếu lại ($statusLabel).")
                refreshCccdReviewQueue()
                onCompleted()
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể đọc lại QR trên hồ sơ này."
                _cccdReviewMutationError.value = msg
                showBanner(msg, isError = true)
                if ((e as? BackendApiException)?.kind == BackendFailureKind.CONFLICT) {
                    refreshCccdReviewQueue()
                }
            } finally {
                _cccdReviewMutating.value = false
            }
        }
    }

    fun reuploadCccdDocumentInReview(
        entryId: String,
        documentType: String,
        uri: Uri,
        initialEntryVersion: Int,
        onCompleted: () -> Unit = {}
    ) {
        if (_cccdReviewMutating.value) return
        _cccdReviewMutating.value = true
        _cccdReviewMutationError.value = null
        viewModelScope.launch {
            try {
                val docLabel = if (documentType == "CCCD_FRONT") "CCCD mặt trước" else "CCCD mặt sau"
                repository.directEntryService.reuploadCccdDocumentAndRetryQr(
                    entryId = entryId,
                    documentType = documentType,
                    uri = uri,
                    initialEntryVersion = initialEntryVersion
                )
                showBanner("Đã tải lại $docLabel và tự động giải mã lại QR.")
                refreshCccdReviewQueue()
                onCompleted()
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải lại ảnh CCCD."
                _cccdReviewMutationError.value = msg
                showBanner(msg, isError = true)
                if ((e as? BackendApiException)?.kind == BackendFailureKind.CONFLICT) {
                    refreshCccdReviewQueue()
                }
            } finally {
                _cccdReviewMutating.value = false
            }
        }
    }

    // =========================================================================
    // Work Reports Actions (Obligations, Draft/Submit, Reviews, Analytics)
    // =========================================================================

    fun refreshWorkReportsData(actor: AppActor? = currentActor.value) {
        val resolvedActor = actor ?: return
        if (_workReportsLoading.value) return
        _workReportsLoading.value = true
        _workReportsError.value = null
        viewModelScope.launch {
            try {
                var loadedObligations: List<WorkReportObligationEntity> = emptyList()
                try {
                    loadedObligations = repository.workReportsAndReportingService.fetchMyObligations()
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    _workReportsError.value = (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Không thể tải danh sách báo cáo từ máy chủ."
                }

                val canReview = resolvedActor.hasCapability("report_review") &&
                    (resolvedActor.hasScopeKind("team") || resolvedActor.hasScopeKind("all"))
                val loadedReviews = if (canReview) {
                    runCatching { repository.workReportsAndReportingService.fetchPendingReviews() }
                        .onFailure { err ->
                            err.rethrowIfCancelled()
                            if (_workReportsError.value == null) {
                                _workReportsError.value = (err as? BackendApiException)?.message
                            }
                        }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                _reviewQueue.value = loadedReviews

                val canAnalytics = resolvedActor.hasCapability("report_analytics_view") &&
                    (resolvedActor.hasScopeKind("team") || resolvedActor.hasScopeKind("all"))
                if (canAnalytics) {
                    val templateMap = linkedMapOf<String, String>()
                    loadedObligations.forEach { ob ->
                        if (ob.templateId.isNotBlank() && ob.templateName.isNotBlank()) {
                            templateMap[ob.templateId] = ob.templateName
                        }
                    }
                    loadedReviews.forEach { rv ->
                        if (rv.templateId.isNotBlank() && rv.templateName.isNotBlank()) {
                            templateMap.putIfAbsent(rv.templateId, rv.templateName)
                        }
                    }
                    val serverTemplates = runCatching {
                        repository.workReportsAndReportingService.fetchSelectableTemplates(resolvedActor)
                    }.onFailure { it.rethrowIfCancelled() }.getOrDefault(emptyList())
                    serverTemplates.forEach { opt ->
                        if (opt.templateId.isNotBlank() && opt.displayName.isNotBlank()) {
                            templateMap.putIfAbsent(opt.templateId, opt.displayName)
                        }
                    }
                    _availableReportTemplates.value.forEach { prev ->
                        if (prev.templateId.isNotBlank() && prev.displayName.isNotBlank()) {
                            templateMap.putIfAbsent(prev.templateId, prev.displayName)
                        }
                    }
                    _analyticsSnapshot.value?.let { snap ->
                        if (snap.templateId.isNotBlank() && snap.templateName.isNotBlank()) {
                            templateMap.putIfAbsent(snap.templateId, snap.templateName)
                        }
                    }
                    val mergedTemplates = templateMap.map { (tid, tname) ->
                        WorkReportTemplateOption(templateId = tid, displayName = tname)
                    }
                    _availableReportTemplates.value = mergedTemplates

                    val existingSnap = _analyticsSnapshot.value
                    val targetTemplateId = existingSnap?.templateId?.takeIf { it.isNotBlank() }
                        ?: mergedTemplates.firstOrNull()?.templateId
                    if (!targetTemplateId.isNullOrBlank()) {
                        val resolvedNameHint = templateMap[targetTemplateId]
                            ?: existingSnap?.templateName.orEmpty()
                        runCatching {
                            repository.workReportsAndReportingService.fetchAnalyticsSnapshot(
                                templateId = targetTemplateId,
                                templateNameHint = resolvedNameHint,
                                groupBy = existingSnap?.groupBy ?: "team",
                                finality = existingSnap?.finality ?: "final"
                            )
                        }.onFailure { it.rethrowIfCancelled() }.onSuccess { snap ->
                            _analyticsSnapshot.value = snap
                        }
                    }
                } else {
                    _analyticsSnapshot.value = null
                    _availableReportTemplates.value = emptyList()
                }
            } finally {
                _workReportsLoading.value = false
            }
        }
    }

    fun openObligationEditor(item: WorkReportObligationEntity) {
        _editingObligation.value = item
        _workReportsError.value = null
        viewModelScope.launch {
            try {
                val detailed = repository.workReportsAndReportingService.fetchObligationDetail(
                    obligationId = item.obligationId,
                    fallback = item
                )
                if (_editingObligation.value?.obligationId == item.obligationId) {
                    _editingObligation.value = detailed
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải nội dung chi tiết báo cáo.",
                    isError = true
                )
            }
        }
    }

    fun closeObligationEditor() {
        _editingObligation.value = null
    }

    private suspend fun handleWorkReportDeadlinePassed(obligation: WorkReportObligationEntity) {
        val currentOpen = _editingObligation.value?.takeIf { it.obligationId == obligation.obligationId } ?: obligation
        val lockedFallback = currentOpen.copy(isOverdue = true)
        _editingObligation.value = lockedFallback
        repository.dao.upsertWorkReportCache(lockedFallback)

        val refreshedList = runCatching {
            repository.workReportsAndReportingService.fetchMyObligations(status = null)
        }.onFailure { it.rethrowIfCancelled() }.getOrNull()

        val fromList = refreshedList?.firstOrNull { it.obligationId == obligation.obligationId }
        val resolvedPayload = lockedFallback.payloadJsonRaw.takeIf { it.isNotBlank() && it != "{}" }
            ?: fromList?.payloadJsonRaw
            ?: lockedFallback.payloadJsonRaw
        val finalLocked = (fromList ?: lockedFallback).copy(
            isOverdue = true,
            payloadJsonRaw = resolvedPayload
        )
        repository.dao.upsertWorkReportCache(finalLocked)
        if (_editingObligation.value?.obligationId == obligation.obligationId) {
            _editingObligation.value = finalLocked
        }
    }

    fun saveWorkReportDraft(obligation: WorkReportObligationEntity, payloadJson: JSONObject) {
        if (_workReportMutationBusy.value) return
        _workReportMutationBusy.value = true
        viewModelScope.launch {
            try {
                repository.workReportsAndReportingService.saveDraft(
                    obligationId = obligation.obligationId,
                    expectedVersion = obligation.submissionVersion,
                    payloadJson = payloadJson,
                    fallback = obligation
                )
                _editingObligation.value = null
                showBanner("Đã lưu bản nháp báo cáo.")
            } catch (e: BackendApiException) {
                showBanner(e.message, isError = true)
                if (e.backendCode == "WORK_REPORT_DEADLINE_PASSED") {
                    handleWorkReportDeadlinePassed(obligation)
                } else if (e.kind == BackendFailureKind.CONFLICT) {
                    val refreshed = runCatching {
                        repository.workReportsAndReportingService.fetchObligationDetail(
                            obligationId = obligation.obligationId,
                            fallback = obligation
                        )
                    }.onFailure { it.rethrowIfCancelled() }.getOrNull()
                    if (refreshed != null) {
                        _editingObligation.value = refreshed
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(e.message ?: "Không thể lưu bản nháp báo cáo.", isError = true)
            } finally {
                _workReportMutationBusy.value = false
            }
        }
    }

    fun submitWorkReportFinal(obligation: WorkReportObligationEntity, payloadJson: JSONObject) {
        if (_workReportMutationBusy.value) return
        _workReportMutationBusy.value = true
        viewModelScope.launch {
            try {
                val updated = repository.workReportsAndReportingService.submitReport(
                    obligationId = obligation.obligationId,
                    expectedVersion = obligation.submissionVersion,
                    payloadJson = payloadJson,
                    fallback = obligation
                )
                _editingObligation.value = null
                val vnState = WorkReportsContract.submissionStateDisplayLabel(updated.submissionState)
                showBanner("Đã nộp báo cáo thành công ($vnState).")
            } catch (e: BackendApiException) {
                showBanner(e.message, isError = true)
                if (e.backendCode == "WORK_REPORT_DEADLINE_PASSED") {
                    handleWorkReportDeadlinePassed(obligation)
                } else if (e.kind == BackendFailureKind.CONFLICT) {
                    val refreshed = runCatching {
                        repository.workReportsAndReportingService.fetchObligationDetail(
                            obligationId = obligation.obligationId,
                            fallback = obligation
                        )
                    }.onFailure { it.rethrowIfCancelled() }.getOrNull()
                    if (refreshed != null) {
                        _editingObligation.value = refreshed
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(e.message ?: "Không thể nộp báo cáo.", isError = true)
            } finally {
                _workReportMutationBusy.value = false
            }
        }
    }

    fun decideWorkReportReview(
        item: WorkReportReviewItem,
        approve: Boolean,
        reason: String
    ) {
        val actor = currentActor.value ?: return
        if (!actor.hasCapability("report_review")) return
        if (_workReportMutationBusy.value) return
        _workReportMutationBusy.value = true
        viewModelScope.launch {
            try {
                repository.workReportsAndReportingService.decideReview(
                    obligationId = item.obligationId,
                    approve = approve,
                    reason = reason,
                    expectedVersion = item.submissionVersion
                )
                showBanner(
                    if (approve) "Đã phê duyệt báo cáo của ${item.assigneeDisplayName}."
                    else "Đã gửi yêu cầu chỉnh sửa báo cáo của ${item.assigneeDisplayName}."
                )
                refreshWorkReportsData(actor)
            } catch (e: BackendApiException) {
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT) {
                    refreshWorkReportsData(actor)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(e.message ?: "Không thể thực hiện phê duyệt báo cáo.", isError = true)
            } finally {
                _workReportMutationBusy.value = false
            }
        }
    }

    fun loadWorkReportAnalytics(
        templateId: String,
        templateNameHint: String = "",
        groupBy: String = _analyticsSnapshot.value?.groupBy ?: "team",
        finality: String = _analyticsSnapshot.value?.finality ?: "final"
    ) {
        val actor = currentActor.value ?: return
        if (!actor.hasCapability("report_analytics_view")) return
        val resolvedName = templateNameHint.ifBlank {
            _availableReportTemplates.value.firstOrNull { it.templateId == templateId }?.displayName
                ?: _analyticsSnapshot.value?.takeIf { it.templateId == templateId }?.templateName.orEmpty()
        }
        _workReportsLoading.value = true
        viewModelScope.launch {
            try {
                val snap = repository.workReportsAndReportingService.fetchAnalyticsSnapshot(
                    templateId = templateId,
                    templateNameHint = resolvedName,
                    groupBy = groupBy,
                    finality = finality
                )
                _analyticsSnapshot.value = snap
                _workReportsError.value = null
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải số liệu tổng hợp báo cáo.",
                    isError = true
                )
            } finally {
                _workReportsLoading.value = false
            }
        }
    }

    // =========================================================================
    // Direct Entry & Worker Operations Actions
    // =========================================================================

    fun loadCatalogIfNeeded(
        effectiveDateIso: String = DirectEntryContract.todayIsoInHcm(),
        force: Boolean = false
    ) {
        if (!force && (_catalogLoading.value || _catalog.value?.effectiveDate == effectiveDateIso)) {
            return
        }
        _catalogLoading.value = true
        _catalogError.value = null
        viewModelScope.launch {
            try {
                val loaded = repository.directEntryService.fetchCatalog(effectiveDateIso)
                _catalog.value = loaded
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _catalogError.value = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải danh mục từ máy chủ."
            } finally {
                _catalogLoading.value = false
            }
        }
    }

    fun refreshOwnSubmissionsForHistory() {
        viewModelScope.launch {
            try {
                val page = repository.directEntryService.listOwnSubmissions(pageSize = 20)
                _ownSubmissions.value = page.items
                _submissionsHasMore.value = page.hasMore
                _submissionsNextCursor.value = page.nextCursor
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                // Non-blocking history preview
            }
        }
    }

    fun uploadStageDocument(
        documentType: String,
        uri: Uri,
        onStaged: (StagedDocumentRef) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val staged = repository.directEntryService.uploadStageUri(documentType, uri)
                onStaged(staged)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                onError(
                    (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Tải hồ sơ lên máy chủ thất bại."
                )
            }
        }
    }

    fun prepareLocalStageDocument(
        documentType: String,
        uri: Uri,
        onPrepared: (StagedDocumentRef) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val prepared = repository.directEntryService.prepareLocalAttachmentUri(documentType, uri)
                onPrepared(prepared)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                onError(
                    (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Không thể chuẩn bị tệp đính kèm từ thiết bị."
                )
            }
        }
    }

    fun prepareLocalMultiImagePdfStageDocument(
        documentType: String,
        uris: List<Uri>,
        existingRef: StagedDocumentRef? = null,
        onPrepared: (StagedDocumentRef) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val prepared = repository.directEntryService.prepareLocalMultiImagePdfAttachment(
                    documentType = documentType,
                    uris = uris,
                    existingRef = existingRef
                )
                onPrepared(prepared)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                onError(
                    (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Không thể ghép các trang ảnh thành tệp PDF."
                )
            }
        }
    }

    fun deleteStageDocument(stageId: String) {
        if (stageId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.directEntryService.deleteStage(stageId)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                // Best-effort stage cleanup when user discards/replaces draft attachment
            }
        }
    }

    fun discardDraftAttachment(ref: StagedDocumentRef?) {
        if (ref == null) return
        if (ref.stageId.isNotBlank()) {
            deleteStageDocument(ref.stageId)
        }
        val localPath = ref.localFilePath?.trim().orEmpty()
        if (localPath.isNotEmpty() || ref.sourceImagePaths.isNotEmpty()) {
            viewModelScope.launch {
                repository.directEntryService.deleteLocalAttachmentRef(ref)
            }
        }
    }

    private fun buildPendingAttachmentTasks(
        validDrafts: List<NewWorkerEntryDraft>,
        entryIds: List<String>
    ): List<PendingEntryAttachmentTask> {
        val count = minOf(validDrafts.size, entryIds.size)
        val tasks = mutableListOf<PendingEntryAttachmentTask>()
        for (i in 0 until count) {
            val draft = validDrafts[i]
            val entryId = entryIds[i].trim()
            if (!DirectEntryContract.isUuid(entryId)) continue
            val fallbackLabel = draft.fullName.trim().ifBlank { "CCCD ${draft.cccd.trim()}" }
            for (ref in draft.localPendingAttachments()) {
                val localPath = ref.localFilePath?.trim().orEmpty()
                if (localPath.isNotEmpty()) {
                    tasks.add(
                        PendingEntryAttachmentTask(
                            entryId = entryId,
                            workerDisplayName = fallbackLabel,
                            documentType = ref.documentType,
                            fileName = ref.fileName,
                            mimeType = ref.mimeType,
                            localFilePath = localPath,
                            idempotencyBaseKey = UUID.randomUUID().toString(),
                            sourceImagePaths = ref.sourceImagePaths
                        )
                    )
                }
            }
        }
        return tasks
    }

    fun clearProfileSaveStatus() {
        _profileSaveStatusMessage.value = null
    }

    fun clearPhotoUploadStatus() {
        _photoUploadStatusMessage.value = null
    }

    internal fun enqueueBackgroundEntryUploads(tasks: List<PendingEntryAttachmentTask>) {
        if (tasks.isEmpty()) return
        _backgroundUploadActiveCount.value = _backgroundUploadActiveCount.value + tasks.size
        _photoUploadStatusMessage.value = "Đang tải ngầm ${tasks.size} ảnh & giấy tờ đính kèm…"
        viewModelScope.launch {
            backgroundUploadMutex.withLock {
                var succeededCount = 0
                val failedTasks = mutableListOf<PendingEntryAttachmentTask>()
                var lastErrorDetail: String? = null

                val grouped = tasks.groupBy { it.entryId }
                for ((entryId, entryTasks) in grouped) {
                    var knownVersion: Int? = 1
                    for (task in entryTasks) {
                        try {
                            val fileBytes = withContext(Dispatchers.IO) {
                                val f = File(task.localFilePath)
                                if (!f.exists() || f.length() <= 0L) {
                                    throw BackendApiException(
                                        kind = BackendFailureKind.INVALID_REQUEST,
                                        statusCode = 400,
                                        backendCode = "LOCAL_FILE_MISSING",
                                        message = "Không tìm thấy tệp ảnh tạm trên thiết bị."
                                    )
                                }
                                f.readBytes()
                            }
                            knownVersion = repository.directEntryService.uploadDocumentToEntry(
                                entryId = entryId,
                                initialEntryVersion = knownVersion,
                                documentType = task.documentType,
                                fileBytes = fileBytes,
                                mimeType = task.mimeType,
                                idempotencyBaseKey = task.idempotencyBaseKey
                            )
                            repository.directEntryService.deleteLocalAttachment(task.localFilePath)
                            task.sourceImagePaths.forEach { pagePath ->
                                repository.directEntryService.deleteLocalAttachment(pagePath)
                            }
                            succeededCount++
                        } catch (e: Exception) {
                            e.rethrowIfCancelled()
                            knownVersion = 1
                            failedTasks.add(
                                task.copy(idempotencyBaseKey = UUID.randomUUID().toString())
                            )
                            lastErrorDetail = (e as? BackendApiException)?.message ?: e.message
                        } finally {
                            _backgroundUploadActiveCount.value =
                                (_backgroundUploadActiveCount.value - 1).coerceAtLeast(0)
                        }
                    }
                    if (_selectedEntryDetail.value?.entryId == entryId && succeededCount > 0) {
                        runCatching { repository.directEntryService.getEntryDetail(entryId) }
                            .onFailure { it.rethrowIfCancelled() }
                            .onSuccess { refreshed -> _selectedEntryDetail.value = refreshed }
                    }
                }

                if (failedTasks.isEmpty()) {
                    _hasFailedBackgroundUploads.value = _failedBackgroundUploads.value.isNotEmpty()
                    val msg = "Đã tải ngầm thành công $succeededCount/${tasks.size} ảnh & giấy tờ đính kèm lên hồ sơ NLĐ."
                    _photoUploadStatusMessage.value = msg
                    showBanner(msg, isError = false)
                } else {
                    val merged = (_failedBackgroundUploads.value + failedTasks)
                        .distinctBy { "${it.entryId}:${it.documentType}:${it.localFilePath}" }
                    _failedBackgroundUploads.value = merged
                    _hasFailedBackgroundUploads.value = merged.isNotEmpty()
                    if (_profileSaveStatusMessage.value.isNullOrBlank()) {
                        _profileSaveStatusMessage.value = "Hồ sơ NLĐ đã được ghi nhận trên hệ thống."
                    }
                    val detailSuffix = lastErrorDetail?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    val msg = "Tải ảnh đính kèm: thành công $succeededCount/${tasks.size}, lỗi ${failedTasks.size} tệp$detailSuffix. Bấm Thử lại để gửi tiếp."
                    _photoUploadStatusMessage.value = msg
                    showBanner(msg, isError = true)
                }
            }
        }
    }

    fun retryFailedBackgroundUploads() {
        val pendingFailed = _failedBackgroundUploads.value
        if (pendingFailed.isEmpty()) return
        _failedBackgroundUploads.value = emptyList()
        _hasFailedBackgroundUploads.value = false
        val msg = "Đang tải lại ${pendingFailed.size} ảnh & giấy tờ đính kèm…"
        _photoUploadStatusMessage.value = msg
        showBanner(msg, isError = false)
        enqueueBackgroundEntryUploads(pendingFailed)
    }

    fun syncPendingOfflineProfiles(
        onDraftsSaved: () -> Unit = {},
        onSubmittedSuccess: () -> Unit = {}
    ) {
        val queued = _pendingOfflineProfileDrafts.value
        if (queued.isEmpty()) return
        submitFullProfileDrafts(
            validDrafts = queued,
            onDraftsSaved = onDraftsSaved,
            onSubmittedSuccess = onSubmittedSuccess
        )
    }

    fun submitFullProfileDrafts(
        validDrafts: List<NewWorkerEntryDraft>,
        onDraftsSaved: () -> Unit,
        onSubmittedSuccess: () -> Unit = {}
    ) {
        if (_workerMutationBusy.value) return
        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            try {
                val result = repository.directEntryService.submitFullProfileBatch(validDrafts)
                _pendingOfflineProfileDrafts.value = emptyList()
                val pendingAttachments = buildPendingAttachmentTasks(validDrafts, result.entryIds)
                onDraftsSaved()
                val codesNote = if (result.employeeCodes.isNotEmpty()) {
                    " Mã NLĐ: ${result.employeeCodes.joinToString(", ")}"
                } else {
                    ""
                }
                _profileSaveStatusMessage.value =
                    "Đã lưu ${result.entryIds.size} hồ sơ NLĐ trên hệ thống.$codesNote"
                if (result.state == "SUBMITTED") {
                    refreshOwnSubmissionsForHistory()
                    val profileMsg = "Đã đẩy chính thức ${result.entryIds.size} hồ sơ NLĐ lên hệ thống.$codesNote"
                    _profileSaveStatusMessage.value = profileMsg
                    if (pendingAttachments.isNotEmpty()) {
                        showBanner(
                            "$profileMsg Đang tải ngầm ${pendingAttachments.size} ảnh & giấy tờ…"
                        )
                        enqueueBackgroundEntryUploads(pendingAttachments)
                    } else {
                        _photoUploadStatusMessage.value = null
                        showBanner(profileMsg)
                    }
                    onSubmittedSuccess()
                    return@launch
                }
                transitionDraftSubmissionInternal(
                    submissionId = result.submissionId,
                    entryCount = result.entryIds.size,
                    employeeCodes = result.employeeCodes,
                    navigateToWorkersOnConfirm = true,
                    wasJustCreated = true,
                    pendingAttachments = pendingAttachments,
                    onSubmittedSuccess = onSubmittedSuccess
                )
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val apiEx = e as? BackendApiException
                if (apiEx != null &&
                    (apiEx.kind == BackendFailureKind.NETWORK || apiEx.kind == BackendFailureKind.SERVER_UNAVAILABLE)
                ) {
                    _pendingOfflineProfileDrafts.value = validDrafts
                    val hasLocalPhotos = validDrafts.any { it.localPendingAttachments().isNotEmpty() }
                    _profileSaveStatusMessage.value =
                        "Đang chờ đồng bộ ${validDrafts.size} hồ sơ lên máy chủ khi có kết nối mạng."
                    if (hasLocalPhotos) {
                        _photoUploadStatusMessage.value =
                            "Ảnh đính kèm đang chờ hồ sơ được tạo thành công trên máy chủ trước khi tải lên."
                    }
                }
                val msg = apiEx?.message
                    ?: e.message
                    ?: "Không thể gửi hồ sơ lên máy chủ."
                _workerMutationError.value = msg
                showBanner(msg, isError = true)
            } finally {
                _workerMutationBusy.value = false
            }
        }
    }

    fun submitExistingDraftSubmission(item: OwnSubmissionItem) {
        if (_workerMutationBusy.value) return
        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            try {
                transitionDraftSubmissionInternal(
                    submissionId = item.submissionId,
                    entryCount = item.entryCount,
                    employeeCodes = emptyList(),
                    navigateToWorkersOnConfirm = false,
                    wasJustCreated = false,
                    pendingAttachments = emptyList(),
                    onSubmittedSuccess = {
                        if (_currentDestination.value == MainDestination.WORKERS) {
                            selectWorkerTab(_activeWorkerTabKey.value)
                        }
                    }
                )
            } finally {
                _workerMutationBusy.value = false
            }
        }
    }

    private suspend fun transitionDraftSubmissionInternal(
        submissionId: String,
        entryCount: Int,
        employeeCodes: List<String>,
        navigateToWorkersOnConfirm: Boolean,
        wasJustCreated: Boolean,
        pendingAttachments: List<PendingEntryAttachmentTask> = emptyList(),
        onSubmittedSuccess: () -> Unit
    ) {
        val codesNote = if (employeeCodes.isNotEmpty()) {
            " Mã NLĐ: ${employeeCodes.joinToString(", ")}"
        } else {
            ""
        }
        try {
            val preflight = repository.directEntryService.preflightDuplicateCccd(submissionId)
            if (preflight.conflictCount > 0) {
                refreshOwnSubmissionsForHistory()
                _pendingDuplicateCccdSubmission.value = PendingDuplicateCccdSubmission(
                    entryCount = entryCount,
                    employeeCodes = employeeCodes,
                    preflight = preflight,
                    navigateToWorkersOnConfirm = navigateToWorkersOnConfirm,
                    pendingAttachments = pendingAttachments
                )
                return
            }
            val transitioned = repository.directEntryService.transitionSubmissionToSubmitted(
                submissionId = submissionId,
                expectedVersion = preflight.version
            )
            refreshOwnSubmissionsForHistory()
            val baseBannerMsg = if (transitioned.state == "SUBMITTED") {
                "Đã đẩy chính thức $entryCount hồ sơ NLĐ lên hệ thống.$codesNote"
            } else {
                "Đã gửi $entryCount hồ sơ NLĐ sang trạng thái chờ duyệt.$codesNote"
            }
            _profileSaveStatusMessage.value = baseBannerMsg
            if (pendingAttachments.isNotEmpty()) {
                showBanner("$baseBannerMsg Đang tải ngầm ${pendingAttachments.size} ảnh & giấy tờ…")
                enqueueBackgroundEntryUploads(pendingAttachments)
            } else {
                _photoUploadStatusMessage.value = null
                showBanner(baseBannerMsg)
            }
            onSubmittedSuccess()
        } catch (e: BackendApiException) {
            if (e.backendCode == "DUPLICATE_CCCD_CONFIRMATION_REQUIRED") {
                val refreshedPreflight = runCatching {
                    repository.directEntryService.preflightDuplicateCccd(submissionId)
                }.onFailure { it.rethrowIfCancelled() }.getOrNull()
                if (refreshedPreflight != null && refreshedPreflight.conflictCount > 0) {
                    refreshOwnSubmissionsForHistory()
                    _pendingDuplicateCccdSubmission.value = PendingDuplicateCccdSubmission(
                        entryCount = entryCount,
                        employeeCodes = employeeCodes,
                        preflight = refreshedPreflight,
                        navigateToWorkersOnConfirm = navigateToWorkersOnConfirm,
                        pendingAttachments = pendingAttachments
                    )
                    return
                }
            }
            refreshOwnSubmissionsForHistory()
            val msg = if (wasJustCreated) {
                "Đã lưu đợt nháp ($entryCount NLĐ)$codesNote nhưng chưa gửi chính thức: ${e.message} Mở Đợt nhập liệu hoặc mục Tôi đã nhập để bấm Gửi chính thức."
            } else {
                e.message
            }
            _workerMutationError.value = msg
            showBanner(msg, isError = true)
            if (wasJustCreated && pendingAttachments.isNotEmpty()) {
                enqueueBackgroundEntryUploads(pendingAttachments)
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            refreshOwnSubmissionsForHistory()
            val detail = e.message ?: "Không thể chuyển đợt nhập sang trạng thái gửi chính thức."
            val msg = if (wasJustCreated) {
                "Đã lưu đợt nháp ($entryCount NLĐ)$codesNote nhưng chưa gửi chính thức: $detail"
            } else {
                detail
            }
            _workerMutationError.value = msg
            showBanner(msg, isError = true)
            if (wasJustCreated && pendingAttachments.isNotEmpty()) {
                enqueueBackgroundEntryUploads(pendingAttachments)
            }
        }
    }

    fun confirmDuplicateCccdSubmission(onNavigateToWorkers: (() -> Unit)? = null) {
        val pending = _pendingDuplicateCccdSubmission.value ?: return
        if (_workerMutationBusy.value) return
        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            try {
                val preflight = pending.preflight
                val transitioned = repository.directEntryService.transitionSubmissionToSubmitted(
                    submissionId = preflight.submissionId,
                    expectedVersion = preflight.version,
                    duplicateCccdFingerprint = preflight.fingerprint,
                    duplicateCccdCount = preflight.conflictCount
                )
                _pendingDuplicateCccdSubmission.value = null
                refreshOwnSubmissionsForHistory()
                val codesNote = if (pending.employeeCodes.isNotEmpty()) {
                    " Mã NLĐ: ${pending.employeeCodes.joinToString(", ")}"
                } else {
                    ""
                }
                val baseBannerMsg = if (transitioned.state == "SUBMITTED") {
                    "Đã xác nhận và đẩy chính thức ${pending.entryCount} hồ sơ NLĐ lên hệ thống.$codesNote"
                } else {
                    "Đã xác nhận và gửi ${pending.entryCount} hồ sơ NLĐ sang trạng thái chờ duyệt.$codesNote"
                }
                _profileSaveStatusMessage.value = baseBannerMsg
                if (pending.pendingAttachments.isNotEmpty()) {
                    showBanner("$baseBannerMsg Đang tải ngầm ${pending.pendingAttachments.size} ảnh & giấy tờ…")
                    enqueueBackgroundEntryUploads(pending.pendingAttachments)
                } else {
                    _photoUploadStatusMessage.value = null
                    showBanner(baseBannerMsg)
                }
                if (pending.navigateToWorkersOnConfirm) {
                    onNavigateToWorkers?.invoke()
                } else if (_currentDestination.value == MainDestination.WORKERS) {
                    selectWorkerTab(_activeWorkerTabKey.value)
                }
            } catch (e: BackendApiException) {
                if (e.backendCode == "DUPLICATE_CCCD_CONFIRMATION_REQUIRED") {
                    val refreshedPreflight = runCatching {
                        repository.directEntryService.preflightDuplicateCccd(pending.preflight.submissionId)
                    }.onFailure { it.rethrowIfCancelled() }.getOrNull()
                    if (refreshedPreflight != null && refreshedPreflight.conflictCount > 0) {
                        _pendingDuplicateCccdSubmission.value = pending.copy(preflight = refreshedPreflight)
                    }
                }
                _workerMutationError.value = e.message
                showBanner(e.message, isError = true)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = e.message ?: "Không thể xác nhận gửi chính thức đợt nhập liệu."
                _workerMutationError.value = msg
                showBanner(msg, isError = true)
            } finally {
                _workerMutationBusy.value = false
            }
        }
    }

    fun dismissDuplicateCccdConfirmation() {
        val pending = _pendingDuplicateCccdSubmission.value ?: return
        _pendingDuplicateCccdSubmission.value = null
        refreshOwnSubmissionsForHistory()
        _profileSaveStatusMessage.value =
            "Đợt nhập (${pending.entryCount} NLĐ) đã lưu trên hệ thống ở trạng thái Bản nháp."
        if (pending.pendingAttachments.isNotEmpty()) {
            showBanner(
                "Đợt nhập (${pending.entryCount} NLĐ) đang được giữ ở Bản nháp. Đang tải ngầm ${pending.pendingAttachments.size} ảnh & giấy tờ…",
                isError = false
            )
            enqueueBackgroundEntryUploads(pending.pendingAttachments)
        } else {
            showBanner(
                "Đợt nhập (${pending.entryCount} NLĐ) đang được giữ ở Bản nháp. Vào Đợt nhập liệu hoặc mục Tôi đã nhập để gửi chính thức sau.",
                isError = false
            )
        }
    }

    fun ensureInitialWorkerTabLoaded(actor: AppActor) {
        val visibleTabs = resolveVisibleWorkerTabs(actor)
        if (visibleTabs.isEmpty()) {
            _workerRows.value = emptyList()
            _ownSubmissions.value = emptyList()
            _workerError.value = "Tài khoản chưa được cấp quyền xem danh sách người lao động."
            return
        }
        val currentTab = _activeWorkerTabKey.value
        val initialTab = if (visibleTabs.any { it.key == currentTab }) {
            currentTab
        } else {
            visibleTabs.first().key
        }
        if (_workerLoading.value && _activeWorkerTabKey.value == initialTab && workerFetchJob?.isActive == true) {
            return
        }
        selectWorkerTab(
            tabKey = initialTab,
            search = lastSearchQuery,
            projectId = lastFilterProjectId,
            recruiterId = lastFilterRecruiterId,
            employmentStatus = lastFilterStatus,
            allowFallbackFromManaged = (initialTab == "managed")
        )
    }

    fun selectWorkerTab(
        tabKey: String,
        search: String = lastSearchQuery,
        projectId: String? = lastFilterProjectId,
        recruiterId: String? = lastFilterRecruiterId,
        employmentStatus: String? = lastFilterStatus,
        allowFallbackFromManaged: Boolean = false
    ) {
        _activeWorkerTabKey.value = tabKey
        lastSearchQuery = search
        lastFilterProjectId = projectId
        lastFilterRecruiterId = recruiterId
        lastFilterStatus = employmentStatus

        val fetchToken = ++workerFetchToken
        workerFetchJob?.cancel()
        workerFetchJob = viewModelScope.launch {
            _workerLoading.value = true
            _workerError.value = null
            if (tabKey == "uploader") {
                try {
                    val page = repository.directEntryService.listOwnSubmissions(pageSize = 20, cursor = null)
                    if (workerFetchToken == fetchToken) {
                        _ownSubmissions.value = page.items
                        _submissionsHasMore.value = page.hasMore
                        _submissionsNextCursor.value = page.nextCursor
                    }
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    if (workerFetchToken == fetchToken) {
                        _ownSubmissions.value = emptyList()
                        _submissionsHasMore.value = false
                        _submissionsNextCursor.value = null
                        _workerError.value = (e as? BackendApiException)?.message
                            ?: e.message
                            ?: "Không thể tải lịch sử đợt nhập liệu."
                    }
                } finally {
                    if (workerFetchToken == fetchToken) {
                        _workerLoading.value = false
                    }
                }
            } else {
                try {
                    val page = repository.directEntryService.listWorkers(
                        scope = tabKey,
                        pageSize = 25,
                        cursor = null,
                        search = search,
                        projectId = projectId,
                        recruiterId = recruiterId,
                        employmentStatus = employmentStatus
                    )
                    if (workerFetchToken == fetchToken) {
                        _workerRows.value = page.items
                        _workerHasMore.value = page.hasMore
                        _workerNextCursor.value = page.nextCursor
                        _workerSummary.value = page.summary
                    }
                } catch (e: BackendApiException) {
                    if (workerFetchToken != fetchToken) return@launch
                    if (allowFallbackFromManaged && tabKey == "managed" && e.kind == BackendFailureKind.FORBIDDEN) {
                        val actor = currentActor.value
                        val fallbackTab = actor?.let { resolveVisibleWorkerTabs(it) }
                            ?.firstOrNull { it.key != "managed" }
                        if (fallbackTab != null) {
                            selectWorkerTab(
                                tabKey = fallbackTab.key,
                                search = search,
                                projectId = projectId,
                                recruiterId = recruiterId,
                                employmentStatus = employmentStatus,
                                allowFallbackFromManaged = false
                            )
                            return@launch
                        }
                    }
                    // Per App BI `relationDenialIsEmpty`: 403 on `managed` or `recruited` means the actor
                    // currently has no active project assignment or recruiter link -> render empty list, not error.
                    if ((tabKey == "managed" || tabKey == "recruited") && e.kind == BackendFailureKind.FORBIDDEN) {
                        _workerRows.value = emptyList()
                        _workerSummary.value = null
                        _workerHasMore.value = false
                        _workerNextCursor.value = null
                        _workerError.value = null
                        return@launch
                    }
                    if (e.kind == BackendFailureKind.NETWORK) {
                        val cached = runCatching { repository.getCachedWorkersByScope(tabKey) }
                            .onFailure { it.rethrowIfCancelled() }
                            .getOrDefault(emptyList())
                        if (workerFetchToken != fetchToken) return@launch
                        _workerRows.value = cached
                    } else {
                        _workerRows.value = emptyList()
                    }
                    _workerHasMore.value = false
                    _workerNextCursor.value = null
                    _workerError.value = e.message
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    if (workerFetchToken == fetchToken) {
                        _workerRows.value = emptyList()
                        _workerHasMore.value = false
                        _workerNextCursor.value = null
                        _workerError.value = e.message ?: "Không thể tải danh sách người lao động."
                    }
                } finally {
                    if (workerFetchToken == fetchToken) {
                        _workerLoading.value = false
                    }
                }
            }
        }
    }

    fun loadMoreWorkersOrSubmissions() {
        if (_workerLoading.value) return
        val tabKey = _activeWorkerTabKey.value
        if (tabKey == "uploader") {
            val cursor = _submissionsNextCursor.value ?: return
            if (!_submissionsHasMore.value) return
            viewModelScope.launch {
                _workerLoading.value = true
                try {
                    val page = repository.directEntryService.listOwnSubmissions(pageSize = 20, cursor = cursor)
                    _ownSubmissions.value = _ownSubmissions.value + page.items
                    _submissionsHasMore.value = page.hasMore
                    _submissionsNextCursor.value = page.nextCursor
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    showBanner(
                        (e as? BackendApiException)?.message ?: "Không thể tải thêm lịch sử đợt nhập.",
                        isError = true
                    )
                } finally {
                    _workerLoading.value = false
                }
            }
        } else {
            val cursor = _workerNextCursor.value ?: return
            if (!_workerHasMore.value) return
            viewModelScope.launch {
                _workerLoading.value = true
                try {
                    val page = repository.directEntryService.listWorkers(
                        scope = tabKey,
                        pageSize = 25,
                        cursor = cursor,
                        search = lastSearchQuery,
                        projectId = lastFilterProjectId,
                        recruiterId = lastFilterRecruiterId,
                        employmentStatus = lastFilterStatus
                    )
                    _workerRows.value = _workerRows.value + page.items
                    _workerHasMore.value = page.hasMore
                    _workerNextCursor.value = page.nextCursor
                    _workerSummary.value = page.summary
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    showBanner(
                        (e as? BackendApiException)?.message ?: "Không thể tải thêm danh sách NLĐ.",
                        isError = true
                    )
                } finally {
                    _workerLoading.value = false
                }
            }
        }
    }

    fun openEntryDetail(entryId: String) {
        viewModelScope.launch {
            try {
                val detail = repository.directEntryService.getEntryDetail(entryId)
                _selectedEntryDetail.value = detail
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải chi tiết hồ sơ từ máy chủ.",
                    isError = true
                )
            }
        }
    }

    fun closeEntryDetail() {
        _selectedEntryDetail.value = null
    }

    fun openProposeChange(worker: WorkerProfileEntity) {
        if (!worker.allowedActions.proposeChange) return
        _workerMutationError.value = null
        loadCatalogIfNeeded()
        viewModelScope.launch {
            val detail = if (worker.allowedActions.view) {
                runCatching { repository.directEntryService.getEntryDetail(worker.entryId) }
                    .onFailure { it.rethrowIfCancelled() }
                    .getOrNull()
            } else {
                null
            }
            _proposeChangeTarget.value = worker to detail
        }
    }

    fun closeProposeChange() {
        _proposeChangeTarget.value = null
        _workerMutationError.value = null
    }

    fun submitProposeChange(
        entryId: String,
        targetKind: String,
        expectedVersion: Int,
        proposal: JSONObject,
        reason: String?
    ) {
        if (_workerMutationBusy.value) return
        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            try {
                val created = repository.directEntryService.createChangeRequest(
                    entryId = entryId,
                    targetKind = targetKind,
                    expectedVersion = expectedVersion,
                    proposal = proposal,
                    reason = reason
                )
                _proposeChangeTarget.value = null
                showBanner("Đã gửi đề xuất thay đổi (#${created.requestId.take(8)}).")
                selectWorkerTab(_activeWorkerTabKey.value)
            } catch (e: BackendApiException) {
                _workerMutationError.value = e.message
                if (e.kind == BackendFailureKind.CONFLICT) {
                    // Reload baseline from server on OCC conflict
                    val currentWorker = _proposeChangeTarget.value?.first
                    if (currentWorker != null) {
                        val refreshed = runCatching { repository.directEntryService.getEntryDetail(entryId) }
                            .onFailure { it.rethrowIfCancelled() }
                            .getOrNull()
                        if (refreshed != null) {
                            _proposeChangeTarget.value = currentWorker.copy(entryVersion = refreshed.version) to refreshed
                        }
                    }
                    selectWorkerTab(_activeWorkerTabKey.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _workerMutationError.value = e.message ?: "Không thể gửi đề xuất thay đổi."
            } finally {
                _workerMutationBusy.value = false
            }
        }
    }

    private fun cleanupAllDirectEditStagedFiles() {
        val currentMap = _directEditFileStatuses.value
        _directEditFileStatuses.value = emptyMap()
        currentMap.values.forEach { status ->
            discardDraftAttachment(status.stagedRef)
        }
    }

    fun openDirectEdit(worker: WorkerProfileEntity) {
        if (!worker.allowedActions.directEdit) return
        cleanupAllDirectEditStagedFiles()
        _directEditProfileSaveMessage.value = null
        _workerMutationError.value = null
        loadCatalogIfNeeded()
        viewModelScope.launch {
            try {
                val detail = repository.directEntryService.getEntryDetail(worker.entryId)
                _directEditTarget.value = worker.copy(entryVersion = detail.version) to detail
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải hồ sơ gốc để sửa trực tiếp.",
                    isError = true
                )
            }
        }
    }

    fun closeDirectEdit() {
        cleanupAllDirectEditStagedFiles()
        _directEditProfileSaveMessage.value = null
        _directEditTarget.value = null
        _workerMutationError.value = null
    }

    fun attachDirectEditStagedDocument(documentType: String, stagedRef: StagedDocumentRef) {
        val prev = _directEditFileStatuses.value[documentType]?.stagedRef
        if (prev != null && prev.localFilePath != stagedRef.localFilePath) {
            discardDraftAttachment(prev)
        }
        val kb = ((stagedRef.sizeBytes + 512L) / 1024L).coerceAtLeast(1L)
        val sizeLabel = if (kb >= 1024L) {
            String.format(Locale.US, "%.1f MB", kb / 1024.0)
        } else {
            "$kb KB"
        }
        val label = DirectEntryContract.directEditDocumentLabel(documentType)
        val pageHint = if (stagedRef.pageCount > 1) " · ${stagedRef.pageCount} trang PDF" else ""
        val status = DirectEditFileStatus(
            documentType = documentType,
            state = DirectEditFileState.SELECTED,
            message = "$label: Đã chọn tệp mới ($sizeLabel$pageHint), chờ lưu.",
            stagedRef = stagedRef
        )
        _directEditFileStatuses.value = _directEditFileStatuses.value + (documentType to status)
    }

    fun clearDirectEditStagedDocument(documentType: String) {
        val prev = _directEditFileStatuses.value[documentType] ?: return
        discardDraftAttachment(prev.stagedRef)
        _directEditFileStatuses.value = _directEditFileStatuses.value - documentType
    }

    fun retryDirectEditDocument(
        documentType: String,
        reason: String? = null
    ) {
        if (_workerMutationBusy.value) return
        val target = _directEditTarget.value ?: return
        val fileStatus = _directEditFileStatuses.value[documentType] ?: return
        val stagedRef = fileStatus.stagedRef ?: return
        if (currentActor.value?.hasCapability("document_upload") == false) return

        _workerMutationBusy.value = true
        _workerMutationError.value = null
        val label = DirectEntryContract.directEditDocumentLabel(documentType)
        _directEditFileStatuses.value = _directEditFileStatuses.value + (
            documentType to fileStatus.copy(
                state = DirectEditFileState.UPLOADING,
                message = "$label: Đang thử tải lại..."
            )
        )

        viewModelScope.launch {
            try {
                val currentWorker = _directEditTarget.value?.first ?: target.first
                val currentDetail = _directEditTarget.value?.second ?: target.second
                val outcome = repository.directEntryService.uploadDirectEditDocument(
                    entryId = currentDetail.entryId,
                    expectedEntryVersion = currentDetail.version,
                    documentType = documentType,
                    stagedRef = stagedRef,
                    reason = reason
                )
                val updatedFileVersions = if (outcome.ok) {
                    currentDetail.fileVersions + (documentType to outcome.documentVersion)
                } else {
                    currentDetail.fileVersions
                }
                val updatedDetail = currentDetail.copy(
                    version = outcome.entryVersion,
                    fileVersions = updatedFileVersions
                )
                _directEditTarget.value = currentWorker.copy(entryVersion = outcome.entryVersion) to updatedDetail

                if (!outcome.ok) {
                    val errMsg = outcome.message
                        ?: DirectEntryContract.directEditDocumentErrorMessage(documentType, outcome.code)
                    _directEditFileStatuses.value = _directEditFileStatuses.value + (
                        documentType to DirectEditFileStatus(
                            documentType = documentType,
                            state = DirectEditFileState.ERROR,
                            message = errMsg,
                            stagedRef = stagedRef
                        )
                    )
                    _workerMutationError.value = errMsg
                } else {
                    discardDraftAttachment(stagedRef)
                    _directEditFileStatuses.value = _directEditFileStatuses.value + (
                        documentType to DirectEditFileStatus(
                            documentType = documentType,
                            state = DirectEditFileState.SAVED,
                            message = "$label: Đã tải lên và lưu thành công (v${outcome.documentVersion}).",
                            documentVersion = outcome.documentVersion,
                            stagedRef = null
                        )
                    )
                    val refreshed = runCatching { repository.directEntryService.getEntryDetail(currentDetail.entryId) }
                        .onFailure { it.rethrowIfCancelled() }
                        .getOrNull()
                    if (refreshed != null) {
                        _directEditTarget.value = currentWorker.copy(entryVersion = refreshed.version) to refreshed
                        if (_selectedEntryDetail.value?.entryId == refreshed.entryId) {
                            _selectedEntryDetail.value = refreshed
                        }
                    }
                    val hasOtherErrors = _directEditFileStatuses.value.values.any { it.state == DirectEditFileState.ERROR }
                    if (!hasOtherErrors) {
                        _workerMutationError.value = null
                    }
                    selectWorkerTab(_activeWorkerTabKey.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val errMsg = (e as? BackendApiException)?.message
                    ?: "$label: Không tải được tệp lên máy chủ. Vui lòng bấm Thử lại tệp này."
                _directEditFileStatuses.value = _directEditFileStatuses.value + (
                    documentType to DirectEditFileStatus(
                        documentType = documentType,
                        state = DirectEditFileState.ERROR,
                        message = errMsg,
                        stagedRef = stagedRef
                    )
                )
                _workerMutationError.value = errMsg
            } finally {
                _workerMutationBusy.value = false
            }
        }
    }

    fun submitDirectEdit(
        entryId: String,
        expectedEntryVersion: Int,
        patch: JSONObject,
        reason: String?
    ) {
        if (_workerMutationBusy.value) return
        val canUploadDocs = currentActor.value?.hasCapability("document_upload") ?: true
        val pendingSpecs = if (canUploadDocs) {
            DirectEntryContract.DIRECT_EDIT_DOCUMENT_TYPES.filter { spec ->
                val st = _directEditFileStatuses.value[spec.type]
                st != null && st.stagedRef != null &&
                    (st.state == DirectEditFileState.SELECTED || st.state == DirectEditFileState.ERROR)
            }
        } else {
            emptyList()
        }
        val hasProfilePatch = patch.length() > 0
        if (!hasProfilePatch && pendingSpecs.isEmpty()) {
            _workerMutationError.value = "Bạn chưa thay đổi thông tin hoặc chọn tài liệu mới để lưu."
            return
        }

        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            var currentVersion = expectedEntryVersion
            var profileSavedNow = false
            try {
                if (hasProfilePatch) {
                    val baselineDetail = _directEditTarget.value?.second
                    val refreshedDetail = repository.directEntryService.privilegedEditEntry(
                        entryId = entryId,
                        expectedEntryVersion = currentVersion,
                        patch = patch,
                        reason = reason,
                        baselineFallback = baselineDetail
                    )
                    currentVersion = refreshedDetail.version
                    profileSavedNow = true
                    _directEditProfileSaveMessage.value =
                        "Thông tin hồ sơ (cá nhân, công việc, ngân hàng): Đã lưu thành công."
                    val currentWorker = _directEditTarget.value?.first
                    if (currentWorker != null) {
                        _directEditTarget.value = currentWorker.copy(entryVersion = refreshedDetail.version) to refreshedDetail
                    }
                    if (_selectedEntryDetail.value?.entryId == entryId) {
                        _selectedEntryDetail.value = refreshedDetail
                    }
                }

                if (pendingSpecs.isEmpty()) {
                    cleanupAllDirectEditStagedFiles()
                    _directEditProfileSaveMessage.value = null
                    _directEditTarget.value = null
                    showBanner("Đã cập nhật hồ sơ thành công (phiên bản v$currentVersion).")
                    selectWorkerTab(_activeWorkerTabKey.value)
                    return@launch
                }

                var anyFileFailed = false
                for (spec in pendingSpecs) {
                    val currentStatus = _directEditFileStatuses.value[spec.type] ?: continue
                    val stagedRef = currentStatus.stagedRef ?: continue
                    _directEditFileStatuses.value = _directEditFileStatuses.value + (
                        spec.type to currentStatus.copy(
                            state = DirectEditFileState.UPLOADING,
                            message = "${spec.label}: Đang tải tệp lên máy chủ..."
                        )
                    )
                    val outcome = repository.directEntryService.uploadDirectEditDocument(
                        entryId = entryId,
                        expectedEntryVersion = currentVersion,
                        documentType = spec.type,
                        stagedRef = stagedRef,
                        reason = reason
                    )
                    currentVersion = outcome.entryVersion
                    val latestTarget = _directEditTarget.value
                    if (latestTarget != null) {
                        val updatedFileVersions = if (outcome.ok) {
                            latestTarget.second.fileVersions + (spec.type to outcome.documentVersion)
                        } else {
                            latestTarget.second.fileVersions
                        }
                        _directEditTarget.value = latestTarget.first.copy(entryVersion = currentVersion) to
                            latestTarget.second.copy(
                                version = currentVersion,
                                fileVersions = updatedFileVersions
                            )
                    }

                    if (!outcome.ok) {
                        anyFileFailed = true
                        val errMsg = outcome.message
                            ?: DirectEntryContract.directEditDocumentErrorMessage(spec.type, outcome.code)
                        _directEditFileStatuses.value = _directEditFileStatuses.value + (
                            spec.type to DirectEditFileStatus(
                                documentType = spec.type,
                                state = DirectEditFileState.ERROR,
                                message = errMsg,
                                stagedRef = stagedRef
                            )
                        )
                    } else {
                        discardDraftAttachment(stagedRef)
                        _directEditFileStatuses.value = _directEditFileStatuses.value + (
                            spec.type to DirectEditFileStatus(
                                documentType = spec.type,
                                state = DirectEditFileState.SAVED,
                                message = "${spec.label}: Đã tải lên và lưu thành công (v${outcome.documentVersion}).",
                                documentVersion = outcome.documentVersion,
                                stagedRef = null
                            )
                        )
                    }
                }

                val refreshedAfterDocs = runCatching { repository.directEntryService.getEntryDetail(entryId) }
                    .onFailure { it.rethrowIfCancelled() }
                    .getOrNull()
                if (refreshedAfterDocs != null) {
                    val currentWorker = _directEditTarget.value?.first
                    if (currentWorker != null) {
                        _directEditTarget.value = currentWorker.copy(entryVersion = refreshedAfterDocs.version) to refreshedAfterDocs
                    }
                    if (_selectedEntryDetail.value?.entryId == entryId) {
                        _selectedEntryDetail.value = refreshedAfterDocs
                    }
                }

                selectWorkerTab(_activeWorkerTabKey.value)
                if (anyFileFailed) {
                    _workerMutationError.value = if (profileSavedNow || _directEditProfileSaveMessage.value != null) {
                        "Thông tin hồ sơ đã được lưu. Một số tài liệu chưa tải lên được — vui lòng kiểm tra từng tệp bên dưới và thử lại."
                    } else {
                        "Một số tài liệu chưa tải lên được — vui lòng kiểm tra từng tệp bên dưới và thử lại."
                    }
                } else {
                    cleanupAllDirectEditStagedFiles()
                    _directEditProfileSaveMessage.value = null
                    _directEditTarget.value = null
                    showBanner(
                        if (hasProfilePatch) {
                            "Đã cập nhật hồ sơ và tài liệu đính kèm thành công."
                        } else {
                            "Đã tải lên tài liệu đính kèm thành công."
                        }
                    )
                }
            } catch (e: BackendApiException) {
                _workerMutationError.value = e.message
                if (e.kind == BackendFailureKind.CONFLICT) {
                    // Version conflict: reload baseline from backend
                    val currentWorker = _directEditTarget.value?.first
                    val latest = runCatching { repository.directEntryService.getEntryDetail(entryId) }
                        .onFailure { it.rethrowIfCancelled() }
                        .getOrNull()
                    if (currentWorker != null && latest != null) {
                        _directEditTarget.value = currentWorker.copy(entryVersion = latest.version) to latest
                    }
                    selectWorkerTab(_activeWorkerTabKey.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _workerMutationError.value = e.message ?: "Không thể cập nhật hồ sơ."
            } finally {
                _workerMutationBusy.value = false
            }
        }
    }

    fun exportWorkersExcel(
        scopeKey: String,
        projectId: String?,
        recruiterId: String?,
        employmentStatus: String?,
        onReadyFile: (File) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val file = repository.directEntryService.exportWorkersExcel(
                    scope = scopeKey,
                    projectId = projectId,
                    recruiterId = recruiterId,
                    employmentStatus = employmentStatus
                )
                showBanner("Đã xuất tệp Excel: ${file.name}")
                onReadyFile(file)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể xuất tệp Excel từ máy chủ.",
                    isError = true
                )
            }
        }
    }

    fun downloadEntryDocument(
        downloadPath: String,
        fallbackFileName: String,
        onReadyFile: (File) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val file = repository.directEntryService.downloadEntryDocument(downloadPath, fallbackFileName)
                onReadyFile(file)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải tài liệu đính kèm.",
                    isError = true
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentGpsLocation(onReady: ((GpsSnapshot?) -> Unit)? = null) {
        val appContext = getApplication<Application>()
        _gpsLoading.value = true
        _gpsError.value = null
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    try {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
                        val tokenSource = CancellationTokenSource()
                        val location = suspendCancellableCoroutine { cont ->
                            fusedClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                tokenSource.token
                            ).addOnSuccessListener { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }.addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }
                        } ?: run {
                            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        }

                        if (location != null) {
                            val address = reverseGeocodeAddress(appContext, location.latitude, location.longitude)
                            GpsSnapshot(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracyMeters = location.accuracy.coerceAtLeast(1.0f),
                                addressHint = address
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        e.rethrowIfCancelled()
                        null
                    }
                }
                _gpsSnapshot.value = snapshot
                if (snapshot == null) {
                    _gpsError.value = "Không lấy được tọa độ GPS thực tế từ thiết bị. Vui lòng bật Dịch vụ Vị trí."
                }
                onReady?.invoke(snapshot)
            } finally {
                _gpsLoading.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeAddress(context: Context, lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val first = addresses?.firstOrNull()
            if (first != null) {
                buildString {
                    for (i in 0..first.maxAddressLineIndex) {
                        if (isNotEmpty()) append(", ")
                        append(first.getAddressLine(i))
                    }
                }.ifBlank { "Tọa độ GPS: %.5f, %.5f".format(Locale.US, lat, lng) }
            } else {
                "Tọa độ GPS: %.5f, %.5f".format(Locale.US, lat, lng)
            }
        } catch (_: Exception) {
            "Tọa độ GPS: %.5f, %.5f".format(Locale.US, lat, lng)
        }
    }

    // =========================================================================
    // Attendance Check-In & Durable Outbox Actions (Zero fake success)
    // =========================================================================

    fun syncAttendanceTimeAnchor(actor: AppActor? = currentActor.value, force: Boolean = false) {
        val resolvedActor = actor ?: return
        viewModelScope.launch {
            runCatching {
                repository.attendanceTimeAnchorManager.syncTimeAnchor(
                    appUserId = resolvedActor.appUserId,
                    force = force
                )
            }.onFailure { it.rethrowIfCancelled() }
        }
    }

    fun syncAttendanceHistoryFromServer(actor: AppActor? = currentActor.value) {
        val resolvedActor = actor ?: return
        if (!WorkReportsContract.isUuid(resolvedActor.appUserId)) return
        viewModelScope.launch {
            runCatching {
                val todayIso = resolveReferenceTodayIsoHcm(resolvedActor.appUserId)
                repository.attendanceOutboxService.syncServerHistoryForUser(
                    actor = resolvedActor,
                    referenceTodayIso = todayIso
                )
            }.onFailure { it.rethrowIfCancelled() }
        }
    }

    fun millisUntilTimeAnchorRefresh(appUserId: String): Long? {
        return repository.attendanceTimeAnchorManager.millisUntilProactiveRefresh(appUserId)
    }

    fun snapshotAttendanceCaptureTime(appUserId: String? = currentActor.value?.appUserId): CapturedAttendanceTime? {
        val cleanId = appUserId?.trim().orEmpty()
        if (cleanId.isEmpty()) return null
        return repository.attendanceTimeAnchorManager.resolveCaptureTime(cleanId)
    }

    fun resolveReferenceTodayIsoHcm(appUserId: String): String {
        val eval = repository.attendanceTimeAnchorManager.evaluateAnchor(appUserId)
        val estEpoch = eval.estimatedServerNowEpochMs
        return if (estEpoch != null) {
            AttendanceTimeContract.formatEpochMillisInHcmIso(estEpoch).take(10)
        } else {
            DirectEntryContract.todayIsoInHcm()
        }
    }

    fun submitAttendanceCheckIn(
        photoFile: File,
        photoUri: String?,
        note: String,
        capturedTime: CapturedAttendanceTime? = null
    ) {
        val actor = currentActor.value ?: run {
            showBanner("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.", isError = true)
            return
        }
        val gps = _gpsSnapshot.value ?: run {
            showBanner("Chưa có tọa độ GPS thực tế. Vui lòng bấm làm mới GPS.", isError = true)
            return
        }
        if (_submittingAttendance.value) return
        _submittingAttendance.value = true
        viewModelScope.launch {
            try {
                val outcome = repository.attendanceOutboxService.submitCheckIn(
                    actor = actor,
                    latitude = gps.latitude,
                    longitude = gps.longitude,
                    accuracyMeters = gps.accuracyMeters,
                    addressHint = gps.addressHint,
                    sourcePhotoFile = photoFile,
                    sourcePhotoUri = photoUri,
                    note = note,
                    capturedTimeOverride = capturedTime
                )
                // Only `SYNCED` has isError = false with "Đã chấm công"; offline QUEUED has "Đã lưu trên thiết bị, đang chờ đồng bộ"
                showBanner(outcome.userMessage, isError = !outcome.isSynced)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Không thể thực hiện chấm công.",
                    isError = true
                )
            } finally {
                _submittingAttendance.value = false
            }
        }
    }

    fun retryAttendanceOutbox() {
        val actor = currentActor.value ?: return
        if (_submittingAttendance.value) return
        _submittingAttendance.value = true
        viewModelScope.launch {
            try {
                val summary = repository.attendanceOutboxService.flushRetryableOutbox(
                    appUserId = actor.appUserId,
                    scheduleWorkOnFailure = true
                )
                when {
                    summary.processedCount == 0 && summary.remainingRetryableCount > 0 ->
                        showBanner("Đã lưu trên thiết bị, đang chờ đồng bộ khi có mạng.", isError = true)
                    summary.syncedCount > 0 && summary.remainingRetryableCount == 0 && summary.terminalFailureCount == 0 ->
                        showBanner("Đã chấm công (${summary.syncedCount} lượt đồng bộ thành công).", isError = false)
                    summary.syncedCount > 0 ->
                        showBanner("Đã đồng bộ ${summary.syncedCount}/${summary.processedCount} lượt chấm công.", isError = true)
                    else -> {
                        val latestRecord = attendanceRecords.value.firstOrNull { it.appUserId == actor.appUserId }
                        showBanner(
                            latestRecord?.statusMessage ?: "Chưa thể đồng bộ hàng đợi chấm công lên máy chủ.",
                            isError = true
                        )
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể đồng bộ hàng đợi chấm công.",
                    isError = true
                )
            } finally {
                _submittingAttendance.value = false
            }
        }
    }

    // =========================================================================
    // Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") Actions
    // Strictly authority-driven from backend `/api/leave/me` & `/api/leave/approvals`
    // =========================================================================

    fun resolveVisibleAttendanceTabs(): List<AttendanceAreaTab> {
        return LeaveContract.resolveVisibleAttendanceTabs(
            authority = _myLeaveSummary.value?.authority,
            errorCode = _myLeaveErrorCode.value
        )
    }

    fun selectAttendanceTab(tab: AttendanceAreaTab) {
        val visibleTabs = resolveVisibleAttendanceTabs()
        val safeTab = if (tab in visibleTabs) tab else AttendanceAreaTab.CHECK_IN
        _activeAttendanceTab.value = safeTab
        _leaveMutationError.value = null
        when (safeTab) {
            AttendanceAreaTab.CHECK_IN -> Unit
            AttendanceAreaTab.LEAVE_REQUEST -> {
                ensureInitialLeaveDraftDay()
                if (_myLeaveSummary.value == null && !_myLeaveLoading.value) {
                    refreshMyLeaveSummary(year = _leaveYear.value)
                }
            }
            AttendanceAreaTab.LEAVE_APPROVAL -> {
                refreshLeaveApprovalInbox(
                    statusFilter = _leaveInboxStatusFilter.value,
                    page = _leaveInboxPage.value
                )
            }
        }
    }

    fun ensureInitialLeaveDraftDay() {
        val summary = _myLeaveSummary.value
        val defaultType = if (summary != null && summary.balance.availableUnits <= 0) "UNPAID" else "ANNUAL"
        if (leaveDraftDays.isEmpty()) {
            val defaultDate = summary?.asOfDate
                ?.takeIf { LeaveContract.isValidIsoDate(it) }
                ?: LeaveContract.todayVietnamIsoDate()
            leaveDraftDays.add(
                LeaveDraftDayInput(
                    leaveDate = defaultDate,
                    sessionCode = "FULL",
                    leaveType = defaultType
                )
            )
        } else if (summary != null && summary.balance.availableUnits <= 0 &&
            leaveDraftDays.size == 1 && leaveDraftDays[0].leaveType == "ANNUAL"
        ) {
            leaveDraftDays[0] = leaveDraftDays[0].copy(leaveType = "UNPAID")
        }
    }

    private fun nextIsoDateAfter(isoDate: String): String {
        if (!LeaveContract.isValidIsoDate(isoDate)) {
            return _myLeaveSummary.value?.asOfDate
                ?.takeIf { LeaveContract.isValidIsoDate(it) }
                ?: LeaveContract.todayVietnamIsoDate()
        }
        return runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(isoDate) ?: return@runCatching isoDate
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                time = parsed
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            sdf.format(cal.time)
        }.getOrDefault(isoDate)
    }

    fun addLeaveDraftDay() {
        if (leaveDraftDays.size >= 120) {
            _leaveMutationError.value = "Mỗi đơn chỉ được tối đa 120 dòng ngày/buổi nghỉ."
            return
        }
        _leaveMutationError.value = null
        val summary = _myLeaveSummary.value
        val baseDate = leaveDraftDays.lastOrNull()?.leaveDate
            ?: summary?.asOfDate
            ?: LeaveContract.todayVietnamIsoDate()
        val nextDate = if (leaveDraftDays.isEmpty()) baseDate else nextIsoDateAfter(baseDate)
        val defaultType = leaveDraftDays.lastOrNull()?.leaveType
            ?: if (summary != null && summary.balance.availableUnits <= 0) "UNPAID" else "ANNUAL"
        leaveDraftDays.add(
            LeaveDraftDayInput(
                leaveDate = nextDate,
                sessionCode = "FULL",
                leaveType = defaultType
            )
        )
    }

    fun applyLeaveDraftDateRange(
        startDateIso: String,
        endDateIso: String,
        leaveType: String = "ANNUAL",
        skipSundays: Boolean = false
    ): Boolean {
        _leaveMutationError.value = null
        val (generated, errorMessage) = LeaveContract.generateDraftDaysFromRange(
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            leaveType = leaveType,
            sessionCode = "FULL",
            skipSundays = skipSundays
        )
        if (errorMessage != null) {
            _leaveMutationError.value = errorMessage
            return false
        }
        leaveDraftDays.clear()
        leaveDraftDays.addAll(generated)
        return true
    }

    fun removeLeaveDraftDay(id: String) {
        _leaveMutationError.value = null
        leaveDraftDays.removeAll { it.id == id }
        ensureInitialLeaveDraftDay()
    }

    fun updateLeaveDraftDayDate(id: String, leaveDate: String) {
        _leaveMutationError.value = null
        val index = leaveDraftDays.indexOfFirst { it.id == id }
        if (index >= 0) {
            leaveDraftDays[index] = leaveDraftDays[index].copy(leaveDate = leaveDate.trim())
        }
    }

    fun updateLeaveDraftDaySession(id: String, sessionCode: String) {
        _leaveMutationError.value = null
        val normalized = sessionCode.trim().uppercase(Locale.ROOT)
        if (normalized !in LeaveContract.SESSION_CODES) return
        val index = leaveDraftDays.indexOfFirst { it.id == id }
        if (index >= 0) {
            leaveDraftDays[index] = leaveDraftDays[index].copy(sessionCode = normalized)
        }
    }

    fun updateLeaveDraftDayType(id: String, leaveType: String) {
        _leaveMutationError.value = null
        val normalized = leaveType.trim().uppercase(Locale.ROOT)
        if (normalized !in LeaveContract.LEAVE_TYPES) return
        val index = leaveDraftDays.indexOfFirst { it.id == id }
        if (index >= 0) {
            leaveDraftDays[index] = leaveDraftDays[index].copy(leaveType = normalized)
        }
    }

    fun splitLeaveDraftDayToHalfDays(id: String) {
        if (leaveDraftDays.size >= 120) {
            _leaveMutationError.value = "Mỗi đơn chỉ được tối đa 120 dòng ngày/buổi nghỉ."
            return
        }
        _leaveMutationError.value = null
        val index = leaveDraftDays.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = leaveDraftDays[index]
        if (target.sessionCode != "FULL") return
        val primaryType = target.leaveType.trim().uppercase(Locale.ROOT).let {
            if (it in LeaveContract.LEAVE_TYPES) it else "ANNUAL"
        }
        val secondaryType = if (primaryType == "ANNUAL") "UNPAID" else "ANNUAL"
        val amRow = target.copy(sessionCode = "AM", leaveType = primaryType)
        val pmRow = LeaveDraftDayInput(
            leaveDate = target.leaveDate,
            sessionCode = "PM",
            leaveType = secondaryType
        )
        leaveDraftDays[index] = amRow
        leaveDraftDays.add(index + 1, pmRow)
    }

    fun autoAllocateLeaveDraftDaysByBalance() {
        _leaveMutationError.value = null
        val availableUnits = (_myLeaveSummary.value?.balance?.availableUnits ?: 0).coerceAtLeast(0)
        val allocated = LeaveContract.autoAllocateDraftDaysByBalance(
            days = leaveDraftDays.toList(),
            availableUnits = availableUnits
        )
        leaveDraftDays.clear()
        leaveDraftDays.addAll(allocated)
    }

    fun updateLeaveDraftReason(reason: String) {
        _leaveMutationError.value = null
        _leaveDraftReason.value = reason
    }

    fun clearLeaveMutationError() {
        _leaveMutationError.value = null
    }

    fun setLeaveYear(year: Int) {
        if (year < 2000 || year > 2100) return
        _leaveYear.value = year
        refreshMyLeaveSummary(year = year)
    }

    fun refreshMyLeaveSummary(year: Int = _leaveYear.value) {
        _myLeaveLoading.value = true
        _myLeaveError.value = null
        _myLeaveErrorCode.value = null
        viewModelScope.launch {
            try {
                val summary = repository.leaveService.fetchMyLeaveSummary(leaveYear = year)
                _myLeaveSummary.value = summary
                _leaveYear.value = summary.leaveYear
                _myLeaveError.value = null
                _myLeaveErrorCode.value = null
                ensureInitialLeaveDraftDay()

                val visibleTabs = LeaveContract.resolveVisibleAttendanceTabs(summary.authority, null)
                if (_activeAttendanceTab.value !in visibleTabs) {
                    _activeAttendanceTab.value = AttendanceAreaTab.CHECK_IN
                }
                if (summary.authority.canApproveRequests &&
                    !summary.authority.isSharedNonEmployee &&
                    (_activeAttendanceTab.value == AttendanceAreaTab.LEAVE_APPROVAL || _leaveApprovalInbox.value != null)
                ) {
                    refreshLeaveApprovalInbox(
                        statusFilter = _leaveInboxStatusFilter.value,
                        page = _leaveInboxPage.value
                    )
                }
            } catch (e: BackendApiException) {
                _myLeaveError.value = e.message
                _myLeaveErrorCode.value = e.backendCode
                if (e.backendCode == "LEAVE_SHARED_ACCOUNT_FORBIDDEN") {
                    _myLeaveSummary.value = null
                    _activeAttendanceTab.value = AttendanceAreaTab.CHECK_IN
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _myLeaveError.value = e.message ?: "Không thể tải thông tin phép cá nhân."
            } finally {
                _myLeaveLoading.value = false
            }
        }
    }

    fun submitLeaveRequest(onSuccess: (() -> Unit)? = null) {
        if (_leaveMutationBusy.value) return
        val daysSnapshot = leaveDraftDays.toList()
        val reasonSnapshot = _leaveDraftReason.value
        val summary = _myLeaveSummary.value
        val validationError = LeaveContract.validateCreateDraftInput(
            days = daysSnapshot,
            reason = reasonSnapshot,
            leaveYear = summary?.leaveYear,
            availableUnits = summary?.balance?.availableUnits
        )
        if (validationError != null) {
            _leaveMutationError.value = validationError
            showBanner(validationError, isError = true)
            return
        }
        _leaveMutationBusy.value = true
        _leaveMutationError.value = null
        viewModelScope.launch {
            try {
                repository.leaveService.createLeaveRequest(
                    days = daysSnapshot,
                    reason = reasonSnapshot
                )
                leaveDraftDays.clear()
                ensureInitialLeaveDraftDay()
                _leaveDraftReason.value = ""
                showBanner("Đã gửi đơn xin nghỉ phép thành công.")
                refreshMyLeaveSummary(year = _leaveYear.value)
                onSuccess?.invoke()
            } catch (e: BackendApiException) {
                _leaveMutationError.value = e.message
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT ||
                    e.backendCode == "LEAVE_ROW_VERSION_CONFLICT" ||
                    e.backendCode == "LEAVE_INVALID_STATE_TRANSITION"
                ) {
                    refreshMyLeaveSummary(year = _leaveYear.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = e.message ?: "Không thể gửi đơn xin nghỉ phép."
                _leaveMutationError.value = msg
                showBanner(msg, isError = true)
            } finally {
                _leaveMutationBusy.value = false
            }
        }
    }

    fun withdrawLeaveRequest(
        request: LeaveRequestProjection,
        reason: String?,
        onSuccess: (() -> Unit)? = null
    ) {
        if (_leaveMutationBusy.value) return
        val actorId = currentActor.value?.appUserId ?: _myLeaveSummary.value?.authority?.appUserId
        if (!LeaveContract.canWithdrawOwnLeaveRequest(actorId, request)) {
            val msg = "Đơn này không ở trạng thái Chờ duyệt hoặc không thuộc quyền tự rút của bạn."
            _leaveMutationError.value = msg
            showBanner(msg, isError = true)
            return
        }
        _leaveMutationBusy.value = true
        _leaveMutationError.value = null
        viewModelScope.launch {
            try {
                repository.leaveService.withdrawLeaveRequest(
                    requestId = request.requestId,
                    expectedRowVersion = request.rowVersion,
                    reason = reason
                )
                showBanner("Đã rút đơn xin nghỉ phép.")
                refreshMyLeaveSummary(year = _leaveYear.value)
                onSuccess?.invoke()
            } catch (e: BackendApiException) {
                _leaveMutationError.value = e.message
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT ||
                    e.backendCode == "LEAVE_ROW_VERSION_CONFLICT" ||
                    e.backendCode == "LEAVE_INVALID_STATE_TRANSITION"
                ) {
                    refreshMyLeaveSummary(year = _leaveYear.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = e.message ?: "Không thể rút đơn xin nghỉ phép."
                _leaveMutationError.value = msg
                showBanner(msg, isError = true)
            } finally {
                _leaveMutationBusy.value = false
            }
        }
    }

    fun setLeaveInboxFilter(statusFilter: String) {
        val clean = statusFilter.trim().uppercase(Locale.ROOT)
        if (clean !in LeaveContract.REQUEST_STATUS_FILTERS) return
        _leaveInboxStatusFilter.value = clean
        _leaveInboxPage.value = 1
        refreshLeaveApprovalInbox(statusFilter = clean, page = 1)
    }

    fun setLeaveInboxPage(page: Int) {
        val safePage = page.coerceAtLeast(1)
        _leaveInboxPage.value = safePage
        refreshLeaveApprovalInbox(statusFilter = _leaveInboxStatusFilter.value, page = safePage)
    }

    fun refreshLeaveApprovalInbox(
        statusFilter: String = _leaveInboxStatusFilter.value,
        page: Int = _leaveInboxPage.value
    ) {
        val authority = _myLeaveSummary.value?.authority
        if (authority != null && (!authority.canApproveRequests || authority.isSharedNonEmployee)) {
            _leaveApprovalInbox.value = null
            return
        }
        val safePage = page.coerceAtLeast(1)
        val limit = LEAVE_INBOX_PAGE_SIZE
        val offset = (safePage - 1) * limit
        _leaveInboxLoading.value = true
        _leaveInboxError.value = null
        viewModelScope.launch {
            try {
                val inbox = repository.leaveService.fetchApprovalInbox(
                    statusFilter = statusFilter,
                    limit = limit,
                    offset = offset
                )
                _leaveApprovalInbox.value = inbox
                _leaveInboxError.value = null
            } catch (e: BackendApiException) {
                _leaveInboxError.value = e.message
                if (e.kind == BackendFailureKind.FORBIDDEN || e.backendCode == "LEAVE_APPROVAL_FORBIDDEN") {
                    _leaveApprovalInbox.value = null
                    if (_activeAttendanceTab.value == AttendanceAreaTab.LEAVE_APPROVAL) {
                        _activeAttendanceTab.value = AttendanceAreaTab.CHECK_IN
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _leaveInboxError.value = e.message ?: "Không thể tải danh sách đơn cần duyệt."
            } finally {
                _leaveInboxLoading.value = false
            }
        }
    }

    fun decideLeaveRequest(
        request: LeaveRequestProjection,
        decision: String,
        reason: String?,
        onSuccess: (() -> Unit)? = null
    ) {
        if (_leaveMutationBusy.value) return
        val actorId = currentActor.value?.appUserId ?: _myLeaveSummary.value?.authority?.appUserId
        val (isValid, validationError) = LeaveContract.validateApproverDecisionInput(
            actorAppUserId = actorId,
            request = request,
            decision = decision,
            reason = reason.orEmpty()
        )
        if (!isValid) {
            val msg = validationError ?: "Quyết định xử lý đơn không hợp lệ."
            _leaveMutationError.value = msg
            showBanner(msg, isError = true)
            return
        }
        _leaveMutationBusy.value = true
        _leaveMutationError.value = null
        viewModelScope.launch {
            try {
                val cleanDecision = decision.trim().uppercase(Locale.ROOT)
                repository.leaveService.decideLeaveRequest(
                    requestId = request.requestId,
                    decision = cleanDecision,
                    expectedRowVersion = request.rowVersion,
                    reason = reason
                )
                val successMsg = when (cleanDecision) {
                    "APPROVE" -> "Đã duyệt đơn nghỉ phép."
                    "REJECT" -> "Đã từ chối đơn nghỉ phép."
                    "CANCEL_APPROVED" -> "Đã hủy đơn nghỉ phép đã duyệt và hoàn phép."
                    else -> "Đã cập nhật quyết định duyệt nghỉ phép."
                }
                showBanner(successMsg)
                refreshLeaveApprovalInbox(
                    statusFilter = _leaveInboxStatusFilter.value,
                    page = _leaveInboxPage.value
                )
                refreshMyLeaveSummary(year = _leaveYear.value)
                onSuccess?.invoke()
            } catch (e: BackendApiException) {
                _leaveMutationError.value = e.message
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT ||
                    e.backendCode == "LEAVE_ROW_VERSION_CONFLICT" ||
                    e.backendCode == "LEAVE_INVALID_STATE_TRANSITION"
                ) {
                    refreshLeaveApprovalInbox(
                        statusFilter = _leaveInboxStatusFilter.value,
                        page = _leaveInboxPage.value
                    )
                    refreshMyLeaveSummary(year = _leaveYear.value)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                val msg = e.message ?: "Không thể gửi quyết định xử lý đơn nghỉ phép."
                _leaveMutationError.value = msg
                showBanner(msg, isError = true)
            } finally {
                _leaveMutationBusy.value = false
            }
        }
    }

    companion object {
        internal const val BANNER_AUTO_DISMISS_MS = 5_000L
        internal const val LEAVE_INBOX_PAGE_SIZE = 50
    }
}
