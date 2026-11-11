package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import com.example.data.TaskReminderManager
import com.example.ui.MainDestination
import com.example.ui.MiniBiViewModel
import com.example.ui.resolveMainNavigationDestinations
import com.example.ui.components.HrpDesignTokens
import com.example.ui.components.HrpHaptics
import com.example.ui.components.HrpInitialsAvatar
import com.example.ui.components.HrpStatusBadge
import com.example.ui.components.hrpCardSurface
import com.example.ui.components.hrpMutedTile
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DirectEntryNewWorkersScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RecruitmentPostsScreen
import com.example.ui.screens.TaskReminderDialog
import com.example.ui.screens.WorkReportsScreen
import com.example.ui.screens.WorkersAndDirectEntryScreen
import com.example.ui.screens.resolveVisibleWorkerTabs
import com.example.ui.theme.LocalMiniBiPalette
import com.example.ui.theme.MiniBiThemes
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SemanticColors
import com.example.ui.theme.tabularNums
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MiniBiViewModel = viewModel()
            val themeId by viewModel.themeId.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                Coil.setImageLoader(viewModel.imageLoader)
            }

            MyApplicationTheme(themeId = themeId) {
                MiniBiAppRoot(viewModel = viewModel)
            }
        }
    }
}

internal fun resolveBrandBadgeLabel(serverUrl: String = BuildConfig.DEFAULT_SERVER_URL): String {
    val normalized = serverUrl.trim().lowercase()
    return if (
        normalized.contains("://bi2.") ||
        normalized.contains("//bi2.") ||
        normalized.startsWith("bi2.") ||
        normalized.contains("://bi.hrpvietnam.") ||
        normalized.contains("//bi.hrpvietnam.") ||
        normalized.startsWith("bi.hrpvietnam.")
    ) {
        "BI2"
    } else {
        "BI"
    }
}

