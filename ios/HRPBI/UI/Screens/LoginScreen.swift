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
                colors: [
                    Color(hex: 0xFFFBF8),
                    theme.background,
                    Color(hex: 0xF5F4F2)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    Spacer(minLength: 28)

                    // Brand Header (`HRP BI` or `HRP BI 2`) — matches Android LoginScreen.kt
                    VStack(spacing: 8) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(Color.white)
                                .frame(width: 64, height: 64)
                                .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                        .stroke(Color(hex: 0xE5E7EB), lineWidth: 1)
                                )
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(theme.primary)
                                .frame(width: 48, height: 48)
                            Text(env.appBrandBadgeText)
                                .font(.system(size: 20, weight: .heavy, design: .rounded))
                                .foregroundColor(.white)
                        }

                        Text(env.appBrandTitle)
                            .font(.system(size: 24, weight: .heavy))
                            .foregroundColor(SemanticColors.textPrimary)

                        Text("Hệ thống quản trị nhân sự HRP")
                            .font(.subheadline)
                            .foregroundColor(SemanticColors.textMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.bottom, 4)

                    // Login Card
                    VStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Đăng Nhập Tài Khoản")
                                .font(.title3.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)
                            Text("Sử dụng email hoặc mã nhân viên đã đăng ký")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textMuted)
                        }
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
                            Text("Email hoặc ID nhân viên")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)

                            HStack {
                                Image(systemName: "person.fill")
                                    .foregroundColor(theme.primary)
                                TextField("Nhập email hoặc mã ID", text: $viewModel.loginIdentifierInput)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                            }
                            .padding(12)
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color(hex: 0xD8DEE6), lineWidth: 1)
                            )
                            .cornerRadius(14)
                        }

                        // Password Input
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Mật khẩu")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)

                            HStack {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(theme.primary)
                                if isPasswordVisible {
                                    TextField("Nhập mật khẩu", text: $viewModel.loginPasswordInput)
                                        .textInputAutocapitalization(.never)
                                        .autocorrectionDisabled(true)
                                } else {
                                    SecureField("Nhập mật khẩu", text: $viewModel.loginPasswordInput)
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
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color(hex: 0xD8DEE6), lineWidth: 1)
                            )
                            .cornerRadius(14)
                        }

                        // Remember Login Toggle
                        Toggle(isOn: $viewModel.rememberLoginChecked) {
                            Text("Lưu tài khoản đã đăng nhập")
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
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(SemanticColors.dangerBorder, lineWidth: 1)
                            )
                            .cornerRadius(12)
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
                                Text(viewModel.isLoggingIn ? "Đang đăng nhập…" : "Đăng Nhập")
                                    .font(.headline.weight(.bold))
                                    .foregroundColor(.white)
                                Spacer()
                            }
                            .padding(.vertical, 14)
                            .background(theme.primary)
                            .cornerRadius(14)
                        }
                        .disabled(viewModel.isLoggingIn || viewModel.isBootstrapping)
                    }
                    .padding(18)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color(hex: 0xE5E7EB), lineWidth: 1)
                    )
                    .cornerRadius(22)
                    .shadow(color: Color.black.opacity(0.06), radius: 10, x: 0, y: 4)
                    .padding(.horizontal, 18)

                    Text("Phiên bản \(env.marketingVersion) (Build \(env.buildNumber))")
                        .font(.caption)
                        .foregroundColor(SemanticColors.textMuted)
                        .padding(.top, 8)

                    Spacer(minLength: 24)
                }
            }
        }
    }
}
