package com.example.repository

import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.example.apps.AppAlternative
import com.example.apps.AppAvailability
import com.example.apps.AppController
import com.example.apps.AppState
import com.example.apps.PendingAppConfirmation
import com.example.apps.SupportedApp
import com.example.camera.CameraController
import com.example.camera.CameraLens
import com.example.camera.CameraManager
import com.example.camera.CameraMode
import com.example.camera.CameraState
import com.example.camera.CapturedMediaResult
import com.example.conversation.ConversationTurn
import com.example.core.CoreLifecycleState
import com.example.core.MAXCore
import com.example.core.ModuleDescriptor
import com.example.emotion.AffectiveTone
import com.example.emotion.EmotionState
import com.example.emotion.EmotionType
import com.example.files.ExtractedDocumentContent
import com.example.files.FileController
import com.example.files.FileKind
import com.example.files.FileState
import com.example.files.MAXFileItem
import com.example.files.OcrResult
import com.example.files.PendingFileConfirmation
import com.example.gallery.GalleryController
import com.example.gallery.GalleryFilter
import com.example.gallery.GalleryManager
import com.example.gallery.GalleryMediaItem
import com.example.gallery.GalleryState
import com.example.gallery.ImageSearchResult
import com.example.gallery.OcrPreparationResult
import com.example.gallery.PendingDeleteConfirmation
import com.example.internet.ConnectionQuality
import com.example.internet.InternetController
import com.example.internet.InternetState
import com.example.internet.SearchCategory
import com.example.internet.WebPageContent
import com.example.internet.WebSearchResultItem
import com.example.memory.MemoryActionResult
import com.example.memory.MemoryEngineState
import com.example.memory.MemoryEntry
import com.example.memory.MemoryModel
import com.example.memory.MemorySettings
import com.example.memory.MemoryType
import com.example.permissions.AppPermission
import com.example.permissions.PendingPermissionRequest
import com.example.permissions.PermissionSnapshot
import com.example.permissions.PrivacyPolicy
import com.example.phone.AlarmEntry
import com.example.phone.CalculationRecord
import com.example.phone.PendingPhoneConfirmation
import com.example.phone.PhoneContact
import com.example.phone.PhoneController
import com.example.phone.PhoneState
import com.example.phone.StopwatchStatus
import com.example.phone.TimerStatus
import com.example.settings.MAXSettings
import com.example.smart.AISuggestionItem
import com.example.smart.CalendarEventItem
import com.example.smart.ReminderItem
import com.example.smart.SmartFeatureState
import com.example.smart.SmartFeaturesController
import com.example.smart.SmartNoteItem
import com.example.smart.TranslationDirection
import com.example.smart.TranslationRecord
import com.example.smart.VoiceNoteItem
import com.example.speech.SpeechState
import com.example.tts.SpeechStyle
import com.example.tts.TTSState
import com.example.tts.VoiceConfig
import com.example.tts.VoiceState
import com.example.utils.DeviceCompatibilityProfile
import com.example.utils.LogEntry
import com.example.wakeword.WakeWordState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository boundary abstracting [MAXCore] and [com.example.settings.SettingsManager]
 * for the MVVM presentation layer.
 */
