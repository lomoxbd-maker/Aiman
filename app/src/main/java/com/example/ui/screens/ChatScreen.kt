package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.CoreLifecycleState
import com.example.permissions.AppPermission
import com.example.permissions.PermissionStatus
import com.example.speech.SpeechState
import com.example.tts.SpeechStyle
import com.example.tts.VoiceLanguage
import com.example.tts.VoiceState
import com.example.ui.components.AIOrbVisualState
import com.example.ui.components.FuturisticAIOrb
import com.example.ui.components.MAXGlassCard
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.GradientCyanGlow
import com.example.ui.theme.GradientElectricPurple
import com.example.ui.theme.GradientRoyalBlue
import com.example.viewmodel.MAXUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
  uiState: MAXUiState,
  onSendMessage: (String, Boolean) -> Unit,
  onActivateVoice: () -> Unit,
  onRequestMicPermission: () -> Unit,
  onSpeakText: (String, SpeechStyle?) -> Unit,
  onStopSpeaking: () -> Unit,
  onSelectLanguage: (VoiceLanguage) -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

  val hasAudioPermission =
    uiState.permissionSnapshots
      .firstOrNull { it.permission == AppPermission.MICROPHONE_WAKE_WORD }
      ?.status == PermissionStatus.GRANTED

  val lowPowerMotion =
    uiState.settings.lowPowerStandbyMode || (uiState.compatibility?.isPowerSaveMode == true)

  val isSpeaking = uiState.voiceState is VoiceState.Speaking
  val isThinking = uiState.isThinking
  val isListening =
    uiState.coreState == CoreLifecycleState.LISTENING_PREPARED ||
      uiState.coreState == CoreLifecycleState.AWAITING_FOLLOW_UP ||
      uiState.speechState == SpeechState.LISTENING_ACTIVE ||
      uiState.speechState == SpeechState.PREPARED_FOR_CONVERSATION

  val orbState =
    when {
      isThinking -> AIOrbVisualState.THINKING
      isSpeaking -> AIOrbVisualState.SPEAKING
      isListening -> AIOrbVisualState.LISTENING
      else -> AIOrbVisualState.READY
    }

  val statusText =
    when (orbState) {
      AIOrbVisualState.READY -> "Ready"
      AIOrbVisualState.LISTENING -> "Listening..."
      AIOrbVisualState.THINKING -> "Thinking..."
      AIOrbVisualState.SPEAKING -> "Speaking..."
    }

  LaunchedEffect(uiState.conversationTurns.size) {
    if (uiState.conversationTurns.isNotEmpty()) {
      listState.animateScrollToItem(uiState.conversationTurns.lastIndex)
    }
  }

  Box(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 780.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Compact Futuristic Header with Mini AI Orb
      MAXGlassCard(
        cornerRadius = 24.dp,
        accentColor =
          when (orbState) {
            AIOrbVisualState.THINKING -> AmberStandby
            AIOrbVisualState.SPEAKING -> GradientElectricPurple
            AIOrbVisualState.LISTENING -> GradientCyanGlow
            AIOrbVisualState.READY -> GradientRoyalBlue
          },
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            FuturisticAIOrb(
              orbState = orbState,
              lowPowerMotion = lowPowerMotion,
              orbSize = 64.dp,
              onClick = {
                if (hasAudioPermission) {
                  onActivateVoice()
                } else {
                  onRequestMicPermission()
                }
              },
              modifier = Modifier.testTag("chat_mini_orb_button"),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "MAX Conversation",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "$statusText • ${uiState.emotionState.currentEmotion.displayName} • ${uiState.settings.voiceLanguage.displayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (isSpeaking) {
              OutlinedButton(
                onClick = onStopSpeaking,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop speaking")
              }
            }
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VoiceLanguage.entries) { lang ->
              FilterChip(
                selected = uiState.settings.voiceLanguage == lang,
                onClick = { onSelectLanguage(lang) },
                label = { Text(lang.displayLabel) },
              )
            }
          }
        }
      }

      // Conversation Thread
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (uiState.conversationTurns.isEmpty()) {
          item {
            MAXGlassCard(cornerRadius = 24.dp, accentColor = GradientElectricPurple) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp),
                )
                Text(
                  text = "আসসালামু আলাইকুম, সানজু 👋",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Ask me anything in Bangla or English—reminders, notes, translations, web summaries, or phone tools.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(uiState.conversationTurns, key = { it.id }) { turn ->
            val isMax = turn.speaker == "MAX"
            val formattedTime = remember(turn.timestampMillis) { timeFormat.format(Date(turn.timestampMillis)) }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (isMax) Arrangement.Start else Arrangement.End,
            ) {
              MAXGlassCard(
                cornerRadius = 24.dp,
                accentColor = if (isMax) GradientRoyalBlue else GradientElectricPurple,
                modifier = Modifier.widthIn(max = 560.dp),
              ) {
                Column(
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "${turn.speaker} • $formattedTime",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color =
                        if (isMax) {
                          MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.secondary
                        },
                    )
                    if (isMax) {
                      IconButton(
                        onClick = { onSpeakText(turn.content, uiState.settings.speechStyle) },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                      ) {
                        Icon(
                          imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                          contentDescription = "Speak reply aloud",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(20.dp),
                        )
                      }
                    }
                  }
                  Text(
                    text = turn.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }
      }

      // Quick Conversational Starter Chips
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val prompts =
          listOf(
            "আজকের ব্রিফিং দাও",
            "আমার নাম মনে রাখো সানজু",
            "এটা ইংরেজিতে অনুবাদ করো: আমি ভালো আছি",
            "Summarize today's notes",
          )
        items(prompts) { prompt ->
          AssistChip(
            onClick = { onSendMessage(prompt, true) },
            label = { Text(prompt) },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
          )
        }
      }

      // Composer Bar
      MAXGlassCard(cornerRadius = 24.dp) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Message MAX (Bangla or English)...") },
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).testTag("chat_input_field"),
          )
          Button(
            onClick = {
              val trimmed = draft.trim()
              if (trimmed.isNotEmpty()) {
                onSendMessage(trimmed, true)
                draft = ""
              }
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 52.dp).testTag("chat_send_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send chat message")
          }
        }
      }
    }
  }
}
