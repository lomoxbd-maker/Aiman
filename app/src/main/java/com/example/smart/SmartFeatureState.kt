package com.example.smart

/**
 * Supported offline-safe translation directions in MAX.
 */
enum class TranslationDirection(val displayLabel: String) {
  BANGLA_TO_ENGLISH("বাংলা -> English"),
  ENGLISH_TO_BANGLA("English -> বাংলা"),
}

/**
 * Single translation output kept strictly in volatile session memory (zero cloud logging).
 */
data class TranslationRecord(
  val id: String,
  val sourceText: String,
  val translatedText: String,
  val direction: TranslationDirection,
  val createdAtMillis: Long,
)

/**
 * User-scheduled reminder item with time metadata and completion toggle.
 */
data class ReminderItem(
  val id: String,
  val title: String,
  val dueLabel: String,
  val triggerAtMillis: Long,
  val isCompleted: Boolean = false,
  val notificationPosted: Boolean = false,
)

/**
 * User-authored smart note stored locally in session memory with optional memory link.
 */
data class SmartNoteItem(
  val id: String,
  val title: String,
  val content: String,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

/**
 * Read-only calendar event summary loaded strictly after explicit user request.
 */
data class CalendarEventItem(
  val id: String,
  val title: String,
  val timeLabel: String,
  val startMillis: Long,
  val endMillis: Long,
  val sourceLabel: String,
)

/**
 * Voice note transcript entry (never stores raw microphone audio files).
 */
data class VoiceNoteItem(
  val id: String,
  val title: String,
  val transcript: String,
  val estimatedDurationSec: Int,
  val createdAtMillis: Long,
)

/**
 * Contextual, privacy-preserving smart suggestion derived on-device from mood, memory, notes, and schedule.
 */
data class AISuggestionItem(
  val id: String,
  val titleBangla: String,
  val titleEnglish: String,
  val reason: String,
  val suggestedActionCommand: String,
  val priority: Int = 1,
)

/**
 * Reactive state machine for MAX's Smart Features System.
 */
sealed class SmartFeatureState {
  data object Idle : SmartFeatureState()

  data class Working(
    val featureName: String,
  ) : SmartFeatureState()

  data class TranslationReady(
    val record: TranslationRecord,
  ) : SmartFeatureState()

  data class ReminderScheduled(
    val reminder: ReminderItem,
  ) : SmartFeatureState()

  data class NoteSaved(
    val note: SmartNoteItem,
  ) : SmartFeatureState()

  data class CalendarLoaded(
    val events: List<CalendarEventItem>,
    val summary: String,
  ) : SmartFeatureState()

  data class VoiceNoteSaved(
    val voiceNote: VoiceNoteItem,
  ) : SmartFeatureState()

  data class SuggestionsReady(
    val suggestions: List<AISuggestionItem>,
  ) : SmartFeatureState()

  data class PermissionRequired(
    val featureName: String,
    val reason: String,
  ) : SmartFeatureState()

  data class BlockedByPrivacyPolicy(
    val reason: String,
  ) : SmartFeatureState()

  data class Error(
    val message: String,
  ) : SmartFeatureState()
}

interface SmartEventListener {
  fun onSmartAnnouncement(
    bangla: String,
    english: String,
    speakAloud: Boolean = true,
  )
}

/**
 * Coordinates MAX's Smart Features ([TranslationManager], [ReminderManager], [NotesManager],
 * [CalendarManager], [VoiceNotesManager], [AISuggestionManager]) with [MAXCore],
 * [ConversationManager], [EmotionManager], [MemoryManager], [PermissionManager], and [TTSManager].
 */
class SmartFeaturesController(
  val translationManager: TranslationManager,
  val reminderManager: ReminderManager,
  val notesManager: NotesManager,
  val calendarManager: CalendarManager,
  val voiceNotesManager: VoiceNotesManager,
  val aiSuggestionManager: AISuggestionManager,
) {
  private val _state = kotlinx.coroutines.flow.MutableStateFlow<SmartFeatureState>(SmartFeatureState.Idle)
  val state: kotlinx.coroutines.flow.StateFlow<SmartFeatureState> = _state

  private var listener: SmartEventListener? = null

  fun setEventListener(eventListener: SmartEventListener?) {
    listener = eventListener
  }

  fun translateText(
    sourceText: String,
    direction: TranslationDirection? = null,
    speakAloud: Boolean = true,
  ): Boolean {
    _state.value = SmartFeatureState.Working("Translation")
    return translationManager.translate(sourceText, direction).fold(
      onSuccess = { record ->
        _state.value = SmartFeatureState.TranslationReady(record)
        listener?.onSmartAnnouncement(
          bangla = "অনুবাদ সম্পন্ন: ${record.translatedText}",
          english = "Translation: ${record.translatedText}",
          speakAloud = speakAloud,
        )
        true
      },
      onFailure = { err ->
        _state.value = SmartFeatureState.Error(err.message ?: "Translation failed.")
        false
      },
    )
  }

  fun scheduleReminder(
    title: String,
    triggerAtMillis: Long,
    initiatedByUser: Boolean,
    requestNotificationIfNeeded: Boolean = false,
  ): Boolean {
    if (!initiatedByUser) {
      _state.value = SmartFeatureState.BlockedByPrivacyPolicy("Automatic reminders are blocked.")
      return false
    }
    return reminderManager.scheduleReminder(
      title = title,
      triggerAtMillis = triggerAtMillis,
      initiatedByUser = true,
      requestNotificationIfNeeded = requestNotificationIfNeeded,
    ).fold(
      onSuccess = { rem ->
        _state.value = SmartFeatureState.ReminderScheduled(rem)
        aiSuggestionManager.generateContextualSuggestions()
        listener?.onSmartAnnouncement(
          bangla = "${rem.dueLabel}-এর জন্য '${rem.title}' রিমাইন্ডার সেট করা হয়েছে।",
          english = "Reminder '${rem.title}' scheduled for ${rem.dueLabel}.",
          speakAloud = true,
        )
        true
      },
      onFailure = { err ->
        _state.value = SmartFeatureState.Error(err.message ?: "Could not set reminder.")
        false
      },
    )
  }

  fun addNote(
    title: String,
    content: String,
    alsoRememberInMemory: Boolean,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!initiatedByUser) {
      _state.value = SmartFeatureState.BlockedByPrivacyPolicy("Automatic note creation is blocked.")
      return false
    }
    return notesManager.addNote(title, content, alsoRememberInMemory, initiatedByUser = true).fold(
      onSuccess = { note ->
        _state.value = SmartFeatureState.NoteSaved(note)
        aiSuggestionManager.generateContextualSuggestions()
        listener?.onSmartAnnouncement(
          bangla = "'${note.title}' নোটটি নিরাপদে সংরক্ষণ করা হয়েছে।",
          english = "Saved your note '${note.title}' safely.",
          speakAloud = true,
        )
        true
      },
      onFailure = { err ->
        _state.value = SmartFeatureState.Error(err.message ?: "Note could not be saved.")
        false
      },
    )
  }

  fun loadTodayCalendar(
    initiatedByUser: Boolean,
    preferBangla: Boolean,
  ): Boolean {
    if (!initiatedByUser) {
      _state.value = SmartFeatureState.BlockedByPrivacyPolicy("Automatic calendar access is blocked.")
      return false
    }
    return calendarManager.loadTodaySchedule(initiatedByUser = true, preferBangla = preferBangla).fold(
      onSuccess = { (events, summary) ->
        _state.value = SmartFeatureState.CalendarLoaded(events, summary)
        listener?.onSmartAnnouncement(bangla = summary, english = summary, speakAloud = true)
        true
      },
      onFailure = { err ->
        _state.value =
          SmartFeatureState.PermissionRequired(
            featureName = "Calendar",
            reason = err.message ?: "Calendar permission required.",
          )
        false
      },
    )
  }

  fun saveVoiceNote(
    title: String,
    transcript: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!initiatedByUser) {
      _state.value = SmartFeatureState.BlockedByPrivacyPolicy("Automatic voice note recording is blocked.")
      return false
    }
    return voiceNotesManager.saveVoiceNoteTranscript(title, transcript, initiatedByUser = true).fold(
      onSuccess = { vn ->
        _state.value = SmartFeatureState.VoiceNoteSaved(vn)
        listener?.onSmartAnnouncement(
          bangla = "ভয়েস নোট '${vn.title}' টেক্সট আকারে সংরক্ষণ করা হয়েছে (কোনো অডিও সেভ করা হয়নি)।",
          english = "Voice note '${vn.title}' saved as text without storing audio.",
          speakAloud = true,
        )
        true
      },
      onFailure = { err ->
        _state.value = SmartFeatureState.Error(err.message ?: "Voice note failed.")
        false
      },
    )
  }

  fun refreshSuggestions(): List<AISuggestionItem> {
    val items = aiSuggestionManager.generateContextualSuggestions()
    _state.value = SmartFeatureState.SuggestionsReady(items)
    return items
  }

  fun handleVoiceCommand(
    input: String,
    preferBangla: Boolean,
    initiatedByUser: Boolean,
  ): String? {
    val lower = input.lowercase().trim()

    translationManager.extractTranslationPayload(input)?.let { (payload, direction) ->
      translateText(payload, direction, speakAloud = false)
      val translated = translationManager.lastRecord.value?.translatedText.orEmpty()
      return if (preferBangla) {
        "অনুবাদ (${direction.displayLabel}): $translated"
      } else {
        "Translation (${direction.displayLabel}): $translated"
      }
    }

    if (lower.contains("রিমাইন্ডার") || lower.contains("reminder") || lower.contains("remind me")) {
      if (!initiatedByUser) return null
      val res = reminderManager.scheduleFromVoiceCommand(input, initiatedByUser = true)
      val rem = res.getOrNull() ?: return null
      _state.value = SmartFeatureState.ReminderScheduled(rem)
      aiSuggestionManager.generateContextualSuggestions()
      return if (preferBangla) {
        "${rem.dueLabel}-এর জন্য '${rem.title}' রিমাইন্ডার সেট করা হয়েছে।"
      } else {
        "Scheduled reminder '${rem.title}' for ${rem.dueLabel}."
      }
    }

    if (lower.contains("ভয়েস নোট") || lower.contains("voice note")) {
      val clean =
        input
          .replace(Regex("(?i)max[,:\\s-]*"), "")
          .replace(Regex("ভয়েস নোট( রাখো| লিখো)?|(?i)voice note[:\\s-]*"), "")
          .trim()
          .ifBlank { "Quick voice memo" }
      saveVoiceNote("", clean, initiatedByUser = initiatedByUser)
      return if (preferBangla) {
        "ভয়েস নোট টেক্সট আকারে সংরক্ষণ করা হয়েছে।"
      } else {
        "Saved your voice note transcript cleanly."
      }
    }

    if (lower.contains("নোট লিখে") || lower.contains("নোট রাখো") || lower.contains("write a note") || lower.contains("take a note")) {
      val content = notesManager.extractNoteFromVoiceCommand(input)
      addNote("", content, alsoRememberInMemory = true, initiatedByUser = initiatedByUser)
      return if (preferBangla) {
        "নোট সংরক্ষণ করা হয়েছে: $content"
      } else {
        "Saved note: $content"
      }
    }

    if (lower.contains("ক্যালেন্ডার") || lower.contains("calendar") || lower.contains("schedule today")) {
      val ok = loadTodayCalendar(initiatedByUser = initiatedByUser, preferBangla = preferBangla)
      return if (ok) {
        calendarManager.lastSummary.value
      } else {
        if (preferBangla) {
          "আজকের ক্যালেন্ডার দেখতে অনুগ্রহ করে Calendar অনুমতি নিশ্চিত করুন।"
        } else {
          "Please confirm Calendar permission to view today's events."
        }
      }
    }

    if (lower.contains("পরামর্শ") || lower.contains("সাজেশন") || lower.contains("suggestion") || lower.contains("suggest")) {
      val items = refreshSuggestions()
      val top = items.firstOrNull()
      return if (top != null) {
        if (preferBangla) "আপনার জন্য পরামর্শ: ${top.titleBangla}" else "Suggestion: ${top.titleEnglish}"
      } else {
        null
      }
    }

    return null
  }
}
