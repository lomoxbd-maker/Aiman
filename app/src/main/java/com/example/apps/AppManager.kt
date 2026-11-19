package com.example.apps

import android.content.Intent
import com.example.permissions.PermissionController
import com.example.permissions.PrivacyGuard
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppEventListener {
  fun onAppAnnouncement(
    bangla: String,
    english: String,
    speakAloud: Boolean = true,
  )
}

interface AppController : AppLauncherCapability {
  val state: StateFlow<AppState>
  val appAvailabilities: StateFlow<Map<SupportedApp, AppAvailability>>
  val pendingConfirmation: StateFlow<PendingAppConfirmation?>

  fun refreshAvailability(): Map<SupportedApp, AppAvailability>

  fun requestOpenApp(
    app: SupportedApp,
    initiatedByUser: Boolean,
    requireConfirmation: Boolean = true,
  ): Boolean

  fun requestOpenAlternative(
    app: SupportedApp,
    alternative: AppAlternative,
    initiatedByUser: Boolean,
    requireConfirmation: Boolean = true,
  ): Boolean

  fun requestWhatsAppChatDraft(
    phoneDigits: String,
    draftText: String,
    initiatedByUser: Boolean,
  ): Boolean

  fun requestGmailDraft(
    recipientEmail: String,
    subject: String,
    body: String,
    initiatedByUser: Boolean,
  ): Boolean

  fun requestMapsSearch(
    destinationQuery: String,
    initiatedByUser: Boolean,
  ): Boolean

  fun requestChromeUrl(
    url: String,
    initiatedByUser: Boolean,
  ): Boolean

  fun requestYouTubeSearch(
    query: String,
    initiatedByUser: Boolean,
  ): Boolean

  fun confirmPendingAction(): Boolean

  fun cancelPendingAction()

  fun handleVoiceCommand(
    input: String,
    preferBangla: Boolean,
    initiatedByUser: Boolean,
  ): String?

  fun resetToDormant()

  fun release()
}

/**
 * AppManager — Orchestrates explicit, user-confirmed control over supported third-party apps
 * (WhatsApp, Messenger, Facebook, Chrome, YouTube, Gmail, Google Maps).
 *
 * Strict privacy & safety guarantees:
 * 1. Never opens any app automatically or in the background ([initiatedByUser] must be true).
 * 2. Never sends messages, posts content, or reads private app notifications/chats.
 * 3. Stages explicit user confirmation before launching third-party apps or pre-filled drafts.
 * 4. Suggests compatible installed apps or safe web fallbacks when a requested app is missing.
 */
