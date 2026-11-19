package com.example.tts

/**
 * Expressive speaking styles supported by MAX's voice synthesis subsystem.
 * Each style applies subtle natural rate and pitch shaping to avoid robotic delivery.
 */
enum class SpeechStyle(
  val displayName: String,
  val rateMultiplier: Float,
  val pitchMultiplier: Float,
  val sentencePauseMs: Long,
) {
  FRIENDLY(
    displayName = "Friendly",
    rateMultiplier = 1.02f,
    pitchMultiplier = 1.05f,
    sentencePauseMs = 260L,
  ),
  CALM(
    displayName = "Calm",
    rateMultiplier = 0.90f,
    pitchMultiplier = 0.96f,
    sentencePauseMs = 380L,
  ),
  HELPFUL(
    displayName = "Helpful",
    rateMultiplier = 0.98f,
    pitchMultiplier = 1.00f,
    sentencePauseMs = 300L,
  ),
  CARING(
    displayName = "Caring",
    rateMultiplier = 0.88f,
    pitchMultiplier = 0.99f,
    sentencePauseMs = 420L,
  ),
}
