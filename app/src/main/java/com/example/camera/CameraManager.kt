package com.example.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.permissions.AppPermission
import com.example.permissions.PermissionController
import com.example.permissions.PermissionRequestHandler
import com.example.permissions.PermissionStatus
import com.example.permissions.PrivacyCheckResult
import com.example.permissions.PrivacyGuard
import com.example.permissions.SensitiveAction
import com.example.utils.ErrorTracker
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listener notified by [CameraManager] when camera tasks produce user results so
 * [com.example.core.MAXCore], [com.example.conversation.ConversationManager], and
 * [com.example.tts.TTSManager] can react naturally.
 */
interface CameraEventListener {
  fun onPhotoSaved(result: CapturedMediaResult)

  fun onVideoSaved(result: CapturedMediaResult)

  fun onQrScanned(rawValue: String, format: String)

  fun onCameraClosed(reason: String)
}

/**
 * CameraManager — Manages MAX's explicit user-triggered CameraX pipeline:
 * - Photo capture (rear/front)
 * - Selfie mode (defaults to front camera with fallback to rear when front is absent)
 * - Video recording (CameraX VideoCapture + Recorder with low-RAM quality adaptation)
 * - Offline QR code scanning (throttled ZXing Y-plane analyzer for low battery & RAM usage)
 *
 * Strict privacy guarantees:
 * - NEVER opens the camera automatically (`initiatedByUser == false` is immediately rejected).
 * - Checks permissions via [PermissionController], [PrivacyGuard], and [PermissionRequestHandler].
 * - Automatically closes & unbinds CameraX hardware immediately after capture/scan completion.
 */
