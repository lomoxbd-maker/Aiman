package com.example.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface for safely inspecting and auditing runtime permissions across MAX.
 */
interface PermissionController {
  val snapshots: StateFlow<List<PermissionSnapshot>>

  fun hasWakeWordAudioPermission(): Boolean

  fun checkStatus(permission: AppPermission): PermissionStatus

  fun evaluateState(permission: AppPermission, policy: PrivacyPolicy): PermissionState

  fun refreshAll(policy: PrivacyPolicy = PrivacyPolicy()): List<PermissionSnapshot>
}

/**
 * PermissionManager — Controls and audits all 7 permission domains safely across Android versions.
 *
 * - Never auto-requests permissions on startup.
 * - Uses version-aware checks (`POST_NOTIFICATIONS` on Android 13+, `NotificationManagerCompat` on older APIs).
 * - Keeps sensitive dormant modules locked until explicitly requested and permitted.
 */
class PermissionManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) : PermissionController {

  private val _snapshots = MutableStateFlow<List<PermissionSnapshot>>(emptyList())
  override val snapshots: StateFlow<List<PermissionSnapshot>> = _snapshots.asStateFlow()

  init {
    refreshAll()
  }

  override fun hasWakeWordAudioPermission(): Boolean {
    return checkStatus(AppPermission.MICROPHONE_WAKE_WORD) == PermissionStatus.GRANTED
  }

  override fun checkStatus(permission: AppPermission): PermissionStatus {
    return when (permission.mechanism) {
      PermissionMechanism.ZERO_PERMISSION_PICKER ->
        if (permission.isFoundationDormant) PermissionStatus.DORMANT_LOCKED
        else PermissionStatus.GRANTED

      PermissionMechanism.SAF_DOCUMENT_PICKER ->
        if (permission.isFoundationDormant) PermissionStatus.DORMANT_LOCKED
        else PermissionStatus.GRANTED

      PermissionMechanism.ANDROID_13_RUNTIME_NOTIFICATION -> {
        if (permission.isFoundationDormant) return PermissionStatus.DORMANT_LOCKED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          checkManifestPermission(permission)
        } else {
          val enabled =
            runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
              .getOrDefault(false)
          if (enabled) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
        }
      }

      PermissionMechanism.INSTALL_TIME_POLICY_GATED -> {
        if (permission.isFoundationDormant) PermissionStatus.DORMANT_LOCKED
        else checkManifestPermission(permission)
      }

      PermissionMechanism.RUNTIME_OS_PERMISSION -> {
        if (permission.isFoundationDormant) PermissionStatus.DORMANT_LOCKED
        else checkManifestPermission(permission)
      }
    }
  }

  override fun evaluateState(permission: AppPermission, policy: PrivacyPolicy): PermissionState {
    if (!policy.isAllowedByUser(permission)) {
      return PermissionState.BlockedByPrivacyPolicy(
        permission = permission,
        reason = "${permission.displayName} is locked by your Privacy Policy.",
      )
    }
    return when (permission.mechanism) {
      PermissionMechanism.ZERO_PERMISSION_PICKER,
      PermissionMechanism.SAF_DOCUMENT_PICKER, -> PermissionState.ReadyViaSystemPicker(permission)
      else -> {
        if (checkStatus(permission) == PermissionStatus.GRANTED) {
          PermissionState.GrantedAndAllowed(permission)
        } else {
          PermissionState.RequiresExplanation(
            permission = permission,
            title = "${permission.displayName} Permission",
            explanationBangla = permission.rationaleBangla,
            explanationEnglish = permission.rationaleEnglish,
          )
        }
      }
    }
  }

  override fun refreshAll(policy: PrivacyPolicy): List<PermissionSnapshot> {
    val latest =
      AppPermission.entries.map { perm ->
        PermissionSnapshot(
          permission = perm,
          status = checkStatus(perm),
          userAllowedInPolicy = policy.isAllowedByUser(perm),
        )
      }
    _snapshots.value = latest
    return latest
  }

  private fun checkManifestPermission(permission: AppPermission): PermissionStatus {
    val manifestName = permission.manifestPermission ?: return PermissionStatus.DORMANT_LOCKED
    return try {
      val result = ContextCompat.checkSelfPermission(context, manifestName)
      if (result == PackageManager.PERMISSION_GRANTED) {
        PermissionStatus.GRANTED
      } else {
        PermissionStatus.NOT_GRANTED
      }
    } catch (t: Throwable) {
      errorTracker.logError(MODULE_NAME, "Failed permission check for ${permission.displayName}", t)
      PermissionStatus.NOT_GRANTED
    }
  }

  companion object {
    private const val MODULE_NAME = "PermissionManager"
  }
}
