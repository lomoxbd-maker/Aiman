package com.example.utils

import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contract for centralized, privacy-safe error and diagnostic tracking across MAX modules.
 */
interface ErrorTracker {
  val logs: StateFlow<List<LogEntry>>

  fun logInfo(module: String, message: String)

  fun logWarning(module: String, message: String)

  fun logError(module: String, message: String, throwable: Throwable? = null)

  fun clear()
}

/**
 * ErrorLogger — Foundation error tracking system for MAX.
 *
 * Keeps memory footprint strictly bounded using a fixed-capacity ring buffer ([MAX_LOG_ENTRIES])
 * so diagnostics never cause unbounded RAM growth on low-memory devices.
 */
class ErrorLogger : ErrorTracker {

  private val lock = Any()
  private val idGenerator = AtomicLong(0L)
  private val buffer = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)

  private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
  override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

  override fun logInfo(module: String, message: String) {
    append(LogLevel.INFO, module, message, null)
  }

  override fun logWarning(module: String, message: String) {
    append(LogLevel.WARNING, module, message, null)
  }

  override fun logError(module: String, message: String, throwable: Throwable?) {
    append(LogLevel.ERROR, module, message, throwable)
  }

  override fun clear() {
    synchronized(lock) {
      buffer.clear()
      _logs.value = emptyList()
    }
  }

  private fun append(
    level: LogLevel,
    module: String,
    message: String,
    throwable: Throwable?,
  ) {
    val summary = throwable?.let { "${it::class.java.simpleName}: ${it.message ?: "Unknown error"}" }
    val entry =
      LogEntry(
        id = idGenerator.incrementAndGet(),
        timestampMillis = System.currentTimeMillis(),
        level = level,
        module = module,
        message = message,
        throwableSummary = summary,
      )

    when (level) {
      LogLevel.INFO -> if (com.example.BuildConfig.DEBUG) Log.i(TAG, "[$module] $message")
      LogLevel.WARNING -> if (com.example.BuildConfig.DEBUG) Log.w(TAG, "[$module] $message")
      LogLevel.ERROR -> Log.e(TAG, "[$module] $message", throwable)
    }

    synchronized(lock) {
      if (buffer.size >= MAX_LOG_ENTRIES) {
        buffer.removeFirst()
      }
      buffer.addLast(entry)
      // Emit newest-first snapshot for responsive diagnostic viewing.
      _logs.value = buffer.reversed()
    }
  }

  companion object {
    private const val TAG = "MAXErrorLogger"
    private const val MAX_LOG_ENTRIES = 120
  }
}
