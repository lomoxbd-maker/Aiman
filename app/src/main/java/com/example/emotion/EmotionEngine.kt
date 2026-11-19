package com.example.emotion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AffectiveTone {
  NEUTRAL_CALM,
  ATTENTIVE,
  EMPATHETIC,
  FOCUSED,
}

/**
 * Legacy Step 1 interface preserved for seamless compatibility alongside [EmotionManager].
 */
interface EmotionController {
  val currentTone: StateFlow<AffectiveTone>

  fun setTone(tone: AffectiveTone)

  fun reset()
}

/**
 * EmotionEngine — Bridges Step 1 tone controls to the upgraded [EmotionManager].
 */
class EmotionEngine(
  private val emotionManager: EmotionManager? = null,
) : EmotionController {
  private val _currentTone = MutableStateFlow(AffectiveTone.NEUTRAL_CALM)
  override val currentTone: StateFlow<AffectiveTone> = _currentTone.asStateFlow()

  override fun setTone(tone: AffectiveTone) {
    _currentTone.value = tone
    when (tone) {
      AffectiveTone.NEUTRAL_CALM -> emotionManager?.setEmotion(EmotionType.CALM, "Neutral calm readiness")
      AffectiveTone.ATTENTIVE -> emotionManager?.setEmotion(EmotionType.RESPECTFUL, "Attentive listening active")
      AffectiveTone.EMPATHETIC -> emotionManager?.setEmotion(EmotionType.CARING, "Empathetic care mode")
      AffectiveTone.FOCUSED -> emotionManager?.setEmotion(EmotionType.SUPPORTIVE, "Supportive focus mode")
    }
  }

  override fun reset() {
    _currentTone.value = AffectiveTone.NEUTRAL_CALM
    emotionManager?.reset()
  }
}
