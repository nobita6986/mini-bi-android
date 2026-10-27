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

    // MARK: - Notifications (SSE + List)
    @Published public var notifications: [AppNotificationItem] = []
    @Published public var unreadNotificationCount: Int = 0
    @Published public var isNotificationSheetPresented: Bool = false

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
        leaveService: LeaveService = .shared
    ) {
        self.repository = repository
        self.directEntryService = directEntryService
        self.workReportsService = workReportsService
        self.attendanceOutboxService = attendanceOutboxService
        self.timeAnchorManager = timeAnchorManager
        self.notificationService = notificationService
        self.leaveService = leaveService
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
        bannerMessage = trimmed
        bannerIsError = isError
    }

    public func dismissBanner() {
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
                    if !self.notifications.contains(where: { $0.id == incomingItem.id }) {
                        self.notifications.insert(incomingItem, at: 0)
                    }
                    self.unreadNotificationCount = self.notifications.filter { !$0.isRead }.count
                    self.showBanner("Thông báo mới: \(incomingItem.title)", isError: false)
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
            await loadWorkReportsData()
        }
    }

    public func performLogout() async {
        notificationService.stopSseStream()
        attendanceOutboxService.stopNetworkAutoFlush()
        await repository.logout()

        isAuthenticated = false
        currentUser = nil
        currentDestination = .dashboard
        // Reset Cấp mới draft rows only on logout
        newWorkerDraftRows = [NewWorkerEntryDraft()]
        selectedObligationEditor = nil
        editorFieldValues = [:]
        notifications = []
        unreadNotificationCount = 0
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

    public func removeContractAttachmentFromDraftRow(rowId: String, attachmentId: String) {
        updateNewWorkerDraftRow(rowId: rowId) { draft in
            draft.contractFiles.removeAll(where: { $0.id == attachmentId })
        }
    }

    public func loadDirectEntryAssigneeOptions() async {
        let res = await directEntryService.fetchAssigneeOptions()
        if case .success(let opts, _) = res {
            directEntryOptions = opts
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
                    if let portrait = row.portraitFile {
                        _ = await directEntryService.uploadEntryDocumentAttachments(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeWorkerPortrait,
                            attachments: [portrait]
                        )
                    }
                    if let front = row.cccdFrontFile {
                        _ = await directEntryService.uploadEntryDocumentAttachments(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeCccdFront,
                            attachments: [front]
                        )
                    }
                    if let back = row.cccdBackFile {
                        _ = await directEntryService.uploadEntryDocumentAttachments(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeCccdBack,
                            attachments: [back]
                        )
                    }
                    if !row.contractFiles.isEmpty {
                        _ = await directEntryService.uploadEntryDocumentAttachments(
                            entryIdUuid: entryIdUuid,
                            docType: DirectEntryContract.docTypeEmploymentContract,
                            attachments: row.contractFiles
                        )
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
        let (_, apiResult) = await attendanceOutboxService.submitCheckIn(
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
            todayServerAttendanceCount += 1
            showBanner("Chấm công thành công! Đã đồng bộ lên máy chủ.")
        case .failure(let message, _, _, _):
            showBanner("Đã lưu chấm công vào hàng đợi ngoại tuyến (\(message)). Sẽ tự động gửi bù khi có mạng.", isError: false)
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
        if leaveDraftDays.isEmpty {
            let asOf = myLeaveSummary?.asOfDate ?? ""
            let defaultDate = LeaveContract.isValidIsoDate(asOf)
                ? asOf
                : LeaveContract.todayVietnamIsoDate()
            leaveDraftDays = [
                LeaveDraftDayInput(leaveDate: defaultDate, sessionCode: "FULL")
            ]
        }
    }

    public func addLeaveDraftDay() {
        if leaveDraftDays.count >= 60 {
            leaveMutationError = "Mỗi đơn chỉ được đăng ký tối đa 60 ngày nghỉ."
            return
        }
        leaveMutationError = nil
        let fallbackDate = myLeaveSummary?.asOfDate ?? LeaveContract.todayVietnamIsoDate()
        if let lastDate = leaveDraftDays.last?.leaveDate {
            let nextDate = LeaveContract.nextIsoDateAfter(lastDate, fallbackDate: fallbackDate)
            leaveDraftDays.append(LeaveDraftDayInput(leaveDate: nextDate, sessionCode: "FULL"))
        } else {
            let startDate = LeaveContract.isValidIsoDate(fallbackDate) ? fallbackDate : LeaveContract.todayVietnamIsoDate()
            leaveDraftDays.append(LeaveDraftDayInput(leaveDate: startDate, sessionCode: "FULL"))
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

        if let validationError = LeaveContract.validateCreateDraftInput(
            days: daysSnapshot,
            reason: reasonSnapshot
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
               let refreshed = list.first(where: { $0.id == currentEditor.id }) {
                var merged = refreshed
                if merged.sections.isEmpty {
                    merged.sections = currentEditor.sections
                }
                selectedObligationEditor = merged
            }
        }
    }

    public func openObligationEditor(_ obligation: WorkReportObligationItem) async {
        selectedObligationEditor = obligation
        editorFieldValues = obligation.formPayload

        // Fetch full detail to ensure latest schema_json, payload_json, submission_version, and `is_overdue`
        let detailRes = await workReportsService.fetchObligationDetail(obligationIdUuid: obligation.obligationIdUuid)
        if case .success(let detail, _) = detailRes {
            selectedObligationEditor = detail
            editorFieldValues = detail.formPayload
            if let idx = workReportObligations.firstIndex(where: { $0.id == detail.id || $0.obligationIdUuid == detail.obligationIdUuid }) {
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
            sections: editor.sections
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

        // Validate required fields
        for section in editor.sections {
            for field in section.fields where field.required {
                let val = (editorFieldValues[field.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                if val.isEmpty {
                    showBanner("Vui lòng nhập trường bắt buộc: \(field.label)", isError: true)
                    return
                }
            }
        }

        isSavingWorkReport = true
        let result = await workReportsService.submitReport(
            obligationIdUuid: editor.obligationIdUuid,
            expectedVersion: editor.submissionVersion,
            formPayload: editorFieldValues,
            sections: editor.sections
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
            if merged.id == 0 { merged = currentEditor }
            if merged.sections.isEmpty { merged.sections = currentEditor.sections }
            if merged.formPayload.isEmpty { merged.formPayload = editorFieldValues }
            if isFinalSubmit {
                merged.submissionState = WorkReportsContract.stateSubmitted
            } else if merged.submissionState == WorkReportsContract.stateNotStarted {
                merged.submissionState = WorkReportsContract.stateDraft
            }
            selectedObligationEditor = merged
            editorFieldValues = merged.formPayload
            if let idx = workReportObligations.firstIndex(where: { $0.id == currentEditor.id || $0.obligationIdUuid == currentEditor.obligationIdUuid }) {
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
                if let idx = workReportObligations.firstIndex(where: { $0.id == currentEditor.id || $0.obligationIdUuid == currentEditor.obligationIdUuid }) {
                    workReportObligations[idx].isOverdue = true
                }
                await loadWorkReportObligations()
            } else if httpStatus == 409 {
                // Version conflict (NOT deadline passed): reload latest version without locking as overdue
                showBanner(message, isError: true)
                let detailRes = await workReportsService.fetchObligationDetail(obligationIdUuid: currentEditor.obligationIdUuid)
                if case .success(let refreshed, _) = detailRes {
                    selectedObligationEditor = refreshed
                    editorFieldValues = refreshed.formPayload
                    if let idx = workReportObligations.firstIndex(where: { $0.id == refreshed.id || $0.obligationIdUuid == refreshed.obligationIdUuid }) {
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
        let res = await workReportsService.decideReview(
            submissionIdUuid: review.submissionIdUuid,
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
