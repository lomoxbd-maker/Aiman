package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.camera.CameraMode
import com.example.camera.CameraState
import com.example.gallery.GalleryFilter
import com.example.ui.components.SectionHeader
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionScreen(
  uiState: MAXUiState,
  onOpenCamera: (CameraMode) -> Unit,
  onBindCameraXSession: (LifecycleOwner, Preview.SurfaceProvider) -> Unit,
  onCapturePhoto: () -> Unit,
  onStartVideoRecording: () -> Unit,
  onStopVideoRecording: () -> Unit,
  onSwitchCameraLens: () -> Unit,
  onToggleCameraFlash: () -> Unit,
  onCloseCamera: () -> Unit,
  onOpenGallery: () -> Unit,
  onGalleryMediaPicked: (List<Uri>) -> Unit,
  onSelectGalleryItem: (String) -> Unit,
  onFindGalleryPhotos: (String, GalleryFilter) -> Unit,
  onPrepareGalleryOcr: (String) -> Unit,
  onRequestDeleteGalleryItem: (String) -> Unit,
  onConfirmDeleteGalleryItem: () -> Unit,
  onCancelDeleteGalleryItem: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var searchDraft by remember(uiState.gallerySearchQuery) { mutableStateOf(uiState.gallerySearchQuery) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
      onGalleryMediaPicked(uris)
    }

  LaunchedEffect(uiState.galleryPickerLaunchToken) {
    if (uiState.galleryPickerLaunchToken > 0L) {
      val mediaType =
        when (uiState.galleryFilter) {
          GalleryFilter.IMAGES -> ActivityResultContracts.PickVisualMedia.ImageOnly
          GalleryFilter.VIDEOS -> ActivityResultContracts.PickVisualMedia.VideoOnly
          GalleryFilter.ALL -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
        }
      photoPickerLauncher.launch(PickVisualMediaRequest(mediaType))
    }
  }

  val isCameraOpen =
    uiState.cameraState is CameraState.Preparing ||
      uiState.cameraState is CameraState.PreviewActive ||
      uiState.cameraState is CameraState.RecordingVideo

  DisposableEffect(Unit) {
    onDispose {
      if (isCameraOpen) {
        onCloseCamera()
      }
    }
  }

  uiState.pendingDeleteConfirmation?.let { pending ->
    AlertDialog(
      onDismissRequest = onCancelDeleteGalleryItem,
      title = { Text("Confirm Removal") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(pending.warningMessageBangla, style = MaterialTheme.typography.bodyMedium)
          Text(
            pending.warningMessageEnglish,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = onConfirmDeleteGalleryItem,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_gallery_button"),
        ) {
          Text("Remove")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = onCancelDeleteGalleryItem,
          modifier = Modifier.testTag("cancel_delete_gallery_button"),
        ) {
          Text("Keep")
        }
      },
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // MAX Camera System Deck
        SectionHeader(
          title = "MAX Camera System",
          trailingBadge = if (isCameraOpen) "LIVE • ${uiState.cameraMode.displayName}" else "Dormant",
        )

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text =
                "Camera never starts automatically. Choose a mode to request permission if needed, complete your task, and auto-close hardware.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = { onOpenCamera(CameraMode.PHOTO) },
                modifier = Modifier.testTag("camera_mode_photo"),
              ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Take Photo")
              }
              FilledTonalButton(
                onClick = { onOpenCamera(CameraMode.SELFIE) },
                modifier = Modifier.testTag("camera_mode_selfie"),
              ) {
                Icon(Icons.Filled.Face, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Selfie")
              }
              FilledTonalButton(
                onClick = { onOpenCamera(CameraMode.VIDEO_RECORDING) },
                modifier = Modifier.testTag("camera_mode_video"),
              ) {
                Icon(Icons.Filled.Videocam, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Record Video")
              }
              FilledTonalButton(
                onClick = { onOpenCamera(CameraMode.QR_SCAN) },
                modifier = Modifier.testTag("camera_mode_qr"),
              ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Scan QR")
              }
            }

            if (isCameraOpen) {
              CameraLivePreviewCard(
                uiState = uiState,
                lifecycleOwner = lifecycleOwner,
                onBindCameraXSession = onBindCameraXSession,
                onCapturePhoto = onCapturePhoto,
                onStartVideoRecording = onStartVideoRecording,
                onStopVideoRecording = onStopVideoRecording,
                onSwitchCameraLens = onSwitchCameraLens,
                onToggleCameraFlash = onToggleCameraFlash,
                onCloseCamera = onCloseCamera,
              )
            }

            when (val camState = uiState.cameraState) {
              is CameraState.QrDetected -> {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = EmeraldReady.copy(alpha = 0.14f),
                  border = BorderStroke(1.dp, EmeraldReady),
                ) {
                  Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                      text = "QR Scanned (${camState.format}) — Camera Closed",
                      style = MaterialTheme.typography.labelLarge,
                      color = EmeraldReady,
                    )
                    Text(
                      text = camState.rawValue,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
              is CameraState.CaptureCompleted -> {
                Text(
                  text = "Saved ${camState.result.mode.displayName} (${camState.result.lens.displayName}). Camera hardware released.",
                  style = MaterialTheme.typography.labelMedium,
                  color = EmeraldReady,
                )
              }
              is CameraState.UnsupportedFallback -> {
                Text(
                  text = camState.reason,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              is CameraState.Error -> {
                Text(
                  text = camState.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              else -> Unit
            }
          }
        }

        // MAX Gallery System Deck
        SectionHeader(
          title = "MAX Gallery & Photo Search",
          trailingBadge = "${uiState.galleryItems.size} selected",
        )

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Button(
              onClick = onOpenGallery,
              modifier = Modifier.fillMaxWidth().testTag("open_gallery_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Open Gallery (Zero-Permission Picker)")
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = searchDraft,
                onValueChange = {
                  searchDraft = it
                  onFindGalleryPhotos(it, uiState.galleryFilter)
                },
                placeholder = { Text("Find photos by name, tag, or OCR...") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("gallery_search_input"),
              )
              IconButton(
                onClick = { onFindGalleryPhotos(searchDraft, uiState.galleryFilter) },
                modifier = Modifier.testTag("gallery_search_button"),
              ) {
                Icon(Icons.Filled.Search, contentDescription = "Find photos")
              }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(GalleryFilter.entries) { filter ->
                FilterChip(
                  selected = uiState.galleryFilter == filter,
                  onClick = { onFindGalleryPhotos(searchDraft, filter) },
                  label = { Text(filter.displayName) },
                  modifier = Modifier.testTag("gallery_filter_${filter.name.lowercase()}"),
                )
              }
            }

            uiState.activeGalleryItem?.let { active ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  if (!active.isVideo && !active.mimeType.startsWith("text/")) {
                    AsyncImage(
                      model = active.uriString,
                      contentDescription = active.displayName,
                      contentScale = ContentScale.Crop,
                      modifier =
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                    )
                  }
                  Text(
                    text = active.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "${active.mimeType} • Tags: ${active.searchTags.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    FilledTonalButton(
                      onClick = { onPrepareGalleryOcr(active.id) },
                      modifier = Modifier.weight(1f).testTag("gallery_prepare_ocr_button"),
                    ) {
                      Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                      Spacer(Modifier.width(6.dp))
                      Text("Prepare OCR")
                    }
                    OutlinedButton(
                      onClick = { onRequestDeleteGalleryItem(active.id) },
                      modifier = Modifier.weight(1f).testTag("gallery_request_delete_button"),
                    ) {
                      Icon(Icons.Filled.Delete, contentDescription = null)
                      Spacer(Modifier.width(6.dp))
                      Text("Remove")
                    }
                  }
                }
              }
            } ?: Text(
              text = "No photos selected yet. MAX never scans your gallery automatically.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.lastOcrResult?.let { ocr ->
              Text(
                text = ocr.statusMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            if (uiState.galleryItems.isNotEmpty()) {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.galleryItems, key = { it.id }) { item ->
                  val isSelected = uiState.activeGalleryItem?.id == item.id
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    border =
                      BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                      ),
                    modifier =
                      Modifier
                        .size(width = 96.dp, height = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectGalleryItem(item.id) }
                        .testTag("gallery_thumb_${item.id}"),
                  ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                      if (!item.isVideo && !item.mimeType.startsWith("text/")) {
                        AsyncImage(
                          model = item.uriString,
                          contentDescription = item.displayName,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.fillMaxSize(),
                        )
                      } else {
                        Box(
                          modifier = Modifier.fillMaxSize().padding(8.dp),
                          contentAlignment = Alignment.Center,
                        ) {
                          Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 3,
                          )
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
    }
  }
}

@Composable
private fun CameraLivePreviewCard(
  uiState: MAXUiState,
  lifecycleOwner: LifecycleOwner,
  onBindCameraXSession: (LifecycleOwner, Preview.SurfaceProvider) -> Unit,
  onCapturePhoto: () -> Unit,
  onStartVideoRecording: () -> Unit,
  onStopVideoRecording: () -> Unit,
  onSwitchCameraLens: () -> Unit,
  onToggleCameraFlash: () -> Unit,
  onCloseCamera: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val previewViewHolder = remember { mutableStateOf<PreviewView?>(null) }

      LaunchedEffect(uiState.cameraMode, uiState.cameraLens, previewViewHolder.value) {
        val pv = previewViewHolder.value ?: return@LaunchedEffect
        onBindCameraXSession(lifecycleOwner, pv.surfaceProvider)
      }

      Box(
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
      ) {
        AndroidView(
          factory = { ctx ->
            PreviewView(ctx).apply {
              scaleType = PreviewView.ScaleType.FILL_CENTER
              previewViewHolder.value = this
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onSwitchCameraLens,
          modifier = Modifier.testTag("camera_switch_lens_button"),
        ) {
          Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch lens")
        }
        IconButton(
          onClick = onToggleCameraFlash,
          modifier = Modifier.testTag("camera_flash_button"),
        ) {
          Icon(
            imageVector = if (uiState.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            contentDescription = "Toggle flash",
          )
        }

        when (uiState.cameraState) {
          is CameraState.RecordingVideo -> {
            Button(
              onClick = onStopVideoRecording,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.weight(1f).testTag("camera_stop_video_button"),
            ) {
              Icon(Icons.Filled.Stop, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Stop (${uiState.cameraState.elapsedSeconds}s)")
            }
          }
          else -> {
            when (uiState.cameraMode) {
              CameraMode.VIDEO_RECORDING -> {
                Button(
                  onClick = onStartVideoRecording,
                  modifier = Modifier.weight(1f).testTag("camera_start_video_button"),
                ) {
                  Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Start Video")
                }
              }
              CameraMode.QR_SCAN -> {
                Text(
                  text = "Point at a QR code to scan offline...",
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.weight(1f),
                )
              }
              else -> {
                Button(
                  onClick = onCapturePhoto,
                  modifier = Modifier.weight(1f).testTag("camera_capture_button"),
                ) {
                  Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Capture")
                }
              }
            }
          }
        }

        IconButton(
          onClick = onCloseCamera,
          modifier = Modifier.testTag("camera_close_button"),
        ) {
          Icon(Icons.Filled.Close, contentDescription = "Close camera")
        }
      }
    }
  }
}
