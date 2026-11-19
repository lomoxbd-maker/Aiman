package com.example.floating

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FloatingAssistantManager — User-triggered floating bubble & quick voice activation controller.
 *
 * Supports:
 * - In-app draggable/tappable Floating MAX Bubble (zero extra OS permissions required), and
 * - System-wide Android 8.0+ overlay service when explicitly enabled & granted `SYSTEM_ALERT_WINDOW`.
 *
 * Privacy guarantee:
 * - Never activates automatically on boot or hides secret microphone/camera capture.
 */
class FloatingAssistantManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
  private val overlayPermissionProvider: () -> Boolean = {
    runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
  },
) {

  private val _state = MutableStateFlow<FloatingState>(FloatingState.Inactive)
  val state: StateFlow<FloatingState> = _state.asStateFlow()

  private val _quickVoiceActivationCount = MutableStateFlow(0)
  val quickVoiceActivationCount: StateFlow<Int> = _quickVoiceActivationCount.asStateFlow()

  fun hasSystemOverlayPermission(): Boolean = overlayPermissionProvider()

  fun activateFloatingAssistant(
    initiatedByUser: Boolean,
    preferSystemOverlay: Boolean = false,
  ): Boolean {
    if (!initiatedByUser) {
      errorTracker.logWarning(MODULE_NAME, "Blocked automatic floating overlay activation without user request.")
      _state.value = FloatingState.Error("Floating assistant starts only on your tap.")
      return false
    }

    if (preferSystemOverlay && !hasSystemOverlayPermission()) {
      _state.value =
        FloatingState.PermissionRequired(
          "Allow 'Display over other apps' in Android settings to float MAX over other apps.",
        )
      return false
    }

    val mode =
      if (preferSystemOverlay && startOverlayServiceSafely()) {
        FloatingAssistantMode.SYSTEM_OVERLAY_ACTIVE
      } else {
        FloatingAssistantMode.IN_APP_BUBBLE
      }

    _state.value = FloatingState.MinimizedBubble(mode)
    errorTracker.logInfo(MODULE_NAME, "Floating MAX bubble activated ($mode).")
    return true
  }

  fun requestOverlayPermissionSettings(): Boolean {
    return runCatching {
      val intent =
        Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          "package:${context.packageName}".toUri(),
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun minimizeBubble() {
    val currentMode = activeMode()
    if (currentMode == FloatingAssistantMode.OFF) return
    _state.value = FloatingState.MinimizedBubble(currentMode)
  }

  fun expandBubble(isVoiceArmed: Boolean = false) {
    val currentMode = activeMode()
    val targetMode =
      if (currentMode == FloatingAssistantMode.OFF) FloatingAssistantMode.IN_APP_BUBBLE
      else currentMode
    _state.value = FloatingState.ExpandedQuickPanel(targetMode, isVoiceArmed)
  }

  fun triggerQuickVoiceActivation(): Boolean {
    val mode =
      if (activeMode() == FloatingAssistantMode.OFF) FloatingAssistantMode.IN_APP_BUBBLE
      else activeMode()
    _quickVoiceActivationCount.value = _quickVoiceActivationCount.value + 1
    _state.value = FloatingState.ExpandedQuickPanel(mode = mode, isVoiceArmed = true)
    errorTracker.logInfo(MODULE_NAME, "Floating bubble triggered quick voice activation.")
    return true
  }

  fun restoreMainActivity(): Boolean {
    return runCatching {
      val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
      if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(launch)
        true
      } else {
        false
      }
    }.getOrDefault(false)
  }

  fun closeFloatingAssistant() {
    runCatching {
      context.stopService(Intent(context, FloatingOverlayService::class.java))
    }
    _state.value = FloatingState.Inactive
    errorTracker.logInfo(MODULE_NAME, "Floating MAX assistant closed cleanly.")
  }

  fun release() {
    closeFloatingAssistant()
  }

  private fun activeMode(): FloatingAssistantMode {
    return when (val s = _state.value) {
      is FloatingState.MinimizedBubble -> s.mode
      is FloatingState.ExpandedQuickPanel -> s.mode
      else -> FloatingAssistantMode.OFF
    }
  }

  private fun startOverlayServiceSafely(): Boolean {
    return runCatching {
      val intent = Intent(context, FloatingOverlayService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
      true
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Falling back to in-app floating bubble: ${err.message}")
    }.getOrDefault(false)
  }

  companion object {
    private const val MODULE_NAME = "FloatingAssistantManager"
  }
}
