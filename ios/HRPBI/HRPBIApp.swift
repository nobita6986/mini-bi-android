import SwiftUI

@main
struct HRPBIApp: App {
    var body: some Scene {
        WindowGroup {
            MainContainerView()
                .preferredColorScheme(.light)
                .environment(\.colorScheme, .light)
        }
    }
}
