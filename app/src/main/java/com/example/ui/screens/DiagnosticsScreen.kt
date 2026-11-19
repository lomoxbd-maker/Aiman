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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.memory.MemoryType
import com.example.permissions.PermissionStatus
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.utils.LogLevel
import com.example.viewmodel.MAXUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
  uiState: MAXUiState,
  onSetLogFilter: (LogLevel?) -> Unit,
  onRunSelfCheck: () -> Unit,
  onClearLogs: () -> Unit,
  onAddMemoryNote: (MemoryType, String) -> Unit,
  onDeleteMemoryById: (String) -> Unit,
  onClearMemories: () -> Unit,
  onSetAllowSensitiveMemory: (Boolean) -> Unit,
  onRunBanglaMemoryCommand: (String) -> Unit,
  onSetPermissionAllowedByUser: (com.example.permissions.AppPermission, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var memoryDraft by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(MemoryType.CONVERSATION_CONTEXT) }
  val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

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
        // Passive Permission Audit Panel
        SectionHeader(title = "PermissionManager Audit", trailingBadge = "Passive Check")
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            uiState.permissionSnapshots.forEach { snap ->
              val statusColor =
                when (snap.status) {
                  PermissionStatus.GRANTED -> EmeraldReady
                  PermissionStatus.NOT_GRANTED -> AmberStandby
                  PermissionStatus.DORMANT_LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Text(
                    text = snap.permission.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = snap.permission.purpose,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f),
                  ) {
                    Text(
                      text = snap.status.name.replace("_", " "),
                      style = MaterialTheme.typography.labelSmall,
                      color = statusColor,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                  }
                  Switch(
                    checked = snap.userAllowedInPolicy,
                    onCheckedChange = { allowed ->
                      onSetPermissionAllowedByUser(snap.permission, allowed)
                    },
                    modifier =
                      Modifier.testTag("privacy_switch_${snap.permission.name.lowercase()}"),
                  )
                }
              }
            }
          }
        }

        // Secure User-Controlled Memory System (Step 5)
        SectionHeader(
          title = "Secure MAX Memory System",
          trailingBadge = "${uiState.structuredMemories.size}/${uiState.memorySettings.maxStoredMemories}",
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(MemoryType.entries) { type ->
                FilterChip(
                  selected = selectedType == type,
                  onClick = { selectedType = type },
                  label = { Text(type.displayName) },
                  modifier = Modifier.testTag("mem_type_${type.name.lowercase()}"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = memoryDraft,
                onValueChange = { memoryDraft = it },
                placeholder = { Text("Store ${selectedType.displayName}...") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("memory_note_input"),
              )
              Button(
                onClick = {
                  if (memoryDraft.isNotBlank()) {
                    onAddMemoryNote(selectedType, memoryDraft)
                    memoryDraft = ""
                  }
                },
                modifier = Modifier.testTag("add_memory_button"),
              ) {
                Text("Save")
              }
            }

            // Quick Bangla Memory Command Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val commands =
                listOf(
                  "MAX, এটা মনে রাখো আমার নাম রাকিব",
                  "MAX, আমার Memory দেখাও",
                  "MAX, এটা ভুলে যাও রাকিব",
                  "MAX, সব Memory মুছে দাও",
                )
              items(commands) { cmd ->
                AssistChip(
                  onClick = { onRunBanglaMemoryCommand(cmd) },
                  label = { Text(cmd) },
                  modifier = Modifier.testTag("bangla_mem_cmd_${cmd.hashCode()}"),
                )
              }
            }

            uiState.lastMemoryFeedback?.let { feedback ->
              Text(
                text = feedback,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Allow Sensitive Info (Off = Safe Default)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              Switch(
                checked = uiState.memorySettings.allowSensitiveInformation,
                onCheckedChange = onSetAllowSensitiveMemory,
                modifier = Modifier.testTag("switch_allow_sensitive_memory"),
              )
            }

            if (uiState.structuredMemories.isEmpty()) {
              Text(
                text = "No memories saved. User has full control to store or clear at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              uiState.structuredMemories.take(8).forEach { item ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = item.type.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                    IconButton(
                      onClick = { onDeleteMemoryById(item.id) },
                      modifier = Modifier.testTag("delete_memory_${item.id}"),
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
                modifier = Modifier.fillMaxWidth().testTag("clear_memories_button"),
              ) {
                Text("Delete All Memory")
              }
            }
          }
        }

        // ErrorLogger Console
        SectionHeader(
          title = "ErrorLogger Telemetry",
          trailingBadge = "${uiState.logs.size} events",
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onRunSelfCheck,
            modifier = Modifier.weight(1f).testTag("run_self_check_button"),
          ) {
            Text("Run Self-Check")
          }
          OutlinedButton(
            onClick = onClearLogs,
            modifier = Modifier.weight(1f).testTag("clear_logs_button"),
          ) {
            Text("Clear Logs")
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = uiState.selectedLogFilter == null,
              onClick = { onSetLogFilter(null) },
              label = { Text("All") },
              modifier = Modifier.testTag("log_filter_all"),
            )
          }
          items(LogLevel.entries) { level ->
            FilterChip(
              selected = uiState.selectedLogFilter == level,
              onClick = { onSetLogFilter(level) },
              label = { Text(level.name) },
              modifier = Modifier.testTag("log_filter_${level.name.lowercase()}"),
            )
          }
        }
      }
    }

    if (uiState.logs.isEmpty()) {
      item {
        Text(
          text = "No diagnostic log events matching current filter.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 16.dp),
        )
      }
    } else {
      items(uiState.logs, key = { it.id }) { log ->
        val levelColor =
          when (log.level) {
            LogLevel.INFO -> MaterialTheme.colorScheme.primary
            LogLevel.WARNING -> AmberStandby
            LogLevel.ERROR -> MaterialTheme.colorScheme.error
          }
        Card(
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          shape = RoundedCornerShape(14.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "[${log.level.name}] ${log.module}",
                style = MaterialTheme.typography.labelMedium,
                color = levelColor,
              )
              Text(
                text = timeFormatter.format(Date(log.timestampMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = log.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            log.throwableSummary?.let { summary ->
              Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }
  }
}
