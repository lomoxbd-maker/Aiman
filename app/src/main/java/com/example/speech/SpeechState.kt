package com.example.speech

/**
 * Lifecycle states for [SpeechManager].
 *
 * By architecture and privacy contract, [SpeechManager] remains in [DORMANT_STANDBY] while
 * MAX awaits the "MAX" wake word. Full speech recognition is never started prior to wake-word
 * activation.
 */
enum class SpeechState {
  UNINITIALIZED,
  DORMANT_STANDBY,
  PREPARED_FOR_CONVERSATION,
  UNSUPPORTED_FALLBACK,
  ERROR,
}
