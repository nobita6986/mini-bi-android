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
    public let salaryAdvanceService: SalaryAdvanceService

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
    @Published public var currentDestination: MiniBiNavDestination = .dashboard
    @Published public var bannerMessage: String?
    @Published public var bannerIsError: Bool = false
    private var bannerDismissTask: Task<Void, Never>?

    // MARK: - Notifications (SSE + List)
    @Published public var notifications: [AppNotificationItem] = []
    @Published public var unreadNotificationCount: Int = 0
    @Published public var isNotificationSheetPresented: Bool = false
    private var announcedNotificationIds: Set<Int64> = []

    // MARK: - Dashboard & CCCD Review State
    @Published public var selectedDashboardSubTab: DashboardSubTab = .overview
    @Published public var selectedDatePreset: DateFilterPreset = .thisMonth
    @Published public var filterStartDate: String = ""
    @Published public var filterEndDate: String = ""
    @Published public var dashboardScope: String = "all"
    @Published public var isLoadingDashboard: Bool = false
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

    // MARK: - Direct Entry ("Cấp mới") State — Preserved Across Menu/Tab Switches!
    @Published public var newWorkerDraftRows: [NewWorkerEntryDraft] = [NewWorkerEntryDraft()]
    @Published public var directEntryOptions: DirectEntryAssigneeOptions = DirectEntryAssigneeOptions()
    @Published public var directEntryHistoryBatches: [DirectEntryBatchHistoryItem] = []
    @Published public var isSubmittingNewWorkers: Bool = false
    @Published public var pendingDuplicateConfirmCandidates: [DirectEntryDuplicateCandidate] = []
    @Published public var showDuplicateConfirmDialog: Bool = false

    // MARK: - Workers Management State
    @Published public var selectedWorkerScope: String = "all"
    @Published public var workerSearchQuery: String = ""
    @Published public var workerProjectFilter: String = ""
    @Published public var workerStatusFilter: String = ""
    @Published public var workers: [WorkerItem] = []
    @Published public var workersTotalCount: Int = 0
    @Published public var workersPage: Int = 1
    @Published public var workersTotalPages: Int = 1
    @Published public var isLoadingWorkers: Bool = false
    @Published public var selectedWorkerDetail: WorkerItem?
    @Published public var isUploadingWorkerContract: Bool = false

    // MARK: - Direct Edit ("Sửa trực tiếp") State
    @Published public var directEditWorkerTarget: WorkerItem?
    @Published public var directEditBaselineDetail: WorkerEntryDetail?
    @Published public var directEditLoadingDetail: Bool = false
    @Published public var directEditSavingProfile: Bool = false
    @Published public var directEditErrorMessage: String?
    @Published public var directEditFieldErrors: [String: String] = [:]
    @Published public var directEditProfileSavedMessage: String?
    @Published public var directEditFileStatuses: [String: DirectEditFileState] = [:]

    // MARK: - Salary Advance ("Ứng lương" for QLDA) State
    @Published public var salaryAdvanceContext: SalaryAdvanceActorContext?
    @Published public var salaryAdvanceSheetOpen: Bool = false
    @Published public var salaryAdvanceSelectedProjectId: String?
    @Published public var salaryAdvanceWorkerSearch: String = ""
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

    @Published public var salaryAdvanceHistoryOpen: Bool = false
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

    // MARK: - Attendance State
    @Published public var attendanceTimeEvaluation: AnchorEvaluationResult
    @Published public var attendanceOutboxItems: [AttendanceOutboxItem] = []
    @Published public var todayServerAttendanceCount: Int = 0
    @Published public var isSubmittingAttendance: Bool = false
    @Published public var isSyncingAttendance: Bool = false

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
    @Published public var leaveInboxError: String?

    // MARK: - Work Reports State
    @Published public var selectedWorkReportsSubTab: WorkReportsSubTab = .actionable
    @Published public var workReportObligations: [WorkReportObligationItem] = []
    @Published public var selectedObligationEditor: WorkReportObligationItem?
    @Published public var editorFieldValues: [String: String] = [:]
    @Published public var isLoadingWorkReports: Bool = false
    @Published public var isSavingWorkReport: Bool = false
    @Published public var workReportReviews: [WorkReportReviewItem] = []
    @Published public var selectedReviewDetail: WorkReportReviewItem?
    @Published public var workReportTemplates: [WorkReportTemplateOption] = []
    @Published public var workReportAnalytics: WorkReportsAnalyticsSnapshot = WorkReportsAnalyticsSnapshot()

    public init(
        repository: MiniBiRepository = .shared,
        directEntryService: DirectEntryService = .shared,
        workReportsService: WorkReportsAndReportingService = .shared,
        attendanceOutboxService: AttendanceOutboxService = .shared,
        timeAnchorManager: AttendanceTimeAnchorManager = AttendanceTimeAnchorManager(),
        notificationService: NotificationService = .shared,
        leaveService: LeaveService = .shared,
        salaryAdvanceService: SalaryAdvanceService = .shared
    ) {
        self.repository = repository
        self.directEntryService = directEntryService
        self.workReportsService = workReportsService
        self.attendanceOutboxService = attendanceOutboxService
        self.timeAnchorManager = timeAnchorManager
        self.notificationService = notificationService
        self.leaveService = leaveService
        self.salaryAdvanceService = salaryAdvanceService
        self.attendanceTimeEvaluation = timeAnchorManager.evaluateAnchor()

        let bundleEnv = AppEnvironmentConfig.current
        if let savedVariant = repository.getSelectedEnvironmentVariant() {
            let resolvedEnv = AppEnvironmentConfig.forVariant(
                savedVariant,
                marketingVersion: bundleEnv.marketingVersion,
                buildNumber: bundleEnv.buildNumber
            )
            self.environmentConfig = resolvedEnv
            self.repository.httpClient.setBaseUrl(resolvedEnv.defaultBaseUrl)
        } else {
            self.environmentConfig = bundleEnv
            self.repository.httpClient.setBaseUrl(bundleEnv.defaultBaseUrl)
        }

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
            }
        }
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
        currentDestination = destination
        if destination == .attendance {
            activeAttendanceTab = .checkIn
            if isAuthenticated {
                Task { await refreshMyLeaveSummary(year: leaveYear) }
            }
        } else if destination == .workers {
            if isAuthenticated {
                Task { await loadSalaryAdvanceContext() }
            }
        }
    }

    // MARK: - Session Bootstrap, Login & Logout

    public func bootstrapSession() async {
        savedAccounts = repository.getSavedLoginAccounts()
        guard repository.httpClient.cookieStore.hasSessionCookie() else {
            return
        }
        isBootstrapping = true
        let meResult = await repository.fetchCurrentUser()
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

        isLoggingIn = true
        loginErrorMessage = nil

        let result = await repository.login(identifier: trimmedId, password: password)
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

    private func onAuthenticatedSessionEstablished(user: MiniBiUser) {
        currentUser = user
        isAuthenticated = true
        currentDestination = .dashboard

        // Ensure at least 1 draft row is ready for "Cấp mới"
        if newWorkerDraftRows.isEmpty {
            newWorkerDraftRows = [NewWorkerEntryDraft()]
        }

        attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: user.username)
        attendanceOutboxService.startNetworkAutoFlush { [weak self] in
            self?.currentUser?.username
        }

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

        Task {
            await syncAttendanceTimeAnchor()
            await refreshNotifications()
            await loadDashboardData()
            await loadDirectEntryAssigneeOptions()
            await loadSalaryAdvanceContext()
            await loadWorkReportsData()
        }
    }

    public func performLogout() async {
        notificationService.stopSseStream()
        attendanceOutboxService.stopNetworkAutoFlush()
        salaryAdvanceSearchTask?.cancel()
        await repository.logout()

        isAuthenticated = false
        currentUser = nil
        currentDestination = .dashboard
        // Reset Cấp mới draft rows only on logout
        newWorkerDraftRows = [NewWorkerEntryDraft()]
        selectedObligationEditor = nil
        editorFieldValues = [:]
        selectedWorkerDetail = nil
        closeDirectEdit()
        salaryAdvanceContext = nil
        closeSalaryAdvanceSheet()
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
            currentDestination = .workReports
            await loadWorkReportsData()
            if let entityIdStr = item.targetEntityId, let obId = Int64(entityIdStr) {
                if let matched = workReportObligations.first(where: { $0.id == obId || $0.submissionId == obId }) {
                    await openObligationEditor(matched)
                }
            }
        } else if typeUpper.contains("CCCD") || routeLower.contains("cccd") {
            currentDestination = .dashboard
            selectedDashboardSubTab = .cccdReview
            await loadCccdReviewItems()
        } else if typeUpper.contains("LEAVE") || routeLower.contains("leave") {
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
            currentDestination = .attendance
            activeAttendanceTab = .checkIn
        } else if typeUpper.contains("WORKER") || routeLower.contains("worker") {
            currentDestination = .workers
            await loadWorkers(resetPage: true)
            if let maNld = item.targetEntityId, !maNld.isEmpty {
                let detailRes = await repository.fetchWorkerDetail(maNld: maNld)
                if case .success(let detail, _) = detailRes {
                    selectedWorkerDetail = detail
                }
            }
        } else if typeUpper.contains("DIRECT_ENTRY") || routeLower.contains("direct-entry") {
            currentDestination = .newEntry
            await loadDirectEntryBatchHistory()
        }
    }

    // MARK: - Dashboard & CCCD Review Actions

    public func applyDatePreset(_ preset: DateFilterPreset, reload: Bool = true) {
        selectedDatePreset = preset
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current
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
            Task { await loadDashboardData() }
        }
    }

    public func loadDashboardData() async {
        isLoadingDashboard = true
        defer { isLoadingDashboard = false }

        async let overviewTask = workReportsService.fetchGeneralOverview(
            scope: dashboardScope,
            startDate: filterStartDate,
            endDate: filterEndDate
        )
        async let deepTask = workReportsService.fetchWorkforceDeepMetrics(
            scope: dashboardScope,
            startDate: filterStartDate,
            endDate: filterEndDate
        )
        async let teamTask = workReportsService.fetchTeamReport(
            startDate: filterStartDate,
            endDate: filterEndDate
        )
        async let dupTask = workReportsService.fetchDuplicateCccdReport(search: duplicateSearchQuery)
        async let cccdTask = directEntryService.fetchCccdReviewList(
            status: cccdReviewFilterStatus,
            search: cccdReviewSearchQuery
        )

        if case .success(let ov, _) = await overviewTask { generalOverview = ov }
        if case .success(let dp, _) = await deepTask { workforceDeepMetrics = dp }
        if case .success(let tm, _) = await teamTask { teamReport = tm }
        if case .success(let dup, _) = await dupTask { duplicateCccdReport = dup }
        if case .success(let cccd, _) = await cccdTask {
            cccdReviewItems = cccd.items
            cccdReviewTotal = cccd.total
        }
    }

    public func loadCccdReviewItems() async {
        isLoadingCccdReview = true
        let res = await directEntryService.fetchCccdReviewList(
            status: cccdReviewFilterStatus,
            search: cccdReviewSearchQuery
        )
        isLoadingCccdReview = false
        if case .success(let data, _) = res {
            cccdReviewItems = data.items
            cccdReviewTotal = data.total
        }
    }

    public func resolveCccdReview(
        entryId: Int64,
        action: String,
        correctedCccd: String? = nil,
        note: String = ""
    ) async {
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
        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
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
        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
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

    public func loadDirectEntryAssigneeOptions() async {
        let res = await directEntryService.fetchAssigneeOptions()
        if case .success(let opts, _) = res {
            directEntryOptions = opts
            if let defaultLabor = opts.catalogLaborTypes.first(where: {
                $0.displayName.lowercased().contains("thời vụ") ||
                $0.laborTypeKey.uppercased() == "TEMPORARY" ||
                $0.laborTypeKey.uppercased().contains("SEASONAL")
            }) ?? opts.catalogLaborTypes.first {
                for idx in newWorkerDraftRows.indices {
                    if newWorkerDraftRows[idx].laborTypeKey.isEmpty || newWorkerDraftRows[idx].laborTypeKey == "SEASONAL" {
                        newWorkerDraftRows[idx].laborTypeKey = defaultLabor.laborTypeKey
                        if newWorkerDraftRows[idx].nganhNghe.isEmpty || newWorkerDraftRows[idx].nganhNghe == "Thời vụ" {
                            newWorkerDraftRows[idx].nganhNghe = defaultLabor.displayName
                        }
                    }
                }
            }
        }
    }

    public func loadDirectEntryBatchHistory() async {
        let res = await directEntryService.fetchBatchHistory()
        if case .success(let list, _) = res {
            directEntryHistoryBatches = list
        }
    }

    /// Validates, checks duplicate CCCDs, submits valid rows in `newWorkerDraftRows`, uploads their documents
    /// (automatically merging 2+ contract images into 1 PDF), and removes ONLY the rows that succeeded!
    public func submitNewWorkerDraftBatch(confirmedDuplicates: Bool = false) async {
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
        defer { isSubmittingNewWorkers = false }

        if !confirmedDuplicates {
            let checkRes = await directEntryService.checkDuplicates(drafts: validRows)
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

        switch submitRes {
        case .success(let outcome, _):
            // Upload staged documents (Portrait + CCCD front/back + multi-image merged PDF contract) for each created entry
            for row in validRows {
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
                        currentEntryVersion = up.nextEntryVersion
                    }
                    if let front = row.cccdFrontFile {
                        let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeCccdFront,
                            attachments: [front],
                            initialEntryVersion: currentEntryVersion
                        )
                        currentEntryVersion = up.nextEntryVersion
                    }
                    if let back = row.cccdBackFile {
                        let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeCccdBack,
                            attachments: [back],
                            initialEntryVersion: currentEntryVersion
                        )
                        currentEntryVersion = up.nextEntryVersion
                    }
                    if !row.contractFiles.isEmpty {
                        let up = await directEntryService.uploadEntryDocumentAttachmentsWithVersion(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeEmploymentContract,
                            attachments: row.contractFiles,
                            initialEntryVersion: currentEntryVersion
                        )
                        currentEntryVersion = up.nextEntryVersion
                    }
                }
            }

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

    public func loadWorkers(resetPage: Bool = false) async {
        if resetPage {
            workersPage = 1
        }
        isLoadingWorkers = true
        let res = await repository.fetchWorkers(
            scope: selectedWorkerScope,
            search: workerSearchQuery,
            project: workerProjectFilter,
            status: workerStatusFilter,
            page: workersPage
        )
        isLoadingWorkers = false
        switch res {
        case .success(let data, _):
            workers = data.items
            workersTotalCount = data.total
            workersTotalPages = data.totalPages
        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
        }
    }

    public func uploadWorkerContract(maNld: String, attachments: [LocalAttachmentItem]) async -> Bool {
        isUploadingWorkerContract = true
        let res = await repository.uploadWorkerContractAttachments(maNld: maNld, attachments: attachments)
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
        guard worker.canDirectEdit else {
            showBanner("Bạn không có quyền sửa trực tiếp hồ sơ này.", isError: true)
            return
        }
        directEditWorkerTarget = worker
        directEditBaselineDetail = nil
        directEditLoadingDetail = true
        directEditSavingProfile = false
        directEditErrorMessage = nil
        directEditFieldErrors = [:]
        directEditProfileSavedMessage = nil
        directEditFileStatuses = [:]

        if directEntryOptions.projects.isEmpty && directEntryOptions.catalogLaborTypes.isEmpty {
            await loadDirectEntryAssigneeOptions()
        }

        let targetEntryId = !worker.entryIdUuid.isEmpty ? worker.entryIdUuid : (worker.id != 0 ? String(worker.id) : worker.maNld)
        let detailRes = await directEntryService.fetchWorkerEntryDetail(entryIdUuid: targetEntryId)
        directEditLoadingDetail = false

        switch detailRes {
        case .success(let detail, _):
            directEditBaselineDetail = detail
            if !detail.canDirectEdit {
                directEditErrorMessage = "Tài khoản hiện tại không có quyền sửa trực tiếp hồ sơ này."
            }
        case .failure(let message, _, _, _):
            directEditErrorMessage = message
        }
    }

    public func closeDirectEdit() {
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
        guard let baseline = directEditBaselineDetail else { return }

        directEditErrorMessage = nil
        directEditFieldErrors = [:]

        let validationErrors = DirectEntryService.validateDirectEditFormFields(baseline: baseline, form: form)
        if !validationErrors.isEmpty {
            directEditFieldErrors = validationErrors
            let firstError = validationErrors.values.first ?? "Dữ liệu chỉnh sửa chưa hợp lệ."
            directEditErrorMessage = validationErrors.count == 1
                ? firstError
                : "Có \(validationErrors.count) trường chưa hợp lệ: \(firstError)"
            return
        }

        let patch = DirectEntryService.buildDirectEditPatch(baseline: baseline, form: form)
        let nonEmptyStaged = stagedFiles.filter { !$0.value.isEmpty }

        if patch.isEmpty && nonEmptyStaged.isEmpty {
            directEditErrorMessage = "Chưa có thay đổi thông tin hoặc tệp đính kèm nào để lưu."
            return
        }

        var currentVersion = baseline.entryVersion
        let targetEntryId = !baseline.entryIdUuid.isEmpty
            ? baseline.entryIdUuid
            : (baseline.id != 0 ? String(baseline.id) : baseline.maNld)

        // Step 1: Save profile fields via privileged-edit API first (if any field changed or if profile hasn't been saved yet)
        if !patch.isEmpty {
            directEditSavingProfile = true
            let editRes = await directEntryService.submitPrivilegedEdit(
                entryIdUuid: targetEntryId,
                expectedEntryVersion: currentVersion,
                patch: patch
            )
            directEditSavingProfile = false

            switch editRes {
            case .success(let newVersion, _):
                currentVersion = newVersion
                directEditProfileSavedMessage = "Đã lưu thông tin hồ sơ (v\(newVersion))."
                // Refresh baseline so subsequent retries don't re-submit already saved profile changes
                if case .success(let refreshed, _) = await directEntryService.fetchWorkerEntryDetail(entryIdUuid: targetEntryId) {
                    directEditBaselineDetail = refreshed
                    currentVersion = refreshed.entryVersion
                } else if var cur = directEditBaselineDetail {
                    cur.entryVersion = newVersion
                    directEditBaselineDetail = cur
                }

            case .failure(let message, let httpStatus, let errorCode, _):
                directEditErrorMessage = message
                let codeUpper = (errorCode ?? "").uppercased()
                if httpStatus == 409 && codeUpper == "OCC_CONFLICT" {
                    if case .success(let refreshed, _) = await directEntryService.fetchWorkerEntryDetail(entryIdUuid: targetEntryId) {
                        directEditBaselineDetail = refreshed
                    }
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
                guard let files = nonEmptyStaged[docType], !files.isEmpty else { continue }
                let displayName = files.count >= 2
                    ? "\(files.count) tệp ảnh (ghép PDF)"
                    : (files.first?.fileName ?? docType)
                directEditFileStatuses[docType] = DirectEditFileState(
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
                currentVersion = upRes.nextEntryVersion
                if var cur = directEditBaselineDetail {
                    cur.entryVersion = currentVersion
                    directEditBaselineDetail = cur
                }
                switch upRes.result {
                case .success:
                    directEditFileStatuses[docType] = DirectEditFileState(
                        docType: docType,
                        fileName: displayName,
                        status: .success,
                        message: "Đã tải tệp thành công."
                    )
                case .failure(let errMsg, _, _, _):
                    anyFileFailed = true
                    directEditFileStatuses[docType] = DirectEditFileState(
                        docType: docType,
                        fileName: displayName,
                        status: .error,
                        message: errMsg
                    )
                }
            }

            if case .success(let refreshed, _) = await directEntryService.fetchWorkerEntryDetail(entryIdUuid: targetEntryId) {
                directEditBaselineDetail = refreshed
            }
        }

        await loadWorkers(resetPage: false)

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
        guard let baseline = directEditBaselineDetail, !attachments.isEmpty else { return }
        let targetEntryId = !baseline.entryIdUuid.isEmpty
            ? baseline.entryIdUuid
            : (baseline.id != 0 ? String(baseline.id) : baseline.maNld)
        let displayName = attachments.count >= 2
            ? "\(attachments.count) tệp ảnh (ghép PDF)"
            : (attachments.first?.fileName ?? docType)

        directEditFileStatuses[docType] = DirectEditFileState(
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

        if case .success(let refreshed, _) = await directEntryService.fetchWorkerEntryDetail(entryIdUuid: targetEntryId) {
            directEditBaselineDetail = refreshed
        } else if var cur = directEditBaselineDetail {
            cur.entryVersion = upRes.nextEntryVersion
            directEditBaselineDetail = cur
        }

        switch upRes.result {
        case .success:
            directEditFileStatuses[docType] = DirectEditFileState(
                docType: docType,
                fileName: displayName,
                status: .success,
                message: "Đã tải tệp thành công."
            )
            if !directEditFileStatuses.values.contains(where: { $0.status == .error }) {
                directEditErrorMessage = nil
            }
            await loadWorkers(resetPage: false)
            onComplete?()
        case .failure(let errMsg, _, _, _):
            directEditFileStatuses[docType] = DirectEditFileState(
                docType: docType,
                fileName: displayName,
                status: .error,
                message: errMsg
            )
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

    public func loadSalaryAdvanceContext() async {
        let res = await salaryAdvanceService.fetchContext()
        switch res {
        case .success(let ctx, _):
            let activeProjects = ctx.activeManagedProjects
            salaryAdvanceContext = SalaryAdvanceActorContext(
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
            let currentProjectId = salaryAdvanceSelectedProjectId
            let resolvedProjectId: String?
            if let currentProjectId, activeProjects.contains(where: { $0.projectId == currentProjectId }) {
                resolvedProjectId = currentProjectId
            } else {
                resolvedProjectId = activeProjects.first?.projectId
            }
            salaryAdvanceSelectedProjectId = resolvedProjectId
        case .failure:
            break
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
        salaryAdvanceSelectedProjectId = resolvedProjectId
        salaryAdvanceWorkerSearch = ""
        salaryAdvanceWorkers = []
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

        if let resolvedProjectId {
            await loadSalaryAdvanceProjectWorkers(projectId: resolvedProjectId, search: "", cursor: nil, append: false)
        }
    }

    public func closeSalaryAdvanceSheet() {
        salaryAdvanceSearchTask?.cancel()
        salaryAdvanceSheetOpen = false
        salaryAdvanceCreateSubmitting = false
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
        salaryAdvanceHistoryOpen = false
    }

    public func selectSalaryAdvanceProject(_ projectId: String) async {
        let trimmed = projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        salaryAdvanceSelectedProjectId = trimmed
        salaryAdvanceSelectedWorkerId = nil
        salaryAdvanceWorkers = []
        salaryAdvanceWorkersError = nil
        salaryAdvanceWorkersNextCursor = nil
        salaryAdvanceWorkersHasMore = false
        salaryAdvanceCreateError = nil
        salaryAdvancePendingIdempotencyKey = nil
        salaryAdvancePendingSignature = nil
        await loadSalaryAdvanceProjectWorkers(
            projectId: trimmed,
            search: salaryAdvanceWorkerSearch,
            cursor: nil,
            append: false
        )
    }

    public func updateSalaryAdvanceWorkerSearch(_ query: String) {
        salaryAdvanceWorkerSearch = query
        salaryAdvanceSearchTask?.cancel()
        guard let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else { return }
        salaryAdvanceSearchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            await self?.loadSalaryAdvanceProjectWorkers(
                projectId: projectId,
                search: query,
                cursor: nil,
                append: false
            )
        }
    }

    public func loadMoreSalaryAdvanceWorkers() async {
        guard !salaryAdvanceWorkersLoading,
              !salaryAdvanceWorkersLoadingMore,
              salaryAdvanceWorkersHasMore,
              let cursor = salaryAdvanceWorkersNextCursor, !cursor.isEmpty,
              let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else {
            return
        }
        await loadSalaryAdvanceProjectWorkers(
            projectId: projectId,
            search: salaryAdvanceWorkerSearch,
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
        if append {
            salaryAdvanceWorkersLoadingMore = true
        } else {
            salaryAdvanceWorkersLoading = true
            salaryAdvanceWorkersError = nil
        }

        let res = await salaryAdvanceService.listProjectWorkers(
            projectId: projectId,
            search: search,
            cursor: cursor,
            pageSize: 30
        )

        if append {
            salaryAdvanceWorkersLoadingMore = false
        } else {
            salaryAdvanceWorkersLoading = false
        }

        guard salaryAdvanceSelectedProjectId == projectId else { return }

        switch res {
        case .success(let page, _):
            let combined = append ? (salaryAdvanceWorkers + page.workers) : page.workers
            let filtered = SalaryAdvanceContract.filterSelectableProjectWorkers(combined)
            let resolvedSelectedId: String?
            if let cur = salaryAdvanceSelectedWorkerId, filtered.contains(where: { $0.workerEntryId == cur }) {
                resolvedSelectedId = cur
            } else {
                resolvedSelectedId = nil
            }
            salaryAdvanceWorkers = filtered
            salaryAdvanceWorkersError = nil
            salaryAdvanceWorkersNextCursor = page.nextCursor
            salaryAdvanceWorkersHasMore = page.hasMore
            salaryAdvanceSelectedWorkerId = resolvedSelectedId

        case .failure(let message, _, _, _):
            salaryAdvanceWorkersError = message
        }
    }

    public func selectSalaryAdvanceWorker(_ workerEntryId: String) {
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
            closeSalaryAdvanceSheet()
            let codeSuffix = (reqId.isEmpty || reqId == "created") ? "" : " (\(reqId))"
            showBanner("Đã gửi đề nghị ứng lương thành công\(codeSuffix).")
        case .failure(let message, _, _, _):
            salaryAdvanceCreateError = message
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

    public func closeSalaryAdvanceWorkerHistory() {
        salaryAdvanceHistoryOpen = false
        salaryAdvanceHistoryLoading = false
        salaryAdvanceHistoryLoadingMore = false
        salaryAdvanceHistoryError = nil
    }

    public func selectSalaryAdvanceHistoryPeriodMode(_ periodMode: String, month: String? = nil) async {
        guard let worker = selectedSalaryAdvanceWorker,
              let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else { return }
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
            projectId: projectId,
            workerEntryId: worker.workerEntryId,
            periodMode: normalizedMode,
            month: resolvedMonth,
            fromDate: salaryAdvanceHistoryFromDate,
            toDate: salaryAdvanceHistoryToDate,
            cursor: nil,
            append: false
        )
    }

    public func applySalaryAdvanceHistoryCustomRange(fromDate: String, toDate: String) async {
        guard let worker = selectedSalaryAdvanceWorker,
              let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else { return }
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
            projectId: projectId,
            workerEntryId: worker.workerEntryId,
            periodMode: SalaryAdvanceContract.periodModeCustomRange,
            month: salaryAdvanceHistoryMonth,
            fromDate: f,
            toDate: t,
            cursor: nil,
            append: false
        )
    }

    public func loadMoreSalaryAdvanceWorkerHistory() async {
        guard !salaryAdvanceHistoryLoading,
              !salaryAdvanceHistoryLoadingMore,
              salaryAdvanceHistoryHasMore,
              let cursor = salaryAdvanceHistoryNextCursor, !cursor.isEmpty,
              let worker = selectedSalaryAdvanceWorker,
              let projectId = salaryAdvanceSelectedProjectId, !projectId.isEmpty else {
            return
        }
        await fetchSalaryAdvanceWorkerHistoryInternal(
            projectId: projectId,
            workerEntryId: worker.workerEntryId,
            periodMode: salaryAdvanceHistoryPeriodMode,
            month: salaryAdvanceHistoryMonth,
            fromDate: salaryAdvanceHistoryFromDate,
            toDate: salaryAdvanceHistoryToDate,
            cursor: cursor,
            append: true
        )
    }

    private func fetchSalaryAdvanceWorkerHistoryInternal(
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

        let res = await salaryAdvanceService.fetchWorkerHistoryForPm(
            projectId: projectId,
            workerEntryId: workerEntryId,
            periodMode: periodMode,
            month: periodMode == SalaryAdvanceContract.periodModeMonth ? month : nil,
            fromDate: periodMode == SalaryAdvanceContract.periodModeCustomRange ? fromDate : nil,
            toDate: periodMode == SalaryAdvanceContract.periodModeCustomRange ? toDate : nil,
            cursor: cursor,
            pageSize: 20
        )

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
            salaryAdvanceHistoryError = message
        }
    }

    // MARK: - Attendance Actions

    public func syncAttendanceTimeAnchor() async {
        _ = await timeAnchorManager.syncTimeAnchor()
        attendanceTimeEvaluation = timeAnchorManager.evaluateAnchor()
        if let username = currentUser?.username, !username.isEmpty {
            let histRes = await attendanceOutboxService.syncServerHistoryForUser(userKey: username)
            if case .success(let data, _) = histRes {
                attendanceOutboxItems = data.items
                todayServerAttendanceCount = data.todayCount
            } else {
                attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: username)
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
        isSubmittingAttendance = false
        attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: userKey)

        switch apiResult {
        case .success:
            todayServerAttendanceCount = max(
                todayServerAttendanceCount,
                attendanceOutboxService.todaySyncedCountForUser(userKey: userKey)
            )
            showBanner("Chấm công thành công! Đã đồng bộ lên máy chủ.")
        case .failure(let message, _, _, _):
            if item.status == AttendanceContract.statusFailedPermanent {
                showBanner(message, isError: true)
            } else {
                showBanner("Đã lưu chấm công vào hàng đợi ngoại tuyến (\(message)). Sẽ tự động gửi bù khi có mạng.", isError: false)
            }
        }
    }

    public func flushAttendanceOutboxNow() async {
        guard let userKey = currentUser?.username, !userKey.isEmpty else { return }
        isSyncingAttendance = true
        let syncedCount = await attendanceOutboxService.flushRetryableOutbox(userKey: userKey)
        let histRes = await attendanceOutboxService.syncServerHistoryForUser(userKey: userKey)
        isSyncingAttendance = false
        if case .success(let data, _) = histRes {
            attendanceOutboxItems = data.items
            todayServerAttendanceCount = data.todayCount
        } else {
            attendanceOutboxItems = attendanceOutboxService.listItemsForUser(userKey: userKey)
        }
        showBanner(syncedCount > 0 ? "Đã gửi bù thành công \(syncedCount) bản ghi chấm công." : "Đã đồng bộ trạng thái chấm công.")
    }

    // MARK: - Leave Request ("Xin nghỉ") & Leave Approval ("Duyệt nghỉ") Actions

    public static let leaveInboxPageSize: Int = 50

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
            errorCode: myLeaveErrorCode
        )
    }

    public func selectAttendanceTab(_ tab: AttendanceAreaTab) {
        let visibleTabs = resolveVisibleAttendanceTabs()
        let safeTab = visibleTabs.contains(tab) ? tab : .checkIn
        activeAttendanceTab = safeTab
        leaveMutationError = nil

        switch safeTab {
        case .checkIn:
            break
        case .leaveRequest:
            ensureInitialLeaveDraftDay()
            if myLeaveSummary == nil && !isLoadingMyLeave {
                Task { await refreshMyLeaveSummary(year: leaveYear) }
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

    public func refreshMyLeaveSummary(year: Int? = nil) async {
        let targetYear = year ?? leaveYear
        isLoadingMyLeave = true
        myLeaveError = nil
        myLeaveErrorCode = nil
        let result = await leaveService.fetchMyLeaveSummary(leaveYear: targetYear)
        isLoadingMyLeave = false

        switch result {
        case .success(let summary, _):
            myLeaveSummary = summary
            leaveYear = summary.leaveYear
            myLeaveError = nil
            myLeaveErrorCode = nil
            ensureInitialLeaveDraftDay()

            let visibleTabs = LeaveContract.resolveVisibleAttendanceTabs(
                authority: summary.authority,
                errorCode: nil
            )
            if !visibleTabs.contains(activeAttendanceTab) {
                activeAttendanceTab = .checkIn
            }
            if summary.authority.canApproveRequests &&
                !summary.authority.isSharedNonEmployee &&
                (activeAttendanceTab == .leaveApproval || leaveApprovalInbox != nil) {
                await refreshLeaveApprovalInbox(
                    statusFilter: leaveInboxStatusFilter,
                    page: leaveInboxPage
                )
            }

        case .failure(let message, _, let errorCode, _):
            myLeaveError = message
            myLeaveErrorCode = errorCode
            if errorCode?.uppercased() == "LEAVE_SHARED_ACCOUNT_FORBIDDEN" {
                myLeaveSummary = nil
                activeAttendanceTab = .checkIn
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

        isLoadingLeaveInbox = true
        leaveInboxError = nil
        let result = await leaveService.fetchApprovalInbox(
            statusFilter: effectiveFilter,
            limit: limit,
            offset: offset
        )
        isLoadingLeaveInbox = false

        switch result {
        case .success(let inbox, _):
            leaveApprovalInbox = inbox
            leaveInboxError = nil

        case .failure(let message, let httpStatus, let errorCode, _):
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

    // MARK: - Work Reports Actions (Including `is_overdue` & `WORK_REPORT_DEADLINE_PASSED` Contract)

    public func loadWorkReportsData() async {
        isLoadingWorkReports = true
        defer { isLoadingWorkReports = false }

        await loadWorkReportObligations()

        async let reviewsTask = workReportsService.fetchPendingReviews()
        async let templatesTask = workReportsService.fetchSelectableTemplates()
        async let analyticsTask = workReportsService.fetchAnalyticsSnapshot()

        if case .success(let revs, _) = await reviewsTask { workReportReviews = revs }
        if case .success(let tpls, _) = await templatesTask { workReportTemplates = tpls }
        if case .success(let ana, _) = await analyticsTask { workReportAnalytics = ana }
    }

    public func loadWorkReportObligations() async {
        let res = await workReportsService.fetchMyObligations()
        if case .success(let list, _) = res {
            workReportObligations = list
            // Keep open editor synchronized if present
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
        selectedObligationEditor = obligation
        editorFieldValues = Self.seedDefaultFieldValues(base: obligation.formPayload, sections: obligation.sections)

        // Fetch full detail to ensure latest schema_json, payload_json, submission_version, and `is_overdue`
        let detailRes = await workReportsService.fetchObligationDetail(
            obligationIdUuid: obligation.obligationIdUuid,
            fallback: obligation
        )
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
        guard let editor = selectedObligationEditor else { return }
        guard WorkReportsContract.isEditableObligation(editor) else {
            showBanner(WorkReportsContract.deadlinePassedMessageVi, isError: true)
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
        isSavingWorkReport = false

        await handleWorkReportMutationResult(result, currentEditor: editor, isFinalSubmit: false)
    }

    public func submitWorkReportFinal() async {
        guard let editor = selectedObligationEditor else { return }
        guard WorkReportsContract.isEditableObligation(editor) else {
            showBanner(WorkReportsContract.deadlinePassedMessageVi, isError: true)
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
        isSavingWorkReport = false

        await handleWorkReportMutationResult(result, currentEditor: editor, isFinalSubmit: true)
    }

    private func handleWorkReportMutationResult(
        _ result: MiniBiApiResult<WorkReportObligationItem>,
        currentEditor: WorkReportObligationItem,
        isFinalSubmit: Bool
    ) async {
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
                // Version conflict (NOT deadline passed): reload latest version without locking as overdue
                showBanner(message, isError: true)
                let detailRes = await workReportsService.fetchObligationDetail(
                    obligationIdUuid: currentEditor.obligationIdUuid,
                    fallback: currentEditor
                )
                if case .success(let refreshed, _) = detailRes {
                    selectedObligationEditor = refreshed
                    editorFieldValues = Self.seedDefaultFieldValues(base: refreshed.formPayload, sections: refreshed.sections)
                    if let idx = workReportObligations.firstIndex(where: {
                        (!refreshed.obligationIdUuid.isEmpty && $0.obligationIdUuid == refreshed.obligationIdUuid)
                        || ($0.id != 0 && $0.id == refreshed.id)
                    }) {
                        workReportObligations[idx] = refreshed
                    }
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
        let targetObligationId = review.obligationIdUuid.isEmpty ? review.submissionIdUuid : review.obligationIdUuid
        let res = await workReportsService.decideReview(
            submissionIdUuid: targetObligationId,
            decision: decision,
            comment: comment,
            expectedVersion: review.version
        )
        switch res {
        case .success:
            selectedReviewDetail = nil
            showBanner(decision == WorkReportsContract.decisionApprove ? "Đã phê duyệt báo cáo." : "Đã từ chối báo cáo.")
            await loadWorkReportsData()
        case .failure(let message, _, _, _):
            showBanner(message, isError: true)
        }
    }
}
