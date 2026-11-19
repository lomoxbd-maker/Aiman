package com.example.utils

/**
 * Severity classification for MAX diagnostic and error tracking events.
 */
enum class LogLevel {
  INFO,
  WARNING,
  ERROR,
}

/**
 * Immutable diagnostic record stored in [ErrorLogger]'s bounded memory buffer.
 */
data class LogEntry(
  val id: Long,
  val timestampMillis: Long,
  val level: LogLevel,
  val module: String,
  val message: String,
  val throwableSummary: String? = null,
)
