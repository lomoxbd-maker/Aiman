package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.internet.ConnectionQuality
import com.example.internet.InternetState
import com.example.internet.SearchCategory
import com.example.tts.SpeechStyle
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState

@Composable
fun InternetScreen(
  uiState: MAXUiState,
  onBack: () -> Unit,
  onSearchInternet: (String, SearchCategory) -> Unit,
  onOpenWebsite: (String) -> Unit,
  onSummarizeWebContent: (String?) -> Unit,
  onOpenExternalVerifiedUrl: (String) -> Unit,
  onCancelInternetRequest: () -> Unit,
  onCloseBrowserSession: () -> Unit,
  onRefreshConnection: () -> Unit,
  onRunVoiceCommand: (String) -> Unit,
  onSpeakText: (String, SpeechStyle?) -> Unit,
  onStopSpeaking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember(uiState.searchCategory) { mutableStateOf(uiState.searchCategory) }
  var searchQuery by remember { mutableStateOf("") }
  var websiteUrlInput by remember { mutableStateOf("https://bn.wikipedia.org/wiki/বাংলাদেশ") }

  val internetAllowed = uiState.privacyPolicy.allowInternetOnUserAction

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Header with Back Navigation
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("internet_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Core")
            }
            Spacer(Modifier.width(4.dp))
            Column {
              Text(
                text = "MAX Safe Internet & Browser",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text =
                  if (internetAllowed) "User-Authorized HTTPS Mode"
                  else "Privacy Locked (Asks before 1st browse)",
                style = MaterialTheme.typography.bodySmall,
                color = if (internetAllowed) EmeraldReady else AmberStandby,
              )
            }
          }
          OutlinedButton(
            onClick = onRefreshConnection,
            modifier = Modifier.testTag("internet_refresh_connection_button"),
          ) {
            Text(uiState.connectionQuality.label)
          }
        }

        // Privacy & Network Shield Card
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = null,
              tint = if (internetAllowed) EmeraldReady else AmberStandby,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  when (uiState.connectionQuality) {
                    ConnectionQuality.OFFLINE -> "Device is offline • Requests stay dormant"
                    ConnectionQuality.SLOW_OR_METERED ->
                      "Metered / Low-bandwidth mode • Compact payloads"
                    ConnectionQuality.NORMAL_WIFI_OR_FAST ->
                      "HTTPS-only Safe Browsing • Zero history stored"
                  },
                style = MaterialTheme.typography.labelLarge,
              )
              Text(
                text = "Never browses automatically, blocks unsafe URLs & clears session data on close.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (uiState.searchResults.isNotEmpty() || uiState.currentWebPage != null) {
              IconButton(
                onClick = onCloseBrowserSession,
                modifier = Modifier.testTag("internet_clear_session_button"),
              ) {
                Icon(Icons.Filled.Close, contentDescription = "Clear browser session")
              }
            }
          }
        }

        // Search Section (6 Verticals + Bangla Voice Command Presets)
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SectionHeader(
              title = "Search Verticals",
              trailingBadge = selectedCategory.displayBangla,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(SearchCategory.entries) { category ->
                FilterChip(
                  selected = selectedCategory == category,
                  onClick = { selectedCategory = category },
                  label = { Text(category.displayName) },
                  modifier = Modifier.testTag("search_cat_${category.name.lowercase()}"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                  Text(
                    when (selectedCategory) {
                      SearchCategory.WEATHER -> "City (e.g., Dhaka, Chattogram)"
                      SearchCategory.WEBSITE -> "Domain or topic (e.g., bbc.com cricket)"
                      SearchCategory.WIKIPEDIA -> "Wikipedia topic (Bangla/English)"
                      SearchCategory.YOUTUBE -> "Search YouTube videos..."
                      SearchCategory.NEWS -> "News topic..."
                      SearchCategory.WEB -> "Search the web safely..."
                    },
                  )
                },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("internet_search_input"),
              )
              Button(
                onClick = {
                  if (searchQuery.isNotBlank()) {
                    onSearchInternet(searchQuery.trim(), selectedCategory)
                  }
                },
                modifier = Modifier.testTag("internet_search_button"),
              ) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
              }
            }

            Text(
              text = "Bangla Voice Command Shortcuts",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val presets =
                listOf(
                  "MAX, Google-এ খুঁজে দেখো Bangladesh technology",
                  "MAX, এই খবরটা সংক্ষেপে বলো",
                  "MAX, YouTube-এ সার্চ করো Bangla LoFi",
                  "MAX, Dhaka আবহাওয়া",
                )
              items(presets) { cmd ->
                AssistChip(
                  onClick = { onRunVoiceCommand(cmd) },
                  label = { Text(cmd) },
                  modifier = Modifier.testTag("internet_voice_preset_${cmd.hashCode()}"),
                )
              }
            }
          }
        }

        // Direct Safe Browser & Webpage Reader Section
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SectionHeader(title = "Safe Browser & Page Reader", trailingBadge = "HTTPS")

            OutlinedTextField(
              value = websiteUrlInput,
              onValueChange = { websiteUrlInput = it },
              label = { Text("Website URL (HTTPS enforced)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("internet_url_input"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = { onOpenWebsite(websiteUrlInput.trim()) },
                modifier = Modifier.weight(1f).testTag("internet_open_site_button"),
              ) {
                Icon(Icons.Filled.Public, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open & Read")
              }
              FilledTonalButton(
                onClick = { onSummarizeWebContent(websiteUrlInput.trim().ifBlank { null }) },
                modifier = Modifier.weight(1f).testTag("internet_summarize_button"),
              ) {
                Icon(Icons.Filled.Summarize, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Summarize")
              }
            }

            OutlinedButton(
              onClick = { onOpenExternalVerifiedUrl(websiteUrlInput.trim()) },
              modifier = Modifier.fillMaxWidth().testTag("internet_open_external_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Open Safely in System Browser")
            }
          }
        }

        // Active Loading / Error / Privacy Notice
        when (val state = uiState.internetState) {
          is InternetState.Loading -> {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "${state.actionLabel}: ${state.targetQueryOrUrl}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  OutlinedButton(
                    onClick = onCancelInternetRequest,
                    modifier = Modifier.testTag("internet_cancel_button"),
                  ) {
                    Text("Cancel")
                  }
                }
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
              }
            }
          }
          is InternetState.Error,
          is InternetState.OfflineOrUnavailable,
          is InternetState.BlockedByPrivacyPolicy,
          is InternetState.PermissionRequired, -> {
            val notice =
              when (state) {
                is InternetState.Error -> state.message
                is InternetState.OfflineOrUnavailable -> state.reason
                is InternetState.BlockedByPrivacyPolicy -> state.reason
                is InternetState.PermissionRequired -> state.explanation
                else -> ""
              }
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            ) {
              Text(
                text = notice,
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          else -> Unit
        }

        // Active Webpage / Search Spoken Summary Card
        uiState.lastWebSummary?.let { summary ->
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SectionHeader(
                title = uiState.currentWebPage?.title ?: "MAX Web Summary",
                trailingBadge = "Read Aloud Ready",
              )
              Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
              )
              uiState.currentWebPage?.let { page ->
                if (page.readableText.isNotBlank()) {
                  Text(
                    text = page.readableText.take(420),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = { onSpeakText(summary, SpeechStyle.CALM) },
                  modifier = Modifier.weight(1f).testTag("internet_speak_summary_button"),
                ) {
                  Icon(Icons.Filled.VolumeUp, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Speak Summary")
                }
                OutlinedButton(
                  onClick = onStopSpeaking,
                  modifier = Modifier.testTag("internet_stop_voice_button"),
                ) {
                  Icon(Icons.Filled.Stop, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text("Stop")
                }
              }
            }
          }
        }

        // Search Results Cards
        if (uiState.searchResults.isNotEmpty()) {
          SectionHeader(
            title = "Search Results",
            trailingBadge = "${uiState.searchResults.size} items",
          )
          uiState.searchResults.forEachIndexed { idx, item ->
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "${item.source} • ${item.url}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (item.snippet.isNotBlank()) {
                  Text(
                    text = item.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilledTonalButton(
                    onClick = {
                      websiteUrlInput = item.url
                      onOpenWebsite(item.url)
                    },
                    modifier = Modifier.testTag("result_read_button_$idx"),
                  ) {
                    Text("Read & Summarize")
                  }
                  OutlinedButton(
                    onClick = { onOpenExternalVerifiedUrl(item.url) },
                    modifier = Modifier.testTag("result_external_button_$idx"),
                  ) {
                    Text("Open Link")
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
