package com.example.apps

import com.example.utils.ErrorTracker

/**
 * GmailManager — Opens Gmail (or device email client fallback) strictly on user request.
 * Privacy invariant: NEVER sends emails automatically and NEVER reads inbox messages.
 */
class GmailManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.GMAIL)

  fun launchGmail(): Result<String> {
    errorTracker.logInfo(MODULE_NAME, "Opening Gmail after user confirmation.")
    return appLauncher.openPackage(SupportedApp.GMAIL.primaryPackage)
  }

  /**
   * Opens pre-filled email draft in Gmail or default mail app; user taps Send manually.
   */
  fun openComposeDraftOnly(
    recipientEmail: String,
    subject: String,
    body: String,
  ): Result<String> {
    errorTracker.logInfo(MODULE_NAME, "Opening Gmail compose draft (manual send required).")
    return appLauncher.openEmailDraft(
      recipientEmail = recipientEmail,
      subject = subject,
      body = body,
      packageHint = SupportedApp.GMAIL.primaryPackage,
    )
  }

  companion object {
    private const val MODULE_NAME = "GmailManager"
  }
}
