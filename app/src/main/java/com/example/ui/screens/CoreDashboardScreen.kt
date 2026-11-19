package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.core.CoreLifecycleState
import com.example.emotion.EmotionType
import com.example.permissions.AppPermission
import com.example.permissions.PermissionStatus
import com.example.speech.SpeechState
import com.example.tts.SpeechStyle
import com.example.tts.VoiceLanguage
import com.example.tts.VoiceState
import com.example.ui.components.CoreHeroBanner
import com.example.ui.components.DormantChip
import com.example.ui.components.PrivacyShieldBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubsystemMetricCard
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState
import com.example.wakeword.WakeWordState

private const val DEFAULT_BANGLA_SAMPLE =
  "নমস্কার, আমি ম্যাক্স। আমি শান্তভাবে আপনার কথা শুনছি। বলুন, আমি কীভাবে সাহায্য করতে পারি?"
private const val DEFAULT_ENGLISH_SAMPLE =
  "Hello, I am MAX. My voice system is calm and ready. How can I help you today?"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreDashboardScreen(
  uiState: MAXUiState,
  onArmWakeWordStandby: () -> Unit,
  onDisarmWakeWordStandby: () -> Unit,
  onSimulateWakeWordMax: () -> Unit,
  onReturnToStandby: () -> Unit,
  onRequestWakeWordPermissionWithExplanation: () -> Unit,
  onSendConversationalMessage: (String, Boolean) -> Unit,
  onSelectEmotion: (EmotionType) -> Unit,
  onSpeakText: (String, SpeechStyle?) -> Unit,
  onPauseSpeaking: () -> Unit,
  onResumeSpeaking: () -> Unit,
  onStopSpeaking: () -> Unit,
  onSelectLanguage: (VoiceLanguage) -> Unit,
  onSelectStyle: (SpeechStyle) -> Unit,
  onOpenInternetScreen: () -> Unit = {},
  onOpenPhoneScreen: () -> Unit = {},
  onOpenAppsScreen: () -> Unit = {},
  onOpenFilesSmartScreen: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val hasAudioPermission =
    uiState.permissionSnapshots
      .firstOrNull { it.permission == AppPermission.MICROPHONE_WAKE_WORD }
      ?.status == PermissionStatus.GRANTED

  var voiceDraft by
    remember(uiState.settings.voiceLanguage) {
      mutableStateOf(
        if (uiState.settings.voiceLanguage == VoiceLanguage.ENGLISH_US) {
          DEFAULT_ENGLISH_SAMPLE
        } else {
          DEFAULT_BANGLA_SAMPLE
        },
      )
    }

  var moodInput by remember { mutableStateOf("") }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWide = maxWidth >= 640.dp

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          CoreHeroBanner(
            coreState = uiState.coreState,
            wakeWordState = uiState.wakeWordState,
            lastWakeWordEvent = uiState.lastWakeWordEvent,
          )

          // Wake Word Control Deck ("MAX")
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
                title = "Wake Word Control • \"MAX\"",
                trailingBadge = "Detections: ${uiState.wakeWordDetections}",
              )

              Text(
                text =
                  if (uiState.coreState == CoreLifecycleState.LISTENING_PREPARED) {
                    "Wake word \"MAX\" detected. MAXCore activated listening state and prepared speech/conversation pipelines."
                  } else {
                    "MAX waits in low-power standby for \"MAX\". Full speech recognition stays OFF until wake word detection."
                  },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (uiState.coreState == CoreLifecycleState.LISTENING_PREPARED) {
                  Button(
                    onClick = onReturnToStandby,
                    modifier = Modifier.weight(1f).testTag("return_standby_button"),
                    shape = RoundedCornerShape(14.dp),
                  ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.widthIn(min = 6.dp))
                    Text("Return to Standby")
                  }
                } else {
                  val isStandby = uiState.wakeWordState is WakeWordState.StandbyListening
                  Button(
                    onClick = {
                      if (isStandby) {
                        onDisarmWakeWordStandby()
                      } else if (hasAudioPermission) {
                        onArmWakeWordStandby()
                      } else {
                        onRequestWakeWordPermissionWithExplanation()
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("toggle_wake_standby_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                      if (isStandby) {
                        ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.secondaryContainer,
                          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                      } else {
                        ButtonDefaults.buttonColors()
                      },
                  ) {
                    Icon(
                      imageVector = if (isStandby) Icons.Filled.MicOff else Icons.Filled.Mic,
                      contentDescription = null,
                    )
                    Spacer(Modifier.widthIn(min = 6.dp))
                    Text(
                      text =
                        when {
                          isStandby -> "Disarm Standby"
                          hasAudioPermission -> "Arm \"MAX\" Standby"
                          else -> "Enable Mic Standby"
                        },
                    )
                  }
                }

                FilledTonalButton(
                  onClick = onSimulateWakeWordMax,
                  modifier = Modifier.weight(1f).testTag("simulate_wake_word_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Bolt, contentDescription = null)
                  Spacer(Modifier.widthIn(min = 6.dp))
                  Text("Trigger \"MAX\"")
                }
              }

              if (!hasAudioPermission) {
                OutlinedButton(
                  onClick = onRequestWakeWordPermissionWithExplanation,
                  modifier = Modifier.fillMaxWidth().testTag("request_wake_mic_button"),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Text("Grant Microphone Permission (Wake Word Only)")
                }
              }
            }
          }

          // MAX Emotion & Natural Conversation System (Step 4)
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
                title = "Emotion & Personality",
                trailingBadge = uiState.emotionState.currentEmotion.displayName,
              )

              Text(
                text =
                  "Mood: ${uiState.emotionState.detectedUserMood.displayName} • ${uiState.emotionState.responseStyle.toneDescription}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EmotionType.entries) { emotion ->
                  FilterChip(
                    selected = uiState.emotionState.currentEmotion == emotion,
                    onClick = { onSelectEmotion(emotion) },
                    label = { Text(emotion.displayName) },
                    modifier = Modifier.testTag("emotion_chip_${emotion.name.lowercase()}"),
                  )
                }
              }

              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val quickPrompts =
                  listOf(
                    "আমি খুব খুশি!",
                    "আমি একটু চিন্তিত আছি",
                    "Can you help me?",
                    "That is awesome news!",
                    "Thank you MAX",
                  )
                items(quickPrompts) { sample ->
                  AssistChip(
                    onClick = { onSendConversationalMessage(sample, true) },
                    label = { Text(sample) },
                    modifier = Modifier.testTag("mood_preset_${sample.hashCode()}"),
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedTextField(
                  value = moodInput,
                  onValueChange = { moodInput = it },
                  placeholder = { Text("Share how you feel (Bangla or English)...") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("emotion_message_input"),
                )
                Button(
                  onClick = {
                    if (moodInput.isNotBlank()) {
                      onSendConversationalMessage(moodInput, true)
                      moodInput = ""
                    }
                  },
                  modifier = Modifier.testTag("emotion_send_button"),
                ) {
                  Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send mood message")
                }
              }

              if (uiState.conversationTurns.isNotEmpty()) {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    uiState.conversationTurns.takeLast(4).forEach { turn ->
                      Text(
                        text = "${turn.speaker}: ${turn.content}",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                          if (turn.speaker == "MAX") {
                            MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.onSurface
                          },
                      )
                    }
                  }
                }
              }
            }
          }

          // MAX Bangla & Natural Voice Synthesis Deck (Step 3)
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              val voiceBadge =
                when (val vState = uiState.voiceState) {
                  is VoiceState.Speaking ->
                    "Speaking ${vState.currentSentenceIndex + 1}/${vState.totalSentences}"
                  is VoiceState.Paused ->
                    "Paused ${vState.resumeFromSentenceIndex + 1}/${vState.totalSentences}"
                  is VoiceState.Ready ->
                    if (vState.isFallbackVoice) "Fallback (${vState.activeLocaleTag})"
                    else "Natural (${vState.activeLocaleTag})"
                  is VoiceState.UnsupportedFallback -> "Text Fallback"
                  VoiceState.Initializing -> "Initializing"
                  else -> "Offline"
                }

              SectionHeader(
                title = "Voice & Bangla TTS",
                trailingBadge = voiceBadge,
              )

              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VoiceLanguage.entries) { lang ->
                  FilterChip(
                    selected = uiState.settings.voiceLanguage == lang,
                    onClick = { onSelectLanguage(lang) },
                    label = { Text(lang.displayLabel) },
                    modifier = Modifier.testTag("voice_lang_${lang.name.lowercase()}"),
                  )
                }
              }

              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SpeechStyle.entries) { style ->
                  FilterChip(
                    selected = uiState.settings.speechStyle == style,
                    onClick = { onSelectStyle(style) },
                    label = { Text(style.displayName) },
                    modifier = Modifier.testTag("voice_style_${style.name.lowercase()}"),
                  )
                }
              }

              OutlinedTextField(
                value = voiceDraft,
                onValueChange = { voiceDraft = it },
                label = { Text("Bangla / Multilingual Speech Test") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("tts_input_field"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                when (uiState.voiceState) {
                  is VoiceState.Speaking -> {
                    Button(
                      onClick = onPauseSpeaking,
                      modifier = Modifier.weight(1f).testTag("tts_pause_button"),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Filled.Pause, contentDescription = null)
                      Spacer(Modifier.widthIn(min = 4.dp))
                      Text("Pause")
                    }
                    OutlinedButton(
                      onClick = onStopSpeaking,
                      modifier = Modifier.weight(1f).testTag("tts_stop_button"),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Filled.Stop, contentDescription = null)
                      Spacer(Modifier.widthIn(min = 4.dp))
                      Text("Stop")
                    }
                  }
                  is VoiceState.Paused -> {
                    Button(
                      onClick = onResumeSpeaking,
                      modifier = Modifier.weight(1f).testTag("tts_resume_button"),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = null)
                      Spacer(Modifier.widthIn(min = 4.dp))
                      Text("Resume")
                    }
                    OutlinedButton(
                      onClick = onStopSpeaking,
                      modifier = Modifier.weight(1f).testTag("tts_stop_button"),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Filled.Stop, contentDescription = null)
                      Spacer(Modifier.widthIn(min = 4.dp))
                      Text("Stop")
                    }
                  }
                  else -> {
                    Button(
                      onClick = { onSpeakText(voiceDraft, uiState.settings.speechStyle) },
                      modifier = Modifier.fillMaxWidth().testTag("tts_speak_button"),
                      shape = RoundedCornerShape(14.dp),
                    ) {
                      Icon(Icons.Filled.VolumeUp, contentDescription = null)
                      Spacer(Modifier.widthIn(min = 6.dp))
                      Text("Speak (${uiState.settings.speechStyle.displayName})")
                    }
                  }
                }
              }
            }
          }

          // Subsystem Readiness Grid
          SectionHeader(title = "Subsystem Readiness", trailingBadge = "MVVM Core")

          if (isWide) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              SubsystemGridCards(uiState = uiState, modifier = Modifier.weight(1f))
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                WakeMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
                SpeechMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                EmotionMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
                TtsMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              }
              MemoryMetricCard(uiState = uiState, modifier = Modifier.fillMaxWidth())
            }
          }

          // Safe Internet & Web Browser Access Card (Step 8)
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Web Search & Safe Browser",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Google Web, News, Weather, Wikipedia, YouTube & Bangla summaries — on your command only.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onOpenInternetScreen,
                modifier = Modifier.testTag("open_internet_screen_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Public, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Explore")
              }
            }
          }

          // Phone & Utilities Control Card (Step 9)
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Phone & Utilities Control",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Calls, SMS, Contacts, Alarm, Timer, Stopwatch, Calculator & Flashlight — confirmation-locked.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onOpenPhoneScreen,
                modifier = Modifier.testTag("open_phone_screen_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Open")
              }
            }
          }

          // Third-Party Apps Control Card (Step 10)
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Apps Control & Safe Launcher",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "WhatsApp, Messenger, Facebook, Chrome, YouTube, Gmail & Maps — confirmation-locked with alternatives.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onOpenAppsScreen,
                modifier = Modifier.testTag("open_apps_screen_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Apps, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Apps")
              }
            }
          }

          // Files, OCR & Smart Features Card (Step 11)
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Files, OCR & Smart Tools",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "PDF, Docs, Image OCR, Offline Translation, Reminders, Notes, Calendar, Voice Notes & AI Suggestions.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onOpenFilesSmartScreen,
                modifier = Modifier.testTag("open_files_smart_screen_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Smart")
              }
            }
          }

          // Dormant Capabilities Strip
          Card(
            shape = RoundedCornerShape(18.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "Dormant Capabilities (Privacy Locked)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                DormantChip("Location")
              }
            }
          }

          // Device Compatibility & Fallbacks
          uiState.compatibility?.let { compat ->
            if (compat.fallbackNotes.isNotEmpty() || compat.isLowRamDevice || compat.isPowerSaveMode) {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = "Device Compatibility & Fallbacks (${compat.deviceModel})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  compat.fallbackNotes.forEach { note ->
                    Text(
                      text = "• $note",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }

          PrivacyShieldBanner()
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}

@Composable
private fun SubsystemGridCards(uiState: MAXUiState, modifier: Modifier = Modifier) {
  WakeMetricCard(uiState = uiState, modifier = modifier)
  SpeechMetricCard(uiState = uiState, modifier = modifier)
  EmotionMetricCard(uiState = uiState, modifier = modifier)
  TtsMetricCard(uiState = uiState, modifier = modifier)
}

@Composable
private fun WakeMetricCard(uiState: MAXUiState, modifier: Modifier = Modifier) {
  val label =
    when (uiState.wakeWordState) {
      is WakeWordState.StandbyListening -> "STANDBY"
      is WakeWordState.Detected -> "DETECTED"
      is WakeWordState.PausedForConversation -> "PAUSED"
      is WakeWordState.PermissionRequired -> "NEEDS MIC"
      is WakeWordState.UnsupportedFallback -> "FALLBACK"
      is WakeWordState.Error -> "ERROR"
      WakeWordState.Idle -> "IDLE"
    }
  SubsystemMetricCard(
    title = "Wake Word",
    value = label,
    subtitle = "Keyword: \"MAX\"",
    icon = Icons.Filled.GraphicEq,
    accent = MaterialTheme.colorScheme.primary,
    modifier = modifier,
  )
}

@Composable
private fun SpeechMetricCard(uiState: MAXUiState, modifier: Modifier = Modifier) {
  val label =
    when (uiState.speechState) {
      SpeechState.PREPARED_FOR_CONVERSATION -> "PREPARED"
      SpeechState.DORMANT_STANDBY -> "GATED OFF"
      SpeechState.UNSUPPORTED_FALLBACK -> "TEXT FALLBACK"
      else -> uiState.speechState.name
    }
  val accent =
    if (uiState.speechState == SpeechState.PREPARED_FOR_CONVERSATION) {
      EmeraldReady
    } else {
      AmberStandby
    }
  SubsystemMetricCard(
    title = "Speech",
    value = label,
    subtitle = "Pre-wake gated",
    icon = Icons.Filled.RecordVoiceOver,
    accent = accent,
    modifier = modifier,
  )
}

@Composable
private fun EmotionMetricCard(uiState: MAXUiState, modifier: Modifier = Modifier) {
  SubsystemMetricCard(
    title = "Emotion",
    value = uiState.emotionState.currentEmotion.displayName,
    subtitle = "User: ${uiState.emotionState.detectedUserMood.displayName}",
    icon = Icons.Filled.Favorite,
    accent = MaterialTheme.colorScheme.tertiary,
    modifier = modifier,
  )
}

@Composable
private fun TtsMetricCard(uiState: MAXUiState, modifier: Modifier = Modifier) {
  SubsystemMetricCard(
    title = "TTS Voice",
    value = uiState.settings.voiceLanguage.displayLabel,
    subtitle = "${uiState.settings.speechStyle.displayName} • ${"%.2f".format(uiState.settings.ttsSpeechRate)}x",
    icon = Icons.Filled.VolumeUp,
    accent = MaterialTheme.colorScheme.secondary,
    modifier = modifier,
  )
}

@Composable
private fun MemoryMetricCard(uiState: MAXUiState, modifier: Modifier = Modifier) {
  SubsystemMetricCard(
    title = "Memory",
    value = "${uiState.structuredMemories.size} saved",
    subtitle = uiState.memoryState.name.replace("_", " "),
    icon = Icons.Filled.Memory,
    accent = EmeraldReady,
    modifier = modifier,
  )
}