interface MAXRepository {
  val coreState: StateFlow<CoreLifecycleState>
  val wakeWordState: StateFlow<WakeWordState>
  val wakeWordDetections: StateFlow<Int>
  val speechState: StateFlow<SpeechState>
  val ttsState: StateFlow<TTSState>
  val voiceState: StateFlow<VoiceState>
  val voiceConfig: StateFlow<VoiceConfig>
  val memoryState: StateFlow<MemoryEngineState>
  val memoryEntries: StateFlow<List<MemoryEntry>>
  val structuredMemories: StateFlow<List<MemoryModel>>
  val memorySettings: StateFlow<MemorySettings>
  val affectiveTone: StateFlow<AffectiveTone>
  val emotionState: StateFlow<EmotionState>
  val conversationTurns: StateFlow<List<ConversationTurn>>
  val permissionSnapshots: StateFlow<List<PermissionSnapshot>>
  val privacyPolicy: StateFlow<PrivacyPolicy>
  val pendingPermissionRequest: StateFlow<PendingPermissionRequest?>
  val cameraState: StateFlow<CameraState>
  val cameraMode: StateFlow<CameraMode>
  val cameraLens: StateFlow<CameraLens>
  val flashEnabled: StateFlow<Boolean>
  val lastCapturedMedia: StateFlow<CapturedMediaResult?>
  val lastScannedQr: StateFlow<String?>
  val galleryState: StateFlow<GalleryState>
  val galleryItems: StateFlow<List<GalleryMediaItem>>
  val activeGalleryItem: StateFlow<GalleryMediaItem?>
  val gallerySearchQuery: StateFlow<String>
  val galleryFilter: StateFlow<GalleryFilter>
  val pendingDeleteConfirmation: StateFlow<PendingDeleteConfirmation?>
  val lastOcrResult: StateFlow<OcrPreparationResult?>
  val lastSearchResult: StateFlow<ImageSearchResult?>
  val internetState: StateFlow<InternetState>
  val connectionQuality: StateFlow<ConnectionQuality>
  val searchCategory: StateFlow<SearchCategory>
  val searchResults: StateFlow<List<WebSearchResultItem>>
  val currentWebPage: StateFlow<WebPageContent?>
  val lastWebSummary: StateFlow<String?>
  val phoneState: StateFlow<PhoneState>
  val phoneContacts: StateFlow<List<PhoneContact>>
  val scheduledAlarms: StateFlow<List<AlarmEntry>>
  val timerStatus: StateFlow<TimerStatus>
  val stopwatchStatus: StateFlow<StopwatchStatus>
  val calculationHistory: StateFlow<List<CalculationRecord>>
  val isTorchOn: StateFlow<Boolean>
  val pendingPhoneConfirmation: StateFlow<PendingPhoneConfirmation?>
  val appState: StateFlow<AppState>
  val appAvailabilities: StateFlow<Map<SupportedApp, AppAvailability>>
  val pendingAppConfirmation: StateFlow<PendingAppConfirmation?>
  val fileState: StateFlow<FileState>
  val sessionFiles: StateFlow<List<MAXFileItem>>
  val activeFileItem: StateFlow<MAXFileItem?>
  val activeFileContent: StateFlow<ExtractedDocumentContent?>
  val lastFileOcrResult: StateFlow<OcrResult?>
  val pendingFileConfirmation: StateFlow<PendingFileConfirmation?>
  val smartState: StateFlow<SmartFeatureState>
  val translationHistory: StateFlow<List<TranslationRecord>>
  val lastTranslation: StateFlow<TranslationRecord?>
  val reminders: StateFlow<List<ReminderItem>>
  val smartNotes: StateFlow<List<SmartNoteItem>>
  val calendarEvents: StateFlow<List<CalendarEventItem>>
  val calendarSummary: StateFlow<String>
  val voiceNotes: StateFlow<List<VoiceNoteItem>>
  val aiSuggestions: StateFlow<List<AISuggestionItem>>
  val logs: StateFlow<List<LogEntry>>
  val compatibilityProfile: StateFlow<DeviceCompatibilityProfile>
  val lastWakeWordEvent: StateFlow<String?>
  val followUpPrompt: StateFlow<String?>
  val lastCompletedTask: StateFlow<String?>
  val isThinking: StateFlow<Boolean>
  val updateState: StateFlow<com.example.update.UpdateState>
  val brainState: StateFlow<com.example.brain.BrainLifecycleState>
  val routines: StateFlow<List<com.example.automation.AutomationRoutine>>
  val automationState: StateFlow<com.example.automation.AutomationState>
  val lastBriefing: StateFlow<com.example.automation.DailyBriefingSummary?>
  val floatingState: StateFlow<com.example.floating.FloatingState>
  val settingsFlow: Flow<MAXSettings>

  fun initializeCore()

  fun activateManualVoiceSession()

  fun finishFollowUpToWakeMode()

  fun handleBackgroundLifecycle(inBackground: Boolean)

  fun checkForUpdates()

  fun openVerifiedUpdateUrl(url: String): Boolean

  fun runRoutine(category: com.example.automation.RoutineCategory): String

  fun toggleRoutineEnabled(routineId: String)

  fun activateFloatingBubble(preferSystemOverlay: Boolean = false): Boolean

  fun minimizeFloatingBubble()

  fun expandFloatingBubble(isVoiceArmed: Boolean = false)

  fun triggerFloatingQuickVoice(): Boolean

  fun closeFloatingBubble()

  fun requestOverlayPermissionSettings(): Boolean

  fun armWakeWordStandby()

  fun disarmWakeWordStandby()

  fun simulateWakeWordMax()

  fun returnToStandby()

  fun requestWakeWordPermissionWithExplanation()

  fun dismissPendingPermissionRequest()

  fun onPermissionPromptCompleted(permission: AppPermission, granted: Boolean)

  fun setPermissionAllowedByUser(permission: AppPermission, allowed: Boolean)

  fun openCamera(mode: CameraMode): Boolean

  fun bindCameraXSession(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)

  fun capturePhoto()

  fun startVideoRecording()

  fun stopVideoRecording()

  fun switchCameraLens()

  fun toggleCameraFlash()

  fun closeCamera()

  fun openGallery(): Boolean

  fun onGalleryMediaPicked(uris: List<Uri>)

  fun selectGalleryItem(itemId: String)

  fun findGalleryPhotos(query: String, filter: GalleryFilter)

  fun prepareGalleryOcr(itemId: String)

  fun requestDeleteGalleryItem(itemId: String)

  fun confirmDeleteGalleryItem()

  fun cancelDeleteGalleryItem()

  fun closeGallery()

  fun refreshConnectionQuality(): ConnectionQuality

  fun searchInternet(query: String, category: SearchCategory): Boolean

  fun openWebsite(url: String): Boolean

  fun summarizeWebContent(topicOrUrl: String? = null): Boolean

  fun openExternalVerifiedUrl(url: String): Boolean

  fun cancelInternetRequest()

