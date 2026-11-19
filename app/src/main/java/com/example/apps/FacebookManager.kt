package com.example.apps

import com.example.utils.ErrorTracker

/**
 * FacebookManager — Opens Facebook or Facebook Lite strictly when explicitly requested by user.
 * Privacy invariant: NEVER creates posts, comments, or reactions automatically.
 */
class FacebookManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.FACEBOOK)

  fun launchFacebook(packageOverride: String? = null): Result<String> {
    val pkg = packageOverride ?: SupportedApp.FACEBOOK.primaryPackage
    errorTracker.logInfo(MODULE_NAME, "Opening Facebook ($pkg) after user confirmation.")
    return appLauncher.openPackage(pkg)
  }

  companion object {
    private const val MODULE_NAME = "FacebookManager"
  }
}
