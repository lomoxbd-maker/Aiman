package com.example.smart

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ReminderManager — Schedules and tracks user-requested reminders cleanly without background
 * wake-locks or battery drain. Integrates with [PermissionController] & [PrivacyGuard] for
 * Android 13+ notification delivery, falling back gracefully to visual + TTS alerts.
 */
class ReminderManager(
  private val context: Context,
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val errorTracker: ErrorTracker,
  private val maxReminders: Int = 32,
  private val clockNowMillis: () -> Long = { System.currentTimeMillis() },
) {

  private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())
  val reminders: StateFlow<List<ReminderItem>> = _reminders.asStateFlow()

  fun scheduleReminder(
    title: String,
    triggerAtMillis: Long,
    initiatedByUser: Boolean,
    requestNotificationIfNeeded: Boolean = false,
  ): Result<ReminderItem> {
    if (!initiatedByUser) {
      val reason = "Blocked automatic reminder creation without explicit user instruction."
      errorTracker.logWarning(MODULE_NAME, reason)
      return Result.failure(SecurityException(reason))
    }

    val cleanTitle = title.trim().ifBlank { "MAX Reminder" }
    val safeTime = if (triggerAtMillis > clockNowMillis()) triggerAtMillis else clockNowMillis() + 60_000L
    val formattedDue = formatDueTime(safeTime)

    val auth = privacyGuard.authorizeAction(SensitiveAction.POST_NOTIFICATION, initiatedByUser = true)
    var notificationPosted = false
    if (auth is PrivacyCheckResult.Allowed) {
      notificationPosted = postOptionalConfirmationNotification(cleanTitle, formattedDue)
    } else if (requestNotificationIfNeeded) {
      permissionRequestHandler.requestForExplicitAction(AppPermission.NOTIFICATIONS, preferBangla = true)
    }

    val item =
      ReminderItem(
        id = UUID.randomUUID().toString(),
        title = cleanTitle,
        dueLabel = formattedDue,
        triggerAtMillis = safeTime,
        isCompleted = false,
        notificationPosted = notificationPosted,
      )
    _reminders.value = (listOf(item) + _reminders.value).take(maxReminders)
    errorTracker.logInfo(MODULE_NAME, "Scheduled reminder '$cleanTitle' for $formattedDue.")
    return Result.success(item)
  }

  /**
   * Parses Bangla & English reminder commands such as:
   * - "MAX, আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও"
   * - "MAX, tomorrow 8 AM reminder drink water"
   */
  fun scheduleFromVoiceCommand(
    input: String,
    initiatedByUser: Boolean,
  ): Result<ReminderItem> {
    val parsed = parseScheduleCommand(input)
    return scheduleReminder(
      title = parsed.first,
      triggerAtMillis = parsed.second,
      initiatedByUser = initiatedByUser,
      requestNotificationIfNeeded = false,
    )
  }

  fun toggleReminderCompleted(id: String) {
    _reminders.value =
      _reminders.value.map { item ->
        if (item.id == id) item.copy(isCompleted = !item.isCompleted) else item
      }
  }

  fun deleteReminder(id: String) {
    _reminders.value = _reminders.value.filterNot { it.id == id }
  }

  fun parseScheduleCommand(input: String): Pair<String, Long> {
    val asciiInput = normalizeBanglaDigits(input)
    val lower = asciiInput.lowercase()

    val cal =
      Calendar.getInstance().apply {
        timeInMillis = clockNowMillis()
      }

    val isTomorrow = lower.contains("আগামীকাল") || lower.contains("কালকে") || lower.contains("tomorrow")
    if (isTomorrow) {
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    val minutesLaterMatch = Regex("(\\d+)\\s*(min|minute|minutes|মিনিট)").find(lower)
    if (minutesLaterMatch != null && !isTomorrow) {
      val mins = minutesLaterMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 1440) ?: 10
      cal.add(Calendar.MINUTE, mins)
      val title = cleanReminderTitle(asciiInput)
      return title to cal.timeInMillis
    }

    val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|টা|টার)?").find(lower)
    var hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val suffix = timeMatch?.groupValues?.getOrNull(3).orEmpty()

    if ((lower.contains("বিকাল") || lower.contains("সন্ধ্যা") || lower.contains("রাত") || suffix == "pm") &&
      hour in 1..11
    ) {
      hour += 12
    } else if ((lower.contains("সকাল") || suffix == "am") && hour == 12) {
      hour = 0
    }

    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    if (!isTomorrow && cal.timeInMillis <= clockNowMillis()) {
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    val title = cleanReminderTitle(asciiInput)
    return title to cal.timeInMillis
  }

  private fun cleanReminderTitle(raw: String): String {
    val cleaned =
      raw
        .replace(Regex("(?i)max[,:\\s-]*"), "")
        .replace(Regex("আগামীকাল|সকাল|বিকাল|সন্ধ্যা|রাত|রিমাইন্ডার দাও|রিমাইন্ডার|মনে করিয়ে দিও|জন্য"), "")
        .replace(Regex("(?i)tomorrow|reminder|remind me( to)?|set reminder( for)?|at \\d{1,2}(:\\d{2})?\\s*(am|pm)?"), "")
        .replace(Regex("\\d{1,2}(:\\d{2})?\\s*(am|pm|টা|টার)"), "")
        .trim(' ', ':', '-', ',', '।', '.')
    return cleaned.ifBlank { "Scheduled Reminder" }
  }

  private fun postOptionalConfirmationNotification(title: String, dueLabel: String): Boolean {
    return runCatching {
      if (permissionController.checkStatus(AppPermission.NOTIFICATIONS) != com.example.permissions.PermissionStatus.GRANTED) {
        return false
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
      ) {
        return false
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "MAX Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
        mgr.createNotificationChannel(channel)
      }
      val notif =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
          .setContentTitle("MAX Reminder")
          .setContentText("$title • $dueLabel")
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
          .build()
      try {
        NotificationManagerCompat.from(context).notify(title.hashCode(), notif)
        true
      } catch (_: SecurityException) {
        false
      }
    }.getOrDefault(false)
  }

  private fun formatDueTime(millis: Long): String {
    val fmt = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.US)
    return fmt.format(millis)
  }

  private fun normalizeBanglaDigits(text: String): String {
    val map =
      mapOf(
        '০' to '0',
        '১' to '1',
        '২' to '2',
        '৩' to '3',
        '৪' to '4',
        '৫' to '5',
        '৬' to '6',
        '৭' to '7',
        '৮' to '8',
        '৯' to '9',
      )
    return buildString(text.length) {
      text.forEach { ch -> append(map[ch] ?: ch) }
    }
  }

  companion object {
    private const val MODULE_NAME = "ReminderManager"
    private const val CHANNEL_ID = "max_reminders_channel"
  }
}
