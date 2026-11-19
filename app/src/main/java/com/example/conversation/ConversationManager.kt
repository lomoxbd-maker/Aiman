package com.example.conversation

import com.example.emotion.EmotionControllerContract
import com.example.emotion.EmotionState
import com.example.emotion.PersonalityController
import com.example.emotion.PersonalityManager
import com.example.memory.MemoryController
import com.example.tts.SpeechStyle
import com.example.tts.TTSController
import com.example.tts.VoiceLanguage
import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ConversationManager — Orchestrates natural human-like conversation turns between
 * [MemoryController], [EmotionControllerContract], [PersonalityController], and [TTSController].
 *
 * Handles Bangla Memory Commands directly during user conversation ("MAX, এটা মনে রাখো",
 * "MAX, এটা ভুলে যাও", "MAX, আমার Memory দেখাও", "MAX, সব Memory মুছে দাও").
 */
class ConversationManager(
  private val emotionManager: EmotionControllerContract,
  private val errorTracker: ErrorTracker,
  private val memoryController: MemoryController? = null,
  private val personalityManager: PersonalityController? =
    memoryController?.let { PersonalityManager(emotionManager, it) },
) : ConversationController {

  private var voiceOutput: TTSController? = null
  private var visionCommandHandler: ((String) -> String?)? = null

  private val _state = MutableStateFlow(ConversationState.DORMANT)
  override val state: StateFlow<ConversationState> = _state.asStateFlow()

  private val _recentTurns = MutableStateFlow<List<ConversationTurn>>(emptyList())
  override val recentTurns: StateFlow<List<ConversationTurn>> = _recentTurns.asStateFlow()

  override fun attachVoiceOutput(ttsController: TTSController) {
    voiceOutput = ttsController
  }

  fun setVisionCommandHandler(handler: ((String) -> String?)?) {
    visionCommandHandler = handler
  }

  override fun prepareSession(triggerKeyword: String) {
    _state.value = ConversationState.AWAITING_USER_UTTERANCE
    errorTracker.logInfo(
      MODULE_NAME,
      "ConversationManager armed following wake word '$triggerKeyword'.",
    )
  }

  /**
   * Processes user input:
   * 1. Checks for explicit Bangla/English Memory Commands first.
   * 2. Checks for explicit user-initiated Camera/Gallery requests when wired.
   * 3. Otherwise evaluates mood & crafts a personalized human reply via [PersonalityController].
   * 4. Applies emotional voice prosody and speaks via [TTSController] when requested.
   */
  fun processUserMessage(
    userText: String,
    language: VoiceLanguage,
    speakAloud: Boolean = true,
  ): Pair<EmotionState, String> {
    val cleanInput = userText.trim()
    if (cleanInput.isNotEmpty()) {
      appendTurn(speaker = "User", content = cleanInput)
    }

    val memCommand = memoryController?.parseCommand(cleanInput)
    if (memCommand != null && memoryController != null) {
      val result = memoryController.executeCommand(memCommand)
      val reply =
        if (language == VoiceLanguage.ENGLISH_US) result.messageEnglish else result.messageBangla
      val styleEmotion =
        if (result.accepted) com.example.emotion.EmotionType.RESPECTFUL
        else com.example.emotion.EmotionType.CARING
      emotionManager.setEmotion(styleEmotion, "Memory command handled")
      val currentEmotion = emotionManager.emotionState.value
      voiceOutput?.applyEmotionalVoiceProfile(currentEmotion.voiceProfile)
      appendTurn(speaker = "MAX", content = reply)
      if (speakAloud) {
        voiceOutput?.speak(reply, currentEmotion.voiceProfile.speechStyle)
      }
      return currentEmotion to reply
    }

    val visionReply = visionCommandHandler?.invoke(cleanInput)
    if (!visionReply.isNullOrBlank()) {
      emotionManager.setEmotion(com.example.emotion.EmotionType.SUPPORTIVE, "Vision action")
      val currentEmotion = emotionManager.emotionState.value
      voiceOutput?.applyEmotionalVoiceProfile(currentEmotion.voiceProfile)
      appendTurn(speaker = "MAX", content = visionReply)
      if (speakAloud) {
        voiceOutput?.speak(visionReply, currentEmotion.voiceProfile.speechStyle)
      }
      return currentEmotion to visionReply
    }

    val (emotionState, reply) =
      personalityManager?.craftPersonalizedReply(cleanInput, language)
        ?: emotionManager.analyzeAndRespond(cleanInput, language)

    voiceOutput?.applyEmotionalVoiceProfile(emotionState.voiceProfile)
    appendTurn(speaker = "MAX", content = reply)

    if (speakAloud) {
      voiceOutput?.speak(reply, emotionState.voiceProfile.speechStyle)
    }

    return emotionState to reply
  }

  /**
   * Speaks and logs Camera or Gallery feedback calmly using the active language and TTS system.
   */
  fun announceVisionOutcome(
    banglaMessage: String,
    englishMessage: String,
    language: VoiceLanguage,
    speakAloud: Boolean = true,
  ) {
    val message = if (language == VoiceLanguage.ENGLISH_US) englishMessage else banglaMessage
    appendTurn(speaker = "MAX", content = message)
    if (speakAloud) {
      voiceOutput?.speak(message, SpeechStyle.HELPFUL)
    }
  }

  override fun deliverAssistantResponse(text: String, style: SpeechStyle) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    appendTurn(speaker = "MAX", content = trimmed)
    voiceOutput?.speak(trimmed, style)
  }

  override fun returnToDormant() {
    _state.value = ConversationState.DORMANT
  }

  override fun release() {
    voiceOutput = null
    visionCommandHandler = null
    _recentTurns.value = emptyList()
    _state.value = ConversationState.DORMANT
  }

  private fun appendTurn(speaker: String, content: String) {
    val turn =
      ConversationTurn(
        id = UUID.randomUUID().toString(),
        speaker = speaker,
        content = content,
        timestampMillis = System.currentTimeMillis(),
      )
    _recentTurns.value = (_recentTurns.value + turn).takeLast(MAX_RECENT_TURNS)
  }

  companion object {
    private const val MODULE_NAME = "ConversationManager"
    private const val MAX_RECENT_TURNS = 16
  }
}
