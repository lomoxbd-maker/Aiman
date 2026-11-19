package com.example

import android.app.Application
import com.example.apps.AndroidAppLaunchBackend
import com.example.apps.AppLauncher
import com.example.apps.AppManager
import com.example.apps.ChromeManager
import com.example.apps.FacebookManager
import com.example.apps.GmailManager
import com.example.apps.MapsManager
import com.example.apps.MessengerManager
import com.example.apps.WhatsAppManager
import com.example.apps.YouTubeManager
import com.example.automation.AutomationManager
import com.example.brain.AIBrainManager
import com.example.camera.CameraManager
import com.example.conversation.ConversationManager
import com.example.core.MAXCore
import com.example.emotion.EmotionDetector
import com.example.emotion.EmotionEngine
import com.example.emotion.EmotionManager
import com.example.emotion.PersonalityManager
import com.example.files.AndroidDocumentReaderBackend
import com.example.files.AndroidFileMetadataBackend
import com.example.files.AndroidImageReaderBackend
import com.example.files.AndroidPdfReaderBackend
import com.example.files.DocumentReader
import com.example.files.FileBrowser
import com.example.files.FileManager
import com.example.files.ImageReader
import com.example.files.OCRManager
import com.example.files.OnDeviceOcrBackend
import com.example.files.PDFReader
import com.example.floating.FloatingAssistantManager
import com.example.gallery.GalleryManager
import com.example.internet.BrowserManager
import com.example.internet.InternetManager
import com.example.internet.SearchManager
import com.example.internet.WebRequestManager
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.permissions.PermissionManager
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyController
import com.example.phone.AlarmManager
import com.example.phone.CalculatorManager
import com.example.phone.CallManager
import com.example.phone.ContactsManager
import com.example.phone.FlashlightManager
import com.example.phone.PhoneManager
import com.example.phone.SMSManager
import com.example.phone.StopwatchManager
import com.example.phone.TimerManager
import com.example.repository.MAXRepository
import com.example.repository.MAXRepositoryImpl
import com.example.service.FoundationServiceCoordinator
import com.example.settings.SettingsManager
import com.example.smart.AISuggestionManager
import com.example.smart.AndroidCalendarBackend
import com.example.smart.CalendarManager
import com.example.smart.NotesManager
import com.example.smart.ReminderManager
import com.example.smart.SmartFeatureManager
import com.example.smart.TranslationManager
import com.example.smart.VoiceNotesManager
import com.example.speech.SpeechManager
import com.example.tts.TTSManager
import com.example.update.AppUpdateManager
import com.example.utils.DeviceCompatibilityChecker
import com.example.utils.ErrorLogger
import com.example.wakeword.WakeWordManager

/**
 * Lightweight dependency container for the MAX modular architecture.
 * Uses explicit constructor injection without heavy DI code-generation overhead.
 */
class MAXApplication : Application() {

  lateinit var maxCore: MAXCore
    private set

  lateinit var repository: MAXRepository
    private set

