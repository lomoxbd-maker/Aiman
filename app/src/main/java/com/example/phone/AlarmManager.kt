package com.example.phone

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AlarmManager — Handles user-confirmed alarm scheduling via Android's native [AlarmClock]
 * intents across different device manufacturers cleanly with zero background wake locks.
 */
class AlarmManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) {

  private val _scheduledAlarms = MutableStateFlow<List<AlarmEntry>>(emptyList())
  val scheduledAlarms: StateFlow<List<AlarmEntry>> = _scheduledAlarms.asStateFlow()

  fun isClockAlarmSupported(): Boolean {
    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
    return runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)
  }

  /**
   * Schedules an alarm ONLY after [confirmedByUser] is true, keeping `EXTRA_SKIP_UI = false`
   * so OEM Clock apps handle vendor-specific Doze & battery optimization reliably.
   */
  fun setConfirmedAlarm(
    hour: Int,
    minute: Int,
    label: String,
    confirmedByUser: Boolean,
  ): Result<AlarmEntry> {
    if (!confirmedByUser) {
      val msg = "Blocked automatic alarm modification. Explicit user confirmation required."
      errorTracker.logWarning(MODULE_NAME, msg)
      return Result.failure(SecurityException(msg))
    }

    if (hour !in 0..23 || minute !in 0..59) {
      return Result.failure(IllegalArgumentException("Alarm time must be between 00:00 and 23:59."))
    }

    val cleanLabel = label.trim().ifEmpty { "MAX Alarm" }
    val entry =
      AlarmEntry(
        id = "alarm_${System.currentTimeMillis()}",
        hour = hour,
        minute = minute,
        label = cleanLabel,
        createdAtMillis = System.currentTimeMillis(),
      )

    val intent =
      Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour)
        putExtra(AlarmClock.EXTRA_MINUTES, minute)
        putExtra(AlarmClock.EXTRA_MESSAGE, cleanLabel)
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    val canResolve = runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)
    if (canResolve) {
      runCatching { context.startActivity(intent) }
        .onFailure { err ->
          errorTracker.logWarning(MODULE_NAME, "Clock app launch failed: ${err.message}")
        }
    } else {
      errorTracker.logWarning(MODULE_NAME, "No OEM Clock handler resolved; recorded session alarm entry.")
    }

    _scheduledAlarms.value = (_scheduledAlarms.value + entry).takeLast(MAX_SESSION_ALARMS)
    errorTracker.logInfo(MODULE_NAME, "Alarm confirmed by user for ${entry.formattedTime} ($cleanLabel).")
    return Result.success(entry)
  }

  fun openSystemAlarmsApp(): Boolean {
    val intent =
      Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return runCatching {
      if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        true
      } else {
        false
      }
    }.getOrDefault(false)
  }

  fun clearSessionAlarms() {
    _scheduledAlarms.value = emptyList()
  }

  companion object {
    private const val MODULE_NAME = "AlarmManager"
    private const val MAX_SESSION_ALARMS = 10
  }
}
