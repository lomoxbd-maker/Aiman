package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.CoreLifecycleState
import com.example.emotion.EmotionType
import com.example.permissions.AppPermission
import com.example.permissions.PermissionStatus
import com.example.speech.SpeechState
import com.example.tts.SpeechStyle
import com.example.tts.VoiceLanguage
import com.example.tts.VoiceState
import com.example.ui.components.DormantChip
import com.example.ui.components.PrivacyShieldBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubsystemMetricCard
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GradientCyanGlow
import com.example.ui.theme.GradientElectricPurple
import com.example.ui.theme.GradientRoyalBlue
import com.example.viewmodel.MAXUiState
import com.example.wakeword.WakeWordState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val DEFAULT_BANGLA_SAMPLE =
  "আসসালামু আলাইকুম, আমি ম্যাক্স। আমি শান্তভাবে আপনার কথা শুনছি। বলুন, আমি কীভাবে সাহায্য করতে পারি?"
private const val DEFAULT_ENGLISH_SAMPLE =
  "Hello, I am MAX. My voice system is calm and ready. How can I help you today?"

private data class QuickActionSpec(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val tint: Color,
  val testTag: String,
  val onClick: () -> Unit,
)

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
  onActivateManualVoiceSession: () -> Unit = onSimulateWakeWordMax,
  onFinishFollowUpToWakeMode: () -> Unit = onReturnToStandby,
  onOpenCameraQuick: () -> Unit = {},
  onOpenGalleryQuick: () -> Unit = {},
  onOpenInternetScreen: () -> Unit = {},
  onOpenPhoneScreen: () -> Unit = {},
  onOpenWhatsAppQuick: () -> Unit = {},
  onOpenAppsScreen: () -> Unit = {},
  onOpenFilesSmartScreen: () -> Unit = {},
  onOpenSettingsQuick: () -> Unit = {},
  onRunRoutine: (com.example.automation.RoutineCategory) -> Unit = {},
  onToggleFloatingBubble: () -> Unit = {},
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
  var showVoiceLabExpanded by remember { mutableStateOf(false) }

  // Battery-conscious clock updater (refreshes every 15 seconds while visible)
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(15_000L)
      nowMillis = System.currentTimeMillis()
    }
  }

  val lowPowerMotion =
    uiState.settings.lowPowerStandbyMode || (uiState.compatibility?.isPowerSaveMode == true)

  val quickActions =
    remember(
      onOpenCameraQuick,
      onOpenGalleryQuick,
      onOpenInternetScreen,
      onOpenPhoneScreen,
      onOpenWhatsAppQuick,
      onOpenFilesSmartScreen,
      onOpenSettingsQuick,
    ) {
      listOf(
        QuickActionSpec(
          title = "Camera",
          subtitle = "Photo • QR",
          icon = Icons.Filled.PhotoCamera,
          tint = GradientRoyalBlue,
          testTag = "quick_action_camera",
          onClick = onOpenCameraQuick,
        ),
        QuickActionSpec(
          title = "Gallery",
          subtitle = "Photos • OCR",
          icon = Icons.Filled.PhotoLibrary,
          tint = GradientElectricPurple,
          testTag = "quick_action_gallery",
          onClick = onOpenGalleryQuick,
        ),
        QuickActionSpec(
          title = "Browser",
          subtitle = "Web • News",
          icon = Icons.Filled.Public,
          tint = GradientCyanGlow,
          testTag = "quick_action_browser",
          onClick = onOpenInternetScreen,
        ),
        QuickActionSpec(
          title = "Phone",
          subtitle = "Calls • Tools",
          icon = Icons.Filled.PhoneAndroid,
          tint = EmeraldReady,
          testTag = "quick_action_phone",
          onClick = onOpenPhoneScreen,
        ),
        QuickActionSpec(
          title = "WhatsApp",
          subtitle = "Confirm open",
          icon = Icons.AutoMirrored.Filled.Chat,
          tint = EmeraldReady,
          testTag = "quick_action_whatsapp",
          onClick = onOpenWhatsAppQuick,
        ),
        QuickActionSpec(
          title = "Notes",
          subtitle = "Smart notes",
          icon = Icons.Filled.EditNote,
          tint = GradientRoyalBlue,
          testTag = "quick_action_notes",
          onClick = onOpenFilesSmartScreen,
        ),
        QuickActionSpec(
          title = "Reminder",
          subtitle = "Alarms • Tasks",
          icon = Icons.Filled.AlarmAdd,
          tint = AmberStandby,
          testTag = "quick_action_reminder",
          onClick = onOpenFilesSmartScreen,
        ),
        QuickActionSpec(
          title = "Settings",
          subtitle = "Voice • Safe",
          icon = Icons.Filled.Settings,
          tint = GradientElectricPurple,
          testTag = "quick_action_settings",
          onClick = onOpenSettingsQuick,
        ),
      )
    }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWide = maxWidth >= 640.dp

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // 1. TOP AREA: Premium Logo + Sanju Dynamic Greeting + Time & Date
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 780.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          SanjuTopGreetingHeader(
            nowMillis = nowMillis,
            coreState = uiState.coreState,
          )

          // 2. CENTER AREA: Large Circular Animated Mic Button + Status Text
          CenterVoiceOrbSection(
            uiState = uiState,
            hasAudioPermission = hasAudioPermission,
            lowPowerMotion = lowPowerMotion,
            onMicTapped = {
              if (!hasAudioPermission) {
                onRequestWakeWordPermissionWithExplanation()
              } else {
                onActivateManualVoiceSession()
              }
            },
            onArmStandby = {
              if (uiState.wakeWordState is WakeWordState.StandbyListening) {
                onDisarmWakeWordStandby()
              } else if (hasAudioPermission) {
                onArmWakeWordStandby()
              } else {
                onRequestWakeWordPermissionWithExplanation()
              }
            },
            onTriggerWakeWord = onSimulateWakeWordMax,
            onReturnToStandby = onReturnToStandby,
            onDismissFollowUp = onFinishFollowUpToWakeMode,
            onRequestPermission = onRequestWakeWordPermissionWithExplanation,
          )

          // 3. QUICK ACTION CARDS (Camera, Gallery, Browser, Phone, WhatsApp, Notes, Reminder, Settings)
          SectionHeader(
            title = "Quick Actions",
            trailingBadge = "Permission Protected",
          )

          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (isWide) 4 else 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            quickActions.forEach { action ->
              QuickActionTileCard(
                spec = action,
                modifier = Modifier.weight(1f),
              )
            }
          }

          // Smart Routines (Morning, Night, Daily Briefing) + Floating Bubble Launch
          SectionHeader(
            title = "Smart Routines & Overlay",
            trailingBadge = if (uiState.floatingState is com.example.floating.FloatingState.Inactive) "Offline Ready" else "Bubble Active",
          )

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              AssistChip(
                onClick = { onRunRoutine(com.example.automation.RoutineCategory.MORNING) },
                label = { Text("Morning Routine") },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("routine_morning_chip"),
              )
            }
            item {
              AssistChip(
                onClick = { onRunRoutine(com.example.automation.RoutineCategory.DAILY_BRIEFING) },
                label = { Text("Daily Briefing") },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("routine_briefing_chip"),
              )
            }
            item {
              AssistChip(
                onClick = { onRunRoutine(com.example.automation.RoutineCategory.NIGHT) },
                label = { Text("Night Routine") },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("routine_night_chip"),
              )
            }
            item {
              AssistChip(
                onClick = onToggleFloatingBubble,
                label = {
                  Text(
                    if (uiState.floatingState is com.example.floating.FloatingState.Inactive) {
                      "Floating Bubble"
                    } else {
                      "Hide Bubble"
                    },
                  )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("toggle_floating_bubble_chip"),
              )
            }
          }

          // 4. BOTTOM AREA: Last Completed Task + Suggested Actions + Recent Conversations
          LastCompletedTaskStrip(lastTask = uiState.lastCompletedTask)

          SuggestedActionsSection(
            uiState = uiState,
            onSuggestionClick = { prompt ->
              onSendConversationalMessage(prompt, true)
            },
          )

          RecentConversationsCard(
            uiState = uiState,
            moodInput = moodInput,
            onMoodInputChange = { moodInput = it },
            onSelectEmotion = onSelectEmotion,
            onSendMessage = { text ->
              onSendConversationalMessage(text, true)
              moodInput = ""
            },
            onQuickPrompt = { sample -> onSendConversationalMessage(sample, true) },
          )

          // Collapsible Bangla Voice & TTS Studio + Full Workspace Links (keeps all tools 1-tap away)
          BanglaTtsAndWorkspaceCard(
            uiState = uiState,
            voiceDraft = voiceDraft,
            onVoiceDraftChange = { voiceDraft = it },
            expanded = showVoiceLabExpanded,
            onToggleExpanded = { showVoiceLabExpanded = !showVoiceLabExpanded },
            onSelectLanguage = onSelectLanguage,
            onSelectStyle = onSelectStyle,
            onSpeakText = onSpeakText,
            onPauseSpeaking = onPauseSpeaking,
            onResumeSpeaking = onResumeSpeaking,
            onStopSpeaking = onStopSpeaking,
            onOpenInternetScreen = onOpenInternetScreen,
            onOpenPhoneScreen = onOpenPhoneScreen,
            onOpenAppsScreen = onOpenAppsScreen,
            onOpenFilesSmartScreen = onOpenFilesSmartScreen,
          )

          // Subsystem Readiness Summary
          SectionHeader(title = "Subsystem Readiness", trailingBadge = "16 Modules")

          if (isWide) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              WakeMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              SpeechMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              EmotionMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              TtsMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                WakeMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
                SpeechMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                EmotionMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
                TtsMetricCard(uiState = uiState, modifier = Modifier.weight(1f))
              }
              MemoryMetricCard(uiState = uiState, modifier = Modifier.fillMaxWidth())
            }
          }

          // Dormant Location Privacy Strip + Device Compatibility Note
          Card(
            shape = RoundedCornerShape(18.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Dormant Capabilities (Privacy Locked)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              DormantChip("Location")
            }
          }

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
                    text = "Device Compatibility (${compat.deviceModel})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  compat.fallbackNotes.forEach { note ->
                    Text(
                      text = "• $note",
                      style = MaterialTheme.typography.bodySmall,
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
private fun SanjuTopGreetingHeader(
  nowMillis: Long,
  coreState: CoreLifecycleState,
) {
  val dateObj = remember(nowMillis) { Date(nowMillis) }
  val timeText =
    remember(nowMillis) {
      SimpleDateFormat("hh:mm a", Locale.getDefault()).format(dateObj)
    }
  val dateText =
    remember(nowMillis) {
      SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(dateObj)
    }
  val hour =
    remember(nowMillis) {
      Calendar.getInstance().apply { time = dateObj }.get(Calendar.HOUR_OF_DAY)
    }
  val timeOfDaySubtitle =
    when (hour) {
      in 5..11 -> "শুভ সকাল • Good Morning"
      in 12..16 -> "শুভ অপরাহ্ন • Good Afternoon"
      in 17..20 -> "শুভ সন্ধ্যা • Good Evening"
      else -> "শান্ত রাত্রি • Peaceful Night"
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            brush =
              Brush.linearGradient(
                colors =
                  listOf(
                    GradientRoyalBlue,
                    GradientElectricPurple,
                  ),
              ),
          )
          .padding(18.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Premium MAX Logo Badge
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier =
                Modifier.size(46.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.18f))
                  .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "MAX Assistant Logo",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
              )
            }
            Column {
              Text(
                text = "MAX",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
              )
              Text(
                text = timeOfDaySubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.18f),
          ) {
            Text(
              text = coreState.name.replace("_", " "),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "আসসালামু আলাইকুম, সানজু",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
          )
          Text(
            text = "Welcome Back, Sanju",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.92f),
          )
        }

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color.Black.copy(alpha = 0.18f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = "Current time and date",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = timeText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
              )
            }
            Text(
              text = dateText,
              style = MaterialTheme.typography.labelMedium,
              color = Color.White.copy(alpha = 0.9f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CenterVoiceOrbSection(
  uiState: MAXUiState,
  hasAudioPermission: Boolean,
  lowPowerMotion: Boolean,
  onMicTapped: () -> Unit,
  onArmStandby: () -> Unit,
  onTriggerWakeWord: () -> Unit,
  onReturnToStandby: () -> Unit,
  onDismissFollowUp: () -> Unit,
  onRequestPermission: () -> Unit,
) {
  val isSpeaking = uiState.voiceState is VoiceState.Speaking
  val isThinking = uiState.isThinking
  val isListening =
    uiState.coreState == CoreLifecycleState.LISTENING_PREPARED ||
      uiState.coreState == CoreLifecycleState.AWAITING_FOLLOW_UP ||
      uiState.speechState == SpeechState.LISTENING_ACTIVE ||
      uiState.speechState == SpeechState.PREPARED_FOR_CONVERSATION

  val statusLabel =
    when {
      isThinking -> "Thinking..."
      isSpeaking -> "Speaking..."
      isListening -> "Listening..."
      else -> "Waiting..."
    }

  val statusHint =
    when {
      isThinking -> "শান্তভাবে আপনার উত্তর প্রস্তুত করছি..."
      isSpeaking -> "MAX সুন্দর ও স্পষ্ট কণ্ঠে কথা বলছে..."
      isListening -> "বলুন সানজু, আমি মনোযোগ দিয়ে শুনছি"
      uiState.wakeWordState is WakeWordState.StandbyListening -> "Say \"MAX\" or tap the microphone"
      else -> "Tap mic to speak or arm \"MAX\" wake standby"
    }

  val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
  val glowScale by
    infiniteTransition.animateFloat(
      initialValue = 1.0f,
      targetValue = if (isSpeaking) 1.24f else if (isListening) 1.18f else 1.06f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (lowPowerMotion) 2200 else if (isSpeaking) 900 else 1500,
              easing = FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orb_glow_scale",
    )

  val floatOffset by
    infiniteTransition.animateFloat(
      initialValue = -3f,
      targetValue = 3f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orb_float",
    )

  val orbAccent by
    animateColorAsState(
      targetValue =
        when {
          isSpeaking -> GradientElectricPurple
          isListening -> GradientCyanGlow
          isThinking -> AmberStandby
          else -> GradientRoyalBlue
        },
      label = "orb_accent_color",
    )

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.93f else 1.0f,
    label = "mic_press_scale",
  )

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, orbAccent.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Animated Circular Microphone Button
      Box(
        modifier =
          Modifier.size(148.dp)
            .graphicsLayer {
              translationY = if (lowPowerMotion) 0f else floatOffset
            },
        contentAlignment = Alignment.Center,
      ) {
        // Outer Listening Glow Ring
        Box(
          modifier =
            Modifier.size(134.dp)
              .scale(glowScale)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  colors =
                    listOf(
                      orbAccent.copy(alpha = if (isListening || isSpeaking) 0.34f else 0.14f),
                      Color.Transparent,
                    ),
                ),
              ),
        )

        // Middle Ripple Ring
        Box(
          modifier =
            Modifier.size(112.dp)
              .scale(if (isSpeaking || isListening) (glowScale * 0.96f) else 1f)
              .clip(CircleShape)
              .border(
                width = if (isSpeaking || isListening) 2.dp else 1.dp,
                color = orbAccent.copy(alpha = 0.55f),
                shape = CircleShape,
              ),
        )

        // Interactive 94dp Primary Mic Circle (>48dp touch target)
        Surface(
          onClick = onMicTapped,
          interactionSource = interactionSource,
          shape = CircleShape,
          modifier =
            Modifier.size(94.dp)
              .scale(pressScale)
              .shadow(
                elevation = if (isListening || isSpeaking) 12.dp else 6.dp,
                shape = CircleShape,
                ambientColor = orbAccent,
                spotColor = orbAccent,
              )
              .semantics { contentDescription = "MAX Microphone Button: $statusLabel" }
              .testTag("home_mic_orb_button"),
          color = Color.Transparent,
        ) {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.linearGradient(
                    colors =
                      when {
                        isSpeaking -> listOf(GradientElectricPurple, GradientRoyalBlue)
                        isListening -> listOf(GradientRoyalBlue, GradientCyanGlow)
                        else -> listOf(GradientRoyalBlue, GradientElectricPurple)
                      },
                  ),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                when {
                  isSpeaking -> Icons.Filled.GraphicEq
                  isListening -> Icons.Filled.Mic
                  else -> Icons.Filled.Mic
                },
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(38.dp),
            )
          }
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          color = orbAccent,
          modifier = Modifier.testTag("home_orb_status_text"),
        )
        Text(
          text = statusHint,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Sanju Follow-Up Prompt Card (appears right after task completion)
      AnimatedVisibility(
        visible = !uiState.followUpPrompt.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = uiState.followUpPrompt.orEmpty(),
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              OutlinedButton(
                onClick = onDismissFollowUp,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("dismiss_followup_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text("না, ধন্যবাদ • Return to Wake Mode")
              }
            }
          }
        }
      }

      // Wake Word Control Pills (Preserves existing standby/trigger tests & flows)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (uiState.coreState == CoreLifecycleState.LISTENING_PREPARED) {
          Button(
            onClick = onReturnToStandby,
            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("return_standby_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Return to Standby")
          }
        } else {
          val isStandby = uiState.wakeWordState is WakeWordState.StandbyListening
          Button(
            onClick = onArmStandby,
            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("toggle_wake_standby_button"),
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
            Spacer(Modifier.width(6.dp))
            Text(
              text =
                when {
                  isStandby -> "Disarm Standby"
                  hasAudioPermission -> "Arm \"MAX\""
                  else -> "Enable Standby"
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        FilledTonalButton(
          onClick = onTriggerWakeWord,
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("simulate_wake_word_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.Bolt, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Trigger \"MAX\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      if (!hasAudioPermission) {
        OutlinedButton(
          onClick = onRequestPermission,
          modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("request_wake_mic_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("Grant Microphone Permission (Wake Word Only)")
        }
      }
    }
  }
}

@Composable
private fun QuickActionTileCard(
  spec: QuickActionSpec,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.96f else 1.0f,
    label = "quick_action_press_scale",
  )
  Card(
    onClick = spec.onClick,
    interactionSource = interactionSource,
    modifier = modifier.scale(scale).widthIn(min = 132.dp).defaultMinSize(minHeight = 80.dp).testTag(spec.testTag),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(spec.tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = spec.icon,
          contentDescription = spec.title,
          tint = spec.tint,
          modifier = Modifier.size(22.dp),
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = spec.title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = spec.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LastCompletedTaskStrip(lastTask: String?) {
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("last_completed_task_card"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Last completed task",
        tint = EmeraldReady,
        modifier = Modifier.size(20.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Last Completed Task",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = lastTask?.takeIf { it.isNotBlank() } ?: "Ready for your first request, Sanju",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun SuggestedActionsSection(
  uiState: MAXUiState,
  onSuggestionClick: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionHeader(title = "Suggested Actions", trailingBadge = "For Sanju")
    val suggestions = uiState.aiSuggestions
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (suggestions.isNotEmpty()) {
        items(suggestions, key = { it.id }) { suggestion ->
          val displayTitle =
            if (uiState.settings.voiceLanguage == VoiceLanguage.ENGLISH_US) {
              suggestion.titleEnglish
            } else {
              suggestion.titleBangla
            }
          Card(
            onClick = { onSuggestionClick(suggestion.suggestedActionCommand) },
            modifier = Modifier.widthIn(min = 180.dp, max = 250.dp).defaultMinSize(minHeight = 64.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = displayTitle,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = suggestion.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      } else {
        val fallbackPrompts =
          listOf(
            "আজকের ক্যালেন্ডার দেখাও",
            "Google-এ AI খবর খুঁজে দেখো",
            "আমি খুব ভালো আছি",
          )
        items(fallbackPrompts) { prompt ->
          AssistChip(
            onClick = { onSuggestionClick(prompt) },
            label = { Text(prompt) },
          )
        }
      }
    }
  }
}

@Composable
private fun RecentConversationsCard(
  uiState: MAXUiState,
  moodInput: String,
  onMoodInputChange: (String) -> Unit,
  onSelectEmotion: (EmotionType) -> Unit,
  onSendMessage: (String) -> Unit,
  onQuickPrompt: (String) -> Unit,
) {
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
        title = "Recent Conversations & Mood",
        trailingBadge = uiState.emotionState.currentEmotion.displayName,
      )

      Text(
        text = "Mood: ${uiState.emotionState.detectedUserMood.displayName} • ${uiState.emotionState.responseStyle.toneDescription}",
        style = MaterialTheme.typography.bodySmall,
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
            onClick = { onQuickPrompt(sample) },
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
          onValueChange = onMoodInputChange,
          placeholder = { Text("Speak or type (Bangla or English)...") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("emotion_message_input"),
        )
        Button(
          onClick = {
            if (moodInput.isNotBlank()) {
              onSendMessage(moodInput)
            }
          },
          modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("emotion_send_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message to MAX")
        }
      }

      if (uiState.conversationTurns.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          uiState.conversationTurns.takeLast(4).forEach { turn ->
            val isMax = turn.speaker == "MAX"
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              color =
                if (isMax) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            ) {
              Text(
                text = "${turn.speaker}: ${turn.content}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BanglaTtsAndWorkspaceCard(
  uiState: MAXUiState,
  voiceDraft: String,
  onVoiceDraftChange: (String) -> Unit,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onSelectLanguage: (VoiceLanguage) -> Unit,
  onSelectStyle: (SpeechStyle) -> Unit,
  onSpeakText: (String, SpeechStyle?) -> Unit,
  onPauseSpeaking: () -> Unit,
  onResumeSpeaking: () -> Unit,
  onStopSpeaking: () -> Unit,
  onOpenInternetScreen: () -> Unit,
  onOpenPhoneScreen: () -> Unit,
  onOpenAppsScreen: () -> Unit,
  onOpenFilesSmartScreen: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Bangla Voice Studio & Modules", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Natural ${uiState.settings.voiceLanguage.displayLabel} • ${"%.2f".format(uiState.settings.ttsSpeechRate)}x calm pace",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(
          onClick = onToggleExpanded,
          modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
          Text(if (expanded) "Hide" else "Voice Lab")
          Spacer(Modifier.width(4.dp))
          Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            onValueChange = onVoiceDraftChange,
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
                  modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("tts_pause_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Pause, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text("Pause")
                }
                OutlinedButton(
                  onClick = onStopSpeaking,
                  modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("tts_stop_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Stop, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text("Stop")
                }
              }
              is VoiceState.Paused -> {
                Button(
                  onClick = onResumeSpeaking,
                  modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("tts_resume_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text("Resume")
                }
                OutlinedButton(
                  onClick = onStopSpeaking,
                  modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("tts_stop_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Stop, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text("Stop")
                }
              }
              else -> {
                Button(
                  onClick = { onSpeakText(voiceDraft, uiState.settings.speechStyle) },
                  modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("tts_speak_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Speak (${uiState.settings.speechStyle.displayName})")
                }
              }
            }
          }
        }
      }

      // Dedicated Workspace Screen Shortcuts (preserving all existing screen buttons & testTags)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = onOpenInternetScreen,
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("open_internet_screen_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Browser", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
          onClick = onOpenPhoneScreen,
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("open_phone_screen_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Phone", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = onOpenAppsScreen,
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("open_apps_screen_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("All Apps", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
          onClick = onOpenFilesSmartScreen,
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("open_files_smart_screen_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Files & Smart", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
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
      SpeechState.LISTENING_ACTIVE -> "LISTENING"
      SpeechState.PREPARED_FOR_CONVERSATION -> "PREPARED"
      SpeechState.DORMANT_STANDBY -> "GATED OFF"
      SpeechState.UNSUPPORTED_FALLBACK -> "TEXT FALLBACK"
      else -> uiState.speechState.name
    }
  val accent =
    if (uiState.speechState == SpeechState.PREPARED_FOR_CONVERSATION ||
      uiState.speechState == SpeechState.LISTENING_ACTIVE
    ) {
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
    icon = Icons.AutoMirrored.Filled.VolumeUp,
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
