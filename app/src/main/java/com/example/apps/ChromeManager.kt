package com.example.apps

import com.example.utils.ErrorTracker

/**
 * ChromeManager — Opens Google Chrome (or system browser fallback) strictly on user request.
 * Enforces HTTPS-only links and never opens background tabs.
 */
class ChromeManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.CHROME)

  fun launchChrome(packageOverride: String? = null): Result<String> {
    val pkg = packageOverride ?: SupportedApp.CHROME.primaryPackage
    errorTracker.logInfo(MODULE_NAME, "Opening Chrome ($pkg) by explicit user request.")
    return appLauncher.openPackage(pkg)
  }

  fun openUrlInChrome(url: String): Result<String> {
    errorTracker.logInfo(MODULE_NAME, "Opening verified HTTPS URL via Chrome/browser.")
    return appLauncher.openSafeHttpsUrl(
      url = url,
      packageHint = SupportedApp.CHROME.primaryPackage,
    )
  }

  companion object {
    private const val MODULE_NAME = "ChromeManager"
  }
}