@Composable
fun MiniBiAppRoot(viewModel: MiniBiViewModel) {
    val sessionBootstrapping by viewModel.sessionBootstrapping.collectAsStateWithLifecycle()
    val actor by viewModel.currentActor.collectAsStateWithLifecycle()
    val themeId by viewModel.themeId.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val brandBadgeLabel = resolveBrandBadgeLabel(serverUrl)
    val loginBusy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val savedAccounts by viewModel.savedAccounts.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val salaryAdvanceDialogOpen by viewModel.salaryAdvanceDialogOpen.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val bannerIsError by viewModel.statusBannerIsError.collectAsStateWithLifecycle()
    val hasFailedBackgroundUploads by viewModel.hasFailedBackgroundUploads.collectAsStateWithLifecycle()
    val palette = LocalMiniBiPalette.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current

    if (sessionBootstrapping) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .hrpCardSurface(cornerRadius = HrpDesignTokens.RadiusHero, shadowElevation = 1.dp)
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SemanticColors.SurfaceLight,
                        border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                        shadowElevation = 1.dp,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hrpartner_logo),
                            contentDescription = "HRPartner",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(9.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "HRP",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = brandBadgeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Đang xác thực phiên làm việc…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val authenticatedActor = actor
    if (authenticatedActor == null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                LoginScreen(
                    busy = loginBusy,
                    error = loginError,
                    brandBadgeLabel = brandBadgeLabel,
                    savedAccounts = savedAccounts,
                    onRemoveSavedAccount = { viewModel.removeSavedAccount(it) },
                    onLoginSubmit = { identifier, password, rememberAccount ->
                        viewModel.login(identifier, password, rememberAccount)
                    }
                )
            }
        }
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val taskReminderManager = remember(context) { TaskReminderManager.getInstance(context) }
    val taskReminders by taskReminderManager.remindersFlow.collectAsStateWithLifecycle()
    val enabledReminderCount = taskReminders.count { it.isEnabled }
    var reminderDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(taskReminderManager) {
        taskReminderManager.reconcileExpiredOnceReminders()
    }

    val canCreateEntry = authenticatedActor.hasCapability("entry_create") &&
        authenticatedActor.hasCapability("submission_create")

    val navDestinations = resolveMainNavigationDestinations(authenticatedActor).map { dest ->
        when (dest) {
            MainDestination.DASHBOARD -> Triple(dest, Icons.Default.Analytics, Icons.Outlined.Analytics)
            MainDestination.WORK_REPORTS -> Triple(dest, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment)
            MainDestination.ATTENDANCE -> Triple(dest, Icons.Default.CameraAlt, Icons.Outlined.CameraAlt)
            MainDestination.WORKERS -> Triple(dest, Icons.Default.Groups, Icons.Outlined.Groups)
            MainDestination.RECRUITMENT -> Triple(dest, Icons.Default.Campaign, Icons.Outlined.Campaign)
            MainDestination.NEW_ENTRY -> Triple(dest, Icons.Default.PersonAdd, Icons.Outlined.PersonAdd)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(304.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SemanticColors.SurfaceLight,
                            border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hrpartner_logo),
                                contentDescription = "HRPartner",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(5.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HRPartner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("drawer_brand_badge")
                                ) {
                                    Text(
                                        text = brandBadgeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Hệ thống quản trị nhân sự HRP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hrpMutedTile(cornerRadius = HrpDesignTokens.RadiusMD)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HrpInitialsAvatar(
                            fullName = authenticatedActor.displayName,
                            size = 40.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = authenticatedActor.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            authenticatedActor.displayIdentifier?.let { identifierText ->
                                Text(
                                    text = identifierText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (authenticatedActor.scopes.isNotEmpty()) {
                                val localizedScopes = authenticatedActor.scopes
                                    .map { sc ->
                                        when (sc.kind) {
                                            "all" -> "Toàn hệ thống"
                                            "team" -> "Đội nhóm"
                                            "own" -> "Cá nhân"
                                            else -> sc.kind
                                        }
                                    }
                                    .distinct()
                                    .joinToString(", ")
                                HrpStatusBadge(
                                    text = "Phạm vi: $localizedScopes",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = SemanticColors.SurfaceBorder)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "DANH MỤC CHỨC NĂNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                navDestinations.forEach { (dest, icon, _) ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = dest.label,
                                fontWeight = if (currentDestination == dest) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selected = currentDestination == dest,
                        onClick = {
                            HrpHaptics.tick(hapticFeedback)
                            viewModel.navigateTo(dest)
                            scope.launch { drawerState.close() }
                        },
                        shape = RoundedCornerShape(12.dp),
                        icon = { Icon(icon, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("drawer_item_${dest.route}")
                    )
                }

                HorizontalDivider(
                    color = SemanticColors.SurfaceBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Text(
                    text = "CHỦ ĐỀ MÀU SẮC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniBiThemes.forEach { themePalette ->
                        val selected = themePalette.id == themeId
                        Box(
                            modifier = Modifier
                                .size(if (selected) 30.dp else 26.dp)
                                .clip(CircleShape)
                                .background(themePalette.lightPrimary)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { viewModel.setTheme(themePalette.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider(color = SemanticColors.SurfaceBorder)

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Đăng xuất",
                            color = SemanticColors.ErrorText,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    selected = false,
                    onClick = {
                        HrpHaptics.tick(hapticFeedback)
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = SemanticColors.ErrorBg.copy(alpha = 0.45f)
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Đăng xuất",
                            tint = SemanticColors.ErrorText
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .testTag("drawer_logout_button")
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    HrpHaptics.tick(hapticFeedback)
                                    scope.launch { drawerState.open() }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SemanticColors.SurfaceSubtle.copy(alpha = 0.7f))
                                    .testTag("open_drawer_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Mở menu điều hướng",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = SemanticColors.SurfaceLight,
                                border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable {
                                        HrpHaptics.tick(hapticFeedback)
                                        scope.launch { drawerState.open() }
                                    }
                                    .testTag("topbar_brand_logo")
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_hrpartner_logo),
                                    contentDescription = "HRPartner",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "HRPartner",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("topbar_brand_badge")
                                    ) {
                                        Text(
                                            text = brandBadgeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val topBarSubtitleLabel = when {
                                    currentDestination == MainDestination.WORKERS && salaryAdvanceDialogOpen ->
                                        "Ứng lương"
                                    currentDestination == MainDestination.RECRUITMENT ->
                                        "Tin tuyển dụng"
                                    else ->
                                        currentDestination.label
                                }
                                Text(
                                    text = "$topBarSubtitleLabel · ${authenticatedActor.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (enabledReminderCount > 0) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        SemanticColors.SurfaceSubtle
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (enabledReminderCount > 0) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        } else {
                                            SemanticColors.SurfaceBorder
                                        }
                                    ),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            HrpHaptics.tick(hapticFeedback)
                                            reminderDialogOpen = true
                                        }
                                        .testTag("topbar_bell_reminder_button")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Nhắc việc",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                if (enabledReminderCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SemanticColors.ErrorLight,
                                        border = BorderStroke(1.dp, SemanticColors.SurfaceLight),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .testTag("topbar_bell_reminder_badge")
                                    ) {
                                        Text(
                                            text = enabledReminderCount.coerceAtMost(99).toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp).tabularNums(),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = SemanticColors.SurfaceBorder)
                    }
                }
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = SemanticColors.SurfaceBorder)
                    val currentDensity = LocalDensity.current
                    val bottomBarDensity = Density(
                        density = currentDensity.density,
                        fontScale = currentDensity.fontScale.coerceAtMost(1.05f)
                    )
                    CompositionLocalProvider(LocalDensity provides bottomBarDensity) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                                    .height(66.dp)
                                    .selectableGroup()
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navDestinations.forEach { (dest, filledIcon, outlinedIcon) ->
                                    val selected = currentDestination == dest
                                    val pillColor by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                        animationSpec = tween(durationMillis = 180),
                                        label = "bottom_nav_pill_${dest.route}"
                                    )
                                    val itemTint by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        animationSpec = tween(durationMillis = 180),
                                        label = "bottom_nav_tint_${dest.route}"
                                    )
                                    val interactionSource = remember { MutableInteractionSource() }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .selectable(
                                                selected = selected,
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = {
                                                    if (!selected) {
                                                        HrpHaptics.tick(hapticFeedback)
                                                        viewModel.navigateTo(dest)
                                                    }
                                                },
                                                role = Role.Tab
                                            )
                                            .padding(horizontal = 1.dp, vertical = 6.dp)
                                            .testTag("bottom_nav_${dest.route}"),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(50.dp)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(pillColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (selected) filledIcon else outlinedIcon,
                                                contentDescription = dest.label,
                                                tint = itemTint,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                letterSpacing = (-0.15).sp
                                            ),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = itemTint,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("bottom_nav_label_${dest.route}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_screen_content_layer")
                ) {
                    when (currentDestination) {
                        MainDestination.DASHBOARD -> {
                            DashboardScreen(
                                actor = authenticatedActor,
                                viewModel = viewModel,
                                onQuickCheckInClick = { viewModel.navigateTo(MainDestination.ATTENDANCE) },
                                onQuickDailyReportClick = { viewModel.navigateTo(MainDestination.WORK_REPORTS) }
                            )
                        }

                        MainDestination.WORK_REPORTS -> {
                            WorkReportsScreen(
                                actor = authenticatedActor,
                                viewModel = viewModel,
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }

                        MainDestination.ATTENDANCE -> {
                            AttendanceScreen(
                                actor = authenticatedActor,
                                viewModel = viewModel,
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) },
                                onSubmitAttendanceAction = { file, uriStr, noteText, capturedTime ->
                                    viewModel.submitAttendanceCheckIn(file, uriStr, noteText, capturedTime)
                                },
                                onRetryOutboxAction = {
                                    viewModel.retryAttendanceOutbox()
                                }
                            )
                        }

                        MainDestination.WORKERS -> {
                            WorkersAndDirectEntryScreen(
                                actor = authenticatedActor,
                                viewModel = viewModel,
                                onOpenNewEntryScreen = { viewModel.navigateTo(MainDestination.NEW_ENTRY) },
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }

                        MainDestination.RECRUITMENT -> {
                            RecruitmentPostsScreen(
                                viewModel = viewModel
                            )
                        }

                        MainDestination.NEW_ENTRY -> {
                            if (canCreateEntry) {
                                DirectEntryNewWorkersScreen(
                                    actor = authenticatedActor,
                                    viewModel = viewModel,
                                    onNavigateToWorkersList = {
                                        viewModel.navigateTo(MainDestination.WORKERS)
                                    },
                                    onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                                )
                            } else {
                                LaunchedEffect(authenticatedActor.appUserId) {
                                    viewModel.navigateTo(MainDestination.DASHBOARD)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = bannerMessage != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(20f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("floating_status_banner_overlay")
                ) {
                    bannerMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = 6.dp,
                            color = if (bannerIsError) SemanticColors.ErrorLight else SemanticColors.SuccessLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                if (bannerIsError && hasFailedBackgroundUploads) {
                                    TextButton(
                                        onClick = { viewModel.retryFailedBackgroundUploads() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("banner_retry_background_upload_button")
                                    ) {
                                        Text(
                                            text = "Thử lại",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.clearBanner() },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("dismiss_status_banner_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Đóng thông báo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (reminderDialogOpen) {
            TaskReminderDialog(
                taskReminderManager = taskReminderManager,
                onDismiss = { reminderDialogOpen = false }
            )
        }
    }
}
