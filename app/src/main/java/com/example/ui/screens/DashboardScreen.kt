package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppActor
import com.example.data.BiBucketItem
import com.example.data.DuplicateCccdAlert
import com.example.data.ProjectProviderMixItem
import com.example.data.WorkforceDeepMetrics
import com.example.ui.DashboardComputedState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalMiniBiPalette
import com.example.ui.theme.SemanticColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    actor: AppActor,
    state: DashboardComputedState,
    selectedProjectFilter: String?,
    selectedProviderFilter: String?,
    selectedTeamFilter: String?,
    highlightedKey: String?,
    onSelectProjectFilter: (String?) -> Unit,
    onSelectProviderFilter: (String?) -> Unit,
    onSelectTeamFilter: (String?) -> Unit,
    onToggleHighlight: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onQuickCheckInClick: () -> Unit,
    onQuickDailyReportClick: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Báo cáo tổng quát",
        "Báo cáo chuyên sâu",
        "Báo cáo theo nhóm",
        "Cảnh báo CCCD trùng (${state.duplicateCccdAlerts.size})"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Executive Header + Quick Remote HR Actions (Attendance + Daily Report)
        item {
            ExecutiveBiHeaderCard(
                actor = actor,
                state = state,
                onQuickCheckInClick = onQuickCheckInClick,
                onQuickDailyReportClick = onQuickDailyReportClick
            )
        }

        // 2. BoD Dashboard Tabs (matching `src/components/dashboard/bod-dashboard-tabs.tsx`)
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedTabIndex == index)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .testTag("dashboard_tab_$index"),
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        // 3. Interactive Filter Toolbar + Period Comparison Banner
        item {
            BiFilterBar(
                selectedProjectFilter = selectedProjectFilter,
                selectedProviderFilter = selectedProviderFilter,
                selectedTeamFilter = selectedTeamFilter,
                highlightedKey = highlightedKey,
                onSelectProviderFilter = onSelectProviderFilter,
                onClearFilters = onClearFilters
            )
        }

        when (selectedTabIndex) {
            0 -> {
                // TAB 0: General BI Overview (KPI Strip, Daily Trend, Project Donut, Recruiter Ranking, Provider Mix)
                item {
                    PeriodComparisonStrip(state = state)
                }
                item {
                    KpiSummaryGrid(state = state)
                }
                item {
                    DailyRecruitmentTrendCard(
                        dailyTrend = state.dailyTrend,
                        peakDate = state.peakDayDate,
                        peakCount = state.peakDayCount
                    )
                }
                item {
                    ProjectDonutAndLegendCard(
                        projects = state.byProject,
                        total = state.totalRecruited,
                        selectedProjectKey = selectedProjectFilter,
                        highlightedKey = highlightedKey,
                        onProjectClick = { key ->
                            onToggleHighlight(key)
                            onSelectProjectFilter(key)
                        }
                    )
                }
                item {
                    RecruiterHorizontalBarCard(
                        recruiters = state.byRecruiter,
                        highlightedKey = highlightedKey,
                        onRecruiterClick = { onToggleHighlight(it) }
                    )
                }
                item {
                    ProjectProviderMixTableCard(mixItems = state.projectProviderMix)
                }
            }

            1 -> {
                // TAB 1: Deep Workforce Analytics (WF01 - WF04 Snapshot, Balance, Waiting, Tenure, Retention, Leave Reasons)
                item {
                    WorkforceDeepReportSection(metrics = state.workforceDeep)
                }
            }

            2 -> {
                // TAB 2: Team Breakdown & Drill-down (`TeamReportView`)
                item {
                    TeamBreakdownReportSection(
                        teams = state.byTeam,
                        recruiters = state.byRecruiter,
                        selectedTeamCode = selectedTeamFilter,
                        onSelectTeam = onSelectTeamFilter
                    )
                }
            }

            3 -> {
                // TAB 3: Duplicate CCCD Self-Service & Audit (`DuplicateCccdReportPanel`)
                items(state.duplicateCccdAlerts, key = { it.cccdMasked }) { alert ->
                    DuplicateCccdCard(alert = alert)
                }
            }
        }
    }
}

