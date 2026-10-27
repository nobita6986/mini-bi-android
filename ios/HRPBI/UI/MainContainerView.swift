import SwiftUI

// MARK: - Main Container View (Top Bar + Notifications Sheet + Banner + Bottom Tab Bar)
// Mirrors Android's `MainActivity.kt` root scaffold 100%

public struct MainContainerView: View {
    @StateObject private var viewModel = MiniBiViewModel()

    public init() {}

    public var body: some View {
        Group {
            if viewModel.isAuthenticated {
                authenticatedScaffold
            } else {
                LoginScreen(viewModel: viewModel)
            }
        }
        .task {
            await viewModel.bootstrapSession()
        }
    }

    private var authenticatedScaffold: some View {
        let theme = viewModel.selectedTheme
        let env = viewModel.environmentConfig

        return VStack(spacing: 0) {
            // Top App Bar
            HStack(spacing: 10) {
                HStack(spacing: 8) {
                    Text(env.appBrandBadgeText)
                        .font(.caption.weight(.heavy))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color.white.opacity(0.2))
                        .foregroundColor(.white)
                        .cornerRadius(8)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(env.appBrandTitle)
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(.white)
                        if let user = viewModel.currentUser {
                            Text("\(user.fullName) · \(user.roleLabel)")
                                .font(.caption2)
                                .foregroundColor(Color.white.opacity(0.85))
                                .lineLimit(1)
                        }
                    }
                }

                Spacer()

                // Notifications Bell with Unread Badge
                Button {
                    Task { await viewModel.refreshNotifications() }
                    viewModel.isNotificationSheetPresented = true
                } label: {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill")
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .padding(8)
                        if viewModel.unreadNotificationCount > 0 {
                            Text("\(min(99, viewModel.unreadNotificationCount))")
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.red)
                                .foregroundColor(.white)
                                .clipShape(Capsule())
                                .offset(x: 4, y: -2)
                        }
                    }
                }

                // Theme Selector & Logout Menu
                Menu {
                    Section("Giao diện màu sắc") {
                        ForEach(MiniBiThemes.all) { palette in
                            Button {
                                viewModel.selectTheme(palette)
                            } label: {
                                HStack {
                                    Text(palette.name)
                                    if palette.id == theme.id {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    }
                    Divider()
                    Button(role: .destructive) {
                        Task { await viewModel.performLogout() }
                    } label: {
                        Label("Đăng xuất", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle.fill")
                        .font(.title3)
                        .foregroundColor(.white)
                        .padding(4)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(theme.primary)

            // Global Banner (Success / Error / Deadline Passed)
            if let message = viewModel.bannerMessage, !message.isEmpty {
                HStack(spacing: 8) {
                    Image(systemName: viewModel.bannerIsError ? "exclamationmark.octagon.fill" : "checkmark.circle.fill")
                        .foregroundColor(viewModel.bannerIsError ? SemanticColors.dangerText : SemanticColors.successText)
                    Text(message)
                        .font(.caption.weight(.semibold))
                        .foregroundColor(viewModel.bannerIsError ? SemanticColors.dangerText : SemanticColors.successText)
                    Spacer()
                    Button {
                        viewModel.dismissBanner()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.caption2.weight(.bold))
                            .foregroundColor(SemanticColors.textMuted)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(viewModel.bannerIsError ? SemanticColors.dangerBg : SemanticColors.successBg)
            }

            // Active Screen Content
            ZStack {
                switch viewModel.currentDestination {
                case .dashboard:
                    DashboardScreen(viewModel: viewModel)
                case .newEntry:
                    DirectEntryNewWorkersScreen(viewModel: viewModel)
                case .workers:
                    WorkersScreen(viewModel: viewModel)
                case .attendance:
                    AttendanceScreen(viewModel: viewModel)
                case .workReports:
                    WorkReportsScreen(viewModel: viewModel)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider()

            // Bottom Navigation Bar
            HStack(spacing: 0) {
                ForEach(MiniBiNavDestination.allCases) { dest in
                    let isSelected = viewModel.currentDestination == dest
                    Button {
                        viewModel.navigateTo(dest)
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: dest.systemIconName)
                                .font(.system(size: 18, weight: isSelected ? .bold : .regular))
                            Text(dest.titleVi)
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .foregroundColor(isSelected ? theme.primary : SemanticColors.textMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 6)
            .background(Color.white)
        }
        .sheet(isPresented: $viewModel.isNotificationSheetPresented) {
            NotificationDrawerSheet(viewModel: viewModel)
        }
    }
}

// MARK: - Notification Drawer Sheet

struct NotificationDrawerSheet: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let theme = viewModel.selectedTheme

        NavigationStack {
            List {
                if viewModel.notifications.isEmpty {
                    Text("Chưa có thông báo mới.")
                        .font(.subheadline)
                        .foregroundColor(SemanticColors.textSecondary)
                } else {
                    ForEach(viewModel.notifications) { item in
                        Button {
                            Task {
                                await viewModel.handleNotificationTap(item)
                            }
                        } label: {
                            HStack(alignment: .top, spacing: 10) {
                                Circle()
                                    .fill(item.isRead ? Color.clear : theme.primary)
                                    .frame(width: 8, height: 8)
                                    .padding(.top, 6)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.title)
                                        .font(.subheadline.weight(item.isRead ? .medium : .bold))
                                        .foregroundColor(SemanticColors.textPrimary)
                                    if !item.body.isEmpty {
                                        Text(item.body)
                                            .font(.caption)
                                            .foregroundColor(SemanticColors.textSecondary)
                                    }
                                    Text(WorkReportsContract.formatIsoDateTimeVi(item.createdAt))
                                        .font(.caption2)
                                        .foregroundColor(SemanticColors.textMuted)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("Thông báo (\(viewModel.unreadNotificationCount))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if viewModel.unreadNotificationCount > 0 {
                        Button("Đọc tất cả") {
                            Task { await viewModel.markAllNotificationsRead() }
                        }
                    }
                }
            }
        }
    }
}
