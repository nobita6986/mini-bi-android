package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActor
import com.example.data.BULLETIN_REMOTE_API_ENABLED
import com.example.data.HrpBulletinAttachment
import com.example.data.HrpBulletinAttachmentKind
import com.example.data.HrpBulletinCategory
import com.example.data.HrpBulletinContract
import com.example.data.HrpBulletinItem
import com.example.data.HrpBulletinManager
import com.example.ui.MiniBiViewModel
import com.example.ui.components.HrpDesignTokens
import com.example.ui.components.HrpHaptics
import com.example.ui.components.HrpStatusBadge
import com.example.ui.components.hrpCardSurface
import com.example.ui.components.hrpMutedTile
import com.example.ui.theme.SemanticColors
import com.example.ui.theme.tabularNums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val HolidayCategoryColor = Color(0xFF0284C7)
private val PolicyCategoryColor = Color(0xFF15803D)
private val PdfBadgeRed = Color(0xFFDC2626)

@Composable
private fun resolveBulletinCategoryTint(category: HrpBulletinCategory): Color {
    val primary = MaterialTheme.colorScheme.primary
    return when (category) {
        HrpBulletinCategory.ALL -> primary
        HrpBulletinCategory.HR_NEWS -> primary
        HrpBulletinCategory.HOLIDAY_SCHEDULE -> HolidayCategoryColor
        HrpBulletinCategory.INTERNAL_POLICY -> PolicyCategoryColor
        HrpBulletinCategory.GENERAL -> SemanticColors.TextSecondary
    }
}

private fun resolveBulletinCategoryIcon(category: HrpBulletinCategory): ImageVector {
    return when (category) {
        HrpBulletinCategory.ALL -> Icons.Outlined.Newspaper
        HrpBulletinCategory.HR_NEWS -> Icons.Default.Groups
        HrpBulletinCategory.HOLIDAY_SCHEDULE -> Icons.Default.CalendarMonth
        HrpBulletinCategory.INTERNAL_POLICY -> Icons.Default.VerifiedUser
        HrpBulletinCategory.GENERAL -> Icons.AutoMirrored.Filled.Article
    }
}

