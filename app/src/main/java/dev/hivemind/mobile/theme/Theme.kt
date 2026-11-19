package dev.hivemind.mobile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Identidade Obsidian Graphite × Silver + Steel Blue (zero roxo).
val ObsidianBg = Color(0xFF0B0D10)
val ObsidianCard = Color(0xFF12151B)
val ObsidianSurface = Color(0xFF1A1F28)
val SteelBlue = Color(0xFF4C8DFF)
val SuccessGreen = Color(0xFF34D399)
val ErrorRed = Color(0xFFF87171)
val ApprovalAmber = Color(0xFFFBBF24)
val TextPrimary = Color(0xFFE6E9EF)
val TextDim = Color(0xFF8A93A3)

private val HiveMindScheme = darkColorScheme(
    primary = SteelBlue,
    onPrimary = Color.White,
    secondary = SteelBlue,
    onSecondary = Color.White,
    tertiary = SuccessGreen,
    onTertiary = Color.Black,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianCard,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextDim,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = Color(0xFF222936),
    surfaceContainerLow = Color(0xFF161A22),
    error = ErrorRed,
    onError = Color.Black,
    outline = Color(0xFF2A3140),
    outlineVariant = Color(0xFF1F2530),
)

@Composable
fun HiveMindTheme(content: @Composable () -> Unit) {
    // App é dark-only por identidade (espec: fundo #0B0D10).
    MaterialTheme(
        colorScheme = HiveMindScheme,
        typography = Typography(),
        content = content,
    )
}
