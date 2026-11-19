package com.example.phone

/**
 * Dormant foundation contract for future telephony & call intent helpers.
 * Privacy guarantee: Phone state and call logs are NEVER accessed in this phase.
 */
interface PhoneCapability {
  val isPrivacyLocked: Boolean
  val isTelephonySupported: Boolean
}

class DormantPhoneCapability(
  override val isTelephonySupported: Boolean,
) : PhoneCapability {
  override val isPrivacyLocked: Boolean = true
}
