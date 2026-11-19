package com.example.camera

/**
 * Supported capture and vision modes in MAX's Camera System.
 */
enum class CameraMode(val displayName: String, val displayBangla: String) {
  PHOTO("Take Photo", "ছবি তুলুন"),
  SELFIE("Selfie Mode", "সেলফি মোড"),
  VIDEO_RECORDING("Video Recording", "ভিডিও রেকর্ড"),
  QR_SCAN("Scan QR Code", "QR কোড স্ক্যান"),
}

/**
 * Physical camera lens selection with automatic fallback when front camera is absent.
 */
enum class CameraLens(val displayName: String) {
  BACK("Rear Camera"),
  FRONT("Front Camera"),
}

/**
 * Passive hardware snapshot detected via [android.content.pm.PackageManager].
 */
data class CameraHardwareInfo(
  val hasAnyCamera: Boolean = false,
  val hasBackCamera: Boolean = false,
  val hasFrontCamera: Boolean = false,
  val hasFlash: Boolean = false,
  val supportsVideo: Boolean = false,
)

/**
 * Outcome of an explicit user-triggered camera task.
 */
data class CapturedMediaResult(
  val id: String,
  val uriString: String,
  val mode: CameraMode,
  val lens: CameraLens,
  val timestampMillis: Long,
  val qrContent: String? = null,
)

/**
 * Lifecycle & operational states for MAX Camera System.
 * Camera starts in [Dormant] and returns to [Dormant] immediately after task completion.
 */
sealed class CameraState {
  data object Dormant : CameraState()

  data class PermissionRequired(
    val requestedMode: CameraMode,
    val explanation: String,
  ) : CameraState()

  data class BlockedByPrivacyPolicy(
    val requestedMode: CameraMode,
    val reason: String,
  ) : CameraState()

  data class Preparing(
    val mode: CameraMode,
    val lens: CameraLens,
  ) : CameraState()

  data class PreviewActive(
    val mode: CameraMode,
    val lens: CameraLens,
    val flashEnabled: Boolean,
    val hardwareInfo: CameraHardwareInfo,
  ) : CameraState()

  data class RecordingVideo(
    val lens: CameraLens,
    val elapsedSeconds: Long,
    val audioEnabled: Boolean,
  ) : CameraState()

  data class QrDetected(
    val rawValue: String,
    val format: String,
    val timestampMillis: Long,
  ) : CameraState()

  data class CaptureCompleted(
    val result: CapturedMediaResult,
  ) : CameraState()

  data class UnsupportedFallback(
    val reason: String,
  ) : CameraState()

  data class Error(
    val message: String,
  ) : CameraState()
}
