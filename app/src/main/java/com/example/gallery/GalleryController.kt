package com.example.gallery

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/**
 * Hook interface preparing future visual/semantic image search across user-selected photos.
 */
interface GalleryImageSearchEngine {
  val engineName: String

  fun search(
    items: List<GalleryMediaItem>,
    query: String,
    filter: GalleryFilter,
  ): ImageSearchResult
}

/**
 * Hook interface preparing future on-device OCR text extraction for a user-selected image.
 */
interface GalleryOcrEngine {
  val engineName: String

  fun prepareAndAnalyze(item: GalleryMediaItem): OcrPreparationResult
}

/**
 * Interface defining MAX Gallery operations across Core, Repository, ViewModel, and UI.
 *
 * Privacy contract:
 * - Never scans device storage in the background.
 * - Never deletes any item without explicit confirmation.
 */
interface GalleryController {
  val state: StateFlow<GalleryState>
  val selectedItems: StateFlow<List<GalleryMediaItem>>
  val filteredItems: StateFlow<List<GalleryMediaItem>>
  val activeItem: StateFlow<GalleryMediaItem?>
  val searchQuery: StateFlow<String>
  val activeFilter: StateFlow<GalleryFilter>
  val pendingDeleteConfirmation: StateFlow<PendingDeleteConfirmation?>
  val lastOcrResult: StateFlow<OcrPreparationResult?>
  val lastSearchResult: StateFlow<ImageSearchResult?>

  fun requestOpenGallery(initiatedByUser: Boolean): Boolean

  fun onMediaPicked(uris: List<Uri>)

  fun addCapturedMedia(
    uriString: String,
    displayName: String,
    mimeType: String,
    tags: List<String> = emptyList(),
  )

  fun selectMediaItem(itemId: String)

  fun findPhotos(query: String, filter: GalleryFilter = activeFilter.value): ImageSearchResult

  fun prepareImageSearch(query: String): ImageSearchResult

  fun prepareOcrForSelectedPhoto(itemId: String): OcrPreparationResult

  fun requestDeleteMedia(itemId: String)

  fun confirmDeleteMedia(): Boolean

  fun cancelDeleteMedia()

  fun closeGallery()

  fun release()
}
