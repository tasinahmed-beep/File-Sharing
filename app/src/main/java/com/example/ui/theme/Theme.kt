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

private val DarkColorScheme = darkColorScheme(
  primary = Indigo400,
  onPrimary = Indigo950,
  primaryContainer = Indigo900,
  onPrimaryContainer = Indigo100,
  secondary = Indigo400,
  onSecondary = Indigo950,
  secondaryContainer = Indigo950,
  onSecondaryContainer = Slate200,
  background = Slate900,
  onBackground = Slate50,
  surface = Slate800,
  onSurface = Slate50,
  surfaceVariant = Slate800,
  onSurfaceVariant = Slate400,
  outline = Slate600,
  outlineVariant = Slate600
)

private val LightColorScheme = lightColorScheme(
  primary = Indigo600,
  onPrimary = Color.White,
  primaryContainer = Indigo50,
  onPrimaryContainer = Indigo800,
  secondary = Indigo600,
  onSecondary = Color.White,
  secondaryContainer = Indigo50,
  onSecondaryContainer = Slate800,
  background = Slate50,
  onBackground = Slate800,
  surface = Color.White,
  onSurface = Slate800,
  surfaceVariant = Color.White,
  onSurfaceVariant = Slate600,
  outline = Slate200,
  outlineVariant = Slate100,
  error = Color(0xFFEF4444)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so that the handcrafted design theme is applied correctly
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
