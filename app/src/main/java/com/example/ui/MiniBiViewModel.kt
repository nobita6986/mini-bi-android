package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.BuildConfig
import com.example.data.AppActor
import com.example.data.AttendanceEntity
import com.example.data.AttendanceTimeContract
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.BiBucketItem
import com.example.data.CapturedAttendanceTime
import com.example.data.DirectEntryCatalog
import com.example.data.DirectEntryContract
import com.example.data.DuplicateCccdGroupItem
import com.example.data.GeneralOverviewSnapshot
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

enum class MainDestination(val route: String, val label: String) {
    DASHBOARD("dashboard", "Tổng quan"),
    WORK_REPORTS("work_reports", "Báo cáo ngày"),
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

    init {
        bootstrapSession()
    }

    internal fun cancelScopeForTest() {
        clearBanner()
        workerFetchJob?.cancel()
        workerFetchJob = null
        viewModelScope.cancel()
    }

    fun bootstrapSession() {
        _sessionBootstrapping.value = true
        viewModelScope.launch {
            try {
                val res = repository.bootstrapSession()
                _sessionBootstrapping.value = false
                res.onSuccess { actor ->
                    syncAttendanceTimeAnchor(actor = actor, force = false)
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
            else -> Unit
        }
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            repository.setThemeId(themeId)
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
            cachedTeamScopeWorkers = emptyList()
            cachedTeamTotalSummary = 0
            _duplicateGroups.value = emptyList()
            _duplicateError.value = null
            _duplicatePage.value = 1
            _duplicateHasMore.value = false
            _duplicateManagedOnly.value = false
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
            _editingObligation.value = null
            _workReportsError.value = null
            _reviewQueue.value = emptyList()
            _analyticsSnapshot.value = null
            _availableReportTemplates.value = emptyList()
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

    private fun buildGrantedTeamSnapshotFromCache(
        actor: AppActor,
        teamRef: String
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
        val scopedRows = if (canFilterByCatalogTeam) {
            cachedTeamScopeWorkers.filter { row ->
                recruiterMap[row.recruiterId]?.teamId == teamRef
            }
        } else {
            cachedTeamScopeWorkers
        }
        val totalOverride = if (teamGrants.size <= 1) cachedTeamTotalSummary else null
        return WorkReportsContract.buildTeamReportFromWorkerRows(
            bucketKey = "grant:$teamRef",
            bucketDisplay = bucketDisplay,
            rows = scopedRows,
            recruiterCatalogById = recruiterMap,
            totalWorkersOverride = totalOverride
        )
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
        _workforceLoading.value = true
        _workforceError.value = null
        _generalOverviewLoading.value = true
        _generalOverviewError.value = null
        viewModelScope.launch {
            try {
                val metrics = repository.workReportsAndReportingService.fetchWorkforceDeep(fromDate, toDate)
                _workforceDeep.value = metrics
                _teamScopeOptions.value = metrics.byTeam
                val leadingProject = metrics.byProject.firstOrNull { it.key != "unknown" }
                    ?: metrics.byProject.firstOrNull()
                _generalOverview.value = GeneralOverviewSnapshot(
                    asOfDate = metrics.asOfDate,
                    scopeDescription = "Toàn hệ thống",
                    totalProfiles = metrics.eligibleEpisodeTotal,
                    workingCount = metrics.onCount,
                    waitingCount = metrics.unconfirmedCount,
                    leftCount = metrics.offCount,
                    audienceKind = "all",
                    placementRatePct = metrics.placementRatePct,
                    retention7dPct = metrics.retention7dPct,
                    retention30dPct = metrics.retention30dPct,
                    projectCount = metrics.byProject.count { it.key != "unknown" }.coerceAtLeast(metrics.byProject.size),
                    recruiterCount = metrics.byRecruiter.count { it.key != "unknown" }.coerceAtLeast(metrics.byRecruiter.size),
                    leadingProject = leadingProject,
                    byRecruiter = metrics.byRecruiter.take(10),
                    byProject = metrics.byProject.take(10),
                    bySecondaryGroup = metrics.byTeam.take(10),
                    secondaryGroupTitle = "Cơ cấu theo nhóm tuyển dụng",
                    byProvider = metrics.byProvider.take(6)
                )
                _generalOverviewLoading.value = false
                val candidateBucket = metrics.byTeam.firstOrNull { it.key != "unknown" }
                    ?: metrics.byTeam.firstOrNull()
                if (candidateBucket != null &&
                    (_teamReport.value == null ||
                        metrics.byTeam.none { it.display == _teamReport.value?.bucketDisplay })
                ) {
                    loadTeamReport(candidateBucket.key, fromDate, toDate)
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
                    }
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

                val mergedByEntry = linkedMapOf<String, WorkerProfileEntity>()
                teamPage?.items?.forEach { mergedByEntry.putIfAbsent(it.entryId, it) }
                managedPage?.items?.forEach { mergedByEntry.putIfAbsent(it.entryId, it) }
                recruitedPage?.items?.forEach { mergedByEntry.putIfAbsent(it.entryId, it) }
                val rows = mergedByEntry.values.toList()

                val workingCount = rows.count { it.employmentStatus.equals("ON", ignoreCase = true) }
                val waitingCount = rows.count { it.employmentStatus.equals("UNCONFIRMED", ignoreCase = true) }
                val leftCount = rows.count { it.employmentStatus.equals("OFF", ignoreCase = true) }

                val totalProfiles = maxOf(
                    rows.size,
                    teamPage?.summary?.totalWorkers ?: 0,
                    managedPage?.summary?.totalWorkers ?: 0,
                    recruitedPage?.summary?.totalWorkers ?: 0
                )

                val placementRatePct = if (rows.isNotEmpty()) {
                    ((workingCount + leftCount).toFloat() * 100f) / rows.size.toFloat()
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

                val projectCount = maxOf(
                    rows.map { it.projectId }.filter { it.isNotBlank() }.distinct().size,
                    teamPage?.summary?.projectCount ?: 0,
                    managedPage?.summary?.projectCount ?: 0,
                    recruitedPage?.summary?.projectCount ?: 0
                )
                val recruiterCount = maxOf(
                    rows.map { it.recruiterId }.filter { it.isNotBlank() }.distinct().size,
                    teamPage?.summary?.recruiterCount ?: 0,
                    managedPage?.summary?.recruiterCount ?: 0,
                    recruitedPage?.summary?.recruiterCount ?: 0
                )
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

                _generalOverview.value = GeneralOverviewSnapshot(
                    asOfDate = asOfDate,
                    scopeDescription = scopeDescription,
                    totalProfiles = totalProfiles,
                    workingCount = workingCount,
                    waitingCount = waitingCount,
                    leftCount = leftCount,
                    audienceKind = if (isTeamActor) "team" else "own",
                    placementRatePct = placementRatePct,
                    projectCount = projectCount,
                    recruiterCount = recruiterCount,
                    managedTotal = managedPage?.summary?.totalWorkers,
                    recruitedTotal = recruitedPage?.summary?.totalWorkers,
                    teamTotal = teamPage?.summary?.totalWorkers,
                    activeHiringDays = hiringSummary.activeHiringDays,
                    avgPerActiveDay = hiringSummary.avgPerActiveDay,
                    peakDailyCount = hiringSummary.peakDailyCount,
                    peakDateIso = hiringSummary.peakDateIso,
                    leadingProject = leadingProject,
                    byRecruiter = byRecruiter,
                    byProject = byProject,
                    bySecondaryGroup = byLaborType,
                    secondaryGroupTitle = "Cơ cấu theo loại hình lao động",
                    byProvider = byProvider
                )

                if (isTeamActor) {
                    cachedTeamScopeWorkers = teamPage?.items.orEmpty()
                    cachedTeamTotalSummary = teamPage?.summary?.totalWorkers ?: cachedTeamScopeWorkers.size

                    when {
                        teamGrants.isEmpty() -> {
                            _teamScopeOptions.value = emptyList()
                            _teamReport.value = WorkReportsContract.buildTeamReportFromWorkerRows(
                                bucketKey = "grant:team",
                                bucketDisplay = "Nhóm của tôi",
                                rows = cachedTeamScopeWorkers,
                                recruiterCatalogById = recruiterMap,
                                totalWorkersOverride = cachedTeamTotalSummary
                            )
                        }

                        teamGrants.all { teamGrantUuidRegex.matches(it) } -> {
                            val grantSnapshots = teamGrants.map { ref ->
                                buildGrantedTeamSnapshotFromCache(actor, ref)
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
                                    bucketKeyOrTeamCode = selectedGrant
                                )
                            } catch (teamErr: Exception) {
                                teamErr.rethrowIfCancelled()
                                if (teamPage != null) {
                                    _teamReport.value = buildGrantedTeamSnapshotFromCache(actor, selectedGrant)
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
        viewModelScope.launch {
            _workforceLoading.value = true
            try {
                val (moreRows, hasMore, nextCursor) = repository.workReportsAndReportingService
                    .fetchWorkforceProjectPage(
                        cursor = cursor,
                        eligibleTotal = current.eligibleEpisodeTotal
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
                WorkReportsContract.buildTeamReportFromWorkerRows(
                    bucketKey = "grant:team",
                    bucketDisplay = "Nhóm của tôi",
                    rows = cachedTeamScopeWorkers,
                    recruiterCatalogById = recruiterMap,
                    totalWorkersOverride = cachedTeamTotalSummary
                )
            } else {
                buildGrantedTeamSnapshotFromCache(actor, teamRef)
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
                _teamReport.value = buildGrantedTeamSnapshotFromCache(actor, matchedUuidGrant)
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

        _teamReportLoading.value = true
        _teamReportError.value = null
        viewModelScope.launch {
            try {
                val snap = repository.workReportsAndReportingService.fetchTeamReport(
                    bucketKeyOrTeamCode = trimmedKey,
                    fromDate = fromDate,
                    toDate = toDate
                )
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
        _teamReportLoading.value = true
        viewModelScope.launch {
            try {
                val next = repository.workReportsAndReportingService.fetchTeamDrillDownPage(
                    bucketKeyOrTeamCode = current.bucketKey,
                    cursor = cursor
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
                if (e.kind == BackendFailureKind.CONFLICT) {
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
                if (e.kind == BackendFailureKind.CONFLICT) {
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
        if (localPath.isNotEmpty()) {
            viewModelScope.launch {
                repository.directEntryService.deleteLocalAttachment(localPath)
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
            val entryId = entryIds[i]
            for (ref in draft.localPendingAttachments()) {
                val localPath = ref.localFilePath?.trim().orEmpty()
                if (localPath.isNotEmpty()) {
                    tasks.add(
                        PendingEntryAttachmentTask(
                            entryId = entryId,
                            workerDisplayName = draft.fullName.trim(),
                            documentType = ref.documentType,
                            fileName = ref.fileName,
                            mimeType = ref.mimeType,
                            localFilePath = localPath,
                            idempotencyBaseKey = UUID.randomUUID().toString()
                        )
                    )
                }
            }
        }
        return tasks
    }

    internal fun enqueueBackgroundEntryUploads(tasks: List<PendingEntryAttachmentTask>) {
        if (tasks.isEmpty()) return
        _backgroundUploadActiveCount.value = _backgroundUploadActiveCount.value + tasks.size
        viewModelScope.launch {
            backgroundUploadMutex.withLock {
                var succeededCount = 0
                val failedTasks = mutableListOf<PendingEntryAttachmentTask>()
                var lastErrorDetail: String? = null

                val grouped = tasks.groupBy { it.entryId }
                for ((entryId, entryTasks) in grouped) {
                    var knownVersion: Int? = null
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
                            succeededCount++
                        } catch (e: Exception) {
                            e.rethrowIfCancelled()
                            knownVersion = null
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
                    showBanner(
                        "Đã tải ngầm thành công $succeededCount/${tasks.size} ảnh & giấy tờ đính kèm lên hồ sơ NLĐ.",
                        isError = false
                    )
                } else {
                    val merged = (_failedBackgroundUploads.value + failedTasks)
                        .distinctBy { "${it.entryId}:${it.documentType}:${it.localFilePath}" }
                    _failedBackgroundUploads.value = merged
                    _hasFailedBackgroundUploads.value = merged.isNotEmpty()
                    val detailSuffix = lastErrorDetail?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    showBanner(
                        "Tải ảnh đính kèm: thành công $succeededCount/${tasks.size}, lỗi ${failedTasks.size} tệp$detailSuffix. Bấm Thử lại để gửi tiếp.",
                        isError = true
                    )
                }
            }
        }
    }

    fun retryFailedBackgroundUploads() {
        val pendingFailed = _failedBackgroundUploads.value
        if (pendingFailed.isEmpty()) return
        _failedBackgroundUploads.value = emptyList()
        _hasFailedBackgroundUploads.value = false
        showBanner("Đang tải lại ${pendingFailed.size} ảnh & giấy tờ đính kèm…", isError = false)
        enqueueBackgroundEntryUploads(pendingFailed)
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
                val pendingAttachments = buildPendingAttachmentTasks(validDrafts, result.entryIds)
                onDraftsSaved()
                val codesNote = if (result.employeeCodes.isNotEmpty()) {
                    " Mã NLĐ: ${result.employeeCodes.joinToString(", ")}"
                } else {
                    ""
                }
                if (result.state == "SUBMITTED") {
                    refreshOwnSubmissionsForHistory()
                    if (pendingAttachments.isNotEmpty()) {
                        showBanner(
                            "Đã đẩy chính thức ${result.entryIds.size} hồ sơ NLĐ lên hệ thống.$codesNote Đang tải ngầm ${pendingAttachments.size} ảnh & giấy tờ…"
                        )
                        enqueueBackgroundEntryUploads(pendingAttachments)
                    } else {
                        showBanner("Đã đẩy chính thức ${result.entryIds.size} hồ sơ NLĐ lên hệ thống.$codesNote")
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
                val msg = (e as? BackendApiException)?.message
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
            if (pendingAttachments.isNotEmpty()) {
                showBanner("$baseBannerMsg Đang tải ngầm ${pendingAttachments.size} ảnh & giấy tờ…")
                enqueueBackgroundEntryUploads(pendingAttachments)
            } else {
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
                if (pending.pendingAttachments.isNotEmpty()) {
                    showBanner("$baseBannerMsg Đang tải ngầm ${pending.pendingAttachments.size} ảnh & giấy tờ…")
                    enqueueBackgroundEntryUploads(pending.pendingAttachments)
                } else {
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
                        val cached = repository.getCachedWorkersByScope(tabKey)
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

    fun openDirectEdit(worker: WorkerProfileEntity) {
        if (!worker.allowedActions.directEdit) return
        _workerMutationError.value = null
        loadCatalogIfNeeded()
        viewModelScope.launch {
            try {
                val detail = repository.directEntryService.getEntryDetail(worker.entryId)
                _directEditTarget.value = worker to detail
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
        _directEditTarget.value = null
        _workerMutationError.value = null
    }

    fun submitDirectEdit(
        entryId: String,
        expectedEntryVersion: Int,
        patch: JSONObject,
        reason: String?
    ) {
        if (_workerMutationBusy.value) return
        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            try {
                val refreshedDetail = repository.directEntryService.privilegedEditEntry(
                    entryId = entryId,
                    expectedEntryVersion = expectedEntryVersion,
                    patch = patch,
                    reason = reason
                )
                _directEditTarget.value = null
                if (_selectedEntryDetail.value?.entryId == entryId) {
                    _selectedEntryDetail.value = refreshedDetail
                }
                showBanner("Đã cập nhật hồ sơ thành công (phiên bản v${refreshedDetail.version}).")
                selectWorkerTab(_activeWorkerTabKey.value)
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

    companion object {
        internal const val BANNER_AUTO_DISMISS_MS = 10_000L
    }
}
