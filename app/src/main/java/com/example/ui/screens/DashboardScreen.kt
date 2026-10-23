package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActor
import com.example.data.DuplicateCccdGroupItem
import com.example.data.TeamReportSnapshot
import com.example.data.WorkforceDeepMetrics
import com.example.ui.MiniBiViewModel
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalMiniBiPalette
import com.example.ui.theme.SemanticColors

@Composable
fun DashboardScreen(
    actor: AppActor,
    viewModel: MiniBiViewModel,
    onQuickCheckInClick: () -> Unit,
    onQuickDailyReportClick: () -> Unit
) {
    val workforceDeep by viewModel.workforceDeep.collectAsStateWithLifecycle()
    val workforceLoading by viewModel.workforceLoading.collectAsStateWithLifecycle()
    val workforceError by viewModel.workforceError.collectAsStateWithLifecycle()

    val teamReport by viewModel.teamReport.collectAsStateWithLifecycle()
    val teamReportLoading by viewModel.teamReportLoading.collectAsStateWithLifecycle()
    val teamReportError by viewModel.teamReportError.collectAsStateWithLifecycle()

    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val duplicateLoading by viewModel.duplicateLoading.collectAsStateWithLifecycle()
    val duplicateError by viewModel.duplicateError.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add("Báo cáo tổng quát")
        if (actor.hasScopeKind("all")) {
            add("Báo cáo chuyên sâu")
        }
        if (actor.hasScopeKind("all") || actor.hasScopeKind("team")) {
            add("Báo cáo theo nhóm")
        }
        add("Cảnh báo CCCD trùng (${duplicateGroups.size})")
    }

    val safeTabIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ExecutiveBiHeaderCard(
                actor = actor,
                onQuickCheckInClick = onQuickCheckInClick,
                onQuickDailyReportClick = onQuickDailyReportClick
            )
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = safeTabIndex,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = safeTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (safeTabIndex == index)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .testTag("dashboard_tab_$index"),
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (safeTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (safeTabIndex == index)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        val currentTabLabel = tabs.getOrNull(safeTabIndex).orEmpty()
        when {
            currentTabLabel.startsWith("Báo cáo tổng quát") -> {
                item {
                    DashboardStatusCard(
                        title = "Chưa hỗ trợ trên ứng dụng",
                        subtitle = "Báo cáo tổng quát tuyển dụng hiện mới được kết xuất trực tiếp trên Web Server và chưa mở JSON API riêng cho ứng dụng di động. Vui lòng chuyển sang tab Báo cáo chuyên sâu, Báo cáo theo nhóm hoặc Cảnh báo CCCD trùng."
                    )
                }
            }

            currentTabLabel.startsWith("Báo cáo chuyên sâu") -> {
                item {
                    when {
                        workforceLoading -> LoadingSectionCard("Đang tải báo cáo chuyên sâu lực lượng lao động…")
                        workforceError != null -> DashboardStatusCard(
                            title = "Không thể tải báo cáo chuyên sâu",
                            subtitle = workforceError.orEmpty()
                        )
                        workforceDeep == null -> DashboardStatusCard(
                            title = "Chưa có dữ liệu báo cáo chuyên sâu",
                            subtitle = "Dữ liệu lực lượng lao động từ máy chủ sẽ hiển thị tại đây khi sẵn sàng."
                        )
                        else -> WorkforceDeepReportSection(metrics = workforceDeep!!)
                    }
                }
            }

            currentTabLabel.startsWith("Báo cáo theo nhóm") -> {
                item {
                    when {
                        teamReportLoading -> LoadingSectionCard("Đang tải báo cáo theo nhóm…")
                        teamReportError != null -> DashboardStatusCard(
                            title = "Không thể tải báo cáo theo nhóm",
                            subtitle = teamReportError.orEmpty()
                        )
                        teamReport == null -> DashboardStatusCard(
                            title = "Chưa có dữ liệu báo cáo nhóm",
                            subtitle = "Chọn nhóm tuyển dụng hợp lệ hoặc làm mới dữ liệu từ máy chủ."
                        )
                        else -> TeamReportDetailCard(snapshot = teamReport!!)
                    }
                }
            }

            else -> {
                when {
                    duplicateLoading -> item {
                        LoadingSectionCard("Đang tải danh sách cảnh báo CCCD trùng…")
                    }
                    duplicateError != null -> item {
                        DashboardStatusCard(
                            title = "Không thể tải cảnh báo CCCD trùng",
                            subtitle = duplicateError.orEmpty()
                        )
                    }
                    duplicateGroups.isEmpty() -> item {
                        DashboardStatusCard(
                            title = "Không có cảnh báo CCCD trùng",
                            subtitle = "Máy chủ hiện không ghi nhận nhóm hồ sơ trùng CCCD nào trong phạm vi của bạn."
                        )
                    }
                    else -> {
                        items(duplicateGroups, key = { "${it.cccdLast4}_${it.firstWorkDate}" }) { group ->
                            DuplicateCccdGroupCard(group = group)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutiveBiHeaderCard(
    actor: AppActor,
    onQuickCheckInClick: () -> Unit,
    onQuickDailyReportClick: () -> Unit
) {
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
            Column {
                if (actor.scopes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Phạm vi máy chủ: " + actor.scopes.map { it.kind }.distinct().joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = "Trung tâm Điều hành Tuyển dụng",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Số liệu trực tiếp từ máy chủ Mini BI theo quyền hạn thực tế của ${actor.displayName}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
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
internal fun LoadingSectionCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun DashboardStatusCard(
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_status_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
internal fun WorkforceDeepReportSection(metrics: WorkforceDeepMetrics) {
    val palette = LocalMiniBiPalette.current
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
                    text = "Báo cáo Chuyên sâu Lực lượng Lao động (${metrics.asOfDate})",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "ĐANG LÀM (ON)",
                        value = "${metrics.onCount}",
                        unit = "NLĐ",
                        hint = "Tổng hồ sơ: ${metrics.eligibleEpisodeTotal}",
                        accentColor = SemanticColors.SuccessLight
                    )
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "CHỜ VIỆC",
                        value = "${metrics.unconfirmedCount}",
                        unit = "NLĐ",
                        hint = "TB chờ: ${metrics.avgWaitingDays?.toString() ?: "—"} ngày",
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
                        value = metrics.retention7dPct?.let { "${it}%" } ?: "—",
                        unit = "ổn định",
                        hint = "30 ngày: ${metrics.retention30dPct?.let { "${it}%" } ?: "—"}",
                        accentColor = palette.lightPrimary
                    )
                    BiMetricKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "THÂM NIÊN TB",
                        value = metrics.avgTenureDays?.toString() ?: "—",
                        unit = "ngày",
                        hint = "Đã nghỉ (OFF): ${metrics.offCount}",
                        accentColor = SemanticColors.WarningLight
                    )
                }
            }
        }

        if (metrics.leaveReasons.isNotEmpty()) {
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
                                progress = { (reason.sharePct / 100f).coerceIn(0f, 1f) },
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
}

@Composable
internal fun TeamReportDetailCard(snapshot: TeamReportSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(snapshot.bucketDisplay, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${snapshot.totalRecruited} lượt tuyển",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
            snapshot.rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${row.projectDisplay} · ${row.recruiterDisplay}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ngày ${row.businessDate} · ${row.providerTypeDisplay} · ${row.employmentTypeDisplay} · SL: ${row.recruitedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                        imageVector = if (hasActiveConflict) Icons.Default.WarningAmber else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (hasActiveConflict) SemanticColors.ErrorLight else SemanticColors.SuccessLight
                    )
                    Column {
                        Text(
                            text = "CCCD đuôi ***${group.cccdLast4} (${group.profileCount} hồ sơ)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${group.projectCount} dự án · Đang active: ${group.activeProfileCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider()
            group.episodes.forEach { ep ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${ep.displayName} (${ep.employeeCode})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${ep.projectDisplay} · Ngày đầu: ${ep.firstWorkDate} · ${ep.latestStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (ep.canSelfWithdraw && onSelfWithdraw != null) {
                        OutlinedButton(
                            onClick = { onSelfWithdraw(ep.entryId, ep.entryVersion) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Rút hồ sơ", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
