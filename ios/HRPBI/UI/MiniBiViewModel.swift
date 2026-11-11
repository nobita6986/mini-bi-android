import Foundation
import Combine

// MARK: - Root ViewModel (Single Source of Truth for App State)
// Mirrors Android's `ui/MiniBiViewModel.kt` 100%

@MainActor
public final class MiniBiViewModel: ObservableObject {
    // MARK: - Dependencies
    public let repository: MiniBiRepository
    public let directEntryService: DirectEntryService
    public let workReportsService: WorkReportsAndReportingService
    public let attendanceOutboxService: AttendanceOutboxService
    public let timeAnchorManager: AttendanceTimeAnchorManager
    public let notificationService: NotificationService
    public let leaveService: LeaveService
    public let attendanceExplanationService: AttendanceExplanationService
    public let salaryAdvanceService: SalaryAdvanceService
    public let recruitmentPostService: RecruitmentPostService
    public let taskReminderManager: TaskReminderManager

    // MARK: - Environment & Theme
    @Published public var environmentConfig: AppEnvironmentConfig = .current
    @Published public var selectedTheme: MiniBiThemePalette = MiniBiThemes.hrPartner

    // MARK: - Auth & Session State
    @Published public var isBootstrapping: Bool = false
    @Published public var isAuthenticated: Bool = false
    @Published public var currentUser: MiniBiUser?
    @Published public var savedAccounts: [SavedLoginAccount] = []
    @Published public var loginIdentifierInput: String = ""
    @Published public var loginPasswordInput: String = ""
    @Published public var rememberLoginChecked: Bool = true
    @Published public var isLoggingIn: Bool = false
    @Published public var loginErrorMessage: String?

    // MARK: - Navigation & Global Banner
    @Published public var currentDestination: MiniBiNavDestination = .attendance
    @Published public var bannerMessage: String?
    @Published public var bannerIsError: Bool = false
    private var bannerDismissTask: Task<Void, Never>?
    private var hasExplicitDestinationOverride: Bool = false
    private var initialAttendanceLoadedGeneration: Int64?
    public private(set) var hasLoadedAttendanceHistoryInSession: Bool = false
    public private(set) var isSyncingTimeAnchor: Bool = false
    public private(set) var timeAnchorBackoffAttempt: Int = 0
    public private(set) var nextAllowedTimeAnchorSyncElapsedMs: Int64 = 0
    public private(set) var outboxBackoffAttempt: Int = 0
    public private(set) var nextAllowedOutboxFlushElapsedMs: Int64 = 0
    public var backoffJitterProvider: () -> Double = { Double.random(in: 0.0...1.0) }

    // MARK: - Notifications (SSE + List) & Local Task Reminders
    @Published public var notifications: [AppNotificationItem] = []
    @Published public var unreadNotificationCount: Int = 0
    @Published public var isNotificationSheetPresented: Bool = false
    @Published public var isTaskReminderSheetPresented: Bool = false
    @Published public var taskReminders: [TaskReminderItem] = []
    private var announcedNotificationIds: Set<Int64> = []

    public var enabledRemindersCount: Int {
        taskReminders.filter { $0.isEnabled }.count
    }

    // MARK: - Dashboard & CCCD Review State
    @Published public var selectedDashboardSubTab: DashboardSubTab = .overview {
        didSet {
            if !DashboardSubTab.primaryTabs.contains(selectedDashboardSubTab) {
                selectedDashboardSubTab = .overview
            }
        }
    }
    @Published public var selectedDatePreset: DateFilterPreset = .thisMonth
    @Published public var filterStartDate: String = ""
    @Published public var filterEndDate: String = ""
    @Published public var dashboardScope: String = "all"
    @Published public var isLoadingDashboard: Bool = false
    @Published public var dashboardErrorMessage: String = ""
    @Published public var generalOverview: GeneralOverviewData = GeneralOverviewData()
    @Published public var workforceDeepMetrics: WorkforceDeepMetricsData = WorkforceDeepMetricsData()
    @Published public var teamReport: TeamReportData = TeamReportData()
    @Published public var duplicateCccdReport: DuplicateCccdReportData = DuplicateCccdReportData()
    @Published public var duplicateSearchQuery: String = ""

    @Published public var cccdReviewFilterStatus: String = "ALL"
    @Published public var cccdReviewSearchQuery: String = ""
    @Published public var cccdReviewItems: [CccdReviewItem] = []
    @Published public var cccdReviewTotal: Int = 0
    @Published public var selectedCccdReviewItem: CccdReviewItem?
    @Published public var isLoadingCccdReview: Bool = false
    @Published public private(set) var hasLoadedDashboardInSession: Bool = false

    // MARK: - Direct Entry ("Cấp mới") State — Preserved Across Menu/Tab Switches!
    @Published public var newWorkerDraftRows: [NewWorkerEntryDraft] = [NewWorkerEntryDraft()]
    @Published public var directEntryOptions: DirectEntryAssigneeOptions = DirectEntryAssigneeOptions()
    @Published public var directEntryHistoryBatches: [DirectEntryBatchHistoryItem] = []
    @Published public var isSubmittingNewWorkers: Bool = false
    @Published public var pendingDuplicateConfirmCandidates: [DirectEntryDuplicateCandidate] = []
    @Published public var showDuplicateConfirmDialog: Bool = false

    // MARK: - Workers Management & Uploader Submissions State
    @Published public var selectedWorkerScope: String = ""
    @Published public var workerSearchQuery: String = ""
    @Published public var workerProjectFilter: String = ""
    @Published public var workerStatusFilter: String = ""
    @Published public var workers: [WorkerItem] = []
    @Published public var workersTotalCount: Int = 0
    @Published public var workersPage: Int = 1
    @Published public var workersTotalPages: Int = 1
    @Published public var workersHasMore: Bool = false
    @Published public var workersNextCursor: String? = nil
    @Published public var isLoadingWorkers: Bool = false
    @Published public var selectedWorkerDetail: WorkerItem?
    @Published public var isUploadingWorkerContract: Bool = false
    @Published public var ownSubmissions: [DirectEntryBatchHistoryItem] = []
    @Published public var ownSubmissionsNextCursor: String?
    @Published public var ownSubmissionsHasMore: Bool = false
    @Published public var isLoadingOwnSubmissions: Bool = false
    @Published public var ownSubmissionsErrorMessage: String?

    public private(set) var sessionGeneration: Int64 = 0
    private var inFlightTasks: [String: Task<Void, Never>] = [:]
    private var salaryAdvanceContextLoadedGeneration: Int64?
    private var dashboardLoadGeneration: Int64 = 0
    private var ownSubmissionsFetchGeneration: Int64 = 0
    private var openDirectEditGeneration: Int64 = 0

    private struct SessionRequestToken: Equatable {
        let generation: Int64
        let userId: String
    }

    private func captureSessionToken() -> SessionRequestToken? {
        guard isAuthenticated,
              let uid = currentUser?.id.trimmingCharacters(in: .whitespacesAndNewlines),
              !uid.isEmpty else {
            return nil
        }
        return SessionRequestToken(generation: sessionGeneration, userId: uid)
    }

    private func isSessionValid(_ token: SessionRequestToken?) -> Bool {
        guard let token, isAuthenticated, sessionGeneration == token.generation else {
            return false
        }
        let currentUid = currentUser?.id.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return !currentUid.isEmpty && currentUid.caseInsensitiveCompare(token.userId) == .orderedSame
    }

    private func cancelAllSessionTasks() {
        dashboardReloadTask?.cancel()
        dashboardReloadTask = nil
        salaryAdvanceSearchTask?.cancel()
        salaryAdvanceSearchTask = nil
        salaryAdvanceListSearchTask?.cancel()
        salaryAdvanceListSearchTask = nil
        for (_, task) in inFlightTasks {
            task.cancel()
        }
        inFlightTasks.removeAll()
    }

    private func cancelInFlightTasksWithPrefix(_ prefix: String) {
        for (key, task) in inFlightTasks where key.contains(":\(prefix)") {
            task.cancel()
            inFlightTasks.removeValue(forKey: key)
        }
    }

    private func runDeduplicatedSessionTask(
        key: String,
        force: Bool = false,
        operation: @escaping (SessionRequestToken) async -> Void
    ) async {
        guard let token = captureSessionToken() else { return }
        let compositeKey = "gen:\(token.generation):uid:\(token.userId.lowercased()):\(key)"
        if let existing = inFlightTasks[compositeKey] {
            if !force {
                await existing.value
                return
            }
            existing.cancel()
            inFlightTasks.removeValue(forKey: compositeKey)
        }
        let task = Task { @MainActor [weak self] in
            guard self != nil else { return }
            await operation(token)
        }
        inFlightTasks[compositeKey] = task
        await task.value
        if inFlightTasks[compositeKey] == task {
            inFlightTasks.removeValue(forKey: compositeKey)
        }
    }

    @MainActor
    public func performRefreshable(_ action: @escaping @MainActor () async -> Void) async {
        let task = Task { @MainActor in
            await action()
        }
        await task.value
    }

    public static func resolveVisibleWorkerTabs(for user: MiniBiUser?) -> [(key: String, label: String)] {
        guard let user else { return [] }
        return user.safeAllowedWorkerDirectoryScopes.compactMap { rawKey in
            switch rawKey {
            case "managed": return ("managed", "Dự án quản lý")
            case "recruited": return ("recruited", "Tôi tuyển")
            case "team": return ("team", "Nhóm quản lý")
            case "all": return ("all", "Toàn hệ thống")
            default: return nil
            }
        }
    }

    public var visibleWorkerTabs: [(key: String, label: String)] {
        Self.resolveVisibleWorkerTabs(for: currentUser)
    }

    public var visibleMainDestinations: [MiniBiNavDestination] {
        guard isAuthenticated else { return [] }
        return MiniBiNavDestination.resolveMainNavigationDestinations(for: currentUser)
    }

    public var canAccessNewWorkerEntry: Bool {
        currentUser?.canAccessNewWorkerEntry == true
    }

    // MARK: - Recruitment Posts ("Tin tuyển dụng" — Read-Only) State
    @Published public var recruitmentPosts: [RecruitmentPostItem] = []
    @Published public var recruitmentPostsLoading: Bool = false
    @Published public var recruitmentPostsLoadingMore: Bool = false
    @Published public var recruitmentPostsError: String?
    @Published public var recruitmentPostsErrorCode: String?
    @Published public var recruitmentPostsHasMore: Bool = false
    @Published public var recruitmentPostsNextCursor: String?
    @Published public var recruitmentPostsSnapshotToken: String?
    @Published public var recruitmentPostsAccessDenied: Bool = false
    @Published public var recruitmentPostsFeatureUnavailable: Bool = false
    @Published public var selectedRecruitmentPostDetail: RecruitmentPostItem?
    @Published public var recruitmentPostDetailLoading: Bool = false
    @Published public var recruitmentPostDetailError: String?
    @Published public var recruitmentPostDetailBriefId: String?
    private var recruitmentPostsFetchGeneration: Int64 = 0
    private var recruitmentPostDetailFetchGeneration: Int64 = 0

    // MARK: - Direct Edit ("Sửa trực tiếp") State
    @Published public var directEditWorkerTarget: WorkerItem?
    @Published public var directEditBaselineDetail: WorkerEntryDetail?
    @Published public var directEditLoadingDetail: Bool = false
    @Published public var directEditSavingProfile: Bool = false
    @Published public var directEditErrorMessage: String?
    @Published public var directEditFieldErrors: [String: String] = [:]
    @Published public var directEditProfileSavedMessage: String?
    @Published public var directEditFileStatuses: [String: DirectEditFileStatus] = [:]

    // MARK: - Salary Advance ("Ứng lương" for QLDA) State
    @Published public var salaryAdvanceContext: SalaryAdvanceActorContext?
    @Published public var salaryAdvanceSheetOpen: Bool = false
    @Published public var salaryAdvanceWorkspaceTab: String = "CREATE"
    @Published public var salaryAdvanceSelectedProjectId: String?
    @Published public var salaryAdvanceWorkerSearch: String = ""
    @Published public var salaryAdvanceWorkerEligibilityFilter: String = "ALL"
    @Published public var salaryAdvanceWorkerPickerExpanded: Bool = true
    @Published public var salaryAdvanceWorkers: [SalaryAdvanceWorkerOption] = []
    @Published public var salaryAdvanceWorkersLoading: Bool = false
    @Published public var salaryAdvanceWorkersLoadingMore: Bool = false
    @Published public var salaryAdvanceWorkersError: String?
    @Published public var salaryAdvanceWorkersNextCursor: String?
    @Published public var salaryAdvanceWorkersHasMore: Bool = false
    @Published public var salaryAdvanceSelectedWorkerId: String?
    @Published public var salaryAdvancePaymentMethod: String = SalaryAdvanceContract.paymentMethodCash
    @Published public var salaryAdvanceAmountRaw: String = ""
    @Published public var salaryAdvanceInitialContent: String = ""
    @Published public var salaryAdvanceCreateSubmitting: Bool = false
    @Published public var salaryAdvanceCreateError: String?
    @Published public var salaryAdvancePendingIdempotencyKey: String?
    @Published public var salaryAdvancePendingSignature: String?

    // Salary Advance PM Queue Request List State
    @Published public var salaryAdvanceRequests: [SalaryAdvanceRequestSummary] = []
    @Published public var salaryAdvanceRequestsLoading: Bool = false
    @Published public var salaryAdvanceRequestsLoadingMore: Bool = false
    @Published public var salaryAdvanceRequestsError: String?
    @Published public var salaryAdvanceRequestsNextCursor: String?
    @Published public var salaryAdvanceRequestsHasMore: Bool = false
    @Published public var salaryAdvancePmBadgeCount: Int = 0
    @Published public var salaryAdvanceListProjectFilter: String = "ALL"
    @Published public var salaryAdvanceListStateFilter: String = "ALL"
    @Published public var salaryAdvanceListEvidenceFilter: String = "ALL"
    @Published public var salaryAdvanceListSearch: String = ""

    // Salary Advance Request Detail, Messages, Actions & Evidence Upload State
    @Published public var salaryAdvanceSelectedRequestId: String?
    @Published public var salaryAdvanceActiveDetail: SalaryAdvanceRequestDetail?
    @Published public var salaryAdvanceDetailLoading: Bool = false
    @Published public var salaryAdvanceDetailError: String?
    @Published public var salaryAdvanceConflictBanner: String?
    @Published public var salaryAdvanceSelectedEvidenceVersion: Int?
    @Published public var salaryAdvanceMessageDraft: String = ""
    @Published public var salaryAdvanceMessageSubmitting: Bool = false
    @Published public var salaryAdvanceActionNoteDraft: String = ""
    @Published public var salaryAdvanceActionSubmitting: Bool = false
    @Published public var salaryAdvanceEvidenceCaptionDraft: String = ""
    @Published public var salaryAdvanceEvidenceUploading: Bool = false
    @Published public var salaryAdvanceEvidenceError: String?
    private var salaryAdvanceMessageIdempotencyKey: String = UUID().uuidString.lowercased()
    private var salaryAdvanceActionIdempotencyKey: String = UUID().uuidString.lowercased()
    private var salaryAdvanceEvidenceReserveKey: String = UUID().uuidString.lowercased()
    private var salaryAdvanceEvidenceFinalizeKey: String = UUID().uuidString.lowercased()

    @Published public var salaryAdvanceHistoryOpen: Bool = false
    @Published public var salaryAdvanceHistoryTargetRequestId: String?
    @Published public var salaryAdvanceHistoryPeriodMode: String = SalaryAdvanceContract.periodModeLast30Days
    @Published public var salaryAdvanceHistoryMonth: String = ""
    @Published public var salaryAdvanceHistoryFromDate: String = ""
    @Published public var salaryAdvanceHistoryToDate: String = ""
    @Published public var salaryAdvanceHistoryLoading: Bool = false
    @Published public var salaryAdvanceHistoryLoadingMore: Bool = false
    @Published public var salaryAdvanceHistoryError: String?
    @Published public var salaryAdvanceHistoryResponse: SalaryAdvanceWorkerHistoryResponse?
    @Published public var salaryAdvanceHistoryItems: [SalaryAdvanceWorkerHistoryItem] = []
    @Published public var salaryAdvanceHistoryNextCursor: String?
    @Published public var salaryAdvanceHistoryHasMore: Bool = false
    private var salaryAdvanceSearchTask: Task<Void, Never>?
    private var salaryAdvanceListSearchTask: Task<Void, Never>?

    // MARK: - Attendance State
    @Published public var attendanceTimeEvaluation: AnchorEvaluationResult
    @Published public var attendanceOutboxItems: [AttendanceOutboxItem] = []
    @Published public var todayServerAttendanceCount: Int = 0
    @Published public var isSubmittingAttendance: Bool = false
    @Published public var isSyncingAttendance: Bool = false
    @Published public var selectedAttendanceHistoryDate: String = LeaveContract.todayVietnamIsoDate()
    @Published public var attendanceHistoryPage: Int = 1
    @Published public var attendanceHistoryHasMore: Bool = false
    @Published public var attendanceHistoryTotalCount: Int = 0
    @Published public var isLoadingMoreAttendanceHistory: Bool = false

    // MARK: - Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") State
    @Published public var activeAttendanceTab: AttendanceAreaTab = .checkIn
    @Published public var leaveYear: Int = LeaveContract.currentVietnamLeaveYear()
    @Published public var myLeaveSummary: LeaveMeSummaryProjection?
    @Published public var isLoadingMyLeave: Bool = false
    @Published public var myLeaveError: String?
    @Published public var myLeaveErrorCode: String?
    @Published public var leaveDraftDays: [LeaveDraftDayInput] = []
    @Published public var leaveDraftReason: String = ""
    @Published public var isMutatingLeave: Bool = false
    @Published public var leaveMutationError: String?
    @Published public var leaveInboxStatusFilter: String = "PENDING"
    @Published public var leaveInboxPage: Int = 1
    @Published public var leaveApprovalInbox: LeaveApprovalInboxProjection?
    @Published public var isLoadingLeaveInbox: Bool = false
    @Published public var isLoadingMoreLeaveInbox: Bool = false
    @Published public var leaveInboxError: String?

    // MARK: - Attendance Explanation ("Giải trình") & Approval ("Xét duyệt giải trình") State
    @Published public var explanationSelectedDate: String = AttendanceExplanationContract.todayVietnamIsoDate()
    @Published public var myExplanationsOverview: AttendanceMyExplanationsOverviewProjection?
    @Published public var isLoadingMyExplanations: Bool = false
    @Published public var isLoadingMoreMyExplanations: Bool = false
    @Published public var myExplanationsError: String?
    @Published public var myExplanationsErrorCode: String?
    @Published public var isMutatingExplanation: Bool = false
    @Published public var explanationMutationError: String?
    @Published public var explanationInboxStatusFilter: String = "PENDING"
    @Published public var explanationInboxPage: Int = 1
    @Published public var explanationApprovalInbox: AttendanceExplanationApprovalInboxProjection?
    @Published public var isLoadingExplanationInbox: Bool = false
    @Published public var isLoadingMoreExplanationInbox: Bool = false
    @Published public var explanationInboxError: String?
    @Published public var explanationInboxErrorCode: String?

    // MARK: - Work Reports State
    @Published public var selectedWorkReportsSubTab: WorkReportsSubTab = .actionable
    @Published public var workReportObligations: [WorkReportObligationItem] = []
    @Published public var workReportHistoryFilterByDate: Bool = false
    @Published public var workReportHistorySelectedDate: String = LeaveContract.todayVietnamIsoDate()
    @Published public var selectedObligationEditor: WorkReportObligationItem?
    @Published public var editorFieldValues: [String: String] = [:]
    @Published public var isLoadingWorkReports: Bool = false
    @Published public var isSavingWorkReport: Bool = false
    @Published public var workReportReviews: [WorkReportReviewItem] = []
    @Published public var workReportReviewsPage: Int = 1
    @Published public var workReportReviewsNextCursor: String? = nil
    @Published public var workReportReviewsHasMore: Bool = false
    @Published public var isLoadingMoreWorkReportReviews: Bool = false
    @Published public var selectedReviewDetail: WorkReportReviewItem?
    @Published public var workReportTemplates: [WorkReportTemplateOption] = []
    @Published public var workReportAnalytics: WorkReportsAnalyticsSnapshot = WorkReportsAnalyticsSnapshot()

    // MARK: - Pagination Load-More State for Workers & CCCD
    @Published public var isLoadingMoreWorkers: Bool = false
    @Published public var cccdReviewPage: Int = 1
    @Published public var isLoadingMoreCccdReview: Bool = false
    @Published public var isLoadingMoreDuplicateCccd: Bool = false

