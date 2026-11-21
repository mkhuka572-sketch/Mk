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

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

private val ProjectXDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF281D59),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = NeonCyan,
    onSecondary = Color(0xFF021A22),
    secondaryContainer = Color(0xFF0E3446),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF172C57),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = VoidBlack,
    onBackground = TextPrimaryDark,
    surface = MidnightNavy,
    onSurface = TextPrimaryDark,
    surfaceVariant = DeepSlateSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = VoidBlack,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = DeepSlateSurface,
    surfaceContainerHigh = GlassCardDark,
    surfaceContainerHighest = Color(0xFF1E2B54),
    outline = GlassBorderDark,
    outlineVariant = Color(0xFF1D284D),
    error = RoseError,
    onError = Color.White
)

private val ProjectXLightColorScheme = lightColorScheme(
    primary = ElectricVioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = TealCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = CobaltBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = IceCanvas,
    onBackground = TextPrimaryLight,
    surface = FrostSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftTintLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = FrostSurface,
    surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = SoftTintLight,
    surfaceContainerHigh = Color(0xFFDDE7F7),
    surfaceContainerHighest = Color(0xFFD2DDF2),
    outline = GlassBorderLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFE11D48),
    onError = Color.White
)

val ProjectXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun ProjectXTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (useDark) ProjectXDarkColorScheme else ProjectXLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ProjectXTypography,
        shapes = ProjectXShapes,
        content = content
    )
}
