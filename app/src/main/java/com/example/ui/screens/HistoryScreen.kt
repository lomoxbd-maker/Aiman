package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.MAXGlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubsystemMetricCard
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GradientCyanGlow
import com.example.ui.theme.GradientElectricPurple
import com.example.ui.theme.GradientRoyalBlue
import com.example.viewmodel.MAXUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class WorkspaceHubItem(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val tint: Color,
  val testTag: String,
  val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
  uiState: MAXUiState,
  onDeleteMemoryById: (String) -> Unit,
  onClearMemories: () -> Unit,
  onOpenVisionScreen: () -> Unit,
  onOpenModulesScreen: () -> Unit,
  onOpenDiagnosticsScreen: () -> Unit,
  onOpenInternetScreen: () -> Unit,
  onOpenPhoneScreen: () -> Unit,
  onOpenAppsScreen: () -> Unit,
  onOpenFilesSmartScreen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val timeFormat = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

  val hubItems =
    remember(
      onOpenVisionScreen,
      onOpenFilesSmartScreen,
      onOpenPhoneScreen,
      onOpenInternetScreen,
      onOpenAppsScreen,
      onOpenModulesScreen,
      onOpenDiagnosticsScreen,
    ) {
      listOf(
        WorkspaceHubItem(
          title = "Vision & Gallery",
          subtitle = "Camera • QR • OCR",
          icon = Icons.Filled.PhotoCamera,
          tint = GradientRoyalBlue,
          testTag = "hub_vision_button",
          onClick = onOpenVisionScreen,
        ),
        WorkspaceHubItem(
          title = "Files & Smart",
          subtitle = "PDF • Notes • Translate",
          icon = Icons.Filled.FolderOpen,
          tint = GradientElectricPurple,
          testTag = "hub_files_smart_button",
          onClick = onOpenFilesSmartScreen,
        ),
        WorkspaceHubItem(
          title = "Phone Tools",
          subtitle = "Calls • Alarms • Timer",
          icon = Icons.Filled.PhoneAndroid,
          tint = EmeraldReady,
          testTag = "hub_phone_button",
          onClick = onOpenPhoneScreen,
        ),
        WorkspaceHubItem(
          title = "Safe Browser",
          subtitle = "Weather • Web • Wiki",
          icon = Icons.Filled.Public,
          tint = GradientCyanGlow,
          testTag = "hub_internet_button",
          onClick = onOpenInternetScreen,
        ),
        WorkspaceHubItem(
          title = "App Control",
          subtitle = "WhatsApp • YouTube",
          icon = Icons.Filled.Apps,
          tint = AmberStandby,
          testTag = "hub_apps_button",
          onClick = onOpenAppsScreen,
        ),
        WorkspaceHubItem(
          title = "16 Modules",
          subtitle = "Core Architecture",
          icon = Icons.Filled.Hub,
          tint = GradientRoyalBlue,
          testTag = "hub_modules_button",
          onClick = onOpenModulesScreen,
        ),
        WorkspaceHubItem(
          title = "Diagnostics",
          subtitle = "Logs • Permissions",
          icon = Icons.Filled.BugReport,
          tint = GradientElectricPurple,
          testTag = "hub_diagnostics_button",
          onClick = onOpenDiagnosticsScreen,
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
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 780.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          SectionHeader(
            title = "Activity Overview",
            trailingBadge = "${uiState.conversationTurns.size} turns",
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SubsystemMetricCard(
              title = "Memories",
              value = "${uiState.structuredMemories.size}",
              subtitle = "User approved",
              icon = Icons.Filled.Memory,
              accent = EmeraldReady,
              modifier = Modifier.weight(1f),
            )
            SubsystemMetricCard(
              title = "Reminders",
              value = "${uiState.reminders.size}",
              subtitle = "${uiState.smartNotes.size} smart notes",
              icon = Icons.Filled.AutoAwesome,
              accent = GradientRoyalBlue,
              modifier = Modifier.weight(1f),
            )
          }

          if (!uiState.lastCompletedTask.isNullOrBlank()) {
            MAXGlassCard(cornerRadius = 24.dp, accentColor = EmeraldReady) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Last completed task",
                  tint = EmeraldReady,
                )
                Column {
                  Text(
                    text = "Latest Completed Action",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = uiState.lastCompletedTask,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }

          SectionHeader(title = "Recent Conversations", trailingBadge = "History")

          MAXGlassCard(cornerRadius = 24.dp) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (uiState.conversationTurns.isEmpty()) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = "No conversation history yet. Say \"MAX\" or send a message in Chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              } else {
                uiState.conversationTurns.takeLast(10).asReversed().forEach { turn ->
                  val stamp = remember(turn.timestampMillis) { timeFormat.format(Date(turn.timestampMillis)) }
                  MAXGlassCard(
                    cornerRadius = 18.dp,
                    accentColor =
                      if (turn.speaker == "MAX") GradientRoyalBlue else GradientElectricPurple,
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = turn.speaker,
                          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                          text = stamp,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = turn.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                  }
                }
              }
            }
          }

          SectionHeader(
            title = "Memory Vault",
            trailingBadge = "${uiState.structuredMemories.size} saved",
          )

          MAXGlassCard(cornerRadius = 24.dp) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (uiState.structuredMemories.isEmpty()) {
                Text(
                  text = "No saved personal memories yet. Say \"আমার নাম মনে রাখো...\" anytime.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                uiState.structuredMemories.forEach { memory ->
                  MAXGlassCard(cornerRadius = 18.dp) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = memory.type.displayName,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                          text = memory.content,
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurface,
                        )
                      }
                      IconButton(
                        onClick = { onDeleteMemoryById(memory.id) },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Delete,
                          contentDescription = "Delete memory",
                          tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                }
                OutlinedButton(
                  onClick = onClearMemories,
                  modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                  shape = RoundedCornerShape(16.dp),
                ) {
                  Text("Clear All Memories")
                }
              }
            }
          }

          SectionHeader(title = "MAX Modules & Tools", trailingBadge = "All Features")

          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (isWide) 3 else 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            hubItems.forEach { item ->
              MAXGlassCard(
                onClick = item.onClick,
                cornerRadius = 24.dp,
                accentColor = item.tint,
                modifier = Modifier.weight(1f).widthIn(min = 140.dp).defaultMinSize(minHeight = 76.dp).testTag(item.testTag),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                    Icon(
                      imageVector = item.icon,
                      contentDescription = item.title,
                      tint = item.tint,
                      modifier = Modifier.size(22.dp),
                    )
                  }
                  Column {
                    Text(
                      text = item.title,
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = item.subtitle,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}
