package com.example.smart

import com.example.memory.MemoryController
import com.example.memory.MemoryType
import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NotesManager — Manages user-authored quick notes cleanly in bounded memory and optionally
 * mirrors notes into [MemoryController] when the user grants permission.
 */
class NotesManager(
  private val memoryController: MemoryController,
  private val errorTracker: ErrorTracker,
  private val maxNotes: Int = 64,
) {

  private val _notes = MutableStateFlow<List<SmartNoteItem>>(emptyList())
  val notes: StateFlow<List<SmartNoteItem>> = _notes.asStateFlow()

  fun addNote(
    title: String,
    content: String,
    alsoRememberInMemory: Boolean = false,
    initiatedByUser: Boolean = true,
  ): Result<SmartNoteItem> {
    if (!initiatedByUser) {
      return Result.failure(SecurityException("Blocked silent note creation."))
    }
    val cleanContent = content.trim()
    if (cleanContent.isBlank()) {
      return Result.failure(IllegalArgumentException("Note text cannot be blank."))
    }
    val resolvedTitle =
      title.trim().ifBlank {
        cleanContent.take(36).let { if (cleanContent.length > 36) "$it..." else it }
      }
    val now = System.currentTimeMillis()
    val note =
      SmartNoteItem(
        id = UUID.randomUUID().toString(),
        title = resolvedTitle,
        content = cleanContent,
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    _notes.value = (listOf(note) + _notes.value).take(maxNotes)
    if (alsoRememberInMemory) {
      memoryController.saveMemory(
        type = MemoryType.CONVERSATION_CONTEXT,
        content = "Note '$resolvedTitle': $cleanContent",
        hasExplicitPermission = true,
      )
    }
    errorTracker.logInfo(MODULE_NAME, "Saved smart note '$resolvedTitle'.")
    return Result.success(note)
  }

  fun searchNotes(query: String): List<SmartNoteItem> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return _notes.value
    return _notes.value.filter {
      it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
    }
  }

  fun deleteNote(id: String) {
    _notes.value = _notes.value.filterNot { it.id == id }
  }

  fun extractNoteFromVoiceCommand(command: String): String {
    return command
      .replace(Regex("(?i)max[,:\\s-]*"), "")
      .replace(Regex("একটা নোট লিখে রাখো|নোট লিখে রাখো|নোট লেখো|নোট রাখো"), "")
      .replace(Regex("(?i)(write|take|save|create)\\s+(a\\s+)?note[:\\s-]*"), "")
      .trim(' ', ':', '-', ',', '।')
      .ifBlank { "Quick user note saved with MAX" }
  }

  companion object {
    private const val MODULE_NAME = "NotesManager"
  }
}
