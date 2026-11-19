package com.example.phone

import android.os.SystemClock
import com.example.utils.ErrorTracker
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
 * TimerManager — Drift-free, low-battery coroutine countdown timer for MAX.
 *
 * Battery optimization:
 * - Zero wake locks; sleeps cleanly between 1-second checks and cancels its coroutine job
 *   immediately on pause, reset, completion, or release.
 */
class TimerManager(
  private val errorTracker: ErrorTracker,
  private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var tickJob: Job? = null
  private var onCompletedCallback: ((String) -> Unit)? = null
  private var endElapsedMillis: Long = 0L

  private val _status = MutableStateFlow(TimerStatus())
  val status: StateFlow<TimerStatus> = _status.asStateFlow()

  fun setOnCompletedListener(listener: ((String) -> Unit)?) {
    onCompletedCallback = listener
  }

  fun startTimer(
    durationSeconds: Int,
    label: String = "MAX Timer",
  ): Boolean {
    if (durationSeconds <= 0) return false
    tickJob?.cancel()
    val cleanLabel = label.trim().ifEmpty { "MAX Timer" }
    endElapsedMillis = elapsedRealtimeProvider() + (durationSeconds * 1_000L)
    _status.value =
      TimerStatus(
        isRunning = true,
        isPaused = false,
        totalSeconds = durationSeconds,
        remainingSeconds = durationSeconds,
        label = cleanLabel,
        isFinished = false,
      )
    errorTracker.logInfo(MODULE_NAME, "Timer started for ${durationSeconds}s ($cleanLabel).")
    launchTicker()
    return true
  }

  fun pauseTimer() {
    val current = _status.value
    if (!current.isRunning) return
    tickJob?.cancel()
    tickJob = null
    val remaining = ((endElapsedMillis - elapsedRealtimeProvider()).coerceAtLeast(0L) / 1_000L).toInt()
    _status.value =
      current.copy(
        isRunning = false,
        isPaused = true,
        remainingSeconds = remaining,
      )
  }

  fun resumeTimer() {
    val current = _status.value
    if (!current.isPaused || current.remainingSeconds <= 0) return
    endElapsedMillis = elapsedRealtimeProvider() + (current.remainingSeconds * 1_000L)
    _status.value = current.copy(isRunning = true, isPaused = false)
    launchTicker()
  }

  fun resetTimer() {
    tickJob?.cancel()
    tickJob = null
    _status.value = TimerStatus()
  }

  fun release() {
    resetTimer()
    onCompletedCallback = null
    scope.cancel()
  }

  private fun launchTicker() {
    tickJob?.cancel()
    tickJob =
      scope.launch {
        while (isActive) {
          delay(1_000L)
          val remMs = (endElapsedMillis - elapsedRealtimeProvider()).coerceAtLeast(0L)
          val remSec = ((remMs + 500L) / 1_000L).toInt()
          val current = _status.value
          if (remMs <= 0L || remSec <= 0) {
            _status.value =
              current.copy(
                isRunning = false,
                isPaused = false,
                remainingSeconds = 0,
                isFinished = true,
              )
            errorTracker.logInfo(MODULE_NAME, "Timer '${current.label}' completed.")
            onCompletedCallback?.invoke(current.label)
            break
          } else {
            _status.value = current.copy(remainingSeconds = remSec)
          }
        }
      }
  }

  companion object {
    private const val MODULE_NAME = "TimerManager"
  }
}
