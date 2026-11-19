package com.example.emotion

import com.example.tts.VoiceLanguage
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contract for MAX's emotion understanding and natural personality generator.
 */
interface EmotionControllerContract {
  val emotionState: StateFlow<EmotionState>

  fun analyzeAndRespond(
    userInput: String,
    language: VoiceLanguage,
  ): Pair<EmotionState, String>

  fun setEmotion(emotion: EmotionType, reason: String = "System state transition")

  fun nextNaturalPhrase(emotion: EmotionType, language: VoiceLanguage): String

  fun reset()
}

/**
 * EmotionManager — Understands user mood, adapts MAX's emotional state across
 * Happy, Calm, Caring, Supportive, Excited, Sorry, and Respectful, prepares voice prosody
 * profiles for TTSManager, and rotates phrasing so MAX never repeats identical sentences.
 */
class EmotionManager(
  private val detector: EmotionDetectorContract = EmotionDetector(),
  private val errorTracker: ErrorTracker? = null,
) : EmotionControllerContract {

  private val lock = Any()
  // Bounded ring buffer of recently used phrases to prevent repetitive robotic replies.
  private val recentPhraseHistory = ArrayDeque<String>(MAX_HISTORY)

  private val _emotionState = MutableStateFlow(EmotionState())
  override val emotionState: StateFlow<EmotionState> = _emotionState.asStateFlow()

  override fun analyzeAndRespond(
    userInput: String,
    language: VoiceLanguage,
  ): Pair<EmotionState, String> {
    val detection = detector.detect(userInput)
    val style = ResponseStyle.forEmotion(detection.suggestedEmotion)
    val newState =
      EmotionState(
        currentEmotion = detection.suggestedEmotion,
        detectedUserMood = detection.userMood,
        confidence = detection.confidence,
        responseStyle = style,
        voiceProfile = style.toVoiceProfile(),
        reasonSummary = detection.reason,
        updatedAtMillis = System.currentTimeMillis(),
      )
    _emotionState.value = newState

    val reply = nextNaturalPhrase(detection.suggestedEmotion, language)
    errorTracker?.logInfo(
      MODULE_NAME,
      "Mood=${detection.userMood.name} -> MAX=${detection.suggestedEmotion.name} (${style.toneDescription})",
    )
    return newState to reply
  }

  override fun setEmotion(emotion: EmotionType, reason: String) {
    val style = ResponseStyle.forEmotion(emotion)
    _emotionState.value =
      _emotionState.value.copy(
        currentEmotion = emotion,
        responseStyle = style,
        voiceProfile = style.toVoiceProfile(),
        reasonSummary = reason,
        updatedAtMillis = System.currentTimeMillis(),
      )
  }

  override fun nextNaturalPhrase(emotion: EmotionType, language: VoiceLanguage): String {
    val style = ResponseStyle.forEmotion(emotion)
    val pool =
      if (language == VoiceLanguage.ENGLISH_US) {
        style.englishPhrases
      } else {
        style.banglaPhrases
      }

    synchronized(lock) {
      val freshCandidate = pool.firstOrNull { !recentPhraseHistory.contains(it) } ?: pool.first()
      if (recentPhraseHistory.size >= MAX_HISTORY) {
        recentPhraseHistory.removeFirst()
      }
      recentPhraseHistory.addLast(freshCandidate)
      return freshCandidate
    }
  }

  override fun reset() {
    synchronized(lock) {
      recentPhraseHistory.clear()
    }
    _emotionState.value = EmotionState()
  }

  companion object {
    private const val MODULE_NAME = "EmotionManager"
    private const val MAX_HISTORY = 8
  }
}
