package com.example.internet

import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Listener notified by [InternetManager] when search or webpage summaries complete so
 * [com.example.core.MAXCore], [com.example.conversation.ConversationManager], and
 * [com.example.tts.TTSManager] can deliver spoken & conversational answers.
 */
interface InternetEventListener {
  fun onSearchReady(
    query: String,
    category: SearchCategory,
    results: List<WebSearchResultItem>,
    summary: String,
  )

  fun onWebPageSummarized(page: WebPageContent, summary: String)

  fun onNetworkError(message: String)
}

/**
 * Contract for MAX's privacy-locked Internet & Browser subsystem.
 */
interface InternetController : NetworkCapability {
  val state: StateFlow<InternetState>
  val connectionQuality: StateFlow<ConnectionQuality>
  val activeCategory: StateFlow<SearchCategory>
  val searchResults: StateFlow<List<WebSearchResultItem>>
  val currentPage: StateFlow<WebPageContent?>
  val lastSummary: StateFlow<String?>

  fun refreshConnectionQuality(): ConnectionQuality

  fun search(
    query: String,
    category: SearchCategory = activeCategory.value,
    initiatedByUser: Boolean,
    preferBangla: Boolean = false,
  ): Boolean

  fun openWebsite(
    url: String,
    initiatedByUser: Boolean,
    preferBangla: Boolean = false,
  ): Boolean

  fun summarizeCurrentOrTopic(
    topicOrUrl: String? = null,
    initiatedByUser: Boolean,
    preferBangla: Boolean = false,
  ): Boolean

  fun openExternalVerifiedLink(url: String, initiatedByUser: Boolean): Boolean

  fun cancelActiveRequest()

  fun closeBrowserSession()

  fun release()
}

/**
 * InternetManager — Central privacy-gated coordinator for web search and safe browsing.
 *
 * Strict privacy guarantees:
 * - Never performs automatic or background web requests (`initiatedByUser == false` is blocked).
 * - Checks [PrivacyGuard], [PermissionController], and [PermissionRequestHandler] before every network call.
 * - Adapts payload limits & timeouts for slow connections and low-RAM devices.
 */
