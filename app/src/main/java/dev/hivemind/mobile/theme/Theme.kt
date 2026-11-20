package dev.hivemind.mobile.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Identidade Obsidian Slate × Silver + Steel Blue (zero roxo).
val ObsidianBg = Color(0xFF090B10)
val ObsidianCard = Color(0xFF12161F)
val ObsidianSurface = Color(0xFF1A202D)
val ObsidianBorder = Color(0xFF252D3D)
val SteelBlue = Color(0xFF4C8DFF)
val SuccessGreen = Color(0xFF34D399)
val ErrorRed = Color(0xFFF87171)
val ApprovalAmber = Color(0xFFF5B951)
val TextPrimary = Color(0xFFEEF1F7)
val TextDim = Color(0xFF939EB3)

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
    surfaceContainerHigh = Color(0xFF222938),
    surfaceContainerLow = Color(0xFF141822),
    error = ErrorRed,
    onError = Color.Black,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1D2330),
)

private val HiveMindShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

private val HiveMindTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun HiveMindTheme(content: @Composable () -> Unit) {
    // App é dark-only por identidade (fundo Obsidian Slate #090B10, zero roxo).
    MaterialTheme(
        colorScheme = HiveMindScheme,
        shapes = HiveMindShapes,
        typography = HiveMindTypography,
        content = content,
    )
}
