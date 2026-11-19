package com.example.permissions

/**
 * Granular state representing permission & privacy readiness for a specific [AppPermission].
 */
sealed class PermissionState {
  data class GrantedAndAllowed(val permission: AppPermission) : PermissionState()

  data class RequiresExplanation(
    val permission: AppPermission,
    val title: String,
    val explanationBangla: String,
    val explanationEnglish: String,
  ) : PermissionState()

  data class DeniedByUser(val permission: AppPermission) : PermissionState()

  data class BlockedByPrivacyPolicy(
    val permission: AppPermission,
    val reason: String,
  ) : PermissionState()

  data class ReadyViaSystemPicker(val permission: AppPermission) : PermissionState()
}

/**
 * User-controlled privacy switches ensuring MAX never performs sensitive actions without
 * explicit consent and final user control.
 */
data class PrivacyPolicy(
  val allowMicrophoneWakeWord: Boolean = true,
  val allowMicrophoneSpeechPostWake: Boolean = true,
  val allowCameraOnUserAction: Boolean = false,
  val allowGalleryOnUserAction: Boolean = false,
  val allowFilesOnUserAction: Boolean = false,
  val allowLocationOnUserAction: Boolean = false,
  val allowNotifications: Boolean = false,
  val allowInternetOnUserAction: Boolean = false,
  val allowPhoneCallsOnUserAction: Boolean = false,
  val allowContactsOnUserAction: Boolean = false,
  val allowCalendarOnUserAction: Boolean = false,
) {
  fun isAllowedByUser(permission: AppPermission): Boolean =
    when (permission) {
      AppPermission.MICROPHONE_WAKE_WORD -> allowMicrophoneWakeWord
      AppPermission.CAMERA -> allowCameraOnUserAction
      AppPermission.GALLERY -> allowGalleryOnUserAction
      AppPermission.FILES -> allowFilesOnUserAction
      AppPermission.LOCATION -> allowLocationOnUserAction
      AppPermission.NOTIFICATIONS -> allowNotifications
      AppPermission.INTERNET -> allowInternetOnUserAction
      AppPermission.PHONE_CALL -> allowPhoneCallsOnUserAction
      AppPermission.CONTACTS -> allowContactsOnUserAction
      AppPermission.CALENDAR -> allowCalendarOnUserAction
    }
}
