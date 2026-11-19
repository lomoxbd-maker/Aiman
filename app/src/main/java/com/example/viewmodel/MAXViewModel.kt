package com.example.viewmodel

import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.apps.AppAlternative
import com.example.apps.AppAvailability
import com.example.apps.AppState
import com.example.apps.PendingAppConfirmation
import com.example.apps.SupportedApp
import com.example.camera.CameraLens
import com.example.camera.CameraMode
import com.example.camera.CameraState
import com.example.camera.CapturedMediaResult
import com.example.conversation.ConversationTurn
import com.example.core.MAXCore
import com.example.emotion.EmotionState
import com.example.emotion.EmotionType
import com.example.files.ExtractedDocumentContent
import com.example.files.FileKind
import com.example.files.FileState
import com.example.files.MAXFileItem
import com.example.files.OcrResult
import com.example.files.PendingFileConfirmation
import com.example.gallery.GalleryFilter
import com.example.gallery.GalleryMediaItem
import com.example.gallery.GalleryState
import com.example.gallery.ImageSearchResult
import com.example.gallery.OcrPreparationResult
import com.example.gallery.PendingDeleteConfirmation
import com.example.internet.ConnectionQuality
import com.example.internet.InternetState
import com.example.internet.SearchCategory
import com.example.internet.WebPageContent
import com.example.internet.WebSearchResultItem
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
import com.example.phone.PhoneState
import com.example.phone.StopwatchStatus
import com.example.phone.TimerStatus
import com.example.repository.MAXRepository
import com.example.settings.MAXSettings
import com.example.smart.AISuggestionItem
import com.example.smart.CalendarEventItem
import com.example.smart.ReminderItem
import com.example.smart.SmartFeatureState
import com.example.smart.SmartNoteItem
import com.example.smart.TranslationDirection
import com.example.smart.TranslationRecord
import com.example.smart.VoiceNoteItem
import com.example.tts.SpeechStyle
import com.example.tts.VoiceConfig
import com.example.tts.VoiceLanguage
import com.example.tts.VoiceState
import com.example.utils.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MAXViewModel — Exposes reactive [MAXUiState] and dispatches user actions cleanly to [MAXRepository].
 */
