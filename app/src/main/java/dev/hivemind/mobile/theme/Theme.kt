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

// Havrus Obsidian & Brand Gold — preto profundo, grafite sólido, branco e dourado (SDD Spec 001).
val ObsidianBg = Color(0xFF050505)
val SurfacePanel = Color(0xFF0C0C0C)
val SurfaceInput = Color(0xFF0A0A0A)
val ObsidianCard = Color(0xFF111111)
val SurfaceHover = Color(0xFF1B1B1B)
val ObsidianSurface = Color(0xFF1F1F1F)
val ObsidianBorder = Color(0xFF262626)

val ActionPrimary = Color(0xFFF3F2F0)
val ActionPrimaryText = Color(0xFF090909)
val BrandGold = Color(0xFFC6A15B)
val BrandGoldStrong = Color(0xFFE8C881)
// Alias de compatibilidade apontando para o acento de marca Havrus.
val SteelBlue = BrandGold

val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFE58C98)
val ApprovalAmber = Color(0xFFE2AE62)
val InfoBlue = Color(0xFF3B82F6)

val TextPrimary = Color(0xFFFFFFFF)
val TextBody = Color(0xFFE5E5E5)
val TextDim = Color(0xFFA3A3A3)
val TextTertiary = Color(0xFF8A8A8A)

private val HiveMindScheme = darkColorScheme(
    primary = BrandGold,
    onPrimary = Color(0xFF111111),
    secondary = ActionPrimary,
    onSecondary = ActionPrimaryText,
    tertiary = SuccessGreen,
    onTertiary = Color(0xFF090909),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianCard,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextDim,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = Color(0xFF2B2B2B),
    surfaceContainerLow = SurfacePanel,
    error = ErrorRed,
    onError = Color(0xFF2A0A10),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1B1B1B),
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
    // Dark-only na paleta Havrus Obsidian & Brand Gold (SDD Spec 001).
    MaterialTheme(
        colorScheme = HiveMindScheme,
        shapes = HiveMindShapes,
        typography = HiveMindTypography,
        content = content,
    )
}
