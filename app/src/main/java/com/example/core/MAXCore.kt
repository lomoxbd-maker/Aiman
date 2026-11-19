package com.example.core

import android.net.Uri
import com.example.apps.AppController
import com.example.apps.AppEventListener
import com.example.apps.AppLauncherCapability
import com.example.apps.AppManager
import com.example.apps.AppState
import com.example.automation.AutomationManager
import com.example.automation.RoutineCategory
import com.example.brain.AIBrainManager
import com.example.brain.BrainLifecycleState
import com.example.camera.CameraCapability
import com.example.camera.CameraController
import com.example.camera.CameraEventListener
import com.example.camera.CameraManager
import com.example.camera.CameraMode
import com.example.camera.CameraState
import com.example.camera.CapturedMediaResult
import com.example.conversation.ConversationController
import com.example.conversation.ConversationManager
import com.example.emotion.AffectiveTone
import com.example.emotion.EmotionController
import com.example.emotion.EmotionControllerContract
import com.example.emotion.EmotionManager
import com.example.emotion.EmotionType
import com.example.emotion.PersonalityController
import com.example.emotion.PersonalityManager
import com.example.files.FileCapability
import com.example.files.FileController
import com.example.files.FileEventListener
import com.example.files.FileManager
import com.example.files.FileState
import com.example.floating.FloatingAssistantManager
import com.example.floating.FloatingState
import com.example.gallery.GalleryCapability
import com.example.gallery.GalleryController
import com.example.gallery.GalleryEventListener
import com.example.gallery.GalleryFilter
import com.example.gallery.GalleryManager
import com.example.gallery.GalleryMediaItem
import com.example.gallery.GalleryState
import com.example.gallery.ImageSearchResult
import com.example.gallery.OcrPreparationResult
import com.example.internet.InternetController
import com.example.internet.InternetEventListener
import com.example.internet.InternetManager
import com.example.internet.InternetState
import com.example.internet.NetworkCapability
import com.example.internet.SearchCategory
import com.example.internet.WebPageContent
import com.example.internet.WebSearchResultItem
import com.example.memory.MemoryActionResult
import com.example.memory.MemoryCategory
import com.example.memory.MemoryController
import com.example.memory.MemoryEngineState
import com.example.memory.MemoryManager
import com.example.memory.MemorySettings
import com.example.memory.MemoryStore
import com.example.memory.MemoryType
import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyController
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.phone.CalculationRecord
import com.example.phone.PhoneCapability
import com.example.phone.PhoneController
import com.example.phone.PhoneEventListener
import com.example.phone.PhoneManager
import com.example.phone.PhoneState
import com.example.service.AssistantServiceContract
import com.example.settings.MAXSettings
import com.example.settings.SettingsStore
import com.example.smart.SmartEventListener
import com.example.smart.SmartFeatureManager
import com.example.smart.SmartFeatureState
import com.example.smart.SmartFeaturesController
import com.example.speech.SpeechController
import com.example.speech.SpeechRecognitionCallback
import com.example.speech.SpeechState
import com.example.tts.TTSConfig
import com.example.tts.TTSController
import com.example.tts.TTSState
import com.example.update.AppUpdateManager
import com.example.utils.DeviceCompatibilityChecker
import com.example.utils.DeviceCompatibilityProfile
import com.example.utils.ErrorLogger
import com.example.utils.ErrorTracker
import com.example.wakeword.WakeWordConfig
import com.example.wakeword.WakeWordController
import com.example.wakeword.WakeWordListener
import com.example.wakeword.WakeWordState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MAXCore — Central controller of the MAX Android AI Assistant.
 *
 * Coordinates all 16 subsystems via interfaces while strictly enforcing privacy, wake-word
 * gating, respectful follow-up for Sanju, and automatic resource cleanup on completion/background.
 */
