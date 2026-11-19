package com.example.apps

/**
 * Dormant foundation contract for future safe app-launching intents.
 * Privacy guarantee: Installed app lists are NEVER queried in this phase.
 */
interface AppLauncherCapability {
  val isPrivacyLocked: Boolean
}

class DormantAppLauncherCapability : AppLauncherCapability {
  override val isPrivacyLocked: Boolean = true
}
