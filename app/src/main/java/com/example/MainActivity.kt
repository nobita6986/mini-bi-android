package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import com.example.ui.MainDestination
import com.example.ui.MiniBiViewModel
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DirectEntryNewWorkersScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.WorkReportsScreen
import com.example.ui.screens.WorkersAndDirectEntryScreen
import com.example.ui.theme.LocalMiniBiPalette
import com.example.ui.theme.MiniBiThemes
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SemanticColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

@Composable
fun MiniBiAppRoot(viewModel: MiniBiViewModel) {
    val sessionBootstrapping by viewModel.sessionBootstrapping.collectAsStateWithLifecycle()
    val actor by viewModel.currentActor.collectAsStateWithLifecycle()
    val themeId by viewModel.themeId.collectAsStateWithLifecycle()
    val loginBusy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val savedAccounts by viewModel.savedAccounts.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val bannerIsError by viewModel.statusBannerIsError.collectAsStateWithLifecycle()
    val hasFailedBackgroundUploads by viewModel.hasFailedBackgroundUploads.collectAsStateWithLifecycle()
    val palette = LocalMiniBiPalette.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (sessionBootstrapping) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, palette.lightTopBarBorder),
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hrpartner_logo),
                            contentDescription = "HRPartner",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Đang xác thực phiên làm việc với máy chủ…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Box(modifier = Modifier.padding(innerPadding)) {
                LoginScreen(
                    busy = loginBusy,
                    error = loginError,
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

    val canCreateEntry = authenticatedActor.hasCapability("entry_create") &&
        authenticatedActor.hasCapability("submission_create")

    val navDestinations = buildList {
        add(Triple(MainDestination.DASHBOARD, Icons.Default.Analytics, Icons.Outlined.Analytics))
        add(Triple(MainDestination.WORK_REPORTS, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment))
        add(Triple(MainDestination.ATTENDANCE, Icons.Default.CameraAlt, Icons.Outlined.CameraAlt))
        add(Triple(MainDestination.WORKERS, Icons.Default.Groups, Icons.Outlined.Groups))
        if (canCreateEntry) {
            add(Triple(MainDestination.NEW_ENTRY, Icons.Default.PersonAdd, Icons.Outlined.PersonAdd))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(304.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.lightTopBar)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, palette.lightTopBarBorder),
                            modifier = Modifier.size(46.dp)
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
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "BI",
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

                    HorizontalDivider(color = palette.lightTopBarBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = authenticatedActor.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        authenticatedActor.displayIdentifier?.let { identifierText ->
                            Text(
                                text = identifierText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Phạm vi: $localizedScopes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.lightTopBarBorder)
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
                            viewModel.navigateTo(dest)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_item_${dest.route}")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

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

                HorizontalDivider()

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Đăng xuất",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Đăng xuất",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .padding(bottom = 12.dp)
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
                    color = palette.lightTopBar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("open_drawer_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Mở menu điều hướng",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, palette.lightTopBarBorder),
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable { scope.launch { drawerState.open() } }
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
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "BI",
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
                                    text = "${currentDestination.label} · ${authenticatedActor.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { viewModel.logout() },
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("topbar_logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Đăng xuất",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Đăng xuất",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.lightTopBarBorder)
                    }
                }
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        navDestinations.forEach { (dest, filledIcon, outlinedIcon) ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) filledIcon else outlinedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("bottom_nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }

                        MainDestination.NEW_ENTRY -> {
                            DirectEntryNewWorkersScreen(
                                actor = authenticatedActor,
                                viewModel = viewModel,
                                onNavigateToWorkersList = {
                                    viewModel.navigateTo(MainDestination.WORKERS)
                                },
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
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
    }
}
