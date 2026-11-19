package com.example.speech

import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contract for MAX's speech recognition subsystem.
 */
interface SpeechController {
  val state: StateFlow<SpeechState>
  val isRecognitionServiceAvailable: StateFlow<Boolean>
  val lastTriggerSource: StateFlow<String?>

  fun initialize(recognitionAvailable: Boolean)

  /**
   * Transitions speech pipeline from dormant standby to prepared state ONLY after
   * wake-word ("MAX") verification or explicit user trigger.
   */
  fun prepareForVoiceConversation(triggerKeyword: String)

  /**
   * Returns speech subsystem back to dormant standby without keeping microphone open.
   */
  fun returnToStandby()

  fun release()
}

/**
 * SpeechManager — Prepares the speech recognition architecture for MAX.
 *
 * Strictly enforces the privacy rule that full speech recognition does NOT start before
 * wake-word detection ("MAX"), and falls back cleanly when a device has no speech recognizer.
 */
class SpeechManager(
  private val errorTracker: ErrorTracker,
) : SpeechController {

  private val _state = MutableStateFlow(SpeechState.UNINITIALIZED)
  override val state: StateFlow<SpeechState> = _state.asStateFlow()

  private val _isRecognitionServiceAvailable = MutableStateFlow(false)
  override val isRecognitionServiceAvailable: StateFlow<Boolean> =
    _isRecognitionServiceAvailable.asStateFlow()

  private val _lastTriggerSource = MutableStateFlow<String?>(null)
  override val lastTriggerSource: StateFlow<String?> = _lastTriggerSource.asStateFlow()

  override fun initialize(recognitionAvailable: Boolean) {
    _isRecognitionServiceAvailable.value = recognitionAvailable
    _state.value =
      if (recognitionAvailable) {
        SpeechState.DORMANT_STANDBY
      } else {
        errorTracker.logWarning(
          MODULE_NAME,
          "Speech recognition service unavailable on this device; text input fallback enabled.",
        )
        SpeechState.UNSUPPORTED_FALLBACK
      }
    errorTracker.logInfo(MODULE_NAME, "SpeechManager initialized in ${_state.value}.")
  }

  override fun prepareForVoiceConversation(triggerKeyword: String) {
    if (!triggerKeyword.equals(REQUIRED_WAKE_WORD, ignoreCase = true)) {
      errorTracker.logWarning(
        MODULE_NAME,
        "Rejected pre-wake speech preparation from unexpected trigger '$triggerKeyword'.",
      )
      return
    }
    _lastTriggerSource.value = triggerKeyword
    if (!_isRecognitionServiceAvailable.value) {
      _state.value = SpeechState.UNSUPPORTED_FALLBACK
      errorTracker.logWarning(
        MODULE_NAME,
        "Wake word '$triggerKeyword' received, but device lacks SpeechRecognizer; using text fallback.",
      )
      return
    }
    _state.value = SpeechState.PREPARED_FOR_CONVERSATION
    errorTracker.logInfo(
      MODULE_NAME,
      "Speech pipeline prepared for voice conversation following wake word '$triggerKeyword'.",
    )
  }

  override fun returnToStandby() {
    _state.value =
      if (_isRecognitionServiceAvailable.value) {
        SpeechState.DORMANT_STANDBY
      } else {
        SpeechState.UNSUPPORTED_FALLBACK
      }
    errorTracker.logInfo(MODULE_NAME, "SpeechManager returned to ${_state.value}.")
  }

  override fun release() {
    _lastTriggerSource.value = null
    _state.value = SpeechState.UNINITIALIZED
  }

  companion object {
    private const val MODULE_NAME = "SpeechManager"
    private const val REQUIRED_WAKE_WORD = "MAX"
  }
}