class InternetManager(
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val webRequestManager: WebRequestManager,
  private val searchManager: SearchManager,
  private val browserManager: BrowserManager,
  private val errorTracker: ErrorTracker,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InternetController {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private var activeJob: Job? = null
  private var eventListener: InternetEventListener? = null

  private val _state = MutableStateFlow<InternetState>(InternetState.Dormant)
  override val state: StateFlow<InternetState> = _state.asStateFlow()

  private val _connectionQuality =
    MutableStateFlow(webRequestManager.inspectConnectionQuality())
  override val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

  private val _activeCategory = MutableStateFlow(SearchCategory.WEB)
  override val activeCategory: StateFlow<SearchCategory> = _activeCategory.asStateFlow()

  private val _searchResults = MutableStateFlow<List<WebSearchResultItem>>(emptyList())
  override val searchResults: StateFlow<List<WebSearchResultItem>> = _searchResults.asStateFlow()

  override val currentPage: StateFlow<WebPageContent?> = browserManager.currentPage

  private val _lastSummary = MutableStateFlow<String?>(null)
  override val lastSummary: StateFlow<String?> = _lastSummary.asStateFlow()

  override val isOfflineLocked: Boolean
    get() = privacyGuard.policy.value.allowInternetOnUserAction != true

  fun setEventListener(listener: InternetEventListener?) {
    eventListener = listener
  }

  override fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult {
    return privacyGuard.authorizeAction(SensitiveAction.BROWSE_INTERNET, initiatedByUser)
  }

  override fun refreshConnectionQuality(): ConnectionQuality {
    val quality = webRequestManager.inspectConnectionQuality()
    _connectionQuality.value = quality
    return quality
  }

  override fun search(
    query: String,
    category: SearchCategory,
    initiatedByUser: Boolean,
    preferBangla: Boolean,
  ): Boolean {
    _activeCategory.value = category
    if (!verifyUserAuthorized(initiatedByUser, preferBangla, "search")) {
      return false
    }

    val quality = refreshConnectionQuality()
    activeJob?.cancel()
    _state.value = InternetState.Loading(category.displayName, query, quality)

    activeJob =
      scope.launch {
        runCatching {
          val (results, summary) = searchManager.search(query, category, preferBangla)
          _searchResults.value = results
          _lastSummary.value = summary
          _state.value = InternetState.SearchCompleted(query, category, results, summary)
          privacyGuard.notifyResourcesReleased(SensitiveAction.BROWSE_INTERNET)
          eventListener?.onSearchReady(query, category, results, summary)
        }.onFailure { err ->
          val msg = err.localizedMessage ?: "Search failed due to network interruption."
          errorTracker.logError(MODULE_NAME, msg, err)
          _state.value = InternetState.Error(msg, isRecoverable = true)
          eventListener?.onNetworkError(msg)
        }
      }
    return true
  }

  override fun openWebsite(
    url: String,
    initiatedByUser: Boolean,
    preferBangla: Boolean,
  ): Boolean {
    if (!verifyUserAuthorized(initiatedByUser, preferBangla, "open website")) {
      return false
    }

    val safeCheck = browserManager.checkSafeUrl(url)
    if (!safeCheck.isSafe) {
      val reason = safeCheck.blockedReason ?: "Unsafe URL blocked by MAX Safe Browsing."
      _state.value = InternetState.Error(reason, isRecoverable = true)
      errorTracker.logWarning(MODULE_NAME, reason)
      return false
    }

    val quality = refreshConnectionQuality()
    if (quality == ConnectionQuality.OFFLINE) {
      val offlineMsg =
        if (preferBangla) "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
        else "Device is offline. Connect to Wi-Fi or mobile data and try again."
      _state.value = InternetState.OfflineOrUnavailable(offlineMsg)
      eventListener?.onNetworkError(offlineMsg)
      return false
    }

    activeJob?.cancel()
    _state.value = InternetState.Loading("Reading Website", safeCheck.normalizedUrl, quality)

    activeJob =
      scope.launch {
        val result = browserManager.openAndReadWebsite(safeCheck.normalizedUrl, preferBangla)
        privacyGuard.notifyResourcesReleased(SensitiveAction.BROWSE_INTERNET)
        result.fold(
          onSuccess = { page ->
            _lastSummary.value = page.summary
            _state.value = InternetState.WebPageLoaded(page, page.summary)
            eventListener?.onWebPageSummarized(page, page.summary)
          },
          onFailure = { err ->
            val msg = err.localizedMessage ?: "Could not load webpage safely."
            _state.value = InternetState.Error(msg, isRecoverable = true)
            eventListener?.onNetworkError(msg)
          },
        )
      }
    return true
  }

  override fun summarizeCurrentOrTopic(
    topicOrUrl: String?,
    initiatedByUser: Boolean,
    preferBangla: Boolean,
  ): Boolean {
    val active = currentPage.value
    if (topicOrUrl.isNullOrBlank() && active != null) {
      val summary = browserManager.summarizeText(active.readableText, 3, preferBangla, active.title)
      _lastSummary.value = summary
      _state.value = InternetState.WebPageLoaded(active.copy(summary = summary), summary)
      eventListener?.onWebPageSummarized(active, summary)
      return true
    }

    val target = topicOrUrl?.trim().orEmpty()
    if (target.startsWith("http://") || target.startsWith("https://") ||
      (target.contains('.') && !target.contains(' '))
    ) {
      return openWebsite(target, initiatedByUser, preferBangla)
    }

    val newsQuery = target.ifBlank { "Bangladesh world top news" }
    return search(newsQuery, SearchCategory.NEWS, initiatedByUser, preferBangla)
  }

  override fun openExternalVerifiedLink(url: String, initiatedByUser: Boolean): Boolean {
    if (!verifyUserAuthorized(initiatedByUser, preferBangla = false, actionLabel = "external browser")) {
      return false
    }
    val outcome = browserManager.launchSafeExternalBrowser(url)
    privacyGuard.notifyResourcesReleased(SensitiveAction.BROWSE_INTERNET)
    return outcome.isSuccess
  }

  override fun cancelActiveRequest() {
    activeJob?.cancel()
    activeJob = null
    webRequestManager.cancelActiveRequests()
    if (_state.value is InternetState.Loading) {
      _state.value = InternetState.Dormant
    }
    privacyGuard.notifyResourcesReleased(SensitiveAction.BROWSE_INTERNET)
  }

  override fun closeBrowserSession() {
    cancelActiveRequest()
    browserManager.clearSessionPage()
    _searchResults.value = emptyList()
    _lastSummary.value = null
    _state.value = InternetState.Dormant
  }

  override fun release() {
    closeBrowserSession()
    eventListener = null
    scope.cancel()
  }

  private fun verifyUserAuthorized(
    initiatedByUser: Boolean,
    preferBangla: Boolean,
    actionLabel: String,
  ): Boolean {
    if (!initiatedByUser) {
      val reason = "Blocked automatic internet attempt ($actionLabel). Explicit user request required."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = InternetState.BlockedByPrivacyPolicy(reason)
      return false
    }

    val auth = privacyGuard.authorizeAction(SensitiveAction.BROWSE_INTERNET, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler.requestForExplicitAction(
        permission = AppPermission.INTERNET,
        preferBangla = preferBangla,
      )
      _state.value = InternetState.PermissionRequired(auth.reason)
      return false
    }
    return true
  }

  companion object {
    private const val MODULE_NAME = "InternetManager"
  }
}
