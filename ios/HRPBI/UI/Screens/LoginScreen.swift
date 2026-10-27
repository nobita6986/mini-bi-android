import SwiftUI

// MARK: - Login Screen
// Mirrors Android's `ui/screens/LoginScreen.kt` 100%

public struct LoginScreen: View {
    @ObservedObject var viewModel: MiniBiViewModel
    @State private var isPasswordVisible: Bool = false

    public init(viewModel: MiniBiViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        let theme = viewModel.selectedTheme
        let env = viewModel.environmentConfig

        ZStack {
            LinearGradient(
                colors: [theme.primaryDark, theme.primary, theme.background],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    Spacer(minLength: 28)

                    // Brand Header (`HRP BI` or `HRP BI 2`)
                    VStack(spacing: 10) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 20, style: .continuous)
                                .fill(Color.white.opacity(0.18))
                                .frame(width: 76, height: 76)
                            Text(env.appBrandBadgeText)
                                .font(.system(size: 28, weight: .heavy, design: .rounded))
                                .foregroundColor(.white)
                        }

                        Text(env.appBrandTitle)
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)

                        Text("Hệ thống Quản trị Nhân sự & Báo cáo điều hành")
                            .font(.subheadline)
                            .foregroundColor(Color.white.opacity(0.88))
                            .multilineTextAlignment(.center)

                        Text(env.defaultBaseUrl)
                            .font(.caption2)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.16))
                            .foregroundColor(.white)
                            .clipShape(Capsule())
                    }
                    .padding(.bottom, 4)

                    // Login Card
                    VStack(alignment: .leading, spacing: 16) {
                        // Saved Login Accounts (1-tap login or remove)
                        if !viewModel.savedAccounts.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Tài khoản đã lưu (chạm để đăng nhập nhanh)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.textSecondary)

                                ForEach(viewModel.savedAccounts) { account in
                                    HStack(spacing: 10) {
                                        Button {
                                            Task {
                                                await viewModel.quickLoginWithSavedAccount(account)
                                            }
                                        } label: {
                                            HStack(spacing: 10) {
                                                Circle()
                                                    .fill(theme.primaryLight)
                                                    .frame(width: 36, height: 36)
                                                    .overlay(
                                                        Text(String(account.displayName.prefix(1)).uppercased())
                                                            .font(.subheadline.weight(.bold))
                                                            .foregroundColor(theme.primary)
                                                    )
                                                VStack(alignment: .leading, spacing: 2) {
                                                    Text(account.displayName)
                                                        .font(.subheadline.weight(.semibold))
                                                        .foregroundColor(SemanticColors.textPrimary)
                                                    Text(account.identifier + (account.role.isEmpty ? "" : " · \(account.role)"))
                                                        .font(.caption)
                                                        .foregroundColor(SemanticColors.textMuted)
                                                }
                                                Spacer()
                                                Image(systemName: "arrow.right.circle.fill")
                                                    .foregroundColor(theme.primary)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                        .disabled(viewModel.isLoggingIn)

                                        Button {
                                            viewModel.removeSavedAccount(account)
                                        } label: {
                                            Image(systemName: "xmark.circle.fill")
                                                .foregroundColor(SemanticColors.textMuted)
                                                .padding(4)
                                        }
                                        .buttonStyle(.plain)
                                        .accessibilityLabel("Xóa tài khoản đã lưu \(account.identifier)")
                                    }
                                    .padding(10)
                                    .background(Color(hex: 0xF8FAFC))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                                    )
                                    .cornerRadius(10)
                                }
                            }
                            Divider()
                        }

                        // Identifier Input
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Tài khoản / Mã nhân viên / Email")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)

                            HStack {
                                Image(systemName: "person.fill")
                                    .foregroundColor(SemanticColors.textMuted)
                                TextField("Nhập tên đăng nhập...", text: $viewModel.loginIdentifierInput)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                            }
                            .padding(12)
                            .background(Color(hex: 0xF8FAFC))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )
                            .cornerRadius(10)
                        }

                        // Password Input
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Mật khẩu")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)

                            HStack {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(SemanticColors.textMuted)
                                if isPasswordVisible {
                                    TextField("Nhập mật khẩu...", text: $viewModel.loginPasswordInput)
                                        .textInputAutocapitalization(.never)
                                        .autocorrectionDisabled(true)
                                } else {
                                    SecureField("Nhập mật khẩu...", text: $viewModel.loginPasswordInput)
                                        .textInputAutocapitalization(.never)
                                }
                                Button {
                                    isPasswordVisible.toggle()
                                } label: {
                                    Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                                        .foregroundColor(SemanticColors.textMuted)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(12)
                            .background(Color(hex: 0xF8FAFC))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(SemanticColors.borderSubtle, lineWidth: 1)
                            )
                            .cornerRadius(10)
                        }

                        // Remember Login Toggle
                        Toggle(isOn: $viewModel.rememberLoginChecked) {
                            Text("Ghi nhớ đăng nhập trên thiết bị này")
                                .font(.subheadline)
                                .foregroundColor(SemanticColors.textSecondary)
                        }
                        .tint(theme.primary)

                        // Error Banner
                        if let errorMsg = viewModel.loginErrorMessage, !errorMsg.isEmpty {
                            HStack(spacing: 8) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(SemanticColors.dangerText)
                                Text(errorMsg)
                                    .font(.caption.weight(.medium))
                                    .foregroundColor(SemanticColors.dangerText)
                                Spacer()
                            }
                            .padding(10)
                            .background(SemanticColors.dangerBg)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(SemanticColors.dangerBorder, lineWidth: 1)
                            )
                            .cornerRadius(8)
                        }

                        // Login Button
                        Button {
                            Task {
                                await viewModel.performLogin()
                            }
                        } label: {
                            HStack {
                                Spacer()
                                if viewModel.isLoggingIn || viewModel.isBootstrapping {
                                    ProgressView()
                                        .tint(.white)
                                        .padding(.trailing, 6)
                                }
                                Text(viewModel.isLoggingIn ? "Đang đăng nhập..." : "Đăng nhập \(env.appBrandTitle)")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Spacer()
                            }
                            .padding(.vertical, 14)
                            .background(theme.primary)
                            .cornerRadius(12)
                        }
                        .disabled(viewModel.isLoggingIn || viewModel.isBootstrapping)
                    }
                    .padding(20)
                    .background(Color.white)
                    .cornerRadius(18)
                    .shadow(color: Color.black.opacity(0.12), radius: 14, x: 0, y: 6)
                    .padding(.horizontal, 16)

                    Text("Phiên bản \(env.marketingVersion) (Build \(env.buildNumber))")
                        .font(.caption)
                        .foregroundColor(Color.white.opacity(0.8))
                        .padding(.top, 8)

                    Spacer(minLength: 24)
                }
            }
        }
    }
}
