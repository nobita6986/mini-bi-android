package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainDestination
import com.example.ui.MiniBiViewModel
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DirectEntryNewWorkersScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.WorkReportsScreen
import com.example.ui.screens.WorkersAndDirectEntryScreen
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

            MyApplicationTheme(themeId = themeId) {
                MiniBiAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniBiAppRoot(viewModel: MiniBiViewModel) {
    val actor by viewModel.currentActor.collectAsStateWithLifecycle()
    val themeId by viewModel.themeId.collectAsStateWithLifecycle()
    val loginBusy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val authenticatedActor = actor
    if (authenticatedActor == null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                LoginScreen(
                    busy = loginBusy,
                    error = loginError,
                    onLoginSubmit = { identifier, password ->
                        viewModel.login(identifier, password)
                    }
                )
            }
        }
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val selectedProjectFilter by viewModel.selectedProjectFilter.collectAsStateWithLifecycle()
    val selectedProviderFilter by viewModel.selectedProviderFilter.collectAsStateWithLifecycle()
    val selectedTeamFilter by viewModel.selectedTeamFilter.collectAsStateWithLifecycle()
    val highlightedKey by viewModel.highlightedDimensionKey.collectAsStateWithLifecycle()
    val gpsSnapshot by viewModel.gpsSnapshot.collectAsStateWithLifecycle()
    val gpsLoading by viewModel.gpsLoading.collectAsStateWithLifecycle()
    val submittingAttendance by viewModel.submittingAttendance.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val workReports by viewModel.workReports.collectAsStateWithLifecycle()
    val workerProfiles by viewModel.workerProfiles.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "HR PARTNER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = authenticatedActor.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = authenticatedActor.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = authenticatedActor.roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "DANH MỤC CHỨC NĂNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                val menuItems = listOf(
                    Triple(MainDestination.DASHBOARD, "Tổng quan Báo cáo", Icons.Default.Analytics),
                    Triple(MainDestination.WORK_REPORTS, "Báo cáo Công việc Hàng ngày", Icons.Default.Assignment),
                    Triple(MainDestination.ATTENDANCE, "Chấm công & Hình ảnh", Icons.Default.CameraAlt),
                    Triple(MainDestination.WORKERS, "Nhân sự (Người lao động)", Icons.Default.Groups),
                    Triple(MainDestination.NEW_ENTRY, "Cấp mới (Nhập liệu NLĐ)", Icons.Default.PersonAdd)
                )

                menuItems.forEach { (dest, label, icon) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        selected = currentDestination == dest,
                        onClick = {
                            viewModel.navigateTo(dest)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(icon, contentDescription = null) },
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniBiThemes.forEach { palette ->
                        val selected = palette.id == themeId
                        Box(
                            modifier = Modifier
                                .size(if (selected) 30.dp else 26.dp)
                                .clip(CircleShape)
                                .background(palette.lightPrimary)
                                .clickable { viewModel.setTheme(palette.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text("Đăng xuất") },
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
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentDestination.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${authenticatedActor.displayName} · ${authenticatedActor.teamName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Mở menu điều hướng"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val nextIdx = (MiniBiThemes.indexOfFirst { it.id == themeId } + 1) % MiniBiThemes.size
                                viewModel.setTheme(MiniBiThemes[nextIdx].id)
                            },
                            modifier = Modifier.testTag("cycle_theme_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Đổi màu giao diện"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.testTag("topbar_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Đăng xuất"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    val navItems = listOf(
                        Triple(MainDestination.DASHBOARD, Icons.Default.Analytics, Icons.Outlined.Analytics),
                        Triple(MainDestination.WORK_REPORTS, Icons.Default.Assignment, Icons.Outlined.Assignment),
                        Triple(MainDestination.ATTENDANCE, Icons.Default.CameraAlt, Icons.Outlined.CameraAlt),
                        Triple(MainDestination.WORKERS, Icons.Default.Groups, Icons.Outlined.Groups),
                        Triple(MainDestination.NEW_ENTRY, Icons.Default.PersonAdd, Icons.Outlined.PersonAdd)
                    )
                    navItems.forEach { (dest, filledIcon, outlinedIcon) ->
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("bottom_nav_${dest.route}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = bannerMessage != null) {
                    bannerMessage?.let { msg ->
                        Surface(
                            color = SemanticColors.SuccessLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearBanner() },
                                    modifier = Modifier.size(28.dp)
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

                Box(modifier = Modifier.weight(1f)) {
                    when (currentDestination) {
                        MainDestination.DASHBOARD -> {
                            DashboardScreen(
                                actor = authenticatedActor,
                                state = dashboardState,
                                selectedProjectFilter = selectedProjectFilter,
                                selectedProviderFilter = selectedProviderFilter,
                                selectedTeamFilter = selectedTeamFilter,
                                highlightedKey = highlightedKey,
                                onSelectProjectFilter = { viewModel.setProjectFilter(it) },
                                onSelectProviderFilter = { viewModel.setProviderFilter(it) },
                                onSelectTeamFilter = { viewModel.setTeamFilter(it) },
                                onToggleHighlight = { viewModel.toggleHighlightKey(it) },
                                onClearFilters = { viewModel.clearAllDashboardFilters() },
                                onQuickCheckInClick = { viewModel.navigateTo(MainDestination.ATTENDANCE) },
                                onQuickDailyReportClick = { viewModel.navigateTo(MainDestination.WORK_REPORTS) }
                            )
                        }

                        MainDestination.WORK_REPORTS -> {
                            WorkReportsScreen(
                                actor = authenticatedActor,
                                reports = workReports,
                                onSaveReport = { ob, calls, interested, started, notes, submitFinal ->
                                    viewModel.saveWorkReport(ob, calls, interested, started, notes, submitFinal)
                                },
                                onCreateNewDailyReport = { calls, interested, started, notes, submitFinal ->
                                    viewModel.createNewDailyReport(calls, interested, started, notes, submitFinal)
                                },
                                onReviewReport = { ob, approved, reason ->
                                    viewModel.reviewReport(ob, approved, reason)
                                },
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }

                        MainDestination.ATTENDANCE -> {
                            AttendanceScreen(
                                actor = authenticatedActor,
                                gpsSnapshot = gpsSnapshot,
                                gpsLoading = gpsLoading,
                                submitting = submittingAttendance,
                                attendanceRecords = attendanceRecords,
                                onRefreshGps = { viewModel.fetchCurrentGpsLocation() },
                                onSubmitAttendance = { type, file, uri, note ->
                                    viewModel.submitCheckInWithPhoto(type, file, uri, note) {}
                                },
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }

                        MainDestination.WORKERS -> {
                            WorkersAndDirectEntryScreen(
                                workers = workerProfiles,
                                onOpenNewEntryScreen = { viewModel.navigateTo(MainDestination.NEW_ENTRY) },
                                onProposeWorkerChange = { worker, targetLabel, proposedSummary, reason ->
                                    viewModel.proposeWorkerChange(worker, targetLabel, proposedSummary, reason)
                                },
                                onDirectEditWorker = { worker, fullName, gender, projectDisplay, employmentStatus, phoneFull, cccdFull, bankName, bankAccountNumber, bankAccountHolder, note ->
                                    viewModel.directEditWorker(
                                        worker, fullName, gender, projectDisplay, employmentStatus,
                                        phoneFull, cccdFull, bankName, bankAccountNumber, bankAccountHolder, note
                                    )
                                },
                                onShowMessage = { msg -> viewModel.showBanner(msg) },
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }

                        MainDestination.NEW_ENTRY -> {
                            DirectEntryNewWorkersScreen(
                                onSaveValidWorkers = { validDrafts ->
                                    viewModel.saveNewWorkersBatch(validDrafts)
                                },
                                onNavigateToWorkersList = {
                                    viewModel.navigateTo(MainDestination.WORKERS)
                                },
                                onBackToDashboard = { viewModel.navigateTo(MainDestination.DASHBOARD) }
                            )
                        }
                    }
                }
            }
        }
    }
}