class CameraManager(
  private val context: Context,
  private val permissionController: PermissionController,
  private val privacyGuard: PrivacyGuard,
  private val permissionRequestHandler: PermissionRequestHandler,
  private val errorTracker: ErrorTracker,
  private val isLowRamDevice: Boolean = false,
) : CameraController, CameraCapability {

  private var eventListener: CameraEventListener? = null

  private val _state = MutableStateFlow<CameraState>(CameraState.Dormant)
  override val state: StateFlow<CameraState> = _state.asStateFlow()

  private val _isActive = MutableStateFlow(false)
  override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

  private val _activeMode = MutableStateFlow(CameraMode.PHOTO)
  override val activeMode: StateFlow<CameraMode> = _activeMode.asStateFlow()

  private val _activeLens = MutableStateFlow(CameraLens.BACK)
  override val activeLens: StateFlow<CameraLens> = _activeLens.asStateFlow()

  private val _flashEnabled = MutableStateFlow(false)
  override val flashEnabled: StateFlow<Boolean> = _flashEnabled.asStateFlow()

  private val _hardwareInfo = MutableStateFlow(inspectHardwareInternal())
  override val hardwareInfo: StateFlow<CameraHardwareInfo> = _hardwareInfo.asStateFlow()

  private val _lastCapturedMedia = MutableStateFlow<CapturedMediaResult?>(null)
  override val lastCapturedMedia: StateFlow<CapturedMediaResult?> = _lastCapturedMedia.asStateFlow()

  private val _lastScannedQr = MutableStateFlow<String?>(null)
  override val lastScannedQr: StateFlow<String?> = _lastScannedQr.asStateFlow()

  override val isPrivacyLocked: Boolean
    get() = privacyGuard.policy.value.allowCameraOnUserAction != true

  override val isHardwareSupported: Boolean
    get() = _hardwareInfo.value.hasAnyCamera

  // CameraX runtime references — strictly cleared & unbound whenever camera closes.
  private var cameraProvider: ProcessCameraProvider? = null
  private var boundCamera: androidx.camera.core.Camera? = null
  private var imageCapture: ImageCapture? = null
  private var videoCapture: VideoCapture<Recorder>? = null
  private var activeRecording: Recording? = null
  private var analysisExecutor: ExecutorService? = null

  fun setEventListener(listener: CameraEventListener?) {
    eventListener = listener
  }

  override fun checkExplicitAccess(initiatedByUser: Boolean): PrivacyCheckResult {
    return privacyGuard.authorizeAction(SensitiveAction.OPEN_CAMERA, initiatedByUser)
  }

  override fun inspectHardware(): CameraHardwareInfo {
    val latest = inspectHardwareInternal()
    _hardwareInfo.value = latest
    return latest
  }

  private fun inspectHardwareInternal(): CameraHardwareInfo {
    val pm = context.packageManager
    val hasAny = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    val hasBack = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    val hasFront = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    return CameraHardwareInfo(
      hasAnyCamera = hasAny || hasBack || hasFront,
      hasBackCamera = hasBack || hasAny,
      hasFrontCamera = hasFront,
      hasFlash = hasFlash,
      supportsVideo = hasAny || hasBack || hasFront,
    )
  }

  override fun openCamera(mode: CameraMode, initiatedByUser: Boolean): Boolean {
    // 1. Block any automatic/background camera opening attempt.
    if (!initiatedByUser) {
      val reason = "Blocked automatic camera access for ${mode.displayName}. Explicit user command required."
      errorTracker.logWarning(MODULE_NAME, reason)
      _state.value = CameraState.BlockedByPrivacyPolicy(mode, reason)
      return false
    }

    // 2. Verify device camera hardware exists.
    val hw = inspectHardware()
    if (!hw.hasAnyCamera) {
      val msg = "No camera hardware available on this device."
      errorTracker.logWarning(MODULE_NAME, msg)
      _state.value = CameraState.UnsupportedFallback(msg)
      return false
    }

    // 3. Enforce PrivacyController & PermissionManager checks before opening.
    val auth = privacyGuard.authorizeAction(SensitiveAction.OPEN_CAMERA, initiatedByUser = true)
    if (auth is PrivacyCheckResult.Denied) {
      permissionRequestHandler.requestForExplicitAction(
        permission = AppPermission.CAMERA,
        preferBangla = true,
      )
      _state.value =
        if (auth.requiresUserExplanation ||
          permissionController.checkStatus(AppPermission.CAMERA) != PermissionStatus.GRANTED
        ) {
          CameraState.PermissionRequired(mode, auth.reason)
        } else {
          CameraState.BlockedByPrivacyPolicy(mode, auth.reason)
        }
      return false
    }

    // 4. Select safe default lens (Selfie tries FRONT, falls back to BACK if unavailable).
    val chosenLens =
      when {
        mode == CameraMode.SELFIE && hw.hasFrontCamera -> CameraLens.FRONT
        mode == CameraMode.SELFIE && !hw.hasFrontCamera -> {
          errorTracker.logInfo(MODULE_NAME, "Front camera missing; falling back to rear camera.")
          CameraLens.BACK
        }
        hw.hasBackCamera -> CameraLens.BACK
        hw.hasFrontCamera -> CameraLens.FRONT
        else -> CameraLens.BACK
      }

    _activeMode.value = mode
    _activeLens.value = chosenLens
    _flashEnabled.value = false
    _isActive.value = true
    _state.value = CameraState.Preparing(mode, chosenLens)
    errorTracker.logInfo(MODULE_NAME, "Camera prepared by explicit user command: $mode ($chosenLens).")
    return true
  }

  override fun setMode(mode: CameraMode) {
    if (!_isActive.value) {
      openCamera(mode, initiatedByUser = true)
      return
    }
    val hw = _hardwareInfo.value
    val nextLens =
      if (mode == CameraMode.SELFIE && hw.hasFrontCamera) {
        CameraLens.FRONT
      } else if (mode != CameraMode.SELFIE && hw.hasBackCamera) {
        CameraLens.BACK
      } else {
        _activeLens.value
      }
    _activeMode.value = mode
    _activeLens.value = nextLens
    _state.value = CameraState.PreviewActive(mode, nextLens, _flashEnabled.value, hw)
  }

  override fun switchLens(): CameraLens {
    val hw = _hardwareInfo.value
    val current = _activeLens.value
    val target =
      when {
        current == CameraLens.BACK && hw.hasFrontCamera -> CameraLens.FRONT
        current == CameraLens.FRONT && hw.hasBackCamera -> CameraLens.BACK
        else -> current
      }
    _activeLens.value = target
    if (_isActive.value) {
      _state.value = CameraState.PreviewActive(_activeMode.value, target, _flashEnabled.value, hw)
    }
    return target
  }

  override fun toggleFlash(): Boolean {
    val hw = _hardwareInfo.value
    if (!hw.hasFlash || _activeLens.value == CameraLens.FRONT) {
      _flashEnabled.value = false
      return false
    }
    val next = !_flashEnabled.value
    _flashEnabled.value = next
    runCatching {
      boundCamera?.cameraControl?.enableTorch(next && _activeMode.value == CameraMode.QR_SCAN)
      imageCapture?.flashMode =
        if (next) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    if (_isActive.value) {
      _state.value = CameraState.PreviewActive(_activeMode.value, _activeLens.value, next, hw)
    }
    return next
  }

  override fun onPreviewBound() {
    if (_isActive.value) {
      _state.value =
        CameraState.PreviewActive(
          mode = _activeMode.value,
          lens = _activeLens.value,
          flashEnabled = _flashEnabled.value,
          hardwareInfo = _hardwareInfo.value,
        )
    }
  }

  /**
   * Binds only the CameraX use cases required by [mode] so memory and battery remain optimized.
   */
  fun bindCameraXSession(
    lifecycleOwner: LifecycleOwner,
    surfaceProvider: Preview.SurfaceProvider,
  ) {
    if (!_isActive.value) return
    val mode = _activeMode.value
    val lens = _activeLens.value

    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
      {
        runCatching {
          val provider = providerFuture.get()
          cameraProvider = provider
          provider.unbindAll()

          val selector =
            if (lens == CameraLens.FRONT && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
              CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
              CameraSelector.DEFAULT_BACK_CAMERA
            }

          val preview =
            Preview.Builder().build().also {
              it.surfaceProvider = surfaceProvider
            }

          val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)

          when (mode) {
            CameraMode.PHOTO,
            CameraMode.SELFIE, -> {
              val capture =
                ImageCapture.Builder()
                  .setCaptureMode(
                    if (isLowRamDevice) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY,
                  )
                  .setFlashMode(
                    if (_flashEnabled.value) ImageCapture.FLASH_MODE_ON
                    else ImageCapture.FLASH_MODE_OFF,
                  )
                  .build()
              imageCapture = capture
              videoCapture = null
              useCases.add(capture)
            }

            CameraMode.VIDEO_RECORDING -> {
              val preferredQuality =
                if (isLowRamDevice) Quality.SD else Quality.HD
              val qualitySelector =
                QualitySelector.from(
                  preferredQuality,
                  FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
              val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
              val video = VideoCapture.withOutput(recorder)
              videoCapture = video
              imageCapture = null
              useCases.add(video)
            }

            CameraMode.QR_SCAN -> {
              imageCapture = null
              videoCapture = null
              val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also { analysisExecutor = it }
              val analysis =
                ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
                  .also {
                    it.setAnalyzer(executor, ThrottledQrImageAnalyzer { value, format ->
                      ContextCompat.getMainExecutor(context).execute {
                        onQrCodeDetected(value, format)
                      }
                    })
                  }
              useCases.add(analysis)
            }
          }

          boundCamera =
            provider.bindToLifecycle(
              lifecycleOwner,
              selector,
              *useCases.toTypedArray(),
            )
          if (mode == CameraMode.QR_SCAN && _flashEnabled.value &&
            boundCamera?.cameraInfo?.torchState?.value != TorchState.ON
          ) {
            boundCamera?.cameraControl?.enableTorch(true)
          }
          onPreviewBound()
        }.onFailure { err ->
          errorTracker.logError(MODULE_NAME, "Failed binding CameraX use cases", err)
          onCameraError(err.localizedMessage ?: "Camera initialization failed.")
        }
      },
      ContextCompat.getMainExecutor(context),
    )
  }

  /**
   * Captures a still photo via CameraX [ImageCapture] using scoped storage on Android 10+
   * or app-private external pictures directory on Android 7-9 (requiring zero storage permission).
   */
  fun capturePhotoNow() {
    val capture = imageCapture
    if (capture == null) {
      onCameraError("Camera photo pipeline is not ready.")
      return
    }
    val fileName = "MAX_${System.currentTimeMillis()}.jpg"
    val outputOptions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
          ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MAX")
          }
        ImageCapture.OutputFileOptions.Builder(
          context.contentResolver,
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          values,
        ).build()
      } else {
        val dir = File(context.filesDir, "max_photos").apply { mkdirs() }
        ImageCapture.OutputFileOptions.Builder(File(dir, fileName)).build()
      }

    capture.takePicture(
      outputOptions,
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
          val savedUri =
            outputFileResults.savedUri?.toString()
              ?: File(File(context.filesDir, "max_photos"), fileName).toURI().toString()
          onPhotoCaptured(savedUri)
        }

        override fun onError(exception: ImageCaptureException) {
          onCameraError(exception.localizedMessage ?: "Photo capture failed.")
        }
      },
    )
  }

  /**
   * Starts CameraX video recording. Audio is attached ONLY when microphone permission is
   * already granted (respecting CameraX fluent immutability); otherwise records silent video cleanly.
   */
  @SuppressLint("MissingPermission")
  fun startVideoRecording() {
    val video = videoCapture
    if (video == null) {
      onCameraError("Video recording pipeline is not ready.")
      return
    }
    if (activeRecording != null) return

    val fileName = "MAX_VID_${System.currentTimeMillis()}.mp4"
    val canRecordAudio = permissionController.hasWakeWordAudioPermission() &&
      privacyGuard.policy.value.allowMicrophoneSpeechPostWake

    val pending =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values =
          ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MAX")
          }
        val mediaStoreOptions =
          MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          )
            .setContentValues(values)
            .build()
        video.output.prepareRecording(context, mediaStoreOptions)
      } else {
        val dir = File(context.filesDir, "max_videos").apply { mkdirs() }
        val fileOptions = FileOutputOptions.Builder(File(dir, fileName)).build()
        video.output.prepareRecording(context, fileOptions)
      }

    // Reassign immutable PendingRecording cleanly per CameraX best practice.
    val configuredPending =
      if (canRecordAudio) pending.withAudioEnabled() else pending

    activeRecording =
      configuredPending.start(ContextCompat.getMainExecutor(context)) { event ->
        when (event) {
          is VideoRecordEvent.Start -> onVideoRecordingStarted(canRecordAudio)
          is VideoRecordEvent.Status -> {
            val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
            onVideoRecordingProgress(seconds)
          }
          is VideoRecordEvent.Finalize -> {
            activeRecording = null
            if (!event.hasError()) {
              onVideoRecordingFinished(event.outputResults.outputUri.toString())
            } else {
              onCameraError("Video recording stopped with code ${event.error}.")
            }
          }
        }
      }
  }

  fun stopVideoRecording() {
    runCatching { activeRecording?.stop() }
  }

  override fun onPhotoCaptured(uriString: String) {
    val result =
      CapturedMediaResult(
        id = UUID.randomUUID().toString(),
        uriString = uriString,
        mode = _activeMode.value,
        lens = _activeLens.value,
        timestampMillis = System.currentTimeMillis(),
      )
    _lastCapturedMedia.value = result
    _state.value = CameraState.CaptureCompleted(result)
    errorTracker.logInfo(MODULE_NAME, "Photo captured (${_activeLens.value}); releasing camera resources.")
    closeCameraHardwareOnly()
    eventListener?.onPhotoSaved(result)
  }

  override fun onVideoRecordingStarted(audioEnabled: Boolean) {
    _state.value = CameraState.RecordingVideo(_activeLens.value, 0L, audioEnabled)
  }

  override fun onVideoRecordingProgress(elapsedSeconds: Long) {
    val current = _state.value
    if (current is CameraState.RecordingVideo) {
      _state.value = current.copy(elapsedSeconds = elapsedSeconds)
    }
  }

  override fun onVideoRecordingFinished(uriString: String) {
    val result =
      CapturedMediaResult(
        id = UUID.randomUUID().toString(),
        uriString = uriString,
        mode = CameraMode.VIDEO_RECORDING,
        lens = _activeLens.value,
        timestampMillis = System.currentTimeMillis(),
      )
    _lastCapturedMedia.value = result
    _state.value = CameraState.CaptureCompleted(result)
    errorTracker.logInfo(MODULE_NAME, "Video saved; releasing camera resources.")
    closeCameraHardwareOnly()
    eventListener?.onVideoSaved(result)
  }

  override fun onQrCodeDetected(rawValue: String, format: String) {
    if (!_isActive.value) return
    val cleanValue = rawValue.trim()
    if (cleanValue.isEmpty()) return
    _lastScannedQr.value = cleanValue
    val result =
      CapturedMediaResult(
        id = UUID.randomUUID().toString(),
        uriString = cleanValue,
        mode = CameraMode.QR_SCAN,
        lens = _activeLens.value,
        timestampMillis = System.currentTimeMillis(),
        qrContent = cleanValue,
      )
    _lastCapturedMedia.value = result
    _state.value = CameraState.QrDetected(cleanValue, format, result.timestampMillis)
    errorTracker.logInfo(MODULE_NAME, "QR code detected; closing camera resources automatically.")
    closeCameraHardwareOnly()
    eventListener?.onQrScanned(cleanValue, format)
  }

  override fun onCameraError(message: String) {
    errorTracker.logError(MODULE_NAME, "Camera error: $message")
    closeCameraHardwareOnly()
    _state.value = CameraState.Error(message)
  }

  override fun closeCamera(reason: String) {
    closeCameraHardwareOnly()
    _state.value = CameraState.Dormant
    eventListener?.onCameraClosed(reason)
  }

  private fun closeCameraHardwareOnly() {
    runCatching { activeRecording?.stop() }
    activeRecording = null
    runCatching { boundCamera?.cameraControl?.enableTorch(false) }
    boundCamera = null
    imageCapture = null
    videoCapture = null
    runCatching { cameraProvider?.unbindAll() }
    cameraProvider = null
    analysisExecutor?.shutdown()
    analysisExecutor = null
    _flashEnabled.value = false
    if (_isActive.value) {
      _isActive.value = false
      privacyGuard.notifyResourcesReleased(SensitiveAction.OPEN_CAMERA)
    }
  }

  override fun release() {
    closeCamera("MAX released")
    eventListener = null
  }

  companion object {
    private const val MODULE_NAME = "CameraManager"
  }
}

