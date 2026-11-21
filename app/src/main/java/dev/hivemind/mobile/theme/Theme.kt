@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.hivemind.mobile.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hivemind.mobile.R

// Havrus Obsidian & Brand Gold — preto profundo, grafite sólido, branco e dourado (SDD Spec 001).
val ObsidianBg = Color(0xFF050505)
val SurfacePanel = Color(0xFF0C0C0C)
val SurfaceInput = Color(0xFF0A0A0A)
val ObsidianCard = Color(0xFF111111)
val SurfaceHover = Color(0xFF1B1B1B)
val ObsidianSurface = Color(0xFF1F1F1F)
val SurfaceOptionActive = Color(0xFF353535)
val ObsidianBorder = Color(0xFF262626)
val BorderEmphasis = Color(0x1FFFFFFF)
val BorderControl = Color(0xFF707070)

val ActionPrimary = Color(0xFFF3F2F0)
val ActionPrimaryText = Color(0xFF090909)
val OnPrimary = Color(0xFF090909)
val BrandGold = Color(0xFFC6A15B)
val BrandGoldStrong = Color(0xFFE8C881)
// Decorativo. Não usar como texto: #8C6E33 sobre #050505 fica abaixo de 4.5:1.
val BrandGoldMuted = Color(0xFF8C6E33)
val BrandGoldSubtle = Color(0x1FC6A15B)
val BrandGoldBorder = Color(0x40C6A15B)
// Dourado escuro para superfície clara invertida. #C6A15B sobre branco fica abaixo de 4.5:1.
val InverseBrand = Color(0xFF5C4716)

val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFE58C98)
val ErrorText = Color(0xFFEBA2AC)
val ApprovalAmber = Color(0xFFE2AE62)
val InfoBlue = Color(0xFF3B82F6)
val InfoText = Color(0xFF60A5FA)

val TextPrimary = Color(0xFFFFFFFF)
val TextBody = Color(0xFFE5E5E5)
val TextDim = Color(0xFFA3A3A3)
val TextTertiary = Color(0xFF8A8A8A)

val DiffAddedBg = Color(0xFF0E2A1E)
val DiffAddedFg = Color(0xFF8CE0B3)
val DiffRemovedBg = Color(0xFF2A1214)
val DiffRemovedFg = Color(0xFFF3A8A8)
val DiffHunkBg = Color(0xFF2A2418)
val DiffHeaderBg = Color(0xFF141414)

private val SurfaceContainerHigh = Color(0xFF2B2B2B)
private val ErrorContainer = Color(0xFF2A1214)
private val SuccessContainer = Color(0xFF0E2A1E)

private val HiveMindScheme = darkColorScheme(
    primary = BrandGold,
    onPrimary = OnPrimary,
    primaryContainer = BrandGold,
    onPrimaryContainer = OnPrimary,
    inversePrimary = InverseBrand,
    secondary = ActionPrimary,
    onSecondary = ActionPrimaryText,
    secondaryContainer = ObsidianSurface,
    onSecondaryContainer = TextBody,
    tertiary = SuccessGreen,
    onTertiary = OnPrimary,
    tertiaryContainer = SuccessContainer,
    onTertiaryContainer = DiffAddedFg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    // Paridade com --surface-panel (#0C0C0C) da IDE (Spec 10 / constituição D2/D8).
    surface = SurfacePanel,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextBody,
    surfaceTint = BrandGold,
    inverseSurface = TextBody,
    inverseOnSurface = OnPrimary,
    error = ErrorRed,
    onError = OnPrimary,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorText,
    outline = ObsidianBorder,
    outlineVariant = SurfaceHover,
    scrim = Color(0xFF000000),
    surfaceBright = SurfaceContainerHigh,
    surfaceDim = ObsidianBg,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SurfacePanel,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceOptionActive,
)

private val HiveMindShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

// --- Tipografia: Inter (sans, variável) e JetBrains Mono, embutidas no APK
//     para espelhar a IDE (Spec 10 §3.1.3; constituição D8). ---

/** Inter variable: pesos resolvidos via eixo wght (minSdk 26 = API O). */
val HiveFonts = FontFamily(
    Font(
        R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** JetBrains Mono para código, diffs, tokens e metadados Jev. */
val MonoFonts = FontFamily(
    Font(R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
)

private val HiveMindTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp, fontFamily = HiveFonts,
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp, fontFamily = HiveFonts,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp, fontFamily = HiveFonts,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = HiveFonts,
    ),
    titleSmall = TextStyle(
        fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = HiveFonts,
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFamily = HiveFonts),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = HiveFonts),
    labelLarge = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = HiveFonts,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFamily = HiveFonts,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFamily = HiveFonts,
    ),
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