  fun closeBrowserSession()

  fun requestPhoneCall(phoneNumberOrName: String, recipientName: String? = null, preferDirectCall: Boolean = false): Boolean

  fun requestSms(phoneNumberOrName: String, messageBody: String, recipientName: String? = null): Boolean

  fun searchPhoneContacts(query: String): Boolean

  fun onSystemContactPicked(uri: Uri): PhoneContact?

  fun requestAddOrEditContact(name: String, phoneNumber: String, email: String? = null): Boolean

  fun requestSetAlarm(hour: Int, minute: Int, label: String): Boolean

  fun openSystemAlarms(): Boolean

  fun startTimer(durationSeconds: Int, label: String = "MAX Timer"): Boolean

  fun pauseTimer()

  fun resumeTimer()

  fun resetTimer()

  fun startStopwatch(): Boolean

  fun pauseStopwatch()

  fun lapStopwatch()

  fun resetStopwatch()

  fun calculateMath(expression: String): CalculationRecord?

  fun requestToggleFlashlight(enable: Boolean, requireConfirmation: Boolean = true): Boolean

  fun confirmPendingPhoneAction(): Boolean

  fun cancelPendingPhoneAction()

  fun releasePhoneTaskResources()

  fun refreshAppAvailability()

  fun requestOpenApp(app: SupportedApp, requireConfirmation: Boolean = true): Boolean

  fun requestOpenAppAlternative(app: SupportedApp, alternative: AppAlternative): Boolean

  fun requestWhatsAppDraft(phoneDigits: String, draftText: String): Boolean

  fun requestGmailDraft(recipientEmail: String, subject: String, body: String): Boolean

  fun requestMapsSearch(query: String): Boolean

  fun requestChromeUrl(url: String): Boolean

  fun requestYouTubeSearch(query: String): Boolean

  fun confirmPendingAppAction(): Boolean

  fun cancelPendingAppAction()

  fun resetAppControlState()

  fun requestOpenFilePicker(): Boolean

  fun onFilesPicked(uris: List<Uri>)

  fun filterSessionFiles(query: String, kindFilter: FileKind? = null)

  fun openAndReadFile(itemId: String): Boolean

  fun runOcrOnFile(itemId: String): OcrResult?

  fun requestDeleteFileItem(itemId: String)

  fun confirmPendingFileAction(): Boolean

  fun cancelPendingFileAction()

  fun closeFilesSession()

  fun translateSmartText(text: String, direction: TranslationDirection? = null): Boolean

  fun scheduleReminder(title: String, triggerAtMillis: Long): Boolean

  fun toggleReminderCompleted(id: String)

  fun deleteReminder(id: String)

  fun addSmartNote(title: String, content: String, rememberInMemory: Boolean): Boolean

  fun deleteSmartNote(id: String)

  fun loadTodayCalendar(): Boolean

  fun openSystemCalendarInsert(title: String): Boolean

  fun saveVoiceNoteTranscript(title: String, transcript: String): Boolean

  fun deleteVoiceNote(id: String)

  fun refreshAiSuggestions(): List<AISuggestionItem>

  fun processConversationalMessage(userText: String, speakAloud: Boolean = true): String

  fun setEmotion(emotion: EmotionType)

  fun speakText(text: String, style: SpeechStyle? = null)

  fun pauseSpeaking()

  fun resumeSpeaking()

  fun stopSpeaking()

  fun refreshPermissions()

  fun getModuleTopology(): List<ModuleDescriptor>

  fun addStructuredMemory(type: MemoryType, content: String): MemoryActionResult

  fun deleteMemoryById(id: String)

  fun clearMemories()

  fun updateMemorySettings(settings: MemorySettings)

  fun logDiagnosticPing()

  fun clearLogs()

  suspend fun syncAndSaveSettings(transform: suspend () -> Unit, updated: MAXSettings)
}

