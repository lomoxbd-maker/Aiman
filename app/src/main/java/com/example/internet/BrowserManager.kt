package com.example.internet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BrowserManager — Safe webpage reader, content extractor, and summarizer for MAX.
 *
 * Security & privacy guarantees:
 * - Validates every URL with [WebRequestManager.validateSafeUrl] before loading or opening.
 * - Strips `<script>`, `<style>`, `<nav>`, and markup cleanly in RAM without background tracking.
 * - Never keeps a persistent browsing history log.
 */
class BrowserManager(
  private val context: Context,
  private val webRequestManager: WebRequestManager,
  private val errorTracker: ErrorTracker,
) {

  private val _currentPage = MutableStateFlow<WebPageContent?>(null)
  val currentPage: StateFlow<WebPageContent?> = _currentPage.asStateFlow()

  fun checkSafeUrl(rawUrl: String): SafeUrlCheckResult = webRequestManager.validateSafeUrl(rawUrl)

  /**
   * Reads a webpage over verified HTTPS, extracts human-readable text, and generates a concise
   * summary ready to be read aloud by [com.example.tts.TTSManager].
   */
  suspend fun openAndReadWebsite(
    rawUrl: String,
    preferBangla: Boolean = false,
  ): Result<WebPageContent> {
    val safeCheck = webRequestManager.validateSafeUrl(rawUrl)
    if (!safeCheck.isSafe) {
      return Result.failure(IllegalArgumentException(safeCheck.blockedReason ?: "Unsafe URL blocked."))
    }

    return when (val outcome = webRequestManager.fetchSafeText(safeCheck.normalizedUrl)) {
      is WebRequestOutcome.Success -> {
        val title = extractTitle(outcome.body, safeCheck.normalizedUrl)
        val cleanText = extractReadableText(outcome.body)
        val summary =
          summarizeText(
            rawText = cleanText,
            maxSentences = 3,
            preferBangla = preferBangla,
            title = title,
          )
        val page =
          WebPageContent(
            url = outcome.url,
            title = title,
            readableText = cleanText.take(MAX_READABLE_CHARS),
            summary = summary,
            fetchedAtMillis = System.currentTimeMillis(),
            byteCount = outcome.byteCount,
          )
        _currentPage.value = page
        errorTracker.logInfo(MODULE_NAME, "Loaded & summarized '${page.title}' (${page.byteCount} bytes).")
        Result.success(page)
      }
      is WebRequestOutcome.Failure -> {
        errorTracker.logWarning(MODULE_NAME, "Failed reading webpage: ${outcome.reason}")
        Result.failure(IllegalStateException(outcome.reason))
      }
    }
  }

  /**
   * Summarizes arbitrary article text or the currently loaded webpage cleanly into 2-3 calm sentences.
   */
  fun summarizeText(
    rawText: String,
    maxSentences: Int = 3,
    preferBangla: Boolean = false,
    title: String = "",
  ): String {
    val normalized = rawText.replace(Regex("\\s+"), " ").trim()
    if (normalized.isEmpty()) {
      return if (preferBangla) "এই পেজটিতে পড়ার মতো পর্যাপ্ত লেখা পাওয়া যায়নি।"
      else "No readable article text was found on this page."
    }

    val sentences =
      normalized
        .split(Regex("(?<=[।.!?])\\s+"))
        .map { it.trim() }
        .filter { it.length >= 25 }

    val picked =
      if (sentences.isNotEmpty()) {
        sentences.take(maxSentences.coerceIn(1, 5)).joinToString(" ")
      } else {
        normalized.take(280)
      }

    return if (title.isNotBlank() && !picked.startsWith(title, ignoreCase = true)) {
      "$title — $picked"
    } else {
      picked
    }
  }

  /**
   * Launches the user's system browser via `ACTION_VIEW` ONLY when [validateSafeUrl] passes.
   */
  fun launchSafeExternalBrowser(rawUrl: String): Result<String> {
    val safeCheck = webRequestManager.validateSafeUrl(rawUrl)
    if (!safeCheck.isSafe) {
      return Result.failure(IllegalArgumentException(safeCheck.blockedReason ?: "Unsafe URL blocked."))
    }

    return runCatching {
      val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(safeCheck.normalizedUrl)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      errorTracker.logInfo(MODULE_NAME, "Opened verified HTTPS link externally.")
      safeCheck.normalizedUrl
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "No browser app available: ${err.message}")
    }
  }

  fun clearSessionPage() {
    _currentPage.value = null
  }

  private fun extractTitle(html: String, fallbackUrl: String): String {
    val match = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
    val decoded = match?.let { decodeHtml(it) }?.replace(Regex("\\s+"), " ")?.trim()
    return if (!decoded.isNullOrBlank()) decoded.take(120) else fallbackUrl
  }

  private fun extractReadableText(html: String): String {
    val stripped =
      html
        .replace(SCRIPT_REGEX, " ")
        .replace(STYLE_REGEX, " ")
        .replace(NAV_REGEX, " ")
    val withoutTags = decodeHtml(stripped).replace(Regex("\\s+"), " ").trim()
    return withoutTags
  }

  private fun decodeHtml(input: String): String {
    return runCatching {
      Html.fromHtml(input, Html.FROM_HTML_MODE_COMPACT).toString()
    }.getOrElse {
      input.replace(Regex("<[^>]*>"), " ")
    }
  }

  companion object {
    private const val MODULE_NAME = "BrowserManager"
    private const val MAX_READABLE_CHARS = 4_000
    private val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_REGEX = Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val STYLE_REGEX = Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val NAV_REGEX = Regex("<nav[^>]*>.*?</nav>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  }
}
