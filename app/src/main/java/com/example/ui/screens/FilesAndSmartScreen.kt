package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.files.FileKind
import com.example.files.FileState
import com.example.smart.SmartFeatureState
import com.example.smart.TranslationDirection
import com.example.ui.components.SectionHeader
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesAndSmartScreen(
  uiState: MAXUiState,
  onBack: () -> Unit,
  onRequestOpenFilePicker: () -> Unit,
  onFilesPicked: (List<Uri>) -> Unit,
  onFilterFiles: (String, FileKind?) -> Unit,
  onOpenAndReadFile: (String) -> Unit,
  onRunOcrOnFile: (String) -> Unit,
  onRequestDeleteFile: (String) -> Unit,
  onTranslateText: (String, TranslationDirection?) -> Unit,
  onScheduleReminder: (String, Long) -> Unit,
  onToggleReminderCompleted: (String) -> Unit,
  onDeleteReminder: (String) -> Unit,
  onAddNote: (String, String, Boolean) -> Unit,
  onDeleteNote: (String) -> Unit,
  onLoadTodayCalendar: () -> Unit,
  onOpenSystemCalendarInsert: (String) -> Unit,
  onSaveVoiceNote: (String, String) -> Unit,
  onDeleteVoiceNote: (String) -> Unit,
  onRefreshSuggestions: () -> Unit,
  onRunVoiceCommand: (String) -> Unit,
  onSpeakText: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Smart Features, 1 = Files & OCR
  var selectedKind by remember { mutableStateOf<FileKind?>(null) }
  var fileQuery by remember { mutableStateOf("") }

  var translateInput by remember { mutableStateOf("আমি আজ খুব ভালো আছি।") }
  var selectedDirection by remember { mutableStateOf(TranslationDirection.BANGLA_TO_ENGLISH) }
  var reminderTitle by remember { mutableStateOf("Morning Study & Tea") }
  var noteInput by remember { mutableStateOf("") }
  var voiceNoteInput by remember { mutableStateOf("") }

  val safLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
      onFilesPicked(uris)
    }

  LaunchedEffect(uiState.filePickerLaunchToken) {
    if (uiState.filePickerLaunchToken > 0L) {
      safLauncher.launch(arrayOf("application/pdf", "text/*", "image/*"))
    }
  }

  LaunchedEffect(Unit) {
    if (uiState.aiSuggestions.isEmpty()) {
      onRefreshSuggestions()
    }
  }

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
              modifier = Modifier.testTag("files_smart_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
              Text("Files & Smart Tools", style = MaterialTheme.typography.titleLarge)
              Text(
                "On-device PDF/Doc/OCR & Bangla productivity tools",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Privacy Shield Info
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Filled.Security, contentDescription = "Privacy lock", tint = MaterialTheme.colorScheme.primary)
            Text(
              text =
                "Zero auto file scans, zero uploads, zero document sharing. Files open only via Android SAF when you tap Pick.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }

        // Section Switcher
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            label = { Text("Smart Features") },
            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
            modifier = Modifier.weight(1f).testTag("tab_smart_features"),
          )
          FilterChip(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            label = { Text("Files & OCR (${uiState.sessionFiles.size})") },
            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
            modifier = Modifier.weight(1f).testTag("tab_files_ocr"),
          )
        }

        if (selectedTab == 0) {
          // Bangla Voice Shortcuts
          SectionHeader(title = "Bangla Voice Commands", trailingBadge = "Instant")
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val prompts =
              listOf(
                "MAX, এটা বাংলা থেকে ইংরেজিতে অনুবাদ করো: আমি ভালো আছি",
                "MAX, আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও",
                "MAX, একটা নোট লিখে রাখো: প্রজেক্ট আপডেট",
                "MAX, আজকের ক্যালেন্ডার দেখাও",
              )
            items(prompts) { cmd ->
              AssistChip(
                onClick = { onRunVoiceCommand(cmd) },
                label = { Text(cmd) },
              )
            }
          }

          // Smart State Banner
          when (val s = uiState.smartState) {
            is SmartFeatureState.PermissionRequired ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
              ) {
                Text(s.reason, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
              }
            is SmartFeatureState.Error ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
              ) {
                Text(s.message, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
              }
            else -> Unit
          }

          // 1. Offline Translator Card
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SectionHeader(title = "Translation", trailingBadge = "Offline Safe")
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranslationDirection.entries.forEach { dir ->
                  FilterChip(
                    selected = selectedDirection == dir,
                    onClick = { selectedDirection = dir },
                    label = { Text(dir.displayLabel) },
                  )
                }
              }
              OutlinedTextField(
                value = translateInput,
                onValueChange = { translateInput = it },
                label = { Text("Text to translate") },
                modifier = Modifier.fillMaxWidth().testTag("translate_input"),
              )
              Button(
                onClick = { onTranslateText(translateInput, selectedDirection) },
                modifier = Modifier.fillMaxWidth().testTag("translate_button"),
              ) {
                Icon(Icons.Filled.Translate, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Translate Privately")
              }
              uiState.lastTranslation?.let { rec ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = rec.translatedText,
                      style = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onSpeakText(rec.translatedText) }) {
                      Icon(Icons.Filled.VolumeUp, contentDescription = "Speak translation")
                    }
                  }
                }
              }
            }
          }

          // 2. Reminders Card
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SectionHeader(title = "Reminders", trailingBadge = "${uiState.reminders.size}")
              OutlinedTextField(
                value = reminderTitle,
                onValueChange = { reminderTitle = it },
                label = { Text("Reminder title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("reminder_title_input"),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilledTonalButton(
                  onClick = {
                    onScheduleReminder(reminderTitle, System.currentTimeMillis() + 15 * 60_000L)
                  },
                  modifier = Modifier.weight(1f).testTag("reminder_15m_button"),
                ) {
                  Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                  Spacer(Modifier.widthIn(min = 4.dp))
                  Text("+15 min")
                }
                Button(
                  onClick = {
                    val cal =
                      Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 8)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                      }
                    onScheduleReminder(reminderTitle, cal.timeInMillis)
                  },
                  modifier = Modifier.weight(1f).testTag("reminder_tomorrow_8am_button"),
                ) {
                  Text("Tomorrow 8 AM")
                }
              }
              uiState.reminders.forEach { rem ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(
                    checked = rem.isCompleted,
                    onCheckedChange = { onToggleReminderCompleted(rem.id) },
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(rem.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                      rem.dueLabel,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(onClick = { onDeleteReminder(rem.id) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove reminder")
                  }
                }
              }
            }
          }

          // 3. Notes & Voice Notes Card
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SectionHeader(title = "Notes & Voice Notes", trailingBadge = "No Audio Stored")
              OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Quick note content") },
                modifier = Modifier.fillMaxWidth().testTag("smart_note_input"),
              )
              Button(
                onClick = {
                  if (noteInput.isNotBlank()) {
                    onAddNote("", noteInput, true)
                    noteInput = ""
                  }
                },
                modifier = Modifier.fillMaxWidth().testTag("save_note_button"),
              ) {
                Icon(Icons.Filled.TextSnippet, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Save Note")
              }
              OutlinedTextField(
                value = voiceNoteInput,
                onValueChange = { voiceNoteInput = it },
                label = { Text("Voice note transcript (saved as text only)") },
                modifier = Modifier.fillMaxWidth().testTag("voice_note_input"),
              )
              FilledTonalButton(
                onClick = {
                  if (voiceNoteInput.isNotBlank()) {
                    onSaveVoiceNote("", voiceNoteInput)
                    voiceNoteInput = ""
                  }
                },
                modifier = Modifier.fillMaxWidth().testTag("save_voice_note_button"),
              ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null)
                Spacer(Modifier.widthIn(min = 6.dp))
                Text("Save Voice Note Transcript")
              }
              uiState.smartNotes.forEach { n ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                    Text(n.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  IconButton(onClick = { onDeleteNote(n.id) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete note")
                  }
                }
              }
              uiState.voiceNotes.forEach { vn ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("${vn.title} (Text-Only)", style = MaterialTheme.typography.titleSmall)
                    Text(vn.transcript, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  IconButton(onClick = { onDeleteVoiceNote(vn.id) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete voice note")
                  }
                }
              }
            }
          }

          // 4. Calendar Card
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SectionHeader(title = "Today's Calendar", trailingBadge = "Read-Only")
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = onLoadTodayCalendar,
                  modifier = Modifier.weight(1f).testTag("load_calendar_button"),
                ) {
                  Icon(Icons.Filled.CalendarToday, contentDescription = null)
                  Spacer(Modifier.widthIn(min = 6.dp))
                  Text("Check Today")
                }
                OutlinedButton(
                  onClick = { onOpenSystemCalendarInsert("MAX Scheduled Event") },
                  modifier = Modifier.weight(1f).testTag("add_calendar_event_button"),
                ) {
                  Text("Add in Calendar")
                }
              }
              if (uiState.calendarSummary.isNotBlank()) {
                Text(uiState.calendarSummary, style = MaterialTheme.typography.bodySmall)
              }
              uiState.calendarEvents.forEach { ev ->
                Text(
                  text = "• ${ev.title} (${ev.timeLabel}) [${ev.sourceLabel}]",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }

          // 5. Contextual AI Suggestions Card
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                Text("AI Suggestions (On-Device)", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRefreshSuggestions) {
                  Icon(Icons.Filled.Refresh, contentDescription = "Refresh suggestions")
                }
              }
              uiState.aiSuggestions.forEach { sug ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text(sug.titleBangla, style = MaterialTheme.typography.bodyMedium)
                    Text(
                      "${sug.titleEnglish} • ${sug.reason}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                      onClick = { onRunVoiceCommand(sug.suggestedActionCommand) },
                      shape = RoundedCornerShape(10.dp),
                    ) {
                      Text("Try Action")
                    }
                  }
                }
              }
            }
          }
        } else {
          // TAB 1: Files & OCR Section
          Button(
            onClick = onRequestOpenFilePicker,
            modifier = Modifier.fillMaxWidth().testTag("open_file_picker_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.widthIn(min = 6.dp))
            Text("Pick PDF, Document, or Image (SAF)")
          }

          when (val fState = uiState.fileState) {
            is FileState.PermissionRequired ->
              Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(fState.reason, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
              }
            is FileState.Error ->
              Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(fState.message, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
              }
            else -> Unit
          }

          OutlinedTextField(
            value = fileQuery,
            onValueChange = {
              fileQuery = it
              onFilterFiles(it, selectedKind)
            },
            label = { Text("Filter selected files") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("file_search_input"),
          )

          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = selectedKind == null,
              onClick = {
                selectedKind = null
                onFilterFiles(fileQuery, null)
              },
              label = { Text("All") },
            )
            listOf(FileKind.PDF, FileKind.TEXT_DOCUMENT, FileKind.IMAGE).forEach { kind ->
              FilterChip(
                selected = selectedKind == kind,
                onClick = {
                  selectedKind = kind
                  onFilterFiles(fileQuery, kind)
                },
                label = { Text(kind.displayLabel) },
              )
            }
          }

          if (uiState.sessionFiles.isEmpty()) {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Text(
                text = "No files selected yet. Tap 'Pick PDF, Document, or Image' above to read documents privately on-device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
              )
            }
          } else {
            uiState.sessionFiles.forEach { file ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(
                        imageVector =
                          if (file.kind == FileKind.PDF) Icons.Filled.PictureAsPdf
                          else Icons.Filled.Description,
                        contentDescription = file.kind.displayLabel,
                      )
                      Column {
                        Text(file.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                          "${file.kind.displayLabel} • ${file.mimeType}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    IconButton(onClick = { onRequestDeleteFile(file.id) }) {
                      Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove file from list")
                    }
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                      onClick = { onOpenAndReadFile(file.id) },
                      modifier = Modifier.weight(1f),
                    ) {
                      Text("Read")
                    }
                    if (file.kind == FileKind.IMAGE || file.kind == FileKind.PDF) {
                      OutlinedButton(
                        onClick = { onRunOcrOnFile(file.id) },
                        modifier = Modifier.weight(1f),
                      ) {
                        Text("Run OCR")
                      }
                    }
                  }
                }
              }
            }
          }

          uiState.activeFileContent?.let { content ->
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
              border = BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.45f)),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldReady)
                  Text("${content.fileName} (${content.pageCount} pg)", style = MaterialTheme.typography.titleSmall)
                }
                Text(content.text.take(1_000), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(onClick = { onSpeakText(content.text.take(350)) }) {
                    Text("Speak")
                  }
                  OutlinedButton(
                    onClick = {
                      translateInput = content.text.take(300)
                      selectedTab = 0
                      onTranslateText(translateInput, null)
                    },
                  ) {
                    Text("Translate")
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}