@Composable
private fun ExecutiveBiHeaderCard(
    actor: AppActor,
    state: DashboardComputedState,
    onQuickCheckInClick: () -> Unit,
    onQuickDailyReportClick: () -> Unit
) {
    val palette = LocalMiniBiPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = actor.roleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = actor.teamName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Báo cáo Điều hành Tuyển dụng",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Lượt tuyển ghi nhận theo ngày, dự án, người tuyển, HRP/Vendor & loại hình.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Action Bar for Remote HR Management (GPS Check-in & Daily Work Report)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onQuickCheckInClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_attendance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chấm công GPS",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onQuickDailyReportClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_work_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Báo cáo ngày",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BiFilterBar(
    selectedProjectFilter: String?,
    selectedProviderFilter: String?,
    selectedTeamFilter: String?,
    highlightedKey: String?,
    onSelectProviderFilter: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    val hasActiveFilter = selectedProjectFilter != null ||
        selectedProviderFilter != null ||
        selectedTeamFilter != null ||
        highlightedKey != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedProviderFilter == null,
            onClick = { onSelectProviderFilter(null) },
            label = { Text("Tất cả nguồn") }
        )
        FilterChip(
            selected = selectedProviderFilter == "hrp",
            onClick = { onSelectProviderFilter("hrp") },
            label = { Text("Nguồn nội bộ HRP") }
        )
        FilterChip(
            selected = selectedProviderFilter == "vendor",
            onClick = { onSelectProviderFilter("vendor") },
            label = { Text("Nguồn đối tác Vendor") }
        )
        if (hasActiveFilter) {
            AssistChip(
                onClick = onClearFilters,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterAltOff,
                        contentDescription = "Xóa bộ lọc",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Xóa lọc") },
                modifier = Modifier.testTag("clear_dashboard_filters_chip")
            )
        }
    }
}

@Composable
private fun PeriodComparisonStrip(state: DashboardComputedState) {
    val isPositive = state.deltaPct >= 0f
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isPositive) SemanticColors.SuccessLight.copy(alpha = 0.10f)
        else SemanticColors.WarningLight.copy(alpha = 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isPositive) SemanticColors.SuccessLight.copy(alpha = 0.3f)
                else SemanticColors.WarningLight.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = if (isPositive) SemanticColors.SuccessLight else SemanticColors.WarningLight
                )
                Column {
                    Text(
                        text = "So với kỳ liền trước (10/10 – 16/10)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Kỳ hiện tại: ${state.totalRecruited} người · Kỳ trước: ${state.priorPeriodTotal} người",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isPositive) SemanticColors.SuccessLight else SemanticColors.WarningLight
            ) {
                Text(
                    text = (if (isPositive) "+" else "") + "${state.deltaPct}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun KpiSummaryGrid(state: DashboardComputedState) {
    val palette = LocalMiniBiPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BiMetricKpiCard(
                modifier = Modifier.weight(1f),
                label = "TỔNG TUYỂN MỚI",
                value = "${state.totalRecruited}",
                unit = "NLĐ",
                hint = "${state.activeHiringDays} ngày phát sinh dữ liệu",
                accentColor = palette.lightPrimary
            )
            BiMetricKpiCard(
                modifier = Modifier.weight(1f),
                label = "TRUNG BÌNH / NGÀY",
                value = "${state.avgPerActiveDay}",
                unit = "NLĐ/ngày",
                hint = "Đỉnh: ${state.peakDayCount} (${state.peakDayDate.takeLast(5)})",
                accentColor = palette.lightSecondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BiMetricKpiCard(
                modifier = Modifier.weight(1f),
                label = "TỶ TRỌNG NỘI BỘ HRP",
                value = "${state.hrpSharePct}%",
                unit = "vs Vendor ${state.vendorSharePct}%",
                hint = "Chuẩn hóa nguồn tuyển dụng",
                accentColor = palette.chartColors.getOrElse(4) { SemanticColors.SuccessLight }
            )
            BiMetricKpiCard(
                modifier = Modifier.weight(1f),
                label = "CƠ CẤU LOẠI HÌNH",
                value = "${state.officialSharePct}%",
                unit = "Chính thức",
                hint = "Thời vụ: ${state.seasonalSharePct}%",
                accentColor = palette.lightAccent
            )
        }
    }
}

