package com.example.camera

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining MAX Camera operations across Core, Repository, ViewModel, and UI.
 *
 * Privacy contract:
 * - Implementations MUST NEVER open the camera without explicit user initiation.
 * - Camera resources MUST be released immediately upon task completion or cancellation.
 */
interface CameraController {
  val state: StateFlow<CameraState>
  val isActive: StateFlow<Boolean>
  val activeMode: StateFlow<CameraMode>
  val activeLens: StateFlow<CameraLens>
  val flashEnabled: StateFlow<Boolean>
  val hardwareInfo: StateFlow<CameraHardwareInfo>
  val lastCapturedMedia: StateFlow<CapturedMediaResult?>
  val lastScannedQr: StateFlow<String?>

  fun inspectHardware(): CameraHardwareInfo

  fun openCamera(mode: CameraMode, initiatedByUser: Boolean): Boolean

  fun setMode(mode: CameraMode)

  fun switchLens(): CameraLens

  fun toggleFlash(): Boolean

  fun onPreviewBound()

  fun onPhotoCaptured(uriString: String)

  fun onVideoRecordingStarted(audioEnabled: Boolean)

  fun onVideoRecordingProgress(elapsedSeconds: Long)

  fun onVideoRecordingFinished(uriString: String)

  fun onQrCodeDetected(rawValue: String, format: String = "QR_CODE")

  fun onCameraError(message: String)

  fun closeCamera(reason: String = "Task completed")

  fun release()
}
