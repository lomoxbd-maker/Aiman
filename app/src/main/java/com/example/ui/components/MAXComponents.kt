package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.CoreLifecycleState
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.wakeword.WakeWordState

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
          CoreLifecycleState.STANDBY -> MaterialTheme.colorScheme.primary
          CoreLifecycleState.IDLE_DISARMED -> AmberStandby
          else -> MaterialTheme.colorScheme.secondary
        },
      label = "core_accent",
    )

  val statusHeadline =
    when (coreState) {
      CoreLifecycleState.LISTENING_PREPARED -> "Listening State Active"
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
    shape = RoundedCornerShape(18.dp),
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
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
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
    color = MaterialTheme.colorScheme.surfaceVariant,
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
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (trailingBadge != null) {
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
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
