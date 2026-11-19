package com.example.files

/**
 * Dormant foundation contract for future Storage Access Framework document integration.
 * Privacy guarantee: Local storage and user files are NEVER accessed in this phase.
 */
interface FileCapability {
  val isPrivacyLocked: Boolean
}

class DormantFileCapability : FileCapability {
  override val isPrivacyLocked: Boolean = true
}
