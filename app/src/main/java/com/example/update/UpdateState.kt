package com.example.update

/**
 * Metadata describing an available or current MAX release manifest entry.
 * Uses only verified HTTPS links; never downloads executable code (.dex/.jar/.so).
 */
data class AppReleaseInfo(
  val currentVersionName: String,
  val currentVersionCode: Int,
  val latestVersionName: String,
  val latestVersionCode: Int,
  val releaseChannel: String = "Stable",
  val releaseNotes: String = "",
  val releasePageUrl: String = "https://github.com/sanju-max-assistant/max-android/releases",
  val minSupportedApi: Int = 26,
) {
  val isUpdateAvailable: Boolean
    get() = latestVersionCode > currentVersionCode
}

sealed class UpdateState {
  data class Idle(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val channel: String = "Stable",
  ) : UpdateState()

  data object Checking : UpdateState()

  data class UpToDate(
    val info: AppReleaseInfo,
    val checkedAtMillis: Long,
  ) : UpdateState()

  data class UpdateAvailable(
    val info: AppReleaseInfo,
  ) : UpdateState()

  data class PermissionRequired(
    val reason: String,
  ) : UpdateState()

  data class Error(
    val message: String,
  ) : UpdateState()
}
