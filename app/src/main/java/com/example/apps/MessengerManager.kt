package com.example.apps

import com.example.utils.ErrorTracker

/**
 * MessengerManager — Opens Facebook Messenger or Messenger Lite strictly on user command.
 * Privacy invariant: NEVER sends messages automatically and NEVER reads notifications or chats.
 */
class MessengerManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.MESSENGER)

  fun launchMessenger(packageOverride: String? = null): Result<String> {
    val pkg = packageOverride ?: SupportedApp.MESSENGER.primaryPackage
    errorTracker.logInfo(MODULE_NAME, "Opening Messenger ($pkg) after user confirmation.")
    return appLauncher.openPackage(pkg)
  }

  companion object {
    private const val MODULE_NAME = "MessengerManager"
  }
}
