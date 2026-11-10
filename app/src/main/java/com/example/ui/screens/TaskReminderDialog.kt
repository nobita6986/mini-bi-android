package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SystemSoundOption
import com.example.data.TaskReminderItem
import com.example.data.TaskReminderManager
import com.example.data.TaskReminderRepeatMode
import com.example.ui.theme.LocalMiniBiPalette
import java.util.Locale

@Composable
fun TaskReminderDialog(
    taskReminderManager: TaskReminderManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalMiniBiPalette.current
    val reminders by taskReminderManager.remindersFlow.collectAsStateWithLifecycle()
    val systemSounds = remember(taskReminderManager) {
        taskReminderManager.querySystemSounds()
    }

    var isEditing by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftHourText by remember { mutableStateOf("08") }
    var draftMinuteText by remember { mutableStateOf("00") }
    var draftRepeatMode by remember { mutableStateOf(TaskReminderRepeatMode.DAILY) }
    var draftSound by remember(systemSounds) {
        mutableStateOf(
            systemSounds.firstOrNull() ?: SystemSoundOption(
                uri = TaskReminderManager.DEFAULT_SOUND_URI,
                title = TaskReminderManager.DEFAULT_SOUND_TITLE
            )
        )
    }

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

    fun openCreateForm() {
        editingId = null
        draftTitle = ""
        draftHourText = "08"
        draftMinuteText = "00"
        draftRepeatMode = TaskReminderRepeatMode.DAILY
        draftSound = systemSounds.firstOrNull() ?: SystemSoundOption(
            uri = TaskReminderManager.DEFAULT_SOUND_URI,
            title = TaskReminderManager.DEFAULT_SOUND_TITLE
        )
        isEditing = true
    }

    fun openEditForm(item: TaskReminderItem) {
        editingId = item.id
        draftTitle = item.title
        draftHourText = String.format(Locale.US, "%02d", item.hour.coerceIn(0, 23))
        draftMinuteText = String.format(Locale.US, "%02d", item.minute.coerceIn(0, 59))
        draftRepeatMode = item.repeatMode
        draftSound = systemSounds.firstOrNull { it.uri == item.soundUri }
            ?: SystemSoundOption(uri = item.soundUri, title = item.soundTitle)
        isEditing = true
    }

    LaunchedEffect(taskReminderManager) {
        taskReminderManager.reconcileExpiredOnceReminders()
    }

    DisposableEffect(taskReminderManager) {
        onDispose {
            taskReminderManager.stopSoundPreview()
        }
    }

    Dialog(
        onDismissRequest = {
            taskReminderManager.stopSoundPreview()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, palette.lightTopBarBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("task_reminder_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isEditing) {
                                    if (editingId == null) "Thêm nhắc việc" else "Chỉnh sửa nhắc việc"
                                } else {
                                    "Nhắc việc"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hẹn giờ báo thức công việc trên thiết bị",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            taskReminderManager.stopSoundPreview()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("task_reminder_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = palette.lightTopBarBorder)
                Spacer(modifier = Modifier.height(12.dp))

                if (isEditing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = draftTitle,
                            onValueChange = { draftTitle = it.take(TaskReminderManager.MAX_TITLE_LENGTH) },
                            label = { Text("Tiêu đề nhắc việc") },
                            placeholder = { Text("Ví dụ: Gửi báo cáo ca sáng") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_reminder_title_input")
                        )

                        // Time inputs (24h HH:mm)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = draftHourText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    draftHourText = digits
                                },
                                label = { Text("Giờ (00-23)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("task_reminder_hour_input")
                            )
                            Text(
                                text = ":",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = draftMinuteText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    draftMinuteText = digits
                                },
                                label = { Text("Phút (00-59)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("task_reminder_minute_input")
                            )
                        }

                        // Quick preset chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("07" to "30", "08" to "00", "12" to "00", "17" to "00", "20" to "00").forEach { (h, m) ->
                                val selectedPreset = draftHourText.padStart(2, '0') == h && draftMinuteText.padStart(2, '0') == m
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedPreset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedPreset) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            draftHourText = h
                                            draftMinuteText = m
                                        }
                                ) {
                                    Text(
                                        text = "$h:$m",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedPreset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Repeat Mode (DAILY / ONCE)
                        Text(
                            text = "Chế độ lặp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TaskReminderRepeatMode.entries.forEach { mode ->
                                val isSelected = draftRepeatMode == mode
                                val tag = if (mode == TaskReminderRepeatMode.DAILY) {
                                    "task_reminder_mode_daily"
                                } else {
                                    "task_reminder_mode_once"
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { draftRepeatMode = mode }
                                        .testTag(tag)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = mode.labelVi,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // System Sounds Selector
                        Text(
                            text = "Âm thanh báo động (Hệ thống)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            systemSounds.forEachIndexed { index, soundOption ->
                                val selectedSound = draftSound.uri == soundOption.uri
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedSound) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selectedSound) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            draftSound = soundOption
                                            taskReminderManager.playSoundPreview(soundOption.uri)
                                        }
                                        .testTag("task_reminder_sound_option_$index")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedSound,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = soundOption.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selectedSound) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Nghe thử",
                                            tint = if (selectedSound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Form Action Buttons (pinned below scrollable content)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                taskReminderManager.stopSoundPreview()
                                isEditing = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("task_reminder_cancel_edit_button")
                        ) {
                            Text("Hủy", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                taskReminderManager.stopSoundPreview()
                                ensureNotificationPermissionIfNeeded()
                                val parsedHour = (draftHourText.toIntOrNull() ?: 8).coerceIn(0, 23)
                                val parsedMinute = (draftMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                                taskReminderManager.saveReminder(
                                    id = editingId,
                                    title = draftTitle,
                                    hour = parsedHour,
                                    minute = parsedMinute,
                                    repeatMode = draftRepeatMode,
                                    soundUri = draftSound.uri,
                                    soundTitle = draftSound.title,
                                    isEnabled = true
                                )
                                isEditing = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("task_reminder_save_button")
                        ) {
                            Text("Lưu nhắc việc", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // List View
                    Button(
                        onClick = { openCreateForm() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("task_reminder_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thêm nhắc việc",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (reminders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp)
                                .testTag("task_reminder_empty_state"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Chưa có lịch nhắc việc nào.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reminders.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (item.isEnabled) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (item.isEnabled) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        } else {
                                            palette.lightTopBarBorder
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { openEditForm(item) }
                                        .testTag("task_reminder_item_${item.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = item.formattedTime,
                                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (item.isEnabled) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (item.repeatMode == TaskReminderRepeatMode.DAILY) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    }
                                                ) {
                                                    Text(
                                                        text = item.repeatMode.labelVi,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (item.repeatMode == TaskReminderRepeatMode.DAILY) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (item.isEnabled) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Âm báo: ${item.soundTitle}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Switch(
                                            checked = item.isEnabled,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    ensureNotificationPermissionIfNeeded()
                                                }
                                                taskReminderManager.setReminderEnabled(item.id, enabled)
                                            },
                                            modifier = Modifier.testTag("task_reminder_toggle_${item.id}")
                                        )

                                        IconButton(
                                            onClick = { taskReminderManager.deleteReminder(item.id) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .testTag("task_reminder_delete_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Xóa nhắc việc",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(19.dp)
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
}
