package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MAXDarkColorScheme =
  darkColorScheme(
    primary = CyanCore,
    onPrimary = ObsidianBackground,
    primaryContainer = CyanCoreContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = IndigoSecondary,
    onSecondary = ObsidianBackground,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = EmeraldReady,
    onTertiary = ObsidianBackground,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = ObsidianBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianOutline,
    error = CoralError,
    onError = ObsidianBackground,
  )

private val MAXLightColorScheme =
  lightColorScheme(
    primary = CyanLightPrimary,
    onPrimary = Color.White,
    primaryContainer = CyanLightContainer,
    onPrimaryContainer = CyanLightOnContainer,
    secondary = IndigoLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = EmeraldLightReady,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = DaylightBackground,
    onBackground = Color(0xFF0F172A),
    surface = DaylightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = DaylightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
  )

val MAXShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun MAXTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) MAXDarkColorScheme else MAXLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = MAXTypography,
    shapes = MAXShapes,
    content = content,
  )
}
