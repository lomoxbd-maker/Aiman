package com.example.wakeword

/**
 * Immutable configuration for MAX's wake-word detection subsystem.
 */
data class WakeWordConfig(
  val keyword: String = DEFAULT_KEYWORD,
  val sensitivity: Float = 0.72f,
  val sampleRateHz: Int = 16_000,
  val frameDurationMs: Int = 30,
  val standbyThrottleMs: Long = 30L,
  val saveAudioToDisk: Boolean = false,
  val allowFullSpeechBeforeWakeWord: Boolean = false,
) {
  init {
    require(!saveAudioToDisk) { "Privacy policy prohibits saving wake-word audio to disk." }
    require(!allowFullSpeechBeforeWakeWord) {
      "Full speech recognition must never start before wake-word detection."
    }
  }

  val frameSampleCount: Int
    get() = (sampleRateHz * frameDurationMs) / 1000

  companion object {
    const val DEFAULT_KEYWORD = "MAX"
  }
}
