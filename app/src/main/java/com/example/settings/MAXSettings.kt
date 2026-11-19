package com.example.settings

import com.example.tts.SpeechStyle
import com.example.tts.VoiceLanguage

/**
 * Persisted configuration state for the MAX assistant foundation, wake word, and voice synthesis.
 */
data class MAXSettings(
  val wakeWordEnabled: Boolean = true,
  val wakeWordSensitivity: Float = 0.72f,
  val ttsSpeechRate: Float = 0.96f,
  val ttsPitch: Float = 1.0f,
  val voiceLanguage: VoiceLanguage = VoiceLanguage.BANGLA_BD,
  val speechStyle: SpeechStyle = SpeechStyle.CALM,
  val memoryRetentionEnabled: Boolean = true,
  val strictPrivacyLock: Boolean = true,
  val lowPowerStandbyMode: Boolean = true,
  val darkThemePreferred: Boolean = true,
)
