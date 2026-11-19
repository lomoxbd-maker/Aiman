package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.CoreLifecycleState
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.DarkBackdropBottom
import com.example.ui.theme.DarkBackdropMid
import com.example.ui.theme.DarkBackdropTop
import com.example.ui.theme.DarkGlassBorderEnd
import com.example.ui.theme.DarkGlassBorderStart
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.GradientCyanGlow
import com.example.ui.theme.GradientElectricPurple
import com.example.ui.theme.GradientRoyalBlue
import com.example.ui.theme.LightBackdropBottom
import com.example.ui.theme.LightBackdropMid
import com.example.ui.theme.LightBackdropTop
import com.example.ui.theme.LightGlassBorderEnd
import com.example.ui.theme.LightGlassBorderStart
import com.example.ui.theme.LightGlassSurface
import com.example.wakeword.WakeWordState

enum class AIOrbVisualState {
  READY,
  LISTENING,
  THINKING,
  SPEAKING,
}

/**
 * Full-bleed soft blue-and-purple gradient backdrop optimized for zero-jank 60 FPS rendering
 * in both Light Mode and Dark Mode.
 */
@Composable
fun MAXGlassBackdrop(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
  val baseBrush =
    remember(isDark) {
      if (isDark) {
        Brush.verticalGradient(
          colors = listOf(DarkBackdropTop, DarkBackdropMid, DarkBackdropBottom),
        )
      } else {
        Brush.verticalGradient(
          colors = listOf(LightBackdropTop, LightBackdropMid, LightBackdropBottom),
        )
      }
    }

  Box(modifier = modifier.fillMaxSize().background(baseBrush)) {
    // Subtle ambient top-right cosmic glow
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                GradientRoyalBlue.copy(alpha = if (isDark) 0.16f else 0.10f),
                Color.Transparent,
              ),
            center = Offset(size.width * 0.85f, size.height * 0.12f),
            radius = size.width * 0.72f,
          ),
        center = Offset(size.width * 0.85f, size.height * 0.12f),
        radius = size.width * 0.72f,
      )
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                GradientElectricPurple.copy(alpha = if (isDark) 0.16f else 0.09f),
                Color.Transparent,
              ),
            center = Offset(size.width * 0.15f, size.height * 0.65f),
            radius = size.width * 0.75f,
          ),
        center = Offset(size.width * 0.15f, size.height * 0.65f),
        radius = size.width * 0.75f,
      )
    }
    content()
  }
}

/**
 * Translucent glassmorphic card with 24.dp rounded corners, soft shadow, and luminous border.
 */
@Composable
fun MAXGlassCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 24.dp,
  onClick: (() -> Unit)? = null,
  accentColor: Color? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
  val containerColor =
    if (isDark) DarkGlassSurface.copy(alpha = 0.74f)
    else LightGlassSurface.copy(alpha = 0.84f)
  val borderBrush =
    remember(isDark, accentColor) {
      if (accentColor != null) {
        Brush.linearGradient(
          colors = listOf(accentColor.copy(alpha = 0.52f), GradientElectricPurple.copy(alpha = 0.30f)),
        )
      } else if (isDark) {
        Brush.linearGradient(
          colors = listOf(DarkGlassBorderStart.copy(alpha = 0.30f), DarkGlassBorderEnd.copy(alpha = 0.18f)),
        )
      } else {
        Brush.linearGradient(
          colors = listOf(LightGlassBorderStart.copy(alpha = 0.95f), LightGlassBorderEnd.copy(alpha = 0.45f)),
        )
      }
    }
  val shape = RoundedCornerShape(cornerRadius)

  if (onClick != null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
      targetValue = if (isPressed) 0.965f else 1.0f,
      animationSpec = tween(durationMillis = 120),
      label = "glass_card_scale",
    )
    Card(
      onClick = onClick,
      interactionSource = interactionSource,
      modifier = modifier.scale(scale),
      shape = shape,
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, borderBrush),
      elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 3.dp),
      content = content,
    )
  } else {
    Card(
      modifier = modifier,
      shape = shape,
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, borderBrush),
      elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 2.dp),
      content = content,
    )
  }
}

/**
 * Futuristic Animated AI Orb:
 * - Idle ("Ready"): gentle floating breath & blue-purple aura
 * - Listening ("Listening..."): rhythmic ripple pulses & cyan glow
 * - Thinking ("Thinking..."): smooth slow orbital rotation
 * - Speaking ("Speaking..."): dynamic harmonic pulse waves
 */
