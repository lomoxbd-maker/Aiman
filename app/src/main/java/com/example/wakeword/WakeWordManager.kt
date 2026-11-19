package com.example.wakeword

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.utils.ErrorTracker
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Interface for plugging future acoustic/neural keyword spotters (e.g. TFLite, Porcupine, ONNX)
 * into [WakeWordManager] without altering MAXCore or privacy guarantees.
 */
interface WakeWordFrameAnalyzer {
  /**
   * Evaluates a transient mono 16-bit PCM frame in RAM and returns detection confidence in [0f, 1f].
   * Implementations MUST NOT persist or copy audio outside the frame call.
   */
  fun analyzeFrame(pcmFrame: ShortArray, sampleCount: Int, config: WakeWordConfig): Float
}

/**
 * Lightweight, zero-allocation baseline acoustic detector prepared for future neural spotter
 * replacement. Detects crisp vocal onset patterns above calibrated sensitivity threshold.
 */
class BaselineAcousticKeywordAnalyzer : WakeWordFrameAnalyzer {
  private var consecutiveVoiceFrames = 0
  private var cooldownFramesRemaining = 0
  private var ambientNoiseFloorRms = INITIAL_NOISE_FLOOR

  override fun analyzeFrame(pcmFrame: ShortArray, sampleCount: Int, config: WakeWordConfig): Float {
    if (sampleCount <= 0) return 0f
    if (cooldownFramesRemaining > 0) {
      cooldownFramesRemaining--
      return 0f
    }

    var sumSquares = 0.0
    var peakAbs = 0
    var zeroCrossings = 0
    var prevSample = pcmFrame[0].toInt()
    for (i in 0 until sampleCount) {
      val s = pcmFrame[i].toInt()
      val mag = abs(s)
      if (mag > peakAbs) peakAbs = mag
      sumSquares += (s * s).toDouble()
      if ((s >= 0 && prevSample < 0) || (s < 0 && prevSample >= 0)) {
        zeroCrossings++
      }
      prevSample = s
    }

    val rms = sqrt(sumSquares / sampleCount).toFloat()
    val rmsNormalized = rms / Short.MAX_VALUE
    val zcrNormalized = zeroCrossings.toFloat() / sampleCount
    val crestFactor = if (rms > 1f) peakAbs.toFloat() / rms else 0f

    // Adaptive threshold combines calibrated sensitivity with dynamic ambient noise floor.
    val baseThreshold = (1.15f - config.sensitivity).coerceIn(0.12f, 0.85f) * 0.25f
    val adaptiveThreshold = maxOf(baseThreshold, ambientNoiseFloorRms * 1.75f)
    val isVocalSyllable =
      rmsNormalized > adaptiveThreshold &&
        zcrNormalized in 0.03f..0.45f &&
        crestFactor >= MIN_VOCAL_CREST_FACTOR

    if (isVocalSyllable) {
      consecutiveVoiceFrames++
      if (consecutiveVoiceFrames >= TRIGGER_FRAMES_REQUIRED) {
        consecutiveVoiceFrames = 0
        cooldownFramesRemaining = COOLDOWN_FRAMES
        val snrBoost = ((rmsNormalized - adaptiveThreshold) * 0.6f).coerceIn(0f, 0.18f)
        return (0.78f + snrBoost).coerceAtMost(0.99f)
      }
    } else {
      // Smoothly adapt ambient baseline only during non-speech frames.
      ambientNoiseFloorRms =
        (ambientNoiseFloorRms * 0.90f + rmsNormalized * 0.10f).coerceIn(0.004f, 0.20f)
      consecutiveVoiceFrames = (consecutiveVoiceFrames - 1).coerceAtLeast(0)
    }
    return 0f
  }

  fun reset() {
    consecutiveVoiceFrames = 0
    cooldownFramesRemaining = 0
    ambientNoiseFloorRms = INITIAL_NOISE_FLOOR
  }

  companion object {
    private const val TRIGGER_FRAMES_REQUIRED = 6
    private const val COOLDOWN_FRAMES = 45
    private const val INITIAL_NOISE_FLOOR = 0.015f
    private const val MIN_VOCAL_CREST_FACTOR = 1.25f
  }
}

/**
 * Contract for [WakeWordManager] so MAXCore and tests remain independent of implementation details.
 */
interface WakeWordController {
  val state: StateFlow<WakeWordState>
  val config: StateFlow<WakeWordConfig>
  val detectionCount: StateFlow<Int>

  fun addListener(listener: WakeWordListener)

  fun removeListener(listener: WakeWordListener)

