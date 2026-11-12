package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.RecruitmentPostCardViewModel
import com.example.data.RecruitmentPostContract
import com.example.data.RecruitmentPostDetailViewModel
import com.example.data.RecruitmentPostItem
import com.example.ui.MiniBiViewModel
import com.example.ui.components.HrpDesignTokens
import com.example.ui.components.HrpHaptics
import com.example.ui.components.HrpSkeletonCard
import com.example.ui.components.HrpStatusBadge
import com.example.ui.components.hrpCardSurface
import com.example.ui.components.hrpMutedTile
import com.example.ui.components.hrpPressableScale
import com.example.ui.theme.SemanticColors
import com.example.ui.theme.tabularNums

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecruitmentPostsScreen(
    viewModel: MiniBiViewModel
) {
    val items by viewModel.recruitmentPosts.collectAsState()
    val catalogIndustrialParks by viewModel.recruitmentIndustrialParks.collectAsState()
    val loading by viewModel.recruitmentPostsLoading.collectAsState()
    val loadingMore by viewModel.recruitmentPostsLoadingMore.collectAsState()
    val error by viewModel.recruitmentPostsError.collectAsState()
    val hasMore by viewModel.recruitmentPostsHasMore.collectAsState()
    val accessDenied by viewModel.recruitmentPostsAccessDenied.collectAsState()
    val featureUnavailable by viewModel.recruitmentPostsFeatureUnavailable.collectAsState()
    val selectedDetail by viewModel.selectedRecruitmentPostDetail.collectAsState()
    val detailLoading by viewModel.recruitmentPostDetailLoading.collectAsState()
    val detailError by viewModel.recruitmentPostDetailError.collectAsState()
    val detailBriefId by viewModel.recruitmentPostDetailBriefId.collectAsState()
    val selectedParkId by viewModel.selectedRecruitmentIndustrialParkId.collectAsState()
    val selectedLaborTypeKey by viewModel.selectedRecruitmentLaborTypeKey.collectAsState()
    val haptic = LocalHapticFeedback.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!accessDenied && !featureUnavailable && error == null) {
            if (items.isEmpty() && !loading) {
                viewModel.loadRecruitmentPosts(reset = true, autoLoadAllPages = true)
            } else if (hasMore && !loading && !loadingMore) {
                viewModel.ensureAllRecruitmentPostsLoaded(maxAutoPages = 50)
            }
        }
    }

    val cardModels = remember(items) {
        items.map { RecruitmentPostContract.toCardViewModel(it) }
    }

    val kcnJobCounts = remember(cardModels) {
        cardModels.groupingBy { it.industrialParkId.lowercase(java.util.Locale.ROOT) }.eachCount()
    }
    val laborTypeJobCounts = remember(cardModels) {
        val counts = LinkedHashMap<String, Int>()
        cardModels.forEach { card ->
            card.laborTypes
                .map { it.laborTypeKey.lowercase(java.util.Locale.ROOT) }
                .distinct()
                .forEach { key ->
                    counts[key] = (counts[key] ?: 0) + 1
                }
        }
        counts
    }

    val viCollator = remember { java.text.Collator.getInstance(java.util.Locale.forLanguageTag("vi-VN")) }
    val kcnOptions = remember(items, catalogIndustrialParks, viCollator) {
        (catalogIndustrialParks + items.map { it.industrialPark })
            .filter { it.industrialParkId.isNotBlank() && it.displayName.isNotBlank() }
            .distinctBy { it.industrialParkId.lowercase(java.util.Locale.ROOT) }
            .sortedWith { a, b -> viCollator.compare(a.displayName, b.displayName) }
    }
    val laborTypeOptions = remember(items, viCollator) {
        items
            .flatMap { it.general.laborTypes }
            .filter { it.laborTypeKey.isNotBlank() && it.displayName.isNotBlank() }
            .distinctBy { it.laborTypeKey.lowercase(java.util.Locale.ROOT) }
            .sortedWith { a, b -> viCollator.compare(a.displayName, b.displayName) }
    }

    val hasActiveFilter = !selectedParkId.isNullOrBlank() || !selectedLaborTypeKey.isNullOrBlank()
    val filteredCards = remember(cardModels, selectedParkId, selectedLaborTypeKey) {
        cardModels.filter { card ->
            val matchesPark = selectedParkId.isNullOrBlank() ||
                card.industrialParkId.equals(selectedParkId, ignoreCase = true)
            val matchesLabor = selectedLaborTypeKey.isNullOrBlank() ||
                card.laborTypes.any { it.laborTypeKey.equals(selectedLaborTypeKey, ignoreCase = true) }
            matchesPark && matchesLabor
        }
    }

    var kcnMenuExpanded by remember { mutableStateOf(false) }
    var laborMenuExpanded by remember { mutableStateOf(false) }

    val selectedParkLabel = remember(selectedParkId, kcnOptions, cardModels, kcnJobCounts) {
        val cleanId = selectedParkId?.trim().orEmpty()
        if (cleanId.isEmpty()) {
            "KCN: Tất cả"
        } else {
            val name = kcnOptions.firstOrNull { it.industrialParkId.equals(cleanId, ignoreCase = true) }?.displayName
                ?: cardModels.firstOrNull { it.industrialParkId.equals(cleanId, ignoreCase = true) }?.industrialParkName
                ?: cleanId
            val count = kcnJobCounts[cleanId.lowercase(java.util.Locale.ROOT)] ?: 0
            "KCN: $name ($count)"
        }
    }
    val selectedLaborLabel = remember(selectedLaborTypeKey, laborTypeOptions, laborTypeJobCounts) {
        val cleanKey = selectedLaborTypeKey?.trim().orEmpty()
        if (cleanKey.isEmpty()) {
            "Hình thức: Tất cả"
        } else {
            val name = laborTypeOptions.firstOrNull { it.laborTypeKey.equals(cleanKey, ignoreCase = true) }?.displayName
                ?: cleanKey
            val count = laborTypeJobCounts[cleanKey.lowercase(java.util.Locale.ROOT)] ?: 0
            "Hình thức: $name ($count)"
        }
    }

    val onRefresh: () -> Unit = {
        HrpHaptics.tick(haptic)
        viewModel.loadRecruitmentPosts(reset = true, autoLoadAllPages = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SemanticColors.BackgroundLight)
            .testTag("recruitment_posts_screen")
    ) {
        Surface(
            color = SemanticColors.SurfaceLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                    Text(
                        text = "Tin tuyển dụng",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SemanticColors.TextPrimary
                    )
                    if (cardModels.isNotEmpty()) {
                        val badgeText = if (hasActiveFilter) {
                            "${filteredCards.size}/${cardModels.size}"
                        } else {
                            "${cardModels.size}${if (hasMore) "+" else ""}"
                        }
                        HrpStatusBadge(
                            text = badgeText,
                            containerColor = SemanticColors.InfoBg,
                            contentColor = SemanticColors.InfoText
                        )
                    }
                }

                if (cardModels.isNotEmpty() || hasActiveFilter) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            RecruitmentFilterDropdownChip(
                                label = selectedParkLabel,
                                selected = !selectedParkId.isNullOrBlank(),
                                onClick = {
                                    HrpHaptics.tick(haptic)
                                    kcnMenuExpanded = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recruitment_filter_kcn")
                            )
                            DropdownMenu(
                                expanded = kcnMenuExpanded,
                                onDismissRequest = { kcnMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Tất cả KCN (${cardModels.size})",
                                            style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                            fontWeight = if (selectedParkId.isNullOrBlank()) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = if (selectedParkId.isNullOrBlank()) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        HrpHaptics.tick(haptic)
                                        kcnMenuExpanded = false
                                        viewModel.setRecruitmentIndustrialParkFilter(null)
                                    },
                                    modifier = Modifier.testTag("recruitment_filter_kcn_all")
                                )
                                kcnOptions.forEach { park ->
                                    val isSelected = selectedParkId != null &&
                                        park.industrialParkId.equals(selectedParkId, ignoreCase = true)
                                    val parkJobCount = kcnJobCounts[park.industrialParkId.lowercase(java.util.Locale.ROOT)] ?: 0
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${park.displayName} ($parkJobCount)",
                                                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            HrpHaptics.tick(haptic)
                                            kcnMenuExpanded = false
                                            viewModel.setRecruitmentIndustrialParkFilter(park.industrialParkId)
                                        },
                                        modifier = Modifier.testTag("recruitment_filter_kcn_option_${park.industrialParkId}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            RecruitmentFilterDropdownChip(
                                label = selectedLaborLabel,
                                selected = !selectedLaborTypeKey.isNullOrBlank(),
                                onClick = {
                                    HrpHaptics.tick(haptic)
                                    laborMenuExpanded = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recruitment_filter_labor_type")
                            )
                            DropdownMenu(
                                expanded = laborMenuExpanded,
                                onDismissRequest = { laborMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Tất cả hình thức (${cardModels.size})",
                                            style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                            fontWeight = if (selectedLaborTypeKey.isNullOrBlank()) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = if (selectedLaborTypeKey.isNullOrBlank()) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        HrpHaptics.tick(haptic)
                                        laborMenuExpanded = false
                                        viewModel.setRecruitmentLaborTypeFilter(null)
                                    },
                                    modifier = Modifier.testTag("recruitment_filter_labor_type_all")
                                )
                                laborTypeOptions.forEach { labor ->
                                    val isSelected = selectedLaborTypeKey != null &&
                                        labor.laborTypeKey.equals(selectedLaborTypeKey, ignoreCase = true)
                                    val laborJobCount = laborTypeJobCounts[labor.laborTypeKey.lowercase(java.util.Locale.ROOT)] ?: 0
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${labor.displayName} ($laborJobCount)",
                                                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            HrpHaptics.tick(haptic)
                                            laborMenuExpanded = false
                                            viewModel.setRecruitmentLaborTypeFilter(labor.laborTypeKey)
                                        },
                                        modifier = Modifier.testTag("recruitment_filter_labor_type_option_${labor.laborTypeKey}")
                                    )
                                }
                            }
                        }

                        if (hasActiveFilter) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SemanticColors.SurfaceSubtle,
                                border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        HrpHaptics.tick(haptic)
                                        viewModel.clearRecruitmentPostFilters()
                                    }
                                    .testTag("recruitment_filter_clear")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Xóa lọc",
                                        tint = SemanticColors.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Xóa lọc",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SemanticColors.TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = SemanticColors.SurfaceBorder)

        when {
            accessDenied -> {
                RecruitmentPostStatusBox(
                    title = "Từ chối truy cập",
                    message = error ?: "Bạn không có quyền truy cập mục Tin tuyển dụng.",
                    actionLabel = null,
                    onAction = null,
                    testTag = "recruitment_posts_access_denied"
                )
            }
            featureUnavailable -> {
                RecruitmentPostStatusBox(
                    title = "Tính năng tạm không khả dụng",
                    message = error ?: "Tính năng Tin tuyển dụng hiện tạm thời không khả dụng.",
                    actionLabel = "Thử lại",
                    onAction = onRefresh,
                    testTag = "recruitment_posts_feature_unavailable"
                )
            }
            loading && cardModels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("recruitment_posts_loading"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        HrpSkeletonCard(
                            lineCount = 4,
                            showAvatar = false
                        )
                    }
                }
            }
            error != null && cardModels.isEmpty() -> {
                RecruitmentPostStatusBox(
                    title = "Không thể tải danh sách",
                    message = error.orEmpty(),
                    actionLabel = "Thử lại",
                    onAction = onRefresh,
                    testTag = "recruitment_posts_error_box"
                )
            }
            cardModels.isEmpty() -> {
                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        RecruitmentPostStatusBox(
                            title = "Chưa có tin tuyển dụng",
                            message = "Hiện chưa có tin tuyển dụng nào đang hiệu lực.",
                            actionLabel = "Làm mới",
                            onAction = onRefresh,
                            testTag = "recruitment_posts_empty_box"
                        )
                    }
                }
            }
            filteredCards.isEmpty() -> {
                PullToRefreshBox(
                    isRefreshing = loading || loadingMore,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        RecruitmentPostStatusBox(
                            title = "Không có tin tuyển dụng phù hợp",
                            message = "Không tìm thấy tin tuyển dụng nào khớp với bộ lọc đã chọn.",
                            actionLabel = "Xóa bộ lọc",
                            onAction = {
                                HrpHaptics.tick(haptic)
                                viewModel.clearRecruitmentPostFilters()
                            },
                            testTag = "recruitment_posts_filtered_empty_box"
                        )
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = loading && cardModels.isNotEmpty(),
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("recruitment_posts_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!error.isNullOrBlank()) {
                            item(key = "inline_error") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .hrpCardSurface(
                                            containerColor = SemanticColors.ErrorBg,
                                            borderColor = SemanticColors.ErrorBorder
                                        )
                                        .padding(12.dp)
                                        .testTag("recruitment_posts_inline_error"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = error.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SemanticColors.ErrorText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            if (hasMore) {
                                                viewModel.loadMoreRecruitmentPosts()
                                            } else {
                                                onRefresh()
                                            }
                                        },
                                        modifier = Modifier.testTag("recruitment_posts_retry_button")
                                    ) {
                                        Text(
                                            text = "Thử lại",
                                            fontWeight = FontWeight.Bold,
                                            color = SemanticColors.ErrorText
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(filteredCards, key = { _, it -> it.briefId }) { idx, card ->
                            RecruitmentPostSummaryCard(
                                index = idx + 1,
                                card = card,
                                onOpenDetail = { viewModel.openRecruitmentPostDetail(card.briefId) }
                            )
                        }

                        if (hasMore) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMoreRecruitmentPosts() },
                                        enabled = !loading && !loadingMore,
                                        shape = RoundedCornerShape(HrpDesignTokens.RadiusMD),
                                        border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("recruitment_posts_load_more_button")
                                    ) {
                                        if (loadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Đang tải thêm…")
                                        } else {
                                            Text("Tải thêm", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!detailBriefId.isNullOrBlank()) {
        RecruitmentPostDetailDialog(
            briefId = detailBriefId.orEmpty(),
            item = selectedDetail,
            loading = detailLoading,
            error = detailError,
            onRetry = { viewModel.openRecruitmentPostDetail(detailBriefId.orEmpty()) },
            onDismiss = { viewModel.closeRecruitmentPostDetail() }
        )
    }
}

@Composable
private fun RecruitmentPostStatusBox(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpCardSurface()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SemanticColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SemanticColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(HrpDesignTokens.RadiusXS),
                    modifier = Modifier.testTag("recruitment_posts_retry_button")
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun RecruitmentFilterDropdownChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            SemanticColors.SurfaceLight
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                SemanticColors.SurfaceBorder
            }
        ),
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.tabularNums(),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    SemanticColors.TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    SemanticColors.TextSecondary
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecruitmentPostSummaryCard(
    index: Int,
    card: RecruitmentPostCardViewModel,
    onOpenDetail: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val locationLine = remember(card.locationDisplay, card.customerNameDisplay, card.projectName) {
        val cust = card.customerNameDisplay.trim()
        val proj = card.projectName.trim()
        if (cust.isNotEmpty() &&
            cust != RecruitmentPostContract.UNPROVIDED_LABEL &&
            !cust.equals(proj, ignoreCase = true)
        ) {
            "${card.locationDisplay} · $cust"
        } else {
            card.locationDisplay
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpPressableScale(interactionSource)
            .hrpCardSurface()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                HrpHaptics.tick(haptic)
                onOpenDetail()
            }
            .testTag("recruitment_post_card_${card.briefId}")
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tier 1: Numbered Project Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.titleMedium.tabularNums(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("recruitment_post_index_${card.briefId}")
            )
            Text(
                text = card.projectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Tier 2: Highlighted Salary & Income Range Banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SemanticColors.SuccessBg.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, SemanticColors.SuccessLight.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = SemanticColors.SuccessText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = card.baseSalaryAndIncomeDisplay,
                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                    fontWeight = FontWeight.Bold,
                    color = SemanticColors.SuccessText,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Tier 3: Location & Compact Quota/Labor Pills
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SemanticColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = locationLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = SemanticColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SemanticColors.SurfaceSubtle
                ) {
                    Text(
                        text = card.laborTypesDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SemanticColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "SL: ${card.expectedHeadcountDisplay}",
                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                if (isProvidedValue(card.genderAndAgeDisplay)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = SemanticColors.SurfaceSubtle
                    ) {
                        Text(
                            text = card.genderAndAgeDisplay,
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = SemanticColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Tier 4: Footer (Contact Person on left + View Detail button on right)
        HorizontalDivider(color = SemanticColors.SurfaceBorder.copy(alpha = 0.7f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Phụ trách: ${card.contactPersonDisplay}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = SemanticColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    HrpHaptics.tick(haptic)
                    onOpenDetail()
                },
                shape = RoundedCornerShape(HrpDesignTokens.RadiusXS),
                border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("recruitment_post_detail_btn_${card.briefId}")
            ) {
                Text(
                    text = "Xem chi tiết",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun isProvidedValue(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.isNotEmpty() &&
        trimmed != RecruitmentPostContract.UNPROVIDED_LABEL &&
        trimmed != "Chưa cập nhật"
}

private fun extractDialablePhone(rawPhone: String): String? {
    if (!isProvidedValue(rawPhone)) return null
    val cleaned = rawPhone.filter { it.isDigit() || it == '+' }
    return cleaned.takeIf { it.count { ch -> ch.isDigit() } >= 7 }
}

@Composable
private fun RecruitmentPostDetailDialog(
    briefId: String,
    item: RecruitmentPostItem?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val detail: RecruitmentPostDetailViewModel? = remember(item) {
        item?.let { RecruitmentPostContract.toDetailViewModel(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(HrpDesignTokens.RadiusCard),
            color = SemanticColors.SurfaceLight,
            border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 8.dp)
                .testTag("recruitment_post_detail_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = detail?.section1General?.projectName ?: "Chi tiết tin tuyển dụng",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SemanticColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("recruitment_post_detail_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = SemanticColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = SemanticColors.SurfaceBorder)

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                                .testTag("recruitment_post_detail_loading"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .testTag("recruitment_post_detail_error"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SemanticColors.ErrorText,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(HrpDesignTokens.RadiusXS),
                                modifier = Modifier.testTag("recruitment_post_detail_retry_btn")
                            ) {
                                Text("Thử lại")
                            }
                        }
                    }
                    detail != null -> {
                        val s1 = detail.section1General
                        val s2 = detail.section2CandidateRequirements
                        val s3 = detail.section3CompensationBenefits
                        val s4 = detail.section4ApplicationDocuments
                        val dialablePhone = remember(s1.contactPhoneDisplay) {
                            extractDialablePhone(s1.contactPhoneDisplay)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Ultra-compact Hero Summary Strip (~46dp) to keep Sections 1..3 inside initial viewport
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SemanticColors.SuccessBg.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, SemanticColors.SuccessLight.copy(alpha = 0.28f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Thu nhập: ${s3.incomeRangeDisplay} · LCB: ${s3.baseSalaryVndDisplay}",
                                            style = MaterialTheme.typography.labelMedium.tabularNums(),
                                            fontWeight = FontWeight.Bold,
                                            color = SemanticColors.SuccessText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (dialablePhone != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = SemanticColors.SurfaceLight,
                                                border = BorderStroke(1.dp, SemanticColors.SuccessBorder),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        runCatching {
                                                            val intent = Intent(
                                                                Intent.ACTION_DIAL,
                                                                Uri.parse("tel:$dialablePhone")
                                                            )
                                                            context.startActivity(intent)
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Phone,
                                                        contentDescription = null,
                                                        tint = SemanticColors.SuccessText,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = s1.contactPhoneDisplay,
                                                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                                                        fontWeight = FontWeight.Bold,
                                                        color = SemanticColors.SuccessText,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${s1.industrialParkName}, ${s1.provinceName} · ${s1.laborTypesDisplay} · SL: ${s1.expectedHeadcountDisplay}",
                                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                                        color = SemanticColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            RecruitmentDetailSectionCard(title = "1. Thông tin chung dự án") {
                                RecruitmentCompactPairRow(
                                    leftLabel = "Khách hàng",
                                    leftValue = s1.customerNameDisplay,
                                    rightLabel = "Khu vực",
                                    rightValue = "${s1.industrialParkName}, ${s1.provinceName}"
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Hình thức",
                                    leftValue = s1.laborTypesDisplay,
                                    rightLabel = "Số lượng",
                                    rightValue = s1.expectedHeadcountDisplay
                                )
                                RecruitmentCardMetaRow("Công việc", s1.jobDescriptionDisplay)
                                RecruitmentCompactPairRow(
                                    leftLabel = "Môi trường",
                                    leftValue = s1.environmentTypesDisplay,
                                    rightLabel = "Tư thế",
                                    rightValue = s1.workPosturesDisplay
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Ca làm",
                                    leftValue = s1.shiftPatternDisplay,
                                    rightLabel = "Chi tiết ca",
                                    rightValue = s1.shiftDetailsDisplay
                                )
                                if (isProvidedValue(s1.manufacturingIndustryDisplay) || isProvidedValue(s1.investmentOriginDisplay)) {
                                    RecruitmentCompactPairRow(
                                        leftLabel = "Ngành nghề",
                                        leftValue = s1.manufacturingIndustryDisplay,
                                        rightLabel = "Vốn đầu tư",
                                        rightValue = s1.investmentOriginDisplay
                                    )
                                }
                                if (isProvidedValue(s1.supplyScheduleTextDisplay)) {
                                    RecruitmentCardMetaRow("Lịch cung ứng", s1.supplyScheduleTextDisplay)
                                }
                                RecruitmentCompactPairRow(
                                    leftLabel = "Người phụ trách",
                                    leftValue = s1.contactPersonDisplay,
                                    rightLabel = "Số điện thoại",
                                    rightValue = s1.contactPhoneDisplay
                                )
                            }

                            RecruitmentDetailSectionCard(title = "2. Yêu cầu ứng viên") {
                                RecruitmentCompactPairRow(
                                    leftLabel = "Độ tuổi",
                                    leftValue = s2.ageRangeDisplay,
                                    rightLabel = "Giới tính",
                                    rightValue = s2.genderDisplay
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Học vấn",
                                    leftValue = s2.minimumEducationDisplay,
                                    rightLabel = "Kinh nghiệm",
                                    rightValue = s2.experienceRequiredDisplay
                                )
                                if (isProvidedValue(s2.experienceDetailsDisplay)) {
                                    RecruitmentCardMetaRow("Chi tiết kinh nghiệm", s2.experienceDetailsDisplay)
                                }
                                RecruitmentCompactPairRow(
                                    leftLabel = "Nhận NLĐ cũ",
                                    leftValue = s2.acceptsFormerWorkersDisplay,
                                    rightLabel = "Kiểm tra đầu vào",
                                    rightValue = s2.entryTestFormatDisplay
                                )
                                if (isProvidedValue(s2.otherRequirementsDisplay)) {
                                    RecruitmentCardMetaRow("Yêu cầu khác", s2.otherRequirementsDisplay)
                                }
                            }

                            RecruitmentDetailSectionCard(title = "3. Lương, thưởng và phúc lợi") {
                                RecruitmentCompactPairRow(
                                    leftLabel = "Lương cơ bản",
                                    leftValue = s3.baseSalaryVndDisplay,
                                    rightLabel = "Thu nhập dự kiến",
                                    rightValue = s3.incomeRangeDisplay
                                )
                                if (isProvidedValue(s3.baseSalaryConditionsDisplay)) {
                                    RecruitmentCardMetaRow("Điều kiện lương cơ bản", s3.baseSalaryConditionsDisplay)
                                }
                                if (isProvidedValue(s3.attendanceBonusVndDisplay)) {
                                    RecruitmentCardMetaRow("Thưởng chuyên cần", s3.attendanceBonusVndDisplay)
                                }
                                if (isProvidedValue(s3.attendanceBonusConditionsDisplay)) {
                                    RecruitmentCardMetaRow("Điều kiện chuyên cần", s3.attendanceBonusConditionsDisplay)
                                }
                                if (isProvidedValue(s3.livingAllowanceVndDisplay)) {
                                    RecruitmentCardMetaRow("Phụ cấp đời sống", s3.livingAllowanceVndDisplay)
                                }
                                if (isProvidedValue(s3.livingAllowanceConditionsDisplay)) {
                                    RecruitmentCardMetaRow("Điều kiện PC đời sống", s3.livingAllowanceConditionsDisplay)
                                }
                                if (isProvidedValue(s3.seniorityAllowanceVndDisplay)) {
                                    RecruitmentCardMetaRow("Phụ cấp thâm niên", s3.seniorityAllowanceVndDisplay)
                                }
                                if (isProvidedValue(s3.seniorityAllowanceConditionsDisplay)) {
                                    RecruitmentCardMetaRow("Điều kiện PC thâm niên", s3.seniorityAllowanceConditionsDisplay)
                                }
                                if (isProvidedValue(s3.eyesightAllowanceVndDisplay)) {
                                    RecruitmentCardMetaRow("Phụ cấp độc hại/KHV", s3.eyesightAllowanceVndDisplay)
                                }
                                if (isProvidedValue(s3.eyesightAllowanceConditionsDisplay)) {
                                    RecruitmentCardMetaRow("Điều kiện PC độc hại/KHV", s3.eyesightAllowanceConditionsDisplay)
                                }
                                if (isProvidedValue(s3.otherAllowanceTextDisplay)) {
                                    RecruitmentCardMetaRow("Phụ cấp khác", s3.otherAllowanceTextDisplay)
                                }
                                RecruitmentCompactPairRow(
                                    leftLabel = "Ngày trả lương",
                                    leftValue = s3.salaryPaymentScheduleDisplay,
                                    rightLabel = "Ứng lương",
                                    rightValue = s3.salaryAdvanceAvailableDisplay
                                )
                                if (isProvidedValue(s3.salaryAdvanceAmountVndDisplay) || isProvidedValue(s3.salaryAdvanceScheduleDisplay)) {
                                    RecruitmentCompactPairRow(
                                        leftLabel = "Mức ứng lương",
                                        leftValue = s3.salaryAdvanceAmountVndDisplay,
                                        rightLabel = "Lịch ứng lương",
                                        rightValue = s3.salaryAdvanceScheduleDisplay
                                    )
                                }
                                RecruitmentCompactPairRow(
                                    leftLabel = "Lương tuần",
                                    leftValue = s3.weeklySalaryAvailableDisplay,
                                    rightLabel = "Lịch lương tuần",
                                    rightValue = s3.weeklySalaryPaymentScheduleDisplay
                                )
                            }

                            RecruitmentDetailSectionCard(title = "4. Hồ sơ ứng viên cần chuẩn bị") {
                                RecruitmentDocChecklistRow(
                                    label = "CCCD gốc & bản sao",
                                    required = s4.requireOriginalCccdAndCopy,
                                    value = s4.requireOriginalCccdAndCopyDisplay
                                )
                                RecruitmentDocChecklistRow(
                                    label = "Sơ yếu lý lịch công chứng",
                                    required = s4.requireCertifiedResume,
                                    value = s4.requireCertifiedResumeDisplay
                                )
                                RecruitmentDocChecklistRow(
                                    label = "Giấy khám sức khỏe (6 tháng)",
                                    required = s4.requireHealthCertificateWithin6Months,
                                    value = s4.requireHealthCertificateWithin6MonthsDisplay
                                )
                                RecruitmentDocChecklistRow(
                                    label = "Ảnh 3x4",
                                    required = s4.requirePhoto3x4,
                                    value = s4.requirePhoto3x4Display
                                )
                                RecruitmentDocChecklistRow(
                                    label = "Bằng cấp/chứng chỉ",
                                    required = s4.requireDegreeOrCertificate,
                                    value = s4.requireDegreeOrCertificateDisplay
                                )
                                RecruitmentDocChecklistRow(
                                    label = "Sổ BHXH",
                                    required = s4.requireSocialInsuranceBook,
                                    value = s4.requireSocialInsuranceBookDisplay
                                )
                                RecruitmentDocChecklistRow(
                                    label = "Giấy tờ khác",
                                    required = s4.requireOtherDocuments,
                                    value = s4.requireOtherDocumentsDisplay
                                )
                                if (isProvidedValue(s4.otherDocumentsTextDisplay)) {
                                    RecruitmentCardMetaRow("Chi tiết giấy tờ khác", s4.otherDocumentsTextDisplay)
                                }
                            }

                            RecruitmentDetailSectionCard(title = "5. Đơn giá ca làm & bảo hộ") {
                                RecruitmentCompactPairRow(
                                    leftLabel = "Ca ngày 8h",
                                    leftValue = s3.dayShift8hVndDisplay,
                                    rightLabel = "Ca ngày 11h",
                                    rightValue = s3.dayShift11hVndDisplay
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Ca đêm 8h",
                                    leftValue = s3.nightShift8hVndDisplay,
                                    rightLabel = "Ca đêm 11h",
                                    rightValue = s3.nightShift11hVndDisplay
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Chủ nhật ngày 11h",
                                    leftValue = s3.sundayDay11hVndDisplay,
                                    rightLabel = "Chủ nhật đêm 11h",
                                    rightValue = s3.sundayNight11hVndDisplay
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Ngày lễ ngày 11h",
                                    leftValue = s3.holidayDay11hVndDisplay,
                                    rightLabel = "Ngày lễ đêm 11h",
                                    rightValue = s3.holidayNight11hVndDisplay
                                )
                                RecruitmentCompactPairRow(
                                    leftLabel = "Đồng phục/bảo hộ",
                                    leftValue = s3.ppeProvidedDisplay,
                                    rightLabel = "Chi tiết bảo hộ",
                                    rightValue = s3.ppeDetailsDisplay
                                )
                                if (isProvidedValue(s3.otherBenefitsDisplay)) {
                                    RecruitmentCardMetaRow("Phúc lợi khác", s3.otherBenefitsDisplay)
                                }
                            }

                            RecruitmentDetailSectionCard(
                                title = "6. Thông tin đăng tải & cập nhật",
                                modifier = Modifier.testTag("recruitment_post_detail_metadata")
                            ) {
                                Text(
                                    text = "Người đăng: ${detail.createdByDisplayName} · ${detail.publishedAtDisplay}",
                                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                                    color = SemanticColors.TextSecondary
                                )
                                Text(
                                    text = "Người cập nhật: ${detail.updatedByDisplayName} · ${detail.updatedAtDisplay}",
                                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                                    color = SemanticColors.TextSecondary
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
private fun RecruitmentDetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hrpMutedTile()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun RecruitmentCompactPairRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SemanticColors.TextMuted)) {
                    append("$leftLabel: ")
                }
                withStyle(SpanStyle(color = SemanticColors.TextPrimary, fontWeight = FontWeight.Medium)) {
                    append(leftValue)
                }
            },
            style = MaterialTheme.typography.labelSmall.tabularNums(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SemanticColors.TextMuted)) {
                    append("$rightLabel: ")
                }
                withStyle(SpanStyle(color = SemanticColors.TextPrimary, fontWeight = FontWeight.Medium)) {
                    append(rightValue)
                }
            },
            style = MaterialTheme.typography.labelSmall.tabularNums(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecruitmentCardMetaRow(
    label: String,
    value: String
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = SemanticColors.TextSecondary)) {
                append("$label: ")
            }
            withStyle(SpanStyle(color = SemanticColors.TextPrimary, fontWeight = FontWeight.Medium)) {
                append(value)
            }
        },
        style = MaterialTheme.typography.labelSmall.tabularNums(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RecruitmentDocChecklistRow(
    label: String,
    required: Boolean,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SemanticColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        HrpStatusBadge(
            text = value,
            containerColor = if (required) SemanticColors.SuccessBg else SemanticColors.SurfaceLight,
            contentColor = if (required) SemanticColors.SuccessText else SemanticColors.TextMuted
        )
    }
}
