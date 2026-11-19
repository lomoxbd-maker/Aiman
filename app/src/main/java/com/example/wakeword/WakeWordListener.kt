package com.example.wakeword

/**
 * Callback interface decoupling [WakeWordManager] from [com.example.core.MAXCore] and UI layers.
 */
interface WakeWordListener {
  /**
   * Invoked when the wake word ("MAX") is detected while in standby mode.
   */
  fun onWakeWordDetected(keyword: String, confidence: Float, detectedAtMillis: Long)

  /**
   * Invoked whenever [WakeWordManager] transitions between lifecycle states.
   */
  fun onWakeWordStateChanged(state: WakeWordState) {}

  /**
   * Invoked on recoverable wake-word errors without crashing the assistant core.
   */
  fun onWakeWordError(message: String, throwable: Throwable? = null) {}
}
