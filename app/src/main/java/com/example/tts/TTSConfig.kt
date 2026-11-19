package com.example.tts

/**
 * Configuration parameters for MAX's voice synthesis subsystem.
 */
data class TTSConfig(
  val speechRate: Float = 1.0f,
  val pitch: Float = 1.0f,
  val languageTag: String = "en-US",
)

enum class TTSState {
  UNINITIALIZED,
  INITIALIZING,
  READY,
  UNSUPPORTED_FALLBACK,
  ERROR,
}
