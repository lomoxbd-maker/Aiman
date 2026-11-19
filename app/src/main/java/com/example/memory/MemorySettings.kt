package com.example.memory

/**
 * User-controlled privacy and storage settings for the MAX Memory System.
 */
data class MemorySettings(
  val memoryEnabled: Boolean = true,
  val allowSensitiveInformation: Boolean = false,
  val autoRememberUserPreferences: Boolean = true,
  val maxStoredMemories: Int = 50,
  val encryptAtRest: Boolean = true,
)
