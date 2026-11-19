package com.example.camera

import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction

/**
 * Dormant foundation contract for future vision capabilities, guarded by [PrivacyGuard].
 * Privacy guarantee: Camera hardware is NEVER opened or activated automatically.
 */
interface CameraCapability {
  val isPrivacyLocked: Boolean
  val isHardwareSupported: Boolean

  fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult
}

class DormantCameraCapability(
  override val isHardwareSupported: Boolean,
  private val privacyGuard: PrivacyGuard? = null,
) : CameraCapability {
  override val isPrivacyLocked: Boolean
    get() = privacyGuard?.policy?.value?.allowCameraOnUserAction != true

  override fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult {
    return privacyGuard?.authorizeAction(SensitiveAction.OPEN_CAMERA, initiatedByUser)
      ?: PrivacyCheckResult.Denied(
        SensitiveAction.OPEN_CAMERA,
        "Camera capability is dormant in foundation mode.",
      )
  }
}
