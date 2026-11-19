package com.example.files

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.utils.ErrorTracker

/**
 * Testable image metadata backend.
 */
interface ImageReaderBackend {
  fun inspectImage(uri: Uri): Result<ImageDimensions>
}

data class ImageDimensions(
  val widthPx: Int,
  val heightPx: Int,
  val mimeType: String,
)

/**
 * Production Android image metadata reader using `inJustDecodeBounds = true`
 * so image dimensions are inspected with near-zero RAM allocation.
 */
class AndroidImageReaderBackend(
  private val context: Context,
) : ImageReaderBackend {

  override fun inspectImage(uri: Uri): Result<ImageDimensions> {
    return try {
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
      } ?: return Result.failure(IllegalStateException("Could not open image stream."))

      val width = opts.outWidth.coerceAtLeast(0)
      val height = opts.outHeight.coerceAtLeast(0)
      val mime = opts.outMimeType ?: (context.contentResolver.getType(uri) ?: "image/*")
      Result.success(ImageDimensions(width, height, mime))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

/**
 * ImageReader — Inspects user-selected image files without loading oversized bitmaps into RAM.
 */
class ImageReader(
  private val backend: ImageReaderBackend,
  private val errorTracker: ErrorTracker,
) {

  fun readImage(item: MAXFileItem): Result<ExtractedDocumentContent> {
    val uri = runCatching { Uri.parse(item.uriString) }.getOrNull()
      ?: return Result.failure(IllegalArgumentException("Invalid image URI."))

    return backend.inspectImage(uri).map { dims ->
      val summary =
        if (dims.widthPx > 0 && dims.heightPx > 0) {
          "Image '${item.displayName}' (${dims.widthPx}x${dims.heightPx}px, ${dims.mimeType}). Ready for preview or on-device OCR."
        } else {
          "Image '${item.displayName}' (${item.mimeType}) selected. Ready for preview or on-device OCR."
        }
      errorTracker.logInfo(MODULE_NAME, summary)
      ExtractedDocumentContent(
        fileId = item.id,
        fileName = item.displayName,
        kind = FileKind.IMAGE,
        text = item.extractedTextPreview ?: summary,
        pageCount = 1,
        widthPx = dims.widthPx,
        heightPx = dims.heightPx,
        summaryNote = summary,
      )
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Image inspection failed for '${item.displayName}': ${err.message}")
    }
  }

  companion object {
    private const val MODULE_NAME = "ImageReader"
  }
}
