package com.example.apps

import android.net.Uri
import com.example.utils.ErrorTracker

/**
 * YouTubeManager — Opens YouTube app (or HTTPS web fallback) strictly on user command.
 */
class YouTubeManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.YOUTUBE)

  fun launchYouTube(): Result<String> {
    errorTracker.logInfo(MODULE_NAME, "Opening YouTube by explicit user request.")
    return appLauncher.openPackage(SupportedApp.YOUTUBE.primaryPackage)
  }

  fun openSearchQuery(query: String): Result<String> {
    val cleanQuery = query.trim().ifBlank { "Bangla music" }
    val url = "https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}"
    errorTracker.logInfo(MODULE_NAME, "Opening YouTube search in app/web for '$cleanQuery'.")
    return appLauncher.openSafeHttpsUrl(
      url = url,
      packageHint = SupportedApp.YOUTUBE.primaryPackage,
    )
  }

  companion object {
    private const val MODULE_NAME = "YouTubeManager"
  }
}
