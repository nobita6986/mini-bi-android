import SwiftUI

@main
struct HRPBIApp: App {
    init() {
        TaskReminderNotificationDelegate.shared.configure()
    }

    var body: some Scene {
        WindowGroup {
            MainContainerView()
                .preferredColorScheme(.light)
                .environment(\.colorScheme, .light)
        }
    }
}
