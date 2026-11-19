package com.example.files

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.utils.ErrorTracker
import java.nio.charset.StandardCharsets

/**
 * Testable PDF reading abstraction for Android native [PdfRenderer] and unit tests.
 */
interface PdfReaderBackend {
  fun readPdf(uri: Uri, maxChars: Int): Result<PdfReadOutput>
}

data class PdfReadOutput(
  val pageCount: Int,
  val extractedText: String,
  val note: String,
)

/**
 * Production PDF backend using Android's built-in [PdfRenderer] (API 21+, full API 26–36 safe)
 * paired with lightweight PDF literal text extraction. Opens descriptors strictly read-only ("r")
 * and releases file descriptors immediately in `use {}` blocks.
 */
class AndroidPdfReaderBackend(
  private val context: Context,
) : PdfReaderBackend {

  override fun readPdf(uri: Uri, maxChars: Int): Result<PdfReadOutput> {
    return try {
      val resolver = context.contentResolver
      val pageCount =
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            renderer.pageCount
          }
        } ?: return Result.failure(IllegalStateException("Could not open PDF file descriptor."))

      val rawBytes =
        resolver.openInputStream(uri)?.use { stream ->
          val buffer = ByteArray(minOf(256 * 1024, maxChars * 12))
          var offset = 0
          while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read <= 0) break
            offset += read
          }
          buffer.copyOf(offset)
        } ?: ByteArray(0)

      val extracted = extractReadablePdfStrings(rawBytes, maxChars)
      val note =
        if (extracted.isBlank()) {
          "Rendered $pageCount PDF page(s). This PDF appears image-based/scanned — use OCR to inspect scanned text."
        } else {
          "Read $pageCount PDF page(s) safely on-device."
        }
      Result.success(
        PdfReadOutput(
          pageCount = pageCount,
          extractedText = extracted,
          note = note,
        ),
      )
    } catch (se: SecurityException) {
      Result.failure(IllegalStateException("PDF is password-protected or access expired.", se))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  companion object {
    /**
     * Lightweight parser extracting printable literal text operators from uncompressed PDF streams
     * without allocating large heaps on low-RAM devices.
     */
    internal fun extractReadablePdfStrings(bytes: ByteArray, maxChars: Int): String {
      if (bytes.isEmpty()) return ""
      val raw = String(bytes, StandardCharsets.ISO_8859_1)
      val regex = Regex("\\(([^()\\\\]{2,240})\\)\\s*(Tj|'|\"|TJ)")
      val out = StringBuilder()
      regex.findAll(raw).forEach { match ->
        val token =
          match.groupValues[1]
            .replace("\\r", " ")
            .replace("\\n", " ")
            .replace("\\t", " ")
            .trim()
        val readable = token.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,?!-:" }
        if (token.isNotBlank() && readable * 2 >= token.length) {
          if (out.length + token.length + 1 <= maxChars) {
            out.append(token).append(' ')
          }
        }
      }
      return out.toString().replace(Regex("\\s+"), " ").trim()
    }
  }
}

/**
 * PDFReader — Safely reads user-selected PDF documents on-device with low memory overhead.
 */
class PDFReader(
  private val backend: PdfReaderBackend,
  private val errorTracker: ErrorTracker,
  private val isLowRamDevice: Boolean = false,
) {

  fun readDocument(item: MAXFileItem): Result<ExtractedDocumentContent> {
    val uri = runCatching { Uri.parse(item.uriString) }.getOrNull()
      ?: return Result.failure(IllegalArgumentException("Invalid PDF URI."))
    val charBudget = if (isLowRamDevice) 8_000 else 20_000

    val result = backend.readPdf(uri, charBudget)
    return result.map { output ->
      val text =
        output.extractedText.ifBlank {
          "PDF document '${item.displayName}' has ${output.pageCount} page(s). ${output.note}"
        }
      val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
      errorTracker.logInfo(MODULE_NAME, "Read PDF '${item.displayName}' (${output.pageCount} pages, $words words).")
      ExtractedDocumentContent(
        fileId = item.id,
        fileName = item.displayName,
        kind = FileKind.PDF,
        text = text,
        pageCount = output.pageCount,
        wordCount = words,
        summaryNote = output.note,
      )
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "PDF read warning for '${item.displayName}': ${err.message}")
    }
  }

  companion object {
    private const val MODULE_NAME = "PDFReader"
  }
}
