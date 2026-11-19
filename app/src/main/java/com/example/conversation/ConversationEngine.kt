package com.example.conversation

import com.example.tts.SpeechStyle
import com.example.tts.TTSController
import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConversationState {
  DORMANT,
  AWAITING_USER_UTTERANCE,
  READY_FOR_FUTURE_LLM,
}

data class ConversationTurn(
  val id: String,
  val speaker: String,
  val content: String,
  val timestampMillis: Long,
)

/**
 * Interface for multi-turn dialogue orchestration in MAX, wired cleanly to voice output.
 */
interface ConversationController {
  val state: StateFlow<ConversationState>
  val recentTurns: StateFlow<List<ConversationTurn>>

  fun attachVoiceOutput(ttsController: TTSController)

  fun prepareSession(triggerKeyword: String)

  fun deliverAssistantResponse(text: String, style: SpeechStyle = SpeechStyle.CALM)

  fun returnToDormant()

  fun release()
}

/**
 * ConversationEngine — Dialogue pipeline for MAX prepared for future LLM + Bangla TTS replies.
 */
class ConversationEngine(
  private val errorTracker: ErrorTracker,
) : ConversationController {

  private var voiceOutput: TTSController? = null

  private val _state = MutableStateFlow(ConversationState.DORMANT)
  override val state: StateFlow<ConversationState> = _state.asStateFlow()

  private val _recentTurns = MutableStateFlow<List<ConversationTurn>>(emptyList())
  override val recentTurns: StateFlow<List<ConversationTurn>> = _recentTurns.asStateFlow()

  override fun attachVoiceOutput(ttsController: TTSController) {
    voiceOutput = ttsController
  }

  override fun prepareSession(triggerKeyword: String) {
    _state.value = ConversationState.AWAITING_USER_UTTERANCE
    errorTracker.logInfo(
      MODULE_NAME,
      "Conversation session armed following wake word '$triggerKeyword'.",
    )
  }

  override fun deliverAssistantResponse(text: String, style: SpeechStyle) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return

    val turn =
      ConversationTurn(
        id = UUID.randomUUID().toString(),
        speaker = "MAX",
        content = trimmed,
        timestampMillis = System.currentTimeMillis(),
      )
    _recentTurns.value = (_recentTurns.value + turn).takeLast(MAX_RECENT_TURNS)
    voiceOutput?.speak(trimmed, style)
  }

  override fun returnToDormant() {
    _state.value = ConversationState.DORMANT
  }

  override fun release() {
    voiceOutput = null
    _recentTurns.value = emptyList()
    _state.value = ConversationState.DORMANT
  }

  companion object {
    private const val MODULE_NAME = "ConversationEngine"
    private const val MAX_RECENT_TURNS = 20
  }
}
