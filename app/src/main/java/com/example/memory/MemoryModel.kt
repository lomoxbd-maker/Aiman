package com.example.memory

import java.util.UUID

/**
 * Supported semantic memory categories for MAX.
 */
enum class MemoryType(val displayName: String) {
  USER_NAME("User Name"),
  USER_PREFERENCE("User Preference"),
  CONVERSATION_CONTEXT("Conversation Context"),
  USER_ROUTINE("User Routine"),
  USER_SETTING("User Setting"),
}

/**
 * Structured, privacy-controlled memory record managed by MAX.
 */
data class MemoryModel(
  val id: String = UUID.randomUUID().toString(),
  val type: MemoryType,
  val key: String = "",
  val content: String,
  val isSensitive: Boolean = false,
  val userApproved: Boolean = true,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = createdAtMillis,
)

/**
 * Structured command intent parsed from Bangla/natural voice or text commands.
 */
sealed class MemoryCommand {
  data class Remember(val payload: String) : MemoryCommand()

  data class Forget(val targetQuery: String) : MemoryCommand()

  data object ShowAll : MemoryCommand()

  data object ClearAll : MemoryCommand()
}

/**
 * Outcome returned after evaluating a memory command or write request.
 */
data class MemoryActionResult(
  val accepted: Boolean,
  val messageBangla: String,
  val messageEnglish: String,
  val affectedItems: List<MemoryModel> = emptyList(),
)
