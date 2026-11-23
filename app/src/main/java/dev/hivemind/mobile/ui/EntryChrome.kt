package dev.hivemind.mobile.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.R
import dev.hivemind.mobile.theme.ActionPrimary
import dev.hivemind.mobile.theme.ActionPrimaryText
import dev.hivemind.mobile.theme.BorderControl
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.BrandGoldBorder
import dev.hivemind.mobile.theme.BrandGoldSubtle
import dev.hivemind.mobile.theme.ErrorRed
import dev.hivemind.mobile.theme.ObsidianBg
import dev.hivemind.mobile.theme.ObsidianCard
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SuccessGreen
import dev.hivemind.mobile.theme.TextDim
import dev.hivemind.mobile.theme.TextPrimary
import kotlinx.coroutines.isActive

/** Paparazzi e previews estáticos desligam o pulso para o frame não ficar preso numa animação infinita. */
val LocalEntryMotion = staticCompositionLocalOf { true }

@Composable
fun StaticEntry(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEntryMotion provides false, content = content)
}

@Composable
fun EntryScaffold(
    applyInsets: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        0f to BrandGold.copy(alpha = 0.20f),
                        0.55f to BrandGoldSubtle,
                        1f to ObsidianBg,
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .then(if (applyInsets) Modifier.edgeInsets() else Modifier)
                .padding(horizontal = 22.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 12.dp),
                content = content,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = footer,
            )
        }
    }
}

@Composable
fun EntryBackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            onClick = onBack,
            tint = TextPrimary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun GoldMark() {
    var scale by remember { mutableFloatStateOf(1f) }
    val motion = LocalEntryMotion.current
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        while (isActive) {
            animate(1f, 1.08f, animationSpec = tween(1500, easing = FastOutSlowInEasing)) { value, _ ->
                scale = value
            }
            animate(1.08f, 0.94f, animationSpec = tween(1500, easing = FastOutSlowInEasing)) { value, _ ->
                scale = value
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(188.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    Brush.radialGradient(
                        0f to BrandGold.copy(alpha = 0.55f),
                        0.42f to BrandGold.copy(alpha = 0.18f),
                        1f to ObsidianBg.copy(alpha = 0f),
                    ),
                    CircleShape,
                ),
        )
        Image(
            painter = painterResource(R.drawable.hivemind_mark),
            contentDescription = "HiveMind",
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .border(2.dp, BrandGoldBorder, CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun QrHero() {
    Box(Modifier.fillMaxWidth().height(268.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(248.dp)
                .background(
                    Brush.radialGradient(
                        0f to BrandGold.copy(alpha = 0.28f),
                        1f to ObsidianBg.copy(alpha = 0f),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(214.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(ObsidianCard)
                .border(1.dp, BrandGoldBorder, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                val arm = 26.dp.toPx()
                val stroke = 3.dp.toPx()
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(BrandGold, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
                    drawLine(BrandGold, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
                }
                corner(0f, 0f, 1f, 1f)
                corner(size.width, 0f, -1f, 1f)
                corner(0f, size.height, 1f, -1f)
                corner(size.width, size.height, -1f, -1f)
            }
            Canvas(Modifier.size(132.dp)) {
                val n = 15
                val gap = size.width * 0.012f
                val cell = (size.width - gap * (n - 1)) / n
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (!qrModule(x, y)) continue
                        drawRoundRect(
                            color = TextPrimary,
                            topLeft = Offset(x * (cell + gap), y * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(cell * 0.22f, cell * 0.22f),
                        )
                    }
                }
            }
        }
    }
}

private fun qrFinder(x: Int, y: Int, ox: Int, oy: Int): Boolean {
    val lx = x - ox
    val ly = y - oy
    if (lx !in 0..6 || ly !in 0..6) return false
    if (lx == 0 || ly == 0 || lx == 6 || ly == 6) return true
    return lx in 2..4 && ly in 2..4
}

private fun qrModule(x: Int, y: Int): Boolean {
    if (qrFinder(x, y, 0, 0) || qrFinder(x, y, 8, 0) || qrFinder(x, y, 0, 8)) return true
    val data = intArrayOf(
        8 * 15 + 8, 9 * 15 + 10, 10 * 15 + 8, 8 * 15 + 12, 12 * 15 + 3,
        13 * 15 + 5, 12 * 15 + 12, 14 * 15 + 10, 10 * 15 + 13, 4 * 15 + 9,
        11 * 15 + 11, 9 * 15 + 14, 14 * 15 + 14, 6 * 15 + 12,
    )
    val key = y * 15 + x
    return data.any { it == key }
}

@Composable
fun EntryBullet(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(ObsidianSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = BrandGold, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

@Composable
fun EntryStep(index: Int, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(ObsidianSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString(),
                color = BrandGold,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val background = if (primary) ActionPrimary else ObsidianSurface
    val foreground = if (primary) ActionPrimaryText else TextPrimary
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = background.copy(alpha = 0.45f),
            disabledContentColor = foreground.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        border = if (primary) null else BorderStroke(1.dp, BorderControl),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = foreground,
            )
        } else {
            Text(text, fontWeight = FontWeight.SemiBold, color = foreground)
        }
    }
}

@Composable
fun QuietLink(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
    ) {
        Text(
            text,
            color = TextDim,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun IconField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = hiveFieldColors(),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PulseDot(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    val motion = LocalEntryMotion.current
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        while (isActive) {
            animate(1f, 1.45f, animationSpec = tween(800, easing = FastOutSlowInEasing)) { value, _ ->
                scale = value
            }
            animate(1.45f, 1f, animationSpec = tween(800, easing = FastOutSlowInEasing)) { value, _ ->
                scale = value
            }
        }
    }
    Box(modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(16.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.35f
                }
                .background(color, CircleShape),
        )
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}

@Composable
fun ChangeBar(added: Int, removed: Int, modifier: Modifier = Modifier) {
    val add = added.coerceAtLeast(0)
    val cut = removed.coerceAtLeast(0)
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(ObsidianSurface),
    ) {
        if (add == 0 && cut == 0) {
            Box(Modifier.fillMaxSize().background(ObsidianSurface))
        } else {
            if (add > 0) {
                Box(Modifier.weight(add.toFloat()).fillMaxHeight().background(SuccessGreen))
            }
            if (cut > 0) {
                Box(Modifier.weight(cut.toFloat()).fillMaxHeight().background(ErrorRed))
            }
        }
    }
}

@Composable
fun StudioEmpty(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            0f to BrandGold.copy(alpha = 0.45f),
                            1f to ObsidianBg.copy(alpha = 0f),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .size(72.dp)
                    .background(ObsidianSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = BrandGold, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            textAlign = TextAlign.Center,
        )
    }
}
