package dev.hivemind.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hivemind.mobile.theme.ApprovalAmber
import dev.hivemind.mobile.theme.BrandGold
import dev.hivemind.mobile.theme.InfoBlue
import dev.hivemind.mobile.theme.ObsidianBg
import dev.hivemind.mobile.theme.ObsidianBorder
import dev.hivemind.mobile.theme.ObsidianSurface
import dev.hivemind.mobile.theme.SuccessGreen

fun blobColor(slot: Int): Color = when (slot and 3) {
    0 -> SuccessGreen
    1 -> InfoBlue
    2 -> BrandGold
    else -> ApprovalAmber
}

@Composable
fun HiveBlob(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(w * 0.42f, h * 0.42f),
        )
        val eye = w * 0.075f
        drawCircle(color = ObsidianBg, radius = eye, center = Offset(w * 0.38f, h * 0.44f))
        drawCircle(color = ObsidianBg, radius = eye, center = Offset(w * 0.64f, h * 0.44f))
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = ObsidianSurface,
    tint: Color = Color.Unspecified,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

@Composable
fun WaveformGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = floatArrayOf(0.35f, 0.72f, 1f, 0.55f, 0.88f, 0.42f)
        val slot = size.width / bars.size
        val barW = slot * 0.42f
        bars.forEachIndexed { index, scale ->
            val bh = size.height * scale
            val x = slot * index + (slot - barW) / 2f
            drawRoundRect(
                color = tint,
                topLeft = Offset(x, (size.height - bh) / 2f),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

@Composable
fun MicGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.08f),
            size = Size(w * 0.32f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
        )
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.28f),
            size = Size(w * 0.64f, h * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f),
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.70f),
            end = Offset(w * 0.5f, h * 0.86f),
            strokeWidth = w * 0.07f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.88f),
            end = Offset(w * 0.66f, h * 0.88f),
            strokeWidth = w * 0.07f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
fun AppsGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = size.width * 0.14f
        val cell = (size.width - gap) / 2f
        val positions = listOf(
            Offset(0f, 0f),
            Offset(cell + gap, 0f),
            Offset(0f, cell + gap),
            Offset(cell + gap, cell + gap),
        )
        positions.forEach { offset ->
            drawRoundRect(
                color = tint,
                topLeft = offset,
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.28f, cell * 0.28f),
            )
        }
    }
}
