import AudioToolbox
import Foundation
import UserNotifications

public enum TaskReminderRepeatMode: String, Codable, CaseIterable, Equatable {
    case daily = "DAILY"
    case once = "ONCE"

    public var labelVi: String {
        switch self {
        case .daily:
            return "Hàng ngày"
        case .once:
            return "1 lần"
        }
    }
}

public struct TaskReminderSystemSound: Identifiable, Codable, Equatable {
    public let id: String
    public let title: String
    public let systemSoundId: UInt32

    public init(id: String, title: String, systemSoundId: UInt32) {
        self.id = id
        self.title = title
        self.systemSoundId = systemSoundId
    }

    public static let defaultSound = TaskReminderSystemSound(
        id: "default",
        title: "Mặc định (Hệ thống)",
        systemSoundId: 1007
    )

    public static let allSystemSounds: [TaskReminderSystemSound] = [
        defaultSound,
        TaskReminderSystemSound(id: "alarm", title: "Chuông báo mặc định", systemSoundId: 1005),
        TaskReminderSystemSound(id: "chime", title: "Chime (Thanh âm)", systemSoundId: 1008),
        TaskReminderSystemSound(id: "glass", title: "Glass (Pha lê)", systemSoundId: 1009),
        TaskReminderSystemSound(id: "horn", title: "Horn (Kèn báo)", systemSoundId: 1010),
        TaskReminderSystemSound(id: "bell", title: "Bell (Chuông ngân)", systemSoundId: 1013),
        TaskReminderSystemSound(id: "electronic", title: "Electronic (Điện tử)", systemSoundId: 1014),
        TaskReminderSystemSound(id: "calypso", title: "Calypso", systemSoundId: 1022),
        TaskReminderSystemSound(id: "fanfare", title: "Fanfare", systemSoundId: 1025)
    ]

    public static func resolve(byId rawId: String?) -> TaskReminderSystemSound {
        let cleaned = (rawId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return allSystemSounds.first(where: { $0.id == cleaned }) ?? defaultSound
    }
}

public struct TaskReminderItem: Identifiable, Codable, Equatable {
    public var id: String
    public var title: String
    public var hour: Int
    public var minute: Int
    public var repeatMode: TaskReminderRepeatMode
    public var isEnabled: Bool
    public var soundId: String
    public var soundTitle: String
    public var nextTriggerEpochMs: Int64

    public init(
        id: String = UUID().uuidString.lowercased(),
        title: String = TaskReminderManager.defaultTitle,
        hour: Int = 8,
        minute: Int = 0,
        repeatMode: TaskReminderRepeatMode = .daily,
        isEnabled: Bool = true,
        soundId: String = TaskReminderSystemSound.defaultSound.id,
        soundTitle: String = TaskReminderSystemSound.defaultSound.title,
        nextTriggerEpochMs: Int64 = 0
    ) {
        self.id = id
        self.title = title
        self.hour = min(23, max(0, hour))
        self.minute = min(59, max(0, minute))
        self.repeatMode = repeatMode
        self.isEnabled = isEnabled
        self.soundId = soundId
        self.soundTitle = soundTitle
        self.nextTriggerEpochMs = nextTriggerEpochMs
    }

    public var formattedTime: String {
        String(format: "%02d:%02d", min(23, max(0, hour)), min(59, max(0, minute)))
    }
}

public final class TaskReminderManager: ObservableObject {
    public static let shared = TaskReminderManager()

    public static let defaultStorageKey = "hrp_bi_task_reminders_v1"
    public static let defaultTitle = "Nhắc việc"
    public static let maxTitleLength = 120
    public static let notificationIdentifierPrefix = "hrp_task_reminder_"

    private let userDefaults: UserDefaults
    private let storageKey: String
    private let scheduleSystemNotifications: Bool
    private let playSystemAudioPreview: Bool
    private let calendar: Calendar
    public var nowProvider: () -> Date

    @Published public private(set) var reminders: [TaskReminderItem] = []
    public var onRemindersChanged: (([TaskReminderItem]) -> Void)?

    public init(
        userDefaults: UserDefaults = .standard,
        storageKey: String = TaskReminderManager.defaultStorageKey,
        scheduleSystemNotifications: Bool = true,
        playSystemAudioPreview: Bool = true,
        calendar: Calendar = .current,
        nowProvider: @escaping () -> Date = { Date() }
    ) {
        self.userDefaults = userDefaults
        self.storageKey = storageKey
        self.scheduleSystemNotifications = scheduleSystemNotifications
        self.playSystemAudioPreview = playSystemAudioPreview
        self.calendar = calendar
        self.nowProvider = nowProvider
        _ = reconcileExpiredOnceReminders()
    }

    public var enabledRemindersCount: Int {
        reminders.filter { $0.isEnabled }.count
    }

    public static func normalizeTitle(_ rawTitle: String?) -> String {
        let trimmed = (rawTitle ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return defaultTitle
        }
        return String(trimmed.prefix(maxTitleLength))
    }

