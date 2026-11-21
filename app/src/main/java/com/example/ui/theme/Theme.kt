package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

@Composable
fun getThemeColorScheme(themeName: String): androidx.compose.material3.ColorScheme {
  val primaryColor = when (themeName) {
    "Blue" -> PrimaryBlue
    "Red" -> PrimaryRed
    "Green" -> PrimaryGreen
    "Orange" -> PrimaryOrange
    "Yellow" -> PrimaryYellow
    "Pink" -> PrimaryPink
    "Forest" -> PrimaryForest
    "Carbon Fiber" -> PrimaryCarbon
    "Turquoise" -> PrimaryTurquoise
    "Maroon" -> PrimaryMaroon
    else -> PrimaryPurple
  }
  val onPrimaryColor = when (themeName) {
    "Yellow", "Green", "Orange", "Carbon Fiber", "Turquoise" -> Color.Black
    else -> Color.White
  }
  
  val backgroundColor = when (themeName) {
    "Forest" -> ForestBackground
    "Carbon Fiber" -> CarbonBackground
    "Turquoise" -> TurquoiseBackground
    "Maroon" -> MaroonBackground
    else -> BackgroundDark
  }
  
  val surfaceColor = when (themeName) {
    "Forest" -> ForestSurface
    "Carbon Fiber" -> CarbonSurface
    "Turquoise" -> TurquoiseSurface
    "Maroon" -> MaroonSurface
    else -> SurfaceDark
  }
  
  val surfaceVariantColor = when (themeName) {
    "Forest" -> ForestSurfaceVariant
    "Carbon Fiber" -> CarbonSurfaceVariant
    "Turquoise" -> TurquoiseSurfaceVariant
    "Maroon" -> MaroonSurfaceVariant
    else -> SurfaceVariantDark
  }
  
  val outlineColor = when (themeName) {
    "Forest" -> ForestOutline
    "Carbon Fiber" -> CarbonOutline
    "Turquoise" -> TurquoiseOutline
    "Maroon" -> MaroonOutline
    else -> OutlineDark
  }

  return darkColorScheme(
    primary = primaryColor,
    onPrimary = onPrimaryColor,
    background = backgroundColor,
    onBackground = TextPrimary,
    surface = surfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = surfaceVariantColor,
    onSurfaceVariant = TextSecondary,
    outline = outlineColor,
    secondary = primaryColor,
    onSecondary = onPrimaryColor,
    secondaryContainer = surfaceVariantColor,
    onSecondaryContainer = primaryColor,
    tertiary = primaryColor,
    onTertiary = TextPrimary,
    tertiaryContainer = surfaceVariantColor,
    onTertiaryContainer = TextSecondary,
  )
}

@Composable
fun MyApplicationTheme(
  themeName: String = "Purple",
  content: @Composable () -> Unit,
) {
  val colorScheme = getThemeColorScheme(themeName)

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