@Composable
fun FuturisticAIOrb(
  orbState: AIOrbVisualState,
  lowPowerMotion: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  orbSize: Dp = 178.dp,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "futuristic_ai_orb")

  val floatOffset by
    infiniteTransition.animateFloat(
      initialValue = -4f,
      targetValue = 4f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = if (lowPowerMotion) 3000 else 2200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orb_float",
    )

  val pulseProgress by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis =
                when {
                  lowPowerMotion -> 2400
                  orbState == AIOrbVisualState.SPEAKING -> 950
                  orbState == AIOrbVisualState.LISTENING -> 1250
                  else -> 2200
                },
              easing = FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "orb_pulse_wave",
    )

  val orbitRotation by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (orbState == AIOrbVisualState.THINKING) 2800 else 9000,
              easing = LinearEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "orb_rotation",
    )

  val primaryGlow by
    animateColorAsState(
      targetValue =
        when (orbState) {
          AIOrbVisualState.READY -> GradientRoyalBlue
          AIOrbVisualState.LISTENING -> GradientCyanGlow
          AIOrbVisualState.THINKING -> AmberStandby
          AIOrbVisualState.SPEAKING -> GradientElectricPurple
        },
      animationSpec = tween(durationMillis = 360),
      label = "orb_primary_glow",
    )

  val secondaryGlow by
    animateColorAsState(
      targetValue =
        when (orbState) {
          AIOrbVisualState.READY -> GradientElectricPurple
          AIOrbVisualState.LISTENING -> GradientRoyalBlue
          AIOrbVisualState.THINKING -> GradientElectricPurple
          AIOrbVisualState.SPEAKING -> GradientCyanGlow
        },
      animationSpec = tween(durationMillis = 360),
      label = "orb_secondary_glow",
    )

  val coreScale by
    animateFloatAsState(
      targetValue =
        when (orbState) {
          AIOrbVisualState.READY -> 1.0f
          AIOrbVisualState.LISTENING -> 1.06f
          AIOrbVisualState.THINKING -> 0.98f
          AIOrbVisualState.SPEAKING -> 1.08f
        },
      animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
      label = "orb_core_scale",
    )

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.92f else 1.0f,
    animationSpec = tween(100),
    label = "orb_press",
  )

  val statusAccessibility =
    when (orbState) {
      AIOrbVisualState.READY -> "MAX AI Orb Ready. Double tap to speak."
      AIOrbVisualState.LISTENING -> "MAX AI Orb Listening. Tap to manage session."
      AIOrbVisualState.THINKING -> "MAX AI Orb Thinking."
      AIOrbVisualState.SPEAKING -> "MAX AI Orb Speaking."
    }

  Box(
    modifier =
      modifier
        .size(orbSize)
        .graphicsLayer {
          translationY = if (lowPowerMotion) 0f else floatOffset
        },
    contentAlignment = Alignment.Center,
  ) {
    // Rotating Orbital Halo & Pulse Waves drawn cleanly on GPU Canvas
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val baseRadius = size.minDimension * 0.34f

      // Expanding pulse ring when Listening or Speaking
      if (orbState == AIOrbVisualState.LISTENING || orbState == AIOrbVisualState.SPEAKING) {
        val ringRadius = baseRadius + (size.minDimension * 0.16f * pulseProgress)
        val ringAlpha = (1f - pulseProgress) * 0.52f
        drawCircle(
          color = primaryGlow.copy(alpha = ringAlpha),
          radius = ringRadius,
          center = center,
          style = Stroke(width = 3.dp.toPx()),
        )
        if (orbState == AIOrbVisualState.SPEAKING) {
          val waveProgress = (pulseProgress + 0.5f) % 1f
          val innerWaveRadius = baseRadius + (size.minDimension * 0.14f * waveProgress)
          drawCircle(
            color = secondaryGlow.copy(alpha = (1f - waveProgress) * 0.42f),
            radius = innerWaveRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
          )
        }
      }

      // Soft ambient nebula aura
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(primaryGlow.copy(alpha = 0.34f), Color.Transparent),
            center = center,
            radius = size.minDimension * 0.49f,
          ),
        center = center,
        radius = size.minDimension * 0.49f,
      )

      // Slow rotating futuristic orbital arcs (accelerated smoothly while Thinking)
      val arcRadius = baseRadius * 1.24f
      val arcStroke = if (orbState == AIOrbVisualState.THINKING) 3.2.dp.toPx() else 1.8.dp.toPx()
      drawArc(
        brush = Brush.sweepGradient(listOf(primaryGlow, secondaryGlow, Color.Transparent, primaryGlow)),
        startAngle = if (lowPowerMotion) 0f else orbitRotation,
        sweepAngle = if (orbState == AIOrbVisualState.THINKING) 220f else 140f,
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
        style = Stroke(width = arcStroke, cap = StrokeCap.Round),
      )
      drawArc(
        color = secondaryGlow.copy(alpha = 0.55f),
        startAngle = (if (lowPowerMotion) 180f else orbitRotation + 180f),
        sweepAngle = 78f,
        useCenter = false,
        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
      )
    }

    // Core Interactive Sphere (>48dp touch target, high contrast)
    Surface(
      onClick = onClick,
      interactionSource = interactionSource,
      shape = CircleShape,
      color = Color.Transparent,
      modifier =
        Modifier
          .size(orbSize * 0.60f)
          .scale(coreScale * pressScale)
          .shadow(
            elevation = if (orbState == AIOrbVisualState.READY) 10.dp else 18.dp,
            shape = CircleShape,
            ambientColor = primaryGlow,
            spotColor = secondaryGlow,
          )
          .semantics { contentDescription = statusAccessibility },
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(
              Brush.linearGradient(
                colors = listOf(primaryGlow, secondaryGlow),
              ),
            )
            .border(
              width = 1.5.dp,
              color = Color.White.copy(alpha = 0.55f),
              shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
      ) {
        // Inner specular glass highlight
        Box(
          modifier =
            Modifier
              .fillMaxSize(0.76f)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                ),
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              when (orbState) {
                AIOrbVisualState.SPEAKING -> Icons.Filled.GraphicEq
                AIOrbVisualState.THINKING -> Icons.Filled.Psychology
                AIOrbVisualState.LISTENING -> Icons.Filled.Mic
                AIOrbVisualState.READY -> Icons.Filled.Mic
              },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(38.dp),
          )
        }
      }
    }
  }
}

