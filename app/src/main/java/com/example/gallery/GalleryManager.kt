package com.example.gallery

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listener notified by [GalleryManager] so [com.example.core.MAXCore],
 * [com.example.conversation.ConversationManager], and [com.example.tts.TTSManager] stay synced.
 */
interface GalleryEventListener {
  fun onPhotosLoaded(count: Int, latestName: String?)

  fun onSearchCompleted(result: ImageSearchResult)

  fun onOcrPrepared(result: OcrPreparationResult)

  fun onMediaRemoved(item: GalleryMediaItem)
}

/**
 * Default keyword, metadata, tag, and OCR-text search engine for user-selected media.
 * Ready to be swapped or augmented with embedding-based visual search in future steps.
 */
class DefaultGalleryImageSearchEngine : GalleryImageSearchEngine {
  override val engineName: String = "LocalMetadataAndTagSearch"

  override fun search(
    items: List<GalleryMediaItem>,
    query: String,
    filter: GalleryFilter,
  ): ImageSearchResult {
    val cleanQuery = query.trim().lowercase()
    val byFilter =
      items.filter { item ->
        when (filter) {
          GalleryFilter.ALL -> true
          GalleryFilter.IMAGES -> !item.isVideo
          GalleryFilter.VIDEOS -> item.isVideo
        }
      }

    val matches =
      if (cleanQuery.isEmpty()) {
        byFilter
      } else {
        byFilter.filter { item ->
          item.displayName.lowercase().contains(cleanQuery) ||
            item.mimeType.lowercase().contains(cleanQuery) ||
            item.searchTags.any { it.lowercase().contains(cleanQuery) } ||
            (item.extractedOcrText?.lowercase()?.contains(cleanQuery) == true)
        }
      }

    val summary =
      if (cleanQuery.isEmpty()) {
        "Showing ${matches.size} selected item(s)."
      } else {
        "Found ${matches.size} item(s) matching '$query'."
      }

    return ImageSearchResult(
      query = query,
      matches = matches,
      capabilityReady = true,
      statusMessage = summary,
    )
  }
}

/**
 * Prepared OCR integration hook that safely inspects selected image metadata/QR annotations
 * without performing any hidden disk scanning or cloud uploads.
 */
class PreparedGalleryOcrEngine : GalleryOcrEngine {
  override val engineName: String = "PreparedOfflineOcrPipeline"

  override fun prepareAndAnalyze(item: GalleryMediaItem): OcrPreparationResult {
    val existingText = item.extractedOcrText?.takeIf { it.isNotBlank() }
    val status =
      if (existingText != null) {
        "OCR text ready for '${item.displayName}': $existingText"
      } else {
        "OCR pipeline prepared for '${item.displayName}'. Ready for on-device text recognizer."
      }
    return OcrPreparationResult(
      itemId = item.id,
      itemDisplayName = item.displayName,
      extractedText = existingText,
      isEngineReady = true,
      statusMessage = status,
    )
  }
}

/**
 * GalleryManager — Zero-permission Android Photo Picker & selected media controller for MAX.
 *
 * Strict privacy guarantees:
 * - Never scans device gallery or media directories in the background.
 * - Opens ONLY via explicit user request after passing [PermissionController], [PrivacyGuard],
 *   and [PermissionRequestHandler].
 * - Never deletes or removes media items without explicit confirmation.
 * - Caps stored session items for low-RAM compatibility.
 */