class MAXCore(
  val settingsManager: SettingsStore,
  val permissionManager: PermissionController,
  val privacyController: PrivacyGuard = PrivacyController({ permissionManager }, ErrorLogger()),
  val permissionRequestHandler: PermissionRequestHandler =
    PermissionRequestHandler(permissionManager, privacyController, ErrorLogger()),
  val speechManager: SpeechController,
  val ttsManager: TTSController,
  val memoryManager: MemoryStore,
  val memoryController: MemoryController = memoryManager as MemoryController,
  val wakeWordManager: WakeWordController,
  val errorLogger: ErrorTracker,
  val conversationEngine: ConversationController,
  val emotionEngine: EmotionController,
  val emotionManager: EmotionControllerContract = EmotionManager(errorTracker = errorLogger),
  val personalityManager: PersonalityController = PersonalityManager(emotionManager, memoryController),
  val serviceCoordinator: AssistantServiceContract,
  val compatibilityChecker: DeviceCompatibilityChecker,
  val cameraCapability: CameraCapability,
  val galleryCapability: GalleryCapability,
  val networkCapability: NetworkCapability,
  val phoneCapability: PhoneCapability,
  val appLauncherCapability: AppLauncherCapability,
  val fileCapability: FileCapability,
  val cameraManager: CameraController? = cameraCapability as? CameraController,
  val galleryManager: GalleryController? = galleryCapability as? GalleryController,
  val internetManager: InternetController? = networkCapability as? InternetController,
  val phoneManager: PhoneController? = phoneCapability as? PhoneController,
  val appManager: AppController? = appLauncherCapability as? AppController,
  val fileManager: FileController? = fileCapability as? FileController,
  val smartFeatureManager: SmartFeatureManager? = null,
  val smartController: SmartFeaturesController? = smartFeatureManager,
  val appUpdateManager: AppUpdateManager? = null,
  val brainManager: AIBrainManager? = null,
  val automationManager: AutomationManager? = null,
  val floatingAssistantManager: FloatingAssistantManager? = null,
  private val followUpWaitTimeoutMs: Long = DEFAULT_FOLLOW_UP_TIMEOUT_MS,
  private val enableNaturalFollowUp: Boolean = true,
  mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : WakeWordListener {

  val aiBrainManager: AIBrainManager =
    brainManager
      ?: AIBrainManager(
        emotionManager = emotionManager,
        memoryController = memoryController,
        personalityManager = personalityManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        ttsController = ttsManager,
        errorTracker = errorLogger,
      )

  private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
  private var followUpTimeoutJob: Job? = null

  private val _coreState = MutableStateFlow(CoreLifecycleState.UNINITIALIZED)
  val coreState: StateFlow<CoreLifecycleState> = _coreState.asStateFlow()

  private val _compatibilityProfile =
    MutableStateFlow(compatibilityChecker.inspect())
  val compatibilityProfile: StateFlow<DeviceCompatibilityProfile> =
    _compatibilityProfile.asStateFlow()

  private val _lastWakeWordEvent = MutableStateFlow<String?>(null)
  val lastWakeWordEvent: StateFlow<String?> = _lastWakeWordEvent.asStateFlow()

  private val _followUpPrompt = MutableStateFlow<String?>(null)
  val followUpPrompt: StateFlow<String?> = _followUpPrompt.asStateFlow()

  private val _lastCompletedTask = MutableStateFlow<String?>(null)
  val lastCompletedTask: StateFlow<String?> = _lastCompletedTask.asStateFlow()

  private val _isThinking = MutableStateFlow(false)
  val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

  private var initialized = false

  init {
    wakeWordManager.addListener(this)
    conversationEngine.attachVoiceOutput(ttsManager)
    (conversationEngine as? ConversationManager)?.setBrainManager(aiBrainManager)
    wireCameraAndGalleryEvents()
  }

  private fun wireCameraAndGalleryEvents() {
    val activeCamera = cameraManager as? CameraManager
    val activeGallery = galleryManager as? GalleryManager

    activeCamera?.setEventListener(
      object : CameraEventListener {
        override fun onPhotoSaved(result: CapturedMediaResult) {
          galleryManager?.addCapturedMedia(
            uriString = result.uriString,
            displayName =
              if (result.mode == CameraMode.SELFIE) "MAX_Selfie_${result.timestampMillis}.jpg"
              else "MAX_Photo_${result.timestampMillis}.jpg",
            mimeType = "image/jpeg",
            tags = listOf("camera", result.mode.name.lowercase(), result.lens.name.lowercase()),
          )
          announceVision(
            bangla = "ছবি তোলা সম্পন্ন হয়েছে এবং ক্যামেরা নিরাপদে বন্ধ করা হয়েছে।",
            english = "Photo saved safely and camera resources have been closed.",
          )
        }

        override fun onVideoSaved(result: CapturedMediaResult) {
          galleryManager?.addCapturedMedia(
            uriString = result.uriString,
            displayName = "MAX_Video_${result.timestampMillis}.mp4",
            mimeType = "video/mp4",
            tags = listOf("video", result.lens.name.lowercase()),
          )
          announceVision(
            bangla = "ভিডিও সংরক্ষণ করা হয়েছে এবং ক্যামেরা বন্ধ করা হয়েছে।",
            english = "Video saved and camera hardware closed.",
          )
        }

        override fun onQrScanned(rawValue: String, format: String) {
          galleryManager?.addCapturedMedia(
            uriString = rawValue,
            displayName = "QR_${format}_${System.currentTimeMillis()}",
            mimeType = "text/qr-code",
            tags = listOf("qr", "scan", "qr:$rawValue"),
          )
          announceVision(
            bangla = "QR কোড স্ক্যান সম্পন্ন: $rawValue। ক্যামেরা বন্ধ করা হয়েছে।",
            english = "QR code scanned: $rawValue. Camera closed.",
          )
        }

        override fun onCameraClosed(reason: String) {
          errorLogger.logInfo(MODULE_NAME, "Camera closed: $reason")
        }
      },
    )

    activeGallery?.setEventListener(
      object : GalleryEventListener {
        override fun onPhotosLoaded(count: Int, latestName: String?) {
          announceVision(
            bangla = "${count}টি নির্বাচিত মিডিয়া লোড করা হয়েছে।",
            english = "Loaded $count selected media item(s).",
            speakAloud = false,
          )
        }

        override fun onSearchCompleted(result: ImageSearchResult) {
          errorLogger.logInfo(MODULE_NAME, result.statusMessage)
        }

        override fun onOcrPrepared(result: OcrPreparationResult) {
          announceVision(
            bangla = "'${result.itemDisplayName}' ছবির জন্য OCR প্রস্তুত করা হয়েছে।",
            english = result.statusMessage,
            speakAloud = true,
          )
        }

        override fun onMediaRemoved(item: GalleryMediaItem) {
          announceVision(
            bangla = "'${item.displayName}' আপনার তালিকা থেকে সরানো হয়েছে।",
            english = "Removed '${item.displayName}' after your confirmation.",
            speakAloud = false,
          )
        }
      },
    )

    (internetManager as? InternetManager)?.setEventListener(
      object : InternetEventListener {
        override fun onSearchReady(
          query: String,
          category: SearchCategory,
          results: List<WebSearchResultItem>,
          summary: String,
        ) {
          announceVision(
            bangla = summary,
            english = summary,
            speakAloud = true,
          )
        }

        override fun onWebPageSummarized(page: WebPageContent, summary: String) {
          announceVision(
            bangla = summary,
            english = summary,
            speakAloud = true,
          )
        }

        override fun onNetworkError(message: String) {
          errorLogger.logWarning(MODULE_NAME, "Internet notice: $message")
          announceVision(
            bangla = "নেটওয়ার্ক সমস্যা হয়েছে: $message",
            english = "Network issue: $message",
            speakAloud = true,
          )
        }
      },
    )

    (phoneManager as? PhoneManager)?.setEventListener(
      object : PhoneEventListener {
        override fun onPhoneAnnouncement(bangla: String, english: String, speakAloud: Boolean) {
          announceVision(bangla = bangla, english = english, speakAloud = speakAloud)
        }
      },
    )

    (appManager as? AppManager)?.setEventListener(
      object : AppEventListener {
        override fun onAppAnnouncement(bangla: String, english: String, speakAloud: Boolean) {
          announceVision(bangla = bangla, english = english, speakAloud = speakAloud)
        }
      },
    )

    (fileManager as? FileManager)?.setEventListener(
      object : FileEventListener {
        override fun onFilesAnnouncement(bangla: String, english: String, speakAloud: Boolean) {
          announceVision(bangla = bangla, english = english, speakAloud = speakAloud)
        }
      },
    )

    smartController?.setEventListener(
      object : SmartEventListener {
        override fun onSmartAnnouncement(bangla: String, english: String, speakAloud: Boolean) {
          announceVision(bangla = bangla, english = english, speakAloud = speakAloud)
        }
      },
    )

    (conversationEngine as? ConversationManager)?.setVisionCommandHandler { input ->
      handleNaturalVisionCommand(input)
    }
  }

  private fun announceVision(
    bangla: String,
    english: String,
    speakAloud: Boolean = true,
  ) {
    val lang = ttsManager.voiceConfig.value.language
    val conv = conversationEngine as? ConversationManager
    if (conv != null) {
      conv.announceVisionOutcome(bangla, english, lang, speakAloud)
    } else if (speakAloud) {
      val msg = if (lang == com.example.tts.VoiceLanguage.ENGLISH_US) english else bangla
      conversationEngine.deliverAssistantResponse(msg, com.example.tts.SpeechStyle.HELPFUL)
    }
  }

  private fun handleNaturalVisionCommand(input: String): String? {
    val lower = input.lowercase()
    val preferEnglish = ttsManager.voiceConfig.value.language == com.example.tts.VoiceLanguage.ENGLISH_US
    val preferBangla = !preferEnglish

    // Check explicit App Control commands first (e.g., "MAX, WhatsApp খোলো", "MAX, YouTube খোলো")
    appManager?.handleVoiceCommand(input, preferBangla = preferBangla, initiatedByUser = true)?.let { appReply ->
      return appReply
    }

    // Check Smart Automation voice commands (Morning/Night Routine, Daily Briefing)
    automationManager?.handleVoiceAutomationCommand(input, preferBangla = preferBangla, initiatedByUser = true)?.let { autoReply ->
      return autoReply
    }

    // Check Smart Features voice commands (Translation, Reminder, Notes, Calendar, Voice Notes, AI Suggestions)
    smartController?.handleVoiceCommand(input, preferBangla = preferBangla, initiatedByUser = true)?.let { smartReply ->
      return smartReply
    }

    // Check Internet & Browser voice commands next
    if (lower.contains("youtube") || lower.contains("ইউটিউব")) {
      val cleanQuery =
        input
          .replace(Regex("(?i)max,?"), "")
          .replace(Regex("(?i)youtube(-এ| এ)?"), "")
          .replace("সার্চ করো", "")
          .replace("খুঁজে দেখো", "")
          .replace("search", "")
          .trim()
      val query = cleanQuery.ifBlank { "Bangla music" }
      val started = searchInternet(query, SearchCategory.YOUTUBE, initiatedByUser = true)
      return if (started) {
        if (preferEnglish) "Searching YouTube for '$query'..." else "YouTube-এ '$query' সার্চ করা হচ্ছে..."
      } else {
        if (preferEnglish) "Please confirm Internet permission before searching YouTube."
        else "YouTube সার্চ করার আগে অনুগ্রহ করে ইন্টারনেট অনুমতি নিশ্চিত করুন।"
      }
    }

    if (lower.contains("খবর") || lower.contains("সংক্ষেপে") || lower.contains("summarize") || lower.contains("news")) {
      val cleanTopic =
        input
          .replace(Regex("(?i)max,?"), "")
          .replace("এই খবরটা সংক্ষেপে বলো", "")
          .replace("সংক্ষেপে বলো", "")
          .replace("খবর", "")
          .replace(Regex("(?i)summarize( the)?( news)?"), "")
          .trim()
      val started = summarizeWebContent(cleanTopic.ifBlank { null }, initiatedByUser = true)
      return if (started) {
        if (preferEnglish) "Fetching and summarizing the latest information calmly..."
        else "আপনার জন্য সংক্ষিপ্ত সারসংক্ষেপ প্রস্তুত করা হচ্ছে..."
      } else {
        if (preferEnglish) "Please confirm Internet permission to summarize web content."
        else "তথ্য সংক্ষেপে জানার আগে অনুগ্রহ করে ইন্টারনেট অনুমতি দিন।"
      }
    }

    if (lower.contains("google") || lower.contains("খুঁজে দেখো") || lower.contains("search ")) {
      val cleanQuery =
        input
          .replace(Regex("(?i)max,?"), "")
          .replace(Regex("(?i)google(-এ| এ)?"), "")
          .replace("খুঁজে দেখো", "")
          .replace(Regex("(?i)search( for)?"), "")
          .trim()
      val query = cleanQuery.ifBlank { input.trim() }
      val started = searchInternet(query, SearchCategory.WEB, initiatedByUser = true)
      return if (started) {
        if (preferBangla) "Google ও ওয়েবে '$query' খুঁজে দেখা হচ্ছে..."
        else "Searching the web for '$query'..."
      } else {
        if (preferBangla) "ওয়েবে খোঁজার আগে অনুগ্রহ করে ইন্টারনেট অনুমতি নিশ্চিত করুন।"
        else "Please confirm Internet permission before searching the web."
      }
    }

    if (lower.contains("weather") || lower.contains("আবহাওয়া")) {
      val city =
        input
          .replace(Regex("(?i)max,?"), "")
          .replace(Regex("(?i)weather( in)?"), "")
          .replace("আবহাওয়া", "")
          .trim()
          .ifBlank { "Dhaka" }
      val started = searchInternet(city, SearchCategory.WEATHER, initiatedByUser = true)
      return if (started) {
        if (preferBangla) "$city-এর আবহাওয়া দেখা হচ্ছে..." else "Checking weather for $city..."
      } else {
        if (preferBangla) "আবহাওয়া দেখতে ইন্টারনেট অনুমতি নিশ্চিত করুন।"
        else "Please confirm Internet permission to check weather."
      }
    }

    if (lower.contains("wikipedia") || lower.contains("উইকিপিডিয়া")) {
      val topic =
        input
          .replace(Regex("(?i)max,?"), "")
          .replace(Regex("(?i)wikipedia"), "")
          .replace("উইকিপিডিয়া", "")
          .trim()
          .ifBlank { "Bangladesh" }
      val started = searchInternet(topic, SearchCategory.WIKIPEDIA, initiatedByUser = true)
      return if (started) {
        if (preferBangla) "উইকিপিডিয়া থেকে '$topic' দেখা হচ্ছে..." else "Searching Wikipedia for '$topic'..."
      } else {
        if (preferBangla) "উইকিপিডিয়া দেখতে ইন্টারনেট অনুমতি দিন।"
        else "Please confirm Internet permission to search Wikipedia."
      }
    }

    return when {
      lower.contains("selfie") || lower.contains("সেলফি") -> {
        val opened = openCamera(CameraMode.SELFIE, initiatedByUser = true)
        if (opened) {
          if (preferEnglish) "Opening selfie camera now." else "সেলফি ক্যামেরা চালু করা হচ্ছে।"
        } else {
          if (preferEnglish) "Please confirm Camera permission before taking a selfie."
          else "সেলফি তুলতে অনুগ্রহ করে ক্যামেরা অনুমতি নিশ্চিত করুন।"
        }
      }
      lower.contains("qr") || lower.contains("কিউআর") -> {
        val opened = openCamera(CameraMode.QR_SCAN, initiatedByUser = true)
        if (opened) {
          if (preferEnglish) "Opening QR scanner now." else "QR স্ক্যানার চালু করা হচ্ছে।"
        } else {
          if (preferEnglish) "Please confirm Camera permission to scan QR codes."
          else "QR স্ক্যান করতে ক্যামেরা অনুমতি দিন।"
        }
      }
      lower.contains("record video") || lower.contains("ভিডিও") -> {
        val opened = openCamera(CameraMode.VIDEO_RECORDING, initiatedByUser = true)
        if (opened) {
          if (preferEnglish) "Video recording mode is ready." else "ভিডিও রেকর্ডিং মোড প্রস্তুত।"
        } else {
          if (preferEnglish) "Please confirm Camera permission to record video."
          else "ভিডিও রেকর্ড করতে ক্যামেরা অনুমতি দিন।"
        }
      }
      lower.contains("open camera") || lower.contains("take photo") || lower.contains("ক্যামেরা") -> {
        val opened = openCamera(CameraMode.PHOTO, initiatedByUser = true)
        if (opened) {
          if (preferEnglish) "Camera is ready for your photo." else "ছবি তোলার জন্য ক্যামেরা প্রস্তুত।"
        } else {
          if (preferEnglish) "Please confirm Camera permission first."
          else "প্রথমে ক্যামেরা ব্যবহারের অনুমতি দিন।"
        }
      }
      lower.contains("open gallery") || lower.contains("গ্যালারি") -> {
        val allowed = openGallery(initiatedByUser = true)
        if (allowed) {
          if (preferEnglish) "Gallery picker is authorized and ready in the Vision tab."
          else "আপনার অনুমতিক্রমে গ্যালারি প্রস্তুত করা হয়েছে।"
        } else {
          if (preferEnglish) "Please confirm Gallery access first."
          else "প্রথমে গ্যালারি ব্যবহারের অনুমতি নিশ্চিত করুন।"
        }
      }
      lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") -> {
        val turnOff = lower.contains("off") || lower.contains("বন্ধ")
        val targetOn = !turnOff
        val staged = phoneManager?.requestToggleFlashlight(targetOn, initiatedByUser = true, requireConfirmation = true) ?: false
        if (staged) {
          if (preferEnglish) "Please confirm turning flashlight ${if (targetOn) "ON" else "OFF"}."
          else "ফ্ল্যাশলাইট ${if (targetOn) "চালু" else "বন্ধ"} করতে অনুগ্রহ করে নিশ্চিত করুন।"
        } else {
          if (preferEnglish) "Flashlight hardware is unavailable on this device."
          else "এই ডিভাইসে ফ্ল্যাশলাইট পাওয়া যাচ্ছে না।"
        }
      }
      lower.contains("calculate") || lower.contains("হিসাব") || lower.contains("কত হয়") -> {
        val expr =
          input
            .replace(Regex("(?i)max,?"), "")
            .replace(Regex("(?i)calculate"), "")
            .replace("হিসাব করো", "")
            .replace("হিসাব", "")
            .replace("কত হয়", "")
            .trim()
        val rec = phoneManager?.calculate(expr, initiatedByUser = true)
        if (rec != null) {
          if (preferBangla) "${rec.expression} এর উত্তর হলো ${rec.result}।"
          else "${rec.expression} equals ${rec.result}."
        } else {
          if (preferBangla) "অনুগ্রহ করে সঠিক অঙ্ক বলুন বা লিখুন।"
          else "Please provide a valid math expression to calculate."
        }
      }
      lower.contains("timer") || lower.contains("টাইমার") -> {
        val digits = Regex("\\d+").find(input)?.value?.toIntOrNull() ?: 60
        val seconds = if (lower.contains("min") || lower.contains("মিনিট")) digits * 60 else digits
        phoneManager?.startTimer(seconds, "MAX Voice Timer", initiatedByUser = true)
        if (preferBangla) "${seconds} সেকেন্ডের টাইমার শুরু করা হয়েছে।"
        else "Started a ${seconds}-second timer for you."
      }
      lower.contains("stopwatch") || lower.contains("স্টপওয়াচ") -> {
        if (lower.contains("stop") || lower.contains("pause") || lower.contains("থামাও") || lower.contains("বন্ধ")) {
          phoneManager?.pauseStopwatch()
          if (preferBangla) "স্টপওয়াচ থামানো হয়েছে।" else "Stopwatch paused."
        } else {
          phoneManager?.startStopwatch(initiatedByUser = true)
          if (preferBangla) "স্টপওয়াচ চালু করা হয়েছে।" else "Stopwatch started."
        }
      }
      lower.contains("alarm") || lower.contains("অ্যালার্ম") -> {
        val match = Regex("(\\d{1,2})[:.](\\d{2})").find(input)
        val hour = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val minute = match?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        phoneManager?.requestSetAlarm(hour, minute, "MAX Alarm", initiatedByUser = true)
        if (preferBangla) "%02d:%02d-এ অ্যালার্ম সেট করার আগে নিশ্চিত করুন।".format(hour, minute)
        else "Please confirm setting your alarm for %02d:%02d.".format(hour, minute)
      }
      lower.contains("sms") || lower.contains("মেসেজ") -> {
        val num = Regex("[+\\d][\\d-]{3,}").find(input)?.value ?: ""
        if (num.isNotBlank()) {
          phoneManager?.requestSms(num, "", initiatedByUser = true)
          if (preferBangla) "$num নম্বরে SMS খোলার আগে নিশ্চিত করুন।"
          else "Please confirm opening SMS composer for $num."
        } else {
          if (preferBangla) "Phone স্ক্রিন থেকে প্রাপকের নম্বর ও বার্তা লিখুন।"
          else "Please enter recipient and message in Phone Controls."
        }
      }
      lower.contains("call ") || lower.contains("কল করো") -> {
        val num = Regex("[+\\d][\\d-]{3,}").find(input)?.value ?: ""
        if (num.isNotBlank()) {
          phoneManager?.requestCall(num, initiatedByUser = true)
          if (preferBangla) "$num নম্বরে কল করার আগে অনুগ্রহ করে নিশ্চিত করুন।"
          else "Please confirm calling $num."
        } else {
          if (preferBangla) "অনুগ্রহ করে Phone স্ক্রিনে নম্বর বা কন্টাক্ট নির্বাচন করুন।"
          else "Please enter a phone number or pick a contact in Phone Controls."
        }
      }
      lower.contains("open file") || lower.contains("ফাইল খোলো") || lower.contains("read pdf") || lower.contains("pdf পড়ো") -> {
        val ok = fileManager?.requestOpenFilePicker(initiatedByUser = true, preferBangla = preferBangla) ?: false
        if (ok) {
          if (preferBangla) "ফাইল বেছে নেওয়ার জন্য Files ও Smart স্ক্রিন প্রস্তুত।"
          else "File browser is authorized in the Files & Smart Features screen."
        } else {
          if (preferBangla) "ফাইল খুলতে অনুগ্রহ করে Files অনুমতি নিশ্চিত করুন।"
          else "Please confirm Files access permission first."
        }
      }
      else -> null
    }
  }

  /**
   * Boots the MAX foundation safely without activating invasive hardware or starting
   * unnecessary background processes.
   */
  fun initialize() {
    if (initialized) return
    initialized = true
    _coreState.value = CoreLifecycleState.INITIALIZING

    scope.launch {
      val compat = compatibilityChecker.inspect()
      _compatibilityProfile.value = compat

      val settings = runCatching { settingsManager.settingsFlow.first() }.getOrDefault(MAXSettings())
      permissionManager.refreshAll()
      memoryManager.initialize(settings.memoryRetentionEnabled)
      speechManager.initialize(compat.hasSpeechRecognitionService)
      ttsManager.updateVoiceConfig(
        com.example.tts.VoiceConfig(
          language = settings.voiceLanguage,
          speechStyle = settings.speechStyle,
          speechSpeed = settings.ttsSpeechRate,
          pitch = settings.ttsPitch,
        ),
      )
      ttsManager.initialize()
      applyWakeWordSettings(settings, compat)
      serviceCoordinator.markOnDemandReady()

      // Begin in safe standby / disarmed state — never start full speech recognition at boot.
      _coreState.value =
        if (settings.wakeWordEnabled && permissionManager.hasWakeWordAudioPermission() && compat.hasMicrophone) {
          wakeWordManager.startStandby(
            hasAudioPermission = true,
            hasMicrophoneHardware = true,
          )
          CoreLifecycleState.STANDBY
        } else {
          CoreLifecycleState.IDLE_DISARMED
        }

      errorLogger.logInfo(
        MODULE_NAME,
        "MAXCore initialized (${_coreState.value}, SDK ${compat.sdkInt}, lowRam=${compat.isLowRamDevice}).",
      )
    }
  }

  /**
   * Arms Wake Word standby ("MAX") after permission check. Full speech recognition remains
   * dormant until "MAX" is detected.
   */
  fun armWakeWordStandby() {
    val compat = compatibilityChecker.inspect()
    _compatibilityProfile.value = compat
    val auth =
      privacyController.authorizeAction(
        action = SensitiveAction.WAKE_WORD_STANDBY,
        initiatedByUser = true,
      )
    val hasPermission = auth is PrivacyCheckResult.Allowed

    wakeWordManager.startStandby(
      hasAudioPermission = hasPermission,
      hasMicrophoneHardware = compat.hasMicrophone,
    )

    _coreState.value =
      if (hasPermission && compat.hasMicrophone) {
        speechManager.returnToStandby()
        conversationEngine.returnToDormant()
        emotionEngine.setTone(AffectiveTone.NEUTRAL_CALM)
        CoreLifecycleState.STANDBY
      } else {
        CoreLifecycleState.IDLE_DISARMED
      }
    permissionManager.refreshAll(privacyController.policy.value)
  }

  fun requestWakeWordPermissionWithExplanation() {
    val preferBangla = ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    permissionRequestHandler.requestWhenNeeded(
      permission = AppPermission.MICROPHONE_WAKE_WORD,
      preferBangla = preferBangla,
    )
  }

  fun setPermissionAllowedByUser(permission: AppPermission, allowed: Boolean) {
    privacyController.setPermissionAllowed(permission, allowed)
    if (permission == AppPermission.MICROPHONE_WAKE_WORD && !allowed) {
      disarmWakeWordStandby()
    }
    permissionManager.refreshAll(privacyController.policy.value)
  }

  fun disarmWakeWordStandby() {
    wakeWordManager.stopStandby()
    ttsManager.stopSpeaking()
    speechManager.returnToStandby()
    conversationEngine.returnToDormant()
    aiBrainManager.clearSessionContext()
    emotionEngine.reset()
    _coreState.value = CoreLifecycleState.IDLE_DISARMED
  }

  /**
   * Returns MAX from active listening/prepared conversation back into low-power wake-word standby.
   */
  fun returnToStandby() {
    cancelFollowUpWait()
    _followUpPrompt.value = null
    _isThinking.value = false
    ttsManager.stopSpeaking()
    speechManager.returnToStandby()
    conversationEngine.returnToDormant()
    aiBrainManager.clearSessionContext()
    emotionEngine.setTone(AffectiveTone.NEUTRAL_CALM)
    releaseTransientTaskResources()
    val compat = compatibilityChecker.inspect()
    _compatibilityProfile.value = compat
    val hasMicPerm = permissionManager.hasWakeWordAudioPermission()
    if (compat.hasMicrophone && hasMicPerm) {
      wakeWordManager.startStandby(hasAudioPermission = true, hasMicrophoneHardware = true)
      _coreState.value = CoreLifecycleState.STANDBY
    } else {
      wakeWordManager.stopStandby()
      _coreState.value = CoreLifecycleState.IDLE_DISARMED
    }
    errorLogger.logInfo(MODULE_NAME, "MAX returned to ${_coreState.value}.")
  }

  /**
   * Releases transient device hardware handles cleanly after each completed task.
   */
  fun releaseTransientTaskResources() {
    phoneManager?.releaseTaskResources()
    privacyController.notifyResourcesReleased(SensitiveAction.READ_FILES)
    privacyController.notifyResourcesReleased(SensitiveAction.READ_CALENDAR)
  }

  /**
   * Pauses active audio/hardware cleanly when the app moves to the background, preventing silent
   * microphone or camera retention and optimizing battery life.
   */
  fun handleBackgroundLifecycle(inBackground: Boolean) {
    if (inBackground) {
      cancelFollowUpWait()
      _followUpPrompt.value = null
      _isThinking.value = false
      ttsManager.stopSpeaking()
      speechManager.returnToStandby()
      wakeWordManager.stopStandby()
      cameraManager?.closeCamera("App moved to background")
      internetManager?.cancelActiveRequest()
      releaseTransientTaskResources()
      serviceCoordinator.markStopped()
      if (_coreState.value == CoreLifecycleState.LISTENING_PREPARED ||
        _coreState.value == CoreLifecycleState.AWAITING_FOLLOW_UP ||
        _coreState.value == CoreLifecycleState.STANDBY
      ) {
        _coreState.value = CoreLifecycleState.IDLE_DISARMED
      }
      errorLogger.logInfo(MODULE_NAME, "Background lifecycle: released microphone & task resources safely.")
    } else if (initialized) {
      serviceCoordinator.markOnDemandReady()
      permissionManager.refreshAll(privacyController.policy.value)
      scope.launch {
        val settings = runCatching { settingsManager.settingsFlow.first() }.getOrDefault(MAXSettings())
        val compat = compatibilityChecker.inspect()
        _compatibilityProfile.value = compat
        if (settings.wakeWordEnabled && permissionManager.hasWakeWordAudioPermission() && compat.hasMicrophone) {
          armWakeWordStandby()
        }
      }
    }
  }

  /**
   * Starts or resumes active voice listening explicitly triggered by Sanju's tap on the Home mic.
   */
  fun activateManualVoiceSession() {
    cancelFollowUpWait()
    _followUpPrompt.value = null
    onWakeWordDetected(WakeWordConfig.DEFAULT_KEYWORD, 1.0f, System.currentTimeMillis())
  }

  /**
   * Ends follow-up immediately when Sanju indicates nothing else is needed and returns to Wake Mode.
   */
  fun finishFollowUpAndReturnToWakeMode(speakFarewell: Boolean = true) {
    cancelFollowUpWait()
    val activeLanguage = ttsManager.voiceConfig.value.language
    val conv = conversationEngine as? ConversationManager
    if (speakFarewell && conv != null) {
      val farewell = conv.craftWakeStandbyFarewell(activeLanguage)
      conv.deliverAssistantResponse(farewell, com.example.tts.SpeechStyle.CARING)
    }
    returnToStandby()
  }

  fun processConversationalMessage(
    userText: String,
    speakAloud: Boolean = true,
  ): String {
    cancelFollowUpWait()
    _followUpPrompt.value = null
    _isThinking.value = true

    val activeLanguage = ttsManager.voiceConfig.value.language
    val convManager = conversationEngine as? ConversationManager

    if (convManager != null && convManager.isDismissalOrDoneCue(userText)) {
      val farewell = convManager.craftWakeStandbyFarewell(activeLanguage)
      if (speakAloud) {
        convManager.deliverAssistantResponse(farewell, com.example.tts.SpeechStyle.CARING)
      }
      _lastCompletedTask.value =
        if (activeLanguage == com.example.tts.VoiceLanguage.ENGLISH_US) "Returned to Wake Mode"
        else "ওয়েক মোডে ফিরে এসেছে"
      _isThinking.value = false
      returnToStandby()
      return farewell
    }

    val reply =
      if (convManager != null) {
        val (_, response) = convManager.processUserMessage(userText, activeLanguage, speakAloud)
        response
      } else {
        val (emotionState, response) = personalityManager.craftPersonalizedReply(userText, activeLanguage)
        ttsManager.applyEmotionalVoiceProfile(emotionState.voiceProfile)
        conversationEngine.deliverAssistantResponse(response, emotionState.voiceProfile.speechStyle)
        response
      }

    _lastCompletedTask.value = userText.trim().take(52)
    releaseTransientTaskResources()
    _isThinking.value = false

    val awaitingClarification =
      aiBrainManager.brainState.value is BrainLifecycleState.AwaitingClarification ||
        aiBrainManager.brainState.value is BrainLifecycleState.AskingInternetConsent

    if (awaitingClarification) {
      // Keep listening naturally for Sanju's answer to the clarifying / permission question.
      _coreState.value = CoreLifecycleState.LISTENING_PREPARED
    } else if (enableNaturalFollowUp) {
      beginNaturalFollowUpWait(activeLanguage = activeLanguage, speakAloud = speakAloud)
    }
    return reply
  }

  private fun beginNaturalFollowUpWait(
    activeLanguage: com.example.tts.VoiceLanguage,
    speakAloud: Boolean,
  ) {
    val convManager = conversationEngine as? ConversationManager ?: return
    val followUp = convManager.craftFollowUpPrompt(activeLanguage)
    _followUpPrompt.value = followUp
    _coreState.value = CoreLifecycleState.AWAITING_FOLLOW_UP
    if (speakAloud) {
      ttsManager.speak(followUp, com.example.tts.SpeechStyle.CARING)
    }

    followUpTimeoutJob =
      scope.launch {
        delay(followUpWaitTimeoutMs)
        errorLogger.logInfo(
          MODULE_NAME,
          "No follow-up response within ${followUpWaitTimeoutMs}ms; returning calmly to Wake Mode.",
        )
        returnToStandby()
      }
  }

  private fun cancelFollowUpWait() {
    followUpTimeoutJob?.cancel()
    followUpTimeoutJob = null
  }

  fun saveStructuredMemory(
    type: MemoryType,
    content: String,
    hasExplicitPermission: Boolean = true,
  ): MemoryActionResult = memoryController.saveMemory(type, content, hasExplicitPermission = hasExplicitPermission)

  fun deleteMemoryById(id: String): Boolean = memoryController.deleteMemoryById(id)

  fun clearAllMemories(): MemoryActionResult = memoryController.deleteAllMemories()

  fun updateMemorySettings(settings: MemorySettings) {
    memoryController.updateMemorySettings(settings)
  }

  fun setEmotion(emotion: EmotionType) {
    emotionManager.setEmotion(emotion, "Manual style preview")
    ttsManager.applyEmotionalVoiceProfile(emotionManager.emotionState.value.voiceProfile)
  }

  fun speakResponse(text: String, style: com.example.tts.SpeechStyle? = null) {
    val activeStyle = style ?: ttsManager.voiceConfig.value.speechStyle
    conversationEngine.deliverAssistantResponse(text, activeStyle)
  }

  fun openCamera(mode: CameraMode, initiatedByUser: Boolean = true): Boolean {
    return cameraManager?.openCamera(mode, initiatedByUser) ?: false
  }

  fun closeCamera(reason: String = "Closed by user") {
    cameraManager?.closeCamera(reason)
  }

  fun openGallery(initiatedByUser: Boolean = true): Boolean {
    return galleryManager?.requestOpenGallery(initiatedByUser) ?: false
  }

  fun onGalleryMediaPicked(uris: List<Uri>) {
    galleryManager?.onMediaPicked(uris)
  }

  fun findGalleryPhotos(query: String, filter: GalleryFilter): ImageSearchResult? {
    return galleryManager?.findPhotos(query, filter)
  }

  fun prepareGalleryOcr(itemId: String): OcrPreparationResult? {
    return galleryManager?.prepareOcrForSelectedPhoto(itemId)
  }

  fun requestDeleteGalleryItem(itemId: String) {
    galleryManager?.requestDeleteMedia(itemId)
  }

  fun confirmDeleteGalleryItem() {
    galleryManager?.confirmDeleteMedia()
  }

  fun cancelDeleteGalleryItem() {
    galleryManager?.cancelDeleteMedia()
  }

  fun searchInternet(
    query: String,
    category: SearchCategory = SearchCategory.WEB,
    initiatedByUser: Boolean = true,
  ): Boolean {
    val preferBangla = ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return internetManager?.search(query, category, initiatedByUser, preferBangla) ?: false
  }

  fun openWebsite(
    url: String,
    initiatedByUser: Boolean = true,
  ): Boolean {
    val preferBangla = ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return internetManager?.openWebsite(url, initiatedByUser, preferBangla) ?: false
  }

  fun summarizeWebContent(
    topicOrUrl: String? = null,
    initiatedByUser: Boolean = true,
  ): Boolean {
    val preferBangla = ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return internetManager?.summarizeCurrentOrTopic(topicOrUrl, initiatedByUser, preferBangla) ?: false
  }

  fun openExternalVerifiedUrl(url: String, initiatedByUser: Boolean = true): Boolean {
    return internetManager?.openExternalVerifiedLink(url, initiatedByUser) ?: false
  }

  fun cancelInternetRequest() {
    internetManager?.cancelActiveRequest()
  }

  fun closeBrowserSession() {
    internetManager?.closeBrowserSession()
  }

  fun pauseSpeaking() = ttsManager.pauseSpeaking()

  fun resumeSpeaking() = ttsManager.resumeSpeaking()

  fun stopSpeaking() = ttsManager.stopSpeaking()

  fun syncSettings(settings: MAXSettings) {
    val compat = _compatibilityProfile.value
    applyWakeWordSettings(settings, compat)
    ttsManager.updateVoiceConfig(
      com.example.tts.VoiceConfig(
        language = settings.voiceLanguage,
        speechStyle = settings.speechStyle,
        speechSpeed = settings.ttsSpeechRate,
        pitch = settings.ttsPitch,
      ),
    )
    memoryManager.setRetentionEnabled(settings.memoryRetentionEnabled)
  }

  private fun applyWakeWordSettings(
    settings: MAXSettings,
    compat: DeviceCompatibilityProfile,
  ) {
    val throttle =
      if (settings.lowPowerStandbyMode) {
        maxOf(45L, compat.recommendedWakeWordIntervalMs)
      } else {
        compat.recommendedWakeWordIntervalMs
      }
    wakeWordManager.updateConfig(
      WakeWordConfig(
        keyword = WakeWordConfig.DEFAULT_KEYWORD,
        sensitivity = settings.wakeWordSensitivity,
        standbyThrottleMs = throttle,
      ),
    )
  }

  // --- WakeWordListener implementation ---

  override fun onWakeWordDetected(keyword: String, confidence: Float, detectedAtMillis: Long) {
    // Final Wake Word Flow:
    // 1. Notify MAXCore & pause low-power wake listener
    // 2. Activate assistant listening state & prepare SpeechRecognizer
    // 3. Deliver recognized utterance directly into ConversationManager & post-task follow-up
    cancelFollowUpWait()
    _followUpPrompt.value = null
    wakeWordManager.pauseForConversation()
    _coreState.value = CoreLifecycleState.LISTENING_PREPARED
    _lastWakeWordEvent.value = "Detected '$keyword' (${(confidence * 100).toInt()}%)"

    emotionEngine.setTone(AffectiveTone.ATTENTIVE)
    speechManager.prepareForVoiceConversation(triggerKeyword = keyword)
    conversationEngine.prepareSession(triggerKeyword = keyword)
    ttsManager.prepareVoiceResponseSession()

    if (permissionManager.hasWakeWordAudioPermission()) {
      val langTag = ttsManager.voiceConfig.value.language.tag
      speechManager.startRecognitionSession(
        languageTag = langTag,
        callback =
          object : SpeechRecognitionCallback {
            override fun onFinalTranscript(text: String) {
              processConversationalMessage(text, speakAloud = true)
            }

            override fun onSpeechTimeoutOrError(reason: String) {
              errorLogger.logInfo(MODULE_NAME, "Speech capture paused cleanly: $reason")
            }
          },
      )
    }

    memoryManager.remember(
      MemoryCategory.SYSTEM_SESSION,
      "Wake word '$keyword' activated listening state.",
    )

    errorLogger.logInfo(
      MODULE_NAME,
      "Wake word '$keyword' detected -> Assistant listening & speech recognition prepared.",
    )
  }

  override fun onWakeWordStateChanged(state: WakeWordState) {
    if (state is WakeWordState.PermissionRequired || state is WakeWordState.UnsupportedFallback) {
      if (_coreState.value != CoreLifecycleState.LISTENING_PREPARED &&
        _coreState.value != CoreLifecycleState.AWAITING_FOLLOW_UP
      ) {
        _coreState.value = CoreLifecycleState.IDLE_DISARMED
      }
    }
  }

  override fun onWakeWordError(message: String, throwable: Throwable?) {
    errorLogger.logError(MODULE_NAME, "WakeWord callback error: $message", throwable)
  }

  /**
   * Produces a live snapshot of all 20 architectural modules and their current readiness.
   */
  fun buildModuleTopology(): List<ModuleDescriptor> {
    val wakeState = wakeWordManager.state.value
    val speechState = speechManager.state.value
    val ttsState = ttsManager.state.value
    val memState = memoryManager.state.value
    val compat = _compatibilityProfile.value

    return listOf(
      ModuleDescriptor(
        packageName = "core/",
        title = "MAXCore",
        roleSummary = "Central controller orchestrating lifecycle, wake-word handoff, and module readiness.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          if (_coreState.value == CoreLifecycleState.LISTENING_PREPARED) {
            ModuleReadiness.ACTIVE_LISTENING
          } else {
            ModuleReadiness.READY
          },
        privacyNote = "Coordinates subsystems in memory with zero external telemetry.",
      ),
      ModuleDescriptor(
        packageName = "wakeword/",
        title = "WakeWordManager",
        roleSummary = "Monitors standby strictly for keyword 'MAX' using ephemeral RAM frames.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when (wakeState) {
            is WakeWordState.StandbyListening -> ModuleReadiness.STANDBY
            is WakeWordState.Detected -> ModuleReadiness.ACTIVE_LISTENING
            is WakeWordState.UnsupportedFallback -> ModuleReadiness.FALLBACK_MODE
            else -> ModuleReadiness.READY
          },
        privacyNote = "Never records conversations or saves microphone audio.",
      ),
      ModuleDescriptor(
        packageName = "speech/",
        title = "SpeechManager",
        roleSummary = "Pre-wake gated speech recognizer; prepares voice conversation only after 'MAX'.",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness =
          when (speechState) {
            SpeechState.PREPARED_FOR_CONVERSATION -> ModuleReadiness.ACTIVE_LISTENING
            SpeechState.UNSUPPORTED_FALLBACK -> ModuleReadiness.FALLBACK_MODE
            else -> ModuleReadiness.STANDBY
          },
        privacyNote = "Full speech recognition never starts before wake-word detection.",
      ),
      ModuleDescriptor(
        packageName = "tts/",
        title = "TTSManager",
        roleSummary = "Android TextToSpeech readiness with configurable rate, pitch, and fallback.",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness =
          if (ttsState == TTSState.UNSUPPORTED_FALLBACK) {
            ModuleReadiness.FALLBACK_MODE
          } else {
            ModuleReadiness.READY
          },
        privacyNote = "Local synthesis engine only; no cloud voice streaming.",
      ),
      ModuleDescriptor(
        packageName = "brain/",
        title = "AIBrainManager",
        roleSummary = "IntentAnalyzer, ContextManager, DecisionEngine & ResponsePlanner thinking before every reply.",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness =
          when (aiBrainManager.brainState.value) {
            is BrainLifecycleState.Thinking,
            is BrainLifecycleState.AwaitingClarification, -> ModuleReadiness.ACTIVE_LISTENING
            else -> ModuleReadiness.READY
          },
        privacyNote = "Offline-first reasoning; asks permission before using internet and never leaks context.",
      ),
      ModuleDescriptor(
        packageName = "automation/",
        title = "AutomationManager",
        roleSummary = "Morning & Night routines, Daily Briefing & scheduled offline tasks for Sanju.",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness = ModuleReadiness.READY,
        privacyNote = "Runs strictly offline on user triggers with zero idle polling.",
      ),
      ModuleDescriptor(
        packageName = "floating/",
        title = "FloatingAssistantManager",
        roleSummary = "User-activated floating quick-voice bubble and system overlay service.",
        category = ModuleCategory.INFRASTRUCTURE,
        readiness =
          when (floatingAssistantManager?.state?.value) {
            is FloatingState.MinimizedBubble,
            is FloatingState.ExpandedQuickPanel, -> ModuleReadiness.ACTIVE_LISTENING
            else -> ModuleReadiness.STANDBY
          },
        privacyNote = "Appears only on tap; never hides mic/camera recording.",
      ),
      ModuleDescriptor(
        packageName = "conversation/",
        title = "ConversationManager",
        roleSummary = "Human-like multi-turn dialogue orchestrator connected to AIBrainManager, EmotionManager & TTS.",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness =
          if (_coreState.value == CoreLifecycleState.LISTENING_PREPARED) {
            ModuleReadiness.ACTIVE_LISTENING
          } else {
            ModuleReadiness.STANDBY
          },
        privacyNote = "Session-scoped only; avoids robotic repetition locally.",
      ),
      ModuleDescriptor(
        packageName = "emotion/",
        title = "EmotionManager",
        roleSummary = "Mood detector & 7-state emotional personality engine (${emotionManager.emotionState.value.currentEmotion.displayName}).",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness = ModuleReadiness.READY,
        privacyNote = "Purely deterministic local mood detection with low RAM footprint.",
      ),
      ModuleDescriptor(
        packageName = "memory/",
        title = "MemoryManager",
        roleSummary = "Bounded in-memory user memory ring buffer with privacy retention switch.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          if (memState == MemoryEngineState.DISABLED_BY_PRIVACY) {
            ModuleReadiness.DORMANT_LOCKED
          } else {
            ModuleReadiness.READY
          },
        privacyNote = "Volatile ring buffer (max 64 entries) optimized for low RAM.",
      ),
      ModuleDescriptor(
        packageName = "permissions/",
        title = "PermissionManager",
        roleSummary = "Passive OS permission auditor enforcing strict dormant locks on sensitive scopes.",
        category = ModuleCategory.CORE_MANAGER,
        readiness = ModuleReadiness.READY,
        privacyNote = "Never auto-prompts for camera, gallery, files, or location.",
      ),
      ModuleDescriptor(
        packageName = "settings/",
        title = "SettingsManager",
        roleSummary = "Jetpack DataStore persistence for wake sensitivity, TTS tuning, and privacy locks.",
        category = ModuleCategory.CORE_MANAGER,
        readiness = ModuleReadiness.READY,
        privacyNote = "Stored locally inside private app sandbox.",
      ),
      ModuleDescriptor(
        packageName = "utils/",
        title = "ErrorLogger & Compatibility",
        roleSummary = "Bounded diagnostic ring buffer + hardware capability fallback checker.",
        category = ModuleCategory.INFRASTRUCTURE,
        readiness = ModuleReadiness.READY,
        privacyNote = "Capped at 120 log items; strips sensitive audio payloads.",
      ),
      ModuleDescriptor(
        packageName = "service/",
        title = "Service Coordinator",
        roleSummary = "On-demand service lifecycle contract avoiding idle background battery drain.",
        category = ModuleCategory.INFRASTRUCTURE,
        readiness = ModuleReadiness.STANDBY,
        privacyNote = "No persistent background service spawned.",
      ),
      ModuleDescriptor(
        packageName = "camera/",
        title = "CameraManager",
        roleSummary = "User-triggered CameraX capture (Photo, Selfie, Video, QR) (${if (compat.hasCameraHardware) "Hardware present" else "No camera"}).",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when (cameraManager?.state?.value) {
            is CameraState.PreviewActive,
            is CameraState.RecordingVideo,
            is CameraState.Preparing, -> ModuleReadiness.ACTIVE_LISTENING
            is CameraState.UnsupportedFallback -> ModuleReadiness.FALLBACK_MODE
            else -> ModuleReadiness.READY
          },
        privacyNote = "Permission-gated; closes camera immediately after task completion.",
      ),
      ModuleDescriptor(
        packageName = "gallery/",
        title = "GalleryManager",
        roleSummary = "Zero-permission Photo Picker, photo search, OCR preparation, and safe deletion.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when (galleryManager?.state?.value) {
            is GalleryState.DisplayingMedia,
            is GalleryState.ReadyToPick,
            is GalleryState.OcrPrepared, -> ModuleReadiness.ACTIVE_LISTENING
            else -> ModuleReadiness.READY
          },
        privacyNote = "Zero background scanning; deletes only after explicit confirmation.",
      ),
      ModuleDescriptor(
        packageName = "files/",
        title = "FileManager",
        roleSummary = "User-requested SAF browsing, PDFReader, DocumentReader, ImageReader & on-device OCRManager.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when (fileManager?.state?.value) {
            is FileState.Reading,
            is FileState.AwaitingConfirmation,
            is FileState.BrowsingFiles, -> ModuleReadiness.ACTIVE_LISTENING
            else -> ModuleReadiness.READY
          },
        privacyNote = "SAF-only; never scans, uploads, modifies, or deletes files without confirmation.",
      ),
      ModuleDescriptor(
        packageName = "smart/",
        title = "SmartFeatureManager",
        roleSummary = "Offline Translation, Reminders, Notes, Calendar, Voice Notes & Mood-aware AI Suggestions.",
        category = ModuleCategory.VOICE_AND_COGNITION,
        readiness =
          when (smartController?.state?.value) {
            is SmartFeatureState.Working -> ModuleReadiness.ACTIVE_LISTENING
            is SmartFeatureState.PermissionRequired -> ModuleReadiness.STANDBY
            else -> ModuleReadiness.READY
          },
        privacyNote = "Connected to Emotion, Memory, Permission & TTS; zero cloud file uploads.",
      ),
      ModuleDescriptor(
        packageName = "update/",
        title = "AppUpdateManager",
        roleSummary = "Play-compliant HTTPS release manifest checker for future MAX updates.",
        category = ModuleCategory.INFRASTRUCTURE,
        readiness = ModuleReadiness.READY,
        privacyNote = "User-triggered only; zero dynamic code loading.",
      ),
      ModuleDescriptor(
        packageName = "internet/",
        title = "InternetManager",
        roleSummary = "User-requested Web, News, Weather, Wikipedia & YouTube search plus safe webpage summarization.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when {
            internetManager?.state?.value is InternetState.Loading -> ModuleReadiness.ACTIVE_LISTENING
            privacyController.policy.value.allowInternetOnUserAction -> ModuleReadiness.READY
            else -> ModuleReadiness.DORMANT_LOCKED
          },
        privacyNote = "HTTPS-only; never browses automatically or tracks history.",
      ),
      ModuleDescriptor(
        packageName = "phone/",
        title = "PhoneManager",
        roleSummary = "User-confirmed Calls, SMS, Contacts, Alarm, Timer, Stopwatch, Calculator & Flashlight (${if (compat.hasTelephonyHardware) "Telephony" else "Wi-Fi/Tablet"}). ",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when (phoneManager?.state?.value) {
            is PhoneState.AwaitingConfirmation -> ModuleReadiness.ACTIVE_LISTENING
            is PhoneState.ActionReady -> ModuleReadiness.READY
            else -> ModuleReadiness.READY
          },
        privacyNote = "Explicit confirmation & permissions; never reads call logs or SMS.",
      ),
      ModuleDescriptor(
        packageName = "apps/",
        title = "AppManager",
        roleSummary = "User-confirmed app launcher & safe alternatives for WhatsApp, Messenger, Facebook, Chrome, YouTube, Gmail & Maps.",
        category = ModuleCategory.CORE_MANAGER,
        readiness =
          when (appManager?.state?.value) {
            is AppState.AwaitingConfirmation -> ModuleReadiness.ACTIVE_LISTENING
            is AppState.AppNotInstalledWithAlternatives -> ModuleReadiness.FALLBACK_MODE
            else -> ModuleReadiness.READY
          },
        privacyNote = "Explicit confirmation required; zero background opening, auto-messaging, or auto-posting.",
      ),
    )
  }

  fun release() {
    cancelFollowUpWait()
    wakeWordManager.removeListener(this)
    wakeWordManager.release()
    cameraManager?.release()
    galleryManager?.release()
    internetManager?.release()
    phoneManager?.release()
    appManager?.release()
    fileManager?.release()
    smartFeatureManager?.release() ?: smartController?.release()
    automationManager?.release()
    floatingAssistantManager?.release()
    appUpdateManager?.release()
    speechManager.release()
    ttsManager.release()
    conversationEngine.release()
    aiBrainManager.release()
    memoryManager.release()
    serviceCoordinator.markStopped()
    scope.cancel()
  }

  companion object {
    private const val MODULE_NAME = "MAXCore"
    const val DEFAULT_FOLLOW_UP_TIMEOUT_MS = 8_000L
  }
}
