package com.example.files

import android.net.Uri
import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface FileEventListener {
  fun onFilesAnnouncement(
    bangla: String,
    english: String,
    speakAloud: Boolean = true,
  )
}

interface FileController : FileCapability {
  val state: StateFlow<FileState>
  val files: StateFlow<List<MAXFileItem>>
  val filteredFiles: StateFlow<List<MAXFileItem>>
  val activeFile: StateFlow<MAXFileItem?>
  val activeContent: StateFlow<ExtractedDocumentContent?>
  val lastOcrResult: StateFlow<OcrResult?>
  val pendingConfirmation: StateFlow<PendingFileConfirmation?>

  fun requestOpenFilePicker(initiatedByUser: Boolean, preferBangla: Boolean = true): Boolean

  fun onFilesPicked(uris: List<Uri>)

  fun filterFiles(query: String, kindFilter: FileKind? = null)

  fun openAndReadFile(itemId: String, initiatedByUser: Boolean): Boolean

  fun runOcrOnFile(itemId: String, initiatedByUser: Boolean): OcrResult?

  fun requestDeleteFile(itemId: String)

  fun requestModifyFileContent(
    itemId: String,
    updatedText: String,
    hasExplicitPermission: Boolean,
  ): Boolean

  fun confirmPendingFileAction(): Boolean

  fun cancelPendingFileAction()

  fun closeFilesSession()

  fun release()
}

/**
 * FileManager — Coordinates MAX's Storage Access Framework browsing, PDF/Text/Image reading,
 * and on-device OCR extraction without ever accessing files automatically or uploading data.
 */
