package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppActor
import com.example.data.AttendanceEntity
import com.example.data.BackendApiException
import com.example.data.DirectEntryCatalog
import com.example.data.DuplicateCccdGroupItem
import com.example.data.MiniBiRepository
import com.example.data.OwnSubmissionItem
import com.example.data.TeamReportSnapshot
import com.example.data.WorkReportAnalyticsSnapshot
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkReportReviewItem
import com.example.data.WorkerEntryDetail
import com.example.data.WorkerProfileEntity
import com.example.data.WorkforceDeepMetrics
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    val themeId: StateFlow<String> = repository.themeIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "hr-partner")

    val serverUrl: StateFlow<String> = repository.serverUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniBiRepository.DEFAULT_SERVER_URL)

    val currentActor: StateFlow<AppActor?> = repository.currentActorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sessionBootstrapping = MutableStateFlow(false)
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

    private val _selectedEntryDetail = MutableStateFlow<WorkerEntryDetail?>(null)
    val selectedEntryDetail: StateFlow<WorkerEntryDetail?> = _selectedEntryDetail.asStateFlow()

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

    fun navigateTo(destination: MainDestination) {
        _currentDestination.value = destination
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            repository.setThemeId(themeId)
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            repository.setServerUrl(url)
            showBanner("Đã cập nhật URL máy chủ.")
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
            _teamReport.value = null
            _duplicateGroups.value = emptyList()
            _catalog.value = null
            _workerRows.value = emptyList()
            _ownSubmissions.value = emptyList()
            _selectedEntryDetail.value = null
            _reviewQueue.value = emptyList()
            _analyticsSnapshot.value = null
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
}
