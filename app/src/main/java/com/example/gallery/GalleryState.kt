package com.example.gallery

/**
 * Filter categories for browsing and finding user-selected photos and videos.
 */
enum class GalleryFilter(val displayName: String) {
  ALL("All Media"),
  IMAGES("Photos"),
  VIDEOS("Videos"),
}

/**
 * Lightweight descriptor of a user-selected or user-captured media item.
 * Stored strictly in bounded in-memory session state (no background gallery crawling).
 */
data class GalleryMediaItem(
  val id: String,
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val addedAtMillis: Long,
  val extractedOcrText: String? = null,
  val searchTags: List<String> = emptyList(),
) {
  val isVideo: Boolean
    get() = mimeType.startsWith("video/", ignoreCase = true)
}

/**
 * Pending explicit confirmation before removing or deleting any item from MAX's gallery session.
 */
data class PendingDeleteConfirmation(
  val item: GalleryMediaItem,
  val warningMessageBangla: String,
  val warningMessageEnglish: String,
)

/**
 * Prepared image search result matching across user-selected photos & tags.
 */
data class ImageSearchResult(
  val query: String,
  val matches: List<GalleryMediaItem>,
  val capabilityReady: Boolean,
  val statusMessage: String,
)

/**
 * Prepared OCR extraction output for a selected photo, ready for on-device OCR plugins.
 */
data class OcrPreparationResult(
  val itemId: String,
  val itemDisplayName: String,
  val extractedText: String?,
  val isEngineReady: Boolean,
  val statusMessage: String,
)

/**
 * Lifecycle & operational states for MAX Gallery System.
 */
sealed class GalleryState {
  data object Dormant : GalleryState()

  data class PermissionRequired(val explanation: String) : GalleryState()

  data class BlockedByPrivacyPolicy(val reason: String) : GalleryState()

  data object ReadyToPick : GalleryState()

  data class DisplayingMedia(
    val visibleItems: List<GalleryMediaItem>,
    val activeItem: GalleryMediaItem?,
    val searchQuery: String,
    val filter: GalleryFilter,
  ) : GalleryState()

  data class ConfirmingDeletion(
    val pending: PendingDeleteConfirmation,
  ) : GalleryState()

  data class OcrPrepared(
    val result: OcrPreparationResult,
  ) : GalleryState()

  data class Error(val message: String) : GalleryState()
}
