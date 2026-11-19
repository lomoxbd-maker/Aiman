package com.example.apps

/**
 * Supported third-party Android applications controlled by MAX strictly on explicit user command.
 */
enum class SupportedApp(
  val displayName: String,
  val displayBangla: String,
  val primaryPackage: String,
  val alternativePackages: List<String>,
  val webFallbackUrl: String,
  val playStoreUrl: String,
  val isMessagingOrSocial: Boolean,
) {
  WHATSAPP(
    displayName = "WhatsApp",
    displayBangla = "হোয়াটসঅ্যাপ",
    primaryPackage = "com.whatsapp",
    alternativePackages = listOf("com.whatsapp.w4b"),
    webFallbackUrl = "https://web.whatsapp.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.whatsapp",
    isMessagingOrSocial = true,
  ),
  MESSENGER(
    displayName = "Messenger",
    displayBangla = "মেসেঞ্জার",
    primaryPackage = "com.facebook.orca",
    alternativePackages = listOf("com.facebook.mlite"),
    webFallbackUrl = "https://www.messenger.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.facebook.orca",
    isMessagingOrSocial = true,
  ),
  FACEBOOK(
    displayName = "Facebook",
    displayBangla = "ফেসবুক",
    primaryPackage = "com.facebook.katana",
    alternativePackages = listOf("com.facebook.lite"),
    webFallbackUrl = "https://m.facebook.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.facebook.katana",
    isMessagingOrSocial = true,
  ),
  CHROME(
    displayName = "Chrome",
    displayBangla = "ক্রোম",
    primaryPackage = "com.android.chrome",
    alternativePackages = listOf("com.sec.android.app.sbrowser", "org.mozilla.firefox"),
    webFallbackUrl = "https://www.google.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.android.chrome",
    isMessagingOrSocial = false,
  ),
  YOUTUBE(
    displayName = "YouTube",
    displayBangla = "ইউটিউব",
    primaryPackage = "com.google.android.youtube",
    alternativePackages = emptyList(),
    webFallbackUrl = "https://m.youtube.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.google.android.youtube",
    isMessagingOrSocial = false,
  ),
  GMAIL(
    displayName = "Gmail",
    displayBangla = "জিমেইল",
    primaryPackage = "com.google.android.gm",
    alternativePackages = emptyList(),
    webFallbackUrl = "https://mail.google.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.google.android.gm",
    isMessagingOrSocial = true,
  ),
  GOOGLE_MAPS(
    displayName = "Google Maps",
    displayBangla = "গুগল ম্যাপস",
    primaryPackage = "com.google.android.apps.maps",
    alternativePackages = emptyList(),
    webFallbackUrl = "https://maps.google.com",
    playStoreUrl = "https://play.google.com/store/apps/details?id=com.google.android.apps.maps",
    isMessagingOrSocial = false,
  ),
}

enum class AlternativeKind {
  INSTALLED_ALTERNATIVE_APP,
  SYSTEM_EMAIL_CLIENT,
  SYSTEM_BROWSER,
  GEO_MAP_CLIENT,
  WEB_FALLBACK,
  PLAY_STORE,
}

/**
 * Concrete fallback suggested when the primary app is unavailable on the user's device.
 */
data class AppAlternative(
  val kind: AlternativeKind,
  val title: String,
  val subtitle: String,
  val targetValue: String,
)

/**
 * Availability snapshot for a single [SupportedApp] without querying arbitrary installed apps.
 */
data class AppAvailability(
  val app: SupportedApp,
  val primaryInstalled: Boolean,
  val alternatives: List<AppAlternative> = emptyList(),
)

/**
 * Confirmation-gated actions for launching apps or opening draft composers.
 * MAX never sends messages, posts, or emails automatically.
 */
sealed interface AppConfirmationAction {
  data class LaunchApp(
    val app: SupportedApp,
    val packageOverride: String? = null,
  ) : AppConfirmationAction

  data class OpenAlternative(
    val app: SupportedApp,
    val alternative: AppAlternative,
  ) : AppConfirmationAction

  data class PrepareWhatsAppChatDraft(
    val phoneDigits: String,
    val draftText: String,
  ) : AppConfirmationAction

  data class OpenYouTubeQuery(
    val query: String,
  ) : AppConfirmationAction

  data class OpenChromeUrl(
    val url: String,
  ) : AppConfirmationAction

  data class DraftGmailMessage(
    val recipientEmail: String,
    val subject: String,
    val body: String,
  ) : AppConfirmationAction

  data class SearchGoogleMaps(
    val destinationQuery: String,
  ) : AppConfirmationAction
}

/**
 * Interactive confirmation prompt presented to the user before launching third-party apps
 * or pre-filled messaging/email drafts.
 */
data class PendingAppConfirmation(
  val id: String,
  val app: SupportedApp,
  val action: AppConfirmationAction,
  val isSensitiveAction: Boolean,
  val titleBangla: String,
  val titleEnglish: String,
  val messageBangla: String,
  val messageEnglish: String,
)

/**
 * Reactive state machine for MAX's App Control System.
 * Begins in [Dormant] and never launches third-party apps in the background.
 */
sealed class AppState {
  data object Dormant : AppState()

  data class AwaitingConfirmation(
    val pending: PendingAppConfirmation,
  ) : AppState()

  data class AppNotInstalledWithAlternatives(
    val app: SupportedApp,
    val alternatives: List<AppAlternative>,
    val messageBangla: String,
    val messageEnglish: String,
  ) : AppState()

  data class Launched(
    val app: SupportedApp,
    val targetLabel: String,
    val messageBangla: String,
    val messageEnglish: String,
  ) : AppState()

  data class BlockedByPrivacyPolicy(
    val app: SupportedApp?,
    val reason: String,
  ) : AppState()

  data class Error(
    val message: String,
  ) : AppState()
}
