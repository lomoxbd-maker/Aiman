package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.tts.SpeechStyle
import com.example.tts.VoiceLanguage
import com.example.ui.components.SectionHeader
import com.example.viewmodel.MAXUiState

@Composable
fun SettingsScreen(
  uiState: MAXUiState,
  onSetWakeWordEnabled: (Boolean) -> Unit,
  onSetWakeWordSensitivity: (Float) -> Unit,
  onSetLowPowerStandbyMode: (Boolean) -> Unit,
  onSetVoiceLanguage: (VoiceLanguage) -> Unit,
  onSetSpeechStyle: (SpeechStyle) -> Unit,
  onSetTtsSpeechRate: (Float) -> Unit,
  onSetTtsPitch: (Float) -> Unit,
  onSetMemoryRetentionEnabled: (Boolean) -> Unit,
  onSetDarkThemePreferred: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val settings = uiState.settings

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
        SectionHeader(title = "Wake Word \"MAX\" Settings", trailingBadge = "DataStore")

        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SettingToggleRow(
              title = "Wake Word Standby",
              subtitle = "Monitor standby only for keyword \"MAX\" (never saves audio).",
              checked = settings.wakeWordEnabled,
              onCheckedChange = onSetWakeWordEnabled,
              testTag = "switch_wake_word",
            )

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("Wake Word Sensitivity", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = "${(settings.wakeWordSensitivity * 100).toInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Slider(
                value = settings.wakeWordSensitivity,
                onValueChange = onSetWakeWordSensitivity,
                valueRange = 0.25f..0.95f,
                modifier = Modifier.testTag("slider_wake_sensitivity"),
              )
            }

            SettingToggleRow(
              title = "Low-Power Standby Cadence",
              subtitle = "Throttles frame polling to protect battery and low-RAM devices.",
              checked = settings.lowPowerStandbyMode,
              onCheckedChange = onSetLowPowerStandbyMode,
              testTag = "switch_low_power",
            )
          }
        }

        SectionHeader(title = "Bangla Voice, Style & Memory")

        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Preferred Voice Language", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(VoiceLanguage.entries) { lang ->
                FilterChip(
                  selected = settings.voiceLanguage == lang,
                  onClick = { onSetVoiceLanguage(lang) },
                  label = { Text(lang.displayLabel) },
                  modifier = Modifier.testTag("settings_lang_${lang.name.lowercase()}"),
                )
              }
            }

            Text("Speaking Style", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(SpeechStyle.entries) { style ->
                FilterChip(
                  selected = settings.speechStyle == style,
                  onClick = { onSetSpeechStyle(style) },
                  label = { Text(style.displayName) },
                  modifier = Modifier.testTag("settings_style_${style.name.lowercase()}"),
                )
              }
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("TTS Speech Speed (Comfortable)", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = "${"%.2f".format(settings.ttsSpeechRate)}x",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Slider(
                value = settings.ttsSpeechRate.coerceIn(0.65f, 1.25f),
                onValueChange = onSetTtsSpeechRate,
                valueRange = 0.65f..1.25f,
                modifier = Modifier.testTag("slider_tts_rate"),
              )
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("TTS Voice Warmth / Pitch", style = MaterialTheme.typography.labelLarge)
                Text(
                  text = "${"%.2f".format(settings.ttsPitch)}x",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Slider(
                value = settings.ttsPitch.coerceIn(0.75f, 1.35f),
                onValueChange = onSetTtsPitch,
                valueRange = 0.75f..1.35f,
                modifier = Modifier.testTag("slider_tts_pitch"),
              )
            }

            SettingToggleRow(
              title = "In-Memory Context Buffer",
              subtitle = "Allows MemoryManager to hold volatile session context.",
              checked = settings.memoryRetentionEnabled,
              onCheckedChange = onSetMemoryRetentionEnabled,
              testTag = "switch_memory_retention",
            )

            SettingToggleRow(
              title = "Dark Obsidian Theme",
              subtitle = "Switch between Obsidian dark console and Daylight mode.",
              checked = settings.darkThemePreferred,
              onCheckedChange = onSetDarkThemePreferred,
              testTag = "switch_dark_theme",
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
