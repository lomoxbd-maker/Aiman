package com.example.internet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.utils.ErrorTracker
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Outcome of an executed safe HTTPS request.
 */
sealed class WebRequestOutcome {
  data class Success(
    val url: String,
    val statusCode: Int,
    val contentType: String,
    val body: String,
    val byteCount: Int,
  ) : WebRequestOutcome()

  data class Failure(
    val url: String,
    val reason: String,
    val isNetworkError: Boolean = true,
  ) : WebRequestOutcome()
}

/**
 * Abstraction over HTTPS transport so [WebRequestManager] can execute real OkHttp calls in
 * production and deterministic responses in JVM/Robolectric tests.
 */
interface SafeHttpTransport {
  suspend fun executeGet(
    url: String,
    timeoutMs: Long,
    maxBytes: Int,
  ): WebRequestOutcome

  fun cancelAll()
}

/**
 * WebRequestManager — Security-hardened HTTPS transport layer for MAX.
 *
 * Security & efficiency guarantees:
 * - Enforces HTTPS (`ConnectionSpec.MODERN_TLS`), automatically upgrading `http://` or rejecting
 *   unsafe schemes (`javascript:`, `file:`, `data:`, `content:`) and private/loopback SSRF hosts.
 * - Uses zero cookie storage (`CookieJar.NO_COOKIES`) and strips common tracking query parameters.
 * - Adapts timeouts & max download bytes for slow/metered connections and low-RAM devices.
 */
class WebRequestManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
  private val isLowRamDevice: Boolean = false,
  customTransport: SafeHttpTransport? = null,
) {

  private val defaultClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
      .cookieJar(CookieJar.NO_COOKIES)
      .followRedirects(true)
      .followSslRedirects(true)
      .retryOnConnectionFailure(true)
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .callTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  private val transport: SafeHttpTransport =
    customTransport ?: OkHttpSafeHttpTransport(defaultClient)

  /**
   * Inspects Android [ConnectivityManager] across API 24–36 without triggering network traffic.
   */
  fun inspectConnectionQuality(): ConnectionQuality {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return ConnectionQuality.OFFLINE

    val network = cm.activeNetwork ?: return ConnectionQuality.OFFLINE
    val caps = cm.getNetworkCapabilities(network) ?: return ConnectionQuality.OFFLINE
    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    if (!hasInternet) return ConnectionQuality.OFFLINE
    val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    val downstreamKbps = caps.linkDownstreamBandwidthKbps
    return if (!isUnmetered || (downstreamKbps in 1..1500)) {
      ConnectionQuality.SLOW_OR_METERED
    } else {
      ConnectionQuality.NORMAL_WIFI_OR_FAST
    }
  }

  /**
   * Validates, upgrades, and sanitizes URLs before any network request or browser launch.
   */
  fun validateSafeUrl(rawUrl: String): SafeUrlCheckResult {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) {
      return SafeUrlCheckResult(rawUrl, "", isSafe = false, blockedReason = "Empty URL.")
    }

    val lower = trimmed.lowercase()
    val blockedSchemes = listOf("javascript:", "file:", "content:", "data:", "vbscript:", "intent:")
    if (blockedSchemes.any { lower.startsWith(it) }) {
      return SafeUrlCheckResult(
        originalUrl = rawUrl,
        normalizedUrl = "",
        isSafe = false,
        blockedReason = "Blocked unsafe URL scheme for your security.",
      )
    }

    val candidate =
      when {
        lower.startsWith("https://") -> trimmed
        lower.startsWith("http://") -> "https://${trimmed.substring(7)}"
        !lower.contains("://") -> "https://$trimmed"
        else ->
          return SafeUrlCheckResult(
            originalUrl = rawUrl,
            normalizedUrl = "",
            isSafe = false,
            blockedReason = "Only HTTPS web addresses are permitted.",
          )
      }

    val parsed =
      runCatching { URI(candidate) }.getOrNull()
        ?: return SafeUrlCheckResult(
          originalUrl = rawUrl,
          normalizedUrl = "",
          isSafe = false,
          blockedReason = "Malformed web address.",
        )

    val host = parsed.host?.lowercase().orEmpty()
    if (host.isEmpty() || !host.contains('.')) {
      return SafeUrlCheckResult(
        originalUrl = rawUrl,
        normalizedUrl = "",
        isSafe = false,
        blockedReason = "Invalid or local-only hostname blocked.",
      )
    }

    if (isRestrictedHost(host)) {
      return SafeUrlCheckResult(
        originalUrl = rawUrl,
        normalizedUrl = "",
        isSafe = false,
        blockedReason = "Blocked private or local network address ($host).",
      )
    }

    val sanitized = stripTrackingParams(candidate)
    return SafeUrlCheckResult(
      originalUrl = rawUrl,
      normalizedUrl = sanitized,
      isSafe = true,
    )
  }

  /**
   * Executes a privacy-preserving, size-bounded HTTPS GET request.
   */
  suspend fun fetchSafeText(rawUrl: String): WebRequestOutcome {
    val safeCheck = validateSafeUrl(rawUrl)
    if (!safeCheck.isSafe) {
      val reason = safeCheck.blockedReason ?: "URL failed safe browsing checks."
      errorTracker.logWarning(MODULE_NAME, "Blocked unsafe request: $reason")
      return WebRequestOutcome.Failure(rawUrl, reason, isNetworkError = false)
    }

    val quality = inspectConnectionQuality()
    if (quality == ConnectionQuality.OFFLINE) {
      return WebRequestOutcome.Failure(
        url = safeCheck.normalizedUrl,
        reason = "Device appears offline. Please check Wi-Fi or mobile data.",
        isNetworkError = true,
      )
    }

    val timeoutMs =
      if (quality == ConnectionQuality.SLOW_OR_METERED) SLOW_TIMEOUT_MS else NORMAL_TIMEOUT_MS
    val maxBytes =
      when {
        isLowRamDevice -> LOW_RAM_MAX_BYTES
        quality == ConnectionQuality.SLOW_OR_METERED -> SLOW_MAX_BYTES
        else -> NORMAL_MAX_BYTES
      }

    return transport.executeGet(safeCheck.normalizedUrl, timeoutMs, maxBytes)
  }

  fun cancelActiveRequests() {
    transport.cancelAll()
  }

  private fun isRestrictedHost(host: String): Boolean {
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return true
    if (host == "127.0.0.1" || host == "0.0.0.0" || host == "::1") return true
    val parts = host.split('.')
    if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
      val first = parts[0].toInt()
      val second = parts[1].toInt()
      if (first == 10 || first == 127) return true
      if (first == 192 && second == 168) return true
      if (first == 169 && second == 254) return true
      if (first == 172 && second in 16..31) return true
    }
    return runCatching {
      val addr = InetAddress.getByName(host)
      addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress
    }.getOrDefault(false)
  }

  private fun stripTrackingParams(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    val trackingKeys = setOf("fbclid", "gclid", "mc_cid", "mc_eid", "igshid")
    val builder: HttpUrl.Builder = parsed.newBuilder()
    parsed.queryParameterNames.forEach { name ->
      val lowerName = name.lowercase()
      if (lowerName.startsWith("utm_") || trackingKeys.contains(lowerName)) {
        builder.removeAllQueryParameters(name)
      }
    }
    return builder.build().toString()
  }

  companion object {
    private const val MODULE_NAME = "WebRequestManager"
    private const val NORMAL_TIMEOUT_MS = 12_000L
    private const val SLOW_TIMEOUT_MS = 22_000L
    private const val NORMAL_MAX_BYTES = 256 * 1024
    private const val SLOW_MAX_BYTES = 96 * 1024
    private const val LOW_RAM_MAX_BYTES = 96 * 1024
  }
}