class AppManager(
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val appLauncher: AppLauncher,
  private val whatsAppManager: WhatsAppManager,
  private val messengerManager: MessengerManager,
  private val facebookManager: FacebookManager,
  private val chromeManager: ChromeManager,
  private val youtubeManager: YouTubeManager,
  private val gmailManager: GmailManager,
  private val mapsManager: MapsManager,
  private val errorTracker: ErrorTracker,
) : AppController {

  override val isPrivacyLocked: Boolean = false

  private val _state = MutableStateFlow<AppState>(AppState.Dormant)
  override val state: StateFlow<AppState> = _state.asStateFlow()

  private val _appAvailabilities =
    MutableStateFlow<Map<SupportedApp, AppAvailability>>(emptyMap())
  override val appAvailabilities: StateFlow<Map<SupportedApp, AppAvailability>> =
    _appAvailabilities.asStateFlow()

  private val _pendingConfirmation = MutableStateFlow<PendingAppConfirmation?>(null)
  override val pendingConfirmation: StateFlow<PendingAppConfirmation?> =
    _pendingConfirmation.asStateFlow()

  private var eventListener: AppEventListener? = null

  fun setEventListener(listener: AppEventListener?) {
    eventListener = listener
  }

  override fun refreshAvailability(): Map<SupportedApp, AppAvailability> {
    val map =
      linkedMapOf(
        SupportedApp.WHATSAPP to whatsAppManager.checkAvailability(),
        SupportedApp.MESSENGER to messengerManager.checkAvailability(),
        SupportedApp.FACEBOOK to facebookManager.checkAvailability(),
        SupportedApp.CHROME to chromeManager.checkAvailability(),
        SupportedApp.YOUTUBE to youtubeManager.checkAvailability(),
        SupportedApp.GMAIL to gmailManager.checkAvailability(),
        SupportedApp.GOOGLE_MAPS to mapsManager.checkAvailability(),
      )
    _appAvailabilities.value = map
    return map
  }

  override fun requestOpenApp(
    app: SupportedApp,
    initiatedByUser: Boolean,
    requireConfirmation: Boolean,
  ): Boolean {
    if (!guardUserInitiated(app, "open ${app.displayName}", initiatedByUser)) return false

    val availability = refreshSingleAvailability(app)
    if (!availability.primaryInstalled) {
      val banglaMsg =
        if (availability.alternatives.isNotEmpty()) {
          "${app.displayBangla} আপনার ডিভাইসে ইনস্টল করা নেই। আপনি নিচের নিরাপদ বিকল্পগুলো বেছে নিতে পারেন।"
        } else {
          "${app.displayBangla} এবং এর কোনো বিকল্প এই ডিভাইসে পাওয়া যায়নি।"
        }
      val englishMsg =
        if (availability.alternatives.isNotEmpty()) {
          "${app.displayName} is not installed on this device. You can choose a safe alternative below."
        } else {
          "${app.displayName} is not installed and no compatible handler was found."
        }
      _pendingConfirmation.value = null
      _state.value =
        AppState.AppNotInstalledWithAlternatives(
          app = app,
          alternatives = availability.alternatives,
          messageBangla = banglaMsg,
          messageEnglish = englishMsg,
        )
      errorTracker.logInfo(MODULE_NAME, "${app.displayName} not installed; prepared ${availability.alternatives.size} alternatives.")
      eventListener?.onAppAnnouncement(banglaMsg, englishMsg, speakAloud = true)
      return false
    }

    val mustConfirm = requireConfirmation || app.isMessagingOrSocial
    if (mustConfirm) {
      val safetySuffixBn =
        if (app.isMessagingOrSocial) {
          " ম্যাক্স কখনো নিজে থেকে কোনো মেসেজ পাঠায় না বা পোস্ট করে না।"
        } else {
          " শুধুমাত্র আপনার নির্দেশে অ্যাপটি খোলা হবে।"
        }
      val safetySuffixEn =
        if (app.isMessagingOrSocial) {
          " MAX never sends messages or creates posts automatically."
        } else {
          " MAX only opens apps directly when you confirm."
        }
      val pending =
        PendingAppConfirmation(
          id = "app_open_${app.name}_${System.currentTimeMillis()}",
          app = app,
          action = AppConfirmationAction.LaunchApp(app),
          isSensitiveAction = app.isMessagingOrSocial,
          titleBangla = "${app.displayBangla} খোলার নিশ্চিতকরণ",
          titleEnglish = "Open ${app.displayName}?",
          messageBangla = "আপনি কি ${app.displayBangla} অ্যাপটি খুলতে চান?$safetySuffixBn",
          messageEnglish = "Confirm opening ${app.displayName}.$safetySuffixEn",
        )
      stageConfirmation(pending)
      return true
    }

    return executeConfirmedAction(AppConfirmationAction.LaunchApp(app), app)
  }

  override fun requestOpenAlternative(
    app: SupportedApp,
    alternative: AppAlternative,
    initiatedByUser: Boolean,
    requireConfirmation: Boolean,
  ): Boolean {
    if (!guardUserInitiated(app, "open alternative ${alternative.title}", initiatedByUser)) return false

    if (requireConfirmation) {
      val pending =
        PendingAppConfirmation(
          id = "app_alt_${app.name}_${System.currentTimeMillis()}",
          app = app,
          action = AppConfirmationAction.OpenAlternative(app, alternative),
          isSensitiveAction = true,
          titleBangla = "বিকল্প মাধ্যম খোলার নিশ্চিতকরণ",
          titleEnglish = "Open ${alternative.title}?",
          messageBangla =
            "${app.displayBangla}-এর বিকল্প হিসেবে '${alternative.title}' খুলতে চান? (${alternative.subtitle})",
          messageEnglish =
            "Open '${alternative.title}' as an alternative for ${app.displayName}? (${alternative.subtitle})",
        )
      stageConfirmation(pending)
      return true
    }

    return executeConfirmedAction(AppConfirmationAction.OpenAlternative(app, alternative), app)
  }

  override fun requestWhatsAppChatDraft(
    phoneDigits: String,
    draftText: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!guardUserInitiated(SupportedApp.WHATSAPP, "prepare WhatsApp chat draft", initiatedByUser)) {
      return false
    }
    if (draftText.isBlank()) {
      _state.value = AppState.Error("Please write a short draft message first.")
      return false
    }
    val pending =
      PendingAppConfirmation(
        id = "wa_draft_${System.currentTimeMillis()}",
        app = SupportedApp.WHATSAPP,
        action = AppConfirmationAction.PrepareWhatsAppChatDraft(phoneDigits, draftText.trim()),
        isSensitiveAction = true,
        titleBangla = "WhatsApp ড্রাফট খোলার নিশ্চিতকরণ",
        titleEnglish = "Open WhatsApp Draft?",
        messageBangla =
          "নিচের বার্তাটি WhatsApp-এ ড্রাফট হিসেবে খোলা হবে। ম্যাক্স কখনো নিজে মেসেজ পাঠায় না — আপনি দেখে নিজে Send চাপবেন: \"${draftText.trim()}\"",
        messageEnglish =
          "MAX will open a WhatsApp draft only (never auto-send). You decide whether to tap Send: \"${draftText.trim()}\"",
      )
    stageConfirmation(pending)
    return true
  }

  override fun requestGmailDraft(
    recipientEmail: String,
    subject: String,
    body: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!guardUserInitiated(SupportedApp.GMAIL, "prepare Gmail compose draft", initiatedByUser)) {
      return false
    }
    val cleanTo = recipientEmail.trim()
    val pending =
      PendingAppConfirmation(
        id = "gmail_draft_${System.currentTimeMillis()}",
        app = SupportedApp.GMAIL,
        action = AppConfirmationAction.DraftGmailMessage(cleanTo, subject.trim(), body.trim()),
        isSensitiveAction = true,
        titleBangla = "Gmail ইমেইল ড্রাফট নিশ্চিতকরণ",
        titleEnglish = "Open Email Draft?",
        messageBangla =
          "প্রাপক ${cleanTo.ifBlank { "(নির্বাচন করুন)" }}-এর জন্য ইমেইল ড্রাফট খোলা হবে। ম্যাক্স কখনো নিজে ইমেইল পাঠায় না।",
        messageEnglish =
          "Open pre-filled email draft for ${cleanTo.ifBlank { "recipient" }}. MAX never sends emails automatically.",
      )
    stageConfirmation(pending)
    return true
  }

  override fun requestMapsSearch(
    destinationQuery: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!guardUserInitiated(SupportedApp.GOOGLE_MAPS, "search Google Maps", initiatedByUser)) {
      return false
    }
    val cleanQuery = destinationQuery.trim().ifBlank { "Dhaka" }
    val pending =
      PendingAppConfirmation(
        id = "maps_search_${System.currentTimeMillis()}",
        app = SupportedApp.GOOGLE_MAPS,
        action = AppConfirmationAction.SearchGoogleMaps(cleanQuery),
        isSensitiveAction = false,
        titleBangla = "Google Maps-এ খোঁজার নিশ্চিতকরণ",
        titleEnglish = "Open Google Maps Search?",
        messageBangla = "আপনি কি Google Maps-এ '$cleanQuery' খুঁজতে চান? (ব্যাকগ্রাউন্ড GPS চালু করা হবে না)",
        messageEnglish = "Open '$cleanQuery' in Google Maps? MAX will not read GPS in the background.",
      )
    stageConfirmation(pending)
    return true
  }

  override fun requestChromeUrl(
    url: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!guardUserInitiated(SupportedApp.CHROME, "open link in Chrome", initiatedByUser)) {
      return false
    }
    val safeUrl = appLauncher.normalizeSafeHttpsUrl(url)
    if (safeUrl == null) {
      val errMsg = "Blocked unsafe or non-HTTPS link. Please use a valid https:// website address."
      _state.value = AppState.Error(errMsg)
      return false
    }
    val pending =
      PendingAppConfirmation(
        id = "chrome_url_${System.currentTimeMillis()}",
        app = SupportedApp.CHROME,
        action = AppConfirmationAction.OpenChromeUrl(safeUrl),
        isSensitiveAction = true,
        titleBangla = "Chrome-এ ওয়েবসাইট খোলার নিশ্চিতকরণ",
        titleEnglish = "Open Link in Chrome?",
        messageBangla = "আপনি কি নিরাপদ HTTPS লিংক '$safeUrl' খুলতে চান?",
        messageEnglish = "Confirm opening verified HTTPS link '$safeUrl' in Chrome or default browser.",
      )
    stageConfirmation(pending)
    return true
  }

  override fun requestYouTubeSearch(
    query: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!guardUserInitiated(SupportedApp.YOUTUBE, "search inside YouTube", initiatedByUser)) {
      return false
    }
    val clean = query.trim().ifBlank { "Bangla music" }
    val pending =
      PendingAppConfirmation(
        id = "yt_search_${System.currentTimeMillis()}",
        app = SupportedApp.YOUTUBE,
        action = AppConfirmationAction.OpenYouTubeQuery(clean),
        isSensitiveAction = false,
        titleBangla = "YouTube খোলার নিশ্চিতকরণ",
        titleEnglish = "Open YouTube Search?",
        messageBangla = "আপনি কি YouTube-এ '$clean' দেখতে চান?",
        messageEnglish = "Confirm opening YouTube for '$clean'.",
      )
    stageConfirmation(pending)
    return true
  }

  override fun confirmPendingAction(): Boolean {
    val pending = _pendingConfirmation.value ?: return false
    _pendingConfirmation.value = null
    return executeConfirmedAction(pending.action, pending.app)
  }

  override fun cancelPendingAction() {
    val cancelled = _pendingConfirmation.value
    _pendingConfirmation.value = null
    _state.value = AppState.Dormant
    if (cancelled != null) {
      errorTracker.logInfo(MODULE_NAME, "User cancelled opening ${cancelled.app.displayName}.")
      eventListener?.onAppAnnouncement(
        bangla = "ঠিক আছে, আপনার অনুরোধ অনুযায়ী ${cancelled.app.displayBangla} খোলা বাতিল করা হয়েছে।",
        english = "Okay, cancelled opening ${cancelled.app.displayName} as requested.",
        speakAloud = true,
      )
    }
  }

  override fun handleVoiceCommand(
    input: String,
    preferBangla: Boolean,
    initiatedByUser: Boolean,
  ): String? {
    val lower = input.lowercase().trim()
    val matchedApp = detectRequestedApp(lower) ?: return null

    if (!initiatedByUser) {
      guardUserInitiated(matchedApp, "voice open ${matchedApp.displayName}", initiatedByUser = false)
      return if (preferBangla) {
        "গোপনীয়তা নীতির কারণে ব্যবহারকারীর স্পষ্ট অনুরোধ ছাড়া অ্যাপ খোলা হয় না।"
      } else {
        "Privacy protection blocks opening apps without an explicit user request."
      }
    }

    // Never auto-send social posts or chat messages via voice.
    val mentionsAutoSend =
      lower.contains("send message") ||
        lower.contains("মেসেজ পাঠাও") ||
        lower.contains("পোস্ট করো") ||
        lower.contains("post on facebook")

    val ready = requestOpenApp(matchedApp, initiatedByUser = true, requireConfirmation = true)
    return if (ready) {
      if (mentionsAutoSend) {
        if (preferBangla) {
          "আমি নিজে থেকে কোনো মেসেজ পাঠাই না বা পোস্ট করি না। ${matchedApp.displayBangla} খোলার আগে অনুগ্রহ করে নিশ্চিত করুন।"
        } else {
          "MAX never sends messages or posts automatically. Please confirm opening ${matchedApp.displayName}."
        }
      } else {
        if (preferBangla) {
          "${matchedApp.displayBangla} খোলার আগে অনুগ্রহ করে নিশ্চিত করুন।"
        } else {
          "Please confirm opening ${matchedApp.displayName}."
        }
      }
    } else {
      val current = _state.value
      if (current is AppState.AppNotInstalledWithAlternatives) {
        if (preferBangla) current.messageBangla else current.messageEnglish
      } else {
        if (preferBangla) {
          "${matchedApp.displayBangla} এখন খোলা যায়নি।"
        } else {
          "Unable to open ${matchedApp.displayName} right now."
        }
      }
    }
  }

  override fun resetToDormant() {
    _pendingConfirmation.value = null
    _state.value = AppState.Dormant
  }

  override fun release() {
    _pendingConfirmation.value = null
    _state.value = AppState.Dormant
    eventListener = null
    errorTracker.logInfo(MODULE_NAME, "AppManager released safely (zero background monitors active).")
  }

  private fun executeConfirmedAction(
    action: AppConfirmationAction,
    app: SupportedApp,
  ): Boolean {
    val result: Result<String> =
      when (action) {
        is AppConfirmationAction.LaunchApp ->
          when (action.app) {
            SupportedApp.WHATSAPP -> whatsAppManager.launchWhatsApp(action.packageOverride)
            SupportedApp.MESSENGER -> messengerManager.launchMessenger(action.packageOverride)
            SupportedApp.FACEBOOK -> facebookManager.launchFacebook(action.packageOverride)
            SupportedApp.CHROME -> chromeManager.launchChrome(action.packageOverride)
            SupportedApp.YOUTUBE -> youtubeManager.launchYouTube()
            SupportedApp.GMAIL -> gmailManager.launchGmail()
            SupportedApp.GOOGLE_MAPS -> mapsManager.launchMaps()
          }

        is AppConfirmationAction.OpenAlternative ->
          when (action.alternative.kind) {
            AlternativeKind.INSTALLED_ALTERNATIVE_APP ->
              appLauncher.openPackage(action.alternative.targetValue)
            AlternativeKind.SYSTEM_EMAIL_CLIENT ->
              gmailManager.openComposeDraftOnly("", "", "")
            AlternativeKind.GEO_MAP_CLIENT ->
              appLauncher.openSafeUri(Intent.ACTION_VIEW, action.alternative.targetValue)
            AlternativeKind.SYSTEM_BROWSER,
            AlternativeKind.WEB_FALLBACK,
            AlternativeKind.PLAY_STORE, ->
              appLauncher.openSafeHttpsUrl(action.alternative.targetValue)
          }

        is AppConfirmationAction.PrepareWhatsAppChatDraft ->
          whatsAppManager.openChatDraftOnly(action.phoneDigits, action.draftText)

        is AppConfirmationAction.OpenYouTubeQuery ->
          youtubeManager.openSearchQuery(action.query)

        is AppConfirmationAction.OpenChromeUrl ->
          chromeManager.openUrlInChrome(action.url)

        is AppConfirmationAction.DraftGmailMessage ->
          gmailManager.openComposeDraftOnly(action.recipientEmail, action.subject, action.body)

        is AppConfirmationAction.SearchGoogleMaps ->
          mapsManager.openPlaceSearch(action.destinationQuery)
      }

    return result.fold(
      onSuccess = { label ->
        val banglaMsg = "${app.displayBangla} সফলভাবে খোলা হয়েছে।"
        val englishMsg = "Opened ${app.displayName}."
        _state.value =
          AppState.Launched(
            app = app,
            targetLabel = label,
            messageBangla = banglaMsg,
            messageEnglish = englishMsg,
          )
        eventListener?.onAppAnnouncement(banglaMsg, englishMsg, speakAloud = true)
        true
      },
      onFailure = { err ->
        val availability = refreshSingleAvailability(app)
        if (!availability.primaryInstalled && availability.alternatives.isNotEmpty()) {
          val banglaMsg =
            "${app.displayBangla} খোলা যায়নি কারণ অ্যাপটি ইনস্টল করা নেই। আপনি নিচের বিকল্পগুলো ব্যবহার করতে পারেন।"
          val englishMsg =
            "${app.displayName} is unavailable. Try one of the suggested alternatives below."
          _state.value =
            AppState.AppNotInstalledWithAlternatives(
              app = app,
              alternatives = availability.alternatives,
              messageBangla = banglaMsg,
              messageEnglish = englishMsg,
            )
          eventListener?.onAppAnnouncement(banglaMsg, englishMsg, speakAloud = true)
        } else {
          val errMsg = err.message ?: "Could not launch ${app.displayName}."
          _state.value = AppState.Error(errMsg)
          eventListener?.onAppAnnouncement(
            bangla = "${app.displayBangla} খোলা সম্ভব হয়নি: $errMsg",
            english = "Could not open ${app.displayName}: $errMsg",
            speakAloud = true,
          )
        }
        false
      },
    )
  }

  private fun stageConfirmation(pending: PendingAppConfirmation) {
    _pendingConfirmation.value = pending
    _state.value = AppState.AwaitingConfirmation(pending)
    eventListener?.onAppAnnouncement(
      bangla = pending.messageBangla,
      english = pending.messageEnglish,
      speakAloud = true,
    )
  }

  private fun refreshSingleAvailability(app: SupportedApp): AppAvailability {
    val updated =
      when (app) {
        SupportedApp.WHATSAPP -> whatsAppManager.checkAvailability()
        SupportedApp.MESSENGER -> messengerManager.checkAvailability()
        SupportedApp.FACEBOOK -> facebookManager.checkAvailability()
        SupportedApp.CHROME -> chromeManager.checkAvailability()
        SupportedApp.YOUTUBE -> youtubeManager.checkAvailability()
        SupportedApp.GMAIL -> gmailManager.checkAvailability()
        SupportedApp.GOOGLE_MAPS -> mapsManager.checkAvailability()
      }
    val current = _appAvailabilities.value.toMutableMap()
    current[app] = updated
    _appAvailabilities.value = current
    return updated
  }

  private fun guardUserInitiated(
    app: SupportedApp?,
    actionDescription: String,
    initiatedByUser: Boolean,
  ): Boolean {
    if (!initiatedByUser) {
      val reason =
        "Blocked automatic/background attempt to $actionDescription. Apps open strictly on explicit user command."
      errorTracker.logWarning(MODULE_NAME, reason)
      _pendingConfirmation.value = null
      _state.value = AppState.BlockedByPrivacyPolicy(app, reason)
      return false
    }
    // Synchronize read of PermissionController & PrivacyGuard policy without triggering prompts
    val policy = privacyGuard.policy.value
    val internetSnapshots = permissionController.snapshots.value
    if (internetSnapshots.isNotEmpty() && !policy.allowMicrophoneWakeWord && app == null) {
      return false
    }
    return true
  }

  private fun detectRequestedApp(lower: String): SupportedApp? {
    val hasOpenVerb =
      lower.contains("খোলো") ||
        lower.contains("খুলুন") ||
        lower.contains("চালু করো") ||
        lower.contains("open") ||
        lower.contains("launch") ||
        lower.contains("start")

    val hasSearchVerb =
      lower.contains("সার্চ") ||
        lower.contains("খুঁজে দেখো") ||
        lower.contains("search")

    return when {
      lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ") -> SupportedApp.WHATSAPP
      lower.contains("messenger") || lower.contains("মেসেঞ্জার") -> SupportedApp.MESSENGER
      lower.contains("facebook") || lower.contains("ফেসবুক") -> SupportedApp.FACEBOOK
      lower.contains("chrome") || lower.contains("ক্রোম") -> SupportedApp.CHROME
      lower.contains("gmail") || lower.contains("জিমেইল") -> SupportedApp.GMAIL
      lower.contains("google maps") || lower.contains("maps") || lower.contains("ম্যাপস") || lower.contains("ম্যাপ") ->
        SupportedApp.GOOGLE_MAPS
      (lower.contains("youtube") || lower.contains("ইউটিউব")) && hasOpenVerb && !hasSearchVerb ->
        SupportedApp.YOUTUBE
      else -> null
    }
  }

  companion object {
    private const val MODULE_NAME = "AppManager"
  }
}
