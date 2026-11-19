package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ModuleCategory
import com.example.core.ModuleDescriptor
import com.example.core.ModuleReadiness
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberStandby
import com.example.ui.theme.EmeraldReady
import com.example.viewmodel.MAXUiState

@Composable
fun ModulesArchitectureScreen(
  uiState: MAXUiState,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember { mutableStateOf<ModuleCategory?>(null) }

  val filteredModules =
    remember(uiState.modules, selectedCategory) {
      if (selectedCategory == null) {
        uiState.modules
      } else {
        uiState.modules.filter { it.category == selectedCategory }
      }
    }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isTwoColumn = maxWidth >= 640.dp

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SectionHeader(
            title = "MAX Modular Architecture",
            trailingBadge = "${uiState.modules.size} packages",
          )

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All") },
                modifier = Modifier.testTag("filter_modules_all"),
              )
            }
            items(ModuleCategory.entries) { category ->
              FilterChip(
                selected = selectedCategory == category,
                onClick = { selectedCategory = category },
                label = {
                  Text(
                    when (category) {
                      ModuleCategory.CORE_MANAGER -> "Core Managers"
                      ModuleCategory.VOICE_AND_COGNITION -> "Voice & AI"
                      ModuleCategory.DEVICE_CAPABILITY_DORMANT -> "Dormant"
                      ModuleCategory.INFRASTRUCTURE -> "Infra"
                    },
                  )
                },
                modifier = Modifier.testTag("filter_modules_${category.name.lowercase()}"),
              )
            }
          }
        }
      }

      if (isTwoColumn) {
        val rows = filteredModules.chunked(2)
        items(rows) { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            rowItems.forEach { module ->
              ModuleCard(module = module, modifier = Modifier.weight(1f))
            }
            if (rowItems.size == 1) {
              SpacerPlaceholder(modifier = Modifier.weight(1f))
            }
          }
        }
      } else {
        items(filteredModules, key = { it.packageName }) { module ->
          ModuleCard(
            module = module,
            modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun SpacerPlaceholder(modifier: Modifier = Modifier) {
  androidx.compose.foundation.layout.Box(modifier = modifier)
}

@Composable
private fun ModuleCard(
  module: ModuleDescriptor,
  modifier: Modifier = Modifier,
) {
  val badgeColor =
    when (module.readiness) {
      ModuleReadiness.ACTIVE_LISTENING -> EmeraldReady
      ModuleReadiness.READY -> MaterialTheme.colorScheme.primary
      ModuleReadiness.STANDBY -> MaterialTheme.colorScheme.secondary
      ModuleReadiness.FALLBACK_MODE -> AmberStandby
      ModuleReadiness.DORMANT_LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Card(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
          text = module.packageName,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Surface(
          shape = RoundedCornerShape(50),
          color = badgeColor.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        ) {
          Text(
            text = module.readiness.name.replace("_", " "),
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      Text(
        text = module.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Text(
        text = module.roleSummary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = "Privacy: ${module.privacyNote}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
      )
    }
  }
}
