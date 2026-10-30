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
        .preferredColorScheme(.light)
        .environment(\.colorScheme, .light)
        .dismissKeyboardOnTapOutside()
        .task {
            await viewModel.bootstrapSession()
        }
    }

    private var authenticatedScaffold: some View {
        let theme = viewModel.selectedTheme
        let env = viewModel.environmentConfig

        return VStack(spacing: 0) {
            // Top App Bar (matches Android MainActivity.kt lightTopBar + primary brand badge)
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    HStack(spacing: 8) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .fill(Color.white)
                                .frame(width: 40, height: 40)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                                        .stroke(theme.topBarBorder, lineWidth: 1)
                                )
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(theme.primary)
                                .frame(width: 30, height: 30)
                            Text(env.appBrandBadgeText)
                                .font(.caption.weight(.heavy))
                                .foregroundColor(.white)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text("HRPartner")
                                    .font(.system(size: 17, weight: .heavy))
                                    .foregroundColor(SemanticColors.textPrimary)
                                Text(env.appBrandBadgeText)
                                    .font(.system(size: 10, weight: .bold))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(theme.primary)
                                    .foregroundColor(.white)
                                    .cornerRadius(6)
                            }
                            if let user = viewModel.currentUser {
                                let activeTitle = (viewModel.currentDestination == .workers && viewModel.salaryAdvanceSheetOpen)
                                    ? "Ứng lương"
                                    : viewModel.currentDestination.titleVi
                                Text("\(activeTitle) · \(user.fullName)")
                                    .font(.caption)
                                    .foregroundColor(SemanticColors.textMuted)
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
                                .foregroundColor(theme.primary)
                                .padding(8)
                                .background(Color.white.opacity(0.85))
                                .clipShape(Circle())
                                .overlay(
                                    Circle()
                                        .stroke(theme.topBarBorder, lineWidth: 1)
                                )
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
                        HStack(spacing: 4) {
                            Image(systemName: "ellipsis.circle.fill")
                                .font(.subheadline)
                            Text("Menu")
                                .font(.caption.weight(.semibold))
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(Color.white.opacity(0.85))
                        .foregroundColor(theme.primary)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(theme.primary.opacity(0.28), lineWidth: 1)
                        )
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(theme.topBar)

                Rectangle()
                    .fill(theme.topBarBorder)
                    .frame(height: 1)
            }

            // Active Screen Content + Floating Toast Layer (does not occupy layout space or shift UI)
            ZStack(alignment: .top) {
                Group {
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

                if let message = viewModel.bannerMessage, !message.isEmpty {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: viewModel.bannerIsError ? "exclamationmark.octagon.fill" : "checkmark.circle.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(viewModel.bannerIsError ? SemanticColors.dangerText : SemanticColors.successText)
                            .padding(.top, 1)

                        Text(message)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(viewModel.bannerIsError ? SemanticColors.dangerText : SemanticColors.successText)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)

                        Spacer(minLength: 4)

                        Button {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                viewModel.dismissBanner()
                            }
                        } label: {
                            Image(systemName: "xmark")
                                .font(.caption2.weight(.bold))
                                .foregroundColor(SemanticColors.textMuted)
                                .padding(4)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(viewModel.bannerIsError ? SemanticColors.dangerBg : SemanticColors.successBg)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(
                                viewModel.bannerIsError ? SemanticColors.dangerBorder : SemanticColors.successBorder,
                                lineWidth: 1
                            )
                    )
                    .shadow(color: Color.black.opacity(0.14), radius: 10, x: 0, y: 4)
                    .padding(.horizontal, 14)
                    .padding(.top, 10)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(999)
                }
            }
            .animation(.easeInOut(duration: 0.22), value: viewModel.bannerMessage)
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider()

            // Bottom Navigation Bar
            HStack(spacing: 0) {
                ForEach(MiniBiNavDestination.allCases) { dest in
                    let isSelected = viewModel.currentDestination == dest
                    Button {
                        KeyboardDismissal.dismiss()
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
