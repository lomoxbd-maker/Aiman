package com.example.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.speech.SpeechRecognizer

/**
 * Snapshot of hardware and OS capabilities so MAX can adapt gracefully on any Android device
 * and provide safe fallbacks when features are missing or restricted.
 */
data class DeviceCompatibilityProfile(
  val sdkInt: Int,
  val deviceModel: String,
  val isLowRamDevice: Boolean,
  val isPowerSaveMode: Boolean,
  val hasMicrophone: Boolean,
  val hasSpeechRecognitionService: Boolean,
  val hasCameraHardware: Boolean,
  val hasTelephonyHardware: Boolean,
  val recommendedWakeWordIntervalMs: Long,
  val fallbackNotes: List<String>,
)

/**
 * Passive device inspector that queries Android system capabilities without activating hardware
 * or triggering runtime permissions.
 */
class DeviceCompatibilityChecker(private val context: Context) {

  fun inspect(): DeviceCompatibilityProfile {
    val pm = context.packageManager
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    val isLowRam = activityManager?.isLowRamDevice ?: false
    val isPowerSave = powerManager?.isPowerSaveMode ?: false
    val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    val hasSpeechService =
      runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    // Stretch standby frame sleep when device is low-RAM or in battery saver mode.
    val wakeIntervalMs =
      when {
        isPowerSave -> 80L
        isLowRam -> 50L
        else -> 30L
      }

    val notes = buildList {
      if (!hasMic) {
        add("No microphone hardware detected: Wake Word 'MAX' falls back to manual activation.")
      }
      if (!hasSpeechService) {
        add("System SpeechRecognizer service unavailable: future speech module will use manual text fallback.")
      }
      if (isLowRam) {
        add("Low-RAM device detected: MAX runs with compacted ring buffers and reduced frame cadence.")
      }
      if (isPowerSave) {
        add("Battery Saver active: Wake Word standby cadence throttled to conserve power.")
      }
    }

    return DeviceCompatibilityProfile(
      sdkInt = Build.VERSION.SDK_INT,
      deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
      isLowRamDevice = isLowRam,
      isPowerSaveMode = isPowerSave,
      hasMicrophone = hasMic,
      hasSpeechRecognitionService = hasSpeechService,
      hasCameraHardware = hasCamera,
      hasTelephonyHardware = hasTelephony,
      recommendedWakeWordIntervalMs = wakeIntervalMs,
      fallbackNotes = notes,
    )
  }
}