@Composable
fun CoreHeroBanner(
  coreState: CoreLifecycleState,
  wakeWordState: WakeWordState,
  lastWakeWordEvent: String?,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "core_pulse")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 0.45f,
      targetValue = 0.95f,
      animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
      label = "pulse_alpha",
    )

  val accentColor by
    animateColorAsState(
      targetValue =
        when (coreState) {
          CoreLifecycleState.LISTENING_PREPARED -> EmeraldReady
          CoreLifecycleState.AWAITING_FOLLOW_UP -> EmeraldReady
          CoreLifecycleState.STANDBY -> MaterialTheme.colorScheme.primary
          CoreLifecycleState.IDLE_DISARMED -> AmberStandby
          else -> MaterialTheme.colorScheme.secondary
        },
      label = "core_accent",
    )

  val statusHeadline =
    when (coreState) {
      CoreLifecycleState.LISTENING_PREPARED -> "Listening State Active"
      CoreLifecycleState.AWAITING_FOLLOW_UP -> "Awaiting Follow-up • Sanju"
      CoreLifecycleState.STANDBY -> "Standby • Waiting for \"MAX\""
      CoreLifecycleState.IDLE_DISARMED -> "Disarmed • Manual Mode"
      CoreLifecycleState.INITIALIZING -> "Booting MAX Core..."
      CoreLifecycleState.UNINITIALIZED -> "Core Offline"
    }

  val statusSubtext =
    when (wakeWordState) {
      is WakeWordState.StandbyListening ->
        if (wakeWordState.isLiveMicrophoneActive) {
          "Ephemeral wake-word framing active. Full speech recognition stays OFF."
        } else {
          "Passive standby active. Say or simulate \"MAX\" to prepare voice conversation."
        }
      is WakeWordState.Detected ->
        "Keyword \"${wakeWordState.keyword}\" verified (${(wakeWordState.confidence * 100).toInt()}%). Voice pipeline ready."
      is WakeWordState.PausedForConversation ->
        "MAXCore notified • Voice conversation pipeline armed."
      is WakeWordState.PermissionRequired ->
        "Microphone permission needed for live \"MAX\" wake-word standby."
      is WakeWordState.UnsupportedFallback -> wakeWordState.reason
      is WakeWordState.Error -> wakeWordState.message
      WakeWordState.Idle -> "Microphone closed. Arm standby when ready for wake word \"MAX\"."
    }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(212.dp)) {
      Image(
        painter = painterResource(id = R.drawable.img_max_core_banner),
        contentDescription = stringResource(id = R.string.cd_core_banner),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().alpha(0.55f),
      )

      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                  ),
              ),
            ),
      )

      Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(44.dp)
                  .clip(CircleShape)
                  .background(accentColor.copy(alpha = 0.16f))
                  .border(1.5.dp, accentColor.copy(alpha = pulseAlpha), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector =
                  when (coreState) {
                    CoreLifecycleState.LISTENING_PREPARED -> Icons.Filled.GraphicEq
                    CoreLifecycleState.STANDBY -> Icons.Filled.Mic
                    else -> Icons.Filled.MicOff
                  },
                contentDescription = stringResource(id = R.string.cd_core_status),
                tint = accentColor,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "MAX",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "CORE ARCHITECTURE v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          Surface(
            color = accentColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
          ) {
            Text(
              text = coreState.name,
              style = MaterialTheme.typography.labelSmall,
              color = accentColor,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = statusHeadline,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = statusSubtext,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (!lastWakeWordEvent.isNullOrBlank()) {
            Text(
              text = "Last event: $lastWakeWordEvent",
              style = MaterialTheme.typography.labelMedium,
              color = EmeraldReady,
            )
          }
        }
      }
    }
  }
}

@Composable
fun PrivacyShieldBanner(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.VerifiedUser,
        contentDescription = stringResource(id = R.string.cd_privacy_shield),
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(22.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = stringResource(id = R.string.privacy_shield_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(id = R.string.privacy_shield_summary),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun SubsystemMetricCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  MAXGlassCard(
    modifier = modifier,
    cornerRadius = 20.dp,
    accentColor = accent,
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = accent,
          modifier = Modifier.size(18.dp),
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun DormantChip(label: String) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = "$label locked",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun SectionHeader(
  title: String,
  trailingBadge: String? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (trailingBadge != null) {
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = trailingBadge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

