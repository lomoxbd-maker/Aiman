package com.example.automation

import com.example.memory.MemoryController
import com.example.smart.NotesManager
import com.example.smart.ReminderManager
import com.example.tts.SpeechStyle
import com.example.tts.TTSController
import com.example.tts.VoiceLanguage
import com.example.utils.ErrorTracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AutomationManager — Smart automation engine for Sanju's routines, daily briefings,
 * morning/night flows, and user-scheduled tasks.
 *
 * Works 100% offline using local Reminders, Notes, and Memory without polling battery or
 * triggering unrequested network/camera/microphone actions.
 */
class AutomationManager(
  private val reminderManager: ReminderManager? = null,
  private val notesManager: NotesManager? = null,
  private val memoryController: MemoryController? = null,
  private val ttsController: TTSController? = null,
  private val errorTracker: ErrorTracker? = null,
) {

  private val _state = MutableStateFlow<AutomationState>(AutomationState.Idle)
  val state: StateFlow<AutomationState> = _state.asStateFlow()

  private val _routines = MutableStateFlow(defaultRoutines())
  val routines: StateFlow<List<AutomationRoutine>> = _routines.asStateFlow()

  private val _scheduledTasks = MutableStateFlow<List<ScheduledTaskItem>>(emptyList())
  val scheduledTasks: StateFlow<List<ScheduledTaskItem>> = _scheduledTasks.asStateFlow()

  private val _lastBriefing = MutableStateFlow<DailyBriefingSummary?>(null)
  val lastBriefing: StateFlow<DailyBriefingSummary?> = _lastBriefing.asStateFlow()

  fun runRoutineByCategory(
    category: RoutineCategory,
    initiatedByUser: Boolean,
    preferBangla: Boolean = true,
    speakAloud: Boolean = true,
  ): String {
    if (!initiatedByUser) {
      val reason = "Automation requires explicit user activation or schedule trigger."
      _state.value = AutomationState.BlockedByPrivacyPolicy(reason)
      return reason
    }

    val routine = _routines.value.firstOrNull { it.category == category } ?: defaultRoutines().first()
    val totalSteps = routine.steps.size.coerceAtLeast(1)
    _state.value = AutomationState.RunningRoutine(routine.titleEnglish, 1, totalSteps)

    val briefing = buildDailyBriefing(preferBangla = preferBangla)
    val summary =
      when (category) {
        RoutineCategory.MORNING ->
          if (preferBangla) {
            "সুপ্রভাত সানজু! ${briefing.headlineBangla} আজ আপনার ${briefing.pendingRemindersCount}টি রিমাইন্ডার এবং ${briefing.savedNotesCount}টি নোট রয়েছে। দিনটি সুন্দর কাটুক!"
          } else {
            "Good morning, Sanju! ${briefing.headlineEnglish} You have ${briefing.pendingRemindersCount} active reminder(s) and ${briefing.savedNotesCount} note(s) today."
          }

        RoutineCategory.NIGHT ->
          if (preferBangla) {
            "শুভ রাত্রি সানজু। আজকের কাজগুলো সুন্দরভাবে গুছিয়ে রাখা হয়েছে (${briefing.pendingRemindersCount}টি আসন্ন রিমাইন্ডার)। আমি শান্তভাবে ওয়েক মোডে বিশ্রাম নিচ্ছি।"
          } else {
            "Good night, Sanju. Everything is organized for tomorrow (${briefing.pendingRemindersCount} upcoming reminder(s)). Resting calmly in standby."
          }

        RoutineCategory.DAILY_BRIEFING, RoutineCategory.VOICE_AUTOMATION ->
          if (preferBangla) {
            "সানজুর ডেইলি ব্রিফিং: ${briefing.headlineBangla} মোট রিমাইন্ডার ${briefing.pendingRemindersCount}টি, নোট ${briefing.savedNotesCount}টি, এবং সংরক্ষিত স্মৃতি ${briefing.memoriesCount}টি।"
          } else {
            "Sanju's Daily Briefing: ${briefing.headlineEnglish} Reminders: ${briefing.pendingRemindersCount}, Notes: ${briefing.savedNotesCount}, Saved memories: ${briefing.memoriesCount}."
          }
      }

    val now = System.currentTimeMillis()
    _routines.value =
      _routines.value.map { item ->
        if (item.id == routine.id) item.copy(lastRunMillis = now) else item
      }
    _state.value = AutomationState.Completed(routine.titleEnglish, summary)

    if (speakAloud) {
      val style = if (category == RoutineCategory.NIGHT) SpeechStyle.CALM else SpeechStyle.CARING
      ttsController?.speak(summary, style)
    }
    errorTracker?.logInfo(MODULE_NAME, "Completed ${category.name} automation routine for Sanju.")
    return summary
  }

  fun toggleRoutineEnabled(routineId: String) {
    _routines.value =
      _routines.value.map { r ->
        if (r.id == routineId) r.copy(isEnabled = !r.isEnabled) else r
      }
  }

  fun addCustomRoutine(
    title: String,
    triggerVoicePhrase: String,
    scheduledTimeLabel: String,
    steps: List<String>,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!initiatedByUser || title.isBlank()) return false
    val item =
      AutomationRoutine(
        id = "routine_${UUID.randomUUID().toString().take(8)}",
        titleBangla = title.trim(),
        titleEnglish = title.trim(),
        category = RoutineCategory.VOICE_AUTOMATION,
        triggerVoicePhrase = triggerVoicePhrase.trim().ifBlank { title.trim() },
        scheduledTimeLabel = scheduledTimeLabel.trim().ifBlank { "On Voice Command" },
        steps = steps.filter { it.isNotBlank() }.ifEmpty { listOf("Greet Sanju", "Summarize tasks") },
      )
    _routines.value = (_routines.value + item).takeLast(MAX_ROUTINES)
    return true
  }

  fun scheduleUserTask(
    title: String,
    scheduledTimeLabel: String,
    triggerAtMillis: Long,
    actionCommand: String,
    initiatedByUser: Boolean,
  ): ScheduledTaskItem? {
    if (!initiatedByUser || title.isBlank()) return null
    val task =
      ScheduledTaskItem(
        id = "task_${UUID.randomUUID().toString().take(8)}",
        title = title.trim(),
        scheduledTimeLabel = scheduledTimeLabel.trim().ifBlank { "Scheduled" },
        triggerAtMillis = triggerAtMillis,
        actionCommand = actionCommand.trim().ifBlank { title.trim() },
      )
    _scheduledTasks.value = (_scheduledTasks.value + task).takeLast(MAX_SCHEDULED_TASKS)
    errorTracker?.logInfo(MODULE_NAME, "Scheduled task '${task.title}' ($scheduledTimeLabel)")
    return task
  }

  fun toggleTaskCompleted(taskId: String) {
    _scheduledTasks.value =
      _scheduledTasks.value.map { t ->
        if (t.id == taskId) t.copy(isCompleted = !t.isCompleted) else t
      }
  }

  fun handleVoiceAutomationCommand(
    input: String,
    preferBangla: Boolean,
    initiatedByUser: Boolean,
  ): String? {
    val lower = input.lowercase()
    return when {
      lower.contains("সকালের রুটিন") || lower.contains("morning routine") ->
        runRoutineByCategory(RoutineCategory.MORNING, initiatedByUser, preferBangla, speakAloud = false)
      lower.contains("রাতের রুটিন") || lower.contains("night routine") || lower.contains("evening routine") ->
        runRoutineByCategory(RoutineCategory.NIGHT, initiatedByUser, preferBangla, speakAloud = false)
      lower.contains("ডেইলি ব্রিফিং") || lower.contains("daily briefing") || lower.contains("আজকের ব্রিফিং") ->
        runRoutineByCategory(RoutineCategory.DAILY_BRIEFING, initiatedByUser, preferBangla, speakAloud = false)
      else -> null
    }
  }

  fun buildDailyBriefing(preferBangla: Boolean = true): DailyBriefingSummary {
    val dateLabel = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(Date())
    val pendingReminders = reminderManager?.reminders?.value?.count { !it.isCompleted } ?: 0
    val savedNotes = notesManager?.notes?.value?.size ?: 0
    val memories = memoryController?.memories?.value?.size ?: 0

    val summary =
      DailyBriefingSummary(
        headlineBangla = "আজ $dateLabel।",
        headlineEnglish = "Today is $dateLabel.",
        pendingRemindersCount = pendingReminders,
        savedNotesCount = savedNotes,
        memoriesCount = memories,
        generatedAtMillis = System.currentTimeMillis(),
      )
    _lastBriefing.value = summary
    return summary
  }

  fun release() {
    _state.value = AutomationState.Idle
  }

  companion object {
    private const val MODULE_NAME = "AutomationManager"
    private const val MAX_ROUTINES = 12
    private const val MAX_SCHEDULED_TASKS = 24

    fun defaultRoutines(): List<AutomationRoutine> =
      listOf(
        AutomationRoutine(
          id = "routine_morning",
          titleBangla = "সকালের রুটিন",
          titleEnglish = "Morning Routine",
          category = RoutineCategory.MORNING,
          triggerVoicePhrase = "সকালের রুটিন চালাও",
          scheduledTimeLabel = "07:30 AM",
          steps = listOf("Warm Assalamu Alaikum Greeting", "Check Pending Reminders", "Review Saved Notes"),
        ),
        AutomationRoutine(
          id = "routine_briefing",
          titleBangla = "ডেইলি ব্রিফিং",
          titleEnglish = "Daily Briefing",
          category = RoutineCategory.DAILY_BRIEFING,
          triggerVoicePhrase = "ডেইলি ব্রিফিং দাও",
          scheduledTimeLabel = "09:00 AM",
          steps = listOf("Summarize Date & Day", "Count Pending Tasks", "Read Highlighted Memory"),
        ),
        AutomationRoutine(
          id = "routine_night",
          titleBangla = "রাতের রুটিন",
          titleEnglish = "Night Routine",
          category = RoutineCategory.NIGHT,
          triggerVoicePhrase = "রাতের রুটিন চালাও",
          scheduledTimeLabel = "10:30 PM",
          steps = listOf("Day Wrap-up", "Review Tomorrow Reminders", "Calm Standby Switch"),
        ),
      )
  }
}
