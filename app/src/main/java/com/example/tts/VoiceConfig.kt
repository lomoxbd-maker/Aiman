package com.example.tts

import java.util.Locale

/**
 * Languages supported by MAX Voice with future multi-language extensibility.
 */
enum class VoiceLanguage(
  val tag: String,
  val displayLabel: String,
  val locale: Locale,
) {
  BANGLA_BD(
    tag = "bn-BD",
    displayLabel = "বাংলা (BD)",
    locale = Locale.Builder().setLanguage("bn").setRegion("BD").build(),
  ),
  BANGLA_IN(
    tag = "bn-IN",
    displayLabel = "বাংলা (IN)",
    locale = Locale.Builder().setLanguage("bn").setRegion("IN").build(),
  ),
  ENGLISH_US(
    tag = "en-US",
    displayLabel = "English (US)",
    locale = Locale.US,
  ),
  ;

  companion object {
    fun fromTag(tag: String): VoiceLanguage =
      entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: BANGLA_BD
  }
}

/**
 * Complete voice synthesis configuration for MAX.
 */
data class VoiceConfig(
  val language: VoiceLanguage = VoiceLanguage.BANGLA_BD,
  val speechStyle: SpeechStyle = SpeechStyle.CALM,
  val speechSpeed: Float = 0.94f,
  val pitch: Float = 1.0f,
  val preferOfflineNaturalVoice: Boolean = true,
  val naturalSentencePauses: Boolean = true,
) {
  fun effectiveSpeechRate(): Float =
    (speechSpeed * speechStyle.rateMultiplier).coerceIn(0.65f, 1.25f)

  fun effectivePitch(): Float =
    (pitch * speechStyle.pitchMultiplier).coerceIn(0.75f, 1.35f)
}