class MAXRepositoryImpl(
  private val maxCore: MAXCore,
) : MAXRepository {

  override val coreState: StateFlow<CoreLifecycleState> = maxCore.coreState
  override val wakeWordState: StateFlow<WakeWordState> = maxCore.wakeWordManager.state
  override val wakeWordDetections: StateFlow<Int> = maxCore.wakeWordManager.detectionCount
  override val speechState: StateFlow<SpeechState> = maxCore.speechManager.state
  override val ttsState: StateFlow<TTSState> = maxCore.ttsManager.state
  override val voiceState: StateFlow<VoiceState> = maxCore.ttsManager.voiceState
  override val voiceConfig: StateFlow<VoiceConfig> = maxCore.ttsManager.voiceConfig
  override val memoryState: StateFlow<MemoryEngineState> = maxCore.memoryManager.state
  override val memoryEntries: StateFlow<List<MemoryEntry>> = maxCore.memoryManager.entries
  override val structuredMemories: StateFlow<List<MemoryModel>> = maxCore.memoryController.memories
  override val memorySettings: StateFlow<MemorySettings> = maxCore.memoryController.memorySettings
  override val affectiveTone: StateFlow<AffectiveTone> = maxCore.emotionEngine.currentTone
  override val emotionState: StateFlow<EmotionState> = maxCore.emotionManager.emotionState
  override val conversationTurns: StateFlow<List<ConversationTurn>> =
    maxCore.conversationEngine.recentTurns
  override val permissionSnapshots: StateFlow<List<PermissionSnapshot>> =
    maxCore.permissionManager.snapshots
  override val privacyPolicy: StateFlow<PrivacyPolicy> = maxCore.privacyController.policy
  override val pendingPermissionRequest: StateFlow<PendingPermissionRequest?> =
    maxCore.permissionRequestHandler.pendingRequest

  private val camController: CameraController? = maxCore.cameraManager
  private val galController: GalleryController? = maxCore.galleryManager
  private val netController: InternetController? = maxCore.internetManager
  private val phoneController: PhoneController? = maxCore.phoneManager
  private val appController: AppController? = maxCore.appManager
  private val fileController: FileController? = maxCore.fileManager
  private val smartController: SmartFeaturesController? = maxCore.smartController

  override val cameraState: StateFlow<CameraState> =
    camController?.state ?: MutableStateFlow(CameraState.Dormant)
  override val cameraMode: StateFlow<CameraMode> =
    camController?.activeMode ?: MutableStateFlow(CameraMode.PHOTO)
  override val cameraLens: StateFlow<CameraLens> =
    camController?.activeLens ?: MutableStateFlow(CameraLens.BACK)
  override val flashEnabled: StateFlow<Boolean> =
    camController?.flashEnabled ?: MutableStateFlow(false)
  override val lastCapturedMedia: StateFlow<CapturedMediaResult?> =
    camController?.lastCapturedMedia ?: MutableStateFlow(null)
  override val lastScannedQr: StateFlow<String?> =
    camController?.lastScannedQr ?: MutableStateFlow(null)

  override val galleryState: StateFlow<GalleryState> =
    galController?.state ?: MutableStateFlow(GalleryState.Dormant)
  override val galleryItems: StateFlow<List<GalleryMediaItem>> =
    galController?.filteredItems ?: MutableStateFlow(emptyList())
  override val activeGalleryItem: StateFlow<GalleryMediaItem?> =
    galController?.activeItem ?: MutableStateFlow(null)
  override val gallerySearchQuery: StateFlow<String> =
    galController?.searchQuery ?: MutableStateFlow("")
  override val galleryFilter: StateFlow<GalleryFilter> =
    galController?.activeFilter ?: MutableStateFlow(GalleryFilter.ALL)
  override val pendingDeleteConfirmation: StateFlow<PendingDeleteConfirmation?> =
    galController?.pendingDeleteConfirmation ?: MutableStateFlow(null)
  override val lastOcrResult: StateFlow<OcrPreparationResult?> =
    galController?.lastOcrResult ?: MutableStateFlow(null)
  override val lastSearchResult: StateFlow<ImageSearchResult?> =
    galController?.lastSearchResult ?: MutableStateFlow(null)

  override val internetState: StateFlow<InternetState> =
    netController?.state ?: MutableStateFlow(InternetState.Dormant)
  override val connectionQuality: StateFlow<ConnectionQuality> =
    netController?.connectionQuality ?: MutableStateFlow(ConnectionQuality.OFFLINE)
  override val searchCategory: StateFlow<SearchCategory> =
    netController?.activeCategory ?: MutableStateFlow(SearchCategory.WEB)
  override val searchResults: StateFlow<List<WebSearchResultItem>> =
    netController?.searchResults ?: MutableStateFlow(emptyList())
  override val currentWebPage: StateFlow<WebPageContent?> =
    netController?.currentPage ?: MutableStateFlow(null)
  override val lastWebSummary: StateFlow<String?> =
    netController?.lastSummary ?: MutableStateFlow(null)

  override val phoneState: StateFlow<PhoneState> =
    phoneController?.state ?: MutableStateFlow(PhoneState.Dormant)
  override val phoneContacts: StateFlow<List<PhoneContact>> =
    phoneController?.contacts ?: MutableStateFlow(emptyList())
  override val scheduledAlarms: StateFlow<List<AlarmEntry>> =
    phoneController?.scheduledAlarms ?: MutableStateFlow(emptyList())
  override val timerStatus: StateFlow<TimerStatus> =
    phoneController?.timerStatus ?: MutableStateFlow(TimerStatus())
  override val stopwatchStatus: StateFlow<StopwatchStatus> =
    phoneController?.stopwatchStatus ?: MutableStateFlow(StopwatchStatus())
  override val calculationHistory: StateFlow<List<CalculationRecord>> =
    phoneController?.calculationHistory ?: MutableStateFlow(emptyList())
  override val isTorchOn: StateFlow<Boolean> =
    phoneController?.isTorchOn ?: MutableStateFlow(false)
  override val pendingPhoneConfirmation: StateFlow<PendingPhoneConfirmation?> =
    phoneController?.pendingConfirmation ?: MutableStateFlow(null)

  override val appState: StateFlow<AppState> =
    appController?.state ?: MutableStateFlow(AppState.Dormant)
  override val appAvailabilities: StateFlow<Map<SupportedApp, AppAvailability>> =
    appController?.appAvailabilities ?: MutableStateFlow(emptyMap())
  override val pendingAppConfirmation: StateFlow<PendingAppConfirmation?> =
    appController?.pendingConfirmation ?: MutableStateFlow(null)

  override val fileState: StateFlow<FileState> =
    fileController?.state ?: MutableStateFlow(FileState.Dormant)
  override val sessionFiles: StateFlow<List<MAXFileItem>> =
    fileController?.filteredFiles ?: MutableStateFlow(emptyList())
  override val activeFileItem: StateFlow<MAXFileItem?> =
    fileController?.activeFile ?: MutableStateFlow(null)
  override val activeFileContent: StateFlow<ExtractedDocumentContent?> =
    fileController?.activeContent ?: MutableStateFlow(null)
  override val lastFileOcrResult: StateFlow<OcrResult?> =
    fileController?.lastOcrResult ?: MutableStateFlow(null)
  override val pendingFileConfirmation: StateFlow<PendingFileConfirmation?> =
    fileController?.pendingConfirmation ?: MutableStateFlow(null)

  override val smartState: StateFlow<SmartFeatureState> =
    smartController?.state ?: MutableStateFlow(SmartFeatureState.Idle)
  override val translationHistory: StateFlow<List<TranslationRecord>> =
    smartController?.translationManager?.history ?: MutableStateFlow(emptyList())
  override val lastTranslation: StateFlow<TranslationRecord?> =
    smartController?.translationManager?.lastRecord ?: MutableStateFlow(null)
  override val reminders: StateFlow<List<ReminderItem>> =
    smartController?.reminderManager?.reminders ?: MutableStateFlow(emptyList())
  override val smartNotes: StateFlow<List<SmartNoteItem>> =
    smartController?.notesManager?.notes ?: MutableStateFlow(emptyList())
  override val calendarEvents: StateFlow<List<CalendarEventItem>> =
    smartController?.calendarManager?.events ?: MutableStateFlow(emptyList())
  override val calendarSummary: StateFlow<String> =
    smartController?.calendarManager?.lastSummary ?: MutableStateFlow("")
  override val voiceNotes: StateFlow<List<VoiceNoteItem>> =
    smartController?.voiceNotesManager?.voiceNotes ?: MutableStateFlow(emptyList())
  override val aiSuggestions: StateFlow<List<AISuggestionItem>> =
    smartController?.aiSuggestionManager?.suggestions ?: MutableStateFlow(emptyList())

  override val logs: StateFlow<List<LogEntry>> = maxCore.errorLogger.logs
  override val compatibilityProfile: StateFlow<DeviceCompatibilityProfile> =
    maxCore.compatibilityProfile
  override val lastWakeWordEvent: StateFlow<String?> = maxCore.lastWakeWordEvent
  override val followUpPrompt: StateFlow<String?> = maxCore.followUpPrompt
  override val lastCompletedTask: StateFlow<String?> = maxCore.lastCompletedTask
  override val isThinking: StateFlow<Boolean> = maxCore.isThinking
  override val updateState: StateFlow<com.example.update.UpdateState> =
    maxCore.appUpdateManager?.state ?: MutableStateFlow(com.example.update.UpdateState.Idle("1.0", 1))
  override val brainState: StateFlow<com.example.brain.BrainLifecycleState> =
    maxCore.aiBrainManager.brainState
  override val routines: StateFlow<List<com.example.automation.AutomationRoutine>> =
    maxCore.automationManager?.routines ?: MutableStateFlow(emptyList())
  override val automationState: StateFlow<com.example.automation.AutomationState> =
    maxCore.automationManager?.state ?: MutableStateFlow(com.example.automation.AutomationState.Idle)
  override val lastBriefing: StateFlow<com.example.automation.DailyBriefingSummary?> =
    maxCore.automationManager?.lastBriefing ?: MutableStateFlow(null)
  override val floatingState: StateFlow<com.example.floating.FloatingState> =
    maxCore.floatingAssistantManager?.state ?: MutableStateFlow(com.example.floating.FloatingState.Inactive)
  override val settingsFlow: Flow<MAXSettings> = maxCore.settingsManager.settingsFlow

  override fun initializeCore() {
    maxCore.initialize()
  }

  override fun activateManualVoiceSession() {
    maxCore.activateManualVoiceSession()
  }

  override fun finishFollowUpToWakeMode() {
    maxCore.finishFollowUpAndReturnToWakeMode()
  }

  override fun handleBackgroundLifecycle(inBackground: Boolean) {
    maxCore.handleBackgroundLifecycle(inBackground)
  }

  override fun checkForUpdates() {
    val preferBangla = maxCore.ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    maxCore.appUpdateManager?.checkForUpdates(initiatedByUser = true, preferBangla = preferBangla)
  }

  override fun openVerifiedUpdateUrl(url: String): Boolean {
    return maxCore.appUpdateManager?.openVerifiedReleasePage(url) ?: false
  }

  override fun runRoutine(category: com.example.automation.RoutineCategory): String {
    val preferBangla = maxCore.ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return maxCore.automationManager?.runRoutineByCategory(
      category = category,
      initiatedByUser = true,
      preferBangla = preferBangla,
      speakAloud = true,
    ).orEmpty()
  }

  override fun toggleRoutineEnabled(routineId: String) {
    maxCore.automationManager?.toggleRoutineEnabled(routineId)
  }

  override fun activateFloatingBubble(preferSystemOverlay: Boolean): Boolean {
    return maxCore.floatingAssistantManager?.activateFloatingAssistant(
      initiatedByUser = true,
      preferSystemOverlay = preferSystemOverlay,
    ) ?: false
  }

  override fun minimizeFloatingBubble() {
    maxCore.floatingAssistantManager?.minimizeBubble()
  }

  override fun expandFloatingBubble(isVoiceArmed: Boolean) {
    maxCore.floatingAssistantManager?.expandBubble(isVoiceArmed)
  }

  override fun triggerFloatingQuickVoice(): Boolean {
    val ok = maxCore.floatingAssistantManager?.triggerQuickVoiceActivation() ?: false
    if (ok) maxCore.activateManualVoiceSession()
    return ok
  }

  override fun closeFloatingBubble() {
    maxCore.floatingAssistantManager?.closeFloatingAssistant()
  }

  override fun requestOverlayPermissionSettings(): Boolean {
    return maxCore.floatingAssistantManager?.requestOverlayPermissionSettings() ?: false
  }

  override fun armWakeWordStandby() {
    maxCore.armWakeWordStandby()
  }

  override fun disarmWakeWordStandby() {
    maxCore.disarmWakeWordStandby()
  }

  override fun simulateWakeWordMax() {
    maxCore.wakeWordManager.simulateWakeWordDetection()
  }

  override fun returnToStandby() {
    maxCore.returnToStandby()
  }

  override fun requestWakeWordPermissionWithExplanation() {
    maxCore.requestWakeWordPermissionWithExplanation()
  }

  override fun dismissPendingPermissionRequest() {
    maxCore.permissionRequestHandler.dismissPendingRequest()
  }

  override fun onPermissionPromptCompleted(permission: AppPermission, granted: Boolean) {
    maxCore.permissionRequestHandler.onSystemPermissionResult(permission, granted)
    maxCore.permissionManager.refreshAll(maxCore.privacyController.policy.value)
    if (permission == AppPermission.MICROPHONE_WAKE_WORD && granted) {
      maxCore.armWakeWordStandby()
    }
  }

  override fun setPermissionAllowedByUser(permission: AppPermission, allowed: Boolean) {
    maxCore.setPermissionAllowedByUser(permission, allowed)
  }

  override fun openCamera(mode: CameraMode): Boolean {
    return maxCore.openCamera(mode, initiatedByUser = true)
  }

  override fun bindCameraXSession(
    lifecycleOwner: LifecycleOwner,
    surfaceProvider: Preview.SurfaceProvider,
  ) {
    (camController as? CameraManager)?.bindCameraXSession(lifecycleOwner, surfaceProvider)
  }

  override fun capturePhoto() {
    (camController as? CameraManager)?.capturePhotoNow()
  }

  override fun startVideoRecording() {
    (camController as? CameraManager)?.startVideoRecording()
  }

  override fun stopVideoRecording() {
    (camController as? CameraManager)?.stopVideoRecording()
  }

  override fun switchCameraLens() {
    camController?.switchLens()
  }

  override fun toggleCameraFlash() {
    camController?.toggleFlash()
  }

  override fun closeCamera() {
    maxCore.closeCamera("Closed by user")
  }

  override fun openGallery(): Boolean {
    return maxCore.openGallery(initiatedByUser = true)
  }

  override fun onGalleryMediaPicked(uris: List<Uri>) {
    maxCore.onGalleryMediaPicked(uris)
  }

  override fun selectGalleryItem(itemId: String) {
    galController?.selectMediaItem(itemId)
  }

  override fun findGalleryPhotos(query: String, filter: GalleryFilter) {
    maxCore.findGalleryPhotos(query, filter)
  }

  override fun prepareGalleryOcr(itemId: String) {
    maxCore.prepareGalleryOcr(itemId)
  }

  override fun requestDeleteGalleryItem(itemId: String) {
    maxCore.requestDeleteGalleryItem(itemId)
  }

  override fun confirmDeleteGalleryItem() {
    maxCore.confirmDeleteGalleryItem()
  }

  override fun cancelDeleteGalleryItem() {
    maxCore.cancelDeleteGalleryItem()
  }

  override fun closeGallery() {
    galController?.closeGallery()
  }

  override fun refreshConnectionQuality(): ConnectionQuality {
    return netController?.refreshConnectionQuality() ?: ConnectionQuality.OFFLINE
  }

  override fun searchInternet(query: String, category: SearchCategory): Boolean {
    return maxCore.searchInternet(query, category, initiatedByUser = true)
  }

  override fun openWebsite(url: String): Boolean {
    return maxCore.openWebsite(url, initiatedByUser = true)
  }

  override fun summarizeWebContent(topicOrUrl: String?): Boolean {
    return maxCore.summarizeWebContent(topicOrUrl, initiatedByUser = true)
  }

  override fun openExternalVerifiedUrl(url: String): Boolean {
    return maxCore.openExternalVerifiedUrl(url, initiatedByUser = true)
  }

  override fun cancelInternetRequest() {
    maxCore.cancelInternetRequest()
  }

  override fun closeBrowserSession() {
    maxCore.closeBrowserSession()
  }

  override fun requestPhoneCall(
    phoneNumberOrName: String,
    recipientName: String?,
    preferDirectCall: Boolean,
  ): Boolean {
    val preferBangla = maxCore.ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return phoneController?.requestCall(
      phoneNumberOrName = phoneNumberOrName,
      recipientName = recipientName,
      preferDirectCall = preferDirectCall,
      initiatedByUser = true,
      preferBangla = preferBangla,
    ) ?: false
  }

  override fun requestSms(
    phoneNumberOrName: String,
    messageBody: String,
    recipientName: String?,
  ): Boolean {
    return phoneController?.requestSms(phoneNumberOrName, messageBody, recipientName, initiatedByUser = true)
      ?: false
  }

  override fun searchPhoneContacts(query: String): Boolean {
    val preferBangla = maxCore.ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return phoneController?.searchContacts(query, initiatedByUser = true, preferBangla = preferBangla) ?: false
  }

  override fun onSystemContactPicked(uri: Uri): PhoneContact? {
    return phoneController?.onSystemContactPicked(uri)
  }

  override fun requestAddOrEditContact(
    name: String,
    phoneNumber: String,
    email: String?,
  ): Boolean {
    return phoneController?.requestAddOrEditContact(name, phoneNumber, email, initiatedByUser = true) ?: false
  }

  override fun requestSetAlarm(hour: Int, minute: Int, label: String): Boolean {
    return phoneController?.requestSetAlarm(hour, minute, label, initiatedByUser = true) ?: false
  }

  override fun openSystemAlarms(): Boolean {
    return phoneController?.openSystemAlarms(initiatedByUser = true) ?: false
  }

  override fun startTimer(durationSeconds: Int, label: String): Boolean {
    return phoneController?.startTimer(durationSeconds, label, initiatedByUser = true) ?: false
  }

  override fun pauseTimer() {
    phoneController?.pauseTimer()
  }

  override fun resumeTimer() {
    phoneController?.resumeTimer()
  }

  override fun resetTimer() {
    phoneController?.resetTimer()
  }

  override fun startStopwatch(): Boolean {
    return phoneController?.startStopwatch(initiatedByUser = true) ?: false
  }

  override fun pauseStopwatch() {
    phoneController?.pauseStopwatch()
  }

  override fun lapStopwatch() {
    phoneController?.lapStopwatch()
  }

  override fun resetStopwatch() {
    phoneController?.resetStopwatch()
  }

  override fun calculateMath(expression: String): CalculationRecord? {
    return phoneController?.calculate(expression, initiatedByUser = true)
  }

  override fun requestToggleFlashlight(enable: Boolean, requireConfirmation: Boolean): Boolean {
    return phoneController?.requestToggleFlashlight(enable, initiatedByUser = true, requireConfirmation = requireConfirmation)
      ?: false
  }

  override fun confirmPendingPhoneAction(): Boolean {
    return phoneController?.confirmPendingAction() ?: false
  }

  override fun cancelPendingPhoneAction() {
    phoneController?.cancelPendingAction()
  }

  override fun releasePhoneTaskResources() {
    phoneController?.releaseTaskResources()
  }

  override fun refreshAppAvailability() {
    appController?.refreshAvailability()
  }

  override fun requestOpenApp(app: SupportedApp, requireConfirmation: Boolean): Boolean {
    return appController?.requestOpenApp(app, initiatedByUser = true, requireConfirmation = requireConfirmation)
      ?: false
  }

  override fun requestOpenAppAlternative(app: SupportedApp, alternative: AppAlternative): Boolean {
    return appController?.requestOpenAlternative(
      app = app,
      alternative = alternative,
      initiatedByUser = true,
      requireConfirmation = true,
    ) ?: false
  }

  override fun requestWhatsAppDraft(phoneDigits: String, draftText: String): Boolean {
    return appController?.requestWhatsAppChatDraft(phoneDigits, draftText, initiatedByUser = true)
      ?: false
  }

  override fun requestGmailDraft(recipientEmail: String, subject: String, body: String): Boolean {
    return appController?.requestGmailDraft(recipientEmail, subject, body, initiatedByUser = true)
      ?: false
  }

  override fun requestMapsSearch(query: String): Boolean {
    return appController?.requestMapsSearch(query, initiatedByUser = true) ?: false
  }

  override fun requestChromeUrl(url: String): Boolean {
    return appController?.requestChromeUrl(url, initiatedByUser = true) ?: false
  }

  override fun requestYouTubeSearch(query: String): Boolean {
    return appController?.requestYouTubeSearch(query, initiatedByUser = true) ?: false
  }

  override fun confirmPendingAppAction(): Boolean {
    return appController?.confirmPendingAction() ?: false
  }

  override fun cancelPendingAppAction() {
    appController?.cancelPendingAction()
  }

  override fun resetAppControlState() {
    appController?.resetToDormant()
  }

  override fun requestOpenFilePicker(): Boolean {
    val preferBangla = maxCore.ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return fileController?.requestOpenFilePicker(initiatedByUser = true, preferBangla = preferBangla) ?: false
  }

  override fun onFilesPicked(uris: List<Uri>) {
    fileController?.onFilesPicked(uris)
  }

  override fun filterSessionFiles(query: String, kindFilter: FileKind?) {
    fileController?.filterFiles(query, kindFilter)
  }

  override fun openAndReadFile(itemId: String): Boolean {
    return fileController?.openAndReadFile(itemId, initiatedByUser = true) ?: false
  }

  override fun runOcrOnFile(itemId: String): OcrResult? {
    return fileController?.runOcrOnFile(itemId, initiatedByUser = true)
  }

  override fun requestDeleteFileItem(itemId: String) {
    fileController?.requestDeleteFile(itemId)
  }

  override fun confirmPendingFileAction(): Boolean {
    return fileController?.confirmPendingFileAction() ?: false
  }

  override fun cancelPendingFileAction() {
    fileController?.cancelPendingFileAction()
  }

  override fun closeFilesSession() {
    fileController?.closeFilesSession()
  }

  override fun translateSmartText(text: String, direction: TranslationDirection?): Boolean {
    return smartController?.translateText(text, direction, speakAloud = true) ?: false
  }

  override fun scheduleReminder(title: String, triggerAtMillis: Long): Boolean {
    return smartController?.scheduleReminder(
      title = title,
      triggerAtMillis = triggerAtMillis,
      initiatedByUser = true,
      requestNotificationIfNeeded = true,
    ) ?: false
  }

  override fun toggleReminderCompleted(id: String) {
    smartController?.reminderManager?.toggleReminderCompleted(id)
  }

  override fun deleteReminder(id: String) {
    smartController?.reminderManager?.deleteReminder(id)
  }

  override fun addSmartNote(title: String, content: String, rememberInMemory: Boolean): Boolean {
    return smartController?.addNote(title, content, rememberInMemory, initiatedByUser = true) ?: false
  }

  override fun deleteSmartNote(id: String) {
    smartController?.notesManager?.deleteNote(id)
  }

  override fun loadTodayCalendar(): Boolean {
    val preferBangla = maxCore.ttsManager.voiceConfig.value.language != com.example.tts.VoiceLanguage.ENGLISH_US
    return smartController?.loadTodayCalendar(initiatedByUser = true, preferBangla = preferBangla) ?: false
  }

  override fun openSystemCalendarInsert(title: String): Boolean {
    return smartController?.calendarManager?.openSystemCalendarInsert(title) ?: false
  }

  override fun saveVoiceNoteTranscript(title: String, transcript: String): Boolean {
    return smartController?.saveVoiceNote(title, transcript, initiatedByUser = true) ?: false
  }

  override fun deleteVoiceNote(id: String) {
    smartController?.voiceNotesManager?.deleteVoiceNote(id)
  }

  override fun refreshAiSuggestions(): List<AISuggestionItem> {
    return smartController?.refreshSuggestions().orEmpty()
  }

  override fun processConversationalMessage(userText: String, speakAloud: Boolean): String =
    maxCore.processConversationalMessage(userText, speakAloud)

  override fun setEmotion(emotion: EmotionType) {
    maxCore.setEmotion(emotion)
  }

  override fun speakText(text: String, style: SpeechStyle?) {
    maxCore.speakResponse(text, style)
  }

  override fun pauseSpeaking() {
    maxCore.pauseSpeaking()
  }

  override fun resumeSpeaking() {
    maxCore.resumeSpeaking()
  }

  override fun stopSpeaking() {
    maxCore.stopSpeaking()
  }

  override fun refreshPermissions() {
    maxCore.permissionManager.refreshAll(maxCore.privacyController.policy.value)
  }

  override fun getModuleTopology(): List<ModuleDescriptor> = maxCore.buildModuleTopology()

  override fun addStructuredMemory(type: MemoryType, content: String): MemoryActionResult =
    maxCore.saveStructuredMemory(type, content, hasExplicitPermission = false)

  override fun deleteMemoryById(id: String) {
    maxCore.deleteMemoryById(id)
  }

  override fun clearMemories() {
    maxCore.clearAllMemories()
  }

  override fun updateMemorySettings(settings: MemorySettings) {
    maxCore.updateMemorySettings(settings)
  }

  override fun logDiagnosticPing() {
    maxCore.errorLogger.logInfo("Diagnostics", "Manual foundation self-check completed cleanly.")
  }

  override fun clearLogs() {
    maxCore.errorLogger.clear()
  }

  override suspend fun syncAndSaveSettings(transform: suspend () -> Unit, updated: MAXSettings) {
    transform()
    maxCore.syncSettings(updated)
  }
}
