package com.example.permissions

import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sensitive operations guarded by [PrivacyController] before execution.
 */
enum class SensitiveAction(
  val requiredPermission: AppPermission,
  val description: String,
) {
  WAKE_WORD_STANDBY(AppPermission.MICROPHONE_WAKE_WORD, "Ephemeral 'MAX' wake-word detection"),
  POST_WAKE_SPEECH(AppPermission.MICROPHONE_WAKE_WORD, "Voice conversation after 'MAX' wake word"),
  OPEN_CAMERA(AppPermission.CAMERA, "Camera capture"),
  ACCESS_GALLERY(AppPermission.GALLERY, "Gallery photo selection"),
  READ_FILES(AppPermission.FILES, "Document file selection"),
  READ_LOCATION(AppPermission.LOCATION, "GPS location lookup"),
  POST_NOTIFICATION(AppPermission.NOTIFICATIONS, "User reminder notification"),
  BROWSE_INTERNET(AppPermission.INTERNET, "Online request"),
  MAKE_CALL(AppPermission.PHONE_CALL, "User-confirmed phone call"),
  ACCESS_CONTACTS(AppPermission.CONTACTS, "User-requested contact lookup"),
  READ_CALENDAR(AppPermission.CALENDAR, "User-requested today's calendar check"),
}

sealed class PrivacyCheckResult {
  data object Allowed : PrivacyCheckResult()

  data class Denied(
    val action: SensitiveAction,
    val reason: String,
    val requiresUserExplanation: Boolean = false,
  ) : PrivacyCheckResult()
}

/**
 * Contract for privacy checks before every sensitive action across MAX modules.
 */
interface PrivacyGuard {
  val policy: StateFlow<PrivacyPolicy>

  fun authorizeAction(
    action: SensitiveAction,
    initiatedByUser: Boolean,
  ): PrivacyCheckResult

  fun updatePolicy(newPolicy: PrivacyPolicy)

  fun setPermissionAllowed(permission: AppPermission, allowed: Boolean)

  fun notifyResourcesReleased(action: SensitiveAction)
}

/**
 * PrivacyController — Enforces MAX's strict privacy invariants:
 * - Never triggers camera, gallery, files, GPS, or internet automatically.
 * - Ensures full speech recognition only starts after wake-word activation.
 * - Gives the user final control via [PrivacyPolicy].
 */
class PrivacyController(
  private val permissionChecker: () -> PermissionController,
  private val errorTracker: ErrorTracker,
) : PrivacyGuard {

  private val _policy = MutableStateFlow(PrivacyPolicy())
  override val policy: StateFlow<PrivacyPolicy> = _policy.asStateFlow()

  override fun authorizeAction(
    action: SensitiveAction,
    initiatedByUser: Boolean,
  ): PrivacyCheckResult {
    // Rule 1: Background/automatic activation of camera, gallery, files, GPS, or internet is forbidden.
    if (!initiatedByUser && action != SensitiveAction.WAKE_WORD_STANDBY) {
      val reason = "Blocked automatic attempt for '${action.description}'. User initiation required."
      errorTracker.logWarning(MODULE_NAME, reason)
      return PrivacyCheckResult.Denied(action, reason)
    }

    val currentPolicy = _policy.value
    val perm = action.requiredPermission

    // Rule 2: User's privacy switch always has final control.
    if (!currentPolicy.isAllowedByUser(perm)) {
      val reason = "${perm.displayName} is locked by user Privacy Policy."
      errorTracker.logInfo(MODULE_NAME, reason)
      return PrivacyCheckResult.Denied(action, reason)
    }

    if (action == SensitiveAction.POST_WAKE_SPEECH && !currentPolicy.allowMicrophoneSpeechPostWake) {
      return PrivacyCheckResult.Denied(action, "Post-wake speech recognition disabled by user.")
    }

    // Rule 3: Verify OS runtime permission when applicable.
    val status = permissionChecker().checkStatus(perm)
    return when {
      perm.mechanism == PermissionMechanism.ZERO_PERMISSION_PICKER ||
        perm.mechanism == PermissionMechanism.SAF_DOCUMENT_PICKER -> PrivacyCheckResult.Allowed

      perm == AppPermission.INTERNET ->
        if (currentPolicy.allowInternetOnUserAction) PrivacyCheckResult.Allowed
        else PrivacyCheckResult.Denied(action, "Internet access locked by privacy policy.")

      status == PermissionStatus.GRANTED -> PrivacyCheckResult.Allowed

      status == PermissionStatus.DORMANT_LOCKED ->
        PrivacyCheckResult.Denied(
          action,
          "${perm.displayName} remains dormant in this build until explicitly enabled.",
        )

      else ->
        PrivacyCheckResult.Denied(
          action = action,
          reason = "${perm.displayName} permission is required for ${action.description}.",
          requiresUserExplanation = true,
        )
    }
  }

  override fun updatePolicy(newPolicy: PrivacyPolicy) {
    _policy.value = newPolicy
    errorTracker.logInfo(MODULE_NAME, "Privacy policy updated by user.")
  }

  override fun setPermissionAllowed(permission: AppPermission, allowed: Boolean) {
    val p = _policy.value
    val updated =
      when (permission) {
        AppPermission.MICROPHONE_WAKE_WORD -> p.copy(allowMicrophoneWakeWord = allowed)
        AppPermission.CAMERA -> p.copy(allowCameraOnUserAction = allowed)
        AppPermission.GALLERY -> p.copy(allowGalleryOnUserAction = allowed)
        AppPermission.FILES -> p.copy(allowFilesOnUserAction = allowed)
        AppPermission.LOCATION -> p.copy(allowLocationOnUserAction = allowed)
        AppPermission.NOTIFICATIONS -> p.copy(allowNotifications = allowed)
        AppPermission.INTERNET -> p.copy(allowInternetOnUserAction = allowed)
        AppPermission.PHONE_CALL -> p.copy(allowPhoneCallsOnUserAction = allowed)
        AppPermission.CONTACTS -> p.copy(allowContactsOnUserAction = allowed)
        AppPermission.CALENDAR -> p.copy(allowCalendarOnUserAction = allowed)
      }
    updatePolicy(updated)
  }

  override fun notifyResourcesReleased(action: SensitiveAction) {
    errorTracker.logInfo(MODULE_NAME, "Released hardware/privacy resources after ${action.description}.")
  }

  companion object {
    private const val MODULE_NAME = "PrivacyController"
  }
}
