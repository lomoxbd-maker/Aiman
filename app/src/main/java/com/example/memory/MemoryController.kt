package com.example.memory

import kotlinx.coroutines.flow.StateFlow

/**
 * High-level controller contract decoupling MAX Memory System operations from
 * MAXCore, ConversationManager, PersonalityManager, and UI layers.
 */
interface MemoryController {
  val memories: StateFlow<List<MemoryModel>>
  val memorySettings: StateFlow<MemorySettings>

  fun parseCommand(inputText: String): MemoryCommand?

  fun executeCommand(command: MemoryCommand): MemoryActionResult

  fun saveMemory(
    type: MemoryType,
    content: String,
    key: String = "",
    hasExplicitPermission: Boolean = false,
  ): MemoryActionResult

  fun deleteMemoryById(id: String): Boolean

  fun deleteAllMemories(): MemoryActionResult

  fun getUserName(): String?

  fun getMemoriesByType(type: MemoryType): List<MemoryModel>

  fun updateMemorySettings(settings: MemorySettings)
}
