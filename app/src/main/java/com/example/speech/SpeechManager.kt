package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Callback interface notifying MAXCore / Conversation UI when on-device speech recognition
 * delivers a partial or final utterance after wake-word detection.
 */
interface SpeechRecognitionCallback {
  fun onPartialTranscript(text: String) {}
  fun onFinalTranscript(text: String)
  fun onSpeechTimeoutOrError(reason: String) {}
}

/**
 * Contract for MAX's speech recognition subsystem.
 */
interface SpeechController {
  val state: StateFlow<SpeechState>
  val isRecognitionServiceAvailable: StateFlow<Boolean>
  val lastTriggerSource: StateFlow<String?>
  val lastRecognizedTranscript: StateFlow<String?>

  fun initialize(recognitionAvailable: Boolean)

  /**
   * Transitions speech pipeline from dormant standby to prepared state ONLY after
   * wake-word ("MAX") verification or explicit user trigger.
   */
  fun prepareForVoiceConversation(triggerKeyword: String)

  /**
   * Starts Android SpeechRecognizer strictly when already armed after wake word ("MAX")
   * or explicit user trigger, never storing raw audio recordings.
   */
  fun startRecognitionSession(
    languageTag: String = "bn-BD",
    callback: SpeechRecognitionCallback? = null,
  ): Boolean

  /**
   * Stops active speech recognition and releases native recognizer handles.
   */
  fun stopRecognitionSession()

  /**
   * Returns speech subsystem back to dormant standby without keeping microphone open.
   */
  fun returnToStandby()

  fun release()
}

/**
 * SpeechManager — Privacy-gated Speech Recognition controller for MAX.
 *
 * Enforces the privacy contract that full speech recognition NEVER starts before
 * wake-word detection ("MAX"), wraps Android's [SpeechRecognizer] on compatible devices,
 * releases resources immediately after task completion, and falls back gracefully to text input
 * when a device lacks a recognition service.
 */
class SpeechManager(
  private val errorTracker: ErrorTracker,
  private val context: Context? = null,
) : SpeechController {

  private var speechRecognizer: SpeechRecognizer? = null
  private var activeCallback: SpeechRecognitionCallback? = null

  private val _state = MutableStateFlow(SpeechState.UNINITIALIZED)
  override val state: StateFlow<SpeechState> = _state.asStateFlow()

  private val _isRecognitionServiceAvailable = MutableStateFlow(false)
  override val isRecognitionServiceAvailable: StateFlow<Boolean> =
    _isRecognitionServiceAvailable.asStateFlow()

  private val _lastTriggerSource = MutableStateFlow<String?>(null)
  override val lastTriggerSource: StateFlow<String?> = _lastTriggerSource.asStateFlow()

  private val _lastRecognizedTranscript = MutableStateFlow<String?>(null)
  override val lastRecognizedTranscript: StateFlow<String?> =
    _lastRecognizedTranscript.asStateFlow()

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

  override fun startRecognitionSession(
    languageTag: String,
    callback: SpeechRecognitionCallback?,
  ): Boolean {
    if (_state.value != SpeechState.PREPARED_FOR_CONVERSATION &&
      _state.value != SpeechState.LISTENING_ACTIVE
    ) {
      errorTracker.logWarning(
        MODULE_NAME,
        "Blocked SpeechRecognizer start before wake-word 'MAX' preparation (state=${_state.value}).",
      )
      return false
    }

    activeCallback = callback
    val appContext = context?.applicationContext
    if (appContext == null || !_isRecognitionServiceAvailable.value) {
      errorTracker.logInfo(
        MODULE_NAME,
        "Speech recognition in manual/text-ready mode (context or recognizer unavailable).",
      )
      return false
    }

    return runCatching {
      destroyRecognizerInternal()
      val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
      recognizer.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SpeechState.LISTENING_ACTIVE
          }

          override fun onBeginningOfSpeech() = Unit

          override fun onRmsChanged(rmsdB: Float) = Unit

          override fun onBufferReceived(buffer: ByteArray?) = Unit

          override fun onEndOfSpeech() = Unit

          override fun onError(error: Int) {
            val reason = mapRecognitionError(error)
            errorTracker.logWarning(MODULE_NAME, "SpeechRecognizer finished: $reason")
            _state.value =
              if (_isRecognitionServiceAvailable.value) SpeechState.PREPARED_FOR_CONVERSATION
              else SpeechState.UNSUPPORTED_FALLBACK
            activeCallback?.onSpeechTimeoutOrError(reason)
          }

          override fun onResults(results: Bundle?) {
            val best =
              results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            _state.value =
              if (_isRecognitionServiceAvailable.value) SpeechState.PREPARED_FOR_CONVERSATION
              else SpeechState.UNSUPPORTED_FALLBACK
            if (best.isNotBlank()) {
              _lastRecognizedTranscript.value = best
              activeCallback?.onFinalTranscript(best)
            } else {
              activeCallback?.onSpeechTimeoutOrError("No speech detected.")
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partial =
              partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotBlank()) {
              activeCallback?.onPartialTranscript(partial)
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) = Unit
        },
      )

      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
          )
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
      speechRecognizer = recognizer
      recognizer.startListening(intent)
      _state.value = SpeechState.LISTENING_ACTIVE
      errorTracker.logInfo(MODULE_NAME, "Active SpeechRecognizer listening ($languageTag).")
      true
    }.getOrElse { err ->
      errorTracker.logWarning(
        MODULE_NAME,
        "SpeechRecognizer start fell back safely to text mode: ${err.message}",
      )
      _state.value = SpeechState.PREPARED_FOR_CONVERSATION
      false
    }
  }

  override fun stopRecognitionSession() {
    activeCallback = null
    destroyRecognizerInternal()
    if (_state.value == SpeechState.LISTENING_ACTIVE) {
      _state.value =
        if (_isRecognitionServiceAvailable.value) SpeechState.PREPARED_FOR_CONVERSATION
        else SpeechState.UNSUPPORTED_FALLBACK
    }
  }

  override fun returnToStandby() {
    activeCallback = null
    destroyRecognizerInternal()
    _state.value =
      if (_isRecognitionServiceAvailable.value) {
        SpeechState.DORMANT_STANDBY
      } else {
        SpeechState.UNSUPPORTED_FALLBACK
      }
    errorTracker.logInfo(MODULE_NAME, "SpeechManager returned to ${_state.value}.")
  }

  override fun release() {
    activeCallback = null
    destroyRecognizerInternal()
    _lastTriggerSource.value = null
    _lastRecognizedTranscript.value = null
    _state.value = SpeechState.UNINITIALIZED
  }

  private fun destroyRecognizerInternal() {
    runCatching {
      speechRecognizer?.stopListening()
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    }
    speechRecognizer = null
  }

  private fun mapRecognitionError(code: Int): String =
    when (code) {
      SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
      SpeechRecognizer.ERROR_AUDIO -> "Audio input unavailable"
      SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network recognition unavailable"
      else -> "Speech status ($code)"
    }

  companion object {
    private const val MODULE_NAME = "SpeechManager"
    private const val REQUIRED_WAKE_WORD = "MAX"
  }
}