    public init(
        repository: MiniBiRepository = .shared,
        directEntryService: DirectEntryService = .shared,
        workReportsService: WorkReportsAndReportingService = .shared,
        attendanceOutboxService: AttendanceOutboxService = .shared,
        timeAnchorManager: AttendanceTimeAnchorManager = .shared,
        notificationService: NotificationService = .shared,
        leaveService: LeaveService = .shared,
        attendanceExplanationService: AttendanceExplanationService = .shared,
        salaryAdvanceService: SalaryAdvanceService = .shared,
        recruitmentPostService: RecruitmentPostService = .shared,
        taskReminderManager: TaskReminderManager = .shared
    ) {
        self.repository = repository
        self.directEntryService = directEntryService
        self.workReportsService = workReportsService
        self.attendanceOutboxService = attendanceOutboxService
        self.timeAnchorManager = timeAnchorManager
        self.notificationService = notificationService
        self.leaveService = leaveService
        self.attendanceExplanationService = attendanceExplanationService
        self.salaryAdvanceService = salaryAdvanceService
        self.recruitmentPostService = recruitmentPostService
        self.taskReminderManager = taskReminderManager
        self.attendanceTimeEvaluation = timeAnchorManager.evaluateAnchor()
        self.taskReminders = taskReminderManager.reconcileExpiredOnceReminders()

        let bundleEnv = AppEnvironmentConfig.current
        let resolvedEnv = AppEnvironmentConfig.forVariant(
            .release,
            marketingVersion: bundleEnv.marketingVersion,
            buildNumber: bundleEnv.buildNumber
        )
        self.environmentConfig = resolvedEnv
        self.repository.setSelectedEnvironmentVariant(.release)
        self.repository.httpClient.setBaseUrl(resolvedEnv.defaultBaseUrl)

        self.selectedTheme = MiniBiThemes.byId(repository.getSelectedThemeId())
        self.savedAccounts = repository.getSavedLoginAccounts()
        if let firstAccount = savedAccounts.first {
            self.loginIdentifierInput = firstAccount.identifier
        }
        applyDatePreset(.all, reload: false)

        self.attendanceOutboxService.onOutboxChanged = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, let username = self.currentUser?.username, !username.isEmpty else { return }
                self.attendanceOutboxItems = self.attendanceOutboxService.listItemsForUser(userKey: username)
                self.todayServerAttendanceCount = max(
                    self.todayServerAttendanceCount,
                    self.attendanceOutboxService.todaySyncedCountForUser(userKey: username)
                )
            }
        }

        self.taskReminderManager.onRemindersChanged = { [weak self] updated in
            Task { @MainActor [weak self] in
                self?.taskReminders = updated
            }
        }
    }

    // MARK: - Local Task Reminders ("Nhắc việc")

    public func refreshTaskReminders() {
        taskReminders = taskReminderManager.reconcileExpiredOnceReminders()
    }

    @discardableResult
    public func saveTaskReminder(
        id: String? = nil,
        title: String,
        hour: Int,
        minute: Int,
        repeatMode: TaskReminderRepeatMode,
        soundId: String = TaskReminderSystemSound.defaultSound.id,
        isEnabled: Bool = true
    ) -> TaskReminderItem {
        let item = taskReminderManager.saveReminder(
            id: id,
            title: title,
            hour: hour,
            minute: minute,
            repeatMode: repeatMode,
            soundId: soundId,
            isEnabled: isEnabled
        )
        taskReminders = taskReminderManager.reminders
        return item
    }

    public func setTaskReminderEnabled(id: String, isEnabled: Bool) {
        _ = taskReminderManager.setReminderEnabled(id: id, isEnabled: isEnabled)
        taskReminders = taskReminderManager.reminders
    }

    public func deleteTaskReminder(id: String) {
        taskReminderManager.deleteReminder(id: id)
        taskReminders = taskReminderManager.reminders
    }

    public func playTaskReminderSoundPreview(soundId: String) {
        taskReminderManager.playSoundPreview(soundId: soundId)
    }

    // MARK: - Computed Properties for Work Reports (Overdue Contract)

    /// Actionable obligations (`Cần nộp`): excludes any obligation with `isOverdue == true`.
    public var actionableObligations: [WorkReportObligationItem] {
        workReportObligations.filter { WorkReportsContract.isActionableObligation($0) }
    }

    /// History obligations (`Lịch sử / Đã lưu / Đã nộp`): includes `SUBMITTED`, `APPROVED`, and any obligation with `isOverdue == true`.
    public var historyObligations: [WorkReportObligationItem] {
        workReportObligations.filter { WorkReportsContract.isHistoryObligation($0) }
    }

    /// Whether the currently open obligation editor is locked in read-only mode.
    public var isEditorReadOnly: Bool {
        guard let ob = selectedObligationEditor else { return true }
        return !WorkReportsContract.isEditableObligation(ob)
    }

    // MARK: - Banner & Theme Helpers

    public func showBanner(_ message: String, isError: Bool = false) {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              !trimmed.lowercased().contains("(cancelled)"),
              trimmed.uppercased() != "REQUEST_CANCELLED" else {
            return
        }
        bannerDismissTask?.cancel()
        bannerMessage = trimmed
        bannerIsError = isError
        bannerDismissTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard !Task.isCancelled else { return }
            self?.bannerMessage = nil
        }
    }

    public func dismissBanner() {
        bannerDismissTask?.cancel()
        bannerDismissTask = nil
        bannerMessage = nil
    }

    public func selectTheme(_ palette: MiniBiThemePalette) {
        selectedTheme = palette
        repository.setSelectedThemeId(palette.id)
    }

    public func selectEnvironmentVariant(_ variant: AppEnvironmentVariant) {
        sessionGeneration &+= 1
        cancelAllSessionTasks()
        let currentEnv = AppEnvironmentConfig.current
        let nextEnv = AppEnvironmentConfig.forVariant(
            variant,
            marketingVersion: currentEnv.marketingVersion,
            buildNumber: currentEnv.buildNumber
        )
        environmentConfig = nextEnv
        repository.setSelectedEnvironmentVariant(variant)
        repository.httpClient.setBaseUrl(nextEnv.defaultBaseUrl)
        repository.httpClient.cookieStore.clearAll()
        loginErrorMessage = nil
    }

    // MARK: - Navigation (Preserves `newWorkerDraftRows`)

    public func navigateTo(_ destination: MiniBiNavDestination) {
        // Switching destinations NEVER resets `newWorkerDraftRows`
        if destination == .workers && currentDestination == .workers && salaryAdvanceSheetOpen {
            closeSalaryAdvanceSheet()
            return
        }
        if destination == .workers && (!isAuthenticated || (visibleWorkerTabs.isEmpty && !canAccessNewWorkerEntry)) {
            return
        }
        if destination == .newEntry && (!isAuthenticated || !canAccessNewWorkerEntry) {
            return
        }
        if isAuthenticated && destination != .newEntry {
            let allowed = MiniBiNavDestination.resolveMainNavigationDestinations(for: currentUser)
            if !allowed.contains(destination) {
                return
            }
        }
        let previousDestination = currentDestination
        if previousDestination == .recruitment && destination != .recruitment {
            cancelInFlightRecruitmentPostRequests()
            closeRecruitmentPostDetail()
        }
        hasExplicitDestinationOverride = true
        currentDestination = destination
        if destination == .attendance {
            activeAttendanceTab = .checkIn
            if isAuthenticated {
                Task {
                    await loadInitialAttendanceScreenDataIfNeeded()
                }
            }
        } else if destination == .dashboard {
            normalizeSelectedDashboardSubTab()
            if isAuthenticated
                && !isLoadingDashboard
                && !hasLoadedDashboardInSession {
                isLoadingDashboard = true
                Task { await loadDashboardData(includeCccdTabs: false) }
            }
        } else if destination == .workers {
            if salaryAdvanceSheetOpen {
                closeSalaryAdvanceSheet()
            }
            if isAuthenticated && currentUser?.canAttemptPmSalaryAdvanceContext == true {
                Task { await loadSalaryAdvanceContext() }
            }
        } else if destination == .recruitment {
            if isAuthenticated
                && recruitmentPosts.isEmpty
                && !recruitmentPostsLoading
                && !recruitmentPostsAccessDenied
                && !recruitmentPostsFeatureUnavailable {
                Task { await loadRecruitmentPosts(reset: true) }
            }
        } else if destination == .newEntry {
            if isAuthenticated && currentUser?.canLoadDirectEntryCatalog == true && directEntryOptions.catalogProjects.isEmpty {
                Task { await loadDirectEntryAssigneeOptions() }
            }
        } else if destination == .workReports {
            if isAuthenticated && workReportObligations.isEmpty {
                Task { await loadWorkReportsData() }
            }
        }
    }

    // MARK: - Session Bootstrap, Login & Logout

    public func bootstrapSession() async {
        savedAccounts = repository.getSavedLoginAccounts()
        guard repository.httpClient.cookieStore.hasSessionCookie() else {
            return
        }
        let expectedGen = sessionGeneration
        isBootstrapping = true
        let meResult = await repository.fetchCurrentUser()
        guard sessionGeneration == expectedGen else {
            return
        }
        isBootstrapping = false

        if case .success(let user, _) = meResult {
            onAuthenticatedSessionEstablished(user: user)
        }
    }

    public func performLogin() async {
        let trimmedId = loginIdentifierInput.trimmingCharacters(in: .whitespacesAndNewlines)
        let password = loginPasswordInput
        guard !trimmedId.isEmpty && !password.isEmpty else {
            loginErrorMessage = "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu."
            return
        }

        let expectedGen = sessionGeneration
        isLoggingIn = true
        loginErrorMessage = nil

        let result = await repository.login(identifier: trimmedId, password: password)
        guard sessionGeneration == expectedGen else {
            isLoggingIn = false
            return
        }
        isLoggingIn = false

        switch result {
        case .success(let user, _):
            if rememberLoginChecked {
                repository.saveLoginAccount(
                    identifier: trimmedId,
                    password: password,
                    displayName: user.fullName,
                    role: user.roleLabel
                )
                savedAccounts = repository.getSavedLoginAccounts()
            }
            loginPasswordInput = ""
            onAuthenticatedSessionEstablished(user: user)
        case .failure(let message, _, _, _):
            loginErrorMessage = message
        }
    }

    public func quickLoginWithSavedAccount(_ account: SavedLoginAccount) async {
        loginIdentifierInput = account.identifier
        loginPasswordInput = account.password
        rememberLoginChecked = true
        await performLogin()
    }

    public func removeSavedAccount(_ account: SavedLoginAccount) {
        repository.removeSavedLoginAccount(identifier: account.identifier)
        savedAccounts = repository.getSavedLoginAccounts()
    }

    private func resetSessionScopedState(clearDraftRows: Bool) {
        cancelAllSessionTasks()
        salaryAdvanceContextLoadedGeneration = nil
        dashboardLoadGeneration &+= 1
        initialAttendanceLoadedGeneration = nil
        hasLoadedAttendanceHistoryInSession = false
        hasLoadedDashboardInSession = false
        isSyncingTimeAnchor = false
        isSyncingAttendance = false
        isSubmittingAttendance = false
        timeAnchorBackoffAttempt = 0
        nextAllowedTimeAnchorSyncElapsedMs = 0
        outboxBackoffAttempt = 0
        nextAllowedOutboxFlushElapsedMs = 0
        ownSubmissionsFetchGeneration &+= 1
        openDirectEditGeneration &+= 1
        selectedWorkerScope = ""
        workerSearchQuery = ""
        workerProjectFilter = ""
        workerStatusFilter = ""
        workers = []
        workersTotalCount = 0
        workersPage = 1
        workersTotalPages = 1
        workersHasMore = false
        workersNextCursor = nil
        isLoadingWorkers = false
        isLoadingMoreWorkers = false
        selectedWorkerDetail = nil
        isUploadingWorkerContract = false
        ownSubmissions = []
        ownSubmissionsNextCursor = nil
        ownSubmissionsHasMore = false
        isLoadingOwnSubmissions = false
        ownSubmissionsErrorMessage = nil
        directEntryOptions = DirectEntryAssigneeOptions()
        directEntryHistoryBatches = []
        isSubmittingNewWorkers = false
        pendingDuplicateConfirmCandidates = []
        showDuplicateConfirmDialog = false
        directEntryService.clearCatalogCache()
        closeDirectEdit()
        salaryAdvanceContext = nil
        salaryAdvanceSelectedProjectId = nil
        closeSalaryAdvanceSheet()
        salaryAdvanceRequests = []
        salaryAdvancePmBadgeCount = 0
        generalOverview = GeneralOverviewData()
        workforceDeepMetrics = WorkforceDeepMetricsData()
        teamReport = TeamReportData()
        duplicateCccdReport = DuplicateCccdReportData()
        isLoadingMoreDuplicateCccd = false
        cccdReviewItems = []
        cccdReviewTotal = 0
        cccdReviewPage = 1
        isLoadingMoreCccdReview = false
        selectedCccdReviewItem = nil
        workReportObligations = []
        workReportHistoryFilterByDate = false
        workReportHistorySelectedDate = LeaveContract.todayVietnamIsoDate()
        selectedObligationEditor = nil
        editorFieldValues = [:]
        isSavingWorkReport = false
        workReportReviews = []
        workReportReviewsPage = 1
        workReportReviewsNextCursor = nil
        workReportReviewsHasMore = false
        isLoadingMoreWorkReportReviews = false
        selectedReviewDetail = nil
        workReportTemplates = []
        workReportAnalytics = WorkReportsAnalyticsSnapshot()
        isLoadingDashboard = false
        isLoadingWorkReports = false
        isLoadingCccdReview = false
        selectedAttendanceHistoryDate = LeaveContract.todayVietnamIsoDate()
        attendanceHistoryPage = 1
        attendanceHistoryHasMore = false
        attendanceHistoryTotalCount = 0
        isLoadingMoreAttendanceHistory = false
        isLoadingMoreLeaveInbox = false
        isLoadingMoreMyExplanations = false
        isLoadingMoreExplanationInbox = false
        resetRecruitmentPostsState()
        if clearDraftRows {
            newWorkerDraftRows = [NewWorkerEntryDraft()]
        }
    }

    public func onAuthenticatedSessionEstablished(user: MiniBiUser) {
        let previousUserId = currentUser?.id
        sessionGeneration &+= 1
        if previousUserId != user.id {
            resetSessionScopedState(clearDraftRows: previousUserId != nil)
        } else {
            cancelAllSessionTasks()
            salaryAdvanceContextLoadedGeneration = nil
            initialAttendanceLoadedGeneration = nil
            hasLoadedAttendanceHistoryInSession = false
            isSyncingTimeAnchor = false
            isSyncingAttendance = false
            isSubmittingAttendance = false
            timeAnchorBackoffAttempt = 0
            nextAllowedTimeAnchorSyncElapsedMs = 0
            outboxBackoffAttempt = 0
            nextAllowedOutboxFlushElapsedMs = 0
            ownSubmissionsFetchGeneration &+= 1
            openDirectEditGeneration &+= 1
            workers = []
            workersTotalCount = 0
            workersHasMore = false
            workersNextCursor = nil
            ownSubmissions = []
            ownSubmissionsNextCursor = nil
            ownSubmissionsHasMore = false
            directEntryOptions = DirectEntryAssigneeOptions()
            directEntryService.clearCatalogCache()
            salaryAdvanceContext = nil
            salaryAdvanceSelectedProjectId = nil
            resetRecruitmentPostsState()
        }
        currentUser = user
        selectedWorkerScope = Self.resolveVisibleWorkerTabs(for: user).first?.key ?? ""
        isAuthenticated = true
        let allowedDestinations = MiniBiNavDestination.resolveMainNavigationDestinations(for: user)
        if hasExplicitDestinationOverride
            && (allowedDestinations.contains(currentDestination)
                || (currentDestination == .newEntry && user.canAccessNewWorkerEntry)) {
            // Preserve deep-link / notification destination selected prior to session establishment
        } else {
            hasExplicitDestinationOverride = false
            currentDestination = MiniBiNavDestination.resolveDefaultDestination(for: user)
        }

        // Ensure at least 1 draft row is ready for "Cấp mới"
        if newWorkerDraftRows.isEmpty {
            newWorkerDraftRows = [NewWorkerEntryDraft()]
        }

        attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: user.username)
        attendanceOutboxService.startNetworkAutoFlush(
            userKeyProvider: { [weak self] in
                self?.currentUser?.username
            },
            customFlushHandler: { [weak self] in
                guard let self else { return }
                _ = await self.flushAttendanceOutboxOnly(isManual: false)
            }
        )

        notificationService.startSseStream { [weak self] incomingItem in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let incomingItem {
                    let isNew = !self.notifications.contains(where: { $0.id == incomingItem.id })
                        && !self.announcedNotificationIds.contains(incomingItem.id)
                    self.announcedNotificationIds.insert(incomingItem.id)
                    if isNew {
                        self.notifications.insert(incomingItem, at: 0)
                        self.unreadNotificationCount = self.notifications.filter { !$0.isRead }.count
                        if !incomingItem.isRead {
                            self.showBanner("Thông báo mới: \(incomingItem.title)", isError: false)
                        }
                    }
                } else {
                    await self.refreshNotifications()
                }
            }
        }

        // Never eagerly load `/api/direct-entry/catalog`, `/api/salary-advance/context`, `/api/work-reports/*`, or Dashboard data when opening into `Chấm công`.
        let token = captureSessionToken()
        let initialDest = currentDestination
        Task { [weak self] in
            guard let self, self.isSessionValid(token) else { return }
            await self.refreshNotifications()
            guard self.isSessionValid(token) else { return }
            switch initialDest {
            case .attendance:
                await self.loadInitialAttendanceScreenDataIfNeeded()
            case .dashboard:
                await self.loadDashboardData(includeCccdTabs: false)
            case .workReports:
                if self.workReportObligations.isEmpty {
                    await self.loadWorkReportsData()
                }
            case .workers:
                if self.currentUser?.canAttemptPmSalaryAdvanceContext == true {
                    await self.loadSalaryAdvanceContext()
                }
            case .recruitment:
                if self.recruitmentPosts.isEmpty {
                    await self.loadRecruitmentPosts(reset: true)
                }
            case .newEntry:
                if self.currentUser?.canLoadDirectEntryCatalog == true && self.directEntryOptions.catalogProjects.isEmpty {
                    await self.loadDirectEntryAssigneeOptions()
                }
            }
        }
    }

    public func performLogout() async {
        sessionGeneration &+= 1
        cancelAllSessionTasks()
        notificationService.stopSseStream()
        attendanceOutboxService.stopNetworkAutoFlush()
        isAuthenticated = false
        currentUser = nil
        hasExplicitDestinationOverride = false
        currentDestination = .attendance
        resetSessionScopedState(clearDraftRows: true)
        await repository.logout()
        timeAnchorManager.clearAnchor()
        attendanceTimeEvaluation = timeAnchorManager.evaluateAnchor()

        selectedObligationEditor = nil
        editorFieldValues = [:]
        notifications = []
        unreadNotificationCount = 0
        announcedNotificationIds = []
        activeAttendanceTab = .checkIn
        leaveYear = LeaveContract.currentVietnamLeaveYear()
        myLeaveSummary = nil
        isLoadingMyLeave = false
        myLeaveError = nil
        myLeaveErrorCode = nil
        leaveDraftDays = []
        leaveDraftReason = ""
        isMutatingLeave = false
        leaveMutationError = nil
        leaveInboxStatusFilter = "PENDING"
        leaveInboxPage = 1
        leaveApprovalInbox = nil
        isLoadingLeaveInbox = false
        leaveInboxError = nil
        explanationSelectedDate = AttendanceExplanationContract.todayVietnamIsoDate()
        myExplanationsOverview = nil
        isLoadingMyExplanations = false
        myExplanationsError = nil
        myExplanationsErrorCode = nil
        isMutatingExplanation = false
        explanationMutationError = nil
        explanationInboxStatusFilter = "PENDING"
        explanationInboxPage = 1
        explanationApprovalInbox = nil
        isLoadingExplanationInbox = false
        explanationInboxError = nil
        explanationInboxErrorCode = nil
        savedAccounts = repository.getSavedLoginAccounts()
    }

    // MARK: - Notifications & Deep-Linking

    public func refreshNotifications() async {
        let res = await notificationService.fetchNotifications()
        if case .success(let data, _) = res {
            notifications = data.items
            unreadNotificationCount = data.unreadCount
            for item in data.items {
                announcedNotificationIds.insert(item.id)
            }
        }
    }

    public func markAllNotificationsRead() async {
        _ = await notificationService.markAllAsRead()
        for i in notifications.indices {
            notifications[i].isRead = true
        }
        unreadNotificationCount = 0
    }

    public func handleNotificationTap(_ item: AppNotificationItem) async {
        if !item.isRead {
            _ = await notificationService.markAsRead(notificationId: item.id)
            if let idx = notifications.firstIndex(where: { $0.id == item.id }) {
                notifications[idx].isRead = true
            }
            unreadNotificationCount = notifications.filter { !$0.isRead }.count
        }
        isNotificationSheetPresented = false

        let typeUpper = item.type.uppercased()
        let routeLower = (item.targetRoute ?? "").lowercased()

        if typeUpper.contains("WORK_REPORT") || routeLower.contains("work-report") {
            hasExplicitDestinationOverride = true
            currentDestination = .workReports
            await loadWorkReportsData()
            if let entityIdStr = item.targetEntityId, let obId = Int64(entityIdStr) {
                if let matched = workReportObligations.first(where: { $0.id == obId || $0.submissionId == obId }) {
                    await openObligationEditor(matched)
                }
            }
        } else if typeUpper.contains("CCCD") || routeLower.contains("cccd") {
            hasExplicitDestinationOverride = true
            currentDestination = .dashboard
            selectedDashboardSubTab = .overview
            await loadDashboardData(includeCccdTabs: false)
        } else if typeUpper.contains("LEAVE") || routeLower.contains("leave") {
            hasExplicitDestinationOverride = true
            currentDestination = .attendance
            await refreshMyLeaveSummary(year: leaveYear)
            if (typeUpper.contains("APPROV") || routeLower.contains("approv")),
               let auth = myLeaveSummary?.authority,
               auth.canApproveRequests && !auth.isSharedNonEmployee {
                selectAttendanceTab(.leaveApproval)
            } else if resolveVisibleAttendanceTabs().contains(.leaveRequest) {
                selectAttendanceTab(.leaveRequest)
            }
        } else if typeUpper.contains("ATTENDANCE") || routeLower.contains("attendance") {
            hasExplicitDestinationOverride = true
            currentDestination = .attendance
            activeAttendanceTab = .checkIn
        } else if typeUpper.contains("WORKER") || routeLower.contains("worker") {
            guard !visibleWorkerTabs.isEmpty else { return }
            hasExplicitDestinationOverride = true
            currentDestination = .workers
            await loadWorkers(resetPage: true)
            if let maNld = item.targetEntityId, !maNld.isEmpty {
                let detailRes = await repository.fetchWorkerDetail(maNld: maNld)
                if case .success(let detail, _) = detailRes {
                    selectedWorkerDetail = detail
                }
            }
        } else if typeUpper.contains("DIRECT_ENTRY") || routeLower.contains("direct-entry") {
            guard canAccessNewWorkerEntry else { return }
            hasExplicitDestinationOverride = true
            currentDestination = .newEntry
            await loadDirectEntryBatchHistory()
        }
    }

    // MARK: - Dashboard & CCCD Review Actions

    private var dashboardReloadTask: Task<Void, Never>?

    public func normalizeSelectedDashboardSubTab() {
        if !DashboardSubTab.primaryTabs.contains(selectedDashboardSubTab) {
            selectedDashboardSubTab = .overview
        }
    }

    public func resetDashboardDisplayForFilterChange() {
        dashboardLoadGeneration &+= 1
        hasLoadedDashboardInSession = false
        generalOverview = GeneralOverviewData()
        teamReport = TeamReportData()
        dashboardErrorMessage = ""
        isLoadingDashboard = captureSessionToken() != nil
    }

    public func setDashboardScope(_ scope: String, reload: Bool = true) {
        let trimmed = scope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalized = trimmed.isEmpty ? "all" : trimmed
        dashboardScope = normalized
        if reload {
            resetDashboardDisplayForFilterChange()
            dashboardReloadTask?.cancel()
            cancelInFlightTasksWithPrefix("dashboard:")
            dashboardReloadTask = Task { [weak self] in
                await self?.loadDashboardData(includeCccdTabs: false, force: true)
            }
        }
    }

    public func applyDatePreset(_ preset: DateFilterPreset, reload: Bool = true) {
        selectedDatePreset = preset
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        cal.firstWeekday = 2 // Monday (ISO-8601 / Vietnam standard)
        cal.minimumDaysInFirstWeek = 4
        let now = Date()
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = cal.timeZone
        df.dateFormat = "yyyy-MM-dd"

        switch preset {
        case .all:
            filterStartDate = ""
            filterEndDate = ""
        case .thisWeek:
            let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)
            let startOfWeek = cal.date(from: comps) ?? now
            filterStartDate = df.string(from: startOfWeek)
            filterEndDate = df.string(from: now)
        case .thisMonth:
            let comps = cal.dateComponents([.year, .month], from: now)
            let startOfMonth = cal.date(from: comps) ?? now
            filterStartDate = df.string(from: startOfMonth)
            filterEndDate = df.string(from: now)
        case .lastMonth:
            let comps = cal.dateComponents([.year, .month], from: now)
            let startOfThisMonth = cal.date(from: comps) ?? now
            let startOfLastMonth = cal.date(byAdding: .month, value: -1, to: startOfThisMonth) ?? now
            let endOfLastMonth = cal.date(byAdding: .day, value: -1, to: startOfThisMonth) ?? now
            filterStartDate = df.string(from: startOfLastMonth)
            filterEndDate = df.string(from: endOfLastMonth)
        }

        if reload {
            resetDashboardDisplayForFilterChange()
            dashboardReloadTask?.cancel()
            cancelInFlightTasksWithPrefix("dashboard:")
            dashboardReloadTask = Task { [weak self] in
                await self?.loadDashboardData(includeCccdTabs: false, force: true)
            }
        }
    }

    public func loadDashboardData(includeCccdTabs: Bool = false, force: Bool = false) async {
        guard captureSessionToken() != nil else { return }
        normalizeSelectedDashboardSubTab()
        let targetScope = dashboardScope
        let targetStart = filterStartDate
        let targetEnd = filterEndDate
        let allowedScopes = currentUser?.safeAllowedWorkerTabs ?? []
        let dedupeKey = "dashboard:\(targetScope):\(targetStart):\(targetEnd)"

        await runDeduplicatedSessionTask(key: dedupeKey, force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            self.dashboardLoadGeneration &+= 1
            let loadGen = self.dashboardLoadGeneration
            self.isLoadingDashboard = true
            self.dashboardErrorMessage = ""
            defer {
                if self.isSessionValid(token), self.dashboardLoadGeneration == loadGen {
                    self.isLoadingDashboard = false
                }
            }

            async let overviewTask = self.workReportsService.fetchGeneralOverview(
                scope: targetScope,
                allowedWorkerScopes: allowedScopes,
                startDate: targetStart,
                endDate: targetEnd
            )
            async let teamTask = self.workReportsService.fetchTeamReport(
                allowedWorkerScopes: allowedScopes,
                startDate: targetStart,
                endDate: targetEnd
            )

            let overviewRes = await overviewTask
            let teamRes = await teamTask

            guard !Task.isCancelled,
                  self.isSessionValid(token),
                  self.dashboardLoadGeneration == loadGen,
                  self.dashboardScope == targetScope,
                  self.filterStartDate == targetStart,
                  self.filterEndDate == targetEnd else {
                return
            }

            if overviewRes.isCancelledRequest && teamRes.isCancelledRequest {
                return
            }

            var firstError: String?
            switch overviewRes {
            case .success(let ov, _):
                self.generalOverview = ov
            case .failure(let message, _, _, _):
                if !overviewRes.isCancelledRequest {
                    firstError = message
                }
            }

            switch teamRes {
            case .success(let tm, _):
                self.teamReport = tm
            case .failure(let message, _, _, _):
                if !teamRes.isCancelledRequest, firstError == nil, self.selectedDashboardSubTab == .teamReport {
                    firstError = message
                }
            }

            if let firstError, !firstError.isEmpty {
                self.dashboardErrorMessage = firstError
            }
            self.hasLoadedDashboardInSession = true
        }
    }

    public func loadCccdReviewItems() async {
        guard captureSessionToken() != nil else { return }
        let targetStatus = cccdReviewFilterStatus
        let targetSearch = cccdReviewSearchQuery
        await runDeduplicatedSessionTask(key: "cccd_review:\(targetStatus):\(targetSearch)") { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            self.isLoadingCccdReview = true
            let res = await self.directEntryService.fetchCccdReviewList(
                status: targetStatus,
                search: targetSearch,
                page: 1,
                pageSize: Self.leaveInboxPageSize
            )
            guard !Task.isCancelled, self.isSessionValid(token) else { return }
            self.isLoadingCccdReview = false
            if case .success(let data, _) = res {
                self.cccdReviewItems = data.items
                self.cccdReviewTotal = data.total
                self.cccdReviewPage = 1
            }
        }
    }

    public func loadMoreCccdReviewItems() async {
        guard let token = captureSessionToken(),
              !isLoadingCccdReview,
              !isLoadingMoreCccdReview,
              cccdReviewItems.count < cccdReviewTotal else { return }
        let targetStatus = cccdReviewFilterStatus
        let targetSearch = cccdReviewSearchQuery
        let nextPage = cccdReviewPage + 1
        isLoadingMoreCccdReview = true
        let res = await directEntryService.fetchCccdReviewList(
            status: targetStatus,
            search: targetSearch,
            page: nextPage,
            pageSize: Self.leaveInboxPageSize
        )
        guard !Task.isCancelled, isSessionValid(token) else {
            isLoadingMoreCccdReview = false
            return
        }
        isLoadingMoreCccdReview = false
        if case .success(let data, _) = res {
            let existingIds = Set(cccdReviewItems.map { $0.id })
            let appended = data.items.filter { !existingIds.contains($0.id) }
            cccdReviewItems.append(contentsOf: appended)
            cccdReviewTotal = max(data.total, cccdReviewItems.count)
            cccdReviewPage = nextPage
        }
    }

    public func loadMoreDuplicateCccdReport() async {
        guard let token = captureSessionToken(),
              !isLoadingMoreDuplicateCccd,
              (duplicateCccdReport.page < duplicateCccdReport.totalPages || duplicateCccdReport.groups.count < duplicateCccdReport.totalGroups) else {
            return
        }
        let nextPage = max(1, duplicateCccdReport.page) + 1
        let targetSearch = duplicateSearchQuery
        isLoadingMoreDuplicateCccd = true
        let res = await workReportsService.fetchDuplicateCccdReport(
            search: targetSearch,
            page: nextPage,
            pageSize: Self.leaveInboxPageSize
        )
        guard !Task.isCancelled, isSessionValid(token) else {
            isLoadingMoreDuplicateCccd = false
            return
        }
        isLoadingMoreDuplicateCccd = false
        if case .success(let data, _) = res {
            let existingCccds = Set(duplicateCccdReport.groups.map { $0.cccd })
            let appended = data.groups.filter { !existingCccds.contains($0.cccd) }
            let combined = duplicateCccdReport.groups + appended
            duplicateCccdReport = DuplicateCccdReportData(
                totalGroups: max(data.totalGroups, combined.count),
                groups: combined,
                page: data.page,
                totalPages: data.totalPages
            )
        }
    }

    @discardableResult
    public func resolveCccdReview(
        entryId: Int64,
        action: String,
        correctedCccd: String? = nil,
        note: String = ""
    ) async -> Bool {
        let targetUuid = selectedCccdReviewItem?.entryId == entryId
            ? selectedCccdReviewItem?.entryIdUuid ?? String(entryId)
            : (cccdReviewItems.first(where: { $0.entryId == entryId })?.entryIdUuid ?? String(entryId))
        let targetVer = selectedCccdReviewItem?.entryId == entryId
            ? selectedCccdReviewItem?.entryVersion ?? 1
            : (cccdReviewItems.first(where: { $0.entryId == entryId })?.entryVersion ?? 1)
        let res = await directEntryService.resolveCccdReview(
            entryIdUuid: targetUuid,
            expectedEntryVersion: targetVer,
            action: action,
            correctedCccd: correctedCccd,
            note: note
        )
        switch res {
        case .success(let updated, _):
            selectedCccdReviewItem = updated
            showBanner("Đã cập nhật duyệt CCCD thành công.")
            await loadCccdReviewItems()
            return true
        case .failure(let message, let httpStatus, let errorCode, _):
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 || codeUpper.contains("CONFLICT") {
                await loadCccdReviewItems()
                if let refreshed = cccdReviewItems.first(where: { $0.entryId == entryId || $0.entryIdUuid == targetUuid }) {
                    selectedCccdReviewItem = refreshed
                }
                showBanner(
                    "Dữ liệu hồ sơ đã thay đổi trên máy chủ (phiên bản mới). Nội dung bạn đang nhập được giữ nguyên — vui lòng kiểm tra và xác nhận gửi lại.",
                    isError: true
                )
            } else {
                showBanner(message, isError: true)
            }
            return false
        }
    }

    public func retryCccdQrExtraction(entryId: Int64) async {
        let targetUuid = selectedCccdReviewItem?.entryId == entryId
            ? selectedCccdReviewItem?.entryIdUuid ?? String(entryId)
            : (cccdReviewItems.first(where: { $0.entryId == entryId })?.entryIdUuid ?? String(entryId))
        let res = await directEntryService.retryCccdQrExtraction(entryIdUuid: targetUuid)
        switch res {
        case .success(let updated, _):
            selectedCccdReviewItem = updated
            showBanner("Đã trích xuất lại mã QR CCCD.")
            await loadCccdReviewItems()
        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
        }
    }

    public func withdrawDuplicateCccdEpisode(_ episode: DuplicateCccdEpisode, cccd: String) async {
        let res = await workReportsService.selfWithdrawDuplicateEpisode(
            episodeId: episode.episodeId,
            cccd: cccd,
            maNld: episode.entryIdUuid.isEmpty ? episode.maNld : episode.entryIdUuid,
            entryVersion: episode.entryVersion
        )
        switch res {
        case .success:
            showBanner("Đã rút hồ sơ CCCD trùng cho \(episode.hoTen).")
            let dupRes = await workReportsService.fetchDuplicateCccdReport(search: duplicateSearchQuery)
            if case .success(let dup, _) = dupRes {
                duplicateCccdReport = dup
            }
        case .failure(let message, let httpStatus, let errorCode, _):
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 || codeUpper.contains("CONFLICT") {
                let dupRes = await workReportsService.fetchDuplicateCccdReport(search: duplicateSearchQuery)
                if case .success(let dup, _) = dupRes {
                    duplicateCccdReport = dup
                }
                showBanner(
                    "Hồ sơ đã thay đổi phiên bản trên máy chủ. Dữ liệu mới nhất đã được tải lại — vui lòng kiểm tra và xác nhận rút lại nếu cần.",
                    isError: true
                )
            } else {
                showBanner(message, isError: true)
            }
        }
    }

    // MARK: - Direct Entry ("Cấp mới") Draft Management & Submission

    public func addNewWorkerDraftRow() {
        var newDraft = NewWorkerEntryDraft()
        if let prev = newWorkerDraftRows.last ?? newWorkerDraftRows.first {
            newDraft.projectId = prev.projectId
            newDraft.duAn = prev.duAn
            newDraft.providerType = prev.providerType
            newDraft.recruiterId = prev.recruiterId
            newDraft.nguoiTuyen = prev.nguoiTuyen
            newDraft.laborTypeKey = prev.laborTypeKey
            newDraft.nganhNghe = prev.nganhNghe
            newDraft.ngayVaoLam = prev.ngayVaoLam
            newDraft.trangThai = prev.trangThai
            newDraft.quanLy = prev.quanLy
        }
        newWorkerDraftRows.append(newDraft)
    }

    public func removeNewWorkerDraftRow(rowId: String) {
        newWorkerDraftRows.removeAll(where: { $0.rowId == rowId })
        if newWorkerDraftRows.isEmpty {
            newWorkerDraftRows = [NewWorkerEntryDraft()]
        }
    }

    public func updateNewWorkerDraftRow(rowId: String, mutate: (inout NewWorkerEntryDraft) -> Void) {
        guard let idx = newWorkerDraftRows.firstIndex(where: { $0.rowId == rowId }) else { return }
        var copy = newWorkerDraftRows[idx]
        mutate(&copy)
        newWorkerDraftRows[idx] = copy
    }

    @discardableResult
    public func applyScannedCccdQr(rowId: String, rawQr: String) -> Bool {
        guard let parsed = CccdQrDecoder.parse(rawQr),
              let idx = newWorkerDraftRows.firstIndex(where: { $0.rowId == rowId }) else {
            return false
        }
        newWorkerDraftRows[idx] = CccdQrDecoder.applyToDraft(newWorkerDraftRows[idx], qrData: parsed)
        showBanner("Đã điền tự động CCCD: \(parsed.soCccd) - \(parsed.hoTen)")
        return true
    }

    public func addAttachmentToDraftRow(
        rowId: String,
        docType: String,
        attachment: LocalAttachmentItem
    ) {
        updateNewWorkerDraftRow(rowId: rowId) { draft in
            switch docType {
            case DirectEntryContract.docTypeWorkerPortrait:
                draft.portraitFile = attachment
            case DirectEntryContract.docTypeCccdFront:
                draft.cccdFrontFile = attachment
            case DirectEntryContract.docTypeCccdBack:
                draft.cccdBackFile = attachment
            default:
                draft.contractFiles.append(attachment)
            }
        }
    }

    public func removeSingleAttachmentFromDraftRow(rowId: String, docType: String) {
        updateNewWorkerDraftRow(rowId: rowId) { draft in
            switch docType {
            case DirectEntryContract.docTypeWorkerPortrait:
                draft.portraitFile = nil
            case DirectEntryContract.docTypeCccdFront:
                draft.cccdFrontFile = nil
            case DirectEntryContract.docTypeCccdBack:
                draft.cccdBackFile = nil
            default:
                break
            }
        }
    }

    public func removeContractAttachmentFromDraftRow(rowId: String, attachmentId: String) {
        updateNewWorkerDraftRow(rowId: rowId) { draft in
            draft.contractFiles.removeAll(where: { $0.id == attachmentId })
        }
    }

    public func loadDirectEntryAssigneeOptions(force: Bool = false) async {
        guard isAuthenticated,
              let user = currentUser,
              user.canLoadDirectEntryCatalog,
              captureSessionToken() != nil else {
            directEntryOptions = DirectEntryAssigneeOptions()
            directEntryService.clearCatalogCache()
            return
        }
        if !force && !directEntryOptions.catalogProjects.isEmpty {
            return
        }
        await runDeduplicatedSessionTask(key: "catalog", force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            let res = await self.directEntryService.fetchAssigneeOptions()
            guard !Task.isCancelled, self.isSessionValid(token) else { return }
            if case .success(let opts, _) = res {
                self.directEntryOptions = opts
                if let defaultLabor = opts.catalogLaborTypes.first(where: {
                    $0.displayName.lowercased().contains("thời vụ") ||
                    $0.laborTypeKey.uppercased() == "TEMPORARY" ||
                    $0.laborTypeKey.uppercased().contains("SEASONAL")
                }) ?? opts.catalogLaborTypes.first {
                    for idx in self.newWorkerDraftRows.indices {
                        if self.newWorkerDraftRows[idx].laborTypeKey.isEmpty || self.newWorkerDraftRows[idx].laborTypeKey == "SEASONAL" {
                            self.newWorkerDraftRows[idx].laborTypeKey = defaultLabor.laborTypeKey
                            if self.newWorkerDraftRows[idx].nganhNghe.isEmpty || self.newWorkerDraftRows[idx].nganhNghe == "Thời vụ" {
                                self.newWorkerDraftRows[idx].nganhNghe = defaultLabor.displayName
                            }
                        }
                    }
                }
            }
        }
    }

    public func refreshOwnSubmissions(reset: Bool = true) async {
        guard isAuthenticated,
              let user = currentUser,
              (user.canAccessNewWorkerEntry || user.safeAllowedWorkerTabs.contains("uploader") || user.hasCapability("submission_read")),
              let token = captureSessionToken() else {
            ownSubmissionsFetchGeneration &+= 1
            cancelInFlightTasksWithPrefix("own_submissions:")
            ownSubmissions = []
            ownSubmissionsNextCursor = nil
            ownSubmissionsHasMore = false
            directEntryHistoryBatches = []
            ownSubmissionsErrorMessage = nil
            isLoadingOwnSubmissions = false
            return
        }
        let targetCursor: String? = reset ? nil : ownSubmissionsNextCursor
        if !reset && (targetCursor == nil || !ownSubmissionsHasMore || isLoadingOwnSubmissions) {
            return
        }
        if reset {
            cancelInFlightTasksWithPrefix("own_submissions:cursor:")
        }
        let dedupeKey = reset ? "own_submissions:first" : "own_submissions:cursor:\(targetCursor ?? "")"
        let compositeKey = "gen:\(token.generation):uid:\(token.userId.lowercased()):\(dedupeKey)"
        if inFlightTasks[compositeKey] == nil {
            ownSubmissionsFetchGeneration &+= 1
        }
        let expectedFetchGen = ownSubmissionsFetchGeneration
        await runDeduplicatedSessionTask(key: dedupeKey) { [weak self] token in
            guard let self,
                  self.isSessionValid(token),
                  self.ownSubmissionsFetchGeneration == expectedFetchGen else {
                return
            }
            self.isLoadingOwnSubmissions = true
            if reset {
                self.ownSubmissionsErrorMessage = nil
            }
            let res = await self.directEntryService.listOwnSubmissions(pageSize: 20, cursor: targetCursor)
            guard !Task.isCancelled,
                  self.isSessionValid(token),
                  self.ownSubmissionsFetchGeneration == expectedFetchGen,
                  (reset || self.ownSubmissionsNextCursor == targetCursor) else {
                return
            }
            self.isLoadingOwnSubmissions = false
            switch res {
            case .success(let page, _):
                if reset {
                    self.ownSubmissions = page.items
                } else {
                    let existingIds = Set(self.ownSubmissions.map { $0.submissionIdUuid.isEmpty ? String($0.batchId) : $0.submissionIdUuid })
                    let appended = page.items.filter {
                        let key = $0.submissionIdUuid.isEmpty ? String($0.batchId) : $0.submissionIdUuid
                        return !existingIds.contains(key)
                    }
                    self.ownSubmissions.append(contentsOf: appended)
                }
                self.ownSubmissionsNextCursor = page.nextCursor
                self.ownSubmissionsHasMore = page.hasMore
                self.directEntryHistoryBatches = self.ownSubmissions
                self.ownSubmissionsErrorMessage = nil
            case .failure(let message, _, _, _):
                guard !res.isCancelledRequest else { return }
                if reset {
                    self.ownSubmissions = []
                    self.ownSubmissionsNextCursor = nil
                    self.ownSubmissionsHasMore = false
                    self.directEntryHistoryBatches = []
                }
                self.ownSubmissionsErrorMessage = message
            }
        }
    }

    public func loadMoreOwnSubmissions() async {
        await refreshOwnSubmissions(reset: false)
    }

    public func loadDirectEntryBatchHistory() async {
        await refreshOwnSubmissions(reset: true)
    }

    /// Validates, checks duplicate CCCDs, submits valid rows in `newWorkerDraftRows`, uploads their documents
    /// (automatically merging 2+ contract images into 1 PDF), and removes ONLY the rows that succeeded!
    public func submitNewWorkerDraftBatch(confirmedDuplicates: Bool = false) async {
        guard let token = captureSessionToken(),
              let user = currentUser,
              user.canAccessNewWorkerEntry else {
            return
        }
        let validRows = newWorkerDraftRows.filter { $0.isReadyForSubmit }
        if validRows.isEmpty {
            if let firstRow = newWorkerDraftRows.first, let err = firstRow.validateAndroidContractError() {
                showBanner(err, isError: true)
            } else {
                showBanner("Vui lòng chọn Dự án, Người tuyển / Vendor, Loại hình LĐ và nhập Số CCCD (12 chữ số).", isError: true)
            }
            return
        }

        isSubmittingNewWorkers = true
        defer {
            if isSessionValid(token) {
                isSubmittingNewWorkers = false
            }
        }

        if !confirmedDuplicates {
            let checkRes = await directEntryService.checkDuplicates(drafts: validRows)
            guard !Task.isCancelled, isSessionValid(token) else { return }
            if case .success(let duplicates, _) = checkRes, !duplicates.isEmpty {
                pendingDuplicateConfirmCandidates = duplicates
                showDuplicateConfirmDialog = true
                return
            }
        }

        showDuplicateConfirmDialog = false
        pendingDuplicateConfirmCandidates = []

        let submitRes = await directEntryService.submitBatch(
            drafts: validRows,
            confirmDuplicates: confirmedDuplicates
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }

        switch submitRes {
        case .success(let outcome, _):
            let canUploadDocs = currentUser?.hasCapability("document_upload") == true
            // Upload staged documents (Portrait + CCCD front/back + multi-image merged PDF contract) for each created entry
            if canUploadDocs {
                for row in validRows {
                    guard !Task.isCancelled, isSessionValid(token) else { return }
                    let entryIdUuid = outcome.entryIdUuidByRowId[row.rowId]
                        ?? outcome.entryIdByRowId[row.rowId].map { String($0) }
                    if let entryIdUuid, !entryIdUuid.isEmpty {
                        var currentEntryVersion: Int = 1
                        if let portrait = row.portraitFile {
                            let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                                entryIdUuid: entryIdUuid,
                                docType: DirectEntryContract.docTypeWorkerPortrait,
                                attachments: [portrait],
                                initialEntryVersion: currentEntryVersion
                            )
                            guard !Task.isCancelled, isSessionValid(token) else { return }
                            currentEntryVersion = up.nextEntryVersion
                        }
                        if let front = row.cccdFrontFile {
                            let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                                entryIdUuid: entryIdUuid,
                                docType: DirectEntryContract.docTypeCccdFront,
                                attachments: [front],
                                initialEntryVersion: currentEntryVersion
                            )
                            guard !Task.isCancelled, isSessionValid(token) else { return }
                            currentEntryVersion = up.nextEntryVersion
                        }
                        if let back = row.cccdBackFile {
                            let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                                entryIdUuid: entryIdUuid,
                                docType: DirectEntryContract.docTypeCccdBack,
                                attachments: [back],
                                initialEntryVersion: currentEntryVersion
                            )
                            guard !Task.isCancelled, isSessionValid(token) else { return }
                            currentEntryVersion = up.nextEntryVersion
                        }
                        if !row.contractFiles.isEmpty {
                            let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                                entryIdUuid: entryIdUuid,
                                docType: DirectEntryContract.docTypeEmploymentContract,
                                attachments: row.contractFiles,
                                initialEntryVersion: currentEntryVersion
                            )
                            guard !Task.isCancelled, isSessionValid(token) else { return }
                            currentEntryVersion = up.nextEntryVersion
                        }
                    }
                }
            }

            guard !Task.isCancelled, isSessionValid(token) else { return }
            // Preserve any unsaved/incomplete draft rows; only remove rows that were saved!
            let savedRowIdSet = Set(validRows.map { $0.rowId })
            let remainingRows = newWorkerDraftRows.filter { !savedRowIdSet.contains($0.rowId) }
            newWorkerDraftRows = remainingRows.isEmpty ? [NewWorkerEntryDraft()] : remainingRows

            showBanner("Đã lưu thành công \(outcome.createdCount) hồ sơ cấp mới.")
            await loadDirectEntryBatchHistory()

        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
        }
    }

    // MARK: - Workers Management Actions

    public func selectWorkerScope(_ scope: String) {
        let allowedKeys = visibleWorkerTabs.map(\.key)
        let trimmed = scope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard allowedKeys.contains(trimmed) else { return }
        guard selectedWorkerScope != trimmed else { return }
        selectedWorkerScope = trimmed
        Task { await loadWorkers(resetPage: true) }
    }

    public func loadWorkers(resetPage: Bool = false, force: Bool = false) async {
        guard captureSessionToken() != nil else {
            selectedWorkerScope = ""
            workers = []
            workersTotalCount = 0
            workersPage = 1
            workersTotalPages = 1
            workersHasMore = false
            workersNextCursor = nil
            isLoadingWorkers = false
            return
        }
        let allowedKeys = visibleWorkerTabs.map(\.key)
        guard !allowedKeys.isEmpty else {
            selectedWorkerScope = ""
            workers = []
            workersTotalCount = 0
            workersPage = 1
            workersTotalPages = 1
            workersHasMore = false
            workersNextCursor = nil
            isLoadingWorkers = false
            return
        }
        let trimmedRequested = selectedWorkerScope.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let resolvedScope: String
        if trimmedRequested.isEmpty {
            resolvedScope = allowedKeys[0]
            selectedWorkerScope = resolvedScope
        } else if allowedKeys.contains(trimmedRequested) {
            resolvedScope = trimmedRequested
            selectedWorkerScope = resolvedScope
        } else {
            workers = []
            workersTotalCount = 0
            workersTotalPages = 1
            workersHasMore = false
            workersNextCursor = nil
            isLoadingWorkers = false
            return
        }

        if resetPage {
            workersPage = 1
            workersNextCursor = nil
        }
        let targetSearch = workerSearchQuery
        let targetProject = workerProjectFilter
        let targetStatus = workerStatusFilter
        let dedupeKey = "workers:\(resolvedScope):\(targetSearch):\(targetProject):\(targetStatus)"

        await runDeduplicatedSessionTask(key: dedupeKey, force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            self.isLoadingWorkers = true
            defer {
                if self.isSessionValid(token) {
                    self.isLoadingWorkers = false
                }
            }
            let res = await self.repository.fetchWorkers(
                scope: resolvedScope,
                search: targetSearch,
                project: targetProject,
                status: targetStatus,
                cursor: nil,
                pageSize: Self.leaveInboxPageSize
            )
            guard !Task.isCancelled,
                  self.isSessionValid(token),
                  self.selectedWorkerScope == resolvedScope,
                  self.workerSearchQuery == targetSearch,
                  self.workerProjectFilter == targetProject,
                  self.workerStatusFilter == targetStatus else {
                return
            }
            switch res {
            case .success(let data, _):
                self.workers = data.items
                self.workersTotalCount = data.total
                self.workersNextCursor = data.nextCursor
                self.workersHasMore = data.hasMore && !(data.nextCursor ?? "").isEmpty
                self.workersPage = 1
                self.workersTotalPages = self.workersHasMore ? 2 : 1
            case .failure(let message, _, _, _):
                guard !res.isCancelledRequest else { return }
                self.workers = []
                self.workersTotalCount = 0
                self.workersNextCursor = nil
                self.workersHasMore = false
                self.workersTotalPages = 1
                self.showBanner(message, isError: true)
            }
        }
    }

    public func loadMoreWorkers() async {
        guard let token = captureSessionToken() else { return }
        let allowedTabs = visibleWorkerTabs.map(\.key)
        guard allowedTabs.contains(selectedWorkerScope),
              !isLoadingWorkers,
              !isLoadingMoreWorkers,
              workersHasMore,
              let cursor = workersNextCursor,
              !cursor.isEmpty else {
            return
        }
        let resolvedScope = selectedWorkerScope
        let targetSearch = workerSearchQuery
        let targetProject = workerProjectFilter
        let targetStatus = workerStatusFilter

        isLoadingMoreWorkers = true
        defer {
            isLoadingMoreWorkers = false
        }
        let res = await repository.fetchWorkers(
            scope: resolvedScope,
            search: targetSearch,
            project: targetProject,
            status: targetStatus,
            cursor: cursor,
            pageSize: Self.leaveInboxPageSize
        )
        guard !Task.isCancelled,
              isSessionValid(token),
              selectedWorkerScope == resolvedScope,
              workerSearchQuery == targetSearch,
              workerProjectFilter == targetProject,
              workerStatusFilter == targetStatus else {
            return
        }
        switch res {
        case .success(let data, _):
            let existingIds = Set(workers.map { $0.id })
            let appended = data.items.filter { !existingIds.contains($0.id) }
            workers.append(contentsOf: appended)
            workersNextCursor = data.nextCursor
            workersHasMore = data.hasMore && !(data.nextCursor ?? "").isEmpty
            workersTotalCount = max(data.total, workers.count)
            workersPage += 1
            workersTotalPages = workersHasMore ? (workersPage + 1) : workersPage
        case .failure(let message, _, _, _):
            guard !res.isCancelledRequest else { return }
            showBanner(message, isError: true)
        }
    }

    public func uploadWorkerContract(maNld: String, attachments: [LocalAttachmentItem]) async -> Bool {
        guard let token = captureSessionToken(),
              let user = currentUser,
              user.hasCapability("document_upload"),
              !attachments.isEmpty else {
            return false
        }
        isUploadingWorkerContract = true
        let res = await repository.uploadWorkerContractAttachments(maNld: maNld, attachments: attachments)
        guard !Task.isCancelled, isSessionValid(token) else { return false }
        isUploadingWorkerContract = false
        switch res {
        case .success(let fileUrl, _):
            if var detail = selectedWorkerDetail, detail.maNld == maNld {
                detail.contractFileUrl = fileUrl
                selectedWorkerDetail = detail
            }
            if let idx = workers.firstIndex(where: { $0.maNld == maNld }) {
                workers[idx].contractFileUrl = fileUrl
            }
            let summary = attachments.count >= 2
                ? "Đã ghép \(attachments.count) ảnh thành 1 file PDF và tải lên hợp đồng thành công."
                : "Đã tải lên hồ sơ hợp đồng thành công."
            showBanner(summary)
            return true
        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
            return false
        }
    }

    // MARK: - Direct Edit ("Sửa trực tiếp hồ sơ NLĐ") Actions

    public func openDirectEdit(_ worker: WorkerItem) async {
        guard let token = captureSessionToken(), let user = currentUser else { return }
        guard user.hasCapability("entry_direct_edit") && worker.canDirectEdit else {
            showBanner("Bạn không có quyền sửa trực tiếp hồ sơ này.", isError: true)
            return
        }
        openDirectEditGeneration &+= 1
        let expectedOpenGen = openDirectEditGeneration
        let targetEntryId = worker.maNld
        directEditWorkerTarget = worker
        directEditBaselineDetail = nil
        directEditLoadingDetail = true
        directEditSavingProfile = false
        directEditErrorMessage = nil
        directEditFieldErrors = [:]
        directEditProfileSavedMessage = nil
        directEditFileStatuses = [:]

        if directEntryOptions.catalogProjects.isEmpty {
            await loadDirectEntryAssigneeOptions()
            guard !Task.isCancelled,
                  isSessionValid(token),
                  openDirectEditGeneration == expectedOpenGen,
                  directEditWorkerTarget?.maNld == targetEntryId else {
                return
            }
        }

        let detailRes = await directEntryService.fetchWorkerEntryDetail(entryId: targetEntryId)
        guard !Task.isCancelled,
              isSessionValid(token),
              openDirectEditGeneration == expectedOpenGen,
              directEditWorkerTarget?.maNld == targetEntryId else {
            return
        }
        directEditLoadingDetail = false

        switch detailRes {
        case .success(let detail, _):
            guard detail.entryId.isEmpty || detail.entryId.caseInsensitiveCompare(targetEntryId) == .orderedSame else {
                return
            }
            directEditBaselineDetail = detail
            if !detail.canDirectEdit || currentUser?.hasCapability("entry_direct_edit") != true {
                directEditErrorMessage = "Tài khoản hiện tại không có quyền sửa trực tiếp hồ sơ này."
            }
        case .failure(let message, _, _, _):
            guard !detailRes.isCancelledRequest else { return }
            directEditErrorMessage = message
        }
    }

    public func closeDirectEdit() {
        openDirectEditGeneration &+= 1
        directEditWorkerTarget = nil
        directEditBaselineDetail = nil
        directEditLoadingDetail = false
        directEditSavingProfile = false
        directEditErrorMessage = nil
        directEditFieldErrors = [:]
        directEditProfileSavedMessage = nil
        directEditFileStatuses = [:]
    }

    public func submitDirectEditProfileAndDocuments(
        form: DirectEditFormInput,
        stagedFiles: [String: [LocalAttachmentItem]],
        onAllComplete: (() -> Void)? = nil
    ) async {
        guard !directEditSavingProfile else { return }
        guard let token = captureSessionToken(),
              let user = currentUser,
              let baseline = directEditBaselineDetail else {
            return
        }

        directEditErrorMessage = nil
        directEditFieldErrors = [:]

        let validationErrors = DirectEntryService.validateDirectEditFormFields(baseline: baseline, input: form)
        if !validationErrors.isEmpty {
            directEditFieldErrors = validationErrors
            let firstError = validationErrors.values.first ?? "Dữ liệu chỉnh sửa chưa hợp lệ."
            directEditErrorMessage = validationErrors.count == 1
                ? firstError
                : "Có \(validationErrors.count) trường chưa hợp lệ: \(firstError)"
            return
        }

        let patch = DirectEntryService.buildDirectEditPatch(baseline: baseline, input: form)
        let requestedStaged = stagedFiles.filter { !$0.value.isEmpty }
        let canDirectEdit = user.hasCapability("entry_direct_edit") && baseline.canDirectEdit
        let canUploadDocs = user.hasCapability("document_upload")

        if !patch.isEmpty && !canDirectEdit {
            directEditErrorMessage = "Tài khoản hiện tại không có quyền sửa trực tiếp hồ sơ này."
            return
        }
        if patch.isEmpty && !requestedStaged.isEmpty && !canUploadDocs {
            directEditErrorMessage = "Tài khoản hiện tại không có quyền tải tệp đính kèm."
            return
        }

        let nonEmptyStaged = canUploadDocs ? requestedStaged : [:]

        if patch.isEmpty && nonEmptyStaged.isEmpty {
            directEditErrorMessage = "Chưa có thay đổi thông tin hoặc tệp đính kèm nào để lưu."
            return
        }

        var currentVersion = baseline.entryVersion
        let targetEntryId = baseline.entryId

        // Step 1: Save profile fields via privileged-edit API first (if any field changed or if profile hasn't been saved yet)
        if !patch.isEmpty {
            directEditSavingProfile = true
            let editRes = await directEntryService.submitPrivilegedEdit(
                entryId: targetEntryId,
                expectedEntryVersion: currentVersion,
                patch: patch
            )
            guard !Task.isCancelled, isSessionValid(token) else { return }
            directEditSavingProfile = false

            switch editRes {
            case .success(let updatedDetail, _):
                currentVersion = updatedDetail.entryVersion
                directEditBaselineDetail = updatedDetail
                directEditProfileSavedMessage = "Đã lưu thông tin hồ sơ (v\(currentVersion))."
                // Refresh baseline so subsequent retries don't re-submit already saved profile changes
                let refreshRes = await directEntryService.fetchWorkerEntryDetail(entryId: targetEntryId)
                guard !Task.isCancelled, isSessionValid(token) else { return }
                if case .success(let refreshed, _) = refreshRes {
                    directEditBaselineDetail = refreshed
                    currentVersion = refreshed.entryVersion
                }

            case .failure(let message, let httpStatus, let errorCode, _):
                let codeUpper = (errorCode ?? "").uppercased()
                if httpStatus == 409 || codeUpper.contains("CONFLICT") {
                    let refreshRes = await directEntryService.fetchWorkerEntryDetail(entryId: targetEntryId)
                    guard !Task.isCancelled, isSessionValid(token) else { return }
                    if case .success(let refreshed, _) = refreshRes {
                        directEditBaselineDetail = refreshed
                    }
                    directEditErrorMessage = "Hồ sơ đã được cập nhật ở phiên bản mới trên máy chủ. Nội dung bạn vừa chỉnh sửa vẫn được giữ nguyên — vui lòng kiểm tra và bấm Lưu lại để xác nhận."
                } else {
                    directEditErrorMessage = message
                }
                return
            }
        }

        // Step 2: Upload staged documents sequentially via private R2 upload (reserve -> PUT -> finalize)
        var anyFileFailed = false
        if !nonEmptyStaged.isEmpty {
            let orderedDocTypes = [
                DirectEntryContract.docTypeWorkerPortrait,
                DirectEntryContract.docTypeCccdFront,
                DirectEntryContract.docTypeCccdBack,
                DirectEntryContract.docTypeEmploymentContract,
            ]
            for docType in orderedDocTypes {
                guard !Task.isCancelled, isSessionValid(token) else { return }
                guard let files = nonEmptyStaged[docType], !files.isEmpty else { continue }
                let displayName = files.count >= 2
                    ? "\(files.count) tệp ảnh (ghép PDF)"
                    : (files.first?.fileName ?? docType)
                directEditFileStatuses[docType] = DirectEditFileStatus(
                    docType: docType,
                    fileName: displayName,
                    status: .uploading,
                    message: "Đang tải tệp..."
                )
                let upRes = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                    entryIdUuid: targetEntryId,
                    docType: docType,
                    attachments: files,
                    initialEntryVersion: currentVersion
                )
                guard !Task.isCancelled, isSessionValid(token) else { return }
                currentVersion = upRes.nextEntryVersion
                if var cur = directEditBaselineDetail {
                    cur.entryVersion = currentVersion
                    directEditBaselineDetail = cur
                }
                switch upRes.result {
                case .success:
                    directEditFileStatuses[docType] = DirectEditFileStatus(
                        docType: docType,
                        fileName: displayName,
                        status: .success,
                        message: "Đã tải tệp thành công."
                    )
                case .failure(let errMsg, let httpStatus, let errorCode, _):
                    anyFileFailed = true
                    let codeUpper = (errorCode ?? "").uppercased()
                    if httpStatus == 409 || codeUpper.contains("CONFLICT") {
                        let conflictRefresh = await directEntryService.fetchWorkerEntryDetail(entryId: targetEntryId)
                        guard !Task.isCancelled, isSessionValid(token) else { return }
                        if case .success(let refreshed, _) = conflictRefresh {
                            directEditBaselineDetail = refreshed
                            currentVersion = refreshed.entryVersion
                        }
                    }
                    directEditFileStatuses[docType] = DirectEditFileStatus(
                        docType: docType,
                        fileName: displayName,
                        status: .error,
                        message: errMsg
                    )
                }
            }

            let finalRefresh = await directEntryService.fetchWorkerEntryDetail(entryId: targetEntryId)
            guard !Task.isCancelled, isSessionValid(token) else { return }
            if case .success(let refreshed, _) = finalRefresh {
                directEditBaselineDetail = refreshed
            }
        }

        guard !Task.isCancelled, isSessionValid(token) else { return }
        await loadWorkers(resetPage: false)
        guard !Task.isCancelled, isSessionValid(token) else { return }

        if anyFileFailed {
            directEditErrorMessage = "Một số tệp đính kèm tải lên chưa thành công. Thông tin hồ sơ đã được bảo toàn, bạn có thể bấm Thử lại trên từng tệp lỗi."
        } else {
            showBanner("Đã lưu hồ sơ NLĐ thành công.")
            onAllComplete?()
            closeDirectEdit()
        }
    }

    public func retryDirectEditDocument(
        docType: String,
        attachments: [LocalAttachmentItem],
        onComplete: (() -> Void)? = nil
    ) async {
        guard let token = captureSessionToken(),
              let user = currentUser,
              user.hasCapability("document_upload"),
              let baseline = directEditBaselineDetail,
              !attachments.isEmpty else {
            return
        }
        let targetEntryId = baseline.entryId
        let displayName = attachments.count >= 2
            ? "\(attachments.count) tệp ảnh (ghép PDF)"
            : (attachments.first?.fileName ?? docType)

        directEditFileStatuses[docType] = DirectEditFileStatus(
            docType: docType,
            fileName: displayName,
            status: .uploading,
            message: "Đang thử tải lại..."
        )

        let upRes = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
            entryIdUuid: targetEntryId,
            docType: docType,
            attachments: attachments,
            initialEntryVersion: baseline.entryVersion
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }

        let refreshRes = await directEntryService.fetchWorkerEntryDetail(entryId: targetEntryId)
        guard !Task.isCancelled, isSessionValid(token) else { return }
        if case .success(let refreshed, _) = refreshRes {
            directEditBaselineDetail = refreshed
        } else if var cur = directEditBaselineDetail {
            cur.entryVersion = upRes.nextEntryVersion
            directEditBaselineDetail = cur
        }

        switch upRes.result {
        case .success:
            directEditFileStatuses[docType] = DirectEditFileStatus(
                docType: docType,
                fileName: displayName,
                status: .success,
                message: "Đã tải tệp thành công."
            )
            if !directEditFileStatuses.values.contains(where: { $0.status == .error }) {
                directEditErrorMessage = nil
            }
            await loadWorkers(resetPage: false)
            guard !Task.isCancelled, isSessionValid(token) else { return }
            onComplete?()
        case .failure(let errMsg, let httpStatus, let errorCode, _):
            directEditFileStatuses[docType] = DirectEditFileStatus(
                docType: docType,
                fileName: displayName,
                status: .error,
                message: errMsg
            )
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 || codeUpper.contains("CONFLICT") {
                directEditErrorMessage = "Hồ sơ đã được cập nhật ở phiên bản mới trên máy chủ. Dữ liệu mới nhất đã được tải lại — vui lòng kiểm tra và bấm Thử lại để xác nhận."
            }
        }
    }

    // MARK: - Salary Advance ("Ứng lương" for QLDA) Actions

    public var canShowPmSalaryAdvanceButton: Bool {
        SalaryAdvanceContract.canShowPmSalaryAdvanceEntry(salaryAdvanceContext)
    }

    public var selectedSalaryAdvanceWorker: SalaryAdvanceWorkerOption? {
        guard let selectedId = salaryAdvanceSelectedWorkerId else { return nil }
        return salaryAdvanceWorkers.first(where: { $0.workerEntryId == selectedId })
    }

    public func loadSalaryAdvanceContext(force: Bool = false) async {
        guard isAuthenticated,
              let user = currentUser,
              user.canAttemptPmSalaryAdvanceContext,
              captureSessionToken() != nil else {
            salaryAdvanceContext = nil
            salaryAdvanceSelectedProjectId = nil
            salaryAdvanceContextLoadedGeneration = nil
            return
        }
        if !force && salaryAdvanceContextLoadedGeneration == sessionGeneration && salaryAdvanceContext != nil {
            return
        }
        await runDeduplicatedSessionTask(key: "salary_advance_context", force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            let res = await self.salaryAdvanceService.fetchContext()
            guard !Task.isCancelled, self.isSessionValid(token) else { return }
            switch res {
            case .success(let ctx, _):
                let ctxUserId = ctx.appUserId.trimmingCharacters(in: .whitespacesAndNewlines)
                if !ctxUserId.isEmpty && ctxUserId.caseInsensitiveCompare(token.userId) != .orderedSame {
                    self.salaryAdvanceContext = nil
                    self.salaryAdvanceSelectedProjectId = nil
                    self.salaryAdvanceContextLoadedGeneration = nil
                    return
                }
                let activeProjects = ctx.activeManagedProjects
                self.salaryAdvanceContext = SalaryAdvanceActorContext(
                    appUserId: ctx.appUserId,
                    displayName: ctx.displayName,
                    authorizationDate: ctx.authorizationDate,
                    isFullAdmin: ctx.isFullAdmin,
                    isBod: ctx.isBod,
                    isProjectManager: ctx.isProjectManager,
                    isProjectAccountant: ctx.isProjectAccountant,
                    isSupervisoryViewer: ctx.isSupervisoryViewer,
                    managedProjects: activeProjects
                )
                self.salaryAdvanceContextLoadedGeneration = token.generation
                let currentProjectId = self.salaryAdvanceSelectedProjectId
                let resolvedProjectId: String?
                if let currentProjectId, activeProjects.contains(where: { $0.projectId == currentProjectId }) {
                    resolvedProjectId = currentProjectId
                } else {
                    resolvedProjectId = activeProjects.first?.projectId
                }
                self.salaryAdvanceSelectedProjectId = resolvedProjectId
            case .failure:
                self.salaryAdvanceContext = nil
                self.salaryAdvanceSelectedProjectId = nil
                self.salaryAdvanceContextLoadedGeneration = token.generation
            }
        }
    }

    public func openSalaryAdvanceSheet() async {
        if salaryAdvanceContext == nil {
            await loadSalaryAdvanceContext()
        }
        guard SalaryAdvanceContract.canShowPmSalaryAdvanceEntry(salaryAdvanceContext) else {
            showBanner("Tài khoản hiện tại không có dự án QLDA để tạo đề nghị ứng lương.", isError: true)
            return
        }
        let projects = salaryAdvanceContext?.activeManagedProjects ?? []
        let resolvedProjectId: String?
        if let cur = salaryAdvanceSelectedProjectId, projects.contains(where: { $0.projectId == cur }) {
            resolvedProjectId = cur
        } else {
            resolvedProjectId = projects.first?.projectId
        }

        salaryAdvanceSheetOpen = true
        salaryAdvanceWorkspaceTab = "CREATE"
        salaryAdvanceSelectedProjectId = resolvedProjectId
        salaryAdvanceWorkerSearch = ""
        salaryAdvanceWorkerEligibilityFilter = "ALL"
        salaryAdvanceWorkerPickerExpanded = true
        salaryAdvanceWorkers = []
        salaryAdvanceWorkersLoading = false
        salaryAdvanceWorkersLoadingMore = false
        salaryAdvanceWorkersError = nil
        salaryAdvanceWorkersNextCursor = nil
        salaryAdvanceWorkersHasMore = false
        salaryAdvanceSelectedWorkerId = nil
        salaryAdvancePaymentMethod = SalaryAdvanceContract.paymentMethodCash
        salaryAdvanceAmountRaw = ""
        salaryAdvanceInitialContent = ""
        salaryAdvanceCreateSubmitting = false
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
        salaryAdvanceHistoryOpen = false

        await refreshSalaryAdvanceRequests()
    }

    public func closeSalaryAdvanceSheet() {
        salaryAdvanceSearchTask?.cancel()
        salaryAdvanceListSearchTask?.cancel()
        salaryAdvanceSheetOpen = false
        salaryAdvanceCreateSubmitting = false
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
        salaryAdvanceHistoryOpen = false
    }

    public func selectSalaryAdvanceWorkspaceTab(_ tab: String) {
        let clean = tab.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard clean == "CREATE" || clean == "LIST" || clean == "DETAIL" else { return }
        salaryAdvanceWorkspaceTab = clean
        if clean == "LIST" {
            Task { await refreshSalaryAdvanceRequests() }
        }
    }

    public func updateSalaryAdvanceWorkerEligibilityFilter(_ filterCode: String) {
        let clean = filterCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        salaryAdvanceWorkerEligibilityFilter = clean.isEmpty ? "ALL" : clean
    }

    public func expandSalaryAdvanceWorkerPicker(_ expanded: Bool = true) {
        salaryAdvanceWorkerPickerExpanded = expanded
    }

    public func refreshSalaryAdvanceCreateTab() async {
        guard !salaryAdvanceCreateSubmitting else { return }
        await loadSalaryAdvanceContext()
        await refreshSalaryAdvanceRequests()
        let projectId = (salaryAdvanceSelectedProjectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let currentQuery = salaryAdvanceWorkerSearch.trimmingCharacters(in: .whitespacesAndNewlines)
        if !projectId.isEmpty && !currentQuery.isEmpty {
            await loadSalaryAdvanceProjectWorkers(
                projectId: projectId,
                search: currentQuery,
                cursor: nil,
                append: false
            )
        }
    }

    public func selectSalaryAdvanceProject(_ projectId: String) async {
        let trimmed = projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        salaryAdvanceSelectedProjectId = trimmed
        salaryAdvanceSelectedWorkerId = nil
        salaryAdvanceWorkerPickerExpanded = true
        salaryAdvanceWorkers = []
        salaryAdvanceWorkersLoading = false
        salaryAdvanceWorkersLoadingMore = false
        salaryAdvanceWorkersError = nil
        salaryAdvanceWorkersNextCursor = nil
        salaryAdvanceWorkersHasMore = false
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
        let currentQuery = salaryAdvanceWorkerSearch.trimmingCharacters(in: .whitespacesAndNewlines)
        if !currentQuery.isEmpty {
            await loadSalaryAdvanceProjectWorkers(
                projectId: trimmed,
                search: currentQuery,
                cursor: nil,
                append: false
            )
        }
    }

    public func updateSalaryAdvanceWorkerSearch(_ query: String) {
        let clean = String(query.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ").prefix(120))
        salaryAdvanceWorkerSearch = clean
        salaryAdvanceWorkerPickerExpanded = true
        salaryAdvanceSearchTask?.cancel()
        let trimmed = clean.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            salaryAdvanceWorkersLoading = false
            salaryAdvanceWorkersLoadingMore = false
            salaryAdvanceWorkersError = nil
            salaryAdvanceWorkersHasMore = false
            salaryAdvanceWorkersNextCursor = nil
            if let selectedId = salaryAdvanceSelectedWorkerId, !selectedId.isEmpty {
                salaryAdvanceWorkers = salaryAdvanceWorkers.filter { $0.workerEntryId == selectedId }
            } else {
                salaryAdvanceWorkers = []
            }
            return
        }
        guard let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else { return }
        salaryAdvanceSearchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            await self?.loadSalaryAdvanceProjectWorkers(
                projectId: projectId,
                search: trimmed,
                cursor: nil,
                append: false
            )
        }
    }

    public func loadMoreSalaryAdvanceWorkers() async {
        let currentQuery = salaryAdvanceWorkerSearch.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !currentQuery.isEmpty,
              !salaryAdvanceWorkersLoading,
              !salaryAdvanceWorkersLoadingMore,
              salaryAdvanceWorkersHasMore,
              let cursor = salaryAdvanceWorkersNextCursor, !cursor.isEmpty,
              let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else {
            return
        }
        await loadSalaryAdvanceProjectWorkers(
            projectId: projectId,
            search: currentQuery,
            cursor: cursor,
            append: true
        )
    }

    public func loadSalaryAdvanceProjectWorkers(
        projectId: String,
        search: String,
        cursor: String?,
        append: Bool
    ) async {
        let trimmedSearch = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSearch.isEmpty else { return }

        if append {
            salaryAdvanceWorkersLoadingMore = true
        } else {
            salaryAdvanceWorkersLoading = true
            salaryAdvanceWorkersError = nil
        }

        let res = await salaryAdvanceService.listProjectWorkers(
            projectId: projectId,
            search: trimmedSearch,
            cursor: cursor,
            pageSize: 30
        )

        guard salaryAdvanceSelectedProjectId == projectId else {
            salaryAdvanceWorkersLoading = false
            salaryAdvanceWorkersLoadingMore = false
            return
        }

        switch res {
        case .success(var page, _):
            var filtered = SalaryAdvanceContract.filterSelectableProjectWorkers(page.workers)
            if !append && filtered.isEmpty {
                let fallbackRes = await salaryAdvanceService.listProjectWorkers(
                    projectId: projectId,
                    search: "",
                    cursor: nil,
                    pageSize: 100
                )
                if case .success(let fallbackPage, _) = fallbackRes {
                    let fallbackSelectable = SalaryAdvanceContract.filterSelectableProjectWorkers(fallbackPage.workers)
                    filtered = fallbackSelectable.filter { worker in
                        SalaryAdvanceContract.matchesWorkerSearchQuery(
                            worker: worker,
                            query: trimmedSearch,
                            eligibilityFilter: "ALL"
                        )
                    }
                    page = SalaryAdvanceProjectWorkersPage(
                        projectId: page.projectId,
                        nextCursor: nil,
                        hasMore: false,
                        workers: filtered
                    )
                }
            }

            if append {
                salaryAdvanceWorkersLoadingMore = false
            } else {
                salaryAdvanceWorkersLoading = false
            }

            let combined: [SalaryAdvanceWorkerOption]
            if append {
                let existingIds = Set(salaryAdvanceWorkers.map { $0.workerEntryId })
                combined = salaryAdvanceWorkers + filtered.filter { !existingIds.contains($0.workerEntryId) }
            } else {
                if let cur = salaryAdvanceSelectedWorkerId, !cur.isEmpty,
                   !filtered.contains(where: { $0.workerEntryId == cur }),
                   let preserved = salaryAdvanceWorkers.first(where: { $0.workerEntryId == cur }) {
                    combined = [preserved] + filtered
                } else {
                    combined = filtered
                }
            }
            salaryAdvanceWorkers = combined
            salaryAdvanceWorkersError = nil
            salaryAdvanceWorkersNextCursor = page.nextCursor
            salaryAdvanceWorkersHasMore = page.hasMore

        case .failure(let message, _, _, _):
            if append {
                salaryAdvanceWorkersLoadingMore = false
            } else {
                salaryAdvanceWorkersLoading = false
            }
            guard !res.isCancelledRequest else { return }
            salaryAdvanceWorkersError = message
        }
    }

    public func selectSalaryAdvanceWorker(_ workerEntryId: String) {
        guard !salaryAdvanceCreateSubmitting else { return }
        let trimmed = workerEntryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let matched = salaryAdvanceWorkers.first(where: { $0.workerEntryId == trimmed })
        let currentMethod = salaryAdvancePaymentMethod
        let nextMethod: String
        if currentMethod == SalaryAdvanceContract.paymentMethodBankTransfer,
           let matched,
           !matched.bankTransferEligible {
            nextMethod = SalaryAdvanceContract.paymentMethodCash
        } else {
            nextMethod = currentMethod
        }
        salaryAdvanceSelectedWorkerId = trimmed
        salaryAdvancePaymentMethod = nextMethod
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
        if let matched, SalaryAdvanceContract.describeWorkerEligibility(matched).isEligible {
            salaryAdvanceWorkerPickerExpanded = false
        }
    }

    public func updateSalaryAdvancePaymentMethod(_ method: String) {
        let normalized = method.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard normalized == SalaryAdvanceContract.paymentMethodCash ||
              normalized == SalaryAdvanceContract.paymentMethodBankTransfer else { return }
        salaryAdvancePaymentMethod = normalized
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
    }

    public func updateSalaryAdvanceAmount(_ rawAmount: String) {
        let digitsOnly = String(rawAmount.filter { $0.isNumber }.prefix(12))
        salaryAdvanceAmountRaw = digitsOnly
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
    }

    public func updateSalaryAdvanceInitialContent(_ content: String) {
        salaryAdvanceInitialContent = content
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
    }

    public func submitSalaryAdvanceRequest() async {
        guard !salaryAdvanceCreateSubmitting else { return }
        let projectId = (salaryAdvanceSelectedProjectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !projectId.isEmpty else {
            salaryAdvanceCreateError = "Vui lòng chọn dự án."
            return
        }
        let selectedProject = salaryAdvanceContext?.activeManagedProjects.first(where: { $0.projectId == projectId })
        if selectedProject?.hasActiveAccountant == false {
            salaryAdvanceCreateError = SalaryAdvanceContract.describeSalaryAdvanceError(
                code: "SALARY_ADVANCE_PROJECT_HAS_NO_ACTIVE_ACCOUNTANT",
                httpStatus: 422,
                fallback: nil
            )
            return
        }
        guard let worker = selectedSalaryAdvanceWorker else {
            salaryAdvanceCreateError = "Vui lòng chọn người lao động."
            return
        }
        let eligibilityDesc = SalaryAdvanceContract.describeWorkerEligibility(worker)
        guard eligibilityDesc.isEligible else {
            salaryAdvanceCreateError = eligibilityDesc.blockingMessage
                ?? "Người lao động chưa đủ điều kiện ứng lương."
            return
        }
        let bankDesc = SalaryAdvanceContract.describeWorkerBankTransferEligibility(worker)
        if salaryAdvancePaymentMethod == SalaryAdvanceContract.paymentMethodBankTransfer &&
            !bankDesc.canUseBankTransfer {
            salaryAdvanceCreateError = bankDesc.blockingMessage
                ?? "Người lao động chưa đủ điều kiện nhận chuyển khoản."
            return
        }
        guard let normalizedAmount = SalaryAdvanceContract.normalizePositiveVndInput(salaryAdvanceAmountRaw) else {
            salaryAdvanceCreateError = "Số tiền đề nghị phải là số nguyên dương (VNĐ)."
            return
        }
        let trimmedContent = salaryAdvanceInitialContent.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedContent.isEmpty else {
            salaryAdvanceCreateError = "Vui lòng nhập nội dung đề nghị ứng lương."
            return
        }

        let signature = "\(projectId)|\(worker.workerEntryId)|\(normalizedAmount)|\(salaryAdvancePaymentMethod)|\(trimmedContent)"
        let idempotencyKey: String
        if salaryAdvancePendingSignature == signature,
           let existingKey = salaryAdvancePendingIdempotencyKey, !existingKey.isEmpty {
            idempotencyKey = existingKey
        } else {
            idempotencyKey = UUID().uuidString.lowercased()
        }

        salaryAdvanceCreateSubmitting = true
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = idempotencyKey
        salaryAdvancePendingSignature = signature

        let res = await salaryAdvanceService.createRequest(
            projectId: projectId,
            workerEntryId: worker.workerEntryId,
            requestedAmountRaw: normalizedAmount,
            paymentMethod: salaryAdvancePaymentMethod,
            initialContent: trimmedContent,
            idempotencyKey: idempotencyKey
        )

        salaryAdvanceCreateSubmitting = false

        switch res {
        case .success(let reqId, _):
            let workerName = worker.displayName
            let formattedAmount = SalaryAdvanceContract.formatVndAmount(normalizedAmount)
            salaryAdvanceSelectedWorkerId = nil
            salaryAdvanceAmountRaw = ""
            salaryAdvanceInitialContent = ""
            salaryAdvanceWorkerPickerExpanded = true
            salaryAdvancePendingIdempotencyKey = nil
            salaryAdvancePendingSignature = nil
            showBanner("Đã tạo yêu cầu ứng lương cho \(workerName) (\(formattedAmount)).")
            await refreshSalaryAdvanceRequests()
            if !reqId.isEmpty && reqId != "created" {
                await openSalaryAdvanceRequestDetail(reqId)
            } else {
                salaryAdvanceWorkspaceTab = "LIST"
            }
        case .failure(let message, let httpStatus, let errorCode, _):
            if SalaryAdvanceContract.isConflictError(statusCode: httpStatus, errorCode: errorCode) {
                salaryAdvancePendingIdempotencyKey = nil
                salaryAdvancePendingSignature = nil
            }
            salaryAdvanceCreateError = message
        }
    }

    // MARK: - Salary Advance PM Queue Request List & Filtering

    public func refreshSalaryAdvanceRequests() async {
        await loadSalaryAdvanceRequests(append: false, cursor: nil)
    }

    public func updateSalaryAdvanceListProjectFilter(_ projectFilter: String) {
        let clean = projectFilter.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolved = clean.isEmpty ? "ALL" : clean
        guard salaryAdvanceListProjectFilter != resolved else { return }
        salaryAdvanceListProjectFilter = resolved
        Task { await loadSalaryAdvanceRequests(append: false, cursor: nil) }
    }

    public func updateSalaryAdvanceListStateFilter(_ stateFilter: String) {
        let clean = stateFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let resolved = clean.isEmpty ? "ALL" : clean
        guard salaryAdvanceListStateFilter != resolved else { return }
        salaryAdvanceListStateFilter = resolved
        Task { await loadSalaryAdvanceRequests(append: false, cursor: nil) }
    }

    public func updateSalaryAdvanceListEvidenceFilter(_ evidenceFilter: String) {
        let clean = evidenceFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let resolved = clean.isEmpty ? "ALL" : clean
        guard salaryAdvanceListEvidenceFilter != resolved else { return }
        salaryAdvanceListEvidenceFilter = resolved
        Task { await loadSalaryAdvanceRequests(append: false, cursor: nil) }
    }

    public func updateSalaryAdvanceListSearch(_ search: String) {
        let clean = String(search.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ").prefix(120))
        salaryAdvanceListSearch = clean
        salaryAdvanceListSearchTask?.cancel()
        salaryAdvanceListSearchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            await self?.loadSalaryAdvanceRequests(append: false, cursor: nil)
        }
    }

    public func loadMoreSalaryAdvanceRequests() async {
        guard !salaryAdvanceRequestsLoading,
              !salaryAdvanceRequestsLoadingMore,
              salaryAdvanceRequestsHasMore,
              let cursor = salaryAdvanceRequestsNextCursor, !cursor.isEmpty else {
            return
        }
        await loadSalaryAdvanceRequests(append: true, cursor: cursor)
    }

    private func loadSalaryAdvanceRequests(append: Bool, cursor: String?) async {
        let isAppend = append && !(cursor ?? "").isEmpty
        if isAppend {
            salaryAdvanceRequestsLoadingMore = true
        } else {
            salaryAdvanceRequestsLoading = true
            salaryAdvanceRequestsError = nil
        }

        let res = await salaryAdvanceService.listRequests(
            queue: "PM",
            projectFilter: salaryAdvanceListProjectFilter,
            requestStateFilter: salaryAdvanceListStateFilter,
            evidenceStateFilter: salaryAdvanceListEvidenceFilter,
            search: salaryAdvanceListSearch,
            cursor: isAppend ? cursor : nil,
            pageSize: 25,
            includeBadge: true
        )

        if isAppend {
            salaryAdvanceRequestsLoadingMore = false
        } else {
            salaryAdvanceRequestsLoading = false
        }

        switch res {
        case .success(let payload, _):
            if isAppend {
                let existingIds = Set(salaryAdvanceRequests.map { $0.requestId })
                salaryAdvanceRequests = salaryAdvanceRequests + payload.requests.filter { !existingIds.contains($0.requestId) }
            } else {
                salaryAdvanceRequests = payload.requests
            }
            salaryAdvanceRequestsHasMore = payload.hasMore
            salaryAdvanceRequestsNextCursor = payload.nextCursor
            if let badge = payload.badgeCount {
                salaryAdvancePmBadgeCount = badge
            }
            salaryAdvanceRequestsError = nil
        case .failure(let message, _, _, _):
            guard !res.isCancelledRequest else { return }
            salaryAdvanceRequestsError = message
        }
    }

    // MARK: - Salary Advance Request Detail, Messages, Transition Actions & Evidence Upload

    public func openSalaryAdvanceRequestDetail(_ requestId: String) async {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else { return }
        salaryAdvanceSelectedRequestId = cleanId
        salaryAdvanceWorkspaceTab = "DETAIL"
        salaryAdvanceDetailError = nil
        salaryAdvanceConflictBanner = nil
        salaryAdvanceEvidenceError = nil
        salaryAdvanceMessageDraft = ""
        salaryAdvanceActionNoteDraft = ""
        salaryAdvanceEvidenceCaptionDraft = ""
        salaryAdvanceSelectedEvidenceVersion = nil
        salaryAdvanceMessageIdempotencyKey = UUID().uuidString.lowercased()
        salaryAdvanceActionIdempotencyKey = UUID().uuidString.lowercased()
        salaryAdvanceEvidenceReserveKey = UUID().uuidString.lowercased()
        salaryAdvanceEvidenceFinalizeKey = UUID().uuidString.lowercased()
        await loadSalaryAdvanceRequestDetail(cleanId)
    }

    public func backFromSalaryAdvanceDetailToList() {
        salaryAdvanceWorkspaceTab = "LIST"
        salaryAdvanceConflictBanner = nil
        salaryAdvanceDetailError = nil
        salaryAdvanceEvidenceError = nil
    }

    public func refreshSalaryAdvanceActiveDetail() async {
        guard let reqId = salaryAdvanceSelectedRequestId, !reqId.isEmpty else { return }
        await loadSalaryAdvanceRequestDetail(reqId)
    }

    private func loadSalaryAdvanceRequestDetail(_ requestId: String) async {
        salaryAdvanceDetailLoading = true
        salaryAdvanceDetailError = nil
        let res = await salaryAdvanceService.getRequestDetail(requestId: requestId)
        salaryAdvanceDetailLoading = false
        switch res {
        case .success(let detail, _):
            applyUpdatedSalaryAdvanceDetail(detail)
        case .failure(let message, _, _, _):
            guard !res.isCancelledRequest else { return }
            salaryAdvanceDetailError = message
        }
    }

    private func applyUpdatedSalaryAdvanceDetail(_ detail: SalaryAdvanceRequestDetail) {
        salaryAdvanceActiveDetail = detail
        let currentEvVer = detail.request.currentEvidence?.evidenceVersion ?? detail.evidenceVersions.first?.evidenceVersion
        salaryAdvanceSelectedEvidenceVersion = currentEvVer
        if let idx = salaryAdvanceRequests.firstIndex(where: { $0.requestId == detail.request.requestId }) {
            salaryAdvanceRequests[idx] = detail.request
        } else {
            salaryAdvanceRequests.insert(detail.request, at: 0)
        }
    }

    public func selectSalaryAdvanceEvidenceVersion(_ version: Int?) {
        salaryAdvanceSelectedEvidenceVersion = version
    }

    public func updateSalaryAdvanceMessageDraft(_ text: String) {
        guard !salaryAdvanceMessageSubmitting else { return }
        salaryAdvanceMessageDraft = String(text.prefix(2000))
        salaryAdvanceDetailError = nil
    }

    public func postSalaryAdvanceMessage() async {
        guard !salaryAdvanceMessageSubmitting,
              let detail = salaryAdvanceActiveDetail,
              detail.request.allowedActions.canPostMessage else { return }
        let cleanText = salaryAdvanceMessageDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanText.isEmpty else {
            salaryAdvanceDetailError = "Vui lòng nhập nội dung trao đổi."
            return
        }
        let reqId = detail.request.requestId
        let expectedVersion = detail.request.version
        let key = salaryAdvanceMessageIdempotencyKey

        salaryAdvanceDetailError = nil
        salaryAdvanceConflictBanner = nil
        salaryAdvanceMessageSubmitting = true

        let res = await salaryAdvanceService.postMessage(
            requestId: reqId,
            expectedVersion: expectedVersion,
            messageText: cleanText,
            idempotencyKey: key
        )
        salaryAdvanceMessageSubmitting = false

        switch res {
        case .success(let updated, _):
            salaryAdvanceMessageIdempotencyKey = UUID().uuidString.lowercased()
            salaryAdvanceMessageDraft = ""
            applyUpdatedSalaryAdvanceDetail(updated)
            showBanner("Đã gửi tin nhắn trao đổi.")
        case .failure(let message, let httpStatus, let errorCode, _):
            if SalaryAdvanceContract.isConflictError(statusCode: httpStatus, errorCode: errorCode) {
                salaryAdvanceMessageIdempotencyKey = UUID().uuidString.lowercased()
                salaryAdvanceConflictBanner = message
                await loadSalaryAdvanceRequestDetail(reqId)
                await loadSalaryAdvanceRequests(append: false, cursor: nil)
            } else {
                salaryAdvanceDetailError = message
            }
        }
    }

    public func updateSalaryAdvanceActionNoteDraft(_ text: String) {
        guard !salaryAdvanceActionSubmitting else { return }
        salaryAdvanceActionNoteDraft = String(text.prefix(2000))
        salaryAdvanceDetailError = nil
    }

    public func submitSalaryAdvanceTransitionAction(_ action: String, noteOverride: String? = nil) async {
        guard !salaryAdvanceActionSubmitting,
              let detail = salaryAdvanceActiveDetail else { return }
        let cleanAction = action.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let note = (noteOverride ?? salaryAdvanceActionNoteDraft).trimmingCharacters(in: .whitespacesAndNewlines)

        switch cleanAction {
        case "PROVIDE_INFO":
            guard detail.request.allowedActions.canProvideInfo else { return }
            if note.isEmpty {
                salaryAdvanceDetailError = "Vui lòng nhập nội dung phản hồi bổ sung thông tin cho Kế toán."
                return
            }
        case "WITHDRAW":
            guard detail.request.allowedActions.canWithdraw else { return }
        default:
            return
        }

        let reqId = detail.request.requestId
        let expectedVersion = detail.request.version
        let key = salaryAdvanceActionIdempotencyKey

        salaryAdvanceDetailError = nil
        salaryAdvanceConflictBanner = nil
        salaryAdvanceActionSubmitting = true

        let res = await salaryAdvanceService.submitTransitionAction(
            requestId: reqId,
            action: cleanAction,
            expectedVersion: expectedVersion,
            messageText: note.isEmpty ? nil : note,
            idempotencyKey: key
        )
        salaryAdvanceActionSubmitting = false

        switch res {
        case .success(let updated, _):
            salaryAdvanceActionIdempotencyKey = UUID().uuidString.lowercased()
            salaryAdvanceActionNoteDraft = ""
            applyUpdatedSalaryAdvanceDetail(updated)
            await loadSalaryAdvanceRequests(append: false, cursor: nil)
            showBanner(cleanAction == "WITHDRAW" ? "Đã rút yêu cầu ứng lương." : "Đã gửi bổ sung thông tin cho Kế toán.")
        case .failure(let message, let httpStatus, let errorCode, _):
            if SalaryAdvanceContract.isConflictError(statusCode: httpStatus, errorCode: errorCode) {
                salaryAdvanceActionIdempotencyKey = UUID().uuidString.lowercased()
                salaryAdvanceConflictBanner = message
                await loadSalaryAdvanceRequestDetail(reqId)
                await loadSalaryAdvanceRequests(append: false, cursor: nil)
            } else {
                salaryAdvanceDetailError = message
            }
        }
    }

    public func updateSalaryAdvanceEvidenceCaptionDraft(_ caption: String) {
        guard !salaryAdvanceEvidenceUploading else { return }
        salaryAdvanceEvidenceCaptionDraft = String(caption.prefix(1000))
        salaryAdvanceEvidenceError = nil
    }

    public func uploadSalaryAdvanceEvidenceData(_ rawData: Data, fileName: String = "evidence.jpg") async {
        guard !salaryAdvanceEvidenceUploading,
              let detail = salaryAdvanceActiveDetail,
              detail.request.allowedActions.canUploadEvidence else { return }

        salaryAdvanceEvidenceError = nil
        salaryAdvanceConflictBanner = nil
        salaryAdvanceEvidenceUploading = true

        let attachment = LocalAttachmentItem(
            fileName: fileName,
            mimeType: "image/jpeg",
            data: rawData
        )
        let preparedPayload: PreparedContractUploadPayload
        do {
            preparedPayload = try PdfContractMerger.prepareContractUploadPayload(attachments: [attachment])
        } catch {
            salaryAdvanceEvidenceUploading = false
            salaryAdvanceEvidenceError = error.localizedDescription
            return
        }

        let reqId = detail.request.requestId
        let expectedVersion = detail.request.version
        let reserveKey = salaryAdvanceEvidenceReserveKey
        let finalizeKey = salaryAdvanceEvidenceFinalizeKey

        let res = await salaryAdvanceService.uploadEvidenceBytes(
            requestId: reqId,
            expectedVersion: expectedVersion,
            imageData: preparedPayload.data,
            mimeType: preparedPayload.mimeType,
            captionNote: salaryAdvanceEvidenceCaptionDraft,
            reserveIdempotencyKey: reserveKey,
            finalizeIdempotencyKey: finalizeKey
        )
        salaryAdvanceEvidenceUploading = false
        salaryAdvanceEvidenceReserveKey = UUID().uuidString.lowercased()
        salaryAdvanceEvidenceFinalizeKey = UUID().uuidString.lowercased()

        switch res {
        case .success(let updated, _):
            salaryAdvanceEvidenceCaptionDraft = ""
            applyUpdatedSalaryAdvanceDetail(updated)
            await loadSalaryAdvanceRequests(append: false, cursor: nil)
            showBanner("Đã tải lên ảnh chứng từ nhận tiền thành công.")
        case .failure(let message, let httpStatus, let errorCode, _):
            if SalaryAdvanceContract.isConflictError(statusCode: httpStatus, errorCode: errorCode) {
                salaryAdvanceConflictBanner = message
                await loadSalaryAdvanceRequestDetail(reqId)
                await loadSalaryAdvanceRequests(append: false, cursor: nil)
            } else {
                salaryAdvanceEvidenceError = message
            }
        }
    }

    // MARK: - Salary Advance Worker History (PM Modal)

    private static func currentVnYearMonth() -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
        df.dateFormat = "yyyy-MM"
        return df.string(from: Date())
    }

    public func openSalaryAdvanceWorkerHistory() async {
        guard let worker = selectedSalaryAdvanceWorker,
              let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else { return }
        let defaultMonth = salaryAdvanceHistoryMonth.isEmpty ? Self.currentVnYearMonth() : salaryAdvanceHistoryMonth
        salaryAdvanceHistoryTargetRequestId = nil
        salaryAdvanceHistoryOpen = true
        salaryAdvanceHistoryPeriodMode = SalaryAdvanceContract.periodModeLast30Days
        salaryAdvanceHistoryMonth = defaultMonth
        salaryAdvanceHistoryLoading = true
        salaryAdvanceHistoryLoadingMore = false
        salaryAdvanceHistoryError = nil
        salaryAdvanceHistoryResponse = nil
        salaryAdvanceHistoryItems = []
        salaryAdvanceHistoryNextCursor = nil
        salaryAdvanceHistoryHasMore = false

        await fetchSalaryAdvanceWorkerHistoryInternal(
            requestId: nil,
            projectId: projectId,
            workerEntryId: worker.workerEntryId,
            periodMode: SalaryAdvanceContract.periodModeLast30Days,
            month: defaultMonth,
            fromDate: salaryAdvanceHistoryFromDate,
            toDate: salaryAdvanceHistoryToDate,
            cursor: nil,
            append: false
        )
    }

    public func openSalaryAdvanceRequestWorkerHistory(_ requestId: String) async {
        let cleanId = requestId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else { return }
        let defaultMonth = salaryAdvanceHistoryMonth.isEmpty ? Self.currentVnYearMonth() : salaryAdvanceHistoryMonth
        salaryAdvanceHistoryTargetRequestId = cleanId
        salaryAdvanceHistoryOpen = true
        salaryAdvanceHistoryPeriodMode = SalaryAdvanceContract.periodModeLast30Days
        salaryAdvanceHistoryMonth = defaultMonth
        salaryAdvanceHistoryLoading = true
        salaryAdvanceHistoryLoadingMore = false
        salaryAdvanceHistoryError = nil
        salaryAdvanceHistoryResponse = nil
        salaryAdvanceHistoryItems = []
        salaryAdvanceHistoryNextCursor = nil
        salaryAdvanceHistoryHasMore = false

        await fetchSalaryAdvanceWorkerHistoryInternal(
            requestId: cleanId,
            projectId: "",
            workerEntryId: "",
            periodMode: SalaryAdvanceContract.periodModeLast30Days,
            month: defaultMonth,
            fromDate: salaryAdvanceHistoryFromDate,
            toDate: salaryAdvanceHistoryToDate,
            cursor: nil,
            append: false
        )
    }

    public func closeSalaryAdvanceWorkerHistory() {
        salaryAdvanceHistoryOpen = false
        salaryAdvanceHistoryTargetRequestId = nil
        salaryAdvanceHistoryLoading = false
        salaryAdvanceHistoryLoadingMore = false
        salaryAdvanceHistoryError = nil
    }

    public func selectSalaryAdvanceHistoryPeriodMode(_ periodMode: String, month: String? = nil) async {
        let targetReqId = salaryAdvanceHistoryTargetRequestId
        let projectId = salaryAdvanceSelectedProjectId ?? ""
        let workerId = selectedSalaryAdvanceWorker?.workerEntryId ?? ""
        guard !(targetReqId ?? "").isEmpty || (!projectId.isEmpty && !workerId.isEmpty) else { return }

        let normalizedMode = periodMode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let nextMonth = (month ?? salaryAdvanceHistoryMonth).trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedMonth = nextMonth.isEmpty ? Self.currentVnYearMonth() : nextMonth

        salaryAdvanceHistoryPeriodMode = normalizedMode
        salaryAdvanceHistoryMonth = resolvedMonth
        salaryAdvanceHistoryError = nil

        if normalizedMode == SalaryAdvanceContract.periodModeCustomRange &&
            (salaryAdvanceHistoryFromDate.isEmpty || salaryAdvanceHistoryToDate.isEmpty) {
            return
        }

        await fetchSalaryAdvanceWorkerHistoryInternal(
            requestId: targetReqId,
            projectId: projectId,
            workerEntryId: workerId,
            periodMode: normalizedMode,
            month: resolvedMonth,
            fromDate: salaryAdvanceHistoryFromDate,
            toDate: salaryAdvanceHistoryToDate,
            cursor: nil,
            append: false
        )
    }

    public func applySalaryAdvanceHistoryCustomRange(fromDate: String, toDate: String) async {
        let targetReqId = salaryAdvanceHistoryTargetRequestId
        let projectId = salaryAdvanceSelectedProjectId ?? ""
        let workerId = selectedSalaryAdvanceWorker?.workerEntryId ?? ""
        guard !(targetReqId ?? "").isEmpty || (!projectId.isEmpty && !workerId.isEmpty) else { return }

        let f = fromDate.trimmingCharacters(in: .whitespacesAndNewlines)
        let t = toDate.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !f.isEmpty && !t.isEmpty else {
            salaryAdvanceHistoryError = "Vui lòng nhập đầy đủ từ ngày và đến ngày (DD/MM/YYYY hoặc YYYY-MM-DD)."
            return
        }
        salaryAdvanceHistoryPeriodMode = SalaryAdvanceContract.periodModeCustomRange
        salaryAdvanceHistoryFromDate = f
        salaryAdvanceHistoryToDate = t
        salaryAdvanceHistoryError = nil

        await fetchSalaryAdvanceWorkerHistoryInternal(
            requestId: targetReqId,
            projectId: projectId,
            workerEntryId: workerId,
            periodMode: SalaryAdvanceContract.periodModeCustomRange,
            month: salaryAdvanceHistoryMonth,
            fromDate: f,
            toDate: t,
            cursor: nil,
            append: false
        )
    }

    public func loadMoreSalaryAdvanceWorkerHistory() async {
        let targetReqId = salaryAdvanceHistoryTargetRequestId
        let projectId = salaryAdvanceSelectedProjectId ?? ""
        let workerId = selectedSalaryAdvanceWorker?.workerEntryId ?? ""
        guard !salaryAdvanceHistoryLoading,
              !salaryAdvanceHistoryLoadingMore,
              salaryAdvanceHistoryHasMore,
              let cursor = salaryAdvanceHistoryNextCursor, !cursor.isEmpty,
              (!(targetReqId ?? "").isEmpty || (!projectId.isEmpty && !workerId.isEmpty)) else {
            return
        }
        await fetchSalaryAdvanceWorkerHistoryInternal(
            requestId: targetReqId,
            projectId: projectId,
            workerEntryId: workerId,
            periodMode: salaryAdvanceHistoryPeriodMode,
            month: salaryAdvanceHistoryMonth,
            fromDate: salaryAdvanceHistoryFromDate,
            toDate: salaryAdvanceHistoryToDate,
            cursor: cursor,
            append: true
        )
    }

    private func fetchSalaryAdvanceWorkerHistoryInternal(
        requestId: String?,
        projectId: String,
        workerEntryId: String,
        periodMode: String,
        month: String,
        fromDate: String,
        toDate: String,
        cursor: String?,
        append: Bool
    ) async {
        if append {
            salaryAdvanceHistoryLoadingMore = true
        } else {
            salaryAdvanceHistoryLoading = true
            salaryAdvanceHistoryError = nil
        }

        let res: MiniBiApiResult<SalaryAdvanceWorkerHistoryResponse>
        if let reqId = requestId, !reqId.isEmpty {
            res = await salaryAdvanceService.fetchWorkerHistoryForRequest(
                requestId: reqId,
                periodMode: periodMode,
                month: periodMode == SalaryAdvanceContract.periodModeMonth ? month : nil,
                fromDate: periodMode == SalaryAdvanceContract.periodModeCustomRange ? fromDate : nil,
                toDate: periodMode == SalaryAdvanceContract.periodModeCustomRange ? toDate : nil,
                cursor: cursor,
                pageSize: 20
            )
        } else {
            res = await salaryAdvanceService.fetchWorkerHistoryForPm(
                projectId: projectId,
                workerEntryId: workerEntryId,
                periodMode: periodMode,
                month: periodMode == SalaryAdvanceContract.periodModeMonth ? month : nil,
                fromDate: periodMode == SalaryAdvanceContract.periodModeCustomRange ? fromDate : nil,
                toDate: periodMode == SalaryAdvanceContract.periodModeCustomRange ? toDate : nil,
                cursor: cursor,
                pageSize: 20
            )
        }

        if append {
            salaryAdvanceHistoryLoadingMore = false
        } else {
            salaryAdvanceHistoryLoading = false
        }

        switch res {
        case .success(let payload, _):
            let combined = append ? (salaryAdvanceHistoryItems + payload.items) : payload.items
            salaryAdvanceHistoryResponse = payload
            salaryAdvanceHistoryItems = combined
            salaryAdvanceHistoryNextCursor = payload.nextCursor
            salaryAdvanceHistoryHasMore = payload.hasMore
            salaryAdvanceHistoryError = nil
        case .failure(let message, _, _, _):
            guard !res.isCancelledRequest else { return }
            salaryAdvanceHistoryError = message
        }
    }

    // MARK: - Attendance Actions

    public var attendancePendingCount: Int {
        attendanceOutboxItems.filter {
            AttendanceContract.isRetryableStatus($0.status) || $0.status == AttendanceContract.statusUploading
        }.count
    }

    public func refreshAttendanceTimeEvaluation() {
        attendanceTimeEvaluation = timeAnchorManager.evaluateAnchor()
    }

    public func canAttemptTimeAnchorAutoSync() -> Bool {
        !isSyncingTimeAnchor && timeAnchorManager.clock.elapsedRealtimeMs() >= nextAllowedTimeAnchorSyncElapsedMs
    }

    public func canAttemptOutboxAutoFlush() -> Bool {
        !isSyncingAttendance && !isSubmittingAttendance && timeAnchorManager.clock.elapsedRealtimeMs() >= nextAllowedOutboxFlushElapsedMs
    }

    private func recordTimeAnchorSyncSuccess() {
        timeAnchorBackoffAttempt = 0
        nextAllowedTimeAnchorSyncElapsedMs = 0
    }

    private func recordTimeAnchorSyncFailure(httpStatus: Int?) {
        guard AttendanceRetryBackoffPolicy.isRetryableFailure(httpStatus: httpStatus) else { return }
        timeAnchorBackoffAttempt += 1
        let delayMs = AttendanceRetryBackoffPolicy.computeDelayMs(
            attempt: timeAnchorBackoffAttempt,
            jitterSample: backoffJitterProvider()
        )
        nextAllowedTimeAnchorSyncElapsedMs = timeAnchorManager.clock.elapsedRealtimeMs() + delayMs
    }

    private func recordOutboxFlushSuccess() {
        outboxBackoffAttempt = 0
        nextAllowedOutboxFlushElapsedMs = 0
    }

    private func recordOutboxFlushFailure(httpStatus: Int?) {
        guard AttendanceRetryBackoffPolicy.isRetryableFailure(httpStatus: httpStatus) else { return }
        outboxBackoffAttempt += 1
        let delayMs = AttendanceRetryBackoffPolicy.computeDelayMs(
            attempt: outboxBackoffAttempt,
            jitterSample: backoffJitterProvider()
        )
        nextAllowedOutboxFlushElapsedMs = timeAnchorManager.clock.elapsedRealtimeMs() + delayMs
    }

    /// Operation 1 (isolated): Synchronizes the server time anchor (`GET /api/attendance/time`) ONLY.
    /// Never chains check-in (`POST /api/attendance/check-in`) or history (`GET /api/attendance/history`).
    @discardableResult
    public func syncAttendanceTimeAnchorOnly(force: Bool = false, isAutomatic: Bool = false) async -> Bool {
        guard let token = captureSessionToken() else { return false }
        if isAutomatic && !canAttemptTimeAnchorAutoSync() {
            return false
        }
        if !force {
            let eval = timeAnchorManager.evaluateAnchor()
            if eval.isTrusted, let remaining = eval.remainingValidMs, remaining > AttendanceTimeContract.refreshBeforeExpiryMs {
                attendanceTimeEvaluation = eval
                return true
            }
        }
        guard !isSyncingTimeAnchor else { return false }
        isSyncingTimeAnchor = true
        defer {
            isSyncingTimeAnchor = false
        }

        let res = await timeAnchorManager.syncTimeAnchor()
        guard !Task.isCancelled, isSessionValid(token) else { return false }
        attendanceTimeEvaluation = timeAnchorManager.evaluateAnchor()

        switch res {
        case .success:
            recordTimeAnchorSyncSuccess()
            return true
        case .failure(_, let httpStatus, _, _):
            recordTimeAnchorSyncFailure(httpStatus: httpStatus)
            return false
        }
    }

    public func syncAttendanceTimeAnchor() async {
        _ = await syncAttendanceTimeAnchorOnly(force: true, isAutomatic: false)
    }

    /// Operation 2 (isolated): Flushes retryable offline outbox records (`POST /api/attendance/check-in`).
    /// Only refreshes server history (`GET /api/attendance/history`) when at least one record is newly synced (`syncedCount > 0`).
    /// Never refreshes history when check-in fails.
    @discardableResult
    public func flushAttendanceOutboxOnly(isManual: Bool = false) async -> AttendanceOutboxFlushSummary {
        let emptySummary = AttendanceOutboxFlushSummary(
            processedCount: 0,
            syncedCount: 0,
            retryableFailureCount: 0,
            remainingRetryableCount: attendancePendingCount
        )
        guard let token = captureSessionToken(),
              let userKey = currentUser?.username.trimmingCharacters(in: .whitespacesAndNewlines),
              !userKey.isEmpty else {
            return emptySummary
        }
        guard !isSyncingAttendance && !isSubmittingAttendance else {
            return emptySummary
        }
        if !isManual && !canAttemptOutboxAutoFlush() {
            return emptySummary
        }
        guard attendanceOutboxService.pendingCountForUser(userKey: userKey) > 0 else {
            return emptySummary
        }

        isSyncingAttendance = true
        defer {
            isSyncingAttendance = false
        }

        let summary = await attendanceOutboxService.flushRetryableOutboxWithSummary(userKey: userKey)
        guard !Task.isCancelled, isSessionValid(token) else {
            return summary
        }

        if summary.retryableFailureCount > 0 {
            recordOutboxFlushFailure(httpStatus: summary.lastFailureHttpStatus)
        } else if summary.syncedCount > 0 && summary.remainingRetryableCount == 0 {
            recordOutboxFlushSuccess()
        }

        attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: userKey)

        if summary.syncedCount > 0 {
            _ = await refreshAttendanceHistoryOnly(force: true)
        }

        return summary
    }

    /// Operation 3 (isolated): Refreshes server attendance history (`GET /api/attendance/history`).
    @discardableResult
    public func refreshAttendanceHistoryOnly(force: Bool = false) async -> Bool {
        guard let token = captureSessionToken(),
              let userKey = currentUser?.username.trimmingCharacters(in: .whitespacesAndNewlines),
              !userKey.isEmpty else {
            return false
        }
        if !force && hasLoadedAttendanceHistoryInSession {
            return true
        }
        let targetDate = selectedAttendanceHistoryDate.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedDate = LeaveContract.isValidIsoDate(targetDate) ? targetDate : LeaveContract.todayVietnamIsoDate()
        let histRes = await attendanceOutboxService.syncServerHistoryForUser(
            userKey: userKey,
            fromDate: resolvedDate,
            toDate: resolvedDate,
            page: 1,
            pageSize: Self.leaveInboxPageSize
        )
        guard !Task.isCancelled, isSessionValid(token) else { return false }
        switch histRes {
        case .success(let data, _):
            hasLoadedAttendanceHistoryInSession = true
            attendanceOutboxItems = data.items
            todayServerAttendanceCount = data.todayCount
            attendanceHistoryPage = 1
            attendanceHistoryHasMore = attendanceOutboxService.lastHistorySyncHasMore
            attendanceHistoryTotalCount = attendanceOutboxService.lastHistorySyncTotalCount
            return true
        case .failure:
            attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: userKey)
            return false
        }
    }

    public func selectAttendanceHistoryDate(_ dateIso: String) {
        let clean = dateIso.trimmingCharacters(in: .whitespacesAndNewlines)
        guard LeaveContract.isValidIsoDate(clean) else { return }
        let todayIso = LeaveContract.todayVietnamIsoDate()
        let clamped = clean > todayIso ? todayIso : clean
        selectedAttendanceHistoryDate = clamped
        attendanceHistoryPage = 1
        Task {
            _ = await refreshAttendanceHistoryOnly(force: true)
        }
    }

    public func loadMoreAttendanceHistory() async {
        guard let token = captureSessionToken(),
              let userKey = currentUser?.username.trimmingCharacters(in: .whitespacesAndNewlines),
              !userKey.isEmpty,
              attendanceHistoryHasMore,
              !isLoadingMoreAttendanceHistory,
              !isSyncingAttendance else {
            return
        }
        let targetDate = selectedAttendanceHistoryDate.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedDate = LeaveContract.isValidIsoDate(targetDate) ? targetDate : LeaveContract.todayVietnamIsoDate()
        let nextPage = attendanceHistoryPage + 1
        isLoadingMoreAttendanceHistory = true
        defer {
            isLoadingMoreAttendanceHistory = false
        }
        let histRes = await attendanceOutboxService.syncServerHistoryForUser(
            userKey: userKey,
            fromDate: resolvedDate,
            toDate: resolvedDate,
            page: nextPage,
            pageSize: Self.leaveInboxPageSize
        )
        guard !Task.isCancelled, isSessionValid(token), selectedAttendanceHistoryDate == resolvedDate else { return }
        if case .success(let data, _) = histRes {
            attendanceOutboxItems = data.items
            todayServerAttendanceCount = data.todayCount
            attendanceHistoryPage = nextPage
            attendanceHistoryHasMore = attendanceOutboxService.lastHistorySyncHasMore
            attendanceHistoryTotalCount = max(attendanceHistoryTotalCount, attendanceOutboxService.lastHistorySyncTotalCount)
        }
    }

    /// Deduplicated initial load flow for the `Chấm công` screen (at most once per authenticated session generation).
    /// Respects strict failure gates:
    /// - If `GET /api/attendance/time` fails -> does NOT call `check-in` or `history`.
    /// - If `POST /api/attendance/check-in` fails -> does NOT call `history`.
    public func loadInitialAttendanceScreenDataIfNeeded() async {
        guard captureSessionToken() != nil else { return }
        await runDeduplicatedSessionTask(key: "attendance_initial_load") { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            let isFirstLoadInSession = (self.initialAttendanceLoadedGeneration != token.generation)
            if isFirstLoadInSession {
                self.initialAttendanceLoadedGeneration = token.generation

                let timeOk = await self.syncAttendanceTimeAnchorOnly(force: true, isAutomatic: true)
                guard !Task.isCancelled, self.isSessionValid(token) else { return }

                if timeOk, let userKey = self.currentUser?.username.trimmingCharacters(in: .whitespacesAndNewlines), !userKey.isEmpty {
                    if self.attendanceOutboxService.pendingCountForUser(userKey: userKey) > 0 {
                        let flushSummary = await self.flushAttendanceOutboxOnly(isManual: false)
                        guard !Task.isCancelled, self.isSessionValid(token) else { return }
                        if flushSummary.retryableFailureCount == 0 && !self.hasLoadedAttendanceHistoryInSession {
                            _ = await self.refreshAttendanceHistoryOnly(force: false)
                        }
                    } else {
                        _ = await self.refreshAttendanceHistoryOnly(force: false)
                    }
                }
            } else {
                _ = await self.syncAttendanceTimeAnchorOnly(force: false, isAutomatic: true)
            }

            guard !Task.isCancelled, self.isSessionValid(token) else { return }
            if self.myLeaveSummary == nil && !self.isLoadingMyLeave {
                await self.refreshMyLeaveSummary(year: self.leaveYear)
            }
            guard !Task.isCancelled, self.isSessionValid(token) else { return }
            if self.myExplanationsOverview == nil && !self.isLoadingMyExplanations {
                await self.refreshMyAttendanceExplanations()
            }
        }
    }

    public func submitAttendanceCheckIn(
        lat: Double,
        lng: Double,
        accuracyM: Double?,
        photoData: Data,
        note: String,
        frozenTimestamp: CapturedAttendanceTimestamp? = nil
    ) async {
        let userKey = currentUser?.username ?? "guest"
        isSubmittingAttendance = true
        defer {
            isSubmittingAttendance = false
        }
        let (item, apiResult) = await attendanceOutboxService.submitCheckIn(
            userKey: userKey,
            lat: lat,
            lng: lng,
            accuracyM: accuracyM,
            locationCapturedAt: frozenTimestamp?.capturedAtIso,
            photoData: photoData,
            mimeType: "image/jpeg",
            note: note,
            captureTimestampOverride: frozenTimestamp
        )
        attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: userKey)

        switch apiResult {
        case .success:
            recordOutboxFlushSuccess()
            todayServerAttendanceCount = max(
                todayServerAttendanceCount,
                attendanceOutboxService.todaySyncedCountForUser(userKey: userKey)
            )
            _ = await refreshAttendanceHistoryOnly(force: true)
            showBanner("Chấm công thành công! Đã đồng bộ lên máy chủ.")
        case .failure(let message, let httpStatus, _, _):
            if item.status == AttendanceContract.statusFailedPermanent {
                showBanner(message, isError: true)
            } else {
                recordOutboxFlushFailure(httpStatus: httpStatus)
                showBanner("Đã lưu chấm công vào hàng đợi ngoại tuyến (\(message)). Sẽ tự động gửi bù khi có mạng.", isError: false)
            }
        }
    }

    public func flushAttendanceOutboxNow() async {
        guard let userKey = currentUser?.username.trimmingCharacters(in: .whitespacesAndNewlines),
              !userKey.isEmpty else { return }
        guard !isSyncingAttendance && !isSubmittingAttendance else { return }

        if attendanceOutboxService.pendingCountForUser(userKey: userKey) > 0 {
            let summary = await flushAttendanceOutboxOnly(isManual: true)
            if summary.syncedCount > 0 {
                showBanner("Đã gửi bù thành công \(summary.syncedCount) bản ghi chấm công.")
            } else if summary.retryableFailureCount > 0 {
                showBanner("Chưa thể gửi bù chấm công lúc này. Sẽ thử lại sau theo chu kỳ an toàn.", isError: true)
            }
        } else {
            isSyncingAttendance = true
            defer {
                isSyncingAttendance = false
            }
            _ = await refreshAttendanceHistoryOnly(force: true)
            showBanner("Đã đồng bộ trạng thái chấm công.")
        }
    }

    // MARK: - Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") Actions

    public static let leaveInboxPageSize: Int = 15

    public var currentLeaveActorAppUserId: String? {
        if let authId = myLeaveSummary?.authority.appUserId.trimmingCharacters(in: .whitespacesAndNewlines),
           !authId.isEmpty {
            return authId
        }
        if let userId = currentUser?.id.trimmingCharacters(in: .whitespacesAndNewlines),
           !userId.isEmpty {
            return userId
        }
        return nil
    }

    public func resolveVisibleAttendanceTabs() -> [AttendanceAreaTab] {
        LeaveContract.resolveVisibleAttendanceTabs(
            authority: myLeaveSummary?.authority,
            errorCode: myLeaveErrorCode,
            explanationViewer: myExplanationsOverview?.viewer,
            explanationInboxViewer: explanationApprovalInbox?.viewer,
            explanationErrorCode: myExplanationsErrorCode,
            explanationInboxErrorCode: explanationInboxErrorCode
        )
    }

    public func selectAttendanceTab(_ tab: AttendanceAreaTab) {
        let visibleTabs = resolveVisibleAttendanceTabs()
        let safeTab = visibleTabs.contains(tab) ? tab : .checkIn
        activeAttendanceTab = safeTab
        leaveMutationError = nil
        explanationMutationError = nil

        switch safeTab {
        case .checkIn:
            if isAuthenticated {
                Task { await syncAttendanceTimeAnchor() }
            }
        case .explanation:
            if myExplanationsOverview == nil && !isLoadingMyExplanations {
                Task { await refreshMyAttendanceExplanations() }
            }
        case .leaveRequest:
            ensureInitialLeaveDraftDay()
            if myLeaveSummary == nil && !isLoadingMyLeave {
                Task { await refreshMyLeaveSummary(year: leaveYear) }
            }
        case .explanationApproval:
            Task {
                await refreshExplanationApprovalInbox(
                    statusFilter: explanationInboxStatusFilter,
                    page: explanationInboxPage
                )
            }
        case .leaveApproval:
            Task {
                await refreshLeaveApprovalInbox(
                    statusFilter: leaveInboxStatusFilter,
                    page: leaveInboxPage
                )
            }
        }
    }

    public func ensureInitialLeaveDraftDay() {
        let summary = myLeaveSummary
        let defaultType = (summary != nil && summary!.balance.availableUnits <= 0) ? "UNPAID" : "ANNUAL"
        if leaveDraftDays.isEmpty {
            let asOf = summary?.asOfDate ?? ""
            let defaultDate = LeaveContract.isValidIsoDate(asOf)
                ? asOf
                : LeaveContract.todayVietnamIsoDate()
            leaveDraftDays = [
                LeaveDraftDayInput(leaveDate: defaultDate, sessionCode: "FULL", leaveType: defaultType)
            ]
        } else if let summary,
                  summary.balance.availableUnits <= 0,
                  leaveDraftDays.count == 1,
                  leaveDraftDays[0].leaveType == "ANNUAL" {
            leaveDraftDays[0].leaveType = "UNPAID"
        }
    }

    public func addLeaveDraftDay() {
        if leaveDraftDays.count >= 120 {
            leaveMutationError = "Mỗi đơn chỉ được tối đa 120 dòng ngày/buổi nghỉ."
            return
        }
        leaveMutationError = nil
        let summary = myLeaveSummary
        let fallbackDate = summary?.asOfDate ?? LeaveContract.todayVietnamIsoDate()
        let defaultType = leaveDraftDays.last?.leaveType
            ?? ((summary != nil && summary!.balance.availableUnits <= 0) ? "UNPAID" : "ANNUAL")
        if let lastDate = leaveDraftDays.last?.leaveDate {
            let nextDate = LeaveContract.nextIsoDateAfter(lastDate, fallbackDate: fallbackDate)
            leaveDraftDays.append(LeaveDraftDayInput(leaveDate: nextDate, sessionCode: "FULL", leaveType: defaultType))
        } else {
            let startDate = LeaveContract.isValidIsoDate(fallbackDate) ? fallbackDate : LeaveContract.todayVietnamIsoDate()
            leaveDraftDays.append(LeaveDraftDayInput(leaveDate: startDate, sessionCode: "FULL", leaveType: defaultType))
        }
    }

    public func removeLeaveDraftDay(id: String) {
        leaveMutationError = nil
        leaveDraftDays.removeAll(where: { $0.id == id })
        ensureInitialLeaveDraftDay()
    }

    public func updateLeaveDraftDayDate(id: String, leaveDate: String) {
        leaveMutationError = nil
        guard let idx = leaveDraftDays.firstIndex(where: { $0.id == id }) else { return }
        leaveDraftDays[idx].leaveDate = leaveDate.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public func updateLeaveDraftDaySession(id: String, sessionCode: String) {
        leaveMutationError = nil
        let normalized = sessionCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard LeaveContract.sessionCodes.contains(normalized),
              let idx = leaveDraftDays.firstIndex(where: { $0.id == id }) else {
            return
        }
        leaveDraftDays[idx].sessionCode = normalized
    }

    public func updateLeaveDraftDayType(id: String, leaveType: String) {
        leaveMutationError = nil
        let normalized = leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard LeaveContract.leaveTypes.contains(normalized),
              let idx = leaveDraftDays.firstIndex(where: { $0.id == id }) else {
            return
        }
        leaveDraftDays[idx].leaveType = normalized
    }

    public func splitLeaveDraftDayToHalfDays(id: String) {
        if leaveDraftDays.count >= 120 {
            leaveMutationError = "Mỗi đơn chỉ được tối đa 120 dòng ngày/buổi nghỉ."
            return
        }
        leaveMutationError = nil
        guard let idx = leaveDraftDays.firstIndex(where: { $0.id == id }) else { return }
        let target = leaveDraftDays[idx]
        guard target.sessionCode.uppercased() == "FULL" else { return }
        let rawType = target.leaveType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let primaryType = LeaveContract.leaveTypes.contains(rawType) ? rawType : "ANNUAL"
        let secondaryType = primaryType == "ANNUAL" ? "UNPAID" : "ANNUAL"
        var amRow = target
        amRow.sessionCode = "AM"
        amRow.leaveType = primaryType
        let pmRow = LeaveDraftDayInput(
            leaveDate: target.leaveDate,
            sessionCode: "PM",
            leaveType: secondaryType
        )
        leaveDraftDays[idx] = amRow
        leaveDraftDays.insert(pmRow, at: idx + 1)
    }

    public func autoAllocateLeaveDraftDaysByBalance() {
        leaveMutationError = nil
        let availableUnits = max(0, myLeaveSummary?.balance.availableUnits ?? 0)
        leaveDraftDays = LeaveContract.autoAllocateDraftDaysByBalance(
            days: leaveDraftDays,
            availableUnits: availableUnits
        )
    }

    @discardableResult
    public func applyLeaveDraftDateRange(
        startDateIso: String,
        endDateIso: String,
        leaveType: String,
        skipSundays: Bool = false
    ) -> Bool {
        leaveMutationError = nil
        let res = LeaveContract.generateDraftDaysFromRange(
            startDateIso: startDateIso,
            endDateIso: endDateIso,
            leaveType: leaveType,
            sessionCode: "FULL",
            skipSundays: skipSundays
        )
        if let err = res.errorMessage {
            leaveMutationError = err
            return false
        }
        leaveDraftDays = res.days
        return true
    }

    public func updateLeaveDraftReason(_ reason: String) {
        leaveMutationError = nil
        leaveDraftReason = reason
    }

    public func clearLeaveMutationError() {
        leaveMutationError = nil
    }

    public func setLeaveYear(_ year: Int) {
        guard (2000...2100).contains(year) else { return }
        leaveYear = year
        Task { await refreshMyLeaveSummary(year: year) }
    }

    public func refreshMyLeaveSummary(year: Int? = nil, force: Bool = false) async {
        guard captureSessionToken() != nil else { return }
        let targetYear = year ?? leaveYear
        await runDeduplicatedSessionTask(key: "leave_me:\(targetYear)", force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            self.isLoadingMyLeave = true
            self.myLeaveError = nil
            self.myLeaveErrorCode = nil
            defer {
                if self.isSessionValid(token) {
                    self.isLoadingMyLeave = false
                }
            }
            let result = await self.leaveService.fetchMyLeaveSummary(leaveYear: targetYear)
            guard !Task.isCancelled, self.isSessionValid(token) else { return }

            switch result {
            case .success(let summary, _):
                self.myLeaveSummary = summary
                self.leaveYear = summary.leaveYear
                self.myLeaveError = nil
                self.myLeaveErrorCode = nil
                self.ensureInitialLeaveDraftDay()

                let visibleTabs = self.resolveVisibleAttendanceTabs()
                if !visibleTabs.contains(self.activeAttendanceTab) {
                    self.activeAttendanceTab = .checkIn
                }
                if summary.authority.canApproveRequests &&
                    !summary.authority.isSharedNonEmployee &&
                    (self.activeAttendanceTab == .leaveApproval || self.leaveApprovalInbox != nil) {
                    await self.refreshLeaveApprovalInbox(
                        statusFilter: self.leaveInboxStatusFilter,
                        page: self.leaveInboxPage
                    )
                }

            case .failure(let message, _, let errorCode, _):
                guard !result.isCancelledRequest else { return }
                self.myLeaveError = message
                self.myLeaveErrorCode = errorCode
                if errorCode?.uppercased() == "LEAVE_SHARED_ACCOUNT_FORBIDDEN" {
                    self.myLeaveSummary = nil
                    self.activeAttendanceTab = .checkIn
                }
            }
        }
    }

    public func submitLeaveRequest(onSuccess: (() -> Void)? = nil) async {
        guard !isMutatingLeave else { return }
        let daysSnapshot = leaveDraftDays
        let reasonSnapshot = leaveDraftReason
        let summary = myLeaveSummary

        if let validationError = LeaveContract.validateCreateDraftInput(
            days: daysSnapshot,
            reason: reasonSnapshot,
            leaveYear: summary?.leaveYear,
            availableUnits: summary?.balance.availableUnits
        ) {
            leaveMutationError = validationError
            showBanner(validationError, isError: true)
            return
        }

        isMutatingLeave = true
        leaveMutationError = nil
        let result = await leaveService.createLeaveRequest(
            days: daysSnapshot,
            reason: reasonSnapshot
        )
        isMutatingLeave = false

        switch result {
        case .success:
            leaveDraftDays = []
            ensureInitialLeaveDraftDay()
            leaveDraftReason = ""
            showBanner("Đã gửi đơn xin nghỉ phép thành công.")
            await refreshMyLeaveSummary(year: leaveYear)
            onSuccess?()

        case .failure(let message, let httpStatus, let errorCode, _):
            leaveMutationError = message
            showBanner(message, isError: true)
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 ||
                codeUpper == "LEAVE_ROW_VERSION_CONFLICT" ||
                codeUpper == "LEAVE_INVALID_STATE_TRANSITION" {
                await refreshMyLeaveSummary(year: leaveYear)
            }
        }
    }

    public func withdrawLeaveRequest(
        request: LeaveRequestProjection,
        reason: String?,
        onSuccess: (() -> Void)? = nil
    ) async {
        guard !isMutatingLeave else { return }
        let actorId = currentLeaveActorAppUserId
        guard LeaveContract.canWithdrawOwnLeaveRequest(actorAppUserId: actorId, request: request) else {
            let msg = "Đơn này không ở trạng thái Chờ duyệt hoặc không thuộc quyền tự rút của bạn."
            leaveMutationError = msg
            showBanner(msg, isError: true)
            return
        }

        isMutatingLeave = true
        leaveMutationError = nil
        let result = await leaveService.withdrawLeaveRequest(
            requestId: request.requestId,
            expectedRowVersion: request.rowVersion,
            reason: reason
        )
        isMutatingLeave = false

        switch result {
        case .success:
            showBanner("Đã rút đơn xin nghỉ phép.")
            await refreshMyLeaveSummary(year: leaveYear)
            onSuccess?()

        case .failure(let message, let httpStatus, let errorCode, _):
            leaveMutationError = message
            showBanner(message, isError: true)
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 ||
                codeUpper == "LEAVE_ROW_VERSION_CONFLICT" ||
                codeUpper == "LEAVE_INVALID_STATE_TRANSITION" {
                await refreshMyLeaveSummary(year: leaveYear)
            }
        }
    }

    public func setLeaveInboxFilter(_ statusFilter: String) {
        let clean = statusFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard LeaveContract.requestStatusFilters.contains(clean) else { return }
        leaveInboxStatusFilter = clean
        leaveInboxPage = 1
        Task {
            await refreshLeaveApprovalInbox(statusFilter: clean, page: 1)
        }
    }

    public func setLeaveInboxPage(_ page: Int) {
        let safePage = max(page, 1)
        leaveInboxPage = safePage
        Task {
            await refreshLeaveApprovalInbox(statusFilter: leaveInboxStatusFilter, page: safePage)
        }
    }

    public func refreshLeaveApprovalInbox(
        statusFilter: String? = nil,
        page: Int? = nil
    ) async {
        if let authority = myLeaveSummary?.authority,
           (!authority.canApproveRequests || authority.isSharedNonEmployee) {
            leaveApprovalInbox = nil
            return
        }

        let effectiveFilter = statusFilter ?? leaveInboxStatusFilter
        let safePage = max(page ?? leaveInboxPage, 1)
        let limit = Self.leaveInboxPageSize
        let offset = (safePage - 1) * limit
        let dedupeKey = "leave_inbox:\(effectiveFilter):\(safePage)"

        if captureSessionToken() != nil {
            await runDeduplicatedSessionTask(key: dedupeKey, force: true) { [weak self] token in
                guard let self, self.isSessionValid(token) else { return }
                self.isLoadingLeaveInbox = true
                self.leaveInboxError = nil
                defer {
                    if self.isSessionValid(token) {
                        self.isLoadingLeaveInbox = false
                    }
                }
                let result = await self.leaveService.fetchApprovalInbox(
                    statusFilter: effectiveFilter,
                    limit: limit,
                    offset: offset
                )
                guard !Task.isCancelled, self.isSessionValid(token) else { return }

                switch result {
                case .success(let inbox, _):
                    self.leaveApprovalInbox = inbox
                    self.leaveInboxError = nil

                case .failure(let message, let httpStatus, let errorCode, _):
                    guard !result.isCancelledRequest else { return }
                    self.leaveInboxError = message
                    let codeUpper = (errorCode ?? "").uppercased()
                    if httpStatus == 403 || codeUpper == "LEAVE_APPROVAL_FORBIDDEN" {
                        self.leaveApprovalInbox = nil
                        if self.activeAttendanceTab == .leaveApproval {
                            self.activeAttendanceTab = .checkIn
                        }
                    }
                }
            }
        } else {
            isLoadingLeaveInbox = true
            leaveInboxError = nil
            defer {
                isLoadingLeaveInbox = false
            }
            let result = await leaveService.fetchApprovalInbox(
                statusFilter: effectiveFilter,
                limit: limit,
                offset: offset
            )
            guard !Task.isCancelled else { return }

            switch result {
            case .success(let inbox, _):
                leaveApprovalInbox = inbox
                leaveInboxError = nil

            case .failure(let message, let httpStatus, let errorCode, _):
                guard !result.isCancelledRequest else { return }
                leaveInboxError = message
                let codeUpper = (errorCode ?? "").uppercased()
                if httpStatus == 403 || codeUpper == "LEAVE_APPROVAL_FORBIDDEN" {
                    leaveApprovalInbox = nil
                    if activeAttendanceTab == .leaveApproval {
                        activeAttendanceTab = .checkIn
                    }
                }
            }
        }
    }

    public func loadMoreLeaveApprovalInbox() async {
        guard let currentInbox = leaveApprovalInbox,
              !isLoadingLeaveInbox,
              !isLoadingMoreLeaveInbox,
              currentInbox.items.count < currentInbox.total else {
            return
        }
        let nextOffset = currentInbox.items.count
        let limit = Self.leaveInboxPageSize
        let effectiveFilter = leaveInboxStatusFilter

        isLoadingMoreLeaveInbox = true
        let result = await leaveService.fetchApprovalInbox(
            statusFilter: effectiveFilter,
            limit: limit,
            offset: nextOffset
        )
        isLoadingMoreLeaveInbox = false

        if case .success(let pageData, _) = result {
            let existingIds = Set(currentInbox.items.map { $0.requestId })
            let appended = pageData.items.filter { !existingIds.contains($0.requestId) }
            let combined = currentInbox.items + appended
            leaveApprovalInbox = LeaveApprovalInboxProjection(
                asOfDate: pageData.asOfDate,
                statusFilter: pageData.statusFilter,
                total: max(pageData.total, combined.count),
                limit: limit,
                offset: 0,
                items: combined
            )
            leaveInboxPage = max(1, (combined.count + limit - 1) / limit)
        }
    }

    public func decideLeaveRequest(
        request: LeaveRequestProjection,
        decision: String,
        reason: String?,
        onSuccess: (() -> Void)? = nil
    ) async {
        guard !isMutatingLeave else { return }
        let actorId = currentLeaveActorAppUserId
        let validation = LeaveContract.validateApproverDecisionInput(
            actorAppUserId: actorId,
            request: request,
            decision: decision,
            reason: reason ?? ""
        )
        guard validation.isValid else {
            let msg = validation.errorMessage ?? "Quyết định xử lý đơn không hợp lệ."
            leaveMutationError = msg
            showBanner(msg, isError: true)
            return
        }

        let cleanDecision = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        isMutatingLeave = true
        leaveMutationError = nil
        let result = await leaveService.decideLeaveRequest(
            requestId: request.requestId,
            decision: cleanDecision,
            expectedRowVersion: request.rowVersion,
            reason: reason
        )
        isMutatingLeave = false

        switch result {
        case .success:
            let successMsg: String
            switch cleanDecision {
            case "APPROVE":
                successMsg = "Đã duyệt đơn nghỉ phép."
            case "REJECT":
                successMsg = "Đã từ chối đơn nghỉ phép."
            case "CANCEL_APPROVED":
                successMsg = "Đã hủy đơn nghỉ phép đã duyệt và hoàn phép."
            default:
                successMsg = "Đã cập nhật quyết định duyệt nghỉ phép."
            }
            showBanner(successMsg)
            await refreshLeaveApprovalInbox(
                statusFilter: leaveInboxStatusFilter,
                page: leaveInboxPage
            )
            await refreshMyLeaveSummary(year: leaveYear)
            onSuccess?()

        case .failure(let message, let httpStatus, let errorCode, _):
            leaveMutationError = message
            showBanner(message, isError: true)
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 ||
                codeUpper == "LEAVE_ROW_VERSION_CONFLICT" ||
                codeUpper == "LEAVE_INVALID_STATE_TRANSITION" {
                await refreshLeaveApprovalInbox(
                    statusFilter: leaveInboxStatusFilter,
                    page: leaveInboxPage
                )
                await refreshMyLeaveSummary(year: leaveYear)
            }
        }
    }

    // MARK: - Attendance Explanation ("Giải trình chấm công") & Approval ("Xét duyệt giải trình") Actions

    public var currentExplanationActorAppUserId: String? {
        if let inboxViewerId = explanationApprovalInbox?.viewer.appUserId.trimmingCharacters(in: .whitespacesAndNewlines),
           !inboxViewerId.isEmpty {
            return inboxViewerId
        }
        if let viewerId = myExplanationsOverview?.viewer.appUserId.trimmingCharacters(in: .whitespacesAndNewlines),
           !viewerId.isEmpty {
            return viewerId
        }
        if let authId = myLeaveSummary?.authority.appUserId.trimmingCharacters(in: .whitespacesAndNewlines),
           !authId.isEmpty {
            return authId
        }
        if let userId = currentUser?.id.trimmingCharacters(in: .whitespacesAndNewlines),
           !userId.isEmpty {
            return userId
        }
        return nil
    }

    public func clearExplanationMutationError() {
        explanationMutationError = nil
    }

    public func setExplanationSelectedDate(_ businessDate: String) {
        let cleanDate = businessDate.trimmingCharacters(in: .whitespacesAndNewlines)
        guard AttendanceExplanationContract.isValidIsoDate(cleanDate) else {
            explanationMutationError = "Ngày tra cứu phải đúng định dạng YYYY-MM-DD."
            return
        }
        let todayLocal: String = {
            if let tl = myExplanationsOverview?.todayLocal, AttendanceExplanationContract.isValidIsoDate(tl) {
                return tl
            }
            return AttendanceExplanationContract.todayVietnamIsoDate()
        }()
        if cleanDate > todayLocal {
            explanationMutationError = "Chưa thể tra cứu hoặc gửi giải trình cho ngày trong tương lai."
            return
        }
        explanationMutationError = nil
        explanationSelectedDate = cleanDate
        Task {
            await refreshMyAttendanceExplanations(businessDate: cleanDate)
        }
    }

    public func shiftExplanationSelectedDate(days: Int) {
        let current = explanationSelectedDate
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.dateFormat = "yyyy-MM-dd"
        guard let parsed = df.date(from: current) else { return }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        guard let nextDate = cal.date(byAdding: .day, value: days, to: parsed) else { return }
        let nextStr = df.string(from: nextDate)
        let todayLocal = myExplanationsOverview?.todayLocal ?? AttendanceExplanationContract.todayVietnamIsoDate()
        if nextStr <= todayLocal {
            setExplanationSelectedDate(nextStr)
        }
    }

    public func refreshMyAttendanceExplanations(businessDate: String? = nil, force: Bool = false) async {
        guard captureSessionToken() != nil else { return }
        let targetDate: String? = {
            let candidate = businessDate ?? explanationSelectedDate
            let clean = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            return AttendanceExplanationContract.isValidIsoDate(clean) ? clean : nil
        }()
        await runDeduplicatedSessionTask(key: "explanations_me:\(targetDate ?? "")", force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            self.isLoadingMyExplanations = true
            self.myExplanationsError = nil
            self.myExplanationsErrorCode = nil
            defer {
                if self.isSessionValid(token) {
                    self.isLoadingMyExplanations = false
                }
            }
            let result = await self.attendanceExplanationService.fetchMyExplanationsOverview(
                businessDate: targetDate,
                limit: Self.leaveInboxPageSize,
                offset: 0
            )
            guard !Task.isCancelled, self.isSessionValid(token) else { return }

            switch result {
            case .success(let overview, _):
                self.myExplanationsOverview = overview
                self.explanationSelectedDate = overview.selectedDate
                self.myExplanationsError = nil
                self.myExplanationsErrorCode = nil

                let visibleTabs = self.resolveVisibleAttendanceTabs()
                if !visibleTabs.contains(self.activeAttendanceTab) {
                    self.activeAttendanceTab = .checkIn
                }
                if overview.viewer.canApproveExplanations &&
                    !overview.viewer.isSharedNonEmployee &&
                    (self.activeAttendanceTab == .explanationApproval || self.explanationApprovalInbox != nil) {
                    await self.refreshExplanationApprovalInbox(
                        statusFilter: self.explanationInboxStatusFilter,
                        page: self.explanationInboxPage
                    )
                }

            case .failure(let message, _, let errorCode, _):
                guard !result.isCancelledRequest else { return }
                self.myExplanationsError = message
                self.myExplanationsErrorCode = errorCode
                let codeUpper = (errorCode ?? "").uppercased()
                if codeUpper == "ATTENDANCE_EXPLANATION_ACCESS_DENIED" ||
                    codeUpper == "LEAVE_SHARED_ACCOUNT_FORBIDDEN" {
                    self.myExplanationsOverview = nil
                    let visibleTabs = self.resolveVisibleAttendanceTabs()
                    if !visibleTabs.contains(self.activeAttendanceTab) {
                        self.activeAttendanceTab = .checkIn
                    }
                }
            }
        }
    }

    public func loadMoreMyExplanations() async {
        guard let currentOverview = myExplanationsOverview,
              !isLoadingMyExplanations,
              !isLoadingMoreMyExplanations,
              currentOverview.items.count < currentOverview.pagination.totalRows else {
            return
        }
        let nextOffset = currentOverview.items.count
        let limit = Self.leaveInboxPageSize
        let targetDate = explanationSelectedDate

        isLoadingMoreMyExplanations = true
        let result = await attendanceExplanationService.fetchMyExplanationsOverview(
            businessDate: targetDate,
            limit: limit,
            offset: nextOffset
        )
        isLoadingMoreMyExplanations = false

        if case .success(let pageData, _) = result {
            let existingIds = Set(currentOverview.items.map { $0.explanationId })
            let appended = pageData.items.filter { !existingIds.contains($0.explanationId) }
            let combined = currentOverview.items + appended
            myExplanationsOverview = AttendanceMyExplanationsOverviewProjection(
                viewer: pageData.viewer,
                routing: pageData.routing,
                todayLocal: pageData.todayLocal,
                selectedDate: pageData.selectedDate,
                dayEvaluation: pageData.dayEvaluation,
                pagination: AttendanceExplanationPaginationProjection(
                    limit: limit,
                    offset: 0,
                    totalRows: max(pageData.pagination.totalRows, combined.count)
                ),
                items: combined
            )
        }
    }

    public func submitAttendanceExplanation(
        businessDate: String? = nil,
        slotCode: String,
        reason: String,
        onSuccess: (() -> Void)? = nil
    ) async {
        guard !isMutatingExplanation else { return }
        let cleanDate = (businessDate ?? explanationSelectedDate).trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSlot = slotCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let todayLocal = myExplanationsOverview?.todayLocal
        if let validationError = AttendanceExplanationContract.validateSubmitInput(
            businessDate: cleanDate,
            slotCode: cleanSlot,
            reason: reason,
            todayLocal: todayLocal
        ) {
            explanationMutationError = validationError
            showBanner(validationError, isError: true)
            return
        }

        isMutatingExplanation = true
        explanationMutationError = nil
        let result = await attendanceExplanationService.submitExplanation(
            businessDate: cleanDate,
            slotCode: cleanSlot,
            reason: reason
        )
        isMutatingExplanation = false

        switch result {
        case .success:
            showBanner("Đã gửi giải trình chấm công thành công.")
            await refreshMyAttendanceExplanations(businessDate: cleanDate)
            onSuccess?()

        case .failure(let message, let httpStatus, let errorCode, _):
            explanationMutationError = message
            showBanner(message, isError: true)
            if AttendanceExplanationContract.isConflictError(httpStatus: httpStatus, errorCode: errorCode) {
                await refreshMyAttendanceExplanations(businessDate: cleanDate)
            }
        }
    }

    public func setExplanationInboxFilter(_ statusFilter: String) {
        let clean = statusFilter.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard AttendanceExplanationContract.approvalStatusFilters.contains(clean) else { return }
        explanationInboxStatusFilter = clean
        explanationInboxPage = 1
        Task {
            await refreshExplanationApprovalInbox(statusFilter: clean, page: 1)
        }
    }

    public func setExplanationInboxPage(_ page: Int) {
        let safePage = max(1, page)
        explanationInboxPage = safePage
        Task {
            await refreshExplanationApprovalInbox(statusFilter: explanationInboxStatusFilter, page: safePage)
        }
    }

    public func refreshExplanationApprovalInbox(
        statusFilter: String? = nil,
        page: Int? = nil
    ) async {
        let overviewViewer = myExplanationsOverview?.viewer
        let authority = myLeaveSummary?.authority
        let explicitlyDenied: Bool = {
            if let ov = overviewViewer {
                return !ov.canApproveExplanations || ov.isSharedNonEmployee
            }
            if let auth = authority {
                return !auth.canApproveRequests || auth.isSharedNonEmployee
            }
            return false
        }()
        if explicitlyDenied {
            explanationApprovalInbox = nil
            return
        }

        let effectiveFilter = statusFilter ?? explanationInboxStatusFilter
        let safePage = max(1, page ?? explanationInboxPage)
        let limit = Self.leaveInboxPageSize
        let offset = (safePage - 1) * limit
        let dedupeKey = "explanation_inbox:\(effectiveFilter):\(safePage)"

        if captureSessionToken() != nil {
            await runDeduplicatedSessionTask(key: dedupeKey, force: true) { [weak self] token in
                guard let self, self.isSessionValid(token) else { return }
                self.isLoadingExplanationInbox = true
                self.explanationInboxError = nil
                self.explanationInboxErrorCode = nil
                defer {
                    if self.isSessionValid(token) {
                        self.isLoadingExplanationInbox = false
                    }
                }
                let result = await self.attendanceExplanationService.fetchApprovalInbox(
                    statusFilter: effectiveFilter,
                    limit: limit,
                    offset: offset
                )
                guard !Task.isCancelled, self.isSessionValid(token) else { return }

                switch result {
                case .success(let inbox, _):
                    self.explanationApprovalInbox = inbox
                    self.explanationInboxError = nil
                    self.explanationInboxErrorCode = nil
                    if !inbox.viewer.canApproveExplanations && self.activeAttendanceTab == .explanationApproval {
                        self.activeAttendanceTab = .checkIn
                    }

                case .failure(let message, let httpStatus, let errorCode, _):
                    guard !result.isCancelledRequest else { return }
                    self.explanationInboxError = message
                    self.explanationInboxErrorCode = errorCode
                    let codeUpper = (errorCode ?? "").uppercased()
                    if httpStatus == 403 || codeUpper == "ATTENDANCE_EXPLANATION_APPROVAL_ACCESS_DENIED" {
                        self.explanationApprovalInbox = nil
                        if self.activeAttendanceTab == .explanationApproval {
                            self.activeAttendanceTab = .checkIn
                        }
                    }
                }
            }
        } else {
            isLoadingExplanationInbox = true
            explanationInboxError = nil
            explanationInboxErrorCode = nil
            defer {
                isLoadingExplanationInbox = false
            }
            let result = await attendanceExplanationService.fetchApprovalInbox(
                statusFilter: effectiveFilter,
                limit: limit,
                offset: offset
            )
            guard !Task.isCancelled else { return }

            switch result {
            case .success(let inbox, _):
                explanationApprovalInbox = inbox
                explanationInboxError = nil
                explanationInboxErrorCode = nil
                if !inbox.viewer.canApproveExplanations && activeAttendanceTab == .explanationApproval {
                    activeAttendanceTab = .checkIn
                }

            case .failure(let message, let httpStatus, let errorCode, _):
                guard !result.isCancelledRequest else { return }
                explanationInboxError = message
                explanationInboxErrorCode = errorCode
                let codeUpper = (errorCode ?? "").uppercased()
                if httpStatus == 403 || codeUpper == "ATTENDANCE_EXPLANATION_APPROVAL_ACCESS_DENIED" {
                    explanationApprovalInbox = nil
                    if activeAttendanceTab == .explanationApproval {
                        activeAttendanceTab = .checkIn
                    }
                }
            }
        }
    }

    public func loadMoreExplanationApprovalInbox() async {
        guard let currentInbox = explanationApprovalInbox,
              !isLoadingExplanationInbox,
              !isLoadingMoreExplanationInbox,
              currentInbox.items.count < currentInbox.pagination.totalRows else {
            return
        }
        let nextOffset = currentInbox.items.count
        let limit = Self.leaveInboxPageSize
        let effectiveFilter = explanationInboxStatusFilter

        isLoadingMoreExplanationInbox = true
        let result = await attendanceExplanationService.fetchApprovalInbox(
            statusFilter: effectiveFilter,
            limit: limit,
            offset: nextOffset
        )
        isLoadingMoreExplanationInbox = false

        if case .success(let pageData, _) = result {
            let existingIds = Set(currentInbox.items.map { $0.explanationId })
            let appended = pageData.items.filter { !existingIds.contains($0.explanationId) }
            let combined = currentInbox.items + appended
            explanationApprovalInbox = AttendanceExplanationApprovalInboxProjection(
                viewer: pageData.viewer,
                pagination: AttendanceExplanationApprovalInboxPaginationProjection(
                    limit: limit,
                    offset: 0,
                    totalRows: max(pageData.pagination.totalRows, combined.count),
                    pendingCount: pageData.pagination.pendingCount
                ),
                items: combined
            )
            explanationInboxPage = max(1, (combined.count + limit - 1) / limit)
        }
    }

    public func decideAttendanceExplanation(
        item: AttendanceExplanationItemProjection,
        decision: String,
        decisionNote: String?,
        onSuccess: (() -> Void)? = nil
    ) async {
        guard !isMutatingExplanation else { return }
        let actorId = currentUser?.id
            ?? explanationApprovalInbox?.viewer.appUserId
            ?? myExplanationsOverview?.viewer.appUserId
            ?? myLeaveSummary?.authority.appUserId
        if let validationError = AttendanceExplanationContract.validateDecisionInput(
            actorAppUserId: actorId,
            item: item,
            decision: decision,
            decisionNote: decisionNote
        ) {
            explanationMutationError = validationError
            showBanner(validationError, isError: true)
            return
        }

        let cleanDecision = decision.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        isMutatingExplanation = true
        explanationMutationError = nil
        let result = await attendanceExplanationService.decideExplanation(
            explanationId: item.explanationId,
            decision: cleanDecision,
            expectedRowVersion: item.rowVersion,
            decisionNote: decisionNote
        )
        isMutatingExplanation = false

        switch result {
        case .success:
            let msg = cleanDecision == "APPROVE"
                ? "Đã duyệt giải trình chấm công."
                : "Đã từ chối giải trình chấm công."
            showBanner(msg)
            await refreshExplanationApprovalInbox(
                statusFilter: explanationInboxStatusFilter,
                page: explanationInboxPage
            )
            await refreshMyAttendanceExplanations()
            onSuccess?()

        case .failure(let message, let httpStatus, let errorCode, _):
            explanationMutationError = message
            showBanner(message, isError: true)
            if AttendanceExplanationContract.isConflictError(httpStatus: httpStatus, errorCode: errorCode) {
                await refreshExplanationApprovalInbox(
                    statusFilter: explanationInboxStatusFilter,
                    page: explanationInboxPage
                )
                await refreshMyAttendanceExplanations()
            }
        }
    }

    // MARK: - Work Reports Actions (Including `is_overdue` & `WORK_REPORT_DEADLINE_PASSED` Contract)

    public func loadWorkReportsData(force: Bool = false) async {
        guard captureSessionToken() != nil else { return }
        await runDeduplicatedSessionTask(key: "work_reports", force: force) { [weak self] token in
            guard let self, self.isSessionValid(token) else { return }
            self.isLoadingWorkReports = true
            defer {
                if self.isSessionValid(token) {
                    self.isLoadingWorkReports = false
                }
            }

            await self.loadWorkReportObligations()
            guard !Task.isCancelled, self.isSessionValid(token) else { return }

            async let reviewsTask = self.workReportsService.fetchPendingReviews(
                cursor: nil,
                pageSize: Self.leaveInboxPageSize
            )
            async let templatesTask = self.workReportsService.fetchSelectableTemplates()
            async let analyticsTask = self.workReportsService.fetchAnalyticsSnapshot()

            let revRes = await reviewsTask
            let tplRes = await templatesTask
            let anaRes = await analyticsTask
            guard !Task.isCancelled, self.isSessionValid(token) else { return }

            if case .success(let revs, _) = revRes {
                self.workReportReviews = revs
                self.workReportReviewsPage = 1
                self.workReportReviewsNextCursor = self.workReportsService.lastPendingReviewsNextCursor
                self.workReportReviewsHasMore = self.workReportsService.lastPendingReviewsHasMore
                    && !(self.workReportReviewsNextCursor ?? "").isEmpty
            }
            if case .success(let tpls, _) = tplRes { self.workReportTemplates = tpls }
            if case .success(let ana, _) = anaRes { self.workReportAnalytics = ana }
        }
    }

    public func selectWorkReportHistoryDate(_ dateIso: String) {
        let clean = dateIso.trimmingCharacters(in: .whitespacesAndNewlines)
        guard LeaveContract.isValidIsoDate(clean) else { return }
        workReportHistorySelectedDate = clean
        workReportHistoryFilterByDate = true
    }

    public func setWorkReportHistoryFilterMode(byDate: Bool) {
        workReportHistoryFilterByDate = byDate
    }

    public func loadMoreWorkReportReviews() async {
        guard let token = captureSessionToken(),
              !isLoadingWorkReports,
              !isLoadingMoreWorkReportReviews,
              workReportReviewsHasMore,
              let cursor = workReportReviewsNextCursor,
              !cursor.isEmpty else {
            return
        }
        let nextPage = workReportReviewsPage + 1
        isLoadingMoreWorkReportReviews = true
        defer {
            isLoadingMoreWorkReportReviews = false
        }
        let revRes = await workReportsService.fetchPendingReviews(
            cursor: cursor,
            pageSize: Self.leaveInboxPageSize
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }
        if case .success(let newItems, _) = revRes {
            let existingIds = Set(workReportReviews.map { $0.id })
            let appended = newItems.filter { !existingIds.contains($0.id) }
            workReportReviews.append(contentsOf: appended)
            workReportReviewsPage = nextPage
            workReportReviewsNextCursor = workReportsService.lastPendingReviewsNextCursor
            workReportReviewsHasMore = workReportsService.lastPendingReviewsHasMore
                && !(workReportReviewsNextCursor ?? "").isEmpty
        }
    }

    public func loadWorkReportObligations() async {
        guard let token = captureSessionToken() else { return }
        let res = await workReportsService.fetchMyObligations()
        guard !Task.isCancelled, isSessionValid(token) else { return }
        if case .success(let list, _) = res {
            workReportObligations = list
            // Keep open editor synchronized if present (without wiping unsaved editorFieldValues!)
            if let currentEditor = selectedObligationEditor,
               let refreshed = list.first(where: {
                   (!currentEditor.obligationIdUuid.isEmpty && $0.obligationIdUuid == currentEditor.obligationIdUuid)
                   || ($0.id != 0 && $0.id == currentEditor.id)
               }) {
                var merged = refreshed
                if merged.sections.isEmpty {
                    merged.sections = currentEditor.sections
                }
                if merged.formPayload.isEmpty {
                    merged.formPayload = currentEditor.formPayload
                }
                if merged.submissionVersion == nil {
                    merged.submissionVersion = currentEditor.submissionVersion
                }
                selectedObligationEditor = merged
            }
        }
    }

    private static func seedDefaultFieldValues(
        base: [String: String],
        sections: [WorkReportSectionSchema]
    ) -> [String: String] {
        var result = base
        for section in sections {
            for field in section.fields where result[field.id] == nil {
                if let def = field.defaultValue, !def.isEmpty {
                    result[field.id] = def
                } else if field.isBooleanType {
                    result[field.id] = "false"
                }
            }
        }
        return result
    }

    public func openObligationEditor(_ obligation: WorkReportObligationItem) async {
        guard let token = captureSessionToken() else { return }
        selectedObligationEditor = obligation
        editorFieldValues = Self.seedDefaultFieldValues(base: obligation.formPayload, sections: obligation.sections)

        // Fetch full detail to ensure latest schema_json, payload_json, submission_version, and `is_overdue`
        let detailRes = await workReportsService.fetchObligationDetail(
            obligationIdUuid: obligation.obligationIdUuid,
            fallback: obligation
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }
        if case .success(let detail, _) = detailRes {
            selectedObligationEditor = detail
            editorFieldValues = Self.seedDefaultFieldValues(base: detail.formPayload, sections: detail.sections)
            if let idx = workReportObligations.firstIndex(where: {
                (!detail.obligationIdUuid.isEmpty && $0.obligationIdUuid == detail.obligationIdUuid)
                || ($0.id != 0 && $0.id == detail.id)
            }) {
                workReportObligations[idx] = detail
            }
        }
    }

    public func closeObligationEditor() {
        selectedObligationEditor = nil
        editorFieldValues = [:]
    }

    public func saveWorkReportDraft() async {
        guard let token = captureSessionToken(),
              let editor = selectedObligationEditor else {
            return
        }
        guard WorkReportsContract.isEditableObligation(editor) else {
            let isInactive = editor.obligationStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() != WorkReportsContract.obligationStatusActive
            showBanner(isInactive ? WorkReportsContract.obligationInactiveMessageVi : WorkReportsContract.deadlinePassedMessageVi, isError: true)
            return
        }

        isSavingWorkReport = true
        let result = await workReportsService.saveDraft(
            obligationIdUuid: editor.obligationIdUuid,
            expectedVersion: editor.submissionVersion,
            formPayload: editorFieldValues,
            sections: editor.sections,
            fallback: editor
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }
        isSavingWorkReport = false

        await handleWorkReportMutationResult(result, currentEditor: editor, isFinalSubmit: false, token: token)
    }

    public func submitWorkReportFinal() async {
        guard let token = captureSessionToken(),
              let editor = selectedObligationEditor else {
            return
        }
        guard WorkReportsContract.isEditableObligation(editor) else {
            let isInactive = editor.obligationStatus.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() != WorkReportsContract.obligationStatusActive
            showBanner(isInactive ? WorkReportsContract.obligationInactiveMessageVi : WorkReportsContract.deadlinePassedMessageVi, isError: true)
            return
        }

        // Validate required fields (boolean toggle fields are valid at both "Có / Đạt" and "Không")
        if let validationError = WorkReportsContract.validateFormPayloadForSubmit(
            formPayload: editorFieldValues,
            sections: editor.sections
        ) {
            showBanner(validationError, isError: true)
            return
        }

        isSavingWorkReport = true
        let result = await workReportsService.submitReport(
            obligationIdUuid: editor.obligationIdUuid,
            expectedVersion: editor.submissionVersion,
            formPayload: editorFieldValues,
            sections: editor.sections,
            fallback: editor
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }
        isSavingWorkReport = false

        await handleWorkReportMutationResult(result, currentEditor: editor, isFinalSubmit: true, token: token)
    }

    private func handleWorkReportMutationResult(
        _ result: MiniBiApiResult<WorkReportObligationItem>,
        currentEditor: WorkReportObligationItem,
        isFinalSubmit: Bool,
        token: SessionRequestToken
    ) async {
        guard !Task.isCancelled, isSessionValid(token) else { return }
        switch result {
        case .success(let updated, _):
            var merged = updated
            if merged.id == 0 && merged.obligationIdUuid.isEmpty { merged = currentEditor }
            if merged.sections.isEmpty { merged.sections = currentEditor.sections }
            if merged.formPayload.isEmpty { merged.formPayload = editorFieldValues }
            if isFinalSubmit {
                merged.submissionState = WorkReportsContract.stateSubmitted
            } else if merged.submissionState == WorkReportsContract.stateNotStarted {
                merged.submissionState = WorkReportsContract.stateDraft
            }
            selectedObligationEditor = merged
            editorFieldValues = Self.seedDefaultFieldValues(base: merged.formPayload, sections: merged.sections)
            if let idx = workReportObligations.firstIndex(where: {
                (!currentEditor.obligationIdUuid.isEmpty && $0.obligationIdUuid == currentEditor.obligationIdUuid)
                || ($0.id != 0 && $0.id == currentEditor.id)
            }) {
                workReportObligations[idx] = merged
            }
            showBanner(isFinalSubmit ? "Đã nộp báo cáo công việc thành công." : "Đã lưu bản nháp báo cáo.")
            await loadWorkReportObligations()

        case .failure(let message, let httpStatus, let errorCode, _):
            let normalizedCode = (errorCode ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if httpStatus == 409 && normalizedCode == WorkReportsContract.codeDeadlinePassed {
                // Requirement 3.6:
                // 1. Display exact Vietnamese message: "Đã quá hạn nộp báo cáo."
                showBanner(WorkReportsContract.deadlinePassedMessageVi, isError: true)
                // 2. Immediately switch form to read-only mode
                var lockedEditor = currentEditor
                lockedEditor.isOverdue = true
                selectedObligationEditor = lockedEditor
                // 3. Move item in obligations list to History tab and reload from server
                if let idx = workReportObligations.firstIndex(where: {
                    (!currentEditor.obligationIdUuid.isEmpty && $0.obligationIdUuid == currentEditor.obligationIdUuid)
                    || ($0.id != 0 && $0.id == currentEditor.id)
                }) {
                    workReportObligations[idx].isOverdue = true
                }
                await loadWorkReportObligations()
            } else if httpStatus == 409 {
                // Version conflict (NOT deadline passed): reload latest version from server,
                // preserve the user's unsaved draft (`editorFieldValues`), and prompt confirmation to re-submit.
                let detailRes = await workReportsService.fetchObligationDetail(
                    obligationIdUuid: currentEditor.obligationIdUuid,
                    fallback: currentEditor
                )
                guard !Task.isCancelled, isSessionValid(token) else { return }
                if case .success(let refreshed, _) = detailRes {
                    var updatedEditor = refreshed
                    if updatedEditor.sections.isEmpty {
                        updatedEditor.sections = currentEditor.sections
                    }
                    selectedObligationEditor = updatedEditor
                    // Seed only missing default keys if any new schema fields appeared; NEVER overwrite user's edited `editorFieldValues`!
                    for (k, v) in Self.seedDefaultFieldValues(base: refreshed.formPayload, sections: updatedEditor.sections) where editorFieldValues[k] == nil {
                        editorFieldValues[k] = v
                    }
                    if let idx = workReportObligations.firstIndex(where: {
                        (!refreshed.obligationIdUuid.isEmpty && $0.obligationIdUuid == refreshed.obligationIdUuid)
                        || ($0.id != 0 && $0.id == refreshed.id)
                    }) {
                        workReportObligations[idx] = updatedEditor
                    }
                }
                showBanner(
                    "Dữ liệu báo cáo đã thay đổi trên hệ thống (phiên bản mới). Nội dung bạn đang nhập được giữ nguyên — vui lòng kiểm tra lại và xác nhận lưu/gửi lại.",
                    isError: true
                )
            } else if httpStatus == 403 {
                showBanner(message, isError: true)
                await loadWorkReportObligations()
                guard !Task.isCancelled, isSessionValid(token) else { return }
                if let refreshed = workReportObligations.first(where: {
                    (!currentEditor.obligationIdUuid.isEmpty && $0.obligationIdUuid == currentEditor.obligationIdUuid)
                    || ($0.id != 0 && $0.id == currentEditor.id)
                }) {
                    selectedObligationEditor = refreshed
                }
            } else {
                showBanner(message, isError: true)
            }
        }
    }

    public func submitWorkReportReviewDecision(
        review: WorkReportReviewItem,
        decision: String,
        comment: String
    ) async {
        guard let token = captureSessionToken() else { return }
        let targetObligationId = review.obligationIdUuid.isEmpty ? review.submissionIdUuid : review.obligationIdUuid
        let res = await workReportsService.decideReview(
            submissionIdUuid: targetObligationId,
            decision: decision,
            comment: comment,
            expectedVersion: review.version
        )
        guard !Task.isCancelled, isSessionValid(token) else { return }
        switch res {
        case .success:
            selectedReviewDetail = nil
            showBanner(decision == WorkReportsContract.decisionApprove ? "Đã phê duyệt báo cáo." : "Đã từ chối báo cáo.")
            await loadWorkReportsData()
        case .failure(let message, let httpStatus, let errorCode, _):
            let codeUpper = (errorCode ?? "").uppercased()
            if httpStatus == 409 || codeUpper.contains("CONFLICT") {
                await loadWorkReportsData()
                guard !Task.isCancelled, isSessionValid(token) else { return }
                if let refreshed = workReportReviews.first(where: {
                    $0.id == review.id
                    || (!review.obligationIdUuid.isEmpty && $0.obligationIdUuid == review.obligationIdUuid)
                }) {
                    selectedReviewDetail = refreshed
                }
                showBanner(
                    "Báo cáo đã thay đổi phiên bản trên máy chủ. Dữ liệu mới nhất đã được tải lại và ghi chú của bạn được giữ nguyên — vui lòng kiểm tra rồi xác nhận lại.",
                    isError: true
                )
            } else {
                showBanner(message, isError: true)
            }
        }
    }

    // MARK: - Recruitment Posts ("Tin tuyển dụng" — Read-Only Feed & Detail)

    public func cancelInFlightRecruitmentPostRequests() {
        recruitmentPostsFetchGeneration &+= 1
        cancelInFlightTasksWithPrefix("recruitment_posts:")
        recruitmentPostsLoading = false
        recruitmentPostsLoadingMore = false
        recruitmentPostDetailFetchGeneration &+= 1
        cancelInFlightTasksWithPrefix("recruitment_post_detail:")
        recruitmentPostDetailLoading = false
    }

    public func resetRecruitmentPostsState() {
        cancelInFlightRecruitmentPostRequests()
        recruitmentPosts = []
        recruitmentPostsError = nil
        recruitmentPostsErrorCode = nil
        recruitmentPostsHasMore = false
        recruitmentPostsNextCursor = nil
        recruitmentPostsSnapshotToken = nil
        recruitmentPostsAccessDenied = false
        recruitmentPostsFeatureUnavailable = false
        selectedRecruitmentPostDetail = nil
        recruitmentPostDetailError = nil
        recruitmentPostDetailBriefId = nil
    }

    public func loadRecruitmentPosts(reset: Bool = true) async {
        await loadRecruitmentPostsInternal(reset: reset, isSnapshotInvalidRetry: false)
    }

    public func loadMoreRecruitmentPosts() async {
        guard !recruitmentPostsLoading,
              !recruitmentPostsLoadingMore,
              recruitmentPostsHasMore,
              !recruitmentPostsAccessDenied,
              !recruitmentPostsFeatureUnavailable else {
            return
        }
        let cursor = recruitmentPostsNextCursor?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let snapshot = recruitmentPostsSnapshotToken?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cursor.isEmpty, !snapshot.isEmpty else {
            return
        }
        await loadRecruitmentPostsInternal(reset: false, isSnapshotInvalidRetry: false)
    }

    private func loadRecruitmentPostsInternal(
        reset: Bool,
        isSnapshotInvalidRetry: Bool
    ) async {
        guard let token = captureSessionToken() else {
            resetRecruitmentPostsState()
            return
        }
        if !reset && (recruitmentPostsLoading || recruitmentPostsLoadingMore) {
            return
        }

        let expectedCursor: String? = reset
            ? nil
            : recruitmentPostsNextCursor?.trimmingCharacters(in: .whitespacesAndNewlines)
        let expectedSnapshot: String? = reset
            ? nil
            : recruitmentPostsSnapshotToken?.trimmingCharacters(in: .whitespacesAndNewlines)

        if !reset && ((expectedCursor?.isEmpty ?? true) || (expectedSnapshot?.isEmpty ?? true)) {
            return
        }

        if reset {
            cancelInFlightTasksWithPrefix("recruitment_posts:")
            recruitmentPostsFetchGeneration &+= 1
            recruitmentPostsLoading = true
            recruitmentPostsLoadingMore = false
            recruitmentPostsError = nil
            recruitmentPostsErrorCode = nil
            recruitmentPostsAccessDenied = false
            recruitmentPostsFeatureUnavailable = false
            recruitmentPostsNextCursor = nil
            recruitmentPostsSnapshotToken = nil
            recruitmentPostsHasMore = false
        } else {
            recruitmentPostsFetchGeneration &+= 1
            recruitmentPostsLoadingMore = true
            recruitmentPostsError = nil
            recruitmentPostsErrorCode = nil
        }

        let expectedFetchGen = recruitmentPostsFetchGeneration
        let res = await recruitmentPostService.listRecruitmentPosts(
            pageSize: RecruitmentPostContract.defaultPageSize,
            cursor: expectedCursor,
            snapshotToken: expectedSnapshot
        )
        guard !Task.isCancelled,
              isSessionValid(token),
              recruitmentPostsFetchGeneration == expectedFetchGen else {
            return
        }

        switch res {
        case .success(let page, _):
            if !reset {
                guard recruitmentPostsNextCursor == expectedCursor,
                      recruitmentPostsSnapshotToken == expectedSnapshot else {
                    recruitmentPostsLoading = false
                    recruitmentPostsLoadingMore = false
                    return
                }
                recruitmentPosts = RecruitmentPostContract.mergeUniqueItems(
                    current: recruitmentPosts,
                    incoming: page.items
                )
                recruitmentPostsSnapshotToken = expectedSnapshot
            } else {
                recruitmentPosts = RecruitmentPostContract.mergeUniqueItems(
                    current: [],
                    incoming: page.items
                )
                recruitmentPostsSnapshotToken = page.snapshotToken
            }
            recruitmentPostsHasMore = page.hasMore
            recruitmentPostsNextCursor = page.nextCursor
            recruitmentPostsError = nil
            recruitmentPostsErrorCode = nil
            recruitmentPostsAccessDenied = false
            recruitmentPostsFeatureUnavailable = false
            recruitmentPostsLoading = false
            recruitmentPostsLoadingMore = false

        case .failure(let message, let httpStatus, let errorCode, _):
            if res.isCancelledRequest {
                recruitmentPostsLoading = false
                recruitmentPostsLoadingMore = false
                return
            }
            let code = errorCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if httpStatus == 401 || code == "UNAUTHENTICATED" {
                resetRecruitmentPostsState()
                return
            }
            if httpStatus == 403 || code == "ACTOR_NOT_AVAILABLE" || code == "RECRUITMENT_POST_ACCESS_DENIED" {
                let deniedCode = code ?? "RECRUITMENT_POST_ACCESS_DENIED"
                resetRecruitmentPostsState()
                recruitmentPostsAccessDenied = true
                recruitmentPostsErrorCode = deniedCode
                recruitmentPostsError = RecruitmentPostContract.errorMessageForCode(deniedCode)
                return
            }
            if code == "RECRUITMENT_POST_SNAPSHOT_INVALID" {
                if !isSnapshotInvalidRetry {
                    recruitmentPostsLoading = false
                    recruitmentPostsLoadingMore = false
                    await loadRecruitmentPostsInternal(reset: true, isSnapshotInvalidRetry: true)
                    return
                }
                recruitmentPostsLoading = false
                recruitmentPostsLoadingMore = false
                recruitmentPostsErrorCode = "RECRUITMENT_POST_SNAPSHOT_INVALID"
                recruitmentPostsError = RecruitmentPostContract.errorMessageForCode("RECRUITMENT_POST_UNAVAILABLE")
                return
            }
            if httpStatus == 404 {
                recruitmentPosts = []
                recruitmentPostsHasMore = false
                recruitmentPostsNextCursor = nil
                recruitmentPostsSnapshotToken = nil
                recruitmentPostsFeatureUnavailable = true
                recruitmentPostsErrorCode = code ?? "NOT_FOUND"
                recruitmentPostsError = "Tính năng Tin tuyển dụng hiện tạm thời không khả dụng."
                recruitmentPostsLoading = false
                recruitmentPostsLoadingMore = false
                return
            }
            recruitmentPostsErrorCode = code ?? "RECRUITMENT_POST_UNAVAILABLE"
            recruitmentPostsError = message.isEmpty
                ? RecruitmentPostContract.errorMessageForCode(code)
                : message
            recruitmentPostsLoading = false
            recruitmentPostsLoadingMore = false
        }
    }

    public func openRecruitmentPostDetail(briefId: String) async {
        guard let token = captureSessionToken() else {
            return
        }
        let cleanBriefId = briefId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanBriefId.isEmpty else { return }

        recruitmentPostDetailFetchGeneration &+= 1
        let expectedDetailGen = recruitmentPostDetailFetchGeneration
        recruitmentPostDetailBriefId = cleanBriefId
        selectedRecruitmentPostDetail = nil
        recruitmentPostDetailError = nil
        recruitmentPostDetailLoading = true

        let res = await recruitmentPostService.getRecruitmentPostDetail(briefId: cleanBriefId)
        guard !Task.isCancelled,
              isSessionValid(token),
              recruitmentPostDetailFetchGeneration == expectedDetailGen,
              recruitmentPostDetailBriefId == cleanBriefId else {
            return
        }

        switch res {
        case .success(let item, _):
            selectedRecruitmentPostDetail = item
            recruitmentPostDetailError = nil
            recruitmentPostDetailLoading = false
            if let idx = recruitmentPosts.firstIndex(where: { $0.briefId == item.briefId }) {
                recruitmentPosts[idx] = item
            }

        case .failure(let message, let httpStatus, let errorCode, _):
            if res.isCancelledRequest {
                recruitmentPostDetailLoading = false
                return
            }
            let code = errorCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if httpStatus == 401 || code == "UNAUTHENTICATED" {
                resetRecruitmentPostsState()
                return
            }
            if httpStatus == 403 || code == "ACTOR_NOT_AVAILABLE" || code == "RECRUITMENT_POST_ACCESS_DENIED" {
                let deniedCode = code ?? "RECRUITMENT_POST_ACCESS_DENIED"
                resetRecruitmentPostsState()
                recruitmentPostsAccessDenied = true
                recruitmentPostsErrorCode = deniedCode
                recruitmentPostsError = RecruitmentPostContract.errorMessageForCode(deniedCode)
                return
            }
            if httpStatus == 404 || code == "RECRUITMENT_POST_NOT_FOUND" {
                closeRecruitmentPostDetail()
                recruitmentPosts.removeAll(where: { $0.briefId == cleanBriefId })
                showBanner("Tin tuyển dụng không còn hiệu lực.", isError: true)
                return
            }
            recruitmentPostDetailLoading = false
            recruitmentPostDetailError = message.isEmpty
                ? RecruitmentPostContract.errorMessageForCode(code)
                : message
        }
    }

    public func closeRecruitmentPostDetail() {
        recruitmentPostDetailFetchGeneration &+= 1
        recruitmentPostDetailLoading = false
        recruitmentPostDetailError = nil
        selectedRecruitmentPostDetail = nil
        recruitmentPostDetailBriefId = nil
    }
}
