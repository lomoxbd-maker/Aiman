package com.example.smart

import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoiceNotesManager — Stores structured voice note transcripts captured strictly on user command.
 *
 * Privacy rule: NEVER saves raw microphone audio recordings to disk.
 */
class VoiceNotesManager(
  private val errorTracker: ErrorTracker,
  private val maxVoiceNotes: Int = 32,
) {

  private val _voiceNotes = MutableStateFlow<List<VoiceNoteItem>>(emptyList())
  val voiceNotes: StateFlow<List<VoiceNoteItem>> = _voiceNotes.asStateFlow()

  fun saveVoiceNoteTranscript(
    title: String,
    transcript: String,
    initiatedByUser: Boolean,
  ): Result<VoiceNoteItem> {
    if (!initiatedByUser) {
      val msg = "Blocked automatic microphone/voice-note capture."
      errorTracker.logWarning(MODULE_NAME, msg)
      return Result.failure(SecurityException(msg))
    }
    val cleanTranscript = transcript.trim()
    if (cleanTranscript.isBlank()) {
      return Result.failure(IllegalArgumentException("Voice note transcript cannot be empty."))
    }

    val wordCount = cleanTranscript.split(Regex("\\s+")).count { it.isNotBlank() }
    val estimatedSeconds = maxOf(2, (wordCount * 0.55f).toInt())
    val resolvedTitle =
      title.trim().ifBlank {
        "Voice Note (${estimatedSeconds}s)"
      }

    val note =
      VoiceNoteItem(
        id = UUID.randomUUID().toString(),
        title = resolvedTitle,
        transcript = cleanTranscript,
        estimatedDurationSec = estimatedSeconds,
        createdAtMillis = System.currentTimeMillis(),
      )
    _voiceNotes.value = (listOf(note) + _voiceNotes.value).take(maxVoiceNotes)
    errorTracker.logInfo(MODULE_NAME, "Saved text-only voice note '$resolvedTitle' (no audio recorded).")
    return Result.success(note)
  }

  fun deleteVoiceNote(id: String) {
    _voiceNotes.value = _voiceNotes.value.filterNot { it.id == id }
  }

  companion object {
    private const val MODULE_NAME = "VoiceNotesManager"
  }
}