/**
 * Throttled offline QR code analyzer operating solely in RAM on the luminance Y-plane.
 */
private class ThrottledQrImageAnalyzer(
  private val onDecoded: (String, String) -> Unit,
) : ImageAnalysis.Analyzer {

  private var lastAnalyzedAtMs = 0L
  private var hasDelivered = false

  private val reader =
    MultiFormatReader().apply {
      setHints(
        mapOf(
          DecodeHintType.POSSIBLE_FORMATS to
            listOf(
              BarcodeFormat.QR_CODE,
              BarcodeFormat.CODE_128,
              BarcodeFormat.EAN_13,
            ),
        ),
      )
    }

  override fun analyze(image: ImageProxy) {
    try {
      val now = System.currentTimeMillis()
      if (hasDelivered || now - lastAnalyzedAtMs < ANALYSIS_INTERVAL_MS) return
      lastAnalyzedAtMs = now

      val plane = image.planes.firstOrNull() ?: return
      val buffer = plane.buffer
      val bytes = ByteArray(buffer.remaining())
      buffer.get(bytes)

      val source =
        PlanarYUVLuminanceSource(
          bytes,
          image.width,
          image.height,
          0,
          0,
          image.width,
          image.height,
          false,
        )
      val bitmap = BinaryBitmap(HybridBinarizer(source))
      val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
      if (result != null && result.text.isNotBlank()) {
        hasDelivered = true
        onDecoded(result.text, result.barcodeFormat.name)
      }
    } finally {
      reader.reset()
      image.close()
    }
  }

  companion object {
    private const val ANALYSIS_INTERVAL_MS = 350L
  }
}
