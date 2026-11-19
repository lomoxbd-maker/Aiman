package com.example.core

/**
 * High-level operational state of MAXCore.
 */
enum class CoreLifecycleState {
  UNINITIALIZED,
  INITIALIZING,
  /** Standby mode waiting for the "MAX" wake word; full speech recognition is OFF. */
  STANDBY,
  /** "MAX" wake word detected; assistant listening state active & voice conversation prepared. */
  LISTENING_PREPARED,
  /** Task finished; MAX respectfully asked if Sanju needs anything else before returning to Wake Mode. */
  AWAITING_FOLLOW_UP,
  /** Disarmed idle state (e.g., wake word disabled or permission not granted). */
  IDLE_DISARMED,
}

enum class ModuleCategory {
  CORE_MANAGER,
  VOICE_AND_COGNITION,
  DEVICE_CAPABILITY_DORMANT,
  INFRASTRUCTURE,
}

enum class ModuleReadiness {
  READY,
  STANDBY,
  ACTIVE_LISTENING,
  DORMANT_LOCKED,
  FALLBACK_MODE,
}

/**
 * Architectural descriptor surfaced in the Modules Inspector so the foundation topology
 * stays transparent and auditable.
 */
data class ModuleDescriptor(
  val packageName: String,
  val title: String,
  val roleSummary: String,
  val category: ModuleCategory,
  val readiness: ModuleReadiness,
  val privacyNote: String,
)
