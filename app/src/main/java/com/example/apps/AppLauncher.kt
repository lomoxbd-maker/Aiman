package com.example.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.example.utils.ErrorTracker

/**
 * Testable OS launcher abstraction so MAX's App Control System works cleanly across Android 7–16
 * (API 24–36) and in local Robolectric unit tests without requiring a live emulator.
 */
interface AppLaunchBackend {
  fun isPackageInstalled(packageName: String): Boolean

  fun hasIntentHandler(action: String, uriString: String): Boolean

  fun launchPackage(packageName: String): Result<String>

  fun launchUri(
    action: String,
    uriString: String,
    packageHint: String? = null,
  ): Result<String>

  fun launchEmailComposer(
    recipientEmail: String,
    subject: String,
    body: String,
    packageHint: String? = null,
  ): Result<String>
}

/**
 * Production Android implementation using targeted <queries>-declared package visibility
 * (no QUERY_ALL_PACKAGES) and zero background package scanning.
 */
class AndroidAppLaunchBackend(
  private val context: Context,
) : AppLaunchBackend {

  private val packageManager: PackageManager = context.packageManager

  override fun isPackageInstalled(packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
          packageName,
          PackageManager.PackageInfoFlags.of(0L),
        )
      } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: Exception) {
      packageManager.getLaunchIntentForPackage(packageName) != null
    }
  }

  override fun hasIntentHandler(action: String, uriString: String): Boolean {
    return try {
      val intent = Intent(action, uriString.toUri())
      intent.resolveActivity(packageManager) != null
    } catch (_: Exception) {
      false
    }
  }

  override fun launchPackage(packageName: String): Result<String> {
    val launchIntent =
      packageManager.getLaunchIntentForPackage(packageName)
        ?: return Result.failure(
          IllegalStateException("App '$packageName' has no launchable Activity on this device."),
        )
    return startSafeIntent(launchIntent, packageName)
  }

  override fun launchUri(
    action: String,
    uriString: String,
    packageHint: String?,
  ): Result<String> {
    val parsed =
      runCatching { uriString.toUri() }.getOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid URI format."))
    val intent =
      Intent(action, parsed).apply {
        if (!packageHint.isNullOrBlank()) {
          setPackage(packageHint)
        }
      }
    return startSafeIntent(intent, packageHint ?: uriString)
  }

  override fun launchEmailComposer(
    recipientEmail: String,
    subject: String,
    body: String,
    packageHint: String?,
  ): Result<String> {
    val mailtoUri = "mailto:${Uri.encode(recipientEmail.trim())}".toUri()
    val intent =
      Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
        if (recipientEmail.isNotBlank()) {
          putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail.trim()))
        }
        if (subject.isNotBlank()) {
          putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        if (body.isNotBlank()) {
          putExtra(Intent.EXTRA_TEXT, body)
        }
        if (!packageHint.isNullOrBlank() && isPackageInstalled(packageHint)) {
          setPackage(packageHint)
        }
      }
    return startSafeIntent(intent, packageHint ?: "mailto:$recipientEmail")
  }

  private fun startSafeIntent(intent: Intent, label: String): Result<String> {
    return try {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (intent.resolveActivity(packageManager) == null) {
        return Result.failure(ActivityNotFoundException("No Activity available for $label"))
      }
      context.startActivity(intent)
      Result.success(label)
    } catch (e: ActivityNotFoundException) {
      Result.failure(e)
    } catch (e: SecurityException) {
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

/**
 * AppLauncher — Validates supported app packages & safe URI intents, inspects alternatives
 * when an app is missing, and launches apps strictly upon explicit user instruction.
 */
class AppLauncher(
  private val backend: AppLaunchBackend,
  private val errorTracker: ErrorTracker,
) {

  fun inspectAvailability(app: SupportedApp): AppAvailability {
    val primaryInstalled = backend.isPackageInstalled(app.primaryPackage)
    val alternatives = mutableListOf<AppAlternative>()

    app.alternativePackages.forEach { altPkg ->
      if (backend.isPackageInstalled(altPkg)) {
        alternatives.add(
          AppAlternative(
            kind = AlternativeKind.INSTALLED_ALTERNATIVE_APP,
            title = formatPackageLabel(altPkg),
            subtitle = "Installed compatible alternative app",
            targetValue = altPkg,
          ),
        )
      }
    }

    if (app == SupportedApp.GMAIL && backend.hasIntentHandler(Intent.ACTION_SENDTO, "mailto:test@example.com")) {
      alternatives.add(
        AppAlternative(
          kind = AlternativeKind.SYSTEM_EMAIL_CLIENT,
          title = "Device Email App",
          subtitle = "Open system mail composer (user sends manually)",
          targetValue = "mailto:",
        ),
      )
    }

    if (app == SupportedApp.GOOGLE_MAPS && backend.hasIntentHandler(Intent.ACTION_VIEW, "geo:0,0?q=Dhaka")) {
      alternatives.add(
        AppAlternative(
          kind = AlternativeKind.GEO_MAP_CLIENT,
          title = "Device Map Viewer",
          subtitle = "Open installed maps / navigation handler",
          targetValue = "geo:0,0?q=Dhaka",
        ),
      )
    }

    if (backend.hasIntentHandler(Intent.ACTION_VIEW, app.webFallbackUrl)) {
      alternatives.add(
        AppAlternative(
          kind =
            if (app == SupportedApp.CHROME) {
              AlternativeKind.SYSTEM_BROWSER
            } else {
              AlternativeKind.WEB_FALLBACK
            },
          title = if (app == SupportedApp.CHROME) "Default System Browser" else "${app.displayName} (Web)",
          subtitle = "Safe HTTPS fallback (${app.webFallbackUrl.removePrefix("https://")})",
          targetValue = app.webFallbackUrl,
        ),
      )
    }

    alternatives.add(
      AppAlternative(
        kind = AlternativeKind.PLAY_STORE,
        title = "Install ${app.displayName}",
        subtitle = "View ${app.displayName} on Google Play",
        targetValue = app.playStoreUrl,
      ),
    )

    return AppAvailability(
      app = app,
      primaryInstalled = primaryInstalled,
      alternatives = alternatives.distinctBy { "${it.kind}_${it.targetValue}" },
    )
  }

  fun openPackage(packageName: String): Result<String> {
    if (!backend.isPackageInstalled(packageName)) {
      val msg = "Package '$packageName' is not installed on this device."
      errorTracker.logInfo(MODULE_NAME, msg)
      return Result.failure(IllegalStateException(msg))
    }
    val result = backend.launchPackage(packageName)
    result.onSuccess {
      errorTracker.logInfo(MODULE_NAME, "Launched app package '$packageName' by user request.")
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Launch failed for '$packageName': ${err.message}")
    }
    return result
  }

  fun openSafeHttpsUrl(
    url: String,
    packageHint: String? = null,
  ): Result<String> {
    val normalized = normalizeSafeHttpsUrl(url)
      ?: return Result.failure(IllegalArgumentException("Only verified https:// URLs are allowed."))
    val resolvedHint = packageHint?.takeIf { backend.isPackageInstalled(it) }
    val result = backend.launchUri(Intent.ACTION_VIEW, normalized, resolvedHint)
    result.onSuccess {
      errorTracker.logInfo(MODULE_NAME, "Opened verified HTTPS link '$normalized'.")
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Could not open HTTPS link '$normalized': ${err.message}")
    }
    return result
  }

  fun openSafeUri(
    action: String,
    uriString: String,
    packageHint: String? = null,
  ): Result<String> {
    val trimmed = uriString.trim()
    val allowedScheme =
      trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("geo:", ignoreCase = true) ||
        trimmed.startsWith("mailto:", ignoreCase = true)
    if (!allowedScheme) {
      return Result.failure(SecurityException("Blocked non-whitelisted URI scheme."))
    }
    val resolvedHint = packageHint?.takeIf { backend.isPackageInstalled(it) }
    return backend.launchUri(action, trimmed, resolvedHint)
  }

  fun openEmailDraft(
    recipientEmail: String,
    subject: String,
    body: String,
    packageHint: String? = null,
  ): Result<String> {
    val result = backend.launchEmailComposer(recipientEmail, subject, body, packageHint)
    result.onSuccess {
      errorTracker.logInfo(MODULE_NAME, "Opened email composer draft (user must tap Send manually).")
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Email composer failed: ${err.message}")
    }
    return result
  }

  fun normalizeSafeHttpsUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase()
    if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("content:")) {
      return null
    }
    val withScheme =
      when {
        lower.startsWith("https://") -> trimmed
        lower.startsWith("http://") -> "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> return null
      }
    val host = runCatching { withScheme.toUri().host.orEmpty() }.getOrDefault("")
    if (host.isBlank() || host == "localhost" || host.startsWith("127.") || host.startsWith("10.")) {
      return null
    }
    return withScheme
  }

  private fun formatPackageLabel(pkg: String): String =
    when (pkg) {
      "com.whatsapp.w4b" -> "WhatsApp Business"
      "com.facebook.mlite" -> "Messenger Lite"
      "com.facebook.lite" -> "Facebook Lite"
      "com.sec.android.app.sbrowser" -> "Samsung Internet"
      "org.mozilla.firefox" -> "Firefox"
      else -> pkg
    }

  companion object {
    private const val MODULE_NAME = "AppLauncher"
  }
}
