package com.example.memory

/**
 * Classification of structured memory items managed by [MemoryManager].
 */
enum class MemoryCategory {
  USER_PREFERENCE,
  CONTEXT_NOTE,
  SYSTEM_SESSION,
}

/**
 * Lightweight user memory entry stored strictly in volatile foundation memory unless persistence
 * is explicitly enabled in future milestones.
 */
data class MemoryEntry(
  val id: String,
  val category: MemoryCategory,
  val summary: String,
  val createdAtMillis: Long,
)
