import SwiftUI

// MARK: - Login Screen
// Mirrors Android's `ui/screens/LoginScreen.kt` 100%

public struct LoginScreen: View {
    private enum LoginFocusField: Hashable {
        case identifier
        case password
    }

    @ObservedObject var viewModel: MiniBiViewModel
    @State private var isPasswordVisible: Bool = false
    @State private var accountPendingDeletion: SavedLoginAccount?
    @FocusState private var focusedField: LoginFocusField?

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
                VStack(spacing: 18) {
                    Spacer(minLength: 28)

                    // Brand Header (`HRP BI` or `HRP BI 2`) — Squircle Hero Logo
                    VStack(spacing: 10) {
                        ZStack {
                            RoundedRectangle(cornerRadius: HrpDesignTokens.radiusHero, style: .continuous)
                                .fill(Color.white)
                                .frame(width: 68, height: 68)
                                .shadow(color: Color.black.opacity(0.06), radius: 10, x: 0, y: 4)
                                .overlay(
                                    RoundedRectangle(cornerRadius: HrpDesignTokens.radiusHero, style: .continuous)
                                        .stroke(SemanticColors.borderSubtle.opacity(0.85), lineWidth: 0.8)
                                )
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(theme.primary)
                                .frame(width: 50, height: 50)
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

                    // Login Card (22pt continuous squircle)
                    VStack(alignment: .leading, spacing: 16) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Đăng Nhập Tài Khoản")
                                .font(.title3.weight(.bold))
                                .foregroundColor(SemanticColors.textPrimary)
                            Text("Sử dụng email hoặc mã nhân viên đã đăng ký")
                                .font(.caption)
                                .foregroundColor(SemanticColors.textMuted)
                        }

                        // Saved Login Accounts (1-tap login or confirmed remove)
                        if !viewModel.savedAccounts.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Tài khoản đã lưu (chạm để đăng nhập nhanh)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(SemanticColors.textSecondary)

                                ForEach(viewModel.savedAccounts) { account in
                                    HStack(spacing: 8) {
                                        Button {
                                            HapticsManager.lightImpact()
                                            Task {
                                                await viewModel.quickLoginWithSavedAccount(account)
                                                if viewModel.isAuthenticated {
                                                    HapticsManager.success()
                                                } else if viewModel.loginErrorMessage != nil {
                                                    HapticsManager.error()
                                                }
                                            }
                                        } label: {
                                            HStack(spacing: 10) {
                                                Circle()
                                                    .fill(theme.primaryLight)
                                                    .frame(width: 38, height: 38)
                                                    .overlay(
                                                        Text(String(account.displayName.prefix(1)).uppercased())
                                                            .font(.subheadline.weight(.bold))
                                                            .foregroundColor(theme.primary)
                                                    )
                                                VStack(alignment: .leading, spacing: 2) {
                                                    Text(account.displayName)
                                                        .font(.subheadline.weight(.semibold))
                                                        .foregroundColor(SemanticColors.textPrimary)
                                                        .lineLimit(1)
                                                    Text(account.identifier + (account.role.isEmpty ? "" : " · \(account.role)"))
                                                        .font(.caption)
                                                        .foregroundColor(SemanticColors.textMuted)
                                                        .lineLimit(1)
                                                }
                                                Spacer()
                                                Image(systemName: "arrow.right.circle.fill")
                                                    .font(.body)
                                                    .foregroundColor(theme.primary)
                                            }
                                            .padding(.leading, 10)
                                            .padding(.vertical, 8)
                                            .contentShape(Rectangle())
                                        }
                                        .buttonStyle(HrpPressableButtonStyle())
                                        .disabled(viewModel.isLoggingIn)

                                        Button(role: .destructive) {
                                            HapticsManager.warning()
                                            accountPendingDeletion = account
                                        } label: {
                                            Image(systemName: "xmark.circle.fill")
                                                .font(.subheadline)
                                                .foregroundColor(SemanticColors.textMuted)
                                                .frame(minWidth: 44, minHeight: 44)
                                                .contentShape(Rectangle())
                                        }
                                        .buttonStyle(.plain)
                                        .accessibilityLabel("Xóa tài khoản đã lưu \(account.identifier)")
                                    }
                                    .hrpMutedTile(cornerRadius: HrpDesignTokens.radiusMD)
                                }
                            }
                            Divider()
                        }

                        // Identifier Input
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Email hoặc ID nhân viên")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)