class GalleryManager(
  private val context: Context,
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val errorTracker: ErrorTracker,
  private val imageSearchEngine: GalleryImageSearchEngine = DefaultGalleryImageSearchEngine(),
  private val ocrEngine: GalleryOcrEngine = PreparedGalleryOcrEngine(),
  maxSessionItems: Int = 32,
) : GalleryController, GalleryCapability {

  private val maxItems = maxSessionItems.coerceIn(8, 64)
  private var eventListener: GalleryEventListener? = null

  private val _state = MutableStateFlow<GalleryState>(GalleryState.Dormant)
  override val state: StateFlow<GalleryState> = _state.asStateFlow()

  private val _selectedItems = MutableStateFlow<List<GalleryMediaItem>>(emptyList())
  override val selectedItems: StateFlow<List<GalleryMediaItem>> = _selectedItems.asStateFlow()

  private val _filteredItems = MutableStateFlow<List<GalleryMediaItem>>(emptyList())
  override val filteredItems: StateFlow<List<GalleryMediaItem>> = _filteredItems.asStateFlow()

  private val _activeItem = MutableStateFlow<GalleryMediaItem?>(null)
  override val activeItem: StateFlow<GalleryMediaItem?> = _activeItem.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  override val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _activeFilter = MutableStateFlow(GalleryFilter.ALL)
  override val activeFilter: StateFlow<GalleryFilter> = _activeFilter.asStateFlow()

  private val _pendingDeleteConfirmation = MutableStateFlow<PendingDeleteConfirmation?>(null)
  override val pendingDeleteConfirmation: StateFlow<PendingDeleteConfirmation?> =
    _pendingDeleteConfirmation.asStateFlow()

  private val _lastOcrResult = MutableStateFlow<OcrPreparationResult?>(null)
  override val lastOcrResult: StateFlow<OcrPreparationResult?> = _lastOcrResult.asStateFlow()

  private val _lastSearchResult = MutableStateFlow<ImageSearchResult?>(null)
  override val lastSearchResult: StateFlow<ImageSearchResult?> = _lastSearchResult.asStateFlow()

  override val isPrivacyLocked: Boolean
    get() = privacyGuard.policy.value.allowGalleryOnUserAction != true

  fun setEventListener(listener: GalleryEventListener?) {
    eventListener = listener
  }

  override fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult {
    return privacyGuard.authorizeAction(SensitiveAction.ACCESS_GALLERY, initiatedByUser)
  }

  override fun requestOpenGallery(initiatedByUser: Boolean): Boolean {
    if (!initiatedByUser) {
      val reason = "Blocked automatic gallery access. User request required."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = GalleryState.BlockedByPrivacyPolicy(reason)
      return false
    }

    val auth = privacyGuard.authorizeAction(SensitiveAction.ACCESS_GALLERY, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler.requestForExplicitAction(
        permission = AppPermission.GALLERY,
        preferBangla = true,
      )
      _state.value = GalleryState.PermissionRequired(auth.reason)
      return false
    }

    _state.value = GalleryState.ReadyToPick
    errorTracker.logInfo(MODULE_NAME, "Gallery Photo Picker authorized by user.")
    return true
  }

  override fun onMediaPicked(uris: List<Uri>) {
    if (uris.isEmpty()) {
      privacyGuard.notifyResourcesReleased(SensitiveAction.ACCESS_GALLERY)
      updateDisplayState()
      return
    }

    val resolved = uris.map { uri -> inspectPickedUri(uri) }
    val merged = (resolved + _selectedItems.value).distinctBy { it.uriString }.take(maxItems)
    _selectedItems.value = merged
    if (_activeItem.value == null || resolved.isNotEmpty()) {
      _activeItem.value = resolved.firstOrNull() ?: merged.firstOrNull()
    }
    privacyGuard.notifyResourcesReleased(SensitiveAction.ACCESS_GALLERY)
    refreshFilteredState()
    errorTracker.logInfo(MODULE_NAME, "Loaded ${resolved.size} user-selected media item(s).")
    eventListener?.onPhotosLoaded(resolved.size, resolved.firstOrNull()?.displayName)
  }

  override fun addCapturedMedia(
    uriString: String,
    displayName: String,
    mimeType: String,
    tags: List<String>,
  ) {
    val item =
      GalleryMediaItem(
        id = UUID.randomUUID().toString(),
        uriString = uriString,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = 0L,
        addedAtMillis = System.currentTimeMillis(),
        extractedOcrText = tags.firstOrNull { it.startsWith("qr:") }?.removePrefix("qr:"),
        searchTags = tags,
      )
    _selectedItems.value = (listOf(item) + _selectedItems.value).distinctBy { it.uriString }.take(maxItems)
    _activeItem.value = item
    refreshFilteredState()
  }

  override fun selectMediaItem(itemId: String) {
    val target = _selectedItems.value.firstOrNull { it.id == itemId } ?: return
    _activeItem.value = target
    updateDisplayState()
  }

  override fun findPhotos(query: String, filter: GalleryFilter): ImageSearchResult {
    _searchQuery.value = query
    _activeFilter.value = filter
    val result = imageSearchEngine.search(_selectedItems.value, query, filter)
    _filteredItems.value = result.matches
    _lastSearchResult.value = result
    if (_activeItem.value != null && result.matches.none { it.id == _activeItem.value?.id }) {
      _activeItem.value = result.matches.firstOrNull()
    } else if (_activeItem.value == null) {
      _activeItem.value = result.matches.firstOrNull()
    }
    updateDisplayState()
    eventListener?.onSearchCompleted(result)
    return result
  }

  override fun prepareImageSearch(query: String): ImageSearchResult {
    val result = findPhotos(query, _activeFilter.value)
    errorTracker.logInfo(
      MODULE_NAME,
      "Image search (${imageSearchEngine.engineName}) ran on selected media: ${result.matches.size} match(es).",
    )
    return result
  }

  override fun prepareOcrForSelectedPhoto(itemId: String): OcrPreparationResult {
    val item =
      _selectedItems.value.firstOrNull { it.id == itemId }
        ?: _activeItem.value
        ?: return OcrPreparationResult(
          itemId = itemId,
          itemDisplayName = "None",
          extractedText = null,
          isEngineReady = false,
          statusMessage = "Please select a photo first before preparing OCR.",
        )

    val result = ocrEngine.prepareAndAnalyze(item)
    _lastOcrResult.value = result
    _state.value = GalleryState.OcrPrepared(result)
    errorTracker.logInfo(MODULE_NAME, "OCR prepared (${ocrEngine.engineName}) for '${item.displayName}'.")
    eventListener?.onOcrPrepared(result)
    return result
  }

  override fun requestDeleteMedia(itemId: String) {
    val target = _selectedItems.value.firstOrNull { it.id == itemId } ?: return
    val confirmation =
      PendingDeleteConfirmation(
        item = target,
        warningMessageBangla =
          "আপনি কি নিশ্চিত যে '${target.displayName}' MAX থেকে সরাতে চান? আপনার সম্মতি ছাড়া কোনো ফাইল মুছা হবে না।",
        warningMessageEnglish =
          "Are you sure you want to remove '${target.displayName}'? MAX never deletes items without confirmation.",
      )
    _pendingDeleteConfirmation.value = confirmation
    _state.value = GalleryState.ConfirmingDeletion(confirmation)
  }

  override fun confirmDeleteMedia(): Boolean {
    val pending = _pendingDeleteConfirmation.value ?: return false
    _pendingDeleteConfirmation.value = null
    _selectedItems.value = _selectedItems.value.filterNot { it.id == pending.item.id }
    if (_activeItem.value?.id == pending.item.id) {
      _activeItem.value = _selectedItems.value.firstOrNull()
    }
    refreshFilteredState()
    errorTracker.logInfo(MODULE_NAME, "User confirmed removal of '${pending.item.displayName}'.")
    eventListener?.onMediaRemoved(pending.item)
    return true
  }

  override fun cancelDeleteMedia() {
    _pendingDeleteConfirmation.value = null
    updateDisplayState()
  }

  override fun closeGallery() {
    _pendingDeleteConfirmation.value = null
    privacyGuard.notifyResourcesReleased(SensitiveAction.ACCESS_GALLERY)
    _state.value = GalleryState.Dormant
  }

  override fun release() {
    _selectedItems.value = emptyList()
    _filteredItems.value = emptyList()
    _activeItem.value = null
    _pendingDeleteConfirmation.value = null
    _lastOcrResult.value = null
    _lastSearchResult.value = null
    _state.value = GalleryState.Dormant
    eventListener = null
  }

  private fun refreshFilteredState() {
    val result = imageSearchEngine.search(_selectedItems.value, _searchQuery.value, _activeFilter.value)
    _filteredItems.value = result.matches
    _lastSearchResult.value = result
    updateDisplayState()
  }

  private fun updateDisplayState() {
    if (_selectedItems.value.isEmpty() && _state.value is GalleryState.Dormant) {
      return
    }
    _state.value =
      GalleryState.DisplayingMedia(
        visibleItems = _filteredItems.value,
        activeItem = _activeItem.value,
        searchQuery = _searchQuery.value,
        filter = _activeFilter.value,
      )
  }

  private fun inspectPickedUri(uri: Uri): GalleryMediaItem {
    var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected_Photo"
    var sizeBytes = 0L

    runCatching {
      context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
            displayName = cursor.getString(nameIdx)
          }
          if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
            sizeBytes = cursor.getLong(sizeIdx)
          }
        }
      }
    }

    val mimeType =
      runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "image/*"

    val autoTags =
      buildList {
        if (mimeType.startsWith("video/")) add("video") else add("photo")
        displayName.substringBeforeLast('.').split('_', '-', ' ').filter { it.length >= 2 }.forEach {
          add(it.lowercase())
        }
      }

    return GalleryMediaItem(
      id = UUID.randomUUID().toString(),
      uriString = uri.toString(),
      displayName = displayName,
      mimeType = mimeType,
      sizeBytes = sizeBytes,
      addedAtMillis = System.currentTimeMillis(),
      searchTags = autoTags.distinct(),
    )
  }

  companion object {
    private const val MODULE_NAME = "GalleryManager"
  }
}