@Composable
private fun BiMetricKpiCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    hint: String,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DailyRecruitmentTrendCard(
    dailyTrend: List<Pair<String, Int>>,
    peakDate: String,
    peakCount: Int
) {
    val palette = LocalMiniBiPalette.current
    val primaryColor = palette.lightPrimary
    val secondaryColor = palette.lightSecondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Xu hướng tuyển dụng theo ngày",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Biểu đồ đường & cột sản lượng NLĐ đi làm thực tế",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Đỉnh: $peakCount NLĐ (${peakDate.takeLast(5)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (dailyTrend.isNotEmpty()) {
                val maxVal = (dailyTrend.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    val count = dailyTrend.size
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.46f).coerceAtMost(32.dp.toPx())
                    val chartHeight = size.height - 22.dp.toPx()

                    // Horizontal reference grid lines
                    for (i in 0..3) {
                        val y = chartHeight * (i / 3f)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.16f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val linePath = Path()
                    dailyTrend.forEachIndexed { index, (_, value) ->
                        val ratio = value.toFloat() / maxVal
                        val barH = chartHeight * ratio
                        val centerX = index * slotWidth + slotWidth / 2f
                        val topY = chartHeight - barH

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.85f),
                                    secondaryColor.copy(alpha = 0.35f)
                                ),
                                startY = topY,
                                endY = chartHeight
                            ),
                            topLeft = Offset(centerX - barWidth / 2f, topY),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        if (index == 0) {
                            linePath.moveTo(centerX, topY)
                        } else {
                            linePath.lineTo(centerX, topY)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = palette.lightAccent,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    dailyTrend.forEachIndexed { index, (_, value) ->
                        val ratio = value.toFloat() / maxVal
                        val centerX = index * slotWidth + slotWidth / 2f
                        val topY = chartHeight - chartHeight * ratio
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(centerX, topY)
                        )
                        drawCircle(
                            color = palette.lightAccent,
                            radius = 3.5.dp.toPx(),
                            center = Offset(centerX, topY)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dailyTrend.forEach { (dateStr, value) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$value",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = dateStr.takeLast(5).replace("-", "/"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDonutAndLegendCard(
    projects: List<BiBucketItem>,
    total: Int,
    selectedProjectKey: String?,
    highlightedKey: String?,
    onProjectClick: (String) -> Unit
) {
    val palette = LocalMiniBiPalette.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Tỷ trọng theo Dự án",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Chạm vào dự án bất kỳ để lọc nhanh số liệu tương ứng",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(124.dp)) {
                        val strokeWidth = 22.dp.toPx()
                        var startAngle = -90f
                        projects.forEachIndexed { idx, item ->
                            val sweep = if (total > 0) (item.count.toFloat() / total) * 360f else 0f
                            val sliceColor = palette.chartColors[idx % palette.chartColors.size]
                            val isActive = selectedProjectKey == null || selectedProjectKey == item.key
                            drawArc(
                                color = if (isActive) sliceColor else sliceColor.copy(alpha = 0.25f),
                                startAngle = startAngle,
                                sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$total",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tổng NLĐ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEachIndexed { idx, item ->
                        val color = palette.chartColors[idx % palette.chartColors.size]
                        val isHighlighted = selectedProjectKey == item.key || highlightedKey == item.key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isHighlighted) color.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable { onProjectClick(item.key) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = item.display,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${item.count} (${(item.sharePct * 10).toInt() / 10f}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecruiterHorizontalBarCard(
    recruiters: List<BiBucketItem>,
    highlightedKey: String?,
    onRecruiterClick: (String) -> Unit
) {
    val palette = LocalMiniBiPalette.current
    val maxCount = (recruiters.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Xếp hạng Chuyên viên Tuyển dụng",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Đóng góp thực tế của từng nhân sự trong kỳ báo cáo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            recruiters.forEachIndexed { idx, item ->
                val barColor = palette.chartColors[idx % palette.chartColors.size]
                val isSelected = highlightedKey == item.key
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) barColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onRecruiterClick(item.key) }
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${item.display}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${item.count} NLĐ · ${(item.sharePct * 10).toInt() / 10f}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { item.count.toFloat() / maxCount.toFloat() },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectProviderMixTableCard(mixItems: List<ProjectProviderMixItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Ma trận Dự án × Nguồn (HRP vs Vendor)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Phân tích tỷ trọng tự tuyển nội bộ (HRP) so với nguồn cung ứng ngoài (Vendor)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            mixItems.forEach { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = row.projectDisplay,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tổng: ${row.total} NLĐ",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (row.hrpShare > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(row.hrpShare.coerceAtLeast(1f))
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        if (row.vendorShare > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(row.vendorShare.coerceAtLeast(1f))
                                    .height(10.dp)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HRP: ${row.hrpCount} (${row.hrpShare.toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Vendor: ${row.vendorCount} (${row.vendorShare.toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun WorkforceDeepReportSection(metrics: WorkforceDeepMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Báo cáo Chuyên sâu Lực lượng Lao động",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tổng hợp quân số đang làm việc, tỷ lệ gắn bó sau 7 ngày / 30 ngày, thời gian chờ nhận việc và lý do nghỉ việc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "QUÂN SỐ QUẢN LÝ",
                        value = "${metrics.activeHeadcount}",
                        unit = "NLĐ",
                        hint = "Đang đi làm: ${metrics.workingToday}",
                        accentColor = SemanticColors.SuccessLight
                    )
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "CHỜ NHẬN VIỆC",
                        value = "${metrics.waitingOnboarding}",
                        unit = "NLĐ",
                        hint = "TB chờ: ${metrics.avgWaitingDays} ngày",
                        accentColor = SemanticColors.RunningLight
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "GIỮ CHÂN 7 NGÀY",
                        value = "${metrics.retention7dPct}%",
                        unit = "ổn định",
                        hint = "30 ngày: ${metrics.retention30dPct}%",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "THÂM NIÊN TB",
                        value = "${metrics.avgTenureDays}",
                        unit = "ngày",
                        hint = "Nghỉ trong tháng: ${metrics.resignedThisMonth}",
                        accentColor = SemanticColors.WarningLight
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Phân tích Lý do Nghỉ việc",
                    style = MaterialTheme.typography.titleMedium
                )
                metrics.leaveReasons.forEach { reason ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(reason.display, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${reason.count} ca (${reason.sharePct}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono
                            )
                        }
                        LinearProgressIndicator(
                            progress = { reason.sharePct / 100f },
                            color = SemanticColors.WarningLight,
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

@Composable
private fun TeamBreakdownReportSection(
    teams: List<BiBucketItem>,
    recruiters: List<BiBucketItem>,
    selectedTeamCode: String?,
    onSelectTeam: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Báo cáo Chi tiết theo Nhóm Tuyển dụng",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Chọn một nhóm bên dưới để lọc phạm vi báo cáo của nhóm đó:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            teams.forEach { team ->
                val isSelected = selectedTeamCode == team.key
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTeam(team.key) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = team.display,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Tỷ trọng đóng góp: ${(team.sharePct * 10).toInt() / 10f}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "${team.count} NLĐ",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateCccdCard(alert: DuplicateCccdAlert) {
    val isConflict = alert.status.contains("trùng", ignoreCase = true) || alert.status == "ACTIVE_CONFLICT"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isConflict) Icons.Default.WarningAmber else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (isConflict) SemanticColors.ErrorLight else SemanticColors.SuccessLight
                    )
                    Column {
                        Text(
                            text = "${alert.workerName} · CCCD: ${alert.cccdMasked}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Phát hiện lúc: ${alert.detectedDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConflict) SemanticColors.ErrorLight.copy(alpha = 0.14f)
                    else SemanticColors.SuccessLight.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = alert.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isConflict) SemanticColors.ErrorLight else SemanticColors.SuccessLight,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "• Hồ sơ 1: ${alert.projectA} — Người tuyển: ${alert.recruiterA}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Hồ sơ 2: ${alert.projectB} — Người tuyển: ${alert.recruiterB}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
