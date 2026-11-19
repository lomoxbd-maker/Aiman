package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.apps.SupportedApp
import com.example.floating.FloatingState
import com.example.permissions.AppPermission
import com.example.ui.navigation.MAXDestination
import com.example.ui.theme.GradientElectricPurple
import com.example.ui.theme.GradientRoyalBlue
import com.example.ui.screens.AppsControlScreen
import com.example.ui.screens.CoreDashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.FilesAndSmartScreen
import com.example.ui.screens.InternetScreen
import com.example.ui.screens.ModulesArchitectureScreen
import com.example.ui.screens.PhoneControlScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VisionScreen
import com.example.ui.theme.MAXTheme
import com.example.viewmodel.MAXViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MAXAppShell(viewModel: MAXViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: MAXDestination.CORE.route
  var awaitingOsPermission by remember { mutableStateOf<AppPermission?>(null) }

  val runtimePermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
      val perm = awaitingOsPermission ?: AppPermission.MICROPHONE_WAKE_WORD
      awaitingOsPermission = null
      viewModel.onPermissionPromptCompleted(perm, granted)
    }

  MAXTheme(darkTheme = uiState.settings.darkThemePreferred) {
    uiState.pendingPermissionRequest?.let { pending ->
      AlertDialog(
        onDismissRequest = viewModel::dismissPendingPermissionRequest,
        title = { Text(pending.title) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pending.explanationBangla, style = MaterialTheme.typography.bodyMedium)
            Text(
              pending.explanationEnglish,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val perm = pending.permission
              val manifestPerm = perm.manifestPermission
              val launchOs = pending.canLaunchSystemPrompt && manifestPerm != null
              if (launchOs) {
                awaitingOsPermission = perm
                viewModel.dismissPendingPermissionRequest()
                runtimePermissionLauncher.launch(manifestPerm)
              } else {
                viewModel.onPermissionPromptCompleted(perm, true)
              }
            },
            modifier = Modifier.testTag("permission_rationale_allow"),
          ) {
            Text("Continue")
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = viewModel::dismissPendingPermissionRequest,
            modifier = Modifier.testTag("permission_rationale_cancel"),
          ) {
            Text("Keep Locked")
          }
        },
      )
    }

    uiState.pendingPhoneConfirmation?.let { phoneConfirm ->
      AlertDialog(
        onDismissRequest = viewModel::cancelPendingPhoneAction,
        title = { Text("${phoneConfirm.titleBangla} • ${phoneConfirm.titleEnglish}") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(phoneConfirm.messageBangla, style = MaterialTheme.typography.bodyMedium)
            Text(
              phoneConfirm.messageEnglish,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = { viewModel.confirmPendingPhoneAction() },
            modifier = Modifier.testTag("phone_confirm_allow_button"),
          ) {
            Text("Confirm")
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = viewModel::cancelPendingPhoneAction,
            modifier = Modifier.testTag("phone_confirm_cancel_button"),
          ) {
            Text("Cancel")
          }
        },
      )
    }

    uiState.pendingAppConfirmation?.let { appConfirm ->
      AlertDialog(
        onDismissRequest = viewModel::cancelPendingAppAction,
        title = { Text("${appConfirm.titleBangla} • ${appConfirm.titleEnglish}") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(appConfirm.messageBangla, style = MaterialTheme.typography.bodyMedium)
            Text(
              appConfirm.messageEnglish,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = { viewModel.confirmPendingAppAction() },
            modifier = Modifier.testTag("app_confirm_allow_button"),
          ) {
            Text("Confirm")
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = viewModel::cancelPendingAppAction,
            modifier = Modifier.testTag("app_confirm_cancel_button"),
          ) {
            Text("Cancel")
          }
        },
      )
    }

    uiState.pendingFileConfirmation?.let { fileConfirm ->
      AlertDialog(
        onDismissRequest = viewModel::cancelPendingFileAction,
        title = { Text("${fileConfirm.titleBangla} • ${fileConfirm.titleEnglish}") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fileConfirm.messageBangla, style = MaterialTheme.typography.bodyMedium)
            Text(
              fileConfirm.messageEnglish,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = { viewModel.confirmPendingFileAction() },
            modifier = Modifier.testTag("file_confirm_allow_button"),
          ) {
            Text("Confirm")
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = viewModel::cancelPendingFileAction,
            modifier = Modifier.testTag("file_confirm_cancel_button"),
          ) {
            Text("Cancel")
          }
        },
      )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 600.dp

      Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
          CenterAlignedTopAppBar(
            title = {
              Text(
                text = "MAX ASSISTANT",
                style = MaterialTheme.typography.titleLarge,
              )
            },
            colors =
              TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
              ),
          )
        },
        bottomBar = {
          if (!useNavRail) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
              MAXDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    navController.navigate(destination.route) {
                      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = {
                    Icon(
                      imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                      contentDescription = destination.label,
                    )
                  },
                  label = { Text(destination.label) },
                  modifier = Modifier.testTag(destination.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          if (useNavRail) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
              MAXDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationRailItem(
                  selected = selected,
                  onClick = {
                    navController.navigate(destination.route) {
                      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = {
                    Icon(
                      imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                      contentDescription = destination.label,
                    )
                  },
                  label = { Text(destination.label) },
                  modifier = Modifier.testTag(destination.testTag),
                )
              }
            }
          }

          NavHost(
            navController = navController,
            startDestination = MAXDestination.CORE.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
              androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220),
              )
            },
            exitTransition = {
              androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(180),
              )
            },
            popEnterTransition = {
              androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220),
              )
            },
            popExitTransition = {
              androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(180),
              )
            },
          ) {
            composable(MAXDestination.CORE.route) {
              CoreDashboardScreen(
                uiState = uiState,
                onArmWakeWordStandby = viewModel::armWakeWordStandby,
                onDisarmWakeWordStandby = viewModel::disarmWakeWordStandby,
                onSimulateWakeWordMax = viewModel::simulateWakeWordMax,
                onReturnToStandby = viewModel::returnToStandby,
                onRequestWakeWordPermissionWithExplanation =
                  viewModel::requestWakeWordPermissionWithExplanation,
                onSendConversationalMessage = viewModel::sendConversationalMessage,
                onSelectEmotion = viewModel::selectEmotion,
                onSpeakText = viewModel::speakText,
                onPauseSpeaking = viewModel::pauseSpeaking,
                onResumeSpeaking = viewModel::resumeSpeaking,
                onStopSpeaking = viewModel::stopSpeaking,
                onSelectLanguage = viewModel::setVoiceLanguage,
                onSelectStyle = viewModel::setSpeechStyle,
                onActivateManualVoiceSession = viewModel::activateManualVoiceSession,
                onFinishFollowUpToWakeMode = viewModel::finishFollowUpToWakeMode,
                onOpenCameraQuick = {
                  navController.navigate(MAXDestination.VISION.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onOpenGalleryQuick = {
                  navController.navigate(MAXDestination.VISION.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onOpenInternetScreen = { navController.navigate("max_internet") },
                onOpenPhoneScreen = { navController.navigate("max_phone") },
                onOpenWhatsAppQuick = {
                  viewModel.requestOpenApp(SupportedApp.WHATSAPP, requireConfirmation = true)
                },
                onOpenAppsScreen = { navController.navigate("max_apps") },
                onOpenFilesSmartScreen = { navController.navigate("max_files_smart") },
                onOpenSettingsQuick = {
                  navController.navigate(MAXDestination.SETTINGS.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                onRunRoutine = viewModel::runRoutine,
                onToggleFloatingBubble = {
                  if (uiState.floatingState is FloatingState.Inactive) {
                    viewModel.activateFloatingBubble(preferSystemOverlay = false)
                  } else {
                    viewModel.closeFloatingBubble()
                  }
                },
              )
            }
            composable("max_files_smart") {
              FilesAndSmartScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRequestOpenFilePicker = viewModel::requestOpenFilePicker,
                onFilesPicked = viewModel::onFilesPicked,
                onFilterFiles = viewModel::filterSessionFiles,
                onOpenAndReadFile = viewModel::openAndReadFile,
                onRunOcrOnFile = viewModel::runOcrOnFile,
                onRequestDeleteFile = viewModel::requestDeleteFileItem,
                onTranslateText = viewModel::translateSmartText,
                onScheduleReminder = viewModel::scheduleReminder,
                onToggleReminderCompleted = viewModel::toggleReminderCompleted,
                onDeleteReminder = viewModel::deleteReminder,
                onAddNote = viewModel::addSmartNote,
                onDeleteNote = viewModel::deleteSmartNote,
                onLoadTodayCalendar = viewModel::loadTodayCalendar,
                onOpenSystemCalendarInsert = viewModel::openSystemCalendarInsert,
                onSaveVoiceNote = viewModel::saveVoiceNoteTranscript,
                onDeleteVoiceNote = viewModel::deleteVoiceNote,
                onRefreshSuggestions = viewModel::refreshAiSuggestions,
                onRunVoiceCommand = { cmd -> viewModel.sendConversationalMessage(cmd, true) },
                onSpeakText = { txt -> viewModel.speakText(txt, null) },
              )
            }
            composable("max_apps") {
              AppsControlScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRefreshAvailability = viewModel::refreshAppAvailability,
                onRequestOpenApp = { app -> viewModel.requestOpenApp(app, requireConfirmation = true) },
                onRequestOpenAlternative = viewModel::requestOpenAppAlternative,
                onRequestWhatsAppDraft = viewModel::requestWhatsAppDraft,
                onRequestGmailDraft = viewModel::requestGmailDraft,
                onRequestMapsSearch = viewModel::requestMapsSearch,
                onRequestChromeUrl = viewModel::requestChromeUrl,
                onRequestYouTubeSearch = viewModel::requestYouTubeSearch,
                onRunVoiceCommand = { cmd -> viewModel.sendConversationalMessage(cmd, true) },
              )
            }
            composable("max_phone") {
              PhoneControlScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onRequestCall = viewModel::requestPhoneCall,
                onRequestSms = viewModel::requestSms,
                onSearchContacts = viewModel::searchPhoneContacts,
                onSystemContactPicked = viewModel::onSystemContactPicked,
                onRequestAddOrEditContact = viewModel::requestAddOrEditContact,
                onRequestSetAlarm = viewModel::requestSetAlarm,
                onOpenSystemAlarms = viewModel::openSystemAlarms,
                onStartTimer = viewModel::startTimer,
                onPauseTimer = viewModel::pauseTimer,
                onResumeTimer = viewModel::resumeTimer,
                onResetTimer = viewModel::resetTimer,
                onStartStopwatch = viewModel::startStopwatch,
                onPauseStopwatch = viewModel::pauseStopwatch,
                onLapStopwatch = viewModel::lapStopwatch,
                onResetStopwatch = viewModel::resetStopwatch,
                onCalculateMath = viewModel::calculateMath,
                onRequestToggleFlashlight = viewModel::requestToggleFlashlight,
                onReleasePhoneResources = viewModel::releasePhoneTaskResources,
              )
            }
            composable("max_internet") {
              InternetScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSearchInternet = viewModel::searchInternet,
                onOpenWebsite = viewModel::openWebsite,
                onSummarizeWebContent = viewModel::summarizeWebContent,
                onOpenExternalVerifiedUrl = viewModel::openExternalVerifiedUrl,
                onCancelInternetRequest = viewModel::cancelInternetRequest,
                onCloseBrowserSession = viewModel::closeBrowserSession,
                onRefreshConnection = viewModel::refreshConnectionQuality,
                onRunVoiceCommand = { cmd -> viewModel.sendConversationalMessage(cmd, true) },
                onSpeakText = viewModel::speakText,
                onStopSpeaking = viewModel::stopSpeaking,
              )
            }
            composable(MAXDestination.VISION.route) {
              VisionScreen(
                uiState = uiState,
                onOpenCamera = viewModel::openCamera,
                onBindCameraXSession = viewModel::bindCameraXSession,
                onCapturePhoto = viewModel::capturePhoto,
                onStartVideoRecording = viewModel::startVideoRecording,
                onStopVideoRecording = viewModel::stopVideoRecording,
                onSwitchCameraLens = viewModel::switchCameraLens,
                onToggleCameraFlash = viewModel::toggleCameraFlash,
                onCloseCamera = viewModel::closeCamera,
                onOpenGallery = viewModel::openGallery,
                onGalleryMediaPicked = viewModel::onGalleryMediaPicked,
                onSelectGalleryItem = viewModel::selectGalleryItem,
                onFindGalleryPhotos = viewModel::findGalleryPhotos,
                onPrepareGalleryOcr = viewModel::prepareGalleryOcr,
                onRequestDeleteGalleryItem = viewModel::requestDeleteGalleryItem,
                onConfirmDeleteGalleryItem = viewModel::confirmDeleteGalleryItem,
                onCancelDeleteGalleryItem = viewModel::cancelDeleteGalleryItem,
              )
            }
            composable(MAXDestination.MODULES.route) {
              ModulesArchitectureScreen(uiState = uiState)
            }
            composable(MAXDestination.DIAGNOSTICS.route) {
              DiagnosticsScreen(
                uiState = uiState,
                onSetLogFilter = viewModel::setLogFilter,
                onRunSelfCheck = viewModel::runSelfCheck,
                onClearLogs = viewModel::clearLogs,
                onAddMemoryNote = viewModel::addMemoryNote,
                onDeleteMemoryById = viewModel::deleteMemoryById,
                onClearMemories = viewModel::clearMemories,
                onSetAllowSensitiveMemory = viewModel::setAllowSensitiveMemory,
                onRunBanglaMemoryCommand = { cmd -> viewModel.sendConversationalMessage(cmd, true) },
                onSetPermissionAllowedByUser = viewModel::setPermissionAllowedByUser,
              )
            }
            composable(MAXDestination.SETTINGS.route) {
              SettingsScreen(
                uiState = uiState,
                onSetWakeWordEnabled = viewModel::setWakeWordEnabled,
                onSetWakeWordSensitivity = viewModel::setWakeWordSensitivity,
                onSetLowPowerStandbyMode = viewModel::setLowPowerStandbyMode,
                onSetVoiceLanguage = viewModel::setVoiceLanguage,
                onSetSpeechStyle = viewModel::setSpeechStyle,
                onSetTtsSpeechRate = viewModel::setTtsSpeechRate,
                onSetTtsPitch = viewModel::setTtsPitch,
                onSetMemoryRetentionEnabled = viewModel::setMemoryRetentionEnabled,
                onSetDarkThemePreferred = viewModel::setDarkThemePreferred,
                onCheckForUpdates = viewModel::checkForUpdates,
                onOpenReleasePage = { url -> viewModel.openVerifiedUpdateUrl(url) },
                onToggleFloatingBubble = {
                  if (uiState.floatingState is FloatingState.Inactive) {
                    viewModel.activateFloatingBubble(preferSystemOverlay = false)
                  } else {
                    viewModel.closeFloatingBubble()
                  }
                },
                onRequestOverlayPermission = { viewModel.requestOverlayPermissionSettings() },
              )
            }
          }
        }
      }

      // Floating Assistant Bubble Overlay (shown strictly when activated by the user)
      if (uiState.floatingState is FloatingState.MinimizedBubble ||
        uiState.floatingState is FloatingState.ExpandedQuickPanel
      ) {
        Box(
          modifier = Modifier.fillMaxSize().padding(end = 16.dp, bottom = 92.dp),
          contentAlignment = Alignment.BottomEnd,
        ) {
          AnimatedContent(
            targetState = uiState.floatingState is FloatingState.ExpandedQuickPanel,
            label = "floating_max_bubble",
          ) { expanded ->
            if (!expanded) {
              Surface(
                onClick = { viewModel.expandFloatingBubble(false) },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(58.dp).shadow(8.dp, CircleShape).testTag("floating_bubble_minimized"),
              ) {
                Box(
                  modifier =
                    Modifier.fillMaxSize()
                      .background(Brush.linearGradient(listOf(GradientRoyalBlue, GradientElectricPurple))),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Expand Floating MAX Assistant",
                    tint = Color.White,
                  )
                }
              }
            } else {
              Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.testTag("floating_bubble_expanded"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  FilledTonalIconButton(
                    onClick = { viewModel.triggerFloatingQuickVoice() },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("floating_quick_voice_btn"),
                  ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Quick Voice Activation")
                  }
                  IconButton(
                    onClick = {
                      navController.navigate(MAXDestination.CORE.route) { launchSingleTop = true }
                      viewModel.minimizeFloatingBubble()
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                  ) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = "Restore Home")
                  }
                  IconButton(
                    onClick = { viewModel.minimizeFloatingBubble() },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                  ) {
                    Icon(Icons.Filled.Minimize, contentDescription = "Minimize Bubble")
                  }
                  IconButton(
                    onClick = { viewModel.closeFloatingBubble() },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                  ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Floating Bubble")
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
