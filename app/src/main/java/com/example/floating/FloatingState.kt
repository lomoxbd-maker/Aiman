package com.example.floating

enum class FloatingAssistantMode {
  OFF,
  IN_APP_BUBBLE,
  SYSTEM_OVERLAY_ACTIVE,
}

sealed class FloatingState {
  data object Inactive : FloatingState()

  data class PermissionRequired(
    val reason: String,
  ) : FloatingState()

  data class MinimizedBubble(
    val mode: FloatingAssistantMode,
  ) : FloatingState()

  data class ExpandedQuickPanel(
    val mode: FloatingAssistantMode,
    val isVoiceArmed: Boolean,
  ) : FloatingState()

  data class Error(
    val message: String,
  ) : FloatingState()
}
