package com.example.viewmodel

import com.example.apps.AppAvailability
import com.example.apps.AppState
import com.example.apps.PendingAppConfirmation
import com.example.apps.SupportedApp
import com.example.camera.CameraLens
import com.example.camera.CameraMode
import com.example.camera.CameraState
import com.example.camera.CapturedMediaResult
import com.example.conversation.ConversationTurn
import com.example.core.CoreLifecycleState
import com.example.core.ModuleDescriptor
import com.example.emotion.AffectiveTone
import com.example.emotion.EmotionState
import com.example.files.ExtractedDocumentContent
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
import com.example.settings.MAXSettings
import com.example.smart.AISuggestionItem
import com.example.smart.CalendarEventItem
import com.example.smart.ReminderItem
import com.example.smart.SmartFeatureState
import com.example.smart.SmartNoteItem
import com.example.smart.TranslationRecord
import com.example.smart.VoiceNoteItem
import com.example.speech.SpeechState
import com.example.tts.TTSState
import com.example.tts.VoiceConfig
import com.example.tts.VoiceState
import com.example.utils.DeviceCompatibilityProfile
import com.example.utils.LogEntry
import com.example.utils.LogLevel
import com.example.wakeword.WakeWordState

/**
 * Immutable UI state consumed by Jetpack Compose screens.
 */
data class MAXUiState(
  val coreState: CoreLifecycleState = CoreLifecycleState.UNINITIALIZED,
  val wakeWordState: WakeWordState = WakeWordState.Idle,
  val wakeWordDetections: Int = 0,
  val speechState: SpeechState = SpeechState.UNINITIALIZED,
  val ttsState: TTSState = TTSState.UNINITIALIZED,
  val voiceState: VoiceState = VoiceState.Uninitialized,
  val voiceConfig: VoiceConfig = VoiceConfig(),
  val memoryState: MemoryEngineState = MemoryEngineState.UNINITIALIZED,
  val memoryEntries: List<MemoryEntry> = emptyList(),
  val structuredMemories: List<MemoryModel> = emptyList(),
  val memorySettings: MemorySettings = MemorySettings(),
  val lastMemoryFeedback: String? = null,
  val affectiveTone: AffectiveTone = AffectiveTone.NEUTRAL_CALM,
  val emotionState: EmotionState = EmotionState(),
  val conversationTurns: List<ConversationTurn> = emptyList(),
  val permissionSnapshots: List<PermissionSnapshot> = emptyList(),
  val privacyPolicy: PrivacyPolicy = PrivacyPolicy(),
  val pendingPermissionRequest: PendingPermissionRequest? = null,
  val cameraState: CameraState = CameraState.Dormant,
  val cameraMode: CameraMode = CameraMode.PHOTO,
  val cameraLens: CameraLens = CameraLens.BACK,
  val flashEnabled: Boolean = false,
  val lastCapturedMedia: CapturedMediaResult? = null,
  val lastScannedQr: String? = null,
  val galleryState: GalleryState = GalleryState.Dormant,
  val galleryItems: List<GalleryMediaItem> = emptyList(),
  val activeGalleryItem: GalleryMediaItem? = null,
  val gallerySearchQuery: String = "",
  val galleryFilter: GalleryFilter = GalleryFilter.ALL,
  val pendingDeleteConfirmation: PendingDeleteConfirmation? = null,
  val lastOcrResult: OcrPreparationResult? = null,
  val lastSearchResult: ImageSearchResult? = null,
  val galleryPickerLaunchToken: Long = 0L,
  val internetState: InternetState = InternetState.Dormant,
  val connectionQuality: ConnectionQuality = ConnectionQuality.OFFLINE,
  val searchCategory: SearchCategory = SearchCategory.WEB,
  val searchResults: List<WebSearchResultItem> = emptyList(),
  val currentWebPage: WebPageContent? = null,
  val lastWebSummary: String? = null,
  val phoneState: PhoneState = PhoneState.Dormant,
  val phoneContacts: List<PhoneContact> = emptyList(),
  val scheduledAlarms: List<AlarmEntry> = emptyList(),
  val timerStatus: TimerStatus = TimerStatus(),
  val stopwatchStatus: StopwatchStatus = StopwatchStatus(),
  val calculationHistory: List<CalculationRecord> = emptyList(),
  val isTorchOn: Boolean = false,
  val pendingPhoneConfirmation: PendingPhoneConfirmation? = null,
  val appState: AppState = AppState.Dormant,
  val appAvailabilities: Map<SupportedApp, AppAvailability> = emptyMap(),
  val pendingAppConfirmation: PendingAppConfirmation? = null,
  val fileState: FileState = FileState.Dormant,
  val sessionFiles: List<MAXFileItem> = emptyList(),
  val activeFileItem: MAXFileItem? = null,
  val activeFileContent: ExtractedDocumentContent? = null,
  val lastFileOcrResult: OcrResult? = null,
  val pendingFileConfirmation: PendingFileConfirmation? = null,
  val filePickerLaunchToken: Long = 0L,
  val smartState: SmartFeatureState = SmartFeatureState.Idle,
  val translationHistory: List<TranslationRecord> = emptyList(),
  val lastTranslation: TranslationRecord? = null,
  val reminders: List<ReminderItem> = emptyList(),
  val smartNotes: List<SmartNoteItem> = emptyList(),
  val calendarEvents: List<CalendarEventItem> = emptyList(),
  val calendarSummary: String = "",
  val voiceNotes: List<VoiceNoteItem> = emptyList(),
  val aiSuggestions: List<AISuggestionItem> = emptyList(),
  val logs: List<com.example.utils.LogEntry> = emptyList(),
  val selectedLogFilter: LogLevel? = null,
  val settings: MAXSettings = MAXSettings(),
  val modules: List<ModuleDescriptor> = emptyList(),
  val compatibility: DeviceCompatibilityProfile? = null,
  val lastWakeWordEvent: String? = null,
  val followUpPrompt: String? = null,
  val lastCompletedTask: String? = null,
  val isThinking: Boolean = false,
  val updateState: com.example.update.UpdateState = com.example.update.UpdateState.Idle("1.0", 1),
  val brainState: com.example.brain.BrainLifecycleState = com.example.brain.BrainLifecycleState.Dormant,
  val routines: List<com.example.automation.AutomationRoutine> = emptyList(),
  val automationState: com.example.automation.AutomationState = com.example.automation.AutomationState.Idle,
  val lastBriefing: com.example.automation.DailyBriefingSummary? = null,
  val floatingState: com.example.floating.FloatingState = com.example.floating.FloatingState.Inactive,
)
