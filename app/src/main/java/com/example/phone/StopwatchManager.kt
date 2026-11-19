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
 * StopwatchManager — Low-battery, drift-free stopwatch with bounded lap storage.
 */
class StopwatchManager(
  private val errorTracker: ErrorTracker,
  private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var tickJob: Job? = null
  private var accumulatedMillis: Long = 0L
  private var startedAtElapsedMillis: Long = 0L

  private val _status = MutableStateFlow(StopwatchStatus())
  val status: StateFlow<StopwatchStatus> = _status.asStateFlow()

  fun start() {
    if (_status.value.isRunning) return
    startedAtElapsedMillis = elapsedRealtimeProvider()
    _status.value = _status.value.copy(isRunning = true)
    errorTracker.logInfo(MODULE_NAME, "Stopwatch started.")

    tickJob?.cancel()
    tickJob =
      scope.launch {
        while (isActive && _status.value.isRunning) {
          delay(200L)
          val total = accumulatedMillis + (elapsedRealtimeProvider() - startedAtElapsedMillis).coerceAtLeast(0L)
          _status.value = _status.value.copy(elapsedMillis = total)
        }
      }
  }

  fun pause() {
    if (!_status.value.isRunning) return
    tickJob?.cancel()
    tickJob = null
    accumulatedMillis += (elapsedRealtimeProvider() - startedAtElapsedMillis).coerceAtLeast(0L)
    _status.value = _status.value.copy(isRunning = false, elapsedMillis = accumulatedMillis)
  }

  fun recordLap() {
    val current = _status.value
    val total =
      if (current.isRunning) {
        accumulatedMillis + (elapsedRealtimeProvider() - startedAtElapsedMillis).coerceAtLeast(0L)
      } else {
        accumulatedMillis
      }
    if (total <= 0L) return
    val updatedLaps = (current.laps + total).takeLast(MAX_LAPS)
    _status.value = current.copy(elapsedMillis = total, laps = updatedLaps)
  }

  fun reset() {
    tickJob?.cancel()
    tickJob = null
    accumulatedMillis = 0L
    startedAtElapsedMillis = 0L
    _status.value = StopwatchStatus()
  }

  fun release() {
    reset()
    scope.cancel()
  }

  companion object {
    private const val MODULE_NAME = "StopwatchManager"
    private const val MAX_LAPS = 50
  }
}
