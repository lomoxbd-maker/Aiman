package com.example.wakeword

/**
 * Represents the operational lifecycle of the MAX Wake Word system.
 */
sealed class WakeWordState {
  /** Wake word system is disarmed; microphone is completely closed. */
  data object Idle : WakeWordState()

  /**
   * MAX is in low-power standby mode waiting for the "MAX" keyword.
   * Ephemeral audio frames are analyzed in RAM and discarded immediately.
   */
  data class StandbyListening(
    val keyword: String,
    val isLiveMicrophoneActive: Boolean,
  ) : WakeWordState()

  /**
   * Keyword "MAX" was detected. Notifies MAXCore to activate assistant listening
   * and prepare voice conversation.
   */
  data class Detected(
    val keyword: String,
    val confidence: Float,
    val detectedAtMillis: Long,
  ) : WakeWordState()

  /** Brief cooldown after detection before returning to standby or pausing. */
  data object PausedForConversation : WakeWordState()

  /** Microphone permission is required before live wake-word capture can be armed. */
  data object PermissionRequired : WakeWordState()

  /** Device does not have microphone hardware; manual/text fallback applies. */
  data class UnsupportedFallback(val reason: String) : WakeWordState()

  /** Recoverable error while keeping user privacy intact. */
  data class Error(val message: String) : WakeWordState()
}
