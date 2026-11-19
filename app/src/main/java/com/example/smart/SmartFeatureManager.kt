package com.example.smart

import com.example.utils.ErrorTracker

/**
 * SmartFeatureManager — Unified manager coordinating MAX's Translation, Reminder, Notes,
 * Calendar, Voice Notes, and Contextual AI Suggestions with [MAXCore],
 * [ConversationManager], [EmotionManager], [MemoryManager], [PermissionManager], and [TTSManager].
 */
class SmartFeatureManager(
  translationManager: TranslationManager,
  reminderManager: ReminderManager,
  notesManager: NotesManager,
  calendarManager: CalendarManager,
  voiceNotesManager: VoiceNotesManager,
  aiSuggestionManager: AISuggestionManager,
  private val errorTracker: ErrorTracker,
) : SmartFeaturesController(
    translationManager = translationManager,
    reminderManager = reminderManager,
    notesManager = notesManager,
    calendarManager = calendarManager,
    voiceNotesManager = voiceNotesManager,
    aiSuggestionManager = aiSuggestionManager,
  ) {

  override fun release() {
    super.release()
    errorTracker.logInfo(MODULE_NAME, "SmartFeatureManager released transient task state.")
  }

  companion object {
    private const val MODULE_NAME = "SmartFeatureManager"
  }
}
