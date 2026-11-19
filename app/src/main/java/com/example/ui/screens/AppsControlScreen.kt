package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.apps.AppAlternative
import com.example.apps.AppState
import com.example.apps.SupportedApp
import com.example.ui.components.SectionHeader
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsControlScreen(
  uiState: MAXUiState,
  onBack: () -> Unit,
  onRefreshAvailability: () -> Unit,
  onRequestOpenApp: (SupportedApp) -> Unit,
  onRequestOpenAlternative: (SupportedApp, AppAlternative) -> Unit,
  onRequestWhatsAppDraft: (String, String) -> Unit,
  onRequestGmailDraft: (String, String, String) -> Unit,
  onRequestMapsSearch: (String) -> Unit,
  onRequestChromeUrl: (String) -> Unit,
  onRequestYouTubeSearch: (String) -> Unit,
  onRunVoiceCommand: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(Unit) {
    onRefreshAvailability()
  }

  var waPhone by remember { mutableStateOf("") }
  var waDraft by remember { mutableStateOf("হ্যালো, কেমন আছেন?") }
  var gmailTo by remember { mutableStateOf("") }
  var gmailSubject by remember { mutableStateOf("Quick Update") }
  var mapsQuery by remember { mutableStateOf("Dhaka") }
  var chromeOrYtQuery by remember { mutableStateOf("https://www.wikipedia.org") }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Header Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("apps_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
              Text("Apps Control System", style = MaterialTheme.typography.titleLarge)
              Text(
                "Confirmation-gated app launching & safe fallbacks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = onRefreshAvailability,
            modifier = Modifier.testTag("apps_refresh_button"),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh installed apps status")
          }
        }

        // Strict Privacy Shield Card
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Filled.Security, contentDescription = "Privacy guard", tint = MaterialTheme.colorScheme.primary)
            Text(
              text =
                "MAX opens apps strictly on your explicit command & confirmation. Never opens apps in background, never sends messages, and never posts automatically.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        // Reactive AppState Banner (Launched / Alternatives / Blocked / Error)
        when (val state = uiState.appState) {
          is AppState.AppNotInstalledWithAlternatives -> {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(Icons.Filled.Info, contentDescription = null)
                  Text(
                    "${state.app.displayName} Not Installed — Suggested Alternatives",
                    style = MaterialTheme.typography.titleSmall,
                  )
                }
                Text(state.messageBangla, style = MaterialTheme.typography.bodySmall)
                Text(
                  state.messageEnglish,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  state.alternatives.forEach { alt ->
                    OutlinedButton(
                      onClick = { onRequestOpenAlternative(state.app, alt) },
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.testTag("alt_${state.app.name.lowercase()}_${alt.kind.name.lowercase()}"),
                    ) {
                      Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                      Spacer(Modifier.widthIn(min = 6.dp))
                      Text(alt.title)
                    }
                  }
                }
              }
            }
          }
          is AppState.Launched -> {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = EmeraldReady.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.45f)),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldReady)
                Column {
                  Text(state.messageBangla, style = MaterialTheme.typography.bodyMedium)
                  Text(
                    state.messageEnglish,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          is AppState.BlockedByPrivacyPolicy -> {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            ) {
              Text(
                text = state.reason,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
              )
            }
          }
          is AppState.Error -> {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            ) {
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
              )
            }
          }
          else -> Unit
        }

        // Bangla Voice Commands Strip
        SectionHeader(
          title = "Bangla Voice Shortcuts",
          trailingBadge = "Command-only",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val voiceCommands =
            listOf(
              "MAX, WhatsApp খোলো",
              "MAX, YouTube খোলো",
              "MAX, Maps খোলো",
              "MAX, Chrome খোলো",
              "MAX, Messenger খোলো",
              "MAX, Facebook খোলো",
              "MAX, Gmail খোলো",
            )
          items(voiceCommands) { cmd ->
            AssistChip(
              onClick = { onRunVoiceCommand(cmd) },
              label = { Text(cmd) },
              modifier = Modifier.testTag("voice_app_cmd_${cmd.hashCode()}"),
            )
          }
        }

        // Supported Apps Cards
        SectionHeader(
          title = "Supported Apps (7)",
          trailingBadge = "Privacy Gated",
        )

        SupportedApp.entries.forEach { app ->
          val availability = uiState.appAvailabilities[app]
          val isInstalled = availability?.primaryInstalled ?: false
          val altCount = availability?.alternatives?.size ?: 0
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = iconForApp(app),
                    contentDescription = app.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Column {
                    Text(
                      text = "${app.displayName} • ${app.displayBangla}",
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                      text =
                        when {
                          availability == null -> "Checking device support..."
                          isInstalled -> "Installed • Ready on your confirmation"
                          else -> "Not installed • $altCount safe alternative(s) ready"
                        },
                      style = MaterialTheme.typography.bodySmall,
                      color =
                        if (isInstalled) EmeraldReady else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Button(
                  onClick = { onRequestOpenApp(app) },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.testTag("open_app_${app.name.lowercase()}"),
                ) {
                  Text(if (isInstalled) "Open" else "Alternatives")
                }
              }
              if (app.isMessagingOrSocial) {
                Text(
                  text = "Safety lock: Never sends messages/emails or makes posts automatically.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        // Safe Compose & Navigation Helpers (Always Confirmation-Gated)
        SectionHeader(
          title = "Safe App Drafts & Deep Links",
          trailingBadge = "Confirm First",
        )

        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("WhatsApp Draft (Manual Send Only)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
              value = waPhone,
              onValueChange = { waPhone = it },
              label = { Text("Phone with country code (optional)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("wa_draft_phone_input"),
            )
            OutlinedTextField(
              value = waDraft,
              onValueChange = { waDraft = it },
              label = { Text("Draft text (you tap Send manually)") },
              modifier = Modifier.fillMaxWidth().testTag("wa_draft_text_input"),
            )
            FilledTonalButton(
              onClick = { onRequestWhatsAppDraft(waPhone, waDraft) },
              modifier = Modifier.fillMaxWidth().testTag("wa_draft_button"),
            ) {
              Text("Confirm & Open WhatsApp Draft")
            }

            Spacer(Modifier.height(4.dp))
            Text("Gmail Compose Draft (Manual Send Only)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
              value = gmailTo,
              onValueChange = { gmailTo = it },
              label = { Text("Recipient email") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("gmail_to_input"),
            )
            OutlinedTextField(
              value = gmailSubject,
              onValueChange = { gmailSubject = it },
              label = { Text("Subject") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("gmail_subject_input"),
            )
            FilledTonalButton(
              onClick = { onRequestGmailDraft(gmailTo, gmailSubject, "") },
              modifier = Modifier.fillMaxWidth().testTag("gmail_draft_button"),
            ) {
              Text("Confirm & Open Email Draft")
            }

            Spacer(Modifier.height(4.dp))
            Text("Maps Search & Browser / YouTube", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
              value = mapsQuery,
              onValueChange = { mapsQuery = it },
              label = { Text("Place for Google Maps") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("maps_query_input"),
            )
            FilledTonalButton(
              onClick = { onRequestMapsSearch(mapsQuery) },
              modifier = Modifier.fillMaxWidth().testTag("maps_search_button"),
            ) {
              Text("Confirm & Search in Google Maps")
            }

            OutlinedTextField(
              value = chromeOrYtQuery,
              onValueChange = { chromeOrYtQuery = it },
              label = { Text("HTTPS URL (Chrome) or Topic (YouTube)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("chrome_yt_input"),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { onRequestChromeUrl(chromeOrYtQuery) },
                modifier = Modifier.weight(1f).testTag("chrome_open_url_button"),
              ) {
                Text("Chrome HTTPS")
              }
              OutlinedButton(
                onClick = { onRequestYouTubeSearch(chromeOrYtQuery) },
                modifier = Modifier.weight(1f).testTag("yt_open_query_button"),
              ) {
                Text("YouTube App")
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}

private fun iconForApp(app: SupportedApp): ImageVector =
  when (app) {
    SupportedApp.WHATSAPP -> Icons.Filled.Chat
    SupportedApp.MESSENGER -> Icons.Filled.Forum
    SupportedApp.FACEBOOK -> Icons.Filled.Share
    SupportedApp.CHROME -> Icons.Filled.Public
    SupportedApp.YOUTUBE -> Icons.Filled.PlayCircle
    SupportedApp.GMAIL -> Icons.Filled.Email
    SupportedApp.GOOGLE_MAPS -> Icons.Filled.Map
  }
