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
import com.example.data.BiBucketItem
import com.example.data.BiRecruitmentFact
import com.example.data.DuplicateCccdAlert
import com.example.data.MiniBiRepository
import com.example.data.NewWorkerEntryDraft
import com.example.data.ProjectProviderMixItem
import com.example.data.WorkReportObligationEntity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

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
    val isLiveGps: Boolean,
    val capturedAtMillis: Long = System.currentTimeMillis()
)

data class DashboardComputedState(
    val totalRecruited: Int = 0,
    val priorPeriodTotal: Int = 342,
    val deltaPct: Float = 14.6f,
    val activeHiringDays: Int = 0,
    val avgPerActiveDay: Float = 0f,
    val peakDayDate: String = "—",
    val peakDayCount: Int = 0,
    val hrpSharePct: Float = 0f,
    val vendorSharePct: Float = 0f,
    val officialSharePct: Float = 0f,
    val seasonalSharePct: Float = 0f,
    val dailyTrend: List<Pair<String, Int>> = emptyList(),
    val byProject: List<BiBucketItem> = emptyList(),
    val byRecruiter: List<BiBucketItem> = emptyList(),
    val byTeam: List<BiBucketItem> = emptyList(),
    val projectProviderMix: List<ProjectProviderMixItem> = emptyList(),
    val workforceDeep: WorkforceDeepMetrics,
    val duplicateCccdAlerts: List<DuplicateCccdAlert>
)

class MiniBiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MiniBiRepository(application)

    val themeId: StateFlow<String> = repository.themeIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "hr-partner")

    val serverUrl: StateFlow<String> = repository.serverUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniBiRepository.DEFAULT_SERVER_URL)

    val currentActor: StateFlow<AppActor?> = repository.currentActorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentDestination = MutableStateFlow(MainDestination.DASHBOARD)
    val currentDestination: StateFlow<MainDestination> = _currentDestination.asStateFlow()

    // Login state
    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Dashboard Filter State
    private val _selectedProjectFilter = MutableStateFlow<String?>(null)
    val selectedProjectFilter: StateFlow<String?> = _selectedProjectFilter.asStateFlow()

    private val _selectedProviderFilter = MutableStateFlow<String?>(null)
    val selectedProviderFilter: StateFlow<String?> = _selectedProviderFilter.asStateFlow()

    private val _selectedTeamFilter = MutableStateFlow<String?>(null)
    val selectedTeamFilter: StateFlow<String?> = _selectedTeamFilter.asStateFlow()

    private val _highlightedDimensionKey = MutableStateFlow<String?>(null)
    val highlightedDimensionKey: StateFlow<String?> = _highlightedDimensionKey.asStateFlow()

    // GPS & Camera Attendance State
    private val _gpsSnapshot = MutableStateFlow<GpsSnapshot?>(null)
    val gpsSnapshot: StateFlow<GpsSnapshot?> = _gpsSnapshot.asStateFlow()

    private val _gpsLoading = MutableStateFlow(false)
    val gpsLoading: StateFlow<Boolean> = _gpsLoading.asStateFlow()

    private val _submittingAttendance = MutableStateFlow(false)
    val submittingAttendance: StateFlow<Boolean> = _submittingAttendance.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val attendanceRecords: StateFlow<List<AttendanceEntity>> = repository.attendanceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workReports: StateFlow<List<WorkReportObligationEntity>> = repository.workReportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workerProfiles: StateFlow<List<WorkerProfileEntity>> = repository.workersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allFacts: List<BiRecruitmentFact> = repository.getBiRecruitmentFacts()
    private val workforceMetrics = repository.getWorkforceDeepMetrics()
    private val duplicateAlerts = repository.getDuplicateCccdAlerts()

    val dashboardState: StateFlow<DashboardComputedState> = combine(
        _selectedProjectFilter,
        _selectedProviderFilter,
        _selectedTeamFilter,
        currentActor
    ) { projectKey, providerKey, teamCode, actor ->
        computeDashboardState(projectKey, providerKey, teamCode, actor)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        computeDashboardState(null, null, null, null)
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedDataLoaded()
        }
    }

    fun navigateTo(destination: MainDestination) {
        _currentDestination.value = destination
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            repository.setThemeId(themeId)
        }
    }

    fun clearBanner() {
        _statusBannerMessage.value = null
    }

    fun showBanner(message: String) {
        _statusBannerMessage.value = message
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
                _loginError.value = when (err.message) {
                    "AUTH_REQUEST_INVALID" -> "Vui lòng nhập đầy đủ tài khoản và mật khẩu."
                    "AUTH_INVALID_CREDENTIALS" -> "Tài khoản hoặc mật khẩu không đúng."
                    else -> "Không thể kết nối đăng nhập. Vui lòng thử lại."
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _currentDestination.value = MainDestination.DASHBOARD
        }
    }

    fun setProjectFilter(projectKey: String?) {
        _selectedProjectFilter.value = if (_selectedProjectFilter.value == projectKey) null else projectKey
    }

    fun setProviderFilter(providerKey: String?) {
        _selectedProviderFilter.value = if (_selectedProviderFilter.value == providerKey) null else providerKey
    }

    fun setTeamFilter(teamCode: String?) {
        _selectedTeamFilter.value = if (_selectedTeamFilter.value == teamCode) null else teamCode
    }

    fun toggleHighlightKey(key: String?) {
        _highlightedDimensionKey.value = if (_highlightedDimensionKey.value == key) null else key
    }

    fun clearAllDashboardFilters() {
        _selectedProjectFilter.value = null
        _selectedProviderFilter.value = null
        _selectedTeamFilter.value = null
        _highlightedDimensionKey.value = null
    }

    private fun computeDashboardState(
        projectKey: String?,
        providerKey: String?,
        teamCode: String?,
        actor: AppActor?
    ): DashboardComputedState {
        val filtered = allFacts.filter { fact ->
            val matchProject = projectKey == null || fact.projectKey == projectKey
            val matchProvider = providerKey == null || fact.providerTypeKey == providerKey
            val matchTeam = teamCode == null || fact.teamCode == teamCode
            matchProject && matchProvider && matchTeam
        }

        val total = filtered.sumOf { it.recruitedCount }
        val byDateMap = filtered.groupBy { it.businessDate }
            .mapValues { entry -> entry.value.sumOf { it.recruitedCount } }
            .toSortedMap()

        val activeDays = byDateMap.count { it.value > 0 }
        val avgPerDay = if (activeDays > 0) (total.toFloat() / activeDays) else 0f
        val peakEntry = byDateMap.maxByOrNull { it.value }

        val hrpCount = filtered.filter { it.providerTypeKey == "hrp" }.sumOf { it.recruitedCount }
        val vendorCount = filtered.filter { it.providerTypeKey == "vendor" }.sumOf { it.recruitedCount }
        val hrpShare = if (total > 0) (hrpCount * 100f / total) else 0f
        val vendorShare = if (total > 0) (vendorCount * 100f / total) else 0f

        val officialCount = filtered.filter { it.employmentTypeKey == "chính thức" }.sumOf { it.recruitedCount }
        val seasonalCount = filtered.filter { it.employmentTypeKey == "thời vụ" }.sumOf { it.recruitedCount }
        val officialShare = if (total > 0) (officialCount * 100f / total) else 0f
        val seasonalShare = if (total > 0) (seasonalCount * 100f / total) else 0f

        val byProject = filtered.groupBy { it.projectKey }
            .map { (key, list) ->
                val sum = list.sumOf { it.recruitedCount }
                BiBucketItem(
                    key = key,
                    display = list.first().projectDisplay,
                    count = sum,
                    sharePct = if (total > 0) (sum * 100f / total) else 0f
                )
            }
            .sortedByDescending { it.count }

        val byRecruiter = filtered.groupBy { it.recruiterKey }
            .map { (key, list) ->
                val sum = list.sumOf { it.recruitedCount }
                BiBucketItem(
                    key = key,
                    display = list.first().recruiterDisplay,
                    count = sum,
                    sharePct = if (total > 0) (sum * 100f / total) else 0f
                )
            }
            .sortedByDescending { it.count }

        val byTeam = filtered.groupBy { it.teamCode }
            .map { (key, list) ->
                val sum = list.sumOf { it.recruitedCount }
                BiBucketItem(
                    key = key,
                    display = list.first().teamDisplayName,
                    count = sum,
                    sharePct = if (total > 0) (sum * 100f / total) else 0f
                )
            }
            .sortedByDescending { it.count }

        val mixList = filtered.groupBy { it.projectKey }
            .map { (pKey, list) ->
                val pTotal = list.sumOf { it.recruitedCount }
                val pHrp = list.filter { it.providerTypeKey == "hrp" }.sumOf { it.recruitedCount }
                val pVendor = list.filter { it.providerTypeKey == "vendor" }.sumOf { it.recruitedCount }
                ProjectProviderMixItem(
                    projectKey = pKey,
                    projectDisplay = list.first().projectDisplay,
                    total = pTotal,
                    hrpCount = pHrp,
                    vendorCount = pVendor,
                    hrpShare = if (pTotal > 0) (pHrp * 100f / pTotal) else 0f,
                    vendorShare = if (pTotal > 0) (pVendor * 100f / pTotal) else 0f
                )
            }
            .sortedByDescending { it.total }

        val priorTotal = 342
        val deltaPct = if (priorTotal > 0) ((total - priorTotal) * 100f / priorTotal) else 0f

        return DashboardComputedState(
            totalRecruited = total,
            priorPeriodTotal = priorTotal,
            deltaPct = (deltaPct * 10f).roundToInt() / 10f,
            activeHiringDays = activeDays,
            avgPerActiveDay = (avgPerDay * 10f).roundToInt() / 10f,
            peakDayDate = peakEntry?.key ?: "—",
            peakDayCount = peakEntry?.value ?: 0,
            hrpSharePct = (hrpShare * 10f).roundToInt() / 10f,
            vendorSharePct = (vendorShare * 10f).roundToInt() / 10f,
            officialSharePct = (officialShare * 10f).roundToInt() / 10f,
            seasonalSharePct = (seasonalShare * 10f).roundToInt() / 10f,
            dailyTrend = byDateMap.entries.map { it.key to it.value },
            byProject = byProject,
            byRecruiter = byRecruiter,
            byTeam = byTeam,
            projectProviderMix = mixList,
            workforceDeep = workforceMetrics,
            duplicateCccdAlerts = duplicateAlerts
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentGpsLocation(onReady: ((GpsSnapshot) -> Unit)? = null) {
        val appContext = getApplication<Application>()
        _gpsLoading.value = true
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
                            accuracyMeters = location.accuracy.coerceAtLeast(3.5f),
                            addressHint = address,
                            isLiveGps = true
                        )
                    } else {
                        GpsSnapshot(
                            latitude = 21.028511,
                            longitude = 105.804817,
                            accuracyMeters = 8.4f,
                            addressHint = "Văn phòng điều hành HR Partner (Cầu Giấy, Hà Nội)",
                            isLiveGps = false
                        )
                    }
                } catch (_: Exception) {
                    GpsSnapshot(
                        latitude = 21.028511,
                        longitude = 105.804817,
                        accuracyMeters = 8.4f,
                        addressHint = "Văn phòng điều hành HR Partner (Cầu Giấy, Hà Nội)",
                        isLiveGps = false
                    )
                }
            }
            _gpsSnapshot.value = snapshot
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
            "Vị trí định vị GPS: %.5f, %.5f".format(Locale.US, lat, lng)
        }
    }

    fun submitCheckInWithPhoto(
        checkInType: String,
        photoFile: File,
        photoUriString: String,
        note: String,
        onComplete: (AttendanceEntity) -> Unit
    ) {
        val actor = currentActor.value ?: return
        val currentGps = _gpsSnapshot.value ?: GpsSnapshot(
            latitude = 21.028511,
            longitude = 105.804817,
            accuracyMeters = 8.4f,
            addressHint = "Văn phòng điều hành HR Partner (Cầu Giấy, Hà Nội)",
            isLiveGps = false
        )
        _submittingAttendance.value = true
        viewModelScope.launch {
            val saved = repository.submitAttendanceCheckIn(
                actor = actor,
                checkInType = checkInType,
                latitude = currentGps.latitude,
                longitude = currentGps.longitude,
                accuracyMeters = currentGps.accuracyMeters,
                addressHint = currentGps.addressHint,
                photoFile = photoFile,
                photoUriString = photoUriString,
                note = note
            )
            _submittingAttendance.value = false
            showBanner("Đã chấm công thành công lúc ${saved.clientTimestampIso.take(19).replace('T', ' ')}")
            onComplete(saved)
        }
    }

    fun saveWorkReport(
        obligation: WorkReportObligationEntity,
        callsMade: Int,
        interestedCandidates: Int,
        workersStarted: Int,
        notes: String,
        submitFinal: Boolean
    ) {
        viewModelScope.launch {
            repository.saveWorkReportSubmission(
                obligation = obligation,
                callsMade = callsMade,
                interestedCandidates = interestedCandidates,
                workersStarted = workersStarted,
                notes = notes,
                submitFinal = submitFinal
            )
            showBanner(if (submitFinal) "Đã nộp báo cáo công việc ngày ${obligation.periodKey}!" else "Đã lưu bản nháp báo cáo ngày ${obligation.periodKey}.")
        }
    }

    fun createNewDailyReport(
        callsMade: Int,
        interestedCandidates: Int,
        workersStarted: Int,
        notes: String,
        submitFinal: Boolean
    ) {
        val actor = currentActor.value ?: return
        viewModelScope.launch {
            repository.createNewDailyReportForToday(
                actor = actor,
                callsMade = callsMade,
                interestedCandidates = interestedCandidates,
                workersStarted = workersStarted,
                notes = notes,
                submitFinal = submitFinal
            )
            showBanner(if (submitFinal) "Đã tạo & nộp báo cáo công việc hôm nay!" else "Đã tạo bản nháp báo cáo hôm nay.")
        }
    }

    fun reviewReport(
        obligation: WorkReportObligationEntity,
        approved: Boolean,
        reason: String
    ) {
        val actor = currentActor.value ?: return
        viewModelScope.launch {
            repository.reviewWorkReport(
                obligation = obligation,
                reviewerName = actor.displayName,
                approved = approved,
                reason = reason
            )
            showBanner(if (approved) "Đã phê duyệt báo cáo của ${obligation.assigneeDisplayName}" else "Đã yêu cầu chỉnh sửa báo cáo của ${obligation.assigneeDisplayName}")
        }
    }

    // Save valid workers from "Cấp mới" (Direct Entry Multi-Entry Form)
    fun saveNewWorkersBatch(validDrafts: List<NewWorkerEntryDraft>) {
        val actor = currentActor.value ?: return
        if (validDrafts.isEmpty()) return
        viewModelScope.launch {
            val entities = validDrafts.mapIndexed { idx, draft ->
                val num = (900..999).random() + idx
                val code = "${draft.providerType.lowercase()}-2026-000$num"
                val rawCccd = draft.cccd.trim().ifBlank { "027099001234" }
                val rawPhone = draft.phone.trim().ifBlank { "0988123456" }
                val rawBank = draft.bankAccountNumber.trim()
                val cccdMasked = if (rawCccd.length >= 9) rawCccd.take(6) + "***" + rawCccd.takeLast(3) else rawCccd
                val phoneMasked = if (rawPhone.length >= 7) rawPhone.take(4) + "***" + rawPhone.takeLast(3) else rawPhone
                val bankMasked = if (rawBank.length >= 6) rawBank.take(4) + "***" + rawBank.takeLast(4) else rawBank.ifBlank { "Chưa cập nhật" }

                WorkerProfileEntity(
                    workerCode = code,
                    fullName = draft.fullName.trim(),
                    gender = draft.gender.ifBlank { "Nam" },
                    cccdMasked = cccdMasked,
                    cccdFull = rawCccd,
                    phoneMasked = phoneMasked,
                    phoneFull = rawPhone,
                    projectDisplay = draft.projectDisplay,
                    recruiterDisplay = draft.recruiterOrVendor.ifBlank { actor.displayName },
                    teamDisplay = actor.teamName,
                    providerType = draft.providerType.ifBlank { "HRP" },
                    laborType = draft.laborType.ifBlank { "Chính thức" },
                    employmentStatus = draft.employmentStatus.ifBlank { "Đang làm" },
                    firstWorkDate = draft.firstWorkDate.ifBlank { "23/10/2026" },
                    bankName = draft.bankName.ifBlank { "Chưa cập nhật" },
                    bankAccountMasked = bankMasked,
                    bankAccountNumber = rawBank,
                    bankAccountHolder = draft.bankAccountHolder.trim().uppercase(),
                    note = draft.note.trim(),
                    birthDate = draft.birthDate.trim(),
                    cccdIssueDate = draft.cccdIssueDate.trim(),
                    address = draft.address.trim(),
                    portraitUri = draft.portraitFileName.ifBlank { draft.portraitUri },
                    cccdFrontUri = draft.cccdFrontFileName.ifBlank { draft.cccdFrontUri },
                    cccdBackUri = draft.cccdBackFileName.ifBlank { draft.cccdBackUri },
                    contractUri = draft.contractFileName.ifBlank { draft.contractUri },
                    scopeRelation = "managed",
                    pendingRequestSummary = "",
                    lastDecisionSummary = "Vừa cấp mới · 23/10/2026"
                )
            }
            repository.batchInsertWorkers(entities)
            showBanner("Đã lưu thành công ${entities.size} hồ sơ NLĐ mới vào phần mềm!")
        }
    }

    // Propose a change (`Đề xuất thay đổi`) on a worker in `/direct-entry/workers`
    fun proposeWorkerChange(
        worker: WorkerProfileEntity,
        targetLabel: String,
        proposedSummary: String,
        reason: String
    ) {
        viewModelScope.launch {
            val updated = worker.copy(
                pendingRequestSummary = "Đang chờ duyệt ($targetLabel)",
                note = if (reason.isNotBlank()) "${worker.note} | Đề xuất: $proposedSummary ($reason)".trim(' ', '|') else worker.note
            )
            repository.addWorkerProfile(updated)
            showBanner("Đã gửi đề xuất thay đổi ($targetLabel) cho ${worker.fullName}.")
        }
    }

    // Direct Edit (`Sửa trực tiếp`) on a worker in `/direct-entry/workers`
    fun directEditWorker(
        worker: WorkerProfileEntity,
        fullName: String,
        gender: String,
        projectDisplay: String,
        employmentStatus: String,
        phoneFull: String,
        cccdFull: String,
        bankName: String,
        bankAccountNumber: String,
        bankAccountHolder: String,
        note: String
    ) {
        viewModelScope.launch {
            val cccdMasked = if (cccdFull.length >= 9) cccdFull.take(6) + "***" + cccdFull.takeLast(3) else cccdFull
            val phoneMasked = if (phoneFull.length >= 7) phoneFull.take(4) + "***" + phoneFull.takeLast(3) else phoneFull
            val bankMasked = if (bankAccountNumber.length >= 6) bankAccountNumber.take(4) + "***" + bankAccountNumber.takeLast(4) else bankAccountNumber
            val updated = worker.copy(
                fullName = fullName.trim().ifBlank { worker.fullName },
                gender = gender.ifBlank { worker.gender },
                projectDisplay = projectDisplay.ifBlank { worker.projectDisplay },
                employmentStatus = employmentStatus.ifBlank { worker.employmentStatus },
                phoneFull = phoneFull,
                phoneMasked = phoneMasked.ifBlank { worker.phoneMasked },
                cccdFull = cccdFull,
                cccdMasked = cccdMasked.ifBlank { worker.cccdMasked },
                bankName = bankName.ifBlank { worker.bankName },
                bankAccountNumber = bankAccountNumber,
                bankAccountMasked = bankMasked.ifBlank { worker.bankAccountMasked },
                bankAccountHolder = bankAccountHolder.trim().uppercase(),
                note = note.trim(),
                lastDecisionSummary = "Đã sửa trực tiếp · 23/10/2026"
            )
            repository.addWorkerProfile(updated)
            showBanner("Đã cập nhật trực tiếp thông tin của ${updated.fullName}.")
        }
    }
}
