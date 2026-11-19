package com.example.internet

import com.example.utils.ErrorTracker
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * SearchManager — Executes privacy-safe searches across all 6 MAX search verticals:
 * 1. Web Search
 * 2. Website Search (`site:domain` or domain-scoped query)
 * 3. News Search
 * 4. Weather Search
 * 5. Wikipedia Search
 * 6. YouTube Search
 */
class SearchManager(
  private val webRequestManager: WebRequestManager,
  private val errorTracker: ErrorTracker,
) {

  suspend fun search(
    rawQuery: String,
    category: SearchCategory,
    preferBangla: Boolean = false,
  ): Pair<List<WebSearchResultItem>, String> {
    val query = rawQuery.trim()
    if (query.isEmpty()) {
      val emptyMsg =
        if (preferBangla) "কী খুঁজতে চান তা অনুগ্রহ করে লিখুন বা বলুন।"
        else "Please provide a topic or question to search."
      return emptyList<WebSearchResultItem>() to emptyMsg
    }

    return when (category) {
      SearchCategory.WEB -> executeWebSearch(query, preferBangla)
      SearchCategory.WEBSITE -> executeWebsiteSearch(query, preferBangla)
      SearchCategory.NEWS -> executeNewsSearch(query, preferBangla)
      SearchCategory.WEATHER -> executeWeatherSearch(query, preferBangla)
      SearchCategory.WIKIPEDIA -> executeWikipediaSearch(query, preferBangla)
      SearchCategory.YOUTUBE -> executeYouTubeSearch(query, preferBangla)
    }
  }

  private suspend fun executeWebSearch(
    query: String,
    preferBangla: Boolean,
  ): Pair<List<WebSearchResultItem>, String> {
    val encoded = encode(query)
    val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
    val googleSearchUrl = "https://www.google.com/search?q=$encoded"

    val results = mutableListOf<WebSearchResultItem>()
    when (val outcome = webRequestManager.fetchSafeText(ddgUrl)) {
      is WebRequestOutcome.Success -> {
        runCatching {
          val json = JSONObject(outcome.body)
          val abstractText = json.optString("AbstractText").trim()
          val heading = json.optString("Heading").trim()
          val abstractUrl = json.optString("AbstractURL").trim()
          if (abstractText.isNotEmpty() && abstractUrl.isNotEmpty()) {
            results.add(
              WebSearchResultItem(
                title = heading.ifEmpty { query },
                url = abstractUrl,
                snippet = abstractText,
                source = json.optString("AbstractSource").ifEmpty { "Web Answer" },
                category = SearchCategory.WEB,
              ),
            )
          }
          val related = json.optJSONArray("RelatedTopics") ?: JSONArray()
          for (i in 0 until minOf(related.length(), 4)) {
            val item = related.optJSONObject(i) ?: continue
            val text = item.optString("Text").trim()
            val firstUrl = item.optString("FirstURL").trim()
            if (text.isNotEmpty() && firstUrl.isNotEmpty()) {
              results.add(
                WebSearchResultItem(
                  title = text.substringBefore(" - ").take(72),
                  url = firstUrl,
                  snippet = text,
                  source = "Web",
                  category = SearchCategory.WEB,
                ),
              )
            }
          }
        }.onFailure { err ->
          errorTracker.logWarning(MODULE_NAME, "Partial web JSON parse fallback: ${err.message}")
        }
      }
      is WebRequestOutcome.Failure -> {
        errorTracker.logWarning(MODULE_NAME, "Web instant answer unreachable: ${outcome.reason}")
      }
    }

    results.add(
      WebSearchResultItem(
        title = "Google Search: $query",
        url = googleSearchUrl,
        snippet = "Open safe Google Search results in your browser for '$query'.",
        source = "Google",
        category = SearchCategory.WEB,
      ),
    )

    val topSnippet = results.firstOrNull()?.snippet.orEmpty()
    val summary =
      if (results.size > 1 && topSnippet.isNotEmpty()) {
        if (preferBangla) "'$query' সম্পর্কে তথ্য পাওয়া গেছে: $topSnippet"
        else "Here is what I found for '$query': $topSnippet"
      } else {
        if (preferBangla) "'$query' Google-এ সার্চের জন্য লিংক প্রস্তুত করা হয়েছে।"
        else "Prepared safe Google search results for '$query'."
      }
    return results to summary
  }

  private suspend fun executeWebsiteSearch(
    query: String,
    preferBangla: Boolean,
  ): Pair<List<WebSearchResultItem>, String> {
    val parts = query.split(" ", limit = 2)
    val rawDomain =
      parts.firstOrNull().orEmpty().removePrefix("https://").removePrefix("http://").trim('/')
    val subQuery = parts.getOrNull(1)?.trim().orEmpty()
    val safeCheck = webRequestManager.validateSafeUrl("https://$rawDomain")
    val googleSiteUrl = "https://www.google.com/search?q=${encode("site:$rawDomain $subQuery".trim())}"

    val results = buildList {
      if (safeCheck.isSafe) {
        add(
          WebSearchResultItem(
            title = "Open $rawDomain",
            url = safeCheck.normalizedUrl,
            snippet = "Read or open verified HTTPS website $rawDomain directly.",
            source = rawDomain,
            category = SearchCategory.WEBSITE,
          ),
        )
      }
      add(
        WebSearchResultItem(
          title = "Search within $rawDomain",
          url = googleSiteUrl,
          snippet = "Site-scoped search: site:$rawDomain $subQuery",
          source = "Google Site Search",
          category = SearchCategory.WEBSITE,
        ),
      )
    }

    val summary =
      if (preferBangla) "$rawDomain ওয়েবসাইট সার্চের জন্য প্রস্তুত করা হয়েছে।"
      else "Prepared website search for $rawDomain."
    return results to summary
  }

  private suspend fun executeNewsSearch(
    query: String,
    preferBangla: Boolean,
  ): Pair<List<WebSearchResultItem>, String> {
    val cleanQuery = query.ifBlank { "world news" }
    val hl = if (preferBangla) "bn" else "en-US"
    val rssUrl =
      "https://news.google.com/rss/search?q=${encode(cleanQuery)}&hl=$hl&gl=BD&ceid=BD:$hl"
    val results = mutableListOf<WebSearchResultItem>()

    when (val outcome = webRequestManager.fetchSafeText(rssUrl)) {
      is WebRequestOutcome.Success -> {
        val titles = Regex("<item>.*?<title>(.*?)</title>.*?<link>(.*?)</link>", RegexOption.DOT_MATCHES_ALL)
        titles.findAll(outcome.body).take(5).forEach { match ->
          val title = cleanXmlText(match.groupValues[1])
          val link = cleanXmlText(match.groupValues[2])
          if (title.isNotEmpty() && link.startsWith("https://")) {
            results.add(
              WebSearchResultItem(
                title = title,
                url = link,
                snippet = title,
                source = "Google News",
                category = SearchCategory.NEWS,
              ),
            )
          }
        }
      }
      is WebRequestOutcome.Failure -> {
        errorTracker.logWarning(MODULE_NAME, "News RSS fallback: ${outcome.reason}")
      }
    }

    if (results.isEmpty()) {
      val fallbackUrl = "https://news.google.com/search?q=${encode(cleanQuery)}"
      results.add(
        WebSearchResultItem(
          title = "Latest News: $cleanQuery",
          url = fallbackUrl,
          snippet = "Open safe Google News coverage for '$cleanQuery'.",
          source = "Google News",
          category = SearchCategory.NEWS,
        ),
      )
    }

    val headlineSummary = results.take(2).joinToString(" | ") { it.title }
    val summary =
      if (preferBangla) "সর্বশেষ খবরের সংক্ষিপ্ত শিরোনাম: $headlineSummary"
      else "Top news summary for '$cleanQuery': $headlineSummary"
    return results to summary
  }

  private suspend fun executeWeatherSearch(
    query: String,
    preferBangla: Boolean,
  ): Pair<List<WebSearchResultItem>, String> {
    val city = query.ifBlank { "Dhaka" }
    val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${encode(city)}&count=1&language=en&format=json"

    when (val geoOutcome = webRequestManager.fetchSafeText(geoUrl)) {
      is WebRequestOutcome.Success -> {
        val loc =
          runCatching {
            val arr = JSONObject(geoOutcome.body).optJSONArray("results")
            arr?.optJSONObject(0)
          }.getOrNull()

        if (loc != null) {
          val lat = loc.optDouble("latitude")
          val lon = loc.optDouble("longitude")
          val placeName = loc.optString("name", city)
          val country = loc.optString("country", "")
          val forecastUrl =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m"

          when (val wxOutcome = webRequestManager.fetchSafeText(forecastUrl)) {
            is WebRequestOutcome.Success -> {
              val current = runCatching { JSONObject(wxOutcome.body).optJSONObject("current") }.getOrNull()
              if (current != null) {
                val temp = current.optDouble("temperature_2m")
                val humidity = current.optInt("relative_humidity_2m")
                val wind = current.optDouble("wind_speed_10m")
                val descBangla =
                  "$placeName ($country)-এর বর্তমান তাপমাত্রা $temp°C, আর্দ্রতা $humidity% এবং বাতাসের গতি $wind km/h।"
                val descEnglish =
                  "Current weather in $placeName, $country: $temp°C, humidity $humidity%, wind $wind km/h."
                val summary = if (preferBangla) descBangla else descEnglish
                val item =
                  WebSearchResultItem(
                    title = "Weather in $placeName ($temp°C)",
                    url = "https://open-meteo.com/",
                    snippet = summary,
                    source = "Open-Meteo",
                    category = SearchCategory.WEATHER,
                  )
                return listOf(item) to summary
              }
            }
            is WebRequestOutcome.Failure -> {
              errorTracker.logWarning(MODULE_NAME, "Weather forecast fetch failed: ${wxOutcome.reason}")
            }
          }
        }
      }
      is WebRequestOutcome.Failure -> {
        errorTracker.logWarning(MODULE_NAME, "Weather geocoding failed: ${geoOutcome.reason}")
      }
    }

    val fallbackUrl = "https://www.google.com/search?q=${encode("weather $city")}"
    val fallbackMsg =
      if (preferBangla) "'$city'-এর আবহাওয়ার লাইভ তথ্য পাওয়া যায়নি; নিরাপদ সার্চ লিংক তৈরি করা হয়েছে।"
      else "Live weather API unavailable right now; prepared safe weather search link for '$city'."
    return listOf(
      WebSearchResultItem(
        title = "Weather Search: $city",
        url = fallbackUrl,
        snippet = fallbackMsg,
        source = "Web Weather",
        category = SearchCategory.WEATHER,
      ),
    ) to fallbackMsg
  }

  private suspend fun executeWikipediaSearch(
    query: String,
    preferBangla: Boolean,
  ): Pair<List<WebSearchResultItem>, String> {
    val langCode = if (preferBangla || containsBangla(query)) "bn" else "en"
    val slug = encode(query.replace(' ', '_'))
    val apiUrl = "https://$langCode.wikipedia.org/api/rest_v1/page/summary/$slug"

    when (val outcome = webRequestManager.fetchSafeText(apiUrl)) {
      is WebRequestOutcome.Success -> {
        val parsed =
          runCatching {
            val json = JSONObject(outcome.body)
            val title = json.optString("title").trim()
            val extract = json.optString("extract").trim()
            val pageUrl =
              json.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                ?.takeIf { it.isNotBlank() }
                ?: "https://$langCode.wikipedia.org/wiki/$slug"
            if (extract.isNotEmpty()) {
              WebSearchResultItem(
                title = title.ifEmpty { query },
                url = pageUrl,
                snippet = extract,
                source = "Wikipedia ($langCode)",
                category = SearchCategory.WIKIPEDIA,
              )
            } else {
              null
            }
          }.getOrNull()

        if (parsed != null) {
          val summary =
            if (preferBangla) "উইকিপিডিয়া সারাংশ (${parsed.title}): ${parsed.snippet}"
            else "Wikipedia summary for ${parsed.title}: ${parsed.snippet}"
          return listOf(parsed) to summary
        }
      }
      is WebRequestOutcome.Failure -> {
        errorTracker.logWarning(MODULE_NAME, "Wikipedia fetch failed: ${outcome.reason}")
      }
    }

    val fallbackUrl = "https://$langCode.wikipedia.org/wiki/Special:Search?search=${encode(query)}"
    val msg =
      if (preferBangla) "উইকিপিডিয়ায় '$query' খোঁজার লিংক প্রস্তুত।"
      else "Prepared Wikipedia search link for '$query'."
    return listOf(
      WebSearchResultItem(
        title = "Wikipedia: $query",
        url = fallbackUrl,
        snippet = msg,
        source = "Wikipedia",
        category = SearchCategory.WIKIPEDIA,
      ),
    ) to msg
  }

  private fun executeYouTubeSearch(
    query: String,
    preferBangla: Boolean,
  ): Pair<List<WebSearchResultItem>, String> {
    val ytUrl = "https://www.youtube.com/results?search_query=${encode(query)}"
    val item =
      WebSearchResultItem(
        title = "YouTube Search: $query",
        url = ytUrl,
        snippet = "Tap to open safe YouTube results for '$query'.",
        source = "YouTube",
        category = SearchCategory.YOUTUBE,
      )
    val msg =
      if (preferBangla) "YouTube-এ '$query' সার্চ প্রস্তুত করা হয়েছে।"
      else "Prepared YouTube search for '$query'."
    return listOf(item) to msg
  }

  private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

  private fun containsBangla(text: String): Boolean = text.any { it in '\u0980'..'\u09FF' }

  private fun cleanXmlText(raw: String): String {
    return raw
      .replace("<![CDATA[", "")
      .replace("]]>", "")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .trim()
  }

  companion object {
    private const val MODULE_NAME = "SearchManager"
  }
}
