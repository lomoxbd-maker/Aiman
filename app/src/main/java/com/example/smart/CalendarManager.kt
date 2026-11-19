package com.example.smart

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.permissions.AppPermission
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Testable OS calendar reader abstraction.
 */
interface CalendarBackend {
  fun queryEventsForRange(startMillis: Long, endMillis: Long): Result<List<CalendarEventItem>>
}

/**
 * Production Android [CalendarContract.Instances] reader executing strictly on explicit user query
 * after runtime READ_CALENDAR permission and privacy authorization pass.
 */
class AndroidCalendarBackend(
  private val context: Context,
) : CalendarBackend {

  override fun queryEventsForRange(
    startMillis: Long,
    endMillis: Long,
  ): Result<List<CalendarEventItem>> {
    return try {
      val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
      ContentUris.appendId(builder, startMillis)
      ContentUris.appendId(builder, endMillis)

      val projection =
        arrayOf(
          CalendarContract.Instances.EVENT_ID,
          CalendarContract.Instances.TITLE,
          CalendarContract.Instances.BEGIN,
          CalendarContract.Instances.END,
        )

      val results = mutableListOf<CalendarEventItem>()
      val fmt = SimpleDateFormat("hh:mm a", Locale.US)

      context.contentResolver.query(
        builder.build(),
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
      )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
        val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
        val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
        val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)

        while (cursor.moveToNext() && results.size < 20) {
          val id = if (idIdx >= 0) cursor.getLong(idIdx).toString() else results.size.toString()
          val title =
            if (titleIdx >= 0 && !cursor.isNull(titleIdx)) {
              cursor.getString(titleIdx)
            } else {
              "Calendar Event"
            }
          val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else startMillis
          val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
          results.add(
            CalendarEventItem(
              id = id,
              title = title,
              timeLabel = "${fmt.format(begin)} - ${fmt.format(end)}",
              startMillis = begin,
              endMillis = end,
              sourceLabel = "Device Calendar",
            ),
          )
        }
      }
      Result.success(results)
    } catch (se: SecurityException) {
      Result.failure(se)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

/**
 * CalendarManager — Reads today's schedule ONLY when explicitly asked by the user.
 * Never alters events in the background and falls back safely if calendar permission is withheld.
 */
class CalendarManager(
  private val context: Context,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val backend: CalendarBackend,
  private val reminderManager: ReminderManager,
  private val errorTracker: ErrorTracker,
) {

  private val _events = MutableStateFlow<List<CalendarEventItem>>(emptyList())
  val events: StateFlow<List<CalendarEventItem>> = _events.asStateFlow()

  private val _lastSummary = MutableStateFlow("")
  val lastSummary: StateFlow<String> = _lastSummary.asStateFlow()

  fun loadTodaySchedule(
    initiatedByUser: Boolean,
    preferBangla: Boolean = true,
  ): Result<Pair<List<CalendarEventItem>, String>> {
    if (!initiatedByUser) {
      val reason = "Blocked automatic calendar access. User request required."
      errorTracker.logWarning(MODULE_NAME, reason)
      return Result.failure(SecurityException(reason))
    }

    val (startOfDay, endOfDay) = todayBounds()
    val localReminderEvents =
      reminderManager.reminders.value
        .filter { !it.isCompleted && it.triggerAtMillis in startOfDay..endOfDay }
        .map { rem ->
          CalendarEventItem(
            id = "rem_${rem.id}",
            title = rem.title,
            timeLabel = rem.dueLabel,
            startMillis = rem.triggerAtMillis,
            endMillis = rem.triggerAtMillis,
            sourceLabel = "MAX Reminder",
          )
        }

    val auth = privacyGuard.authorizeAction(SensitiveAction.READ_CALENDAR, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler.requestForExplicitAction(AppPermission.CALENDAR, preferBangla)
      _events.value = localReminderEvents
      val fallbackSummary =
        if (preferBangla) {
          "ডিভাইস ক্যালেন্ডার পড়তে অনুমতি প্রয়োজন। MAX-এ আজকের ${localReminderEvents.size}টি রিমাইন্ডার পাওয়া গেছে।"
        } else {
          "Calendar permission needed for device events. Found ${localReminderEvents.size} local MAX reminder(s) for today."
        }
      _lastSummary.value = fallbackSummary
      return Result.failure(IllegalStateException(auth.reason))
    }

    val osResult = backend.queryEventsForRange(startOfDay, endOfDay)
    return osResult.fold(
      onSuccess = { osEvents ->
        val combined = (osEvents + localReminderEvents).sortedBy { it.startMillis }
        _events.value = combined
        val summary =
          if (combined.isEmpty()) {
            if (preferBangla) {
              "আজকের ক্যালেন্ডারে কোনো নির্ধারিত ইভেন্ট নেই। দিনটা শান্তভাবে পরিকল্পনা করতে পারেন।"
            } else {
              "No events are scheduled on today's calendar. Your schedule is clear."
            }
          } else {
            val preview = combined.take(3).joinToString("; ") { "${it.title} (${it.timeLabel})" }
            if (preferBangla) {
              "আজ আপনার ${combined.size}টি ইভেন্ট রয়েছে: $preview।"
            } else {
              "You have ${combined.size} item(s) today: $preview."
            }
          }
        _lastSummary.value = summary
        privacyGuard.notifyResourcesReleased(SensitiveAction.READ_CALENDAR)
        errorTracker.logInfo(MODULE_NAME, "Loaded ${combined.size} today calendar item(s).")
        Result.success(combined to summary)
      },
      onFailure = { err ->
        _events.value = localReminderEvents
        val summary =
          if (preferBangla) {
            "ডিভাইস ক্যালেন্ডার পড়া যায়নি; আজকের ${localReminderEvents.size}টি MAX রিমাইন্ডার দেখানো হচ্ছে।"
          } else {
            "Could not query OS calendar; showing ${localReminderEvents.size} local MAX reminder(s)."
          }
        _lastSummary.value = summary
        errorTracker.logWarning(MODULE_NAME, "Calendar query fallback: ${err.message}")
        Result.success(localReminderEvents to summary)
      },
    )
  }

  /**
   * Opens the user's Calendar app composer strictly when tapped by user (no silent edits).
   */
  fun openSystemCalendarInsert(title: String): Boolean {
    return runCatching {
      val intent =
        Intent(Intent.ACTION_INSERT).apply {
          data = CalendarContract.Events.CONTENT_URI
          putExtra(CalendarContract.Events.TITLE, title.trim().ifBlank { "MAX Event" })
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  private fun todayBounds(): Pair<Long, Long> {
    val start =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    val end =
      Calendar.getInstance().apply {
        timeInMillis = start.timeInMillis
        add(Calendar.DAY_OF_YEAR, 1)
        add(Calendar.MILLISECOND, -1)
      }
    return start.timeInMillis to end.timeInMillis
  }

  companion object {
    private const val MODULE_NAME = "CalendarManager"
  }
}
