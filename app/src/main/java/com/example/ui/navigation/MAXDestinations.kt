package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Type-safe destination descriptors for Navigation Compose.
 */
enum class MAXDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  CORE(
    route = "max_core",
    label = "Core",
    selectedIcon = Icons.Filled.Memory,
    unselectedIcon = Icons.Outlined.Memory,
    testTag = "nav_core",
  ),
  VISION(
    route = "max_vision",
    label = "Vision",
    selectedIcon = Icons.Filled.PhotoCamera,
    unselectedIcon = Icons.Outlined.PhotoCamera,
    testTag = "nav_vision",
  ),
  MODULES(
    route = "max_modules",
    label = "Modules",
    selectedIcon = Icons.Filled.Hub,
    unselectedIcon = Icons.Outlined.Hub,
    testTag = "nav_modules",
  ),
  DIAGNOSTICS(
    route = "max_diagnostics",
    label = "Diagnostics",
    selectedIcon = Icons.Filled.Build,
    unselectedIcon = Icons.Outlined.Build,
    testTag = "nav_diagnostics",
  ),
  SETTINGS(
    route = "max_settings",
    label = "Settings",
    selectedIcon = Icons.Filled.Settings,
    unselectedIcon = Icons.Outlined.Settings,
    testTag = "nav_settings",
  ),
}