class MAXViewModel(
  private val repository: MAXRepository,
  private val maxCore: MAXCore,
) : ViewModel() {

  private val selectedLogFilter = MutableStateFlow<LogLevel?>(null)
  private val lastMemoryFeedback = MutableStateFlow<String?>(null)
  private val galleryPickerLaunchToken = MutableStateFlow(0L)
  private val filePickerLaunchToken = MutableStateFlow(0L)
  private var pendingCameraModeAfterPermission: CameraMode? = null
  private var pendingInternetActionAfterPermission: PendingInternetAction? = null
  private var pendingPhonePermissionAction: PendingPhonePermissionAction? = null
  private var pendingLoadCalendarAfterPermission: Boolean = false
  private var pendingUpdateCheckAfterPermission: Boolean = false

  init {
    repository.initializeCore()
  }

  private val coreAndVoiceFlow =
    combine(
      repository.coreState,
      repository.wakeWordState,
      repository.wakeWordDetections,
      repository.speechState,
      repository.ttsState,
    ) { core, wake, detections, speech, tts ->
      CoreVoiceBundle(core, wake, detections, speech, tts)
    }

  private val ttsAndEmotionFlow =
    combine(
      repository.voiceState,
      repository.voiceConfig,
      repository.emotionState,
      repository.conversationTurns,
    ) { vState, vConfig, eState, turns ->
      VoiceEmotionBundle(vState, vConfig, eState, turns)
    }

  private val memoryBundleFlow =
    combine(
      repository.memoryState,
      repository.memoryEntries,
      repository.structuredMemories,
      repository.memorySettings,
      lastMemoryFeedback,
    ) { mState, entries, structured, mSettings, feedback ->
      StructuredMemoryBundle(mState, entries, structured, mSettings, feedback)
    }

  private val privacyFlow =
    combine(
      repository.permissionSnapshots,
      repository.privacyPolicy,
      repository.pendingPermissionRequest,
    ) { snapshots, policy, pending ->
      PrivacyBundle(snapshots, policy, pending)
    }

  private val cameraPrimaryFlow =
    combine(
      repository.cameraState,
      repository.cameraMode,
      repository.cameraLens,
      repository.flashEnabled,
    ) { cState, mode, lens, flash ->
      CameraPrimaryBundle(cState, mode, lens, flash)
    }

  private val cameraBundleFlow =
    combine(
      cameraPrimaryFlow,
      repository.lastCapturedMedia,
      repository.lastScannedQr,
    ) { primary, captured, qr ->
      CameraBundle(primary.state, primary.mode, primary.lens, primary.flashEnabled, captured, qr)
    }

  private val gallerySelectionFlow =
    combine(
      repository.galleryState,
      repository.galleryItems,
      repository.activeGalleryItem,
      repository.gallerySearchQuery,
      repository.galleryFilter,
    ) { gState, items, active, query, filter ->
      GallerySelectionBundle(gState, items, active, query, filter)
    }

  private val galleryBundleFlow =
    combine(
      gallerySelectionFlow,
      repository.pendingDeleteConfirmation,
      repository.lastOcrResult,
      repository.lastSearchResult,
      galleryPickerLaunchToken,
    ) { sel, del, ocr, search, token ->
      GalleryBundle(sel.state, sel.items, sel.activeItem, sel.query, sel.filter, del, ocr, search, token)
    }

  private val auditAndLogsFlow =
    combine(
      repository.affectiveTone,
      privacyFlow,
      repository.logs,
      selectedLogFilter,
      repository.lastWakeWordEvent,
    ) { tone, privacy, logs, filter, lastEvent ->
      AuditLogsBundle(tone, privacy, logs, filter, lastEvent)
    }

  private val internetPrimaryFlow =
    combine(
      repository.internetState,
      repository.connectionQuality,
      repository.searchCategory,
    ) { iState, quality, cat ->
      InternetPrimaryBundle(iState, quality, cat)
    }

  private val internetBundleFlow =
    combine(
      internetPrimaryFlow,
      repository.searchResults,
      repository.currentWebPage,
      repository.lastWebSummary,
    ) { primary, results, page, summary ->
      InternetBundle(primary.state, primary.quality, primary.category, results, page, summary)
    }

  private val phoneCommFlow =
    combine(
      repository.phoneState,
      repository.phoneContacts,
      repository.scheduledAlarms,
      repository.pendingPhoneConfirmation,
    ) { pState, contacts, alarms, pending ->
      PhoneCommBundle(pState, contacts, alarms, pending)
    }

  private val phoneUtilitiesFlow =
    combine(
      repository.timerStatus,
      repository.stopwatchStatus,
      repository.calculationHistory,
      repository.isTorchOn,
    ) { timer, stopwatch, calc, torch ->
      PhoneUtilitiesBundle(timer, stopwatch, calc, torch)
    }

  private val phoneBundleFlow =
    combine(phoneCommFlow, phoneUtilitiesFlow) { comm, util ->
      PhoneBundle(
        state = comm.state,
        contacts = comm.contacts,
        alarms = comm.alarms,
        pendingConfirmation = comm.pendingConfirmation,
        timerStatus = util.timerStatus,
        stopwatchStatus = util.stopwatchStatus,
        calculationHistory = util.calculationHistory,
        isTorchOn = util.isTorchOn,
      )
    }

  private val appBundleFlow =
    combine(
      repository.appState,
      repository.appAvailabilities,
      repository.pendingAppConfirmation,
    ) { aState, avail, pending ->
      AppBundle(aState, avail, pending)
    }

  private val filesPrimaryFlow =
    combine(
      repository.fileState,
      repository.sessionFiles,
      repository.activeFileItem,
      repository.activeFileContent,
    ) { fState, files, active, content ->
      FilesPrimaryBundle(fState, files, active, content)
    }

  private val filesBundleFlow =
    combine(
      filesPrimaryFlow,
      repository.lastFileOcrResult,
      repository.pendingFileConfirmation,
      filePickerLaunchToken,
    ) { primary, ocr, pending, token ->
      FilesBundle(primary.state, primary.files, primary.active, primary.content, ocr, pending, token)
    }

  private val smartPrimaryFlow =
    combine(
      repository.smartState,
      repository.translationHistory,
      repository.lastTranslation,
      repository.reminders,
      repository.smartNotes,
    ) { sState, tHist, lastTrans, rems, notes ->
      SmartPrimaryBundle(sState, tHist, lastTrans, rems, notes)
    }

  private val smartBundleFlow =
    combine(
      smartPrimaryFlow,
      repository.calendarEvents,
      repository.calendarSummary,
      repository.voiceNotes,
      repository.aiSuggestions,
    ) { primary, calEvents, calSum, vNotes, suggestions ->
      SmartBundle(
        state = primary.state,
        translationHistory = primary.translationHistory,
        lastTranslation = primary.lastTranslation,
        reminders = primary.reminders,
        notes = primary.notes,
        calendarEvents = calEvents,
        calendarSummary = calSum,
        voiceNotes = vNotes,
        aiSuggestions = suggestions,
      )
    }

  private val productivityBundleFlow =
    combine(appBundleFlow, filesBundleFlow, smartBundleFlow) { apps, files, smart ->
      ProductivityBundle(apps, files, smart)
    }

  private val visionCombinedFlow =
    combine(
      cameraBundleFlow,
      galleryBundleFlow,
      internetBundleFlow,
      phoneBundleFlow,
      productivityBundleFlow,
    ) { cam, gal, net, phone, prod ->
      VisionAndInternetBundle(cam, gal, net, phone, prod.apps, prod.files, prod.smart)
    }

  private val finalIntegrationFlow =
    combine(
      repository.followUpPrompt,
      repository.lastCompletedTask,
      repository.isThinking,
      repository.updateState,
    ) { followUp, lastTask, thinking, update ->
      FinalIntegrationBundle(followUp, lastTask, thinking, update)
    }

  private val advancedSystemsFlow =
    combine(
      repository.brainState,
      repository.routines,
      repository.automationState,
      repository.lastBriefing,
      repository.floatingState,
    ) { brain, routines, autoState, briefing, floating ->
      AdvancedSystemsBundle(brain, routines, autoState, briefing, floating)
    }

  val uiState: StateFlow<MAXUiState> =
    combine(
      coreAndVoiceFlow,
      ttsAndEmotionFlow,
      memoryBundleFlow,
      auditAndLogsFlow,
      visionCombinedFlow,
      repository.settingsFlow,
      repository.compatibilityProfile,
      finalIntegrationFlow,
      advancedSystemsFlow,
    ) { values ->
      val coreVoice = values[0] as CoreVoiceBundle
      val voiceEmotion = values[1] as VoiceEmotionBundle
      val memoryBundle = values[2] as StructuredMemoryBundle
      val auditLogs = values[3] as AuditLogsBundle
      val multiBundle = values[4] as VisionAndInternetBundle
      val settings = values[5] as MAXSettings
      val compat = values[6] as com.example.utils.DeviceCompatibilityProfile
      val finalBundle = values[7] as FinalIntegrationBundle
      val advanced = values[8] as AdvancedSystemsBundle

      val cam = multiBundle.camera
      val gal = multiBundle.gallery
      val net = multiBundle.internet
      val phone = multiBundle.phone
      val apps = multiBundle.apps
      val files = multiBundle.files
      val smart = multiBundle.smart

      val filteredLogs =
        if (auditLogs.logFilter == null) {
          auditLogs.logs
        } else {
          auditLogs.logs.filter { it.level == auditLogs.logFilter }
        }

      MAXUiState(
        coreState = coreVoice.coreState,
        wakeWordState = coreVoice.wakeWordState,
        wakeWordDetections = coreVoice.wakeWordDetections,
        speechState = coreVoice.speechState,
        ttsState = coreVoice.ttsState,
        voiceState = voiceEmotion.voiceState,
        voiceConfig = voiceEmotion.voiceConfig,
        memoryState = memoryBundle.memoryState,
        memoryEntries = memoryBundle.memoryEntries,
        structuredMemories = memoryBundle.structuredMemories,
        memorySettings = memoryBundle.memorySettings,
        lastMemoryFeedback = memoryBundle.feedback,
        affectiveTone = auditLogs.tone,
        emotionState = voiceEmotion.emotionState,
        conversationTurns = voiceEmotion.turns,
        permissionSnapshots = auditLogs.privacy.snapshots,
        privacyPolicy = auditLogs.privacy.policy,
        pendingPermissionRequest = auditLogs.privacy.pending,
        cameraState = cam.state,
        cameraMode = cam.mode,
        cameraLens = cam.lens,
        flashEnabled = cam.flashEnabled,
        lastCapturedMedia = cam.lastCaptured,
        lastScannedQr = cam.lastQr,
        galleryState = gal.state,
        galleryItems = gal.items,
        activeGalleryItem = gal.activeItem,
        gallerySearchQuery = gal.searchQuery,
        galleryFilter = gal.filter,
        pendingDeleteConfirmation = gal.pendingDelete,
        lastOcrResult = gal.lastOcr,
        lastSearchResult = gal.lastSearch,
        galleryPickerLaunchToken = gal.pickerLaunchToken,
        internetState = net.state,
        connectionQuality = net.quality,
        searchCategory = net.category,
        searchResults = net.results,
        currentWebPage = net.currentPage,
        lastWebSummary = net.lastSummary,
        phoneState = phone.state,
        phoneContacts = phone.contacts,
        scheduledAlarms = phone.alarms,
        timerStatus = phone.timerStatus,
        stopwatchStatus = phone.stopwatchStatus,
        calculationHistory = phone.calculationHistory,
        isTorchOn = phone.isTorchOn,
        pendingPhoneConfirmation = phone.pendingConfirmation,
        appState = apps.state,
        appAvailabilities = apps.availabilities,
        pendingAppConfirmation = apps.pendingConfirmation,
        fileState = files.state,
        sessionFiles = files.items,
        activeFileItem = files.activeItem,
        activeFileContent = files.activeContent,
        lastFileOcrResult = files.lastOcr,
        pendingFileConfirmation = files.pendingConfirmation,
        filePickerLaunchToken = files.pickerLaunchToken,
        smartState = smart.state,
        translationHistory = smart.translationHistory,
        lastTranslation = smart.lastTranslation,
        reminders = smart.reminders,
        smartNotes = smart.notes,
        calendarEvents = smart.calendarEvents,
        calendarSummary = smart.calendarSummary,
        voiceNotes = smart.voiceNotes,
        aiSuggestions = smart.aiSuggestions,
        logs = filteredLogs,
        selectedLogFilter = auditLogs.logFilter,
        settings = settings,
        modules = repository.getModuleTopology(),
        compatibility = compat,
        lastWakeWordEvent = auditLogs.lastWakeWordEvent,
        followUpPrompt = finalBundle.followUpPrompt,
        lastCompletedTask = finalBundle.lastCompletedTask,
        isThinking = finalBundle.isThinking,
        updateState = finalBundle.updateState,
        brainState = advanced.brainState,
        routines = advanced.routines,
        automationState = advanced.automationState,
        lastBriefing = advanced.lastBriefing,
        floatingState = advanced.floatingState,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = MAXUiState(modules = repository.getModuleTopology()),
    )

  fun runRoutine(category: com.example.automation.RoutineCategory) {
    repository.runRoutine(category)
  }

  fun toggleRoutineEnabled(routineId: String) {
    repository.toggleRoutineEnabled(routineId)
  }

  fun activateFloatingBubble(preferSystemOverlay: Boolean = false): Boolean {
    return repository.activateFloatingBubble(preferSystemOverlay)
  }

  fun minimizeFloatingBubble() {
    repository.minimizeFloatingBubble()
  }

  fun expandFloatingBubble(isVoiceArmed: Boolean = false) {
    repository.expandFloatingBubble(isVoiceArmed)
  }

  fun triggerFloatingQuickVoice(): Boolean {
    return repository.triggerFloatingQuickVoice()
  }

  fun closeFloatingBubble() {
    repository.closeFloatingBubble()
  }

  fun requestOverlayPermissionSettings(): Boolean {
    return repository.requestOverlayPermissionSettings()
  }

  fun activateManualVoiceSession() {
    repository.activateManualVoiceSession()
  }

  fun finishFollowUpToWakeMode() {
    repository.finishFollowUpToWakeMode()
  }

  fun onAppLifecycleChanged(inBackground: Boolean) {
    repository.handleBackgroundLifecycle(inBackground)
  }

  fun checkForUpdates() {
    pendingUpdateCheckAfterPermission = true
    repository.checkForUpdates()
    if (repository.updateState.value !is com.example.update.UpdateState.PermissionRequired) {
      pendingUpdateCheckAfterPermission = false
    }
  }

  fun openVerifiedUpdateUrl(url: String): Boolean {
    return repository.openVerifiedUpdateUrl(url)
  }

  fun armWakeWordStandby() {
    repository.armWakeWordStandby()
  }

  fun disarmWakeWordStandby() {
    repository.disarmWakeWordStandby()
  }

  fun requestWakeWordPermissionWithExplanation() {
    repository.requestWakeWordPermissionWithExplanation()
  }

  fun dismissPendingPermissionRequest() {
    pendingCameraModeAfterPermission = null
    pendingInternetActionAfterPermission = null
    pendingPhonePermissionAction = null
    repository.dismissPendingPermissionRequest()
  }

  fun onPermissionPromptCompleted(permission: AppPermission, granted: Boolean) {
    repository.onPermissionPromptCompleted(permission, granted)
    if (permission == AppPermission.CAMERA && granted) {
      val pendingMode = pendingCameraModeAfterPermission
      pendingCameraModeAfterPermission = null
      if (pendingMode != null) {
        repository.openCamera(pendingMode)
      }
    } else if (permission == AppPermission.GALLERY && granted) {
      if (repository.openGallery()) {
        galleryPickerLaunchToken.value = System.currentTimeMillis()
      }
    } else if (permission == AppPermission.INTERNET && granted) {
      val pendingAction = pendingInternetActionAfterPermission
      pendingInternetActionAfterPermission = null
      when (pendingAction) {
        is PendingInternetAction.Search ->
          repository.searchInternet(pendingAction.query, pendingAction.category)
        is PendingInternetAction.OpenWebsite ->
          repository.openWebsite(pendingAction.url)
        is PendingInternetAction.Summarize ->
          repository.summarizeWebContent(pendingAction.topicOrUrl)
        is PendingInternetAction.ExternalUrl ->
          repository.openExternalVerifiedUrl(pendingAction.url)
        null -> {
          if (pendingUpdateCheckAfterPermission) {
            pendingUpdateCheckAfterPermission = false
            repository.checkForUpdates()
          } else {
            repository.refreshConnectionQuality()
          }
        }
      }
    } else if (permission == AppPermission.CONTACTS && granted) {
      val pending = pendingPhonePermissionAction
      pendingPhonePermissionAction = null
      if (pending is PendingPhonePermissionAction.SearchContacts) {
        repository.searchPhoneContacts(pending.query)
      }
    } else if (permission == AppPermission.PHONE_CALL && granted) {
      val pending = pendingPhonePermissionAction
      pendingPhonePermissionAction = null
      if (pending is PendingPhonePermissionAction.PlaceCall) {
        repository.requestPhoneCall(pending.number, pending.name, pending.directCall)
      }
    } else if (permission == AppPermission.FILES && granted) {
      if (repository.requestOpenFilePicker()) {
        filePickerLaunchToken.value = System.currentTimeMillis()
      }
    } else if (permission == AppPermission.CALENDAR && granted) {
      if (pendingLoadCalendarAfterPermission) {
        pendingLoadCalendarAfterPermission = false
        repository.loadTodayCalendar()
      }
    }
  }

  fun onAudioPermissionResult(granted: Boolean) {
    onPermissionPromptCompleted(AppPermission.MICROPHONE_WAKE_WORD, granted)
  }

  fun openCamera(mode: CameraMode) {
    pendingCameraModeAfterPermission = mode
    if (repository.openCamera(mode)) {
      pendingCameraModeAfterPermission = null
    }
  }

  fun bindCameraXSession(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
    repository.bindCameraXSession(lifecycleOwner, surfaceProvider)
  }

  fun capturePhoto() = repository.capturePhoto()

  fun startVideoRecording() = repository.startVideoRecording()

  fun stopVideoRecording() = repository.stopVideoRecording()

  fun switchCameraLens() = repository.switchCameraLens()

  fun toggleCameraFlash() = repository.toggleCameraFlash()

  fun closeCamera() = repository.closeCamera()

  fun openGallery() {
    if (repository.openGallery()) {
      galleryPickerLaunchToken.value = System.currentTimeMillis()
    }
  }

  fun onGalleryMediaPicked(uris: List<Uri>) = repository.onGalleryMediaPicked(uris)

  fun selectGalleryItem(itemId: String) = repository.selectGalleryItem(itemId)

  fun findGalleryPhotos(query: String, filter: GalleryFilter) =
    repository.findGalleryPhotos(query, filter)

  fun prepareGalleryOcr(itemId: String) = repository.prepareGalleryOcr(itemId)

  fun requestDeleteGalleryItem(itemId: String) = repository.requestDeleteGalleryItem(itemId)

  fun confirmDeleteGalleryItem() = repository.confirmDeleteGalleryItem()

  fun cancelDeleteGalleryItem() = repository.cancelDeleteGalleryItem()

  fun closeGallery() = repository.closeGallery()

  fun refreshConnectionQuality() = repository.refreshConnectionQuality()

  fun searchInternet(query: String, category: SearchCategory) {
    pendingInternetActionAfterPermission = PendingInternetAction.Search(query, category)
    if (repository.searchInternet(query, category)) {
      pendingInternetActionAfterPermission = null
    }
  }

  fun openWebsite(url: String) {
    pendingInternetActionAfterPermission = PendingInternetAction.OpenWebsite(url)
    if (repository.openWebsite(url)) {
      pendingInternetActionAfterPermission = null
    }
  }

  fun summarizeWebContent(topicOrUrl: String? = null) {
    pendingInternetActionAfterPermission = PendingInternetAction.Summarize(topicOrUrl)
    if (repository.summarizeWebContent(topicOrUrl)) {
      pendingInternetActionAfterPermission = null
    }
  }

  fun openExternalVerifiedUrl(url: String) {
    pendingInternetActionAfterPermission = PendingInternetAction.ExternalUrl(url)
    if (repository.openExternalVerifiedUrl(url)) {
      pendingInternetActionAfterPermission = null
    }
  }

  fun cancelInternetRequest() = repository.cancelInternetRequest()

  fun closeBrowserSession() = repository.closeBrowserSession()

  fun requestPhoneCall(
    phoneNumberOrName: String,
    recipientName: String? = null,
    preferDirectCall: Boolean = false,
  ) {
    pendingPhonePermissionAction =
      PendingPhonePermissionAction.PlaceCall(phoneNumberOrName, recipientName, preferDirectCall)
    if (repository.requestPhoneCall(phoneNumberOrName, recipientName, preferDirectCall)) {
      pendingPhonePermissionAction = null
    }
  }

  fun requestSms(
    phoneNumberOrName: String,
    messageBody: String,
    recipientName: String? = null,
  ) = repository.requestSms(phoneNumberOrName, messageBody, recipientName)

  fun searchPhoneContacts(query: String) {
    pendingPhonePermissionAction = PendingPhonePermissionAction.SearchContacts(query)
    if (repository.searchPhoneContacts(query)) {
      pendingPhonePermissionAction = null
    }
  }

  fun onSystemContactPicked(uri: Uri) = repository.onSystemContactPicked(uri)

  fun requestAddOrEditContact(name: String, phoneNumber: String, email: String? = null) =
    repository.requestAddOrEditContact(name, phoneNumber, email)

  fun requestSetAlarm(hour: Int, minute: Int, label: String) =
    repository.requestSetAlarm(hour, minute, label)

  fun openSystemAlarms() = repository.openSystemAlarms()

  fun startTimer(durationSeconds: Int, label: String = "MAX Timer") =
    repository.startTimer(durationSeconds, label)

  fun pauseTimer() = repository.pauseTimer()

  fun resumeTimer() = repository.resumeTimer()

  fun resetTimer() = repository.resetTimer()

  fun startStopwatch() = repository.startStopwatch()

  fun pauseStopwatch() = repository.pauseStopwatch()

  fun lapStopwatch() = repository.lapStopwatch()

  fun resetStopwatch() = repository.resetStopwatch()

  fun calculateMath(expression: String) = repository.calculateMath(expression)

  fun requestToggleFlashlight(enable: Boolean, requireConfirmation: Boolean = true) =
    repository.requestToggleFlashlight(enable, requireConfirmation)

  fun confirmPendingPhoneAction() = repository.confirmPendingPhoneAction()

  fun cancelPendingPhoneAction() = repository.cancelPendingPhoneAction()

  fun releasePhoneTaskResources() = repository.releasePhoneTaskResources()

  fun refreshAppAvailability() = repository.refreshAppAvailability()

  fun requestOpenApp(app: SupportedApp, requireConfirmation: Boolean = true) =
    repository.requestOpenApp(app, requireConfirmation)

  fun requestOpenAppAlternative(app: SupportedApp, alternative: AppAlternative) =
    repository.requestOpenAppAlternative(app, alternative)

  fun requestWhatsAppDraft(phoneDigits: String, draftText: String) =
    repository.requestWhatsAppDraft(phoneDigits, draftText)

  fun requestGmailDraft(recipientEmail: String, subject: String, body: String) =
    repository.requestGmailDraft(recipientEmail, subject, body)

  fun requestMapsSearch(query: String) = repository.requestMapsSearch(query)

  fun requestChromeUrl(url: String) = repository.requestChromeUrl(url)

  fun requestYouTubeSearch(query: String) = repository.requestYouTubeSearch(query)

  fun confirmPendingAppAction() = repository.confirmPendingAppAction()

  fun cancelPendingAppAction() = repository.cancelPendingAppAction()

  fun resetAppControlState() = repository.resetAppControlState()

  fun requestOpenFilePicker() {
    if (repository.requestOpenFilePicker()) {
      filePickerLaunchToken.value = System.currentTimeMillis()
    }
  }

  fun onFilesPicked(uris: List<Uri>) = repository.onFilesPicked(uris)

  fun filterSessionFiles(query: String, kindFilter: FileKind? = null) =
    repository.filterSessionFiles(query, kindFilter)

  fun openAndReadFile(itemId: String) = repository.openAndReadFile(itemId)

  fun runOcrOnFile(itemId: String) = repository.runOcrOnFile(itemId)

  fun requestDeleteFileItem(itemId: String) = repository.requestDeleteFileItem(itemId)

  fun confirmPendingFileAction() = repository.confirmPendingFileAction()

  fun cancelPendingFileAction() = repository.cancelPendingFileAction()

  fun closeFilesSession() = repository.closeFilesSession()

  fun translateSmartText(text: String, direction: TranslationDirection? = null) =
    repository.translateSmartText(text, direction)

  fun scheduleReminder(title: String, triggerAtMillis: Long) =
    repository.scheduleReminder(title, triggerAtMillis)

  fun toggleReminderCompleted(id: String) = repository.toggleReminderCompleted(id)

  fun deleteReminder(id: String) = repository.deleteReminder(id)

  fun addSmartNote(title: String, content: String, rememberInMemory: Boolean) =
    repository.addSmartNote(title, content, rememberInMemory)

  fun deleteSmartNote(id: String) = repository.deleteSmartNote(id)

  fun loadTodayCalendar() {
    pendingLoadCalendarAfterPermission = true
    if (repository.loadTodayCalendar()) {
      pendingLoadCalendarAfterPermission = false
    }
  }

  fun openSystemCalendarInsert(title: String) = repository.openSystemCalendarInsert(title)

  fun saveVoiceNoteTranscript(title: String, transcript: String) =
    repository.saveVoiceNoteTranscript(title, transcript)

  fun deleteVoiceNote(id: String) = repository.deleteVoiceNote(id)

  fun refreshAiSuggestions() = repository.refreshAiSuggestions()

  fun setPermissionAllowedByUser(permission: AppPermission, allowed: Boolean) {
    repository.setPermissionAllowedByUser(permission, allowed)
  }

  fun simulateWakeWordMax() {
    repository.simulateWakeWordMax()
  }

  fun returnToStandby() {
    repository.returnToStandby()
  }

  fun sendConversationalMessage(userText: String, speakAloud: Boolean = true) {
    repository.processConversationalMessage(userText, speakAloud)
  }

  fun selectEmotion(emotion: EmotionType) {
    repository.setEmotion(emotion)
  }

  fun speakText(text: String, style: SpeechStyle? = null) {
    repository.speakText(text, style)
  }

  fun pauseSpeaking() {
    repository.pauseSpeaking()
  }

  fun resumeSpeaking() {
    repository.resumeSpeaking()
  }

  fun stopSpeaking() {
    repository.stopSpeaking()
  }

  fun setVoiceLanguage(language: VoiceLanguage) {
    val updated = uiState.value.settings.copy(voiceLanguage = language)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setVoiceLanguage(language) },
        updated = updated,
      )
    }
  }

  fun setSpeechStyle(style: SpeechStyle) {
    val updated = uiState.value.settings.copy(speechStyle = style)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setSpeechStyle(style) },
        updated = updated,
      )
    }
  }

  fun setLogFilter(level: LogLevel?) {
    selectedLogFilter.value = level
  }

  fun addMemoryNote(type: MemoryType, note: String) {
    val result = repository.addStructuredMemory(type, note)
    lastMemoryFeedback.value =
      if (uiState.value.settings.voiceLanguage == VoiceLanguage.ENGLISH_US) {
        result.messageEnglish
      } else {
        result.messageBangla
      }
  }

  fun deleteMemoryById(id: String) {
    repository.deleteMemoryById(id)
  }

  fun clearMemories() {
    repository.clearMemories()
    lastMemoryFeedback.value = "সব মেমরি মুছে ফেলা হয়েছে।"
  }

  fun setAllowSensitiveMemory(allow: Boolean) {
    val updated = uiState.value.memorySettings.copy(allowSensitiveInformation = allow)
    repository.updateMemorySettings(updated)
  }

  fun runSelfCheck() {
    repository.refreshPermissions()
    repository.logDiagnosticPing()
  }

  fun clearLogs() {
    repository.clearLogs()
  }

  fun setWakeWordEnabled(enabled: Boolean) {
    val updated = uiState.value.settings.copy(wakeWordEnabled = enabled)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setWakeWordEnabled(enabled) },
        updated = updated,
      )
      if (enabled) armWakeWordStandby() else disarmWakeWordStandby()
    }
  }

  fun setWakeWordSensitivity(value: Float) {
    val updated = uiState.value.settings.copy(wakeWordSensitivity = value)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setWakeWordSensitivity(value) },
        updated = updated,
      )
    }
  }

  fun setTtsSpeechRate(rate: Float) {
    val updated = uiState.value.settings.copy(ttsSpeechRate = rate)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setTtsSpeechRate(rate) },
        updated = updated,
      )
    }
  }

  fun setTtsPitch(pitch: Float) {
    val updated = uiState.value.settings.copy(ttsPitch = pitch)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setTtsPitch(pitch) },
        updated = updated,
      )
    }
  }

  fun setMemoryRetentionEnabled(enabled: Boolean) {
    val updated = uiState.value.settings.copy(memoryRetentionEnabled = enabled)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setMemoryRetentionEnabled(enabled) },
        updated = updated,
      )
    }
  }

  fun setLowPowerStandbyMode(enabled: Boolean) {
    val updated = uiState.value.settings.copy(lowPowerStandbyMode = enabled)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setLowPowerStandbyMode(enabled) },
        updated = updated,
      )
    }
  }

  fun setDarkThemePreferred(preferred: Boolean) {
    val updated = uiState.value.settings.copy(darkThemePreferred = preferred)
    viewModelScope.launch {
      repository.syncAndSaveSettings(
        transform = { maxCore.settingsManager.setDarkThemePreferred(preferred) },
        updated = updated,
      )
    }
  }

  private data class CoreVoiceBundle(
    val coreState: com.example.core.CoreLifecycleState,
    val wakeWordState: com.example.wakeword.WakeWordState,
    val wakeWordDetections: Int,
    val speechState: com.example.speech.SpeechState,
    val ttsState: com.example.tts.TTSState,
  )

  private data class VoiceEmotionBundle(
    val voiceState: VoiceState,
    val voiceConfig: VoiceConfig,
    val emotionState: EmotionState,
    val turns: List<ConversationTurn>,
  )

  private data class StructuredMemoryBundle(
    val memoryState: MemoryEngineState,
    val memoryEntries: List<MemoryEntry>,
    val structuredMemories: List<MemoryModel>,
    val memorySettings: MemorySettings,
    val feedback: String?,
  )

  private data class PrivacyBundle(
    val snapshots: List<PermissionSnapshot>,
    val policy: PrivacyPolicy,
    val pending: PendingPermissionRequest?,
  )

  private data class AuditLogsBundle(
    val tone: com.example.emotion.AffectiveTone,
    val privacy: PrivacyBundle,
    val logs: List<com.example.utils.LogEntry>,
    val logFilter: LogLevel?,
    val lastWakeWordEvent: String?,
  )

  private data class CameraPrimaryBundle(
    val state: CameraState,
    val mode: CameraMode,
    val lens: CameraLens,
    val flashEnabled: Boolean,
  )

  private data class CameraBundle(
    val state: CameraState,
    val mode: CameraMode,
    val lens: CameraLens,
    val flashEnabled: Boolean,
    val lastCaptured: CapturedMediaResult?,
    val lastQr: String?,
  )

  private data class GallerySelectionBundle(
    val state: GalleryState,
    val items: List<GalleryMediaItem>,
    val activeItem: GalleryMediaItem?,
    val query: String,
    val filter: GalleryFilter,
  )

  private data class GalleryBundle(
    val state: GalleryState,
    val items: List<GalleryMediaItem>,
    val activeItem: GalleryMediaItem?,
    val searchQuery: String,
    val filter: GalleryFilter,
    val pendingDelete: PendingDeleteConfirmation?,
    val lastOcr: OcrPreparationResult?,
    val lastSearch: ImageSearchResult?,
    val pickerLaunchToken: Long,
  )

  private data class InternetPrimaryBundle(
    val state: InternetState,
    val quality: ConnectionQuality,
    val category: SearchCategory,
  )

  private data class InternetBundle(
    val state: InternetState,
    val quality: ConnectionQuality,
    val category: SearchCategory,
    val results: List<WebSearchResultItem>,
    val currentPage: WebPageContent?,
    val lastSummary: String?,
  )

  private data class PhoneCommBundle(
    val state: PhoneState,
    val contacts: List<PhoneContact>,
    val alarms: List<AlarmEntry>,
    val pendingConfirmation: PendingPhoneConfirmation?,
  )

  private data class PhoneUtilitiesBundle(
    val timerStatus: TimerStatus,
    val stopwatchStatus: StopwatchStatus,
    val calculationHistory: List<CalculationRecord>,
    val isTorchOn: Boolean,
  )

  private data class PhoneBundle(
    val state: PhoneState,
    val contacts: List<PhoneContact>,
    val alarms: List<AlarmEntry>,
    val pendingConfirmation: PendingPhoneConfirmation?,
    val timerStatus: TimerStatus,
    val stopwatchStatus: StopwatchStatus,
    val calculationHistory: List<CalculationRecord>,
    val isTorchOn: Boolean,
  )

  private data class AppBundle(
    val state: AppState,
    val availabilities: Map<SupportedApp, AppAvailability>,
    val pendingConfirmation: PendingAppConfirmation?,
  )

  private data class FilesPrimaryBundle(
    val state: FileState,
    val files: List<MAXFileItem>,
    val active: MAXFileItem?,
    val content: ExtractedDocumentContent?,
  )

  private data class FilesBundle(
    val state: FileState,
    val items: List<MAXFileItem>,
    val activeItem: MAXFileItem?,
    val activeContent: ExtractedDocumentContent?,
    val lastOcr: OcrResult?,
    val pendingConfirmation: PendingFileConfirmation?,
    val pickerLaunchToken: Long,
  )

  private data class SmartPrimaryBundle(
    val state: SmartFeatureState,
    val translationHistory: List<TranslationRecord>,
    val lastTranslation: TranslationRecord?,
    val reminders: List<ReminderItem>,
    val notes: List<SmartNoteItem>,
  )

  private data class SmartBundle(
    val state: SmartFeatureState,
    val translationHistory: List<TranslationRecord>,
    val lastTranslation: TranslationRecord?,
    val reminders: List<ReminderItem>,
    val notes: List<SmartNoteItem>,
    val calendarEvents: List<CalendarEventItem>,
    val calendarSummary: String,
    val voiceNotes: List<VoiceNoteItem>,
    val aiSuggestions: List<AISuggestionItem>,
  )

  private data class ProductivityBundle(
    val apps: AppBundle,
    val files: FilesBundle,
    val smart: SmartBundle,
  )

  private data class AdvancedSystemsBundle(
    val brainState: com.example.brain.BrainLifecycleState,
    val routines: List<com.example.automation.AutomationRoutine>,
    val automationState: com.example.automation.AutomationState,
    val lastBriefing: com.example.automation.DailyBriefingSummary?,
    val floatingState: com.example.floating.FloatingState,
  )

  private data class FinalIntegrationBundle(
    val followUpPrompt: String?,
    val lastCompletedTask: String?,
    val isThinking: Boolean,
    val updateState: com.example.update.UpdateState,
  )

  private data class VisionAndInternetBundle(
    val camera: CameraBundle,
    val gallery: GalleryBundle,
    val internet: InternetBundle,
    val phone: PhoneBundle,
    val apps: AppBundle,
    val files: FilesBundle,
    val smart: SmartBundle,
  )

  private sealed interface PendingInternetAction {
    data class Search(val query: String, val category: SearchCategory) : PendingInternetAction
    data class OpenWebsite(val url: String) : PendingInternetAction
    data class Summarize(val topicOrUrl: String?) : PendingInternetAction
    data class ExternalUrl(val url: String) : PendingInternetAction
  }

  private sealed interface PendingPhonePermissionAction {
    data class SearchContacts(val query: String) : PendingPhonePermissionAction
    data class PlaceCall(val number: String, val name: String?, val directCall: Boolean) : PendingPhonePermissionAction
  }

  class Factory(
    private val repository: MAXRepository,
    private val maxCore: MAXCore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(MAXViewModel::class.java)) {
        return MAXViewModel(repository, maxCore) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
  }
}
