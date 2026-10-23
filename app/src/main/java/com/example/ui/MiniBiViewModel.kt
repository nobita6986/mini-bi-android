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
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.DirectEntryCatalog
import com.example.data.DirectEntryContract
import com.example.data.DuplicateCccdGroupItem
import com.example.data.MiniBiRepository
import com.example.data.NewWorkerEntryDraft
import com.example.data.OwnSubmissionItem
import com.example.data.StagedDocumentRef
import com.example.data.TeamReportSnapshot
import com.example.data.WorkReportAnalyticsSnapshot
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportReviewItem
import com.example.data.WorkerDirectorySummary
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.data.WorkforceDeepMetrics
import com.example.ui.screens.resolveVisibleWorkerTabs
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
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

class MiniBiViewModel(application: Application) : AndroidViewModel(application) {

    internal val repository = MiniBiRepository(application)

    val imageLoader: ImageLoader
        get() = repository.imageLoader

    val themeId: StateFlow<String> = repository.themeIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "hr-partner")

    val serverUrl: StateFlow<String> = repository.serverUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BuildConfig.DEFAULT_SERVER_URL)

    val currentActor: StateFlow<AppActor?> = repository.currentActorFlow

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

    // Reporting & Dashboard States (Backend authority only; zero seed data)
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

    private var workerFetchJob: Job? = null
    private var lastSearchQuery: String = ""
    private var lastFilterProjectId: String? = null
    private var lastFilterRecruiterId: String? = null
    private var lastFilterStatus: String? = null

    // Work Reports States (Backend authority only; zero seed data)
    val workReports: StateFlow<List<WorkReportObligationEntity>> = repository.workReportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _workReportsLoading = MutableStateFlow(false)
    val workReportsLoading: StateFlow<Boolean> = _workReportsLoading.asStateFlow()

    private val _workReportsError = MutableStateFlow<String?>(null)
    val workReportsError: StateFlow<String?> = _workReportsError.asStateFlow()

    private val _reviewQueue = MutableStateFlow<List<WorkReportReviewItem>>(emptyList())
    val reviewQueue: StateFlow<List<WorkReportReviewItem>> = _reviewQueue.asStateFlow()

    private val _analyticsSnapshot = MutableStateFlow<WorkReportAnalyticsSnapshot?>(null)
    val analyticsSnapshot: StateFlow<WorkReportAnalyticsSnapshot?> = _analyticsSnapshot.asStateFlow()

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

    val attendanceRecords: StateFlow<List<AttendanceEntity>> = repository.attendanceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        bootstrapSession()
    }

    fun bootstrapSession() {
        _sessionBootstrapping.value = true
        viewModelScope.launch {
            val res = repository.bootstrapSession()
            _sessionBootstrapping.value = false
            res.onSuccess { actor ->
                refreshDashboardData(actor)
            }
        }
    }

    fun navigateTo(destination: MainDestination) {
        _currentDestination.value = destination
        val actor = currentActor.value ?: return
        when (destination) {
            MainDestination.DASHBOARD -> {
                if (_duplicateGroups.value.isEmpty() && !_duplicateLoading.value) {
                    refreshDashboardData(actor)
                }
            }
            MainDestination.WORK_REPORTS -> {
                refreshWorkReportsData(actor)
            }
            MainDestination.NEW_ENTRY -> {
                loadCatalogIfNeeded()
                if (actor.hasCapability("submission_read")) {
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

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            repository.setServerUrl(url)
                .onSuccess { showBanner("Đã cập nhật URL máy chủ: $it") }
                .onFailure { err -> showBanner(err.message ?: "URL máy chủ không hợp lệ.", isError = true) }
        }
    }

    fun clearBanner() {
        _statusBannerMessage.value = null
        _statusBannerIsError.value = false
    }

    fun showBanner(message: String, isError: Boolean = false) {
        _statusBannerMessage.value = message
        _statusBannerIsError.value = isError
    }

    fun login(identifier: String, password: String) {
        if (_loginBusy.value) return
        _loginBusy.value = true
        _loginError.value = null
        viewModelScope.launch {
            val result = repository.login(identifier, password)
            _loginBusy.value = false
            result.onSuccess { actor ->
                _loginError.value = null
                showBanner("Xin chào ${actor.displayName}")
                refreshDashboardData(actor)
            }.onFailure { err ->
                _loginError.value = (err as? BackendApiException)?.message
                    ?: err.message
                    ?: "Không thể kết nối đăng nhập. Vui lòng thử lại."
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _currentDestination.value = MainDestination.DASHBOARD
            _workforceDeep.value = null
            _workforceError.value = null
            _teamReport.value = null
            _teamReportError.value = null
            _duplicateGroups.value = emptyList()
            _duplicateError.value = null
            _duplicatePage.value = 1
            _duplicateHasMore.value = false
            _duplicateManagedOnly.value = false
            _catalog.value = null
            _workerRows.value = emptyList()
            _ownSubmissions.value = emptyList()
            _workerSummary.value = null
            _workerHasMore.value = false
            _workerNextCursor.value = null
            _submissionsHasMore.value = false
            _submissionsNextCursor.value = null
            _selectedEntryDetail.value = null
            _proposeChangeTarget.value = null
            _directEditTarget.value = null
            _workerMutationError.value = null
            _editingObligation.value = null
            _workReportsError.value = null
            _reviewQueue.value = emptyList()
            _analyticsSnapshot.value = null
        }
    }

    // =========================================================================
    // Reporting, Dashboard & Duplicate CCCD Actions (Backend authority only)
    // =========================================================================

    fun refreshDashboardData(actor: AppActor? = currentActor.value) {
        val resolvedActor = actor ?: return
        refreshDuplicateCccdReport(page = _duplicatePage.value, managedOnly = _duplicateManagedOnly.value)
        if (resolvedActor.hasScopeKind("all")) {
            refreshWorkforceDeep()
        } else {
            _workforceDeep.value = null
            _workforceError.value = null
        }
    }

    fun refreshWorkforceDeep(fromDate: String? = null, toDate: String? = null) {
        val actor = currentActor.value ?: return
        if (!actor.hasScopeKind("all")) return
        _workforceLoading.value = true
        _workforceError.value = null
        viewModelScope.launch {
            try {
                val metrics = repository.workReportsAndReportingService.fetchWorkforceDeep(fromDate, toDate)
                _workforceDeep.value = metrics
                // Auto-select first valid team bucket when none selected yet
                if (_teamReport.value == null) {
                    val candidateBucket = metrics.byTeam.firstOrNull { it.key != "unknown" }
                        ?: metrics.byTeam.firstOrNull()
                    if (candidateBucket != null) {
                        loadTeamReport(candidateBucket.key, fromDate, toDate)
                    }
                }
            } catch (e: Exception) {
                _workforceError.value = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải báo cáo chuyên sâu lực lượng lao động."
            } finally {
                _workforceLoading.value = false
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
        _teamReportLoading.value = true
        _teamReportError.value = null
        viewModelScope.launch {
            try {
                val snap = repository.workReportsAndReportingService.fetchTeamReport(
                    bucketKeyOrTeamCode = bucketKeyOrTeamCode,
                    fromDate = fromDate,
                    toDate = toDate
                )
                _teamReport.value = snap
            } catch (e: Exception) {
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
                showBanner(e.message ?: "Không thể rút hồ sơ trùng CCCD.", isError = true)
            }
        }
    }

    // =========================================================================
    // Work Reports Actions (Obligations, Draft/Submit, Reviews, Analytics)
    // =========================================================================

    fun refreshWorkReportsData(actor: AppActor? = currentActor.value) {
        val resolvedActor = actor ?: return
        _workReportsLoading.value = true
        _workReportsError.value = null
        viewModelScope.launch {
            var loadedObligations: List<WorkReportObligationEntity> = emptyList()
            try {
                loadedObligations = repository.workReportsAndReportingService.fetchMyObligations()
            } catch (e: Exception) {
                _workReportsError.value = (e as? BackendApiException)?.message
                    ?: e.message
                    ?: "Không thể tải nghĩa vụ báo cáo từ máy chủ."
            }

            val canReview = resolvedActor.hasCapability("report_review") &&
                (resolvedActor.hasScopeKind("team") || resolvedActor.hasScopeKind("all"))
            val loadedReviews = if (canReview) {
                runCatching { repository.workReportsAndReportingService.fetchPendingReviews() }
                    .onFailure { err ->
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
                val existingTemplateId = _analyticsSnapshot.value?.templateId
                val candidateTemplate = when {
                    !existingTemplateId.isNullOrBlank() -> existingTemplateId to ""
                    loadedObligations.isNotEmpty() -> loadedObligations.first().templateId to loadedObligations.first().templateName
                    loadedReviews.isNotEmpty() -> loadedReviews.first().templateId to loadedReviews.first().templateName
                    else -> null
                }
                if (candidateTemplate != null && candidateTemplate.first.isNotBlank()) {
                    runCatching {
                        repository.workReportsAndReportingService.fetchAnalyticsSnapshot(
                            templateId = candidateTemplate.first,
                            templateNameHint = candidateTemplate.second
                        )
                    }.onSuccess { snap ->
                        _analyticsSnapshot.value = snap
                    }
                }
            } else {
                _analyticsSnapshot.value = null
            }

            _workReportsLoading.value = false
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
                val updated = repository.workReportsAndReportingService.saveDraft(
                    obligationId = obligation.obligationId,
                    expectedVersion = obligation.submissionVersion,
                    payloadJson = payloadJson,
                    fallback = obligation
                )
                _editingObligation.value = null
                showBanner("Đã lưu bản nháp báo cáo lên máy chủ (v${updated.submissionVersion ?: 1}).")
            } catch (e: BackendApiException) {
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT) {
                    val refreshed = runCatching {
                        repository.workReportsAndReportingService.fetchObligationDetail(
                            obligationId = obligation.obligationId,
                            fallback = obligation
                        )
                    }.getOrNull()
                    if (refreshed != null) {
                        _editingObligation.value = refreshed
                    }
                }
            } catch (e: Exception) {
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
                showBanner("Đã nộp báo cáo lên máy chủ (trạng thái: ${updated.submissionState}).")
            } catch (e: BackendApiException) {
                showBanner(e.message, isError = true)
                if (e.kind == BackendFailureKind.CONFLICT) {
                    val refreshed = runCatching {
                        repository.workReportsAndReportingService.fetchObligationDetail(
                            obligationId = obligation.obligationId,
                            fallback = obligation
                        )
                    }.getOrNull()
                    if (refreshed != null) {
                        _editingObligation.value = refreshed
                    }
                }
            } catch (e: Exception) {
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
                showBanner(e.message ?: "Không thể thực hiện phê duyệt báo cáo.", isError = true)
            } finally {
                _workReportMutationBusy.value = false
            }
        }
    }

    fun loadWorkReportAnalytics(templateId: String, templateNameHint: String = "") {
        val actor = currentActor.value ?: return
        if (!actor.hasCapability("report_analytics_view")) return
        _workReportsLoading.value = true
        viewModelScope.launch {
            try {
                val snap = repository.workReportsAndReportingService.fetchAnalyticsSnapshot(
                    templateId = templateId,
                    templateNameHint = templateNameHint
                )
                _analyticsSnapshot.value = snap
                _workReportsError.value = null
            } catch (e: Exception) {
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể tải số liệu phân tích báo cáo.",
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
            } catch (_: Exception) {
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
                onError(
                    (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Tải hồ sơ lên máy chủ thất bại."
                )
            }
        }
    }

    fun deleteStageDocument(stageId: String) {
        viewModelScope.launch {
            try {
                repository.directEntryService.deleteStage(stageId)
            } catch (_: Exception) {
                // Best-effort stage cleanup when user discards/replaces draft attachment
            }
        }
    }

    fun submitFullProfileDrafts(
        validDrafts: List<NewWorkerEntryDraft>,
        onSuccess: () -> Unit
    ) {
        if (_workerMutationBusy.value) return
        _workerMutationBusy.value = true
        _workerMutationError.value = null
        viewModelScope.launch {
            try {
                val result = repository.directEntryService.submitFullProfileBatch(validDrafts)
                val codesNote = if (result.employeeCodes.isNotEmpty()) {
                    " Mã NLĐ: ${result.employeeCodes.joinToString(", ")}"
                } else {
                    ""
                }
                showBanner("Đã lưu ${result.entryIds.size} hồ sơ lên máy chủ.$codesNote")
                refreshOwnSubmissionsForHistory()
                onSuccess()
            } catch (e: Exception) {
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

        workerFetchJob?.cancel()
        workerFetchJob = viewModelScope.launch {
            _workerLoading.value = true
            _workerError.value = null
            if (tabKey == "uploader") {
                try {
                    val page = repository.directEntryService.listOwnSubmissions(pageSize = 20, cursor = null)
                    _ownSubmissions.value = page.items
                    _submissionsHasMore.value = page.hasMore
                    _submissionsNextCursor.value = page.nextCursor
                } catch (e: Exception) {
                    _ownSubmissions.value = emptyList()
                    _submissionsHasMore.value = false
                    _submissionsNextCursor.value = null
                    _workerError.value = (e as? BackendApiException)?.message
                        ?: e.message
                        ?: "Không thể tải lịch sử đợt nhập liệu."
                } finally {
                    _workerLoading.value = false
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
                    _workerRows.value = page.items
                    _workerHasMore.value = page.hasMore
                    _workerNextCursor.value = page.nextCursor
                    _workerSummary.value = page.summary
                } catch (e: BackendApiException) {
                    if (allowFallbackFromManaged && tabKey == "managed" && e.kind == BackendFailureKind.FORBIDDEN) {
                        val actor = currentActor.value
                        val fallbackTab = actor?.let { resolveVisibleWorkerTabs(it) }
                            ?.firstOrNull { it.key != "managed" }
                        if (fallbackTab != null) {
                            _workerLoading.value = false
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
                    if (e.kind == BackendFailureKind.NETWORK) {
                        val cached = repository.getCachedWorkersByScope(tabKey)
                        _workerRows.value = cached
                    } else {
                        _workerRows.value = emptyList()
                    }
                    _workerHasMore.value = false
                    _workerNextCursor.value = null
                    _workerError.value = e.message
                } catch (e: Exception) {
                    _workerRows.value = emptyList()
                    _workerHasMore.value = false
                    _workerNextCursor.value = null
                    _workerError.value = e.message ?: "Không thể tải danh sách người lao động."
                } finally {
                    _workerLoading.value = false
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
                runCatching { repository.directEntryService.getEntryDetail(worker.entryId) }.getOrNull()
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
                        val refreshed = runCatching { repository.directEntryService.getEntryDetail(entryId) }.getOrNull()
                        if (refreshed != null) {
                            _proposeChangeTarget.value = currentWorker.copy(entryVersion = refreshed.version) to refreshed
                        }
                    }
                    selectWorkerTab(_activeWorkerTabKey.value)
                }
            } catch (e: Exception) {
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
                    val latest = runCatching { repository.directEntryService.getEntryDetail(entryId) }.getOrNull()
                    if (currentWorker != null && latest != null) {
                        _directEditTarget.value = currentWorker.copy(entryVersion = latest.version) to latest
                    }
                    selectWorkerTab(_activeWorkerTabKey.value)
                }
            } catch (e: Exception) {
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
                } catch (_: Exception) {
                    null
                }
            }
            _gpsSnapshot.value = snapshot
            if (snapshot == null) {
                _gpsError.value = "Không lấy được tọa độ GPS thực tế từ thiết bị. Vui lòng bật Dịch vụ Vị trí."
            }
            _gpsLoading.value = false
            onReady?.invoke(snapshot)
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

    fun submitAttendanceCheckIn(
        photoFile: File,
        photoUri: String?,
        note: String
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
                    note = note
                )
                // Only `SYNCED` has isError = false with "Đã chấm công"; offline QUEUED has "Đã lưu trên thiết bị, đang chờ đồng bộ"
                showBanner(outcome.userMessage, isError = !outcome.isSynced)
            } catch (e: Exception) {
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
                showBanner(
                    (e as? BackendApiException)?.message ?: "Không thể đồng bộ hàng đợi chấm công.",
                    isError = true
                )
            } finally {
                _submittingAttendance.value = false
            }
        }
    }
}
