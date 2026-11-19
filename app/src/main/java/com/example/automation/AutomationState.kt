package com.example.automation

enum class RoutineCategory(val displayBangla: String, val displayEnglish: String) {
  MORNING("সকালের রুটিন", "Morning Routine"),
  NIGHT("রাতের রুটিন", "Night Routine"),
  DAILY_BRIEFING("ডেইলি ব্রিফিং", "Daily Briefing"),
  VOICE_AUTOMATION("ভয়েস অটোমেশন", "Voice Routine"),
}

data class AutomationRoutine(
  val id: String,
  val titleBangla: String,
  val titleEnglish: String,
  val category: RoutineCategory,
  val triggerVoicePhrase: String,
  val scheduledTimeLabel: String,
  val steps: List<String>,
  val isEnabled: Boolean = true,
  val lastRunMillis: Long? = null,
)

data class ScheduledTaskItem(
  val id: String,
  val title: String,
  val scheduledTimeLabel: String,
  val triggerAtMillis: Long,
  val actionCommand: String,
  val isCompleted: Boolean = false,
)

data class DailyBriefingSummary(
  val headlineBangla: String,
  val headlineEnglish: String,
  val pendingRemindersCount: Int,
  val savedNotesCount: Int,
  val memoriesCount: Int,
  val generatedAtMillis: Long,
)

sealed class AutomationState {
  data object Idle : AutomationState()

  data class RunningRoutine(
    val routineTitle: String,
    val stepIndex: Int,
    val totalSteps: Int,
  ) : AutomationState()

  data class Completed(
    val routineTitle: String,
    val summaryMessage: String,
  ) : AutomationState()

  data class BlockedByPrivacyPolicy(
    val reason: String,
  ) : AutomationState()
}
