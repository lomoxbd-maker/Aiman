package com.example.internet

import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction

/**
 * Dormant foundation contract for future network/cloud orchestration, guarded by [PrivacyGuard].
 * Privacy guarantee: MAX never browses the internet automatically.
 */
interface NetworkCapability {
  val isOfflineLocked: Boolean

  fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult
}

class DormantNetworkCapability(
  private val privacyGuard: PrivacyGuard? = null,
) : NetworkCapability {
  override val isOfflineLocked: Boolean
    get() = privacyGuard?.policy?.value?.allowInternetOnUserAction != true

  override fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult {
    return privacyGuard?.authorizeAction(SensitiveAction.BROWSE_INTERNET, initiatedByUser)
      ?: PrivacyCheckResult.Denied(
        SensitiveAction.BROWSE_INTERNET,
        "Internet capability is offline-locked in foundation mode.",
      )
  }
}
