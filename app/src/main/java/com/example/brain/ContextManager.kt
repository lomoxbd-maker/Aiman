package com.example.brain

import com.example.tts.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ContextManager — Lightweight active-conversation memory tailored for low-RAM devices.
 *
 * Tracks the active topic, pending clarification slots, and recent entities strictly while
 * a conversation is active, with bounded capacity and zero background persistence leaks.
 */
class ContextManager {

  private data class SessionTurn(
    val speaker: String,
    val text: String,
    val intent: BrainIntent?,
    val entities: List<String>,
  )

  private val activeTurns = ArrayDeque<SessionTurn>(MAX_ACTIVE_TURNS)

  private val _snapshot = MutableStateFlow(ConversationContextSnapshot())
  val snapshot: StateFlow<ConversationContextSnapshot> = _snapshot.asStateFlow()

  fun recordTurn(
    speaker: String,
    text: String,
    intent: BrainIntent? = null,
    topic: String? = null,
    entities: List<String> = emptyList(),
  ) {
    val clean = text.trim()
    if (clean.isEmpty()) return

    if (activeTurns.size >= MAX_ACTIVE_TURNS) {
      activeTurns.removeFirst()
    }
    activeTurns.addLast(
      SessionTurn(
        speaker = speaker,
        text = clean,
        intent = intent,
        entities = entities,
      ),
    )

    val mergedEntities =
      (activeTurns.flatMap { it.entities } + entities)
        .filter { it.isNotBlank() }
        .distinct()
        .takeLast(MAX_TRACKED_ENTITIES)

    val prev = _snapshot.value
    _snapshot.value =
      prev.copy(
        activeTopic = topic?.takeIf { it.isNotBlank() } ?: prev.activeTopic,
        lastIntent = intent ?: prev.lastIntent,
        turnCount = prev.turnCount + 1,
        mentionedEntities = mergedEntities,
        recentSummary = activeTurns.takeLast(3).joinToString(" | ") { "${it.speaker}: ${it.text}" },
      )
  }

  fun setPendingClarification(clarification: PendingClarification) {
    _snapshot.value = _snapshot.value.copy(pendingClarification = clarification)
  }

  fun clearPendingClarification() {
    if (_snapshot.value.pendingClarification != null) {
      _snapshot.value = _snapshot.value.copy(pendingClarification = null)
    }
  }

  fun buildResolvedClarificationCommand(
    pending: PendingClarification,
    answer: String,
    language: VoiceLanguage,
  ): String {
    val cleanAnswer = answer.trim()
    return when (pending.intent) {
      BrainIntent.WEATHER_LOOKUP ->
        if (language == VoiceLanguage.ENGLISH_US) "weather in $cleanAnswer"
        else "$cleanAnswer এর আবহাওয়া"
      BrainIntent.WEB_RESEARCH ->
        if (language == VoiceLanguage.ENGLISH_US) "search $cleanAnswer"
        else "Google-এ $cleanAnswer খুঁজে দেখো"
      BrainIntent.YOUTUBE_SEARCH ->
        if (language == VoiceLanguage.ENGLISH_US) "YouTube search $cleanAnswer"
        else "YouTube-এ $cleanAnswer সার্চ করো"
      BrainIntent.WIKIPEDIA_LOOKUP ->
        if (language == VoiceLanguage.ENGLISH_US) "wikipedia $cleanAnswer"
        else "উইকিপিডিয়া $cleanAnswer"
      BrainIntent.NEWS_SUMMARY ->
        if (language == VoiceLanguage.ENGLISH_US) "summarize news $cleanAnswer"
        else "$cleanAnswer খবর সংক্ষেপে বলো"
      BrainIntent.TRANSLATION ->
        if (language == VoiceLanguage.ENGLISH_US) "translate $cleanAnswer"
        else "অনুবাদ করো: $cleanAnswer"
      BrainIntent.CALL_CONTACT ->
        if (language == VoiceLanguage.ENGLISH_US) "call $cleanAnswer"
        else "$cleanAnswer কল করো"
      BrainIntent.SEND_SMS ->
        if (language == VoiceLanguage.ENGLISH_US) "sms $cleanAnswer"
        else "$cleanAnswer মেসেজ"
      BrainIntent.CALCULATION ->
        if (language == VoiceLanguage.ENGLISH_US) "calculate $cleanAnswer"
        else "হিসাব করো $cleanAnswer"
      else -> "${pending.originalQuery} $cleanAnswer".trim()
    }
  }

  fun clearActiveSession() {
    activeTurns.clear()
    _snapshot.value = ConversationContextSnapshot()
  }

  companion object {
    private const val MAX_ACTIVE_TURNS = 10
    private const val MAX_TRACKED_ENTITIES = 8
  }
}
