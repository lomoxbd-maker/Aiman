package com.example.ui.screens

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.phone.PhoneState
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState

private enum class PhoneTab(val title: String) {
  COMMUNICATE("Calls & SMS"),
  TIME_TOOLS("Alarm & Clocks"),
  UTILITIES("Calc & Torch"),
}

@Composable
fun PhoneControlScreen(
  uiState: MAXUiState,
  onBack: () -> Unit,
  onRequestCall: (String, String?, Boolean) -> Unit,
  onRequestSms: (String, String, String?) -> Unit,
  onSearchContacts: (String) -> Unit,
  onSystemContactPicked: (android.net.Uri) -> Unit,
  onRequestAddOrEditContact: (String, String, String?) -> Unit,
  onRequestSetAlarm: (Int, Int, String) -> Unit,
  onOpenSystemAlarms: () -> Unit,
  onStartTimer: (Int, String) -> Unit,
  onPauseTimer: () -> Unit,
  onResumeTimer: () -> Unit,
  onResetTimer: () -> Unit,
  onStartStopwatch: () -> Unit,
  onPauseStopwatch: () -> Unit,
  onLapStopwatch: () -> Unit,
  onResetStopwatch: () -> Unit,
  onCalculateMath: (String) -> Unit,
  onRequestToggleFlashlight: (Boolean, Boolean) -> Unit,
  onReleasePhoneResources: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf(PhoneTab.COMMUNICATE) }
  var phoneInput by remember { mutableStateOf("") }
  var contactNameInput by remember { mutableStateOf("") }
  var smsBodyInput by remember { mutableStateOf("") }
  var contactSearchQuery by remember { mutableStateOf("") }

  var alarmHour by remember { mutableIntStateOf(7) }
  var alarmMinute by remember { mutableIntStateOf(30) }
  var alarmLabel by remember { mutableStateOf("Morning Wake-up") }
  var customTimerSecs by remember { mutableStateOf("60") }
  var mathExpression by remember { mutableStateOf("125 * 4 + 25") }

  val contactPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
      val uri = result.data?.data
      if (uri != null) {
        onSystemContactPicked(uri)
      }
    }

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
        // Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("phone_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Core")
            }
            Spacer(Modifier.width(4.dp))
            Column {
              Text("MAX Phone & Utilities", style = MaterialTheme.typography.titleLarge)
              Text(
                text = "Explicit Confirmation Required • Zero Auto-Actions",
                style = MaterialTheme.typography.bodySmall,
                color = EmeraldReady,
              )
            }
          }
          OutlinedButton(
            onClick = onReleasePhoneResources,
            modifier = Modifier.testTag("phone_release_resources_button"),
          ) {
            Text("Release")
          }
        }

        // Privacy Guarantee Banner
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = EmeraldReady)
            Text(
              text = "Never calls, texts, reads call history/SMS, modifies contacts, or enables flashlight automatically.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Live State Notice
        when (val pState = uiState.phoneState) {
          is PhoneState.ActionReady ->
            PhoneStatusBanner(pState.message, isError = false)
          is PhoneState.Error ->
            PhoneStatusBanner(pState.message, isError = true)
          is PhoneState.UnsupportedFallback ->
            PhoneStatusBanner(pState.reason, isError = true)
          is PhoneState.BlockedByPrivacyPolicy ->
            PhoneStatusBanner(pState.reason, isError = true)
          is PhoneState.PermissionRequired ->
            PhoneStatusBanner(pState.explanation, isError = true)
          else -> Unit
        }

        // Section Filter Tabs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(PhoneTab.entries) { tab ->
            FilterChip(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              label = { Text(tab.title) },
              modifier = Modifier.testTag("phone_tab_${tab.name.lowercase()}"),
            )
          }
        }

        when (selectedTab) {
          PhoneTab.COMMUNICATE -> {
            // Calls & SMS Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(title = "Phone Call & SMS Composer", trailingBadge = "Confirmed Only")

                OutlinedTextField(
                  value = contactNameInput,
                  onValueChange = { contactNameInput = it },
                  label = { Text("Recipient Name (optional)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("phone_name_input"),
                )

                OutlinedTextField(
                  value = phoneInput,
                  onValueChange = { phoneInput = it },
                  label = { Text("Phone Number") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("phone_number_input"),
                )

                OutlinedTextField(
                  value = smsBodyInput,
                  onValueChange = { smsBodyInput = it },
                  label = { Text("SMS Message Text") },
                  minLines = 2,
                  maxLines = 3,
                  modifier = Modifier.fillMaxWidth().testTag("phone_sms_body_input"),
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Button(
                    onClick = {
                      onRequestCall(phoneInput, contactNameInput.ifBlank { null }, false)
                    },
                    modifier = Modifier.weight(1f).testTag("phone_call_button"),
                  ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Call")
                  }
                  FilledTonalButton(
                    onClick = {
                      onRequestSms(phoneInput, smsBodyInput, contactNameInput.ifBlank { null })
                    },
                    modifier = Modifier.weight(1f).testTag("phone_sms_button"),
                  ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("SMS")
                  }
                }
              }
            }

            // Contacts Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(title = "Contacts Lookup & Editor", trailingBadge = "Privacy Locked")

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  OutlinedTextField(
                    value = contactSearchQuery,
                    onValueChange = { contactSearchQuery = it },
                    placeholder = { Text("Search name or number...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("phone_contact_search_input"),
                  )
                  Button(
                    onClick = { onSearchContacts(contactSearchQuery) },
                    modifier = Modifier.testTag("phone_contact_search_button"),
                  ) {
                    Text("Find")
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = {
                      val pickerIntent =
                        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                      contactPickerLauncher.launch(pickerIntent)
                    },
                    modifier = Modifier.weight(1f).testTag("phone_pick_contact_button"),
                  ) {
                    Icon(Icons.Filled.Contacts, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("System Picker")
                  }
                  FilledTonalButton(
                    onClick = {
                      onRequestAddOrEditContact(contactNameInput, phoneInput, null)
                    },
                    modifier = Modifier.weight(1f).testTag("phone_add_contact_button"),
                  ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add / Edit")
                  }
                }

                uiState.phoneContacts.forEachIndexed { idx, contact ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(12.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(contact.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                          contact.phoneNumber,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      OutlinedButton(
                        onClick = {
                          contactNameInput = contact.displayName
                          phoneInput = contact.phoneNumber
                        },
                        modifier = Modifier.testTag("use_contact_button_$idx"),
                      ) {
                        Text("Use")
                      }
                    }
                  }
                }
              }
            }
          }

          PhoneTab.TIME_TOOLS -> {
            // Alarm Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(title = "Alarm Controller", trailingBadge = "%02d:%02d".format(alarmHour, alarmMinute))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { alarmHour = (alarmHour + 1) % 24 },
                    modifier = Modifier.weight(1f).testTag("alarm_hour_plus_button"),
                  ) {
                    Text("Hour +1 ($alarmHour)")
                  }
                  OutlinedButton(
                    onClick = { alarmMinute = (alarmMinute + 5) % 60 },
                    modifier = Modifier.weight(1f).testTag("alarm_min_plus_button"),
                  ) {
                    Text("Min +5 ($alarmMinute)")
                  }
                }

                OutlinedTextField(
                  value = alarmLabel,
                  onValueChange = { alarmLabel = it },
                  label = { Text("Alarm Label") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("alarm_label_input"),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onRequestSetAlarm(alarmHour, alarmMinute, alarmLabel) },
                    modifier = Modifier.weight(1f).testTag("alarm_set_button"),
                  ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Set Alarm")
                  }
                  OutlinedButton(
                    onClick = onOpenSystemAlarms,
                    modifier = Modifier.testTag("alarm_open_clock_button"),
                  ) {
                    Text("Clock App")
                  }
                }
              }
            }

            // Timer Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              val tStatus = uiState.timerStatus
              val mins = tStatus.remainingSeconds / 60
              val secs = tStatus.remainingSeconds % 60
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(
                  title = "Countdown Timer",
                  trailingBadge = "%02d:%02d".format(mins, secs),
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(listOf(30 to "30s", 60 to "1 min", 300 to "5 min", 600 to "10 min")) { (sec, label) ->
                    AssistChip(
                      onClick = { onStartTimer(sec, "MAX $label Timer") },
                      label = { Text(label) },
                      modifier = Modifier.testTag("timer_preset_${sec}s"),
                    )
                  }
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  OutlinedTextField(
                    value = customTimerSecs,
                    onValueChange = { customTimerSecs = it.filter(Char::isDigit) },
                    label = { Text("Seconds") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("timer_seconds_input"),
                  )
                  Button(
                    onClick = {
                      val valSec = customTimerSecs.toIntOrNull() ?: 60
                      onStartTimer(valSec, "MAX Custom Timer")
                    },
                    modifier = Modifier.testTag("timer_start_button"),
                  ) {
                    Icon(Icons.Filled.Timer, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start")
                  }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (tStatus.isRunning) {
                    FilledTonalButton(
                      onClick = onPauseTimer,
                      modifier = Modifier.weight(1f).testTag("timer_pause_button"),
                    ) {
                      Icon(Icons.Filled.Pause, contentDescription = null)
                      Text(" Pause")
                    }
                  } else if (tStatus.isPaused) {
                    FilledTonalButton(
                      onClick = onResumeTimer,
                      modifier = Modifier.weight(1f).testTag("timer_resume_button"),
                    ) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = null)
                      Text(" Resume")
                    }
                  }
                  OutlinedButton(
                    onClick = onResetTimer,
                    modifier = Modifier.weight(1f).testTag("timer_reset_button"),
                  ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(" Reset")
                  }
                }
              }
            }

            // Stopwatch Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              val sw = uiState.stopwatchStatus
              val totalSec = sw.elapsedMillis / 1_000L
              val tenths = (sw.elapsedMillis % 1_000L) / 100L
              val swDisplay = "%02d:%02d.%d".format(totalSec / 60L, totalSec % 60L, tenths)
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(title = "Stopwatch", trailingBadge = swDisplay)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { if (sw.isRunning) onPauseStopwatch() else onStartStopwatch() },
                    modifier = Modifier.weight(1f).testTag("stopwatch_toggle_button"),
                  ) {
                    Text(if (sw.isRunning) "Pause" else "Start")
                  }
                  FilledTonalButton(
                    onClick = onLapStopwatch,
                    modifier = Modifier.testTag("stopwatch_lap_button"),
                  ) {
                    Text("Lap")
                  }
                  OutlinedButton(
                    onClick = onResetStopwatch,
                    modifier = Modifier.testTag("stopwatch_reset_button"),
                  ) {
                    Text("Reset")
                  }
                }

                if (sw.laps.isNotEmpty()) {
                  Text(
                    text = "Laps: " + sw.laps.takeLast(4).joinToString(" • ") { "${it / 1000.0}s" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          PhoneTab.UTILITIES -> {
            // Calculator Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(title = "Smart Calculator", trailingBadge = "Bangla & Math")

                OutlinedTextField(
                  value = mathExpression,
                  onValueChange = { mathExpression = it },
                  label = { Text("Expression (e.g., 15 যোগ 25 or 25% of 200)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("calculator_input"),
                )

                Button(
                  onClick = { onCalculateMath(mathExpression) },
                  modifier = Modifier.fillMaxWidth().testTag("calculator_evaluate_button"),
                ) {
                  Icon(Icons.Filled.Calculate, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Calculate")
                }

                uiState.calculationHistory.takeLast(4).reversed().forEach { rec ->
                  Text(
                    text = "${rec.expression} = ${rec.result}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }

            // Flashlight Card
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                SectionHeader(
                  title = "Flashlight Control",
                  trailingBadge = if (uiState.isTorchOn) "TORCH ON" else "TORCH OFF",
                )
                Text(
                  text = "Only turns on after your explicit confirmation and turns off automatically when MAX closes or releases resources.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = { onRequestToggleFlashlight(!uiState.isTorchOn, true) },
                  modifier = Modifier.fillMaxWidth().testTag("flashlight_toggle_button"),
                ) {
                  Icon(
                    imageVector = if (uiState.isTorchOn) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                    contentDescription = null,
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(if (uiState.isTorchOn) "Turn Off Flashlight" else "Turn On Flashlight")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PhoneStatusBanner(message: String, isError: Boolean) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color =
      if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
      else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    border =
      BorderStroke(
        1.dp,
        if (isError) AmberStandby.copy(alpha = 0.7f) else EmeraldReady.copy(alpha = 0.7f),
      ),
  ) {
    Text(
      text = message,
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}
