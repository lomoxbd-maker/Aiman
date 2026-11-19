package com.example.conversation

import com.example.brain.AIBrainManager
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
 * [AIBrainManager], [MemoryController], [EmotionControllerContract], [PersonalityController],
 * and [TTSController].
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
  private var brainManager: AIBrainManager? = null

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

  fun setBrainManager(brain: AIBrainManager?) {
    brainManager = brain
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
    if (memCommand != null) {
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

    val activeBrain = brainManager
    if (activeBrain != null) {
      val brainOutcome =
        activeBrain.thinkAndPlan(
          userInput = cleanInput,
          language = language,
          actionExecutor = visionCommandHandler,
        )
      voiceOutput?.applyEmotionalVoiceProfile(brainOutcome.emotionState.voiceProfile)
      appendTurn(speaker = "MAX", content = brainOutcome.reply)
      if (speakAloud) {
        voiceOutput?.speak(brainOutcome.reply, brainOutcome.emotionState.voiceProfile.speechStyle)
      }
      return brainOutcome.emotionState to brainOutcome.reply
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

  /**
   * Produces the calm post-task follow-up question for Sanju after completing any request.
   */
  fun craftFollowUpPrompt(language: VoiceLanguage): String {
    val savedName = memoryController?.getUserName()
    return when {
      language == VoiceLanguage.ENGLISH_US -> {
        val name = savedName?.takeIf { it.isNotBlank() } ?: "Sanju"
        "$name, is there anything else I can help you with?"
      }
      !savedName.isNullOrBlank() && !savedName.equals("সানজু", ignoreCase = true) && !savedName.equals("Sanju", ignoreCase = true) ->
        "$savedName, আপনাকে কি আর কোনোভাবে সাহায্য করতে পারি?"
      else -> FOLLOW_UP_PROMPT_SANJU_BANGLA
    }
  }

  fun isDismissalOrDoneCue(userText: String): Boolean {
    val lower = userText.trim().lowercase()
    if (lower.isBlank()) return false
    val exactOrContained =
      listOf(
        "না ধন্যবাদ",
        "না আর কিছু না",
        "আর কিছু না",
        "লাগবে না",
        "প্রয়োজন নেই",
        "থাক এখন",
        "no thanks",
        "nothing else",
        "that is all",
        "that's all",
        "i'm good",
        "all good",
        "stop listening",
      )
    if (exactOrContained.any { lower.contains(it) }) return true
    return lower == "না" || lower == "no" || lower == "nope" || lower == "done" || lower == "ok bye"
  }

  fun craftWakeStandbyFarewell(language: VoiceLanguage): String {
    val savedName = memoryController?.getUserName()
    val name = savedName?.takeIf { it.isNotBlank() } ?: if (language == VoiceLanguage.ENGLISH_US) "Sanju" else "সানজু"
    return if (language == VoiceLanguage.ENGLISH_US) {
      "Alright $name, I will wait calmly in Wake Mode. Just say MAX whenever you need me."
    } else {
      "ঠিক আছে $name, আমি ওয়েক মোডে অপেক্ষায় রইলাম। প্রয়োজন হলেই MAX বলে ডাকবেন।"
    }
  }

  override fun returnToDormant() {
    _state.value = ConversationState.DORMANT
  }

  override fun release() {
    voiceOutput = null
    visionCommandHandler = null
    brainManager?.release()
    brainManager = null
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
    const val FOLLOW_UP_PROMPT_SANJU_BANGLA = "সানজু, আপনাকে কি আর কোনোভাবে সাহায্য করতে পারি?"
    const val FOLLOW_UP_PROMPT_SANJU_ENGLISH = "Sanju, is there anything else I can help you with?"
  }
}