@Composable
fun HrpBulletinDialog(
    actor: AppActor,
    bulletinManager: HrpBulletinManager,
    viewModel: MiniBiViewModel? = null,
    initialBulletinId: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val readIdsVersion by bulletinManager.readIdsVersionFlow.collectAsStateWithLifecycle()
    val notificationEnabled by bulletinManager.notificationEnabledFlow.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    var selectedCategory by remember { mutableStateOf(HrpBulletinCategory.ALL) }
    var selectedBulletinId by remember(initialBulletinId) { mutableStateOf(initialBulletinId) }
    var activeImageAttachment by remember { mutableStateOf<HrpBulletinAttachment?>(null) }
    var activePdfAttachment by remember { mutableStateOf<HrpBulletinAttachment?>(null) }

    val useRemote = BULLETIN_REMOTE_API_ENABLED && viewModel != null
    val remoteItems = viewModel?.bulletins?.collectAsStateWithLifecycle()?.value.orEmpty()
    val remoteUnreadCount = viewModel?.bulletinUnreadCount?.collectAsStateWithLifecycle()?.value ?: 0
    val remoteLoading = viewModel?.bulletinsLoading?.collectAsStateWithLifecycle()?.value ?: false
    val remoteLoadingMore = viewModel?.bulletinsLoadingMore?.collectAsStateWithLifecycle()?.value ?: false
    val remoteError = viewModel?.bulletinsError?.collectAsStateWithLifecycle()?.value
    val remoteHasMore = viewModel?.bulletinHasMore?.collectAsStateWithLifecycle()?.value ?: false
    val remoteDetail = viewModel?.selectedBulletinDetail?.collectAsStateWithLifecycle()?.value
    val remoteDetailLoading = viewModel?.bulletinDetailLoading?.collectAsStateWithLifecycle()?.value ?: false
    val remoteDetailError = viewModel?.bulletinDetailError?.collectAsStateWithLifecycle()?.value
    val markingAllRead = viewModel?.bulletinMarkingAllRead?.collectAsStateWithLifecycle()?.value ?: false

    val localMockupItems = remember(actor.appUserId, readIdsVersion) {
        if (useRemote) {
            emptyList()
        } else {
            bulletinManager.getBulletinsForActor(
                appUserId = actor.appUserId,
                category = HrpBulletinCategory.ALL
            )
        }
    }
    val allItems = if (useRemote) remoteItems else localMockupItems
    val filteredItems = remember(allItems, selectedCategory, useRemote) {
        if (useRemote || selectedCategory == HrpBulletinCategory.ALL) {
            allItems
        } else {
            allItems.filter { it.category == selectedCategory }
        }
    }
    val effectiveUnreadCount = remember(useRemote, remoteUnreadCount, allItems) {
        if (useRemote) remoteUnreadCount else allItems.count { !it.isRead }
    }
    val selectedBulletin = remember(allItems, selectedBulletinId, remoteDetail, useRemote) {
        selectedBulletinId?.let { targetId ->
            if (useRemote && remoteDetail?.bulletinId == targetId) {
                remoteDetail
            } else {
                allItems.firstOrNull { it.bulletinId == targetId }
            }
        }
    }

    LaunchedEffect(actor.appUserId, useRemote) {
        if (useRemote) {
            viewModel?.loadBulletins(
                category = selectedCategory,
                reset = true,
                actorOverride = actor,
                notifyNewIfAll = true
            )
        }
    }

    LaunchedEffect(initialBulletinId, actor.appUserId, useRemote) {
        val targetId = initialBulletinId?.trim().orEmpty()
        if (targetId.isNotEmpty()) {
            selectedBulletinId = targetId
            if (useRemote) {
                viewModel?.openBulletinDetail(targetId, actorOverride = actor)
            } else {
                bulletinManager.markBulletinRead(actor.appUserId, targetId)
            }
        }
    }

    val loadAttachmentFile: suspend (HrpBulletinAttachment) -> File = remember(viewModel, context) {
        { attachment ->
            if (viewModel != null) {
                viewModel.ensureBulletinAttachmentFile(attachment)
            } else {
                HrpBulletinContract.ensureAttachmentLocalFile(context, attachment)
            }
        }
    }

    Dialog(
        onDismissRequest = {
            when {
                activeImageAttachment != null -> activeImageAttachment = null
                activePdfAttachment != null -> activePdfAttachment = null
                selectedBulletinId != null -> {
                    selectedBulletinId = null
                    viewModel?.clearSelectedBulletinDetail()
                }
                else -> onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = selectedBulletinId != null && activeImageAttachment == null && activePdfAttachment == null) {
            selectedBulletinId = null
            viewModel?.clearSelectedBulletinDetail()
        }

        Surface(
            shape = RoundedCornerShape(HrpDesignTokens.RadiusHero),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag("hrp_bulletin_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Sheet decorative drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(SemanticColors.SurfaceBorder)
                    )
                }

                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (selectedBulletinId != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SemanticColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    HrpHaptics.tick(hapticFeedback)
                                    selectedBulletinId = null
                                    viewModel?.clearSelectedBulletinDetail()
                                }
                                .testTag("bulletin_detail_back_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Quay lại danh sách",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Danh sách",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Newspaper,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Thông báo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("bulletin_dialog_title")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedBulletinId == null) {
                            if (useRemote) {
                                Surface(
                                    shape = CircleShape,
                                    color = SemanticColors.SurfaceSubtle,
                                    border = BorderStroke(1.dp, SemanticColors.SurfaceBorder),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !remoteLoading) {
                                            HrpHaptics.tick(hapticFeedback)
                                            viewModel?.loadBulletins(
                                                category = selectedCategory,
                                                reset = true,
                                                actorOverride = actor,
                                                notifyNewIfAll = false
                                            )
                                        }
                                        .testTag("bulletin_refresh_button")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (remoteLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Làm mới bản tin",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (notificationEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    SemanticColors.SurfaceSubtle
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (notificationEnabled) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                    } else {
                                        SemanticColors.SurfaceBorder
                                    }
                                ),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        HrpHaptics.tick(hapticFeedback)
                                        val nextState = !notificationEnabled
                                        bulletinManager.setNewBulletinNotificationEnabled(nextState)
                                        if (nextState) {
                                            ensureNotificationPermissionIfNeeded()
                                            bulletinManager.notifyNewBulletinsIfNeeded(
                                                appUserId = actor.appUserId,
                                                seedItems = allItems
                                            )
                                        }
                                    }
                                    .testTag("bulletin_notification_toggle_button")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (notificationEnabled) {
                                            Icons.Default.Notifications
                                        } else {
                                            Icons.Default.NotificationsOff
                                        },
                                        contentDescription = if (notificationEnabled) {
                                            "Đang bật thông báo tin mới"
                                        } else {
                                            "Đang tắt thông báo tin mới"
                                        },
                                        tint = if (notificationEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                HrpHaptics.tick(hapticFeedback)
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                                .testTag("bulletin_close_button")
                        ) {
                            Text(
                                text = "Đóng",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SemanticColors.SurfaceBorder)
                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = selectedBulletinId,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "bulletin_sheet_view_transition",
                    modifier = Modifier.weight(1f)
                ) { detailId ->
                    if (detailId == null) {
                        BulletinListContent(
                            selectedCategory = selectedCategory,
                            items = filteredItems,
                            unreadCount = effectiveUnreadCount,
                            isLoading = useRemote && remoteLoading,
                            isLoadingMore = useRemote && remoteLoadingMore,
                            hasMore = useRemote && remoteHasMore,
                            errorMessage = if (useRemote) remoteError else null,
                            markingAllRead = markingAllRead,
                            onSelectCategory = { picked ->
                                HrpHaptics.tick(hapticFeedback)
                                selectedCategory = picked
                                if (useRemote) {
                                    viewModel?.loadBulletins(
                                        category = picked,
                                        reset = true,
                                        actorOverride = actor,
                                        notifyNewIfAll = false
                                    )
                                }
                            },
                            onRetry = {
                                HrpHaptics.tick(hapticFeedback)
                                viewModel?.loadBulletins(
                                    category = selectedCategory,
                                    reset = true,
                                    actorOverride = actor,
                                    notifyNewIfAll = false
                                )
                            },
                            onMarkAllRead = {
                                HrpHaptics.tick(hapticFeedback)
                                viewModel?.markAllBulletinsRead(actorOverride = actor)
                            },
                            onLoadMore = {
                                HrpHaptics.tick(hapticFeedback)
                                viewModel?.loadMoreBulletins(actorOverride = actor)
                            },
                            onOpenBulletin = { item ->
                                HrpHaptics.tick(hapticFeedback)
                                selectedBulletinId = item.bulletinId
                                if (useRemote) {
                                    viewModel?.openBulletinDetail(item.bulletinId, actorOverride = actor)
                                } else {
                                    bulletinManager.markBulletinRead(actor.appUserId, item.bulletinId)
                                }
                            }
                        )
                    } else {
                        val detailTarget = selectedBulletin
                        when {
                            detailTarget != null -> {
                                BulletinDetailContent(
                                    item = detailTarget,
                                    isDetailRefreshing = useRemote && remoteDetailLoading && detailTarget.contentSections.isEmpty(),
                                    loadAttachmentFile = loadAttachmentFile,
                                    onOpenImageAttachment = { att ->
                                        HrpHaptics.tick(hapticFeedback)
                                        activeImageAttachment = att
                                    },
                                    onOpenPdfAttachment = { att ->
                                        HrpHaptics.tick(hapticFeedback)
                                        activePdfAttachment = att
                                    }
                                )
                            }
                            useRemote && remoteDetailLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = remoteDetailError ?: "Không thể tải chi tiết bản tin.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SemanticColors.ErrorText,
                                            textAlign = TextAlign.Center
                                        )
                                        if (useRemote) {
                                            OutlinedButton(
                                                onClick = {
                                                    HrpHaptics.tick(hapticFeedback)
                                                    viewModel?.openBulletinDetail(detailId, actorOverride = actor)
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                            ) {
                                                Text("Thử lại")
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

        activeImageAttachment?.let { imageAttachment ->
            BulletinFullScreenImageViewerDialog(
                attachment = imageAttachment,
                loadAttachmentFile = loadAttachmentFile,
                onDismiss = { activeImageAttachment = null }
            )
        }

        activePdfAttachment?.let { pdfAttachment ->
            BulletinNativePdfReaderDialog(
                attachment = pdfAttachment,
                loadAttachmentFile = loadAttachmentFile,
                onDismiss = { activePdfAttachment = null }
            )
        }
    }
}

@Composable
private fun BulletinListContent(
    selectedCategory: HrpBulletinCategory,
    items: List<HrpBulletinItem>,
    unreadCount: Int,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    markingAllRead: Boolean,
    onSelectCategory: (HrpBulletinCategory) -> Unit,
    onRetry: () -> Unit,
    onMarkAllRead: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBulletin: (HrpBulletinItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("bulletin_category_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HrpBulletinCategory.filterCategories.forEach { category ->
                val isSelected = selectedCategory == category
                val categoryTint = resolveBulletinCategoryTint(category)
                val chipBg = if (isSelected) {
                    categoryTint.copy(alpha = 0.14f)
                } else {
                    SemanticColors.SurfaceSubtle
                }
                val chipBorder = if (isSelected) {
                    categoryTint.copy(alpha = 0.45f)
                } else {
                    SemanticColors.SurfaceBorder
                }
                val chipContentColor = if (isSelected) {
                    categoryTint
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = CircleShape,
                    color = chipBg,
                    border = BorderStroke(1.dp, chipBorder),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(CircleShape)
                        .clickable { onSelectCategory(category) }
                        .testTag("bulletin_category_chip_${category.wireKey}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = resolveBulletinCategoryIcon(category),
                            contentDescription = null,
                            tint = chipContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = category.labelVi,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = chipContentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Unread summary + "Đánh dấu tất cả đã đọc" quick bar
        if (unreadCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$unreadCount bản tin chưa đọc",
                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !markingAllRead) { onMarkAllRead() }
                        .testTag("bulletin_mark_all_read_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (markingAllRead) "Đang cập nhật…" else "Đánh dấu tất cả đã đọc",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Mockup mode notice banner (hidden automatically when BULLETIN_REMOTE_API_ENABLED == true)
        if (!BULLETIN_REMOTE_API_ENABLED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hrpMutedTile(cornerRadius = 10.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("bulletin_mockup_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Bản tin nội bộ HRPartner (dữ liệu mẫu minh họa).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (!errorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("bulletin_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SemanticColors.ErrorText,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Text("Thử lại", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("bulletin_loading_state"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("bulletin_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedCategory == HrpBulletinCategory.ALL) {
                            "Chưa có bản tin mới"
                        } else {
                            "Chưa có bản tin nào trong danh mục này."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("bulletin_list_column"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(items, key = { it.bulletinId }) { item ->
                        BulletinCardItem(
                            item = item,
                            onClick = { onOpenBulletin(item) }
                        )
                    }
                    if (hasMore) {
                        item(key = "bulletin_load_more_footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    enabled = !isLoadingMore,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 44.dp)
                                        .testTag("bulletin_load_more_button")
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (isLoadingMore) "Đang tải thêm…" else "Tải thêm",
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun BulletinCardItem(
    item: HrpBulletinItem,
    onClick: () -> Unit
) {
    val categoryTint = resolveBulletinCategoryTint(item.category)
    val borderHighlight = if (item.isPinned) {
        categoryTint.copy(alpha = 0.42f)
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface(borderColor = borderHighlight)
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("bulletin_card_${item.bulletinId}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top metadata row: Category icon (10dp rounded box) + Category label · Date (tabularNums) + Badge + Unread dot
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(categoryTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = resolveBulletinCategoryIcon(item.category),
                        contentDescription = null,
                        tint = categoryTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "${item.categoryDisplay} · ${item.publishedDateDisplay}",
                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Bản tin ghim",
                        tint = categoryTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
                item.badgeText?.takeIf { it.isNotBlank() }?.let { badge ->
                    HrpStatusBadge(
                        text = badge,
                        tint = categoryTint
                    )
                }
                if (!item.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SemanticColors.ErrorLight)
                            .testTag("bulletin_unread_dot_${item.bulletinId}")
                    )
                }
            }
        }

        // Bold title + 2-line summary
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = SemanticColors.SurfaceBorder.copy(alpha = 0.65f))

        // Footer: Publisher left · Attachment chips · "Xem chi tiết >" right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.authorDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (item.pdfCount > 0) {
                    AttachmentCountPill(
                        icon = Icons.Default.PictureAsPdf,
                        label = "${item.pdfCount} PDF",
                        tint = PdfBadgeRed
                    )
                }
                if (item.imageCount > 0) {
                    AttachmentCountPill(
                        icon = Icons.Default.Image,
                        label = "${item.imageCount} ảnh",
                        tint = HolidayCategoryColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Xem chi tiết",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryTint
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = categoryTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentCountPill(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(0.5.dp, tint.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp).tabularNums(),
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BulletinDetailContent(
    item: HrpBulletinItem,
    isDetailRefreshing: Boolean = false,
    loadAttachmentFile: suspend (HrpBulletinAttachment) -> File,
    onOpenImageAttachment: (HrpBulletinAttachment) -> Unit,
    onOpenPdfAttachment: (HrpBulletinAttachment) -> Unit
) {
    val categoryTint = resolveBulletinCategoryTint(item.category)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
            .testTag("bulletin_detail_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Category pill + Published Date
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HrpStatusBadge(
                text = item.categoryDisplay,
                tint = categoryTint,
                icon = resolveBulletinCategoryIcon(item.category)
            )
            Text(
                text = "Ngày đăng: ${item.publishedDateDisplay}",
                style = MaterialTheme.typography.labelMedium.tabularNums(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Large title
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("bulletin_detail_title")
        )

        // Publisher info
        Text(
            text = "Đơn vị phát hành: ${item.authorDisplay}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Bold summary
        Text(
            text = item.summary,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Content sections on hrpMutedTile
        if (isDetailRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        item.contentSections.forEachIndexed { idx, sectionText ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hrpMutedTile(cornerRadius = HrpDesignTokens.RadiusMD)
                    .padding(14.dp)
                    .testTag("bulletin_detail_section_$idx")
            ) {
                Text(
                    text = sectionText,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Attachments section (automatically hidden when attachments is empty)
        if (item.attachments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tệp đính kèm (${item.attachments.size})",
                style = MaterialTheme.typography.titleSmall.tabularNums(),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            item.attachments.forEach { attachment ->
                when (attachment.kind) {
                    HrpBulletinAttachmentKind.IMAGE -> {
                        BulletinImageAttachmentCard(
                            attachment = attachment,
                            loadAttachmentFile = loadAttachmentFile,
                            onClick = { onOpenImageAttachment(attachment) }
                        )
                    }

                    HrpBulletinAttachmentKind.PDF -> {
                        BulletinPdfAttachmentCard(
                            attachment = attachment,
                            onClick = { onOpenPdfAttachment(attachment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletinImageAttachmentCard(
    attachment: HrpBulletinAttachment,
    loadAttachmentFile: suspend (HrpBulletinAttachment) -> File,
    onClick: () -> Unit
) {
    var thumbnailBitmap by remember(attachment.attachmentId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(attachment.attachmentId) {
        thumbnailBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val file = loadAttachmentFile(attachment)
                BitmapFactory.decodeFile(file.absolutePath)
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("bulletin_attachment_${attachment.attachmentId}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SemanticColors.SurfaceSubtle)
                .border(1.dp, SemanticColors.SurfaceBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnailBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = attachment.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = HolidayCategoryColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = attachment.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${attachment.fileName} · ${attachment.formattedFileSize}",
                    style = MaterialTheme.typography.labelSmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = HolidayCategoryColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, HolidayCategoryColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = HolidayCategoryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Phóng to",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = HolidayCategoryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletinPdfAttachmentCard(
    attachment: HrpBulletinAttachment,
    onClick: () -> Unit
) {
    val pageCountDisplay = attachment.pageCount ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hrpCardSurface()
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("bulletin_attachment_${attachment.attachmentId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PdfBadgeRed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = "PDF",
                tint = PdfBadgeRed,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = attachment.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$pageCountDisplay trang · ${attachment.formattedFileSize}",
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                fontWeight = FontWeight.SemiBold,
                color = PdfBadgeRed
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .testTag("bulletin_open_pdf_${attachment.attachmentId}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Đọc văn bản",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BulletinFullScreenImageViewerDialog(
    attachment: HrpBulletinAttachment,
    loadAttachmentFile: suspend (HrpBulletinAttachment) -> File,
    onDismiss: () -> Unit
) {
    var bitmap by remember(attachment.attachmentId) { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(attachment.attachmentId) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val file = loadAttachmentFile(attachment)
                BitmapFactory.decodeFile(file.absolutePath)
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxSize()
                .testTag("bulletin_image_viewer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Viewer Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${attachment.fileName} · ${(scale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                scale = (scale - 0.5f).coerceAtLeast(1f)
                                if (scale <= 1f) offset = Offset.Zero
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Thu nhỏ",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                scale = (scale + 0.5f).coerceAtMost(4f)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Phóng to",
                                tint = Color.White
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("bulletin_image_viewer_close")
                        ) {
                            Text(
                                text = "Đóng",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Zoomable Image Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(attachment.attachmentId) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(attachment.attachmentId) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                offset = if (newScale > 1f) {
                                    Offset(
                                        x = (offset.x + pan.x).coerceIn(-1200f, 1200f),
                                        y = (offset.y + pan.y).coerceIn(-1200f, 1200f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = attachment.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletinNativePdfReaderDialog(
    attachment: HrpBulletinAttachment,
    loadAttachmentFile: suspend (HrpBulletinAttachment) -> File,
    onDismiss: () -> Unit
) {
    var pageBitmaps by remember(attachment.attachmentId) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember(attachment.attachmentId) { mutableStateOf(true) }
    var loadError by remember(attachment.attachmentId) { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()
    val currentVisiblePage by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex + 1).coerceAtLeast(1)
        }
    }

    DisposableEffect(attachment.attachmentId) {
        onDispose {
            pageBitmaps = emptyList()
        }
    }

    LaunchedEffect(attachment.attachmentId) {
        loading = true
        loadError = null
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                val pdfFile = loadAttachmentFile(attachment)
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val count = renderer.pageCount.coerceAtLeast(1)
                        val bitmaps = ArrayList<Bitmap>(count)
                        for (pageIdx in 0 until count) {
                            renderer.openPage(pageIdx).use { page ->
                                val targetWidth = (page.width * 2).coerceIn(1080, 2480)
                                val targetHeight = ((page.height.toFloat() / page.width.toFloat()) * targetWidth)
                                    .roundToInt()
                                    .coerceAtLeast(1080)
                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bmp)
                            }
                        }
                        bitmaps
                    }
                }
            }
        }
        rendered.onSuccess { bitmaps ->
            pageBitmaps = bitmaps
            loading = false
        }.onFailure { err ->
            loadError = err.message ?: "Không thể đọc tệp PDF."
            loading = false
        }
    }

    val totalPages = pageBitmaps.size.takeIf { it > 0 } ?: (attachment.pageCount ?: 1)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SemanticColors.BackgroundLight,
            modifier = Modifier
                .fillMaxSize()
                .testTag("bulletin_pdf_reader_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // PDF Reader Header Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Trang ${currentVisiblePage.coerceAtMost(totalPages)} / $totalPages · ${(zoomScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.tabularNums(),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("bulletin_pdf_page_indicator")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                                    if (zoomScale <= 1f) panOffsetX = 0f
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Thu nhỏ PDF",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale + 0.25f).coerceAtMost(3f)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Phóng to PDF",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("bulletin_pdf_reader_close")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Đóng",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = SemanticColors.SurfaceBorder)

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    loadError != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = loadError ?: "Không thể hiển thị tài liệu PDF.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SemanticColors.ErrorText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .pointerInput(attachment.attachmentId) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val updatedScale = (zoomScale * zoom).coerceIn(1f, 3f)
                                        zoomScale = updatedScale
                                        panOffsetX = if (updatedScale > 1f) {
                                            (panOffsetX + pan.x).coerceIn(-900f, 900f)
                                        } else {
                                            0f
                                        }
                                    }
                                }
                                .testTag("bulletin_pdf_pages_list"),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(pageBitmaps) { index, bmp ->
                                val pageNumber = index + 1
                                val ratio = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.5f, 2f)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .hrpCardSurface()
                                        .padding(10.dp)
                                        .testTag("bulletin_pdf_page_$pageNumber"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Trang $pageNumber / ${pageBitmaps.size}",
                                            style = MaterialTheme.typography.labelMedium.tabularNums(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = attachment.fileName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, SemanticColors.SurfaceBorder, RoundedCornerShape(8.dp))
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Trang $pageNumber",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(ratio)
                                                .graphicsLayer {
                                                    scaleX = zoomScale
                                                    scaleY = zoomScale
                                                    translationX = panOffsetX
                                                }
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
}
