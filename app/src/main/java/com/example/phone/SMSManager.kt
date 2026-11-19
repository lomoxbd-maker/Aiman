package com.example.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.example.utils.ErrorTracker

/**
 * Outcome of opening the user-confirmed SMS composer.
 */
sealed class SmsLaunchResult {
  data class ComposerOpened(val recipient: String) : SmsLaunchResult()
  data class Unsupported(val reason: String) : SmsLaunchResult()
  data class Failed(val reason: String) : SmsLaunchResult()
}

/**
 * SMSManager — Privacy-safe SMS composition helper for MAX.
 *
 * Strict guarantees:
 * - Never sends SMS automatically or silently in the background.
 * - Never reads SMS inbox or messages (`READ_SMS` and `RECEIVE_SMS` are never declared).
 * - Opens Android's trusted Messages composer (`Intent.ACTION_SENDTO`) only after confirmation.
 */
class SMSManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
) {

  fun isSmsSupported(): Boolean {
    val probe = Intent(Intent.ACTION_SENDTO, "smsto:".toUri())
    return runCatching { probe.resolveActivity(context.packageManager) != null }.getOrDefault(false)
  }

  /**
   * Opens the SMS composer ONLY when [confirmedByUser] is true.
   */
  fun openConfirmedSmsComposer(
    recipientNumber: String,
    messageBody: String,
    confirmedByUser: Boolean,
  ): SmsLaunchResult {
    if (!confirmedByUser) {
      val msg = "Blocked automatic SMS attempt. User confirmation is mandatory."
      errorTracker.logWarning(MODULE_NAME, msg)
      return SmsLaunchResult.Failed(msg)
    }

    val cleanNumber = recipientNumber.trim().filter { it.isDigit() || it == '+' }
    if (cleanNumber.isEmpty()) {
      return SmsLaunchResult.Failed("Please specify a valid phone number for SMS.")
    }

    val smsIntent =
      Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(cleanNumber)}".toUri()).apply {
        putExtra("sms_body", messageBody.trim())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    val resolved = runCatching { smsIntent.resolveActivity(context.packageManager) }.getOrNull()
    if (resolved == null) {
      return SmsLaunchResult.Unsupported("No SMS messaging app is available on this device.")
    }

    return runCatching {
      context.startActivity(smsIntent)
      errorTracker.logInfo(MODULE_NAME, "Opened SMS composer after explicit confirmation.")
      SmsLaunchResult.ComposerOpened(cleanNumber)
    }.getOrElse { err ->
      errorTracker.logError(MODULE_NAME, "Failed to open SMS composer: ${err.message}", err)
      SmsLaunchResult.Failed(err.localizedMessage ?: "Unable to open SMS app.")
    }
  }

  companion object {
    private const val MODULE_NAME = "SMSManager"
  }
}
