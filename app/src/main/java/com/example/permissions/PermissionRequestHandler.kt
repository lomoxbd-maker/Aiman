package com.example.permissions

import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pending rationale & permission request awaiting explicit user decision in UI.
 */
data class PendingPermissionRequest(
  val permission: AppPermission,
  val title: String,
  val explanationBangla: String,
  val explanationEnglish: String,
  val canLaunchSystemPrompt: Boolean,
)

/**
 * PermissionRequestHandler — Manages just-in-time permission requests with clear user
 * explanations before any OS prompt appears, and tracks resource cleanup afterwards.
 */
class PermissionRequestHandler(
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val errorTracker: ErrorTracker,
) {

  private val _pendingRequest = MutableStateFlow<PendingPermissionRequest?>(null)
  val pendingRequest: StateFlow<PendingPermissionRequest?> = _pendingRequest.asStateFlow()

  /**
   * Prepares a permission request ONLY when needed for an explicit action, showing why the
   * permission is required first.
   */
  fun requestWhenNeeded(
    permission: AppPermission,
    preferBangla: Boolean = true,
  ): PermissionState {
    if (!privacyGuard.policy.value.isAllowedByUser(permission)) {
      return PermissionState.BlockedByPrivacyPolicy(
        permission = permission,
        reason = "${permission.displayName} is turned off in your Privacy Controls.",
      )
    }

    if (
      permission.mechanism == PermissionMechanism.ZERO_PERMISSION_PICKER ||
      permission.mechanism == PermissionMechanism.SAF_DOCUMENT_PICKER
    ) {
      return PermissionState.ReadyViaSystemPicker(permission)
    }

    val status = permissionController.checkStatus(permission)
    if (status == PermissionStatus.GRANTED) {
      return PermissionState.GrantedAndAllowed(permission)
    }

    val canPromptOs =
      permission.manifestPermission != null &&
        (permission == AppPermission.MICROPHONE_WAKE_WORD || !permission.isFoundationDormant)

    _pendingRequest.value =
      PendingPermissionRequest(
        permission = permission,
        title =
          if (preferBangla) "${permission.displayName} অনুমতি প্রয়োজন"
          else "${permission.displayName} Permission Needed",
        explanationBangla = permission.rationaleBangla,
        explanationEnglish = permission.rationaleEnglish,
        canLaunchSystemPrompt = canPromptOs,
      )

    errorTracker.logInfo(
      MODULE_NAME,
      "Prepared explanation prompt for ${permission.displayName} (osPrompt=$canPromptOs).",
    )
    return PermissionState.RequiresExplanation(
      permission = permission,
      title = "${permission.displayName} Permission",
      explanationBangla = permission.rationaleBangla,
      explanationEnglish = permission.rationaleEnglish,
    )
  }

  /**
   * Prepares an explanation dialog when the user explicitly initiates a sensitive feature
   * (e.g., Camera or Gallery) that still needs user privacy confirmation or OS runtime permission.
   */
  fun requestForExplicitAction(
    permission: AppPermission,
    preferBangla: Boolean = true,
  ): PermissionState {
    val policyAllowed = privacyGuard.policy.value.isAllowedByUser(permission)
    val osStatus = permissionController.checkStatus(permission)

    if (policyAllowed && osStatus == PermissionStatus.GRANTED) {
      return if (
        permission.mechanism == PermissionMechanism.ZERO_PERMISSION_PICKER ||
        permission.mechanism == PermissionMechanism.SAF_DOCUMENT_PICKER
      ) {
        PermissionState.ReadyViaSystemPicker(permission)
      } else {
        PermissionState.GrantedAndAllowed(permission)
      }
    }

    val canPromptOs =
      permission.manifestPermission != null &&
        (permission == AppPermission.MICROPHONE_WAKE_WORD || !permission.isFoundationDormant)

    _pendingRequest.value =
      PendingPermissionRequest(
        permission = permission,
        title =
          if (preferBangla) "${permission.displayName} অনুমতি প্রয়োজন"
          else "${permission.displayName} Permission Needed",
        explanationBangla = permission.rationaleBangla,
        explanationEnglish = permission.rationaleEnglish,
        canLaunchSystemPrompt = canPromptOs && osStatus != PermissionStatus.GRANTED,
      )

    errorTracker.logInfo(
      MODULE_NAME,
      "Prepared explicit action rationale for ${permission.displayName} (osPrompt=$canPromptOs).",
    )
    return PermissionState.RequiresExplanation(
      permission = permission,
      title = "${permission.displayName} Permission",
      explanationBangla = permission.rationaleBangla,
      explanationEnglish = permission.rationaleEnglish,
    )
  }

  fun dismissPendingRequest() {
    _pendingRequest.value = null
  }

  fun onSystemPermissionResult(permission: AppPermission, granted: Boolean): PermissionState {
    _pendingRequest.value = null
    if (granted) {
      privacyGuard.setPermissionAllowed(permission, true)
    }
    permissionController.refreshAll(privacyGuard.policy.value)
    return if (granted) {
      errorTracker.logInfo(MODULE_NAME, "${permission.displayName} granted by user.")
      PermissionState.GrantedAndAllowed(permission)
    } else {
      errorTracker.logWarning(MODULE_NAME, "${permission.displayName} denied by user.")
      PermissionState.DeniedByUser(permission)
    }
  }

  fun releaseAfterTask(action: SensitiveAction) {
    privacyGuard.notifyResourcesReleased(action)
  }

  companion object {
    private const val MODULE_NAME = "PermissionRequestHandler"
  }
}
