package com.example.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.utils.ErrorTracker
import java.util.UUID

/**
 * Testable Storage Access Framework (SAF) metadata backend.
 * Reads ONLY URIs explicitly returned by the Android system picker.
 */
interface FileMetadataBackend {
  fun inspectUri(uri: Uri): MAXFileItem?
}

/**
 * Production SAF metadata reader querying OpenableColumns on user-picked URIs only.
 */
class AndroidFileMetadataBackend(
  private val context: Context,
) : FileMetadataBackend {

  override fun inspectUri(uri: Uri): MAXFileItem? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: inferMimeFromPath(uri.toString())
    var displayName = uri.lastPathSegment ?: "Selected_File"
    var sizeBytes = 0L

    runCatching {
      resolver.query(
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

    return MAXFileItem(
      id = UUID.randomUUID().toString(),
      uriString = uri.toString(),
      displayName = displayName,
      mimeType = mime,
      sizeBytes = sizeBytes,
      kind = classifyFileKind(displayName, mime),
      addedAtMillis = System.currentTimeMillis(),
    )
  }

  companion object {
    fun inferMimeFromPath(path: String): String {
      val lower = path.lowercase()
      return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".txt") || lower.endsWith(".log") -> "text/plain"
        lower.endsWith(".md") -> "text/markdown"
        lower.endsWith(".csv") -> "text/csv"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
      }
    }

    fun classifyFileKind(name: String, mime: String): FileKind {
      val lowerName = name.lowercase()
      val lowerMime = mime.lowercase()
      return when {
        lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> FileKind.PDF
        lowerMime.startsWith("image/") ||
          lowerName.endsWith(".jpg") ||
          lowerName.endsWith(".jpeg") ||
          lowerName.endsWith(".png") ||
          lowerName.endsWith(".webp") -> FileKind.IMAGE
        lowerMime.startsWith("text/") ||
          lowerMime == "application/json" ||
          lowerName.endsWith(".txt") ||
          lowerName.endsWith(".md") ||
          lowerName.endsWith(".csv") ||
          lowerName.endsWith(".json") ||
          lowerName.endsWith(".xml") ||
          lowerName.endsWith(".log") ||
          lowerName.endsWith(".html") -> FileKind.TEXT_DOCUMENT
        else -> FileKind.OTHER
      }
    }
  }
}

/**
 * FileBrowser — Safely manages user-selected SAF files in bounded session RAM.
 * Privacy rule: Never enumerates folders or reads device storage automatically.
 */
class FileBrowser(
  private val backend: FileMetadataBackend,
  private val errorTracker: ErrorTracker,
  maxSessionFiles: Int = 24,
) {

  private val maxFiles = maxSessionFiles.coerceIn(6, 48)

  fun addSelectedUris(
    existing: List<MAXFileItem>,
    uris: List<Uri>,
  ): List<MAXFileItem> {
    if (uris.isEmpty()) return existing
    val inspected = uris.mapNotNull { uri -> backend.inspectUri(uri) }
    val merged = (inspected + existing).distinctBy { it.uriString }.take(maxFiles)
    errorTracker.logInfo(MODULE_NAME, "Loaded ${inspected.size} user-selected SAF file(s).")
    return merged
  }

  fun filterSessionFiles(
    items: List<MAXFileItem>,
    query: String,
    kindFilter: FileKind? = null,
  ): List<MAXFileItem> {
    val cleanQuery = query.trim().lowercase()
    return items.filter { item ->
      val kindMatches = kindFilter == null || item.kind == kindFilter
      val queryMatches =
        cleanQuery.isEmpty() ||
          item.displayName.lowercase().contains(cleanQuery) ||
          item.mimeType.lowercase().contains(cleanQuery)
      kindMatches && queryMatches
    }
  }

  companion object {
    private const val MODULE_NAME = "FileBrowser"
  }
}
