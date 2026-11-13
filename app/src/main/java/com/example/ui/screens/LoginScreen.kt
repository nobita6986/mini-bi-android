package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SavedLoginAccount
import com.example.ui.components.HrpDesignTokens
import com.example.ui.components.HrpHaptics
import com.example.ui.components.HrpInitialsAvatar
import com.example.ui.components.hrpCardSurface
import com.example.ui.components.hrpPressableScale
import com.example.ui.theme.SemanticColors

@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onLoginSubmit: (String, String, Boolean) -> Unit,
    brandBadgeLabel: String = "BI",
    savedAccounts: List<SavedLoginAccount> = emptyList(),
    onRemoveSavedAccount: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val latestSaved = savedAccounts.firstOrNull()

    var identifier by remember { mutableStateOf(latestSaved?.identifier.orEmpty()) }
    var password by remember { mutableStateOf(latestSaved?.password.orEmpty()) }
    var rememberAccount by remember { mutableStateOf(true) }
    var hasManualEdit by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val submitInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(savedAccounts) {
        if (!hasManualEdit) {
            val top = savedAccounts.firstOrNull()
            if (top != null) {
                identifier = top.identifier
                password = top.password
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        SemanticColors.BackgroundLight,
                        Color(0xFFEEF2F7)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SemanticColors.SurfaceLight,
                border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("login_brand_logo")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hrpartner_logo),
                    contentDescription = "Logo HRP",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "HRP $brandBadgeLabel",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                fontWeight = FontWeight.ExtraBold,
                color = SemanticColors.TextPrimary,
                modifier = Modifier
                    .testTag("login_brand_title")
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Hệ thống quản trị nhân sự HRP",
                style = MaterialTheme.typography.bodyMedium,
                color = SemanticColors.TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hrpCardSurface(
                        cornerRadius = HrpDesignTokens.RadiusHero,
                        shadowElevation = 1.dp
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Đăng Nhập Tài Khoản",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SemanticColors.TextPrimary
                    )
                    Text(
                        text = "Sử dụng email hoặc mã nhân viên đã đăng ký",
                        style = MaterialTheme.typography.bodySmall,
                        color = SemanticColors.TextMuted
                    )
                }

                if (savedAccounts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Tài khoản đã lưu",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SemanticColors.TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            savedAccounts.forEach { acc ->
                                val isSelected = identifier.trim().equals(acc.identifier.trim(), ignoreCase = true)
                                val chipShape = RoundedCornerShape(HrpDesignTokens.RadiusMD)
                                Surface(
                                    shape = chipShape,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        SemanticColors.SurfaceSubtle
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            SemanticColors.SurfaceBorder
                                        }
                                    ),
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .clickable {
                                            HrpHaptics.tick(hapticFeedback)
                                            focusManager.clearFocus()
                                            identifier = acc.identifier
                                            password = acc.password
                                            rememberAccount = true
                                            hasManualEdit = true
                                        }
                                        .testTag("saved_account_chip_${acc.identifier}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            start = 8.dp,
                                            end = 4.dp,
                                            top = 6.dp,
                                            bottom = 6.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        HrpInitialsAvatar(
                                            fullName = acc.displayName.ifBlank { acc.identifier },
                                            size = 28.dp,
                                            cornerRadius = 9.dp
                                        )
                                        Column(
                                            modifier = Modifier.widthIn(max = 160.dp)
                                        ) {
                                            Text(
                                                text = acc.displayName.ifBlank { acc.identifier },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SemanticColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!acc.displayName.equals(acc.identifier, ignoreCase = true)) {
                                                Text(
                                                    text = acc.identifier,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SemanticColors.TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                HrpHaptics.tick(hapticFeedback)
                                                if (identifier.trim().equals(acc.identifier.trim(), ignoreCase = true)) {
                                                    identifier = ""
                                                    password = ""
                                                    hasManualEdit = true
                                                }
                                                onRemoveSavedAccount(acc.identifier)
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("remove_saved_account_${acc.identifier}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa tài khoản đã lưu",
                                                tint = SemanticColors.TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = identifier,
                    onValueChange = {
                        hasManualEdit = true
                        identifier = it
                    },
                    label = { Text("Email hoặc ID nhân viên") },
                    placeholder = { Text("Nhập email hoặc mã ID") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(HrpDesignTokens.RadiusMD),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SemanticColors.SurfaceLight,
                        unfocusedContainerColor = SemanticColors.SurfaceSubtle.copy(alpha = 0.45f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = SemanticColors.SurfaceBorder
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_identifier_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        hasManualEdit = true
                        password = it
                    },
                    label = { Text("Mật khẩu") },
                    placeholder = { Text("Nhập mật khẩu") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(HrpDesignTokens.RadiusMD),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SemanticColors.SurfaceLight,
                        unfocusedContainerColor = SemanticColors.SurfaceSubtle.copy(alpha = 0.45f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = SemanticColors.SurfaceBorder
                    ),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (!busy && identifier.isNotBlank() && password.isNotBlank()) {
                                onLoginSubmit(identifier, password, rememberAccount)
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.testTag("toggle_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                tint = SemanticColors.TextSecondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            rememberAccount = !rememberAccount
                        }
                        .testTag("remember_account_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberAccount,
                        onCheckedChange = {
                            focusManager.clearFocus()
                            rememberAccount = it
                        },
                        modifier = Modifier.testTag("remember_account_checkbox")
                    )
                    Text(
                        text = "Lưu tài khoản đã đăng nhập",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SemanticColors.TextSecondary
                    )
                }

                if (error != null) {
                    Surface(
                        shape = RoundedCornerShape(HrpDesignTokens.RadiusMD),
                        color = SemanticColors.ErrorBg,
                        border = BorderStroke(1.dp, SemanticColors.ErrorBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_error_banner")
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = SemanticColors.ErrorText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                val canSubmit = !busy && identifier.isNotBlank() && password.isNotBlank()
                Button(
                    onClick = {
                        HrpHaptics.tick(hapticFeedback)
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onLoginSubmit(identifier, password, rememberAccount)
                    },
                    enabled = canSubmit,
                    interactionSource = submitInteractionSource,
                    shape = RoundedCornerShape(HrpDesignTokens.RadiusMD),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                        disabledContentColor = Color.White.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .hrpPressableScale(
                            interactionSource = submitInteractionSource,
                            enabled = canSubmit
                        )
                        .testTag("login_submit_button")
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Đang đăng nhập…")
                    } else {
                        Text(
                            text = "Đăng Nhập",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
