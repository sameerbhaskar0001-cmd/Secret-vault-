package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppTheme(val id: String, val displayName: String, val previewColor: Color) {
    CLASSIC("classic", "Classic", Color(0xFF4DB6AC)),
    OCEAN_BREEZE("ocean_breeze", "Ocean Breeze", Color(0xFF0EA5E9)),
    NORDIC_EMERALD("nordic_emerald", "Nordic Emerald", Color(0xFF10B981)),
    SUNSET_ROSE("sunset_rose", "Sunset Rose", Color(0xFFF43F5E)),
    MIDNIGHT_BLUE("midnight_blue", "Midnight Blue", Color(0xFF3B82F6)),
    GRAPHITE("graphite", "Graphite", Color(0xFFFFFFFF)),
    LAVENDER_MIST("lavender_mist", "Lavender Mist", Color(0xFF8B5CF6)),
    QUANTUM_CYAN("quantum_cyan", "Quantum Cyan", Color(0xFF00E5FF))
}

data class AppThemeColors(
    val brandBg: Color,
    val textDark: Color,
    val textMedium: Color,
    val themePurple: Color,
    val themeLightPurple: Color,
    val themeContainerBorder: Color,
    val keypadBg: Color,
    val digitBg: Color
)

val ClassicColors = AppThemeColors(
    brandBg = Color(0xFF121212),
    textDark = Color(0xFFFFFFFF),
    textMedium = Color(0xFFAAAAAA),
    themePurple = Color(0xFF4DB6AC),
    themeLightPurple = Color(0xFF263238),
    themeContainerBorder = Color(0xFF2D2D2D),
    keypadBg = Color(0xFF1E1E1E),
    digitBg = Color(0xFF2C2C2C)
)

val OceanBreezeColors = AppThemeColors(
    brandBg = Color(0xFF0F172A),
    textDark = Color(0xFFF8FAFC),
    textMedium = Color(0xFF94A3B8),
    themePurple = Color(0xFF0EA5E9),
    themeLightPurple = Color(0xFF1E293B),
    themeContainerBorder = Color(0xFF334155),
    keypadBg = Color(0xFF141F33),
    digitBg = Color(0xFF1E293B)
)

val NordicEmeraldColors = AppThemeColors(
    brandBg = Color(0xFF0B1914),
    textDark = Color(0xFFF0FDF4),
    textMedium = Color(0xFF86EFAC),
    themePurple = Color(0xFF10B981),
    themeLightPurple = Color(0xFF133629),
    themeContainerBorder = Color(0xFF1B4332),
    keypadBg = Color(0xFF0D1E18),
    digitBg = Color(0xFF163026)
)

val SunsetRoseColors = AppThemeColors(
    brandBg = Color(0xFF26191B),
    textDark = Color(0xFFFFF1F2),
    textMedium = Color(0xFFFDA4AF),
    themePurple = Color(0xFFF43F5E),
    themeLightPurple = Color(0xFF3F2128),
    themeContainerBorder = Color(0xFF4C2B32),
    keypadBg = Color(0xFF2C1B1F),
    digitBg = Color(0xFF3B242A)
)

val MidnightBlueColors = AppThemeColors(
    brandBg = Color(0xFF0A0F24),
    textDark = Color(0xFFF0F4FF),
    textMedium = Color(0xFF9BA4B5),
    themePurple = Color(0xFF3B82F6),
    themeLightPurple = Color(0xFF17203F),
    themeContainerBorder = Color(0xFF202B52),
    keypadBg = Color(0xFF0F1530),
    digitBg = Color(0xFF1A2345)
)

val GraphiteColors = AppThemeColors(
    brandBg = Color(0xFF1A1A1C),
    textDark = Color(0xFFF4F4F5),
    textMedium = Color(0xFFA1A1AA),
    themePurple = Color(0xFFFFFFFF),
    themeLightPurple = Color(0xFF27272A),
    themeContainerBorder = Color(0xFF3F3F46),
    keypadBg = Color(0xFF18181B),
    digitBg = Color(0xFF27272A)
)

val LavenderMistColors = AppThemeColors(
    brandBg = Color(0xFF1A1625),
    textDark = Color(0xFFF5F3FF),
    textMedium = Color(0xFFC4B5FD),
    themePurple = Color(0xFF8B5CF6),
    themeLightPurple = Color(0xFF2E2244),
    themeContainerBorder = Color(0xFF3B2C59),
    keypadBg = Color(0xFF1F1A30),
    digitBg = Color(0xFF2B2342)
)

val QuantumCyanColors = AppThemeColors(
    brandBg = Color(0xFF081418),
    textDark = Color(0xFFFFFFFF),
    textMedium = Color(0xFF00E5FF),
    themePurple = Color(0xFF00E5FF),
    themeLightPurple = Color(0xFF0F242A),
    themeContainerBorder = Color(0xFF16373F),
    keypadBg = Color(0xFF0D1C21),
    digitBg = Color(0xFF0D1C21)
)

val LocalAppThemeColors = androidx.compose.runtime.staticCompositionLocalOf { GraphiteColors }
val LocalAppTheme = androidx.compose.runtime.staticCompositionLocalOf { AppTheme.GRAPHITE }

@Composable
fun MyApplicationTheme(
  theme: AppTheme = AppTheme.GRAPHITE,
  content: @Composable () -> Unit,
) {
  val colors = when (theme) {
    AppTheme.CLASSIC -> ClassicColors
    AppTheme.OCEAN_BREEZE -> OceanBreezeColors
    AppTheme.NORDIC_EMERALD -> NordicEmeraldColors
    AppTheme.SUNSET_ROSE -> SunsetRoseColors
    AppTheme.MIDNIGHT_BLUE -> MidnightBlueColors
    AppTheme.GRAPHITE -> GraphiteColors
    AppTheme.LAVENDER_MIST -> LavenderMistColors
    AppTheme.QUANTUM_CYAN -> QuantumCyanColors
  }

  val colorScheme = lightColorScheme(
    primary = colors.themePurple,
    secondary = colors.themeLightPurple,
    tertiary = colors.themeContainerBorder,
    background = colors.brandBg,
    surface = colors.brandBg
  )

  androidx.compose.runtime.CompositionLocalProvider(
    LocalAppThemeColors provides colors,
    LocalAppTheme provides theme
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

