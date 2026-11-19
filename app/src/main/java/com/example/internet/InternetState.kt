package com.example.internet

/**
 * Supported search verticals in MAX's Internet & Search System.
 */
enum class SearchCategory(val displayName: String, val displayBangla: String) {
  WEB("Web Search", "ওয়েব সার্চ"),
  WEBSITE("Website Search", "ওয়েবসাইট সার্চ"),
  NEWS("News Search", "খবর সার্চ"),
  WEATHER("Weather Search", "আবহাওয়া"),
  WIKIPEDIA("Wikipedia Search", "উইকিপিডিয়া"),
  YOUTUBE("YouTube Search", "ইউটিউব সার্চ"),
}

/**
 * Network condition classification used to adapt timeouts, payload size caps,
 * and battery usage cleanly on slow or metered connections.
 */
enum class ConnectionQuality(val label: String) {
  OFFLINE("Offline"),
  SLOW_OR_METERED("Slow / Metered"),
  NORMAL_WIFI_OR_FAST("Normal / Wi-Fi"),
}

/**
 * Outcome of MAX Safe Browsing URL verification before any network packet is sent.
 */
data class SafeUrlCheckResult(
  val originalUrl: String,
  val normalizedUrl: String,
  val isSafe: Boolean,
  val blockedReason: String? = null,
)

/**
 * Individual search result item surfaced to UI and conversational summaries.
 */
data class WebSearchResultItem(
  val title: String,
  val url: String,
  val snippet: String,
  val source: String,
  val category: SearchCategory,
)

/**
 * Cleanly extracted and summarized webpage payload kept strictly in volatile RAM.
 */
data class WebPageContent(
  val url: String,
  val title: String,
  val readableText: String,
  val summary: String,
  val fetchedAtMillis: Long,
  val byteCount: Int,
)

/**
 * Reactive state machine for MAX Internet and Browser operations.
 * Starts in [Dormant] and never initiates network traffic automatically.
 */
sealed class InternetState {
  data object Dormant : InternetState()

  data class PermissionRequired(val explanation: String) : InternetState()

  data class BlockedByPrivacyPolicy(val reason: String) : InternetState()

  data class OfflineOrUnavailable(val reason: String) : InternetState()

  data class Loading(
    val actionLabel: String,
    val targetQueryOrUrl: String,
    val connectionQuality: ConnectionQuality,
  ) : InternetState()

  data class SearchCompleted(
    val query: String,
    val category: SearchCategory,
    val results: List<WebSearchResultItem>,
    val summary: String,
  ) : InternetState()

  data class WebPageLoaded(
    val page: WebPageContent,
    val spokenSummary: String,
  ) : InternetState()

  data class Error(
    val message: String,
    val isRecoverable: Boolean = true,
  ) : InternetState()
}
