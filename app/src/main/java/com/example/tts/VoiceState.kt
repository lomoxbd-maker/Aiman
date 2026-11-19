package com.example.tts

/**
 * Operational voice playback states emitted by [TTSManager].
 */
sealed class VoiceState {
  data object Uninitialized : VoiceState()

  data object Initializing : VoiceState()

  data class Ready(
    val activeLocaleTag: String,
    val voiceName: String,
    val isFallbackVoice: Boolean,
  ) : VoiceState()

  data class Speaking(
    val currentSentenceIndex: Int,
    val totalSentences: Int,
    val activeStyle: SpeechStyle,
    val activeLocaleTag: String,
  ) : VoiceState()

  data class Paused(
    val resumeFromSentenceIndex: Int,
    val totalSentences: Int,
    val activeStyle: SpeechStyle,
  ) : VoiceState()

  data class UnsupportedFallback(
    val reason: String,
  ) : VoiceState()

  data class Error(
    val message: String,
  ) : VoiceState()
}
