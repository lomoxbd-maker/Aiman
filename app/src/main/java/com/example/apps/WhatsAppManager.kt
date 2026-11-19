package com.example.apps

import android.content.Intent
import android.net.Uri
import com.example.utils.ErrorTracker

/**
 * WhatsAppManager — Opens WhatsApp or WhatsApp Business ONLY when explicitly requested.
 * Privacy invariant: NEVER sends messages automatically and NEVER reads chat history.
 */
class WhatsAppManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.WHATSAPP)

  fun launchWhatsApp(packageOverride: String? = null): Result<String> {
    val pkg = packageOverride ?: SupportedApp.WHATSAPP.primaryPackage
    errorTracker.logInfo(MODULE_NAME, "Opening WhatsApp ($pkg) after user confirmation.")
    return appLauncher.openPackage(pkg)
  }

  /**
   * Opens WhatsApp's draft screen with pre-filled text; the user must tap Send inside WhatsApp.
   */
  fun openChatDraftOnly(phoneDigits: String, draftText: String): Result<String> {
    val cleanDigits = phoneDigits.filter { it.isDigit() }
    val encodedText = Uri.encode(draftText.trim())
    val url =
      if (cleanDigits.isNotBlank()) {
        "https://wa.me/$cleanDigits?text=$encodedText"
      } else {
        "https://wa.me/?text=$encodedText"
      }
    errorTracker.logInfo(MODULE_NAME, "Opening WhatsApp compose draft (manual send required).")
    return appLauncher.openSafeUri(
      action = Intent.ACTION_VIEW,
      uriString = url,
      packageHint = SupportedApp.WHATSAPP.primaryPackage,
    )
  }

  companion object {
    private const val MODULE_NAME = "WhatsAppManager"
  }
}
