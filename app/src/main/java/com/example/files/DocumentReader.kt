package com.example.files

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.utils.ErrorTracker
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Testable text document reading abstraction.
 */
interface DocumentReaderBackend {
  fun readText(uri: Uri, mimeType: String, maxChars: Int): Result<String>
}

/**
 * Production Android SAF read-only document text reader supporting .txt, .md, .csv, .json, .html, etc.
 * Caps buffered reads so large files never cause OutOfMemoryError on low-RAM Android devices.
 */
class AndroidDocumentReaderBackend(
  private val context: Context,
) : DocumentReaderBackend {

  override fun readText(uri: Uri, mimeType: String, maxChars: Int): Result<String> {
    return try {
      val stream =
        context.contentResolver.openInputStream(uri)
          ?: return Result.failure(IllegalStateException("Unable to open document stream."))
      val rawText =
        stream.use { input ->
          BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(2048)
            while (sb.length < maxChars) {
              val read = reader.read(buf, 0, minOf(buf.size, maxChars - sb.length))
              if (read <= 0) break
              sb.append(buf, 0, read)
            }
            sb.toString()
          }
        }
      val cleaned =
        if (mimeType.contains("html", ignoreCase = true)) {
          rawText
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        } else {
          rawText.trim()
        }
      Result.success(cleaned)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

/**
 * DocumentReader — Reads text documents strictly on explicit user command (never modifies files).
 */
class DocumentReader(
  private val backend: DocumentReaderBackend,
  private val errorTracker: ErrorTracker,
  private val isLowRamDevice: Boolean = false,
) {

  fun readDocument(item: MAXFileItem): Result<ExtractedDocumentContent> {
    val uri = runCatching { item.uriString.toUri() }.getOrNull()
      ?: return Result.failure(IllegalArgumentException("Invalid document URI."))
    val maxChars = if (isLowRamDevice) 10_000 else 24_000

    return backend.readText(uri, item.mimeType, maxChars).map { text ->
      val finalText = text.ifBlank { "(Empty text document: ${item.displayName})" }
      val words = finalText.split(WHITESPACE_REGEX).count { it.isNotBlank() }
      errorTracker.logInfo(MODULE_NAME, "Read document '${item.displayName}' ($words words, read-only).")
      ExtractedDocumentContent(
        fileId = item.id,
        fileName = item.displayName,
        kind = FileKind.TEXT_DOCUMENT,
        text = finalText,
        pageCount = 1,
        wordCount = words,
        summaryNote = "Read document safely (strictly read-only).",
      )
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Failed reading '${item.displayName}': ${err.message}")
    }
  }

  companion object {
    private const val MODULE_NAME = "DocumentReader"
    private val WHITESPACE_REGEX = Regex("\\s+")
  }
}
