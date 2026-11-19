package com.example.gallery

import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction

/**
 * Dormant foundation contract for future Android Photo Picker media access, guarded by [PrivacyGuard].
 * Privacy guarantee: Gallery and user photos are NEVER accessed automatically.
 */
interface GalleryCapability {
  val isPrivacyLocked: Boolean

  fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult
}

class DormantGalleryCapability(
  private val privacyGuard: PrivacyGuard? = null,
) : GalleryCapability {
  override val isPrivacyLocked: Boolean
    get() = privacyGuard?.policy?.value?.allowGalleryOnUserAction != true

  override fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult {
    return privacyGuard?.authorizeAction(SensitiveAction.ACCESS_GALLERY, initiatedByUser)
      ?: PrivacyCheckResult.Denied(
        SensitiveAction.ACCESS_GALLERY,
        "Gallery capability is dormant in foundation mode.",
      )
  }
}