private class OkHttpSafeHttpTransport(
  private val baseClient: OkHttpClient,
) : SafeHttpTransport {

  @Volatile
  private var currentCall: Call? = null

  override suspend fun executeGet(
    url: String,
    timeoutMs: Long,
    maxBytes: Int,
  ): WebRequestOutcome =
    withContext(Dispatchers.IO) {
      val scopedClient =
        baseClient.newBuilder()
          .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
          .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
          .callTimeout(timeoutMs + 4_000L, TimeUnit.MILLISECONDS)
          .build()

      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", "MAXAssistantAndroid/1.0 (PrivacySafe)")
          .header("Accept", "application/json, text/html, application/xml, text/plain")
          .header("DNT", "1")
          .get()
          .build()

      try {
        val call = scopedClient.newCall(request)
        currentCall = call
        call.execute().use { response ->
          if (!response.isSuccessful) {
            return@withContext WebRequestOutcome.Failure(
              url = url,
              reason = "Server responded with HTTP ${response.code}.",
              isNetworkError = response.code >= 500,
            )
          }
          val bodySource = response.body ?: return@withContext WebRequestOutcome.Failure(
            url = url,
            reason = "Empty response from server.",
            isNetworkError = true,
          )
          val bytes = readBoundedBytes(bodySource.byteStream(), maxBytes)
          val text = bytes.toString(Charsets.UTF_8)
          val contentType = response.header("Content-Type") ?: "text/plain"
          WebRequestOutcome.Success(
            url = url,
            statusCode = response.code,
            contentType = contentType,
            body = text,
            byteCount = bytes.size,
          )
        }
      } catch (io: IOException) {
        WebRequestOutcome.Failure(
          url = url,
          reason = "Network request failed (${io.localizedMessage ?: "connection timeout"}).",
          isNetworkError = true,
        )
      } catch (t: Throwable) {
        WebRequestOutcome.Failure(
          url = url,
          reason = t.localizedMessage ?: "Unexpected network error.",
          isNetworkError = true,
        )
      } finally {
        currentCall = null
      }
    }

  override fun cancelAll() {
    runCatching { currentCall?.cancel() }
    currentCall = null
  }

  private fun readBoundedBytes(stream: java.io.InputStream, maxBytes: Int): ByteArray {
    val buffer = ByteArray(4096)
    val out = java.io.ByteArrayOutputStream()
    var total = 0
    while (total < maxBytes) {
      val toRead = minOf(buffer.size, maxBytes - total)
      val read = stream.read(buffer, 0, toRead)
      if (read <= 0) break
      out.write(buffer, 0, read)
      total += read
    }
    return out.toByteArray()
  }
}
