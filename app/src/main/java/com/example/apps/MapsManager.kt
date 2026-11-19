package com.example.apps

import android.content.Intent
import android.net.Uri
import com.example.utils.ErrorTracker

/**
 * MapsManager — Opens Google Maps (or geo:/web maps fallback) strictly on explicit user command.
 * Does NOT access device GPS in background.
 */
class MapsManager(
  private val appLauncher: AppLauncher,
  private val errorTracker: ErrorTracker,
) {

  fun checkAvailability(): AppAvailability =
    appLauncher.inspectAvailability(SupportedApp.GOOGLE_MAPS)

  fun launchMaps(): Result<String> {
    errorTracker.logInfo(MODULE_NAME, "Opening Google Maps by explicit user request.")
    return appLauncher.openPackage(SupportedApp.GOOGLE_MAPS.primaryPackage)
  }

  fun openPlaceSearch(destinationQuery: String): Result<String> {
    val cleanQuery = destinationQuery.trim().ifBlank { "Dhaka" }
    val geoUri = "geo:0,0?q=${Uri.encode(cleanQuery)}"
    errorTracker.logInfo(MODULE_NAME, "Opening place search '$cleanQuery' in Google Maps.")
    val geoResult =
      appLauncher.openSafeUri(
        action = Intent.ACTION_VIEW,
        uriString = geoUri,
        packageHint = SupportedApp.GOOGLE_MAPS.primaryPackage,
      )
    return if (geoResult.isSuccess) {
      geoResult
    } else {
      val webUrl = "https://maps.google.com/?q=${Uri.encode(cleanQuery)}"
      appLauncher.openSafeHttpsUrl(webUrl)
    }
  }

  companion object {
    private const val MODULE_NAME = "MapsManager"
  }
}