    public static func computeNextTriggerDate(
        hour: Int,
        minute: Int,
        now: Date,
        calendar: Calendar = .current
    ) -> Date {
        let safeHour = min(23, max(0, hour))
        let safeMinute = min(59, max(0, minute))
        var components = calendar.dateComponents([.year, .month, .day], from: now)
        components.hour = safeHour
        components.minute = safeMinute
        components.second = 0
        components.nanosecond = 0

        let candidateToday = calendar.date(from: components) ?? now
        if candidateToday > now {
            return candidateToday
        }
        return calendar.date(byAdding: .day, value: 1, to: candidateToday) ?? candidateToday.addingTimeInterval(86_400)
    }

    public static func computeNextTriggerEpochMs(
        hour: Int,
        minute: Int,
        now: Date,
        calendar: Calendar = .current
    ) -> Int64 {
        let date = computeNextTriggerDate(hour: hour, minute: minute, now: now, calendar: calendar)
        return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
    }

    @discardableResult
    public func reconcileExpiredOnceReminders(now: Date? = nil) -> [TaskReminderItem] {
        let effectiveNow = now ?? nowProvider()
        let nowEpochMs = Int64((effectiveNow.timeIntervalSince1970 * 1000.0).rounded())
        let loaded = loadFromDefaults()
        var mutated = false
        var expiredIds: [String] = []

        let updated: [TaskReminderItem] = loaded.map { item in
            if item.repeatMode == .once &&
                item.isEnabled &&
                item.nextTriggerEpochMs > 0 &&
                item.nextTriggerEpochMs <= nowEpochMs {
                mutated = true
                expiredIds.append(item.id)
                var copy = item
                copy.isEnabled = false
                return copy
            }
            return item
        }

        let sorted = Self.sortReminders(updated)
        if mutated {
            saveToDefaults(sorted)
            if scheduleSystemNotifications {
                for id in expiredIds {
                    cancelSystemNotification(forId: id)
                }
            }
        }
        publishReminders(sorted)
        return sorted
    }

    @discardableResult
    public func saveReminder(
        id: String? = nil,
        title: String,
        hour: Int,
        minute: Int,
        repeatMode: TaskReminderRepeatMode,
        soundId: String = TaskReminderSystemSound.defaultSound.id,
        isEnabled: Bool = true
    ) -> TaskReminderItem {
        let now = nowProvider()
        let normalizedTitle = Self.normalizeTitle(title)
        let safeHour = min(23, max(0, hour))
        let safeMinute = min(59, max(0, minute))
        let resolvedSound = TaskReminderSystemSound.resolve(byId: soundId)
        let targetId: String = {
            let trimmed = (id ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? UUID().uuidString.lowercased() : trimmed
        }()
        let nextTriggerMs = Self.computeNextTriggerEpochMs(
            hour: safeHour,
            minute: safeMinute,
            now: now,
            calendar: calendar
        )

        let item = TaskReminderItem(
            id: targetId,
            title: normalizedTitle,
            hour: safeHour,
            minute: safeMinute,
            repeatMode: repeatMode,
            isEnabled: isEnabled,
            soundId: resolvedSound.id,
            soundTitle: resolvedSound.title,
            nextTriggerEpochMs: nextTriggerMs
        )

        var current = reconcileExpiredOnceReminders(now: now)
        if let idx = current.firstIndex(where: { $0.id == targetId }) {
            current[idx] = item
        } else {
            current.append(item)
        }
        let sorted = Self.sortReminders(current)
        saveToDefaults(sorted)
        publishReminders(sorted)

        if scheduleSystemNotifications {
            if item.isEnabled {
                requestAuthorizationAndSchedule(item: item, now: now)
            } else {
                cancelSystemNotification(forId: item.id)
            }
        }
        return item
    }

    @discardableResult
    public func setReminderEnabled(id: String, isEnabled: Bool) -> TaskReminderItem? {
        let now = nowProvider()
        var current = reconcileExpiredOnceReminders(now: now)
        guard let idx = current.firstIndex(where: { $0.id == id }) else {
            return nil
        }
        var target = current[idx]
        target.isEnabled = isEnabled
        if isEnabled {
            target.nextTriggerEpochMs = Self.computeNextTriggerEpochMs(
                hour: target.hour,
                minute: target.minute,
                now: now,
                calendar: calendar
            )
        }
        current[idx] = target
        let sorted = Self.sortReminders(current)
        saveToDefaults(sorted)
        publishReminders(sorted)

        if scheduleSystemNotifications {
            if target.isEnabled {
                requestAuthorizationAndSchedule(item: target, now: now)
            } else {
                cancelSystemNotification(forId: target.id)
            }
        }
        return target
    }

    public func deleteReminder(id: String) {
        let current = loadFromDefaults().filter { $0.id != id }
        let sorted = Self.sortReminders(current)
        saveToDefaults(sorted)
        publishReminders(sorted)
        if scheduleSystemNotifications {
            cancelSystemNotification(forId: id)
        }
    }

    @discardableResult
    public func onNotificationTriggered(id: String, now: Date? = nil) -> TaskReminderItem? {
        let effectiveNow = now ?? nowProvider()
        var current = loadFromDefaults()
        guard let idx = current.firstIndex(where: { $0.id == id }) else {
            return nil
        }
        let triggered = current[idx]
        guard triggered.isEnabled else {
            return nil
        }
        var updated = triggered
        switch triggered.repeatMode {
        case .once:
            updated.isEnabled = false
            if scheduleSystemNotifications {
                cancelSystemNotification(forId: id)
            }
        case .daily:
            let nextRef = Date(
                timeIntervalSince1970: max(
                    effectiveNow.timeIntervalSince1970,
                    Double(triggered.nextTriggerEpochMs) / 1000.0
                ) + 1.0
            )
            updated.nextTriggerEpochMs = Self.computeNextTriggerEpochMs(
                hour: triggered.hour,
                minute: triggered.minute,
                now: nextRef,
                calendar: calendar
            )
        }
        current[idx] = updated
        let sorted = Self.sortReminders(current)
        saveToDefaults(sorted)
        publishReminders(sorted)
        return triggered
    }

    public func playSoundPreview(soundId: String) {
        let sound = TaskReminderSystemSound.resolve(byId: soundId)
        guard playSystemAudioPreview else { return }
        AudioServicesPlaySystemSound(SystemSoundID(sound.systemSoundId))
    }

    // MARK: - Private Helpers

    private func loadFromDefaults() -> [TaskReminderItem] {
        guard let data = userDefaults.data(forKey: storageKey) else {
            return []
        }
        return (try? JSONDecoder().decode([TaskReminderItem].self, from: data)) ?? []
    }

    private func saveToDefaults(_ items: [TaskReminderItem]) {
        if let encoded = try? JSONEncoder().encode(items) {
            userDefaults.set(encoded, forKey: storageKey)
        }
    }

    private func publishReminders(_ sorted: [TaskReminderItem]) {
        reminders = sorted
        onRemindersChanged?(sorted)
    }

    private static func sortReminders(_ items: [TaskReminderItem]) -> [TaskReminderItem] {
        items.sorted { lhs, rhs in
            let lhsMinutes = lhs.hour * 60 + lhs.minute
            let rhsMinutes = rhs.hour * 60 + rhs.minute
            if lhsMinutes != rhsMinutes {
                return lhsMinutes < rhsMinutes
            }
            if lhs.title != rhs.title {
                return lhs.title < rhs.title
            }
            return lhs.id < rhs.id
        }
    }

    private func notificationRequestIdentifier(forId id: String) -> String {
        "\(Self.notificationIdentifierPrefix)\(id)"
    }

    private func requestAuthorizationAndSchedule(item: TaskReminderItem, now: Date) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { [weak self] _, _ in
            self?.scheduleNotificationRequest(item: item, now: now)
        }
    }

