package com.example.phone

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.example.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstraction over Android [CameraManager] torch hardware for cross-OEM compatibility and testing.
 */
interface TorchHardwareBackend {
  fun hasFlashHardware(): Boolean
  fun setTorchEnabled(enabled: Boolean): Result<Boolean>
  fun releaseCallback()
}

/**
 * FlashlightManager — Privacy-locked flashlight controller using Camera2 `setTorchMode`.
 *
 * Guarantees:
 * - Never turns on flashlight automatically (`confirmedOrUserRequested` is strictly checked).
 * - Handles devices without camera flash (e.g., tablets/emulators) gracefully.
 * - Releases torch OFF and unregisters callbacks on [release] to prevent battery drain.
 */
class FlashlightManager(
  private val context: Context,
  private val errorTracker: ErrorTracker,
  customBackend: TorchHardwareBackend? = null,
) {

  private val _isTorchOn = MutableStateFlow(false)
  val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

  private val hardwareBackend: TorchHardwareBackend =
    customBackend ?: Camera2TorchBackend(context, errorTracker) { isEnabled ->
      _isTorchOn.value = isEnabled
    }

  fun isFlashSupported(): Boolean = hardwareBackend.hasFlashHardware()

  fun setTorch(enable: Boolean, initiatedByUser: Boolean): Result<Boolean> {
    if (!initiatedByUser) {
      val msg = "Blocked automatic flashlight activation. Explicit user action is required."
      errorTracker.logWarning(MODULE_NAME, msg)
      return Result.failure(SecurityException(msg))
    }

    if (!hardwareBackend.hasFlashHardware()) {
      return Result.failure(UnsupportedOperationException("This device has no camera flash hardware."))
    }

    return hardwareBackend.setTorchEnabled(enable).onSuccess { actual ->
      _isTorchOn.value = actual
      errorTracker.logInfo(MODULE_NAME, "Flashlight turned ${if (actual) "ON" else "OFF"} by user.")
    }.onFailure { err ->
      errorTracker.logWarning(MODULE_NAME, "Flashlight toggle failed: ${err.message}")
    }
  }

  fun release() {
    if (_isTorchOn.value) {
      runCatching { hardwareBackend.setTorchEnabled(false) }
      _isTorchOn.value = false
    }
    hardwareBackend.releaseCallback()
  }

  companion object {
    private const val MODULE_NAME = "FlashlightManager"
  }
}

private class Camera2TorchBackend(
  private val context: Context,
  private val errorTracker: ErrorTracker,
  private val onTorchStateChanged: (Boolean) -> Unit,
) : TorchHardwareBackend {

  private val cameraManager: CameraManager? by lazy {
    runCatching { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }.getOrNull()
  }

  private var cachedFlashCameraId: String? = null
  private var torchCallbackRegistered = false

  private val torchCallback =
    object : CameraManager.TorchCallback() {
      override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
        if (cameraId == cachedFlashCameraId) {
          onTorchStateChanged(enabled)
        }
      }

      override fun onTorchModeUnavailable(cameraId: String) {
        if (cameraId == cachedFlashCameraId) {
          onTorchStateChanged(false)
        }
      }
    }

  override fun hasFlashHardware(): Boolean {
    val hasFeature =
      runCatching {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
      }.getOrDefault(false)
    if (!hasFeature) return false
    return findFlashCameraId() != null
  }

  override fun setTorchEnabled(enabled: Boolean): Result<Boolean> {
    val cm = cameraManager
      ?: return Result.failure(UnsupportedOperationException("CameraManager service unavailable."))
    val flashCameraId = findFlashCameraId()
      ?: return Result.failure(UnsupportedOperationException("No rear/front flash unit detected."))

    if (!torchCallbackRegistered) {
      runCatching {
        cm.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        torchCallbackRegistered = true
      }
    }

    return runCatching {
      cm.setTorchMode(flashCameraId, enabled)
      onTorchStateChanged(enabled)
      enabled
    }.recoverCatching { err ->
      errorTracker.logError("Camera2TorchBackend", "setTorchMode failed: ${err.message}", err)
      throw err
    }
  }

  override fun releaseCallback() {
    if (torchCallbackRegistered) {
      runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
      torchCallbackRegistered = false
    }
  }

  private fun findFlashCameraId(): String? {
    cachedFlashCameraId?.let { return it }
    val cm = cameraManager ?: return null
    return runCatching {
      // Prefer back-facing camera with flash, then fall back to any flash camera.
      var fallbackId: String? = null
      for (id in cm.cameraIdList) {
        val chars = cm.getCameraCharacteristics(id)
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        if (hasFlash) {
          val facing = chars.get(CameraCharacteristics.LENS_FACING)
          if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            cachedFlashCameraId = id
            return@runCatching id
          }
          if (fallbackId == null) fallbackId = id
        }
      }
      cachedFlashCameraId = fallbackId
      fallbackId
    }.getOrNull()
  }
}
