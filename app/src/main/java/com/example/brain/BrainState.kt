package com.example.brain

/**
 * High-level intents understood by MAX AI Brain beyond naive single keywords.
 */
enum class BrainIntent {
  MEMORY_COMMAND,
  GREETING_CHECKIN,
  EMOTIONAL_SUPPORT,
  PERSONAL_RECALL,
  WEATHER_LOOKUP,
  NEWS_SUMMARY,
  WEB_RESEARCH,
  YOUTUBE_SEARCH,
  WIKIPEDIA_LOOKUP,
  CALCULATION,
  TIMER_OR_STOPWATCH,
  ALARM_SCHEDULE,
  TRANSLATION,
  REMINDER_OR_NOTE,
  CALENDAR_SCHEDULE,
  CALL_CONTACT,
  SEND_SMS,
  CAMERA_VISION,
  GALLERY_MEDIA,
  FILE_DOCUMENT,
  APP_LAUNCH,
  FLASHLIGHT_CONTROL,
  CLARIFICATION_ANSWER,
  FOLLOW_UP_CONTINUATION,
  DISMISSAL,
  GENERAL_QUESTION,
}

enum class ExecutionMode {
  OFFLINE_LOCAL,
  REQUIRES_INTERNET_PERMISSION,
  INTERNET_EXECUTION,
  NEEDS_CLARIFICATION,
  DEVICE_ACTION_DISPATCH,
  GRACEFUL_ERROR,
}

data class IntentAnalysis(
  val primaryIntent: BrainIntent,
  val confidence: Float,
  val requiresInternet: Boolean,
  val isPersonalQuery: Boolean,
  val extractedSlots: Map<String, String> = emptyMap(),
  val missingSlots: List<String> = emptyList(),
  val summaryLabel: String = "",
)

data class PendingClarification(
  val intent: BrainIntent,
  val missingSlot: String,
  val originalQuery: String,
  val questionBangla: String,
  val questionEnglish: String,
)

data class ConversationContextSnapshot(
  val activeTopic: String? = null,
  val lastIntent: BrainIntent? = null,
  val pendingClarification: PendingClarification? = null,
  val turnCount: Int = 0,
  val mentionedEntities: List<String> = emptyList(),
  val recentSummary: String = "",
)

data class BrainDecision(
  val intent: BrainIntent,
  val mode: ExecutionMode,
  val shouldUseInternet: Boolean,
  val requiresInternetConsent: Boolean,
  val missingSlotQuestion: String? = null,
  val delegateToActionHandler: Boolean = false,
  val rewrittenCommand: String? = null,
  val memoryContextHint: String? = null,
  val suggestedTopic: String? = null,
)

sealed class BrainLifecycleState {
  data object Dormant : BrainLifecycleState()

  data class Thinking(
    val stage: String,
  ) : BrainLifecycleState()

  data class AwaitingClarification(
    val missingSlot: String,
    val intent: BrainIntent,
  ) : BrainLifecycleState()

  data class AskingInternetConsent(
    val topic: String,
  ) : BrainLifecycleState()

  data class Responded(
    val intent: BrainIntent,
    val usedInternet: Boolean,
  ) : BrainLifecycleState()

  data class ErrorFallback(
    val friendlyMessage: String,
  ) : BrainLifecycleState()
}