    private func scheduleNotificationRequest(item: TaskReminderItem, now: Date) {
        let center = UNUserNotificationCenter.current()
        let requestId = notificationRequestIdentifier(forId: item.id)
        center.removePendingNotificationRequests(withIdentifiers: [requestId])

        let content = UNMutableNotificationContent()
        content.title = item.title
        content.body = "Đến giờ nhắc việc \(item.formattedTime) (\(item.repeatMode.labelVi))"
        content.sound = .default
        content.userInfo = [
            "reminder_id": item.id,
            "sound_id": item.soundId,
            "repeat_mode": item.repeatMode.rawValue
        ]

        let trigger: UNCalendarNotificationTrigger
        switch item.repeatMode {
        case .daily:
            var comps = DateComponents()
            comps.hour = item.hour
            comps.minute = item.minute
            comps.second = 0
            trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
        case .once:
            let targetDate = Self.computeNextTriggerDate(
                hour: item.hour,
                minute: item.minute,
                now: now,
                calendar: calendar
            )
            let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: targetDate)
            trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        }

        let request = UNNotificationRequest(identifier: requestId, content: content, trigger: trigger)
        center.add(request, withCompletionHandler: nil)
    }

    private func cancelSystemNotification(forId id: String) {
        let requestId = notificationRequestIdentifier(forId: id)
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [requestId])
        center.removeDeliveredNotifications(withIdentifiers: [requestId])
    }
}

public final class TaskReminderNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = TaskReminderNotificationDelegate()

    private override init() {
        super.init()
    }

    public func configure() {
        UNUserNotificationCenter.current().delegate = self
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        if let reminderId = userInfo["reminder_id"] as? String {
            let soundId = userInfo["sound_id"] as? String
            Task { @MainActor in
                _ = TaskReminderManager.shared.onNotificationTriggered(id: reminderId)
                if let soundId {
                    TaskReminderManager.shared.playSoundPreview(soundId: soundId)
                }
            }
        }
        completionHandler([.banner, .list, .sound])
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let reminderId = userInfo["reminder_id"] as? String {
            Task { @MainActor in
                _ = TaskReminderManager.shared.onNotificationTriggered(id: reminderId)
            }
        }
        completionHandler()
    }
}