  override fun onCreate() {
    super.onCreate()
    val errorLogger = ErrorLogger()
    val compatChecker = DeviceCompatibilityChecker(this)
    val compat = compatChecker.inspect()

    val settingsManager = SettingsManager(this, errorLogger)
    val permissionManager = PermissionManager(this, errorLogger)
    val privacyController = PrivacyController({ permissionManager }, errorLogger)
    val permissionRequestHandler =
      PermissionRequestHandler(permissionManager, privacyController, errorLogger)

    val speechManager = SpeechManager(errorLogger, context = this)
    val ttsManager = TTSManager(this, errorLogger)
    val memoryRepository = MemoryRepository(this, errorLogger)
    val memoryManager = MemoryManager(errorLogger, repository = memoryRepository)
    val wakeWordManager = WakeWordManager(errorLogger, privacyGuard = privacyController)
    val emotionManager = EmotionManager(EmotionDetector(), errorLogger)
    val emotionEngine = EmotionEngine(emotionManager)
    val personalityManager = PersonalityManager(emotionManager, memoryManager)
    val aiBrainManager =
      AIBrainManager(
        emotionManager = emotionManager,
        memoryController = memoryManager,
        personalityManager = personalityManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        ttsController = ttsManager,
        errorTracker = errorLogger,
      )
    val conversationManager =
      ConversationManager(
        emotionManager = emotionManager,
        errorTracker = errorLogger,
        memoryController = memoryManager,
        personalityManager = personalityManager,
      ).apply {
        setBrainManager(aiBrainManager)
      }
    val serviceCoordinator = FoundationServiceCoordinator()
    val cameraManager =
      CameraManager(
        context = this,
        permissionController = permissionManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        errorTracker = errorLogger,
        isLowRamDevice = compat.isLowRamDevice,
      )
    val galleryManager =
      GalleryManager(
        context = this,
        permissionController = permissionManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        errorTracker = errorLogger,
      )
    val webRequestManager =
      WebRequestManager(
        context = this,
        errorTracker = errorLogger,
        isLowRamDevice = compat.isLowRamDevice,
      )
    val searchManager = SearchManager(webRequestManager, errorLogger)
    val browserManager = BrowserManager(this, webRequestManager, errorLogger)
    val internetManager =
      InternetManager(
        permissionController = permissionManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        webRequestManager = webRequestManager,
        searchManager = searchManager,
        browserManager = browserManager,
        errorTracker = errorLogger,
      )
    val phoneManager =
      PhoneManager(
        permissionController = permissionManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        callManager = CallManager(this, errorLogger),
        smsManager = SMSManager(this, errorLogger),
        contactsManager = ContactsManager(this, errorLogger),
        alarmManager = AlarmManager(this, errorLogger),
        timerManager = TimerManager(errorLogger),
        stopwatchManager = StopwatchManager(errorLogger),
        calculatorManager = CalculatorManager(errorLogger),
        flashlightManager = FlashlightManager(this, errorLogger),
        errorTracker = errorLogger,
      )
    val appLauncher = AppLauncher(AndroidAppLaunchBackend(this), errorLogger)
    val appManager =
      AppManager(
        permissionController = permissionManager,
        privacyGuard = privacyController,
        appLauncher = appLauncher,
        whatsAppManager = WhatsAppManager(appLauncher, errorLogger),
        messengerManager = MessengerManager(appLauncher, errorLogger),
        facebookManager = FacebookManager(appLauncher, errorLogger),
        chromeManager = ChromeManager(appLauncher, errorLogger),
        youtubeManager = YouTubeManager(appLauncher, errorLogger),
        gmailManager = GmailManager(appLauncher, errorLogger),
        mapsManager = MapsManager(appLauncher, errorLogger),
        errorTracker = errorLogger,
      )
    val fileBrowser = FileBrowser(AndroidFileMetadataBackend(this), errorLogger)
    val pdfReader = PDFReader(AndroidPdfReaderBackend(this), errorLogger, compat.isLowRamDevice)
    val documentReader = DocumentReader(AndroidDocumentReaderBackend(this), errorLogger, compat.isLowRamDevice)
    val imageReader = ImageReader(AndroidImageReaderBackend(this), errorLogger)
    val ocrManager = OCRManager(OnDeviceOcrBackend(this, compat.isLowRamDevice), errorLogger)
    val fileManager =
      FileManager(
        permissionController = permissionManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        fileBrowser = fileBrowser,
        pdfReader = pdfReader,
        documentReader = documentReader,
        imageReader = imageReader,
        ocrManager = ocrManager,
        errorTracker = errorLogger,
      )
    val translationManager = TranslationManager(errorLogger)
    val reminderManager =
      ReminderManager(
        context = this,
        permissionController = permissionManager,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        errorTracker = errorLogger,
      )
    val notesManager = NotesManager(memoryManager, errorLogger)
    val calendarManager =
      CalendarManager(
        context = this,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        backend = AndroidCalendarBackend(this),
        reminderManager = reminderManager,
        errorTracker = errorLogger,
      )
    val voiceNotesManager = VoiceNotesManager(errorLogger)
    val aiSuggestionManager =
      AISuggestionManager(
        emotionManager = emotionManager,
        memoryController = memoryManager,
        reminderManager = reminderManager,
        notesManager = notesManager,
        calendarManager = calendarManager,
        errorTracker = errorLogger,
      )
    val smartFeatureManager =
      SmartFeatureManager(
        translationManager = translationManager,
        reminderManager = reminderManager,
        notesManager = notesManager,
        calendarManager = calendarManager,
        voiceNotesManager = voiceNotesManager,
        aiSuggestionManager = aiSuggestionManager,
        errorTracker = errorLogger,
      )
    val appUpdateManager =
      AppUpdateManager(
        context = this,
        privacyGuard = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        errorTracker = errorLogger,
      )
    val automationManager =
      AutomationManager(
        reminderManager = reminderManager,
        notesManager = notesManager,
        memoryController = memoryManager,
        ttsController = ttsManager,
        errorTracker = errorLogger,
      )
    val floatingAssistantManager =
      FloatingAssistantManager(
        context = this,
        errorTracker = errorLogger,
      )

    maxCore =
      MAXCore(
        settingsManager = settingsManager,
        permissionManager = permissionManager,
        privacyController = privacyController,
        permissionRequestHandler = permissionRequestHandler,
        speechManager = speechManager,
        ttsManager = ttsManager,
        memoryManager = memoryManager,
        memoryController = memoryManager,
        wakeWordManager = wakeWordManager,
        errorLogger = errorLogger,
        conversationEngine = conversationManager,
        emotionEngine = emotionEngine,
        emotionManager = emotionManager,
        personalityManager = personalityManager,
        serviceCoordinator = serviceCoordinator,
        compatibilityChecker = compatChecker,
        cameraCapability = cameraManager,
        galleryCapability = galleryManager,
        networkCapability = internetManager,
        phoneCapability = phoneManager,
        appLauncherCapability = appManager,
        fileCapability = fileManager,
        cameraManager = cameraManager,
        galleryManager = galleryManager,
        internetManager = internetManager,
        phoneManager = phoneManager,
        appManager = appManager,
        fileManager = fileManager,
        smartFeatureManager = smartFeatureManager,
        smartController = smartFeatureManager,
        appUpdateManager = appUpdateManager,
        brainManager = aiBrainManager,
        automationManager = automationManager,
        floatingAssistantManager = floatingAssistantManager,
      )
    repository = MAXRepositoryImpl(maxCore)

    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      runCatching {
        errorLogger.logError("MAXApplication", "Unhandled exception on ${thread.name}", throwable)
        maxCore.releaseTransientTaskResources()
        maxCore.handleBackgroundLifecycle(inBackground = true)
      }
      previousHandler?.uncaughtException(thread, throwable)
    }
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_UI_HIDDEN && ::maxCore.isInitialized) {
      runCatching {
        coil.Coil.imageLoader(this).memoryCache?.clear()
        maxCore.releaseTransientTaskResources()
      }
    }
  }

  override fun onTerminate() {
    if (::maxCore.isInitialized) {
      maxCore.release()
    }
    super.onTerminate()
  }
}
