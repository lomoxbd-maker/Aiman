package com.example.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.utils.ErrorTracker

/**
 * Outcome of launching a user-confirmed phone call or dialer intent.
 */
sealed class CallLaunchResult {
  data class Launched(val dialModeUsed: Boolean, val normalizedNumber: String) : CallLaunchResult()
  data class Unsupported(val reason: String) : CallLaunchResult()
  data class Failed(val reason: String) : CallLaunchResult()
}

/**
 * CallManager — Privacy-safe phone calling helper for MAX.
 *
 * Strict guarantees:
 * - Never calls anyone automatically; callers must pass explicit confirmation.
 * - Never reads call history or call logs (`READ_CALL_LOG` is never declared or queried).
 * - Checks telephony capability and gracefully falls back across tablets and OEM dialers
 *   using `Intent.ACTION_DIAL` when `CALL_PHONE` is unavailable or denied.
 */
class CallManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) {

  fun isTelephonySupported(): Boolean {
    return runCatching {
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }.getOrDefault(false)
  }

  fun hasCallPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun normalizePhoneNumber(raw: String): String {
    return raw.trim().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
  }

  /**
   * Executes a call ONLY after [confirmedByUser] is true.
   * Uses `ACTION_CALL` only when [preferDirectCall] AND `CALL_PHONE` + telephony exist;
   * otherwise safely opens Android's dialer (`ACTION_DIAL`).
   */
  fun executeConfirmedCall(
    phoneNumber: String,
    confirmedByUser: Boolean,
    preferDirectCall: Boolean = false,
  ): CallLaunchResult {
    if (!confirmedByUser) {
      val msg = "Blocked automatic call attempt. User confirmation is mandatory."
      errorTracker.logWarning(MODULE_NAME, msg)
      return CallLaunchResult.Failed(msg)
    }

    val normalized = normalizePhoneNumber(phoneNumber)
    if (normalized.isEmpty()) {
      return CallLaunchResult.Failed("Invalid or empty phone number.")
    }

    if (!isTelephonySupported()) {
      return CallLaunchResult.Unsupported(
        "This device does not support cellular voice calls; dialer fallback unavailable.",
      )
    }

    val canDirectCall = preferDirectCall && hasCallPermission()
    val action = if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent =
      Intent(action, Uri.parse("tel:${Uri.encode(normalized)}")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    val resolved = runCatching { intent.resolveActivity(context.packageManager) }.getOrNull()
    if (resolved == null) {
      return CallLaunchResult.Unsupported("No Phone dialer app found on this device.")
    }

    return runCatching {
      context.startActivity(intent)
      errorTracker.logInfo(
        MODULE_NAME,
        "Launched ${if (canDirectCall) "direct call" else "dialer"} for user-confirmed number.",
      )
      CallLaunchResult.Launched(dialModeUsed = !canDirectCall, normalizedNumber = normalized)
    }.getOrElse { err ->
      errorTracker.logError(MODULE_NAME, "Call intent failed: ${err.message}", err)
      CallLaunchResult.Failed(err.localizedMessage ?: "Unable to open phone dialer.")
    }
  }

  companion object {
    private const val MODULE_NAME = "CallManager"
  }
}