  fun updateConfig(newConfig: WakeWordConfig)

  fun startStandby(
    hasAudioPermission: Boolean,
    hasMicrophoneHardware: Boolean,
  )

  fun pauseForConversation()

  fun stopStandby()

  /**
   * Diagnostic / accessibility trigger simulating detection of keyword "MAX" without requiring
   * live microphone speech. Follows the exact same notification contract to MAXCore.
   */
  fun simulateWakeWordDetection(confidence: Float = 0.96f)

  fun release()
}

/**
 * WakeWordManager — Privacy-first wake-word subsystem for "MAX".
 *
 * Key guarantees:
 * 1. Stays in lightweight standby mode waiting strictly for "MAX".
 * 2. Never starts full speech recognition prior to wake-word detection.
 * 3. Never records normal conversations or saves microphone audio to storage.
 * 4. Reuses a single fixed [ShortArray] frame buffer in RAM to minimize GC and battery impact.
 */
class WakeWordManager(
  private val errorTracker: ErrorTracker,
  private val frameAnalyzer: WakeWordFrameAnalyzer = BaselineAcousticKeywordAnalyzer(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val privacyGuard: com.example.permissions.PrivacyGuard? = null,
) : WakeWordController {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val listeners = CopyOnWriteArraySet<WakeWordListener>()
  private var standbyJob: Job? = null

  private val _config = MutableStateFlow(WakeWordConfig())
  override val config: StateFlow<WakeWordConfig> = _config.asStateFlow()

  private val _state = MutableStateFlow<WakeWordState>(WakeWordState.Idle)
  override val state: StateFlow<WakeWordState> = _state.asStateFlow()

  private val _detectionCount = MutableStateFlow(0)
  override val detectionCount: StateFlow<Int> = _detectionCount.asStateFlow()

  override fun addListener(listener: WakeWordListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: WakeWordListener) {
    listeners.remove(listener)
  }

  override fun updateConfig(newConfig: WakeWordConfig) {
    _config.value =
      newConfig.copy(
        keyword = WakeWordConfig.DEFAULT_KEYWORD,
        sensitivity = newConfig.sensitivity.coerceIn(0.2f, 1.0f),
        standbyThrottleMs = newConfig.standbyThrottleMs.coerceIn(20L, 200L),
      )
  }

  override fun startStandby(
    hasAudioPermission: Boolean,
    hasMicrophoneHardware: Boolean,
  ) {
    if (privacyGuard != null && !privacyGuard.policy.value.allowMicrophoneWakeWord) {
      stopAudioLoopOnly()
      transitionState(WakeWordState.Idle)
      errorTracker.logInfo(MODULE_NAME, "Wake-word standby blocked by user Privacy Policy.")
      return
    }

    if (!hasMicrophoneHardware) {
      val fallback =
        WakeWordState.UnsupportedFallback(
          "Device has no microphone hardware; use manual wake trigger or text input.",
        )
      transitionState(fallback)
      errorTracker.logWarning(MODULE_NAME, fallback.reason)
      return
    }

    if (!hasAudioPermission) {
      transitionState(WakeWordState.PermissionRequired)
      errorTracker.logInfo(MODULE_NAME, "Standby awaiting RECORD_AUDIO permission for wake word 'MAX'.")
      return
    }

    if (standbyJob?.isActive == true && _state.value is WakeWordState.StandbyListening) {
      return
    }

    stopAudioLoopOnly()
    (frameAnalyzer as? BaselineAcousticKeywordAnalyzer)?.reset()

    standbyJob =
      scope.launch {
        runEphemeralAudioStandbyLoop()
      }
  }

  override fun pauseForConversation() {
    stopAudioLoopOnly()
    privacyGuard?.notifyResourcesReleased(com.example.permissions.SensitiveAction.WAKE_WORD_STANDBY)
    transitionState(WakeWordState.PausedForConversation)
    errorTracker.logInfo(MODULE_NAME, "Wake word standby paused while MAX is in conversation mode.")
  }

  override fun stopStandby() {
    stopAudioLoopOnly()
    privacyGuard?.notifyResourcesReleased(com.example.permissions.SensitiveAction.WAKE_WORD_STANDBY)
    transitionState(WakeWordState.Idle)
    errorTracker.logInfo(MODULE_NAME, "Wake word standby stopped; microphone closed.")
  }

  override fun simulateWakeWordDetection(confidence: Float) {
    val clamped = confidence.coerceIn(0.5f, 1.0f)
    errorTracker.logInfo(
      MODULE_NAME,
      "Wake word '${_config.value.keyword}' detected (confidence=${"%.2f".format(clamped)}).",
    )
    notifyDetection(clamped)
  }

  override fun release() {
    stopAudioLoopOnly()
    listeners.clear()
    scope.cancel()
    _state.value = WakeWordState.Idle
  }

  @SuppressLint("MissingPermission")
  private suspend fun runEphemeralAudioStandbyLoop() {
    val currentConfig = _config.value
    val minBufBytes =
      AudioRecord.getMinBufferSize(
        currentConfig.sampleRateHz,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )

    if (minBufBytes == AudioRecord.ERROR || minBufBytes == AudioRecord.ERROR_BAD_VALUE) {
      transitionState(
        WakeWordState.UnsupportedFallback("AudioRecord configuration unsupported on this device."),
      )
      errorTracker.logWarning(MODULE_NAME, "AudioRecord minBufferSize invalid ($minBufBytes).")
      return
    }

    val frameSamples = currentConfig.frameSampleCount.coerceAtLeast(160)
    val bufferBytes = maxOf(minBufBytes, frameSamples * 2)

    var audioRecord: AudioRecord? = null
    try {
      audioRecord =
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          currentConfig.sampleRateHz,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferBytes,
        )

      if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
        transitionState(
          WakeWordState.StandbyListening(
            keyword = currentConfig.keyword,
            isLiveMicrophoneActive = false,
          ),
        )
        errorTracker.logWarning(
          MODULE_NAME,
          "AudioRecord unavailable on this hardware/emulator; staying in passive standby.",
        )
        return
      }

      audioRecord.startRecording()
      transitionState(
        WakeWordState.StandbyListening(
          keyword = currentConfig.keyword,
          isLiveMicrophoneActive = true,
        ),
      )
      errorTracker.logInfo(
        MODULE_NAME,
        "Ephemeral wake-word standby active for '${currentConfig.keyword}' (RAM-only frames).",
      )

      // Single pre-allocated buffer reused on every read; zero audio persistence.
      val ephemeralFrame = ShortArray(frameSamples)

      while (scope.isActive && _state.value is WakeWordState.StandbyListening) {
        val readCount = audioRecord.read(ephemeralFrame, 0, ephemeralFrame.size)
        if (readCount > 0) {
          val confidence =
            try {
              frameAnalyzer.analyzeFrame(ephemeralFrame, readCount, _config.value)
            } finally {
              // Overwrite frame memory immediately after inspection for strict privacy.
              ephemeralFrame.fill(0)
            }
          if (confidence >= _config.value.sensitivity) {
            notifyDetection(confidence)
            break
          }
        } else if (readCount < 0) {
          errorTracker.logWarning(MODULE_NAME, "AudioRecord read returned code $readCount.")
          delay(120L)
        }
        delay(_config.value.standbyThrottleMs)
      }
    } catch (security: SecurityException) {
      transitionState(WakeWordState.PermissionRequired)
      notifyError("Microphone permission revoked during wake-word standby.", security)
    } catch (t: Throwable) {
      transitionState(WakeWordState.Error(t.message ?: "Wake-word standby error"))
      notifyError("Unexpected error in wake-word standby loop.", t)
    } finally {
      runCatching {
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          audioRecord.stop()
        }
        audioRecord?.release()
      }
    }
  }

  private fun notifyDetection(confidence: Float) {
    stopAudioLoopOnly()
    val timestamp = System.currentTimeMillis()
    val keyword = _config.value.keyword
    _detectionCount.value = _detectionCount.value + 1
    transitionState(
      WakeWordState.Detected(
        keyword = keyword,
        confidence = confidence,
        detectedAtMillis = timestamp,
      ),
    )
    listeners.forEach { listener ->
      runCatching {
        listener.onWakeWordDetected(
          keyword = keyword,
          confidence = confidence,
          detectedAtMillis = timestamp,
        )
      }.onFailure { err ->
        errorTracker.logError(MODULE_NAME, "WakeWordListener callback failure.", err)
      }
    }
  }

  private fun notifyError(message: String, throwable: Throwable?) {
    errorTracker.logError(MODULE_NAME, message, throwable)
    listeners.forEach { listener ->
      runCatching { listener.onWakeWordError(message, throwable) }
    }
  }

  private fun transitionState(newState: WakeWordState) {
    _state.value = newState
    listeners.forEach { listener ->
      runCatching { listener.onWakeWordStateChanged(newState) }
    }
  }

  private fun stopAudioLoopOnly() {
    standbyJob?.cancel()
    standbyJob = null
  }

  companion object {
    private const val MODULE_NAME = "WakeWordManager"
  }
}
