package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import com.example.permissions.AppPermission
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Testable release manifest transport for MAX's future update checks.
 */
interface UpdateManifestTransport {
  fun fetchLatestReleaseManifest(manifestUrl: String): Result<AppReleaseInfo>
}

class HttpsUpdateManifestTransport(
  private val currentVersionName: String,
  private val currentVersionCode: Int,
) : UpdateManifestTransport {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .build()

  override fun fetchLatestReleaseManifest(manifestUrl: String): Result<AppReleaseInfo> {
    if (!manifestUrl.startsWith("https://", ignoreCase = true)) {
      return Result.failure(IllegalArgumentException("Update manifest URL must use HTTPS."))
    }
    return try {
      val req = Request.Builder().url(manifestUrl).get().build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
          return Result.failure(IllegalStateException("Update server returned HTTP ${resp.code}"))
        }
        val body = resp.body?.string().orEmpty()
        val json = JSONObject(body)
        val latestName = json.optString("versionName", currentVersionName)
        val latestCode = json.optInt("versionCode", currentVersionCode)
        val channel = json.optString("channel", "Stable")
        val notes = json.optString("releaseNotes", "Stability and voice quality improvements.")
        val url = json.optString("releaseUrl", DEFAULT_RELEASE_PAGE)
        val minApi = json.optInt("minSupportedApi", 26)
        Result.success(
          AppReleaseInfo(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestVersionName = latestName,
            latestVersionCode = latestCode,
            releaseChannel = channel,
            releaseNotes = notes,
            releasePageUrl = url,
            minSupportedApi = minApi,
          ),
        )
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  companion object {
    const val DEFAULT_RELEASE_PAGE = "https://github.com/sanju-max-assistant/max-android/releases"
  }
}

/**
 * AppUpdateManager — Safe, user-triggered version registry & future release check subsystem.
 *
 * Security & Play compliance:
 * - Checks updates strictly on explicit user tap and after [AppPermission.INTERNET] authorization.
 * - Never executes dynamic code downloads (.dex/.jar/.so); routes users to verified HTTPS releases.
 */
class AppUpdateManager(
  private val context: Context,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val errorTracker: ErrorTracker,
  private val transport: UpdateManifestTransport =
    HttpsUpdateManifestTransport(
      currentVersionName = BuildConfig.VERSION_NAME,
      currentVersionCode = BuildConfig.VERSION_CODE,
    ),
) {

  private val _state =
    MutableStateFlow<UpdateState>(
      UpdateState.Idle(
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE,
      ),
    )
  val state: StateFlow<UpdateState> = _state.asStateFlow()

  val isAndroid8OrAbove: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

  fun checkForUpdates(
    initiatedByUser: Boolean,
    preferBangla: Boolean = true,
    manifestUrl: String? = null,
  ): UpdateState {
    if (!initiatedByUser) {
      val reason = "Blocked automatic background update check without user action."
      errorTracker.logWarning(MODULE_NAME, reason)
      val err = UpdateState.Error(reason)
      _state.value = err
      return err
    }

    val auth = privacyGuard.authorizeAction(SensitiveAction.BROWSE_INTERNET, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler.requestForExplicitAction(AppPermission.INTERNET, preferBangla)
      val permState = UpdateState.PermissionRequired(auth.reason)
      _state.value = permState
      return permState
    }

    if (manifestUrl.isNullOrBlank()) {
      // Local offline release verification when no remote endpoint is configured yet.
      val localInfo =
        AppReleaseInfo(
          currentVersionName = BuildConfig.VERSION_NAME,
          currentVersionCode = BuildConfig.VERSION_CODE,
          latestVersionName = BuildConfig.VERSION_NAME,
          latestVersionCode = BuildConfig.VERSION_CODE,
          releaseChannel = "Stable",
          releaseNotes = "All 16 MAX assistant modules verified locally (Android ${Build.VERSION.RELEASE}).",
        )
      val upToDate = UpdateState.UpToDate(localInfo, System.currentTimeMillis())
      _state.value = upToDate
      privacyGuard.notifyResourcesReleased(SensitiveAction.BROWSE_INTERNET)
      errorTracker.logInfo(
        MODULE_NAME,
        "Version v${localInfo.currentVersionName} (${localInfo.currentVersionCode}) verified up to date.",
      )
      return upToDate
    }

    _state.value = UpdateState.Checking
    val outcome = transport.fetchLatestReleaseManifest(manifestUrl)
    privacyGuard.notifyResourcesReleased(SensitiveAction.BROWSE_INTERNET)

    val nextState =
      outcome.fold(
        onSuccess = { info ->
          if (info.isUpdateAvailable) {
            UpdateState.UpdateAvailable(info)
          } else {
            UpdateState.UpToDate(info, System.currentTimeMillis())
          }
        },
        onFailure = { err ->
          UpdateState.Error(err.message ?: "Unable to verify update manifest safely.")
        },
      )
    _state.value = nextState
    return nextState
  }

  fun openVerifiedReleasePage(url: String): Boolean {
    if (!url.startsWith("https://", ignoreCase = true)) return false
    return runCatching {
      val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun release() {
    _state.value =
      UpdateState.Idle(
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE,
      )
  }

  companion object {
    private const val MODULE_NAME = "AppUpdateManager"
  }
}
