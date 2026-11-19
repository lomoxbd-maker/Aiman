package com.example.emotion

import com.example.tts.SpeechStyle

/**
 * Emotional states supported by MAX to express natural human empathy and warmth.
 */
enum class EmotionType(
  val displayName: String,
  val mappedSpeechStyle: SpeechStyle,
) {
  HAPPY("Happy", SpeechStyle.FRIENDLY),
  CALM("Calm", SpeechStyle.CALM),
  CARING("Caring", SpeechStyle.CARING),
  SUPPORTIVE("Supportive", SpeechStyle.HELPFUL),
  EXCITED("Excited", SpeechStyle.FRIENDLY),
  SORRY("Sorry", SpeechStyle.CARING),
  RESPECTFUL("Respectful", SpeechStyle.CALM),
}

/**
 * Interpreted mood of the user's message or conversational context.
 */
enum class UserMood(val displayName: String) {
  HAPPY("Happy"),
  EXCITED("Excited"),
  SAD_OR_WORRIED("Sad / Worried"),
  NEEDS_HELP("Needs Help"),
  APOLOGETIC_OR_ERROR("Apologetic / Setback"),
  RESPECTFUL_OR_GRATEFUL("Respectful / Grateful"),
  NEUTRAL("Calm / Neutral"),
}

/**
 * Voice modulation hint prepared for [com.example.tts.TTSManager] so speech prosody
 * adapts naturally to MAX's current emotional state.
 */
data class EmotionalVoiceProfile(
  val emotion: EmotionType,
  val speechStyle: SpeechStyle,
  val rateScale: Float,
  val pitchScale: Float,
  val pauseMs: Long,
)

/**
 * Immutable snapshot of MAX's active emotional state.
 */
data class EmotionState(
  val currentEmotion: EmotionType = EmotionType.CALM,
  val detectedUserMood: UserMood = UserMood.NEUTRAL,
  val confidence: Float = 0.85f,
  val responseStyle: ResponseStyle = ResponseStyle.forEmotion(EmotionType.CALM),
  val voiceProfile: EmotionalVoiceProfile = ResponseStyle.forEmotion(EmotionType.CALM).toVoiceProfile(),
  val reasonSummary: String = "Resting in calm, attentive readiness.",
  val updatedAtMillis: Long = System.currentTimeMillis(),
)
