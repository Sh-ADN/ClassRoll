package com.aistudio.classroll.jkmxlp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

private val ForestColorScheme = lightColorScheme(
  primary = ForestPrimary,
  secondary = ForestSecondary,
  tertiary = ForestTertiary,
  background = ForestBackground
)

private val OceanColorScheme = lightColorScheme(
  primary = OceanPrimary,
  secondary = OceanSecondary,
  tertiary = OceanTertiary,
  background = OceanBackground
)

@Composable
fun MyApplicationTheme(
  themeMode: String = "SYSTEM",
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    "DARK" -> true
    "LIGHT" -> false
    else -> darkTheme
  }

  val colorScheme = when (themeMode) {
    "FOREST" -> ForestColorScheme
    "OCEAN" -> OceanColorScheme
    "DARK" -> DarkColorScheme
    "LIGHT" -> LightColorScheme
    else -> {
      if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else if (isDark) {
        DarkColorScheme
      } else {
        LightColorScheme
      }
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
