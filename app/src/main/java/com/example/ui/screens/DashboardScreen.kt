package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActor
import com.example.data.BiBucketItem
import com.example.data.DashboardDatePreset
import com.example.data.DirectEntryContract
import com.example.data.DuplicateCccdGroupItem
import com.example.data.GeneralOverviewSnapshot
import com.example.data.TeamReportSnapshot
import com.example.data.WorkReportsContract
import com.example.data.WorkforceDeepMetrics
import com.example.ui.MiniBiViewModel
import com.example.ui.components.HrpCompactFilterChip
import com.example.ui.components.HrpDesignTokens
import com.example.ui.components.HrpSegmentedTabItem
import com.example.ui.components.HrpSegmentedTabRow
import com.example.ui.components.HrpSkeletonCard
import com.example.ui.components.HrpStatusBadge
import com.example.ui.components.hrpCardSurface
import com.example.ui.components.hrpMutedTile
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalMiniBiPalette
import com.example.ui.theme.SemanticColors
import com.example.ui.theme.tabularNums
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onQuickCheckInClick: () -> Unit = {},
    onQuickDailyReportClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val generalOverview by viewModel.generalOverview.collectAsStateWithLifecycle()
    val generalOverviewLoading by viewModel.generalOverviewLoading.collectAsStateWithLifecycle()
    val generalOverviewError by viewModel.generalOverviewError.collectAsStateWithLifecycle()

    val teamReport by viewModel.teamReport.collectAsStateWithLifecycle()
    val teamReportLoading by viewModel.teamReportLoading.collectAsStateWithLifecycle()
    val teamReportError by viewModel.teamReportError.collectAsStateWithLifecycle()
    val teamScopeOptions by viewModel.teamScopeOptions.collectAsStateWithLifecycle()

    val selectedDatePreset by viewModel.dashboardDatePreset.collectAsStateWithLifecycle()

    LaunchedEffect(actor.appUserId) {
        if (generalOverview == null && !generalOverviewLoading && generalOverviewError == null) {
            viewModel.refreshDashboardData(actor)
        }
    }

    var selectedTabKey by remember { mutableStateOf("overview") }
    val canViewTeamReport = actor.hasScopeKind("all") || actor.hasScopeKind("team")
    val segmentedTabs = remember {
        listOf(
            HrpSegmentedTabItem(
                key = "overview",
                label = "Tổng quát",
                testTag = "dashboard_tab_0"
            ),
            HrpSegmentedTabItem(
                key = "team",
                label = "Theo nhóm",
                testTag = "dashboard_tab_1"
            )
        )
    }

    val isDashboardRefreshing = if (selectedTabKey == "team") {
        teamReportLoading
    } else {
        generalOverviewLoading
    }

    PullToRefreshBox(
        isRefreshing = isDashboardRefreshing,
        onRefresh = {
            focusManager.clearFocus()
            viewModel.refreshDashboardData(actor)
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .testTag("dashboard_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HrpSegmentedTabRow(
                        items = segmentedTabs,
                        selectedKey = selectedTabKey,
                        onSelect = { key ->
                            focusManager.clearFocus()
                            selectedTabKey = key
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_tab_grid")
                    )

                    DashboardTimeFilterAndRefreshBar(
                        selectedPreset = selectedDatePreset,
                        onSelectPreset = { preset ->
                            focusManager.clearFocus()
                            viewModel.selectDashboardDatePreset(preset, actor)
                        },
                        onRefreshClick = {
                            focusManager.clearFocus()
                            viewModel.refreshDashboardData(actor)
                        }
                    )
                }
            }

            when (selectedTabKey) {
                "overview" -> {
                    item {
                        when {
                            generalOverviewLoading && generalOverview == null ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HrpSkeletonCard(lineCount = 3)
                                    HrpSkeletonCard(lineCount = 3)
                                }
                            generalOverviewError != null && generalOverview == null ->
                                DashboardStatusCard(
                                    title = "Không thể tải báo cáo tổng quát",
                                    subtitle = generalOverviewError.orEmpty()
                                )
                            generalOverview == null ->
                                DashboardStatusCard(
                                    title = "Chưa có dữ liệu báo cáo tổng quát",
                                    subtitle = "Bấm nút Làm mới ở thanh bộ lọc phía trên để tải số liệu từ máy chủ."
                                )
                            else -> GeneralOverviewSection(snapshot = generalOverview!!)
                        }
                    }
                }

                "team" -> {
                    if (!canViewTeamReport) {
                        item {
                            DashboardStatusCard(
                                title = "Báo cáo theo nhóm",
                                subtitle = "Tài khoản hiện tại không quản lý nhóm tuyển dụng. Vui lòng xem số liệu cá nhân tại tab Tổng quát."
                            )
                        }
                    } else {
                        if (teamScopeOptions.size > 1) {
                            item {
                                TeamBucketPickerCard(
                                    teamBuckets = teamScopeOptions,
                                    selectedBucketKey = teamReport?.bucketKey.orEmpty(),
                                    selectedBucketDisplay = teamReport?.bucketDisplay.orEmpty(),
                                    onSelectBucket = { key -> viewModel.loadTeamReport(key) }
                                )
                            }
                        }
                        item {
                            when {
                                teamReportLoading && teamReport == null ->
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        HrpSkeletonCard(lineCount = 3)
                                        HrpSkeletonCard(lineCount = 3)
                                    }
                                teamReportError != null -> DashboardStatusCard(
                                    title = "Không thể tải báo cáo theo nhóm",
                                    subtitle = teamReportError.orEmpty()
                                )
                                teamReport == null -> DashboardStatusCard(
                                    title = "Chưa có dữ liệu báo cáo nhóm",
                                    subtitle = if (teamScopeOptions.size > 1) {
                                        "Chọn một nhóm trong danh sách phía trên để xem số liệu tuyển dụng chi tiết."
                                    } else {
                                        "Bấm nút Làm mới ở thanh bộ lọc phía trên để tải báo cáo nhóm bạn quản lý."
                                    }
                                )
                                else -> TeamReportDetailCard(
                                    snapshot = teamReport!!,
                                    loadingMore = teamReportLoading,
                                    onLoadMore = { viewModel.loadMoreTeamDrillDown() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTimeFilterAndRefreshBar(
    selectedPreset: DashboardDatePreset,
    onSelectPreset: (DashboardDatePreset) -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_filter_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardDatePreset.entries.forEach { preset ->
                val isSelected = selectedPreset == preset
                val chipWeight = when (preset) {
                    DashboardDatePreset.ALL_TIME -> 0.80f
                    DashboardDatePreset.THIS_WEEK -> 0.98f
                    DashboardDatePreset.THIS_MONTH -> 1.05f
                    DashboardDatePreset.LAST_MONTH -> 1.17f
                }
                Surface(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    modifier = Modifier
                        .weight(chipWeight)
                        .height(32.dp)
                        .testTag("dashboard_date_preset_${preset.key}"),
                    shape = RoundedCornerShape(percent = 50),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        SemanticColors.SurfaceLight
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        } else {
                            SemanticColors.SurfaceBorder
                        }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                SemanticColors.TextSecondary
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SemanticColors.SurfaceLight,
            shadowElevation = 0.5.dp,
            border = BorderStroke(1.dp, SemanticColors.SurfaceBorder)
        ) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("dashboard_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới báo cáo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PeriodBalanceSummaryBanner(
    fromDate: String?,
    toDate: String?,
    newEpisodes: Int?,
    onInflow: Int?,
    offInflow: Int?,
    netOnChange: Int?
) {
    if (fromDate.isNullOrBlank() || toDate.isNullOrBlank() || newEpisodes == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("period_balance_summary"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Biến động trong kỳ (${DirectEntryContract.isoDateToUiDate(fromDate)} – ${DirectEntryContract.isoDateToUiDate(toDate)})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniStatColumn(
                    modifier = Modifier.weight(1f),
                    label = "Tuyển mới",
                    value = "$newEpisodes NLĐ"
                )
                MiniStatColumn(
                    modifier = Modifier.weight(1f),
                    label = "Vào làm",
                    value = "${onInflow ?: 0} NLĐ"
                )
                MiniStatColumn(
                    modifier = Modifier.weight(1f),
                    label = "Nghỉ việc",
                    value = "${offInflow ?: 0} NLĐ"
                )
                if (netOnChange != null) {
                    MiniStatColumn(
                        modifier = Modifier.weight(1f),
                        label = "Tăng/giảm",
                        value = if (netOnChange > 0) "+$netOnChange" else "$netOnChange"
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateCccdToolbarCard(
    managedOnly: Boolean,
    page: Int,
    hasMore: Boolean,
    groupCount: Int,
    loading: Boolean,
    onToggleManagedOnly: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("duplicate_cccd_toolbar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = managedOnly,
                onClick = onToggleManagedOnly,
                label = {
                    Text(
                        text = "Chỉ dự án tôi phụ trách",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            Text(
                text = "Trang $page · $groupCount nhóm",
                style = MaterialTheme.typography.labelMedium.tabularNums(),
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }

        if (page > 1 || hasMore) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = !loading && page > 1,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Trang trước",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onNextPage,
                    enabled = !loading && hasMore,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Trang sau",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun GeneralOverviewSection(snapshot: GeneralOverviewSnapshot) {
    val palette = LocalMiniBiPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("general_overview_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpCardSurface()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Báo cáo tổng quát tuyển dụng",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitleText = if (snapshot.isPeriodFiltered &&
                    !snapshot.periodFromDate.isNullOrBlank() &&
                    !snapshot.periodToDate.isNullOrBlank()
                ) {
                    "Kỳ lọc: ${DirectEntryContract.isoDateToUiDate(snapshot.periodFromDate)} - ${DirectEntryContract.isoDateToUiDate(snapshot.periodToDate)} · Phạm vi: ${snapshot.scopeDescription}"
                } else {
                    "Ngày dữ liệu: ${DirectEntryContract.isoDateToUiDate(snapshot.asOfDate)} · Phạm vi: ${snapshot.scopeDescription}"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val profileHint = when {
                snapshot.audienceKind == "team" && snapshot.teamTotal != null ->
                    "Nhóm: ${snapshot.teamTotal} · QLDA: ${snapshot.managedTotal ?: 0} · Tôi tuyển: ${snapshot.recruitedTotal ?: 0}"
                snapshot.managedTotal != null || snapshot.recruitedTotal != null ->
                    "QLDA: ${snapshot.managedTotal ?: 0} · Tôi tuyển: ${snapshot.recruitedTotal ?: 0}"
                else ->
                    "${snapshot.projectCount} dự án · ${snapshot.recruiterCount} người tuyển"
            }

            if (snapshot.isPeriodFiltered && snapshot.audienceKind == "all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "TUYỂN MỚI TRONG KỲ",
                        value = "${snapshot.totalProfiles}",
                        unit = "NLĐ",
                        hint = profileHint,
                        accentColor = palette.lightPrimary
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "VÀO LÀM TRONG KỲ",
                        value = "${snapshot.workingCount}",
                        unit = "NLĐ",
                        hint = snapshot.closingWorkingCount?.let { cw ->
                            val pct = snapshot.workingRatePct
                                ?.let { " (${String.format(Locale.US, "%.1f%%", it)})" }
                                .orEmpty()
                            "Đang làm cuối kỳ: $cw$pct"
                        } ?: "Phát sinh đi làm trong kỳ",
                        accentColor = SemanticColors.SuccessLight
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "CHỜ VIỆC TRONG KỲ",
                        value = "${snapshot.waitingCount}",
                        unit = "NLĐ",
                        hint = "Chờ việc cuối kỳ: ${snapshot.closingWaitingCount ?: 0} NLĐ",
                        accentColor = SemanticColors.RunningLight
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "NGHỈ VIỆC TRONG KỲ",
                        value = "${snapshot.leftCount}",
                        unit = "NLĐ",
                        hint = snapshot.periodNetOnChange?.let { d ->
                            val signed = if (d > 0) "+$d" else "$d"
                            "Biến động đang làm: $signed NLĐ"
                        } ?: "Nghỉ việc trong kỳ",
                        accentColor = SemanticColors.WarningLight
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "TỔNG HỒ SƠ",
                        value = "${snapshot.totalProfiles}",
                        unit = "NLĐ",
                        hint = profileHint,
                        accentColor = palette.lightPrimary
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "ĐANG LÀM",
                        value = "${snapshot.workingCount}",
                        unit = "NLĐ",
                        hint = snapshot.workingRatePct?.let {
                            "Tỷ lệ đang làm: ${String.format(Locale.US, "%.1f%%", it)}"
                        } ?: "Đang làm việc thực tế",
                        accentColor = SemanticColors.SuccessLight
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "ĐANG CHỜ VIỆC",
                        value = "${snapshot.waitingCount}",
                        unit = "NLĐ",
                        hint = "${snapshot.projectCount} dự án có nhân sự",
                        accentColor = SemanticColors.RunningLight
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "ĐÃ NGHỈ",
                        value = "${snapshot.leftCount}",
                        unit = "NLĐ",
                        hint = snapshot.retention7dPct?.let { "Giữ chân 7 ngày: ${it}%" }
                            ?: "${snapshot.recruiterCount} người tuyển phụ trách",
                        accentColor = SemanticColors.WarningLight
                    )
                }

                PeriodBalanceSummaryBanner(
                    fromDate = snapshot.periodFromDate,
                    toDate = snapshot.periodToDate,
                    newEpisodes = snapshot.periodNewEpisodes,
                    onInflow = snapshot.periodOnInflow,
                    offInflow = snapshot.periodOffInflow,
                    netOnChange = snapshot.periodNetOnChange
                )
            }

            if (snapshot.activeHiringDays > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hrpMutedTile(cornerRadius = HrpDesignTokens.RadiusMD)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    MiniStatColumn(
                        modifier = Modifier.weight(1f),
                        label = "Ngày có tuyển",
                        value = "${snapshot.activeHiringDays} ngày"
                    )
                    MiniStatColumn(
                        modifier = Modifier.weight(1f),
                        label = "TB / ngày có số",
                        value = snapshot.avgPerActiveDay?.let {
                            "${String.format(Locale.US, "%.1f", it)} NLĐ"
                        } ?: "—"
                    )
                    MiniStatColumn(
                        modifier = Modifier.weight(1f),
                        label = "Đỉnh / ngày",
                        value = snapshot.peakDailyCount?.let { "$it NLĐ" } ?: "—",
                        subValue = snapshot.peakDateIso
                            ?.takeIf { it.isNotBlank() }
                            ?.let { DirectEntryContract.isoDateToUiDate(it) }
                    )
                }
            }

            if (snapshot.totalProfiles == 0 && (!snapshot.isPeriodFiltered || (snapshot.workingCount == 0 && snapshot.leftCount == 0))) {
                Text(
                    text = if (snapshot.isPeriodFiltered) {
                        "Không có hồ sơ phát sinh trong kỳ lọc đã chọn."
                    } else {
                        "Chưa có hồ sơ người lao động nào được ghi nhận trong phạm vi hiện tại."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (snapshot.byRecruiter.isNotEmpty() &&
            (snapshot.audienceKind == "team" || snapshot.audienceKind == "all" || snapshot.byRecruiter.size > 1)
        ) {
            BucketBreakdownCard(
                title = if (snapshot.audienceKind == "team") {
                    "Đóng góp thành viên nhóm"
                } else {
                    "Tỷ trọng theo người tuyển"
                },
                subtitle = null,
                buckets = snapshot.byRecruiter,
                barColor = palette.lightPrimary
            )
        }

        if (snapshot.byProject.isNotEmpty()) {
            val leadSubtitle = snapshot.leadingProject?.let { lead ->
                "Dự án dẫn đầu: ${lead.display} (${lead.count} NLĐ · ${String.format(Locale.US, "%.1f%%", lead.sharePct)})"
            }
            BucketBreakdownCard(
                title = "Tỷ trọng theo dự án",
                subtitle = leadSubtitle,
                buckets = snapshot.byProject,
                barColor = MaterialTheme.colorScheme.primary
            )
        }

        if (snapshot.bySecondaryGroup.isNotEmpty()) {
            BucketBreakdownCard(
                title = snapshot.secondaryGroupTitle.ifBlank { "Cơ cấu chi tiết" },
                subtitle = null,
                buckets = snapshot.bySecondaryGroup,
                barColor = SemanticColors.SuccessLight
            )
        }

        if (snapshot.byProvider.isNotEmpty()) {
            BucketBreakdownCard(
                title = "Cơ cấu nguồn tuyển (HRP / Vendor)",
                subtitle = null,
                buckets = snapshot.byProvider,
                barColor = SemanticColors.RunningLight
            )
        }
    }
}

@Composable
private fun MiniStatColumn(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.tabularNums(),
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (!subValue.isNullOrBlank()) {
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.tabularNums().copy(fontSize = 10.5.sp),
                color = SemanticColors.TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BucketBreakdownCard(
    title: String,
    subtitle: String?,
    buckets: List<BiBucketItem>,
    barColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        buckets.forEach { bucket ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bucket.display,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${bucket.count} NLĐ (${String.format(Locale.US, "%.1f%%", bucket.sharePct)})",
                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                LinearProgressIndicator(
                    progress = { (bucket.sharePct / 100f).coerceIn(0f, 1f) },
                    color = barColor,
                    trackColor = SemanticColors.SurfaceSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
internal fun LoadingSectionCard(message: String) {
    HrpSkeletonCard(lineCount = 3)
}

@Composable
internal fun DashboardStatusCard(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .padding(18.dp)
            .testTag("dashboard_status_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun BiMetricKpiCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    hint: String,
    accentColor: Color
) {
    val isSemanticRatePill = hint.startsWith("Tỷ lệ đang làm:")
    Column(
        modifier = modifier
            .hrpMutedTile(cornerRadius = 14.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge.tabularNums(),
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Box(modifier = Modifier.padding(top = 6.dp)) {
            if (isSemanticRatePill) {
                val pillShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .background(SemanticColors.SuccessBg, pillShape)
                        .border(0.5.dp, SemanticColors.SuccessBorder.copy(alpha = 0.6f), pillShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall.tabularNums().copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = SemanticColors.SuccessText,
                        softWrap = true,
                        maxLines = 2
                    )
                }
            } else {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall.tabularNums().copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
internal fun WorkforceDeepReportSection(
    metrics: WorkforceDeepMetrics,
    loadingMore: Boolean = false,
    onLoadMoreProjects: (() -> Unit)? = null
) {
    val palette = LocalMiniBiPalette.current
    val hasPeriodFilter = !metrics.periodFromDate.isNullOrBlank() || !metrics.periodToDate.isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpCardSurface()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Báo cáo chuyên sâu lực lượng lao động (${DirectEntryContract.isoDateToUiDate(metrics.asOfDate)})",
                style = MaterialTheme.typography.titleMedium.tabularNums(),
                fontWeight = FontWeight.Bold
            )
            if (hasPeriodFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "TUYỂN MỚI KỲ",
                        value = "${metrics.periodNewEpisodes ?: 0}",
                        unit = "NLĐ",
                        hint = "Tích lũy: ${metrics.eligibleEpisodeTotal} NLĐ",
                        accentColor = palette.lightPrimary
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "VÀO LÀM (ON)",
                        value = "${metrics.periodOnInflow ?: 0}",
                        unit = "NLĐ",
                        hint = "Đang làm cuối kỳ: ${metrics.onCount}",
                        accentColor = SemanticColors.SuccessLight
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "NGHỈ TRONG KỲ",
                        value = "${metrics.periodOffInflow ?: 0}",
                        unit = "NLĐ",
                        hint = "Đã nghỉ cuối kỳ: ${metrics.offCount}",
                        accentColor = SemanticColors.WarningLight
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "GIỮ CHÂN 7 NGÀY",
                        value = metrics.retention7dPct?.let { "${it}%" } ?: "—",
                        unit = "ổn định",
                        hint = "30 ngày: ${metrics.retention30dPct?.let { "${it}%" } ?: "—"}",
                        accentColor = SemanticColors.RunningLight
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "ĐANG LÀM",
                        value = "${metrics.onCount}",
                        unit = "NLĐ",
                        hint = "Tổng hồ sơ: ${metrics.eligibleEpisodeTotal}",
                        accentColor = SemanticColors.SuccessLight
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "CHỜ VIỆC",
                        value = "${metrics.unconfirmedCount}",
                        unit = "NLĐ",
                        hint = "TB chờ: ${metrics.avgWaitingDays?.toString() ?: "—"} ngày",
                        accentColor = SemanticColors.RunningLight
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "GIỮ CHÂN 7 NGÀY",
                        value = metrics.retention7dPct?.let { "${it}%" } ?: "—",
                        unit = "ổn định",
                        hint = "30 ngày: ${metrics.retention30dPct?.let { "${it}%" } ?: "—"}",
                        accentColor = palette.lightPrimary
                    )
                    BiMetricKpiCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "THÂM NIÊN TB",
                        value = metrics.avgTenureDays?.toString() ?: "—",
                        unit = "ngày",
                        hint = "Đã nghỉ: ${metrics.offCount}",
                        accentColor = SemanticColors.WarningLight
                    )
                }
            }
            PeriodBalanceSummaryBanner(
                fromDate = metrics.periodFromDate,
                toDate = metrics.periodToDate,
                newEpisodes = metrics.periodNewEpisodes,
                onInflow = metrics.periodOnInflow,
                offInflow = metrics.periodOffInflow,
                netOnChange = metrics.periodNetOnChange
            )
        }

        if (metrics.byProject.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hrpCardSurface()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Phân bổ theo dự án",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                metrics.byProject.forEach { proj ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proj.display,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${proj.count} (${proj.sharePct}%)",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            fontFamily = JetBrainsMono
                        )
                    }
                }
                if (metrics.projectHasMore && onLoadMoreProjects != null) {
                    OutlinedButton(
                        onClick = onLoadMoreProjects,
                        enabled = !loadingMore,
                        shape = RoundedCornerShape(HrpDesignTokens.RadiusMD),
                        border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (loadingMore) "Đang tải thêm…" else "Tải thêm dự án")
                    }
                }
            }
        }

        if (metrics.leaveReasons.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hrpCardSurface()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Phân tích lý do nghỉ việc",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                metrics.leaveReasons.forEach { reason ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = reason.display,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${reason.count} ca (${reason.sharePct}%)",
                                style = MaterialTheme.typography.labelSmall.tabularNums(),
                                fontFamily = JetBrainsMono,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (reason.sharePct / 100f).coerceIn(0f, 1f) },
                            color = SemanticColors.WarningLight,
                            trackColor = SemanticColors.SurfaceSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamBucketPickerCard(
    teamBuckets: List<BiBucketItem>,
    selectedBucketKey: String,
    selectedBucketDisplay: String,
    onSelectBucket: (String) -> Unit
) {
    if (teamBuckets.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .padding(16.dp)
            .testTag("team_bucket_picker_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Chọn nhóm tuyển dụng",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            teamBuckets.forEach { bucket ->
                val selected = bucket.key == selectedBucketKey ||
                    bucket.display == selectedBucketDisplay ||
                    runCatching { WorkReportsContract.encodeTeamBucketKey(bucket.key) == selectedBucketKey }
                        .getOrDefault(false)
                val chipLabel = if (bucket.count > 0) {
                    "${bucket.display} (${bucket.count})"
                } else {
                    bucket.display
                }
                HrpCompactFilterChip(
                    label = chipLabel,
                    selected = selected,
                    onClick = { onSelectBucket(bucket.key) },
                    testTag = "team_bucket_chip_${bucket.key}"
                )
            }
        }
    }
}

@Composable
internal fun TeamReportDetailCard(
    snapshot: TeamReportSnapshot,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val palette = LocalMiniBiPalette.current
    var visibleDetailCount by remember(snapshot.bucketKey) { mutableIntStateOf(20) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("team_report_detail_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpCardSurface()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = snapshot.bucketDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Báo cáo hiệu suất nhóm tuyển dụng",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HrpStatusBadge(
                    text = "${snapshot.totalRecruited} NLĐ",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            val statusHint = if (snapshot.workingCount != null) {
                "Đang làm: ${snapshot.workingCount} · Chờ: ${snapshot.waitingCount ?: 0} · Nghỉ: ${snapshot.leftCount ?: 0}"
            } else {
                "Tổng nhân sự ghi nhận của nhóm"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BiMetricKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "TỔNG TUYỂN NHÓM",
                    value = "${snapshot.totalRecruited}",
                    unit = "NLĐ",
                    hint = statusHint,
                    accentColor = palette.lightPrimary
                )
                BiMetricKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "THÀNH VIÊN CÓ SỐ",
                    value = "${snapshot.activeRecruiterCount}",
                    unit = "người",
                    hint = "Có phát sinh tuyển dụng",
                    accentColor = SemanticColors.SuccessLight
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BiMetricKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "DỰ ÁN THAM GIA",
                    value = "${snapshot.activeProjectCount}",
                    unit = "dự án",
                    hint = "Có nhân sự của nhóm",
                    accentColor = SemanticColors.RunningLight
                )
                BiMetricKpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "TB / NGÀY CÓ SỐ",
                    value = snapshot.avgPerActiveDay?.let { String.format(Locale.US, "%.1f", it) } ?: "0.0",
                    unit = "NLĐ",
                    hint = if (snapshot.activeHiringDays > 0) {
                        "${snapshot.activeHiringDays} ngày có tuyển · Đỉnh: ${snapshot.peakDailyCount ?: 0}"
                    } else {
                        "Chưa phát sinh ngày tuyển"
                    },
                    accentColor = SemanticColors.WarningLight
                )
            }
        }

        if (snapshot.byRecruiter.isNotEmpty()) {
            BucketBreakdownCard(
                title = "Đóng góp của thành viên nhóm",
                subtitle = "Xếp hạng theo số lượng NLĐ tuyển được trong nhóm",
                buckets = snapshot.byRecruiter,
                barColor = palette.lightPrimary
            )
        }

        if (snapshot.byProject.isNotEmpty()) {
            BucketBreakdownCard(
                title = "Theo dự án trong nhóm",
                subtitle = null,
                buckets = snapshot.byProject,
                barColor = MaterialTheme.colorScheme.primary
            )
        }

        if (snapshot.byEmployment.isNotEmpty()) {
            BucketBreakdownCard(
                title = "Theo loại hình làm việc",
                subtitle = null,
                buckets = snapshot.byEmployment,
                barColor = SemanticColors.SuccessLight
            )
        }

        if (snapshot.byProvider.isNotEmpty()) {
            BucketBreakdownCard(
                title = "Theo nguồn tuyển (HRP / Vendor)",
                subtitle = null,
                buckets = snapshot.byProvider,
                barColor = SemanticColors.RunningLight
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpCardSurface()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chi tiết lượt tuyển trong nhóm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${snapshot.rows.size} dòng",
                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = SemanticColors.SurfaceBorder)
            if (snapshot.rows.isEmpty()) {
                Text(
                    text = "Chưa có dòng tuyển dụng chi tiết trong nhóm này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val displayedRows = snapshot.rows.take(visibleDetailCount)
                displayedRows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${row.projectDisplay} · ${row.recruiterDisplay}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ngày ${DirectEntryContract.isoDateToUiDate(row.businessDate)} · ${row.providerTypeDisplay} · ${row.employmentTypeDisplay} · SL: ${row.recruitedCount}",
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = SemanticColors.SurfaceBorder)
                }

                if (snapshot.rows.size > visibleDetailCount) {
                    val remaining = snapshot.rows.size - visibleDetailCount
                    OutlinedButton(
                        onClick = { visibleDetailCount += 30 },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Xem thêm (${remaining} dòng còn lại)")
                    }
                }
            }
            if (snapshot.nextCursor != null && onLoadMore != null) {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (loadingMore) "Đang tải thêm…" else "Tải thêm từ máy chủ")
                }
            }
        }
    }
}

@Composable
internal fun DuplicateCccdGroupCard(
    group: DuplicateCccdGroupItem,
    onSelfWithdraw: ((entryId: String, expectedVersion: Int) -> Unit)? = null
) {
    val hasActiveConflict = group.activeProfileCount > 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (hasActiveConflict) Icons.Default.WarningAmber else Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = if (hasActiveConflict) SemanticColors.ErrorLight else SemanticColors.SuccessLight
                )
                Column {
                    Text(
                        text = "CCCD đuôi ***${group.cccdLast4} (${group.profileCount} hồ sơ)",
                        style = MaterialTheme.typography.titleSmall.tabularNums(),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${group.projectCount} dự án · Đang làm: ${group.activeProfileCount}",
                        style = MaterialTheme.typography.bodySmall.tabularNums(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HrpStatusBadge(
                text = if (hasActiveConflict) "Trùng đang làm" else "Đã tách kỳ",
                containerColor = if (hasActiveConflict) SemanticColors.ErrorBg else SemanticColors.SuccessBg,
                contentColor = if (hasActiveConflict) SemanticColors.ErrorText else SemanticColors.SuccessText
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpMutedTile(cornerRadius = HrpDesignTokens.RadiusMD)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            group.episodes.forEachIndexed { idx, ep ->
                if (idx > 0) {
                    HorizontalDivider(color = SemanticColors.SurfaceBorder)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${ep.displayName} (${ep.employeeCode})",
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ep.projectDisplay} · Ngày đầu: ${DirectEntryContract.isoDateToUiDate(ep.firstWorkDate)} · ${DirectEntryContract.workStatusUiLabel(ep.latestStatus)}",
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (ep.canSelfWithdraw && onSelfWithdraw != null) {
                        OutlinedButton(
                            onClick = { onSelfWithdraw(ep.entryId, ep.entryVersion) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SemanticColors.ErrorBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Rút hồ sơ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SemanticColors.ErrorText
                            )
                        }
                    }
                }
            }
        }
    }
}
