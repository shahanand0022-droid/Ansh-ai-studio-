package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PrinceDarkColorScheme = darkColorScheme(
  primary = ElectricCyan,
  onPrimary = ObsidianBg,
  primaryContainer = CyanContainer,
  onPrimaryContainer = TextPrimary,
  secondary = RoyalGold,
  onSecondary = ObsidianBg,
  secondaryContainer = GoldContainer,
  onSecondaryContainer = RoyalGold,
  tertiary = PlasmaViolet,
  onTertiary = ObsidianBg,
  tertiaryContainer = VioletContainer,
  onTertiaryContainer = TextPrimary,
  background = ObsidianBg,
  onBackground = TextPrimary,
  surface = DeepSurface,
  onSurface = TextPrimary,
  surfaceVariant = CardSurface,
  onSurfaceVariant = TextSecondary,
  surfaceContainerLowest = ObsidianBg,
  surfaceContainerLow = DeepSurface,
  surfaceContainer = CardSurface,
  surfaceContainerHigh = ElevatedSurface,
  surfaceContainerHighest = ElevatedSurface,
  outline = OutlineSubtle,
  outlineVariant = OutlineSubtle.copy(alpha = 0.6f),
  error = CrimsonError,
  onError = TextPrimary,
  errorContainer = CrimsonContainer,
  onErrorContainer = TextPrimary,
)

private val StudioShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PrinceAiStudioTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = PrinceDarkColorScheme,
    typography = Typography,
    shapes = StudioShapes,
    content = content,
  )
}