                            HStack(spacing: 10) {
                                Image(systemName: "person.fill")
                                    .foregroundColor(theme.primary)
                                TextField("Nhập email hoặc mã ID", text: $viewModel.loginIdentifierInput)
                                    .focused($focusedField, equals: .identifier)
                                    .submitLabel(.next)
                                    .onSubmit {
                                        focusedField = .password
                                    }
                                    .textContentType(.username)
                                    .keyboardType(.emailAddress)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                                    .foregroundColor(SemanticColors.textPrimary)
                                    .tint(theme.primary)
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 48)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(Color.white)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .stroke(
                                        focusedField == .identifier ? theme.primary : Color(hex: 0xD8DEE6),
                                        lineWidth: focusedField == .identifier ? 1.5 : 1.0
                                    )
                            )
                            .animation(.easeOut(duration: 0.15), value: focusedField)
                        }

                        // Password Input
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Mật khẩu")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(SemanticColors.textSecondary)

                            HStack(spacing: 10) {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(theme.primary)
                                Group {
                                    if isPasswordVisible {
                                        TextField("Nhập mật khẩu", text: $viewModel.loginPasswordInput)
                                            .textInputAutocapitalization(.never)
                                            .autocorrectionDisabled(true)
                                    } else {
                                        SecureField("Nhập mật khẩu", text: $viewModel.loginPasswordInput)
                                            .textInputAutocapitalization(.never)
                                    }
                                }
                                .focused($focusedField, equals: .password)
                                .textContentType(.password)
                                .submitLabel(.go)
                                .onSubmit {
                                    submitLoginFromForm()
                                }
                                .foregroundColor(SemanticColors.textPrimary)
                                .tint(theme.primary)

                                Button {
                                    HapticsManager.selection()
                                    isPasswordVisible.toggle()
                                } label: {
                                    Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                                        .foregroundColor(SemanticColors.textMuted)
                                        .frame(minWidth: 44, minHeight: 44)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.leading, 12)
                            .padding(.trailing, 4)
                            .frame(minHeight: 48)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(Color.white)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .stroke(
                                        focusedField == .password ? theme.primary : Color(hex: 0xD8DEE6),
                                        lineWidth: focusedField == .password ? 1.5 : 1.0
                                    )
                            )
                            .animation(.easeOut(duration: 0.15), value: focusedField)
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
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(SemanticColors.dangerBg)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .stroke(SemanticColors.dangerBorder, lineWidth: 0.8)
                            )
                        }

                        // Login Button (50pt height + HrpPressableButtonStyle)
                        Button {
                            submitLoginFromForm()
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
                            .frame(minHeight: 50)
                            .background(
                                RoundedRectangle(cornerRadius: HrpDesignTokens.radiusMD, style: .continuous)
                                    .fill(theme.primary)
                            )
                        }
                        .buttonStyle(HrpPressableButtonStyle())
                        .disabled(viewModel.isLoggingIn || viewModel.isBootstrapping)
                    }
                    .padding(18)
                    .hrpCardSurface(cornerRadius: HrpDesignTokens.radiusHero)
                    .padding(.horizontal, 18)

                    Text("Phiên bản \(env.marketingVersion) (Build \(env.buildNumber))")
                        .font(.caption.monospacedDigit())
                        .foregroundColor(SemanticColors.textMuted)
                        .padding(.top, 8)

                    Spacer(minLength: 24)
                }
            }
        }
        .dismissKeyboardOnTapOutside()
        .confirmationDialog(
            "Xóa tài khoản đã lưu?",
            isPresented: Binding(
                get: { accountPendingDeletion != nil },
                set: { if !$0 { accountPendingDeletion = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let pending = accountPendingDeletion {
                Button("Xóa \(pending.displayName)", role: .destructive) {
                    HapticsManager.lightImpact()
                    viewModel.removeSavedAccount(pending)
                    accountPendingDeletion = nil
                }
            }
            Button("Hủy", role: .cancel) {
                accountPendingDeletion = nil
            }
        } message: {
            if let pending = accountPendingDeletion {
                Text("Tài khoản \(pending.identifier) sẽ được gỡ khỏi danh sách đăng nhập nhanh trên thiết bị này.")
            }
        }
    }

    private func submitLoginFromForm() {
        focusedField = nil
        KeyboardDismissal.dismiss()
        HapticsManager.lightImpact()
        Task {
            await viewModel.performLogin()
            if viewModel.isAuthenticated {
                HapticsManager.success()
            } else if viewModel.loginErrorMessage != nil {
                HapticsManager.error()
            }
        }
    }
}
