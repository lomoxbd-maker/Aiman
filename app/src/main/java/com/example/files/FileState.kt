package com.example.files

/**
 * Supported user-selected file categories inside MAX's privacy-locked Files System.
 */
enum class FileKind(val displayLabel: String) {
  PDF("PDF"),
  TEXT_DOCUMENT("Document"),
  IMAGE("Image"),
  OTHER("File"),
}

/**
 * Metadata representation of a file explicitly selected by the user via Storage Access Framework.
 * MAX never indexes or scans device directories in the background.
 */
data class MAXFileItem(
  val id: String,
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val kind: FileKind,
  val addedAtMillis: Long,
  val isReadOnly: Boolean = true,
  val extractedTextPreview: String? = null,
)

/**
 * Result produced by [PDFReader], [DocumentReader], or [ImageReader] after explicit user selection.
 */
data class ExtractedDocumentContent(
  val fileId: String,
  val fileName: String,
  val kind: FileKind,
  val text: String,
  val pageCount: Int = 1,
  val wordCount: Int = 0,
  val widthPx: Int = 0,
  val heightPx: Int = 0,
  val summaryNote: String = "",
)

/**
 * Structured OCR output generated on-device without uploading user images or documents.
 */
data class OcrResult(
  val fileId: String,
  val fileName: String,
  val extractedText: String,
  val engineName: String,
  val isSuccess: Boolean,
  val statusMessage: String,
)

/**
 * Confirmation-gated file actions so user files are never deleted or modified silently.
 */
sealed interface FileConfirmationAction {
  data class DeleteFileItem(
    val item: MAXFileItem,
  ) : FileConfirmationAction

  data class ModifyOrSaveDocumentCopy(
    val item: MAXFileItem,
    val updatedText: String,
  ) : FileConfirmationAction
}

/**
 * Pending interactive confirmation shown to the user before removing or modifying a file entry.
 */
data class PendingFileConfirmation(
  val id: String,
  val titleBangla: String,
  val titleEnglish: String,
  val messageBangla: String,
  val messageEnglish: String,
  val action: FileConfirmationAction,
)

/**
 * Reactive state machine for the MAX Files System.
 * Begins in [Dormant] and accesses documents strictly after explicit user action.
 */
sealed class FileState {
  data object Dormant : FileState()

  data class PermissionRequired(
    val reason: String,
  ) : FileState()

  data class BlockedByPrivacyPolicy(
    val reason: String,
  ) : FileState()

  data object ReadyToBrowse : FileState()

  data class Reading(
    val fileName: String,
  ) : FileState()

  data class BrowsingFiles(
    val items: List<MAXFileItem>,
    val activeItem: MAXFileItem? = null,
    val activeContent: ExtractedDocumentContent? = null,
    val ocrResult: OcrResult? = null,
  ) : FileState()

  data class AwaitingConfirmation(
    val pending: PendingFileConfirmation,
  ) : FileState()

  data class Error(
    val message: String,
  ) : FileState()
}
