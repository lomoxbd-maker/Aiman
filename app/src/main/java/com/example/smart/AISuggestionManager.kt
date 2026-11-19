package com.example.smart

import com.example.emotion.EmotionControllerContract
import com.example.emotion.EmotionType
import com.example.memory.MemoryController
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AISuggestionManager — Generates context-aware, non-repetitive Bangla & English suggestions
 * locally using signals from [EmotionControllerContract], [MemoryController], [ReminderManager],
 * [NotesManager], and [CalendarManager]. Zero personal files or notes leave the device.
 */
class AISuggestionManager(
  private val emotionManager: EmotionControllerContract,
  private val memoryController: MemoryController,
  private val reminderManager: ReminderManager,
  private val notesManager: NotesManager,
  private val calendarManager: CalendarManager,
  private val errorTracker: ErrorTracker,
) {

  private val _suggestions = MutableStateFlow<List<AISuggestionItem>>(emptyList())
  val suggestions: StateFlow<List<AISuggestionItem>> = _suggestions.asStateFlow()

  fun generateContextualSuggestions(): List<AISuggestionItem> {
    val mood = emotionManager.emotionState.value.currentEmotion
    val memories = memoryController.memories.value
    val activeReminders = reminderManager.reminders.value.filter { !it.isCompleted }
    val notes = notesManager.notes.value
    val events = calendarManager.events.value

    val userName = memoryController.getUserName()
    val greetingPrefix = if (!userName.isNullOrBlank()) "$userName, " else ""

    val generated = mutableListOf<AISuggestionItem>()

    when (mood) {
      EmotionType.CARING, EmotionType.SORRY ->
        generated.add(
          AISuggestionItem(
            id = "sug_calm_breath",
            titleBangla = "${greetingPrefix}কিছুক্ষণ শান্ত হয়ে শ্বাস নিন এবং সহজ কাজের তালিকা বানান",
            titleEnglish = "${greetingPrefix}Take a calm break and jot down just one gentle priority",
            reason = "Adapted to caring mood",
            suggestedActionCommand = "MAX, একটা নোট লিখে রাখো: আজ নিজেকে একটু বিশ্রাম দেব",
            priority = 1,
          ),
        )
      EmotionType.HAPPY, EmotionType.EXCITED ->
        generated.add(
          AISuggestionItem(
            id = "sug_capture_momentum",
            titleBangla = "${greetingPrefix}আজকের দারুণ আইডিয়াটা একটা নোটে বা ভয়েস নোটে সেভ করে রাখুন",
            titleEnglish = "${greetingPrefix}Capture your current idea in a quick note while inspired",
            reason = "Positive mood momentum",
            suggestedActionCommand = "MAX, একটা নোট লিখে রাখো: নতুন আইডিয়া ও লক্ষ্য",
            priority = 1,
          ),
        )
      else ->
        generated.add(
          AISuggestionItem(
            id = "sug_daily_focus",
            titleBangla = "${greetingPrefix}আজকের জরুরি কাজগুলোর জন্য একটি রিমাইন্ডার সেট করতে পারেন",
            titleEnglish = "${greetingPrefix}Set a timely reminder for your most important task",
            reason = "Helpful daily structure",
            suggestedActionCommand = "MAX, আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও",
            priority = 1,
          ),
        )
    }

    if (activeReminders.isNotEmpty()) {
      val next = activeReminders.first()
      generated.add(
        AISuggestionItem(
          id = "sug_next_rem",
          titleBangla = "সামনের রিমাইন্ডার: '${next.title}' (${next.dueLabel})",
          titleEnglish = "Upcoming reminder: '${next.title}' at ${next.dueLabel}",
          reason = "${activeReminders.size} active reminder(s)",
          suggestedActionCommand = "MAX, আজকের ক্যালেন্ডার দেখাও",
          priority = 2,
        ),
      )
    } else if (events.isNotEmpty()) {
      generated.add(
        AISuggestionItem(
          id = "sug_cal_event",
          titleBangla = "আজ ক্যালেন্ডারে ${events.size}টি ইভেন্ট আছে — সময় দেখে নিন",
          titleEnglish = "Review your ${events.size} calendar event(s) for today",
          reason = "Calendar schedule awareness",
          suggestedActionCommand = "MAX, আজকের ক্যালেন্ডার দেখাও",
          priority = 2,
        ),
      )
    }

    generated.add(
      AISuggestionItem(
        id = "sug_translate_helper",
        titleBangla = "যেকোনো বাংলা বা ইংরেজি বাক্য অফলাইনে নিরাপদে অনুবাদ করুন",
        titleEnglish = "Translate Bangla & English phrases privately offline",
        reason = "Zero-upload on-device translator (${notes.size} note(s), ${memories.size} memory item(s))",
        suggestedActionCommand = "MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো: আমি ভালো আছি",
        priority = 3,
      ),
    )

    val sorted = generated.sortedBy { it.priority }
    _suggestions.value = sorted
    errorTracker.logInfo(MODULE_NAME, "Generated ${sorted.size} contextual local suggestions.")
    return sorted
  }

  companion object {
    private const val MODULE_NAME = "AISuggestionManager"
  }
}