class FileManager(
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val fileBrowser: FileBrowser,
  private val pdfReader: PDFReader,
  private val documentReader: DocumentReader,
  private val imageReader: ImageReader,
  private val ocrManager: OCRManager,
  private val errorTracker: ErrorTracker,
) : FileController {

  private val _state = MutableStateFlow<FileState>(FileState.Dormant)
  override val state: StateFlow<FileState> = _state.asStateFlow()

  private val _files = MutableStateFlow<List<MAXFileItem>>(emptyList())
  override val files: StateFlow<List<MAXFileItem>> = _files.asStateFlow()

  private val _filteredFiles = MutableStateFlow<List<MAXFileItem>>(emptyList())
  override val filteredFiles: StateFlow<List<MAXFileItem>> = _filteredFiles.asStateFlow()

  private val _activeFile = MutableStateFlow<MAXFileItem?>(null)
  override val activeFile: StateFlow<MAXFileItem?> = _activeFile.asStateFlow()

  private val _activeContent = MutableStateFlow<ExtractedDocumentContent?>(null)
  override val activeContent: StateFlow<ExtractedDocumentContent?> = _activeContent.asStateFlow()

  private val _lastOcrResult = MutableStateFlow<OcrResult?>(null)
  override val lastOcrResult: StateFlow<OcrResult?> = _lastOcrResult.asStateFlow()

  private val _pendingConfirmation = MutableStateFlow<PendingFileConfirmation?>(null)
  override val pendingConfirmation: StateFlow<PendingFileConfirmation?> =
    _pendingConfirmation.asStateFlow()

  private var currentQuery: String = ""
  private var currentKindFilter: FileKind? = null
  private var eventListener: FileEventListener? = null

  override val isPrivacyLocked: Boolean
    get() = privacyGuard.policy.value.allowFilesOnUserAction != true

  fun setEventListener(listener: FileEventListener?) {
    eventListener = listener
  }

  override fun requestOpenFilePicker(initiatedByUser: Boolean, preferBangla: Boolean): Boolean {
    if (!initiatedByUser) {
      val reason = "Blocked automatic file access. Files only open on explicit user request."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = FileState.BlockedByPrivacyPolicy(reason)
      return false
    }

    val auth = privacyGuard.authorizeAction(SensitiveAction.READ_FILES, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler.requestForExplicitAction(
        permission = AppPermission.FILES,
        preferBangla = preferBangla,
      )
      _state.value = FileState.PermissionRequired(auth.reason)
      return false
    }

    _state.value = FileState.ReadyToBrowse
    errorTracker.logInfo(MODULE_NAME, "Storage Access Framework picker authorized by user.")
    return true
  }

  override fun onFilesPicked(uris: List<Uri>) {
    if (uris.isEmpty()) {
      privacyGuard.notifyResourcesReleased(SensitiveAction.READ_FILES)
      publishBrowseState()
      return
    }
    val merged = fileBrowser.addSelectedUris(_files.value, uris)
    _files.value = merged
    if (_activeFile.value == null || merged.isNotEmpty()) {
      _activeFile.value = merged.firstOrNull()
    }
    privacyGuard.notifyResourcesReleased(SensitiveAction.READ_FILES)
    filterFiles(currentQuery, currentKindFilter)
    val latest = merged.firstOrNull()
    if (latest != null) {
      openAndReadFile(latest.id, initiatedByUser = true)
    } else {
      publishBrowseState()
    }
  }

  override fun filterFiles(query: String, kindFilter: FileKind?) {
    currentQuery = query
    currentKindFilter = kindFilter
    _filteredFiles.value = fileBrowser.filterSessionFiles(_files.value, query, kindFilter)
    publishBrowseState()
  }

  override fun openAndReadFile(itemId: String, initiatedByUser: Boolean): Boolean {
    if (!initiatedByUser) {
      val reason = "Blocked automatic reading of user document."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = FileState.BlockedByPrivacyPolicy(reason)
      return false
    }

    val auth = privacyGuard.authorizeAction(SensitiveAction.READ_FILES, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      _state.value = FileState.PermissionRequired(auth.reason)
      return false
    }

    val item = _files.value.firstOrNull { it.id == itemId } ?: return false
    _activeFile.value = item
    _state.value = FileState.Reading(item.displayName)

    val readResult: Result<ExtractedDocumentContent> =
      when (item.kind) {
        FileKind.PDF -> pdfReader.readDocument(item)
        FileKind.TEXT_DOCUMENT -> documentReader.readDocument(item)
        FileKind.IMAGE -> imageReader.readImage(item)
        FileKind.OTHER ->
          documentReader.readDocument(item).recoverCatching {
            ExtractedDocumentContent(
              fileId = item.id,
              fileName = item.displayName,
              kind = FileKind.OTHER,
              text = "Selected file '${item.displayName}' (${item.mimeType}, ${item.sizeBytes} bytes).",
              summaryNote = "Binary or custom file metadata shown safely.",
            )
          }
      }

    return readResult.fold(
      onSuccess = { content ->
        _activeContent.value = content
        publishBrowseState()
        val bangla = "'${item.displayName}' পড়া সম্পন্ন হয়েছে (${content.wordCount} শব্দ)।"
        val english = "Finished reading '${item.displayName}' cleanly on-device."
        eventListener?.onFilesAnnouncement(bangla, english, speakAloud = true)
        true
      },
      onFailure = { err ->
        val msg = err.message ?: "Unable to read '${item.displayName}'."
        _state.value = FileState.Error(msg)
        eventListener?.onFilesAnnouncement(
          bangla = "'${item.displayName}' ফাইলটি পড়া যায়নি: $msg",
          english = "Could not read '${item.displayName}': $msg",
          speakAloud = true,
        )
        false
      },
    )
  }

  override fun runOcrOnFile(itemId: String, initiatedByUser: Boolean): OcrResult? {
    if (!initiatedByUser) {
      val reason = "Blocked automatic OCR scan on user files."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = FileState.BlockedByPrivacyPolicy(reason)
      return null
    }
    val item = _files.value.firstOrNull { it.id == itemId } ?: return null
    _activeFile.value = item

    val result = ocrManager.runOcr(item)
    _lastOcrResult.value = result
    if (result.isSuccess && result.extractedText.isNotBlank()) {
      _files.value =
        _files.value.map { existing ->
          if (existing.id == item.id) {
            existing.copy(extractedTextPreview = result.extractedText)
          } else {
            existing
          }
        }
      _activeContent.value =
        ExtractedDocumentContent(
          fileId = item.id,
          fileName = item.displayName,
          kind = item.kind,
          text = result.extractedText,
          wordCount = result.extractedText.split(Regex("\\s+")).count { it.isNotBlank() },
          summaryNote = "Extracted via on-device OCR (no cloud upload).",
        )
    }
    filterFiles(currentQuery, currentKindFilter)
    eventListener?.onFilesAnnouncement(
      bangla = "'${item.displayName}' থেকে OCR টেক্সট সংগ্রহ করা হয়েছে।",
      english = result.statusMessage,
      speakAloud = true,
    )
    return result
  }

  override fun requestDeleteFile(itemId: String) {
    val item = _files.value.firstOrNull { it.id == itemId } ?: return
    val pending =
      PendingFileConfirmation(
        id = "del_file_${item.id}_${System.currentTimeMillis()}",
        titleBangla = "ফাইল সরানোর নিশ্চিতকরণ",
        titleEnglish = "Remove File Entry?",
        messageBangla =
          "আপনি কি '${item.displayName}' MAX-এর তালিকা থেকে সরাতে চান? মূল ডিভাইস ফাইল মুছে ফেলা হবে না।",
        messageEnglish =
          "Remove '${item.displayName}' from MAX's session list? Your original file on storage stays untouched.",
        action = FileConfirmationAction.DeleteFileItem(item),
      )
    _pendingConfirmation.value = pending
    _state.value = FileState.AwaitingConfirmation(pending)
  }

  override fun requestModifyFileContent(
    itemId: String,
    updatedText: String,
    hasExplicitPermission: Boolean,
  ): Boolean {
    if (!hasExplicitPermission) {
      val reason = "Blocked file modification without explicit user permission."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = FileState.BlockedByPrivacyPolicy(reason)
      return false
    }
    val item = _files.value.firstOrNull { it.id == itemId } ?: return false
    val pending =
      PendingFileConfirmation(
        id = "mod_file_${item.id}_${System.currentTimeMillis()}",
        titleBangla = "ডকুমেন্ট পরিবর্তনের নিশ্চিতকরণ",
        titleEnglish = "Confirm Document Session Edit",
        messageBangla =
          "'${item.displayName}'-এর মূল ফাইলে হাত না দিয়ে MAX কেবল আপনার অনুমতিক্রমে একটি প্রিভিউ কপি আপডেট করবে। নিশ্চিত করবেন?",
        messageEnglish =
          "Update session preview copy for '${item.displayName}'? Original disk file remains read-only and unmodified.",
        action = FileConfirmationAction.ModifyOrSaveDocumentCopy(item, updatedText),
      )
    _pendingConfirmation.value = pending
    _state.value = FileState.AwaitingConfirmation(pending)
    return true
  }

  override fun confirmPendingFileAction(): Boolean {
    val pending = _pendingConfirmation.value ?: return false
    _pendingConfirmation.value = null
    return when (val action = pending.action) {
      is FileConfirmationAction.DeleteFileItem -> {
        _files.value = _files.value.filterNot { it.id == action.item.id }
        if (_activeFile.value?.id == action.item.id) {
          _activeFile.value = _files.value.firstOrNull()
          _activeContent.value = null
        }
        filterFiles(currentQuery, currentKindFilter)
        errorTracker.logInfo(MODULE_NAME, "Removed '${action.item.displayName}' after user confirmation.")
        eventListener?.onFilesAnnouncement(
          bangla = "'${action.item.displayName}' তালিকা থেকে সরানো হয়েছে।",
          english = "Removed '${action.item.displayName}' after confirmation.",
          speakAloud = false,
        )
        true
      }

      is FileConfirmationAction.ModifyOrSaveDocumentCopy -> {
        val target = action.item
        _files.value =
          _files.value.map { existing ->
            if (existing.id == target.id) {
              existing.copy(extractedTextPreview = action.updatedText)
            } else {
              existing
            }
          }
        _activeContent.value =
          ExtractedDocumentContent(
            fileId = target.id,
            fileName = target.displayName,
            kind = target.kind,
            text = action.updatedText,
            wordCount = action.updatedText.split(Regex("\\s+")).count { it.isNotBlank() },
            summaryNote = "Updated local preview copy by explicit user permission.",
          )
        filterFiles(currentQuery, currentKindFilter)
        true
      }
    }
  }

  override fun cancelPendingFileAction() {
    _pendingConfirmation.value = null
    publishBrowseState()
  }

  override fun closeFilesSession() {
    _pendingConfirmation.value = null
    _activeContent.value = null
    _lastOcrResult.value = null
    _activeFile.value = null
    _state.value = FileState.Dormant
    privacyGuard.notifyResourcesReleased(SensitiveAction.READ_FILES)
  }

  override fun release() {
    _files.value = emptyList()
    _filteredFiles.value = emptyList()
    _activeFile.value = null
    _activeContent.value = null
    _lastOcrResult.value = null
    _pendingConfirmation.value = null
    _state.value = FileState.Dormant
    eventListener = null
  }

  private fun publishBrowseState() {
    val visible =
      if (currentQuery.isBlank() && currentKindFilter == null) {
        _files.value
      } else {
        _filteredFiles.value
      }
    _filteredFiles.value = visible
    _state.value =
      if (_files.value.isEmpty()) {
        val policyOk = privacyGuard.policy.value.allowFilesOnUserAction
        if (policyOk) FileState.ReadyToBrowse else FileState.Dormant
      } else {
        FileState.BrowsingFiles(
          items = visible,
          activeItem = _activeFile.value,
          activeContent = _activeContent.value,
          ocrResult = _lastOcrResult.value,
        )
      }
  }

  companion object {
    private const val MODULE_NAME = "FileManager"
  }
}
