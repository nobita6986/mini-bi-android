package com.example.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class TaskReminderRepeatMode(val wireValue: String, val labelVi: String) {
    DAILY("DAILY", "Hàng ngày"),
    ONCE("ONCE", "1 lần");

    companion object {
        fun fromWireValue(raw: String?): TaskReminderRepeatMode {
            return entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: DAILY
        }
    }
}

data class SystemSoundOption(
    val uri: String,
    val title: String
)

data class TaskReminderItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = TaskReminderManager.DEFAULT_TITLE,
    val hour: Int = 8,
    val minute: Int = 0,
    val repeatMode: TaskReminderRepeatMode = TaskReminderRepeatMode.DAILY,
    val isEnabled: Boolean = true,
    val soundUri: String = TaskReminderManager.DEFAULT_SOUND_URI,
    val soundTitle: String = TaskReminderManager.DEFAULT_SOUND_TITLE,
    val nextTriggerEpochMs: Long = 0L
) {
    val formattedTime: String
        get() = String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}

class TaskReminderManager(
    private val context: Context,
    private val prefsName: String = PREFS_NAME,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    private val scheduleSystemAlarms: Boolean = true
) {
    private val lock = Any()
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _remindersFlow = MutableStateFlow<List<TaskReminderItem>>(emptyList())
    val remindersFlow: StateFlow<List<TaskReminderItem>> = _remindersFlow.asStateFlow()

    @Volatile
    private var activePreviewRingtone: Ringtone? = null

    init {
        reconcileExpiredOnceReminders(clockMillis())
    }

    fun getReminders(nowEpochMs: Long = clockMillis()): List<TaskReminderItem> {
        return reconcileExpiredOnceReminders(nowEpochMs)
    }

    fun reconcileExpiredOnceReminders(nowEpochMs: Long = clockMillis()): List<TaskReminderItem> {
        synchronized(lock) {
            val loaded = loadFromPrefsLocked()
            var mutated = false
            val reconciled = loaded.map { item ->
                if (item.repeatMode == TaskReminderRepeatMode.ONCE &&
                    item.isEnabled &&
                    item.nextTriggerEpochMs > 0L &&
                    item.nextTriggerEpochMs <= nowEpochMs
                ) {
                    mutated = true
                    if (scheduleSystemAlarms) {
                        cancelSystemAlarm(item.id)
                    }
                    item.copy(isEnabled = false)
                } else {
                    item
                }
            }
            val sorted = sortReminders(reconciled)
            if (mutated) {
                saveToPrefsLocked(sorted)
            }
            _remindersFlow.value = sorted
            return sorted
        }
    }

    fun saveReminder(
        id: String? = null,
        title: String,
        hour: Int,
        minute: Int,
        repeatMode: TaskReminderRepeatMode,
        soundUri: String = DEFAULT_SOUND_URI,
        soundTitle: String = DEFAULT_SOUND_TITLE,
        isEnabled: Boolean = true,
        nowEpochMs: Long = clockMillis()
    ): TaskReminderItem {
        val normalizedTitle = normalizeTitle(title)
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val safeSoundUri = soundUri.trim().ifEmpty { DEFAULT_SOUND_URI }
        val safeSoundTitle = soundTitle.trim().ifEmpty { DEFAULT_SOUND_TITLE }
        val targetId = id?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val nextTrigger = computeNextTriggerEpochMs(
            hour = safeHour,
            minute = safeMinute,
            nowEpochMs = nowEpochMs,
            timeZone = timeZoneProvider()
        )
        val savedItem = TaskReminderItem(
            id = targetId,
            title = normalizedTitle,
            hour = safeHour,
            minute = safeMinute,
            repeatMode = repeatMode,
            isEnabled = isEnabled,
            soundUri = safeSoundUri,
            soundTitle = safeSoundTitle,
            nextTriggerEpochMs = nextTrigger
        )

        synchronized(lock) {
            val current = reconcileExpiredOnceReminders(nowEpochMs).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == targetId }
            if (existingIndex >= 0) {
                current[existingIndex] = savedItem
            } else {
                current.add(savedItem)
            }
            val sorted = sortReminders(current)
            saveToPrefsLocked(sorted)
            _remindersFlow.value = sorted
        }

        if (scheduleSystemAlarms) {
            if (savedItem.isEnabled) {
                scheduleSystemAlarm(savedItem)
            } else {
                cancelSystemAlarm(savedItem.id)
            }
        }
        return savedItem
    }

    fun setReminderEnabled(
        id: String,
        enabled: Boolean,
        nowEpochMs: Long = clockMillis()
    ): TaskReminderItem? {
        var updatedItem: TaskReminderItem? = null
        synchronized(lock) {
            val current = reconcileExpiredOnceReminders(nowEpochMs).toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx < 0) return null
            val existing = current[idx]
            val nextTrigger = if (enabled) {
                computeNextTriggerEpochMs(
                    hour = existing.hour,
                    minute = existing.minute,
                    nowEpochMs = nowEpochMs,
                    timeZone = timeZoneProvider()
                )
            } else {
                existing.nextTriggerEpochMs
            }
            val modified = existing.copy(
                isEnabled = enabled,
                nextTriggerEpochMs = nextTrigger
            )
            current[idx] = modified
            val sorted = sortReminders(current)
            saveToPrefsLocked(sorted)
            _remindersFlow.value = sorted
            updatedItem = modified
        }

        val finalItem = updatedItem ?: return null
        if (scheduleSystemAlarms) {
            if (finalItem.isEnabled) {
                scheduleSystemAlarm(finalItem)
            } else {
                cancelSystemAlarm(finalItem.id)
            }
        }
        return finalItem
    }

    fun deleteReminder(id: String) {
        synchronized(lock) {
            val current = loadFromPrefsLocked().filterNot { it.id == id }
            val sorted = sortReminders(current)
            saveToPrefsLocked(sorted)
            _remindersFlow.value = sorted
        }
        if (scheduleSystemAlarms) {
            cancelSystemAlarm(id)
        }
    }

    fun onAlarmTriggered(
        id: String,
        nowEpochMs: Long = clockMillis()
    ): TaskReminderItem? {
        var triggeredItem: TaskReminderItem? = null
        var nextScheduledItem: TaskReminderItem? = null
        synchronized(lock) {
            val current = loadFromPrefsLocked().toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx < 0) return null
            val existing = current[idx]
            if (!existing.isEnabled) return null
            triggeredItem = existing
            val updated = when (existing.repeatMode) {
                TaskReminderRepeatMode.ONCE -> existing.copy(isEnabled = false)
                TaskReminderRepeatMode.DAILY -> {
                    val nextDayTrigger = computeNextTriggerEpochMs(
                        hour = existing.hour,
                        minute = existing.minute,
                        nowEpochMs = maxOf(nowEpochMs, existing.nextTriggerEpochMs) + 1_000L,
                        timeZone = timeZoneProvider()
                    )
                    existing.copy(isEnabled = true, nextTriggerEpochMs = nextDayTrigger)
                }
            }
            current[idx] = updated
            if (updated.isEnabled) {
                nextScheduledItem = updated
            }
            val sorted = sortReminders(current)
            saveToPrefsLocked(sorted)
            _remindersFlow.value = sorted
        }

        if (scheduleSystemAlarms) {
            val toSchedule = nextScheduledItem
            if (toSchedule != null) {
                scheduleSystemAlarm(toSchedule)
            } else {
                cancelSystemAlarm(id)
            }
        }
        return triggeredItem
    }

    fun rescheduleAllEnabledReminders(nowEpochMs: Long = clockMillis()) {
        val current = reconcileExpiredOnceReminders(nowEpochMs)
        if (!scheduleSystemAlarms) return
        synchronized(lock) {
            var updatedAny = false
            val refreshed = current.map { item ->
                if (item.isEnabled) {
                    val trigger = if (item.nextTriggerEpochMs > nowEpochMs) {
                        item.nextTriggerEpochMs
                    } else {
                        updatedAny = true
                        computeNextTriggerEpochMs(
                            hour = item.hour,
                            minute = item.minute,
                            nowEpochMs = nowEpochMs,
                            timeZone = timeZoneProvider()
                        )
                    }
                    val updated = item.copy(nextTriggerEpochMs = trigger)
                    scheduleSystemAlarm(updated)
                    updated
                } else {
                    item
                }
            }
            if (updatedAny) {
                val sorted = sortReminders(refreshed)
                saveToPrefsLocked(sorted)
                _remindersFlow.value = sorted
            }
        }
    }

    fun querySystemSounds(): List<SystemSoundOption> {
        val result = mutableListOf(
            SystemSoundOption(
                uri = DEFAULT_SOUND_URI,
                title = DEFAULT_SOUND_TITLE
            )
        )
        val seenUris = mutableSetOf(DEFAULT_SOUND_URI)
        val appContext = context.applicationContext

        fun collectByType(type: Int, maxItems: Int) {
            runCatching {
                val manager = RingtoneManager(appContext).apply { setType(type) }
                val cursor = manager.cursor ?: return@runCatching
                cursor.use { c ->
                    var count = 0
                    while (c.moveToNext() && count < maxItems) {
                        val ringtoneUri = runCatching { manager.getRingtoneUri(c.position) }.getOrNull()
                            ?: continue
                        val uriStr = ringtoneUri.toString().trim()
                        if (uriStr.isEmpty() || !seenUris.add(uriStr)) continue
                        val rawTitle = runCatching { c.getString(RingtoneManager.TITLE_COLUMN_INDEX) }
                            .getOrNull()
                            ?.trim()
                            .orEmpty()
                        val displayTitle = rawTitle.ifEmpty { "Âm báo hệ thống #${result.size}" }
                        result.add(SystemSoundOption(uri = uriStr, title = displayTitle))
                        count++
                    }
                }
            }
        }

        collectByType(RingtoneManager.TYPE_ALARM, 20)
        collectByType(RingtoneManager.TYPE_NOTIFICATION, 20)
        return result
    }

    fun playSoundPreview(soundUri: String) {
        stopSoundPreview()
        runCatching {
            val resolvedUri = resolveSystemSoundUri(soundUri) ?: return@runCatching
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, resolvedUri) ?: return@runCatching
            activePreviewRingtone = ringtone
            ringtone.play()
        }
    }

    fun stopSoundPreview() {
        runCatching {
            activePreviewRingtone?.let { ringtone ->
                if (ringtone.isPlaying) {
                    ringtone.stop()
                }
            }
        }
        activePreviewRingtone = null
    }

    internal fun resolveSystemSoundUri(soundUri: String): Uri? {
        val cleaned = soundUri.trim()
        if (cleaned.isEmpty() || cleaned == DEFAULT_SOUND_URI) {
            return runCatching {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }.getOrNull()
        }
        return runCatching { Uri.parse(cleaned) }.getOrNull()
    }

    private fun scheduleSystemAlarm(item: TaskReminderItem) {
        runCatching {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildAlarmPendingIntent(appContext, item.id)
            val triggerAt = maxOf(item.nextTriggerEpochMs, clockMillis() + 1_000L)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (_: SecurityException) {
                runCatching {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            }
        }
    }

    private fun cancelSystemAlarm(reminderId: String) {
        runCatching {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildAlarmPendingIntent(appContext, reminderId)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun buildAlarmPendingIntent(appContext: Context, reminderId: String): PendingIntent {
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_TASK_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(appContext, reminderId.hashCode(), intent, flags)
    }

    private fun loadFromPrefsLocked(): List<TaskReminderItem> {
        val raw = prefs.getString(KEY_REMINDERS_JSON, null)?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val items = mutableListOf<TaskReminderItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim().ifEmpty { UUID.randomUUID().toString() }
                val title = normalizeTitle(obj.optString("title", DEFAULT_TITLE))
                val hour = obj.optInt("hour", 8).coerceIn(0, 23)
                val minute = obj.optInt("minute", 0).coerceIn(0, 59)
                val repeatMode = TaskReminderRepeatMode.fromWireValue(obj.optString("repeat_mode", "DAILY"))
                val isEnabled = obj.optBoolean("is_enabled", true)
                val soundUri = obj.optString("sound_uri", DEFAULT_SOUND_URI).trim().ifEmpty { DEFAULT_SOUND_URI }
                val soundTitle = obj.optString("sound_title", DEFAULT_SOUND_TITLE).trim().ifEmpty { DEFAULT_SOUND_TITLE }
                val nextTriggerEpochMs = obj.optLong("next_trigger_epoch_ms", 0L)
                items.add(
                    TaskReminderItem(
                        id = id,
                        title = title,
                        hour = hour,
                        minute = minute,
                        repeatMode = repeatMode,
                        isEnabled = isEnabled,
                        soundUri = soundUri,
                        soundTitle = soundTitle,
                        nextTriggerEpochMs = nextTriggerEpochMs
                    )
                )
            }
            items
        }.getOrDefault(emptyList())
    }

    private fun saveToPrefsLocked(items: List<TaskReminderItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("hour", item.hour)
                put("minute", item.minute)
                put("repeat_mode", item.repeatMode.wireValue)
                put("is_enabled", item.isEnabled)
                put("sound_uri", item.soundUri)
                put("sound_title", item.soundTitle)
                put("next_trigger_epoch_ms", item.nextTriggerEpochMs)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_REMINDERS_JSON, arr.toString()).commit()
    }

    private fun sortReminders(items: List<TaskReminderItem>): List<TaskReminderItem> {
        return items.sortedWith(
            compareBy<TaskReminderItem>({ it.hour * 60 + it.minute }, { it.title }, { it.id })
        )
    }

    companion object {
        const val PREFS_NAME = "hrp_task_reminders_prefs"
        const val KEY_REMINDERS_JSON = "task_reminders_json"
        const val DEFAULT_TITLE = "Nhắc việc"
        const val DEFAULT_SOUND_URI = "system_default"
        const val DEFAULT_SOUND_TITLE = "Mặc định hệ thống"
        const val MAX_TITLE_LENGTH = 120
        const val ACTION_TRIGGER_TASK_REMINDER = "vn.hrpartner.bi.ACTION_TRIGGER_TASK_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val NOTIFICATION_CHANNEL_PREFIX = "hrp_task_reminder_channel"

        @Volatile
        private var sharedInstance: TaskReminderManager? = null

        fun getInstance(context: Context): TaskReminderManager {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: TaskReminderManager(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }

        fun normalizeTitle(rawTitle: String?): String {
            val trimmed = rawTitle?.trim().orEmpty()
            return if (trimmed.isEmpty()) {
                DEFAULT_TITLE
            } else {
                trimmed.take(MAX_TITLE_LENGTH)
            }
        }

        fun computeNextTriggerEpochMs(
            hour: Int,
            minute: Int,
            nowEpochMs: Long,
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long {
            val cal = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowEpochMs
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= nowEpochMs) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        internal fun postReminderNotification(
            context: Context,
            item: TaskReminderItem,
            soundUri: Uri?
        ) {
            runCatching {
                val appContext = context.applicationContext
                val notificationManager =
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channelId = if (soundUri != null) {
                    "${NOTIFICATION_CHANNEL_PREFIX}_${soundUri.toString().hashCode()}"
                } else {
                    "${NOTIFICATION_CHANNEL_PREFIX}_default"
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val existing = notificationManager.getNotificationChannel(channelId)
                    if (existing == null) {
                        val channel = NotificationChannel(
                            channelId,
                            "Nhắc việc HRPartner",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Thông báo hẹn giờ nhắc việc trên thiết bị"
                            enableVibration(true)
                            if (soundUri != null) {
                                val attrs = AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                                setSound(soundUri, attrs)
                            }
                        }
                        notificationManager.createNotificationChannel(channel)
                    }
                }

                val tapIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val tapPendingIntent = PendingIntent.getActivity(
                    appContext,
                    item.id.hashCode(),
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val modeLabel = item.repeatMode.labelVi
                val notification = NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(item.title)
                    .setContentText("Đến giờ nhắc việc ${item.formattedTime} ($modeLabel)")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(tapPendingIntent)
                    .apply {
                        if (soundUri != null) {
                            setSound(soundUri)
                        }
                    }
                    .build()

                notificationManager.notify(item.id.hashCode(), notification)
            }
        }
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val manager = TaskReminderManager.getInstance(context)
        when (action) {
            TaskReminderManager.ACTION_TRIGGER_TASK_REMINDER -> {
                val reminderId = intent.getStringExtra(TaskReminderManager.EXTRA_REMINDER_ID)?.trim().orEmpty()
                if (reminderId.isEmpty()) return
                val triggered = manager.onAlarmTriggered(reminderId) ?: return
                val soundUri = manager.resolveSystemSoundUri(triggered.soundUri)
                TaskReminderManager.postReminderNotification(context, triggered, soundUri)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                manager.rescheduleAllEnabledReminders()
            }
        }
    }
}
