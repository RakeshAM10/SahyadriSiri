package com.sahyadrisiri.ui.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyadrisiri.ui.theme.*
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  LIQUID GLASS DESIGN TOKENS
//  Mirrors the web app's backdrop-blur + border-white/[0.04] + shadow patterns
// ─────────────────────────────────────────────────────────────────────────────

object Glass {
    // Base glass surface colours
    val surfaceLight     = Color.White.copy(alpha = 0.72f)
    val surfaceMedium    = Color.White.copy(alpha = 0.55f)
    val surfaceFrost     = Color.White.copy(alpha = 0.38f)
    val surfaceUltraThin = Color.White.copy(alpha = 0.20f)

    // Border
    val borderLight      = Color.White.copy(alpha = 0.60f)
    val borderHair       = Color(0xFF000000).copy(alpha = 0.06f)

    // Specular highlight (top shimmer line, mirrors the CSS gradient line)
    val specularGradient = Brush.horizontalGradient(
        listOf(Color.Transparent, Color.White.copy(0.90f), Color.Transparent)
    )

    // Scrim for overlays
    val scrim = Color.Black.copy(alpha = 0.30f)

    // Status tints
    fun cleanTint(alpha: Float = 0.07f)    = Green34C759.copy(alpha)
    fun warnTint(alpha: Float = 0.07f)     = OrangeFF9500.copy(alpha)
    fun pollutedTint(alpha: Float = 0.07f) = RedFF3B30.copy(alpha)
}

// ─────────────────────────────────────────────────────────────────────────────
//  GlassPanel — replaces all "bg-white/80 backdrop-blur-2xl" cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    surfaceColor: Color = Glass.surfaceLight,
    tintBrush: Brush? = null,
    showSpecular: Boolean = true,
    showBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (tintBrush != null)
                    Modifier.background(tintBrush)
                else
                    Modifier.background(surfaceColor)
            )
            .then(
                if (showBorder)
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Glass.borderLight, Glass.borderHair)
                        ),
                        shape = RoundedCornerShape(cornerRadius)
                    )
                else Modifier
            )
    ) {
        // Specular highlight at top — the signature iOS glass shimmer line
        if (showSpecular) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Glass.specularGradient)
                    .align(Alignment.TopCenter)
            )
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  StatusGlassCard — coloured tinted glass card for report statuses
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusGlassCard(
    status: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val gradient = when (status) {
        "clean"    -> Brush.linearGradient(listOf(Color.White.copy(0.70f), Color(0xFFE5F5EA).copy(0.70f)))
        "polluted" -> Brush.linearGradient(listOf(Color.White.copy(0.70f), Color(0xFFFFEBE5).copy(0.70f)))
        else       -> Brush.linearGradient(listOf(Color.White.copy(0.70f), Color(0xFFFFF4E5).copy(0.70f)))
    }

    GlassPanel(
        modifier = modifier,
        cornerRadius = cornerRadius,
        tintBrush = gradient,
        showSpecular = true,
        showBorder = true,
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  LiquidIconBox — the animated "water level" icon box from the web app
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiquidIconBox(
    color: Color,
    size: Dp = 64.dp,
    cornerRadius: Dp = 20.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Infinite slow oscillation — mirrors the motion.div y:[20,15,20] animation
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(0.90f))
            .border(1.dp, Glass.borderLight, RoundedCornerShape(cornerRadius))
            .drawBehind {
                // Draw the liquid fill animated blob
                drawLiquidFill(color, wavePhase, fillFraction = 0.48f)
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Draws an organic liquid blob that rises and falls — a pure-Compose
 * equivalent of the CSS clip-path blob in the web app.
 */
private fun DrawScope.drawLiquidFill(
    color: Color,
    phase: Float,
    fillFraction: Float
) {
    val w = size.width
    val h = size.height
    val baseY = h * (1f - fillFraction)
    val amplitude = h * 0.06f

    val path = Path().apply {
        moveTo(0f, h)
        lineTo(w, h)
        lineTo(w, baseY + amplitude * sin(phase + 2f))
        // Wavy top edge
        val steps = 32
        for (i in steps downTo 0) {
            val x = w * i / steps
            val y = baseY + amplitude * sin(phase + x / w * 4f)
            lineTo(x, y)
        }
        close()
    }
    drawPath(path, color.copy(alpha = 0.30f))
}

// ─────────────────────────────────────────────────────────────────────────────
//  GlassBottomNavBar  — translucent nav with hairline border
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassNavBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF9F9F9).copy(0.70f), Color(0xFFF9F9F9).copy(0.92f))
                )
            )
    ) {
        // Top hairline border
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.Black.copy(0.10f))
                .align(Alignment.TopCenter)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            content = content
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GlassFAB — the floating action buttons on the map
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassFAB(
    onClick: () -> Unit,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.95f), Color.White.copy(0.85f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White, Glass.borderHair)),
                RoundedCornerShape(size / 2)
            )
            .then(if (enabled) Modifier.glassClickable(onClick) else Modifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  GlassSheet — the bottom sheet glass surface
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassSheetBackground(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.96f), Color.White.copy(0.99f))
                )
            )
    ) {
        // Pull handle
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .size(width = 36.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(0.15f))
                .align(Alignment.CenterHorizontally)
        )
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SegmentedGlassControl — the flow / clarity selector pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SegmentedGlassControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    selectedColor: (String) -> Color,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        surfaceColor = Color(0xFFF2F2F7),
        showSpecular = false,
        showBorder = false
    ) {
        Row(
            Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = selected == option
                val color = selectedColor(option)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected)
                                Brush.verticalGradient(listOf(Color.White, color.copy(0.08f)))
                            else
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(
                            if (isSelected) Modifier.border(
                                0.5.dp, Glass.borderLight, RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .glassClickable { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold
                                     else FontWeight.Normal,
                        color = if (isSelected) color else Color(0xFF6C6C70)
                    )
                }
            }
        }
    }
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.glassClickable(onClick)
